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
              [clojure.math :refer (PI cos sin)]
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

;; # Worley noise
(def size 512)
(def divisions 32)
(def cellsize (/ size divisions))


(defn pt [y x] (fm/add (fm/mult (fm/vec2 x y) cellsize) (fm/vec2 (rand cellsize) (rand cellsize)) ))

(defn clip [x] (if (>= x (/ size 2)) (- x size) (if (< x (/ size -2)) (+ x size) x)))

(defn dist [a b] (fm/mag (fm/vec2 (clip (- (a 0) (b 0))) (clip (- (a 1) (b 1))))))

(def random-points (tensor/clone (tensor/compute-tensor [divisions divisions] pt)))
random-points

(def worley
  (tensor/compute-tensor [size size]
                         (fn [y x]
                             (let [center (fm/add (fm/vec2 x y) (fm/vec2 0.5 0.5))
                                   divx   (quot x cellsize)
                                   divy   (quot y cellsize)]
                               (apply min
                                      (for [dy [-1 0 1] dx [-1 0 1]]
                                           (dist (random-points (mod (+ divy dy) divisions) (mod (+ divx dx) divisions)) center)))))
                         :double))

(def sample (tensor/clone worley))

(def img (dfn/* (/ 255 (- (dfn/reduce-max sample) (dfn/reduce-min sample))) (dfn/- (dfn/reduce-max sample) sample)))
img

(bufimg/tensor->image img)

;; # Perlin noise
(defn random-gradient
  [y x]
  (let [angle (rand (* 2 PI))]
    (fm/vec2 (cos angle) (sin angle))))


(def random-gradients (tensor/clone (tensor/compute-tensor [divisions divisions] random-gradient)))
random-gradients

(defn division-index
  [point]
  (fm/vec2 (int (/ (point 0) cellsize)) (int (/ (point 1) cellsize))))

(defn cell-pos
  [point]
  (fm/sub (fm/div point cellsize) (division-index point)))


(defn corner-vectors
  [point]
  (let [cell-pos (cell-pos point)]
    (for [y (range 2) x (range 2)]
         (fm/sub cell-pos (fm/vec2 x y)))))


(defn corner-gradients
  [point]
  (let [[div-x div-y] (division-index point)]
    (for [y [div-y (inc div-y)] x [div-x (inc div-x)]]
         (random-gradients (mod y divisions) (mod x divisions)))))


(defn influence-values
  [gradients vectors]
  (map fm/dot gradients vectors))


(defn ease-curve
  [t]
  (-> t (* 6.0) (- 15.0) (* t) (+ 10.0) (* t t t)))


(defn interpolation-weights
  [point]
  (let [[bx by] (cell-pos point)
        [ax ay] (fm/sub (fm/vec2 1 1) (cell-pos point))]
    (for [y [ay by] x [ax bx]]
         (* (ease-curve y) (ease-curve x)))))


(defn perlin-sample
  [point]
  (let [gradients (corner-gradients point)
        vectors   (corner-vectors point)
        influence (influence-values gradients vectors)
        weights   (interpolation-weights point)]
    (apply + (map * weights influence))))


(def perlin
  (tensor/compute-tensor [size size]
                         (fn [y x]
                             (let [center (fm/add (fm/vec2 x y) (fm/vec2 0.5 0.5))]
                               (perlin-sample center)))
                         :double))

(def sample2 (tensor/clone perlin))
(def img2 (dfn/* (/ 255 (- (dfn/reduce-max sample2) (dfn/reduce-min sample2))) (dfn/- sample2 (dfn/reduce-min sample2))))
img2

(bufimg/tensor->image img2)
