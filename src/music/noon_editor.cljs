(ns music.noon-editor
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

;; ============================================================================
;; CSS (injected once)
;; ============================================================================

(defonce _inject-styles
  (let [style (js/document.createElement "style")]
    (set! (.-textContent style)
          "@keyframes noon-spin {
             0% { transform: rotate(0deg); }
             100% { transform: rotate(360deg); }
           }
           .noon-spinner {
             display: inline-block;
             width: 14px; height: 14px;
             border: 2px solid rgba(255,255,255,0.3);
             border-top-color: #fff;
             border-radius: 50%;
             animation: noon-spin 0.6s linear infinite;
             vertical-align: middle;
             margin-right: 6px;
           }
           .noon-editor .CodeMirror {
             border: 1px solid #ccc;
             border-radius: 4px;
             font-size: 0.9rem;
             height: auto;
             min-height: 60px;
           }
           .noon-editor .CodeMirror-scroll {
             min-height: 60px;
             max-height: 300px;
           }
           .noon-editor .CodeMirror-activeline-background {
             background: rgba(0, 120, 215, 0.06);
           }
           .noon-editor .CodeMirror-matchingbracket {
             color: #2196F3 !important;
             font-weight: bold;
             background: rgba(33, 150, 243, 0.15);
           }")
    (.appendChild js/document.head style)))

;; ============================================================================
;; noon interop (via window globals from noon-eval.js)
;; ============================================================================

(defn eval-noon [code-str]
  (if js/window.NoonReady
    (js/window.noon_eval_string code-str)
    #js {:error "noon is still loading..."}))

(defn eval-noon-to-piano-roll [code-str]
  (if js/window.NoonReady
    (js/window.noon_eval_to_piano_roll code-str)
    #js {:error "noon is still loading..."}))

(defn eval-noon-to-midi [code-str]
  (if js/window.NoonReady
    (js/window.noon_eval_to_midi code-str)
    #js {:error "noon is still loading..."}))

(defn play-midi [midi-data-js bpm]
  (when js/window.NoonReady
    (js/window.noon_play_midi midi-data-js (or bpm 60))))

(defn stop-noon []
  (when js/window.NoonReady
    (js/window.noon_stop)))

;; ============================================================================
;; Playback duration computation
;; ============================================================================

(defn compute-duration
  "Compute total playback duration in seconds from JS midi data array."
  [midi-data-js bpm]
  (let [n (.-length midi-data-js)
        bpm (or bpm 60)
        stretch (/ 60 bpm)]
    (loop [i 0 max-end 0]
      (if (< i n)
        (let [evt (aget midi-data-js i)
              pos (or (aget evt "position") 0)
              dur (or (aget evt "duration") 0)
              end (+ pos dur)]
          (recur (inc i) (max max-end end)))
        (* max-end stretch)))))

;; ============================================================================
;; Unique ID generator for CodeMirror mount points
;; ============================================================================

(defonce cm-counter (atom 0))

(defn next-cm-id []
  (str "noon-cm-" (swap! cm-counter inc)))

;; ============================================================================
;; CodeMirror wrapper component
;; ============================================================================

(defn codemirror-editor
  "A Reagent component wrapping CodeMirror 5.
  Props: {:value atom, :on-eval fn, :on-play fn}"
  [{:keys [value on-eval on-play]}]
  (let [cm-instance (atom nil)
        dom-id (next-cm-id)]
    (r/create-class
     {:display-name "codemirror-editor"

      :component-did-mount
      (fn [_this]
        (when-let [node (js/document.getElementById dom-id)]
          (let [cm (js/CodeMirror.
                    node
                    #js {:value @value
                         :mode "clojure"
                         :theme "neat"
                         :lineNumbers false
                         :matchBrackets true
                         :autoCloseBrackets true
                         :styleActiveLine true
                         :viewportMargin js/Infinity
                         :extraKeys #js {"Ctrl-Enter" (fn [_cm] (when on-eval (on-eval)))
                                         "Shift-Enter" (fn [_cm] (when on-play (on-play)))
                                         "Cmd-Enter" (fn [_cm] (when on-eval (on-eval)))}})]
            (reset! cm-instance cm)
            (.on cm "change"
                 (fn [_cm _change]
                   (let [v (.getValue cm)]
                     (when (not= v @value)
                       (reset! value v))))))))

      :reagent-render
      (fn [_props]
        [:div {:id dom-id}])})))

;; ============================================================================
;; Editor component
;; ============================================================================

(defn code-editor [{:keys [initial-code label]}]
  (let [code (r/atom (or initial-code "(score (tup s0 s1 s2))"))
        output (r/atom nil)
        piano-roll-html (r/atom nil)
        play-state (r/atom :idle) ;; :idle :evaluating :loading :playing
        error? (r/atom false)
        done-timeout (atom nil)

        do-eval!
        (fn []
          (when (= @play-state :idle)
            (reset! play-state :evaluating)
            (js/setTimeout
             (fn []
               (let [result (eval-noon @code)
                     pr-result (eval-noon-to-piano-roll @code)]
                 (if (.-error result)
                   (do (reset! error? true)
                       (reset! output (.-error result))
                       (reset! piano-roll-html nil))
                   (do (reset! error? false)
                       (reset! output (.-result result))
                       (if (.-error pr-result)
                         (reset! piano-roll-html nil)
                         (reset! piano-roll-html (.-result pr-result)))))
                 (reset! play-state :idle)))
             15)))

        do-play!
        (fn []
          (when (= @play-state :idle)
            (let [result (eval-noon-to-midi @code)]
              (if (.-error result)
                (do (reset! error? true)
                    (reset! output (.-error result)))
                (let [midi-data (.-result result)
                      bpm 60
                      duration-s (compute-duration midi-data bpm)]
                  (reset! error? false)
                  (reset! play-state :loading)
                  (-> (play-midi midi-data bpm)
                      (.then
                       (fn [_]
                         (reset! play-state :playing)
                         (let [tid (js/setTimeout
                                    (fn []
                                      (reset! done-timeout nil)
                                      (when (= @play-state :playing)
                                        (reset! play-state :idle)))
                                    (+ 1500 (* 1000 duration-s)))]
                           (reset! done-timeout tid))))
                      (.catch
                       (fn [err]
                         (reset! play-state :idle)
                         (reset! error? true)
                         (reset! output (str "Playback error: " (.-message err)))))))))))]

    ;; Auto-eval on mount: wait for noon to be ready, then eval
    (letfn [(try-auto-eval [attempts]
              (if js/window.NoonReady
                (do-eval!)
                (when (pos? attempts)
                  (js/setTimeout #(try-auto-eval (dec attempts)) 500))))]
      (js/setTimeout #(try-auto-eval 10) 200))

    (fn [{:keys [initial-code label]}]
      [:div.noon-editor
       {:style {:margin "1.5rem 0" :padding "1rem"
                :border "1px solid #d0d7de" :border-radius "8px"
                :background "#fafbfc"
                :box-shadow "0 1px 3px rgba(0,0,0,0.06)"}}
       (when label
         [:h4 {:style {:margin-top 0 :margin-bottom "0.75rem"
                       :color "#333" :font-size "1rem"}} label])

       ;; CodeMirror editor
       [codemirror-editor {:value code :on-eval do-eval! :on-play do-play!}]

       ;; Keyboard hint
       [:div {:style {:margin-top "0.25rem" :font-size "0.75rem"
                      :color "#888"}}
        "Ctrl+Enter = Eval · Shift+Enter = Play"]

       ;; Buttons
       [:div {:style {:display :flex :gap "0.5rem" :margin-top "0.5rem"
                      :flex-wrap :wrap :align-items :center}}

        ;; Eval button
        [:button
         {:on-click (fn [_] (do-eval!))
          :disabled (not= @play-state :idle)
          :style {:padding "0.4rem 1rem"
                  :background (if (= @play-state :evaluating) "#90caf9" "#2196F3")
                  :color :white
                  :border :none :border-radius "4px"
                  :cursor (if (= @play-state :idle) :pointer :default)
                  :font-size "0.85rem"
                  :transition "background 0.2s"}}
         (if (= @play-state :evaluating)
           [:span [:span.noon-spinner] "Evaluating..."]
           "Eval")]

        ;; Play button
        (case @play-state
          (:idle :evaluating :loading)
          [:button
           {:on-click (fn [_] (do-play!))
            :disabled (not= @play-state :idle)
            :style {:padding "0.4rem 1rem"
                    :background (case @play-state
                                  :idle "#4CAF50"
                                  "#999")
                    :color :white
                    :border :none :border-radius "4px"
                    :cursor (if (= @play-state :idle) :pointer :default)
                    :font-size "0.85rem"
                    :transition "background 0.2s"}}
           (case @play-state
             :idle "▶ Play"
             :evaluating "▶ Play"
             :loading [:span [:span.noon-spinner] "Loading..."])]

          :playing
          [:button
           {:on-click
            (fn [_]
              (when-let [tid @done-timeout]
                (js/clearTimeout tid)
                (reset! done-timeout nil))
              (stop-noon)
              (reset! play-state :idle))
            :style {:padding "0.4rem 1rem"
                    :background "#f44336" :color :white
                    :border :none :border-radius "4px"
                    :cursor :pointer :font-size "0.85rem"
                    :transition "background 0.2s"}}
           "■ Stop"])]

       ;; Piano roll visualization
       (when @piano-roll-html
         [:div {:style {:margin-top "0.75rem" :overflow-x "auto"
                        :border "1px solid #e5e7eb" :border-radius "4px"
                        :background "#fff"}
                :dangerouslySetInnerHTML {:__html @piano-roll-html}}])

       ;; Text output
       (when @output
         [:details {:style {:margin-top "0.5rem"}
                    :open (when @error? true)}
          [:summary {:style {:font-size "0.8rem" :color "#666"
                             :cursor :pointer :user-select :none}}
           (if @error? "Error" "Score data")]
          [:pre {:style {:margin-top "0.25rem" :padding "0.75rem"
                         :background (if @error? "#fff0f0" "#f0f8f0")
                         :border (str "1px solid " (if @error? "#f5c6cb" "#c3e6cb"))
                         :border-radius "4px" :overflow-x :auto
                         :font-size "0.85rem" :font-family "monospace"
                         :white-space :pre-wrap :max-height "200px"
                         :overflow-y :auto}}
           @output]])])))

;; ============================================================================
;; Mount helper
;; ============================================================================

(defn render-editor [element-id opts]
  (when-let [el (js/document.getElementById element-id)]
    (rdom/render [code-editor opts] el)))
