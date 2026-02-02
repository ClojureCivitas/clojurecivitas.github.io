(ns data-visualization.simplestplotlystreamingchart)

(def id "streaming-chart")

(def v* (atom 0))

(js/Plotly.newPlot id (clj->js [{:y [@v*]}]))

(let [f (fn []
          (swap! v* + (- (* 2 (rand)) 1))
          (js/Plotly.extendTraces id (clj->js {:y [[@v*]]}) (clj->js [0])))
      i (js/setInterval f 300)]
  (js/setTimeout #(js/clearInterval i) 30000))
