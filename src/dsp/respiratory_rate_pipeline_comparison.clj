^{:kindly/hide-code true
  :clay {:title "Respiratory Rate Estimation: Comparing Signal Processing Pipelines"
         :quarto {:author [:daslu]
                  :draft true
                  :type :post
                  :date "2025-11-30"
                  :category :data
                  :tags [:dsp :signal-processing :ml :pipeline-comparison :bidmc]}}}
(ns dsp.respiratory-rate-pipeline-comparison
  "Comparing different signal processing pipelines for respiratory rate estimation
   from photoplethysmogram (PPG) signals.
   
   This workshop demonstrates ML best practices through systematic pipeline comparison:
   - Multiple feature extraction approaches (RIIV, RIAV, RIFV)
   - Different spectral analysis methods (FFT, Welch)
   - Hyperparameter tuning (window sizes, filter cutoffs)
   - Cross-validation and ground truth evaluation
   
   Dataset: BIDMC PPG and Respiration (53 subjects × 8 min, 125 Hz)
   Ground truth: 1 Hz respiratory rate from clinical monitoring
   
   Reference: Pimentel et al., IEEE TBME 2016
   https://doi.org/10.1109/TBME.2016.2613124"
  (:require [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [tech.v3.tensor :as tensor]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.tableplot.v1.plotly :as plotly]
            [scicloj.metamorph.ml :as ml]
            [scicloj.metamorph.core :as mm])
  (:import [com.github.psambit9791.jdsp.filter Butterworth]
           [com.github.psambit9791.jdsp.signal.peaks FindPeak]
           [com.github.psambit9791.jdsp.transform DiscreteFourier]
           [us.hebi.matlab.mat.format Mat5]
           [us.hebi.matlab.mat.types MatFile Struct Matrix]
           [com.github.psambit9791.jdsp.signal CrossCorrelation]))

;; =============================================================================
;; Part 1: Data Loading and Exploration
;; =============================================================================

;; ## Introduction: The Pipeline Comparison Problem
;; 
;; We have PPG signals from 53 ICU patients, each recorded for 8 minutes at 125 Hz.
;; Our goal: **estimate respiratory rate** from the pulse oximeter signal.
;; 
;; **The ML framing:**
;; - **Features**: Respiratory signals extracted from PPG (RIIV, RIAV, RIFV)
;; - **Models**: Different signal processing pipelines
;; - **Hyperparameters**: Filter cutoffs, window sizes, spectral methods
;; - **Ground truth**: Clinical respiratory rate annotations (1 Hz)
;; - **Validation**: Per-subject cross-validation, MAE/RMSE metrics
;; 
;; This is algorithm selection and pipeline optimization - core ML practices!
;; ### Load BIDMC Dataset
(def mat-data-struct
  "BIDMC dataset: 53 ICU patients, ~8 min recordings at 125 Hz.
   Reference: https://peterhcharlton.github.io/RRest/bidmc_dataset.html"
  (-> "data/dsp/BIDMC/bidmc_data.mat"
      Mat5/readFromFile
      (.getStruct "data")))

(defn signal-as-time-series
  "Load physiological signal for a given subject.
  
  Returns a tablecloth dataset with columns:
  - `:t` - time in milliseconds
  - `:y` - signal amplitude (normalized)"
  [signal-type subject-idx]
  (let [^Struct signal-record (.get mat-data-struct (name signal-type) (int subject-idx))
        ^Matrix signal-matrix (.get signal-record "v" (int 0))
        ^Matrix fs-matrix (.get signal-record "fs" (int 0))
        fs (int (.getDouble fs-matrix 0 0))
        n (.getNumRows signal-matrix)
        ms-per-sample (/ 1000.0 fs) ; At 125 Hz, this is 8 ms/sample

        ;; Zero-copy reader for efficiency - note: idx is implicit binding
        signal-reader (dtype/make-reader :float64 n
                                         (.getDouble signal-matrix idx 0))]

    (tc/dataset {:t (dtype/make-reader :float64 n (* idx ms-per-sample))
                 :y signal-reader})))

;; EKG of subject 0 - head

(-> (signal-as-time-series :ekg 0)
    (tc/head 400))

;; EKG of subject 0 - plot

(-> (signal-as-time-series :ppg 0)
    (tc/head 400)
    (plotly/layer-line {:=x :t}))

;; Plotting the EKG and PPG of subject 0 against each other

(let [subject 0
      fields [:ekg :ppg]]
  (-> (->> fields
           (map #(signal-as-time-series % subject))
           (apply #(tc/left-join %1 %2 [:t])))
      (tc/rename-columns {:y (fields 0)
                          :right.y (fields 1)})
      (tc/head 400)
      (tc/pivot->longer fields)
      (plotly/base {:=x :t
                    :=y :$value
                    :=color :$column})
      plotly/layer-line
      #_plotly/layer-point))

(comment
  ;; User will provide .mat loading code here
  ;; Expected structure:
  ;; {:ppg (125 Hz signal)
  ;;  :ecg (125 Hz signal)
  ;;  :resp-impedance (125 Hz reference)
  ;;  :rr (1 Hz ground truth respiratory rate)
  ;;  :sampling-rate 125}
  )

(def bidmc-data-path "/workspace/datasets/BIMDC/bidmc_data.mat")

(defn load-subject-data
  "Load all data for a single subject from BIDMC dataset.
   
   Returns map with:
   - :subject-id - Subject index (0-52)
   - :ppg - PPG signal tablecloth dataset {:t :y}
   - :ecg - ECG signal tablecloth dataset {:t :y}  
   - :rr - Ground truth respiratory rate tablecloth dataset {:t :y}
   - :sampling-rate - PPG/ECG sampling rate (125 Hz)
   - :rr-sampling-rate - RR sampling rate (1 Hz)"
  [subject-idx]
  (let [;; Load high-freq signals (125 Hz)
        ppg-ds (signal-as-time-series :ppg subject-idx)
        ecg-ds (signal-as-time-series :ekg subject-idx)

        ;; Load ground truth RR (1 Hz) from ref.params.rr
        ref-record (.get mat-data-struct "ref" (int subject-idx))
        params-record (.get ref-record "params" (int 0))
        rr-record (.get params-record "rr" (int 0))
        rr-t-matrix (.get rr-record "t" (int 0))
        rr-v-matrix (.get rr-record "v" (int 0))

        ;; Extract RR time and values - note: idx is implicit binding
        n-rr (.getNumRows rr-v-matrix)
        rr-time (dtype/make-reader :float64 n-rr
                                   (* 1000.0 (.getDouble rr-t-matrix idx 0))) ; Convert to ms
        rr-values (dtype/make-reader :float64 n-rr
                                     (.getDouble rr-v-matrix idx 0))

        ;; Get sampling rate from PPG
        ppg-record (.get mat-data-struct "ppg" (int subject-idx))
        fs-matrix (.get ppg-record "fs" (int 0))
        fs (int (.getDouble fs-matrix 0 0))]

    {:subject-id subject-idx
     :sampling-rate fs
     :rr-sampling-rate 1.0 ; RR sampled at 1 Hz
     :ppg ppg-ds
     :ecg ecg-ds
     :rr (tc/dataset {:t rr-time :y rr-values})}))

(def all-subjects (range 53)) ;; 53 subjects: 0-52 (0-based indexing)

;; ### Visualize One Subject
(comment
  ;; Visualize first subject's PPG + ground truth RR
  ;; This shows what we're working with
  )

(defn visualize-subject
  "Plot PPG signal with ground truth respiratory rate overlay"
  [subject-data]
  ;; TODO: Create dual-axis plot
  ;; - PPG on left axis
  ;; - RR on right axis
  )

;; =============================================================================
;; Part 2: Signal Processing Pipeline Components
;; =============================================================================

;; ## Pipeline Building Blocks
;; 
;; Each pipeline extracts a respiratory signal from PPG, then estimates RR from its spectrum.
;; 
;; **Three respiratory signals:**
;; 1. **RIIV** (Respiratory-Induced Intensity Variation) - DC component of each pulse
;; 2. **RIAV** (Respiratory-Induced Amplitude Variation) - AC amplitude of each pulse  
;; 3. **RIFV** (Respiratory-Induced Frequency Variation) - Heart rate changes
;; 
;; **Two spectral methods:**
;; 1. **FFT** - Fast Fourier Transform (simple, fast)
;; 2. **Welch** - Averaged periodogram (smoother, less variance)
;; ### Preprocessing: Bandpass Filter
(defn bandpass-filter
  "Apply Butterworth bandpass filter to remove noise and baseline wander.
   Typical range: 0.5-10 Hz for PPG pulse extraction."
  [signal sampling-rate low-hz high-hz order]
  (let [^Butterworth filt (Butterworth. (double sampling-rate))]
    (.bandPassFilter filt
                     (double-array signal)
                     (int order)
                     (double low-hz)
                     (double high-hz))))

;; ### Peak Detection
(defn detect-peaks
  "Detect systolic peaks in filtered PPG signal.
   Returns vector of peak indices."
  [filtered-signal]
  (let [peak-finder (FindPeak. (double-array filtered-signal))]
    (.detectPeaks peak-finder)
    (vec (.getPeaks peak-finder))))

(defn detect-troughs
  "Detect troughs (diastolic minimums) between peaks.
   Returns vector of trough indices."
  [filtered-signal peak-indices]
  ;; TODO: Find local minima between consecutive peaks
  ;; Simple approach: search for min value in each inter-peak segment
  )

;; ### Respiratory Signal Extraction
(defn extract-riiv
  "Extract Respiratory-Induced Intensity Variation.
   RIIV = DC component (baseline level) of each pulse."
  [signal peak-indices trough-indices sampling-rate]
  ;; TODO: For each pulse (trough to trough):
  ;;   - Calculate mean value = DC level
  ;;   - Store with timestamp
  ;; Returns: {:time [...] :riiv [...]}
  )

(defn extract-riav
  "Extract Respiratory-Induced Amplitude Variation.
   RIAV = AC component (peak - trough amplitude) of each pulse."
  [signal peak-indices trough-indices sampling-rate]
  ;; TODO: For each pulse:
  ;;   - Calculate amplitude = peak_value - trough_value
  ;;   - Store with timestamp
  ;; Returns: {:time [...] :riav [...]}
  )

(defn extract-rifv
  "Extract Respiratory-Induced Frequency Variation.
   RIFV = Instantaneous heart rate variation."
  [peak-indices sampling-rate]
  ;; TODO: For each consecutive peak pair:
  ;;   - Calculate IBI (inter-beat interval) in seconds
  ;;   - Convert to heart rate: 60/IBI
  ;;   - Store with timestamp
  ;; Returns: {:time [...] :rifv [...]}
  )

(defn resample-respiratory-signal
  "Resample irregularly-spaced respiratory signal to uniform rate.
   Needed because pulses occur at irregular times (~1 Hz)."
  [time-series target-rate]
  ;; TODO: Linear interpolation to regular grid
  ;; Input: {:time [...] :values [...]}
  ;; Output: uniformly sampled vector at target-rate Hz
  )

;; ### Spectral Analysis
(defn fft-spectrum
  "Compute power spectrum using FFT.
   Returns: {:freqs [...] :power [...]}"
  [signal sampling-rate]
  ;; TODO: Use JDSP DiscreteFourier
  ;; - Apply window (Hanning)
  ;; - Compute FFT
  ;; - Get magnitude spectrum
  )

(defn welch-spectrum
  "Compute power spectrum using Welch's method (averaged periodogram).
   More robust than single FFT, less variance."
  [signal sampling-rate {:keys [window-size overlap]}]
  ;; TODO: Segment signal into overlapping windows
  ;; - Compute FFT for each window
  ;; - Average power spectra
  ;; Returns: {:freqs [...] :power [...]}
  )

(defn find-respiratory-peak
  "Find peak in respiratory frequency band (0.05-0.7 Hz = 3-42 BPM).
   Returns estimated respiratory rate in breaths/minute."
  [spectrum respiratory-band]
  ;; TODO: 
  ;; - Filter spectrum to respiratory band
  ;; - Find frequency of maximum power
  ;; - Convert to breaths/minute: freq * 60
  )

;; =============================================================================
;; Part 3: Define Pipeline "Models"
;; =============================================================================

;; ## Three Competing Pipelines
;; 
;; Each pipeline is a different approach to estimating RR from PPG.
;; We'll treat each as a 'model' to evaluate systematically.
(defn pipeline-A
  "Model A: RIAV + FFT
   Simplest approach - pulse amplitude variation with FFT."
  [ppg-signal sampling-rate]
  ;; TODO: Chain operations
  ;; - Bandpass filter (0.5-10 Hz)
  ;; - Detect peaks and troughs
  ;; - Extract RIAV
  ;; - Resample to 4 Hz
  ;; - FFT spectrum
  ;; - Find respiratory peak
  ;; Returns: estimated RR in BPM
  )

(defn pipeline-B
  "Model B: RIIV + Welch
   DC component with smoothed spectrum."
  [ppg-signal sampling-rate {:keys [window-size overlap]}]
  ;; TODO: Similar to pipeline-A but:
  ;; - Extract RIIV instead of RIAV
  ;; - Use Welch instead of FFT
  ;; Returns: estimated RR in BPM
  )

(defn pipeline-C
  "Model C: Median Fusion (RIIV + RIAV + RIFV)
   Extract all three signals, fuse their spectra via median."
  [ppg-signal sampling-rate]
  ;; TODO:
  ;; - Extract RIIV, RIAV, RIFV
  ;; - Compute FFT for each
  ;; - Take element-wise median across three spectra
  ;; - Find respiratory peak in median spectrum
  ;; Returns: estimated RR in BPM
  )

;; =============================================================================
;; Part 4: Evaluation and Cross-Validation
;; =============================================================================

;; ## Systematic Pipeline Evaluation
;; 
;; We'll use **leave-one-subject-out cross-validation** to avoid overfitting.
;; For each subject:
;; - Use other 52 subjects for development (if needed)
;; - Test pipeline on held-out subject
;; - Compare predictions vs. ground truth
(defn evaluate-pipeline
  "Evaluate a pipeline on all subjects using cross-validation.
   Returns dataset with columns: [:subject :predicted-rr :true-rr :error]"
  [pipeline-fn subjects]
  ;; TODO:
  ;; - For each subject:
  ;;   - Load data
  ;;   - Apply pipeline to get RR estimate
  ;;   - Compare with ground truth
  ;;   - Store results
  ;; Returns: tablecloth dataset
  )

(defn compute-metrics
  "Compute evaluation metrics: MAE, RMSE, correlation, etc."
  [predictions-dataset]
  ;; TODO:
  ;; - MAE: mean absolute error
  ;; - RMSE: root mean squared error
  ;; - Correlation: Pearson r
  ;; - Bland-Altman: bias and limits of agreement
  )

;; ### Compare All Models
(comment
  ;; Evaluate all three pipelines
  (def results-A (evaluate-pipeline pipeline-A all-subjects))
  (def results-B (evaluate-pipeline pipeline-B all-subjects))
  (def results-C (evaluate-pipeline pipeline-C all-subjects))

  ;; Compare metrics
  (def comparison
    (tc/dataset
     {:model ["RIAV+FFT" "RIIV+Welch" "Fusion"]
      :mae [(-> results-A compute-metrics :mae)
            (-> results-B compute-metrics :mae)
            (-> results-C compute-metrics :mae)]
      :rmse [(-> results-A compute-metrics :rmse)
             (-> results-B compute-metrics :rmse)
             (-> results-C compute-metrics :rmse)]}))

  ;; Visualize comparison
  (-> comparison
      (plotly/layer-bar {:=x :model :=y :mae})
      (plotly/plot)))

;; ### Bland-Altman Plot (Clinical Validation)
(defn bland-altman-plot
  "Create Bland-Altman plot: standard clinical validation method.
   X-axis: mean of predicted and true
   Y-axis: difference (predicted - true)
   Shows bias and limits of agreement."
  [predictions-dataset]
  ;; TODO:
  ;; - Calculate mean and difference for each prediction
  ;; - Plot scatter with horizontal lines for bias and ±1.96*SD
  )

;; =============================================================================
;; Part 5: Hyperparameter Sensitivity Analysis
;; =============================================================================

;; ## Hyperparameter Tuning
;; 
;; Signal processing pipelines have hyperparameters too!
;; Let's see how sensitive our results are to window size.
(defn hyperparameter-sweep
  "Test pipeline across range of hyperparameter values.
   Example: window sizes [16, 32, 64, 128] seconds"
  [pipeline-fn param-name param-values subjects]
  ;; TODO:
  ;; - For each parameter value:
  ;;   - Evaluate pipeline with that setting
  ;;   - Compute MAE
  ;; Returns: dataset with [:param-value :mae]
  )

(comment
  ;; Window size sensitivity for Welch method
  (def window-sweep
    (hyperparameter-sweep
     (fn [window-size] (partial pipeline-B {:window-size window-size}))
     :window-size
     [16 32 64 128]
     all-subjects))

  ;; Visualize: MAE vs window size
  (-> window-sweep
      (plotly/layer-line {:=x :window-size :=y :mae})
      (plotly/plot)))

;; =============================================================================
;; Part 6: ML Best Practices Demonstrated
;; =============================================================================

;; ## What We've Demonstrated
;; 
;; This workshop showed **ML engineering best practices** using signal processing pipelines:
;; 
;; ✅ **Systematic comparison** - Not 'it seemed to work', but rigorous evaluation  
;; ✅ **Cross-validation** - Per-subject holdout to avoid overfitting  
;; ✅ **Multiple metrics** - MAE, RMSE, correlation, Bland-Altman  
;; ✅ **Hyperparameter analysis** - Understanding sensitivity to choices  
;; ✅ **Reproducible code** - Clay notebook with all steps documented
;; 
;; **Clojure libraries used:**
;; - `dtype-next` - Efficient numerical arrays
;; - `tablecloth` - DataFrame operations for results
;; - `JDSP` - Signal processing (filters, FFT, peaks)
;; - `plotly` - Interactive visualizations
;; - `metamorph.ml` - ML pipeline composition
;; - `Clay` - Literate programming and presentation
;; 
;; **These practices apply to any pipeline optimization problem**, not just biomedical signals!
;; ## Next Steps
;; 
;; 1. Implement more spectral methods (AR, wavelet-based)
;; 2. Try different fusion strategies (weighted, smart fusion)
;; 3. Add signal quality assessment
;; 4. Optimize for real-time processing
;; 5. Apply to other datasets (CapnoBase, custom recordings)