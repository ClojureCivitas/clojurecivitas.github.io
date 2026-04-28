(ns music.musicpad.app
  (:require [clojure.walk :as walk]
            [music.musicpad.view :as view]
            [music.musicpad.model :as model]
            [replicant.dom :as r]))

(def state
  (atom {:drawing {:title ""
                   :notes ""
                   :svg []}
         :history {:index -1
                   :values []}
         :img nil
         :show-data? false
         :pen-down? false
         :mode ::view/draw
         :selected nil}))

(defn interpolate-handler-data [event-data handler-data]
  (let [event (:replicant/dom-event event-data)]
    (walk/postwalk
     (fn [x]
       (case x
         :event/target.value (.. event -target -value)
         :event/target.files (.. event -target -files)
         x))
     handler-data)))

(defn commit-drawing! []
  (swap! state update :history model/new-state (:drawing @state)))

(defn parse-int [s]
  (when (some? s)
    (let [n (js/parseInt s 10)]
      (when-not (js/isNaN n)
        n))))

(defn selected-point [e]
  (let [dataset (some-> e .-target .-dataset)
        element-idx (parse-int (some-> dataset .-elementIdx))
        path-idx (parse-int (some-> dataset .-pathIdx))]
    (when (and (some? element-idx) (some? path-idx))
      {:element-idx element-idx
       :path-idx path-idx})))

(defn xy [e _dims elem]
  (let [pointer (or (some-> e .-targetTouches (aget 0))
                    (some-> e .-changedTouches (aget 0))
                    e)
        svg (or elem (.-currentTarget e) (.-target e))
        pt (.createSVGPoint svg)]
    (set! (.-x pt) (.-clientX pointer))
    (set! (.-y pt) (.-clientY pointer))
    (if-let [ctm (.getScreenCTM svg)]
      (let [local (.matrixTransform pt (.inverse ctm))]
        [(js/Math.round (.-x local))
         (js/Math.round (.-y local))])
      [(js/Math.round (.-x pt))
       (js/Math.round (.-y pt))])))

(defn event-xy [event]
  (xy event nil (.-currentTarget event)))

(defn load-background! [files]
  (when-let [file (aget files 0)]
    (let [reader (js/FileReader.)]
      (set! (.-onload reader)
            (fn [e]
              (swap! state assoc :img (.. e -target -result))))
      (.readAsDataURL reader file))))

(defn apply-history! [history]
  (swap! state assoc
         :history history
         :drawing (model/current history)))

(defn play-drawing! [state]
  (let [ctx (or (:audio-context @state)
                (let [Ctor (or (.-AudioContext js/window)
                               (.-webkitAudioContext js/window))]
                  (when Ctor
                    (let [c (Ctor.)]
                      (swap! state assoc :audio-context c)
                      c))))]
    (when ctx
      (when (= "suspended" (.-state ctx))
        (.resume ctx))
      (let [events (model/drawing->note-events (model/drawing->data-view @state))
            t0 (+ (.-currentTime ctx) 0.05)]
        (doseq [{:keys [t freq duration amp wave]} events]
          (let [osc (.createOscillator ctx)
                gain-node (.createGain ctx)
                start (+ t0 t)
                attack (+ start 0.01)
                release (+ start duration)]
            (set! (.-type osc) wave)
            (.setValueAtTime (.-frequency osc) freq start)
            (.setValueAtTime (.-gain gain-node) 0 start)
            (.linearRampToValueAtTime (.-gain gain-node) amp attack)
            (.linearRampToValueAtTime (.-gain gain-node) 0 release)
            (.connect osc gain-node)
            (.connect gain-node (.-destination ctx))
            (.start osc start)
            (.stop osc (+ release 0.01))))))))

;; Each stroke is a short horizontal line: x = time, y = pitch (y=0 → C5, y=400 → C3).
;; Tempo ~80 BPM; eighth note ≈ 38 x-units, quarter ≈ 75 x-units.
(def presets
  {"Für Elise"
   {:title "Für Elise"
    :notes "Beethoven - Für Elise (A-theme contour, lightly transposed). The line alternates short high turns with gentle downward answers, then repeats with a slightly wider cadence. Gray keeps it sine-like and smooth, so the phrase reads as lyrical rather than percussive."
    :svg [[:path {:fill "none" :stroke "#b8b8b8" :stroke-width 4
      :stroke-linecap "round" :stroke-linejoin "round"
          ;; One continuous path with one point per note.
      ;; Most notes are tightly spaced; larger x-gaps create phrase pauses.
      ;; Melody outline follows the recognizable A-theme (with repeat + cadence).
      :d ['M 10 133
            'L 20 144
            30 133
            40 144
            50 133
            60 189
            70 155
            80 166
            90 211
            108 166
            118 133
            128 211
            138 189
            156 133
            166 222
            176 189
            186 166
            206 133
            216 144
            226 133
            236 144
            246 133
            256 189
            266 155
            276 166
            286 211
            304 166
            314 133
            324 211
            334 189
            350 166
            360 189
            372 211]}]]}

     "Kookaburra"
     {:title "Kookaburra"
      :notes "Kookaburra call study. It opens with short high chirps plus a low boxy chuckle, rises into a harsh accelerating laugh, then breaks into quick answer calls at different heights. Mixed colors intentionally vary timbre so the ending sounds conversational instead of like one continuous whistle."
      :svg [;; Short intro chirps (green triangle): high, quick, bright calls.
        [:path {:fill "none" :stroke "#a1b56c" :stroke-width 2
            :stroke-linecap "round" :stroke-linejoin "round"
            :d ['M 8 132
              'L 16 118
              24 128
              32 108
              40 122]}]
        ;; Short intro chuckle (blue square): low, boxy onset before the main laugh.
        [:path {:fill "none" :stroke "#7cafc2" :stroke-width 3
            :stroke-linecap "round" :stroke-linejoin "round"
            :d ['M 44 320
              'L 52 304
              60 316
              68 296]}]
        ;; Intro chuckles (brown sawtooth): harsh, low, slightly rising.
        [:path {:fill "none" :stroke "#a16946" :stroke-width 3
            :stroke-linecap "round" :stroke-linejoin "round"
            :d ['M 72 295
              'L 88 288
              104 282
              122 276
              140 268]}]
        ;; Building laugh: accelerating climb (mid-low -> upper-mid).
        [:path {:fill "none" :stroke "#a16946" :stroke-width 4
            :stroke-linecap "round" :stroke-linejoin "round"
            :d ['M 148 258
              'L 162 245
              175 230
              186 212
              196 192
              205 172
              213 152
              220 132
              226 114]}]
        ;; Peak laugh: rapid dense arc up then back.
        [:path {:fill "none" :stroke "#a16946" :stroke-width 5
            :stroke-linecap "round" :stroke-linejoin "round"
            :d ['M 232 102
              'L 238 84
              244 66
              250 50
              256 40
              262 34
              268 38
              275 50
              283 66
              292 84
              302 102
              314 118
              328 136]}]
        ;; Descending cackle body.
        [:path {:fill "none" :stroke "#a16946" :stroke-width 3
            :stroke-linecap "round" :stroke-linejoin "round"
            :d ['M 334 146
              'L 346 168
              358 192
              370 218
              382 246]}]
        ;; Short ending answer calls (gray sine): higher, softer tail notes.
        [:path {:fill "none" :stroke "#b8b8b8" :stroke-width 2
            :stroke-linecap "round" :stroke-linejoin "round"
            :d ['M 356 148
              'L 364 128
              372 142
              380 118
              388 132]}]
        ;; Final low stutter (red sawtooth): short rough punctuation at the end.
        [:path {:fill "none" :stroke "#ab4642" :stroke-width 3
            :stroke-linecap "round" :stroke-linejoin "round"
            :d ['M 370 302
              'L 378 286
              386 298
              394 282]}]]}

     "Thunderstruck"
   {:title "Thunderstruck"
    :notes "AC/DC - Thunderstruck inspired contour. Red carries the fast lead-tapping motion, blue anchors steady low 'thunder' hits, and green adds a chant-like response phrase. The three layers are offset in time so it feels like a band texture instead of a single monophonic line."
    :svg [;; Guitar: ascending tapping riff repeated twice to fill full canvas
          ;; Red = sawtooth (buzzy/electric), thin width for fast feel
          [:path {:fill "none" :stroke "#ab4642" :stroke-width 2
                  :stroke-linecap "round" :stroke-linejoin "round"
                  :d ['M 5 144      ; B4
                      'L 18 111     ; D5
                      31 144        ; B4
                      44 89         ; E5
                      57 144        ; B4
                      70 67         ; F#5
                      83 144        ; B4
                      96 56         ; G5
                      109 144       ; B4
                      122 33        ; A5
                      135 144       ; B4
                      148 11        ; B5 peak
                      168 33        ; A5
                      188 56        ; G5
                      210 89        ; E5 resolve — repeat:
                      225 144       ; B4
                      238 111       ; D5
                      251 144       ; B4
                      264 89        ; E5
                      277 144       ; B4
                      290 67        ; F#5
                      303 144       ; B4
                      316 56        ; G5
                      329 144       ; B4
                      342 33        ; A5
                      355 144       ; B4
                      368 11        ; B5 peak
                      383 33]}] ; A5 fade
          ;; Thunder bass: low E3 hits throughout, sparse quarter-note spacing
          ;; Blue = square (deep, boxy), thick width for impact
          [:path {:fill "none" :stroke "#7cafc2" :stroke-width 5
                  :stroke-linecap "round" :stroke-linejoin "round"
                  :d ['M 5 356
                      'L 56 356
                      107 356
                      158 356
                      209 356
                      260 356
                      311 356
                      362 356]}]
          ;; Nah nah nah: tight bursts of B4-A4-G4-A4, then silence, repeat
          ;; Green = triangle (warm, hollow)
          [:path {:fill "none" :stroke "#a1b56c" :stroke-width 3
                  :stroke-linecap "round" :stroke-linejoin "round"
                  :d ['M 5 144      ; burst 1
                      'L 17 167
                      29 189
                      41 167
                      ; gap ~40px
                      82 144        ; burst 2
                      94 167
                      106 189
                      118 167
                      ; gap
                      159 144       ; burst 3
                      171 167
                      183 189
                      195 167
                      ; gap
                      236 144       ; burst 4
                      248 167
                      260 189
                      272 167
                      ; gap
                      313 144       ; burst 5
                      325 167
                      337 189
                      349 167]}]]}})

(defn drop-selected! []
  (when (:selected @state)
    (swap! state assoc :selected nil)
    (commit-drawing!)))

(defn handle-event! [event-data handler-data]
  (let [[action & args] (interpolate-handler-data event-data handler-data)]
    (case action
      :musicpad/set-mode
      (swap! state assoc :mode (first args))

      :musicpad/clear-background
      (swap! state assoc :img nil)

      :musicpad/load-background
      (load-background! (first args))

      :musicpad/prevent-default
      (.preventDefault (:replicant/dom-event event-data))

      :musicpad/set-stroke-width
      (do
        (swap! state assoc-in [:drawing :svg-attrs :stroke-width]
               (js/parseInt (first args)))
        (commit-drawing!))

      :musicpad/set-color
      (do
        (swap! state assoc-in [:drawing :svg-attrs :stroke] (first args))
        (commit-drawing!))

      :musicpad/select-point
      (swap! state assoc :selected
              (selected-point (:replicant/dom-event event-data)))

      :musicpad/start-path
      (let [event (:replicant/dom-event event-data)]
        (when (not= (.-buttons event) 0)
          (swap! state assoc :pen-down? true)
          (let [[x y] (event-xy event)]
            (swap! state update-in [:drawing :svg]
                   conj
                   [:path (merge (view/current-stroke-attrs @state)
                                 {:d ['M x y 'L x y]})]))))

      :musicpad/continue-path
      (let [event (:replicant/dom-event event-data)]
        (when (:pen-down? @state)
          (let [[x y] (event-xy event)]
            (swap! state update-in [:drawing :svg]
                   #(update-in % [(dec (count %)) 1 :d] conj x y)))))

      :musicpad/end-path
      (let [event (:replicant/dom-event event-data)]
        (when (:pen-down? @state)
          (when event
            (let [[x y] (event-xy event)]
              (swap! state update-in [:drawing :svg]
                     #(update-in % [(dec (count %)) 1 :d] conj x y))))
          (swap! state assoc :pen-down? false)
          (commit-drawing!)))

      :musicpad/drag-point
      (let [event (:replicant/dom-event event-data)]
        (when-let [{:keys [element-idx path-idx]} (:selected @state)]
          (if (and (some? (.-buttons event))
                   (zero? (.-buttons event)))
            (drop-selected!)
            (let [[x y] (event-xy event)]
              (swap! state update :drawing model/move-point element-idx path-idx x y)))))

      :musicpad/drop-point
      (drop-selected!)

      :musicpad/erase-point
      (let [[element-idx path-idx] args]
        (swap! state update :drawing model/erase-point element-idx path-idx)
        (commit-drawing!))

      :musicpad/erase-path
      (let [[element-idx] args]
        (swap! state update :drawing model/erase element-idx)
        (commit-drawing!))

      :musicpad/undo
      (apply-history! (model/undo (:history @state)))

      :musicpad/redo
      (apply-history! (model/redo (:history @state)))

      :musicpad/set-title
      (swap! state assoc-in [:drawing :title] (first args))

      :musicpad/set-notes
      (swap! state assoc-in [:drawing :notes] (first args))

      :musicpad/save
      (commit-drawing!)

      :musicpad/toggle-data-view
      (swap! state update :show-data? not)

      :musicpad/play-drawing
      (play-drawing! state)

      :musicpad/clear-canvas
      (do
        (swap! state
               (fn [s]
                 (-> s
                     (assoc :img nil)
                     (assoc-in [:drawing :svg] []))))
        (commit-drawing!))

      :musicpad/load-preset
      (when-let [drawing (get presets (first args))]
        (swap! state assoc :drawing drawing)
        (commit-drawing!))

      nil)))

(defn app []
  (view/draw @state))

(defn start! []
  (r/set-dispatch! handle-event!)
  (let [root (js/document.getElementById "app")
        render! #(r/render root (app))]
    (remove-watch state :ui-rerender)
    (add-watch state :ui-rerender (fn [_ _ _ _] (render!)))
    (render!)))

(start!)
