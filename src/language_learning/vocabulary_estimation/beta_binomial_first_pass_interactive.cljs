(ns language-learning.vocabulary-estimation.beta-binomial-first-pass-interactive
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

(def initial-state
  {:alpha 1
   :beta 1
   :previous [1 1]
   :last-response nil
   :responses []})

(defonce state (r/atom initial-state))

(def worked-correct-counts [4 4 3 3 2 1 1 0])
(def sampling-target 500)
(def sampling-seed 20260712)

(def worked-sampling-strata
  (mapv (fn [index k]
          {:index (inc index)
           :pool-size 1000
           :k k
           :n 4
           :alpha (inc k)
           :beta (inc (- 4 k))})
        (range)
        worked-correct-counts))

(defn log-factorial [n]
  (reduce + 0 (map #(js/Math.log %) (range 2 (inc n)))))

(defn beta-density
  "Normalized Beta density for positive integer alpha and beta."
  [alpha beta p]
  (let [log-beta (- (+ (log-factorial (dec alpha))
                       (log-factorial (dec beta)))
                    (log-factorial (dec (+ alpha beta))))]
    (js/Math.exp (- (+ (* (dec alpha) (js/Math.log p))
                       (* (dec beta) (js/Math.log (- 1 p))))
                    log-beta))))

(defn likelihood
  [response p]
  (case response
    :correct p
    :dont-know (- 1 p)
    1))

(def plot-points
  (mapv #(/ % 100.0) (range 1 100)))

(defn scaled-path
  [f max-y]
  (->> plot-points
       (map-indexed
        (fn [i p]
          (let [x (+ 52 (* p 616))
                y (- 230 (* 170 (/ (f p) max-y)))]
            (str (if (zero? i) "M" "L") x " " y))))
       (clojure.string/join " ")))

(defn response-label [response]
  (case response
    :correct "correct"
    :dont-know "don't know"
    "none yet"))

(defn density-chart
  [{:keys [alpha beta previous last-response]}]
  (let [[previous-alpha previous-beta] previous
        curves [{:label "Prior Beta(1,1)"
                 :color "#6c757d"
                 :dash "6 5"
                 :f #(beta-density 1 1 %)}
                {:label (str "Previous Beta(" previous-alpha "," previous-beta ")")
                 :color "#7b61a8"
                 :dash "3 4"
                 :f #(beta-density previous-alpha previous-beta %)}
                {:label (str "Last-response likelihood (" (response-label last-response) ")")
                 :color "#e69f00"
                 :dash "8 4"
                 :f #(likelihood last-response %)}
                {:label (str "Current Beta(" alpha "," beta ")")
                 :color "#2780e3"
                 :dash nil
                 :f #(beta-density alpha beta %)}]
        max-y (apply max 1 (mapcat (fn [{:keys [f]}] (map f plot-points)) curves))]
    [:svg {:view-box "0 0 720 330"
           :role "img"
           :aria-labelledby "beta-chart-title beta-chart-desc"
           :style {:width "100%" :height "auto" :display "block"}}
     [:title#beta-chart-title "Prior, likelihood, and posterior density curves"]
     [:desc#beta-chart-desc
      (str "The current posterior is Beta " alpha ", " beta
           ". The latest response is " (response-label last-response)
           ". Horizontal axis is knowing rate p from zero to one; vertical axis is relative density.")]
     [:line {:x1 52 :x2 668 :y1 230 :y2 230 :stroke "currentColor" :stroke-width 1.5}]
     [:line {:x1 52 :x2 52 :y1 45 :y2 230 :stroke "currentColor" :stroke-width 1.5}]
     (for [[p label] [[0 "0"] [0.25 ".25"] [0.5 ".50"] [0.75 ".75"] [1 "1"]]
           :let [x (+ 52 (* p 616))]]
       ^{:key p}
       [:g
        [:line {:x1 x :x2 x :y1 230 :y2 237 :stroke "currentColor"}]
        [:text {:x x :y 256 :text-anchor "middle" :font-size 13 :fill "currentColor"} label]])
     [:text {:x 360 :y 280 :text-anchor "middle" :font-size 14 :fill "currentColor"}
      "Knowing rate p"]
     [:text {:x 52 :y 30 :text-anchor "start" :font-size 14 :fill "currentColor"}
      "Relative density"]
     (for [{:keys [label color dash f]} curves]
       ^{:key label}
       [:path {:d (scaled-path f max-y)
               :fill "none"
               :stroke color
               :stroke-width (if (= color "#2780e3") 4 2.5)
               :stroke-dasharray dash
               :vector-effect "non-scaling-stroke"}])
     (for [[i {:keys [label color dash]}] (map-indexed vector curves)
           :let [x (if (< i 2) 56 365)
                 y (+ 300 (* (mod i 2) 22))]]
       ^{:key label}
       [:g
        [:line {:x1 x :x2 (+ x 28) :y1 (- y 5) :y2 (- y 5)
                :stroke color :stroke-width 3 :stroke-dasharray dash}]
        [:text {:x (+ x 36) :y y :font-size 12 :fill "currentColor"} label]])]))

(defn record-response! [response]
  (swap! state
         (fn [{:keys [alpha beta responses] :as current}]
           (-> current
               (assoc :previous [alpha beta]
                      :last-response response
                      :responses (conj responses response))
               (update (if (= response :correct) :alpha :beta) inc)))))

(defn control-button [label on-click class-name]
  [:button {:type "button"
            :class class-name
            :on-click on-click
            :style {:border "1px solid #6c757d"
                    :border-radius ".35rem"
                    :padding ".6rem 1rem"
                    :font-weight 600
                    :cursor "pointer"
                    :background "var(--bs-body-bg, white)"
                    :color "var(--bs-body-color, #212529)"}}
   label])

(defn make-rng [seed]
  #js {:state (mod seed 2147483647)})

(defn uniform! [rng]
  (let [next-state (mod (* 48271 (.-state rng)) 2147483647)]
    (set! (.-state rng) next-state)
    (/ (dec next-state) 2147483646.0)))

(defn sample-integer-beta!
  "Exact Beta draw for positive integer parameters, using an order statistic."
  [rng alpha beta]
  (->> (repeatedly (dec (+ alpha beta)) #(uniform! rng))
       sort
       (drop (dec alpha))
       first))

(defn sample-low-p-binomial! [rng trials p]
  (let [q (- 1 p)
        u (uniform! rng)]
    (loop [x 0
           probability (js/Math.pow q trials)
           cumulative (js/Math.pow q trials)]
      (if (or (>= cumulative u) (= x trials))
        x
        (let [next-x (inc x)
              next-probability (* probability
                                  (/ (* (- (inc trials) next-x) p)
                                     (* next-x q)))]
          (recur next-x
                 next-probability
                 (+ cumulative next-probability)))))))

(defn sample-binomial! [rng trials p]
  (if (> p 0.5)
    (- trials (sample-low-p-binomial! rng trials (- 1 p)))
    (sample-low-p-binomial! rng trials p)))

(defn sample-stratum! [rng {:keys [pool-size k n alpha beta] :as stratum}]
  (let [p (sample-integer-beta! rng alpha beta)]
    (assoc stratum
           :p p
           :known (+ k (sample-binomial! rng (- pool-size n) p)))))

(defn sample-complete-draw! [rng]
  (let [strata (mapv #(sample-stratum! rng %) worked-sampling-strata)]
    {:strata strata
     :total (reduce + (map :known strata))}))

(defn fresh-sampling-state
  ([] (fresh-sampling-state 20))
  ([rate]
   {:status :idle
    :rate rate
    :draws []
    :latest nil
    :rng (make-rng sampling-seed)}))

(defonce sampling-state (r/atom (fresh-sampling-state)))
(defonce animation-frame-id (atom nil))
(defonce animation-clock #js {:last nil :carry 0})

(defn reset-animation-clock! []
  (set! (.-last animation-clock) nil)
  (set! (.-carry animation-clock) 0))

(defn cancel-animation! []
  (when-let [frame-id @animation-frame-id]
    (js/cancelAnimationFrame frame-id)
    (reset! animation-frame-id nil)))

(declare animation-step!)

(defn schedule-animation! []
  (reset! animation-frame-id (js/requestAnimationFrame animation-step!)))

(defn animation-step! [timestamp]
  (when (= :running (:status @sampling-state))
    (let [{:keys [rate draws rng]} @sampling-state
          previous-time (or (.-last animation-clock) timestamp)
          elapsed (min 250 (- timestamp previous-time))
          budget (+ (.-carry animation-clock) (* elapsed (/ rate 1000.0)))
          remaining (- sampling-target (count draws))
          due (min remaining (long (js/Math.floor budget)))]
      (set! (.-last animation-clock) timestamp)
      (set! (.-carry animation-clock) (- budget due))
      (when (pos? due)
        (let [new-draws (vec (repeatedly due #(sample-complete-draw! rng)))
              all-draws (into draws new-draws)
              complete? (= sampling-target (count all-draws))]
          (swap! sampling-state assoc
                 :draws all-draws
                 :latest (peek all-draws)
                 :status (if complete? :complete :running))))
      (if (= :running (:status @sampling-state))
        (schedule-animation!)
        (reset! animation-frame-id nil)))))

(defn start-sampling! []
  (when (= :complete (:status @sampling-state))
    (let [rate (:rate @sampling-state)]
      (reset! sampling-state (fresh-sampling-state rate))))
  (cancel-animation!)
  (reset-animation-clock!)
  (swap! sampling-state assoc :status :running)
  (schedule-animation!))

(defn pause-sampling! []
  (cancel-animation!)
  (swap! sampling-state assoc :status :paused))

(defn toggle-sampling! []
  (if (= :running (:status @sampling-state))
    (pause-sampling!)
    (start-sampling!)))

(defn reset-sampling! []
  (let [rate (:rate @sampling-state)]
    (cancel-animation!)
    (reset-animation-clock!)
    (reset! sampling-state (fresh-sampling-state rate))))

(defn set-sampling-rate! [rate]
  (swap! sampling-state assoc :rate rate))

(def number-formatter (js/Intl.NumberFormat. "en-US"))

(defn format-count [number]
  (.format number-formatter number))

(defn format-rate [p]
  (str (.toFixed (* 100 p) 1) "%"))

(defn sample-quantile [totals probability]
  (let [ordered (vec (sort totals))
        index (long (js/Math.floor (* probability (dec (count ordered)))))]
    (nth ordered index)))

(defn sampling-summary [draws]
  (when (seq draws)
    (let [totals (mapv :total draws)]
      {:mean (/ (reduce + totals) (count totals))
       :lower (sample-quantile totals 0.025)
       :upper (sample-quantile totals 0.975)})))

(defn x-for-stratum-count [known]
  (+ 132 (* known (/ 500 1000.0))))

(defn x-for-total [total]
  (+ 60 (* total (/ 600 8000.0))))

(defn latest-draw-chart [latest draw-number]
  [:figure.ve-sample-panel
   [:h4 "Newest complete draw"]
   (if latest
     [:div
      [:p.ve-sample-stat
       [:strong (str "Draw " draw-number ": " (format-count (:total latest)) " pairs total")]
       " — eight stratum draws shown below."]
      [:svg {:view-box "0 0 720 356"
             :role "img"
             :aria-labelledby "latest-draw-title latest-draw-desc"}
       [:title#latest-draw-title "Newest complete posterior-predictive draw by stratum"]
       [:desc#latest-draw-desc
        (str "Draw " draw-number " totals " (:total latest)
             " known pairs. Eight rows show the posterior rate and predicted known count for each 1,000-pair stratum.")]
       (for [known [0 250 500 750 1000]
             :let [x (x-for-stratum-count known)]]
         ^{:key known}
         [:g
          [:line.ve-sample-guide {:x1 x :x2 x :y1 28 :y2 305}]
          [:text {:x x :y 329 :text-anchor "middle" :font-size 12 :fill "currentColor"}
           known]])
       (for [{:keys [index p known]} (:strata latest)
             :let [y (+ 27 (* index 34))
                   x (x-for-stratum-count known)]]
         ^{:key index}
         [:g
          [:text {:x 18 :y (+ y 4) :font-size 13 :font-weight 700 :fill "currentColor"}
           (str "S" index)]
          [:line.ve-sample-axis {:x1 132 :x2 632 :y1 y :y2 y}]
          [:line.ve-latest-line {:x1 132 :x2 x :y1 y :y2 y}]
          [:circle.ve-latest-dot {:cx x :cy y :r 6}]
          [:text {:x 704 :y (+ y 4) :text-anchor "end" :font-size 12 :fill "currentColor"}
           (str (format-rate p) " → " known)]])
       [:text {:x 382 :y 350 :text-anchor "middle" :font-size 13 :fill "currentColor"}
        "Known pairs in each 1,000-pair stratum"]]
      [:figcaption.ve-sample-note
       "Each row shows p drawn from that stratum's Beta posterior, then the resulting finite-pool known count (observed correct + simulated untested known)."]]
     [:div.ve-empty-sample
      "Press Start to reveal each complete eight-stratum draw."])])

(def dot-bin-count 80)

(defn dot-positions [draws]
  (let [bin-counts (atom {})]
    (mapv (fn [[index {:keys [total]}]]
            (let [bin (min (dec dot-bin-count)
                           (long (js/Math.floor (* dot-bin-count (/ total 8000.0)))))
                  level (get @bin-counts bin 0)]
              (swap! bin-counts update bin (fnil inc 0))
              {:index index
               :x (x-for-total total)
               :y (- 228 (* level 4.1))}))
          (map-indexed vector draws))))

(defn all-draws-chart [draws]
  (let [summary (sampling-summary draws)
        positions (dot-positions draws)
        draw-count (count draws)]
    [:figure.ve-sample-panel
     [:h4 "Every complete draw used"]
     (if summary
       [:div
        [:p.ve-sample-stat
         [:strong (str draw-count " / " sampling-target " dots")]
         (str " · mean " (format-count (js/Math.round (:mean summary)))
              " · live 95% equal-tail interval "
              (format-count (:lower summary)) "–" (format-count (:upper summary)))]
        [:svg {:view-box "0 0 720 286"
               :role "img"
               :aria-labelledby "all-draws-title all-draws-desc"}
         [:title#all-draws-title "All complete posterior-predictive draws"]
         [:desc#all-draws-desc
          (str draw-count " complete draws are shown, one dot each. Their mean is "
               (js/Math.round (:mean summary)) " and their current 95 percent equal-tail interval is "
               (:lower summary) " to " (:upper summary) ".")]
         (for [total [0 2000 4000 6000 8000]
               :let [x (x-for-total total)]]
           ^{:key total}
           [:g
            [:line.ve-sample-guide {:x1 x :x2 x :y1 22 :y2 232}]
            [:text {:x x :y 258 :text-anchor "middle" :font-size 12 :fill "currentColor"}
             (format-count total)]])
         [:line.ve-sample-axis {:x1 60 :x2 660 :y1 232 :y2 232}]
         (when (> draw-count 1)
           [:line {:x1 (x-for-total (:lower summary))
                   :x2 (x-for-total (:upper summary))
                   :y1 16 :y2 16 :stroke "#2780e3" :stroke-width 8
                   :stroke-linecap "round"}])
         (when (> draw-count 1)
           [:circle {:cx (x-for-total (:mean summary)) :cy 16 :r 6
                     :fill "#c44536" :stroke "white" :stroke-width 1.5}])
         (for [{:keys [index x y]} positions]
           ^{:key index}
           [:circle {:class (if (= index (dec draw-count)) "ve-dot-latest" "ve-dot")
                     :cx x :cy y :r (if (= index (dec draw-count)) 3.8 2.4)}])
         [:text {:x 360 :y 281 :text-anchor "middle" :font-size 13 :fill "currentColor"}
          "Total known pairs in the fixed 8,000-pair pool"]]
        [:figcaption.ve-sample-note
         "One dot is one complete eight-stratum draw. Blue line: live central 95% interval. Red marker: live sample mean. Both stabilize as dots accumulate."]]
       [:div.ve-empty-sample
        "All complete draws will remain visible here, one dot per draw."])]))

(defn rate-button [rate selected-rate]
  [:button {:type "button"
            :class (str "ve-sampling-button ve-rate-" rate)
            :aria-pressed (= rate selected-rate)
            :on-click #(set-sampling-rate! rate)}
   (str rate "/s")])

(defn sampling-status-text [status draw-count]
  (case status
    :running (str "Running: " draw-count " of " sampling-target " complete draws.")
    :paused (str "Paused after " draw-count " of " sampling-target " complete draws.")
    :complete (str "Complete: all " sampling-target " draws are visible.")
    (str "Ready: 0 of " sampling-target " complete draws.")))

(defn posterior-sampling-simulator []
  (let [{:keys [status rate draws latest]} @sampling-state
        draw-count (count draws)
        running? (= status :running)]
    [:section.ve-posterior-sampler {:aria-labelledby "posterior-sampler-heading"}
     [:div.ve-sampling-intro
      [:h3#posterior-sampler-heading {:style {:margin-top 0}}
       "Posterior-predictive sampling, one complete draw at a time"]
      [:p
       "The deterministic seed fixes the sequence. Speed changes only how quickly the same 500 complete draws appear."]]
     [:div.ve-sampling-controls
      [:fieldset.ve-rate-fieldset
       [:legend "Complete samples per second"]
       [:div.ve-button-row
        [rate-button 10 rate]
        [rate-button 20 rate]
        [rate-button 50 rate]]]
      [:div.ve-button-row
       [:button {:type "button"
                 :class "ve-sampling-button ve-primary ve-sampling-toggle"
                 :on-click toggle-sampling!}
        (cond
          running? "Pause"
          (= status :complete) "Run again"
          :else "Start")]
       [:button {:type "button"
                 :class "ve-sampling-button ve-sampling-reset"
                 :on-click reset-sampling!}
        "Reset"]]]
     [:progress.ve-sampling-progress {:value draw-count :max sampling-target
                                      :aria-label "Posterior-predictive samples completed"}]
     [:p.ve-sampling-status {:aria-live (if running? "off" "polite")}
      (sampling-status-text status draw-count)]
     [:div.ve-sampling-grid
      [latest-draw-chart latest draw-count]
      [all-draws-chart draws]]]))

(defn simulator []
  (let [{:keys [alpha beta responses] :as current} @state
        n (count responses)
        k (count (filter #{:correct} responses))
        posterior-mean (/ alpha (+ alpha beta))]
    [:section {:aria-labelledby "simulator-heading"
               :style {:border "1px solid #ced4da"
                       :border-radius ".5rem"
                       :padding "clamp(.75rem, 3vw, 1.25rem)"
                       :min-width 0}}
     [:h3#simulator-heading {:style {:margin-top 0}} "One-stratum update simulator"]
     [:p {:aria-live "polite"}
      [:strong (str "Observed: " k " correct of " n ". ")]
      (str "Current posterior Beta(" alpha "," beta "); mean knowing rate "
           (.toFixed posterior-mean 3) ".")]
     [density-chart current]
     [:p {:style {:font-size ".85rem"
                  :color "var(--bs-body-color, #212529)"
                  :opacity 0.75
                  :margin ".35rem 0 0"}}
      "The Beta curves are normalized densities on one shared scale. The response likelihood uses its natural 0–1 scale; compare its shape, not its height, with the densities."]
     [:div {:style {:display "flex" :gap ".65rem" :flex-wrap "wrap" :margin-top "1rem"}}
      [control-button "Correct" #(record-response! :correct) "ve-correct"]
      [control-button "Don't know" #(record-response! :dont-know) "ve-dont-know"]
      [control-button "Reset" #(reset! state initial-state) "ve-reset"]]
     [:p {:style {:font-size ".9rem"
                  :color "var(--bs-body-color, #212529)"
                  :opacity 0.75
                  :margin-bottom 0}}
      "Keyboard: Tab to a button, then press Enter or Space. “Don't know” contributes the same stage-one likelihood as a wrong answer, while the raw value remains distinct."]]))

(defn ^:export mount []
  (when-let [root (js/document.getElementById "beta-binomial-simulator")]
    (rdom/render [simulator] root))
  (when-let [root (js/document.getElementById "posterior-sampling-simulator")]
    (rdom/render [posterior-sampling-simulator] root)))

(if (= "loading" js/document.readyState)
  (.addEventListener js/document "DOMContentLoaded" mount)
  (mount))
