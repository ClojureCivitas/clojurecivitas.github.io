^{:kindly/hide-code true
  :clay
  {:title  "Introduction to Violin Plots with Vega-Lite (Part 2)"
   :quarto {:author      :mt
            :draft       true
            :type        :post
            :date        "2026-02-02"
            :category    :clojure
            :tags        [:dataviz :vega-lite]}}}


(ns scicloj.data-visualization.violin2
  (:require
   [tablecloth.api :as tc]
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind]
   [hyperphor.multitool.core :as mu]
   ))


;; # The World's Smallest Violin (plot generating code), Part 2


;;; This is Part 2 of a tutorial,  [Part 1 is here](/scicloj/data_visualization/violin.html).


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
    :y (when jitter?
         {:field "jitter"
          :type :quantitative
          :axis false})
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
  "Generalized dot plot"
  [data value-field group-field & {:keys [overrides] :as options}]
  (mu/merge-recursive
   (dot-plot data value-field group-field options)
   overrides))


;;; So here's another penguin dotplot, but in this case we've used the `:overrides` option to specify that the marks should eb filled, bigger than the defautl, and we'll add an econding.

^:kind/vega-lite
(dot-plot-g {:values (tc/rows penguin-data :as-maps)}
            "body_mass_g" "species island"
            :jitter? true
            :overrides {:mark {:filled true :size 65}
                        :encoding {:shape {:field "sex"}}}
            )



;;; # An abstraction for box plots

;;; We can use our new tools to trivially do do a boxplot, by patching the dot-ploit spec a bit

(defn box-plot-g
  "Generalized box plot"
  [data value-field group-field & {:keys [overrides] :as options}]
  (dot-plot-g data value-field group-field
              :jitter? false
              :overrides
              (mu/merge-recursive
               {:mark {:type :boxplot
                       :extent :min-max}}
               overrides)))

^:kind/vega-lite
(box-plot-g {:values (tc/rows penguin-data :as-maps)}
            "body_mass_g" "species island"
            )

;;; # Composition

;;; Now that we have some abstractions, we can combine them using Vega=Lite layers and some judicious Clojure


(defn box-dot-plot
  [data value-field group-field]
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
                   }}}
   :spec
   {:layer
    [(dot-plot-g nil value-field group-field
                 :jitter? true)
     (box-plot-g nil value-field group-field
                 :overrides {:mark {:box {:strokeWidth 1.5 :stroke "gray"}}})]
    :width 800}
   })

^:kind/vega-lite
(box-dot-plot {:url penguin-data-url
               :format {:type "tsv"}}
              "flipper_length_mm" "species island"
              )

;;; Let's try that for the movies

^:kind/vega-lite
(box-dot-plot {:url "https://vega.github.io/editor/data/movies.json"}
              "US Gross" "Major Genre"
              )

;;; # An abstraction for violin plots


;;; This could also be built from dot-plot, but since theres a lot to change let's not.


(defn violin-plot
  [data value-field group-field & {:keys [bandwidth overrides] :or {bandwidth 100}}]
  (mu/merge-recursive
   {:mark {:type :area :orient :vertical}
    :data data
    :transform [{:density value-field
                 :groupby [group-field]
                 :bandwidth bandwidth}]
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
         :stack :center
         :axis false}                      ; hide some labels
     :row {:field group-field
           :type :nominal
           :spacing 0
           :header {:labelAngle 0 :labelAlign :left}
           }
     }
    :height 50                            ;this is the height of each row (facet)
    :width 800
    }
   overrides))


^:kind/vega-lite
(violin-plot {:url "https://vega.github.io/editor/data/movies.json"}
             "US Gross" "Major Genre"
             :bandwidth 5000000
             :overrides
             {:encoding {:x {:scale {:domain [0 100000000]}}} ;force scale to exclude outliers
              :mark {:clip true}}                             ;and don't plot them
             )


;;; # Rotate

;;; Violin plots are often oriented vertically. We'll rotate our visualizations with another hack. First we'll define a utility function to swap pairs of keywords in a stucture, using some existing utility functions in multitool:

(defn swap-keys
  "Swap keywords in struct based on pairs defined in keyswaps"
  [struct keyswaps]
  (let [remap (mu/map-bidirectional keyswaps)] ;This ensures that the relationships in keyswaps are bidirectional
    (mu/walk-filtered                   ;This applies a fn to all keyword elements of struct.
     (fn [k] (get remap k k))
     struct
     keyword?)))

^:kind/vega-lite
(-> (violin-plot {:values (tc/rows penguin-data :as-maps)}
                 "body_mass_g" "species island")
    (swap-keys {:x :y
                :row :column
                :rows :columns
                :height :width
                :horizontal :vertical})
    (mu/merge-recursive
     {:encoding {:column {:header {:labelAngle 90 :labelAlign :center}}}}))

;;; And, let's try to abstract that out and apply it to a dotplot

(defn rotate-spec
  [spec]
  (-> spec
      (swap-keys {:x :y
                  :row :column
                  :rows :columns
                  :height :width
                  :horizontal :vertical})
      (mu/merge-recursive
       {:encoding {:column {:header {:labelAngle 90 :labelAlign :center}}}})))

^:kind/vega-lite
(-> (box-plot-g {:values (tc/rows penguin-data :as-maps)}
                "body_mass_g" "species island")
    rotate-spec)

;;; # And finally put all the pieces together. 

;;; What we'd like to do is make something like box-dot-plot above, but with a 3rd layer – the violins – generated by its own fn. Unfortunately that doesn't quite work due to some technical issues. So we'll expand everything out for the grand finale, which also makes it a bit easier to tweak things so they fit together better.


^:kind/vega-lite
{:data {:values (tc/rows penguin-data :as-maps)}
 :config {:facet {:spacing 0}
          :view {}}
 :facet
 {:row {:field "species island"
        :type :nominal
        :header {:labelAngle 0 :labelAlign "left"}}}
 :spec
 {:height 50
  :width 800
  :resolve {:scale {:y "independent"}}  ;each layer uses the y axis differently
  :encoding
  {:x {:field "body_mass_g"
       :type :quantitative
       :title "Body Mass (g)"}}
  :layer
  [
   ;; Violins
   {:mark {:type :area :orient :vertical}
    :transform [{:density "body_mass_g"
                 :groupby ["species island"]
                 :bandwidth 80}]
    :encoding
    {:color {:field "species island"
             :type :nominal
             :legend false}
     :x {:field "value"
         :type :quantitative}
     :y {:field "density"
         :type :quantitative
         :stack :center
         :axis false
         :scale {:range [0 50]}}
     :opacity {:value 0.7}
     }}

   ;; Points
   {:mark {:type "point" :tooltip {:content :data}}
    :transform [{:calculate "random()" :as "jitter"}]
    :encoding
    {:y {:field "jitter"
         :type :quantitative
         :axis false}
     }}

   ;; Box
   {:mark
    {:type :boxplot
     :tooltip {:content :data}
     :box {:strokeWidth 1.5 :stroke "gray"}}
    :transform [{:calculate "random()" :as "jitter"}]
    :encoding
    {:fill {:value "none"}
     :stroke {:value "black"}
     }}
   ] }}

;;; There you have it, the datapoints themselves, the coarse statistics, and the probability density, all in one place so you can see how they relate.
