(ns core.async.flow.exploration
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [core.async.flow.example.stats :as stats]
            [clojure.core.async.flow :as flow]
            [clojure.datafy :as datafy]))

;; # Visualizing core.async.flows

;; Clojure's async flows are Directed Acyclic Graphs (DAGs) of channel operations.
;; The new [flow-monitor](https://github.com/clojure/core.async.flow-monitor)
;; can visualize these flows.

;; ## What We'll Explore

;; 1. Basic flow structure (processes, channels, connections)
;; 2. Static visualization of a sample flow
;; 3. Evolution as the flow changes

(defn id-for [x]
  (cond (keyword? x) (str (symbol x))
        (vector? x) (str/join "_" (map id-for x))
        (string? x) x
        :else (str x)))

(defn conn-table [flow]
  (let [{:keys [conns procs]} (datafy/datafy flow)
        all-proc-chans (into #{} cat conns)]
    ;; TODO: add channel state
    ^:kind/table
    {:row-maps (vec (for [[from to] conns]
                      {:source (id-for from)
                       :target (id-for to)}))}))

(defn proc-table [flow]
  (let [{:keys [conns procs]} (datafy/datafy flow)
        all-proc-chans (into #{} cat conns)]
    ^:kind/table
    {:column-names ["process" "start params" "in chans" "out chans"]
     :row-vectors  (for [[proc-key proc-chans] (group-by first all-proc-chans)]
                     (let [{:keys [args proc]} (get procs proc-key)
                           {:keys [desc]} proc
                           {:keys [params ins outs]} desc]
                       [(name proc-key)
                        ^:kind/hiccup
                        [:div
                         (for [[k param] params]
                           [:div
                            [:div [:strong (name k)] ": " (get args k)]
                            [:div param]])]
                        ^:kind/hiccup
                        [:div (for [[k v] ins]
                                [:div [:strong (name k)] ": " v])]
                        ^:kind/hiccup
                        [:div (for [[k v] outs]
                                [:div [:strong (name k)] ": " v])]]))}))

(def stats-flow
  (flow/create-flow stats/config))

(proc-table stats-flow)

;; would be more interesting if we show the buffer state
(conn-table stats-flow)


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
