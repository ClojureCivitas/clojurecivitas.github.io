^{:clay {:title  "Macroexpand 2025 Noj: Civitas Workshop"
         :quarto {:author      :timothypratley
                  :description "What is Civitas, why use it, and how to use it."
                  :type        :post
                  :date        "2025-10-17"
                  :category    :clay
                  :tags        [:clay :workflow]}}}
(ns scicloj.tableplot.ideas.macroexpand-workshop-tableplot
  (:require [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as tp]))

;; We built this code in the Civitas Workshop,
;; we selected Tableplot as the topic to write about.


;; Everything about Tableplot is inspired by R,
;; there everything is about tables - dataframes.
;; We hope to have our own version of that.

;; It is a limitation at the moment to require a dataset.

(def scatter-ds
  (tc/dataset {:x [1 2 3 4 5]
               :y [10 20 15 25 18]
               :z [1 2 1 2 1]}))

;; Why is the plot not Kindly annotated?
;; Tableplot returns annotated values

(-> scatter-ds
    (tp/base {:=title "Sample Scatter Plot"})
    (tp/layer-point {:=x :x
                     :=y :y}))

;; Why is the plot not Kindly annotated?
;; Tableplot returns annotated values

(-> scatter-ds
    (tp/base {:=title "Sample Scatter Plot"})
    (tp/layer-point {:=x :x
                     :=y :y})
    (meta))

;; The kind is `:kind/fn` which is a transformation.
;; Where is the function?

(-> scatter-ds
    (tp/base {:=title "Sample Scatter Plot"})
    (tp/layer-point {:=x :x
                     :=y :y})
    (:kindly/f))

;; hidden in the value is the `:kindly/f`.

;; Other types of plots:

;; using layer-bar instead of layer-point

(-> scatter-ds
    (tp/base {:=title "Sample Scatter Plot"})
    (tp/layer-bar {:=x :x
                   :=y :y}))

;; We can plot different columns of the dataset
;; as layers.

(-> scatter-ds
    (tp/base {:=title "Sample Scatter Plot"})
    (tp/layer-line {:=x :x
                    :=y :z})
    (tp/layer-bar {:=x :x
                   :=y :y}))

;; You don't need a big idea to write a Civitas post,
;; small explorations are welcome.
