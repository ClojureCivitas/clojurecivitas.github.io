^{:clay {:title "ELK SVG"
         :quarto {:author   :timothypratley
                  :category :clojure
                  :tags     [:graphs :svg :visualization]}}}
(ns idea.graph.layout.elk-svg
  (:require [clojure.string :as str]
            [scicloj.kindly.v4.kind :as kind]))

(def default-styles
  {:edge-shape-stroke      "black"
   :edge-shape-fill        "none"
   :node-shape-stroke      "black"
   :node-shape-fill        "white"
   :node-label-stroke      "none"
   :node-label-fill        "black"
   :node-label-font-size   "12px"
   :node-label-font-family "sans-serif"
   :port-shape-stroke      "black"
   :port-shape-fill        "white"})

(defn edge-path [{:keys [sections]}]
  (let [[a & more] (for [{:keys [startPoint bendPoints endPoint]} sections
                         {:keys [x y]} (concat [startPoint] bendPoints [endPoint])]
                     (str x "," y))]
    (str "M" a "L" (str/join " " more))))

(defn edge [{:as e :keys [id]}]
  [:g {:id id}
   [:path {:d          (edge-path e)
           :stroke     (:edge-shape-stroke default-styles)
           :fill       (:edge-shape-fill default-styles)
           :marker-end "url(#edgeShapeMarker)"}]])

(defn edge-defs []
  [:marker {:id           "edgeShapeMarker"
            :markerWidth  10
            :markerHeight 10
            :refX         6
            :refY         3
            :orient       "auto"
            :markerUnits  "strokeWidth"}
   [:path {:d    "M0,0 L0,6 L6,3 z"
           :fill (:edge-shape-stroke default-styles)}]])

(defn shape [{:keys [x y width height]}]
  [:rect {:width  width
          :height height
          :stroke (:node-shape-stroke default-styles)
          :fill   (:node-shape-fill default-styles)}])

;; TODO: good? bad?
(defn fo-div [width height content]
  [:foreignObject {:width  width
                   :height height}
   [:div {:xmlns "http://www.w3.org/1999/xhtml"
          :style {:width  "100%"
                  :height "100%"}}
    content]])

(defn html-node [{:keys [width height layoutOptions]}]
  [:foreignObject {:width  width
                   :height height}
   [:div {:xmlns "http://www.w3.org/1999/xhtml"
          :style {:width  "100%"
                  :height "100%"}}
    (:content layoutOptions)]])

(defn centered-label [{:keys [text width height]}]
  [:foreignObject {:width  width
                   :height height
                   :style  {:overflow "visible"}}
   [:div {:xmlns "http://www.w3.org/1999/xhtml"
          :style {:font-size       "8px"
                  :display         "flex"
                  :align-items     "center"
                  :justify-content "center"
                  :width           "100%"
                  :height          "100%"}}
    text]])

(defn positioned-label [{:keys [x y text width height]}]
  [:foreignObject {:x      (some-> x (* 2.5))
                   :y      (some-> y (* 2.5))
                   :width  width
                   :height height
                   :style  {:overflow "visible"}}
   [:div {:xmlns "http://www.w3.org/1999/xhtml"
          :style {:font-size "8px"
                  :width     "100%"
                  :height    "100%"}}
    text]])

(defn node [{:as n :keys [id labels ports children edges x y layoutOptions]}]
  [:g (merge
        {:id id}
        (when (and x y)
          {:transform (str "translate(" x ", " y ")")}))
   (if (:content layoutOptions)
     (html-node n)
     (shape n))
   (concat
     (for [l labels]
       ;; TODO: is there a nicer way to default the size?
       (if (seq children)
         (positioned-label l)
         (centered-label (merge l (select-keys n [:width :height])))))
     ;; TODO: for ports need to adjust relative to node
     (map node ports)
     (map node children)
     (map edge edges))])

(defn render-graph [{:as g :keys [x y width height]}]
  (kind/hiccup
    [:svg {:viewBox (str (or x 0) " " (or y 0) " " width " " height)
           :width   "100%"
           :height  800}
     [:defs (edge-defs)]

     (node g)]))
