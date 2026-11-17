(ns scicloj.clay.ideas.uije3-sine-wave
  (:require [scicloj.clay.ideas.uije3 :as uj]))

(def width 400)
(def height 200)
(def default-state {:amplitude 1 :frequency 2 :phase 0})

(defn wave-path [{:keys [amplitude frequency phase]}]
  (let [n 200
        points (for [i (range (inc n))]
                 (let [t (/ i n)
                       y (* amplitude (Math/sin (+ (* 2 Math/PI frequency t) phase)))]
                   (str (* t width) "," (* (/ height 2) (- 1 (* 0.9 y))))))]
    (str "M" (apply str (interpose " L" points)))))

(defn sine-ui [{:keys [amplitude frequency phase] :as state}]
  [:col {:state state}
   [:svg {:viewBox (str "0 0 " width " " height)
          :style {:width "100%"
                  :height :auto
                  :border "1px solid #ccc"}}
    [:line {:x1 0 :y1 (/ height 2) :x2 width :y2 (/ height 2) :stroke "#999"}]
    [:path {:d (wave-path state) :fill "none" :stroke "#2a7ae2" :stroke-width 2}]]
   [:input {:name :amplitude
            :type "range" :min 0 :max 1 :step 0.01
            :label (str "Amplitude: " amplitude)}]
   [:input {:name :frequency
            :type "range" :min 1 :max 10 :step 0.1
            :label (str "Frequency: " frequency)}]
   [:input {:name :phase
            :type "range" :min 0 :max 20 :step 0.2
            :label (str "Phase: " phase)}]
   [:button {:name :reset} "Reset"]])

(defn sine-event [{:keys [click] :as state}]
  (if (= click :reset)
    default-state
    (merge default-state state)))

(def sine-wave (comp sine-ui sine-event))

(uj/uije #'sine-wave)
