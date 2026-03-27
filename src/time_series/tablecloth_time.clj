^{:kindly/hide-code true
  :clay {:title "Getting Started with tablecloth.time"
         :quarto {:author [:ezmiller]
                  :description "A composable approach to time series analysis in Clojure"
                  :draft false
                  :type :post
                  :date "2026-03-27"
                  :category :clojure
                  :tags [:time-series :tablecloth :data-science]}}}
(ns time-series.tablecloth-time
  (:require [tablecloth.api :as tc]
            [tablecloth.time.api :as tct]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tech.v3.datatype.functional :as dfn]
            [scicloj.kindly.v4.kind :as kind]))

;; [tablecloth.time](https://github.com/scicloj/tablecloth.time) is a composable
;; extension for time series analysis built on top of
;; [tablecloth](https://scicloj.github.io/tablecloth/). This post walks through
;; its core primitives using the classic Victoria electricity demand dataset.

;; ## Design Philosophy
;;
;; If you've used Pandas for time series work, you're familiar with the index-based
;; approach: set a DateTimeIndex, and operations like slicing and resampling work
;; implicitly on it. It's convenient, but it's also hidden state threaded through
;; your data.
;;
;; tablecloth.time takes a different path. Following tablecloth's design — and
;; Clojure's preference for explicit, composable operations — you always specify
;; which column you're working with. Each function takes the data and the columns
;; it operates on. The pipeline reads like what it does.
;;
;; This isn't a compromise. As Chris Nuernberger (author of tech.ml.dataset) noted,
;; with immutable datasets that get rebuilt on each transformation, a tree-based
;; index offers no performance advantage over binary search on sorted data. The
;; simplicity is the feature.

;; ## Loading the Data
;;
;; We'll use the `vic_elec` dataset: half-hourly electricity demand from Victoria,
;; Australia, spanning 2012-2014. Let's load it and take a look.

(def vic-elec
  (-> (tc/dataset "https://raw.githubusercontent.com/scicloj/tablecloth.time/main/data/vic_elec.csv"
                  {:key-fn keyword})
      (tc/convert-types :Time :instant)))

(tc/head vic-elec)

;; The dataset has half-hourly readings with `:Time`, `:Demand` (in MW), 
;; `:Temperature`, and other fields.

;; ## Extracting Time Components
;;
;; The first primitive is `add-time-columns`. It extracts temporal fields from a
;; datetime column — day-of-week, month, hour, etc. — as new columns you can
;; group or filter on.

(-> vic-elec
    (tct/add-time-columns :Time [:day-of-week :hour])
    (tc/head 10))

;; With these extracted fields, standard tablecloth operations give you resampling
;; and aggregation patterns. Let's compute average demand by day of week:

(-> vic-elec
    (tct/add-time-columns :Time [:day-of-week])
    (tc/group-by [:day-of-week])
    (tc/aggregate {:Demand #(dfn/mean (:Demand %))})
    (tc/order-by [:day-of-week])
    (plotly/layer-bar {:=x :day-of-week :=y :Demand}))

;; Weekends (days 6 and 7) clearly have lower demand. The `:day-of-week` field
;; came from `add-time-columns`; the aggregation is pure tablecloth.

;; ## Slicing Time Ranges
;;
;; `slice` selects rows within a time range using binary search on sorted data.
;; It's fast even on large datasets.

(-> vic-elec
    (tct/slice :Time "2012-01-09" "2012-01-15")
    (tc/row-count))

;; One week of data — 336 half-hourly observations. Let's visualize it:

(-> vic-elec
    (tct/slice :Time "2012-01-09" "2012-01-15")
    (plotly/layer-line {:=x :Time :=y :Demand}))

;; The daily oscillation is clearly visible: demand peaks during the day and
;; drops at night.

;; ## Lag and Lead Columns
;;
;; `add-lag` shifts column values by a fixed number of rows — useful for 
;; autocorrelation analysis. Note this is row-based, not time-aware: you need
;; to know your data's frequency and calculate the offset.
;;
;; Since this dataset has half-hourly readings, a lag of 48 rows equals 24 hours:

(-> vic-elec
    (tct/add-lag :Demand 48 :Demand_lag48)
    (tc/drop-missing)
    (tc/head 10))

;; Let's see if demand correlates with the same time yesterday:

(-> vic-elec
    (tct/add-lag :Demand 48 :Demand_lag48)
    (tc/drop-missing)
    (plotly/layer-point {:=x :Demand_lag48 
                         :=y :Demand
                         :=mark-opacity 0.3}))

;; The tight diagonal shows strong positive correlation — demand at any given
;; time is highly predictive of demand at the same time the previous day.

;; ## Resampling as a Pattern
;;
;; tablecloth.time doesn't have a `resample` function (yet). Instead, resampling
;; is a composable pattern: extract the time component you want, group by it,
;; and aggregate.
;;
;; Daily averages:

(-> vic-elec
    (tct/add-time-columns :Time [:date])
    (tc/group-by [:date])
    (tc/aggregate {:Demand #(dfn/mean (:Demand %))
                   :Temperature #(dfn/mean (:Temperature %))})
    (tc/order-by [:date])
    (tc/head 10))

;; Monthly averages:

(-> vic-elec
    (tct/add-time-columns :Time [:year :month])
    (tc/group-by [:year :month])
    (tc/aggregate {:Demand #(dfn/mean (:Demand %))})
    (tc/order-by [:year :month])
    (plotly/layer-bar {:=x :month :=y :Demand :=color :year}))

;; Each step is visible. Each step composes with the rest of your pipeline.

;; ## Combining Primitives
;;
;; Let's do something more interesting: analyze the daily demand profile,
;; comparing weekdays to weekends.

(-> vic-elec
    (tct/add-time-columns :Time [:day-of-week :hour])
    (tc/map-columns :weekend? [:day-of-week] #(>= % 6))
    (tc/group-by [:weekend? :hour])
    (tc/aggregate {:Demand #(dfn/mean (:Demand %))})
    (tc/order-by [:hour])
    (plotly/layer-line {:=x :hour 
                        :=y :Demand 
                        :=color :weekend?}))

;; Weekday demand shows the classic two-peak pattern (morning and evening),
;; while weekend demand is flatter and lower overall.

;; ## What's Next
;;
;; tablecloth.time is experimental. The current release provides these focused
;; primitives:
;;
;; - `add-time-columns` — extract temporal fields
;; - `slice` — select time ranges efficiently  
;; - `add-lag` / `add-lead` — shift values for autocorrelation
;;
;; Planned additions include rolling windows, differencing, and higher-level
;; patterns like `resample` that wrap the composable building blocks.
;;
;; The [repository is on GitHub](https://github.com/scicloj/tablecloth.time).
;; For more worked examples, see the
;; [fpp3 Chapter 2 notebook](https://kingkongbot.github.io/tablecloth.time/chapter_02_time_series_graphics.html).
