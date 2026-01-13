^{:kindly/hide-code true
  :clay             {:title  "Volumetric Clouds"
                     :external-requirements ["Xorg"]
                     :quarto {:author   [:janwedekind]
                              :description "Procedural generation of volumetric clouds using different types of noise"
                              :image    "clouds.jpg"
                              :type     :post
                              :date     "2026-01-13"
                              :category :clojure
                              :tags     [:visualization]}}}
(ns volumetric-clouds.main
    (:require [clojure.java.io :as io]
              [tech.v3.tensor :as dtt])
    (:import [javax.imageio ImageIO]
             [org.lwjgl.opengl GL11]
             [org.lwjgl.stb STBImageWrite]
             ))

;; Hello
(def window-width 640)
(def window-height 480)

(defn tmpdir
  []
  (System/getProperty "java.io.tmpdir"))

(defn tmpname
  []
  (str (tmpdir) "/civitas-" (java.util.UUID/randomUUID) ".tmp"))

(defn screenshot
  []
  (let [filename (tmpname)
        buffer   (java.nio.ByteBuffer/allocateDirect (* 4 window-width window-height))]
    (GL11/glReadPixels 0 0 window-width window-height
                       GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
    (STBImageWrite/stbi_write_png filename window-width window-height 4
                                  buffer (* 4 window-width))
    (-> filename io/file (ImageIO/read))))
