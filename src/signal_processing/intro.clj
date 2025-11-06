^{:kindly/hide-code true
  :clay {:title "DSP Intro"
         :quarto {:author [:eugnes :daslu]
                  :description "Starting the journey of DSP in Clojure."
                  :category :clojure
                  :type :post
                  :date "2025-11-02"
                  :tags [:dsp :math :music]
                  :draft true}}}
(ns signal-processing.intro
  (:require [scicloj.kindly.v4.kind :as kind]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [clojure.math :as math]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]))

;; # Introduction to Digital Signal Processing
;;
;; Welcome! Let's explore how to generate and manipulate audio signals in Clojure.
;; We'll start simple - creating a single tone - then build up to synthesizing
;; a more complex sound like a violin.
;;
;; ## What is Digital Signal Processing?
;;
;; Sound waves are continuous vibrations in the air. To work with them on a computer,
;; we need to **sample** them - take measurements at regular intervals. The **sample rate**
;; tells us how many measurements per second. CD-quality audio uses 44,100 samples per second.

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
;; Notice how the higher harmonics oscillate faster!

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

;; ## What's Next?
;;
;; You've now learned the fundamentals of digital signal processing:
;; - Sampling and sample rates
;; - Generating sine waves
;; - Additive synthesis with harmonics
;;
;; From here, you could explore:
;; - Envelopes (attack, decay, sustain, release) to shape sounds over time
;; - Filters (low-pass, high-pass) to sculpt frequency content
;; - Modulation (vibrato, tremolo) for expressive effects
;; - The Fourier transform to analyze existing sounds
;;
;; Happy signal processing!




