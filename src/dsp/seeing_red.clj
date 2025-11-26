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
            [tech.v3.dataset.tensor :as ds-tensor]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tech.v3.parallel.for :as pfor]
            [ham-fisted.reduce]
            [tech.v3.dataset :as ds]))

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
;; The dataset is available at: https://ora.ox.ac.uk/objects/uuid:1a04e852-e7e1-4981-aa83-f2e729371484
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
  "data/dsp/seeing-red/videos/00000/7254825.mp4")

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
      (tc/rename-columns [:a :r :g :b])))

;; Now let's visualize how these RGBA values vary across the first row.
;; Since the entire camera is covered by a finger, we expect the values to be
;; fairly uniform (mostly showing the pinkish-red color of finger tissue):

(let [[height width] (take 2 (dtype/shape first-tensor))]
  (-> first-tensor
      (tensor/slice 1)
      first
      ds-tensor/tensor->dataset
      (tc/rename-columns [:a :r :g :b])
      (tc/add-column :x (range))
      (plotly/base {:=mark-opacity 0.8
                    :=mark-size 5})
      (plotly/layer-line {:=y :r :=name "r" :=mark-color "red"})
      (plotly/layer-line {:=y :g :=name "g" :=mark-color "green"})
      (plotly/layer-line {:=y :b :=name "b" :=mark-color "blue"})
      (plotly/layer-line {:=y :a :=name "a" :=mark-color "black"})))

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

(def channels-along-time
  (-> all-tensors
      (->> (pmap (fn [t]
                   (-> t
                       ;; averge over height
                       (tensor/reduce-axis dfn/mean 0)
                       ;; average over width
                       (tensor/reduce-axis dfn/mean 0)))))
      tensor/->tensor
      ds-tensor/tensor->dataset
      (tc/rename-columns [:a :r :g :b])))

channels-along-time

;; Let us plot this:

(def sampling-rate
  (/ (count all-tensors)
     30.0))

(-> channels-along-time
    (tc/add-column :time (dfn// (range) sampling-rate))
    (plotly/base {:=x :time
                  :=mark-opacity 0.8
                  :=mark-size 5})
    (plotly/layer-line {:=y :r :=name "r" :=mark-color "red"})
    (plotly/layer-line {:=y :g :=name "g" :=mark-color "green"})
    (plotly/layer-line {:=y :b :=name "b" :=mark-color "blue"})
    (plotly/layer-line {:=y :a :=name "a" :=mark-color "black"}))

;; ## Focusing on the Green Channel
;;
;; As we mentioned earlier, the paper found that the green channel works best for PPG.
;; Let's isolate it and take a closer look:

(-> channels-along-time
    (tc/add-column :time (dfn// (range) sampling-rate))
    (plotly/base {:=x :time
                  :=mark-opacity 0.8
                  :=title "Raw Green Channel Signal"})
    (plotly/layer-line {:=y :g :=name "green channel" :=mark-color "green"}))

;; Can you spot the heartbeats? It's pretty noisy! We can see there's definitely
;; some periodic variation, but it's buried in other variations.
;;
;; The raw signal contains:
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

;; Extract just the green channel values:

(def green-signal
  (:g channels-along-time))

;; Design and apply a 4th-order Butterworth bandpass filter:
;; - Low cutoff: 0.75 Hz (45 BPM)
;; - High cutoff: 4.0 Hz (240 BPM)
;; - Sampling rate: from video frame rate

(def filtered-signal
  (let [flt (Butterworth. sampling-rate)]
    (vec (.bandPassFilter flt
                          (double-array green-signal)
                          4 ; filter order
                          0.75 ; low cutoff (Hz)
                          4.0)))) ; high cutoff (Hz)

;; Let's compare the raw and filtered signals:

(def comparison-data
  (tc/dataset {:time (dfn// (range (count green-signal)) sampling-rate)
               :raw green-signal
               :filtered filtered-signal}))

(-> comparison-data
    (plotly/base {:=x :time
                  :=title "Raw vs Filtered Green Channel"})
    (plotly/layer-line {:=y :raw
                        :=name "raw"
                        :=mark-color "lightgreen"
                        :=mark-opacity 0.5})
    (plotly/layer-line {:=y :filtered
                        :=name "filtered (0.75-4 Hz)"
                        :=mark-color "darkgreen"}))

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
                        :=mark-color "darkgreen"}))

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
  (.getPeaks (.detectPeaks peak-finder)))

;; How many heartbeats did we detect?

(count peak-indices)

;; Let's visualize the detected peaks on our signal:

(-> comparison-data
    (tc/select-rows (fn [row] (< 5 (:time row) 10)))
    (plotly/base {:=x :time
                  :=title "Detected Heartbeats (5-10 seconds)"})
    (plotly/layer-line {:=y :filtered
                        :=name "filtered signal"
                        :=mark-color "darkgreen"})
    (plotly/layer-point {:=x (mapv #(nth (:time comparison-data) %)
                                   (filter #(and (>= (nth (:time comparison-data) %) 5)
                                                 (< (nth (:time comparison-data) %) 10))
                                           peak-indices))
                         :=y (mapv #(nth filtered-signal %)
                                   (filter #(and (>= (nth (:time comparison-data) %) 5)
                                                 (< (nth (:time comparison-data) %) 10))
                                           peak-indices))
                         :=name "detected peaks"
                         :=mark-color "red"
                         :=mark-size 10}))

;; Great! We can now identify individual heartbeats in the signal.
;;
;; ## Calculating Heart Rate
;;
;; With the detected peaks, we can estimate the heart rate.
;; The time between consecutive peaks is the inter-beat interval (IBI).

(def peak-times
  (mapv #(nth (:time comparison-data) %) peak-indices))

(def inter-beat-intervals
  (mapv - (rest peak-times) (butlast peak-times)))

;; Average heart rate in beats per minute:

(def avg-heart-rate
  (/ 60.0 (dfn/mean inter-beat-intervals)))

(kind/hiccup
 [:div
  [:h3 "Estimated Heart Rate"]
  [:p {:style {:font-size "24px" :font-weight "bold"}}
   (format "%.1f BPM" avg-heart-rate)]])

;; This is just a rough estimate - a more robust implementation would:
;; - Remove outliers (missed or false detections)
;; - Use a sliding window for continuous heart rate estimation
;; - Account for heart rate variability

;; ## What We've Learned So Far
;;
;; In this DSP study session, we've successfully reproduced the first few steps
;; of the "Seeing Red" PPG biometric authentication pipeline:
;;
;; 1. ✅ **Signal Extraction**: Extracted average green channel values from video frames
;; 2. ✅ **Preprocessing**: Applied Butterworth bandpass filtering (0.75-4 Hz)
;; 3. ✅ **Beat Detection**: Located individual heartbeats using peak detection
;; 4. ✅ **Heart Rate Estimation**: Calculated average BPM from inter-beat intervals
;;
;; ### Next Steps for Future Sessions
;;
;; To complete the biometric authentication system, we'd need to:
;;
;; - **Heartbeat Segmentation**: Extract individual heartbeat waveforms around each peak
;; - **Feature Extraction**: Compute morphological features for each heartbeat:
;;   - Systolic peak amplitude and timing
;;   - Diastolic phase characteristics  
;;   - Pulse width and area under curve
;;   - Frequency domain features (FFT)
;; - **Classification**: Train a classifier (SVM or Random Forest) to distinguish users
;; - **Evaluation**: Test on multiple sessions to measure authentication accuracy
;;
;; The paper achieved 8% EER within sessions but 20% across sessions, suggesting
;; that PPG biometrics are promising but sensitive to physiological state changes.
;;
;; ### Key Takeaways
;;
;; - The **green channel** really does work best for PPG extraction
;; - **Bandpass filtering** dramatically improves signal quality
;; - We can reliably detect heartbeats even from noisy smartphone video
;; - Real-world biometric systems need to handle temporal variations
;;
;; Thanks for joining this study session! The code and techniques we explored here
;; could be extended to other PPG applications like stress monitoring, fitness tracking,
;; or medical diagnostics.
