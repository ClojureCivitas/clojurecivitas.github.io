^:kindly/hide-code
(ns scicloj.clay.ideas.uije3
  {:clay {:title          "A Shared UI Convention for Clojure (Part 3)"
          :quarto         {:author      [:timothypratley]
                           :description "Bind controls to state keys and declare state updates in Hiccup for the next render."
                           :abstract    "This installment implements a browser-side adapter in ClojureScript. It maps named inputs, button events, and Hiccup state updates onto Replicant, demonstrating how the shared UI convention can be used without a server."
                           :date        "2026-11-16"
                           :type        :post
                           :category    :clay
                           :tags        [:clay :dashboard :layout]}}
   :kindly/options {:static true}}
  (:require [civitas.why.village.color :as color]
            [clojure.string :as str]))

;; Expressing a UI as a function of state is a familiar and useful pattern.
;; But it doesn't prescribe how controls connect to state, how events are
;; represented, or how changes make it into the next render.
;; Frameworks such as Datastar, Replicant, and Reagent offer different mental
;; models and APIs for these interactions. Moving a UI between them requires
;; rethinking and rewriting the event wiring and state management, especially
;; when moving between server and browser rendering.
;;
;; What if we could describe those interactions using a shared convention,
;; leaving an adapter to translate them for each framework?
;;
;; This is part 3 of the Uije experiments
;; (see [uije2](uije2.html) for the server-side Datastar version).
;;
;; ::: {.callout-tip title="Main idea: Bind values to state and accept updates in the hiccup result"}
;; An input's `:name` identifies its state key; a button's `:name` identifies its transient click event.
;; A Hiccup element's `:state` can declare changes to carry into the next render.
;; An adapter connects these declarations to the framework's event and state mechanisms,
;; allowing the same UI convention to work across architectures.
;; :::

;; ## Example: greeting
;;
;; A tiny example with named inputs and a button:

^:kindly/hide-code
(defn code-example [filename]
  ^:kind/md ^:kindly/hide-code
  [(str/join \newline
             [(format "```{.clojure filename=\"%s\"}" filename)
              (slurp (str "src/scicloj/clay/ideas/" filename))
              "```"])])

^:kind/hiccup
[:div {:id "greet"} "Loading..."]

;; Try entering two names and clicking **Swap**. Each input's `:name` binds it
;; to its corresponding state key. The button's `:name` becomes the transient
;; `:click` event; `greet-event` uses it to swap the values, and `greet-ui`
;; returns the updated state with its Hiccup tree.

;; Here is the ClojureScript code for the greet example app:

(code-example "uije3_example.cljs")

;; For example, with Alice and Bob entered, clicking **Swap** passes
;; `{:a "Alice" :b "Bob" :click :c}` to `greet-event`.
;; It swaps `:a` and `:b`, and `greet-ui` includes the resulting state in the
;; returned Hiccup's `:state` attribute. The adapter carries the swapped values
;; forward and consumes `:click`, so the event is not repeated on the next render.

;; ::: {.callout-note title="Separating state handling from rendering"}
;; In the [real-world-data](https://clojurians.zulipchat.com/#narrow/channel/315077-real-world-data) meeting,
;; a concern about the earlier Datastar example
;; was that state handling and rendering were not separated.
;; Here, `greet` composes `greet-event`, which computes the updated state,
;; with `greet-ui`, which renders it and includes it in the returned Hiccup.
;; The convention supports this separation, but also allows a single function
;; to handle both when that is more convenient.
;; :::

;; ## The convention
;;
;; - **Named values:** an input's `:name` binds its value to a state key.
;; - **Named events:** a button's `:name` identifies a transient event
;;   (for example a `:click`).
;;
;; The UI describes these bindings without spelling out framework-specific
;; event wiring. The adapter translates control interactions into state updates
;; and exposes transient events to the UI function.
;;
;; ## Describing state changes
;;
;; To declare a state change, put a map in a Hiccup element's `:state` attribute.
;; The adapter collects these maps throughout the tree and merges them into the
;; current state, in tree order, carrying the result into the next render.
;; The UI can therefore declare state changes in the tree it returns,
;; while the adapter handles framework-specific event wiring and carries updates
;; forward.
;;
;; Updates are partial: keys not mentioned are left unchanged.
;; To reset a key, explicitly associate its new value.
;; Transient entries such as `:click` are consumed after rendering,
;; even if they appear in a `:state` patch.

;; The earlier Datastar example in [uije2](uije2.html) uses `:data-signals`
;; for updates. This prototype uses `:state` instead, so the convention does
;; not overwrite a valid Datastar attribute. The two examples explore the same
;; idea, but the Datastar adapter would need to move `:state` to `:data-signals`.

;; Everything in this Replicant example runs in the browser, with no server required (unlike uije2).

;; ## Example: sine wave
;;
;; A richer UI with buttons and sliders:

^:kind/hiccup
[:div {:id "sine-wave"} "Loading..."]

;; The ClojureScript code:

(code-example "uije3_sine_wave.cljs")

;; Each slider's `:name` binds it to a state key, so changing a slider updates
;; the value used to draw the wave. **Reset** sends a one-shot event; the UI
;; responds with the default state, which is carried into the next render.
;; The adapter reads range and number inputs as numbers when valid; an empty or
;; invalid number input retains its string value. Text inputs remain strings.
;;
;; ## Implementation notes
;;
;; The translation to Replicant lives in [uije3.cljs](uije3.cljs).
;; It maps named controls and `:state` declarations onto Replicant's event
;; and rendering mechanisms; another adapter could connect the same convention
;; to a different framework or architecture.

;; We need to have replicant loaded in, and to load the cljs files.

^:kind/hiccup
[:div
 [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/dev/scittle.js"}]
 [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/dev/scittle.cljs-devtools.js"}]
 [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/dev/scittle.replicant.js"}]
 [:script {:src "uije3.cljs" :type "application/x-scittle"}]
 [:script {:src "uije3_example.cljs" :type "application/x-scittle"}]
 [:script {:src "uije3_sine_wave.cljs" :type "application/x-scittle"}]]

;; It would be useful to provide a Clojure helper that sets up the target div and the script statements.
;; For now, we are loading them like this to show the concept works.

;;
;; ## What this prototype shows
;;
;; This prototype shows that the convention works in the browser with Replicant.
;; Together with the earlier Datastar example, it explores the same idea in
;; client- and server-rendered settings: a UI declares its bindings and state
;; changes, while an adapter handles the framework-specific wiring.

^:kind/hiccup ^:kindly/hide-code
[:svg {:style {:width "100%", :height :auto}
       :viewBox [0 0 400 200]
       :role "img"
       :aria-label "A circle between square brackets in Clojure colors"}
 [:rect {:fill (color/scicloj-palette 1) :width 200 :height 200}]
 [:rect {:fill (color/scicloj-palette 2) :x 200 :width 200 :height 200}]
 [:path {:d "M 115 45 H 75 V 155 H 115"
         :fill "none" :stroke (color/scicloj-palette 3) :stroke-width 16}]
 [:path {:d "M 285 45 H 325 V 155 H 285"
         :fill "none" :stroke (color/scicloj-palette 4) :stroke-width 16}]
 [:circle {:fill (color/scicloj-palette 6)
           :stroke (color/scicloj-palette 2) :stroke-width 4
           :r 55 :cx 200 :cy 100}]]
