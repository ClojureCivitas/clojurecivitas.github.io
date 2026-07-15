(ns language-learning.vocabulary-estimation.bayes-theorem-simulations-interactive
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent.dom :as rdom]))

;; Shared deterministic random-number generator and probability helpers.

(defn make-rng [seed]
  #js {:state (mod seed 2147483647)})

(defn uniform! [rng]
  (let [next-state (mod (* 48271 (.-state rng)) 2147483647)]
    (set! (.-state rng) next-state)
    (/ (dec next-state) 2147483646.0)))

(def probability-grid
  (mapv #(/ % 200.0) (range 201)))

(defn binomial-coefficient [n k]
  (let [k (min k (- n k))]
    (reduce (fn [acc i]
              (* acc (/ (- (inc n) i) i)))
            1.0
            (range 1 (inc k)))))

(defn normalize-mean-one [values]
  (let [mean-value (/ (reduce + values) (count values))]
    (if (pos? mean-value)
      (mapv #(/ % mean-value) values)
      (vec values))))

(defn normalize-mass [values]
  (let [total (reduce + values)]
    (if (pos? total)
      (mapv #(/ % total) values)
      (vec values))))

(defn observation-counts [samples]
  (let [water (count (filter #{:water} samples))
        land (- (count samples) water)]
    {:n (count samples) :water water :land land}))

(defn sequence-likelihood [p water land]
  (* (js/Math.pow p water)
     (js/Math.pow (- 1 p) land)))

(defn binomial-likelihood [p water land]
  (* (binomial-coefficient (+ water land) water)
     (sequence-likelihood p water land)))

(defn standard-posterior [prior samples]
  (let [{:keys [water land]} (observation-counts samples)]
    (->> probability-grid
         (mapv #(binomial-likelihood % water land))
         (mapv * prior)
         normalize-mean-one)))

(def uniform-prior (vec (repeat 201 1.0)))
(def step-up-prior (mapv #(if (<= % 0.5) 0.0 2.0) probability-grid))
(def step-down-prior (mapv #(if (<= % 0.5) 2.0 0.0) probability-grid))
(def ramp-prior
  (normalize-mean-one
   (mapv #(js/Math.pow 1.08 (* 200 (if (<= % 0.5) % (- 1 %))))
         probability-grid)))

(def prior-options
  [{:label "Uniform" :values uniform-prior}
   {:label "Step up" :values step-up-prior}
   {:label "Step down" :values step-down-prior}
   {:label "Ramp up and down" :values ramp-prior}])

(def priors-by-label
  (into {} (map (juxt :label :values) prior-options)))

(def number-formatter (js/Intl.NumberFormat. "en-US"))

(defn format-number [number]
  (.format number-formatter number))

(defn format-decimal [number digits]
  (.toFixed number digits))

(defn counted-label [number singular]
  (str number " " singular (when (not= number 1) "s")))

(defn modal-grid-value [values]
  (let [index (first (apply max-key second (map-indexed vector values)))]
    (nth probability-grid index)))

;; Accessible SVG primitives. The old application delegated these marks to
;; Vega; this recreation keeps the transformation visible in ClojureScript.

(defn x-position [index point-count]
  (+ 52 (* index (/ 548 (max 1 (dec point-count))))))

(defn y-position [value maximum]
  (- 218 (* 174 (/ value (max maximum 1.0e-12)))))

(defn values-path [values]
  (let [maximum (apply max 1.0e-12 values)
        point-count (count values)]
    (str/join
     " "
     (map-indexed
      (fn [index value]
        (str (if (zero? index) "M" "L")
             (x-position index point-count) " "
             (y-position value maximum)))
      values))))

(defn line-chart
  [{:keys [id title description values secondary-values x-label y-label]}]
  (let [all-values (into (vec values) (or secondary-values []))
        maximum (apply max 1.0e-12 all-values)]
    [:figure.bp-chart
     [:h4 title]
     [:svg {:view-box "0 0 640 285"
            :role "img"
            :aria-labelledby (str id "-title " id "-desc")}
      [:title {:id (str id "-title")} title]
      [:desc {:id (str id "-desc")} description]
      (for [[p label] [[0 "0"] [0.25 ".25"] [0.5 ".50"] [0.75 ".75"] [1 "1"]]
            :let [x (+ 52 (* 548 p))]]
        ^{:key p}
        [:g
         [:line.bp-guide {:x1 x :x2 x :y1 44 :y2 218}]
         [:text {:x x :y 240 :text-anchor "middle" :font-size 12 :fill "currentColor"}
          label]])
      [:line.bp-axis {:x1 52 :x2 600 :y1 218 :y2 218}]
      [:line.bp-axis {:x1 52 :x2 52 :y1 44 :y2 218}]
      [:text {:x 326 :y 273 :text-anchor "middle" :font-size 12 :fill "currentColor"}
       x-label]
      [:text {:x 54 :y 27 :font-size 12 :fill "currentColor"} y-label]
      [:path.bp-line {:d (values-path values)}]
      (when secondary-values
        [:path.bp-line-secondary
         {:d (let [point-count (count secondary-values)]
               (str/join
                " "
                (map-indexed
                 (fn [index value]
                   (str (if (zero? index) "M" "L")
                        (x-position index point-count) " "
                        (y-position value maximum)))
                 secondary-values)))}])]]))

(defn proportion-chart [{:keys [water land]} id]
  (let [n (+ water land)
        water-rate (if (pos? n) (/ water n) 0)
        land-rate (if (pos? n) (/ land n) 0)]
    [:figure.bp-chart
     [:h4 (str "Observed sample · n = " n)]
     [:svg {:view-box "0 0 640 285"
            :role "img"
            :aria-labelledby (str id "-title " id "-desc")}
      [:title {:id (str id "-title")} "Observed water and land proportions"]
      [:desc {:id (str id "-desc")}
       (str water " water and " land " land observations out of " n ".")]
      (for [[rate label] [[0 "0%"] [0.25 "25%"] [0.5 "50%"] [0.75 "75%"] [1 "100%"]]
            :let [y (- 218 (* 174 rate))]]
        ^{:key rate}
        [:g
         [:line.bp-guide {:x1 52 :x2 600 :y1 y :y2 y}]
         [:text {:x 46 :y (+ y 4) :text-anchor "end" :font-size 11 :fill "currentColor"}
          label]])
      [:line.bp-axis {:x1 52 :x2 600 :y1 218 :y2 218}]
      [:rect.bp-bar-water {:x 150 :y (- 218 (* 174 water-rate))
                           :width 110 :height (* 174 water-rate)}]
      [:rect.bp-bar-land {:x 375 :y (- 218 (* 174 land-rate))
                          :width 110 :height (* 174 land-rate)}]
      [:text {:x 205 :y 241 :text-anchor "middle" :font-size 13 :fill "currentColor"}
       (str "Water " (format-decimal (* 100 water-rate) 1) "%")]
      [:text {:x 430 :y 241 :text-anchor "middle" :font-size 13 :fill "currentColor"}
       (str "Land " (format-decimal (* 100 land-rate) 1) "%")]
      [:text {:x 326 :y 273 :text-anchor "middle" :font-size 12 :fill "currentColor"}
       "Outcome"]]]))

(defn control-button
  ([label on-click] (control-button label on-click {}))
  ([label on-click attributes]
   [:button (merge {:type "button"
                    :class "bp-button"
                    :on-click on-click
                    :on-key-down (fn [event]
                                   (when (contains? #{"Enter" " "} (.-key event))
                                     (.preventDefault event)
                                     (on-click)))}
                   attributes)
    label]))

;; Simulation 1: sequential globe observations.

(def max-update-samples 200)
(def update-seed 20260713)
(def initial-update-state
  {:prior "Uniform" :samples [] :speed 1.0 :running? false :timer nil})
(defonce update-state (r/atom initial-update-state))
(defonce update-rng (atom (make-rng update-seed)))

(defn cancel-update-timer! []
  (when-let [timer (:timer @update-state)]
    (js/clearTimeout timer))
  (swap! update-state assoc :timer nil :running? false))

(defn generated-observation! []
  (if (< (uniform! @update-rng) 0.6) :water :land))

(defn add-generated-observations! [amount]
  (let [remaining (- max-update-samples (count (:samples @update-state)))
        amount (min amount remaining)
        additions (vec (repeatedly amount generated-observation!))]
    (swap! update-state update :samples into additions)))

(defn add-observation! [outcome]
  (when (< (count (:samples @update-state)) max-update-samples)
    (swap! update-state update :samples conj outcome)))

(declare update-tick!)

(defn schedule-update-tick! []
  (let [delay (/ 1000 (:speed @update-state))]
    (swap! update-state assoc :timer (js/setTimeout update-tick! delay))))

(defn update-tick! []
  (when (:running? @update-state)
    (add-generated-observations! 1)
    (if (< (count (:samples @update-state)) max-update-samples)
      (schedule-update-tick!)
      (swap! update-state assoc :running? false :timer nil))))

(defn start-update! []
  (when (= max-update-samples (count (:samples @update-state)))
    (reset! update-rng (make-rng update-seed))
    (swap! update-state assoc :samples []))
  (cancel-update-timer!)
  (swap! update-state assoc :running? true)
  (update-tick!))

(defn reset-update! []
  (cancel-update-timer!)
  (reset! update-rng (make-rng update-seed))
  (let [prior (:prior @update-state)
        speed (:speed @update-state)]
    (reset! update-state (assoc initial-update-state :prior prior :speed speed))))

(defn remove-observations! [amount]
  (swap! update-state update :samples
         #(vec (drop-last (min amount (count %)) %))))

(defn update-process-strip
  [{:keys [prior latest counts likelihood raw-weights posterior]}]
  (let [likelihood-mode (modal-grid-value likelihood)
        raw-mode (modal-grid-value raw-weights)
        posterior-mode (modal-grid-value posterior)
        latest-label (case latest
                       :water "latest: water"
                       :land "latest: land"
                       "waiting for data")]
    [:div.bp-process-strip
     {:role "img"
      :aria-label
      (str prior " prior multiplied by likelihood from " (:water counts)
           " water and " (:land counts) " land observations; raw weights "
           "are normalised to form a posterior peaking near p equals "
           (format-decimal posterior-mode 3) ".")}
     [:div.bp-process-step
      [:strong "Prior"]
      [:small prior]]
     [:div.bp-process-symbol {:aria-hidden "true"} "×"]
     [:div.bp-process-step
      [:strong "Likelihood"]
      [:small (str (:water counts) " W · " (:land counts) " L")]
      [:small (str latest-label " · peak p ≈ " (format-decimal likelihood-mode 3))]]
     [:div.bp-process-symbol {:aria-hidden "true"} "="]
     [:div.bp-process-step
      [:strong "Raw weights"]
      [:small (str "peak p ≈ " (format-decimal raw-mode 3))]]
     [:div.bp-process-symbol {:aria-label "then normalise"} "→"]
     [:div.bp-process-step
      [:strong "Posterior"]
      [:small (str "normalised · peak p ≈ " (format-decimal posterior-mode 3))]]]))

(defn globe-update-simulator []
  (let [{:keys [prior samples speed running?]} @update-state
        selected-prior (get priors-by-label prior)
        previous-samples (vec (butlast samples))
        counts (observation-counts samples)
        previous-counts (observation-counts previous-samples)
        current-posterior (standard-posterior selected-prior samples)
        previous-posterior (standard-posterior selected-prior previous-samples)
        latest (peek samples)
        latest-likelihood (mapv (fn [p]
                                  (case latest
                                    :water p
                                    :land (- 1 p)
                                    1.0))
                                probability-grid)
        ordered-likelihood (normalize-mean-one
                            (mapv #(sequence-likelihood % (:water counts) (:land counts))
                                  probability-grid))
        any-order-likelihood (normalize-mean-one
                              (mapv #(binomial-likelihood % (:water counts) (:land counts))
                                    probability-grid))
        raw-posterior-weights (mapv * selected-prior any-order-likelihood)
        combination-count (binomial-coefficient (:n counts) (:water counts))]
    [:section.bp-shell {:aria-labelledby "globe-simulator-heading"}
     [:h3#globe-simulator-heading "Calculate a posterior distribution"]
     [:details.bp-details
      [:summary "Question"]
      [:div
       [:p "Toss and catch a globe repeatedly. Record whether the point beneath your index finger is water or land. What do those observations imply about p, the proportion of the globe covered by water?"]
       [:p.bp-note "Adapted from Richard McElreath, Statistical Rethinking, section 2.2."]]]
     [:div.bp-field
      [:label {:for "prior-choice"} "Prior before any data"]
      [:select#prior-choice.bp-select
       {:value prior
        :on-change #(swap! update-state assoc :prior (.. % -target -value))}
       (for [{:keys [label]} prior-options]
         ^{:key label} [:option {:value label} label])]]
     [:div.bp-controls
      [control-button (if running? "Pause" "Play")
       #(if running? (cancel-update-timer!) (start-update!))
       {:class "bp-button bp-primary" :aria-pressed running?}]
      [:label {:for "update-speed"} "Speed"]
      [:input#update-speed
       {:type "range" :min 0.5 :max 10 :step 0.5 :value speed
        :on-change #(swap! update-state assoc :speed
                           (js/parseFloat (.. % -target -value)))}]
      [:span (str speed " samples/second")]
      [control-button "Random sample" #(add-generated-observations! 1)
       {:disabled (>= (:n counts) max-update-samples)}]
      [control-button "+10" #(add-generated-observations! 10)
       {:disabled (> (+ (:n counts) 10) max-update-samples)}]
      [control-button "Water" #(add-observation! :water)
       {:disabled (>= (:n counts) max-update-samples)}]
      [control-button "Land" #(add-observation! :land)
       {:disabled (>= (:n counts) max-update-samples)}]
      [control-button "Remove one" #(remove-observations! 1)
       {:disabled (zero? (:n counts))}]
      [control-button "Remove 10" #(remove-observations! 10)
       {:disabled (< (:n counts) 10)}]
      [control-button "Clear" reset-update!
       {:disabled (zero? (:n counts))}]]
     [:p.bp-stat {:aria-live "polite"}
      [:strong (str (:water counts) " water, " (:land counts) " land, " (:n counts) " total. ")]
      (str "Latest observation: " (if latest (name latest) "none") ".")]
     [:div.bp-sample-sequence
      (if (seq samples)
        (str/join " " (map #(if (= % :water) "W" "L") samples))
        "No observations yet.")]
     [update-process-strip
      {:prior prior
       :latest latest
       :counts counts
       :likelihood any-order-likelihood
       :raw-weights raw-posterior-weights
       :posterior current-posterior}]
     [:details.bp-details
      [:summary "Formulae for the current observations"]
      [:div
       [:p.bp-formula
        (str "One ordered sequence: p^" (:water counts)
             "(1-p)^" (:land counts))]
       [:p.bp-formula
        (str "Possible orderings: (" (:water counts) "+" (:land counts)
             ")!/(" (:water counts) "!" (:land counts) "!) = "
             (format-number combination-count))]
       [:p.bp-formula
        (str "Any ordering: " (format-number combination-count)
             " p^" (:water counts) "(1-p)^" (:land counts))]
       [:p.bp-formula "Posterior ∝ likelihood × prior; the grid weights are then normalised."]]]
     [:div.bp-grid
      [proportion-chart counts "globe-sample-bars"]
      [line-chart {:id "chosen-prior" :title (str prior " prior")
                   :description (str "The selected " prior " prior across water proportion p.")
                   :values selected-prior :x-label "Water proportion p" :y-label "Prior weight"}]
      [line-chart {:id "previous-posterior" :title (str "Previous posterior · n = " (:n previous-counts))
                   :description "Posterior before the latest observation."
                   :values previous-posterior :x-label "Water proportion p" :y-label "Relative density"}]
      [line-chart {:id "latest-likelihood" :title (str "Latest-observation likelihood · " (or (some-> latest name) "none"))
                   :description "Likelihood supplied by the latest water or land observation."
                   :values latest-likelihood :x-label "Water proportion p" :y-label "Likelihood"}]
      [line-chart {:id "ordered-likelihood" :title "This ordered sequence"
                   :description "Relative likelihood of this exact ordered water and land sequence."
                   :values ordered-likelihood :x-label "Water proportion p" :y-label "Relative likelihood"}]
      [line-chart {:id "any-order-likelihood" :title "Any ordering with these counts"
                   :description "Relative binomial likelihood of any ordering with these water and land counts."
                   :values any-order-likelihood :x-label "Water proportion p" :y-label "Relative likelihood"}]
      [line-chart {:id "current-posterior" :title "Current posterior"
                   :description "Current posterior distribution after combining the full likelihood and selected prior."
                   :values current-posterior :x-label "Water proportion p" :y-label "Relative density"}]]
     [:p.bp-note "Random observations use true p = 0.6 and seed 20260713. Reset replays the same sequence. The two full-likelihood panels share a normalised shape because their binomial coefficient differs only by a constant."]]))

;; Simulation 2: posterior sampling and absolute-loss decisions.

(def posterior-data-seed 320260713)
(def posterior-draw-seed 420260713)
(def fixed-draw-seed 520260713)

(def fixed-globe-samples
  (let [rng (make-rng posterior-data-seed)]
    (vec (repeatedly 100 #(if (< (uniform! rng) 0.6) :water :land)))))

(def fixed-globe-counts (observation-counts fixed-globe-samples))
(def fixed-posterior (standard-posterior uniform-prior fixed-globe-samples))
(def fixed-posterior-mass (normalize-mass fixed-posterior))

(defn weighted-grid-sample! [rng]
  (let [target (uniform! rng)]
    (loop [index 0
           cumulative (first fixed-posterior-mass)]
      (if (or (>= cumulative target)
              (= index (dec (count fixed-posterior-mass))))
        (nth probability-grid index)
        (recur (inc index)
               (+ cumulative (nth fixed-posterior-mass (inc index))))))))

(def fixed-ten-thousand-samples
  (let [rng (make-rng fixed-draw-seed)]
    (vec (repeatedly 10000 #(weighted-grid-sample! rng)))))

(defn expected-loss [decision]
  (reduce + (map (fn [p mass]
                   (* (js/Math.abs (- decision p)) mass))
                 probability-grid
                 fixed-posterior-mass)))

(def loss-values (mapv expected-loss probability-grid))
(def minimum-loss-pair
  (reduce (fn [[best-p best-loss] [p loss]]
            (if (< loss best-loss) [p loss] [best-p best-loss]))
          [0 js/Infinity]
          (map vector probability-grid loss-values)))

(defn median [numbers]
  (let [ordered (vec (sort numbers))]
    (nth ordered (js/Math.floor (/ (count ordered) 2)))))

(def fixed-posterior-median (median fixed-ten-thousand-samples))
(def maximum-posterior-samples 2000)
(def initial-posterior-sampling-state
  {:samples [] :speed 50 :running? false :timer nil})
(defonce posterior-sampling-state (r/atom initial-posterior-sampling-state))
(defonce posterior-sampling-rng (atom (make-rng posterior-draw-seed)))

(defn cancel-posterior-timer! []
  (when-let [timer (:timer @posterior-sampling-state)]
    (js/clearTimeout timer))
  (swap! posterior-sampling-state assoc :timer nil :running? false))

(defn add-posterior-samples! []
  (let [{:keys [samples speed]} @posterior-sampling-state
        amount (min speed (- maximum-posterior-samples (count samples)))
        additions (vec (repeatedly amount #(weighted-grid-sample! @posterior-sampling-rng)))]
    (swap! posterior-sampling-state update :samples into additions)))

(declare posterior-sampling-tick!)

(defn schedule-posterior-tick! []
  (swap! posterior-sampling-state assoc
         :timer (js/setTimeout posterior-sampling-tick! 1000)))

(defn posterior-sampling-tick! []
  (when (:running? @posterior-sampling-state)
    (add-posterior-samples!)
    (if (< (count (:samples @posterior-sampling-state)) maximum-posterior-samples)
      (schedule-posterior-tick!)
      (swap! posterior-sampling-state assoc :running? false :timer nil))))

(defn reset-posterior-sampling! []
  (cancel-posterior-timer!)
  (reset! posterior-sampling-rng (make-rng posterior-draw-seed))
  (let [speed (:speed @posterior-sampling-state)]
    (reset! posterior-sampling-state
            (assoc initial-posterior-sampling-state :speed speed))))

(defn start-posterior-sampling! []
  (when (= maximum-posterior-samples
           (count (:samples @posterior-sampling-state)))
    (reset-posterior-sampling!))
  (cancel-posterior-timer!)
  (swap! posterior-sampling-state assoc :running? true)
  (posterior-sampling-tick!))

(def posterior-bin-count 200)

(defn posterior-histogram [samples]
  (reduce (fn [counts sample]
            (let [index (min (dec posterior-bin-count)
                             (js/Math.floor (* posterior-bin-count sample)))]
              (update counts index inc)))
          (vec (repeat posterior-bin-count 0))
          samples))

(defn point-path [samples]
  (let [sample-count (count samples)]
    (str/join
     " "
     (map-indexed
      (fn [index p]
        (let [x (+ 50 (* index (/ 550 (max 1 (dec sample-count)))))
              y (- 220 (* 176 p))]
          (str "M" x " " y "l0.01 0")))
      samples))))

(defn posterior-trace-chart [samples title id]
  [:figure.bp-chart
   [:h4 title]
   (if (seq samples)
     [:svg {:view-box "0 0 640 285" :role "img"
            :aria-labelledby (str id "-title " id "-desc")}
      [:title {:id (str id "-title")} title]
      [:desc {:id (str id "-desc")}
       (str (count samples) " posterior samples plotted by draw number and water proportion.")]
      (for [[p label] [[0 "0"] [0.25 ".25"] [0.5 ".50"] [0.75 ".75"] [1 "1"]]
            :let [y (- 220 (* 176 p))]]
        ^{:key p}
        [:g
         [:line.bp-guide {:x1 50 :x2 600 :y1 y :y2 y}]
         [:text {:x 44 :y (+ y 4) :text-anchor "end" :font-size 11 :fill "currentColor"}
          label]])
      [:line.bp-axis {:x1 50 :x2 600 :y1 220 :y2 220}]
      [:line.bp-axis {:x1 50 :x2 50 :y1 44 :y2 220}]
      [:path.bp-points {:d (point-path samples)}]
      [:text {:x 325 :y 272 :text-anchor "middle" :font-size 12 :fill "currentColor"}
       "Draw number"]]
     [:div.bp-empty "Press Play to draw from the posterior."])])

(defn posterior-count-chart [samples title id]
  (let [counts (posterior-histogram samples)
        maximum (apply max 1 counts)]
    [:figure.bp-chart
     [:h4 title]
     (if (seq samples)
       [:svg {:view-box "0 0 640 285" :role "img"
              :aria-labelledby (str id "-title " id "-desc")}
        [:title {:id (str id "-title")} title]
        [:desc {:id (str id "-desc")}
         (str (count samples) " posterior samples counted in 200 bins.")]
        [:line.bp-axis {:x1 50 :x2 600 :y1 220 :y2 220}]
        (for [[index count] (map-indexed vector counts)
              :let [x (+ 50 (* index (/ 550 posterior-bin-count)))
                    height (* 176 (/ count maximum))]]
          ^{:key index}
          [:rect {:x x :y (- 220 height)
                  :width (max 1.2 (- (/ 550 posterior-bin-count) 0.2))
                  :height height :fill "var(--bp-accent, #1464b5)" :fill-opacity 0.78}])
        (for [[p label] [[0 "0"] [0.25 ".25"] [0.5 ".50"] [0.75 ".75"] [1 "1"]]
              :let [x (+ 50 (* 550 p))]]
          ^{:key p}
          [:text {:x x :y 242 :text-anchor "middle" :font-size 11 :fill "currentColor"}
           label])
        [:text {:x 325 :y 272 :text-anchor "middle" :font-size 12 :fill "currentColor"}
         "Water proportion p"]]
       [:div.bp-empty "The 200-bin count appears as samples accumulate."])]))

(defn posterior-density-chart [samples title id]
  (let [counts (posterior-histogram samples)
        average (/ (max 1 (reduce + counts)) posterior-bin-count)
        density (mapv #(/ % average) counts)]
    [line-chart {:id id :title title
                 :description (str (count samples) " posterior samples converted to a standardised 200-bin density.")
                 :values density :x-label "Water proportion p" :y-label "Sample density"}]))

(defn posterior-sample-panels [samples prefix id-prefix]
  [:div.bp-grid
   [posterior-trace-chart samples (str prefix " · trace") (str id-prefix "-trace")]
   [posterior-count-chart samples (str prefix " · count in 200 bins") (str id-prefix "-count")]
   [posterior-density-chart samples (str prefix " · standardised density") (str id-prefix "-density")]])

(defn posterior-sampling-simulator []
  (let [{:keys [samples speed running?]} @posterior-sampling-state
        sample-count (count samples)
        live-median (when (seq samples) (median samples))
        direct-decision (first minimum-loss-pair)
        dollar-loss (js/Math.round (* 100 (js/Math.abs (- 0.6 fixed-posterior-median))))]
    [:section.bp-shell {:aria-labelledby "posterior-sampling-heading"}
     [:h3#posterior-sampling-heading "Sample from the posterior"]
     [:details.bp-details
      [:summary "Question"]
      [:div
       [:p "Choose d, your estimate of the globe's water proportion. A perfect answer earns $100, with $1 subtracted for every 0.01 of absolute error. Which decision minimises expected loss?"]
       [:p.bp-note "Adapted from Richard McElreath, Statistical Rethinking, section 3.2."]]]
     [:p.bp-stat
      (str "Seeded dataset: " (:water fixed-globe-counts) " water and "
           (:land fixed-globe-counts) " land among 100 observations generated with true p = 0.6.")]
     [:div.bp-grid
      [proportion-chart fixed-globe-counts "posterior-data-bars"]
      [line-chart {:id "fixed-posterior" :title "Posterior from 100 observations"
                   :description "Grid posterior for the fixed set of 100 globe observations."
                   :values fixed-posterior :x-label "Water proportion p" :y-label "Relative density"}]
      [line-chart {:id "absolute-loss" :title (str "Expected absolute loss · minimum at d = " direct-decision)
                   :description "Expected absolute loss across candidate decisions from zero to one."
                   :values loss-values :x-label "Decision d" :y-label "Expected loss"}]]
     [:p
      "The direct grid calculation minimises expected loss at "
      [:strong (format-decimal direct-decision 3)]
      ". Theory says that absolute loss is also minimised by the posterior median. Draw from the posterior to watch that estimate settle."]
     [:div.bp-controls
      [control-button (if running? "Pause" "Play")
       #(if running? (cancel-posterior-timer!) (start-posterior-sampling!))
       {:class "bp-button bp-primary" :aria-pressed running?}]
      [:label {:for "posterior-speed"} "Speed"]
      [:select#posterior-speed.bp-select
       {:value speed
        :on-change #(swap! posterior-sampling-state assoc :speed
                           (js/parseInt (.. % -target -value)))}
       (for [option [1 5 10 50 100 200]]
         ^{:key option}
         [:option {:value option} (str option " samples/second")])]
      [control-button "Clear samples" reset-posterior-sampling!
       {:disabled (zero? sample-count)}]]
     [:progress.bp-progress {:value sample-count :max maximum-posterior-samples
                             :aria-label "Posterior samples completed"}]
     [:p.bp-stat {:aria-live (if running? "off" "polite")}
      [:strong (str sample-count " / " maximum-posterior-samples " animated samples")]
      (when live-median
        (str " · live median " (format-decimal live-median 3)))]
     [posterior-sample-panels samples "Animated samples" "animated"]
     [:h4 "Fixed 10,000-sample comparison"]
     [:p.bp-stat
      (str "Median = " (format-decimal fixed-posterior-median 6)
           ". At $1 per 0.01 of error from the true p = 0.6, rounded loss = $"
           dollar-loss "; final payoff = $" (- 100 dollar-loss) ".")]
     [posterior-sample-panels fixed-ten-thousand-samples "10,000 samples" "fixed"]
     [:p.bp-note "All posterior draws are discrete grid values spaced by 0.005. The 10,000-point trace is rendered as one SVG path, avoiding 10,000 DOM nodes while retaining every draw."]]))

;; Simulation 3: sequential grid updating for Gaussian height parameters.

(def adult-heights
  [151.765 139.7 136.525 156.845 145.415 163.83 149.225 168.91 147.955
   165.1 154.305 151.13 144.78 149.9 150.495 163.195 157.48 143.9418
   161.29 156.21 146.4 148.59 147.32 147.955 161.925 146.05 146.05
   152.7048 142.875 142.875 147.955 160.655 151.765 162.8648 171.45
   147.32 147.955 154.305 143.51 146.7 157.48 165.735 152.4 141.605
   158.8 155.575 164.465 151.765 161.29 154.305 145.415 145.415 152.4
   163.83 144.145 153.67 142.875 167.005 158.4198 165.735 149.86 154.94
   160.9598 161.925 147.955 159.385 148.59 136.525 158.115 144.78
   156.845 179.07 170.18 146.05 147.32 162.56 152.4 160.02 149.86
   142.875 167.005 159.385 154.94 162.56 152.4 170.18 146.05 159.385
   151.13 160.655 169.545 158.75 149.86 153.035 161.925 162.56 149.225
   163.195 161.925 145.415 163.195 151.13 150.495 170.815 157.48 152.4
   147.32 145.415 157.48 154.305 167.005 142.875 152.4 160 159.385 149.86
   160.655 160.655 149.225 140.97 154.94 141.605 160.02 150.1648 155.575
   156.21 153.035 167.005 149.86 147.955 159.385 161.925 155.575 159.385
   146.685 172.72 166.37 141.605 151.765 156.845 148.59 157.48 149.86
   147.955 153.035 160.655 149.225 138.43 162.56 149.225 158.75 149.86
   158.115 156.21 148.59 143.51 154.305 157.48 157.48 154.305 168.275
   145.415 149.225 154.94 162.56 156.845 161.0106 144.78 143.51 149.225
   149.86 165.735 144.145 157.48 154.305 163.83 156.21 144.145 162.56
   146.05 154.94 144.78 146.685 152.4 163.83 165.735 156.21 152.4
   140.335 163.195 151.13 171.1198 149.86 163.83 141.605 149.225 146.05
   161.29 162.56 145.415 170.815 159.385 159.4 153.67 160.02 150.495
   149.225 142.875 142.113 147.32 162.56 164.465 160.02 153.67 167.005
   151.13 153.035 139.065 152.4 154.94 147.955 144.145 155.575 150.495
   155.575 154.305 157.48 168.91 150.495 160.02 167.64 144.145 145.415
   160.02 164.465 153.035 149.225 160.02 149.225 153.67 150.495 151.765
   158.115 149.225 151.765 154.94 161.29 148.59 160.655 157.48 167.005
   157.48 152.4 152.4 161.925 152.4 159.385 142.24 168.91 160.02 158.115
   152.4 155.575 154.305 156.845 156.21 168.275 147.955 157.48 160.7
   161.29 150.495 163.195 148.59 148.59 161.925 153.67 151.13 163.83
   153.035 151.765 156.21 140.335 158.75 142.875 151.9428 161.29 160.9852
   144.78 160.02 160.9852 165.989 157.988 154.94 160.655 147.32 146.7
   147.32 172.9994 158.115 147.32 165.989 149.86 161.925 163.83 160.02
   154.94 152.4 146.05 151.9936 151.765 144.78 160.655 151.13 153.67
   147.32 139.7 157.48 154.94 143.51 158.115 147.32 160.02 165.1 154.94
   153.67 141.605 163.83 161.29 154.9 161.3 170.18 149.86 160.655 154.94
   166.37 148.2852 151.765 148.59 153.67 146.685 154.94 156.21 160.655
   146.05 156.21 152.4 162.56 142.875 162.56 156.21 158.75])

(def mean-grid (mapv #(+ 150 (* 0.25 %)) (range 41)))
(def sd-grid (mapv #(+ 7 (* 0.05 %)) (range 41)))
(def gaussian-grid
  (vec (for [sd sd-grid
             mean mean-grid]
         {:mean mean :sd sd})))

(defn normal-density [x mean sd]
  (/ (js/Math.exp (/ (* -0.5 (js/Math.pow (- x mean) 2))
                     (js/Math.pow sd 2)))
     (* (js/Math.sqrt (* 2 js/Math.PI)) sd)))

(def gaussian-prior
  (normalize-mean-one
   (mapv (fn [{:keys [mean]}]
           (normal-density mean 178 20))
         gaussian-grid)))

(defonce gaussian-cache
  (atom {:posteriors [gaussian-prior]
         :likelihoods []}))

(defn height-likelihood [height]
  (normalize-mean-one
   (mapv (fn [{:keys [mean sd]}]
           (normal-density height mean sd))
         gaussian-grid)))

(defn ensure-gaussian-step! [target-posterior-count]
  (loop []
    (let [{:keys [posteriors likelihoods]} @gaussian-cache
          observed-count (dec (count posteriors))]
      (when (< observed-count target-posterior-count)
        (let [likelihood (height-likelihood (nth adult-heights observed-count))
              posterior (normalize-mean-one
                         (mapv * (peek posteriors) likelihood))]
          (reset! gaussian-cache
                  {:posteriors (conj posteriors posterior)
                   :likelihoods (conj likelihoods likelihood)})
          (recur))))))

(defn heat-opacity [value maximum]
  (let [ratio (/ value (max maximum 1.0e-300))
        intensity (/ (js/Math.log1p (* 999 ratio)) (js/Math.log 1000))]
    (+ 0.035 (* 0.965 intensity))))

(defn heat-map [{:keys [id title description values]}]
  (let [maximum (apply max 1.0e-300 values)
        cell-size 8]
    [:figure.bp-chart
     [:h4 title]
     [:svg {:view-box "0 0 410 410" :role "img"
            :aria-labelledby (str id "-title " id "-desc")}
      [:title {:id (str id "-title")} title]
      [:desc {:id (str id "-desc")} description]
      (for [[index value] (map-indexed vector values)
            :let [column (mod index 41)
                  row (js/Math.floor (/ index 41))
                  x (+ 54 (* column cell-size))
                  y (+ 28 (* (- 40 row) cell-size))]]
        ^{:key index}
        [:rect {:x x :y y :width cell-size :height cell-size
                :fill "var(--bp-accent, #1464b5)" :fill-opacity (heat-opacity value maximum)}])
      [:rect {:x 54 :y 28 :width (* 41 cell-size) :height (* 41 cell-size)
              :fill "none" :stroke "currentColor" :stroke-opacity 0.45}]
      (for [[mean label] [[150 "150"] [155 "155"] [160 "160"]]
            :let [x (+ 54 (* (- mean 150) (/ (* 40 cell-size) 10)))]]
        ^{:key mean}
        [:text {:x x :y 377 :text-anchor "middle" :font-size 11 :fill "currentColor"}
         label])
      (for [[sd label] [[7 "7"] [8 "8"] [9 "9"]]
            :let [y (+ 34 (* (- 9 sd) (/ (* 40 cell-size) 2)))]]
        ^{:key sd}
        [:text {:x 47 :y y :text-anchor "end" :font-size 11 :fill "currentColor"}
         label])
      [:text {:x 218 :y 401 :text-anchor "middle" :font-size 12 :fill "currentColor"}
       "Mean μ (cm)"]
      [:text {:x 12 :y 194 :transform "rotate(-90 12 194)"
              :text-anchor "middle" :font-size 12 :fill "currentColor"}
       "Standard deviation σ (cm)"]
      (for [index (range 10)
            :let [x (+ 297 (* index 9))]]
        ^{:key index}
        [:rect {:x x :y 386 :width 9 :height 8 :fill "var(--bp-accent, #1464b5)"
                :fill-opacity (+ 0.035 (* 0.965 (/ index 9)))}])
      [:text {:x 293 :y 393 :text-anchor "end" :font-size 9 :fill "currentColor"} "low"]
      [:text {:x 390 :y 393 :font-size 9 :fill "currentColor"} "high"]]]))

(def maximum-height-step (dec (count adult-heights)))
(def initial-gaussian-state {:step 0 :speed 1.0 :running? false :timer nil})
(defonce gaussian-state (r/atom initial-gaussian-state))

(defn cancel-gaussian-timer! []
  (when-let [timer (:timer @gaussian-state)]
    (js/clearTimeout timer))
  (swap! gaussian-state assoc :timer nil :running? false))

(declare gaussian-tick!)

(defn schedule-gaussian-tick! []
  (swap! gaussian-state assoc
         :timer (js/setTimeout gaussian-tick! (/ 1000 (:speed @gaussian-state)))))

(defn gaussian-tick! []
  (when (:running? @gaussian-state)
    (if (< (:step @gaussian-state) maximum-height-step)
      (do
        (swap! gaussian-state update :step inc)
        (schedule-gaussian-tick!))
      (swap! gaussian-state assoc :running? false :timer nil))))

(defn start-gaussian! []
  (when (= (:step @gaussian-state) maximum-height-step)
    (swap! gaussian-state assoc :step 0))
  (cancel-gaussian-timer!)
  (swap! gaussian-state assoc :running? true)
  (schedule-gaussian-tick!))

(defn gaussian-height-simulator []
  (let [{:keys [step speed running?]} @gaussian-state
        _ (ensure-gaussian-step! (inc step))
        {:keys [posteriors likelihoods]} @gaussian-cache
        before (nth posteriors step)
        likelihood (nth likelihoods step)
        after (nth posteriors (inc step))
        height (nth adult-heights step)]
    [:section.bp-shell {:aria-labelledby "gaussian-simulator-heading"}
     [:h3#gaussian-simulator-heading "Update a Gaussian height model"]
     [:details.bp-details
      [:summary "Question"]
      [:div
       [:p "Every pair (μ, σ) defines a possible Gaussian distribution. Give each pair a prior plausibility, then use the observed heights to rank the pairs by posterior compatibility with the data and model."]
       [:p.bp-note "Adapted from Richard McElreath, Statistical Rethinking, section 4.3."]]]
     [:details.bp-details
      [:summary "Prior"]
      [:div
       [:p "The grid is uniform over σ from 7 to 9 cm. Across μ from 150 to 160 cm, prior weight follows a Normal(178, 20) density."]
       [heat-map {:id "gaussian-prior" :title "Prior · μ ~ Normal(178, 20)"
                  :description "Prior plausibility over the 41 by 41 grid of mean and standard-deviation values."
                  :values gaussian-prior}]]]
     [:div.bp-controls
      [control-button (if running? "Pause" "Play")
       #(if running? (cancel-gaussian-timer!) (start-gaussian!))
       {:class "bp-button bp-primary" :aria-pressed running?}]
      [:label {:for "gaussian-speed"} "Speed"]
      [:input#gaussian-speed
       {:type "range" :min 0.5 :max 10 :step 0.5 :value speed
        :on-change #(swap! gaussian-state assoc :speed
                           (js/parseFloat (.. % -target -value)))}]
      [:span (str (counted-label speed "height") "/second")]
      [control-button "Previous"
       #(swap! gaussian-state update :step (fn [current] (max 0 (dec current))))
       {:disabled (zero? step)}]
      [control-button "Next"
       #(swap! gaussian-state update :step (fn [current] (min maximum-height-step (inc current))))
       {:disabled (= step maximum-height-step)}]
      [control-button "Clear" #(do (cancel-gaussian-timer!)
                                   (swap! gaussian-state assoc :step 0))
       {:disabled (zero? step)}]]
     [:p.bp-stat {:aria-live (if running? "off" "polite")}
      [:strong (str "Height " (inc step) " of " (count adult-heights) ": " height " cm. ")]
      (str "Left: posterior after " (counted-label step "height")
           ". Right: posterior after " (counted-label (inc step) "height") ".")]
     [:div.bp-heat-grid
      [heat-map {:id "posterior-before-height" :title (str "Posterior after " (counted-label step "height"))
                 :description (str "Posterior plausibility over mean and standard deviation after " (counted-label step "observed height") ".")
                 :values before}]
      [heat-map {:id "current-height-likelihood" :title (str "Likelihood of height " height " cm")
                 :description (str "Relative Gaussian likelihood of the current height, " height " centimetres, over the parameter grid.")
                 :values likelihood}]
      [heat-map {:id "posterior-after-height" :title (str "Posterior after " (counted-label (inc step) "height"))
                 :description (str "Updated posterior after multiplying by the likelihood of height " height " centimetres and normalising.")
                 :values after}]]
     [:p.bp-note "Each map contains all 1,681 (μ, σ) candidates. Colour opacity uses a logarithmic scale within that panel; compare location and concentration, not absolute colour between panels."]]))

(defn ^:export mount []
  (when-let [root (js/document.getElementById "globe-update-simulator")]
    (rdom/render [globe-update-simulator] root))
  (when-let [root (js/document.getElementById "posterior-sampling-simulator")]
    (rdom/render [posterior-sampling-simulator] root))
  (when-let [root (js/document.getElementById "gaussian-height-simulator")]
    (rdom/render [gaussian-height-simulator] root)))

(if (= "loading" js/document.readyState)
  (.addEventListener js/document "DOMContentLoaded" mount)
  (mount))
