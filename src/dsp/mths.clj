^{:kindly/hide-code true
  :clay {:title "Extracting Heart Rate from Smartphone Camera Video"
         :quarto {:author [:daslu]
                  :description "Using signal processing to extract and validate heart rate from Camera PPG data against clinical ground truth"
                  :category :clojure
                  :type :post
                  :date "2025-11-21"
                  :tags [:dsp :signal-processing :biometrics :camera-ppg :heart-rate]
                  :draft true}}}
(ns dsp.mths
  (:require [scicloj.kindly.v4.kind :as kind]))

;; ## Introduction
;;
;; ### What is Camera PPG?
;;
;; **[Photoplethysmography (PPG)](https://en.wikipedia.org/wiki/Photoplethysmogram)** is a technique for measuring blood volume changes in tissue, typically used to measure heart rate and blood oxygen levels. Traditional PPG sensors (like those in fitness watches) use dedicated infrared LEDs and photodetectors.
;;
;; **Camera PPG** (also called remote PPG or rPPG) is a fascinating alternative: it uses an ordinary smartphone camera to detect these same blood volume changes by analyzing subtle color variations in the skin. When you place your fingertip over the camera, each heartbeat causes a tiny change in blood volume that slightly alters the amount of light absorbed by the tissue. By recording video and analyzing the RGB color channels over time, we can extract vital signs like [heart rate](https://en.wikipedia.org/wiki/Heart_rate) and [SpO2](https://en.wikipedia.org/wiki/Oxygen_saturation_(medicine)) (blood oxygen saturation).
;;
;; ### The MTHS Dataset
;;
;; We're using the [MTHS dataset from the MEDVSE repository](https://github.com/MahdiFarvardin/MEDVSE), which contains smartphone camera PPG data from 60 subjects. For each subject, the dataset provides:
;;
;; - **Signal data** (`signal_x.npy`): RGB time series sampled at 30 [Hz](https://en.wikipedia.org/wiki/Hertz) from fingertip videos
;; - **Ground truth labels** (`label_x.npy`): Heart rate ([bpm](https://en.wikipedia.org/wiki/Heart_rate)) and SpO2 (%) sampled at 1 Hz, measured with clinical-grade equipment
;;
;; ### What We'll Explore
;;
;; **Our goal is to extract heart rate from smartphone camera video and validate it against clinical measurements.**
;;
;; The MTHS dataset provides both the raw camera data (RGB signals) and ground truth heart rate measurements from medical-grade equipment. This allows us to:
;;
;; 1. Develop and test signal processing algorithms
;; 2. Validate our extracted heart rates against the ground truth
;; 3. Calibrate parameters (filter cutoffs, window sizes, etc.) for optimal accuracy
;;
;; In this notebook, we'll walk through the complete pipeline:
;;
;; 1. **Loading and visualizing** raw [RGB](https://en.wikipedia.org/wiki/RGB_color_model) signals from multiple subjects
;; 2. **Signal preprocessing**: removing [DC offset](https://en.wikipedia.org/wiki/DC_bias), [bandpass filtering](https://en.wikipedia.org/wiki/Band-pass_filter) to isolate heart rate frequencies (0.5-5 Hz, corresponding to 30-300 bpm), and [standardization](https://en.wikipedia.org/wiki/Standard_score)
;; 3. **Power spectrum analysis**: using windowed [FFT](https://en.wikipedia.org/wiki/Fast_Fourier_transform) to identify the dominant frequency
;; 4. **Heart rate extraction**: finding the peak frequency and converting to bpm
;; 5. **Validation**: comparing our estimates with ground truth measurements
;;
;; This exploration demonstrates how Clojure's ecosystem—combining tablecloth for data wrangling, dtype-next for efficient numerical computation, jdsp for signal processing, and tableplot for visualization—provides powerful tools for biomedical signal analysis.

;; ## Setup

(ns dsp.mths
  (:require
   ;; Python interop for loading .npy files
   [libpython-clj2.require :refer [require-python]]
   [libpython-clj2.python :refer [py. py.. py.-] :as py]
   ;; Support for numpy array conversion to dtype-next
   [libpython-clj2.python.np-array]

   ;; Numerical computing with dtype-next
   [tech.v3.datatype :as dtype] ; Array operations, shapes, types
   [tech.v3.datatype.functional :as dfn] ; Vectorized math operations
   [tech.v3.tensor :as tensor] ; Multi-dimensional array operations
   [tech.v3.dataset :as ds] ; Dataset core functionality
   [tech.v3.dataset.tensor :as ds-tensor] ; Dataset <-> tensor conversions
   [tech.v3.parallel.for :as pfor] ; Parallel processing

   ;; Data manipulation with tablecloth
   [tablecloth.api :as tc] ; Dataset transformations
   [tablecloth.column.api :as tcc] ; Column-level operations

   ;; Visualization
   [scicloj.tableplot.v1.plotly :as plotly] ; Declarative plotting
   [scicloj.kindly.v4.kind :as kind] ; Rendering hints

   ;; Statistics
   [fastmath.stats :as stats] ; Statistical functions

   ;; Utilities
   [clojure.java.io :as io] ; File I/O
   [babashka.fs :as fs] ; Filesystem operations
   [clojure.string :as str]) ; String manipulation

  ;; Java DSP library (jdsp) - signal processing tools
  (:import
   [com.github.psambit9791.jdsp.filter Butterworth Chebyshev] ; Digital filters
   [com.github.psambit9791.jdsp.signal Detrend] ; DC removal
   [com.github.psambit9791.jdsp.signal.peaks FindPeak Peak] ; Peak detection
   [com.github.psambit9791.jdsp.transform DiscreteFourier FastFourier] ; FFT
   [com.github.psambit9791.jdsp.windows Hanning])) ; Window functions

(require-python '[numpy :as np])

^:kindly/hide-code
(kind/hiccup
 [:style
  ".clay-dataset {
  max-height:400px; 
  overflow-y: auto;
}
.clay-table {
  max-height:400px; 
  overflow-y: auto;
}
"])

;; ## Reading data
;;
;; The MTHS dataset stores signals and labels as [NumPy](https://numpy.org/) `.npy` files. Each subject has two files:
;;
;; - `signal_X.npy`: RGB [time series](https://en.wikipedia.org/wiki/Time_series) (3 channels × time samples)
;; - `label_X.npy`: Ground truth measurements
;;
;; We'll use libpython-clj to load these files via NumPy, then convert them to dtype-next structures for efficient processing in Clojure.

;; We assume you have downloaded the MEDVSE repo alongside your Clojure project repo.

(def data-base-path
  "../MEDVSE/MTHS/Data/")

;; Let's see how we read one file as a NumPy array:

(np/load (str data-base-path "/signal_12.npy"))

;; Now, let's read all data files and organize them in a single map.
;; We'll use keywords like `[:signal 12]` and `[:label 12]` as keys for easy access.

(def raw-data
  (-> data-base-path
      fs/list-dir
      (->> (map (fn [path]
                  (let [nam (-> path
                                fs/file-name
                                (str/replace #"\.npy" ""))]
                    [[(keyword (re-find #"signal|label" nam))
                      (-> nam
                          (str/split #"_")
                          last
                          Integer/parseInt)]
                     (-> path
                         str
                         np/load)])))
           (into {}))))

;; NumPy arrays can be inspected using dtype-next:

(dtype/shape (raw-data [:signal 23])) ; 30Hz signal → [n-samples, 3] array
(dtype/shape (raw-data [:label 23])) ; 1Hz labels → [n-samples, 2] array (heart rate + SpO2)

;; The [sampling rate](https://en.wikipedia.org/wiki/Sampling_(signal_processing)) is 30 Hz (30 samples per second)
(def sampling-rate 30)

;; ## Ground Truth Data
;;
;; Before we dive into signal processing, let's look at the ground truth labels.
;; These provide the reference heart rate values we'll use to validate our algorithms.

(defn labels [i]
  (some-> [:label i]
          raw-data
          ds-tensor/tensor->dataset
          (tc/rename-columns [:heart-rate :spo2])))

;; For example, subject 23's ground truth measurements:

(labels 23)

;; Let's create a helper function to convert a subject's raw signal into a tablecloth dataset
;; with properly named columns (:R, :G, :B) and a time column (:t in seconds):

(defn signal [i]
  (some-> [:signal i]
          raw-data
          ds-tensor/tensor->dataset
          (tc/rename-columns [:R :G :B])
          (tc/add-column :t (tcc// (range) 30.0))))

;; For example:

(signal 23)

;; ## Plotting
;;
;; Let's create a plotting function to visualize all three RGB channels over time.
;; In Camera PPG, different color channels can have different [signal-to-noise ratios](https://en.wikipedia.org/wiki/Signal-to-noise_ratio)
;; depending on skin tone and lighting conditions. Typically, the green channel is
;; strongest due to [hemoglobin](https://en.wikipedia.org/wiki/Hemoglobin)'s absorption characteristics.

(defn plot-signal [s]
  (-> s
      (plotly/base {:=x :t
                    :=mark-opacity 0.7})
      (plotly/layer-line {:=y :R
                          :=mark-color "red"})
      (plotly/layer-line {:=y :G
                          :=mark-color "green"})
      (plotly/layer-line {:=y :B
                          :=mark-color "blue"})))

(-> 23
    signal
    plot-signal)

;; ## Signal Processing Pipeline
;;
;; Raw Camera PPG signals are noisy and contain artifacts. Before we can extract heart rate,
;; we need to clean them up through a series of transformations:
;;
;; 1. **[DC](https://en.wikipedia.org/wiki/Direct_current) removal ([detrending](https://en.wikipedia.org/wiki/Detrended_fluctuation_analysis))**: Removes the constant offset, leaving only the [AC component](https://en.wikipedia.org/wiki/Alternating_current)
;;    (the oscillating part due to heartbeats)
;;
;; 2. **[Bandpass filtering](https://en.wikipedia.org/wiki/Band-pass_filter)**: Keeps only frequencies in the 0.5-5 Hz range, which corresponds
;;    to heart rates between 30-300 bpm. This removes high-frequency [noise](https://en.wikipedia.org/wiki/Noise_(signal_processing)) and low-frequency drift.
;;
;; 3. **[Standardization](https://en.wikipedia.org/wiki/Standard_score)**: Scales the signal to zero [mean](https://en.wikipedia.org/wiki/Mean) and unit [variance](https://en.wikipedia.org/wiki/Variance), making it easier
;;    to compare across subjects and channels.
;;
;; Let's create a utility function to visualize how each transformation affects all subjects:

(defn plot-signals-with-transformations [transformations]
  (kind/table
   {:row-vectors (->> (range 2 62)
                      (pfor/pmap (fn [i]
                                   (->> transformations
                                        vals
                                        (reductions (fn [ds t]
                                                      (tc/update-columns
                                                       ds [:R :G :B] t))
                                                    (signal i))
                                        (map plot-signal)
                                        (cons (kind/md (str "### " i))))))

                      kind/table)
    :column-names (concat ["subject" "raw"]
                          (keys transformations))}))

;; ### Transformation Functions
;;
;; **DC Removal (Detrending)**
;;
;; Removes the constant (DC) component from the signal. This is crucial because camera sensors
;; capture both the steady ambient light level and the small oscillations from blood volume changes.
;; We only care about the oscillations.

(defn remove-dc [signal]
  (-> signal
      double-array
      (Detrend. "constant")
      .detrendSignal))

;; **Bandpass Filter**
;;
;; A 4th-order [Butterworth](https://en.wikipedia.org/wiki/Butterworth_filter) bandpass filter that keeps only the frequencies associated with
;; normal heart rates (0.5-5 Hz = 30-300 bpm). This is a standard technique in PPG signal processing.

(defn bandpass-filter [signal {:keys [fs order low-cutoff high-cutoff]}]
  (let [flt (Butterworth. fs)
        result (.bandPassFilter flt
                                (double-array signal)
                                order
                                low-cutoff
                                high-cutoff)]
    (vec result)))

;; ### Applying the Pipeline
;;
;; Now let's apply our complete preprocessing pipeline to all subjects.
;; We'll visualize the raw signal alongside each transformation step to see
;; how the signal quality improves:

(let [[low-cutoff high-cutoff] [0.5 5]
      window-size 10
      window-samples (* sampling-rate window-size)
      overlap-fraction 0.5
      hop (* sampling-rate window-samples)
      windows-starts (range 0)]
  (plot-signals-with-transformations
   {:remove-dc remove-dc
    :bandpass #(bandpass-filter % {:fs sampling-rate
                                   :order 4
                                   :low-cutoff low-cutoff
                                   :high-cutoff high-cutoff})
    :standardize stats/standardize}))

;; ## Power Spectrum Analysis: Extracting Heart Rate
;;
;; Now comes the key step: **extracting heart rate from the camera signal**.
;;
;; To do this, we'll use **[frequency domain](https://en.wikipedia.org/wiki/Frequency_domain) analysis**.
;; The idea is simple: a heartbeat creates a periodic oscillation in the signal, and we can find
;; the dominant frequency of that oscillation using the [Fast Fourier Transform (FFT)](https://en.wikipedia.org/wiki/Fast_Fourier_transform).
;;
;; Once we identify the peak frequency, we can:
;;
;; 1. Convert it to beats per minute (bpm)
;; 2. Compare it with the ground truth heart rate
;; 3. Evaluate our algorithm's accuracy
;;
;; ### Windowing and Overlap
;;
;; Rather than analyzing the entire signal at once, we'll use **[windowed analysis](https://en.wikipedia.org/wiki/Window_function)**:
;;
;; - Split the signal into overlapping windows (e.g., 10-second windows with 5% overlap)
;; - Apply a **[Hanning window](https://en.wikipedia.org/wiki/Hann_function)** to each segment to reduce [spectral leakage](https://en.wikipedia.org/wiki/Spectral_leakage)
;; - Compute the FFT for each window
;; - The resulting [power spectrum](https://en.wikipedia.org/wiki/Spectral_density) shows which frequencies are strongest
;;
;; The peak frequency in the power spectrum corresponds to the heart rate!
;;
;; ### Example: Single Subject Analysis
;;
;; Let's walk through the complete pipeline for one subject:

(let [;; Frequency range for heart rate (0.65-4 Hz = 39-240 bpm)
      [low-cutoff high-cutoff] [0.65 4]

      ;; Window parameters for spectral analysis
      window-size 10 ; seconds
      window-samples (* sampling-rate window-size) ; 300 samples at 30 Hz

      ;; Create Hanning window to reduce spectral leakage
      hanning (-> window-samples
                  Hanning.
                  .getWindow
                  dtype/as-reader)

      ;; Overlap parameters
      overlap-fraction 0.05 ; 5% overlap between windows
      hop (int (* overlap-fraction window-samples))

      ;; Select subject to analyze
      subject 51
      sig (signal subject)
      n-samples (tc/row-count sig)

      ;; Apply preprocessing pipeline: DC removal → bandpass → standardization
      std (-> sig
              (tc/update-columns [:R :G :B]
                                 #(-> %
                                      remove-dc
                                      (bandpass-filter {:fs sampling-rate
                                                        :order 4
                                                        :low-cutoff low-cutoff
                                                        :high-cutoff high-cutoff})
                                      stats/standardize))
              (tc/select-columns [:R :G :B])
              ds-tensor/dataset->tensor)

      ;; Calculate how many windows we can extract
      n-windows (-> n-samples
                    (- window-samples)
                    (quot hop))

      ;; Create sliding windows: [time × windows × channels]
      windows-shape [window-samples
                     n-windows
                     3]
      windows (tensor/compute-tensor
               windows-shape
               (fn [i j k]
                 ;; Extract sample at time i, window j, channel k
                 (std (+ (* j hop) i)
                      k))
               :float32)

      ;; Apply Hanning window to each segment
      hanninged-windows (tensor/compute-tensor
                         windows-shape
                         (fn [i j k]
                           (* (windows i j k)
                              (hanning i)))
                         :float32)

      ;; Compute power spectrum for each window
      power-spectrum (-> hanninged-windows
                         ;; Rearrange to [windows × channels × time]
                         (tensor/transpose [1 2 0])
                         (tensor/slice 2)
                         ;; Apply FFT to each window
                         (->> (mapv (fn [window]
                                      (let [fft (-> window
                                                    double-array
                                                    DiscreteFourier.)]
                                        (.transform fft)
                                        ;; Get magnitude spectrum (power)
                                        (.getMagnitude fft true)))))
                         tensor/->tensor
                         ;; Reshape to [windows × channels × frequencies]
                         (#(tensor/reshape %
                                           [n-windows
                                            3
                                            (-> % first count)]))
                         ;; Transpose to [frequencies × windows × channels] for plotting
                         (tensor/transpose [2 0 1]))]
  ;; ### Visualizations
  ;;
  ;; We'll create several visualizations to understand the signal processing pipeline:

  (kind/fragment
   [(kind/hiccup
     [:div
      [:h4 "1. Raw Windowed Signal (after preprocessing)"]
      [:p "Each row is a time sample, showing how the signal varies across windows (columns) and channels."]])

    (-> windows
        (dfn/* 100)
        plotly/imshow)

    (kind/hiccup
     [:div
      [:h4 "2. After Hanning Window"]
      [:p "Notice how the edges of each window are tapered to zero. This reduces spectral leakage in the FFT."]])

    (-> hanninged-windows
        (dfn/* 100)
        plotly/imshow)

    (kind/hiccup
     [:div
      [:h4 "3. Power Spectrum"]
      [:p "Each row is a frequency bin. Bright bands indicate strong frequency components. "
       "The dominant frequency (brightest band in the 0.65-4 Hz range) corresponds to the heart rate."]])

    (-> power-spectrum
        plotly/imshow)

    (kind/md "### 4. Power Spectrum Time Series\n\nEach plot shows how the power spectrum evolves over time.")

    (-> power-spectrum
        (tensor/slice 1)
        (->> (map (fn [s]
                    (-> s
                        ds-tensor/tensor->dataset
                        (tc/rename-columns [:R :G :B])
                        plot-signal)))
             (into [:div {:style {:max-height "600px"
                                  :overflow-y "auto"}}])
             kind/hiccup))]))



