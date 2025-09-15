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

;; We need to get some things and we can use add-libs to fetch dependencies

;; ```Clojure
;; (add-libs {'org.lwjgl/lwjgl                      {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl$natives-linux        {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-opengl               {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-opengl$natives-linux {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-glfw                 {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-glfw$natives-linux   {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-stb                  {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-stb$natives-linux    {:mvn/version "3.3.6"}})
;; (require '[clojure.java.io :as io])
;; (import '[javax.imageio ImageIO]
;;         '[org.lwjgl.glfw GLFW]
;;         '[org.lwjgl.opengl GL GL11]
;;         '[org.lwjgl.stb STBImageWrite])
;; ```

;; Next we choose the window width and height.
(def window-width 640)
(def window-height 480)

;; We use temporary files to convert STB images to ImageIO images.
(defn tmpname
  []
  (str (System/getProperty "java.io.tmpdir") "/civitas-" (java.util.UUID/randomUUID) ".tmp"))

;; We define a function to take a screenshot of the window.
(defn screenshot
  []
  (let [filename (tmpname)
        buffer   (java.nio.ByteBuffer/allocateDirect (* 4 window-width window-height))]
    (GL11/glReadPixels 0 0 window-width window-height GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
    (STBImageWrite/stbi_write_png filename window-width window-height 4 buffer (* 4 window-width))
    (-> filename io/file (ImageIO/read))))

;; We need to initialize the GLFW library.
(GLFW/glfwInit)

;; Now we create an invisible window.
;; You can create a visisble window if you want to.
(def window
  (do
    (GLFW/glfwDefaultWindowHints)
    (GLFW/glfwWindowHint GLFW/GLFW_DEPTH_BITS 24)
    (GLFW/glfwWindowHint GLFW/GLFW_STENCIL_BITS 8)
    (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
    (GLFW/glfwCreateWindow window-width window-height "Invisible Window" 0 0)))

;; Next we need to set up OpenGL rendering for this window.
(do
  (GLFW/glfwMakeContextCurrent window)
  (GL/createCapabilities))

;; A simple test is to set a clear color, clear depth, and clear the window.
(do
  (GL11/glClearColor 1.0 0.5 0.25 1.0)
  (GL11/glClearDepth 0.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (screenshot))

;; When we are finished, we destroy the window.
(GLFW/glfwDestroyWindow window)

;; Finally we terminate use of the GLFW library.
(GLFW/glfwTerminate)
