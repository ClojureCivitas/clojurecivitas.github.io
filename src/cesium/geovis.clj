^:kindly/hide-code
^{:clay {:title  "Working with Cesium"
         :quarto {:author      :joinr
                  :description "Looking at leveraging cesium for geospatial visualization."
                  :type        :post
                  :date        "2025-08-13"
                  :category    :clay
                  :tags        [:clay :workflow :scittle :reagent :cesium]}}}
(ns cesium.geovis
  (:require [scicloj.kindly.v4.kind :as kind]
            [tablecloth.api :as tc]
            [charred.api :as charred]))

;; # Geospatial Visualization With Clay and CesiumJS

;; This is a short port of a more sophisticated cljs application that leverages
;; cesiumjs for geospatial visualization, animation, and various interactive 3d
;; projections.  After seeing other civitas posts, I decided to try to port over
;; a subset of the functionality in a "lightweight" package, combined with
;; some analysis of open data (in this case, fires in Oregon), and then
;; build up an interactive view/animation of the result.

;; Clay affords a nice platform for doing this, and scittle can be bent to our
;; will to allow fairly sophisticated cljs/js stuff to be used directly (0
;; install). We will discuss alternate methods of accomplishing this later in a
;; more efficient manner, but as an experimentation with clay and scittle and
;; cesium, it's not bad!

;; # Fire Data

;; Our working dataset comes from historical Oregon fire data.
;; The original can be found at https://catalog.data.gov/dataset/odf-fire-occurrence-data-2000-2022
;; I have taken the liberty of gzipping the original csv to make it easier
;; to host.
(def flds
  [:FireName  :Size_class :EstTotalAcres  :Ign_DateTime
   :ReportDateTime :Discover_DateTime :Control_DateTime :Lat_DD :Long_DD
   :CauseBy])

(def fire-data
  (-> (tc/dataset "src/cesium/ODF_Fire_Occurrence_Data_2000-2022.csv.gz" {:key-fn keyword})
      (tc/select-columns flds)
      (tc/select-rows (fn [{:keys [Discover_DateTime Control_DateTime]}]
                        (and  Discover_DateTime Control_DateTime)))))
fire-data

;; the way we're going to expose this to sci, for efficiency, is to serialize
;; our fire data as json, then load it via a script tag.

;; I have processing methods for loading from the local file system and the
;; original application does so and then does ETL from cljs over either csv
;; files, or we precompile stuff in cljs as edn defined in data namespaces.

;; since we're using scittle, I don't want to break the bank on pushing ETL
;; into the interpreter.  clay allows us to preprocess the data and bake it
;; here for consumption, so we'll do that.

;;we can use the ambiently available (and fast) charred to spit
;;our stuff to json...

(defn extract-fires [d]
  (->> d
       (tech.v3.dataset/mapseq-reader)
       vec
       (charred/write-json-str )
       (str "FireData = ")
       (spit "src/cesium/firedata.js")))

;;Cesium wants date/time in a julian format, but we have conversion paths
;;for js Date types established.  So we'll leave the start/stop times of
;;our fires as strings and coerce them to Date types directly from cljs.
;;when we read this in on the cljs side, we'll get json objects for
;;each fire event.

;; In order to use all our stuff, we'll need a bunch of dependencies...
;; How can we load them in clay though and have them available for our
;; in-browser cljs environment?  Script tags of course!

;; We can abuse hiccup with script tags and just pull in js files
;; as we would with html.  As demonstrated elsewhere, we can do the
;; same with cljs files (local to this notebook/the page) if
;; we have scittle as a script.

;; So our scheme is this:  define some hidden hiccup that defines
;; a div that loads all of our dependencies.

;; Then we can build out the browser portion of our app by
;; leveraging cljs tooling (e.g. reagent) via scittle.

;; Note: the app portion can be as sophisticated and spread out
;; as we'd like it to be.  We can have multiple files (e.g. namespaces)
;; and just load them as script tags, and require them from cljs in scittle
;; as normal.


;; ```clojure
;; ^:kindly/hide-code
;; (kind/hiccup
;;  [:div
;;   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.7.23/dist/scittle.js"
;;                  :type "application/javascript"}]
;;   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.7.23/dist/scittle.promesa.js"
;;             :type "application/javascript"}]
;;   [:script {:crossorigin true
;;             :src "https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js"}]
;;   [:script {:crossorigin true
;;             :src "https://cdn.jsdelivr.net/npm/react-dom@18/umd/react-dom.production.min.js"}]
;;   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.7.23/dist/scittle.reagent.js"
;;             :type "application/javascript"}]
;;   [:script {:src "https://cesium.com/downloads/cesiumjs/releases/1.132/Build/Cesium/Cesium.js"
;;             :type "application/javascript"}]
;;   [:link {:href "https://cesium.com/downloads/cesiumjs/releases/1.132/Build/Cesium/Widgets/widgets.css"
;;           :rel "stylesheet" }]
;;   ;;color patch
;;   [:script {:src "color.js"
;;             :type "application/javascript"}]
;;   ;;data
;;   [:script {:src "firedata.js" ;;creates FireData var.
;;             :type "application/javascript"}]
;;   ;;app deps.
;;   [:script {:src "protocols.cljs"
;;             :type "application/x-scittle"}]

;;   [:script {:src "time.cljs"
;;             :type "application/x-scittle"}]
;;   [:script {:src "util.cljs"
;;             :type "application/x-scittle"}]
;;   [:script {:src "czml.cljs"
;;             :type "application/x-scittle"}]
;;   [:script {:src "etl.cljs"
;;             :type "application/x-scittle"}]
;;   [:script {:src "cesium.cljs"
;;             :type "application/x-scittle"}]
;;   [:script {:src "widget.cljs"
;;             :type "application/x-scittle"}]
;;   ])
;; ```


^:kindly/hide-code
(kind/hiccup
 [:div
  [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.7.23/dist/scittle.js"
                 :type "application/javascript"}]
  [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.7.23/dist/scittle.promesa.js"
            :type "application/javascript"}]
  [:script {:crossorigin true
            :src "https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js"}]
  [:script {:crossorigin true
            :src "https://cdn.jsdelivr.net/npm/react-dom@18/umd/react-dom.production.min.js"}]
  [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.7.23/dist/scittle.reagent.js"
            :type "application/javascript"}]
  [:script {:src "https://cesium.com/downloads/cesiumjs/releases/1.132/Build/Cesium/Cesium.js"
            :type "application/javascript"}]
  [:link {:href "https://cesium.com/downloads/cesiumjs/releases/1.132/Build/Cesium/Widgets/widgets.css"
          :rel "stylesheet" }]
  ;;color patch
  [:script {:src "color.js"
            :type "application/javascript"}]
  ;;data
  [:script {:src "firedata.js" ;;creates FireData var.
            :type "application/javascript"}]
  ;;app deps.
  [:script {:src "protocols.cljs"
            :type "application/x-scittle"}]

  [:script {:src "time.cljs"
            :type "application/x-scittle"}]
  [:script {:src "util.cljs"
            :type "application/x-scittle"}]
  [:script {:src "czml.cljs"
            :type "application/x-scittle"}]
  [:script {:src "etl.cljs"
            :type "application/x-scittle"}]
  [:script {:src "cesium.cljs"
            :type "application/x-scittle"}]
  [:script {:src "widget.cljs"
            :type "application/x-scittle"}]
  ])

;;  then our cesium app's entry point is
;;  another hiccup div exposing an app id
;;  with the "core.cljs" script being loaded
;;  from scittle

;; ```clojure
;; ^:kindly/hide-code
;; (kind/hiccup
;;  [:div#app
;;   [:script {:type "application/x-scittle"
;;             :src "core.cljs"}]])
;; ```

;; # The App

;; The application is a typical cesium widget with some
;; reagent ui bits.  We have the primary geospatial
;; view, which default to an interactive globe with
;; the ability to spin, zoom, pan, and tilt the view
;; with the mouse or a touchscreen.

;; cesium is meant to show animated 3d visuals on top
;; of geospatial layers and projections, so it prominently
;; features a timeline on the bottom.  This is an interactive
;; ui that allows the user to move forward and backward into
;; arbitrary points in time, with any temporal data in the
;; visual updating accordingly.

;; Additionally, the timeline has a control mechanism in the
;; bottom left clock, which allows time to progress forward
;; or backward at a constant rate, to change the rate of change,
;; or to pause the passage of time.


^:kindly/hide-code
(kind/hiccup
 [:div#app
  [:script {:type "application/x-scittle"
            :src "core.cljs"}]])

;; As a bonus, I tossed in a few buttons below the main view
;; to simplify the interface.  We can load all 23K of our
;; fire data (spanning 2000-2022), if we click "Load Fires!".

;; After a second or two the fire events will be loaded and
;; we'll notice the timeline has changed with a new start time
;; somewhere in the year 2000.

;; At this point, we can play around with the ui a bit to
;; see what cesium offers out of the box.  The default
;; Bing Maps aerial layer doesn't have any state information; we can get
;; a bit of a better view, with more contrast, if we change to the
;; Stadia x Stamen Toner layer.  We can click on the layer picker in
;; upper right, and scroll down to find it on the second group of
;; layers as a black-and-white high contrast layer.

;; Once that's done, we'll zoom in to Oregon in the northwest
;; corner of the US.  This is where our data lives, so our
;;fires will show up animating around here as simple arcs
;;popping up from the earth over time, corresponding to the
;;presence of a fire.

;;If we hit play at this point, we might find a view that looks like
;;this:

^:kindly/hide-code
(kind/hiccup
 [:div
  [:img {:src "simplefires.jpg"}]])

;; # Outro
;; This is a brief little demonstration of integrating cesium with clay
;; and doing some very basic analysis.  In the original application, I
;; had much more sophisticated happenings, like tying together realtime
;; analytics with the visualization (e.g. interactive vega plots).

;; I have intentionally elided some of the interop requirements for
;; interfacing with cesium and its entity model, geospatial api,
;; timeline, CZML, various uis, etc.  The source files in this repo
;; provide some documentation for the intrepid, but I will explore
;; some of these concepts in future posts.  CesiumJS has a ton of
;; features, and it's a bit of a shame the clojure world doesn't
;; leverage it more.
