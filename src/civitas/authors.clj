^:kindly/hide-code
^{:clay {:quarto {:title "Authors"
                  :type :page}}}
(ns civitas.authors
  (:require [clojure.edn :as edn]
            [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(defn card [{:keys [name url links affiliation email image]}]
  (kind/hiccup
    [:div.card
     (when image [:img.card-img-top {:src image}])
     [:div.card-body
      [:h5.card-title [:a {:href url} name]]
      #_[:p email]]
     (for [{:keys [href icon text]} links]
       [:a {:href href} [:i {:class icon}] text])
     (for [a affiliation]
       (pr-str a))]))

^:kindly/hide-code
(->> (slurp "clay.edn")
     (edn/read-string)
     :aliases :markdown :quarto/expansions :author vals
     (sort-by :name)
     (into ^:kind/hiccup [:div.card-group] (map card)))
