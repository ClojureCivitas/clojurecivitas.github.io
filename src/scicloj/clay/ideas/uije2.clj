^:kindly/hide-code
(ns scicloj.clay.ideas.uije2
  {:clay {:title          "A Shiny-like dashboard prototype for Clojure"
          :page-layout    :full
          :quarto         {:author      [:timothypratley]
                           :description "Server-rendered reactive dashboards in Clojure with Hiccup and Datastar."
                           :abstract    "This post explores a custom Clay kind that turns Hiccup into a reactive UI. A dashboard using penguin data shows how Datastar connects browser controls to Clojure state with little extra code."
                           :date        "2026-10-29"
                           :type        :post
                           :category    :clay
                           :tags        [:clay :dashboard :layout]}}
   :kindly/options {:static true}}
  (:require [charred.api :as json]
            [civitas.why.village.color :as color]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [ring.util.codec :as codec]
            [scicloj.clay.v2.prepare :as prepare]
            [scicloj.clay.v2.server :as server]
            [scicloj.kindly-advice.v1.api :as kindly-advice]
            [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.metamorph.ml.rdatasets :as rdatasets]
            [scicloj.plotje.api :as pj]
            [tablecloth.api :as tc]))

;; Hiccup is a concise way to describe a graphical user interface as a tree.

(let [size 400
      size2 (/ size 2.0)
      size25 (/ size 2.5)
      size3 (/ size 3.0)]
  ^:kind/hiccup
  [:svg {:style {:width "100%", :height :auto}
         :viewBox [0 0 size size]
         :role "img"
         :aria-label "yin-yang"}
   [:rect {:fill (color/palette 1) :width size2 :height size}]
   [:rect {:fill (color/palette 4) :x size2 :width size2 :height size}]
   [:rect {:fill (color/palette 2) :x (- size2 size25) :y (- size2 size25) :width (* size25 2) :height size25}]
   [:rect {:fill (color/palette 5) :x (- size2 size25) :y size2 :width (* size25 2) :height size25}]
   [:circle {:fill (color/palette 3) :r size3 :cx size2 :cy size2}]])

;; Interactive UIs also need a way to connect controls to state and events.
;; **Shiny**, an R framework for building interactive web applications, does this through reactive programming.
;; When inputs change, dependent outputs update automatically.
;; What if we could achieve something similar in Clojure with a small set of conventions?
;; The goal is a similar server-side dashboard experience, not a reproduction of Shiny's reactive model.

;; ::: {.callout-note icon="💡"}
;; ## Implicit bindings

;; When a control changes, Datastar sends the current signal values to Clay.
;; A button can also send a one-request event.
;; The UI function reads that state and returns Hiccup with controls bound to state keys.
;; :::

(defn greet [{:keys [a b]}]
  [:col
   [:strong (str "Hello: " a ", " b)]
   [:row
    [:input {:name :a}]
    [:input {:name :b}]]])

;; A UI is a function of state.
;; The `greet` function takes `:a` and `:b` from state to make a greeting.
;; The two inputs use those same keys in their `:name` attributes.
;; This function can be used to set up a reactive UI.
;; Clojure renders the HTML on the server; Datastar updates the browser DOM.
;; (We'll see what that looks like later in the notebook...)

;; Let's make a more compelling UI:

(def penguins
  (tc/drop-missing (rdatasets/palmerpenguins-penguins)))

(def column-names
  (mapv name (tc/column-names penguins)))

(defn penguins-ui [{:keys [x y color-by click]
                    :or {x (get column-names 6)
                         y (get column-names 3)
                         color-by (get column-names 1)}
                    :as state}]
  (let [swap (= click :swap-axis)
        [x y] (if swap [y x] [x y])
        signals (cond (empty? state) {:x x :y y :color-by color-by}
                      swap {:x x :y y})]
    [:div.card (when signals {:data-signals signals})
     [:div.card-header "Penguins"]
     [:style ".plotje-plot {width:100%; height:auto}"]
     [:div.card-body
      [:div.container
       [:row
        ^:kind/md ["Observations of *three penguin species* in the Palmer Archipelago, Antarctica.
                     The data was collected by Dr. Kristen Gorman at the Palmer Station Long Term Ecological Research (LTER) network."]
        [:img {:src "https://allisonhorst.github.io/palmerpenguins/reference/figures/lter_penguins.png"
               :alt "Illustration of penguins from the Palmer Archipelago"
               :style {:width "200px"
                       :height :auto}}]]]
      [:row
       [:select {:name :y, :label "y-axis", :options column-names}]
       [:col
        (try (-> (pj/pose penguins (keyword x) (keyword y)
                          {:color (when (not (str/blank? color-by))
                                    (keyword color-by))})
                 (pj/plot))
             (catch Exception ex ex))
        [:select {:name :x, :label "x-axis", :options column-names}]
        [:button {:name :swap-axis} "Swap x/y"]]
       [:col
        [:select {:name :color-by :label "color-by" :options (cons "" column-names)}]]]]]))


;; Treating UI components as functions is familiar.
;; The difference here is that browser state, when changed, is implicitly passed to a server function, which returns the next UI.
;; This hides most of the request-and-response plumbing.

;; Buttons can send one-off events as well as controls sending state.
;; Clicking the swap button adds `:click` with the value `:swap-axis` to that request only.
;; The UI function handles the event during that render and returns the chart with its axes swapped.
;; No separate per-button handler is needed; the new axis choices become UI state.

;; ```
;; A control changes
;; → the browser sends state (and any one-off event)
;; → the Clojure function returns Hiccup
;; → Datastar updates the page.
;; ```

;; ## Implementation

;; How can we turn a function into a UI?
;; The `uije` function is defined in this namespace you are reading right now,
;; we'll get to the details of what it does soon.
;; In the future we might create a library with a more complete implementation,
;; and possibly include it with Clay.

;; Datastar is a JavaScript library that updates the DOM from server responses.
;; The server sends HTML, and Datastar morphs the existing UI to match it.
;; The first thing we will need to do is include the JavaScript, and set up some state change calls to an endpoint we will create.

(defn datastar-root [id endpoint]
  [:div {:id id
         :data-on:input (str "@post('" endpoint "')")
         :data-on:change (str "@post('" endpoint "')")}
   [:script {:type "module"
             :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.4/bundles/datastar.js"}]])

;; We add the Datastar syntax during rendering so that the UI function doesn't need it.
;; To enhance the Hiccup, we walk the tree:

(defn branch? [x]
  (and (vector? x)
       (keyword? (first x))))

(defn enhance-attrs [attrs]
  (cond-> attrs
    (contains? attrs :data-signals)
    (update :data-signals json/write-json-str)))

(defmulti enhance-tag (fn [tag attrs endpoint] tag))
(defmethod enhance-tag :default [tag attrs endpoint]
  (if attrs
    [tag attrs]
    [tag]))

(defn enhance-hiccup [node endpoint]
  (if (branch? node)
    (let [[tag & [x & more :as xs]] node
          attrs (when (and (map? x)
                           (-> {:value x} kindly-advice/advise :meta-kind not))
                  (enhance-attrs x))
          children (if attrs more xs)]
      (-> (enhance-tag tag attrs endpoint)
          (into (map #(enhance-hiccup % endpoint) children))))
    node))

(defn clojurize [query-params]
  (-> query-params
      (update-keys keyword)
      (update-vals edn/read-string)))

;; ### The Main Entry Point

;; And here is the function to create a UI from a function.
;; It creates (or updates) an endpoint, enhances the Hiccup and adds the Datastar script.

(defn uije
  "Makes a UI from a var or function that accepts state, and returns hiccup."
  [ui-var]
  (let [id (or (some-> ui-var meta :name) "uije-app")
        endpoint (str "/uije/" id)
        ;; Wrap the UI to make it a datastar app
        ui-fn (fn [state]
                (-> (ui-var state)
                    (enhance-hiccup endpoint)
                    (->> (conj (datastar-root id endpoint)))
                    (kind/hiccup)))
        ;; Install a handler to listen to datastar posts which contain state,
        ;; re-renders the UI with that state,
        ;; Datastar will then morph the DOM to the new UI.
        endpoint-var (intern *ns* (symbol (str id "-endpoint"))
                             (fn [{:keys [request-method uri query-params body-params]}]
                               (when (= uri endpoint)
                                 {:status 200
                                  :headers {"Content-Type" "text/html; charset=utf-8"}
                                  :body (-> {:value (ui-fn (merge body-params (clojurize query-params)))}
                                            (prepare/hiccup 2)
                                            (:html)
                                            (str))})))]
    (server/install-handler! endpoint-var)
    ;; Initial render with no state, Clay will put this in the page.
    (ui-fn nil)))

;; ### Hiccup Enhancement

;; Enhancing allows custom properties to expand into larger hiccup blocks:

(defmethod enhance-tag :input [_ {:as attrs :keys [name label]} endpoint]
  (cond->> [:input.form-control (cond-> {}
                                  name (assoc :data-bind name)
                                  name (assoc :id name)
                                  true (merge (dissoc attrs :label)))]
    (and name label) (conj [:div.mb-3
                            [:label.form-label {:for name} label]])))

;; The important part here is that `:data-bind` was set to the name of the input.
;; This is a Datastar attribute called a signal.

(defmethod enhance-tag :select [_ {:as attrs :keys [name options label]} endpoint]
  (cond->> [:select.form-select (cond-> {}
                                  name (assoc :data-bind name)
                                  name (assoc :id name)
                                  true (merge (dissoc attrs :options :label)))
            (for [c options]
              (if (vector? c)
                [:option {:value (first c)} (second c)]
                [:option {:value c} c]))]
    (and name label) (conj [:div.mb-3
                            [:label.form-label {:for name} label]])))

;; We can provide options more compactly as a custom attribute.

;; For buttons, we wish to know that a click occurred,
;; rather than receiving a state.
;; Events are sent as query params (which are transient), rather than data-bind (which create state).

(defmethod enhance-tag :button [_ {:as attrs :keys [name]} endpoint]
  [:button (cond-> {}
             name (assoc :data-on:click (str "@post('" endpoint "?"
                                             (codec/form-encode {"click" name})
                                             "')"))
             true (merge attrs))])

;; We can add custom tags like `:row` and `:col`:

(defmethod enhance-tag :row [_ {:as attrs :keys [name]} endpoint]
  [:div (kindly/deep-merge {:style {:display :flex}} attrs)])

(defmethod enhance-tag :col [_ {:as attrs :keys [name]} endpoint]
  [:div (kindly/deep-merge {:style {:display :flex :flex-direction :column}} attrs)])

;; The main point of enhancement is that a simple, expressive UI DSL can expand into styled, interactive components.
;; One possibility is to render the same DSL through different backends, perhaps including
;; [Membrane](https://github.com/phronmophobic/membrane).
;; A separate possibility is to use Membrane's own UI model directly.
;; What would each approach require, and could they share any state and event conventions?
;; We'd like users not be required to learn web development to create a polished interface.
;; Instead, the basic representation should stay simple and allow additional enhancements.

;; ## The Result

;; ::: {.callout-warning}
;; The controls will only be responsive if you are running this notebook locally.
;; Running this notebook sets up the Clay server to listen to and respond to the client with UI updates.
;; The browser sends input changes to an endpoint hosted by Clay.
;; Clay renders the UI on the server, and Datastar applies the returned HTML in the browser.
;; To run this notebook, clone the [ClojureCivitas repository](https://github.com/ClojureCivitas/clojurecivitas.github.io),
;; and run Clay inside the `clojurecivitas.github.io` directory:
;;
;; ```sh
;; clojure -M:clay src/scicloj/clay/ideas/uije2.clj
;; ```
;;
;; Or call "Clay Make File" using your favourite [editor integration](https://scicloj.github.io/clay/#setup).
;; :::

(uije #'greet)

(uije #'penguins-ui)

;; ## Related approaches to reactive UIs

;; Many people have reported success with server-rendered architectures.
;; Their experience offers ideas we can draw on.
;; Here are some talks about server-rendered and server-driven UIs:

^:kindly/hide-code
(kind/table
 [[(kind/md "From Tomorrow Back to Yesterday: A Tale of Two Web Architectures (by David Yang).
            Contrasts traditional retained-mode state rendering with architectures that leverage Server-Sent Events (SSE) streams to dynamically wire up DOM events and handle partial updates directly from the backend.")
   (kind/video {:youtube-id "8W6Lr1hRgXo"})]
  [(kind/md "A ClojureScript Survival Kit (by David Nolen).
             Compares a data-heavy ledger application built using traditional React against one built using backend DOM morphing and web components.")
   (kind/video {:youtube-id "BeE00vGC36E"})]
  [(kind/md "Electric Clojure: Differential Dataflow for UI (by Dustin Getz).
             Write unified code that a specialized compiler automatically splits into client and server execution targets.")
   (kind/video {:youtube-id "ML8cFrWkWeg"})]
  [(kind/md "Datastar and Clojure, with Arne Brasseur.
             How to use Datastar, a lightweight frontend library, alongside Clojure and Datomic to build reactive web applications.")
   (kind/video {:youtube-id "sR0yrp2wDUU"})]])

;; ## Server-rendering is not for everyone

;; Sometimes it is useful to package a UI as a self-contained browser-side component.
;; For some applications, an entirely browser-side experience is a better fit.
;; Supporting that could be another goal.

;; Clay already works great with [Reagent](https://github.com/reagent-project/reagent)
;; and [Replicant](https://replicant.fun/) (via [Scittle Kitchen](https://timothypratley.github.io/scittle-kitchen/)).
;; It would be interesting to see whether opinionated state and event conventions could make them easier to use for small dashboard-style experiences.

;; State and event handling are key abstractions for Shiny-like dashboards.
;; Clojure and ClojureScript offer flexible ways to express them,
;; but how can we represent interactive behavior in a way that
;; can be preserved across rendering environments, such as web pages,
;; notebooks, and desktop applications?

;; ::: {.callout-note}
;; In this post, we propose a convention worth exploring:
;; * App state lives on the client.
;; * The render function receives the full app state, rather than isolated component state.
;; * Transient interactions create temporary state.
;; * The render function can return updated state alongside the UI.
;; :::

;; ## Conclusion

;; This prototype explores how a custom Clay kind could provide a Shiny-like dashboard experience for Clojure.
;; It would define conventions for transferring state between the browser and server,
;; and enhance Hiccup controls with the bindings and events needed for interaction.
