^{:kindly/hide-code true
  :clay {:title "Composable Plotting in Clojure"
         :quarto {:type :post
                  :author [:daslu :claude]
                  :date "2026-02-22"
                  :description "Composable plotting with wadogo scales, stat-driven domains, and a single coord function"
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :design :wadogo]
                  :keywords [:datavis]
                  :toc true
                  :toc-depth 2
                  :image "aog_iris.png"
                  :draft true}}}
(ns data-visualization.aog-in-clojure-part2
  (:require [tablecloth.api :as tc]
            [scicloj.kindly.v4.kind :as kind]))

^{:kindly/hide-code true
  :kindly/kind :kind/hiccup}
[:style
 ".clay-dataset {
  max-height: 400px;
  overflow-y: auto;
}
.sourceCode.clojure {
  max-height: 500px;
  overflow-y: auto;
}
"]

;; ## Introduction
;;
;; Wilkinson's [Grammar of Graphics](https://link.springer.com/book/10.1007/0-387-28695-0)
;; (2005) describes statistical charts as a pipeline of composable components:
;; data transformations, variable mappings, geometric elements, scales,
;; coordinate systems, and guides. It also introduces algebraic operators,
;; cross (×), nest (/), and blend (+) for combining variables.
;;
;; Julia's [AlgebraOfGraphics.jl](https://aog.makie.org/stable/) applies
;; algebraic composition to plot specifications with two operators
;; (distinct from Wilkinson's, despite similar symbols): `*` merges
;; partial specs (dataset + mapping + mark), `+` layers them in one plot.
;; The library infers axes, legends, and layout from the composed result.
;;
;; This post explores what something similar could look like in Clojure,
;; using [Tablecloth](https://scicloj.github.io/tablecloth/) datasets and
;; SVG created from scratch. It is part of developing
;; [Tableplot](https://scicloj.github.io/tableplot/) in the
;; [Scicloj](https://scicloj.github.io/) community.
;;
;; > *This post is self-contained: everything needed is explained inline.
;; > [Implementing the Algebra of Graphics in Clojure](aog_in_clojure_part1.html) explores alternative
;; > composition operators and multi-target rendering, not required reading.*
;;
;; #### Background
;;
;; [Tableplot](https://scicloj.github.io/tableplot/) was created in mid-2024
;; as a plotting layer for the [Noj](https://scicloj.github.io/noj/) toolkit.
;; Its current APIs (wrapping [Vega-Lite](https://vega.github.io/vega-lite/)
;; and [Plotly](https://plotly.com/javascript/)) have been useful in real projects,
;; but we always intended to explore fresh designs alongside them.
;;
;; This exploration has been shaped by conversations with
;; Scicloj community members, in particular Cvetomir Dimov,
;; Timothy Pratley, Kira Howe, Jon Anthony, Adrian Smith,
;; respatialized, and others.
;; It takes place in the context of the
;; [Real-World Data dev group](https://scicloj.github.io/docs/community/groups/real-world-data/),
;; recently reinitiated by Timothy Pratley.
;;
;; #### Reading this document
;;
;; Section headers use emoji to indicate content type:
;; **📖** narrative, **⚙️** implementation, **🧪** examples.
;;
;; #### Design choices
;;
;; Three decisions shape the rendering:
;;
;; **1. Wadogo for scales.**
;; [Wadogo](https://github.com/generateme/wadogo) handles ticks, formatting,
;; band calculation, and log transforms.
;;
;; **2. Stat-driven domains.** Each stat returns `{:x-domain :y-domain}`
;; alongside its data. A histogram's y-domain is `[0 max-count]`, not the
;; raw data range, so axes are always correct.
;;
;; **3. Single coord function.** One function per coordinate system:
;; `[data-x data-y] -> [pixel-x pixel-y]`. All marks call it the same way.
;;
;; Everything renders to [SVG](https://en.wikipedia.org/wiki/SVG) via [Hiccup](https://github.com/weavejester/hiccup). The post builds up incrementally:
;; scatter plots, histograms, regression lines, bars,
;; multi-panel layouts, polar coordinates, and interactivity.
;; ---

;; ## Motivation
;;
;; The [SPLOM Tutorial](splom_tutorial.html) builds a colored
;; [scatterplot matrix](https://en.wikipedia.org/wiki/Scatter_plot#Scatter_plot_matrices)
;; with regression lines by hand: manual grid offsets, explicit scale
;; computation, per-panel rendering loops. The result is impressive
;; but the code is long and tightly coupled to one layout.
;;
;; Here is that 4x4 SPLOM, rendered from that tutorial:

(require '[data-visualization.splom-tutorial :as splom-tut])

;; :::{.column-screen-inset-right}
(kind/hiccup splom-tut/iris-splom-4x4)
;; ::: 

;; The goal of this post is to build a composable API where
;; a similar result requires only a few lines:
;;
;; ```clojure
;; (-> (views iris (cross iris-quantities iris-quantities))
;;     (layer {:color :species})
;;     (plot {:brush true}))
;; ```
;;
;; Everything that the SPLOM Tutorial does manually — grid layout,
;; scale sharing, color assignment, diagonal detection —
;; should follow from the composed specification.
;; ---

;; ## Glossary
;;
;; **Mark** -- a visual element: point, bar, line, or text.
;;
;; **View** -- a binding of data to column roles: which dataset,
;; which x, which y.
;;
;; **Layer** -- a mark applied to views. A scatter plot with
;; regression lines has two layers sharing the same data.
;;
;; **Stat** -- a statistical transform applied before drawing.
;; Binning produces a histogram, regression fits a line,
;; identity passes values through unchanged.
;;
;; **Scale** -- a mapping from one dimension of data to one axis.
;; A numeric scale turns value ranges into positions;
;; a categorical scale assigns bands.
;;
;; **Coord** -- a coordinate system that turns two scaled values
;; into a position on the canvas. Cartesian gives a standard grid,
;; flip swaps the axes, polar wraps them into angle and radius.
;;
;; **Panel** -- one subplot: background, grid, marks, and tick labels.
;;
;; **Facet** -- splitting views by a categorical column,
;; producing one panel per group.
;;
;; **Layout** -- how panels are arranged: a single plot,
;; a scatterplot matrix, or a faceted grid.
;; ---

;; ## Setup

;; ### ⚙️ Dependencies

(ns data-visualization.aog-in-clojure-part2
  (:require
   ;; Tablecloth - dataset manipulation
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   [tech.v3.datatype.functional :as dfn]

   ;; Kindly - notebook rendering protocol
   [scicloj.kindly.v4.kind :as kind]

   ;; Fastmath - regression and loess smoothing
   [fastmath.ml.regression :as regr]
   [fastmath.stats :as stats]
   [fastmath.interpolation :as interp]

   ;; Wadogo - scales (ticks, formatting, bands, log)
   [wadogo.scale :as ws]

   ;; RDatasets - sample datasets (iris, mpg, etc.)
   [scicloj.metamorph.ml.rdatasets :as rdatasets]

   [clojure.string :as str]))

;; ### 📖 Datasets

;;
;; Two datasets appear throughout: **iris** (150 flowers, 4 measurements
;; plus species) and **mpg** (234 cars, fuel economy and class).
;; Both come from [R datasets](https://vincentarelbundock.github.io/Rdatasets/).
(def iris (rdatasets/datasets-iris))
(def iris-quantities [:sepal-length :sepal-width :petal-length :petal-width])

iris

(def mpg (rdatasets/ggplot2-mpg))

mpg

;; ---

;; ## Composing Views
;;
;; Two approaches inspire this design:
;; Wilkinson's [Grammar of Graphics](https://www.google.com/books/edition/The_Grammar_of_Graphics/ZiwLCAAAQBAJ),
;; which combines variables through operators like cross, blend, and nest;
;; and Julia's [AlgebraOfGraphics.jl](https://aog.makie.org/stable/),
;; which composes visual specifications through `*` and `+`.
;; Our operators are shaped by Clojure's idioms (`merge`, `concat`,
;; threading) rather than by a direct translation of either system.
;; They work in two phases:
;;
;; | Phase              | Operators                                                    | Acts on                      |
;; |--------------------|--------------------------------------------------------------|------------------------------|
;; | **What** to plot   | `cross`, `views`                                             | column names to view maps     |
;; | **How** to plot    | `layer`, `layers`, layer specs (`point`, `bar`, `lm`, …) | view maps to richer view maps |
;;
;; Both phases operate on the same unit -- a flat vector of view maps --
;; so they compose freely through threading.

;; ### ⚙️ Cross

;;
;; `cross` produces all pairings of two sequences. Under the hood, just `for` --
;; naming it makes intent explicit.

(defn cross
  "Cartesian product of two sequences."
  [xs ys]
  (for [x xs, y ys] [x y]))

;; ### 🧪 Cross

(cross [:a :b] [:x :y])

;; With the iris columns, this produces 16 column pairs -- one per panel:

(cross iris-quantities iris-quantities)

;; ### ⚙️ Views

(defn- parse-view-spec
  "Parse a view spec: a keyword becomes a single-variable view (histogram),
  a vector becomes {:x ... :y ...}, a map passes through."
  [spec]
  (cond
    (keyword? spec) {:x spec :y spec}
    (map? spec) spec
    :else {:x (first spec) :y (second spec)}))

(def ^:private column-keys
  "View keys whose values are column names in the dataset."
  #{:x :y :color :size :shape})

(defn- validate-columns
  "Check that every column-referencing key in view-map names a real column in ds.
  Also usable as (validate-columns ds :facet col) for a single named check."
  ([ds view-map]
   (let [col-names (set (tc/column-names ds))]
     (doseq [k column-keys
             :let [col (get view-map k)]
             :when (and col (not (col-names col)))]
       (throw (ex-info (str "Column " col " (from " k ") not found in dataset. Available: " (sort col-names))
                       {:key k :column col :available (sort col-names)})))))
  ([ds role col]
   (let [col-names (set (tc/column-names ds))]
     (when-not (col-names col)
       (throw (ex-info (str "Column " col " (from " role ") not found in dataset. Available: " (sort col-names))
                       {:key role :column col :available (sort col-names)}))))))

(defn view
  "Create a single view from data and a column spec.
  Accepts (view data), (view data :col) for a single variable,
  (view data :x :y), (view data [:x :y]),
  or (view data {:x :a :y :b :color :c}).
  With no spec, defaults to columns :x and :y."
  ([data]
   (view data :x :y))
  ([data spec-or-x]
   (let [ds (if (tc/dataset? data) data (tc/dataset data))
         parsed (parse-view-spec spec-or-x)]
     (validate-columns ds parsed)
     [(assoc parsed :data ds)]))
  ([data x y]
   (let [ds (if (tc/dataset? data) data (tc/dataset data))
         v {:x x :y y}]
     (validate-columns ds v)
     [(assoc v :data ds)])))

(defn views
  "Create multiple views from data and a sequence of specs.
  Each spec can be a [x y] pair or a map with any view keys."
  [data specs]
  (let [ds (if (tc/dataset? data) data (tc/dataset data))]
    (mapv (fn [spec]
            (let [parsed (parse-view-spec spec)]
              (validate-columns ds parsed)
              (assoc parsed :data ds)))
          specs)))

;; ### 🧪 What a View Looks Like
;;
;; All three forms produce the same view:

(let [data {:a [1 2 3] :b [4 5 6]}]
  (kind/pprint
   {:from-keywords (view data :a :b)
    :from-pair (view data [:a :b])
    :from-map (view data {:x :a :y :b})}))

(-> {:x [1 2 3] :y [4 5 6] :z [7 8 9]}
    (views [[:x :y] [:x :z]])
    kind/pprint)

;; ### ⚙️ Layer
;;
;; `layer` merges a visual specification -- mark, stat, aesthetics --
;; into every view. This is the "how" phase: same data, different rendering.

(defn layer
  "Merge overrides into each view. This is how you add marks, aesthetics,
  and other properties to a set of views."
  [views overrides]
  (mapv (fn [v]
          (when (:data v)
            (validate-columns (:data v) overrides))
          (merge v overrides))
        views))

;; ### 🧪 Adding a Mark

;; `layer` merges a mark specification into every view:

(-> {:x [1 2 3] :y [4 5 6] :group ["a" "a" "b"]}
    (views [[:x :y]])
    (layer {:mark :point :color :group})
    kind/pprint)

;; ### ⚙️ Layer Specs

;; Each layer spec is a map that `layer` merges into views:

(defn point
  ([] {:mark :point})
  ([opts] (merge {:mark :point} opts)))

;; ### 🧪 Using Point

(-> {:x [1 2 3] :y [4 5 6] :group ["a" "a" "b"]}
    (views [[:x :y]])
    (layer (point {:color :group}))
    kind/pprint)

;; The two phases compose through threading:
;; `(-> data (views pairs) (layer (point)))` reads as
;; "these column pairings, drawn as points" -- what, then how.
;; ---

;; ## Theme and Colors

(def ggplot-palette
  ["#F8766D" "#00BA38" "#619CFF" "#A855F7" "#F97316" "#14B8A6" "#EF4444" "#6B7280"])

(def theme {:bg "#EBEBEB" :grid "#FFFFFF" :font-size 8})

;; ### 🧪 Theme

(let [{:keys [bg grid]} theme]
  (kind/hiccup
   (into [:svg {:width 300 :height 30}]
         (map-indexed (fn [i [label col]]
                        [:g
                         [:rect {:x (* i 150) :y 0 :width 20 :height 20 :fill col :rx 2
                                 :stroke "#ccc" :stroke-width 0.5}]
                         [:text {:x (+ (* i 150) 25) :y 14 :font-size 11 :fill "#333"
                                 :font-family "sans-serif"} (str label " " col)]])
                      [["bg" bg] ["grid" grid]]))))

;; ### ⚙️ Color and Shape Helpers

(defn- fmt-name
  "Format a keyword as a readable name: :sepal-length -> \"sepal length\"."
  [k]
  (str/replace (name k) #"[-_]" " "))

(defn- color-for
  "Look up the color for a categorical value from the palette."
  [categories val]
  (let [idx (.indexOf categories val)]
    (nth ggplot-palette (mod (if (neg? idx) 0 idx) (count ggplot-palette)))))

(def ^:private shape-syms [:circle :square :triangle :diamond])

(defn- render-shape-elem
  "Render a shape element (circle, square, triangle, diamond) at (cx, cy) with radius r."
  [shape cx cy r fill opts]
  (case shape
    :square [:rect (merge {:x (- cx r) :y (- cy r) :width (* 2 r) :height (* 2 r)
                           :fill fill} opts)]
    :triangle [:polygon (merge {:points (str cx "," (- cy r) " "
                                             (- cx r) "," (+ cy r) " "
                                             (+ cx r) "," (+ cy r))
                                :fill fill} opts)]
    :diamond [:polygon (merge {:points (str cx "," (- cy r) " "
                                            (+ cx r) "," cy " "
                                            cx "," (+ cy r) " "
                                            (- cx r) "," cy)
                               :fill fill} opts)]
    [:circle (merge {:cx cx :cy cy :r r :fill fill} opts)]))

;; ### 🧪 The Palette

(kind/hiccup
 (into [:svg {:width 400 :height 30}]
       (map-indexed (fn [i c]
                      [:rect {:x (* i 50) :y 0 :width 45 :height 25 :fill c :rx 3}])
                    ggplot-palette)))

;; ### 🧪 Shape Elements

(kind/hiccup
 [:svg {:width 300 :height 50}
  (render-shape-elem :circle 40 25 10 "#F8766D" {})
  (render-shape-elem :square 100 25 10 "#00BA38" {})
  (render-shape-elem :triangle 160 25 10 "#619CFF" {})
  (render-shape-elem :diamond 220 25 10 "#A855F7" {})])

;; ### 🧪 Color Lookup

(let [cats ["setosa" "versicolor" "virginica"]]
  (kind/hiccup
   (into [:svg {:width 400 :height 25}]
         (map-indexed (fn [i c]
                        (let [col (color-for cats c)]
                          [:g
                           [:rect {:x (* i 135) :y 0 :width 15 :height 15 :fill col :rx 2}]
                           [:text {:x (+ (* i 135) 20) :y 12 :font-size 11 :fill "#333"
                                   :font-family "sans-serif"} (str c " " col)]]))
                      cats))))

;; ### 🧪 Name Formatting

(mapv fmt-name [:sepal-length :petal_width :species])

;; ---

;; ## Inference and Defaults
;;
;; Many plot parameters derive from others: column types from data, grouping
;; from column types, scale types from domains. Each follows the same pattern:
;;
;; ```
;; resolved-value = (or user-override (infer-from dependencies))
;; ```
;;
;; `resolve-view` walks this chain once, filling in defaults.
;; User-specified values always win.

;; ### ⚙️ Visual Defaults
;;
;; All visual constants in one map, overridable per-plot via `:config`:

(def defaults
  {;; Layout
   :width 600 :height 400
   :margin 25 :margin-multi 15 :legend-width 100
   ;; Ticks
   :tick-spacing-x 60 :tick-spacing-y 40
   ;; Points
   :point-radius 2.5 :point-opacity 0.7
   :point-stroke "#fff" :point-stroke-width 0.5
   ;; Bars and lines
   :bar-opacity 0.7 :line-width 1.5 :grid-stroke-width 0.5
   ;; Annotations
   :annotation-stroke "#333" :annotation-dash "4,3" :band-opacity 0.08
   ;; Statistics
   :bin-method :sturges :domain-padding 0.05
   ;; Fallback
   :default-color "#333"})

;; ### ⚙️ Column Type Detection

(defn- column-type
  "Classify a dataset column as :categorical or :numerical.
  Uses Tablecloth's tcc/typeof when available, falls back to value inspection."
  [ds col]
  (let [t (try (tcc/typeof (ds col)) (catch Exception _ nil))]
    (cond
      (#{:string :keyword :boolean :symbol :text} t) :categorical
      (#{:float32 :float64 :int8 :int16 :int32 :int64} t) :numerical
      ;; fallback: inspect first values
      (every? number? (take 100 (ds col))) :numerical
      :else :categorical)))

;; ### ⚙️ `resolve-view`
;;
;; Walks the inference chain top-down, from column types through
;; grouping to mark and stat.
;; Each property is `(or user-specified inferred)`:

(defn resolve-view
  "Fill in derived properties: :x-type, :y-type, :color-type, :group, :mark, :stat.
  User-specified values always win."
  [v]
  (if-not (:data v)
    v
    (let [ds (:data v)
          x-type (or (:x-type v) (column-type ds (:x v)))
          y-type (or (:y-type v) (when (and (:y v) (not= (:x v) (:y v)))
                                   (column-type ds (:y v))))
          c-type (when (:color v)
                   (or (:color-type v) (column-type ds (:color v))))
          group (or (:group v)
                    (when (= c-type :categorical) [(:color v)])
                    [])
          ;; Infer mark and stat from column types when not specified
          diagonal? (= (:x v) (:y v))
          [default-mark default-stat]
          (cond
            ;; Same column on both axes (or y absent): single-variable
            (or diagonal? (nil? (:y v)))
            (if (= x-type :categorical)
              [:rect :count]
              [:bar :bin])
            ;; categorical x, numerical y → strip plot
            (and (= x-type :categorical) (= y-type :numerical))
            [:point :identity]
            ;; otherwise → scatter
            :else [:point :identity])
          mark (or (:mark v) default-mark)
          stat (or (:stat v) default-stat)]
      (assoc v :x-type x-type :y-type y-type :color-type c-type
             :group group :mark mark :stat stat))))

;; ### 🧪 What `resolve-view` Produces
;;
;; Both columns are numerical and the color is categorical, so
;; `resolve-view` infers grouping by species and defaults to a scatter:

(let [v (first (-> iris
                   (views [[:sepal-length :sepal-width]])
                   (layer (point {:color :species}))))]
  (select-keys (resolve-view v) [:x-type :y-type :color-type :group :mark :stat]))

;; When the color column is numerical, there is no grouping:

(let [v (first (-> iris
                   (views [[:sepal-length :sepal-width]])
                   (layer (point {:color :petal-length}))))]
  (select-keys (resolve-view v) [:x-type :y-type :color-type :group :mark :stat]))

;; When no mark is specified, `resolve-view` infers one from the column types.
;; A numerical column mapped to both x and y becomes a histogram:

(let [v (first (views iris [[:sepal-length :sepal-length]]))]
  (select-keys (resolve-view v) [:x-type :y-type :mark :stat]))

;; A categorical column on its own becomes a bar chart with counting:

(let [v (first (views iris [[:species :species]]))]
  (select-keys (resolve-view v) [:x-type :y-type :mark :stat]))

;; A categorical x with a numerical y becomes a scatter:

(let [v (first (views iris [[:species :sepal-length]]))]
  (select-keys (resolve-view v) [:x-type :y-type :mark :stat]))

;; A user-specified mark always overrides the inference:

(let [v (first (-> iris
                   (views [[:sepal-length :sepal-width]])
                   (layer {:mark :line :stat :lm})))]
  (select-keys (resolve-view v) [:x-type :y-type :mark :stat]))

;; Override defaults per-plot with `:config`:

(select-keys (merge defaults {:point-radius 5 :bar-opacity 0.9})
             [:point-radius :bar-opacity :line-width])
;; ---

;; ## Computing Statistics
;;
;; `compute-stat` is a multimethod that transforms data and returns domain
;; information. `:identity` is defined here; `:bin`, `:lm`, `:loess`, and
;; `:count` follow in later sections.

(defmulti compute-stat
  "Compute a statistical transform for a view.
  Dispatches on (:stat view), defaulting to :identity.
  Returns a map with transform-specific output data plus :x-domain and :y-domain."
  (fn [view] (or (:stat view) :identity)))

;; ### ⚙️ Shared Helpers

(defn- numeric-extent
  "Min/max pair from a numeric column."
  [col]
  [(dfn/reduce-min col) (dfn/reduce-max col)])

(defn- group-by-columns
  "Split dataset by grouping columns, apply f to each group.
  f takes [dataset group-key]. group-key is nil when no grouping,
  a single value for one column, a vector for multiple columns."
  [ds group-cols f]
  (if (seq group-cols)
    (for [[gk gds] (-> ds (tc/group-by group-cols) tc/groups->map)]
      (f gds (if (= 1 (count group-cols))
               (get gk (first group-cols))
               (mapv gk group-cols))))
    [(f ds nil)]))

;; `numeric-extent` returns the min and max of a numeric column:

(numeric-extent (iris :sepal-length))

;; `group-by-columns` splits a dataset by one or more columns and
;; applies a function to each group:

(group-by-columns
 (tc/drop-missing iris [:sepal-length :species])
 [:species]
 (fn [ds cv]
   {:color cv :n (tc/row-count ds)}))

;; ### ⚙️ `prepare-points` -- Data Preparation
;;
;; Cleanup (drop-missing, row indexing), domain computation,
;; and grouping via `group-by-columns`. Used by `:identity` below.

(defn- prepare-points
  "Clean data, compute domains, group by columns, extract aesthetics.
  Expects a resolved view (with :x-type, :group already filled in)."
  [view]
  (let [{:keys [data x y color size shape text-col x-type y-type group]} view
        data-idx (tc/add-column data :__row-idx (range (tc/row-count data)))
        clean (cond-> (tc/drop-missing data-idx [x y])
                (= x-type :categorical) (tc/map-columns x [x] str))]
    (if (zero? (tc/row-count clean))
      {:points [] :x-domain [0 1] :y-domain [0 1]}
      (let [xs-col (clean x)
            ys-col (clean y)
            cat-x? (= x-type :categorical)
            cat-y? (= y-type :categorical)
            x-dom (if cat-x? (distinct xs-col) (numeric-extent xs-col))
            y-dom (if cat-y? (distinct ys-col) (numeric-extent ys-col))
            point-group (fn [ds group-val]
                          (cond-> {:xs (ds x) :ys (ds y)
                                   :row-indices (ds :__row-idx)}
                            group-val (assoc :color group-val)
                            size (assoc :sizes (ds size))
                            shape (assoc :shapes (ds shape))
                            text-col (assoc :labels (ds text-col))))
            groups (group-by-columns clean (or group []) point-group)]
        {:points groups :x-domain x-dom :y-domain y-dom}))))

;; ### ⚙️ `:identity` -- Raw Data
;;
;; No transformation, just `prepare-points`:

(defmethod compute-stat :identity [view]
  (prepare-points view))

;; ---

;; ## Scales -- Data to Pixels
;;
;; Ticks, formatting, and band calculations via Wadogo.
;; Scale type is auto-detected from domain values.

;; ### ⚙️ `make-scale`

(defn- numeric-domain?
  [dom]
  (and (sequential? dom) (seq dom) (number? (first dom))))

(defn- categorical-domain?
  [dom]
  (and (sequential? dom) (seq dom) (not (number? (first dom)))))

(defn- scale-kind
  "Determine the scale kind from domain and scale-spec."
  [domain scale-spec]
  (cond
    (categorical-domain? domain) :categorical
    (= :log (:type scale-spec)) :log
    :else :linear))

(defmulti make-scale
  "Build a wadogo scale from domain values and pixel range.
   Dispatches on scale-kind."
  (fn [domain pixel-range scale-spec] (scale-kind domain scale-spec)))

(defmethod make-scale :categorical [domain pixel-range _]
  (ws/scale :bands {:domain domain :range pixel-range}))

(defmethod make-scale :linear [domain pixel-range _]
  (ws/scale :linear {:domain domain :range pixel-range}))

(defn- pad-domain
  "Add padding to a numeric domain. For log scales, use multiplicative padding."
  [[lo hi] scale-spec]
  (if (= :log (:type scale-spec))
    (let [log-lo (Math/log lo) log-hi (Math/log hi)
          pad (* 0.05 (max 1e-6 (- log-hi log-lo)))]
      [(Math/exp (- log-lo pad)) (Math/exp (+ log-hi pad))])
    (let [pad (* 0.05 (max 1e-6 (- hi lo)))]
      [(- lo pad) (+ hi pad)])))

;; ### 🧪 What Wadogo Gives Us

(let [s (ws/scale :linear {:domain [0 100] :range [50 550]})]
  {:value-at-50 (s 50)
   :ticks (ws/ticks s)
   :formatted (ws/format s (ws/ticks s))})

;; ### 🧪 Domain Padding

{:raw [4.3 7.9]
 :padded (pad-domain [4.3 7.9] {:type :linear})
 :log-padded (pad-domain [1 1000] {:type :log})}

;; ### 🧪 Categorical Scale

(let [s (make-scale ["A" "B" "C"] [50 550] {})]
  {:A-position (s "A")
   :B-band-info (s "B" true)
   :ticks (ws/ticks s)})
;; ---

;; ## Coordinate Systems
;;
;; A coordinate function maps data values to pixel positions:
;; `(coord data-x data-y)` returns `[pixel-x pixel-y]`.
;; All marks call it the same way, so they don't need to know
;; whether the plot is cartesian, flipped, or polar.
;;
;; `make-coord` builds this function from:
;;
;; - **coord-type** -- `:cartesian`, `:flip`, or `:polar`
;; - **sx, sy** -- Wadogo scale for each axis (data -> pixels)
;; - **pw, ph** -- panel width and height in pixels
;; - **m** -- margin in pixels

(defmulti make-coord
  "Build a coordinate function: (coord data-x data-y) -> [pixel-x pixel-y].
   Dispatches on coord-type keyword."
  (fn [coord-type sx sy pw ph m] coord-type))

(defmethod make-coord :cartesian [_ sx sy pw ph m]
  (fn [dx dy] [(sx dx) (sy dy)]))

;; ### 🧪 Coord in Action
;;
;; Data-space to pixel-space on a 600x400 canvas with 25px margin:

(let [sx (ws/scale :linear {:domain [0 10] :range [25 575]})
      sy (ws/scale :linear {:domain [0 100] :range [375 25]})
      coord (make-coord :cartesian sx sy 600 400 25)]
  {:origin (coord 0 0)
   :center (coord 5 50)
   :top-right (coord 10 100)})

;; ### ⚙️ Flip
;;
;; Swaps x and y axes. Histograms become horizontal, bar charts grow sideways.

(defmethod make-coord :flip [_ sx sy pw ph m]
  (fn [dx dy] [(sx dy) (sy dx)]))

(defmethod render-grid :flip [_ sx sy pw ph m cfg]
  (render-grid :cartesian sx sy pw ph m cfg))

(defmethod render-grid :default [_ sx sy pw ph m cfg]
  (render-grid :cartesian sx sy pw ph m cfg))

;; ### ⚙️ Polar
;;
;; Maps x to angle and y to radius. Bars become wedges, scatters wrap into a disc.

(defmethod make-coord :polar [_ sx sy pw ph m]
  (let [cx (/ pw 2.0) cy (/ ph 2.0)
        r-max (- (min cx cy) m)
        x-lo (double m) x-span (double (- pw m m))
        y-lo (double m) y-span (double (- ph m m))]
    (fn [dx dy]
      (let [px (sx dx) py (sy dy)
            t-angle (/ (- px x-lo) (max 1.0 x-span))
            t-radius (/ (- (+ y-lo y-span) py) (max 1.0 y-span))
            angle (* 2.0 Math/PI t-angle)
            radius (* r-max t-radius)]
        [(+ cx (* radius (Math/cos (- angle (/ Math/PI 2.0)))))
         (+ cy (* radius (Math/sin (- angle (/ Math/PI 2.0)))))]))))

(defmethod render-grid :polar [_ sx sy pw ph m cfg]
  (let [cfg (or cfg defaults)
        cx (/ pw 2.0) cy (/ ph 2.0)
        r-max (- (min cx cy) m)]
    (into [:g]
          (concat
           (for [i (range 1 6)
                 :let [r (* r-max (/ i 5.0))]]
             [:circle {:cx cx :cy cy :r r :fill "none"
                       :stroke (:grid theme) :stroke-width (:grid-stroke-width cfg)}])
           (for [i (range 8)
                 :let [a (* i (/ Math/PI 4))]]
             [:line {:x1 cx :y1 cy
                     :x2 (+ cx (* r-max (Math/cos a)))
                     :y2 (+ cy (* r-max (Math/sin a)))
                     :stroke (:grid theme) :stroke-width (:grid-stroke-width cfg)}])))))

;; ---

;; ## Drawing Marks
;;
;; `render-mark` is a multimethod dispatching on mark keyword.
;; `:point` is defined here; `:bar`, `:line`, `:rect`, `:text` follow later.

(defmulti render-mark
  "Render a mark layer. Dispatches on mark keyword.
  ctx contains :coord, :all-colors, :tooltip-fn, :shape-categories, :sx, :sy, :coord-px, :position."
  (fn [mark stat ctx] mark))

(defmethod render-mark :point [_ stat ctx]
  (let [{:keys [coord all-colors tooltip-fn shape-categories cfg]} ctx
        cfg (or cfg defaults)
        all-sizes (seq (mapcat :sizes (:points stat)))
        size-scale (when all-sizes
                     (let [lo (reduce min all-sizes) hi (reduce max all-sizes)
                           span (max 1e-6 (- (double hi) (double lo)))]
                       (fn [v] (+ 2.0 (* 6.0 (/ (- (double v) (double lo)) span))))))
        shape-map (when shape-categories
                    (into {} (map-indexed (fn [i c] [c (nth shape-syms (mod i (count shape-syms)))])
                                          shape-categories)))]
    (into [:g]
          (mapcat (fn [{:keys [color xs ys sizes shapes row-indices]}]
                    (let [c (if color (color-for all-colors color) (:default-color cfg))]
                      (for [i (range (count xs))
                            :let [[px py] (coord (nth xs i) (nth ys i))
                                  r (if sizes (size-scale (nth sizes i)) (:point-radius cfg))
                                  sh (if shapes (get shape-map (nth shapes i) :circle) :circle)
                                  row-idx (when row-indices (nth row-indices i))
                                  tip (when tooltip-fn
                                        (tooltip-fn {:x (nth xs i) :y (nth ys i) :color color}))
                                  base-opts (cond-> {:stroke (:point-stroke cfg)
                                                     :stroke-width (:point-stroke-width cfg)
                                                     :opacity (:point-opacity cfg)}
                                              row-idx (assoc :data-row-idx row-idx)
                                              tip (assoc :data-tooltip tip))]]
                        (render-shape-elem sh px py r c base-opts))))
                  (:points stat)))))

(defmethod render-mark :default [_ stat ctx]
  (render-mark :point stat ctx))

;; ### 🧪 What `render-mark` Produces
;;
;; Hiccup SVG elements, circles for `:point`:

(let [sx (ws/scale :linear {:domain [0 10] :range [25 575]})
      sy (ws/scale :linear {:domain [0 50] :range [375 25]})
      stat {:points [{:xs [2 5 8] :ys [10 30 20]}]}
      ctx {:coord (make-coord :cartesian sx sy 600 400 25)
           :all-colors nil :tooltip-fn nil :shape-categories nil}
      marks (render-mark :point stat ctx)]
  (kind/hiccup [:svg {:width 600 :height 400
                      "xmlns" "http://www.w3.org/2000/svg"}
                marks]))

;; ---

;; ## Axes and Grid Lines
;;
;; Multimethods, extended later with polar grids and categorical ticks.

;; ### ⚙️ Tick Helpers

(defn- format-ticks
  "Format tick values: strip .0 when all ticks are whole numbers."
  [sx ticks]
  (let [labels (ws/format sx ticks)]
    (if (every? #(== (Math/floor %) %) ticks)
      (mapv #(str (long %)) ticks)
      labels)))

(defn- tick-count
  "Suggested tick count based on available pixel range."
  [pixel-range spacing]
  (max 2 (int (/ pixel-range spacing))))

;; ### 🧪 Tick Formatting

(let [s (ws/scale :linear {:domain [0 50] :range [25 575]})]
  {:whole-numbers (format-ticks s [0.0 10.0 20.0 30.0])
   :decimals (format-ticks s [0.5 1.0 1.5 2.0])
   :tick-count-wide (tick-count 550 60)
   :tick-count-narrow (tick-count 120 60)})

;; ### ⚙️ Grid and Tick Rendering

(defmulti render-grid
  "Render grid lines for a panel."
  (fn [coord-type sx sy pw ph m cfg] coord-type))

(defmethod render-grid :cartesian [_ sx sy pw ph m cfg]
  (let [cfg (or cfg defaults)
        x-ticks (ws/ticks sx (tick-count (- pw (* 2 m)) (:tick-spacing-x cfg)))
        y-ticks (ws/ticks sy (tick-count (- ph (* 2 m)) (:tick-spacing-y cfg)))]
    (into [:g]
          (concat
           (for [t x-ticks :let [px (sx t)]]
             [:line {:x1 px :y1 m :x2 px :y2 (- ph m)
                     :stroke (:grid theme) :stroke-width (:grid-stroke-width cfg)}])
           (for [t y-ticks :let [py (sy t)]]
             [:line {:x1 m :y1 py :x2 (- pw m) :y2 py
                     :stroke (:grid theme) :stroke-width (:grid-stroke-width cfg)}])))))

(defmulti render-x-ticks
  "Render x-axis tick labels."
  (fn [domain-type sx pw ph m cfg] domain-type))

(defmethod render-x-ticks :numeric [_ sx pw ph m cfg]
  (let [cfg (or cfg defaults)
        n (tick-count (- pw (* 2 m)) (:tick-spacing-x cfg))
        ticks (ws/ticks sx n)
        labels (format-ticks sx ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666"}]
          (map (fn [t label]
                 [:text {:x (sx t) :y (- ph 2) :text-anchor "middle"} label])
               ticks labels))))

(defmulti render-y-ticks
  "Render y-axis tick labels."
  (fn [domain-type sy pw ph m cfg] domain-type))

(defmethod render-y-ticks :numeric [_ sy pw ph m cfg]
  (let [cfg (or cfg defaults)
        n (tick-count (- ph (* 2 m)) (:tick-spacing-y cfg))
        ticks (ws/ticks sy n)
        labels (format-ticks sy ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666"}]
          (map (fn [t label]
                 [:text {:x (- m 3) :y (+ (sy t) 3) :text-anchor "end"} label])
               ticks labels))))

;; ### 🧪 Bare Grid
;;
;; Background, grid lines, and tick labels, no data.
;; This is the canvas that marks get drawn onto.

(let [sx (ws/scale :linear {:domain [0 10] :range [25 575]})
      sy (ws/scale :linear {:domain [0 100] :range [375 25]})
      pw 600 ph 400 m 25]
  (kind/hiccup
   [:svg {:width pw :height ph "xmlns" "http://www.w3.org/2000/svg"}
    [:rect {:x 0 :y 0 :width pw :height ph :fill (:bg theme)}]
    (render-grid :cartesian sx sy pw ph m defaults)
    (render-x-ticks :numeric sx pw ph m defaults)
    (render-y-ticks :numeric sy pw ph m defaults)]))

;; ---

;; ## Assembling a Panel
;;
;; `render-panel` takes views for a single panel and produces SVG.
;; It computes stats, merges domains, builds scales, and dispatches
;; rendering through multimethods.

(def ^:private annotation-marks #{:rule-h :rule-v :band-h})

(defmulti render-annotation
  "Render a single annotation view. Dispatches on (:mark ann).
   ann-ctx contains :coord, :x-domain, :y-domain."
  (fn [ann ann-ctx] (:mark ann)))

(defmethod render-annotation :default [_ _] [:g])

;; ### ⚙️ `render-panel`
;;
;; The longest function in this notebook. It turns a list of views
;; into one SVG `[:g ...]` group, in eight steps:
;;
;; 1. **Config** -- read coord type and scale specs from the first view.
;; 2. **Resolve & Stats** -- `resolve-view` infers types, grouping, mark, and stat; then `compute-stat` runs.
;; 3. **Domains** -- merge x/y domains from all stats.
;; 4. **Stack adjustment** -- inflate y-domain for stacked bars.
;; 5. **Scales** -- build Wadogo scales (swap axes if flipped).
;; 6. **Coord** -- build the coordinate function.
;; 7. **Polar projection** -- pixel-space reprojection (polar only).
;; 8. **SVG** -- emit background, grid, annotations, marks, ticks.

(defn render-panel
  [panel-views pw ph m & {:keys [x-domain y-domain show-x? show-y? all-colors
                                 tooltip-fn shape-categories cfg]
                          :or {show-x? true show-y? true cfg defaults}}]
  (let [v1 (first panel-views)
        coord-type (or (:coord v1) :cartesian)
        x-scale-spec (or (:x-scale v1) {:type :linear})
        y-scale-spec (or (:y-scale v1) {:type :linear})
        polar? (= coord-type :polar)

        ;; Compute stats for data views (not annotations)
        view-stats (for [v panel-views
                         :let [rv (resolve-view v)]
                         :when (and (:mark rv) (not (annotation-marks (:mark rv))))]
                     (let [stat (compute-stat (assoc rv :cfg cfg))]
                       {:view rv :stat stat}))

        ;; Merge domains from stats
        stat-x-domains (keep #(get-in % [:stat :x-domain]) view-stats)
        stat-y-domains (keep #(get-in % [:stat :y-domain]) view-stats)

        merged-x-dom (or x-domain
                         (if (categorical-domain? (first stat-x-domains))
                           (distinct (mapcat identity stat-x-domains))
                           (let [lo (reduce min (map first stat-x-domains))
                                 hi (reduce max (map second stat-x-domains))]
                             (pad-domain [lo hi] x-scale-spec))))
        merged-y-dom (or y-domain
                         (if (categorical-domain? (first stat-y-domains))
                           (distinct (mapcat identity stat-y-domains))
                           (let [lo (reduce min (map first stat-y-domains))
                                 hi (reduce max (map second stat-y-domains))]
                             (pad-domain [lo hi] y-scale-spec))))

        ;; Adjust y-domain for stacked bars
        merged-y-dom (if (and (sequential? merged-y-dom) (number? (first merged-y-dom))
                              (some #(= :stack (:position (:view %))) view-stats))
                       (let [stacked-stats (filter #(= :stack (:position (:view %))) view-stats)
                             max-stack (reduce max 0
                                               (for [{:keys [stat]} stacked-stats
                                                     :when (:bars stat)
                                                     cat (:categories stat)]
                                                 (reduce + (for [{:keys [counts]} (:bars stat)
                                                                 {:keys [category count]} counts
                                                                 :when (= category cat)]
                                                             count))))]
                         (if (pos? max-stack)
                           (pad-domain [0 max-stack] y-scale-spec)
                           merged-y-dom))
                       merged-y-dom)

        ;; Build wadogo scales
        [x-dom' y-dom'] (if (= coord-type :flip)
                          [merged-y-dom merged-x-dom]
                          [merged-x-dom merged-y-dom])
        x-pixel-range [m (- pw m)]
        y-pixel-range [(- ph m) m]
        sx (make-scale x-dom' x-pixel-range (if (= coord-type :flip) y-scale-spec x-scale-spec))
        sy (make-scale y-dom' y-pixel-range (if (= coord-type :flip) x-scale-spec y-scale-spec))
        cat-x? (categorical-domain? x-dom')
        cat-y? (categorical-domain? y-dom')

        ;; Build coord function
        coord (make-coord coord-type sx sy pw ph m)

        ;; Pixel-space polar projection (for categorical bar arc munching)
        coord-px (when polar?
                   (let [cx (/ pw 2.0) cy (/ ph 2.0)
                         r-max (- (min cx cy) m)
                         x-lo (double m) x-span (double (- pw m m))
                         y-lo (double m) y-span (double (- ph m m))]
                     (fn [px py]
                       (let [t-angle (/ (- px x-lo) (max 1.0 x-span))
                             t-radius (/ (- (+ y-lo y-span) py) (max 1.0 y-span))
                             angle (* 2.0 Math/PI t-angle)
                             radius (* r-max t-radius)]
                         [(+ cx (* radius (Math/cos (- angle (/ Math/PI 2.0)))))
                          (+ cy (* radius (Math/sin (- angle (/ Math/PI 2.0)))))]))))

        annotation-views (filter #(annotation-marks (:mark %)) panel-views)

        ctx {:coord coord :all-colors all-colors :tooltip-fn tooltip-fn
             :shape-categories shape-categories :sx sx :sy sy :coord-px coord-px
             :cfg cfg}
        ann-ctx {:coord coord :x-domain merged-x-dom :y-domain merged-y-dom :cfg cfg}]

    [:g
     ;; Background
     [:rect {:x 0 :y 0 :width pw :height ph :fill (:bg theme)}]

     ;; Grid
     (render-grid (if polar? :polar coord-type) sx sy pw ph m cfg)

     ;; Annotations, dispatch through render-annotation multimethod
     (into [:g]
           (for [ann annotation-views]
             (render-annotation ann ann-ctx)))

     ;; Data layers, dispatch through render-mark multimethod
     (into [:g]
           (mapcat (fn [{:keys [view stat]}]
                     (let [mark (:mark view)
                           mark-ctx (assoc ctx :position (or (:position view) :dodge))]
                       [(render-mark mark stat mark-ctx)]))
                   view-stats))

     ;; Tick labels
     (when (and show-x? (not polar?))
       (render-x-ticks (if cat-x? :categorical :numeric) sx pw ph m cfg))
     (when (and show-y? (not polar?))
       (render-y-ticks (if cat-y? :categorical :numeric) sy pw ph m cfg))]))

;; ### 🧪 A Single Panel
;;
;; `render-panel` directly: background, grid, data, ticks:

(kind/hiccup
 [:svg {:width 600 :height 400
        "xmlns" "http://www.w3.org/2000/svg"}
  (render-panel
   (-> {:x [1 2 3 4 5] :y [2 4 3 5 4]}
       (views [[:x :y]])
       (layer (point)))
   600 400 25)])
;; ---

;; ## Rendering the Plot
;;
;; `plot` is the main entry point. It computes stats, builds scales,
;; and delegates to `arrange-panels` for the SVG layout.

;; ### ⚙️ `arrange-panels`

(defmulti arrange-panels
  "Arrange panels into an SVG layout. Dispatches on layout type."
  (fn [layout-type ctx] layout-type))

(defn- panel-from-ctx
  "Call render-panel with common args from ctx. Overrides via kwargs."
  [ctx panel-views & {:keys [show-x? show-y? x-domain y-domain]
                      :or {show-x? true show-y? true}}]
  (render-panel panel-views (:pw ctx) (:ph ctx) (:m ctx)
                :show-x? show-x? :show-y? show-y?
                :all-colors (:all-colors ctx)
                :x-domain (or x-domain (:global-x-doms ctx))
                :y-domain (or y-domain (:global-y-doms ctx))
                :tooltip-fn (:tooltip-fn ctx)
                :shape-categories (:shape-categories ctx)
                :cfg (:cfg ctx)))

(defn- infer-layout [views]
  (let [facet-rows (seq (remove nil? (map :facet-row views)))
        facet-cols (seq (remove nil? (map :facet-col views)))
        facet-vals (seq (remove nil? (map :facet-val views)))]
    (cond
      (and facet-rows facet-cols) :facet-grid
      facet-vals :facet
      :else (let [x-vars (distinct (map :x views))
                  y-vars (distinct (map :y views))]
              (if (or (> (count x-vars) 1) (> (count y-vars) 1))
                :multi-variable
                :single)))))

(defmethod arrange-panels :single [_ ctx]
  (let [panel-views (concat (:non-ann-views ctx) (:ann-views ctx))]
    [[:g (panel-from-ctx ctx panel-views)]]))

;; ### ⚙️ `render-legend`

(defn- render-legend [categories color-fn & {:keys [x y title]}]
  [:g {:font-family "sans-serif" :font-size 10}
   (when title [:text {:x x :y (- y 5) :fill "#333" :font-size 9} (fmt-name title)])
   (for [[i cat] (map-indexed vector categories)]
     [:g [:circle {:cx x :cy (+ y (* i 16)) :r 4 :fill (color-fn cat)}]
      [:text {:x (+ x 10) :y (+ y (* i 16) 4) :fill "#333"} (str cat)]])])

;; ### 🧪 Legend
(let [cats ["setosa" "versicolor" "virginica"]]
  (kind/hiccup
   [:svg {:width 120 :height 70}
    (render-legend cats #(color-for cats %) :x 10 :y 15 :title :species)]))

;; ### ⚙️ `wrap-plot`
;;
;; Wraps SVG with [Scittle](https://github.com/babashka/scittle) scripts
;; for tooltip and/or brush interactions.

(defn- tooltip-script
  "Scittle script for custom tooltips on elements with data-tooltip attribute."
  [div-id]
  (list 'let ['container (list '.getElementById 'js/document div-id)
              'svg '(.querySelector container "svg")
              'tip-el '(.createElement js/document "div")]
        '(set! (.-className tip-el) "aog-tooltip")
        '(.appendChild container tip-el)
        '(let [show! (fn [e]
                       (let [text (.getAttribute (.-target e) "data-tooltip")]
                         (when text
                           (set! (.-textContent tip-el) text)
                           (set! (.. tip-el -style -display) "block"))))
               hide! (fn [_]
                       (set! (.. tip-el -style -display) "none"))
               move! (fn [e]
                       (let [r (.getBoundingClientRect container)
                             x (+ (- (.-clientX e) (.-left r)) 12)
                             y (+ (- (.-clientY e) (.-top r)) 12)]
                         (set! (.. tip-el -style -left) (str x "px"))
                         (set! (.. tip-el -style -top) (str y "px"))))]
           (.addEventListener svg "mouseover" show!)
           (.addEventListener svg "mouseout" hide!)
           (.addEventListener svg "mousemove" move!))))

(defn- brush-script
  "Scittle script for drag-to-select brush interaction."
  [div-id]
  (list 'let ['svg (list '.querySelector 'js/document (str "#" div-id " svg"))
              'pts '(.querySelectorAll svg "[data-row-idx]")
              'all-shapes '(.querySelectorAll svg "circle,polygon")
              'state '(atom {:drag false :x0 0 :y0 0 :sel nil})
              'set-all-opacity '(fn [shapes o]
                                  (.forEach shapes (fn [p] (.setAttribute p "opacity" o))))]
        '(.addEventListener svg "mousedown"
                            (fn [e]
                              (let [r (.getBoundingClientRect svg)
                                    x0 (- (.-clientX e) (.-left r))
                                    y0 (- (.-clientY e) (.-top r))
                                    sel (.createElementNS js/document "http://www.w3.org/2000/svg" "rect")]
                                (.setAttribute sel "fill" "rgba(100,100,255,0.2)")
                                (.setAttribute sel "stroke" "#66f")
                                (.appendChild svg sel)
                                (reset! state {:drag true :x0 x0 :y0 y0 :sel sel}))))
        '(.addEventListener svg "mousemove"
                            (fn [e]
                              (when (:drag @state)
                                (let [{:keys [x0 y0 sel]} @state
                                      r (.getBoundingClientRect svg)
                                      x1 (- (.-clientX e) (.-left r))
                                      y1 (- (.-clientY e) (.-top r))]
                                  (.setAttribute sel "x" (min x0 x1))
                                  (.setAttribute sel "y" (min y0 y1))
                                  (.setAttribute sel "width" (js/Math.abs (- x1 x0)))
                                  (.setAttribute sel "height" (js/Math.abs (- y1 y0)))))))
        '(.addEventListener svg "mouseup"
                            (fn [e]
                              (when (:drag @state)
                                (let [{:keys [sel]} @state
                                      _ (swap! state assoc :drag false)
                                      bx (js/parseFloat (.getAttribute sel "x"))
                                      by (js/parseFloat (.getAttribute sel "y"))
                                      bw (js/parseFloat (.getAttribute sel "width"))
                                      bh (js/parseFloat (.getAttribute sel "height"))]
                                  (.removeChild svg sel)
                                  (if (and (< bw 3) (< bh 3))
                                    (set-all-opacity all-shapes "0.7")
                                    (let [sr (.getBoundingClientRect svg)
                                          selected (atom #{})]
                                      (.forEach pts
                                                (fn [p]
                                                  (let [pr (.getBoundingClientRect p)
                                                        cx (- (+ (.-left pr) (/ (.-width pr) 2)) (.-left sr))
                                                        cy (- (+ (.-top pr) (/ (.-height pr) 2)) (.-top sr))]
                                                    (when (and (>= cx bx) (<= cx (+ bx bw))
                                                               (>= cy by) (<= cy (+ by bh)))
                                                      (swap! selected conj (.getAttribute p "data-row-idx"))))))
                                      (if (zero? (count @selected))
                                        (set-all-opacity all-shapes "0.7")
                                        (.forEach pts
                                                  (fn [p]
                                                    (if (contains? @selected (.getAttribute p "data-row-idx"))
                                                      (.setAttribute p "opacity" "1.0")
                                                      (.setAttribute p "opacity" "0.15")))))))))))))

(def ^:private tooltip-css
  ".aog-tooltip { display:none; position:absolute; pointer-events:none; background:rgba(0,0,0,0.8); color:#fff; padding:6px 10px; border-radius:4px; font-family:sans-serif; font-size:13px; white-space:nowrap; z-index:10; }")

(defn wrap-plot
  "Wrap SVG content with interaction scripts (tooltip, brush, or both)."
  [modes svg-content]
  (if (empty? modes)
    (kind/hiccup svg-content)
    (let [div-id (str "plot-" (random-uuid))
          has-tooltip? (modes :tooltip)
          scripts (cond-> []
                    has-tooltip? (conj (tooltip-script div-id))
                    (modes :brush) (conj (brush-script div-id)))]
      (kind/hiccup
       (into (cond-> [:div {:id div-id
                            :style {:position "relative" :display "inline-block"}}]
               has-tooltip? (conj [:style tooltip-css]))
             (cons svg-content scripts))))))

;; Without interaction modes, `wrap-plot` passes SVG through as hiccup.
;; With `:tooltip`, it wraps the SVG in a div and appends a Scittle
;; script that shows a tooltip for any element with a `data-tooltip` attribute:

(wrap-plot #{:tooltip}
           [:svg {:width 100 :height 40}
            [:circle {:cx 25 :cy 20 :r 8 :fill "#F8766D" :data-tooltip "point A"}]
            [:circle {:cx 50 :cy 20 :r 8 :fill "#00BA38" :data-tooltip "point B"}]
            [:circle {:cx 75 :cy 20 :r 8 :fill "#619CFF" :data-tooltip "point C"}]])

;; ### ⚙️ Domain Helpers

(defn- collect-domain
  "Collect and merge domains from stat-results along axis-key (:x-domain or :y-domain).
   Returns a padded numeric domain or a distinct categorical domain."
  [stat-results axis-key scale-spec]
  (let [vals (mapcat (fn [dv] (let [d (axis-key dv)]
                                (if (and (= 2 (count d)) (number? (first d)))
                                  d (map str d)))) stat-results)]
    (when (seq vals)
      (if (number? (first vals))
        (pad-domain [(reduce min vals) (reduce max vals)] scale-spec)
        (distinct vals)))))

(defn- compute-global-y-domain
  "Compute the global y-domain, handling stacked bar accumulation."
  [stat-results views scale-spec]
  (let [has-stacked? (some #(= :stack (:position %)) views)]
    (if has-stacked?
      (let [count-views (filter #(= :count (:stat %)) stat-results)
            all-cats (distinct (mapcat :categories count-views))
            max-stack (if (seq all-cats)
                        (apply max
                               (for [cat all-cats]
                                 (reduce + (for [dv count-views
                                                 :let [idx (.indexOf (:categories dv) cat)]
                                                 :when (>= idx 0)]
                                             (nth (:counts dv) idx)))))
                        0)
            other-yd (mapcat (fn [dv]
                               (when-not (= :count (:stat (first (filter #(= (:x dv) (:x %)) views))))
                                 (:y-domain dv)))
                             stat-results)
            hi (if (seq other-yd)
                 (max max-stack (reduce max other-yd))
                 max-stack)]
        (pad-domain [0 hi] scale-spec))
      (collect-domain stat-results :y-domain scale-spec))))

;; ### ⚙️ `plot`
;;
;; The main entry point. The view pipeline (algebra) specifies *what*
;; to show -- data, columns, marks, aesthetics. `plot` handles *how*
;; to show it: canvas size, scale sharing, interactivity. This boundary
;; mirrors ggplot2's separation of `aes` + `geom` from `theme` and
;; rendering options.

(defn plot
  "Render views as SVG. Options: :width :height :scales :coord :tooltip :brush :config"
  ([views] (plot views {}))
  ([views {:keys [width height scales coord tooltip brush config]}]
   (let [cfg (merge defaults config)
         width (or width (:width cfg))
         height (or height (:height cfg))
         views (if (map? views) [views] views)
         views (if coord (mapv #(assoc % :coord coord) views) views)
         ann-views (filter #(annotation-marks (:mark %)) views)
         non-ann-views (remove #(annotation-marks (:mark %)) views)
         layout-type (infer-layout non-ann-views)
         x-vars (distinct (map :x non-ann-views))
         y-vars (distinct (map :y non-ann-views))
         facet-vals (distinct (remove nil? (map :facet-val non-ann-views)))
         facet-row-vals (distinct (remove nil? (map :facet-row non-ann-views)))
         facet-col-vals (distinct (remove nil? (map :facet-col non-ann-views)))
         cols (case layout-type
                :facet-grid (count facet-col-vals)
                :facet (count facet-vals)
                (count x-vars))
         rows (case layout-type
                :facet-grid (count facet-row-vals)
                :facet 1
                (count y-vars))
         multi? (and (= layout-type :multi-variable) (> cols 1) (> rows 1))
         m (if multi? (:margin-multi cfg) (:margin cfg))
         pw0 (double (/ width cols)) ph0 (double (/ height rows))
         pw (if multi? (min pw0 ph0) pw0)
         ph (if multi? (min pw0 ph0) ph0)
         stat-results (mapv (comp compute-stat #(assoc % :cfg cfg) resolve-view) non-ann-views)
         all-colors (let [color-views (filter #(and (:color %) (:data %)) views)]
                      (when (seq color-views)
                        (distinct (mapcat #((:data %) (:color %)) color-views))))
         color-cols (distinct (remove nil? (map :color views)))
         shape-col (first (remove nil? (map :shape views)))
         shape-categories (when shape-col
                            (distinct (mapcat (fn [v] (when (:data v) (map #(get % shape-col) (tc/rows (:data v) :as-maps))))
                                              views)))
         polar? (= :polar (:coord (first views)))
         tooltip-fn (when tooltip
                      (fn [row] (str/join ", " (map (fn [[k v]] (str (name k) ": " v)) row))))
         scale-mode (or scales :shared)
         x-scale-spec (or (:x-scale (first non-ann-views)) {:type :linear})
         y-scale-spec (or (:y-scale (first non-ann-views)) {:type :linear})
         global-x-doms (when (#{:shared :free-y} scale-mode)
                         (collect-domain stat-results :x-domain x-scale-spec))
         global-y-doms (when (#{:shared :free-x} scale-mode)
                         (compute-global-y-domain stat-results views y-scale-spec))
         legend-w (if (or all-colors shape-categories) (:legend-width cfg) 0)
         total-w (+ (* cols pw) legend-w)
         total-h (* rows ph)
         ctx {:non-ann-views non-ann-views :ann-views ann-views
              :pw pw :ph ph :m m :rows rows :cols cols
              :all-colors all-colors :tooltip-fn tooltip-fn
              :shape-categories shape-categories :polar? polar?
              :global-x-doms global-x-doms :global-y-doms global-y-doms
              :x-vars x-vars :y-vars y-vars
              :facet-vals facet-vals :facet-row-vals facet-row-vals :facet-col-vals facet-col-vals
              :color-cols color-cols :shape-col shape-col :scale-mode scale-mode
              :cfg cfg}
         svg-content
         [:svg {:width total-w :height total-h
                "xmlns" "http://www.w3.org/2000/svg"
                "xmlns:xlink" "http://www.w3.org/1999/xlink"
                "version" "1.1"}
          (when all-colors
            (render-legend all-colors #(color-for all-colors %)
                           :x (+ (* cols pw) 10) :y 20
                           :title (first color-cols)))
          (when shape-categories
            (let [y-off (if all-colors (+ 20 (* (count all-colors) 16) 10) 20)
                  x-off (+ (* cols pw) 10)]
              (into [:g {:font-family "sans-serif" :font-size 10}
                     (when shape-col [:text {:x x-off :y (- y-off 5) :fill "#333" :font-size 9}
                                      (fmt-name shape-col)])]
                    (for [[i cat] (map-indexed vector shape-categories)
                          :let [sh (nth shape-syms (mod i (count shape-syms)))]]
                      [:g (render-shape-elem sh (+ x-off 5) (+ y-off (* i 16)) 4
                                             (if all-colors (color-for all-colors cat) "#333") {})
                       [:text {:x (+ x-off 15) :y (+ y-off (* i 16) 4) :fill "#333"}
                        (str cat)]]))))
          (into [:g] (remove nil? (arrange-panels layout-type ctx)))]]
     (wrap-plot (cond-> #{} tooltip (conj :tooltip) brush (conj :brush)) svg-content))))

;; ---

;; ## Scatter Plots
;;
;; The simplest complete plots: data, views, a mark, and `plot`.

;; ### 🧪 Scatter from Inline Data
;;
;; A map of columns works as data -- `views` wraps it into a dataset:

(-> {:x [1 2 3 4 5 6]
     :y [2 4 3 5 4 6]
     :group ["a" "a" "a" "b" "b" "b"]}
    (views [[:x :y]])
    (layer (point {:color :group}))
    plot)

;; ### 🧪 Iris Scatter
;;
;; Same pipeline, now with a real dataset and no color:

(-> iris
    (views [[:sepal-length :sepal-width]])
    (layer (point))
    plot)

;; ### 🧪 Colored Scatter
;;
;; Adding `:color` to the `point` spec splits the data by species:

(-> iris
    (views [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    plot)

;; ---

;; ## Histograms
;;
;; Bins numerical data, renders counts as bars.
;; `compute-stat :bin` returns `{:y-domain [0 max-count]}`,
;; the y-axis scales to counts, not raw data values.

;; ### ⚙️ Histogram Constructor

(defn histogram
  ([] {:mark :bar :stat :bin})
  ([opts] (merge {:mark :bar :stat :bin} opts)))

;; ### ⚙️ `compute-stat` `:bin`

(defmethod compute-stat :bin [view]
  (let [{:keys [data x group cfg]} view
        clean (tc/drop-missing data [x])
        xs-col (clean x)]
    (if (zero? (tc/row-count clean))
      {:bins [] :max-count 0 :x-domain [0 1] :y-domain [0 1]}
      (let [all-bin-data (group-by-columns
                          clean (or group [])
                          (fn [ds gv]
                            (let [hist (stats/histogram (double-array (ds x)) (:bin-method (or cfg defaults)))]
                              (cond-> {:bin-maps (:bins-maps hist)}
                                gv (assoc :color gv)))))
            max-count (reduce max 1 (for [{:keys [bin-maps]} all-bin-data
                                          b bin-maps]
                                      (:count b)))]
        {:bins all-bin-data
         :max-count max-count
         :x-domain (numeric-extent xs-col)
         :y-domain [0 max-count]}))))

;; ### 🧪 What `:bin` Returns
;;
;; Bins with counts and boundaries. Note the y-domain: it comes from
;; bin counts, not raw data values -- this is how stat-driven domains work.

(let [stat (-> (view iris :sepal-length)
               (layer (histogram))
               first
               resolve-view
               compute-stat)]
  {:x-domain (:x-domain stat)
   :y-domain (:y-domain stat)
   :first-3-bins (mapv #(select-keys % [:min :max :count])
                       (take 3 (:bin-maps (first (:bins stat)))))})
;; ### ⚙️ `render-mark` `:bar`
;;
;; Bars projected as 4-corner polygons, works with cartesian, flip, and polar.

(defmethod render-mark :bar [_ stat ctx]
  (let [{:keys [coord all-colors cfg]} ctx
        cfg (or cfg defaults)]
    (into [:g]
          (mapcat (fn [{:keys [color bin-maps]}]
                    (let [c (if color (color-for all-colors color) (:default-color cfg))]
                      (for [{:keys [min max count]} bin-maps
                            :let [[x1 y1] (coord min 0)
                                  [x2 y2] (coord max 0)
                                  [x3 y3] (coord max count)
                                  [x4 y4] (coord min count)]]
                        [:polygon {:points (str x1 "," y1 " " x2 "," y2 " "
                                                x3 "," y3 " " x4 "," y4)
                                   :fill c :opacity (:bar-opacity cfg)}])))
                  (:bins stat)))))
;; ### 🧪 Histogram
;;
;; A single column means x = y, which auto-selects `:bin`:

(-> (view iris :sepal-length)
    (layer (histogram))
    plot)

;; ### 🧪 Colored Histogram
;;
;; Color splits bins per group, dodging them side by side:

(-> (view iris :sepal-length)
    (layer (histogram {:color :species}))
    plot)

;; ### 🧪 Flipped Histogram
;;
;; `:flip` swaps the axes -- bars grow leftward:
;;

(-> (view iris :sepal-length)
    (layer (histogram))
    (layer {:coord :flip})
    plot)

;; ---

;; ## Line Charts
;;
;; Lines connecting raw data points, using the `:line` mark
;; with `:identity` stat. The same `render-mark :line` handles
;; both raw lines and regression/loess fits.

;; ### ⚙️ Layer Spec

(defn line-mark
  ([] {:mark :line :stat :identity})
  ([opts] (merge {:mark :line :stat :identity} opts)))

;; ### 🧪 Line Chart (Connecting Raw Points)

(-> (views {:year [2018 2019 2020 2021 2022]
            :sales [10 15 13 17 20]}
           [[:year :sales]])
    (layer (line-mark))
    plot)

;; ### 🧪 Colored Line Chart

(-> (views {:year [2018 2019 2020 2021 2022 2018 2019 2020 2021 2022]
            :sales [10 15 13 17 20 8 12 11 14 18]
            :region ["East" "East" "East" "East" "East"
                     "West" "West" "West" "West" "West"]}
           [[:year :sales]])
    (layer (line-mark {:color :region}))
    plot)

;; ---

;; ## Layers
;;
;; `layer` merges one spec into every view. `layers` stacks multiple
;; marks on the same base views, duplicating each view per spec.
;; Annotation specs (hline, vline, hband) are kept as separate views.

;; ### ⚙️ `layers`

(defn layers
  "Stack multiple layers from the same base views."
  [base-views & layer-specs]
  (let [ann-specs (filter #(and (map? %) (annotation-marks (:mark %))) layer-specs)
        data-specs (remove #(and (map? %) (annotation-marks (:mark %))) layer-specs)]
    (concat (apply concat (map #(layer base-views %) data-specs))
            ann-specs)))

;; ### 🧪 What `layers` Produces
;;
;; Base views duplicated, each copy with a different mark:

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point) (line-mark))
    (->> (mapv #(select-keys % [:x :y :mark :stat]))))

;; ### 🧪 Scatter + Line Overlay

(-> (views {:year [2018 2019 2020 2021 2022]
            :sales [10 15 13 17 20]}
           [[:year :sales]])
    (layers (point) (line-mark))
    plot)

;; ### 🧪 Colored Scatter + Line

(-> (views {:year [2018 2019 2020 2021 2022 2018 2019 2020 2021 2022]
            :sales [10 15 13 17 20 8 12 11 14 18]
            :region ["East" "East" "East" "East" "East"
                     "West" "West" "West" "West" "West"]}
           [[:year :sales]])
    (layers (point {:color :region}) (line-mark {:color :region}))
    plot)

;; ---

;; ## Regression and Smooth Lines
;;
;; Regression ([OLS](https://en.wikipedia.org/wiki/Ordinary_least_squares) via [Fastmath](https://github.com/generateme/fastmath)) and smooth curves ([LOESS](https://en.wikipedia.org/wiki/Local_regression) interpolation).

;; ### ⚙️ Layer Specs

(defn lm
  ([] {:mark :line :stat :lm})
  ([opts] (merge {:mark :line :stat :lm} opts)))

(defn loess
  ([] {:mark :line :stat :loess})
  ([opts] (merge {:mark :line :stat :loess} opts)))

;; ### ⚙️ `compute-stat` `:lm`

(defn- fit-lm
  "Fit a linear model on xs-col and ys-col, return {:x1 :y1 :x2 :y2}."
  [xs-col ys-col]
  (let [model (regr/lm (double-array ys-col) (double-array xs-col))
        x-min (dfn/reduce-min xs-col)
        x-max (dfn/reduce-max xs-col)]
    {:x1 x-min :y1 (regr/predict model [x-min])
     :x2 x-max :y2 (regr/predict model [x-max])}))

(defmethod compute-stat :lm [view]
  (let [{:keys [data x y group]} view
        clean (tc/drop-missing data [x y])
        n (tc/row-count clean)]
    (if (or (< n 2)
            (= (dfn/reduce-min (clean x)) (dfn/reduce-max (clean x))))
      {:lines []
       :x-domain (if (pos? n) (numeric-extent (clean x)) [0 1])
       :y-domain (if (pos? n) (numeric-extent (clean y)) [0 1])}
      (let [lines (group-by-columns
                   clean (or group [])
                   (fn [ds gv]
                     (when (and (>= (tc/row-count ds) 2)
                                (not= (dfn/reduce-min (ds x))
                                      (dfn/reduce-max (ds x))))
                       (cond-> (fit-lm (ds x) (ds y))
                         gv (assoc :color gv)))))]
        {:lines (remove nil? lines)
         :x-domain (numeric-extent (clean x))
         :y-domain (numeric-extent (clean y))}))))

;; ### 🧪 What `:lm` Returns
;;
;; Two endpoints per group, the fitted line from x-min to x-max:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (lm {:color :species}))
    first
    resolve-view
    compute-stat
    kind/pprint)
;; ### ⚙️ `compute-stat` `:loess`
;;
;; Loess via fastmath.interpolation (80 sample points, x values deduplicated).

(defmethod compute-stat :loess [view]
  (let [{:keys [data x y group]} view
        clean (tc/drop-missing data [x y])
        n (tc/row-count clean)]
    (if (< n 4)
      {:points [] :x-domain [0 1] :y-domain [0 1]}
      (let [n-sample 80
            dedup-sort (fn [ds]
                         (-> ds
                             (tc/group-by [x])
                             (tc/aggregate {y #(dfn/mean (% y))})
                             (tc/order-by [x])))
            fit-loess (fn [ds]
                        (let [deduped (dedup-sort ds)
                              sxs (double-array (deduped x)) sys (double-array (deduped y))
                              f (interp/interpolation :loess sxs sys)
                              x-lo (aget sxs 0) x-hi (aget sxs (dec (alength sxs)))
                              step (/ (- x-hi x-lo) (dec n-sample))
                              sample-xs (mapv #(+ x-lo (* % step)) (range n-sample))
                              sample-ys (mapv f sample-xs)]
                          {:xs sample-xs :ys sample-ys}))
            results (group-by-columns clean (or group [])
                                      (fn [ds gv]
                                        (cond-> (fit-loess ds)
                                          gv (assoc :color gv))))]
        {:points results
         :x-domain (numeric-extent (clean x))
         :y-domain (numeric-extent (clean y))}))))

;; ### ⚙️ `render-mark` `:line`

(defmethod render-mark :line [_ stat ctx]
  (let [{:keys [coord all-colors cfg]} ctx
        cfg (or cfg defaults)]
    (into [:g]
          (concat
           (when-let [lines (:lines stat)]
             (for [{:keys [color x1 y1 x2 y2]} lines
                   :let [c (if color (color-for all-colors color) (:default-color cfg))
                         [px1 py1] (coord x1 y1)
                         [px2 py2] (coord x2 y2)]]
               [:line {:x1 px1 :y1 py1 :x2 px2 :y2 py2
                       :stroke c :stroke-width (:line-width cfg)}]))
           (when-let [pts (:points stat)]
             (for [{:keys [color xs ys]} pts
                   :let [c (if color (color-for all-colors color) (:default-color cfg))
                         projected (sort-by first (map (fn [x y] (coord x y)) xs ys))]]
               [:polyline {:points (str/join " " (map (fn [[px py]] (str px "," py)) projected))
                           :stroke c :stroke-width (:line-width cfg) :fill "none"}]))))))

;; ### 🧪 Scatter + Regression
;;
;; `layers` stacks two marks on the same data -- one scatter, one regression line:

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point {:color :species})
            (lm {:color :species}))
    plot)

;; ### 🧪 Smooth Curve (Loess)
;;
;; LOESS fits a local curve instead of a straight line:

(-> (views iris [[:sepal-length :petal-length]])
    (layers (point {:color :species})
            (loess {:color :species}))
    plot)

;; ### 🧪 Triple Layer (Scatter + Regression + Smooth)
;;
;; Three marks on the same data -- `layers` accepts any number of specs:

(-> (views iris [[:sepal-length :petal-length]])
    (layers (point {:color :species})
            (lm {:color :species})
            (loess {:color :species}))
    plot)

;; ---

;; ## Categorical Charts
;;
;; Wadogo band scales for bar positioning. `compute-stat :count` tallies
;; categories; `render-mark :rect` handles dodge and stack.

;; ### ⚙️ Layer Specs

(defn bar
  ([] {:mark :rect :stat :count})
  ([opts] (merge {:mark :rect :stat :count} opts)))

(defn stacked-bar
  "Stacked bar chart: bars stacked by color group."
  ([] {:mark :rect :stat :count :position :stack})
  ([opts] (merge {:mark :rect :stat :count :position :stack} opts)))

(defn value-bar
  "Pre-aggregated bars: categorical x, numerical y, no counting."
  ([] {:mark :rect :stat :identity})
  ([opts] (merge {:mark :rect :stat :identity} opts)))

;; ### ⚙️ `compute-stat` `:count`

(defmethod compute-stat :count [view]
  (let [{:keys [data x x-type group]} view
        group-cols (or group [])
        clean (cond-> (tc/drop-missing data [x])
                (= x-type :categorical) (tc/map-columns x [x] str))
        categories (distinct (clean x))]
    (if (empty? categories)
      {:categories [] :bars [] :max-count 0 :x-domain ["?"] :y-domain [0 1]}
      (if (seq group-cols)
        (let [color-col (first group-cols)
              clean-c (tc/drop-missing clean group-cols)
              color-cats (sort (distinct (clean-c color-col)))
              all-group-cols (distinct (cons x group-cols))
              grouped (-> clean-c (tc/group-by all-group-cols) tc/groups->map)
              count-fn (fn [cat cc]
                         (let [key (merge {x cat} (zipmap group-cols
                                                          (if (= 1 (count group-cols))
                                                            [cc] cc)))]
                           (if-let [ds (get grouped key)]
                             (tc/row-count ds)
                             0)))
              max-count (reduce max 1 (for [cat categories, cc color-cats]
                                        (count-fn cat cc)))]
          {:categories categories
           :bars (for [cc color-cats]
                   {:color cc
                    :counts (mapv (fn [cat] {:category cat :count (count-fn cat cc)})
                                  categories)})
           :max-count max-count
           :x-domain categories
           :y-domain [0 max-count]})
        (let [grouped (-> clean (tc/group-by [x]) tc/groups->map)
              counts-by-cat (mapv (fn [cat]
                                    {:category cat
                                     :count (if-let [ds (get grouped {x cat})]
                                              (tc/row-count ds) 0)})
                                  categories)
              max-count (reduce max 1 (map :count counts-by-cat))]
          {:categories categories
           :bars [{:counts counts-by-cat}]
           :max-count max-count
           :x-domain categories
           :y-domain [0 max-count]})))))

;; ### 🧪 What `:count` Returns
;;
;; Rows tallied per category:

(-> (view iris :species)
    (layer (bar))
    first
    resolve-view
    compute-stat
    kind/pprint)

;; ### ⚙️ Categorical Bar Helpers

(defn- munch-arc
  "Sample points along an arc for polar bar rendering."
  [coord-px px-lo px-hi py-lo py-hi n-seg]
  (let [outer (for [i (range (inc n-seg))
                    :let [t (/ (double i) n-seg)
                          px (+ px-lo (* t (- px-hi px-lo)))]]
                (coord-px px py-hi))
        inner (for [i (range n-seg -1 -1)
                    :let [t (/ (double i) n-seg)
                          px (+ px-lo (* t (- px-hi px-lo)))]]
                (coord-px px py-lo))]
    (str/join " " (map (fn [[x y]] (str x "," y))
                       (concat outer inner)))))

(defn- render-bar-elem
  "Render a bar as rect (cartesian) or polygon (polar)."
  [coord-px x-lo x-hi py-lo py-hi color cfg]
  (let [opacity (:bar-opacity cfg)]
    (if coord-px
      [:polygon {:points (munch-arc coord-px x-lo x-hi py-lo py-hi 20)
                 :fill color :opacity opacity}]
      [:rect {:x x-lo :y (clojure.core/min py-lo py-hi)
              :width (- x-hi x-lo)
              :height (Math/abs (- py-lo py-hi))
              :fill color :opacity opacity}])))

(defn- render-categorical-bars
  [stat ctx]
  (let [{:keys [all-colors sx sy coord-px position cfg]} ctx
        cfg (or cfg defaults)
        bw (ws/data sx :bandwidth)
        n-colors (clojure.core/count (:bars stat))
        cum-y (atom {})
        active-map (when (= position :dodge)
                     (into {}
                           (for [cat (:categories stat)]
                             [cat (keep-indexed
                                   (fn [bi {:keys [counts]}]
                                     (let [c (some #(when (= cat (:category %)) (:count %)) counts)]
                                       (when (and c (pos? c)) bi)))
                                   (:bars stat))])))]
    (into [:g]
          (mapcat (fn [bi {:keys [color counts]}]
                    (let [c (if color (color-for all-colors color) (:default-color cfg))]
                      (for [{:keys [category count]} counts
                            :when (or (= position :stack) (pos? count))
                            :let [band-info (sx category true)
                                  band-start (:rstart band-info)
                                  band-end (:rend band-info)
                                  band-mid (/ (+ band-start band-end) 2.0)]]
                        (if (= position :stack)
                          (let [base (get @cum-y category 0)
                                py-lo (sy base)
                                py-hi (sy (+ base count))
                                x-lo (- band-mid (* bw 0.4))
                                x-hi (+ band-mid (* bw 0.4))]
                            (swap! cum-y assoc category (+ base count))
                            (render-bar-elem coord-px x-lo x-hi py-lo py-hi c cfg))
                          (let [active (get active-map category)
                                n-active (clojure.core/count active)
                                active-idx (.indexOf ^java.util.List active bi)
                                sub-bw (/ (* bw 0.8) (clojure.core/max 1 n-active))
                                x-lo (+ (- band-mid (/ (* n-active sub-bw) 2.0)) (* active-idx sub-bw))
                                x-hi (+ x-lo sub-bw)
                                py-lo (sy 0)
                                py-hi (sy count)]
                            (render-bar-elem coord-px x-lo x-hi py-lo py-hi c cfg))))))
                  (range) (:bars stat)))))

(defn- render-value-bars
  [stat ctx]
  (let [{:keys [all-colors sx sy coord-px position cfg]} ctx
        cfg (or cfg defaults)
        bw (ws/data sx :bandwidth)
        groups (:points stat)
        n-groups (clojure.core/count groups)
        sub-bw (/ (* bw 0.8) (clojure.core/max 1 n-groups))
        cum-y (atom {})]
    (into [:g]
          (mapcat (fn [gi {:keys [color xs ys]}]
                    (let [c (if color (color-for all-colors color) (:default-color cfg))]
                      (for [i (range (clojure.core/count xs))
                            :let [cat (nth xs i)
                                  val (nth ys i)
                                  band-info (sx cat true)
                                  band-start (:rstart band-info)
                                  band-end (:rend band-info)
                                  band-mid (/ (+ band-start band-end) 2.0)]]
                        (if (= position :stack)
                          (let [base (get @cum-y cat 0)
                                py-lo (sy base)
                                py-hi (sy (+ base val))
                                x-lo (- band-mid (* bw 0.4))
                                x-hi (+ band-mid (* bw 0.4))]
                            (swap! cum-y assoc cat (+ base val))
                            (render-bar-elem coord-px x-lo x-hi py-lo py-hi c cfg))
                          (let [x-lo (+ (- band-mid (/ (* n-groups sub-bw) 2.0)) (* gi sub-bw))
                                x-hi (+ x-lo sub-bw)
                                py-lo (sy 0)
                                py-hi (sy val)]
                            (render-bar-elem coord-px x-lo x-hi py-lo py-hi c cfg))))))
                  (range) groups))))

;; ### ⚙️ `render-mark` `:rect`

(defmethod render-mark :rect [_ stat ctx]
  (if (:bars stat)
    (render-categorical-bars stat ctx)
    (render-value-bars stat ctx)))

;; ### ⚙️ `render-x-ticks` `:categorical`

(defmethod render-x-ticks :categorical [_ sx pw ph m cfg]
  (let [ticks (ws/ticks sx)
        labels (map str ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666"}]
          (map (fn [t label]
                 [:text {:x (sx t) :y (- ph 2) :text-anchor "middle"} label])
               ticks labels))))

(defmethod render-y-ticks :categorical [_ sy pw ph m cfg]
  (let [ticks (ws/ticks sy)
        labels (map str ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666"}]
          (map (fn [t label]
                 [:text {:x (- m 3) :y (+ (sy t) 3) :text-anchor "end"} label])
               ticks labels))))

;; ### 🧪 Bar Chart
;;
;; The simplest categorical plot -- `:count` tallies species, band scale positions bars:

(-> (view iris :species)
    (layer (bar))
    plot)

;; ### 🧪 Colored Bar Chart
;;
;; Color = same column as x: each bar gets its species color:

(-> (view iris :species)
    (layer (bar {:color :species}))
    plot)

;; ### 🧪 Stacked Bar Chart
;;
;; Color = a *different* column: bars stack by drive type within each class:

(-> (view mpg :class)
    (layer (stacked-bar {:color :drv}))
    plot)

;; ### 🧪 Strip Plot (Categorical x, Continuous y)
;;
;; A categorical x with a numerical y: points line up along the category axis:

(-> (views iris [[:species :sepal-length]])
    (layer (point {:color :species}))
    plot)

;; ### 🧪 Horizontal Strip Plot (Flipped)
;;
;; `:flip` works on categorical plots too:

(-> (views iris [[:species :sepal-length]])
    (layer (point {:color :species}))
    (layer {:coord :flip})
    plot)

;; ### 🧪 Numeric-as-Categorical
;;
;; `:x-type :categorical` forces a numeric column onto a band scale:

(-> (view mpg :cyl)
    (layer (bar {:x-type :categorical}))
    plot)

;; ### 🧪 Value Bar (Pre-Aggregated Data)
;;
;; When data is already aggregated, `value-bar` skips the `:count` stat:

(-> (views {:fruit ["Apple" "Banana" "Cherry"]
            :amount [30 20 45]}
           [[:fruit :amount]])
    (layer (value-bar {:color :fruit}))
    plot)

;; ### 🧪 Value Bar (Plain)
;;
;; Same data without color -- single-color bars:

(-> (views {:fruit ["Apple" "Banana" "Cherry"]
            :amount [30 20 45]}
           [[:fruit :amount]])
    (layer (value-bar))
    plot)

;; ---

;; ## Multi-Panel Layouts
;;
;; Multiple variables or faceting split views across panels.
;; `arrange-panels` multimethod handles layout.

(defn diagonal?
  "True if a view maps the same column to both x and y."
  [v]
  (= (:x v) (:y v)))

;; ### 🧪 Auto-Detection in Action
;;
;; Diagonal views (x=y) become histograms, off-diagonal become scatters:

(-> (views iris (cross [:sepal-length :sepal-width] [:sepal-length :sepal-width]))
    (->> (mapv #(select-keys (resolve-view %) [:x :y :mark :stat]))))

;; ### ⚙️ Filtering and Conditional Specs

(defn where [views pred] (filter pred views))
(defn where-not [views pred] (remove pred views))

(defn when-diagonal
  "Merge spec into diagonal views only."
  [views spec]
  (mapv (fn [v] (if (diagonal? v) (merge v spec) v)) views))

(defn when-off-diagonal
  "Merge spec into off-diagonal views only."
  [views spec]
  (mapv (fn [v] (if-not (diagonal? v) (merge v spec) v)) views))

;; ### 🧪 Filtering Views

(let [vs (-> iris
             (views (cross [:sepal-length :sepal-width] [:sepal-length :sepal-width]))
             (when-off-diagonal {:color :species}))]
  (mapv #(select-keys % [:x :y :mark :color]) vs))

;; This is useful when diagonal and off-diagonal cells need different
;; aesthetics. Here, scatters are colored by species while the
;; overall distribution on the diagonal stays uncolored:

(-> (views iris (cross [:sepal-length :sepal-width :petal-length]
                       [:sepal-length :sepal-width :petal-length]))
    (when-off-diagonal {:color :species})
    plot)

;; ### ⚙️ Column-Pair Helpers

(defn distribution
  "Create diagonal views (x=y) for each column, used for histograms."
  [data & cols]
  (views data (mapv (fn [c] [c c]) cols)))

(defn pairs
  "Upper-triangle pairs of columns, used for pairwise scatters."
  [cols]
  (for [i (range (count cols))
        j (range (inc i) (count cols))]
    [(nth cols i) (nth cols j)]))

;; ### 🧪 Column Pairs

(mapv #(select-keys % [:x :y]) (distribution iris :sepal-length :sepal-width))

(pairs [:a :b :c])

;; ### ⚙️ Faceting

(defn facet
  "Split each view by a categorical column."
  [views col]
  (mapcat
   (fn [v]
     (validate-columns (:data v) :facet col)
     (let [groups (-> (:data v) (tc/group-by [col]) tc/groups->map)]
       (map (fn [[gk gds]]
              (assoc v :data gds :facet-val (get gk col)))
            groups)))
   views))

(defn facet-grid
  "Split each view by two categorical columns for a row × column grid."
  [views row-col col-col]
  (mapcat
   (fn [v]
     (validate-columns (:data v) :facet-row row-col)
     (validate-columns (:data v) :facet-col col-col)
     (let [groups (-> (:data v) (tc/group-by [row-col col-col]) tc/groups->map)]
       (map (fn [[gk gds]]
              (assoc v :data gds
                     :facet-row (get gk row-col)
                     :facet-col (get gk col-col)))
            groups)))
   views))

;; ### 🧪 Faceting in Action

(-> iris
    (views [[:sepal-length :sepal-width]])
    (facet :species)
    kind/pprint)

;; ### ⚙️ `arrange-panels` `:multi-variable`

(defmethod arrange-panels :multi-variable [_ ctx]
  (let [{:keys [non-ann-views ann-views pw ph x-vars y-vars rows cols polar?]} ctx
        ;; Per-variable domains: each column shares its x-variable's domain,
        ;; each row shares its y-variable's domain (excluding diagonal histograms).
        var-domain (fn [var-key views-seq]
                     (let [scatter-views (filter #(not= (:x %) (:y %)) views-seq)
                           stats (map (comp compute-stat #(assoc % :cfg (:cfg ctx)) resolve-view) scatter-views)
                           doms (keep var-key stats)
                           num-doms (filter #(number? (first %)) doms)]
                       (when (seq num-doms)
                         (pad-domain [(reduce min (map first num-doms))
                                      (reduce max (map second num-doms))]
                                     {:type :linear}))))
        col-x-doms (into {} (for [xv x-vars]
                              [xv (var-domain :x-domain (filter #(= xv (:x %)) non-ann-views))]))
        row-y-doms (into {} (for [yv y-vars]
                              [yv (var-domain :y-domain (filter #(= yv (:y %)) non-ann-views))]))]
    (for [[ri yv] (map-indexed vector y-vars)
          [ci xv] (map-indexed vector x-vars)
          :let [panel-views (concat (filter #(and (= xv (:x %)) (= yv (:y %))) non-ann-views)
                                    ann-views)
                diagonal? (= xv yv)]]
      (when (seq panel-views)
        [:g {:transform (str "translate(" (* ci pw) "," (* ri ph) ")")}
         (panel-from-ctx ctx panel-views
                         :show-x? (= ri (dec rows))
                         :show-y? (zero? ci)
                         :x-domain (get col-x-doms xv)
                         :y-domain (when-not diagonal? (get row-y-doms yv)))
         (when (and (zero? ri) (not polar?))
           [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
                   :font-size 9 :fill "#333"} (fmt-name xv)])
         (when (and (= ci (dec cols)) (not polar?))
           [:text {:x (- pw 5) :y (/ ph 2) :text-anchor "end"
                   :font-size 9 :fill "#333"
                   :transform (str "rotate(-90," (- pw 5) "," (/ ph 2) ")")}
            (fmt-name yv)])]))))

;; ### ⚙️ `arrange-panels` `:facet`

(defmethod arrange-panels :facet [_ ctx]
  (let [{:keys [non-ann-views ann-views pw ph facet-vals]} ctx]
    (for [[ci fv] (map-indexed vector facet-vals)
          :let [fviews (concat (filter #(= fv (:facet-val %)) non-ann-views)
                               ann-views)]]
      [:g {:transform (str "translate(" (* ci pw) ",0)")}
       (panel-from-ctx ctx fviews :show-y? (zero? ci))
       [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
               :font-size 10 :fill "#333"} (str fv)]])))

;; ### ⚙️ `arrange-panels` `:facet-grid`

(defmethod arrange-panels :facet-grid [_ ctx]
  (let [{:keys [non-ann-views ann-views pw ph facet-row-vals facet-col-vals rows cols]} ctx]
    (for [[ri rv] (map-indexed vector facet-row-vals)
          [ci cv] (map-indexed vector facet-col-vals)
          :let [panel-views (concat (filter #(and (= rv (:facet-row %))
                                                  (= cv (:facet-col %))) non-ann-views)
                                    ann-views)]]
      (when (seq panel-views)
        [:g {:transform (str "translate(" (* ci pw) "," (* ri ph) ")")}
         (panel-from-ctx ctx panel-views
                         :show-x? (= ri (dec rows))
                         :show-y? (zero? ci))
         (when (zero? ri)
           [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
                   :font-size 10 :fill "#333"} (str cv)])
         (when (= ci (dec cols))
           [:text {:x (- pw 5) :y (/ ph 2) :text-anchor "end"
                   :font-size 10 :fill "#333"
                   :transform (str "rotate(-90," (- pw 5) "," (/ ph 2) ")")}
            (str rv)])]))))

;; ### 🧪 SPLOM ([Scatterplot Matrix](https://en.wikipedia.org/wiki/Scatter_plot#Scatter_plot_matrices))
;;
;; Cross all columns with themselves; `resolve-view` infers histogram
;; for diagonal cells and scatter for off-diagonal:

(-> (views iris (cross iris-quantities iris-quantities))
    (layer {:color :species})
    plot)

;; ### 🧪 Faceted Scatter
;;
;; One panel per species -- `facet` splits views by a column:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (facet :species)
    plot)

;; ### 🧪 Row × Column Faceting
;;
;; `facet-grid` maps two columns to rows and columns of panels:

(let [tips (tc/dataset "https://raw.githubusercontent.com/mwaskom/seaborn-data/master/tips.csv")]
  (-> (views tips [["total_bill" "tip"]])
      (layer (point {:color "day"}))
      (facet-grid "smoker" "sex")
      (plot {:width 600 :height 500})))

;; ### 🧪 Faceted Scatter with Free Y-Scale
;;
;; `:free-y` lets each panel fit its own y-range:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (facet :species)
    (plot {:scales :free-y}))

;; ### 🧪 Faceted Scatter with Free X-Scale
;;
;; Likewise for the x-axis:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (facet :species)
    (plot {:scales :free-x}))

;; ### 🧪 Faceted Scatter with Free Scales (Both Axes)
;;
;; Both axes free -- each panel zooms to its own data:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (facet :species)
    (plot {:scales :free}))

;; ---

;; ## Polish
;;
;; Scale setters, polar coordinates, annotations, text labels, and interactivity.

;; ### ⚙️ Scale and Coord Setters

(defn set-scale
  "Set a scale type for :x or :y across all views."
  ([views channel type] (set-scale views channel type {}))
  ([views channel type opts]
   (let [k (case channel :x :x-scale :y :y-scale)]
     (mapv #(assoc % k (merge {:type type} opts)) views))))

;; ### ⚙️ `make-scale` `:log`

(defmethod make-scale :log [domain pixel-range _]
  (ws/scale :log {:domain domain :range pixel-range}))

(defn set-coord
  "Set coordinate system: :cartesian (default), :flip, or :polar."
  [views c]
  (mapv #(assoc % :coord c) views))

;; ### 🧪 How Setters Modify Views
;;
;; `set-scale` and `set-coord` add keys to each view map:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point))
    (set-scale :x :log)
    (set-coord :polar)
    first
    (select-keys [:x :y :mark :x-scale :coord]))

;; ### 🧪 Log Scale

(let [data (tc/dataset {:x (mapv #(Math/pow 10 %) (range 0.0 3.01 0.1))
                        :y (mapv #(+ % (* 0.5 (rand))) (range 0.0 3.01 0.1))})]
  (-> (views data [[:x :y]])
      (layer (point))
      (set-scale :x :log)
      plot))

;; ### 🧪 Log Scale (Y-Axis)

(-> (views {:x [1 2 3 4 5] :y [1 10 100 1000 10000]}
           [[:x :y]])
    (layer (point))
    (set-scale :y :log)
    plot)

;; ### 🧪 Polar Scatter
;;
;; `set-coord :polar` wraps the same scatter into polar space:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (set-coord :polar)
    plot)

;; ### 🧪 Polar Bar Chart (Rose / Coxcomb)
;;
;; Bars become wedges -- bar width maps to angle, height to radius:

(-> (view iris :species)
    (layer (bar))
    (set-coord :polar)
    plot)

;; ### 🧪 Polar Stacked Bar Chart
;;
;; Stacking works the same way in polar coords:

(-> (view mpg :class)
    (layer (stacked-bar {:color :drv}))
    (set-coord :polar)
    plot)

;; ### ⚙️ Annotation Constructors

(defn text-label
  "Text labels at data positions. `col` is the column used for label text."
  ([col] {:mark :text :stat :identity :text-col col})
  ([col opts] (merge {:mark :text :stat :identity :text-col col} opts)))

(defn hline
  "Horizontal reference line at y = `val`."
  [val] {:mark :rule-h :value val})

(defn vline
  "Vertical reference line at x = `val`."
  [val] {:mark :rule-v :value val})

(defn hband
  "Horizontal reference band between y1 and y2."
  [y1 y2] {:mark :band-h :y1 y1 :y2 y2})

;; ### 🧪 Annotation Specs
;;
;; Each annotation constructor returns a plain map:

[(hline 3.0)
 (vline 6.0)
 (hband 2.5 3.5)]
;; ### ⚙️ `render-annotation` methods

(defmethod render-annotation :rule-h [ann {:keys [coord x-domain cfg]}]
  (let [cfg (or cfg defaults)
        [x1 y1] (coord (first x-domain) (:value ann))
        [x2 y2] (coord (if (categorical-domain? x-domain)
                         (last x-domain)
                         (second x-domain))
                       (:value ann))]
    [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2
            :stroke (:annotation-stroke cfg) :stroke-width 1
            :stroke-dasharray (:annotation-dash cfg)}]))

(defmethod render-annotation :rule-v [ann {:keys [coord y-domain cfg]}]
  (let [cfg (or cfg defaults)
        [x1 y1] (coord (:value ann) (first y-domain))
        [x2 y2] (coord (:value ann)
                       (if (categorical-domain? y-domain)
                         (last y-domain)
                         (second y-domain)))]
    [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2
            :stroke (:annotation-stroke cfg) :stroke-width 1
            :stroke-dasharray (:annotation-dash cfg)}]))

(defmethod render-annotation :band-h [ann {:keys [coord x-domain cfg]}]
  (let [cfg (or cfg defaults)
        [x1 y1] (coord (first x-domain) (:y1 ann))
        [x2 y2] (coord (second x-domain) (:y2 ann))]
    [:rect {:x (min x1 x2) :y (min y1 y2)
            :width (Math/abs (- x2 x1)) :height (Math/abs (- y2 y1))
            :fill (:annotation-stroke cfg) :opacity (:band-opacity cfg)}]))

;; ### ⚙️ `render-mark` `:text`

(defmethod render-mark :text [_ stat ctx]
  (let [{:keys [coord all-colors cfg]} ctx
        cfg (or cfg defaults)]
    (into [:g {:font-size 9 :fill (:default-color cfg) :text-anchor "middle"}]
          (mapcat (fn [{:keys [color xs ys labels]}]
                    (let [c (if color (color-for all-colors color) (:default-color cfg))]
                      (for [i (range (count xs))
                            :let [[px py] (coord (nth xs i) (nth ys i))]]
                        [:text {:x px :y (- py 5) :fill c}
                         (str (nth labels i))])))
                  (:points stat)))))

;; ### 🧪 Annotations

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point {:color :species})
            (hline 3.0)
            (hband 2.5 3.5)
            (vline 6.0))
    plot)

;; ### 🧪 Text Labels at Group Means

(let [means (-> iris
                (tc/group-by :species)
                (tc/aggregate {:sepal-length #(dfn/mean (% :sepal-length))
                               :sepal-width #(dfn/mean (% :sepal-width))
                               :species #(first (% :species))}))]
  (-> (views iris [[:sepal-length :sepal-width]])
      (layers (point {:color :species}))
      (concat (-> (views means [[:sepal-length :sepal-width]])
                  (layer (text-label :species))))
      plot))

;; ### 🧪 Bubble Chart (Size Aesthetic)
;;
;; `:size` maps a numerical column to circle radius:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species :size :petal-length}))
    plot)

;; ### 🧪 Shape Aesthetic
;;
;; `:shape` maps a categorical column to marker shape:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species :shape :species}))
    plot)

;; ### 🧪 Tooltips on Hover
;;
;; `:tooltip true` adds mouseover labels showing data values:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (plot {:tooltip true}))
;; ### 🧪 Brushable Scatter
;;
;; `:brush true` adds a drag-to-select rectangle:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (plot {:brush true}))

;; ### 🧪 Brushable SPLOM (Cross-Panel)
;;
;; Brush in one panel highlights the same rows across all panels:

(-> (views iris (cross iris-quantities iris-quantities))
    (layer {:color :species})
    (plot {:brush true}))

;; ### 🧪 Brushable Facets
;;
;; Cross-panel brushing works on faceted plots too:

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (facet :species)
    (plot {:brush true}))

;; ### 🧪 Missing Data Tolerance
;;
;; `nil` values are dropped silently -- no crash, just fewer points:

(let [data (tc/dataset {:x [1 2 nil 4 5 6 nil 8]
                        :y [2 4 6 nil 10 12 14 16]})]
  (-> (views data [[:x :y]])
      (layer (point))
      plot))

;; ### 🧪 Single Point
;;
;; Edge case: one data point still produces a valid plot:

(-> (views {:x [5] :y [10]}
           [[:x :y]])
    (layer (point))
    plot)

;; ### 🧪 Single Category
;;
;; Edge case: one bar still renders with correct axis:

(-> (views {:cat ["A"] :val [42]}
           [[:cat :val]])
    (layer (value-bar))
    plot)

;; ---

;; ## Reflection
;;
;; ### 📖 What worked
;;
;; **Stat-driven domains.** `compute-stat :bin` returns `{:y-domain [0 28]}`,
;; so histogram axes scale to counts rather than raw data values.
;; Any stat that produces new dimensions (binning, counting, regression)
;; supplies its own domain, the renderer doesn't need to know.
;;
;; **Wadogo.** `(ws/scale ...)`, `(ws/ticks ...)`, `(ws/format ...)` replace
;; custom `linear-scale`, `nice-ticks`, `categorical-scale`. Log scales,
;; band padding, and datetime support are included.
;;
;; **One coord function.** `[x y] -> [px py]` per coordinate system.
;; Marks decompose to points and call it; renderers never branch on
;; coord type. Bars project 4 corners; polar bars sample arc points.
;;
;; **`resolve-view` + `defaults`.**  A single function infers column types,
;; grouping, mark, and stat -- each as `(or user-override inferred)`.
;; Visual constants live in one `defaults` map, overridable per-plot via `:config`.
;; A categorical color column triggers grouping; a numerical one is visual-only.
;; When no mark is specified, column types decide: two numerical columns
;; produce a scatter, a diagonal pair becomes a histogram, and a categorical
;; column becomes a bar chart.
;;
;; ### 📖 Composition in hindsight
;;
;; The compositional core is `views`, `layer`, `layers`, and `cross`.
;; Layer specs, faceting, scale/coord setters, and view selectors
;; round out the user-facing API. Some observations after building it all.
;;
;; **`cross` earns its name.** It is just `for`, but naming it makes
;; the SPLOM read as intent: `(cross cols cols)` says "all pairs"
;; where a raw `for` would not.
;;
;; **`views` syntax is heavy for the common case.**
;; Every call is `(views data [[:x :y]])` -- the nested vector is
;; necessary for multi-pair grids but awkward for a single scatter.
;; `(view data :x :y)` exists as a convenience, but the two names
;; (`view` vs `views`) are easy to confuse.
;;
;; **`layer` vs `layers` is confusing.** `layer` merges a map into
;; every view (apply overrides). `layers` duplicates views per spec
;; (stack marks). The glossary defines Layer as stacking marks,
;; which is what `layers` does, not what `layer` does.
;;
;; **The histogram idiom `[[:col :col]]` is non-obvious.**
;; Mapping x = y signals "same variable for both axes," which
;; inference turns into a histogram. Conceptually a histogram
;; has one variable, so the encoding is clever but surprising.
;;
;; **`plot` options live outside the composition.** Interactivity
;; (`:brush`, `:tooltip`), scale sharing (`:scales :free`), and
;; faceting all pass through `plot` rather than composing through
;; views. Whether that is a limitation or a reasonable boundary
;; between specification and rendering is an open question.
;;
;; ### 📖 What's rough
;;
;; **Flip.** Domain swap at scale level + argument swap in the coord
;; function, a double-swap that works but is subtle.
;;
;; **render-panel.** Does too much: stats, domain merge, scales, coord,
;; annotations, data, ticks. `resolve-view` and `defaults` extract some
;; concerns, but the function is still large.
;;
;; **No axis labels.** Tick values but no "sepal length ->" on
;; standalone plots. SPLOM panels use column headers.
;;
;; **Partial validation.** `view` and `views` check that columns exist
;; in the dataset, catching typos early. But marks, stats, and `plot`
;; options are not validated -- a misspelled `:mark :piont` fails
;; silently or deep in `render-panel`.
;;
;; ### 📖 Design space
;;
;; [Implementing the Algebra of Graphics in Clojure](aog_in_clojure_part1.html) takes a different
;; tradeoff: richer algebraic operators (`=*`/`=+`), Malli schema
;; validation, multi-target rendering (geom, Vega-Lite, Plotly),
;; but lighter rendering (no polar, stacked bars, loess, annotations).
;; This post has simpler composition but more rendering features. A future library
;; would ideally combine both.
;;
;; ### 📖 Open questions
;;
;; - Composition operators: algebraic feel (`=*`/`=+`) or explicit (`views`/`layer`)?
;; - Single SVG target or multiple backends?
;; - Axis labels and titles?
;; - Faceting: part of the algebra or a separate operation?
;;
;; Feedback welcome. This is part of the
;; [Real-World Data dev group](https://scicloj.github.io/docs/community/groups/real-world-data/).

;; ---
