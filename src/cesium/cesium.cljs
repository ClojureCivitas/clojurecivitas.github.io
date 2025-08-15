(ns cesiumdemo.cesium
  (:require  [reagent.core :as r]
             [clojure.core]
             [clojure.protocols :as cljp]))

(def ^:const +wgs-84+
  js/Cesium.Ellipsoid.WGS84)

;;aux functions
(defn radians [x] (js/Cesium.Math.toRadians x))

;;this doesn't work per the tutorial...
(def +default-offset+
  (js/Cesium.HeadingPitchRange.
   (radians 90)
   (radians -45)))

;;basic cesium stuff
(defprotocol ICartesian
  (-as-cartesian [c]))

#_(defn degree-heights->carts)

;;this lets us use vectors
;;interchangably with cartesian
;;types in cesium.
(extend-protocol ICartesian
  js/Cesium.Cartesian3
  (-as-cartesian [c] c)
  js/Cesium.Cartesian2
  (-as-cartesian [c] c)
  js/Cesium.Cartesian4
  (-as-cartesian [c] c)
  clojure.core/PersistentVector
  (-as-cartesian [c]
    (case (count c)
      2 (let [[x y] c]
          (js/Cesium.Cartesian2. x y))
      3 (let [[x y z] c]
          (js/Cesium.Cartesian3. x y z))
      4 (let [[a b c d] c]
          (js/Cesium.Cartesian3. a b c d)))))  

;;probably create a point protocol and
;;extend it to vectors, like as-cartesian
(defn ->cartesian 
  ([x y]
   (js/Cesium.Cartesian2. x y))
  ([x y z]
   (js/Cesium.Cartesian3. x y z))
  ([x y z w]
   (js/Cesium.Cartesian4. x y z w)))

(defn degrees->cartesian 
  ([lat long height ellipsoid]
   (js/Cesium.Cartesian3.fromDegrees lat long height ellipsoid))
  ([lat long height]
   (degrees->cartesian lat long height +wgs-84+))  
  ([lat long]
   (degrees->cartesian lat long 0 +wgs-84+)))

(defn radians->cartesian 
  ([lat long height ellipsoid]
   (js/Cesium.Cartesian3.fromRadians lat long height ellipsoid))
  ([lat long height]
   (degrees->cartesian lat long height +wgs-84+))  
  ([lat long]
   (degrees->cartesian lat long 0 +wgs-84+)))

;;Also want coercions for degree sequences
;;to cart 3 coord....
(defn degrees->cart3 [xs]
  (js/Cesium.Cartesian3.fromDegreesArray
   (clj->js (vec xs))))

;;more generic, data-driven version
;;since the only version that supports
;;this is cart3....we can get away.
;;Most of the cesium examples show
;;the Array methods (i.e. ingest and
;;return js Arrays).
(defn degrees->cartesians
  "Given a sequence of [lat1, long1, lat2, long2] pairs
   or [lat1, long1, height1, lat2, long2, height2] triples
   coerces to a Cartesian3 based on the specified encoding
   and optional ellipsoid.  lat/long are encoded as degrees"
  ([n xs]
   (degrees->cartesians n +wgs-84+ xs))
  ([n ellipsoid xs]
   (let [f (case n
             2  (fn [lat long]
                  (degrees->cartesian lat long 0 ellipsoid))
             3  (fn [lat long height]
                  (degrees->cartesian lat long 0 ellipsoid)))]
     (->> xs
          (partition n)
          (map #(apply f %))))))

(defn radians->cartesians
  "Given a sequence of [lat1, long1, lat2, long2] pairs
   or [lat1, long1, height1, lat2, long2, height2] triples
   coerces to a Cartesian3 based on the specified encoding
   and optional ellipsoid.  lat/long are encoded as radians"
  ([n xs]
   (radians->cartesians n +wgs-84+ xs))
  ([n ellipsoid xs]
   (let [f (case n
             2  (fn [lat long]
                  (radians->cartesian lat long 0 ellipsoid))
             3  (fn [lat long height]
                  (radians->cartesian lat long 0 ellipsoid)))]
     (->> xs
          (partition n)
          (map #(apply f %))))))

;;Generate a random lat/long
;;along the respective domains
;;[-90 89]
;;[-180 179]
(defn random-point []
  [(- (rand-int 90) 89)
   (- (rand-int 180) 179)])

;;n random coordinates.
(defn random-coords [n]
  (repeatedly n (fn []
                   (random-point))))

;;Generic color stuff
;;probably useful to have an as-color....
;;We can get a simple color bridge, going
;;from
(def red (js/Cesium.Color.RED.withAlpha 0.5))
(def black js/Cesium.Color.BLACK)

;;we can define a protocol here...
;;might be useful if we wrap the viewer object
;;and delegate.

;;Entity Operations
(defn entities [v]
  (-> v .-entities))

(defn select-entity! [v e]
  (-> v .-selectedEntity (set! e)))

(defn track-entity! [v e]
  (-> v .-trackedEntity (set! e)))

(defn add-entity! [v x]
  (let [es (.-entities v)]
    (.add es x)))

(defn add-entities! [v xs]
  (reduce (fn [acc e]
            (doto acc (.add e)))
          (.-entities v)
          xs))

;;note: this doesn't work with polygons, as the
;;tutorial depicts!  We either need a protocol or
;;another function to help us out here...
(defn set-position! [e pos]
  (-> e .-position (set! pos)))

(defn set-height! [e h]
  (-> e .-polygon .-height (set! h)))

(defn set-material! [e m]
  (-> e .-polygon .-material (set! m)))

(defn extrude-height! [e h]
  (-> e .-polygon .-extrudedHeight (set! h)))

(defn set-description! [e d]
  (-> e .-description (set! d)))

;; var heading = js/Cesium.Math.toRadians(90);
;; var pitch = js/Cesium.Math.toRadians(-30);

;;Primitive Operations



;;View API (zooming, flying, selecting)
(defn zoom-to!
  ([v]
   (.zoomTo v (.-entities v)))
  ([v e]
   (.zoomTo v e))
  ([v e offset]
   (.zoomTo v e offset)))

;;look at decoupling the
;;promise, or messing
;;with the promise chain
;;via a macro or something.

(defn fly-to!
  ([v e]
   (-> v
      (.flyTo e)
      (.then (fn [result]
               (when result
                 (select-entity! v e))))))
  ([v e offset]
   (-> v
       (.flyTo e offset)
       (.then (fn [result]
                (when result
                  (select-entity! v e)))))))

(defn ->point [lat long]
    {
     :position (js/Cesium.Cartesian3.fromDegrees lat long),
     :point {
             :pixelSize 5,
             :color js/Cesium.Color.RED,
             :outlineColor js/Cesium.Color.WHITE,
             :outlineWidth 2}})

(defn ->random-points [n]
  (for [[lat lng] (random-coords n)]
    (->point lat lng)))


(defn obj-lookup [obj k]
  (if-not (keyword? k)
    (aget obj k)
    (aget obj (name k))))

(defprotocol IEntities
  (-entities [this]))

(defprotocol IProperty
  (-properties [e])
  (-property   [e k]))

;;protocol helpers...
(extend-protocol clojure.core/ILookup
  js/Cesium.EntityCollection
  (-lookup
    ([e k] (.getById e k))
    ([e k nf] (or (.getById  e k) nf)))
  js/Cesium.Entity
  (-lookup
    ([e k] (obj-lookup e k))
    ([e k nf] (or (obj-lookup e k) nf)))
  js/Cesium.DataSourceCollection
  (-lookup
    ([e k] (obj-lookup e k))
    ([e k nf] (or (obj-lookup e k) nf)))
  )

(extend-protocol clojure.core/INamed
  js/Cesium.Entity
  (-name [e] (.-id e)))

(extend-protocol clojure.core/ISeqable
  js/Cesium.EntityCollection
  (-seq [e]
    (for [ent (.-values e)]
      [(name ent) ent nil])))

;;no deftype in sci
(defrecord #_deftype lazy-props [ent]
  clojure.core/ILookup
  (-lookup [this k]
    (when-let [res (some-> (aget ent "properties") (obj-lookup k))]
      (.-_value res)))
  (-lookup [this k nf]
    (if-let [res (some-> (aget ent "properties") (obj-lookup k))]
      (.-_value res)
      nf))
  clojure.core/ISeqable
  (-seq [this]
    (let [props (aget ent "properties")]
      (for [k (some-> props (aget "propertyNames"))]
        [ k (aget props k) nil])))
  #_#_#_
  clojure.core/IFn
  (-invoke [this k] (-lookup this k))
  (-invoke [this k nf] (-lookup this k nf)))

(extend-protocol clojure.core/IMeta
  js/Cesium.Entity
  (-meta [this]
    (lazy-props. this)))

;;doesn't work like I'd want it too...
#_
(extend-protocol IEntities
  js/Cesium.Datasource
  (-entities [this] (.-_entityCollection this))
  js/Cesium.CzmlDataSource
  (-entities [this] (.-_entityCollection this)))


