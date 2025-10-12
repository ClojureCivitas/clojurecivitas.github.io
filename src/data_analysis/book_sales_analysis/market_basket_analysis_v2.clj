^:kindly/hide-code
(ns data-analysis.book-sales-analysis.market-basket-analysis-v2
  "Market Basket Analysis using Apriori algorithm - Presentation Version
   
   This version is optimized for presentations with:
   - Split functions for better REPL workflow
   - Pre-computed results that can be reused
   - Strategic code reveals for teaching moments
   - Presenter notes as comments"
  (:require [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [clojure.math.combinatorics :as combo]
            [clojure.string :as str]
            [clojure.set]
            [scicloj.tableplot.v1.plotly :as plotly]
            [scicloj.kindly.v4.kind :as kind]
            [data-analysis.book-sales-analysis.data-sources-v2 :as data]
            [data-analysis.book-sales-analysis.core-helpers-v2 :as helpers]))

;; # Market Basket Analysis
;; ## Discovering Customer Purchase Patterns

;; ## Why Market Basket Analysis?
;;
;; Correlations told us which books are bought together, but not **why** or **in what order**.
;; 
;; **Market Basket Analysis** reveals **directional rules**:
;; - "Customers who bought Book A **tend to also buy** Book B"
;; - Confidence: How often does this happen?
;; - Lift: Is this more than random chance?
;;
;; This lets us make **actionable recommendations** for our website.

;; ## The Apriori Algorithm
;;
;; The Apriori algorithm is our workhorse. It finds "frequent itemsets" - combinations
;; of books that appear together often enough to be interesting.
;;
;; The key insight: **"Any subset of a frequent itemset must also be frequent."**
;;
;; This lets us prune billions of possible combinations efficiently.

;; ### Step 1: Join Itemsets Efficiently
;;;; 
;; The challenge: Generate all k-item combinations from (k-1)-item combinations
;; **without creating duplicates**.
;;
;; Example: From `[[:A :B], [:A :C]]` generate `[:A :B :C]` but not `[:A :C :B]`
;; (they're the same set!)
;;
;; The solution: **Enforce canonical ordering**. Only generate combinations where
;; items are in alphabetical order. The `(pos? (compare last2 last1))` check ensures
;; we only create `[:A :B :C]` and skip redundant permutations.
;;
;; This isn't about finding duplicates after creation - it **prevents them from
;; ever being created** and later evaluated for support.

(defn join-itemsets
  "Itemset joining for Apriori algorithm.

   Generates k-itemsets from (k-1)-itemsets efficiently by:
   1. Grouping by common prefix (first k-2 elements)
   2. Joining pairs that share this prefix
   3. Enforcing ordering to prevent duplicate sets
   
   Example: [:A :B] + [:A :C] → [:A :B :C] (but not [:A :C :B])"
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
     by-prefix)))

;; ### Step 2: Generate Frequent Itemsets

^:kindly/hide-code
(defn generate-frequent-itemsets
  "Generates frequent itemsets using the Apriori algorithm.
   
   Parameters:
   - `dataset` – one-hot encoded dataset (books as columns, customers as rows)
   - `min-support` – minimum support threshold (e.g., 0.01 = 1% of customers)
   - `max-size` – maximum itemset size (default 3)
   
   Returns: Collection of all frequent itemsets across all sizes"
  ([dataset min-support] (generate-frequent-itemsets dataset min-support 3))
  ([dataset min-support max-size]
   (let [ds (-> dataset (tc/drop-columns :zakaznik))
         items (tc/column-names ds)
         ;; Step 1: Find frequent 1-itemsets (individual books)
         frequent-1-itemsets (->> items
                                  (filter #(>= (helpers/calculate-support dataset [%]) min-support))
                                  (map vector))
         ;; Step 2: Iteratively build larger itemsets
         join-and-prune (fn [candidates level]
                          (->> (join-itemsets candidates level)
                               (filter #(>= (helpers/calculate-support dataset %) min-support))))
         most-frequent-itemsets (loop [idx 2
                                       current-itemsets frequent-1-itemsets
                                       all-itemsets frequent-1-itemsets]
                                  (let [next-itemsets (join-and-prune current-itemsets idx)]
                                    (cond
                                      (or (> idx max-size) (not (seq next-itemsets))) all-itemsets
                                      :else (recur (inc idx)
                                                   next-itemsets
                                                   (concat all-itemsets next-itemsets)))))]
     most-frequent-itemsets)))

;; ### Step 3: Generate Association Rules

^:kindly/hide-code
(defn generate-association-rules
  "Generates association rules from frequent itemsets.
   
   For each frequent itemset, creates rules of the form:
   {antecedent} → {consequent}
   
   Calculates:
   - Support: How often does the full itemset appear?
   - Confidence: If antecedent is bought, what % also buy consequent?
   - Lift: How much more likely than random?
   
   Only returns rules meeting minimum confidence threshold."
  [dataset frequent-itemsets min-confidence]
  (->> frequent-itemsets
       (filter #(> (count %) 1))
       (mapcat (fn [itemset]
                 (let [support-itemset (helpers/calculate-support dataset itemset)]
                   (->> (combo/subsets itemset)
                        (remove #(or (empty? %) (= (set itemset) (set %))))
                        (map (fn [antecedent]
                               (let [consequent (clojure.set/difference (set itemset) (set antecedent))]
                                 {:antecedent (vec antecedent)
                                  :consequent consequent
                                  :support-itemset support-itemset
                                  :support-antecedent (helpers/calculate-support dataset antecedent)
                                  :support-consequent (helpers/calculate-support dataset consequent)})))
                        (map (fn [rule]
                               (assoc rule :confidence (double (/ (:support-itemset rule) (:support-antecedent rule))))))
                        (filter #(>= (:confidence %) min-confidence))
                        (map (fn [rule]
                               (-> (assoc rule :lift (/ (:confidence rule) (:support-consequent rule)))
                                   (select-keys [:antecedent :consequent :support-itemset :confidence :lift])
                                   (clojure.set/rename-keys {:support-itemset :support}))))))))))

;; ### Stage 1: Generate Analysis (The 15-Minute Step)

(defn generate-market-basket-analysis
  "Generates market basket analysis results.
   
   ⚠️ computationally expensive for bigger subset-size ⚠️
   Run once and bind the result to a `def` for reuse.
   
   Parameters:
   - `dataset` – raw orders dataset
   - `min-support` – minimum support threshold (default 0.01)
   - `min-confidence` – minimum confidence for rules (default 0.15)
   - `max-size` – maximum itemset size (default 2)
   - `subset-size` – number of rows to analyze (default: all)
   
   Returns: Map with :frequent-itemsets, :rules, and :onehot-dataset"
  [dataset & {:keys [subset-size min-support min-confidence max-size]
              :or {subset-size (tc/row-count dataset)
                   min-support 0.01 
                   min-confidence 0.15
                   max-size 2}}]
  (let [;; Prepare data
        orders-subset (-> dataset (tc/head subset-size))
        books-by-customer (helpers/onehot-encode-by-customers orders-subset)

        ;; Generate frequent itemsets and rules
        frequent-itemsets (generate-frequent-itemsets books-by-customer min-support max-size)
        rules (generate-association-rules books-by-customer frequent-itemsets min-confidence)

        ;; Apply popularity bias correction
        popularity-index (helpers/build-popularity-index books-by-customer)
        adaptive-coefficient (if (not (seq rules)) 0 (helpers/calculate-adaptive-coefficient rules popularity-index))
        adjusted-rules (map #(helpers/improved-adjusted-lift % popularity-index adaptive-coefficient) rules)]

    {:frequent-itemsets frequent-itemsets
     :rules (sort-by :lift > adjusted-rules)
     :onehot-dataset books-by-customer
     :meta {:itemsets-count (count frequent-itemsets)
            :rules-count (count rules)
            :min-support min-support
            :min-confidence min-confidence
            :max-size max-size}}))

;; ### Stage 2: Format Results for Display
(defn format-analysis-results
  "Formats analysis results into presentation-ready tables.
   
   Takes the output from `generate-market-basket-analysis` and creates:
   - Nice table with grouped antecedents
   - Summary statistics
   - Top rules for quick viewing
   
   This is instant - you can call it repeatedly with different options."
  [analysis-results & {:keys [top-n] :or {top-n 10}}]
  (let [rules (:rules analysis-results)
        meta-info (:meta analysis-results)
        rules-nice (-> (tc/dataset rules)
                       (tc/map-columns :consequent :consequent #(into [] %))
                       (tc/map-columns :antestr :antecedent 
                                      (fn [ant] (str/join " + " (map (comp str/capitalize name) ant))))
                       (tc/map-columns :consequents :consequent 
                                      (fn [con] (str/join " + " (map (comp str/capitalize name) con))))
                       (tc/map-columns :confidence-display :confidence 
                                      #(String/format "%.2f %%" (to-array [(* 100 %)])))
                       (tc/map-columns :lift-display :lift 
                                      #(str (String/format "%.1f " (to-array [%])) 
                                           (when (< % 1) "❗") "×"))
                       (tc/select-columns [:antestr :consequents :confidence-display :lift-display :support :confidence :lift])
                       (tc/rename-columns {:confidence-display :confidence 
                                          :lift-display :lift}))]
    
    {:rules-table rules-nice
     :rules-grouped (-> (tc/group-by rules-nice :antestr)
                        (tc/drop-columns :antestr))
     :top-rules (take top-n rules)
     :summary {:itemsets (:itemsets-count meta-info)
               :rules (:rules-count meta-info)
               :min-support (:min-support meta-info)
               :min-confidence (:min-confidence meta-info)}
     :raw-rules rules}))

;; ### Stage 3: Create Visualizations

(defn create-graphviz-visualization
  "Creates GraphViz visualization of association rules network.
   
   This creates the network diagram that reveals hidden patterns in purchase behavior.
   
   Parameters:
   - `analysis-results` – output from generate-market-basket-analysis
   - `limit` – number of rules to include (default 200, try 20-50 for clearer view)
   - `scale` – graph scale factor (default 0.1, increase for bigger output)
   
   The visualization shows:
   - **Nodes** = book combinations (color intensity = support)
   - **Edges** = rules (thickness = lift strength, labels = confidence %)"
  [analysis-results & {:keys [limit scale] :or {limit 200 scale 0.1}}]
  (let [dataset (:onehot-dataset analysis-results)
        rules (:rules analysis-results)]

    (if (empty? rules)
      "digraph G {\n  node [style=filled, shape=ellipse];\n  \"No rules found\" [fillcolor=\"#f0f0f0\"];\n}"
      (let [top-rules (->> rules
                           (sort-by :lift >)
                           (take limit)
                           (map #(update % :consequent vec))
                           (mapv #(update % :antecedent (fn [a] (sort-by name a))))
                           (mapv #(update % :consequent (fn [c] (sort-by name c)))))

            nodes-set (into []
                            (comp
                             (mapcat (juxt :antecedent :consequent))
                             (remove nil?)
                             (distinct))
                            top-rules)

            supports (reduce (fn [acc items]
                               (assoc acc (vec items) (helpers/calculate-support dataset items)))
                             {}
                             nodes-set)
            support-values (vals supports)
            min-support (if (seq support-values) (apply min support-values) 0.0)
            max-support (if (seq support-values) (apply max support-values) 1.0)
            min-lift (apply min (map :lift top-rules))
            max-lift (apply max (map :lift top-rules))

            node-defs (map #(str "  \""
                                 %
                                 "\" [label=\"" (str/join " + \\n" (map (comp str/capitalize name) %)) "\""
                                 ", fillcolor=\"" (helpers/color-hex (helpers/calculate-support dataset %) min-support max-support) "\""
                                 ", color=\"" (helpers/color-hex (helpers/calculate-support dataset %) min-support max-support) "\""  ;; Match border to fill color
                                 ", width=" (+ 0.8 (* 0.12 (count %)))
                                 ", height=0.4"
                                 ", margin=\"0.15,0.1\""
                                 ", pin=false"
                                 "];")
                           nodes-set)

            edge-defs (map #(let [normalized-lift (/ (- (:lift %) min-lift)
                                                     (max 0.1 (- max-lift min-lift)))
                                  edge-len (+ 6.0 (* 12.0 normalized-lift))
                                  edge-weight (max 0.05 (- 0.5 (* 0.3 normalized-lift)))]
                              (str "  \"" (:antecedent %) "\" -> \"" (:consequent %) "\""
                                   " [label=\"" (format "%.0f%%" (* 100 (:confidence %))) "\\n"
                                   (format "%.1f×" (:lift %)) "\""
                                   ", len=" edge-len
                                   ", weight=" edge-weight
                                   ", fontsize=8"
                                   ", fontname=\"Helvetica\""
                                   ", penwidth=" (max 0.5 (* 1.5 normalized-lift))
                                   ", minlen=3"
                                   "];"))
                           top-rules)

            graphviz-def (str "digraph AssociationRules {\n"
                              "  layout=neato;\n"
                              "  bgcolor=transparent;\n"
                              "  overlap=false;\n"
                              "  sep=\"+100,100\";\n"
                              "  splines=true;\n"
                              "  concentrate=false;\n"
                              "  scale=" scale ";\n"
                              "  pad=\"1.0,1.0\";\n"
                              "  size=\"20,16\";\n"
                              "  ratio=auto;\n"
                              "  start=random;\n"
                              "  epsilon=0.0001;\n"
                              "  maxiter=5000;\n"
                              "  node [shape=rectangle, style=filled, fontname=\"Helvetica\", fontsize=9];\n"
                              "  edge [color=\"#c1556b\", arrowhead=vee, arrowsize=0.5, splines=curved];\n"
                              (str/join "\n" node-defs) "\n"
                              (str/join "\n" edge-defs) "\n"
                              "}\n")]
        graphviz-def))))


;; ## Prediction Function

^:kindly/hide-code
(defn predict-next-book-choice
  "Predicts customer's next book choice based on association rules and purchase history.
   
   Parameters:
   - `rules` – Collection of association rules
   - `customer-books` – Books the customer has already bought
   - Options: `:top-n` (default 5), `:min-confidence` (default 0.1)
   
   Returns: Ranked predictions with relevance scores
   
   The relevance score uses a business-oriented formula:
   - 80% weight on confidence (practical likelihood)
   - 20% weight on lift (interestingness)
   - Bonus for support (popularity signal)"
  [rules customer-books & {:keys [top-n min-confidence] :or {top-n 5 min-confidence 0.1}}]
  (let [customer-book-set (set customer-books)
        expanded-predictions
        (->> rules
             (filter #(and (>= (:confidence %) min-confidence)
                           (every? customer-book-set (:antecedent %))))
             (mapcat (fn [rule]
                       (map (fn [consequent-item]
                              (let [confidence (:confidence rule)
                                    lift (:lift rule)
                                    support (:support rule)
                                    business-score (+ (* 0.8 confidence)
                                                      (* 0.2 (min 2.0 lift))
                                                      (* (Math/sqrt support)))]
                                {:antecedent-books (str/join ", " (map (comp str/capitalize name) (:antecedent rule)))
                                 :predicted-book consequent-item
                                 :confidence confidence
                                 :lift lift
                                 :support support
                                 :relevance-score business-score}))
                            (:consequent rule))))
             (remove #(customer-book-set (:predicted-book %))))]
    (if (empty? expanded-predictions)
      (-> (tc/dataset {})
          (tc/set-dataset-name "Next Book Predictions (No matches found)"))
      (let [aggregated (-> expanded-predictions
                           tc/dataset
                           (tc/group-by :predicted-book)
                           (tc/aggregate {:book #(first (tc/column % :predicted-book))
                                          :avg-confidence #(tcc/mean (tc/column % :confidence))
                                          :avg-lift #(tcc/mean (tc/column % :lift))
                                          :max-relevance #(tcc/max (tc/column % :relevance-score))
                                          :supporting-rules #(tc/row-count %)
                                          :example-antecedent #(first (tc/column % :antecedent-books))})
                           (tc/drop-columns :$group-name))
            relevance-col (first (filter #(str/starts-with? (name %) "max-relevance") (tc/column-names aggregated)))]
        (-> aggregated
            (tc/order-by relevance-col :desc) 
            (tc/head top-n)
            (tc/map-columns :book #(str/capitalize (name %)))
            (tc/map-columns :confidence-% :avg-confidence #(format "%.1f%%" (* 100 %)))
            (tc/map-columns :lift-factor :avg-lift #(format "%.1f×" %))
            (tc/map-columns :relevance relevance-col #(format "%.2f" %))
            (tc/select-columns [:book :confidence-% :lift-factor :relevance :supporting-rules :example-antecedent])
            (tc/set-dataset-name "Next Book Predictions"))))))

;; ---
;; # Presentation: Live Examples
;; ---

;; ## Quick Demo (6,000 orders - runs in ~1 minute)

^:kindly/hide-code
(comment
  ;; Generate once - takes ~1 minute
  (def quick-analysis
    (generate-market-basket-analysis data/orders-slides 
                                     :subset-size 6000 
                                     :min-confidence 0.03))

  ;; Save to file for safety
  (tc/write! quick-analysis "quick-analysis-results-v2.nippy")
  
  ;; Load if needed
  (def quick-analysis
    (tc/read-nippy "quick-analysis-results-v2.nippy"))
  
  
  ;; Format results 
  (def quick-formatted
    (format-analysis-results quick-analysis :top-n 10))
  
  ;; Create visualizations 
  (def quick-viz-small
    (create-graphviz-visualization quick-analysis :limit 40 :scale 0.2))
  
  ;; Show small visualization
  (kind/graphviz quick-viz-small)

  (def quick-viz-full
    (create-graphviz-visualization quick-analysis :limit 200 :scale 0.15))

  (kind/graphviz quick-viz-full)
)

;; ## Full Analysis (All orders - runs in ~15 minutes)


^:kindly/hide-code
(comment
  ;; Pre-generate the night before - takes ~15 minutes
  (def full-analysis
    (generate-market-basket-analysis data/orders-slides :min-confidence 0.15 :min-support 0.003))
  
  ;; Save to file for safety
  (tc/write! full-analysis "full-analysis-results.nippy")
  
  ;; Load if needed
  (def full-analysis
    (tc/read-nippy "full-analysis-results.nippy"))
  
  ;; Format and visualize 
  (def full-formatted
    (format-analysis-results full-analysis :top-n 20))
  
  (def full-viz
    (create-graphviz-visualization full-analysis :limit 200))
  
  ;; Export GraphViz for external rendering
  (spit "apriori-full.dot" full-viz)
  )

;; ## Show Results

;; ### Summary Statistics

^:kindly/hide-code
(comment
  (kind/table
   (tc/dataset {:metric ["Frequent Itemsets" "Association Rules" "Min Support" "Min Confidence"]
                :value [(:itemsets (:summary quick-formatted))
                        (:rules (:summary quick-formatted))
                        (:min-support (:summary quick-formatted))
                        (:min-confidence (:summary quick-formatted))]})))

;; ### Top Rules (Grouped by Antecedent)

^:kindly/hide-code
(comment
  (kind/table
   (:rules-grouped quick-formatted)
   {:element/max-height "600px"}))

;; ### Network Visualization

^:kindly/hide-code
(comment
  (kind/graphviz quick-viz-small))  ;; Start with small, clear view

^:kindly/hide-code
(comment
  (kind/graphviz quick-viz-full))   ;; Then show full complexity

;; ### Prediction Demo

;; 🎯 **LIVE DEMO**: Show how the website will make recommendations

^:kindly/hide-code
(comment
  ;; Example: Customer who bought these two books

  (def quick-analysis
    (tc/read-nippy "quick-analysis-results.nippy"))

  (kind/table
   (predict-next-book-choice (:rules quick-analysis)
                             [:vas-kapesni-terapeut :k5-principu-rodicovstvi]
                             :top-n 10
                             :min-confidence 0.15))
  )