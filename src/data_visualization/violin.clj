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
   [clojure.set :as set]
   [hyperphor.multitool.core :as mu]
   ))

;; # The World's Smallest Violin (plot generating code)


;;; This post explains how to visualize data with violin plots, using Vega-Lite and Clojure. 



;; # What is a violin plot?

;; A [Violin plot](https://en.wikipedia.org/wiki/Violin_plot) is a way to visualize how data is distributed - essentially showing you where your data points fall and how spread out they are.is 

;; Imagine you're analyzing a dataset of movies and how much money they made. A violin plot would show you not just the median value, but the full "shape" of your data: Are values clustered around certain points, ore evenly spread out? How much concentration is there? How many such points?

;; A violin plot is best understood as an extension of the more common box plot. Violin plots add a visulization of the probability **density**, and can reveal more features of the data, such as multiple modes. This tutorial shows you how to make box plots and violin plots in Vega-Lite. 

^:kindly/hide-code ^:kind/hidden
(import javax.imageio.ImageIO
        java.net.URL)

^:kindly/hide-code
(-> "src/data_visualization/violin-example.png"
    io/file
    (ImageIO/read))

;;; If you want to see a full-fledged implementation of interactive violin plots for visualizaing biological data, [the BRUCE website](https://bruce.parkerici.org) has one. 

;;; ## References

;;; - [Violin Plots: A Box Plot-Density Trace Synergism](https://web.archive.org/web/20231106021405/https://quantixed.org/wp-content/uploads/2014/12/hintze_1998.pdf) Jerry L. Hintze, Ray D. Nelson


;;; # Data

;;; We'll use this classic [dataset about penguin morphology](https://github.com/ttimbers/palmerpenguins/blob/master/README.md). <img src='man/figures/logo.png' align="right" height="138.5" /></a>. Each row describes an individual penguin, with properties like species, sex, body mass, wing size.

(def penguin-data-url
  "https://raw.githubusercontent.com/ttimbers/palmerpenguins/refs/heads/file-variants/inst/extdata/penguins.tsv")

(def penguin-data
  (tc/dataset penguin-data-url {:key-fn keyword}))

(kind/table
 (tc/random penguin-data 10))

;;; # Just the points, ma'am

;;; Let's start off with a simple dot-plot. We'll group the data by `species`, and turn each value for `body_mass` into a point.
;;; Vega just requires specifying some basic mappings (aka encodings) between data fields and visual properties. So a minimal dot plot can look like this:

^:kind/vega-lite
{:mark {:type "point"}
 :data {:values (tc/rows penguin-data :as-maps)}
 :encoding
 {:x {:field "body_mass_g"
      :type :quantitative}
  :y {:field "species island"
      :type :nominal}}
 }

;;; Vega's defaults are not always what we want, so this is the same as above with a bit of tweaking to look more like what we want. One nonobvious change: we use `:row` in place of `:y`. This is not estrictly necessary at this point, but will make it easier when we get to actual violin plots. Also, we add some randomness to (jitter) so we can better see individual points, and just for the hell of it, map another attribute (`sex`) to `:shape`.

^:kind/vega-lite
{:mark {:type "point" :tooltip {:content :data}}
 :data {:values (tc/rows penguin-data :as-maps)}
 :transform [{:calculate "random()" :as "jitter"}]
 :encoding
 {:x {:field "body_mass_g"
      :type :quantitative
      :scale {:zero false}}
  :row {:field "species island"
        :type :nominal
        :header {:labelAngle 0 :labelAlign "left"}
        :spacing 0}
  :color {:field "species island"
          :type :nominal
          :legend false}
  :shape {:field "sex"
          :type :nominal}
  :y {:field "jitter"
      :type :quantitative
      :axis false}}
 :height 50                             ;Note: this specifies the height of a single row 
 :width 800
 }


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
          :type :nominal
          :legend false}}
 :height {:step 50}
 :width 800
 }


;;; # Violin Plot

;;; Violin plots extend the idea of a box plot. The basic geometry is the same, but instead of showing just a few coarse statistics like median, a violin plot shows the [probability density](https://en.wikipedia.org/wiki/Probability_density_function) as a continuous variable.

;;; Vega-lite provides a [`:density`](https://vega.github.io/vega-lite/docs/density.html) transform that does the work of computing this. This transform has a number of options; `:bandwidth` controls the degree of smoothing of the density curve, and you can select a value depending on your data and needs.

(kind/vega-lite
 {:mark {:type :area}
  :data {:values (tc/rows penguin-data :as-maps)}
  :transform [{:density "body_mass_g"
               :groupby ["species island"]
               :bandwidth 80}]
  :encoding
  {:color {:field "species island"
           :type :nominal
           :legend false}
   :x {:field "value"
       :title "body_mass_g"
       :type :quantitative
       :scale {:zero false}}
   :y {:field "density"
       :type :quantitative
       :stack :center                   ; this reflects the area plot to produce the violin shape. 
       :axis false                      ; hide some labels
       } 
   :row {:field "species island"
         :type :nominal
         :spacing 0
         :header {:labelAngle 0 :labelAlign :left}
         }
   }
  :height 50                            ;this is the height of each row (facet)
  :width 800
  })

;;; That's the basics of a violin plot! In the followup page, we'll see about abstracting some of this into functions, with some variations. and we'll look at combining violin plots with dot and box plots for a richer of our data.

