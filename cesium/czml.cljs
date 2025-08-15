;;pending NS for defining helpers to go from
;;EDN->CZML easily.
(ns cesiumdemo.czml
  (:require [cesiumdemo.time :as time :refer [add-days interval iso-str]]
            [cesiumdemo.util :as util]
            [goog.object :as gobject]))

(def +day-seconds+  86400)
(def +hour-seconds+ 3600)

(defn ->path [id availability moves-t-long-lat-height
              & {:keys [material width leadTime trailTime resolution epoch lerp-level]
                 :or {material {:polylineOutline {:color {:rgba [255, 0, 255, 255]},
                                                  :outlineColor {:rgba [0, 255, 255, 255]}
                                                  :outlineWidth 5}}
                      width 8
                      resolution +day-seconds+
                      lerp-level 0}}]
  (let [pos (util/->sampled-degrees moves-t-long-lat-height
              :scale resolution :epoch epoch :lerp-level lerp-level)]
  {:id id
   :name (str "path" id)
   :description "a random path!"
   :availability availability,
   :path {:material material
          :width    width,
          :leadTime leadTime
          :trailTime trailTime
          :resolution resolution}
   :position pos}))

(defn ->arcing-path [id start-stop  moves
                     & {:keys [scale material width lerp-level]
                        :or {material {:solidColor
                                       {:color
                                        {:rgba [255 255 255 255]}}}
                             width 1
                             lerp-level 1}}]
  (let [[tstart tstop] (if (coll? start-stop)
                         start-stop
                         [start-stop nil])
        tstop (or tstop
                  (->> moves
                       (util/ensure-partitions 4)
                       (map first)
                       (reduce max)
                       (add-days tstart)))
        avail  (interval tstart tstop)]
    (-> (->path id
                avail
                moves
                :epoch (iso-str tstart)
                :leadTime 0
                :resolution +day-seconds+
                :material material
                :width width
                :lerp-level lerp-level)
        (update :position util/interpolate :interp-degree 5))))


(defn decompose-move [xs & {:keys [transit-height]}]
  (let [xs  (util/ensure-partitions 4 xs)
        [origin poe pod]         xs
        [origin transit1 poe :as mv1]   (util/lerpn 1 [origin poe])
        [t y x z] poe
        [poe2 transit2 pod :as mv2]     (util/lerpn 1 [[(+ t 0.001) y x z] pod])
        mv2 (if transit-height [poe2 (assoc transit2 3 transit-height) pod] mv2)]
    (concat (util/spline-degrees 1 mv1)
            (util/spline-degrees 1 mv2))))

;;one thing we want to do is have a simple conversion
;;for our time/epoch stuff.  So we can do date/time manipulation,
;;even interval definition in clojure, then convert to czml and let
;;the conversion make sure our iso8601 dats and times are set where
;;appropriate.

(defprotocol IEncodeCZML
  (-clj->czml [this]))


;; (def isokeys
;;   {:availability  time/interval
;;    :interval      time/interval
;;    :currentTime   time/-isostring
;;    :epoch         time/-isostring
;;    :cartesian           encode-cartesian
;;    :cartographicDegrees encode-cartesian
;;    :rgba
;;    :rgbaf
;;    })

;;if cartesian or cartographicDegrees exists as a sibling of
;;epoch, and we have multiple samples, 

(defn key->js
  ([k] (key->js k clj->js))
  ([k primitive-fn]
   (cond
     ;;(satisfies? IEncodeJS k) (-clj->js k)
     (or (string? k)
         (number? k)
         (keyword? k)
         (symbol? k)) (primitive-fn k)
     :default (pr-str k))))

(defn clj->czml
  "Recursively transforms ClojureScript values to CZML, identical to
   clj->js since it returns js objects, but it coerces EDN and js
   values for dates czml compatible ones for us.

  sets/vectors/lists become Arrays, Keywords and Symbol become Strings,
  Maps become Objects. Arbitrary keys are encoded to by `key->js`.
  Options is a key-value pair, where the only valid key is
  :keyword-fn, which should point to a single-argument function to be
  called on keyword keys. Default to `name`."
  [x & {:keys [keyword-fn]
        :or   {keyword-fn name}
        :as options}]
  (letfn [(keyfn [k] (key->js k thisfn))
          (thisfn [x] (cond
                        (nil? x) nil
                        (satisfies? IEncodeCZML x) (-clj->czml x)
;;                        (satisfies? IEncodeJS x)   (-clj->js x) 
                        (keyword? x) (keyword-fn x)
                        (symbol? x) (str x)
                        (map? x) (let [m (js-obj)]
                                   (doseq [[k v] x]
                                     (aset m (keyfn k) (thisfn v)))
                                   m)
                        (coll? x) (let [arr (array)]
                                    (doseq [x (map thisfn x)]
                                      (.push arr x))
                                    arr)
                        :else x))]
    (thisfn x)))


;; ;;we want to provide a really simple cljs way to define
;; ;;valid CZML...

;; ;;property values:

;; 5 => 5
;; {_ 5}      => {_ 5}
;; {_ string} => {_ string}


;; ;;naked collections
;; [x y z w]   =>  {:cartesian [x y z w]}

;; [x y z w
;;  x y z w]   =>  {:cartesian [x y z w
;;                             x y z w]}
;; [[x y z w]
;;  [x y z w]] =>  {:cartesian [x y z w
;;                              x y z w]}

;; ^:degrees|:cartographicDegrees
;;  [[t y x z]
;;   [t y z z]]   =>   {:degrees|:cartographicDegrees [t x y z t x y z]}

;; ^:degrees|:cartographicDegrees
;; [t y x z
;;  t y z z]   =>   {:degrees|:cartographicDegrees [t x y z t x y z]}

;; {:interval [from to]|intervalstring
;;  :number   5} =>  {:interval "2012-04-30T12:00:00Z/14:00:00Z"
;;                   :number 5}

;; ;;collections are inferred to be property intervals.
;; [{:interval [from to]|intervalstring
;;   :number 5}

;;  {:interval [from to]|intervalstring
;;   :number 5}]

;; {:degrees|:cartographicDegrees
;;    [[t y x z]
;;     [t y z z]]}   =>   {:cartographicDegrees [t x y z t x y z]}

;; {:cartesian [[t x y z]
;;              [t x y z]]} =>  {:cartesian [t x y z t x y z]}

;; {:degrees|:cartographicDegrees
;;  [t y x z
;;   t y z z]}     =>  {:cartographicDegrees [t x y z t x y z]}

;; {:cartesian [t x y z
;;              t x y z]}   =>  {:cartesian [t x y z t x y z]}



;; ;;infinite interval, collection implies cartesian property.
;;  [t x y z] => {:cartesian  [t x y z]}


;;Random experiments with the API during path
;;construction...

;; (defn demo-path2 []
;;   (let [samples (geo-jitter* [0  -78.59   35.08   30000
;;                               3  -79.0052 35.1015 30000
;;                               22 13.4605  51.0804 1000])
;;         tstart +now+
;;         tstop  (add-days +now+ 22)
;;         avail  (interval tstart tstop)]
;;     (-> (->path "demo-path"
;;                 avail
;;                 samples
;;                 :epoch    (iso-str +now+)
;;                 :leadTime 0
;;                 :resolution +day-seconds+
;;                 :width    1
;;                 :material {:solidColor {:color        {:rgba [255 0 0 130]}}})
;;         (update :position util/interpolate :interp-degree 2 ))))

;; (defn tllh->carts [xs]
;;   (->> xs
;;        (util/ensure-partitions 4)
;;        (map (fn [[t lng lat h]]
;;               (js/Cesium.Cartographic. lng lat h)))))

;; (defn ^js/Cesium.Cartographic ->elipsoid-interp [n from to]
;;   (let [e    (js/Cesium.EllipsoidGeodesic. from to)
;;         step (/ 1.0 n)
;;         res  (js/Cesium.Cartographic.)]
;;     (for [i (concat (range 0.0 1.0 step) [1.0])]
;;       (let [res  (.interpolateUsingFraction e i)]
;;             [i (.-longitude res) (.-latitude res) (.-height  res)]))))

;; (defn ->elipsoid-path [n xs]
;;   (apply concat
;;     (for [[l r] (partition 2 1 (util/ensure-partitions 4 xs))]
;;       (let [[t1 lng1 lat1 h1] l
;;             [t2 lng2 lat2 h2] r
;;             dt (- t2 t1)
;;             [frac cart] (->elipsoid-interp n (js/Cesium.Cartographic. lng1 lat1 h1)
;;                                              (js/Cesium.Cartographic. lng2 lat2 h2))]
;;         [(+ t1 (* frac dt)) (.-long cart) (.-lat cart) (.-height cart)]))))


;; (defn demo-path3 []
;;   (let [moves (czml/decompose-move dummy-move :transit-height 500000)]
;;     [(czml/->arcing-path "blah" +now+
;;                          moves
;;                          :material {:solidColor {:color {:rgba [255 0 0 130]}}}
;;                          :lerp-level 0 :width 5)
;;      {:id "blah-billboard"
;;       :name "blah-bb"
;;       :billboard {:image "/icons/div.png"
;;                   :scale 0.1
;;                   :eyeOffset   {:cartesian [0 0 -100000]}
;;                   }
;;       :position {:reference "blah#position"}}]))


#_
(defn ->move [from transit to tstart tstop & {:keys [id imagery move-types]}]
  (let [id (or id (str "-move" (rand)))
        id-pos (str id "#position")
        bbid   (str id "-bb")
        {:keys [Patch Icon]} (or imagery
                                 (rand-nth ea/known-imagery))
        moves [from transit to]
        dynavail (time/interval tstart tstop)]
    [(when (move-types :pax)
       (-> (czml/->arcing-path id
                               tstart
                               (czml/decompose-move (util/geo-jitter* moves))
                               :transit-height (min (* (inc (rand 4)) 100000) 200000)
                               :material {:solidColor {:color {:rgba [255 0 0 175]}}}
                               :lerp-level 2 :width 1)
           (assoc :properties {:move-type "pax"})
           (assoc-in [:path :show] false)
           (update :position util/interpolate :interp-degree 5 )))

     (when (move-types :equipment)
       (-> (czml/->arcing-path (str id ":eq")
                               tstart
                               (czml/decompose-move (util/-geo-jitter* moves))
                               :transit-height (min (* (inc (rand 4)) 100000) 200000)
                               :material {:solidColor {:color {:rgba [255, 165, 0, 175]}}}
                               :lerp-level 2 :width 1)
           (assoc :properties {:move-type "equipment"})
           (assoc-in [:path :show] false)
           (update :position util/interpolate :interp-degree 5 )))
     {:id   bbid
      :name bbid
      :billboard {:image (ea/patch-path Patch)
                  :scale 0.25 #_0.35
                  :pixelOffset {:cartesian2 [0 0]}
                  :eyeOffset   {:cartesian [0 0 -10000]}}
      :position {:reference id-pos}
      :availability dynavail
      :properties {:billboard-type "patch"}}
     {:id   (str bbid "src")
      :name (str bbid "src")
      :billboard {:image (ea/icon-path Icon)
                  :scale 0.85 #_1.0
                  :pixelOffset {:cartesian2 [63 0]}
                  :eyeOffset   {:cartesian [0 0 -10000]}}
      :availability dynavail
      :position {:reference id-pos}
      :properties {:billboard-type "icon"}}]))

;; (defn movement->growing [mv start stop]
;;   (let [t1     (str (->jd start))
;;         t2     (str (->jd stop))
;;         t2+    (str (->jd stop))
;;         t3     (str (->jd (add-days stop 365)))
;;         from   (str (gensym "from"))
;;         to     (str (gensym "target"))
;;         dynavail  (str t1 "/" t2)
;;         staticavail (str t2+ "/" t3)
;;         {:keys [cartographicDegrees] :as m} (-> mv :polyline :positions)
;;         source {:id from
;;                 :name from
;;                 :availability dynavail
;;                 :position {:cartographicDegrees (vec (take 3 cartographicDegrees))}
;;                 :properties {:move-type "source"}}
;;         target {:id   to
;;                 :name to
;;                 :availability dynavail
;;                 :position {:cartographicDegrees (vec (concat (into [t1] (take 3 cartographicDegrees))
;;                                                              (into [t2] (drop 3 cartographicDegrees))
;;                                                              (into [t3] (drop 3 cartographicDegrees))))
;;                            :interpolationAlgorithm "LAGRANGE"}
;;                 :properties {:move-type "target"}}
;;         id     (mv :id)
;;         bbid (str "randmove" (rand))
;;         {:keys [Patch Icon]} (rand-nth ea/known-imagery)]
;;     [source
;;      target
;;      (-> mv
;;       (assoc-in [:polyline :positions] {:references [(str from "#position") (str to "#position")]})
;;       (assoc :availability dynavail :id (str id  "_dynamic")))
;;      ;;entity icons that follow the to of the move....
;;      {:id   bbid
;;       :name bbid
;;       :billboard {:image (ea/patch-path Patch)
;;                   :scale 0.35
;;                   :pixelOffset {:cartesian2 [0 0]}
;;                   :eyeOffset   {:cartesian [0 0 -500000]}}
;;       :position {:reference (str to "#position")}
;;       :availability dynavail
;;       :properties {:billboard-type "patch"}}
;;      {:id   (str bbid "src")
;;       :name (str bbid "src")
;;       :billboard {:image (ea/icon-path Icon)
;;                   :scale 1.0
;;                   :pixelOffset {:cartesian2 [63 0]}
;;                   :eyeOffset   {:cartesian [0 0 -500000]}}
;;       :position {:reference (str to "#position")}
;;       :properties {:billboard-type "icon"}
;;       :availability dynavail}
;;      (assoc mv :availability staticavail :id (str id "_static") :name (str (mv :name) "_static"))]))

;; (defn random-movement []
;;   (let [edge (rand-nth (keys connections))
;;         [from to] edge
;;         {:keys [start stop]} (connections edge)]
;;    [(equipment-movement from to start stop)
;;     (pax-movement from to start stop)]))

;; (defn time-based-movement
;;   [start duration mv]
;;   (let [pos1 (-> mv :polyline)]
;;     (movement->growing mv start  (add-days start duration))))

;; (defn random-time [init span]
;;   (str (->jd (add-days init (rand-int span)))
;;              "/"
;;              (->jd (add-days init 1000))))
