(ns cesium.cesium
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            #_
            [cesiumdemo.widgets :as widget]))

(defonce appstate (atom {}))

(rdom/render
 [:div "Hello from a ClojureScript file (app.cljs)"]
 (js/document.getElementById "app4"))
