^{:kindly/hide-code true
  :clay             {:title  "DSP Intro"
                     :quarto {:author []
                              :description "Starting the journey of DSP in Clojure."
                              :category    :clojure
                              :type        :post
                              :date        "2025-11-02"
                              :tags        [:dsp :math :music]
                              :draft       true}}}
(ns signal-processing.intro
  (:require [scicloj.kindly.v4.kind :as kind]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [clojure.math :as math]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]))


(defn sine-samples [{:keys [sample-rate duration frequency amplitude]
                     :or {amplitude 0.5}}]
  (let [num-samples (int (* sample-rate duration))
        ;; Generate samples as int16 reader
        ]
    (for [idx (range num-samples)]
      (let [angle (* 2.0 Math/PI idx frequency (/ 1.0 sample-rate))]
        (* amplitude (Math/sin angle))))))


(defn sample-info [samples sample-rate]
  (with-meta
    {:samples samples
     :sample-rate sample-rate}
    {:kind/audio true}))


(let [sample-rate 44100.0
      A 440.0
      E 659.25
      wave (fn [freq]
             (sine-samples
              {:sample-rate sample-rate
               :duration 3
               :frequency freq
               :amplitude 0.3}))]
  (sample-info (map +
                    (wave A)
                    (wave E))
               sample-rate))


(require '[tech.v3.datatype :as dtype]
         '[tech.v3.datatype.functional :as dfn]
         '[clojure.math :as math]
         '[tablecloth.api :as tc]
         '[scicloj.tableplot.v1.plotly :as plotly])


(def violin-components
  [[:A4 440 3800]
   [:A5 880 2750]
   [:E6 1320 600]
   [:A6 1760 700]
   [:C#7 2200 1900]])

(def sample-rate 44100.0)

(def violin-components-dataset
  (let [duration 10
        num-samples (* duration sample-rate)
        time (dtype/make-reader :float32
                                num-samples
                                (/ idx sample-rate))]
    (->> violin-components
         (map (fn [[label freq amp]]
                [label (-> time
                           (dfn/* (* 2 Math/PI freq))
                           dfn/sin
                           (dfn/* amp))]))
         (into {:time time})
         tc/dataset)))


violin-components-dataset


(-> violin-components-dataset
    (tc/head 1000)
    (plotly/layer-line {:=x :time
                        :=y :A4}))


(-> violin-components-dataset
    (tc/head 100)
    (tc/pivot->longer (complement #{:time}))
    (plotly/layer-line {:=x :time
                        :=y :$value
                        :=color :$column}))

(def violin-dataset
  (-> violin-components-dataset
      (tc/add-column :violin
                     #(dfn/+ (:A4 %)
                             (:A5 %)
                             (:E6 %)
                             (:A6 %)
                             (:C#7 %)))))


(-> violin-dataset
    (tc/head 400)
    (plotly/layer-line {:=x :time
                        :=y :violin}))


(sample-info (dfn// (:violin violin-dataset)
                    7000.0)
             sample-rate)


