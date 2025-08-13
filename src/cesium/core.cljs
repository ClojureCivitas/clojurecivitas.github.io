(ns cesiumdemo.core
  (:require
   [clojure.protocols :as cljp]
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [cesiumdemo.widget :as ces]
   [cesiumdemo.cesium :as c]
   [cesiumdemo.time :as time :refer [->jd add-days iso-str interval]]
   [cesiumdemo.util :as util]
   [cesiumdemo.etl  :as etl]
   [promesa.core :as p]
   ))

(def color-schemes
  {:red          {:colors {:equipment [255 0 0 125]}}
   :orange       {:colors {:equipment [255, 165, 0, 125]}}
   :green        {:colors {:equipment [181 230 29 125]}}
   :red-trans    {:colors {:equipment [255 0 0 50]
                           :pax       [0, 59, 255  50]}}
   :orange-trans {:colors {:equipment [255, 165, 0, 50]
                           :pax       [0, 59, 255 50]}}
   :green-trans  {:colors {:equipment [181 230 29 50]
                           :pax       [0, 59, 255 50]}}})

(defonce app-state (r/atom {:colors (-> color-schemes :orange-trans :colors)}))

(defn get-color [k] (-> @app-state :color (get k)))

;;Date/Time Junk
;;We have to convert between js/Date and cesium's julian
;;date quite a bit.  For the moment, we do stuff fairly manually.
;;We have the current time the app was launched and then
;;provide some convenience functions around it for adding days,
;;converting to julian, etc.

(def +now+ (new js/Date))

(def shared-clock
  (js/Cesium.ClockViewModel.))

(def viewer-options
  {:skyBox false
   :baseLayerPicker true
   :imageryProvider false #_(js/Cesium.TileMapServiceImageryProvider.
                             #js{:url  (js/Cesium.buildModuleUrl. "Assets/Textures/NaturalEarthII")})
   ;;false ;;(-> local-layers :blue-marble)
   :geocoder true
   :resolutionScale 1.0
   :clockViewModel shared-clock
   })

(defn play! []
  (println  [:play (.-clockRange shared-clock)])
  (when-not (= (.-clockRange shared-clock)  js/Cesium.ClockRange.CLAMPED)
    (set! (.-clockRange shared-clock) js/Cesium.ClockRange.CLAMPED))
  (set! (.-shouldAnimate shared-clock) true))

(defn stop! []
  (println  [:stop (.-clockRange shared-clock)])
  (set! (.-shouldAnimate shared-clock) false))

(defn set-day! [n]
  (set! (.-currentTime shared-clock)
        (add-days (.-startTime shared-clock) n)))

;;state for the current time in days relative to now.
;;We may move this into the app-state or into reframe
;;with some views later. for now we just use global state
;;in ratoms.

;; (def c-time (reagent/atom 0))
;; (def c-init (reagent/atom (.-dayNumber (->jd +now+))))
;; (def c-day  (reagent/atom 0))

;; (defn current-day [^js/Cesium.JulianDate curr]
;;   (- (.-dayNumber curr) @c-init))

;; ;;hook up our event listener to the current view's clock.
;; ;;an option here could be to just store the event listener in
;; ;;an atom and swap it out during reload.  This setup might
;; ;;have multiple listeners during dev.
;; (defn listen-to-time! []
;;   (.addEventListener (.-onTick (ces/clock))
;;                      (fn [e] (let [t  (.-currentTime  (ces/clock))
;;                                    d-prev @c-day
;;                                    d  (current-day t)]
;;                                (when (not= d-prev d)
;;                                  (reset! c-day d))))))

;;coerces a map of maps into czml js objects in the packet format
;;cesium expects.  The packet name will determine, per cesium, which
;;entity collection recieves the updates / changes defined by the entity
;;packets.  nils are skipped.
(defn ->czml-packets
  ([packet-name xs]
   (clj->js
    (concat [{:id "document"
              :name packet-name
              :version "1.0"}]
            (filter identity xs))))
  ([xs] (->czml-packets (gensym "packet") xs)))

(defn set-finish! [start stop]
  (set! (.-startTime shared-clock) (time/-julian start))
  (set! (.-stopTime shared-clock)  (time/-julian stop))
  (set! (.-clockRange shared-clock) js/Cesium.ClockRange.CLAMPED)
  (swap! app-state assoc :extents [start stop]))

#_
(defn simple-movement  [from to start stop]
  ;;just draw a straight line between from and to for now.
  (let [arc (str from "->" to "-arc" (rand))]
    ;;just draw a straight line between from and to for now.
    {:id   arc
     :name arc
     :polyline {:positions {:cartographicDegrees (mapv util/jitter- [(start :long) (start :lat) 100000
                                                                     (stop  :long) (stop  :lat) 200])}
                :material  {:solidColor {:color {:rgba (get-color :pax)}}}
                :width 3 #_(get @app-state :pax-origin-width 3)
                :clampToGround false
                :arcType  "NONE"}
     :properties {:move-type "simple"}}))

;;we want to build a data model for our fires.
(defn ->fire-entity [{:keys [start stop Lat_DD Long_DD FireName id] :as r}]
  (let [arc (str FireName "_" id)]
    ;;just draw a straight line between from and to for now.
    {:id   arc
     :name arc
     :polyline {:positions {:cartographicDegrees (mapv util/jitter- [Long_DD Lat_DD 100000
                                                                     Long_DD Lat_DD 200])}
                :material  {:solidColor {:color {:rgba [255, 165, 0, 255 #_50] #_(get-color :pax)}}}
                :width 3 #_(get @app-state :pax-origin-width 3)
                :clampToGround false
                :arcType  "NONE"}
     :availability (time/interval start stop)
     :properties r}))

(defn fire-events! []
  (let [fires (->> etl/fire-data (sort-by :start) (map ->fire-entity))
        [hd tl] [(first fires) (last fires)]
        [start stop] [(-> hd :properties :start) (-> tl :properties :stop)]
        _ (println [start stop])]
    ;;reverse order to ensure we don't skip time!
    (p/do! (ces/load-czml! (->czml-packets "moves" fires) :id :current)
           (set-finish! start stop)
           :done)))

(defn demo-click! []
  (fire-events!))
;;put your own here.
(set! (.-defaultAccessToken js/Cesium.Ion)
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiJiODFiNWRmOC02MDQ4LTRjNDktYjZmMy1kZjk0MDFjYWM4MDQiLCJpZCI6MzMyMTI0LCJpYXQiOjE3NTUyMDI3MTZ9.1L8bGdlixVLs_-YYNOBrrCkHikDAGEy7EPkSeEn3ukY")

(def bounds {:default   [-125.147327626 24.6163352675 -66.612171376 49.6742238918]
             :shifted   [-115.85193175578075 23.6163352675 -66.612171376 49.6742238918]
             :3d-us     {:position [-63215.181346188605 -9528933.76322208 6760084.984842104], :direction [0.007298723988826753 0.8268915851484712 -0.5623140003937158], :up [0.08733080420213787 0.5596533194968705 0.8241125485111495], :right [0.9961526284990382 -0.05512230389581595 -0.06812835201055821]}
             :3d-us-perspective {:position [-317622.8693122543 -9992858.180467833 4246834.783303648], :direction [0.031052476256112346 0.9868503489240992 -0.15862576255686647], :up [0.0037603323137679672 0.15858582933665222 0.9873380346337804], :right [0.9995106820936202 -0.03125577645796077 0.001213597444119795]}
             :3d-us-small {:position [-762505.075345366 -8709290.1490951 4043268.4155746778], :direction [0.09961461776873652 0.9857124352588807 -0.13582313095638476], :up [0.05184249751899356 0.1311751752861519 0.9900027418344055], :right [0.9936746365776786 -0.10566015504746376 -0.038034829532472586]}
             :inset-atlantic {:position [-5415797.494191807 4642953.337264422 10653133.091939844], :direction [0 0 -1], :up [-2.2204460492503126e-16 1 0], :right [1 2.2204460492503126e-16 0]}
             :us        [-129.2	20.2	-62.7	51.1]
             :us-europe [-125.8	16.7	71.7	55.2]
             :europe    [-12.6	34.7	53.8	60.3]
             :us-asia   [88.7	5.3	-67.7	48.5]
             :us-hld   {:position [-1.1789283742143026E7 -851171.9394538645 4220213.20184823],
                        :direction [-0.014107715109319626 0.8223014117772767 -0.5688772807587698],
                        :up [-0.009758438829426674 0.5687935771746235 0.8224224215307534],
                        :right [0.9998528617981781 0.01715385536820768 2.833893553849664E-10]}})

(defn cesium-root
  ([opts]
   (fn []
     [:div.cesiumContainer opts
      [ces/cesium-viewer {:name "cesium"
                          :opts viewer-options
                          :extents (bounds :3d-us)}]]))
  ([] (cesium-root {:class "fullSize"})))

(defn page [ratom]
  [:div
   [cesium-root]
   [:div.flexControlPanel {:style {:display "flex" :width "100%" :height "auto" #_"50%"}}
    [:button.cesium-button {:style {:flex "1"} :id "play" :type "button" :on-click #(play!)}
     "play"]
    [:button.cesium-button {:style {:flex "1"} :id "stop" :type "button" :on-click #(stop!)}
     "stop"]
    [:button.cesium-button {:style {:flex "1"} :id "demo" :type "button" :on-click demo-click!}
     "Load Fires!"]]] )
;; Initialize App
(defn main []
;;  (reset! app-state default-state)
  (rdom/render [page app-state]
            (.getElementById js/document "app")))

(main)

#_
(defn ^:export main []
  (ces/set-extents! -125.147327626 24.6163352675 -66.612171376 49.6742238918)
  (dev-setup)
  (reload)
  (layers!)
  (listen-to-time!))

