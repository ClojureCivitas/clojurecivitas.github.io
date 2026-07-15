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
    :wrong (- 1 p)
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
    :wrong "wrong"
    :dont-know "don't know"
    "none yet"))

(defn density-chart
  [{:keys [alpha beta previous last-response]}]
  (let [[previous-alpha previous-beta] previous
        curves [{:label "Prior Beta(1,1)"
                 :color "var(--ve-muted)"
                 :dash "6 5"
                 :f #(beta-density 1 1 %)}
                {:label (str "Previous Beta(" previous-alpha "," previous-beta ")")
                 :color "var(--ve-purple)"
                 :dash "3 4"
                 :f #(beta-density previous-alpha previous-beta %)}
                {:label (str "Last-response likelihood (" (response-label last-response) ")")
                 :color "var(--ve-warm)"
                 :dash "8 4"
                 :f #(likelihood last-response %)}
                {:label (str "Current Beta(" alpha "," beta ")")
                 :color "var(--ve-accent)"
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
               :stroke-width (if (= color "var(--ve-accent)") 4 2.5)
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

(def balanced-response-sequence
  [:correct :wrong :dont-know :correct :correct :dont-know :wrong :correct
   :dont-know :correct :wrong :wrong :correct :dont-know :correct :correct])

(def balanced-selection-limit 16)
(defonce balanced-state (r/atom {:revealed 0}))

(defn scheduled-selection [index]
  (let [stratum (inc (mod index 8))
        round (inc (quot index 8))]
    {:stratum stratum
     :round round
     :item-id (str "S" stratum "-R" round)
     :response (nth balanced-response-sequence index)}))

(defn reveal-next-selection! []
  (swap! balanced-state update :revealed
         #(min balanced-selection-limit (inc %))))

(defn reset-balanced-rounds! []
  (reset! balanced-state {:revealed 0}))

(defn balanced-round-simulator []
  (let [revealed (:revealed @balanced-state)
        selections (mapv scheduled-selection (range revealed))
        latest (peek selections)
        complete? (= revealed balanced-selection-limit)]
    [:section.ve-round-shell {:aria-labelledby "balanced-round-heading"}
     [:h3#balanced-round-heading "Balanced non-adaptive rounds"]
     [:p
      "Two fixed demonstration rounds are queued. Each click reveals the next scheduled item and its sample response."]
     [:div.ve-round-grid
      (for [stratum (range 1 9)
            :let [seen (filterv #(= stratum (:stratum %)) selections)
                  most-recent (peek seen)
                  next-round (inc (count seen))]]
        ^{:key stratum}
        [:div.ve-round-card
         [:strong (str "Stratum " stratum)]
         [:span (if most-recent
                  (str "Latest: " (:item-id most-recent)
                       " · " (response-label (:response most-recent)))
                  "Latest: none")]
         [:small (if (< next-round 3)
                   (str "Next queued: S" stratum "-R" next-round)
                   "Two demonstration items used")]])]
     [:progress.ve-round-progress
      {:value revealed :max balanced-selection-limit
       :aria-label "Scheduled demonstration items revealed"}]
     [:p.ve-round-status
      {:aria-live "polite"}
      (cond
        complete? "Two complete rounds: every stratum supplied two unseen items."
        latest (str "Revealed " (:item-id latest) " as "
                    (response-label (:response latest))
                    ". The response did not alter any next-item queue.")
        :else "Ready. No response has altered the fixed queues.")]
     [:div.ve-button-row
      [:button.ve-sampling-button.ve-primary
       {:type "button" :on-click reveal-next-selection! :disabled complete?}
       (if complete? "Two rounds complete" "Reveal next scheduled item")]
      [:button.ve-sampling-button
       {:type "button" :on-click reset-balanced-rounds!}
       "Reset"]]
     [:p.ve-sample-note
      "The response labels are illustrative. Schedule order is S1 through S8 in every round, independent of all answers."]]))

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
  (let [p (sample-integer-beta! rng alpha beta)
        untested (- pool-size n)
        predicted-untested (sample-binomial! rng untested p)]
    (assoc stratum
           :p p
           :tested-correct k
           :tested-not-correct (- n k)
           :untested untested
           :predicted-untested predicted-untested
           :known (+ k predicted-untested))))

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
      [:div.ve-draw-breakdown-wrap
       [:table.ve-draw-breakdown
        [:caption.ve-sr-only
         "Newest posterior-predictive draw split into tested correct, tested not-correct, predicted untested, and total known pairs"]
        [:thead
         [:tr
          [:th {:scope "col"} "Stratum"]
          [:th {:scope "col"} "Tested correct"]
          [:th {:scope "col"} "Tested not-correct"]
          [:th {:scope "col"} "Predicted untested"]
          [:th {:scope "col"} "Known total"]]]
        [:tbody
         (for [{:keys [index tested-correct tested-not-correct
                       predicted-untested known]} (:strata latest)]
           ^{:key index}
           [:tr
            [:th {:scope "row"} (str "S" index)]
            [:td tested-correct]
            [:td tested-not-correct]
            [:td predicted-untested]
            [:td known]])]]]
      [:figcaption.ve-sample-note
       "Tested-correct pairs are added once. Tested-not-correct pairs remain zero. Only the 996 untested pairs receive a predictive draw."]]
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

(def authoritative-stopping-settings
  {:minimum 32
   :target-percent 10
   :soft-cap 96})

(def initial-stopping-state
  (merge authoritative-stopping-settings
         {:items-tested 40
          :half-width 750}))

(defonce stopping-state (r/atom initial-stopping-state))

(defn event-number [event]
  (js/Number (.. event -target -value)))

(defn update-stopping-setting! [key event]
  (swap! stopping-state assoc key (event-number event)))

(defn reset-stopping-settings! []
  (reset! stopping-state initial-stopping-state))

(defn explorer-stopping-check
  [{:keys [minimum target-percent soft-cap items-tested half-width]}]
  (let [complete-round? (zero? (mod items-tested 8))
        assess? (and complete-round? (>= items-tested minimum))
        target-count (* (/ target-percent 100.0) 8000)
        target? (and assess? (<= half-width target-count))
        soft-max? (and assess? (>= items-tested soft-cap))]
    {:complete-round? complete-round?
     :assess? assess?
     :target-count target-count
     :target? target?
     :soft-max? soft-max?
     :stop? (or target? soft-max?)}))

(defn stopping-range
  [{:keys [id label value min max step suffix on-change]}]
  [:div.ve-stop-field
   [:div
    [:label {:for id} label]
    [:output {:for id} (str value suffix)]]
   [:input {:id id :type "range" :value value :min min :max max :step step
            :on-input on-change
            :on-change on-change}]])

(defn stopping-rule-explorer []
  (let [{:keys [minimum target-percent soft-cap items-tested half-width]
         :as settings} @stopping-state
        {:keys [complete-round? assess? target-count target? soft-max? stop?]}
        (explorer-stopping-check settings)
        observed-percent (* 100.0 (/ half-width 8000))
        defaults? (= authoritative-stopping-settings
                     (select-keys settings [:minimum :target-percent :soft-cap]))
        decision (cond
                   (not complete-round?) "Not assessed: this is not an eight-item round boundary."
                   (< items-tested minimum) "Not assessed: the selected minimum has not been reached."
                   (and target? soft-max?) "Recommend stop: both precision target and soft cap are met."
                   target? "Recommend stop: the precision target is met."
                   soft-max? "Recommend stop: the soft cap is reached."
                   :else "Continue: assessed, but neither stopping condition is met.")]
    [:section.ve-stop-shell {:aria-labelledby "stopping-explorer-heading"}
     [:h3#stopping-explorer-heading "Teaching-only stopping-rule explorer"]
     [:div {:class (str "ve-stop-banner "
                        (if defaults? "is-default" "is-counterfactual"))}
      [:strong (if defaults?
                 "Authoritative v1 defaults"
                 "Counterfactual teaching settings")]
      [:span (if defaults?
               " Minimum 32 · target 10% · soft cap 96."
               " These controls do not rewrite the article's v1 rule.")]]
     [:div.ve-stop-grid
      [stopping-range {:id "stop-minimum" :label "Minimum items"
                       :value minimum :min 8 :max 64 :step 8 :suffix ""
                       :on-change #(update-stopping-setting! :minimum %)}]
      [stopping-range {:id "stop-target" :label "Interval half-width target"
                       :value target-percent :min 5 :max 25 :step 1 :suffix "% of pool"
                       :on-change #(update-stopping-setting! :target-percent %)}]
      [stopping-range {:id "stop-cap" :label "Soft cap"
                       :value soft-cap :min 64 :max 160 :step 8 :suffix " items"
                       :on-change #(update-stopping-setting! :soft-cap %)}]
      [stopping-range {:id "stop-items" :label "Observed items"
                       :value items-tested :min 8 :max 160 :step 4 :suffix ""
                       :on-change #(update-stopping-setting! :items-tested %)}]
      [stopping-range {:id "stop-width" :label "Observed interval half-width"
                       :value half-width :min 200 :max 2000 :step 50 :suffix " pairs"
                       :on-change #(update-stopping-setting! :half-width %)}]]
     [:div.ve-stop-result {:aria-live "polite"}
      [:strong decision]
      [:ul
       [:li (str "Complete round: " (if complete-round? "yes" "no"))]
       [:li (str "Eligible to assess: " (if assess? "yes" "no"))]
       [:li (str "Precision threshold: ±" (js/Math.round target-count)
                 " pairs (" target-percent "% of pool).")]
       [:li (str "Observed precision: ±" half-width " pairs ("
                 (.toFixed observed-percent 1) "% of pool).")]
       [:li (str "Soft cap reached: " (if soft-max? "yes" "no"))]]]
     [:div.ve-button-row
      [:button.ve-sampling-button
       {:type "button" :on-click reset-stopping-settings!}
       "Reset v1 defaults"]]
     [:p.ve-sample-note
      "Voluntary stopping remains available in every scenario and is intentionally outside this statistical recommendation."]]))

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
      [control-button "Wrong" #(record-response! :wrong) "ve-wrong"]
      [control-button "Don't know" #(record-response! :dont-know) "ve-dont-know"]
      [control-button "Reset" #(reset! state initial-state) "ve-reset"]]
     [:p {:style {:font-size ".9rem"
                  :color "var(--bs-body-color, #212529)"
                  :opacity 0.75
                  :margin-bottom 0}}
      "Keyboard: Tab to a button, then press Enter or Space. Wrong and “don't know” contribute the same stage-one likelihood, while their raw values remain distinct."]]))

(defn ^:export mount []
  (when-let [root (js/document.getElementById "balanced-round-simulator")]
    (rdom/render [balanced-round-simulator] root))
  (when-let [root (js/document.getElementById "beta-binomial-simulator")]
    (rdom/render [simulator] root))
  (when-let [root (js/document.getElementById "posterior-sampling-simulator")]
    (rdom/render [posterior-sampling-simulator] root))
  (when-let [root (js/document.getElementById "stopping-rule-explorer")]
    (rdom/render [stopping-rule-explorer] root)))

(if (= "loading" js/document.readyState)
  (.addEventListener js/document "DOMContentLoaded" mount)
  (mount))
