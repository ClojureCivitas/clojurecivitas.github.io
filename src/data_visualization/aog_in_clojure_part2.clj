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
  (:require [tablecloth.api :as tc]
            [scicloj.kindly.v4.kind :as kind]))

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
;; Wilkinson's [Grammar of Graphics](https://link.springer.com/book/10.1007/0-387-28695-0)
;; (2005) describes a formal system for specifying statistical charts as
;; a pipeline of composable components: data transformations, variable
;; mappings, geometric elements, scales, coordinate systems, and guides.
;; The book also introduces algebraic operators — cross (×), nest (/), and
;; blend (+) — for combining variables within a specification.
;;
;; Julia's [AlgebraOfGraphics.jl](https://aog.makie.org/stable/) applies
;; algebraic composition to plot specifications using two operators
;; (distinct from Wilkinson's cross/nest/blend, despite the similar
;; symbols): `*` ("merge together") combines partial specifications — a
;; dataset with a mapping with a visual mark — while `+` ("stack on top")
;; layers separate specifications in the same plot. The library infers
;; axes, legends, and layout from the composed result.
;;
;; Can we bring this to Clojure? This post builds a compositional plotting library
;; from scratch, implementing these ideas on top of
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
;; A plot is a sequence of views. Small functions compose views algebraically.
;; Since views are plain maps, you can `println` them, `assoc` into them, or
;; manipulate them with any Clojure function.
;;
;; Three architectural decisions keep the rendering pipeline simple:
;;
;; **1. Wadogo for scales.** Instead of hand-rolling scale functions,
;; we delegate to [wadogo](https://github.com/generateme/wadogo) — a scale library
;; inspired by d3-scale. One call gives us ticks, formatting, band calculation,
;; and log transforms, with support for datetime and other scale types.
;;
;; **2. Stat-driven domains.** Each statistical transform returns `{:x-domain :y-domain}`
;; alongside its output data. A histogram's y-domain is `[0 max-count]`, not the
;; raw data range. This means the renderer always gets correct axis bounds — whether
;; for identity, binning, regression, smoothing, or categorical counting.
;;
;; **3. Single coord function.** There's one projection function per coordinate system:
;; `[data-x data-y] → [pixel-x pixel-y]`. All marks decompose to points before calling
;; it. Adding a new coordinate system means writing one function, not updating every renderer.
;;
;; Everything renders to SVG via Hiccup. The post builds up incrementally:
;; first a simple scatter plot, then histograms, regression lines, categorical bars,
;; multi-panel layouts, and finally polar coordinates, annotations, and interactivity.

;; ---

;; # Setup

;; ## ⚙️ Dependencies

(ns data-visualization.aog-in-clojure-part2
  (:require
   ;; Tablecloth - dataset manipulation
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   [tech.v3.datatype.functional :as dfn]

   ;; Kindly - notebook rendering protocol
   [scicloj.kindly.v4.kind :as kind]

   ;; thi.ng/geom - SVG generation
   [thi.ng.geom.svg.core :as svg]
   [thi.ng.geom.viz.core :as viz]

   ;; Fastmath - regression and loess smoothing
   [fastmath.ml.regression :as regr]
   [fastmath.stats :as stats]
   [fastmath.interpolation :as interp]

   ;; Wadogo - scales (ticks, formatting, bands, log)
   [wadogo.scale :as ws]

   ;; RDatasets - sample datasets (iris, mpg, etc.)
   [scicloj.metamorph.ml.rdatasets :as rdatasets]

   [clojure.string :as str]))

;; ## 📖 Datasets

(def iris (rdatasets/datasets-iris))
(def iris-quantities [:sepal-length :sepal-width :petal-length :petal-width])

(def mpg (rdatasets/ggplot2-mpg))

iris
;; ---

;; # The Algebra
;;
;; A plot is a sequence of views — plain maps describing one layer each.
;; The algebra provides small functions that compose views through standard
;; `merge` and `concat` operations. You can `println` any view and understand
;; everything. You can `assoc` or `merge` views with standard Clojure functions.

;; ## ⚙️ Combinators

(defn cross
  "Cartesian product of two sequences."
  [xs ys]
  (for [x xs, y ys] [x y]))

(defn stack
  "Concatenate multiple collections into one flat sequence."
  [& colls]
  (apply concat colls))

;; ## 🧪 Cross and Stack

(cross [:a :b] [:x :y])

(stack [1 2] [3 4] [5])

;; ## ⚙️ Views

(defn views
  "Bind data to column pairs → sequence of view maps.
  Data can be a tablecloth dataset, a map of columns, or a sequence of row maps —
  anything `tc/dataset` accepts."
  [data pairs]
  (let [ds (if (tc/dataset? data) data (tc/dataset data))]
    (mapv (fn [[x y]] {:data ds :x x :y y}) pairs)))

;; ## 🧪 What a View Looks Like

;; `views` accepts anything `tc/dataset` accepts — a dataset, a map of columns,
;; or a sequence of row maps:

(-> {:x [1 2 3] :y [4 5 6]}
    (views [[:x :y]])
    kind/pprint)

;; ## ⚙️ Layer

(defn layer
  "Merge overrides into each view. This is how you add marks, aesthetics,
  and other properties to a set of views."
  [views overrides]
  (mapv #(merge % overrides) views))

;; ## 🧪 Adding a Mark

;; `layer` merges a mark specification into every view:

(-> {:x [1 2 3] :y [4 5 6] :group ["a" "a" "b"]}
    (views [[:x :y]])
    (layer {:mark :point :color :group})
    kind/pprint)

;; ## ⚙️ Mark Constructors

;; A geometry spec is a map with a `:mark` and optionally a `:stat`.
;; For now, just the point mark:

(defn point
  ([] {:mark :point})
  ([opts] (merge {:mark :point} opts)))

;; ## 🧪 Using Point

(-> {:x [1 2 3] :y [4 5 6]}
    (views [[:x :y]])
    (layer (point {:color :group}))
    kind/pprint)

;; ---

;; # Theme and Colors

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

;; ---

;; # Statistical Transforms
;;
;; Each stat method transforms a view's data and returns domain information.
;; `compute-stat` is a multimethod — we define the `:identity` pass-through
;; here and add `:bin`, `:lm`, `:loess`, and `:count` in later sections.

(defmulti compute-stat
  "Compute a statistical transform for a view.
  Dispatches on (:stat view), defaulting to :identity.
  Returns a map with transform-specific output data plus :x-domain and :y-domain."
  (fn [view] (or (:stat view) :identity)))

;; ## ⚙️ :identity — Pass-Through

(defmethod compute-stat :identity [view]
  (let [{:keys [data x y color size shape text-col x-type]} view
        clean (cond-> (tc/drop-missing data [x y])
                (= x-type :categorical) (tc/map-columns x [x] str))]
    (if (zero? (tc/row-count clean))
      {:points [] :x-domain [0 1] :y-domain [0 1]}
      (let [xs-col (clean x)
            ys-col (clean y)
            cat-x? (not (number? (first xs-col)))
            cat-y? (not (number? (first ys-col)))
            x-dom (if cat-x?
                    (distinct xs-col)
                    [(dfn/reduce-min xs-col) (dfn/reduce-max xs-col)])
            y-dom (if cat-y?
                    (distinct ys-col)
                    [(dfn/reduce-min ys-col) (dfn/reduce-max ys-col)])]
        (if color
          (let [grouped (-> clean (tc/group-by [color]) tc/groups->map)]
            {:points (for [[gk gds] grouped]
                       (cond-> {:color (gk color)
                                :xs (gds x)
                                :ys (gds y)}
                         size (assoc :sizes (gds size))
                         shape (assoc :shapes (gds shape))
                         text-col (assoc :labels (gds text-col))))
             :x-domain x-dom :y-domain y-dom})
          {:points [(cond-> {:xs xs-col :ys ys-col}
                      size (assoc :sizes (clean size))
                      shape (assoc :shapes (clean shape))
                      text-col (assoc :labels (clean text-col)))]
           :x-domain x-dom :y-domain y-dom})))))

;; ---

;; # Scales (Wadogo)
;;
;; Wadogo gives us scales that handle ticks, formatting, and band calculations.
;; We auto-detect the scale type from domain values.

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

;; ## 🧪 What Wadogo Gives Us

(let [s (ws/scale :linear {:domain [0 100] :range [50 550]})]
  (kind/pprint
   {:value-at-50 (s 50)
    :ticks (ws/ticks s)
    :formatted (ws/format s (ws/ticks s))}))

;; ---

;; # Coordinate Functions
;;
;; A coord is a single function: `[data-x data-y] → [pixel-x pixel-y]`.
;; Renderers call `(coord x y)` and get pixels back, regardless of
;; coordinate system.

(defmulti make-coord
  "Build a coordinate function: [data-x data-y] → [pixel-x pixel-y].
   Dispatches on coord-type keyword."
  (fn [coord-type sx sy pw ph m] coord-type))

(defmethod make-coord :cartesian [_ sx sy pw ph m]
  (fn [dx dy] [(sx dx) (sy dy)]))

;; ---

;; # Mark Rendering
;;
;; `render-mark` is a multimethod that renders a single mark type.
;; Each method receives the mark keyword, the stat output, and a context map.
;; We define `:point` here and add more marks in later sections.

(defmulti render-mark
  "Render a mark layer. Dispatches on mark keyword.
  ctx contains :coord, :all-colors, :tooltip-fn, :shape-categories, :sx, :sy, :coord-px, :position."
  (fn [mark stat ctx] mark))

(defmethod render-mark :point [_ stat ctx]
  (let [{:keys [coord all-colors tooltip-fn shape-categories]} ctx
        all-sizes (seq (mapcat :sizes (:points stat)))
        size-scale (when all-sizes
                     (let [lo (reduce min all-sizes) hi (reduce max all-sizes)
                           span (max 1e-6 (- (double hi) (double lo)))]
                       (fn [v] (+ 4.0 (* 12.0 (/ (- (double v) (double lo)) span))))))
        shape-map (when shape-categories
                    (into {} (map-indexed (fn [i c] [c (nth shape-syms (mod i (count shape-syms)))])
                                          shape-categories)))]
    (into [:g]
          (mapcat (fn [{:keys [color xs ys sizes shapes]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [i (range (count xs))
                            :let [[px py] (coord (nth xs i) (nth ys i))
                                  r (if sizes (size-scale (nth sizes i)) 5.0)
                                  sh (if shapes (get shape-map (nth shapes i) :circle) :circle)
                                  base-opts {:stroke "#fff" :stroke-width 0.5 :opacity 0.7}
                                  elem (render-shape-elem sh px py r c base-opts)]]
                        (if tooltip-fn
                          (conj elem [:title (tooltip-fn {:x (nth xs i) :y (nth ys i) :color color})])
                          elem))))
                  (:points stat)))))

(defmethod render-mark :default [_ stat ctx]
  (render-mark :point stat ctx))

;; ---

;; # Grid and Ticks
;;
;; Grid and tick rendering are multimethods so we can add polar grids
;; and categorical ticks in later sections.

(defmulti render-grid
  "Render grid lines for a panel."
  (fn [coord-type sx sy pw ph m] coord-type))

(defmethod render-grid :cartesian [_ sx sy pw ph m]
  (let [x-ticks (ws/ticks sx) y-ticks (ws/ticks sy)]
    (into [:g]
          (concat
           (for [t x-ticks :let [px (sx t)]]
             [:line {:x1 px :y1 m :x2 px :y2 (- ph m)
                     :stroke (:grid theme) :stroke-width 0.5}])
           (for [t y-ticks :let [py (sy t)]]
             [:line {:x1 m :y1 py :x2 (- pw m) :y2 py
                     :stroke (:grid theme) :stroke-width 0.5}])))))

(defmulti render-x-ticks
  "Render x-axis tick labels."
  (fn [domain-type sx pw ph m] domain-type))

(defmethod render-x-ticks :numeric [_ sx pw ph m]
  (let [ticks (ws/ticks sx) labels (ws/format sx ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666"}]
          (map (fn [t label]
                 [:text {:x (sx t) :y (- ph 2) :text-anchor "middle"} label])
               ticks labels))))

(defn- render-y-ticks [sy pw ph m]
  (let [ticks (ws/ticks sy) labels (ws/format sy ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666"}]
          (map (fn [t label]
                 [:text {:x (- m 3) :y (+ (sy t) 3) :text-anchor "end"} label])
               ticks labels))))

;; ---

;; # Panel Renderer
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

(defn render-panel
  [panel-views pw ph m & {:keys [x-domain y-domain show-x? show-y? all-colors
                                 tooltip-fn shape-categories]
                          :or {show-x? true show-y? true}}]
  (let [v1 (first panel-views)
        coord-type (or (:coord v1) :cartesian)
        x-scale-spec (or (:x-scale v1) {:type :linear})
        y-scale-spec (or (:y-scale v1) {:type :linear})
        polar? (= coord-type :polar)

        ;; Compute stats for data views (not annotations)
        view-stats (for [v panel-views
                         :when (and (:mark v) (not (annotation-marks (:mark v))))]
                     (let [stat (compute-stat v)]
                       {:view v :stat stat}))

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
             :shape-categories shape-categories :sx sx :sy sy :coord-px coord-px}
        ann-ctx {:coord coord :x-domain merged-x-dom :y-domain merged-y-dom}]

    [:g
     ;; Background
     [:rect {:x 0 :y 0 :width pw :height ph :fill (:bg theme)}]

     ;; Grid
     (render-grid (if polar? :polar coord-type) sx sy pw ph m)

     ;; Annotations — dispatch through render-annotation multimethod
     (into [:g]
           (for [ann annotation-views]
             (render-annotation ann ann-ctx)))

     ;; Data layers — dispatch through render-mark multimethod
     (into [:g]
           (mapcat (fn [{:keys [view stat]}]
                     (let [mark (:mark view)
                           mark-ctx (assoc ctx :position (or (:position view) :dodge))]
                       [(render-mark mark stat mark-ctx)]))
                   view-stats))

     ;; Tick labels
     (when (and show-x? (not polar?))
       (render-x-ticks (if cat-x? :categorical :numeric) sx pw ph m))
     (when (and show-y? (not polar?))
       (render-y-ticks sy pw ph m))]))

;; ---

;; # Layout
;;
;; `arrange-panels` is a multimethod that handles different layout modes.
;; `plot` infers the layout type and delegates.

(defmulti arrange-panels
  "Arrange panels into an SVG layout. Dispatches on layout type."
  (fn [layout-type ctx] layout-type))

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
  (let [{:keys [non-ann-views ann-views pw ph m all-colors tooltip-fn shape-categories
                global-x-doms global-y-doms]} ctx
        panel-views (concat non-ann-views ann-views)]
    [[:g (render-panel panel-views pw ph m
                       :all-colors all-colors
                       :x-domain global-x-doms :y-domain global-y-doms
                       :tooltip-fn tooltip-fn
                       :shape-categories shape-categories)]]))

(defn- render-legend [categories color-fn & {:keys [x y title]}]
  [:g {:font-family "sans-serif" :font-size 10}
   (when title [:text {:x x :y (- y 5) :fill "#333" :font-size 9} (fmt-name title)])
   (for [[i cat] (map-indexed vector categories)]
     [:g [:circle {:cx x :cy (+ y (* i 16)) :r 4 :fill (color-fn cat)}]
      [:text {:x (+ x 10) :y (+ y (* i 16) 4) :fill "#333"} (str cat)]])])

(defmulti wrap-plot
  "Wrap SVG content for final output. Dispatches on interaction mode keyword."
  (fn [mode svg-content] mode))

(defmethod wrap-plot :default [_ svg]
  (kind/hiccup svg))

(defn plot
  "Render views as SVG. Options: :width :height :scales :coord :tooltip :brush"
  ([views] (plot views {}))
  ([views {:keys [width height scales coord tooltip brush]
           :or {width 600 height 400}}]
   (let [views (if (map? views) [views] views)
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
         m 25
         pw0 (/ width cols) ph0 (/ height rows)
         square? (and (= layout-type :multi-variable) (> cols 1) (> rows 1))
         pw (if square? (min pw0 ph0) pw0)
         ph (if square? (min pw0 ph0) ph0)
         ;; Compute stats once for global domain inference
         stat-results (mapv compute-stat non-ann-views)
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
                         (let [xd (mapcat (fn [dv] (let [d (:x-domain dv)]
                                                     (if (and (= 2 (count d)) (number? (first d)))
                                                       d (map str d)))) stat-results)]
                           (when (seq xd)
                             (if (number? (first xd))
                               (pad-domain [(reduce min xd) (reduce max xd)] x-scale-spec)
                               (distinct xd)))))
         global-y-doms (when (#{:shared :free-x} scale-mode)
                         (let [has-stacked? (some #(= :stack (:position %)) views)
                               yd (if has-stacked?
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
                                      [0 hi])
                                    (mapcat (fn [dv] (let [d (:y-domain dv)]
                                                       (if (and (= 2 (count d)) (number? (first d)))
                                                         d (map str d)))) stat-results))]
                           (when (seq yd)
                             (if (number? (first yd))
                               (pad-domain [(reduce min yd) (reduce max yd)] y-scale-spec)
                               (distinct yd)))))
         legend-w (if (or all-colors shape-categories) 100 0)
         total-w (+ (* cols pw) legend-w)
         total-h (* rows ph)
         ctx {:non-ann-views non-ann-views :ann-views ann-views
              :pw pw :ph ph :m m :rows rows :cols cols
              :all-colors all-colors :tooltip-fn tooltip-fn
              :shape-categories shape-categories :polar? polar?
              :global-x-doms global-x-doms :global-y-doms global-y-doms
              :x-vars x-vars :y-vars y-vars
              :facet-vals facet-vals :facet-row-vals facet-row-vals :facet-col-vals facet-col-vals
              :color-cols color-cols :shape-col shape-col :scale-mode scale-mode}
         svg-content
         [:svg {:width total-w :height total-h
                "xmlns" "http://www.w3.org/2000/svg"
                "xmlns:xlink" "http://www.w3.org/1999/xlink"
                "version" "1.1"}
          (when all-colors
            (render-legend all-colors #(color-for all-colors %)
                           :x (+ (* cols pw) 10) :y 30
                           :title (first color-cols)))
          (when shape-categories
            (let [y-off (+ 30 (if all-colors (* (count all-colors) 16) 0) 10)
                  x-off (+ (* cols pw) 10)]
              (into [:g {:font-family "sans-serif" :font-size 10}
                     (when shape-col [:text {:x x-off :y (- y-off 5) :fill "#333" :font-size 9}
                                      (fmt-name shape-col)])]
                    (for [[i cat] (map-indexed vector shape-categories)
                          :let [sh (nth shape-syms (mod i (count shape-syms)))]]
                      [:g (render-shape-elem sh x-off (+ y-off (* i 16)) 4 "#666" {:stroke "none"})
                       [:text {:x (+ x-off 10) :y (+ y-off (* i 16) 4) :fill "#333"} (str cat)]]))))
          (into [:g] (remove nil? (arrange-panels layout-type ctx)))]]
     (wrap-plot (if brush :brush :default) svg-content))))

;; ---

;; # 🧪 First Plots
;;
;; With the core pipeline in place we can render scatter plots.

;; ## 🧪 Scatter from Inline Data

(-> {:x [1 2 3 4 5 6]
     :y [2 4 3 5 4 6]
     :group ["a" "a" "a" "b" "b" "b"]}
    (views [[:x :y]])
    (layer (point {:color :group}))
    plot)

;; ## 🧪 Iris Scatter

(-> iris
    (views [[:sepal-length :sepal-width]])
    (layer (point))
    plot)

;; ## 🧪 Colored Scatter

(-> iris
    (views [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    plot)

;; ---

;; # Histograms
;;
;; A histogram bins continuous data and renders counts as bars. The key insight:
;; `compute-stat :bin` returns `{:y-domain [0 max-count]}` — the y-axis scales
;; to counts, not raw data values. This is what makes histogram axes correct.

;; ## ⚙️ Histogram Constructor

(defn histogram
  ([] {:mark :bar :stat :bin})
  ([opts] (merge {:mark :bar :stat :bin} opts)))

;; ## ⚙️ compute-stat :bin

(defmethod compute-stat :bin [view]
  (let [{:keys [data x color]} view
        clean (tc/drop-missing data [x])
        xs-col (clean x)]
    (if (zero? (tc/row-count clean))
      {:bins [] :max-count 0 :x-domain [0 1] :y-domain [0 1]}
      (if color
        (let [grouped (-> (tc/drop-missing clean [color])
                          (tc/group-by [color]) tc/groups->map)
              all-bin-data (for [[gk gds] grouped
                                 :let [hist (stats/histogram (double-array (gds x)) :sturges)]]
                             {:color (gk color) :bin-maps (:bins-maps hist)})
              max-count (reduce max 1 (for [{:keys [bin-maps]} all-bin-data
                                            b bin-maps]
                                        (:count b)))]
          {:bins all-bin-data
           :max-count max-count
           :x-domain [(dfn/reduce-min xs-col) (dfn/reduce-max xs-col)]
           :y-domain [0 max-count]})
        (let [hist (stats/histogram (double-array xs-col) :sturges)
              max-count (reduce max 1 (map :count (:bins-maps hist)))]
          {:bins [{:bin-maps (:bins-maps hist)}]
           :max-count max-count
           :x-domain [(dfn/reduce-min xs-col) (dfn/reduce-max xs-col)]
           :y-domain [0 max-count]})))))

;; ## ⚙️ render-mark :bar
;;
;; Histogram bars are projected through the coord function as 4-corner polygons.
;; This means they automatically work with cartesian, flip, and polar coordinates.

(defmethod render-mark :bar [_ stat ctx]
  (let [{:keys [coord all-colors]} ctx]
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
                  (:bins stat)))))

;; ## 🧪 Stat-Driven Domains
;;
;; The y-domain comes from bin counts, not data values:

(let [v {:data iris :x :sepal-length :y :sepal-length :stat :bin}
      result (compute-stat v)]
  (kind/pprint
   {:x-domain (:x-domain result)
    :y-domain (:y-domain result)
    :max-count (:max-count result)
    :n-bins (count (:bin-maps (first (:bins result))))}))

;; ## 🧪 Histogram

(-> (views iris [[:sepal-length :sepal-length]])
    (layer (histogram))
    plot)

;; ## 🧪 Colored Histogram

(-> (views iris [[:sepal-length :sepal-length]])
    (layer (histogram {:color :species}))
    plot)

;; ## ⚙️ Flip Coordinate
;;
;; Flipping swaps the x and y axes. We extend `make-coord` and `render-grid`
;; with a `:flip` method — histograms become horizontal automatically.

(defmethod make-coord :flip [_ sx sy pw ph m]
  (fn [dx dy] [(sx dy) (sy dx)]))

(defmethod render-grid :flip [_ sx sy pw ph m]
  (render-grid :cartesian sx sy pw ph m))

(defmethod render-grid :default [_ sx sy pw ph m]
  (render-grid :cartesian sx sy pw ph m))

;; ## 🧪 Flipped Histogram
;;
;; Flip swaps the axes — bars grow horizontally instead of vertically.
;; Because bars are rendered as 4-corner polygons through the coord function,
;; this works without any special-casing:

(-> (views iris [[:sepal-length :sepal-length]])
    (layer (histogram))
    (layer {:coord :flip})
    plot)

;; ---

;; # Regression and Smooth Lines
;;
;; Lines are a second mark type: regression lines use OLS from fastmath,
;; smooth curves use loess interpolation.

;; ## ⚙️ Mark Constructors

(defn lm
  ([] {:mark :line :stat :lm})
  ([opts] (merge {:mark :line :stat :lm} opts)))

(defn loess
  ([] {:mark :line :stat :loess})
  ([opts] (merge {:mark :line :stat :loess} opts)))

;; ## ⚙️ compute-stat :lm

(defmethod compute-stat :lm [view]
  (let [{:keys [data x y color]} view
        clean (tc/drop-missing data [x y])
        n (tc/row-count clean)]
    (if (or (< n 2)
            (= (dfn/reduce-min (clean x)) (dfn/reduce-max (clean x))))
      {:lines []
       :x-domain (if (pos? n)
                   [(dfn/reduce-min (clean x)) (dfn/reduce-max (clean x))]
                   [0 1])
       :y-domain (if (pos? n)
                   [(dfn/reduce-min (clean y)) (dfn/reduce-max (clean y))]
                   [0 1])}
      (if color
        (let [grouped (-> clean (tc/group-by [color]) tc/groups->map)]
          {:lines (for [[gk gds] grouped
                        :let [gxs (double-array (gds x))
                              gys (double-array (gds y))
                              gx-min (dfn/reduce-min (gds x))
                              gx-max (dfn/reduce-max (gds x))]
                        :when (and (>= (count gxs) 2) (not= gx-min gx-max))
                        :let [model (regr/lm gys gxs)]]
                    {:color (gk color)
                     :x1 gx-min :y1 (regr/predict model [gx-min])
                     :x2 gx-max :y2 (regr/predict model [gx-max])})
           :x-domain [(dfn/reduce-min (clean x)) (dfn/reduce-max (clean x))]
           :y-domain [(dfn/reduce-min (clean y)) (dfn/reduce-max (clean y))]})
        (let [model (regr/lm (double-array (clean y)) (double-array (clean x)))
              x-min (dfn/reduce-min (clean x))
              x-max (dfn/reduce-max (clean x))]
          {:lines [{:x1 x-min :y1 (regr/predict model [x-min])
                    :x2 x-max :y2 (regr/predict model [x-max])}]
           :x-domain [x-min x-max]
           :y-domain [(dfn/reduce-min (clean y)) (dfn/reduce-max (clean y))]})))))

;; ## ⚙️ compute-stat :loess
;;
;; Loess curve via fastmath.interpolation, sampling 80 points.
;; Deduplicates x values via `tc/group-by` + `tc/aggregate`.

(defmethod compute-stat :loess [view]
  (let [{:keys [data x y color]} view
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
                          {:xs sample-xs :ys sample-ys}))]
        (if color
          (let [grouped (-> clean (tc/group-by [color]) tc/groups->map)
                results (for [[gk gds] grouped
                              :let [{:keys [xs ys]} (fit-loess gds)]]
                          {:color (gk color) :xs xs :ys ys})]
            {:points results
             :x-domain [(dfn/reduce-min (clean x)) (dfn/reduce-max (clean x))]
             :y-domain [(dfn/reduce-min (clean y)) (dfn/reduce-max (clean y))]})
          (let [{:keys [xs ys]} (fit-loess clean)]
            {:points [{:xs xs :ys ys}]
             :x-domain [(dfn/reduce-min (clean x)) (dfn/reduce-max (clean x))]
             :y-domain [(dfn/reduce-min (clean y)) (dfn/reduce-max (clean y))]}))))))

;; ## ⚙️ render-mark :line

(defmethod render-mark :line [_ stat ctx]
  (let [{:keys [coord all-colors]} ctx]
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
                           :stroke c :stroke-width 1.5 :fill "none"}]))))))

;; ## ⚙️ Layers (Multi-Layer Composition)
;;
;; `layers` stacks multiple marks on the same views. Annotation specs
;; (hline, vline, hband) are stacked as separate views rather than merged
;; into data views.

(defn layers
  "Stack multiple layers from the same base views."
  [base-views & layer-specs]
  (let [ann-specs (filter #(and (map? %) (annotation-marks (:mark %))) layer-specs)
        data-specs (remove #(and (map? %) (annotation-marks (:mark %))) layer-specs)]
    (concat (apply stack (map #(layer base-views %) data-specs))
            ann-specs)))

;; ## 🧪 Scatter + Regression

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point {:color :species})
            (lm {:color :species}))
    plot)

;; ## 🧪 Smooth Curve (Loess)

(-> (views iris [[:sepal-length :petal-length]])
    (layers (point {:color :species})
            (loess {:color :species}))
    plot)

;; ---

;; # Categorical Charts
;;
;; Categorical marks use wadogo's band scale for bar positioning.
;; `compute-stat :count` tallies categories, and `render-mark :rect`
;; renders bars with proper dodge/stack positioning.

;; ## ⚙️ Mark Constructors

(defn bar
  ([] {:mark :rect :stat :count})
  ([opts] (merge {:mark :rect :stat :count} opts)))

(defn stacked-bar
  "Stacked bar chart: bars stacked by color group."
  ([] {:mark :rect :stat :count :position :stack})
  ([opts] (merge {:mark :rect :stat :count :position :stack} opts)))

(defn value-bar
  "Pre-aggregated bars: categorical x, continuous y, no counting."
  ([] {:mark :rect :stat :identity})
  ([opts] (merge {:mark :rect :stat :identity} opts)))

(defn line-mark
  ([] {:mark :line :stat :identity})
  ([opts] (merge {:mark :line :stat :identity} opts)))

;; ## ⚙️ compute-stat :count

(defmethod compute-stat :count [view]
  (let [{:keys [data x color x-type]} view
        clean (cond-> (tc/drop-missing data [x])
                (= x-type :categorical) (tc/map-columns x [x] str))
        categories (distinct (clean x))]
    (if (empty? categories)
      {:categories [] :bars [] :max-count 0 :x-domain ["?"] :y-domain [0 1]}
      (if color
        (let [clean-c (tc/drop-missing clean [color])
              color-cats (sort (distinct (clean-c color)))
              group-cols (distinct [x color])
              grouped (-> clean-c (tc/group-by group-cols) tc/groups->map)
              count-fn (fn [cat cc]
                         (let [key (if (= x color) {x cat} {x cat color cc})]
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

;; ## ⚙️ Categorical Bar Helpers

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

(defn- render-categorical-bars
  [stat ctx]
  (let [{:keys [all-colors sx sy coord-px position]} ctx
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
                    (let [c (if color (color-for all-colors color) "#333")]
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
                            (if coord-px
                              [:polygon {:points (munch-arc coord-px x-lo x-hi py-lo py-hi 20)
                                         :fill c :opacity 0.7}]
                              [:rect {:x x-lo :y (clojure.core/min py-lo py-hi)
                                      :width (- x-hi x-lo)
                                      :height (Math/abs (- py-lo py-hi))
                                      :fill c :opacity 0.7}]))
                          (let [active (get active-map category)
                                n-active (clojure.core/count active)
                                active-idx (.indexOf ^java.util.List active bi)
                                sub-bw (/ (* bw 0.8) (clojure.core/max 1 n-active))
                                x-lo (+ (- band-mid (/ (* n-active sub-bw) 2.0)) (* active-idx sub-bw))
                                x-hi (+ x-lo sub-bw)
                                py-lo (sy 0)
                                py-hi (sy count)]
                            (if coord-px
                              [:polygon {:points (munch-arc coord-px x-lo x-hi py-lo py-hi 20)
                                         :fill c :opacity 0.7}]
                              [:rect {:x x-lo :y (clojure.core/min py-lo py-hi)
                                      :width (- x-hi x-lo)
                                      :height (Math/abs (- py-lo py-hi))
                                      :fill c :opacity 0.7}]))))))
                  (range) (:bars stat)))))

(defn- render-value-bars
  [stat ctx]
  (let [{:keys [all-colors sx sy coord-px position]} ctx
        bw (ws/data sx :bandwidth)
        groups (:points stat)
        n-groups (clojure.core/count groups)
        sub-bw (/ (* bw 0.8) (clojure.core/max 1 n-groups))
        cum-y (atom {})]
    (into [:g]
          (mapcat (fn [gi {:keys [color xs ys]}]
                    (let [c (if color (color-for all-colors color) "#333")]
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
                            (if coord-px
                              [:polygon {:points (munch-arc coord-px x-lo x-hi py-lo py-hi 20)
                                         :fill c :opacity 0.7}]
                              [:rect {:x x-lo :y (clojure.core/min py-lo py-hi)
                                      :width (- x-hi x-lo)
                                      :height (Math/abs (- py-lo py-hi))
                                      :fill c :opacity 0.7}]))
                          (let [x-lo (+ (- band-mid (/ (* n-groups sub-bw) 2.0)) (* gi sub-bw))
                                x-hi (+ x-lo sub-bw)
                                py-lo (sy 0)
                                py-hi (sy val)]
                            (if coord-px
                              [:polygon {:points (munch-arc coord-px x-lo x-hi py-lo py-hi 20)
                                         :fill c :opacity 0.7}]
                              [:rect {:x x-lo :y (clojure.core/min py-lo py-hi)
                                      :width (- x-hi x-lo)
                                      :height (Math/abs (- py-lo py-hi))
                                      :fill c :opacity 0.7}]))))))
                  (range) groups))))

;; ## ⚙️ render-mark :rect

(defmethod render-mark :rect [_ stat ctx]
  (if (:bars stat)
    (render-categorical-bars stat ctx)
    (render-value-bars stat ctx)))

;; ## ⚙️ render-x-ticks :categorical

(defmethod render-x-ticks :categorical [_ sx pw ph m]
  (let [ticks (ws/ticks sx)
        labels (map str ticks)]
    (into [:g {:font-size (:font-size theme) :fill "#666"}]
          (map (fn [t label]
                 [:text {:x (sx t) :y (- ph 2) :text-anchor "middle"} label])
               ticks labels))))

;; ## 🧪 Bar Chart

(-> (views iris [[:species :species]])
    (layer (bar))
    plot)

;; ## 🧪 Colored Bar Chart

(-> (views iris [[:species :species]])
    (layer (bar {:color :species}))
    plot)

;; ## 🧪 Stacked Bar Chart

(-> (views mpg [[:class :class]])
    (layer (stacked-bar {:color :drv}))
    plot)

;; ## 🧪 Strip Plot (Categorical x, Continuous y)

(-> (views iris [[:species :sepal-length]])
    (layer (point {:color :species}))
    plot)

;; ## 🧪 Numeric-as-Categorical

(-> (views mpg [[:cyl :cyl]])
    (layer (bar {:x-type :categorical}))
    plot)

;; ---

;; # Multi-Panel Layouts
;;
;; Views can span multiple variables or be split by facets. Helper functions
;; detect these patterns and the `arrange-panels` multimethod handles layout.

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

;; ## 🧪 Filtering Views

(let [vs (-> iris
             (views (cross [:sepal-length :sepal-width] [:sepal-length :sepal-width]))
             auto
             (when-off-diagonal {:color :species}))]
  (kind/pprint
   (mapv #(select-keys % [:x :y :mark :color]) vs)))

;; ## ⚙️ Column-Pair Helpers

(defn distribution
  "Create diagonal views (x=y) for each column — used for histograms."
  [data & cols]
  (views data (mapv (fn [c] [c c]) cols)))

(defn pairs
  "Upper-triangle pairs of columns — used for pairwise scatters."
  [cols]
  (for [i (range (count cols))
        j (range (inc i) (count cols))]
    [(nth cols i) (nth cols j)]))

;; ## 🧪 Column Pairs

(mapv #(select-keys % [:x :y]) (distribution iris :sepal-length :sepal-width))

(pairs [:a :b :c])

;; ## ⚙️ Faceting

(defn facet
  "Split each view by a categorical column."
  [views col]
  (mapcat
   (fn [v]
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
     (let [groups (-> (:data v) (tc/group-by [row-col col-col]) tc/groups->map)]
       (map (fn [[gk gds]]
              (assoc v :data gds
                     :facet-row (get gk row-col)
                     :facet-col (get gk col-col)))
            groups)))
   views))

;; ## 🧪 Faceting in Action

(-> iris
    (views [[:sepal-length :sepal-width]])
    (facet :species)
    count)

;; ## ⚙️ arrange-panels :multi-variable

(defmethod arrange-panels :multi-variable [_ ctx]
  (let [{:keys [non-ann-views ann-views pw ph m all-colors tooltip-fn shape-categories
                global-x-doms global-y-doms x-vars y-vars rows cols polar?]} ctx]
    (for [[ri yv] (map-indexed vector y-vars)
          [ci xv] (map-indexed vector x-vars)
          :let [panel-views (concat (filter #(and (= xv (:x %)) (= yv (:y %))) non-ann-views)
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
            (fmt-name yv)])]))))

;; ## ⚙️ arrange-panels :facet

(defmethod arrange-panels :facet [_ ctx]
  (let [{:keys [non-ann-views ann-views pw ph m all-colors tooltip-fn shape-categories
                global-x-doms global-y-doms facet-vals cols]} ctx]
    (for [[ci fv] (map-indexed vector facet-vals)
          :let [fviews (concat (filter #(= fv (:facet-val %)) non-ann-views)
                               ann-views)]]
      [:g {:transform (str "translate(" (* ci pw) ",0)")}
       (render-panel fviews pw ph m
                     :show-x? true :show-y? (zero? ci)
                     :all-colors all-colors
                     :x-domain global-x-doms :y-domain global-y-doms
                     :tooltip-fn tooltip-fn
                     :shape-categories shape-categories)
       [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
               :font-size 10 :fill "#333"} (str fv)]])))

;; ## ⚙️ arrange-panels :facet-grid

(defmethod arrange-panels :facet-grid [_ ctx]
  (let [{:keys [non-ann-views ann-views pw ph m all-colors tooltip-fn shape-categories
                global-x-doms global-y-doms facet-row-vals facet-col-vals rows cols]} ctx]
    (for [[ri rv] (map-indexed vector facet-row-vals)
          [ci cv] (map-indexed vector facet-col-vals)
          :let [panel-views (concat (filter #(and (= rv (:facet-row %))
                                                  (= cv (:facet-col %))) non-ann-views)
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
            (str rv)])]))))

;; ## 🧪 SPLOM (Scatterplot Matrix)
;;
;; Cross all columns with themselves. `auto` detects diagonal views (histograms)
;; and off-diagonal views (scatters):

(-> (views iris (cross iris-quantities iris-quantities))
    auto
    (layer {:color :species})
    plot)

;; ## 🧪 Faceted Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (facet :species)
    plot)

;; ## 🧪 Row × Column Faceting

(let [tips (tc/dataset "https://raw.githubusercontent.com/mwaskom/seaborn-data/master/tips.csv")]
  (-> (views tips [["total_bill" "tip"]])
      (layer (point {:color "day"}))
      (facet-grid "smoker" "sex")
      (plot {:width 600 :height 500})))

;; ## 🧪 Faceted Scatter with Free Y-Scale

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (facet :species)
    (plot {:scales :free-y}))

;; ---

;; # Polish
;;
;; Scale setters, polar coordinates, annotations, text labels, and interactivity.

;; ## ⚙️ Scale and Coord Setters

(defn set-scale
  "Set a scale type for :x or :y across all views."
  ([views channel type] (set-scale views channel type {}))
  ([views channel type opts]
   (let [k (case channel :x :x-scale :y :y-scale)]
     (mapv #(assoc % k (merge {:type type} opts)) views))))

;; ## ⚙️ make-scale :log

(defmethod make-scale :log [domain pixel-range _]
  (ws/scale :log {:domain domain :range pixel-range}))

(defn set-coord
  "Set coordinate system: :cartesian (default), :flip, or :polar."
  [views c]
  (mapv #(assoc % :coord c) views))

;; ## 🧪 Log Scale

(let [data (tc/dataset {:x (mapv #(Math/pow 10 %) (range 0.0 3.01 0.1))
                        :y (mapv #(+ % (* 0.5 (rand))) (range 0.0 3.01 0.1))})]
  (-> (views data [[:x :y]])
      (layer (point))
      (set-scale :x :log)
      plot))

;; ## ⚙️ render-grid :polar

;; ## ⚙️ make-coord :polar

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

(defmethod render-grid :polar [_ sx sy pw ph m]
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

;; ## 🧪 Polar Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (set-coord :polar)
    plot)

;; ## 🧪 Polar Bar Chart (Rose / Coxcomb)

(-> (views iris [[:species :species]])
    (layer (bar))
    (set-coord :polar)
    plot)

;; ## 🧪 Polar Stacked Bar Chart

(-> (views mpg [[:class :class]])
    (layer (stacked-bar {:color :drv}))
    (set-coord :polar)
    plot)

;; ## ⚙️ Annotation Constructors

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

;; ## ⚙️ render-annotation methods

(defmethod render-annotation :rule-h [ann {:keys [coord x-domain]}]
  (let [[x1 y1] (coord (first x-domain) (:value ann))
        [x2 y2] (coord (if (categorical-domain? x-domain)
                         (last x-domain)
                         (second x-domain))
                       (:value ann))]
    [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2
            :stroke "#333" :stroke-width 1 :stroke-dasharray "4,3"}]))

(defmethod render-annotation :rule-v [ann {:keys [coord y-domain]}]
  (let [[x1 y1] (coord (:value ann) (first y-domain))
        [x2 y2] (coord (:value ann)
                       (if (categorical-domain? y-domain)
                         (last y-domain)
                         (second y-domain)))]
    [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2
            :stroke "#333" :stroke-width 1 :stroke-dasharray "4,3"}]))

(defmethod render-annotation :band-h [ann {:keys [coord x-domain]}]
  (let [[x1 y1] (coord (first x-domain) (:y1 ann))
        [x2 y2] (coord (second x-domain) (:y2 ann))]
    [:rect {:x (min x1 x2) :y (min y1 y2)
            :width (Math/abs (- x2 x1)) :height (Math/abs (- y2 y1))
            :fill "#333" :opacity 0.08}]))

;; ## ⚙️ render-mark :text

(defmethod render-mark :text [_ stat ctx]
  (let [{:keys [coord all-colors]} ctx]
    (into [:g {:font-size 9 :fill "#333" :text-anchor "middle"}]
          (mapcat (fn [{:keys [color xs ys labels]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [i (range (count xs))
                            :let [[px py] (coord (nth xs i) (nth ys i))]]
                        [:text {:x px :y (- py 5) :fill c}
                         (str (nth labels i))])))
                  (:points stat)))))

;; ## 🧪 Annotations

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point {:color :species})
            (hline 3.0)
            (hband 2.5 3.5)
            (vline 6.0))
    plot)

;; ## 🧪 Text Labels at Group Means

(let [means (-> iris
                (tc/group-by :species)
                (tc/aggregate {:sepal-length #(dfn/mean (% :sepal-length))
                               :sepal-width #(dfn/mean (% :sepal-width))
                               :species #(first (% :species))}))]
  (-> (views iris [[:sepal-length :sepal-width]])
      (layers (point {:color :species}))
      (stack (-> (views means [[:sepal-length :sepal-width]])
                 (layer (text-label :species))))
      plot))

;; ## 🧪 Bubble Chart (Size Aesthetic)

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species :size :petal-length}))
    plot)

;; ## 🧪 Shape Aesthetic

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species :shape :species}))
    plot)

;; ## 🧪 Tooltips on Hover

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (plot {:tooltip true}))

;; ## ⚙️ wrap-plot :brush
;;
;; Brushable plots wrap the SVG in a div with a selection script.

(defmethod wrap-plot :brush [_ svg-content]
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
"]]))

;; ## 🧪 Brushable Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point {:color :species}))
    (plot {:brush true}))

;; ## 🧪 Missing Data Tolerance

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
;; `(ws/format ...)`. Log scales get proper tick placement, band scales
;; get proper padding, and datetime support is available.
;;
;; **Single coord function keeps renderers simple.** There's one function
;; `[x y] → [px py]` per coordinate system. All marks decompose to points
;; before calling it, so renderers never branch on coordinate type.
;; For bars, we project 4 corners into a polygon. For polar bars, we
;; munch arcs by sampling 20+ points and projecting each one.
;;
;; ## 📖 What's Still Rough
;;
;; **Flip domain swap.** We still swap x/y domains for flip at the scale
;; construction level. The coord function `(fn [dx dy] [(sx dy) (sy dx)])`
;; then swaps the arguments. This double-swap works but is subtle.
;;
;; **render-panel is long.** It does too much: compute stats, merge
;; domains, build scales, build coord, render annotations, render data, render
;; ticks. Could be split into `compute-panel-domains`, `render-panel-content`,
;; `render-panel-axes`.
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
