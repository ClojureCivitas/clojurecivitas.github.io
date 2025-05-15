(ns civitas.metadata
  (:require [babashka.fs :as fs]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [markdown.core :as md]))

(defn source-path-for [{:keys [topics id] :as notebook}]
  {:pre [id (seq topics)]}
  (str (fs/path "notebooks"
                (str (symbol (first topics)))
                (str id ".md"))))

(defn spit-md [notebook]
  (str "---\n" (yaml/generate-string notebook) "---\n"))

(defn spit-notebook [{:keys [source-path] :as notebook}]
  (let [source-path (or source-path (source-path-for notebook))]
    (if (fs/exists? source-path)
      (println source-path "exists")
      (do
        (io/make-parents source-path)
        #_(spit source-path notebook)
        (println source-path "created")))))

(defn spit-all [notebooks]
  (run! spit-notebook notebooks))


(defn find-mds [{:keys [site-dir]}]
  (map str (fs/glob site-dir "**.md")))

(defn front-matter [md-file]
  (-> (slurp md-file)
      (md/md-to-meta)
      (dissoc :format :code-block-background)
      (assoc :source-path md-file
             :base-source-path nil
             :id (-> (fs/file-name md-file)
                     (fs/strip-ext)))))

(defn front-matters [opts]
  (map front-matter (find-mds opts)))
