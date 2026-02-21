^{:kindly/hide-code true
  :clay {:title "Variation A: Algebra → geom.viz IR"
         :quarto {:type :post
                  :author [:daslu :claude]
                  :date "2026-02-19"
                  :description "What if our grammar compiled to geom.viz specs as its intermediate representation?"
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :geom-viz :design]
                  :toc true
                  :toc-depth 3
                  :draft true}}}
(ns data-visualization.aog.sketch-geom-ir
  "Variation A: Our algebra compiles to geom.viz specs.

  The Idea:
  We keep the same high-level algebra — cross, stack, views, layer —
  but instead of hand-rolling SVG, we compile view-maps into
  thi.ng/geom.viz spec maps:

    {:x-axis (viz/linear-axis ...)
     :y-axis (viz/linear-axis ...)
     :grid   {:attribs ...}
     :data   [{:values [[x y] ...] :layout viz/svg-scatter-plot :attribs {...}}]}

  geom.viz then handles axes, scales, grid lines, and SVG generation.

  The question this notebook explores:
  Can we keep the algebra elegant while targeting an existing IR?"
  (:require
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.viz.core :as viz]
   [thi.ng.geom.svg.core :as svg]
   [thi.ng.math.core :as m]
   [fastmath.ml.regression :as regr]
   [clojure.string :as str]
   [scicloj.metamorph.ml.rdatasets :as rdatasets]))

;; # Variation A: Algebra → geom.viz IR
;;
;; In `sketch.clj` we rendered SVG by hand.
;; Here, we ask: what if the algebra compiled to **geom.viz specs** instead?
;;
;; geom.viz already handles:
;; - linear/log scales
;; - axis tick marks and labels
;; - grid lines
;; - scatter, line, bar, area, contour, heatmap plots
;;
;; Our job is to generate the spec. geom.viz renders it.

;; ---

;; ## The Data

(def iris (rdatasets/datasets-iris))
(def iris-cols [:sepal-length :sepal-width :petal-length :petal-width])

;; ## Part 1: The Algebra (same as sketch.clj)

(defn cross [xs ys] (vec (for [x xs, y ys] [x y])))
(defn stack [& colls] (vec (apply concat colls)))

(defn views
  "Bind a dataset to column pairs → vector of view-maps."
  [data pairs]
  (mapv (fn [[x y]] {:data data :x x :y y}) pairs))

(defn layer
  "Add overrides to each view in a vector."
  [base-views & {:as overrides}]
  (mapv #(merge % overrides) base-views))

(defn layers
  "Stack multiple layers that inherit from the same base views."
  [base-views & layer-specs]
  (apply stack (map #(layer base-views %) layer-specs)))

(defn facet
  "Split each view by a categorical column."
  [views col]
  (vec (mapcat (fn [v]
                 (let [groups (-> (:data v) (tc/group-by [col]) tc/groups->map)]
                   (map (fn [[group-key group-data]]
                          (assoc v :data group-data :facet-value (get group-key col)))
                        groups)))
               views)))

;; ## Part 2: Compiling Views → geom.viz Specs
;;
;; This is the heart of Variation A.
;; A view-map like `{:data ds :x :sepal-length :y :sepal-width :mark :point :color :species}`
;; compiles to a geom.viz spec map.
;;
;; The key insight: one geom.viz spec per **panel** (unique [x, y] pair).
;; Multiple views on the same panel contribute `:data` entries.

(def ggplot-palette
  ["#F8766D" "#00BA38" "#619CFF" "#A855F7" "#F97316" "#14B8A6" "#EF4444" "#6B7280"])

(defn domain
  "Compute [min max] for a column across datasets, with 5% padding."
  [datasets col]
  (let [all-vals (mapcat #(vec (% col)) datasets)
        lo (reduce min all-vals)
        hi (reduce max all-vals)
        pad (* 0.05 (- hi lo))]
    [(- lo pad) (+ hi pad)]))

(defn compile-axis
  "Create a geom.viz linear-axis for a column."
  [datasets col orient size margin]
  (let [[lo hi] (domain datasets col)]
    (case orient
      :x (viz/linear-axis {:domain [lo hi]
                           :range [margin (- size margin)]
                           :major (max 1.0 (/ (- hi lo) 5))
                           :pos (- size margin)
                           :label-dist 12
                           :label (fn [_ v] (format "%.1f" (double v)))
                           :major-size 3
                           :minor-size 0
                           :attribs {:stroke "none"}})
      :y (viz/linear-axis {:domain [lo hi]
                           :range [(- size margin) margin]
                           :major (max 1.0 (/ (- hi lo) 5))
                           :pos margin
                           :label-dist 12
                           :label (fn [_ v] (format "%.1f" (double v)))
                           :label-style {:text-anchor "end"}
                           :major-size 3
                           :minor-size 0
                           :attribs {:stroke "none"}}))))

(defn color-groups
  "Split a dataset by color column and assign palette colors."
  [ds color-col]
  (let [groups (-> ds (tc/group-by [color-col]) tc/groups->map)]
    (map-indexed (fn [i [group-key group-ds]]
                   {:label (get group-key color-col)
                    :color (nth ggplot-palette (mod i (count ggplot-palette)))
                    :data group-ds})
                 groups)))

(defn view->data-specs
  "Compile a single view-map into geom.viz :data entries."
  [view]
  (let [{:keys [data x y mark stat color]} view
        mk (or mark :point)
        groups (if color (color-groups data color) [{:color "#333" :data data}])]
    (mapcat
     (fn [{:keys [color data label]}]
       (let [xs (vec (data x))
             ys (vec (data y))]
         (case mk
           :point
           [{:values (mapv vector xs ys)
             :attribs {:fill color :stroke "none" :opacity "0.6"}
             :shape (fn [[p]] (svg/circle p 2.5))
             :layout viz/svg-scatter-plot}]

           :line
           (case (or stat :identity)
             :regress
             (let [model (regr/lm ys xs)
                   x-min (reduce min xs) x-max (reduce max xs)
                   y-min (regr/predict model [x-min])
                   y-max (regr/predict model [x-max])]
               [{:values [[x-min y-min] [x-max y-max]]
                 :attribs {:stroke color :stroke-width 1.5 :fill "none"}
                 :layout viz/svg-line-plot}])
             :identity
             [{:values (mapv vector (sort xs) (map (zipmap xs ys) (sort xs)))
               :attribs {:stroke color :stroke-width 1.5 :fill "none"}
               :layout viz/svg-line-plot}])

           :bar
           ;; Histogram: bin the data, produce bar specs
           (let [bins (fastmath.stats/histogram ys :sturges)
                 bin-maps (:bins-maps bins)]
             [{:values (mapv (fn [{:keys [min max count]}]
                               [(/ (+ min max) 2.0) count])
                             bin-maps)
               :attribs {:fill color :stroke "none" :opacity "0.7"}
               :bar-width 8
               :layout viz/svg-bar-plot}]))))
     groups)))

(defn diagonal? [v] (= (:x v) (:y v)))

;; ### The Compiler
;;
;; Groups views by panel [x y], compiles each panel into a geom.viz spec.

(defn compile-panel
  "Compile all views for one panel into a geom.viz spec."
  [panel-views size margin]
  (let [v (first panel-views)
        datasets (map :data panel-views)
        x-col (:x v) y-col (:y v)
        diag (diagonal? v)
        ;; For histograms on diagonal, y domain is count-based
        data-specs (mapcat view->data-specs panel-views)
        ;; For diagonal panels, override axes for histogram
        x-ax (compile-axis datasets x-col :x size margin)
        y-ax (if diag
                ;; For histogram, y is count — derive from bar data
               (viz/linear-axis {:domain [0 50]
                                 :range [(- size margin) margin]
                                 :major 10 :pos margin
                                 :label-dist 12
                                 :label (fn [_ v] (format "%.0f" (double v)))
                                 :label-style {:text-anchor "end"}
                                 :major-size 3 :minor-size 0
                                 :attribs {:stroke "none"}})
               (compile-axis datasets y-col :y size margin))]
    {:x-axis x-ax
     :y-axis y-ax
     :grid {:attribs {:stroke "#E0E0E0" :stroke-width 0.5}}
     :data (vec data-specs)}))

;; ## Part 3: Rendering
;;
;; A single panel renders via `viz/svg-plot2d-cartesian`.
;; A grid of panels is positioned with SVG transforms.

(def bg-color "#EBEBEB")

(defn render-panel-svg
  "Render one geom.viz spec into an SVG group with background."
  [spec size]
  [:g
   (svg/rect [0 0] size size {:fill bg-color})
   (viz/svg-plot2d-cartesian spec)])

(defn infer-defaults
  "If x=y (diagonal), treat as histogram."
  [view]
  (if (diagonal? view)
    (merge {:mark :bar :stat :bin} view)
    view))

(defn plot
  "Render a vector of views as SVG.
  Groups by [x y] panel, compiles each to geom.viz, positions in a grid."
  [views & {:keys [panel-size margin] :or {panel-size 250 margin 30}}]
  (let [views (mapv infer-defaults views)
        ;; Group by [x y] panel
        panels (group-by (fn [v] [(:x v) (:y v)]) views)
        panel-keys (vec (keys panels))
        ;; Compute grid dimensions from unique x and y columns
        x-cols (distinct (map (comp :x first) (vals panels)))
        y-cols (distinct (map (comp :y first) (vals panels)))
        x-index (zipmap x-cols (range))
        y-index (zipmap y-cols (range))
        ncols (count x-cols)
        nrows (count y-cols)
        gap 5
        total-w (+ (* ncols panel-size) (* (dec ncols) gap))
        total-h (+ (* nrows panel-size) (* (dec nrows) gap))]
    ^:kind/hiccup
    [:svg {:width total-w :height total-h
           "xmlns" "http://www.w3.org/2000/svg"
           "version" "1.1"}
     (for [[panel-key panel-views] panels
           :let [[x-col y-col] panel-key
                 col (get x-index x-col 0)
                 row (get y-index y-col 0)
                 tx (* col (+ panel-size gap))
                 ty (* row (+ panel-size gap))
                 spec (compile-panel panel-views panel-size margin)]]
       [:g {:transform (str "translate(" tx "," ty ")")}
        (render-panel-svg spec panel-size)])]))

;; ---
;; ## Examples
;;
;; All examples use the same algebra from sketch.clj.
;; The difference: rendering goes through geom.viz.

;; ### Single Scatter Plot

(-> (views iris [[:sepal-length :sepal-width]])
    (layer :mark :point)
    plot)

;; geom.viz draws the axes, grid, and tick labels for us.
;; We didn't write any axis code.

;; ### Colored Scatter

(-> (views iris [[:sepal-length :sepal-width]])
    (layer :mark :point :color :species)
    plot)

;; ### Scatter + Regression

(let [base (views iris [[:sepal-length :sepal-width]])]
  (plot (layers base
                {:mark :point :color :species}
                {:mark :line :stat :regress :color :species})))

;; geom.viz renders both scatter and line-strip in the same coordinate system.
;; The polyline and circles share axes computed once.

;; ### 2×2 Grid

(-> (views iris (cross [:sepal-length :sepal-width]
                       [:petal-length :petal-width]))
    (layer :mark :point :color :species)
    plot)

;; ### 4×4 SPLOM

(-> (views iris (cross iris-cols iris-cols))
    (layer :mark :point :color :species)
    (plot :panel-size 150))

;; ---
;; ## Reflection: geom.viz as IR
;;
;; **What we gain:**
;; - Axes, tick marks, and scale computation are free
;; - geom.viz handles domain→range mapping
;; - Many plot types available: area, heatmap, contour, polar, radar
;; - The IR is inspectable: you can print the spec before rendering
;;
;; **What we lose:**
;; - geom.viz's `:data` model is rigid: `{:values [[x y] ...] :layout fn}`
;; - Histogram support is awkward — geom.viz bar-plot expects pre-binned [x count] pairs
;;   but positions them differently than a statistical histogram
;; - No built-in legend; we'd need to add our own
;; - No built-in facet labels
;; - The axis styling is hard to match to ggplot2 aesthetics
;; - geom.viz outputs string coordinates ("30.00") not numbers
;;
;; **The key tension:**
;; Our algebra produces *views* (maps with datasets and column names).
;; geom.viz expects *data series* (vectors of [x y] point pairs).
;; The compile step bridges this gap, but it's a lossy translation:
;; the dataset, column names, and semantic meaning disappear
;; once we convert to `[[4.3 3.0] [4.4 3.2] ...]`.
;;
;; **Verdict:**
;; geom.viz is excellent for **individual panels** — its axis and scale logic
;; is solid. But it's designed for single plots, not grids.
;; We still need our own grid layout, legend, and facet labels.
;; The algebra→IR compilation is doable but feels like a translation tax.
;;
;; The deeper issue: geom.viz's IR is a *rendering specification*,
;; not a *statistical graphics specification*.
;; It knows about coordinates and shapes, not about data and aesthetics.
;; This is the gap that a Grammar of Graphics fills.
