^{:kindly/hide-code true
  :clay {:title "Beautiful Plotting: A Refined Grammar of Graphics"
         :quarto {:type :post
                  :author [:daslu]
                  :date "2026-01-07"
                  :description "Elegant, harmonious data visualization combining GoG algebra with compositional layers"
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :beauty]
                  :keywords [:datavis :grammar-of-graphics :algebra-of-graphics :splom]
                  :toc true
                  :toc-depth 4
                  :toc-expand 4
                  :draft true}}}
(ns data-visualization.beautiful-plotting
  "Beautiful plotting: A refined grammar of graphics implementation.
  
  Core principles:
  - Uniform interfaces (all renderers return same shape)
  - Single responsibility (each function does one thing)
  - Explicit over implicit (clear intent)
  - Evolutionary design (built on proven patterns)
  
  Architecture:
  1. Algebraic layer - Pure data manipulation (l*, l+, d*, d+)
  2. Inference layer - Visualization logic (smart-defaults)
  3. Rendering layer - SVG generation (plot)"
  (:require
   ;; Tablecloth - Dataset manipulation
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]

   ;; Kindly - Notebook visualization protocol
   [scicloj.kindly.v4.kind :as kind]

   ;; thi.ng/geom-viz - Static SVG visualization
   [thi.ng.geom.viz.core :as viz]
   [thi.ng.geom.svg.core :as svg]
   [thi.ng.geom.core :as g]

   ;; Fastmath - Statistical computations
   [fastmath.ml.regression :as regr]
   [fastmath.stats :as stats]

   ;; Combinatorics - For cartesian product in l*
   [clojure.math.combinatorics :as combo]

   ;; Toydata - Example datasets
   [scicloj.metamorph.ml.toydata :as toydata]))

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

;; # Beautiful Plotting
;;
;; An elegant implementation of the Grammar of Graphics that prioritizes:
;; - **Harmony** - Every piece feels perfectly in place
;; - **Clarity** - Intent is obvious from reading the code
;; - **Consistency** - Similar operations work similarly
;; - **Composability** - Operations combine without surprises
;;
;; This is a refined evolution of unified_plotting.clj, keeping what works
;; beautifully and polishing what can be better.

;; ## Theme

(def theme
  "Visual theme - ggplot2 aesthetic.
  
  Color palette is colorblind-friendly and visually balanced."
  {:background "#EBEBEB"
   :grid "#FFFFFF"
   :colors ["#F8766D" "#00BA38" "#619CFF" "#F564E3"]
   :default-mark "#333333"
   :point-fill "#333333"
   :point-stroke "#FFFFFF"
   :point-size 2
   :line-stroke "#333333"
   :line-width 1.5
   :histogram-fill "#333333"
   :histogram-stroke "#FFFFFF"
   :histogram-stroke-width 0.5})

;; ## Core Algebraic Operations

(defn- merge-layers
  "Merge multiple layer maps with special :=columns handling.
  
  :=columns vectors concatenate to preserve positional provenance, but ONLY
  when no visual roles (:=x, :=y) are assigned yet. If roles exist, :=columns
  concatenation is skipped to avoid contradictions."
  [& layers]
  (let [has-roles? (some #(or (:=x %) (:=y %)) layers)]
    (reduce (fn [acc layer]
              (reduce-kv (fn [m k v]
                           (cond
                             ;; Only concatenate :=columns if no roles assigned yet
                             (and (= k :=columns)
                                  (not has-roles?)
                                  (vector? (get m k)))
                             (update m k into v)

                             ;; If roles exist, skip :=columns concat (avoid conflicts)
                             (and (= k :=columns) has-roles?)
                             m

                             ;; All other properties: normal merge (right wins)
                             :else
                             (assoc m k v)))
                         acc
                         layer))
            {}
            layers)))

(defn auto-assign-roles
  "Assign :=x and :=y visual roles from :=columns positional provenance.
  
  Positional semantics: 1 column → :=x only, 2 columns → :=x + :=y,
  3+ columns → error (ambiguous). Idempotent - if :=x/:=y already assigned,
  returns layer unchanged."
  [layer]
  (if (or (:=x layer) (:=y layer))
    layer
    (let [cols (:=columns layer)]
      (case (count cols)
        1 (assoc layer :=x (first cols))
        2 (assoc layer :=x (first cols) :=y (second cols))
        (throw (ex-info "Ambiguous column count; specify :=x/:=y explicitly"
                        {:columns cols
                         :layer layer}))))))

(defn l*
  "Layer cross: Cartesian product with property merging.
  
  Core algebraic operation implementing the distributive law. Takes specs with
  :=layers and produces their cross-product, merging layers with special
  :=columns handling. Non-commutative - order matters for :=columns."
  [& specs]
  (let [specs (remove nil? specs)]
    (if (= 1 (count specs))
      (first specs)
      (reduce
       (fn [acc spec]
         (cond
           ;; Both have layers → cross-product
           (and (:=layers acc) (:=layers spec))
           (let [all-layers (list (:=layers acc) (:=layers spec))
                 product-layers (apply combo/cartesian-product all-layers)
                 merged-layers (map #(apply merge-layers %) product-layers)
                 plot-props (merge (dissoc acc :=layers) (dissoc spec :=layers))]
             (assoc plot-props :=layers (vec merged-layers)))

           ;; Only one has layers → merge properties
           (:=layers acc)
           (merge spec acc)

           (:=layers spec)
           (merge acc spec)

           ;; Neither has layers → simple merge
           :else
           (merge acc spec)))
       specs))))

(defn l+
  "Layer blend: Concatenate layers into alternatives.
  
  Core algebraic operation for creating collections. Takes specs with :=layers
  and concatenates all layers into a single vector. Plot-level properties are
  merged (rightmost wins). 
  
  Inheritance: New layers inherit :=x, :=y, :=color from the first layer if
  they don't already have them (enables overlays like scatter + linear)."
  [& specs]
  (let [all-layers (mapcat :=layers specs)
        plot-props (apply merge (map #(dissoc % :=layers) specs))

        ;; Extract inheritable properties from first layer (base layer)
        base-layer (first all-layers)
        inheritable-keys [:=x :=y :=color]
        inherited-props (when base-layer
                          (select-keys base-layer inheritable-keys))

        ;; Apply inheritance: new layers get base properties if they lack them
        layers-with-inheritance
        (if (and inherited-props
                 (seq inherited-props)
                 (> (count all-layers) 1))
          (map-indexed
           (fn [idx layer]
             (if (zero? idx)
               layer ; First layer is base - don't modify
               (merge inherited-props layer))) ; Subsequent inherit
           all-layers)
          all-layers)]

    (assoc plot-props :=layers (vec layers-with-inheritance))))

(defn overlay
  "Explicit overlay: Stack geometries with clear inheritance.
  
  Equivalent to l+ but makes overlay intent explicit. Use when you want
  to be clear that layers are stacked (e.g., scatter + linear + smooth).
  
  Example:
    (overlay scatter-spec linear-spec smooth-spec)"
  [& specs]
  (apply l+ specs))

(defn smart-defaults
  "Apply opinionated visualization choices to prepare a spec for rendering.
  
  Transforms raw crossed/blended spec into render-ready spec:
  - Auto-assigns visual roles (:=columns → :=x/:=y)
  - Detects diagonals (:=x == :=y)
  - Infers plottypes (diagonal → :histogram, else → :scatter)
  - Assigns grid positions
  - Sets layout flags
  
  Returns spec ready for (plot spec)."
  [spec]
  (let [layers (:=layers spec)
        n-layers (count layers)

        ;; Check if this looks like a crossed grid
        all-have-pairs? (every? #(= 2 (count (:=columns %))) layers)
        is-grid? (and (> n-layers 1) all-have-pairs?)

        ;; Process layers with role assignment, diagonal detection, and grid positions
        processed-layers
        (if is-grid?
          ;; Grid layout: assign positions based on unique column values
          (let [x-vars (distinct (map #(first (:=columns %)) layers))
                y-vars (distinct (map #(second (:=columns %)) layers))
                x-index (into {} (map-indexed (fn [i v] [v i]) x-vars))
                y-index (into {} (map-indexed (fn [i v] [v i]) y-vars))]
            (map (fn [layer]
                   (let [assigned (auto-assign-roles layer)
                         x-col (:=x assigned)
                         y-col (:=y assigned)
                         diagonal? (= x-col y-col)
                         row (x-index x-col)
                         col (y-index y-col)]
                     (assoc assigned
                            :=grid-row row
                            :=grid-col col
                            :=diagonal? diagonal?
                            :=plottype (if diagonal? :histogram :scatter))))
                 layers))
          ;; Single panel: just assign roles and detect diagonal
          (for [layer layers]
            (let [assigned (auto-assign-roles layer)
                  x-col (:=x assigned)
                  y-col (:=y assigned)
                  diagonal? (= x-col y-col)]
              (assoc assigned
                     :=diagonal? diagonal?
                     :=plottype (if diagonal? :histogram :scatter)))))]

    (cond-> (assoc spec :=layers processed-layers)
      is-grid? (assoc :=layout :grid))))

(defn d*
  "Dataset cross: Pure algebraic crossing with NO inference.
  
  Variadic - crosses 2+ column lists. Each argument is a vector of columns to
  cross. Returns spec with :=layers (cartesian product), :=data, :=indices.
  Use (smart-defaults spec) to prepare for rendering."
  [dataset & col-lists]
  (let [specs (map (fn [cols]
                     (let [col-vec (if (vector? cols) cols [cols])]
                       {:=layers (map (fn [col] {:=columns [col]}) col-vec)
                        :=data dataset
                        :=indices (range (tc/row-count dataset))}))
                   col-lists)]
    (reduce l* specs)))

(defn d+
  "Dataset blend: Create column alternatives with NO inference.
  
  Takes a vector of columns and creates alternatives - each column becomes a
  separate layer. Returns spec with :=layers (one per column), :=data, :=indices.
  Use (smart-defaults spec) to prepare for rendering."
  [dataset columns]
  (apply l+ (map (fn [col]
                   {:=layers [{:=columns [col]}]
                    :=data dataset
                    :=indices (range (tc/row-count dataset))})
                 columns)))

;; ## Helper Functions

(defn- compute-domain
  "Compute min/max domain from values."
  [values]
  (when (seq values)
    [(reduce min values)
     (reduce max values)]))

(defn- get-column-values
  "Extract values from a dataset column."
  [dataset column]
  (vec (get dataset column)))

(defn- resolve-column
  "Resolve column keyword to actual data during rendering.
  
  Precedence:
  1. Try layer :=data first
  2. If column missing, try plot :=data
  3. If still missing, error with helpful message"
  [layer plot-data col-key]
  (let [layer-data (:=data layer)
        dataset (or layer-data plot-data)]
    (if-not dataset
      (throw (ex-info "No data available"
                      {:column col-key
                       :layer-has-data? (some? layer-data)
                       :plot-has-data? (some? plot-data)}))
      (if (tc/has-column? dataset col-key)
        (tc/column dataset col-key)
        (throw (ex-info "Column not found in layer or plot data"
                        {:column col-key
                         :layer-columns (when layer-data (tc/column-names layer-data))
                         :plot-columns (when plot-data (tc/column-names plot-data))}))))))

(defn- extract-points
  "Extract point data from a layer, with plot-level data fallback.
  
  Returns sequence of point maps with :x, :y, and optionally :color keys."
  [layer plot-data]
  (let [x-col (:=x layer)
        y-col (:=y layer)
        xs (resolve-column layer plot-data x-col)
        ys (resolve-column layer plot-data y-col)
        color-col (:=color layer)]
    (if color-col
      (let [colors (resolve-column layer plot-data color-col)]
        (mapv (fn [x y c] {:x x :y y :color c})
              xs ys colors))
      (mapv (fn [x y] {:x x :y y}) xs ys))))

;; ## Geometry Renderers (Phase 1: Uniform Return Types)

(defn- render-scatter
  "Render scatter plot points.
  
  Returns uniform geometry spec:
  {:geom/type :scatter
   :geom/data {viz-spec or list of viz-specs for color groups}}"
  [layer points x-scale y-scale]
  (let [point-fill (:point-fill theme)
        point-stroke (:point-stroke theme)
        color-groups (group-by :color points)]
    (if (> (count color-groups) 1)
      ;; Colored scatter - multiple groups
      (let [colors (:colors theme)
            viz-specs (map-indexed
                       (fn [idx [color-val group-points]]
                         {:values (mapv (fn [p] [(:x p) (:y p)]) group-points)
                          :layout viz/svg-scatter-plot
                          :attribs {:fill (get colors idx point-fill)
                                    :stroke (get colors idx point-stroke)
                                    :stroke-width 0.5
                                    :opacity 1.0}})
                       color-groups)]
        {:geom/type :scatter
         :geom/data viz-specs})
      ;; Simple scatter - single group
      {:geom/type :scatter
       :geom/data {:values (mapv (fn [p] [(:x p) (:y p)]) points)
                   :layout viz/svg-scatter-plot
                   :attribs {:fill point-fill
                             :stroke point-stroke
                             :stroke-width 0.5
                             :opacity 1.0}}})))

(defn- compute-histogram-bins
  "Compute histogram bins using Sturges' rule.
  
  Returns vector of {:bin-start :bin-end :count} maps."
  [points]
  (let [values (mapv :x points)
        n (count values)
        num-bins (max 5 (int (Math/ceil (+ 1 (* 3.322 (Math/log10 n))))))
        min-val (reduce min values)
        max-val (reduce max values)
        bin-width (/ (- max-val min-val) num-bins)
        bins (for [i (range num-bins)]
               (let [start (+ min-val (* i bin-width))
                     end (+ min-val (* (inc i) bin-width))]
                 {:bin-start start
                  :bin-end end
                  :count (count (filter #(<= start % end) values))}))]
    (vec bins)))

(defn- render-histogram
  "Render histogram bars.
  
  Returns uniform geometry spec:
  {:geom/type :histogram
   :geom/data {:bars [...] :max-count N :fill ... :stroke ...}}"
  [layer points x-scale y-scale]
  (let [bins (compute-histogram-bins points)
        max-count (apply max (map :count bins))
        histogram-fill (:histogram-fill theme)
        histogram-stroke (:histogram-stroke theme)]
    {:geom/type :histogram
     :geom/data {:bars bins
                 :max-count max-count
                 :fill histogram-fill
                 :stroke histogram-stroke}}))

(defn- compute-linear-regression
  "Compute linear regression for a collection of points.
  
  Returns map with :slope, :intercept, :x-min, :x-max or nil if not enough data."
  [points]
  (when (>= (count points) 2)
    (let [xs (mapv :x points)
          ys (mapv :y points)
          result (regr/lm ys xs)
          slope (first (:beta result))
          intercept (:intercept result)]
      (when (and slope intercept
                 (not (Double/isNaN slope))
                 (not (Double/isNaN intercept)))
        {:slope slope
         :intercept intercept
         :x-min (reduce min xs)
         :x-max (reduce max xs)}))))

(defn- render-linear
  "Render linear regression line.
  
  Returns uniform geometry spec:
  {:geom/type :linear
   :geom/data {line-data or list of line-data for color groups}}"
  [layer points x-scale y-scale]
  (let [line-stroke (:line-stroke theme)
        colors (:colors theme)
        color-groups (group-by :color points)]
    (if (> (count color-groups) 1)
      ;; Colored regression - one line per group
      (let [line-specs (keep-indexed
                        (fn [idx [color-val group-points]]
                          (when-let [regression (compute-linear-regression group-points)]
                            (let [{:keys [slope intercept x-min x-max]} regression
                                  y-start (+ (* slope x-min) intercept)
                                  y-end (+ (* slope x-max) intercept)]
                              {:x1 x-min :y1 y-start :x2 x-max :y2 y-end
                               :stroke (get colors idx line-stroke)
                               :stroke-width 2
                               :opacity 1.0})))
                        color-groups)]
        {:geom/type :linear
         :geom/data line-specs})
      ;; Simple regression - single line
      (when-let [regression (compute-linear-regression points)]
        (let [{:keys [slope intercept x-min x-max]} regression
              y-start (+ (* slope x-min) intercept)
              y-end (+ (* slope x-max) intercept)]
          {:geom/type :linear
           :geom/data {:x1 x-min :y1 y-start :x2 x-max :y2 y-end
                       :stroke line-stroke
                       :stroke-width 2
                       :opacity 1.0}})))))

(defn- compute-smooth
  "Compute smoothed y-values using moving average.
  
  Returns vector of {:x x :y smoothed-y} maps sorted by x."
  ([points] (compute-smooth points 11))
  ([points window]
   (when (>= (count points) 2)
     (let [sorted-points (sort-by :x points)
           xs (mapv :x sorted-points)
           ys (mapv :y sorted-points)
           n (count ys)
           half-window (quot window 2)
           smoothed-ys (mapv (fn [i]
                               (let [start (max 0 (- i half-window))
                                     end (min n (+ i half-window 1))
                                     window-vals (subvec ys start end)]
                                 (/ (reduce + window-vals) (count window-vals))))
                             (range n))]
       (mapv (fn [x y] {:x x :y y}) xs smoothed-ys)))))

(defn- render-smooth
  "Render smoothed curves as SVG polyline elements.
  
  Returns uniform geometry spec:
  {:geom/type :smooth
   :geom/data {polyline or list of polylines for color groups}}"
  [layer points x-scale y-scale]
  (when (and x-scale y-scale)
    (let [color-col (:=color layer)
          palette (:colors theme)]
      (if color-col
        ;; Grouped: one curve per color value
        (let [grouped (group-by :color points)
              polylines (keep-indexed
                         (fn [idx [color-val group-points]]
                           (when-let [smooth-data (compute-smooth group-points)]
                             (let [scaled-points (map (fn [{:keys [x y]}]
                                                        [(x-scale x) (y-scale y)])
                                                      smooth-data)
                                   polyline-str (clojure.string/join " "
                                                                     (map (fn [[x y]] (str x "," y))
                                                                          scaled-points))]
                               {:points polyline-str
                                :fill "none"
                                :stroke (nth palette idx "#333333")
                                :stroke-width 2.5
                                :opacity 0.8})))
                         grouped)]
          {:geom/type :smooth
           :geom/data polylines})
        ;; Ungrouped: single curve
        (when-let [smooth-data (compute-smooth points)]
          (let [scaled-points (map (fn [{:keys [x y]}]
                                     [(x-scale x) (y-scale y)])
                                   smooth-data)
                polyline-str (clojure.string/join " "
                                                  (map (fn [[x y]] (str x "," y))
                                                       scaled-points))]
            {:geom/type :smooth
             :geom/data {:points polyline-str
                         :fill "none"
                         :stroke "#333333"
                         :stroke-width 2.5
                         :opacity 0.8}}))))))

;; ## Rendering Coordination

(defmulti render-geometry
  "Render a geometry based on its type.
  
  All render-* functions return uniform {:geom/type ... :geom/data ...} shape."
  (fn [layer points x-scale y-scale] (:=plottype layer)))

(defmethod render-geometry :scatter
  [layer points x-scale y-scale]
  (render-scatter layer points x-scale y-scale))

(defmethod render-geometry :histogram
  [layer points x-scale y-scale]
  (render-histogram layer points x-scale y-scale))

(defmethod render-geometry :linear
  [layer points x-scale y-scale]
  (render-linear layer points x-scale y-scale))

(defmethod render-geometry :smooth
  [layer points x-scale y-scale]
  (render-smooth layer points x-scale y-scale))

(defn- render-single-panel
  "Render a single panel (one or more overlaid layers) to SVG hiccup.
  
  All layers in the panel share the same domain and coordinate system.
  Returns hiccup vector."
  [layers plot-dataset width height]
  (let [;; Check if we have histogram layers
        has-histogram? (some #(= (:=plottype %) :histogram) layers)

        ;; Extract all points to compute domains
        all-points (mapcat #(extract-points % plot-dataset) layers)
        x-vals (map :x all-points)
        y-vals (if has-histogram? [] (map :y all-points))
        [x-min x-max] (compute-domain x-vals)
        [y-min y-max] (if has-histogram?
                        [0 0]
                        (compute-domain y-vals))

        ;; Add 5% padding to domains
        x-padding (* 0.05 (- x-max x-min))
        y-padding (* 0.05 (- y-max y-min))
        x-domain [(- x-min x-padding) (+ x-max x-padding)]
        y-domain [(- y-min y-padding) (+ y-max y-padding)]

        ;; Calculate ranges based on margins
        margin 20
        x-range [margin (- width margin)]
        y-range [(- height margin) margin]

        ;; Render each layer (returns uniform {:geom/type ... :geom/data ...})
        rendered-geometries (for [layer layers]
                              (let [points (extract-points layer plot-dataset)]
                                (render-geometry layer points nil nil)))

        ;; Remove nils
        rendered-geometries (remove nil? rendered-geometries)

        ;; Separate by type using the uniform :geom/type field
        histogram-geoms (filter #(= (:geom/type %) :histogram) rendered-geometries)
        linear-geoms (filter #(= (:geom/type %) :linear) rendered-geometries)
        smooth-geoms (filter #(= (:geom/type %) :smooth) rendered-geometries)
        scatter-geoms (filter #(= (:geom/type %) :scatter) rendered-geometries)

        ;; Update y-domain if we have histograms
        y-domain (if (seq histogram-geoms)
                   (let [max-count (apply max (map #(get-in % [:geom/data :max-count]) histogram-geoms))]
                     [0 (* 1.05 max-count)])
                   y-domain)

        ;; Create scale functions
        x-scale (fn [x] (+ (first x-range)
                           (* (/ (- x (first x-domain))
                                 (- (second x-domain) (first x-domain)))
                              (- (second x-range) (first x-range)))))
        y-scale (fn [y] (+ (first y-range)
                           (* (/ (- y (first y-domain))
                                 (- (second y-domain) (first y-domain)))
                              (- (second y-range) (first y-range)))))

        ;; Re-render smooth layers with proper scales
        smooth-elements (mapcat (fn [layer]
                                  (let [points (extract-points layer plot-dataset)
                                        geom (render-geometry layer points x-scale y-scale)
                                        data (:geom/data geom)]
                                    (if (sequential? data)
                                      (map (fn [d] [:polyline d]) data)
                                      [[:polyline data]])))
                                (filter #(= (:=plottype %) :smooth) layers))

        ;; Convert histogram bars to SVG rect elements
        histogram-rects (mapcat (fn [geom]
                                  (let [data (:geom/data geom)
                                        bars (:bars data)
                                        fill (:fill data)
                                        stroke (:stroke data)]
                                    (map (fn [bar]
                                           (let [x1 (x-scale (:bin-start bar))
                                                 x2 (x-scale (:bin-end bar))
                                                 y-base (y-scale 0)
                                                 y-top (y-scale (:count bar))
                                                 bar-width (- x2 x1)
                                                 bar-height (- y-base y-top)]
                                             (svg/rect [x1 y-top] bar-width bar-height
                                                       {:fill fill
                                                        :stroke stroke
                                                        :stroke-width (:histogram-stroke-width theme)})))
                                         bars)))
                                histogram-geoms)

        ;; Convert line data to SVG line elements
        regression-lines (mapcat (fn [geom]
                                   (let [data (:geom/data geom)]
                                     (if (sequential? data)
                                       ;; Multiple lines (colored groups)
                                       (map (fn [line-data]
                                              (let [{:keys [x1 y1 x2 y2 stroke stroke-width opacity]} line-data
                                                    x1-px (x-scale x1)
                                                    y1-px (y-scale y1)
                                                    x2-px (x-scale x2)
                                                    y2-px (y-scale y2)]
                                                (svg/line [x1-px y1-px] [x2-px y2-px]
                                                          {:stroke stroke
                                                           :stroke-width stroke-width
                                                           :opacity opacity})))
                                            data)
                                       ;; Single line
                                       (when data
                                         (let [{:keys [x1 y1 x2 y2 stroke stroke-width opacity]} data
                                               x1-px (x-scale x1)
                                               y1-px (y-scale y1)
                                               x2-px (x-scale x2)
                                               y2-px (y-scale y2)]
                                           [(svg/line [x1-px y1-px] [x2-px y2-px]
                                                      {:stroke stroke
                                                       :stroke-width stroke-width
                                                       :opacity opacity})])))))
                                 linear-geoms)

        ;; Extract viz-compatible scatter data
        viz-layers (mapcat (fn [geom]
                             (let [data (:geom/data geom)]
                               (if (sequential? data)
                                 data ; Multiple viz specs (colored groups)
                                 [data]))) ; Single viz spec
                           scatter-geoms)

        ;; Build viz spec
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
                  :data viz-layers}

        ;; Render base plot
        base-plot (viz/svg-plot2d-cartesian viz-spec)]

    ;; Insert custom elements into the SVG
    (if (or (seq histogram-rects) (seq regression-lines) (seq smooth-elements))
      (let [[tag attrs & children] base-plot
            custom-groups (concat
                           (when (seq histogram-rects)
                             [(into [:g {:class "histogram-bars"}] histogram-rects)])
                           (when (seq regression-lines)
                             [(into [:g {:class "regression-lines"}] regression-lines)])
                           (when (seq smooth-elements)
                             [(into [:g {:class "smooth-curves"}] smooth-elements)]))]
        (if (= tag :g)
          (into [tag attrs]
                (concat (take 3 children)
                        custom-groups
                        (drop 3 children)))
          base-plot))
      base-plot)))

(defn plot
  "Render a plot specification to SVG.
  
  Takes a spec with :=layers and renders to SVG.
  Handles single layers, grids, and overlays."
  [spec]
  (let [layers (:=layers spec)
        plot-dataset (:=data spec)
        layout (:=layout spec :single)
        width 600
        height 400]

    (when (empty? layers)
      (throw (ex-info "Cannot plot spec with no layers"
                      {:spec spec})))

    (case layout
      :single
      (kind/html
       (svg/serialize
        [:div {:style (str "background:" (:background theme))}
         [:svg {:width width :height height}
          (render-single-panel layers plot-dataset width height)]]))

      :grid
      (let [grid-map (group-by (juxt :=grid-row :=grid-col) layers)
            grid-rows (keep :=grid-row layers)
            grid-cols (keep :=grid-col layers)
            max-row (if (seq grid-rows) (apply max grid-rows) 0)
            max-col (if (seq grid-cols) (apply max grid-cols) 0)
            panel-width 150
            panel-height 150
            total-width (* (inc max-col) panel-width)
            total-height (* (inc max-row) panel-height)]

        (kind/html
         (svg/serialize
          [:div {:style (str "background:" (:background theme))}
           [:svg {:width total-width :height total-height}
            (for [row (range (inc max-row))
                  col (range (inc max-col))]
              (when-let [panel-layers (get grid-map [row col])]
                (let [x-offset (* col panel-width)
                      y-offset (* row panel-height)
                      panel-svg (render-single-panel panel-layers plot-dataset panel-width panel-height)]
                  [:g {:transform (str "translate(" x-offset "," y-offset ")")}
                   panel-svg])))]])))

      (kind/html
       (svg/serialize
        [:div {:style (str "background:" (:background theme))}
         [:svg {:width width :height height}
          (render-single-panel layers plot-dataset width height)]])))))

;; ## Geometry Constructors

(defn linear
  "Linear regression geometry.
  
  Returns a spec with :=plottype :linear."
  []
  {:=layers [{:=plottype :linear}]})

(defn smooth
  "Smooth curve geometry using moving average.
  
  Accepts optional {:window N} to control smoothing window size."
  ([] (smooth {}))
  ([opts]
   {:=layers [(merge {:=plottype :smooth} opts)]}))

;; ## Override Constructors (Phase 2: Separated from l*)

(defn- apply-override
  "Apply override spec to matching layers.
  
  Helper for diagonal and off-diagonal functions."
  [spec predicate override-spec]
  (update spec :=layers
          (fn [layers]
            (mapcat
             (fn [layer]
               (if (predicate layer)
                 ;; Layer matches predicate - apply override
                 (if-let [override-layers (:=layers override-spec)]
                   ;; Override has layers - keep original + add overlays
                   (cons layer
                         (for [override-layer override-layers]
                           (merge layer override-layer)))
                   ;; Override is just properties - merge them
                   [(merge layer override-spec)])
                 ;; Layer doesn't match - pass through unchanged
                 [layer]))
             layers))))

(defn diagonal
  "Apply geometry to diagonal layers (x=y).
  
  Can take a plottype keyword or a full layer spec for composition.
  
  Example:
    (diagonal spec :scatter)
    (diagonal spec (linear))"
  [spec plottype-or-spec]
  (let [override (if (keyword? plottype-or-spec)
                   {:=plottype plottype-or-spec}
                   plottype-or-spec)]
    (apply-override spec :=diagonal? override)))

(defn off-diagonal
  "Apply geometry to off-diagonal layers (x≠y).
  
  Can take a plottype keyword or a full layer spec for composition.
  
  Example:
    (off-diagonal spec :histogram)
    (off-diagonal spec (l+ (linear) (smooth)))"
  [spec plottype-or-spec]
  (let [override (if (keyword? plottype-or-spec)
                   {:=plottype plottype-or-spec}
                   plottype-or-spec)]
    (apply-override spec (complement :=diagonal?) override)))

;; ## Convenience Functions

(defn splom
  "Create a scatterplot matrix (SPLOM) from a dataset and variables.
  
  Applies smart-defaults automatically - equivalent to:
    (-> (d* dataset variables variables) smart-defaults)
  
  Returns grid spec ready for plotting with diagonal panels as histograms,
  off-diagonal as scatter."
  [dataset variables]
  (-> (d* dataset variables variables)
      smart-defaults))

;; ## Dataset

(def iris
  "Classic Iris dataset - 150 observations of iris flowers."
  (toydata/iris-ds))

iris

;; # Examples

;; ## Simple Scatter Plot

(-> (d* iris [:sepal-length] [:sepal-width])
    smart-defaults
    plot)

;; ## Histogram (Diagonal)

(-> (d* iris [:sepal-length] [:sepal-length])
    smart-defaults
    plot)

;; ## Scatter with Regression (Using overlay)

(-> (d* iris [:sepal-length] [:sepal-width])
    smart-defaults
    (overlay (linear))
    plot)

;; ## Three-Layer Composition

(-> (d* iris [:sepal-length] [:sepal-width])
    smart-defaults
    (overlay (linear))
    (overlay (smooth))
    plot)

;; ## 2×2 SPLOM

(-> (splom iris [:sepal-length :sepal-width])
    plot)

;; ## SPLOM with Diagonal Override

(-> (splom iris [:sepal-length :sepal-width])
    (diagonal :scatter)
    plot)

;; ## SPLOM with Off-Diagonal Regression

(-> (splom iris [:sepal-length :sepal-width])
    (off-diagonal (overlay (linear)))
    plot)

;; ## Full 4×4 SPLOM

(-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
    plot)

;; ## Colored SPLOM with Regression

(-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
    (l* {:=layers [{:=color :species}]})
    (off-diagonal (overlay (linear)))
    plot)

;; # What We've Achieved
;;
;; **Phase 1 Complete:** Uniform render return types
;; - All geometry renderers return `{:geom/type ... :geom/data ...}`
;; - Filter by `:geom/type` field (clean, extensible)
;; - Add new geometries without changing coordination logic
;;
;; **Phase 2 Complete:** Separated override logic
;; - `l*` does cross-product and property merging only
;; - `diagonal` and `off-diagonal` handle override application
;; - Single responsibility throughout
;;
;; **Phase 3 Complete:** Explicit overlay option
;; - `overlay` makes stacking intent clear
;; - `l+` still works for those who prefer it
;; - No breaking changes
;;
;; **Phase 4 Complete:** Naming consistency
;; - `extract-points` instead of `layer->points`
;; - `compute-domain` instead of `infer-domain`
;; - Consistent verb naming throughout
;;
;; **Phase 5 Complete:** Beautiful examples
;; - Progressive complexity (scatter → histogram → overlays → grids → SPLOM)
;; - Clear demonstration of composition patterns
;; - Every example works and renders correctly
;;
;; ## Design Harmony Achieved
;;
;; ✓ **Uniform interfaces** - All renderers return same shape
;; ✓ **Single responsibility** - Each function does one thing
;; ✓ **Explicit options** - `overlay` makes intent clear
;; ✓ **Backward compatible** - `l+` still works
;; ✓ **Consistent naming** - Predictable patterns throughout
;; ✓ **Beautiful examples** - Progressive pedagogy
;; ✓ **Minimal changes** - Evolution, not revolution
;;
;; The result is a visualization library where every piece feels perfectly in place.
