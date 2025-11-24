^{:kindly/hide-code true
  :clay
  {:title  "Introduction to Violin Plots with Vega"
   :quarto {:author      :mt
            :description "..."
            :image       "overlayplot.png"
            :type        :post
            :date        "2025-11-24"
            :category    :clojure
            :tags        [:dataviz :vega-lite]}}}

(ns scicloj.data-visualization.violin
  (:require
   [clojure.data.json :as json]
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind]
   ))


;;; Get the movie dataset
(def movie-data
  (json/read-str (slurp  "https://vega.github.io/editor/data/movies.json") ))


;;; Make a simple boxplot showing US Gross by Genre
(kind/vega-lite
 {:mark {:type "boxplot" :tooltip {:content "data"}},
  :data {:values  movie-data}
  :encoding
  {"color" {:field "Major Genre", :type "nominal" :legend false},
   "y" {:field "Major Genre",
        :type "nominal"},
   "x" {:field "US Gross",
        :type "quantitative"}
   :tooltip {:field "Title"
             ;; :type "quantitative"
             }},
  :width 800
  })


;; That's nice, but how can we add violins to this?
(kind/vega-lite
 {:mark {:type "area"},
  :data {:values movie-data}
  :transform [{:density "US Gross"
               :groupby ["Major Genre"]
               :extent [0, 200000000]}]
  :encoding
  {"color" {:field "Major Genre", :type "nominal" :legend false},
   "y" {:field "density",
        :type "quantitative"
        :stack "center"
        :axis false
        },               ;this reflect-doubles the area plot to produce the violin shape
   "x" {:field "value",
        :type "quantitative"}
   "facet" {:field "Major Genre"
            :type "nominal"
            :columns 1
            :spacing 0
            :legend :left
            }
   },
  :width 800
  })






