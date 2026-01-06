^{:kindly/hide-code true
  :clay
  {:title  "Introduction to Violin Plots with Vega Lite"
   :quarto {:author      :mt
            :description "..."
            :image       "violin-example.png"
            :type        :post
            :date        "2026-01-05"
            :category    :clojure
            :tags        [:dataviz :vega-lite]}}}

(ns scicloj.data-visualization.violin
  (:require
   [clojure.data.json :as json]
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind]
   [clojure.java.io :as io]
   ))

;; # The World's Smallest Violin (plot generating code)


;;; This post will explain how to visualize data with violin plots, using Vega-Lite and Clojure. 



;; # What is a violin plot?

;; A [Violin plot](https://en.wikipedia.org/wiki/Violin_plot) is a way to visualize how data is distributed - essentially showing you where your data points fall and how spread out they are.is 

;; Imagine you're analyzing a dataset of movies and how much money they made. A violin plot would show you not just the median value, but the full "shape" of your data: Are values clustered around certain points, ore evenly spread out? How much concentration is there? How many such points?

;; A violin plot is best understood as an extension of the more common box plot. Violin plots add a visulization of the probability **density**, and can reveal more features of the data, such as multiple modes. This tutorial shows you how to make box plots and violin plots in Vega-Lite. 

^:kindly/hide-code
(import javax.imageio.ImageIO
        java.net.URL)

;;; TODO use movie example, and tweak bandwidth

^:kindly/hide-code
(->  "src/data_visualization/violin-example.png"
     io/file
     (ImageIO/read))

;;; If you want to see a full-fledged implementation of interactive violin plots for visualizaing biological data, [the BRUCE website](https://bruce.parkerici.org) has one. 



;;; # Data

;;; We'll use this classic [dataset about penguin morphology](https://github.com/ttimbers/palmerpenguins/blob/master/README.md). <img src='man/figures/logo.png' align="right" height="138.5" /></a>. Each row describes an individual penguin, with properties like species, sex, body mass, wing size.

(def penguin-data-url
  "https://raw.githubusercontent.com/ttimbers/palmerpenguins/refs/heads/file-variants/inst/extdata/penguins.tsv")

(def penguin-data
  (tc/dataset penguin-data-url {:key-fn keyword}))

(kind/table
 (tc/random penguin-data 10))

;;; # Just the points, ma'am

;;; Let's start off with a simple dot-plot. We'll group the data by species, and turn each value for body_mass into a point


;;; Vega really just requires specifying some basic mappings (encodings) between data and visual properties. This is a minimal version of dot plot:

^:kind/vega-lite
{:mark {:type "point"}
 :data {:values (tc/rows penguin-data :as-maps)}
 :encoding
 {:x {:field "body_mass_g"
      :type "quantitative"}
  :y {:field "species island"
      :type "nominal"}}
 }

;;; Vega's defaults are not always what we want, so this is the same as above with a bit of tweaking to look more like what we want. One nonobvious change: we use `:row` in place of `:y`. This is not estrictly necessary at this point, but will make it easier when we get to actual violin plots

^:kind/vega-lite
{:mark {:type "point" :tooltip {:content :data}}
 :data {:values (tc/rows penguin-data :as-maps)}
 :encoding
 {:x {:field "body_mass_g"
      :type "quantitative"
      :scale {:zero false}}
  :row {:field "species island"
        :type "nominal"
        :header {:labelAngle 0 :labelAlign "left"}
        :spacing 0
        }
  :color {:field "species island"
          :type "nominal"
          :legend false}  
  }
 :height 50
 :width 800
 }

;;; In this case the data is not very dense, so plotting everything on with a constant y value is OK. In a more polished application, you might want to add random y jitter to make the points more visually distinguishable.



;;; # Boxplot

;;; A boxplot is another way of displaying the distribution of a single numeric varianle. 
;;; A box plot summarizes a distribution of quantitative values using a set of summary statistics. The median tick in the box represents the median. The left and right parts of the box represent the first and third quartile respectively. The whisker shows the full domain of the data. 

^:kind/vega-lite
{:mark {:type :boxplot
        :extent :min-max}
 :data {:values (tc/rows penguin-data :as-maps)}
 :encoding
 {:x {:field "body_mass_g"
      :type :quantitative
      :scale {:zero false}}
  :y {:field "species island"
      :type :nominal}
  :color {:field "species island"
          :type "nominal"
          :legend false}}
 :height {:step 50}
 :width 800
 }



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





;;; # Violin Plot

;;; Violin plots extend the ida of a box plot. Isntead of showing just the mean and some percentiles, a violin plot shows the probability density as a continuous variables.

;;; ## Kernel Density Estimate


(kind/vega-lite
 {:mark {:type :area}
  :data {:values (tc/rows penguin-data :as-maps)}
  :transform [{:density "flipper_length_mm"
               :groupby ["species island"]
               :extent [150 250]
               :bandwidth 2
               }]
  :height 50                            ;this is the height of each row (facet)
  :encoding
  {:color {:field "species island"
           :type :nominal
           :legend false}
   :y {:field "density"
       :type :quantitative
       :stack :center                ; this reflects the area plot to produce the violin shape. 
       :axis false                  ; hide some labels
       } 
   :x {:field "value"
       :type :quantitative
       :scale {:zero false}}            ;not strictly necessary
   :row {:field "species island"
         :type :nominal
         :columns 1
         :spacing 0
         :header {:labelAngle 0 :labelAlign :left}
         }
   }
  :width 800
  })


;;; We can try some of the other properties

(kind/vega-lite
 {:mark {:type :area}
  :data {:values (tc/rows penguin-data :as-maps)}
  :transform [{:density "body_mass_g"
               :groupby ["species island"]
               :bandwidth 100
               :extent [2700 6300]}]
  :height 50                            ;this is the height of each row (facet)
  :encoding
  {:color {:field "species island"
           :type :nominal
           :legend false}
   :y {:field "density"
       :type :quantitative
       :stack :center                ; this reflects the area plot to produce the violin shape. 
       :axis false                  ; hide some labels
       } 
   :x {:field "value"
       :type :quantitative
       :scale {:zero false}}            ;not strictly necessary
   :row {:field "species island"
         :type :nominal
         :columns 1
         :spacing 0
         :header {:labelAngle 0 :labelAlign :left}
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

(def iris-url "https://gist.githubusercontent.com/curran/a08a1080b88344b0c8a7/raw/0e7a9b0a5d22642a06d3d5b9bcbad9890c8ee534/iris.csv")

^:kind/vega-lite
(violin-plot {:url iris-url
              :format "csv"}
             "petal_width" "species"
             0 4)

^:kind/vega-lite
 {:mark {:type "boxplot"}
  :data {:url iris-url
         :format "csv"}
  :encoding
  {"x" {:field "petal_width"
        :type "quantitative"}
   "y" {:field "species"
        :type "nominal"}
   "color" {:field "species" :type "nominal" :legend false}
   }
  :width 800
  }


^:kind/vega-lite
(violin-plot {:url penguin-data-url
              :format {:type "tsv"}
              }
             "flipper_length_mm" "species island"
             150 250)


;;; # Dotplot



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
   :config {:facet {:spacing 0}
            :view {:stroke nil}}
   :facet
   {:row {:field group-field
          :type "nominal"
          :header {:labelAngle 0
                   :labelAlign "left"
                   }
          }
    }

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
     ;; TODO  widen box
     {:mark {:type "boxplot"
             :outliers false}           ;turn off outlier points since we draw all points
      :encoding
      {:color {:field group-field :type "nominal" :legend false}
       }}]
    :width 800
    }

   })

^:kind/vega-lite
(box-dot-plot {:url penguin-data-url
               :format {:type "tsv"}}
              "flipper_length_mm" "species island"
              150 250)

;;; Let's try that for the movies

^:kind/vega-lite
(box-dot-plot {:values movie-data}
              "US Gross" "Major Genre"
              0 500000000)


;;; # Scrap

;;; # Get some data

;;; ## Movie Dataset

;;; Get the movie dataset
(def movie-data
  (json/read-str (slurp "https://vega.github.io/editor/data/movies.json") ))

;;; Here we'll take a look at a sample of the data (selected columns and just a few rows).
(kind/table
 {:row-maps (take 10 movie-data)
  :column-names ["Title" "IMDB Rating" "US Gross" "Distributor" "Production Budget" "MPAA Rating" "Major Genre"]})


;;; # Experiment with dataset choice

(def data (atom nil))

;;; ## Scittle min

(kind/scittle
 '(def geotiff-sources
    (js/ol.source.GeoTIFF.
     (clj->js
      {:sources [{:min 0
                  :nodata 0
                  :max 10000
                  :bands [1 ;; B02 blue (490nm)  -> band 1
                          2 ;; B03 green (560nm) -> band 2
                          3 ;; B04 red (665nm)   -> band 3
                          4 ;; B08 NIR (841nm)   -> band 4
                          ]
                  :url "https://s2downloads.eox.at/demo/Sentinel-2/3857/R10m.tif"}]}))))

;;; # References

;;; - [Violin Plots: A Box Plot-Density Trace Synergism](https://web.archive.org/web/20231106021405/https://quantixed.org/wp-content/uploads/2014/12/hintze_1998.pdf) Jerry L. Hintze, Ray D. Nelson


