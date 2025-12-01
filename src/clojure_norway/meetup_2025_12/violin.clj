(ns clojure-norway.meetup-2025-12.violin
  (:require [scicloj.kindly.v4.kind :as kind]
            [clojure.java.io :as io]
            [babashka.fs :as fs]
            [tech.v3.datatype.functional :as dfn]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tech.v3.tensor :as tensor]
            [tablecloth.column.api :as tcc]
            [fastmath.stats :as stats]))


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
              (map first))
         32768.0))

;; ## Listening to the data back as audio



^kind/audio
{:samples wav-samples
 :sample-rate sample-rate}

;; ## Computing the power spectrum of the data

(import 'com.github.psambit9791.jdsp.transform.DiscreteFourier)

(count wav-samples)

(def first-part
  (take sample-rate ; just a second
        wav-samples))

(def power-spectrum
  (let [fft (-> first-part
                double-array
                DiscreteFourier.)]
    (.transform fft)
    ;; Get magnitude spectrum (power)
    (.getMagnitude fft true)))

(count power-spectrum)

(def power-spectrum-ds
  (tc/dataset
   {:freq (range)
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
    (-> (tc/dataset {:freq (.getPeaks peaks)
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


(def violin-dataset
  (-> components-dataset
      (tc/add-column :violin
                     (fn [ds]
                       (-> ds
                           (tc/drop-columns [:time])
                           vals
                           (->> (apply tcc/+)))))))

(-> violin-dataset
    (tc/head 200)
    (plotly/layer-line {:=x :time
                        :=y :violin}))

(defn audio [samples]
  (with-meta
    {:samples samples
     :sample-rate sample-rate}
    {:kind/audio true}))

(defn normalize [values]
  (tcc// values
         (tcc/reduce-max (tcc/abs values))))

(-> violin-dataset
    :violin
    normalize
    audio)

;; ## Power spectrum by part

(require '[tech.v3.tensor :as tensor])

(def parts
  (let [n-parts 25]
    (-> wav-samples
        tensor/->tensor
        (tensor/reshape  [n-parts
                          (/ (count wav-samples) n-parts)])
        (tensor/slice 1))))


(def spectra
  (->> parts
       (pmap (fn [part]
               (let [fft (-> part
                             double-array
                             DiscreteFourier.)]
                 (.transform fft)
                 ;; Get magnitude spectrum (power)
                 (.getMagnitude fft true))))))

(->> spectra
     (map (fn [s]
            (-> {:freq (range)
                 :power s}
                tc/dataset
                (plotly/base {:=height 300
                              :=width 900})
                (plotly/layer-line {:=x :freq
                                    :=y :power})
                plotly/plot
                (assoc-in [:layout :xaxis :range] [0 1000])))))

