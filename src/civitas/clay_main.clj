(ns civitas.clay-main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.util.mime-type :as mime-type]
            [scicloj.clay.v2.main :as clay-main]
            [scicloj.clay.v2.server :as clay-server]
            [scicloj.clay.v2.server.state :as server-state])
  (:import (java.io File)))

(def default-preview-source
  "language_learning/vocabulary_estimation/bayes_theorem_simulations.clj")

(def preview-root "temp")
(def published-root "site/_site")

(defn- regular-file-under [root uri]
  (when (and (string? uri) (str/starts-with? uri "/"))
    (let [root-file (.getCanonicalFile (io/file root))
          file (.getCanonicalFile (io/file root-file (subs uri 1)))
          root-prefix (str (.getPath root-file) File/separator)]
      (when (and (str/starts-with? (.getPath file) root-prefix)
                 (.isFile file))
        file))))

(defn- current-preview-uri []
  (let [{:keys [base-target-path full-target-path]}
        (:last-rendered-spec @server-state/*state)]
    (when (and base-target-path full-target-path)
      (str "/" (clay-server/relative-url-path base-target-path
                                              full-target-path)))))

(defn preview-fallback-handler [{:keys [request-method uri]}]
  (let [preview-file (regular-file-under preview-root uri)
        linked-html? (and (str/ends-with? uri ".html")
                          (not= uri (current-preview-uri)))]
    (when (and (= :get request-method)
               (or (nil? preview-file) linked-html?))
      (when-let [file (regular-file-under published-root uri)]
        {:status 200
         :headers {"Content-Type"
                   (or (mime-type/ext-mime-type uri)
                       "application/octet-stream")}
         :body (if (str/ends-with? uri ".html")
                 (clay-server/wrap-html (slurp file) @server-state/*state)
                 file)}))))

(defn args-with-default-source [args]
  (if (seq args)
    args
    [default-preview-source]))

(defn -main [& args]
  (clay-server/install-handler! #'preview-fallback-handler)
  (apply clay-main/-main (args-with-default-source args)))
