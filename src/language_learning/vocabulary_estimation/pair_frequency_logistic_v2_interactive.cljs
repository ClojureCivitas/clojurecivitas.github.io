(ns language-learning.vocabulary-estimation.pair-frequency-logistic-v2-interactive
  (:require [language-learning.vocabulary-estimation.pair-frequency-logistic-v2 :as v2]
            [reagent.core :as r]
            [reagent.dom :as rdom]))

(defn parse-json-element [id keywordize?]
  (when-let [element (.getElementById js/document id)]
    (js->clj (js/JSON.parse (.-textContent element))
             :keywordize-keys keywordize?)))

(def xs (or (parse-json-element "pair-frequency-xs" false) []))
(def gate-data (parse-json-element "pair-frequency-gate-data" true))

(def number-format (js/Intl.NumberFormat. "en-US"))
(defn format-count [number] (.format number-format (js/Math.round number)))
(defn parse-number [event] (js/Number (.. event -target -value)))

(defonce curve-state (r/atom {:threshold 0.0 :width 2.0}))

(def curve-x-min -1.2)
(def curve-x-max 7.2)
(defn curve-px [x]
  (+ 48 (* (- x curve-x-min) (/ 590.0 (- curve-x-max curve-x-min)))))

(defn curve-path [threshold width]
  (->> (range 101)
       (map (fn [index]
              (let [x (+ curve-x-min
                         (* index (/ (- curve-x-max curve-x-min) 100.0)))
                    probability (v2/knowledge-probability x threshold width)
                    px (curve-px x)
                    py (- 210 (* probability 160))]
                (str (if (zero? index) "M" "L") px " " py))))
       (clojure.string/join " ")))

(defn field [id label value minimum maximum step on-input]
  [:div.pf-control
   [:label {:for id} label ": " [:strong (.toFixed value 2)]]
   [:input {:id id :type "range" :value value :min minimum :max maximum
            :step step :on-input on-input :on-change on-input}]])

(defn curve-explorer []
  (let [{:keys [threshold width]} @curve-state
        total (v2/expected-total xs threshold width)
        anchors [{:x (- threshold (/ width 2.0))
                  :probability 0.1 :label "10%, odds 1 to 9"}
                 {:x threshold :probability 0.5 :label "50%, odds 1 to 1"}
                 {:x (+ threshold (/ width 2.0))
                  :probability 0.9 :label "90%, odds 9 to 1"}]]
    [:div
     [:div.pf-controls
      [field "curve-threshold" "Threshold t" threshold -2.5 3.0 0.05
       #(swap! curve-state assoc :threshold (parse-number %))]
      [field "curve-width" "10%–90% width w" width 0.25 5.0 0.25
       #(swap! curve-state assoc :width (parse-number %))]]
     [:p.pf-live {:aria-live "polite"}
      "Expected known total in this fixture: " [:strong (format-count total)]
      " of 8,000 pairs."]
     [:p.pf-note
      "Annotated dots mark t − w/2 (10%), t (50%), and t + w/2 (90%) when those positions are inside the displayed predictor range."]
     [:svg {:view-box "0 0 680 270" :role "img"
            :aria-labelledby "curve-title curve-desc"}
      [:title#curve-title "Frequency predictor and knowing probability"]
      [:desc#curve-desc
       (str "A logistic curve with threshold " (.toFixed threshold 2)
            ", width " (.toFixed width 2) ", and expected total "
            (format-count total)
            ". Dots identify the ten, fifty, and ninety percent anchor points when visible.")]
      [:line {:x1 48 :x2 638 :y1 210 :y2 210 :stroke "currentColor"}]
      [:line {:x1 48 :x2 48 :y1 50 :y2 210 :stroke "currentColor"}]
      (for [[probability label] [[0 "0"] [0.5 ".5"] [1 "1"]]
            :let [y (- 210 (* probability 160))]]
        ^{:key label}
        [:g [:line {:x1 41 :x2 48 :y1 y :y2 y :stroke "currentColor"}]
         [:text {:x 35 :y (+ y 5) :text-anchor "end" :font-size 13
                 :fill "currentColor"} label]])
      [:text {:x 14 :y 130 :text-anchor "middle" :font-size 14
              :transform "rotate(-90 14 130)" :fill "currentColor"}
       "Knowing probability pᵢ"]
      [:path {:d (curve-path threshold width) :fill "none"
              :stroke "var(--pf-accent,#2780e3)"
              :stroke-width 4 :vector-effect "non-scaling-stroke"}]
      (for [{:keys [x probability label]} anchors
            :when (<= curve-x-min x curve-x-max)
            :let [px (curve-px x)
                  py (- 210 (* probability 160))]]
        ^{:key label}
        [:g
         [:line {:x1 px :x2 px :y1 py :y2 210
                 :stroke "var(--pf-warn,#8a5000)"
                 :stroke-width 1.5 :stroke-dasharray "4 4"}]
         [:circle {:cx px :cy py :r 6
                   :fill "var(--pf-warn,#8a5000)"
                   :stroke "var(--bs-body-bg,#fff)" :stroke-width 2}
          [:title label]]])
      [:text {:x 343 :y 250 :text-anchor "middle" :font-size 14
              :fill "currentColor"} "Standardized log₁₀ pair frequency x"]]]))

(def bounded-grid
  {:threshold-points 41 :width-points 21
   :minimum-width 0.25 :maximum-width 8.0})

(defonce lab-state
  (r/atom {:seed 20260713 :threshold -0.19 :width 1.5
           :status :idle :result nil}))
(defonce lab-posterior-view (r/atom :current))

(def response-demo-limit 16)
(defonce response-demo-state (r/atom {:observations []}))
(defonce response-heatmap-view (r/atom :current))
(def joint-heatmap-mass-ceiling 0.10)

(defn response-demo-label [response]
  (case response
    :correct "correct"
    :wrong "wrong"
    :dont-know "don't know"
    "none"))

(defn response-demo-item [index]
  (let [stratum (inc (mod index 8))
        round (inc (quot index 8))
        within-stratum-index (if (= round 1) 249 749)
        pair-index (+ (* (dec stratum) 1000) within-stratum-index)]
    {:stratum stratum
     :round round
     :item-id (str "S" stratum "-R" round)
     :pair-index pair-index
     :pair-frequency-rank (inc pair-index)
     :x (nth xs pair-index)}))

(defn record-response-demo! [response]
  (swap! response-demo-state update :observations
         (fn [observations]
           (if (< (count observations) response-demo-limit)
             (conj observations
                   (assoc (response-demo-item (count observations))
                          :response response))
             observations))))

(defn reset-response-demo! []
  (reset! response-demo-state {:observations []})
  (reset! response-heatmap-view :current))

(defn response-demo-posterior [observations]
  (if (seq observations)
    (v2/posterior-grid xs observations bounded-grid v2/default-prior)
    (v2/prior-grid xs bounded-grid v2/default-prior)))

(defn posterior-marginal [posterior parameter]
  (->> posterior
       (group-by parameter)
       (map (fn [[value entries]]
              {:value value
               :weight (reduce + 0.0 (map :weight entries))}))
       (sort-by :value)
       vec))

(defn heatmap-view-label [view]
  (case view
    :prior "Prior"
    :previous "Previous posterior"
    :current "Current posterior"))

(defn joint-heat-opacity [weight]
  (let [ratio (min 1.0 (/ weight joint-heatmap-mass-ceiling))]
    (+ 0.04 (* 0.96 (js/Math.sqrt ratio)))))

(defn posterior-joint-heatmap
  [id title prior previous current requested-view on-view-change]
  (let [heading-id (str id "-heading")
        title-id (str id "-title")
        description-id (str id "-description")
        view (if (and (= requested-view :previous) (nil? previous))
               :current
               requested-view)
        posterior (case view
                    :prior prior
                    :previous previous
                    :current current)
        view-label (heatmap-view-label view)
        thresholds (->> posterior (map :threshold) distinct sort vec)
        widths (->> posterior (map :width) distinct sort vec)
        threshold-index (zipmap thresholds (range))
        width-index (zipmap widths (range))
        threshold-count (count thresholds)
        width-count (count widths)
        plot-x 76.0
        plot-y 24.0
        plot-width 528.0
        plot-height 252.0
        cell-width (/ plot-width threshold-count)
        cell-height (/ plot-height width-count)
        maximum-cell (apply max-key :weight posterior)
        maximum-x (+ plot-x
                     (* (+ (get threshold-index (:threshold maximum-cell)) 0.5)
                        cell-width))
        maximum-y (+ plot-y
                     (* (+ (- (dec width-count)
                              (get width-index (:width maximum-cell)))
                           0.5)
                        cell-height))
        threshold-ticks [(first thresholds)
                         (nth thresholds (quot threshold-count 2))
                         (peek thresholds)]
        width-ticks [(first widths)
                     (nth widths (quot width-count 2))
                     (peek widths)]]
    [:section.pf-joint-posterior-panel
     {:aria-labelledby heading-id}
     [:div.pf-joint-posterior-header
      [:h4 {:id heading-id} title]
      [:div.pf-heatmap-view
       {:role "group" :aria-label (str "Heatmap view for " title)}
       (for [{:keys [key label]}
             [{:key :prior :label "Prior"}
              {:key :previous :label "Previous"}
              {:key :current :label "Current"}]]
         ^{:key key}
         [:button
          {:type "button"
           :class (when (= key view) "is-selected")
           :aria-pressed (= key view)
           :disabled (and (= key :previous) (nil? previous))
           :on-click #(on-view-change key)}
          label])]]
     [:svg {:view-box "0 0 640 340" :role "img"
            :aria-labelledby (str title-id " " description-id)}
      [:title {:id title-id}
       (str view-label " over threshold and width")]
      [:desc {:id description-id}
       (str view-label " probability mass over the 41 by 21 threshold-and-width grid. "
            "The maximum-probability cell is at threshold "
            (.toFixed (:threshold maximum-cell) 2) " and width "
            (.toFixed (:width maximum-cell) 2) ".")]
      (for [{:keys [threshold width weight]} posterior
            :let [column (get threshold-index threshold)
                  row (get width-index width)
                  x (+ plot-x (* column cell-width))
                  y (+ plot-y (* (- (dec width-count) row) cell-height))]]
        ^{:key (str threshold "-" width)}
        [:rect {:x x :y y
                :width (+ cell-width 0.15) :height (+ cell-height 0.15)
                :fill "var(--pf-accent,#1464b5)"
                :fill-opacity (joint-heat-opacity weight)
                :aria-hidden "true"}])
      [:rect {:x plot-x :y plot-y :width plot-width :height plot-height
              :fill "none" :stroke "currentColor" :stroke-opacity 0.55
              :vector-effect "non-scaling-stroke"}]
      (for [threshold threshold-ticks
            :let [x (+ plot-x
                       (* (+ (get threshold-index threshold) 0.5)
                          cell-width))]]
        ^{:key threshold}
        [:g
         [:line {:x1 x :x2 x :y1 (+ plot-y plot-height)
                 :y2 (+ plot-y plot-height 7) :stroke "currentColor"}]
         [:text {:x x :y 298 :text-anchor "middle" :font-size 14
                 :fill "currentColor"}
          (.toFixed threshold 2)]])
      (for [width width-ticks
            :let [y (+ plot-y
                       (* (+ (- (dec width-count) (get width-index width)) 0.5)
                          cell-height))]]
        ^{:key width}
        [:g
         [:line {:x1 (- plot-x 7) :x2 plot-x :y1 y :y2 y
                 :stroke "currentColor"}]
         [:text {:x (- plot-x 11) :y (+ y 5) :text-anchor "end"
                 :font-size 14 :fill "currentColor"}
          (.toFixed width 2)]])
      [:circle {:cx maximum-x :cy maximum-y :r 8
                :fill "none" :stroke "var(--bs-body-bg,#fff)"
                :stroke-width 6 :vector-effect "non-scaling-stroke"}]
      [:circle {:cx maximum-x :cy maximum-y :r 8
                :fill "none" :stroke "var(--bs-body-color,#212529)"
                :stroke-width 2.5 :vector-effect "non-scaling-stroke"}]
      [:text {:x (+ plot-x (/ plot-width 2.0)) :y 326
              :text-anchor "middle" :font-size 15 :fill "currentColor"}
       "Standardized-frequency threshold t"]
      [:text {:x 18 :y (+ plot-y (/ plot-height 2.0))
              :text-anchor "middle" :font-size 15
              :transform (str "rotate(-90 18 "
                              (+ plot-y (/ plot-height 2.0)) ")")
              :fill "currentColor"}
       "10%–90% transition width w"]]
     [:div.pf-heatmap-scale
      {:role "img"
       :aria-label "Fixed probability-mass colour scale from zero to 0.10 or more"}
      [:span "0"]
      [:span.pf-heatmap-ramp {:aria-hidden "true"}
       (for [index (range 11)]
         ^{:key index}
         [:span {:style {:opacity (joint-heat-opacity (/ index 100.0))}}])]
      [:span "0.10+"]
      [:strong "Probability mass"]]
     [:p.pf-heatmap-status {:aria-live "polite"}
      [:strong "Maximum-probability cell: "]
      (str "t = " (.toFixed (:threshold maximum-cell) 2)
           ", w = " (.toFixed (:width maximum-cell) 2)
           ", mass = " (.toFixed (* 100.0 (:weight maximum-cell)) 2) "%.")]
     [:p.pf-note
      "Colour uses a fixed probability-mass scale from 0 to 0.10 across all responses and views; cells at or above 0.10 share the darkest colour."]]))

(defn marginal-path [entries minimum maximum maximum-weight]
  (->> entries
       (map-indexed
        (fn [index {:keys [value weight]}]
          (let [x (+ 48 (* (/ (- value minimum) (- maximum minimum)) 442.0))
                y (- 174 (* (/ weight maximum-weight) 130.0))]
            (str (if (zero? index) "M" "L") x " " y))))
       (clojure.string/join " ")))

(defn posterior-marginal-chart
  [id title axis-label parameter prior previous current]
  (let [prior-marginal (posterior-marginal prior parameter)
        previous-marginal (when previous
                            (posterior-marginal previous parameter))
        current-marginal (posterior-marginal current parameter)
        all-marginals (remove nil? [prior-marginal previous-marginal
                                    current-marginal])
        minimum (:value (first prior-marginal))
        maximum (:value (peek prior-marginal))
        midpoint (/ (+ minimum maximum) 2.0)
        maximum-weight (apply max (map :weight (mapcat identity all-marginals)))
        series [{:key :prior :label "Prior" :entries prior-marginal
                 :color "var(--pf-muted,#4f5b66)" :dash "8 6"}
                {:key :previous :label "Previous posterior"
                 :entries previous-marginal
                 :color "var(--pf-warn,#8a5000)" :dash "3 5"}
                {:key :current :label "Current posterior"
                 :entries current-marginal
                 :color "var(--pf-accent,#1464b5)" :dash nil}]]
    [:section.pf-posterior-panel
     [:h4 {:id (str id "-heading")} title]
     [:svg {:view-box "0 0 540 230" :role "img"
            :aria-labelledby (str id "-heading " id "-description")}
      [:desc {:id (str id "-description")}
       (str title ". "
            (if previous
              "Prior, previous posterior, and current posterior are "
              "Prior and current posterior are ")
            "shown on one shared relative-mass scale.")]
      [:line {:x1 48 :x2 490 :y1 174 :y2 174 :stroke "currentColor"}]
      [:line {:x1 48 :x2 48 :y1 44 :y2 174 :stroke "currentColor"}]
      (for [{:keys [key entries color dash]} series
            :when entries]
        ^{:key key}
        [:path {:d (marginal-path entries minimum maximum maximum-weight)
                :fill "none" :stroke color :stroke-width 3
                :stroke-dasharray dash :vector-effect "non-scaling-stroke"}])
      (for [[value label] [[minimum (.toFixed minimum 2)]
                           [midpoint (.toFixed midpoint 2)]
                           [maximum (.toFixed maximum 2)]]
            :let [x (+ 48 (* (/ (- value minimum) (- maximum minimum)) 442.0))]]
        ^{:key label}
        [:g
         [:line {:x1 x :x2 x :y1 174 :y2 181 :stroke "currentColor"}]
         [:text {:x x :y 197 :text-anchor "middle" :font-size 12
                 :fill "currentColor"} label]])
      [:text {:x 269 :y 220 :text-anchor "middle" :font-size 13
              :fill "currentColor"} axis-label]
      [:text {:x 15 :y 109 :text-anchor "middle" :font-size 12
              :transform "rotate(-90 15 109)" :fill "currentColor"}
       "Relative marginal mass"]]
     [:div.pf-posterior-legend {:aria-hidden "true"}
      (for [{:keys [key label color dash entries]} series
            :when entries]
        ^{:key key}
        [:span
         [:svg {:view-box "0 0 34 8"}
          [:line {:x1 1 :x2 33 :y1 4 :y2 4 :stroke color
                  :stroke-width 3 :stroke-dasharray dash}]]
         label])]]))

(defn posterior-parameter-summary [posterior parameter]
  {:mean (v2/weighted-mean posterior parameter)
   :lower (v2/weighted-quantile posterior parameter 0.025)
   :upper (v2/weighted-quantile posterior parameter 0.975)})

(defn response-inference-simulator []
  (let [observations (:observations @response-demo-state)
        observed (count observations)
        prior (response-demo-posterior [])
        previous (when (seq observations)
                   (response-demo-posterior (pop observations)))
        current (response-demo-posterior observations)
        latest (peek observations)
        response-counts (frequencies (map :response observations))
        threshold-summary (posterior-parameter-summary current :threshold)
        width-summary (posterior-parameter-summary current :width)
        heatmap-view @response-heatmap-view
        next-selection (when (< observed response-demo-limit)
                         (response-demo-item observed))
        complete? (= observed response-demo-limit)]
    [:section.pf-response-shell
     {:aria-labelledby "response-inference-heading"}
     [:h3#response-inference-heading "Learner responses update the v2 posterior"]
     [:p
      "Answer the next item in a fixed two-round schedule. Each response "
      "reweights the joint threshold-and-width grid; it never changes which "
      "item comes next."]
     [:div.pf-response-summary
      [:div.pf-response-effect
       [:strong "Observed responses"]
       [:span (str observed " of " response-demo-limit " · "
                   (get response-counts :correct 0) " correct · "
                   (get response-counts :wrong 0) " wrong · "
                   (get response-counts :dont-know 0) " don't know")]
       [:small (if latest
                 (str "Latest: " (:item-id latest) " · "
                      (response-demo-label (:response latest)) " · likelihood "
                      (if (= :correct (:response latest)) "pᵢ" "1 − pᵢ"))
                 "The current posterior initially equals the prior.")]]
      [:div.pf-response-effect.is-fixed
       [:strong "Next fixed item"]
       [:span (if next-selection
                (str (:item-id next-selection) " · rank "
                     (:pair-frequency-rank next-selection) " · xᵢ = "
                     (.toFixed (:x next-selection) 3))
                "Both fixed rounds complete")]
       [:small "The queue was fixed before any response was recorded."]]]
     [:progress.pf-response-progress
      {:value observed :max response-demo-limit
       :aria-label "Fixed-schedule responses recorded"}]
     [:p.pf-response-status
      {:aria-live "polite"}
      (str "Current posterior: mean threshold t = "
           (.toFixed (:mean threshold-summary) 2) " (95% interval "
           (.toFixed (:lower threshold-summary) 2) " to "
           (.toFixed (:upper threshold-summary) 2) "); mean width w = "
           (.toFixed (:mean width-summary) 2) " (95% interval "
           (.toFixed (:lower width-summary) 2) " to "
           (.toFixed (:upper width-summary) 2) ").")]
     [posterior-joint-heatmap
      "response-joint-posterior" "Joint threshold × width posterior"
      prior previous current heatmap-view
      #(reset! response-heatmap-view %)]
     [:div.pf-posterior-grid
      [posterior-marginal-chart
       "threshold-posterior-chart" "Threshold t posterior"
       "Standardized-frequency threshold t" :threshold
       prior previous current]
      [posterior-marginal-chart
       "width-posterior-chart" "Width w posterior"
       "10%–90% transition width w" :width
       prior previous current]]
     [:div.pf-response-actions
      [:button.pf-response-button.is-primary
       {:type "button" :on-click #(record-response-demo! :correct)
        :disabled complete?}
       "Correct"]
      [:button.pf-response-button
       {:type "button" :on-click #(record-response-demo! :wrong)
        :disabled complete?}
       "Wrong"]
      [:button.pf-response-button
       {:type "button" :on-click #(record-response-demo! :dont-know)
        :disabled complete?}
       "Don't know"]
      [:button.pf-response-button
       {:type "button" :on-click reset-response-demo!}
       "Reset"]]
     [:p.pf-note
      "The heatmap shows all 861 cells of the joint 41×21 browser grid; the charts below are its one-dimensional marginals. Wrong and don't-know remain distinct raw events but both contribute 1 − pᵢ to this v2 binary likelihood. The authoritative scorer uses the denser configured grid."]]))

(defn browser-pairs []
  (mapv (fn [index]
          {:pair-index index :pair-frequency-rank (inc index)
           :lemma-id index :surface-form-id index})
        (range 8000)))

(defn raw-responses [binary-observations]
  (mapv (fn [index observation]
          (if (= :correct (:response observation))
            observation
            (assoc observation :response (if (even? index) :wrong :dont-know))))
        (range) binary-observations))

(defn v1-summary [selected observations]
  (let [counts
        (reduce (fn [result [item observation]]
                  (-> result
                      (update-in [(:stratum-index item) :n] (fnil inc 0))
                      (update-in [(:stratum-index item) :k] (fnil + 0)
                                 (v2/collapse-response (:response observation)))))
                {} (map vector selected observations))
        moments
        (for [stratum (range 8)
              :let [{:keys [k n]} (get counts stratum {:k 0 :n 0})
                    alpha (+ 1.0 k)
                    beta (+ 1.0 (- n k))
                    untested (- 1000 n)]]
          {:mean (+ k (* untested (/ alpha (+ alpha beta))))
           :variance (/ (* untested alpha beta (+ alpha beta untested))
                        (* (+ alpha beta) (+ alpha beta) (+ alpha beta 1.0)))})
        mean (reduce + (map :mean moments))
        sd (js/Math.sqrt (reduce + (map :variance moments)))]
    {:mean mean
     :lower (max 0 (- mean (* 1.96 sd)))
     :upper (min 8000 (+ mean (* 1.96 sd)))}))

(def model-bin-count 64)
(def model-bin-size (quot 8000 model-bin-count))
(def model-chart-width 1000.0)
(def model-grid
  {:threshold-points 41 :width-points 21
   :minimum-width 0.25 :maximum-width 8.0})

(defn draw-latent-outcomes [probabilities seed]
  (loop [remaining (seq probabilities)
         state (v2/normalize-seed seed)
         outcomes []]
    (if-not remaining
      outcomes
      (let [[outcome next-state]
            (v2/bernoulli-draw state (first remaining))]
        (recur (next remaining) next-state (conj outcomes outcome))))))

(defn mixture-probability [x threshold]
  (+ (* 0.5 (v2/knowledge-probability x (- threshold 0.6) 0.75))
     (* 0.5 (v2/knowledge-probability x (+ threshold 0.6) 3.0))))

(defn threshold-for-probability-total [probability-at target]
  (loop [lower (- (apply min xs) 20.0)
         upper (+ (apply max xs) 20.0)
         iteration 0]
    (let [midpoint (/ (+ lower upper) 2.0)
          total (reduce + 0.0 (map #(probability-at % midpoint) xs))]
      (if (= iteration 60)
        midpoint
        (if (> total target)
          (recur midpoint upper (inc iteration))
          (recur lower midpoint (inc iteration)))))))

(defn measured-observations
  [selected latent-outcomes false-negative seed]
  (loop [remaining (seq selected)
         state (v2/normalize-seed seed)
         observations []]
    (if-not remaining
      observations
      (let [item (first remaining)
            pair-index (:pair-index item)
            known? (= 1 (nth latent-outcomes pair-index))
            [missed? next-state]
            (if (and known? (pos? false-negative))
              (v2/bernoulli-draw state false-negative)
              [0 state])]
        (recur (next remaining)
               next-state
               (conj observations
                     {:pair-index pair-index
                      :x (nth xs pair-index)
                      :response (if (and known? (zero? missed?))
                                  :correct
                                  :dont-know)}))))))

(defn v1-stratum-probabilities [selected observations]
  (let [counts
        (reduce (fn [result [item observation]]
                  (-> result
                      (update-in [(:stratum-index item) :n] (fnil inc 0))
                      (update-in [(:stratum-index item) :k] (fnil + 0)
                                 (v2/collapse-response (:response observation)))))
                {} (map vector selected observations))]
    (mapv (fn [stratum]
            (let [{:keys [k n]} (get counts stratum {:k 0 :n 0})]
              (/ (+ 1.0 k) (+ 2.0 n))))
          (range 8))))

(defn model-bins [latent-outcomes selected]
  (let [selected-indexes (set (map :pair-index selected))]
    (mapv
     (fn [bin-index]
       (let [start (* bin-index model-bin-size)
             end (+ start model-bin-size)
             indexes (range start end)
             selected-count (count (filter selected-indexes indexes))]
         {:index bin-index
          :x (/ (reduce + 0.0 (map #(nth xs %) indexes)) model-bin-size)
          :latent-rate (/ (reduce + 0.0
                                  (map #(nth latent-outcomes %) indexes))
                          model-bin-size)
          :selected-count selected-count
          :untested-count (- model-bin-size selected-count)}))
     (range model-bin-count))))

(defn posterior-bin-probabilities [posterior bins]
  (mapv
   (fn [{:keys [x]}]
     (reduce + 0.0
             (map (fn [{:keys [threshold width weight]}]
                    (* weight (v2/knowledge-probability x threshold width)))
                  posterior)))
   bins))

(defn fit-model-scenario
  [{:keys [key title description latent-outcomes false-negative]}
   selected]
  (let [observations (measured-observations
                      selected latent-outcomes false-negative
                      (+ 20260716 (if (= key :false-negatives) 91 0)))
        posterior (v2/posterior-grid xs observations model-grid v2/default-prior)
        stratum-probabilities (v1-stratum-probabilities selected observations)
        bins (model-bins latent-outcomes selected)
        v2-probabilities (posterior-bin-probabilities posterior bins)
        bins (mapv (fn [bin probability]
                     (assoc bin
                            :v1-probability
                            (nth stratum-probabilities
                                 (quot (:index bin) 8))
                            :v2-probability probability))
                   bins v2-probabilities)
        correct (count (filter #(= :correct (:response %)) observations))
        v1-total (:mean (v1-summary selected observations))
        v2-total (+ correct
                    (reduce + 0.0
                            (map #(* (:untested-count %)
                                     (:v2-probability %))
                                 bins)))]
    {:key key
     :title title
     :description description
     :latent-total (reduce + 0 latent-outcomes)
     :correct correct
     :v1-total v1-total
     :v2-total v2-total
     :observations observations
     :bins bins}))

(defn build-model-scenarios []
  (let [selected (->> (v2/selection-schedule
                       (browser-pairs) 8 20260716)
                      (take 8)
                      (mapcat identity)
                      vec)
        logistic-at (fn [x threshold]
                      (v2/knowledge-probability x threshold 1.5))
        logistic-threshold (threshold-for-probability-total logistic-at 4000.0)
        mixture-threshold (threshold-for-probability-total
                           mixture-probability 4000.0)
        logistic-latent (draw-latent-outcomes
                         (mapv #(logistic-at % logistic-threshold) xs)
                         20260717)
        mixture-latent (draw-latent-outcomes
                        (mapv #(mixture-probability % mixture-threshold) xs)
                        20260718)]
    (mapv #(fit-model-scenario % selected)
          [{:key :supported-logistic
            :title "Smooth frequency signal"
            :description
            "Latent knowledge follows the single smooth frequency curve v2 assumes; observed outcomes equal the latent state."
            :latent-outcomes logistic-latent
            :false-negative 0.0}
           {:key :non-logistic-mixture
            :title "Non-logistic mixture"
            :description
            "Latent knowledge blends a sharp and a gradual transition, so neither eight flat steps nor one smooth curve is the true shape."
            :latent-outcomes mixture-latent
            :false-negative 0.0}
           {:key :false-negatives
            :title "10% false negatives"
            :description
            "Latent knowledge is identical to the smooth scenario, but some known tested pairs are observed as not correct."
            :latent-outcomes logistic-latent
            :false-negative 0.10}])))

(defn probability-y [probability]
  (- 94.0 (* 88.0 probability)))

(defn smooth-path [bins value-key]
  (->> bins
       (map-indexed
        (fn [index bin]
          (str (if (zero? index) "M" "L")
               (* (/ (+ index 0.5) model-bin-count) model-chart-width)
               " " (probability-y (get bin value-key)))))
       (clojure.string/join " ")))

(defn v1-step-path [bins]
  (let [probabilities (mapv :v1-probability (take-nth 8 bins))]
    (str "M0 " (probability-y (first probabilities)) " "
         (->> (range 8)
              (mapcat
               (fn [stratum]
                 (let [right (* (/ (inc stratum) 8.0) model-chart-width)
                       following (when (< stratum 7)
                                   (nth probabilities (inc stratum)))]
                   (cond-> [(str "H" right)]
                     following (conj (str "V" (probability-y following)))))))
              (clojure.string/join " ")))))

(defn latent-strip [{:keys [title bins latent-total]}]
  [:svg.pf-model-chart
   {:view-box "0 0 1000 100" :preserve-aspect-ratio "none"
    :role "img"
    :aria-label (str title ": realised latent knowledge by frequency bin; "
                     (format-count latent-total) " of 8,000 pairs are known")}
   [:line {:x1 0 :x2 model-chart-width :y1 86 :y2 86
           :stroke "var(--bs-border-color,#dee2e6)"}]
   (for [{:keys [index latent-rate]} bins
         :let [x (* (/ index model-bin-count) model-chart-width)
               width (+ 0.5 (/ model-chart-width model-bin-count))]]
     ^{:key index}
     [:rect {:x x :y 10 :width width :height 72
             :fill "var(--pf-success,#0f695f)"
             :fill-opacity (+ 0.08 (* 0.9 latent-rate))}])])

(defn evidence-strip [{:keys [title observations correct]}]
  [:svg.pf-model-chart
   {:view-box "0 0 1000 100" :preserve-aspect-ratio "none"
    :role "img"
    :aria-label (str title ": " correct " correct and "
                     (- 64 correct) " not-correct observed outcomes")}
   [:line {:x1 0 :x2 model-chart-width :y1 25 :y2 25
           :stroke "var(--bs-border-color,#dee2e6)"}]
   [:line {:x1 0 :x2 model-chart-width :y1 75 :y2 75
           :stroke "var(--bs-border-color,#dee2e6)"}]
   (for [{:keys [pair-index response]} observations
         :let [x (* (/ pair-index 7999.0) model-chart-width)
               correct? (= response :correct)]]
     ^{:key pair-index}
     [:line {:x1 x :x2 x
             :y1 (if correct? 9 59) :y2 (if correct? 41 91)
             :stroke (if correct?
                       "var(--pf-success,#0f695f)"
                       "var(--pf-fail,#a72f24)")
             :stroke-width 4 :vector-effect "non-scaling-stroke"}])])

(defn prediction-strip [{:keys [title bins v1-total v2-total]}]
  [:svg.pf-model-chart
   {:view-box "0 0 1000 100" :preserve-aspect-ratio "none"
    :role "img"
    :aria-label (str title ": realised latent bin rates compared with v1 and v2 "
                     "posterior predictions. V1 total " (format-count v1-total)
                     "; approximate v2 total " (format-count v2-total))}
   (for [probability [0.0 0.5 1.0]
         :let [y (probability-y probability)]]
     ^{:key probability}
     [:line {:x1 0 :x2 model-chart-width :y1 y :y2 y
             :stroke "var(--bs-border-color,#dee2e6)"}])
   [:path {:d (smooth-path bins :latent-rate)
           :fill "none" :stroke "var(--pf-muted,#4f5b66)"
           :stroke-width 2 :stroke-dasharray "3 5"
           :vector-effect "non-scaling-stroke"}]
   [:path {:d (v1-step-path bins)
           :fill "none" :stroke "var(--pf-warn,#8a5000)"
           :stroke-width 3 :vector-effect "non-scaling-stroke"}]
   [:path {:d (smooth-path bins :v2-probability)
           :fill "none" :stroke "var(--pf-accent,#1464b5)"
           :stroke-width 3 :vector-effect "non-scaling-stroke"}]])

(defn model-lane [label detail chart]
  [:div.pf-model-lane
   [:div.pf-model-lane-label
    [:strong label]
    [:span detail]]
   [:div.pf-model-plot chart]])

(defn model-scenario-panel [scenario]
  (let [{:keys [key title description latent-total correct v1-total v2-total]}
        scenario]
    [:section.pf-model-scenario {:aria-labelledby (str "model-scenario-" (name key))}
     [:div.pf-model-heading
      [:h4 {:id (str "model-scenario-" (name key))} title]
      [:p description]]
     [:div.pf-model-summary
      [:span [:strong (format-count latent-total)] " latent known"]
      [:span [:strong (str correct "/64")] " observed correct"]
      [:span [:strong (format-count v1-total)] " v1 predicted"]
      [:span [:strong (str "≈" (format-count v2-total))] " v2 predicted"]]
     [model-lane "Latent knowledge" "Darker bins contain more realised known pairs."
      [latent-strip scenario]]
     [model-lane "Observed evidence" "Correct marks are above; not-correct marks are below."
      [evidence-strip scenario]]
     [model-lane "Model predictions" "Probability 1 at top · 0 at bottom."
      [prediction-strip scenario]]
     [:div.pf-model-footer
      [:div.pf-model-axis
       [:span "More frequent · rank 1"]
       [:span "Rarer · rank 8,000"]]
      [:ul.pf-model-legend {:aria-label "Prediction line legend"}
       [:li [:span.pf-model-swatch.is-latent {:aria-hidden "true"}] "latent bin rate"]
       [:li [:span.pf-model-swatch.is-v1 {:aria-hidden "true"}] "v1: 8 steps"]
       [:li [:span.pf-model-swatch.is-v2 {:aria-hidden "true"}] "v2: smooth curve"]]]]))

(defonce model-scenario-state (r/atom {:status :idle :scenarios nil}))

(defn load-model-scenarios! []
  (when (= :idle (:status @model-scenario-state))
    (swap! model-scenario-state assoc :status :running)
    (js/setTimeout
     (fn []
       (try
         (reset! model-scenario-state
                 {:status :complete :scenarios (build-model-scenarios)})
         (catch :default error
           (js/console.error error)
           (reset! model-scenario-state
                   {:status :error :error (.-message error)}))))
     20)))

(defn model-scenario-visualizations []
  (let [{:keys [status scenarios error]} @model-scenario-state]
    [:div.pf-model-shell
     (case status
       :running [:p {:aria-live "polite"}
                 "Fitting v1 and the bounded v2 grid for three paired scenarios…"]
       :complete (for [scenario scenarios]
                   ^{:key (:key scenario)} [model-scenario-panel scenario])
       :error [:p {:role "alert"} (str "Scenario visualization failed: " error)]
       [:p "Preparing paired scenarios…"])]))

(defn run-lab! []
  (reset! lab-posterior-view :current)
  (swap! lab-state assoc :status :running :result nil)
  (js/setTimeout
   (fn []
     (try
       (let [{:keys [seed threshold width]} @lab-state
             schedule (->> (v2/selection-schedule (browser-pairs) 8 seed)
                           (take 8) (mapcat identity) vec)
             indexes (mapv :pair-index schedule)
             observations (-> (v2/simulate-responses
                               xs indexes {:threshold threshold :width width}
                               (+ seed 17))
                              raw-responses)
             prior (v2/prior-grid xs bounded-grid v2/default-prior)
             previous (v2/posterior-grid xs (pop observations) bounded-grid
                                         v2/default-prior)
             posterior (v2/posterior-grid xs observations bounded-grid
                                          v2/default-prior)
             v2-summary (v2/posterior-predictive-summary
                         xs observations posterior 300 (+ seed 31))
             result {:truth (v2/expected-total xs threshold width)
                     :responses (frequencies (map :response observations))
                     :posterior-surfaces {:prior prior
                                          :previous previous
                                          :current posterior}
                     :v1 (v1-summary schedule observations)
                     :v2 v2-summary}]
         (swap! lab-state assoc :status :complete :result result))
       (catch :default error
         (js/console.error error)
         (swap! lab-state assoc :status :error :error (.-message error)))))
   20))

(defn interval-row [label color {:keys [mean lower upper]}]
  (let [x #(+ 40 (* % (/ 600 8000.0)))]
    [:g
     [:text {:x 8 :y (if (= label "v1") 48 96) :font-size 14
             :font-weight 700 :fill "currentColor"} label]
     [:line {:x1 (x lower) :x2 (x upper)
             :y1 (if (= label "v1") 44 92)
             :y2 (if (= label "v1") 44 92)
             :stroke color :stroke-width 9 :stroke-linecap "round"}]
     [:circle {:cx (x mean) :cy (if (= label "v1") 44 92) :r 7
               :fill color :stroke "white" :stroke-width 2}]]))

(defn simulation-result
  [{:keys [truth responses posterior-surfaces v1 v2]}]
  (let [{:keys [prior previous current]} posterior-surfaces
        heatmap-view @lab-posterior-view]
    [:div.pf-result
     [:div.pf-summary-grid
      [:section [:h4 "Simulated learner"]
       [:p "Expected total: " [:strong (format-count truth)]]
       [:p "64 raw responses: "
        (str (get responses :correct 0) " correct, "
             (get responses :wrong 0) " wrong, "
             (get responses :dont-know 0) " don't know")]]
      [:section [:h4 "v1"]
       [:p [:strong (format-count (:mean v1))]
        (str " (" (format-count (:lower v1)) "–"
             (format-count (:upper v1)) ")")]]
      [:section [:h4 "bounded v2"]
       [:p [:strong (format-count (:mean v2))]
        (str " (" (format-count (:lower v2)) "–"
             (format-count (:upper v2)) ")")]]]
     [:svg {:view-box "0 0 680 135" :role "img"
            :aria-label "v1 and bounded v2 estimates with 95 percent intervals"}
      [interval-row "v1" "var(--pf-warn,#8a5000)" v1]
      [interval-row "v2" "var(--pf-accent,#1464b5)" v2]
      [:line {:x1 40 :x2 640 :y1 116 :y2 116 :stroke "currentColor"}]
      (for [value [0 2000 4000 6000 8000]
            :let [x (+ 40 (* value (/ 600 8000.0)))]]
        ^{:key value}
        [:g [:line {:x1 x :x2 x :y1 116 :y2 122 :stroke "currentColor"}]
         [:text {:x x :y 134 :text-anchor "middle" :font-size 11
                 :fill "currentColor"} value]])]
     [:section.pf-quiz-posterior
      {:aria-label "Completed quiz posterior visualisations"}
      [posterior-joint-heatmap
       "quiz-joint-posterior"
       "Completed quiz posterior · joint threshold × width"
       prior previous current heatmap-view
       #(reset! lab-posterior-view %)]
      [:p.pf-note
       "Current shows the posterior after all 64 responses. Previous means after 63 responses, immediately before the final answer."]
      [:div.pf-posterior-grid
       [posterior-marginal-chart
        "quiz-threshold-posterior-chart" "Completed threshold t posterior"
        "Standardized-frequency threshold t" :threshold
        prior previous current]
       [posterior-marginal-chart
        "quiz-width-posterior-chart" "Completed width w posterior"
        "10%–90% transition width w" :width
        prior previous current]]]]))

(defn simulation-lab []
  (let [{:keys [seed threshold width status result error]} @lab-state]
    [:div
     [:div.pf-controls
      [:div.pf-control
       [:label {:for "lab-seed"} "Seed"]
       [:input#lab-seed {:type "number" :value seed
                         :on-change #(swap! lab-state assoc :seed
                                            (long (parse-number %)))}]]
      [field "lab-threshold" "True threshold" threshold -2.5 2.5 0.05
       #(swap! lab-state assoc :threshold (parse-number %))]
      [field "lab-width" "True width" width 0.5 4.0 0.25
       #(swap! lab-state assoc :width (parse-number %))]
      [:button.pf-button {:type "button" :on-click run-lab!
                          :disabled (= status :running)}
       (if (= status :running) "Calculating…" "Run seeded quiz")]]
     [:p.pf-note "Selection is fixed before responses: eight rounds, one item per stratum. Changing only the response seed never changes the queues."]
     [:div {:aria-live "polite"}
      (case status
        :running [:p "Fitting the bounded browser grid…"]
        :complete [simulation-result result]
        :error [:p {:role "alert"} (str "Simulation failed: " error)]
        [:p "Choose parameters, then run the quiz."])]]))

(def gate-defaults
  (or (:defaults gate-data)
      {:aggregateCoverage 94.5
       :minimumCellCoverage 94.0
       :aggregateMae 610.7
       :maximumCellMaeRatio 105.0
       :medianItems 40}))

(def gate-actual
  (or (:actual gate-data)
      {:aggregateCoverage 95.484
       :minimumCellCoverage 92.4
       :aggregateMae 253.0
       :maximumCellMaeRatio 122.05
       :medianItems 64}))

(defonce gate-state (r/atom gate-defaults))

(def gate-threshold-specs
  [{:key :aggregateCoverage
    :id "gate-aggregate-coverage"
    :label "Minimum aggregate coverage"
    :minimum 90.0 :maximum 99.5 :step 0.1 :suffix "%"}
   {:key :minimumCellCoverage
    :id "gate-worst-cell-coverage"
    :label "Minimum worst-cell coverage"
    :minimum 88.0 :maximum 99.5 :step 0.1 :suffix "%"}
   {:key :aggregateMae
    :id "gate-aggregate-mae"
    :label "Maximum aggregate MAE"
    :minimum 200.0 :maximum 800.0 :step 0.1 :suffix " pairs"}
   {:key :maximumCellMaeRatio
    :id "gate-worst-cell-mae-ratio"
    :label "Maximum worst-cell MAE ratio"
    :minimum 95.0 :maximum 140.0 :step 0.5 :suffix "%"}
   {:key :medianItems
    :id "gate-median-items"
    :label "Maximum median quiz length"
    :minimum 24 :maximum 96 :step 8 :suffix " items"}])

(defn fixed [value digits]
  (.toFixed (js/Number value) digits))

(defn threshold-label [key value]
  (case key
    :aggregateCoverage (str (fixed value 1) "%")
    :minimumCellCoverage (str (fixed value 1) "%")
    :aggregateMae (str (fixed value 1) " pairs")
    :maximumCellMaeRatio (str (fixed value 1) "%")
    :medianItems (str (js/Math.round value) " items")))

(defn gate-checks [thresholds]
  (let [{:keys [aggregateCoverage minimumCellCoverage aggregateMae
                maximumCellMaeRatio medianItems]} gate-actual]
    [{:key :aggregateCoverage
      :label "Aggregate interval coverage"
      :comparison (str (fixed aggregateCoverage 3) "% ≥ "
                       (fixed (:aggregateCoverage thresholds) 1) "%")
      :passes? (>= aggregateCoverage (:aggregateCoverage thresholds))}
     {:key :minimumCellCoverage
      :label "Worst-cell interval coverage"
      :comparison (str (fixed minimumCellCoverage 1) "% ≥ "
                       (fixed (:minimumCellCoverage thresholds) 1) "%")
      :passes? (>= minimumCellCoverage (:minimumCellCoverage thresholds))}
     {:key :aggregateMae
      :label "Aggregate MAE"
      :comparison (str (fixed aggregateMae 1) " < "
                       (fixed (:aggregateMae thresholds) 1))
      :passes? (< aggregateMae (:aggregateMae thresholds))}
     {:key :maximumCellMaeRatio
      :label "Worst-cell v2/v1 MAE ratio"
      :comparison (str (fixed maximumCellMaeRatio 2) "% ≤ "
                       (fixed (:maximumCellMaeRatio thresholds) 1) "%")
      :passes? (<= maximumCellMaeRatio
                   (:maximumCellMaeRatio thresholds))}
     {:key :medianItems
      :label "Median quiz length"
      :comparison (str (js/Math.round medianItems) " ≤ "
                       (js/Math.round (:medianItems thresholds)))
      :passes? (<= medianItems (:medianItems thresholds))}]))

(defn gate-threshold-field
  [{:keys [key id label minimum maximum step]} thresholds]
  (let [value (get thresholds key)]
    [:div.pf-gate-field
     [:div
      [:label {:for id} label]
      [:output {:for id} (threshold-label key value)]]
     [:input {:id id :type "range" :value value
              :min minimum :max maximum :step step
              :aria-describedby "gate-counterfactual-note"
              :on-input #(swap! gate-state assoc key (parse-number %))
              :on-change #(swap! gate-state assoc key (parse-number %))}]]))

(defn reset-gate! []
  (reset! gate-state gate-defaults))

(defn gate-inspector []
  (let [thresholds @gate-state
        checks (gate-checks thresholds)
        passing-count (count (filter :passes? checks))
        all-pass? (= passing-count (count checks))
        counterfactual? (not= thresholds gate-defaults)]
    [:section.pf-gate-shell {:aria-labelledby "gate-inspector-heading"}
     [:h3#gate-inspector-heading "Five checks, one AND decision"]
     [:div {:class (str "pf-gate-banner "
                        (if counterfactual?
                          "is-counterfactual"
                          "is-historical"))
            :aria-live "polite"}
      [:strong
       (if counterfactual?
         "COUNTERFACTUAL THRESHOLDS — teaching only"
         "Historical precommitted gate")]
      [:span
       (if counterfactual?
         (str (if all-pass?
                "Would promote under these altered thresholds"
                "Would not promote under these altered thresholds")
              " · " passing-count "/5 checks pass.")
         (str "NOT PROMOTED · " passing-count "/5 checks passed."))]]
     [:p#gate-counterfactual-note.pf-note
      (if counterfactual?
        "These sliders do not edit the immutable results or revise the historical non-promotion decision."
        "Defaults reproduce the historical tuning gate. Promotion requires all five checks.")]
     [:div.pf-gate-controls
      (for [spec gate-threshold-specs]
        ^{:key (:key spec)}
        [gate-threshold-field spec thresholds])]
     [:div.pf-table-wrap
      [:table.pf-table.pf-gate-table
       [:caption.pf-sr-only "Gate checks under the currently displayed thresholds"]
       [:thead
        [:tr
         [:th {:scope "col"} "Check"]
         [:th {:scope "col"} "Actual vs threshold"]
         [:th {:scope "col"} "Status"]]]
       [:tbody
        (for [{:keys [key label comparison passes?]} checks]
          ^{:key key}
          [:tr
           [:th {:scope "row"} label]
           [:td comparison]
           [:td {:class (if passes? "pf-status-pass" "pf-status-fail")}
            (if passes? "PASS ✓" "FAIL ✕")]])]]]
     [:button.pf-gate-reset
      {:type "button" :on-click reset-gate! :disabled (not counterfactual?)}
      "Reset historical thresholds"]]))

(def styles
  (str
   ".pf-controls{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,13rem),1fr));gap:.8rem;align-items:end}.pf-control{display:grid;gap:.3rem;min-width:0}.pf-control input{width:100%;min-width:0;accent-color:var(--pf-accent,#1464b5)}"
   ".pf-button{border:1px solid var(--pf-accent,#1464b5);border-radius:.4rem;padding:.6rem .9rem;background:var(--pf-accent,#1464b5);color:var(--bs-body-bg,#fff);font-weight:700;cursor:pointer}.quarto-dark .pf-button{color:#10212b}.pf-button:disabled{opacity:.6;cursor:wait}"
   ".pf-button:focus-visible,.pf-control input:focus-visible,.pf-gate-field input:focus-visible,.pf-gate-reset:focus-visible{outline:3px solid color-mix(in srgb,var(--pf-accent,#2780e3) 50%,transparent);outline-offset:2px}.pf-live{font-variant-numeric:tabular-nums}.pf-lab svg{display:block;width:100%;height:auto}.pf-note{font-size:.88rem;color:var(--pf-muted,#4f5b66)}"
   ".pf-summary-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,12rem),1fr));gap:.8rem;margin-top:1rem}.pf-summary-grid section{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.7rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.pf-summary-grid h4{font-size:.95rem;margin:0 0 .3rem}.pf-summary-grid p{margin:.2rem 0}"
   ".pf-model-shell{display:grid;gap:1.4rem;min-width:0}.pf-model-scenario{display:grid;gap:.65rem;min-width:0;border-top:1px solid var(--bs-border-color,#dee2e6);padding-top:1.25rem}.pf-model-scenario:first-child{border-top:0;padding-top:0}.pf-model-heading h4{margin:0 0 .2rem}.pf-model-heading p{margin:0;color:var(--pf-muted,#4f5b66)}.pf-model-summary{display:flex;gap:.45rem 1.1rem;flex-wrap:wrap;font-variant-numeric:tabular-nums}.pf-model-summary span{white-space:nowrap}.pf-model-lane{display:grid;grid-template-columns:minmax(10.25rem,.24fr) minmax(0,1fr);gap:.7rem;align-items:center;min-width:0}.pf-model-lane-label{min-width:0}.pf-model-lane-label strong,.pf-model-lane-label span{display:block}.pf-model-lane-label span{font-size:.82rem;color:var(--pf-muted,#4f5b66)}.pf-model-plot{min-width:0}.pf-lab svg.pf-model-chart{display:block;width:100%;height:4.35rem}.pf-model-footer{display:grid;grid-template-columns:minmax(0,1fr);gap:.4rem;align-items:start;margin-left:10.95rem}.pf-model-axis{display:flex;justify-content:space-between;gap:1rem;font-size:.8rem;color:var(--pf-muted,#4f5b66)}.pf-model-legend{display:flex;gap:.35rem .8rem;flex-wrap:wrap;justify-content:flex-start;margin:0;padding:0;list-style:none;font-size:.8rem}.pf-model-legend li{display:flex;align-items:center;gap:.3rem;white-space:nowrap}.pf-model-swatch{display:inline-block;width:1.5rem;border-top:3px solid var(--pf-muted,#4f5b66)}.pf-model-swatch.is-latent{border-top-width:2px;border-top-style:dashed}.pf-model-swatch.is-v1{border-color:var(--pf-warn,#8a5000)}.pf-model-swatch.is-v2{border-color:var(--pf-accent,#1464b5)}"
   ".pf-response-shell{min-width:0;border:1px solid var(--bs-border-color,#ced4da);border-radius:.65rem;padding:clamp(.8rem,3vw,1.3rem);margin:1.25rem 0;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.pf-response-shell h3{margin-top:0}.pf-response-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:.55rem;margin:1rem 0}.pf-response-card{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.65rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 94%,var(--pf-accent,#1464b5) 6%);overflow-wrap:anywhere}.pf-response-card strong,.pf-response-card span,.pf-response-card small{display:block}.pf-response-card small{color:var(--pf-muted,#4f5b66);margin-top:.25rem}.pf-response-progress{width:100%;height:.55rem;accent-color:var(--pf-accent,#1464b5)}.pf-response-effects{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.65rem;margin:.85rem 0}.pf-response-effect{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-left:4px solid var(--pf-accent,#1464b5);border-radius:.45rem;padding:.65rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 92%,var(--pf-accent,#1464b5) 8%);overflow-wrap:anywhere}.pf-response-effect.is-fixed{border-left-color:var(--pf-success,#0f695f);background:color-mix(in srgb,var(--bs-body-bg,#fff) 92%,var(--pf-success,#0f695f) 8%)}.pf-response-effect strong,.pf-response-effect span,.pf-response-effect small{display:block}.pf-response-effect small{color:var(--pf-muted,#4f5b66);margin-top:.25rem}.pf-response-status{min-height:2.8rem;font-variant-numeric:tabular-nums}.pf-response-actions{display:flex;gap:.5rem;flex-wrap:wrap}.pf-response-button{border:1px solid var(--bs-border-color,#6c757d);border-radius:.35rem;padding:.55rem .85rem;font-weight:700;cursor:pointer;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.pf-response-button.is-primary{border-color:var(--pf-accent,#1464b5);background:var(--pf-accent,#1464b5);color:#fff}.quarto-dark .pf-response-button.is-primary{color:#10212b}.pf-response-button:disabled{opacity:.5;cursor:not-allowed}.pf-response-button:focus-visible{outline:3px solid color-mix(in srgb,var(--pf-accent,#1464b5) 50%,transparent);outline-offset:2px}"
   ".pf-response-summary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.65rem;margin:.85rem 0}.pf-posterior-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,18rem),1fr));gap:.8rem;margin:1rem 0}.pf-posterior-panel{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.5rem;padding:.7rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 96%,var(--pf-accent,#1464b5) 4%)}.pf-posterior-panel h4{font-size:1rem;margin:0 0 .35rem}.pf-posterior-panel>svg{display:block;width:100%;height:auto}.pf-posterior-legend{display:flex;gap:.7rem;flex-wrap:wrap;font-size:.8rem;color:var(--pf-muted,#4f5b66)}.pf-posterior-legend span{display:flex;align-items:center;gap:.25rem}.pf-posterior-legend svg{width:2.1rem;height:.55rem;flex:none}"
   ".pf-quiz-posterior{min-width:0;margin-top:1.2rem;padding-top:.2rem;border-top:1px solid var(--bs-border-color,#dee2e6)}.pf-joint-posterior-panel{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.5rem;padding:.7rem;margin:1rem 0;background:color-mix(in srgb,var(--bs-body-bg,#fff) 96%,var(--pf-accent,#1464b5) 4%)}.pf-joint-posterior-header{display:flex;justify-content:space-between;align-items:center;gap:.7rem;flex-wrap:wrap}.pf-joint-posterior-header h4{font-size:1rem;margin:0}.pf-joint-posterior-panel>svg{display:block;width:100%;height:auto}.pf-heatmap-view{display:flex;gap:.3rem;flex-wrap:wrap}.pf-heatmap-view button{border:1px solid var(--bs-border-color,#6c757d);border-radius:999px;padding:.32rem .62rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529);font-size:.82rem;font-weight:700;cursor:pointer}.pf-heatmap-view button[aria-pressed=true]{border-color:var(--pf-accent,#1464b5);background:var(--pf-accent,#1464b5);color:#fff}.quarto-dark .pf-heatmap-view button[aria-pressed=true]{color:#10212b}.pf-heatmap-view button:disabled{opacity:.45;cursor:not-allowed}.pf-heatmap-view button:focus-visible{outline:3px solid color-mix(in srgb,var(--pf-accent,#1464b5) 50%,transparent);outline-offset:2px}.pf-heatmap-scale{display:grid;grid-template-columns:auto minmax(5rem,12rem) auto;align-items:center;justify-content:start;gap:.35rem .5rem;font-size:.78rem;color:var(--pf-muted,#4f5b66)}.pf-heatmap-scale>strong{grid-column:1/-1;font-size:.78rem}.pf-heatmap-ramp{display:grid;grid-template-columns:repeat(11,minmax(0,1fr));height:.72rem;min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.2rem;overflow:hidden}.pf-heatmap-ramp>span{background:var(--pf-accent,#1464b5)}.pf-heatmap-status{margin:.65rem 0 .2rem;font-variant-numeric:tabular-nums}"
   ".pf-gate-shell{min-width:0}.pf-gate-shell h3{margin-top:0}.pf-gate-banner{display:grid;gap:.25rem;border:2px solid var(--pf-fail,#a72f24);border-left-width:6px;border-radius:.45rem;padding:.8rem .9rem;margin:.7rem 0;background:color-mix(in srgb,var(--bs-body-bg,#fff) 86%,var(--pf-fail,#a72f24) 14%);color:var(--bs-body-color,#212529)}.pf-gate-banner.is-counterfactual{border-color:var(--pf-warn,#8a5000);background:color-mix(in srgb,var(--bs-body-bg,#fff) 84%,var(--pf-warn,#8a5000) 16%)}"
   ".pf-gate-controls{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,14rem),1fr));gap:.7rem;margin:1rem 0}.pf-gate-field{display:grid;gap:.4rem;min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.7rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.pf-gate-field>div{display:flex;justify-content:space-between;align-items:baseline;gap:.55rem;flex-wrap:wrap}.pf-gate-field label{font-weight:700}.pf-gate-field output{font-variant-numeric:tabular-nums;color:var(--pf-muted,#4f5b66)}.pf-gate-field input{width:100%;min-width:0;accent-color:var(--pf-accent,#1464b5)}"
   ".pf-gate-table td:nth-child(2){font-variant-numeric:tabular-nums}.pf-gate-reset{border:1px solid var(--pf-accent,#1464b5);border-radius:.4rem;padding:.55rem .8rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529);font-weight:700;cursor:pointer}.pf-gate-reset:disabled{opacity:.55;cursor:not-allowed}"
   "@media(max-width:767px){.pf-response-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:575px){.pf-gate-table th,.pf-gate-table td{white-space:normal;min-width:7rem}.pf-response-effects,.pf-response-summary{grid-template-columns:minmax(0,1fr)}.pf-model-lane{grid-template-columns:minmax(0,1fr);gap:.25rem}.pf-model-footer{margin-left:0}.pf-lab svg.pf-model-chart{height:3.8rem}.pf-model-summary span{white-space:normal}}@media(max-width:400px){.pf-response-grid{grid-template-columns:minmax(0,1fr)}}"))

(let [style (.createElement js/document "style")]
  (set! (.-textContent style) styles)
  (.appendChild (.-head js/document) style))

(defn mount! [id component]
  (when-let [element (.getElementById js/document id)]
    (rdom/render [component] element)))

(when (seq xs)
  (mount! "pair-frequency-curve-explorer" curve-explorer)
  (mount! "pair-frequency-model-scenarios" model-scenario-visualizations)
  (load-model-scenarios!)
  (mount! "pair-frequency-simulation-lab" simulation-lab))

(mount! "pair-frequency-response-inference-simulator"
        response-inference-simulator)

(when gate-data
  (mount! "pair-frequency-gate-inspector" gate-inspector))
