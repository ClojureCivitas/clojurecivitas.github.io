^{:kindly/hide-code true
  :clay {:title "Building a SPLOM from Scratch"
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
  "Building a scatter plot matrix (SPLOM) from scratch.
  
  We'll start with the simplest visualization and progressively add
  complexity, learning thi.ng/geom.viz along the way.")

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
;; Each step introduces one new concept.

;; ## Setup

(ns data-visualization.splom-tutorial
  (:require
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.viz.core :as viz]
   [thi.ng.geom.svg.core :as svg]
   [scicloj.metamorph.ml.rdatasets :as rdatasets]))

;; ## The Data
;;
;; We'll use the classic [Iris dataset](https://en.wikipedia.org/wiki/Iris_flower_data_set):
;; 150 flowers, 4 measurements each, 3 species.

(def iris (rdatasets/datasets-iris))

iris

;; Three species, 50 flowers each:
(-> iris (tc/group-by :species) (tc/row-count))

;; ## Step 0: SVG Helper with Hiccup Compatibility
;;
;; thi.ng/geom.viz returns vectors, but hiccup needs sequences for
;; element collections. This helper handles the conversion automatically.

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

;; Here's a simple example showing why we need this:
;; Create three colored circles as a collection

(def three-circles
  (mapv (fn [[x color]]
          (svg/circle [x 50] 20 {:fill color :stroke "none"}))
        [[30 "#F8766D"] 
         [70 "#619CFF"] 
         [110 "#00BA38"]]))

;; Without our helper, this would fail with hiccup:
;; ```clj
;; (kind/hiccup (svg/svg {:width 150 :height 100} three-circles))
;; ```


;; But our helper converts the vector to a seq automatically:
(svg {:width 150 :height 100} three-circles)


;; ## Step 1: Single Scatter Plot (Ungrouped)

;; Let's start with the simplest possible visualization: a scatter plot
;; of sepal length vs. sepal width, all points grey.

(def grey-bg "#EBEBEB")
(def grid-color "#FFFFFF")
(def grey-points "#333333")

(def sepal-points
  (mapv vector (iris :sepal-length) (iris :sepal-width)))

;; Create axes
(def x-axis
  (viz/linear-axis
   {:domain [4.3 7.9]
    :range [50 350]
    :major 1.0
    :pos 350
    :label-dist 12
    :major-size 3
    :minor-size 0
    :attribs {:stroke "none"}}))

(def y-axis
  (viz/linear-axis
   {:domain [2.0 4.5]
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
   :attribs {:fill grey-points :stroke "none"}
   :layout viz/svg-scatter-plot})

;; Assemble plot
(def plot-spec
  {:x-axis x-axis
   :y-axis y-axis
   :grid {:attribs {:stroke grid-color :stroke-width 1}}
   :data [scatter-series]})

(def scatter-plot
  (viz/svg-plot2d-cartesian plot-spec))

;; Render with grey background
(svg
    {:width 400 :height 400}
    (svg/rect [0 0] 400 400 {:fill grey-bg})
    scatter-plot)

;; We can see the relationship between sepal length and width!


;; ## Step 2: Color by Species
;;
;; Now let's color the points by species to see the three clusters.

;; Derive species from data
(def species-names (sort (distinct (iris :species))))

species-names

;; Define colors
(def species-colors
  ["#F8766D"   ;; Setosa (red-ish)
   "#619CFF"   ;; Versicolor (blue)
   "#00BA38"]) ;; Virginica (green)

;; Create species -> color map
(def species-color-map
  (zipmap species-names species-colors))

species-color-map

;; Group data by species
(def species-groups
  (tc/group-by iris :species {:result-type :as-map}))

;; Create one series per species
(def colored-scatter-series
  (mapv (fn [species color]
          (let [data (species-groups species)
                points (mapv vector (data :sepal-length) (data :sepal-width))]
            {:values points
             :attribs {:fill color :stroke "none"}
             :layout viz/svg-scatter-plot}))
        species-names
        species-colors))

;; Same plot, different data
(def colored-plot-spec
  {:x-axis x-axis
   :y-axis y-axis
   :grid {:attribs {:stroke grid-color :stroke-width 1}}
   :data colored-scatter-series})

(svg
    {:width 400 :height 400}
    (svg/rect [0 0] 400 400 {:fill grey-bg})
    (viz/svg-plot2d-cartesian colored-plot-spec))

;; Now we can see three distinct clusters! Setosa (red) is clearly separated.

;; ## Step 3: Single Histogram
;;
;; Before building a grid, let's learn how to render a histogram.
;; We'll show the distribution of sepal width.

(require '[fastmath.stats :as stats])
(require '[fastmath.ml.regression :as regr])

(def sepal-width-hist
  (stats/histogram (iris :sepal-width) :sturges))

(:bins-maps sepal-width-hist)

;; We need to manually render bars as SVG rectangles.
;; First, define scale functions to map data → pixels.

(defn make-x-scale [domain range-min range-max]
  (let [[d-min d-max] domain
        d-span (- d-max d-min)
        r-span (- range-max range-min)]
    (fn [value]
      (+ range-min (* (/ (- value d-min) d-span) r-span)))))

(defn make-y-scale [domain range-min range-max]
  (let [[d-min d-max] domain
        d-span (- d-max d-min)
        r-span (- range-max range-min)]
    (fn [value]
      (+ range-min (* (/ (- value d-min) d-span) r-span)))))

(def panel-size 400)
(def margin 50)

(def hist-x-scale
  (make-x-scale [2.0 4.5] margin (- panel-size margin)))

(def hist-y-scale
  (make-y-scale [0 36] (- panel-size margin) margin))

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
              {:fill grey-points 
               :stroke grid-color 
               :stroke-width 0.5})))
        (:bins-maps sepal-width-hist)))


;; Also create axes and grid
(def histogram-axes
  (let [x-axis (viz/linear-axis
                 {:domain [2.0 4.5]
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
     (viz/svg-axis-grid2d-cartesian x-axis y-axis {:attribs {:stroke grid-color :stroke-width 0.5}})]))
(svg
    {:width panel-size :height panel-size}
    (svg/rect [0 0] panel-size panel-size {:fill grey-bg})
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
  (make-y-scale [0 max-count] (- panel-size margin) margin))

;; Render bars for all species (with transparency for overlay)
(def colored-histogram-bars
  (mapcat
    (fn [idx {:keys [hist]}]
      (let [color (species-colors idx)]
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
                     :stroke grid-color
                     :stroke-width 0.5
                     :opacity 0.6})))
              (:bins-maps hist))))
    (range)
    sepal-width-hists-by-species))

;; Axes for colored histogram (using same domain as before)
(def colored-histogram-axes
  (let [x-axis (viz/linear-axis
                 {:domain [2.0 4.5]
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
     (viz/svg-axis-grid2d-cartesian x-axis y-axis {:attribs {:stroke grid-color :stroke-width 0.5}})]))

(svg
    {:width panel-size :height panel-size}
    (svg/rect [0 0] panel-size panel-size {:fill grey-bg})
    colored-histogram-axes
    colored-histogram-bars)

;; Beautiful! We can see Setosa (red) has wider sepals on average.


;; ## Step 5: 2×2 Grid of Scatter Plots
;;
;; Now let's create a grid showing relationships between two variables:
;; sepal.width and petal.length. We'll place scatter plots at offsets
;; to create a 2×2 grid.

(def grid-panel-size 200)
(def grid-margin 30)

;; We'll use two variables for our 2×2 grid
(def var1-data (iris :sepal-width))
(def var2-data (iris :petal-length))

;; Panel (0, 0): sepal.width vs sepal.width
(def panel-00
  (let [x-offset 0
        y-offset 0
        
        x-axis (viz/linear-axis
                {:domain [2.0 4.5]
                 :range [(+ x-offset grid-margin)
                         (+ x-offset grid-panel-size (- grid-margin))]
                 :major 0.5
                 :pos (+ y-offset grid-panel-size (- grid-margin))
                 :label-dist 12
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        y-axis (viz/linear-axis
                {:domain [2.0 4.5]
                 :range [(+ y-offset grid-panel-size (- grid-margin))
                         (+ y-offset grid-margin)]
                 :major 0.5
                 :pos (+ x-offset grid-margin)
                 :label-dist 12
                 :label-style {:text-anchor "end"}
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        series (mapv (fn [species color]
                       (let [data (species-groups species)
                             points (mapv vector (data :sepal-width) (data :sepal-width))]
                         {:values points
                          :attribs {:fill color :stroke "none"}
                          :layout viz/svg-scatter-plot}))
                     species-names
                     species-colors)]
    
    (viz/svg-plot2d-cartesian
     {:x-axis x-axis
      :y-axis y-axis
      :grid {:attribs {:stroke grid-color :stroke-width 0.5}}
      :data series})))

;; Panel (0, 1): sepal.width vs petal.length
(def panel-01
  (let [x-offset grid-panel-size
        y-offset 0
        
        x-axis (viz/linear-axis
                {:domain [1.0 7.0]
                 :range [(+ x-offset grid-margin)
                         (+ x-offset grid-panel-size (- grid-margin))]
                 :major 1.0
                 :pos (+ y-offset grid-panel-size (- grid-margin))
                 :label-dist 12
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        y-axis (viz/linear-axis
                {:domain [2.0 4.5]
                 :range [(+ y-offset grid-panel-size (- grid-margin))
                         (+ y-offset grid-margin)]
                 :major 0.5
                 :pos (+ x-offset grid-margin)
                 :label-dist 12
                 :label-style {:text-anchor "end"}
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        series (mapv (fn [species color]
                       (let [data (species-groups species)
                             points (mapv vector (data :petal-length) (data :sepal-width))]
                         {:values points
                          :attribs {:fill color :stroke "none"}
                          :layout viz/svg-scatter-plot}))
                     species-names
                     species-colors)]
    
    (viz/svg-plot2d-cartesian
     {:x-axis x-axis
      :y-axis y-axis
      :grid {:attribs {:stroke grid-color :stroke-width 0.5}}
      :data series})))

;; Panel (1, 0): petal.length vs sepal.width  
(def panel-10
  (let [x-offset 0
        y-offset grid-panel-size
        
        x-axis (viz/linear-axis
                {:domain [2.0 4.5]
                 :range [(+ x-offset grid-margin)
                         (+ x-offset grid-panel-size (- grid-margin))]
                 :major 0.5
                 :pos (+ y-offset grid-panel-size (- grid-margin))
                 :label-dist 12
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        y-axis (viz/linear-axis
                {:domain [1.0 7.0]
                 :range [(+ y-offset grid-panel-size (- grid-margin))
                         (+ y-offset grid-margin)]
                 :major 1.0
                 :pos (+ x-offset grid-margin)
                 :label-dist 12
                 :label-style {:text-anchor "end"}
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        series (mapv (fn [species color]
                       (let [data (species-groups species)
                             points (mapv vector (data :sepal-width) (data :petal-length))]
                         {:values points
                          :attribs {:fill color :stroke "none"}
                          :layout viz/svg-scatter-plot}))
                     species-names
                     species-colors)]
    
    (viz/svg-plot2d-cartesian
     {:x-axis x-axis
      :y-axis y-axis
      :grid {:attribs {:stroke grid-color :stroke-width 0.5}}
      :data series})))

;; Panel (1, 1): petal.length vs petal.length
(def panel-11
  (let [x-offset grid-panel-size
        y-offset grid-panel-size
        
        x-axis (viz/linear-axis
                {:domain [1.0 7.0]
                 :range [(+ x-offset grid-margin)
                         (+ x-offset grid-panel-size (- grid-margin))]
                 :major 1.0
                 :pos (+ y-offset grid-panel-size (- grid-margin))
                 :label-dist 12
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        y-axis (viz/linear-axis
                {:domain [1.0 7.0]
                 :range [(+ y-offset grid-panel-size (- grid-margin))
                         (+ y-offset grid-margin)]
                 :major 1.0
                 :pos (+ x-offset grid-margin)
                 :label-dist 12
                 :label-style {:text-anchor "end"}
                 :major-size 2
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        series (mapv (fn [species color]
                       (let [data (species-groups species)
                             points (mapv vector (data :petal-length) (data :petal-length))]
                         {:values points
                          :attribs {:fill color :stroke "none"}
                          :layout viz/svg-scatter-plot}))
                     species-names
                     species-colors)]
    
    (viz/svg-plot2d-cartesian
     {:x-axis x-axis
      :y-axis y-axis
      :grid {:attribs {:stroke grid-color :stroke-width 0.5}}
      :data series})))

;; Assemble the 2×2 grid
(def grid-total-size (* 2 grid-panel-size))

(svg
    {:width grid-total-size :height grid-total-size}
    
    ;; Backgrounds first (z-order)
    (svg/rect [0 0] grid-panel-size grid-panel-size {:fill grey-bg})
    (svg/rect [grid-panel-size 0] grid-panel-size grid-panel-size {:fill grey-bg})
    (svg/rect [0 grid-panel-size] grid-panel-size grid-panel-size {:fill grey-bg})
    (svg/rect [grid-panel-size grid-panel-size] grid-panel-size grid-panel-size {:fill grey-bg})
    
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
  (let [x-scale (viz/linear-scale [2.0 4.5] [grid-margin (- grid-panel-size grid-margin)])
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
                                 {:fill color :stroke grid-color :stroke-width 0.5 :opacity 0.7})))
                   (:bins-maps hist)))
            grid-sepal-width-hists
            species-colors)))

(def grid-hist-length-bars
  (let [x-scale (viz/linear-scale [1 7] [grid-margin (- grid-panel-size grid-margin)])
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
                                 {:fill color :stroke grid-color :stroke-width 0.5 :opacity 0.7})))
                   (:bins-maps hist)))
            grid-petal-length-hists
            species-colors)))

;; Axes for the histograms (with updated y-domains)
(def grid-hist-width-axes
  (let [max-count (apply max (mapcat #(map :count (:bins-maps %)) grid-sepal-width-hists))
        x-axis (viz/linear-axis
                 {:domain [2.0 4.5]
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
     (viz/svg-axis-grid2d-cartesian x-axis y-axis {:attribs {:stroke grid-color :stroke-width 0.5}})]))

(def grid-hist-length-axes
  (let [max-count (apply max (mapcat #(map :count (:bins-maps %)) grid-petal-length-hists))
        x-axis (viz/linear-axis
                 {:domain [1 7]
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
     (viz/svg-axis-grid2d-cartesian x-axis y-axis {:attribs {:stroke grid-color :stroke-width 0.5}})]))

;; Now render the grid with colored histograms on the diagonal
(svg
  {:width (* 2 grid-panel-size) :height (* 2 grid-panel-size)}
  
  ;; Background panels
  (svg/rect [0 0] grid-panel-size grid-panel-size {:fill grey-bg})
  (svg/rect [grid-panel-size 0] grid-panel-size grid-panel-size {:fill grey-bg})
  (svg/rect [0 grid-panel-size] grid-panel-size grid-panel-size {:fill grey-bg})
  (svg/rect [grid-panel-size grid-panel-size] grid-panel-size grid-panel-size {:fill grey-bg})
  
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


;; ## Step 8: Single Scatter with Regression Line
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
        x-scale (viz/linear-scale [4.0 8.0] [margin (- panel-size margin)])
        y-scale (viz/linear-scale [2.0 4.5] [(- panel-size margin) margin])
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
  (svg/rect [0 0] panel-size panel-size {:fill grey-bg})
  scatter-plot
  regression-line)

;; The red line shows the linear trend: sepal width slightly decreases
;; as sepal length increases.



;; ## Step 9: Single Scatter with Regression Lines by Species
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
  (let [x-scale (viz/linear-scale [4.0 8.0] [margin (- panel-size margin)])
        y-scale (viz/linear-scale [2.0 4.5] [(- panel-size margin) margin])
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
                         :opacity 0.8})))
          species-names)))

;; Render scatter plot with per-species regression lines
(svg
  {:width panel-size :height panel-size}
  (svg/rect [0 0] panel-size panel-size {:fill grey-bg})
  scatter-plot
  species-regression-lines)

;; Each species has its own trend! Setosa (red) has a positive slope,
;; while versicolor (green) and virginica (blue) have gentler relationships.

;; ## Step 10: 2×2 Grid with Regression Lines
;;
;; Add regression overlays to the scatter plots in our grid.

;; Compute regressions for both variable pairs
(def sepal-width-petal-length-regression
  (let [xs (iris :sepal-width)
        ys (iris :petal-length)
        xss (mapv vector xs)
        model (regr/lm ys xss)
        slope (first (:beta model))
        intercept (:intercept model)]
    {:slope slope :intercept intercept}))

(def petal-length-sepal-width-regression
  (let [xs (iris :petal-length)
        ys (iris :sepal-width)
        xss (mapv vector xs)
        model (regr/lm ys xss)
        slope (first (:beta model))
        intercept (:intercept model)]
    {:slope slope :intercept intercept}))

;; Create regression lines for grid panels (using panel coordinates)
(def grid-regression-01
  (let [{:keys [slope intercept]} sepal-width-petal-length-regression
        x-offset grid-panel-size
        y-offset 0
        x-scale (viz/linear-scale [2.0 4.5] [(+ x-offset grid-margin) (+ x-offset grid-panel-size (- grid-margin))])
        y-scale (viz/linear-scale [1 7] [(+ y-offset grid-panel-size (- grid-margin)) (+ y-offset grid-margin)])
        x1 2.0
        x2 4.5
        y1 (+ intercept (* slope x1))
        y2 (+ intercept (* slope x2))]
    (svg/line [(x-scale x1) (y-scale y1)]
              [(x-scale x2) (y-scale y2)]
              {:stroke "#FF6B6B" :stroke-width 2})))

(def grid-regression-10
  (let [{:keys [slope intercept]} petal-length-sepal-width-regression
        x-offset 0
        y-offset grid-panel-size
        x-scale (viz/linear-scale [1 7] [(+ x-offset grid-margin) (+ x-offset grid-panel-size (- grid-margin))])
        y-scale (viz/linear-scale [2.0 4.5] [(+ y-offset grid-panel-size (- grid-margin)) (+ y-offset grid-margin)])
        x1 1
        x2 7
        y1 (+ intercept (* slope x1))
        y2 (+ intercept (* slope x2))]
    (svg/line [(x-scale x1) (y-scale y1)]
              [(x-scale x2) (y-scale y2)]
              {:stroke "#FF6B6B" :stroke-width 2})))

;; Render grid with histograms and regression lines
(svg
  {:width (* 2 grid-panel-size) :height (* 2 grid-panel-size)}
  
  ;; Background panels
  (svg/rect [0 0] grid-panel-size grid-panel-size {:fill grey-bg})
  (svg/rect [grid-panel-size 0] grid-panel-size grid-panel-size {:fill grey-bg})
  (svg/rect [0 grid-panel-size] grid-panel-size grid-panel-size {:fill grey-bg})
  (svg/rect [grid-panel-size grid-panel-size] grid-panel-size grid-panel-size {:fill grey-bg})
  
  ;; Top-left: histogram for sepal.width
  (svg/group {:transform (str "translate(0,0)")}
             grid-hist-width-axes
             grid-hist-width-bars)
  
  ;; Top-right: sepal.width vs petal.length with regression
  panel-01
  grid-regression-01
  
  ;; Bottom-left: petal.length vs sepal.width with regression
  panel-10
  grid-regression-10
  
  ;; Bottom-right: histogram for petal.length
  (svg/group {:transform (str "translate(" grid-panel-size "," grid-panel-size ")")}
             grid-hist-length-axes
             grid-hist-length-bars))

;; Regression lines reveal different trends in each variable pair!
