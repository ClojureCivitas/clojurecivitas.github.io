^{:clay {:quarto {:draft true}}}
(ns civitas.explorer.db
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.walk :as walk]
            [tablecloth.api :as tc]
            [clj-yaml.core :as yaml]))

(walk/postwalk
  (fn [x]
    (cond (map? x) (into {} x)
          (seq? x) (into [] x)
          :else x))
  (yaml/parse-string (slurp "site/_quarto.yml")))

;; TODO: it might be more convenient to use Quarto to gather the metadata
;; ```
;; quarto list --to json
;; ```

(def db-file "site/db.edn")

(defn spit-edn [f content]
  (spit f (with-out-str (pprint/pprint content))))

(defn slurp-edn [f]
  (edn/read-string (slurp f)))

(def db (atom (slurp-edn db-file)))

;; TODO: what if the front matter doesn't match existing?

(defn set-notebooks [notebooks]
  (->> {:notebook notebooks}
       (reset! db)
       (spit-edn db-file)))

(defn index-by
  "Return a map where a key is (f item) and a value is item."
  [f coll]
  (persistent!
    (reduce
      (fn [ret x]
        (assoc! ret (f x) x))
      (transient {}) coll)))

(defn notebooks-ds []
  (tc/dataset (:notebook @db)))

(defn notebooks []
  (:notebook @db))

(defn authors []
  (tc/dataset (:author @db)))

(defn topic-ds []
  (tc/dataset (:topic @db)))

(defn topics []
  (:topic @db))

;; TODO: this is a terrible way to do it
(def get-topic
  (index-by :id (:topic @db)))

(defn get-notebooks-by-topic []
  (-> (group-by (comp first :topic) (:notebook @db))
      (update-vals #(map-indexed (fn [idx x]
                                   (assoc x :position idx))
                                 %))))

(def get-colors
  (vec (keep :color (:topic @db))))
