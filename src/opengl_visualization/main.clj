^{:kindly/hide-code true
  :clay             {:title  "OpenGL Visualization with LWJGL"
                     :external-requirements ["Xorg"]
                     :quarto {:author   [:janwedekind]
                              :type     :post
                              :date     "2025-09-09"
                              :category :clojure
                              :tags     [:visualization]}}}
(ns opengl-visualization.main
    (:require [clojure.java.io :as io])
    (:import [javax.imageio ImageIO]
             [org.lwjgl.glfw GLFW]
             [org.lwjgl.opengl GL GL11]
             [org.lwjgl.stb STBImageWrite]))

;; https://scicloj.github.io/clay/clay_book.examples.html
;;
;; clj -M:clay -A:markdown
;; quarto render site

(def window-width 640)
(def window-height 480)

(defn tmpname
  []
  (str (System/getProperty "java.io.tmpdir") "/civitas-" (java.util.UUID/randomUUID) ".tmp"))

(defn screenshot
  []
  (let [filename (tmpname)
        buffer   (java.nio.ByteBuffer/allocateDirect (* 4 window-width window-height))]
    (GL11/glReadPixels 0 0 window-width window-height GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
    (STBImageWrite/stbi_write_png filename window-width window-height 4 buffer (* 4 window-width))
    (-> filename io/file (ImageIO/read))))

(GLFW/glfwInit)

(def window
  (do
    (GLFW/glfwDefaultWindowHints)
    (GLFW/glfwWindowHint GLFW/GLFW_DEPTH_BITS 24)
    (GLFW/glfwWindowHint GLFW/GLFW_STENCIL_BITS 8)
    (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
    (GLFW/glfwCreateWindow window-width window-height "Invisible Window" 0 0)))

(do
  (GLFW/glfwMakeContextCurrent window)
  (GL/createCapabilities))

(GL11/glClearColor 1.0 0.5 0.25 1.0)
(GL11/glClearDepth 0.0)
(GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))

(screenshot)

(GLFW/glfwDestroyWindow window)
(GLFW/glfwTerminate)
