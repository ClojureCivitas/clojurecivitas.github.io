(ns civitas.svg
  (:require [civitas.geometry :as geom]
            [clojure.string :as str]
            [scicloj.kindly.v4.kind :as kind]))

(defn pt [[x y]]
  (str x "," y))

(defn pts [xys]
  (str/join " " (map pt xys)))

(defn polygon [attrs points]
  [:polygon (assoc attrs :points (pts points))])

(defn path [attrs [start & more]]
  [:path (assoc attrs :d (str "M" (pt start) " L" (pts more) " Z"))])

;; A flat hexagon

(kind/hiccup
  [:svg {:width   "100%"
         :viewbox [-30 -30 60 60]}
   (polygon {:stroke "lightblue"
             :fill   "lightgreen"}
            (geom/hex 10))])
