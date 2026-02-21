^{:kindly/hide-code true
  :clay {:title "Views: A Minimal Grammar of Graphics"
         :quarto {:type :post
                  :author [:daslu :claude]
                  :date "2026-02-19"
                  :description "What if the Grammar of Graphics were just vectors and maps?"
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :design :composition]
                  :toc true
                  :toc-depth 3
                  :draft true}}}
(ns data-visualization.aog.sketch
  "Views: A Minimal Grammar of Graphics.

  Core idea: a visualization is a vector of views.
  A view is a plain map: {:data ds :x :col-a :y :col-b :mark :point ...}

  Five functions build all visualizations:
  - cross:  cartesian product of column vectors -> grids
  - stack:  concatenation of view vectors -> overlays
  - views:  bind column pairs to a dataset -> view maps
  - layer:  inherit from base views with overrides
  - facet:  split data by a categorical column

  Everything else is just map manipulation with standard Clojure."
  (:require
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.viz.core :as viz]
   [thi.ng.geom.svg.core :as svg]
   [fastmath.ml.regression :as regr]
   [fastmath.stats :as stats]
   [clojure.string :as str]
   [scicloj.metamorph.ml.rdatasets :as rdatasets]))

^{:kindly/hide-code true
  :kindly/kind :kind/hiccup}
[:style
 ".clay-dataset { max-height:400px; overflow-y: auto; }
  .printedClojure { max-height:400px; overflow-y: auto; }"]

;; # Views: A Minimal Grammar of Graphics
;;
;; What if we took the Grammar of Graphics seriously as *algebra*?
;;
;; Wilkinson showed that a SPLOM is `(X + Y + Z + W) * (X + Y + Z + W)` ---
;; literally a cartesian product of variable sets. The diagonal cells
;; (where a variable meets itself) show distributions; the off-diagonal
;; cells show relationships. The algebra tells us the structure;
;; the structure implies the geometry.
;;
;; This notebook explores a radically simple implementation of this idea.
;; The entire API is five functions, zero special types:
;;
;; | Concept | Clojure | Result |
;; |---------|---------|--------|
;; | **Columns** | `[:a :b :c]` | A vector of column names |
;; | **Cross** | `(cross cols cols)` | All pairs --- the grid skeleton |
;; | **Views** | `(views data pairs)` | Maps binding data to columns |
;; | **Stack** | `(stack vs1 vs2)` | Overlay --- concatenate views |
;; | **Layer** | `(layer base :k v)` | Inherit + override |
;; | **Facet** | `(facet views :col)` | Split data by category |
;;
;; A *view* is just a Clojure map: `{:data ds :x :col-a :y :col-b :mark :point}`.
;; A *plot* is just a vector of views. No records, no protocols, no macros.
;; You modify views with `assoc`, filter them with `filter`, transform them
;; with `map`. The entire Clojure standard library is your plotting DSL.

;; ## The Data

(def iris (rdatasets/datasets-iris))

iris

;; ---

;; ## Part 1: The Algebra
;;
;; Three pure functions form the algebraic core.

;; ### Cross --- The Cartesian Product
;;
;; The foundation of the Grammar of Graphics is the **cross** operation.
;; It's just the cartesian product of two collections.

(defn cross
  "Cartesian product: all pairs from xs and ys."
  [xs ys]
  (vec (for [x xs, y ys] [x y])))

;; Four variables crossed with themselves produce 16 pairs:

(def iris-cols [:sepal-length :sepal-width :petal-length :petal-width])

(cross iris-cols iris-cols)

;; That's the skeleton of a 4x4 SPLOM. No special chart type ---
;; just algebra on column names.

;; ### Stack --- Overlay
;;
;; Stack concatenates collections. It overlays views on the same panels.

(defn stack
  "Concatenate view collections (overlay)."
  [& colls]
  (vec (apply concat colls)))

;; ### Views --- Binding Data to Columns
;;
;; A view is a map that binds a dataset to column roles.
;; `views` creates one view per column pair.

(defn views
  "Create view maps from a dataset and column pairs.
  Each pair [x y] becomes {:data ds :x x :y y}."
  [data pairs]
  (mapv (fn [[x y]] {:data data :x x :y y}) pairs))

;; A single scatter plot --- just one view:

(views iris [[:sepal-length :sepal-width]])

;; A full SPLOM skeleton --- 16 views:

(def splom-views
  (views iris (cross iris-cols iris-cols)))

(count splom-views)

(mapv #(select-keys % [:x :y]) splom-views)

;; ---

;; ## Part 2: Structure Implies Geometry
;;
;; Here's the deep insight: the *structure* of a view implies its geometry.
;; When x = y (diagonal), we're looking at one variable's distribution -> histogram.
;; When x != y (off-diagonal), we're comparing two variables -> scatter plot.
;; The algebra *tells us* what question we're asking.

(defn diagonal?
  "Is this a diagonal view (x = y)?"
  [view]
  (= (:x view) (:y view)))

(defn infer-defaults
  "Infer mark and stat from view structure.
  Diagonal -> histogram. Off-diagonal -> scatter. Stat implies mark."
  [view]
  (cond-> view
    ;; No mark/stat: infer from structure
    (and (not (:mark view)) (not (:stat view)))
    (merge (if (diagonal? view)
             {:mark :bar :stat :bin}
             {:mark :point}))

    ;; Stat without mark: infer mark
    (and (:stat view) (not (:mark view)) (#{:regress :smooth} (:stat view)))
    (assoc :mark :line)

    (and (:stat view) (not (:mark view)) (= :bin (:stat view)))
    (assoc :mark :bar)))

;; Apply defaults to our SPLOM:

(->> splom-views
     (mapv infer-defaults)
     (mapv #(select-keys % [:x :y :mark :stat])))

;; Diagonal panels got `:bar :bin` (histogram).
;; Off-diagonal panels got `:point` (scatter).
;; No explicit rules needed --- the structure decided.

;; ---

;; ## Part 3: Composing with Plain Clojure
;;
;; Because views are plain maps, we compose them with standard Clojure.
;; No DSL needed. `map`, `filter`, `assoc` are the entire API.

;; ### Color by species on off-diagonal panels

(->> splom-views
     (mapv infer-defaults)
     (mapv (fn [v]
             (if-not (diagonal? v)
               (assoc v :color :species)
               v)))
     (mapv #(select-keys % [:x :y :mark :color])))

;; ### Layer --- Inherit and Override
;;
;; When overlaying multiple geometries (e.g., points + regression lines),
;; we want to inherit from a base and override specific keys.
;; `layer` does this: it maps `merge` over the base views.

(defn layer
  "Create a new layer: base views merged with overrides.
  Returns a new vector of views inheriting from base."
  [base-views & {:as overrides}]
  (mapv #(merge % overrides) base-views))

;; `layers` stacks multiple layers from one base --- the ggplot2 `+` operator:

(defn layers
  "Stack multiple layers from base views.
  Each spec is a map of overrides applied to all base views."
  [base-views & layer-specs]
  (apply stack (map #(layer base-views %) layer-specs)))

;; Now scatter + regression in one expression:

(let [base (views iris [[:sepal-length :sepal-width]])]
  (->> (layers base
               {:color :species} ;; inferred -> scatter
               {:mark :line :stat :regress :color :species})
       (mapv infer-defaults)
       (mapv #(select-keys % [:x :y :mark :stat :color]))))

;; Two views: one scatter with color, one regression line with color.
;; Compare to ggplot2: `aes(x, y, color=species) + geom_point() + geom_smooth(method="lm")`

;; ### Facet --- Split Data by Category
;;
;; Faceting is the Grammar's **nest** operation:
;; partition the data by a categorical variable,
;; creating one panel per category value.
;;
;; In our algebra, faceting is just: for each category value,
;; create views with a data subset.

(defn facet
  "Split views by a categorical column.
  Creates one copy of each view per category value,
  each with its own data subset."
  [views col]
  (let [ds (:data (first views))
        vals (sort (distinct (ds col)))]
    (vec (for [v vals
               view views]
           (assoc view
                  :data (tc/select-rows ds (fn [row] (= (get row col) v)))
                  :facet-col col
                  :facet-val v)))))

;; Facet a scatter plot by species:

(let [base (views iris [[:sepal-length :sepal-width]])]
  (->> (facet (mapv #(assoc % :mark :point) base) :species)
       (mapv #(-> % (select-keys [:x :y :facet-val :mark])
                  (update :facet-val str)))))

;; Three views, one per species. Each has its own 50-row data subset.

;; ---

;; ## Part 3b: Geometry and Mapping Functions
;;
;; While views-as-maps is the foundation, **functions** add value:
;;
;; - **Discoverability**: what geometries exist? Tab-complete finds them.
;; - **Verification**: a function can validate its arguments.
;; - **Clarity**: `(linear :color :species)` documents intent better
;;   than `{:mark :line :stat :regress :color :species}`.
;;
;; Each function returns a **spec map** --- a partial view
;; that gets merged onto base views via `layer` or `layers`.
;; The data representation stays the same; the functions are
;; a structured way to construct it.

;; ### Geometry Specs

(defn point
  "Scatter plot geometry.
  Options: :color, :size, :opacity, :shape"
  [& {:keys [color size opacity shape] :as opts}]
  (merge {:mark :point} opts))

(defn linear
  "Linear regression line.
  Fits a line per color group (if :color is specified).
  Options: :color"
  [& {:keys [color] :as opts}]
  (merge {:mark :line :stat :regress} opts))

(defn smooth
  "Smoothed trend line.
  Options: :color, :bandwidth"
  [& {:keys [color bandwidth] :as opts}]
  (merge {:mark :line :stat :smooth} opts))

(defn histogram
  "Histogram (binned bar chart).
  Options: :bins (default 15), :color"
  [& {:keys [bins color] :as opts}]
  (merge {:mark :bar :stat :bin} opts))

(defn line-mark
  "Line connecting data points in order.
  Options: :color"
  [& {:keys [color] :as opts}]
  (merge {:mark :line} opts))

;; ### Usage: Functions + layers
;;
;; The geometry functions compose naturally with `layers`:

;; Compare the two styles ---
;;
;; **Raw maps** (always available, fully transparent):
;;
;;     (layers base {:mark :point :color :species}
;;                  {:mark :line :stat :regress :color :species})
;;
;; **Functions** (discoverable, self-documenting):
;;
;;     (layers base (point :color :species)
;;                  (linear :color :species))

;; Here's a scatter + regression using functions:

(let [base (views iris [[:sepal-length :sepal-width]])]
  (->> (layers base
               (point :color :species)
               (linear :color :species))
       (mapv infer-defaults)
       (mapv #(select-keys % [:x :y :mark :stat :color]))))

;; Both styles produce the same result. Use whichever is clearer.
;; In a REPL session, raw maps are fast. In library code, functions

;; ## Part 4: Rendering
;;
;; The renderer is the only complex part. But it's isolated ---
;; the algebra and composition happen in pure data space.

;; ### Theme

(def theme
  {:bg "#EBEBEB" :grid "#FFFFFF" :fg "#333333"
   :colors ["#F8766D" "#00BA38" "#619CFF" "#F564E3"
            "#00BFC4" "#FF9999" "#66CC99" "#9999FF"]
   :point-r 2.5 :line-w 1.5 :opacity 0.7 :font-size 10})

;; ### Helpers

(defn color-for
  "Get color by sorted index."
  [categories value]
  (let [idx (.indexOf (vec categories) value)]
    (nth (:colors theme) (mod (max 0 idx) (count (:colors theme))))))

(defn domain
  "Compute [min max] with padding."
  [values & {:keys [pad] :or {pad 0.05}}]
  (let [vs (filter (fn [v] (and (some? v)
                                (not (Double/isNaN v))
                                (not (Double/isInfinite v))))
                   values)]
    (when (seq vs)
      (let [lo (reduce min vs) hi (reduce max vs)
            span (- hi lo) p (* span pad)]
        [(- lo p) (+ hi p)]))))

(defn linear-scale [dom rng]
  (let [[d0 d1] dom [r0 r1] rng span (- d1 d0)]
    (if (zero? span)
      (constantly (/ (+ r0 r1) 2))
      (fn [v] (+ r0 (* (/ (- v d0) span) (- r1 r0)))))))

(defn fmt-name [k]
  (if (keyword? k) (-> k name (str/replace "-" " ")) (str k)))

;; ### Statistical Transforms

(defmulti compute-stat (fn [view] (or (:stat view) :identity)))

(defmethod compute-stat :identity [view]
  (let [{:keys [data x y color]} view
        color-col (when color (data color))]
    (if color
      (let [cats (sort (distinct color-col))]
        {:groups (into {}
                       (for [cat cats]
                         (let [mask (mapv #(= % cat) color-col)
                               xs (keep-indexed (fn [i v] (when (nth mask i) v)) (data x))
                               ys (when y (keep-indexed (fn [i v] (when (nth mask i) v)) (data y)))]
                           [cat (if y (mapv vector xs ys) (vec xs))])))
         :categories cats})
      {:points (if y (mapv vector (data x) (data y)) (vec (data x)))})))

(defmethod compute-stat :bin [{:keys [data x color bins] :or {bins 15}}]
  (let [all-vals (vec (data x))
        [lo hi] (domain all-vals :pad 0.0)
        bw (/ (- hi lo) bins)
        bin-idx (fn [v] (min (dec bins) (int (/ (- v lo) bw))))
        bin-ctr (fn [b] (+ lo (* (+ b 0.5) bw)))]
    (if color
      (let [color-col (data color) cats (sort (distinct color-col))]
        {:groups (into {}
                       (for [cat cats]
                         (let [vs (keep-indexed
                                   (fn [i v] (when (= (nth color-col i) cat) v))
                                   all-vals)
                               counts (frequencies (map bin-idx vs))]
                           [cat (mapv (fn [b] [(bin-ctr b) (get counts b 0)])
                                      (range bins))])))
         :categories cats :bin-width bw})
      (let [counts (frequencies (map bin-idx all-vals))]
        {:bars (mapv (fn [b] {:center (bin-ctr b)
                              :left (+ lo (* b bw))
                              :right (+ lo (* (inc b) bw))
                              :count (get counts b 0)})
                     (range bins))
         :bin-width bw}))))

(defmethod compute-stat :regress [{:keys [data x y color]}]
  (letfn [(fit [xs ys]
            (when (>= (count xs) 2)
              (let [model (regr/lm ys (mapv vector xs))
                    slope (first (:beta model))
                    intercept (:intercept model)
                    x-lo (reduce min xs) x-hi (reduce max xs)]
                [[x-lo (+ intercept (* slope x-lo))]
                 [x-hi (+ intercept (* slope x-hi))]])))]
    (if color
      (let [cc (data color) cats (sort (distinct cc))]
        {:groups (into {}
                       (for [cat cats]
                         (let [mask (mapv #(= % cat) cc)
                               xs (vec (keep-indexed (fn [i v] (when (nth mask i) v)) (data x)))
                               ys (vec (keep-indexed (fn [i v] (when (nth mask i) v)) (data y)))]
                           [cat (fit xs ys)])))
         :categories cats})
      {:line (fit (vec (data x)) (vec (data y)))})))

;; ### Geometry Renderers

(defn render-points [stat sx sy]
  (if (:groups stat)
    (mapcat (fn [cat]
              (let [pts (get-in stat [:groups cat])
                    c (color-for (:categories stat) cat)]
                (map (fn [[x y]]
                       [:circle {:cx (sx x) :cy (sy y) :r (:point-r theme)
                                 :fill c :stroke "#fff" :stroke-width 0.5
                                 :opacity (:opacity theme)}])
                     pts)))
            (:categories stat))
    (map (fn [[x y]]
           [:circle {:cx (sx x) :cy (sy y) :r (:point-r theme)
                     :fill (:fg theme) :stroke "#fff" :stroke-width 0.5
                     :opacity (:opacity theme)}])
         (:points stat))))

(defn render-bars [stat sx panel-h margin]
  (if (:groups stat)
    (let [cats (:categories stat)
          max-c (apply max 1 (mapcat (fn [cat] (map second (get-in stat [:groups cat]))) cats))
          sy (linear-scale [0 max-c] [(- panel-h margin) margin])]
      (mapcat (fn [cat]
                (let [bins (get-in stat [:groups cat])
                      c (color-for cats cat)]
                  (keep (fn [[center count]]
                          (when (pos? count)
                            (let [hw (/ (:bin-width stat) 2)
                                  x1 (sx (- center hw)) x2 (sx (+ center hw))
                                  y (sy count)]
                              [:rect {:x x1 :y y :width (- x2 x1) :height (max 0 (- (- panel-h margin) y))
                                      :fill c :opacity 0.5}])))
                        bins)))
              cats))
    (let [bars (:bars stat)
          max-c (apply max 1 (map :count bars))
          sy (linear-scale [0 max-c] [(- panel-h margin) margin])]
      (keep (fn [{:keys [left right count]}]
              (when (pos? count)
                (let [x1 (sx left) x2 (sx right) y (sy count)]
                  [:rect {:x x1 :y y :width (- x2 x1) :height (max 0 (- (- panel-h margin) y))
                          :fill (:fg theme) :opacity (:opacity theme)}])))
            bars))))

(defn render-lines [stat sx sy]
  (if (:groups stat)
    (keep (fn [cat]
            (when-let [pts (get-in stat [:groups cat])]
              (let [c (color-for (:categories stat) cat)]
                [:polyline {:points (str/join " " (map (fn [[x y]] (str (sx x) "," (sy y))) pts))
                            :fill "none" :stroke c :stroke-width (:line-w theme) :opacity 0.9}])))
          (:categories stat))
    (when-let [pts (or (:line stat) (:points stat))]
      [[:polyline {:points (str/join " " (map (fn [[x y]] (str (sx x) "," (sy y))) pts))
                   :fill "none" :stroke (:fg theme) :stroke-width (:line-w theme) :opacity 0.9}]])))

;; ### Legend

(defn render-legend
  "Render a color legend. Returns hiccup SVG group."
  [categories color-fn & {:keys [x y item-h font-size title]
                          :or {x 0 y 0 item-h 16 font-size 10}}]
  (into [:g {:transform (str "translate(" x "," y ")")}
         (when title
           [:text {:x 0 :y -4 :font-size font-size :font-weight "bold" :fill "#333"}
            (fmt-name title)])]
        (map-indexed
         (fn [i cat]
           [:g {:transform (str "translate(0," (* i item-h) ")")}
            [:circle {:cx 6 :cy (/ item-h 2) :r 4
                      :fill (color-fn cat) :stroke "#fff" :stroke-width 0.5}]
            [:text {:x 16 :y (+ (/ item-h 2) 3.5) :font-size font-size :fill "#333"}
             (str cat)]])
         categories)))

;; ### Panel Renderer

(defn render-panel
  "Render one panel from its views."
  [panel-views pw ph m & {:keys [x-domain y-domain show-x? show-y?]
                          :or {show-x? true show-y? true}}]
  (let [x-dom (or x-domain (domain (mapcat #((:data %) (:x %)) panel-views)))
        y-dom (or y-domain (domain (mapcat #(when (:y %) ((:data %) (:y %))) panel-views)))
        sx (linear-scale x-dom [m (- pw m)])
        sy (when y-dom (linear-scale y-dom [(- ph m) m]))
        ng 5
        gxs (for [i (range (inc ng))] (+ (first x-dom) (* i (/ (- (second x-dom) (first x-dom)) ng))))
        gys (when y-dom (for [i (range (inc ng))] (+ (first y-dom) (* i (/ (- (second y-dom) (first y-dom)) ng)))))]
    [:g
     [:rect {:x 0 :y 0 :width pw :height ph :fill (:bg theme)}]
     (into [:g]
           (concat
            (for [gx gxs] [:line {:x1 (sx gx) :y1 m :x2 (sx gx) :y2 (- ph m) :stroke (:grid theme) :stroke-width 0.5}])
            (when sy (for [gy gys] [:line {:x1 m :y1 (sy gy) :x2 (- pw m) :y2 (sy gy) :stroke (:grid theme) :stroke-width 0.5}]))))
     (into [:g]
           (mapcat (fn [view]
                     (let [stat (compute-stat view)]
                       (case (:mark view)
                         :point (render-points stat sx sy)
                         :bar (render-bars stat sx ph m)
                         :line (render-lines stat sx sy)
                         (render-points stat sx sy))))
                   panel-views))
     (when show-x?
       (into [:g] (for [gx gxs] [:text {:x (sx gx) :y (- ph 2) :text-anchor "middle" :font-size 8 :fill "#666"} (format "%.1f" (double gx))])))
     (when (and show-y? sy)
       (into [:g] (for [gy gys] [:text {:x (- m 3) :y (+ (sy gy) 3) :text-anchor "end" :font-size 8 :fill "#666"} (format "%.1f" (double gy))])))]))

;; ### The Plot Function

(defn plot
  "Render views to SVG. Auto-detects single panel vs grid vs faceted layout.
  Includes legend when color is present."
  [views & {:keys [panel-size margin gap]
            :or {panel-size 150 margin 25 gap 5}}]
  (let [;; Detect faceting
        has-facet? (some :facet-val views)
        ;; Group into panels
        panel-key (fn [v] [(:x v) (:y v) (:facet-val v)])
        panels (group-by panel-key views)
        ;; Derive ordering from views vector, not from (keys panels) which is a HashMap
        x-vars (distinct (map :x views))
        y-vars (distinct (map :y views))
        facet-vals (distinct (remove nil? (map :facet-val views)))
        ;; Grid dimensions
        nc-base (count x-vars)
        nr (count y-vars)
        nf (max 1 (count facet-vals))
        nc (if has-facet? (* nc-base nf) nc-base)
        single? (and (= 1 nc) (= 1 nr) (not has-facet?))
        pw (if single? 400 panel-size) ph (if single? 300 panel-size)
        ;; Global domains for consistent scales
        ds (:data (first views))
        x-doms (into {} (for [xv x-vars] [xv (domain (ds xv))]))
        y-doms (into {} (for [yv y-vars] [yv (domain (ds yv))]))
        ;; Legend info
        all-colors (let [color-cols (keep :color views)]
                     (when (seq color-cols)
                       (sort (distinct (ds (first color-cols))))))
        first-color-key (first (keep :color views))
        legend-w (if all-colors 100 0)
        ;; Total dimensions
        left-pad (if single? 0 15)
        top-pad (if single? 0 20)
        grid-w (if single? pw (* nc (+ pw gap)))
        grid-h (if single? ph (* nr (+ ph gap)))
        tw (+ left-pad grid-w (if single? 0 15) legend-w)
        th (+ top-pad grid-h (if (and has-facet? (not single?)) 10 0))]
    (kind/hiccup
     (svg/svg {:width tw :height th}
              ;; Column/row labels
              (when-not single?
                (into [:g]
                      (concat
                       ;; Column labels (top)
                       (if has-facet?
                         (for [[fi fv] (map-indexed vector (or (not-empty facet-vals) [nil]))
                               [ci xv] (map-indexed vector x-vars)]
                           (let [col-idx (+ (* fi nc-base) ci)]
                             [:text {:x (+ left-pad (* col-idx (+ pw gap)) (/ pw 2))
                                     :y 12 :text-anchor "middle" :font-size 10 :fill "#333"}
                              (str (fmt-name xv) " | " (fmt-name fv))]))
                         (map-indexed (fn [ci xv]
                                        [:text {:x (+ left-pad (* ci (+ pw gap)) (/ pw 2))
                                                :y 12 :text-anchor "middle" :font-size 10 :fill "#333"}
                                         (fmt-name xv)])
                                      x-vars))
                       ;; Row labels (right of grid)
                       (map-indexed (fn [ri yv]
                                      [:text {:x (+ left-pad grid-w 10)
                                              :y (+ top-pad (* ri (+ ph gap)) (/ ph 2))
                                              :text-anchor "middle" :font-size 10 :fill "#333"
                                              :transform (str "rotate(90,"
                                                              (+ left-pad grid-w 10) ","
                                                              (+ top-pad (* ri (+ ph gap)) (/ ph 2)) ")")}
                                       (fmt-name yv)])
                                    y-vars))))
              ;; Panels
              (if single?
                (render-panel (val (first panels)) pw ph margin)
                (into [:g]
                      (for [[ci xv] (map-indexed vector x-vars)
                            [ri yv] (map-indexed vector y-vars)
                            [fi fv] (map-indexed vector (or (not-empty facet-vals) [nil]))
                            :let [pvs (get panels [xv yv fv])]
                            :when pvs
                            :let [col-idx (if has-facet? (+ (* fi nc-base) ci) ci)]]
                        [:g {:transform (str "translate(" (+ left-pad (* col-idx (+ pw gap))) ","
                                             (+ top-pad (* ri (+ ph gap))) ")")}
                         (render-panel pvs pw ph margin
                                       :x-domain (x-doms xv)
                                       :y-domain (y-doms yv)
                                       :show-x? (= ri (dec nr))
                                       :show-y? (zero? col-idx))])))
              ;; Legend
              (when all-colors
                (render-legend all-colors
                               #(color-for all-colors %)
                               :x (+ left-pad grid-w (if single? 10 25))
                               :y (+ top-pad 5)
                               :title first-color-key))))))

;; ---

;; ---

;; ## Part 5: Helpers for Common Patterns
;;
;; Because views are plain maps, Clojure itself is the API.
;; But a few helpers make common patterns more concise.

;; ### Conditional Geometry: `when-diagonal` / `when-off-diagonal`

;; ### Pipeline Helpers: `auto`, `where`, `where-not`
;;
;; These make `->` threading natural. Every function takes views
;; as first argument and returns views.

(defn auto
  "Apply infer-defaults to all views. -> friendly."
  [views]
  (mapv infer-defaults views))

(defn where
  "Keep only views matching predicate. -> friendly."
  [views pred]
  (vec (filter pred views)))

(defn where-not
  "Remove views matching predicate. -> friendly."
  [views pred]
  (vec (remove pred views)))

;; ### Conditional Geometry: `when-diagonal` / `when-off-diagonal`

(defn when-diagonal
  "Apply spec only to diagonal views (x = y). Pass others through."
  [views spec]
  (mapv (fn [v] (if (diagonal? v) (merge v spec) v)) views))

(defn when-off-diagonal
  "Apply spec only to off-diagonal views (x != y). Pass others through."
  [views spec]
  (mapv (fn [v] (if-not (diagonal? v) (merge v spec) v)) views))
;; Now a colored SPLOM is a clean pipeline:

(-> (views iris (cross iris-cols iris-cols))
    auto
    (when-off-diagonal {:color :species})
    (->> (mapv #(select-keys % [:x :y :mark :color]))
         (take 5)))

;; ### Distribution: 1-Variable Shorthand

(defn distribution
  "Create views for variable distributions (histograms/density).
  Takes a dataset and one or more column names.
  Produces diagonal views (x = y) that infer-defaults treats as histograms."
  [data & cols]
  (views data (mapv (fn [c] [c c]) cols)))

;; Compare:
;;   (views iris [[:sepal-length :sepal-length]])     ;; verbose
;;   (distribution iris :sepal-length)                 ;; clean

;; Multiple distributions side by side:

(-> (distribution iris :sepal-length :petal-length)
    auto
    (->> (mapv #(select-keys % [:x :mark :stat]))))

;; ### Unique Pairs (Upper Triangle)

(defn pairs
  "All unique off-diagonal pairs from columns (no duplicates, no diagonal).
  Useful for compact correlation views."
  [cols]
  (vec (for [i (range (count cols))
             j (range (inc i) (count cols))]
         [(nth cols i) (nth cols j)])))

(pairs iris-cols)

;; ---

;; ## Part 6: Examples
;;
;; Every example is a `->` pipeline.
;; No `let` bindings needed for common cases.

;; ### A Single Scatter Plot

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point))
    plot)

;; ### Colored Scatter with Legend

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    plot)

;; ### Scatter + Regression

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point :color :species)
            (linear :color :species))
    plot)

;; ### Histogram

(-> (distribution iris :sepal-length)
    auto
    plot)

;; ### Colored Histogram

(-> (distribution iris :sepal-length)
    (layer (histogram :color :species))
    plot)

;; ### Two Distributions Side by Side

(-> (distribution iris :sepal-length :petal-length)
    auto
    plot)

;; ### Faceted Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point))
    (facet :species)
    (plot :panel-size 200))

;; ### Multi-Dataset Overlay
;;
;; Multiple datasets share a panel via `stack`.
;; Each branch is its own `->` pipeline.

(let [ref-line (tc/dataset {:sepal-length [4.5 8.0]
                            :sepal-width [2.0 4.5]})]
  (plot (stack (-> (views iris [[:sepal-length :sepal-width]])
                   (layer (point :color :species)))
               (-> (views ref-line [[:sepal-length :sepal-width]])
                   (layer (line-mark))))))

;; ### Actual vs Predicted with Reference Line

(let [n 50
      actual (vec (repeatedly n #(+ 3.0 (* 4.0 (rand)))))
      predicted (mapv #(+ % (* 0.3 (- (rand) 0.5))) actual)
      data (tc/dataset {:actual actual :predicted predicted})
      ref (tc/dataset {:actual [3.0 7.0] :predicted [3.0 7.0]})]
  (plot (stack (-> (views data [[:actual :predicted]]) (layer (point)))
               (-> (views ref [[:actual :predicted]]) (layer (line-mark))))))

;; ### Asymmetric Grid

(-> (views iris (cross [:petal-length :petal-width]
                       [:sepal-length :sepal-width]))
    (layer (point :color :species))
    plot)

;; ### Compact Pairs (Upper Triangle Only)

(-> (views iris (pairs iris-cols))
    (layer (point :color :species))
    plot)

;; ### 4x4 SPLOM with Defaults

(-> (views iris (cross iris-cols iris-cols))
    auto
    plot)

;; ### SPLOM with Color and Regression

(plot (stack (-> (views iris (cross iris-cols iris-cols))
                 auto
                 (when-off-diagonal {:color :species}))
             (-> (views iris (cross iris-cols iris-cols))
                 (where-not diagonal?)
                 (layer (linear :color :species)))))

;; ### Heterogeneous SPLOM
;;
;; Different geometry per region: scatter (upper), histogram (diagonal),
;; regression lines (lower).

(let [idx (fn [c] (.indexOf iris-cols c))
      upper? (fn [v] (> (idx (:y v)) (idx (:x v))))
      lower? (fn [v] (< (idx (:y v)) (idx (:x v))))]
  (plot (stack (-> (views iris (cross iris-cols iris-cols))
                   (where upper?)
                   (layer (point :color :species)))
               (-> (views iris (cross iris-cols iris-cols))
                   (where diagonal?)
                   (layer (histogram :color :species)))
               (-> (views iris (cross iris-cols iris-cols))
                   (where lower?)
                   (layer (linear :color :species))))))

;; ### Selective Regression
;;
;; Regression lines only on sepal-vs-petal panels (not sepal-vs-sepal):

(let [sepal? #{:sepal-length :sepal-width}
      mixed? (fn [v] (not= (boolean (sepal? (:x v)))
                           (boolean (sepal? (:y v)))))]
  (plot (stack (-> (views iris (cross iris-cols iris-cols))
                   auto
                   (when-off-diagonal {:color :species}))
               (-> (views iris (cross iris-cols iris-cols))
                   (where-not diagonal?)
                   (where mixed?)
                   (layer (linear :color :species))))))

;; ### Faceted SPLOM

(-> (views iris (cross [:sepal-length :sepal-width]
                       [:sepal-length :sepal-width]))
    auto
    (facet :species)
    (plot :panel-size 120))

;; ---

;; ## Part 7: Overcoming Limitations
;;
;; ### The Panel Identity Question
;;
;; The most fundamental design choice in this sketch:
;; **panel identity = [x y] column names.**
;; This means views with the same x and y columns share a panel,
;; and views with different column names go to different panels.
;;
;; **When this is perfect:**
;; SPLOM, faceting, any grid where each cell has a clear column pair.
;; The algebra cross(cols, cols) naturally produces [x y] pairs,
;; and the renderer naturally groups by them.
;;
;; **When this constrains:**
;; Overlaying data from different sources with different column names.
;; e.g., scatter of :measured-x vs :measured-y overlaid with
;; a model prediction from :predicted-x vs :predicted-y.
;;
;; **Three escape routes:**
;;
;; **(1) Rename columns to match** (recommended for most cases):
;;
;;     (let [model (tc/rename-columns model {:predicted-x :x :predicted-y :y})]
;;       (stack actual-views model-views))
;;
;; This is what ggplot2 users do. The column names define the
;; semantic space. If two datasets measure the same thing,
;; they should use the same names. Renaming makes that explicit.
;;
;; **(2) Separate data access from panel identity:**
;; Add a `:read-x` / `:read-y` key that tells the renderer
;; which column to read, while `:x` / `:y` control panel placement.
;;
;;     {:data model :x :actual :y :predicted
;;      :read-x :model-x :read-y :model-y}
;;
;; The renderer would use `:read-x` (falling back to `:x`) for data access.
;; This adds complexity but preserves the algebra.
;;
;; **(3) Explicit `:panel` key:**
;; Override the [x y] grouping entirely.
;;
;;     {:data model :x :pred-x :y :pred-y :panel :comparison}
;;
;; Views with the same `:panel` share a panel regardless of x/y.
;; Problem: domain computation needs to merge across different columns.
;;
;; **Our recommendation:** Start with (1). It works for the vast majority
;; of cases and requires zero framework changes. If (2) or (3) prove
;; necessary, they can be added without changing the algebra.
;; The algebra operates on column names; only the renderer needs to change.
;;
;; ### The 1-Variable Question
;;
;; Histograms require `[:col :col]` (the diagonal convention).
;; This is algebraically meaningful (x crossed with itself) but
;; ergonomically awkward for standalone histograms.
;;
;; Solution: the `distribution` helper function.
;; It creates diagonal views from column names:
;;
;;     (distribution iris :sepal-length)
;;     ;; => [{:data iris :x :sepal-length :y :sepal-length}]
;;
;; The algebra stays clean; the ergonomics improve.
;; This pattern generalizes: **keep the algebra pure,
;; add helpers for common patterns.**
;;
;; ### The Missing Geometries Question
;;
;; Boxplots, violin plots, categorical bars, heatmaps ---
;; these are all missing from the renderer, not the algebra.
;;
;; Each new geometry follows the same pattern:
;;
;; 1. Add a `compute-stat` method (e.g., `:boxplot` → quartiles)
;; 2. Add a render function (e.g., `render-boxplot`)
;; 3. Add a spec function (e.g., `(boxplot :color :species)`)
;; 4. Extend `render-panel` to dispatch on the new `:mark`
;;
;; The algebra doesn't change. Views remain maps.
;; `cross` and `stack` remain pure functions.
;; **New geometries are purely additive.**
;;
;; This is the payoff of separating algebra from rendering:
;; the rendering layer can grow indefinitely without
;; complicating the composition layer.

;; ---

;; ## Part 8: Reflection
;;
;; ### The Design in One Table
;;
;; | Wilkinson | Clojure | This Sketch | Visualization |
;; |-----------|---------|-------------|---------------|
;; | Variable | keyword | `:sepal-length` | Column name |
;; | Blend (+) | vector | `[:a :b :c]` | Multiple variables |
;; | Cross (x) | `cross` | `(cross cols cols)` | Grid of panels |
;; | Frame | map | `{:data ds :x :a :y :b}` | Single view |
;; | Stack | `stack` | `(stack vs1 vs2)` | Overlay |
;; | Nest | `facet` | `(facet views :species)` | Data partitioning |
;; | Geometry | function | `(point)`, `(linear)` | Spec maps |
;;
;; ### Two Layers, Both Necessary
;;
;; **Data layer** (the algebra): views as maps, cross, stack, layer, facet.
;; Transparent, inspectable, composable with standard Clojure.
;;
;; **Function layer** (the vocabulary): point, linear, histogram, smooth, ...
;; Discoverable, documented, validated. Returns the same maps.
;;
;; Neither layer alone is sufficient. Raw maps lack discoverability.
;; Functions alone lack transparency. Together they give you both:
;; you can write `(linear :color :species)` for clarity,
;; or `{:mark :line :stat :regress :color :species}` for power,
;; and they produce exactly the same thing.
;;
;; ### What Clojure Gets Right
;;
;; This design works because of Clojure's specific strengths:
;;
;; - **Keywords as column names**: zero boilerplate, instant access
;; - **Maps as views**: transparent, mergeable, inspectable
;; - **Vectors as collections**: natural for cross, stack, filter
;; - **Keyword arguments**: `(point :color :species)` reads like English
;; - **Destructuring**: easy to pattern-match on view structure
;; - **No type system overhead**: a view is a map, period
;;
;; The grammar *is* the language. No translation layer needed.
