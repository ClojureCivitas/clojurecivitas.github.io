^{:kindly/hide-code true
  :clay {:title "DSP Study Group - Camera PPG data"
         :quarto {:author [:onbreath :daslu]
                  :description "Analysing the MTHS dataset of Camera PPG data"
                  :category :clojure
                  :type :post
                  :date "2025-11-21"
                  :tags [:dsp :math :biometrics]}}}
(ns dsp.mths)

;; ## Intro

;; ## Setup

(ns dsp.mths
  (:require [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.-] :as py]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [libpython-clj2.python.np-array]
            [clojure.java.io :as io]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [tablecloth.api :as tc]
            [tech.v3.dataset.tensor :as ds-tensor]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tablecloth.column.api :as tcc]
            [fastmath.stats :as stats]
            [scicloj.kindly.v4.kind :as kind]
            [tech.v3.parallel.for :as pfor]
            [tech.v3.tensor :as tensor]
            [tech.v3.dataset :as ds])
  (:import [com.github.psambit9791.jdsp.filter Butterworth Chebyshev]
           [com.github.psambit9791.jdsp.signal Detrend]
           [com.github.psambit9791.jdsp.signal.peaks FindPeak Peak]
           [com.github.psambit9791.jdsp.transform DiscreteFourier FastFourier]
           [com.github.psambit9791.jdsp.windows Hanning]))

(require-python '[numpy :as np])

^:kindly/hide-code
(kind/hiccup
 [:style
  ".clay-dataset {
  max-height:400px; 
  overflow-y: auto;
}
"])

;; ## Reading data

;; We assume you have downloaded the MEDVSE repo alongside your Clojure project repo.

(def data-base-path
  "../MEDVSE/MTHS/Data/")

;; Let us see how we read one file as a Numpy data structure.

(np/load (str data-base-path "/signal_12.npy"))

;; Now, let us read all data and organise it in one data structure.

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

;; Numpy arrays can be inspected using dtype-next:

(dtype/shape (raw-data [:signal 23])) ; 30Hz signal
(dtype/shape (raw-data [:label 23])) ; 1Hz labels


(def sampling-rate 30)


;; A function to read the signal of the i'th subject:

(defn signal [i]
  (some-> [:signal i]
          raw-data
          ds-tensor/tensor->dataset
          (tc/rename-columns [:R :G :B])
          (tc/add-column :t (tcc// (range) 30.0))))

;; For example:

(signal 23)

;; ## Plotting

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


;; ## Taking a signal through a pipeline of transformations

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


(defn remove-dc [signal]
  (-> signal
      double-array
      (Detrend. "constant")
      .detrendSignal))


;; Bandpass filter (0.5-5 Hz for heart rate range)
(defn bandpass-filter [signal {:keys [fs order low-cutoff high-cutoff]}]
  (let [flt (Butterworth. fs)
        result (.bandPassFilter flt 
                                (double-array signal) 
                                order 
                                low-cutoff 
                                high-cutoff)]
    (vec result)))



(let [[low-cutoff high-cutoff] [0.5 5]
      window-size 10
      window-samples (* sampling-rate window-size)
      overlap-fraction 0.5
      hop (* sampling-rate window-samples)
      windows-starts (range 0 )]
  (plot-signals-with-transformations
   {:remove-dc remove-dc
    :bandpass #(bandpass-filter % {:fs sampling-rate
                                   :order 4
                                   :low-cutoff low-cutoff
                                   :high-cutoff high-cutoff})
    :standardize stats/standardize}))




;; ## Power spectry (WIP draft, incomplete)


(let [[low-cutoff high-cutoff] [0.65 4]
      window-size 10
      window-samples (* sampling-rate window-size)
      hanning (-> window-samples
                  Hanning.
                  .getWindow
                  dtype/as-reader)
      overlap-fraction 0.05
      hop (int (* overlap-fraction window-samples))
      subject 51
      sig (signal subject)
      n-samples (tc/row-count sig)
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
      n-windows (-> n-samples
                    (- window-samples)
                    (quot hop))
      windows-shape [window-samples
                     n-windows
                     3]
      windows (tensor/compute-tensor
               windows-shape
               (fn [i j k]
                 (std (+ (* j hop) i)
                      k))
               :float32)
      hanninged-windows (tensor/compute-tensor
                         windows-shape
                         (fn [i j k]
                           (* (windows i j k)
                              (hanning i)))
                         :float32)
      power-spectrum (-> hanninged-windows
                         (tensor/transpose [1 2 0])
                         (tensor/slice 2)
                         (->> (mapv (fn [window]
                                      (let [fft (-> window
                                                    double-array
                                                    DiscreteFourier.)]
                                        (.transform fft)
                                        (.getMagnitude fft true)))))
                         tensor/->tensor
                         (#(tensor/reshape %
                                           [n-windows
                                            3
                                            (-> % first count)]))
                         (tensor/transpose [2 0 1]))]
  [(-> windows
       (dfn/* 100)
       plotly/imshow)
   (-> hanninged-windows
       (dfn/* 100)
       plotly/imshow)
   (-> power-spectrum
       plotly/imshow)
   (-> power-spectrum
       (tensor/slice 1)
       (->> (map (fn [s]
                   (-> s
                       ds-tensor/tensor->dataset
                       (tc/rename-columns [:R :G :B])
                       plot-signal)))))])



