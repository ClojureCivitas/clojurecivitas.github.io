^{:kindly/hide-code true
  :clay             {:title  "Stats and Signals in the Flow of Asynctopolis"
                     :quarto {:author   [:alexmiller :timothypratley]
                              :type     :post
                              :draft    true
                              :date     "2025-05-1"
                              :category :clojure
                              :tags     [:core.async :core.async.flow]}}}
(ns core.async.flow.example.asynctopolis
  (:require [clojure.core.async :as a]
            [clojure.core.async.flow :as flow]
            [clojure.core.async.flow-static :as flow-static]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]
            [clojure.print-object.remove-extraneous]
            [clojure.datafy :as datafy]))

;; Welcome to Asynctopolis, a city where agents act on signals, not orders.
;; Here, unseen agents pass messages, track patterns, and sound alarms when the moment calls.
;; No one oversees the whole city, yet everything flows.
;;
;; Beneath it all hums the Stats Core Async Flow,
;; a network of processes working together without ever meeting.
;; Today, you'll meet the agents of this asynchronous allegiance.

;; This code is adapted from [Alex's stats flow example](https://github.com/puredanger/flow-example),
;; used for his video walkthrough.

^:kind/video ^:kindly/hide-code
{:youtube-id   "lXFwf3O4BVY"
 :iframe-width "100%"}

;; Above us in the sky flies Talon the Stat Hawk.
;; Sleek, silent, and tireless.
;; With a glint in his eye and wings tipped in probability,
;; he soars into the realm of the unknowable every half second,
;; returning with a fresh stat clutched in his talons.
;; He doesn't question, he doesn't falter.
;; He circles over the range from min to max, plucks a random integer,
;; and drops it onto a channel without ceremony.

(defn Talon
  "Generates a random value between min (inclusive) and max (exclusive)
  and writes it to out chan, waiting wait ms between until stop-atom is flagged."
  ([out min max wait stop-atom]
   (loop []
     (let [val (+ min (rand-int (- max min)))
           put (a/>!! out val)]
       (when (and put (not @stop-atom))
         (^[long] Thread/sleep wait)
         (recur))))))

;; Born of wind and randomness, Talon is no ordinary bird.
;; He executes his mission with the rhythm and the grace of chance incarnate.
;; Talon embodies the eternal recurrence of the loop.
;; An autonomous creature of purpose, relentless and unthinking.
;; To be a process is to endure.
;; Ever watchful, speaking in channels.

;; Fly Talon! Collect samples. Let's see what distribution you bring.

(let [c (a/chan)
      stop (atom false)
      n 100]
  (future (Talon c 0 20 0 stop))
  (let [samples (vec (repeatedly n (fn [] (a/<!! c))))]
    (reset! stop true)
    (-> (tc/dataset {:index  (range n)
                     :sample samples})
        (plotly/base {:=x     :index
                      :=y     :sample
                      :=title "The prey of Talon"})
        (plotly/layer-point))))

;; You have sampled fairly, Talon.

;; Talon operates at the behest of the city's Generator.

;; ## Meet Randomius Maximus, the Generator
;;
;; In a stone tower at the edge of the async city lives Randomius Maximus.
;; Robed in numbers, crowned with entropy, keeper of the unceasing stream.
;; He does not wander. He does not speak.
;; He gestures, and Talon flies.
;;
;; With a sweep of his hand, he dispatches his hawk to gather truths from the swirling chaos.
;; Min and Max are his decree.
;; Wait is his tempo.
;; As long as his flow runs, the stats will come.

;; To be a true citizen of Asynctopolis is to be known as a process.
;; To follow the sacred cycle of Vita Processus:
;; Describe your duties.
;; Initialize your station.
;; Transition with order.
;; Transform with purpose.

(defn Randomius
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
   (case transition
     :clojure.core.async.flow/resume
     (let [stop-atom (atom false)]
       (future (Talon (:stat in-ports) min max wait stop-atom))
       (assoc state :stop stop-atom))

     (:clojure.core.async.flow/pause :clojure.core.async.flow/stop)
     (do
       (reset! (:stop state) true)
       state)))

  ;; transform
  ([state in msg]
   [state (when (= in :stat) {:out [msg]})]))

;; Randomius, describe your duties!
(Randomius)

;; Initialize your station!
(def state
  (atom (Randomius {:min  10
                    :max  20
                    :wait 1})))
^:kind/println
@state

;; Transition with order.
(swap! state Randomius :clojure.core.async.flow/resume)

;; Talon is flying.
(-> (:clojure.core.async.flow/in-ports @state)
    (:stat)
    (a/<!!))

;; Transform with purpose.
(swap! state
       (fn [state]
         (let [[state step] (Randomius state :stat "I transform, therefore I am")]
           (println step)
           state)))
;; I see you wish to send a message to `stat`.
;; Be wary in the future, speak only numbers to those who seek stats.


;; Well done, Randomius.
;; You are a true citizen.
;; Now rest.
(swap! state Randomius :clojure.core.async.flow/stop)


;; ## Meet Tallystrix, the Whispering Aggregator
;;
;; In the marble shadows of the Hall of Measures,
;; Tallystrix gathers numbers in her obsidian basin.
;; She listens not to the sky, but to the `stat` channel,
;; where strange numbers arrive without explanation.
;; She lets them settle, silent and still.
;;
;; She says nothing—until the bell rings.
;; Then, with a tilt of the bowl and a whisper of reckoning,
;; she releases the average to those who asked.
;;
;; If a number is too high or too low, she sends a warning,
;; a flare in the async night.

(defn Tallystrix
  ;; describe
  ([] {:params   {:min "Min value, alert if lower"
                  :max "Max value, alert if higher"}
       :ins      {:stat "Channel to receive stat values"
                  :poke "Channel to poke when it is time to report a window of data to the log"}
       :outs     {:alert "Notify of value out of range {:val value, :error :high|:low"}
       :workload :compute})

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

;; Tallystrix, what messages have you?

(let [state {:min 1 :max 5 :vals []}
      [state' msgs'] (Tallystrix state :stat 100)]
  msgs')

;; Well alerted.
;; Your transform is sound.

;; ## Meet Chronon, the Scheduler of Bells

;; In a chamber just outside the Hall of Measures,
;; Chronon stands beside a great brass bell.
;; Every few thousand milliseconds, he raises his staff and strikes it.
;; A chime ripples through the channels and stirs the Aggregator within.

;; He does not wait for thanks. He does not miss a beat.
;; His duty is rhythm. His gift is regularity.
;; And with every ring, the silence grows wiser.

(defn Chronon
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

;; Chronon has no familiar to do his work,
;; and listens to no-one.

;; ## Meet Claxxus, the Notifier, the Herald

;; At the city’s edge stands Claxxus, cloaked in red and brass,
;; eyes ever on the flame that signals alarm.
;; He does not gather, he does not measure,
;; he only declares.
;;
;; When Tallystrix sends a flare,
;; Claxxus steps forward to speak.
;; He raises his voice for all to hear:
;; “Out of range!”

(defn Claxxus
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

;; Cursed to know only how to shout.

(Claxxus {:prefix "ERROR:"} :in "Out of range!")

;; ## The Asynchronous Allegiance
;;
;; All these roles are bound together in a flow,
;; a living graph of asynchronous collaboration.
;;
;; Randomius Maximus generates.
;; Chronon keeps the beat.
;; Tallystrix listens and computes.
;; Claxxus alerts.
;;
;; They never meet.
;; They never speak.
;; Yet they move as one.
;;
;; This is an allegiance, asynchronous and unseen.
;; Held together by channels, purpose, and trust.

(def config
  {:procs {:Randomius  {:args {:min 0 :max 12 :wait 500}
                        :proc (flow/process #'Randomius)}
           :Tallystrix {:args {:min 1 :max 10}
                        :proc (flow/process #'Tallystrix)}
           :Chronon    {:args {:wait 3000}
                        :proc (flow/process #'Chronon)}
           :Claxxus    {:args      {:prefix "Alert: "}
                        :proc      (flow/process #'Claxxus)
                        :chan-opts {:in {:buf-or-n (a/sliding-buffer 3)}}}}
   :conns [[[:Randomius :out] [:Tallystrix :stat]]
           [[:Chronon :out] [:Tallystrix :poke]]
           [[:Tallystrix :alert] [:Claxxus :in]]]})

^:kind/hiccup
[:iframe {:width  "100%"
          :height "600px"
          :srcdoc (flow-static/template config nil)}]

;; The Flow creates them, calling upon their civic duties,
;; Describe your duties.
;; Initialize your station.

(def flow (flow/create-flow config))

;; The city is ready, but not yet in action.

(datafy/datafy flow)

(def chs (flow/start flow))

chs

;; `report-chan` and `error-chan` are special conduits in the Flow.
;; Tallystrix sends her summaries to `report`, dutifully.
;; When something breaks it flows to `error`.

;; Claxxus does not speak of such failures.
;; He is for alerts.
;; Thresholds breached, events of note, things the city must hear.

;; The city breathes, the asynchronous allegiance stirs.
;; Transition with order.

(flow/resume flow)

;; Transform with purpose.

(flow/inject flow [:Tallystrix :poke] [true])
(flow/inject flow [:Tallystrix :stat] ["abc1000"])             ;; trigger an alert
(flow/inject flow [:Claxxus :in] [:sandwich])

(a/poll! (:report-chan chs))
(a/poll! (:error-chan chs))

;; The flow can coordinate peace.

(flow/pause flow)

(flow/stop flow)

;; The city falls silent.

;; Thus does Asynctopolis coordinate,
;; thus is Vita Processus observed.

;; The flow of Asynctopolis is a choreography of concurrent logic,
;; where each part knows just enough to play its role, and no more.
;; It's a quiet network of intent.
;; Each role with a narrow purpose, joined by shared channels and rhythm.

;; You can observe its work as it happens.
;; You can inspect, poke, pause, and resume.
;; Buffers shape its tempo, and transitions reveal its state.

;; In Asynctopolis, no one rules,
;; yet the system flows precisely, predictably, asynchronously.
