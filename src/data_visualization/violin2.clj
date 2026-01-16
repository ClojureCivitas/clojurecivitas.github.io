^{:kindly/hide-code true
  :clay
  {:title  "Part 2 Violin Plots Variations"
   :quarto {:author      :mt
            :description "..."
            :image       "violin-example.png" ;TODO
            :type        :post
            :date        "2026-01-05"
            :category    :clojure
            :tags        [:dataviz :vega-lite]}}}

(ns scicloj.data-visualization.violin2
  (:require
   [clojure.data.json :as json]
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [hyperphor.multitool.core :as mu]
   ))


;; # The World's Smallest Violin (plot generating code), Part 2


;;; [Link to Part 1]()


^:kindly/hide-code ^:kind/hidden
(def penguin-data-url
  "https://raw.githubusercontent.com/ttimbers/palmerpenguins/refs/heads/file-variants/inst/extdata/penguins.tsv")

^:kindly/hide-code ^:kind/hidden
(def penguin-data
  (tc/dataset penguin-data-url {:key-fn keyword}))




;;; # Abstraction

;;; Once we know how to make a visualization, it makes sense to abstract it into a procedure, so this knowledge in a function. 


;;; So lets do that for dot plot. We'll make a function that takes three required objects: `data`, `value-field`, and `group-field`, along with some options. 

(defn dot-plot
  [data value-field group-field
   & {:keys [jitter? color-field]}]
  {:mark {:type "point" :tooltip {:content :data}}
   :data data
   :transform [{:calculate "random()" :as "jitter"}]
   :encoding
   {:x {:field value-field
        :type :quantitative
        :scale {:zero false}}
    :row {:field group-field
          :type :nominal
          :header {:labelAngle 0 :labelAlign "left"}
          :spacing 0}
    :y {:field (if jitter? "jitter" nil)
        :type :quantitative
        :axis false}
    :color {:field (or color-field group-field)
            :type :nominal
            :legend (if color-field true false)}}
   :height 50
   :width 800
   })

;;; Which can be used like this (here we'll look some differen attributes)

^:kind/vega-lite
(dot-plot {:values (tc/rows penguin-data :as-maps)}
          "body_mass_g" "sex"
          :jitter? true
          :color-field "species island"
          )


;;; And we can easily reuse the function on a different data set (this one is about movies)

^:kind/vega-lite
(dot-plot {:url "https://vega.github.io/editor/data/movies.json"}
          "US Gross" "Major Genre"
          :jitter? true)



;;; # Generalize

;;; This section introduces a new, and somewhat funky way of generalizing Vega specs.

;;; Take our dot-plot abstraction above. We could parameterize it further, eg by adding optional arguments for height or scale or any of the many things Vega allows you to tweak.  

;; But instead, we're going to introduce a much more general (if somewhat unclean) way of modifying a base Vega spec – through structural merge. This makes use of a function `mu/merge-recursive` from the  (Multitool utility library)[https://github.com/hyperphor/multitool/blob/9e10c6b9cfe7f1deb496e842fc12505748a09d69/src/cljc/hyperphor/multitool/core.cljc#L1012]. This function m merges arbitrarily nested structures. This means we can alter any aspect of the spec, at the cost of having to have some knowledge of its structure. 

(defn dot-plot-g
  [data value-field group-field overrides]
  (mu/merge-recursive
   (dot-plot data value-field group-field false)
   overrides))


^:kind/vega-lite
(dot-plot-g {:values (tc/rows penguin-data :as-maps)}
            "body_mass_g" "sex"
            {:mark {:filled true}}
            )



;;; Next, an abstraction for violin plots. This could also be built from dot-plot-2, but since theres a lot to change let's not.


(defn violin-plot
  [data value-field group-field & bandwidth]
 {:mark {:type :area}
  :data data
  :transform [{:density value-field
               :groupby [group-field]
               :bandwidth bandwidth
               }]
  :encoding
  {:color {:field group-field
           :type :nominal
           :legend false}
   :x {:field "value"
       :type :quantitative
       :title value-field
       :scale {:zero false}}
   :y {:field "density"
       :type :quantitative
       :stack :center                   ; this reflects the area plot to produce the violin shape. 
       :axis false                      ; hide some labels
       } 
   :row {:field group-field
         :type :nominal
         :spacing 0
         :header {:labelAngle 0 :labelAlign :left}
         }
   }
  :height 50                            ;this is the height of each row (facet)
  :width 800
  })


^:kind/vega-lite
(-> (violin-plot {:url "https://vega.github.io/editor/data/movies.json"}
                 "US Gross" "Major Genre" 5000000)
    (mu/merge-recursive {:encoding {:x {:scale {:domain [0 100000000]}}} ;force scale to exclude outliers
                      :mark {:clip true}}))                           ;and don't plot them



;;; # Some more variations







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

;;; # Here's a more scientific example

(def iris-url "https://gist.githubusercontent.com/curran/a08a1080b88344b0c8a7/raw/0e7a9b0a5d22642a06d3d5b9bcbad9890c8ee534/iris.csv")

^:kind/vega-lite
(violin-plot {:url iris-url
              :format "csv"}
             "petal_width" "species"
             )

;;; ## Let's flip the orientation with a hack
(defn swap-keys
  [struct keys]
  (let [remap (mu/map-bidirectional keys)]
    (mu/walk-map-entries
     (fn [[k v]]
       [(get remap k k) v])
     struct)))


^:kind/vega-lite
(-> (violin-plot {:url iris-url
                  :format "csv"}
                 "petal_width" "species"
                 )
    (swap-keys {:x :y
                :row :column
                :rows :columns
                :height :width
                })
    (mu/merge-recursive
     {:mark {:orient "horizontal"}
      }))

^:kind/vega-lite
(-> (violin-plot {:url "https://vega.github.io/editor/data/movies.json"}
                   "US Gross" "Major Genre"
                   )

        (mu/merge-recursive {:encoding {:x {:scale {:domain [0 100000000]}}} ;force scale to exclude outliers
                         :mark {:clip true}})

    (swap-keys {:x :y
                :row :column
                :rows :columns
                :height :width
                })
    (mu/merge-recursive
     {:mark {:orient "horizontal"}
      :encoding {:column {:header nil}}}))


;;; TODO controls
;;; TODO layering
;;; TODO more options


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
          :type :nominal
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
         :type :quantitative
         :scale {:domain [min max]}}
     }    
    
    :layer
    [{:mark {:type "point" :tooltip {:content :data}}
      :transform [{:filter (format "datum['%s'] != 'NA'" value-field)}
                  {:calculate "random()" :as "jitter"}]

      :encoding
      {:color {:value "gray"}
       :y {:field "jitter"
           :type :quantitative
           :axis false}
       }}

     ;; box layer
     ;; TODO  widen box
     {:mark {:type "boxplot"
             :outliers false}           ;turn off outlier points since we draw all points
      :encoding
      {:color {:field group-field :type :nominal :legend false}
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
(box-dot-plot {:url "https://vega.github.io/editor/data/movies.json"}
              "US Gross" "Major Genre"
              0 500000000)


;;; # Scrap

;;; # Get some data

;;; ## Movie Dataset



;;; Here we'll take a look at a sample of the data (selected columns and just a few rows).



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


