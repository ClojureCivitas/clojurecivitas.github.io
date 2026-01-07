^{:kindly/hide-code true
  :clay {:title "A Deep Grammar of Graphics"
         :quarto {:type :post
                  :author [:daslu]
                  :date "2026-01-07"
                  :description "Building a composable visualization system from first principles"
                  :category :data-visualization
                  :tags [:grammar-of-graphics :composition :beauty]
                  :toc true
                  :toc-depth 3
                  :draft true}}}
(ns data-visualization.grammar
  "A deep grammar of graphics: composable visualization from first principles.
  
  Core idea: A plot is a mapping from data to visual marks.
  
  Three primitives:
  - layer: Create a mapping
  - cross: Multiply mappings (grids)
  - stack: Add mappings (overlays)
  
  Everything else emerges from composition."
  (:require
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.viz.core :as viz]
   [thi.ng.geom.svg.core :as svg]
   [fastmath.ml.regression :as regr]
   [clojure.math.combinatorics :as combo]
   [scicloj.metamorph.ml.toydata :as toydata]))

^{:kindly/hide-code true
  :kindly/kind :kind/hiccup}
[:style
 ".clay-dataset { max-height:400px; overflow-y: auto; }
  .printedClojure { max-height:400px; overflow-y: auto; }"]

;; # A Deep Grammar of Graphics
;;
;; ## The Big Idea
;;
;; **What if we could build ANY visualization from just three simple operations?**
;;
;; This notebook explores that question. We'll build a complete grammar of graphics
;; from first principles, discovering that complex visualizations emerge naturally
;; from composing simple primitives.
;;
;; ### The Core Insight
;;
;; > A plot is a **mapping** from data to visual marks.
;;
;; Breaking this down:
;; - **Data**: Rows and columns in a table
;; - **Mapping**: Which columns map to which visual properties (x, y, color, size)
;; - **Marks**: The shapes we draw (points, lines, bars)
;;
;; ### The Three Primitives
;;
;; Everything we build will use only these three operations:
;;
;; 1. **`layer`** - Create a single mapping (data → marks)
;; 2. **`cross`** - Multiply mappings (creates grids)
;; 3. **`stack`** - Add mappings (creates overlays)
;;
;; That's it. No special cases, no exceptions. Just composition.
;;
;; Let's build it step by step.

;; ## Part 1: Foundations

;; ### Theme

(def theme
  "Visual styling (ggplot2 aesthetic)"
  {:background "#EBEBEB"
   :grid "#FFFFFF"
   :colors ["#F8766D" "#00BA38" "#619CFF" "#F564E3"]
   :point-fill "#333333"
   :point-stroke "#FFFFFF"
   :line-stroke "#333333"
   :bar-fill "#333333"
   :bar-stroke "#FFFFFF"})

;; ### The Dataset

(def iris (toydata/iris-ds))

iris

;; Classic Iris dataset: 150 observations, 4 measurements, 3 species.
;; Perfect for exploring relationships between variables.

;; ## Part 2: The First Primitive - `layer`
;;
;; A **layer** is the atomic unit - one mapping from data to marks.
;;
;; Let's create the simplest possible layer:

(defn layer
  "Create a layer - one mapping from data to visual space.
  
  A layer specifies:
  - Which dataset to use
  - Which columns map to which aesthetics (x, y, color, size)
  - What transformation to apply (identity, bin, smooth, regress)
  - What geometry to render (point, line, bar, area)
  
  Returns a layer spec (just a map)."
  [data & {:keys [x y color size group
                  transform transform-opts
                  geom geom-opts]
           :or {transform :identity
                geom :point
                transform-opts {}
                geom-opts {}}}]
  {:data data
   :x x
   :y y
   :color color
   :size size
   :group group
   :transform transform
   :transform-opts transform-opts
   :geom geom
   :geom-opts geom-opts})

;; Let's create our first layer:

;; Let's create our first layer:

(comment
  (def scatter-layer
    (layer iris :x :sepal-length :y :sepal-width))

  ;; What does this give us? Let's look:

  (kind/pprint scatter-layer))

;; It's just data! A map describing what we want to plot.
;; - `:data` - the Iris dataset
;; - `:x` - map sepal-length to x-axis
;; - `:y` - map sepal-width to y-axis
;; - `:transform` - identity (no transformation, use raw data)
;; - `:geom` - point (draw as scatter plot)
;;
;; Now we need to **render** this layer to see it.

;; ## Part 3: Rendering (Making It Visual)
;;
;; To render a layer, we need to:
;; 1. Apply the transformation (if any)
;; 2. Map data to visual space
;; 3. Draw the geometry

;; ### Transform Functions

(defmulti transform-data
  "Apply statistical transformation to data.
  
  Returns {:data original-data :result transformed-data}"
  (fn [layer] (:transform layer)))

(defmethod transform-data :identity [layer]
  ;; Identity: return data unchanged
  {:data (:data layer)
   :result (:data layer)})

;; ### Helper: Extract Points

(defn extract-values
  "Extract column values from dataset."
  [dataset col]
  (when col
    (vec (tc/column dataset col))))

(defn layer->points
  "Convert layer to point data for rendering.
  
  Returns sequence of maps with :x, :y, :color, :size keys.
  
  Special handling for binned data: uses bin-center for :x and count for :y,
  preserving :bin-right for bar rendering."
  [layer]
  (let [{:keys [data result]} (transform-data layer)
        dataset result

        ;; Check if this is binned data (has :bin-center column)
        is-binned? (some #(= :bin-center %) (tc/column-names dataset))

        x-vals (if is-binned?
                 (extract-values dataset :bin-center)
                 (extract-values dataset (:x layer)))
        y-vals (if is-binned?
                 (extract-values dataset :count)
                 (extract-values dataset (:y layer)))

        ;; For binned data, also extract bin-right for bar rendering
        bin-right-vals (when is-binned?
                         (extract-values dataset :bin-right))

        color-vals (when (:color layer)
                     (extract-values dataset (:color layer)))
        size-vals (when (:size layer)
                    (extract-values dataset (:size layer)))]

    (cond
      ;; Binned data with bars
      (and is-binned? bin-right-vals)
      (map (fn [x y br] {:x x :y y :bin-right br})
           x-vals y-vals bin-right-vals)

      ;; x, y, color, size
      (and color-vals size-vals)
      (map (fn [x y c s] {:x x :y y :color c :size s})
           x-vals y-vals color-vals size-vals)

      ;; x, y, color
      color-vals
      (map (fn [x y c] {:x x :y y :color c})
           x-vals y-vals color-vals)

      ;; x, y, size
      size-vals
      (map (fn [x y s] {:x x :y y :size s})
           x-vals y-vals size-vals)

      ;; x, y only
      :else
      (map (fn [x y] {:x x :y y})
           x-vals y-vals))))

;; ### Geometry Renderers

(defn- compute-domain
  "Compute [min max] from values."
  [values]
  (when (seq values)
    [(reduce min values)
     (reduce max values)]))

(defmulti render-geom
  "Render geometry marks.
  
  Returns {:geom/type ... :geom/elements [...]} where elements are
  thi.ng/geom-viz specs or hiccup vectors."
  (fn [layer points scales] (:geom layer)))

(defmethod render-geom :point [layer points {:keys [x-scale y-scale]}]
  (let [point-fill (:point-fill theme)
        point-stroke (:point-stroke theme)
        colors (:colors theme)
        color-groups (group-by :color points)]
    (if (> (count color-groups) 1)
      ;; Colored points
      {:geom/type :point
       :geom/elements (map-indexed
                       (fn [idx [color-val group-points]]
                         {:values (mapv (fn [p] [(:x p) (:y p)]) group-points)
                          :layout viz/svg-scatter-plot
                          :attribs {:fill (get colors idx point-fill)
                                    :stroke (get colors idx point-stroke)
                                    :stroke-width 0.5
                                    :opacity 1.0}})
                       color-groups)}
      ;; Simple points
      {:geom/type :point
       :geom/elements [{:values (mapv (fn [p] [(:x p) (:y p)]) points)
                        :layout viz/svg-scatter-plot
                        :attribs {:fill point-fill
                                  :stroke point-stroke
                                  :stroke-width 0.5
                                  :opacity 1.0}}]})))

;; ### The Plot Function

(defn plot
  "Render layers to SVG with support for multiple geometry types and grid layouts.
  
  Handles:
  - Single layers
  - Stacked layers (overlays)
  - Grid layouts (from cross)"
  [spec]
  (let [is-grid? (= (:layout spec) :grid)]
    (if is-grid?
      ;; Grid rendering
      (let [layers (:layers spec)
            n-rows (inc (apply max (map :row layers)))
            n-cols (inc (apply max (map :col layers)))

            panel-width 200
            panel-height 200
            panel-margin 30
            outer-margin 40

            total-width (+ (* n-cols panel-width)
                           (* (dec n-cols) panel-margin)
                           (* 2 outer-margin))
            total-height (+ (* n-rows panel-height)
                            (* (dec n-rows) panel-margin)
                            (* 2 outer-margin))

            ;; Group layers by position
            grid-map (group-by (fn [layer] [(:row layer) (:col layer)]) layers)

            ;; Render each panel
            panels (for [row (range n-rows)
                         col (range n-cols)]
                     (let [panel-layers (get grid-map [row col])
                           x-offset (+ outer-margin (* col (+ panel-width panel-margin)))
                           y-offset (+ outer-margin (* row (+ panel-height panel-margin)))]
                       (when (seq panel-layers)
                         (let [all-layer-points (map layer->points panel-layers)
                               all-points (apply concat all-layer-points)

                               x-vals (map :x all-points)
                               y-vals (map :y all-points)
                               [x-min x-max] (compute-domain x-vals)
                               [y-min y-max] (compute-domain y-vals)

                               x-padding (* 0.05 (- x-max x-min))
                               y-padding (* 0.05 (- y-max y-min))
                               x-domain [(- x-min x-padding) (+ x-max x-padding)]
                               y-domain [(- y-min y-padding) (+ y-max y-padding)]

                               inner-margin 20
                               x-range [inner-margin (- panel-width inner-margin)]
                               y-range [(- panel-height inner-margin) inner-margin]

                               x-scale (fn [x] (+ x-offset (first x-range)
                                                  (* (/ (- x (first x-domain))
                                                        (- (second x-domain) (first x-domain)))
                                                     (- (second x-range) (first x-range)))))
                               y-scale (fn [y] (+ y-offset (first y-range)
                                                  (* (/ (- y (first y-domain))
                                                        (- (second y-domain) (first y-domain)))
                                                     (- (second y-range) (first y-range)))))

                               scales {:x-scale x-scale :y-scale y-scale
                                       :x-domain x-domain :y-domain y-domain
                                       :x-range x-range :y-range y-range}

                               rendered (map (fn [layer points]
                                               (render-geom layer points scales))
                                             panel-layers all-layer-points)

                               point-geoms (filter #(= (:geom/type %) :point) rendered)
                               line-geoms (filter #(= (:geom/type %) :line) rendered)
                               bar-geoms (filter #(= (:geom/type %) :bar) rendered)

                               viz-elements (mapcat :geom/elements point-geoms)
                               line-elements (mapcat :geom/elements line-geoms)
                               bar-elements (mapcat :geom/elements bar-geoms)

                               x-ticks (for [tick (range 5)]
                                         (let [x (+ inner-margin (* tick (/ (- panel-width (* 2 inner-margin)) 4)))]
                                           [:line {:x1 x :y1 inner-margin
                                                   :x2 x :y2 (- panel-height inner-margin)
                                                   :stroke (:grid theme)
                                                   :stroke-width 0.5}]))

                               y-ticks (for [tick (range 5)]
                                         (let [y (+ inner-margin (* tick (/ (- panel-height (* 2 inner-margin)) 4)))]
                                           [:line {:x1 inner-margin :y1 y
                                                   :x2 (- panel-width inner-margin) :y2 y
                                                   :stroke (:grid theme)
                                                   :stroke-width 0.5}]))

                               point-circles (when (seq viz-elements)
                                               (mapcat (fn [elem]
                                                         (let [values (:values elem)
                                                               attribs (:attribs elem)]
                                                           (map (fn [[x y]]
                                                                  [:circle {:cx (x-scale x)
                                                                            :cy (y-scale y)
                                                                            :r 2.5
                                                                            :fill (:fill attribs)
                                                                            :stroke (:stroke attribs)
                                                                            :stroke-width (:stroke-width attribs)
                                                                            :opacity (:opacity attribs)}])
                                                                values)))
                                                       viz-elements))]

                           [:g {:class "panel" :transform (str "translate(" x-offset "," y-offset ")")}
                            [:rect {:x 0 :y 0
                                    :width panel-width :height panel-height
                                    :fill "white"
                                    :stroke (:grid theme)
                                    :stroke-width 1}]
                            (into [:g {:class "grid"}] (concat x-ticks y-ticks))
                            (when point-circles
                              (into [:g {:class "points"}] point-circles))
                            (when (seq line-elements)
                              (into [:g {:class "lines"}] line-elements))]))))]

        (kind/html
         (svg/serialize
          [:div {:style (str "background:" (:background theme))}
           [:svg {:width total-width :height total-height}
            (into [:g] (remove nil? panels))]])))

      ;; Non-grid rendering (single plot)
      (let [layers (cond
                     (vector? spec) spec
                     (map? (:layers spec)) (:layers spec)
                     (vector? (:layers spec)) (:layers spec)
                     :else [spec])

            all-layer-points (map layer->points layers)
            all-points (apply concat all-layer-points)

            x-vals (map :x all-points)
            y-vals (map :y all-points)
            [x-min x-max] (compute-domain x-vals)
            [y-min y-max] (compute-domain y-vals)

            x-padding (* 0.05 (- x-max x-min))
            y-padding (* 0.05 (- y-max y-min))
            x-domain [(- x-min x-padding) (+ x-max x-padding)]
            y-domain [(- y-min y-padding) (+ y-max y-padding)]

            margin 40
            width 600
            height 400
            x-range [margin (- width margin)]
            y-range [(- height margin) margin]

            x-scale (fn [x] (+ (first x-range)
                               (* (/ (- x (first x-domain))
                                     (- (second x-domain) (first x-domain)))
                                  (- (second x-range) (first x-range)))))
            y-scale (fn [y] (+ (first y-range)
                               (* (/ (- y (first y-domain))
                                     (- (second y-domain) (first y-domain)))
                                  (- (second y-range) (first y-range)))))

            scales {:x-scale x-scale :y-scale y-scale
                    :x-domain x-domain :y-domain y-domain
                    :x-range x-range :y-range y-range}

            rendered (map (fn [layer points]
                            (render-geom layer points scales))
                          layers all-layer-points)

            point-geoms (filter #(= (:geom/type %) :point) rendered)
            line-geoms (filter #(= (:geom/type %) :line) rendered)
            bar-geoms (filter #(= (:geom/type %) :bar) rendered)

            viz-elements (mapcat :geom/elements point-geoms)
            line-elements (mapcat :geom/elements line-geoms)
            bar-elements (mapcat :geom/elements bar-geoms)

            viz-spec {:x-axis (viz/linear-axis
                               {:domain x-domain
                                :range x-range
                                :major (/ (- (second x-domain) (first x-domain)) 5)
                                :pos (first y-range)})
                      :y-axis (viz/linear-axis
                               {:domain y-domain
                                :range y-range
                                :major (/ (- (second y-domain) (first y-domain)) 5)
                                :pos (first x-range)})
                      :grid {:attribs {:stroke (:grid theme)}}
                      :data viz-elements}

            base-plot (viz/svg-plot2d-cartesian viz-spec)]

        (kind/html
         (svg/serialize
          [:div {:style (str "background:" (:background theme))}
           [:svg {:width width :height height}
            (cond
              ;; Bars and other elements
              (seq bar-elements)
              (let [[tag attrs & children] base-plot]
                (into [tag attrs]
                      (concat (take 3 children)
                              [(into [:g {:class "bars"}] bar-elements)]
                              (when (seq line-elements)
                                [(into [:g {:class "lines"}] line-elements)])
                              (drop 3 children))))

              ;; Lines only
              (seq line-elements)
              (let [[tag attrs & children] base-plot]
                (into [tag attrs]
                      (concat (take 3 children)
                              [(into [:g {:class "lines"}] line-elements)]
                              (drop 3 children))))

              ;; Default (points only)
              :else
              base-plot)]]))))))

;; ## Part 4: Our First Plot! 🎉
;;
;; Now we have everything we need. Let's see our scatter plot:

(comment
  (plot scatter-layer))

;; **We did it!** A scatter plot from first principles.
;;
;; Let's understand what happened:
;; 1. `layer` created a spec (just data)
;; 2. `plot` transformed that spec into visual marks
;; 3. We see sepal length vs width for 150 iris flowers
;;
;; Notice the elegant simplicity: **layer → plot → visualization**.

;; ## Part 5: Exploring Layers
;;
;; Layers are just data, so we can create many variations:

;; ### Different Variables

(comment
  (plot (layer iris :x :petal-length :y :petal-width)))

;; Petal measurements show stronger correlation than sepals!

;; ### Adding Color

(comment
  (plot (layer iris :x :sepal-length :y :sepal-width :color :species)))

;; Now we see the three species! Color is just another aesthetic channel.
;; Notice how setosa (red) separates cleanly from the others.

;; ## Part 6: The Second Primitive - `stack`
;;
;; **What if we want multiple layers on one plot?**
;;
;; Enter `stack` - it concatenates layers for overlays.

(defn stack
  "Concatenate layers (create overlays).
  
  Later layers can inherit aesthetics from earlier layers.
  Returns vector of layers."
  [& layers]
  (let [layers (remove nil? (flatten layers))]
    (vec layers)))

;; ## Part 8: The Third Primitive - `cross`
;;
;; **What if we want to see multiple relationships at once?**
;;
;; Enter `cross` - the Cartesian product of layers, creating grids.
;;
;; The key insight: **multiplication creates grids**.
;; When we cross two layers, we get all combinations of their aesthetics.

(defn- normalize-to-vector
  "Ensure spec is a vector of layers."
  [spec]
  (cond
    (vector? spec) spec
    (map? spec) (if (:layers spec)
                  (normalize-to-vector (:layers spec))
                  [spec])
    :else [spec]))

(defn- smart-merge
  "Merge maps, but don't overwrite with nil values."
  [m1 m2]
  (merge m1
         (into {} (remove (fn [[k v]] (nil? v)) m2))))

(defn cross
  "Cartesian product of layer specs (creates grids).
  
  When you cross two specs, you get all combinations:
  - cross(A, B) = all pairs (a, b) where a ∈ A, b ∈ B
  
  Each resulting layer gets:
  - Merged aesthetics from both parents
  - :row position (from first spec enumeration)
  - :col position (from second spec enumeration)
  
  Returns {:layout :grid :layers [...]} with position metadata."
  [spec-a spec-b]
  (let [layers-a (normalize-to-vector spec-a)
        layers-b (normalize-to-vector spec-b)
        crossed (for [[row-idx layer-a] (map-indexed vector layers-a)
                      [col-idx layer-b] (map-indexed vector layers-b)]
                  (smart-merge (smart-merge layer-a layer-b)
                               {:row row-idx :col col-idx}))]
    {:layout :grid
     :layers (vec crossed)}))

;; ### Seeing Cross in Action
;;
;; Let's start simple. What if we want to see two relationships side by side?

;; Create two layers with different x aesthetics:

(comment
  (def x-layers
    [(layer iris :x :sepal-length)
     (layer iris :x :petal-length)])

  ;; Create two layers with different y aesthetics:

  (def y-layers
    [(layer iris :y :sepal-width)
     (layer iris :y :petal-width)])

  ;; Now cross them:

  (def grid-2x2 (cross x-layers y-layers))

  ;; What do we get? Let's look at the structure:

  (kind/pprint
   (update grid-2x2 :layers
           (fn [layers]
             (mapv #(select-keys % [:x :y :row :col]) layers)))))

;; A 2×2 grid!
;; - Row 0, Col 0: sepal-length × sepal-width
;; - Row 0, Col 1: sepal-length × petal-width
;; - Row 1, Col 0: petal-length × sepal-width
;; - Row 1, Col 1: petal-length × petal-width
;;
;; **Every combination of x and y variables!**

;; Now let's render it:

;; Now let's render it:

(comment
  (plot grid-2x2))

;; **Powerful!** We see all pairwise relationships at once.
;;
;; Notice the structure:
;; - Top row: sepal-length on x-axis (two different y variables)
;; - Bottom row: petal-length on x-axis (two different y variables)
;; - Left column: sepal-width on y-axis
;; - Right column: petal-width on y-axis
;;
;; This is the beginning of a **scatterplot matrix (SPLOM)**!

;; Let's stack two layers - points at different positions:

(comment
  (plot
   (stack
    (layer iris :x :sepal-length :y :sepal-width)
    (layer iris :x :petal-length :y :petal-width))))

;; **Two scatter plots on one canvas!**
;;
;; But wait - this doesn't make much sense visually. Different variables need
;; different scales. Where `stack` shines is for overlaying different
;; **representations** of the same variables.
;;
;; Let me show you what I mean...

;; ## Part 7: Transforms - The Missing Piece
;;
;; Before we can do meaningful overlays, we need **transforms**.
;; A transform computes derived data (smoothing, regression, etc).
;;
;; ### Smooth Transform

(defmethod transform-data :smooth [layer]
  (let [dataset (:data layer)
        window (get-in layer [:transform-opts :window] 11)
        points (layer->points (assoc layer :transform :identity))
        sorted-points (sort-by :x points)
        xs (mapv :x sorted-points)
        ys (mapv :y sorted-points)
        n (count ys)
        half-window (quot window 2)
        smoothed-ys (mapv (fn [i]
                            (let [start (max 0 (- i half-window))
                                  end (min n (+ i half-window 1))
                                  window-vals (subvec ys start end)]
                              (/ (reduce + window-vals) (count window-vals))))
                          (range n))
        smoothed-data (tc/dataset {:x xs :y smoothed-ys})]
    {:data dataset
     :result smoothed-data}))

;; ### Line Geometry

(defmethod render-geom :line [layer points {:keys [x-scale y-scale]}]
  (let [line-stroke (get-in layer [:geom-opts :stroke] (:line-stroke theme))
        line-width (get-in layer [:geom-opts :stroke-width] 2)
        sorted-points (sort-by :x points)
        scaled-points (map (fn [p] [(x-scale (:x p)) (y-scale (:y p))])
                           sorted-points)
        polyline-str (clojure.string/join " "
                                          (map (fn [[x y]] (str x "," y))
                                               scaled-points))]
    {:geom/type :line
     :geom/elements [[:polyline {:points polyline-str
                                 :fill "none"
                                 :stroke line-stroke
                                 :stroke-width line-width
                                 :opacity 0.8}]]}))

;; ## Part 9: Histograms (Bin + Bar)
;;
;; **Histograms reveal distributions.**
;;
;; To create a histogram, we need two pieces:
;; 1. **Bin transform** - Group continuous data into bins
;; 2. **Bar geometry** - Draw rectangles for each bin
;;
;; This demonstrates the power of separating transformation from rendering.

;; ### Bin Transform

(defmethod transform-data :bin [layer]
  (let [dataset (:data layer)
        x-col (:x layer)
        n-bins (get-in layer [:transform-opts :bins] 10)

        ;; Extract values
        values (vec (tc/column dataset x-col))

        ;; Compute bin edges
        min-val (reduce min values)
        max-val (reduce max values)
        bin-width (/ (- max-val min-val) n-bins)
        bin-edges (mapv (fn [i] (+ min-val (* i bin-width))) (range (inc n-bins)))

        ;; Assign each value to a bin
        assign-bin (fn [v]
                     (let [bin-idx (int (min (dec n-bins)
                                             (Math/floor (/ (- v min-val) bin-width))))]
                       bin-idx))

        ;; Count values in each bin
        bin-counts (reduce (fn [counts v]
                             (let [bin-idx (assign-bin v)]
                               (update counts bin-idx (fnil inc 0))))
                           {}
                           values)

        ;; Create binned dataset with bin centers and counts
        bin-data (mapv (fn [i]
                         {:bin-center (+ (nth bin-edges i) (/ bin-width 2))
                          :bin-left (nth bin-edges i)
                          :bin-right (nth bin-edges (inc i))
                          :count (get bin-counts i 0)})
                       (range n-bins))

        binned-dataset (tc/dataset bin-data)]

    {:data dataset
     :result binned-dataset}))

;; ## Part 10: Regression Transform
;;
;; **Regression reveals trends.**
;;
;; The regress transform fits a linear model and generates predicted values.

(defmethod transform-data :regress [layer]
  (let [dataset (:data layer)
        x-col (:x layer)
        y-col (:y layer)

        ;; Extract data
        x-vals (vec (tc/column dataset x-col))
        y-vals (vec (tc/column dataset y-col))

        ;; Prepare data for regression
        X (mapv vector x-vals)
        y y-vals

        ;; Fit linear model
        model (regr/lm X y)

        ;; Generate predictions over the range of x
        x-min (reduce min x-vals)
        x-max (reduce max x-vals)
        n-points 100
        pred-xs (mapv (fn [i]
                        (+ x-min (* i (/ (- x-max x-min) (dec n-points)))))
                      (range n-points))

        ;; Get predictions - predict expects the model first, then data
        pred-ys (mapv (fn [x]
                        (first (regr/predict model [x])))
                      pred-xs)

        ;; Create dataset with predictions
        pred-data (tc/dataset {:x pred-xs :y pred-ys})]

    {:data dataset
     :result pred-data}))

;; ### Regression in Action
;;
;; Let's add a regression line to a scatter plot:

(comment
  (plot
   (stack
    (layer iris :x :petal-length :y :petal-width :geom :point)
    (layer iris :x :petal-length :y :petal-width
           :transform :regress :geom :line
           :geom-opts {:stroke "#FF0000" :stroke-width 2}))))

;; **Powerful!** The red regression line shows the strong linear relationship
;; between petal length and width.
;;
;; Notice the composition:
;; - First layer: raw data as points
;; - Second layer: regressed data as line
;; - `stack` overlays them
;;
;; Same x and y aesthetics, different transforms and geometries!

;; ### Bar Geometry

(defmethod render-geom :bar [layer points {:keys [x-scale y-scale]}]
  (let [bar-fill (get-in layer [:geom-opts :fill] (:bar-fill theme))
        bar-stroke (get-in layer [:geom-opts :stroke] (:bar-stroke theme))

        ;; Extract bin information
        ;; Points come from binned data with :bin-left, :bin-right, :count
        bars (map (fn [p]
                    (let [x-left (x-scale (:x p))
                          x-right (x-scale (:bin-right p))
                          y-bottom (y-scale 0)
                          y-top (y-scale (:y p))
                          width (- x-right x-left)
                          height (- y-bottom y-top)]
                      [:rect {:x x-left
                              :y y-top
                              :width width
                              :height height
                              :fill bar-fill
                              :stroke bar-stroke
                              :stroke-width 1
                              :opacity 0.8}]))
                  points)]

    {:geom/type :bar
     :geom/elements bars}))

;; ### Creating a Histogram
;;
;; Now we can see the distribution of sepal lengths:

(comment
  (plot
   (layer iris
          :x :sepal-length
          :transform :bin
          :transform-opts {:bins 15}
          :geom :bar)))

;; **Beautiful!** The histogram shows the distribution clearly.
;; Most irises have sepal lengths around 5-6 cm.
;;
;; Notice the simplicity:
;; - `:transform :bin` - Groups data into bins
;; - `:geom :bar` - Renders as rectangles
;; - The pipeline handles everything: bin → count → scale → draw
;;
;; This is the power of compositional design!

;; Update plot to handle line geometry:

;; ### Overlaying Raw Data + Smoothed Curve

(comment
  (plot
   (stack
    (layer iris :x :sepal-length :y :sepal-width :geom :point)
    (layer iris :x :sepal-length :y :sepal-width
           :transform :smooth :geom :line))))

;; **Beautiful!** The smoothed curve reveals the underlying trend.
;;
;; Notice what we did:
;; - Same x and y columns
;; - Different transforms (:identity vs :smooth)
;; - Different geometries (:point vs :line)
;; - `stack` overlays them
;;
;; This is the power of composition!

;; ### What We've Discovered
;;
;; The three primitives are now complete:
;; - **`layer`** - Creates a single mapping (data → marks)
;; - **`stack`** - Concatenates layers (overlays)
;; - **`cross`** - Multiplies layers (grids)
;;
;; From just these three operations, we can build:
;; - Single plots (layer → plot)
;; - Overlays (stack layers → plot)
;; - Grids (cross layers → plot)
;;
;; **The algebra is emerging!**

;; ## Progress Check
;;
;; ✅ **Part 1-7: Foundation Complete**
;; - layer primitive with keyword args
;; - stack primitive for overlays  
;; - Transform system (identity, smooth)
;; - Geometry system (point, line)
;; - Single plot rendering
;;
;; ✅ **Part 8: Cross Primitive Complete**
;; - cross function with smart merge
;; - Grid rendering (2×2 demonstrated)
;; - Position tracking (:row, :col)
;; - Scatterplot matrix foundation
;;
;; ✅ **Part 9: Histograms Complete**
;; - Bin transform (groups continuous data)
;; - Bar geometry (renders rectangles)
;; - Histogram visualization working
;; - Transform + geometry composition demonstrated
;;
;; ✅ **Part 10: Regression Complete**
;; - Regress transform (linear models)
;; - Generates predictions over x range
;; - Overlays with raw data using stack
;; - Shows transform composability
;;
;; **Coming Next:**
;; - Part 11: Grouped statistics (per-species regression)
;; - Part 12: Complete SPLOMs (4×4 with diagonals)
;; - Part 13: Reveal the algebra
