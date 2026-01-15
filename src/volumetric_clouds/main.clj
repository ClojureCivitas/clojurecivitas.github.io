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
              [midje.sweet :refer (fact facts tabular => roughly)]
              [fastmath.vector :refer (vec2 add mult sub div mag dot)]
              [tech.v3.datatype :as dtype]
              [tech.v3.tensor :as tensor]
              [tech.v3.datatype.functional :as dfn]
              [tablecloth.api :as tc]
              [scicloj.tableplot.v1.plotly :as plotly]
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
      (make-noise-params 512 16) => {:size 512 :divisions 16 :cellsize 32})


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


(let [points  (tensor/reshape (random-points (make-noise-params 512 16)) [(* 16 16)])
      scatter (tc/dataset {:x (map first points) :y (map second points)})]
  (-> scatter
      (plotly/base {:=title "Random points"})
      (plotly/layer-point {:=x :x :=y :y})))


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


(defn worley-noise
  [{:keys [size] :as params}]
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


(def worley (worley-noise (make-noise-params 512 16)))

(def worley-norm (dfn/* (/ 255 (- (dfn/reduce-max worley) (dfn/reduce-min worley))) (dfn/- (dfn/reduce-max worley) worley)))

(bufimg/tensor->image worley-norm)

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


(let [gradients (tensor/reshape (random-gradients (make-noise-params 512 16)) [(* 16 16)])
      points    (tensor/reshape (tensor/compute-tensor [16 16] (fn [y x] (vec2 x y))) [(* 16 16)])
      scatter   (tc/dataset {:x (mapcat (fn [point gradient] [(point 0) (+ (point 0) (* 0.5 (gradient 0))) nil]) points gradients)
                             :y (mapcat (fn [point gradient] [(point 1) (+ (point 1) (* 0.5 (gradient 1))) nil]) points gradients)})]
  (-> scatter
      (plotly/base {:=title "Random gradients" :=mode "lines"})
      (plotly/layer-point {:=x :x :=y :y})))


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
  [params point]
  (let [cell-pos (cell-pos params point)]
    (tensor/compute-tensor [2 2] (fn [y x] (sub cell-pos (vec2 x y))))))


(facts "Compute relative vectors from cell corners to point in cell"
       (let [v (corner-vectors {:cellsize 4} (vec2 7 6))]
         (v 0 0) => (vec2 0.75 0.5)
         (v 0 1) => (vec2 -0.25 0.5)
         (v 1 0) => (vec2 0.75 -0.5)
         (v 1 1) => (vec2 -0.25 -0.5)))


(defn corner-gradients
  [params gradients point]
  (let [[div-x div-y] (map (partial division-index params) point)]
    (tensor/compute-tensor [2 2] (fn [y x] (wrap-get gradients (+ div-y y) (+ div-x x))))))


(facts "Get 2x2 tensor of gradients from a larger tensor using wrap around"
       (let [gradients (tensor/compute-tensor [4 6] (fn [y x] (vec2 x y)))]
         ((corner-gradients {:cellsize 4} gradients (vec2 9 6)) 0 0) => (vec2 2 1)
         ((corner-gradients {:cellsize 4} gradients (vec2 9 6)) 0 1) => (vec2 3 1)
         ((corner-gradients {:cellsize 4} gradients (vec2 9 6)) 1 0) => (vec2 2 2)
         ((corner-gradients {:cellsize 4} gradients (vec2 9 6)) 1 1) => (vec2 3 2)
         ((corner-gradients {:cellsize 4} gradients (vec2 23 15)) 1 1) => (vec2 0 0)))


(defn influence-values
  [gradients vectors]
  (tensor/compute-tensor [2 2] (fn [y x] (dot (gradients y x) (vectors y x))) :double))


(facts "Compute influence values from corner vectors and gradients"
       (let [gradients (tensor/compute-tensor [2 2] (fn [_y x] (vec2 x 10)))
             vectors   (tensor/compute-tensor [2 2] (fn [y _x] (vec2 1 y)))
             influence (influence-values gradients vectors)]
         (influence 0 0) => 0.0
         (influence 0 1) => 1.0
         (influence 1 0) => 10.0
         (influence 1 1) => 11.0))


(defn ease-curve
  [t]
  (-> t (* 6.0) (- 15.0) (* t) (+ 10.0) (* t t t)))


(facts "Monotonously increasing function with zero derivative at zero and one"
       (ease-curve 0.0) => 0.0
       (ease-curve 0.25) => (roughly 0.103516 1e-6)
       (ease-curve 0.5) => 0.5
       (ease-curve 0.75) => (roughly 0.896484 1e-6)
       (ease-curve 1.0) => 1.0)


(-> (tc/dataset {:t (range 0.0 1.025 0.025) :ease (map ease-curve (range 0.0 1.025 0.025))})
    (plotly/base {:=title "Ease Curve"})
    (plotly/layer-line {:=x :t :=y :ease}))


(defn interpolation-weights
  [params point]
  (let [pos     (cell-pos params point)
        [bx by] pos
        [ax ay] (sub (vec2 1 1) pos)]
    (tensor/->tensor (for [y [ay by]] (for [x [ax bx]] (* (ease-curve y) (ease-curve x)))))))


(facts "Interpolation weights"
       (let [weights (interpolation-weights {:cellsize 8} (vec2 2 7))]
         (weights 0 0) => (roughly 0.014391 1e-6)
         (weights 0 1) => (roughly 0.001662 1e-6)
         (weights 1 0) => (roughly 0.882094 1e-6)
         (weights 1 1) => (roughly 0.101854 1e-6)))


(defn perlin-sample
  [params gradients point]
  (let [gradients (corner-gradients params gradients point)
        vectors   (corner-vectors params point)
        influence (influence-values gradients vectors)
        weights   (interpolation-weights params point)]
    (dfn/reduce-+ (dfn/* weights influence))))


(defn perlin-noise
  [{:keys [size] :as params}]
  (let [gradients (random-gradients params)]
    (tensor/clone
      (tensor/compute-tensor [size size]
                             (fn [y x]
                                 (let [center (add (vec2 x y) (vec2 0.5 0.5))]
                                   (perlin-sample params gradients center)))))))


(def perlin (perlin-noise (make-noise-params 512 16)))

(def perlin-norm (dfn/* (/ 255 (- (dfn/reduce-max perlin) (dfn/reduce-min perlin))) (dfn/- perlin (dfn/reduce-min perlin))))

(bufimg/tensor->image perlin-norm)
