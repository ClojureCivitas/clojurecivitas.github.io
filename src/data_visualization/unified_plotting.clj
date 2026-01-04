^{:kindly/hide-code true
  :clay {:title "A Unified Approach to Data Visualization in Clojure"
         :quarto {:type :post
                  :author [:daslu]
                  :date "2026-01-05"
                  :description "Combining the Grammar of Graphics algebra with compositional layer construction"
                  :category :data-visualization
                  :tags [:datavis :splom :brushing]
                  :keywords [:datavis :grammar-of-graphics :algebra-of-graphics]
                  :toc true
                  :toc-depth 4
                  :toc-expand 4
                  :image "unified_splom.png"
                  :draft true}}}
(ns data-visualization.unified-plotting)

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

;; # A Unified Approach to Data Visualization
;;
;; This notebook explores a unified approach that combines two powerful ideas:
;;
;; 1. **The Grammar of Graphics** - Algebraic operations on data (cross, blend, nest)
;; 2. **Algebra of Graphics** - Compositional layer construction with `=*` and `=+`
;;
;; The key insight: **structure can determine geometry**. When you cross a variable with itself
;; (like sepal-length × sepal-length), you're looking at the *distribution* of one variable.
;; When you cross different variables, you're *comparing* them. The algebra tells us what
;; kind of comparison we're making, which suggests sensible geometric defaults.
;;
;; Let's see where this leads.

;; ## What We're Building Toward
;;
;; Here's a scatterplot matrix (SPLOM) showing relationships between four variables in the Iris dataset.
;; The diagonal shows distributions (histograms), while off-diagonal panels show comparisons (scatterplots).
;; Notice that we can express this entire visualization quite tersely:

(comment
  ;; This is where we're headed - don't worry if it's not clear yet
  (-> iris
      (cross [:sepal-length :sepal-width :petal-length :petal-width])
      (plot)))

;; The `cross` operation creates a 4×4 grid of all variable combinations. The system notices that
;; diagonal panels have the same variable on both axes (x=y), so it renders them as histograms.
;; Off-diagonal panels have different variables (x≠y), so they become scatterplots.
;;
;; We can override these defaults for richer visualizations:

(comment
  (-> iris
      (cross [:sepal-length :sepal-width :petal-length :petal-width])
      (diagonal (=+ histogram density))        ;; Overlay histogram + density curve
      (off-diagonal (=+ scatter linear))       ;; Overlay scatter + regression line
      (plot)))

;; And the finale - a brushable SPLOM where selecting observations in one panel highlights
;; them across all panels:

(comment
  (-> iris
      (cross [:sepal-length :sepal-width :petal-length :petal-width])
      (brushable true)
      (plot)))

;; Let's build up to this step by step.

;; ## Setup

(ns data-visualization.unified-plotting
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

   ;; Toydata - Example datasets
   [scicloj.metamorph.ml.toydata :as toydata]))

;; ## The Dataset
;;
;; We'll use the classic Iris dataset throughout. It has 150 observations of iris flowers,
;; with measurements of sepal and petal dimensions, plus the species (setosa, versicolor, virginica).

(def iris
  (-> (toydata/iris-ds)
      (tc/rename-columns {:sepal_length :sepal-length
                          :sepal_width :sepal-width
                          :petal_length :petal-length
                          :petal_width :petal-width
                          :species :species})))

;; Let's peek at the data:

(-> iris
    (tc/head 5)
    kind/table)

;; Four numeric columns (sepal-length, sepal-width, petal-length, petal-width) and one categorical
;; column (species). Perfect for exploring multi-dimensional relationships.

;; ## Working with Variables and Data
;;
;; The foundation of our approach is the **varset** - a projection of observations onto a variable.
;; Think of it as asking: "what values does this variable take, and which observations have each value?"

(defn varset
  "Create a varset from a dataset column.

  A varset is a map with:
  - :variable - the column name
  - :dataset - reference to the data
  - :indices - observation indices (always [0 1 2 ... n-1])

  This represents a projection that we can combine algebraically."
  [dataset variable]
  {:variable variable
   :dataset dataset
   :indices (range (tc/row-count dataset))})

;; Let's create varsets for sepal length and width:

(def sepal-length (varset iris :sepal-length))
(def sepal-width (varset iris :sepal-width))

;; A varset is just a map describing which variable we're looking at:

(kind/pprint sepal-length)

;; The `:indices` field is crucial - it tracks observation identity. All varsets from the same
;; dataset share the same index space (0 through 149 for Iris). This enables linked brushing later.

;; ## Crossing Variables
;;
;; The fundamental operation is **cross** (×) - creating tuples from two varsets.
;; When we cross sepal-length with sepal-width, we're asking: "show me the relationship
;; between these two dimensions."

(defn cross
  "Cross two varsets or blends to create a grid of alternatives.

  For simple varsets: creates a single layer with both variables.
  For blends: applies the distributive law to create all combinations.

  This is where the magic happens - structure determines geometry."
  [vs-x vs-y]
  (cond
    ;; Simple case: two varsets → one layer
    (and (:variable vs-x) (:variable vs-y))
    (let [x-var (:variable vs-x)
          y-var (:variable vs-y)
          diagonal? (= x-var y-var)]
      {:=layers [{:=x x-var
                  :=y y-var
                  :=data (:dataset vs-x)
                  :=diagonal? diagonal?
                  ;; Phase 1 smart default: diagonal → histogram
                  :=plottype (if diagonal? :histogram :scatter)}]
       :=data (:dataset vs-x)
       :=indices (:indices vs-x)})

    ;; TODO: Handle blends (multiple alternatives)
    :else
    (throw (ex-info "Blend crossing not yet implemented"
                    {:vs-x vs-x :vs-y vs-y}))))

;; Let's cross sepal-length with sepal-width:

(def length-vs-width
  (cross sepal-length sepal-width))

;; This creates a plot specification with a single layer. Notice the structure:

(kind/pprint length-vs-width)

;; The `:=plottype` is `:scatter` because these are different variables (x≠y).
;; The spec includes `:=data` (the dataset) and `:=indices` (tracking observations).

;; Now let's cross sepal-length with *itself*:

(def length-distribution
  (cross sepal-length sepal-length))

(kind/pprint length-distribution)

;; Notice `:=plottype :histogram` - the system detected `x=y` (diagonal) and chose histogram.
;; This is our first smart default in action.

;; ## A Simple 2×2 Grid
;;
;; Before we build the full machinery, let's manually construct a 2×2 grid to see the pattern.
;; We'll cross sepal-length and sepal-width with themselves:

(defn manual-grid
  "Manually construct a 2×2 grid for pedagogical purposes.
  Shows what cross-product of variables looks like."
  [dataset var-x var-y]
  (let [vs-x (varset dataset var-x)
        vs-y (varset dataset var-y)]
    {:=layers
     [;; Row 0, Col 0: x × x (diagonal)
      {:=x var-x :=y var-x :=grid-row 0 :=grid-col 0
       :=plottype :histogram :=data dataset}

      ;; Row 0, Col 1: x × y (off-diagonal)
      {:=x var-x :=y var-y :=grid-row 0 :=grid-col 1
       :=plottype :scatter :=data dataset}

      ;; Row 1, Col 0: y × x (off-diagonal)
      {:=x var-y :=y var-x :=grid-row 1 :=grid-col 0
       :=plottype :scatter :=data dataset}

      ;; Row 1, Col 1: y × y (diagonal)
      {:=x var-y :=y var-y :=grid-row 1 :=grid-col 1
       :=plottype :histogram :=data dataset}]

     :=data dataset
     :=indices (range (tc/row-count dataset))
     :=layout :grid}))

(def grid-2x2
  (manual-grid iris :sepal-length :sepal-width))

;; Let's examine the structure:

(kind/pprint grid-2x2)

;; Four layers arranged in a 2×2 grid:
;; - `:=grid-row` and `:=grid-col` specify position
;; - Diagonal layers (row=col) have `:=plottype :histogram`
;; - Off-diagonal layers have `:=plottype :scatter`
;;
;; The `:=layout :grid` tells the renderer to arrange these spatially.

;; ## Composition Operators
;;
;; Before we can render, we need composition operators. These let us build complex
;; specifications from simple pieces.

(defn =*
  "Merge/compose specs (cross-product of layers).

  When both specs have layers, creates cross-product.
  Otherwise merges plot-level properties.

  This is the 'and' operator - combining constraints."
  [& specs]
  (let [specs (remove nil? specs)]
    (if (= 1 (count specs))
      (first specs)
      (reduce
       (fn [acc spec]
         (cond
           ;; Both have layers → cross-product
           (and (:=layers acc) (:=layers spec))
           (update acc :=layers
                   (fn [layers]
                     (for [layer1 layers
                           layer2 (:=layers spec)]
                       (merge layer1 layer2))))

           ;; Only one has layers → merge properties
           (:=layers acc)
           (merge spec acc)

           (:=layers spec)
           (merge acc spec)

           ;; Neither has layers → simple merge
           :else
           (merge acc spec)))
       specs))))

(defn =+
  "Overlay/concatenate specs (sum of layers).

  Layers from second spec inherit properties from first.
  This is the 'or' operator - alternatives/overlays."
  [& specs]
  (let [specs (remove nil? specs)
        base (first specs)
        rest-specs (rest specs)]
    (if (empty? rest-specs)
      base
      (reduce
       (fn [acc spec]
         (let [base-layer (first (:=layers acc))
               new-layers (for [layer (:=layers spec)]
                           (merge base-layer layer))]
           (-> acc
               (update :=layers concat new-layers)
               (merge (dissoc spec :=layers)))))
       base
       rest-specs))))

;; These operators let us compose specifications:
;; - `=*` combines constraints (merge layers via cross-product)
;; - `=+` creates alternatives (concatenate layers with inheritance)
;;
;; We'll see them in action shortly.

;; ## Rendering to SVG
;;
;; Now we need to render our specifications to actual graphics. We'll use thi.ng/geom-viz
;; for SVG output, which gives us precise control and works well with D3 for interactivity.

;; First, some rendering infrastructure:

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
   :point-size 2
   :line-stroke "#333333"
   :line-width 1.5
   :histogram-fill "#333333"
   :histogram-stroke "#FFFFFF"
   :histogram-stroke-width 0.5})

(defn- infer-domain
  "Infer min/max domain from values."
  [values]
  (when (seq values)
    [(reduce min values)
     (reduce max values)]))

(defn- get-column-values
  "Extract values from a dataset column."
  [dataset column]
  (vec (get dataset column)))

(defn- layer->points
  "Extract [x y] points from a layer.
  Returns vector of [x-val y-val] pairs."
  [layer]
  (let [dataset (:=data layer)
        x-col (:=x layer)
        y-col (:=y layer)
        x-vals (get-column-values dataset x-col)
        y-vals (get-column-values dataset y-col)]
    (mapv vector x-vals y-vals)))

;; ## Rendering Scatter Plots
;;
;; Let's start with the simplest geometry: scatter plots. A scatter plot is just points
;; positioned by their x and y coordinates.

(defn- render-scatter
  "Render scatter plot points to geom.viz spec.

  Returns a viz data map for svg-plot2d-cartesian."
  [layer points x-scale y-scale]
  (let [point-fill (:point-fill theme)
        point-stroke (:point-stroke theme)]
    {:values points
     :layout viz/svg-scatter-plot
     :attribs {:fill point-fill
               :stroke point-stroke
               :stroke-width 0.5
               :opacity 1.0}}))

;; ## Rendering Histograms
;;
;; Histograms show the distribution of a single variable by binning values and counting
;; occurrences in each bin. For diagonal panels (x=y), we only use the x values.

(defn- compute-histogram-bins
  "Compute histogram bins using Sturges' formula for bin count.

  Returns vector of {:bin-start :bin-end :count} maps."
  [values]
  (let [n (count values)
        ;; Sturges' formula: k = ceil(log2(n) + 1)
        k (Math/ceil (+ (Math/log (double n)) 1.0))
        bin-count (max 5 (int k))
        [min-val max-val] (infer-domain values)
        bin-width (/ (- max-val min-val) bin-count)]
    (when (pos? bin-width)
      (let [bins (for [i (range bin-count)]
                   (let [bin-start (+ min-val (* i bin-width))
                         bin-end (+ min-val (* (inc i) bin-width))]
                     {:bin-start bin-start
                      :bin-end bin-end
                      :count 0}))]
        ;; Count values in each bin
        (reduce
         (fn [bins val]
           (let [bin-idx (min (dec bin-count)
                             (int (/ (- val min-val) bin-width)))]
             (update-in bins [bin-idx :count] inc)))
         (vec bins)
         values)))))

(defn- render-histogram
  "Render histogram bars to geom.viz spec.

  For diagonal panels, only uses x values (y values are the same)."
  [layer points x-scale y-scale]
  (let [x-vals (map first points)
        bins (compute-histogram-bins x-vals)
        max-count (apply max (map :count bins))
        histogram-fill (:histogram-fill theme)
        histogram-stroke (:histogram-stroke theme)]
    {:bars bins
     :max-count max-count
     :fill histogram-fill
     :stroke histogram-stroke}))

;; ## Rendering Linear Regression
;;
;; Linear regression fits a line through the points using least squares.
;; We'll compute the slope and intercept, then render the fitted line.

(defn- compute-linear-regression
  "Compute linear regression (slope and intercept) from points.

  Returns {:slope m :intercept b} or nil if not enough variation."
  [points]
  (when (> (count points) 1)
    (let [xs (map first points)
          ys (map second points)
          n (count points)
          sum-x (reduce + xs)
          sum-y (reduce + ys)
          sum-xy (reduce + (map * xs ys))
          sum-x2 (reduce + (map #(* % %) xs))
          mean-x (/ sum-x n)
          mean-y (/ sum-y n)
          ;; Calculate slope: m = (n*Σxy - Σx*Σy) / (n*Σx² - (Σx)²)
          numerator (- (* n sum-xy) (* sum-x sum-y))
          denominator (- (* n sum-x2) (* sum-x sum-x))]
      (when (> (Math/abs denominator) 1e-10)
        (let [slope (/ numerator denominator)
              intercept (- mean-y (* slope mean-x))]
          {:slope slope
           :intercept intercept
           :x-min (reduce min xs)
           :x-max (reduce max xs)})))))

(defn- render-linear
  "Render linear regression line to geom.viz spec."
  [layer points x-scale y-scale]
  (when-let [regression (compute-linear-regression points)]
    (let [{:keys [slope intercept x-min x-max]} regression
          y-start (+ (* slope x-min) intercept)
          y-end (+ (* slope x-max) intercept)
          line-stroke (:line-stroke theme)]
      {:values [[x-min y-start] [x-max y-end]]
       :layout viz/svg-line-plot
       :attribs {:stroke line-stroke
                 :stroke-width 2
                 :opacity 1.0}})))

;; ## Coordinating the Rendering
;;
;; Now we need to dispatch to the right renderer based on plottype, and coordinate
;; the overall rendering process.

(defmulti render-layer
  "Render a single layer based on its plottype.

  Returns geom.viz spec elements."
  (fn [layer points x-scale y-scale] (:=plottype layer)))

(defmethod render-layer :scatter
  [layer points x-scale y-scale]
  (render-scatter layer points x-scale y-scale))

(defmethod render-layer :histogram
  [layer points x-scale y-scale]
  (render-histogram layer points x-scale y-scale))

(defmethod render-layer :linear
  [layer points x-scale y-scale]
  (render-linear layer points x-scale y-scale))

(defn- render-single-panel
  "Render a single panel (one or more overlaid layers) to SVG.

  All layers in the panel share the same domain and coordinate system."
  [layers width height]
  (let [;; Check if we have histogram layers (they need special y-domain handling)
        has-histogram? (some #(= (:=plottype %) :histogram) layers)

        ;; Extract all points to compute domains
        all-points (mapcat layer->points layers)
        x-vals (map first all-points)
        y-vals (if has-histogram?
                 ;; For histograms, y domain should be [0, max-count]
                 ;; We'll compute this from the histogram bars
                 []
                 (map second all-points))
        [x-min x-max] (infer-domain x-vals)
        [y-min y-max] (if has-histogram?
                        [0 0] ; Will be updated after rendering histogram
                        (infer-domain y-vals))

        ;; Add 5% padding to domains
        x-padding (* 0.05 (- x-max x-min))
        y-padding (* 0.05 (- y-max y-min))
        x-domain [(- x-min x-padding) (+ x-max x-padding)]
        y-domain [(- y-min y-padding) (+ y-max y-padding)]

        ;; Calculate ranges based on margins
        margin 40
        x-range [margin (- width margin)]
        y-range [(- height margin) margin]

        ;; Render each layer
        rendered-layers (for [layer layers]
                         (let [points (layer->points layer)]
                           (render-layer layer points nil nil)))

        ;; Filter out nils and separate histogram bars from viz layers
        rendered-layers (remove nil? rendered-layers)
        histogram-layers (filter :bars rendered-layers)
        viz-layers (remove :bars rendered-layers)

        ;; Update y-domain if we have histograms
        y-domain (if (seq histogram-layers)
                   (let [max-count (apply max (map :max-count histogram-layers))]
                     [0 (* 1.05 max-count)]) ; 5% padding at top
                   y-domain)

        ;; Recalculate y-padding with updated domain
        y-padding (if has-histogram? 0 (* 0.05 (- (second y-domain) (first y-domain))))

        ;; Create scale functions for coordinate mapping
        x-scale (fn [x] (+ (first x-range)
                           (* (/ (- x (first x-domain))
                                 (- (second x-domain) (first x-domain)))
                              (- (second x-range) (first x-range)))))
        ;; For y-scale: SVG y increases downward, so we need to invert
        ;; y=y-min maps to y-range[0] (bottom, larger pixel value)
        ;; y=y-max maps to y-range[1] (top, smaller pixel value)
        y-scale (fn [y] (+ (first y-range)
                           (* (/ (- y (first y-domain))
                                 (- (second y-domain) (first y-domain)))
                              (- (second y-range) (first y-range)))))

        ;; Convert histogram bars to SVG rect elements
        histogram-rects (mapcat (fn [hist-layer]
                                  (let [bars (:bars hist-layer)
                                        fill (:fill hist-layer)
                                        stroke (:stroke hist-layer)
                                        max-count (:max-count hist-layer)]
                                    (map (fn [bar]
                                           (let [x1 (x-scale (:bin-start bar))
                                                 x2 (x-scale (:bin-end bar))
                                                 ;; In SVG, y increases downward
                                                 ;; y=0 (bottom) is at y-range[0] (larger value)
                                                 ;; y=max (top) is at y-range[1] (smaller value)
                                                 y-base (y-scale 0) ; bottom of bar (count=0)
                                                 y-top (y-scale (:count bar)) ; top of bar
                                                 bar-width (- x2 x1)
                                                 bar-height (- y-base y-top)] ; positive because y-base > y-top
                                             [:rect {:x x1
                                                     :y y-top
                                                     :width bar-width
                                                     :height bar-height
                                                     :fill fill
                                                     :stroke stroke
                                                     :stroke-width (:histogram-stroke-width theme)}]))
                                         bars)))
                                histogram-layers)

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

    ;; If we have histogram bars, insert them into the SVG
    (if (seq histogram-rects)
      ;; The structure is [:g nil [...grid...] () [...x-axis...] [...y-axis...]]
      ;; Insert histogram bars after the grid (index 2) and before the empty () (index 3)
      (let [[tag attrs & children] base-plot]
        (if (= tag :g)
          ;; Insert histogram group as a new child
          (into [tag attrs]
                (concat (take 3 children)  ; grid and major/minor ticks
                        [(into [:g {:class "histogram-bars"}] histogram-rects)]
                        (drop 3 children))) ; axes
          base-plot))
      base-plot)))

(defn plot
  "Render a plot specification to SVG.

  Takes a spec with :=layers and renders to interactive SVG.
  Handles single layers, grids, and overlays."
  [spec]
  (let [layers (:=layers spec)
        layout (:=layout spec :single)
        width 600
        height 400]

    (case layout
      ;; Single panel: all layers overlay in one coordinate system
      :single
      (kind/hiccup
       [:div {:style {:background (:background theme)}}
        (render-single-panel layers width height)])

      ;; Grid layout: multiple panels arranged spatially
      :grid
      (let [;; Group layers by grid position
            grid-map (group-by (juxt :=grid-row :=grid-col) layers)
            max-row (apply max (map :=grid-row layers))
            max-col (apply max (map :=grid-col layers))
            panel-width 150
            panel-height 150
            total-width (* (inc max-col) panel-width)
            total-height (* (inc max-row) panel-height)]

        (kind/hiccup
         [:div {:style {:background (:background theme)}}
          [:svg {:width total-width :height total-height}
           (for [row (range (inc max-row))
                 col (range (inc max-col))]
             (when-let [panel-layers (get grid-map [row col])]
               (let [x-offset (* col panel-width)
                     y-offset (* row panel-height)]
                 [:g {:transform (str "translate(" x-offset "," y-offset ")")}
                  (render-single-panel panel-layers panel-width panel-height)])))]]))

      ;; Default: single panel
      (kind/hiccup
       [:div {:style {:background (:background theme)}}
        (render-single-panel layers width height)]))))

;; ## Seeing It in Action
;;
;; Now that we have rendering, let's see our smart defaults at work.

;; ### Scatter Plot (Off-Diagonal)
;;
;; When we cross different variables, we get a scatter plot:

(-> (cross sepal-length sepal-width)
    plot)

;; The system detected `x≠y` and chose `:scatter` geometry automatically.

;; ### Histogram (Diagonal)
;;
;; When we cross a variable with itself, we get a histogram:

(-> (cross sepal-length sepal-length)
    plot)

;; The system detected `x=y` and chose `:histogram` geometry automatically.
;; Notice it only uses the x-axis values (y is the same anyway).

;; ### Overlaying Geometries with `=+`
;;
;; We can overlay multiple geometries using the `=+` operator. Let's add a regression line
;; to our scatter plot:

(defn linear
  "Add linear regression geometry.

  Returns a spec with :=plottype :linear."
  []
  {:=layers [{:=plottype :linear}]})

(-> (cross sepal-length sepal-width)
    (=+ (linear))
    plot)

;; The scatter points and regression line share the same coordinate system.
;; This is the power of `=+` - it concatenates layers with inheritance.

;; ## Building Grids with Blend
;;
;; So far we've crossed individual varsets. But what if we want to cross *multiple* variables
;; at once? That's where **blend** comes in - it creates a collection of alternatives.

(defn blend
  "Create a blend (collection of alternatives) from multiple variables.

  Takes a dataset and vector of column names.
  Returns a structure representing multiple varsets that can be crossed."
  [dataset variables]
  {:alternatives (for [var variables]
                  (varset dataset var))
   :dataset dataset})

;; Now let's update `cross` to handle blends using the distributive law:
;; (A + B) × (C + D) = AC + AD + BC + BD

(defn cross
  "Cross two varsets or blends to create a grid of alternatives.

  For simple varsets: creates a single layer with both variables.
  For blends: applies the distributive law to create all combinations.

  This is where the magic happens - structure determines geometry."
  [vs-x vs-y]
  (cond
    ;; Both are blends → full cross-product with grid positions
    (and (:alternatives vs-x) (:alternatives vs-y))
    (let [x-vars (:alternatives vs-x)
          y-vars (:alternatives vs-y)
          dataset (:dataset vs-x)
          layers (for [[row-idx y-vs] (map-indexed vector y-vars)
                      [col-idx x-vs] (map-indexed vector x-vars)]
                  (let [x-var (:variable x-vs)
                        y-var (:variable y-vs)
                        diagonal? (= x-var y-var)]
                    {:=x x-var
                     :=y y-var
                     :=data dataset
                     :=grid-row row-idx
                     :=grid-col col-idx
                     :=diagonal? diagonal?
                     ;; Phase 1 smart default: diagonal → histogram
                     :=plottype (if diagonal? :histogram :scatter)}))]
      {:=layers layers
       :=data dataset
       :=indices (range (tc/row-count dataset))
       :=layout :grid})

    ;; Simple case: two varsets → one layer
    (and (:variable vs-x) (:variable vs-y))
    (let [x-var (:variable vs-x)
          y-var (:variable vs-y)
          diagonal? (= x-var y-var)]
      {:=layers [{:=x x-var
                  :=y y-var
                  :=data (:dataset vs-x)
                  :=diagonal? diagonal?
                  ;; Phase 1 smart default: diagonal → histogram
                  :=plottype (if diagonal? :histogram :scatter)}]
       :=data (:dataset vs-x)
       :=indices (:indices vs-x)})

    ;; One blend, one varset → multiple layers
    (:alternatives vs-x)
    (let [x-vars (:alternatives vs-x)
          y-var (:variable vs-y)
          dataset (:dataset vs-x)
          layers (for [[idx x-vs] (map-indexed vector x-vars)]
                  (let [x-var (:variable x-vs)
                        diagonal? (= x-var y-var)]
                    {:=x x-var
                     :=y y-var
                     :=data dataset
                     :=diagonal? diagonal?
                     :=plottype (if diagonal? :histogram :scatter)}))]
      {:=layers layers
       :=data dataset
       :=indices (range (tc/row-count dataset))})

    (:alternatives vs-y)
    (let [y-vars (:alternatives vs-y)
          x-var (:variable vs-x)
          dataset (:dataset vs-y)
          layers (for [[idx y-vs] (map-indexed vector y-vars)]
                  (let [y-var (:variable y-vs)
                        diagonal? (= x-var y-var)]
                    {:=x x-var
                     :=y y-var
                     :=data dataset
                     :=diagonal? diagonal?
                     :=plottype (if diagonal? :histogram :scatter)}))]
      {:=layers layers
       :=data dataset
       :=indices (range (tc/row-count dataset))})

    :else
    (throw (ex-info "Invalid cross arguments"
                    {:vs-x vs-x :vs-y vs-y}))))

;; Now we can create a full SPLOM! Let's try a 2×2 first:

(def vars-2x2 (blend iris [:sepal-length :sepal-width]))

(-> (cross vars-2x2 vars-2x2)
    plot)

;; Notice the structure - four panels in a 2×2 grid:
;; - Top-left: sepal-width × sepal-width (histogram)
;; - Top-right: sepal-width × sepal-length (scatter)
;; - Bottom-left: sepal-length × sepal-width (scatter)
;; - Bottom-right: sepal-length × sepal-length (histogram)

;; ## Override Constructors
;;
;; The smart defaults are useful, but sometimes we want different geometries.
;; **Override constructors** let us specify custom geometry for diagonal or off-diagonal panels.

(defn diagonal
  "Override geometry for diagonal layers (x=y).

  Can take a plottype keyword or a full layer spec for composition."
  [plottype-or-spec]
  (let [override (if (keyword? plottype-or-spec)
                  {:=plottype plottype-or-spec}
                  plottype-or-spec)]
    {:=diagonal-override override}))

(defn off-diagonal
  "Override geometry for off-diagonal layers (x≠y).

  Can take a plottype keyword or a full layer spec for composition."
  [plottype-or-spec]
  (let [override (if (keyword? plottype-or-spec)
                  {:=plottype plottype-or-spec}
                  plottype-or-spec)]
    {:=off-diagonal-override override}))

;; Update `=*` to recognize and apply these overrides:

(defn =*
  "Merge/compose specs (cross-product of layers).

  When both specs have layers, creates cross-product.
  Otherwise merges plot-level properties.

  Recognizes override constructors and applies them to matching layers."
  [& specs]
  (let [specs (remove nil? specs)]
    (if (= 1 (count specs))
      (first specs)
      (reduce
       (fn [acc spec]
         (cond
           ;; Both have layers → cross-product
           (and (:=layers acc) (:=layers spec))
           (update acc :=layers
                   (fn [layers]
                     (for [layer1 layers
                           layer2 (:=layers spec)]
                       (merge layer1 layer2))))

           ;; Only one has layers → merge properties
           (:=layers acc)
           (let [merged (merge spec acc)
                 ;; Apply overrides if present
                 diagonal-override (:=diagonal-override merged)
                 off-diagonal-override (:=off-diagonal-override merged)]
             (cond-> merged
               diagonal-override
               (update :=layers
                       (fn [layers]
                         (map (fn [layer]
                               (if (:=diagonal? layer)
                                 (merge layer diagonal-override)
                                 layer))
                             layers)))

               off-diagonal-override
               (update :=layers
                       (fn [layers]
                         (map (fn [layer]
                               (if-not (:=diagonal? layer)
                                 (merge layer off-diagonal-override)
                                 layer))
                             layers)))

               ;; Clean up override markers
               true
               (dissoc :=diagonal-override :=off-diagonal-override)))

           (:=layers spec)
           (merge acc spec)

           ;; Neither has layers → simple merge
           :else
           (merge acc spec)))
       specs))))

;; Now we can override the defaults! Let's change the diagonal to use a different geometry:

(def vars-2x2-custom
  (-> (cross vars-2x2 vars-2x2)
      (=* (diagonal :scatter))  ; Override: diagonal becomes scatter instead of histogram
      (=* (off-diagonal :histogram))))  ; Override: off-diagonal becomes histogram instead of scatter

;; This demonstrates full control - we can make any geometry decision we want.

;; ## Convenience: Direct SPLOM Function
;;
;; For the common case of creating a SPLOM from a list of variables, let's add a convenience function:

(defn splom
  "Create a scatterplot matrix (SPLOM) from a dataset and variables.

  Returns a grid spec with smart defaults (diagonal → histogram, off-diagonal → scatter)."
  [dataset variables]
  (let [vars-blend (blend dataset variables)]
    (cross vars-blend vars-blend)))

;; Now we can create a full 4×4 SPLOM in one line:

(-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
    plot)

;; A 16-panel visualization created from a single line of code!
;;
;; The system automatically:
;; - Detected diagonal panels (x=y) → rendered as histograms
;; - Detected off-diagonal panels (x≠y) → rendered as scatterplots
;; - Arranged everything in a 4×4 grid
;; - Computed appropriate domains for each panel
;;
;; This is the power of structure-determined geometry.

;; ## Heterogeneous SPLOMs
;;
;; We can create more sophisticated SPLOMs by overriding geometry for different panel types.
;; For example, let's add regression lines to the off-diagonal panels:

(comment
  ;; TODO: This requires implementing =+ overlay with grid layouts
  ;; Currently =+ works for single panels but not grids
  (-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
      (=* (off-diagonal (=+ (linear))))  ; Add regression to scatter
      plot))

;; Or change the diagonal to show density instead of histogram:

(comment
  ;; TODO: Implement density geometry
  (-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
      (=* (diagonal :density))
      plot))

;; ## What We've Built
;;
;; Let's take stock of what we have:
;;
;; 1. **Varsets & Cross** - Algebraic operations on variables
;; 2. **Blend** - Create collections of alternatives for grid layouts
;; 3. **Smart Defaults** - Phase 1: diagonal → histogram, off-diagonal → scatter
;; 4. **Override Constructors** - `(diagonal ...)` and `(off-diagonal ...)` for control
;; 5. **Composition** - `=*` and `=+` operators for building complex specs
;; 6. **Rendering** - Scatter, histogram, and linear regression to SVG
;; 7. **Grid Layout** - Spatial arrangement of multiple panels
;; 8. **SPLOM** - Scatterplot matrices in one line
;;
;; The key insight has proven powerful: **the structure of the algebra determines sensible defaults for geometry**.
;; When we cross a variable with itself, we're asking about distribution (histogram).
;; When we cross different variables, we're asking about relationship (scatter).
;;
;; The defaults work, but we retain full control through override constructors.

;; ## Next: Linked Brushing
;;
;; The final piece is **interactivity**. Since all panels share the same observation index space
;; (`:=indices [0..149]`), we can implement brushing: selecting points in one panel highlights
;; them across all panels.
;;
;; This requires:
;; 1. Adding data attributes to rendered points with their observation indices
;; 2. Embedding D3.js code for brush interaction
;; 3. Cross-panel highlighting via shared observation IDs
;;
;; That's coming in the next iteration. For now, we have a solid foundation for
;; declarative, compositional data visualization that combines the best of
;; the Grammar of Graphics algebra with layer-based construction.

;; ## Summary
;;
;; This unified approach demonstrates that:
;;
;; - **Algebra and layers compose naturally** - GoG operations create structure, AoG layers add geometry
;; - **Smart defaults reduce specification burden** - diagonal detection eliminates boilerplate
;; - **Override constructors preserve control** - defaults work until they don't, then override
;; - **Plain data IR stays inspectable** - `kind/pprint` shows what the algebra expands to
;; - **Shared observation space enables linking** - future brushing relies on this foundation
;;
;; The result is terse yet powerful:
;;
;; ```clojure
;; (-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
;;     plot)
;; ```
;;
;; A complete 16-panel SPLOM from one line. The defaults handle the common case,
;; overrides handle the rest.

;; ---
;;
;; **Status**: Core functionality complete. Rendering works. Smart defaults (Phase 1) implemented.
;; Override constructors functional. Grid layout rendering operational. D3 brushing pending.
