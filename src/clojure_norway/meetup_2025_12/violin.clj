(ns clojure-norway.meetup-2025-12.violin
  (:require [scicloj.kindly.v4.kind :as kind]
            [clojure.java.io :as io]
            [babashka.fs :as fs]
            [tech.v3.datatype.functional :as dfn]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tech.v3.tensor :as tensor]
            [tablecloth.column.api :as tcc]
            [fastmath.stats :as stats]
            [tech.v3.datatype :as dtype]))

;; ## Data source
;;
;; [violin tremolo G#2.aif](https://freesound.org/people/ldk1609/sounds/56085/)
;; by [ldk1609](https://freesound.org/people/ldk1609/)

;; We converted it from '.aiff' format to `.wav`
;; for familiarity:
;; ```bash
;; ffmpeg -i 56085__ldk1609__violin-tremolo-g2.aiff violin-tremolo-g2.wav
;; ```

(def violin-file-name
  "violin-tremolo-g2.wav")

(require '[babashka.fs :as fs])

(def violin-file-path
  (fs/file (fs/parent *file*)
           violin-file-name))

;; ## Listening to the file

(kind/audio
 {:src violin-file-name})

;; ## Reading the Wav file as data

;; [Reading WAV files](https://clojurecivitas.github.io/dsp/wav_files.html).

(import '(javax.sound.sampled AudioFileFormat
                              AudioInputStream
                              AudioSystem)
        '(java.io InputStream)
        '(java.nio ByteBuffer
                   ByteOrder))

(defn audio-format [^InputStream is]
  (let [file-format (AudioSystem/getAudioFileFormat is)
        format      (.getFormat file-format)]
    {:is-big-endian?   (.isBigEndian format)
     :channels         (.getChannels format)
     :sample-rate      (.getSampleRate format)
     :sample-size-bits (.getSampleSizeInBits format)
     :frame-length     (.getFrameLength file-format)
     :encoding         (str (.getEncoding format))}))

(require '[clojure.java.io :as io])

(def wav-format
  (with-open [wav-stream (io/input-stream violin-file-path)]
    (audio-format wav-stream)))

wav-format

(def sample-rate
  (:sample-rate wav-format))

(defn audio-data [^InputStream is]
  (let [{:keys [frame-length
                channels]} (audio-format is)
        format           (-> (AudioSystem/getAudioFileFormat is)
                             AudioFileFormat/.getFormat)
        ^bytes
        audio-bytes      (with-open [ais (AudioInputStream. is format
                                                            frame-length)]
                           (AudioInputStream/.readAllBytes ais))
        audio-shorts     (short-array frame-length)
        bb               (ByteBuffer/allocate 2)]
    (dotimes [i frame-length]
      (ByteBuffer/.clear bb)
      (.order bb ByteOrder/LITTLE_ENDIAN)
      (.put bb ^byte (aget audio-bytes (* 2 i)))
      (.put bb ^byte (aget audio-bytes (inc (* 2 i))))
      (aset-short audio-shorts i (.getShort bb 0)))
    audio-shorts))

(def wav-shorts
  (with-open [wav-stream (io/input-stream violin-file-path)]
    (audio-data wav-stream)))

(def wav-samples
  ;; one of the two stereo channels
  (dfn// (->> wav-shorts
              (partition 2)
              (map first)
              dtype/->double-array)
         32768.0))

(def wav-ds
  (tc/dataset
   {:time (dfn// (range)
                 (double sample-rate))
    :sample wav-samples}))

wav-ds

(-> wav-ds
    (tc/select-rows #(<= 0 (:time %) 1))
    (plotly/layer-line {:=x :time
                        :=y :sample}))

(-> wav-ds
    (tc/select-rows #(<= 1 (:time %) 1.06))
    (plotly/layer-line {:=x :time
                        :=y :sample}))

;; ## Listening to the data back as audio

^kind/audio
{:samples wav-samples
 :sample-rate sample-rate}

^kind/audio
{:samples (->> (-> wav-ds
                   (tc/select-rows #(<= 1 (:time %) 1.06))
                   :sample)
               (repeat 30)
               (apply concat))
 :sample-rate sample-rate}

;; ## Computing the power spectrum of the data

(import 'com.github.psambit9791.jdsp.transform.DiscreteFourier)

(count wav-samples)

(def some-part
  (->> wav-samples
       (drop sample-rate) ; drop a second
       (take (* sample-rate 0.06)) ; take 6% of a second
       ))

(def power-spectrum
  (let [fft (-> some-part
                double-array
                DiscreteFourier.)]
    (.transform fft)
    ;; Get magnitude spectrum (power)
    (.getMagnitude fft true)))

(count power-spectrum)

(def Nyquist-freq (* 0.5 sample-rate))

(def power-spectrum-ds
  (tc/dataset
   {:freq (dfn// (range)
                 (/ (count power-spectrum) Nyquist-freq))
    :power power-spectrum}))

(-> power-spectrum-ds
    (plotly/layer-line {:=x :freq
                        :=y :power}))

;; ## Finding peaks (WIP)

(import 'com.github.psambit9791.jdsp.signal.peaks.FindPeak)

(def n-peaks 10)

(def peaks-ds
  (let [peaks (-> power-spectrum
                  FindPeak.
                  .detectPeaks)]
    (-> (tc/dataset {:freq (dfn// (.getPeaks peaks)
                                  (/ (count power-spectrum) Nyquist-freq ))
                     :power (.getHeights peaks)
                     :prominence (.getProminence peaks)})
        (tc/order-by [:prominence] :desc)
        (tc/head n-peaks))))

(-> power-spectrum-ds
    (plotly/base {:=x :freq
                  :=y :power})
    plotly/layer-line 
    (plotly/layer-point {:=dataset peaks-ds}))


;; ## Synthesizing from the peaks

(require '[clojure.math :as math])
(require '[tech.v3.datatype :as dtype])

(def components-dataset
  (let [duration 1
        num-samples (* duration sample-rate)
        time (dtype/make-reader :float32
                                num-samples
                                (/ idx sample-rate))]
    (->> (tc/rows peaks-ds :as-maps)
         ;; For each peak, generate a sine wave
         (map-indexed (fn [i {:keys [freq power]}]
                        [(str "wave" i)
                         (-> time
                             (dfn/* (* 2 math/PI freq))
                             dfn/sin
                             (dfn/* power))]))
         (into {:time time})
         tc/dataset)))

(-> components-dataset
    (tc/head 200)
    (tc/pivot->longer (complement #{:time})))

(-> components-dataset
    (tc/head 200)
    (tc/pivot->longer (complement #{:time}))
    (tc/rename-columns {:$value :value})
    (plotly/layer-line {:=x :time
                        :=y :value
                        :=color :$column}))

(defn normalize [values]
  (tcc// values
         (tcc/reduce-max (tcc/abs values))))

(def combined-dataset
  (-> components-dataset
      (tc/add-column :combined
                     (fn [ds]
                       (-> ds
                           (tc/drop-columns [:time])
                           vals
                           (->> (apply tcc/+))
                           normalize)))))

(-> combined-dataset
    (tc/head 200)
    (plotly/layer-line {:=x :time
                        :=y :combined}))

(defn audio [samples]
  (with-meta
    {:samples samples
     :sample-rate sample-rate}
    {:kind/audio true}))

(-> some-part
    audio)

(-> combined-dataset
    :combined
    audio)

;; ## Spectogram (WIP)

(import 'com.github.psambit9791.jdsp.windows.Hanning)
(require '[tech.v3.dataset.tensor :as ds-tensor])

(let [window-size 0.03 ; seconds
      window-samples (* sample-rate window-size)

      ;; Create Hanning window to reduce spectral leakage
      hanning (-> window-samples
                  Hanning.
                  .getWindow
                  dtype/as-reader)

      ;; Overlap parameters
      overlap-fraction 0.05
      hop (int (* overlap-fraction window-samples))

      n-samples (count wav-samples)

      ;; Calculate how many windows we can extract
      n-windows (-> n-samples
                    (- window-samples)
                    (quot hop))

      ;; Create sliding windows: [time × windows × channels]
      windows-shape [window-samples n-windows]
      windows (tensor/compute-tensor
               windows-shape
               (fn [i j]
                 ;; Extract sample at time i, window j, channel k
                 (wav-samples (+ (* j hop) i)))
               :float32)

      ;; Apply Hanning window to each segment
      hanninged-windows (tensor/compute-tensor
                         windows-shape
                         (fn [i j]
                           (* (windows i j)
                              (hanning i)))
                         :float32)

      ;; ;; Compute power spectrum for each window
      spectogram (-> hanninged-windows
                     ;; Rearrange to [windows × time]
                     (tensor/transpose [1 0])
                     (tensor/slice 1)
                     ;; Apply FFT to each window
                     (->> (pmap (fn [window]
                                  (let [fft (-> window
                                                dtype/as-reader
                                                double-array
                                                DiscreteFourier.)]
                                    (.transform fft)
                                    ;; Get magnitude spectrum (power)
                                    (.getMagnitude fft true)))))
                     tensor/->tensor
                     ;; Reshape to [windows × frequencies]
                     (#(tensor/reshape %
                                       [n-windows
                                        (-> % first count)]))
                     ;; Transpose to [frequencies × windows] for plotting
                     (tensor/transpose [1 0]))]
  ;; (-> windows
  ;;     (dfn/* 100)
  ;;     plotly/imshow)

  ;; (-> hanninged-windows
  ;;     (dfn/* 100)
  ;;     plotly/imshow)

  (-> spectogram
      (#(tensor/broadcast % (cons 3 (dtype/shape %))))
      (dfn/* 50)
      (tensor/transpose [1 2 0])
      plotly/imshow))







