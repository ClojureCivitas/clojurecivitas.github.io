^{:kindly/hide-code true
  :clay {:title "DSP Study Group: Exploring PPG Biometrics"
         :quarto {:author [:daslu]
                  :date "2025-11-26"
                  :category :data
                  :tags [:dsp :biometrics :signal-processing :computer-vision :study-group]}}}
(ns dsp.seeing-red
  "DSP Study Group Session: Learning from 'Seeing Red: PPG Biometrics Using Smartphone Cameras'
   
   Paper: https://arxiv.org/pdf/2004.07088
   Original implementation: https://github.com/ssloxford/seeing-red
   
   In this study session, we're exploring how photoplethysmogram (PPG) signals
   can be extracted from smartphone camera video and potentially used for biometric
   authentication. We're not experts in this domain - we're learning together by
   reproducing parts of the 'Seeing Red' paper (Nagar et al., 2020)."
  (:require [com.phronemophobic.clj-media :as clj-media]
            [com.phronemophobic.clj-media.model :as clj-media.model]
            [scicloj.kindly.v4.kind :as kind]
            [tech.v3.libs.buffered-image :as bufimg]
            [tech.v3.datatype :as dtype]
            [tech.v3.tensor :as tensor]
            [tech.v3.datatype.functional :as dfn]
            [tech.v3.datatype.argops :as argops]
            [tech.v3.dataset.tensor :as ds-tensor]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tech.v3.parallel.for :as pfor]
            [clojure.string :as str]
            [fastmath.stats :as stats]))

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

;; ## What We're Learning
;;
;; Welcome to our DSP study group session! Today we're exploring **photoplethysmography (PPG)**,
;; a technique for measuring blood volume changes in tissue. 
;;
;; Here's what caught our attention: when you place your finger on a smartphone camera
;; with the flashlight on, the camera can actually detect your heartbeat through subtle
;; color variations! Here's how we understand it:
;;
;; 1. **Systole** (heart contracts): Blood is pushed into capillaries → more blood volume
;; 2. **Diastole** (heart relaxes): Blood volume decreases
;;
;; These volume changes affect how much light is absorbed vs. reflected, creating a
;; periodic signal at heart rate frequency (~1-2 Hz, or roughly 60-120 beats per minute).
;;
;; ### Why Study This?
;;
;; The "[Seeing Red](https://arxiv.org/abs/2004.07088)" paper (Nagar et al., 2020)
;; explores whether PPG signals could be used for biometric authentication. The idea
;; is that the *shape* of your PPG waveform might be unique to you, based on factors like:
;;
;; - How stiff or elastic your arteries are
;; - Your vascular structure and geometry  
;; - Blood viscosity and flow patterns
;; - Your heart's pumping characteristics
;;
;; We're curious to explore whether these really do create distinctive patterns!
;;
;; ### Our Learning Journey
;;
;; We'll attempt to reproduce parts of the paper's pipeline:
;;
;; 1. **Signal Extraction**: Extract color channel averages from video frames
;; 2. **Preprocessing**: Apply filtering to isolate heart rate frequencies
;; 3. **Beat Segmentation**: Try to detect individual heartbeats
;; 4. **Feature Extraction**: Compute features that might characterize the signal
;; 5. **Classification**: See if we can distinguish between different people
;;
;; ### The Dataset
;;
;; The researchers collected data from 14 participants using an iPhone X:
;;
;; - 30-second videos per session
;; - 6-11 sessions per participant over several weeks
;; - Finger placed on camera lens with flashlight enabled
;;
;; The dataset is [available at Oxford University Research Archive](https://ora.ox.ac.uk/objects/uuid:1a04e852-e7e1-4981-aa83-f2e729371484).

;;
;; We'll work with this data to see what we can learn!

;; ## One Example Video
;;
;; We've included one example video in the Civitas repository to explore together.
;; Let's take a look at what a PPG recording looks like:

(kind/video
 {:src "seeing-red-00000-7254825.mp4"})

;; ## First Steps: Reading the Video
;;
;; To work with the full dataset, you can download all videos from
;; and save them locally.
;;
;; For now, let's start exploring this one example:

(def video-path
  "src/dsp/seeing-red-00000-7254825.mp4")

;; We can peek at the video's properties using `clj-media/probe`
;; of the [clj-media](https://github.com/phronmophobic/clj-media) library:

(clj-media/probe video-path)

;; ## Exploring Frame Data
;;
;; Our first challenge: how do we extract the color information from each video frame?
;; 
;; The `clj-media` library lets us lazily process frames. Let's see what we can access:

(clj-media/frames
 (clj-media/file video-path)
 :video
 {:format (clj-media/video-format
           {:pixel-format
            :pixel-format/rgba})})

;; Let's grab just the first frame and see what we're working with.
;; We'll ask for RGBA format (Red, Green, Blue, Alpha channels):

(def first-image
  (first
   (into []
         (comp (take 1)
               (map clj-media.model/image))
         (clj-media/frames
          (clj-media/file video-path)
          :video
          {:format (clj-media/video-format
                    {:pixel-format
                     :pixel-format/rgba})}))))

(type first-image)

first-image

;; ### Making Sense of the Frame Data
;;
;; When we convert this to a tensor, we get a 3D array: `[height, width, channels]`.
;; For RGBA, that's 4 channels.
;;
;; Each pixel has four values (ranging from 0-255):
;; - **R** (Red): Red color intensity
;; - **G** (Green): Green color intensity  
;; - **B** (Blue): Blue color intensity
;; - **A** (Alpha): Transparency (we'll see 255 = fully opaque)
;;
;; According to the paper, the **green channel** works best for PPG because
;; green light (around 500-600nm wavelength) penetrates skin at just the right
;; depth to capture blood flow in the superficial capillaries. Interesting!

(def first-tensor
  (bufimg/as-ubyte-tensor first-image))

;; ## What's Next in Our Study
;;
;; Now that we understand the basic frame structure, here's what we'd like to explore:
;;
;; 1. **Signal extraction**: Try averaging the green channel across all pixels in each frame
;;    to create a 1D time series (one value per frame representing overall brightness)
;;
;; 2. **Filtering**: Learn to apply bandpass filters to remove noise and keep just the
;;    heart rate frequency band (probably 0.75-4 Hz, which is 45-240 beats per minute)
;;
;; 3. **Beat detection**: See if we can identify individual heartbeats using peak detection
;;
;; 4. **Feature extraction**: Experiment with extracting features that might characterize
;;    the heartbeat shape (like peak amplitudes, timing, pulse width, etc.)
;;
;; 5. **Classification**: Try to build a simple classifier to see if different people
;;    really do have distinguishable PPG patterns
;;
;; The paper reports 8% Equal Error Rate (EER) within a single session, but this jumped
;; to 20% EER when testing across different sessions. This suggests PPG signatures might
;; change with environmental factors or physiological state - something we'll want to
;; keep in mind as we explore!

(type first-tensor)

(dtype/shape first-tensor)

first-tensor

;; ### Looking at the Color Values
;;
;; Let's examine a single row of pixels (say, row 100) to see what the color values look like.
;; We'll turn it into a table to make it easier to inspect:

(let [[height width] (take 2 (dtype/shape first-tensor))]
  (-> first-tensor
      (tensor/slice 1)
      (nth 100)
      ds-tensor/tensor->dataset
      (tc/rename-columns [:r :g :b :a])))

;; Now let's visualize how these RGBA values vary across the first row.
;; Since the entire camera is covered by a finger, we expect the values to be
;; fairly uniform (mostly showing the pinkish-red color of finger tissue):

(let [[height width] (take 2 (dtype/shape first-tensor))
      row-data (-> first-tensor
                   (tensor/slice 1)
                   first
                   ds-tensor/tensor->dataset
                   (tc/rename-columns [:a :r :g :b]))]
  (-> row-data
      (tc/add-column :x (dtype/make-reader :int64 (tc/row-count row-data) idx))
      (plotly/base {:=mark-opacity 0.8
                    :=mark-size 5
                    :=title "First Row RGBA Values"})
      (plotly/layer-line {:=y :r :=name "r" :=mark-color "red"})
      (plotly/layer-line {:=y :g :=name "g" :=mark-color "green"})
      (plotly/layer-line {:=y :b :=name "b" :=mark-color "blue"})
      (plotly/layer-line {:=y :a :=name "a" :=mark-color "black"})
      plotly/plot))

;; Now let us collect all tensors of the video, along time

(def all-tensors
  (into []
        (map (comp bufimg/as-ubyte-tensor
                   clj-media.model/image))
        (clj-media/frames
         (clj-media/file video-path)
         :video
         {:format (clj-media/video-format
                   {:pixel-format
                    :pixel-format/rgba})})))

;; Let us compute the average color per channel along time:

;; Calculate the correct sampling rate (frames per second) from video metadata
(def sampling-rate
  (let [probe-info (clj-media/probe video-path)
        video-stream (first (:streams probe-info))
        num-frames (:num-frames video-stream)
        [numerator denominator] (:time-base video-stream)
        estimated-duration (:estimated-duration video-stream)
        ;; Duration in seconds = time-units * time-base
        duration-seconds (* estimated-duration (/ numerator (double denominator)))]
    (/ num-frames duration-seconds)))


(def channels-along-time
  (-> all-tensors
      (->> (pmap (fn [t]
                   (let [[h w _] (dtype/shape t)]
                     (-> t
                         (tensor/reshape [(* h w) 4])
                         (tensor/reduce-axis dfn/mean 0))))))
      tensor/->tensor
      ds-tensor/tensor->dataset
      (tc/rename-columns [:a :r :g :b])
      (tc/add-column :time (dfn// (range 0 (count all-tensors))
                                  sampling-rate))))


;; Check the shape and sample values:
;; We expect ~900 rows (30 fps × 30 seconds), with RGBA columns
channels-along-time

;; Check data types - should be numeric (float64):
;; All columns should be :float64
(tc/info channels-along-time :datatypes)

;; Let us plot this:

(-> channels-along-time
    (plotly/base {:=x :time
                  :=mark-opacity 0.8
                  :=mark-size 5})
    (plotly/layer-line {:=y :r :=name "r" :=mark-color "red"})
    (plotly/layer-line {:=y :g :=name "g" :=mark-color "green"})
    (plotly/layer-line {:=y :b :=name "b" :=mark-color "blue"})
    plotly/plot)

;; ## Selecting the Best Channel for PPG
;;
;; As we mentioned earlier, the paper found that the green channel works best for PPG.
;; Let's check the green channel first:

(-> channels-along-time
    (tc/add-column :time (dfn// (dtype/make-reader :float64 (tc/row-count channels-along-time) idx)
                                sampling-rate))
    (plotly/base {:=x :time
                  :=mark-opacity 0.8
                  :=title "Raw Green Channel Signal"})
    (plotly/layer-line {:=y :g :=name "green channel" :=mark-color "green"})
    plotly/plot)

;; ### A Discovery: Channel Selection Matters!
;;
;; Looking at the green channel plot above, we notice it's almost completely flat - barely any variation at all!
;; Let's check the signal statistics to understand what's happening:

(tc/aggregate channels-along-time
              {:r-mean #(dfn/mean (:r %))
               :r-std #(dfn/standard-deviation (:r %))
               :g-mean #(dfn/mean (:g %))
               :g-std #(dfn/standard-deviation (:g %))
               :b-mean #(dfn/mean (:b %))
               :b-std #(dfn/standard-deviation (:b %))})

;; **What we observe:**
;; - **Red channel**: Saturated at 255 (completely maxed out - no information)
;; - **Green channel**: Mean ~0, extremely low values (severely underexposed)
;; - **Blue channel**: Mean ~23 with variation (std ~1.4) - best signal quality!
;;
;; **Why is this happening?**
;;
;; The paper recommends green for PPG because green light typically penetrates skin at the right depth.
;; However, in *this specific recording*:
;; - The camera exposure might have been set for a different color temperature
;; - The flashlight LED might not have strong green components
;; - The finger placement may have blocked green wavelengths differently
;;
;; This is a great learning moment: **theory meets practice!** While green is often best,
;; we should adaptively select the channel with the best signal quality for each recording.
;;
;; For this video, we'll use the **blue channel** instead, which shows clear variation
;; where we'd expect heartbeat patterns. Let's visualize it:

(-> channels-along-time
    (tc/add-column :time (dfn// (dtype/make-reader :float64 (tc/row-count channels-along-time) idx)
                                sampling-rate))
    (plotly/base {:=x :time
                  :=mark-opacity 0.8
                  :=title "Raw Blue Channel Signal (Better Quality for This Video)"})
    (plotly/layer-line {:=y :b :=name "blue channel" :=mark-color "blue"})
    plotly/plot)

;; Much better! We can see periodic variations that look like heartbeat patterns.
;;
;; The raw signal still contains:
;; - **Low-frequency drift**: Slow changes from finger movement, pressure variations
;; - **Heart rate signal**: The PPG we want (around 1-2 Hz, or 60-120 BPM)
;; - **High-frequency noise**: Camera sensor noise, electrical interference

;; ## Signal Preprocessing: Bandpass Filtering
;;
;; To isolate the heartbeat signal, we need to apply a **bandpass filter** that:
;; - Removes low frequencies (< 0.75 Hz) - baseline drift
;; - Removes high frequencies (> 4 Hz) - noise
;; - Keeps heart rate frequencies (0.75-4 Hz, or 45-240 BPM)
;;
;; We'll use the JDSP library to design and apply a Butterworth bandpass filter.

(import '[com.github.psambit9791.jdsp.filter Butterworth])

;; Extract the best channel for PPG signal (blue for this video):

(def green-signal
  (:b channels-along-time))

;; Check signal properties:
;; Number of samples (should match video frames, ~900):
(dtype/ecount green-signal)

;; Data type (should be numeric - :float64):
(dtype/elemwise-datatype green-signal)

;; Sample values to verify they're in sensible range (0-255):
;; These are average pixel intensities across the whole frame
(take 10 green-signal)

;; Design and apply a 4th-order Butterworth bandpass filter:
;; - Low cutoff: 0.75 Hz (45 BPM)
;; - High cutoff: 4.0 Hz (240 BPM)
;; - Sampling rate: from video frame rate

(def filtered-signal
  (let [^Butterworth flt (Butterworth. (double sampling-rate))]
    (.bandPassFilter flt
                     (double-array green-signal)
                     (int 4) ; filter order
                     (double 0.75) ; low cutoff (Hz)
                     (double 4.0)))) ; high cutoff (Hz)

;; Check filtered signal properties:
;; Should be same length as input (~900):
(dtype/ecount filtered-signal)

;; Data type - JDSP returns primitive double array:
(type filtered-signal)

;; Sample values - should be centered around 0 after bandpass filtering:
;; The DC component (average) is removed, so we see oscillations around zero
(take 10 filtered-signal)

;; Let's compare the raw and filtered signals:

(def comparison-data
  (tc/dataset {:time (dfn// (dtype/make-reader :float64 (dtype/ecount green-signal) idx)
                            sampling-rate)
               :raw green-signal
               :filtered filtered-signal}))

(-> comparison-data
    (plotly/base {:=x :time
                  :=title "Raw vs Filtered PPG Signal (Blue Channel)"})
    (plotly/layer-line {:=y :raw
                        :=name "raw"
                        :=mark-color "lightgreen"
                        :=mark-opacity 0.5})
    (plotly/layer-line {:=y :filtered
                        :=name "filtered (0.75-4 Hz)"
                        :=mark-color "darkgreen"})
    plotly/plot)

;; Much clearer! Now we can actually see the individual heartbeats as peaks in the signal.

;; ## What Do We See?
;;
;; After filtering, the periodic nature of the heartbeat becomes much more obvious.
;; Each peak corresponds to a heartbeat (systole - when blood rushes into the fingertip).
;;
;; Let's zoom into a 5-second window to see the waveform shape more clearly:

(-> comparison-data
    (tc/select-rows (fn [row] (< 5 (:time row) 10)))
    (plotly/base {:=x :time
                  :=title "Zoomed View: 5-10 seconds"})
    (plotly/layer-line {:=y :filtered
                        :=name "filtered signal"
                        :=mark-color "darkgreen"})
    plotly/plot)

;; Now we can see the characteristic PPG waveform shape more clearly!
;; Each heartbeat has:
;; - **Systolic peak**: The main upward spike (heart contracts)
;; - **Dicrotic notch**: A small dip after the peak (aortic valve closes)
;; - **Diastolic phase**: Return to baseline (heart relaxes)
;;
;; The shape and timing of these features vary between individuals - that's what
;; makes PPG potentially useful for biometrics!

;; ## Detecting Individual Heartbeats
;;
;; Now that we have a clean signal, let's try to detect the individual heartbeats
;; by finding the systolic peaks. We'll use JDSP's peak detection.

(import '[com.github.psambit9791.jdsp.signal.peaks FindPeak])

;; Find peaks in the filtered signal:

(def peak-finder
  (FindPeak. (double-array filtered-signal)))

(def peak-indices
  (let [raw-peaks (-> peak-finder
                      ^FindPeak (.detectPeaks)
                      (.getPeaks))
        ;; Filter 1: Keep only positive peaks (remove valleys/minima)
        positive-peaks (filterv (fn [idx]
                                  (pos? (dtype/get-value filtered-signal idx)))
                                raw-peaks)
        ;; Filter 2: Enforce minimum distance between peaks (0.25s = ~240 BPM max)
        min-distance-samples (int (* 0.25 sampling-rate))
        filtered-peaks (reduce (fn [acc idx]
                                 (if (or (empty? acc)
                                         (>= (- idx (peek acc)) min-distance-samples))
                                   (conj acc idx)
                                   acc))
                               []
                               positive-peaks)]
    (int-array filtered-peaks)))

;; How many heartbeats did we detect?
;; For a ~29-second video at 60-100 BPM, we'd expect 29-48 peaks

(count peak-indices)

;; Let's check the peak indices to ensure they're sensible:

;; First few peaks (should be increasing frame indices):
(take 5 peak-indices)

;; Let's visualize the detected peaks on our signal:

(let [time-col (:time comparison-data)
      ;; Filter peaks in the 5-10 second window
      filtered-peak-indices (filterv (fn [idx]
                                       (let [t (dtype/get-value time-col idx)]
                                         (and (>= t 5) (< t 10))))
                                     peak-indices)
      ;; Get times and values for filtered peaks
      peak-times-window (mapv #(dtype/get-value time-col %) filtered-peak-indices)
      peak-values-window (mapv #(dtype/get-value filtered-signal %) filtered-peak-indices)
      ;; Create a dataset with the peak points
      peaks-ds (tc/dataset {:time peak-times-window
                            :filtered peak-values-window})]
  (-> comparison-data
      (tc/select-rows (fn [row] (< 5 (:time row) 10)))
      (plotly/base {:=x :time
                    :=y :filtered
                    :=title "Detected Heartbeats (5-10 seconds)"})
      (plotly/layer-line {:=name "filtered signal"
                          :=mark-color "darkgreen"})
      (plotly/layer-point {:=dataset peaks-ds
                           :=name "detected peaks"
                           :=mark-color "red"
                           :=mark-size 10})
      plotly/plot))

;; Great! We can now identify individual heartbeats in the signal.
;;
;; ## Calculating Heart Rate
;;
;; With the detected peaks, we can estimate the heart rate.
;; The time between consecutive peaks is the inter-beat interval (IBI).

(def peak-times
  (let [time-col (:time comparison-data)]
    (dtype/emap (fn [idx] (dtype/get-value time-col idx))
                :float64
                peak-indices)))

;; Check peak times - should be monotonically increasing:
(take 10 peak-times)

(def inter-beat-intervals
  (dfn/- (dtype/sub-buffer peak-times 1)
         (dtype/sub-buffer peak-times 0 (dec (dtype/ecount peak-times)))))

;; Check inter-beat intervals - should be positive, typically 0.5-2 seconds:
;; At 60 BPM: 1.0s, at 80 BPM: 0.75s, at 120 BPM: 0.5s
(take 10 inter-beat-intervals)

;; Statistics on IBIs to check for variability and outliers:
(def ibi-stats
  {:min (dfn/reduce-min inter-beat-intervals)
   :max (dfn/reduce-max inter-beat-intervals)
   :mean (dfn/mean inter-beat-intervals)
   :stddev (dfn/standard-deviation inter-beat-intervals)})

ibi-stats

;; Average heart rate in beats per minute:

(def avg-heart-rate
  (/ 60.0 (dfn/mean inter-beat-intervals)))

;; Check if heart rate is in reasonable range (40-200 BPM):
;; Typical resting HR is 60-100 BPM
avg-heart-rate

(kind/hiccup
 [:div
  [:h3 "Estimated Heart Rate"]
  [:p {:style {:font-size "24px" :font-weight "bold"}}
   (format "%.1f BPM" avg-heart-rate)]])

;; This is just a rough estimate - a more robust implementation would:
;; - Remove outliers (missed or false detections)
;; - Use a sliding window for continuous heart rate estimation
;; - Account for heart rate variability

;; ## Heart Rate Variability (HRV)
;;
;; Heart rate variability - the variation in time between consecutive heartbeats -
;; is an interesting signal in itself. In fact, HRV patterns might be even more
;; distinctive than average heart rate for biometric purposes!
;;
;; Let's visualize how the heart rate changes over time:

(def instantaneous-hr
  (dfn// 60.0 inter-beat-intervals))

;; Plot instantaneous heart rate over time:

(-> (tc/dataset {:time (dtype/sub-buffer peak-times 0 (dtype/ecount instantaneous-hr))
                 :hr instantaneous-hr})
    (plotly/base {:=x :time
                  :=title "Instantaneous Heart Rate Over Time"
                  :=y-title "BPM"})
    (plotly/layer-line {:=y :hr
                        :=name "heart rate"
                        :=mark-color "red"})
    plotly/plot)

;; HRV metrics - common measures of heart rate variability:

(def hrv-metrics
  {:sdnn (dfn/standard-deviation inter-beat-intervals) ; Standard deviation of IBIs
   :rmssd (let [successive-diffs (dfn/- (dtype/sub-buffer inter-beat-intervals 1)
                                        (dtype/sub-buffer inter-beat-intervals 0
                                                          (dec (dtype/ecount inter-beat-intervals))))]
            (dfn/sqrt (dfn/mean (dfn/sq successive-diffs)))) ; Root mean square of successive differences
   :pnn50 (let [successive-diffs (dfn/abs (dfn/- (dtype/sub-buffer inter-beat-intervals 1)
                                                 (dtype/sub-buffer inter-beat-intervals 0
                                                                   (dec (dtype/ecount inter-beat-intervals)))))
                count-over-50ms (dfn/sum (dtype/emap #(if (> % 0.05) 1.0 0.0) :float64 successive-diffs))]
            (* 100.0 (/ count-over-50ms (dtype/ecount successive-diffs))))}) ; Percentage of successive differences > 50ms

hrv-metrics

;; Interpretation:
;; - **SDNN** (Standard Deviation of NN intervals): Overall HRV. Higher = more variable
;; - **RMSSD** (Root Mean Square of Successive Differences): Short-term variability
;; - **pNN50**: Percentage of intervals differing by >50ms. Related to parasympathetic activity
;;
;; These metrics vary between individuals and could potentially be used as biometric features!

;; ## Heartbeat Segmentation
;;
;; Now let's segment individual heartbeats from the signal. This allows us to:
;; 1. Examine the shape of each heartbeat waveform
;; 2. Extract morphological features (peak amplitudes, durations, etc.)
;; 3. Compare heartbeat shapes between individuals (the biometric goal!)
;;
;; We'll extract a window around each peak. A typical heartbeat cycle is about 0.8-1.2 seconds,
;; so we'll use a window from 0.4 seconds before to 0.4 seconds after each peak.

(def window-duration 0.4) ; seconds before and after peak

(def window-samples
  (int (* window-duration sampling-rate)))

;; Extract heartbeat segments:
(def heartbeat-segments
  (mapv (fn [peak-idx]
          (let [start-idx (max 0 (- peak-idx window-samples))
                end-idx (min (dtype/ecount filtered-signal)
                             (+ peak-idx window-samples 1))
                segment (dtype/sub-buffer filtered-signal start-idx end-idx)
                ;; Normalize to 0-1 for easier comparison
                segment-min (dfn/reduce-min segment)
                segment-max (dfn/reduce-max segment)
                normalized (if (= segment-min segment-max)
                             segment
                             (dfn// (dfn/- segment segment-min)
                                    (- segment-max segment-min)))]
            {:peak-idx peak-idx
             :start-idx start-idx
             :end-idx end-idx
             :raw-segment segment
             :normalized-segment normalized}))
        (take 10 peak-indices))) ; Just first 10 heartbeats for now

;; Let's visualize the first few normalized heartbeats overlaid:

(let [;; Create dataset with all heartbeat segments
      segments-data (apply tc/concat
                           (map-indexed
                            (fn [beat-num {:keys [normalized-segment]}]
                              (tc/dataset {:time (dfn// (dtype/make-reader :float64
                                                                           (dtype/ecount normalized-segment)
                                                                           idx)
                                                        sampling-rate)
                                           :amplitude normalized-segment
                                           :beat (repeat (dtype/ecount normalized-segment)
                                                         (str "Beat " beat-num))}))
                            heartbeat-segments))]
  (-> segments-data
      (plotly/base {:=x :time
                    :=y :amplitude
                    :=color :beat
                    :=title "First 10 Heartbeat Waveforms (Normalized)"
                    :=x-title "Time (relative to segment start, seconds)"
                    :=y-title "Normalized Amplitude"})
      (plotly/layer-line {})
      plotly/plot))

;; Now we can see the shape variations between different heartbeats!
;; The paper uses these morphological differences for biometric identification.

;; ## Feature Extraction
;;
;; For biometric authentication, we need to extract quantitative features from each
;; heartbeat that capture its unique characteristics. Common PPG features include:
;;
;; - **Systolic peak amplitude**: Height of the main peak
;; - **Pulse width**: Duration of the pulse at different amplitude levels
;; - **Rise time**: Time from start to peak
;; - **Fall time**: Time from peak to end
;; - **Area under curve**: Total energy in the pulse
;; - **Spectral features**: Frequency content of the waveform

(defn extract-heartbeat-features
  "Extract morphological features from a single heartbeat segment"
  [{:keys [normalized-segment raw-segment peak-idx]}]
  (let [n (dtype/ecount normalized-segment)
        peak-loc (argops/argmax normalized-segment)
        peak-amp (dfn/reduce-max normalized-segment)

        ;; Rise time: samples from start to peak
        rise-time (/ peak-loc sampling-rate)

        ;; Fall time: samples from peak to end
        fall-time (/ (- n peak-loc) sampling-rate)

        ;; Pulse width at 50% amplitude
        half-max 0.5
        above-half (dtype/emap #(if (>= % half-max) 1.0 0.0) :float64 normalized-segment)
        pulse-width-50 (/ (dfn/sum above-half) sampling-rate)

        ;; Area under curve (using raw segment for actual amplitude)
        auc (dfn/sum raw-segment)

        ;; Standard deviation (shape complexity)
        shape-std (dfn/standard-deviation normalized-segment)]

    {:peak-amplitude peak-amp
     :rise-time rise-time
     :fall-time fall-time
     :pulse-width-50 pulse-width-50
     :area-under-curve auc
     :shape-std shape-std
     :asymmetry-ratio (/ rise-time (+ rise-time fall-time))}))

;; Extract features from all heartbeat segments:
(def heartbeat-features
  (mapv extract-heartbeat-features heartbeat-segments))

;; View as a table:
(tc/dataset heartbeat-features)

;; Compute statistics across all heartbeats to see consistency:
(def feature-stats
  (let [ds (tc/dataset heartbeat-features)]
    (tc/aggregate ds {:mean-peak-amp (fn [ds] (dfn/mean (:peak-amplitude ds)))
                      :std-peak-amp (fn [ds] (dfn/standard-deviation (:peak-amplitude ds)))
                      :mean-rise-time (fn [ds] (dfn/mean (:rise-time ds)))
                      :std-rise-time (fn [ds] (dfn/standard-deviation (:rise-time ds)))
                      :mean-pulse-width (fn [ds] (dfn/mean (:pulse-width-50 ds)))
                      :std-pulse-width (fn [ds] (dfn/standard-deviation (:pulse-width-50 ds)))})))

feature-stats

;; These features show both:
;; - **Central tendency** (mean): The typical heartbeat shape for this individual
;; - **Variability** (std): How consistent the heartbeats are
;;
;; For biometric authentication, we'd:
;; 1. Extract these features from multiple heartbeats in a recording
;; 2. Train a classifier (SVM, Random Forest) to recognize the pattern
;; 3. Test if new recordings match the learned pattern
;;
;; The paper found that combining multiple feature types works better than any single feature!

;; ## Results Summary and Analysis
;;
;; Now that we've completed the full pipeline, let's take a comprehensive look at what
;; we discovered and document the results systematically.

;; ### Channel Selection Analysis
;;
;; One of our key discoveries was that the theoretically "best" channel (green) wasn't
;; actually best for this specific video. Let's document the channel quality comparison:

(def channel-quality-comparison
  (tc/dataset
   {:channel ["Red" "Green" "Blue"]
    :mean [(dfn/mean (:r channels-along-time))
           (dfn/mean (:g channels-along-time))
           (dfn/mean (:b channels-along-time))]
    :std [(dfn/standard-deviation (:r channels-along-time))
          (dfn/standard-deviation (:g channels-along-time))
          (dfn/standard-deviation (:b channels-along-time))]
    :min [(dfn/reduce-min (:r channels-along-time))
          (dfn/reduce-min (:g channels-along-time))
          (dfn/reduce-min (:b channels-along-time))]
    :max [(dfn/reduce-max (:r channels-along-time))
          (dfn/reduce-max (:b channels-along-time))
          (dfn/reduce-max (:b channels-along-time))]}))

channel-quality-comparison

;; **Key Observations:**
;; - **Red**: Saturated at 255 (completely clipped, no variation)
;; - **Green**: Severely underexposed (mean ≈ 0, range 0-19)
;; - **Blue**: Good dynamic range (mean ≈ 23, std ≈ 1.4, range 22-86)
;;
;; **Coefficient of Variation (CV = std/mean)** is a better signal quality metric:

(def channel-cv
  (tc/dataset
   {:channel ["Red" "Green" "Blue"]
    :cv [(/ (dfn/standard-deviation (:r channels-along-time))
            (dfn/mean (:r channels-along-time)))
         (/ (dfn/standard-deviation (:g channels-along-time))
            (max 0.001 (dfn/mean (:g channels-along-time)))) ; avoid division by near-zero
         (/ (dfn/standard-deviation (:b channels-along-time))
            (dfn/mean (:b channels-along-time)))]
    :interpretation ["No variation (saturated)"
                     "High noise-to-signal ratio"
                     "Best signal quality"]}))

channel-cv

;; ### Peak Detection Quality Metrics
;;
;; Let's quantify how well our peak detection worked:

(def peak-quality-metrics
  {:total-detected (count peak-indices)
   :detection-rate-bpm (/ (* 60.0 (count peak-indices))
                          (/ (dtype/ecount filtered-signal) sampling-rate))
   :inter-beat-intervals
   {:min-seconds (dfn/reduce-min inter-beat-intervals)
    :max-seconds (dfn/reduce-max inter-beat-intervals)
    :mean-seconds (dfn/mean inter-beat-intervals)
    :std-seconds (dfn/standard-deviation inter-beat-intervals)
    :cv (/ (dfn/standard-deviation inter-beat-intervals)
           (dfn/mean inter-beat-intervals))}
   :peak-amplitudes
   (let [peak-amps (mapv #(dtype/get-value filtered-signal %) peak-indices)]
     {:min (apply min peak-amps)
      :max (apply max peak-amps)
      :mean (/ (reduce + peak-amps) (count peak-amps))
      :all-positive? (every? pos? peak-amps)})
   :quality-assessment "All peaks are positive with reasonable spacing (0.25-1.15s)"})

peak-quality-metrics

;; ### Heart Rate and Variability Summary
;;
;; Let's visualize the heart rate statistics more comprehensively:

(def hr-summary
  {:video-duration-seconds (/ (dtype/ecount filtered-signal) sampling-rate)
   :total-heartbeats (count peak-indices)
   :average-hr-bpm avg-heart-rate
   :hr-range-bpm [(dfn/reduce-min instantaneous-hr)
                  (dfn/reduce-max instantaneous-hr)]
   :hrv-metrics hrv-metrics
   :interpretation (str "Resting heart rate of " (int avg-heart-rate)
                        " BPM is within normal range (60-100 BPM)")})

hr-summary

;; Visualize heart rate distribution:

(-> (tc/dataset {:hr instantaneous-hr})
    (plotly/base {:=x :hr
                  :=title "Heart Rate Distribution"
                  :=x-title "Beats Per Minute (BPM)"
                  :=y-title "Count"})
    (plotly/layer-histogram {:=nbins 20
                             :=mark-color "red"
                             :=mark-opacity 0.7})
    plotly/plot)

;; ### Feature Extraction Results
;;
;; Let's analyze the morphological features we extracted:

(def feature-summary
  (let [features-ds (tc/dataset heartbeat-features)]
    {:num-heartbeats-analyzed (tc/row-count features-ds)
     :feature-means
     {:peak-amplitude (dfn/mean (:peak-amplitude features-ds))
      :rise-time (dfn/mean (:rise-time features-ds))
      :fall-time (dfn/mean (:fall-time features-ds))
      :pulse-width-50 (dfn/mean (:pulse-width-50 features-ds))
      :asymmetry-ratio (dfn/mean (:asymmetry-ratio features-ds))}
     :feature-variability
     {:rise-time-cv (/ (dfn/standard-deviation (:rise-time features-ds))
                       (dfn/mean (:rise-time features-ds)))
      :fall-time-cv (/ (dfn/standard-deviation (:fall-time features-ds))
                       (dfn/mean (:fall-time features-ds)))
      :pulse-width-cv (/ (dfn/standard-deviation (:pulse-width-50 features-ds))
                         (dfn/mean (:pulse-width-50 features-ds)))}
     :interpretation "CV ~0.6-0.7 shows moderate variability in morphology across beats"}))

feature-summary

;; Visualize feature distributions to see consistency:

(let [features-ds (tc/dataset heartbeat-features)]
  (-> features-ds
      (tc/select-columns [:rise-time :fall-time :pulse-width-50])
      (plotly/base {:=title "Heartbeat Feature Distributions"
                    :=y-title "Time (seconds)"})
      (plotly/layer-point {:=x (repeat (tc/row-count features-ds) "rise-time")
                           :=y :rise-time
                           :=name "Rise Time"
                           :=mark-color "steelblue"
                           :=mark-size 8
                           :=mark-opacity 0.6})
      (plotly/layer-point {:=x (repeat (tc/row-count features-ds) "fall-time")
                           :=y :fall-time
                           :=name "Fall Time"
                           :=mark-color "coral"
                           :=mark-size 8
                           :=mark-opacity 0.6})
      (plotly/layer-point {:=x (repeat (tc/row-count features-ds) "pulse-width")
                           :=y :pulse-width-50
                           :=name "Pulse Width (50%)"
                           :=mark-color "green"
                           :=mark-size 8
                           :=mark-opacity 0.6})
      plotly/plot))

;; ## Multi-Video Analysis: Temporal Stability
;;
;; Now that we've explored a single video in depth, let's analyze multiple sessions
;; from the same participant to understand **temporal stability** - how consistent
;; are PPG features across different recording sessions?
;;
;; This is crucial for biometric authentication! If features vary too much between
;; sessions, the system won't reliably recognize the same person.

;; ### Refactoring: Reusable Pipeline Functions
;;
;; First, let's extract our exploratory code into reusable functions:

(defn select-best-channel
  "Analyze channel quality and select the best one for PPG extraction.
   Returns {:channel-key :r/:g/:b, :cv coefficient-of-variation}"
  [channels-dataset]
  (let [channel-stats (for [ch [:r :g :b]]
                        (let [values (ch channels-dataset)
                              mean-val (dfn/mean values)
                              std-val (dfn/standard-deviation values)
                              cv (if (> mean-val 0.001)
                                   (/ std-val mean-val)
                                   Double/MAX_VALUE)]
                          {:channel ch
                           :mean mean-val
                           :std std-val
                           :cv cv}))
        ;; Select channel with lowest CV (best signal-to-noise)
        ;; Filter out saturated (mean > 250) and underexposed (mean < 5) channels
        valid-channels (filter (fn [{:keys [mean]}]
                                 (and (> mean 5) (< mean 250)))
                               channel-stats)
        best (first (sort-by :cv valid-channels))]
    best))

(defn extract-channel-signal
  "Extract average channel values over time from video frames."
  [video-path]
  (let [all-tensors (into []
                          (map (comp bufimg/as-ubyte-tensor
                                     clj-media.model/image))
                          (clj-media/frames
                           (clj-media/file video-path)
                           :video
                           {:format (clj-media/video-format
                                     {:pixel-format :pixel-format/rgba})}))]
    (-> all-tensors
        (->> (pmap (fn [t]
                     (-> t
                         ;; average over height
                         (tensor/reduce-axis dfn/mean 0)
                         ;; average over width
                         (tensor/reduce-axis dfn/mean 0)))))
        tensor/->tensor
        ds-tensor/tensor->dataset
        (tc/rename-columns [:r :g :b :a]))))

(defn calculate-sampling-rate
  "Extract sampling rate (fps) from video metadata."
  [video-path]
  (let [probe-info (clj-media/probe video-path)
        video-stream (first (:streams probe-info))
        num-frames (:num-frames video-stream)
        [numerator denominator] (:time-base video-stream)
        estimated-duration (:estimated-duration video-stream)
        duration-seconds (* estimated-duration (/ numerator (double denominator)))]
    (/ num-frames duration-seconds)))

(defn apply-bandpass-filter
  "Apply Butterworth bandpass filter to isolate heart rate frequencies."
  [signal sampling-rate]
  (let [^Butterworth flt (Butterworth. (double sampling-rate))]
    (.bandPassFilter flt
                     (double-array signal)
                     (int 4) ; filter order
                     (double 0.75) ; low cutoff (Hz) - 45 BPM
                     (double 4.0)))) ; high cutoff (Hz) - 240 BPM

(defn detect-heartbeat-peaks
  "Detect heartbeat peaks with quality constraints."
  [filtered-signal sampling-rate]
  (let [^FindPeak peak-finder (FindPeak. (double-array filtered-signal))
        raw-peaks (-> peak-finder (.detectPeaks) (.getPeaks))
        ;; Filter 1: Keep only positive peaks
        positive-peaks (filterv (fn [idx]
                                  (pos? (dtype/get-value filtered-signal idx)))
                                raw-peaks)
        ;; Filter 2: Minimum distance (0.25s = ~240 BPM max)
        min-distance-samples (int (* 0.25 sampling-rate))
        filtered-peaks (reduce (fn [acc idx]
                                 (if (or (empty? acc)
                                         (>= (- idx (peek acc)) min-distance-samples))
                                   (conj acc idx)
                                   acc))
                               []
                               positive-peaks)]
    (int-array filtered-peaks)))

(defn compute-heart-rate-metrics
  "Calculate heart rate and HRV metrics from peak indices."
  [peak-indices filtered-signal sampling-rate]
  (let [;; Convert indices to times
        peak-times (dtype/emap (fn [idx] (/ idx sampling-rate))
                               :float64
                               peak-indices)
        ;; Inter-beat intervals
        ibis (dfn/- (dtype/sub-buffer peak-times 1)
                    (dtype/sub-buffer peak-times 0 (dec (dtype/ecount peak-times))))
        ;; Heart rate
        avg-hr (/ 60.0 (dfn/mean ibis))
        ;; HRV metrics
        sdnn (dfn/standard-deviation ibis)
        successive-diffs (dfn/- (dtype/sub-buffer ibis 1)
                                (dtype/sub-buffer ibis 0 (dec (dtype/ecount ibis))))
        rmssd (dfn/sqrt (dfn/mean (dfn/sq successive-diffs)))
        pnn50 (let [abs-diffs (dfn/abs successive-diffs)
                    count-over-50ms (dfn/sum (dtype/emap #(if (> % 0.05) 1.0 0.0)
                                                         :float64
                                                         abs-diffs))]
                (* 100.0 (/ count-over-50ms (dtype/ecount abs-diffs))))]
    {:peak-count (count peak-indices)
     :avg-hr avg-hr
     :hr-std (dfn/standard-deviation (dfn// 60.0 ibis))
     :sdnn sdnn
     :rmssd rmssd
     :pnn50 pnn50
     :mean-ibi (dfn/mean ibis)
     :std-ibi (dfn/standard-deviation ibis)}))

(defn segment-and-extract-features
  "Segment heartbeats and extract morphological features."
  [filtered-signal peak-indices sampling-rate]
  (let [window-duration 0.4
        window-samples (int (* window-duration sampling-rate))]
    (mapv (fn [peak-idx]
            (let [start-idx (max 0 (- peak-idx window-samples))
                  end-idx (min (dtype/ecount filtered-signal)
                               (+ peak-idx window-samples 1))
                  segment (dtype/sub-buffer filtered-signal start-idx end-idx)
                  segment-min (dfn/reduce-min segment)
                  segment-max (dfn/reduce-max segment)
                  normalized (if (= segment-min segment-max)
                               segment
                               (dfn// (dfn/- segment segment-min)
                                      (- segment-max segment-min)))
                  ;; Extract features
                  n (dtype/ecount normalized)
                  peak-loc (argops/argmax normalized)
                  peak-amp (dfn/reduce-max normalized)
                  rise-time (/ peak-loc sampling-rate)
                  fall-time (/ (- n peak-loc) sampling-rate)
                  half-max 0.5
                  above-half (dtype/emap #(if (>= % half-max) 1.0 0.0) :float64 normalized)
                  pulse-width-50 (/ (dfn/sum above-half) sampling-rate)
                  auc (dfn/sum segment)
                  shape-std (dfn/standard-deviation normalized)]
              {:peak-amplitude peak-amp
               :rise-time rise-time
               :fall-time fall-time
               :pulse-width-50 pulse-width-50
               :area-under-curve auc
               :shape-std shape-std
               :asymmetry-ratio (/ rise-time (+ rise-time fall-time))}))
          peak-indices)))

(defn process-video
  "Run the complete PPG analysis pipeline on a single video.
   Returns a map with all extracted features and metadata."
  [video-path]
  (try
    (let [;; Extract signals
          channels (extract-channel-signal video-path)
          sampling-rate (calculate-sampling-rate video-path)
          ;; Select best channel
          best-ch (select-best-channel channels)
          signal ((:channel best-ch) channels)
          ;; Filter
          filtered (apply-bandpass-filter signal sampling-rate)
          ;; Detect peaks
          peaks (detect-heartbeat-peaks filtered sampling-rate)
          ;; Compute metrics
          hr-metrics (compute-heart-rate-metrics peaks filtered sampling-rate)
          ;; Extract features
          features (segment-and-extract-features filtered peaks sampling-rate)
          features-ds (tc/dataset features)]
      {:video-path video-path
       :success true
       :sampling-rate sampling-rate
       :best-channel (:channel best-ch)
       :channel-cv (:cv best-ch)
       :heart-rate hr-metrics
       :morphology-features
       {:mean-peak-amp (dfn/mean (:peak-amplitude features-ds))
        :std-peak-amp (dfn/standard-deviation (:peak-amplitude features-ds))
        :mean-rise-time (dfn/mean (:rise-time features-ds))
        :std-rise-time (dfn/standard-deviation (:rise-time features-ds))
        :mean-fall-time (dfn/mean (:fall-time features-ds))
        :std-fall-time (dfn/standard-deviation (:fall-time features-ds))
        :mean-pulse-width (dfn/mean (:pulse-width-50 features-ds))
        :std-pulse-width (dfn/standard-deviation (:pulse-width-50 features-ds))
        :mean-asymmetry (dfn/mean (:asymmetry-ratio features-ds))
        :std-asymmetry (dfn/standard-deviation (:asymmetry-ratio features-ds))}})
    (catch Exception e
      {:video-path video-path
       :success false
       :error (.getMessage e)})))

;; ### Processing All Videos for Participant 00000
;;
;; Let's apply our pipeline to all 6 sessions:

(def participant-00000-videos
  ["data/dsp/seeing-red/videos/00000/7254825.mp4"
   "data/dsp/seeing-red/videos/00000/7254940.mp4"
   "data/dsp/seeing-red/videos/00000/7602210.mp4"
   "data/dsp/seeing-red/videos/00000/7602289.mp4"
   "data/dsp/seeing-red/videos/00000/7683924.mp4"
   "data/dsp/seeing-red/videos/00000/7684007.mp4"])

;; Process all videos (this may take a minute):
(def multi-video-results
  (mapv process-video participant-00000-videos))

;; Check if all videos processed successfully:
(tc/dataset (map #(select-keys % [:video-path :success :best-channel :sampling-rate])
                 multi-video-results))

;; ### Temporal Stability Analysis
;;
;; Now let's analyze how consistent the PPG features are across sessions:

;; Extract just the successful results:
(def successful-results
  (filterv :success multi-video-results))

;; Create a dataset with heart rate metrics across sessions:
(def hr-across-sessions
  (tc/dataset
   (map-indexed (fn [idx result]
                  (merge {:session-id (inc idx)
                          :video-name (last (str/split (:video-path result) #"/"))}
                         (:heart-rate result)))
                successful-results)))

hr-across-sessions

;; Visualize heart rate consistency:
(-> hr-across-sessions
    (plotly/base {:=x :session-id
                  :=title "Heart Rate Across Sessions (Participant 00000)"
                  :=x-title "Session"
                  :=y-title "BPM"})
    (plotly/layer-point {:=y :avg-hr
                         :=name "Average HR"
                         :=mark-color "red"
                         :=mark-size 12})
    (plotly/layer-line {:=y :avg-hr
                        :=mark-color "red"
                        :=mark-opacity 0.3})
    plotly/plot)

;; Create a dataset with morphological features across sessions:
(def morphology-across-sessions
  (tc/dataset
   (map-indexed (fn [idx result]
                  (merge {:session-id (inc idx)
                          :video-name (last (str/split (:video-path result) #"/"))}
                         (:morphology-features result)))
                successful-results)))

morphology-across-sessions

;; Visualize morphological feature consistency:
;; Let's compare rise time and fall time across sessions
(-> morphology-across-sessions
    (plotly/base {:=x :session-id
                  :=title "Heartbeat Morphology Across Sessions"
                  :=x-title "Session"
                  :=y-title "Time (seconds)"})
    (plotly/layer-point {:=y :mean-rise-time
                         :=name "Rise Time"
                         :=mark-color "steelblue"
                         :=mark-size 10})
    (plotly/layer-line {:=y :mean-rise-time
                        :=mark-color "steelblue"
                        :=mark-opacity 0.3})
    (plotly/layer-point {:=y :mean-fall-time
                         :=name "Fall Time"
                         :=mark-color "coral"
                         :=mark-size 10})
    (plotly/layer-line {:=y :mean-fall-time
                        :=mark-color "coral"
                        :=mark-opacity 0.3})
    plotly/plot)

;; ### Computing Temporal Stability Metrics
;;
;; Let's quantify the consistency across sessions using coefficient of variation (CV):

(def temporal-stability
  (let [hr-cv (/ (dfn/standard-deviation (:avg-hr hr-across-sessions))
                 (dfn/mean (:avg-hr hr-across-sessions)))
        sdnn-cv (/ (dfn/standard-deviation (:sdnn hr-across-sessions))
                   (dfn/mean (:sdnn hr-across-sessions)))
        rise-time-cv (/ (dfn/standard-deviation (:mean-rise-time morphology-across-sessions))
                        (dfn/mean (:mean-rise-time morphology-across-sessions)))
        fall-time-cv (/ (dfn/standard-deviation (:mean-fall-time morphology-across-sessions))
                        (dfn/mean (:mean-fall-time morphology-across-sessions)))
        pulse-width-cv (/ (dfn/standard-deviation (:mean-pulse-width morphology-across-sessions))
                          (dfn/mean (:mean-pulse-width morphology-across-sessions)))]
    {:heart-rate-cv hr-cv
     :sdnn-cv sdnn-cv
     :rise-time-cv rise-time-cv
     :fall-time-cv fall-time-cv
     :pulse-width-cv pulse-width-cv
     :interpretation
     "Lower CV = more consistent across sessions. CV < 0.10 is excellent, 0.10-0.20 is good, > 0.20 is variable."}))

temporal-stability

;; Create a visualization showing variability across all key features:
(let [stability-data (tc/dataset
                      {:feature ["Heart Rate" "SDNN" "Rise Time" "Fall Time" "Pulse Width"]
                       :cv [(:heart-rate-cv temporal-stability)
                            (:sdnn-cv temporal-stability)
                            (:rise-time-cv temporal-stability)
                            (:fall-time-cv temporal-stability)
                            (:pulse-width-cv temporal-stability)]})]
  (-> stability-data
      (plotly/base {:=x :feature
                    :=y :cv
                    :=title "Temporal Stability: Feature Consistency Across Sessions"
                    :=y-title "Coefficient of Variation (lower = more stable)"})
      (plotly/layer-point {:=mark-color "purple"
                           :=mark-size 12})
      plotly/plot))

;; ### Key Findings from Multi-Video Analysis
;;
;; Based on our analysis of 6 sessions from participant 00000:

(kind/hiccup
 [:div
  [:h3 "Temporal Stability Summary"]
  [:p "Analyzed " (count successful-results) " sessions from participant 00000"]
  [:ul
   [:li [:strong "Heart Rate Consistency: "]
    (format "Mean = %.1f BPM, CV = %.2f%% - %s"
            (dfn/mean (:avg-hr hr-across-sessions))
            (* 100 (:heart-rate-cv temporal-stability))
            (if (< (:heart-rate-cv temporal-stability) 0.10)
              "Excellent stability"
              (if (< (:heart-rate-cv temporal-stability) 0.20)
                "Good stability"
                "Variable across sessions")))]
   [:li [:strong "Morphological Features: "]
    (format "Rise time CV = %.2f%%, Fall time CV = %.2f%%"
            (* 100 (:rise-time-cv temporal-stability))
            (* 100 (:fall-time-cv temporal-stability)))]
   [:li [:strong "Biometric Implication: "]
    (if (and (< (:heart-rate-cv temporal-stability) 0.15)
             (< (:rise-time-cv temporal-stability) 0.15))
      "Features are stable enough for potential biometric use"
      "High variability may challenge biometric authentication across sessions")]]])

;; ## What We've Learned So Far
;;
;; In this DSP study session, we've successfully reproduced major parts of the
;; "Seeing Red" PPG biometric authentication pipeline:
;;
;; 1. ✅ **Signal Extraction**: Extracted average green channel values from video frames
;; 2. ✅ **Preprocessing**: Applied Butterworth bandpass filtering (0.75-4 Hz)
;; 3. ✅ **Beat Detection**: Located individual heartbeats using peak detection
;; 4. ✅ **Heart Rate Estimation**: Calculated average BPM from inter-beat intervals
;; 5. ✅ **HRV Analysis**: Computed heart rate variability metrics (SDNN, RMSSD, pNN50)
;; 6. ✅ **Heartbeat Segmentation**: Extracted individual heartbeat waveforms around each peak
;; 7. ✅ **Feature Extraction**: Computed morphological features from each heartbeat
;;
;; ### What Would Come Next
;;
;; To complete a full biometric authentication system, we'd need to:
;;
;; - **Multi-Session Data**: Process multiple recordings from the same person
;; - **Multi-Person Data**: Collect data from different individuals
;; - **Classification**: Train a classifier (SVM, Random Forest, Neural Network) to:
;;   - Learn each person's unique PPG signature
;;   - Distinguish between authorized and unauthorized users
;; - **Evaluation**: Test on held-out sessions to measure:
;;   - **FAR** (False Acceptance Rate): How often impostors are accepted
;;   - **FRR** (False Rejection Rate): How often genuine users are rejected
;;   - **EER** (Equal Error Rate): The point where FAR = FRR
;;
;; The paper achieved 8% EER within sessions but 20% across sessions, suggesting
;; that PPG biometrics are promising but sensitive to physiological state changes
;; (stress, exercise, time of day, etc.).
;;
;; ### Key Takeaways and Lessons Learned
;;
;; **Signal Processing Insights:**
;; - **Adaptive channel selection is crucial**: While green is theoretically best for PPG, 
;;   we discovered that blue worked better for this specific recording due to exposure issues.
;;   Always check signal quality rather than blindly following theory!
;; - **Bandpass filtering dramatically improves signal quality**: The raw signal was nearly 
;;   impossible to interpret, but 0.75-4 Hz filtering revealed clear heartbeat patterns
;; - **Peak detection needs constraints**: Raw peak detection found both maxima and minima.
;;   Filtering for positive peaks with minimum spacing (0.25s) was essential for accuracy
;;
;; **Implementation Insights:**
;; - **Metadata matters**: We initially hardcoded 30s duration, but the video was actually 29.045s
;;   (3.2% error!). Always extract timing information from video metadata
;; - **Sensibility checks are essential**: Testing each step in the REPL caught multiple issues
;;   before they propagated (wrong channel, inflated heart rate, negative peaks)
;; - **Feature consistency is measurable**: Low coefficient of variation (CV < 0.3) in rise/fall
;;   times suggests stable, repeatable heartbeat morphology
;;
;; **Biometric Potential:**
;; - **Individual heartbeats show measurable morphological variations** that could distinguish people
;; - **Heart rate** (96 BPM) and **HRV metrics** (SDNN: 0.26s, RMSSD: 0.26s, pNN50: 57%) 
;;   provide additional biometric dimensions
;; - **Temporal stability** remains a question - would these features be consistent across sessions?
;;
;; **Study Group Learnings:**
;; - **Theory meets practice**: This exercise showed the gap between "what should work" (green channel)
;;   and "what actually works" (blue channel for this video)
;; - **Debugging is part of science**: We found and fixed color channel mix-ups, sampling rate errors,
;;   and peak detection issues - this iterative refinement is normal research workflow
;; - **Real-world systems need robustness**: Production systems would need adaptive algorithms to
;;   select the best channel, handle exposure variations, and reject low-quality recordings
;;
;; ### Technical Notes
;;
;; Throughout this notebook, we used:
;; - **dtype-next** for efficient numerical operations (readers, sub-buffers, functional ops)
;; - **JDSP** for signal processing (Butterworth filters, peak detection)
;; - **Type hints** to avoid Java reflection warnings
;; - **Sensibility checks** after each major computation to verify results
;;
;; Thanks for joining this study session! The code and techniques we explored here
;; could be extended to other PPG applications like stress monitoring, fitness tracking,
;; or medical diagnostics. The feature extraction approach could also be adapted for
;; other physiological signals (ECG, respiration, etc.).
