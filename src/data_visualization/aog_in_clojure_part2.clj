^{:kindly/hide-code true
  :clay {:title "Implementing the Algebra of Graphics in Clojure - Part 2"
         :quarto {:type :post
                  :author [:daslu :claude]
                  :date "2026-02-22"
                  :description "A revised design: wadogo scales, stat-driven domains, single coord function"
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :design :wadogo]
                  :keywords [:datavis]
                  :toc true
                  :toc-depth 3
                  :toc-expand 3
                  :image "aog_iris.png"
                  :draft true}}}
(ns data-visualization.aog-in-clojure-part2
  (:require
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   [tech.v3.datatype.functional :as dfn]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.svg.core :as svg]
   [thi.ng.geom.viz.core :as viz]
   [fastmath.ml.regression :as regr]
   [fastmath.stats :as stats]
   [fastmath.interpolation :as interp]
   [clojure.string :as str]
   [scicloj.metamorph.ml.rdatasets :as rdatasets]
   [wadogo.scale :as ws]))

^{:kindly/hide-code true
  :kindly/kind :kind/hiccup}
[:style
 ".clay-dataset {
  max-height:400px;
  overflow-y: auto;
}
.printedClojure {
  max-height:400px;
  overflow-y: auto;
}
"]

;; # Introduction
;;
;; The [Algebra of Graphics](https://link.springer.com/book/10.1007/0-387-28695-0)
;; (Wilkinson, 2005) proposes that charts are built from composable pieces:
;; a dataset, aesthetic mappings (which columns map to x, y, color, etc.),
;; statistical transforms (bin, regress, smooth), and coordinate systems
;; (Cartesian, polar, flipped). Julia's
;; [AlgebraOfGraphics.jl](https://aog.makie.org/stable/) showed this can work
;; beautifully in practice: you multiply data × mappings × visual marks, and
;; the library figures out axes, legends, and layout.
;;
;; Can we bring this to Clojure? This post builds a compositional plotting library
;; from scratch — about 1300 lines of code — implementing these ideas on top of
;; [Tablecloth](https://scicloj.github.io/tablecloth/) datasets and hand-rolled
;; SVG. The design emerged from ongoing experimentation in the
;; [Scicloj](https://scicloj.github.io/) community, and is part of a broader
;; effort to develop
;; [Tableplot](https://scicloj.github.io/tableplot/) — a Clojure plotting library
;; for data science.
;;
;; > *This post is self-contained: everything you need is explained inline.
;; > A [companion post](aog_in_clojure_part1.html) explores alternative
;; > composition operators and multi-target rendering — interesting context
;; > but not required reading.*
;;
;; The central idea: a **view** is a plain Clojure map describing one layer of a plot:
;;
;; ```clojure
;; {:data iris :x :sepal-length :y :sepal-width :mark :point :color :species}
;; ```
;;
;; A plot is a vector of views. Five small functions (`cross`, `stack`, `views`,
;; `layer`, `layers`) compose views algebraically — no macros, no classes, no hidden
;; state. Since views are just maps, you can `println` them, `assoc` into them,
;; or manipulate them with any Clojure function.
;;
;; Three architectural decisions keep the rendering pipeline simple:
;;
;; **1. Wadogo for scales.** Instead of hand-rolling scale functions,
;; we delegate to [wadogo](https://github.com/generateme/wadogo) — a scale library
;; inspired by d3-scale. One call gives us ticks, formatting, band calculation,
;; and log transforms. When we need datetime scales, it's one keyword away.
;;
;; **2. Stat-driven domains.** Each statistical transform returns `{:x-domain :y-domain}`
;; alongside its output data. A histogram's y-domain is `[0 max-count]`, not the
;; raw data range. This means the renderer always gets correct axis bounds — whether
;; for identity, binning, regression, smoothing, or categorical counting.
;;
;; **3. Single coord function.** There's one projection function per coordinate system:
;; `[data-x data-y] → [pixel-x pixel-y]`. All marks decompose to points before calling
;; it. For bars, we project 4 corners into a polygon. This eliminates the M×N
;; combinatorics of marks × coordinate systems.
;;
;; Everything renders to SVG via Hiccup — no external charting libraries, no JSON
;; serialization, full pixel control. We add tooltips and brushing via inline
;; JavaScript. The result covers scatter plots, histograms, regression, smoothing,
;; categorical bars, stacked bars, faceting, SPLOM, polar coordinates, log scales,
;; annotations, size/shape aesthetics, and more. Let's build it up piece by piece.

;; ---

;; # Setup

;; ## ⚙️ Dependencies
;;
;; [Tablecloth](https://scicloj.github.io/tablecloth/) for data manipulation,
;; [Kindly](https://scicloj.github.io/kindly-noted/) for notebook rendering,
;; [Fastmath](https://github.com/generateme/fastmath) for regression and loess
;; smoothing, [thi.ng/geom](https://github.com/thi-ng/geom) for SVG generation,
;; [wadogo](https://github.com/generateme/wadogo) for scales (ticks, formatting,
;; band calculation, log transforms), and
;; [rdatasets](https://github.com/scicloj/rdatasets-clj) for sample datasets.

;; ## 📖 Datasets

(def iris (rdatasets/datasets-iris))
(def iris-cols [:sepal-length :sepal-width :petal-length :petal-width])

(def mpg (rdatasets/ggplot2-mpg))

iris

;; ---

;; # The Algebra
;;
;; As introduced above, a **view** is a plain Clojure map describing one layer
;; of a chart — its data, aesthetic mappings, mark type, and any statistical
;; transform:
;;
;; ```clojure
;; {:data iris :x :sepal-length :y :sepal-width :mark :point :color :species}
;; ```
;;
;; A plot is a vector of views. The algebra provides five small functions that
;; compose views through standard `merge` and `concat` operations. You can
;; `println` any view and understand everything. You can `assoc` or `merge`
;; views with standard Clojure functions — there's nothing hidden.

;; ## ⚙️ Combinators

(defn cross
  "Cartesian product of two sequences."
  [xs ys]
  (vec (for [x xs, y ys] [x y])))

(defn stack
  "Concatenate multiple collections into one flat vector."
  [& colls]
  (vec (apply concat colls)))

;; ## 🧪 Cross and Stack

(cross [:a :b] [:x :y])

(stack [1 2] [3 4] [5])

;; ## ⚙️ Views

(defn views
  "Bind a dataset to column pairs → vector of view maps.
  Each pair `[x-col y-col]` becomes a view `{:data data :x x-col :y y-col}`."
  [data pairs]
  (mapv (fn [[x y]] {:data data :x x :y y}) pairs))

;; ## 🧪 What a View Looks Like

;; A single view — just a map with the dataset, x column, and y column:

(kind/pprint
 (first (views iris [[:sepal-length :sepal-width]])))

;; ## ⚙️ Layer

(defn layer
  "Merge overrides into each view. This is how you add marks, aesthetics,
  and other properties to a set of views."
  [views & {:as overrides}]
  (mapv #(merge % overrides) views))

;; ## 🧪 Adding a Mark

;; `layer` merges a mark specification into every view:

(kind/pprint
 (first
  (-> (views iris [[:sepal-length :sepal-width]])
      (layer :mark :point :color :species))))

;; The view now has `:mark :point` and `:color :species` — everything we need
;; to know how to render this data.

;; ## ⚙️ Layers (Multi-Layer Composition)

(def ^:private annotation-marks
  "Mark types that represent annotations (not data-driven)."
  #{:rule-h :rule-v :band-h})

(defn layers
  "Stack multiple layers from the same base views.
  Annotation specs (hline, vline, hband) are stacked as separate views
  rather than merged into data views."
  [base-views & layer-specs]
  (let [ann-specs (filter #(and (map? %) (annotation-marks (:mark %))) layer-specs)
        data-specs (remove #(and (map? %) (annotation-marks (:mark %))) layer-specs)]
    (into (vec (apply stack (map #(layer base-views %) data-specs)))
          ann-specs)))

;; ## ⚙️ Faceting

(defn facet
  "Split each view by a categorical column. Creates one view per category,
  each with a filtered dataset and a `:facet-val` tag."
  [views col]
  (vec (mapcat
        (fn [v]
          (let [groups (-> (:data v) (tc/group-by [col]) tc/groups->map)]
            (map (fn [[gk gds]]
                   (assoc v :data gds :facet-val (get gk col)))
                 groups)))
        views)))

(defn facet-grid
  "Split each view by two categorical columns for a row × column grid."
  [views row-col col-col]
  (vec (mapcat
        (fn [v]
          (let [groups (-> (:data v) (tc/group-by [row-col col-col]) tc/groups->map)]
            (map (fn [[gk gds]]
                   (assoc v :data gds
                          :facet-row (get gk row-col)
                          :facet-col (get gk col-col)))
                 groups)))
        views)))

;; ---

;; # Geometry Specs
;;
;; Geometry constructors return maps that describe **what** to draw and **how** to
;; transform the data. They're designed to be merged into views via `layer`.
;;
;; Each spec has a `:mark` (visual element type) and optionally a `:stat`
;; (statistical transform to apply before rendering).

;; ## ⚙️ Mark Constructors

(defn point [& {:as opts}] (merge {:mark :point} opts))
(defn linear [& {:as opts}] (merge {:mark :line :stat :regress} opts))
(defn smooth [& {:as opts}] (merge {:mark :line :stat :smooth} opts))
(defn histogram [& {:as opts}] (merge {:mark :bar :stat :bin} opts))
(defn line-mark [& {:as opts}] (merge {:mark :line :stat :identity} opts))
(defn bar [& {:as opts}] (merge {:mark :rect :stat :count} opts))

(defn value-bar
  "Pre-aggregated bars: categorical x, continuous y, no counting."
  [& {:as opts}] (merge {:mark :rect :stat :identity} opts))

(defn stacked-bar
  "Stacked bar chart: bars stacked by color group."
  [& {:as opts}] (merge {:mark :rect :stat :count :position :stack} opts))

;; ## ⚙️ Annotation Constructors

(defn text-label
  "Text labels at data positions. `col` is the column used for label text."
  [col & {:as opts}] (merge {:mark :text :stat :identity :text-col col} opts))

(defn hline
  "Horizontal reference line at y = `val`."
  [val & {:as opts}] {:mark :rule-h :value val})

(defn vline
  "Vertical reference line at x = `val`."
  [val & {:as opts}] {:mark :rule-v :value val})

(defn hband
  "Horizontal reference band between y1 and y2."
  [y1 y2 & {:as opts}] {:mark :band-h :y1 y1 :y2 y2})

;; ## 🧪 What Specs Look Like

;; A geometry spec is just a map. The `layer` function merges it into views:

(point :color :species)

(histogram)

(bar :color :drv)

;; ---

;; # View Helpers
;;
;; Convenience functions for common patterns: auto-detecting mark types from
;; column structure, filtering views, and creating common column-pair patterns.

;; ## ⚙️ Auto-Detection

(defn diagonal?
  "True if a view maps the same column to both x and y."
  [v]
  (= (:x v) (:y v)))

(defn infer-defaults
  "Auto-detect mark and stat from column structure.
  Diagonal (x=y) → histogram. Off-diagonal → scatter."
  [v]
  (if (diagonal? v)
    (merge {:mark :bar :stat :bin} v)
    (merge {:mark :point :stat :identity} v)))

(defn auto
  "Apply `infer-defaults` to all views."
  [views]
  (mapv infer-defaults views))

;; ## ⚙️ Filtering and Conditional Specs

(defn where [views pred] (vec (filter pred views)))
(defn where-not [views pred] (vec (remove pred views)))

(defn when-diagonal
  "Merge spec into diagonal views only."
  [views spec]
  (mapv (fn [v] (if (diagonal? v) (merge v spec) v)) views))

(defn when-off-diagonal
  "Merge spec into off-diagonal views only."
  [views spec]
  (mapv (fn [v] (if-not (diagonal? v) (merge v spec) v)) views))

;; ## ⚙️ Column-Pair Helpers

(defn distribution
  "Create diagonal views (x=y) for each column — used for histograms."
  [data & cols]
  (views data (mapv (fn [c] [c c]) cols)))

(defn pairs
  "Upper-triangle pairs of columns — used for pairwise scatters."
  [cols]
  (vec (for [i (range (count cols))
             j (range (inc i) (count cols))]
         [(nth cols i) (nth cols j)])))

;; ## 🧪 Auto-Detection in Action

;; When you cross all columns with themselves, `auto` detects that diagonal
;; views (where x=y) should be histograms and off-diagonal should be scatters:

(let [vs (views iris (cross iris-cols iris-cols))
      auto-vs (auto vs)]
  (kind/pprint
   {:diagonal-example (select-keys (first (filter diagonal? auto-vs))
                                   [:x :y :mark :stat])
    :off-diagonal-example (select-keys (first (filter (complement diagonal?) auto-vs))
                                       [:x :y :mark :stat])}))

;; ---

;; # Scales and Coordinates
;;
;; These functions set scale types (linear, log) and coordinate systems
;; (cartesian, flip, polar) on views. They're applied before rendering.

;; ## ⚙️ Scale and Coord Setters

(defn set-scale
  "Set a scale type for :x or :y across all views."
  [views channel type & {:as opts}]
  (let [k (case channel :x :x-scale :y :y-scale)]
    (mapv #(assoc % k (merge {:type type} opts)) views)))

(defn set-coord
  "Set coordinate system: :cartesian (default), :flip, or :polar."
  [views c]
  (mapv #(assoc % :coord c) views))

;; ---

;; # Theme and Colors
;;
;; A ggplot2-inspired visual theme: gray background, white grid lines,
;; and a categorical color palette.

;; ## ⚙️ Theme Constants

(def ggplot-palette
  ["#F8766D" "#00BA38" "#619CFF" "#A855F7" "#F97316" "#14B8A6" "#EF4444" "#6B7280"])

(def theme {:bg "#EBEBEB" :grid "#FFFFFF" :font-size 8})

(defn- fmt-name
  "Format a keyword as a readable name: :sepal-length → \"sepal length\"."
  [k]
  (str/replace (name k) #"[-_]" " "))

(defn- color-for
  "Look up the color for a categorical value from the palette."
  [categories val]
  (let [idx (.indexOf (vec categories) val)]
    (nth ggplot-palette (mod (if (neg? idx) 0 idx) (count ggplot-palette)))))

;; ---

;; # Statistical Transforms
;;
;; A key architectural decision: each stat returns not just
;; its output data, but also the **domains** that should be used for axes:
;;
;; ```clojure
;; (compute-stat {:data iris :x :sepal-length :y :sepal-length :stat :bin})
;; ;; => {:bins [...] :max-count 28 :x-domain [4.3 7.9] :y-domain [0 28]}
;; ```
;;
;; The y-domain `[0 28]` comes from the bin counts, not from the data column.
;; This is what makes histograms work — the renderer uses stat-driven domains
;; instead of raw data domains.
;;
;; The `compute-stat` multimethod dispatches on the `:stat` key in each view:
;; `:identity` (pass-through), `:bin` (histogram), `:regress` (linear regression),
;; `:smooth` (loess), `:count` (categorical counting).

;; ## ⚙️ Data Cleaning Helpers

(defn- clean-vec
  "Remove entries where the value is nil or NaN."
  [xs]
  (vec (remove (fn [x] (or (nil? x) (and (number? x) (Double/isNaN (double x))))) xs)))

(defn- clean-paired
  "Remove pairs where either x or y is nil/NaN. Returns [clean-xs clean-ys extra-vecs...]."
  [xs ys & extra-vecs]
  (let [n (count xs)
        good? (fn [i]
                (let [x (nth xs i) y (nth ys i)]
                  (and (some? x) (some? y)
                       (not (and (number? x) (Double/isNaN (double x))))
                       (not (and (number? y) (Double/isNaN (double y)))))))
        idxs (filterv good? (range n))]
    (into [(mapv #(nth xs %) idxs) (mapv #(nth ys %) idxs)]
          (map (fn [ev] (when ev (mapv #(nth ev %) idxs))) extra-vecs))))

;; ## ⚙️ The compute-stat Multimethod

(defmulti compute-stat
  "Compute a statistical transform for a view.
  Dispatches on (:stat view), defaulting to :identity.
  Returns a map with transform-specific output data plus :x-domain and :y-domain."
  (fn [view] (or (:stat view) :identity)))

;; ### ⚙️ :identity — Pass-Through
;;
;; No transformation. Extracts x, y, color, size, shape values and returns them
;; grouped by color (if present). Handles categorical x coercion.

(defmethod compute-stat :identity [view]
  (let [{:keys [data x y color size shape text-col x-type]} view
        raw-xs (vec (data x)) raw-ys (vec (data y))
        raw-szs (when size (vec (data size)))
        raw-shs (when shape (vec (data shape)))
        raw-lbls (when text-col (vec (data text-col)))
        [xs0 ys szs shs lbls] (clean-paired raw-xs raw-ys raw-szs raw-shs raw-lbls)
        xs (if (= x-type :categorical) (mapv str xs0) xs0)]
    (if (empty? xs)
      {:points [] :x-domain [0 1] :y-domain [0 1]}
      (let [cat-x? (not (number? (first xs)))
            cat-y? (and (seq ys) (not (number? (first ys))))
            x-dom (if cat-x? (vec (distinct xs)) [(reduce min xs) (reduce max xs)])
            y-dom (if cat-y? (vec (distinct ys)) [(reduce min ys) (reduce max ys)])]
        (if color
          (let [raw-cs (vec (data color))
                [_ _ cleaned-cs] (clean-paired raw-xs raw-ys raw-cs)
                groups (group-by #(nth cleaned-cs %) (range (count xs)))]
            {:points (for [[c idxs] groups]
                       (cond-> {:color c
                                :xs (mapv #(nth xs %) idxs)
                                :ys (mapv #(nth ys %) idxs)}
                         szs (assoc :sizes (mapv #(nth szs %) idxs))
                         shs (assoc :shapes (mapv #(nth shs %) idxs))
                         lbls (assoc :labels (mapv #(nth lbls %) idxs))))
             :x-domain x-dom
             :y-domain y-dom})
          {:points [(cond-> {:xs xs :ys ys}
                      szs (assoc :sizes szs)
                      shs (assoc :shapes shs)
                      lbls (assoc :labels lbls))]
           :x-domain x-dom
           :y-domain y-dom})))))

;; ### ⚙️ :bin — Histogram
;;
;; Bins continuous x values using Sturges' rule. Returns bin maps and a y-domain
;; based on the maximum count — this is the stat-driven domain fix.

(defmethod compute-stat :bin [view]
  (let [{:keys [data x color]} view
        xs (clean-vec (vec (data x)))]
    (if (empty? xs)
      {:bins [] :max-count 0 :x-domain [0 1] :y-domain [0 1]}
      (if color
        (let [cs (vec (data color))
              raw-xs (vec (data x))
              pairs (filter (fn [[xv _]] (some? xv)) (map vector raw-xs cs))
              groups (group-by second pairs)
              all-bin-data (for [[c group-pairs] groups
                                 :let [vals (mapv first group-pairs)
                                       hist (stats/histogram vals :sturges)]]
                             {:color c :bin-maps (:bins-maps hist)})
              max-count (reduce max 1 (for [{:keys [bin-maps]} all-bin-data
                                            b bin-maps]
                                        (:count b)))]
          {:bins all-bin-data
           :max-count max-count
           :x-domain [(reduce min xs) (reduce max xs)]
           :y-domain [0 max-count]})
        (let [hist (stats/histogram xs :sturges)
              max-count (reduce max 1 (map :count (:bins-maps hist)))]
          {:bins [{:bin-maps (:bins-maps hist)}]
           :max-count max-count
           :x-domain [(reduce min xs) (reduce max xs)]
           :y-domain [0 max-count]})))))

;; ### ⚙️ :regress — Linear Regression
;;
;; Fits OLS regression using fastmath. Returns line endpoints per color group.
;; Guards against degenerate cases (zero-variance x, <2 points).

(defmethod compute-stat :regress [view]
  (let [{:keys [data x y color]} view
        raw-xs (vec (data x)) raw-ys (vec (data y))
        [xs ys] (clean-paired raw-xs raw-ys)
        x-range-zero? (fn [xv] (= (reduce min xv) (reduce max xv)))]
    (if (or (< (count xs) 2) (x-range-zero? xs))
      {:lines [] :x-domain (if (seq xs) [(first xs) (first xs)] [0 1])
       :y-domain (if (seq ys) [(reduce min ys) (reduce max ys)] [0 1])}
      (if color
        (let [raw-cs (vec (data color))
              [_ _ cs] (clean-paired raw-xs raw-ys raw-cs)
              groups (group-by #(nth cs %) (range (count xs)))]
          {:lines (for [[c idxs] groups
                        :let [gxs (mapv #(nth xs %) idxs)
                              gys (mapv #(nth ys %) idxs)]
                        :when (and (>= (count gxs) 2) (not (x-range-zero? gxs)))
                        :let [model (regr/lm gys gxs)
                              xmin (reduce min gxs) xmax (reduce max gxs)]]
                    {:color c
                     :x1 xmin :y1 (regr/predict model [xmin])
                     :x2 xmax :y2 (regr/predict model [xmax])})
           :x-domain [(reduce min xs) (reduce max xs)]
           :y-domain [(reduce min ys) (reduce max ys)]})
        (let [model (regr/lm ys xs)
              xmin (reduce min xs) xmax (reduce max xs)]
          {:lines [{:x1 xmin :y1 (regr/predict model [xmin])
                    :x2 xmax :y2 (regr/predict model [xmax])}]
           :x-domain [xmin xmax]
           :y-domain [(reduce min ys) (reduce max ys)]})))))

;; ### ⚙️ :smooth — Loess Curve
;;
;; Fits a loess curve using fastmath.interpolation, sampling 80 points.
;; Handles duplicate x values by averaging y.

(defmethod compute-stat :smooth [view]
  (let [{:keys [data x y color]} view
        raw-xs (vec (data x)) raw-ys (vec (data y))
        [xs ys] (clean-paired raw-xs raw-ys)]
    (if (< (count xs) 4)
      {:points [] :x-domain [0 1] :y-domain [0 1]}
      (let [n-sample 80
            dedup-sort (fn [gxs gys]
                         (let [pairs (sort-by first (map vector gxs gys))
                               groups (partition-by first pairs)
                               sxs (mapv (fn [g] (ffirst g)) groups)
                               sys (mapv (fn [g] (/ (reduce + (map second g)) (count g))) groups)]
                           [sxs sys]))
            fit-smooth (fn [gxs gys]
                         (let [[sxs sys] (dedup-sort gxs gys)
                               f (interp/interpolation :loess sxs sys)
                               xmin (first sxs) xmax (last sxs)
                               step (/ (- xmax xmin) (dec n-sample))
                               sample-xs (mapv #(+ xmin (* % step)) (range n-sample))
                               sample-ys (mapv f sample-xs)]
                           {:xs sample-xs :ys sample-ys}))]
        (if color
          (let [raw-cs (vec (data color))
                [_ _ cs] (clean-paired raw-xs raw-ys raw-cs)
                groups (group-by #(nth cs %) (range (count xs)))]
            {:points (for [[c idxs] groups
                           :let [gxs (mapv #(nth xs %) idxs)
                                 gys (mapv #(nth ys %) idxs)
                                 {:keys [xs ys]} (fit-smooth gxs gys)]]
                       {:color c :xs xs :ys ys})
             :x-domain [(reduce min xs) (reduce max xs)]
             :y-domain [(reduce min ys) (reduce max ys)]})
          (let [{:keys [xs ys]} (fit-smooth xs ys)]
            {:points [{:xs xs :ys ys}]
             :x-domain [(reduce min xs) (reduce max xs)]
             :y-domain [(reduce min ys) (reduce max ys)]}))))))

;; ### ⚙️ :count — Categorical Counting
;;
;; Counts occurrences per category. Supports coloring (grouped bars) and
;; `:x-type :categorical` coercion for numeric columns.

(defmethod compute-stat :count [view]
  (let [{:keys [data x color x-type]} view
        raw-xs (clean-vec (vec (data x)))
        xs (if (= x-type :categorical) (mapv str raw-xs) raw-xs)
        categories (vec (distinct xs))]
    (if (empty? categories)
      {:categories [] :bars [] :max-count 0 :x-domain ["?"] :y-domain [0 1]}
      (if color
        (let [all-raw (vec (data x))
              cs (vec (data color))
              pairs (filter (fn [[xv _]] (some? xv)) (map vector all-raw cs))
              xs-c (mapv (fn [[xv _]] (if (= x-type :categorical) (str xv) xv)) pairs)
              cs-c (mapv second pairs)
              pair-tuples (map vector xs-c cs-c)
              grouped (group-by identity pair-tuples)
              color-cats (sort (distinct cs-c))
              max-count (reduce max 1 (map #(count (val %)) grouped))]
          {:categories categories
           :bars (for [cc color-cats]
                   {:color cc
                    :counts (mapv (fn [cat] {:category cat :count (count (get grouped [cat cc] []))})
                                  categories)})
           :max-count max-count
           :x-domain categories
           :y-domain [0 max-count]})
        (let [counts-by-cat (mapv (fn [cat] {:category cat :count (count (filter #{cat} xs))}) categories)
              max-count (reduce max 1 (map :count counts-by-cat))]
          {:categories categories
           :bars [{:counts counts-by-cat}]
           :max-count max-count
           :x-domain categories
           :y-domain [0 max-count]})))))

;; ## 🧪 Stat-Driven Domains in Action
;;
;; Let's see what `compute-stat` returns for a histogram. Notice the y-domain
;; comes from bin counts, not data values:

(let [v {:data iris :x :sepal-length :y :sepal-length :stat :bin}
      result (compute-stat v)]
  (kind/pprint
   {:x-domain (:x-domain result)
    :y-domain (:y-domain result)
    :max-count (:max-count result)
    :n-bins (count (:bin-maps (first (:bins result))))}))

;; The y-domain is `[0 N]` where N is the tallest bin — exactly what we need
;; for the y-axis of a histogram. If we'd computed the y-domain from the raw
;; data column, we'd get `[4.3 7.9]` — completely wrong for a count axis.

;; ---

;; # Wadogo Scale Construction
;;
;; Wadogo gives us scales that handle ticks, formatting, and band calculations.
;; We auto-detect the scale type from domain values: categorical domains get
;; band scales, numeric domains get linear or log scales.

;; ## ⚙️ Scale Helpers

(defn- numeric-domain?
  [dom]
  (and (sequential? dom) (seq dom) (number? (first dom))))

(defn- categorical-domain?
  [dom]
  (and (sequential? dom) (seq dom) (not (number? (first dom)))))

(defn- make-scale
  "Build a wadogo scale from domain values and pixel range.
  scale-spec is {:type :linear} or {:type :log} etc."
  [domain pixel-range scale-spec]
  (let [scale-type (:type scale-spec :linear)]
    (cond
      (categorical-domain? domain)
      (ws/scale :bands {:domain domain :range pixel-range})

      (= scale-type :log)
      (ws/scale :log {:domain domain :range pixel-range})

      :else
      (ws/scale :linear {:domain domain :range pixel-range}))))

;; ## 🧪 What Wadogo Gives Us

;; A wadogo scale is callable (maps data → pixels) and provides ticks and formatting:

(let [s (ws/scale :linear {:domain [0 100] :range [50 550]})]
  (kind/pprint
   {:value-at-50 (s 50)
    :ticks (vec (ws/ticks s))
    :formatted (vec (ws/format s (ws/ticks s)))}))

;; Band scales handle categorical data with proper padding:

(let [s (ws/scale :bands {:domain ["A" "B" "C"] :range [50 350]})]
  (kind/pprint
   {:a-position (s "A")
    :bandwidth (ws/data s :bandwidth)
    :ticks (vec (ws/ticks s))}))

;; ---

;; # Coordinate Functions
;;
;; A coord is a single function: `[data-x data-y] → [pixel-x pixel-y]`.
;;
;; For **cartesian**, the scales already produce pixel coords, so the coord
;; just calls `(sx dx)` and `(sy dy)`.
;;
;; For **flip**, we swap: `(sx dy)` and `(sy dx)`.
;;
;; For **polar**, we normalize the pixel positions to angle and radius, then
;; compute `(cx + r·cos(θ), cy + r·sin(θ))`.
;;
;; This single-function approach means renderers never branch on coordinate type.
;; They just call `(coord x y)` and get pixels back.

;; ## ⚙️ make-coord

(defn- make-coord
  "Build a coordinate function. sx and sy are wadogo scales (data → pixel).
  Returns a function [data-x data-y] → [pixel-x pixel-y]."
  [coord-type sx sy pw ph m]
  (case coord-type
    :flip
    (fn [dx dy] [(sx dy) (sy dx)])

    :polar
    (let [cx (/ pw 2.0)
          cy (/ ph 2.0)
          r-max (- (min cx cy) m)
          x-lo (double m)
          x-span (double (- pw m m))
          y-lo (double m)
          y-span (double (- ph m m))]
      (fn [dx dy]
        (let [px (sx dx) py (sy dy)
              t-angle (/ (- px x-lo) (max 1.0 x-span))
              t-radius (/ (- (+ y-lo y-span) py) (max 1.0 y-span))
              angle (* 2.0 Math/PI t-angle)
              radius (* r-max t-radius)]
          [(+ cx (* radius (Math/cos (- angle (/ Math/PI 2.0)))))
           (+ cy (* radius (Math/sin (- angle (/ Math/PI 2.0)))))])))

    ;; default: cartesian
    (fn [dx dy] [(sx dx) (sy dy)])))

;; ---

;; # Renderers
;;
;; All renderers take a `coord` function and call it for every data point.
;; No coord-type branching anywhere — the coord function handles the
;; coordinate system transformation.

;; ## ⚙️ Shape Elements

(def ^:private shape-syms [:circle :square :triangle :diamond])

(defn- render-shape-elem [shape cx cy r fill opts]
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
    ;; default: circle
    [:circle (merge {:cx cx :cy cy :r r :fill fill} opts)]))

;; ## ⚙️ Point Renderer

(defn- render-points [stat coord all-colors & {:keys [tooltip-fn shape-categories]}]
  (let [all-sizes (seq (mapcat :sizes (:points stat)))
        size-scale (when all-sizes
                     (let [lo (reduce min all-sizes) hi (reduce max all-sizes)
                           span (max 1e-6 (- (double hi) (double lo)))]
                       (fn [v] (+ 2.0 (* 6.0 (/ (- (double v) (double lo)) span))))))
        shape-map (when shape-categories
                    (into {} (map-indexed (fn [i c] [c (nth shape-syms (mod i (count shape-syms)))])
                                          shape-categories)))]
    (into [:g]
          (mapcat (fn [{:keys [color xs ys sizes shapes]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [i (range (count xs))
                            :let [[px py] (coord (nth xs i) (nth ys i))
                                  r (if sizes (size-scale (nth sizes i)) 2.5)
                                  sh (if shapes (get shape-map (nth shapes i) :circle) :circle)
                                  base-opts {:stroke "#fff" :stroke-width 0.5 :opacity 0.7}
                                  elem (render-shape-elem sh px py r c base-opts)]]
                        (if tooltip-fn
                          (conj elem [:title (tooltip-fn {:x (nth xs i) :y (nth ys i) :color color})])
                          elem))))
                  (:points stat)))))

;; ## ⚙️ Histogram Bar Renderer
;;
;; Histogram bars are projected through the coord function as 4-corner polygons.
;; This means they automatically work with cartesian, flip, and polar coordinates.

(defn- render-bars [stat coord all-colors]
  (into [:g]
        (mapcat (fn [{:keys [color bin-maps]}]
                  (let [c (if color (color-for all-colors color) "#333")]
                    (for [{:keys [min max count]} bin-maps
                          :let [[x1 y1] (coord min 0)
                                [x2 y2] (coord max 0)
                                [x3 y3] (coord max count)
                                [x4 y4] (coord min count)]]
                      [:polygon {:points (str x1 "," y1 " " x2 "," y2 " "
                                              x3 "," y3 " " x4 "," y4)
                                 :fill c :opacity 0.7}])))
                (:bins stat))))

;; ## ⚙️ Categorical Bar Renderer
;;
;; Categorical bars use wadogo's band scale for positioning. In polar coordinates,
;; each bar is rendered as an arc-shaped wedge by "munching" — sampling points
;; along the arc and projecting them through a pixel-space polar function.

(defn- render-categorical-bars
  [stat coord sx-scale all-colors & {:keys [position coord-px sy] :or {position :dodge}}]
  (let [bw (ws/data sx-scale :bandwidth)
        n-colors (clojure.core/count (:bars stat))
        cum-y (atom {})
        active-map (when (= position :dodge)
                     (into {}
                           (for [cat (:categories stat)]
                             [cat (vec (keep-indexed
                                        (fn [bi {:keys [counts]}]
                                          (let [c (some #(when (= cat (:category %)) (:count %)) counts)]
                                            (when (and c (pos? c)) bi)))
                                        (:bars stat)))])))
        munch-arc (when coord-px
                    (fn [px-lo px-hi py-lo py-hi n-seg]
                      (let [outer (for [i (range (inc n-seg))
                                        :let [t (/ (double i) n-seg)
                                              px (+ px-lo (* t (- px-hi px-lo)))]]
                                    (coord-px px py-hi))
                            inner (for [i (range n-seg -1 -1)
                                        :let [t (/ (double i) n-seg)
                                              px (+ px-lo (* t (- px-hi px-lo)))]]
                                    (coord-px px py-lo))]
                        (str/join " " (map (fn [[x y]] (str x "," y))
                                           (concat outer inner))))))]
    (into [:g]
          (mapcat (fn [bi {:keys [color counts]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [{:keys [category count]} counts
                            :when (or (= position :stack) (pos? count))
                            :let [band-info (sx-scale category true)
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
                            (if coord-px
                              [:polygon {:points (munch-arc x-lo x-hi py-lo py-hi 20)
                                         :fill c :opacity 0.7}]
                              [:rect {:x x-lo :y (clojure.core/min py-lo py-hi)
                                      :width (- x-hi x-lo)
                                      :height (Math/abs (- py-lo py-hi))
                                      :fill c :opacity 0.7}]))
                          ;; dodge: center only non-zero bars per category
                          (let [active (get active-map category)
                                n-active (clojure.core/count active)
                                active-idx (.indexOf ^java.util.List active bi)
                                sub-bw (/ (* bw 0.8) (clojure.core/max 1 n-active))
                                x-lo (+ (- band-mid (/ (* n-active sub-bw) 2.0)) (* active-idx sub-bw))
                                x-hi (+ x-lo sub-bw)
                                py-lo (sy 0)
                                py-hi (sy count)]
                            (if coord-px
                              [:polygon {:points (munch-arc x-lo x-hi py-lo py-hi 20)
                                         :fill c :opacity 0.7}]
                              [:rect {:x x-lo :y (clojure.core/min py-lo py-hi)
                                      :width (- x-hi x-lo)
                                      :height (Math/abs (- py-lo py-hi))
                                      :fill c :opacity 0.7}]))))))
                  (range) (:bars stat)))))

;; ## ⚙️ Value Bar Renderer (Pre-Aggregated)

(defn- render-value-bars
  [stat coord sx-scale all-colors & {:keys [position coord-px sy] :or {position :dodge}}]
  (let [bw (ws/data sx-scale :bandwidth)
        groups (:points stat)
        n-groups (clojure.core/count groups)
        sub-bw (/ (* bw 0.8) (clojure.core/max 1 n-groups))
        cum-y (atom {})
        munch-arc (when coord-px
                    (fn [px-lo px-hi py-lo py-hi n-seg]
                      (let [outer (for [i (range (inc n-seg))
                                        :let [t (/ (double i) n-seg)
                                              px (+ px-lo (* t (- px-hi px-lo)))]]
                                    (coord-px px py-hi))
                            inner (for [i (range n-seg -1 -1)
                                        :let [t (/ (double i) n-seg)
                                              px (+ px-lo (* t (- px-hi px-lo)))]]
                                    (coord-px px py-lo))]
                        (str/join " " (map (fn [[x y]] (str x "," y))
                                           (concat outer inner))))))]
    (into [:g]
          (mapcat (fn [gi {:keys [color xs ys]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [i (range (clojure.core/count xs))
                            :let [cat (nth xs i)
                                  val (nth ys i)
                                  band-info (sx-scale cat true)
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
                            (if coord-px
                              [:polygon {:points (munch-arc x-lo x-hi py-lo py-hi 20)
                                         :fill c :opacity 0.7}]
                              [:rect {:x x-lo :y (clojure.core/min py-lo py-hi)
                                      :width (- x-hi x-lo)
                                      :height (Math/abs (- py-lo py-hi))
                                      :fill c :opacity 0.7}]))
                          ;; dodge
                          (let [x-lo (+ (- band-mid (/ (* n-groups sub-bw) 2.0)) (* gi sub-bw))
                                x-hi (+ x-lo sub-bw)
                                py-lo (sy 0)
                                py-hi (sy val)]
                            (if coord-px
                              [:polygon {:points (munch-arc x-lo x-hi py-lo py-hi 20)
                                         :fill c :opacity 0.7}]
                              [:rect {:x x-lo :y (clojure.core/min py-lo py-hi)
                                      :width (- x-hi x-lo)
                                      :height (Math/abs (- py-lo py-hi))
                                      :fill c :opacity 0.7}]))))))
                  (range) groups))))

;; ## ⚙️ Line Renderer (Regression + Smooth)

(defn- render-lines [stat coord all-colors]
  (into [:g]
        (concat
         (when-let [lines (:lines stat)]
           (for [{:keys [color x1 y1 x2 y2]} lines
                 :let [c (if color (color-for all-colors color) "#333")
                       [px1 py1] (coord x1 y1)
                       [px2 py2] (coord x2 y2)]]
             [:line {:x1 px1 :y1 py1 :x2 px2 :y2 py2
                     :stroke c :stroke-width 1.5}]))
         (when-let [pts (:points stat)]
           (for [{:keys [color xs ys]} pts
                 :let [c (if color (color-for all-colors color) "#333")
                       projected (sort-by first (map (fn [x y] (coord x y)) xs ys))]]
             [:polyline {:points (str/join " " (map (fn [[px py]] (str px "," py)) projected))
                         :stroke c :stroke-width 1.5 :fill "none"}])))))

;; ## ⚙️ Text Renderer

(defn- render-text [stat coord all-colors]
  (into [:g {:font-size 9 :fill "#333" :text-anchor "middle"}]
        (mapcat (fn [{:keys [color xs ys labels]}]
                  (let [c (if color (color-for all-colors color) "#333")]
                    (for [i (range (count xs))
                          :let [[px py] (coord (nth xs i) (nth ys i))]]
                      [:text {:x px :y (- py 5) :fill c}
                       (str (nth labels i))])))
                (:points stat))))

;; ---

;; # Grid, Ticks, and Legend
;;
;; Supporting visual elements for the plot panels.

;; ## ⚙️ Grid Drawing

(defn- render-grid-cartesian [sx sy pw ph m]
  (let [x-ticks (ws/ticks sx)
        y-ticks (ws/ticks sy)]
    (into [:g]
          (concat
           (for [t x-ticks :let [px (sx t)]]
             [:line {:x1 px :y1 m :x2 px :y2 (- ph m)
                     :stroke (:grid theme) :stroke-width 0.5}])
           (for [t y-ticks :let [py (sy t)]]
             [:line {:x1 m :y1 py :x2 (- pw m) :y2 py
                     :stroke (:grid theme) :stroke-width 0.5}])))))

(defn- render-grid-polar [pw ph m]
  (let [cx (/ pw 2.0) cy (/ ph 2.0)
        r-max (- (min cx cy) m)]
    (into [:g]
          (concat
           (for [i (range 1 6)
                 :let [r (* r-max (/ i 5.0))]]
             [:circle {:cx cx :cy cy :r r :fill "none"
                       :stroke (:grid theme) :stroke-width 0.5}])
           (for [i (range 8)
                 :let [a (* i (/ Math/PI 4))]]
             [:line {:x1 cx :y1 cy
                     :x2 (+ cx (* r-max (Math/cos a)))
                     :y2 (+ cy (* r-max (Math/sin a)))
                     :stroke (:grid theme) :stroke-width 0.5}])))))

;; ## ⚙️ Tick Labels

(defn- render-x-ticks [sx pw ph m]
  (let [ticks (ws/ticks sx)
        labels (ws/format sx ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666"}]
          (map (fn [t label]
                 [:text {:x (sx t) :y (- ph 2) :text-anchor "middle"} label])
               ticks labels))))

(defn- render-x-ticks-bands [sx pw ph m]
  (let [ticks (ws/ticks sx)
        labels (map str ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666"}]
          (map (fn [t label]
                 [:text {:x (sx t) :y (- ph 2) :text-anchor "middle"} label])
               ticks labels))))

(defn- render-y-ticks [sy pw ph m]
  (let [ticks (ws/ticks sy)
        labels (ws/format sy ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666"}]
          (map (fn [t label]
                 [:text {:x (- m 3) :y (+ (sy t) 3) :text-anchor "end"} label])
               ticks labels))))

;; ## ⚙️ Legend

(defn- render-legend [categories color-fn & {:keys [x y title]}]
  [:g {:font-family "sans-serif" :font-size 10}
   (when title [:text {:x x :y (- y 5) :fill "#333" :font-size 9} (fmt-name title)])
   (for [[i cat] (map-indexed vector categories)]
     [:g [:circle {:cx x :cy (+ y (* i 16)) :r 4 :fill (color-fn cat)}]
      [:text {:x (+ x 10) :y (+ y (* i 16) 4) :fill "#333"} (str cat)]])])

;; ---

;; # Panel Renderer
;;
;; `render-panel` is the heart of the rendering pipeline. It takes a set of views
;; for a single panel and produces SVG. The pipeline has four steps:
;;
;; 1. **Compute stats** for all data views → get stat-driven domains
;; 2. **Merge domains** across all views in the panel
;; 3. **Build wadogo scales** from merged domains → pixel ranges
;; 4. **Build coord function** → render each view through it
;;
;; The key insight: domains come from stats, not from raw data columns. This is
;; what makes histograms, regression, and other transforms render with correct axes.

;; ## ⚙️ Domain Padding

(defn- pad-domain
  "Add padding to a numeric domain. For log scales, use multiplicative padding."
  [[lo hi] scale-spec]
  (if (= :log (:type scale-spec))
    (let [log-lo (Math/log lo) log-hi (Math/log hi)
          pad (* 0.05 (max 1e-6 (- log-hi log-lo)))]
      [(Math/exp (- log-lo pad)) (Math/exp (+ log-hi pad))])
    (let [pad (* 0.05 (max 1e-6 (- hi lo)))]
      [(- lo pad) (+ hi pad)])))

;; ## ⚙️ render-panel

(defn render-panel
  [panel-views pw ph m & {:keys [x-domain y-domain show-x? show-y? all-colors
                                 tooltip-fn shape-categories]
                          :or {show-x? true show-y? true}}]
  (let [v1 (first panel-views)
        coord-type (or (:coord v1) :cartesian)
        x-scale-spec (or (:x-scale v1) {:type :linear})
        y-scale-spec (or (:y-scale v1) {:type :linear})
        polar? (= coord-type :polar)

        ;; Step 1: compute stats → stat-driven domains
        view-stats (for [v panel-views
                         :when (#{:point :bar :line :rect :text} (:mark v))]
                     (let [stat (compute-stat v)]
                       {:view v :stat stat}))

        ;; Step 2: merge domains from stats
        stat-x-domains (keep #(get-in % [:stat :x-domain]) view-stats)
        stat-y-domains (keep #(get-in % [:stat :y-domain]) view-stats)

        merged-x-dom (or x-domain
                         (if (categorical-domain? (first stat-x-domains))
                           (vec (distinct (mapcat identity stat-x-domains)))
                           (let [lo (reduce min (map first stat-x-domains))
                                 hi (reduce max (map second stat-x-domains))]
                             (pad-domain [lo hi] x-scale-spec))))
        merged-y-dom (or y-domain
                         (if (categorical-domain? (first stat-y-domains))
                           (vec (distinct (mapcat identity stat-y-domains)))
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

        ;; Step 3: build wadogo scales
        [x-dom' y-dom'] (if (= coord-type :flip)
                          [merged-y-dom merged-x-dom]
                          [merged-x-dom merged-y-dom])
        x-pixel-range [m (- pw m)]
        y-pixel-range [(- ph m) m]

        sx (make-scale x-dom' x-pixel-range (if (= coord-type :flip) y-scale-spec x-scale-spec))
        sy (make-scale y-dom' y-pixel-range (if (= coord-type :flip) x-scale-spec y-scale-spec))

        cat-x? (categorical-domain? x-dom')

        ;; Step 4: build coord function
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

        annotation-views (filter #(#{:rule-h :rule-v :band-h} (:mark %)) panel-views)]

    [:g
     ;; Background
     [:rect {:x 0 :y 0 :width pw :height ph :fill (:bg theme)}]

     ;; Grid
     (if polar?
       (render-grid-polar pw ph m)
       (render-grid-cartesian sx sy pw ph m))

     ;; Annotations
     (into [:g]
           (for [ann annotation-views]
             (case (:mark ann)
               :rule-h (let [[x1 y1] (coord (if (categorical-domain? merged-x-dom)
                                              (first merged-x-dom)
                                              (first merged-x-dom))
                                            (:value ann))
                             [x2 y2] (coord (if (categorical-domain? merged-x-dom)
                                              (last merged-x-dom)
                                              (second merged-x-dom))
                                            (:value ann))]
                         [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2
                                 :stroke "#333" :stroke-width 1 :stroke-dasharray "4,3"}])
               :rule-v (let [[x1 y1] (coord (:value ann)
                                            (if (categorical-domain? merged-y-dom)
                                              (first merged-y-dom)
                                              (first merged-y-dom)))
                             [x2 y2] (coord (:value ann)
                                            (if (categorical-domain? merged-y-dom)
                                              (last merged-y-dom)
                                              (second merged-y-dom)))]
                         [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2
                                 :stroke "#333" :stroke-width 1 :stroke-dasharray "4,3"}])
               :band-h (let [[x1 y1] (coord (first merged-x-dom) (:y1 ann))
                             [x2 y2] (coord (second merged-x-dom) (:y2 ann))]
                         [:rect {:x (min x1 x2) :y (min y1 y2)
                                 :width (Math/abs (- x2 x1)) :height (Math/abs (- y2 y1))
                                 :fill "#333" :opacity 0.08}])
               [:g])))

     ;; Data layers
     (into [:g]
           (mapcat (fn [{:keys [view stat]}]
                     (let [mark (:mark view)]
                       (case mark
                         :point [(render-points stat coord all-colors
                                                :tooltip-fn tooltip-fn
                                                :shape-categories shape-categories)]
                         :bar [(render-bars stat coord all-colors)]
                         :rect (if (:bars stat)
                                 [(render-categorical-bars stat coord sx all-colors
                                                           :position (or (:position view) :dodge)
                                                           :coord-px coord-px :sy sy)]
                                 [(render-value-bars stat coord sx all-colors
                                                     :position (or (:position view) :dodge)
                                                     :coord-px coord-px :sy sy)])
                         :line [(render-lines stat coord all-colors)]
                         :text [(render-text stat coord all-colors)]
                         [(render-points stat coord all-colors
                                         :tooltip-fn tooltip-fn
                                         :shape-categories shape-categories)])))
                   view-stats))

     ;; Tick labels (skip for polar)
     (when (and show-x? (not polar?))
       (if cat-x?
         (render-x-ticks-bands sx pw ph m)
         (render-x-ticks sx pw ph m)))
     (when (and show-y? (not polar?))
       (render-y-ticks sy pw ph m))]))

;; ---

;; # The Plot Function
;;
;; `plot` is the top-level function that assembles panels into a complete SVG.
;; It handles four layout modes:
;;
;; - **Single panel** — one view or layered views with shared x/y
;; - **Multi-variable grid** — SPLOM-style grid where rows/columns are data variables
;; - **Faceted** — split data by a categorical column
;; - **Grid-faceted** — row × column faceting by two columns
;;
;; It also handles color/shape legends, shared vs free scales, and optional
;; interactivity (tooltips, brushing) via inline JavaScript.

;; ## ⚙️ plot

(defn plot
  "Render views as SVG. Options: :width :height :scales :coord :tooltip :brush"
  [views & {:keys [width height scales coord tooltip brush]
            :or {width 600 height 400}}]
  (let [views (if (map? views) [views] views)
        views (if coord (mapv #(assoc % :coord coord) views) views)
        ;; Separate annotations from data views
        ann-views (filter #(annotation-marks (:mark %)) views)
        non-ann-views (remove #(annotation-marks (:mark %)) views)
        ;; Collect facet/aesthetic info
        facet-vals (distinct (remove nil? (map :facet-val non-ann-views)))
        facet-row-vals (distinct (remove nil? (map :facet-row non-ann-views)))
        facet-col-vals (distinct (remove nil? (map :facet-col non-ann-views)))
        grid-facet? (and (seq facet-row-vals) (seq facet-col-vals))
        multi-facet? (seq facet-vals)
        x-vars (distinct (map :x non-ann-views))
        y-vars (distinct (map :y non-ann-views))
        cols (cond grid-facet? (count facet-col-vals)
                   multi-facet? (count facet-vals)
                   :else (count x-vars))
        rows (cond grid-facet? (count facet-row-vals)
                   multi-facet? 1
                   :else (count y-vars))
        m 25
        pw0 (/ width cols)
        ph0 (/ height rows)
        square? (and (not grid-facet?) (not multi-facet?) (> cols 1) (> rows 1))
        pw (if square? (min pw0 ph0) pw0)
        ph (if square? (min pw0 ph0) ph0)
        ;; Compute stats + domains
        data-views (mapv compute-stat non-ann-views)
        all-colors (let [color-views (filter #(and (:color %) (:data %)) views)]
                     (when (seq color-views)
                       (vec (distinct (mapcat #(vec ((:data %) (:color %))) color-views)))))
        color-cols (distinct (remove nil? (map :color views)))
        shape-col (first (remove nil? (map :shape views)))
        shape-categories (when shape-col
                           (distinct (mapcat (fn [v] (when (:data v) (map #(get % shape-col) (tc/rows (:data v) :as-maps))))
                                             views)))
        polar? (= :polar (:coord (first views)))
        tooltip-fn (when tooltip
                     (fn [row]
                       (str/join ", " (map (fn [[k v]] (str (name k) ": " v)) row))))
        scale-mode (or scales :shared)
        global-x-doms (when (#{:shared :free-y} scale-mode)
                        (let [xd (mapcat (fn [dv] (let [d (:x-domain dv)]
                                                    (if (and (= 2 (count d)) (number? (first d)))
                                                      d (map str d)))) data-views)]
                          (if (number? (first xd))
                            (pad-domain [(reduce min xd) (reduce max xd)] (or (:x-scale (first views)) {:type :linear}))
                            (vec (distinct xd)))))
        global-y-doms (when (#{:shared :free-x} scale-mode)
                        (let [has-stacked? (some #(= :stack (:position %)) views)
                              yd (if has-stacked?
                                   (let [count-views (filter #(= :count (:stat %)) data-views)
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
                                                              (let [d (:y-domain dv)] d)))
                                                          data-views)
                                         hi (if (seq other-yd)
                                              (max max-stack (reduce max other-yd))
                                              max-stack)]
                                     [0 hi])
                                   (mapcat (fn [dv] (let [d (:y-domain dv)]
                                                      (if (and (= 2 (count d)) (number? (first d)))
                                                        d (map str d)))) data-views))]
                          (if (number? (first yd))
                            (pad-domain [(reduce min yd) (reduce max yd)]
                                        (or (:y-scale (first data-views)) {:type :linear}))
                            (vec (distinct yd)))))
        legend-w (if (or all-colors shape-categories) 100 0)
        total-w (+ (* cols pw) legend-w)
        total-h (* rows ph)
        svg-content
        [:svg {:width total-w :height total-h
               "xmlns" "http://www.w3.org/2000/svg"
               "xmlns:xlink" "http://www.w3.org/1999/xlink"
               "version" "1.1"}
         ;; Color legend
         (when all-colors
           (render-legend all-colors #(color-for all-colors %)
                          :x (+ (* cols pw) 10) :y 30
                          :title (first color-cols)))
         ;; Shape legend
         (when shape-categories
           (let [y-off (+ 30 (if all-colors (* (count all-colors) 16) 0) 10)
                 x-off (+ (* cols pw) 10)]
             (into [:g {:font-family "sans-serif" :font-size 10}
                    (when shape-col [:text {:x x-off :y (- y-off 5) :fill "#333" :font-size 9}
                                     (fmt-name shape-col)])]
                   (for [[i cat] (map-indexed vector shape-categories)
                         :let [sh (nth shape-syms (mod i (count shape-syms)))]]
                     [:g (render-shape-elem sh x-off (+ y-off (* i 16)) 4 "#666"
                                            {:stroke "none"})
                      [:text {:x (+ x-off 10) :y (+ y-off (* i 16) 4) :fill "#333"}
                       (str cat)]]))))
         ;; Panels
         (cond
           grid-facet?
           (for [[ri rv] (map-indexed vector facet-row-vals)
                 [ci cv] (map-indexed vector facet-col-vals)
                 :let [panel-views (into (vec (filter #(and (= rv (:facet-row %))
                                                            (= cv (:facet-col %))) non-ann-views))
                                         ann-views)]]
             (when (seq panel-views)
               [:g {:transform (str "translate(" (* ci pw) "," (* ri ph) ")")}
                (render-panel panel-views pw ph m
                              :show-x? (= ri (dec rows))
                              :show-y? (zero? ci)
                              :all-colors all-colors
                              :x-domain global-x-doms :y-domain global-y-doms
                              :tooltip-fn tooltip-fn
                              :shape-categories shape-categories)
                (when (zero? ri)
                  [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
                          :font-size 10 :fill "#333"} (str cv)])
                (when (= ci (dec cols))
                  [:text {:x (- pw 5) :y (/ ph 2) :text-anchor "end"
                          :font-size 10 :fill "#333"
                          :transform (str "rotate(-90," (- pw 5) "," (/ ph 2) ")")}
                   (str rv)])]))
           multi-facet?
           (for [[ci fv] (map-indexed vector facet-vals)
                 :let [fviews (into (vec (filter #(= fv (:facet-val %)) non-ann-views))
                                    ann-views)]]
             [:g {:transform (str "translate(" (* ci pw) ",0)")}
              (render-panel fviews pw ph m
                            :show-x? true :show-y? (zero? ci)
                            :all-colors all-colors
                            :x-domain global-x-doms :y-domain global-y-doms
                            :tooltip-fn tooltip-fn
                            :shape-categories shape-categories)
              [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
                      :font-size 10 :fill "#333"} (str fv)]])
           :else
           (for [[ri yv] (map-indexed vector y-vars)
                 [ci xv] (map-indexed vector x-vars)
                 :let [panel-views (into (vec (filter #(and (= xv (:x %)) (= yv (:y %))) non-ann-views))
                                         ann-views)]]
             (when (seq panel-views)
               [:g {:transform (str "translate(" (* ci pw) "," (* ri ph) ")")}
                (render-panel panel-views pw ph m
                              :show-x? (= ri (dec rows))
                              :show-y? (zero? ci)
                              :all-colors all-colors
                              :x-domain (when (<= (count x-vars) 1) global-x-doms)
                              :y-domain (when (<= (count y-vars) 1) global-y-doms)
                              :tooltip-fn tooltip-fn
                              :shape-categories shape-categories)
                (when (and (zero? ri) (not polar?))
                  [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
                          :font-size 9 :fill "#333"} (fmt-name xv)])
                (when (and (= ci (dec cols)) (not polar?))
                  [:text {:x (- pw 5) :y (/ ph 2) :text-anchor "end"
                          :font-size 9 :fill "#333"
                          :transform (str "rotate(-90," (- pw 5) "," (/ ph 2) ")")}
                   (fmt-name yv)])])))]]
    (if brush
      (kind/hiccup
       [:div {:style {:position "relative" :display "inline-block"}}
        svg-content
        [:script "
(function(){
  var svg = document.currentScript.previousElementSibling;
  var pts = svg.querySelectorAll('circle,rect.data-point,polygon');
  var drag = false, x0, y0, sel;
  svg.addEventListener('mousedown', function(e){
    var r = svg.getBoundingClientRect();
    x0 = e.clientX - r.left; y0 = e.clientY - r.top;
    sel = document.createElementNS('http://www.w3.org/2000/svg','rect');
    sel.setAttribute('fill','rgba(100,100,255,0.2)');
    sel.setAttribute('stroke','#66f');
    svg.appendChild(sel); drag = true;
  });
  svg.addEventListener('mousemove', function(e){
    if(!drag) return;
    var r = svg.getBoundingClientRect();
    var x1 = e.clientX - r.left, y1 = e.clientY - r.top;
    sel.setAttribute('x', Math.min(x0,x1));
    sel.setAttribute('y', Math.min(y0,y1));
    sel.setAttribute('width', Math.abs(x1-x0));
    sel.setAttribute('height', Math.abs(y1-y0));
  });
  svg.addEventListener('mouseup', function(e){
    if(!drag) return; drag = false;
    var bx = parseFloat(sel.getAttribute('x')), by = parseFloat(sel.getAttribute('y'));
    var bw = parseFloat(sel.getAttribute('width')), bh = parseFloat(sel.getAttribute('height'));
    svg.removeChild(sel);
    pts.forEach(function(p){
      var cx = parseFloat(p.getAttribute('cx')||p.getAttribute('x'));
      var cy = parseFloat(p.getAttribute('cy')||p.getAttribute('y'));
      if(cx>=bx && cx<=bx+bw && cy>=by && cy<=by+bh){
        p.setAttribute('opacity','1.0');
      } else {
        p.setAttribute('opacity','0.15');
      }
    });
  });
})();
"]])
      (kind/hiccup svg-content))))

;; ---

;; # Examples
;;
;; Now that all the pieces are in place, let's see the system in action.
;; Each example shows the full pipeline from data to rendered SVG.

;; ## Basic

;; ### 🧪 1. Basic Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point))
    plot)

;; ### 🧪 2. Colored Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    plot)

;; ### 🧪 3. Scatter + Regression
;;
;; `layers` stacks two marks on the same views — scatter points and
;; regression lines, both colored by species:

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point :color :species)
            (linear :color :species))
    plot)

;; ## Distributions

;; ### 🧪 4. Histogram
;;
;; The key test for stat-driven domains. The y-axis ranges over counts
;; (0 to ~28), not data values (4.3 to 7.9):

(-> (views iris [[:sepal-length :sepal-length]])
    (layer (histogram))
    plot)

;; ### 🧪 5. Colored Histogram

(-> (views iris [[:sepal-length :sepal-length]])
    (layer (histogram :color :species))
    plot)

;; ### 🧪 6. Flipped Histogram
;;
;; Flip swaps the axes — bars grow horizontally instead of vertically:

(-> (views iris [[:sepal-length :sepal-length]])
    (layer (histogram))
    (set-coord :flip)
    plot)

;; ## Grid

;; ### 🧪 7. SPLOM (Scatterplot Matrix)
;;
;; Cross all columns with themselves. `auto` detects diagonal views (histograms)
;; and off-diagonal views (scatters). Color by species:

(-> (views iris (cross iris-cols iris-cols))
    auto
    (layer :color :species)
    plot)

;; ### 🧪 8. Faceted Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (facet :species)
    plot)

;; ### 🧪 9. Row × Column Faceting

(let [tips (tc/dataset "https://raw.githubusercontent.com/mwaskom/seaborn-data/master/tips.csv")]
  (-> (views tips [["total_bill" "tip"]])
      (layer (point :color "day"))
      (facet-grid "smoker" "sex")
      (plot :width 600 :height 500)))

;; ## Categorical

;; ### 🧪 10. Categorical Bar Chart

(-> (views iris [[:species :species]])
    (layer (bar))
    plot)

;; ### 🧪 11. Colored Categorical Bar Chart

(-> (views iris [[:species :species]])
    (layer (bar :color :species))
    plot)

;; ### 🧪 12. Stacked Bar Chart

(-> (views mpg [[:class :class]])
    (layer (stacked-bar :color :drv))
    plot)

;; ### 🧪 13. Strip Plot (Categorical x, Continuous y)

(-> (views iris [[:species :sepal-length]])
    (layer (point :color :species))
    plot)

;; ### 🧪 14. Numeric-as-Categorical

(-> (views mpg [[:cyl :cyl]])
    (layer (bar :x-type :categorical))
    plot)

;; ## Advanced

;; ### 🧪 15. Log Scale
;;
;; With wadogo, log ticks show proper values (1, 10, 100...) instead of
;; transformed values:

(let [data (tc/dataset {:x (mapv #(Math/pow 10 %) (range 0.0 3.01 0.1))
                        :y (mapv #(+ % (* 0.5 (rand))) (range 0.0 3.01 0.1))})]
  (-> (views data [[:x :y]])
      (layer (point))
      (set-scale :x :log)
      plot))

;; ### 🧪 16. Polar Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (set-coord :polar)
    plot)

;; ### 🧪 17. Polar Bar Chart (Rose / Coxcomb)
;;
;; Categorical bars in polar coordinates render as arc-shaped wedges via
;; "munching" — sampling points along the arc and projecting through polar:

(-> (views iris [[:species :species]])
    (layer (bar))
    (set-coord :polar)
    plot)

;; ### 🧪 18. Polar Stacked Bar Chart

(-> (views mpg [[:class :class]])
    (layer (stacked-bar :color :drv))
    (set-coord :polar)
    plot)

;; ### 🧪 19. Bubble Chart (Size Aesthetic)

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species :size :petal-length))
    plot)

;; ### 🧪 20. Shape Aesthetic

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species :shape :species))
    plot)

;; ### 🧪 21. Annotations: Reference Line and Band

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point :color :species)
            (hline 3.0)
            (hband 2.5 3.5)
            (vline 6.0))
    plot)

;; ### 🧪 22. Text Labels at Group Means

(let [means (-> iris
                (tc/group-by :species)
                (tc/aggregate {:sepal-length #(dfn/mean (% :sepal-length))
                               :sepal-width #(dfn/mean (% :sepal-width))
                               :species #(first (% :species))}))]
  (-> (views iris [[:sepal-length :sepal-width]])
      (layers (point :color :species))
      (stack (-> (views means [[:sepal-length :sepal-width]])
                 (layer (text-label :species))))
      plot))

;; ### 🧪 23. Smooth Curve (Loess)

(-> (views iris [[:sepal-length :petal-length]])
    (layers (point :color :species)
            (smooth :color :species))
    plot)

;; ## Scales

;; ### 🧪 24. Faceted Scatter with Free Y-Scale

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (facet :species)
    (plot :scales :free-y))

;; ## Interactive

;; ### 🧪 25. Tooltips on Hover

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (plot :tooltip true))

;; ### 🧪 26. Brushable Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (plot :brush true))

;; ## Edge Cases

;; ### 🧪 27. Missing Data Tolerance

(let [data (tc/dataset {:x [1 2 nil 4 5 6 nil 8]
                        :y [2 4 6 nil 10 12 14 16]})]
  (-> (views data [[:x :y]])
      (layer (point))
      plot))

;; ---

;; # Reflection
;;
;; ## 📖 What This Design Demonstrates
;;
;; **Stat-driven domains fix histograms.** By having `compute-stat :bin`
;; return `{:y-domain [0 28]}`, the y-axis naturally scales to counts.
;; A naive approach would compute y-domain from data column values,
;; which would be wrong for any stat that produces new dimensions.
;;
;; **Wadogo eliminates hand-rolled scale code.** Functions like `linear-scale`,
;; `nice-ticks`, `categorical-scale`, and `inv-scale-label` that you'd
;; normally hand-roll are replaced by `(ws/scale ...)`, `(ws/ticks ...)`,
;; `(ws/format ...)`. Log scales get proper tick placement for free. Band
;; scales get proper padding for free. When we need datetime, it's one keyword away.
;;
;; **Single coord function eliminates M×N.** Instead of `make-projection`
;; returning 8 functions per coord type, there's one function
;; `[x y] → [px py]`. All marks decompose to points before calling it.
;; For bars, we project 4 corners into a polygon. For polar bars, we
;; munch arcs by sampling 20+ points and projecting each one.
;;
;; ## 📖 What's Still Rough
;;
;; **Flip domain swap.** We still swap x/y domains for flip at the scale
;; construction level. The coord function `(fn [dx dy] [(sx dy) (sy dx)])`
;; then swaps the arguments. This double-swap works but is subtle.
;;
;; **render-panel is 120+ lines.** It does too much: compute stats, merge
;; domains, build scales, build coord, render annotations, render data, render
;; ticks. Could be split into `compute-panel-domains`, `render-panel-content`,
;; `render-panel-axes`.
;;
;; **plot is 170+ lines.** The three `cond` branches (grid-facet, multi-facet,
;; default) share structure that could be extracted.
;;
;; **No axis labels.** Single-panel plots have tick values but no
;; "sepal length →" label. The SPLOM panels use column headers, which works,
;; but standalone scatters are unlabeled.
;;
;; ## 📖 Design Space
;;
;; There are other ways to build this. The
;; [companion post](aog_in_clojure_part1.html) explores an alternative with
;; `=*`/`=+` operators, Malli schema validation, and multi-target rendering
;; (geom, Vega-Lite, Plotly). That approach has a richer composition API but
;; lighter rendering — no polar, no stacked bars, no loess, no annotations.
;;
;; This post takes the opposite tradeoff: simpler composition surface
;; (`views`, `layer`, `layers`, `plot`) but deeper rendering capability.
;; A future library would ideally combine both: elegant composition operators
;; with a full rendering pipeline backed by wadogo + stat-driven domains +
;; a single coord function.
;;
;; ## 📖 What Comes Next
;;
;; The design here is ready to inform a real library API. Key decisions to make:
;;
;; - Which composition operators? `=*`/`=+` (algebraic feel) or `views`/`layer` (explicit)?
;; - Should we support multiple rendering targets or focus on SVG?
;; - How should we handle axis labels and titles?
;; - Should faceting be part of the algebra or a separate operation?
;;
;; As always, feedback is welcome — especially if you've tried the code and found
;; patterns that work well or feel awkward. This is happening in the
;; [Real-World Data dev group](https://scicloj.github.io/docs/community/groups/real-world-data/)
;; context, so your input shapes what gets built.

;; ---
