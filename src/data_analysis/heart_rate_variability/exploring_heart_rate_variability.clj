^{:kindly/hide-code true
  :clay             {:title  "Exploring Heart Rate Variability"
                     :external-requirements ["WESAD dataset at /workspace/datasets/WESAD/"]
                     :quarto {:author :ludgersolbach
                              :draft true
                              :type   :post
                              :date   "2025-10-17"
                              :tags   [:data-analysis :noj]}}}
(ns data-analysis.heart-rate-variability.exploring-heart-rate-variability
  (:require [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.rolling :as rolling]
            [tech.v3.datatype.functional :as dfn]
            [tech.v3.tensor :as tensor]
            [tech.v3.datatype.datetime :as dt-datetime]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [fastmath.stats :as stats]
            [clojure.math :as math]
            [fastmath.interpolation :as interp]
            [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.-] :as py]
            [libpython-clj2.python.np-array]
            [tech.v3.parallel.for :as pfor]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.tableplot.v1.plotly :as plotly]
            [java-time.api :as jt]))


;; # Exploring HRV - DRAFT 🛠

(ns data-analysis.heart-rate-variability.exploring-heart-rate-variability
  (:require [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [tech.v3.tensor :as tensor]
            [tech.v3.datatype.datetime :as dt-datetime]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [fastmath.stats :as stats]
            [clojure.math :as math]
            [fastmath.interpolation :as interp]
            [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.-] :as py]
            [libpython-clj2.python.np-array]
            [tech.v3.parallel.for :as pfor]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.tableplot.v1.plotly :as plotly]
            [java-time.api :as jt])
  (:import [com.github.psambit9791.jdsp.signal CrossCorrelation]
           [com.github.psambit9791.jdsp.signal.peaks FindPeak]
           [com.github.psambit9791.jdsp.filter Butterworth]
           [com.github.psambit9791.jdsp.transform DiscreteFourier]))


;; ## My pulse-to-pulse intervals


;; (extracted from PPG data)


(def my-ppi
  (-> (tc/dataset "src/data_analysis/heart_rate_variability/ppi-series.csv"
                  {:key-fn keyword})
      (tc/map-columns :t
                      :t
                      (fn [t]
                        (-> t
                            jt/to-millis-from-epoch
                            (/ 1000.0))))))

(delay
  (-> my-ppi
      (plotly/base {:=x :t
                    :=height 300 :=width 700})
      (plotly/layer-bar {:=y :ppi})))


(def compute-measures
  (fn [ppi-ds {:keys [sampling-rate
                      window-size-in-sec ]}]
    (let [spline (interp/interpolation
                  :cubic
                  (:t ppi-ds)
                  (:ppi ppi-ds))
          resampled-ppi (-> {:t (tcc/* (range 50
                                              (* (int (tcc/reduce-max (:t ppi-ds)))
                                                 sampling-rate))
                                       (/ 1.0 sampling-rate))}
                            tc/dataset
                            (tc/map-columns :ppi :t spline))
          bw (com.github.psambit9791.jdsp.filter.Butterworth.
              sampling-rate)
          n (tc/row-count resampled-ppi)
          window-size (* window-size-in-sec  sampling-rate)
          hop-size 8
          n-windows (int (/ (- n window-size)
                            hop-size))
          ranges (-> ppi-ds
                     :t
                     (rolling/variable-rolling-window-ranges
                      30
                      {:relative-window-position :left}))
          range-datasets (->> ranges
                              (map (fn [r]
                                     (tc/select-rows ppi-ds r))))
          reasonable-ppi? #(< 500 % 900)
          rmssds (->> range-datasets
                      (map (fn [{:keys [ppi]}]
                             (->> ppi
                                  (partition-by reasonable-ppi?)
                                  (map (fn [part]
                                         (when (-> part first reasonable-ppi?)
                                           (map -
                                                (rest part)
                                                part))))
                                  (remove nil?)
                                  (apply concat)
                                  double-array
                                  tcc/sq
                                  tcc/mean
                                  math/sqrt))))
          spectrograms (->> (range n-windows)
                            (pfor/pmap (fn [w]
                                         (let [start-idx (* w hop-size)
                                               window (-> resampled-ppi
                                                          :ppi
                                                          (dtype/sub-buffer start-idx window-size))
                                               window-standardized (stats/standardize window)
                                               window-filtered (.bandPassFilter bw
                                                                                (double-array window-standardized)
                                                                                4
                                                                                0
                                                                                0.4)
                                               fft (DiscreteFourier. (double-array window-filtered))
                                               _ (.transform fft)
                                               whole-magnitude (.getMagnitude fft true)]
                                           {:t (* w hop-size (/ 1.0 sampling-rate))
                                            :whole-magnitude whole-magnitude
                                            :magnitude (take 40 whole-magnitude)})))
                            vec)]
      {:sampling-rate sampling-rate
       :resampled-ppi resampled-ppi
       :rmssds rmssds
       :spectrograms spectrograms})))


(comment
  (compute-measures my-ppi
                    {:sampling-rate 10
                     :window-size-in-sec 60}))


;; [An Overview of Heart Rate Variability Metrics and Norms](https://pmc.ncbi.nlm.nih.gov/articles/PMC5624990/)
;; by Fred Shaffer, & J P Ginsberg.
;; doi: [10.3389/fpubh.2017.00258](https://www.frontiersin.org/journals/public-health/articles/10.3389/fpubh.2017.00258/full)

(defn LF-to-HF [freqs spectrogram]
  (let [ds (tc/dataset {:f freqs
                        :s (:magnitude spectrogram)}
                       tc/dataset)]
    (/ (-> ds
           (tc/select-rows #(<= 0.04 (% :f) 0.15))
           :s
           tcc/sum)
       (-> ds
           (tc/select-rows #(<= 0.15 (% :f) 0.4))
           :s
           tcc/sum))))


(defn plot-with-measures [{:keys [sampling-rate
                                  resampled-ppi
                                  rmssds
                                  spectrograms]}]
  (when spectrograms
    (let [n (-> spectrograms first :magnitude count)
          Nyquist-freq (/ sampling-rate 2.0)
          freq-resolution (/ Nyquist-freq n)
          times (map (comp str :t) spectrograms)
          freqs (tcc/* (range n)
                       freq-resolution)]
      {:resampled-ppi (-> resampled-ppi
                          (plotly/base {:=height 300 :=width 700})
                          (plotly/layer-bar (merge {:=x :t
                                                    :=y :ppi}
                                                   (when (:label resampled-ppi)
                                                     {:=color :label
                                                      :=color-type :nominal}))))
       :rmssd (-> {:t times
                   :rmssd rmssds}
                  tc/dataset
                  (plotly/base {:=height 300 :=width 700})
                  (plotly/layer-line {:=x :t
                                      :=y :rmssd})
                  plotly/plot
                  (assoc-in [:layout :yaxis :range] [0 100]))
       :power-spectrum (kind/plotly
                        {:data [{:x times
                                 :y freqs
                                 :z (-> (mapv :magnitude spectrograms)
                                        (tensor/transpose [1 0]))
                                 :type :heatmap
                                 :showscale false}]
                         :layout {:height 300
                                  :width 700
                                  :margin {:t 25}
                                  :xaxis {:title {:text "t"}}
                                  :yaxis {:title {:text "freq"}}}})
       :mean-power-spectrum (-> {:freq freqs
                                 :mean-power (-> spectrograms
                                                 (->> (map :magnitude))
                                                 tensor/->tensor
                                                 (tensor/reduce-axis dfn/mean 0))}
                                tc/dataset
                                (plotly/base {:=height 300 :=width 700})
                                (plotly/layer-bar {:=x :freq
                                                   :=y :mean-power}))
       :LF-to-HF-series (-> {:t times
                             :LF-to-HF (->> spectrograms
                                            (map (partial LF-to-HF freqs)))}
                            tc/dataset
                            (plotly/base {:=height 300 :=width 700})
                            (plotly/layer-line {:=x :t
                                                :=y :LF-to-HF})
                            plotly/plot
                            (assoc-in [:layout :yaxis :range] [0 4])
                            (assoc-in [:layout :yaxis :title] {:text "LF/HF"}))})))


(delay
  (-> my-ppi
      (compute-measures {:sampling-rate 10
                         :window-size-in-sec 60})
      plot-with-measures))


;; ## Analysing ECG data

;; ### The [WESAD](https://dl.acm.org/doi/10.1145/3242969.3242985) dataset

(def WESAD-sampling-rate 700)

(require-python '[pickle :as pkl]
                '[pandas :as pd]
                '[builtins])

(defn load-pickle [filename]
  "Load object from pickle file"
  (py/with [f (builtins/open filename "rb")]
           (pkl/load f :encoding "latin")))

(def labelled-data
  (memoize
   (fn [subject]
     (load-pickle (format "/workspace/datasets/WESAD/WESAD/S%d/S%d.pkl"
                          subject subject)))))

(def labelled-dataset
  (memoize
   (fn [subject]
     (let [ld (labelled-data subject)]
       (tc/dataset {:t (tcc/* (range)
                              (/ 1.0 WESAD-sampling-rate))
                    :ECG (-> ld
                             (get-in [:signal :chest :ECG])
                             (py. flatten))
                    :label  (-> ld
                                (get :label))})))))

(delay
  (labelled-dataset 5))

;; ### Finding peaks

;; [Pan-Tompkins Algorithm](https://en.wikipedia.org/wiki/Pan%E2%80%93Tompkins_algorithm)

;; [Unupervised ECG QRS Detection](https://hooman650.github.io/ECG-QRS.html) by Hooman SedghamizHooman Sedghamiz

;; scipy: `peaks = signal.find_peaks(signal, height=mean, distance=200)`
;; JDSP equivalent:
(defn find-peaks [signal {:keys [distance]}]
  (let [signal-array (double-array signal)
        fp (FindPeak. signal-array)
        peak-obj (.detectPeaks fp)
        signal-mean (dfn/mean signal-array)
        ;; Filter by  (lower=mean, upper=nil for no upper limit)
        height-filtered (.filterByHeight peak-obj signal-mean nil)
        ;; Then filter by distance
        final-peaks (.filterByPeakDistance peak-obj height-filtered distance)]
    final-peaks)) ; Returns int[] of peak row-numbers


(delay
  (let [bw (com.github.psambit9791.jdsp.filter.Butterworth.
            WESAD-sampling-rate)
        raw (labelled-dataset 5)
        pipeline (-> raw
                     (tc/head 50000)
                     (tc/add-column :filtered
                                    #(.bandPassFilter bw
                                                      (double-array (:ECG %))
                                                      4
                                                      5 15))
                     (tc/add-column :sqdiff
                                    #(tcc/sq
                                      (tcc/-
                                       (:filtered %)
                                       (tcc/shift (:filtered %) 1))))
                     (tc/add-column :peak #(let [peaks (set
                                                        (find-peaks (:sqdiff %)
                                                                    {:distance 200}))]
                                             (->> (tc/row-count %)
                                                  range
                                                  (map (fn [i]
                                                         (if (peaks i)
                                                           1
                                                           nil)))))))]
    (-> pipeline
        (tc/head 5000)
        (plotly/base {:=x :t})
        (plotly/layer-line {:=y :ECG
                            :=name "raw ECG"})
        (plotly/layer-line {:=y :filtered
                            :=name "filtered"})
        (plotly/layer-line {:=y :sqdiff
                            :=name "squared difference"})
        (plotly/layer-point {:=y :peak
                             :=name "peak"}))))


(defn extract-ppi
  "Extract peak-to-peak intervals from ECG signal.
  Returns dataset with columns: :t (time in seconds), :ppi (interval in seconds)"
  [{:keys [subject-id row-interval]}]
  (let [bw (Butterworth. WESAD-sampling-rate)
        raw (labelled-dataset subject-id)
        pipeline (-> raw
                     (cond-> row-interval
                       (tc/select-rows (apply range row-interval)))
                     ;; Bandpass filter 5-15 Hz for QRS detection
                     (tc/add-column :filtered
                                    #(.bandPassFilter bw
                                                      (double-array (:ECG %))
                                                      4 5 15))
                     ;; Differentiate and square
                     (tc/add-column :sqdiff
                                    #(tcc/- (:filtered %)
                                            (tcc/shift (:filtered %) 1))))
        ;; Find peaks with distance constraint (200 samples = ~0.29s)
        peak-indices (find-peaks (:sqdiff pipeline)
                                 {:distance 200})
        peak-times (tcc/* peak-indices (/ 1.0 WESAD-sampling-rate))]
    (-> {:t peak-times}
        tc/dataset
        ;; Calculate peak-to-peak intervals
        (tc/add-column :ppi #(tcc/* 1000
                                    (tcc/- (:t %)
                                           (tcc/shift (:t %) 1))))
        (tc/drop-rows [0]))))

;; ### Plotting the PPI

(delay
  (-> (extract-ppi {:subject-id 5
                    :row-interval [0 200000]})
      (plotly/layer-bar {:=x :t
                         :=y :ppi})))

(delay
  (-> (extract-ppi {:subject-id 5})
      (plotly/layer-bar {:=x :t
                         :=y :ppi})))

;; ### Measures again

(def WESAD-spectrograms
  (memoize
   (fn [{:keys [ppi-params measures-params]}]
     (-> ppi-params
         extract-ppi
         (compute-measures measures-params)))))


(delay
  (-> {:ppi-params {:subject-id 5
                    :row-interval [0 1000000]}
       :measures-params {:sampling-rate 10
                         :window-size-in-sec 120}}
      WESAD-spectrograms
      plot-with-measures))

;; ## A subject's journey

;; The various phases of the WESAD experiments:

(def id->label
  [:transient, :baseline,
   :stress, :amusement, :meditation,
   :ignore :ignore :ignore])

(def label-intervals
  (memoize
   (fn [subject]
     (-> (labelled-dataset subject)
         :label
         (->> (partition-by identity)
              (map (fn [part]
                     [(-> part first int id->label)
                      (count part)])))
         tc/dataset
         (tc/rename-columns [:label :n])
         (tc/add-column :offset #(cons 0 (reductions + (:n %))))
         (tc/select-columns [:offset :n :label])))))


(delay
  (label-intervals 5))


(delay
  (let [subject 5]
    (kind/fragment
     (-> (label-intervals subject)
         (tc/select-rows #(not= (:label %) :ignore))
         #_(tc/select-rows #(= (:label %) :meditation))
         (tc/rows :as-maps)
         (->> (mapcat (fn [{:keys [offset n label]}]
                        [label
                         (try (-> {:ppi-params {:subject-id subject
                                                :row-interval [offset (+ offset n)]}
                                   :measures-params {:sampling-rate 10
                                                     :window-size-in-sec 120}}
                                  WESAD-spectrograms
                                  plot-with-measures)
                              (catch Exception e 'unavailable))])))))))
