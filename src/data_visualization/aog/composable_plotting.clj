^{:kindly/hide-code true
  :clay {:title "Composable Plotting in Clojure"
         :quarto {:type :post
                  :author [:daslu :claude]
                  :date "2026-02-22"
                  :description "Building a composable plotting API in Clojure, from views and layers to scatterplot matrices"
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :design :wadogo]
                  :keywords [:datavis]
                  :toc true
                  :toc-depth 2
                  :image "aog_iris.png"
                  :draft true}}}
(ns data-visualization.aog.composable-plotting
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
;; > [Implementing the Algebra of Graphics in Clojure](../aog_in_clojure_part1.html) explores alternative
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
;; respatialized, generateme, Harold Hausman, Bruce Durning,
;; and others.
;; It takes place in the context of the
;; [Real-World Data dev group](https://scicloj.github.io/docs/community/groups/real-world-data/),
;; recently reinitiated by Timothy Pratley.
;;
;; #### Reading this document
;;
;; Section headers use emoji to indicate content type:
;; **📖** narrative, **⚙️** implementation, **🧪** examples.
;;
;; Everything renders to [SVG](https://en.wikipedia.org/wiki/SVG) via [Hiccup](https://github.com/weavejester/hiccup). The post builds up incrementally:
;; scatter plots, histograms, regression lines, bars,
;; multi-panel layouts, polar coordinates, and interactivity.
;; ---

;; ## Motivation
;;
;; The [SPLOM Tutorial](../splom_tutorial.html) builds a colored
;; [scatterplot matrix](https://en.wikipedia.org/wiki/Scatter_plot#Scatter_plot_matrices)
;; with regression lines by hand: manual grid offsets, explicit scale
;; computation, per-panel rendering loops. The result is nice
;; but the code is long and tightly coupled to one layout.
;;
;; Here is that 4×4 [SPLOM](https://en.wikipedia.org/wiki/Scatter_plot#Scatter_plot_matrices) (scatterplot matrix), rendered from that tutorial:

(require '[data-visualization.splom-tutorial :as splom-tut])

;; :::{.column-screen-inset-right}
splom-tut/iris-splom-4x4
;; ::: 

;; The goal of this post is to build a composable API where
;; something like this could produce a similar result:
;;
;; ```clojure
;; (-> (view iris (cross iris-quantities iris-quantities))
;;     (lay {:color :species})
;;     (plot {:brush true}))
;; ```
;;
;; Everything that the SPLOM Tutorial does — grid layout,
;; scale sharing, color assignment, diagonal detection —
;; should follow from the composed specification.
;; ---

;; ## Glossary
;;
;; **Mark** -- a visual element: point, bar, line, or text.
;;
;; **View** -- *what* to plot: a map binding data to column roles
;; (dataset, x, y, color, size, ...).
;;
;; **Layer** -- *how* to plot it: a map specifying mark and stat.
;; A scatter plot with regression lines has two layers sharing
;; the same views.
;;
;; **Stat** -- a statistical transform applied before drawing.
;; Binning produces a histogram, regression fits a line,
;; identity passes values through unchanged.
;;
;; **Domain** -- the extent of data values along one channel:
;; `[4.3 7.9]` for numerical, `["setosa" "versicolor" "virginica"]`
;; for categorical. Stats may produce their own domains
;; (e.g. binning yields a count domain for y).
;;
;; **Range** -- the extent of pixel positions a domain maps to,
;; e.g. `[25 575]`. Together with the domain, it defines a scale.
;;
;; **Scale** -- a function from domain values to pixel positions
;; (and back). Built by [Wadogo](https://github.com/generateme/wadogo);
;; also provides ticks and formatting. Numeric, categorical (band),
;; and log scales are supported.
;;
;; **Coord** -- a coordinate function that maps two data values
;; to a pixel position: `(coord dx dy)` → `[px py]`.
;; It composes two scales with a coordinate system.
;; Cartesian maps directly, flip swaps the axes,
;; polar wraps them into angle and radius.
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

(ns data-visualization.aog.composable-plotting
  (:require
   ;; Tablecloth - dataset manipulation
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   [tech.v3.datatype :as dtype]
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
;; Three datasets appear throughout: **iris** (150 flowers, 4 measurements
;; plus species), **mpg** (234 cars, fuel economy and class),
;; and **tips** (244 restaurant bills with tip amount, party size, day,
;; and smoker status).
;; All come from [R datasets](https://vincentarelbundock.github.io/Rdatasets/).
(def iris (rdatasets/datasets-iris))
(def iris-quantities [:sepal-length :sepal-width :petal-length :petal-width])

iris

(def mpg (rdatasets/ggplot2-mpg))

mpg

(def tips (rdatasets/reshape2-tips))

tips

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
;; | Phase            | Main concept | Representation                                  |
;; |------------------|--------------|--------------------------------------------------|
;; | **What** to plot | `view`       | `{:data ds :x :a :y :b :color :c}`              |
;; | **How** to plot  | `lay`        | `{:mark :point}`, `{:mark :line :stat :lm}`, ... |
;;
;; Both are plain maps, so they compose freely through threading.

;; ### ⚙️ Views

(defn parse-view-spec
  "Parse a view spec: a keyword becomes a single-variable view (histogram),
  a vector becomes {:x ... :y ...}, a map passes through."
  [spec]
  (cond
    (keyword? spec) {:x spec :y spec}
    (map? spec) spec
    :else {:x (first spec) :y (second spec)}))

(defn column-ref?
  "True if v is a column reference (keyword), false if it's a fixed constant (string, number)."
  [v]
  (keyword? v))

(def column-keys
  "View keys whose values are column names in the dataset."
  #{:x :y :color :size :shape})

(defn validate-columns
  "Check that every column-referencing key in view-map names a real column in ds.
  Non-keyword values (strings, numbers) are fixed constants and skip validation.
  Also usable as (validate-columns ds :facet col) for a single named check."
  ([ds view-map]
   (let [col-names (set (tc/column-names ds))]
     (doseq [k column-keys
             :let [col (get view-map k)]
             :when (and col (column-ref? col) (not (col-names col)))]
       (throw (ex-info (str "Column " col " (from " k ") not found in dataset. Available: " (sort col-names))
                       {:key k :column col :available (sort col-names)})))))
  ([ds role col]
   (let [col-names (set (tc/column-names ds))]
     (when-not (col-names col)
       (throw (ex-info (str "Column " col " (from " role ") not found in dataset. Available: " (sort col-names))
                       {:key role :column col :available (sort col-names)}))))))

(defn multi-spec?
  "True if specs is a sequence of view specs (pairs, keywords, or maps)
  rather than a single spec."
  [specs]
  (and (sequential? specs)
       (let [fst (first specs)]
         (or (sequential? fst) (map? fst)))))

(defn view
  "Create views from data and column specs. Accepts several forms:
  - (view data :x :y)           — two keywords, one scatter view
  - (view data :col)            — single column, histogram view
  - (view data [:x :y])         — pair as vector, same as two keywords
  - (view data {:x :a :y :b})   — map spec with any view keys
  - (view data [[:x :y] [:x :z]]) — multiple pairs, one view per pair
  - (view data (cross cols cols))  — cross product of columns
  Anything `tc/dataset` accepts works as data."
  ([data spec-or-x]
   (let [ds (if (tc/dataset? data) data (tc/dataset data))]
     (if (multi-spec? spec-or-x)
       (mapv (fn [spec]
               (let [parsed (parse-view-spec spec)]
                 (validate-columns ds parsed)
                 (assoc parsed :data ds)))
             spec-or-x)
       (let [parsed (parse-view-spec spec-or-x)]
         (validate-columns ds parsed)
         [(assoc parsed :data ds)]))))
  ([data x y]
   (let [ds (if (tc/dataset? data) data (tc/dataset data))
         v {:x x :y y}]
     (validate-columns ds v)
     [(assoc v :data ds)])))

;; ### 🧪 What a View Looks Like
;;
;; Two keywords — one scatter view:

(-> (view {:a [1 2 3] :b [4 5 6]} :a :b)
    kind/pprint)

;; A pair as a vector — same result:

(-> (view {:a [1 2 3] :b [4 5 6]} [:a :b])
    kind/pprint)

;; A single keyword — histogram (x=y):

(-> (view {:a [1 2 3] :b [4 5 6]} :a)
    kind/pprint)

;; A map — bind additional column roles like `:color`:

(-> (view {:a [1 2 3] :b [4 5 6] :g ["x" "x" "y"]}
          {:x :a :y :b :color :g})
    kind/pprint)

;; Multiple pairs — one view per pair:

(-> {:x [1 2 3] :y [4 5 6] :z [7 8 9]}
    (view [[:x :y] [:x :z]])
    kind/pprint)

;; Cross product — all pairings (defined later in this notebook):
;;
;; ```clojure
;; (view iris (cross iris-quantities iris-quantities))
;; ```

;; ### ⚙️ Layer
;;
;; `lay` applies one or more layers (mark, stat, and optional column bindings)
;; to every view. This is the "how" phase: same data, different rendering.

(defn merge-layer
  "Merge a layer into each view, adding mark, stat, and column bindings."
  [views overrides]
  (mapv (fn [v]
          (when (:data v)
            (validate-columns (:data v) overrides))
          (merge v overrides))
        views))

;; Defined here (rather than in the Annotations section) because `lay`
;; needs it to separate annotation specs from data layers.
(def annotation-marks
  "Marks that are annotations rather than data layers.
  'rule' is the traditional name for a reference line in plotting libraries."
  #{:rule-h :rule-v :band-h :band-v})

(defn lay
  "Apply one or more layers to the base views. Each layer adds a mark, stat,
  and optional column bindings. Multiple layers duplicate the views."
  [base-views & layer-specs]
  (let [ann-specs (filter #(and (map? %) (annotation-marks (:mark %))) layer-specs)
        data-specs (remove #(and (map? %) (annotation-marks (:mark %))) layer-specs)]
    (concat (apply concat (map #(merge-layer base-views %) data-specs))
            ann-specs)))

;; Defined here (rather than in the Scales and Coordinates section) because
;; examples use it before that section.
(defn coord
  "Set coordinate system: :cartesian (default), :flip, or :polar."
  [views c]
  (mapv #(assoc % :coord c) views))

;; ### 🧪 Adding a Mark
;;
;; A layer is just a map -- you can write it directly:

(-> {:x [1 2 3] :y [4 5 6] :group ["a" "a" "b"]}
    (view [[:x :y]])
    (lay {:mark :point :color :group})
    kind/pprint)

;; Constructors like `point` below make this more readable.

;; ### ⚙️ Layer Constructors

;; Each constructor returns a layer -- a map that `lay` merges into views:

(defn point
  ([] {:mark :point})
  ([opts] (merge {:mark :point} opts)))

;; ### 🧪 Using Point

(-> {:x [1 2 3] :y [4 5 6] :group ["a" "a" "b"]}
    (view [[:x :y]])
    (lay (point {:color :group}))
    kind/pprint)

;; The two phases compose through threading:
;; `(-> data (view pairs) (lay (point)))` reads as
;; "these column pairings, drawn as points" -- what, then how.
;;
;; > *The next sections build the rendering pipeline piece by piece.
;; > To see results first and return for the implementation,
;; > skip to [Scatter Plots](#scatter-plots).*
;; ---

;; ## Theme and Colors
;;
;; Background colors, grid colors, and a categorical palette.

(def ggplot-palette
  "Default categorical colors, matching the palette from
  [ggplot2](https://ggplot2.tidyverse.org/), the R plotting library."
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

(defn fmt-name
  "Format a keyword as a readable name: :sepal-length -> \"sepal length\"."
  [k]
  (str/replace (name k) #"[-_]" " "))

(defn color-for
  "Look up the color for a categorical value from the palette."
  [categories val]
  (let [idx (.indexOf categories val)]
    (nth ggplot-palette (mod (if (neg? idx) 0 idx) (count ggplot-palette)))))

(def shape-syms [:circle :square :triangle :diamond])

(defn render-shape-elem
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
   :margin 25 :margin-multi 30 :panel-size 200 :legend-width 100
   ;; Ticks
   :tick-spacing-x 60 :tick-spacing-y 40
   ;; Points
   :point-radius 2.5 :point-opacity 0.7
   :point-stroke "none" :point-stroke-width 0
   ;; Bars and lines
   :bar-opacity 0.7 :line-width 2 :grid-stroke-width 1.5
   ;; Annotations
   :annotation-stroke "#333" :annotation-dash "4,3" :band-opacity 0.08
   ;; Statistics
   :bin-method :sturges ;; Sturges' rule: bin count = ceil(log2(n) + 1)
   :domain-padding 0.05
   ;; Labels and titles
   :label-font-size 11 :title-font-size 13
   :label-offset 18 :title-offset 18
   ;; Fallback
   :default-color "#333"})

;; ### ⚙️ Column Type Detection

(defn column-type
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
  User-specified values always win.
  Fixed aesthetic values (strings, numbers) are split into :fixed-color, :fixed-size
  so downstream code only sees column references in :color, :size."
  [v]
  (if-not (:data v)
    v
    (let [ds (:data v)
          x-type (or (:x-type v) (column-type ds (:x v)))
          y-type (or (:y-type v) (when (and (:y v) (not= (:x v) (:y v)))
                                   (column-type ds (:y v))))
          ;; Color: only resolve column type for keyword (column) colors
          color-val (:color v)
          color-is-col? (and color-val (column-ref? color-val))
          c-type (when color-is-col?
                   (or (:color-type v) (column-type ds color-val)))
          fixed-color (when (and color-val (not color-is-col?)) color-val)
          ;; Size: split into column-ref vs fixed
          size-val (:size v)
          size-is-col? (and size-val (column-ref? size-val))
          fixed-size (when (and size-val (not size-is-col?)) size-val)
          ;; Group only by column-ref colors
          group (or (:group v)
                    (when (= c-type :categorical) [color-val])
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
            ;; one categorical, one numerical → strip plot
            (not= x-type y-type)
            [:point :identity]
            ;; both numerical → scatter
            :else [:point :identity])
          mark (or (:mark v) default-mark)
          stat (or (:stat v) default-stat)]
      (assoc v :x-type x-type :y-type y-type :color-type c-type
             :group group :mark mark :stat stat
             ;; Normalize: column-ref stays, fixed goes to :fixed-*
             :color (when color-is-col? color-val)
             :fixed-color fixed-color
             :size (when size-is-col? size-val)
             :fixed-size fixed-size))))

;; ### 🧪 What `resolve-view` Produces
;;
;; Both columns are numerical and the color is categorical, so
;; `resolve-view` infers grouping by species and defaults to a scatter:

(let [v (first (-> iris
                   (view [[:sepal-length :sepal-width]])
                   (lay (point {:color :species}))))]
  (select-keys (resolve-view v) [:x-type :y-type :color-type :group :mark :stat]))

;; When the color column is numerical, there is no grouping:

(let [v (first (-> iris
                   (view [[:sepal-length :sepal-width]])
                   (lay (point {:color :petal-length}))))]
  (select-keys (resolve-view v) [:x-type :y-type :color-type :group :mark :stat]))

;; When no mark is specified, `resolve-view` infers one from the column types.
;; A numerical column mapped to both x and y becomes a histogram:

(let [v (first (view iris [[:sepal-length :sepal-length]]))]
  (select-keys (resolve-view v) [:x-type :y-type :mark :stat]))

;; A categorical column on its own becomes a bar chart with counting:

(let [v (first (view iris [[:species :species]]))]
  (select-keys (resolve-view v) [:x-type :y-type :mark :stat]))

;; A categorical x with a numerical y becomes a strip plot:

(let [v (first (view iris [[:species :sepal-length]]))]
  (select-keys (resolve-view v) [:x-type :y-type :mark :stat]))

;; A user-specified mark always overrides the inference:

(let [v (first (-> iris
                   (view [[:sepal-length :sepal-width]])
                   (lay {:mark :line :stat :lm})))]
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

(defn numeric-extent
  "Min/max pair from a numeric column."
  [col]
  [(dfn/reduce-min col) (dfn/reduce-max col)])

(defn group-by-columns
  "Split dataset by grouping columns, apply f to each group.
  f takes [dataset group-key]. group-key is nil when no grouping,
  a single value for one column, a vector for multiple columns."
  [ds group-cols f]
  (if (seq group-cols)
    (for [[gk gds] (tc/group-by ds group-cols {:result-type :as-map})]
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

(defn prepare-points
  "Clean data, compute domains, group by columns, extract color/size/shape values.
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
;; A scale maps a domain (data values) to a range (pixel positions).
;; Wadogo builds the scale and provides ticks and formatting.
;; Scale type is auto-detected from the domain.

;; ### ⚙️ `make-scale`

(defn numeric-domain?
  [dom]
  (and (sequential? dom) (seq dom) (number? (first dom))))

(defn categorical-domain?
  [dom]
  (and (sequential? dom) (seq dom) (not (number? (first dom)))))

(defn scale-kind
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
  ;; A band scale divides the pixel range into equal-width bands,
  ;; one per category, with padding between them.
  (ws/scale :bands {:domain domain :range pixel-range}))

(defmethod make-scale :linear [domain pixel-range _]
  (ws/scale :linear {:domain domain :range pixel-range}))

(defn pad-domain
  "Add padding to a numeric domain. Additive for linear scales, multiplicative for log scales."
  [[lo hi] scale-spec]
  (let [log? (= :log (:type scale-spec))
        [a b] (if log? [(Math/log lo) (Math/log hi)] [lo hi])
        pad (* 0.05 (max 1e-6 (- b a)))
        from (if log? #(Math/exp %) identity)]
    [(from (- a pad)) (from (+ b pad))]))

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
;; A coord composes two scales with a coordinate system,
;; mapping `(coord dx dy)` to `[px py]`.
;; All marks call it the same way, so they don't need to know
;; whether the plot is cartesian, flipped, or polar.
;;
;; `make-coord` builds this function from:
;;
;; - **coord-type** -- `:cartesian`, `:flip`, or `:polar`
;; - **sx, sy** -- Wadogo scale for each axis (data -> pixels)
;; - **pw, ph** -- panel width and height in pixels
;; - **m** -- margin in pixels

(defmulti render-grid
  "Render grid lines for a panel."
  (fn [coord-type sx sy pw ph m cfg] coord-type))

(defmulti make-coord
  "Build a coordinate function: (coord data-x data-y) -> [pixel-x pixel-y].
   Dispatches on coord-type keyword."
  (fn [coord-type sx sy pw ph m] coord-type))

(defmulti make-coord-px
  "Build a pixel-space reprojection function for coordinate systems that need
  arc interpolation (e.g. polar). Returns nil for coordinate systems where
  bars can be drawn as simple rectangles."
  (fn [coord-type sx sy pw ph m] coord-type))

(defmethod make-coord-px :default [_ _ _ _ _ _] nil)

(defmulti show-ticks?
  "Whether to show tick labels for this coordinate system."
  (fn [coord-type] coord-type))

(defmethod show-ticks? :default [_] true)

(defmethod make-coord :cartesian [_ sx sy pw ph m]
  (fn [dx dy] [(sx dx) (sy dy)]))

;; ### 🧪 Coord in Action
;;
;; Data-space to pixel-space on a 600x400 canvas with 25px margin:

(let [sx (ws/scale :linear {:domain [0 10] :range [25 575]})
      sy (ws/scale :linear {:domain [0 100] :range [375 25]})
      coord-fn (make-coord :cartesian sx sy 600 400 25)]
  {:origin (coord-fn 0 0)
   :center (coord-fn 5 50)
   :top-right (coord-fn 10 100)})

(defmethod render-grid :default [_ sx sy pw ph m cfg]
  (render-grid :cartesian sx sy pw ph m cfg))

;; ---

;; ## Drawing Marks
;;
;; `render-mark` is a multimethod dispatching on mark keyword.
;; `:point` is defined here; `:bar`, `:line`, `:rect`, `:text` follow later.

(defmulti render-mark
  "Render a mark layer. Dispatches on mark keyword.
  ctx contains :coord-fn, :all-colors, :tooltip-fn, :shape-categories, :sx, :sy, :coord-px, :position."
  (fn [mark stat ctx] mark))

(defmethod render-mark :point [_ stat ctx]
  (let [{:keys [coord-fn all-colors tooltip-fn shape-categories cfg]} ctx
        cfg (or cfg defaults)
        size-bufs (keep :sizes (:points stat))
        size-scale (when (seq size-bufs)
                     (let [all-sizes (dtype/concat-buffers size-bufs)
                           lo (dfn/reduce-min all-sizes) hi (dfn/reduce-max all-sizes)
                           span (max 1e-6 (- (double hi) (double lo)))]
                       (fn [v] (+ 2.0 (* 6.0 (/ (- (double v) (double lo)) span))))))
        shape-map (when shape-categories
                    (into {} (map-indexed (fn [i c] [c (nth shape-syms (mod i (count shape-syms)))])
                                          shape-categories)))]
    (into [:g]
          (mapcat (fn [{:keys [color xs ys sizes shapes row-indices]}]
                    (let [c (if color (color-for all-colors color) (or (:fixed-color ctx) (:default-color cfg)))]
                      (for [i (range (count xs))
                            :let [[px py] (coord-fn (nth xs i) (nth ys i))
                                  r (if sizes (size-scale (nth sizes i)) (or (:fixed-size ctx) (:point-radius cfg)))
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
;; Hiccup SVG elements, circles for `:point`.
;; Overriding `:point-radius` in the config makes them larger:

(let [sx (ws/scale :linear {:domain [0 10] :range [25 575]})
      sy (ws/scale :linear {:domain [0 50] :range [375 25]})
      stat {:points [{:xs [2 5 8] :ys [10 30 20]}]}
      cfg (merge defaults {:point-radius 6})
      ctx {:coord-fn (make-coord :cartesian sx sy 600 400 25)
           :cfg cfg
           :all-colors nil :tooltip-fn nil :shape-categories nil}
      marks (render-mark :point stat ctx)]
  (kind/hiccup [:svg {:width 600 :height 400
                      "xmlns" "http://www.w3.org/2000/svg"}
                [:rect {:x 0 :y 0 :width 600 :height 400 :fill (:bg theme)}]
                marks]))

;; ---

;; ## Axes and Grid Lines
;;
;; Multimethods, extended later with polar grids and categorical ticks.

;; ### ⚙️ Tick Helpers

(defn format-ticks
  "Format tick values: strip .0 when all ticks are whole numbers."
  [sx ticks]
  (let [labels (ws/format sx ticks)]
    (if (every? #(== (Math/floor %) %) ticks)
      (mapv #(str (long %)) ticks)
      labels)))

(defn tick-count
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
    (into [:g {:font-size (:font-size theme) :fill "#666" :font-family "sans-serif"}]
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
    (into [:g {:font-size (:font-size theme) :fill "#666" :font-family "sans-serif"}]
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

(defmulti render-annotation
  "Render a single annotation view. Dispatches on (:mark ann).
   ann-ctx contains :coord-fn, :x-domain, :y-domain."
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
;; 7. **Pixel reprojection** -- for coordinate systems that need arc interpolation.
;; 8. **SVG** -- emit background, grid, annotations, marks, ticks.

(defn render-panel
  [panel-views pw ph m & {:keys [x-domain y-domain show-x? show-y? all-colors
                                 tooltip-fn shape-categories cfg]
                          :or {show-x? true show-y? true cfg defaults}}]
  (let [v1 (first panel-views)
        coord-type (or (:coord v1) :cartesian)
        x-scale-spec (or (:x-scale v1) {:type :linear})
        y-scale-spec (or (:y-scale v1) {:type :linear})

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
                         (:domain x-scale-spec) ;; user-specified domain
                         (if (categorical-domain? (first stat-x-domains))
                           (distinct (mapcat identity stat-x-domains))
                           (let [lo (reduce min (map first stat-x-domains))
                                 hi (reduce max (map second stat-x-domains))]
                             (pad-domain [lo hi] x-scale-spec))))
        merged-y-dom (or y-domain
                         (:domain y-scale-spec) ;; user-specified domain
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
        coord-fn (make-coord coord-type sx sy pw ph m)

        ;; Pixel-space reprojection for coordinate systems that need arc interpolation
        coord-px (make-coord-px coord-type sx sy pw ph m)

        annotation-views (filter #(annotation-marks (:mark %)) panel-views)

        ctx {:coord-fn coord-fn :all-colors all-colors :tooltip-fn tooltip-fn
             :shape-categories shape-categories :sx sx :sy sy :coord-px coord-px
             :cfg cfg}
        ann-ctx {:coord-fn coord-fn :x-domain merged-x-dom :y-domain merged-y-dom :cfg cfg}]

    [:g
     ;; Background
     [:rect {:x 0 :y 0 :width pw :height ph :fill (:bg theme)}]

     ;; Grid
     (render-grid coord-type sx sy pw ph m cfg)

     ;; Annotations, dispatch through render-annotation multimethod
     (into [:g]
           (for [ann annotation-views]
             (render-annotation ann ann-ctx)))

     ;; Data layers, dispatch through render-mark multimethod
     (into [:g]
           (mapcat (fn [{:keys [view stat]}]
                     (let [mark (:mark view)
                           mark-ctx (cond-> (assoc ctx :position (or (:position view) :dodge))
                                      (:fixed-color view) (assoc :fixed-color (:fixed-color view))
                                      (:fixed-size view) (assoc :fixed-size (:fixed-size view)))]
                       [(render-mark mark stat mark-ctx)]))
                   view-stats))

     ;; Tick labels
     (when (and show-x? (show-ticks? coord-type))
       (render-x-ticks (if cat-x? :categorical :numeric) sx pw ph m cfg))
     (when (and show-y? (show-ticks? coord-type))
       (render-y-ticks (if cat-y? :categorical :numeric) sy pw ph m cfg))]))

;; ### 🧪 A Single Panel
;;
;; `render-panel` directly: background, grid, data, ticks:

(kind/hiccup
 [:svg {:width 600 :height 400
        "xmlns" "http://www.w3.org/2000/svg"}
  (render-panel
   (-> {:x [1 2 3 4 5] :y [2 4 3 5 4]}
       (view [[:x :y]])
       (lay (point)))
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

(defn panel-from-ctx
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

(defn infer-layout [views]
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

(defn render-legend [categories color-fn & {:keys [x y title]}]
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
;; Wraps SVG as hiccup. This initial definition is a passthrough;
;; it is **redefined** in the [Interactivity](#interactivity) section
;; to add tooltip and brush support.

(defn wrap-plot
  "Wrap SVG content as hiccup. Interaction modes are added later."
  [modes svg-content]
  (kind/hiccup svg-content))

;; Without interaction modes, `wrap-plot` passes SVG through as hiccup:

(wrap-plot #{} [:svg {:width 40 :height 20}
                [:circle {:cx 20 :cy 10 :r 5 :fill "#333"}]])

;; ### ⚙️ Domain Helpers

(defn collect-domain
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

(defn compute-global-y-domain
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
;; to show -- data, columns, marks, column bindings. `plot` handles *how*
;; to show it: canvas size, scale sharing, interactivity. This boundary
;; mirrors [ggplot2](https://ggplot2.tidyverse.org/)'s separation of
;; `aes` + `geom` from `theme` and rendering options.

(defn plot
  "Render views as SVG. Options: :width :height :scales :coord :tooltip :brush :config
  :x-label :y-label :title — axis labels auto-infer from column names, override here."
  ([views] (plot views {}))
  ([views {:keys [width height scales coord tooltip brush config
                  x-label y-label title] :as opts}]
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
         pw (if multi?
              (double (:panel-size cfg))
              (double (/ width cols)))
         ph (if multi?
              (double (:panel-size cfg))
              (double (/ height rows)))
         stat-results (mapv (comp compute-stat #(assoc % :cfg cfg) resolve-view) non-ann-views)
         all-colors (let [color-views (filter #(and (column-ref? (:color %)) (:data %)) views)]
                      (when (seq color-views)
                        (distinct (mapcat #((:data %) (:color %)) color-views))))
         color-cols (distinct (keep #(when (column-ref? (:color %)) (:color %)) views))
         shape-col (first (keep #(when (column-ref? (:shape %)) (:shape %)) views))
         shape-categories (when shape-col
                            (distinct (mapcat (fn [v] (when (and (:data v) (column-ref? (:shape v)))
                                                        (map #(get % shape-col) (tc/rows (:data v) :as-maps))))
                                              views)))
         coord-type-main (or (:coord (first views)) :cartesian)
         tooltip-fn (when tooltip
                      (fn [row] (str/join ", " (map (fn [[k v]] (str (name k) ": " v)) row))))
         scale-mode (or scales :shared)
         x-scale-spec (or (:x-scale (first non-ann-views)) {:type :linear})
         y-scale-spec (or (:y-scale (first non-ann-views)) {:type :linear})
         global-x-doms (or (:domain x-scale-spec)
                           (when (#{:shared :free-y} scale-mode)
                             (collect-domain stat-results :x-domain x-scale-spec)))
         global-y-doms (or (:domain y-scale-spec)
                           (when (#{:shared :free-x} scale-mode)
                             (compute-global-y-domain stat-results views y-scale-spec)))
         ;; Axis labels: auto-infer unless multi-variable (SPLOM), allow override
         auto-label? (not multi?)
         eff-x-label (or x-label
                         (:label x-scale-spec)
                         (when auto-label?
                           (when-let [x (first x-vars)] (fmt-name x))))
         eff-y-label (or y-label
                         (:label y-scale-spec)
                         (when auto-label?
                           (when-let [y (first y-vars)] (fmt-name y))))
         eff-title title
         ;; Extra space for labels
         x-label-pad (if eff-x-label (:label-offset cfg) 0)
         y-label-pad (if eff-y-label (:label-offset cfg) 0)
         title-pad (if eff-title (:title-offset cfg) 0)
         legend-w (if (or all-colors shape-categories) (:legend-width cfg) 0)
         total-w (+ y-label-pad (* cols pw) legend-w)
         total-h (+ title-pad (* rows ph) x-label-pad)
         ctx {:non-ann-views non-ann-views :ann-views ann-views
              :pw pw :ph ph :m m :rows rows :cols cols
              :all-colors all-colors :tooltip-fn tooltip-fn
              :shape-categories shape-categories :coord-type coord-type-main
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
          ;; Plot title
          (when eff-title
            [:text {:x (+ y-label-pad (/ (* cols pw) 2))
                    :y 14
                    :text-anchor "middle" :font-size (:title-font-size cfg)
                    :fill "#333" :font-weight "bold" :font-family "sans-serif"}
             eff-title])
          ;; Y-axis label (rotated)
          (when eff-y-label
            (let [cy (+ title-pad (/ (* rows ph) 2))]
              [:text {:x 12 :y cy
                      :text-anchor "middle" :font-size (:label-font-size cfg)
                      :fill "#333" :font-family "sans-serif"
                      :transform (str "rotate(-90,12," cy ")")}
               eff-y-label]))
          ;; X-axis label
          (when eff-x-label
            [:text {:x (+ y-label-pad (/ (* cols pw) 2))
                    :y (- total-h 3)
                    :text-anchor "middle" :font-size (:label-font-size cfg)
                    :fill "#333" :font-family "sans-serif"}
             eff-x-label])
          ;; Legend (offset by label padding)
          (when all-colors
            (render-legend all-colors #(color-for all-colors %)
                           :x (+ y-label-pad (* cols pw) 10) :y (+ title-pad 20)
                           :title (first color-cols)))
          (when shape-categories
            (let [y-off (+ title-pad (if all-colors (+ 20 (* (count all-colors) 16) 10) 20))
                  x-off (+ y-label-pad (* cols pw) 10)]
              (into [:g {:font-family "sans-serif" :font-size 10}
                     (when shape-col [:text {:x x-off :y (- y-off 5) :fill "#333" :font-size 9}
                                      (fmt-name shape-col)])]
                    (for [[i cat] (map-indexed vector shape-categories)
                          :let [sh (nth shape-syms (mod i (count shape-syms)))]]
                      [:g (render-shape-elem sh (+ x-off 5) (+ y-off (* i 16)) 4
                                             (if all-colors (color-for all-colors cat) "#333") {})
                       [:text {:x (+ x-off 15) :y (+ y-off (* i 16) 4) :fill "#333"}
                        (str cat)]]))))
          ;; Panels (offset by label padding)
          [:g {:transform (str "translate(" y-label-pad "," title-pad ")")}
           (into [:g] (remove nil? (arrange-panels layout-type ctx)))]]]
     (wrap-plot (cond-> #{} tooltip (conj :tooltip) brush (conj :brush)) svg-content))))

;; ---

;; ## Scatter Plots
;;
;; The simplest complete plots: data, views, a mark, and `plot`.

;; ### 🧪 Scatter from Inline Data
;;
;; A map of columns works as data -- `view` wraps it into a dataset:

(-> {:x [1 2 3 4 5 6]
     :y [2 4 3 5 4 6]
     :group ["a" "a" "a" "b" "b" "b"]}
    (view [[:x :y]])
    (lay (point {:color :group}))
    plot)

;; ### 🧪 Iris Scatter
;;
;; Same pipeline, now with a real dataset and no color:

(-> iris
    (view [[:sepal-length :sepal-width]])
    (lay (point))
    plot)

;; ### 🧪 Colored Scatter
;;
;; Adding `:color` to the `point` spec splits the data by species:

(-> iris
    (view [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    plot)

;; ### 🧪 Fixed Aesthetics
;;
;; When an aesthetic is a keyword, it binds to a column.
;; When it's a string or number, it's a fixed value —
;; no grouping, no legend entry:

(-> {:x [1 2 3 4] :y [2 4 3 5]}
    (view [[:x :y]])
    (lay (point {:color "steelblue" :size 6}))
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

(defmethod compute-stat :bin [{:keys [data x x-type group cfg] :as view}]
  (let [clean (cond-> (tc/drop-missing data [x])
                (= x-type :categorical) (tc/map-columns x [x] str))
        xs-col (clean x)]
    (if (zero? (tc/row-count clean))
      {:bins [] :max-count 0 :x-domain [0 1] :y-domain [0 1]}
      (let [all-bin-data (group-by-columns
                          clean (or group [])
                          (fn [ds gv]
                            (let [hist (stats/histogram (ds x) (:bin-method (or cfg defaults)))]
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
               (lay (histogram))
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
  (let [{:keys [coord-fn all-colors cfg]} ctx
        cfg (or cfg defaults)]
    (into [:g]
          (mapcat (fn [{:keys [color bin-maps]}]
                    (let [c (if color (color-for all-colors color) (or (:fixed-color ctx) (:default-color cfg)))]
                      (for [{:keys [min max count]} bin-maps
                            :let [[x1 y1] (coord-fn min 0)
                                  [x2 y2] (coord-fn max 0)
                                  [x3 y3] (coord-fn max count)
                                  [x4 y4] (coord-fn min count)]]
                        [:polygon {:points (str x1 "," y1 " " x2 "," y2 " "
                                                x3 "," y3 " " x4 "," y4)
                                   :fill c :opacity (:bar-opacity cfg)}])))
                  (:bins stat)))))
;; ### 🧪 Histogram
;;
;; A single column means x = y, which auto-selects `:bin`:

(-> (view iris :sepal-length)
    (lay (histogram))
    plot)

;; ### 🧪 Colored Histogram
;;
;; Color splits bins per group, dodging them side by side:

(-> (view iris :sepal-length)
    (lay (histogram {:color :species}))
    plot)

;; ### ⚙️ Flip
;;
;; Swaps x and y axes. Histograms become horizontal, bar charts grow sideways.

(defmethod make-coord :flip [_ sx sy pw ph m]
  (fn [dx dy] [(sx dy) (sy dx)]))

(defmethod render-grid :flip [_ sx sy pw ph m cfg]
  (render-grid :cartesian sx sy pw ph m cfg))

;; ### 🧪 Flipped Histogram
;;
;; `:flip` swaps the axes -- bars grow leftward:
;;

(-> (view iris :sepal-length)
    (lay (histogram))
    (coord :flip)
    plot)

;; ---

;; ## Line Charts
;;
;; Lines connecting raw data points, using the `:line` mark
;; with `:identity` stat. The same `render-mark :line` handles
;; both raw lines and regression/loess fits.

;; ### ⚙️ Line Constructors

(defn line
  "Line mark with identity stat."
  ([] {:mark :line :stat :identity})
  ([opts] (merge {:mark :line :stat :identity} opts)))

;; ### ⚙️ `render-mark` `:line`

(defmethod render-mark :line [_ stat ctx]
  (let [{:keys [coord-fn all-colors cfg]} ctx
        cfg (or cfg defaults)]
    (into [:g]
          (concat
           (when-let [lines (:lines stat)]
             (for [{:keys [color x1 y1 x2 y2]} lines
                   :let [c (if color (color-for all-colors color) (or (:fixed-color ctx) (:default-color cfg)))
                         [px1 py1] (coord-fn x1 y1)
                         [px2 py2] (coord-fn x2 y2)]]
               [:line {:x1 px1 :y1 py1 :x2 px2 :y2 py2
                       :stroke c :stroke-width (:line-width cfg)}]))
           (when-let [pts (:points stat)]
             (for [{:keys [color xs ys]} pts
                   :let [c (if color (color-for all-colors color) (or (:fixed-color ctx) (:default-color cfg)))
                         projected (sort-by first (map (fn [x y] (coord-fn x y)) xs ys))]]
               [:polyline {:points (str/join " " (map (fn [[px py]] (str px "," py)) projected))
                           :stroke c :stroke-width (:line-width cfg) :fill "none"}]))))))

;; ### 🧪 Line Chart (Connecting Raw Points)

(-> (view {:year [2018 2019 2020 2021 2022]
           :sales [10 15 13 17 20]}
          [[:year :sales]])
    (lay (line))
    plot)

;; ### 🧪 Colored Line Chart

(-> (view {:year [2018 2019 2020 2021 2022 2018 2019 2020 2021 2022]
           :sales [10 15 13 17 20 8 12 11 14 18]
           :region ["East" "East" "East" "East" "East"
                    "West" "West" "West" "West" "West"]}
          [[:year :sales]])
    (lay (line {:color :region}))
    plot)

;; ---

;; ## Layers
;;
;; Multiple layers duplicate the views -- here's what that looks like
;; internally, and how it renders.

;; ### 🧪 What `lay` Produces
;;
;; Each layer gets its own copy of every base view:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point) (line))
    (->> (mapv #(select-keys % [:x :y :mark :stat]))))

;; ### 🧪 Scatter + Line Overlay

(-> (view {:year [2018 2019 2020 2021 2022]
           :sales [10 15 13 17 20]}
          [[:year :sales]])
    (lay (point) (line))
    plot)

;; ### 🧪 Colored Scatter + Line

(-> (view {:year [2018 2019 2020 2021 2022 2018 2019 2020 2021 2022]
           :sales [10 15 13 17 20 8 12 11 14 18]
           :region ["East" "East" "East" "East" "East"
                    "West" "West" "West" "West" "West"]}
          [[:year :sales]])
    (lay (point {:color :region}) (line {:color :region}))
    plot)

;; ---

;; ## Regression and Smooth Lines
;;
;; Regression ([OLS](https://en.wikipedia.org/wiki/Ordinary_least_squares) via [Fastmath](https://github.com/generateme/fastmath)) and smooth curves ([LOESS](https://en.wikipedia.org/wiki/Local_regression) interpolation).

;; ### ⚙️ Regression Constructors

(defn lm
  ([] {:mark :line :stat :lm})
  ([opts] (merge {:mark :line :stat :lm} opts)))

(defn loess
  ([] {:mark :line :stat :loess})
  ([opts] (merge {:mark :line :stat :loess} opts)))

;; ### ⚙️ `compute-stat` `:lm`

(defn fit-lm
  "Fit a linear model on xs-col and ys-col, return {:x1 :y1 :x2 :y2}."
  [xs-col ys-col]
  (let [model (regr/lm ys-col xs-col)
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

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (lm {:color :species}))
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
                              sxs (deduped x) sys (deduped y)
                              f (interp/interpolation :loess sxs sys)
                              x-lo (double (dfn/reduce-min sxs))
                              x-hi (double (dfn/reduce-max sxs))
                              step (/ (- x-hi x-lo) (dec n-sample))
                              sample-xs (dfn/+ x-lo (dfn/* (dtype/make-reader :float64 n-sample idx) step))
                              sample-ys (dtype/emap f :float64 sample-xs)]
                          {:xs sample-xs :ys sample-ys}))
            results (group-by-columns clean (or group [])
                                      (fn [ds gv]
                                        (cond-> (fit-loess ds)
                                          gv (assoc :color gv))))]
        {:points results
         :x-domain (numeric-extent (clean x))
         :y-domain (numeric-extent (clean y))}))))

;; ### 🧪 What `:loess` Returns
;;
;; Sampled points along the fitted curve, one set per color group:

(-> (view iris [[:sepal-length :petal-length]])
    (lay (loess {:color :species}))
    first
    resolve-view
    compute-stat
    (update :points (fn [pts] (mapv #(-> % (update :xs count) (update :ys count)) pts)))
    kind/pprint)

;; ### 🧪 Scatter + Regression
;;
;; `lay` applies two layers to the same data -- one scatter, one regression line:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species})
         (lm {:color :species}))
    plot)

;; ### 🧪 Mixed Fixed and Column Aesthetics
;;
;; Column-bound color on scatter, fixed color on the regression line.
;; The black line gets no legend entry:

(-> iris
    (view [[:sepal-length :sepal-width]])
    (lay (point {:color :species})
         (lm {:color "black"}))
    plot)

;; ### 🧪 Smooth Curve (Loess)
;;
;; LOESS fits a local curve instead of a straight line:

(-> (view iris [[:sepal-length :petal-length]])
    (lay (point {:color :species})
         (loess {:color :species}))
    plot)

;; ### 🧪 Triple Layer (Scatter + Regression + Smooth)
;;
;; Three layers on the same data -- `lay` accepts any number of layers:

(-> (view iris [[:sepal-length :petal-length]])
    (lay (point {:color :species})
         (lm {:color :species})
         (loess {:color :species}))
    plot)

;; ---

;; ## Categorical Charts
;;
;; Wadogo band scales for bar positioning. `compute-stat :count` tallies
;; categories; `render-mark :rect` handles two positioning modes:
;; [dodge](https://ggplot2.tidyverse.org/reference/position_dodge.html)
;; (bars side by side) and stack (bars on top of each other).

;; ### ⚙️ Bar Constructors

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
              grouped (tc/group-by clean-c all-group-cols {:result-type :as-map})
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
        (let [grouped (tc/group-by clean [x] {:result-type :as-map})
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
    (lay (bar))
    first
    resolve-view
    compute-stat
    kind/pprint)

;; ### ⚙️ Categorical Bar Helpers

(defn arc-polygon-points
  "Subdivide a bar into many points along an arc for polar rendering.
  SVG has no filled-arc primitive, so we approximate the curved wedge
  as a polygon with n-seg segments along each edge."
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

(defn render-bar-elem
  "Render a bar as rect (cartesian) or polygon (polar)."
  [coord-px x-lo x-hi py-lo py-hi color cfg]
  (let [opacity (:bar-opacity cfg)]
    (if coord-px
      [:polygon {:points (arc-polygon-points coord-px x-lo x-hi py-lo py-hi 20)
                 :fill color :opacity opacity}]
      [:rect {:x x-lo :y (min py-lo py-hi)
              :width (- x-hi x-lo)
              :height (Math/abs (- py-lo py-hi))
              :fill color :opacity opacity}])))

(defn render-categorical-bars
  [stat ctx]
  (let [{:keys [all-colors sx sy coord-px position cfg]} ctx
        cfg (or cfg defaults)
        bw (ws/data sx :bandwidth)
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
                    (let [c (if color (color-for all-colors color) (or (:fixed-color ctx) (:default-color cfg)))]
                      (for [{cat :category cnt :count} counts
                            :when (or (= position :stack) (pos? cnt))
                            :let [band-info (sx cat true)
                                  band-start (:rstart band-info)
                                  band-end (:rend band-info)
                                  band-mid (/ (+ band-start band-end) 2.0)]]
                        (if (= position :stack)
                          (let [base (get @cum-y cat 0)
                                py-lo (sy base)
                                py-hi (sy (+ base cnt))
                                x-lo (- band-mid (* bw 0.4))
                                x-hi (+ band-mid (* bw 0.4))]
                            (swap! cum-y assoc cat (+ base cnt))
                            (render-bar-elem coord-px x-lo x-hi py-lo py-hi c cfg))
                          (let [active (get active-map cat)
                                n-active (count active)
                                active-idx (.indexOf ^java.util.List active bi)
                                sub-bw (/ (* bw 0.8) (max 1 n-active))
                                x-lo (+ (- band-mid (/ (* n-active sub-bw) 2.0)) (* active-idx sub-bw))
                                x-hi (+ x-lo sub-bw)
                                py-lo (sy 0)
                                py-hi (sy cnt)]
                            (render-bar-elem coord-px x-lo x-hi py-lo py-hi c cfg))))))
                  (range) (:bars stat)))))

(defn render-value-bars
  [stat ctx]
  (let [{:keys [all-colors sx sy coord-px position cfg]} ctx
        cfg (or cfg defaults)
        bw (ws/data sx :bandwidth)
        groups (:points stat)
        n-groups (count groups)
        sub-bw (/ (* bw 0.8) (max 1 n-groups))
        cum-y (atom {})]
    (into [:g]
          (mapcat (fn [gi {:keys [color xs ys]}]
                    (let [c (if color (color-for all-colors color) (or (:fixed-color ctx) (:default-color cfg)))]
                      (for [i (range (count xs))
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
    (into [:g {:font-size (:font-size theme) :fill "#666" :font-family "sans-serif"}]
          (map (fn [t label]
                 [:text {:x (sx t) :y (- ph 2) :text-anchor "middle"} label])
               ticks labels))))

(defmethod render-y-ticks :categorical [_ sy pw ph m cfg]
  (let [ticks (ws/ticks sy)
        labels (map str ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666" :font-family "sans-serif"}]
          (map (fn [t label]
                 [:text {:x (- m 3) :y (+ (sy t) 3) :text-anchor "end"} label])
               ticks labels))))

;; ### 🧪 Bar Chart
;;
;; The simplest categorical plot -- `:count` tallies species, band scale positions bars:

(-> (view iris :species)
    (lay (bar))
    plot)

;; ### 🧪 Colored Bar Chart
;;
;; Color = same column as x: each bar gets its species color:

(-> (view iris :species)
    (lay (bar {:color :species}))
    plot)

;; ### 🧪 Stacked Bar Chart
;;
;; Color = a *different* column: bars stack by drive type within each class:

(-> (view mpg :class)
    (lay (stacked-bar {:color :drv}))
    plot)

;; ### 🧪 Strip Plot (Categorical x, Continuous y)
;;
;; A categorical x with a numerical y: points line up along the category axis:

(-> (view iris [[:species :sepal-length]])
    (lay (point {:color :species}))
    plot)

;; ### 🧪 Horizontal Strip Plot (Flipped)
;;
;; `:flip` works on categorical plots too:

(-> (view iris [[:species :sepal-length]])
    (lay (point {:color :species}))
    (coord :flip)
    plot)

;; ### 🧪 Numeric-as-Categorical
;;
;; `:x-type :categorical` forces a numeric column onto a band scale:

(-> (view mpg :cyl)
    (lay (bar {:x-type :categorical}))
    plot)

;; ### 🧪 Value Bar (Pre-Aggregated Data)
;;
;; When data is already aggregated, `value-bar` skips the `:count` stat:

(-> (view {:fruit ["Apple" "Banana" "Cherry"]
           :amount [30 20 45]}
          [[:fruit :amount]])
    (lay (value-bar {:color :fruit}))
    plot)

;; ### 🧪 Value Bar (Plain)
;;
;; Same data without color -- single-color bars:

(-> (view {:fruit ["Apple" "Banana" "Cherry"]
           :amount [30 20 45]}
          [[:fruit :amount]])
    (lay (value-bar))
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

;; ### 🧪 Auto-Detection in Action
;;
;; Diagonal views (x=y) become histograms, off-diagonal become scatters:

(-> (view iris (cross [:sepal-length :sepal-width] [:sepal-length :sepal-width]))
    (->> (mapv #(select-keys (resolve-view %) [:x :y :mark :stat]))))

;; ### ⚙️ Filtering and Conditional Specs

(defn where [views pred] (filter pred views))
(defn where-not [views pred] (remove pred views))

(defn when-diagonal
  "Apply spec to diagonal views only.
  spec can be a map (merged into each diagonal view)
  or a function (called with the diagonal views, returns replacement views)."
  [views spec]
  (if (fn? spec)
    (let [diag (filterv diagonal? views)
          off (filterv (complement diagonal?) views)]
      (into (vec (spec diag)) off))
    (mapv (fn [v] (if (diagonal? v) (merge v spec) v)) views)))

(defn when-off-diagonal
  "Apply spec to off-diagonal views only.
  spec can be a map (merged into each off-diagonal view)
  or a function (called with the off-diagonal views, returns replacement views)."
  [views spec]
  (if (fn? spec)
    (let [diag (filterv diagonal? views)
          off (filterv (complement diagonal?) views)]
      (into diag (vec (spec off))))
    (mapv (fn [v] (if-not (diagonal? v) (merge v spec) v)) views)))

;; ### 🧪 Filtering Views

(let [vs (-> iris
             (view (cross [:sepal-length :sepal-width] [:sepal-length :sepal-width]))
             (when-off-diagonal {:color :species}))]
  (mapv #(select-keys % [:x :y :mark :color]) vs))

;; Both `when-diagonal` and `when-off-diagonal` also accept a function.
;; When given a function, it receives the matching views and returns
;; replacement views. This is how layers can target specific cells:

(let [vs (-> iris
             (view (cross [:sepal-length :sepal-width]
                          [:sepal-length :sepal-width]))
             (when-off-diagonal #(lay % (point) (lm))))]
  (mapv #(select-keys % [:x :y :mark :stat]) vs))

;; `where` and `where-not` filter views by predicate -- useful for
;; keeping only certain column pairings:

(-> iris
    (view (cross [:sepal-length :sepal-width :petal-length]
                 [:sepal-length :sepal-width :petal-length]))
    (where-not diagonal?)
    count)

;; ### ⚙️ Column-Pair Helpers

(defn distribution
  "Create diagonal views (x=y) for each column, used for histograms."
  [data & cols]
  (view data (mapv (fn [c] [c c]) cols)))

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
     (let [groups (tc/group-by (:data v) [col] {:result-type :as-map})]
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
     (let [groups (tc/group-by (:data v) [row-col col-col] {:result-type :as-map})]
       (map (fn [[gk gds]]
              (assoc v :data gds
                     :facet-row (get gk row-col)
                     :facet-col (get gk col-col)))
            groups)))
   views))

;; ### 🧪 Faceting in Action

(-> iris
    (view [[:sepal-length :sepal-width]])
    (facet :species)
    kind/pprint)

;; ### ⚙️ `arrange-panels` `:multi-variable`

(defmethod arrange-panels :multi-variable [_ ctx]
  (let [{:keys [non-ann-views ann-views pw ph x-vars y-vars rows cols coord-type]} ctx
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
         (when (and (zero? ri) (show-ticks? (or coord-type :cartesian)))
           [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
                   :font-size 9 :fill "#333"} (fmt-name xv)])
         (when (and (= ci (dec cols)) (show-ticks? (or coord-type :cartesian)))
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

;; :::{.column-screen-inset-right}
(-> (view iris (cross iris-quantities iris-quantities))
    (lay {:color :species})
    plot)
;; :::

;; This is useful when diagonal and off-diagonal cells need different
;; aesthetics. Here, scatters are colored by species while the
;; overall distribution on the diagonal stays uncolored:

(-> (view iris (cross [:sepal-length :sepal-width :petal-length]
                      [:sepal-length :sepal-width :petal-length]))
    (when-off-diagonal {:color :species})
    plot)

;; ### 🧪 Faceted Scatter
;;
;; One panel per species -- `facet` splits views by a column:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (facet :species)
    plot)

;; ### 🧪 Row × Column Faceting
;;
;; `facet-grid` maps two columns to rows and columns of panels:

(-> (view tips [[:total-bill :tip]])
    (lay (point {:color :day}))
    (facet-grid :smoker :sex)
    (plot {:width 600 :height 500}))

;; ### 🧪 Faceted Scatter with Free Y-Scale
;;
;; `:free-y` lets each panel fit its own y-domain:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (facet :species)
    (plot {:scales :free-y}))

;; ### 🧪 Faceted Scatter with Free X-Scale
;;
;; Likewise for the x-axis:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (facet :species)
    (plot {:scales :free-x}))

;; ### 🧪 Faceted Scatter with Free Scales (Both Axes)
;;
;; Both axes free -- each panel zooms to its own data:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (facet :species)
    (plot {:scales :free}))

;; ### 🧪 Faceted Histogram
;;
;; Distribution of sepal length per species -- `facet` composes with histograms:

(-> (view iris :sepal-length)
    (lay (histogram {:color :species}))
    (facet :species)
    plot)

;; ### 🧪 Faceted Regression
;;
;; Scatter with regression line per species -- `facet` composes with `lay`:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species}) (lm {:color :species}))
    (facet :species)
    plot)

;; ### 🧪 SPLOM with Regression
;;
;; Scatter plots with trend lines on off-diagonal panels, histograms on
;; the diagonal. Passing a function to `when-off-diagonal` applies the
;; layers only to off-diagonal views, leaving diagonal views for inference:

(-> (view iris (cross [:sepal-length :sepal-width :petal-length]
                      [:sepal-length :sepal-width :petal-length]))
    (when-off-diagonal {:color :species})
    (when-off-diagonal #(lay % (point) (lm)))
    plot)

;; ### 🧪 Faceted Bar Chart
;;
;; Drivetrain counts by cylinder count -- `facet` composes with bar charts.
;; Using mpg data:

(-> (view mpg [[:drv :drv]])
    (lay (bar {:color :drv}))
    (facet :cyl)
    plot)

;; ---

;; ## Scales and Coordinates
;;
;; Scale setters, log scales, and polar coordinate examples.

;; ### ⚙️ Scale and Coord Setters

(defn scale
  "Set scale options for :x or :y across all views.
  Accepts (views channel type), (views channel type opts), or (views channel opts-map).
  opts-map may include :type, :domain, and :label."
  ([views channel type-or-opts]
   (if (map? type-or-opts)
     (scale views channel (or (:type type-or-opts) :linear) (dissoc type-or-opts :type))
     (scale views channel type-or-opts {})))
  ([views channel type opts]
   (let [k (case channel :x :x-scale :y :y-scale)]
     (mapv #(assoc % k (merge {:type type} opts)) views))))

;; ### ⚙️ `make-scale` `:log`

(defmethod make-scale :log [domain pixel-range _]
  (ws/scale :log {:domain domain :range pixel-range}))

;; ### 🧪 How Setters Modify Views
;;
;; `scale` and `coord` add keys to each view map:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point))
    (scale :x :log)
    (coord :polar)
    first
    (select-keys [:x :y :mark :x-scale :coord]))

;; ### 🧪 Log Scale

(let [data (tc/dataset {:x (mapv #(Math/pow 10 %) (range 0.0 3.01 0.1))
                        :y (mapv #(+ % (* 0.5 (rand))) (range 0.0 3.01 0.1))})]
  (-> (view data [[:x :y]])
      (lay (point))
      (scale :x :log)
      plot))

;; ### 🧪 Log Scale (Y-Axis)

(-> (view {:x [1 2 3 4 5] :y [1 10 100 1000 10000]}
          [[:x :y]])
    (lay (point))
    (scale :y :log)
    plot)

;; ### 🧪 Custom Domain
;;
;; The `scale` function also accepts an options map with `:domain`
;; to clip or expand the axis range:

(-> iris
    (view [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (scale :x {:domain [4 8]})
    plot)

;; ### 🧪 Axis Titles
;;
;; Axis labels are auto-inferred from column names.
;; Override with plot options:

(-> iris
    (view [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (plot {:x-label "Sepal Length (cm)"
           :y-label "Sepal Width (cm)"
           :title "Iris Measurements"}))

;; Or via the scale constructor — the label travels with the scale:

(-> iris
    (view [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (scale :x {:label "Length (cm)"})
    plot)

;; ### ⚙️ Polar
;;
;; [Polar coordinates](https://en.wikipedia.org/wiki/Polar_coordinate_system)
;; map x to angle and y to radius. Bars become wedges, scatters wrap into a disc.

(defmethod show-ticks? :polar [_] false)

(defmethod make-coord :polar [_ sx sy pw ph m]
  (let [cx (/ pw 2.0) cy (/ ph 2.0)
        r-max (- (min cx cy) m)
        x-lo (double m) x-span (double (- pw m m))
        y-lo (double m) y-span (double (- ph m m))]
    (fn [dx dy]
      (let [px (sx dx) py (sy dy)
            ;; Normalize to [0,1]: t-angle sweeps the full circle,
            ;; t-radius goes from center (0) to edge (1).
            t-angle (/ (- px x-lo) (max 1.0 x-span))
            t-radius (/ (- (+ y-lo y-span) py) (max 1.0 y-span))
            angle (* 2.0 Math/PI t-angle)
            radius (* r-max t-radius)]
        [(+ cx (* radius (Math/cos (- angle (/ Math/PI 2.0)))))
         (+ cy (* radius (Math/sin (- angle (/ Math/PI 2.0)))))]))))

(defmethod make-coord-px :polar [_ sx sy pw ph m]
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

;; ### 🧪 Polar Scatter
;;
;; `coord :polar` wraps the same scatter into polar space:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (coord :polar)
    plot)

;; ### 🧪 Polar Bar Chart ([Rose / Coxcomb](https://en.wikipedia.org/wiki/Coxcomb_diagram))
;;
;; Bars become wedges -- bar width maps to angle, height to radius:

(-> (view iris :species)
    (lay (bar))
    (coord :polar)
    plot)

;; ### 🧪 Polar Stacked Bar Chart
;;
;; Stacking works the same way in polar coords:

(-> (view mpg :class)
    (lay (stacked-bar {:color :drv}))
    (coord :polar)
    plot)

;; ---

;; ## Annotations and Text
;;
;; Reference lines, bands, and text labels.

;; ### ⚙️ Annotation Constructors

(defn text
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

(defn vband
  "Vertical reference band between x1 and x2."
  [x1 x2] {:mark :band-v :x1 x1 :x2 x2})

;; ### 🧪 Annotation Specs
;;
;; Each annotation constructor returns a plain map:

[(hline 3.0)
 (vline 6.0)
 (hband 2.5 3.5)
 (vband 5.5 6.5)]

;; ### ⚙️ `render-annotation` methods

(defmethod render-annotation :rule-h [ann {:keys [coord-fn x-domain cfg]}]
  (let [cfg (or cfg defaults)
        [x1 y1] (coord-fn (first x-domain) (:value ann))
        [x2 y2] (coord-fn (if (categorical-domain? x-domain)
                            (last x-domain)
                            (second x-domain))
                          (:value ann))]
    [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2
            :stroke (:annotation-stroke cfg) :stroke-width 1
            :stroke-dasharray (:annotation-dash cfg)}]))

(defmethod render-annotation :rule-v [ann {:keys [coord-fn y-domain cfg]}]
  (let [cfg (or cfg defaults)
        [x1 y1] (coord-fn (:value ann) (first y-domain))
        [x2 y2] (coord-fn (:value ann)
                          (if (categorical-domain? y-domain)
                            (last y-domain)
                            (second y-domain)))]
    [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2
            :stroke (:annotation-stroke cfg) :stroke-width 1
            :stroke-dasharray (:annotation-dash cfg)}]))

(defmethod render-annotation :band-h [ann {:keys [coord-fn x-domain cfg]}]
  (let [cfg (or cfg defaults)
        [x1 y1] (coord-fn (first x-domain) (:y1 ann))
        [x2 y2] (coord-fn (second x-domain) (:y2 ann))]
    [:rect {:x (min x1 x2) :y (min y1 y2)
            :width (Math/abs (- x2 x1)) :height (Math/abs (- y2 y1))
            :fill (:annotation-stroke cfg) :opacity (:band-opacity cfg)}]))

(defmethod render-annotation :band-v [ann {:keys [coord-fn y-domain cfg]}]
  (let [cfg (or cfg defaults)
        [x1 y1] (coord-fn (:x1 ann) (first y-domain))
        [x2 y2] (coord-fn (:x2 ann) (second y-domain))]
    [:rect {:x (min x1 x2) :y (min y1 y2)
            :width (Math/abs (- x2 x1)) :height (Math/abs (- y2 y1))
            :fill (:annotation-stroke cfg) :opacity (:band-opacity cfg)}]))

;; ### ⚙️ `render-mark` `:text`

(defmethod render-mark :text [_ stat ctx]
  (let [{:keys [coord-fn all-colors cfg]} ctx
        cfg (or cfg defaults)]
    (into [:g {:font-size 9 :fill (:default-color cfg) :text-anchor "middle"}]
          (mapcat (fn [{:keys [color xs ys labels]}]
                    (let [c (if color (color-for all-colors color) (or (:fixed-color ctx) (:default-color cfg)))]
                      (for [i (range (count xs))
                            :let [[px py] (coord-fn (nth xs i) (nth ys i))]]
                        [:text {:x px :y (- py 5) :fill c}
                         (str (nth labels i))])))
                  (:points stat)))))

;; ### 🧪 Annotations

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species})
         (hline 3.0)
         (hband 2.5 3.5)
         (vline 6.0)
         (vband 5.5 6.5))
    plot)

;; ### 🧪 Text Labels at Group Means

(let [means (-> iris
                (tc/group-by :species)
                (tc/aggregate {:sepal-length #(dfn/mean (% :sepal-length))
                               :sepal-width #(dfn/mean (% :sepal-width))
                               :species #(first (% :species))}))]
  (-> (view iris [[:sepal-length :sepal-width]])
      (lay (point {:color :species}))
      (concat (-> (view means [[:sepal-length :sepal-width]])
                  (lay (text :species))))
      plot))

;; ---

;; ## More Aesthetics
;;
;; Size and shape channels.

;; ### 🧪 Bubble Chart (Size Aesthetic)
;;
;; `:size` maps a numerical column to circle radius:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species :size :petal-length}))
    plot)

;; ### 🧪 Shape Aesthetic
;;
;; `:shape` maps a categorical column to marker shape:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species :shape :species}))
    plot)

;; ---

;; ## Interactivity
;;
;; Tooltips and brushing via [Scittle](https://github.com/babashka/scittle).

;; ### ⚙️ Interaction Scripts
;;
;; [Scittle](https://github.com/babashka/scittle) scripts for tooltip
;; and brush interactions. Redefining `wrap-plot` to support them.

(defn tooltip-script
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

(defn brush-script
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

(def tooltip-css
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

;; ### 🧪 Tooltips on Hover
;;
;; `:tooltip true` adds mouseover labels showing data values:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (plot {:tooltip true}))

;; ### 🧪 Brushable Scatter
;;
;; `:brush true` adds a drag-to-select rectangle:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (plot {:brush true}))

;; ### 🧪 Brushable SPLOM (Cross-Panel)
;;
;; Brush in one panel highlights the same rows across all panels:

;; :::{.column-screen-inset-right}
(-> (view iris (cross iris-quantities iris-quantities))
    (lay {:color :species})
    (plot {:brush true}))
;; ::

;; ### 🧪 Brushable Facets
;;
;; Cross-panel brushing works on faceted plots too:

(-> (view iris [[:sepal-length :sepal-width]])
    (lay (point {:color :species}))
    (facet :species)
    (plot {:brush true}))

;; ---

;; ## Edge Cases
;;
;; Graceful handling of missing data, single points, and single categories.

;; ### 🧪 Missing Data Tolerance
;;
;; `nil` values are dropped silently -- no crash, just fewer points:

(let [data (tc/dataset {:x [1 2 nil 4 5 6 nil 8]
                        :y [2 4 6 nil 10 12 14 16]})]
  (-> (view data [[:x :y]])
      (lay (point))
      plot))

;; ### 🧪 Single Point
;;
;; Edge case: one data point still produces a valid plot:

(-> (view {:x [5] :y [10]}
          [[:x :y]])
    (lay (point))
    plot)

;; ### 🧪 Single Category
;;
;; Edge case: one bar still renders with correct axis:

(-> (view {:cat ["A"] :val [42]}
          [[:cat :val]])
    (lay (value-bar))
    plot)

;; ---

;; ## Reflection
;;
;; ### 📖 What seemed to work
;;
;; **Stat-driven domains.**
;; One of the trickier parts of building a plotting system is figuring out
;; axis ranges. Raw data has a natural domain, but statistics create new
;; dimensions: binning produces counts, regression produces fitted values.
;; Here, each stat returns its own domain -- for instance, `compute-stat :bin`
;; includes `{:y-domain [0 28]}` so the y-axis scales to counts rather than
;; to the original data values. This means the renderer never needs to know
;; which stat produced the data; it just uses whatever domain the stat provides.
;;
;; **Wadogo for scales.**
;; Rather than implementing linear interpolation, tick generation, and label
;; formatting, the notebook delegates all of that to
;; [Wadogo](https://github.com/generateme/wadogo). A single call to `ws/scale`
;; gives us a function from domain values to pixel positions, plus `ws/ticks`
;; and `ws/format` for axis rendering. Log scales, band padding for
;; categorical axes, and datetime support come along for free.
;;
;; **One coord function.**
;; Each coordinate system -- Cartesian, flipped, polar -- is just a function
;; `(coord dx dy)` that returns `[px py]`. Mark renderers decompose their
;; geometry into points and call this function; they never branch on coordinate
;; type. A bar becomes four projected corners; a polar bar becomes a polygon
;; that approximates the curved wedge by interpolating points along the arc.
;; This keeps the rendering code simple and makes it straightforward to
;; add new coordinate systems later.
;;
;; **Inference through `resolve-view`.**
;; A single function, `resolve-view`, fills in everything the renderer needs:
;; column types, grouping, mark, and stat. Each property follows the same
;; pattern: `(or user-override inferred)`. If you set `:mark :point`, that's
;; what you get; if you don't, the system looks at column types and picks a
;; sensible default. Two numerical columns become a scatter, a diagonal pair
;; (x = y) becomes a histogram, a categorical column becomes a bar chart.
;; All visual constants -- colors, margins, radii -- live in one `defaults`
;; map that can be overridden per-plot via `:config`.
;;
;; **Axis labels from column names.**
;; Standalone and faceted plots auto-infer axis titles from column names
;; via `fmt-name` (e.g. `:sepal-length` → "sepal length"). SPLOM grids
;; skip this since they already show column headers. The `scale`
;; constructor and `plot` options both accept overrides, and custom
;; domains work via `(scale :x {:domain [4 8]})`.
;;
;; ### 📖 Composition, reviewed
;;
;; The compositional core of this notebook is small: `view` and `cross`
;; handle the *what* (which columns, which pairings), while `lay`
;; and the mark constructors handle the *how* (which marks, which stats).
;; Faceting, `scale`, `coord`, and view selectors like `when-off-diagonal`
;; round out the user-facing API. After building the whole thing, a few
;; observations stand out.
;;
;; **`view` accepts several forms.**
;; Two keywords for the common case `(view data :x :y)`, a vector of
;; pairs for grids `(view data [[:x :y] [:x :z]])`, a single keyword
;; for histograms, a map for extra bindings. Earlier versions had
;; separate `view` and `views` functions; unifying them removed a
;; source of confusion without losing any expressiveness.
;;
;; **`lay` unifies single and multi-layer application.**
;; Earlier versions had separate `layer` (merge one spec) and `layers`
;; (duplicate views per spec). The names were almost identical but the
;; operations differed in kind. `lay` handles both: one argument merges,
;; multiple arguments duplicate.
;;
;; **No boxplot yet.**
;; When a view pairs a categorical column with a numerical one, the system
;; defaults to a strip plot (individual points). R's `plot(factor, numeric)`
;; gives a boxplot instead, which is usually a better summary for exploring
;; distributions across groups. Adding a boxplot stat and mark would be a
;; natural next step.
;;
;; **The histogram convention.**
;; `(view data :col)` is the simple way to request a histogram --
;; it maps the same column to both x and y, which inference turns into
;; a binned distribution. The shorthand is convenient, but the underlying
;; encoding (same column twice = histogram) is a convention that needs
;; explaining. Inside `cross` results, pairs like `[:sepal-length :sepal-length]`
;; appear naturally, and `resolve-view` handles them. The `distribution`
;; helper makes multi-column cases more readable.
;;
;; **`plot` options live outside the composition.**
;; Interactivity (`:brush`, `:tooltip`), scale sharing (`:scales :free`),
;; and config overrides all pass through `plot` as options rather than
;; composing through the view pipeline. This is partly by design -- these
;; are rendering concerns, not data-mapping concerns -- but it does mean
;; that some aspects of a plot can't be built up incrementally the way
;; views and layers can. Whether that boundary is a limitation or a
;; reasonable separation of concerns is an open question.
;;
;; ### 📖 What's rough
;;
;; **Flip is a double swap.**
;; Flipped coordinates work by swapping the domains at scale-construction
;; time *and* swapping the arguments in the coord function. Both swaps are
;; necessary, and the result is correct, but the implementation is subtle
;; enough that it took a few tries to get right. A reader tracing through
;; the code has to hold both swaps in mind simultaneously.
;;
;; **`render-panel` does too much.**
;; This function is the workhorse of the system: it computes statistics,
;; merges domains, builds scales, constructs the coord function, renders
;; marks, draws axes and grid lines, and assembles everything into an SVG
;; group. `resolve-view` and `defaults` pull out some of that complexity,
;; but the function is still long and touches many concerns. Breaking it
;; into smaller steps -- a preparation phase, a rendering phase, and an
;; assembly phase -- would make it easier to understand and modify.
;;
;; **Partial validation.**
;; `view` checks that the specified columns actually exist in the
;; dataset, which catches typos early. But marks, stats, and `plot` options
;; are not validated at all -- a misspelled `:mark :piont` or `:stat :bni`
;; will fail silently or produce a confusing error deep inside `render-panel`.
;; Adding Malli schemas for view maps and plot options would catch these
;; mistakes at the point where the user makes them.
;;
;; ### 📖 Design space
;;
;; This notebook is one of several experiments in the same design space.
;;
;; [Implementing the Algebra of Graphics in Clojure](../aog_in_clojure_part1.html)
;; takes a different tradeoff: it provides richer algebraic operators (`=*`/`=+`),
;; Malli schema validation, and multi-target rendering (thi.ng/geom, Vega-Lite,
;; Plotly), but with lighter rendering capabilities (no polar coordinates,
;; stacked bars, loess, or annotations). This notebook has simpler composition
;; but more rendering features. A future library might try to combine both --
;; the algebraic expressiveness of the first approach with the rendering
;; completeness of this one.
;;
;; [Plotting Datoms: Queries as Visual Mappings](datomframes.html)
;; takes a different starting point entirely. Instead of mapping column names
;; to visual channels via view maps, it uses DataScript queries as the mapping
;; mechanism. The query itself defines how data flows into the plot, which
;; opens up interesting possibilities for visualizing graph-structured data.
;;
;; ### 📖 Open questions
;;
;; Several design questions remain open, and we'd love to hear what the
;; community thinks:
;;
;; - **Composition style:** Should the operators feel algebraic (`=*`/`=+`,
;;   as in Part 1) or explicit (`view`/`lay`, as here)? The algebraic
;;   style is more concise; the explicit style is easier to learn.
;;
;; - **Rendering targets:** Should we commit to SVG, or design for multiple
;;   backends? SVG is universal and works well with Clay, but Canvas or
;;   WebGL would be needed for large datasets.
;;
;; - **Faceting:** Is faceting part of the data algebra (another way to split
;;   views) or a separate layout concern? Right now it sits between the two --
;;   `facet` modifies views, but the grid layout is handled by `plot`.
;;
;; Feedback is welcome. This work is part of the Scicloj
;; [Real-World Data dev group](https://scicloj.github.io/docs/community/groups/real-world-data/).

;; ---
