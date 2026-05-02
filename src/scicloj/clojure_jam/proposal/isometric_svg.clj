(ns scicloj.clojure-jam.proposal.isometric-svg
  {:clay {:title          "Proposal for Isometric SVG talk"
          :quarto         {:author      [:timothypratley]
                           :description "How did you make your slides?"
                           :draft       true
                           :type        :post
                           :date        "2026-01-16"
                           :category    :collaboration
                           :tags        [:clojure :writing :workflow :motivation :community]}}}
  (:require [clojure.string :as str]))

;; Topic: Rendering Isometric SVG images in Clojure

;; How to make an SVG in Clay

^:kind/hiccup
[:svg {:style {:border "solid 1px black"}
       :width "100%"}]

;; In this talk we'll discuss the way to represent
;; isometric images

;; How to make an SVG in Clay

^:kind/hiccup
[:svg {:style {:border "solid 1px black"}
       :width "100%"}
 [:polygon {:points (str/join
                     " "
                     (map #(str/join "," %)
                          [[0 0]
                           [0 100]
                           [100 100]
                           [100 0]]
                          ))}]]

;; Transformation to skew the polygon

;; It's easier to just draw the square and then skew it.

;; What transformations can be applied?
;; Rotation, translation, others?
;; How can we display a matrix in Clojure

;; a transformation is often through a matrix

[[0 0 0]
 [0 0 0]
 [0 0 0]]

;; What are we transforming?
;; Point.
;; Face (collection of points that are connected).


;; That's the talk.

;; Include some more interesting examples

;; What are some interesting things to draw?
