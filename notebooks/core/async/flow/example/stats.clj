^{:kindly/hide-code true
  :clay             {:title  "Core Async Flow Stats Example"
                     :quarto {:author   [:alexmiller :timothypratley]
                              :type     :post
                              :date     "2025-06-01"
                              :category :clojure
                              :tags     [:core.async :core.async.flow]}}}
(ns core.async.flow.example.stats
  (:require [clojure.core.async :as a]
            [clojure.core.async.flow :as flow]
            [clojure.core.async.flow-monitor :as mon]))

;; Recently Alex provided a video walkthrough on how to use `core.async.flow` to build a stats monitor.

^:kind/video ^:kindly/hide-code
{:youtube-id   "lXFwf3O4BVY"
 :iframe-width "100%"}

;; This notebook is adapted from [his code](https://github.com/puredanger/flow-example).

(defn stat-gen
  "Generates a random value between min (inclusive) and max (exclusive)
  and writes it to out chan, waiting wait ms between until stop-atom is flagged."
  ([out min max wait stop-atom]
   (loop []
     (let [val (+ min (rand-int (- max min)))
           put (a/>!! out val)]
       ;(println "stat-gen" (System/identityHashCode stop-atom) val put (not @stop-atom))
       (when (and put (not @stop-atom))
         (^[long] Thread/sleep wait)
         (recur))))))

(defn source
  "Source proc for random stats"
  ;; describe
  ([] {:params {:min  "Min value to generate"
                :max  "Max value to generate"
                :wait "Time in ms to wait between generating"}
       :outs   {:out "Output channel for stats"}})

  ;; init
  ([args]
   (assoc args
     :clojure.core.async.flow/in-ports {:stat (a/chan 100)}
     :stop (atom false)))

  ;; transition
  ([{:keys [min max wait :clojure.core.async.flow/in-ports] :as state} transition]
   ;(println "transition" transition)
   (case transition
     :clojure.core.async.flow/resume
     (let [stop-atom (atom false)]
       (future (stat-gen (:stat in-ports) min max wait stop-atom))
       (assoc state :stop stop-atom))

     (:clojure.core.async.flow/pause :clojure.core.async.flow/stop)
     (do
       (reset! (:stop state) true)
       state)))

  ;; transform
  ([state in msg]
   ;(println "source transform" in msg)
   [state (when (= in :stat) {:out [msg]})]))

(defn aggregator
  ;; describe
  ([] {:params   {:min "Min value, alert if lower"
                  :max "Max value, alert if higher"}
       :ins      {:stat "Channel to receive stat values"
                  :poke "Channel to poke when it is time to report a window of data to the log"}
       :outs     {:alert "Notify of value out of range {:val value, :error :high|:low"}
       :workload :compute
       })

  ;; init
  ([args] (assoc args :vals []))

  ;; transition
  ([state transition] state)

  ;; transform
  ([{:keys [min max vals] :as state} input-id msg]
   (case input-id
     :stat (let [state' (assoc state :vals (conj vals msg))
                 msgs (cond
                        (< msg min) {:alert [{:val msg, :error :low}]}
                        (< max msg) {:alert [{:val msg, :error :high}]}
                        :else nil)]
             [state' msgs])
     :poke [(assoc state :vals [])
            {:clojure.core.async.flow/report (if (empty? vals)
                                               [{:count 0}]
                                               [{:avg   (/ (double (reduce + vals)) (count vals))
                                                 :count (count vals)}])}]
     [state nil])))

(comment
  ;; test aggregator alert case - no channels involved
  (let [state {:min 1 :max 5 :vals []}
        [state' msgs'] (aggregator state :stat 100)]
    (assert (= msgs' {:alert [{:val 100, :error :high}]})))
  )


(defn scheduler
  ;; describe
  ([] {:params {:wait "Time to wait between pokes"}
       :outs   {:out "Poke channel, will send true when the alarm goes off"}})

  ;; init
  ([args]
   (assoc args
     :clojure.core.async.flow/in-ports {:alarm (a/chan 10)}
     :stop (atom false)))

  ;; transition
  ([{:keys [wait :clojure.core.async.flow/in-ports] :as state} transition]
   ;(println "scheduler transition" transition state transition)
   (case transition
     :clojure.core.async.flow/resume
     (let [stop-atom (atom false)]
       (future (loop []
                 (let [put (a/>!! (:alarm in-ports) true)]
                   (when (and put (not @stop-atom))
                     (^[long] Thread/sleep wait)
                     (recur)))))
       (assoc state :stop stop-atom))

     (:clojure.core.async.flow/pause :clojure.core.async.flow/stop)
     (do
       (reset! (:stop state) true)
       state)))

  ;; transform
  ([state in msg]
   [state (when (= in :alarm) {:out [true]})]))

(defn printer
  ;; describe
  ([] {:params {:prefix "Log message prefix"}
       :ins    {:in "Channel to receive messages"}})

  ;; init
  ([state] state)

  ;; transition
  ([state _transition] state)

  ;; transform
  ([{:keys [prefix] :as state} _in msg]
   (println prefix msg)
   [state nil]))

(def config
  {:procs {:generator  {:args {:min 0 :max 12 :wait 500} :proc (flow/process #'source)}
           :aggregator {:args {:min 1 :max 10} :proc (flow/process #'aggregator)}
           :scheduler  {:args {:wait 3000} :proc (flow/process #'scheduler)}
           :notifier   {:args      {:prefix "Alert: "} :proc (flow/process #'printer)
                        :chan-opts {:in {:buf-or-n (a/sliding-buffer 3)}}}}
   :conns [[[:generator :out] [:aggregator :stat]]
           [[:scheduler :out] [:aggregator :poke]]
           [[:aggregator :alert] [:notifier :in]]]})

(defn create-flow
  []
  (flow/create-flow config))

(comment
  (def f (create-flow))
  (def chs (flow/start f))
  (flow/resume f)
  (flow/pause f)
  (flow/stop f)

  (def server (mon/start-server {:flow f}))
  (mon/stop-server server)

  @(flow/inject f [:aggregator :poke] [true])
  @(flow/inject f [:aggregator :stat] ["abc1000"])          ;; trigger an alert
  @(flow/inject f [:notifier :in] [:sandwich])

  (def report-chan (:report-chan chs))
  (flow/ping f)
  (a/poll! report-chan)
  (def error-chan (:error-chan chs))
  (a/poll! error-chan)

  (flow/stop f)
  (a/close! stat-chan)

  @(flow/inject f [:aggregator :poke] [true])

  (require '[clojure.datafy :as datafy])
  (datafy/datafy f)

  (require '[clojure.core.async.flow-static :refer [graph]])
  (graph f)

  )
