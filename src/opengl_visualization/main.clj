^{:kindly/hide-code true
  :clay             {:title  "OpenGL Visualization with LWJGL"
                     :quarto {:author   [:janwedekind]
                              :type     :post
                              :date     "2025-09-08"
                              :category :clojure
                              :tags     [:visualization]}}}
(ns opengl-visualization.main
    (:require [clojure.java.io :as io])
    (:import [javax.imageio ImageIO]))

;; https://scicloj.github.io/clay/clay_book.examples.html

(-> "src/opengl_visualization/mach-diamonds.jpg" io/file (ImageIO/read))
