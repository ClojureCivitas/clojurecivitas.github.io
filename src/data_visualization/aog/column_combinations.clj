^{:kindly/hide-code true
  :clay {:title "Visual data summaries"
         :quarto {:type :post
                  :author [:timothypratley]
                  :draft true
                  :date "2026-01-011"
                  :description "Can we plot interesting charts for all columns of a dataset?"
                  :category :data-visualization
                  :tags [:datavis]
                  :keywords [:datavis]}}}
(ns data-visualization.aog.column-combinations
  (:require
   [clojure.string :as str]
   [fastmath.stats :as fms]
   [scicloj.kindly.v4.kind :as kind]
   [scicloj.metamorph.ml.rdatasets :as rdatasets]
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]))

;; When exploring a new dataset, we face an immediate challenge:
;; How do we quickly understand the structure and distribution of all our columns?
;;
;; This notebook explores a "show everything" approach:

;; - Visual summaries for each column (distributions, categories)
;; - Summary statistics paired with visualizations
;; - A scatterplot-matrix-like view showing all column combinations
;;
;; The goal is to enable rapid visual discovery of patterns and relationships.

;; ## Starting with a Complete Dataset
;;
;; Let's load a well-known dataset and explore how to present its columns effectively:

(def penguins
  (tc/drop-missing (rdatasets/palmerpenguins-penguins)))

;; ### Option 1: Print the data

;; We could just print the first few rows, but that only shows a small sample:

penguins

;; ### Option 2: Summary statistics
;; We could compute statistics for a single column:

(fms/stats-map (:bill-length-mm penguins))

;; But this requires mental effort to visualize what the numbers mean.
;;
;; ### Option 3: Visual summaries

;; What if we automatically plot the distribution of every column?
;; This lets us see patterns at a glance.

;; ## Visualization inference

(def plot-width 100)
(def plot-height 100)

;; ## Type Classification and Plot Selection
;;
;; We use tablecloth's type system to classify columns and make intelligent
;; visualization choices based on their semantic role in the data:
;;
;; - **Quantitative**: numerical data with meaningful magnitude and order
;; - **Temporal**: datetime/time data (often paired with quantitative for time series)
;; - **Categorical**: textual or logical data with discrete values
;; - **Identity/Index**: all values unique or nearly unique (IDs, timestamps, etc.)

(defn column-general-type
  "Returns the general type category of a column: :quantitative, :temporal, :categorical, or :identity."
  [col]
  (cond
    (tcc/typeof? col :numerical) :quantitative
    (tcc/typeof? col :datetime) :temporal
    (tcc/typeof? col :logical) :categorical
    (tcc/typeof? col :textual) :categorical
    :else :identity))

(defn cardinality
  "Count of unique non-missing values in a column."
  [col]
  (let [values (tcc/drop-missing col)]
    (count (set values))))

(defn is-identity-column?
  "Returns true if column appears to be an identity/index (all or nearly all unique values)."
  [col]
  (let [values (tcc/drop-missing col)
        n (count values)]
    (>= (cardinality col) (* 0.95 n))))  ;; 95%+ unique values

(defn is-numeric-type?
  "Convenience function: returns true for quantitative columns."
  [col]
  (= :quantitative (column-general-type col)))

(defn get-numeric-domain
  "Get min and max of numeric column for scaling."
  [col]
  (let [values (remove nil? (tcc/drop-missing col))]
    (when (seq values)
      {:min (apply min values)
       :max (apply max values)})))

(defn scale-value
  "Scale a value from domain to plot range."
  [value domain plot-min plot-max]
  (when (and value domain)
    (let [{:keys [min max]} domain
          range (- max min)]
      (if (zero? range)
        (/ (+ plot-min plot-max) 2)
        (+ plot-min (* (/ (- value min) range) (- plot-max plot-min)))))))

(defn plot-basic [g]
  (let [{:keys [data mappings geometry]} (g 1)
        {:keys [x y]} mappings
        x-col (data x)
        y-col (data y)
        x-domain (when (tcc/typeof? x-col :numerical) (get-numeric-domain x-col))
        y-domain (when (tcc/typeof? y-col :numerical) (get-numeric-domain y-col))]
    (for [geom geometry]
      (case geom
        :bar (let [x-vals (remove nil? (data x))
                   categories (distinct x-vals)
                   counts (frequencies x-vals)
                   max-count (when (seq counts) (apply max (vals counts)))
                   bar-width (/ plot-width (count categories))]
               (when max-count
                 (for [[i cat] (map-indexed vector categories)]
                   (let [count (get counts cat 0)
                         bar-height (* (/ count max-count) plot-height)]
                     [:rect {:x (* i bar-width)
                             :y (- plot-height bar-height)
                             :width bar-width
                             :height bar-height
                             :fill "lightblue"
                             :stroke "gray"
                             :stroke-width 0.5}]))))
        :heatmap (let [x-vals (remove nil? (data x))
                       y-vals (remove nil? (data y))
                       x-cats (distinct x-vals)
                       y-cats (distinct y-vals)
                       x-count (count x-cats)
                       y-count (count y-cats)
                       ;; Build contingency table
                       contingency (reduce (fn [acc i]
                                             (assoc-in acc [(data y i) (data x i)]
                                                        (inc (get-in acc [(data y i) (data x i)] 0))))
                                           {} (range (tc/row-count data)))
                       all-counts (for [y-cat y-cats x-cat x-cats] (get-in contingency [y-cat x-cat] 0))
                       max-count (when (seq all-counts) (apply max all-counts))
                       cell-width (/ plot-width x-count)
                       cell-height (/ plot-height y-count)]
                   (when (and max-count (> max-count 0))
                     (for [[y-idx y-cat] (map-indexed vector y-cats)
                           [x-idx x-cat] (map-indexed vector x-cats)]
                       (let [count (get-in contingency [y-cat x-cat] 0)
                             intensity (/ count max-count)
                             color (str "rgba(70, 130, 180, " intensity ")")]
                         [:rect {:x (* x-idx cell-width)
                                 :y (* y-idx cell-height)
                                 :width cell-width
                                 :height cell-height
                                 :fill color
                                 :stroke "gray"
                                 :stroke-width 0.5}]))))
        :histogram (let [values (remove nil? (data x))
                         hist-result (when (seq values) (fms/histogram values))
                         bins (:bins-maps hist-result)]
                     (when (seq bins)
                       (let [max-count (apply max (map :count bins))
                             bin-width (/ plot-width (count bins))]
                         (for [[i bin] (map-indexed vector bins)]
                           (let [bar-height (* (/ (:count bin) max-count) plot-height)]
                             [:rect {:x (* i bin-width)
                                     :y (- plot-height bar-height)
                                     :width bin-width
                                     :height bar-height
                                     :fill "lightblue"
                                     :stroke "gray"
                                     :stroke-width 0.5}])))))
        :point (let [rows (tc/rows data :as-maps)
                     xys (mapv (juxt x y) rows)]
                 (for [[x-val y-val] xys]
                   (when (and x-val y-val x-domain y-domain)
                     (let [cx (scale-value (double x-val) x-domain 5 (- plot-width 5))
                           cy (scale-value (double y-val) y-domain (- plot-height 5) 5)]
                       (when (and cx cy)
                         [:circle {:r 2, :cx cx, :cy cy, :fill "lightblue" :stroke "blue" :stroke-width 0.5}])))))
        :line (let [rows (tc/rows data :as-maps)
                    xys (mapv (juxt #(get % x) #(get % y)) rows)
                    scaled-xys (for [[x-val y-val] xys]
                                 (when (and x-val y-val x-domain y-domain)
                                   [(scale-value (double x-val) x-domain 5 (- plot-width 5))
                                    (scale-value (double y-val) y-domain (- plot-height 5) 5)]))
                    valid-xys (remove nil? scaled-xys)]
                (when (seq valid-xys)
                  [:path {:d (str "M " (str/join ","
                                                  (first valid-xys))
                                  " L " (str/join " "
                                                   (map #(str/join "," %)
                                                        (rest valid-xys))))
                          :stroke "lightblue"
                          :fill "none"
                          :stroke-width 0.5}]))
        :identity (let [values (remove nil? (data x))
                        unique-vals (distinct values)
                        n (count unique-vals)
                        point-spacing (/ plot-width n)]
                    (for [[i val] (map-indexed vector unique-vals)]
                      [:circle {:r 1.5
                                :cx (* (+ i 0.5) point-spacing)
                                :cy (/ plot-height 2)
                                :fill "lightgray"}]))))))

(defn plot-distribution [ds column geom]
  ^:kind/hiccup
  [:svg {:width   100
         :viewBox (str/join " " [0 0 plot-width plot-height])
         :xmlns   "http://www.w3.org/2000/svg"
         :style {:border "solid 1px gray"}}
   [:g {:stroke "gray", :fill "none"}
    (plot-basic [:graphic {:data ds
                           :mappings {:x column}
                           :geometry geom}])]])

(plot-distribution penguins :bill-length-mm [:histogram])

;; ## Geometry Selection
;;
;; Determines what type of chart works best for different data patterns

(defn select-geometry-single
  "Select visualization geometry for a single column based on its type and cardinality."
  [col]
  (let [general-type (column-general-type col)
        card (cardinality col)
        n (count (tcc/drop-missing col))]
    (cond
      ;; All/nearly all unique values → show domain, not distribution
      (is-identity-column? col) :identity
      ;; Quantitative → histogram shows distribution
      (= :quantitative general-type) :histogram
      ;; Temporal → histogram of counts also works
      (= :temporal general-type) :histogram
      ;; Categorical → bar chart shows frequencies
      (= :categorical general-type) :bar
      :else :bar)))

(defn select-geometry-pair
  "Select visualization geometry for a pair of columns based on their types."
  [col-a col-b]
  (let [type-a (column-general-type col-a)
        type-b (column-general-type col-b)]
    (cond
      ;; Same column (diagonal) → sparse identity plot
      (= col-a col-b) :identity
      ;; Quantitative × Quantitative → scatter plot reveals correlation
      (and (= :quantitative type-a) (= :quantitative type-b)) :point
      ;; Temporal × Quantitative → line chart shows time series
      (and (= :temporal type-a) (= :quantitative type-b)) :line
      (and (= :quantitative type-a) (= :temporal type-b)) :line
      ;; Categorical × Categorical → heatmap shows contingency
      (and (= :categorical type-a) (= :categorical type-b)) :heatmap
      ;; Categorical × Anything else → bar chart (show distribution by category)
      (or (= :categorical type-a) (= :categorical type-b)) :bar
      ;; Fallback
      :else :bar)))

;; ## Pair test

(defn plot-pair
  "Visualization for a pair of columns with automatic geometry selection."
  [ds column-a column-b]
  ^:kind/hiccup
  [:svg {:width   100
         :viewBox (str/join " " [0 0 plot-width plot-height])
         :xmlns   "http://www.w3.org/2000/svg"
         :style {:border "solid 1px gray"}}
   [:g {:stroke "gray", :fill "none"}
    (plot-basic [:graphic {:data ds
                           :mappings {:x column-a, :y column-b}
                           :geometry [(select-geometry-pair (ds column-a) (ds column-b))]}])]])

(plot-pair penguins :bill-length-mm :bill-depth-mm)


;; ## Single Column Summaries
;;
;; The summarize function automatically selects the right visualization type:
;; - Quantitative columns → histogram (shows distribution shape)
;; - Temporal columns → histogram (shows frequency distribution)
;; - Categorical columns → bar chart (shows frequencies)
;; - Identity columns → sparse plot (shows all unique values)

(defn summarize
  "Generate a single-column visualization with appropriate geometry."
  [ds column]
  (let [col (ds column)
        geom (select-geometry-single col)]
    (plot-distribution ds column [geom])))

;; Companion function: provides numeric summaries alongside visualizations
;; Shows count, mean, standard deviation, min/max for quantitative data
;; Shows count and cardinality for categorical and identity data

(defn get-summary-stats
  "Generate summary statistics appropriate to the column's type."
  [ds column]
  (let [col (ds column)
        general-type (column-general-type col)]
    (if (= :quantitative general-type)
      (let [stats (tcc/descriptive-statistics col)]
        (format "n: %d, μ: %.2f, σ: %.2f, min: %.2f, max: %.2f"
                (:n-elems stats)
                (:mean stats)
                (:standard-deviation stats)
                (:min stats)
                (:max stats)))
      (let [values (tcc/drop-missing col)
            card (cardinality col)]
        (str "n: " (count values) ", card: " card)))))

;; ## Summary Table: All Columns at a Glance
;;
;; Combines visualization + statistics for every column.
;; This gives us a complete overview of the dataset's structure.

(defn visual-summary [ds]
  (kind/table
   (doall (for [column-name (tc/column-names ds)]
            [column-name (summarize ds column-name) (get-summary-stats ds column-name)]))))

(defn visual-summary-grid
  "Grid layout with columns as vertical strips, each showing name + viz + stats."
  [ds]
  ^:kind/hiccup
  [:div {:style {:display "grid"
                 :grid-template-columns "repeat(auto-fit, minmax(150px, 1fr))"
                 :gap "10px"
                 :padding "10px"}}
   (doall (for [column-name (tc/column-names ds)]
            [:div {:style {:border "1px solid #ddd"
                           :padding "10px"
                           :text-align "center"}}
             [:h4 {:style {:margin "0 0 10px 0"
                           :font-size "14px"}} (name column-name)]
             (summarize ds column-name)
             [:div {:style {:margin-top "10px"
                            :font-size "12px"
                            :color "#666"}}
              (get-summary-stats ds column-name)]]))])

(defn visual-summary-cards
  "Bootstrap card layout with each column as a card in a responsive grid."
  [ds]
  ^:kind/hiccup
  [:div {:class "container-fluid"}
   [:div {:class "row"}
    (doall (for [column-name (tc/column-names ds)]
             [:div {:class "col-md-4 col-lg-3 mb-3"}
              [:div {:class "card h-100"}
               [:div {:class "card-header"}
                [:h5 {:class "card-title mb-0"} (name column-name)]]
               [:div {:class "card-body text-center d-flex flex-column justify-content-center"}
                (summarize ds column-name)]
               [:div {:class "card-footer mt-auto"}
                [:small {:class "text-muted"}
                 (get-summary-stats ds column-name)]]]]))]])

(defn visual-summary-rows
  "Row-based layout with each column getting a full-width row."
  [ds]
  ^:kind/hiccup
  [:div {:style {:max-width "800px"
                 :margin "0 auto"}}
   (doall (for [column-name (tc/column-names ds)]
            [:div {:style {:border "1px solid #ddd"
                           :margin-bottom "20px"
                           :padding "15px"
                           :border-radius "5px"}}
             [:div {:style {:display "flex"
                            :align-items "center"
                            :gap "20px"}}
              [:div {:style {:flex "1 1 auto"}}
               [:h4 {:style {:margin "0"}} (name column-name)]
               [:div {:style {:margin-top "5px"
                              :font-size "12px"
                              :color "#666"}}
                (get-summary-stats ds column-name)]]
              [:div {:style {:flex "0 0 auto"
                             :text-align "right"}}
               (summarize ds column-name)]]]))])

(visual-summary penguins)

(visual-summary-grid penguins)

(visual-summary-cards penguins)

(visual-summary-rows penguins)

;; ## Matrix View: All Column Combinations
;;
;; The next step: instead of showing each column separately,
;; what if we show how every column relates to every other column?
;; This is the idea behind the scatterplot matrix.
;;
;; The matrix automatically chooses the right chart for each combination:
;; - Quantitative × Quantitative → scatter plot (reveal correlations)
;; - Temporal × Quantitative → line chart (show time series)
;; - Categorical × Anything → bar chart (show distribution by category)
;; - Single column (diagonal) → histogram/bar based on type

(defn matrix
  "Create a scatterplot-matrix-style view of all column combinations.
  Each cell uses an appropriate visualization based on the column types."
  [ds]
  (let [column-names (tc/column-names ds)
        c (count column-names)]
    ^:kind/hiccup
    [:svg {:width   "100%"
           :viewBox (str/join " " [0 0 (* plot-width c) (* plot-height c)])
           :xmlns   "http://www.w3.org/2000/svg"
           :style {:border "solid 1px gray"}}
     [:g {:stroke "gray", :fill "none"}
      (for [[a-idx a] (map-indexed vector column-names)
            [b-idx b] (map-indexed vector column-names)]
        (let [col-a (ds a)
              col-b (ds b)
              geom (select-geometry-pair col-a col-b)]
          [:g {:transform (str "translate(" (* a-idx plot-width) "," (* b-idx plot-height) ")")}
           [:rect {:x 0 :y 0 :width plot-width :height plot-height
                   :fill "none" :stroke "gray" :stroke-width 1}]
           (plot-basic [:graphic {:data ds
                                  :mappings {:x a :y b}
                                  :geometry [geom]}])]))]]))

(matrix penguins)


(defn labeled-matrix
  "Create a scatterplot-matrix-style view with row/column labels.
  Each cell uses an appropriate visualization based on the column types."
  [ds]
  (let [column-names (tc/column-names ds)
        c (count column-names)
        label-width 80
        cell-size plot-width]
    ^:kind/hiccup
    [:svg {:width   "100%"
           :viewBox (str/join " " [0 0 (+ label-width (* cell-size c)) (+ label-width (* cell-size c))])
           :xmlns   "http://www.w3.org/2000/svg"
           :style {:border "solid 1px gray"}}
     [:g
      ;; Column labels (top)
      (for [[idx col-name] (map-indexed vector column-names)]
        [:g {:key (str "col-label-" idx)}
         [:text {:x (+ label-width (* idx cell-size) (/ cell-size 2))
                 :y 20
                 :text-anchor "middle"
                 :font-size "12"
                 :fill "#333"} (name col-name)]])

      ;; Row labels (left)
      (for [[idx row-name] (map-indexed vector column-names)]
        [:g {:key (str "row-label-" idx)}
         [:text {:x 10
                 :y (+ label-width (* idx cell-size) (/ cell-size 2) 4)
                 :font-size "12"
                 :fill "#333"} (name row-name)]])

      ;; Grid cells
      (for [[a-idx a] (map-indexed vector column-names)
            [b-idx b] (map-indexed vector column-names)]
        (let [col-a (ds a)
              col-b (ds b)
              geom (select-geometry-pair col-a col-b)]
          [:g {:key (str "cell-" a-idx "-" b-idx)
               :transform (str "translate(" (+ label-width (* a-idx cell-size)) "," (+ label-width (* b-idx cell-size)) ")")}
           [:rect {:x 0 :y 0 :width cell-size :height cell-size
                   :fill "none" :stroke "gray" :stroke-width 1}]
           (plot-basic [:graphic {:data ds
                                  :mappings {:x a :y b}
                                  :geometry [geom]}])]))]]))

(labeled-matrix penguins)
