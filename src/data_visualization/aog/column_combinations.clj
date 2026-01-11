^{:kindly/hide-code true
  :clay {:title "Visual data summaries"
         :quarto {:type :post
                  :author [:timothypratley]
                  :draft true
                  :date "2026-01-011"
                  :description "Can we plot interesting charts for all columns of a dataset?"
                  :category :data-visualization
                  :tags [:datavis :algebra]
                  :keywords [:datavis :composition :operators]}}}
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

;; Type detection: determines whether to show histograms (numeric) or bar charts (categorical)

(defn is-numeric-type? [col]
  (tcc/typeof? col :numerical))

(defn plot-basic [g]
  (let [{:keys [data mappings geometry]} (g 1)
        {:keys [x y]} mappings]
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
        :point (let [xys (mapv (juxt x y) data)]
                 (for [[x y] xys]
                   [:circle {:r 2, :cx x, :cy y, :fill "lightblue"}]))
        :line (let [xys (mapv (juxt x y) data)]
                [:path {:d (str "M " (str/join ","
                                               (first xys))
                                " L " (str/join " "
                                                (map #(str/join "," %)
                                                     (rest xys))))}])))))

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

;; ## Single Column Summaries
;;
;; The summarize function automatically selects the right visualization type:

;; - Numeric columns → histogram (shows distribution shape)
;; - Categorical columns → bar chart (shows frequencies)

(defn summarize [ds column]
  (if (is-numeric-type? (ds column))
    (plot-distribution ds column [:histogram])
    (plot-distribution ds column [:bar])))

;; Companion function: provides numeric summaries alongside visualizations
;; Shows count, mean, standard deviation, min/max for numeric data
;; Shows count and unique values for categorical data

(defn get-summary-stats [ds column]
  (let [col (ds column)]
    (if (is-numeric-type? col)
      (let [stats (tcc/descriptive-statistics col)]
        (format "n: %d, μ: %.2f, σ: %.2f, min: %.2f, max: %.2f"
                (:n-elems stats)
                (:mean stats)
                (:standard-deviation stats)
                (:min stats)
                (:max stats)))
      (let [values (tcc/drop-missing col)
            counts (frequencies values)]
        (str "n: " (count values) ", unique: " (count counts))))))

;; ## Summary Table: All Columns at a Glance
;;
;; Combines visualization + statistics for every column.
;; This gives us a complete overview of the dataset's structure.

(defn visual-summary [ds]
  (kind/table
   (doall (for [column-name (tc/column-names ds)]
            [column-name (summarize ds column-name) (get-summary-stats ds column-name)]))))

(visual-summary penguins)

;; ## Matrix View: All Column Combinations
;;
;; The next step: instead of showing each column separately,
;; what if we show how every column relates to every other column?
;; This is the idea behind the scatterplot matrix.
;;
;; The matrix automatically chooses the right chart for each combination:

;; - Numeric × Numeric → scatter plot (reveal relationships)
;; - Otherwise → bar chart (show distribution differences)

(defn matrix [ds]
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
              a-numeric? (is-numeric-type? col-a)
              b-numeric? (is-numeric-type? col-b)]
          [:g {:transform (str "translate(" (* a-idx plot-width) "," (* b-idx plot-height) ")")}
           [:rect {:x 0 :y 0 :width plot-width :height plot-height
                   :fill "none" :stroke "gray" :stroke-width 1}]
           (plot-basic [:graphic {:data ds
                                  :mappings {:x a :y b}
                                  :geometry (cond
                                              (and a-numeric? b-numeric?) [:point]
                                              :else [:bar])}])]))]]))

(matrix penguins)
