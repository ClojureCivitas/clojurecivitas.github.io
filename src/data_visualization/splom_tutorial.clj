^{:kindly/hide-code true
  :clay {:title "Building a SPLOM using geom.viz"
         :quarto {:type :post
                  :author [:daslu]
                  :date "2026-01-11"
                  :description "Progressive tutorial building scatter plot matrices step-by-step with thi.ng/geom.viz"
                  :category :data-visualization
                  :tags [:datavis :geomviz :splom :tutorial]
                  :keywords [:datavis :splom :scatterplot-matrix :geomviz :tutorial]
                  :toc true
                  :toc-depth 2
                  :draft true}}}
(ns data-visualization.splom-tutorial
  (:require [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]))

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

;; ## Introduction
;;
;; A [SPLOM](https://en.wikipedia.org/wiki/Scatter_plot#Scatter_plot_matrices) 
;; (scatter plot matrix) displays pairwise relationships between multiple 
;; variables in a grid. It's invaluable for exploratory data analysis.
;;
;; We'll build one from scratch using [thi.ng/geom.viz](https://github.com/thi-ng/geom),
;; progressing from a simple scatter plot to a complete 4×4 matrix.
;; Each step introduces exactly one new concept.
;;
;; **Context:** This tutorial is part of ongoing work on the
;; [Tableplot](https://scicloj.github.io/tableplot/) plotting library
;; and the [Real-World-Data](https://scicloj.github.io/docs/community/groups/real-world-data/)
;; dev group's exploration of visualization APIs for Clojure.
;; By building a SPLOM manually, we better understand what a high-level plotting
;; library needs to provide.
;;
;; This is a working Clojure notebook compatible with
;; [Kindly](https://scicloj.github.io/kindly/)-compatible tools like
;; [Clay](https://scicloj.github.io/clay/).
;;
;; Clojurians Zulip discussion (requires login):
;; [#**data-science>AlgebraOfGraphics.jl**](https://clojurians.zulipchat.com/#narrow/channel/151924-data-science/topic/AlgebraOfGraphics.2Ejl/)

;; ## Setup
;;
;; We'll use [thi.ng/geom.viz](https://github.com/thi-ng/geom/blob/feature/no-org/org/examples/viz/demos.org)
;; for low-level SVG rendering. thi.ng/geom is part of a comprehensive ecosystem of
;; computational design tools created by [Karsten Schmidt](https://github.com/postspectacular)
;; (aka "toxi"), with the thi.ng collection established in 2006 and thi.ng/geom starting
;; around 2011. The library provides ~320 sub-projects for geometry, visualization, and
;; generative art, actively maintained for nearly two decades.
;;
;; This notebook uses several libraries from the Clojure data science ecosystem:

(ns data-visualization.splom-tutorial
  (:require
   ;; Tablecloth - Dataset manipulation
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]

   ;; Kindly - Notebook visualization protocol
   [scicloj.kindly.v4.kind :as kind]

   ;; thi.ng/geom - SVG rendering and visualization
   [thi.ng.geom.viz.core :as viz]
   [thi.ng.geom.svg.core :as svg]

   ;; Fastmath - Statistical computations
   [fastmath.stats :as stats]
   [fastmath.ml.regression :as regr]

   ;; RDatasets - Example datasets
   [scicloj.metamorph.ml.rdatasets :as rdatasets]))

;; [**Tablecloth**](https://scicloj.github.io/tablecloth/) provides our dataset API, wrapping
;; [tech.ml.dataset](https://github.com/techascent/tech.ml.dataset) with a friendly interface.
;; We use it to load data, group by species, and select rows.
;;
;; [**Kindly**](https://scicloj.github.io/kindly-noted/) is the visualization protocol that lets
;; this notebook render in different environments ([Clay](https://scicloj.github.io/clay/),
;; [Portal](https://github.com/djblue/portal), etc.).
;;
;; [**thi.ng/geom**](https://github.com/thi-ng/geom) provides low-level SVG rendering primitives.
;; We use [geom.viz](https://github.com/thi-ng/geom/blob/feature/no-org/org/examples/viz/demos.org)
;; for creating axes, scales, and plot layouts, and
;; [geom.svg](https://github.com/thi-ng/geom) for SVG element construction.
;;
;; [**Fastmath**](https://github.com/generateme/fastmath) handles statistical computations,
;; including histogram binning (Steps 4-7) and linear regression (Steps 8-10).
;; It's a comprehensive math library for Clojure.
;;
;; [**RDatasets**](https://vincentarelbundock.github.io/Rdatasets/articles/data.html) provides
;; classic datasets for examples. It is made available in Clojure through
;; [metamorph.ml](https://github.com/scicloj/metamorph.ml).

;; ## The Data
;;
;; We'll use the classic [Iris dataset](https://en.wikipedia.org/wiki/Iris_flower_data_set):
;; 150 flowers, 4 measurements each, 3 species.

(def iris (rdatasets/datasets-iris))

iris

;; Three species, 50 flowers each:
(-> iris
    (tc/group-by [:species])
    (tc/aggregate {:count tc/row-count}))

;; ## Colors and Data Preparation
;;
;; Define our color palette and derive species information from the data.
;;
;; The species colors are inspired by [ggplot2](https://ggplot2.tidyverse.org/)'s
;; default discrete color scale.

(def colors
  {:grey-bg "#EBEBEB"
   :grid "#FFFFFF"
   :grey-points "#333333"
   :regression "#2C3E50" ; Dark blue-gray for regression lines
   :species ["#F8766D" ; Red (Setosa)
             "#619CFF" ; Blue (Versicolor)
             "#00BA38"]}) ; Green (Virginica)

;; Derive species names from data (used throughout)
(def species-names (sort (distinct (iris :species))))

;; Create a species -> color mapping
(def species-color-map
  (zipmap species-names (:species colors)))

species-color-map

;; Group data by species for later use
(def species-groups
  (tc/group-by iris :species {:result-type :as-map}))

;; Numerical columns
(def numerical-column-names
  (->> iris
       keys
       (filter (fn [k]
                 (-> k
                     iris
                     (tcc/typeof? :numerical))))))

numerical-column-names

;; Compute domains from data
;; We'll use these throughout to avoid hard-coding ranges.
(defn compute-domain
  "Compute [min max] domain for a variable, with optional padding."
  ([var-data] (compute-domain var-data 0.05))
  ([var-data padding]
   (let [min-val (tcc/reduce-min var-data)
         max-val (tcc/reduce-max var-data)
         span (- max-val min-val)
         pad (* span padding)]
     [(- min-val pad) (+ max-val pad)])))

(def domains
  (->> numerical-column-names
       (map (fn [colname]
              [colname (compute-domain (iris colname))]))
       (into {})))


;; Helper for integer axis labels
(defn int-label [x] (str (int x)))
(def int-label-fn (viz/default-svg-label int-label))
domains

;; ## Step 0: SVG as Hiccup
;;
;; Before we start plotting, let's understand how we'll render SVG.
;;
;; thi.ng/geom provides SVG functions that we can render as hiccup.
;; Let's start with a simple example:

(kind/hiccup
 (svg/svg {:width 150 :height 100}
          (svg/circle [30 50] 20 {:fill "#F8766D" :stroke "none"})
          (svg/circle [70 50] 20 {:fill "#619CFF" :stroke "none"})
          (svg/circle [110 50] 20 {:fill "#00BA38" :stroke "none"})))

;; This works because each circle is a valid hiccup element (vector starting with a keyword).
;;
;; However, when we generate SVG elements programmatically, we often create
;; collections of elements. Hiccup expects the first element of a vector to be
;; a tag keyword, so this won't work:
;;
;; ```clj
;; [[:circle ...] [:circle ...] [:circle ...]]  ; Not valid hiccup!
;; ```
;;
;; We need a helper to convert such vectors to sequences.

(require '[clojure.walk :as walk])

(defn hiccup-compat
  "Convert non-hiccup vectors to sequences.
  
  [:tag ...] stays as vector (valid hiccup element)
  [[:tag1 ...] [:tag2 ...]] becomes seq (element children)"
  [form]
  (walk/postwalk
   (fn [x]
     (if (and (vector? x)
              (not (map-entry? x))
              (seq x)
              (not (keyword? (first x))))
       (seq x)
       x))
   form))

(defn svg
  "Like thi.ng.geom.svg/svg, but hiccup-compatible.
  
  Converts nested vectors to seqs and wraps with kind/hiccup."
  [attrs & children]
  (-> (apply svg/svg attrs children)
      hiccup-compat
      kind/hiccup))

;; Now we can generate circles programmatically:

(def three-circles
  (mapv (fn [[x color]]
          (svg/circle [x 50] 20 {:fill color :stroke "none"}))
        [[30 "#F8766D"]
         [70 "#619CFF"]
         [110 "#00BA38"]]))

;; Our helper automatically converts the vector to a seq:
(svg {:width 150 :height 100} three-circles)
;; Common plotting constants
(def panel-size 400)
(def margin 60)

;; Grid constants (we'll use these later for multi-panel layouts)
(def grid-panel-size 200)
(def grid-margin 40)

;; ## Step 1: Single Scatter Plot (Ungrouped)
;;
;; Let's start with the simplest possible visualization: a scatter plot
;; of sepal length vs. sepal width, all points grey.

;; Let's create helpers for x and y axes.
(defn axis [{:keys [column rng pos label-style]
             :or {label-style {}}}]
  (viz/linear-axis
   {:domain (domains column)
    :range rng
    :major 2.0
    :pos pos
    :label-dist 12
    :label int-label-fn
    :label-style label-style
    :major-size 3
    :minor-size 0
    :attribs {:stroke "none"}}))

(defn x-axis [column]
  (axis {:column column :rng [margin (- panel-size margin)] :pos (- panel-size margin)}))

(defn y-axis [column]
  (axis {:column column :rng [(- panel-size margin) margin] :pos margin :label-style
         {:text-anchor "end"}}))


;; Let's see what these axis helpers produce:
(x-axis :sepal-length)
(y-axis :sepal-width)
;; Now let's assemble the complete plot specification.
(defn plot-spec [columns]
  (let [[x-col y-col] columns]
    {:x-axis (x-axis x-col)
     :y-axis (y-axis y-col)
     :grid {:attribs {:stroke (:grid colors) :stroke-width
                      1}}
     :data [{:values (-> iris
                         (tc/select-columns columns)
                         tc/rows)
             :attribs {:fill (:grey-points colors) :stroke
                       "none"}
             :layout viz/svg-scatter-plot}]}))

(plot-spec [:sepal-length
            :sepal-width])

;; Render with grey background
(svg
 {:width panel-size :height panel-size}
 (svg/rect [0 0] panel-size panel-size {:fill (:grey-bg colors)})
 (viz/svg-plot2d-cartesian (plot-spec [:sepal-length
                                       :sepal-width])))

;; We can see the relationship between sepal length and width!

;; ## Step 2: Color by Species
;;
;; Now let's color the points by species to see the three clusters.

;; Same plot, different data
(defn colored-plot-spec [columns]
  (let [[x-col y-col] columns]
    {:x-axis (x-axis x-col)
     :y-axis (y-axis y-col)
     :grid {:attribs {:stroke (:grid colors) :stroke-width 1.5}}
     :data (map (fn [species color]
                  (let [data (species-groups species)
                        points (-> data
                                   (tc/select-columns columns)
                                   tc/rows)]
                    {:values points
                     :attribs {:fill color :stroke "none"}
                     :layout viz/svg-scatter-plot}))
                species-names
                (:species colors))}))

(colored-plot-spec [:sepal-length
                    :sepal-width])

(svg
 {:width panel-size :height panel-size}
 (svg/rect [0 0] panel-size panel-size {:fill (:grey-bg colors)})
 (viz/svg-plot2d-cartesian (colored-plot-spec [:sepal-length
                                               :sepal-width])))

;; Now we can see three distinct clusters! Setosa (red) is clearly separated.

;; ## Step 3: Single Histogram
;;
;; Before building a grid, let's learn how to render a histogram.
;; We'll show the distribution of sepal width.

(-> (iris :sepal-width)
    (stats/histogram :sturges)
    :bins-maps)

;; The :sturges method automatically chooses a good number of bins based on the data size.

;; We need to manually render bars as SVG rectangles.
;; We'll use viz/linear-scale to map data values → pixel coordinates.

;; Let's compute histogram data and scales in one go.
(defn compute-histogram-data [column]
  (let [hist (stats/histogram (iris column) :sturges)
        bins (:bins-maps hist)
        max-count (tcc/reduce-max (map :count bins))
        x-scale (viz/linear-scale (domains column) [margin (- panel-size margin)])
        y-scale (viz/linear-scale [0 max-count] [(- panel-size margin) margin])]
    {:bins bins
     :max-count max-count
     :x-scale x-scale
     :y-scale y-scale}))

(compute-histogram-data :sepal-width)

;; Now we can render bars using the pre-computed data.
(defn histogram-bars [{:keys [bins x-scale y-scale]}]
  (map (fn [{:keys [min max count]}]
         (let [x1 (x-scale min)
               x2 (x-scale max)
               y (y-scale count)
               bar-width (- x2 x1)
               bar-height (- (- panel-size margin) y)]
           (svg/rect
            [x1 y]
            bar-width
            bar-height
            {:fill (:grey-points colors)
             :stroke "none"})))
       bins))

;; And create axes and grid using the same data.
(defn histogram-axes [column {:keys [max-count]}]
  (let [x-axis (viz/linear-axis
                {:domain (domains column)
                 :range [margin (- panel-size margin)]
                 :major 2.0
                 :pos (- panel-size margin)
                 :label-dist 12
                 :label int-label-fn
                 :major-size 3
                 :minor-size 0
                 :attribs {:stroke "none"}})
        y-axis (viz/linear-axis
                {:domain [0 max-count]
                 :range [(- panel-size margin) margin]
                 :major 5
                 :pos margin
                 :label-dist 12
                 :label-style {:text-anchor "end"}
                 :major-size 3
                 :minor-size 0
                 :attribs {:stroke "none"}})]
    [(viz/svg-x-axis-cartesian x-axis)
     (viz/svg-y-axis-cartesian y-axis)
     (viz/svg-axis-grid2d-cartesian x-axis y-axis
                                    {:attribs {:stroke (:grid colors)
                                               :stroke-width 1.5}})]))

;; Usage: compute once, pass to both functions
(let [column :sepal-width
      hist-data (compute-histogram-data column)]
  (svg {:width panel-size :height panel-size}
       (svg/rect [0 0]
                 panel-size panel-size
                 {:fill (:grey-bg colors)})
       (histogram-axes column hist-data)
       (histogram-bars hist-data)))

;; A simple histogram! Most flowers have sepal width around 3.0.

;; ## Step 4: Colored Histogram (Overlaid by Species)
;;
;; Now let's overlay three histograms (one per species) to see their
;; different distributions.

;; Let's compute histogram data for all three species.
(defn compute-colored-histogram-data [column]
  (let [;; Histogram for each species
        species-hists (mapv (fn [species]
                              {:species species
                               :hist (stats/histogram ((species-groups species) column) :sturges)})
                            species-names)
        ;; Find max count across all species for shared y-scale
        max-count (tcc/reduce-max
                   (mapcat (fn [{:keys [hist]}]
                             (map :count (:bins-maps hist)))
                           species-hists))
        x-scale (viz/linear-scale (domains column) [margin (- panel-size margin)])
        y-scale (viz/linear-scale [0 max-count] [(- panel-size margin) margin])]
    {:species-hists species-hists
     :max-count max-count
     :x-scale x-scale
     :y-scale y-scale}))

(compute-colored-histogram-data :sepal-width)

;; Now we can render the overlaid colored bars.
(defn colored-histogram-bars [{:keys [species-hists x-scale y-scale]}]
  (mapcat
   (fn [idx {:keys [hist]}]
     (let [color ((:species colors) idx)]
       (map (fn [{:keys [min max count]}]
              (let [x1 (x-scale min)
                    x2 (x-scale max)
                    y (y-scale count)
                    bar-width (- x2 x1)
                    bar-height (- (- panel-size margin) y)]
                (svg/rect
                 [x1 y]
                 bar-width
                 bar-height
                 {:fill color
                  :stroke "none"
                  :opacity 0.7})))
            (:bins-maps hist))))
   (range)
   species-hists))

;; And create the axes.
(defn colored-histogram-axes [column {:keys [max-count]}]
  (let [x-axis (viz/linear-axis
                {:domain (domains column)
                 :range [margin (- panel-size margin)]
                 :major 2.0
                 :pos (- panel-size margin)
                 :label-dist 12
                 :label int-label-fn
                 :major-size 3
                 :minor-size 0
                 :attribs {:stroke "none"}})
        y-axis (viz/linear-axis
                {:domain [0 max-count]
                 :range [(- panel-size margin) margin]
                 :major 5
                 :pos margin
                 :label-dist 12
                 :label-style {:text-anchor "end"}
                 :major-size 3
                 :minor-size 0
                 :attribs {:stroke "none"}})]
    [(viz/svg-x-axis-cartesian x-axis)
     (viz/svg-y-axis-cartesian y-axis)
     (viz/svg-axis-grid2d-cartesian x-axis y-axis
                                    {:attribs {:stroke (:grid colors)
                                               :stroke-width 1.5}})]))

;; Usage: compute once, pass to both functions
(let [column :sepal-width
      hist-data (compute-colored-histogram-data column)]
  (svg
   {:width panel-size :height panel-size}
   (svg/rect [0 0] panel-size panel-size {:fill (:grey-bg colors)})
   (colored-histogram-axes column hist-data)
   (colored-histogram-bars hist-data)))

;; Beautiful! We can see Setosa (red) has wider sepals on average.

;; ## Step 5: 2×2 Grid of Scatter Plots
;;
;; Now let's create a grid showing relationships between two variables:
;; sepal.width and petal.length.


;; Let's create a helper to render a scatter panel at any grid position.
(defn make-grid-scatter-panel [x-col y-col row col]
  (let [x-offset (* col grid-panel-size)
        y-offset (* row grid-panel-size)
        x-axis (viz/linear-axis
                {:domain (domains x-col)
                 :range [(+ x-offset grid-margin)
                         (+ x-offset grid-panel-size (- grid-margin))]
                 :major 2.0
                 :label int-label-fn
                 :pos (+ y-offset grid-panel-size (- grid-margin))
                 :label-dist 12
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        y-axis (viz/linear-axis
                {:domain (domains y-col)
                 :range [(+ y-offset grid-panel-size (- grid-margin))
                         (+ y-offset grid-margin)]
                 :major 2.0
                 :pos (+ x-offset grid-margin)
                 :label int-label-fn
                 :label-dist 12
                 :label-style {:text-anchor "end"}
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        series (mapv (fn [species color]
                       (let [data (species-groups species)
                             points (mapv vector (data x-col) (data y-col))]
                         {:values points
                          :attribs {:fill color :stroke "none"}
                          :layout viz/svg-scatter-plot}))
                     species-names
                     (:species colors))]
    (viz/svg-plot2d-cartesian
     {:x-axis x-axis
      :y-axis y-axis
      :grid {:attribs {:stroke (:grid colors) :stroke-width 1.5}}
      :data series})))

;; Assemble the 2×2 grid
(let [grid-total-size (* 2 grid-panel-size)]
  (svg
   {:width grid-total-size :height grid-total-size}

   ;; Backgrounds first (z-order)
   (svg/rect [0 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
   (svg/rect [grid-panel-size 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
   (svg/rect [0 grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
   (svg/rect [grid-panel-size grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})

   ;; The four scatter plots
   (make-grid-scatter-panel :sepal-width :sepal-width 0 0) ; top-left
   (make-grid-scatter-panel :petal-length :sepal-width 0 1) ; top-right
   (make-grid-scatter-panel :sepal-width :petal-length 1 0) ; bottom-left
   (make-grid-scatter-panel :petal-length :petal-length 1 1))) ; bottom-right

;; We can see relationships! Notice the diagonal panels show x=y
;; which isn't very informative. Let's fix that next.

;; ## Step 6: 2×2 Grid with Diagonal Histograms
;;
;; Now let's improve our grid by using histograms on the diagonal (where x=y).

;; Let's do the same for histograms.
(defn make-grid-histogram-panel [column row col]
  (let [x-offset (* col grid-panel-size)
        y-offset (* row grid-panel-size)
        ;; Compute histogram data for all species
        species-hists (mapv (fn [species]
                              {:species species
                               :hist (stats/histogram ((species-groups species) column) 12)})
                            species-names)
        max-count (tcc/reduce-max
                   (mapcat (fn [{:keys [hist]}]
                             (map :count (:bins-maps hist)))
                           species-hists))
        x-scale (viz/linear-scale (domains column)
                                  [(+ x-offset grid-margin)
                                   (+ x-offset grid-panel-size (- grid-margin))])
        y-scale (viz/linear-scale [0 max-count]
                                  [(+ y-offset grid-panel-size (- grid-margin))
                                   (+ y-offset grid-margin)])
        ;; Create axes
        x-axis (viz/linear-axis
                {:domain (domains column)
                 :range [(+ x-offset grid-margin) (+ x-offset grid-panel-size (- grid-margin))]
                 :major 2.0
                 :pos (+ y-offset grid-panel-size (- grid-margin))
                 :label-dist 12
                 :label int-label-fn
                 :major-size 3
                 :minor-size 0
                 :attribs {:stroke "none"}})
        y-axis (viz/linear-axis
                {:domain [0 max-count]
                 :range [(+ y-offset grid-panel-size (- grid-margin)) (+ y-offset grid-margin)]
                 :major (if (> max-count 20) 5 2)
                 :pos (+ x-offset grid-margin)
                 :label-dist 12
                 :label int-label-fn
                 :label-style {:text-anchor "end"}
                 :major-size 3
                 :minor-size 0
                 :attribs {:stroke "none"}})
        ;; Create bars
        bars (mapcat (fn [idx {:keys [hist]}]
                       (let [color ((:species colors) idx)]
                         (map (fn [{:keys [min max count]}]
                                (let [x1 (x-scale min)
                                      x2 (x-scale max)
                                      y (y-scale count)
                                      bar-width (- x2 x1)
                                      bar-height (- (+ y-offset grid-panel-size (- grid-margin)) y)]
                                  (svg/rect [x1 y] bar-width bar-height
                                            {:fill color
                                             :stroke "none"
                                             :opacity 0.7})))
                              (:bins-maps hist))))
                     (range)
                     species-hists)]
    (svg/group {}
               (viz/svg-x-axis-cartesian x-axis)
               (viz/svg-y-axis-cartesian y-axis)
               (viz/svg-axis-grid2d-cartesian x-axis y-axis
                                              {:attribs {:stroke (:grid colors)
                                                         :stroke-width 1.5}})
               bars)))

;; Now render the grid with colored histograms on the diagonal
(let [grid-total-size (* 2 grid-panel-size)]
  (svg
   {:width grid-total-size :height grid-total-size}

   ;; Background panels
   (svg/rect [0 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
   (svg/rect [grid-panel-size 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
   (svg/rect [0 grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
   (svg/rect [grid-panel-size grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})

   ;; Top-left: histogram for sepal.width
   (make-grid-histogram-panel :sepal-width 0 0)

   ;; Top-right: sepal.width vs petal.length
   (make-grid-scatter-panel :petal-length :sepal-width 0 1)

   ;; Bottom-left: petal.length vs sepal.width  
   (make-grid-scatter-panel :sepal-width :petal-length 1 0)

   ;; Bottom-right: histogram for petal.length
   (make-grid-histogram-panel :petal-length 1 1)))

;; Perfect! Now we can see both relationships and distributions by species.

;; ## Step 7: Single Scatter with Regression Line
;;
;; Add a linear regression overlay to understand the trend.

;; First, let's compute a linear regression.
(defn compute-regression [x-col y-col]
  (let [xs (iris x-col)
        ys (iris y-col)
        xss (mapv vector xs)
        model (regr/lm ys xss)
        slope (first (:beta model))
        intercept (:intercept model)]
    {:slope slope :intercept intercept}))


;; See what the regression coefficients look like:
(compute-regression :sepal-length :sepal-width)
;; Now let's turn those coefficients into an SVG line.
(defn regression-line [x-col y-col regression-data]
  (let [{:keys [slope intercept]} regression-data
        [x-min x-max] (domains x-col)
        x-scale (viz/linear-scale (domains x-col) [margin (- panel-size margin)])
        y-scale (viz/linear-scale (domains y-col) [(- panel-size margin) margin])
        y1 (+ intercept (* slope x-min))
        y2 (+ intercept (* slope x-max))]
    (svg/line [(x-scale x-min) (y-scale y1)]
              [(x-scale x-max) (y-scale y2)]
              {:stroke (:regression colors) :stroke-width 2})))

;; Render scatter plot with regression overlay
(let [x-col :sepal-length
      y-col :sepal-width
      regression-data (compute-regression x-col y-col)
      plot (colored-plot-spec [x-col y-col])]
  (svg
   {:width panel-size :height panel-size}
   (svg/rect [0 0] panel-size panel-size {:fill (:grey-bg colors)})
   (viz/svg-plot2d-cartesian plot)
   (regression-line x-col y-col regression-data)))

;; The red line shows the linear trend: sepal width slightly decreases
;; as sepal length increases.

;; ## Step 8: Single Scatter with Regression Lines by Species
;;
;; Compute separate regression lines for each species group.

;; Let's compute separate regressions for each species.
(defn compute-species-regressions [x-col y-col]
  (into {}
        (for [species species-names]
          (let [species-data (tc/select-rows iris #(= (% :species) species))
                xs (species-data x-col)
                ys (species-data y-col)
                xss (mapv vector xs)
                model (regr/lm ys xss)
                slope (first (:beta model))
                intercept (:intercept model)]
            [species {:slope slope :intercept intercept}]))))


;; Per-species regression coefficients:
(compute-species-regressions :sepal-length :sepal-width)
;; Now let's create the line SVGs for all species at once.
(defn species-regression-lines [x-col y-col species-regressions-data]
  (let [[x-min x-max] (domains x-col)
        x-scale (viz/linear-scale (domains x-col) [margin (- panel-size margin)])
        y-scale (viz/linear-scale (domains y-col) [(- panel-size margin) margin])]
    (mapv (fn [species]
            (let [{:keys [slope intercept]} (species-regressions-data species)
                  y1 (+ intercept (* slope x-min))
                  y2 (+ intercept (* slope x-max))]
              (svg/line [(x-scale x-min) (y-scale y1)]
                        [(x-scale x-max) (y-scale y2)]
                        {:stroke (species-color-map species)
                         :stroke-width 2
                         :opacity 0.7})))
          species-names)))

;; Render scatter plot with per-species regression lines
(let [x-col :sepal-length
      y-col :sepal-width
      regressions (compute-species-regressions x-col y-col)
      plot (colored-plot-spec [x-col y-col])]
  (svg
   {:width panel-size :height panel-size}
   (svg/rect [0 0] panel-size panel-size {:fill (:grey-bg colors)})
   (viz/svg-plot2d-cartesian plot)
   (species-regression-lines x-col y-col regressions)))

;; Each species has its own trend! Setosa (red) has a positive slope,
;; while versicolor (green) and virginica (blue) have gentler relationships.

;; ## Step 9: 2×2 Grid with Regression Lines
;;
;; Add per-species regression overlays to the scatter plots in our grid.

;; We'll need per-species regression lines positioned in the grid.
(defn make-grid-regression-lines [x-col y-col row col species-regressions-data]
  (let [x-offset (* col grid-panel-size)
        y-offset (* row grid-panel-size)
        [x-min x-max] (domains x-col)
        x-scale (viz/linear-scale (domains x-col)
                                  [(+ x-offset grid-margin)
                                   (+ x-offset grid-panel-size (- grid-margin))])
        y-scale (viz/linear-scale (domains y-col)
                                  [(+ y-offset grid-panel-size (- grid-margin))
                                   (+ y-offset grid-margin)])]
    (mapv (fn [species]
            (let [{:keys [slope intercept]} (species-regressions-data species)
                  y1 (+ intercept (* slope x-min))
                  y2 (+ intercept (* slope x-max))]
              (svg/line [(x-scale x-min) (y-scale y1)]
                        [(x-scale x-max) (y-scale y2)]
                        {:stroke (species-color-map species)
                         :stroke-width 2
                         :opacity 0.7})))
          species-names)))

;; Render grid with histograms and per-species regression lines
(let [grid-total-size (* 2 grid-panel-size)
      ;; Compute regressions for the two scatter panels
      regressions-01 (compute-species-regressions :petal-length :sepal-width)
      regressions-10 (compute-species-regressions :sepal-width :petal-length)]
  (svg
   {:width grid-total-size :height grid-total-size}

   ;; Background panels
   (svg/rect [0 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
   (svg/rect [grid-panel-size 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
   (svg/rect [0 grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
   (svg/rect [grid-panel-size grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})

   ;; Top-left: histogram for sepal.width
   (make-grid-histogram-panel :sepal-width 0 0)

   ;; Top-right: petal.length (x) vs sepal.width (y) with per-species regressions
   (make-grid-scatter-panel :petal-length :sepal-width 0 1)
   (make-grid-regression-lines :petal-length :sepal-width 0 1 regressions-01)

   ;; Bottom-left: sepal.width (x) vs petal.length (y) with per-species regressions
   (make-grid-scatter-panel :sepal-width :petal-length 1 0)
   (make-grid-regression-lines :sepal-width :petal-length 1 0 regressions-10)

   ;; Bottom-right: histogram for petal.length
   (make-grid-histogram-panel :petal-length 1 1)))

;; Beautiful! Each species shows its own trend in both scatter panels.
;; Notice how the three colored regression lines reveal different relationships
;; for each species across the grid.



;; We've now seen the same pattern several times: render scatter plots with regressions
;; on off-diagonal panels, and histograms on the diagonal. Let's abstract this.

;; ## Step 10: Extract Helper Function
;;
;; Looking at our 2×2 grid rendering, we're repeating a pattern:
;; - Diagonal panels (row = col) → histogram
;; - Off-diagonal panels (row ≠ col) → scatter + regressions
;;
;; Let's abstract this into a single helper function.

;; Here's our abstraction: one function that decides what to render based on position.
(defn render-panel [x-col y-col row col species-regressions-data]
  (if (= row col)
    ;; Diagonal: histogram
    (make-grid-histogram-panel x-col row col)
    ;; Off-diagonal: scatter + regression lines
    (list
     (make-grid-scatter-panel x-col y-col row col)
     (make-grid-regression-lines x-col y-col row col species-regressions-data))))


;; Notice how render-panel chooses what to render:
;; Diagonal (row=col): returns histogram
;; Off-diagonal: returns scatter + regressions
;; Now we can render the same 2×2 grid more concisely
(let [grid-total-size (* 2 grid-panel-size)
      cols [:sepal-width :petal-length]
      ;; Pre-compute regressions for all variable pairs
      regressions-map {[:petal-length :sepal-width] 
                       (compute-species-regressions :petal-length :sepal-width)
                       [:sepal-width :petal-length]
                       (compute-species-regressions :sepal-width :petal-length)}]
  (svg
   {:width grid-total-size :height grid-total-size}
   
   ;; Background panels
   (for [row (range 2)
         col (range 2)]
     (svg/rect [(* col grid-panel-size) (* row grid-panel-size)]
               grid-panel-size grid-panel-size
               {:fill (:grey-bg colors)}))
   
   ;; Render all panels using our helper
   (for [row (range 2)
         col (range 2)]
     (let [x-col (cols col)
           y-col (cols row)
           regressions (get regressions-map [x-col y-col])]
       (render-panel x-col y-col row col regressions)))))

;; Same result as before, but now the pattern is explicit and reusable!


;; ## Step 11: Scale to 4×4 Grid
;;
;; Now that we have the abstraction, scaling to all 4 iris variables is straightforward.
;; This creates a 4×4 grid = 16 panels total (4 histograms + 12 scatter plots).

;; All numerical columns from iris
(def all-cols [:sepal-length :sepal-width :petal-length :petal-width])

;; Pre-compute all pairwise regressions (we only need off-diagonal pairs)
(def all-regressions
  (into {}
        (for [row-idx (range 4)
              col-idx (range 4)
              :when (not= row-idx col-idx)]
          (let [x-col (all-cols col-idx)
                y-col (all-cols row-idx)]
            [[x-col y-col] (compute-species-regressions x-col y-col)]))))



;; That's 12 regression pairs (4 choose 2 × 2 directions), one for each off-diagonal panel.
;; Sample of pre-computed regressions (showing just two pairs):
(select-keys all-regressions [[:sepal-length :sepal-width] 
                              [:petal-length :petal-width]])
;; Render the complete 4×4 SPLOM
(let [n 4
      grid-total-size (* n grid-panel-size)]
  (svg
   {:width grid-total-size :height grid-total-size}
   
   ;; Background panels
   (for [row (range n)
         col (range n)]
     (svg/rect [(* col grid-panel-size) (* row grid-panel-size)]
               grid-panel-size grid-panel-size
               {:fill (:grey-bg colors)}))
   
   ;; Render all 16 panels
   (for [row (range n)
         col (range n)]
     (let [x-col (all-cols col)
           y-col (all-cols row)
           regressions (get all-regressions [x-col y-col])]
       (render-panel x-col y-col row col regressions)))))

;; A complete scatter plot matrix! 
;; - 4 diagonal histograms show distributions
;; - 12 off-diagonal scatter plots show all pairwise relationships
;; - Per-species regression lines reveal species-specific trends
;;
;; Notice the symmetry: the upper and lower triangles are transposes of each other.
;; Some SPLOM designs only show one triangle to avoid redundancy.


;; ## Reflection: What We've Built
;;
;; Over the past 12 steps, we've built a complete scatter plot matrix from scratch.
;; We started with basic scatter plots, added color encoding by species, learned to
;; render histograms manually, arranged everything in grids, overlaid regression lines,
;; and finally abstracted the whole pattern to scale from 2×2 to 4×4.
;;
;; The code here is deliberately explicit. An upcoming library API 
;; would handle these details for you with sensibele defaults
;; which are still extensible and composable.
;; This will be the topic of another blogpost, coming soon.
