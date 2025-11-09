^{:kindly/hide-code true
  :clay {:title "DSP Study Group - Intro: Building the Violin Sound from Sine Waves"
         :quarto {:author [:eugnes :daslu]
                  :description "Starting the journey of DSP in Clojure."
                  :category :clojure
                  :type :post
                  :date "2025-11-10"
                  :tags [:dsp :math :music]}}}
(ns dsp.intro
  (:require [scicloj.kindly.v4.kind :as kind]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [clojure.math :as math]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]))

^:kindly/hide-code
(kind/hiccup
 [:style
  ".clay-dataset {
  max-height:400px; 
  overflow-y: auto;
}
"])

;; **Notes from the [Scicloj DSP Study Group](https://scicloj.github.io/docs/community/groups/dsp-study/)**  
;; *First meeting - Nov. 2nd 2025*
;;
;; Welcome! These are notes from our first study group session, where we're learning
;; digital signal processing together using Clojure. We're following the excellent book
;; [**Think DSP** by Allen B. Downey](https://greenteapress.com/wp/think-dsp/) (available free online).
;;
;; **Huge thanks to Professor Downey** for writing such an accessible and free introduction to DSP, and for sharing with us the work-in-progress notebooks of [Think DSP 2](https://allendowney.github.io/ThinkDSP2/index.html).
;;
;;;; ## What is Digital Signal Processing?
;;
;; Sound waves are continuous vibrations in the air. To work with them on a computer,
;; we need to **sample** them - take measurements at regular intervals. The **sample rate**
;; tells us how many measurements per second. CD-quality audio uses 44,100 samples per second.
;;
;; This session covers concepts from **Chapter 1: Sounds and Signals** of Think DSP.

;; ## Clojure Libraries We're Using
;;
;; To work with DSP in Clojure, we're using tools from the Scicloj ecosystem:
;;
;; - **[Kindly](https://scicloj.github.io/kindly-noted/kindly)** - Visualization protocol that renders our data as interactive HTML elements (through Clay)
;; - **[dtype-next](https://github.com/cnuernber/dtype-next)** - Efficient numerical arrays and vectorized operations (like NumPy for Clojure)
;; - **[Tablecloth](https://scicloj.github.io/tablecloth/)** - DataFrame library for data manipulation and transformation
;; - **[Tableplot](https://scicloj.github.io/tableplot/)** - Declarative plotting library built on Plotly
;;
;; These libraries let us work with large audio datasets efficiently while keeping our code
;; clean and interactive. The `tech.v3.datatype.functional` namespace (aliased as `dfn`) provides
;; vectorized math operations that work on entire arrays at once - essential for DSP!

(require '[scicloj.kindly.v4.kind :as kind]
         '[tech.v3.datatype :as dtype]
         '[tech.v3.datatype.functional :as dfn]
         '[clojure.math :as math]
         '[tablecloth.api :as tc]
         '[scicloj.tableplot.v1.plotly :as plotly])

;; ## Step 1: Creating Our First Sound Wave
;;
;; Let's start by generating a simple pure tone - a sine wave at 440 Hz (the note A4,
;; which orchestras use for tuning). This is the foundation of all audio synthesis.

(def sample-rate 44100.0)

;; Now let's create the actual waveform. We'll generate 10 seconds of audio:
;;
;; - Create a time axis from 0 to 10 seconds
;; - Calculate the sine wave: amplitude × sin(2π × frequency × time)
;; - Store everything in a dataset so we can plot and analyze it

(def example-wave
  (let [duration 10
        num-samples (* duration sample-rate)
        ;; Create a time vector: [0, 1/44100, 2/44100, ..., 10]
        time (dtype/make-reader :float32
                                num-samples
                                (/ idx sample-rate))
        freq 440 ;; A4 - the tuning note
        amp 3800 ;; Amplitude (loudness)
        ;; Generate the sine wave
        value (-> time
                  (dfn/* (* 2 Math/PI freq)) ;; Convert time to phase
                  dfn/sin ;; Calculate sine
                  (dfn/* amp))] ;; Scale by amplitude
    (tc/dataset {:time time
                 :value value})))

;; Let's look at our dataset
example-wave

;; ## Visualizing the Wave
;;
;; Here are the first 200 samples (about 4.5 milliseconds of audio).
;; You can see the smooth oscillation of the sine wave.

(-> example-wave
    (tc/head 200)
    (plotly/layer-line {:=x :time
                        :=y :value}))

;; ## Hearing the Sound
;;
;; Seeing is believing, but hearing is more fun! Let's create an audio player
;; that will render this waveform as actual sound in your browser.

(defn audio [samples]
  (with-meta
    {:samples samples
     :sample-rate sample-rate}
    {:kind/audio true}))

;; Click play to hear the 440 Hz tone!
(-> example-wave
    :value
    audio)

;; ## Step 2: Creating Complex Sounds - Violin Synthesis
;;
;; A pure sine wave sounds very artificial - like an old telephone tone.
;; Real instruments are rich and complex because they produce many frequencies at once.
;;
;; The secret? **Additive synthesis** - combining multiple sine waves (called harmonics).
;; A violin playing A4 doesn't just produce 440 Hz - it produces 440 Hz plus harmonics
;; at 880 Hz, 1320 Hz, 1760 Hz, 2200 Hz, and more. Each harmonic has its own amplitude.
;;
;; Here are the main frequency components that give a violin its characteristic sound:

(def violin-components
  [[:A4 440 3800] ;; Fundamental (the note we hear)
   [:A5 880 2750] ;; 2nd harmonic (octave above)
   [:E6 1320 600] ;; 3rd harmonic
   [:A6 1760 700] ;; 4th harmonic
   [:C#7 2200 1900]]);; 5th harmonic

;; Now let's generate all five harmonics as separate columns in a dataset.
;; Each harmonic is a sine wave at its own frequency and amplitude.

(def violin-components-dataset
  (let [duration 10
        num-samples (* duration sample-rate)
        time (dtype/make-reader :float32
                                num-samples
                                (/ idx sample-rate))]
    (->> violin-components
         ;; For each [label frequency amplitude] triple, generate a sine wave
         (map (fn [[label freq amp]]
                [label (-> time
                           (dfn/* (* 2 math/PI freq))
                           dfn/sin
                           (dfn/* amp))]))
         (into {:time time})
         tc/dataset)))

;; Our dataset now has columns: :time, :A4, :A5, :E6, :A6, :C#7
violin-components-dataset

;; Let's visualize just the fundamental (A4) first:

(-> violin-components-dataset
    (tc/head 200)
    (plotly/layer-line {:=x :time
                        :=y :A4}))

;; Now let's see all five harmonics together. First, we'll reshape the data
;; from wide format (one column per harmonic) to long format (easier to plot):

(-> violin-components-dataset
    (tc/head 200)
    (tc/pivot->longer (complement #{:time})))

;; Plot all harmonics on the same chart, colored by frequency.
;; Notice the mathematical relationships between these waves:
;;
;; - **A5 (880 Hz)** oscillates exactly twice as fast as A4 (440 Hz) - this is **one octave** higher
;; - **E6 (1320 Hz)** is 3× the fundamental frequency (the 3rd harmonic)
;; - **A6 (1760 Hz)** is 4× the fundamental (the 4th harmonic, two octaves up)
;; - **C#7 (2200 Hz)** is 5× the fundamental (the 5th harmonic)
;;
;; These integer multiples create the **harmonic series**, which is fundamental to music theory
;; and acoustics. When waves are related by simple ratios like 2:1, 3:1, 4:1, they blend together
;; harmoniously - this is why octaves and perfect fifths sound so consonant!

(-> violin-components-dataset
    (tc/head 200)
    (tc/pivot->longer (complement #{:time}))
    (tc/rename-columns {:$value :value})
    (plotly/layer-line {:=x :time
                        :=y :value
                        :=color :$column}))

;; ## Step 3: Combining the Harmonics
;;
;; Now for the magic moment! When we add all five sine waves together,
;; we get a complex waveform that sounds like a violin. This is the essence
;; of additive synthesis - rich sounds emerge from simple building blocks.

(def violin-dataset
  (-> violin-components-dataset
      (tc/add-column :violin
                     ;; Add all five harmonics together
                     #(dfn/+ (:A4 %)
                             (:A5 %)
                             (:E6 %)
                             (:A6 %)
                             (:C#7 %)))))

;; Look at the combined waveform - it's much more complex than a pure sine wave!
;; The shape repeats at 440 Hz (the fundamental), but with rich texture from the harmonics.

(-> violin-dataset
    (tc/head 200)
    (plotly/layer-line {:=x :time
                        :=y :violin}))

;; ## Listen to the Violin Sound
;;
;; This is the payoff! Compare this to the pure tone we heard earlier.
;; The violin sound is warmer and more musical because of the harmonics.
;; (We divide by 7000 to normalize the amplitude after adding the components)

(-> violin-dataset
    :violin
    (dfn// 7000.0)
    audio)

;; ## What We Learned
;;
;; In this first session, we covered a few topics from Chapter 1 of Think DSP:
;;
;; - **Sampling and sample rates** - Converting continuous signals to discrete measurements
;; - **Generating sine waves** - The building blocks of all sound
;; - **Additive synthesis with harmonics** - Creating complex sounds from simple components
;;
;; ## Next Steps
;;
;; In our next study group meetings, we'll explore the book step by step, and learn more about sounds and signals,
;; harmonics and the Forier transform, non-periodic signals and spectograms, noise and filtering, and more.
;;
;; Join us at the [Scicloj DSP Study Group](https://scicloj.github.io/docs/community/groups/dsp-study/)!
;;
;; ---
;;
;; *Again, huge thanks to Allen B. Downey for Think DSP. If you find this resource valuable,
;; consider [supporting his work](https://greenteapress.com/wp/) or sharing it with others.*




