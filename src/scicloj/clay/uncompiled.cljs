(ns scicloj.clay.uncompiled
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

(rdom/render
 [:div "hello from Scittle"]
 (js/document.getElementById "app"))