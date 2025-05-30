^{:kindly/hide-code true
  :clay             {:title  "Core Async Flow Exploration"
                     :quarto {:author   [:daslu :timothypratley]
                              :type     :post
                              :date     "2025-05-16"
                              :category :clojure
                              :tags     [:core.async :core.async.flow]}}}
(ns core.async.flow.exploration
  (:require [clojure.core.async :as async]
            [clojure.core.async.flow :as flow]
            [clojure.datafy :as datafy]
            [core.async.flow.example.stats :as stats]
            [core.async.flow.visualization :as fv]))

;; One of Clojure’s superpowers is the ability to coordinate asynchronous operations
;; using `core.async`.
;; While powerful, these operations can become hard to reason about as they grow in complexity.

;; The new `core.async.flow` library offers a higher-level abstraction for modeling
;; async processes as a **Directed Acyclic Graph (DAG)**.
;; And now, with [flow-monitor](https://github.com/clojure/core.async.flow-monitor),
;; we can *visualize* and *analyze* those flows.
;;
;; Let's walk through an exploration of such a flow.

;; ## What We'll Explore

;; In this notebook, we'll take a look at:

;; 1. **Basic flow structure**: What does a flow look like under the hood?
;; 2. **Static visualization**: How can we inspect its components?
;; 3. **Dynamic interaction**: How do values move through the flow, and what happens when they do?

;; ## 1. Creating a Flow

;; Flows are created from configuration

(def stats-flow
  (flow/create-flow stats/config))

;; This flow models a small system involving aggregation, notification, and reporting.
;; Internally, it consists of processes connected via channels.

;; ## 2. Inspecting the Flow

;; Flows implement the `Datafy` protocol so we can inspect them as data.

(datafy/datafy stats-flow)

;; That's a lot to take in! Fortunately, we can make things more digestible
;; by viewing just the **processes** involved.

(fv/proc-table stats-flow)

;; This table gives us a clear list of components in the flow, including their names
;; and behaviors.

;; Next, let’s examine how these processes are **connected**.

(fv/conn-table stats-flow)

;; Now we’re seeing the wiring: who talks to whom, and through what channels.

;; ## 3. Running the Flow

;; Time to bring our flow to life!
;; Calling `start` activates the processes and returns a map of the important channels for interaction.

(def chs (flow/start stats-flow))

;; We can now **inject values** into specific points in the flow.
;; Think of this like poking the system and watching how it reacts.

;; We send a “poke” signal to the `aggregator` process.

@(flow/inject stats-flow [:aggregator :poke] [true])

;; We send a stat string that is designed to trigger an alert.

@(flow/inject stats-flow [:aggregator :stat] ["abc1000"])

;; We send a notification message into the `notifier`.

@(flow/inject stats-flow [:notifier :in] [:sandwich])

;; ## 4. Observing the Results

;; Our flow includes a `report-chan`, where summaries and reports might be sent.

(def report-chan (:report-chan chs))

(flow/ping stats-flow)

(async/poll! report-chan)

;; After pinging the system, we check if anything landed in the report channel.

;; We can also inspect the `error-chan`, where any issues in the flow are reported.

(def error-chan (:error-chan chs))

(async/poll! error-chan)

;; If something unexpected occurred (e.g., bad input or failed routing),
;; this is where we’d find it.
;;
;;
;;
;;(flow/stop stats-flow)
;;(async/close! stat-chan)

;; @(flow/inject stats-flow [:aggregator :poke] [true])


; ## Summary

;; By constructing, inspecting, and interacting with a flow, we can understand the
;; lifecycle and structure of asynchronous systems more clearly.
;;
;; This toolset provides a bridge between the abstract beauty of DAGs and the
;; gritty realism of channel communication—unlocking both power and clarity
;; in asynchronous Clojure code.

;; Happy flowing!
