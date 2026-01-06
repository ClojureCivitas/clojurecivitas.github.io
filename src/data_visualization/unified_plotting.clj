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

;; # A Unified Approach to Data Visualization
;;
;; This notebook combines two powerful ideas:
;;
;; 1. **Grammar of Graphics** - Leland Wilkinson's algebraic framework (cross, blend, nest)
;; 2. **Algebra of Graphics** - Compositional layer construction (inspired by AlgebraOfGraphics.jl)
;;
;; **Key insight:** Structure determines geometry. When you cross a variable with itself (x=y),
;; you're looking at *distribution* → histogram. When you cross different variables (x≠y),
;; you're *comparing* them → scatterplot. The algebra tells us what question we're asking,
;; which suggests sensible defaults.
;;
;; This is a working Clojure namespace executable with Clay/Kindly. Let's build up from
;; simple operations to complete scatterplot matrices.
;;
;; ## Architecture: Three Layers of Abstraction
;;
;; This library uses a **separation of concerns** design with three distinct layers:
;;
;; **1. Algebraic Layer (Pure Data Manipulation)**
;; - `l*` - Layer cross (cartesian product + property merging)
;; - `l+` - Layer blend (concatenation + inheritance)
;; - `d*` - Dataset cross (wraps l* with dataset context)
;; - `d+` - Dataset blend (wraps l+ with dataset context)
;; - **NO visualization inference** - these are pure algebraic operations on data structures
;;
;; **2. Visualization Inference Layer (Opinionated Rendering Choices)**
;; - `smart-defaults` - Applies visualization intelligence:
;;   - Auto-assigns visual roles (:=columns → :=x/:=y)
;;   - Detects diagonals (x=y → histogram, x≠y → scatter)
;;   - Infers plottypes based on structure
;;   - Assigns grid positions for multi-panel layouts
;; - **Separates "what to plot" from "how to render"**
;;
;; **3. Rendering Layer (SVG Generation)**
;; - `plot` - Renders specs to SVG via thi.ng/geom-viz
;; - Geometry renderers (scatter, histogram, linear, smooth)
;; - Multi-panel coordination and layout
;;
;; **Typical Workflow:**
;; ```clojure
;; (-> (d* dataset [:x-var] [:y-var])  ; 1. Algebra: define relationships
;;     smart-defaults                   ; 2. Inference: apply visualization logic
;;     plot)                            ; 3. Render: generate SVG
;; ```
;;
;; This separation means you can use the algebra layer for data manipulation without
;; committing to visualization choices, then apply different inference strategies,
;; or bypass inference entirely for full control.
;;
;; ## Core Algebraic Operations

(defn- merge-layers
  "Merge multiple layer maps with special :=columns handling.

  **Key design decision:** :=columns vectors concatenate to preserve positional
  provenance, but ONLY when no visual roles (:=x, :=y) are assigned yet.

  **Rationale:** :=columns tracks 'where columns came from' in order. When we
  cross {:=columns [:price]} with {:=columns [:size]}, we get {:=columns [:price :size]}.
  This positional tuple enables auto-assignment: first→:=x, second→:=y.

  **Conflict prevention:** If layers already have :=x or :=y assigned, skip
  :=columns concatenation to avoid contradictions. The roles are the source of
  truth, not the provenance.

  All other properties follow normal merge semantics (rightmost wins).

  Examples:
    (merge-layers {:=columns [:a]} {:=columns [:b]})
    ;; => {:=columns [:a :b]}

    (merge-layers {:=columns [:a] :=x :a} {:=columns [:b]})
    ;; => {:=columns [:a] :=x :a}  ; no concat because :=x exists"
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
                             m ; Don't update :=columns

                             ;; All other properties: normal merge (right wins)
                             :else
                             (assoc m k v)))
                         acc
                         layer))
            {}
            layers)))

(defn auto-assign-roles
  "Assign :=x and :=y visual roles from :=columns positional provenance.

  **Positional semantics:**
  - 1 column → :=x only (univariate: histogram, density, bar chart)
  - 2 columns → :=x + :=y (bivariate: scatter, line, regression)
  - 3+ columns → error (ambiguous; user must specify explicitly)

  **Design rationale:** The :=columns tuple tracks which columns were crossed,
  in order. We use position to infer intent: first column usually maps to x-axis,
  second to y-axis. This enables terse ergonomic APIs while maintaining precision.

  **Idempotent:** If :=x/:=y already assigned, returns layer unchanged.

  Examples:
    (auto-assign-roles {:=columns [:price]})
    ;; => {:=columns [:price] :=x :price}

    (auto-assign-roles {:=columns [:price :size]})
    ;; => {:=columns [:price :size] :=x :price :=y :size}

    (auto-assign-roles {:=columns [:a :b :c]})
    ;; => throws ex-info (ambiguous)"
  [layer]
  (if (or (:=x layer) (:=y layer))
    layer ; Already assigned, don't touch
    (let [cols (:=columns layer)]
      (case (count cols)
        1 (assoc layer :=x (first cols))
        2 (assoc layer :=x (first cols) :=y (second cols))
        (throw (ex-info "Ambiguous column count; specify :=x/:=y explicitly"
                        {:columns cols
                         :layer layer}))))))

(defn l*
  "Layer cross: Cartesian product of layers from multiple specs.

  **Core algebraic operation** implementing the distributive law:
    (l* (l+ A B) C) = (l+ (l* A C) (l* B C))

  Takes specs with :=layers and produces their cross-product. Each combination
  of layers is merged using merge-layers (with special :=columns handling).

  Also handles property merging and override constructors (diagonal, off-diagonal).

  Plot-level properties (everything except :=layers) are merged normally.

  **Example - crossing variables:**
    (l* {:=layers [{:=columns [:price]}] :=data ds}
        {:=layers [{:=columns [:size]}] :=data ds})
    ;; => {:=layers [{:=columns [:price :size]}] :=data ds}

  **Example - cartesian product:**
    (l* {:=layers [{:=x :a} {:=x :b}]}
        {:=layers [{:=y :c} {:=y :d}]})
    ;; => {:=layers [{:=x :a :=y :c}
    ;;               {:=x :a :=y :d}
    ;;               {:=x :b :=y :c}
    ;;               {:=x :b :=y :d}]}

  **Example - property merging:**
    (l* {:=layers [{:=x :a :=y :b}]}
        {:=layers [{:=color :species}]})
    ;; => {:=layers [{:=x :a :=y :b :=color :species}]}

  **Example - override constructors:**
    (l* (-> (d* iris [:a :b] [:a :b]) smart-defaults)
        (diagonal :scatter))
    ;; Applies :scatter to diagonal layers only

  **Non-commutative:** (l* A B) ≠ (l* B A) when :=columns order matters.
  Position determines role assignment, so order is semantically significant."
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

           ;; Only one has layers → merge properties and apply overrides
           (:=layers acc)
           (let [merged (merge spec acc)
                 diagonal-override (:=diagonal-override merged)
                 off-diagonal-override (:=off-diagonal-override merged)]
             (cond-> merged
               ;; Apply diagonal override
               diagonal-override
               (update :=layers
                       (fn [layers]
                         (mapcat
                          (fn [layer]
                            (if (:=diagonal? layer)
                              ;; If override has :=layers, keep original + add overlays
                              (if-let [override-layers (:=layers diagonal-override)]
                                (cons layer
                                      (for [override-layer override-layers]
                                        (merge layer override-layer)))
                                ;; Otherwise just merge the override
                                [(merge layer diagonal-override)])
                              ;; Non-diagonal layers pass through unchanged
                              [layer]))
                          layers)))

               ;; Apply off-diagonal override
               off-diagonal-override
               (update :=layers
                       (fn [layers]
                         (mapcat
                          (fn [layer]
                            (if-not (:=diagonal? layer)
                              ;; If override has :=layers, keep original + add overlays
                              (if-let [override-layers (:=layers off-diagonal-override)]
                                (cons layer
                                      (for [override-layer override-layers]
                                        (merge layer override-layer)))
                                ;; Otherwise just merge the override
                                [(merge layer off-diagonal-override)])
                              ;; Diagonal layers pass through unchanged
                              [layer]))
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

(defn l+
  "Layer blend: Concatenate layers from multiple specs into alternatives.

  **Core algebraic operation** for creating collections. In GoG terms, these
  are 'alternatives' - different ways to visualize the same data structure.

  Takes specs with :=layers and concatenates all layers into a single vector.
  Plot-level properties are merged (rightmost wins).

  **Inheritance behavior for overlays:**
  When adding geometry layers (e.g., `(linear)`, `(smooth)`) to a base spec,
  new layers inherit :=x, :=y, :=color from the first layer if they don't
  already have them. This enables overlay composition like:
    (l+ base-scatter (linear))
  where the linear layer automatically inherits the x/y mappings.

  **Example - creating alternatives:**
    (l+ {:=layers [{:=columns [:price]}]}
        {:=layers [{:=columns [:size]}]})
    ;; => {:=layers [{:=columns [:price]}
    ;;               {:=columns [:size]}]}

  **Example - overlay composition:**
    (l+ {:=layers [{:=x :a :=y :b :=plottype :scatter}]}
        {:=layers [{:=plottype :linear}]})
    ;; => {:=layers [{:=x :a :=y :b :=plottype :scatter}
    ;;               {:=x :a :=y :b :=plottype :linear}]}
    ;; Note: linear layer inherits :=x and :=y

  **Use cases:**
  - Blending variables for SPLOM grids
  - Creating multi-layer overlays (scatter + regression)
  - Combining different visualizations of same data"
  [& specs]
  (let [all-layers (mapcat :=layers specs)
        plot-props (apply merge (map #(dissoc % :=layers) specs))

        ;; Extract inheritable properties from first layer (base layer)
        ;; Only inherit layer-level aesthetics: :=x, :=y, :=color
        base-layer (first all-layers)
        inheritable-keys [:=x :=y :=color]
        inherited-props (when base-layer
                          (select-keys base-layer inheritable-keys))

        ;; Apply inheritance: new layers get base properties if they lack them
        ;; Only apply if we have inherited props and this is an overlay situation
        layers-with-inheritance
        (if (and inherited-props
                 (seq inherited-props)
                 (> (count all-layers) 1))
          (map-indexed
           (fn [idx layer]
             (if (zero? idx)
               ;; First layer is the base - don't modify
               layer
               ;; Subsequent layers inherit missing properties
               (merge inherited-props layer)))
           all-layers)
          ;; No inheritance needed - just return layers as-is
          all-layers)]

    (assoc plot-props :=layers (vec layers-with-inheritance))))

(defn smart-defaults
  "Apply opinionated visualization choices to prepare a spec for rendering.

  **Transforms raw crossed/blended spec into render-ready spec:**
  1. Auto-assign visual roles (:=columns → :=x/:=y)
  2. Detect diagonals (:=x == :=y → :=diagonal? true)
  3. Infer plottypes (diagonal → :histogram, else → :scatter)
  4. Assign grid positions (:=grid-row, :=grid-col) when multi-layer
  5. Set layout flags (:=layout :grid)

  **Example - single layer:**
    (smart-defaults {:=layers [{:=columns [:a :b]}]})
    ;; => {:=layers [{:=columns [:a :b]
    ;;               :=x :a :=y :b
    ;;               :=plottype :scatter
    ;;               :=diagonal? false}]}

  **Example - grid (multiple layers):**
    (smart-defaults {:=layers [{:=columns [:a :c]}
                               {:=columns [:a :d]}
                               {:=columns [:b :c]}
                               {:=columns [:b :d]}]})
    ;; => {:=layers [...with grid positions...]
    ;;     :=layout :grid}

  **Grid detection:** Considers it a grid when:
  - Multiple layers (>1)
  - All layers have 2-column tuples in :=columns
  - Infers dimensions from unique values in first/second positions
  
  Returns spec ready for (plot spec)."
  [spec]
  (let [layers (:=layers spec)
        n-layers (count layers)

        ;; Check if this looks like a crossed grid:
        ;; - Multiple layers
        ;; - All layers have 2-element :=columns (crossed pairs)
        all-have-pairs? (every? #(= 2 (count (:=columns %))) layers)
        is-grid? (and (> n-layers 1) all-have-pairs?)

        ;; Process layers with role assignment, diagonal detection, and grid positions
        processed-layers
        (if is-grid?
          ;; Grid layout: assign positions based on unique column values
          ;; In a grid from d*, first column varies by row, second by column
          (let [;; Get ordered unique values for x and y variables
                x-vars (distinct (map #(first (:=columns %)) layers))
                y-vars (distinct (map #(second (:=columns %)) layers))
                ;; Create lookup maps: var → grid index
                ;; x-vars map to rows (varies vertically)
                ;; y-vars map to cols (varies horizontally)
                x-index (into {} (map-indexed (fn [i v] [v i]) x-vars))
                y-index (into {} (map-indexed (fn [i v] [v i]) y-vars))]
            (map (fn [layer]
                   (let [assigned (auto-assign-roles layer)
                         x-col (:=x assigned)
                         y-col (:=y assigned)
                         diagonal? (= x-col y-col)
                         ;; Grid position: x determines row, y determines col
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

  **Variadic - crosses 2+ column lists.**
  Each argument is a vector of columns to cross.
  Returns spec with :=layers (cartesian product), :=data, :=indices.

  **Use (smart-defaults spec) to prepare for rendering.**

  **Example - single columns:**
    (d* iris [:sepal-length] [:sepal-width])
    ;; => {:=layers [{:=columns [:sepal-length :sepal-width]}]
    ;;     :=data iris
    ;;     :=indices [0 1 2 ... 149]}

  **Example - column lists (2×2 grid):**
    (d* iris [:sepal-length :sepal-width] [:petal-length :petal-width])
    ;; => {:=layers [{:=columns [:sepal-length :petal-length]}
    ;;               {:=columns [:sepal-length :petal-width]}
    ;;               {:=columns [:sepal-width :petal-length]}
    ;;               {:=columns [:sepal-width :petal-width]}]
    ;;     :=data iris
    ;;     :=indices [0 1 2 ... 149]}

  **Complete workflow:**
    (-> (d* dataset [:x] [:y])
        smart-defaults
        plot)"
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

  **Takes a vector of columns and creates alternatives.**
  Each column becomes a separate layer.
  Returns spec with :=layers (one per column), :=data, :=indices.

  **Use (smart-defaults spec) to prepare for rendering.**

  **Example - simple blend:**
    (d+ iris [:sepal-length :sepal-width :petal-length])
    ;; => {:=layers [{:=columns [:sepal-length]}
    ;;               {:=columns [:sepal-width]}
    ;;               {:=columns [:petal-length]}]
    ;;     :=data iris
    ;;     :=indices [0 1 2 ... 149]}

  **Example - creating SPLOM (4×4 grid):**
    (-> (d* iris [:sl :sw :pl :pw] [:sl :sw :pl :pw])
        smart-defaults)
    ;; Or using d+ explicitly:
    (-> (let [vars (d+ iris [:sl :sw :pl :pw])]
          (l* vars vars))
        smart-defaults)

  **Complete workflow:**
    (-> (d+ dataset [:a :b :c])
        smart-defaults
        plot)"
  [dataset columns]
  (apply l+ (map (fn [col]
                   {:=layers [{:=columns [col]}]
                    :=data dataset
                    :=indices (range (tc/row-count dataset))})
                 columns)))

;; ### Understanding l* and l+
;;
;; Before we dive into working with data, let's see what l* and l+ actually do.
;; These are the fundamental operators that power everything else.

;; **What does l* do? (Cross-product)**

(comment
  ;; Create two simple specs
  (def spec-a {:=layers [{:=columns [:a]}]})
  (def spec-b {:=layers [{:=columns [:b]}]})

  ;; Cross them with l*
  (kind/pprint (l* spec-a spec-b))
  ;; => {:=layers [{:=columns [:a :b]}]}
  ;;
  ;; Notice: The :=columns vectors merged! This is how relationships form.
  ;; When we later cross :sepal-length with :sepal-width, l* merges their
  ;; column tuples to create [:sepal-length :sepal-width] - a relationship.
  )

;; **What does l+ do? (Concatenation)**

(comment
  ;; Blend the same specs with l+
  (kind/pprint (l+ spec-a spec-b))
  ;; => {:=layers [{:=columns [:a]}
  ;;               {:=columns [:b]}]}
  ;;
  ;; Notice: Two separate layers! This is how alternatives work.
  ;; When we blend variables for a SPLOM, each variable stays separate,
  ;; creating a collection we can later cross.
  )

;; **Key insight:** l* merges (for relationships), l+ concatenates (for alternatives).
;; This distinction drives the entire algebra.

;; ## The Standard Workflow Pattern
;;
;; Before we dive into examples, here's the **standard three-step workflow** you'll see throughout:
;;
;; ```clojure
;; (-> (d* dataset [:x-var] [:y-var])  ; 1. ALGEBRA: Define relationships
;;     smart-defaults                   ; 2. INFERENCE: Apply visualization logic
;;     plot)                            ; 3. RENDER: Generate SVG
;; ```
;;
;; **Why three steps?**
;;
;; 1. **`d*` / `d+`** - Pure algebraic operations create data structures with no visualization decisions
;; 2. **`smart-defaults`** - Applies intelligent defaults (auto-assigns roles, detects diagonals, infers plottypes)
;; 3. **`plot`** - Renders the spec to SVG
;;
;; This separation means you can:
;; - Inspect the raw algebra output before visualization (`kind/pprint` the spec)
;; - Apply different inference strategies (or skip `smart-defaults` for full control)
;; - Reuse algebraic specs across different rendering contexts
;;
;; **Convenience functions** like `splom` bundle these steps for common patterns, but the
;; three-step workflow gives you maximum flexibility when you need it.

;; ## The Dataset
;;
;; We'll use the classic Iris dataset - 150 observations of iris flowers with measurements
;; of sepal and petal dimensions, plus species (setosa, versicolor, virginica).

(def iris
  (toydata/iris-ds))

iris

;; Four numeric columns plus one categorical column. Perfect for exploring relationships.

;; ## What We're Building Toward
;;
;; Here's a scatterplot matrix (SPLOM) showing all pairwise relationships in one visualization.
;; Notice how tersely we can express it:

(comment
  ;; A complete 4×4 SPLOM in one line
  (-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
      plot))

;; The system automatically detects diagonal panels (x=y) → renders as histograms.
;; Off-diagonal panels (x≠y) → renders as scatterplots. Structure determines geometry.
;;
;; We can override defaults and add layers:

(comment
  (-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
      (l* {:=layers [{:=color :species}]}) ;; Add color grouping
      (l* (off-diagonal (l+ (linear)))) ;; Add regression lines
      plot))

;; Let's build up to this step by step.

;; ## Creating Plot Specifications
;;
;; The foundation of our approach is using **d\*** (dataset cross) to create relationships
;; between variables, then **smart-defaults** to prepare them for rendering.

;; Let's create a simple scatterplot specification - sepal length vs width:

(def length-vs-width
  (-> (d* iris [:sepal-length] [:sepal-width])
      smart-defaults))

;; This creates a plot specification with a single layer. Notice the structure:

(kind/pprint length-vs-width)

;; The `:=plottype` is `:scatter` because these are different variables (x≠y).
;; The spec includes `:=data` (the dataset) and `:=indices` (tracking observations).
;; The `smart-defaults` function auto-assigned :=x and :=y roles from the column order.

;; Now let's cross sepal-length with *itself* (a diagonal):

(def length-distribution
  (-> (d* iris [:sepal-length] [:sepal-length])
      smart-defaults))

(kind/pprint length-distribution)

;; Notice `:=plottype :histogram` - the system detected `x=y` (diagonal) and chose histogram.
;; This is our first smart default in action.

;; ## A Simple 2×2 Grid
;;
;; Now let's create a 2×2 grid by crossing two variables with themselves.
;; This is where d* shows its power - it creates all combinations automatically:

(def grid-2x2
  (-> (d* iris [:sepal-length :sepal-width] [:sepal-length :sepal-width])
      smart-defaults))

;; Let's examine the structure:

(kind/pprint grid-2x2)

;; Four layers arranged in a 2×2 grid:
;; - `:=grid-row` and `:=grid-col` specify position
;; - Diagonal layers (row=col) have `:=plottype :histogram`
;; - Off-diagonal layers have `:=plottype :scatter`
;;
;; The `:=layout :grid` tells the renderer to arrange these spatially.
;;
;; This demonstrates the pattern. The `d*` function with column vectors creates this
;; structure automatically using the distributive law:
;; (A+B) × (C+D) = AC + AD + BC + BD

;; ## Rendering to SVG
;;
;; So far we've been building specifications - data structures describing what to plot.
;; Now we need to **render** these specs to actual visualizations.
;;
;; Our rendering pipeline:
;; 1. **Theme** - Visual styling (colors, backgrounds, grid lines)
;; 2. **Helpers** - Domain inference, column resolution, point extraction
;; 3. **Geometry renderers** - Scatter, histogram, linear, smooth
;; 4. **Coordination** - Multi-layer rendering and grid layout
;;
;; We'll use thi.ng/geom-viz to generate SVG, which works well with Kindly for notebooks
;; and can integrate with D3 for interactivity later.

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

(defn- resolve-column
  "Resolve column keyword to actual data during rendering.
  
  Precedence (from DEEPER_ANALYSIS resolution #5):
  1. Try layer :=data first
  2. If column missing, try plot :=data
  3. If still missing, error with helpful message
  
  Example:
    (resolve-column layer plot-data :sepal-length)
    ; Looks in layer data first, falls back to plot data"
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

(defn- layer->points
  "Extract point data from a layer, with plot-level data fallback.
  
  Uses resolve-column to implement inheritance chain.
  Returns sequence of point maps with :x, :y, and optionally :color keys."
  [layer plot-data]
  (let [x-col (:=x layer)
        y-col (:=y layer)
        xs (resolve-column layer plot-data x-col)
        ys (resolve-column layer plot-data y-col)
        color-col (:=color layer)]
    (if color-col
      ;; With color: include color value in each point
      (let [colors (resolve-column layer plot-data color-col)]
        (mapv (fn [x y c] {:x x :y y :color c})
              xs ys colors))
      ;; Without color: just point maps with :x and :y
      (mapv (fn [x y] {:x x :y y}) xs ys))))

;; ## Rendering Scatter Plots
;;
;; Let's start with the simplest geometry: scatter plots. A scatter plot is just points
;; positioned by their x and y coordinates.

(defn- render-scatter
  "Render scatter plot points to geom.viz spec.

  Returns a viz data map (or vector of maps for colored groups) for svg-plot2d-cartesian."
  [layer points x-scale y-scale]
  (let [point-fill (:point-fill theme)
        point-stroke (:point-stroke theme)
        color-groups (group-by :color points)]
    (if (> (count color-groups) 1)
      ;; Colored scatter - multiple groups
      (let [colors (:colors theme)]
        (map-indexed (fn [idx [color-val group-points]]
                       {:values (mapv (fn [p] [(:x p) (:y p)]) group-points)
                        :layout viz/svg-scatter-plot
                        :attribs {:fill (get colors idx point-fill)
                                  :stroke (get colors idx point-stroke)
                                  :stroke-width 0.5
                                  :opacity 1.0}})
                     color-groups))
      ;; Simple scatter - single group
      {:values (mapv (fn [p] [(:x p) (:y p)]) points)
       :layout viz/svg-scatter-plot
       :attribs {:fill point-fill
                 :stroke point-stroke
                 :stroke-width 0.5
                 :opacity 1.0}})))

;; ## Rendering Histograms
;;
;; Histograms show the distribution of a single variable by binning values and counting
;; occurrences in each bin. For diagonal panels (x=y), we only use the x values.

(defn- compute-histogram-bins
  "Compute histogram bins using [Sturges' rule](https://en.wikipedia.org/wiki/Histogram#Sturges'_formula).
  
  Sturges' formula (k = ceil(log₂(n) + 1)) provides a simple way to choose the number
  of bins based on sample size. It works well for roughly normal distributions.
  
  Points should be maps with :x key (y is ignored for histograms).
  Returns vector of {:bin-start :bin-end :count} maps."
  [points]
  (let [values (mapv :x points)
        n (count values)
        ;; Sturges' formula: k = 1 + 3.322 * log10(n) = 1 + log2(n)
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
  "Render histogram bars to geom.viz spec.

  For diagonal panels, only uses x values (y values are the same).
  Points should be maps with :x and :y keys."
  [layer points x-scale y-scale]
  (let [bins (compute-histogram-bins points)
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
  "Compute linear regression for a collection of points.
  
  Points should be maps with :x and :y keys.
  Returns map with :slope, :intercept, :x-min, :x-max or nil if not enough data."
  [points]
  (when (>= (count points) 2)
    (let [xs (mapv :x points)
          ys (mapv :y points)
          ;; regr/lm expects (lm ys xss) - response first, then predictors
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

(defn- compute-smooth
  "Compute smoothed y-values using moving average.
  
  Parameters:
  - points: Vector of maps with :x and :y keys (e.g., [{:x 1 :y 2} ...])
  - window: Window size for moving average (default 11)
  
  Returns: Vector of {:x x :y smoothed-y} maps sorted by x"
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

(defn- render-linear
  "Render linear regression line to geom.viz spec.
  
  Handles both single regression and grouped regressions (by color)."
  [layer points x-scale y-scale]
  (let [line-stroke (:line-stroke theme)
        colors (:colors theme)
        color-groups (group-by :color points)]
    (if (> (count color-groups) 1)
      ;; Colored regression - one line per group
      ;; Return as maps since viz/svg-line-plot won't work here
      (keep-indexed (fn [idx [color-val group-points]]
                      (when-let [regression (compute-linear-regression group-points)]
                        (let [{:keys [slope intercept x-min x-max]} regression
                              y-start (+ (* slope x-min) intercept)
                              y-end (+ (* slope x-max) intercept)]
                          ;; Return line data as map for manual rendering
                          {:line-data {:x1 x-min :y1 y-start :x2 x-max :y2 y-end}
                           :stroke (get colors idx line-stroke)
                           :stroke-width 2
                           :opacity 1.0})))
                    color-groups)
      ;; Simple regression - single line
      (when-let [regression (compute-linear-regression points)]
        (let [{:keys [slope intercept x-min x-max]} regression
              y-start (+ (* slope x-min) intercept)
              y-end (+ (* slope x-max) intercept)]
          ;; Return line data as map for manual rendering
          {:line-data {:x1 x-min :y1 y-start :x2 x-max :y2 y-end}
           :stroke line-stroke
           :stroke-width 2
           :opacity 1.0})))))

(defn- render-smooth
  "Render smoothed curves as SVG polyline elements.
  Supports color grouping - creates one curve per group.
  
  When x-scale and y-scale are nil, returns nil (used for initial layer detection)."
  [layer points x-scale y-scale]
  (when (and x-scale y-scale)
    (let [color-col (:=color layer)
          palette ["#F8766D" "#00BA38" "#619CFF" "#F564E3"]]
      (if color-col
        ;; Grouped: one curve per color value
        (let [grouped (group-by :color points)]
          (keep-indexed
           (fn [idx [color-val group-points]]
             (when-let [smooth-data (compute-smooth group-points)]
               (let [scaled-points (map (fn [{:keys [x y]}]
                                          [(x-scale x) (y-scale y)])
                                        smooth-data)
                     polyline-str (clojure.string/join " "
                                                       (map (fn [[x y]] (str x "," y))
                                                            scaled-points))]
                 [:polyline {:points polyline-str
                             :fill "none"
                             :stroke (nth palette idx "#333333")
                             :stroke-width 2.5
                             :opacity 0.8}])))
           grouped))
        ;; Ungrouped: single curve
        (when-let [smooth-data (compute-smooth points)]
          (let [scaled-points (map (fn [{:keys [x y]}]
                                     [(x-scale x) (y-scale y)])
                                   smooth-data)
                polyline-str (clojure.string/join " "
                                                  (map (fn [[x y]] (str x "," y))
                                                       scaled-points))]
            [[:polyline {:points polyline-str
                         :fill "none"
                         :stroke "#333333"
                         :stroke-width 2.5
                         :opacity 0.8}]]))))))

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

(defmethod render-layer :smooth
  [layer points x-scale y-scale]
  (render-smooth layer points x-scale y-scale))

(defn- render-single-panel
  "Render a single panel (one or more overlaid layers) to SVG [hiccup](https://github.com/weavejester/hiccup).

  Hiccup is a Clojure library for representing HTML/SVG as data structures using vectors.
  For example: [:svg {:width 100} [:circle {:r 10}]] represents an SVG circle.

  All layers in the panel share the same domain and coordinate system.
  Returns hiccup vector (not string - serialization happens in plot function)."
  [layers plot-dataset width height]
  (let [;; Check if we have histogram layers (they need special y-domain handling)
        has-histogram? (some #(= (:=plottype %) :histogram) layers)

        ;; Extract all points to compute domains
        all-points (mapcat #(layer->points % plot-dataset) layers)
        x-vals (map :x all-points)
        y-vals (if has-histogram?
                 ;; For histograms, y domain should be [0, max-count]
                 ;; We'll compute this from the histogram bars
                 []
                 (map :y all-points))
        [x-min x-max] (infer-domain x-vals)
        [y-min y-max] (if has-histogram?
                        [0 0] ; Will be updated after rendering histogram
                        (infer-domain y-vals))

        ;; Add 5% padding to domains
        x-padding (* 0.05 (- x-max x-min))
        y-padding (* 0.05 (- y-max y-min))
        x-domain [(- x-min x-padding) (+ x-max x-padding)]
        y-domain [(- y-min y-padding) (+ y-max y-padding)]

        ;; Calculate ranges based on margins (reduced from 40 to 20 for tighter grids)
        margin 20
        x-range [margin (- width margin)]
        y-range [(- height margin) margin]

        ;; Render each layer
        rendered-layers (for [layer layers]
                          (let [points (layer->points layer plot-dataset)]
                            (render-layer layer points nil nil)))

        ;; Filter out nils and separate special layers from viz layers
        ;; Flatten because render-scatter/render-linear/render-smooth can return lists for colored groups
        rendered-layers (remove nil? (flatten rendered-layers))
        histogram-layers (filter :bars rendered-layers)
        line-layers (filter :line-data rendered-layers)
        smooth-layers (filter #(= (:tag %) :polyline) rendered-layers)
        viz-layers (remove #(or (:bars %) (:line-data %) (= (:tag %) :polyline)) rendered-layers)

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

        ;; Re-render smooth layers with proper scales
        smooth-elements (mapcat (fn [layer]
                                  (let [points (layer->points layer plot-dataset)]
                                    (render-layer layer points x-scale y-scale)))
                                (filter #(= (:=plottype %) :smooth) layers))

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
                                             (svg/rect [x1 y-top] bar-width bar-height
                                                       {:fill fill
                                                        :stroke stroke
                                                        :stroke-width (:histogram-stroke-width theme)})))
                                         bars)))
                                histogram-layers)

        ;; Convert line data to SVG line elements
        regression-lines (keep (fn [line-layer]
                                 (when-let [line-data (:line-data line-layer)]
                                   (let [{:keys [x1 y1 x2 y2]} line-data
                                         stroke (:stroke line-layer)
                                         stroke-width (:stroke-width line-layer)
                                         opacity (:opacity line-layer)
                                         ;; Scale coordinates to pixel space
                                         x1-px (x-scale x1)
                                         y1-px (y-scale y1)
                                         x2-px (x-scale x2)
                                         y2-px (y-scale y2)]
                                     (svg/line [x1-px y1-px] [x2-px y2-px]
                                               {:stroke stroke
                                                :stroke-width stroke-width
                                                :opacity opacity}))))
                               line-layers)

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

    ;; Insert histogram bars, regression lines, and smooth curves into the SVG
    (if (or (seq histogram-rects) (seq regression-lines) (seq smooth-elements))
      ;; The structure is [:g nil [...grid...] () [...x-axis...] [...y-axis...]]
      ;; Insert custom elements after the grid (index 2) and before the empty () (index 3)
      (let [[tag attrs & children] base-plot
            custom-groups (concat
                           (when (seq histogram-rects)
                             [(into [:g {:class "histogram-bars"}] histogram-rects)])
                           (when (seq regression-lines)
                             [(into [:g {:class "regression-lines"}] regression-lines)])
                           (when (seq smooth-elements)
                             [(into [:g {:class "smooth-curves"}] smooth-elements)]))]
        (if (= tag :g)
          ;; Insert custom groups as new children
          (into [tag attrs]
                (concat (take 3 children) ; grid and major/minor ticks
                        custom-groups
                        (drop 3 children))) ; axes
          base-plot))
      base-plot)))

(defn plot
  "Render a plot specification to SVG.

  Takes a spec with :=layers and renders to interactive SVG.
  Handles single layers, grids, and overlays."
  [spec]
  (let [layers (:=layers spec)
        plot-dataset (:=data spec)
        layout (:=layout spec :single)
        width 600
        height 400]

    ;; Handle empty layers
    (when (empty? layers)
      (throw (ex-info "Cannot plot spec with no layers"
                      {:spec spec})))

    (case layout
      ;; Single panel: all layers overlay in one coordinate system
      :single
      (kind/html
       (svg/serialize
        [:div {:style (str "background:" (:background theme))}
         [:svg {:width width :height height}
          (render-single-panel layers plot-dataset width height)]]))

      ;; Grid layout: multiple panels arranged spatially
      :grid
      (let [;; Group layers by grid position
            grid-map (group-by (juxt :=grid-row :=grid-col) layers)
            ;; Safely get max row/col, filtering out nils
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

      ;; Default: single panel
      (kind/html
       (svg/serialize
        [:div {:style (str "background:" (:background theme))}
         [:svg {:width width :height height}
          (render-single-panel layers plot-dataset width height)]])))))

;; ### Understanding Our Rendering Target
;;
;; Before we see our first plot, let's understand what we're creating. We use thi.ng/geom-viz
;; to generate SVG (Scalable Vector Graphics). In Clojure, we represent SVG using **hiccup** -
;; a format where HTML/SVG is just nested vectors.
;;
;; Here's what hiccup looks like:

(comment
  ;; A simple SVG hiccup example
  [:svg {:width 200 :height 200}
   [:circle {:cx 100 :cy 100 :r 50 :fill "#F8766D"}]
   [:line {:x1 50 :y1 50 :x2 150 :y2 150
           :stroke "#00BA38" :stroke-width 2}]]

  ;; This hiccup data structure represents an SVG with a red circle and green line.
  ;; When serialized with (svg/serialize ...), it becomes actual SVG markup that
  ;; browsers can display.
  )

;; **geom.viz** provides a higher-level API for creating coordinated plots with axes
;; and gridlines. Our rendering pipeline converts layer specs into geom.viz format,
;; which then produces hiccup, which finally becomes SVG.
;;
;; Here's a quick example of geom.viz in action:

(comment
  (kind/html
   (svg/serialize
    (viz/svg-plot2d-cartesian
     {:x-axis (viz/linear-axis {:domain [0 10] :range [50 350]})
      :y-axis (viz/linear-axis {:domain [0 10] :range [350 50]})
      :grid {:attribs {:stroke "#EBEBEB"}}
      :data [{:values [[2 3] [5 7] [8 4]]
              :layout viz/svg-scatter-plot
              :attribs {:fill "#619CFF" :stroke "#FFFFFF"}}]})))

  ;; This creates a coordinate system with axes and gridlines, then plots three points.
  ;; Our rendering pipeline automates this entire process.
  )

;; ## Seeing It in Action
;;
;; Now that we understand the rendering target, let's see our smart defaults at work.

;; ### Scatter Plot (Off-Diagonal)
;;
;; When we cross different variables, we get a scatter plot:

(-> (d* iris [:sepal-length] [:sepal-width])
    smart-defaults
    plot)

;; The system detected `x≠y` and chose `:scatter` geometry automatically.

;; ### Histogram (Diagonal)
;;
;; When we cross a variable with itself, we get a histogram:

(-> (d* iris [:sepal-length] [:sepal-length])
    smart-defaults
    plot)

;; The system detected `x=y` and chose `:histogram` geometry automatically.
;; Notice it only uses the x-axis values (y is the same anyway).

;; ### Overlaying Geometries with l+
;;
;; We can overlay multiple geometries using the `l+` operator. Let's add a regression line
;; to our scatter plot:

(defn linear
  "Add linear regression geometry.

  Returns a spec with :=plottype :linear."
  []
  {:=layers [{:=plottype :linear}]})

(defn smooth
  "Smooth curve geometry using moving average.
  
  Example:
    (smooth)                    ; Default smooth
    (smooth {:window 15})       ; Custom window size
  
  Composes with l+ for overlays:
    (l+ scatter linear smooth)  ; Three-layer plot"
  ([] (smooth {}))
  ([opts]
   {:=layers [(merge {:=plottype :smooth} opts)]}))

(-> (d* iris [:sepal-length] [:sepal-width])
    smart-defaults
    (l+ (linear))
    plot)

;; The scatter points and regression line share the same coordinate system.
;; This is the power of `l+` - it concatenates layers with inheritance.

;; ## Building Grids with d*
;;
;; So far we've crossed single columns - one variable on x, one on y, resulting in a single panel.
;; But what if we want to cross *multiple* variables at once to create a grid of panels?
;;
;; That's where **d\*** with column vectors shines. When you pass vectors of columns,
;; d* uses the [distributive law](https://en.wikipedia.org/wiki/Distributive_property):
;; (A + B) × (C + D) = AC + AD + BC + BD
;;
;; For example, crossing [:sepal-length :sepal-width] with itself creates all four combinations:
;; length×length, length×width, width×length, and width×width - a 2×2 grid.
;;
;; Let's try it:

(def vars-2x2
  (-> (d* iris [:sepal-length :sepal-width] [:sepal-length :sepal-width])
      smart-defaults))

(-> vars-2x2
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

;; Now we can override the defaults! Let's change the diagonal to use a different geometry:

(def vars-2x2-custom
  (-> (d* iris [:sepal-length :sepal-width] [:sepal-length :sepal-width])
      smart-defaults
      (l* (diagonal :scatter)) ; Override: diagonal becomes scatter instead of histogram
      (l* (off-diagonal :histogram)))) ; Override: off-diagonal becomes histogram instead of scatter

;; This demonstrates full control - we can make any geometry decision we want.

;; ## Convenience: Direct SPLOM Function
;;
;; For the common case of creating a SPLOM from a list of variables, let's add a convenience function:

(defn splom
  "Create a scatterplot matrix (SPLOM) from a dataset and variables.

  **Applies smart-defaults automatically for convenience.**
  
  Equivalent to:
    (-> (d* dataset variables variables)
        smart-defaults)

  Returns a grid spec ready for plotting with:
  - Diagonal panels → histogram
  - Off-diagonal panels → scatter
  - Grid layout with proper positions

  **Example:**
    (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
    ;; Creates 4×4 grid, ready to plot"
  [dataset variables]
  (-> (d* dataset variables variables)
      smart-defaults))

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
;; The override constructors compose beautifully with the algebra.

;; ### Adding Regression Lines to Scatterplots
;;
;; Let's add regression lines to all off-diagonal panels while keeping histograms on the diagonal.
;; The `off-diagonal` constructor targets only those panels where x≠y:

(-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
    (l* (off-diagonal (l+ (linear))))
    plot)

;; Each off-diagonal panel now shows both scatter points and a fitted regression line.
;; The `l+` operator overlays the linear geometry on top of the scatter geometry,
;; and both inherit the x/y mappings from the grid structure.
;;
;; This is the power of composition: we specify the relationship once (scatter + linear),
;; and the override constructor applies it to 12 panels automatically.

;; ### Swapping Diagonal and Off-Diagonal Geometries
;;
;; We can completely reverse the defaults for pedagogical purposes:

(-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
    (l* (diagonal :scatter))
    (l* (off-diagonal :histogram))
    plot)

;; Diagonal panels now show scatter plots (which look like vertical lines since x=y),
;; while off-diagonal panels show histograms (using just the x values).
;; This demonstrates that the geometry choices are truly orthogonal to the algebra.

;; ### A Minimal 2×2 SPLOM with Regression
;;
;; For a clearer view, let's use just two variables with regression overlays:

(-> (splom iris [:sepal-length :sepal-width])
    (l* (off-diagonal (l+ (linear))))
    plot)

;; Four panels:

;; - 0,0: sepal-length × sepal-length → histogram (diagonal)
;; - 0,1: sepal-length × sepal-width → scatter + regression (off-diagonal)
;; - 1,0: sepal-width × sepal-length → scatter + regression (off-diagonal)
;; - 1,1: sepal-width × sepal-width → histogram (diagonal)
;;
;; The negative correlation in [0,1] and [1,0] is immediately visible from the regression lines.

;; ### Color Aesthetic for Grouping
;;
;; We can add a color aesthetic to group observations by a categorical variable.
;; This reveals patterns within subgroups. Let's color points by species:

(plot
 {:=layers [{:=data iris
             :=x :sepal-length
             :=y :sepal-width
             :=color :species
             :=plottype :scatter}]
  :=layout :single})

;; Each species gets a different color from the theme palette (ggplot2 colors).
;; Now we can see that the three species cluster in different regions of the plot.

;; ### Colored Regression Lines
;;
;; We can also compute separate regressions for each color group.
;; This reveals whether the relationship differs across subgroups.
;;
;; Importantly, when you specify a categorical `:=color` aesthetic on a statistical layer
;; (like `:linear` for regression), the system computes *separate statistics for each group*.
;; This means each species gets its own regression line fitted to just its own points.

(plot
 {:=layers [{:=data iris
             :=x :sepal-length
             :=y :sepal-width
             :=color :species
             :=plottype :scatter}
            {:=data iris
             :=x :sepal-length
             :=y :sepal-width
             :=color :species
             :=plottype :linear}]
  :=layout :single})

;; Three regression lines, one per species (each fitted only to that species' data):
;; - Setosa (red): Shows a positive correlation - wider sepals tend to be longer
;; - Versicolor (green): Slight positive correlation  
;; - Virginica (blue): Slight positive correlation
;;
;; The color aesthetic automatically:
;; 1. Groups points by the categorical variable (:species)
;; 2. Assigns colors from the theme palette
;; 3. **Computes separate regressions for each group** (not one line for all data)
;; 4. Renders each group with matching colors
;;
;; This is a powerful pattern - the same aesthetic (`:=color :species`) drives
;; both the visual encoding (color) and the statistical computation (grouped regression).
;; Each species' regression line answers: "for *this* species, how do these variables relate?"

;; ### SPLOM with Color Groups
;;
;; Now let's combine everything: a SPLOM with color grouping. This reveals multivariate
;; patterns within each species across all variable pairs.

(-> (splom iris [:sepal-length :sepal-width :petal-length :petal-width])
    (l* {:=layers [{:=color :species}]})
    plot)

;; Each panel now shows three colored groups (one per species):
;; - Red: Setosa
;; - Green: Versicolor
;; - Blue: Virginica
;;
;; This is powerful - we can see at a glance:
;; - Setosa clusters separately from the other species (visible in all panels)
;; - Petal dimensions (length/width) strongly separate species
;; - Sepal dimensions show more overlap between versicolor and virginica
;;
;; The color aesthetic is applied via `l*` composition, which merges the `:=color :species`
;; property into all layers in the SPLOM grid. Each panel independently groups and colors
;; its points by species.

;; ## Multi-Layer Overlays

;; The `l+` operator stacks multiple geometries in the same panel.
;; Each geometry shares the same data and coordinate system.

;; ### Three Layers: Scatter + Regression + Smooth

;; Let's overlay three geometries to show different aspects of the relationship:

(-> (d* iris [:sepal-length] [:sepal-width])
    smart-defaults
    (l+ (linear))
    (l+ (smooth))
    plot)

;; The plot shows:
;; - Scatter points (raw data)
;; - Linear regression (linear trend)
;; - Smoothed curve (local variation)

;; ### With Color Groups

;; Multi-layer overlays work with color grouping.
;; Each geometry is computed and rendered separately per group:

(-> (d* iris [:sepal-length] [:sepal-width])
    smart-defaults
    (l* {:=layers [{:=color :species}]})
    (l+ (linear))
    (l+ (smooth))
    plot)

;; This creates 3 species × 3 geometries = 9 visual elements:
;; - 3 colored scatter groups
;; - 3 colored regression lines (one per species)
;; - 3 colored smooth curves (one per species)

;; ## Asymmetric Grids (m×n)

;; Not all grids are square! Asymmetric grids are useful for exploring
;; relationships between different sets of variables.

;; ### Predictor-Outcome Grid (2×3)

;; Suppose we want to see how two predictors relate to three outcomes:

(-> (d* iris [:sepal-length :sepal-width] ; 2 predictors
        [:petal-length :petal-width :sepal-length]) ; 3 outcomes
    smart-defaults
    plot)

;; This creates a 2×3 grid showing all 6 predictor-outcome relationships.
;; Use case: Predictive modeling - which predictors matter for which outcomes?

;; ### With Regression Lines

(-> (d* iris [:sepal-length :sepal-width]
        [:petal-length :petal-width])
    smart-defaults
    (l+ (linear))
    plot)

;; Each of the 4 panels shows scatter + regression.

;; ## Rich Composition with Override Constructors

;; The `diagonal` and `off-diagonal` constructors let you apply different
;; specifications to different panel types.

;; ### Heterogeneous SPLOM

;; We can create a SPLOM where diagonal panels use one geometry,
;; and off-diagonal panels use another:

(-> (splom iris [:sepal-length :sepal-width])
    (l* (diagonal :scatter))
    (l* (off-diagonal (l+ (linear))))
    plot)

;; Result:
;; - Diagonal panels: scatter plots (showing x=y)
;; - Off-diagonal panels: scatter + regression (showing relationships)

;; ### Multi-Layer Overrides

;; Override constructors accept full layer specs created with `l+`:

(-> (splom iris [:sepal-length :sepal-width :petal-length])
    (l* (diagonal :scatter))
    (l* (off-diagonal (l+ (linear) (smooth))))
    plot)

;; Now off-diagonal panels have THREE layers: scatter + regression + smooth.

;; ### With Color Groups

(-> (splom iris [:sepal-length :sepal-width :petal-length])
    (l* {:=layers [{:=color :species}]})
    (l* (off-diagonal (l+ (linear))))
    plot)

;; Full colored SPLOM:
;; - All panels show 3 species in different colors
;; - Diagonal: colored histograms
;; - Off-diagonal: colored scatter + 3 colored regression lines

;; ## What We've Built
;;
;; Let's take stock of what we have:
;;
;; 1. **d* and d+** - Dataset-level algebraic operations (pure, no inference)
;; 2. **l* and l+** - Layer-level operators (cross-product and concatenation)
;; 3. **smart-defaults** - Visualization inference (auto-roles, diagonal detection, plottype)
;; 4. **Override Constructors** - `(diagonal ...)` and `(off-diagonal ...)` for control
;; 5. **Composition** - `l*` and `l+` operators for building complex specs
;; 6. **Rendering** - Scatter, histogram, linear regression, and **smoothed curves** to SVG
;; 7. **Grid Layout** - Spatial arrangement of multiple panels **(including asymmetric m×n grids)**
;; 8. **SPLOM** - Scatterplot matrices in one line
;; 9. **Color Aesthetic** - Group by categorical variables with automatic coloring and grouped statistics
;; 10. **Colored SPLOMs** - Multivariate pattern exploration across species groups
;; 11. **Multi-Layer Overlays** - Stack 3+ geometries (scatter + linear + smooth)
;; 12. **Rich Composition** - Combine overrides, colors, and overlays arbitrarily
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
