^{:kindly/hide-code true
  :clay             {:title  "Core Async Flow Exploration"
                     :quarto {:author   [:daslu :timothypratley]
                              :date     "2025-06-02"
                              :category :clojure
                              :tags     [:core.async :core.async.flow]}}}
(ns lib.core.async.flow.exploration
  (:require [clojure.core.async :as async]
            [clojure.core.async.flow :as flow]
            [clojure.datafy :as datafy]
            [lib.core.async.flow.example.stats :as stats]
            [lib.core.async.flow.visualization :as fv]))

;; # Visualizing core.async.flows

;; Clojure's async flows are Directed Acyclic Graphs (DAGs) of channel operations.
;; The new [flow-monitor](https://github.com/clojure/core.async.flow-monitor)
;; can visualize these flows.

;; ## What We'll Explore

;; 1. Basic flow structure (processes, channels, connections)
;; 2. Static visualization of a sample flow
;; 3. Evolution as the flow changes

(def stats-flow
  (flow/create-flow stats/config))

(fv/proc-table stats-flow)

(fv/conn-table stats-flow)

(def chs (flow/start stats-flow))

@(flow/inject stats-flow [:aggregator :poke] [true])
@(flow/inject stats-flow [:aggregator :stat] ["abc1000"])   ;; trigger an alert
@(flow/inject stats-flow [:notifier :in] [:sandwich])

(def report-chan (:report-chan chs))
(flow/ping stats-flow)
(async/poll! report-chan)
(def error-chan (:error-chan chs))
(async/poll! error-chan)

;;(flow/stop stats-flow)
;;(async/close! stat-chan)

;; @(flow/inject stats-flow [:aggregator :poke] [true])

(datafy/datafy stats-flow)
