(ns civitas.make
  (:require [civitas.db :as db]
            [civitas.metadata :as metadata]
            [scicloj.clay.v2.api :as clay]))

;; IDEA: maybe make a snippet make-quarto-site!
;; IDEA: could also make a make-html-site! (but it wouldn't find markdown files obviously)
(def markdown-opts
  {:render true
   :base-target-path "site"
   :format [:quarto :html]
   :run-quarto false
   :hide-info-line false})

;; TODO: how to avoid local javascript for every page?
(defn make-qmd
  ([] (clay/make! markdown-opts))
  ([src] (clay/make! (assoc markdown-opts :source-path src))))

(defn make-all
  []
  (make-qmd)
  ;; TODO: db needs to be idempotent with existing resources
  #_(db/set-notebooks (metadata/front-matters opts)))

(comment
  (make-qmd))
