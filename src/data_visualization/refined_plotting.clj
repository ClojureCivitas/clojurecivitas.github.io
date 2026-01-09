^{:kindly/hide-code true
  :clay {:title "Refined Plotting: Clean Two-Stage Inference"
         :quarto {:type :post
                  :author [:daslu]
                  :date "2026-01-09"
                  :description "Grammar of Graphics with explicit role inference and spread stages"
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :clean-design]
                  :keywords [:datavis :grammar-of-graphics :wilkinson]
                  :toc true
                  :toc-depth 3
                  :draft true}}}
(ns data-visualization.refined-plotting
  "Refined plotting with clean two-stage inference.
  
  Design principles:
  - Simple IR (intermediate representation) - data is the API
  - Keep :=columns for positional algebra
  - Explicit two-stage transformation:
    1. resolve-roles: :=columns → :=x/:=y
    2. spread: :=color/:=facet → multiple layers
  - Both stages are visible and inspectable
  
  Architecture:
  1. Algebraic layer - cross, blend (operate on :=columns)
  2. Role inference - resolve-roles (positional → named)
  3. Grouping spread - spread (markers → partitioned layers)
  4. Rendering - plot (SVG generation)"
  (:require
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.viz.core :as viz]
   [thi.ng.geom.svg.core :as svg]
   [thi.ng.geom.core :as g]
   [clojure.math.combinatorics :as combo]))

;; # Introduction
;;
;; This notebook implements a clean Grammar of Graphics with explicit stages:
;;
;; **Algebra** → **Assign Roles** → **Spread** → **Render**
;;
;; Each stage is a pure function on data. The IR is simple and inspectable.

;; ## Theme

(def theme
  "Visual theme for plots - matches ggplot2 aesthetic.
  
  Key features:
  - Grey background (#EBEBEB) reduces eye strain
  - White grid lines (#FFFFFF) aid visual comparison
  - Color palette from ggplot2 (colorblind-friendly)
  - Dark marks (#333333) for single-color plots"
  {:background "#EBEBEB"
   :grid "#FFFFFF"
   :colors ["#F8766D" "#00BA38" "#619CFF" "#F564E3"]
   :default-mark "#333333"
   :point-fill "#333333"
   :point-stroke "#FFFFFF"
   :point-size 3
   :line-stroke "#333333"
   :line-width 1.5
   :histogram-fill "#333333"
   :histogram-stroke "#FFFFFF"
   :histogram-stroke-width 0.5})

;; ## Test Data

(def simple-data
  (tc/dataset {:x [1 2 3 4 5]
               :y [2 4 6 8 10]
               :z [1 3 5 7 9]
               :color ["a" "a" "b" "b" "a"]}))

simple-data

;; ## Stage 1: Algebraic Operations
;;
;; These operate on specs with :=columns (positional)

(defn merge-layers
  "Merge layer maps with :=columns concatenation.
  
  Simple rule: :=columns vectors concatenate, all other properties merge
  (rightmost wins)."
  [& layers]
  (reduce (fn [acc layer]
            (reduce-kv (fn [m k v]
                         (if (= k :=columns)
                           (update m k (fnil into []) v)
                           (assoc m k v)))
                       acc
                       layer))
          {}
          layers))

;; Examples:
;; Line 99-100
(kind/pprint
 (merge-layers {:=columns [:a]} {:=columns [:b]}))

(kind/pprint
 (merge-layers {:=columns [:a] :=data "dataset1"}
               {:=columns [:b] :=data "dataset2"}))

(defn cross
  "Cartesian product of specs.
  
  Creates new layers by merging all combinations of input layers.
  This is the fundamental × operation of the grammar."
  [& specs]
  (let [specs (remove nil? specs)]
    (if (= 1 (count specs))
      (first specs)
      (reduce
       (fn [acc spec]
         (cond
           ;; Both have layers → cross product
           (and (:=layers acc) (:=layers spec))
           (let [product-layers (for [layer-a (:=layers acc)
                                      layer-b (:=layers spec)]
                                  (merge-layers layer-a layer-b))
                 plot-props (merge (dissoc acc :=layers) (dissoc spec :=layers))]
             (assoc plot-props :=layers (vec product-layers)))

           ;; Only one has layers → merge
           (:=layers acc)
           (merge spec acc)

           (:=layers spec)
           (merge acc spec)

           ;; Neither has layers → merge
           :else
           (merge acc spec)))
       specs))))

;; Examples:
(kind/pprint
 (cross {:=layers [{:=columns [:a]}]}
        {:=layers [{:=columns [:b]}]}))

(kind/pprint
 (cross {:=layers [{:=columns [:a]} {:=columns [:c]}]}
        {:=layers [{:=columns [:b]} {:=columns [:d]}]}))

(defn blend
  "Concatenate layers from multiple specs.
  
  This is the fundamental + operation of the grammar.
  Layers are stacked for overlay visualization."
  [& specs]
  (let [all-layers (mapcat :=layers specs)
        plot-props (apply merge (map #(dissoc % :=layers) specs))]
    (assoc plot-props :=layers (vec all-layers))))

;; Examples:
(kind/pprint
 (blend {:=layers [{:=columns [:a]}]}
        {:=layers [{:=columns [:b]}]}))

(kind/pprint
 (blend {:=layers [{:=plottype :scatter}]}
        {:=layers [{:=plottype :line}]}))

(defn layer
  "Create a single layer spec from dataset and columns.
  
  Positional semantics: columns are stored in :=columns vector,
  roles will be assigned later by resolve-roles."
  [dataset & columns]
  {:=layers [(into {:=data dataset}
                   (when (seq columns)
                     {:=columns (vec columns)}))]})

;; Examples:
(kind/pprint (layer simple-data :x :y))

(kind/pprint (layer simple-data :x))

(defn layers
  "Create multiple layer specs (one per column).
  
  This is syntactic sugar: (layers dataset [:a :b :c])
  is equivalent to: (blend (layer dataset :a) (layer dataset :b) (layer dataset :c))"
  [dataset cols]
  (apply blend (map #(layer dataset %) cols)))

;; Examples:
(kind/pprint (layers simple-data [:x :y :z]))

(map :=columns (:=layers (layers simple-data [:x :y :z])))

;; ## Stage 2: Role Inference and Defaults
;;
;; Two steps: resolve-roles (inference) → apply-defaults (sensible defaults)

(defn resolve-roles
  "Resolve visual roles from :=columns positional provenance.
  
  Rules:
  - 1 column → :=x only
  - 2 columns → :=x and :=y
  - 3+ columns → error (ambiguous)
  
  Also detects diagonal (:=x = :=y) for later use by apply-defaults.
  Does NOT set :=plottype - that's apply-defaults' job."
  [spec]
  (let [layers (:=layers spec)

        ;; Process each layer
        processed-layers
        (for [layer layers]
          (if (or (:=x layer) (:=y layer))
            ;; Already has roles, just detect diagonal
            (let [x-col (:=x layer)
                  y-col (:=y layer)
                  diagonal? (and x-col y-col (= x-col y-col))]
              (assoc layer :=diagonal? diagonal?))
            ;; Assign from :=columns
            (let [cols (:=columns layer)]
              (case (count cols)
                0 layer ; No columns, pass through
                1 (assoc layer :=x (first cols))
                2 (let [x-col (first cols)
                        y-col (second cols)
                        diagonal? (= x-col y-col)]
                    (assoc layer
                           :=x x-col
                           :=y y-col
                           :=diagonal? diagonal?))
                ;; 3+ columns - error
                (throw (ex-info "Ambiguous column count; specify :=x/:=y explicitly"
                                {:columns cols :layer layer}))))))

        ;; Check if this is a grid layout (SPLOM)
        all-have-pairs? (every? #(= 2 (count (:=columns %))) layers)
        is-grid? (and (> (count layers) 1) all-have-pairs?)

        ;; If grid, assign grid positions
        final-layers
        (if is-grid?
          (let [x-vars (distinct (map #(first (:=columns %)) layers))
                y-vars (distinct (map #(second (:=columns %)) layers))
                x-index (into {} (map-indexed (fn [i v] [v i]) x-vars))
                y-index (into {} (map-indexed (fn [i v] [v i]) y-vars))]
            (map (fn [layer]
                   (let [x-col (:=x layer)
                         y-col (:=y layer)]
                     (assoc layer
                            :=grid-row (x-index x-col)
                            :=grid-col (y-index y-col))))
                 processed-layers))
          processed-layers)]

    (cond-> (assoc spec :=layers (vec final-layers))
      is-grid? (assoc :=layout :grid))))

;; Examples:
(kind/pprint (resolve-roles (layer simple-data :x :y)))

(map (fn [l] (select-keys l [:=x :=y :=diagonal?]))
     (:=layers (resolve-roles (layer simple-data :x :x))))

;; SPLOM example with grid positions:
(let [splom-spec (cross (layers simple-data [:x :z])
                        (layers simple-data [:x :y]))]
  (map (fn [l] (select-keys l [:=columns :=x :=y :=grid-row :=grid-col]))
       (:=layers (resolve-roles splom-spec))))

(defn apply-defaults
  "Apply sensible defaults based on layer structure.
  
  Idempotent: respects existing values, only fills in missing ones.
  
  Takes optional custom-defaults map:
  {:diagonal {:=plottype :histogram}
   :off-diagonal {:=plottype :scatter}}
  
  If not provided, uses built-in sensible defaults."
  ([spec] (apply-defaults spec nil))
  ([spec custom-defaults]
   (let [default-defaults {:diagonal {:=plottype :histogram}
                           :off-diagonal {:=plottype :scatter}}
         defaults (merge default-defaults custom-defaults)]
     (update spec :=layers
             (fn [layers]
               (mapv (fn [layer]
                       (let [diagonal? (:=diagonal? layer)
                             default-props (if diagonal?
                                             (:diagonal defaults)
                                             (:off-diagonal defaults))]
                         ;; Merge defaults, but existing values win
                         (merge default-props layer)))
                     layers))))))

;; Examples:
(kind/pprint
 (-> (layer simple-data :x :y)
     resolve-roles
     apply-defaults))

(kind/pprint
 (-> (layer simple-data :x :x)
     resolve-roles
     apply-defaults))

;; Custom defaults:
(kind/pprint
 (-> (layer simple-data :x :x)
     resolve-roles
     (apply-defaults {:diagonal {:=plottype :density}})))

;; Idempotency - respects existing values:
(kind/pprint
 (-> (layer simple-data :x :x)
     resolve-roles
     (update-in [:=layers 0] assoc :=plottype :custom)
     apply-defaults))

(defn when-diagonal
  "Apply properties to layers where :=diagonal? is true.
  
  Helper for clean conditional modification."
  [spec props]
  (update spec :=layers
          (fn [layers]
            (mapv (fn [layer]
                    (if (:=diagonal? layer)
                      (merge layer props)
                      layer))
                  layers))))

;; Example:
(kind/pprint
 (-> (cross (layers simple-data [:x :z])
            (layers simple-data [:x :y]))
     resolve-roles
     apply-defaults
     (when-diagonal {:=plottype :density})))

(defn when-off-diagonal
  "Apply properties to layers where :=diagonal? is false.
  
  Helper for clean conditional modification."
  [spec props]
  (update spec :=layers
          (fn [layers]
            (mapv (fn [layer]
                    (if-not (:=diagonal? layer)
                      (merge layer props)
                      layer))
                  layers))))

;; Example:
(kind/pprint
 (-> (cross (layers simple-data [:x :z])
            (layers simple-data [:x :y]))
     resolve-roles
     (when-diagonal {:=plottype :histogram})
     (when-off-diagonal {:=plottype :scatter})))
;; ## Stage 3: Grouping Spread (spread)
;;
;; Expand layers with :=color/:=facet into multiple partitioned layers.
;;
;; **Design:** Idempotent - safe to call multiple times.
;; Check for :=color-value/:=facet-value to detect already-spread layers.

(defn spread
  "Expand grouping aesthetics (:=color, :=facet) into multiple layers.
  
  For each layer with :=color or :=facet:
  - Partition data by unique values
  - Create one layer per group
  - Add :=color-value or :=facet-value metadata
  
  Idempotent: If layer already has :=color-value/:=facet-value, skip it."
  [spec]
  (let [layers (:=layers spec)
        expanded-layers (mapcat
                         (fn [layer]
                           ;; Check if already spread
                           (if (or (:=color-value layer)
                                   (:=facet-value layer))
                             ;; Already spread, return as-is
                             [layer]

                             ;; Check if needs spreading
                             (let [color-col (:=color layer)
                                   facet-col (:=facet layer)]
                               (cond
                                 ;; Has color grouping
                                 color-col
                                 (let [data (:=data layer)
                                       groups (-> data
                                                  (tc/group-by [color-col])
                                                  (tc/groups->map))]
                                   (for [[group-val group-data] groups]
                                     (-> layer
                                         (assoc :=color-value (get group-val color-col))
                                         (assoc :=data group-data))))

                                 ;; Has facet grouping
                                 facet-col
                                 (let [data (:=data layer)
                                       groups (-> data
                                                  (tc/group-by [facet-col])
                                                  (tc/groups->map))]
                                   (for [[group-val group-data] groups]
                                     (-> layer
                                         (assoc :=facet-value (get group-val facet-col))
                                         (assoc :=data group-data))))

                                 ;; No grouping needed
                                 :else
                                 [layer]))))
                         layers)]
    (assoc spec :=layers expanded-layers)))

;; ### Examples: Testing spread

;; Add color aesthetic and spread
(-> (layer simple-data :x :y)
    resolve-roles
    apply-defaults
    (update-in [:=layers 0] assoc :=color :color)
    spread
    :=layers
    count)
;; => 2 (one layer per color group)

;; Check the partitioned data
(-> (layer simple-data :x :y)
    resolve-roles
    apply-defaults
    (update-in [:=layers 0] assoc :=color :color)
    spread
    :=layers
    first
    :=data
    tc/row-count)
;; => 3 (group "a" has 3 rows)

;; Test idempotency - calling spread twice is safe
(-> (layer simple-data :x :y)
    resolve-roles
    apply-defaults
    (update-in [:=layers 0] assoc :=color :color)
    spread
    spread ; Called twice!
    :=layers
    count)
;; => Still 2 layers (not 4)

;; ## Stage 4: Rendering
;;
;; Generate SVG from spread spec.

(defn get-column-values
  "Extract all values from a column across multiple layers."
  [layers col-key data-key]
  (mapcat (fn [layer]
            (when-let [col (get layer col-key)]
              (-> layer :=data (tc/column col))))
          layers))

(defn compute-domain
  "Compute [min max] domain for a set of values."
  [values]
  (let [non-nil-values (filter some? values)]
    (when (seq non-nil-values)
      [(apply min non-nil-values)
       (apply max non-nil-values)])))
(defn make-scale
  "Create a linear scale function from domain to range."
  [domain range]
  (let [[d-min d-max] domain
        [r-min r-max] range
        d-span (- d-max d-min)
        r-span (- r-max r-min)]
    (fn [val]
      (+ r-min (* (/ (- val d-min) d-span) r-span)))))

;; Color palette for rendering
(defn get-color
  "Get color from theme palette by index."
  [index]
  (nth (:colors theme) (mod index (count (:colors theme))))) ; Red
(defn assign-color-indices
  "Assign color indices to layers based on unique :=color-value in sorted order.
  
  Returns spec with :=color-index added to each layer that has :=color-value."
  [spec]
  (let [layers (:=layers spec)
        ;; Get unique color values in sorted order
        color-values (->> layers
                          (keep :=color-value)
                          distinct
                          sort
                          vec)
        ;; Create value->index map
        value->index (into {} (map-indexed (fn [i v] [v i]) color-values))
        ;; Add :=color-index to layers
        indexed-layers (mapv (fn [layer]
                               (if-let [color-val (:=color-value layer)]
                                 (assoc layer :=color-index (value->index color-val))
                                 layer))
                             layers)]
    (assoc spec :=layers indexed-layers))) ; Blue

;; ## Transform System
;;
;; Statistical transformations applied to data before rendering.
;; Transforms dispatch on :=transform key (defaults to :identity).

(defmulti transform-data
  "Apply statistical transformation to layer data.
  
  Dispatches on :=transform key (defaults to :identity if not present).
  Returns layer with potentially modified :=data.
  
  Transforms are applied BEFORE geometry rendering, enabling:
  - Smooth curves (moving average)
  - Linear regression
  - Custom statistical transforms
  
  Users can extend by adding new methods:
  (defmethod transform-data :my-transform [layer] ...)"
  (fn [layer] (or (:=transform layer) :identity)))

;; Identity transform - pass through unchanged (default)
(defmethod transform-data :identity
  [layer]
  layer)

;; Moving average transform - smooths y values
(defmethod transform-data :smooth
  [layer]
  (let [data (:=data layer)
        x-col (:=x layer)
        y-col (:=y layer)
        window-size (get-in layer [:=transform-opts :window] 5)

        ;; Get x and y values, filtering out nils
        x-vals (vec (tc/column data x-col))
        y-vals (vec (tc/column data y-col))

        ;; Filter out nil pairs
        pairs (filter (fn [{:keys [x y]}] (and (some? x) (some? y)))
                      (map-indexed (fn [i x] {:i i :x x :y (nth y-vals i)}) x-vals))

        ;; Sort by x
        sorted-pairs (sort-by :x pairs)

        ;; Compute moving average for each point
        smoothed-pairs (map-indexed
                        (fn [idx {:keys [x y]}]
                          (let [start (max 0 (- idx (quot window-size 2)))
                                end (min (count sorted-pairs) (+ idx (quot window-size 2) 1))
                                window-ys (map :y (subvec (vec sorted-pairs) start end))
                                avg-y (/ (reduce + window-ys) (count window-ys))]
                            {:x x :y avg-y}))
                        sorted-pairs)

        ;; Create new dataset with smoothed values
        smoothed-data (tc/dataset {:x (map :x smoothed-pairs)
                                   :y (map :y smoothed-pairs)})]

    (assoc layer
           :=data smoothed-data
           :=x :x
           :=y :y)))

;; ## Geometry System
;;
;; Geometric marks for rendering transformed data.
;; Geometries dispatch on :=plottype key.

(defmulti render-geom
  "Render layer data as geometric marks.
  
  Dispatches on :=plottype key.
  Returns hiccup vector of SVG elements.
  
  Different geometries may take different scale parameters:
  - :scatter, :line → [layer x-scale y-scale]
  - :histogram → [layer x-scale height margin]
  
  Users can extend by adding new methods:
  (defmethod render-geom :my-geom [layer x-scale y-scale] ...)"
  (fn [layer & _] (:=plottype layer)))

;; Render scatter plot layer as circles
(defmethod render-geom :scatter
  [layer x-scale y-scale]
  (let [data (:=data layer)
        x-col (:=x layer)
        y-col (:=y layer)
        color-index (:=color-index layer)
        color (if color-index
                (get-color color-index)
                (:default-mark theme))
        point-size (:point-size theme)
        point-stroke (:point-stroke theme)]

    (for [i (range (tc/row-count data))
          :let [x-val (-> data (tc/column x-col) (nth i))
                y-val (-> data (tc/column y-col) (nth i))]
          :when (and (some? x-val) (some? y-val))]
      (let [cx (x-scale x-val)
            cy (y-scale y-val)]
        [:circle {:cx cx :cy cy :r point-size
                  :fill color
                  :stroke point-stroke
                  :stroke-width 0.5
                  :opacity 0.7}]))))

;; Keep old function as wrapper for backward compatibility
(defn render-scatter-layer
  "Legacy wrapper for render-geom :scatter.
  
  Prefer using render-geom multimethod directly."
  [layer x-scale y-scale]
  (render-geom layer x-scale y-scale))
;; Render histogram layer with bins
(defmethod render-geom :histogram
  [layer x-scale height margin]
  (let [data (:=data layer)
        x-col (:=x layer)
        values (tc/column data x-col)

        ;; Filter nil values
        non-nil-values (filter some? values)

        ;; Simple binning: 10 bins
        bins 10
        [min-val max-val] (compute-domain non-nil-values)
        bin-width (/ (- max-val min-val) bins)

        ;; Count values in each bin
        bin-counts (reduce
                    (fn [counts val]
                      (let [bin (int (min (dec bins)
                                          (/ (- val min-val) bin-width)))]
                        (update counts bin (fnil inc 0))))
                    {}
                    non-nil-values)

        max-count (apply max (vals bin-counts))
        y-scale (make-scale [0 max-count]
                            [(- height margin) margin])

        ;; Color: use color-index if present, otherwise default
        color-index (:=color-index layer)
        hist-fill (if color-index
                    (get-color color-index)
                    (:histogram-fill theme))
        hist-stroke (:histogram-stroke theme)
        hist-stroke-width (:histogram-stroke-width theme)]

    (for [bin (range bins)]
      (let [count (get bin-counts bin 0)
            x1 (x-scale (+ min-val (* bin bin-width)))
            x2 (x-scale (+ min-val (* (inc bin) bin-width)))
            y (y-scale count)
            bar-height (- (- height margin) y)]
        [:rect {:x x1
                :y y
                :width (- x2 x1)
                :height bar-height
                :fill hist-fill
                :stroke hist-stroke
                :stroke-width hist-stroke-width
                :opacity 0.7}]))))

;; Keep old function as wrapper for backward compatibility
(defn render-histogram-layer
  "Legacy wrapper for render-geom :histogram.
  
  Prefer using render-geom multimethod directly."
  [layer x-scale height margin]
  (render-geom layer x-scale height margin))

;; Render line geometry as connected polyline
(defmethod render-geom :line
  [layer x-scale y-scale]
  (let [data (:=data layer)
        x-col (:=x layer)
        y-col (:=y layer)
        color-index (:=color-index layer)
        color (if color-index
                (get-color color-index)
                (:line-stroke theme))
        line-width (get-in layer [:=line-width] (:line-width theme))

        ;; Get points and filter out nils
        points (for [i (range (tc/row-count data))]
                 (let [x-val (-> data (tc/column x-col) (nth i))
                       y-val (-> data (tc/column y-col) (nth i))]
                   (when (and (some? x-val) (some? y-val))
                     {:x x-val :y y-val})))
        valid-points (filter some? points)
        sorted-points (sort-by :x valid-points)

        ;; Create path data string
        path-data (clojure.string/join " "
                                       (map (fn [pt]
                                              (str (x-scale (:x pt)) "," (y-scale (:y pt))))
                                            sorted-points))]

    [[:polyline {:points path-data
                 :fill "none"
                 :stroke color
                 :stroke-width line-width
                 :opacity 0.9}]]))

(defn render-single-panel
  "Render a single panel with multiple layers.
  
  Pipeline: transform-data → render-geom → SVG"
  [layers width height margin]
  (let [;; Compute domains across all layers
        x-vals (get-column-values layers :=x :=data)
        y-vals (get-column-values layers :=y :=data)

        x-domain (compute-domain x-vals)
        y-domain (compute-domain y-vals)

        ;; Create scales
        x-scale (make-scale x-domain [margin (- width margin)])
        y-scale (make-scale y-domain [(- height margin) margin])

        ;; Transform and render each layer
        layer-shapes (mapcat (fn [layer]
                               ;; Apply statistical transform first
                               (let [transformed-layer (transform-data layer)]
                                 (case (:=plottype transformed-layer)
                                   :histogram (render-geom transformed-layer x-scale height margin)
                                   ;; Default: use x-scale y-scale
                                   (render-geom transformed-layer x-scale y-scale))))
                             layers)

        ;; Grid lines (5 vertical, 5 horizontal)
        [x-min x-max] x-domain
        [y-min y-max] y-domain
        x-step (/ (- x-max x-min) 5)
        y-step (/ (- y-max y-min) 5)

        grid-color (:grid theme)
        background (:background theme)]

    [:g
     ;; Background
     [:rect {:x 0 :y 0 :width width :height height :fill background}]

     ;; Grid lines - vertical
     (for [i (range 6)]
       (let [x-val (+ x-min (* i x-step))
             x-pos (x-scale x-val)]
         [:line {:x1 x-pos :y1 margin
                 :x2 x-pos :y2 (- height margin)
                 :stroke grid-color
                 :stroke-width 0.5}]))

     ;; Grid lines - horizontal
     (for [i (range 6)]
       (let [y-val (+ y-min (* i y-step))
             y-pos (y-scale y-val)]
         [:line {:x1 margin :y1 y-pos
                 :x2 (- width margin) :y2 y-pos
                 :stroke grid-color
                 :stroke-width 0.5}]))

     ;; Axes (drawn on top of grid)
     [:line {:x1 margin :y1 (- height margin)
             :x2 (- width margin) :y2 (- height margin)
             :stroke "black" :stroke-width 1}]
     [:line {:x1 margin :y1 margin
             :x2 margin :y2 (- height margin)
             :stroke "black" :stroke-width 1}]

     ;; Layer shapes (drawn on top)
     layer-shapes]))

(defn plot
  "Render a spec to SVG.
  
  Automatically calls resolve-roles, apply-defaults, and spread to prepare the spec.
  These operations are idempotent, so calling them explicitly before plot is safe.
  Assigns color indices based on sorted unique color values.
  Automatically detects grid layout from :=grid-row/:=grid-col metadata."
  [spec]
  (let [spec-prepared (-> spec
                          resolve-roles
                          apply-defaults
                          spread)
        spec-colored (assign-color-indices spec-prepared)
        layers (:=layers spec-colored)

        ;; Detect if this is a grid layout
        has-grid? (some :=grid-row layers)]

    (if has-grid?
      ;; Grid layout: multiple panels arranged spatially
      (let [;; Group layers by grid position
            grid-map (group-by (juxt :=grid-row :=grid-col) layers)

            ;; Compute grid dimensions
            grid-rows (keep :=grid-row layers)
            grid-cols (keep :=grid-col layers)
            max-row (if (seq grid-rows) (apply max grid-rows) 0)
            max-col (if (seq grid-cols) (apply max grid-cols) 0)

            ;; Panel dimensions
            panel-width 150
            panel-height 150
            panel-margin 20

            ;; Total SVG dimensions
            total-width (* (inc max-col) panel-width)
            total-height (* (inc max-row) panel-height)

            ;; Theme
            background (:background theme)]

        (kind/hiccup
         [:svg {:width total-width :height total-height
                :xmlns "http://www.w3.org/2000/svg"}
          ;; Background
          [:rect {:x 0 :y 0 :width total-width :height total-height
                  :fill background}]

          ;; Render each panel
          (for [row (range (inc max-row))
                col (range (inc max-col))]
            (when-let [panel-layers (get grid-map [row col])]
              (let [x-offset (* col panel-width)
                    y-offset (* row panel-height)
                    panel-svg (render-single-panel panel-layers
                                                   panel-width
                                                   panel-height
                                                   panel-margin)]
                [:g {:transform (str "translate(" x-offset "," y-offset ")")}
                 panel-svg])))]))

      ;; Single panel: all layers overlay in one coordinate system
      (let [width 400
            height 300
            margin 40
            svg-content (render-single-panel layers width height margin)]

        (kind/hiccup
         [:svg {:width width :height height
                :xmlns "http://www.w3.org/2000/svg"}
          svg-content])))))

;; # Complete Examples & Tests
;;
;; Comprehensive examples demonstrating all functionality

;; ## Stage 1: Algebraic Operations
;; ### merge-layers - concatenate :=columns vectors
(kind/pprint
 (merge-layers {:=columns [:a]} {:=columns [:b]}))

(kind/pprint
 (merge-layers {:=columns [:a] :=data "dataset1"}
               {:=columns [:b] :=data "dataset2"}))

;; ### cross - Cartesian product

;; Simple 2x2 cross
(kind/pprint
 (cross {:=layers [{:=columns [:a]}]}
        {:=layers [{:=columns [:b]}]}))

;; 2x2 grid
(kind/pprint
 (cross {:=layers [{:=columns [:a]} {:=columns [:c]}]}
        {:=layers [{:=columns [:b]} {:=columns [:d]}]}))

;; Real SPLOM structure
(kind/pprint
 (cross (layers simple-data [:x :z])
        (layers simple-data [:x :y])))

;; ### blend - Concatenate layers (overlay)
(kind/pprint
 (blend {:=layers [{:=columns [:a]}]}
        {:=layers [{:=columns [:b]}]}))

;; Overlay different plot types
(kind/pprint
 (blend {:=layers [{:=plottype :scatter}]}
        {:=layers [{:=plottype :line}]}))

;; ### layer - Create single layer
(kind/pprint (layer simple-data :x :y))

(kind/pprint (layer simple-data :x))

;; ### layers - Create multiple layers (syntactic sugar)
(kind/pprint (layers simple-data [:x :y :z]))

;; Equivalent to:
(kind/pprint
 (blend (layer simple-data :x)
        (layer simple-data :y)
        (layer simple-data :z)))

;; ## Stage 2: Role Inference & Defaults

;; ### resolve-roles - Positional → Named roles

;; Off-diagonal (x ≠ y)
(-> (layer simple-data :x :y)
    resolve-roles
    :=layers first
    (select-keys [:=x :=y :=diagonal? :=columns]))

;; Diagonal (x = y)
(-> (layer simple-data :x :x)
    resolve-roles
    :=layers first
    (select-keys [:=x :=y :=diagonal?]))

;; SPLOM grid structure
(-> (cross (layers simple-data [:x :z])
           (layers simple-data [:x :y]))
    resolve-roles
    :=layers
    (->> (map #(select-keys % [:=x :=y :=grid-row :=grid-col]))))

;; ### apply-defaults - Structure-based defaults

;; Off-diagonal → scatter
(-> (layer simple-data :x :y)
    resolve-roles
    apply-defaults
    :=layers first
    (select-keys [:=plottype :=diagonal?]))

;; Diagonal → histogram
(-> (layer simple-data :x :x)
    resolve-roles
    apply-defaults
    :=layers first
    (select-keys [:=plottype :=diagonal?]))

;; Custom defaults
(-> (layer simple-data :x :x)
    resolve-roles
    (apply-defaults {:diagonal {:=plottype :density}})
    :=layers first :=plottype)

;; Idempotent - respects existing values
(-> (layer simple-data :x :x)
    resolve-roles
    (update-in [:=layers 0] assoc :=plottype :custom)
    apply-defaults
    :=layers first :=plottype)

;; ### when-diagonal & when-off-diagonal - Conditional transforms

;; Apply to diagonal only
(-> (cross (layers simple-data [:x :z])
           (layers simple-data [:x :y]))
    resolve-roles
    apply-defaults
    (when-diagonal {:=plottype :density})
    :=layers
    (->> (map #(select-keys % [:=x :=y :=plottype :=diagonal?]))))

;; Apply to off-diagonal only
(-> (cross (layers simple-data [:x :z])
           (layers simple-data [:x :y]))
    resolve-roles
    (when-diagonal {:=plottype :histogram})
    (when-off-diagonal {:=plottype :scatter})
    :=layers
    (->> (map #(select-keys % [:=plottype :=diagonal?]))))

;; ## Stage 3: Grouping Spread

;; ### spread - Expand grouping aesthetics

;; Add color grouping and spread
(-> (layer simple-data :x :y)
    resolve-roles
    apply-defaults
    (update-in [:=layers 0] assoc :=color :color)
    spread
    :=layers
    (->> (map #(select-keys % [:=color-value :=data]))
         (map #(assoc % :row-count (tc/row-count (:=data %))))
         (map #(dissoc % :=data))))

;; Idempotency test
(= (-> (layer simple-data :x :y)
       resolve-roles
       apply-defaults
       (update-in [:=layers 0] assoc :=color :color)
       spread)
   (-> (layer simple-data :x :y)
       resolve-roles
       apply-defaults
       (update-in [:=layers 0] assoc :=color :color)
       spread
       spread))

;; ## Stage 4: Rendering

;; ### plot - Complete pipeline

;; Simple scatter
(-> (layer simple-data :x :y)
    resolve-roles
    apply-defaults
    plot)

;; Scatter with color groups
(-> (layer simple-data :x :y)
    resolve-roles
    apply-defaults
    (update-in [:=layers 0] assoc :=color :color)
    plot)

;; Histogram
(-> (layer simple-data :x :x)
    resolve-roles
    apply-defaults
    plot)

;; Complete pipeline made explicit
(-> (layer simple-data :x :y)
    ;; After layer: positional :=columns
    resolve-roles
    ;; After resolve-roles: named :=x/:=y, :=diagonal? detected
    apply-defaults
    ;; After apply-defaults: :=plottype set based on structure
    (update-in [:=layers 0] assoc :=color :color)
    ;; After adding aesthetic: marker for grouping
    spread
    ;; After spread: multiple layers, data partitioned
    plot)
    ;; Final: SVG rendering

;; # Real Dataset Examples

;; Load penguins dataset (344 rows, 8 columns)
(def penguins
  (tc/dataset "https://raw.githubusercontent.com/allisonhorst/palmerpenguins/master/inst/extdata/penguins.csv" {:key-fn keyword}))

;; ## Scatter plot with real data

;; Simple scatter: bill length vs bill depth
(-> (layer penguins :bill_length_mm :bill_depth_mm)
    resolve-roles
    apply-defaults
    plot)

;; Scatter with color grouping by species (Adelie, Gentoo, Chinstrap)
(-> (layer penguins :bill_length_mm :bill_depth_mm)
    resolve-roles
    apply-defaults
    (update-in [:=layers 0] assoc :=color :species)
    plot)

;; ## Histogram with real data

;; Distribution of bill lengths
(-> (layer penguins :bill_length_mm :bill_length_mm)
    resolve-roles
    apply-defaults
    plot)

;; Distribution of body mass
(-> (layer penguins :body_mass_g :body_mass_g)
    resolve-roles
    apply-defaults
    plot)

;; ## SPLOM (Scatterplot Matrix) Examples

;; Simple 2×2 SPLOM with synthetic data
(-> (cross (layers simple-data [:x :z])
           (layers simple-data [:x :y]))
    resolve-roles
    apply-defaults
    plot)

;; Full 3×3 SPLOM with penguins dataset
;; Diagonal: histograms showing distributions
;; Off-diagonal: scatter plots showing relationships
(-> (cross (layers penguins [:bill_length_mm :bill_depth_mm :flipper_length_mm])
           (layers penguins [:bill_length_mm :bill_depth_mm :flipper_length_mm]))
    resolve-roles
    apply-defaults
    plot)

;; Custom SPLOM with different diagonal geometry
(-> (cross (layers penguins [:bill_length_mm :bill_depth_mm])
           (layers penguins [:bill_length_mm :bill_depth_mm]))
    resolve-roles
    (when-diagonal {:=plottype :histogram})
    (when-off-diagonal {:=plottype :scatter})
    plot)

;; ## Color Grouping Examples

;; ### Basic color grouping
;; Adding :=color creates groups - one layer per unique value

(-> (layer penguins :bill_length_mm :bill_depth_mm)
    resolve-roles
    apply-defaults
    (assoc-in [:=layers 0 :=color] :species)
    plot)

;; ### Inspect what spread does with color
;; Before spread: 1 layer with :=color
(def before-spread
  (-> (layer penguins :bill_length_mm :bill_depth_mm)
      resolve-roles
      apply-defaults
      (assoc-in [:=layers 0 :=color] :species)))

(kind/pprint
 {:layer-count (count (:=layers before-spread))
  :has-color (get-in before-spread [:=layers 0 :=color])})

;; After spread: 3 layers, one per species
(def after-spread (spread before-spread))

(kind/pprint
 {:layer-count (count (:=layers after-spread))
  :color-values (map :=color-value (:=layers after-spread))})

;; Render the spread result
(plot after-spread)

;; ### Color with different plot types

;; Colored lines
(-> (layer penguins :bill_length_mm :bill_depth_mm)
    resolve-roles
    apply-defaults
    (assoc-in [:=layers 0 :=color] :species)
    (assoc-in [:=layers 0 :=plottype] :line)
    plot)

;; Colored histograms (diagonal pattern)
(-> (layer penguins :bill_length_mm :bill_length_mm)
    resolve-roles
    apply-defaults
    (assoc-in [:=layers 0 :=color] :species)
    plot)

;; ### Layering with color

;; Scatter + smooth, both colored by species
(-> (blend
     (layer penguins :bill_length_mm :bill_depth_mm)
     (-> (layer penguins :bill_length_mm :bill_depth_mm)
         (assoc-in [:=layers 0 :=transform] :smooth)
         (assoc-in [:=layers 0 :=plottype] :line)))
    resolve-roles
    apply-defaults
    ;; Add color to both layers
    (update-in [:=layers 0] assoc :=color :species)
    (update-in [:=layers 1] assoc :=color :species)
    plot)

;; ### How colors are assigned
;; Colors are assigned by sorted unique values with indices

(def color-example
  (-> (layer penguins :bill_length_mm :bill_depth_mm)
      resolve-roles
      apply-defaults
      (assoc-in [:=layers 0 :=color] :species)
      spread))

;; Species sorted alphabetically, assigned color indices
(kind/pprint
 (map #(select-keys % [:=color-value :=color-index])
      (:=layers color-example)))

;; ### Multiple color groups in one plot
;; Each layer can have different color groupings

(-> (blend
      ;; Layer 1: Colored by species
     (-> (layer penguins :bill_length_mm :bill_depth_mm)
         (assoc-in [:=layers 0 :=color] :species))
      ;; Layer 2: Colored by island (will get different colors)
     (-> (layer penguins :flipper_length_mm :body_mass_g)
         (assoc-in [:=layers 0 :=color] :island)))
    resolve-roles
    apply-defaults
    plot)


;; # Understanding the Pipeline: Step-by-Step Transformations

;; This section demonstrates how the 4-stage pipeline gradually transforms
;; specifications from high-level descriptions to rendered SVG, and how you
;; can intervene at each stage for fine-grained control.

;; ## Example 1: Basic scatter plot transformation

;; Start with a simple layer
(def base-spec
  (layer penguins :bill_length_mm :bill_depth_mm))

;; Inspect the initial IR
(kind/pprint base-spec)

;; ### Stage 1 → Stage 2: Role resolution
;; The resolve-roles function infers x/y roles from column positions
(def resolved-spec (resolve-roles base-spec))

(kind/pprint resolved-spec)

;; ### Stage 2 → Stage 3: Apply defaults
;; The apply-defaults function fills in plottype, scales, dimensions
(def defaulted-spec (apply-defaults resolved-spec))

(kind/pprint defaulted-spec)

;; ### Stage 3 → Stage 4: Spread (no-op here, no grouping)
;; The spread function expands color/facet groupings (none here)
(def spread-spec (spread defaulted-spec))

(kind/pprint spread-spec)

;; ### Stage 4: Render to SVG
(plot spread-spec)

;; ## Example 2: Intervening between stages

;; ### Intervention after resolve-roles: Change plottype
(-> base-spec
    resolve-roles
    (assoc-in [:=layers 0 :=plottype] :line)
    apply-defaults
    plot)

;; ### Intervention after apply-defaults: Override scale domain
(-> base-spec
    resolve-roles
    apply-defaults
    (assoc-in [:=scales :x :domain] [30 60])
    (assoc-in [:=scales :y :domain] [10 25])
    plot)

;; ### Intervention after apply-defaults: Change theme colors
(-> base-spec
    resolve-roles
    apply-defaults
    (assoc :=theme (assoc theme
                          :background-fill "#FFF8DC"
                          :point-fill "#8B4513"))
    plot)

;; ## Example 3: Color grouping transformation

;; Start with a colored layer
(def color-spec
  (-> (layer penguins :bill_length_mm :bill_depth_mm)
      (assoc-in [:=layers 0 :=color] :species)))

;; Before spread: single layer with :=color annotation
(def before-spread
  (-> color-spec
      resolve-roles
      apply-defaults))

(kind/pprint
 (select-keys (first (:=layers before-spread))
              [:=columns :=color :=plottype]))

;; After spread: multiple layers, one per species
(def after-spread (spread before-spread))

(kind/pprint
 (map #(select-keys % [:=columns :=color-value :=color-index :=plottype])
      (:=layers after-spread)))

;; Render the spread layers
(plot after-spread)

;; ### Intervention after spread: Modify individual color layers
;; Change the second species (Chinstrap) to use line geometry
(-> color-spec
    resolve-roles
    apply-defaults
    spread
    (update :=layers
            (fn [layers]
              (map-indexed
               (fn [i layer]
                 (if (= i 1)
                   (assoc layer :=plottype :line)
                   layer))
               layers)))
    plot)



;; ## Example 5: Faceting transformation

;; Create a faceted plot
(def facet-spec
  (-> (layer penguins :bill_length_mm :bill_depth_mm)
      (assoc-in [:=layers 0 :=facet] :island)))

;; Before spread: single layer with facet annotation
(def facet-before
  (-> facet-spec
      resolve-roles
      apply-defaults))

(kind/pprint
 (select-keys (first (:=layers facet-before))
              [:=columns :=facet]))

;; After spread: multiple layers, one per island
(def facet-after (spread facet-before))

(kind/pprint
 (map #(select-keys % [:=facet-value :=facet-index])
      (:=layers facet-after)))

;; Render faceted plot
(plot facet-after)

;; ### Intervention: Combine color and facet
(-> (layer penguins :bill_length_mm :bill_depth_mm)
    (assoc-in [:=layers 0 :=color] :species)
    (assoc-in [:=layers 0 :=facet] :island)
    resolve-roles
    apply-defaults
    spread
    plot)

;; ## Example 6: Cross (SPLOM) transformation

;; Create a scatter plot matrix
(def splom-spec
  (cross (layers penguins [:bill_length_mm :bill_depth_mm])
         (layers penguins [:bill_length_mm :flipper_length_mm])))

;; Initial state: cross product structure
(kind/pprint
 (map #(select-keys % [:=columns])
      (:=layers splom-spec)))

;; After resolution: roles inferred
(def splom-resolved (resolve-roles splom-spec))

(kind/pprint
 (map #(select-keys % [:=columns :=x :=y])
      (:=layers splom-resolved)))

;; Render the SPLOM
(plot splom-resolved)

;; ### Intervention: Add color to entire SPLOM
(-> splom-spec
    resolve-roles
    apply-defaults
    (update :=layers
            (fn [layers]
              (map #(assoc % :=color :species) layers)))
    spread
    plot)

;; ## Key Takeaways

;; 1. **Idempotent operations**: You can call resolve-roles, apply-defaults,
;;    and spread multiple times safely. The plot function calls them automatically.

;; 2. **Intervention points**:
;;    - After resolve-roles: Change inferred roles or plottypes
;;    - After apply-defaults: Override scales, themes, dimensions
;;    - After spread: Modify individual group layers

;; 3. **IR inspection**: Use kind/pprint to see the intermediate representation
;;    at any stage and understand what transformations are happening.

;; 4. **Composability**: Algebraic operations (layer, blend, cross) create
;;    initial structure. Prep functions (resolve-roles, apply-defaults, spread)
;;    enrich it. Render function (plot) produces SVG.
