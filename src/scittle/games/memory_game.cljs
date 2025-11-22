(ns scittle.games.memory-game
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

;; ============================================================================
;; Utility Styles
;; ============================================================================

(defn merge-styles
  "Safely merges multiple style maps"
  [& styles]
  (apply merge (filter map? styles)))

;; ============================================================================
;; Game State
;; ============================================================================

(def game-state (r/atom {:sequence [] ; Computer's sequence
                         :player-sequence [] ; Player's current input
                         :playing? false ; Is game active?
                         :showing? false ; Is computer showing sequence?
                         :score 0 ; Current score/level
                         :game-over? false ; Game over state
                         :active-tile nil ; Currently lit up tile
                         :high-score 0})) ; Best score achieved

;; ============================================================================
;; Game Configuration
;; ============================================================================

(def game-colors ["#4caf50" ; Green
                  "#ff4444" ; Red
                  "#ff9800" ; Orange
                  "#2196f3"]) ; Blue

;; Musical notes (frequencies in Hz) for each color
(def color-frequencies [261.63 ; C4 - Green
                        329.63 ; E4 - Red
                        392.00 ; G4 - Orange
                        523.25]) ; C5 - Blue

;; ============================================================================
;; Audio Functions
;; ============================================================================

(def audio-context (when (exists? js/AudioContext) (js/AudioContext.)))

(defn play-tone
  "Plays a tone at the specified frequency for the given duration"
  [& {:keys [frequency duration]
      :or {frequency 440 duration 0.2}}]
  (when audio-context
    (try
      (let [oscillator (.createOscillator audio-context)
            gain-node (.createGain audio-context)]
        (.connect oscillator gain-node)
        (.connect gain-node (.-destination audio-context))
        ;; Set frequency and gain values
        (set! (.-value (.-frequency oscillator)) frequency)
        (set! (.-value (.-gain gain-node)) 0.3)
        (.start oscillator)
        (.stop oscillator (+ (.-currentTime audio-context) duration)))
      (catch js/Error e
        (js/console.error "Audio error:" e)))))

;; ============================================================================
;; Game Logic
;; ============================================================================

(defn add-to-sequence
  "Adds a random color to the game sequence"
  []
  (let [next-color (rand-int 4)]
    (swap! game-state update :sequence conj next-color)))

(defn show-sequence
  "Shows the current sequence to the player with visual and audio feedback"
  []
  (swap! game-state assoc
         :showing? true
         :player-sequence [])
  (let [sequence (:sequence @game-state)
        show-duration 600 ; ms between each color
        display-duration 400] ; ms to display each color
    ;; Show each color in sequence
    (doseq [[idx color-idx] (map-indexed vector sequence)]
      (js/setTimeout
       (fn []
         ;; Light up the tile and play sound
         (swap! game-state assoc :active-tile color-idx)
         (play-tone :frequency (nth color-frequencies color-idx)
                    :duration 0.5)
         ;; Turn off the tile after display duration
         (js/setTimeout
          #(swap! game-state assoc :active-tile nil)
          display-duration))
       (* idx show-duration)))
    ;; Enable player input after showing complete sequence
    (js/setTimeout
     #(swap! game-state assoc :showing? false)
     (* (count sequence) show-duration))))

(defn handle-tile-click
  "Handles player clicking a tile"
  [& {:keys [tile-index]}]
  (when (and (:playing? @game-state)
             (not (:showing? @game-state))
             (not (:game-over? @game-state)))
    ;; Play sound and show visual feedback
    (play-tone :frequency (nth color-frequencies tile-index)
               :duration 0.2)
    (swap! game-state assoc :active-tile tile-index)
    (js/setTimeout #(swap! game-state assoc :active-tile nil) 200)

    ;; Add to player sequence
    (swap! game-state update :player-sequence conj tile-index)

    (let [{:keys [sequence player-sequence score high-score]} @game-state
          current-position (dec (count player-sequence))]
      (cond
        ;; Wrong input - game over
        (not= (nth player-sequence current-position)
              (nth sequence current-position))
        (do
          (swap! game-state assoc
                 :game-over? true
                 :playing? false
                 :high-score (max score high-score))
          ;; Play error sound
          (play-tone :frequency 100 :duration 0.5))

        ;; Correct and sequence complete - next level
        (= (count player-sequence) (count sequence))
        (do
          (swap! game-state update :score inc)
          (js/setTimeout
           (fn []
             (add-to-sequence)
             (show-sequence))
           1000))))))

(defn start-game
  "Starts a new game"
  []
  (reset! game-state (merge @game-state
                            {:sequence []
                             :player-sequence []
                             :playing? true
                             :showing? false
                             :score 0
                             :game-over? false
                             :active-tile nil}))
  ;; Add first color and show it after a short delay
  (add-to-sequence)
  (js/setTimeout show-sequence 500))

;; ============================================================================
;; UI Components
;; ============================================================================

(defn color-tile
  "Renders a clickable color tile"
  [& {:keys [color index on-click disabled?]}]
  (let [active? (= (:active-tile @game-state) index)]
    [:div {:style (merge-styles
                   {:width "100px"
                    :height "100px"
                    :background-color color
                    :border-radius "10px"
                    :cursor (if disabled? "not-allowed" "pointer")
                    :opacity (cond
                               active? 1
                               disabled? 0.5
                               :else 0.8)
                    :transform (if active? "scale(1.1)" "scale(1)")
                    :box-shadow (if active?
                                  "0 0 20px rgba(255,255,255,0.8)"
                                  "0 2px 4px rgba(0,0,0,0.2)")
                    :transition "all 0.2s ease"})
           :on-click (when (and on-click (not disabled?))
                       #(on-click :tile-index index))
           :on-mouse-enter (fn [e]
                             (when (and (not disabled?) (not active?))
                               (set! (.. e -target -style -transform) "scale(1.05)")
                               (set! (.. e -target -style -opacity) "1")
                               (set! (.. e -target -style -boxShadow)
                                     "0 4px 8px rgba(0,0,0,0.3)")))
           :on-mouse-leave (fn [e]
                             (when (and (not disabled?) (not active?))
                               (set! (.. e -target -style -transform) "scale(1)")
                               (set! (.. e -target -style -opacity) "0.8")
                               (set! (.. e -target -style -boxShadow)
                                     "0 2px 4px rgba(0,0,0,0.2)")))}]))

(defn game-controls
  "Game control buttons"
  [& {:keys [playing? game-over? on-start on-give-up]}]
  [:div {:style {:display "flex"
                 :gap "10px"
                 :justify-content "center"}}
   (if (not playing?)
     [:button {:style {:padding "10px 20px"
                       :background "#4caf50"
                       :color "white"
                       :font-size "16px"
                       :border "none"
                       :border-radius "4px"
                       :cursor "pointer"
                       :transition "all 0.2s ease"}
               :on-click on-start
               :on-mouse-enter #(set! (.. % -target -style -background) "#45a049")
               :on-mouse-leave #(set! (.. % -target -style -background) "#4caf50")}
      (if game-over? "Try Again" "Start Game")]
     [:button {:style {:padding "10px 20px"
                       :background "#ff4444"
                       :color "white"
                       :font-size "16px"
                       :border "none"
                       :border-radius "4px"
                       :cursor "pointer"
                       :transition "all 0.2s ease"}
               :on-click on-give-up
               :on-mouse-enter #(set! (.. % -target -style -background) "#ff3333")
               :on-mouse-leave #(set! (.. % -target -style -background) "#ff4444")}
      "Give Up"])])

(defn score-display
  "Displays the current score or game over message"
  [& {:keys [playing? game-over? score high-score]}]
  [:div
   [:h3 {:style {:margin-bottom "20px"
                 :font-size "24px"
                 :color (cond
                          game-over? "#ff4444"
                          playing? "#4caf50"
                          :else "#333")}}
    (cond
      game-over? (str "Game Over! Final Score: " score)
      playing? (str "Level: " (inc score))
      :else "Press Start to Play")]

   ;; High score display
   (when (and (> high-score 0) (not playing?))
     [:div {:style {:margin-top "10px"
                    :padding "8px 16px"
                    :background "#f0f0f0"
                    :border-radius "20px"
                    :display "inline-block"
                    :color "#666"
                    :font-size "14px"}}
      (str "🏆 Best: " high-score)])])

(defn status-indicator
  "Shows the current game status"
  [& {:keys [playing? showing? player-sequence sequence]}]
  (when playing?
    [:div {:style {:margin-bottom "15px"
                   :height "20px"
                   :color "#666"
                   :font-size "14px"}}
     (cond
       showing? "Watch carefully..."
       (empty? player-sequence) "Your turn!"
       :else (str "Progress: " (count player-sequence) "/" (count sequence)))]))

(defn game-tips
  "Displays helpful game tips"
  []
  [:div {:style {:margin-top "30px"
                 :padding "15px"
                 :background "#f9f9f9"
                 :border-radius "8px"
                 :text-align "left"}}
   [:h4 {:style {:margin-bottom "10px"
                 :color "#4caf50"}}
    "How to Play:"]
   [:ul {:style {:margin "0"
                 :padding-left "20px"
                 :color "#666"
                 :font-size "14px"
                 :line-height "1.6"}}
    [:li "Watch the sequence of colors light up"]
    [:li "Click the tiles in the same order"]
    [:li "Each round adds one more color"]
    [:li "How many can you remember?"]]])

;; ============================================================================
;; Main Component
;; ============================================================================

(defn memory-game
  "Main memory game component"
  []
  (let [{:keys [sequence player-sequence playing? showing?
                score game-over? high-score]} @game-state]
    [:div {:style {:padding "20px"
                   :max-width "600px"
                   :margin "0 auto"
                   :font-family "system-ui, -apple-system, sans-serif"}}

     ;; Title
     [:h2 {:style {:text-align "center"
                   :color "#4caf50"
                   :margin-bottom "20px"}}
      "🧠 Memory Game"]

     [:div {:style {:padding "30px"
                    :background "white"
                    :border-radius "8px"
                    :box-shadow "0 2px 8px rgba(0, 0, 0, 0.1)"
                    :text-align "center"}}

      ;; Instructions
      [:p {:style {:margin-bottom "20px"
                   :color "#666"
                   :font-size "14px"}}
       "Watch the sequence and repeat it back!"]

      ;; Score display
      [score-display :playing? playing?
       :game-over? game-over?
       :score score
       :high-score high-score]

      ;; Status indicator
      [status-indicator :playing? playing?
       :showing? showing?
       :player-sequence player-sequence
       :sequence sequence]

      ;; Game tiles
      [:div {:style {:display "flex"
                     :gap "10px"
                     :justify-content "center"
                     :margin-bottom "30px"
                     :flex-wrap "wrap"}}
       (map-indexed
        (fn [idx color]
          ^{:key idx}
          [color-tile :color color
           :index idx
           :on-click handle-tile-click
           :disabled? (or showing? (not playing?))])
        game-colors)]

      ;; Control buttons
      [game-controls :playing? playing?
       :game-over? game-over?
       :on-start start-game
       :on-give-up (fn []
                     (swap! game-state assoc
                            :playing? false
                            :game-over? true))]

      ;; Game tips
      [game-tips]]]))

;; Export the main component
(def page memory-game)

;; ============================================================================
;; Mount point
;; ============================================================================

(defn ^:export mount-memory-game
  "Mount the memory game component to the DOM"
  []
  (when-let [el (js/document.getElementById "memory-game-root")]
    (rdom/render [page] el)))

;; Auto-mount when script loads
(mount-memory-game)
