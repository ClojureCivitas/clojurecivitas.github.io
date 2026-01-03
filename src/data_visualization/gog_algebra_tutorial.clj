;; # The Algebra Behind SPLOM: A Grammar of Graphics Tutorial
;;
;; ## Introduction
;;
;; This notebook explores the core insight from [Leland Wilkinson](https://en.wikipedia.org/wiki/Leland_Wilkinson)'s
;; "[The Grammar of Graphics](https://en.wikipedia.org/wiki/Leland_Wilkinson#The_Grammar_of_Graphics)":
;; complex visualizations like the [scatterplot matrix (SPLOM)](https://en.wikipedia.org/wiki/Scatter_plot#Scatterplot_matrices)
;; aren't special chart types — they *emerge* from simple algebraic operations on variables.
;;
;; As Wilkinson recounted in a [PolicyViz interview](https://policyviz.com/podcast/episode-139-leland-wilkinson/):

;; > "Dan Rope and I sat there, I'd say we worked for maybe two months on that problem,
;; > and all of a sudden, I looked at a symmetric scatterplot matrix, and I said,
;; > oh my God, this thing is a classic [quadratic form](https://en.wikipedia.org/wiki/Quadratic_form).
;; > If you think about it in matrix terms, it's X transpose X, and therefore, it's a product term."
;;
;; The key: `X * X` where X is a set of variables produces a SPLOM — no special-case code.
;;
;; This tutorial implements the algebra in Clojure using [Tablecloth](https://scicloj.github.io/tablecloth/)
;; and [dtype-next](https://github.com/cnuernber/dtype-next), keeping indices explicit
;; throughout to enable brushing/linking across views.

;; ### Acknowledgments
;;
;; This work is part of the ongoing exploration within the
;; [Real-World Data dev group](https://scicloj.github.io/docs/community/groups/real-world-data/),
;; where we're building practical tools for data visualization in Clojure.
;;
;; Special thanks to [**respatialized**](https://respatialized.net/) for insightful comments,
;; for encouraging us to dive deeper into the Grammar of Graphics, and particularly for
;; the thoughtful post "[A Syntax of Graphics](https://respatialized.net/syntax-of-graphics.html)",
;; which helped clarify the distinction between Wilkinson's algebraic foundation and
;; the layered syntax built atop it.

;; ### What We'll Build
;;
;; 1. **Varsets**: The fundamental unit — a mapping from indices to tuples
;; 2. **Operators**: Cross (*), Blend (+), Nest (/) — and their algebraic properties
;; 3. **Frame expansion**: How blend * blend produces multiple frames
;; 4. **Geoms**: Different renderings of the same frame structure
;; 5. **Linked views**: SPLOM + correlation matrix + table, all brushable


;; ## Setup

(ns data-visualization.gog-algebra-tutorial
  (:require
   ;; Tablecloth - Dataset manipulation
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]

   ;; dtype-next - Low-level columnar operations
   [tech.v3.dataset :as ds]
   [tech.v3.datatype :as dtype]
   [tech.v3.datatype.functional :as dfn]

   ;; Kindly - Notebook visualization protocol
   [scicloj.kindly.v4.kind :as kind]
   [scicloj.kindly.v4.api :as kindly]

   ;; Metamorph.ml - Example datasets
   [scicloj.metamorph.ml.toydata :as toydata]))

;; [**Tablecloth**](https://scicloj.github.io/tablecloth/) wraps
;; [tech.ml.dataset](https://github.com/techascent/tech.ml.dataset) with a
;; friendly, pipeline-oriented interface. We mostly use `tc/select-rows` for subsetting,
;; `tc/add-column` for augmenting datasets, and `tc/map-columns` for transformations.
;;
;; [**dtype-next**](https://github.com/cnuernber/dtype-next) (via tech.v3.*) is the columnar engine underneath.
;; Tablecloth datasets are dtype-next datasets, so we can use both APIs interchangeably.
;; Our varsets use `(get dataset :column-name)` to extract column vectors,
;; then `(nth column idx)` for fast indexed access.
;;
;; [**Kindly**](https://scicloj.github.io/kindly-noted/) lets this notebook render in different environments
;; ([Clay](https://scicloj.github.io/clay/), [Portal](https://github.com/djblue/portal), etc.).
;;
;; [**Metamorph.ml**](https://github.com/scicloj/metamorph.ml) provides classic datasets.
;; We use `toydata/iris-ds` to load the full 150-row Iris dataset.


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


;; ## The Data

;;
;; We use the classic [Iris flower dataset](https://en.wikipedia.org/wiki/Iris_flower_data_set):
;; 150 observations, 4 numeric variables, 1 categorical variable (species).
;; Perfect for demonstrating SPLOM.

(def iris-raw
  "The Iris dataset with species decoded to names."
  (-> (toydata/iris-ds)
      (tc/map-columns :species [:species]
                      (fn [code]
                        (case (long code)
                          0 "setosa"
                          1 "versicolor"
                          2 "virginica")))))

(def iris
  "Iris dataset with explicit row indices."
  (tc/add-column iris-raw :index (range (tc/row-count iris-raw))))

;; Quick look at the data:
iris

;; ## Varsets — The Fundamental Unit

;;
;; A **varset** is a function from indices to tuples of values.
;;
;; In Wilkinson's notation:
;;   A varset V has a domain (the set of possible values) and a mapping
;;   from indices (case IDs) to values.
;;
;; Our representation:
;; ```
;;   {:dataset ds        ; the underlying data source
;;    :columns [:col1]   ; which columns define the tuple (can be multiple)
;;    :labels  ["Label"] ; human-readable names (used for axis labels, identity)
;;    :indices [0 1 2...]} ; which rows are "active" in this varset
;; ```
;;
;; The **indices** vector is what allows subsetting (for nesting) and linking (for brushing).
;; All varsets over the same dataset share index semantics — index 7 in varset A is the
;; same observation as index 7 in varset B. We keep indices explicit and materialized
;; throughout this tutorial. In production code, you might use `:all` as a sentinel,
;; but here we make them concrete.

;; Formally, a varset $V$ is a function $V: I \rightarrow D$ where $I$ is a set of
;; indices, $D$ is the domain, and $V(i)$ gives the value for observation $i$.
;; For multi-dimensional varsets, $D = D_1 \times D_2 \times \ldots \times D_k$.

(defn varset
  "Create a varset from dataset columns.
   
   (varset iris :sepal-length)
   (varset iris [:sepal-length :sepal-width] {:labels [\"SL\" \"SW\"]})
   
   Indices default to all rows. Labels default to column names."
  ([ds columns]
   (varset ds columns {}))
  ([ds columns {:keys [labels indices]}]
   (let [cols (if (keyword? columns) [columns] (vec columns))
         lbls (or labels (mapv name cols))
         idxs (or indices (vec (range (tc/row-count ds))))]
     {:type :varset
      :dataset ds
      :columns cols
      :labels lbls
      :indices idxs})))

;; Example: a 1D varset for sepal length
(def sl (varset iris :sepal-length {:labels ["Sepal Length"]}))

;; Let's verify its structure:
(kind/pprint
 (-> sl
     (dissoc :dataset) ; don't print the whole dataset
     (assoc :indices-sample (take 5 (:indices sl)))))

;; The **dimension** of a varset is the number of columns:
(defn varset-dim
  "Return the dimensionality of a varset (number of columns in each tuple)."
  [vs]
  (count (:columns vs)))

(varset-dim sl)

;; ### Accessing Values

;;
;; A varset maps each index to a tuple. Let's make this concrete.

;; ### Understanding Index Semantics
;;
;; The key to understanding varsets is that **indices provide identity**.
;; The same index across different varsets refers to the *same observation*.
;;
;; Let's make this concrete with an example:

;; Create two varsets from different columns
(def vs-sepal-length (varset iris :sepal-length))
(def vs-sepal-width (varset iris :sepal-width))

;; Index 7 refers to the 8th observation in the dataset
;; Let's see what that observation looks like:

;; Index 5 refers to the 6th observation in the dataset
;; Let's see what that observation looks like:

(tc/select-rows iris [5])

;; Now get the values from each varset at index 5:

{:sepal-length-at-5 (varset-value-at vs-sepal-length 5)
 :sepal-width-at-5 (varset-value-at vs-sepal-width 5)
 :same-flower? "Yes! Both refer to observation #5"}

;; This shared index space is what enables:
;;
;; - **Cross**: Combining values from same observation into tuples
;; - **Brushing**: Selecting observations across all views
;; - **Linking**: Highlighting the same observations everywhere
;;
;; Without explicit indices, we couldn't track which data points correspond
;; across different variables and visualizations.

(defn varset-value-at
  "Get the tuple value for a given index."
  [vs idx]
  (let [ds (:dataset vs)]
    (mapv #(nth (get ds %) idx) (:columns vs))))

;; Example: get the sepal length of observation 0
(varset-value-at sl 0)

(defn varset-values
  "Get all values from a varset as a sequence of tuples.
   
   Only includes rows specified by :indices.
   
   Returns: A sequence of vectors, each representing one observation's tuple."
  [vs]
  (map #(varset-value-at vs %) (:indices vs)))

;; Example: first 5 observations
(take 5 (varset-values sl))

;; ### The Domain

;;
;; Wilkinson distinguishes between the *values* (actual data) and the *domain*
;; (the range of possible values). For continuous variables, the domain is
;; typically [min, max]. For categorical variables, it's the set of categories.
;;
;; We compute the domain from the data. In a more complete implementation,
;; you might allow explicit domain specification (e.g., for consistent scales
;; across facets).

(defn column-domain
  "Compute the domain for a single column.
   
   For numeric columns: [min max]
   For categorical columns: sorted vector of unique values
   
   Uses only the rows specified by indices."
  [ds col indices]
  (let [column (get ds col)
        values (map #(nth column %) indices)]
    (if (number? (first values))
      ;; Numeric: return [min max]
      [(apply min values) (apply max values)]
      ;; Categorical: return sorted unique values
      (vec (sort (distinct values))))))

(defn varset-domain
  "Compute the domain of a varset.
   
   Returns a vector of domains, one per column.
   E.g., for a 2D varset: [[0.0 7.9] [0.0 4.4]] (two numeric ranges)"
  [vs]
  (let [{:keys [dataset columns indices]} vs]
    (mapv #(column-domain dataset % indices) columns)))

(varset-domain sl)

;; ## The Cross Operator (*)

;;
;; Cross is the fundamental operation for building multi-dimensional frames.
;;
;; In Wilkinson's algebra:
;;   A * B produces tuples (a, b) for aligned indices
;;
;; The key insight: cross doesn't multiply cases — it concatenates tuple
;; components. If A maps index i to value a_i, and B maps index i to value b_i,
;; then A * B maps index i to tuple (a_i, b_i).
;;
;; This is NOT a Cartesian product of values. It's a join on indices.
;;
;; Visual intuition:
;;   A (1D): index -> [x]
;;   B (1D): index -> [y]
;;   A * B (2D): index -> [x, y]
;;
;; This is why a scatterplot works: we cross X with Y, and each point
;; represents one observation with (x, y) coordinates.

;; In math notation: $(A \times B)(i) = (A(i), B(i))$ — concatenating tuple components
;; for aligned indices. The dimension adds: $\dim(A \times B) = \dim(A) + \dim(B)$.

(defn cross
  "Cross two varsets to make a higher-dimensional one.
   
   Concatenates columns/labels, intersects indices."
  [vs-a vs-b]
  (assert (= :varset (:type vs-a) (:type vs-b))
          "Cross requires two varsets (not blends). Use expand for blends.")
  (assert (identical? (:dataset vs-a) (:dataset vs-b))
          "Cross requires varsets from the same dataset")
  (let [idx-a (set (:indices vs-a))
        idx-b (set (:indices vs-b))
        ;; Intersection preserves order from first varset
        shared-indices (filterv idx-b (:indices vs-a))]
    {:type :varset
     :dataset (:dataset vs-a)
     :columns (vec (concat (:columns vs-a) (:columns vs-b)))
     :labels (vec (concat (:labels vs-a) (:labels vs-b)))
     :indices shared-indices}))

;; Create varsets for all four numeric Iris variables:
(def sl (varset iris :sepal-length {:labels ["Sepal Length"]}))
(def sw (varset iris :sepal-width {:labels ["Sepal Width"]}))
(def pl (varset iris :petal-length {:labels ["Petal Length"]}))
(def pw (varset iris :petal-width {:labels ["Petal Width"]}))

;; Cross two varsets to get a 2D varset (one scatterplot frame):
(def sl-x-sw (cross sl sw))

(-> sl-x-sw
    (dissoc :dataset)
    (assoc :indices-sample (take 5 (:indices sl-x-sw))))

;; Check dimension:
(varset-dim sl-x-sw)

;; Get some values:
(take 5 (varset-values sl-x-sw))

;; ## The Blend Operator (+)

;;
;; Blend is the key to SPLOM. It creates a *superposition* of varsets
;; that expands into multiple alternatives under cross.
;;
;; In Wilkinson's algebra:
;;   A + B produces a union of varsets
;;
;; But the real power comes from distributivity:
;;   (A + B) * (C + D) = A*C + A*D + B*C + B*D
;;
;; This is how X * X for a 4-variable blend produces 16 frames.
;;
;; Our representation:
;;   {:type :blend
;;    :alternatives [{:columns [...] :labels [...]} ...]
;;    :dataset ds
;;    :indices [...]}
;;
;; A blend is a distinct type, not a varset with multiple columns. It's a "superposition"
;; that expands under cross via the distributive law. Blends are specifications, not
;; concrete frames. The labels inside a blend distinguish the alternatives. When we
;; cross (A + B) with (A + B), we get four frames: A*A, A*B, B*A, B*B. The diagonal
;; is where both labels are the same.
;;
;; In notation: $A + B = \{A, B\}$ — a set union, not addition. The power comes from
;; distributivity: $(A + B) \times (C + D) = AC + AD + BC + BD$. Each term becomes
;; a frame in the visualization.

(defn blend
  "Create a blend (union) of varsets.
   
   Arguments:
   - varsets: One or more varsets to blend
   
   All varsets must share the same dataset and indices.
   The result is a blend structure, not a varset.
   
   Example:
     (blend sl sw pl pw)  ; 4-way blend of Iris variables
   
   Note: Each varset becomes an 'alternative' in the blend.
   The columns and labels are preserved per alternative."
  [& varsets]
  (assert (seq varsets) "Blend requires at least one varset")
  (assert (apply = (map :dataset varsets))
          "All blended varsets must share the same dataset")
  (assert (apply = (map :indices varsets))
          "All blended varsets must share the same indices (for now)")
  {:type :blend
   :alternatives (mapv #(select-keys % [:columns :labels]) varsets)
   :dataset (:dataset (first varsets))
   :indices (:indices (first varsets))})

;; Create a blend of all four Iris numeric variables:
(def iris-blend (blend sl sw pl pw))

(-> iris-blend
    (dissoc :dataset)
    (assoc :indices-count (count (:indices iris-blend))))

;; ## Crossing Blends — The Distributive Law

;;
;; When we cross two blends, the distributive law applies:
;;   (A + B) * (C + D) = A*C + A*D + B*C + B*D
;;
;; For SPLOM, we cross a blend with itself:
;;   (A + B + C + D)^2 = 16 terms
;;
;; Each term is a 2D varset (a frame for one cell of the SPLOM).
;;
;; Implementation strategy:
;; - cross-blend takes two blends and returns a new blend
;; - The new blend has (n × m) alternatives where n and m are
;;   the number of alternatives in the inputs
;; - Each new alternative has concatenated columns and labels

(defn cross-alternatives
  "Cross two individual alternatives (from within blends).
   
   Returns a new alternative with concatenated columns and labels."
  [alt-a alt-b]
  {:columns (vec (concat (:columns alt-a) (:columns alt-b)))
   :labels (vec (concat (:labels alt-a) (:labels alt-b)))})

(defn cross-blend
  "Cross two blends, applying the distributive law.
   
   If A has n alternatives and B has m alternatives,
   the result has n*m alternatives.
   
   This is the core of SPLOM generation:
     (cross-blend (blend sl sw pl pw) (blend sl sw pl pw))
   produces 16 alternatives, one for each cell."
  [blend-a blend-b]
  (assert (= :blend (:type blend-a) (:type blend-b))
          "cross-blend requires two blends")
  (assert (identical? (:dataset blend-a) (:dataset blend-b))
          "Blends must share the same dataset")
  (let [new-alternatives
        (for [alt-a (:alternatives blend-a)
              alt-b (:alternatives blend-b)]
          (cross-alternatives alt-a alt-b))
        ;; Intersect indices (usually identical)
        idx-a (set (:indices blend-a))
        idx-b (set (:indices blend-b))
        shared-indices (filterv idx-b (:indices blend-a))]
    {:type :blend
     :alternatives (vec new-alternatives)
     :dataset (:dataset blend-a)
     :indices shared-indices}))

;; ### The Distributive Law: A Concrete Example
;;
;; Before we see the full 4×4 SPLOM, let's understand the distributive law
;; with a simple 2×2 example.
;;
;; The algebra: $(A + B) \times (C + D) = AC + AD + BC + BD$
;;
;; Let's see this with actual data:

;; Create a 2-variable blend for x-axis
(def blend-x (blend sl sw)) ; Sepal Length + Sepal Width

;; Create a 2-variable blend for y-axis  
(def blend-y (blend pl pw)) ; Petal Length + Petal Width

;; Cross them using the distributive law
(def grid-2x2 (cross-blend blend-x blend-y))

;; What did we get?
{:x-variables 2
 :y-variables 2
 :total-frames (count (:alternatives grid-2x2))
 :frame-labels (map :labels (:alternatives grid-2x2))}

;; Let's visualize the grid structure:
;;
;; ```
;;        SL              SW
;;    ┌─────────┐    ┌─────────┐
;; PL │ SL × PL │    │ SW × PL │
;;    └─────────┘    └─────────┘
;;    
;; PW │ SL × PW │    │ SW × PW │
;;    └─────────┘    └─────────┘
;; ```
;;
;; Each cell is a 2D frame with coordinates from one x-variable and one y-variable.
;;
;; Now expand and position in a grid:

(def frames-2x2 (vec (expand-blend grid-2x2)))
(def positioned-2x2 (vec (frames-grid-positions frames-2x2)))

(map #(select-keys % [:x-label :y-label :grid-row :grid-col])
     positioned-2x2)

;; Notice: No cell has the same variable on both axes (no diagonal).
;; That only happens when we cross a blend with *itself*.

;; ### SPLOM Emerges from $X \times X$
;;
;; Now for the key insight: Cross a blend with **itself** to get a SPLOM.
;;
;; If $X = A + B + C + D$, then $X \times X$ produces 16 terms.
;;
;; ```
;;        A         B         C         D
;;    ┌────────┬────────┬────────┬────────┐
;;  A │ A × A  │ A × B  │ A × C  │ A × D  │  Row 0
;;    ├────────┼────────┼────────┼────────┤
;;  B │ B × A  │ B × B  │ B × C  │ B × D  │  Row 1
;;    ├────────┼────────┼────────┼────────┤
;;  C │ C × A  │ C × B  │ C × C  │ C × D  │  Row 2
;;    ├────────┼────────┼────────┼────────┤
;;  D │ D × A  │ D × B  │ D × C  │ D × D  │  Row 3
;;    └────────┴────────┴────────┴────────┘
;;     Col 0    Col 1    Col 2    Col 3
;; ```
;;
;; The diagonal cells (A×A, B×B, C×C, D×D) are special:
;; Same variable on both axes → 1D distribution → **histogram**
;;
;; Off-diagonal cells have different variables:
;; Different variables → 2D relationship → **scatterplot**

;; Let's verify this with the full Iris 4-variable blend:

;; Cross the iris blend with itself:
(def iris-splom-blend (cross-blend iris-blend iris-blend))

;; Check: we should have 16 alternatives
(count (:alternatives iris-splom-blend))

;; Look at a few alternatives:
(take 4 (:alternatives iris-splom-blend))

;; ## Expanding Blends into Frames

;;
;; A blend is a specification. To render it, we need to expand it into
;; concrete frames (varsets).
;;
;; Each alternative in a blend becomes a frame. The frame carries:
;; - The 2D varset structure (columns, labels, indices)
;; - Metadata for rendering (row/column position in a grid, etc.)
;;
;; For SPLOM, we also want to know the grid position. Since the labels
;; determine this (row = y-label, col = x-label), we can derive position
;; from the label structure.

(defn expand-blend
  "Expand a blend into a sequence of varsets (frames).
   
   Each alternative becomes a concrete varset with full structure.
   
   Returns a sequence of varsets, each ready for rendering."
  [b]
  (assert (= :blend (:type b)) "expand-blend requires a blend")
  (for [alt (:alternatives b)]
    {:type :varset
     :dataset (:dataset b)
     :columns (:columns alt)
     :labels (:labels alt)
     :indices (:indices b)}))

(def iris-frames (expand-blend iris-splom-blend))

;; We have 16 frames:
(count iris-frames)

;; Each frame is a 2D varset:
(let [frame (first iris-frames)]
  (-> frame
      (dissoc :dataset)
      (assoc :indices-count (count (:indices frame)))))

;; ### Organizing Frames into a Grid

;;
;; For SPLOM layout, we need to organize 16 frames into a 4×4 grid.
;; The row corresponds to the y-variable, the column to the x-variable.
;;
;; The label structure tells us everything we need:
;;   :labels ["Sepal Length" "Petal Width"]
;;   means x = "Sepal Length", y = "Petal Width"
;;
;; We derive grid position from the unique labels.

(defn frames-grid-positions
  "Annotate frames with their grid positions.
   
   Returns frames with added :grid-row, :grid-col, :x-label, :y-label keys.
   
   The grid is organized so that:
   - Rows correspond to y-variable (second label)
   - Columns correspond to x-variable (first label)
   - Order is determined by first occurrence in alternatives"
  [frames]
  (let [;; Extract unique labels in order of first appearance
        x-labels (distinct (map #(first (:labels %)) frames))
        y-labels (distinct (map #(second (:labels %)) frames))
        x-idx (into {} (map-indexed (fn [i l] [l i]) x-labels))
        y-idx (into {} (map-indexed (fn [i l] [l i]) y-labels))]
    (map (fn [frame]
           (let [[x-label y-label] (:labels frame)]
             (assoc frame
                    :x-label x-label
                    :y-label y-label
                    :grid-col (x-idx x-label)
                    :grid-row (y-idx y-label))))
         frames)))

(def iris-grid-frames (frames-grid-positions iris-frames))

;; Check a few frames:
(map #(select-keys % [:x-label :y-label :grid-row :grid-col])
     (take 5 iris-grid-frames))

;; ### Detecting the Diagonal

;;
;; The diagonal of a SPLOM is where x-variable = y-variable.
;; These cells show the distribution of a single variable.
;;
;; This is NOT detected by grid position (row = col).
;; It's detected by label identity (x-label = y-label).
;;
;; Why the distinction matters:
;; - In a symmetric SPLOM, position and label coincide
;; - In an asymmetric cross (different variables on rows vs columns),
;;   there may be no diagonal
;; - The label-based rule is the correct semantic

;; ### Detecting the Diagonal
;;
;; The diagonal of a SPLOM is where `x-variable = y-variable`.
;; These cells are fundamentally different from off-diagonal cells:
;;
;; Diagonal (e.g., Sepal Length × Sepal Length):
;;
;; - Both axes show the *same* variable
;; - Each point would plot at $(v, v)$ — all points on the line $y = x$
;; - This shows the distribution of one variable, not relationships
;; - Best rendered as: histogram or density plot
;;
;; Off-diagonal (e.g., Sepal Length × Petal Length):
;;
;; - Axes show *different* variables  
;; - Points plot at $(x_i, y_i)$ — scattered across the plane
;; - Shows the relationship between two variables
;; - Best rendered as: scatterplot
;;
;; We detect the diagonal by checking if labels match (not grid position). Labels carry
;; identity — when we blend variables, labels distinguish them. The diagonal of a SPLOM
;; is where `(= x-label y-label)`. This is semantic, not positional, so it works even
;; for asymmetric crosses where rows and columns have different variables.

(defn diagonal-frame?
  "Is this frame on the diagonal (same variable on both axes)?
   
   Returns true when x-label equals y-label, indicating this frame
   shows the distribution of a single variable rather than the
   relationship between two variables."
  [frame]
  (let [[x-label y-label] (:labels frame)]
    (= x-label y-label)))

;; Example: Check which frames are diagonal

(def diagonal-frames (filter diagonal-frame? iris-grid-frames))
(def off-diagonal-frames (remove diagonal-frame? iris-grid-frames))

{:diagonal-count (count diagonal-frames)
 :off-diagonal-count (count off-diagonal-frames)
 :diagonal-labels (map :x-label diagonal-frames)
 :expected-diagonals 4}

;; As expected: 4 diagonal frames (one per variable), 12 off-diagonal frames.

;; Check diagonal frames:
(filter diagonal-frame? iris-grid-frames)

(map #(select-keys % [:x-label :y-label :grid-row :grid-col])
     (filter diagonal-frame? iris-grid-frames))

;; ## The Nest Operator (/)

;;
;; Nest partitions a varset by the values of a grouping variable.
;;
;; In Wilkinson's algebra:
;;   A / B produces varsets where A's domain is conditioned on B's values
;;
;; In practice, this means:
;;   (nest varset :species) produces three varsets, one per species,
;;   each with a subset of indices.
;;
;; This enables:
;; - Faceted SPLOMs (one per species)
;; - Per-group statistics (regression lines per group)
;; - Colored points by group
;;
;; Implementation: We use Tablecloth's group-by to get row indices per group.

;; In notation: $A / G = \{A|_{G=g_1}, A|_{G=g_2}, \ldots\}$ where $A|_{G=g}$ restricts
;; $A$ to indices where grouping variable $G$ equals $g$. This creates separate varsets
;; for each group, enabling faceted displays.

(defn nest
  "Partition a varset by a grouping column.
   
   Returns a sequence of varsets, one per group, each with:
   - A subset of the original indices (only rows in that group)
   - A :facet-value key indicating which group
   
   Arguments:
   - vs: A varset (or blend)
   - group-col: Column name to group by (e.g., :species)
   
   Example:
     (nest sl :species)"
  [vs group-col]
  (let [ds (:dataset vs)
        indices (:indices vs)
        group-column (get ds group-col)
        ;; Get the group value for each index
        groups (group-by #(nth group-column %) indices)]
    (for [[group-value group-indices] (sort-by first groups)]
      (assoc vs
             :indices (vec group-indices)
             :facet-value group-value
             :facet-column group-col))))

;; Nest sepal-length by species:
(def sl-by-species (nest sl :species))

(map #(-> %
          (dissoc :dataset)
          (select-keys [:labels :facet-value :indices]))
     sl-by-species)

;; ### Nesting Blends

;;
;; We can also nest a blend, producing a sequence of blends, each with
;; different indices.

(defn nest-blend
  "Partition a blend by a grouping column.
   
   Returns a sequence of blends, one per group."
  [b group-col]
  (let [ds (:dataset b)
        indices (:indices b)
        group-column (get ds group-col)
        groups (group-by #(nth group-column %) indices)]
    (for [[group-value group-indices] (sort-by first groups)]
      (assoc b
             :indices (vec group-indices)
             :facet-value group-value
             :facet-column group-col))))

;; Nest the iris blend by species:
(def iris-blend-by-species (nest-blend iris-blend :species))

;; Three blends, one per species:
(count iris-blend-by-species)

(map #(-> %
          (dissoc :dataset :alternatives)
          (assoc :n-indices (count (:indices %))))
     iris-blend-by-species)

;; ## Frames and Geoms

;;
;; A frame is a 2D varset — it specifies which data to plot.
;; A geom specifies *how* to render that data.
;;
;; The same frame can be rendered as:
;; - :scatter — points at (x, y) positions
;; - :histogram — bars showing distribution (for diagonal frames)
;; - :regression — fitted line
;; - :correlation — single number (the correlation coefficient)
;;
;; The algebra produces frames (what data to show), geoms decide how to visualize it
;; (scatter plot, histogram, correlation coefficient, etc.).

(defn frame->geom-type
  "Determine the appropriate geom type for a frame.
   
   Rules:
   - Diagonal frames (x-label = y-label): :histogram
   - Off-diagonal frames: :scatter
   
   This is a simple default. Users can override."
  [frame]
  (if (diagonal-frame? frame)
    :histogram
    :scatter))

;; Annotate frames with geom types:
(def iris-frames-with-geoms
  (map #(assoc % :geom (frame->geom-type %)) iris-grid-frames))

(map #(select-keys % [:x-label :y-label :geom])
     (take 6 iris-frames-with-geoms))

;; ## Computing Statistics on Frames

;;
;; Some geoms require derived data:
;; - :histogram needs bin counts
;; - :regression needs slope and intercept
;; - :correlation needs the correlation coefficient
;;
;; These computations use the frame's indices to subset the data.

(defn frame-column-values
  "Extract the values of a specific column for a frame's indices.
   
   Arguments:
   - frame: A frame (varset)
   - col-idx: Which column to extract (0 = x, 1 = y for 2D frames)
   
   Returns a vector of values."
  [frame col-idx]
  (let [col-name (nth (:columns frame) col-idx)
        column (get (:dataset frame) col-name)]
    (mapv #(nth column %) (:indices frame))))

;; Get x-values for a frame:
(take 5 (frame-column-values (first iris-grid-frames) 0))

(defn frame-correlation
  "Compute [Pearson correlation](https://en.wikipedia.org/wiki/Pearson_correlation_coefficient) for a 2D frame.
   
   Returns a number in [-1, 1], or nil for diagonal frames."
  [frame]
  (when-not (diagonal-frame? frame)
    (let [xs (frame-column-values frame 0)
          ys (frame-column-values frame 1)
          n (count xs)
          mean-x (/ (reduce + xs) n)
          mean-y (/ (reduce + ys) n)
          ;; Covariance
          cov (/ (reduce + (map #(* (- %1 mean-x) (- %2 mean-y)) xs ys)) n)
          ;; Standard deviations
          std-x (Math/sqrt (/ (reduce + (map #(Math/pow (- % mean-x) 2) xs)) n))
          std-y (Math/sqrt (/ (reduce + (map #(Math/pow (- % mean-y) 2) ys)) n))]
      (if (and (pos? std-x) (pos? std-y))
        (/ cov (* std-x std-y))
        0.0))))

;; Compute correlation for first off-diagonal frame:
(let [frame (first (remove diagonal-frame? iris-grid-frames))]
  {:labels (:labels frame)
   :correlation (frame-correlation frame)})

(defn frame-histogram-bins
  "Compute histogram bins for a diagonal frame.
   
   Arguments:
   - frame: A diagonal frame (x-label = y-label)
   - n-bins: Number of bins (default 10)
   
   Returns a sequence of {:bin-start :bin-end :count} maps."
  ([frame] (frame-histogram-bins frame 10))
  ([frame n-bins]
   (when (diagonal-frame? frame)
     (let [values (frame-column-values frame 0)
           min-val (apply min values)
           max-val (apply max values)
           ;; Add small epsilon to max to include it in last bin
           range-val (- max-val min-val)
           bin-width (/ range-val n-bins)
           ;; Assign each value to a bin
           bin-index (fn [v]
                       (min (dec n-bins)
                            (int (/ (- v min-val) bin-width))))
           bin-counts (frequencies (map bin-index values))]
       (for [i (range n-bins)]
         {:bin-start (+ min-val (* i bin-width))
          :bin-end (+ min-val (* (inc i) bin-width))
          :count (get bin-counts i 0)})))))

;; Compute histogram for a diagonal frame:
(let [frame (first (filter diagonal-frame? iris-grid-frames))]
  {:label (:x-label frame)
   :bins (frame-histogram-bins frame 5)})

;; ## Selection and Brushing

;;
;; [Brushing and linking](https://en.wikipedia.org/wiki/Brushing_and_linking) is the
;; interactive selection of points in one view, with selection propagating to all linked views.
;;
;; The key insight: selection is an *index set*.
;;
;; When you brush a rectangle in one SPLOM cell:
;; 1. Convert rectangle bounds to data ranges
;; 2. Filter indices where (x, y) is within the rectangle
;; 3. The resulting index set is the selection
;; 4. All views highlight points whose index is in the selection
;;
;; This works because all frames share index semantics.

(defn brush-select
  "Compute selected indices for a brush rectangle on a frame.
   
   Arguments:
   - frame: The frame being brushed
   - x-range: [x-min x-max] in data coordinates
   - y-range: [y-min y-max] in data coordinates
   
   Returns a set of selected indices."
  [frame [x-min x-max] [y-min y-max]]
  (let [ds (:dataset frame)
        [x-col y-col] (:columns frame)
        x-column (get ds x-col)
        y-column (get ds y-col)]
    (set
     (filter (fn [idx]
               (let [x (nth x-column idx)
                     y (nth y-column idx)]
                 (and (<= x-min x x-max)
                      (<= y-min y y-max))))
             (:indices frame)))))

;; Example: select points in a region
(let [frame (first (remove diagonal-frame? iris-grid-frames))]
  {:frame-labels (:labels frame)
   :n-selected (count (brush-select frame [5.0 6.0] [3.0 4.0]))})

(defn highlight-frame
  "Annotate a frame with which of its points are selected.
   
   Arguments:
   - frame: A frame
   - selection: A set of selected indices
   
   Returns the frame with :selected-indices key added."
  [frame selection]
  (let [frame-selection (filter selection (:indices frame))]
    (assoc frame
           :selected-indices (vec frame-selection)
           :n-selected (count frame-selection))))

;; Example: highlight all frames based on a selection
(let [selection #{0 1 2 3 4 5} ; first 6 observations
      highlighted (map #(highlight-frame % selection) iris-grid-frames)]
  (map #(select-keys % [:x-label :y-label :n-selected])
       (take 4 highlighted)))

;; ## Rendering to D3 Specification

;;
;; We now have all the algebraic machinery. The final step is rendering.
;;
;; Our strategy: produce a data structure that [D3.js](https://d3js.org/) can consume.
;; The D3 code (in JavaScript) handles the actual drawing.
;;
;; The Clojure side produces:
;; - Grid layout specification
;; - Per-cell data (points, histogram bins, or correlation value)
;; - Selection state
;;
;; We use Kindly's :kind/hiccup for embedding D3 visualizations.

(defn frame->scatter-data
  "Convert a frame to scatter plot data for D3.
   
   Returns a sequence of {:x :y :index :selected?} maps."
  [frame selection]
  (let [ds (:dataset frame)
        [x-col y-col] (:columns frame)
        x-column (get ds x-col)
        y-column (get ds y-col)
        selected-set (or selection #{})]
    (for [idx (:indices frame)]
      {:x (nth x-column idx)
       :y (nth y-column idx)
       :index idx
       :selected (contains? selected-set idx)})))

(defn frame->histogram-data
  "Convert a diagonal frame to histogram data for D3."
  [frame]
  (frame-histogram-bins frame 10))

(defn frame->d3-spec
  "Convert a frame to a D3-compatible specification.
   
   Arguments:
   - frame: A frame with :geom, :grid-row, :grid-col
   - selection: Set of selected indices (or nil)
   
   Returns a map with rendering instructions."
  [frame selection]
  (let [base {:grid-row (:grid-row frame)
              :grid-col (:grid-col frame)
              :x-label (:x-label frame)
              :y-label (:y-label frame)
              :geom (:geom frame)
              :x-domain (first (varset-domain frame))
              :y-domain (second (varset-domain frame))}]
    (case (:geom frame)
      :scatter
      (assoc base :data (frame->scatter-data frame selection))

      :histogram
      (assoc base
             :data (frame->histogram-data frame)
             :x-domain (first (varset-domain frame)))

      ;; Default: include raw data
      base)))

;; Generate D3 specs for all frames:
(defn splom->d3-spec
  "Convert all SPLOM frames to a complete D3 specification.
   
   Arguments:
   - frames: Sequence of annotated frames
   - selection: Set of selected indices (or nil for no selection)
   
   Returns a map with :n-rows, :n-cols, :frames"
  [frames selection]
  (let [n-rows (inc (apply max (map :grid-row frames)))
        n-cols (inc (apply max (map :grid-col frames)))
        ;; Get unique labels in order
        labels (distinct (map :x-label frames))]
    {:n-rows n-rows
     :n-cols n-cols
     :labels labels
     :frames (mapv #(frame->d3-spec % selection) frames)}))

;; Generate the full SPLOM spec:
(def iris-splom-spec (splom->d3-spec iris-frames-with-geoms nil))

(-> iris-splom-spec
    (update :frames #(take 2 %))) ; Just show first 2 frames

;; ## D3 Rendering Code

;;
;; This section contains the D3 JavaScript code that renders the SPLOM.
;; We embed it as a Kindly hiccup structure with a script tag.
;;
;; The D3 code is kept generic — it doesn't know about SPLOM specifically.
;; It just renders a grid of cells, each containing either scatter points
;; or histogram bars.

;; First, let's create a helper to generate the D3 visualization:

(defn splom-hiccup
  "Generate hiccup for a D3 SPLOM visualization.
   
   Arguments:
   - spec: The SPLOM specification from splom->d3-spec
   - opts: Rendering options
     - :cell-size (default 100)
     - :margin (default 10)
   
   Returns a hiccup structure with embedded D3 code."
  [spec {:keys [cell-size margin]
         :or {cell-size 100 margin 30}}]
  (let [n-rows (:n-rows spec)
        n-cols (:n-cols spec)
        width (+ (* n-cols cell-size) (* 2 margin))
        height (+ (* n-rows cell-size) (* 2 margin))
        spec-json (pr-str spec)] ; Will need proper JSON encoding
    [:div
     {:id "splom-container"
      :style {:width (str width "px")
              :height (str height "px")}}

     ;; Note: In a real implementation, we'd use proper JSON encoding
     ;; and load D3 via a script tag. This is a sketch.
     [:script
      (str "
// SPLOM D3 Rendering Code
// This is a placeholder — in practice, you'd load D3 and implement
// the full rendering logic.

console.log('SPLOM spec:', " spec-json ");

// Key rendering steps:
// 1. Create SVG with grid layout
// 2. For each cell:
//    - If scatter: render points with x/y scales
//    - If histogram: render bars with count scale
// 3. Add axes and labels
// 4. Set up brush interaction
// 5. On brush: compute selected indices, update all cells
")]]))

;; Generate the visualization (placeholder):
;; (kind/hiccup (splom-hiccup iris-splom-spec {:cell-size 80}))

;; ## Linked Views — The Dashboard

;;
;; A linked dashboard combines multiple views over the same data,
;; all connected through the selection (index set).
;;
;; Views we can include:
;; - SPLOM (scatter/histogram grid)
;; - Correlation matrix (same frame structure, different geom)
;; - Summary statistics (aggregates per variable)
;; - Data table (shows selected rows)
;;
;; The algebra connects them: SPLOM and correlation matrix use the same
;; (blend * blend) structure. The summary and table use different structures
;; but share the dataset and selection.

(defn correlation-matrix-spec
  "Generate a correlation matrix specification.
   
   Uses the same frame structure as SPLOM, but computes correlations
   instead of plotting points.
   
   Diagonal entries are 1.0 (correlation with self)."
  [frames]
  (for [frame frames]
    (let [[x-label y-label] [(:x-label frame) (:y-label frame)]]
      {:grid-row (:grid-row frame)
       :grid-col (:grid-col frame)
       :x-label x-label
       :y-label y-label
       :correlation (if (= x-label y-label)
                      1.0
                      (frame-correlation frame))})))

(def iris-corr-matrix (correlation-matrix-spec iris-grid-frames))

(kind/pprint
 (take 5 iris-corr-matrix))

(defn summary-stats-spec
  "Generate summary statistics for each variable in a blend.
   
   Arguments:
   - blend: A blend of variables
   - selection: Set of selected indices (uses all if nil)
   
   Returns a sequence of {:label :mean :std :n} maps."
  [blend selection]
  (let [indices (if (seq selection)
                  (vec (filter selection (:indices blend)))
                  (:indices blend))
        ds (:dataset blend)]
    (for [alt (:alternatives blend)]
      (let [col-name (first (:columns alt))
            column (get ds col-name)
            values (mapv #(nth column %) indices)
            n (count values)
            mean (if (pos? n) (/ (reduce + values) n) 0)
            variance (if (> n 1)
                       (/ (reduce + (map #(Math/pow (- % mean) 2) values))
                          (dec n))
                       0)]
        {:label (first (:labels alt))
         :mean (double mean)
         :std (Math/sqrt variance)
         :n n}))))

;; Summary stats for all data:
(summary-stats-spec iris-blend nil)

;; Summary stats for a selection:
(summary-stats-spec iris-blend #{0 1 2 3 4 5 6 7 8 9})

(defn data-table-spec
  "Generate a data table showing selected rows.
   
   Arguments:
   - ds: The dataset
   - columns: Which columns to show
   - selection: Set of selected indices (shows all if nil)
   - max-rows: Maximum rows to include (default 100)"
  [ds columns selection & {:keys [max-rows] :or {max-rows 100}}]
  (let [indices (if (seq selection)
                  (vec (take max-rows (filter selection (range (tc/row-count ds)))))
                  (vec (take max-rows (range (tc/row-count ds)))))]
    {:columns columns
     :indices indices
     :rows (for [idx indices]
             (into {:_index idx}
                   (map (fn [col] [col (nth (get ds col) idx)]) columns)))}))

;; Data table for first 10 observations:
(-> (data-table-spec iris [:sepal-length :sepal-width :species]
                     #{0 1 2 3 4 5 6 7 8 9})
    (update :rows #(take 5 %)))

;; ## Putting It All Together — The Dashboard Structure

;;
;; A complete dashboard specification combines all views with shared state.

(defn dashboard-spec
  "Generate a complete dashboard specification.
   
   Arguments:
   - ds: The dataset
   - blend: The variable blend for SPLOM/correlation
   - selection: Set of selected indices (or nil)
   
   Returns a map with all view specifications."
  [ds blend selection]
  (let [;; Generate SPLOM frames
        splom-blend (cross-blend blend blend)
        frames (-> (expand-blend splom-blend)
                   (frames-grid-positions)
                   (#(map (fn [f] (assoc f :geom (frame->geom-type f))) %)))]
    {:dataset-info {:n-rows (tc/row-count ds)
                    :n-cols (tc/column-count ds)
                    :columns (tc/column-names ds)}

     :selection {:indices (vec selection)
                 :n-selected (count selection)}

     :views
     {:splom
      {:type :splom
       :spec (splom->d3-spec frames selection)}

      :correlation-matrix
      {:type :correlation
       :spec (correlation-matrix-spec frames)}

      :summary
      {:type :table
       :spec (summary-stats-spec blend selection)}

      :data-table
      {:type :table
       :spec (data-table-spec ds
                              [:sepal-length :sepal-width :petal-length
                               :petal-width :species]
                              selection
                              :max-rows 20)}}}))

;; Generate a dashboard with no selection:
(def dashboard-no-selection (dashboard-spec iris iris-blend nil))

(-> dashboard-no-selection
    (assoc-in [:views :splom :spec :frames] "[16 frames...]")
    (assoc-in [:views :data-table :spec :rows] "[rows...]"))

;; Generate a dashboard with a selection:
(def selection-example #{0 1 2 3 4 10 11 12 13 14 20 21 22 23 24})
(def dashboard-with-selection (dashboard-spec iris iris-blend selection-example))

(:summary (:views dashboard-with-selection))

;; ## Faceted SPLOM

;;
;; The nest operator allows us to create faceted SPLOMs — one SPLOM per group.
;;
;; For Iris, we can create three SPLOMs (one per species) to see if the
;; correlation patterns differ between species.

(defn faceted-splom-spec
  "Generate specifications for a faceted SPLOM.
   
   Arguments:
   - blend: The variable blend
   - facet-col: Column to facet by
   
   Returns a sequence of {:facet-value :splom-spec} maps."
  [blend facet-col]
  (let [nested-blends (nest-blend blend facet-col)]
    (for [nested nested-blends]
      (let [splom-blend (cross-blend nested nested)
            frames (-> (expand-blend splom-blend)
                       (frames-grid-positions)
                       (#(map (fn [f] (assoc f :geom (frame->geom-type f))) %)))]
        {:facet-value (:facet-value nested)
         :n-observations (count (:indices nested))
         :splom (splom->d3-spec frames nil)
         :correlations (correlation-matrix-spec frames)}))))

(def iris-faceted-splom (faceted-splom-spec iris-blend :species))

(map #(-> %
          (select-keys [:facet-value :n-observations])
          (assoc :n-frames (count (get-in % [:splom :frames]))))
     iris-faceted-splom)

;; ## Summary: The Algebra at Work

;;
;; Let's trace how SPLOM emerges from the algebra:
;;
;; 1. Define variables as 1D varsets:
;;    sl = {:columns [:sepal-length], :labels ["SL"], :indices [0..149]}
;;
;; 2. Blend them (union of alternatives):
;;    X = sl + sw + pl + pw
;;    X = {:type :blend, :alternatives [{...} {...} {...} {...}]}
;;
;; 3. Cross the blend with itself:
;;    X * X applies distributive law:
;;    (a+b+c+d) * (a+b+c+d) = aa + ab + ac + ad + ba + bb + ... (16 terms)
;;
;; 4. Each term is a 2D varset (frame):
;;    aa = {:columns [:sepal-length :sepal-length], :labels ["SL" "SL"]}
;;    ab = {:columns [:sepal-length :sepal-width], :labels ["SL" "SW"]}
;;
;; 5. The diagonal is where labels match:
;;    (= (first labels) (second labels)) => histogram, not scatter
;;
;; 6. Nest partitions by group:
;;    (nest X :species) => three blends with different indices
;;    Cross each with itself => three SPLOMs
;;
;; 7. Selection is an index set:
;;    Brushing produces indices; all views filter by those indices
;;
;; The SPLOM was never "implemented" — it emerged from the algebra.

;; ## Appendix: Remaining Questions and Future Work

;;
;; ### Open Design Questions
;;
;; 1. **Asymmetric cross notation**: 
;;    How do we distinguish X * Y (where X and Y are different blends)
;;    from X * X (symmetric SPLOM)? Currently just different arguments.
;;    Could add syntax like (cross-symmetric X) vs (cross X Y).
;;
;; 2. **Computed columns**:
;;    What if we want to include regression residuals in the blend?
;;    These are derived columns, not in the original dataset.
;;    Option: Allow varsets to reference computed columns added to ds.
;;
;; 3. **Scale sharing**:
;;    SPLOM cells in the same row should share y-scale; same column
;;    share x-scale. How do we express this in the spec?
;;    Currently implicit in grid position.
;;
;; 4. **Multiple blends in one cross**:
;;    What about (cross (blend a b) (blend c d))? This gives 4 frames,
;;    but arranged 2×2. Need to generalize grid positioning.
;;
;; ### Performance Considerations
;;
;; 1. **Lazy frames**: 
;;    For large datasets, we might not want to materialize all frame
;;    data upfront. Could generate on-demand during rendering.
;;
;; 2. **Index compression**:
;;    If indices are often "all rows", could use :all sentinel.
;;    But explicit indices are clearer pedagogically.
;;
;; 3. **Brushing at scale**:
;;    For millions of points, brush selection needs spatial indexing
;;    (quadtree, R-tree). Current implementation is O(n) per brush.
;;
;; ### Extensions
;;
;; 1. **Parallel coordinates**:
;;    Different visual paradigm — each variable is an axis, lines
;;    connect values for same observation. The algebra would be
;;    different (not cross).
;;
;; 2. **3D varsets**:
;;    Cross three 1D varsets to get 3D. Rendering becomes 3D scatter.
;;    Same algebra, different renderer.
;;
;; 3. **Aggregation geoms**:
;;    Heatmap (bin 2D, count), contour (kernel density), etc.
;;    Frame structure is the same; computation differs.
;;
;; 4. **Statistical annotations**:
;;    Regression lines, confidence ellipses, etc. These are additional
;;    layers on frames, computed from frame data.

;; ## Appendix: Complete D3 Rendering Code (for reference)

;;
;; Below is a sketch of the D3 code that would render the SPLOM.
;; This would be loaded in the browser; the Clojure side just
;; provides the data.

(def d3-splom-code
  "
// ============================================================
// D3 SPLOM Rendering Code
// ============================================================
//
// This code renders a scatterplot matrix from a specification
// produced by the Clojure algebra.
//
// Expected spec structure:
// {
//   n_rows: 4,
//   n_cols: 4,
//   labels: ['SL', 'SW', 'PL', 'PW'],
//   frames: [
//     {
//       grid_row: 0, grid_col: 0,
//       x_label: 'SL', y_label: 'SL',
//       geom: 'histogram',
//       data: [{bin_start: 4.0, bin_end: 4.5, count: 10}, ...],
//       x_domain: [4.0, 8.0]
//     },
//     {
//       grid_row: 0, grid_col: 1,
//       x_label: 'SW', y_label: 'SL',
//       geom: 'scatter',
//       data: [{x: 3.5, y: 5.1, index: 0, selected: false}, ...],
//       x_domain: [2.0, 4.5],
//       y_domain: [4.0, 8.0]
//     },
//     ...
//   ]
// }

function renderSPLOM(spec, container, options = {}) {
  const cellSize = options.cellSize || 100;
  const margin = options.margin || 30;
  const padding = options.padding || 10;
  
  const {n_rows, n_cols, labels, frames} = spec;
  const width = n_cols * cellSize + 2 * margin;
  const height = n_rows * cellSize + 2 * margin;
  
  // Create SVG
  const svg = d3.select(container)
    .append('svg')
    .attr('width', width)
    .attr('height', height);
  
  // Create group for each cell
  const cells = svg.selectAll('.cell')
    .data(frames)
    .enter()
    .append('g')
    .attr('class', 'cell')
    .attr('transform', d => 
      `translate(${margin + d.grid_col * cellSize}, 
                 ${margin + d.grid_row * cellSize})`);
  
  // Render each cell based on geom type
  cells.each(function(frame) {
    const cell = d3.select(this);
    const innerSize = cellSize - 2 * padding;
    
    if (frame.geom === 'scatter') {
      renderScatter(cell, frame, innerSize, padding);
    } else if (frame.geom === 'histogram') {
      renderHistogram(cell, frame, innerSize, padding);
    }
  });
  
  // Add labels
  // ... (axis labels, grid labels, etc.)
  
  // Set up brushing
  setupBrush(svg, frames, cellSize, margin, onBrush);
}

function renderScatter(cell, frame, size, padding) {
  const xScale = d3.scaleLinear()
    .domain(frame.x_domain)
    .range([padding, size + padding]);
  
  const yScale = d3.scaleLinear()
    .domain(frame.y_domain)
    .range([size + padding, padding]);  // Inverted for SVG
  
  cell.selectAll('circle')
    .data(frame.data)
    .enter()
    .append('circle')
    .attr('cx', d => xScale(d.x))
    .attr('cy', d => yScale(d.y))
    .attr('r', 3)
    .attr('fill', d => d.selected ? '#e41a1c' : '#377eb8')
    .attr('opacity', d => d.selected ? 1.0 : 0.6)
    .attr('data-index', d => d.index);  // For brushing
}

function renderHistogram(cell, frame, size, padding) {
  const xScale = d3.scaleLinear()
    .domain(frame.x_domain)
    .range([padding, size + padding]);
  
  const maxCount = d3.max(frame.data, d => d.count);
  const yScale = d3.scaleLinear()
    .domain([0, maxCount])
    .range([size + padding, padding]);
  
  const barWidth = (size - 2 * padding) / frame.data.length;
  
  cell.selectAll('rect')
    .data(frame.data)
    .enter()
    .append('rect')
    .attr('x', d => xScale(d.bin_start))
    .attr('y', d => yScale(d.count))
    .attr('width', barWidth - 1)
    .attr('height', d => size + padding - yScale(d.count))
    .attr('fill', '#377eb8');
}

function setupBrush(svg, frames, cellSize, margin, onBrush) {
  // D3 brush for each scatter cell
  const brush = d3.brush()
    .extent([[0, 0], [cellSize, cellSize]])
    .on('brush end', function(event) {
      if (!event.selection) {
        onBrush(null);  // Clear selection
        return;
      }
      
      const [[x0, y0], [x1, y1]] = event.selection;
      // Convert pixel coordinates to data coordinates
      // ... (using scales from the brushed cell)
      // Compute selected indices
      // Call onBrush with index set
    });
  
  // Attach brush to scatter cells
  svg.selectAll('.cell')
    .filter(d => d.geom === 'scatter')
    .call(brush);
}

function onBrush(selectedIndices) {
  // Update all cells to highlight selected indices
  d3.selectAll('circle')
    .attr('fill', function() {
      const idx = +d3.select(this).attr('data-index');
      return selectedIndices && selectedIndices.has(idx) 
        ? '#e41a1c' : '#377eb8';
    })
    .attr('opacity', function() {
      const idx = +d3.select(this).attr('data-index');
      return selectedIndices && selectedIndices.has(idx) 
        ? 1.0 : 0.6;
    });
  
  // Also update other linked views (correlation matrix, table, etc.)
  // ... 
}
")

;; The D3 code above is illustrative. In practice, you'd put it in a separate .js file,
;; load D3 from CDN, use Kindly's :kind/hiccup to embed the visualization, and pass
;; data via a ClojureScript/JavaScript bridge or inline JSON.

;;
;; That's the core idea: varsets map indices to tuples, cross concatenates tuples,
;; blend creates alternatives that expand under cross, nest partitions indices,
;; geoms interpret frames. The SPLOM falls out of (blend * blend). No special-case code.
;;
;; ## References and Further Reading
;;
;; ### Primary Sources
;; - Wilkinson, L. (2005). *[The Grammar of Graphics](https://www.springer.com/gp/book/9780387245447)* (2nd ed.). Springer.
;; - Wilkinson, L. (2010). *[The Grammar of Graphics](https://onlinelibrary.wiley.com/doi/10.1002/wics.118)*. WIREs Computational Statistics, 2(6), 673-677.
;; - [PolicyViz Podcast Episode 139](https://policyviz.com/podcast/episode-139-leland-wilkinson/): Leland Wilkinson on the SPLOM discovery
;;
;; ### Implementations
;; - [AlgebraOfGraphics.jl](https://aog.makie.org/stable/): Julia implementation using Makie
;; - [ggplot2](https://ggplot2.tidyverse.org/): R implementation (layered grammar)
;; - [Vega-Lite](https://vega.github.io/vega-lite/): JSON declarative grammar
;; - [Observable Plot](https://observablehq.com/plot/): JavaScript layered grammar
;;
;; ### Visualization Concepts
;; - [Scatterplot Matrix (SPLOM)](https://en.wikipedia.org/wiki/Scatter_plot#Scatterplot_matrices)
;; - [Brushing and Linking](https://en.wikipedia.org/wiki/Brushing_and_linking)
;; - [Small Multiples](https://en.wikipedia.org/wiki/Small_multiple) (faceting)
;; - [Correlation Matrix](https://en.wikipedia.org/wiki/Correlation#Correlation_matrices)
;;
;; ### Interactive Examples
;; - [D3 Brushable Scatterplot Matrix](https://observablehq.com/@d3/brushable-scatterplot-matrix)
;; - [Vega-Lite SPLOM](https://vega.github.io/vega-lite/examples/interactive_splom.html)
;;
;; ### Clojure Data Science
;; - [Tablecloth](https://scicloj.github.io/tablecloth/): DataFrame library
;; - [dtype-next](https://github.com/cnuernber/dtype-next): High-performance arrays
;; - [Scicloj](https://scicloj.github.io/): Clojure data science community
;; - [Clay](https://scicloj.github.io/clay/): Notebook system for literate programming
