(ns cesiumdemo.util
  (:require [clojure.protocols :as cljp]))

(defn rgb->hex [[r g b]]
  (js/rgbToHex r g b))

(defn hex->rgb [hx]
  (js/hexToRgb hx))


;;define a wrapping midpoint formula for longitude.  we ignore latitude for the moment, but
;;the same technique can apply with different bounds (180 vs 360).

;;so basically we project negative longitude onto a bounded interval (e.g. [0 360]).
;;We want to compute the midpoint relative to l.  If l and are positive, its our trivial
;;r - l /2.  Otherwise, if one is negative, we need to project them onto a cohesive interval
;;(e.g. a circle).  compute the distance there, then apply the distance to the midpoint to
;;l and project back into the original space.

(defn project [x bound]
  (if (neg? x)
    (+ bound x)
    x))

(defn invert [x bound]
  (if (> x (/ bound 2.0))
    (- x bound)
    x))

;;we probably want the midpoint that is the minimum distance too....although
;;there are probably exceptions.  At the least, this will correct westerly travel.
(defn wrapping-mid
  ([s d bound]
   (let [sy    (project s bound)
         dy    (project d bound)
         distance (- dy sy)
         d     (/ distance 2.0)
         mpy   (+ sy d)]
     (invert mpy bound)))
  ([s d] (wrapping-mid s d 360)))

(defn midpoint [[t0 x0 y0 z0] [t1 x1 y1 z1]]
  [(+ t0 (/ (- t1 t0) 2.0))
   (+ x0 (/ (- x1 x0) 2.0))
   (+ y0 (/ (- y1 y0) 2.0))
   (+ z0 (/ (- z1 z0) 2.0))])

(defn wrapped-midpoint [[t0 x0 y0 z0] [t1 x1 y1 z1]]
  [(+ t0 (/ (- t1 t0) 2.0))
   (wrapping-mid x0 x1) #_(+ x0 (/ (- x1 x0) 2.0))
   (+ y0 (/ (- y1 y0) 2.0))
   (+ z0 (/ (- z1 z0) 2.0))])

(defn min-direction [[_ x0 _ _] [_ x1 _ _]]
  (cond (and (pos? x0) (pos? x1))
        (if (< x0 x1) :east :west)
        (and (neg? x0) (neg? x1))
        (if (< x0 x1) :east :west)
        (pos? x0) :east
        :else :west))

;;maybe we just uses cesium to lerp for us.
(def wgs84 js/Cesium.Ellipsoid.WGS84)

;;-157.5744 21.1955 0
(defn ->carto [long lat height]
  (js/Cesium.Cartographic.fromDegrees long lat height))

(defn carto->long-lat [c]
  [(js/Cesium.Math.toDegrees (.-longitude c))
   (js/Cesium.Math.toDegrees (.-latitude  c))])


(defprotocol ICarto
  (as-carto [this])
  (as-degrees [this]))

(extend-protocol ICarto
  clojure.core/PersistentMap
  (as-carto [this]
    (->carto (get this :long) (get this :lat) (get this :height 0)))
  (as-degrees [this]
    this)
  js/Cesium.Cartographic
  (as-carto [this] this)
  (as-degrees [this]
    (carto->long-lat this))
  clojure.core/PersistentVector
  (as-carto [this] (->carto (nth this 0) (nth this 1) (nth this 2)))
  (as-degrees [this] this))


;;so if we want to interpolate, we can create a geodisic between two points.
;;pulling in from degrees.  typically expecting radians.
;;so need to convert.  let's do lat long...we assume maps of {:keys [lat long]}
;;for the moment.
(defn ->geodesic [start end]
  (js/Cesium.EllipsoidGeodesic. (as-carto start) (as-carto end) wgs84))


(defn lerpn-1d [n xs]
  (if (zero? n)
    (dedupe xs) ;;cheesy way to clean up, perf isn't a priority though.
    (recur (dec n)
           (apply concat
                  (for [[l r] (partition 2 1 xs)]
                    [l (+ l (/ ( - r l) 2.0)) r])))))

;;naive linear interpolation.  simplistic.
;;probably faster to use double arrays.
(defn lerpn [n xs]
  (if (zero? n)
    xs
    (recur (dec n)
           (apply concat
                  (for [[l r] (partition 2 1 xs)]
                    [l (midpoint l r) r])))))

;;helper to give us a nice quad representation.
;;czml/cesium defaults to using flat arrays,
;;but it's nicer to parse them out.
(defn ensure-partitions [n xs]
  (let [x (first xs)]
    (cond (and (coll? x) (= (count x) n))
          xs
          :else (partition n xs))))

;;meant to be used with ->>
;;if pred is truthy, applies f to rest,
;;otherwise passes xs through)
(defn apply-if [pred f & xs]
  (if pred
    (apply f xs)
    xs))

(defn catvec [xs]
  (->> xs
       (apply concat)
       vec))

(defn assoc-some [m & xs]
  (->> xs
       (partition   2)
       (filter second)
       (reduce (fn [acc [k v]]
                 (assoc! acc k v))
               (transient m))
       persistent!))


;;create a sequence of relative positions from one.
;;scales the relative time dimension by an optional scaling
;;parameter, in seconds.  Cesium also has the annoying
;;habit of wanting to use textual julian dates for everything.
;;So it's probably easier to go the "epoch" route and
;;define relative dates.  This is a fundamental building
;;block for entities...
(defn ->sampled-vec
  [txyz-coll & {:keys [type scale lerp-level interp-degree interp-algorithm epoch interval degree]
                :or {lerp-level 0
                     ;interp-degree 5
                     ;interp-algorithm "LAGRANGE"
                     degree 3} :as opts}]
  (let [scaler (if (and scale (not= scale 1))
                 (fn [[t y x z]]
                   [(* scale t) y x z])
                 identity)
        type (or type (-> txyz-coll meta :czml/type) :cartesian)]
    (-> {type (as-> (ensure-partitions (inc degree) txyz-coll) <parts>
                (if (and lerp-level (pos? lerp-level))
                  (lerpn lerp-level <parts>)
                  <parts>)
                (map scaler <parts>)
                (catvec <parts>))} ;;maybe not ideal, but fast enough for us!
        (assoc-some
         :epoch                  epoch
         :interpolationAlgorithm interp-algorithm
         :interpolationDegree    interp-degree))))

;;kind of lame, but let's do it.
(defn ->sampled-degrees [txyz-coll & {:keys [scale lerp-level interp-degree interp-algorithm epoch interval] :as opts}]
  (->sampled-vec txyz-coll
   :type :cartographicDegrees
   :scale scale :lerp-level lerp-level :interp-degree interp-degree :interp-algorithm interp-algorithm
   :epoch epoch :interval interval :degree 3))

(defn interpolate [m & {:keys [interp-algorithm interp-degree]
                        :or   {interp-degree 5
                               interp-algorithm "LAGRANGE"}}]
  (assoc m  :interpolationAlgorithm interp-algorithm
         :interpolationDegree    interp-degree))

(defn ->sampled-rgba [txyz-coll &
                      {:keys [scale lerp-level interp-degree interp-algorithm epoch interval]
                       :as opts}]
  (->sampled-vec txyz-coll
                 :type :rgba
                 :scale scale :lerp-level lerp-level :interp-degree interp-degree :interp-algorithm interp-algorithm
                 :epoch epoch :interval interval :degree 3))


(defn jitter+ [n]
  (+ n (* (rand) 0.25)))

(defn jitter- [n]
  (- n (* (rand) 0.25)))

(defn jitter-xyz [[sx sy sz] [x y z]]
  [(+ x (* sx (rand)))
   (+ y (* sy (rand)))
   (+ z (* sz (rand)))])

(defn jitter-xyz*
  ([scale xs]
   (let [[sx sy sz] scale]
     (->> xs
          (ensure-partitions 3)
          (map #(jitter-xyz scale %)))))
  ([xs] (jitter-xyz* [1 1 1] xs)))

(defn jitter-txyz [[sx sy sz] [t x y z]]
  [t
   (+ x (* sx (rand)))
   (+ y (* sy (rand)))
   (+ z (* sz (rand)))])

(defn jitter-txyz*
  ([scale xs]
   (let [[sx sy sz] scale]
     (->> xs
          (ensure-partitions 4)
          (map #(jitter-txyz scale %)))))
  ([xs] (jitter-txyz* [1 1 1] xs)))

(defn geo-jitter* [xs]
  (jitter-txyz* [0.25 0.25 0] xs))

(defn -geo-jitter* [xs]
  (jitter-txyz* [-0.25 -0.25 0] xs))

(defn geo-jitter [x]
  (jitter-txyz [0.25 0.25 0] x))

(defn ->splinexyz [times points]
  (js/Cesium.CatmullRomSpline.
   (clj->js {:times times
             :points (for [[x y z] points]
                       (js/Cesium.Cartesian3. x y z))})))

(defn ->splinellh [times points]
  (js/Cesium.CatmullRomSpline.
   (clj->js {:times times
             :points (for [[y x z] points]
                       (js/Cesium.Cartesian3. y x z))})))

(defn spline-degrees [n coords]
  (let [coords (ensure-partitions 4 coords)
        times (map first coords)
        s (->splinellh times
                       (map rest coords))]
    (->> times
         (lerpn-1d n)
         (map (fn [t]  (let [res (.evaluate s t)]
                         [t (.-x res) (.-y res) (.-z res)]))))))

;;this gets us much better results, but we are doing the geodesic arc type
;;more or less computing it by hand.  all we really want is it to get the
;;minimum direction, compute a heading, and then feed that.  Since
;;the gis system understands -180+ degrees (and probably 180+) and
;;can figure it out for us, we just get our end point computed,
;;then go back and compute our midpoint like normal to get a straight
;;line.  Then feed that into our legacy routine maybe.
(defn geo-spline-degrees [n from to]
  (let [[t0 x0 y0 h0] from
        [t1 x1 y1 h1] to
        gd (->geodesic [x0 y0 h0] [x1 y1 h1])
        dt (- t1 t0)
        dz (- h1 h0)
        [mx my] (as-degrees (.interpolateUsingFraction gd 0.5))
        mp [(+ t0 (/ (- t1 t0) 2.0)) mx my (+ h0 (/ (- h1 h0) 2.0))]
        coords [from mp to]
        times  (map first coords)]
    (->> times
         (lerpn-1d n)
         (map (fn [t]
                (let [prog (/ (- t t0) dt)
                      res  (as-degrees (.interpolateUsingFraction gd prog))]
                  [t (res 0) (res 1) (+ h0 (* prog dz))]
                  ))))))

;;returns [from midpoint to]
(defn oriented-coords [from to]
  (let [[t0 x0 y0 h0] from
        [t1 x1 y1 h1] to
        gd (->geodesic [x0 y0 h0] [x1 y1 h1])
        [fx fy] (as-degrees (.interpolateUsingFraction gd 1.0)) ;;new end point
        mp [(+ t0 (/ (- t1 t0) 2.0)) (+ x0 (/ (- fx x0) 2.0)) (+ y0  (/ (- fy y0) 2.0))  (+ h0 (/ (- h1 h0) 2.0))]
        coords [from mp [t1 fx fy h1]]]
        coords))

(defn dedupe-by [keyf xs]
  (let [ks (atom #{})]
    (into [] (filter (fn [x]
                       (let [known @ks
                             k     (keyf x)]
                         (if (known k)
                           false
                           (do (swap! ks conj k)
                               true)))))
          xs)))


(defn ->spline2d [times points]
  (js/Cesium.CatmullRomSpline.
   (clj->js times)
   (clj->js (for [[x y] points]
              (js/Cesium.Cartesian2 x y)))))


(defn precision [n k]
  (js/parseFloat (.toPrecision n k)))
