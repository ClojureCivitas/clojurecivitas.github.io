^{:kindly/hide-code true
  :clay             {:title  "What He Saw Before His Wings Melted"
                     :quarto {:author   [:daslu :timothypratley]
                              :type     :post
                              :draft    true
                              :date     "2025-05-1"
                              :category :clojure
                              :tags     [:core.async :core.async.flow]}}}
(ns core.async.flow.example.before-his-wings-melted
  (:require [clojure.core.async :as async]
            [clojure.core.async.flow :as flow]
            [clojure.datafy :as datafy]
            [core.async.flow.example.asynctopolis :as asynctopolis]
            [core.async.flow.example.flow-show :as show]))

;; Long before he flew too high,
;; before the wax gave way and the world remembered only his fall,
;; Iccarus flew *low*.
;; They often leave out this part of his misadventures,
;; when curiosity, not hubris, guided his wings.
;; He flew not to ascend to Olympus,
;; but rather to get a good view of the lesser known Asynctopolis.

;; A city pulsing with signals, stitched together by invisible threads.
;; From above, its patterns unfolded like a diagram.
;; Flows of information, agents in silent collaboration,
;; each unaware of the others, yet perfectly aligned.

;; This is what he saw.

;; ## Asynctopolis from the Clouds

(show/flow-svg asynctopolis/flow {:show-chans   false
                                  :with-content false})

;; Coordinate asynchronous operations using `core.async`.
;; While powerful, these operations can become hard to reason about as they grow in complexity.
;; The `core.async.flow` library is a higher-level abstraction for modeling async processes as a Directed Acyclic Graph (DAG).
;; We can visualize flows [flow-monitor](https://github.com/clojure/core.async.flow-monitor).

;; He circled the skyline.
;; He watched the channels breathe.
;; And slowly, he spiraled down,
;; drawn not by ambition, but fascination—
;; closer to each process,
;; each transformation,
;; each role in the great asynchronous allegiance.


;; Let's walk through an exploration of such a flow.

;; ## What We'll Explore

;; In this notebook, we'll take a look at:

;; 1. **Basic flow structure**: What does a flow look like under the hood?
;; 2. **Static visualization**: How can we inspect its components?
;; 3. **Dynamic interaction**: How do values move through the flow, and what happens when they do?

;; This flow models a small system involving aggregation, notification, and reporting.
;; Internally, it consists of processes connected via channels.
(show/flow-svg asynctopolis/flow {:chans-as-ports true
                                  :with-content   false})

;; Are channels part of a process or not?
;; You decide

(show/flow-svg asynctopolis/flow {:chans-as-ports false
                                  :with-content   false})

;; Let's dig deeper into the details

(show/proc-table asynctopolis/flow)

;; This table gives us a clear list of components in the flow, including their names
;; and behaviors.

;; Next, let’s examine how these processes are **connected**.

(show/conn-table asynctopolis/flow)

;; Now we’re seeing the wiring: who talks to whom, and through what channels.

;; ## 3. Running the Flow

;; Time to bring our flow to life!
;; Calling `start` activates the processes and returns a map of the important channels for interaction.

(flow/start asynctopolis/flow)

;; We can now **inject values** into specific points in the flow.
;; Think of this like poking the system and watching how it reacts.

;; We send a “poke” signal to the `aggregator` process.

@(flow/inject asynctopolis/flow [:Tallystrix :poke] [true])

;; Flows implement the `Datafy` protocol so we can inspect them as data...
;; Good luck with that, there's a lot of it

(datafy/datafy asynctopolis/flow)

;; We send a stat string that is designed to trigger an alert.

@(flow/inject asynctopolis/flow [:Tallystrix :stat] ["abc1000"])

;; We send a notification message into the `notifier`.

@(flow/inject asynctopolis/flow [:Claxxus :in] [:sandwich])

;; TODO: show something changed

(show/flow-svg asynctopolis/flow {:chans-as-ports false
                                  :with-content   false})


;; ## 4. Observing the Results

;; Our flow includes a `report-chan`, where summaries and reports might be sent.

(def report-chan (:report-chan asynctopolis/chs))

(flow/ping asynctopolis/flow)

(async/poll! report-chan)

;; After pinging the system, we check if anything landed in the report channel.

;; We can also inspect the `error-chan`, where any issues in the flow are reported.

(def error-chan (:error-chan asynctopolis/chs))

(async/poll! error-chan)

;; If something unexpected occurred (e.g., bad input or failed routing),
;; this is where we’d find it.
;;
;;
;;
;;(flow/stop stats-flow)
;;(async/close! stat-chan)

;; @(flow/inject stats-flow [:aggregator :poke] [true])



; ## Flow

; At its core, flow is a library for building concurrent, event-driven systems
; using simple, communication-free functions.
; It lets you wire up processes and connect them through channels,
; while keeping control, error handling, and monitoring centralized and declarative.
;
;You define the structure as a directed graph—processes,
; their inputs and outputs, and how they connect—and the flow system takes care of orchestration.
; Your logic remains focused, while flow handles execution, coordination, and lifecycle concerns.
;
;All processes can be inspected or observed, and flows are fully data-driven, making them easy to reason about and visualize.
; It's concurrent programming with structure—without the chaos.

; ## Summary

;; By constructing, inspecting, and interacting with a flow, we can understand the
;; lifecycle and structure of asynchronous systems more clearly.
;;
;; This toolset provides a bridge between the abstract beauty of DAGs and the
;; gritty realism of channel communication—unlocking both power and clarity
;; in asynchronous Clojure code.

;; Happy flowing!
