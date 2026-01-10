^{:kindly/hide-code true
  :clay {:title "Brushable SPLOMs using thi.ng/geom.viz and D3"
         :quarto {:type :post
                  :author [:daslu]
                  :date "2026-01-10"
                  :description "Building interactive scatter plot matrices from scratch with geom.viz, exploring manual construction before automation"
                  :category :data-visualization
                  :tags [:datavis :geomviz :splom :d3 :interaction]
                  :keywords [:datavis :splom :scatterplot-matrix :geomviz :d3]
                  :toc true
                  :toc-depth 3
                  :draft true}}}
(ns data-visualization.brushable-splom
  "Building brushable SPLOMs (scatter plot matrices) using thi.ng/geom.viz.
  
  We construct everything manually to understand what's involved, then add
  D3-based brushing interaction. This exploration motivates the grammar layer
  that will automate these patterns."
  (:require
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.kind :as kind]
   [thi.ng.geom.viz.core :as viz]
   [fastmath.ml.regression :as regr]
   [fastmath.stats :as stats]
   [clojure.string :as str]
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
;; variables in a grid layout. It's invaluable for exploratory data analysis,
;; revealing correlations, clusters, and outliers at a glance.
;;
;; In this post, we'll build a complete interactive SPLOM from scratch using
;; [thi.ng/geom.viz](https://github.com/thi-ng/geom/blob/feature/no-org/org/examples/viz/demos.org),
;; a composable SVG visualization library. By constructing everything manually,
;; we'll understand:
;;
;; - How grid layouts work (positioning panels at offsets)
;; - How to render multiple geoms (scatter, regression, histograms)
;; - How to handle grouping by categorical variables
;; - How to add D3-based brushing interaction
;;
;; The manual tedium we encounter will motivate the Grammar of Graphics layer
;; that automates these patterns.
;;
;; We'll use the classic [Iris dataset](https://en.wikipedia.org/wiki/Iris_flower_data_set)
;; throughout, exploring relationships between four measurements across three species.

;; ## Theme and Colors

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

;; ## Helper Functions
;;
;; These helpers eliminate duplication across panel rendering functions.

;; ### Configuration Constants

(def axis-config
  "Common axis configuration"
  {:x-major 1.0
   :y-major 0.5
   :label-dist 12
   :major-size 2
   :minor-size 0})

(def histogram-config
  "Histogram rendering configuration"
  {:bar-opacity 0.6
   :bar-stroke "#FFFFFF"
   :bar-stroke-width 0.5})

;; ### Axis Builders

(defn make-x-axis
  "Create a horizontal axis with standard styling"
  [domain range y-pos]
  (viz/linear-axis
   {:domain domain
    :range range
    :major (:x-major axis-config)
    :pos y-pos
    :label-dist (:label-dist axis-config)
    :major-size (:major-size axis-config)
    :minor-size (:minor-size axis-config)
    :attribs {:stroke "none"}}))

(defn make-y-axis
  "Create a vertical axis with standard styling"
  [domain range x-pos]
  (viz/linear-axis
   {:domain domain
    :range range
    :major (:y-major axis-config)
    :pos x-pos
    :label-dist (:label-dist axis-config)
    :label-style {:text-anchor "end"}
    :major-size (:major-size axis-config)
    :minor-size (:minor-size axis-config)
    :attribs {:stroke "none"}}))

;; ### Scale Builders

(defn make-linear-scale
  "Create a linear scale function mapping domain to range"
  [domain [range-min range-max]]
  (let [[d-min d-max] domain
        d-span (- d-max d-min)
        r-span (- range-max range-min)]
    (fn [value]
      (+ range-min (* (/ (- value d-min) d-span) r-span)))))

;; ### Background and Series Builders

(defn make-background
  "Create a background rectangle for a panel"
  [x-offset y-offset panel-size margin]
  (let [plot-width (- panel-size (* 2 margin))
        plot-height (- panel-size (* 2 margin))]
    (svg/rect [(+ x-offset margin) (+ y-offset margin)]
              plot-width plot-height
              {:fill (:background theme)})))

(defn make-scatter-series
  "Create a scatter plot series with optional row indices for brushing"
  ([points color]
   (make-scatter-series points color nil))

  ([points color row-indices]
   (if row-indices
     ;; With row indices: use custom layout function
     {:values (mapv vector points row-indices)
      :attribs {:fill color :stroke "none"}
      :layout (fn [v-spec d-spec]
                ;; Custom scatter plot that adds data-row-idx attributes
                ;; Use scale functions from axes for coordinate transformation
                (let [x-scale (:scale (:x-axis v-spec))
                      y-scale (:scale (:y-axis v-spec))]
                  (mapv (fn [[[x y] row-idx]]
                          (let [px (x-scale x)
                                py (y-scale y)]
                            [:circle {:cx px
                                      :cy py
                                      :r 2
                                      :fill (get-in d-spec [:attribs :fill])
                                      :stroke (get-in d-spec [:attribs :stroke])
                                      :data-row-idx row-idx
                                      :class "splom-point"}]))
                        (:values d-spec))))}
     ;; Without row indices: use standard viz function
     {:values points
      :attribs {:fill color :stroke "none"}
      :layout viz/svg-scatter-plot})))

(defn make-line-series
  "Create a line plot series"
  [points color]
  {:values points
   :attribs {:stroke color :stroke-width 1.5 :fill "none"}
   :layout viz/svg-line-plot})

;; ### Regression Helpers

(defn linear-fit
  "Compute linear regression for a set of [x y] points"
  [points]
  (let [x-vals (mapv first points)
        y-vals (mapv second points)
        xss (mapv vector x-vals)
        model (regr/lm y-vals xss)
        slope (first (:beta model))
        intercept (:intercept model)]
    {:slope slope :intercept intercept}))

(defn regression-line-points
  "Generate endpoint coordinates for a regression line"
  [{:keys [slope intercept]} [x-min x-max]]
  [[x-min (+ (* slope x-min) intercept)]
   [x-max (+ (* slope x-max) intercept)]])

;; ### D3 Brushing Support

(defn hiccup->svg-string
  "Convert hiccup SVG to XML string, avoiding Hiccup library compatibility issues.
  
  Handles all thi.ng/geom.viz elements including those not supported by hiccup.core"
  [hiccup]
  (cond
    ;; Vector: could be SVG element [:tag {...}] or collection of elements [[:tag1 ...] [:tag2 ...]]
    (vector? hiccup)
    (if (and (seq hiccup) (keyword? (first hiccup)))
      ;; This is a hiccup element [:tag {:attr val} ...children]
      (let [[tag maybe-attrs & rest-children] hiccup
            [attrs children] (if (map? maybe-attrs)
                               [maybe-attrs rest-children]
                               [{} (cons maybe-attrs rest-children)])
            tag-name (name tag)
            attrs-str (when (seq attrs)
                        (str/join " "
                                  (map (fn [[k v]]
                                         (let [k-str (if (keyword? k) (name k) (str k))
                                               ;; Special handling for polyline points attribute
                                               v-str (if (and (= k-str "points") (coll? v))
                                                       (str/join " " (map (fn [[x y]] (str x "," y)) v))
                                                       (str v))]
                                           (str k-str "=\"" v-str "\"")))
                                       attrs)))]
        (if (seq children)
          (str "<" tag-name (when attrs-str (str " " attrs-str)) ">"
               (str/join (map hiccup->svg-string children))
               "</" tag-name ">")
          (str "<" tag-name (when attrs-str (str " " attrs-str)) "/>")))
      ;; This is a collection of hiccup elements, process each one
      (str/join (map hiccup->svg-string hiccup)))

    ;; Sequence: multiple elements
    (seq? hiccup)
    (str/join (map hiccup->svg-string hiccup))

    ;; Keyword: skip (these are markers/metadata in hiccup trees)
    (keyword? hiccup)
    ""

    ;; Nil: skip
    (nil? hiccup)
    ""

    ;; Everything else: convert to string
    :else
    (str hiccup)))

(defn make-brushing-script
  "Generate D3.js code for cross-panel brushing in SPLOM.
  
  Parameters:
  - n-panels: number of panels in grid (e.g., 2 for 2×2, 4 for 4×4)
  - panel-size: size of each panel in pixels
  - margin: margin around each panel
  
  The script:
  1. Adds a brush overlay to each scatter panel (off-diagonal)
  2. On brush, highlights matching points across all panels
  3. On brush end (empty selection), clears highlighting"
  [n-panels panel-size margin]
  (let [plot-width (- panel-size (* 2 margin))
        plot-height (- panel-size (* 2 margin))]
    (str
     "
// D3 Brushing for SPLOM - wait for DOM to be ready
(function() {
  // Use setTimeout to ensure SVG is fully parsed
  setTimeout(function() {
    const svg = d3.select('svg');
    
    // Check if SVG was found
    if (svg.empty()) {
      console.error('SPLOM brushing: SVG element not found');
      return;
    }
    
    const nPanels = " n-panels ";
    const panelSize = " panel-size ";
    const margin = " margin ";
    const plotWidth = " plot-width ";
    const plotHeight = " plot-height ";
    
    // Create brush for each off-diagonal panel
    for (let row = 0; row < nPanels; row++) {
      for (let col = 0; col < nPanels; col++) {
        if (row === col) continue; // Skip diagonal (histograms)
        
        const xOffset = col * panelSize;
        const yOffset = row * panelSize;
        
        // Create brush
        const brush = d3.brush()
          .extent([
            [xOffset + margin, yOffset + margin],
            [xOffset + panelSize - margin, yOffset + panelSize - margin]
          ])
          .on('brush', brushed)
          .on('end', brushEnded);
        
        // Add brush overlay group
        svg.append('g')
          .attr('class', 'brush')
          .attr('data-panel-row', row)
          .attr('data-panel-col', col)
          .call(brush);
      }
    }
    
    // Brush event handler
    function brushed(event) {
      const selection = event.selection;
      if (!selection) return;
      
      const [[x0, y0], [x1, y1]] = selection;
      
      // Find all points within brush bounds
      const brushedIndices = new Set();
      svg.selectAll('.splom-point').each(function() {
        const point = d3.select(this);
        const cx = +point.attr('cx');
        const cy = +point.attr('cy');
        
        if (cx >= x0 && cx <= x1 && cy >= y0 && cy <= y1) {
          const rowIdx = point.attr('data-row-idx');
          if (rowIdx) brushedIndices.add(rowIdx);
        }
      });
      
      // Highlight matching points across all panels
      svg.selectAll('.splom-point').each(function() {
        const point = d3.select(this);
        const rowIdx = point.attr('data-row-idx');
        
        if (brushedIndices.has(rowIdx)) {
          point.attr('r', 3)
               .attr('stroke', '#000')
               .attr('stroke-width', 1)
               .style('opacity', 1.0);
        } else {
          point.attr('r', 2)
               .attr('stroke', 'none')
               .style('opacity', 0.3);
        }
      });
    }
    
    // Clear highlighting when brush is removed
    function brushEnded(event) {
      if (!event.selection) {
        svg.selectAll('.splom-point')
          .attr('r', 2)
          .attr('stroke', 'none')
          .style('opacity', 1.0);
      }
    }
  }, 100); // Wait 100ms for DOM to be ready
})();
")))

(defn wrap-with-brushing
  "Wrap SVG hiccup with D3 script for brushing interaction.
  
  Renders SVG to string (to avoid Hiccup compatibility issues) and wraps with:
  - The SVG as HTML string
  - D3.js library script tag
  - Generated brushing script
  
  Returns HTML string suitable for kind/html"
  [svg-hiccup n-panels panel-size margin]
  (let [svg-id (str "splom-" (java.util.UUID/randomUUID))
        svg-string (hiccup->svg-string svg-hiccup)
        ;; Add ID to SVG element for scoped D3 selection
        svg-with-id (.replaceFirst svg-string "<svg " (str "<svg id=\"" svg-id "\" "))
        brushing-script (str
                         "// D3 Brushing for SPLOM - scoped to specific SVG\n"
                         "(function() {\n"
                         "  setTimeout(function() {\n"
                         "    const svg = d3.select('#" svg-id "');\n"
                         "    \n"
                         "    if (svg.empty()) {\n"
                         "      console.error('SPLOM brushing: SVG #" svg-id " not found');\n"
                         "      return;\n"
                         "    }\n"
                         "    \n"
                         "    const nPanels = " n-panels ";\n"
                         "    const panelSize = " panel-size ";\n"
                         "    const margin = " margin ";\n"
                         "    \n"
                         "    // Create brush for each off-diagonal panel\n"
                         "    for (let row = 0; row < nPanels; row++) {\n"
                         "      for (let col = 0; col < nPanels; col++) {\n"
                         "        if (row === col) continue;\n"
                         "        \n"
                         "        const xOffset = col * panelSize;\n"
                         "        const yOffset = row * panelSize;\n"
                         "        \n"
                         "        const brush = d3.brush()\n"
                         "          .extent([\n"
                         "            [xOffset + margin, yOffset + margin],\n"
                         "            [xOffset + panelSize - margin, yOffset + panelSize - margin]\n"
                         "          ])\n"
                         "          .on('brush', brushed)\n"
                         "          .on('end', brushEnded);\n"
                         "        \n"
                         "        svg.append('g')\n"
                         "          .attr('class', 'brush')\n"
                         "          .call(brush);\n"
                         "      }\n"
                         "    }\n"
                         "    \n"
                         "    function brushed(event) {\n"
                         "      const selection = event.selection;\n"
                         "      if (!selection) return;\n"
                         "      \n"
                         "      const [[x0, y0], [x1, y1]] = selection;\n"
                         "      const brushedIndices = new Set();\n"
                         "      \n"
                         "      svg.selectAll('.splom-point').each(function() {\n"
                         "        const point = d3.select(this);\n"
                         "        const cx = +point.attr('cx');\n"
                         "        const cy = +point.attr('cy');\n"
                         "        \n"
                         "        if (cx >= x0 && cx <= x1 && cy >= y0 && cy <= y1) {\n"
                         "          const rowIdx = point.attr('data-row-idx');\n"
                         "          if (rowIdx) brushedIndices.add(rowIdx);\n"
                         "        }\n"
                         "      });\n"
                         "      \n"
                         "      svg.selectAll('.splom-point').each(function() {\n"
                         "        const point = d3.select(this);\n"
                         "        const rowIdx = point.attr('data-row-idx');\n"
                         "        \n"
                         "        if (brushedIndices.has(rowIdx)) {\n"
                         "          point.attr('r', 3)\n"
                         "               .attr('stroke', '#000')\n"
                         "               .attr('stroke-width', 1.5)\n"
                         "               .style('opacity', 1.0);\n"
                         "        } else {\n"
                         "          point.attr('r', 2)\n"
                         "               .attr('stroke', 'none')\n"
                         "               .style('opacity', 0.3);\n"
                         "        }\n"
                         "      });\n"
                         "    }\n"
                         "    \n"
                         "    function brushEnded(event) {\n"
                         "      if (!event.selection) {\n"
                         "        svg.selectAll('.splom-point')\n"
                         "          .attr('r', 2)\n"
                         "          .attr('stroke', 'none')\n"
                         "          .style('opacity', 1.0);\n"
                         "      }\n"
                         "    }\n"
                         "  }, 100);\n"
                         "})();\n")]
    (str "<div style=\"position: relative;\">"
         svg-with-id
         "<script src=\"https://d3js.org/d3.v7.min.js\"></script>"
         "<script type=\"text/javascript\">"
         brushing-script
         "</script>"
         "</div>")))

;; Test the palette - colors for the three Iris species
(mapv get-color (range 3))

;; ## Data

(def iris
  (-> (tc/dataset "https://gist.githubusercontent.com/netj/8836201/raw/6f9306ad21398ea43cba4f7d537619d0e07d5ae3/iris.csv"
                  {:key-fn keyword})
      ;; Add row index for brushing interaction
      (tc/add-column :row-idx (range 150))))

iris

;; Group iris by species for colored plots
(def species-groups (tc/group-by iris :variety {:result-type :as-map}))
(def species-list ["Setosa" "Versicolor" "Virginica"])

species-groups

;; ## Building a SPLOM Step by Step
;;
;; We'll build incrementally:
;; 1. Simple 2×2 SPLOM (diagonal histograms, off-diagonal scatter)
;; 2. Add species colors and regression lines
;; 3. Scale up to full 4×4 SPLOM
;; 4. Add D3 brushing interaction

;; ## Step 1: 2×2 SPLOM Foundation
;;
;; Start with just two variables to establish the grid layout pattern.
;; - Diagonal panels: [histograms](https://en.wikipedia.org/wiki/Histogram)
;; - Off-diagonal panels: scatter plots
;; - All panels share domains for visual alignment

;; Variables for our 2×2 grid
(def vars-2x2 [:sepal.length :sepal.width])

;; Compute domain for each variable (needed for consistent scales)
(defn compute-domain [data var-key]
  (let [vals (data var-key)]
    [(reduce min vals) (reduce max vals)]))

(def domains-2x2
  (into {} (map (fn [v] [v (compute-domain iris v)]) vars-2x2)))

domains-2x2

;; Panel layout constants
(def panel-size 200)
(def margin 30)
(def plot-width (- panel-size (* 2 margin)))
(def plot-height (- panel-size (* 2 margin)))

;; Helper: compute histogram bins
(defn make-histogram [data var-key]
  (let [values (data var-key)]
    (stats/histogram values :sturges)))

;; Helper: render scatter panel at given grid position
(defn render-scatter-panel
  "Render scatter panel, optionally with species grouping and regression"
  ([data var-x var-y domains x-offset y-offset panel-size margin]
   (render-scatter-panel data nil var-x var-y domains x-offset y-offset panel-size margin))

  ([data species-groups var-x var-y domains x-offset y-offset panel-size margin]
   (let [x-domain (domains var-x)
         y-domain (domains var-y)

         ;; Axes using helpers
         x-axis (make-x-axis x-domain
                             [(+ x-offset margin)
                              (+ x-offset panel-size (- margin))]
                             (+ y-offset panel-size (- margin)))

         y-axis (make-y-axis y-domain
                             [(+ y-offset panel-size (- margin))
                              (+ y-offset margin)]
                             (+ x-offset margin))

         ;; Generate series based on grouping
         scatter-series
         (if species-groups
           ;; Grouped by species - extract points and row indices
           (map-indexed
            (fn [idx species]
              (let [ds (species-groups species)
                    points (mapv vector (ds var-x) (ds var-y))
                    row-indices (vec (ds :row-idx))]
                (make-scatter-series points (get-color idx) row-indices)))
            species-list)
           ;; Single ungrouped series
           (let [points (mapv vector (data var-x) (data var-y))
                 row-indices (vec (data :row-idx))]
             [(make-scatter-series points (:default-color theme) row-indices)]))

         ;; Regression lines (only if grouped)
         line-series
         (when species-groups
           (map-indexed
            (fn [idx species]
              (let [ds (species-groups species)
                    points (mapv vector (ds var-x) (ds var-y))
                    regression (linear-fit points)
                    species-xs (map first points)
                    species-x-domain [(reduce min species-xs)
                                      (reduce max species-xs)]
                    line-points (regression-line-points regression species-x-domain)]
                (make-line-series line-points (get-color idx))))
            species-list))

         plot-spec {:x-axis x-axis
                    :y-axis y-axis
                    :grid {:attribs {:stroke (:grid theme) :stroke-width 0.5}}
                    :data (vec (concat scatter-series line-series))}]

     {:background (make-background x-offset y-offset panel-size margin)
      :plot (viz/svg-plot2d-cartesian plot-spec)})))

;; Helper: render histogram panel at given grid position
(defn render-histogram-panel
  "Render histogram panel, optionally with species grouping and overlay"
  ([data var-key domains x-offset y-offset panel-size margin]
   (render-histogram-panel data nil var-key domains x-offset y-offset panel-size margin))

  ([data species-groups var-key domains x-offset y-offset panel-size margin]
   (let [x-domain (domains var-key)
         plot-width (- panel-size (* 2 margin))
         plot-height (- panel-size (* 2 margin))

         ;; Compute histograms (per species if grouped)
         hists (if species-groups
                 (map (fn [species]
                        {:species species
                         :hist (make-histogram (species-groups species) var-key)})
                      species-list)
                 [{:hist (make-histogram data var-key)}])

         ;; Find global max count for y-scale
         max-count (apply max
                          (mapcat (fn [{:keys [hist]}]
                                    (map :count (:bins-maps hist)))
                                  hists))

         ;; Scale functions using helper
         x-scale (make-linear-scale x-domain
                                    [(+ x-offset margin)
                                     (+ x-offset panel-size (- margin))])
         y-scale (make-linear-scale [0 max-count]
                                    [(+ y-offset panel-size (- margin))
                                     (+ y-offset margin)])

         ;; Generate bars for all histograms
         all-bars (mapcat
                   (fn [idx {:keys [hist]}]
                     (let [bins (:bins-maps hist)
                           color (if species-groups
                                   (get-color idx)
                                   (:default-color theme))
                           opacity (if species-groups
                                     (:bar-opacity histogram-config)
                                     1.0)]
                       (mapv (fn [bin]
                               (let [bar-x (x-scale (:min bin))
                                     bar-width (- (x-scale (:max bin)) bar-x)
                                     bar-y (y-scale (:count bin))
                                     bar-height (- (+ y-offset panel-size (- margin)) bar-y)]
                                 (svg/rect [bar-x bar-y] bar-width bar-height
                                           {:fill color
                                            :stroke (:bar-stroke histogram-config)
                                            :stroke-width (:bar-stroke-width histogram-config)
                                            :opacity opacity})))
                             bins)))
                   (range)
                   hists)]

     {:background (make-background x-offset y-offset panel-size margin)
      :bars all-bars})))

;; Render the complete 2×2 SPLOM
(let [total-size (* 2 panel-size)

      ;; Generate all 4 panels
      panels (for [row (range 2)
                   col (range 2)]
               (let [x-offset (* col panel-size)
                     y-offset (* row panel-size)
                     var-x (vars-2x2 col)
                     var-y (vars-2x2 row)]
                 (if (= row col)
                   ;; Diagonal: histogram
                   (render-histogram-panel iris var-x domains-2x2 x-offset y-offset panel-size margin)
                   ;; Off-diagonal: scatter
                   (render-scatter-panel iris var-x var-y domains-2x2 x-offset y-offset panel-size margin))))

      ;; Collect all SVG elements
      backgrounds (map :background panels)
      plots (mapcat #(or (:plot %) []) panels)
      bars (mapcat #(or (:bars %) []) panels)]

  (->> (apply svg/svg
              {:width total-size :height total-size}
              (concat backgrounds plots bars))
       hiccup->svg-string kind/html))

;; Success! We have a 2×2 grid with:
;; - Histograms on the diagonal (sepal.length, sepal.width)
;; - Scatter plots off-diagonal showing correlations
;; - Shared domains ensuring visual alignment

;; ## Step 2: Adding Colors and Regression
;;
;; Now let's add species colors and [linear regression](https://en.wikipedia.org/wiki/Linear_regression) 
;; lines to reveal patterns within each species.

;; Render 2×2 SPLOM with colors and regression
(let [total-size (* 2 panel-size)

      ;; Generate all 4 panels
      panels (for [row (range 2)
                   col (range 2)]
               (let [x-offset (* col panel-size)
                     y-offset (* row panel-size)
                     var-x (vars-2x2 col)
                     var-y (vars-2x2 row)]
                 (if (= row col)
                   ;; Diagonal: colored histogram
                   (render-histogram-panel iris species-groups var-x domains-2x2 x-offset y-offset panel-size margin)
                   ;; Off-diagonal: colored scatter + regression
                   (render-scatter-panel iris species-groups var-x var-y domains-2x2 x-offset y-offset panel-size margin))))

      ;; Collect all SVG elements
      backgrounds (map :background panels)
      plots (mapcat #(or (:plot %) []) panels)
      bars (mapcat #(or (:bars %) []) panels)]

  (->> (apply svg/svg
              {:width total-size :height total-size}
              (concat backgrounds plots bars))
       hiccup->svg-string kind/html))

;; Beautiful! Now we can see:
;; - Species-specific distributions in overlaid histograms (diagonal)
;; - Distinct clusters and regression lines per species (off-diagonal)
;; - Setosa (red) is clearly separated; Versicolor/Virginica overlap

;; ### Adding Interactive Brushing
;;
;; Now let's make this interactive! Wrap the SPLOM with D3 brushing to enable
;; cross-panel highlighting. Brush any scatter panel to see matching points
;; highlighted across all panels.

(let [total-size (* 2 panel-size)

      ;; Generate all 4 panels
      panels (for [row (range 2)
                   col (range 2)]
               (let [x-offset (* col panel-size)
                     y-offset (* row panel-size)
                     var-x (vars-2x2 col)
                     var-y (vars-2x2 row)]
                 (if (= row col)
                   ;; Diagonal: colored histogram
                   (render-histogram-panel iris species-groups var-x domains-2x2 x-offset y-offset panel-size margin)
                   ;; Off-diagonal: colored scatter + regression
                   (render-scatter-panel iris species-groups var-x var-y domains-2x2 x-offset y-offset panel-size margin))))

      ;; Collect all SVG elements
      backgrounds (map :background panels)
      plots (mapcat #(or (:plot %) []) panels)
      bars (mapcat #(or (:bars %) []) panels)

      ;; Build SVG
      svg-elem (apply svg/svg
                      {:width total-size :height total-size}
                      (concat backgrounds plots bars))]

  ;; Wrap with D3 brushing
  (-> svg-elem
      (wrap-with-brushing 2 panel-size margin)
      kind/html))

;; Try it! Click and drag in any scatter panel to brush. The selected points
;; will be highlighted across all panels. Release to clear the selection.

;; ## Step 3: Complete 4×4 SPLOM
;;
;; Scale up to all four iris measurements: sepal length/width, petal length/width.

(def vars-4x4 [:sepal.length :sepal.width :petal.length :petal.width])

(def domains-4x4
  (into {} (map (fn [v] [v (compute-domain iris v)]) vars-4x4)))

domains-4x4

;; Smaller panels for 4×4 grid
(def panel-size-4x4 150)
(def margin-4x4 20)
(def plot-width-4x4 (- panel-size-4x4 (* 2 margin-4x4)))
(def plot-height-4x4 (- panel-size-4x4 (* 2 margin-4x4)))

;; Render complete 4×4 SPLOM with colors and regression
(let [total-size (* 4 panel-size-4x4)

      ;; Generate all 16 panels
      panels (for [row (range 4)
                   col (range 4)]
               (let [x-offset (* col panel-size-4x4)
                     y-offset (* row panel-size-4x4)
                     var-x (vars-4x4 col)
                     var-y (vars-4x4 row)]
                 (if (= row col)
                   ;; Diagonal: colored histogram (adjust for smaller panels)
                   (let [;; Use 4x4 dimensions
                         ps panel-size-4x4
                         m margin-4x4]
                     ;; Call with explicit panel dimensions
                     (render-histogram-panel iris species-groups var-x domains-4x4 x-offset y-offset ps m))
                   ;; Off-diagonal: colored scatter + regression
                   (let [;; Use 4x4 dimensions
                         ps panel-size-4x4
                         m margin-4x4]
                     ;; Call with explicit panel dimensions
                     (render-scatter-panel iris species-groups var-x var-y domains-4x4 x-offset y-offset ps m)))))

      ;; Collect all SVG elements
      backgrounds (map :background panels)
      plots (mapcat #(or (:plot %) []) panels)
      bars (mapcat #(or (:bars %) []) panels)]

  (->> (apply svg/svg
              {:width total-size :height total-size}
              (concat backgrounds plots bars))
       hiccup->svg-string kind/html))

;; Spectacular! The complete 4×4 SPLOM reveals:
;; - Strong correlation between petal length and width
;; - Setosa has distinctly smaller petals (bottom-right quadrant)
;; - Sepal measurements show more overlap between species

;; ## Step 4: Interactive Brushing with D3
;;
;; Now let's add D3 brushing interaction! This is the same 4×4 SPLOM, but wrapped
;; with interactive brushing. Drag in any scatter panel to select points and see
;; them highlighted across all 16 panels.

(let [total-size (* 4 panel-size-4x4)

      ;; Generate all 16 panels
      panels (for [row (range 4)
                   col (range 4)]
               (let [x-offset (* col panel-size-4x4)
                     y-offset (* row panel-size-4x4)
                     var-x (vars-4x4 col)
                     var-y (vars-4x4 row)]
                 (if (= row col)
                   ;; Diagonal: colored histogram
                   (let [ps panel-size-4x4
                         m margin-4x4]
                     (render-histogram-panel iris species-groups var-x domains-4x4 x-offset y-offset ps m))
                   ;; Off-diagonal: colored scatter + regression
                   (let [ps panel-size-4x4
                         m margin-4x4]
                     (render-scatter-panel iris species-groups var-x var-y domains-4x4 x-offset y-offset ps m)))))

      ;; Collect all SVG elements
      backgrounds (map :background panels)
      plots (mapcat #(or (:plot %) []) panels)
      bars (mapcat #(or (:bars %) []) panels)

      ;; Build SVG
      svg-elem (apply svg/svg
                      {:width total-size :height total-size}
                      (concat backgrounds plots bars))]

  ;; Wrap with D3 brushing for 4×4 grid
  (-> svg-elem
      (wrap-with-brushing 4 panel-size-4x4 margin-4x4)
      kind/html))

;; Success! The complete brushable SPLOM demonstrates:
;; - Cross-panel coordination (brush in one panel, highlights in all)
;; - Point identity tracking via row indices  
;; - Species patterns visible through coordinated views
;; 
;; Try brushing different regions to explore:
;; - Setosa's distinct separation in petal measurements
;; - Versicolor/Virginica overlap in sepal dimensions
;; - Strong petal length/width correlation
;; ## What We Learned
;;
;; Building the SPLOM manually revealed substantial complexity:
;;
;; **Grid Layout**
;; - Manual offset calculation for 16 panels (row/col × panel-size)
;; - Shared domain computation across all uses of each variable
;; - Axes positioned relative to each panel's offset
;;
;; **Grouping by Species**
;; - Manual data partitioning: `(tc/group-by iris :variety)`
;; - Color assignment via indexed iteration: `(map-indexed ...)`
;; - Creating 3 series per panel (one per species)
;;
;; **Statistical Transforms**
;; - Linear regression computed per species per panel (24 regressions total!)
;; - Histograms with species-specific binning
;; - Each regression spans only its species' x-range
;;
;; **Rendering Coordination**
;; - Scatter + line series concatenated carefully
;; - Histogram bars rendered as manual SVG rects (geom.viz doesn't have bars)
;; - Background/grid/axes assembled in correct z-order
;;
;; ## Why a Grammar?
;;
;; All of this should be automated. A Grammar of Graphics layer should let us write:
;;
;; ```clj
;; (plot iris
;;   (cross [:sepal.length :sepal.width :petal.length :petal.width]
;;     (blend
;;       (layer {:geom :point :color :variety})
;;       (layer {:geom :smooth :color :variety :method :lm}))))
;; ```
;;
;; The grammar would:
;; - Detect the grid layout from `cross` of 4 variables
;; - Infer diagonal vs off-diagonal geoms
;; - Spread layers by `:color :variety` (3 species)
;; - Compute regressions per group automatically
;; - Assign colors and position panels
;;
;; That's what we'll build in the next post.
