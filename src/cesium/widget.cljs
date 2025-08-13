;;Splitting out our stateful
;;container into another namespace,
;;so that interactive changes at the
;;repl don't reload the viewer.
(ns cesiumdemo.widget
  (:require  [reagent.core :as r]
             [cesiumdemo.cesium :as cesium]))

;;we'll maintain some state for our globe....
(defonce view
  (r/atom {}))

(defn ^js/Cesium.Viewer get-view [id]
  (some-> view deref id))

(defn ^js/Cesium.Viewer current-view []
  (get-view :current))

(defn set-extents!
  ([x1 y1 x2 y2]
   (let [extent (js/Cesium.Rectangle.fromDegrees x1 y1 x2 y2)
         cam    js/Cesium.Camera]
     (set! (.-DEFAULT_VIEW_RECTANGLE cam)  extent)
     (set! (.-DEFAULT_VIEW_FACTOR cam) 0)))
     ;(aset js/Cesium.Camera "DEFAULT_VIEW_RECTANGLE" extent)
     ;(aset js/Cesium.Camera "DEFAULT_VIEW_FACTOR" 0)))
  ([camera x1 y1 x2 y2]
   (let [extent (js/Cesium.Rectangle.fromDegrees x1 y1 x2 y2)]
     (.setView camera #js{:destination extent}))))

(defn current-position-cart [& {:keys [id] :or {id :current}}]
  (let [camera (.-camera (get-view id))
        cart   (.clone (.-positionCartographic camera))]
    cart))

(defn current-position [& {:keys [id] :or {id :current}}]
  (let [camera (.-camera (get-view id))
        cart   (.clone (.-positionCartographic camera))]
    [(js/Cesium.Math.toDegrees (.-longitude cart))
     (js/Cesium.Math.toDegrees (.-latitude cart))
     (.-height cart)]))

(defn current-rectangle [& {:keys [id] :or {id :current}}]
  (let [v   (get-view id)
        cam (.-camera v)
        ellipsoid (.-ellipsoid (.-globe (.-scene v)))
        result (js/Cesium.Rectangle.)]
    (.computeViewRectangle cam ellipsoid result)
    result))

(defn rect-degrees [r]
  {:west (js/Cesium.Math.toDegrees  (.-west r))
   :south (js/Cesium.Math.toDegrees (.-south r))
   :east (js/Cesium.Math.toDegrees  (.-east r))
   :north (js/Cesium.Math.toDegrees (.-north r))})

(defn set-camera-position! [[lng lat h] & {:keys [id] :or {id :current}}]
  (let [cam (.-camera (get-view id))]
    (set! (.-position cam)
          (js/Cesium.Cartesian3.
           (js/Cesium.Math.toRadians lng)
           (js/Cesium.Math.toRadians lat)
           h))))

;;var viewer = new Cesium.Viewer('cesiumContainer');
;;need to add options....
(defn ->viewer [el {:keys [] :as opts}]
  (js/Cesium.Viewer. el (clj->js opts)))

(defn ^js/Cesium.Clock clock [& {:keys [id] :or {id :current}}]
  (-> view deref (get id) .-clock))

(defn cart3->vec [c]
  [(.-x c) (.-y c) (.-z c)])

(defn vec->cart3 [[x y z]]
  (js/Cesium.Cartesian3. x y z))

(defn save-camera [& {:keys [id] :or {id :current}}]
  (let [v (get-view id)
        cam (.-camera v)]
    {:position  (cart3->vec (.-position cam))
     :direction (cart3->vec (.-direction cam))
     :up        (cart3->vec (.-up cam))
     :right     (cart3->vec (.-right cam))
                                        ;:transform (.clone (.-transform cam))
                                        ;:frustum   (.clone (.-frustum cam))
     }))

(defn load-camera! [{:keys [position direction up right #_transform #_frustum]}
                    & {:keys [id] :or {id :current}}]
  (let [v   (get-view id)
        cam (.-camera v)
        [position direction up right]
        (map vec->cart3 [position direction up right])]
    (set! (.-position cam) position)
    (set! (.-direction cam) direction)
    (set! (.-up cam) up)
    (set! (.-right cam) right)
    #_(set! (.-transform cam) transform)
    #_(set! (.-frustum cam) frustum)))

(defn set-view! [coords & {:keys [id] :or {id :current}}]
  (cond (map? coords)  (load-camera! coords :id id)
        (vector? coords)
          (let [[lat1 lng1 lat2 lng2] coords]
            (-> (get-view id)
                (.-camera)
                (set-extents! lat1 lng1 lat2 lng2)))
          :else (throw (ex-info "unknown view coordinates..." {:in coords}))))

;;basic reagent component.
(defn cesium-viewer [{:keys [name opts id extents] :or {id :current}}]
  (let [vw (keyword (str name "-view"))
        my-node (atom nil)]
    (r/create-class
     {:display-name (str name)
      :reagent-render (fn [] [:div {:ref (fn [this] (reset! my-node this))}])
      :component-did-mount
      (fn [this]
        (let [v (->viewer @my-node opts)
              _ (when-let [rscale (opts :resolutionScale)]
                  (set! (.-resolutionScale v) rscale))
              _ (swap! view assoc id v)
              _ (when extents
                  (set-view! extents :id id))]
          v))})))



;;stateful API for working with a "Single" current view....
(defn load-czml! [coll & {:keys [id] :or {id :current}}]
  (let [p (js/Cesium.CzmlDataSource.load coll)
        v  (@view id)]
    (.add  (.-dataSources v)
           p)
    p))

(defn load-kml! [path & {:keys [id] :or {id :current}}]
  (let [v (get @view id)
        p (js/Cesium.KmlDataSource.load path #js{:camera (.. v -scene -camera)
                                                 :canvas (.. v -scene -canvas)})]
    (.add  (.-dataSources v)
           p)
    p))

(defn load-geojson! [path & {:keys [style id] :as opts :or {id :current}}]
  (let [style (if style style
                  {:stroke js/Cesium.Color.BLACK
                   :fill  (js/Cesium.Color.GREY.withAlpha 0.5),
                   :strokeWidth 3})
        v (@view id)
        p (js/Cesium.GeoJsonDataSource.load path #js{:camera (.. v -scene -camera)
                                                     :canvas (.. v -scene -canvas)
                                                     :stroke (style :stroke)
                                                     :fill (style :fill)
                                                     :strokeWidth 20 #_(style :strokeWidth)} #_style)
        #_#__ (aset p "clampToGround" true)]
    (.add  (.-dataSources v)
           p)
    p))

(defn add-primitive! [prim & {:keys [id] :or {id :current}}]
  (let [v (get-view id)
        scene (.-scene v)
        primitives (.-primitives scene)]
    (.add primitives prim)))

(defn remove-primitive! [prim & {:keys [id] :or {id :current}}]
  (let [v (get-view id)
        scene (.-scene v)
        primitives (.-primitives scene)]
    (.remove primitives prim)))

(defn set-quality! [quality & {:keys [low-res-percent id]
                                 :or {low-res-percent 0.7
                                      id :current}}]
  (let [v     (get-view id)
        scene (.-scene v)
        low-quality (case quality :low true false)]
    (set! (.-resolutionScale v)  (if low-quality low-res-percent 1.0))
    (set! (.-fxaa scene)   (not low-quality))
    (set! (.-sunBloom scene) (not low-quality))
    (set! (.-show (.-skyAtmosphere scene)) (not low-quality))
    (set! (.-enabled (.-fog scene)) (not low-quality))
    (set! (.-shadows v) (not low-quality))
    (set! (.-terrainShadows v) (not low-quality))
    (set! (.-enabled (.-shadowMap scene)) (not low-quality))))
