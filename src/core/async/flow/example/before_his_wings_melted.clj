^{:kindly/hide-code true
  :clay             {:title  "What He Saw Before His Wings Melted"
                     :quarto {:author   [:daslu :timothypratley]
                              :type     :post
                              :date     "2025-05-1"
                              :category :clojure
                              :tags     [:core.async :core.async.flow]}}}
(ns core.async.flow.example.before-his-wings-melted
  (:require [clojure.core.async :as async]
            [clojure.core.async.flow :as flow]
            [core.async.flow.example.asynctopolis :as asynctopolis]
            [core.async.flow.example.flow-show :as show]))

;; Long before he flew too high,
;; before the wax gave way and the world remembered only his fall,
;; Icarus flew *low*.
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

(def asynctopolis (flow/create-flow asynctopolis/config))

(show/flow-svg asynctopolis nil {:show-chans   false
                                 :with-content false})

;; He circled the skyline.
;; He watched the channels breathe.
;; And slowly, he spiraled down,
;; drawn not by ambition, but fascination—
;; closer to each process,
;; each transformation,
;; each role in the great asynchronous allegiance.


;; As he flew lower he saw that processes are connected via channels.

(show/flow-svg asynctopolis nil {:chans-as-ports true
                                 :with-content   false})

;; Are channels attached to a process, or are they part of it?
;; You can choose to visualize them as distinct connectors,
;; or as embedded roles within each process.
;; Both perspectives reveal useful insights.

(show/flow-svg asynctopolis nil {:chans-as-ports false
                                 :with-content   false})

;; Wanting to see more, Icarus swooped even lower to survey the processes.

(show/proc-table asynctopolis)

;; With a clearer understanding of the processes,
;; he pondered how these processes are connected.

(show/conn-table asynctopolis)

;; In doing so he realized there are also 2 global channels,
;; `report` and `error`:

(show/flow-svg asynctopolis nil {:chans-as-ports    false
                                 :with-content      false
                                 :show-global-chans true})

;; Any process can put messages on `report` and `error`.

;; ## Street Level

;; Reaching street level, he called out `start`!
;; The flow responded, handing him report and error channels in a map.

(def chs (flow/start asynctopolis))

;; But still, nothing stirred. So he yelled `resume`!

(flow/resume asynctopolis)

(def report-chan (:report-chan chs))

(async/poll! report-chan)

(flow/inject asynctopolis [:Tallystrix :poke] [true])

(flow/inject asynctopolis [:Tallystrix :stat] ["abc1000"])

(show/flow-svg asynctopolis chs {:chans-as-ports false
                                 :with-content   false})

;; Tallystrix takes only numbers, `"abc1000"` was not acceptable.

;; ## Conclusion

        (flow/stop asynctopolis)
(Thread/sleep 1)

;; Icarus realized that
;; Flow is a library for building concurrent, event-driven systems
;; out of simple, communication-free functions.
;; Processes connect through channels.
;; You define the structure as a directed graph.
;; Flow takes care of orchestration.
;; Flows are data-driven, easy to inspect, reason about and visualize.
;; Then he wondered just how high could he fly?

;; Happy flowing, and keep your feathers waxed!
