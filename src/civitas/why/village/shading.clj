(ns civitas.why.village.shading
  (:require [thi.ng.color.core :as col]))

(defn normalize [[x y z]]
  (let [m (Math/sqrt (+ (* x x) (* y y) (* z z)))]
    [(/ x m) (/ y m) (/ z m)]))

(defn dot [[ax ay az] [bx by bz]]
  (+ (* ax bx) (* ay by) (* az bz)))

(def light-dir (normalize [1 1 1]))

(defn shade-color [hex normal]
  (let [n (normalize normal)
        lighting (max 0.3 (dot n light-dir)) ;; never go below 30%
        c (col/hex hex)]
    (-> c
        (col/scale lighting)
        col/as-hex)))

(defn face-normal [verts face]
  (let [[a b c] (map verts face)
        [ax ay az] a
        [bx by bz] b
        [cx cy cz] c
        u [(- bx ax) (- by ay) (- bz az)]
        v [(- cx ax) (- cy ay) (- cz az)]
        [ux uy uz] u
        [vx vy vz] v]
    [( - (* uy vz) (* uz vy))
     ( - (* uz vx) (* ux vz))
     ( - (* ux vy) (* uy vx))]))

(defn shade-faces [verts faces base-color]
  (mapv (fn [f]
          (shade-color base-color (face-normal verts f)))
        faces))
