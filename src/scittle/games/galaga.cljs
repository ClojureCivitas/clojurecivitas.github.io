(ns scittle.games.galaga
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

;; ============================================================================
;; Game Constants
;; ============================================================================

(def canvas-width 480)
(def canvas-height 640)
(def player-width 40)
(def player-height 40)
(def enemy-width 30)
(def enemy-height 30)
(def bullet-width 3)
(def bullet-height 10)
(def player-speed 5)
(def bullet-speed 8)
(def enemy-bullet-speed 4)

;; ============================================================================
;; Audio System
;; ============================================================================

(def audio-context (when (exists? js/AudioContext) (js/AudioContext.)))

(defn play-tone
  "Plays a tone at the specified frequency for the given duration"
  [& {:keys [frequency duration volume]
      :or {frequency 440 duration 0.2 volume 0.3}}]
  (when audio-context
    (try
      (let [oscillator (.createOscillator audio-context)
            gain-node (.createGain audio-context)]
        (.connect oscillator gain-node)
        (.connect gain-node (.-destination audio-context))
        (set! (.-value (.-frequency oscillator)) frequency)
        (set! (.-value (.-gain gain-node)) volume)
        (.start oscillator)
        (.stop oscillator (+ (.-currentTime audio-context) duration)))
      (catch js/Error e
        (js/console.error "Audio error:" e)))))

(defn play-laser-sound
  "Plays a laser shooting sound"
  []
  (play-tone :frequency 800 :duration 0.1 :volume 0.2))

(defn play-explosion-sound
  "Plays an explosion sound"
  []
  (play-tone :frequency 100 :duration 0.2 :volume 0.3))

(defn play-hit-sound
  "Plays a player hit sound"
  []
  (play-tone :frequency 150 :duration 0.3 :volume 0.4))

(defn play-swoop-sound
  "Plays an enemy swooping sound"
  []
  (play-tone :frequency 600 :duration 0.15 :volume 0.15))

(defn play-wave-complete-sound
  "Plays a victory sound for completing a wave"
  []
  ;; Play ascending notes
  (doseq [[idx freq] (map-indexed vector [523 659 784 1047])]
    (js/setTimeout
     #(play-tone :frequency freq :duration 0.2 :volume 0.25)
     (* idx 100))))

;; Enemy types and their properties
(def enemy-types
  {:bee {:color "#00FFFF" :points 50 :hits 1 :sprite "🦋"}
   :butterfly {:color "#FF00FF" :points 80 :hits 1 :sprite "🦋"}
   :boss {:color "#00FF00" :points 150 :hits 2 :sprite "👾"}})

;; Formation patterns for enemies
(def formation-positions
  ;; Boss row
  (vec
   (concat
    (for [x (range 4)] {:x (+ 140 (* x 60)) :y 100 :type :boss})
    ;; Butterfly rows
    (for [x (range 8)] {:x (+ 80 (* x 40)) :y 140 :type :butterfly})
    (for [x (range 8)] {:x (+ 80 (* x 40)) :y 180 :type :butterfly})
    ;; Bee rows
    (for [x (range 10)] {:x (+ 40 (* x 40)) :y 220 :type :bee})
    (for [x (range 10)] {:x (+ 40 (* x 40)) :y 260 :type :bee}))))

;; ============================================================================
;; Game State
;; ============================================================================

(def game-state
  (r/atom {:player {:x 240
                    :y 560
                    :width player-width
                    :height player-height
                    :lives 3}
           :bullets []
           :enemy-bullets []
           :enemies []
           :particles []
           :stars []
           :wave 1
           :score 0
           :high-score 0
           :game-status :ready ; :ready :playing :paused :game-over
           :formation-offset {:x 0 :y 0}
           :formation-direction 1
           :frame-count 0
           ;; Touch controls state
           :touch-controls {:left-pressed false
                            :right-pressed false
                            :fire-pressed false}}))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn wrap-position
  "Wraps position to stay within bounds"
  [& {:keys [value min-val max-val]}]
  (cond
    (< value min-val) max-val
    (> value max-val) min-val
    :else value))

;; ============================================================================
;; Star Field
;; ============================================================================

(defn init-stars []
  (vec (for [_ (range 50)]
         {:x (rand-int canvas-width)
          :y (rand-int canvas-height)
          :speed (+ 0.5 (rand 2))
          :size (+ 1 (rand-int 2))})))

;; ============================================================================
;; Particle System
;; ============================================================================

(defn create-particles
  "Creates explosion particles"
  [& {:keys [x y color count]}]
  (for [_ (range count)]
    {:x x
     :y y
     :vx (- (rand 6) 3)
     :vy (- (rand 6) 3)
     :life 30
     :color color}))

;; ============================================================================
;; Enemy Functions
;; ============================================================================

(defn create-enemy
  "Creates an enemy from formation position"
  [{:keys [x y type]}]
  (let [enemy-props (get enemy-types type)]
    {:x x
     :y y
     :type type
     :width enemy-width
     :height enemy-height
     :color (:color enemy-props)
     :points (:points enemy-props)
     :hits (:hits enemy-props)
     :formation-x x
     :formation-y y
     :state :formation
     :angle 0
     :swoop-path nil
     :path-progress 0}))

;; Generate swoop path
(defn generate-swoop-path
  [{:keys [start-x start-y]}]
  (let [side (if (< start-x (/ canvas-width 2)) 1 -1)]
    (vec (for [t (range 0 1.01 0.02)]
           (let [angle (* t Math/PI 2)
                 x (+ start-x (* side 150 (Math/sin angle)))
                 y (+ start-y (* 400 t))]
             {:x x :y y})))))

;; Initialize wave
(defn init-wave!
  "Initializes a new wave - returns new state"
  [game-state & {:keys [wave-num]}]
  (let [new-enemies (vec (map create-enemy formation-positions))]
    (assoc game-state
           :enemies new-enemies
           :bullets []
           :enemy-bullets []
           :formation-offset {:x 0 :y 0})))

;; ============================================================================
;; Player Control
;; ============================================================================

(defn move-player!
  "Moves player left or right - returns new state"
  [game-state & {:keys [direction]}]
  (update-in game-state [:player :x]
             #(-> %
                  (+ (* direction player-speed))
                  (max (/ player-width 2))
                  (min (- canvas-width (/ player-width 2))))))

(defn fire-bullet!
  "Fires a bullet from the player - returns new state"
  [game-state]
  (play-laser-sound) ; Side effect at the edge
  (let [{:keys [player]} game-state]
    (update game-state :bullets
            #(conj % {:x (:x player)
                      :y (:y player)
                      :width bullet-width
                      :height bullet-height}))))

;; Enemy fires bullet
(defn enemy-fire!
  "Enemy fires bullet at player - returns new state"
  [game-state & {:keys [enemy]}]
  (update game-state :enemy-bullets
          #(conj % {:x (:x enemy)
                    :y (+ (:y enemy) enemy-height)
                    :width bullet-width
                    :height bullet-height})))

;; ============================================================================
;; Collision Detection
;; ============================================================================

(defn collides?
  [& {:keys [a b]}]
  (and (< (Math/abs (- (:x a) (:x b))) (/ (+ (:width a) (:width b)) 2))
       (< (Math/abs (- (:y a) (:y b))) (/ (+ (:height a) (:height b)) 2))))

;; ============================================================================
;; Enemy AI
;; ============================================================================

(defn start-swoop!
  "Initiates enemy swoop attack - returns new state"
  [game-state]
  (let [{:keys [enemies]} game-state
        formation-enemies (filter #(= (:state %) :formation) enemies)]
    (if (and (> (count formation-enemies) 0)
             (< (rand) 0.02)) ; 2% chance per frame
      (let [enemy (rand-nth formation-enemies)
            path (generate-swoop-path {:start-x (:x enemy) :start-y (:y enemy)})]
        (play-swoop-sound) ; Side effect at the edge
        (update game-state :enemies
                (fn [enemies]
                  (mapv #(if (= % enemy)
                           (assoc % :state :swooping
                                  :swoop-path path
                                  :path-progress 0)
                           %)
                        enemies))))
      ;; No swoop - return unchanged state
      game-state)))

(defn update-swooping-enemy
  [enemy]
  (if (and (= (:state enemy) :swooping) (:swoop-path enemy))
    (let [progress (min (+ (:path-progress enemy) 0.02) 1)
          path-index (min (int (* progress (dec (count (:swoop-path enemy)))))
                          (dec (count (:swoop-path enemy))))
          pos (nth (:swoop-path enemy) path-index)]
      (if (>= progress 1)
        ;; Return to formation
        (assoc enemy :state :returning :path-progress 0)
        ;; Continue swooping
        (assoc enemy
               :x (:x pos)
               :y (:y pos)
               :path-progress progress)))
    enemy))

(defn update-returning-enemy
  [enemy]
  (if (= (:state enemy) :returning)
    (let [dx (- (:formation-x enemy) (:x enemy))
          dy (- (:formation-y enemy) (:y enemy))
          dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
      (if (< dist 5)
        ;; Reached formation position
        (assoc enemy :state :formation
               :x (:formation-x enemy)
               :y (:formation-y enemy))
        ;; Move towards formation
        (let [speed 3
              vx (* (/ dx dist) speed)
              vy (* (/ dy dist) speed)]
          (assoc enemy
                 :x (+ (:x enemy) vx)
                 :y (+ (:y enemy) vy)))))
    enemy))

;; ============================================================================
;; Game Update Logic
;; ============================================================================

(defn update-game!
  []
  (let [{:keys [game-status
                enemies
                bullets
                enemy-bullets
                player
                particles
                stars]} @game-state]
    (when (= game-status :playing)
      ;; Update frame count
      (swap! game-state update :frame-count inc)
      ;; Update stars
      (swap! game-state update :stars
             (fn [stars]
               (vec (map (fn [star]
                           (let [new-y (+ (:y star) (:speed star))]
                             (if (> new-y canvas-height)
                               (assoc star :y 0 :x (rand-int canvas-width))
                               (assoc star :y new-y))))
                         stars))))
;; Update formation movement (side-to-side only, no descent)
      (let [direction (:formation-direction @game-state)
            offset (:formation-offset @game-state)
            x (:x offset)
            y (:y offset)
            new-x (+ x (* direction 0.5))]
        ;; Single atomic update for formation movement and direction changes
        (swap! game-state
               (fn [state]
                 (cond
                   (and (>= new-x 30) (= direction 1))
                   (-> state
                       (assoc :formation-direction -1)
                       (assoc :formation-offset {:x 30 :y y}))
                   (and (<= new-x -30) (= direction -1))
                   (-> state
                       (assoc :formation-direction 1)
                       (assoc :formation-offset {:x -30 :y y}))
                   :else
                   (assoc state :formation-offset {:x new-x :y y})))))
      ;; Update enemies
      (swap! game-state update :enemies
             (fn [enemies]
               (vec (map (fn [enemy]
                           (-> enemy
                               update-swooping-enemy
                               update-returning-enemy
    ;; Update formation position
                               ((fn [e]
                                  (if (= (:state e) :formation)
                                    (assoc e
                                           :x (+ (:formation-x e) (get-in @game-state [:formation-offset :x]))
                                           :y (+ (:formation-y e) (get-in @game-state [:formation-offset :y])))
                                    e)))))
                         enemies))))

;; Start random swoops
      (swap! game-state start-swoop!)
;; Random enemy firing - collect all fires and apply in one swap!
      (let [firing-enemies (filter #(and (= (:state %) :swooping)
                                         (< (rand) 0.02))
                                   enemies)]
        (when (seq firing-enemies)
          (swap! game-state
                 (fn [state]
                   (reduce (fn [s enemy]
                             (enemy-fire! s :enemy enemy))
                           state
                           firing-enemies)))))
      ;; Update bullets
      (swap! game-state update :bullets
             #(vec (filter (fn [b] (> (:y b) 0))
                           (map (fn [b] (update b :y - bullet-speed)) %))))
      ;; Update enemy bullets
      (swap! game-state update :enemy-bullets
             #(vec (filter (fn [b] (< (:y b) canvas-height))
                           (map (fn [b] (update b :y + enemy-bullet-speed)) %))))
      ;; Update particles
      (swap! game-state update :particles
             #(vec (filter (fn [p] (> (:life p) 0))
                           (map (fn [p]
                                  (-> p
                                      (update :x + (:vx p))
                                      (update :y + (:vy p))
                                      (update :life dec)))
                                %))))
;; FIXED: Batch bullet-enemy collision detection
      ;; Get FRESH bullets and enemies after updates
      (let [current-bullets (:bullets @game-state)
            current-enemies (:enemies @game-state)
            hit-bullets (atom #{})
            hit-enemies (atom #{})
            damaged-enemies (atom {})
            score-added (atom 0)
            new-particles (atom [])]

        ;; Collect all collisions (but don't apply yet)
        (doseq [bullet current-bullets
                :when (not (contains? @hit-bullets bullet))
                enemy current-enemies
                :when (and (not (contains? @hit-enemies enemy))
                           (not= (:state enemy) :destroyed))]
          (when (collides? :a bullet :b enemy)
            ;; Mark bullet as hit
            (swap! hit-bullets conj bullet)

            (let [new-hits (dec (:hits enemy))
                  destroyed? (<= new-hits 0)]
              (if destroyed?
                ;; Enemy destroyed - mark for removal
                (do
                  (swap! hit-enemies conj enemy)
                  (swap! score-added + (:points enemy))
                  (swap! new-particles concat
                         (create-particles :x (:x enemy)
                                           :y (:y enemy)
                                           :count 10
                                           :color (:color enemy))))
                ;; Enemy damaged - mark for hit count update
                (swap! damaged-enemies assoc enemy new-hits)))))

        ;; Apply all collision effects at once (single swap!)
        (when (or (seq @hit-bullets) (seq @hit-enemies) (seq @damaged-enemies))
          (when (seq @hit-enemies)
            (play-explosion-sound))

          (swap! game-state
                 (fn [state]
                   (-> state
                       ;; Remove hit bullets
                       (update :bullets
                               (fn [bullets]
                                 (vec (remove #(contains? @hit-bullets %) bullets))))
                       ;; Remove destroyed enemies and update damaged ones
                       (update :enemies
                               (fn [enemies]
                                 (vec (keep (fn [e]
                                              (cond
                                                ;; Enemy destroyed - remove it
                                                (contains? @hit-enemies e)
                                                nil

                                                ;; Enemy damaged - update hits
                                                (contains? @damaged-enemies e)
                                                (assoc e :hits (get @damaged-enemies e))

                                                ;; Enemy not hit - keep as is
                                                :else e))
                                            enemies))))
                       ;; Add score for destroyed enemies
                       (update :score + @score-added)
                       ;; Add particles for destroyed enemies
                       (update :particles
                               (fn [particles]
                                 (vec (concat particles @new-particles)))))))))
;; Check enemy bullet-player collisions
      (doseq [bullet enemy-bullets]
        (when (collides? :a bullet :b player)
          (play-hit-sound) ; Play hit sound
          ;; Single atomic update for all hit effects
          (let [new-lives (dec (:lives player))
                game-over? (<= new-lives 0)]
            (swap! game-state
                   (fn [state]
                     (-> state
                         ;; Remove bullet
                         (update :enemy-bullets #(vec (remove (fn [b] (= b bullet)) %)))
                         ;; Decrement lives
                         (update-in [:player :lives] dec)
                         ;; Add particles
                         (update :particles
                                 #(vec (concat % (create-particles :x (:x player)
                                                                   :y (:y player)
                                                                   :count 15
                                                                   :color "#FFFF00"))))
                         ;; If game over, update status and high score
                         (#(if game-over?
                             (-> %
                                 (assoc :game-status :game-over)
                                 (update :high-score max (:score %)))
                             %))))))))
;; Check for wave clear
      (when (empty? enemies)
        (play-wave-complete-sound) ; Play victory sound
        (swap! game-state update :wave inc)
        (js/setTimeout
         #(swap! game-state init-wave! :wave-num (:wave @game-state))
         500))))) ; Delay slightly to let victory sound play

;; ============================================================================
;; Drawing Functions
;; ============================================================================

(defn draw-game!
  [& {:keys [ctx]}]
  (let [{:keys [player bullets enemy-bullets enemies particles stars score lives wave
                game-status]} @game-state]
    ;; Clear canvas
    (set! (.-fillStyle ctx) "#000033")
    (.fillRect ctx 0 0 canvas-width canvas-height)
    ;; Draw stars
    (set! (.-fillStyle ctx) "#FFFFFF")
    (doseq [star stars]
      (.fillRect ctx (:x star) (:y star) (:size star) (:size star)))
;; Draw enemies
    (doseq [enemy enemies]
      (let [props (get enemy-types (:type enemy))]
        ;; Draw emoji sprite
        (set! (.-fillStyle ctx) "#FFFFFF")
        (set! (.-font ctx) "30px Arial")
        (set! (.-textAlign ctx) "center")
        (set! (.-textBaseline ctx) "middle")
        (.fillText ctx (:sprite props) (:x enemy) (:y enemy))))
    ;; Draw player
    (set! (.-fillStyle ctx) "#FFFFFF")
    (set! (.-font ctx) "30px Arial")
    (set! (.-textAlign ctx) "center")
    (.fillText ctx "🚀" (:x player) (:y player))
    ;; Draw bullets
    (set! (.-fillStyle ctx) "#FFFF00")
    (doseq [bullet bullets]
      (.fillRect ctx (- (:x bullet) (/ bullet-width 2))
                 (- (:y bullet) (/ bullet-height 2))
                 bullet-width bullet-height))
    ;; Draw enemy bullets
    (set! (.-fillStyle ctx) "#FF00FF")
    (doseq [bullet enemy-bullets]
      (.fillRect ctx (- (:x bullet) (/ bullet-width 2))
                 (- (:y bullet) (/ bullet-height 2))
                 bullet-width bullet-height))
    ;; Draw particles
    (doseq [particle particles]
      (set! (.-fillStyle ctx) (:color particle))
      (set! (.-globalAlpha ctx) (/ (:life particle) 30))
      (.fillRect ctx (:x particle) (:y particle) 3 3))
    (set! (.-globalAlpha ctx) 1)
    ;; Draw UI
    (set! (.-fillStyle ctx) "#FFFFFF")
    (set! (.-font ctx) "18px Arial")
    (set! (.-textAlign ctx) "left")
    (.fillText ctx (str "Score: " score) 10 25)
    (.fillText ctx (str "Wave: " wave) 10 50)
    (.fillText ctx (str "Lives: " (apply str (repeat (:lives player) "❤️"))) 10 75)
    ;; Draw game over
    (when (= game-status :game-over)
      (set! (.-fillStyle ctx) "rgba(0,0,0,0.75)")
      (.fillRect ctx 0 0 canvas-width canvas-height)
      (set! (.-fillStyle ctx) "#FFFFFF")
      (set! (.-font ctx) "36px Arial")
      (set! (.-textAlign ctx) "center")
      (.fillText ctx "GAME OVER" (/ canvas-width 2) (/ canvas-height 2))
      (set! (.-font ctx) "24px Arial")
      (.fillText ctx (str "Final Score: " score) (/ canvas-width 2) (+ (/ canvas-height 2) 40)))
    ;; Draw pause
    (when (= game-status :paused)
      (set! (.-fillStyle ctx) "rgba(0,0,0,0.5)")
      (.fillRect ctx 0 0 canvas-width canvas-height)
      (set! (.-fillStyle ctx) "#FFFFFF")
      (set! (.-font ctx) "36px Arial")
      (set! (.-textAlign ctx) "center")
      (.fillText ctx "PAUSED" (/ canvas-width 2) (/ canvas-height 2)))))

;; ============================================================================
;; Game Control
;; ============================================================================

(defn start-game!
  []
  (swap! game-state assoc
         :player {:x 240
                  :y 560
                  :width player-width
                  :height player-height
                  :lives 3}
         :bullets []
         :enemy-bullets []
         :enemies []
         :particles []
         :stars (init-stars)
         :wave 1
         :score 0
         :game-status :playing
         :formation-offset {:x 0 :y 0}
         :formation-direction 1
         :frame-count 0
         :touch-controls {:left-pressed false
                          :right-pressed false
                          :fire-pressed false})
  (swap! game-state init-wave! :wave-num 1))

;; ============================================================================
;; Canvas Component
;; ============================================================================

(defn game-canvas []
  (let [canvas-ref (atom nil)
        animation-id (atom nil)
        keys-pressed (atom #{})]
    (r/create-class
     {:component-did-mount
      (fn []
        (when-let [canvas @canvas-ref]
          (let [ctx (.getContext canvas "2d")]
            ;; Keyboard controls
            (.addEventListener js/window "keydown"
                               (fn [e]
                                 (let [key (.-key e)]
                                   (swap! keys-pressed conj key)
                                   (when (= (:game-status @game-state) :playing)
                                     (case key
                                       " " (do (swap! game-state fire-bullet!) (.preventDefault e))
                                       nil)))))

            (.addEventListener js/window "keyup"
                               (fn [e]
                                 (swap! keys-pressed disj (.-key e))))
            ;; Game loop
            (letfn [(game-loop []
;; Handle continuous movement
                      (when (= (:game-status @game-state) :playing)
                        (let [touch-controls (:touch-controls @game-state)]
                          ;; Keyboard controls
                          (when (contains? @keys-pressed "ArrowLeft")
                            (swap! game-state move-player! :direction -1))
                          (when (contains? @keys-pressed "ArrowRight")
                            (swap! game-state move-player! :direction 1))
                          ;; Touch controls
                          (when (:left-pressed touch-controls)
                            (swap! game-state move-player! :direction -1))
                          (when (:right-pressed touch-controls)
                            (swap! game-state move-player! :direction 1))))
                      (update-game!)
                      (draw-game! :ctx ctx)
                      (reset! animation-id (js/requestAnimationFrame game-loop)))]
              (game-loop)))))
      :component-will-unmount
      (fn []
        (when @animation-id
          (js/cancelAnimationFrame @animation-id)))
      :render
      (fn []
        [:canvas {:ref #(reset! canvas-ref %)
                  :width canvas-width
                  :height canvas-height
                  :style {:border "2px solid #0066FF"
                          :background "#000033"}}])})))

;; ============================================================================
;; Touch Controls Components
;; ============================================================================

(defn touch-control-button
  "Touch button for mobile controls"
  [& {:keys [label position on-press on-release color]}]
  (let [button-ref (atom nil)
        touch-id (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn []
        (when-let [button @button-ref]
          (let [handle-touch-start (fn [e]
                                     (.preventDefault e)
                                     (let [touch (aget (.-touches e) 0)]
                                       (reset! touch-id (.-identifier touch))
                                       (when on-press (on-press))))
                handle-touch-end (fn [e]
                                   (.preventDefault e)
                                   (let [touches (.-changedTouches e)]
                                     (loop [i 0]
                                       (when (< i (.-length touches))
                                         (let [touch (aget touches i)]
                                           (when (= (.-identifier touch) @touch-id)
                                             (reset! touch-id nil)
                                             (when on-release (on-release))))
                                         (recur (inc i))))))]
            (.addEventListener button "touchstart" handle-touch-start)
            (.addEventListener button "touchend" handle-touch-end)
            (.addEventListener button "touchcancel" handle-touch-end))))
      :render
      (fn []
        (let [is-pressed (case label
                           "◀" (get-in @game-state [:touch-controls :left-pressed])
                           "▶" (get-in @game-state [:touch-controls :right-pressed])
                           "FIRE" (get-in @game-state [:touch-controls :fire-pressed])
                           false)]
          [:div {:ref #(reset! button-ref %)
                 :style (merge {:position "fixed"
                                :width "70px"
                                :height "70px"
                                :border-radius "50%"
                                :background (if is-pressed
                                              (str "rgba(" (or color "255, 255, 255") ", 0.9)")
                                              (str "rgba(" (or color "255, 255, 255") ", 0.4)"))
                                :border "3px solid rgba(255, 255, 255, 0.8)"
                                :touch-action "none"
                                :user-select "none"
                                :z-index 1000
                                :display "flex"
                                :align-items "center"
                                :justify-content "center"
                                :font-weight "bold"
                                :color (if is-pressed "rgba(0, 0, 0, 0.8)" "rgba(255, 255, 255, 0.9)")
                                :font-size (if (= label "FIRE") "14px" "28px")
                                :text-align "center"
                                :box-shadow "0 2px 8px rgba(0, 0, 0, 0.3)"
                                :transition "all 0.1s ease"}
                               position)}
           label]))})))

(defn touch-controls
  "Touch controls overlay for mobile"
  []
  (when (= (:game-status @game-state) :playing)
    [:div {:style {:position "fixed"
                   :top 0
                   :left 0
                   :width "100%"
                   :height "100%"
                   :pointer-events "none"
                   :z-index 999}}
     [:div {:style {:pointer-events "auto"}}
      ;; Left arrow
      [touch-control-button
       :label "◀"
       :position {:bottom "40px" :left "40px"}
       :color "255, 255, 255"
       :on-press #(swap! game-state assoc-in [:touch-controls :left-pressed] true)
       :on-release #(swap! game-state assoc-in [:touch-controls :left-pressed] false)]
      ;; Right arrow
      [touch-control-button
       :label "▶"
       :position {:bottom "40px" :left "130px"}
       :color "255, 255, 255"
       :on-press #(swap! game-state assoc-in [:touch-controls :right-pressed] true)
       :on-release #(swap! game-state assoc-in [:touch-controls :right-pressed] false)]
;; Fire button
      [touch-control-button
       :label "FIRE"
       :position {:bottom "40px" :right "40px"}
       :color "76, 175, 80"
       :on-press #(do
                    (swap! game-state assoc-in [:touch-controls :fire-pressed] true)
                    (swap! game-state fire-bullet!))
       :on-release #(swap! game-state assoc-in [:touch-controls :fire-pressed] false)]]]))

;; ============================================================================
;; UI Components
;; ============================================================================

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
   ;; Desktop controls
   [:div {:style {:margin-bottom "15px"}}
    [:h5 {:style {:margin "10px 0 5px 0"
                  :color "#333"
                  :font-size "14px"}}
     "🖥️ Desktop Controls:"]
    [:ul {:style {:margin "0"
                  :padding-left "20px"
                  :color "#666"
                  :font-size "14px"
                  :line-height "1.6"}}
     [:li "← → Arrow Keys - Move your spaceship left/right"]
     [:li "Spacebar - Fire lasers"]]]
   ;; Mobile controls
   [:div {:style {:margin-bottom "15px"}}
    [:h5 {:style {:margin "10px 0 5px 0"
                  :color "#333"
                  :font-size "14px"}}
     "📱 Mobile Controls:"]
    [:ul {:style {:margin "0"
                  :padding-left "20px"
                  :color "#666"
                  :font-size "14px"
                  :line-height "1.6"}}
     [:li "◀ ▶ Buttons (bottom-left) - Move ship"]
     [:li "FIRE Button (bottom-right) - Fire lasers"]]]
   ;; Game rules
   [:div
    [:h5 {:style {:margin "10px 0 5px 0"
                  :color "#333"
                  :font-size "14px"}}
     "🎮 Game Rules:"]
    [:ul {:style {:margin "0"
                  :padding-left "20px"
                  :color "#666"
                  :font-size "14px"
                  :line-height "1.6"}}
     [:li "Destroy all enemies to advance waves"]
     [:li "Avoid enemy fire - you have 3 lives"]
     [:li "Enemies swoop down to attack"]
     [:li "Higher waves = more enemies"]
     [:li "Score points for each enemy destroyed"]
     [:li "Boss enemies (👾) take 2 hits and give more points"]]]])

(defn game-controls
  "Game control buttons"
  []
  (let [{:keys [game-status score high-score]} @game-state]
    [:div {:style {:margin "20px 0"}}
     ;; Score display
     [:div {:style {:margin-bottom "20px"}}
      [:h3 {:style {:color (case game-status
                             :game-over "#ff4444"
                             :playing "#4caf50"
                             "#333")
                    :margin-bottom "10px"}}
       (case game-status
         :game-over (str "Game Over! Final Score: " score)
         :playing (str "Score: " score)
         "Press Start to Play")]
      ;; High score display
      (when (> high-score 0)
        [:div {:style {:padding "8px 16px"
                       :background "#f0f0f0"
                       :border-radius "20px"
                       :display "inline-block"
                       :color "#666"
                       :font-size "14px"}}
         (str "🏆 Best: " high-score)])]
     ;; Control buttons
     [:div {:style {:display "flex"
                    :gap "10px"
                    :justify-content "center"}}
      [:button {:style {:padding "10px 20px"
                        :background "#4caf50"
                        :color "white"
                        :font-size "16px"
                        :border "none"
                        :border-radius "4px"
                        :cursor "pointer"
                        :transition "all 0.2s ease"}
                :on-click start-game!
                :on-mouse-enter #(set! (.. % -target -style -background) "#45a049")
                :on-mouse-leave #(set! (.. % -target -style -background) "#4caf50")}
       (if (= game-status :game-over)
         "New Game"
         "Start Game")]
      (when (= game-status :playing)
        [:button {:style {:padding "10px 20px"
                          :background "#6c757d"
                          :color "white"
                          :font-size "16px"
                          :border "none"
                          :border-radius "4px"
                          :cursor "pointer"
                          :transition "all 0.2s ease"}
                  :on-click #(swap! game-state assoc :game-status :paused)
                  :on-mouse-enter #(set! (.. % -target -style -background) "#5a6268")
                  :on-mouse-leave #(set! (.. % -target -style -background) "#6c757d")}
         "Pause"])
      (when (= game-status :paused)
        [:button {:style {:padding "10px 20px"
                          :background "#4caf50"
                          :color "white"
                          :font-size "16px"
                          :border "none"
                          :border-radius "4px"
                          :cursor "pointer"
                          :transition "all 0.2s ease"}
                  :on-click #(swap! game-state assoc :game-status :playing)
                  :on-mouse-enter #(set! (.. % -target -style -background) "#45a049")
                  :on-mouse-leave #(set! (.. % -target -style -background) "#4caf50")}
         "Resume"])]]))

;; ============================================================================
;; Main Component
;; ============================================================================

(defn galaga-game
  "Main galaga game component"
  []
  [:div {:style {:padding "20px"
                 :max-width "600px"
                 :margin "0 auto"
                 :font-family "system-ui, -apple-system, sans-serif"}}
   ;; Title
   [:h2 {:style {:text-align "center"
                 :color "#4caf50"
                 :margin-bottom "20px"}}
    "👾 Galaga"]
   [:div {:style {:padding "30px"
                  :background "white"
                  :border-radius "8px"
                  :box-shadow "0 2px 8px rgba(0, 0, 0, 0.1)"
                  :text-align "center"}}
    ;; Instructions
    [:p {:style {:margin-bottom "20px"
                 :color "#666"
                 :font-size "14px"}}
     "Defend Earth from the alien invasion! Works on desktop and mobile!"]
    ;; Game controls
    [game-controls]
    ;; Game canvas
    [:div {:style {:display "flex"
                   :justify-content "center"
                   :margin "20px 0"}}
     [game-canvas]]
    ;; Touch controls overlay
    [touch-controls]
    ;; Game tips
    [game-tips]]])

;; Export the main component
(def page galaga-game)

;; ============================================================================
;; Mount point
;; ============================================================================

(defn ^:export mount-galaga-game
  "Mount the galaga game component to the DOM"
  []
  (when-let [el (js/document.getElementById "galaga-game-root")]
    (rdom/render [page] el)))

;; Auto-mount when script loads
(mount-galaga-game)
