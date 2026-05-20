^{:kindly/hide-code true
  :clay             {:title  "Pedestrian counts in cities"
                     :external-requirements [] ;; confirm - this is how to take pre-rendered .qmd as per: https://clojurecivitas.github.io/scicloj/clay/skip_if_unchanged_example.html ?
                     :quarto {:author   [:pez
                                         :antonzhyliuk
                                         :tomas81508
                                         :chrysophylax
                                         :dts
                                         :daslu
                                         :dzepat]
                              :type     :post
                              :date     "2026-05-20"
                              :category :gis
                              :tags     [:gis]
                              :draft true}}}
(ns gis.pedestrian-counts)


;; This is a draft from Stockholm Clojure Café, May 20th 2026.



(ns gis.pedestrian-counts
  (:require [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [scicloj.plotje.api :as pj]
            [tech.v3.datatype.datetime :as datetime]
            [scicloj.kindly.v4.kind :as kind]
            [clojure.string :as str]))


(def data-path
  "data/kaggle/nordcauc-urban-mobility-intelligence/pedestrian_flow_counters.csv.gz")



(def ped-data
  (-> data-path
      (tc/dataset {:key-fn keyword})))


ped-data

(type ped-data)

(map? ped-data)

(-> ped-data
    (tc/select-rows (fn [row]
                      (-> row :counter_id (= "PD-ST-0001"))))
    (tc/select-columns [:hour :total_count_hourly])
    pj/pose)


(-> ped-data
    (tc/select-rows (fn [row]
                      (-> row :counter_id (= "PD-ST-0001"))))
    (tc/group-by [:hour])
    (tc/aggregate {:avg-count (fn [ds]
                                (-> ds
                                    :total_count_hourly
                                    tcc/mean))})
    pj/pose
    pj/lay-line
    pj/lay-point)




(-> ped-data
    (tc/select-rows (fn [row]
                      (-> row :counter_id (= "PD-ST-0001"))))
    (tc/group-by [:season :hour])
    (tc/aggregate {:avg-count (fn [ds]
                                (-> ds
                                    :total_count_hourly
                                    tcc/mean))})
    (pj/pose :hour :avg-count {:color :season})
    pj/lay-line)


(-> ped-data
    (tc/group-by [:counter_id] {:result-type :as-seq})
    (->> (sort-by (fn [station-counts]
                    (-> station-counts
                        (tc/rows :as-maps)
                        first
                        :location_type)))
         (map (fn [station-counts]
                (-> station-counts
                    (tc/group-by [:season :hour])
                    (tc/aggregate {:avg-count (fn [ds]
                                                (-> ds
                                                    :total_count_hourly
                                                    tcc/mean))})
                    (pj/pose :hour :avg-count {:color :season})
                    pj/lay-line
                    (pj/options {:title (-> station-counts
                                            (tc/rows :as-maps)
                                            first
                                            ((juxt :city :district :location_type))
                                            (->> (str/join " ")))}))))))



(-> ped-data
    (tc/group-by [:counter_id] {:result-type :as-seq})
    (->> (sort-by (fn [station-counts]
                    (-> station-counts
                        (tc/rows :as-maps)
                        first
                        :location_type)))
         (map (fn [station-counts]
                (let [first-row (-> station-counts
                                    (tc/rows :as-maps)
                                    first)
                      center [(:latitude first-row)
                              (:longitude first-row)]]
                  (kind/fragment
                   [(kind/reagent
                     ['(fn [input-center]
                         [:div {:style {:height "200px"}
                                :ref (fn [el]
                                       (let [m (-> js/L
                                                   (.map el)
                                                   (.setView (clj->js 
                                                              input-center)
                                                             14))]
                                         (-> js/L
                                             .-tileLayer
                                             (.provider "Stadia.AlidadeSmooth")
                                             (.addTo m))
                                         (-> js/L
                                             (.marker (clj->js input-center))
                                             (.addTo m))))}])
                      center]
                     ;; Note we need to mention the dependency:
                     {:html/deps [:leaflet]})
                    (-> station-counts
                        (tc/group-by [:season :hour])
                        (tc/aggregate {:avg-count (fn [ds]
                                                    (-> ds
                                                        :total_count_hourly
                                                        tcc/mean))})
                        (pj/pose :hour :avg-count {:color :season})
                        pj/lay-line
                        (pj/options {:title (->> first-row
                                                 ((juxt :city :district :location_type))
                                                 (str/join " "))}))]))))
         kind/fragment))












