^:kindly/hide-code
^{:clay {:title  "The Sandwich Approach to ClojureScript Development"
         :quarto {:author      :timothypratley
                  :description "A lightweight way to cook with Clay and Scittle"
                  :type        :post
                  :draft       true
                  :date        "2025-08-13"
                  :category    :clay
                  :tags        [:clay :workflow :scittle :reagent]}}}
(ns scicloj.clay.uncompiled-clojurescript
  (:require [scicloj.kindly.v4.kind :as kind]))

;; Imagine you're making breakfast for your significant other one morning,
;; you just cracked the eggs into a frypan and they call out "poached please".
;; What do you do?
;; No need to start over, just add some water.
;; That's the spirit of hot reload; keep your state and adjust the functions.

;; This kind of fluid adaptation is exactly what we want in our development workflow.
;; Let's see how to cook up that experience with two key ingredients.

;; ## The Main Ingredient: Scittle

;; [Scittle](https://github.com/babashka/scittle) by the amazing [borkdude](https://github.com/borkdude) is a ClojureScript interpreter that runs in the browser.
;; No build step, no config, just static files. That makes it perfect to include in Clay notebooks like this one.

;; When you're hungry, do you always cook a gourmet meal?
;; Sometimes you just want to make a quick sandwich—grab what you need,
;; slap it together, and start eating. No prep work, no cleanup.
;; That's Scittle: quick, simple, satisfying ClojureScript without the ceremony.
;; Sometimes you don't need the full kitchen setup with build tools and configurations.

;; Clay lets us interleave plain Hiccup with Reagent/Scittle components:

(kind/hiccup
 [:div [:strong "Hello from Hiccup"]
  ['(fn []
      [:em "Hello from Scittle/Reagent"])]])

;; But because Clay serves HTML, re-evaluating this namespace normally reloads the whole page.
;; Wouldn’t it be great to get a Figwheel/shadow-cljs style experience where code updates in place?

;; ::: {.callout-note}
;; Hot Reloading injects only the modified code into the running application without restarting it entirely.
;; This allows you to preserve state and treat the browser like a REPL.
;; :::

;; ## The Inspiration: Hot Reload Scittle

;; [Chris McCormick](https://github.com/chr15m) showed us something clever
;; with [cljs-josh](https://github.com/chr15m/cljs-josh): Scittle code can be hot-reloaded. Here's his demo:

^:kind/video ^:kindly/hide-code
{:youtube-id   "4tbjE0_W-58"
 :iframe-width "100%"}

;; We'll take that idea but do it the Clay way,
;; using Clay's live reload to manage our pages.

;; ## Clay Can Hot Reload Scittle

;; Here’s the code in our ClojureScript file:

;; **uncompiled.cljs**:
^:kindly/hide-code
(kind/code (slurp "src/scicloj/clay/uncompiled.cljs"))

;; We need a target div for `uncompiled.cljs` to render into, and we load it via Scittle:

(kind/hiccup
 [:div#app
  [:script {:type "application/x-scittle"
            :src "uncompiled.cljs"}]])

;; ## Turn Up the Heat

;; Time to get cooking! Clay needs `:live-reload true` to simmer.
;; You can fire it up from the command line, via [editor integration](https://scicloj.github.io/clay/#setup),
;; or drop this in your REPL:

;; ```clojure
;; (require '[scicloj.clay.v2.snippets :as snippets])
;; (snippets/watch! {})
;; ```

;; Now whenever you save a `.cljs` file, the change is injected into the current Clay page without a full reload.

;; If you prefer editor integration, bind a key to call
;; `scicloj.clay.v2.server/scittle-eval-string!` on the current form.

;; ::: {.callout-tip}
;; To take full advantage of hot reload with Reagent, use `defonce` for app state.
;; This preserves atoms/ratoms across code swaps.
;; :::

;; ## A Taste Test

;; Let's walk through a small example to see hot reload in action.
;; I recommend copying these snippets into the `uncompiled.cljs` and saving as you go.
;; First, we'll set up our ingredients (app state):

;; ```clojure
;; (ns my-app.core
;;   (:require [reagent.core :as r]))
;;
;; (defonce app-state    ; <- defonce preserves state during reload
;;   (r/atom {:style "scrambled"
;;            :eggs 2}))
;; ```

;; Next, we'll create a simple component to display our breakfast order:

;; ```clojure
;; (defn breakfast-order []
;;   [:div
;;    [:h3 "Breakfast Order"]
;;    [:p "Style: " (:style @app-state)]
;;    [:p "Eggs: " (:eggs @app-state)]])
;; ```

;; We can mount our new component on the `app` div.

;; ```clojure
;; (rdom/render
;;   [breakfast-order]
;;   (js/document.getElementById "app"))
;; ```

;; Just like our opening story, imagine the order changes mid-cook.
;; We can update our component without losing the current state:

;; ```clojure
;; (defn breakfast-order []
;;   [:div
;;    [:h3 "Breakfast Order"]
;;    [:p "Style: " [:em (:style @app-state)]]  ; <- added emphasis
;;    [:p "Eggs: " (:eggs @app-state)]
;;    [:small "Water added for poaching..."]])   ; <- new status line
;; ```

;; Save the file, and voilà! The display updates while preserving the
;; existing order details in app-state. No need to start over or lose
;; your place in the cooking process.

;; ## Why Go Light?

;; A Michelin-star kitchen is a marvel of efficiency.
;; Sous chefs prepping ingredients,
;; line cooks at their stations, everything precisely mise en place.
;; That's your typical ClojureScript setup: build tools, asset compilation,
;; development servers, and careful configuration.
;;
;; But sometimes you just want to slap some cold cuts and cheese in a sandwich and start munching.
;; That's Scittle: quick, simple, and satisfying.
;; No waiting for the kitchen to warm up, no cleanup afterward.
;; Just write some code and see it work.

;; ## Chef's Notes

;; Clay blends the traditional with the experimental.
;; Write a namespace, get a webpage.
;; Add some Scittle, get interactive components.

;; The magic happens in how Clay handles changes:

;; - For your narrative and page structure in Clojure live reload refreshes the whole view
;; - For your Scittle components hot reload updates just the code, keeping state alive

;; This is especially sweet when you're cooking up interactive elements like mini-games
;; with matter.js. Tweak physics parameters or game logic, and watch them take effect
;; instantly. Your game state, scores, and position all preserved.
;;
;; Remember how we started with those poached eggs?
;; Maybe they were destined for a sandwich all along.
;; That's the beauty of this approach: start simple,
;; stay flexible, and build exactly what you need.
;; Clay lets you shape your story with markdown, spice it up with interactive widgets,
;; and adjust the ingredients.
;; That's the kind of flow that keeps creative coding delicious.
