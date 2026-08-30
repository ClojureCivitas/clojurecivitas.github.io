^{:kindly/hide-code true
  :clay {:title "A featherweight introduction to datasets and plots for the Norwegian Clojurians"
         :quarto {:author [:com.github/teodorlu :daslu]
                  :description "Meetup notes"
                  :category :clojure
                  :type :post
                  :date "2025-12-03"
                  :tags [:dsp :math :gapminder]
                  :draft true}}}
(ns clojure-norway.meetup-2025-12
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.metamorph.ml.rdatasets :as rdatasets]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tablecloth.api :as tc]
            [tech.v3.dataset :as ds]))

;; This document serves as a featherweight introduction to Tablecloth datasets
;; and Tableplot plotting for programmers familiar with Clojure, but unfamiliar
;; with Data Science.

;; We will pick a real problem for our learning endevaours:
;;
;; > Has GDP per capita improved since 1950?
;;
;; This is a big question, and we will only provide the beginning of an answer.

(def ds
  (-> (rdatasets/gapminder-gapminder)
      (tc/order-by :year)))

;; Our source data is the [gapminder dataset](https://www.gapminder.org/data/).
;; Luckily for us, the gapminder dataset is available as clean Clojure data!
;;
;; Let's have a peek:

(ds/head ds)
(count (ds/rows ds))

;; We observe,
;; - each country+year is it's own row
;; - there are lots of rows

;; To see better, let's plot each country along its own line.

(-> ds
    (plotly/layer-line {:=x :year
                        :=y :gdp-percap
                        :=color :country}))

;; We expected GDP per capita to *increase*.
;; But there's a sharply decreasing line!
;; Hovering our cursor over the line, we see that it's Kuwait.
;;
;; Let's investigate.
;; First, let's narrow our focus to only Kuwait.

(def kuwait? (comp #{"Kuwait"} :country))

(def ds-kuwait
  (-> ds
      (tc/order-by [:country :year])
      (tc/select-rows kuwait?)))

;; First, we repeat GDP per capita per year, only for Kuwait.

(-> ds-kuwait
    (plotly/layer-line {:=x :year
                        :=y :gdp-percap
                        }))

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
