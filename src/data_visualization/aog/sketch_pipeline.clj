^{:kindly/hide-code true
  :clay {:title "Variation C: Plot as Pipeline"
         :quarto {:type :post
                  :author [:daslu :claude]
                  :date "2026-02-19"
                  :description "What if a plot were a context map flowing through ->? Scales, coordinates, layers, and multiple datasets — all as pipeline steps."
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :pipeline :threading :design]
                  :toc true
                  :toc-depth 3
                  :draft true}}}
(ns data-visualization.aog.sketch-pipeline
  "Variation C: Plot as Pipeline.

  Core idea: a plot is a context map that flows through ->.
  Every function takes context as first argument, returns context.
  The terminal `render` step produces SVG.

    (-> (base iris)
        (xy :sepal-length :sepal-width)
        (point :color :species)
        (regression :color :species)
        (scale :x :log)
        (coord :flip)
        render)

  This approach unifies:
  - Threading: -> just works
  - Layers: each geometry fn appends a layer
  - Scales: explicit, stored in context
  - Coordinates: a context key
  - Multiple datasets: layers can carry different data
  - Faceting: a context key
  - Grids: built from context manipulation

  The IR is the context map itself."
  (:require
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   [tech.v3.datatype.functional :as dfn]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.svg.core :as svg]
   [fastmath.ml.regression :as regr]
   [fastmath.stats :as stats]
   [clojure.string :as str]
   [scicloj.metamorph.ml.rdatasets :as rdatasets]))

;; # Variation C: Plot as Pipeline
;;
;; Variation A compiles to geom.viz. Variation B puts specs in dataset metadata.
;; Both have limitations: A loses semantic info, B can't handle multi-dataset.
;;
;; Variation C introduces a **plot context**: a plain map that accumulates
;; bindings, layers, scales, and coordinates as it flows through `->`.
;;
;; The context IS the intermediate representation.

;; ---

;; ## The Data

(def iris (rdatasets/datasets-iris))

;; ## Part 1: The Context
;;
;; A plot context is a map:
;;
;; ```
;; {:data    <dataset>            ;; the current default dataset
;;  :x       <column-name>        ;; current x binding
;;  :y       <column-name>        ;; current y binding
;;  :layers  [{:data ds :x :col :y :col :mark :point :color :species} ...]
;;  :scales  {:x {:type :linear}  ;; or :log, :sqrt, :categorical
;;            :y {:type :linear}
;;            :color {:type :categorical :palette [...]}}
;;  :coord   :cartesian           ;; or :polar, :flip
;;  :facet   nil}                 ;; or :species
;; ```

(def ggplot-palette
  ["#F8766D" "#00BA38" "#619CFF" "#A855F7" "#F97316" "#14B8A6" "#EF4444" "#6B7280"])

;; ### Constructor

(defn base
  "Create a plot context from a dataset."
  [ds]
  {:data ds
   :layers []
   :scales {:color {:type :categorical :palette ggplot-palette}}
   :coord :cartesian})

;; ### Binding

(defn xy
  "Bind x and y columns."
  [ctx x-col y-col]
  (assoc ctx :x x-col :y y-col))

;; ### Geometry: each fn appends a layer

(defn- add-layer [ctx layer]
  (update ctx :layers conj
          (merge {:data (:data ctx) :x (:x ctx) :y (:y ctx)}
                 layer)))

(defn point
  "Add a scatter layer."
  [ctx & {:as opts}]
  (add-layer ctx (merge {:mark :point} opts)))

(defn regression
  "Add a linear regression layer."
  [ctx & {:as opts}]
  (add-layer ctx (merge {:mark :line :stat :regress} opts)))

(defn line
  "Add a line (connect points) layer."
  [ctx & {:as opts}]
  (add-layer ctx (merge {:mark :line :stat :identity} opts)))

(defn histogram
  "Add a histogram layer. On diagonal panels, x=y, bins the x column."
  [ctx & {:as opts}]
  (add-layer ctx (merge {:mark :bar :stat :bin} opts)))

;; ### With a different dataset
;;
;; This is the key to multi-dataset:
;; temporarily switch `:data` while adding layers.

(defn with-data
  "Add layers using a different dataset.
  The fn `f` receives the context with the new dataset
  and should add layers. The original dataset is restored after.

    (-> (base iris)
        (xy :x-col :y-col)
        (point :color :species)
        (with-data ref-line line)
        render)"
  [ctx new-data f & args]
  (let [original-data (:data ctx)
        ctx-with-new (assoc ctx :data new-data)
        ctx-after (apply f ctx-with-new args)]
    (assoc ctx-after :data original-data)))

;; ### Scales

(defn scale
  "Set a scale type for a channel.

    (scale ctx :x :log)
    (scale ctx :x :log :domain [1 100])
    (scale ctx :color :categorical :palette [\"red\" \"blue\"])"
  [ctx channel type & {:as opts}]
  (assoc-in ctx [:scales channel] (merge {:type type} opts)))

;; ### Coordinates

(defn coord
  "Set the coordinate system: :cartesian, :flip, or :polar."
  [ctx c]
  (assoc ctx :coord c))

;; ### Faceting

(defn facet
  "Facet by a categorical column."
  [ctx col]
  (assoc ctx :facet col))

;; ### Grid (for SPLOM-like layouts)

(defn grid
  "Set up a grid of x×y column combinations.
  Stores :grid in context; render handles the layout.

    (grid ctx [:sepal-length :sepal-width]
              [:petal-length :petal-width])"
  [ctx x-cols y-cols]
  (assoc ctx :grid {:x-cols (vec x-cols) :y-cols (vec y-cols)}))

(defn splom
  "Set up a SPLOM (symmetric grid of all column pairs)."
  [ctx cols]
  (grid ctx cols cols))

;; ---

;; ## Part 2: The IR in Plain Sight
;;
;; Before rendering, let's see what the context looks like.
;; This is the IR — inspectable at any pipeline stage.

(-> (base iris)
    (xy :sepal-length :sepal-width)
    (point :color :species)
    (regression :color :species)
    (scale :x :log)
    (dissoc :data))

;; The context is a plain map. You can print it, test it, serialize it.
;; Every pipeline step is a pure function ctx → ctx.

;; ---

;; ## Part 3: Rendering

(def theme {:bg "#EBEBEB" :grid "#FFFFFF" :font "sans-serif" :font-size 10})

(defn- domain-of [datasets col]
  (let [all-vals (mapcat #(vec (% col)) datasets)
        lo (reduce min all-vals) hi (reduce max all-vals)
        pad (* 0.05 (- hi lo))]
    [(- lo pad) (+ hi pad)]))

(defn- apply-scale-transform [values scale-spec]
  (case (:type scale-spec :linear)
    :log (mapv #(Math/log %) values)
    :sqrt (mapv #(Math/sqrt %) values)
    values))

(defn- linear-scale-fn [domain range]
  (let [[d0 d1] domain [r0 r1] range
        sd (- d1 d0) sr (- r1 r0)]
    (fn [v] (+ r0 (* sr (/ (- v d0) sd))))))

(defn- nice-ticks [lo hi n]
  (let [span (- hi lo)
        raw-step (/ span n)
        mag (Math/pow 10 (Math/floor (Math/log10 raw-step)))
        step (->> [1 2 5 10] (map #(* mag %)) (filter #(>= % raw-step)) first)
        start (* (Math/ceil (/ lo step)) step)]
    (vec (take-while #(<= % hi) (iterate #(+ % step) start)))))

(defn- tick-label [scale-type v]
  (case scale-type
    :log (format "%.1f" (Math/exp v))
    :sqrt (format "%.1f" (* v v))
    (format "%.1f" (double v))))

(defn- render-grid-and-axes [sx sy dx dy w h margin x-label y-label x-scale-type y-scale-type]
  (let [xt (nice-ticks (first dx) (second dx) 5)
        yt (nice-ticks (first dy) (second dy) 5)]
    [:g
     ;; Grid
     (for [t xt] [:line {:x1 (sx t) :y1 margin :x2 (sx t) :y2 (- h margin)
                         :stroke (:grid theme) :stroke-width 1}])
     (for [t yt] [:line {:x1 margin :y1 (sy t) :x2 (- w margin) :y2 (sy t)
                         :stroke (:grid theme) :stroke-width 1}])
     ;; Labels
     [:g {:font-family (:font theme) :font-size (:font-size theme) :fill "#666"}
      (for [t xt]
        [:text {:x (sx t) :y (+ (- h margin) 14) :text-anchor "middle"}
         (tick-label x-scale-type t)])
      (for [t yt]
        [:text {:x (- margin 4) :y (+ (sy t) 3) :text-anchor "end"}
         (tick-label y-scale-type t)])
      [:text {:x (/ w 2) :y (- h 2) :text-anchor "middle" :font-size 11 :fill "#333"}
       (name x-label)]
      [:text {:x 11 :y (/ h 2) :text-anchor "middle" :font-size 11 :fill "#333"
              :transform (str "rotate(-90,11," (/ h 2) ")")}
       (name y-label)]]]))

(defn- color-groups [ds col palette]
  (let [groups (-> ds (tc/group-by [col]) tc/groups->map)]
    (map-indexed (fn [i [gk gds]]
                   {:label (get gk col) :color (nth palette (mod i (count palette))) :data gds})
                 groups)))

(defn- render-layer-content [layer sx sy x-scale y-scale coord-type]
  (let [{:keys [data x y mark stat color]} layer
        flipped? (= coord-type :flip)
        [x-col y-col sx' sy'] (if flipped? [y x sy sx] [x y sx sy])
        palette (or (:palette layer) ggplot-palette)
        groups (if color
                 (color-groups data color palette)
                 [{:color "#333" :data data}])]
    [:g
     (for [{:keys [color data]} groups]
       (let [xs (apply-scale-transform (vec (data x-col)) (or x-scale {:type :linear}))
             ys (apply-scale-transform (vec (data y-col)) (or y-scale {:type :linear}))]
         (case (or mark :point)
           :point
           [:g (for [i (range (count xs))]
                 [:circle {:cx (sx' (nth xs i)) :cy (sy' (nth ys i))
                           :r 2.5 :fill color :stroke "#fff" :stroke-width 0.5 :opacity 0.7}])]

           :line
           (case (or stat :identity)
             :regress
             (let [model (regr/lm ys xs)
                   xmin (reduce min xs) xmax (reduce max xs)]
               [:line {:x1 (sx' xmin) :y1 (sy' (regr/predict model [xmin]))
                       :x2 (sx' xmax) :y2 (sy' (regr/predict model [xmax]))
                       :stroke color :stroke-width 1.5}])
             :identity
             (let [pts (sort-by first (map vector xs ys))]
               [:polyline {:points (str/join " " (map (fn [[xv yv]] (str (sx' xv) "," (sy' yv))) pts))
                           :stroke color :stroke-width 1.5 :fill "none"}]))

           :bar
           (let [bins (:bins-maps (stats/histogram xs :sturges))
                 max-c (reduce max 1 (map :count bins))
                 base-y (sy' (second (domain-of [data] y-col)))]
             (if (= (or stat :identity) :bin)
               ;; Histogram: bin the x values
               (let [count-domain [0 max-c]
                     count-sy (linear-scale-fn count-domain [base-y (+ 30 0)])]
                 [:g (for [{:keys [min max count]} bins
                           :let [px1 (sx' min) px2 (sx' max)
                                 py (count-sy count)]]
                       [:rect {:x px1 :y py :width (- px2 px1) :height (- base-y py)
                               :fill color :opacity 0.7}])])
               nil)))))]))

(defn- render-single-panel [layers datasets x-col y-col w h margin scales coord-type]
  (let [x-scale (get scales :x {:type :linear})
        y-scale (get scales :y {:type :linear})
        ;; Apply transforms to compute domains
        all-xs (mapcat #(apply-scale-transform (vec (% x-col)) x-scale) datasets)
        all-ys (mapcat #(apply-scale-transform (vec (% y-col)) y-scale) datasets)
        pad-x (* 0.05 (- (reduce max all-xs) (reduce min all-xs)))
        pad-y (* 0.05 (- (reduce max all-ys) (reduce min all-ys)))
        dx [(- (reduce min all-xs) pad-x) (+ (reduce max all-xs) pad-x)]
        dy [(- (reduce min all-ys) pad-y) (+ (reduce max all-ys) pad-y)]
        flipped? (= coord-type :flip)
        [dx' dy'] (if flipped? [dy dx] [dx dy])
        sx (linear-scale-fn dx' [margin (- w margin)])
        sy (linear-scale-fn dy' [(- h margin) margin])
        [x-label y-label] (if flipped? [y-col x-col] [x-col y-col])]
    [:g
     [:rect {:x 0 :y 0 :width w :height h :fill (:bg theme)}]
     (render-grid-and-axes sx sy dx' dy' w h margin x-label y-label
                           (:type x-scale :linear) (:type y-scale :linear))
     (for [layer layers]
       (render-layer-content layer sx sy x-scale y-scale coord-type))]))

(defn- render-legend [layers scales w margin]
  (let [color-layers (filter :color layers)]
    (when (seq color-layers)
      (let [layer (first color-layers)
            ds (:data layer)
            col (:color layer)
            palette (get-in scales [:color :palette] ggplot-palette)
            categories (distinct (vec (ds col)))
            color-map (zipmap categories (cycle palette))]
        [:g {:font-family (:font theme) :font-size 10}
         (for [[i cat] (map-indexed vector categories)
               :let [y (+ margin (* i 18))]]
           [:g
            [:circle {:cx (+ w 15) :cy y :r 4 :fill (color-map cat)}]
            [:text {:x (+ w 25) :y (+ y 4) :fill "#333"} (str cat)]])]))))

;; ### The render function

(defn render
  "Terminal step: render the plot context to SVG."
  [ctx & {:keys [width height panel-size margin]
          :or {width 400 height 300 panel-size 200 margin 35}}]
  (let [{:keys [layers scales coord facet data x y]} ctx
        grid-spec (:grid ctx)]
    (cond
      ;; Grid/SPLOM layout
      grid-spec
      (let [{:keys [x-cols y-cols]} grid-spec
            ncols (count x-cols) nrows (count y-cols)
            gap 5
            ps panel-size
            total-w (+ (* ncols ps) (* (dec ncols) gap))
            total-h (+ (* nrows ps) (* (dec nrows) gap))]
        ^:kind/hiccup
        [:svg {:width total-w :height total-h
               "xmlns" "http://www.w3.org/2000/svg" "version" "1.1"}
         (for [ci (range ncols) ri (range nrows)
               :let [xc (nth x-cols ci) yc (nth y-cols ri)
                     tx (* ci (+ ps gap)) ty (* ri (+ ps gap))
                     ;; Rebind layers to this cell's columns
                     cell-layers (mapv #(assoc % :x xc :y yc) layers)
                     cell-datasets (mapv :data cell-layers)]]
           [:g {:transform (str "translate(" tx "," ty ")")}
            (render-single-panel cell-layers cell-datasets xc yc ps ps margin scales coord)])])

      ;; Faceted layout
      facet
      (let [groups (-> data (tc/group-by [facet]) tc/groups->map)
            n (count groups)
            gap 10
            total-w (+ (* n panel-size) (* (dec n) gap))]
        ^:kind/hiccup
        [:svg {:width total-w :height (+ panel-size 20)
               "xmlns" "http://www.w3.org/2000/svg" "version" "1.1"}
         (map-indexed
          (fn [i [gk gds]]
            (let [tx (* i (+ panel-size gap))
                  facet-layers (mapv #(assoc % :data gds) layers)]
              [:g {:transform (str "translate(" tx ",0)")}
               (render-single-panel facet-layers [gds] x y panel-size panel-size margin scales coord)
               [:text {:x (/ panel-size 2) :y (+ panel-size 15) :text-anchor "middle"
                       :font-family (:font theme) :font-size 11 :fill "#333"}
                (str (first (vals gk)))]]))
          groups)])

      ;; Single panel
      :else
      (let [has-color? (some :color layers)
            plot-w (if has-color? (- width 80) width)
            datasets (map :data layers)]
        ^:kind/hiccup
        [:svg {:width width :height height
               "xmlns" "http://www.w3.org/2000/svg" "version" "1.1"}
         (render-single-panel layers datasets x y plot-w height margin scales coord)
         (render-legend layers scales plot-w margin)]))))

;; ---

;; ## Part 4: Examples
;;
;; Each example is a `->` pipeline. Note how layers, scales,
;; coordinates, and faceting are all pipeline steps.

;; ### Single Scatter

(-> (base iris)
    (xy :sepal-length :sepal-width)
    (point)
    render)

;; ### Colored Scatter with Legend

(-> (base iris)
    (xy :sepal-length :sepal-width)
    (point :color :species)
    render)

;; ### Scatter + Regression (two layers)

(-> (base iris)
    (xy :sepal-length :sepal-width)
    (point :color :species)
    (regression :color :species)
    render)

;; Two geometry steps, two layers. Clean.

;; ### Flipped Coordinates

(-> (base iris)
    (xy :sepal-length :sepal-width)
    (point :color :species)
    (coord :flip)
    render)

;; `coord :flip` swaps x↔y during rendering. The data and bindings stay the same.
;; This is different from Variation B's `flip` which swaps metadata.
;; Here, the *rendering* flips, not the *data*.

;; ### Log Scale

(-> (base iris)
    (xy :sepal-length :sepal-width)
    (point :color :species)
    (scale :x :log)
    render)

;; `scale :x :log` stores the transform in context.
;; During rendering, data is transformed and tick labels show original values.
;; Compare to Variation B where `scale-x :log` transforms the column in-place.

;; ### Multi-Dataset: Reference Line Overlay

(let [ref-ds (tc/dataset {:sepal-length [4.5 8.0]
                          :sepal-width [2.0 4.5]})]
  (-> (base iris)
      (xy :sepal-length :sepal-width)
      (point :color :species)
      (with-data ref-ds line)
      render))

;; `with-data` temporarily switches the dataset for one layer.
;; This is the clean multi-dataset answer that Variation B couldn't do.

;; ### Faceted

(-> (base iris)
    (xy :sepal-length :sepal-width)
    (point :color :species)
    (facet :species)
    (render :panel-size 200))

;; ### 2×2 Grid

(-> (base iris)
    (grid [:sepal-length :sepal-width]
          [:petal-length :petal-width])
    (point :color :species)
    (render :panel-size 200))

;; `grid` replaces `xy` — it sets up a matrix of x×y pairs.
;; The `point` layer is replicated across all cells.

;; ### 4×4 SPLOM

(-> (base iris)
    (splom [:sepal-length :sepal-width :petal-length :petal-width])
    (point :color :species)
    (render :panel-size 120))

;; ### SPLOM + Regression

(-> (base iris)
    (splom [:sepal-length :sepal-width :petal-length :petal-width])
    (point :color :species)
    (regression :color :species)
    (render :panel-size 120))

;; Two layers on a 4×4 grid. Both scatter and regression in every cell.

;; ---

;; ## Part 5: The IR
;;
;; Let's inspect the context at various pipeline stages.

;; After binding:
(-> (base iris) (xy :sepal-length :sepal-width)
    (dissoc :data) ;; hide dataset for readability
    )

;; After adding layers:
(-> (base iris) (xy :sepal-length :sepal-width)
    (point :color :species) (regression :color :species)
    (update :layers (fn [ls] (mapv #(dissoc % :data) ls))) ;; hide data
    (dissoc :data))

;; After setting scale and coordinate:
(-> (base iris) (xy :sepal-length :sepal-width)
    (point :color :species) (scale :x :log) (coord :flip)
    (update :layers (fn [ls] (mapv #(dissoc % :data) ls)))
    (dissoc :data))

;; The context IS the spec. There is no separate IR.
;; At every step you can print it, test it, transform it.

;; ---

;; ## Part 6: Comparison Table
;;
;; | Aspect | sketch.clj (Views) | Var A (geom.viz IR) | Var B (Dataset) | Var C (Pipeline) |
;; |--------|-------------------|---------------------|-----------------|------------------|
;; | `->` friendly | ★★★☆ | ★★☆☆ | ★★★★★ | ★★★★★ |
;; | Inspectable IR | ★★★★ | ★★★☆ | ★★★★★ | ★★★★★ |
;; | Single scatter | ★★★★ | ★★★★ | ★★★★★ | ★★★★★ |
;; | Multi-layer | ★★★★ | ★★★☆ | ★★★☆☆ | ★★★★★ |
;; | Multi-dataset | ★★★★ | ★★★☆ | ★★☆☆☆ | ★★★★★ |
;; | Faceting | ★★★★ | ★★☆☆ | ★★★★☆ | ★★★★☆ |
;; | SPLOM/grid | ★★★★★ | ★★★☆ | ★★☆☆☆ | ★★★★☆ |
;; | Scales | ★★☆☆ | ★★★☆ | ★★★★☆ | ★★★★★ |
;; | Coordinates | ★☆☆☆ | ★★☆☆ | ★★★☆☆ | ★★★★☆ |
;; | tc integration | ★★☆☆ | ★★☆☆ | ★★★★★ | ★★★☆☆ |
;; | Algebraic | ★★★★★ | ★★★★☆ | ★★☆☆☆ | ★★★☆☆ |

;; ---

;; ## Part 7: Reflection
;;
;; ### Threading: solved
;;
;; The context map flows through `->` perfectly.
;; Every step is a pure function `ctx → ctx`.
;; You can stop the pipeline at any point and inspect the context.
;;
;; ### Scales: where they belong
;;
;; Scales live in the context, not in the data.
;; This lets us:
;; - Apply transforms during rendering (tick labels show original values)
;; - Share scales across panels (grids inherit context scales)
;; - Override scales per-axis
;;
;; Compare the three approaches:
;; - **Var A (geom.viz)**: scales ARE the axes — computed during compilation
;; - **Var B (dataset)**: scales are column transforms — honest but lossy
;; - **Var C (pipeline)**: scales are rendering directives — most flexible
;;
;; ### Coordinates: clean dispatch
;;
;; `(coord :flip)` changes how layers are projected, not how data is stored.
;; Polar would work the same way — a different projection in the renderer.
;;
;; ### Multiple datasets: with-data
;;
;; `with-data` is the key insight: temporarily switch the default dataset
;; while adding layers. The original dataset is restored.
;; This preserves `->` threading while supporting multi-dataset overlay.
;;
;; ### What we trade away
;;
;; Compared to sketch.clj's algebraic approach:
;; - `cross` and `stack` become `grid` and layer-stacking — less general
;; - You can't manipulate individual views as maps
;; - The "views as data" transparency is replaced by "context as data"
;; - Heterogeneous SPLOM (different geometry per cell) needs more work
;;
;; The pipeline is more **imperative** (do this, then do that).
;; The algebraic approach is more **declarative** (here is what I want).
;; Both are valid. The question is which reads better for the task at hand.
;;
;; ### The missing piece: per-cell customization
;;
;; In sketch.clj, you can filter views and apply different specs to different cells.
;; In the pipeline, every layer applies to ALL cells in a grid.
;; To get heterogeneous behavior, we'd need something like:
;;
;; ```clojure
;; (-> (base iris)
;;     (splom cols)
;;     (when-diagonal (histogram :color :species))
;;     (when-off-diagonal (point :color :species))
;;     render)
;; ```
;;
;; This is achievable — `when-diagonal` and `when-off-diagonal` would
;; tag layers with predicates, and the renderer would filter per-cell.
;; But it adds complexity to the context model.
;;
;; ### The deeper question
;;
;; sketch.clj's algebraic approach says: "a plot IS a collection of view-maps."
;; The pipeline approach says: "a plot IS a series of steps."
;;
;; The algebra is a **noun** — you describe the thing.
;; The pipeline is a **verb** — you describe the process.
;;
;; Clojure is comfortable with both. The best API might offer both:
;; - Pipeline for simple, common plots (80% of use cases)
;; - Algebraic for complex, compositional plots (SPLOM, heterogeneous grids)
;;
;; The context map can hold both view-vectors AND pipeline state.
;; This is not a contradiction — it's the Clojure way.
