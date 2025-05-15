(ns civitas.json-ld
  (:require [clojure.data.json :as json]))

(def ^:dynamic *ldns* "civitas")
(def ^:dynamic *base* "https://timothypratley.github.io/" *ldns* "/")

(defn notebook-jsonld [{:keys [id title topics level] :as resource}]
  {"@id"    (str *base* "resource/" id),
   "@type"  (str *ldns* ":Notebook"),
   "title"  title,
   "topics" (mapv #(str *ldns* "topic/" (name %)) topics),
   "level"  level})

(defn write-jsonld [target resources]
  (spit target
        (json/write-str
          {:context (str *base* "context.jsonld")
           :graph   (mapv notebook-jsonld resources)})))

(comment
  (write-jsonld "resources.jsonld" resources))
