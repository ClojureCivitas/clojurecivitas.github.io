^{:kindly/hide-code true
  :clay {:title  "Transforming Datasets to Stack Charts"
         :quarto {:type     :post
                  :author   [:harold]
                  :date     "2025-06-24"
                  :description "A couple of quick ideas about visualizing data, especially with regard to comparison."
                  :category :datasets
                  :tags     [:charts :datasets]
                  :keywords [:charts :datasets]}}}
(ns scicloj.tableplot.ideas.stacking
  (:require [tech.v3.dataset :as ds]
            [scicloj.tableplot.v1.plotly :as plotly]))

;; With observed data, presumably from two runs of some experiment...

(def ds0
  (ds/->dataset "https://gist.githubusercontent.com/harold/18ba174c6c34e7d1c5d8d0954b48327c/raw"
                {:file-type :csv}))

(def ds1
  (ds/->dataset "https://gist.githubusercontent.com/harold/008bbcd477bf51b47548d680107a6195/raw"
                {:file-type :csv}))

;; Well, what have we got?

ds0

;; A few hundred numbers... Hm...

ds1

;; This neglects the hundreds of thousands of years invested in evolving a visual system...

(-> ds0
    (plotly/base {:=title "Run 0"})
    (plotly/layer-point {:=y "y"}))

(-> ds1
    (plotly/base {:=title "Run 1"})
    (plotly/layer-point {:=y "y"}))

;; Better; however, our aim is to compare them... Which is higher?

(-> (ds/concat (assoc ds0 :v "Run 0")
               (assoc ds1 :v "Run 1"))
    (plotly/base {:=title "Comparison Between Runs"})
    (plotly/layer-point {:=y "y"
                         :=color :v}))

;; Now it's up to the viewer to decide whether they like higher numbers or not.

;; ---

;; There are a couple of interesting ideas in that last bit of code:
;;
;; 1. `assoc` a constant onto a ds creates a constant column
;; 1. `:=color` takes care of grouping the results and the downstream display

;; Neat.
