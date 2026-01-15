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
              [clojure.math :refer (PI cos sin)]
              [midje.sweet :refer (fact facts tabular =>)]
              [fastmath.vector :refer (vec2 add mult sub div mag)]
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

(defn make-noise-params
  [size divisions]
  {:size size :divisions divisions :cellsize (/ size divisions)})

(fact "Noise parameter initialisation"
      (make-noise-params 512 32) => {:size 512 :divisions 32 :cellsize 16})


(defn random-point-in-cell
  [{:keys [cellsize]} y x]
  (add (mult (vec2 x y) cellsize) (vec2 (rand cellsize) (rand cellsize))))


(facts "Place random point in a cell"
       (with-redefs [rand (fn [s] (* 0.5 s))]
         (random-point-in-cell {:cellsize 1} 0 0) => (vec2 0.5 0.5)
         (random-point-in-cell {:cellsize 2} 0 0) => (vec2 1.0 1.0)
         (random-point-in-cell {:cellsize 2} 0 3) => (vec2 7.0 1.0)
         (random-point-in-cell {:cellsize 2} 2 0) => (vec2 1.0 5.0)))


(defn random-points
  [{:keys [divisions] :as params}]
  (tensor/clone (tensor/compute-tensor [divisions divisions] (partial random-point-in-cell params))))


(facts "Greate grid of random points"
       (let [params (make-noise-params 32 8)]
         (with-redefs [rand (fn [s] (* 0.5 s))]
           (dtype/shape (random-points params)) => [8 8]
           ((random-points params) 0 0) => (vec2 2.0 2.0)
           ((random-points params) 0 3) => (vec2 14.0 2.0)
           ((random-points params) 2 0) => (vec2 2.0 10.0))))


(defn mod-vec2
  [{:keys [size]} v]
  (let [size2 (/ size 2)
        wrap  (fn [x] (-> x (+ size2) (mod size) (- size2)))]
    (vec2 (wrap (v 0)) (wrap (v 1)))))


(facts "Wrap around components of vector to be within -size/2..size/2"
       (mod-vec2 {:size 8} (vec2 2 3)) => (vec2 2 3)
       (mod-vec2 {:size 8} (vec2 5 2)) => (vec2 -3 2)
       (mod-vec2 {:size 8} (vec2 2 5)) => (vec2 2 -3)
       (mod-vec2 {:size 8} (vec2 -5 2)) => (vec2 3 2)
       (mod-vec2 {:size 8} (vec2 2 -5)) => (vec2 2 3))


(defn mod-dist
  [params a b]
  (mag (mod-vec2 params (sub b a))))


(tabular "Wrapped distance of two points"
         (fact (mod-dist {:size 8} (vec2 ?ax ?ay) (vec2 ?bx ?by)) => ?result)
         ?ax ?ay ?bx ?by ?result
         0   0   0   0   0.0
         0   0   2   0   2.0
         0   0   5   0   3.0
         0   0   0   2   2.0
         0   0   0   5   3.0
         2   0   0   0   2.0
         5   0   0   0   3.0
         0   2   0   0   2.0
         0   5   0   0   3.0)


(defn wrap-get
  [t & args]
  (apply t (map mod args (dtype/shape t))))


(facts "Wrapped lookup of tensor values"
       (let [t (tensor/compute-tensor [4 6] vec2)]
         (wrap-get t 2 3) => (vec2 2 3)
         (wrap-get t 2 7) => (vec2 2 1)
         (wrap-get t 5 3) => (vec2 1 3)))


(defn division-index
  [{:keys [cellsize]} x]
  (int (/ x cellsize)))


(facts "Convert coordinate to division index"
       (division-index {:cellsize 4} 3.5) => 0
       (division-index {:cellsize 4} 7.5) => 1)


(defn worley
  [{:keys [size cellsize] :as params}]
  (let [random-points (random-points params)]
    (tensor/clone
      (tensor/compute-tensor [size size]
                             (fn [y x]
                                 (let [center        (add (vec2 x y) (vec2 0.5 0.5))
                                       [div-x div-y] (map (partial division-index params) center)]
                                   (apply min
                                          (for [dy [-1 0 1] dx [-1 0 1]]
                                               (mod-dist params center (wrap-get random-points (+ div-y dy) (+ div-x dx)))))))
                             :double))))


(def sample (worley (make-noise-params 512 32)))

(def img (dfn/* (/ 255 (- (dfn/reduce-max sample) (dfn/reduce-min sample))) (dfn/- (dfn/reduce-max sample) sample)))

(bufimg/tensor->image img)

;; # Perlin noise

(defn random-gradient
  [& _args]
  (let [angle (rand (* 2 PI))]
    (vec2 (cos angle) (sin angle))))


(defn roughly-vec2
  [expected error]
  (fn [actual]
      (<= (mag (sub actual expected)) error)))


(facts "Create unit vector with random direction"
       (with-redefs [rand (constantly 0)]
         (random-gradient) => (roughly-vec2 (vec2 1 0) 1e-6))
       (with-redefs [rand (constantly (/ PI 2))]
         (random-gradient) => (roughly-vec2 (vec2 0 1) 1e-6)))


(defn random-gradients
 [{:keys [divisions]}]
 (tensor/clone (tensor/compute-tensor [divisions divisions] random-gradient)))


(facts "Random gradients"
       (with-redefs [rand (constantly 0)]
         (dtype/shape (random-gradients {:divisions 8})) => [8 8]
         ((random-gradients {:divisions 8}) 0 0) => (roughly-vec2 (vec2 1 0) 1e-6)))


(defn frac
  [x]
  (- x (Math/floor x)))


(facts "Fractional part of floating point number"
       (frac 0.25) => 0.25
       (frac 1.75) => 0.75
       (frac -0.25) => 0.75)


(defn cell-pos
  [{:keys [cellsize]} point]
  (apply vec2 (map frac (div point cellsize))))


(facts "Relative position of point in a cell"
       (cell-pos {:cellsize 4} (vec2 2 3)) => (vec2 0.5 0.75)
       (cell-pos {:cellsize 4} (vec2 7 5)) => (vec2 0.75 0.25))


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
