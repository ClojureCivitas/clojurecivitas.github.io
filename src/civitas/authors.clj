^:kindly/hide-code
^{:clay {:title "Authors"
         :quarto {:type :page}}}
(ns civitas.authors
  (:require [civitas.db :as db]
            [scicloj.kindly.v4.kind :as kind]))

;; You belong here!

;; Thank you for sharing your ideas.

^:kindly/hide-code
(defn card-link [href icon text]
  [:a.card-link {:href href}
   (if text
     text
     [:i {:class (str "bi bi-" (or icon "box-arrow-up-right"))}])])

^:kindly/hide-code
(defn card [{:keys [name url links affiliation image]} affiliations]
  (kind/hiccup
    [:div.card
     (when image [:img.card-img-top {:src image}])
     [:div.card-body
      [:h5.card-title name]
      [:div
       (when url (card-link url nil nil))
       (for [{:keys [href icon]} links]
         (card-link href icon nil))]
      (for [{:keys [name url]} (map affiliations affiliation)]
        (card-link url nil name))]]))

^:kindly/hide-code
(let [affiliations (db/index-by :id (:affiliation @db/db))]
  (into ^:kind/hiccup [:div]
        (comp (map #(card % affiliations))
              (partition-all 5)
              (map #(into [:div.card-group] %)))
        (->> (:author @db/db)
             (sort-by :name))))
