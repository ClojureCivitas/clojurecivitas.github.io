(ns language-learning.vocabulary-estimation.pair-frequency-logistic-v2-interactive
  (:require [language-learning.vocabulary-estimation.pair-frequency-logistic-v2 :as v2]
            [reagent.core :as r]
            [reagent.dom :as rdom]))

(def xs
  (js->clj
   (js/JSON.parse
    (.-textContent (.getElementById js/document "pair-frequency-xs")))))

(def number-format (js/Intl.NumberFormat. "en-US"))
(defn format-count [number] (.format number-format (js/Math.round number)))
(defn parse-number [event] (js/Number (.. event -target -value)))

(defonce curve-state (r/atom {:threshold 0.0 :width 2.0}))

(defn curve-path [threshold width]
  (->> (range 101)
       (map (fn [index]
              (let [x (+ -1.2 (* index (/ 8.4 100.0)))
                    probability (v2/knowledge-probability x threshold width)
                    px (+ 48 (* index 5.9))
                    py (- 210 (* probability 160))]
                (str (if (zero? index) "M" "L") px " " py))))
       (clojure.string/join " ")))

(defn field [id label value minimum maximum step on-change]
  [:div.pf-control
   [:label {:for id} label ": " [:strong (.toFixed value 2)]]
   [:input {:id id :type "range" :value value :min minimum :max maximum
            :step step :on-change on-change}]])

(defn curve-explorer []
  (let [{:keys [threshold width]} @curve-state
        total (v2/expected-total xs threshold width)]
    [:div
     [:div.pf-controls
      [field "curve-threshold" "Threshold t" threshold -2.5 3.0 0.05
       #(swap! curve-state assoc :threshold (parse-number %))]
      [field "curve-width" "10%–90% width w" width 0.25 5.0 0.25
       #(swap! curve-state assoc :width (parse-number %))]]
     [:p.pf-live {:aria-live "polite"}
      "Expected known total in this fixture: " [:strong (format-count total)]
      " of 8,000 pairs."]
     [:svg {:view-box "0 0 680 270" :role "img"
            :aria-labelledby "curve-title curve-desc"}
      [:title#curve-title "Frequency predictor and knowing probability"]
      [:desc#curve-desc
       (str "A logistic curve with threshold " (.toFixed threshold 2)
            ", width " (.toFixed width 2) ", and expected total "
            (format-count total) ".")]
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
      [:path {:d (curve-path threshold width) :fill "none" :stroke "#2780e3"
              :stroke-width 4 :vector-effect "non-scaling-stroke"}]
      [:text {:x 343 :y 250 :text-anchor "middle" :font-size 14
              :fill "currentColor"} "Standardized log₁₀ pair frequency x"]]]))

(def bounded-grid
  {:threshold-points 41 :width-points 21
   :minimum-width 0.25 :maximum-width 8.0})

(defonce lab-state
  (r/atom {:seed 20260713 :threshold -0.19 :width 1.5
           :status :idle :result nil}))

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
    [interval-row "v1" "#7b61a8" v1]
    [interval-row "v2" "#2780e3" v2]
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

(def styles
  ".pf-controls{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,13rem),1fr));gap:.8rem;align-items:end}.pf-control{display:grid;gap:.3rem;min-width:0}.pf-control input{width:100%;min-width:0}.pf-button{border:1px solid #1464b5;border-radius:.4rem;padding:.6rem .9rem;background:#1464b5;color:#fff;font-weight:700;cursor:pointer}.pf-button:disabled{opacity:.6;cursor:wait}.pf-button:focus-visible,.pf-control input:focus-visible{outline:3px solid color-mix(in srgb,#2780e3 45%,transparent);outline-offset:2px}.pf-live{font-variant-numeric:tabular-nums}.pf-lab svg{display:block;width:100%;height:auto}.pf-note{font-size:.88rem;color:#4f5b66}.quarto-dark .pf-note{color:#b9c7d2}.pf-summary-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,12rem),1fr));gap:.8rem;margin-top:1rem}.pf-summary-grid section{min-width:0;border:1px solid #dee2e6;border-radius:.45rem;padding:.7rem}.pf-summary-grid h4{font-size:.95rem;margin:0 0 .3rem}.pf-summary-grid p{margin:.2rem 0}")

(let [style (.createElement js/document "style")]
  (set! (.-textContent style) styles)
  (.appendChild (.-head js/document) style))

(rdom/render [curve-explorer]
             (.getElementById js/document "pair-frequency-curve-explorer"))
(rdom/render [simulation-lab]
             (.getElementById js/document "pair-frequency-simulation-lab"))
