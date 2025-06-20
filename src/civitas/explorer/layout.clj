^{:clay {:quarto {:draft true}}}
(ns civitas.explorer.layout
  (:require [hiccup.core :as hiccup]
            [scicloj.kindly.v4.kind :as kind]
            [civitas.explorer.db :as db]
            [civitas.explorer.geometry :as geom]
            [civitas.explorer.svg :as svg]))

(def colors
  (vec (remove nil? (map :color (:topic @db/db)))))

(defn notebook-view [{:keys [title topic position level url] :as notebook}
                     {:keys [direction color] :as topic}]
  (let [s 80
        [x y] (nth (geom/sectors direction) position)
        x (* s x)
        y (* s y)]
    [:g {:transform (str "translate(" x "," y ")")}
     (svg/polygon {:fill color}
                  (geom/hex (* 0.9 s)))
     [:foreignObject {:x      (- s)
                      :y      (- s)
                      :width  (* 2 s)
                      :height (* 2 s)}
      [:div {:xmlns "http://www.w3.org/1999/xhtml"
             :style {:width           "100%"
                     :height          "100%"
                     :text-align      :center
                     :display         :flex
                     :justify-content :center
                     :align-items     :center
                     :overflow        :visible}}
       [:a {:href url}
        title]]]]))

(def width 500)
(defn hex-grid []
  [:div
   ;; TODO: how to make it 100% wide? (container class puts it in a column)
   #_{:style {:position :absolute
              :left     0
              :right    0}}
   [:svg {:xmlns   "http://www.w3.org/2000/svg"
          :viewBox [(- width) (- width) (* 2 width) (* 2 width)]
          :width   "100%"}
    (for [[topic notebooks] (db/get-notebooks-by-topic)
          :let [t (db/get-topic topic)]
          notebook notebooks]
      (notebook-view notebook t))]])

(comment
  (kind/hiccup
    (hex-grid))

  (kind/table db/notebooks-ds {:use-datatables true
                     :datatables               {}}))

(def icon
  (kind/hiccup
    [:svg {:xmlns   "http://www.w3.org/2000/svg"
           :width   100
           :height  100
           :viewBox (let [w 200]
                      [(- w) (- w) (* 2 w) (* 2 w)])}
     (for [i (range 6)]
       (let [s 80
             [x y] (nth (geom/sectors i) 0)
             x (* s x)
             y (* s y)]
         [:g {:transform (str "translate(" x "," y ")")}
          (svg/polygon {:fill (db/get-colors i)}
                       (geom/hex (* 0.9 s)))]))]))

(comment
  icon
  (spit "civitas-icon.svg"
        (hiccup/html icon)))
