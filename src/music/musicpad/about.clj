^{:kindly/hide-code true
  :clay {:title  "The Shape of a Kookaburra Call"
         :quarto {:author      [:timothypratley]
                  :description "What if a sketch could be a score? This interactive post maps drawing gestures to sound, turning x/y position, color, and stroke width into timing, pitch, timbre, and dynamics."
                  :image       "musicpad_example.png"
                  :type        :post
                  :date        "2026-04-28"
                  :category    :music
                  :tags        [:drawing :music]}}}
(ns music.musicpad.about)

^:kind/hiccup ^:kindly/hide-code
'([:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.js"}]
  [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.pprint.js"}]
  [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.replicant.js"}]
  [:script {:src "model.cljs"
          :type "application/x-scittle"}]
  [:script {:src "view.cljs"
          :type "application/x-scittle"}]
  [:script {:src "app.cljs"
            :type "application/x-scittle"}])

;; Can a birdsong be sketched?
;; Something more direct than musical staff notation.
;; Draw a contour and press `Play`.

^:kind/hiccup ^:kindly/hide-code
[:div#app]

;; ## The idea
;;
;; Michael Nardell raised the idea in the
;; [real-world-data group](https://scicloj.github.io/docs/community/groups/real-world-data/),
;; pointing to a graphic music composition system built by composer Iannis Xenakis in the 1970s called [UPIC](https://en.wikipedia.org/wiki/UPIC).
;; UPIC let musicians draw waveforms and score structures on a digitising tablet,
;; which the system would then synthesise into sound.
;; The question here is a modest echo of that: what would it look like to treat a drawing as data?
;; We usually plot data as drawings; this flips the direction and asks:
;; what data can we recover from a drawing gesture?
;;
;; A drawing is a sequence of strokes.
;; Each stroke is a sequence of 2D points.
;; Points encode *position over time*.
;; Left to right is time, top to bottom is pitch.
;; Color and thickness can be selected as additional data dimensions.

;; Interpreted as sound, a drawing maps data to musical properties.
;; Contour becomes melody.
;; Spacing becomes rhythm.
;; Color becomes timbre.
;; Harmony comes from layering strokes that occupy the same span of time.
;;
;; Kookaburra calls can be drawn as short chirps, a rising cackle, and falling "laughing" phrases.
;; You may see the shape of the sounds before you can name the notes.

;; ## How strokes become sound
;;
;; When you press Play, each stroke is converted to a series of note events:
;;
;; - **X position → time** — leftmost is the start, rightmost is 3.5 seconds in.
;;   Strokes are not played sequentially; all of them are scheduled at once
;;   and overlap freely.
;;
;; - **Y position → pitch** — the top of the canvas is the highest note (C5),
;;   the bottom is the lowest (C3). The mapping goes through MIDI note numbers
;;   so the pitch grid is evenly-tempered.
;;
;; - **Color → timbre** — the RGB values of the pen color determine the wave shape:
;;   - neutral grays → **sine** (pure, smooth)
;;   - reds → **sawtooth** (bright, buzzy)
;;   - greens → **triangle** (hollow, flute-like)
;;   - blues / other → **square** (nasal, vintage)
;;   Lighter, brighter colors also play slightly louder.
;;
;; - **Stroke width → dynamics** — a thicker pen plays louder notes that sustain
;;   a little longer. A hairline stroke is quiet.

;; ## Drawing as data input
;;
;; Can we use this idea outside of audio?
;; What inputs might be entered as sketches?
;; A hand-drawn gesture might be a fast way to initialize structured data before running a simulation.

;; The interesting part is not that drawings are precise.
;; It is that they are fast, legible, and human.
;; They carry intent, and are fun to create.

;; ## The data model

;; Conceptually, a stroke consists of points plus style.
;; The "Show" button at the bottom of the pad can be used to see the EDN data.

;; A single stroke looks like this:

;; ```clojure
;; {:points [[12 80] [24 75] [37 68] ...]
;;  :style  {:stroke       "#7cafc2"
;;           :stroke-width 2}}
;; ```

;; Every stroke is pushed onto a history stack so you can step backward and forward.
;; History is stored as a vector of snapshots.

;; ## Implementation

;; The UI is built with [Replicant](https://github.com/cjohansen/replicant),
;; a lightweight ClojureScript library for declarative DOM rendering,
;; running in the browser via [Scittle](https://github.com/babashka/scittle).

;; Audio is generated with the [Web Audio API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API).
;; Each note is a scheduled oscillator — the drawing is compiled to a flat
;; sequence of `{:t :freq :amp :duration :wave}` events and handed off to the
;; browser's audio engine.

;; ## Conclusion

;; A sketch does not need to be perfect to be useful.
;; In this little instrument, lines become melody, rhythm, and texture.
;; It's a playful experience.
;; A doodle becomes data you can hear.
