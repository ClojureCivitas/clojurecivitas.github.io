^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Build Galaga with ClojureScript & Scittle"
         :quarto {:author [:burinc]
                  :description "Recreate the classic Galaga arcade game with formation patterns, swooping enemies, and intense space combat - all running in the browser with zero build tools!"
                  :type :post
                  :date "2025-11-09"
                  :category :games
                  :image "galaga-01.png"
                  :tags [:scittle
                         :clojurescript
                         :reagent
                         :games
                         :galaga
                         :canvas
                         :arcade
                         :retro
                         :no-build
                         :mobile
                         :touch-controls
                         :formation-patterns]
                  :keywords [:galaga-game
                             :canvas-graphics
                             :enemy-ai
                             :swoop-patterns
                             :formation-gameplay
                             :game-loop
                             :keyboard-controls
                             :touch-controls
                             :mobile-gaming
                             :retro-gaming
                             :arcade-classics
                             :space-shooter]}}}

(ns scittle.games.galaga
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # Build Galaga with ClojureScript & Scittle

;; ## About This Project

;; Remember spending hours at the arcade, defending Earth from waves of alien invaders? Today, I'll show you how to recreate one of the most beloved arcade classics - Galaga - using ClojureScript and Scittle, running entirely in your browser!

;; This is part of my ongoing exploration of browser-native game development with Scittle. Check out my previous projects:

;; - [Build Asteroids with ClojureScript & Scittle](https://clojurecivitas.github.io/scittle/games/asteroids_article.html) - Classic space shooter with physics
;; - [Build a Memory Game with Scittle](https://clojurecivitas.github.io/scittle/games/memory_game_article.html) - Simon-style memory challenge
;; - [Building Browser-Native Presentations](https://clojurecivitas.github.io/scittle/presentations/browser_native_slides.html) - Interactive presentation systems

;; This project demonstrates classic arcade game mechanics including enemy formation patterns, swooping attacks, and progressive difficulty - all without any build tools!

;; ## What We're Building

;; We'll create a complete Galaga-style game featuring:

;; - **Enemy Formations** with bees, butterflies, and boss aliens
;; - **Swooping Attacks** where enemies dive at your ship
;; - **Formation Movement** with synchronized enemy patterns
;; - **Progressive Waves** that get increasingly challenging
;; - **Particle Effects** for explosions and destruction
;; - **Scrolling Starfield** for authentic space atmosphere
;; - **Arcade Sound Effects** using Web Audio API for retro audio
;; - **High Score Tracking** to compete with yourself
;; - **Mobile Touch Controls** for playing on phones and tablets
;; - **Canvas-Based Graphics** using emojis for retro charm

;; All with clean, readable code using keyword arguments!

;; ## Why Galaga?

;; Galaga (1981) revolutionized the space shooter genre with:
;;
;; - **Distinctive Enemy Behavior**: Three enemy types with unique movement patterns
;; - **Formation Dynamics**: Enemies maintain formation then break away to attack
;; - **Swooping Mechanics**: Elegant dive-bomb patterns that challenge players
;; - **Progressive Difficulty**: Each wave introduces more enemies
;; - **Iconic Design**: Instantly recognizable sprites and sound effects

;; This makes it perfect for learning game development - complex enough to be interesting, but simple enough to implement from scratch!

;; ## Game Architecture

;; The game follows a clean, functional architecture:

^:kindly/hide-code
(kind/mermaid
 "graph TD
    A[Game State Atom] --> B[Game Loop]
    B --> C[Update Enemies]
    B --> D[Check Collisions]
    B --> E[Handle Input]
    C --> F[Update Formations]
    C --> G[Process Swooping]
    C --> H[Enemy AI]
    D --> I[Bullet-Enemy]
    D --> J[Ship-Enemy Bullets]
    E --> K[Keyboard Events]
    E --> L[Touch Controls]
    K --> M[Move/Fire]
    L --> M
    F --> N[Canvas Rendering]
    G --> N
    H --> N
    N --> B")

;; ## Core Game Systems

;; ### 1. State Management

;; All game state lives in a single Reagent atom:

;; ```clojure
;; (def game-state
;;   (r/atom {:player {:x 240 :y 560 :lives 3}
;;            :bullets []
;;            :enemy-bullets []
;;            :enemies []
;;            :particles []
;;            :stars []
;;            :wave 1
;;            :score 0
;;            :high-score 0
;;            :game-status :ready
;;            :formation-offset {:x 0 :y 0}
;;            :formation-direction 1
;;            :touch-controls {:left-pressed false
;;                             :right-pressed false
;;                             :fire-pressed false}}))
;; ```

;; ### 2. Enemy Types and Formation

;; Galaga features three distinct enemy types:

;; ```clojure
;; (def enemy-types
;;   {:bee {:color \"#00FFFF\" :points 50 :hits 1 :sprite \"🦋\"}
;;    :butterfly {:color \"#FF00FF\" :points 80 :hits 1 :sprite \"🦋\"}
;;    :boss {:color \"#00FF00\" :points 150 :hits 2 :sprite \"👾\"}})
;;
;; ;; Formation pattern - boss row at top, butterflies in middle, bees at bottom
;; (def formation-positions
;;   (concat
;;    ;; Boss row (4 enemies)
;;    (for [x (range 4)] {:x (+ 140 (* x 60)) :y 100 :type :boss})
;;    ;; Butterfly rows (16 enemies)
;;    (for [x (range 8)] {:x (+ 80 (* x 40)) :y 140 :type :butterfly})
;;    (for [x (range 8)] {:x (+ 80 (* x 40)) :y 180 :type :butterfly})
;;    ;; Bee rows (20 enemies)
;;    (for [x (range 10)] {:x (+ 40 (* x 40)) :y 220 :type :bee})
;;    (for [x (range 10)] {:x (+ 40 (* x 40)) :y 260 :type :bee})))
;; ```

;; ### 3. Enemy AI and Swooping

;; Enemies maintain formation, then randomly swoop down to attack:

;; ```clojure
;; (defn generate-swoop-path
;;   [{:keys [start-x start-y]}]
;;   (let [side (if (< start-x (/ canvas-width 2)) 1 -1)]
;;     (vec (for [t (range 0 1.01 0.02)]
;;            (let [angle (* t Math/PI 2)
;;                  x (+ start-x (* side 150 (Math/sin angle)))
;;                  y (+ start-y (* 400 t))]
;;              {:x x :y y})))))
;;
;; (defn start-swoop!
;;   []
;;   (let [formation-enemies (filter #(= (:state %) :formation) enemies)]
;;     (when (and (> (count formation-enemies) 0)
;;                (< (rand) 0.02)) ; 2% chance per frame
;;       (let [enemy (rand-nth formation-enemies)
;;             path (generate-swoop-path {:start-x (:x enemy)
;;                                        :start-y (:y enemy)})]
;;         ;; Update enemy to swooping state with path
;;         ...))))
;; ```

;; ## Key Game Mechanics

^:kindly/hide-code
(kind/mermaid
 "sequenceDiagram
    participant P as Player
    participant G as Game Loop
    participant E as Enemies
    participant C as Collision System
    participant R as Renderer

    P->>G: Start Game
    G->>E: Init Formation
    loop Every Frame
        P->>G: Input (Move/Fire)
        E->>E: Update Formation
        E->>E: Random Swoop?
        alt Enemy Swoops
            E->>E: Follow Swoop Path
            E->>G: Fire at Player
        end
        G->>C: Check Collisions
        alt Bullet Hits Enemy
            C->>G: Damage/Destroy
            C->>G: Add Score
            C->>G: Create Particles
        end
        G->>R: Draw Everything
        R-->>P: Display Frame
    end")

;; ### Formation Movement

;; The entire formation moves side-to-side:

;; ```clojure
;; (defn update-game!
;;   []
;;   ;; Update formation offset (side-to-side movement only)
;;   (let [direction (:formation-direction @game-state)]
;;     (swap! game-state update :formation-offset
;;            (fn [{:keys [x y]}]
;;              (let [new-x (+ x (* direction 0.5))]
;;                (cond
;;                  (and (>= new-x 30) (= direction 1))
;;                  (do (swap! game-state assoc :formation-direction -1)
;;                      {:x 30 :y y})  ; Hit right edge, reverse
;;                  (and (<= new-x -30) (= direction -1))
;;                  (do (swap! game-state assoc :formation-direction 1)
;;                      {:x -30 :y y})  ; Hit left edge, reverse
;;                  :else {:x new-x :y y})))))
;;
;;   ;; Apply offset to all formation enemies
;;   (swap! game-state update :enemies
;;          (fn [enemies]
;;            (mapv (fn [enemy]
;;                    (if (= (:state enemy) :formation)
;;                      (assoc enemy
;;                             :x (+ (:formation-x enemy)
;;                                   (get-in @game-state [:formation-offset :x]))
;;                             :y (+ (:formation-y enemy)
;;                                   (get-in @game-state [:formation-offset :y])))
;;                      enemy))
;;                  enemies))))
;; ```

;; ### Enemy States

;; Each enemy can be in one of three states:

;; ```clojure
;; :formation  ; Following formation pattern
;; :swooping   ; Diving at player
;; :returning  ; Flying back to formation
;;
;; (defn update-swooping-enemy
;;   [enemy]
;;   (if (and (= (:state enemy) :swooping) (:swoop-path enemy))
;;     (let [progress (min (+ (:path-progress enemy) 0.02) 1)
;;           path-index (int (* progress (dec (count (:swoop-path enemy)))))
;;           pos (nth (:swoop-path enemy) path-index)]
;;       (if (>= progress 1)
;;         (assoc enemy :state :returning)  ; Finished swooping
;;         (assoc enemy :x (:x pos) :y (:y pos))))  ; Continue path
;;     enemy))
;;
;; ;; Update enemies in game loop using threading
;; (swap! game-state update :enemies
;;        (fn [enemies]
;;          (mapv (fn [enemy]
;;                  (-> enemy
;;                      update-swooping-enemy
;;                      update-returning-enemy))
;;                enemies)))
;; ```

;; ## Collision Detection

;; Simple but effective rectangle-based collision:

;; ```clojure
;; (defn collides?
;;   [& {:keys [a b]}]
;;   (and (< (Math/abs (- (:x a) (:x b)))
;;           (/ (+ (:width a) (:width b)) 2))
;;        (< (Math/abs (- (:y a) (:y b)))
;;           (/ (+ (:height a) (:height b)) 2))))
;; ```

;; ### Critical Bug: Collision Detection Race Conditions

;; During development, we discovered a critical bug that made enemies invulnerable! The problem was subtle but devastating:

;; **The Bug:**
;; ```clojure
;; ;; ❌ BROKEN - Enemies won't die!
;; (doseq [bullet bullets
;;         enemy enemies]
;;   (when (collides? :a bullet :b enemy)
;;     ;; Each collision triggers separate swap!
;;     (swap! game-state update :bullets remove-bullet)
;;     (swap! game-state update :enemies remove-enemy)
;;     ...))
;; ```

;; **Two problems here:**

;; 1. **Stale Data**: `bullets` and `enemies` were captured at the start of `update-game!`, but we check collisions AFTER updating bullet positions. So we're checking collisions with OLD positions!

;; 2. **Race Conditions**: Each collision in `doseq` triggers a separate `swap!`, but all iterations use the ORIGINAL collections. If bullet A hits enemies 1 and 2, both iterations try to remove bullet A, causing state corruption.

;; **The Solution: Collision Batching**

;; We adopted the same pattern used in Asteroids (per [Erik Assum](https://github.com/slipset)'s excellent feedback):

;; ```clojure
;; ;; ✅ FIXED - Batch collision detection
;; ;; Get FRESH bullets and enemies after updates
;; (let [current-bullets (:bullets @game-state)
;;       current-enemies (:enemies @game-state)
;;       hit-bullets (atom #{})
;;       hit-enemies (atom #{})
;;       damaged-enemies (atom {})
;;       score-added (atom 0)
;;       new-particles (atom [])]
;;
;;   ;; Collect all collisions (but don't apply yet)
;;   (doseq [bullet current-bullets
;;           :when (not (contains? @hit-bullets bullet))
;;           enemy current-enemies
;;           :when (and (not (contains? @hit-enemies enemy))
;;                      (not= (:state enemy) :destroyed))]
;;     (when (collides? :a bullet :b enemy)
;;       ;; Mark bullet as hit
;;       (swap! hit-bullets conj bullet)
;;
;;       (let [new-hits (dec (:hits enemy))
;;             destroyed? (<= new-hits 0)]
;;         (if destroyed?
;;           ;; Enemy destroyed - mark for removal
;;           (do
;;             (swap! hit-enemies conj enemy)
;;             (swap! score-added + (:points enemy))
;;             (swap! new-particles concat
;;                    (create-particles :x (:x enemy)
;;                                      :y (:y enemy)
;;                                      :count 10
;;                                      :color (:color enemy))))
;;           ;; Enemy damaged - mark for hit count update
;;           (swap! damaged-enemies assoc enemy new-hits)))))
;;
;;   ;; Apply all collision effects at once (single atomic swap!)
;;   (when (or (seq @hit-bullets) (seq @hit-enemies) (seq @damaged-enemies))
;;     (when (seq @hit-enemies)
;;       (play-explosion-sound))
;;
;;     (swap! game-state
;;            (fn [state]
;;              (-> state
;;                  ;; Remove hit bullets
;;                  (update :bullets
;;                          (fn [bullets]
;;                            (vec (remove #(contains? @hit-bullets %) bullets))))
;;                  ;; Remove destroyed enemies and update damaged ones
;;                  (update :enemies
;;                          (fn [enemies]
;;                            (vec (keep (fn [e]
;;                                         (cond
;;                                           ;; Enemy destroyed - remove it
;;                                           (contains? @hit-enemies e)
;;                                           nil
;;
;;                                           ;; Enemy damaged - update hits
;;                                           (contains? @damaged-enemies e)
;;                                           (assoc e :hits (get @damaged-enemies e))
;;
;;                                           ;; Enemy not hit - keep as is
;;                                           :else e))
;;                                       enemies))))
;;                  ;; Add score for destroyed enemies
;;                  (update :score + @score-added)
;;                  ;; Add particles for destroyed enemies
;;                  (update :particles
;;                          (fn [particles]
;;                            (vec (concat particles @new-particles)))))))))
;; ```

;; **Benefits of the fix:**

;; 1. **Fresh Data**: Capture bullets/enemies AFTER position updates
;; 2. **No Race Conditions**: Sets track which objects are already hit
;; 3. **Single Atomic Update**: One `swap!` applies all effects at once
;; 4. **Proper Boss Damage**: Correctly tracks multi-hit enemies
;; 5. **Predictable Behavior**: State updates are deterministic

;; This same issue affected our Asteroids game, and the batched approach solved both!

;; ## Visual Effects

;; ### Particle System

;; Explosions create satisfying particle effects:

;; ```clojure
;; (defn create-particles
;;   [& {:keys [x y color count]}]
;;   (for [_ (range count)]
;;     {:x x
;;      :y y
;;      :vx (- (rand 6) 3)  ; Random horizontal velocity
;;      :vy (- (rand 6) 3)  ; Random vertical velocity
;;      :life 30            ; Frames to live
;;      :color color}))
;;
;; ;; Draw particles with fade effect
;; (doseq [particle particles]
;;   (set! (.-fillStyle ctx) (:color particle))
;;   (set! (.-globalAlpha ctx) (/ (:life particle) 30))  ; Fade out
;;   (.fillRect ctx (:x particle) (:y particle) 3 3))
;; ```

;; ### Scrolling Starfield

;; Creates depth and movement:

;; ```clojure
;; (defn init-stars []
;;   (vec (for [_ (range 50)]
;;          {:x (rand-int canvas-width)
;;           :y (rand-int canvas-height)
;;           :speed (+ 0.5 (rand 2))
;;           :size (+ 1 (rand-int 2))})))
;;
;; ;; Update stars - scroll and wrap
;; (swap! game-state update :stars
;;        (fn [stars]
;;          (mapv (fn [star]
;;                  (let [new-y (+ (:y star) (:speed star))]
;;                    (if (> new-y canvas-height)
;;                      (assoc star :y 0 :x (rand-int canvas-width))
;;                      (assoc star :y new-y))))
;;                stars)))
;; ```

;; ## Arcade-Style Sound Effects

;; What's an arcade game without sound? We use the **Web Audio API** to create procedural sound effects that bring the game to life!

;; ### Setting Up Audio

;; The audio system uses oscillators to generate tones at different frequencies:

;; ```clojure
;; (def audio-context (when (exists? js/AudioContext) (js/AudioContext.)))
;;
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
;;         (js/console.error \"Audio error:\" e)))))
;; ```

;; ### Sound Effect Library

;; Each game action has its own distinctive sound:

;; ```clojure
;; (defn play-laser-sound
;;   "High-pitched beep for player shooting"
;;   []
;;   (play-tone :frequency 800 :duration 0.1 :volume 0.2))
;;
;; (defn play-explosion-sound
;;   "Low boom for enemy destruction"
;;   []
;;   (play-tone :frequency 100 :duration 0.2 :volume 0.3))
;;
;; (defn play-hit-sound
;;   "Mid-range alert for player damage"
;;   []
;;   (play-tone :frequency 150 :duration 0.3 :volume 0.4))
;;
;; (defn play-swoop-sound
;;   "Dive siren when enemies attack"
;;   []
;;   (play-tone :frequency 600 :duration 0.15 :volume 0.15))
;;
;; (defn play-wave-complete-sound
;;   "Victory fanfare - ascending notes"
;;   []
;;   ;; Play C-E-G-C progression
;;   (doseq [[idx freq] (map-indexed vector [523 659 784 1047])]
;;     (js/setTimeout
;;      #(play-tone :frequency freq :duration 0.2 :volume 0.25)
;;      (* idx 100))))
;; ```

;; ### Triggering Sounds

;; Sounds are triggered at the right moments in gameplay:

;; ```clojure
;; ;; When player shoots
;; (defn fire-bullet! []
;;   (play-laser-sound)  ; 🔫 Pew!
;;   (let [{:keys [player]} @game-state]
;;     (swap! game-state update :bullets
;;            #(conj % {:x (:x player) :y (:y player) ...}))))
;;
;; ;; When enemy is destroyed
;; (when (<= new-hits 0)
;;   (play-explosion-sound)  ; 💥 Boom!
;;   (swap! game-state update :enemies #(remove-enemy %)))
;;
;; ;; When player takes damage
;; (when (collides? :a bullet :b player)
;;   (play-hit-sound)  ; 💔 Ouch!
;;   (swap! game-state update-in [:player :lives] dec))
;;
;; ;; When enemy starts swooping
;; (defn start-swoop! []
;;   (when (should-swoop?)
;;     (play-swoop-sound)  ; 🎵 Dive siren!
;;     (update-enemy-to-swoop!)))
;;
;; ;; When wave is cleared
;; (when (empty? enemies)
;;   (play-wave-complete-sound)  ; 🎉 Victory!
;;   (js/setTimeout #(init-next-wave!) 500))
;; ```

;; ### Why Web Audio API?

;; Using the Web Audio API for procedural sound generation offers several advantages:

;; - **No audio files needed** - Everything is generated in real-time
;; - **Tiny file size** - A few lines of code instead of MB of audio
;; - **Instant loading** - No waiting for audio resources
;; - **Retro authentic** - Sounds like classic arcade games
;; - **Easy to tune** - Just adjust frequency and duration values
;; - **Cross-browser** - Works everywhere JavaScript runs

;; The frequency values are chosen to be distinctive yet not annoying:
;; - **High frequencies (600-800Hz)**: Action sounds (lasers, swoops)
;; - **Low frequencies (100-150Hz)**: Impact sounds (explosions, hits)
;; - **Musical notes (C-E-G-C)**: Victory celebrations

;; ## Game Loop Architecture

^:kindly/hide-code
(kind/mermaid
 "graph LR
    A[requestAnimationFrame] --> B{Game Playing?}
    B -->|Yes| C[Handle Input]
    C --> D[Update Formation]
    D --> E[Update Swooping]
    E --> F[Update Bullets]
    F --> G[Check Collisions]
    G --> H[Update Particles]
    H --> I[Render Canvas]
    I --> J[Next Frame]
    J --> A
    B -->|No| K[Pause/Wait]")

;; The game loop runs at 60 FPS:

;; ```clojure
;; (letfn [(game-loop []
;;           ;; Handle continuous movement
;;           (when (= (:game-status @game-state) :playing)
;;             (let [touch-controls (:touch-controls @game-state)]
;;               ;; Keyboard controls
;;               (when (contains? @keys-pressed \"ArrowLeft\")
;;                 (move-player! :direction -1))
;;               (when (contains? @keys-pressed \"ArrowRight\")
;;                 (move-player! :direction 1))
;;               ;; Touch controls
;;               (when (:left-pressed touch-controls)
;;                 (move-player! :direction -1))
;;               (when (:right-pressed touch-controls)
;;                 (move-player! :direction 1))))
;;
;;           ;; Update game state
;;           (update-game!)
;;
;;           ;; Render to canvas
;;           (draw-game! :ctx ctx)
;;
;;           ;; Schedule next frame
;;           (reset! animation-id (js/requestAnimationFrame game-loop)))]
;;   (game-loop))
;; ```

;; ## Mobile Touch Controls

;; The game includes simple touch controls for mobile:

;; ```clojure
;; (defn touch-control-button
;;   [& {:keys [label position on-press on-release color]}]
;;   (let [button-ref (atom nil)
;;         touch-id (atom nil)]
;;     (r/create-class
;;      {:component-did-mount
;;       (fn []
;;         (when-let [button @button-ref]
;;           (let [handle-touch-start
;;                 (fn [e]
;;                   (.preventDefault e)
;;                   (when on-press (on-press)))
;;
;;                 handle-touch-end
;;                 (fn [e]
;;                   (.preventDefault e)
;;                   (when on-release (on-release)))]
;;
;;             (.addEventListener button \"touchstart\" handle-touch-start)
;;             (.addEventListener button \"touchend\" handle-touch-end))))
;;
;;       :render
;;       (fn []
;;         [:div {:ref #(reset! button-ref %)
;;                :style {:position \"fixed\"
;;                        :border-radius \"50%\"
;;                        :touch-action \"none\"
;;                        ...}}
;;          label])})))
;; ```

;; Three buttons provide full control:
;; - **Left Arrow (◀)**: Move left
;; - **Right Arrow (▶)**: Move right
;; - **FIRE Button**: Shoot bullets

;; ## Canvas Rendering

;; Drawing uses emoji sprites for retro charm:

;; ```clojure
;; (defn draw-game!
;;   [& {:keys [ctx]}]
;;   ;; Clear canvas with space background
;;   (set! (.-fillStyle ctx) \"#000033\")
;;   (.fillRect ctx 0 0 canvas-width canvas-height)
;;
;;   ;; Draw stars
;;   (set! (.-fillStyle ctx) \"#FFFFFF\")
;;   (doseq [star stars]
;;     (.fillRect ctx (:x star) (:y star) (:size star) (:size star)))
;;
;;   ;; Draw enemies with emoji sprites
;;   (doseq [enemy enemies]
;;     (let [props (get enemy-types (:type enemy))]
;;       (set! (.-fillStyle ctx) \"#FFFFFF\")
;;       (set! (.-font ctx) \"30px Arial\")
;;       (set! (.-textAlign ctx) \"center\")
;;       (.fillText ctx (:sprite props) (:x enemy) (:y enemy))))
;;
;;   ;; Draw player
;;   (set! (.-font ctx) \"30px Arial\")
;;   (.fillText ctx \"🚀\" (:x player) (:y player)))
;; ```

;; ## Progressive Difficulty

;; Each wave introduces more challenge:

;; ```clojure
;; (defn init-wave!
;;   [& {:keys [wave-num]}]
;;   (swap! game-state assoc
;;          :enemies (vec (map create-enemy formation-positions))
;;          :bullets []
;;          :enemy-bullets []
;;          :formation-offset {:x 0 :y 0}))
;;
;; ;; Check for wave completion
;; (when (empty? enemies)
;;   (swap! game-state update :wave inc)
;;   (init-wave! :wave-num (inc wave)))
;; ```

;; ## Learning Points

;; This project teaches several game development concepts:

;; ### 1. Formation Patterns

;; - **Grid Layouts**: Organizing enemies in rows and columns (boss, butterfly, bee types)
;; - **Synchronized Movement**: Moving entire formations together side-to-side
;; - **Edge Detection**: Reversing direction when hitting boundaries
;; - **Individual Behavior**: Enemies break from formation to swoop attack
;; - **Return to Formation**: Swooping enemies navigate back to original positions

;; ### 2. Path Following

;; - **Parametric Curves**: Using sine waves for smooth swooping
;; - **Path Progress**: Tracking position along a path
;; - **Path Completion**: Transitioning between states
;; - **Return Navigation**: Flying back to formation position

;; ### 3. State Machines

;; - **Enemy States**: Formation, swooping, returning
;; - **State Transitions**: Rules for changing states
;; - **State-Specific Updates**: Different behavior per state
;; - **Predictable Behavior**: Deterministic state flow

;; ### 4. Game Balance

;; - **Enemy Points**: Higher risk enemies give more points
;; - **Hit Points**: Boss enemies require multiple hits
;; - **Fire Rate**: Balancing enemy shooting frequency
;; - **Swoop Chance**: Tuning attack frequency

;; ### 5. Audio Feedback

;; - **Action-Reaction Sounds**: Immediate audio feedback for player actions
;; - **Frequency Selection**: Choosing distinctive tones for different events
;; - **Volume Balancing**: Keeping sounds audible but not overwhelming
;; - **Timing**: Coordinating sound playback with visual events
;; - **No External Files**: Procedural generation keeps the game lightweight

;; ## Try the Game!

;; The complete Galaga game is embedded below. Works on both desktop and mobile!

;; **Desktop Controls:**
;; - Arrow keys to move left/right
;; - Spacebar to fire

;; **Mobile Controls:**
;; - Touch buttons (bottom-left) to move
;; - FIRE button (bottom-right) to shoot

^:kindly/hide-code
(kind/hiccup
 [:div {:style {:margin "2rem 0"
                :padding "2rem"
                :border "2px solid #e9ecef"
                :border-radius "8px"
                :background-color "#f8f9fa"}}
  [:div#galaga-game-root {:style {:min-height "800px"}}]
  [:script {:type "application/x-scittle"
            :src "galaga.cljs"}]])

;; ## Extending the Game

;; Here are ideas to enhance your Galaga clone:

;; ### 1. Advanced Enemy Behavior

;; - **Capture Beam**: Boss enemies can capture your ship (like original Galaga!)
;; - **Dual Fighter**: Rescue captured ship for double firepower
;; - **Challenge Stages**: Bonus rounds with patterns but no shooting
;; - **Kamikaze Enemies**: Dive directly at player

;; ### 2. Power-Ups

;; - **Rapid Fire**: Increased shooting speed
;; - **Shield**: Temporary invulnerability
;; - **Smart Bombs**: Clear all enemies on screen
;; - **Score Multipliers**: Chain bonuses

;; ### 3. Visual Enhancements

;; - **Better Sprites**: Replace emojis with pixel art
;; - **Explosion Animations**: Frame-by-frame explosions
;; - **Thrust Effects**: Glowing engine trails
;; - **Screen Shake**: Impact feedback
;; - **Color Flashing**: Damage indication

;; ### 4. Enhanced Audio

;; Build on the existing sound system:

;; - **Background Music**: Continuous chiptune soundtrack during gameplay
;; - **Layered Sounds**: Combine multiple oscillators for richer effects
;; - **Dynamic Volume**: Adjust sound based on game intensity
;; - **Sound Pools**: Pre-generate sounds to reduce CPU usage
;; - **Stereo Panning**: Position sounds left/right based on enemy location
;; - **Frequency Sweeps**: Sliding pitch for more dynamic effects
;; - **Echo/Reverb**: Add spatial depth to explosions

;; ### 5. Gameplay Modes

;; - **Endless Mode**: Survive as long as possible
;; - **Time Attack**: Clear waves quickly
;; - **No Miss Mode**: Perfect run challenge
;; - **Boss Rush**: Fight only boss enemies
;; - **Co-op Mode**: Two players!

;; ### 6. Advanced Formation Patterns

;; - **Spiral Entry**: Enemies fly in spiraling
;; - **Figure-8 Swoops**: More complex dive patterns
;; - **Group Attacks**: Multiple enemies swoop together
;; - **Formation Changes**: Mid-game pattern shifts

;; ## Performance Considerations

;; The game is optimized for smooth 60 FPS:

;; ```clojure
;; ;; Limit particle count to prevent slowdown
;; (def max-particles 200)
;;
;; ;; Efficient collision detection - only check active objects
;; (doseq [bullet bullets
;;         enemy enemies
;;         :when (not= (:state enemy) :destroyed)]
;;   (check-collision ...))
;;
;; ;; Batch canvas operations
;; (set! (.-fillStyle ctx) \"#FFFF00\")
;; (doseq [bullet bullets]
;;   (.fillRect ctx ...))  ; Single style set for all bullets
;; ```

;; ## Key Takeaways

;; Building Galaga with Scittle demonstrates:

;; 1. **Formation Gameplay** - Organizing and moving enemy groups in synchronized patterns
;; 2. **Path-Based Movement** - Smooth swooping using parametric curves with sine waves
;; 3. **State Machines** - Managing enemy behavior with clear states (formation, swooping, returning)
;; 4. **Touch Controls** - Making arcade games mobile-friendly with touch buttons
;; 5. **Canvas Graphics** - Retro-style rendering with emoji sprites
;; 6. **Web Audio API** - Procedural sound generation for authentic arcade audio
;; 7. **Progressive Difficulty** - Keeping players engaged with wave-based progression
;; 8. **Functional Patterns** - Using map destructuring and keyword arguments for readable code
;; 9. **Game Loop Optimization** - Achieving smooth 60 FPS with efficient updates

;; ## Technical Highlights

;; What makes this implementation special:

;; ### Clean Separation of Concerns

;; ```clojure
;; ;; Enemy AI (pure functions)
;; (defn update-swooping-enemy [enemy]
;;   (if (= (:state enemy) :swooping) ...))
;;
;; ;; Collision (pure functions)
;; (defn collides? [& {:keys [a b]}]
;;   (< (distance ...) threshold))
;;
;; ;; Rendering (side effects isolated)
;; (defn draw-game! [& {:keys [ctx]}]
;;   (draw-stars ...)
;;   (draw-enemies ...)
;;   (draw-player ...))
;;
;; ;; State updates (atoms)
;; (swap! game-state update :enemies update-all-enemies)
;; ```

;; ### Keyword Arguments for Clarity

;; Makes complex function calls readable:

;; ```clojure
;; ;; Compare these approaches:
;;
;; ;; Map destructuring (used for enemy data)
;; (defn create-enemy [{:keys [x y type]}]
;;   {:x x :y y :type type ...})
;;
;; ;; Keyword arguments (used for function parameters)
;; (defn move-player! [& {:keys [direction]}]
;;   (update-player-position direction))
;;
;; (defn collides? [& {:keys [a b]}]
;;   (check-collision a b))
;;
;; ;; Clear function calls
;; (move-player! :direction -1)
;; (collides? :a player :b enemy)
;; (create-particles :x 100 :y 200 :color "#FF0000" :count 10)
;; ```

;; ## Resources and Links

;; - [Scittle Documentation](https://github.com/babashka/scittle)
;; - [Reagent Guide](https://reagent-project.github.io/)
;; - [Canvas API Tutorial](https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API/Tutorial)
;; - [Original Galaga](https://en.wikipedia.org/wiki/Galaga)
;; - [Game AI Programming](https://www.gameaipro.com/)

;; ## Conclusion

;; This Galaga implementation shows that classic arcade gameplay patterns translate beautifully to modern web development. With Scittle, you can create engaging games that:

;; - Load instantly
;; - Run everywhere (desktop and mobile)
;; - Require zero build tools
;; - Use clean, functional code
;; - Are easy to modify and extend

;; The combination of formation patterns, enemy AI, smooth animations, and progressive difficulty creates an engaging gameplay experience. The enemies maintain a tight formation at the top of the screen, moving side-to-side in synchronized patterns. Individual enemies then break away to perform swooping dive attacks before returning to formation - creating that classic Galaga tension between defensive positioning and aggressive shooting.

;; Whether you're learning game development, teaching programming concepts, or just having fun recreating classics, this approach offers a perfect balance of simplicity and depth.

;; ### From Concept to Reality

;; What started as nostalgia for a childhood favorite became a journey into:

;; - **Formation-based gameplay** mechanics
;; - **Path-following AI** for swooping attacks
;; - **State machine** patterns for enemy behavior
;; - **Procedural audio** with Web Audio API
;; - **Mobile optimization** with touch controls
;; - **Performance tuning** for smooth 60 FPS

;; The functional programming approach with Reagent atoms makes the game logic clear and maintainable, while keyword arguments ensure every function call is self-documenting. The Web Audio API brings authentic arcade sounds without requiring any audio files!

;; Now get out there and defend Earth from the alien invasion! See how many waves you can survive! 👾🚀

;; ### Pro Tips for High Scores

;; - **Stay mobile**: Don't camp in one spot
;; - **Prioritize boss enemies**: They're worth 150 points each
;; - **Watch the formation**: Predict which enemies will swoop
;; - **Dodge first**: Survival is more important than shooting
;; - **Use the edges**: Enemies swoop from the sides
;; - **Clear waves quickly**: Don't let the formation descend too far

;; ---

;; *Want to see more browser-native ClojureScript games? Check out my other articles on [ClojureCivitas](https://clojurecivitas.github.io/) where we explore classic arcade games without build tools!*
