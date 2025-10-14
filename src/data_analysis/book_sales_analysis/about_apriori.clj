^{:kindly/hide-code true     ; don't render this code to the HTML document
  :clay             {:title  "From Correlations to Recommendations"
                     :quarto {:author   [:tombarys]
                              :image "src/data_analysis/book_sales_analysis/graphviz.png"
                              :description "A Publisher's Journey into Data-Driven Book Sales – exploring how association rule mining can transform business insights using the SciCloj stack."
                              :type     :draft
                              :date     "2025-10-13"
                              :category :data-analysis
                              :tags     [:metadata :civitas :data-analysis :market-basket-analysis :book-publishing]}}}

^:kindly/hide-code
^{:clay {:hide-ui-header true}}
(ns data-analysis.book-sales-analysis.about-apriori
  "From Correlations to Recommendations: A Publisher's Data Science Journey
   
   An article for Clojure Civitas exploring how association rule mining
   can transform business insights using the SciCloj stack."
  (:require [scicloj.kindly.v4.kind :as kind]
            [tablecloth.api :as tc]
            [data-analysis.book-sales-analysis.data-sources-v2 :as data]
            [data-analysis.book-sales-analysis.core-helpers-v2 :as helpers]
            [data-analysis.book-sales-analysis.market-basket-analysis-v2 :as mba]))

;; # From Correlations to Recommendations
;; ## A Publisher's Journey into Data-Driven Book Sales

;; When you run an indie publishing house with over 160 titles and sell thousands of books each month, one question keeps coming back: **Which books do our customers buy together?** This seemingly simple question led me down a fascinating path from basic correlation analysis to building a more robust recommendation system using association rule mining—all with Clojure and the SciCloj ecosystem. 

^:kindly/hide-code
(kind/image
 {:src "books.png"
  :width "100%"
  :max-width "800px"})

;; ## The Starting Point: Understanding Our Data

;; As a publisher at [Jan Melvil Publishing](https://www.melvil.cz), I had access to rich data: about 58,000 orders from 34,000 customers spanning several years. But the data wasn't structured for analysis. Orders looked like this:

^:kindly/hide-code
(def orders-sample
  (-> data/orders-share
      (tc/reorder-columns [:zakaznik :datum :produkt-produkty])
      (tc/random 5)))

^:kindly/hide-code
(kind/table
 orders-sample {:element/max-height 400})

;; *(for clarity, many columns were omitted here; rows were generated with `(tc/random 5)` from anonymized dataset)*

;; Each row represented one order, with books listed as comma-separated values. There are many exceptions, inconsistencies, and format-based differences ^[^1]. To analyze purchasing patterns, I needed to transform this into a format where each book became a binary feature: did a customer buy it (1) or not (0)? This transformation is called **one-hot encoding**.
;;
;; ---
;; 
;; 1: We sell e-books and audiobooks too – readers can use our [fantastic app](https://www.melvil.cz/aplikace-melvil/).

;; ## The Transformation: Making Data Analysis-Ready

;; The transformation from raw orders to an analysis-ready format was crucial. Using Tablecloth, the transformation pipeline was easy (and can be even more simplified).

^:kindly/hide-code
(kind/code
";; From customer orders with book lists...
(map
 (fn [customer-row]
   (let [customer-name (:zakaznik customer-row)
         books-bought-set (set (parse-books-from-list (:all-products customer-row)))
         one-hot-map (reduce (fn [acc book]
                               (assoc acc book (if (contains? books-bought-set book) 1 0)))
                             {}
                             all-titles)]
     (merge {:zakaznik customer-name}
            one-hot-map)))
 (tc/rows customer+orders :as-maps))
;; ...to binary matrix where each column is a book")

;; After transformation, each customer became a row, and each book a column with 1 or 0:


^:kindly/hide-code
(def onehot-sample
  (-> data/orders-share
      helpers/onehot-encode-by-customers
      (tc/reorder-columns [:zakaznik])
      (tc/random 5)
      (tc/head 4)
      (tc/select-columns #"^:zakaznik|^:book-00.*"))) 

^:kindly/hide-code
(kind/table onehot-sample)

;; Now I could start asking interesting questions about co-purchase patterns.

;; ## First Insights: The Correlation Matrix

;; My first instinct was to calculate correlations between all books. A correlation tells you how often two books appear together compared to what you'd expect by chance. When I visualized this as a heatmap, with books ordered chronologically, something fascinating emerged:

(kind/plotly
 {:data [{:type "heatmap"
          :z (tc/columns data/corr-matrix-precalculated)
          :x (tc/column-names data/corr-matrix-precalculated)
          :y (tc/column-names data/corr-matrix-precalculated)
          :colorscale "RdBu"
          :zmid 0}]
  :layout {:title "Book Purchase Correlations (Chronological Order)"
           :xaxis {:tickangle 45}
           :margin {:l 200 :b 50}
           :width 800 :height 600
           :shapes [{:type "rect" :x0 -0.5 :y0 -0.5 :x1 80 :y1 80
                     :line {:color "yellow" :width 3}}]
           :annotations [{:x 40 :y 80 :text "Recently published books show <br>much stronger co-purchase patterns"
                          :showarrow true :arrowhead 2 :arrowsize 1 :arrowwidth 2 :arrowcolor "yellow"
                          :ax 60 :ay -60
                          :font {:size 12 :color "black"}
                          :bgcolor "rgba(255, 255, 200, 0.9)" :bordercolor "yellow" :borderwidth 2}]}})

;; The bright red square in the upper-left corner revealed that **recently published books have much stronger co-purchase patterns** than older titles. This made intuitive sense—customers discovering our catalog tend to buy multiple new releases together.

;; ## A Surprising Discovery: Czech vs. Foreign Authors

;; When I analyzed each book's correlation profile—calculating the mean correlation between each book and all others—another pattern emerged:


^:kindly/hide-code
(def books-data
  "Correlation statistics for each book"
  (let [mean-data (-> (tc/info data/corr-matrix-precalculated)
                      (tc/select-columns [:col-name :mean])
                      (tc/order-by :mean :desc))
        skew-data (-> (tc/info data/corr-matrix-precalculated)
                      (tc/select-columns [:col-name :skew])
                      (tc/order-by :skew :desc))]
    (-> (tc/left-join skew-data mean-data :col-name)
        (tc/select-columns [:col-name :skew :mean])
        (tc/add-column :czech-author (fn [ds] (map helpers/czech-author? (ds :col-name)))))))

^:kindly/hide-code
(def scatter-plot
  (kind/plotly
   {:data (mapv (fn [nat] (let [ds (tc/select-rows books-data #(= (:czech-author %) nat))]
                            {:name (if (zero? nat) "Foreign" "Czech")
                             :x (ds :mean)
                             :y (ds :skew)
                             :mode "markers+text"
                             :type "scatter"
                             :marker {:size 10 :color nat :opacity 0.8}
                             :textposition "top center"
                             :textfont {:size 10}
                             :hoverinfo "text"
                             :hovertext (map (fn [book] (str book)) (ds :col-name))}))
                [1 0])
    :layout {:title "Average correlation vs. skewness of books"
             :xaxis {:title "Average correlation with other books"
                     :zeroline true}
             :yaxis {:title "Skewness <br>(more positive → less generic relation)"
                     :zeroline true}
             :hovermode "closest" :showlegend true
             :height 650 :width 750
             :margin {:l 65 :r 50 :b 65 :t 90}
             :legend {:x 1 :y 1 :xanchor "right"}}}))

^:kindly/hide-code
scatter-plot

;; Foreign bestsellers (marked in orange) showed consistently higher correlations with other books. They had broad appeal and were purchased alongside many different titles. Czech authors (in blue), however, showed lower correlations, suggesting their readers were more focused. Many customers would buy just one Czech title, often using it as a "gateway" into our catalog (strongly supported by Czech author's local campaigns), while foreign bestsellers were part of larger, more diverse purchases.

;; This insight immediately changed our marketing approach. We stopped using generic cross-sell recommendations for Czech authors and instead focused on building author-specific communities and started to cooperate with easily reachable local Czech authors on cross-selling approach.

;; ## The Limitation: Correlations Weren't Enough

;; Correlations told me **what** books appeared together, but they couldn't answer crucial business questions:

;; - **Direction**: Does buying Book A lead to buying Book B, or vice versa?
;; - **Confidence**: If a customer buys Book A, what's the probability they'll buy Book B?
;; - **Practical significance**: Is this pattern strong enough to base recommendations on?

;; I needed something more powerful: **association rules**.

;; ## Enter the Apriori Algorithm

;; Association rule mining, powered by the Apriori algorithm, discovers patterns like "Customers who bought Book A tend to buy Book B with 65% confidence and 2.3× lift." These rules are **directional** and **measurable**—perfect for building a recommendation system.

;; The Apriori algorithm's elegance lies in its core insight: **"Any subset of a frequent itemset must also be frequent."** If the combination `{Beer, Chips, Salsa}` appears often, then `{Beer, Chips}` must appear at least as often. This simple observation allows the algorithm to prune billions of potential combinations efficiently.

;; ### The Clever Part: Avoiding Duplicates

;; One of the most elegant aspects of the Apriori implementation is how it generates larger itemsets from smaller ones without creating duplicates. Consider generating 3-item sets from 2-item sets:

;; ```
;; We want: [:book-a :book-b :book-c] ✓  
;; Not:     [:book-a :book-c :book-b] ✗ (same set, different order)  
;;          [:book-b :book-a :book-c] ✗ (same set, different order)  
;; ```

^:kindly/hide-code
(kind/code
 "(defn join-itemsets
  [frequent-itemsets k]
  (let [k-1 (dec k)
        ;; Only process itemsets of the correct size
        valid-sets (filter #(= (count %) k-1) frequent-itemsets)
        ;; Group by prefix (first k-2 elements) for efficiency
        by-prefix (group-by #(vec (take (- k 2) %)) valid-sets)]
    (mapcat
     (fn [[prefix items]]
       (for [set1 items
             set2 items
             :let [last1 (last set1)
                   last2 (last set2)]
             ;; Only join if last2 > last1 (enforces canonical order)
             :when (and (not= last1 last2)
                        (pos? (compare last2 last1)))]
         (concat prefix [last1 last2])))
     by-prefix)))")

;; The magic is in that `(pos? (compare last2 last1))` check—it ensures items are always combined in alphabetical order, preventing duplicates from ever being generated.

;; ## Understanding the Metrics

;; Association rules are evaluated using three key metrics:

;; **Support** measures how frequently an itemset appears:

;; $$  
;; \text{Support}(\{A, B\}) = \dfrac{\text{orders with A and B}}{\text{total orders}}  
;; $$ 

;; **Confidence** measures how often B appears when A is purchased:

;; $$ 
;; \text{Confidence}(A \rightarrow B) = \dfrac{\text{Support}(\{A, B\})}{\text{Support}(\{A\})}
;; $$ 

;; **Lift** measures whether this happens more than random chance:

;; $$ 
;; "\text{Lift}(A \rightarrow B) = \dfrac{\text{Confidence}(A \rightarrow B)}{\text{Support}(\{B\})}
;; $$ 

;; A lift greater than 1 indicates positive association—the items are purchased together more often than if they were independent. A lift of 2.3 means the combination is 2.3 times more likely than chance.

;; ## The Results: Actionable Rules

;; Running the Apriori algorithm on our book sales data produced rules like these:

^:kindly/hide-code
(def quick-analysis
  (tc/read-nippy "src/data_analysis/book_sales_analysis/quick-analysis-results-v2.nippy"))

^:kindly/hide-code
(def quick-formatted
  (mba/format-analysis-results quick-analysis :top-n 20))

(kind/table
 (tc/head (:rules-grouped quick-formatted) 15)
 {:element/max-height "500px"})

;; Reading these rules is straightforward. For example, if a customer buys Yuval Noah Harari's "Sapiens," there's a 72% chance they'll also purchase "Nexus" (another Harari title), and this combination is nearly twice as likely as random chance (lift = 1.99).

;; ## Visualizing the Network

;; Perhaps the most compelling representation of these patterns is a network graph, where books are nodes and rules are edges:

^:kindly/hide-code
(kind/graphviz
 (mba/create-graphviz-visualization quick-analysis :limit 32 :scale 0.2))

;; This visualization reveals clusters of books that customers buy together, forming natural "reading paths" through our catalog. The thickness of edges represents lift (stronger associations), while node darkness indicates support (popularity).

;; (Remember this data comes from part of the dataset with particular parameters and tresholds.)

;; ## From Analysis to Production

;; The final piece was building a prediction function that could recommend books based on a customer's purchase history:

^:kindly/hide-code
(kind/code
 "(defn predict-next-book-choice
   \"Predicts customer's next book based on their purchase history\"
   [rules customer-books & {:keys [top-n min-confidence]}]
   ;; Business-oriented relevance score:
   ;; - 80% weight on confidence (practicality)
   ;; - 20% weight on lift (interestingness)
   ;; - Bonus for support (popularity signal)
   ...)")

;; For example, if a customer has purchased **Přežít** (Peter Attia's *Outlive*) and **Skrytý potenciál** (Adam Grant's *Hidden Potential*), the system recommends:

^:kindly/hide-code
(def full-analysis
  (tc/read-nippy "src/data_analysis/book_sales_analysis/full-analysis-results-without-onehot.nippy"))

^:kindly/hide-code
(kind/table
 (tc/head
  (mba/predict-next-book-choice
   (:rules full-analysis)
   [:prezit :skryty-potencial]
   :top-n 10
   :min-confidence 0.1)
  8))

;; These recommendations are now powering a new "Customers Also Bought" section on our website (still in "manual" mode for now), complementing our existing "Topically Similar" recommendations with data-driven insights.

;; ## Why This Matters for the Clojure Community

;; This project demonstrates several strengths of Clojure and the SciCloj ecosystem for real-world data science:

;; **1. Readable transformations:** Clojure's threading macro (`->`) made complex data pipelines read like narratives. Each step tells a story, making the code understandable to both technical and even business stakeholders.

^:kindly/hide-code
(kind/code
 "(-> data/orders
    (tc/group-by :zakaznik)                    ;; Per customer
    (tc/aggregate {:total-books count-books})  ;; Count their purchases
    (tc/order-by :total-books :desc)           ;; Best customers first
    (tc/head 5))                               ;; Top 5")

;; **2. Interactive development:** Working in a REPL with Clay notebooks meant I could explore, visualize, and validate each step immediately. This tight feedback loop was essential for discovery.

;; **3. A complete stack:** From data manipulation (Tablecloth) to visualization (Tableplot) to presentation (Clay and Kindly), the SciCloj ecosystem provided everything I needed without leaving Clojure.

;; **4. Production-ready code:** The same code that powers my exploratory analysis can be run in production later, generating live recommendations for our website (I hope!).

;; ## The Impact

;; This project is still under construction and tangible business results have yet to be seen. But:

;; - We are already stopping less effective cross-selling campaigns and starting target author communities
;; - Our website now features more data-driven "Customers Also Bought" recommendations
;; - We use these insights to optimize B2B offers for corporate clients
;; - Our social media campaigns are being better targeted based on purchase pattern clusters

;; Most importantly, I learned that you don't need a data science team or expensive tools to extract value from your data. With curiosity, the right tools, and a supportive community (shout out to the SciCloj folks on Zulip!), even a beginner can turn raw data into actionable insights.

;; ---

;; ## About the Author

;; **Tomáš Baránek** is a publisher at [Jan Melvil Publishing](https://www.melvil.cz) and co-founder of [Servantes](https://www.servant.es), developing software for publishers worldwide. He's a computer science graduate, Clojure enthusiast exploring data science, learning by doing on real publishing challenges. You can find him on [Bluesky](https://bsky.app/profile/tombarys.bsky.social) or read his [blog](https://lifehacky.net).

;; **Resources:**
;; - Author: https://barys.me
;; - Full presentation code: *to be published*
;; - SciCloj community: [scicloj.github.io](https://scicloj.github.io)

;; ---

;; *This article is based on a presentation at Macroexpand conference, October 2025.*