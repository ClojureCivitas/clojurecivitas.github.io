^{:kindly/hide-code true
  :clay {:title  "Simplest Plotly Streaming Chart"
         :quarto {:type     :post
                  :author   [:harold]
                  :date     "2025-12-13"
                  :description "Driving Plotly to Update a Chart with New Points."
                  :image    "simplest-plotly-streaming-chart.png"
                  :category :data-visualization
                  :tags     [:chart :data :streaming :update]
                  :keywords [:chart :data :streaming :update]}}}
(ns data-visualization.simplest-ploty-streaming-chart
  (:require [scicloj.kindly.v4.kind :as kind]))

(kind/hiccup
 [:div#streaming-chart])

^:kindly/hide-code
(kind/code (slurp "src/data_visualization/simplestplotlystreamingchart.cljs"))

^:kindly/hide-code
(kind/hiccup
 [:div
  [:script {:src "https://cdn.plot.ly/plotly-3.3.0.min.js"
            :type "application/javascript"}]
  [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.7.28/dist/scittle.js"
            :type "application/javascript"}]
  [:script {:src "simplestplotlystreamingchart.cljs"
            :type "application/x-scittle"}]])

;; ---

;; Discuss here:
;;
;; [https://clojurians.zulipchat.com/#narrow/channel/528764-clojurecivitas/topic/Streaming.20Chart](https://clojurians.zulipchat.com/#narrow/channel/528764-clojurecivitas/topic/Streaming.20Chart)
