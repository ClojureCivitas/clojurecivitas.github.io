(ns cesiumdemo.time
  (:require [clojure.string]))

;;problem
;;czml wants intervals and epochs specifically in terms of julian dates,
;;which are its own type, and rendered as strings.  We tend to work with
;;js dates, and other structures.  We want to be able to trivially
;;convert between all this stuff, do generic operations, mix and match, etc.

(defprotocol IPortableTime
  (-add-days    [this d])
  (-julian      [this])
  (-date        [this]))

(defprotocol IIso8601
  (-isostring   [this]))


(extend-type js/Date
  IPortableTime
  (-add-days    [this d]
    (let [res (new js/Date this)
          _  (.setDate res (+ (.getDate res ) d))]
      res))
  (-julian      [this]
    (js/Cesium.JulianDate.fromDate this))
  (-date        [this] this)
  IIso8601
  (-isostring    [this] (-> this -julian -isostring)))

(extend-type js/Cesium.JulianDate
  IPortableTime
  (-add-days    [this d]
    (let [res (.clone this)
          _   (js/Cesium.JulianDate.addDays res d res)]
      res))
  (-julian      [this] this)
  (-date        [this] (js/Cesium.JulianDate.toDate this))
  IIso8601
  (-isostring    [this] (str this)))

(extend-type  string
  IPortableTime
  (-add-days    [this d]   (-add-days (-julian this) d))
  (-julian      [this] (if (clojure.string/ends-with? this "Z")
                         (js/Cesium.JulianDate.fromIso8601 this)
                         (-julian (js/Date. this))))
  (-date        [this] (if (clojure.string/ends-with? this "Z")
                         (-date (js/Cesium.JulianDate.fromIso8601 this))
                         (js/Date. this)))
  IIso8601
  (-isostring   [this] (if (clojure.string/ends-with? this "Z")
                         this
                         (-isostring (-julian this)))))

(defn ^js/Cesium.JulianDate ->jd [d]
  (js/Cesium.JulianDate.fromDate d))

(defn add-days [date d]
  (-add-days date d))

(defn add-seconds [date s]
  (let [d (-julian date)
        newd (js/Cesium.JulianDate.)]
    (js/Cesium.JulianDate.addSeconds d s newd)
    newd))
;;could protocolize this, but I don't have a use case beyond vectors at the
;;moment...
(defn interval
  ([lr]
   (assert (and (coll? lr) (= (count lr) 2)) "Expected an interval pair!")
   (interval (first lr) (second lr)))
  ([l r] (str (-isostring l) "/" (-isostring r))))

(defn iso-str [x]
  (-isostring x))
