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


(require '[scicloj.kindly.v4.kind :as kind]
         '[tech.v3.datatype :as dtype]
         '[tech.v3.datatype.functional :as dfn]
         '[clojure.math :as math]
         '[tablecloth.api :as tc]
         '[scicloj.tableplot.v1.plotly :as plotly])

(def sample-rate 44100.0)



(def example-wave
  (let [duration 10
        num-samples (* duration sample-rate)
        time (dtype/make-reader :float32
                                num-samples
                                (/ idx sample-rate))
        freq 440
        amp 3800
        value (-> time
                  (dfn/* (* 2 Math/PI freq))
                  dfn/sin
                  (dfn/* amp))]
    (tc/dataset {:time time
                 :value value})))

example-wave

(-> example-wave
    (tc/head 200)
    (plotly/layer-line {:=x :time
                        :=y :value}))

(defn audio [samples]
  (with-meta
    {:samples samples
     :sample-rate sample-rate}
    {:kind/audio true}))

(-> example-wave
    :value
    audio)

(def violin-components
  [[:A4 440 3800]
   [:A5 880 2750]
   [:E6 1320 600]
   [:A6 1760 700]
   [:C#7 2200 1900]])

(def violin-components-dataset
  (let [duration 10
        num-samples (* duration sample-rate)
        time (dtype/make-reader :float32
                                num-samples
                                (/ idx sample-rate))]
    (->> violin-components
         (map (fn [[label freq amp]]
                [label (-> time
                           (dfn/* (* 2 math/PI freq))
                           dfn/sin
                           (dfn/* amp))]))
         (into {:time time})
         tc/dataset)))

violin-components-dataset

(-> violin-components-dataset
    (tc/head 200)
    (plotly/layer-line {:=x :time
                        :=y :A4}))

(-> violin-components-dataset
    (tc/head 200)
    (tc/pivot->longer (complement #{:time})))


(-> violin-components-dataset
    (tc/head 200)
    (tc/pivot->longer (complement #{:time}))
    (tc/rename-columns {:$value :value})
    (plotly/layer-line {:=x :time
                        :=y :value
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
    (tc/head 200)
    (plotly/layer-line {:=x :time
                        :=y :violin}))

(-> violin-dataset
    :violin
    (dfn// 7000.0)
    audio)




