^{:kindly/hide-code true
  :clay             {:title  "GIS roadmap"
                     :quarto {:author   :luke-zeitlin
                              :type     :collaboration
                              :date     "2025-10-23"
                              :draft    true
                              :category :gis
                              :tags     [:gis]}}}

(ns gis.roadmap
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # Roadmap for Clojure GIS
;; The plan is to present a cohesive story for GIS work in the Clojure ecosystem.
;; At present this document can serve as a location to add notes, todos and thoughts.
;; Some future version of it may serve as a landing page.

;; # GIS Task Categories 
;; ## Image formats
;; ### Raster
;; - GeoTIFF
;; 
;; ### Vector
;; - GeoJSON
;; - Shapefile
;; #### Notes:
;; Maybe [Factual/geo](https://github.com/Factual/geo) is good for some of this?
;;
;; ## Serverless
;; - COG
;; - PMTiles
;; ### Notes:
;; [The Cloud Native Geo guide](https://guide.cloudnativegeo.org/) may be a useful starting
;; pount for some of this
;;
;; ## Image processing
;; Routing / Network analysis
;; - [GTFS](https://en.wikipedia.org/wiki/GTFS)
;; - [MATSim](https://github.com/matsim-org)
;;
;; ## Spatial DBs
;; - postGIS
;; - MBTiles / sqllite
;;
;; ## Spatial reference systems
;; - Coordinate system conversions
;; - H3
;;
;; ## Map widgets
;; - Leaflet
;; - Openlayers
;; - Kepler.gl
;;
;; ## Geo-coding / Addresses
;;
;; ## Remote sensing
;; ### Satellite imagery
;; - Sentinel2
;; - Planet
;; - Google Earth Engine
;; ## Tile Servers
;;
;; # Python integration (libpython-clj)
;; A lot of GIS work is done in Python. Creating documentation, tools, containers to
;; make it quick/easy/simple to interact with Python from Clojure
;; will make doing GIS work in Clojure more palatable.
;;
;; # Template Projects
;; Some [deps-new](https://github.com/seancorfield/deps-new) or similar templates for
;; getting started on GIS projects.
;;
;; # Existing articles for Clojure GIS
;; Perhaps we can link to, rework or use some existing work including:
;; - [Seattle Parks - Scicloj](https://scicloj.github.io/clojure-data-scrapbook/projects/geography/seattle-parks/index.html)
;; - [Chicago Bikes - Scicloj](https://scicloj.github.io/clojure-data-scrapbook/projects/geography/chicago-bikes/index.html)
;; - [Remote sensing water - Civitas](https://clojurecivitas.github.io/earth_observation/waterquality.html)
;; - [Cesium - Civitas](https://clojurecivitas.github.io/cesium/geovis.html)
^:kindly/hide-code
(comment
  ,
  (require '[scicloj.clay.v2.api :as clay])

  (clay/make! {:source-path "src/gis/roadmap.clj"
               :live-reload :toggle}))

