^:kindly/hide-code
(ns scicloj.clay.webserver.datastar
  {:kindly/servable true
   :clay {:title  "Serving webapps from your REPL"
          :quarto {:author      :timothypratley
                   :description "Using Clay's new webserver features and Datastar to build a chart with realtime server-push updates"
                   :image       "datastar.png"
                   :reference-location :margin
                   :citation-location :margin
                   :type        :post
                   :date        "2026-01-10"
                   :category    :clay
                   :tags        [:clay :workflow]
                   :keywords    [:clay :datastar]}}}
  (:require [hiccup.core :as hiccup]))

;; Clay converts a Clojure namespace into an HTML page and runs a web server to display it.
;; The Clay server can do more than just serve static pages: we can define
;; endpoints that handle requests and dynamically render content.
;;
;; This page demonstrates a server-driven web application using Datastar.
;; The server manages both the rendering logic and the application state, while the browser handles
;; user interactions and displays the results.
;;
;; > "The cosmos is within us. We are made of star-stuff. We are a way for the universe to know itself."
;; >
;; > — Carl Sagan
;;
;; All the code is in this namespace.
;; The server is in your REPL.

;; ## First light from the REPL

;; I'll send this namespace to Clay by calling the "Clay: Make File Quarto" command in Calva.
;; If you want to follow along, you can clone this repo and do the same.
;; (Let's start stargazing.)
;;
;; Or if you prefer to watch first, here's a screencast:

^:kind/video ^:kindly/hide-code
{:youtube-id   "k98QI-EOHJA"
 :iframe-width "100%"}

;; ## The constellation of reactions

;; Here's the application: a reaction counter with emoji buttons.
;; When we click the buttons, the chart updates in real time, showing the count for each emoji.
;; Notice how if we open this page in multiple browser tabs, the counts update across all tabs
;; simultaneously—this is the server pushing updates to all connected clients.

^:kind/hiccup
[:div.d-flex.justify-content-center.my-5
 [:div.card.shadow {:style {:width "260px"}}
  [:div.card-body
   ;; "reactions" is a target for replacement
   [:div {:id "reactions"
          :data-init "@get('/kindly-compute/scicloj.clay.webserver.datastar/react-html');"}]
   ;; buttons that call the backend with an emoji
   [:div.d-flex.gap-2.mt-3
    [:button.btn.btn-outline-primary {:data-on:click "@post('/kindly-compute/scicloj.clay.webserver.datastar/react-html?emoji=👍');"} "👍"]
    [:button.btn.btn-outline-primary {:data-on:click "@post('/kindly-compute/scicloj.clay.webserver.datastar/react-html?emoji=🎉');"} "🎉"]
    [:button.btn.btn-outline-primary {:data-on:click "@post('/kindly-compute/scicloj.clay.webserver.datastar/react-html?emoji=❤️');"} "❤️"]]]]]

;; ::: {.callout-note}
;; The buttons won't do anything unless you are running the Clay server.
;; To do that, clone this repo and run [Clay make](https://scicloj.github.io/clay/#setup) on this file.
;; :::

;; When we click a button, here's what happens:

;; 1. The browser makes an HTTP request to the server
;; 2. The server updates its state and renders new HTML
;; 3. Datastar receives the HTML and patches it into the page
;;
;; Datastar is a JavaScript library that intelligently updates the DOM without full page reloads.
;; Rather than writing JavaScript callbacks, Datastar behaviors are simply data attributes in HTML.
;; The `:data-on:click` attribute on our buttons tells Datastar which endpoint to call.
;;
;; This approach is ideal for Clay, where we naturally express UI as Hiccup data structures.
;; The server owns both the state and the rendering logic, so we can reason about the entire
;; application in Clojure, but still have interactive, responsive UI on the browser.

^:kind/hiccup
[:script
 {:src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.7/bundles/datastar.js"
  :type "module"}]

;; Datastar is a small JavaScript library that adds interactivity to our HTML.
;; It watches for `data-*` attributes in the DOM and responds to user interactions by making HTTP requests to our server.

;; ## State at the stellar core

;; We keep track of reactions in an atom:

(defonce reactions
  (atom {"👍" 8, "🎉" 4, "❤️" 2}))

;; This state lives on the server in the REPL process, not in the browser.
;; It's the central source of truth.[^state]
;; We can modify it from the REPL to test or debug the application.
;; Clients request this state from the server.
;;
;; [^state]: **State** is the current data that defines how the application looks and behaves.

;; ## Charting star reactions

;; (Now to illuminate the reactions.)
;;
;; Let's create a hiccup representation of the chart:

(defn chart [rs]
  (let [maxv (max 1 (apply max (vals rs)))
        bar (fn [[label n]]
              [:div {:style {:display "flex" :alignItems "center" :gap "0.5rem"}}
               [:span label]
               [:div {:style {:background "#eee" :width "150px" :height "8px"}}
                [:div {:style {:background "#4ECDC4"
                               :height "8px"
                               :width (str (int (* 100 (/ n maxv))) "%")}}]]
               [:span n]])]
    [:div {:id "reactions"}
     [:h3 "Reactions"]
     (into [:div {:style {:display "grid" :gap "0.25rem"}}]
           (map bar rs))]))

;; The top-level `div` has `id=\"reactions\"`.
;; This is crucial: Datastar uses element IDs to match the updated HTML with the existing DOM element, then morphs the DOM in place.[^morph]
;; When Datastar receives new HTML, it looks for matching IDs and updates the content gracefully.
;;
;; [^morph]: **Morph** means to smoothly transform the DOM structure without destroying and recreating elements, preserving animations and focus.

;; ## Signals from the cosmos

;; We need the Clay server to listen for the Datastar request:

(defn ^:kindly/servable react-html [{:keys [emoji]}]
  (-> (if emoji
        (swap! reactions update emoji (fnil inc 0))
        @reactions)
      (chart)
      (hiccup/html)))

;; The `:kindly/servable` metadata marks this function as a remote endpoint that Clay will expose.[^servable-feature]
;; The function is called with the emoji parameter from the URL, updates the shared state atom,
;; and returns the new chart as HTML.
;; (Each click is a signal received.)
;;
;; [^servable-feature]: See the [Clay documentation](https://scicloj.github.io/clay/clay_book.webserver.html#servable-functions) for details on `:kindly/servable` endpoints.

;; ::: {.callout-note}
;; **Naming convention matters:** The function name ends in `html`.
;; Clay uses this naming convention to determine whether to serve the result as HTML (not JSON).
;; This keeps the response as a raw HTML fragment that Datastar can inject directly into the page.
;; :::
;;
;; When the browser receives this HTML, Datastar finds the element with `id="reactions"` in both
;; the old and new HTML, and morphs the DOM in-place.
;; This means animations and focus are preserved rather than destroying and recreating the entire element.

;; ## Broadcasting to the cosmos

;; So far the client pulls data when a button is clicked.
;; What if we want the server to push updates to all connected clients whenever the state changes?
;; (Broadcasting to all the stars.)[^sse]
;; For that we need a channel from server to client: Server Sent Events (SSE).
;; The [Datastar Clojure SDK](https://github.com/starfederation/datastar-clojure)
;; provides helpers to set up and manage these push connections.
;;
;; [^sse]: **Server Sent Events (SSE)** is a standard for opening a one-way channel from server to client, perfect for pushing real-time updates.

;; We track all open SSE connections in a set.
;; When a client connects or disconnects, we add or remove it from this set.

(defonce *ds-connections
  (atom #{}))

;; This handler creates an SSE connection.
;; The `:kindly/handler` metadata tells Clay to expose this function as an HTTP endpoint that takes a request and returns a response.[^handler-feature]
;; The `->sse-response` helper returns the proper SSE response structure.
;; When a client connects, we store its SSE generator.
;; When it closes, we remove it.
;;
;; [^handler-feature]: This `:kindly/handler` feature is available in Clay 2.0.5 and later. See the [Clay documentation](https://scicloj.github.io/clay/clay_book.webserver.html#handler-endpoints) for details.

(require '[starfederation.datastar.clojure.adapter.http-kit :as d*a])

(defn ^:kindly/handler datastar [req]
  (d*a/->sse-response
   req
   {d*a/on-open (fn [sse-gen] (swap! *ds-connections conj sse-gen))
    d*a/on-close (fn [sse-gen _status] (swap! *ds-connections disj sse-gen))}))

;; Broadcast sends HTML to all connected clients.
;; Datastar on the browser side will receive these patches and update the DOM.

(require '[starfederation.datastar.clojure.api :as d*])

(defn broadcast-elements! [elements]
  (doseq [c @*ds-connections]
    (d*/patch-elements! c elements {:id "reactions"})))

;; Now we create a watch callback that will broadcast the chart whenever the state changes:

(defn broadcast-chart! [_k _r _a b]
  (broadcast-elements! (hiccup/html (chart b))))

;; Attach a watch to the reactions atom.
;; Whenever the atom changes, this callback fires, renders the new chart as HTML, and broadcasts it to all connected clients.

(defonce watch-reactions
  (add-watch reactions :chart #'broadcast-chart!))

;; Clojure watches are a built-in mechanism for reacting to state changes.[^watch]
;; They take a reference type (atom, ref, agent, etc.) and a callback function that fires
;; whenever that reference is modified.
;; This is how we push updates to all clients automatically.
;;
;; [^watch]: **Watch** is a Clojure mechanism that lets you react to changes in a reference type by calling a function whenever it changes.

;; For example, we can test this from the REPL:

(comment
  (swap! reactions update "👍" inc))

;; When you evaluate this, the watch callback fires, broadcasts the updated chart as HTML, and all open browser windows receive and display the updated chart immediately.
;; This demonstrates the full loop: REPL state change → watch callback → broadcast to clients → DOM updates in real time.

;; Now our page should start listening to the SSE stream:

^:kind/hiccup
[:div {:data-init "@get('/kindly-compute/scicloj.clay.webserver.datastar/datastar')"}]

;; The `data-init` field tells Datastar to connect to the Server Sent Events handler,
;; and listen for subsequent messages.
;; SSE (Server Sent Events) is ideal here because we only need one-way communication from server to clients.
;; Unlike WebSockets, SSE uses standard HTTP and requires minimal setup.
;; Unlike polling, it's efficient—the server pushes updates only when the state changes.

;; Now when we open multiple pages and click in one,
;; we can see the data update in all the pages.
;; (A cosmos of observers.)

;; ## Reflecting on the skies

;; All the code for this HTML page is written in Clojure.
;; The state management, rendering, and interactivity lives in your REPL.
;;
;; This style of web development is fun and productive.
;; Clay makes it fast to prototype rich applications.
;; Datastar avoids client-side state management and event handling.
;; (One star, many observers.)
;; You reason about the application in one language and get interactive,
;; responsive UX without touching JavaScript.
;; (And so we return to the stars.)
;;
;; > "For small creatures such as we, the vastness is bearable only through love."
;; >
;; > — Carl Sagan
;;
;; If you love Clojure, I think you'll love Clay's new server features.
