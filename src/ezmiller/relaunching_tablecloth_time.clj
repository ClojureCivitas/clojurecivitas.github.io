^{:kindly/hide-code true
  :clay {:title "Relaunch tablecloth.time: Composability over Abstraction"
         :quarto {:author [:ezmiller]
                  :description "A composable approach to time series analysis in Clojure"
                  :draft false
                  :type :post
                  :date "2026-03-27"
                  :category :clojure
                  :tags [:time-series :tablecloth :data-science]}}}
(ns ezmiller.relaunching-tablecloth-time
  (:require [tablecloth.api :as tc]
            [tablecloth.time.api :as tct]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tech.v3.datatype.functional :as dfn]
            [scicloj.kindly.v4.kind :as kind]))

^{:kindly/hide-code true}
(kind/html "<figure>
  <img src=\"vic_elec_yearly.png\" alt=\"Victoria electricity demand by day of year, colored by year\" style=\"width:100%\"/>
  <figcaption>Half-hourly electricity demand in Victoria, Australia (2012–2014). Each line is one day, phased over the time of day (0 = midnight, 1 = midnight). Colors indicate year.</figcaption>
</figure>")

;; I recently relaunched an old Scicloj project called [tablecloth.time](https://github.com/scicloj/tablecloth.time). The goal of this project was to build a composable
;; extension for time series analysis built on top of
;; [tablecloth](https://scicloj.github.io/tablecloth/). Throughout this post,
;; `tct` refers to `tablecloth.time.api` and `tc` refers to `tablecloth.api`.
;; Originally, we
;; had built this project around a dataset index mechanism that was
;; built into tech.ml.dataset, but after that feature was removed in
;; v7, the project required a rethink. This post walks through that
;; rethink and the projects core core primitives today using the
;; Victoria electricity demand dataset.

;; ## Why No Index?
;;
;; The original tablecloth.time was built around an index two reasons:
;; performance (tree-based indexes offer O(log n) lookups) and
;; convenience
;; (you don't have to keep specifying which column is the time
;; column). Anyone who has used the Python Pandas data processing
;; library is likely familiar with this feature.
;;
;; But when tech.ml.dataset removed its indexing mechanism in v7, it forced a
;; rethink. And the rethink revealed that neither rationale held up.
;;
;; **On performance:** Unlike Python DataFrames, Clojure's datasets are immutable.
;; They're rebuilt on each transformation. Under these conditions, maintaining a
;; tree-based index is pure overhead — you'd rebuild it constantly. As Chris
;; Nuernberger (author of tech.ml.dataset)
;; [put it](https://clojurians.zulipchat.com/#narrow/channel/236259-tech.2Eml.2Edataset.2Edev/topic/index.20structures.20in.20Columns.20-.20scope/near/481581872):
;; "Just sorting the dataset and using binary search will outperform most/all
;; tree structures in this scenario."
;; (This is the same conclusion that [Polars](https://pola.rs/), the fastest-growing
;; Pandas alternative, reached — no index by design.)
;;
;; **On convenience:** The index adds implicit state threaded through your data.
;; Tablecloth's API avoids this — you always say which columns you're operating on.
;; The pipeline reads like what it does. This aligns with Clojure's broader preference
;; for explicit, composable operations over hidden magic.
;;
;; The simplicity isn't a compromise. It's the feature.
;;
;; For the full discussion of this design shift, see
;; [Composability Over Abstraction](https://humanscodes.com/tablecloth-time-relaunch)
;; on humanscodes.

;; Now let's dig into this library's primitivs and basic functionality.

;; ## Loading the Data
;;
;; We'll use the `vic_elec` dataset: half-hourly electricity demand from Victoria,
;; Australia, spanning 2012-2014. Let's load it and take a look.

(def vic-elec
  (-> (tc/dataset "https://gist.githubusercontent.com/ezmiller/6edf3e0f41848f532436c15bc94c2f4d/raw/vic_elec.csv"
                  {:key-fn keyword})
      (tc/convert-types :Time :local-date-time)))

(tc/head vic-elec)

;; The dataset has half-hourly readings with `:Time`, `:Demand` (in MW), 
;; `:Temperature`, and other fields.

;; ## Extracting Time Components
;;
;; The first primitive is `add-time-columns`. It extracts temporal fields from a
;; datetime column — day-of-week, month, hour, etc. — as new columns you can
;; group or filter on. Here's a quick look at what it produces:

(-> vic-elec
    (tct/add-time-columns :Time [:day-of-week :hour])
    (tc/head 10))

(def demand-by-day
  (-> vic-elec
      (tct/add-time-columns :Time [:day-of-week])
      (tc/group-by [:day-of-week])
      (tc/aggregate {:Demand #(dfn/mean (:Demand %))})
      (tc/order-by [:day-of-week])))

;; Look at the aggregated data:
(tc/head demand-by-day 7)

;; Step 2: Visualize the result:
(plotly/layer-bar demand-by-day
                  {:=x :day-of-week :=y :Demand})

;; Weekends (days 6 and 7) clearly have lower demand. The `:day-of-week` field
;; came from `add-time-columns`; the group-by, aggregate, and order-by are pure
;; tablecloth. The two libraries compose seamlessly.

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

;; `add-lead` shifts values forward — current Demand aligns with Demand 24 hours
;; ahead. Let's see if today's demand predicts tomorrow's:

(-> vic-elec
    (tct/add-lead :Demand 48 :Demand_lead48)
    (tc/drop-missing)
    (plotly/layer-point {:=x :Demand
                         :=y :Demand_lead48
                         :=mark-opacity 0.3}))

;; ## Resampling as a Pattern
;;
;; We showed the resampling pattern above: extract time fields, group, aggregate,
;; order. The same pattern scales to different granularities. Here are daily and
;; monthly averages using the same building blocks:

;; Daily averages:
(-> vic-elec
    (tct/add-time-columns :Time [:year :month :day])
    (tc/group-by [:year :month :day])
    (tc/aggregate {:Demand #(dfn/mean (:Demand %))
                   :Temperature #(dfn/mean (:Temperature %))})
    (tc/order-by [:year :month :day])
    (tc/head 10))

;; Monthly averages:
(-> vic-elec
    (tct/add-time-columns :Time [:year :month])
    (tc/group-by [:year :month])
    (tc/aggregate {:Demand #(dfn/mean (:Demand %))})
    (tc/order-by [:year :month])
    (plotly/layer-bar {:=x :month :=y :Demand :=color :year}))

;; Note that tablecloth.time is just a light layer in these
;; expressions. You could do this with tablecloth alone by manually
;; extracting datetime components. tablecloth.time's add-time-columns
;; just adds concision and expressiveness — it composes naturally with
;; the tablecloth operations.

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

;; ## Time Utilities (Column API)
;;
;; tablecloth.time mirrors tablecloth's structure: a dataset API (`tct`)
;; and a column API (`tablecloth.time.column.api`). The column API provides
;; lower-level utilities for working with time data directly — parsing,
;; conversion, flooring, extraction. These power the high-level functions
;; and are available when you need finer control.
;;
;; **Parsing** — `tablecloth.time.parse/parse` handles ISO-8601 strings and
;; custom formats with cached formatters for performance.
;;
;; **Conversion** — `convert-time` moves between representations (Instants,
;; LocalDateTimes, LocalDates, epoch milliseconds) with timezone awareness.
;;
;; **Flooring** — `down-to-nearest`, `floor-to-month`, `floor-to-quarter` bucket
;; timestamps to intervals. Useful for aggregating sub-daily data:

(require '[tablecloth.time.column.api :as tctc])

(-> vic-elec
    (tc/add-column :HourBucket
                   #(tctc/down-to-nearest (% :Time) 1 :hours {:zone "UTC"}))
    (tc/head 5))

;; The column API parallels `tablecloth.column.api` — work with columns
;; directly, then add them to your dataset. The high-level dataset functions
;; are convenience wrappers built from these pieces. Manipulating time data
;; is notoriously fiddly; tablecloth.time tries to smooth the sharp edges
;; without hiding the underlying java.time power.

;; ## What's Next
;;
;; tablecloth.time is experimental. Planned additions include rolling windows,
;; differencing, and higher-level patterns like `resample` that wrap the
;; composable building blocks.
;;
;; The [repository is on GitHub](https://github.com/scicloj/tablecloth.time).
;; For more worked examples, see the
;; [fpp3 Chapter 2 notebook](https://kingkongbot.github.io/tablecloth.time/chapter_02_time_series_graphics.html).
