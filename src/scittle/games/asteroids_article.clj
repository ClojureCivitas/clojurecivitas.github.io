^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Build Asteroids with ClojureScript & Scittle"
         :quarto {:author [:burinc]
                  :description "Create a classic Asteroids arcade game with physics simulation, collision detection, and canvas graphics - all running in the browser with zero build tools!"
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
                         :no-build]
                  :keywords [:asteroids-game
                             :canvas-graphics
                             :physics-simulation
                             :collision-detection
                             :vector-graphics
                             :game-loop
                             :keyboard-controls
                             :retro-gaming
                             :arcade-classics]}}}

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
;; - **Progressive difficulty** with increasing asteroid counts
;; - **High score tracking** to compete against yourself
;; - **Canvas-based vector graphics** for that authentic retro feel

;; All of this with keyword arguments for clean, readable code!

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

;; One of the most iconic features! Teleport to safety... or die trying:

;; ```clojure
;; (defn hyperspace!
;;   []
;;   (when (<= (:hyperspace-cooldown @game-state) 0)
;;     ;; Teleport to random location
;;     (swap! game-state assoc-in [:ship :x] (rand-int canvas-width))
;;     (swap! game-state assoc-in [:ship :y] (rand-int canvas-height))
;;     (swap! game-state assoc-in [:ship :vx] 0)
;;     (swap! game-state assoc-in [:ship :vy] 0)
;;     (swap! game-state assoc :hyperspace-cooldown hyperspace-cooldown)
;;     ;; 10% chance of explosion - risk vs reward!
;;     (when (< (rand) 0.1)
;;       (swap! game-state update-in [:lives] dec)
;;       (swap! game-state update :particles
;;              #(vec (concat % (create-particles :x ship-x :y ship-y
;;                                                :count 20 :color \"#FFFFFF\")))))))
;; ```

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
;; - **Response**: Separate detection from response logic
;; - **Invulnerability**: Temporary immunity after hits

;; ### 3. Game Feel

;; - **Particle Effects**: Visual feedback for actions
;; - **Screen Shake**: Could add impact feedback
;; - **Sound Effects**: Web Audio API integration
;; - **Score Popups**: Animated feedback
;; - **Difficulty Curve**: Progressive challenge

;; ### 4. State Management

;; - **Single Source of Truth**: One atom for all state
;; - **Immutable Updates**: Pure functional transformations
;; - **Predictable Updates**: No side effects in update logic
;; - **Reactive Rendering**: Automatic UI updates

;; ## Try the Game!

;; The complete Asteroids game is embedded below. Use arrow keys to rotate and thrust, spacebar to fire, and X for hyperspace!

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

;; ### 3. Audio

;; - Thrust sound loop
;; - Laser firing sounds
;; - Explosion effects
;; - UFO siren
;; - Background music
;; - Use Web Audio API for synthesized retro sounds

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
;; 3. **Collision Detection** - Efficient spatial queries
;; 4. **Canvas Graphics** - Vector-based retro rendering
;; 5. **Game State Management** - Single atom, pure functions
;; 6. **Keyword Arguments** - Self-documenting, maintainable code
;; 7. **Browser APIs** - Canvas, requestAnimationFrame, keyboard events
;; 8. **Functional Patterns** - Immutable state, pure logic

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
;; ;; State updates (atoms)
;; (swap! game-state update-in [:ship] update-physics)
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

;; This Asteroids implementation proves that you don't need complex build tools, game engines, or frameworks to create engaging games. With Scittle, ClojureScript, and the Canvas API, you can build classic arcade games that run anywhere, load instantly, and are easy to modify.

;; The combination of functional programming, reactive state management, physics simulation, and canvas graphics creates a solid foundation for game development. Whether you're learning ClojureScript, teaching game programming, or just having fun recreating classics, this approach offers immediate feedback and pure development joy.

;; Now get out there and see how high you can score! Watch out for those UFOs - they're smarter than they look! 🚀

;; ### Pro Tips for High Scores

;; - Use hyperspace as a last resort (remember that 10% death chance!)
;; - Small asteroids are worth the most points (100 vs 20 for large)
;; - UFOs give 200 points - prioritize them when they appear
;; - Master the thrust-and-drift technique for precise control
;; - Keep moving! Sitting still makes you an easy target
;; - Break up large asteroids early before they become a swarm
;; - Use screen wrap-around to your advantage for quick escapes

;; ---

;; *Want to see more browser-native ClojureScript projects? Check out my other articles on [ClojureCivitas](https://clojurecivitas.github.io/) where we explore the boundaries of what's possible without build tools!*
