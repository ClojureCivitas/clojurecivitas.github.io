^{:kindly/hide-code true
  :clay
  {:title  "Exploring Time Series Data Visualization"
   :quarto {:author      :syungb
            :description "A simple exploration to create time-series data visualization"
            :image       "overlayplot.png"
            :type        :post
            :date        "2025-08-30"
            :category    :clojure
            :tags        [:dataviz :computationalfluiddynamics :cfd :vega-lite]}}}
(ns scicloj.cfd.data-viz.interactive-visualization
  (:require
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind]
   [scicloj.cfd.intro.linear-1d-convection-with-array :as lin-conv]))

;; In my [earlier post](../intro/linear_1d_convection_with_array.html),
;; I demonstrated how to implement a linear fluid dynamics simulation (linear convection equation)
;; using primitive Java arrays in a one-dimensional setting.
;; The example showed how the flow velocity array gets recalculated and overwritten
;; during each loop iteration, with each loop representing a time step in our simulation.
;;
;; While some readers might be satisfied with just seeing the final result,
;; others are likely curious the process that produces the final outcome.
;; What if we could watch this process in real-time? What patterns emerge as the simulation progresses?
;;
;; This time, I want to show how to create a simple, but more engaging and interactive visualization.
;; Instead of simply overwriting arrays as we iterate through our simulation loop,
;; we will accumulate each result into a sequence.
;;
;; Once gain, we'll use the 1-D linear convection equation from fluid dynamics as our playground
;; (and reuse some code from the previous post).
;; The main focus of this post is on creating interactive plots that make simulation results
;; more visually interesting, so we won't dive too deep into physical implications (though the visual results
;; might just inspire you to explore that side too! 😉).
;;
;; ### Understanding the Data
;;
;; Before we jump into the data visualization, let's briefly set our initial data and what it represents.
;;
;; ### The Setup
;;
;; - **x**: Sliced positions along a one-dimensional space (think of points along a line)
;; - **y**: The flow velocity(`u`) at each position `x`
;; - **nt**: The number of time steps we'll simulate
;;
;; ### A Real World Analogy (Just to add a bit of fun)
;;
;; Imagine a rain gutter on a rainy day with water steadily flowing through it.
;; Now, picture slicing a section of this gutter and marking specific positions along its length-
;; these become our `x` coordinates. The water continues to flow, then suddenly, a bird overhead drops
;; a stone into the gutter, creating a sudden disturbance or **"shock"** in the water flow.
;; And this becomes our **initial condition**.
;;
;; **Initial Conditions**: The stone drop becomes our starting point(`t = 0`), and
;; at the moment of the impact, and the velocity at affected points jumps to 2 as a localized region
;; (in our case, between `0.5` and `1.0` of `x` is where the imaginary bird dropped a stone),
;; while the rest of the gutter maintains
;; its normal flow velocity of `1`.
;;
^:kindly/hide-code
(def init-params (assoc lin-conv/init-params
                   :nt 100
                   :array-x lin-conv/array-x))
;;
;; Our initial parameters and plot configuration are:
^:kindly/hide-code (dissoc init-params :array-x)

^:kindly/hide-code
(def array-u
  (let [nx (:nx lin-conv/init-params)
        u  (float-array nx)]
    (dotimes [i nx]
      (let [x-i (aget lin-conv/array-x i)
            u-i (lin-conv/init-cond-fn x-i)]
        (aset u i u-i)))
    u))

^:kindly/hide-code
(def initial-arr-u (float-array array-u))

^:kindly/hide-code
(def default-y-domain [0.8 2.1])

^:kindly/hide-code
(def default-plot-width-height-map {:width 500 :height 300})

^:kindly/hide-code
(kind/vega-lite (merge default-plot-width-height-map
                       {:mark     :line
                        :data     {:values (into [] (map #(hash-map :x % :y %2) lin-conv/array-x initial-arr-u))}
                        :encoding {:x {:title "Position X" :field "x" :type "quantitative"}
                                   :y {:title "Flow Velocity" :field "y" :type "quantitative" :scale {:domain default-y-domain}}}}))

;; ## Simulation Code
;;
;; Here is a simulation function we will use to accumulate the results:
(defn simulate-accumulate!
  "Simulate linear convection for given a given numer of times(nt) and accumulate
  all intermediate states.

  Args:
  - array-u: initial velocity field as a mutable array
             (note: we're reusing some codes from the previous post)
  - params: a config map with required keys: nt, nx, c, dx, and dt

  Returns a vector of (nt + 1) velocity field vectors, where each vector represents
  the velocity field at a specific time step."
  [array-u {:keys [nt]
            :as   params}]
  (loop [n    0
         !cum (transient [(vec array-u)])]
    (if (= n nt)
      (persistent! !cum)
      (recur (inc n) (conj! !cum (vec (lin-conv/mutate-linear-convection-u array-u params)))))))

;; Then we run the simulation and store the accumulated results:
(def accumulated-u (simulate-accumulate! initial-arr-u init-params))
;;
;; ## Data Visualization
;;
;; We have a vector of numbers representing velocity values.
;; With certain conditions, we want to visualize how these numbers change over time.
;; We will use [Vega-Lite](https://vega.github.io/vega-lite/)'s line plots for our visualization.
;;
^:kindly/hide-code
(def plot-params (assoc init-params :accumulated-u accumulated-u))
;;
;; First, we need a function that transforms accumulated results into Vega-Lite data format
;; by flattening time-series velocity fields into individual data points with time step indices:
(defn accumulated-u->vega-data-values [{:keys [array-x accumulated-u]}]
  (apply concat
    (map-indexed
      (fn [idx u-i]
        (map #(hash-map :idx idx :x % :y %2) array-x u-i))
      accumulated-u)))
;;
;; ### Multi-Series Overlay Plot
;;
;; In this first visualization, we show the simulation results as changes over time in one plot.
;; Since showing all 101 time steps in one plot may not be very useful,
;; we take every 10th result, and overlay each of those line plots into a single one.
;;
;; The plotting data is grouped by `idx`(time step), which produces
;; a [multi-series colored line plot](https://vega.github.io/vega-lite/docs/line.html#multi-series-colored-line-chart).
(let [[init-u & rest-u] accumulated-u
      plot-data (->> rest-u
                     (partition-all 10)
                     (map last)
                     (into [init-u])
                     (assoc plot-params :accumulated-u)
                     (accumulated-u->vega-data-values))]
  (kind/vega-lite (merge default-plot-width-height-map
                         {:data     {:values plot-data}
                          :mark     "line"
                          :encoding {:x     {:field "x" :type "quantitative" :title "X"}
                                     :y     {:field "y" :type "quantitative" :title "Flow Velocity" :scale {:domain default-y-domain}}
                                     :color {:field "idx" :type "nominal" :legend nil}}})))
;;
;; ### Interactive Time Step Plot
;;
;; In this second visualization, the plot includes an interactive slider that allows you to select
;; a particular time step(`idx`) to visualize the velocity field at that specific moment.
;;
;; This interactive slider is implemented using Vega-Lite's
;; [parameter binding feature](https://vega.github.io/vega-lite/docs/bind.html#input-element-binding).
(let [select-dt-name "selectedDt"
      initial-dt     0
      dt-step        1
      dt-field       "idx"]
  (kind/vega-lite (merge
                   default-plot-width-height-map
                   {:data      {:values (accumulated-u->vega-data-values plot-params)}
                    :transform [{:filter (str "datum." dt-field " == " select-dt-name)}]
                    :params    [{:name  select-dt-name
                                 :value initial-dt
                                 :bind  {:input "range"
                                         :name  "Time interval idx: "
                                         :min   initial-dt
                                         :max   (:nt plot-params)
                                         :step  dt-step}}]
                    :mark      "line"
                    :encoding  {:x {:field "x"
                                    :type  "quantitative"
                                    :title "X"}
                                :y {:field "y"
                                    :type  "quantitative"
                                    :title "Flow Velocity"
                                    :scale {:domain default-y-domain}}}})))
;;
;; ## Wrapping Up
;;
;; What started as simple primitive Java array manipulation has evolved into something much more engaging!
;; By accumulating our simulation states instead of overwriting those, we've made some progress to create
;; interactive visualizations to be able to describe the temporal dynamics of our fluid dynamics simulation.
;;
;; This exploration originally began while I was preparing [a talk](https://scicloj.github.io/scinoj-light-1/sessions.html#d-viscous-fluid-flow-data-analysis-using-burgers-equation)
;; for the SciCloj Light conference, where I wanted to explore better storytelling through data visualization.
;;
;; So far, I've only scratched the surface of what's possible. Beyond Vega-Lite, many other visualization tools
;; offer diverse options to be creative and make data come alive. I believe interactive visualizations can
;; transform dry numbers into compelling stories.
;;
;; I'll continue exploring these possibilities and share more discoveries along the way if I find any! ✨
;;