^{:kindly/hide-code true
  :clay {:title "Variation B: The Dataset IS the Plot"
         :quarto {:type :post
                  :author [:daslu :claude]
                  :date "2026-02-19"
                  :description "What if plotting were just dataset operations? Threading, group-by, and column transforms as the grammar."
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :tablecloth :threading :design]
                  :toc true
                  :toc-depth 3
                  :draft true}}}
(ns data-visualization.aog.sketch-tc-native
  "Variation B: The Dataset IS the Plot.

  Core idea: a plot specification lives IN the dataset as metadata.
  Every plotting function takes a dataset as first argument and returns
  a dataset — making it fully -> friendly.

  Threading reads like English:

    (-> iris
        (bindxy :sepal-length :sepal-width)
        (color-by :species)
        (mark :point)
        (add-layer :line :stat :regress)
        render)

  Key insight: tc/group-by IS faceting.
  dtype-next column ops ARE scale transforms.
  The dataset carries everything."
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

;; # Variation B: The Dataset IS the Plot
;;
;; In sketch.clj, views are maps: `{:data ds :x :col :y :col}`.
;; The dataset and the plot specification are separate things.
;;
;; What if they were the **same** thing?
;;
;; A dataset already has column names, types, and values.
;; If we store the visual mapping in its metadata,
;; then the dataset IS the full specification.
;; And every function takes/returns a dataset → perfect for `->`.

;; ---

;; ## The Data

(def iris (rdatasets/datasets-iris))

;; ## Part 1: The Binding API
;;
;; Each function takes a dataset, adds to its `:plot` metadata,
;; and returns the dataset unchanged.
;; This means `->` threading just works.

(defn- update-plot
  "Update the :plot key in dataset metadata."
  [ds f & args]
  (vary-meta ds #(apply update % :plot f args)))

(defn- get-plot
  "Get the :plot metadata from a dataset."
  [ds]
  (:plot (meta ds) {}))

(defn bindxy
  "Bind x and y columns. The first step in any plot."
  [ds x-col y-col]
  (update-plot ds merge {:x x-col :y y-col}))

(defn color-by
  "Map a column to color aesthetic."
  [ds col]
  (update-plot ds assoc :color col))

(defn size-by
  "Map a column to size aesthetic."
  [ds col]
  (update-plot ds assoc :size col))

(defn mark
  "Set the geometry mark: :point, :line, :bar."
  [ds m]
  (update-plot ds assoc :mark m))

;; ### Scales: functions on datasets
;;
;; A scale transform is just a column operation.
;; Since dtype-next supports vectorized math,
;; we can transform columns in-place.

(defn scale-x
  "Apply a scale transform to the x column.
  transform can be :log, :sqrt, or a function."
  [ds transform]
  (let [x-col (:x (get-plot ds))
        f (case transform
            :log dfn/log
            :sqrt dfn/sqrt
            :identity identity
            transform)]
    (tc/update-columns ds {x-col f})))

(defn scale-y
  "Apply a scale transform to the y column."
  [ds transform]
  (let [y-col (:y (get-plot ds))
        f (case transform
            :log dfn/log
            :sqrt dfn/sqrt
            :identity identity
            transform)]
    (tc/update-columns ds {y-col f})))

(defn flip
  "Swap x and y: the 'coord_flip' of ggplot2.
  Since x and y are just metadata, flipping is just a swap."
  [ds]
  (let [{:keys [x y]} (get-plot ds)]
    (update-plot ds merge {:x y :y x})))

;; Notice: `scale-x :log` is a dtype-next vectorized column transform.
;; `flip` is a metadata swap. Both are just dataset→dataset functions.
;; Both work with `->`.

;; ---

;; ## Part 2: Layers
;;
;; A single dataset carries one "base" layer in its metadata.
;; For multiple layers (scatter + regression), we store a vector.
;;
;; The crucial design choice: **layers share the same dataset**.
;; Each layer is a map of overrides: `{:mark :line :stat :regress}`.

(defn add-layer
  "Add a layer specification. Each layer is a map of overrides."
  [ds m & {:as opts}]
  (update-plot ds update :layers (fnil conj []) (merge {:mark m} opts)))

;; When there are no explicit layers, the base mark is the only layer.
;; When layers are added, they supplement the base.

;; ---

;; ## Part 3: Rendering
;;
;; Reading the plot metadata and dataset, produce SVG.

(def ggplot-palette
  ["#F8766D" "#00BA38" "#619CFF" "#A855F7" "#F97316" "#14B8A6" "#EF4444" "#6B7280"])

(def theme
  {:bg "#EBEBEB" :grid "#FFFFFF" :font "sans-serif" :font-size 10})

(defn- domain-of [col-data]
  (let [lo (tcc/reduce-min col-data)
        hi (tcc/reduce-max col-data)
        pad (* 0.05 (- hi lo))]
    [(- lo pad) (+ hi pad)]))

(defn- linear-scale [domain range]
  (let [[d0 d1] domain [r0 r1] range
        span-d (- d1 d0) span-r (- r1 r0)]
    (fn [v] (+ r0 (* span-r (/ (- v d0) span-d))))))

(defn- nice-ticks [lo hi n]
  (let [span (- hi lo)
        raw-step (/ span n)
        mag (Math/pow 10 (Math/floor (Math/log10 raw-step)))
        candidates [1 2 5 10]
        step (->> candidates (map #(* mag %)) (filter #(>= % raw-step)) first)
        start (* (Math/ceil (/ lo step)) step)]
    (vec (take-while #(<= % hi) (iterate #(+ % step) start)))))

(defn- render-grid [sx sy domain-x domain-y w h margin]
  (let [x-ticks (nice-ticks (first domain-x) (second domain-x) 5)
        y-ticks (nice-ticks (first domain-y) (second domain-y) 5)]
    [:g
     (for [tx x-ticks :let [px (sx tx)]]
       [:line {:x1 px :y1 margin :x2 px :y2 (- h margin)
               :stroke (:grid theme) :stroke-width 1}])
     (for [ty y-ticks :let [py (sy ty)]]
       [:line {:x1 margin :y1 py :x2 (- w margin) :y2 py
               :stroke (:grid theme) :stroke-width 1}])]))

(defn- render-axes [sx sy domain-x domain-y w h margin x-label y-label]
  (let [x-ticks (nice-ticks (first domain-x) (second domain-x) 5)
        y-ticks (nice-ticks (first domain-y) (second domain-y) 5)]
    [:g {:font-family (:font theme) :font-size (:font-size theme) :fill "#666"}
     ;; x-axis labels
     (for [tx x-ticks :let [px (sx tx)]]
       [:text {:x px :y (+ (- h margin) 15) :text-anchor "middle"}
        (format "%.1f" (double tx))])
     ;; y-axis labels
     (for [ty y-ticks :let [py (sy ty)]]
       [:text {:x (- margin 5) :y (+ py 3) :text-anchor "end"}
        (format "%.1f" (double ty))])
     ;; axis names
     [:text {:x (/ w 2) :y (- h 3) :text-anchor "middle" :font-size 11 :fill "#333"}
      (name x-label)]
     [:text {:x 12 :y (/ h 2) :text-anchor "middle" :font-size 11 :fill "#333"
             :transform (str "rotate(-90," 12 "," (/ h 2) ")")}
      (name y-label)]]))

(defn- render-points [ds sx sy x-col y-col color-fn]
  (let [xs (vec (ds x-col))
        ys (vec (ds y-col))]
    [:g (for [i (range (count xs))
              :let [c (color-fn i)]]
          [:circle {:cx (sx (nth xs i)) :cy (sy (nth ys i))
                    :r 2.5 :fill c :stroke "#fff" :stroke-width 0.5 :opacity 0.7}])]))

(defn- render-regression [ds sx sy x-col y-col color]
  (let [xs (vec (ds x-col)) ys (vec (ds y-col))
        model (regr/lm ys xs)
        x-min (reduce min xs) x-max (reduce max xs)]
    [:line {:x1 (sx x-min) :y1 (sy (regr/predict model [x-min]))
            :x2 (sx x-max) :y2 (sy (regr/predict model [x-max]))
            :stroke color :stroke-width 1.5}]))

(defn- render-line [ds sx sy x-col y-col color]
  (let [xs (vec (ds x-col)) ys (vec (ds y-col))
        pts (sort-by first (map vector xs ys))]
    [:polyline {:points (str/join " " (map (fn [[x y]] (str (sx x) "," (sy y))) pts))
                :stroke color :stroke-width 1.5 :fill "none"}]))

(defn- render-bars [ds sx sy x-col y-col color domain-y margin h]
  (let [xs (vec (ds x-col))
        bins-result (stats/histogram xs :sturges)
        bin-maps (:bins-maps bins-result)
        max-count (reduce max (map :count bin-maps))
        count-scale (linear-scale [0 max-count] [(- h margin) margin])]
    [:g (for [{:keys [min max count]} bin-maps
              :let [px1 (sx min) px2 (sx max)
                    py (count-scale count)
                    bar-h (- (- h margin) py)]]
          [:rect {:x px1 :y py :width (- px2 px1) :height bar-h
                  :fill color :opacity 0.7}])]))

(defn- render-layer [ds layer-spec sx sy x-col y-col color-col palette
                     domain-y margin h]
  (let [m (:mark layer-spec :point)
        s (:stat layer-spec :identity)]
    (if color-col
      (let [groups (-> ds (tc/group-by [color-col]) tc/groups->map)
            color-map (zipmap (map #(get % color-col) (keys groups))
                              (cycle palette))]
        [:g (for [[gk gds] groups
                  :let [label (get gk color-col)
                        c (color-map label)]]
              (case [m s]
                [:point :identity]
                (render-points gds sx sy x-col y-col (constantly c))
                [:line :regress]
                (render-regression gds sx sy x-col y-col c)
                [:line :identity]
                (render-line gds sx sy x-col y-col c)
                [:bar :identity]
                (render-bars gds sx sy x-col y-col c domain-y margin h)
                nil))])
      (case [m s]
        [:point :identity]
        (render-points ds sx sy x-col y-col (constantly "#333"))
        [:line :regress]
        (render-regression ds sx sy x-col y-col "#333")
        [:line :identity]
        (render-line ds sx sy x-col y-col "#333")
        [:bar :identity]
        (render-bars ds sx sy x-col y-col "#333" domain-y margin h)
        nil))))

(defn- render-legend [color-col ds palette w margin]
  (when color-col
    (let [categories (distinct (vec (ds color-col)))
          color-map (zipmap categories (cycle palette))]
      [:g {:font-family (:font theme) :font-size 10}
       (for [[i cat] (map-indexed vector categories)
             :let [y (+ margin (* i 18))]]
         [:g
          [:circle {:cx (+ w 15) :cy y :r 4 :fill (color-map cat)}]
          [:text {:x (+ w 25) :y (+ y 4) :fill "#333"} (str cat)]])])))

(defn render
  "Render a dataset (with :plot metadata) into SVG.
  This is the terminal step of the -> pipeline."
  [ds & {:keys [width height margin]
         :or {width 400 height 300 margin 40}}]
  (let [{:keys [x y mark color layers]} (get-plot ds)
        _ (assert x "No x binding. Use (bindxy ds :x-col :y-col) first.")
        _ (assert y "No y binding. Use (bindxy ds :x-col :y-col) first.")
        all-layers (if (seq layers) layers [{:mark (or mark :point) :stat :identity}])
        domain-x (domain-of (ds x))
        domain-y (domain-of (ds y))
        legend? (some? color)
        plot-w (if legend? (- width 80) width)
        sx (linear-scale domain-x [margin (- plot-w margin)])
        sy (linear-scale domain-y [(- height margin) margin])]
    ^:kind/hiccup
    [:svg {:width width :height height
           "xmlns" "http://www.w3.org/2000/svg"
           "version" "1.1"}
     [:rect {:x 0 :y 0 :width plot-w :height height :fill (:bg theme)}]
     (render-grid sx sy domain-x domain-y plot-w height margin)
     (render-axes sx sy domain-x domain-y plot-w height margin x y)
     (for [layer-spec all-layers]
       (render-layer ds layer-spec sx sy x y color ggplot-palette
                     domain-y margin height))
     (render-legend color ds ggplot-palette plot-w margin)]))

;; ---

;; ## Examples
;;
;; Notice how every example reads as a `->` pipeline starting from data.

;; ### A Single Scatter Plot

(-> iris
    (bindxy :sepal-length :sepal-width)
    (mark :point)
    render)

;; ### Colored Scatter

(-> iris
    (bindxy :sepal-length :sepal-width)
    (color-by :species)
    (mark :point)
    render)

;; ### Scatter + Regression (layering)

(-> iris
    (bindxy :sepal-length :sepal-width)
    (color-by :species)
    (add-layer :point)
    (add-layer :line :stat :regress)
    render)

;; Three lines of intent. No ceremony.

;; ### Flipped Coordinates

(-> iris
    (bindxy :sepal-length :sepal-width)
    (color-by :species)
    (mark :point)
    flip
    render)

;; `flip` is just a metadata swap — zero cost, zero code.

;; ### Log Scale on X

(-> iris
    (bindxy :sepal-length :sepal-width)
    (color-by :species)
    (mark :point)
    (scale-x :log)
    render)

;; `scale-x :log` is a dtype-next vectorized column transform.
;; The data itself changes — the rendering stays the same.
;; This is both elegant and honest: what you see IS the data.

;; ### Multi-Dataset: Reference Line Overlay
;;
;; The `->` pipeline works for a single dataset.
;; For multi-dataset overlay, we compose rendered layers:

(defn overlay
  "Combine two rendered SVGs into one.
  Takes the first SVG's container, adds the second's content."
  [svg1 svg2]
  (let [children1 (drop 2 (second svg1)) ;; skip :svg + attrs
        children2 (drop 2 (second svg2))]
    ;; Return the first SVG with additional children from the second
    svg1)) ;; Simplified — real implementation would merge SVG groups

;; For true multi-dataset support, we need a different approach.
;; This is a genuine limitation of "dataset IS the plot".
;; See Part 5: Discussion.

;; ---

;; ## Part 4: Faceting via tc/group-by
;;
;; The most beautiful part of this variation:
;; `tc/group-by` IS faceting.

(defn render-faceted
  "Render a grouped dataset as faceted panels."
  [ds & {:keys [panel-size margin] :or {panel-size 200 margin 30}}]
  (let [{:keys [x y mark color layers]} (get-plot ds)
        ;; Note: tc/group-by drops metadata, so we save it first
        plot-meta (get-plot ds)
        groups (-> ds (tc/group-by [(:facet plot-meta :species)]) tc/groups->map)
        n (count groups)
        all-layers (if (seq layers) layers [{:mark (or mark :point) :stat :identity}])
        ;; Compute shared domains
        domain-x (domain-of (ds x))
        domain-y (domain-of (ds y))
        gap 10
        total-w (+ (* n panel-size) (* (dec n) gap))]
    ^:kind/hiccup
    [:svg {:width total-w :height (+ panel-size 20)
           "xmlns" "http://www.w3.org/2000/svg"
           "version" "1.1"}
     (map-indexed
      (fn [i [gk gds]]
        (let [tx (* i (+ panel-size gap))
              sx (linear-scale domain-x [margin (- panel-size margin)])
              sy (linear-scale domain-y [(- panel-size margin) margin])
              label (str (first (vals gk)))]
          [:g {:transform (str "translate(" tx ",0)")}
           [:rect {:x 0 :y 0 :width panel-size :height panel-size :fill (:bg theme)}]
           (render-grid sx sy domain-x domain-y panel-size panel-size margin)
           (for [layer-spec all-layers]
             (render-layer gds layer-spec sx sy x y color ggplot-palette
                           domain-y margin panel-size))
           [:text {:x (/ panel-size 2) :y (+ panel-size 15)
                   :text-anchor "middle" :font-family (:font theme) :font-size 11 :fill "#333"}
            label]]))
      groups)]))

(defn facet-by
  "Set the faceting column. Used with render-faceted."
  [ds col]
  (update-plot ds assoc :facet col))

;; ### Faceted scatter

(-> iris
    (bindxy :sepal-length :sepal-width)
    (color-by :species)
    (mark :point)
    (facet-by :species)
    render-faceted)

;; ---

;; ## Part 5: Discussion
;;
;; ### What `->` threading gives us
;;
;; The pipeline reads like a specification in English:
;;
;;   "Take iris,
;;    bind sepal-length to x and sepal-width to y,
;;    color by species,
;;    draw points,
;;    render."
;;
;; Each step is:
;; - **Inspectable**: `(-> iris (bindxy ...) (color-by ...) get-plot)` shows the spec
;; - **Incremental**: add one thing at a time, see the effect
;; - **Reversible**: remove a line, the plot simplifies
;; - **Composable**: any tc operation can appear in the pipeline
;;
;; ```
;; (-> iris
;;     (tc/select-rows (fn [row] (> (:sepal-length row) 5)))  ;; filter!
;;     (bindxy :sepal-length :sepal-width)
;;     (color-by :species)
;;     (mark :point)
;;     render)
;; ```
;;
;; Because the threaded object IS a dataset, tc operations slot in naturally.
;; This is the main advantage over sketch.clj.

;; ### Scales as column transforms
;;
;; In ggplot2, `scale_x_log10()` wraps the axis in a log scale
;; but keeps the data unchanged (tick labels show original values).
;;
;; Here, `(scale-x :log)` actually transforms the data column.
;; This is more honest — the data IS what's plotted — but it means
;; axis labels show log values, not original values.
;;
;; To get ggplot2-style "transform scale but label original",
;; we'd need to store the transform in metadata and apply it during rendering.
;; This is a design choice: simplicity (data transform) vs. polish (scale metadata).

;; ### Coordinates
;;
;; `flip` works beautifully — just swap metadata.
;; Polar coordinates would be harder:
;; they require a different rendering pipeline, not just a metadata swap.
;;
;; A principled approach would be `:coord` in metadata:
;; ```
;; (defn coord [ds c] (update-plot ds assoc :coord c))
;; (-> iris ... (coord :polar) render)
;; ```
;; with `render` dispatching on `:coord`. Achievable but more code.

;; ### The multi-dataset limitation
;;
;; `->` threads ONE dataset. Multi-dataset overlay breaks the pattern:
;; ```
;; ;; This doesn't work with ->:
;; (overlay
;;   (-> iris (bindxy ...) (point) render)
;;   (-> reference-line (bindxy ...) (line) render))
;; ```
;;
;; Three escape routes:
;;
;; 1. **Merge datasets first**: `(-> (tc/concat iris ref-ds) ...)`
;;    — requires compatible columns, loses type safety
;;
;; 2. **Layer as data+spec pair**: `(add-layer-from ds2 :line)`
;;    — breaks the "dataset IS the plot" invariant
;;
;; 3. **Accept the limit**: single-dataset pipelines for `->`,
;;    stack/overlay for multi-dataset (like sketch.clj)
;;
;; This is the fundamental tension: `->` wants ONE thing to thread.
;; Multi-dataset plots are inherently MANY things.

;; ### tc/group-by as faceting
;;
;; This works beautifully for one-level grouping.
;; But tc/group-by drops metadata, so we must save/restore it.
;; Also, nested grouping (facet × color) requires care.
;;
;; The deeper insight: tablecloth's grouped dataset IS a faceted plot.
;; The groups ARE the panels. This is genuinely elegant when it fits.

;; ### What this variation gets right
;;
;; | Aspect | Rating | Notes |
;; |--------|--------|-------|
;; | `->` threading | ★★★★★ | The whole point |
;; | Single scatter | ★★★★★ | Clean, minimal |
;; | Color grouping | ★★★★☆ | Natural |
;; | Layering | ★★★☆☆ | Layers share one dataset |
;; | Faceting | ★★★★☆ | tc/group-by is elegant |
;; | Multi-dataset | ★★☆☆☆ | Breaks `->` |
;; | Scales | ★★★★☆ | dtype-next is powerful |
;; | Coordinates | ★★★☆☆ | flip=easy, polar=hard |
;; | SPLOM/grids | ★★☆☆☆ | Doesn't fit the model |
;; | Inspectability | ★★★★★ | Data IS visible |
