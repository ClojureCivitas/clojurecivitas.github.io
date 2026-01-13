^{:clay {:quarto {:draft true}}}
(ns civitas.explorer.geometry
  (:require [clojure.math :as math]))

;; Height of isosceles triangle with edge length 2

(def √3 (math/sqrt 3))
(def sin60 (/ √3 2))

;; The triangular number formula describes the positions available in each direction
;; $$T_n = 1 + 2 + 3 + \dots + n = \frac{n(n + 1)}{2}$$
;; Inverting
;; $$k = \left\lceil \frac{\sqrt{8T + 1} - 1}{2} \right\rceil$$
;; The triangular root ceiling is the number of layers necessary for T positions
(defn layers [T]
  (int (math/ceil (/ (- (math/sqrt (+ (* 8 T) 1)) 1) 2))))

(def flat-hexagon-points
  [[1.0 0.0] [0.5 sin60] [-0.5 sin60]
   [-1.0 0.0] [-0.5 (- sin60)] [0.5 (- sin60)]])

;; Hexagon layouts may be represented using a cubic coordinate system.
;; `q r s` are axes such that `q + r + s = 0`
;; https://www.redblobgames.com/grids/hexagons/

(def directions
  [[1 0 -1] [1 -1 0] [0 -1 1]
   [-1 0 1] [-1 1 0] [0 1 -1]])

(defn neighbor [cube direction]
  (mapv + cube (directions direction)))

(defn cube-to-cartesian-flat [[q r _]]
  [(* 1.5 q)
   (+ (* sin60 q)
      (* √3 r))])

(defn cube-to-cartesian-pointy [[q r _]]
  [(+ (* √3 q)
      (* sin60 r))
   (* 1.5 r)])

(defn cube-ring [radius]
  (if (zero? radius)
    [[0 0 0]]
    (loop [results []
           cube (mapv #(* % radius) (directions 4))
           [[dir _step] & more] (butlast (for [dir (range 6)
                                            step (range radius)]
                                           [dir step]))]
      (if dir
        (recur (conj results cube)
               (neighbor cube dir)
               more)
        (conj results cube)))))

(defn cube-spiral [radius]
  (mapcat cube-ring (range radius)))

(defn spiral [radius]
  (->> (cube-spiral radius)
       (map cube-to-cartesian-flat)))

(defn walk-radially [start-pos dir steps]
  (->> (iterate #(neighbor % dir) start-pos)
       (take (inc steps))))

(defn build-sector [dir depth]
  (let [secondary-dir (mod (inc dir) 6)
        start-pos (directions dir)]
    (vec (mapcat (fn [layer]
                   (->> (walk-radially start-pos dir layer)
                        (mapcat #(walk-radially % secondary-dir layer))
                        (map cube-to-cartesian-flat)))
                 (range 1 (inc depth))))))

(def sectors
  ;; TODO: figure out how big to make it
  (mapv #(build-sector % 5) (range 6)))

(defn scale [pts s]
  (for [[x y] pts]
    [(* x s) (* y s)]))

(defn hex [r]
  (scale flat-hexagon-points r))
