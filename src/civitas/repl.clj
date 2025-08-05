^:kindly/hide-code
^{:clay {:title  "I'll take a side of REPL with that"
         :quarto {:author      [:timothypratley]
                  :description "Selective interaction with a persistent REPL sidebar inside your Clay documents"
                  :category    :clojure
                  :type        :post
                  :date        "2025-08-05"
                  :tags        [:repl :clay]
                  :page-layout :full}}}
(ns civitas.repl
  (:require [scicloj.kindly.v4.kind :as kind]))

;; > "Would you like to make that interactive?"
;; >
;; > -- The eternal question facing documentation writers.

;; Most code documentation has either static code with fixed results
;; or dynamic editor blocks with evaluated results.
;; This post explores a third option, a sidebar REPL that coexists with static examples,
;; letting you copy interesting snippets and build on them.

;; ## Persistent Workspace Benefits

;; The sidebar REPL mirrors the intuitive "one environment, one REPL" model,
;; keeping state across interactions.

(kind/hiccup
  '((require '[reagent.core :as r])
    (defonce state (r/atom {:zaniness "moderate"
                            :mood "curious"}))))

;; We start with some initial mood and zaniness.
;; Here's a suggestion to modify it:

;; ```clojure
;; (swap! state assoc :zaniness "maximum")
;; ```

;; ::: {.callout-tip}
;; Click the "copy" icon in the code example to transfer it to the REPL window.
;; :::

;; And now we'll add to the post a mini-app for monitoring the state.

(kind/hiccup
  [:div.card {:style {:margin "20px 0"
                      :max-width "600px"}}
   [:div.card-header.bg-success.text-white
    [:h5.mb-0 "🔍 State Monitor"]]
   [:div.card-body
    [:dl.row.mb-0
     [:dt.col-sm-3.text-end "Current state:"]
     [:dd.col-sm-9
      [:div.p-2.bg-light.border.rounded.font-monospace.small
       ['(fn [] (pr-str @state))]]]
     [:dt.col-sm-3.text-end "Instructions:"]
     [:dd.col-sm-9.text-muted.small
      "Try: " [:div.sourceCode [:code "(swap! state assoc :key \"value\")"]] " in the REPL"]]]])

;; The state you create in the REPL affects page components.

;; ## Curated and Open Exploration

;; Authors can provide focused examples for specific learning goals,
;; while readers can pursue tangents.
;; Here's an example showcasing Clojure's `frequencies` function with some delicious data:

(frequencies ["apple"
              "banana"
              "apple"
              "cherry"
              "banana"
              "apple"])

;; Try it with different data, maybe the letters in your name.

;; ```clojure
;; (frequencies "supercalifragilisticexpialidocious")
;; ```

;; Or simulate random selections:

;; ```clojure
;; (frequencies (repeatedly 1000
;;                          #(rand-nth [:a :b :c :d :e :f])))
;; ```

;; ## Visual Playground

;; The REPL can affect components on the page.
;; This interactive canvas demonstrates how data can drive visual elements.

(kind/hiccup
  [:div.card {:style {:margin "20px 0"}}
   [:div.card-header.bg-primary.text-white
    [:h5.mb-0 "🎨 Interactive Canvas"]]
   [:div.card-body
    [:p.mb-3 "Shapes controlled by " [:code "@state"] " — use the REPL to create and modify them."]
    ['(fn []
       [:svg {:xmlns "http://www.w3.org/2000/svg"
              :width "100%"
              :height "250px"
              :viewBox "0 0 500 250"
              :style {:border "2px solid #dee2e6" :border-radius "4px" :background "#f8f9fa"}}
        ;; Grid pattern
        [:defs
         [:pattern {:id "grid" :width "50" :height "50" :patternUnits "userSpaceOnUse"}
          [:path {:d "M 50 0 L 0 0 0 50" :fill "none" :stroke "#e9ecef" :stroke-width "1"}]]]
        [:rect {:width "500" :height "250" :fill "url(#grid)"}]
        ;; Dynamic shapes from state
        (for [{:keys [type x y size color]} (:shapes @state)]
           (case type
             :circle [:circle {:cx x :cy y :r size :fill color :stroke "#333" :stroke-width 1}]
             :square [:rect {:x (- x size) :y (- y size)
                             :width (* 2 size) :height (* 2 size)
                             :fill color :stroke "#333" :stroke-width 1}]
             :triangle [:polygon {:points (str x "," (- y size) " "
                                               (- x size) "," (+ y size) " "
                                               (+ x size) "," (+ y size))
                                  :fill color :stroke "#333" :stroke-width 1}]
             nil))])]]])

;; Add a single shape (maybe a tiny orange dot? 🟠):

;; ```clojure
;; (swap! state update :shapes conj
;;   {:type :circle :x 250 :y 200 :size 15 :color "#f39c12"})
;; ```

;; ::: {.callout-tip}
;; Click the "copy" icon in the code example to transfer it to the REPL window, then press CTRL+Enter to eval.
;; :::

;; Add more shapes by modifying the `:shapes` vector:

;; ```clojure
;; (swap! state update :shapes into
;;   [{:type :circle :x 100 :y 100 :size 30 :color "#e74c3c"}
;;    {:type :square :x 200 :y 150 :size 25 :color "#3498db"}
;;    {:type :triangle :x 350 :y 100 :size 40 :color "#2ecc71"}])
;; ```

;; Generate a ✨random constellation✨:

;; ```clojure
;; (swap! state assoc :shapes
;;   (repeatedly 15
;;     #(hash-map :type (rand-nth [:circle :square :triangle])
;;                :x (rand-int 500)
;;                :y (rand-int 250)
;;                :size (+ 8 (rand-int 25))
;;                :color (rand-nth ["#e74c3c" "#3498db" "#2ecc71"
;;                                  "#f39c12" "#9b59b6" "#1abc9c"]))))
;; ```

;; ## The Full Development Experience

;; While the sidebar REPL provides convenient experimentation for simple examples,
;; the real power of [Clay](https://github.com/scicloj/clay) is in working with the source code.
;; Clone the [repository](https://github.com/clojurecivitas/clojurecivitas.github.io)
;; and open it in your editor to get the full interactive notebook experience.
;; You can modify examples, create new namespaces, and contribute ideas back to the community.
;; Add your own namespace under `src/` and it is published as a new post.

;; ## Sidebar REPL Usage

;; ClojureCivitas authors can require this namespace and enable the REPL sidebar:

;; ```clojure
;; (require '[civitas.repl :as repl])
;; (repl/scittle-sidebar)
;; ```

;; Set `:page-layout :full` in the `:quarto` metadata to make best use of the available page space.

;; ![I'll have the static examples with a side of REPL, thanks](repl-menu.webp)

;; ## Wrapping Up

;; I hope you enjoyed exploring this sidebar REPL approach.
;; The sidebar REPL complements Clay's strength of showing examples and results.
;; Authors can intersperse static examples with outputs and interactive prompts for experimentation.
;; The real magic happens when you clone this repository and work with the source code directly.

;; **Ready to contribute?** Add your own namespace under `src/` and it becomes a new post automatically.

;; **Join the conversation:** The best place to discuss this feature, share improvements, or ask questions is the [Zulip: clay-dev channel](https://clojurians.zulipchat.com/#narrow/channel/422115-clay-dev)

;; Give it a try and let us know what you think! 🚀

^:kindly/hide-code
(defn scittle-sidebar
  "A sidebar component that integrates a REPL with textarea editor and output display."
  []
  (kind/hiccup
    '((require '[reagent.core :as r])
      [(fn []
         (let [output-ref (atom nil)
               editor-ref (atom nil)
               show-editor? (r/atom true)
               layout (r/atom :right)
               repl-output (r/atom [])
               scroll-to-bottom! #(when @output-ref
                                    (js/setTimeout
                                      (fn [] (set! (.-scrollTop @output-ref) (.-scrollHeight @output-ref)))
                                      10))
               eval-code! (fn [code]
                            (let [captured-output (atom [])
                                  result (binding [*print-fn* #(swap! captured-output conj %)
                                                   *print-err-fn* #(swap! captured-output conj (str "ERR: " %))]
                                           (try
                                             (pr-str (js/scittle.core.eval_string code))
                                             (catch :default e
                                               (str "Error: " (.-message e)))))
                                  output-str (when (seq @captured-output)
                                               (clojure.string/join "" @captured-output))]
                              (swap! repl-output into (cond-> [(str "> " code)]
                                                              output-str (conj output-str)
                                                              :always (conj result)))
                              (scroll-to-bottom!)))]
           (.addEventListener js/document "click"
                              (fn [e]
                                (when @editor-ref
                                  (when-let [code-container (.. e -target (closest ".sourceCode"))]
                                    (when-let [code-element (.querySelector code-container "code")]
                                      (let [code-text (.-textContent code-element)]
                                        (set! (.-value @editor-ref) code-text)
                                        (.focus @editor-ref)))))))
           (fn []
             (let [collapsed? (not @show-editor?)
                   is-bottom? (= @layout :bottom)
                   toggle! #(swap! show-editor? not)
                   switch-layout! #(reset! layout (if (= @layout :right) :bottom :right))
                   collapsed-size "40px"
                   full-size "350px"]
               [:<>
                ;; Editor container
                [:div#scittle-sidebar.card.shadow-sm
                 {:style (merge
                           {:position       "fixed"
                            :z-index        "1200"
                            :display        "flex"
                            :flex-direction "column"}
                           (if is-bottom?
                             {:left   "0"
                              :right  "0"
                              :bottom "0"
                              :height (if collapsed? collapsed-size full-size)}
                             {:top         "0"
                              :right       "0"
                              :width       (if collapsed? collapsed-size full-size)
                              :height      "100vh"
                              :padding-top "77px"}))}

                 ;; Button bar
                 (when (not collapsed?)
                   [:div.d-flex.justify-content-between.align-items-center
                    {:style {:flex  "0 0 auto"
                             :order "0"}}
                    [:div.btn-group
                     [:button.btn.btn-outline-primary.btn-sm
                      {:on-click toggle!}
                      (if is-bottom?
                        [:i.bi.bi-chevron-bar-down]
                        [:i.bi.bi-chevron-bar-right])
                      " Hide"]
                     [:button.btn.btn-outline-secondary.btn-sm
                      {:on-click switch-layout!}
                      "↕ Switch Layout"]]
                    [:small.text-muted "Ctrl+Enter to eval"]])

                 ;; Restore button
                 (when collapsed?
                   [:button.btn.btn-outline-primary.w-100.h-100
                    {:on-click toggle!}
                    (if is-bottom?
                      [:i.bi.bi-chevron-bar-up]
                      [:i.bi.bi-chevron-bar-left])])

                 ;; Editor and Output container - flex direction changes based on layout
                 (when (not collapsed?)
                   [:div.card-body.p-0
                    {:style {:flex           "1 1 auto"
                             :order          "1"
                             :display        "flex"
                             :flex-direction (if is-bottom? "row" "column")}}

                    ;; Editor
                    [:div.position-relative
                     {:style {:flex     "1 1 0"
                              :overflow "hidden"}}
                     [:textarea.form-control.border-0
                      {:ref         #(reset! editor-ref %)
                       :on-key-down (fn [e]
                                      (when (and (.-ctrlKey e) (= (.-key e) "Enter"))
                                        (eval-code! (.. e -target -value))))
                       :placeholder "Type Clojure code here...\nCtrl+Enter to evaluate\nClick copy buttons in code blocks to paste here"
                       :style       {:resize        "none"
                                     :width         "100%"
                                     :height        "100%"
                                     :box-sizing    "border-box"
                                     :font-family   "var(--bs-font-monospace)"
                                     :font-size     "0.875rem"
                                     :border-radius "0"
                                     :overflow-y    "auto"}}]]

                    ;; REPL Output
                    [:div.position-relative
                     {:style {:flex     "1 1 0"
                              :overflow "hidden"}}

                     ;; Floating label in top-right
                     [:div.position-absolute.top-0.end-0.badge.bg-secondary.m-2
                      {:style {:font-size "0.65rem"
                               :z-index   "10"}}
                      "Output"]

                     [:div#repl-output.sourceCode.border-start
                      {:ref   #(reset! output-ref %)
                       :class (if is-bottom? "border-start" "border-top")
                       :style {:height      "100%"
                               :padding     "0.75rem"
                               :overflow-y  "auto"
                               :font-family "var(--bs-font-monospace)"
                               :font-size   "0.8125rem"
                               :line-height "1.4"}}

                      ;; Render output lines without rebuilding entire structure
                      (for [[i output] (map-indexed vector @repl-output)]
                        [:div.mb-1 {:key i} output])]]])

                 [:style
                  (str
                    ;; Dynamic content adjustments based on editor state
                    (when @show-editor?
                      (if is-bottom?
                        (str ".content { margin-bottom: " full-size " !important; }")
                        (str ".content { margin-right: " full-size " !important; }"))))]]]))))])))

^:kindly/hide-code
(scittle-sidebar)
