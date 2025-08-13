(ns scicloj.clay.uncompiled
  (:require [reagent.dom :as rdom]))

(rdom/render
 [:div "Hello from a ClojureScript file (uncompiled.cljs)"]
 (js/document.getElementById "app"))