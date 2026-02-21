^{:kindly/hide-code true
  :clay {:title "A Unified Grammar: Views + Pipeline + Scales"
         :quarto {:type :post
                  :author [:daslu :claude]
                  :date "2026-02-19"
                  :description "Combining the algebraic power of view-maps with the ergonomics of -> threading, scales, and coordinates."
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :design :unified]
                  :toc true
                  :toc-depth 3
                  :draft true}}}
(ns data-visualization.aog.sketch-unified
  "A Unified Grammar of Graphics.

  Four variations taught us:
    sketch.clj    — views as maps, algebraic composition, transparent data
    Var A (geom)  — leverage existing renderers, but lossy IR
    Var B (tc)    — start from dataset, -> threading, tc ops slot in
    Var C (pipe)  — context map, scales, coordinates, multi-dataset

  The compromise: keep view-vectors as the IR, add scale/coord as map keys,
  and make every function views→views for -> threading.

  The result threads from dataset to plot:

    (-> iris
        (tc/select-rows #(> (:sepal-length %) 5))
        (views [[:sepal-length :sepal-width]])
        (layer (point :color :species))
        (set-scale :x :log)
        (set-coord :flip)
        plot)"
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

;; # A Unified Grammar
;;
;; We explored four approaches. Here's what each taught us:
;;
;; | Lesson | Source |
;; |--------|--------|
;; | Views as plain maps: transparent, composable | sketch.clj |
;; | `->` threading requires views→views functions | sketch.clj (improved) |
;; | Start from dataset, tc ops before plotting | Var B (tc-native) |
;; | Scales and coordinates as spec keys | Var C (pipeline) |
;; | Multi-dataset via `stack` of pipelines | sketch.clj |
;; | `auto`, `where`, `where-not` for clean pipes | sketch.clj (improved) |
;;
;; The unified design keeps **view-vectors** as the IR.
;; Every function takes views as first argument and returns views.
;; Scales and coordinates are just more keys in the view map.

;; ---

;; ## The Data

(def iris (rdatasets/datasets-iris))
(def iris-cols [:sepal-length :sepal-width :petal-length :petal-width])

;; ## Part 1: Algebra
;;
;; Pure functions on column-pair vectors. Unchanged from sketch.clj.

(defn cross [xs ys] (vec (for [x xs, y ys] [x y])))
(defn stack [& colls] (vec (apply concat colls)))

(defn views
  "Bind a dataset to column pairs → vector of view maps.
  In a -> pipeline, the dataset flows in as first argument."
  [data pairs]
  (mapv (fn [[x y]] {:data data :x x :y y}) pairs))

(defn layer
  "Merge overrides into each view. -> friendly."
  [views & {:as overrides}]
  (mapv #(merge % overrides) views))

(defn layers
  "Stack multiple layers inheriting from the same base views."
  [base-views & layer-specs]
  (apply stack (map #(layer base-views %) layer-specs)))

(defn facet
  "Split each view by a categorical column."
  [views col]
  (vec (mapcat
        (fn [v]
          (let [groups (-> (:data v) (tc/group-by [col]) tc/groups->map)]
            (map (fn [[gk gds]]
                   (assoc v :data gds :facet-val (get gk col)))
                 groups)))
        views)))

;; ## Part 2: Geometry Specs
;;
;; Functions that return partial view-maps. Composable with `layer`/`layers`.

(defn point [& {:as opts}] (merge {:mark :point} opts))
(defn linear [& {:as opts}] (merge {:mark :line :stat :regress} opts))
(defn smooth [& {:as opts}] (merge {:mark :line :stat :smooth} opts))
(defn histogram [& {:as opts}] (merge {:mark :bar :stat :bin} opts))
(defn line-mark [& {:as opts}] (merge {:mark :line :stat :identity} opts))

(defn bar
  "Counted bar chart — categorical x, count on y."
  [& {:as opts}] (merge {:mark :rect :stat :count} opts))

;; Annotation specs
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

;; ## Part 3: View Helpers
;;
;; All views→views, all -> friendly.

(defn diagonal? [v] (= (:x v) (:y v)))

(defn infer-defaults
  "If x=y (diagonal), infer histogram. Otherwise infer scatter."
  [v]
  (if (diagonal? v)
    (merge {:mark :bar :stat :bin} v)
    (merge {:mark :point :stat :identity} v)))

(defn auto
  "Apply infer-defaults to all views."
  [views] (mapv infer-defaults views))

(defn where
  "Keep only views matching predicate."
  [views pred] (vec (filter pred views)))

(defn where-not
  "Remove views matching predicate."
  [views pred] (vec (remove pred views)))

(defn when-diagonal
  "Merge spec into diagonal views only."
  [views spec]
  (mapv (fn [v] (if (diagonal? v) (merge v spec) v)) views))

(defn when-off-diagonal
  "Merge spec into off-diagonal views only."
  [views spec]
  (mapv (fn [v] (if-not (diagonal? v) (merge v spec) v)) views))

(defn distribution
  "Create diagonal views (histograms) for one or more columns."
  [data & cols]
  (views data (mapv (fn [c] [c c]) cols)))

(defn pairs
  "Upper-triangle pairs from columns (no diagonal, no duplicates)."
  [cols]
  (vec (for [i (range (count cols))
             j (range (inc i) (count cols))]
         [(nth cols i) (nth cols j)])))

;; ## Part 4: Scales and Coordinates
;;
;; The new pieces. Scales and coordinates are just map keys
;; on view-maps, applied during rendering.
;;
;; This is the lesson from Var C: scales belong in the spec,
;; not in the data. Tick labels can show original values.

(defn set-scale
  "Set a scale for :x or :y across all views.
  type is :linear (default), :log, or :sqrt.

    (-> views (set-scale :x :log))
    (-> views (set-scale :y :sqrt :domain [0 10]))"
  [views channel type & {:as opts}]
  (let [k (case channel :x :x-scale :y :y-scale)]
    (mapv #(assoc % k (merge {:type type} opts)) views)))

(defn set-coord
  "Set coordinate system for all views.
  :cartesian (default) or :flip (swap x↔y).

    (-> views (set-coord :flip))"
  [views c]
  (mapv #(assoc % :coord c) views))

;; Scales and coordinates compose naturally:
;;
;;   (-> (views iris [[:sepal-length :sepal-width]])
;;       (layer (point :color :species))
;;       (set-scale :x :log)
;;       (set-coord :flip)
;;       plot)
;;
;; The view map becomes:
;;   {:data iris :x :sepal-length :y :sepal-width :mark :point
;;    :color :species :x-scale {:type :log} :coord :flip}

;; ---

;; ## Part 5: Rendering
;;
;; The renderer reads scale and coordinate keys from views.

(def ggplot-palette
  ["#F8766D" "#00BA38" "#619CFF" "#A855F7" "#F97316" "#14B8A6" "#EF4444" "#6B7280"])

(def theme {:bg "#EBEBEB" :grid "#FFFFFF" :font-size 8})

(defn- fmt-name [k] (str/replace (name k) #"[-_]" " "))

(defn- numeric? [v] (number? v))

(defn- domain
  "Compute domain from values. Returns [lo hi] for numeric, or vector of categories for categorical."
  [coll]
  (let [vals (vec coll)]
    (if (or (empty? vals) (numeric? (first vals)))
      (let [lo (reduce min vals) hi (reduce max vals)
            pad (* 0.05 (max 1e-6 (- hi lo)))]
        [(- lo pad) (+ hi pad)])
      (vec (distinct vals)))))

(defn- categorical-domain? [dom] (and (vector? dom) (seq dom) (not (numeric? (first dom)))))

(defn- categorical-scale
  "Band scale: maps each category to a center position. Returns [scale-fn bandwidth]."
  [categories [r0 r1]]
  (let [n (count categories)
        bw (/ (- r1 r0) (max 1 n))
        idx-map (into {} (map-indexed (fn [i c] [c i]) categories))]
    [(fn [v] (+ r0 (* (+ 0.5 (get idx-map v 0)) bw))) bw]))

(defn- linear-scale [[d0 d1] [r0 r1]]
  (let [sd (- d1 d0) sr (- r1 r0)]
    (fn [v] (+ r0 (* sr (/ (- v d0) sd))))))

(defn- nice-ticks [lo hi n]
  (if (or (nil? lo) (nil? hi) (Double/isNaN lo) (Double/isNaN hi)
          (Double/isInfinite lo) (Double/isInfinite hi))
    []
    (let [span (- hi lo)
          raw (/ span (max 1 n))
          mag (Math/pow 10 (Math/floor (Math/log10 (max 1e-10 (abs raw)))))
          step (or (->> [1 2 5 10] (map #(* mag %)) (filter #(>= % (abs raw))) first)
                   mag)
          start (* (Math/ceil (/ lo step)) step)]
      (vec (take-while #(<= % (+ hi 1e-10)) (iterate #(+ % step) start))))))

(defn- apply-scale-fn
  "Apply a scale transform to raw values."
  [vals scale-spec]
  (case (:type scale-spec :linear)
    :log (mapv #(Math/log %) vals)
    :sqrt (mapv #(Math/sqrt %) vals)
    :linear vals
    vals))

(defn- inv-scale-label
  "Produce a tick label showing the original (untransformed) value."
  [v scale-spec]
  (case (:type scale-spec :linear)
    :log (format "%.1f" (Math/exp v))
    :sqrt (format "%.1f" (* v v))
    (format "%.1f" (double v))))

(defn- color-for [categories val]
  (let [idx (.indexOf (vec categories) val)]
    (nth ggplot-palette (mod (if (neg? idx) 0 idx) (count ggplot-palette)))))

;; ### Statistical Transforms

(defmulti compute-stat (fn [view] (or (:stat view) :identity)))

(defmethod compute-stat :identity [view]
  (let [{:keys [data x y color size shape text-col]} view
        xs (vec (data x)) ys (vec (data y))
        szs (when size (vec (data size)))
        shs (when shape (vec (data shape)))
        lbls (when text-col (vec (data text-col)))]
    (if color
      (let [cs (vec (data color))
            groups (group-by #(nth cs %) (range (count xs)))]
        {:points (for [[c idxs] groups]
                   (cond-> {:color c
                            :xs (mapv #(nth xs %) idxs)
                            :ys (mapv #(nth ys %) idxs)}
                     szs (assoc :sizes (mapv #(nth szs %) idxs))
                     shs (assoc :shapes (mapv #(nth shs %) idxs))
                     lbls (assoc :labels (mapv #(nth lbls %) idxs))))})
      {:points [(cond-> {:xs xs :ys ys}
                  szs (assoc :sizes szs)
                  shs (assoc :shapes shs)
                  lbls (assoc :labels lbls))]})))

(defmethod compute-stat :bin [view]
  (let [{:keys [data x color]} view
        xs (vec (data x))]
    (if color
      (let [cs (vec (data color))
            groups (group-by #(nth cs %) (range (count xs)))
            all-cats (sort (distinct cs))]
        {:bins (for [[c idxs] groups
                     :let [vals (mapv #(nth xs %) idxs)
                           hist (stats/histogram vals :sturges)]]
                 {:color c :bin-maps (:bins-maps hist)})
         :max-count (reduce max 1 (for [[_ idxs] groups
                                        :let [h (stats/histogram (mapv #(nth xs %) idxs) :sturges)]
                                        b (:bins-maps h)]
                                    (:count b)))})
      (let [hist (stats/histogram xs :sturges)]
        {:bins [{:bin-maps (:bins-maps hist)}]
         :max-count (reduce max 1 (map :count (:bins-maps hist)))}))))

(defmethod compute-stat :regress [view]
  (let [{:keys [data x y color]} view
        xs (vec (data x)) ys (vec (data y))]
    (if color
      (let [cs (vec (data color))
            groups (group-by #(nth cs %) (range (count xs)))]
        {:lines (for [[c idxs] groups
                      :let [gxs (mapv #(nth xs %) idxs)
                            gys (mapv #(nth ys %) idxs)
                            model (regr/lm gys gxs)
                            xmin (reduce min gxs) xmax (reduce max gxs)]]
                  {:color c
                   :x1 xmin :y1 (regr/predict model [xmin])
                   :x2 xmax :y2 (regr/predict model [xmax])})})
      (let [model (regr/lm ys xs)
            xmin (reduce min xs) xmax (reduce max xs)]
        {:lines [{:x1 xmin :y1 (regr/predict model [xmin])
                  :x2 xmax :y2 (regr/predict model [xmax])}]}))))

(defmethod compute-stat :count [view]
  (let [{:keys [data x color]} view
        xs (vec (data x))
        categories (distinct xs)]
    (if color
      (let [cs (vec (data color))
            pairs (map vector xs cs)
            grouped (group-by identity pairs)
            color-cats (sort (distinct cs))]
        {:categories categories
         :bars (for [cc color-cats]
                 {:color cc
                  :counts (mapv (fn [cat] {:category cat :count (count (get grouped [cat cc] []))})
                                categories)})
         :max-count (reduce max 1 (map #(count (val %)) grouped))})
      {:categories categories
       :bars [{:counts (mapv (fn [cat] {:category cat :count (count (filter #{cat} xs))})
                             categories)}]
       :max-count (reduce max 1 (map (fn [cat] (count (filter #{cat} xs))) categories))})))

;; ### Geometry Renderers

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

(defn- render-points [stat proj all-colors & {:keys [tooltip-fn shape-categories]}]
  (let [project (:point proj)
        all-sizes (seq (mapcat :sizes (:points stat)))
        size-scale (when all-sizes
                     (let [lo (reduce min all-sizes) hi (reduce max all-sizes)]
                       (linear-scale [(double lo) (double hi)] [2.0 8.0])))
        shape-map (when shape-categories
                    (into {} (map-indexed (fn [i c] [c (nth shape-syms (mod i (count shape-syms)))])
                                          shape-categories)))]
    (into [:g]
          (mapcat (fn [{:keys [color xs ys sizes shapes]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [i (range (count xs))
                            :let [[px py] (project (nth xs i) (nth ys i))
                                  r (if sizes (size-scale (nth sizes i)) 2.5)
                                  sh (if shapes (get shape-map (nth shapes i) :circle) :circle)
                                  base-opts {:stroke "#fff" :stroke-width 0.5 :opacity 0.7}
                                  elem (render-shape-elem sh px py r c base-opts)]]
                        (if tooltip-fn
                          (conj elem [:title (tooltip-fn {:x (nth xs i) :y (nth ys i) :color color})])
                          elem))))
                  (:points stat)))))

(defn- render-bars [stat proj all-colors]
  (let [project-rect (:rect proj)
        max-c (:max-count stat)]
    (into [:g]
          (mapcat (fn [{:keys [color bin-maps]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [{:keys [min max count]} bin-maps
                            :let [r (project-rect min 0 max count)]]
                        [:rect (merge r {:fill c :opacity 0.7})])))
                  (:bins stat)))))

(defn- render-categorical-bars [stat proj cat-sx bw all-colors]
  (let [project-rect (:rect proj)
        max-c (:max-count stat)
        n-colors (count (:bars stat))
        sub-bw (/ (* bw 0.8) (max 1 n-colors))]
    (into [:g]
          (mapcat (fn [bi {:keys [color counts]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [{:keys [category count]} counts
                            :let [cx (cat-sx category)
                                  x-lo (+ (- cx (* bw 0.4)) (* bi sub-bw))
                                  x-hi (+ x-lo sub-bw)
                                  r (project-rect x-lo 0 x-hi count)]]
                        [:rect (merge r {:fill c :opacity 0.7})])))
                  (range) (:bars stat)))))

(defn- render-lines [stat proj all-colors]
  (let [project (:point proj)]
    (into [:g]
          (concat
           (when-let [lines (:lines stat)]
             (for [{:keys [color x1 y1 x2 y2]} lines
                   :let [c (if color (color-for all-colors color) "#333")
                         [px1 py1] (project x1 y1)
                         [px2 py2] (project x2 y2)]]
               [:line {:x1 px1 :y1 py1 :x2 px2 :y2 py2
                       :stroke c :stroke-width 1.5}]))
           (when-let [pts (:points stat)]
             (for [{:keys [color xs ys]} pts
                   :let [c (if color (color-for all-colors color) "#333")
                         projected (sort-by first (map (fn [x y] (project x y)) xs ys))]]
               [:polyline {:points (str/join " " (map (fn [[px py]] (str px "," py)) projected))
                           :stroke c :stroke-width 1.5 :fill "none"}]))))))

(defn- render-text [stat proj all-colors]
  (let [project (:point proj)]
    (into [:g {:font-size 9 :fill "#333" :text-anchor "middle"}]
          (mapcat (fn [{:keys [color xs ys labels]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [i (range (count xs))
                            :let [[px py] (project (nth xs i) (nth ys i))]]
                        [:text {:x px :y (- py 5) :fill c}
                         (str (nth labels i))])))
                  (:points stat)))))

;; ### Coordinate Projections
;;
;; A projection is a map of functions that map data-space geometry to SVG.
;; Each coordinate type (cartesian, flip, polar) produces one.
;; Renderers are coord-agnostic — they call these functions, never branch on coord type.

(defn- make-projection
  "Build a projection map for the given coordinate type.
  sx, sy are data-space scales (always: sx maps x-data, sy maps y-data).
  The projection decides which visual axis each data axis targets.
  After domain swap for flip, sx maps y-data→horizontal, sy maps x-data→vertical."
  [coord sx sy pw ph m]
  (case coord
    :flip
    {:point (fn [dx dy] [(sx dx) (sy dy)])
     :rect (fn [x1 y1 x2 y2]
                ;; For flip: data x→vertical (sy), data y→horizontal (sx)
                ;; But with domain swap, sx already maps the swapped domain.
                ;; The stat produces x1/y1 in the view's :x/:y space.
                ;; For histograms (no view swap): x1/x2=bins in x-data, y1/y2=counts
                ;; sx maps y-data-domain→horiz, sy maps x-data-domain→vert
                ;; So bins (x-data) → sy (vertical), counts (y) → sx (horizontal)
             (let [px1 (sy x1) px2 (sy x2) py1 (sx y1) py2 (sx y2)]
               {:x (min py1 py2) :y (min px1 px2)
                :width (abs (- py2 py1)) :height (abs (- px2 px1))}))
     :segment (fn [x1 y1 x2 y2]
                {:x1 (sx y1) :y1 (sy x1) :x2 (sx y2) :y2 (sy x2)})
     :hline (fn [y-val] [:line {:x1 m :y1 (sy y-val) :x2 (- pw m) :y2 (sy y-val)
                                :stroke "#333" :stroke-width 1 :stroke-dasharray "4,3"}])
     :vline (fn [x-val] [:line {:x1 (sx x-val) :y1 m :x2 (sx x-val) :y2 (- ph m)
                                :stroke "#333" :stroke-width 1 :stroke-dasharray "4,3"}])
     :hband (fn [y1 y2] (let [py1 (sy y1) py2 (sy y2)]
                          [:rect {:x m :y (min py1 py2) :width (- pw (* 2 m))
                                  :height (abs (- py2 py1)) :fill "#333" :opacity 0.08}]))
     :grid-x (fn [t] [:line {:x1 (sx t) :y1 m :x2 (sx t) :y2 (- ph m)
                             :stroke (:grid theme) :stroke-width 0.5}])
     :grid-y (fn [t] [:line {:x1 m :y1 (sy t) :x2 (- pw m) :y2 (sy t)
                             :stroke (:grid theme) :stroke-width 0.5}])}

    :polar
    (let [cx (/ pw 2.0) cy (/ ph 2.0)
          r-max (- (min cx cy) m)]
      {:point (fn [dx dy]
                (let [px (sx dx) py (sy dy)
                      angle (* 2.0 Math/PI (/ (- px m) (max 1.0 (- pw m m))))
                      radius (* r-max (/ (- (- ph m) py) (max 1.0 (- ph m m))))]
                  [(+ cx (* radius (Math/cos angle)))
                   (+ cy (* radius (Math/sin angle)))]))
       :rect (fn [x1 y1 x2 y2] {:x 0 :y 0 :width 0 :height 0})
       :segment (fn [x1 y1 x2 y2] {:x1 0 :y1 0 :x2 0 :y2 0})
       :hline (fn [_] [:g])
       :vline (fn [_] [:g])
       :hband (fn [_ _] [:g])
       :grid-x (fn [_] [:g])
       :grid-y (fn [_] [:g])
       :polar-grid
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
                        :stroke (:grid theme) :stroke-width 0.5}])))})

    ;; default: cartesian
    {:point (fn [dx dy] [(sx dx) (sy dy)])
     :rect (fn [x1 y1 x2 y2]
             (let [px1 (sx x1) px2 (sx x2) py1 (sy y1) py2 (sy y2)]
               {:x (min px1 px2) :y (min py1 py2)
                :width (abs (- px2 px1)) :height (abs (- py2 py1))}))
     :segment (fn [x1 y1 x2 y2]
                {:x1 (sx x1) :y1 (sy y1) :x2 (sx x2) :y2 (sy y2)})
     :hline (fn [y-val] [:line {:x1 m :y1 (sy y-val) :x2 (- pw m) :y2 (sy y-val)
                                :stroke "#333" :stroke-width 1 :stroke-dasharray "4,3"}])
     :vline (fn [x-val] [:line {:x1 (sx x-val) :y1 m :x2 (sx x-val) :y2 (- ph m)
                                :stroke "#333" :stroke-width 1 :stroke-dasharray "4,3"}])
     :hband (fn [y1 y2] (let [py1 (sy y1) py2 (sy y2)]
                          [:rect {:x m :y (min py1 py2) :width (- pw (* 2 m))
                                  :height (abs (- py2 py1)) :fill "#333" :opacity 0.08}]))
     :grid-x (fn [t] [:line {:x1 (sx t) :y1 m :x2 (sx t) :y2 (- ph m)
                             :stroke (:grid theme) :stroke-width 0.5}])
     :grid-y (fn [t] [:line {:x1 m :y1 (sy t) :x2 (- pw m) :y2 (sy t)
                             :stroke (:grid theme) :stroke-width 0.5}])}))

;; Annotation renderers now delegate to projection
;; (render-rule-h, render-rule-v, render-band-h removed — projection handles them)

;; (removed)

;; (removed)

(defn- render-legend [categories color-fn & {:keys [x y title]}]
  [:g {:font-family "sans-serif" :font-size 10}
   (when title [:text {:x x :y (- y 5) :fill "#333" :font-size 9} (fmt-name title)])
   (for [[i cat] (map-indexed vector categories)]
     [:g [:circle {:cx x :cy (+ y (* i 16)) :r 4 :fill (color-fn cat)}]
      [:text {:x (+ x 10) :y (+ y (* i 16) 4) :fill "#333"} (str cat)]])])

;; ### Panel Renderer
;;
;; Now honors :x-scale, :y-scale, and :coord from view maps.

(defn render-panel
  [panel-views pw ph m & {:keys [x-domain y-domain show-x? show-y? all-colors tooltip?]
                          :or {show-x? true show-y? true}}]
  (let [v1 (first panel-views)
        x-scale-spec (or (:x-scale v1) {:type :linear})
        y-scale-spec (or (:y-scale v1) {:type :linear})
        coord (or (:coord v1) :cartesian)
        flipped? (= coord :flip)
        polar? (= coord :polar)
        ;; Compute domains
        raw-x-dom (or x-domain (domain (mapcat #(vec ((:data %) (:x %))) panel-views)))
        raw-y-dom (or y-domain (domain (mapcat #(when ((:data %) (:y %)) (vec ((:data %) (:y %)))) panel-views)))
        cat-x? (categorical-domain? raw-x-dom)
        cat-y? (categorical-domain? raw-y-dom)
        ;; Apply scale transforms to numeric domains
        x-dom-t (if cat-x? raw-x-dom (mapv #(first (apply-scale-fn [%] x-scale-spec)) raw-x-dom))
        y-dom-t (if cat-y? raw-y-dom (mapv #(first (apply-scale-fn [%] y-scale-spec)) raw-y-dom))
        ;; For flip: swap domains so visual axes swap (sx gets y-data-domain, sy gets x-data-domain)
        [x-dom' y-dom'] (if flipped? [y-dom-t x-dom-t] [x-dom-t y-dom-t])
        cat-x?' (if flipped? cat-y? cat-x?)
        cat-y?' (if flipped? cat-x? cat-y?)
        ;; Build pixel scales (visual axes)
        [sx bw-x] (if cat-x?'
                    (categorical-scale x-dom' [m (- pw m)])
                    [(linear-scale x-dom' [m (- pw m)]) nil])
        [sy bw-y] (if cat-y?'
                    (categorical-scale y-dom' [(- ph m) m])
                    [(linear-scale y-dom' [(- ph m) m]) nil])
        ;; Build projection — the single source of coord logic
        proj (make-projection coord sx sy pw ph m)
        ;; Ticks
        x-ticks (if cat-x?' x-dom' (nice-ticks (first x-dom') (second x-dom') 5))
        y-ticks (if cat-y?' y-dom' (nice-ticks (first y-dom') (second y-dom') 5))
        ;; Scale specs for tick labels (flip-aware)
        [x-label-spec y-label-spec] (if flipped? [y-scale-spec x-scale-spec] [x-scale-spec y-scale-spec])
        ;; Shape categories
        shape-categories (when-let [shape-col (first (keep :shape panel-views))]
                           (sort (distinct (mapcat #(when (:shape %) (vec ((:data %) (:shape %)))) panel-views))))
        ;; Tooltip
        tooltip-fn (when tooltip?
                     (fn [{:keys [x y color]}]
                       (str/join ", " (cond-> [(format "x=%.2f" (double x)) (format "y=%.2f" (double y))]
                                        color (conj (str "color=" color))))))]
    [:g
     [:rect {:x 0 :y 0 :width pw :height ph :fill (:bg theme)}]
     ;; Grid — via projection
     (if-let [pg (:polar-grid proj)]
       pg
       (into [:g]
             (concat
              (map (:grid-x proj) x-ticks)
              (map (:grid-y proj) y-ticks))))
     ;; Data layers
     (into [:g]
           (mapcat (fn [view]
                     (let [mark (:mark view)]
                       ;; Annotations — use projection's hline/vline/hband
                       (case mark
                         :rule-h [((:hline proj) (:value view))]
                         :rule-v [((:vline proj) (:value view))]
                         :band-h [((:hband proj) (:y1 view) (:y2 view))]
                         ;; Data-driven marks
                         (let [;; Scale transforms on data (non-categorical, non-linear only)
                               view' (if (and (not cat-x?) (not cat-y?)
                                              (or (not= :linear (:type x-scale-spec :linear))
                                                  (not= :linear (:type y-scale-spec :linear))))
                                       (let [d (:data view) x (:x view) y (:y view)
                                             new-x-vals (apply-scale-fn (vec (d x)) x-scale-spec)
                                             new-y-vals (apply-scale-fn (vec (d y)) y-scale-spec)
                                             new-ds (-> d
                                                        (tc/add-column x new-x-vals)
                                                        (tc/add-column y new-y-vals))]
                                         (assoc view :data new-ds))
                                       view)
                               ;; For non-histogram marks with flip: swap x/y on view
                               ;; so compute-stat sees the right columns.
                               ;; Histograms: don't swap — the projection's :rect handles it.
                               hist? (= mark :bar)
                               view'' (if (and flipped? (not hist?))
                                        (assoc view' :x (:y view') :y (:x view'))
                                        view')
                               stat (compute-stat view'')]
                           (case mark
                             :point [(render-points stat proj all-colors
                                                    :tooltip-fn tooltip-fn
                                                    :shape-categories shape-categories)]
                             :bar [(render-bars stat proj all-colors)]
                             :rect [(render-categorical-bars stat proj sx bw-x all-colors)]
                             :line [(render-lines stat proj all-colors)]
                             :text [(render-text stat proj all-colors)]
                             [(render-points stat proj all-colors
                                             :tooltip-fn tooltip-fn
                                             :shape-categories shape-categories)])))))
                   panel-views))
     ;; Tick labels
     (when show-x?
       (into [:g {:font-size (:font-size theme) :fill "#666"}]
             (if cat-x?'
               (for [t x-ticks] [:text {:x (sx t) :y (- ph 2) :text-anchor "middle"} (str t)])
               (for [t x-ticks] [:text {:x (sx t) :y (- ph 2) :text-anchor "middle"}
                                 (inv-scale-label t x-label-spec)]))))
     (when show-y?
       (into [:g {:font-size (:font-size theme) :fill "#666"}]
             (if cat-y?'
               (for [t y-ticks] [:text {:x (- m 3) :y (+ (sy t) 3) :text-anchor "end"} (str t)])
               (for [t y-ticks] [:text {:x (- m 3) :y (+ (sy t) 3) :text-anchor "end"}
                                 (inv-scale-label t y-label-spec)]))))]))

;; ### The Plot Function

(defn plot
  "Render views to SVG.
  Options:
    :panel-size  pixels per panel side (default 150)
    :scales      :shared (default), :free, :free-x, :free-y
    :tooltip     true to enable hover tooltips
    :brush       true to enable rectangle-select brushing"
  [views & {:keys [panel-size margin gap scales tooltip brush]
            :or {panel-size 150 margin 25 gap 5 scales :shared}}]
  (let [;; Filter out annotation views for domain/grouping purposes
        data-views (remove #(#{:rule-h :rule-v :band-h} (:mark %)) views)
        has-facet? (some :facet-val views)
        panel-key (fn [v] [(:x v) (:y v) (:facet-val v)])
        panels (group-by panel-key views)
        ;; Derive ordering from views vector, not from (keys panels) which is a HashMap
        x-vars (distinct (map :x data-views))
        y-vars (distinct (map :y data-views))
        facet-vals (distinct (remove nil? (map :facet-val views)))
        nc-base (count x-vars)
        nr (count y-vars)
        nf (max 1 (count facet-vals))
        nc (if has-facet? (* nc-base nf) nc-base)
        single? (and (= 1 nc) (= 1 nr) (not has-facet?))
        pw (if single? 400 panel-size) ph (if single? 300 panel-size)
        ;; Global domains (used when scales = :shared)
        all-datasets (map :data data-views)
        free-x? (#{:free :free-x} scales)
        free-y? (#{:free :free-y} scales)
        x-doms (when-not free-x?
                 (into {} (for [xv x-vars] [xv (domain (mapcat #(vec (% xv)) all-datasets))])))
        y-doms (when-not free-y?
                 (into {} (for [yv y-vars] [yv (domain (mapcat #(when (% yv) (vec (% yv))) all-datasets))])))
        ;; All unique color categories across views
        all-colors (let [color-views (filter :color data-views)]
                     (when (seq color-views)
                       (sort (distinct (mapcat #(vec ((:data %) (:color %))) color-views)))))
        first-color-key (first (keep :color data-views))
        ;; Shape legend
        shape-col (first (keep :shape data-views))
        shape-categories (when shape-col
                           (sort (distinct (mapcat #(when (:shape %) (vec ((:data %) (:shape %)))) data-views))))
        legend-w (if (or all-colors shape-categories) 100 0)
        left-pad (if single? 0 15)
        top-pad (if single? 0 20)
        grid-w (if single? pw (* nc (+ pw gap)))
        grid-h (if single? ph (* nr (+ ph gap)))
        tw (+ left-pad grid-w (if single? 0 15) legend-w)
        th (+ top-pad grid-h)
        svg-content
        (svg/svg {:width tw :height th}
                 (when-not single?
                   (into [:g]
                         (concat
                          (if has-facet?
                            (for [[fi fv] (map-indexed vector (or (not-empty facet-vals) [nil]))
                                  [ci xv] (map-indexed vector x-vars)]
                              [:text {:x (+ left-pad (* (+ (* fi nc-base) ci) (+ pw gap)) (/ pw 2))
                                      :y 12 :text-anchor "middle" :font-size 10 :fill "#333"}
                               (str (fmt-name xv) " | " (fmt-name fv))])
                            (map-indexed (fn [ci xv]
                                           [:text {:x (+ left-pad (* ci (+ pw gap)) (/ pw 2))
                                                   :y 12 :text-anchor "middle" :font-size 10 :fill "#333"}
                                            (fmt-name xv)])
                                         x-vars))
                          (map-indexed (fn [ri yv]
                                         [:text {:x (+ left-pad grid-w 10)
                                                 :y (+ top-pad (* ri (+ ph gap)) (/ ph 2))
                                                 :text-anchor "middle" :font-size 10 :fill "#333"
                                                 :transform (str "rotate(90,"
                                                                 (+ left-pad grid-w 10) ","
                                                                 (+ top-pad (* ri (+ ph gap)) (/ ph 2)) ")")}
                                          (fmt-name yv)])
                                       y-vars))))
                 (if single?
                   (render-panel (val (first panels)) pw ph margin
                                 :all-colors all-colors :tooltip? tooltip)
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
                                          :x-domain (when x-doms (x-doms xv))
                                          :y-domain (when y-doms (y-doms yv))
                                          :show-x? (or (= ri (dec nr)) free-x?)
                                          :show-y? (or (zero? col-idx) free-y?)
                                          :all-colors all-colors
                                          :tooltip? tooltip)])))
                 ;; Color legend
                 (when all-colors
                   (render-legend all-colors #(color-for all-colors %)
                                  :x (+ left-pad grid-w (if single? 10 25))
                                  :y (+ top-pad 5)
                                  :title first-color-key))
                 ;; Shape legend (below color legend)
                 (when shape-categories
                   (let [y-off (+ top-pad 5 (if all-colors (* (count all-colors) 16) 0) 10)
                         x-off (+ left-pad grid-w (if single? 10 25))]
                     (into [:g {:font-family "sans-serif" :font-size 10}
                            (when shape-col [:text {:x x-off :y (- y-off 5) :fill "#333" :font-size 9}
                                             (fmt-name shape-col)])]
                           (for [[i cat] (map-indexed vector shape-categories)
                                 :let [sh (nth shape-syms (mod i (count shape-syms)))]]
                             [:g (render-shape-elem sh x-off (+ y-off (* i 16)) 4 "#666"
                                                    {:stroke "none"})
                              [:text {:x (+ x-off 10) :y (+ y-off (* i 16) 4) :fill "#333"}
                               (str cat)]])))))]
    (if brush
      ;; Brushing: wrap in HTML with inline JS
      (kind/hiccup
       [:div {:style {:position "relative" :display "inline-block"}}
        svg-content
        [:script (str "
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
")]])
      (kind/hiccup svg-content))))

;; ---

;; ## Part 6: Examples
;;
;; From simplest to most complex. Every example is a -> pipeline.
;; `let` is only used when there is genuinely new data to name.

;; ### 1. Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point))
    plot)

;; ### 2. Colored scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    plot)

;; ### 3. Scatter + regression

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point :color :species)
            (linear :color :species))
    plot)

;; ### 4. Histogram

(-> (distribution iris :sepal-length)
    auto
    plot)

;; ### 5. Colored histogram

(-> (distribution iris :sepal-length)
    (layer (histogram :color :species))
    plot)

;; ### 6. Faceted scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (facet :species)
    (plot :panel-size 200))

;; ### 7. Flipped coordinates

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (set-coord :flip)
    plot)

;; Axis labels and data swap. The view map says `:coord :flip`;
;; the renderer handles the projection.

;; Flip composes with any mark — here a horizontal histogram:

(-> (distribution iris :sepal-length)
    (layer (histogram :color :species))
    (set-coord :flip)
    plot)

;; The projection turns vertical bars into horizontal bars automatically.
;; No special case in the renderer — just a different coordinate mapping.

;; ### 8. Log scale

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (set-scale :x :log)
    plot)

;; Tick labels show original values (not log values).
;; The scale is a rendering directive, not a data transform.

;; ### 9. Multi-dataset: reference line overlay

(let [ref (tc/dataset {:sepal-length [4.5 8.0]
                       :sepal-width [2.0 4.5]})]
  (plot (stack (-> (views iris [[:sepal-length :sepal-width]])
                   (layer (point :color :species)))
               (-> (views ref [[:sepal-length :sepal-width]])
                   (layer (line-mark))))))

;; Two `->` pipelines, `stack`-ed together. Each can have its own dataset.

;; ### 10. Filter + plot (tc integration)

(-> iris
    (tc/select-rows (fn [row] (not= "setosa" (:species row))))
    (views [[:petal-length :petal-width]])
    (layers (point :color :species)
            (linear :color :species))
    plot)

;; tc/select-rows flows naturally into views. No wrapper needed.

;; ### 11. Asymmetric grid

(-> (views iris (cross [:petal-length :petal-width]
                       [:sepal-length :sepal-width]))
    (layer (point :color :species))
    plot)

;; ### 12. 4×4 SPLOM

(-> (views iris (cross iris-cols iris-cols))
    auto
    (when-off-diagonal {:color :species})
    plot)

;; ### 13. SPLOM + regression

(plot (stack (-> (views iris (cross iris-cols iris-cols))
                 auto
                 (when-off-diagonal {:color :species}))
             (-> (views iris (cross iris-cols iris-cols))
                 (where-not diagonal?)
                 (layer (linear :color :species)))))

;; ### 14. Heterogeneous SPLOM

(let [idx (fn [c] (.indexOf iris-cols c))
      upper? (fn [v] (> (idx (:y v)) (idx (:x v))))
      lower? (fn [v] (< (idx (:y v)) (idx (:x v))))]
  (plot (stack (-> (views iris (cross iris-cols iris-cols)) (where upper?) (layer (point :color :species)))
               (-> (views iris (cross iris-cols iris-cols)) (where diagonal?) (layer (histogram :color :species)))
               (-> (views iris (cross iris-cols iris-cols)) (where lower?) (layer (linear :color :species))))))

;; ### 15. SPLOM with log scale

(-> (views iris (cross iris-cols iris-cols))
    auto
    (when-off-diagonal {:color :species})
    (set-scale :x :log)
    plot)

;; Log scale on x applies to all panels. Tick labels show original values.
;; Note: set-scale :y :log on histogram panels needs care,
;; since the y-axis represents counts, not data values.

;; ### 16. Categorical bar chart

(-> (views iris [[:species :species]])
    (layer (bar))
    plot)

;; Bar heights are counts per category. The x-axis is categorical.

;; ### 17. Colored categorical bar chart

(-> (views iris [[:species :species]])
    (layer (bar :color :species))
    plot)

;; ### 18. Bubble chart (size aesthetic)

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species :size :petal-length))
    plot)

;; Circle radius encodes petal-length. Larger petals → larger dots.

;; ### 19. Shape aesthetic

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :shape :species))
    plot)

;; Each species gets a distinct shape (circle, square, triangle).

;; ### 20. Annotations: reference line and band

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point :color :species)
            (hline 3.0)
            (hband 2.5 3.5)
            (vline 6.0))
    plot)

;; Dashed lines mark thresholds; the shaded band highlights a y-range.

;; ### 21. Text labels at group means

(let [means (-> iris
                (tc/group-by :species)
                (tc/aggregate {:sepal-length #(dfn/mean (% :sepal-length))
                               :sepal-width #(dfn/mean (% :sepal-width))
                               :label (fn [ds] (first (ds :$group-name)))}))]
  (plot (stack (-> (views iris [[:sepal-length :sepal-width]])
                   (layer (point :color :species)))
               (-> (views means [[:sepal-length :sepal-width]])
                   (layer (text-label :label))))))

;; ### 22. Faceted scatter with free y-scale

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point))
    (facet :species)
    (plot :panel-size 200 :scales :free-y))

;; Each facet gets its own y-axis range. Tick labels appear on every panel.

;; ### 23. Polar coordinates

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (set-coord :polar)
    plot)

;; x maps to angle, y to radius. Grid shows concentric circles + radials.

;; ### 24. Tooltips on hover

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (plot :tooltip true))

;; Hover over a point to see its x, y, and color values.

;; ### 25. Brushable scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (plot :brush true))

;; Click and drag to select a region. Selected points stay opaque; others fade.
;; ---

;; ## Part 7: The API at a Glance
;;
;; ### Construction (data → views)
;;
;; | Function | Signature | Purpose |
;; |----------|-----------|---------|
;; | `views` | `ds pairs → views` | Bind dataset to column pairs |
;; | `distribution` | `ds & cols → views` | Diagonal views (histograms) |
;; | `cross` | `xs ys → pairs` | Cartesian product of columns |
;; | `pairs` | `cols → pairs` | Upper-triangle pairs |
;; | `stack` | `views... → views` | Concatenate view vectors |
;;
;; ### Geometry (views → views)
;;
;; | Function | Returns | Purpose |
;; |----------|---------|---------|
;; | `layer` | merged views | Add overrides to all views |
;; | `layers` | stacked views | Multiple layers from same base |
;; | `point` | `{:mark :point}` | Scatter / bubble spec (`:color`, `:size`, `:shape`) |
;; | `linear` | `{:mark :line :stat :regress}` | Regression line spec |
;; | `histogram` | `{:mark :bar :stat :bin}` | Histogram spec |
;; | `line-mark` | `{:mark :line :stat :identity}` | Connect-the-dots spec |
;; | `bar` | `{:mark :rect :stat :count}` | Counted bar chart (categorical x) |
;; | `text-label` | `{:mark :text :text-col col}` | Text labels at data positions |
;; | `hline` | `{:mark :rule-h}` | Horizontal reference line |
;; | `vline` | `{:mark :rule-v}` | Vertical reference line |
;; | `hband` | `{:mark :band-h}` | Horizontal reference band |
;;
;; ### Filtering (views → views)
;;
;; | Function | Purpose |
;; |----------|---------|
;; | `auto` | Apply infer-defaults to all views |
;; | `where` | Keep views matching predicate |
;; | `where-not` | Remove views matching predicate |
;; | `when-diagonal` | Merge spec into diagonal views |
;; | `when-off-diagonal` | Merge spec into off-diagonal views |
;;
;; ### Scales & Coordinates (views → views)
;;
;; | Function | Purpose |
;; |----------|---------|
;; | `set-scale` | `:x` or `:y` scale — `:linear`, `:log`, `:sqrt`; categorical auto-detected |
;; | `set-coord` | `:cartesian`, `:flip`, or `:polar` |
;;
;; ### Grouping (views → views)
;;
;; | Function | Purpose |
;; |----------|---------|
;; | `facet` | Split by categorical column |
;;
;; ### Rendering (views → SVG)
;;
;; | Function | Options | Purpose |
;; |----------|---------|---------|
;; | `plot` | `:panel-size`, `:scales`, `:tooltip`, `:brush` | Terminal: produce SVG from views |
;;
;; `:scales` — `:shared` (default), `:free`, `:free-x`, `:free-y`

;; ---

;; ## Part 8: Reflection
;;
;; The unified design preserves what each variation got right:
;;
;; **From sketch.clj:** Views as plain maps. The IR is transparent, inspectable,
;; and compositional. `cross`, `stack`, and algebraic thinking.
;;
;; **From Var B (tc-native):** Start from dataset with `->`.
;; `(-> ds (tc/select-rows ...) (views pairs) ...)` reads like prose.
;;
;; **From Var C (pipeline):** Scales and coordinates as spec keys.
;; `set-scale :x :log` is a rendering directive, not a data transform.
;; Tick labels show original values.
;;
;; **The key insight:** Views are already `->` friendly.
;; We don't need a separate context map or dataset metadata.
;; We just need every function to be `views → views`.
;;
;; **What we built (all six "what remains" items):**
;;
;; 1. **Categorical scales** — auto-detected from data type. Band scale for
;;    categories, `(bar)` for counted bar charts, jittered categorical scatter.
;;
;; 2. **Size and shape aesthetics** — `(point :size :col :shape :col)`.
;;    Size scales radius 2–8px. Shape cycles: circle, square, triangle, diamond.
;;    Both get legends.
;;
;; 3. **Annotations** — `(hline y)`, `(vline x)`, `(hband y1 y2)` for reference
;;    marks. `(text-label col)` for data-positioned text. All compose with `layers`.
;;
;; 4. **Shared vs. free scales** — `(plot views :scales :free-y)`. Per-panel
;;    domains when free; tick labels on every panel.
;;
;; 5. **Polar coordinates** — `(set-coord :polar)`. x → angle, y → radius.
;;    Concentric circle grid + radial lines.
;;
;; 6. **Interactive features** — `(plot views :tooltip true)` for SVG hover
;;    tooltips. `(plot views :brush true)` for rectangle-select brushing via
;;    inline JavaScript.
;;
;; The architecture's promise held: each feature was "add a key to the view map,
;; teach the renderer." No redesign was needed.
;;
;; **Composable coordinates:** The renderer uses a **projection** — a map of
;; functions (`{:point :rect :segment :hline :vline :hband :grid-x :grid-y}`)
;; that each coordinate type implements. Renderers are coord-agnostic: they
;; call `(:point proj)` or `(:rect proj)`, never branch on `:flip` or `:polar`.
;; Flipping a histogram from vertical to horizontal requires zero special-case
;; code — the projection's `:rect` naturally maps (bin-min, 0, bin-max, count)
;; to the right orientation.
