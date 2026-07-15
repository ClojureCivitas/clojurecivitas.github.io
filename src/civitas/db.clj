^{:clay {:quarto {:draft true}}}
(ns civitas.db
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.walk :as walk]
            [scicloj.kindly.v4.api :as kindly]
            [tablecloth.api :as tc]
            [clj-yaml.core :as yaml]))

(defn quarto []
  (walk/postwalk
   (fn [x]
     (cond (map? x) (into {} x)
           (seq? x) (into [] x)
           :else x))
   (yaml/parse-string (slurp "site/_quarto.yml"))))

(def db-file "site/db.edn")

(defn spit-edn [f content]
  (spit f (with-out-str (pprint/pprint content))))

(defn slurp-edn [f]
  (edn/read-string (slurp f)))

(def db (atom (slurp-edn db-file)))

(defn index-by
  "Return a map where a key is (f item) and a value is item."
  [f coll]
  (persistent!
   (reduce
    (fn [ret x]
      (assoc! ret (f x) x))
    (transient {}) coll)))

(defn author-replacements
  "Creates a map of author and affiliation keys to their full definition"
  []
  (-> (mapcat @db [:author :affiliation])
      (->> (index-by :id))
      (update-vals #(dissoc % :id))))

(defn- ns-clay-config [ns-form]
  (let [[_ sym ?doc ?attr] ns-form]
    (kindly/deep-merge
     (some-> ns-form meta :clay)
     (some-> sym meta :clay)
     (:clay (cond
              (map? ?doc) ?doc
              (map? ?attr) ?attr)))))

(defn- restore-replaced-quarto-config [config]
  (let [quarto (:quarto config)
        ns-quarto (some-> config :ns-form ns-clay-config :quarto)]
    (if (and ns-quarto (-> quarto meta :replace))
      (assoc config :quarto
             (kindly/deep-merge ns-quarto (with-meta quarto nil)))
      config)))

(defn expand-authors
  "Hook for Clay to update ns metadata configuration"
  [config]
  (-> config
      restore-replaced-quarto-config
      (update :quarto #(walk/prewalk-replace (author-replacements) %))))

;; TODO: what if the front matter doesn't match existing?

(defn set-notebooks [notebooks]
  (->> {:notebook notebooks}
       (reset! db)
       (spit-edn db-file)))

(defn notebooks-ds []
  (tc/dataset (:notebook @db)))

(defn notebooks []
  (:notebook @db))

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
