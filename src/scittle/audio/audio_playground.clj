
^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Web Audio API Playground with ClojureScript & Scittle"
         :quarto {:author [:burinc]
                  :description "Master browser-native sound synthesis! Build a complete audio playground with sound effects, musical instruments, drum machines, and beat sequencers - all without external audio files or build tools!"
                  :type :post
                  :date "2025-11-10"
                  :category :audio
                  :image "audio-playground-01.png"
                  :tags [:scittle
                         :clojurescript
                         :reagent
                         :web-audio-api
                         :sound-synthesis
                         :music
                         :audio
                         :interactive
                         :no-build
                         :mobile
                         :drums
                         :sequencer]
                  :keywords [:web-audio-api
                             :sound-synthesis
                             :oscillators
                             :drum-machine
                             :beat-sequencer
                             :musical-keyboard
                             :procedural-audio
                             :browser-audio
                             :adsr-envelope
                             :audio-effects]}}}

(ns scittle.audio.audio-playground
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # Web Audio API Playground with ClojureScript & Scittle

;; ## About This Project

;; Ever wanted to create sound effects for your games, build musical instruments, or compose beats - all directly in your browser? Today, I'll show you how to build a complete audio playground using the Web Audio API, ClojureScript, and Scittle - with zero external audio files!

;; This is part of my ongoing exploration of browser-native development with Scittle. Check out my previous articles:
;;
;; - [Build Galaga with ClojureScript & Scittle](https://clojurecivitas.github.io/scittle/games/galaga.html) - Classic arcade game with formation patterns
;; - [Build Asteroids with ClojureScript & Scittle](https://clojurecivitas.github.io/scittle/games/asteroids_article.html) - Space shooter with physics
;; - [Build a Memory Game with Scittle](https://clojurecivitas.github.io/scittle/games/memory_game_article.html) - Simon-style memory challenge
;; - [Browser-Native QR Code Scanner with Scittle](https://clojurecivitas.github.io/scittle/qrcode/qr_code_scanner.html) - QR scanner in the browser
;; - [Free Weather Data with NWS API](https://clojurecivitas.github.io/scittle/weather/weather_nws_integration.html) - Free weather data from NWS (US)
;; - [Building Browser-Native Presentations](https://clojurecivitas.github.io/scittle/presentations/browser_native_slides.html) - Interactive presentation systems

;; This project demonstrates procedural audio synthesis without any build tools or external dependencies!

;; ## What We're Building

;; We'll create a comprehensive audio playground featuring:

;; - **Sound Effects Library** with 7 UI feedback sounds (click, success, error, notification, hover, complete, delete)
;; - **Musical Keyboard** playing C4-C5 chromatic scale with melody sequences
;; - **Tone Generator** with multiple waveforms (sine, square, sawtooth, triangle)
;; - **Drum Machine** with synthesized kick, snare, and hi-hat sounds
;; - **Beat Sequencer** with 16-step patterns and adjustable BPM
;; - **Volume Control** with master gain node
;; - **Mobile-Friendly Design** that works on all devices

;; All sounds are generated procedurally in real-time - no audio files required!

;; ## Why Web Audio API?

;; The Web Audio API offers unique advantages for interactive applications:

;; ### Zero Dependencies
;; - No external audio files to download
;; - Instant loading - everything is code
;; - Tiny file size - a few KB instead of MB
;; - No copyright concerns with sound samples

;; ### Full Control
;; - Generate any sound imaginable
;; - Adjust parameters in real-time
;; - Create dynamic, responsive audio
;; - Perfect for games and interactive apps

;; ### Educational Value
;; - Understand sound synthesis fundamentals
;; - Learn audio programming concepts
;; - Explore frequency, amplitude, and timbre
;; - Build a foundation for music technology

;; ## Audio Architecture

;; The Web Audio API follows a node-based architecture:

^:kindly/hide-code
(kind/mermaid
 "graph LR
    A[AudioContext] --> B[Oscillator Node]
    A --> C[Buffer Source]
    A --> D[Gain Node]
    B --> D
    C --> E[Filter Node]
    E --> D
    D --> F[Destination]
    F --> G[Speakers]

    style A fill:#4caf50
    style D fill:#2196f3
    style F fill:#ff9800")

;; ### Core Concepts

;; **AudioContext**: The master controller for all audio operations
;; ```clojure
;; (defonce audio-context (atom nil))
;;
;; (defn init-audio-context! []
;;   (when-not @audio-context
;;     (let [AudioContext (or js/window.AudioContext
;;                           js/window.webkitAudioContext)
;;           ctx (AudioContext.)]
;;       (reset! audio-context ctx))))
;; ```

;; **Oscillator**: Generates waveforms at specific frequencies
;; ```clojure
;; (let [osc (.createOscillator @audio-context)]
;;   (set! (.-type osc) \"sine\")           ; Waveform type
;;   (set! (.-value (.-frequency osc)) 440) ; A4 note
;;   (.start osc))
;; ```

;; **Gain Node**: Controls volume/amplitude
;; ```clojure
;; (let [gain (.createGain @audio-context)]
;;   (set! (.-value (.-gain gain)) 0.5)) ; 50% volume
;; ```

;; ## Sound Synthesis Fundamentals

;; ### What is Sound?

;; Sound is vibration traveling through air. In digital audio, we represent this as:
;;
;; - **Frequency (Hz)**: How fast the vibration - determines pitch
;; - **Amplitude**: How strong the vibration - determines volume
;; - **Waveform**: The shape of the vibration - determines timbre (tone quality)

;; ### Waveform Types

;; Different waveforms create different timbres:

^:kindly/hide-code
(kind/mermaid
 "graph TD
    A[Waveform Types] --> B[Sine Wave]
    A --> C[Square Wave]
    A --> D[Sawtooth Wave]
    A --> E[Triangle Wave]

    B --> B1[Pure tone - smooth]
    B --> B2[Fundamental only]
    B --> B3[Flute-like]

    C --> C1[Hollow - retro]
    C --> C2[Odd harmonics]
    C --> C3[Clarinet-like]

    D --> D1[Bright - buzzy]
    D --> D2[All harmonics]
    D --> D3[Violin-like]

    E --> E1[Mellow - soft]
    E --> E2[Weak harmonics]
    E --> E3[Ocarina-like]

    style B fill:#4caf50
    style C fill:#ff9800
    style D fill:#2196f3
    style E fill:#9c27b0")

;; ### ADSR Envelope

;; An envelope shapes how a sound evolves over time:

;; - **Attack**: How quickly the sound reaches full volume
;; - **Decay**: How quickly it drops to sustain level
;; - **Sustain**: The held volume level
;; - **Release**: How quickly it fades to silence

;; ```clojure
;; (defn create-oscillator
;;   [& {:keys [frequency type duration attack release gain-value]
;;       :or {frequency 440
;;            type \"sine\"
;;            duration 0.2
;;            attack 0.01
;;            release 0.1
;;            gain-value 0.3}}]
;;   (when @audio-context
;;     (let [osc (.createOscillator @audio-context)
;;           gain (.createGain @audio-context)
;;           now (.-currentTime @audio-context)
;;           stop-time (+ now duration release)]
;;
;;       ;; Configure oscillator
;;       (set! (.-type osc) type)
;;       (set! (.-value (.-frequency osc)) frequency)
;;
;;       ;; Connect audio nodes
;;       (.connect osc gain)
;;       (.connect gain @master-gain)
;;
;;       ;; Apply ADSR envelope
;;       (set! (.-value (.-gain gain)) 0)
;;       ;; Attack: ramp up to gain-value
;;       (.linearRampToValueAtTime (.-gain gain) gain-value (+ now attack))
;;       ;; Release: ramp down to silence
;;       (.exponentialRampToValueAtTime (.-gain gain) 0.01 stop-time)
;;
;;       ;; Start and schedule stop
;;       (.start osc now)
;;       (.stop osc stop-time)
;;
;;       {:oscillator osc :gain gain})))
;; ```

;; ## Sound Effects Library

;; UI feedback sounds make applications feel responsive and alive!

;; ### Why Sound Effects Matter

;; - **Immediate Feedback**: Confirms user actions instantly
;; - **Emotional Response**: Different sounds convey success, warning, or error
;; - **Accessibility**: Audio complements visual feedback
;; - **Engagement**: Sounds make interactions more satisfying

;; ### Designing Effective Sounds

;; Good UI sounds are:
;;
;; - **Brief**: 50-300ms for most effects
;; - **Distinct**: Each sound has a unique character
;; - **Pleasant**: Not harsh or annoying
;; - **Meaningful**: Matches the action semantically

;; ### Implementation Examples

;; **Click Sound** - Quick confirmation
;; ```clojure
;; (defn play-click []
;;   (create-oscillator :frequency 600 :type \"sine\" :duration 0.05)
;;   (create-oscillator :frequency 800 :type \"sine\" :duration 0.05))
;; ```

;; **Success Sound** - Ascending major chord (C-E-G)
;; ```clojure
;; (defn play-success []
;;   (js/setTimeout #(create-oscillator :frequency 523 :duration 0.1) 0)
;;   (js/setTimeout #(create-oscillator :frequency 659 :duration 0.1) 100)
;;   (js/setTimeout #(create-oscillator :frequency 784 :duration 0.2) 200))
;; ```

;; **Error Sound** - Low, harsh dissonance
;; ```clojure
;; (defn play-error []
;;   (create-oscillator :frequency 200
;;                      :type \"sawtooth\"
;;                      :duration 0.3
;;                      :gain-value 0.25)
;;   (create-oscillator :frequency 150
;;                      :type \"square\"
;;                      :duration 0.3
;;                      :gain-value 0.2))
;; ```

;; ### Frequency Selection Strategy

;; - **High frequencies (800-1200Hz)**: Attention-grabbing (notifications, hover)
;; - **Mid frequencies (400-600Hz)**: Neutral actions (clicks, taps)
;; - **Low frequencies (100-300Hz)**: Warnings and errors
;; - **Melodic progressions**: Success and completion (use musical intervals)

;; ## Musical Keyboard

;; ### Note Frequencies

;; Musical notes follow a mathematical relationship. Each semitone is a factor of the 12th root of 2 (≈1.059463):

;; ```clojure
;; (def note-frequencies
;;   \"Musical note frequencies in Hz (C4-C5 chromatic scale)\"
;;   {:C4  261.63   ; Middle C
;;    :C#4 277.18   ; C sharp
;;    :D4  293.66   ; D
;;    :D#4 311.13   ; D sharp
;;    :E4  329.63   ; E
;;    :F4  349.23   ; F
;;    :F#4 369.99   ; F sharp
;;    :G4  392.00   ; G
;;    :G#4 415.30   ; G sharp
;;    :A4  440.00   ; A (concert pitch)
;;    :A#4 466.16   ; A sharp
;;    :B4  493.88   ; B
;;    :C5  523.25}) ; C (octave higher)
;; ```

;; ### Playing Notes

;; Simple note playback:
;; ```clojure
;; (defn play-note
;;   [& {:keys [note duration type]
;;       :or {duration 0.2 type \"sine\"}}]
;;   (when-let [frequency (get note-frequencies note)]
;;     (create-oscillator :frequency frequency
;;                        :type type
;;                        :duration duration)))
;;
;; ;; Usage
;; (play-note :note :A4 :duration 0.5)
;; ```

;; ### Melody Sequences

;; Play multiple notes in sequence:
;; ```clojure
;; (defn play-melody
;;   [& {:keys [note-sequence delay-between]
;;       :or {delay-between 250}}]
;;   (doseq [[idx [note duration]] (map-indexed vector note-sequence)]
;;     (js/setTimeout
;;      #(play-note :note note :duration (or duration 0.2))
;;      (* idx delay-between))))
;;
;; ;; Play C Major chord: C-E-G-C
;; (play-melody :note-sequence [[:C4 0.2] [:E4 0.2]
;;                               [:G4 0.2] [:C5 0.4]])
;;
;; ;; Play C Major scale
;; (play-melody :note-sequence [[:C4] [:D4] [:E4] [:F4]
;;                               [:G4] [:A4] [:B4] [:C5]])
;; ```

;; ### Musical Theory Connection

;; The keyboard demonstrates important concepts:
;;
;; - **Octaves**: C5 is exactly double the frequency of C4 (523.25 / 261.63 ≈ 2)
;; - **Intervals**: Musical intervals are frequency ratios (perfect fifth = 3:2)
;; - **Scales**: Patterns of intervals create different scales
;; - **Chords**: Playing multiple notes simultaneously creates harmony

;; ## Drum Machine

;; Drums are synthesized using different techniques than melodic instruments:

;; ### Kick Drum - Pitch Envelope

;; A kick drum is essentially a sine wave that quickly drops in pitch:

;; ```clojure
;; (defn play-kick []
;;   (when @audio-context
;;     (let [osc (.createOscillator @audio-context)
;;           gain (.createGain @audio-context)
;;           now (.-currentTime @audio-context)]
;;       (set! (.-type osc) \"sine\")
;;       (.connect osc gain)
;;       (.connect gain @master-gain)
;;
;;       ;; Pitch envelope: 150Hz → 50Hz in 0.1s
;;       (set! (.-value (.-frequency osc)) 150)
;;       (.exponentialRampToValueAtTime (.-frequency osc) 50 (+ now 0.1))
;;
;;       ;; Amplitude envelope: loud → quiet in 0.5s
;;       (set! (.-value (.-gain gain)) 1)
;;       (.exponentialRampToValueAtTime (.-gain gain) 0.01 (+ now 0.5))
;;
;;       (.start osc now)
;;       (.stop osc (+ now 0.5)))))
;; ```

;; **Key technique**: The pitch drop creates that characteristic \"thump\"!

;; ### Snare Drum - White Noise + Filter

;; Snares use noise instead of pure tones:

;; ```clojure
;; (defn create-noise-buffer
;;   [& {:keys [sample-rate duration]
;;       :or {sample-rate 44100 duration 0.1}}]
;;   (let [buffer-size (* sample-rate duration)
;;         buffer (.createBuffer @audio-context 1 buffer-size sample-rate)
;;         data (.getChannelData buffer 0)]
;;     ;; Fill with random values (-1 to 1)
;;     (dotimes [i buffer-size]
;;       (aset data i (- (* 2 (js/Math.random)) 1)))
;;     buffer))
;;
;; (defn play-snare []
;;   (let [noise-buffer (create-noise-buffer :duration 0.1)
;;         source (.createBufferSource @audio-context)
;;         filter (.createBiquadFilter @audio-context)
;;         gain (.createGain @audio-context)
;;         now (.-currentTime @audio-context)]
;;
;;     (set! (.-buffer source) noise-buffer)
;;
;;     ;; Highpass filter emphasizes high frequencies
;;     (set! (.-type filter) \"highpass\")
;;     (set! (.-value (.-frequency filter)) 1000)
;;
;;     (.connect source filter)
;;     (.connect filter gain)
;;     (.connect gain @master-gain)
;;
;;     ;; Quick decay
;;     (set! (.-value (.-gain gain)) 0.5)
;;     (.exponentialRampToValueAtTime (.-gain gain) 0.01 (+ now 0.1))
;;
;;     (.start source now)
;;     (.stop source (+ now 0.1))))
;; ```

;; **Key technique**: Filtered white noise creates that crisp snare crack!

;; ### Hi-Hat - Ultra-Short Noise Burst

;; Hi-hats are very short, highly filtered noise:

;; ```clojure
;; (defn play-hihat []
;;   (let [noise-buffer (create-noise-buffer :duration 0.05)
;;         source (.createBufferSource @audio-context)
;;         filter (.createBiquadFilter @audio-context)
;;         gain (.createGain @audio-context)
;;         now (.-currentTime @audio-context)]
;;
;;     (set! (.-buffer source) noise-buffer)
;;
;;     ;; Very high filter for metallic sound
;;     (set! (.-type filter) \"highpass\")
;;     (set! (.-value (.-frequency filter)) 8000)
;;
;;     (.connect source filter)
;;     (.connect filter gain)
;;     (.connect gain @master-gain)
;;
;;     ;; Ultra-quick decay
;;     (set! (.-value (.-gain gain)) 0.3)
;;     (.exponentialRampToValueAtTime (.-gain gain) 0.01 (+ now 0.05))
;;
;;     (.start source now)
;;     (.stop source (+ now 0.05))))
;; ```

;; **Key technique**: Very high filter frequency (8000Hz) creates that metallic shimmer!

;; ## Beat Sequencer

;; A sequencer plays patterns of drums in a loop:

;; ### Pattern Representation

;; ```clojure
;; (def sequencer-state
;;   (r/atom {:playing false
;;            :bpm 120
;;            :current-step 0
;;            :pattern {:kick  [1 0 0 0 1 0 0 0 1 0 0 0 1 0 0 0]
;;                      :snare [0 0 0 0 1 0 0 0 0 0 0 0 1 0 0 0]
;;                      :hihat [1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0]}
;;            :interval-id nil}))
;; ```

;; Each track has 16 steps: `1` means play, `0` means silence.

;; ### BPM to Milliseconds

;; Converting beats per minute to step timing:

;; ```clojure
;; ;; BPM = beats per minute
;; ;; We have 4 steps per beat (16th notes)
;; ;; So: 60000 / (BPM × 4) = milliseconds per step
;;
;; (let [bpm 120
;;       interval-ms (/ 60000 (* bpm 4))]  ; = 125ms at 120 BPM
;;   interval-ms)
;; ```

;; ### Playback Loop

;; ```clojure
;; (defn play-sequencer-step []
;;   (let [{:keys [current-step pattern]} @sequencer-state]
;;     ;; Play active drums for this step
;;     (when (nth (:kick pattern) current-step)
;;       (play-kick))
;;     (when (nth (:snare pattern) current-step)
;;       (play-snare))
;;     (when (nth (:hihat pattern) current-step)
;;       (play-hihat))
;;     ;; Advance to next step (wrap at 16)
;;     (swap! sequencer-state update :current-step #(mod (inc %) 16))))
;;
;; (defn start-sequencer []
;;   (when-not (:playing @sequencer-state)
;;     (let [bpm (:bpm @sequencer-state)
;;           interval-ms (/ 60000 (* bpm 4))
;;           interval-id (js/setInterval play-sequencer-step interval-ms)]
;;       (swap! sequencer-state assoc
;;              :playing true
;;              :interval-id interval-id))))
;; ```

;; ### Pattern Editing

;; Toggle steps on/off:
;; ```clojure
;; (defn toggle-sequencer-step
;;   [& {:keys [track step]}]
;;   (swap! sequencer-state update-in [:pattern track step] not))
;;
;; ;; Usage: toggle kick on step 4
;; (toggle-sequencer-step :track :kick :step 4)
;; ```

;; ## Volume Control

;; The master gain node controls overall volume:

;; ```clojure
;; (defonce master-gain (atom nil))
;;
;; ;; Initialize with audio context
;; (defn init-audio-context! []
;;   (let [ctx (js/AudioContext.)
;;         gain (.createGain ctx)]
;;     (.connect gain (.-destination ctx))
;;     (set! (.-value (.-gain gain)) 0.5)  ; 50% volume
;;     (reset! audio-context ctx)
;;     (reset! master-gain gain)))
;;
;; ;; Set volume (0-100)
;; (defn set-master-volume
;;   [& {:keys [volume]}]
;;   (when @master-gain
;;     (let [gain-value (/ volume 100)]
;;       (set! (.-value (.-gain @master-gain)) gain-value))))
;; ```

;; All audio nodes connect through the master gain, so this affects everything!

;; ## State Management Architecture

^:kindly/hide-code
(kind/mermaid
 "graph TD
    A[Audio Context] --> B[Master Gain]
    B --> C[Oscillators]
    B --> D[Buffer Sources]
    B --> E[Continuous Tones]

    F[Sequencer State] --> G[Pattern Data]
    F --> H[Current Step]
    F --> I[Playing Status]
    G --> J[Playback Loop]
    H --> J
    I --> J

    J --> K[Trigger Drums]
    K --> D

    L[UI Events] --> M[Play Note]
    L --> N[Play Effect]
    L --> O[Toggle Step]
    M --> C
    N --> C
    O --> G

    style A fill:#4caf50
    style F fill:#2196f3
    style L fill:#ff9800")

;; ## Learning Points

;; This project teaches several important concepts:

;; ### Audio Programming
;; - **Node-Based Processing**: Audio flows through connected nodes
;; - **Envelope Shaping**: ADSR controls how sounds evolve
;; - **Frequency Relationships**: Musical intervals are mathematical ratios
;; - **Synthesis Techniques**: Different methods create different timbres
;; - **Noise Generation**: Random data creates percussive sounds

;; ### Music Theory
;; - **Chromatic Scale**: 12 semitones in an octave
;; - **Equal Temperament**: Modern tuning system
;; - **Intervals**: Relationships between notes
;; - **Rhythm**: Time-based patterns in music
;; - **Tempo**: Beats per minute determines speed

;; ### ClojureScript Patterns
;; - **Keyword Arguments**: Clear, self-documenting function calls
;; - **Atoms for State**: Reactive state management with Reagent
;; - **Functional Composition**: Pure functions for audio logic
;; - **Side Effect Isolation**: Audio I/O at program boundaries

;; ### Browser APIs
;; - **AudioContext Lifecycle**: When and how to initialize
;; - **Node Connections**: Building audio graphs
;; - **Timing and Scheduling**: Precise audio timing
;; - **Performance**: Efficient buffer management

;; ## Try It Live!

;; The complete audio playground is embedded below. Works on desktop and mobile!

;; **Instructions:**
;; - Click sound effect buttons to hear different UI feedback sounds
;; - Play the musical keyboard (C4-C5 notes)
;; - Try the melody buttons (C Major chord and scale)
;; - Adjust the volume slider to control all sounds
;; - All sounds generated in real-time - no audio files!

^:kindly/hide-code
(kind/hiccup
 [:div {:style {:margin "2rem 0"
                :padding "2rem"
                :border "2px solid #e9ecef"
                :border-radius "8px"
                :background-color "#f8f9fa"}}
  [:div#audio-playground-root {:style {:min-height "800px"}}]
  [:script {:type "application/x-scittle"
            :src "audio_playground.cljs"}]])

;; ## Extending the Audio Playground

;; Here are ideas to enhance this audio playground:

;; ### 1. More Instruments

;; **Bass Synthesizer:**

;; - Low-frequency oscillators (40-200Hz)
;; - Sawtooth or square waves for richness
;; - Envelope with longer release
;; - Portamento for smooth pitch slides

;; **Lead Synthesizer:**

;; - Sawtooth or pulse waves
;; - LFO (Low-Frequency Oscillator) for vibrato
;; - Filter envelope for brightness changes
;; - Higher octaves for lead melodies

;; **Chord Pads:**

;; - Multiple oscillators playing simultaneously
;; - Slow attack and release (soft envelope)
;; - Detuned oscillators for richness
;; - Lower volume for background texture

;; ### 2. Audio Effects

;; **Reverb:**

;; - Use ConvolverNode with impulse response
;; - Simulates acoustic spaces
;; - Adds depth and ambiance

;; **Delay/Echo:**

;; - DelayNode for time-based effects
;; - Feedback loop for multiple echoes
;; - Adjust delay time and feedback amount

;; **Filters:**

;; - BiquadFilterNode (lowpass, highpass, bandpass)
;; - Sweep filter frequency for movement
;; - Resonance for emphasis

;; **Distortion:**

;; - WaveShaperNode for overdrive
;; - Clip waveforms for saturation
;; - Add harmonics for grit

;; ### 3. Advanced Sequencer Features

;; **Multiple Patterns:**
;; ```clojure
;; (def pattern-banks
;;   (atom {:pattern-1 {...}
;;          :pattern-2 {...}
;;          :pattern-3 {...}}))
;; ```

;; **Pattern Chaining:**

;; - Queue patterns to play in sequence
;; - Loop through pattern lists
;; - Song mode for composition

;; **Swing/Groove:**

;; - Delay every other step slightly
;; - Creates human feel
;; - Adjust swing amount

;; **Velocity/Accent:**

;; - Vary volume per step
;; - Emphasize certain beats
;; - Add dynamics

;; ### 4. Music Theory Tools

;; **Scales and Modes:**
;; ```clojure
;; (def scales
;;   {:major     [0 2 4 5 7 9 11]
;;    :minor     [0 2 3 5 7 8 10]
;;    :pentatonic [0 2 4 7 9]})
;;
;; (defn generate-scale [root scale-type]
;;   (map #(+ root %) (get scales scale-type)))
;; ```

;; **Chord Generator:**

;; - Major/minor triads
;; - 7th chords
;; - Chord progressions
;; - Arpeggios

;; **Key Transposition:**

;; - Shift all notes by semitones
;; - Change key while preserving intervals
;; - Modal interchange

;; ### 5. Visualization

;; **Frequency Spectrum Analyzer:**
;; ```clojure
;; (defonce analyser-node (atom nil))
;;
;; ;; Create analyzer
;; (let [analyser (.createAnalyser @audio-context)]
;;   (set! (.-fftSize analyser) 2048)
;;   (.connect @master-gain analyser)
;;   (reset! analyser-node analyser))
;;
;; ;; Get frequency data
;; (defn get-frequency-data []
;;   (let [buffer-length (.-frequencyBinCount @analyser-node)
;;         data-array (js/Uint8Array. buffer-length)]
;;     (.getByteFrequencyData @analyser-node data-array)
;;     (vec data-array)))
;; ```

;; **Waveform Display:**

;; - Show time-domain audio data
;; - Canvas-based visualization
;; - Real-time updates

;; **Piano Roll:**

;; - Vertical time axis
;; - Horizontal pitch axis
;; - Visual note editing

;; ### 6. User Experience

;; **Preset Patterns:**

;; - Save favorite patterns
;; - Load common rhythms
;; - Share patterns via URL

;; **Keyboard Shortcuts:**

;; - Computer keyboard as piano
;; - Number keys for drums
;; - Space for play/stop

;; **MIDI Support:**

;; - Connect MIDI keyboards
;; - Send MIDI to DAWs
;; - Record performances

;; **Recording:**

;; - MediaRecorder API
;; - Save as WAV/MP3
;; - Export patterns

;; ## Performance Considerations

;; ### Optimize Audio Generation

;; **Buffer Reuse:**
;; ```clojure
;; ;; Don't regenerate noise every time
;; (defonce noise-buffers
;;   (atom {:short (create-noise-buffer :duration 0.05)
;;          :long  (create-noise-buffer :duration 0.1)}))
;; ```

;; **Node Pooling:**
;; - Reuse gain nodes
;; - Limit active oscillators
;; - Clean up stopped nodes

;; **Timing Precision:**
;; ```clojure
;; ;; Use AudioContext time, not js/setTimeout
;; (let [now (.-currentTime @audio-context)]
;;   (.start osc now)
;;   (.stop osc (+ now duration)))
;; ```

;; ### Browser Compatibility

;; **Handle Autoplay Policies:**
;; ```clojure
;; ;; Initialize on user interaction
;; (.addEventListener js/document \"click\"
;;   (fn []
;;     (when (= (.-state @audio-context) \"suspended\")
;;       (.resume @audio-context))))
;; ```

;; **Feature Detection:**
;; ```clojure
;; (when (exists? js/AudioContext)
;;   (init-audio-context!))
;; ```

;; ## Key Takeaways

;; Building this audio playground demonstrates:

;; 1. **Web Audio API Mastery** - Creating complex audio without external files
;; 2. **Sound Synthesis** - Understanding oscillators, envelopes, and filters
;; 3. **Music Theory** - Applying frequency relationships and rhythm
;; 4. **State Management** - Reactive patterns with Reagent atoms
;; 5. **Functional Patterns** - Pure functions with keyword arguments
;; 6. **Browser APIs** - Leveraging modern web platform capabilities
;; 7. **Real-time Audio** - Precise timing and scheduling
;; 8. **Educational Value** - Teaching through interactive examples

;; ## Technical Highlights

;; ### Clean Function Design

;; All functions use keyword arguments for clarity:

;; ```clojure
;; ;; Before: positional arguments (confusing)
;; (create-oscillator 440 \"sine\" 0.2 0.01 0.1 0.3)
;;
;; ;; After: keyword arguments (clear)
;; (create-oscillator :frequency 440
;;                    :type \"sine\"
;;                    :duration 0.2
;;                    :attack 0.01
;;                    :release 0.1
;;                    :gain-value 0.3)
;; ```

;; ### Composable Audio Functions

;; Build complex sounds from simple building blocks:

;; ```clojure
;; ;; Combine multiple oscillators
;; (defn play-chord []
;;   (create-oscillator :frequency 261.63)  ; C
;;   (create-oscillator :frequency 329.63)  ; E
;;   (create-oscillator :frequency 392.00)) ; G
;;
;; ;; Sequence sounds in time
;; (defn play-melody []
;;   (doseq [[idx note] (map-indexed vector [:C4 :E4 :G4])]
;;     (js/setTimeout
;;      #(play-note :note note)
;;      (* idx 250))))
;; ```

;; ### Functional State Updates

;; Pure functions for state transformations:

;; ```clojure
;; ;; Toggle sequencer step (pure function)
;; (defn toggle-step [state track step]
;;   (update-in state [:pattern track step] not))
;;
;; ;; Use with swap!
;; (swap! sequencer-state toggle-step :kick 4)
;; ```

;; ## Resources and Links

;; - [Web Audio API Documentation](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API)
;; - [Learning Synths by Ableton](https://learningsynths.ableton.com/) - Interactive synthesis tutorial
;; - [Web Audio API Spec](https://www.w3.org/TR/webaudio/) - Official W3C specification
;; - [Scittle Documentation](https://github.com/babashka/scittle)
;; - [Reagent Guide](https://reagent-project.github.io/)
;; - [Sound on Sound](https://www.soundonsound.com/) - Synthesis tutorials

;; ## Conclusion

;; This audio playground demonstrates that sophisticated sound synthesis is possible entirely in the browser using ClojureScript and Scittle. With zero build tools and no external audio files, we've created:

;; - A complete sound effects library for UI feedback
;; - A playable musical keyboard with melody support
;; - Synthesized drum sounds (kick, snare, hi-hat)
;; - A functional 16-step beat sequencer
;; - Volume control and state management

;; ### From Basics to Music

;; What started as simple oscillators became a foundation for:
;;
;; - **Understanding Sound**: Frequency, amplitude, and waveforms
;; - **Musical Expression**: Notes, scales, and rhythms
;; - **Interactive Audio**: Real-time synthesis and control
;; - **Creative Coding**: Building instruments with code

;; The Web Audio API offers immense creative potential. Whether you're:
;;
;; - Adding sound to games
;; - Building musical instruments
;; - Teaching audio programming
;; - Exploring creative coding
;; - Learning music theory

;; This playground provides a solid foundation. The functional programming approach with Reagent atoms makes complex audio interactions manageable, while keyword arguments keep the code readable and maintainable.

;; ### Key Insights

;; - **Audio is Data**: Sound waves are just numbers changing over time
;; - **Synthesis is Mathematics**: Frequencies, ratios, and envelopes are pure math
;; - **Browser is Capable**: Modern web APIs rival desktop audio software
;; - **Code is Instrument**: Programming opens infinite sonic possibilities

;; Now go forth and make some noise! Create unique sound effects, compose melodies, sequence beats, and explore the endless possibilities of browser-based audio synthesis! 🎵🎹🥁

;; ---

;; *Want to see more browser-native ClojureScript projects? Check out my other articles on [ClojureCivitas](https://clojurecivitas.github.io/) where we explore audio, games, and interactive applications - all without build tools!*
