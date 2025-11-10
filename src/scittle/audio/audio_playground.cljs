(ns scittle.audio.audio-playground
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [clojure.string :as str]))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn merge-styles
  "Safely merges multiple style maps"
  [& styles]
  (apply merge (filter map? styles)))

;; ============================================================================
;; Audio Context & State Management
;; ============================================================================

(defonce audio-context (atom nil))
(defonce master-gain (atom nil))
(defonce active-oscillators (atom {}))

(defn init-audio-context!
  "Initialize Web Audio API context on first user interaction"
  []
  (when-not @audio-context
    (try
      (let [AudioContext (or js/window.AudioContext js/window.webkitAudioContext)
            ctx (AudioContext.)
            gain (.createGain ctx)]
        (.connect gain (.-destination ctx))
        (set! (.-value (.-gain gain)) 0.5)
        (reset! audio-context ctx)
        (reset! master-gain gain)
        (js/console.log "🔊 Audio context initialized"))
      (catch js/Error e
        (js/console.error "Failed to initialize audio context:" e)))))

;; ============================================================================
;; Core Audio Functions
;; ============================================================================

(defn create-oscillator
  "Creates an oscillator with envelope shaping
   Options:
   - :frequency - Frequency in Hz (default: 440)
   - :type - Waveform type: sine, square, sawtooth, triangle (default: sine)
   - :duration - Duration in seconds (default: 0.2)
   - :attack - Attack time in seconds (default: 0.01)
   - :release - Release time in seconds (default: 0.1)
   - :gain-value - Peak gain value (default: 0.3)"
  [& {:keys [frequency type duration attack release gain-value]
      :or {frequency 440
           type "sine"
           duration 0.2
           attack 0.01
           release 0.1
           gain-value 0.3}}]
  (init-audio-context!)
  (when @audio-context
    (try
      (let [osc (.createOscillator @audio-context)
            gain (.createGain @audio-context)
            now (.-currentTime @audio-context)
            stop-time (+ now duration release)]
        ;; Configure oscillator
        (set! (.-type osc) type)
        (set! (.-value (.-frequency osc)) frequency)

        ;; Connect audio nodes
        (.connect osc gain)
        (.connect gain @master-gain)

        ;; Apply envelope (ADSR)
        (set! (.-value (.-gain gain)) 0)
        ;; Attack
        (.linearRampToValueAtTime (.-gain gain) gain-value (+ now attack))
        ;; Release
        (.exponentialRampToValueAtTime (.-gain gain) 0.01 stop-time)

        ;; Start and schedule stop
        (.start osc now)
        (.stop osc stop-time)

        {:oscillator osc :gain gain})
      (catch js/Error e
        (js/console.error "Error creating oscillator:" e)
        nil))))

(defn create-noise-buffer
  "Creates a buffer filled with white noise
   Options:
   - :sample-rate - Sample rate in Hz (default: 44100)
   - :duration - Duration in seconds (default: 0.1)"
  [& {:keys [sample-rate duration]
      :or {sample-rate 44100
           duration 0.1}}]
  (when @audio-context
    (let [buffer-size (* sample-rate duration)
          buffer (.createBuffer @audio-context 1 buffer-size sample-rate)
          data (.getChannelData buffer 0)]
      ;; Fill with white noise
      (dotimes [i buffer-size]
        (aset data i (- (* 2 (js/Math.random)) 1)))
      buffer)))

;; ============================================================================
;; Sound Effects Library
;; ============================================================================

(defn play-click
  "Click sound - short double beep"
  []
  (create-oscillator :frequency 600
                     :type "sine"
                     :duration 0.05
                     :gain-value 0.2)
  (create-oscillator :frequency 800
                     :type "sine"
                     :duration 0.05
                     :gain-value 0.2))

(defn play-success
  "Success sound - ascending major chord"
  []
  (js/setTimeout #(create-oscillator :frequency 523 :type "sine" :duration 0.1) 0)
  (js/setTimeout #(create-oscillator :frequency 659 :type "sine" :duration 0.1) 100)
  (js/setTimeout #(create-oscillator :frequency 784 :type "sine" :duration 0.2) 200))

(defn play-error
  "Error sound - low harsh tone"
  []
  (create-oscillator :frequency 200
                     :type "sawtooth"
                     :duration 0.3
                     :gain-value 0.25)
  (create-oscillator :frequency 150
                     :type "square"
                     :duration 0.3
                     :gain-value 0.2))

(defn play-notification
  "Notification sound - double ping"
  []
  (js/setTimeout #(create-oscillator :frequency 880 :type "sine" :duration 0.1) 0)
  (js/setTimeout #(create-oscillator :frequency 880 :type "sine" :duration 0.1) 150))

(defn play-hover
  "Hover sound - quick subtle beep"
  []
  (create-oscillator :frequency 1200
                     :type "sine"
                     :duration 0.02
                     :gain-value 0.15))

(defn play-complete
  "Complete sound - ascending arpeggio"
  []
  (js/setTimeout #(create-oscillator :frequency 392 :type "sine" :duration 0.1) 0)
  (js/setTimeout #(create-oscillator :frequency 523 :type "sine" :duration 0.1) 100)
  (js/setTimeout #(create-oscillator :frequency 659 :type "sine" :duration 0.1) 200)
  (js/setTimeout #(create-oscillator :frequency 784 :type "sine" :duration 0.3) 300))

(defn play-delete
  "Delete sound - descending harsh tones"
  []
  (create-oscillator :frequency 300
                     :type "square"
                     :duration 0.1
                     :gain-value 0.2)
  (create-oscillator :frequency 200
                     :type "square"
                     :duration 0.1
                     :gain-value 0.2))

;; ============================================================================
;; Musical Keyboard
;; ============================================================================

(def note-frequencies
  "Musical note frequencies in Hz (C2-C6 chromatic scale)"
  {;; Bass notes (C2-C3)
   :C2 65.41
   :C#2 69.30
   :D2 73.42
   :D#2 77.78
   :E2 82.41
   :F2 87.31
   :F#2 92.50
   :G2 98.00
   :G#2 103.83
   :A2 110.00
   :A#2 116.54
   :B2 123.47
   :C3 130.81

   ;; Mid-range notes (C4-C5)
   :C4 261.63
   :C#4 277.18
   :D4 293.66
   :D#4 311.13
   :E4 329.63
   :F4 349.23
   :F#4 369.99
   :G4 392.00
   :G#4 415.30
   :A4 440.00
   :A#4 466.16
   :B4 493.88
   :C5 523.25

   ;; Lead notes (C5-C6)
   :C#5 554.37
   :D5 587.33
   :D#5 622.25
   :E5 659.25
   :F5 698.46
   :F#5 739.99
   :G5 783.99
   :G#5 830.61
   :A5 880.00
   :A#5 932.33
   :B5 987.77
   :C6 1046.50})

(defn play-note
  "Plays a musical note
   Options:
   - :note - Note keyword (e.g., :C4, :A4)
   - :duration - Duration in seconds (default: 0.2)
   - :type - Waveform type (default: sine)"
  [& {:keys [note duration type]
      :or {duration 0.2
           type "sine"}}]
  (init-audio-context!)
  (when-let [frequency (get note-frequencies note)]
    (create-oscillator :frequency frequency
                       :type type
                       :duration duration)))

(defn play-melody
  "Plays a sequence of notes
   Each note is a vector: [note-keyword duration]"
  [& {:keys [note-sequence delay-between]
      :or {delay-between 250}}]
  (init-audio-context!)
  (doseq [[idx [note duration]] (map-indexed vector note-sequence)]
    (js/setTimeout
     #(play-note :note note :duration (or duration 0.2))
     (* idx delay-between))))

;; ============================================================================
;; Volume Control
;; ============================================================================

(defn set-master-volume
  "Sets the master volume (0-100)"
  [& {:keys [volume]}]
  (when @master-gain
    (let [gain-value (/ volume 100)]
      (set! (.-value (.-gain @master-gain)) gain-value))))

(defn get-master-volume
  "Gets the current master volume (0-100)"
  []
  (if @master-gain
    (* 100 (.-value (.-gain @master-gain)))
    50))

;; ============================================================================
;; Tone Generator (Continuous Tones)
;; ============================================================================

(defn start-continuous-tone
  "Starts a continuous tone that plays until stopped
   Returns an oscillator ID for stopping later
   Options:
   - :frequency - Frequency in Hz
   - :type - Waveform type
   - :gain-value - Volume (default: 0.2)"
  [& {:keys [frequency type gain-value]
      :or {gain-value 0.2}}]
  (init-audio-context!)
  (when @audio-context
    (try
      (let [osc (.createOscillator @audio-context)
            gain (.createGain @audio-context)
            osc-id (str (random-uuid))]
        (set! (.-type osc) type)
        (set! (.-value (.-frequency osc)) frequency)
        (.connect osc gain)
        (.connect gain @master-gain)
        (set! (.-value (.-gain gain)) gain-value)
        (.start osc)
        (swap! active-oscillators assoc osc-id {:osc osc :gain gain})
        osc-id)
      (catch js/Error e
        (js/console.error "Error starting continuous tone:" e)
        nil))))

(defn stop-continuous-tone
  "Stops a continuous tone by its ID"
  [& {:keys [osc-id]}]
  (when-let [{:keys [osc gain]} (get @active-oscillators osc-id)]
    (let [now (.-currentTime @audio-context)]
      (.exponentialRampToValueAtTime (.-gain gain) 0.01 (+ now 0.1))
      (.stop osc (+ now 0.1))
      (swap! active-oscillators dissoc osc-id))))

(defn stop-all-continuous-tones
  "Stops all active continuous tones"
  []
  (doseq [[id _] @active-oscillators]
    (stop-continuous-tone :osc-id id)))

;; ============================================================================
;; Drum Synthesis
;; ============================================================================

(defn play-kick
  "Synthesized kick drum sound using pitch envelope"
  []
  (init-audio-context!)
  (when @audio-context
    (try
      (let [osc (.createOscillator @audio-context)
            gain (.createGain @audio-context)
            now (.-currentTime @audio-context)]
        (set! (.-type osc) "sine")
        (.connect osc gain)
        (.connect gain @master-gain)

        ;; Pitch envelope (150Hz -> 50Hz)
        (set! (.-value (.-frequency osc)) 150)
        (.exponentialRampToValueAtTime (.-frequency osc) 50 (+ now 0.1))

        ;; Amplitude envelope
        (set! (.-value (.-gain gain)) 1)
        (.exponentialRampToValueAtTime (.-gain gain) 0.01 (+ now 0.5))

        (.start osc now)
        (.stop osc (+ now 0.5)))
      (catch js/Error e
        (js/console.error "Error playing kick:" e)))))

(defn play-snare
  "Synthesized snare drum using filtered white noise"
  []
  (init-audio-context!)
  (when @audio-context
    (try
      (let [noise-buffer (create-noise-buffer :duration 0.1)
            source (.createBufferSource @audio-context)
            filter (.createBiquadFilter @audio-context)
            gain (.createGain @audio-context)
            now (.-currentTime @audio-context)]
        (set! (.-buffer source) noise-buffer)
        (set! (.-type filter) "highpass")
        (set! (.-value (.-frequency filter)) 1000)

        (.connect source filter)
        (.connect filter gain)
        (.connect gain @master-gain)

        (set! (.-value (.-gain gain)) 0.5)
        (.exponentialRampToValueAtTime (.-gain gain) 0.01 (+ now 0.1))

        (.start source now)
        (.stop source (+ now 0.1)))
      (catch js/Error e
        (js/console.error "Error playing snare:" e)))))

(defn play-hihat
  "Synthesized hi-hat using high-pass filtered white noise"
  []
  (init-audio-context!)
  (when @audio-context
    (try
      (let [noise-buffer (create-noise-buffer :duration 0.05)
            source (.createBufferSource @audio-context)
            filter (.createBiquadFilter @audio-context)
            gain (.createGain @audio-context)
            now (.-currentTime @audio-context)]
        (set! (.-buffer source) noise-buffer)
        (set! (.-type filter) "highpass")
        (set! (.-value (.-frequency filter)) 8000)

        (.connect source filter)
        (.connect filter gain)
        (.connect gain @master-gain)

        (set! (.-value (.-gain gain)) 0.3)
        (.exponentialRampToValueAtTime (.-gain gain) 0.01 (+ now 0.05))

        (.start source now)
        (.stop source (+ now 0.05)))
      (catch js/Error e
        (js/console.error "Error playing hi-hat:" e)))))

;; ============================================================================
;; Beat Sequencer
;; ============================================================================

(def sequencer-state
  "Sequencer state for 16-step beat patterns"
  (r/atom {:playing false
           :bpm 120
           :current-step 0
           :pattern {:kick [1 0 0 0 1 0 0 0 1 0 0 0 1 0 0 0]
                     :snare [0 0 0 0 1 0 0 0 0 0 0 0 1 0 0 0]
                     :hihat [1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0]}
           :interval-id nil}))

(defn play-sequencer-step
  "Plays the current step in the sequencer"
  []
  (let [{:keys [current-step pattern]} @sequencer-state]
    ;; Play active drums for this step
    (when (nth (:kick pattern) current-step)
      (play-kick))
    (when (nth (:snare pattern) current-step)
      (play-snare))
    (when (nth (:hihat pattern) current-step)
      (play-hihat))
    ;; Advance to next step
    (swap! sequencer-state update :current-step #(mod (inc %) 16))))

(defn start-sequencer
  "Starts the beat sequencer"
  []
  (init-audio-context!)
  (when-not (:playing @sequencer-state)
    (let [bpm (:bpm @sequencer-state)
          interval-ms (/ 60000 (* bpm 4))
          interval-id (js/setInterval play-sequencer-step interval-ms)]
      (swap! sequencer-state assoc
             :playing true
             :interval-id interval-id))))

(defn stop-sequencer
  "Stops the beat sequencer"
  []
  (when-let [interval-id (:interval-id @sequencer-state)]
    (js/clearInterval interval-id)
    (swap! sequencer-state assoc
           :playing false
           :interval-id nil
           :current-step 0)))

(defn toggle-sequencer-step
  "Toggles a step in the pattern
   Options:
   - :track - Keyword for track (:kick, :snare, :hihat)
   - :step - Step index (0-15)"
  [& {:keys [track step]}]
  (swap! sequencer-state update-in [:pattern track step] not))

(defn set-sequencer-bpm
  "Sets the sequencer BPM and restarts if playing"
  [& {:keys [bpm]}]
  (let [was-playing (:playing @sequencer-state)]
    (when was-playing (stop-sequencer))
    (swap! sequencer-state assoc :bpm bpm)
    (when was-playing (start-sequencer))))

;; ============================================================================
;; Synthesizer Extensions - Bass, Lead, and Chord Pads
;; ============================================================================

(defn play-bass-note
  "Synthesized bass note with portamento (pitch glide)
   Options:
   - :frequency - Target frequency in Hz (default: 65Hz, C2)
   - :start-frequency - Starting frequency for portamento (default: same as :frequency)
   - :portamento-time - Glide time in seconds (default: 0.05)
   - :duration - Note duration in seconds (default: 0.5)
   - :waveform - Waveform type: sawtooth or square (default: sawtooth)
   - :release - Release time in seconds (default: 0.3)"
  [& {:keys [frequency start-frequency portamento-time duration waveform release]
      :or {frequency 65
           portamento-time 0.05
           duration 0.5
           waveform "sawtooth"
           release 0.3}}]
  (init-audio-context!)
  (when @audio-context
    (try
      (let [osc (.createOscillator @audio-context)
            gain (.createGain @audio-context)
            now (.-currentTime @audio-context)
            start-freq (or start-frequency frequency)
            stop-time (+ now duration release)]
        ;; Configure oscillator
        (set! (.-type osc) waveform)
        (set! (.-value (.-frequency osc)) start-freq)

        ;; Apply portamento (smooth pitch glide)
        (when (not= start-freq frequency)
          (.exponentialRampToValueAtTime (.-frequency osc) frequency (+ now portamento-time)))

        ;; Connect audio nodes
        (.connect osc gain)
        (.connect gain @master-gain)

        ;; ADSR envelope with longer release for bass
        (set! (.-value (.-gain gain)) 0)
        (.linearRampToValueAtTime (.-gain gain) 0.6 (+ now 0.02)) ; Quick attack
        (.exponentialRampToValueAtTime (.-gain gain) 0.01 stop-time) ; Long release

        ;; Start and schedule stop
        (.start osc now)
        (.stop osc stop-time)

        {:oscillator osc :gain gain :frequency frequency})
      (catch js/Error e
        (js/console.error "Error playing bass note:" e)
        nil))))

(defn play-lead-note
  "Synthesized lead note with LFO vibrato and filter
   Options:
   - :frequency - Note frequency in Hz (default: 523Hz, C5)
   - :duration - Note duration in seconds (default: 0.3)
   - :waveform - Waveform type: sawtooth or square (default: sawtooth)
   - :vibrato-rate - Vibrato speed in Hz (default: 6)
   - :vibrato-depth - Vibrato depth in Hz (default: 10)
   - :filter-cutoff - Filter cutoff frequency in Hz (default: 2000)"
  [& {:keys [frequency duration waveform vibrato-rate vibrato-depth filter-cutoff]
      :or {frequency 523
           duration 0.3
           waveform "sawtooth"
           vibrato-rate 6
           vibrato-depth 10
           filter-cutoff 2000}}]
  (init-audio-context!)
  (when @audio-context
    (try
      (let [osc (.createOscillator @audio-context)
            lfo (.createOscillator @audio-context)
            lfo-gain (.createGain @audio-context)
            filter (.createBiquadFilter @audio-context)
            gain (.createGain @audio-context)
            now (.-currentTime @audio-context)
            stop-time (+ now duration 0.1)]

        ;; Configure main oscillator
        (set! (.-type osc) waveform)
        (set! (.-value (.-frequency osc)) frequency)

        ;; Configure LFO for vibrato
        (set! (.-type lfo) "sine")
        (set! (.-value (.-frequency lfo)) vibrato-rate)
        (set! (.-value (.-gain lfo-gain)) vibrato-depth)

        ;; Connect LFO to oscillator frequency (creates vibrato)
        (.connect lfo lfo-gain)
        (.connect lfo-gain (.-frequency osc))

        ;; Configure lowpass filter
        (set! (.-type filter) "lowpass")
        (set! (.-value (.-frequency filter)) filter-cutoff)
        (set! (.-value (.-Q filter)) 1)

        ;; Connect audio graph
        (.connect osc filter)
        (.connect filter gain)
        (.connect gain @master-gain)

        ;; ADSR envelope
        (set! (.-value (.-gain gain)) 0)
        (.linearRampToValueAtTime (.-gain gain) 0.4 (+ now 0.01)) ; Fast attack
        (.exponentialRampToValueAtTime (.-gain gain) 0.01 stop-time) ; Short release

        ;; Start oscillators
        (.start osc now)
        (.start lfo now)

        ;; Schedule stop
        (.stop osc stop-time)
        (.stop lfo stop-time)

        {:oscillator osc :lfo lfo :filter filter :gain gain})
      (catch js/Error e
        (js/console.error "Error playing lead note:" e)
        nil))))

(defn play-chord-pad
  "Synthesized chord pad with multiple detuned oscillators
   Options:
   - :frequencies - Vector of frequencies for the chord (default: [261.63 329.63 392.00], C major)
   - :duration - Chord duration in seconds (default: 2.0)
   - :attack - Attack time in seconds (default: 0.3)
   - :release - Release time in seconds (default: 0.8)
   - :detune-amount - Detuning in cents (default: 8)
   - :gain-value - Overall volume (default: 0.15)"
  [& {:keys [frequencies duration attack release detune-amount gain-value]
      :or {frequencies [261.63 329.63 392.00] ; C major chord (C-E-G)
           duration 2.0
           attack 0.3
           release 0.8
           detune-amount 8
           gain-value 0.15}}]
  (init-audio-context!)
  (when @audio-context
    (try
      (let [now (.-currentTime @audio-context)
            stop-time (+ now duration release)
            oscillators (atom [])]

        ;; Create multiple oscillators for richness
        (doseq [freq frequencies]
          (let [osc (.createOscillator @audio-context)
                gain (.createGain @audio-context)]

            ;; Configure oscillator with detuning for richness
            (set! (.-type osc) "sine")
            (set! (.-value (.-frequency osc)) freq)
            (set! (.-value (.-detune osc))
                  (- (rand-int (* 2 detune-amount)) detune-amount))

            ;; Connect to gain node
            (.connect osc gain)
            (.connect gain @master-gain)

            ;; Apply slow attack/release envelope (pad characteristic)
            (set! (.-value (.-gain gain)) 0)
            (.linearRampToValueAtTime (.-gain gain) gain-value (+ now attack))
            (.exponentialRampToValueAtTime (.-gain gain) 0.01 stop-time)

            ;; Start and schedule stop
            (.start osc now)
            (.stop osc stop-time)

            (swap! oscillators conj {:oscillator osc :gain gain})))

        @oscillators)
      (catch js/Error e
        (js/console.error "Error playing chord pad:" e)
        nil))))

;; ============================================================================
;; UI Components
;; ============================================================================

(defn sound-effect-button
  "Button component for sound effects"
  [& {:keys [label icon on-click on-hover color]}]
  [:button {:on-click on-click
            :on-mouse-enter (when on-hover on-hover)
            :style (merge-styles
                    {:padding "15px"
                     :background (or color "#4caf50")
                     :color "white"
                     :border "none"
                     :border-radius "8px"
                     :cursor "pointer"
                     :font-size "14px"
                     :font-weight "600"
                     :transition "all 0.2s ease"
                     :display "flex"
                     :flex-direction "column"
                     :align-items "center"
                     :gap "5px"
                     :min-width "120px"})}
   [:span {:style {:font-size "28px"}} icon]
   label])

(defn piano-key
  "Piano key component"
  [& {:keys [note label on-play color]}]
  [:button {:on-click #(when on-play (on-play :note note))
            :style (merge-styles
                    {:width "50px"
                     :height "120px"
                     :background (or color "white")
                     :color (if (= color "white") "black" "white")
                     :border "1px solid #333"
                     :cursor "pointer"
                     :font-weight "bold"
                     :font-size "14px"
                     :transition "all 0.1s ease"
                     :border-radius "0 0 4px 4px"})
            :on-mouse-down (fn [e]
                             (when on-play
                               (set! (.-style.transform (.-currentTarget e)) "scale(0.95)")
                               (set! (.-style.boxShadow (.-currentTarget e))
                                     "inset 0 2px 4px rgba(0,0,0,0.2)")
                               (on-play :note note)))
            :on-mouse-up (fn [e]
                           (set! (.-style.transform (.-currentTarget e)) "scale(1)")
                           (set! (.-style.boxShadow (.-currentTarget e)) "none"))}
   label])

(defn bass-key
  "Bass synthesizer key component (darker styling for low notes)"
  [& {:keys [note label waveform]}]
  (let [frequency (get note-frequencies note)]
    [:button {:on-click #(when frequency
                           (play-bass-note :frequency frequency
                                           :waveform waveform))
              :style {:width "50px"
                      :height "120px"
                      :background "#2c3e50" ; Dark blue-grey for bass
                      :color "white"
                      :border "1px solid #1a252f"
                      :cursor "pointer"
                      :font-weight "bold"
                      :font-size "12px"
                      :transition "all 0.1s ease"
                      :border-radius "0 0 4px 4px"}
              :on-mouse-down (fn [e]
                               (when frequency
                                 (set! (.-style.transform (.-currentTarget e)) "scale(0.95)")
                                 (set! (.-style.boxShadow (.-currentTarget e))
                                       "inset 0 2px 6px rgba(0,0,0,0.4)")
                                 (play-bass-note :frequency frequency
                                                 :waveform waveform)))
              :on-mouse-up (fn [e]
                             (set! (.-style.transform (.-currentTarget e)) "scale(1)")
                             (set! (.-style.boxShadow (.-currentTarget e)) "none"))}
     label]))

(defn lead-key
  "Lead synthesizer key component (brighter styling for high notes)"
  [& {:keys [note label vibrato-rate vibrato-depth filter-cutoff]}]
  (let [frequency (get note-frequencies note)]
    [:button {:on-click #(when frequency
                           (play-lead-note :frequency frequency
                                           :vibrato-rate vibrato-rate
                                           :vibrato-depth vibrato-depth
                                           :filter-cutoff filter-cutoff))
              :style {:width "50px"
                      :height "120px"
                      :background "#f39c12" ; Bright orange for lead
                      :color "white"
                      :border "1px solid #e67e22"
                      :cursor "pointer"
                      :font-weight "bold"
                      :font-size "12px"
                      :transition "all 0.1s ease"
                      :border-radius "0 0 4px 4px"}
              :on-mouse-down (fn [e]
                               (when frequency
                                 (set! (.-style.transform (.-currentTarget e)) "scale(0.95)")
                                 (set! (.-style.boxShadow (.-currentTarget e))
                                       "inset 0 2px 4px rgba(0,0,0,0.2)")
                                 (play-lead-note :frequency frequency
                                                 :vibrato-rate vibrato-rate
                                                 :vibrato-depth vibrato-depth
                                                 :filter-cutoff filter-cutoff)))
              :on-mouse-up (fn [e]
                             (set! (.-style.transform (.-currentTarget e)) "scale(1)")
                             (set! (.-style.boxShadow (.-currentTarget e)) "none"))}
     label]))

(defn chord-button
  "Chord pad button component (large ambient styling)"
  [& {:keys [label frequencies attack release]}]
  [:button {:on-click #(when frequencies
                         (play-chord-pad :frequencies frequencies
                                         :attack attack
                                         :release release))
            :style {:padding "20px 30px"
                    :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                    :color "white"
                    :border "none"
                    :border-radius "8px"
                    :cursor "pointer"
                    :font-weight "600"
                    :font-size "16px"
                    :transition "all 0.2s ease"
                    :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}
            :on-mouse-enter (fn [e]
                              (set! (.-style.transform (.-currentTarget e)) "translateY(-2px)")
                              (set! (.-style.boxShadow (.-currentTarget e))
                                    "0 6px 12px rgba(0,0,0,0.15)"))
            :on-mouse-leave (fn [e]
                              (set! (.-style.transform (.-currentTarget e)) "translateY(0)")
                              (set! (.-style.boxShadow (.-currentTarget e))
                                    "0 4px 6px rgba(0,0,0,0.1)"))
            :on-mouse-down (fn [e]
                             (when frequencies
                               (set! (.-style.transform (.-currentTarget e)) "translateY(0) scale(0.98)")
                               (play-chord-pad :frequencies frequencies
                                               :attack attack
                                               :release release)))
            :on-mouse-up (fn [e]
                           (set! (.-style.transform (.-currentTarget e)) "translateY(-2px) scale(1)"))}
   label])

(defn slider-control
  "Reusable slider control with label and value display"
  [& {:keys [label value min max step on-change unit]
      :or {step 1 unit ""}}]
  [:div {:style {:display "flex"
                 :flex-direction "column"
                 :gap "8px"
                 :flex "1"}}
   [:div {:style {:display "flex"
                  :justify-content "space-between"
                  :align-items "center"}}
    [:label {:style {:font-weight "600"
                     :font-size "13px"
                     :color "#555"}}
     label]
    [:span {:style {:font-weight "bold"
                    :font-size "13px"
                    :color "#333"
                    :background "#f0f0f0"
                    :padding "2px 8px"
                    :border-radius "4px"}}
     (str value unit)]]
   [:input {:type "range"
            :min min
            :max max
            :step step
            :value value
            :on-change on-change
            :style {:width "100%"
                    :height "6px"
                    :border-radius "3px"
                    :cursor "pointer"}}]])

(defn waveform-selector
  "Waveform selector button group"
  [& {:keys [current-waveform on-change options]
      :or {options ["sine" "square" "sawtooth" "triangle"]}}]
  [:div {:style {:display "flex"
                 :gap "8px"
                 :flex-wrap "wrap"}}
   (for [waveform options]
     ^{:key waveform}
     [:button {:on-click #(when on-change (on-change waveform))
               :style {:padding "8px 16px"
                       :background (if (= waveform current-waveform)
                                     "#4caf50"
                                     "#e0e0e0")
                       :color (if (= waveform current-waveform)
                                "white"
                                "#333")
                       :border "none"
                       :border-radius "6px"
                       :cursor "pointer"
                       :font-weight (if (= waveform current-waveform)
                                      "600"
                                      "500")
                       :font-size "13px"
                       :transition "all 0.2s"
                       :text-transform "capitalize"}}
      waveform])])

(defn volume-control
  "Master volume control component"
  [& {:keys [volume on-change]}]
  [:div {:style {:display "flex"
                 :align-items "center"
                 :gap "20px"
                 :padding "20px"
                 :background "white"
                 :border-radius "8px"
                 :box-shadow "0 2px 8px rgba(0,0,0,0.1)"}}
   [:label {:style {:font-weight "600"
                    :min-width "100px"
                    :color "#333"}}
    "🔊 Volume:"]
   [:input {:type "range"
            :min 0
            :max 100
            :value volume
            :on-change (fn [e]
                         (let [val (js/parseInt (.-value (.-target e)))]
                           (when on-change (on-change :volume val))))
            :style {:flex 1
                    :height "8px"
                    :border-radius "4px"}}]
   [:span {:style {:font-weight "bold"
                   :min-width "50px"
                   :text-align "right"
                   :color "#333"}}
    (str volume "%")]])

;; ============================================================================
;; Main Component
;; ============================================================================

(defn audio-playground
  "Main audio playground component"
  []
  (let [volume-atom (r/atom (get-master-volume))
        active-tone (r/atom nil)
        ;; Bass synthesizer parameters
        bass-waveform (r/atom "sawtooth")
        ;; Lead synthesizer parameters
        lead-vibrato-rate (r/atom 6)
        lead-vibrato-depth (r/atom 10)
        lead-filter-cutoff (r/atom 2000)
        ;; Chord pad parameters
        chord-attack (r/atom 0.3)
        chord-release (r/atom 0.8)]
    (fn []
      [:div {:style {:padding "20px"
                     :max-width "1200px"
                     :margin "0 auto"
                     :font-family "system-ui, -apple-system, sans-serif"
                     :background "#f5f5f5"
                     :min-height "100vh"}}

       ;; Title
       [:h2 {:style {:text-align "center"
                     :color "#4caf50"
                     :margin-bottom "10px"
                     :font-size "32px"}}
        "🎵 Web Audio API Playground"]

       [:p {:style {:text-align "center"
                    :color "#666"
                    :margin-bottom "30px"}}
        "Interactive sound synthesis and audio effects"]

       ;; Volume Control
       [:div {:style {:margin-bottom "30px"}}
        [volume-control
         :volume @volume-atom
         :on-change (fn [& {:keys [volume]}]
                      (reset! volume-atom volume)
                      (set-master-volume :volume volume))]]

       ;; Sound Effects Library
       [:div {:style {:background "white"
                      :padding "30px"
                      :border-radius "12px"
                      :box-shadow "0 2px 8px rgba(0,0,0,0.1)"
                      :margin-bottom "30px"}}
        [:h3 {:style {:margin-bottom "20px"
                      :color "#333"}}
         "🔔 Sound Effects Library"]
        [:div {:style {:display "grid"
                       :grid-template-columns "repeat(auto-fit, minmax(140px, 1fr))"
                       :gap "15px"}}
         [sound-effect-button :label "Click" :icon "👆" :on-click play-click :color "#2196f3"]
         [sound-effect-button :label "Success" :icon "✅" :on-click play-success :color "#4caf50"]
         [sound-effect-button :label "Error" :icon "❌" :on-click play-error :color "#f44336"]
         [sound-effect-button :label "Notification" :icon "🔔" :on-click play-notification :color "#ff9800"]
         [sound-effect-button :label "Hover" :icon "🎯" :on-click play-hover
          :on-hover play-hover :color "#9c27b0"]
         [sound-effect-button :label "Complete" :icon "🎉" :on-click play-complete :color "#00bcd4"]
         [sound-effect-button :label "Delete" :icon "🗑️" :on-click play-delete :color "#795548"]]]

       ;; Musical Keyboard
       [:div {:style {:background "white"
                      :padding "30px"
                      :border-radius "12px"
                      :box-shadow "0 2px 8px rgba(0,0,0,0.1)"
                      :margin-bottom "30px"}}
        [:h3 {:style {:margin-bottom "20px"
                      :color "#333"}}
         "🎹 Musical Keyboard (C4-C5)"]

        ;; White keys
        [:div {:style {:display "flex"
                       :gap "2px"
                       :justify-content "center"
                       :margin-bottom "15px"}}
         [piano-key :note :C4 :label "C" :on-play play-note]
         [piano-key :note :D4 :label "D" :on-play play-note]
         [piano-key :note :E4 :label "E" :on-play play-note]
         [piano-key :note :F4 :label "F" :on-play play-note]
         [piano-key :note :G4 :label "G" :on-play play-note]
         [piano-key :note :A4 :label "A" :on-play play-note]
         [piano-key :note :B4 :label "B" :on-play play-note]
         [piano-key :note :C5 :label "C5" :on-play play-note]]

        ;; Play melody buttons
        [:div {:style {:display "flex"
                       :gap "10px"
                       :justify-content "center"
                       :flex-wrap "wrap"}}
         [:button {:on-click #(play-melody :note-sequence [[:C4 0.2] [:E4 0.2]
                                                           [:G4 0.2] [:C5 0.4]])
                   :style {:padding "10px 20px"
                           :background "#4caf50"
                           :color "white"
                           :border "none"
                           :border-radius "6px"
                           :cursor "pointer"
                           :font-weight "600"
                           :transition "all 0.2s"}}
          "Play C Major Chord"]
         [:button {:on-click #(play-melody :note-sequence [[:C4] [:D4] [:E4] [:F4]
                                                           [:G4] [:A4] [:B4] [:C5]])
                   :style {:padding "10px 20px"
                           :background "#2196f3"
                           :color "white"
                           :border "none"
                           :border-radius "6px"
                           :cursor "pointer"
                           :font-weight "600"
                           :transition "all 0.2s"}}
          "Play C Major Scale"]]]

       ;; Bass Synthesizer
       [:div {:style {:background "white"
                      :padding "30px"
                      :border-radius "12px"
                      :box-shadow "0 2px 8px rgba(0,0,0,0.1)"
                      :margin-bottom "30px"}}
        [:h3 {:style {:margin-bottom "20px"
                      :color "#333"}}
         "🎸 Bass Synthesizer"]
        [:p {:style {:color "#666"
                     :margin-bottom "20px"
                     :font-size "14px"}}
         "Deep bass tones with portamento (pitch glide) and sawtooth waves"]

        ;; Bass keyboard (C2-C3 octave)
        [:div {:style {:display "flex"
                       :gap "2px"
                       :justify-content "center"
                       :margin-bottom "15px"}}
         [bass-key :note :C2 :label "C2" :waveform @bass-waveform]
         [bass-key :note :D2 :label "D2" :waveform @bass-waveform]
         [bass-key :note :E2 :label "E2" :waveform @bass-waveform]
         [bass-key :note :F2 :label "F2" :waveform @bass-waveform]
         [bass-key :note :G2 :label "G2" :waveform @bass-waveform]
         [bass-key :note :A2 :label "A2" :waveform @bass-waveform]
         [bass-key :note :B2 :label "B2" :waveform @bass-waveform]
         [bass-key :note :C3 :label "C3" :waveform @bass-waveform]]

        ;; Waveform selector
        [:div {:style {:margin-top "15px"
                       :margin-bottom "15px"}}
         [:label {:style {:display "block"
                          :font-weight "600"
                          :font-size "13px"
                          :color "#555"
                          :margin-bottom "8px"}}
          "Waveform"]
         [waveform-selector
          :current-waveform @bass-waveform
          :on-change #(reset! bass-waveform %)
          :options ["sawtooth" "square"]]]

        ;; Bass info
        [:div {:style {:display "flex"
                       :gap "10px"
                       :justify-content "center"
                       :align-items "center"
                       :margin-top "15px"
                       :padding "10px"
                       :background "#f8f9fa"
                       :border-radius "6px"}}
         [:span {:style {:color "#666"
                         :font-size "13px"}}
          "💡 Features: Sawtooth waveform • 50ms portamento • 300ms release envelope"]]]

       ;; Tone Generator
       [:div {:style {:background "white"
                      :padding "30px"
                      :border-radius "12px"
                      :box-shadow "0 2px 8px rgba(0,0,0,0.1)"
                      :margin-bottom "30px"}}
        [:h3 {:style {:margin-bottom "20px"
                      :color "#333"}}
         "🎛️ Tone Generator"]
        [:p {:style {:color "#666"
                     :margin-bottom "20px"
                     :font-size "14px"}}
         "Generate continuous tones with different waveforms and frequencies"]

        ;; Tone controls grid
        [:div {:style {:display "grid"
                       :grid-template-columns "repeat(auto-fit, minmax(140px, 1fr))"
                       :gap "15px"
                       :margin-bottom "20px"}}
         ;; Sine waves at different frequencies
         (for [[freq label color] [[440 "440Hz Sine" "#4caf50"]
                                   [880 "880Hz Sine" "#8bc34a"]]]
           ^{:key (str freq "-sine")}
           [:button {:on-click (fn []
                                 (if @active-tone
                                   (do (stop-continuous-tone :osc-id @active-tone)
                                       (reset! active-tone nil))
                                   (reset! active-tone
                                           (start-continuous-tone :frequency freq
                                                                  :type "sine"))))
                     :style {:padding "15px"
                             :background (if (and @active-tone
                                                  (= label (str freq "Hz Sine")))
                                           "#e74c3c"
                                           color)
                             :color "white"
                             :border "none"
                             :border-radius "8px"
                             :cursor "pointer"
                             :font-weight "600"
                             :transition "all 0.2s"}}
            label])

         ;; Different waveforms
         (for [[freq type label color] [[220 "square" "220Hz Square" "#ff9800"]
                                        [330 "sawtooth" "330Hz Sawtooth" "#2196f3"]
                                        [660 "triangle" "660Hz Triangle" "#9c27b0"]]]
           ^{:key (str freq "-" type)}
           [:button {:on-click (fn []
                                 (if @active-tone
                                   (do (stop-continuous-tone :osc-id @active-tone)
                                       (reset! active-tone nil))
                                   (reset! active-tone
                                           (start-continuous-tone :frequency freq
                                                                  :type type))))
                     :style {:padding "15px"
                             :background (if (and @active-tone
                                                  (= label (str freq "Hz "
                                                                (str/capitalize type))))
                                           "#e74c3c"
                                           color)
                             :color "white"
                             :border "none"
                             :border-radius "8px"
                             :cursor "pointer"
                             :font-weight "600"
                             :transition "all 0.2s"}}
            label])]

        ;; Stop all button
        (when @active-tone
          [:button {:on-click (fn []
                                (stop-all-continuous-tones)
                                (reset! active-tone nil))
                    :style {:width "100%"
                            :padding "12px"
                            :background "#e74c3c"
                            :color "white"
                            :border "none"
                            :border-radius "8px"
                            :cursor "pointer"
                            :font-weight "600"
                            :font-size "16px"
                            :transition "all 0.2s"}}
           "⏹ Stop All Tones"])]

       ;; Lead Synthesizer
       [:div {:style {:background "white"
                      :padding "30px"
                      :border-radius "12px"
                      :box-shadow "0 2px 8px rgba(0,0,0,0.1)"
                      :margin-bottom "30px"}}
        [:h3 {:style {:margin-bottom "20px"
                      :color "#333"}}
         "🎺 Lead Synthesizer"]
        [:p {:style {:color "#666"
                     :margin-bottom "20px"
                     :font-size "14px"}}
         "High octave leads with vibrato (LFO modulation) and lowpass filter"]

        ;; Lead keyboard (C5-C6 octave)
        [:div {:style {:display "flex"
                       :gap "2px"
                       :justify-content "center"
                       :margin-bottom "15px"}}
         [lead-key :note :C5 :label "C5" :vibrato-rate @lead-vibrato-rate :vibrato-depth @lead-vibrato-depth :filter-cutoff @lead-filter-cutoff]
         [lead-key :note :D5 :label "D5" :vibrato-rate @lead-vibrato-rate :vibrato-depth @lead-vibrato-depth :filter-cutoff @lead-filter-cutoff]
         [lead-key :note :E5 :label "E5" :vibrato-rate @lead-vibrato-rate :vibrato-depth @lead-vibrato-depth :filter-cutoff @lead-filter-cutoff]
         [lead-key :note :F5 :label "F5" :vibrato-rate @lead-vibrato-rate :vibrato-depth @lead-vibrato-depth :filter-cutoff @lead-filter-cutoff]
         [lead-key :note :G5 :label "G5" :vibrato-rate @lead-vibrato-rate :vibrato-depth @lead-vibrato-depth :filter-cutoff @lead-filter-cutoff]
         [lead-key :note :A5 :label "A5" :vibrato-rate @lead-vibrato-rate :vibrato-depth @lead-vibrato-depth :filter-cutoff @lead-filter-cutoff]
         [lead-key :note :B5 :label "B5" :vibrato-rate @lead-vibrato-rate :vibrato-depth @lead-vibrato-depth :filter-cutoff @lead-filter-cutoff]
         [lead-key :note :C6 :label "C6" :vibrato-rate @lead-vibrato-rate :vibrato-depth @lead-vibrato-depth :filter-cutoff @lead-filter-cutoff]]

        ;; Controls
        [:div {:style {:display "flex"
                       :gap "15px"
                       :margin-top "15px"
                       :margin-bottom "15px"
                       :flex-wrap "wrap"}}
         [slider-control
          :label "Vibrato Rate"
          :value @lead-vibrato-rate
          :min 3
          :max 10
          :step 0.5
          :unit " Hz"
          :on-change #(reset! lead-vibrato-rate (js/parseFloat (.-value (.-target %))))]
         [slider-control
          :label "Vibrato Depth"
          :value @lead-vibrato-depth
          :min 5
          :max 20
          :step 1
          :unit " Hz"
          :on-change #(reset! lead-vibrato-depth (js/parseInt (.-value (.-target %))))]
         [slider-control
          :label "Filter Cutoff"
          :value @lead-filter-cutoff
          :min 500
          :max 5000
          :step 100
          :unit " Hz"
          :on-change #(reset! lead-filter-cutoff (js/parseInt (.-value (.-target %))))]]

        ;; Lead info
        [:div {:style {:display "flex"
                       :gap "10px"
                       :justify-content "center"
                       :align-items "center"
                       :margin-top "15px"
                       :padding "10px"
                       :background "#fff3e0"
                       :border-radius "6px"}}
         [:span {:style {:color "#e65100"
                         :font-size "13px"}}
          "💡 Features: 6Hz LFO vibrato • 2000Hz lowpass filter • Bright sawtooth wave"]]]

       ;; Chord Pad Synthesizer
       [:div {:style {:background "white"
                      :padding "30px"
                      :border-radius "12px"
                      :box-shadow "0 2px 8px rgba(0,0,0,0.1)"
                      :margin-bottom "30px"}}
        [:h3 {:style {:margin-bottom "20px"
                      :color "#333"}}
         "🎹 Chord Pad Synthesizer"]
        [:p {:style {:color "#666"
                     :margin-bottom "20px"
                     :font-size "14px"}}
         "Lush ambient chords with slow attack/release and detuned oscillators"]

        ;; Chord selector buttons
        [:div {:style {:display "grid"
                       :grid-template-columns "repeat(auto-fit, minmax(140px, 1fr))"
                       :gap "12px"
                       :margin-bottom "15px"}}
         [chord-button :label "C Major" :frequencies [261.63 329.63 392.00] :attack @chord-attack :release @chord-release]
         [chord-button :label "C Minor" :frequencies [261.63 311.13 392.00] :attack @chord-attack :release @chord-release]
         [chord-button :label "F Major" :frequencies [174.61 220.00 261.63] :attack @chord-attack :release @chord-release]
         [chord-button :label "G Major" :frequencies [196.00 246.94 293.66] :attack @chord-attack :release @chord-release]
         [chord-button :label "Am" :frequencies [220.00 261.63 329.63] :attack @chord-attack :release @chord-release]
         [chord-button :label "Dm" :frequencies [146.83 174.61 220.00] :attack @chord-attack :release @chord-release]
         [chord-button :label "Em" :frequencies [164.81 196.00 246.94] :attack @chord-attack :release @chord-release]
         [chord-button :label "G7" :frequencies [196.00 246.94 293.66 349.23] :attack @chord-attack :release @chord-release]]

        ;; Attack/Release controls
        [:div {:style {:display "flex"
                       :gap "15px"
                       :margin-top "15px"
                       :margin-bottom "15px"
                       :flex-wrap "wrap"}}
         [slider-control
          :label "Attack"
          :value @chord-attack
          :min 0.1
          :max 1.0
          :step 0.1
          :unit " s"
          :on-change #(reset! chord-attack (js/parseFloat (.-value (.-target %))))]
         [slider-control
          :label "Release"
          :value @chord-release
          :min 0.3
          :max 2.0
          :step 0.1
          :unit " s"
          :on-change #(reset! chord-release (js/parseFloat (.-value (.-target %))))]]

        ;; Chord info
        [:div {:style {:display "flex"
                       :gap "10px"
                       :justify-content "center"
                       :align-items "center"
                       :margin-top "15px"
                       :padding "10px"
                       :background "#f3e5f5"
                       :border-radius "6px"}}
         [:span {:style {:color "#6a1b9a"
                         :font-size "13px"}}
          "💡 Features: 300ms attack • 800ms release • ±8 cent detuning • Multiple oscillators"]]]

       ;; Drum Machine
       [:div {:style {:background "white"
                      :padding "30px"
                      :border-radius "12px"
                      :box-shadow "0 2px 8px rgba(0,0,0,0.1)"
                      :margin-bottom "30px"}}
        [:h3 {:style {:margin-bottom "20px"
                      :color "#333"}}
         "🥁 Drum Machine"]
        [:p {:style {:color "#666"
                     :margin-bottom "20px"
                     :font-size "14px"}}
         "Synthesized percussion sounds using oscillators and noise"]

        [:div {:style {:display "grid"
                       :grid-template-columns "repeat(auto-fit, minmax(150px, 1fr))"
                       :gap "20px"}}
         [:button {:on-click play-kick
                   :style {:padding "30px 20px"
                           :background "#e74c3c"
                           :color "white"
                           :border "none"
                           :border-radius "8px"
                           :cursor "pointer"
                           :font-size "18px"
                           :font-weight "700"
                           :transition "all 0.2s"
                           :display "flex"
                           :flex-direction "column"
                           :align-items "center"
                           :gap "10px"}
                   :on-mouse-enter (fn [e]
                                     (set! (.-style.transform (.-currentTarget e)) "scale(1.05)")
                                     (set! (.-style.boxShadow (.-currentTarget e))
                                           "0 4px 12px rgba(0,0,0,0.2)"))
                   :on-mouse-leave (fn [e]
                                     (set! (.-style.transform (.-currentTarget e)) "scale(1)")
                                     (set! (.-style.boxShadow (.-currentTarget e)) "none"))}
          [:span {:style {:font-size "32px"}} "🎺"]
          "KICK"]

         [:button {:on-click play-snare
                   :style {:padding "30px 20px"
                           :background "#3498db"
                           :color "white"
                           :border "none"
                           :border-radius "8px"
                           :cursor "pointer"
                           :font-size "18px"
                           :font-weight "700"
                           :transition "all 0.2s"
                           :display "flex"
                           :flex-direction "column"
                           :align-items "center"
                           :gap "10px"}
                   :on-mouse-enter (fn [e]
                                     (set! (.-style.transform (.-currentTarget e)) "scale(1.05)")
                                     (set! (.-style.boxShadow (.-currentTarget e))
                                           "0 4px 12px rgba(0,0,0,0.2)"))
                   :on-mouse-leave (fn [e]
                                     (set! (.-style.transform (.-currentTarget e)) "scale(1)")
                                     (set! (.-style.boxShadow (.-currentTarget e)) "none"))}
          [:span {:style {:font-size "32px"}} "🥁"]
          "SNARE"]

         [:button {:on-click play-hihat
                   :style {:padding "30px 20px"
                           :background "#f39c12"
                           :color "white"
                           :border "none"
                           :border-radius "8px"
                           :cursor "pointer"
                           :font-size "18px"
                           :font-weight "700"
                           :transition "all 0.2s"
                           :display "flex"
                           :flex-direction "column"
                           :align-items "center"
                           :gap "10px"}
                   :on-mouse-enter (fn [e]
                                     (set! (.-style.transform (.-currentTarget e)) "scale(1.05)")
                                     (set! (.-style.boxShadow (.-currentTarget e))
                                           "0 4px 12px rgba(0,0,0,0.2)"))
                   :on-mouse-leave (fn [e]
                                     (set! (.-style.transform (.-currentTarget e)) "scale(1)")
                                     (set! (.-style.boxShadow (.-currentTarget e)) "none"))}
          [:span {:style {:font-size "32px"}} "🔔"]
          "HI-HAT"]]]

       ;; Beat Sequencer
       [:div {:style {:background "white"
                      :padding "30px"
                      :border-radius "12px"
                      :box-shadow "0 2px 8px rgba(0,0,0,0.1)"
                      :margin-bottom "30px"}}
        [:h3 {:style {:margin-bottom "20px"
                      :color "#333"}}
         "🎼 Beat Sequencer"]
        [:p {:style {:color "#666"
                     :margin-bottom "20px"
                     :font-size "14px"}}
         "16-step pattern sequencer with adjustable BPM"]

        ;; Controls
        [:div {:style {:display "flex"
                       :gap "15px"
                       :margin-bottom "20px"
                       :flex-wrap "wrap"
                       :align-items "center"}}
         [:button {:on-click (fn []
                               (if (:playing @sequencer-state)
                                 (stop-sequencer)
                                 (start-sequencer)))
                   :style {:padding "12px 24px"
                           :background (if (:playing @sequencer-state) "#e74c3c" "#4caf50")
                           :color "white"
                           :border "none"
                           :border-radius "8px"
                           :cursor "pointer"
                           :font-weight "600"
                           :font-size "16px"
                           :transition "all 0.2s"}}
          (if (:playing @sequencer-state) "⏸ Stop" "▶ Play")]

         [:div {:style {:display "flex"
                        :align-items "center"
                        :gap "10px"}}
          [:label {:style {:font-weight "600"
                           :color "#333"}}
           "BPM:"]
          [:input {:type "number"
                   :min 60
                   :max 200
                   :value (:bpm @sequencer-state)
                   :on-change (fn [e]
                                (let [val (js/parseInt (.-value (.-target e)))]
                                  (when (and (>= val 60) (<= val 200))
                                    (set-sequencer-bpm :bpm val))))
                   :style {:width "70px"
                           :padding "8px"
                           :border "2px solid #ccc"
                           :border-radius "4px"
                           :font-size "16px"
                           :font-weight "600"
                           :color "#333"
                           :background "white"
                           :text-align "center"}}]]]

        ;; Pattern Grid
        [:div {:style {:display "grid"
                       :grid-template-columns "80px repeat(16, 1fr)"
                       :gap "3px"
                       :align-items "center"}}
         ;; Kick row
         [:div {:style {:font-weight "bold"
                        :color "#e74c3c"
                        :font-size "14px"}}
          "KICK"]
         (for [i (range 16)]
           ^{:key (str "kick-" i)}
           [:button {:on-click #(toggle-sequencer-step :track :kick :step i)
                     :style {:padding "12px 8px"
                             :background (if (nth (get-in @sequencer-state [:pattern :kick]) i)
                                           "#e74c3c"
                                           "#f0f0f0")
                             :color (if (nth (get-in @sequencer-state [:pattern :kick]) i)
                                      "white"
                                      "#999")
                             :border (if (= i (:current-step @sequencer-state))
                                       "2px solid #4caf50"
                                       "1px solid #ddd")
                             :border-radius "4px"
                             :cursor "pointer"
                             :font-size "12px"
                             :font-weight "bold"
                             :transition "all 0.1s"}}
            (if (= i (:current-step @sequencer-state)) "▶" (inc i))])

         ;; Snare row
         [:div {:style {:font-weight "bold"
                        :color "#3498db"
                        :font-size "14px"}}
          "SNARE"]
         (for [i (range 16)]
           ^{:key (str "snare-" i)}
           [:button {:on-click #(toggle-sequencer-step :track :snare :step i)
                     :style {:padding "12px 8px"
                             :background (if (nth (get-in @sequencer-state [:pattern :snare]) i)
                                           "#3498db"
                                           "#f0f0f0")
                             :color (if (nth (get-in @sequencer-state [:pattern :snare]) i)
                                      "white"
                                      "#999")
                             :border (if (= i (:current-step @sequencer-state))
                                       "2px solid #4caf50"
                                       "1px solid #ddd")
                             :border-radius "4px"
                             :cursor "pointer"
                             :font-size "12px"
                             :font-weight "bold"
                             :transition "all 0.1s"}}
            (if (= i (:current-step @sequencer-state)) "▶" (inc i))])

         ;; Hi-hat row
         [:div {:style {:font-weight "bold"
                        :color "#f39c12"
                        :font-size "14px"}}
          "HI-HAT"]
         (for [i (range 16)]
           ^{:key (str "hihat-" i)}
           [:button {:on-click #(toggle-sequencer-step :track :hihat :step i)
                     :style {:padding "12px 8px"
                             :background (if (nth (get-in @sequencer-state [:pattern :hihat]) i)
                                           "#f39c12"
                                           "#f0f0f0")
                             :color (if (nth (get-in @sequencer-state [:pattern :hihat]) i)
                                      "white"
                                      "#999")
                             :border (if (= i (:current-step @sequencer-state))
                                       "2px solid #4caf50"
                                       "1px solid #ddd")
                             :border-radius "4px"
                             :cursor "pointer"
                             :font-size "12px"
                             :font-weight "bold"
                             :transition "all 0.1s"}}
            (if (= i (:current-step @sequencer-state)) "▶" (inc i))])]]

       ;; Info Section
       [:div {:style {:background "#e3f2fd"
                      :padding "20px"
                      :border-radius "8px"
                      :border "1px solid #2196f3"}}
        [:h4 {:style {:margin-top "0"
                      :color "#1976d2"}}
         "ℹ️ About This Playground"]
        [:ul {:style {:margin "10px 0"
                      :padding-left "20px"
                      :line-height "1.8"
                      :color "#555"}}
         [:li "All sounds are generated using the Web Audio API"]
         [:li "No external audio files required - everything is synthesized in real-time"]
         [:li "Sound effects use oscillators with envelope shaping (ADSR)"]
         [:li "Musical keyboard covers C4-C5 chromatic scale"]
         [:li "Tone generator with multiple waveforms (sine, square, sawtooth, triangle)"]
         [:li "Drum machine with synthesized percussion (kick, snare, hi-hat)"]
         [:li "Beat sequencer with 16-step patterns and adjustable BPM"]
         [:li "Master volume control affects all sounds"]]]])))

;; Export main component
(def page audio-playground)

;; ============================================================================
;; Mount Point
;; ============================================================================

(defn ^:export mount-audio-playground
  "Mount the audio playground component to the DOM"
  []
  (when-let [el (js/document.getElementById "audio-playground-root")]
    (rdom/render [page] el)))

;; Auto-mount when script loads
(mount-audio-playground)
