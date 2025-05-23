(ns civitas.make
  (:require [civitas.db :as db]
            [civitas.metadata :as metadata]
            [scicloj.clay.v2.api :as clay]))

(def markdown-opts
  {:merge-aliases [:markdown]})

;; TODO: how to avoid local javascript for every page?
(defn make-qmd
  ([] (clay/make! markdown-opts))
  ([src] (clay/make! (assoc markdown-opts :source-path src))))

(defn make-all
  []
  (make-qmd)
  (metadata/front-matters {:site-dir "site"})
  ;; TODO: db needs to be idempotent with existing resources
  #_(db/set-notebooks (metadata/front-matters opts)))

(comment
  (make-qmd))
