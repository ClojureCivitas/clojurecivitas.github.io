(ns civitas.why.village.markup
  (:require [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as mdt]
            [scicloj.kindly.v4.api :as kindly]
            [civitas.why.village.color :as color]))

;; test

(def mdctx
  (assoc mdt/default-hiccup-renderers
    :plain (fn [ctx {:keys [text content]}]
             (or text (map #(mdt/->hiccup ctx %) content)))))

(defn marcup
  [s]
  (md/->hiccup mdctx s))

(defn fo [props & body]
  (let [r 25
        w (* r 3)
        w2 (* w 0.5)
        h (* r 2)]
    [:foreignObject {:x      (- w2)
                     :y      (- r)
                     :width  w
                     :height h
                     :style  {:overflow "visible"}}
     (into [:div (kindly/deep-merge {:xmlns "http://www.w3.org/1999/xhtml"}
                                    props)]
           body)]))

(def default
  {:width            "500%"
   :height           "500%"
   :transform        "scale(0.2)"
   :transform-origin "top left"
   :padding          "20px"
   :text-align       "center"
   :border           :none})

(defn mo
  ([s] (mo nil s))
  ([style s]
   (fo {:style (merge default
                      {:color (color/palette 12)}
                      style)}
       (marcup s))))

(defn md
  ([s] (md nil s))
  ([style s]
   (fo {:style (merge default
                      {:color            (color/palette 0)
                       #_#_:background (color/palette 12)}
                      style)}
       (marcup s))))
