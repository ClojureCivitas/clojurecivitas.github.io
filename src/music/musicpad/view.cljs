(ns music.musicpad.view
  (:require [cljs.pprint :as pprint]
            [clojure.string :as str]
            [music.musicpad.model :as model]))

(def default-stroke-attrs
  {:fill "none"
   :stroke "black"
   :stroke-width 4
   :stroke-linecap "round"
   :stroke-linejoin "round"})

(defn current-stroke-attrs [s]
  (merge default-stroke-attrs
         (select-keys (get-in s [:drawing :svg-attrs] {})
                      [:stroke :stroke-width])))

(def colors
  ["black"
   "#181818"
   "#282828"
   "#383838"
   "#585858"
   "#b8b8b8"
   "#d8d8d8"
   "#e8e8e8"
   "#f8f8f8"
   "#ab4642"
   "#dc9656"
   "#f7ca88"
   "#a1b56c"
   "#86c1b9"
   "#7cafc2"
   "#ba8baf"
   "#a16946"])

(defn path-data->string [attrs]
  (update attrs :d #(str/join " " %)))

(defn prepare [[tag attrs] mode element-idx]
  (if (= mode ::draw)
    [tag (path-data->string attrs)]
    (into
     [:g
      [tag (path-data->string attrs)]]
     (map-indexed
      (fn [path-idx [x y]]
        [:circle
           {:on {:mousedown
                 (if (= mode ::erase)
                   [:musicpad/erase-point element-idx path-idx]
                   [:musicpad/select-point])}
          :data-element-idx element-idx
          :data-path-idx path-idx
          :cx x
          :cy y
          :r 3
          :fill "white"
          :stroke "red"
          :stroke-width 1}])
      (partition 2 (filter number? (:d attrs)))))))

(defn toolbar [s]
  (let [mode-button
        (fn [title button-mode]
          [:button.btn.btn-sm.py-0.px-2
           {:title title
            :class (if (= (:mode s) button-mode)
                     "btn-primary"
                     "btn-outline-primary")
            :on {:click [:musicpad/set-mode button-mode]}}
           title])]
    [:div.d-flex.flex-column.gap-2
     [:div
      [:div.form-label.small.mb-1 "Mode"]
      [:div.btn-group.btn-group-sm
       (mode-button "Draw" ::draw)
       (mode-button "Edit" ::edit)
       (mode-button "Erase" ::erase)]]
     (if (:img s)
       [:div
        [:div.form-label.small.mb-1 "Background"]
        [:button.btn.btn-sm.py-0.px-2.btn-outline-secondary
         {:title "Clear Background"
          :on {:click [:musicpad/clear-background]}}
         "Clear"]]
       [:div
        [:div.form-label.small.mb-1 "Background"]
        [:label.btn.btn-sm.py-0.px-2.btn-outline-secondary.mb-0
         [:input
          {:type "file"
           :accept "image/*"
           :style {:display "none"}
           :on {:change [:musicpad/load-background :event/target.files]}}]
         "Upload"]])
     [:div
      [:div.form-label.small.mb-1 "Pen"]
      [:div.d-flex.flex-wrap.gap-1
       (for [w [1 2 3 4 5]]
         [:button.btn.btn-sm.border.rounded-circle
          {:key (str "pen-" w)
           :type "button"
           :title (str "Pen " w " (thicker = louder/longer)")
           :aria-label (str "Set pen width " w)
           :class (when (= w (get-in s [:drawing :svg-attrs :stroke-width] 3))
                    "border-dark")
           :on {:click [:musicpad/set-stroke-width w]}
           :style {:background-color "var(--bs-body-color)"
                   :width "1.35rem"
                   :height "1.35rem"
                   :padding 0
               :box-shadow (str "inset 0 0 0 "
                                    (nth [8.6 6.6 5.0 3.3 1.6] (dec w))
                    "px var(--bs-body-bg)")}}])]]
     [:div
      [:div.form-label.small.mb-1 "Color"]
      [:div.d-flex.flex-wrap.gap-1
       (for [color colors]
         [:button.btn.btn-sm.border.rounded-circle
          {:key color
           :type "button"
           :title color
           :aria-label (str "Set color " color)
           :class (when (= color (get-in s [:drawing :svg-attrs :stroke] "black"))
                    "border-dark")
           :on {:click [:musicpad/set-color color]}
           :style {:background-color color
                   :width "1.35rem"
                   :height "1.35rem"
                   :padding 0}}])]]
     [:div
      [:div.form-label.small.mb-1 "Presets"]
      [:div.d-flex.flex-column.gap-1
       [:button.btn.btn-sm.py-0.px-2.btn-outline-secondary
        {:title "Load Für Elise (Beethoven)"
         :on {:click [:musicpad/load-preset "Für Elise"]}}
        "Für Elise"]
       [:button.btn.btn-sm.py-0.px-2.btn-outline-secondary
        {:title "Load Thunderstruck (AC/DC)"
         :on {:click [:musicpad/load-preset "Thunderstruck"]}}
        "Thunderstruck"]
             [:button.btn.btn-sm.py-0.px-2.btn-outline-secondary
        {:title "Load Kookaburra Call"
         :on {:click [:musicpad/load-preset "Kookaburra"]}}
        "Kookaburra"]]]
     [:div
      [:div.form-label.small.mb-1 "History"]
      [:div.btn-group.btn-group-sm
       [:button.btn.btn-sm.py-0.px-2.btn-outline-secondary
        {:title "Undo"
           :on {:click [:musicpad/undo]}}
        "Undo"]
       [:button.btn.btn-sm.py-0.px-2.btn-outline-secondary
        {:title "Redo"
           :on {:click [:musicpad/redo]}}
         "Redo"]
       [:button.btn.btn-sm.py-0.px-2.btn-outline-danger
        {:title "Clear Canvas"
        :on {:click [:musicpad/clear-canvas]}}
        "Clear"]]]
     [:div
      [:div.form-label.small.mb-1 "Audio"]
      [:button.btn.btn-outline-success.w-100
       {:title "Play as sound"
        :on {:click [:musicpad/play-drawing]}}
       "Play"]]]))

(defn paths [s]
  (into
   [:g
    (merge
     {:style {:pointer-events (if (= ::erase (:mode s))
                                "visiblePainted"
                                (if (= ::edit (:mode s))
                                  "visiblePainted"
                                  "none"))}
      :on {:touchstart
           [:musicpad/prevent-default]}}
     default-stroke-attrs)]
   (map-indexed
    (fn [idx elem]
      (prepare
       (cond-> elem
         (= ::erase (:mode s)) (assoc-in [1 :on :mousedown]
                                     [:musicpad/erase-path idx]))
       (:mode s)
       idx))
    (get-in s [:drawing :svg]))))

(defn observe
  "Render a drawing as a plain SVG element with no interactive controls.
   Useful for displaying a saved drawing without the editor UI."
  [drawing]
  (let [{:keys [dims svg]} drawing
        dims* (or dims model/default-dims)]
    (into
     [:svg (merge default-stroke-attrs
                  {:viewBox (str/join " " (concat [0 0] dims*))
                   :style {:user-select "none"
                           :-webkit-user-select "none"}})]
     (map-indexed (fn [idx elem] (prepare elem ::draw idx)) svg))))

(defn draw [s]
  (let [dims (or (get-in s [:drawing :dims]) model/default-dims)]
    [:div
     {:style {:display "flex"
              :flex-direction "column"
              :gap "0.5rem"}}
     ;; Top row: SVG and toolbar
     [:div
      {:style {:display "flex"
               :gap "0.5rem"}}
      ;; SVG canvas
      [:div
       {:style {:flex "0 1 66.666%"
                :min-width 0}}
       [:div
        ;; http://alistapart.com/article/creating-intrinsic-ratios-for-video
        {:style {:position "relative"
                 :height 0
                 :padding-bottom "100%"
                 :border "1px solid var(--bs-border-color, black)"
                 :border-radius "var(--bs-border-radius, 0.375rem)"
                 :overflow "hidden"}}
        [:svg
         (merge-with
          merge
          {:viewBox (str/join " " (concat [0 0] dims))
           :style {:position "absolute"
                   :top 0
                   :width "100%"
                   :height "100%"
                   :-webkit-touch-callout "none"
                   :-webkit-user-select "none"
                   :-khtml-user-select "none"
                   :-moz-user-select "none"
                   :-ms-user-select "none"
                   :user-select "none"}}
          (case (:mode s)
            ::edit
            {:style {:cursor "move"}
             :on {:touchstart [:musicpad/select-point]
                  :mousedown [:musicpad/select-point]
                  :touchmove [:musicpad/drag-point]
                  :mousemove [:musicpad/drag-point]
                  :touchend [:musicpad/drop-point]
                  :mouseup [:musicpad/drop-point]
                  :touchcancel [:musicpad/drop-point]}}

            ::erase
            {:style {:cursor "cell"}}

            {:style {:cursor "crosshair"}
             :on {:touchstart [:musicpad/start-path]
                  :mousedown [:musicpad/start-path]
                  :mouseover [:musicpad/start-path]
                  :touchmove [:musicpad/continue-path]
                  :mousemove [:musicpad/continue-path]
                  :touchend [:musicpad/end-path]
                  :mouseup [:musicpad/end-path]
                  :touchcancel [:musicpad/end-path]
                  :mouseout [:musicpad/end-path]}}))
         (when (:img s)
           [:image {:href (:img s)
                    :xlink-href (:img s)
                    :x 0
                    :y 0
                    :width (first dims)
                    :height (second dims)
                    :preserveAspectRatio "none"
                    :style {:pointer-events "none"}
                    :opacity 0.3}])
         (paths s)]]]
      ;; Toolbar
      [:div
       {:style {:flex "0 1 33.333%"
                :min-width 0}}
       (toolbar s)]]
     ;; Legend
     [:div.small.text-muted.mb-2
      [:strong "How it sounds: "]
      "Left→right is time (3.5 seconds). "
      "Top→bottom is pitch (C5 down to C3). "
      "Color sets the timbre: gray = sine, red = sawtooth, green = triangle, blue = square. "
      "Brighter colors play slightly louder; a thicker pen plays louder and longer notes."]
     ;; Title and notes
     [:div.row.g-2.align-items-end.mb-2
      [:div.col-12.col-lg-4
       [:label.form-label.small.mb-1 {:for "drawing_title"} "Title"]
       [:input#drawing_title.form-control.form-control-sm
        {:type "text"
         :value (get-in s [:drawing :title])
         :on {:blur [:musicpad/save]
              :change [:musicpad/set-title :event/target.value]}}]]
      [:div.col-12.col-lg-8
       [:label.form-label.small.mb-1 {:for "drawing_notes"} "Notes"]
       [:textarea#drawing_notes.form-control.form-control-sm
        {:rows 3
         :value (get-in s [:drawing :notes] "")
         :on {:blur [:musicpad/save]
              :change [:musicpad/set-notes :event/target.value]}}]]
      [:div.col-12
       [:div.d-flex.align-items-center.justify-content-between.mb-1
        [:label.form-label.small.mb-0 "Drawing Data"]
        [:button.btn.btn-sm.py-0.px-2.btn-outline-secondary
         {:type "button"
          :on {:click [:musicpad/toggle-data-view]}}
         (if (:show-data? s) "Hide" "Show")]]
       (when (:show-data? s)
         [:pre.small.border.rounded.p-2.mb-0.bg-body-tertiary
          {:style {:max-height "16rem"
                   :overflow "auto"}}
          (with-out-str
            (pprint/pprint (model/drawing->data-view s)))])]]]))
