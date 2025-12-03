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
            [tech.v3.datatype :as dtype]
            [clojure.math :as math]))

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

(kind/audio
 {:src (str "clojure_norway/meetup_2025_12/" violin-file-name)})

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

(require '[tech.v3.datatype :as dtype])

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

;; ## Computing the Discrete Fouried Transform the data

(import 'com.github.psambit9791.jdsp.transform.DiscreteFourier)

(count wav-samples)

(defn some-part [t0 t1]
  (dtype/sub-buffer 
   wav-samples
   (int (* sample-rate t0))
   (int (* sample-rate (- t1 t0)))))

(def Nyquist-freq (* 0.5 sample-rate))

(defn data->dft-ds [data]
  (let [dft (-> data
                double-array
                DiscreteFourier.)]
    (.transform dft)
    (let [amp (.getMagnitude dft true)
          phase (.getPhaseRad dft true)]
      (-> (tc/dataset {:freq (dfn// (range)
                                    (/ (count amp) Nyquist-freq))
                       :amplitude amp
                       :phase phase})
          (tc/add-column :power #(tcc/sq (:amplitude %)))))))

(def some-dft-ds
  (data->dft-ds
   (some-part 1 1.05)))

(-> some-dft-ds
    (plotly/layer-line {:=x :freq
                        :=y :power}))

;; ## Finding peaks

(import 'com.github.psambit9791.jdsp.signal.peaks.FindPeak)

(defn dft-ds->peaks-ds [dft-ds {:keys [n-peaks]}]
  (let [peaks (-> dft-ds
                  :power
                  double-array
                  FindPeak.
                  .detectPeaks)]
    (.getPeaks peaks)
    (-> dft-ds
        (tc/select-rows (dtype/as-reader
                         (.getPeaks peaks)))
        (tc/add-column :prominence (.getProminence peaks))
        (tc/order-by [:prominence] :desc)
        (tc/head n-peaks))))

(-> some-dft-ds
    (plotly/base {:=x :freq
                  :=y :power})
    plotly/layer-line 
    (plotly/layer-point {:=dataset (dft-ds->peaks-ds some-dft-ds
                                                     {:n-peaks 10})}))

;; ## Synthesizing from the peaks

(require '[clojure.math :as math])

(defn peaks-ds->components-ds [peaks-ds {:keys [duration]}]
  (let [num-samples (* duration sample-rate)
        time (dtype/make-reader :float32
                                num-samples
                                (/ idx sample-rate))]
    (->> (tc/rows peaks-ds :as-maps)
         ;; For each peak, generate a sine wave
         (map-indexed (fn [i {:keys [freq power phase]}]
                        [(str "wave" i)
                         (-> time
                             (dfn/* (* 2 math/PI freq))
                             ;; (dfn/+ phase)
                             dfn/cos
                             (dfn/* power))]))
         (into {:time time})
         tc/dataset)))

(-> some-dft-ds
    (dft-ds->peaks-ds {:n-peaks 10})
    (peaks-ds->components-ds {:duration 1})
    (tc/head 500))

(-> some-dft-ds
    (dft-ds->peaks-ds {:n-peaks 10})
    (peaks-ds->components-ds {:duration 1})
    (tc/head 500)
    (tc/pivot->longer (complement #{:time}))
    (tc/rename-columns {:$value :value})
    (plotly/layer-line {:=x :time
                        :=y :value
                        :=color :$column}))

(defn normalize [values]
  (dfn// values
         (double (dfn/reduce-max (dfn/abs values)))))

(defn components-ds->combined-ds [components-ds]
  (-> components-ds
      (tc/add-column :combined
                     (fn [ds]
                       (-> ds
                           (tc/drop-columns [:time])
                           vals
                           (->> (apply tcc/+))
                           normalize)))))

(-> some-dft-ds
    (dft-ds->peaks-ds {:n-peaks 10})
    (peaks-ds->components-ds {:duration 1})
    components-ds->combined-ds
    (tc/head 500)
    (plotly/layer-line {:=x :time
                        :=y :combined}))

(defn audio [samples]
  (with-meta
    {:samples samples
     :sample-rate sample-rate}
    {:kind/audio true}))

(-> (some-part 1 1.05)
    audio)

(-> some-dft-ds
    (dft-ds->peaks-ds {:n-peaks 10})
    (peaks-ds->components-ds {:duration 1})
    components-ds->combined-ds
    :combined
    audio)

;; ## Spectogram (WIP)

(import 'com.github.psambit9791.jdsp.windows.Hanning)



(def stft
  (let [window-size 0.05 ; seconds
        window-samples (* sample-rate window-size)

        ;; Create Hanning window to reduce spectral leakage
        hanning (-> window-samples
                    Hanning.
                    .getWindow
                    dtype/as-reader)

        ;; Overlap parameters
        overlap-fraction 0.1
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
                           :float32)]

    (-> hanninged-windows
        ;; Rearrange to [windows × time]
        (tensor/transpose [1 0])
        (tensor/slice 1)
        ;; Apply FFT to each window
        (->> (pmap (fn [window]
                     (let [dft (-> window
                                   dtype/as-reader
                                   double-array
                                   DiscreteFourier.)]
                       (.transform dft)
                       (let [amp (.getMagnitude dft true)
                             phase (.getPhaseRad dft true)]
                         (-> (tc/dataset {:freq (dfn// (range)
                                                       (/ (count amp) Nyquist-freq))
                                          :amplitude amp
                                          :phase phase})
                             (tc/add-column :power #(tcc/sq (:amplitude %))))))))))))


(require '[tech.v3.dataset.tensor :as ds-tensor])

(def spectrogram
  (-> stft
      (->> (map :power))
      tensor/->tensor
      ;; Reshape to [windows × frequencies]
      (#(tensor/reshape %
                        [(count %)
                         (-> % first count)]))
      ;; Transpose to [frequencies × windows] for plotting
      (tensor/transpose [1 0])))


(require '[clojure2d.color :as color])

(def palette
  (let [p (color/palette :viridis)]
    (memoize
     (fn [ratio]
       (p (int (math/round (* ratio (dec (count p))))))))))

(def normalized-spectrogram
  (normalize spectrogram))

(require '[tech.v3.libs.buffered-image :as bufimg])

(-> (tensor/compute-tensor
     (conj (dtype/shape spectrogram) 3)
     (fn [y x c]
       ((palette (normalized-spectrogram y x))
        c)))
    bufimg/tensor->image)


;; ## Playing the spectrogram



(-> stft
    (->> (map (fn [dft]
                (-> dft
                    (dft-ds->peaks-ds {:n-peaks 5})
                    (peaks-ds->components-ds {:duration 0.005})
                    components-ds->combined-ds
                    (tc/select-columns [:combined]))))
         (apply tc/concat))
    :combined
    audio)

