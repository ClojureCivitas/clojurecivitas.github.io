^{:kindly/hide-code true
  :clay {:title "Seeing Red: PPG Biometric Authentication"
         :quarto {:author [:daslu]
                  :date "2025-11-26"
                  :category :data
                  :tags [:dsp :biometrics :signal-processing :computer-vision]}}}
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
            [scicloj.tableplot.v1.plotly :as plotly]))

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


