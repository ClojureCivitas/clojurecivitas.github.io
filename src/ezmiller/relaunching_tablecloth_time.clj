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
            [tablecloth.time.column.api :as tctc]
            [tablecloth.time.parse :as tparse]
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
;; [tablecloth](https://scicloj.github.io/tablecloth/). Originally, we
;; had built this project around a dataset index mechanism that was
;; built into tech.ml.dataset, but after that feature was removed in
;; v7, the project required a rethink. This post walks through that
;; rethink and the core primitives today, using the
;; Victoria electricity demand dataset.

;; ## Why No Index?
;;
;; The original tablecloth.time was built around an index for two
;; reasons: performance (tree-based indexes offer O(log n) lookups)
;; and convenience
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

;; Now let's dig into this library's primitives and basic functionality.
;; Throughout these examples, `tc` refers to `tablecloth.api`,
;; `tct` refers to `tablecloth.time.api`, and `tctc` refers to
;; `tablecloth.time.column.api`.

;; ## Loading the Data
;;
;; We'll use the `vic_elec` dataset: half-hourly electricity demand from Victoria,
;; Australia, spanning 2012-2014. Strings are parsed to datetime types on load:

(def vic-elec
  (-> (tc/dataset "https://gist.githubusercontent.com/ezmiller/6edf3e0f41848f532436c15bc94c2f4d/raw/vic_elec.csv"
                  {:key-fn keyword})
      (tc/convert-types :Time :local-date-time)))

(tc/head vic-elec)

;; The dataset has half-hourly readings with `:Time`, `:Demand` (in MW), 
;; `:Temperature`, and other fields.

;; ## Time at the Column Level
;;
;; Before diving into the high-level API, it's worth understanding what's
;; underneath. tablecloth.time mirrors tablecloth's two-level design: a
;; dataset API and a column API. The column API is where the actual time
;; manipulation happens, built on dtype-next's vectorized operations.
;;
;; Why does this matter? Because manipulating time data is notoriously fiddly.
;; Java's `java.time` package is powerful but verbose. Working with columns
;; of timestamps — converting, extracting, flooring — typically means writing
;; loops or mapping functions over sequences. tablecloth.time's column API
;; gives you operations that work on entire columns at once, using the same
;; fast, primitive-backed machinery as the rest of tech.ml.dataset.
;;
;; The building blocks fall into three categories:
;;
;; **Parsing** — `tablecloth.time.parse/parse` handles ISO-8601 strings and
;; custom formats with cached formatters for performance. For now this is
;; scalar (single value), but bulk parsing happens automatically when loading
;; datasets with `tc/convert-types`.
;;
;; **Conversion** — `convert-time` moves between representations (Instants,
;; LocalDateTimes, LocalDates, epoch milliseconds) with timezone awareness.
;; This is the workhorse for preparing time columns for different operations.
;;
;; **Flooring and extraction** — `down-to-nearest`, `floor-to-month`, and
;; field extractors like `year`, `hour`, `day-of-week` operate on columns
;; using dtype-next's vectorized arithmetic. These are **column in, column out**:

;; Extract just the hour from the Time column:
(tctc/hour (:Time vic-elec))

;; Floor timestamps to hour buckets:
(tctc/down-to-nearest (:Time vic-elec) 1 :hours {:zone "UTC"})

;; The key thing to notice: no Clojure seqs, no explicit loops. These
;; operations work on primitive arrays under the hood, just like dtype-next's
;; numeric operations. The result is a column that can be added directly
;; to a dataset.

;; ## Building Up: add-time-columns
;;
;; With these column-level tools in hand, the dataset-level API is just
;; convenience. `add-time-columns` — the function that most users reach
;; for first — is actually a thin wrapper around the extractors we just saw.
;;
;; Here's what it does internally:
;;
;; 1. Take the source time column from the dataset
;; 2. Look up extractor functions from a map (`:year` → `tctc/year`, etc.)
;; 3. Apply each extractor to produce new columns
;; 4. Add those columns back to the dataset
;;
;; The "primitive" is just composition of lower-level pieces. This matters
;; because it means you can drop down when the high-level API doesn't
;; quite fit. Need a custom computed field? Build it from the column
;; tools and add it yourself.
;;
;; Let's see it in action:

(-> vic-elec
    (tct/add-time-columns :Time [:day-of-week :hour])
    (tc/head 10))

;; ## The Resampling Pattern
;;
;; With time fields extracted, standard tablecloth operations take
;; over. Resampling, which in time series means aggregating to coarser
;; time granularity, is just another pattern of composition: add time
;; columns, group, aggregate, order.
;;
;; Let's break it into two steps. First, the data transformation:

(def demand-by-day
  (-> vic-elec
      (tct/add-time-columns :Time [:day-of-week])
      (tc/group-by [:day-of-week])
      (tc/aggregate {:Demand #(dfn/mean (:Demand %))})
      (tc/order-by [:day-of-week])))

;; Look at the aggregated data:
(tc/head demand-by-day 7)

;; Then visualize:
(plotly/layer-bar demand-by-day
                  {:=x :day-of-week :=y :Demand})

;; Weekends (days 6 and 7) clearly have lower demand. The `:day-of-week` field
;; came from `add-time-columns`; the group-by, aggregate, and order-by are pure
;; tablecloth. tablecloth.time provides the time-specific pieces, then gets
;; out of the way.
;;
;; The same pattern scales to different granularities. Here are daily and
;; monthly averages:

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

;; Note that tablecloth.time is just a light layer here. You could do this
;; with tablecloth alone by manually extracting datetime components.
;; `add-time-columns` just adds concision — it composes naturally with the
;; tablecloth operations you're already using.

;; ## Slicing Time Ranges
;;
;; `slice` selects rows within a time range using binary search on
;; sorted data. Here is where we would have previously leaned on an
;; index. Now we use binary search on a sorted column. It's fast even
;; on large datasets — the O(log n) lookup without the overhead of
;; maintaining a tree structure, though it may need to sort the data
;; if unsorted.

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
;; to know your data's frequency and calculate the offset. Since this dataset
;; has half-hourly readings, a lag of 48 rows equals 24 hours:

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
;;
;; `add-lead` works the same way but shifts values forward. Current demand
;; aligns with demand 24 hours ahead — useful when you need to align past
;; observations with future outcomes for predictive modeling:

(-> vic-elec
    (tct/add-lead :Demand 48 :Demand_lead48)
    (tc/drop-missing)
    (plotly/layer-point {:=x :Demand
                         :=y :Demand_lead48
                         :=mark-opacity 0.3}))

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
;; tablecloth.time is experimental. The current release provides
;; focused primitives built on solid foundations: parsing, conversion,
;; and field extraction at the column level; convenient dataset-level
;; wrappers that compose with standard tablecloth operations. My hope
;; is this provides a solid basis for building convinient abstractions
;; that are just patterns of composition.
;;
;; Planned additions include rolling windows, differencing, and higher-level
;; patterns like `resample` that wrap the composable building blocks.
;;
;; The [repository is on GitHub](https://github.com/scicloj/tablecloth.time).
;; For more worked examples, see the
;; [fpp3 Chapter 2 notebook](https://kingkongbot.github.io/tablecloth.time/chapter_02_time_series_graphics.html).
