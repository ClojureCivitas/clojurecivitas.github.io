^{:kindly/hide-code true
  :clay             {:title  "Remote Sensing - Water Quality"
                     :quarto {:author   :luke-zeitlin
                              :type     :post
                              :image    "waterquality.jpg"
                              :date     "2025-09-26"
                              :category :clojure
                              :tags     [:clay :reagent :scittle
                                         :data-science :remote-sensing]}}}


(ns earth-observation.waterquality
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # Remote sensing water quality analysis

;; This notebook walks through the steps required to create an
;; interactive map widget displaying simple band-ratio algorithms for
;; water quality analysis using cloud-optimized GeoTIFFs.
;; This example will show how to use
;; [Normalized Difference Water Index](https://en.wikipedia.org/wiki/Normalized_difference_water_index)
;; to differentiate water bodies from land.



;; ## Fetching OpenLayers packages
;; The [OpenLayers](https://openlayers.org/) map widget will allow us to fetch
;; multi-spectral satellite imagery in GeoTIFF format and manipulate the data
;; with WebGL.

;; First fetch the packages from CDNs:
(kind/hiccup
 [:div
  [:link {:rel "stylesheet"
          :href "https://cdn.jsdelivr.net/npm/ol@v10.6.0/ol.css"}]
  [:script {:src "https://cdn.jsdelivr.net/npm/geotiff"}]
  [:script {:src "https://cdn.jsdelivr.net/npm/ol@v10.6.0/dist/ol.js"}]])

;; ## Handling the GeoTIFF files
;; We will create an OpenLayers WebGL layer that uses a GeoTIFF source.
;; Here we are using a
;; [Sentinel-2](https://dataspace.copernicus.eu/data-collections/copernicus-sentinel-data/sentinel-2)
;; 10m resolution GeoTIFF image that
;; contains blue, green, red and near infra-red bands.

(kind/scittle
 '(def geotiff-sources
    (js/ol.source.GeoTIFF.
     (clj->js
      {:sources [{:min 0
                  :nodata 0
                  :max 10000
                  :bands [1 ;; B02 blue (490nm)  -> band 1
                          2 ;; B03 green (560nm) -> band 2
                          3 ;; B04 red (665nm)   -> band 3
                          4 ;; B08 NIR (841nm)   -> band 4
                          ]
                  :url "https://s2downloads.eox.at/demo/Sentinel-2/3857/R10m.tif"}]}))))

;; ## OpenLayers WebGL DSL helpers
;; Openlayers has it's own [lisp-oid DSL](https://openlayers.org/en/latest/apidoc/module-ol_expr_expression.html#~ExpressionValue)
;; that gets converted to
;; GLSL. Here we set up a few helper functions to allow us to
;; write it more clearly.

(kind/scittle
 ;; helper fns for writing in OpenLayers GL DSL
 '(do
    (defn gl-var [kw]
      ["var" kw])
    (defn gl-band [band-key]
      ["band" (band-key {:blue  1
                         :green 2
                         :red   3
                         :NIR   4})])))

;; ## Normalized Difference Water Index
^:kindly/hide-code
(kind/tex "\\text{NDWI} = \\frac{\\text{Green} - \\text{NIR}}{\\text{Green} + \\text{NIR}}")
;; NDWI is a band ratio Index that allows us to detect the presence of
;; bodies of water.
;; Water bodies absorb most NIR and reflect plenty of green light,
;; so they result in a positive NDWI.
;; Vegetation and soil strongly reflect NIR and and moderately reflect
;; green light so they result in a negative NDWI.


(kind/scittle
 '(def NDWI
    ["/"
     ["-" (gl-band :green) (gl-band :NIR)]
     ["+" (gl-band :green) (gl-band :NIR)]]))

;; ## WebGL instructions
;; Here we compose the WebGL instructions that will get converted
;; into a GLSL fragment shader. We can access variables that will
;; be passed in a separate variables map, and also the values from
;; the bands selected above.

;; In this example we show the NDWI value in a gradient from blue
;; to green. NDWI values below the `land_threshold` are displayed in RGB
;; with a brightness variable so we can dim the land pixels dynamically.

(kind/scittle
 '(def color
    (let [opaque-blue [1 0 0 255]
          opaque-green [0 1 0 255]]
      ["case"
       ["<" NDWI (gl-var :land_threshold)]
       ["color"
        ["*" (gl-var :land_brightness) (gl-band :red)]
        ["*" (gl-var :land_brightness) (gl-band :green)]
        ["*" (gl-var :land_brightness) (gl-band :blue)]
        225]

       ["interpolate"
        ["linear"]
        NDWI
        (gl-var :ndwi_upper) opaque-blue   ; (lower bound)
        (gl-var :ndwi_lower) opaque-green  ; (upper bound)
        ]])))


(kind/scittle
 ;; default variable values for the map vis
 '(def param-defaults {:land_threshold 0.0
                       :land_brightness 3.0
                       :ndwi_upper 1.0
                       :ndwi_lower 0.0}))

;; ## Openlayers map widget setup
;; Below we create a map widget with a WebGL tile and a
;; view based on the bounds of our GeoTIFF file.

(kind/scittle
 '(def webgl-tile-layer
    (js/ol.layer.WebGLTile.
     (clj->js {:style {:variables param-defaults
                       :color color}
               :source geotiff-sources}))))

(kind/scittle
 '(let [osm-layer (->> {:source (js/ol.source.OSM.)}
                       clj->js
                       js/ol.layer.Tile.)

        view (->> {:center [0 0]
                   :zoom 2}
                  clj->js
                  js/ol.View.)]
    (->> {:target "map"
          :layers [webgl-tile-layer]
          :view (.getView geotiff-sources)}
         clj->js
         js/ol.Map.)))

;; ## Dynamic controls
;; Using reagent we can create some sliders to control the
;; WebGL variables, triggering a redraw on change.

(kind/reagent
 ['(fn []
     (let [*map-params (reagent.core/atom param-defaults)
           slider
           (fn [label param minv maxv]
             (let [value (param @*map-params)]
               [:div
                [:label {:for param} label] " : "
                [:input
                 {:id param
                  :type "range"
                  :min minv
                  :max maxv
                  :value value
                  :step 0.01
                  :on-change (fn [e]
                               (let [newv (js/parseFloat (.. e -target -value))]
                                 (swap! *map-params #(assoc % param newv))
                                 (.updateStyleVariables
                                  webgl-tile-layer
                                  (clj->js @*map-params))))}]]))]

       (fn []
         [:div {:style {:text-align "right"}}
          [slider "Land Threshold" :land_threshold 0.0 1.0]
          [slider "Land Brightness" :land_brightness 0.0 10.0]
          [slider "NDWI Upper" :ndwi_upper 0.0 1.0]
          [slider "NDWI Lower" :ndwi_lower 0.0 1.0]])))])

;; ## Results
;; We can see the river clearly outlined by the NDWI index. The sliders
;; allow us to dim the land to see the water outline more clearly, and
;; adjust the thresholds to control how the water is displayed.

^:kindly/hide-code
(kind/hiccup
 ;; a div to render the map in
 [:div#map {:style {:width "500px"
                    :height "500px"}}])
