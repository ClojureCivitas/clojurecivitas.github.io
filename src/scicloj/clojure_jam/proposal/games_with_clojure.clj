(ns scicloj.clojure-jam.proposal.games-with-clojure
  {:clay {:title "Making Fun Games with Clojure + Raylib"
          :quarto {:author [:burinc]
                   :description "A talk about porting classic games to Clojure using Raylib via FFI"
                   :draft true
                   :type :post
                   :date "2026-02-06"
                   :category :collaboration
                   :tags [:clojure :gamedev :raylib :ffi :creative-coding :games]}}}
  (:require
   [clojure.string :as str]))

;; # Making Fun Games with Clojure + Raylib
;;
;; **Format:** Talk (30-45 min)
;;
;; **Summary:**
;; Can you build real, playable games in Clojure?
;; Yes you can — and it's more fun than you'd expect!
;;
;; In this talk, I'll share my experience porting classic games
;; (Snake, Floppy Bird, a retro 3D maze, and more) from C to Clojure
;; using Raylib — a simple and elegant C game library — called directly
;; from Clojure via JDK 22's Foreign Function API (Project Panama).
;;
;; This work builds on the excellent
;; [raylib-clojure-playground](https://github.com/ertugrulcetin/raylib-clojure-playground)
;; by Ertuğrul Çetin, which showed how to use coffi to bridge
;; Clojure and Raylib's C API.
;;
;; I extended the project with a large collection of ported examples
;; — spanning games, 3D demos, audio, and more — to show
;; that Clojure is a surprisingly great language for game development.

;; ---

;; ## What I'll Cover

;; ### 1. Why Clojure for Games?
;;
;; - Immutable game state in an atom — the game loop is just
;;   `swap!` + pure functions
;; - REPL-driven development: tweak a running game live!
;; - Clojure's data-oriented design maps naturally to game entities

;; ### 2. The Architecture
;;
;; - How Raylib's C API is called from Clojure via coffi + JDK Panama
;; - The game loop pattern: init → tick → draw → cleanup
;; - Game state as a single Clojure map in an atom

;; ### 3. Live Demo: Porting a Game
;;
;; - Walk through porting a classic game from C → Clojure
;; - Show the side-by-side: C code vs idiomatic Clojure
;; - Highlight what becomes simpler (and what's tricky)

;; ### 4. The Fun Part: REPL-Driven Game Dev
;;
;; - Connect to a running game via nREPL
;; - Modify game state live: spawn enemies, change physics, resize things
;; - This is the superpower that makes Clojure game dev uniquely fun

;; ### 5. Gallery: A Buffet of Examples
;;
;; - Quick tour of what's possible: from "Hello World" to
;;   survival action games, from bouncing balls to strange attractors,
;;   from 2D sprites to first-person 3D mazes
;; - How many? Come to the talk and find out 😉
;; - All runnable with a single `bb <game-name>` command

;; ---

;; ## Who Is This Talk For?
;;
;; - Clojure developers curious about creative coding and games
;; - Game developers curious about functional programming
;; - Anyone who thinks "Clojure + games" sounds like fun
;;
;; No prior game development experience required!

;; ## Key Takeaway
;;
;; Clojure's REPL, immutable data, and functional style make it a
;; surprisingly joyful language for building games.
;; You don't need Unity or Unreal — sometimes all you need is
;; a REPL, an atom, and a few hundred lines of Clojure.

;; ---

;; ## Links
;;
;; - Original: [ertugrulcetin/raylib-clojure-playground](https://github.com/ertugrulcetin/raylib-clojure-playground)
;; - Raylib: [raylib.com](https://www.raylib.com/)
