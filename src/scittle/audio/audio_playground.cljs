(ns scittle.audio.audio-playground
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [clojure.string :as str]))

;; Cache buster: v1.1.0 - Audio Effects Update

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

;; Audio Effects Nodes
(defonce delay-node (atom nil))
(defonce delay-feedback-gain (atom nil))
(defonce delay-wet-gain (atom nil))
(defonce delay-dry-gain (atom nil))

(defonce reverb-nodes (atom nil))
(defonce reverb-wet-gain (atom nil))
(defonce reverb-dry-gain (atom nil))

(defn init-reverb!
  "Initialize simple algorithmic reverb using comb and allpass filters"
  []
  (when @audio-context
    (try
      (let [ctx @audio-context
            ;; Create comb filters (parallel delays with feedback)
            comb1 (.createDelay ctx 1.0)
            comb1-gain (.createGain ctx)
            comb2 (.createDelay ctx 1.0)
            comb2-gain (.createGain ctx)
            comb3 (.createDelay ctx 1.0)
            comb3-gain (.createGain ctx)
            comb4 (.createDelay ctx 1.0)
            comb4-gain (.createGain ctx)

            ;; Create allpass filters (for diffusion)
            allpass1 (.createDelay ctx 1.0)
            allpass1-gain (.createGain ctx)
            allpass2 (.createDelay ctx 1.0)
            allpass2-gain (.createGain ctx)

            ;; Output mixer
            reverb-mix (.createGain ctx)]

        ;; Setup comb filter delays (prime numbers for natural sound)
        (set! (.-value (.-delayTime comb1)) 0.0297)
        (set! (.-value (.-delayTime comb2)) 0.0371)
        (set! (.-value (.-delayTime comb3)) 0.0411)
        (set! (.-value (.-delayTime comb4)) 0.0437)

        ;; Comb filter feedback (50%)
        (set! (.-value (.-gain comb1-gain)) 0.5)
        (set! (.-value (.-gain comb2-gain)) 0.5)
        (set! (.-value (.-gain comb3-gain)) 0.5)
        (set! (.-value (.-gain comb4-gain)) 0.5)

        ;; Setup allpass delays
        (set! (.-value (.-delayTime allpass1)) 0.005)
        (set! (.-value (.-delayTime allpass2)) 0.0017)
        (set! (.-value (.-gain allpass1-gain)) 0.7)
        (set! (.-value (.-gain allpass2-gain)) 0.7)

        ;; Connect comb filters in parallel
        (doseq [[delay gain] [[comb1 comb1-gain]
                              [comb2 comb2-gain]
                              [comb3 comb3-gain]
                              [comb4 comb4-gain]]]
          (.connect @reverb-wet-gain delay)
          (.connect delay gain)
          (.connect gain delay)
          (.connect delay reverb-mix))

        ;; Connect allpass filters in series
        (.connect reverb-mix allpass1)
        (.connect allpass1 allpass1-gain)
        (.connect allpass1-gain allpass1)
        (.connect allpass1 allpass2)
        (.connect allpass2 allpass2-gain)
        (.connect allpass2-gain allpass2)
        (.connect allpass2 (.-destination ctx))

        ;; Store reverb nodes
        (reset! reverb-nodes
                {:combs [{:delay comb1 :gain comb1-gain}
                         {:delay comb2 :gain comb2-gain}
                         {:delay comb3 :gain comb3-gain}
                         {:delay comb4 :gain comb4-gain}]
                 :allpass [{:delay allpass1 :gain allpass1-gain}
                           {:delay allpass2 :gain allpass2-gain}]
                 :mix reverb-mix})

        (js/console.log "🎚️ Reverb initialized"))
      (catch js/Error e
        (js/console.error "Failed to initialize reverb:" e)))))

(defn init-audio-context!
  "Initialize Web Audio API context on first user interaction"
  []
  (when-not @audio-context
    (try
      (let [AudioContext (or js/window.AudioContext js/window.webkitAudioContext)
            ctx (AudioContext.)

            ;; Master gain
            gain (.createGain ctx)

            ;; Delay nodes
            delay (.createDelay ctx 1.0)
            delay-fb-gain (.createGain ctx)
            delay-w-gain (.createGain ctx)
            delay-d-gain (.createGain ctx)

            ;; Reverb output gains
            reverb-w-gain (.createGain ctx)
            reverb-d-gain (.createGain ctx)]

        ;; Setup master gain
        (set! (.-value (.-gain gain)) 0.5)

        ;; Setup delay (300ms, 30% feedback, 30% wet)
        (set! (.-value (.-delayTime delay)) 0.3)
        (set! (.-value (.-gain delay-fb-gain)) 0.3)
        (set! (.-value (.-gain delay-w-gain)) 0.3)
        (set! (.-value (.-gain delay-d-gain)) 0.7)

        ;; Setup reverb mix (30% wet)
        (set! (.-value (.-gain reverb-w-gain)) 0.3)
        (set! (.-value (.-gain reverb-d-gain)) 0.7)

        ;; Connect delay chain: master -> delay -> feedback -> delay
        (.connect gain delay-d-gain) ; dry signal
        (.connect gain delay) ; to delay
        (.connect delay delay-fb-gain) ; delay output
        (.connect delay-fb-gain delay) ; feedback loop
        (.connect delay delay-w-gain) ; wet signal

        ;; Both signals to reverb
        (.connect delay-d-gain reverb-d-gain)
        (.connect delay-w-gain reverb-w-gain)

        ;; Initialize reverb (will connect later)
        (reset! reverb-nodes nil)

        ;; Output routing
        (.connect reverb-d-gain (.-destination ctx))
        (.connect reverb-w-gain (.-destination ctx))

        ;; Store references
        (reset! audio-context ctx)
        (reset! master-gain gain)
        (reset! delay-node delay)
        (reset! delay-feedback-gain delay-fb-gain)
        (reset! delay-wet-gain delay-w-gain)
        (reset! delay-dry-gain delay-d-gain)
        (reset! reverb-wet-gain reverb-w-gain)
        (reset! reverb-dry-gain reverb-d-gain)

        ;; Create reverb
        (init-reverb!)

        (js/console.log "🔊 Audio context initialized with effects"))
      (catch js/Error e
        (js/console.error "Failed to initialize audio context:" e)))))

;; ============================================================================
;; Audio Effects Controls
;; ============================================================================

(defn set-delay-time
  "Set delay time in seconds (0.05-1.0)"
  [& {:keys [time]}]
  (when @delay-node
    (set! (.-value (.-delayTime @delay-node)) time)))

(defn set-delay-feedback
  "Set delay feedback amount (0.0-0.9)"
  [& {:keys [feedback]}]
  (when @delay-feedback-gain
    (set! (.-value (.-gain @delay-feedback-gain)) feedback)))

(defn set-delay-mix
  "Set delay wet/dry mix (0.0-1.0, where 1.0 is 100% wet)"
  [& {:keys [mix]}]
  (when (and @delay-wet-gain @delay-dry-gain)
    (set! (.-value (.-gain @delay-wet-gain)) mix)
    (set! (.-value (.-gain @delay-dry-gain)) (- 1.0 mix))))

(defn set-reverb-size
  "Set reverb size by adjusting comb filter feedback (0.3-0.8)"
  [& {:keys [size]}]
  (when-let [combs (:combs @reverb-nodes)]
    (doseq [{:keys [gain]} combs]
      (set! (.-value (.-gain gain)) size))))

(defn set-reverb-mix
  "Set reverb wet/dry mix (0.0-1.0, where 1.0 is 100% wet)"
  [& {:keys [mix]}]
  (when (and @reverb-wet-gain @reverb-dry-gain)
    (set! (.-value (.-gain @reverb-wet-gain)) mix)
    (set! (.-value (.-gain @reverb-dry-gain)) (- 1.0 mix))))

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
  "Synthesized kick drum sound using pitch envelope
   Options:
   - :velocity - Volume multiplier (0.0-1.0, default: 1.0)"
  [& {:keys [velocity]
      :or {velocity 1.0}}]
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

        ;; Amplitude envelope with velocity
        (set! (.-value (.-gain gain)) velocity)
        (.exponentialRampToValueAtTime (.-gain gain) 0.01 (+ now 0.5))

        (.start osc now)
        (.stop osc (+ now 0.5)))
      (catch js/Error e
        (js/console.error "Error playing kick:" e)))))

(defn play-snare
  "Synthesized snare drum using filtered white noise
   Options:
   - :velocity - Volume multiplier (0.0-1.0, default: 0.5)"
  [& {:keys [velocity]
      :or {velocity 0.5}}]
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

        (set! (.-value (.-gain gain)) velocity)
        (.exponentialRampToValueAtTime (.-gain gain) 0.01 (+ now 0.1))

        (.start source now)
        (.stop source (+ now 0.1)))
      (catch js/Error e
        (js/console.error "Error playing snare:" e)))))

(defn play-hihat
  "Synthesized hi-hat using high-pass filtered white noise
   Options:
   - :velocity - Volume multiplier (0.0-1.0, default: 0.3)"
  [& {:keys [velocity]
      :or {velocity 0.3}}]
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

        (set! (.-value (.-gain gain)) velocity)
        (.exponentialRampToValueAtTime (.-gain gain) 0.01 (+ now 0.05))

        (.start source now)
        (.stop source (+ now 0.05)))
      (catch js/Error e
        (js/console.error "Error playing hi-hat:" e)))))

;; ============================================================================
;; Beat Sequencer
;; ============================================================================

(def sequencer-state
  "Sequencer state for 16-step beat patterns with velocity control"
  (r/atom {:playing false
           :bpm 120
           :current-step 0
           :pattern {:kick [1 0 0 0 1 0 0 0 1 0 0 0 1 0 0 0]
                     :snare [0 0 0 0 1 0 0 0 0 0 0 0 1 0 0 0]
                     :hihat [1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0]}
           ;; Velocity values for each step (0.0 = off, 0.5 = soft, 0.7 = normal, 1.0 = accent)
           :velocity {:kick [0.7 0.0 0.0 0.0 0.7 0.0 0.0 0.0 0.7 0.0 0.0 0.0 0.7 0.0 0.0 0.0]
                      :snare [0.0 0.0 0.0 0.0 0.7 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.7 0.0 0.0 0.0]
                      :hihat [0.7 0.0 0.7 0.0 0.7 0.0 0.7 0.0 0.7 0.0 0.7 0.0 0.7 0.0 0.7 0.0]}
           :interval-id nil}))

(defn play-sequencer-step
  "Plays the current step in the sequencer with velocity control"
  []
  (let [{:keys [current-step pattern velocity]} @sequencer-state]
    ;; Play active drums for this step with their velocity values
    (when (and (nth (:kick pattern) current-step)
               (> (nth (:kick velocity) current-step) 0))
      (play-kick :velocity (nth (:kick velocity) current-step)))

    (when (and (nth (:snare pattern) current-step)
               (> (nth (:snare velocity) current-step) 0))
      (play-snare :velocity (nth (:snare velocity) current-step)))

    (when (and (nth (:hihat pattern) current-step)
               (> (nth (:hihat velocity) current-step) 0))
      (play-hihat :velocity (nth (:hihat velocity) current-step)))

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
             :current-step 0
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
  "Toggles a step in the pattern (left-click)
   Options:
   - :track - Keyword for track (:kick, :snare, :hihat)
   - :step - Step index (0-15)"
  [& {:keys [track step]}]
  (swap! sequencer-state update-in [:pattern track step] not)
  ;; Update velocity: if turning on, set to normal (0.7), if turning off, set to 0.0
  (let [is-on (nth (get-in @sequencer-state [:pattern track]) step)]
    (swap! sequencer-state assoc-in [:velocity track step] (if is-on 0.7 0.0))))

(defn cycle-step-velocity
  "Cycles through velocity levels for a step (right-click)
   Velocity levels: 0.0 (off) -> 0.5 (soft) -> 0.7 (normal) -> 1.0 (accent) -> 0.0
   Options:
   - :track - Keyword for track (:kick, :snare, :hihat)
   - :step - Step index (0-15)"
  [& {:keys [track step]}]
  (let [current-velocity (nth (get-in @sequencer-state [:velocity track]) step)
        next-velocity (cond
                        (<= current-velocity 0.0) 0.5 ; off -> soft
                        (<= current-velocity 0.5) 0.7 ; soft -> normal
                        (<= current-velocity 0.7) 1.0 ; normal -> accent
                        :else 0.0)] ; accent -> off
    (swap! sequencer-state assoc-in [:velocity track step] next-velocity)
    ;; Update pattern to reflect on/off state
    (swap! sequencer-state assoc-in [:pattern track step] (if (> next-velocity 0.0) 1 0))))

(defn set-sequencer-bpm
  "Sets the sequencer BPM and restarts if playing"
  [& {:keys [bpm]}]
  (let [was-playing (:playing @sequencer-state)]
    (when was-playing (stop-sequencer))
    (swap! sequencer-state assoc :bpm bpm)
    (when was-playing (start-sequencer))))

(defn clear-pattern
  "Clears all pattern steps and velocities to 0"
  []
  (swap! sequencer-state assoc
         :pattern {:kick (vec (repeat 16 0))
                   :snare (vec (repeat 16 0))
                   :hihat (vec (repeat 16 0))}
         :velocity {:kick (vec (repeat 16 0.0))
                    :snare (vec (repeat 16 0.0))
                    :hihat (vec (repeat 16 0.0))}))

(defn reset-pattern
  "Resets pattern to the original default pattern"
  []
  (swap! sequencer-state assoc
         :pattern {:kick [1 0 0 0 1 0 0 0 1 0 0 0 1 0 0 0]
                   :snare [0 0 0 0 1 0 0 0 0 0 0 0 1 0 0 0]
                   :hihat [1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0]}
         :velocity {:kick [0.7 0.0 0.0 0.0 0.7 0.0 0.0 0.0 0.7 0.0 0.0 0.0 0.7 0.0 0.0 0.0]
                    :snare [0.0 0.0 0.0 0.0 0.7 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.7 0.0 0.0 0.0]
                    :hihat [0.7 0.0 0.7 0.0 0.7 0.0 0.7 0.0 0.7 0.0 0.7 0.0 0.7 0.0 0.7 0.0]}))

(defn load-preset
  "Loads a preset pattern by name
   Available presets:
   - :basic-rock - Classic rock beat (4-on-the-floor kick, backbeat snare)
   - :funk-groove - Syncopated funk pattern with ghost notes
   - :techno-beat - Four-to-the-floor techno with closed hats
   - :breakbeat - Hip-hop style breakbeat with varied velocity"
  [& {:keys [preset]}]
  (case preset
    :basic-rock
    (swap! sequencer-state assoc
           :pattern {:kick [1 0 0 0 1 0 0 0 1 0 0 0 1 0 0 0]
                     :snare [0 0 0 0 1 0 0 0 0 0 0 0 1 0 0 0]
                     :hihat [1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0]}
           :velocity {:kick [1.0 0.0 0.0 0.0 0.7 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.7 0.0 0.0 0.0]
                      :snare [0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0]
                      :hihat [0.7 0.0 0.5 0.0 0.7 0.0 0.5 0.0 0.7 0.0 0.5 0.0 0.7 0.0 0.5 0.0]})

    :funk-groove
    (swap! sequencer-state assoc
           :pattern {:kick [1 0 0 1 0 0 1 0 0 0 1 0 0 1 0 0]
                     :snare [0 0 0 0 1 0 0 1 0 0 0 0 1 0 0 0]
                     :hihat [1 1 1 0 1 1 1 0 1 1 1 0 1 1 1 0]}
           :velocity {:kick [0.7 0.0 0.0 0.5 0.0 0.0 0.7 0.0 0.0 0.0 0.5 0.0 0.0 0.7 0.0 0.0]
                      :snare [0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.5 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0]
                      :hihat [0.7 0.5 0.5 0.0 0.7 0.5 0.5 0.0 0.7 0.5 0.5 0.0 0.7 0.5 0.5 0.0]})

    :techno-beat
    (swap! sequencer-state assoc
           :pattern {:kick [1 0 0 0 1 0 0 0 1 0 0 0 1 0 0 0]
                     :snare [0 0 0 0 1 0 0 0 0 0 0 0 1 0 0 0]
                     :hihat [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]}
           :velocity {:kick [1.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0]
                      :snare [0.0 0.0 0.0 0.0 0.7 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.7 0.0 0.0 0.0]
                      :hihat [0.7 0.5 0.7 0.5 0.7 0.5 0.7 0.5 0.7 0.5 0.7 0.5 0.7 0.5 0.7 0.5]})

    :breakbeat
    (swap! sequencer-state assoc
           :pattern {:kick [1 0 0 0 0 0 1 0 1 0 0 1 0 0 0 0]
                     :snare [0 0 0 0 1 0 0 0 0 1 0 0 1 0 0 1]
                     :hihat [1 0 1 1 1 0 1 0 1 0 1 1 1 0 1 0]}
           :velocity {:kick [1.0 0.0 0.0 0.0 0.0 0.0 0.7 0.0 0.7 0.0 0.0 0.5 0.0 0.0 0.0 0.0]
                      :snare [0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 0.5 0.0 0.0 1.0 0.0 0.0 0.5]
                      :hihat [0.7 0.0 0.5 0.7 0.7 0.0 0.5 0.0 0.7 0.0 0.5 0.7 0.7 0.0 0.5 0.0]})

    ;; Default: do nothing
    nil))

(defn save-pattern
  "Saves the current pattern to LocalStorage with a given name"
  [& {:keys [name]}]
  (when (and name (not (str/blank? name)))
    (try
      (let [current-pattern {:pattern (:pattern @sequencer-state)
                             :velocity (:velocity @sequencer-state)
                             :bpm (:bpm @sequencer-state)}
            pattern-json (js/JSON.stringify (clj->js current-pattern))]
        (.setItem js/localStorage (str "drum-pattern-" name) pattern-json)
        true)
      (catch js/Error e
        (js/console.error "Error saving pattern:" e)
        false))))

(defn load-saved-pattern
  "Loads a saved pattern from LocalStorage by name"
  [& {:keys [name]}]
  (when (and name (not (str/blank? name)))
    (try
      (when-let [pattern-json (.getItem js/localStorage (str "drum-pattern-" name))]
        (let [pattern-data (js->clj (js/JSON.parse pattern-json) :keywordize-keys true)]
          (swap! sequencer-state assoc
                 :pattern (:pattern pattern-data)
                 :velocity (:velocity pattern-data)
                 :bpm (or (:bpm pattern-data) 120))
          true))
      (catch js/Error e
        (js/console.error "Error loading pattern:" e)
        false))))

(defn list-saved-patterns
  "Returns a list of all saved pattern names"
  []
  (try
    (let [keys (for [i (range (.-length js/localStorage))]
                 (.key js/localStorage i))
          pattern-keys (filter #(str/starts-with? % "drum-pattern-") keys)]
      (map #(subs % 13) pattern-keys))
    (catch js/Error e
      (js/console.error "Error listing patterns:" e)
      [])))

(defn delete-saved-pattern
  "Deletes a saved pattern from LocalStorage by name"
  [& {:keys [name]}]
  (when (and name (not (str/blank? name)))
    (try
      (.removeItem js/localStorage (str "drum-pattern-" name))
      true
      (catch js/Error e
        (js/console.error "Error deleting pattern:" e)
        false))))

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
  "Piano key component with improved visibility"
  [& {:keys [note label on-play color]}]
  [:button {:on-click #(when on-play (on-play :note note))
            :style (merge-styles
                    {:width "50px"
                     :height "120px"
                     :background (if (= color "white")
                                   "linear-gradient(to bottom, #ffffff 0%, #f5f5f5 100%)"
                                   color)
                     :color (if (= color "white") "#333" "white")
                     :border "2px solid #333"
                     :cursor "pointer"
                     :font-weight "bold"
                     :font-size "14px"
                     :transition "all 0.1s ease"
                     :border-radius "0 0 6px 6px"
                     :box-shadow "0 4px 6px rgba(0,0,0,0.2), inset 0 -2px 4px rgba(0,0,0,0.1)"
                     :position "relative"})
            :on-mouse-enter (fn [e]
                              (set! (.-style.background (.-currentTarget e))
                                    (if (= color "white")
                                      "linear-gradient(to bottom, #f0f0f0 0%, #e0e0e0 100%)"
                                      color)))
            :on-mouse-leave (fn [e]
                              (set! (.-style.background (.-currentTarget e))
                                    (if (= color "white")
                                      "linear-gradient(to bottom, #ffffff 0%, #f5f5f5 100%)"
                                      color)))
            :on-mouse-down (fn [e]
                             (when on-play
                               (set! (.-style.transform (.-currentTarget e)) "scale(0.95) translateY(2px)")
                               (set! (.-style.boxShadow (.-currentTarget e))
                                     "0 2px 3px rgba(0,0,0,0.3), inset 0 2px 4px rgba(0,0,0,0.2)")
                               (on-play :note note)))
            :on-mouse-up (fn [e]
                           (set! (.-style.transform (.-currentTarget e)) "scale(1) translateY(0)")
                           (set! (.-style.boxShadow (.-currentTarget e))
                                 "0 4px 6px rgba(0,0,0,0.2), inset 0 -2px 4px rgba(0,0,0,0.1)"))}
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

(defn effects-control
  "Audio effects control panel component"
  [& {:keys [delay-time delay-feedback delay-mix reverb-size reverb-mix
             on-delay-time-change on-delay-feedback-change on-delay-mix-change
             on-reverb-size-change on-reverb-mix-change]}]
  [:div {:style {:background "white"
                 :padding "30px"
                 :border-radius "12px"
                 :box-shadow "0 2px 8px rgba(0,0,0,0.1)"
                 :margin-bottom "30px"}}
   [:h3 {:style {:margin-bottom "20px"
                 :color "#333"}}
    "🎚️ Audio Effects"]
   [:p {:style {:color "#666"
                :margin-bottom "20px"
                :font-size "14px"}}
    "Add depth and space to your sounds with delay and reverb"]

   ;; Delay controls
   [:div {:style {:margin-bottom "25px"
                  :padding "20px"
                  :background "#f8f9fa"
                  :border-radius "8px"}}
    [:h4 {:style {:margin-top "0"
                  :margin-bottom "15px"
                  :color "#555"
                  :font-size "16px"}}
     "🔁 Delay/Echo"]
    [:div {:style {:display "flex"
                   :gap "15px"
                   :flex-wrap "wrap"}}
     [slider-control
      :label "Time"
      :value delay-time
      :min 0.05
      :max 1.0
      :step 0.05
      :unit " s"
      :on-change on-delay-time-change]
     [slider-control
      :label "Feedback"
      :value (* delay-feedback 100)
      :min 0
      :max 90
      :step 5
      :unit "%"
      :on-change on-delay-feedback-change]
     [slider-control
      :label "Mix"
      :value (* delay-mix 100)
      :min 0
      :max 100
      :step 10
      :unit "%"
      :on-change on-delay-mix-change]]]

   ;; Reverb controls
   [:div {:style {:padding "20px"
                  :background "#f0f4ff"
                  :border-radius "8px"}}
    [:h4 {:style {:margin-top "0"
                  :margin-bottom "15px"
                  :color "#555"
                  :font-size "16px"}}
     "🌊 Reverb"]
    [:div {:style {:display "flex"
                   :gap "15px"
                   :flex-wrap "wrap"}}
     [slider-control
      :label "Size"
      :value (* reverb-size 100)
      :min 30
      :max 80
      :step 5
      :unit "%"
      :on-change on-reverb-size-change]
     [slider-control
      :label "Mix"
      :value (* reverb-mix 100)
      :min 0
      :max 100
      :step 10
      :unit "%"
      :on-change on-reverb-mix-change]]]])

;; ============================================================================
;; Main Component
;; ============================================================================

(defn velocity-style
  "Calculate button style based on velocity value"
  [& {:keys [velocity base-color current-step? step-active?]}]
  (let [;; Determine opacity and visual intensity based on velocity
        [opacity color box-shadow]
        (cond
          ;; Off (velocity 0.0) - dim gray
          (= velocity 0.0)
          [0.3 "#f0f0f0" "none"]

          ;; Soft (velocity 0.5) - lighter color, medium opacity
          (= velocity 0.5)
          [0.6 (if step-active? base-color "#f0f0f0") "none"]

          ;; Normal (velocity 0.7) - normal color, high opacity
          (= velocity 0.7)
          [0.85 (if step-active? base-color "#f0f0f0") "none"]

          ;; Accent (velocity 1.0) - bright color with glow
          (= velocity 1.0)
          [1.0 (if step-active? base-color "#f0f0f0")
           (if step-active?
             (str "0 0 10px " base-color ", 0 0 20px " base-color)
             "none")]

          ;; Fallback
          :else
          [0.85 (if step-active? base-color "#f0f0f0") "none"])]

    {:padding "12px 8px"
     :background color
     :color (if step-active? "white" "#999")
     :opacity opacity
     :border (if current-step?
               "2px solid #4caf50"
               "1px solid #ddd")
     :border-radius "4px"
     :cursor "pointer"
     :font-size "12px"
     :font-weight "bold"
     :transition "all 0.1s"
     :box-shadow box-shadow}))

(defn audio-playground
  "Main audio playground component"
  []
  (let [volume-atom (r/atom (get-master-volume))
        active-tone (r/atom nil)
        ;; Audio effects parameters
        delay-time (r/atom 0.3)
        delay-feedback (r/atom 0.3)
        delay-mix (r/atom 0.3)
        reverb-size (r/atom 0.5)
        reverb-mix (r/atom 0.3)
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

       ;; Audio Effects Control
       [effects-control
        :delay-time @delay-time
        :delay-feedback @delay-feedback
        :delay-mix @delay-mix
        :reverb-size @reverb-size
        :reverb-mix @reverb-mix
        :on-delay-time-change (fn [e]
                                (let [val (js/parseFloat (.-value (.-target e)))]
                                  (reset! delay-time val)
                                  (set-delay-time :time val)))
        :on-delay-feedback-change (fn [e]
                                    (let [val (/ (js/parseInt (.-value (.-target e))) 100)]
                                      (reset! delay-feedback val)
                                      (set-delay-feedback :feedback val)))
        :on-delay-mix-change (fn [e]
                               (let [val (/ (js/parseInt (.-value (.-target e))) 100)]
                                 (reset! delay-mix val)
                                 (set-delay-mix :mix val)))
        :on-reverb-size-change (fn [e]
                                 (let [val (/ (js/parseInt (.-value (.-target e))) 100)]
                                   (reset! reverb-size val)
                                   (set-reverb-size :size val)))
        :on-reverb-mix-change (fn [e]
                                (let [val (/ (js/parseInt (.-value (.-target e))) 100)]
                                  (reset! reverb-mix val)
                                  (set-reverb-mix :mix val)))]

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
                           :text-align "center"}}]]

         ;; Clear Pattern Button
         [:button {:on-click clear-pattern
                   :style {:padding "12px 24px"
                           :background "#9e9e9e"
                           :color "white"
                           :border "none"
                           :border-radius "8px"
                           :cursor "pointer"
                           :font-weight "600"
                           :font-size "16px"
                           :transition "all 0.2s"}}
          "🗑️ Clear"]

         ;; Reset Pattern Button
         [:button {:on-click reset-pattern
                   :style {:padding "12px 24px"
                           :background "#ff9800"
                           :color "white"
                           :border "none"
                           :border-radius "8px"
                           :cursor "pointer"
                           :font-weight "600"
                           :font-size "16px"
                           :transition "all 0.2s"}}
          "🔄 Reset"]]

        ;; Pattern Presets
        [:div {:style {:display "flex"
                       :gap "10px"
                       :margin-bottom "20px"
                       :flex-wrap "wrap"
                       :align-items "center"
                       :padding "15px"
                       :background "#f8f9fa"
                       :border-radius "8px"}}
         [:label {:style {:font-weight "600"
                          :color "#555"
                          :min-width "70px"}}
          "🎵 Presets:"]
         [:button {:on-click #(load-preset :preset :basic-rock)
                   :style {:padding "8px 16px"
                           :background "#4caf50"
                           :color "white"
                           :border "none"
                           :border-radius "6px"
                           :cursor "pointer"
                           :font-weight "600"
                           :font-size "14px"
                           :transition "all 0.2s"}}
          "🎸 Rock"]
         [:button {:on-click #(load-preset :preset :funk-groove)
                   :style {:padding "8px 16px"
                           :background "#9c27b0"
                           :color "white"
                           :border "none"
                           :border-radius "6px"
                           :cursor "pointer"
                           :font-weight "600"
                           :font-size "14px"
                           :transition "all 0.2s"}}
          "🎺 Funk"]
         [:button {:on-click #(load-preset :preset :techno-beat)
                   :style {:padding "8px 16px"
                           :background "#2196f3"
                           :color "white"
                           :border "none"
                           :border-radius "6px"
                           :cursor "pointer"
                           :font-weight "600"
                           :font-size "14px"
                           :transition "all 0.2s"}}
          "🔊 Techno"]
         [:button {:on-click #(load-preset :preset :breakbeat)
                   :style {:padding "8px 16px"
                           :background "#ff5722"
                           :color "white"
                           :border "none"
                           :border-radius "6px"
                           :cursor "pointer"
                           :font-weight "600"
                           :font-size "14px"
                           :transition "all 0.2s"}}
          "🎤 Breakbeat"]]

        ;; Save/Load Patterns
        [:div {:style {:display "flex"
                       :gap "10px"
                       :margin-bottom "20px"
                       :flex-wrap "wrap"
                       :align-items "center"
                       :padding "15px"
                       :background "#e8f5e9"
                       :border-radius "8px"
                       :border "1px solid #4caf50"}}
         [:label {:style {:font-weight "600"
                          :color "#2e7d32"
                          :min-width "70px"}}
          "💾 Save/Load:"]
         [:input {:type "text"
                  :placeholder "Pattern name..."
                  :id "pattern-name-input"
                  :style {:padding "8px 12px"
                          :border "2px solid #4caf50"
                          :border-radius "6px"
                          :font-size "14px"
                          :min-width "150px"
                          :outline "none"
                          :background "white"
                          :color "#333"}}]
         [:button {:on-click (fn []
                               (let [input (.getElementById js/document "pattern-name-input")
                                     name (.-value input)]
                                 (when (save-pattern :name name)
                                   (set! (.-value input) "")
                                   (js/alert (str "Pattern '" name "' saved!")))))
                   :style {:padding "8px 16px"
                           :background "#4caf50"
                           :color "white"
                           :border "none"
                           :border-radius "6px"
                           :cursor "pointer"
                           :font-weight "600"
                           :font-size "14px"
                           :transition "all 0.2s"}}
          "💾 Save"]
         [:select {:id "pattern-load-select"
                   :style {:padding "8px 12px"
                           :border "2px solid #4caf50"
                           :border-radius "6px"
                           :font-size "14px"
                           :min-width "150px"
                           :cursor "pointer"
                           :background "white"}}
          [:option {:value ""} "-- Select Pattern --"]
          (for [pattern-name (list-saved-patterns)]
            ^{:key pattern-name}
            [:option {:value pattern-name} pattern-name])]
         [:button {:on-click (fn []
                               (let [select (.getElementById js/document "pattern-load-select")
                                     name (.-value select)]
                                 (when (and name (not (str/blank? name)))
                                   (load-saved-pattern :name name)
                                   (js/alert (str "Pattern '" name "' loaded!")))))
                   :style {:padding "8px 16px"
                           :background "#2196f3"
                           :color "white"
                           :border "none"
                           :border-radius "6px"
                           :cursor "pointer"
                           :font-weight "600"
                           :font-size "14px"
                           :transition "all 0.2s"}}
          "📂 Load"]
         [:button {:on-click (fn []
                               (let [select (.getElementById js/document "pattern-load-select")
                                     name (.-value select)]
                                 (when (and name (not (str/blank? name)))
                                   (when (js/confirm (str "Delete pattern '" name "'?"))
                                     (delete-saved-pattern :name name)
                                     (set! (.-value select) "")
                                     (js/alert (str "Pattern '" name "' deleted!"))
                                     ;; Force re-render by updating state
                                     (swap! sequencer-state assoc :dummy (js/Date.now))))))
                   :style {:padding "8px 16px"
                           :background "#f44336"
                           :color "white"
                           :border "none"
                           :border-radius "6px"
                           :cursor "pointer"
                           :font-weight "600"
                           :font-size "14px"
                           :transition "all 0.2s"}}
          "🗑️ Delete"]

        ;; Velocity instructions
         [:div {:style {:display "flex"
                        :align-items "center"
                        :gap "8px"
                        :padding "10px 15px"
                        :background "#fff3e0"
                        :border-radius "6px"
                        :border "1px solid #ffb74d"
                        :margin-top "15px"
                        :margin-bottom "15px"}}
          [:span {:style {:font-size "20px"}} "💡"]
          [:div {:style {:font-size "13px"
                         :color "#e65100"
                         :line-height "1.5"}}
           [:strong "Velocity Control:"] " Left-click to toggle steps • "
           [:strong "Right-click"] " to cycle velocity: Off → Soft → Normal → ACCENT ✨"]]]

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
           (let [velocity (nth (get-in @sequencer-state [:velocity :kick]) i)
                 step-active? (nth (get-in @sequencer-state [:pattern :kick]) i)
                 playing? (:playing @sequencer-state)]
             ^{:key (str "kick-" i)}
             [:button {:on-click #(toggle-sequencer-step :track :kick :step i)
                       :on-context-menu (fn [e]
                                          (.preventDefault e)
                                          (cycle-step-velocity :track :kick :step i))
                       :style (velocity-style :velocity velocity
                                              :base-color "#e74c3c"
                                              :current-step? (and playing? (= i (:current-step @sequencer-state)))
                                              :step-active? step-active?)}
              (if (and playing? (= i (:current-step @sequencer-state))) "▶" (inc i))]))

         ;; Snare row
         [:div {:style {:font-weight "bold"
                        :color "#3498db"
                        :font-size "14px"}}
          "SNARE"]
         (for [i (range 16)]
           (let [velocity (nth (get-in @sequencer-state [:velocity :snare]) i)
                 step-active? (nth (get-in @sequencer-state [:pattern :snare]) i)
                 playing? (:playing @sequencer-state)]
             ^{:key (str "snare-" i)}
             [:button {:on-click #(toggle-sequencer-step :track :snare :step i)
                       :on-context-menu (fn [e]
                                          (.preventDefault e)
                                          (cycle-step-velocity :track :snare :step i))
                       :style (velocity-style :velocity velocity
                                              :base-color "#3498db"
                                              :current-step? (and playing? (= i (:current-step @sequencer-state)))
                                              :step-active? step-active?)}
              (if (and playing? (= i (:current-step @sequencer-state))) "▶" (inc i))]))

         ;; Hi-hat row
         [:div {:style {:font-weight "bold"
                        :color "#f39c12"
                        :font-size "14px"}}
          "HI-HAT"]
         (for [i (range 16)]
           (let [velocity (nth (get-in @sequencer-state [:velocity :hihat]) i)
                 step-active? (nth (get-in @sequencer-state [:pattern :hihat]) i)
                 playing? (:playing @sequencer-state)]
             ^{:key (str "hihat-" i)}
             [:button {:on-click #(toggle-sequencer-step :track :hihat :step i)
                       :on-context-menu (fn [e]
                                          (.preventDefault e)
                                          (cycle-step-velocity :track :hihat :step i))
                       :style (velocity-style :velocity velocity
                                              :base-color "#f39c12"
                                              :current-step? (and playing? (= i (:current-step @sequencer-state)))
                                              :step-active? step-active?)}
              (if (and playing? (= i (:current-step @sequencer-state))) "▶" (inc i))]))]]

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
