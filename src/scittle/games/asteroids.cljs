(ns scittle.games.asteroids
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
;; Game Constants
;; ============================================================================

(def canvas-width 800)
(def canvas-height 600)
(def ship-size 10)
(def rotation-speed 0.1)
(def thrust-power 0.5)
(def max-velocity 7)
(def friction 0.99)
(def bullet-speed 10)
(def bullet-lifetime 40)
(def asteroid-speeds {:large 1 :medium 2 :small 3})
(def asteroid-sizes {:large 40 :medium 25 :small 15})
(def asteroid-points {:large 20 :medium 50 :small 100})
(def ufo-size 20)
(def ufo-speed 2)
(def hyperspace-cooldown 120)

;; ============================================================================
;; Game State
;; ============================================================================

(def game-state
  (r/atom {:ship {:x (/ canvas-width 2)
                  :y (/ canvas-height 2)
                  :vx 0
                  :vy 0
                  :angle 0
                  :thrusting false
                  :invulnerable 0}
           :bullets []
           :asteroids []
           :ufos []
           :particles []
           :score 0
           :high-score 0
           :lives 3
           :level 1
           :game-status :ready
           :hyperspace-cooldown 0
           :frame-count 0
           :ufo-timer 0}))

;; ============================================================================
;; Vector Math Helpers
;; ============================================================================

(defn wrap-position
  "Wraps position to stay within bounds"
  [& {:keys [value max-val]}]
  (cond
    (< value 0) (+ max-val value)
    (> value max-val) (- value max-val)
    :else value))

(defn distance
  "Calculates distance between two points"
  [& {:keys [x1 y1 x2 y2]}]
  (Math/sqrt (+ (* (- x2 x1) (- x2 x1))
                (* (- y2 y1) (- y2 y1)))))

(defn random-angle []
  (* (rand) Math/PI 2))

(defn random-velocity
  "Creates random velocity vector"
  [& {:keys [speed]}]
  (let [angle (random-angle)]
    {:vx (* speed (Math/cos angle))
     :vy (* speed (Math/sin angle))}))

;; ============================================================================
;; Asteroid Functions
;; ============================================================================

(defn create-asteroid-shape
  "Creates irregular polygon vertices for asteroid"
  [& {:keys [size]}]
  (let [num-vertices (+ 8 (rand-int 5))
        angle-step (/ (* 2 Math/PI) num-vertices)]
    (vec (for [i (range num-vertices)]
           (let [angle (* i angle-step)
                 radius (+ (* size 0.8) (* (rand) size 0.4))]
             {:x (* radius (Math/cos angle))
              :y (* radius (Math/sin angle))})))))

(defn create-asteroid
  "Creates a new asteroid"
  [& {:keys [x y size-type]}]
  (let [size (get asteroid-sizes size-type)
        velocity (random-velocity :speed (get asteroid-speeds size-type))]
    {:x x
     :y y
     :vx (:vx velocity)
     :vy (:vy velocity)
     :size size
     :size-type size-type
     :angle 0
     :rotation-speed (* (- (rand) 0.5) 0.05)
     :shape (create-asteroid-shape :size size)}))

(defn split-asteroid
  "Splits asteroid into smaller pieces"
  [& {:keys [asteroid]}]
  (let [{:keys [x y size-type]} asteroid]
    (case size-type
      :large (for [_ (range 2)]
               (create-asteroid :x (+ x (- (rand-int 20) 10))
                                :y (+ y (- (rand-int 20) 10))
                                :size-type :medium))
      :medium (for [_ (range 2)]
                (create-asteroid :x (+ x (- (rand-int 10) 5))
                                 :y (+ y (- (rand-int 10) 5))
                                 :size-type :small))
      :small [])))

;; ============================================================================
;; Particle Functions
;; ============================================================================

(defn create-particles
  "Creates explosion particles"
  [& {:keys [x y count color]}]
  (for [_ (range count)]
    {:x x
     :y y
     :vx (* (- (rand) 0.5) 5)
     :vy (* (- (rand) 0.5) 5)
     :life 30
     :color color}))

;; ============================================================================
;; UFO Functions
;; ============================================================================

(defn create-ufo
  "Creates a new UFO"
  []
  (let [side (if (< (rand) 0.5) 0 canvas-width)
        y (+ 50 (rand-int (- canvas-height 100)))]
    {:x side
     :y y
     :vx (if (= side 0) ufo-speed (- ufo-speed))
     :vy 0
     :size ufo-size
     :shoot-timer 0}))

;; ============================================================================
;; Level Management
;; ============================================================================

(defn init-level!
  "Initializes a new level"
  [& {:keys [level]}]
  (let [num-asteroids (+ 3 level)]
    (swap! game-state assoc
           :asteroids (vec (for [_ (range num-asteroids)]
                             (let [edge (rand-int 4)
                                   x (case edge
                                       0 (rand-int canvas-width)
                                       1 canvas-width
                                       2 (rand-int canvas-width)
                                       (rand-int canvas-height))
                                   y (case edge
                                       0 0
                                       1 (rand-int canvas-height)
                                       2 canvas-height
                                       0)]
                               (create-asteroid :x x :y y :size-type :large))))
           :bullets []
           :particles []
           :ufo-timer (+ 600 (rand-int 600))
           :ship (assoc (:ship @game-state)
                        :invulnerable 120))))

(defn reset-ship!
  "Resets ship to center"
  []
  (swap! game-state assoc-in [:ship :x] (/ canvas-width 2))
  (swap! game-state assoc-in [:ship :y] (/ canvas-height 2))
  (swap! game-state assoc-in [:ship :vx] 0)
  (swap! game-state assoc-in [:ship :vy] 0)
  (swap! game-state assoc-in [:ship :angle] 0)
  (swap! game-state assoc-in [:ship :invulnerable] 120))

;; ============================================================================
;; Weapon Functions
;; ============================================================================

(defn fire-bullet!
  "Fires a bullet from the ship"
  []
  (let [{:keys [x y angle]} (:ship @game-state)
        bullet-vx (* bullet-speed (Math/cos (- angle (/ Math/PI 2))))
        bullet-vy (* bullet-speed (Math/sin (- angle (/ Math/PI 2))))]
    (swap! game-state update :bullets conj
           {:x x
            :y y
            :vx bullet-vx
            :vy bullet-vy
            :life bullet-lifetime})))

(defn ufo-fire!
  "UFO fires bullet at ship"
  [& {:keys [ufo]}]
  (let [{:keys [ship]} @game-state
        dx (- (:x ship) (:x ufo))
        dy (- (:y ship) (:y ufo))
        angle (Math/atan2 dy dx)
        bullet-vx (* 5 (Math/cos angle))
        bullet-vy (* 5 (Math/sin angle))]
    (swap! game-state update :bullets conj
           {:x (:x ufo)
            :y (:y ufo)
            :vx bullet-vx
            :vy bullet-vy
            :life bullet-lifetime
            :from-ufo true})))

;; ============================================================================
;; Special Moves
;; ============================================================================

(defn hyperspace!
  "Hyperspace jump with risk"
  []
  (when (<= (:hyperspace-cooldown @game-state) 0)
    (swap! game-state assoc-in [:ship :x] (rand-int canvas-width))
    (swap! game-state assoc-in [:ship :y] (rand-int canvas-height))
    (swap! game-state assoc-in [:ship :vx] 0)
    (swap! game-state assoc-in [:ship :vy] 0)
    (swap! game-state assoc :hyperspace-cooldown hyperspace-cooldown)
    ;; 10% chance of explosion (risky!)
    (when (< (rand) 0.1)
      (swap! game-state update-in [:lives] dec)
      (swap! game-state update :particles
             #(vec (concat % (create-particles :x (:x (:ship @game-state))
                                               :y (:y (:ship @game-state))
                                               :count 20
                                               :color "#FFFFFF")))))))

;; ============================================================================
;; Collision Detection
;; ============================================================================

(defn check-collision
  "Checks if two objects collide"
  [& {:keys [obj1 obj2 radius1 radius2]}]
  (< (distance :x1 (:x obj1) :y1 (:y obj1)
               :x2 (:x obj2) :y2 (:y obj2))
     (+ radius1 radius2)))

;; ============================================================================
;; Game Update Logic
;; ============================================================================

(defn update-game!
  "Main game update loop"
  []
  (when (= (:game-status @game-state) :playing)
    (let [{:keys [ship bullets asteroids ufos particles]} @game-state]

      ;; Update frame count
      (swap! game-state update :frame-count inc)

      ;; Update hyperspace cooldown
      (swap! game-state update :hyperspace-cooldown #(max 0 (dec %)))

      ;; Update UFO timer
      (swap! game-state update :ufo-timer dec)
      (when (<= (:ufo-timer @game-state) 0)
        (swap! game-state update :ufos conj (create-ufo))
        (swap! game-state assoc :ufo-timer (+ 900 (rand-int 900))))

      ;; Update ship
      (swap! game-state update :ship
             (fn [s]
               (let [new-vx (if (:thrusting s)
                              (min max-velocity
                                   (max (- max-velocity)
                                        (+ (:vx s) (* thrust-power (Math/cos (- (:angle s) (/ Math/PI 2)))))))
                              (* (:vx s) friction))
                     new-vy (if (:thrusting s)
                              (min max-velocity
                                   (max (- max-velocity)
                                        (+ (:vy s) (* thrust-power (Math/sin (- (:angle s) (/ Math/PI 2)))))))
                              (* (:vy s) friction))]
                 (-> s
                     (assoc :vx new-vx :vy new-vy)
                     (update :x #(wrap-position :value (+ % new-vx) :max-val canvas-width))
                     (update :y #(wrap-position :value (+ % new-vy) :max-val canvas-height))
                     (update :invulnerable #(max 0 (dec %)))))))

      ;; Update bullets
      (swap! game-state update :bullets
             (fn [bs]
               (vec (for [b bs
                          :let [new-b (-> b
                                          (update :x #(wrap-position :value (+ % (:vx b)) :max-val canvas-width))
                                          (update :y #(wrap-position :value (+ % (:vy b)) :max-val canvas-height))
                                          (update :life dec))]
                          :when (> (:life new-b) 0)]
                      new-b))))

      ;; Update asteroids
      (swap! game-state update :asteroids
             (fn [as]
               (vec (for [a as]
                      (-> a
                          (update :x #(wrap-position :value (+ % (:vx a)) :max-val canvas-width))
                          (update :y #(wrap-position :value (+ % (:vy a)) :max-val canvas-height))
                          (update :angle #(+ % (:rotation-speed a))))))))

      ;; Update UFOs
      (swap! game-state update :ufos
             (fn [us]
               (vec (for [u us
                          :when (and (> (:x u) -50) (< (:x u) (+ canvas-width 50)))]
                      (do
                        (when (and (= (mod (:frame-count @game-state) 60) 0)
                                   (< (rand) 0.3))
                          (ufo-fire! :ufo u))
                        (-> u
                            (update :x + (:vx u))
                            (update :y + (:vy u))))))))

      ;; Update particles
      (swap! game-state update :particles
             (fn [ps]
               (vec (for [p ps
                          :let [new-p (-> p
                                          (update :x + (:vx p))
                                          (update :y + (:vy p))
                                          (update :life dec))]
                          :when (> (:life new-p) 0)]
                      new-p))))

      ;; Check bullet-asteroid collisions
      (doseq [bullet bullets
              :when (not (:from-ufo bullet))
              asteroid asteroids]
        (when (check-collision :obj1 bullet :obj2 asteroid
                               :radius1 2 :radius2 (:size asteroid))
          (swap! game-state update :bullets #(vec (remove (fn [b] (= b bullet)) %)))
          (swap! game-state update :asteroids #(vec (remove (fn [a] (= a asteroid)) %)))
          (swap! game-state update :asteroids
                 #(vec (concat % (split-asteroid :asteroid asteroid))))
          (swap! game-state update :score + (get asteroid-points (:size-type asteroid)))
          (swap! game-state update :particles
                 #(vec (concat % (create-particles :x (:x asteroid)
                                                   :y (:y asteroid)
                                                   :count 10
                                                   :color "#FFFFFF"))))))

      ;; Check bullet-UFO collisions
      (doseq [bullet bullets
              :when (not (:from-ufo bullet))
              ufo ufos]
        (when (check-collision :obj1 bullet :obj2 ufo
                               :radius1 2 :radius2 ufo-size)
          (swap! game-state update :bullets #(vec (remove (fn [b] (= b bullet)) %)))
          (swap! game-state update :ufos #(vec (remove (fn [u] (= u ufo)) %)))
          (swap! game-state update :score + 200)
          (swap! game-state update :particles
                 #(vec (concat % (create-particles :x (:x ufo)
                                                   :y (:y ufo)
                                                   :count 15
                                                   :color "#FF00FF"))))))

      ;; Check ship-asteroid collisions
      (when (= (:invulnerable ship) 0)
        (doseq [asteroid asteroids]
          (when (check-collision :obj1 ship :obj2 asteroid
                                 :radius1 ship-size :radius2 (:size asteroid))
            (swap! game-state update :lives dec)
            (swap! game-state update :particles
                   #(vec (concat % (create-particles :x (:x ship)
                                                     :y (:y ship)
                                                     :count 20
                                                     :color "#FFFFFF"))))
            (reset-ship!)
            (when (<= (:lives @game-state) 0)
              (swap! game-state assoc :game-status :game-over)
              (swap! game-state update :high-score max (:score @game-state))))))

      ;; Check ship-bullet collisions (from UFO)
      (when (= (:invulnerable ship) 0)
        (doseq [bullet bullets
                :when (:from-ufo bullet)]
          (when (check-collision :obj1 ship :obj2 bullet
                                 :radius1 ship-size :radius2 3)
            (swap! game-state update :bullets #(vec (remove (fn [b] (= b bullet)) %)))
            (swap! game-state update :lives dec)
            (swap! game-state update :particles
                   #(vec (concat % (create-particles :x (:x ship)
                                                     :y (:y ship)
                                                     :count 20
                                                     :color "#FFFFFF"))))
            (reset-ship!)
            (when (<= (:lives @game-state) 0)
              (swap! game-state assoc :game-status :game-over)
              (swap! game-state update :high-score max (:score @game-state))))))

      ;; Check level complete
      (when (empty? asteroids)
        (swap! game-state update :level inc)
        (init-level! :level (:level @game-state))))))

;; ============================================================================
;; Drawing Functions
;; ============================================================================

(defn draw-ship
  "Draws the player's ship"
  [& {:keys [ctx x y angle thrusting invulnerable]}]
  (when (or (= invulnerable 0) (= (mod invulnerable 4) 0))
    (.save ctx)
    (.translate ctx x y)
    (.rotate ctx angle)
    (set! (.-strokeStyle ctx) "#FFFFFF")
    (set! (.-lineWidth ctx) 2)
    (.beginPath ctx)
    (.moveTo ctx 0 (- ship-size))
    (.lineTo ctx (- ship-size) ship-size)
    (.lineTo ctx 0 (/ ship-size 2))
    (.lineTo ctx ship-size ship-size)
    (.closePath ctx)
    (.stroke ctx)

    ;; Draw thrust
    (when thrusting
      (set! (.-strokeStyle ctx) "#FF6600")
      (.beginPath ctx)
      (.moveTo ctx (- (/ ship-size 2)) (/ ship-size 2))
      (.lineTo ctx 0 (+ ship-size 5))
      (.lineTo ctx (/ ship-size 2) (/ ship-size 2))
      (.stroke ctx))
    (.restore ctx)))

(defn draw-asteroid
  "Draws an asteroid"
  [& {:keys [ctx x y shape angle]}]
  (.save ctx)
  (.translate ctx x y)
  (.rotate ctx angle)
  (set! (.-strokeStyle ctx) "#FFFFFF")
  (set! (.-lineWidth ctx) 2)
  (.beginPath ctx)
  (let [first-vertex (first shape)]
    (.moveTo ctx (:x first-vertex) (:y first-vertex)))
  (doseq [vertex (rest shape)]
    (.lineTo ctx (:x vertex) (:y vertex)))
  (.closePath ctx)
  (.stroke ctx)
  (.restore ctx))

(defn draw-ufo
  "Draws a UFO"
  [& {:keys [ctx x y size]}]
  (set! (.-strokeStyle ctx) "#FF00FF")
  (set! (.-lineWidth ctx) 2)
  (.beginPath ctx)
  (.ellipse ctx x y size (/ size 2) 0 0 (* Math/PI 2))
  (.stroke ctx)
  (.beginPath ctx)
  (.ellipse ctx x (- y (/ size 3)) (/ size 2) (/ size 3) 0 0 Math/PI)
  (.stroke ctx))

(defn draw-game!
  "Main drawing function"
  [& {:keys [ctx]}]
  (let [{:keys [ship bullets asteroids ufos particles score lives level game-status]} @game-state]

    ;; Clear canvas
    (set! (.-fillStyle ctx) "#000000")
    (.fillRect ctx 0 0 canvas-width canvas-height)

    ;; Draw ship
    (draw-ship :ctx ctx
               :x (:x ship)
               :y (:y ship)
               :angle (:angle ship)
               :thrusting (:thrusting ship)
               :invulnerable (:invulnerable ship))

    ;; Draw asteroids
    (doseq [asteroid asteroids]
      (draw-asteroid :ctx ctx
                     :x (:x asteroid)
                     :y (:y asteroid)
                     :shape (:shape asteroid)
                     :angle (:angle asteroid)))

    ;; Draw UFOs
    (doseq [ufo ufos]
      (draw-ufo :ctx ctx
                :x (:x ufo)
                :y (:y ufo)
                :size (:size ufo)))

    ;; Draw bullets
    (set! (.-fillStyle ctx) "#FFFFFF")
    (doseq [bullet bullets]
      (if (:from-ufo bullet)
        (set! (.-fillStyle ctx) "#FF00FF")
        (set! (.-fillStyle ctx) "#FFFFFF"))
      (.fillRect ctx (- (:x bullet) 2) (- (:y bullet) 2) 4 4))

    ;; Draw particles
    (doseq [particle particles]
      (set! (.-fillStyle ctx) (:color particle))
      (set! (.-globalAlpha ctx) (/ (:life particle) 30))
      (.fillRect ctx (:x particle) (:y particle) 2 2))
    (set! (.-globalAlpha ctx) 1)

    ;; Draw UI
    (set! (.-fillStyle ctx) "#FFFFFF")
    (set! (.-font ctx) "20px monospace")
    (set! (.-textAlign ctx) "left")
    (.fillText ctx (str "Score: " score) 10 30)
    (.fillText ctx (str "Level: " level) 10 55)
    (.fillText ctx (str "Lives: " lives) 10 80)

    ;; Draw game over
    (when (= game-status :game-over)
      (set! (.-fillStyle ctx) "rgba(0,0,0,0.75)")
      (.fillRect ctx 0 0 canvas-width canvas-height)
      (set! (.-fillStyle ctx) "#FFFFFF")
      (set! (.-font ctx) "48px monospace")
      (set! (.-textAlign ctx) "center")
      (.fillText ctx "GAME OVER" (/ canvas-width 2) (/ canvas-height 2))
      (set! (.-font ctx) "24px monospace")
      (.fillText ctx (str "Final Score: " score) (/ canvas-width 2) (+ (/ canvas-height 2) 50)))

    ;; Draw pause
    (when (= game-status :paused)
      (set! (.-fillStyle ctx) "rgba(0,0,0,0.5)")
      (.fillRect ctx 0 0 canvas-width canvas-height)
      (set! (.-fillStyle ctx) "#FFFFFF")
      (set! (.-font ctx) "48px monospace")
      (set! (.-textAlign ctx) "center")
      (.fillText ctx "PAUSED" (/ canvas-width 2) (/ canvas-height 2)))))

;; ============================================================================
;; Game Control
;; ============================================================================

(defn start-game!
  "Starts a new game"
  []
  (swap! game-state assoc
         :ship {:x (/ canvas-width 2)
                :y (/ canvas-height 2)
                :vx 0
                :vy 0
                :angle 0
                :thrusting false
                :invulnerable 120}
         :bullets []
         :asteroids []
         :ufos []
         :particles []
         :score 0
         :lives 3
         :level 1
         :game-status :playing
         :hyperspace-cooldown 0
         :frame-count 0
         :ufo-timer 600)
  (init-level! :level 1))

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
                                       " " (do (fire-bullet!) (.preventDefault e))
                                       "x" (hyperspace!)
                                       "X" (hyperspace!)
                                       nil)))))

            (.addEventListener js/window "keyup"
                               (fn [e]
                                 (let [key (.-key e)]
                                   (swap! keys-pressed disj key)
                                   (when (= key "ArrowUp")
                                     (swap! game-state assoc-in [:ship :thrusting] false)))))

            ;; Game loop
            (letfn [(game-loop []
                      (when (= (:game-status @game-state) :playing)
                        ;; Handle rotation
                        (when (contains? @keys-pressed "ArrowLeft")
                          (swap! game-state update-in [:ship :angle] - rotation-speed))
                        (when (contains? @keys-pressed "ArrowRight")
                          (swap! game-state update-in [:ship :angle] + rotation-speed))
                        ;; Handle thrust
                        (if (contains? @keys-pressed "ArrowUp")
                          (swap! game-state assoc-in [:ship :thrusting] true)
                          (swap! game-state assoc-in [:ship :thrusting] false)))

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
                  :style {:border "2px solid #FFFFFF"
                          :background "#000000"}}])})))

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
   [:ul {:style {:margin "0"
                 :padding-left "20px"
                 :color "#666"
                 :font-size "14px"
                 :line-height "1.6"}}
    [:li "← → Arrow Keys - Rotate ship"]
    [:li "↑ Arrow Key - Thrust forward"]
    [:li "Spacebar - Fire bullets"]
    [:li "X Key - Hyperspace jump (risky - 10% chance of death!)"]
    [:li "Destroy all asteroids to advance levels"]
    [:li "Large asteroids split into medium, medium into small"]
    [:li "Watch out for UFOs - they shoot back!"]
    [:li "Screen wraps around at edges"]
    [:li "Physics-based movement with momentum"]]])

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

(defn asteroids-game
  "Main asteroids game component"
  []
  [:div {:style {:padding "20px"
                 :max-width "900px"
                 :margin "0 auto"
                 :font-family "system-ui, -apple-system, sans-serif"}}

   ;; Title
   [:h2 {:style {:text-align "center"
                 :color "#4caf50"
                 :margin-bottom "20px"}}
    "🚀 Asteroids"]

   [:div {:style {:padding "30px"
                  :background "white"
                  :border-radius "8px"
                  :box-shadow "0 2px 8px rgba(0, 0, 0, 0.1)"
                  :text-align "center"}}

    ;; Instructions
    [:p {:style {:margin-bottom "20px"
                 :color "#666"
                 :font-size "14px"}}
     "Defend your ship from asteroids and UFOs!"]

    ;; Game controls
    [game-controls]

    ;; Game canvas
    [:div {:style {:display "flex"
                   :justify-content "center"
                   :margin "20px 0"}}
     [game-canvas]]

    ;; Game tips
    [game-tips]]])

;; Export the main component
(def page asteroids-game)

;; ============================================================================
;; Mount point
;; ============================================================================

(defn ^:export mount-asteroids-game
  "Mount the asteroids game component to the DOM"
  []
  (when-let [el (js/document.getElementById "asteroids-game-root")]
    (rdom/render [page] el)))

;; Auto-mount when script loads
(mount-asteroids-game)
