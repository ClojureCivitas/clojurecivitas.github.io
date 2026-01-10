(ns data-visualization.viz-plotting
  "Exploring [thi.ng/geom.viz](https://github.com/thi-ng/geom/blob/feature/no-org/org/examples/viz/demos.org) through examples.
  
  We build increasingly complex plots manually to understand what 
  a grammar layer should automate. The repetitive tedium motivates 
  the abstractions that follow."
  (:require
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.viz.core :as viz]
   [fastmath.ml.regression :as regr]
   [fastmath.stats :as stats]
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

;; ## Exploring geom.viz Through Examples
;;
;; Before building a Grammar of Graphics layer, we'll explore what 
;; thi.ng/geom.viz can do by manually constructing increasingly 
;; complex visualizations. We start simple—a [scatter plot](https://en.wikipedia.org/wiki/Scatter_plot)—then add 
;; colors, [regression lines](https://en.wikipedia.org/wiki/Linear_regression), and finally a complete 4×4 [SPLOM](https://en.wikipedia.org/wiki/Scatter_plot#Scatter_plot_matrices) grid.
;;
;; As the examples grow, the manual work becomes tedious and error-prone.
;; This motivates the grammar layer that will automate:
;; - Grouping data by categorical variables
;; - Assigning colors automatically  
;; - Computing statistical transforms per group
;; - Laying out grids of plots
;; - Combining multiple layers
;;
;; We use the [Iris dataset](https://en.wikipedia.org/wiki/Iris_flower_data_set) throughout to build familiarity.

;; ### Theme and Colors

(def theme
  "ggplot2-inspired visual theme:
  - Grey background (#EBEBEB)
  - White grid lines
  - No visible axis lines  
  - Very short tick marks (~3px)
  - Dark grey default color (#333333)
  - 10-color palette"
  {:background "#EBEBEB"
   :grid "#FFFFFF"
   :default-color "#333333"
   :colors ["#F8766D" "#619CFF" "#00BA38" "#F564E3" "#B79F00" 
            "#00BFC4" "#FF61C3" "#C77CFF" "#7CAE00" "#00B4F0"]})

(defn get-color
  "Get color from theme palette by index (cycles)"
  [index]
  (nth (:colors theme) (mod index (count (:colors theme)))))

;; Test the palette
(mapv get-color (range 3))

;; ### Data

(def iris
  (tc/dataset "https://gist.githubusercontent.com/netj/8836201/raw/6f9306ad21398ea43cba4f7d537619d0e07d5ae3/iris.csv"
              {:key-fn keyword}))

iris

;; ### Simple Scatter Plot

;; Basic scatter: sepal.length vs sepal.width, all points one color

(let [points (mapv vector (iris :sepal.length) (iris :sepal.width))
      
      ;; Compute domains
      xs (map first points)
      ys (map second points)
      x-domain [(apply min xs) (apply max xs)]
      y-domain [(apply min ys) (apply max ys)]
      
      ;; Layout
      width 500
      height 400
      margin 50
      
      ;; Axes (ggplot2 style: no axis lines, short ticks)
      x-axis (viz/linear-axis
              {:domain x-domain
               :range [margin (- width margin)]
               :major 1.0
               :pos (- height margin)
               :label-dist 15
               :major-size 3
               :minor-size 0
               :attribs {:stroke "none"}})
      
      y-axis (viz/linear-axis
              {:domain y-domain
               :range [(- height margin) margin]
               :major 0.5
               :pos margin
               :label-dist 15
               :label-style {:text-anchor "end"}
               :major-size 3
               :minor-size 0
               :attribs {:stroke "none"}})
      
      ;; Background rectangle
      bg-rect (svg/rect [margin margin]
                        (- width (* 2 margin))
                        (- height (* 2 margin))
                        {:fill (:background theme)})
      
      ;; Spec with single scatter series
      plot-spec {:x-axis x-axis
                 :y-axis y-axis
                 :grid {:attribs {:stroke (:grid theme) :stroke-width 1}}
                 :data [{:values points
                         :attribs {:fill (:default-color theme) :stroke "none"}
                         :layout viz/svg-scatter-plot}]}]
  
  (-> (apply svg/svg
             {:width width :height height}
             bg-rect
             (viz/svg-plot2d-cartesian plot-spec))
      kind/hiccup))

;; ### Scatter Colored by Species

;; Now we want each species in a different color. This requires:
;; 1. Grouping the data manually
;; 2. Creating one series per species
;; 3. Assigning colors manually

(let [;; Group by species
      species-groups (tc/group-by iris :variety {:result-type :as-map})
      species-list ["Setosa" "Versicolor" "Virginica"]
      
      ;; Extract points for each species
      species-points (into {}
                       (map (fn [[species ds]]
                              [species (mapv vector 
                                            (ds :sepal.length)
                                            (ds :sepal.width))])
                            species-groups))
      
      ;; Compute overall domain (across all species)
      all-points (mapcat identity (vals species-points))
      x-domain [(apply min (map first all-points))
                (apply max (map first all-points))]
      y-domain [(apply min (map second all-points))
                (apply max (map second all-points))]
      
      ;; Layout
      width 500
      height 400
      margin 50
      
      ;; Axes
      x-axis (viz/linear-axis
               {:domain x-domain
                :range [margin (- width margin)]
                :major 1.0
                :pos (- height margin)
                :label-dist 15
                :major-size 3
                :minor-size 0
                :attribs {:stroke "none"}})
      
      y-axis (viz/linear-axis
               {:domain y-domain
                :range [(- height margin) margin]
                :major 0.5
                :pos margin
                :label-dist 15
                :label-style {:text-anchor "end"}
                :major-size 3
                :minor-size 0
                :attribs {:stroke "none"}})
      
      ;; Create one scatter series per species (manually)
      scatter-series
      (map-indexed
        (fn [idx species]
          {:values (species-points species)
           :attribs {:fill (get-color idx) :stroke "none"}
           :layout viz/svg-scatter-plot})
        species-list)
      
      ;; Background
      bg-rect (svg/rect [margin margin]
                       (- width (* 2 margin))
                       (- height (* 2 margin))
                       {:fill (:background theme)})
      
      ;; Spec with three scatter series
      plot-spec {:x-axis x-axis
                 :y-axis y-axis
                 :grid {:attribs {:stroke (:grid theme) :stroke-width 1}}
                 :data (vec scatter-series)}]
  
  (-> (apply svg/svg
             {:width width :height height}
             bg-rect
             (viz/svg-plot2d-cartesian plot-spec))
      kind/hiccup))

;; Notice the manual work: grouping, extracting points per group, 
;; mapping species names to colors via indexed iteration.

;; ### Scatter + Regression Lines per Species

;; Add linear regression lines, one per species. Each line should:
;; - Span only that species' x-range
;; - Match the species color
;;
;; More manual steps:
;; 1. Compute regression for each species
;; 2. Generate line points spanning each species' domain
;; 3. Create line series matching scatter colors

(let [;; Group by species  
      species-groups (tc/group-by iris :variety {:result-type :as-map})
      species-list ["Setosa" "Versicolor" "Virginica"]
      
      ;; Extract points per species
      species-points (into {}
                       (map (fn [[species ds]]
                              [species (mapv vector 
                                            (ds :sepal.length)
                                            (ds :sepal.width))])
                            species-groups))
      
      ;; Compute overall domain
      all-points (mapcat identity (vals species-points))
      x-domain [(apply min (map first all-points))
                (apply max (map first all-points))]
      y-domain [(apply min (map second all-points))
                (apply max (map second all-points))]
      
      ;; Linear regression helper
      linear-fit (fn [points]
                   (let [x-vals (mapv first points)
                         y-vals (mapv second points)
                         xss (mapv vector x-vals)
                         model (regr/lm y-vals xss)
                         slope (first (:beta model))
                         intercept (:intercept model)]
                     {:slope slope :intercept intercept}))
      
      ;; Compute regression per species
      species-regressions
      (into {}
        (map (fn [[species points]]
               [species (linear-fit points)])
             species-points))
      
      ;; Generate line points for regression (spanning species x-range)
      regression-line-points
      (fn [{:keys [slope intercept]} [x-min x-max]]
        [[x-min (+ (* slope x-min) intercept)]
         [x-max (+ (* slope x-max) intercept)]])
      
      ;; Layout
      width 500
      height 400
      margin 50
      
      ;; Axes
      x-axis (viz/linear-axis
               {:domain x-domain
                :range [margin (- width margin)]
                :major 1.0
                :pos (- height margin)
                :label-dist 15
                :major-size 3
                :minor-size 0
                :attribs {:stroke "none"}})
      
      y-axis (viz/linear-axis
               {:domain y-domain
                :range [(- height margin) margin]
                :major 0.5
                :pos margin
                :label-dist 15
                :label-style {:text-anchor "end"}
                :major-size 3
                :minor-size 0
                :attribs {:stroke "none"}})
      
      ;; Scatter series per species
      scatter-series
      (map-indexed
        (fn [idx species]
          {:values (species-points species)
           :attribs {:fill (get-color idx) :stroke "none"}
           :layout viz/svg-scatter-plot})
        species-list)
      
      ;; Line series per species
      line-series
      (map-indexed
        (fn [idx species]
          (let [points (species-points species)
                regression (species-regressions species)
                species-xs (map first points)
                species-x-domain [(reduce min species-xs) 
                                 (reduce max species-xs)]
                line-points (regression-line-points regression species-x-domain)]
            {:values line-points
             :attribs {:stroke (get-color idx)
                      :stroke-width 2
                      :fill "none"}
             :layout viz/svg-line-plot}))
        species-list)
      
      ;; Background
      bg-rect (svg/rect [margin margin]
                       (- width (* 2 margin))
                       (- height (* 2 margin))
                       {:fill (:background theme)})
      
      ;; Combine scatter + line series
      plot-spec {:x-axis x-axis
                 :y-axis y-axis
                 :grid {:attribs {:stroke (:grid theme) :stroke-width 1}}
                 :data (vec (concat scatter-series line-series))}]
  
  (-> (apply svg/svg
             {:width width :height height}
             bg-rect
             (viz/svg-plot2d-cartesian plot-spec))
      kind/hiccup))

;; The tedium grows: computing transforms per group, coordinating 
;; colors across series, combining multiple geoms.

;; ### 2×2 SPLOM Grid

;; Now we want a grid of plots. Start with 2×2:
;; - Diagonal: [histograms](https://en.wikipedia.org/wiki/Histogram)
;; - Off-diagonal: scatter plots
;; - All panels share domains for alignment
;;
;; New manual tasks:
;; 1. Compute domains for each variable
;; 2. Position panels at different x/y offsets
;; 3. Build histogram bars as SVG rects (geom.viz doesn't have bars)
;; 4. Render axes relative to panel offsets

(let [;; Variables for SPLOM
      vars [:sepal.length :sepal.width]
      
      ;; Compute domain per variable
      domains (into {}
                (map (fn [v]
                       (let [vals (iris v)]
                         [v [(reduce min vals) (reduce max vals)]]))
                     vars))
      
      ;; Panel layout
      panel-size 200
      margin 30
      plot-width (- panel-size (* 2 margin))
      plot-height (- panel-size (* 2 margin))
      
      ;; Helper: histogram using fastmath
      make-histogram (fn [var-key]
                       (let [values (iris var-key)]
                         (stats/histogram values :sturges)))
      
      ;; Render scatter panel at offset
      render-scatter (fn [var-x var-y x-offset y-offset]
                       (let [points (mapv vector (iris var-x) (iris var-y))
                             x-domain (domains var-x)
                             y-domain (domains var-y)
                             
                             ;; Axes positioned at offset
                             x-axis (viz/linear-axis
                                      {:domain x-domain
                                       :range [(+ x-offset margin) 
                                              (+ x-offset panel-size (- margin))]
                                       :major 1.0
                                       :pos (+ y-offset panel-size (- margin))
                                       :label-dist 12
                                       :major-size 2
                                       :minor-size 0
                                       :attribs {:stroke "none"}})
                             
                             y-axis (viz/linear-axis
                                      {:domain y-domain
                                       :range [(+ y-offset panel-size (- margin))
                                              (+ y-offset margin)]
                                       :major 0.5
                                       :pos (+ x-offset margin)
                                       :label-dist 12
                                       :label-style {:text-anchor "end"}
                                       :major-size 2
                                       :minor-size 0
                                       :attribs {:stroke "none"}})
                             
                             bg-rect (svg/rect [(+ x-offset margin) (+ y-offset margin)]
                                              plot-width plot-height
                                              {:fill (:background theme)})
                             
                             plot-spec {:x-axis x-axis
                                       :y-axis y-axis
                                       :grid {:attribs {:stroke (:grid theme) :stroke-width 0.5}}
                                       :data [{:values points
                                              :attribs {:fill (:default-color theme) :stroke "none"}
                                              :layout viz/svg-scatter-plot}]}]
                         
                         {:background bg-rect
                          :plot (viz/svg-plot2d-cartesian plot-spec)}))
      
      ;; Render histogram panel at offset
      render-histogram (fn [var-key x-offset y-offset]
                         (let [hist (make-histogram var-key)
                               bins (:bins-maps hist)
                               x-domain (domains var-key)
                               max-count (apply max (map :count bins))
                               
                               ;; Scale functions
                               x-scale (fn [x] 
                                        (+ x-offset margin
                                           (* (/ (- x (first x-domain))
                                                (- (second x-domain) (first x-domain)))
                                              plot-width)))
                               y-scale (fn [y]
                                        (+ y-offset panel-size (- margin)
                                           (- (* (/ y max-count) plot-height))))
                               
                               ;; Manual bar rectangles
                               bar-rects (mapv (fn [bin]
                                                (let [bar-x (x-scale (:min bin))
                                                      bar-width (- (x-scale (:max bin)) bar-x)
                                                      bar-y (y-scale (:count bin))
                                                      bar-height (- (+ y-offset panel-size (- margin)) bar-y)]
                                                  (svg/rect [bar-x bar-y] bar-width bar-height
                                                           {:fill (:default-color theme)
                                                            :stroke "#FFFFFF"
                                                            :stroke-width 0.5})))
                                              bins)
                               
                               bg-rect (svg/rect [(+ x-offset margin) (+ y-offset margin)]
                                                plot-width plot-height
                                                {:fill (:background theme)})]
                           
                           {:background bg-rect
                            :bars bar-rects}))
      
      ;; Generate all 4 panels
      panels (for [row (range 2)
                   col (range 2)]
               (let [x-offset (* col panel-size)
                     y-offset (* row panel-size)
                     var-x (vars col)
                     var-y (vars row)]
                 (if (= row col)
                   ;; Diagonal: histogram
                   (render-histogram var-x x-offset y-offset)
                   ;; Off-diagonal: scatter
                   (render-scatter var-x var-y x-offset y-offset))))
      
      ;; Collect all SVG elements
      backgrounds (map :background panels)
      plots (mapcat #(or (:plot %) []) panels)
      bars (mapcat #(or (:bars %) []) panels)]
  
  (-> (apply svg/svg
             {:width (* 2 panel-size) :height (* 2 panel-size)}
             (concat backgrounds plots bars))
      kind/hiccup))

;; Grid layout is tedious: computing offsets, rendering bars manually, 
;; coordinating shared domains.

;; ### 4×4 SPLOM with Colors and Regression

;; Finally, the full complexity: 4×4 grid with:
;; - Colored scatter points (3 species)
;; - Regression lines per species  
;; - Overlaid colored histograms with transparency
;; - All panels share domains
;;
;; This compounds all the tedium:
;; - Grouping data by species (12 scatter series + 12 line series)
;; - Color coordination across all series
;; - Statistical transforms per group (regression, histograms)
;; - Grid positioning (16 panels)
;; - Manual bar rendering

(let [;; All 4 variables
      vars [:sepal.length :sepal.width :petal.length :petal.width]
      
      ;; Group by species
      species-groups (tc/group-by iris :variety {:result-type :as-map})
      species-list ["Setosa" "Versicolor" "Virginica"]
      
      ;; Compute domains per variable
      domains (into {}
                    (map (fn [v]
                           (let [vals (iris v)]
                             [v [(reduce min vals) (reduce max vals)]]))
                         vars))
      
      ;; Panel layout
      panel-size 150
      margin 20
      plot-width (- panel-size (* 2 margin))
      plot-height (- panel-size (* 2 margin))
      
      ;; Linear regression
      linear-fit (fn [points]
                   (let [x-vals (mapv first points)
                         y-vals (mapv second points)
                         xss (mapv vector x-vals)
                         model (regr/lm y-vals xss)
                         slope (first (:beta model))
                         intercept (:intercept model)]
                     {:slope slope :intercept intercept}))
      
      regression-line-points
      (fn [{:keys [slope intercept]} [x-min x-max]]
        [[x-min (+ (* slope x-min) intercept)]
         [x-max (+ (* slope x-max) intercept)]])
      
      ;; Histogram helper
      make-histogram (fn [var-key dataset]
                       (let [values (dataset var-key)]
                         (stats/histogram values :sturges)))
      
      ;; Render colored scatter + regression at offset
      render-scatter-colored (fn [var-x var-y x-offset y-offset]
                               (let [x-domain (domains var-x)
                                     y-domain (domains var-y)
                                     
                                     ;; Axes
                                     x-axis (viz/linear-axis
                                             {:domain x-domain
                                              :range [(+ x-offset margin)
                                                      (+ x-offset panel-size (- margin))]
                                              :major 2.0
                                              :pos (+ y-offset panel-size (- margin))
                                              :label-dist 10
                                              :major-size 2
                                              :minor-size 0
                                              :attribs {:stroke "none"}})
                                     
                                     y-axis (viz/linear-axis
                                             {:domain y-domain
                                              :range [(+ y-offset panel-size (- margin))
                                                      (+ y-offset margin)]
                                              :major 1.0
                                              :pos (+ x-offset margin)
                                              :label-dist 10
                                              :label-style {:text-anchor "end"}
                                              :major-size 2
                                              :minor-size 0
                                              :attribs {:stroke "none"}})
                                     
                                     ;; Scatter series per species
                                     scatter-series
                                     (map-indexed
                                      (fn [idx species]
                                        (let [ds (species-groups species)
                                              points (mapv vector (ds var-x) (ds var-y))]
                                          {:values points
                                           :attribs {:fill (get-color idx) :stroke "none"}
                                           :layout viz/svg-scatter-plot}))
                                      species-list)
                                     
                                     ;; Line series per species
                                     line-series
                                     (map-indexed
                                      (fn [idx species]
                                        (let [ds (species-groups species)
                                              points (mapv vector (ds var-x) (ds var-y))
                                              regression (linear-fit points)
                                              species-xs (map first points)
                                              species-x-domain [(reduce min species-xs)
                                                                (reduce max species-xs)]
                                              line-points (regression-line-points regression species-x-domain)]
                                          {:values line-points
                                           :attribs {:stroke (get-color idx)
                                                     :stroke-width 1.5
                                                     :fill "none"}
                                           :layout viz/svg-line-plot}))
                                      species-list)
                                     
                                     bg-rect (svg/rect [(+ x-offset margin) (+ y-offset margin)]
                                                       plot-width plot-height
                                                       {:fill (:background theme)})
                                     
                                     plot-spec {:x-axis x-axis
                                                :y-axis y-axis
                                                :grid {:attribs {:stroke (:grid theme) :stroke-width 0.5}}
                                                :data (vec (concat scatter-series line-series))}]
                                 
                                 {:background bg-rect
                                  :plot (viz/svg-plot2d-cartesian plot-spec)}))
      
      ;; Render colored overlaid histograms at offset
      render-histogram-colored (fn [var-key x-offset y-offset]
                                 (let [x-domain (domains var-key)
                                       
                                       ;; Compute histogram per species
                                       species-hists (map (fn [species]
                                                            {:species species
                                                             :hist (make-histogram var-key (species-groups species))})
                                                          species-list)
                                       
                                       ;; Global max count
                                       max-count (apply max
                                                        (mapcat (fn [{:keys [hist]}]
                                                                  (map :count (:bins-maps hist)))
                                                                species-hists))
                                       
                                       ;; Scale functions
                                       x-scale (fn [x]
                                                 (+ x-offset margin
                                                    (* (/ (- x (first x-domain))
                                                          (- (second x-domain) (first x-domain)))
                                                       plot-width)))
                                       y-scale (fn [y]
                                                 (+ y-offset panel-size (- margin)
                                                    (- (* (/ y max-count) plot-height))))
                                       
                                       ;; Bars for all species (overlaid with transparency)
                                       all-bars (mapcat
                                                 (fn [idx {:keys [hist]}]
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
      
      ;; Generate all 16 panels
      panels (for [row (range 4)
                   col (range 4)]
               (let [x-offset (* col panel-size)
                     y-offset (* row panel-size)
                     var-x (vars col)
                     var-y (vars row)]
                 (if (= row col)
                   ;; Diagonal: colored histogram
                   (render-histogram-colored var-x x-offset y-offset)
                   ;; Off-diagonal: colored scatter + regression
                   (render-scatter-colored var-x var-y x-offset y-offset))))
      
      ;; Collect all SVG elements
      backgrounds (map :background panels)
      plots (mapcat #(or (:plot %) []) panels)
      bars (mapcat #(or (:bars %) []) panels)]
  
  (-> (apply svg/svg
             {:width (* 4 panel-size) :height (* 4 panel-size)}
             (concat backgrounds plots bars))
      kind/hiccup))

;; By now the manual tedium is overwhelming:
;; - Grouping iris by species: manual
;; - Color mapping: manual index coordination
;; - Regression per group: manual iteration
;; - Histogram per group: manual iteration
;; - Grid positioning: manual offset calculation
;; - Combining series: manual concatenation
;;
;; A grammar layer should automate all of this. The user should write:
;;
;; ```clj
;; (plot iris
;;   (cross [:sepal.length :sepal.width :petal.length :petal.width]
;;     (blend
;;       (layer {:geom :point :color :variety})
;;       (layer {:geom :smooth :color :variety :method :lm}))))
;; ```
;;
;; And get the same result. That's what we'll build next.

;; ## Building the Grammar: Algebraic Foundation
;;
;; Now we implement the grammar layer that will automate all the manual work above.
;; We start with the algebraic foundation: operations that combine layer specifications.
;;
;; The key insight: layers are data structures. We can manipulate them with
;; pure functions before any rendering happens.

;; ### Data Representation
;;
;; A **layer** is a map with:
;; - `:=data` - the dataset
;; - `:=columns` - vector of column keywords (positional, will become :=x/:=y later)
;; - `:=plottype` - geometry type (:scatter, :line, :histogram)
;; - `:=color`, `:=facet` - grouping aesthetics
;;
;; A **spec** is a map with:
;; - `:=layers` - vector of layer maps
;; - Plot-level properties (`:=layout`, etc.)

;; ### Algebraic Operations

(defn merge-layers
  "Merge layer maps with :=columns concatenation.
  
  Simple rule: :=columns vectors concatenate, all other properties merge
  (rightmost wins).
  
  Example:
  (merge-layers {:=columns [:x]} {:=columns [:y]})
  => {:=columns [:x :y]}"
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

;; Test merge-layers
(merge-layers {:=columns [:a]} {:=columns [:b]})

(merge-layers {:=columns [:a] :=data "dataset1"}
              {:=columns [:b] :=data "dataset2"})

(defn layer
  "Create a single layer spec from dataset and columns.
  
  Positional semantics: columns are stored in :=columns vector,
  roles (:=x, :=y) will be assigned later by resolve-roles.
  
  Example:
  (layer iris :sepal.length :sepal.width)
  => {:=layers [{:=data iris :=columns [:sepal.length :sepal.width]}]}"
  [dataset & columns]
  {:=layers [(into {:=data dataset}
                   (when (seq columns)
                     {:=columns (vec columns)}))]})

;; Test layer
(layer iris :sepal.length :sepal.width)

(defn layers
  "Create multiple layer specs (one per column).
  
  This is syntactic sugar:
  (layers dataset [:a :b :c])
  
  is equivalent to:
  (blend (layer dataset :a) (layer dataset :b) (layer dataset :c))
  
  Useful for creating multiple separate univariate plots."
  [dataset cols]
  {:=layers (mapv (fn [col]
                    {:=data dataset
                     :=columns [col]})
                  cols)})

;; Test layers
(layers iris [:sepal.length :sepal.width :petal.length])

(defn blend
  "Concatenate layers from multiple specs.
  
  This is the fundamental + operation of the grammar.
  Layers are stacked for overlay visualization.
  
  Example:
  (blend (layer iris :sepal.length :sepal.width)
         (layer iris :petal.length :petal.width))
  
  Creates a spec with 2 layers that will be rendered together."
  [& specs]
  (let [all-layers (mapcat :=layers specs)
        plot-props (apply merge (map #(dissoc % :=layers) specs))]
    (assoc plot-props :=layers (vec all-layers))))

;; Test blend
(blend (layer iris :sepal.length :sepal.width)
       (layer iris :petal.length :petal.width))

(defn cross
  "Cartesian product of specs.
  
  Creates new layers by merging all combinations of input layers.
  This is the fundamental × operation of the grammar.
  
  Example:
  (cross (layers iris [:sepal.length :sepal.width])
         (layers iris [:petal.length :petal.width]))
  
  Creates 2×2 = 4 layers:
  - sepal.length × petal.length
  - sepal.length × petal.width
  - sepal.width × petal.length
  - sepal.width × petal.width
  
  This is the foundation for SPLOM (scatter plot matrices)."
  [& specs]
  (let [specs (remove nil? specs)]
    (if (= 1 (count specs))
      (first specs)
      (reduce
       (fn [acc spec]
         (cond
           ;; Both have layers -> cross product
           (and (:=layers acc) (:=layers spec))
           (let [product-layers (for [layer-a (:=layers acc)
                                      layer-b (:=layers spec)]
                                  (merge-layers layer-a layer-b))
                 plot-props (merge (dissoc acc :=layers) (dissoc spec :=layers))]
             (assoc plot-props :=layers (vec product-layers)))

           ;; Only one has layers -> merge
           (:=layers acc)
           (merge spec acc)

           (:=layers spec)
           (merge acc spec)

           ;; Neither has layers -> merge
           :else
           (merge acc spec)))
       specs))))

;; Test cross - should create 4 layers
(cross (layers iris [:sepal.length :sepal.width])
       (layers iris [:petal.length :petal.width]))

;; Inspect the :=columns of each layer
(map :=columns
     (:=layers
      (cross (layers iris [:sepal.length :sepal.width])
             (layers iris [:petal.length :petal.width]))))

;; ### Testing Algebra
;;
;; The algebra is **compositional**: we can build complex specs from simple parts.

;; Simple layer
(layer iris :sepal.length :sepal.width)

;; Blend two layers
(blend (layer iris :sepal.length :sepal.width)
       (layer iris :petal.length :petal.width))

;; Cross product for SPLOM
(let [spec (cross (layers iris [:sepal.length :sepal.width])
                  (layers iris [:sepal.length :sepal.width]))]
  (println "Created" (count (:=layers spec)) "layers")
  (map :=columns (:=layers spec)))

;; The key insight: we've built the structure of a 2×2 SPLOM
;; without any rendering code. The algebra creates the right
;; layer combinations. Rendering will come later.
