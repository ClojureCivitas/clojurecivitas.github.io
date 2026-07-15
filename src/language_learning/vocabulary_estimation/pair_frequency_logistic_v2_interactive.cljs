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

(def response-demo-sequence
  [:correct :wrong :dont-know :correct :correct :dont-know :wrong :correct
   :dont-know :correct :wrong :wrong :correct :dont-know :correct :correct])

(def response-demo-limit 16)
(defonce response-demo-state (r/atom {:revealed 0}))

(defn response-demo-label [response]
  (case response
    :correct "correct"
    :wrong "wrong"
    :dont-know "don't know"
    "none"))

(defn response-demo-item [index]
  (let [stratum (inc (mod index 8))
        round (inc (quot index 8))]
    {:stratum stratum
     :round round
     :item-id (str "S" stratum "-R" round)
     :response (nth response-demo-sequence index)}))

(defn reveal-response-demo! []
  (swap! response-demo-state update :revealed
         #(min response-demo-limit (inc %))))

(defn reset-response-demo! []
  (reset! response-demo-state {:revealed 0}))

(defn response-inference-simulator []
  (let [revealed (:revealed @response-demo-state)
        selections (mapv response-demo-item (range revealed))
        latest (peek selections)
        correct (count (filter #(= :correct (:response %)) selections))
        not-correct (- revealed correct)
        next-selection (when (< revealed response-demo-limit)
                         (response-demo-item revealed))
        complete? (= revealed response-demo-limit)]
    [:section.pf-response-shell
     {:aria-labelledby "response-inference-heading"}
     [:h3#response-inference-heading "Responses update inference, not selection"]
     [:p
      "As in article 1, two demonstration rounds are queued before any answer. Reveal the same fixed schedule while watching only the inference state change."]
     [:div.pf-response-grid
      (for [stratum (range 1 9)
            :let [seen (filterv #(= stratum (:stratum %)) selections)
                  most-recent (peek seen)
                  next-round (inc (count seen))]]
        ^{:key stratum}
        [:div.pf-response-card
         [:strong (str "Stratum " stratum)]
         [:span (if most-recent
                  (str "Latest: " (:item-id most-recent)
                       " · " (response-demo-label (:response most-recent)))
                  "Latest: none")]
         [:small (if (< next-round 3)
                   (str "Next queued: S" stratum "-R" next-round)
                   "Two demonstration items used")]])]
     [:progress.pf-response-progress
      {:value revealed :max response-demo-limit
       :aria-label "Scheduled demonstration items revealed"}]
     [:div.pf-response-effects
      [:div.pf-response-effect
       [:strong "Inference changes"]
       [:span (if (zero? revealed)
                "Posterior still equals the prior"
                (str revealed " response term" (when (not= revealed 1) "s")
                     ": " correct " correct · " not-correct " not correct"))]
       [:small (if latest
                 (str "Latest likelihood contribution: "
                      (if (= :correct (:response latest)) "pᵢ" "1 − pᵢ"))
                 "Each response will reweight the posterior.")]]
      [:div.pf-response-effect.is-fixed
       [:strong "Selection stays fixed"]
       [:span (if next-selection
                (str "Next scheduled: " (:item-id next-selection))
                "Both fixed rounds complete")]
       [:small "No response changes an item queue."]]]
     [:p.pf-response-status
      {:aria-live "polite"}
      (cond
        complete? "Two complete rounds. Inference used all 16 responses; every queue followed its original order."
        latest (str "Recorded " (:item-id latest) " as "
                    (response-demo-label (:response latest))
                    ". The posterior changed; the next scheduled item did not.")
        :else "Ready. The eight queues and both rounds already exist.")]
     [:div.pf-response-actions
      [:button.pf-response-button.is-primary
       {:type "button" :on-click reveal-response-demo! :disabled complete?}
       (if complete? "Two rounds complete" "Reveal next scheduled item")]
      [:button.pf-response-button
       {:type "button" :on-click reset-response-demo!}
       "Reset"]]
     [:p.pf-note
      "The response labels are illustrative. The schedule is S1 through S8 in both rounds, independent of every answer."]]))

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

(defn run-lab! []
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
             posterior (v2/posterior-grid xs observations bounded-grid
                                          v2/default-prior)
             v2-summary (v2/posterior-predictive-summary
                         xs observations posterior 300 (+ seed 31))
             result {:truth (v2/expected-total xs threshold width)
                     :responses (frequencies (map :response observations))
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

(defn simulation-result [{:keys [truth responses v1 v2]}]
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
               :fill "currentColor"} value]])]])

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
   ".pf-response-shell{min-width:0;border:1px solid var(--bs-border-color,#ced4da);border-radius:.65rem;padding:clamp(.8rem,3vw,1.3rem);margin:1.25rem 0;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.pf-response-shell h3{margin-top:0}.pf-response-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:.55rem;margin:1rem 0}.pf-response-card{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.65rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 94%,var(--pf-accent,#1464b5) 6%);overflow-wrap:anywhere}.pf-response-card strong,.pf-response-card span,.pf-response-card small{display:block}.pf-response-card small{color:var(--pf-muted,#4f5b66);margin-top:.25rem}.pf-response-progress{width:100%;height:.55rem;accent-color:var(--pf-accent,#1464b5)}.pf-response-effects{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.65rem;margin:.85rem 0}.pf-response-effect{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-left:4px solid var(--pf-accent,#1464b5);border-radius:.45rem;padding:.65rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 92%,var(--pf-accent,#1464b5) 8%);overflow-wrap:anywhere}.pf-response-effect.is-fixed{border-left-color:var(--pf-success,#0f695f);background:color-mix(in srgb,var(--bs-body-bg,#fff) 92%,var(--pf-success,#0f695f) 8%)}.pf-response-effect strong,.pf-response-effect span,.pf-response-effect small{display:block}.pf-response-effect small{color:var(--pf-muted,#4f5b66);margin-top:.25rem}.pf-response-status{min-height:2.8rem;font-variant-numeric:tabular-nums}.pf-response-actions{display:flex;gap:.5rem;flex-wrap:wrap}.pf-response-button{border:1px solid var(--bs-border-color,#6c757d);border-radius:.35rem;padding:.55rem .85rem;font-weight:700;cursor:pointer;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.pf-response-button.is-primary{border-color:var(--pf-accent,#1464b5);background:var(--pf-accent,#1464b5);color:#fff}.quarto-dark .pf-response-button.is-primary{color:#10212b}.pf-response-button:disabled{opacity:.5;cursor:not-allowed}.pf-response-button:focus-visible{outline:3px solid color-mix(in srgb,var(--pf-accent,#1464b5) 50%,transparent);outline-offset:2px}"
   ".pf-gate-shell{min-width:0}.pf-gate-shell h3{margin-top:0}.pf-gate-banner{display:grid;gap:.25rem;border:2px solid var(--pf-fail,#a72f24);border-left-width:6px;border-radius:.45rem;padding:.8rem .9rem;margin:.7rem 0;background:color-mix(in srgb,var(--bs-body-bg,#fff) 86%,var(--pf-fail,#a72f24) 14%);color:var(--bs-body-color,#212529)}.pf-gate-banner.is-counterfactual{border-color:var(--pf-warn,#8a5000);background:color-mix(in srgb,var(--bs-body-bg,#fff) 84%,var(--pf-warn,#8a5000) 16%)}"
   ".pf-gate-controls{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,14rem),1fr));gap:.7rem;margin:1rem 0}.pf-gate-field{display:grid;gap:.4rem;min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.7rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.pf-gate-field>div{display:flex;justify-content:space-between;align-items:baseline;gap:.55rem;flex-wrap:wrap}.pf-gate-field label{font-weight:700}.pf-gate-field output{font-variant-numeric:tabular-nums;color:var(--pf-muted,#4f5b66)}.pf-gate-field input{width:100%;min-width:0;accent-color:var(--pf-accent,#1464b5)}"
   ".pf-gate-table td:nth-child(2){font-variant-numeric:tabular-nums}.pf-gate-reset{border:1px solid var(--pf-accent,#1464b5);border-radius:.4rem;padding:.55rem .8rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529);font-weight:700;cursor:pointer}.pf-gate-reset:disabled{opacity:.55;cursor:not-allowed}"
   "@media(max-width:767px){.pf-response-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:575px){.pf-gate-table th,.pf-gate-table td{white-space:normal;min-width:7rem}.pf-response-effects{grid-template-columns:minmax(0,1fr)}}@media(max-width:400px){.pf-response-grid{grid-template-columns:minmax(0,1fr)}}"))

(let [style (.createElement js/document "style")]
  (set! (.-textContent style) styles)
  (.appendChild (.-head js/document) style))

(defn mount! [id component]
  (when-let [element (.getElementById js/document id)]
    (rdom/render [component] element)))

(when (seq xs)
  (mount! "pair-frequency-curve-explorer" curve-explorer)
  (mount! "pair-frequency-simulation-lab" simulation-lab))

(mount! "pair-frequency-response-inference-simulator"
        response-inference-simulator)

(when gate-data
  (mount! "pair-frequency-gate-inspector" gate-inspector))
