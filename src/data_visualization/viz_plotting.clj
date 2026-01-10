(ns data-visualization.viz-plotting
  "Grammar of Graphics using thi.ng/geom.viz for rendering.
  
  Starting with minimal rendering functions, will gradually add
  the full pipeline from refined-plotting.clj"
  (:require
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.viz.core :as viz]
   [fastmath.ml.regression :as regr]
   [fastmath.stats :as stats]
   [thi.ng.geom.svg.core :as svg]))

;; # Theme

(def theme
  "Visual theme for plots - ggplot2-inspired aesthetic
  
  ggplot2 theme_gray characteristics:
  - Grey background (#EBEBEB)
  - White grid lines  
  - NO visible axis lines
  - Very short tick marks (~3px)
  - Dark grey points (#333333) by default"
  {:background "#EBEBEB"
   :grid "#FFFFFF"
   :colors ["#F8766D" "#619CFF" "#00BA38" "#F564E3" "#B79F00" 
            "#00BFC4" "#FF61C3" "#C77CFF" "#7CAE00" "#00B4F0"]})

(defn get-color
  "Get color from theme by index (cycles if index exceeds palette size)"
  [index]
  (nth (:colors theme) (mod index (count (:colors theme)))))

;; Examples - color palette
(mapv get-color (range 5))

;; Colors cycle
(get-color 12)

;; # Simple Rendering Functions

(defn make-viz-spec
  "Create a thi.ng/geom.viz specification for 2D cartesian plot.
  
  Args:
  - data: Vector of [x y] points
  - plot-type: :scatter, :line, etc.
  - opts: Optional map with :x-domain, :y-domain, :width, :height, :attribs, :theme
  
  Returns:
  - viz spec map ready for svg-plot2d-cartesian"
  [data plot-type {:keys [x-domain y-domain width height attribs theme-opts]
                   :or {width 400 height 300
                        attribs {:fill "#333333"}
                        theme-opts theme}}]
  (let [;; Extract x and y values
        xs (map first data)
        ys (map second data)
        
        ;; Compute domains if not provided
        x-domain (or x-domain [(apply min xs) (apply max xs)])
        y-domain (or y-domain [(apply min ys) (apply max ys)])
        
        ;; Panel margins
        margin-left 50
        margin-right 20
        margin-top 20
        margin-bottom 40
        
        ;; Panel boundaries
        panel-left margin-left
        panel-right (- width margin-right)
        panel-top margin-top
        panel-bottom (- height margin-top)
        
        ;; Create axes with ggplot2-style appearance:
        ;; - Very short tick marks (major-size: 3)
        ;; - No visible axis lines (stroke: none)
        x-axis (viz/linear-axis
                {:domain x-domain
                 :range [panel-left panel-right]
                 :major (/ (- (second x-domain) (first x-domain)) 5)
                 :pos panel-bottom
                 :major-size 3
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        y-axis (viz/linear-axis
                {:domain y-domain
                 :range [panel-bottom panel-top]
                 :major (/ (- (second y-domain) (first y-domain)) 5)
                 :pos panel-left
                 :major-size 3
                 :minor-size 0
                 :attribs {:stroke "none"}})
        
        ;; Choose layout function based on plot type
        layout-fn (case plot-type
                    :scatter viz/svg-scatter-plot
                    :line viz/svg-line-plot
                    viz/svg-scatter-plot)]
    
    {:x-axis x-axis
     :y-axis y-axis
     :grid {:attribs {:stroke (:grid theme-opts) :stroke-width 1}}
     :data [{:values data
             :attribs attribs
             :layout layout-fn}]}))

(defn plot-simple
  "Render a simple plot from data points.
  
  Example:
  (plot-simple [[1 2] [3 4] [5 6]] :scatter)
  (plot-simple [[1 2] [3 4] [5 6]] :line {:attribs {:stroke \"#f60\"}})"
  [data plot-type & [opts]]
  (let [width (get opts :width 400)
        height (get opts :height 300)
        theme-opts (get opts :theme theme)
        
        ;; Background rectangle
        background (svg/rect [0 0] width height {:fill (:background theme-opts)})
        
        ;; Generate plot
        plot-elements (viz/svg-plot2d-cartesian
                       (make-viz-spec data plot-type (or opts {})))]
    (-> (apply svg/svg {:width width :height height} 
               (cons background plot-elements))
        kind/hiccup)))

;; # Examples


;; Simple scatter plot - ggplot2 style!
;; Grey background, dark grey points, no axis lines, short ticks
(plot-simple [[1 2] [3 4] [5 6] [7 8]] :scatter)

;; Line plot
(plot-simple [[1 2] [3 4] [5 6] [7 8]] :line
             {:attribs {:stroke "#ff6600" :fill "none"}})

;; Using theme colors
(plot-simple [[1 2] [2 3] [3 5] [4 4] [5 6]] :scatter
             {:attribs {:fill (get-color 0)}})

;; # Design Example: Scatter + Regression
;;
;; This section shows a complete example built with raw geom.viz,
;; then discusses what the Grammar of Graphics layer should automate.

;; ## Load Data

(def penguins 

  (-> (tc/dataset "https://raw.githubusercontent.com/allisonhorst/palmerpenguins/master/inst/extdata/penguins.csv" {:key-fn keyword})

      (tc/drop-missing)))

;; ## Manual Steps (Raw geom.viz)

;; Step 1: Group by species
(def species-groups
  (tc/group-by penguins :species {:result-type :as-map}))

;; Step 2: Extract points for each species
(def species-data
  (into {}
    (map (fn [[species-name group-ds]]
           [species-name
            {:points (mapv vector 
                          (group-ds :bill_length_mm)
                          (group-ds :bill_depth_mm))}])
         species-groups)))

(map (fn [[k v]] [k (count (:points v))]) species-data)

;; Step 3: Linear regression function (using fastmath)
(defn linear-fit 
  "Compute least-squares linear regression for points [[x y] ...] using fastmath"
  [points]
  (let [x-vals (mapv first points)
        y-vals (mapv second points)
        xss (mapv vector x-vals)
        model (regr/lm y-vals xss)
        intercept (:intercept model)
        slope (first (:beta model))]
    {:slope slope :intercept intercept}))

;; Step 4: Compute regression for each species
(def species-regressions
  (into {}
    (map (fn [[species data]]
           [species (linear-fit (:points data))])
         species-data)))

species-regressions

;; Step 5: Color mapping (manual)
(def ggplot2-colors
  ["#F8766D" "#00BA38" "#619CFF"])

;; Step 6: Compute overall domain
(def all-points (mapcat :points (vals species-data)))
(def x-domain [(reduce min (map first all-points))
               (reduce max (map first all-points))])
(def y-domain [(reduce min (map second all-points))
               (reduce max (map second all-points))])

[x-domain y-domain]

;; Step 7: Generate regression line points
(defn regression-line-points 
  "Generate [x y] points for regression line across domain"
  [{:keys [slope intercept]} [x-min x-max]]
  [[x-min (+ (* slope x-min) intercept)]
   [x-max (+ (* slope x-max) intercept)]])

;; Step 8: Build complete viz spec manually
(def scatter-with-regression
  (let [species-list ["Adelie" "Gentoo" "Chinstrap"]
        
        ;; Create scatter series for each species
        scatter-series
        (map-indexed 
          (fn [idx species]
            {:values (get-in species-data [species :points])
             :attribs {:fill (ggplot2-colors idx) :stroke "none"}
             :layout viz/svg-scatter-plot})
          species-list)
        
        ;; Create line series for each species regression
        ;; Each line spans only its species' x-range
        line-series
        (map-indexed
          (fn [idx species]
            (let [regression (species-regressions species)
                  species-xs (map first (get-in species-data [species :points]))
                  species-x-domain [(reduce min species-xs) (reduce max species-xs)]
                  line-points (regression-line-points regression species-x-domain)]
              {:values line-points
               :attribs {:stroke (ggplot2-colors idx) 
                        :stroke-width 2
                        :fill "none"}
               :layout viz/svg-line-plot}))
          species-list)]
    
    {:x-axis (viz/linear-axis
               {:domain x-domain
                :range [50 550]
                :major 10
                :minor 5
                :pos 350
                :label-dist 15
                :major-size 3
                :minor-size 0
                :attribs {:stroke "none"}})
     :y-axis (viz/linear-axis
               {:domain y-domain
                :range [350 50]
                :major 2
                :minor 1
                :pos 50
                :label-dist 15
                :label-style {:text-anchor "end"}
                :major-size 3
                :minor-size 0
                :attribs {:stroke "none"}})
     :grid {:attribs {:stroke "#FFFFFF" :stroke-width 1}
            :minor-y false}
     :data (vec (concat scatter-series line-series))}))
;; Step 9: Render
(defn render-plot [spec]
  (let [bg-rect [:rect {:x 50 :y 50 :width 500 :height 300 
                       :fill "#EBEBEB"}]]
    (-> (apply svg/svg
               {:width 600 :height 400}
               bg-rect
               (viz/svg-plot2d-cartesian spec))
        kind/hiccup)))

;; The final plot!
(render-plot scatter-with-regression)

;; ## What Did We Do Manually?
;;
;; 1. Group by species manually → `species-data`
;; 2. Compute regressions manually → `species-regressions`  
;; 3. Map species → colors manually → `ggplot2-colors` indexed
;; 4. Build scatter series (one per species)
;; 5. Build line series (one regression per species)
;; 6. Combine all series into `:data` vector
;; 7. Set up axes/grid explicitly
;;
;; This is tedious and error-prone!

;; ## What the Grammar Should Automate
;;
;; Desired API:
;;
;; (plot penguins
;;   (blend
;;     (layer {:geom :point 
;;             :x :bill_length_mm 
;;             :y :bill_depth_mm 
;;             :color :species})
;;     (layer {:geom :smooth 
;;             :x :bill_length_mm 
;;             :y :bill_depth_mm 
;;             :color :species
;;             :method :lm})))
;;
;; What the grammar must infer/compute:
;;
;; 1. **Grouping**: `:color :species` means "group data by species"
;; 2. **Color mapping**: Assign ggplot2-colors to species automatically
;; 3. **Spreading**: Each layer spreads into 3 series (one per species)
;; 4. **Transform**: `:geom :smooth :method :lm` triggers linear regression
;; 5. **Domains**: Compute x/y domains from all data
;; 6. **Merging**: Both layers share same grouping, so align by species
;;
;; Key insight: The layer spec `{:x ... :y ... :color ...}` is declarative.
;; The pipeline must:
;; - Detect grouping variables (`:color`, `:facet`, etc.)
;; - Apply transforms (`:smooth` → regression computation)
;; - Spread each layer into geom.viz series
;; - Assign visual attributes (colors, shapes)
;; - Combine into final spec

;; ## Next: Design the Pipeline
;;
;; Stage 1: Layer specs → Internal representation
;; Stage 2: Algebraic combinators (blend, cross, nest)
;; Stage 3: Grouping & spreading (one series per group)
;; Stage 4: Statistical transforms (smooth, bin, density)
;; Stage 5: Rendering to geom.viz

;; # SPLOM Example: Building Incrementally

;; ## Step 1: Load Iris dataset

(def iris
  (tc/dataset "https://gist.githubusercontent.com/netj/8836201/raw/6f9306ad21398ea43cba4f7d537619d0e07d5ae3/iris.csv"
              {:key-fn keyword}))

(tc/head iris)

;; ## Step 2: Single histogram using fastmath

(defn make-histogram
  "Create histogram bins for a single variable using fastmath"
  [data var-key]
  (let [values (data var-key)
        hist (stats/histogram values :sturges)]
    ;; histogram returns {:bins-maps [{:min ... :max ... :count ...} ...]}
    hist))

;; Test it - commented out for now
;; (make-histogram iris :sepal_length)

;; ## Step 3: Render histogram with geom.viz

(defn render-histogram-simple
  "Render a single histogram as SVG using geom.viz bars"
  [data var-key]
  (let [hist (make-histogram data var-key)
        bins (:bins-maps hist)
        
        ;; Extract domain
        x-min (:min hist)
        x-max (:max hist)
        max-count (apply max (map :count bins))
        
        ;; Create bar data for geom.viz
        ;; Each bar: [x-center height]
        bar-points (mapv (fn [bin]
                          [(:mid bin) (:count bin)])
                        bins)
        
        ;; Build viz spec
        width 400
        height 300
        margin 50
        
        x-axis (viz/linear-axis
                 {:domain [x-min x-max]
                  :range [margin (- width margin)]
                  :major 5
                  :pos (- height margin)
                  :label-dist 15
                  :major-size 3
                  :minor-size 0
                  :attribs {:stroke "none"}})
        
        y-axis (viz/linear-axis
                 {:domain [0 max-count]
                  :range [(- height margin) margin]
                  :major 5
                  :pos margin
                  :label-dist 15
                  :label-style {:text-anchor "end"}
                  :major-size 3
                  :minor-size 0
                  :attribs {:stroke "none"}})
        
        ;; Create rectangles manually for histogram bars
        x-scale (fn [x] (+ margin (* (/ (- x x-min) (- x-max x-min))
                                     (- width (* 2 margin)))))
        y-scale (fn [y] (- height margin (* (/ y max-count)
                                            (- height (* 2 margin)))))
        
        bar-rects (mapv (fn [bin]
                         (let [bar-min (:min bin)
                               bar-max (:max bin)
                               bar-count (:count bin)
                               bar-x (x-scale bar-min)
                               bar-width (- (x-scale bar-max) bar-x)
                               bar-y (y-scale bar-count)
                               bar-height (- (- height margin) bar-y)]
                           (svg/rect [bar-x bar-y] bar-width bar-height
                                    {:fill (get-color 0)
                                     :stroke "#FFFFFF"
                                     :stroke-width 1})))
                       bins)
        
        bg-rect [:rect {:x margin :y margin 
                       :width (- width (* 2 margin)) 
                       :height (- height (* 2 margin))
                       :fill (:background theme)}]
        
        plot-spec {:x-axis x-axis
                   :y-axis y-axis
                   :grid {:attribs {:stroke (:grid theme) :stroke-width 1}}
                   :data []}]
    
    (-> (apply svg/svg
               {:width width :height height}
               bg-rect
               (concat (viz/svg-plot2d-cartesian plot-spec)
                      bar-rects))
        kind/hiccup)))

;; Test single histogram
(render-histogram-simple iris :sepal.length)

;; ## Step 4: Minimal 2x2 grid layout

(defn render-minimal-grid
  "Render a simple 2x2 grid to test layout"
  []
  (let [panel-size 120
        total-size (* 2 panel-size)
        
        ;; Create 4 background rectangles at different positions
        panels (for [row (range 2)
                    col (range 2)]
                 (let [x-offset (* col panel-size)
                       y-offset (* row panel-size)]
                   (svg/rect [x-offset y-offset]
                            panel-size panel-size
                            {:fill (:background theme)
                             :stroke (:grid theme)
                             :stroke-width 2})))]
    
    (-> (apply svg/svg
               {:width total-size :height total-size}
               panels)
        kind/hiccup)))

;; Test grid
(render-minimal-grid)

;; ## Step 5: 2x2 SPLOM with plots

;; First, extract just 2 variables for testing
(def splom-vars [:sepal.length :sepal.width])

;; Compute domains for both variables
(defn compute-domain [data var-key]
  (let [values (data var-key)]
    [(reduce min values) (reduce max values)]))

(def splom-domains
  (into {} (map (fn [v] [v (compute-domain iris v)]) splom-vars)))

splom-domains

;; ## Step 6: Panel rendering functions with offsets

(defn render-scatter-panel
  "Render scatter plot in a panel at given offset"
  [data var-x var-y x-offset y-offset panel-size]
  (let [x-domain (splom-domains var-x)
        y-domain (splom-domains var-y)
        
        ;; Extract points
        points (mapv vector (data var-x) (data var-y))
        
        ;; Panel margins (smaller for grid)
        margin 20
        plot-width (- panel-size (* 2 margin))
        plot-height (- panel-size (* 2 margin))
        
        ;; Axes at offset positions
        x-axis (viz/linear-axis
                 {:domain x-domain
                  :range [(+ x-offset margin) 
                         (+ x-offset panel-size (- margin))]
                  :major 2
                  :pos (+ y-offset panel-size (- margin))
                  :label-dist 12
                  :major-size 2
                  :minor-size 0
                  :attribs {:stroke "none"}})
        
        y-axis (viz/linear-axis
                 {:domain y-domain
                  :range [(+ y-offset panel-size (- margin)) 
                         (+ y-offset margin)]
                  :major 2
                  :pos (+ x-offset margin)
                  :label-dist 12
                  :label-style {:text-anchor "end"}
                  :major-size 2
                  :minor-size 0
                  :attribs {:stroke "none"}})
        
        plot-spec {:x-axis x-axis
                   :y-axis y-axis
                   :grid {:attribs {:stroke (:grid theme) :stroke-width 0.5}}
                   :data [{:values points
                          :attribs {:fill (get-color 0) :stroke "none"}
                          :layout viz/svg-scatter-plot}]}
        
        bg-rect (svg/rect [(+ x-offset margin) (+ y-offset margin)]
                         plot-width plot-height
                         {:fill (:background theme)})]
    
    {:background bg-rect
     :plot (viz/svg-plot2d-cartesian plot-spec)}))

;; Test scatter panel
(let [panel (render-scatter-panel iris :sepal.length :sepal.width 0 0 150)]
  (-> (apply svg/svg
             {:width 150 :height 150}
             (:background panel)
             (:plot panel))
      kind/hiccup))

;; ## Step 7: Histogram panel with offset

(defn render-histogram-panel
  "Render histogram in a panel at given offset"
  [data var-key x-offset y-offset panel-size]
  (let [hist (make-histogram data var-key)
        bins (:bins-maps hist)
        x-domain (splom-domains var-key)
        max-count (apply max (map :count bins))
        
        margin 20
        plot-width (- panel-size (* 2 margin))
        plot-height (- panel-size (* 2 margin))
        
        ;; Scale functions
        x-scale (fn [x] (+ x-offset margin 
                          (* (/ (- x (first x-domain)) 
                               (- (second x-domain) (first x-domain)))
                             plot-width)))
        y-scale (fn [y] (+ y-offset panel-size (- margin)
                          (- (* (/ y max-count) plot-height))))
        
        ;; Bar rectangles
        bar-rects (mapv (fn [bin]
                         (let [bar-x (x-scale (:min bin))
                               bar-width (- (x-scale (:max bin)) bar-x)
                               bar-y (y-scale (:count bin))
                               bar-height (- (+ y-offset panel-size (- margin)) bar-y)]
                           (svg/rect [bar-x bar-y] bar-width bar-height
                                    {:fill (get-color 0)
                                     :stroke "#FFFFFF"
                                     :stroke-width 0.5})))
                       bins)
        
        bg-rect (svg/rect [(+ x-offset margin) (+ y-offset margin)]
                         plot-width plot-height
                         {:fill (:background theme)})]
    
    {:background bg-rect
     :bars bar-rects}))

;; Test histogram panel
(let [panel (render-histogram-panel iris :sepal.length 0 0 150)]
  (-> (apply svg/svg
             {:width 150 :height 150}
             (:background panel)
             (:bars panel))
      kind/hiccup))

;; ## Step 8: Complete 2x2 SPLOM

(defn render-splom-2x2
  "Render a 2x2 SPLOM with histograms on diagonal"
  []
  (let [panel-size 150
        total-size (* 2 panel-size)
        vars splom-vars
        
        ;; Generate all panels
        panels (for [row (range 2)
                    col (range 2)]
                 (let [x-offset (* col panel-size)
                       y-offset (* row panel-size)
                       var-x (vars col)
                       var-y (vars row)]
                   (if (= row col)
                     ;; Diagonal: histogram
                     (render-histogram-panel iris var-x x-offset y-offset panel-size)
                     ;; Off-diagonal: scatter
                     (render-scatter-panel iris var-x var-y x-offset y-offset panel-size))))
        
        ;; Collect all elements
        backgrounds (map :background panels)
        plots (mapcat #(or (:plot %) []) panels)
        bars (mapcat #(or (:bars %) []) panels)]
    
    (-> (apply svg/svg
               {:width total-size :height total-size}
               (concat backgrounds plots bars))
        kind/hiccup)))

;; Render the 2x2 SPLOM!
(render-splom-2x2)

;; ## Step 9: Full 4x4 SPLOM with colors and regression

;; All 4 numeric variables
(def splom-vars-full [:sepal.length :sepal.width :petal.length :petal.width])

;; Compute domains for all variables
(def splom-domains-full
  (into {} (map (fn [v] [v (compute-domain iris v)]) splom-vars-full)))

splom-domains-full

;; Group iris by species
(def iris-by-species
  (tc/group-by iris :variety {:result-type :as-map}))

(keys iris-by-species)

;; ## Step 10: Scatter panel with colors and regression

(defn render-scatter-panel-colored
  "Render scatter plot with species colors and regression lines"
  [data var-x var-y x-offset y-offset panel-size]
  (let [x-domain (splom-domains-full var-x)
        y-domain (splom-domains-full var-y)
        
        ;; Group by species
        species-list ["Setosa" "Versicolor" "Virginica"]
        species-groups iris-by-species
        
        margin 20
        plot-width (- panel-size (* 2 margin))
        plot-height (- panel-size (* 2 margin))
        
        ;; Axes
        x-axis (viz/linear-axis
                 {:domain x-domain
                  :range [(+ x-offset margin) 
                         (+ x-offset panel-size (- margin))]
                  :major 2
                  :pos (+ y-offset panel-size (- margin))
                  :label-dist 12
                  :major-size 2
                  :minor-size 0
                  :attribs {:stroke "none"}})
        
        y-axis (viz/linear-axis
                 {:domain y-domain
                  :range [(+ y-offset panel-size (- margin)) 
                         (+ y-offset margin)]
                  :major 2
                  :pos (+ x-offset margin)
                  :label-dist 12
                  :label-style {:text-anchor "end"}
                  :major-size 2
                  :minor-size 0
                  :attribs {:stroke "none"}})
        
        ;; Create scatter series per species
        scatter-series
        (map-indexed
          (fn [idx species]
            (let [group-ds (species-groups species)
                  points (mapv vector (group-ds var-x) (group-ds var-y))]
              {:values points
               :attribs {:fill (get-color idx) :stroke "none"}
               :layout viz/svg-scatter-plot}))
          species-list)
        
        ;; Create regression line per species
        line-series
        (map-indexed
          (fn [idx species]
            (let [group-ds (species-groups species)
                  points (mapv vector (group-ds var-x) (group-ds var-y))
                  regression (linear-fit points)
                  
                  ;; Compute species x-domain
                  species-xs (map first points)
                  species-x-domain [(reduce min species-xs) (reduce max species-xs)]
                  
                  line-points (regression-line-points regression species-x-domain)]
              {:values line-points
               :attribs {:stroke (get-color idx)
                        :stroke-width 1.5
                        :fill "none"}
               :layout viz/svg-line-plot}))
          species-list)
        
        plot-spec {:x-axis x-axis
                   :y-axis y-axis
                   :grid {:attribs {:stroke (:grid theme) :stroke-width 0.5}}
                   :data (vec (concat scatter-series line-series))}
        
        bg-rect (svg/rect [(+ x-offset margin) (+ y-offset margin)]
                         plot-width plot-height
                         {:fill (:background theme)})]
    
    {:background bg-rect
     :plot (viz/svg-plot2d-cartesian plot-spec)}))

;; Test colored scatter with regression
(let [panel (render-scatter-panel-colored iris :sepal.length :sepal.width 0 0 200)]
  (-> (apply svg/svg
             {:width 200 :height 200}
             (:background panel)
             (:plot panel))
      kind/hiccup))

;; ## Step 11: Histogram panel with species colors (overlaid)

(defn render-histogram-panel-colored
  "Render overlaid histograms colored by species"
  [data var-key x-offset y-offset panel-size]
  (let [x-domain (splom-domains-full var-key)
        
        ;; Compute histogram for each species
        species-list ["Setosa" "Versicolor" "Virginica"]
        species-groups iris-by-species
        
        species-hists (map (fn [species]
                            (let [group-ds (species-groups species)]
                              {:species species
                               :hist (make-histogram group-ds var-key)}))
                          species-list)
        
        ;; Find global max count for y-scale
        max-count (apply max 
                        (mapcat (fn [{:keys [hist]}]
                                 (map :count (:bins-maps hist)))
                               species-hists))
        
        margin 20
        plot-width (- panel-size (* 2 margin))
        plot-height (- panel-size (* 2 margin))
        
        ;; Scale functions
        x-scale (fn [x] (+ x-offset margin 
                          (* (/ (- x (first x-domain)) 
                               (- (second x-domain) (first x-domain)))
                             plot-width)))
        y-scale (fn [y] (+ y-offset panel-size (- margin)
                          (- (* (/ y max-count) plot-height))))
        
        ;; Create bars for each species
        all-bars (mapcat
                   (fn [idx {:keys [species hist]}]
                     (let [bins (:bins-maps hist)]
                       (mapv (fn [bin]
                              (let [bar-x (x-scale (:min bin))
                                    bar-width (- (x-scale (:max bin)) bar-x)
                                    bar-y (y-scale (:count bin))
                                    bar-height (- (+ y-offset panel-size (- margin)) bar-y)]
                                (svg/rect [bar-x bar-y] bar-width bar-height
                                         {:fill (get-color idx)
                                          :stroke "#FFFFFF"
                                          :stroke-width 0.5
                                          :opacity 0.6})))
                            bins)))
                   (range)
                   species-hists)
        
        bg-rect (svg/rect [(+ x-offset margin) (+ y-offset margin)]
                         plot-width plot-height
                         {:fill (:background theme)})]
    
    {:background bg-rect
     :bars all-bars}))

;; Test colored histogram
(let [panel (render-histogram-panel-colored iris :sepal.length 0 0 200)]
  (-> (apply svg/svg
             {:width 200 :height 200}
             (:background panel)
             (:bars panel))
      kind/hiccup))

;; ## Step 12: Complete 4x4 SPLOM with colors and regression

(defn render-splom-full
  "Render complete 4x4 SPLOM with colored histograms and scatter+regression"
  []
  (let [panel-size 150
        total-size (* 4 panel-size)
        vars splom-vars-full
        
        ;; Generate all 16 panels
        panels (for [row (range 4)
                    col (range 4)]
                 (let [x-offset (* col panel-size)
                       y-offset (* row panel-size)
                       var-x (vars col)
                       var-y (vars row)]
                   (if (= row col)
                     ;; Diagonal: colored histogram
                     (render-histogram-panel-colored iris var-x x-offset y-offset panel-size)
                     ;; Off-diagonal: colored scatter + regression
                     (render-scatter-panel-colored iris var-x var-y x-offset y-offset panel-size))))
        
        ;; Collect all elements
        backgrounds (map :background panels)
        plots (mapcat #(or (:plot %) []) panels)
        bars (mapcat #(or (:bars %) []) panels)]
    
    (-> (apply svg/svg
               {:width total-size :height total-size}
               (concat backgrounds plots bars))
        kind/hiccup)))

;; Render the complete 4x4 SPLOM!
(render-splom-full)
