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
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind]
   ))

;;; I can't stand writing long text in ;;; comments, so import it from actual .md files
^{:kind/md true :kindly/hide-code true}
(slurp "src/data_visualization/violin1.md")

;;; # Get some data

;;; ## Movie Dataset

;;; Get the movie dataset
(def movie-data
  (json/read-str (slurp "https://vega.github.io/editor/data/movies.json") ))

;;; Here we'll take a look at a sample of the data (selected columns and just a few rows).
(kind/table
 {:row-maps (take 10 movie-data)
  :column-names ["Title" "IMDB Rating" "US Gross" "Distributor" "Production Budget" "MPAA Rating" "Major Genre"]})


;;; ## Penguin dataset

(def penguin-data-url "https://raw.githubusercontent.com/ttimbers/palmerpenguins/refs/heads/file-variants/inst/extdata/penguins.tsv")

(def penguin-data
  (tc/dataset penguin-data-url {:key-fn keyword}))

(kind/table (tc/random penguin-data 10))

;;; # Boxplot


;;; A basic boxplot shows the distribution of a single varianle. Here we look at the distribution of US gross profits:
^:kind/vega-lite
 {:mark {:type "boxplot"}
  :data {:values movie-data}
  :encoding
  {"x" {:field "US Gross"
        :type "quantitative"}
   :tooltip {:field "Title"}}
  :width 800
  }






;;; But boxplots are more useful when you compare distributions given a second variable. Here we see the different distributions for specific genres of movie.

^:kind/vega-lite
 {:mark {:type "boxplot"}
  :data {:values movie-data}
  :encoding
  {"x" {:field "US Gross"
        :type "quantitative"}
   "y" {:field "Major Genre"
        :type "nominal"}
   "color" {:field "Major Genre" :type "nominal" :legend false}
   :tooltip {:field "Title"}}
  :width 800
  }

;;; This shows us the median (white line)

;;; # Violins


(kind/vega-lite
 {:mark {:type "area"}
  :data {:values movie-data}
  :transform [{:density "US Gross"
               :groupby ["Major Genre"]
               :extent [0 200000000]}]
  :height 50                            ;this is the height of each row (facet)
  :encoding
  {"color" {:field "Major Genre" :type "nominal" :legend false}
   "y" {:field "density"
        :type "quantitative"
        :stack "center"
        :axis false
        }               ;this reflect-doubles the area plot to produce the violin shape
   "x" {:field "value"
        :type "quantitative"}
   "row" {:field "Major Genre"
          :type "nominal"
          :columns 1
          :spacing 0
          :header {:labelAngle 0 :labelAlign "left"}
          }
   }
  :width 800
  })


(defn violin-plot
  [data value-field group-field min max]
  {:mark {:type "area"}
   :data data
   :transform [{:filter (format "datum['%s'] != 'NA'" value-field)}
               {:density value-field
                :groupby [group-field]
                ;; :bandwidth 1.0
                :extent [min max]}]
   :height 50                            ;this is the height of each row (facet)
   :encoding
   {:color {:field group-field :type "nominal" :legend false}
    :y {:field "density"
        :type "quantitative"
        :stack "center"
        :axis false
        }               ;this reflect-doubles the area plot to produce the violin shape
    :x {:field "value"
        :type "quantitative"}
    :row {:field group-field
          :type "nominal"
          :columns 1
          :spacing 0
          :header {:labelAngle 0 :labelAlign "left"}
          }
    }
   :width 800
   })

;;; TODO show box, whiskers, points
;;; TODO need a better dataset, this is boring

;;; # Here's a more scientific example

(def iris 
  (json/read-str (slurp "https://storage.googleapis.com/kagglesdsdata/datasets/20079/26025/iris.json?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Credential=gcp-kaggle-com%40kaggle-161607.iam.gserviceaccount.com%2F20251129%2Fauto%2Fstorage%2Fgoog4_request&X-Goog-Date=20251129T050349Z&X-Goog-Expires=259200&X-Goog-SignedHeaders=host&X-Goog-Signature=a076da9c0375641bed362393229356cd341ab694a356bbbadb6678f654ef58880b12de60a307cff229fc845e66a05acc14621bc4a6a022fc6419e0431327bc9b8105ca66e8289bd4b030825dfb5e0aaa7b0824bb9ebe9ed087c23329fb8a9259c86d0bccfdfe4da1f4d7ae84a91e14dc0df16aa011afecaa2daa1a96d83efc170e2d50758690b22e9b1fb289a476786d15f756e84724706c5581389462938de2a7d6d7ec38e20a7d7edc9b143ddef286e462f07c7827900a9e2130ca41cf21ce7da1e540d599d6bec333a0eae26af1532bf2ba745fd07e197226fb75795b1655aab3f62d097fa9be56a907e8c98601deb5c6c880e5ccc00617752ea92518f945")))

^:kind/vega-lite
(violin-plot {:values iris} "petalWidth" "species" 0 4)

^:kind/vega-lite
(violin-plot {:url penguin-data-url
              :format {:type "tsv"}
              }
             "flipper_length_mm" "species island"
             150 250)


;;; # Dotplot

(defn dot-plot
  [data value-field group-field min max]
  {:mark {:type "point" :tooltip {:content :data}}
   :data data
   :transform [{:filter (format "datum['%s'] != 'NA'" value-field)}
               {:calculate "random()" :as "jitter"}]
   :height 50                            ;this is the height of each row (facet)
   :encoding
   {:color {:field group-field :type "nominal" :legend false}
    :x {:field value-field
        :type "quantitative"
        :scale {:domain [min max]}}
    :y {:field "jitter"
        :type "quantitative"
        :axis false}
    :row {:field group-field
          :type "nominal"
          :columns 1
          :spacing 0
          :header {:labelAngle 0 :labelAlign "left"}
          }
    }
   :width 800
   })

^:kind/vega-lite
(dot-plot {:url penguin-data-url
           :format {:type "tsv"}
           }
          "flipper_length_mm" "species island"
          150 250
          )

;;; TODO vertical violins
;;; TODO controls
;;; TODO layering
;;; TODO more options
;;; TODO merge


;;; # Combining layers


(defn box-dot-plot
  [data value-field group-field min max]
  {
   ;; Data is in common
   :data data

   :facet
   {:row {:field group-field
          :type "nominal"
          :spacing 0                    ;??? not working
          :header {:labelAngle 0 :labelAlign "left"}
          }}

   :spec
   {
    :height 50                            ;this is the height of each row (facet)
    :encoding 
    {:x {:field value-field
         :type "quantitative"
         :scale {:domain [min max]}}
     }    
    
    :layer
    [{:mark {:type "point" :tooltip {:content :data}}
      :transform [{:filter (format "datum['%s'] != 'NA'" value-field)}
                  {:calculate "random()" :as "jitter"}]

      :encoding
      {:color {:value "gray"}
       :y {:field "jitter"
           :type "quantitative"
           :axis false}
       }}

     ;; box layer
     ;; TODO turn off outliers, widen box
     {:mark {:type "boxplot" :outliers false}
      :encoding
      {:color {:field group-field :type "nominal" :legend false}
       }}]
   :width 800
    }

   })



^:kind/vega-lite
(box-dot-plot {:url penguin-data-url
           :format {:type "tsv"}
           }
          "flipper_length_mm" "species island"
          150 250
          )
