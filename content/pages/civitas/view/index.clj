^:kindly/hide-code
^{:clay {:quarto {:format {:html {:page-layout :full}}}}}
(ns civitas.view.index
  (:require [scicloj.kindly.v4.kind :as kind]
            [civitas.db :as db]
            [civitas.geometry :as geom]
            [civitas.svg :as svg]))

(defn html [s content]
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
    content]])

^:kindly/hide-code
(defn notebook-view [{:keys [title position url] :as notebook}
                     {:keys [direction color] :as topic}]
  (let [s 80
        [x y] (nth (geom/sectors direction) position)
        x (* s x)
        y (* s y)]
    [:g {:transform (str "translate(" x "," y ")")}
     (svg/polygon {:fill color}
                  (geom/hex (* 0.9 s)))
     (html s [:a {:href url}
              title])]))

(comment
  (kind/hiccup [:div (for [i (range 10)]
                       (/ i 0))]))

^:kindly/hide-code
(defn hex-grid [xs]
  (let [max-position (transduce (map :position) max 0 xs)
        layers (geom/layers max-position)
        r 80
        vr (* layers r 2)]
    [:svg {:xmlns   "http://www.w3.org/2000/svg"
           :viewBox [(- vr) (- vr) (* 2 vr) (* 2 vr)]
           :width   "100%"}
     (for [{:keys [direction position color url title]} xs]
       (let [[x y] (get-in geom/sectors [direction position])
             x (* r x)
             y (* r y)]
         [:g {:transform (str "translate(" x "," y ")")}
          (svg/polygon {:fill color} (geom/hex (* 0.9 r)))
          (html (* 0.8 r) [:a {:href url} title])]))]))

^:kindly/hide-code
(kind/hiccup
  (hex-grid
    (for [[t notebooks] (db/get-notebooks-by-topic)
          :let [topic (db/get-topic t)]
          notebook notebooks]
      (merge notebook topic))))

^:kindly/hide-code
(kind/table (db/notebooks)
            {:use-datatables true
             :datatables     {}})

#_^:kindly/hide-code
#_(kind/hiccup
  (hex-grid
    (for [topic (db/topics)]
      (merge topic {:position  1
                    :url       nil
                    :direction 1
                    :color     (or (:color topic)
                                   (keyword (name (:id topic))))
                    :title     (:desc topic)}))))

^:kindly/hide-code
(kind/table (db/topics)
            {:use-datatables true
             :datatables     {}})
