(ns cesiumdemo.etl
  (:require [cesiumdemo.time :as time]))
;;assuming we have an external var named FireData that's
;;full of json objects...

(defn fire-js->fire-data [fire]
  (let [obj (->> (js->clj fire)
                 (reduce-kv (fn [acc k v] (assoc acc (keyword k) v)) {}))]
    (-> obj
        (assoc :stop  (js/Date. (obj :Control_DateTime)))
        (assoc :start (js/Date. (obj :Discover_DateTime))))))

(defn validate-data [{:keys [start stop] :as r}]
  (if (< start stop)
    r
    (assoc r :start (time/add-days stop -1))))

(def fire-data
  (->> js/FireData
       (into []
             (map-indexed (fn [idx m]
                            (-> m
                                fire-js->fire-data
                                (assoc :id idx)
                                validate-data))))))
