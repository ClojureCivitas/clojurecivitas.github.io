^:kindly/hide-code
^{:clay {:title  "Authors"
         :quarto {:type :page}}}
(ns civitas.authors
  (:require [camel-snake-kebab.core :as csk]
            [civitas.db :as db]
            [civitas.explorer.geometry :as geom]
            [civitas.explorer.svg :as svg]
            [civitas.why.village.color :as color]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [scicloj.kindly.v4.kind :as kind]))

;; You belong here! Thank you for sharing your ideas.

^:kindly/hide-code
(def authors (->> (:author @db/db)
                  (sort-by :name)))

^:kindly/hide-code
(defn authors-hexagon []
  (let [colors (cycle (take 11 color/palette))
        r 6]
    [:g
     [:g {:transform "scale(0.8)"}
      (for [[{:keys [image name]} [x y] color] (map vector authors (geom/spiral 100) colors)]
        [:a {:href (str "/" (csk/->Camel_Snake_Case name) ".html")}
         [:title name]
         [:g {:transform (str "translate(" (* r x) "," (* r y) ")")}
          (svg/polygon {:fill         color
                        :stroke       "#5881D8"
                        :stroke-width 1}
                       (geom/hex r))
          [:image {:x                   -5
                   :y                   -5
                   :width               10
                   :height              10
                   :href                image
                   :clip-path           "url(#hex)"
                   :preserveAspectRatio "xMidYMid slice"}]]])]]))

^:kind/hiccup ^:kindly/hide-code
[:svg
 {:xlmns   "http://www.w3.org/2000/svg"
  :viewBox "-30 -30 60 60"
  :width   "100%"}
 [:defs
  [:clipPath {:id "hex"}
   [:polygon {:points (str/join " "
                                (map #(str/join "," %) (geom/hex 5)))}]]]
 [:text {:x -30, :y -15 :fill "#5881D8"} (str (count authors))]
 (authors-hexagon)]

^:kindly/hide-code
(defn card-link [href icon text]
  [:a.card-link {:href href}
   (if text
     text
     [:i {:class (str "bi bi-" (or icon "box-arrow-up-right"))}])])

^:kindly/hide-code
(defn card [{:keys [name url links affiliation image]} affiliations]
  [:tr
   [:td (when image [:img {:src image :width "50px"}])]
   [:td name]
   [:td (for [{:keys [name url]} (map affiliations affiliation)]
          (card-link url nil name))]
   [:td (card-link (str "/" (csk/->Camel_Snake_Case name) ".html") nil "posts")]])

^:kindly/hide-code
(let [affiliations (db/index-by :id (:affiliation @db/db))]
  ^:kind/hiccup
  [:table
   [:thead
    [:tr
     [:th ""]
     [:th "Name"]
     [:th "Affiliation"]
     [:th "Posts"]]]
   [:tbody
    (map #(card % affiliations)
         authors)]])

^:kindly/hide-code
^:kind/hidden
(doseq [author authors]
  (let [front-matter {:title   (:name author)
                      :about   (assoc (select-keys author [:image :links])
                                 :template "trestles")
                      :author  author
                      :listing {:contents  [".", "!./*.qmd"]
                                :include   {:type   :post
                                            :author (str "{" (:name author) "}" "*")}
                                :sort      ["date desc" "title desc"]
                                :sort-ui   true
                                :filter-ui true}}]
    (doto (io/file "site"
                   (str (csk/->Camel_Snake_Case (:name author)) ".qmd"))
      (io/make-parents)
      (spit (str "---\n"
                 (yaml/generate-string front-matter)
                 "\n---\n")))))
