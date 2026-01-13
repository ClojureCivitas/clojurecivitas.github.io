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
    (:require [scicloj.kindly.v4.kind :as kind]
              [clojure.java.io :as io]
              [fastmath.vector :as fm]
              [tech.v3.datatype :as dtype]
              [tech.v3.tensor :as tensor]
              [tech.v3.datatype.functional :as dfn]
              [tech.v3.libs.buffered-image :as bufimg])
    (:import [javax.imageio ImageIO]
             [org.lwjgl.opengl GL11]
             [org.lwjgl.stb STBImageWrite]))

;; Procedural generation of volumetric clouds
;;
;; * Perlin, Worley noise
;; * fractal brownian motion
;; * curl noise
;; * Midje testing of shaders
;; * Generating OpenGL shaders using templates
;; * cloud shadows
;; * powder function

(def window-width 640)
(def window-height 480)


(def size 512)
(def divisions 32)
(def cellsize (/ size divisions))

(def t (tensor/->tensor [[(fm/vec3 1 2 3) (fm/vec3 4 5 6)] [(fm/vec3 7 8 9) (fm/vec3 10 11 12)]]))
t

(defn pt [y x] (fm/add (fm/mult (fm/vec2 x y) cellsize) (fm/vec2 (rand cellsize) (rand cellsize)) ))

(defn clip [x] (if (>= x (/ size 2)) (- x size) (if (< x (/ size -2)) (+ x size) x)))

(defn dist [a b] (fm/mag (fm/vec2 (clip (- (a 0) (b 0))) (clip (- (a 1) (b 1))))))

(def random-points (tensor/clone (tensor/compute-tensor [divisions divisions] pt)))
random-points

(def worley
  (tensor/clone
    (tensor/compute-tensor [size size]
                           (fn [y x]
                               (let [center (fm/add (fm/vec2 x y) (fm/vec2 0.5 0.5))
                                     divx   (quot x cellsize)
                                     divy   (quot y cellsize)]
                                 (apply min
                                        (for [dy [-1 0 1] dx [-1 0 1]]
                                             (dist (random-points (mod (+ divy dy) divisions) (mod (+ divx dx) divisions)) center)))))
                           :double)))
worley

(def img (dfn/* (/ 255 (- (dfn/reduce-max worley) (dfn/reduce-min worley))) (dfn/- (dfn/reduce-max worley) worley)))
img

(bufimg/tensor->image img)
