(ns clojure-norway.meetup-2025-12
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.metamorph.ml.rdatasets :as rdatasets]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tablecloth.api :as tc]))

;; - Has GDP per capita improved since 1950?
;; - Are trends different per continent?

(def ds
  (-> (rdatasets/gapminder-gapminder)
      (tc/order-by :year)))

(-> ds
    (tc/order-by :year)
    (plotly/layer-line {:=x :year
                        :=y :gdp-percap
                        :=color :country}))

(def kuwait? (comp #{"Kuwait"} :country))

(def ds-kuwait
  (-> ds
      (tc/order-by [:country :year])
      (tc/select-rows kuwait?)))

;; Why does gdp per capita decrease in Kuwait?

(-> ds-kuwait
    (plotly/layer-line {:=x :year
                        :=y :gdp-percap})
    (plotly/layer-line {:=x :year
                        :=y :pop}))

;; Population has increased sharply.

;; What about total gdp?

(-> ds-kuwait
    (tc/* :gdp [:gdp-percap :pop])
    (plotly/layer-line {:=x :year
                        :=y :gdp}))

;; GDP has been rising, overall, even if GDP per capita has been falling.

(tc/head ds)

(defn select-continent [table continent]
  (-> table
      (tc/select-rows (comp #{continent} :continent))))

(defn view-continent [continent]
  [continent
   (-> ds
       (select-continent continent)
       (plotly/layer-line {:=x :year
                           :=y :gdp-percap
                           :=color :country}))])

(def continents
  (into (sorted-set)
        (tc/column ds :continent)))

continents

(mapv view-continent continents)
