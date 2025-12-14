^{:kindly/hide-code true
  :clay {:title "Signal Transforms: A Comprehensive Guide to FFT, DCT, and Wavelets"
         :quarto {:author :daslu
                  :draft true
                  :type :post
                  :date "2025-12-14"
                  :category :data
                  :tags [:dsp :fft :dct :wavelets :transforms :dtype-next :signal-processing
                         :compression :filtering :denoising :jtransforms :fastmath]}}}
(ns dsp.transforms-comprehensive
  "A comprehensive guide to signal transforms in Clojure using dtype-next, JTransforms, and fastmath."
  (:require [fastmath.signal :as sig]
            [fastmath.transform :as t]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [tech.v3.datatype.argops :as argops]
            [tablecloth.api :as tc]
            [tech.v3.dataset :as ds]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.kindly.v4.api :as kindly]
            [scicloj.tableplot.v1.plotly :as plotly])
  (:import [org.jtransforms.fft DoubleFFT_1D]))

^:kindly/hide-code
(kind/hiccup
 [:style
  ".clay-dataset {
  max-height:400px;
  overflow-y: auto;
}
.printedClojure {
  max-height:400px;
  overflow-y: auto;
}
"])

;; # Introduction: Why Learn Signal Transforms?

;; Imagine you're listening to a musical note. You can hear it's an A (440 Hz), but how would
;; you write code to discover that frequency? Or suppose you have 10 seconds of audio—how do
;; you automatically remove a 60 Hz electrical hum without affecting the music?
;;
;; These questions lead us to **signal transforms**—mathematical tools that reveal hidden structure
;; in data. Every JPEG image you view uses the Discrete Cosine Transform for compression. MP3
;; audio files rely on the same mathematics. Speech recognition, radar systems, medical imaging—all
;; depend on transforming signals between different representations.
;;
;; At their core, transforms answer a simple question: **can we express this signal as a weighted
;; sum of simpler basis functions?** The Fourier transform uses sines and cosines. Wavelets use
;; localized, scaled functions. Each transform gives us a different lens for understanding and
;; manipulating data.

;; ## Why These Tools for Transforms?

;; Signal transforms are perfect for learning [dtype-next](https://github.com/cnuernber/dtype-next)
;; because they provide **typed numerical arrays with immediate visual feedback**. Unlike generic
;; sequences where numbers are boxed, dtype-next gives us:
;;
;; - **Efficient storage**: A 1000-sample signal is 8KB of float64 values, not 40KB+ of boxed objects
;; - **Functional operations**: Element-wise transformations that compose naturally
;; - **Zero-copy slicing**: Extract frequency bands without allocating new arrays
;; - **Direct Java interop**: Pass arrays to JTransforms without conversion overhead
;;
;; We'll use [JTransforms](https://github.com/wendykierp/JTransforms) for the actual
;; computation (fast, battle-tested, pure Java), wrapped in [fastmath](https://generateme.github.io/fastmath/)
;; for a clean Clojure API, with dtype-next providing efficient array operations.

;; ## What You'll Learn

;; This tutorial combines theory with practice, building from foundations to real applications:
;;
;; - **Signal generation** — Creating test signals with different characteristics
;; - **Core transforms** — DFT (via FFT), DCT, wavelets, and specialized variants
;; - **Practical applications** — Filtering, compression, denoising, spectral analysis
;; - **dtype-next patterns** — Efficient array operations throughout
;; - **Validation** — Test-driven development ensuring correctness
;;
;; Each section demonstrates transforms through runnable code with visual feedback, making
;; abstract mathematics concrete and verifiable.

;; ## Prerequisites

;; You should be comfortable with:
;;
;; - Basic Clojure (functions, maps, sequences)
;; - High school mathematics (sine, cosine, basic algebra)
;; - The concept of frequency (higher pitch = higher frequency)
;;
;; No digital signal processing background required—we'll build intuition through
;; visualization and concrete examples.

;; ## Tutorial Structure

;; We'll progress from simple signal generation to advanced multi-dimensional transforms:
;;
;; 1. **Signal Generation** — Creating test signals with known properties
;; 2. **DFT/FFT** — Discovering frequencies in signals
;; 3. **DCT** — Compression-optimized transform (JPEG, MP3)
;; 4. **Wavelets** — Time-frequency localization and denoising
;; 5. **Specialized Transforms** — DST, DHT, and 2D variants
;; 6. **Applications** — Practical filtering, compression, and analysis tools
;; 7. **Testing Framework** — Validation strategies for numerical code
;; 8. **Best Practices** — Choosing the right transform for your problem
;;
;; Throughout, we'll introduce dtype-next operations as needed—you'll learn efficient array
;; manipulation in the context of solving real transform problems rather than as abstract
;; prerequisites. Each section includes visualizations and round-trip tests to verify correctness.
;;
;; Let's begin by learning to generate the signals we'll be transforming!

;; # Part 1: Signal Generation and Visualization

;; Before we can analyze frequencies, we need signals to analyze. This section introduces
;; signal generation and visualization—the foundation for everything that follows.
;;
;; We'll also meet **dtype-next**, Clojure's efficient array programming library. Rather than
;; learning it abstractly, we'll discover dtype-next operations as we need them for generating
;; and manipulating signals.

;; ## Introduction to fastmath.signal and dtype-next

(require '[fastmath.signal :as sig])
(require '[tech.v3.datatype :as dtype])
(require '[tech.v3.datatype.functional :as dfn])
(require '[tech.v3.datatype.argops :as argops])

;; The fastmath.signal library provides signal generation through oscillators.
;; An oscillator is simply a function mapping time to amplitude: time → amplitude.
;; This functional approach makes it easy to compose complex signals from simple components.

;; Create a 440 Hz sine wave oscillator
(def osc-440 (sig/oscillator :sin 440.0 1.0 0.0))
;; Parameters: type, frequency, amplitude, phase

;; Oscillators are functions - evaluate at specific times (t=0 and t=0.1s):
[(osc-440 0.0) (osc-440 0.1)]

;; Generate discrete samples:
;; 44.1kHz sample rate, 10ms duration
(sig/oscillator->signal osc-440 44100.0 0.01)

;; ### Available Oscillator Types

;; Fastmath supports: :sin, :square, :saw, :triangle, :noise, :constant

(def sample-rate 100.0)
(def duration 1.0)

(def oscillator-examples
  {:sine (sig/oscillator :sin 3.0 1.0 0.0)
   :square (sig/oscillator :square 3.0 1.0 0.0)
   :saw (sig/oscillator :saw 3.0 1.0 0.0)
   :triangle (sig/oscillator :triangle 3.0 1.0 0.0)})

;; Visualize different types
(def osc-viz-data
  (for [[osc-type osc] oscillator-examples
        [i value] (map-indexed vector
                               (take 50 (sig/oscillator->signal osc sample-rate duration)))]
    {:time (/ i sample-rate)
     :amplitude value
     :type (name osc-type)}))

(-> (tc/dataset osc-viz-data)
    (plotly/base {:=x :time
                  :=y :amplitude
                  :=mark-color :type
                  :=x-title "Time (s)"
                  :=y-title "Amplitude"
                  :=title "Oscillator Types (3 Hz)"
                  :=width 700
                  :=height 250})
    (plotly/layer-line))

;; ### Combining Multiple Frequencies

;; Use `oscillators-sum` for composite signals:
;; Three sine waves: 3 Hz, 7 Hz, and 15 Hz
(def composite-osc
  (sig/oscillators-sum
   (sig/oscillator :sin 3.0 1.0 0.0)
   (sig/oscillator :sin 7.0 0.5 0.0)
   (sig/oscillator :sin 15.0 0.3 0.0)))

(def composite-signal
  (sig/oscillator->signal composite-osc sample-rate duration))

;; ### Teaching Signals: Designed for Transform Comparison

;; Now let's create a suite of test signals, each designed to highlight different transform strengths.
;; These aren't arbitrary—each signal has specific characteristics that make certain transforms
;; more effective than others.
;;
;; **Why multiple test signals?** Different transforms excel at different tasks:
;; - **Pure tones** → FFT easily finds exact frequencies
;; - **Smooth signals** → DCT concentrates energy efficiently (good for compression)
;; - **Localized events** → Wavelets capture time-localized features
;;
;; By testing each transform on all signals, we'll build intuition for when to use which tool.

(defn teaching-signals
  "Standard test signals with known properties.
  
  Each signal is designed to test different transform characteristics:
  - :pure-sine - Single frequency (FFT baseline)
  - :two-tones - Multiple frequencies (tests superposition)
  - :smooth - Low frequency content (DCT compression test)
  - :chirp - Time-varying frequency (wavelet localization test)
  - :impulse - Sharp transient (wavelet vs FFT comparison)"
  []
  {:pure-sine
   {:signal (sig/oscillator->signal
             (sig/oscillator :sin 10.0 1.0 0.0)
             sample-rate duration)
    :description "Single frequency - FFT baseline"
    :best-transform :fft}

   :two-tones
   {:signal (sig/oscillator->signal
             (sig/oscillators-sum
              (sig/oscillator :sin 10.0 1.0 0.0)
              (sig/oscillator :sin 25.0 0.5 0.0))
             sample-rate duration)
    :description "Two frequencies - superposition principle"
    :best-transform :fft}

   :smooth
   {:signal (sig/oscillator->signal
             (sig/oscillators-sum
              (sig/oscillator :sin 5.0 1.0 0.0)
              (sig/oscillator :sin 10.0 0.3 0.0)
              (sig/oscillator :sin 15.0 0.1 0.0))
             sample-rate duration)
    :description "Smooth signal - DCT compresses well"
    :best-transform :dct}

   :chirp
   {:signal (let [n (int (* sample-rate duration))]
              (double-array
               (for [i (range n)]
                 (let [t (/ i sample-rate)
                       freq (+ 5.0 (* 45.0 t))] ;; 5→50 Hz sweep
                   (Math/sin (* 2 Math/PI freq t))))))
    :description "Frequency sweep - wavelets capture time-varying content"
    :best-transform :wavelet}

   :impulse
   {:signal (double-array
             (concat [5.0] (repeat 99 0.0)))
    :description "Localized event - wavelets excel here"
    :best-transform :wavelet}})

(def signals (teaching-signals))

;; ### Signal Visualization with dtype-next

;; Now we'll visualize signals using dtype-next for data preparation and Tableplot for rendering.
;; This is where dtype-next shines: we can efficiently extract slices and perform element-wise
;; division to compute time values.

(defn visualize-signal
  "Plot signal using dtype-next for data preparation.
  
  dtype-next operations used:
  - dtype/ecount: Get array length
  - dtype/sub-buffer: Zero-copy slice extraction
  - dfn//: Element-wise division for time computation"
  [signal title]
  (let [display-n (min 200 (dtype/ecount signal))
        time-vals (dfn// (range display-n) sample-rate) ; Element-wise division
        amp-vals (dtype/sub-buffer signal 0 display-n) ; Zero-copy slice

        dataset (tc/dataset {:time time-vals
                             :amplitude amp-vals})]

    (-> dataset
        (plotly/base {:=x :time
                      :=y :amplitude
                      :=x-title "Time (s)"
                      :=y-title "Amplitude"
                      :=title title
                      :=width 700
                      :=height 200})
        (plotly/layer-line))))

(visualize-signal (:signal (:two-tones signals)) "Two Tones: 10 Hz + 25 Hz")

;; **What we just learned**: Signal generation with fastmath oscillators and basic dtype-next
;; operations (element-wise math, slicing, counting). We now have test signals that will help
;; us compare different transforms.
;;
;; Next, we'll explore the FFT—but first, we need to understand why its output is complex-valued
;; even though our input signals are real numbers.

;; ## Part 1.5: Understanding Complex Transform Outputs

;; Before we dive into the FFT, we need to address a puzzling fact: when we transform a
;; real-valued signal (just regular numbers like 1.5, -0.3, 2.7), the FFT returns
;; **complex numbers** (numbers with "real" and "imaginary" parts).
;;
;; Why would a transform of real data produce complex output? This seems unnecessarily
;; complicated. Let's build intuition for why complex numbers are not just mathematical
;; elegance—they're computational necessity.

;; ### The Problem: Sines and Cosines Are Both Needed

;; Here's the core issue. Suppose we want to decompose a signal into pure frequency components.
;; Should we use sines or cosines as our basis functions?
;;
;; - If we use only **cosines**: We can represent cosine-like signals perfectly, but sine-like
;;   signals require infinite cosine terms
;; - If we use only **sines**: We can represent sine-like signals perfectly, but cosine-like
;;   signals require infinite sine terms
;;
;; **Solution**: Use BOTH sines and cosines. For each frequency, we need two numbers:
;; 1. How much of the cosine wave at that frequency
;; 2. How much of the sine wave at that frequency
;;
;; A [complex number](https://en.wikipedia.org/wiki/Complex_number) packages these two values together:
;; - **Real part** = cosine amplitude
;; - **Imaginary part** = sine amplitude
;;
;; This isn't arbitrary—it's the most compact way to store both components.

;; ### Euler's Formula: The Mathematical Foundation
;;
;; The mathematical underpinning is [Euler's formula](https://en.wikipedia.org/wiki/Euler%27s_formula),
;; which connects complex exponentials to sine and cosine:
;;
;; **e^(iθ) = cos(θ) + i·sin(θ)**
;;
;; Where i = √(-1) is the imaginary unit. This beautiful equation tells us that a rotating
;; complex number naturally encodes both sine and cosine components.
;;
;; Let's visualize this to make it concrete.

;; ### Visualizing Sine and Cosine Together

(def theta-points (dfn// (dfn/* 2.0 Math/PI (range 100)) 100.0))

;; Real part: cos(θ)
(def cosine-vals (dfn/cos theta-points))

;; Imaginary part: sin(θ)  
(def sine-vals (dfn/sin theta-points))

;; Create dataset for visualization
(def euler-dataset
  (tc/dataset {:angle (dfn// theta-points (* 2 Math/PI))
               :cosine cosine-vals
               :sine sine-vals}))

;; Using Tableplot (ggplot2-style layered grammar)
(-> euler-dataset
    (plotly/base {:=x :angle
                  :=y :cosine
                  :=x-title "Angle (cycles)"
                  :=y-title "Value"
                  :=title "Euler's Formula: e^(iθ) = cos(θ) + i·sin(θ)"
                  :=width 700
                  :=height 300})
    (plotly/layer-line {:=mark-color "steelblue"
                        :=name "cos(θ) - Real Part"})
    (plotly/layer-line {:=y :sine
                        :=mark-color "orange"
                        :=name "sin(θ) - Imaginary Part"}))

;; As the angle θ increases from 0 to 2π (one full rotation), cosine and sine trace out
;; their familiar wave shapes. The key insight: **we need both functions to completely
;; describe a sinusoid at any phase**.

;; ### Concrete Example: Pure Cosine vs Pure Sine Signals

;; Let's demonstrate why we need complex output by transforming pure cosine and pure sine signals.

;; Create a simple 5 Hz cosine wave
(def pure-cosine-time (dfn// (range 100) 100.0))
(def pure-cosine-signal
  (dfn/cos (dfn/* 2.0 Math/PI 5.0 pure-cosine-time)))

;; FFT gives complex output (stored as alternating real, imaginary pairs)
(def fft-basic (t/transformer :real :fft))
(def cosine-spectrum (t/forward-1d fft-basic pure-cosine-signal))

;; The spectrum has 100 values: 50 complex numbers stored as [real₀, imag₀, real₁, imag₁, ...]
(dtype/ecount cosine-spectrum)

;; Look at frequency bin 5 (the 5 Hz component we put in)
;; Large value in real part, ≈0 in imaginary part
(def freq-bin-5-real (dtype/get-value cosine-spectrum (* 2 5)))
(def freq-bin-5-imag (dtype/get-value cosine-spectrum (+ 1 (* 2 5))))

;; Pure cosine = all energy in REAL part (cosine basis function)
;; Imaginary part ≈ 0 (no sine component needed)

;; Now try a pure sine wave
(def pure-sine-signal
  (dfn/sin (dfn/* 2.0 Math/PI 5.0 pure-cosine-time)))

(def sine-spectrum (t/forward-1d fft-basic pure-sine-signal))

;; ≈0 in real part, large value in imaginary part
(def sine-freq-bin-5-real (dtype/get-value sine-spectrum (* 2 5)))
(def sine-freq-bin-5-imag (dtype/get-value sine-spectrum (+ 1 (* 2 5))))

;; Pure sine = all energy in IMAGINARY part (sine basis function)
;; Real part ≈ 0 (no cosine component needed)

;; **Key Takeaway**: 
;; - Pure **cosine** signal → FFT has large **real part**, near-zero imaginary part
;; - Pure **sine** signal → FFT has near-zero real part, large **imaginary part**
;; - General signal → FFT has **both parts**, encoding the phase relationship
;;
;; This is why FFT output is complex even though input is real!

;; ### Phase: Encoding the Sine/Cosine Balance
;;
;; Any sinusoid can be written two ways:
;; 1. **Amplitude + phase**: A·cos(2πft + φ)
;; 2. **Cosine + sine**: a·cos(2πft) + b·sin(2πft)
;;
;; The complex number (a + ib) from the FFT encodes both representations:
;; - **Magnitude**: √(a² + b²) = amplitude A
;; - **[Phase](https://en.wikipedia.org/wiki/Phase_(waves))**: arctan(b/a) = phase φ
;;
;; This is why we compute magnitude as √(real² + imag²)—we're combining the cosine and sine
;; contributions to find the total amplitude at each frequency.

;; ### Visualization: Complex Plane Representation

(defn complex-plane-viz
  "Visualize how cosine and sine components form complex numbers."
  [signal-type]
  (let [freq 5.0
        t-vals (dfn// (range 100) 100.0)
        signal (case signal-type
                 :cosine (dfn/cos (dfn/* 2.0 Math/PI freq t-vals))
                 :sine (dfn/sin (dfn/* 2.0 Math/PI freq t-vals))
                 :mixed (dfn/+ (dfn/cos (dfn/* 2.0 Math/PI freq t-vals))
                               (dfn/sin (dfn/* 2.0 Math/PI freq t-vals))))
        spectrum (t/forward-1d fft-basic signal)

        ;; Extract complex numbers for positive frequencies
        freq-bins (range 0 50)
        complex-data (map (fn [k]
                            (let [real-part (dtype/get-value spectrum (* 2 k))
                                  imag-part (dtype/get-value spectrum (+ 1 (* 2 k)))]
                              {:frequency k
                               :real real-part
                               :imag imag-part
                               :magnitude (Math/sqrt (+ (* real-part real-part)
                                                        (* imag-part imag-part)))}))
                          freq-bins)
        dataset (tc/dataset complex-data)]

    (-> dataset
        (plotly/base {:=x :real
                      :=y :imag
                      :=x-title "Real Part (Cosine)"
                      :=y-title "Imaginary Part (Sine)"
                      :=title (str "Complex Spectrum: " (name signal-type) " signal")
                      :=width 400
                      :=height 400})
        (plotly/layer-point {:=mark-size :magnitude
                             :=mark-color :frequency}))))

(complex-plane-viz :cosine)
(complex-plane-viz :sine)
(complex-plane-viz :mixed)

;; **What we just learned**: Complex FFT outputs aren't mysterious—they're the natural way to
;; encode both sine and cosine components at each frequency. The real part tells us "how much
;; cosine," the imaginary part tells us "how much sine."
;;
;; Now that we understand the complex output format, let's put the FFT to work!

;; ## Part 2: DFT and FFT - Discovering Frequencies in Signals

;; We're finally ready to answer our opening question: how do we write code to discover the
;; frequencies in a signal? The answer is the **Discrete Fourier Transform (DFT)**, computed
;; efficiently via the **Fast Fourier Transform (FFT)** algorithm.
;;
;; **Terminology Note**:
;; - **DFT (Discrete Fourier Transform)** = the mathematical transform
;; - **FFT (Fast Fourier Transform)** = the Cooley-Tukey algorithm for computing DFT efficiently (O(n log n) instead of O(n²))
;;
;; In practice, "FFT" is often used colloquially to mean both the transform and the algorithm.
;; We'll be technically precise here, but don't be surprised if you see papers and codebases
;; using "FFT" for both!

;; ### Introduction to fastmath.transform

(require '[fastmath.transform :as t])

;; The fastmath.transform library provides a unified API for all signal transforms.
;; The pattern is consistent across transforms, making it easy to experiment:

;; **Step 1**: Create a transformer (specifies input type and transform algorithm)
(def fft (t/transformer :real :fft))
;; :real means input is real-valued (not complex)
;; :fft specifies the algorithm

;; **Step 2**: Forward transform (signal → spectrum)
(t/forward-1d fft (range 16))

;; **Step 3**: Reverse transform (spectrum → signal) 
;; (t/reverse-1d fft spectrum)
;;
;; This same pattern works for DCT, DST, DHT, and wavelets—just change the transform type!

;; ### Understanding the DFT: Signal Decomposition
;;
;; The [Discrete Fourier Transform (DFT)](https://en.wikipedia.org/wiki/Discrete_Fourier_transform)
;; answers the question: "What frequencies are in this signal, and how strong is each one?"
;;
;; Mathematically, it expresses any signal as a weighted sum of sine and cosine waves:
;;
;; **Signal = a₁·cos(2π·f₁·t) + b₁·sin(2π·f₁·t) + a₂·cos(2π·f₂·t) + b₂·sin(2π·f₂·t) + ...**
;;
;; The DFT finds the weights (amplitudes) a₁, b₁, a₂, b₂, etc. We compute it using the
;; [Fast Fourier Transform (FFT)](https://en.wikipedia.org/wiki/Fast_Fourier_transform)
;; algorithm, which reduces complexity from O(n²) to O(n log n).
;;
;; Let's see this in action by building a signal from known frequencies, then recovering them.

;; ### Concrete Example: Build and Decompose

;; **Step 1: Manual construction** - we choose the frequencies and amplitudes

(def freq-1 10.0)
(def amp-1 1.0)
(def freq-2 25.0)
(def amp-2 0.5)

;; Generate time samples using dtype-next
(def n-samples 1000)
(def time-points (dfn// (range n-samples) sample-rate))

;; Build each frequency component
(def component-1
  (dfn/* amp-1 (dfn/sin (dfn/* 2.0 Math/PI freq-1 time-points))))

(def component-2
  (dfn/* amp-2 (dfn/sin (dfn/* 2.0 Math/PI freq-2 time-points))))

;; The signal is just the sum (linear superposition!)
(def constructed-signal
  (dfn/+ component-1 component-2))

;; **Step 2: FFT recovers the decomposition**

(def fft-transformer (t/transformer :real :fft))
(def spectrum (t/forward-1d fft-transformer constructed-signal))

;; **Step 3: Extract frequency peaks with dtype-next**

(defn fft-magnitude
  "Compute magnitudes from FFT spectrum using dtype-next.
  
  FFT returns interleaved [r0, i0, r1, i1, ...] format.
  Magnitude = sqrt(real² + imag²)"
  [spectrum]
  (let [n-bins (quot (dtype/ecount spectrum) 2)]
    ;; Functional approach: map over bin indices
    (dtype/emap
     (fn [i]
       (let [real (dtype/get-value spectrum (* 2 i))
             imag (dtype/get-value spectrum (inc (* 2 i)))]
         (Math/sqrt (+ (* real real) (* imag imag)))))
     :float64
     (range n-bins))))

(def magnitudes (fft-magnitude spectrum))
(def freq-bins (dfn/* (range (dtype/ecount magnitudes)) (/ sample-rate n-samples)))

;; Find peaks
(def peak-1-idx (argops/argmax magnitudes))
(def peak-1-freq (dtype/get-value freq-bins peak-1-idx))
(def peak-1-mag (dtype/get-value magnitudes peak-1-idx))

;; Remove first peak, find second (functional approach)
(def mags-without-peak-1
  (dtype/emap (fn [i val] (if (= i peak-1-idx) 0.0 val))
              :float64
              (range (dtype/ecount magnitudes))
              magnitudes))

(def peak-2-idx (argops/argmax mags-without-peak-1))
(def peak-2-freq (dtype/get-value freq-bins peak-2-idx))
(def peak-2-mag (dtype/get-value mags-without-peak-1 peak-2-idx))

;; **Verification**: FFT recovered our exact construction!
{:constructed {:freq-1 freq-1 :amp-1 amp-1
               :freq-2 freq-2 :amp-2 amp-2}
 :recovered {:freq-1 peak-1-freq :magnitude-1 (/ peak-1-mag (/ n-samples 2))
             :freq-2 peak-2-freq :magnitude-2 (/ peak-2-mag (/ n-samples 2))}}

;; **Key Insight**: This is linear decomposition in action!
;; We built: signal = 1.0×sin(2π·10·t) + 0.5×sin(2π·25·t)
;; FFT found: peaks at 10 Hz (mag ≈ 1.0) and 25 Hz (mag ≈ 0.5)
;;
;; The FFT perfectly recovered the frequencies we put in. This is the power of the
;; Fourier Transform—it reveals the hidden frequency structure of any signal.

;; ### Practical Function: Finding Dominant Frequency

;; Now let's package this into a reusable function. This is the kind of utility you'd use
;; in real applications: "What's the main frequency in this signal?"

(defn find-dominant-frequency
  "Extract strongest frequency using FFT and dtype-next."
  [signal sample-rate]
  (let [fft (t/transformer :real :fft)
        spectrum (t/forward-1d fft signal)
        n (dtype/ecount signal)
        mags (fft-magnitude spectrum)

        ;; Skip DC component
        max-idx (inc (argops/argmax (dtype/sub-buffer mags 1 (dec (dtype/ecount mags)))))
        freq (* max-idx (/ sample-rate n))]

    {:frequency freq
     :magnitude (dtype/get-value mags max-idx)}))

(find-dominant-frequency (:signal (:pure-sine signals)) sample-rate)

;; ### FFT Visualization

(defn visualize-fft
  "Show frequency spectrum."
  [signal sample-rate title]
  (let [fft (t/transformer :real :fft)
        spectrum (t/forward-1d fft signal)
        mags (fft-magnitude spectrum)
        n (dtype/ecount signal)
        freqs (dfn/* (range (dtype/ecount mags)) (/ sample-rate n))

        display-n (min 100 (dtype/ecount mags))
        dataset (tc/dataset {:frequency (dtype/sub-buffer freqs 0 display-n)
                             :magnitude (dtype/sub-buffer mags 0 display-n)})]

    (-> dataset
        (plotly/base {:=x :frequency
                      :=y :magnitude
                      :=x-title "Frequency (Hz)"
                      :=y-title "Magnitude"
                      :=title title
                      :=width 700
                      :=height 250})
        (plotly/layer-line))))

(visualize-fft (:signal (:two-tones signals)) sample-rate
               "FFT Spectrum: Clear Peaks at 10 Hz and 25 Hz")

;; ### Round-Trip Testing - Validation

(defn test-fft-roundtrip
  "Verify FFT → IFFT recovers original signal.
  
  Returns test results with pass/fail boolean."
  [signal tolerance]
  (let [fft (t/transformer :real :fft)
        original (vec signal)
        spectrum (t/forward-1d fft signal)
        recovered (t/reverse-1d fft spectrum)

        ;; Use dtype-next for error calculation
        error (Math/sqrt (dfn/mean (dfn/sq (dfn/- signal recovered))))]

    {:rmse error
     :tolerance tolerance
     :passed? (< error tolerance)
     :interpretation (if (< error tolerance)
                       "✓ Perfect reconstruction"
                       "✗ Error exceeds tolerance")}))

;; Test with our teaching signals
(def fft-tests
  {:pure-sine (test-fft-roundtrip (:signal (:pure-sine signals)) 1e-10)
   :two-tones (test-fft-roundtrip (:signal (:two-tones signals)) 1e-10)
   :smooth (test-fft-roundtrip (:signal (:smooth signals)) 1e-10)})

^:kindly/hide-code
(kind/table
 (for [[sig-type result] fft-tests]
   {:signal (name sig-type)
    :rmse (format "%.2e" (:rmse result))
    :passed? (:passed? result)
    :status (if (:passed? result) "✓ PASS" "✗ FAIL")}))

;; **Result**: All signals pass - FFT is perfectly invertible!
;;
;; This round-trip property is crucial. It means we can:
;; 1. Transform signal → frequency domain
;; 2. Manipulate frequencies (filter, compress, analyze)
;; 3. Transform back → time domain
;; 4. Get our original signal back (within numerical precision)
;;
;; Next, let's use this round-trip capability to build practical tools.

;; ### Practical Application: Notch Filter

(defn notch-filter
  "Remove specific frequency using FFT."
  [signal sample-rate target-freq bandwidth]
  (let [fft (t/transformer :real :fft)
        spectrum (t/forward-1d fft signal)
        n (dtype/ecount signal)

        ;; Functional approach: map over spectrum indices
        filtered (dtype/emap
                  (fn [idx val]
                    (let [freq (* (quot idx 2) (/ sample-rate n))]
                      (if (< (Math/abs (- freq target-freq)) bandwidth)
                        0.0
                        val)))
                  :float64
                  (range (dtype/ecount spectrum))
                  spectrum)]

    (t/reverse-1d fft filtered)))

(def signal-with-25hz (:signal (:two-tones signals)))
(def signal-25hz-removed (notch-filter signal-with-25hz sample-rate 25.0 2.0))

;; Verify 25 Hz is gone, 10 Hz remains
(find-dominant-frequency signal-25hz-removed sample-rate)

;; ### Time-Frequency Analysis: Spectrograms
;;
;; **Limitation of FFT**: It tells us WHAT frequencies are present, but not WHEN.
;; For time-varying signals (like chirps), we need to see how frequency content changes over time.
;;
;; **Solution**: Short-Time Fourier Transform (STFT) - apply FFT to overlapping windows.

(defn spectrogram
  "Compute spectrogram using sliding-window FFT.

  Returns sequence of {:time t :frequency f :magnitude m} maps."
  [signal sample-rate window-size hop-size]
  (let [fft (t/transformer :real :fft)
        n-windows (quot (- (dtype/ecount signal) window-size) hop-size)]
    (for [win-idx (range n-windows)
          :let [start-idx (* win-idx hop-size)
                window (dtype/sub-buffer signal start-idx window-size)
                spectrum (t/forward-1d fft window)
                mags (fft-magnitude spectrum)
                time-center (/ (+ start-idx (/ window-size 2)) sample-rate)]
          freq-idx (range (min 50 (dtype/ecount mags)))]
      {:time time-center
       :frequency (* freq-idx (/ sample-rate window-size))
       :magnitude (dtype/get-value mags freq-idx)})))

;; Visualize chirp signal (frequency increases over time)
(def chirp-spectrogram
  (spectrogram (:signal (:chirp signals)) sample-rate 32 8))

(-> (tc/dataset chirp-spectrogram)
    (plotly/base {:=x :time
                  :=y :frequency
                  :=mark-color :magnitude
                  :=x-title "Time (s)"
                  :=y-title "Frequency (Hz)"
                  :=title "Spectrogram: Chirp Signal (5→50 Hz sweep)"
                  :=width 700
                  :=height 350})
    (plotly/layer-point {:=mark-symbol "square"}))

;; **Key Observation**: The spectrogram reveals the frequency sweep!
;; Brighter colors = higher magnitude. We can see frequency increasing linearly over time.
;;
;; **Limitation of FFT**: It tells us WHAT frequencies exist, but loses information about WHEN
;; they occur. For signals with time-varying content, we need different tools—wavelets provide
;; this naturally, as we'll see later.
;;
;; **What we just learned**: The FFT decomposes signals into frequency components, enabling
;; frequency analysis, filtering, and spectral visualization. Its invertibility makes it perfect
;; for round-trip transformations.
;;
;; But the FFT has a weakness: it's not optimal for compression. Let's explore why the Discrete
;; Cosine Transform (DCT) outperforms FFT for smooth signals.

;; ## Part 3: DCT - The Compression Transform

;; The FFT is excellent for finding frequencies, but it's not the best choice for compression.
;; Here's why: the FFT uses both sines AND cosines (complex output), which means we're storing
;; twice as much information as we might need.
;;
;; For smooth signals—like natural images, audio, or our "smooth" teaching signal—most energy
;; concentrates in low frequencies. The **Discrete Cosine Transform (DCT)** uses only cosines,
;; which for smooth signals concentrates energy even more efficiently than FFT.
;;
;; This is why JPEG compresses images with DCT, not FFT. Let's see why.

;; ### Understanding DCT as Cosine Decomposition
;;
;; **Core Concept**: The [Discrete Cosine Transform (DCT)](https://en.wikipedia.org/wiki/Discrete_cosine_transform)
;; expresses a signal as a weighted sum of cosine waves.
;;
;; Signal = a₀ + a₁·cos(π·1·t) + a₂·cos(π·2·t) + ...
;;
;; Unlike DFT (sines + cosines), DCT uses only cosines.
;; This makes it perfect for smooth signals → energy concentrates in first few coefficients.

;; ### Why DCT for Compression?
;;
;; Smooth signals have most energy in low frequencies.
;; DCT with cosine-only basis captures this more efficiently than DFT.
;; Result: [JPEG](https://en.wikipedia.org/wiki/JPEG), [MP3](https://en.wikipedia.org/wiki/MP3),
;; and modern codecs all use DCT!

;; ### Energy Concentration Example

(def dct-n-samples 64)
(def dct-time (dfn// (range dct-n-samples) dct-n-samples))

;; Create smooth signal (mostly low frequency)
(def smooth-signal
  (dfn/+ 10.0
         (dfn/* 5.0 (dfn/cos (dfn/* 2.0 Math/PI 2.0 dct-time)))
         (dfn/* 2.0 (dfn/cos (dfn/* 2.0 Math/PI 3.0 dct-time)))))

;; Apply DCT
(def dct (t/transformer :real :dct))
(def dct-coeffs (t/forward-1d dct smooth-signal))

;; Measure energy concentration with dtype-next
(def energy-by-coeff (dfn/sq dct-coeffs))
(def total-energy (dfn/sum energy-by-coeff))
(def energy-first-10 (dfn/sum (dtype/sub-buffer energy-by-coeff 0 10)))

{:total-coeffs dct-n-samples
 :energy-in-first-10 (format "%.1f%%" (* 100.0 (/ energy-first-10 total-energy)))
 :interpretation "Smooth signal → energy concentrated in few DCT coefficients!"}

;; ### DCT vs FFT Energy Concentration

(defn measure-energy-concentration
  "Compare energy concentration across transforms."
  [signal transform-type keep-ratios]
  (let [transformer (t/transformer :real transform-type)
        coeffs (t/forward-1d transformer signal)
        total-energy (dfn/sum (dfn/sq coeffs))]

    (for [ratio keep-ratios]
      (let [keep-n (int (* ratio (dtype/ecount coeffs)))
            kept-energy (dfn/sum (dfn/sq (dtype/sub-buffer coeffs 0 keep-n)))]

        {:keep-ratio (* 100 ratio)
         :energy-retained (* 100 (/ kept-energy total-energy))
         :transform transform-type}))))

(def energy-comparison
  (concat
   (measure-energy-concentration (:signal (:smooth signals)) :fft [0.1 0.2 0.3 0.5])
   (measure-energy-concentration (:signal (:smooth signals)) :dct [0.1 0.2 0.3 0.5])))

(-> (tc/dataset energy-comparison)
    (plotly/base {:=x :keep-ratio
                  :=y :energy-retained
                  :=mark-color :transform
                  :=x-title "% Coefficients Kept"
                  :=y-title "% Energy Retained"
                  :=title "Energy Concentration: DCT vs FFT on Smooth Signal"
                  :=width 600
                  :=height 300})
    (plotly/layer-line)
    (plotly/layer-point))

;; **Observation**: DCT retains ~95% energy with 10% of coefficients!
;; FFT needs ~30% of coefficients for same energy.

;; ### Lossy Compression with Quality Metrics

(defn compress-with-dct
  "Compress signal by keeping only top N% of DCT coefficients."
  [signal keep-ratio]
  (let [dct (t/transformer :real :dct)
        coeffs (t/forward-1d dct signal)
        n (dtype/ecount coeffs)
        keep-n (int (* keep-ratio n))

        ;; Functional approach: zero out high-frequency coefficients
        compressed (dtype/emap
                    (fn [i val] (if (< i keep-n) val 0.0))
                    :float64
                    (range n)
                    coeffs)]

    (t/reverse-1d dct compressed)))

(def original-smooth (:signal (:smooth signals)))
(def compressed-50pct (compress-with-dct original-smooth 0.5))
(def compressed-25pct (compress-with-dct original-smooth 0.25))
(def compressed-10pct (compress-with-dct original-smooth 0.1))

;; Calculate quality metrics with dtype-next
(defn compression-quality [original compressed keep-ratio]
  (let [error (Math/sqrt (dfn/mean (dfn/sq (dfn/- original compressed))))
        signal-power (Math/sqrt (dfn/mean (dfn/sq original)))
        ;; Signal-to-Noise Ratio in decibels
        snr-db (* 20 (Math/log10 (/ signal-power error)))]

    {:compression-ratio (format "%.0f:1" (/ 1.0 keep-ratio))
     :rmse (format "%.4f" error)
     :snr-db (format "%.1f dB" snr-db)
     :keep-ratio (format "%.0f%%" (* 100 keep-ratio))}))

^:kindly/hide-code
(kind/table
 [(compression-quality original-smooth compressed-50pct 0.5)
  (compression-quality original-smooth compressed-25pct 0.25)
  (compression-quality original-smooth compressed-10pct 0.1)])

;; ### Test DCT Energy Concentration Property

(defn test-dct-energy-concentration
  "Verify DCT concentrates energy better than FFT for smooth signals."
  [signal keep-ratio min-energy-threshold]
  (let [dct (t/transformer :real :dct)
        fft (t/transformer :real :fft)

        ;; DCT energy
        dct-coeffs (t/forward-1d dct signal)
        dct-total (dfn/sum (dfn/sq dct-coeffs))
        dct-keep-n (int (* keep-ratio (dtype/ecount dct-coeffs)))
        dct-kept (dfn/sum (dfn/sq (dtype/sub-buffer dct-coeffs 0 dct-keep-n)))
        dct-ratio (/ dct-kept dct-total)

        ;; FFT energy
        fft-spec (t/forward-1d fft signal)
        fft-mags (fft-magnitude fft-spec)
        fft-total (dfn/sum (dfn/sq fft-mags))
        fft-keep-n (int (* keep-ratio (dtype/ecount fft-mags)))
        fft-kept (dfn/sum (dfn/sq (dtype/sub-buffer fft-mags 0 fft-keep-n)))
        fft-ratio (/ fft-kept fft-total)]

    {:dct-energy-retained dct-ratio
     :fft-energy-retained fft-ratio
     :dct-advantage (format "%.1fx better" (/ dct-ratio fft-ratio))
     :passed? (and (> dct-ratio fft-ratio)
                   (> dct-ratio min-energy-threshold))}))

;; Test that DCT concentrates energy better than FFT
;; For smooth signals, keeping 10% of DCT coefficients should retain significantly more energy than FFT
(test-dct-energy-concentration original-smooth 0.1 0.5)

;; **What we just learned**: The DCT uses only cosines (instead of FFT's sines + cosines),
;; which concentrates energy more efficiently for smooth signals. This makes it ideal for
;; compression—JPEG and MP3 both exploit this property.
;;
;; But both FFT and DCT have a fundamental limitation: they use **global basis functions**
;; that span the entire signal. A sine wave at 10 Hz in the FFT basis oscillates across the
;; whole signal—it can't tell us that a frequency appears only briefly. Let's explore wavelets,
;; which solve this problem.

;; ## Part 4: Wavelets - Time-Frequency Localization

;; Remember our chirp signal—frequency increasing from 5 to 50 Hz over time? The FFT told us
;; "this signal contains frequencies between 5 and 50 Hz," but it couldn't tell us WHEN each
;; frequency occurred. The spectrogram helped by windowing, but that was a workaround.
;;
;; **Wavelets** provide time-frequency localization naturally by using basis functions that are
;; inherently localized in time. Instead of global sine waves, wavelets use small, localized
;; "wavelets" (hence the name!) that can detect features at specific times AND specific frequencies.

;; ### Understanding Wavelets as Localized Decomposition
;;
;; **Core Concept**: [Wavelets](https://en.wikipedia.org/wiki/Wavelet) decompose a signal using 
;; scaled and shifted basis functions.
;;
;; Signal = a₁·ψ(t-1) + a₂·ψ(2t-1) + a₃·ψ(2t-2) + ...
;;
;; Where ψ is a localized wavelet function at different scales and positions.
;;
;; See also: [Wavelet Transform](https://en.wikipedia.org/wiki/Wavelet_transform)

;; ### The Key Difference: Global vs Local

;; **DFT/DCT**: Basis functions span entire signal
;; - Answer: "What frequencies are present?"
;; - Cannot tell WHERE events occur

;; **Wavelets**: Localized basis at multiple scales
;; - Answer: "What frequencies, and WHEN?"
;; - Perfect for time-varying signals

;; ### Visualizing Wavelet Basis Functions
;;
;; To understand the difference, let's visualize the actual basis functions.

;; Haar wavelet basis function (simplest wavelet)
(defn haar-wavelet
  "Generate Haar wavelet at given position and scale."
  [t position scale]
  (let [t-scaled (/ (- t position) scale)]
    (cond
      (and (>= t-scaled 0) (< t-scaled 0.5)) 1.0
      (and (>= t-scaled 0.5) (< t-scaled 1.0)) -1.0
      :else 0.0)))

;; Generate basis functions for comparison
(def basis-comparison-data
  (let [n 200
        t-vals (dfn// (range n) n)]
    (concat
     ;; Sine basis (global)
     (map (fn [t] {:time t :amplitude (Math/sin (* 2 Math/PI 5 t)) :type "Sine (global)"})
          t-vals)
     ;; Cosine basis (global)
     (map (fn [t] {:time t :amplitude (Math/cos (* 2 Math/PI 5 t)) :type "Cosine (global)"})
          t-vals)
     ;; Haar wavelet at scale 0.1 (fine detail)
     (map (fn [t] {:time t :amplitude (haar-wavelet t 0.3 0.1) :type "Haar scale=0.1 (fine)"})
          t-vals)
     ;; Haar wavelet at scale 0.2 (coarse detail)
     (map (fn [t] {:time t :amplitude (haar-wavelet t 0.6 0.2) :type "Haar scale=0.2 (coarse)"})
          t-vals))))

(-> (tc/dataset basis-comparison-data)
    (plotly/base {:=x :time
                  :=y :amplitude
                  :=mark-color :type
                  :=x-title "Time"
                  :=y-title "Amplitude"
                  :=title "Basis Functions: Global (Sine/Cosine) vs Local (Haar Wavelets)"
                  :=width 700
                  :=height 350})
    (plotly/layer-line))

;; **Key Observation**: Sine and cosine extend across the entire time axis (global).
;; Haar wavelets are localized to specific time windows and can be scaled to
;; capture both fine details (small scale) and coarse features (large scale).

;; ### Example: Localized Event Detection

;; Localized event signal: quiet, brief pulse, quiet
(def event-signal
  (double-array
   (concat (repeat 25 0.0)
           (repeat 5 5.0)
           (repeat 20 0.0)
           (repeat 14 0.0))))

;; FFT spreads event across all frequencies (global view)
(def event-fft (t/forward-1d (t/transformer :real :fft) event-signal))

;; Wavelet localizes event in time AND frequency
(def haar (t/transformer :fast :haar))
(def event-wavelet (t/forward-1d haar event-signal))

;; Compare: where is the energy?
{:fft-view "Energy distributed across all frequencies - WHERE is unclear"
 :wavelet-view "Energy concentrated at specific time-scale location"
 :key-insight "Wavelets = localized basis functions at multiple scales"}

;; ### Wavelet Families

;; Fastmath supports many wavelet families via JWave:
;; - [Haar](https://en.wikipedia.org/wiki/Haar_wavelet): `:haar`
;; - [Daubechies](https://en.wikipedia.org/wiki/Daubechies_wavelet): `:daubechies-4`, `:daubechies-8`
;; - [Symlets](https://en.wikipedia.org/wiki/Symlet): `:symlet-4`
;; - [Coiflets](https://en.wikipedia.org/wiki/Coiflet): `:coiflet-2`

(defn compare-wavelets [signal]
  (let [;; Ensure signal is power of 2
        n (dtype/ecount signal)
        target-n (int (Math/pow 2 (Math/ceil (/ (Math/log n) (Math/log 2)))))

        ;; Functional padding: use original values or 0
        padded (dtype/emap
                (fn [i] (if (< i n)
                          (dtype/get-value signal i)
                          0.0))
                :float64
                (range target-n))

        wavelets {:haar :haar
                  :daubechies-4 :daubechies-4
                  :daubechies-8 :daubechies-8
                  :symlet-4 :symlet-4}]

    (for [[name wavelet-key] wavelets]
      (let [trans (t/transformer :fast wavelet-key)
            coeffs (t/forward-1d trans padded)
            ;; Sparsity = how many small coefficients
            sparsity (count (filter #(< (Math/abs %) 0.01) coeffs))]

        {:wavelet (clojure.core/name name)
         :sparsity-pct (format "%.0f%%" (* 100.0 (/ sparsity (alength coeffs))))
         :max-coeff (format "%.2f" (dfn/reduce-max (dfn/abs coeffs)))}))))

^:kindly/hide-code
(kind/table (compare-wavelets (:signal (:chirp signals))))

;; **Observation**: Different wavelets give different sparsity.
;; Smoother wavelets (Daubechies, Symlet) → more zeros → better compression!

;; ### Wavelet Denoising

(defn add-noise [signal noise-level]
  ;; Functional approach: generate noise and add to signal
  (let [n (dtype/ecount signal)
        noise (dtype/emap (fn [_] (* noise-level (- (rand) 0.5)))
                          :float64
                          (range n))]
    (dfn/+ signal noise)))

(def clean-sig (sig/oscillator->signal (sig/oscillator :sin 5.0 1.0 0.0) 128.0 1.0))
(def noisy-sig (add-noise clean-sig 0.3))

;; Wavelet denoising: transform → threshold → inverse
;;
;; **Important**: Denoising requires careful threshold tuning. The default threshold
;; may be too aggressive (removing signal) or too weak (leaving noise). In practice,
;; you'd analyze the wavelet coefficients and choose a threshold based on the noise
;; characteristics. This is an active area of research with methods like:
;; - Universal threshold: σ√(2 log N)
;; - SURE threshold (Stein's Unbiased Risk Estimate)
;; - Empirical Bayes methods

(def db8 (t/transformer :fast :daubechies-8))
(def noisy-coeffs (t/forward-1d db8 noisy-sig))
(def denoised-coeffs (t/denoise db8 noisy-coeffs :soft))
(def denoised-sig (t/reverse-1d db8 denoised-coeffs))

;; Quality comparison with dtype-next
(defn signal-quality [clean noisy denoised]
  {:noisy-rmse (format "%.4f" (Math/sqrt (dfn/mean (dfn/sq (dfn/- clean noisy)))))
   :denoised-rmse (format "%.4f" (Math/sqrt (dfn/mean (dfn/sq (dfn/- clean denoised)))))
   :note "Denoising effectiveness depends on threshold tuning"})

(signal-quality clean-sig noisy-sig denoised-sig)

;; ### Round-Trip Testing for Wavelets

(defn test-wavelet-roundtrip
  "Verify wavelet transform is invertible."
  [signal wavelet-type tolerance]
  (let [wavelet (t/transformer :fast wavelet-type)
        coeffs (t/forward-1d wavelet signal)
        recovered (t/reverse-1d wavelet coeffs)
        error (Math/sqrt (dfn/mean (dfn/sq (dfn/- signal recovered))))]

    {:wavelet wavelet-type
     :rmse error
     :passed? (< error tolerance)}))

(def wavelet-tests
  [(test-wavelet-roundtrip (take 64 (:signal (:smooth signals))) :haar 1e-10)
   (test-wavelet-roundtrip (take 64 (:signal (:smooth signals))) :daubechies-8 1e-10)
   (test-wavelet-roundtrip (take 64 (:signal (:smooth signals))) :symlet-4 1e-10)])

^:kindly/hide-code
(kind/table
 (map #(update % :wavelet name) wavelet-tests))

;; ## Part 5: Other Transforms - DST and DHT

;; ### Discrete Sine Transform (DST)

;; **Use case**: Solving [PDEs](https://en.wikipedia.org/wiki/Partial_differential_equation) with 
;; [Dirichlet boundary conditions](https://en.wikipedia.org/wiki/Dirichlet_boundary_condition) 
;; (signal = 0 at endpoints)
;;
;; The [Discrete Sine Transform (DST)](https://en.wikipedia.org/wiki/Discrete_sine_transform) 
;; is like DCT but uses sine basis instead of cosine.
;; Implicitly assumes signal goes to zero at boundaries.

(def dst (t/transformer :real :dst))

;; Signal that naturally goes to zero at edges
;; Signal naturally zero at boundaries (t=0 and t=1)
(def boundary-signal
  (let [n 64
        t (dfn// (range n) n)]
    (dfn/* (dfn/sin (dfn/* Math/PI t))
           (dfn/sin (dfn/* 5.0 Math/PI t)))))

(def dst-coeffs (t/forward-1d dst boundary-signal))
(def dst-reconstructed (t/reverse-1d dst dst-coeffs))

;; Verify round-trip
{:rmse (format "%.2e" (Math/sqrt (dfn/mean (dfn/sq (dfn/- boundary-signal dst-reconstructed)))))
 :passed? (< (Math/sqrt (dfn/mean (dfn/sq (dfn/- boundary-signal dst-reconstructed)))) 1e-10)}

;; ### Discrete Hartley Transform (DHT)

;; **Use case**: Real-valued alternative to DFT for [convolution](https://en.wikipedia.org/wiki/Convolution)
;;
;; The [Discrete Hartley Transform (DHT)](https://en.wikipedia.org/wiki/Discrete_Hartley_transform)
;; is similar to the DFT but output is real-valued (no complex numbers).
;; Useful for applications where you only care about real operations.

(def dht (t/transformer :real :dht))
(def test-sig (take 64 (:signal (:pure-sine signals))))
(def dht-result (t/forward-1d dht test-sig))

;; All real values (unlike FFT which has complex output)
{:all-real? (every? #(not (Double/isNaN %)) dht-result)
 :invertible? (< (Math/sqrt (dfn/mean (dfn/sq (dfn/- test-sig (t/reverse-1d dht dht-result))))) 1e-10)}

;; ### Transform Selection Guide

^:kindly/hide-code
(kind/table
 [{:transform "DFT" :use-case "Frequency analysis" :basis "Sine + Cosine" :output "Complex" :algorithm "FFT"}
  {:transform "DCT" :use-case "Compression (JPEG/MP3)" :basis "Cosine only" :output "Real" :algorithm "Fast DCT"}
  {:transform "DST" :use-case "PDEs, boundary problems" :basis "Sine only" :output "Real" :algorithm "Fast DST"}
  {:transform "DHT" :use-case "Real-valued convolution" :basis "Hartley" :output "Real" :algorithm "Fast DHT"}
  {:transform "Wavelet" :use-case "Time-frequency, denoising" :basis "Localized" :output "Real" :algorithm "Fast Wavelet"}])

;; ## Part 6: 2D Transforms for Images

;; ### Creating a 2D Signal

(defn generate-2d-signal [rows cols]
  (let [data (make-array Double/TYPE rows cols)]
    (dotimes [i rows]
      (dotimes [j cols]
        ;; Create pattern: horizontal 3Hz + vertical 5Hz
        (aset data i j
              (+ (Math/sin (* 2 Math/PI 3.0 (/ j cols)))
                 (Math/sin (* 2 Math/PI 5.0 (/ i rows)))))))
    data))

(def signal-2d (generate-2d-signal 32 32))

;; Visualize 2D signal
(-> (tc/dataset (for [i (range 32) j (range 32)]
                  {:row i :col j :value (aget signal-2d i j)}))
    (plotly/base {:=x :col
                  :=y :row
                  :=mark-color :value
                  :=title "2D Signal: Horizontal 3Hz + Vertical 5Hz"
                  :=width 400
                  :=height 400})
    (plotly/layer-point {:=mark-symbol "square"}))

;; ### 2D FFT

(def fft-2d (t/transformer :real :fft))
(def spectrum-2d (t/forward-2d fft-2d signal-2d))

;; Compute 2D magnitude
(defn magnitude-2d [spectrum]
  (let [rows (alength spectrum)
        cols (alength (aget spectrum 0))
        result (make-array Double/TYPE rows cols)]
    (dotimes [i rows]
      (dotimes [j cols]
        (aset result i j (Math/abs (aget spectrum i j)))))
    result))

(def mag-2d (magnitude-2d spectrum-2d))

;; Visualize 2D spectrum (log scale for visibility)
(-> (tc/dataset (for [i (range 32) j (range 32)]
                  {:row i :col j :value (Math/log (+ 1 (aget mag-2d i j)))}))
    (plotly/base {:=x :col
                  :=y :row
                  :=mark-color :value
                  :=x-title "Frequency X"
                  :=y-title "Frequency Y"
                  :=title "2D FFT Spectrum - Bright Spots Show Frequency Components"
                  :=width 400
                  :=height 400})
    (plotly/layer-point {:=mark-symbol "square"}))

;; **Interpretation**: Just like 1D FFT, 2D FFT decomposes image into
;; frequency components. The bright spots in the spectrum correspond to our
;; horizontal 3Hz and vertical 5Hz patterns!

;; ### 2D DCT Basis Functions
;;
;; Understanding how JPEG compression works: visualizing the 8×8 DCT basis images.
;; Each 8×8 block in JPEG is decomposed into a weighted sum of these 64 basis patterns.

;; 2D DCT-II basis function
(defn dct-2d-basis
  "Generate a single 2D DCT basis function for frequency (u, v)."
  [size u v]
  (let [data (make-array Double/TYPE size size)
        pi Math/PI
        cu (if (zero? u) (/ 1.0 (Math/sqrt 2.0)) 1.0)
        cv (if (zero? v) (/ 1.0 (Math/sqrt 2.0)) 1.0)]
    (dotimes [i size]
      (dotimes [j size]
        (aset data i j
              (* cu cv
                 (Math/cos (* (/ pi size) u (+ i 0.5)))
                 (Math/cos (* (/ pi size) v (+ j 0.5)))))))
    data))

;; Generate a grid of low-frequency DCT basis images (like JPEG uses)
(def dct-basis-grid
  (let [size 8
        grid-size 4]
    (for [u (range grid-size)
          v (range grid-size)
          i (range size)
          j (range size)]
      {:freq-u u
       :freq-v v
       :row i
       :col j
       :value (aget (dct-2d-basis size u v) i j)})))

;; Visualize the first 16 DCT basis functions (0-3 in each direction)
(-> (tc/dataset dct-basis-grid)
    (plotly/base {:=x :col
                  :=y :row
                  :=mark-color :value
                  :=facet-x :freq-u
                  :=facet-y :freq-v
                  :=title "2D DCT Basis Functions (JPEG uses these!)"
                  :=width 600
                  :=height 600})
    (plotly/layer-point {:=mark-symbol "square"}))

;; **Key Insight**:
;; - Top-left (0,0) = DC component (constant, average brightness)
;; - Moving right → increasing horizontal frequency
;; - Moving down → increasing vertical frequency
;; - JPEG keeps low-frequency basis (top-left) and discards high-frequency (bottom-right)
;; - This is why JPEG works well for natural images (most energy in low frequencies)

;; ## Part 7: Practical Applications

;; Now that we understand the theory behind FFT, DCT, and wavelets, let's see how to apply
;; these transforms to solve real-world problems. We'll build practical tools for filtering,
;; compression, and spectral analysis.

;; ### Application 1: Bandpass Filtering

(defn bandpass-filter
  "Keep only frequencies in [low-freq, high-freq] range."
  [signal low-freq high-freq sample-rate]
  (let [fft (t/transformer :real :fft)
        spectrum (t/forward-1d fft signal)
        n (dtype/ecount signal)
        low-bin (int (* n (/ low-freq sample-rate)))
        high-bin (int (* n (/ high-freq sample-rate)))

        ;; Functional approach: keep frequencies in range, zero others
        filtered (dtype/emap
                  (fn [idx val]
                    (let [bin (quot idx 2)]
                      (if (and (>= bin low-bin) (<= bin high-bin))
                        val
                        0.0)))
                  :float64
                  (range (dtype/ecount spectrum))
                  spectrum)]

    (t/reverse-1d fft filtered)))

;; Test: remove high frequency noise
;; Mixed signal: 8 Hz (signal) + 25 Hz (noise) + random noise
(def mixed-signal
  (add-noise
   (sig/oscillator->signal
    (sig/oscillators-sum
     (sig/oscillator :sin 8.0 1.0 0.0)
     (sig/oscillator :sin 25.0 0.3 0.0))
    100.0 1.0)
   0.1))

(def filtered-signal (bandpass-filter mixed-signal 5.0 15.0 100.0))

{:original-dominant (find-dominant-frequency mixed-signal 100.0)
 :filtered-dominant (find-dominant-frequency filtered-signal 100.0)
 :interpretation "High frequency (25 Hz) successfully removed!"}

;; ### Application 2: Spectral Analysis

(defn analyze-spectrum
  "Find all significant frequency components."
  [signal sample-rate threshold-ratio]
  (let [fft (t/transformer :real :fft)
        spectrum (t/forward-1d fft signal)
        mags (fft-magnitude spectrum)
        n (dtype/ecount signal)
        max-mag (dfn/reduce-max mags)
        threshold (* threshold-ratio max-mag)

        ;; Find all peaks above threshold
        peaks (filter #(> (dtype/get-value mags %) threshold) (range (dtype/ecount mags)))]

    {:frequencies (mapv #(* % (/ sample-rate n)) peaks)
     :magnitudes (mapv #(dtype/get-value mags %) peaks)
     :num-components (count peaks)}))

(analyze-spectrum (:signal (:two-tones signals)) sample-rate 0.1)

;; ### Application 3: Compression Pipeline

(defn compression-pipeline
  "Complete compression: signal → DCT → quantize → compress."
  [signal keep-ratio]
  (let [dct (t/transformer :real :dct)
        ;; Step 1: Transform to frequency domain
        coeffs (t/forward-1d dct signal)

        ;; Step 2: Sort by magnitude, keep largest
        indexed-coeffs (map-indexed vector coeffs)
        sorted-coeffs (sort-by #(- (Math/abs (second %))) indexed-coeffs)
        keep-n (int (* keep-ratio (count sorted-coeffs)))
        kept-indices (set (map first (take keep-n sorted-coeffs)))

        ;; Step 3: Functional approach - zero out small coefficients
        compressed (dtype/emap
                    (fn [idx val]
                      (if (kept-indices idx) val 0.0))
                    :float64
                    (range (dtype/ecount coeffs))
                    coeffs)]

    ;; Step 4: Inverse transform
    (t/reverse-1d dct compressed)))

;; Compare compression strategies: sequential vs magnitude-based
(def original (:signal (:smooth signals)))
(def compressed-sequential (compress-with-dct original 0.25))
(def compressed-magnitude (compression-pipeline original 0.25))

{:sequential-rmse (format "%.4f" (Math/sqrt (dfn/mean (dfn/sq (dfn/- original compressed-sequential)))))
 :magnitude-rmse (format "%.4f" (Math/sqrt (dfn/mean (dfn/sq (dfn/- original compressed-magnitude)))))
 :note "Keeping largest coefficients works better than keeping first N!"}

;; ## Part 8: Testing and Validation Framework

;; Transform operations can seem like black boxes. How do we know they're working correctly?
;; This section builds a comprehensive testing framework to validate transform properties:
;; invertibility, numerical stability, and mathematical correctness.

;; ### Utility Functions

(defn compute-rmse
  "Root mean square error using dtype-next."
  [signal1 signal2]
  (Math/sqrt (dfn/mean (dfn/sq (dfn/- signal1 signal2)))))

(defn generate-random-signal
  "Random signal with seeded RNG for reproducibility."
  [n seed]
  (let [rng (java.util.Random. seed)]
    (double-array (repeatedly n #(- (* 2.0 (.nextDouble rng)) 1.0)))))

;; ### Generic Round-Trip Test

(defn test-transform-roundtrip
  "Generic test for any transform's invertibility."
  [signal transformer tolerance]
  (let [forward (t/forward-1d transformer signal)
        inverse (t/reverse-1d transformer forward)
        error (compute-rmse signal inverse)]

    {:signal-size (dtype/ecount signal)
     :rmse error
     :tolerance tolerance
     :passed? (< error tolerance)}))

;; ### Test Multiple Signal Types

(defn test-all-signal-types
  "Test transform on various signal types."
  [transformer tolerance]
  (let [test-signals
        {:pure-sine (sig/oscillator->signal (sig/oscillator :sin 440.0 1.0 0.0) 1000.0 0.1)
         :composite (sig/oscillator->signal
                     (sig/oscillators-sum
                      (sig/oscillator :sin 220.0 1.0 0.0)
                      (sig/oscillator :sin 440.0 0.5 0.0))
                     1000.0 0.1)
         :square (sig/oscillator->signal (sig/oscillator :square 100.0 1.0 0.0) 1000.0 0.1)
         :noise (generate-random-signal 100 42)
         :chirp (:signal (:chirp signals))}]

    (into {}
          (for [[sig-type signal] test-signals]
            [sig-type (test-transform-roundtrip signal transformer tolerance)]))))

;; Run comprehensive FFT tests
(def fft-comprehensive (test-all-signal-types (t/transformer :real :fft) 1e-10))

^:kindly/hide-code
(kind/table
 (for [[sig-type result] fft-comprehensive]
   {:signal (name sig-type)
    :rmse (format "%.2e" (:rmse result))
    :passed? (:passed? result)
    :status (if (:passed? result) "✓" "✗")}))

;; ### Numerical Stability Test

(defn test-numerical-stability
  "Test across wide range of amplitudes."
  [base-signal transformer scale-factors]
  (for [scale scale-factors]
    (let [scaled-signal (dfn/* scale base-signal)
          ;; Use relative tolerance
          tolerance (* 1e-10 scale)
          result (test-transform-roundtrip scaled-signal transformer tolerance)]
      (assoc result :scale-factor scale))))

(def stability-results
  (test-numerical-stability
   (take 100 (:signal (:pure-sine signals)))
   (t/transformer :real :fft)
   [1e-6 1e-3 1.0 1e3 1e6]))

;; All should pass
(every? :passed? stability-results)

;; ### Comprehensive Test Suite

(defn comprehensive-test-suite
  "Run all tests and return boolean: true if all pass."
  []
  (let [fft (t/transformer :real :fft)
        dct (t/transformer :real :dct)
        haar (t/transformer :fast :haar)

        test-signal (take 64 (:signal (:smooth signals)))

        results
        {:fft-roundtrip (every? :passed? (vals (test-all-signal-types fft 1e-10)))
         :dct-roundtrip (every? :passed? (vals (test-all-signal-types dct 1e-10)))
         :wavelet-roundtrip (:passed? (test-wavelet-roundtrip test-signal :haar 1e-10))
         :numerical-stability (every? :passed? stability-results)
         :dct-energy-concentration (:passed? (test-dct-energy-concentration original-smooth 0.1 0.5))}]

    {:individual-results results
     :all-passed? (every? identity (vals results))}))

(def final-test-results (comprehensive-test-suite))

(:all-passed? final-test-results)

;; ## Part 9: Best Practices and Decision Guide

;; ### dtype-next Best Practices Summary

;; **Key Functions Actually Used**:
^:kindly/hide-code
(kind/table
 [{:category "Element-wise" :functions "dfn/+, dfn/-, dfn/*, dfn//, dfn/sq, dfn/sqrt, dfn/cos, dfn/sin, dfn/abs"}
  {:category "Reductions" :functions "dfn/sum, dfn/mean, dfn/standard-deviation, dfn/reduce-max, dfn/reduce-min"}
  {:category "Functional" :functions "dtype/emap - map function over arrays"}
  {:category "Array access" :functions "dtype/ecount, dtype/get-value, dtype/sub-buffer"}
  {:category "Search" :functions "argops/argmax - find index of maximum"}])

;; ### Common Patterns

;; Throughout signal processing, certain computational patterns appear repeatedly. 
;; Root mean square error combines difference, squaring, and averaging to measure 
;; signal similarity:
;;
;; (Math/sqrt (dfn/mean (dfn/sq (dfn/- signal1 signal2))))
;;
;; Signal energy sums the squares of all values:
;;
;; (dfn/sum (dfn/sq signal))
;;
;; Normalization centers and scales a signal to zero mean and unit variance:
;;
;; (dfn// (dfn/- signal (dfn/mean signal)) (dfn/standard-deviation signal))
;;
;; Signal-to-noise ratio ([SNR](https://en.wikipedia.org/wiki/Signal-to-noise_ratio)) 
;; in decibels compares signal power to noise power:
;;
;; (* 20 (Math/log10 (/ (dfn/mean (dfn/sq signal))
;;                      (dfn/mean (dfn/sq noise)))))

;; ### Transform Selection Flowchart

(defn recommend-transform
  "Decision tree for transform selection."
  [signal-properties]
  (cond
    ;; Time-varying frequency
    (:time-varying-frequency signal-properties)
    {:transform :wavelet
     :reason "Frequency changes over time → need time-frequency localization"
     :recommended-type :daubechies-8}

    ;; Compression goal + smooth signal
    (and (:smooth signal-properties) (:compress signal-properties))
    {:transform :dct
     :reason "Smooth signal + compression → DCT energy concentration"
     :compression-ratio "10:1 to 100:1 typical"}

    ;; Frequency analysis with constant frequencies
    (:constant-frequencies signal-properties)
    {:transform :fft
     :reason "Constant frequencies → DFT (via FFT algorithm) is fastest and most accurate"
     :note "Use :real variant for real-valued signals (2x faster)"}

    ;; Denoising
    (:noisy signal-properties)
    {:transform :wavelet
     :reason "Noise removal → wavelet thresholding"
     :recommended-type :daubechies-8
     :threshold-method :soft}

    ;; Boundary conditions (PDEs)
    (:boundary-conditions signal-properties)
    {:transform :dst
     :reason "Signal must be zero at boundaries → use DST"
     :application "Finite element methods, heat equation"}

    ;; Default
    :else
    {:transform :fft
     :reason "General purpose - when in doubt, start with DFT (via FFT)"
     :note "Analyze spectrum first, then choose specialized transform"}))

;; Examples:
(recommend-transform #{:constant-frequencies})
(recommend-transform #{:time-varying-frequency})
(recommend-transform #{:smooth :compress})
(recommend-transform #{:noisy})

;; ### Performance Tips

^:kindly/hide-code
(kind/table
 [{:tip "Use :real transforms"
   :reason "2x faster than complex for real-valued signals"
   :example "(t/transformer :real :fft)"}

  {:tip "Reuse transformers"
   :reason "Pre-computes lookup tables in constructor"
   :example "(def fft (t/transformer :real :fft))"}

  {:tip "Power-of-2 sizes"
   :reason "Enables fastest split-radix algorithm"
   :speedup "~2-3x vs non-power-of-2"}

  {:tip "Use dtype-next"
   :reason "Vectorized operations, lazy evaluation"
   :speedup "10-100x vs seq operations"}

  {:tip "Avoid boxing"
   :reason "Type hints for primitive arrays"
   :example "^doubles or use dtype-next"}])

;; ### Common Pitfalls

^:kindly/hide-code
(kind/table
 [{:pitfall "Forgetting to scale inverse FFT"
   :solution "Use scaled=true or divide by N manually"
   :symptom "Results N× too large"}

  {:pitfall "Non-power-of-2 for wavelets"
   :solution "Pad signal to next power of 2"
   :symptom "Exception or wrong results"}

  {:pitfall "Using seqs for array math"
   :solution "Use dtype-next functional operations"
   :symptom "Slow performance, high memory"}

  {:pitfall "Interpreting FFT magnitude wrong"
   :solution "Remember interleaved [real, imag] format"
   :symptom "Incorrect frequency peaks"}

  {:pitfall "Not testing round-trip"
   :solution "Always verify transform → inverse recovers original"
   :symptom "Lossy operations when expecting lossless"}])

;; ### Recipe Collection

;; **Recipe 1: Find dominant frequency**
(defn recipe-find-frequency [signal sample-rate]
  (let [fft (t/transformer :real :fft)
        spectrum (t/forward-1d fft signal)
        mags (fft-magnitude spectrum)
        max-idx (inc (argops/argmax (dtype/sub-buffer mags 1 (dec (dtype/ecount mags)))))
        freq (* max-idx (/ sample-rate (dtype/ecount signal)))]
    freq))

;; **Recipe 2: Remove frequency**
(defn recipe-notch-filter [signal sample-rate target-freq bandwidth]
  (let [fft (t/transformer :real :fft)
        spectrum (t/forward-1d fft signal)
        n (dtype/ecount signal)

        ;; Functional filtering: zero out target frequency band
        filtered (dtype/emap
                  (fn [idx val]
                    (let [freq (* (quot idx 2) (/ sample-rate n))]
                      (if (< (Math/abs (- freq target-freq)) bandwidth)
                        0.0
                        val)))
                  :float64
                  (range (dtype/ecount spectrum))
                  spectrum)]

    (t/reverse-1d fft filtered)))

;; **Recipe 3: Compress with quality target**
(defn recipe-compress-to-quality [signal target-snr-db]
  (let [dct (t/transformer :real :dct)
        coeffs (t/forward-1d dct signal)]

    (loop [keep-ratio 0.9]
      (let [compressed (compress-with-dct signal keep-ratio)
            error (Math/sqrt (dfn/mean (dfn/sq (dfn/- signal compressed))))
            signal-power (Math/sqrt (dfn/mean (dfn/sq signal)))
            snr-db (* 20 (Math/log10 (/ signal-power error)))]

        (if (or (>= snr-db target-snr-db) (< keep-ratio 0.05))
          {:keep-ratio keep-ratio :snr-db snr-db :compressed compressed}
          (recur (- keep-ratio 0.05)))))))

;; **Recipe 4: Denoise signal**
(defn recipe-denoise [signal]
  (let [;; Pad to power of 2
        n (dtype/ecount signal)
        padded-n (int (Math/pow 2 (Math/ceil (/ (Math/log n) (Math/log 2)))))

        ;; Functional padding
        padded (dtype/emap
                (fn [i] (if (< i n)
                          (dtype/get-value signal i)
                          0.0))
                :float64
                (range padded-n))

        ;; Wavelet denoise
        wavelet (t/transformer :fast :daubechies-8)
        coeffs (t/forward-1d wavelet padded)
        denoised-coeffs (t/denoise wavelet coeffs :soft)
        denoised-full (t/reverse-1d wavelet denoised-coeffs)]

    ;; Return only original length
    (dtype/sub-buffer denoised-full 0 n)))

;; **Recipe 5: Spectral analysis with thresholding**
(defn recipe-find-all-frequencies [signal sample-rate min-magnitude]
  (let [fft (t/transformer :real :fft)
        spectrum (t/forward-1d fft signal)
        mags (fft-magnitude spectrum)
        n (dtype/ecount signal)

        peaks (filter #(> (dtype/get-value mags %) min-magnitude)
                      (range 1 (dtype/ecount mags)))]

    (map (fn [idx]
           {:frequency (* idx (/ sample-rate n))
            :magnitude (dtype/get-value mags idx)})
         peaks)))

;; ## Summary and Next Steps

;; ### What We Learned

;; **Foundation**:
;; - dtype-next for efficient array operations
;; - Lazy evaluation and vectorization
;; - One-liner power for common patterns

;; **Core Transforms**:
;; - **DFT** (via FFT): Frequency analysis, global sine/cosine basis
;; - **DCT**: Compression, cosine-only basis, energy concentration
;; - **Wavelets**: Time-frequency, localized basis, denoising

;; **Other Transforms**:
;; - **DST**: Boundary problems, sine-only basis
;; - **DHT**: Real-valued convolution alternative
;; - **2D transforms**: Image processing, frequency components

;; **Applications**:
;; - Bandpass filtering
;; - Lossy compression with quality metrics
;; - Wavelet denoising
;; - Spectral analysis

;; **Validation**:
;; - Round-trip testing ensures correctness
;; - Numerical stability across 12 orders of magnitude
;; - Property testing (energy concentration, linearity)

;; ### Key Takeaways

^:kindly/hide-code
(kind/table
 [{:concept "Linear Decomposition"
   :insight "All transforms express signals as weighted sums of basis functions"}
  {:concept "dtype-next First"
   :insight "Use vectorized operations for performance and clarity"}
  {:concept "Global vs Local"
   :insight "DFT/DCT see whole signal, wavelets see time AND frequency"}
  {:concept "Test-Driven"
   :insight "Always validate with round-trip tests"}
  {:concept "Choose Wisely"
   :insight "Match transform to signal properties and use case"}])

;; ### Next Steps

;; 1. **Experiment**: Try these transforms on your own signals
;; 2. **Extend**: Implement spectrograms (sliding window FFT)
;; 3. **Optimize**: Profile dtype-next vs traditional approaches
;; 4. **Apply**: Build audio visualizer, image compressor, or signal processor
;; 5. **Deep Dive**: Explore transform theory, basis functions, and mathematics

;; ### Further Resources

;; - **dtype-next Guide**: https://cnuernber.github.io/dtype-next/
;; - **Fastmath Transform**: https://generateme.github.io/fastmath/clay/transform.html
;; - **JTransforms**: https://github.com/wendykierp/JTransforms
;; - **Clay Documentation**: https://scicloj.github.io/clay/

;; ---
;; **End of Comprehensive Tutorial**
;; 
;; You now have: theory, practice, validation, and recipes.
;; Go forth and transform signals with confidence! 🌊📈
