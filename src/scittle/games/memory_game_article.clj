^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Build a Memory Game with ClojureScript & Scittle"
         :quarto {:author [:burinc]
                  :description "Create an interactive Simon-style memory game that runs entirely in the browser using ClojureScript and Scittle - no build tools required!"
                  :type :post
                  :date "2025-11-08"
                  :category :games
                  :image "memory-game.png"
                  :tags [:scittle
                         :clojurescript
                         :reagent
                         :games
                         :memory
                         :audio
                         :interactive
                         :no-build]
                  :keywords [:memory-game
                             :simon
                             :browser-game
                             :web-audio-api
                             :reagent-atoms
                             :game-state
                             :functional-programming]}}}

(ns scittle.games.memory-game-article
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # Build a Memory Game with ClojureScript & Scittle

;; ## About This Project

;; Ever wanted to build an interactive browser game without dealing with build tools, webpack, or complicated setups? Today, I'll show you how to create a Simon-style memory game using Scittle and ClojureScript that runs entirely in your browser!

;; This is part of my ongoing exploration of browser-native development with Scittle. Check out my previous articles in this series:

;; - [Building Browser-Native Presentations with Scittle](https://clojurecivitas.github.io/scittle/presentations/browser_native_slides.html) - Create interactive presentation systems without build tools
;; - [Python + ClojureScript: Pyodide Integration with Scittle](https://clojurecivitas.github.io/scittle/pyodide/pyodide_integration.html) - Run Python code directly in the browser
;; - [Free Weather Data with National Weather Service API](https://clojurecivitas.github.io/scittle/weather/weather_nws_integration.html) - Build weather applications using the free NWS API
;; - [Browser-Native QR Code Scanner with Scittle](https://clojurecivitas.github.io/scittle/qrcode/qr_code_scanner.html) - Build a QR code scanner that runs entirely in your browser

;; This project demonstrates how to build fully interactive games with zero build configuration.

;; ## What We're Building

;; We'll create a classic memory game (similar to Simon) where:

;; - The computer shows a sequence of colors
;; - Each color plays a unique musical note
;; - Players must repeat the sequence correctly
;; - The sequence grows longer with each successful round
;; - The game tracks your high score

;; All of this with keyword arguments for cleaner, more readable code and Web Audio API for sound effects!

;; ## Why Scittle for Games?

;; ### Zero Build Configuration

;; Traditional game development in ClojureScript requires:

;; - Setting up shadow-cljs or figwheel
;; - Managing dependencies
;; - Configuring build tools
;; - Dealing with hot reload setup

;; With Scittle, you just write ClojureScript and it runs. Perfect for prototyping, learning, or building simple games.

;; ### Instant Feedback

;; Changes appear immediately when you save your file. No waiting for builds, no watching compile output - just pure development flow.

;; ### Educational Value

;; This approach is perfect for:

;; - Teaching ClojureScript concepts
;; - Learning game state management
;; - Understanding React/Reagent patterns
;; - Exploring Web APIs (Audio, Animation)

;; ## Key Concepts We'll Cover

^:kindly/hide-code
(kind/mermaid
 "graph TD
    A[Game State Atom] --> B[Sequence Generation]
    A --> C[Player Input]
    A --> D[Score Tracking]
    B --> E[Visual Feedback]
    B --> F[Audio Feedback]
    C --> G[Validation Logic]
    G --> H[Next Level]
    G --> I[Game Over]")

;; ### 1. State Management with Reagent Atoms

;; We use a single atom to manage all game state:

;; ```clojure
;; (def game-state (r/atom {:sequence []          ; Computer's sequence
;;                          :player-sequence []   ; Player's input
;;                          :playing? false       ; Game active?
;;                          :showing? false       ; Showing sequence?
;;                          :score 0              ; Current level
;;                          :game-over? false     ; Game over?
;;                          :active-tile nil      ; Currently lit tile
;;                          :high-score 0}))      ; Best score
;; ```

;; ### 2. Web Audio API Integration

;; Creating dynamic sound effects without audio files:

;; ```clojure
;; (defn play-tone
;;   [& {:keys [frequency duration]
;;       :or {frequency 440 duration 0.2}}]
;;   (when audio-context
;;     (let [oscillator (.createOscillator audio-context)
;;           gain-node (.createGain audio-context)]
;;       (.connect oscillator gain-node)
;;       (.connect gain-node (.-destination audio-context))
;;       (set! (.-value (.-frequency oscillator)) frequency)
;;       (set! (.-value (.-gain gain-node)) 0.3)
;;       (.start oscillator)
;;       (.stop oscillator (+ (.-currentTime audio-context) duration)))))
;; ```

;; ### 3. Keyword Arguments Pattern

;; All functions use keyword arguments for clarity:

;; ```clojure
;; ;; Instead of: (play-tone 440 0.5)
;; ;; We write:
;; (play-tone :frequency 440 :duration 0.5)
;;
;; ;; Component props are also keyword-based:
;; [color-tile :color "#4caf50"
;;             :index 0
;;             :on-click handle-click
;;             :disabled? false]
;; ```

;; ## Game Architecture

;; The game follows a simple but effective architecture:

^:kindly/hide-code
(kind/mermaid
 "sequenceDiagram
    participant P as Player
    participant G as Game Logic
    participant UI as UI Components
    participant A as Audio System

    P->>G: Start Game
    G->>G: Generate Sequence
    G->>UI: Show Sequence
    UI->>A: Play Notes
    UI-->>P: Display Colors
    P->>UI: Click Tiles
    UI->>G: Validate Input
    alt Correct
        G->>G: Add to Sequence
        G->>UI: Next Level
    else Incorrect
        G->>UI: Game Over
        G->>G: Update High Score
    end")

;; ## Core Game Mechanics

;; ### Sequence Generation

;; Each round adds a random color to the sequence:

;; ```clojure
;; (defn add-to-sequence []
;;   (let [next-color (rand-int 4)]
;;     (swap! game-state update :sequence conj next-color)))
;; ```

;; ### Visual and Audio Feedback

;; The game shows the sequence with synchronized visuals and sounds:

;; ```clojure
;; (defn show-sequence []
;;   (doseq [[idx color-idx] (map-indexed vector sequence)]
;;     (js/setTimeout
;;      (fn []
;;        (swap! game-state assoc :active-tile color-idx)
;;        (play-tone :frequency (nth color-frequencies color-idx)
;;                   :duration 0.5)
;;        (js/setTimeout
;;         #(swap! game-state assoc :active-tile nil)
;;         400))
;;      (* idx 600))))
;; ```

;; ### Input Validation

;; Player inputs are validated immediately:

;; ```clojure
;; (defn handle-tile-click [& {:keys [tile-index]}]
;;   (let [current-position (dec (count player-sequence))]
;;     (cond
;;       ;; Wrong input - game over
;;       (not= (nth player-sequence current-position)
;;             (nth sequence current-position))
;;       (do (swap! game-state assoc :game-over? true)
;;           (play-tone :frequency 100 :duration 0.5))
;;
;;       ;; Sequence complete - next level
;;       (= (count player-sequence) (count sequence))
;;       (do (swap! game-state update :score inc)
;;           (add-to-sequence)
;;           (show-sequence)))))
;; ```

;; ## UI Components with Inline Styles

;; Each component is self-contained with its own styles:

;; ### Color Tiles

;; Interactive tiles with hover effects:

;; ```clojure
;; (defn color-tile [& {:keys [color index on-click disabled?]}]
;;   [:div {:style {:width "100px"
;;                  :height "100px"
;;                  :background-color color
;;                  :transform (if active? "scale(1.1)" "scale(1)")
;;                  :transition "all 0.2s ease"}
;;          :on-mouse-enter (fn [e] ...)
;;          :on-mouse-leave (fn [e] ...)}])
;; ```

;; ### Score Display

;; Dynamic score with color changes:

;; ```clojure
;; (defn score-display [& {:keys [playing? game-over? score]}]
;;   [:h3 {:style {:color (cond
;;                          game-over? "#ff4444"
;;                          playing? "#4caf50"
;;                          :else "#333")}}
;;    (cond
;;      game-over? (str "Game Over! Score: " score)
;;      playing? (str "Level: " (inc score))
;;      :else "Press Start to Play")])
;; ```

;; ## Learning Points

;; This project teaches several important concepts:

;; ### 1. Functional Game Development

;; - Pure functions for game logic
;; - Immutable state management
;; - Reactive UI updates with atoms

;; ### 2. Browser API Integration

;; - Web Audio API for dynamic sounds
;; - CSS transitions for animations
;; - Event handling for user input

;; ### 3. ClojureScript Patterns

;; - Keyword arguments for readability
;; - Component composition
;; - State atom patterns

;; ## Try the Game!

;; The complete game is embedded below. Click "Start Game" to begin!

^:kindly/hide-code
(kind/hiccup
 [:div {:style {:margin "2rem 0"
                :padding "2rem"
                :border "2px solid #e9ecef"
                :border-radius "8px"
                :background-color "#f8f9fa"}}
  [:div#memory-game-root {:style {:min-height "600px"}}]
  [:script {:type "application/x-scittle"
            :src "memory_game.cljs"}]])

;; ## Extending the Game

;; Here are some ideas to enhance the game:

;; ### 1. Difficulty Levels

;; - Easy: Slower sequence display
;; - Medium: Current speed
;; - Hard: Faster sequence, more colors

;; ### 2. Visual Themes

;; - Different color schemes
;; - Shape variations
;; - Animation styles

;; ### 3. Sound Options

;; - Different instrument sounds
;; - Musical scales
;; - Volume control

;; ### 4. Game Modes

;; - Reverse mode (play sequence backwards)
;; - Speed mode (time limits)
;; - Pattern mode (specific patterns to memorize)

;; ### 5. Persistence

;; - Local storage for high scores
;; - Player profiles
;; - Statistics tracking

;; ## Key Takeaways

;; Building games with Scittle and ClojureScript offers:

;; 1. Rapid Prototyping - No build setup means faster iteration
;; 2. Clean Code - Functional programming keeps logic simple
;; 3. Browser-Native - Runs anywhere without servers
;; 4. Educational - Perfect for learning and teaching
;; 5. Fun! - Immediate feedback makes development enjoyable

;; ## Resources and Links

;; - [Scittle Documentation](https://github.com/babashka/scittle)
;; - [Reagent Guide](https://reagent-project.github.io/)
;; - [Web Audio API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API)
;; - [ClojureScript Reference](https://clojurescript.org/)

;; ## Conclusion

;; This memory game demonstrates that you don't need complex build tools to create interactive browser applications. With Scittle, ClojureScript, and Reagent, you can build engaging games that run directly in the browser.

;; The combination of functional programming, reactive state management, and browser APIs creates a powerful platform for game development. Whether you're learning ClojureScript, teaching programming concepts, or just having fun, this approach offers a refreshing alternative to traditional web development.

;; Happy coding, and see how high you can score! 🎮

;; ---

;; *Want to see more browser-native ClojureScript projects? Check out my other articles on [ClojureCivitas](https://clojurecivitas.github.io/) where we explore the boundaries of what's possible without build tools!*
