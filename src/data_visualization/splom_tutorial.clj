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
(ns data-visualization.splom-tutorial)

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
;; progressing from a simple scatter plot to a fully interactive matrix.
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
(-> iris (tc/group-by :species) (tc/row-count))

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
   :species ["#F8766D" 
             "#619CFF" 
             "#00BA38"]})

;; Derive species names from data (used throughout)
(def species-names (sort (distinct (iris :species))))

;; Create a species -> color mapping
(def species-color-map
  (zipmap species-names (:species colors)))

species-color-map

;; Group data by species for later use
(def species-groups
  (tc/group-by iris :species {:result-type :as-map}))


;; Compute domains from data
;; We'll use these throughout to avoid hard-coding ranges.
(defn compute-domain
  "Compute [min max] domain for a variable, with optional padding."
  ([var-data] (compute-domain var-data 0.05))
  ([var-data padding]
   (let [min-val (apply min var-data)
         max-val (apply max var-data)
         span (- max-val min-val)
         pad (* span padding)]
     [(- min-val pad) (+ max-val pad)])))

(def sepal-length-domain (compute-domain (iris :sepal-length)))
(def sepal-width-domain (compute-domain (iris :sepal-width)))
(def petal-length-domain (compute-domain (iris :petal-length)))
(def petal-width-domain (compute-domain (iris :petal-width)))
;; ## Step 0: SVG as Hiccup
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

;; ## Step 1: Single Scatter Plot (Ungrouped)
;;
;; Let's start with the simplest possible visualization: a scatter plot
;; of sepal length vs. sepal width, all points grey.

(def sepal-points
  (mapv vector (iris :sepal-length) (iris :sepal-width)))

;; Create axes
(def x-axis
  (viz/linear-axis
   {:domain sepal-length-domain
    :range [50 350]
    :major 1.0
    :pos 350
    :label-dist 12
    :major-size 3
    :minor-size 0
    :attribs {:stroke "none"}}))

(def y-axis
  (viz/linear-axis
   {:domain sepal-width-domain
    :range [350 50]
    :major 0.5
    :pos 50
    :label-dist 12
    :label-style {:text-anchor "end"}
    :major-size 3
    :minor-size 0
    :attribs {:stroke "none"}}))

;; Create scatter series
(def scatter-series
  {:values sepal-points
   :attribs {:fill (:grey-points colors) :stroke "none"}
   :layout viz/svg-scatter-plot})

;; Assemble plot
(def plot-spec
  {:x-axis x-axis
   :y-axis y-axis
   :grid {:attribs {:stroke (:grid colors) :stroke-width 1}}
   :data [scatter-series]})

(def scatter-plot
  (viz/svg-plot2d-cartesian plot-spec))

;; Render with grey background
(svg
    {:width 400 :height 400}
    (svg/rect [0 0] 400 400 {:fill (:grey-bg colors)})
    scatter-plot)

;; We can see the relationship between sepal length and width!


;; ## Step 2: Color by Species
;;
;; Now let's color the points by species to see the three clusters.

;; Create one series per species
(def colored-scatter-series
  (mapv (fn [species color]
          (let [data (species-groups species)
                points (mapv vector (data :sepal-length) (data :sepal-width))]
            {:values points
             :attribs {:fill color :stroke "none"}
             :layout viz/svg-scatter-plot}))
        species-names
        (:species colors)))

;; Same plot, different data
(def colored-plot-spec
  {:x-axis x-axis
   :y-axis y-axis
   :grid {:attribs {:stroke (:grid colors) :stroke-width 1}}
   :data colored-scatter-series})

(svg
    {:width 400 :height 400}
    (svg/rect [0 0] 400 400 {:fill (:grey-bg colors)})
    (viz/svg-plot2d-cartesian colored-plot-spec))

;; Now we can see three distinct clusters! Setosa (red) is clearly separated.

;; ## Step 3: Single Histogram
;;
;; Before building a grid, let's learn how to render a histogram.
;; We'll show the distribution of sepal width.


(def sepal-width-hist
  (stats/histogram (iris :sepal-width) :sturges))

(:bins-maps sepal-width-hist)

;; We need to manually render bars as SVG rectangles.
;; We'll use viz/linear-scale to map data values → pixel coordinates.

(def panel-size 400)
(def margin 50)

(def hist-x-scale
  (viz/linear-scale sepal-width-domain [margin (- panel-size margin)]))

(def hist-y-scale
  (viz/linear-scale [0 36] [(- panel-size margin) margin]))

;; Render bars
(def histogram-bars
  (map (fn [{:keys [min max count]}]
          (let [x1 (hist-x-scale min)
                x2 (hist-x-scale max)
                y (hist-y-scale count)
                bar-width (- x2 x1)
                bar-height (- (- panel-size margin) y)]
            (svg/rect
              [x1 y]
              bar-width
              bar-height
              {:fill (:grey-points colors) 
               :stroke (:grid colors) 
               :stroke-width 0.5})))
        (:bins-maps sepal-width-hist)))


;; Also create axes and grid
(def histogram-axes
  (let [x-axis (viz/linear-axis
                 {:domain sepal-width-domain
                  :range [margin (- panel-size margin)]
                  :major 0.5
                  :pos (- panel-size margin)
                  :label-dist 12
                  :major-size 3
                  :minor-size 0
                  :attribs {:stroke "none"}})
        
        y-axis (viz/linear-axis
                 {:domain [0 36]
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
     (viz/svg-axis-grid2d-cartesian x-axis y-axis {:attribs {:stroke (:grid colors) :stroke-width 0.5}})]))
(svg
    {:width panel-size :height panel-size}
    (svg/rect [0 0] panel-size panel-size {:fill (:grey-bg colors)})
    histogram-axes
    histogram-bars)

;; A simple histogram! Most flowers have sepal width around 3.0.


;; ## Step 4: Colored Histogram (Overlaid by Species)
;;
;; Now let's overlay three histograms (one per species) to see their
;; different distributions.

(def sepal-width-hists-by-species
  (mapv (fn [species]
          {:species species
           :hist (stats/histogram ((species-groups species) :sepal-width) :sturges)})
        species-names))

;; Find max count across all species for y-scale
(def max-count
  (apply max
         (mapcat (fn [{:keys [hist]}]
                   (map :count (:bins-maps hist)))
                 sepal-width-hists-by-species)))

(def hist-y-scale-colored
  (viz/linear-scale [0 max-count] [(- panel-size margin) margin]))

;; Render bars for all species (with transparency for overlay)
(def colored-histogram-bars
  (mapcat
    (fn [idx {:keys [hist]}]
      (let [color ((:species colors) idx)]
        (map (fn [{:keys [min max count]}]
                (let [x1 (hist-x-scale min)
                      x2 (hist-x-scale max)
                      y (hist-y-scale-colored count)
                      bar-width (- x2 x1)
                      bar-height (- (- panel-size margin) y)]
                  (svg/rect
                    [x1 y]
                    bar-width
                    bar-height
                    {:fill color
                     :stroke (:grid colors)
                     :stroke-width 0.5
                     :opacity 0.7})))
              (:bins-maps hist))))
    (range)
    sepal-width-hists-by-species))

;; Axes for colored histogram (using same domain as before)
(def colored-histogram-axes
  (let [x-axis (viz/linear-axis
                 {:domain sepal-width-domain
                  :range [margin (- panel-size margin)]
                  :major 0.5
                  :pos (- panel-size margin)
                  :label-dist 12
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
     (viz/svg-axis-grid2d-cartesian x-axis y-axis {:attribs {:stroke (:grid colors) :stroke-width 0.5}})]))

(svg
    {:width panel-size :height panel-size}
    (svg/rect [0 0] panel-size panel-size {:fill (:grey-bg colors)})
    colored-histogram-axes
    colored-histogram-bars)

;; Beautiful! We can see Setosa (red) has wider sepals on average.



;; Helper function for creating scatter panels in a grid
;; We'll use this to reduce repetition when building our 2×2 grid.
(defn make-scatter-panel
  "Create a scatter plot panel at grid position (row, col).
  
  Parameters:
  - x-var: keyword for x-axis variable (e.g., :sepal-width)
  - y-var: keyword for y-axis variable
  - x-domain: [min max] for x-axis
  - y-domain: [min max] for y-axis
  - x-major: major tick spacing for x-axis
  - y-major: major tick spacing for y-axis
  - row: grid row (0 or 1)
  - col: grid column (0 or 1)"
  [x-var y-var x-domain y-domain x-major y-major row col]
  (let [x-offset (* col grid-panel-size)
        y-offset (* row grid-panel-size)
        
        x-axis (viz/linear-axis
                {:domain x-domain
                 :range [(+ x-offset grid-margin)
                         (+ x-offset grid-panel-size (- grid-margin))]
                 :major x-major
                 :pos (+ y-offset grid-panel-size (- grid-margin))
                 :label-dist 12
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        y-axis (viz/linear-axis
                {:domain y-domain
                 :range [(+ y-offset grid-panel-size (- grid-margin))
                         (+ y-offset grid-margin)]
                 :major y-major
                 :pos (+ x-offset grid-margin)
                 :label-dist 12
                 :label-style {:text-anchor "end"}
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        series (mapv (fn [species color]
                       (let [data (species-groups species)
                             points (mapv vector (data x-var) (data y-var))]
                         {:values points
                          :attribs {:fill color :stroke "none"}
                          :layout viz/svg-scatter-plot}))
                     species-names
                     (:species colors))]
    
    (viz/svg-plot2d-cartesian
     {:x-axis x-axis
      :y-axis y-axis
      :grid {:attribs {:stroke (:grid colors) :stroke-width 0.5}}
      :data series})))

;; ## Step 5: 2×2 Grid of Scatter Plots
;;
;; Now let's create a grid showing relationships between two variables:
;; sepal.width and petal.length. We'll use our helper function to
;; create panels at different grid positions.

(def grid-panel-size 200)
(def grid-margin 30)

;; Create all four panels using the helper function
(def panel-00
  (make-scatter-panel :sepal-width :sepal-width
                      [2.0 4.5] [2.0 4.5]
                      0.5 0.5
                      0 0))

(def panel-01
  (make-scatter-panel :petal-length :sepal-width
                      [1.0 7.0] [2.0 4.5]
                      1.0 0.5
                      0 1))

(def panel-10
  (make-scatter-panel :sepal-width :petal-length
                      [2.0 4.5] [1.0 7.0]
                      0.5 1.0
                      1 0))

(def panel-11
  (make-scatter-panel :petal-length :petal-length
                      [1.0 7.0] [1.0 7.0]
                      1.0 1.0
                      1 1))

;; Assemble the 2×2 grid
(def grid-total-size (* 2 grid-panel-size))

(svg
    {:width grid-total-size :height grid-total-size}
    
    ;; Backgrounds first (z-order)
    (svg/rect [0 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
    (svg/rect [grid-panel-size 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
    (svg/rect [0 grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
    (svg/rect [grid-panel-size grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
    
    ;; Then the plots
    panel-00
    panel-01
    panel-10
    panel-11)

;; We can see relationships! Notice the diagonal panels (0,0) and (1,1)
;; show x=y which isn't very informative. Let's fix that next.
;; ## Step 6: 2×2 Grid with Diagonal Histograms
;;
;; Replace the uninformative diagonal panels (where x=y) with histograms.

;; Create histograms for both variables, grouped by species
(def grid-sepal-width-hists
  (mapv (fn [species]
          (let [data (species-groups species)]
            (stats/histogram (data :sepal-width) 12)))
        species-names))

(def grid-petal-length-hists
  (mapv (fn [species]
          (let [data (species-groups species)]
            (stats/histogram (data :petal-length) 12)))
        species-names))

;; Colored histogram bars for grid (with independent y-scales)
(def grid-hist-width-bars
  (let [x-scale (viz/linear-scale sepal-width-domain [grid-margin (- grid-panel-size grid-margin)])
        max-count (apply max (mapcat #(map :count (:bins-maps %)) grid-sepal-width-hists))
        y-scale (viz/linear-scale [0 max-count] [(- grid-panel-size grid-margin) grid-margin])]
    (mapcat (fn [hist color]
              (map (fn [{:keys [min max count]}]
                     (let [x1 (x-scale min)
                           x2 (x-scale max)
                           y (y-scale count)
                           bar-width (- x2 x1)
                           bar-height (- (- grid-panel-size grid-margin) y)]
                       (svg/rect [x1 y] bar-width bar-height
                                 {:fill color :stroke (:grid colors) :stroke-width 0.5 :opacity 0.7})))
                   (:bins-maps hist)))
            grid-sepal-width-hists
            (:species colors))))

(def grid-hist-length-bars
  (let [x-scale (viz/linear-scale petal-length-domain [grid-margin (- grid-panel-size grid-margin)])
        max-count (apply max (mapcat #(map :count (:bins-maps %)) grid-petal-length-hists))
        y-scale (viz/linear-scale [0 max-count] [(- grid-panel-size grid-margin) grid-margin])]
    (mapcat (fn [hist color]
              (map (fn [{:keys [min max count]}]
                     (let [x1 (x-scale min)
                           x2 (x-scale max)
                           y (y-scale count)
                           bar-width (- x2 x1)
                           bar-height (- (- grid-panel-size grid-margin) y)]
                       (svg/rect [x1 y] bar-width bar-height
                                 {:fill color :stroke (:grid colors) :stroke-width 0.5 :opacity 0.7})))
                   (:bins-maps hist)))
            grid-petal-length-hists
            (:species colors))))

;; Axes for the histograms (with updated y-domains)
(def grid-hist-width-axes
  (let [max-count (apply max (mapcat #(map :count (:bins-maps %)) grid-sepal-width-hists))
        x-axis (viz/linear-axis
                 {:domain sepal-width-domain
                  :range [grid-margin (- grid-panel-size grid-margin)]
                  :major 0.5
                  :pos (- grid-panel-size grid-margin)
                  :label-dist 12
                  :major-size 3
                  :minor-size 0
                  :attribs {:stroke "none"}})
        y-axis (viz/linear-axis
                 {:domain [0 max-count]
                  :range [(- grid-panel-size grid-margin) grid-margin]
                  :major (if (> max-count 20) 5 2)
                  :pos grid-margin
                  :label-dist 12
                  :label-style {:text-anchor "end"}
                  :major-size 3
                  :minor-size 0
                  :attribs {:stroke "none"}})]
    [(viz/svg-x-axis-cartesian x-axis)
     (viz/svg-y-axis-cartesian y-axis)
     (viz/svg-axis-grid2d-cartesian x-axis y-axis {:attribs {:stroke (:grid colors) :stroke-width 0.5}})]))

(def grid-hist-length-axes
  (let [max-count (apply max (mapcat #(map :count (:bins-maps %)) grid-petal-length-hists))
        x-axis (viz/linear-axis
                 {:domain petal-length-domain
                  :range [grid-margin (- grid-panel-size grid-margin)]
                  :major 1
                  :pos (- grid-panel-size grid-margin)
                  :label-dist 12
                  :major-size 3
                  :minor-size 0
                  :attribs {:stroke "none"}})
        y-axis (viz/linear-axis
                 {:domain [0 max-count]
                  :range [(- grid-panel-size grid-margin) grid-margin]
                  :major (if (> max-count 20) 5 2)
                  :pos grid-margin
                  :label-dist 12
                  :label-style {:text-anchor "end"}
                  :major-size 3
                  :minor-size 0
                  :attribs {:stroke "none"}})]
    [(viz/svg-x-axis-cartesian x-axis)
     (viz/svg-y-axis-cartesian y-axis)
     (viz/svg-axis-grid2d-cartesian x-axis y-axis {:attribs {:stroke (:grid colors) :stroke-width 0.5}})]))

;; Now render the grid with colored histograms on the diagonal
(svg
  {:width (* 2 grid-panel-size) :height (* 2 grid-panel-size)}
  
  ;; Background panels
  (svg/rect [0 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
  (svg/rect [grid-panel-size 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
  (svg/rect [0 grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
  (svg/rect [grid-panel-size grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
  
  ;; Top-left: histogram for sepal.width
  (svg/group {:transform (str "translate(0,0)")}
             grid-hist-width-axes
             grid-hist-width-bars)
  
  ;; Top-right: sepal.width vs petal.length
  panel-01
  
  ;; Bottom-left: petal.length vs sepal.width  
  panel-10
  
  ;; Bottom-right: histogram for petal.length
  (svg/group {:transform (str "translate(" grid-panel-size "," grid-panel-size ")")}
             grid-hist-length-axes
             grid-hist-length-bars))

;; Perfect! Now we can see both relationships and distributions by species.


;; ## Step 7: Single Scatter with Regression Line
;;
;; Add a linear regression overlay to understand the trend.

;; Compute linear regression for sepal dimensions
(def sepal-regression
  (let [xs (iris :sepal-length)
        ys (iris :sepal-width)
        xss (mapv vector xs)
        model (regr/lm ys xss)
        slope (first (:beta model))
        intercept (:intercept model)]
    {:slope slope :intercept intercept}))

;; Create regression line path
(def regression-line
  (let [{:keys [slope intercept]} sepal-regression
        x-scale (viz/linear-scale sepal-length-domain [margin (- panel-size margin)])
        y-scale (viz/linear-scale sepal-width-domain [(- panel-size margin) margin])
        x1 4.0
        x2 8.0
        y1 (+ intercept (* slope x1))
        y2 (+ intercept (* slope x2))]
    (svg/line [(x-scale x1) (y-scale y1)]
              [(x-scale x2) (y-scale y2)]
              {:stroke "#FF6B6B" :stroke-width 2})))

;; Render scatter plot with regression overlay
(svg
  {:width panel-size :height panel-size}
  (svg/rect [0 0] panel-size panel-size {:fill (:grey-bg colors)})
  scatter-plot
  regression-line)

;; The red line shows the linear trend: sepal width slightly decreases
;; as sepal length increases.



;; ## Step 8: Single Scatter with Regression Lines by Species
;;
;; Compute separate regression lines for each species group.

;; Compute regressions per species
(def species-regressions
  (into {}
        (for [species species-names]
          (let [species-data (tc/select-rows iris #(= (% :species) species))
                xs (species-data :sepal-length)
                ys (species-data :sepal-width)
                xss (mapv vector xs)
                model (regr/lm ys xss)
                slope (first (:beta model))
                intercept (:intercept model)]
            [species {:slope slope :intercept intercept}]))))

species-regressions

;; Create regression lines for each species
(def species-regression-lines
  (let [x-scale (viz/linear-scale sepal-length-domain [margin (- panel-size margin)])
        y-scale (viz/linear-scale sepal-width-domain [(- panel-size margin) margin])
        x1 4.0
        x2 8.0]
    (mapv (fn [species]
            (let [{:keys [slope intercept]} (species-regressions species)
                  y1 (+ intercept (* slope x1))
                  y2 (+ intercept (* slope x2))]
              (svg/line [(x-scale x1) (y-scale y1)]
                        [(x-scale x2) (y-scale y2)]
                        {:stroke (species-color-map species)
                         :stroke-width 2
                         :opacity 0.7})))
          species-names)))

;; Render scatter plot with per-species regression lines
(svg
  {:width panel-size :height panel-size}
  (svg/rect [0 0] panel-size panel-size {:fill (:grey-bg colors)})
  scatter-plot
  species-regression-lines)

;; Each species has its own trend! Setosa (red) has a positive slope,
;; while versicolor (green) and virginica (blue) have gentler relationships.

;; ## Step 9: 2×2 Grid with Regression Lines
;;
;; Add per-species regression overlays to the scatter plots in our grid.

;; Compute per-species regressions for sepal.width vs petal.length
(def sepal-width-petal-length-regressions
  (into {}
        (for [species species-names]
          (let [species-data (tc/select-rows iris #(= (% :species) species))
                xs (species-data :sepal-width)
                ys (species-data :petal-length)
                xss (mapv vector xs)
                model (regr/lm ys xss)
                slope (first (:beta model))
                intercept (:intercept model)]
            [species {:slope slope :intercept intercept}]))))

;; Compute per-species regressions for petal.length vs sepal.width
(def petal-length-sepal-width-regressions
  (into {}
        (for [species species-names]
          (let [species-data (tc/select-rows iris #(= (% :species) species))
                xs (species-data :petal-length)
                ys (species-data :sepal-width)
                xss (mapv vector xs)
                model (regr/lm ys xss)
                slope (first (:beta model))
                intercept (:intercept model)]
            [species {:slope slope :intercept intercept}]))))

;; Create regression lines for panel-01 (top-right: sepal.width vs petal.length)
(def grid-regressions-01
  (let [x-offset grid-panel-size
        y-offset 0
        x-scale (viz/linear-scale sepal-width-domain [(+ x-offset grid-margin) (+ x-offset grid-panel-size (- grid-margin))])
        y-scale (viz/linear-scale petal-length-domain [(+ y-offset grid-panel-size (- grid-margin)) (+ y-offset grid-margin)])
        x1 2.0
        x2 4.5]
    (mapv (fn [species]
            (let [{:keys [slope intercept]} (sepal-width-petal-length-regressions species)
                  y1 (+ intercept (* slope x1))
                  y2 (+ intercept (* slope x2))]
              (svg/line [(x-scale x1) (y-scale y1)]
                        [(x-scale x2) (y-scale y2)]
                        {:stroke (species-color-map species)
                         :stroke-width 2
                         :opacity 0.7})))
          species-names)))

;; Create regression lines for panel-10 (bottom-left: petal.length vs sepal.width)
(def grid-regressions-10
  (let [x-offset 0
        y-offset grid-panel-size
        x-scale (viz/linear-scale petal-length-domain [(+ x-offset grid-margin) (+ x-offset grid-panel-size (- grid-margin))])
        y-scale (viz/linear-scale sepal-width-domain [(+ y-offset grid-panel-size (- grid-margin)) (+ y-offset grid-margin)])
        x1 1
        x2 7]
    (mapv (fn [species]
            (let [{:keys [slope intercept]} (petal-length-sepal-width-regressions species)
                  y1 (+ intercept (* slope x1))
                  y2 (+ intercept (* slope x2))]
              (svg/line [(x-scale x1) (y-scale y1)]
                        [(x-scale x2) (y-scale y2)]
                        {:stroke (species-color-map species)
                         :stroke-width 2
                         :opacity 0.7})))
          species-names)))

;; Render grid with histograms and per-species regression lines
(svg
 {:width (* 2 grid-panel-size) :height (* 2 grid-panel-size)}
 
 ;; Background panels
 (svg/rect [0 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
 (svg/rect [grid-panel-size 0] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
 (svg/rect [0 grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
 (svg/rect [grid-panel-size grid-panel-size] grid-panel-size grid-panel-size {:fill (:grey-bg colors)})
 
 ;; Top-left: histogram for sepal.width
 (svg/group {:transform (str "translate(0,0)")}
            grid-hist-width-axes
            grid-hist-width-bars)
 
 ;; Top-right: sepal.width vs petal.length with per-species regressions
 panel-01
 grid-regressions-01
 
 ;; Bottom-left: petal.length vs sepal.width with per-species regressions
 panel-10
 grid-regressions-10
 
 ;; Bottom-right: histogram for petal.length
 (svg/group {:transform (str "translate(" grid-panel-size "," grid-panel-size ")")}
            grid-hist-length-axes
            grid-hist-length-bars))

;; Beautiful! Each species shows its own trend in both scatter panels.
;; Notice how the three colored regression lines reveal different relationships
;; for each species across the grid.
