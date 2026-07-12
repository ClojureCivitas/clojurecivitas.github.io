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
                  :color "var(--bs-secondary-color, #5c636a)"
                  :margin ".35rem 0 0"}}
      "The Beta curves are normalized densities on one shared scale. The response likelihood uses its natural 0–1 scale; compare its shape, not its height, with the densities."]
     [:div {:style {:display "flex" :gap ".65rem" :flex-wrap "wrap" :margin-top "1rem"}}
      [control-button "Correct" #(record-response! :correct) "ve-correct"]
      [control-button "Don't know" #(record-response! :dont-know) "ve-dont-know"]
      [control-button "Reset" #(reset! state initial-state) "ve-reset"]]
     [:p {:style {:font-size ".9rem"
                  :color "var(--bs-secondary-color, #5c636a)"
                  :margin-bottom 0}}
      "Keyboard: Tab to a button, then press Enter or Space. “Don't know” contributes the same stage-one likelihood as a wrong answer, while the raw value remains distinct."]]))

(defn ^:export mount []
  (when-let [root (js/document.getElementById "beta-binomial-simulator")]
    (rdom/render [simulator] root)))

(mount)
