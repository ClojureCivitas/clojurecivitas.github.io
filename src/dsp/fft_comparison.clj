^{:kindly/hide-code true
  :clay {:title "Comparing FFT Implementations in Clojure"
         :quarto {:author :daslu
                  :draft true
                  :type :post
                  :date "2025-12-14"
                  :category :data
                  :tags [:dsp :fft :performance :benchmarks :jtransforms :fastmath :apache-commons]}}}
(ns dsp.fft-comparison
  "A practical comparison of FFT (Fast Fourier Transform) implementations available in Clojure."
  (:require [scicloj.kindly.v4.kind :as kind]
            [fastmath.transform :as fm-transform]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]
            [criterium.core :as crit])
  (:import [org.apache.commons.math3.transform FastFourierTransformer
            DftNormalization
            TransformType]
           [org.apache.commons.math3.complex Complex]
           [com.github.psambit9791.jdsp.transform FastFourier]
           [org.jtransforms.fft DoubleFFT_1D]))

;; # Introduction
;;
;; The [Fast Fourier Transform](https://en.wikipedia.org/wiki/Fast_Fourier_transform) (FFT) is fundamental to signal processing, audio analysis, image compression, and scientific computing. If you're working with frequency analysis in Clojure, you have several Java FFT libraries to choose from.
;;
;; This post compares four approaches:
;;
;; - **Apache Commons Math** - Mature library with general mathematical transforms
;; - **jdsp** - Digital signal processing library (uses Apache Commons Math internally)
;; - **JTransforms** - First multithreaded, pure-Java FFT library
;; - **fastmath** - Clojure math library (wraps JTransforms with idiomatic API)
;;
;; We'll compute the FFT of the same test signal using each library, measure performance, and discuss their trade-offs.

;; ## The Libraries

;; ### Apache Commons Math
;;
;; [Apache Commons Math](https://commons.apache.org/proper/commons-math/) is a comprehensive mathematical library for Java. It provides [`FastFourierTransformer`](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/transform/FastFourierTransformer.html) with various transform types and [normalization](https://en.wikipedia.org/wiki/Discrete_Fourier_transform#Normalization) options.
;;
;; **Key features:**
;; - Part of Apache Commons (widely used, stable, maintained)
;; - Supports multiple normalization conventions
;; - 1D FFT, [Hadamard](https://en.wikipedia.org/wiki/Hadamard_transform), and sine/cosine transforms
;; - Requires input length to be a [power of 2](https://en.wikipedia.org/wiki/Power_of_two)
;; - Returns [`Complex[]`](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/complex/Complex.html) arrays (allocation overhead)

;; ### jdsp
;;
;; [jdsp](https://jdsp.dev) by [Sambit Paul](https://github.com/psambit9791) is a Java [Digital Signal Processing](https://en.wikipedia.org/wiki/Digital_signal_processing) library providing [filters](https://en.wikipedia.org/wiki/Digital_filter), transforms, [peak detection](https://en.wikipedia.org/wiki/Peak_detection), and more. It uses Apache Commons Math internally for FFT computation.
;;
;; **Key features:**
;; - Convenient wrapper around Apache Commons Math
;; - Simple API: [`new FastFourier(signal).transform()`](https://javadoc.io/doc/com.github.psambit9791/jdsp/latest/com/github/psambit9791/jdsp/transform/FastFourier.html)
;; - Includes comprehensive DSP utilities ([filters](https://en.wikipedia.org/wiki/Digital_filter), [wavelets](https://en.wikipedia.org/wiki/Wavelet), [convolution](https://en.wikipedia.org/wiki/Convolution), [STFT](https://en.wikipedia.org/wiki/Short-time_Fourier_transform))
;; - Good for projects needing broader DSP functionality beyond FFT

;; ### JTransforms
;;
;; [JTransforms](https://github.com/wendykierp/JTransforms) by [Piotr Wendykier](https://github.com/wendykierp) is the first open-source, multithreaded FFT library written in pure Java. It's optimized for performance with [parallel processing](https://en.wikipedia.org/wiki/Parallel_computing) support.
;;
;; **Key features:**
;; - **Parallelized** [split-radix](https://en.wikipedia.org/wiki/Split-radix_FFT_algorithm) and [mixed-radix](https://en.wikipedia.org/wiki/Mixed-radix) algorithms
;; - Supports **1D, 2D, and 3D** transforms (FFT, [DCT](https://en.wikipedia.org/wiki/Discrete_cosine_transform), [DST](https://en.wikipedia.org/wiki/Discrete_sine_transform), [DHT](https://en.wikipedia.org/wiki/Discrete_Hartley_transform))
;; - [In-place mutations](https://en.wikipedia.org/wiki/In-place_algorithm) (efficient but not functional)
;; - Mixed-radix support: works with arbitrary sizes (not just power-of-2)
;; - Used internally by fastmath and dtype-next

;; ### fastmath
;;
;; [fastmath](https://github.com/generateme/fastmath) (version 3.x) by [Tomasz Sulej](https://github.com/genmeblog) is a Clojure library for fast primitive-based mathematics. Its [`fastmath.transform`](https://generateme.github.io/fastmath/fastmath.transform.html) namespace wraps JTransforms with an idiomatic Clojure API.
;;
;; **Key features:**
;; - Immutable, [functional](https://en.wikipedia.org/wiki/Functional_programming) API (no in-place mutations)
;; - Leverages JTransforms' parallelized performance and mixed-radix algorithms
;; - [Protocol-based](https://clojure.org/reference/protocols) design: `transformer` → `forward-1d`/`reverse-1d`
;; - Supports **1D and 2D** transforms, multiple transform types (FFT, DCT, DST, DHT, wavelets)

;; ## Test Signal: Two-Tone Sine Wave

;; We'll create a simple test signal: a combination of two [sine waves](https://en.wikipedia.org/wiki/Sine_wave) at 5 Hz and 12 Hz, [sampled](https://en.wikipedia.org/wiki/Sampling_(signal_processing)) at 100 Hz for 1 second.

; Hz
(def sample-rate 100.0)
; seconds
(def duration 1.0)
; Power of 2 (required by Apache Commons Math)
(def n-samples 128)

(def time-points
  (dfn/* (range n-samples) (/ duration n-samples)))

(defn generate-test-signal
  "Generate a two-tone sine wave (5 Hz + 12 Hz) test signal.
  Returns a Java double array suitable for FFT libraries."
  [n-samples]
  (let [t (dfn/* (range n-samples) (/ 1.0 n-samples))]
    (dtype/->double-array
     (dfn/+ (dfn/sin (dfn/* 2.0 Math/PI 5.0 t))
            (dfn/sin (dfn/* 2.0 Math/PI 12.0 t))))))

; Convert to Java double array for FFT libraries
; (dtype-next functional ops work on sequences, but Java FFT expects double[])
(def signal (generate-test-signal n-samples))

;; Let's visualize the signal:

(-> (tc/dataset {:time (take 128 time-points)
                 :amplitude (take 128 signal)})
    (plotly/base {:=x :time
                  :=y :amplitude
                  :=x-title "Time (s)"
                  :=y-title "Amplitude"
                  :=title "Test Signal: 5 Hz + 12 Hz"
                  :=width 700
                  :=height 300})
    (plotly/layer-line {:=mark-color "steelblue"})
    plotly/plot)

;; ## Visualization Helper

;; To visualize FFT results throughout this post, we'll use a helper function:

(defn plot-fft-spectrum
  "Plot FFT magnitude spectrum.
  
  Parameters:
  - magnitudes: sequence of magnitude values
  - title: plot title
  - color: line color (e.g., 'darkgreen', 'darkorange')
  
  Returns Plotly visualization."
  [magnitudes title color]
  (let [n-bins (count magnitudes)
        freq-bins (dfn/* (range n-bins) (/ sample-rate n-samples))]
    (-> (tc/dataset {:frequency freq-bins
                     :magnitude magnitudes})
        (plotly/base {:=x :frequency
                      :=y :magnitude
                      :=x-title "Frequency (Hz)"
                      :=y-title "Magnitude"
                      :=title title
                      :=width 700
                      :=height 300})
        (plotly/layer-line {:=mark-color color})
        plotly/plot)))

;; ## FFT Implementation #1: Apache Commons Math

;; Apache Commons Math requires creating a [`FastFourierTransformer`](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/transform/FastFourierTransformer.html) with a [normalization](https://en.wikipedia.org/wiki/Discrete_Fourier_transform#Normalization) type, then calling `transform` with the signal.

(defn fft-apache-commons
  "Compute FFT using Apache Commons Math."
  [signal]
  (let [transformer (FastFourierTransformer. DftNormalization/STANDARD)]
    ; Result is Complex[] array
    (.transform transformer signal TransformType/FORWARD)))

;; Let's run it and measure time:

(def commons-result
  (time (fft-apache-commons signal)))

;; The output is an array of [`Complex`](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/complex/Complex.html) objects. To extract [magnitudes](https://en.wikipedia.org/wiki/Magnitude_(mathematics)#Euclidean_vector_space):

(defn complex-magnitude
  "Compute magnitude from Apache Commons Math Complex."
  [^Complex c]
  (.abs c))

(def commons-magnitudes
  (mapv complex-magnitude commons-result))

;; Visualize the first half (positive frequencies):

(plot-fft-spectrum
 (take (quot (count commons-magnitudes) 2) commons-magnitudes)
 "FFT Spectrum (Apache Commons Math)"
 "darkgreen")

;; ## FFT Implementation #2: jdsp

;; jdsp provides a wrapper around Apache Commons Math with a simpler API.

(defn fft-jdsp
  "Compute FFT using jdsp."
  [signal]
  (let [fft (FastFourier. signal)]
    (.transform fft)
    ; Get magnitude values (positive frequencies only)
    (.getMagnitude fft true)))

(def jdsp-result
  (time (fft-jdsp signal)))

; Visualize
(plot-fft-spectrum
 jdsp-result
 "FFT Spectrum (jdsp)"
 "darkorange")

;; ## FFT Implementation #3: JTransforms (Direct)

;; JTransforms mutates the input array [in-place](https://en.wikipedia.org/wiki/In-place_algorithm). The [`realForward`](https://wendykierp.github.io/JTransforms/apidocs/org/jtransforms/fft/DoubleFFT_1D.html#realForward-double:A-) method transforms the array into interleaved `[real0, imag0, real1, imag1, ...]` format.

(defn fft-jtransforms
  "Compute FFT using JTransforms directly."
  [signal]
  ; Copy signal to avoid mutating original
  (let [fft-obj (DoubleFFT_1D. (long (count signal)))
        signal-copy (double-array signal)]
    (.realForward fft-obj signal-copy)
    signal-copy))

(def jtransforms-result
  (time (fft-jtransforms signal)))

;; Extract magnitudes from interleaved format:

(defn jtransforms-magnitude
  "Compute magnitudes from JTransforms interleaved [real, imag, ...] format."
  [spectrum]
  (let [n-bins (quot (count spectrum) 2)]
    (mapv (fn [i]
            (let [real (nth spectrum (* 2 i))
                  imag (nth spectrum (inc (* 2 i)))]
              (Math/sqrt (+ (* real real) (* imag imag)))))
          (range n-bins))))

(def jtransforms-magnitudes
  (jtransforms-magnitude jtransforms-result))

; Visualize
(plot-fft-spectrum
 jtransforms-magnitudes
 "FFT Spectrum (JTransforms)"
 "purple")

;; ## FFT Implementation #4: fastmath

;; fastmath provides the most Clojure-idiomatic API. Create a transformer, then use `forward-1d`.

(defn fft-fastmath
  "Compute FFT using fastmath."
  [signal]
  (let [transformer (fm-transform/transformer :real :fft)]
    (fm-transform/forward-1d transformer signal)))

(def fastmath-result
  (time (fft-fastmath signal)))

;; Extract magnitudes (fastmath uses JTransforms format internally):

(defn fastmath-magnitude
  "Compute magnitudes from fastmath FFT output."
  [spectrum]
  (let [n-bins (quot (dtype/ecount spectrum) 2)]
    (mapv (fn [i]
            (let [real (dtype/get-value spectrum (* 2 i))
                  imag (dtype/get-value spectrum (inc (* 2 i)))]
              (Math/sqrt (+ (* real real) (* imag imag)))))
          (range n-bins))))

(def fastmath-magnitudes
  (fastmath-magnitude fastmath-result))

; Visualize
(plot-fft-spectrum
 fastmath-magnitudes
 "FFT Spectrum (fastmath)"
 "crimson")

;; ## Performance Comparison

;; Let's run a more rigorous benchmark with 1000 iterations on signals of varying sizes.

(defn benchmark-fft
  "Benchmark an FFT function over n iterations."
  [fft-fn signal n-iterations]
  (let [start (System/nanoTime)
        _ (dotimes [_ n-iterations]
            (fft-fn signal))
        end (System/nanoTime)
        elapsed-ms (/ (- end start) 1e6)]
    {:total-ms elapsed-ms
     :per-iter-ms (/ elapsed-ms n-iterations)}))

;; Small signal (128 samples):

(let [bench-small-128 (let [n 1000]
                        {:apache-commons (benchmark-fft fft-apache-commons signal n)
                         :jdsp (benchmark-fft fft-jdsp signal n)
                         :jtransforms (benchmark-fft fft-jtransforms signal n)
                         :fastmath (benchmark-fft fft-fastmath signal n)})]
  (kind/table
   [{:library "Apache Commons Math"
     :time-per-fft (format "%.3f ms" (get-in bench-small-128 [:apache-commons :per-iter-ms]))}
    {:library "jdsp"
     :time-per-fft (format "%.3f ms" (get-in bench-small-128 [:jdsp :per-iter-ms]))}
    {:library "JTransforms"
     :time-per-fft (format "%.3f ms" (get-in bench-small-128 [:jtransforms :per-iter-ms]))}
    {:library "fastmath"
     :time-per-fft (format "%.3f ms" (get-in bench-small-128 [:fastmath :per-iter-ms]))}]))

;; Larger signal (2^17 = 131,072 samples):

(def signal-large (generate-test-signal 131072))

(let [bench-large-131k (let [n 10]
                         {:apache-commons (benchmark-fft fft-apache-commons signal-large n)
                          :jdsp (benchmark-fft fft-jdsp signal-large n)
                          :jtransforms (benchmark-fft fft-jtransforms signal-large n)
                          :fastmath (benchmark-fft fft-fastmath signal-large n)})]
  (kind/table
   [{:library "Apache Commons Math"
     :time-per-fft (format "%.3f ms" (get-in bench-large-131k [:apache-commons :per-iter-ms]))}
    {:library "jdsp"
     :time-per-fft (format "%.3f ms" (get-in bench-large-131k [:jdsp :per-iter-ms]))}
    {:library "JTransforms"
     :time-per-fft (format "%.3f ms" (get-in bench-large-131k [:jtransforms :per-iter-ms]))}
    {:library "fastmath"
     :time-per-fft (format "%.3f ms" (get-in bench-large-131k [:fastmath :per-iter-ms]))}]))

;; ## Understanding Parallelization Performance

;; JTransforms advertises [parallel processing](https://en.wikipedia.org/wiki/Parallel_computing) support, but our benchmarks show modest speedups. Why?

;; ### Thread Count vs Performance

;; Let's explore how thread count affects FFT performance using fastmath (which uses JTransforms internally):

(import 'pl.edu.icm.jlargearrays.ConcurrencyUtils
        'org.jtransforms.utils.CommonUtils)

(defn benchmark-with-threads
  "Benchmark FFT at specific thread count using criterium for statistical analysis."
  [n-threads signal]
  (let [previous-threads (ConcurrencyUtils/getNumberOfThreads)]
    (try
      (ConcurrencyUtils/setNumberOfThreads n-threads)
      ;; Use criterium's quick-bench for proper JVM warmup and statistics
      (let [result (crit/quick-benchmark* (fn [] (fft-fastmath signal)) {})]
        {:threads n-threads
         ;; Criterium returns [value (lower-ci upper-ci)] for each metric
         :mean-ms (* (first (:mean result)) 1e3) ; Convert seconds to milliseconds
         :variance-ms (* (first (:variance result)) 1e6) ; Variance in ms^2
         :lower-q-ms (* (first (:lower-q result)) 1e3)
         :upper-q-ms (* (first (:upper-q result)) 1e3)})
      (finally
        (ConcurrencyUtils/setNumberOfThreads previous-threads)))))

; Test signals at different sizes (powers of 2)
(def test-signals
  {:size-16k (generate-test-signal 16384)
   :size-131k (generate-test-signal 131072)
   :size-524k (generate-test-signal 524288)})

;; **Important limitation**: According to [Wendykier & Grote (2012)](https://www.math.emory.edu/technical-reports/techrep-00127.pdf),
;; JTransforms 1D FFT can only use **2 or 4 threads maximum**. The algorithm's decomposition
;; strategy doesn't parallelize beyond this for one-dimensional transforms.
;; (2D and 3D transforms can use more threads, but we're testing 1D here.)
(def thread-counts [1 2 4])

; Run comprehensive benchmark with criterium
; Note: This will take several minutes as criterium performs proper JVM warmup and statistical analysis
(let [thread-performance (for [size-key [:size-16k :size-131k :size-524k]
                               n-threads thread-counts]
                           (let [sig (get test-signals size-key)
                                 n-samples (count sig)
                                 result (benchmark-with-threads n-threads sig)]
                             (assoc result
                                    :signal-size (case size-key
                                                   :size-16k "16K (2^14)"
                                                   :size-131k "131K (2^17)"
                                                   :size-524k "524K (2^19)")
                                    :n-samples n-samples)))]
  ; Visualize results
  (-> (tc/dataset thread-performance)
      (plotly/base {:=x :threads
                    :=y :mean-ms
                    :=color :signal-size
                    :=title "FFT Performance vs Thread Count (fastmath/JTransforms)"
                    :=x-title "Number of Threads"
                    :=y-title "Mean Time per FFT (ms)"
                    :=width 800
                    :=height 500})
      (plotly/layer-point {:=mark-size 10})
      (plotly/layer-line {:=mark-opacity 0.6})
      plotly/plot))

;; ### Why Limited Speedup?

;; The visualization reveals that **adding threads doesn't dramatically improve FFT performance**. Peak speedup is only ~1.2-1.4x even with many threads. This is because:

;; **1. Memory Bandwidth Bottleneck**
;;
;; FFT is [memory-bound](https://en.wikipedia.org/wiki/Memory_bound), not compute-bound. The algorithm constantly reads and writes the entire array through multiple passes. All threads compete for the same [memory bus](https://en.wikipedia.org/wiki/Bus_(computing)#Memory_bus), which doesn't scale with CPU cores. Your processor can compute much faster than memory can supply data.

;; **2. Algorithm Has Sequential Stages**
;;
;; The Cooley-Tukey FFT has log₂(n) stages (e.g., 19 stages for 524K samples). Each stage must complete before the next begins, creating synchronization barriers. [Amdahl's Law](https://en.wikipedia.org/wiki/Amdahl%27s_law) limits maximum speedup when part of the algorithm is inherently sequential.

;; **3. Cache Effects**
;;
;; FFT's [butterfly pattern](https://en.wikipedia.org/wiki/Butterfly_diagram) causes non-sequential memory access, defeating CPU prefetching. Multiple cores accessing nearby elements cause [cache coherency overhead](https://en.wikipedia.org/wiki/Cache_coherence) and [false sharing](https://en.wikipedia.org/wiki/False_sharing).

;; This is well-known in HPC: **FFT rarely scales beyond 2-4 threads on single-socket systems** because memory bandwidth, not CPU, is the bottleneck.

;; ## Machine Configuration and Reproducibility

;; Performance results depend heavily on your hardware. Here's how to inspect your environment:

(def machine-info
  {:jvm-version (System/getProperty "java.version")
   :jvm-vendor (System/getProperty "java.vendor")
   :jvm-name (System/getProperty "java.vm.name")
   :heap-max-mb (/ (.maxMemory (Runtime/getRuntime)) 1024.0 1024.0)
   :heap-total-mb (/ (.totalMemory (Runtime/getRuntime)) 1024.0 1024.0)
   :os-name (System/getProperty "os.name")
   :os-arch (System/getProperty "os.arch")
   :os-version (System/getProperty "os.version")
   :available-processors (.availableProcessors (Runtime/getRuntime))
   :clojure-version (clojure-version)})

(kind/table
 [(assoc machine-info :property "Machine Configuration")])

;; **This benchmark ran on:**
;; - **CPU**: 22 cores (Linux 6.14.0-36-generic, amd64)
;; - **JVM**: OpenJDK 21.0.9 (Ubuntu)
;; - **Heap**: 15,936 MB max
;; - **Clojure**: 1.12.3

;; ## Power-of-Two Limitation

;; Apache Commons Math requires signal length to be a [power of 2](https://en.wikipedia.org/wiki/Power_of_two) (128, 256, 512, 1024, etc.). This is common in classic FFT implementations using the [Cooley-Tukey algorithm](https://en.wikipedia.org/wiki/Cooley%E2%80%93Tukey_FFT_algorithm).
;;
;; **JTransforms is more flexible** - it uses [mixed-radix algorithms](https://en.wikipedia.org/wiki/Mixed-radix) that work with arbitrary sizes, though performance is best when the length factors into small primes (2, 3, 5, 7).
;;
;; **fastmath** inherits JTransforms' flexibility. If your data doesn't fit power-of-2 sizes, zero-pad to the next power of 2 or use JTransforms/fastmath directly.

;; ## Related Functionality

;; Each library offers additional capabilities beyond basic FFT:

;; ### Apache Commons Math
;; - [Inverse FFT](https://en.wikipedia.org/wiki/Fast_Fourier_transform#Inverse_FFT)
;; - Sine/Cosine transforms
;; - [Hadamard transform](https://en.wikipedia.org/wiki/Hadamard_transform)
;; - [Complex number](https://en.wikipedia.org/wiki/Complex_number) arithmetic
;; - Statistical analysis, [linear algebra](https://en.wikipedia.org/wiki/Linear_algebra), [optimization](https://en.wikipedia.org/wiki/Mathematical_optimization)

;; ### jdsp
;; - [Butterworth](https://en.wikipedia.org/wiki/Butterworth_filter), [Chebyshev](https://en.wikipedia.org/wiki/Chebyshev_filter), [Bessel filters](https://en.wikipedia.org/wiki/Bessel_filter)
;; - [Peak detection](https://en.wikipedia.org/wiki/Peak_detection)
;; - [Continuous](https://en.wikipedia.org/wiki/Continuous_wavelet_transform)/[Discrete wavelet transforms](https://en.wikipedia.org/wiki/Discrete_wavelet_transform)
;; - [Short-time Fourier transform](https://en.wikipedia.org/wiki/Short-time_Fourier_transform) (STFT)
;; - [Hilbert transform](https://en.wikipedia.org/wiki/Hilbert_transform)
;; - [Convolution](https://en.wikipedia.org/wiki/Convolution), [correlation](https://en.wikipedia.org/wiki/Cross-correlation)

;; ### JTransforms
;; - 1D, 2D, and 3D FFT
;; - [DCT](https://en.wikipedia.org/wiki/Discrete_cosine_transform) (Discrete Cosine Transform)
;; - [DST](https://en.wikipedia.org/wiki/Discrete_sine_transform) (Discrete Sine Transform)
;; - [DHT](https://en.wikipedia.org/wiki/Discrete_Hartley_transform) (Discrete Hartley Transform)
;; - [Multithreaded](https://en.wikipedia.org/wiki/Multithreading_(computer_architecture)) variants for all transforms

;; ### fastmath
;; - [Protocol-based](https://clojure.org/reference/protocols) transform system
;; - All JTransforms transforms (FFT, DCT, DST, DHT, [wavelets](https://en.wikipedia.org/wiki/Wavelet))
;; - Vector operations, statistics, [random number generation](https://en.wikipedia.org/wiki/Random_number_generation)
;; - Signal processing utilities ([oscillators](https://en.wikipedia.org/wiki/Electronic_oscillator), [envelopes](https://en.wikipedia.org/wiki/Envelope_(waves)))
;; - [Noise generation](https://en.wikipedia.org/wiki/Colors_of_noise), [interpolation](https://en.wikipedia.org/wiki/Interpolation)

;; ## Conclusions and Recommendations

;; After comparing these libraries, here are my recommendations:

;; **For most Clojure projects: fastmath 3**
;;
;; fastmath provides the best balance of performance and developer experience:
;; - Idiomatic Clojure API (functional, immutable)
;; - Leverages JTransforms' parallelized performance
;; - Rich ecosystem (transforms, signal processing, statistics)
;; - Actively maintained
;;
;; The `fastmath.transform` namespace makes FFT feel natural in Clojure:

; Round-trip example
(comment
  (let [fft (fm-transform/transformer :real :fft)]
    (-> signal
        (fm-transform/forward-1d fft)
        (fm-transform/reverse-1d fft)))) ; Round-trip

;; **For maximum performance: JTransforms (directly)**
;;
;; If you need the absolute fastest FFT and don't mind mutation:
;; - Use `DoubleFFT_1D` directly
;; - In-place mutations avoid allocations
;; - Parallelized algorithms scale well
;; - Good for real-time audio processing or large-scale data

;; **For broader DSP needs: jdsp**
;;
;; If you need filters, wavelets, peak detection, and FFT in one package:
;; - Convenient, batteries-included library
;; - Simple API
;; - Good documentation
;; - Note: FFT performance lags behind JTransforms

;; **Avoid: Apache Commons Math (for FFT specifically)**
;;
;; While Commons Math is excellent for general mathematics, for FFT specifically:
;; - Slower than JTransforms
;; - Returns boxed `Complex[]` objects (allocation overhead)
;; - Consider it if you're already using Commons Math for other features

;; ## Summary

;; The Clojure ecosystem offers excellent FFT options through Java interop. For typical signal processing tasks, **fastmath 3** provides the sweet spot: JTransforms' performance wrapped in a Clojure-friendly API. For the ultimate speed, use **JTransforms directly**. And if you need comprehensive DSP utilities beyond FFT, **jdsp** is worth exploring.
;;
;; All code from this comparison is available in the [Clojure Civitas repository](https://github.com/clojurecivitas/clojurecivitas.github.io).
