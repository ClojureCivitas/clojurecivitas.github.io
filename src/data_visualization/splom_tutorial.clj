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
  complexity, learning thi.ng/geom.viz along the way."
  (:require
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.viz.core :as viz]
   [thi.ng.geom.svg.core :as svg]))

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

;; ## The Data
;;
;; We'll use the classic [Iris dataset](https://en.wikipedia.org/wiki/Iris_flower_data_set):
;; 150 flowers, 4 measurements each, 3 species.

(def iris
  (tc/dataset "https://gist.githubusercontent.com/netj/8836201/raw/6f9306ad21398ea43cba4f7d537619d0e07d5ae3/iris.csv"
              {:key-fn keyword}))

iris

;; Three species, 50 flowers each:
(-> iris (tc/group-by :variety) (tc/row-count))

;; ## Step 1: Single Scatter Plot
;;
;; Let's start with the simplest possible visualization: a scatter plot
;; of sepal length vs. sepal width, all points grey.

(def grey-bg "#EBEBEB")
(def grid-color "#FFFFFF")
(def grey-points "#333333")

(def sepal-points
  (mapv vector (iris :sepal.length) (iris :sepal.width)))

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
(kind/hiccup
  (svg/svg
    {:width 400 :height 400}
    (svg/rect [0 0] 400 400 {:fill grey-bg})
    scatter-plot))

;; We can see the relationship between sepal length and width!


;; ## Step 2: Color by Species
;;
;; Now let's color the points by species to see the three clusters.

(def species-colors
  ["#F8766D"   ;; Setosa (red-ish)
   "#619CFF"   ;; Versicolor (blue)
   "#00BA38"]) ;; Virginica (green)

;; Group data by species
(def species-groups
  (tc/group-by iris :variety {:result-type :as-map}))

(def species-names ["Setosa" "Versicolor" "Virginica"])

;; Create one series per species
(def colored-scatter-series
  (mapv (fn [species color]
          (let [data (species-groups species)
                points (mapv vector (data :sepal.length) (data :sepal.width))]
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

(kind/hiccup
  (svg/svg
    {:width 400 :height 400}
    (svg/rect [0 0] 400 400 {:fill grey-bg})
    (viz/svg-plot2d-cartesian colored-plot-spec)))

;; Now we can see three distinct clusters! Setosa (red) is clearly separated.

;; ## Step 3: Single Histogram
;;
;; Before building a grid, let's learn how to render a histogram.
;; We'll show the distribution of sepal width.

(require '[fastmath.stats :as stats])

(def sepal-width-hist
  (stats/histogram (iris :sepal.width) :sturges))

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

(kind/hiccup
  (svg/svg
    {:width panel-size :height panel-size}
    (svg/rect [0 0] panel-size panel-size {:fill grey-bg})
    histogram-bars))

;; A simple histogram! Most flowers have sepal width around 3.0.


;; ## Step 4: Colored Histogram (Overlaid by Species)
;;
;; Now let's overlay three histograms (one per species) to see their
;; different distributions.

(def sepal-width-hists-by-species
  (mapv (fn [species]
          {:species species
           :hist (stats/histogram ((species-groups species) :sepal.width) :sturges)})
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

(kind/hiccup
  (svg/svg
    {:width panel-size :height panel-size}
    (svg/rect [0 0] panel-size panel-size {:fill grey-bg})
    colored-histogram-bars))

;; Beautiful! We can see Setosa (red) has wider sepals on average.


;; ## Step 5: 2×2 Grid of Scatter Plots
;;
;; Now let's create a grid showing relationships between two variables:
;; sepal.width and petal.length. We'll place scatter plots at offsets
;; to create a 2×2 grid.

(def grid-panel-size 200)
(def grid-margin 30)

;; We'll use two variables for our 2×2 grid
(def var1-data (iris :sepal.width))
(def var2-data (iris :petal.length))

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
                             points (mapv vector (data :sepal.width) (data :sepal.width))]
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
                             points (mapv vector (data :petal.length) (data :sepal.width))]
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
                             points (mapv vector (data :sepal.width) (data :petal.length))]
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
                             points (mapv vector (data :petal.length) (data :petal.length))]
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

(kind/hiccup
  (svg/svg
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
    panel-11))

;; We can see relationships! Notice the diagonal panels (0,0) and (1,1)
;; show x=y which isn't very informative. Let's fix that next.

