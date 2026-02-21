^{:kindly/hide-code true
  :clay {:title "PoC: Wadogo Scales + Composable Coordinates"
         :quarto {:type :post
                  :author [:daslu :claude]
                  :date "2026-02-21"
                  :description "Exploring wadogo for scales, geom.viz for polar projection, and stat-driven domains."
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :design :wadogo :poc]
                  :toc true
                  :toc-depth 3
                  :draft true}}}
(ns data-visualization.aog.sketch-wadogo
  "PoC: Grammar of Graphics with Wadogo scales.

  Three improvements over sketch_unified.clj:
    1. Scales delegated to wadogo — ticks, formatting, bands, log, datetime
    2. Stat-driven domains — histograms work because y-domain comes from counts
    3. Single coord function — [x y] → [px py], no M×N combinatorics

  Same algebra and API shape as sketch_unified."
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

;; # PoC: Wadogo + Composable Coordinates
;;
;; This notebook explores three architectural improvements to our
;; grammar of graphics:
;;
;; 1. **Wadogo for scales** — domain→range, ticks, formatting
;; 2. **Stat-driven domains** — stats output the y-domain, fixing histograms
;; 3. **Single coord function** — `[x y] → [px py]`, eliminating M×N

;; ---

;; ## The Data

(def iris (rdatasets/datasets-iris))
(def iris-cols [:sepal-length :sepal-width :petal-length :petal-width])

(def mpg (rdatasets/ggplot2-mpg))

;; ## Part 1: Algebra
;;
;; Identical to sketch_unified.clj.

(defn cross [xs ys] (vec (for [x xs, y ys] [x y])))
(defn stack [& colls] (vec (apply concat colls)))

(defn views
  "Bind a dataset to column pairs → vector of view maps."
  [data pairs]
  (mapv (fn [[x y]] {:data data :x x :y y}) pairs))

(defn layer
  "Merge overrides into each view."
  [views & {:as overrides}]
  (mapv #(merge % overrides) views))

(defn layers
  "Stack multiple layers from the same base views."
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

(defn facet-grid
  "Split each view by two categorical columns for a row × column grid.
  row-col determines the rows, col-col determines the columns."
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

;; ## Part 2: Geometry Specs

(defn point [& {:as opts}] (merge {:mark :point} opts))
(defn linear [& {:as opts}] (merge {:mark :line :stat :regress} opts))

(defn smooth [& {:as opts}] (merge {:mark :line :stat :smooth} opts))
(defn histogram [& {:as opts}] (merge {:mark :bar :stat :bin} opts))
(defn line-mark [& {:as opts}] (merge {:mark :line :stat :identity} opts))
(defn bar [& {:as opts}] (merge {:mark :rect :stat :count} opts))

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

(defn value-bar
  "Pre-aggregated bars: categorical x, continuous y, no counting.
  Use when the data already has the values you want to plot."
  [& {:as opts}] (merge {:mark :rect :stat :identity} opts))

(defn stacked-bar
  "Stacked bar chart: bars stacked on top of each other by color group."
  [& {:as opts}] (merge {:mark :rect :stat :count :position :stack} opts))

;; ## Part 3: View Helpers

(defn diagonal? [v] (= (:x v) (:y v)))

(defn infer-defaults [v]
  (if (diagonal? v)
    (merge {:mark :bar :stat :bin} v)
    (merge {:mark :point :stat :identity} v)))

(defn auto [views] (mapv infer-defaults views))

(defn where [views pred] (vec (filter pred views)))
(defn where-not [views pred] (vec (remove pred views)))

(defn when-diagonal [views spec]
  (mapv (fn [v] (if (diagonal? v) (merge v spec) v)) views))

(defn when-off-diagonal [views spec]
  (mapv (fn [v] (if-not (diagonal? v) (merge v spec) v)) views))

(defn distribution [data & cols]
  (views data (mapv (fn [c] [c c]) cols)))

(defn pairs [cols]
  (vec (for [i (range (count cols))
             j (range (inc i) (count cols))]
         [(nth cols i) (nth cols j)])))

;; ## Part 4: Scales and Coordinates

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

;; ## Part 5: Rendering with Wadogo
;;
;; This is where the PoC diverges from sketch_unified.

;; ### Theme

(def ggplot-palette
  ["#F8766D" "#00BA38" "#619CFF" "#A855F7" "#F97316" "#14B8A6" "#EF4444" "#6B7280"])

(def theme {:bg "#EBEBEB" :grid "#FFFFFF" :font-size 8})

(defn- fmt-name [k] (str/replace (name k) #"[-_]" " "))

(defn- color-for [categories val]
  (let [idx (.indexOf (vec categories) val)]
    (nth ggplot-palette (mod (if (neg? idx) 0 idx) (count ggplot-palette)))))

;; ### Statistical Transforms
;;
;; Key change: each stat returns :x-domain and :y-domain alongside its data.
;; This fixes the histogram bug — the renderer uses stat domains, not data columns.

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

(defmulti compute-stat (fn [view] (or (:stat view) :identity)))

(defmethod compute-stat :identity [view]
  (let [{:keys [data x y color size shape text-col x-type]} view
        raw-xs (vec (data x)) raw-ys (vec (data y))
        raw-szs (when size (vec (data size)))
        raw-shs (when shape (vec (data shape)))
        raw-lbls (when text-col (vec (data text-col)))
        [xs0 ys szs shs lbls] (clean-paired raw-xs raw-ys raw-szs raw-shs raw-lbls)
        ;; Coerce to categorical if requested
        xs (if (= x-type :categorical) (mapv str xs0) xs0)
        cat-x? (and (seq xs) (not (number? (first xs))))
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
       :y-domain y-dom})))

(defmethod compute-stat :bin [view]
  (let [{:keys [data x color]} view
        xs (clean-vec (vec (data x)))]
    (if color
      (let [cs (vec (data color))
            raw-xs (vec (data x))
            ;; Align cs with cleaned xs
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
         :y-domain [0 max-count]}))))

(defmethod compute-stat :regress [view]
  (let [{:keys [data x y color]} view
        raw-xs (vec (data x)) raw-ys (vec (data y))
        [xs ys] (clean-paired raw-xs raw-ys)]
    (if color
      (let [raw-cs (vec (data color))
            [_ _ cs] (clean-paired raw-xs raw-ys raw-cs)
            groups (group-by #(nth cs %) (range (count xs)))]
        {:lines (for [[c idxs] groups
                      :let [gxs (mapv #(nth xs %) idxs)
                            gys (mapv #(nth ys %) idxs)
                            model (regr/lm gys gxs)
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
         :y-domain [(reduce min ys) (reduce max ys)]}))))

(defmethod compute-stat :smooth [view]
  (let [{:keys [data x y color]} view
        raw-xs (vec (data x)) raw-ys (vec (data y))
        [xs ys] (clean-paired raw-xs raw-ys)
        n-sample 80
        dedup-sort (fn [gxs gys]
                     ;; Average duplicate x values, then sort — loess needs strict monotonicity
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
         :y-domain [(reduce min ys) (reduce max ys)]}))))

(defmethod compute-stat :count [view]
  (let [{:keys [data x color x-type]} view
        raw-xs (vec (data x))
        xs (if (= x-type :categorical) (mapv str raw-xs) raw-xs)
        categories (vec (distinct xs))]
    (if color
      (let [cs (vec (data color))
            pairs (map vector xs cs)
            grouped (group-by identity pairs)
            color-cats (sort (distinct cs))
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
         :y-domain [0 max-count]}))))

;; ### Wadogo Scale Construction
;;
;; Auto-detect scale type from domain values.
;; Returns the wadogo scale object — callable, has ticks, has format.

(defn- numeric-domain? [dom]
  (and (sequential? dom) (seq dom) (number? (first dom))))

(defn- categorical-domain? [dom]
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

;; ### Coordinate Functions
;;
;; A coord is a single function: [data-x data-y] → [pixel-x pixel-y].
;; Scales map data → intermediate values. The coord maps those → final pixels.
;;
;; For cartesian/flip, the scales already produce pixel coords, so the coord
;; is trivial. For polar, it remaps from (angle, radius) to (px, py).

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
          ;; Pixel range bounds (accounting for SVG y inversion)
          x-lo (double m)
          x-span (double (- pw m m))
          y-lo (double m)
          y-span (double (- ph m m))]
      (fn [dx dy]
        (let [px (sx dx) py (sy dy)
              ;; Normalize to [0,1] using pixel positions
              t-angle (/ (- px x-lo) (max 1.0 x-span))
              ;; For y: small py = top of panel = large data value = large radius
              t-radius (/ (- (+ y-lo y-span) py) (max 1.0 y-span))
              ;; Map to angle and radius
              angle (* 2.0 Math/PI t-angle)
              radius (* r-max t-radius)]
          [(+ cx (* radius (Math/cos (- angle (/ Math/PI 2.0)))))
           (+ cy (* radius (Math/sin (- angle (/ Math/PI 2.0)))))])))

    ;; default: cartesian
    (fn [dx dy] [(sx dx) (sy dy)])))

;; ### Renderers
;;
;; All renderers take a `coord` function and call it for every point.
;; No coord-type branching anywhere.

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

(defn- render-bars
  "Render histogram bars. Each bar is a rect [x1, 0] to [x2, count].
  We project the 4 corners through the coord function and draw a polygon."
  [stat coord all-colors]
  (into [:g]
        (mapcat (fn [{:keys [color bin-maps]}]
                  (let [c (if color (color-for all-colors color) "#333")]
                    (for [{:keys [min max count]} bin-maps
                          :let [;; 4 corners in data space
                                [x1 y1] (coord min 0)
                                [x2 y2] (coord max 0)
                                [x3 y3] (coord max count)
                                [x4 y4] (coord min count)]]
                      [:polygon {:points (str x1 "," y1 " " x2 "," y2 " "
                                              x3 "," y3 " " x4 "," y4)
                                 :fill c :opacity 0.7}])))
                (:bins stat))))

(defn- render-categorical-bars
  "Render counted categorical bars using wadogo band scale."
  [stat coord sx-scale all-colors & {:keys [position] :or {position :dodge}}]
  (let [bw (ws/data sx-scale :bandwidth)
        n-colors (clojure.core/count (:bars stat))
        sub-bw (/ (* bw 0.8) (clojure.core/max 1 n-colors))
        cum-y (atom {})]
    (into [:g]
          (mapcat (fn [bi {:keys [color counts]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [{:keys [category count]} counts
                            :let [band-info (sx-scale category true)
                                  band-start (:rstart band-info)
                                  band-end (:rend band-info)
                                  band-mid (/ (+ band-start band-end) 2.0)]]
                        (if (= position :stack)
                          (let [base (get @cum-y category 0)
                                [_ yb] (coord (first (:x-domain stat)) base)
                                [_ yt] (coord (first (:x-domain stat)) (+ base count))
                                x-lo (+ (- band-mid (/ (* bw 0.4))) (* 0.1 bw))
                                x-hi (- (+ band-mid (/ (* bw 0.4))) (* 0.1 bw))]
                            (swap! cum-y assoc category (+ base count))
                            [:rect {:x x-lo :y (clojure.core/min yb yt)
                                    :width (- x-hi x-lo)
                                    :height (Math/abs (- yb yt))
                                    :fill c :opacity 0.7}])
                          ;; dodge (original behavior)
                          (let [x-lo (+ (- band-mid (/ (* bw 0.4))) (* bi sub-bw))
                                x-hi (+ x-lo sub-bw)
                                [_ y-bottom] (coord (first (:x-domain stat)) 0)
                                [_ y-top] (coord (first (:x-domain stat)) count)]
                            [:rect {:x x-lo :y (clojure.core/min y-bottom y-top)
                                    :width (- x-hi x-lo)
                                    :height (Math/abs (- y-bottom y-top))
                                    :fill c :opacity 0.7}])))))
                  (range) (:bars stat)))))

(defn- render-value-bars
  "Render pre-aggregated bars: categorical x, continuous y.
  stat has :points [{:xs [...] :ys [...] :color ...}]."
  [stat coord sx-scale all-colors & {:keys [position] :or {position :dodge}}]
  (let [bw (ws/data sx-scale :bandwidth)
        groups (:points stat)
        n-groups (clojure.core/count groups)
        sub-bw (/ (* bw 0.8) (clojure.core/max 1 n-groups))
        ;; For stacking: track cumulative y per category
        cum-y (atom {})]
    (into [:g]
          (mapcat (fn [gi {:keys [color xs ys]}]
                    (let [c (if color (color-for all-colors color) "#333")]
                      (for [i (range (clojure.core/count xs))
                            :let [cat (nth xs i)
                                  val (nth ys i)
                                  band-info (sx-scale cat true)
                                  band-start (:rstart band-info)
                                  band-end (:rend band-info)
                                  band-mid (/ (+ band-start band-end) 2.0)
                                  [_ y-bottom] (coord cat 0)
                                  [_ y-top] (coord cat val)]]
                        (if (= position :stack)
                          (let [base (get @cum-y cat 0)
                                [_ yb] (coord cat base)
                                [_ yt] (coord cat (+ base val))
                                x-lo (+ (- band-mid (/ (* bw 0.4))) (* 0.1 bw))
                                x-hi (- (+ band-mid (/ (* bw 0.4))) (* 0.1 bw))]
                            (swap! cum-y assoc cat (+ base val))
                            [:rect {:x x-lo :y (clojure.core/min yb yt)
                                    :width (- x-hi x-lo)
                                    :height (Math/abs (- yb yt))
                                    :fill c :opacity 0.7}])
                          ;; dodge
                          (let [x-lo (+ (- band-mid (/ (* bw 0.4))) (* gi sub-bw))
                                x-hi (+ x-lo sub-bw)]
                            [:rect {:x x-lo :y (clojure.core/min y-bottom y-top)
                                    :width (- x-hi x-lo)
                                    :height (Math/abs (- y-bottom y-top))
                                    :fill c :opacity 0.7}])))))
                  (range) groups))))

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

(defn- render-text [stat coord all-colors]
  (into [:g {:font-size 9 :fill "#333" :text-anchor "middle"}]
        (mapcat (fn [{:keys [color xs ys labels]}]
                  (let [c (if color (color-for all-colors color) "#333")]
                    (for [i (range (count xs))
                          :let [[px py] (coord (nth xs i) (nth ys i))]]
                      [:text {:x px :y (- py 5) :fill c}
                       (str (nth labels i))])))
                (:points stat))))

;; ### Grid Drawing

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
           ;; Concentric circles
           (for [i (range 1 6)
                 :let [r (* r-max (/ i 5.0))]]
             [:circle {:cx cx :cy cy :r r :fill "none"
                       :stroke (:grid theme) :stroke-width 0.5}])
           ;; Radial lines
           (for [i (range 8)
                 :let [a (* i (/ Math/PI 4))]]
             [:line {:x1 cx :y1 cy
                     :x2 (+ cx (* r-max (Math/cos a)))
                     :y2 (+ cy (* r-max (Math/sin a)))
                     :stroke (:grid theme) :stroke-width 0.5}])))))

;; ### Tick Labels

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

;; ### Legend

(defn- render-legend [categories color-fn & {:keys [x y title]}]
  [:g {:font-family "sans-serif" :font-size 10}
   (when title [:text {:x x :y (- y 5) :fill "#333" :font-size 9} (fmt-name title)])
   (for [[i cat] (map-indexed vector categories)]
     [:g [:circle {:cx x :cy (+ y (* i 16)) :r 4 :fill (color-fn cat)}]
      [:text {:x (+ x 10) :y (+ y (* i 16) 4) :fill "#333"} (str cat)]])])

;; ### Panel Renderer
;;
;; The key architectural change: domains come from stats, not data columns.

(defn- pad-domain
  "Add padding to a numeric domain. For log scales, use multiplicative padding."
  [[lo hi] scale-spec]
  (if (= :log (:type scale-spec))
    ;; Multiplicative: shrink/expand by 5% in log space
    (let [log-lo (Math/log lo) log-hi (Math/log hi)
          pad (* 0.05 (max 1e-6 (- log-hi log-lo)))]
      [(Math/exp (- log-lo pad)) (Math/exp (+ log-hi pad))])
    ;; Additive
    (let [pad (* 0.05 (max 1e-6 (- hi lo)))]
      [(- lo pad) (+ hi pad)])))

(defn render-panel
  [panel-views pw ph m & {:keys [x-domain y-domain show-x? show-y? all-colors
                                 tooltip-fn shape-categories]
                          :or {show-x? true show-y? true}}]
  (let [v1 (first panel-views)
        coord-type (or (:coord v1) :cartesian)
        x-scale-spec (or (:x-scale v1) {:type :linear})
        y-scale-spec (or (:y-scale v1) {:type :linear})
        polar? (= coord-type :polar)

        ;; Step 1: compute stats for all views → get stat-driven domains
        view-stats (for [v panel-views
                         :when (#{:point :bar :line :rect :text} (:mark v))]
                     (let [stat (compute-stat v)]
                       {:view v :stat stat}))

        ;; Step 2: merge domains from stats (not from raw data columns!)
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

        ;; Adjust y-domain for stacked bars: max is sum of all groups per category
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
        ;; For flip, swap domains so sx maps y-data, sy maps x-data
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

        ;; Annotation views (not data-driven)
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
                                 ;; Count stat output → categorical bars
                                 [(render-categorical-bars stat coord sx all-colors
                                                           :position (or (:position view) :dodge))]
                                 ;; Identity stat output → pre-aggregated value bars
                                 [(render-value-bars stat coord sx all-colors
                                                     :position (or (:position view) :dodge))])
                         :line [(render-lines stat coord all-colors)]
                         :text [(render-text stat coord all-colors)]
                         [(render-points stat coord all-colors
                                         :tooltip-fn tooltip-fn
                                         :shape-categories shape-categories)])))
                   view-stats))

     ;; Tick labels (skip for polar — just grid)
     (when (and show-x? (not polar?))
       (if cat-x?
         (render-x-ticks-bands sx pw ph m)
         (render-x-ticks sx pw ph m)))
     (when (and show-y? (not polar?))
       (render-y-ticks sy pw ph m))]))

;; ### The Plot Function

(defn plot
  "Render views as SVG. Options: :width :height :margin :scales :coord :tooltip :brush"
  [views & {:keys [width height margin scales coord tooltip brush]
            :or {width 600 height 400 margin 40}}]
  (let [views (if (map? views) [views] views)
        views (if coord (mapv #(assoc % :coord coord) views) views)
        ;; Collect facet/aesthetic info
        facet-vals (distinct (remove nil? (map :facet-val views)))
        facet-row-vals (distinct (remove nil? (map :facet-row views)))
        facet-col-vals (distinct (remove nil? (map :facet-col views)))
        grid-facet? (and (seq facet-row-vals) (seq facet-col-vals))
        multi-facet? (seq facet-vals)
        x-vars (distinct (map :x views))
        y-vars (distinct (map :y views))
        cols (cond grid-facet? (count facet-col-vals)
                   multi-facet? (count facet-vals)
                   :else (count x-vars))
        rows (cond grid-facet? (count facet-row-vals)
                   multi-facet? 1
                   :else (count y-vars))
        m margin
        pw (/ (- width (* 2 m)) cols)
        ph (/ (- height (* 2 m)) rows)
        ;; Compute stats + domains
        data-views (mapv compute-stat views)
        all-colors (let [cs (distinct (mapcat :color-order data-views))]
                     (when (seq cs) (vec cs)))
        color-cols (distinct (remove nil? (map :color views)))
        shape-col (first (remove nil? (map :shape views)))
        shape-categories (when shape-col
                           (distinct (mapcat (fn [v] (map #(get % shape-col) (-> v :data tc/rows)))
                                             views)))
        polar? (= :polar (:coord (first views)))
        tooltip-fn (when tooltip
                     (fn [row view]
                       (str "<title>"
                            (clojure.string/join ", "
                                                 (map (fn [[k v]] (str (name k) ": " v))
                                                      (select-keys row (remove nil? [(:x view) (:y view) (:color view)]))))
                            "</title>")))
        ;; Scales control
        scale-mode (or scales :shared)
        global-x-doms (when (#{:shared :free-y} scale-mode)
                        (let [xd (mapcat (fn [dv] (let [d (:x-domain dv)]
                                                    (if (and (= 2 (count d)) (number? (first d)))
                                                      d (map str d)))) data-views)]
                          (if (number? (first xd))
                            (pad-domain [(reduce min xd) (reduce max xd)] {:type :linear})
                            (vec (distinct xd)))))
        global-y-doms (when (#{:shared :free-x} scale-mode)
                        (let [;; Check for stacked bars — sum counts per category across colors
                              has-stacked? (some #(= :stack (:position %)) views)
                              yd (if has-stacked?
                                    ;; For stacked: sum all counts per category, find max
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
         ;; Shape legend (below color legend)
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
           ;; Grid faceting: row × column layout
           grid-facet?
           (for [[ri rv] (map-indexed vector facet-row-vals)
                 [ci cv] (map-indexed vector facet-col-vals)
                 :let [panel-views (filter #(and (= rv (:facet-row %))
                                                 (= cv (:facet-col %))) views)]]
             (when (seq panel-views)
               [:g {:transform (str "translate(" (* ci pw) "," (* ri ph) ")")}
                (render-panel panel-views pw ph m
                              :show-x? (= ri (dec rows))
                              :show-y? (zero? ci)
                              :all-colors all-colors
                              :x-domain global-x-doms :y-domain global-y-doms
                              :tooltip-fn tooltip-fn
                              :shape-categories shape-categories)
                ;; Column headers (top row only)
                (when (zero? ri)
                  [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
                          :font-size 10 :fill "#333"} (str cv)])
                ;; Row headers (right side, last column only)
                (when (= ci (dec cols))
                  [:text {:x (- pw 5) :y (/ ph 2) :text-anchor "end"
                          :font-size 10 :fill "#333"
                          :transform (str "rotate(-90," (- pw 5) "," (/ ph 2) ")")}
                   (str rv)])]))
           ;; Simple faceting: one row of panels
           multi-facet?
           (for [[ci fv] (map-indexed vector facet-vals)
                 :let [fviews (filter #(= fv (:facet-val %)) views)]]
             [:g {:transform (str "translate(" (* ci pw) ",0)")}
              (render-panel fviews pw ph m
                            :show-x? true :show-y? (zero? ci)
                            :all-colors all-colors
                            :x-domain global-x-doms :y-domain global-y-doms
                            :tooltip-fn tooltip-fn
                            :shape-categories shape-categories)
              [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
                      :font-size 10 :fill "#333"} (str fv)]])
           ;; Default: grid by (x-var, y-var) pairs
           :else
           (for [[ri yv] (map-indexed vector y-vars)
                 [ci xv] (map-indexed vector x-vars)
                 :let [panel-views (filter #(and (= xv (:x %)) (= yv (:y %))) views)]]
             (when (seq panel-views)
               [:g {:transform (str "translate(" (* ci pw) "," (* ri ph) ")")}
                (render-panel panel-views pw ph m
                              :show-x? (= ri (dec rows))
                              :show-y? (zero? ci)
                              :all-colors all-colors
                              :x-domain global-x-doms :y-domain global-y-doms
                              :tooltip-fn tooltip-fn
                              :shape-categories shape-categories)
                ;; Column headers (top row) — skip for polar
                (when (and (zero? ri) (not polar?))
                  [:text {:x (/ pw 2) :y 12 :text-anchor "middle"
                          :font-size 9 :fill "#333"} (fmt-name xv)])
                ;; Row headers (right side) — skip for polar
                (when (and (= ci (dec cols)) (not polar?))
                  [:text {:x (- pw 5) :y (/ ph 2) :text-anchor "end"
                          :font-size 9 :fill "#333"
                          :transform (str "rotate(-90," (- pw 5) "," (/ ph 2) ")")}
                   (fmt-name yv)])])))]]
    (if brush
      ;; Brushing: wrap in HTML with inline JS
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

;; ## Part 6: Examples

;; ### 1. Basic scatter

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
;;
;; The key test: with stat-driven domains, the y-axis should range
;; over counts (0 to ~28), not data values (4.3 to 7.9).

(-> (views iris [[:sepal-length :sepal-length]])
    (layer (histogram))
    plot)

;; ### 5. Colored histogram

(-> (views iris [[:sepal-length :sepal-length]])
    (layer (histogram :color :species))
    plot)

;; ### 6. SPLOM

(-> (views iris (cross iris-cols iris-cols))
    auto
    (layer :color :species)
    plot)

;; ### 7. Flipped histogram
;;
;; The second key test: flip should produce horizontal bars,
;; different from the vertical histogram above.

(-> (views iris [[:sepal-length :sepal-length]])
    (layer (histogram))
    (set-coord :flip)
    plot)

;; ### 8. Categorical bar chart

(-> (views iris [[:species :species]])
    (layer (bar))
    plot)

;; ### 9. Colored categorical bar chart

(-> (views iris [[:species :species]])
    (layer (bar :color :species))
    plot)

;; ### 10. Log scale
;;
;; With wadogo, log ticks show proper values (1, 10, 100...)
;; instead of transformed values (0.0, 2.3, 4.6...).

(let [data (tc/dataset {:x (mapv #(Math/pow 10 %) (range 0.0 3.01 0.1))
                        :y (mapv #(+ % (* 0.5 (rand))) (range 0.0 3.01 0.1))})]
  (-> (views data [[:x :y]])
      (layer (point))
      (set-scale :x :log)
      plot))

;; ### 11. Faceted scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (facet :species)
    plot)

;; ### 12. Polar scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (set-coord :polar)
    plot)

;; ### 13. Bubble chart (size aesthetic)

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species :size :petal-length))
    plot)

;; ### 14. Shape aesthetic

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species :shape :species))
    plot)

;; ### 15. Annotations: reference line and band

(-> (views iris [[:sepal-length :sepal-width]])
    (layers (point :color :species)
            (hline 3.0)
            (hband 2.5 3.5)
            (vline 6.0))
    plot)

;; ### 16. Text labels at group means

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

;; ### 17. Faceted scatter with free y-scale

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (facet :species)
    (plot :scales :free-y))

;; ### 18. Tooltips on hover

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (plot :tooltip true))

;; ### 19. Brushable scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer (point :color :species))
    (plot :brush true))

;; ### 20. Smooth curve (loess)

(-> (views iris [[:sepal-length :petal-length]])
    (layers (point :color :species)
            (smooth :color :species))
    plot)

;; ### 21. Missing data tolerance

(let [data (tc/dataset {:x [1 2 nil 4 5 6 nil 8]
                        :y [2 4 6 nil 10 12 14 16]})]
  (-> (views data [[:x :y]])
      (layer (point))
      plot))

;; ### 22. Strip plot (categorical x, continuous y)

(-> (views iris [[:species :sepal-length]])
    (layer (point :color :species))
    plot)

;; ### 23. Stacked bar chart

(-> (views mpg [[:class :class]])
    (layer (stacked-bar :color :drv))
    plot)

;; ### 24. Row × column faceting

(let [tips (tc/dataset "https://raw.githubusercontent.com/mwaskom/seaborn-data/master/tips.csv")]
  (-> (views tips [["total_bill" "tip"]])
      (layer (point :color "day"))
      (facet-grid "smoker" "sex")
      (plot :width 600 :height 500)))

;; ### 25. Numeric-as-categorical

(-> (views mpg [[:cyl :cyl]])
    (layer (bar :x-type :categorical))
    plot)
;; ---

;; ## Part 7: Reflection
;;
;; ### What this PoC demonstrates
;;
;; **Stat-driven domains fix histograms.** By having `compute-stat :bin`
;; return `{:y-domain [0 28]}`, the y-axis naturally scales to counts.
;; The previous architecture computed y-domain from data column values,
;; which was wrong for any stat that produces new dimensions.
;;
;; **Wadogo eliminates hand-rolled scale code.** Our `linear-scale`,
;; `nice-ticks`, `categorical-scale`, `apply-scale-fn`, and `inv-scale-label`
;; are all replaced by `(ws/scale ...)`, `(ws/ticks ...)`, `(ws/format ...)`.
;; Log scales get proper tick placement for free. Band scales get
;; proper padding for free. When we need datetime, it's one keyword away.
;;
;; **Single coord function eliminates M×N.** Instead of `make-projection`
;; returning 8 functions per coord type (point, rect, segment, hline, ...),
;; there's one function `[x y] → [px py]`. All geoms decompose to points
;; before calling it. For bars, we project 4 corners → polygon.
;;
;; ### What's still rough
;;
;; **Flip domain swap.** We still swap x/y domains for flip at the scale
;; construction level. The coord function `(fn [dx dy] [(sx dy) (sy dx)])`
;; then swaps the arguments. This double-swap works but is subtle.
;;
;; **Categorical bars + coord.** The categorical bar renderer (`render-categorical-bars`)
;; still has some coord-specific logic because band scales produce pixel
;; positions directly. This could be cleaner.
;;
;; **Polar axis labels.** Deliberately omitted — just the grid. Proper polar
;; axis labels need per-coord rendering, which is the right trade-off: keep
;; the data pipeline coord-agnostic, accept that axis *decoration* is
;; coord-specific.
;;
;; **Polar bars (pie charts).** The 4-corner polygon approach works for
;; cartesian/flip but produces distorted shapes in polar. Proper polar
;; bars need arc-segment subdivision ("munching"). Deferred.

;;
