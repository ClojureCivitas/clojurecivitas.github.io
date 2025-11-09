^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Build Asteroids with ClojureScript & Scittle"
         :quarto {:author [:burinc]
                  :description "Create a classic Asteroids arcade game with physics simulation, collision detection, canvas graphics, and retro sound effects - now with mobile touch controls! All running in the browser with zero build tools!"
                  :type :post
                  :date "2025-11-09"
                  :category :games
                  :image "asteroids-01.png"
                  :tags [:scittle
                         :clojurescript
                         :reagent
                         :games
                         :asteroids
                         :canvas
                         :physics
                         :arcade
                         :retro
                         :no-build
                         :mobile
                         :touch-controls
                         :web-audio
                         :sound-effects]
                  :keywords [:asteroids-game
                             :canvas-graphics
                             :physics-simulation
                             :collision-detection
                             :vector-graphics
                             :game-loop
                             :keyboard-controls
                             :touch-controls
                             :mobile-gaming
                             :virtual-joystick
                             :performance-optimization
                             :retro-gaming
                             :arcade-classics
                             :web-audio-api
                             :sound-synthesis
                             :retro-sound-effects]}}}

(ns scittle.games.asteroids-article
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # Build Asteroids with ClojureScript & Scittle

;; ## About This Project

;; Remember the golden age of arcade games? Today, I'll show you how to recreate one of the most iconic games of all time - Asteroids - using ClojureScript and Scittle, running entirely in your browser without any build tools!

;; This is part of my ongoing exploration of browser-native development with Scittle. Check out my previous articles in this series:

;; - [Building Browser-Native Presentations with Scittle](https://clojurecivitas.github.io/scittle/presentations/browser_native_slides.html) - Create interactive presentation systems
;; - [Python + ClojureScript: Pyodide Integration](https://clojurecivitas.github.io/scittle/pyodide/pyodide_integration.html) - Run Python in the browser
;; - [Free Weather Data with NWS API](https://clojurecivitas.github.io/scittle/weather/weather_nws_integration.html) - Build weather applications
;; - [Browser-Native QR Code Scanner](https://clojurecivitas.github.io/scittle/qrcode/qr_code_scanner.html) - QR scanning without servers
;; - [Build a Memory Game with Scittle](https://clojurecivitas.github.io/scittle/games/memory_game_article.html) - Simon-style memory challenge

;; This project demonstrates advanced game development concepts including physics simulation, collision detection, and canvas graphics - all achievable without complex tooling!

;; ## What We're Building

;; We'll create a complete Asteroids game featuring:

;; - **Physics-based spaceship** with momentum and inertia
;; - **Destructible asteroids** that split into smaller pieces
;; - **Hyperspace jump** with a risky twist (10% chance of destruction!)
;; - **UFO enemies** that hunt and shoot at you
;; - **Particle effects** for explosions and destruction
;; - **Retro sound effects** using Web Audio API for authentic arcade audio
;; - **Progressive difficulty** with increasing asteroid counts
;; - **High score tracking** to compete against yourself
;; - **Canvas-based vector graphics** for that authentic retro feel
;; - **Mobile touch controls** with virtual joystick and action buttons
;; - **Performance optimizations** to handle intense gameplay

;; All of this with keyword arguments for clean, readable code!

;; ## The Journey: From Desktop to Mobile

;; ### A Kid's Request

;; After building the initial desktop version, my kids wanted to play Asteroids on their phones. The problem? No physical keyboard! This led me to add comprehensive mobile support with touch controls.

;; The challenge was creating an intuitive control scheme that matched the precision of keyboard controls while working naturally on a touchscreen. The solution: a virtual joystick for ship control and dedicated buttons for firing and hyperspace jumps.

;; ### The Performance Crisis

;; During testing, my kids discovered a critical bug: rapid firing would cause asteroids to split exponentially, creating hundreds of objects that made the game completely unplayable. The browser would freeze, and the game became a slideshow of particle effects and asteroid outlines.

;; This real-world feedback led to important performance improvements that made the game smooth and enjoyable on both desktop and mobile devices.

;; ## Why Scittle for Arcade Games?

;; ### Zero Build Configuration

;; Traditional ClojureScript game development requires:

;; - Setting up shadow-cljs or figwheel
;; - Configuring webpack for assets
;; - Managing dependencies and builds
;; - Dealing with hot reload complications

;; With Scittle, you write pure ClojureScript that runs immediately. Perfect for:
;; - Prototyping game mechanics
;; - Learning game development concepts
;; - Building retro-style browser games
;; - Teaching programming through games

;; ### Instant Feedback Loop

;; Changes appear the moment you save. No watching compile output, no build errors - just code, save, and play!

;; ### Educational Value

;; This approach teaches:

;; - Game state management with atoms
;; - Physics simulation (velocity, acceleration, friction)
;; - Collision detection algorithms
;; - Canvas API and vector graphics
;; - Game loop architecture
;; - Functional game development patterns

;; ## Game Architecture

;; The game follows a clean, functional architecture:

^:kindly/hide-code
(kind/mermaid
 "graph TD
    A[Game State Atom] --> B[Game Loop]
    B --> C[Update Physics]
    B --> D[Check Collisions]
    B --> E[Handle Input]
    C --> F[Update Ship]
    C --> G[Update Asteroids]
    C --> H[Update Bullets]
    C --> I[Update UFOs]
    D --> J[Bullet-Asteroid]
    D --> K[Ship-Asteroid]
    D --> L[Bullet-UFO]
    E --> M[Keyboard Events]
    M --> N[Rotate/Thrust/Fire]
    F --> O[Canvas Rendering]
    G --> O
    H --> O
    I --> O
    O --> B")

;; ## Core Game Systems

;; ### 1. State Management with Reagent Atoms

;; All game state lives in a single atom for predictable updates:

;; ```clojure
;; (def game-state
;;   (r/atom {:ship {:x 400 :y 300 :vx 0 :vy 0 :angle 0
;;                   :thrusting false :invulnerable 0}
;;            :bullets []
;;            :asteroids []
;;            :ufos []
;;            :particles []
;;            :score 0
;;            :high-score 0
;;            :lives 3
;;            :level 1
;;            :game-status :ready
;;            :hyperspace-cooldown 0
;;            :frame-count 0
;;            :ufo-timer 0}))
;; ```

;; ### 2. Physics Simulation

;; The game implements realistic physics with keyword arguments:

;; ```clojure
;; ;; Wrap-around screen boundaries
;; (defn wrap-position
;;   [& {:keys [value max-val]}]
;;   (cond
;;     (< value 0) (+ max-val value)
;;     (> value max-val) (- value max-val)
;;     :else value))
;;
;; ;; Apply thrust with maximum velocity limits
;; (let [new-vx (if thrusting
;;                (min max-velocity
;;                     (max (- max-velocity)
;;                          (+ vx (* thrust-power (Math/cos angle)))))
;;                (* vx friction))]
;;   ...)
;; ```

;; ### 3. Vector Graphics with Canvas

;; Ships and asteroids are drawn using vector paths:

;; ```clojure
;; (defn draw-ship
;;   [& {:keys [ctx x y angle thrusting invulnerable]}]
;;   (.save ctx)
;;   (.translate ctx x y)
;;   (.rotate ctx angle)
;;   (set! (.-strokeStyle ctx) "#FFFFFF")
;;   (.beginPath ctx)
;;   (.moveTo ctx 0 (- ship-size))
;;   (.lineTo ctx (- ship-size) ship-size)
;;   (.lineTo ctx 0 (/ ship-size 2))
;;   (.lineTo ctx ship-size ship-size)
;;   (.closePath ctx)
;;   (.stroke ctx)
;;   (.restore ctx))
;; ```

;; ## Key Game Mechanics

^:kindly/hide-code
(kind/mermaid
 "sequenceDiagram
    participant P as Player
    participant G as Game Loop
    participant C as Collision System
    participant R as Renderer

    P->>G: Start Game
    G->>G: Init Asteroids
    loop Every Frame
        P->>G: Keyboard Input
        G->>G: Update Ship Physics
        G->>G: Update Bullets
        G->>G: Update Asteroids
        G->>C: Check Collisions
        alt Bullet Hits Asteroid
            C->>G: Split Asteroid
            C->>G: Add Score
            C->>G: Create Particles
        else Ship Hits Asteroid
            C->>G: Lose Life
            C->>G: Reset Ship
        end
        G->>R: Draw Everything
        R-->>P: Display Frame
    end")

;; ### Asteroid Creation and Splitting

;; Asteroids are procedurally generated with irregular shapes:

;; ```clojure
;; (defn create-asteroid-shape
;;   [& {:keys [size]}]
;;   (let [num-vertices (+ 8 (rand-int 5))
;;         angle-step (/ (* 2 Math/PI) num-vertices)]
;;     (vec (for [i (range num-vertices)]
;;            (let [angle (* i angle-step)
;;                  radius (+ (* size 0.8) (* (rand) size 0.4))]
;;              {:x (* radius (Math/cos angle))
;;               :y (* radius (Math/sin angle))})))))
;;
;; (defn split-asteroid
;;   [& {:keys [asteroid]}]
;;   (let [{:keys [x y size-type]} asteroid]
;;     (case size-type
;;       :large (for [_ (range 2)]
;;                (create-asteroid :x (+ x (- (rand-int 20) 10))
;;                                 :y (+ y (- (rand-int 20) 10))
;;                                 :size-type :medium))
;;       :medium (for [_ (range 2)]
;;                 (create-asteroid :x (+ x (- (rand-int 10) 5))
;;                                  :y (+ y (- (rand-int 10) 5))
;;                                  :size-type :small))
;;       :small [])))
;; ```

;; ### Collision Detection

;; Simple but effective circle-based collision detection:

;; ```clojure
;; (defn distance
;;   [& {:keys [x1 y1 x2 y2]}]
;;   (Math/sqrt (+ (* (- x2 x1) (- x2 x1))
;;                 (* (- y2 y1) (- y2 y1)))))
;;
;; (defn check-collision
;;   [& {:keys [obj1 obj2 radius1 radius2]}]
;;   (< (distance :x1 (:x obj1) :y1 (:y obj1)
;;                :x2 (:x obj2) :y2 (:y obj2))
;;      (+ radius1 radius2)))
;; ```

;; ### Hyperspace Jump - The Risky Escape

;; One of the most iconic features! Teleport to safety... or die trying.

;; #### Code Review Improvement

;; After sharing this code on Slack, Erik provided excellent feedback:

;; > "Very cool! Looking at the code, in the `hyperspace!` you have multiple swap!s on your gamestate atom. Probs doesn't make a difference here, but it does defeat the purpose of an atom, because you're now susceptible to races (at least in a multi-threaded env), and if you have listeners to the atom state, they'll get more updates than what they bargained for."

;; **The Problem:** Multiple `swap!` calls meant:
;; - Multiple state updates (5-7 separate modifications)
;; - Race condition risk in multi-threaded environments
;; - Multiple Reagent re-renders instead of one
;; - Listeners notified multiple times per hyperspace jump

;; **The Solution:** Combine all updates into a single `swap!`:

;; ```clojure
;; (defn hyperspace!
;;   "Hyperspace jump with risk"
;;   []
;;   (when (<= (:hyperspace-cooldown @game-state) 0)
;;     (play-hyperspace-sound) ; Sound effect added!
;;     (let [new-x (rand-int canvas-width)
;;           new-y (rand-int canvas-height)
;;           died? (< (rand) 0.1)]
;;       (swap! game-state
;;              (fn [state]
;;                (-> state
;;                    ;; Teleport ship
;;                    (assoc-in [:ship :x] new-x)
;;                    (assoc-in [:ship :y] new-y)
;;                    (assoc-in [:ship :vx] 0)
;;                    (assoc-in [:ship :vy] 0)
;;                    (assoc :hyperspace-cooldown hyperspace-cooldown)
;;                    ;; Conditionally handle death (10% chance!)
;;                    (#(if died?
;;                        (-> %
;;                            (update-in [:lives] dec)
;;                            (update :particles
;;                                    (fn [particles]
;;                                      (vec (concat particles
;;                                                   (create-particles
;;                                                    :x new-x
;;                                                    :y new-y
;;                                                    :count 12
;;                                                    :color \"#FFFFFF\"))))))
;;                        %))))))))
;; ```

;; **Benefits of the improved version:**
;; - Single atomic state update
;; - No race conditions
;; - One Reagent re-render per hyperspace jump
;; - Cleaner threading with `->` macro
;; - More functional approach with conditional logic

;; ### UFO Enemies

;; UFOs spawn periodically and actively hunt the player:

;; ```clojure
;; (defn create-ufo
;;   []
;;   (let [side (if (< (rand) 0.5) 0 canvas-width)
;;         y (+ 50 (rand-int (- canvas-height 100)))]
;;     {:x side
;;      :y y
;;      :vx (if (= side 0) ufo-speed (- ufo-speed))
;;      :vy 0
;;      :size ufo-size
;;      :shoot-timer 0}))
;;
;; (defn ufo-fire!
;;   [& {:keys [ufo]}]
;;   (let [{:keys [ship]} @game-state
;;         ;; Calculate angle to ship
;;         dx (- (:x ship) (:x ufo))
;;         dy (- (:y ship) (:y ufo))
;;         angle (Math/atan2 dy dx)
;;         ;; Fire bullet toward ship
;;         bullet-vx (* 5 (Math/cos angle))
;;         bullet-vy (* 5 (Math/sin angle))]
;;     (swap! game-state update :bullets conj
;;            {:x (:x ufo) :y (:y ufo)
;;             :vx bullet-vx :vy bullet-vy
;;             :life bullet-lifetime
;;             :from-ufo true})))
;; ```

;; ## Game Loop Architecture

^:kindly/hide-code
(kind/mermaid
 "graph LR
    A[requestAnimationFrame] --> B{Game Playing?}
    B -->|Yes| C[Handle Input]
    C --> D[Update Physics]
    D --> E[Check Collisions]
    E --> F[Update Particles]
    F --> G[Spawn UFOs]
    G --> H[Render Canvas]
    H --> I[Next Frame]
    I --> A
    B -->|No| J[Pause/Wait]")

;; The game loop runs at 60 FPS using `requestAnimationFrame`:

;; ```clojure
;; (letfn [(game-loop []
;;           ;; Handle continuous input (rotation, thrust)
;;           (when (= (:game-status @game-state) :playing)
;;             (when (contains? @keys-pressed \"ArrowLeft\")
;;               (swap! game-state update-in [:ship :angle] - rotation-speed))
;;             (when (contains? @keys-pressed \"ArrowRight\")
;;               (swap! game-state update-in [:ship :angle] + rotation-speed))
;;             (if (contains? @keys-pressed \"ArrowUp\")
;;               (swap! game-state assoc-in [:ship :thrusting] true)
;;               (swap! game-state assoc-in [:ship :thrusting] false)))
;;
;;           ;; Update all game entities
;;           (update-game!)
;;
;;           ;; Render to canvas
;;           (draw-game! :ctx ctx)
;;
;;           ;; Schedule next frame
;;           (reset! animation-id (js/requestAnimationFrame game-loop)))]
;;   (game-loop))
;; ```

;; ## Particle Effects System

;; Explosions create satisfying particle effects:

;; ```clojure
;; (defn create-particles
;;   [& {:keys [x y count color]}]
;;   (for [_ (range count)]
;;     {:x x
;;      :y y
;;      :vx (* (- (rand) 0.5) 5)  ; Random velocity
;;      :vy (* (- (rand) 0.5) 5)
;;      :life 30                   ; Frames to live
;;      :color color}))
;;
;; ;; Particles fade and decay over time
;; (doseq [particle particles]
;;   (set! (.-fillStyle ctx) (:color particle))
;;   (set! (.-globalAlpha ctx) (/ (:life particle) 30))
;;   (.fillRect ctx (:x particle) (:y particle) 2 2))
;; ```

;; ## Keyword Arguments Pattern

;; All functions use keyword arguments for clarity and maintainability:

;; ```clojure
;; ;; ❌ Hard to read
;; (draw-asteroid ctx 100 200 shape 1.5)
;;
;; ;; ✅ Self-documenting
;; (draw-asteroid :ctx ctx :x 100 :y 200 :shape shape :angle 1.5)
;;
;; ;; ❌ Position matters
;; (create-asteroid 300 400 :large)
;;
;; ;; ✅ Clear intent
;; (create-asteroid :x 300 :y 400 :size-type :large)
;; ```

;; ## Canvas Rendering Techniques

;; Efficient rendering with canvas transformations:

;; ```clojure
;; (defn draw-asteroid
;;   [& {:keys [ctx x y shape angle]}]
;;   (.save ctx)                    ; Save canvas state
;;   (.translate ctx x y)           ; Move to asteroid position
;;   (.rotate ctx angle)            ; Rotate to current angle
;;   (set! (.-strokeStyle ctx) \"#FFFFFF\")
;;   (.beginPath ctx)
;;   (let [first-vertex (first shape)]
;;     (.moveTo ctx (:x first-vertex) (:y first-vertex)))
;;   (doseq [vertex (rest shape)]
;;     (.lineTo ctx (:x vertex) (:y vertex)))
;;   (.closePath ctx)
;;   (.stroke ctx)
;;   (.restore ctx))               ; Restore canvas state
;; ```

;; ## Progressive Difficulty System

;; Each level increases the challenge:

;; ```clojure
;; (defn init-level!
;;   [& {:keys [level]}]
;;   (let [num-asteroids (+ 3 level)]  ; More asteroids each level!
;;     (swap! game-state assoc
;;            :asteroids (vec (for [_ (range num-asteroids)]
;;                              (create-asteroid-at-edge)))
;;            :bullets []
;;            :particles []
;;            :ufo-timer (+ 600 (rand-int 600))
;;            :ship (assoc (:ship @game-state)
;;                         :invulnerable 120))))  ; Invulnerability frames
;;
;; ;; Scoring system rewards skill
;; (def asteroid-points {:large 20 :medium 50 :small 100})
;; (def ufo-points 200)
;; ```

;; ## Learning Points

;; This project teaches several advanced game development concepts:

;; ### 1. Physics Simulation

;; - **Newton's Laws**: Objects in motion stay in motion
;; - **Friction**: Gradual velocity decay (multiply by 0.99)
;; - **Thrust**: Apply force in the direction ship faces
;; - **Max Velocity**: Prevent unrealistic speeds
;; - **Wrap-around**: Screen edge teleportation

;; ### 2. Collision Detection

;; - **Circle-Circle**: Distance-based detection
;; - **Optimization**: Only check active objects
;; - **Batch Processing**: Collect all collisions before applying
;; - **Response**: Separate detection from response logic
;; - **Invulnerability**: Temporary immunity after hits

;; ### 3. Game Feel

;; - **Particle Effects**: Visual feedback for actions
;; - **Sound Effects**: Audio feedback for every action
;; - **Web Audio API**: Procedural sound synthesis
;; - **Retro Tones**: Frequency-based arcade sounds
;; - **Difficulty Curve**: Progressive challenge

;; ### 4. State Management

;; - **Single Source of Truth**: One atom for all state
;; - **Immutable Updates**: Pure functional transformations
;; - **Atomic Updates**: Single `swap!` per operation (Erik's feedback)
;; - **Predictable Updates**: No side effects in update logic
;; - **Reactive Rendering**: Automatic UI updates

;; ## Mobile Touch Controls Implementation

;; ### The Challenge

;; Making Asteroids playable on mobile required solving several problems:

;; 1. **No keyboard**: Touch events completely different from key presses
;; 2. **Continuous input**: Ship rotation and thrust need smooth, continuous control
;; 3. **Multiple actions**: Fire bullets while steering and thrusting
;; 4. **Visual feedback**: Players need to see what they're touching

;; ### The Solution: Virtual Joystick

;; We implemented a virtual joystick that appears in the bottom-left corner when the game starts:

;; ```clojure
;; ;; Touch state management
;; (def game-state
;;   (r/atom {;; ... existing state
;;            :touch-controls {:joystick {:active false
;;                                        :x 0
;;                                        :y 0
;;                                        :angle 0
;;                                        :distance 0}
;;                             :fire-button false
;;                             :hyperspace-button false}}))
;;
;; ;; Helper function to find touch by ID (avoiding array-seq issues)
;; (defn find-touch-by-id
;;   [touch-list touch-id]
;;   (when touch-list
;;     (loop [i 0]
;;       (when (< i (.-length touch-list))
;;         (let [touch (aget touch-list i)]
;;           (if (= (.-identifier touch) touch-id)
;;             touch
;;             (recur (inc i))))))))
;; ```

;; ### Joystick Physics Calculations

;; The joystick calculates angle and distance from center:

;; ```clojure
;; (defn calculate-joystick-angle
;;   [& {:keys [center-x center-y touch-x touch-y]}]
;;   (let [dx (- touch-x center-x)
;;         dy (- touch-y center-y)]
;;     (Math/atan2 dy dx)))
;;
;; (defn calculate-joystick-distance
;;   [& {:keys [center-x center-y touch-x touch-y max-distance]}]
;;   (let [dx (- touch-x center-x)
;;         dy (- touch-y center-y)
;;         dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
;;     (min dist max-distance)))
;;
;; (defn normalize-joystick-input
;;   [& {:keys [distance max-distance]}]
;;   (/ distance max-distance))
;; ```

;; ### Touch Event Handling

;; The virtual joystick component handles `touchstart`, `touchmove`, and `touchend`:

;; ```clojure
;; (defn virtual-joystick []
;;   (let [joystick-ref (atom nil)
;;         touch-id (atom nil)
;;         max-distance 50]
;;     (r/create-class
;;      {:component-did-mount
;;       (fn []
;;         (when-let [joystick @joystick-ref]
;;           ;; touchstart: Record the touch and activate joystick
;;           (let [handle-touch-start
;;                 (fn [e]
;;                   (.preventDefault e)
;;                   (let [touch (aget (.-touches e) 0)]
;;                     (reset! touch-id (.-identifier touch))
;;                     (swap! game-state assoc-in
;;                            [:touch-controls :joystick :active] true)))
;;
;;                 ;; touchmove: Calculate angle and distance
;;                 handle-touch-move
;;                 (fn [e]
;;                   (.preventDefault e)
;;                   (when @touch-id
;;                     (let [touches (.-touches e)
;;                           touch (find-touch-by-id touches @touch-id)]
;;                       (when touch
;;                         (let [center (get-joystick-center)
;;                               angle (calculate-joystick-angle ...)
;;                               distance (calculate-joystick-distance ...)]
;;                           (swap! game-state assoc-in
;;                                  [:touch-controls :joystick]
;;                                  {:active true :angle angle
;;                                   :distance distance ...}))))))]
;;             (.addEventListener joystick \"touchstart\" handle-touch-start)
;;             (.addEventListener joystick \"touchmove\" handle-touch-move))))
;;
;;       :render
;;       (fn []
;;         [:div {:style {:position \"fixed\" :bottom \"40px\" :left \"40px\"
;;                        :width \"120px\" :height \"120px\"
;;                        :border-radius \"50%\"
;;                        :background \"rgba(255, 255, 255, 0.2)\"}}
;;          ;; Inner draggable circle shows touch position
;;          [:div {:style {:width \"60px\" :height \"60px\"
;;                        :transform (str \"translate(\" x \"px, \" y \"px)\")}}]])})))
;; ```

;; ### Action Buttons

;; Separate buttons for firing and hyperspace:

;; ```clojure
;; (defn touch-action-button
;;   [& {:keys [label bottom right on-press on-release color]}]
;;   ;; Creates circular button with touch event handlers
;;   ;; FIRE button: Green, fires bullets
;;   ;; HYPER button: Orange, activates hyperspace
;;   ...)
;; ```

;; ### Game Loop Integration

;; The game loop checks both keyboard and touch input:

;; ```clojure
;; (letfn [(game-loop []
;;           (when (= (:game-status @game-state) :playing)
;;             (let [joystick (get-in @game-state [:touch-controls :joystick])]
;;
;;               ;; Keyboard rotation
;;               (when (contains? @keys-pressed \"ArrowLeft\")
;;                 (swap! game-state update-in [:ship :angle] - rotation-speed))
;;
;;               ;; Touch joystick rotation and thrust
;;               (when (:active joystick)
;;                 (let [normalized-distance (normalize-joystick-input
;;                                            :distance (:distance joystick)
;;                                            :max-distance 50)
;;                       joy-angle (:angle joystick)]
;;                   ;; Rotate ship to match joystick angle
;;                   (swap! game-state assoc-in [:ship :angle]
;;                          (+ joy-angle (/ Math/PI 2)))
;;                   ;; Thrust when joystick pushed > 30%
;;                   (swap! game-state assoc-in [:ship :thrusting]
;;                          (> normalized-distance 0.3))))))
;;           ...)]
;;   (game-loop))
;; ```

;; ### Mobile-Specific Challenges

;; 1. **Touch ID Tracking**: Each touch has a unique identifier to handle multi-touch
;; 2. **Preventing Default**: Stop browser scrolling and zooming during gameplay
;; 3. **Visual Feedback**: Button and joystick colors change when active
;; 4. **Z-Index Management**: Controls overlay the game canvas
;; 5. **Scittle Compatibility**: Avoiding `array-seq` which isn't available in Scittle

;; ## Critical Performance Fixes

;; ### The Exponential Asteroid Explosion Bug

;; The original collision detection had a fatal flaw:

;; ```clojure
;; ;; ❌ BROKEN: Multiple bullets can hit same asteroid in one frame
;; (doseq [bullet bullets
;;         asteroid asteroids]
;;   (when (check-collision bullet asteroid)
;;     (swap! game-state update :bullets remove-bullet)
;;     (swap! game-state update :asteroids remove-asteroid)
;;     (swap! game-state update :asteroids concat (split-asteroid asteroid))))
;; ```

;; **The Problem:** If 3 bullets hit a large asteroid in the same frame:
;; - Asteroid splits into 2 medium (first hit)
;; - Those 2 medium split into 4 small (second hit)
;; - Those 4 small would split further (third hit)
;; - Result: Exponential growth from 1 asteroid to potentially 8+ asteroids
;; - Within seconds: hundreds of asteroids and thousands of particles
;; - Game freezes, becomes unplayable

;; ### The Fix: Collision Batching

;; We collect all collisions first, then apply them once:

;; ```clojure
;; ;; ✅ FIXED: Batch collision detection and resolution
;; (let [hit-bullets (atom #{})
;;       hit-asteroids (atom #{})
;;       new-asteroids (atom [])
;;       score-added (atom 0)
;;       new-particles (atom [])]
;;
;;   ;; Find all collisions (but don't apply yet)
;;   (doseq [bullet bullets
;;           :when (and (not (:from-ufo bullet))
;;                      (not (contains? @hit-bullets bullet)))
;;           asteroid asteroids
;;           :when (not (contains? @hit-asteroids asteroid))]
;;     (when (check-collision bullet asteroid)
;;       ;; Mark as hit (prevents duplicate processing)
;;       (swap! hit-bullets conj bullet)
;;       (swap! hit-asteroids conj asteroid)
;;       ;; Collect effects
;;       (swap! new-asteroids concat (split-asteroid asteroid))
;;       (swap! score-added + points)
;;       (swap! new-particles concat (create-particles ...))))
;;
;;   ;; Apply all effects at once
;;   (when (seq @hit-bullets)
;;     (swap! game-state update :bullets remove-hit-bullets)
;;     (swap! game-state update :asteroids remove-hit-asteroids)
;;     (swap! game-state update :asteroids concat-new-asteroids)))
;; ```

;; ### Performance Safeguards

;; We added safety limits to prevent performance degradation:

;; ```clojure
;; ;; Safety limits
;; (def max-asteroids 50)    ;; Cap total asteroid count
;; (def max-particles 200)   ;; Cap total particle count
;;
;; ;; Reduce particle counts
;; (defn create-particles [& {:keys [count ...]}]
;;   ;; Asteroid hit: 10 → 5 particles
;;   ;; UFO explosion: 15 → 8 particles
;;   ;; Ship explosion: 20 → 12 particles
;;   ...)
;;
;; ;; Apply limits when adding new objects
;; (swap! game-state update :asteroids
;;        #(vec (take max-asteroids (concat current new-ones))))
;;
;; (swap! game-state update :particles
;;        #(vec (take max-particles (concat current new-ones))))
;; ```

;; ### Impact of Performance Fixes

;; **Before fixes:**
;; - Rapid firing → 100+ asteroids in seconds
;; - 1000+ particles causing visual chaos
;; - Frame rate drops from 60 FPS to < 10 FPS
;; - Game becomes unplayable, browser may freeze
;; - Mobile devices especially affected

;; **After fixes:**
;; - Maximum 50 asteroids (smooth on all devices)
;; - Maximum 200 particles (clean visual effects)
;; - Consistent 60 FPS on desktop and mobile
;; - No collision bugs or exponential growth
;; - Enjoyable gameplay even during intense action

;; ### Lessons Learned

;; 1. **Test with real users**: Kids found the bug immediately through enthusiastic rapid-fire testing
;; 2. **Batch state updates**: Collecting changes before applying prevents race conditions
;; 3. **Use sets for tracking**: Prevents duplicate processing of the same object
;; 4. **Add safety limits**: Upper bounds prevent worst-case scenarios
;; 5. **Optimize particle counts**: Fewer particles with better placement looks just as good
;; 6. **Profile on mobile**: Desktop performance doesn't predict mobile behavior

;; ## Retro Sound Effects with Web Audio API

;; No arcade game is complete without sound! We added authentic retro sound effects using the Web Audio API, following the same pattern used in our Galaga implementation.

;; ### The Audio System Architecture

;; The sound system uses procedurally generated tones to create that classic arcade feel:

;; ```clojure
;; ;; Initialize Web Audio API context
;; (def audio-context
;;   "Web Audio API context for sound generation"
;;   (when (exists? js/AudioContext)
;;     (js/AudioContext.)))
;;
;; ;; Generic tone generator
;; (defn play-tone
;;   "Plays a tone at the specified frequency for the given duration"
;;   [& {:keys [frequency duration volume]
;;       :or {frequency 440 duration 0.2 volume 0.3}}]
;;   (when audio-context
;;     (try
;;       (let [oscillator (.createOscillator audio-context)
;;             gain-node (.createGain audio-context)]
;;         (.connect oscillator gain-node)
;;         (.connect gain-node (.-destination audio-context))
;;         (set! (.-value (.-frequency oscillator)) frequency)
;;         (set! (.-value (.-gain gain-node)) volume)
;;         (.start oscillator)
;;         (.stop oscillator (+ (.-currentTime audio-context) duration)))
;;       (catch js/Error e
;;         (js/console.error "Audio error:" e)))))
;; ```

;; ### Sound Effect Implementations

;; Each game event has its own characteristic sound:

;; ```clojure
;; ;; 1. Laser Sound - High frequency burst
;; (defn play-laser-sound []
;;   (play-tone :frequency 800 :duration 0.1 :volume 0.2))
;;
;; ;; 2. Explosion Sound - Low rumble
;; (defn play-explosion-sound []
;;   (play-tone :frequency 100 :duration 0.2 :volume 0.3))
;;
;; ;; 3. Thrust Sound - Continuous engine hum
;; (defn play-thrust-sound []
;;   (play-tone :frequency 150 :duration 0.08 :volume 0.15))
;;
;; ;; 4. Hyperspace - Descending frequency sweep
;; (defn play-hyperspace-sound []
;;   (doseq [[idx freq] (map-indexed vector [880 660 440 220])]
;;     (js/setTimeout
;;      #(play-tone :frequency freq :duration 0.1 :volume 0.25)
;;      (* idx 50))))
;;
;; ;; 5. Hit Sound - Dramatic impact
;; (defn play-hit-sound []
;;   (play-tone :frequency 150 :duration 0.3 :volume 0.4))
;;
;; ;; 6. Victory Sound - Ascending fanfare
;; (defn play-level-complete-sound []
;;   (doseq [[idx freq] (map-indexed vector [523 659 784 1047])]
;;     (js/setTimeout
;;      #(play-tone :frequency freq :duration 0.2 :volume 0.25)
;;      (* idx 100))))
;; ```

;; ### Integrating Sounds with Game Events

;; Sounds are triggered at key moments for maximum impact:

;; ```clojure
;; ;; Fire weapon
;; (defn fire-bullet! []
;;   (play-laser-sound)  ; Instant audio feedback
;;   (let [{:keys [x y angle]} (:ship @game-state)]
;;     (swap! game-state update :bullets conj ...)))
;;
;; ;; Asteroid destruction
;; (when (seq @hit-bullets)
;;   (play-explosion-sound)
;;   (swap! game-state update :bullets ...))
;;
;; ;; Ship collision
;; (when (check-collision ship asteroid)
;;   (play-hit-sound)
;;   (swap! game-state update :lives dec))
;;
;; ;; Level complete
;; (when (empty? asteroids)
;;   (play-level-complete-sound)
;;   (swap! game-state update :level inc))
;; ```

;; ### Throttling Continuous Sounds

;; The thrust sound needs special handling to avoid audio spam:

;; ```clojure
;; ;; Play thrust sound (throttled to every 8 frames)
;; (when (and (:thrusting ship)
;;            (= (mod (:frame-count @game-state) 8) 0))
;;   (play-thrust-sound))
;; ```

;; This creates a continuous engine sound effect while preventing hundreds of simultaneous audio oscillators!

;; ### Sound Design Philosophy

;; The sound effects follow classic arcade game principles:

;; 1. **Laser (800Hz)**: High frequency for energy weapons
;; 2. **Explosion (100Hz)**: Low rumble for impacts
;; 3. **Thrust (150Hz)**: Mid-low hum for engines
;; 4. **Hyperspace (descending)**: Frequency sweep for warp effect
;; 5. **Hit (150Hz)**: Sustained tone for damage feedback
;; 6. **Victory (ascending)**: Rising pitch for achievement

;; ### Browser Compatibility

;; The audio system gracefully handles browsers without Web Audio API support:

;; ```clojure
;; (when audio-context  ; Only play if API available
;;   (try
;;     ;; Generate sound
;;     (catch js/Error e
;;       (js/console.error "Audio error:" e))))
;; ```

;; ### Learning from Galaga

;; We followed the same sound implementation pattern from our Galaga game:
;; - Single `audio-context` initialization
;; - Generic `play-tone` function for all sounds
;; - Specific sound functions with descriptive names
;; - Integration at game event trigger points
;; - Error handling for unsupported browsers

;; This consistent approach makes it easy to add sound to any Scittle-based game!

;; ## Try the Game!

;; The complete Asteroids game is embedded below. Works on both desktop and mobile!

;; **Desktop Controls:**
;; - Arrow keys to rotate and thrust
;; - Spacebar to fire
;; - X for hyperspace

;; **Mobile Controls:**
;; - Virtual joystick (bottom-left) to rotate and thrust
;; - FIRE button (green, right side) to shoot
;; - HYPER button (orange, right side) for hyperspace

^:kindly/hide-code
(kind/hiccup
 [:div {:style {:margin "2rem 0"
                :padding "2rem"
                :border "2px solid #e9ecef"
                :border-radius "8px"
                :background-color "#f8f9fa"}}
  [:div#asteroids-game-root {:style {:min-height "800px"}}]
  [:script {:type "application/x-scittle"
            :src "asteroids.cljs"}]])

;; ## Extending the Game

;; Here are ideas to enhance your Asteroids clone:

;; ### 1. Power-Ups

;; - Shield power-up (temporary invulnerability)
;; - Rapid fire (increased fire rate)
;; - Smart bombs (destroy all on-screen asteroids)
;; - Extra lives at milestone scores

;; ### 2. Visual Enhancements

;; - Starfield background with parallax
;; - Glowing particle trails
;; - Screen shake on collisions
;; - Explosion animations
;; - Retro CRT shader effects

;; ### 3. Enhanced Audio

;; The game now has retro sound effects! Here are ideas to enhance the audio further:

;; - **Background music loop**: Add ambient space music
;; - **UFO siren**: Classic arcade UFO warning sound
;; - **Power-up sounds**: When collecting extra lives or shields
;; - **Menu sounds**: UI interaction feedback
;; - **Dynamic volume**: Lower music during intense action
;; - **Stereo panning**: Position sounds left/right based on screen location
;; - **Sound variations**: Randomize pitch slightly for variety
;; - **Mute toggle**: Let players disable sound

;; Example of stereo panning:

;; ```clojure
;; (defn play-positioned-sound [x]
;;   (when audio-context
;;     (let [oscillator (.createOscillator audio-context)
;;           panner (.createStereoPanner audio-context)
;;           gain-node (.createGain audio-context)
;;           ;; Pan from -1 (left) to 1 (right) based on x position
;;           pan-value (- (* 2 (/ x canvas-width)) 1)]
;;       (set! (.-value (.-pan panner)) pan-value)
;;       (.connect oscillator panner)
;;       (.connect panner gain-node)
;;       (.connect gain-node (.-destination audio-context))
;;       ...)))
;; ```

;; ### 4. Game Modes

;; - **Survival Mode**: Endless asteroids, no levels
;; - **Time Trial**: Clear asteroids before time runs out
;; - **No UFO Mode**: Just asteroids and physics
;; - **Pacifist**: Dodge asteroids without firing
;; - **Bullet Hell**: UFOs spawn more frequently

;; ### 5. Modern Features

;; - Local storage for persistent high scores
;; - Leaderboard integration
;; - Replay system
;; - Achievement badges
;; - Daily challenges

;; ### 6. Physics Experiments

;; - Gravity wells (black holes)
;; - Variable friction zones
;; - Bouncing asteroids
;; - Chain reaction explosions
;; - Magnetic fields affecting bullets

;; ## Performance Optimizations

;; For smooth 60 FPS gameplay:

;; ```clojure
;; ;; Spatial partitioning for collision detection
;; (defn nearby-objects [x y objects radius]
;;   (filter #(< (distance :x1 x :y1 y :x2 (:x %) :y2 (:y %)) radius)
;;           objects))
;;
;; ;; Object pooling for bullets/particles
;; (def bullet-pool (atom []))
;;
;; (defn get-bullet []
;;   (if-let [bullet (first @bullet-pool)]
;;     (do (swap! bullet-pool rest) bullet)
;;     {:x 0 :y 0 :vx 0 :vy 0 :life 0}))
;;
;; ;; Batch canvas operations
;; (defn draw-all-asteroids [ctx asteroids]
;;   (.save ctx)
;;   (set! (.-strokeStyle ctx) \"#FFFFFF\")
;;   (set! (.-lineWidth ctx) 2)
;;   (doseq [asteroid asteroids]
;;     (draw-asteroid-shape ctx asteroid))
;;   (.restore ctx))
;; ```

;; ## Key Takeaways

;; Building Asteroids with Scittle demonstrates:

;; 1. **Zero-Build Game Development** - Iterate rapidly without tooling
;; 2. **Physics Simulation** - Realistic movement with simple math
;; 3. **Collision Detection** - Efficient spatial queries with batch processing
;; 4. **Canvas Graphics** - Vector-based retro rendering
;; 5. **Game State Management** - Single atom, pure functions, atomic updates
;; 6. **Web Audio API** - Procedural sound synthesis for retro effects
;; 7. **Keyword Arguments** - Self-documenting, maintainable code
;; 8. **Browser APIs** - Canvas, requestAnimationFrame, keyboard, touch, audio
;; 9. **Functional Patterns** - Immutable state, pure logic, single `swap!` updates
;; 10. **Code Review Integration** - Erik's feedback improved our atom usage

;; ## Technical Highlights

;; What makes this implementation special:

;; ### Clean Separation of Concerns

;; ```clojure
;; ;; Physics (pure functions)
;; (defn update-physics [entity]
;;   (-> entity
;;       (update :x + (:vx entity))
;;       (update :y + (:vy entity))
;;       (update :vx * friction)))
;;
;; ;; Collision (pure functions)
;; (defn check-collision [obj1 obj2]
;;   (< (distance ...) (+ radius1 radius2)))
;;
;; ;; Rendering (side effects isolated)
;; (defn draw! [ctx state]
;;   (clear-canvas ctx)
;;   (draw-ship ctx (:ship state))
;;   (draw-asteroids ctx (:asteroids state)))
;;
;; ;; Sound (side effects isolated)
;; (defn play-laser-sound []
;;   (play-tone :frequency 800 :duration 0.1 :volume 0.2))
;;
;; ;; State updates (atoms)
;; (swap! game-state update-in [:ship] update-physics)
;; ```

;; ### Single Atomic State Updates

;; Following Erik's code review feedback, all state modifications use single `swap!` calls:

;; ```clojure
;; ;; ❌ Multiple swaps - race conditions!
;; (swap! game-state assoc-in [:ship :x] new-x)
;; (swap! game-state assoc-in [:ship :y] new-y)
;; (swap! game-state assoc-in [:ship :vx] 0)
;;
;; ;; ✅ Single atomic update
;; (swap! game-state
;;        (fn [state]
;;          (-> state
;;              (assoc-in [:ship :x] new-x)
;;              (assoc-in [:ship :y] new-y)
;;              (assoc-in [:ship :vx] 0))))
;; ```

;; ### Keyword Arguments Everywhere

;; Makes complex function calls readable:

;; ```clojure
;; ;; Compare these calls:
;;
;; ;; Traditional
;; (create-asteroid 300 400 :large)
;; (draw-ship ctx 400 300 1.57 true 0)
;; (check-collision ship asteroid 10 40)
;;
;; ;; With keyword arguments
;; (create-asteroid :x 300 :y 400 :size-type :large)
;; (draw-ship :ctx ctx :x 400 :y 300 :angle 1.57 :thrusting true :invulnerable 0)
;; (check-collision :obj1 ship :obj2 asteroid :radius1 10 :radius2 40)
;; ```

;; ## Resources and Links

;; - [Scittle Documentation](https://github.com/babashka/scittle)
;; - [Reagent Guide](https://reagent-project.github.io/)
;; - [Canvas API Tutorial](https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API/Tutorial)
;; - [Game Programming Patterns](https://gameprogrammingpatterns.com/)
;; - [Original Asteroids](https://en.wikipedia.org/wiki/Asteroids_(video_game))

;; ## Conclusion

;; This Asteroids implementation demonstrates that you don't need complex build tools, game engines, or frameworks to create engaging games that work across all devices. With Scittle, ClojureScript, and the Canvas API, you can build classic arcade games that run anywhere, load instantly, and are easy to modify.

;; ### The Development Journey

;; What started as a desktop game evolved through real-world usage:

;; 1. **Initial Build**: Classic desktop keyboard controls
;; 2. **Kid Testing**: "Dad, can we play on our phones?"
;; 3. **Mobile Support**: Virtual joystick and touch buttons added
;; 4. **Performance Crisis**: Kids discovered the exponential asteroid bug
;; 5. **Optimization**: Collision batching and safety limits implemented
;; 6. **Polish**: Smooth 60 FPS on both desktop and mobile

;; This iterative process shows the value of real user testing - especially with enthusiastic kids who push games to their limits!

;; ### Technical Achievements

;; The final implementation showcases:

;; - **Cross-Platform Input**: Seamless keyboard and touch control
;; - **Robust Collision System**: Batched updates prevent race conditions
;; - **Performance Optimization**: Safety limits ensure smooth gameplay
;; - **Retro Sound Effects**: Web Audio API for authentic arcade audio
;; - **Functional Programming**: Pure functions with immutable state
;; - **Atomic State Updates**: Single `swap!` calls following Erik's guidance
;; - **Reactive UI**: Reagent atoms for predictable updates
;; - **Zero Build Tools**: Instant development feedback with Scittle
;; - **Keyword Arguments**: Self-documenting, maintainable code

;; The combination of functional programming, reactive state management, physics simulation, and canvas graphics creates a solid foundation for game development. Whether you're learning ClojureScript, teaching game programming, or just having fun recreating classics, this approach offers immediate feedback and pure development joy.

;; Now get out there and see how high you can score! Watch out for those UFOs - they're smarter than they look! And remember, hyperspace is risky but sometimes necessary! 🚀📱

;; ### Pro Tips for High Scores

;; - Use hyperspace as a last resort (remember that 10% death chance!)
;; - Small asteroids are worth the most points (100 vs 20 for large)
;; - UFOs give 200 points - prioritize them when they appear
;; - Master the thrust-and-drift technique for precise control
;; - Keep moving! Sitting still makes you an easy target
;; - Break up large asteroids early before they become a swarm
;; - Use screen wrap-around to your advantage for quick escapes
;; - Listen to the sounds! Audio cues tell you what's happening on screen

;; ---

;; *Want to see more browser-native ClojureScript projects? Check out my other articles on [ClojureCivitas](https://clojurecivitas.github.io/) where we explore the boundaries of what's possible without build tools!*
