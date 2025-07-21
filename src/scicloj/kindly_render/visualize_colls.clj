^{:kindly/hide-code true
  :clay             {:title    "Collections as grids with borders"
                     :quarto {:type     :post
                              :author   [:timothypratley]
                              :date     "2025-07-20"
                              :category :clojure
                              :tags     [:visualization]
                              :keywords [:kindly :collections]}}}
(ns scicloj.kindly-render.visualize-colls
  (:require [hiccup.core :as hiccup]
            [scicloj.kindly.v4.kind :as kind]
            [tablecloth.api :as tc]))

;; A key idea of Lisp is that all syntax is a list

()

;; Rich innovated by introducing collection literals

[]
{}
#{}

;; Data is well represented by these collections

(def data
  {:numbers [2 9 -1]
   :sets    #{"hello" "world"}
   :mix     [1 "hello world" (kind/hiccup [:div "hello" [:strong "world"] "hiccup"])]
   :nest    {:markdown             (kind/md "hello **markdown**")
             :number               9999
             :nothing-is-something #{nil #{} 0}}
   :dataset (tc/dataset {:x (range 3)
                         :y [:A :B :C]})})

;; In a notebook, we like to visualize values.
;; Often those visualizations summarize the data in some way.

;; 1. The collection represents something
;; 2. The collection organizes some things
;; 3. We are interested in the collection itself

;; In website design, everything is a grid.
;; Grids organize and align content, achieving visual hierarchy.
;; Can we apply this idea to data structures?
;; Let's start with a vector:

(def v [1 2 3 4 5 6 7 8 9])

v

;; Pretty printing the vector is a fine choice,
;; but what if we made it a grid?

(defn content [x columns]
  (kind/hiccup
    (into [:div {:style {:display               :grid
                         :grid-template-columns (str "repeat(" columns ", auto)")
                         :align-items           :center
                         :justify-content       :center
                         :text-align            :center
                         :padding               "10px"}}]
          x)))

(defn vis [x opt]
  (kind/hiccup
    [:div {:style {:display               "grid"
                   :grid-template-columns "auto auto auto"
                   :gap                   "0.25em"
                   :align-items           "center"
                   :justify-content       "center"}}
     [:div {:style {:padding     "0 0.25em"
                    :font-weight :bold
                    :align-self  (when opt "start")
                    :align-items (when-not opt "stretch")}}
      "["]
     (content x 1)
     [:div {:style {:padding     "0.25em"
                    :font-weight :bold
                    :align-self  (when opt "end")
                    :align-items (when-not opt "stretch")}}
      "]"]]))

;; In some situations this feels better, especially when nesting visualizations.
;; But it does raise the question of where the braces belong.

(vis v false)
(vis v true)

;; Another idea is to use borders to indicate collections.

(defn vis2 [x]
  (kind/hiccup
    [:div {:style {:border-left  "2px solid blue"
                   :border-right "2px solid blue"}}
     (content x 1)]))

(vis2 v)

;; Borders can be stylized with images

(defn svg [& body]
  (kind/hiccup
    (into [:svg {:xmlns        "http://www.w3.org/2000/svg"
                 :width        100
                 :height       100
                 :viewBox      [-100 -100 200 200]
                 :stroke       :currentColor
                 :fill         :none
                 :stroke-width 4}
           body])))

(defn border-style [x]
  {:border              "30px solid transparent"
   :border-image-slice  "30"
   :border-image-source (str "url('data:image/svg+xml;utf8,"
                             (hiccup/html {:mode :xml} x)
                             "')")
   :border-image-repeat "round"})

;; We can create a curly brace shape to use as the border

(def curly-brace-path
  "M -10 -40 Q -20 -40, -20 -10, -20 0, -30 0 -20 0, -20 10, -20 40, -10 40")

(def map-svg
  (svg (for [r [0 90 180 270]]
         [:path {:transform (str "rotate(" r ") translate(-30) ")
                 :d         curly-brace-path}])))

map-svg

(def map-style
  (border-style map-svg))

(defn vis3 [style columns x]
  (kind/hiccup [:div {:style style} (content x columns)]))

;; We now have a style that can be used to indicate something is a map

(vis3 map-style 2 (vec (repeat 10 [:div "hahaha"])))

;; Usually we'd put this in a CSS rule,
;; I'm just illustrating what it would look like.

(def set-svg
  (svg
    [:line {:x1 -80 :y1 -20 :x2 -80 :y2 20}]
    [:line {:x1 -70 :y1 -20 :x2 -70 :y2 20}]
    [:line {:x1 -90 :y1 10 :x2 -60 :y2 10}]
    [:line {:x1 -90 :y1 -10 :x2 -60 :y2 -10}]
    (for [r [0 90 180 270]]
      [:path {:transform (str "rotate(" r ") translate(-30) ")
              :d         curly-brace-path}])))

;; A set could have a hash on the left

set-svg

(def set-style (border-style set-svg))

(vis3 set-style 1 (vec (repeat 10 [:div "hahaha"])))

;; Sets could instead have a Venn inspired border

(def set2-svg
  (svg
    (for [r [0 90 180 270]]
      [:g {:transform (str "rotate(" r ")")}
       [:ellipse {:cx -60 :cy -15 :rx 12 :ry 25}]
       [:ellipse {:cx -60 :cy 15 :rx 12 :ry 25}]])))

set2-svg

(def set2-style (border-style set2-svg))

(vis3 set2-style 1 (vec (repeat 10 [:div "hahaha"])))

;; But I think it's better to use the more obvious `#` hash.

(def sequence-svg
  (svg
    (for [r [0 90 180 270]]
      [:g {:transform (str "rotate(" r ")")}
       [:circle {:r 75}]])))

sequence-svg

;; Parenthesis style border for sequences

(def sequence-style (border-style sequence-svg))

(vis3 sequence-style 1 (vec (repeat 10 [:div "hahaha"])))

(def vector-svg
  (svg
    (for [r [0 90 180 270]]
      [:g {:transform (str "rotate(" r ")")}
       [:path {:d "M -65 -65 v 10 "}]
       [:path {:d "M -65 65 v -10 "}]
       [:path {:d "M -65 -30 H -75 V 30 H -65"}]])))

vector-svg

;; And rectangular brace style border for vectors

(def vector-style (border-style vector-svg))

(vis3 vector-style 1 (vec (repeat 10 [:div "hahaha"])))

;; Nesting collections

(vis3 map-style 2
      [:some-key (vis3 vector-style 1 v)
       :some-other (vis3 set-style 1 (repeat 5 (vis3 sequence-style 1 ["ha" "ha" "ha"])))])

;; I think this scheme achieves a visually stylized appearance.
;; It maintains the expected hierarchy.
;; And it is fairly obvious what collections are represented.

;; What do you think?
