^{:kindly/hide-code true
  :clay             {:title  "GIS roadmap"
                     :quarto {:author   :luke-zeitlin
                              :type     :post
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
;; # Why Clojure?
;; ## TODO:
;; A few words about why Clojure is good for GIS...
;; -  Clojure's ability to interact with multiple host environments (Java, JS, Python, etc).
;; - Same language on the front and backend
;; - Ergonomic data workflow
;; - Interactive development
;; # GIS Task Categories 
;; ## Image formats
;; ### Raster
;; #### Cloud Optimized GeoTIFF (COG)
;; ##### What is a COG?
;; **TIFF** is an extensible image file format. **GeoTIFFs** are TIFFs with geospatial metadata
;; such as coordinate reference / projection information, Geographic extent,
;; pixel resolution and so on.
;; **Cloud Optimized GeoTIFFs (COGs)** are geoTIFFs arranged for efficient access over HTTP. This
;; is achieved with tiled layouts allowing for partial access (using HTTP range requests),
;; and resolution pyramids. This allows for fetching only the portion of the COG required.
;;
;; More details on the format at: [cogeo.org](https://www.cogeo.org)
;;
;; #### How to interact with COGs with Clojure
;; Many libraries for creation and manipulation of GeoTIFF data in other languages rely on
;; the [GDAL binary](https://gdal.org). 
;; This is performant and battle-tested but has a large footprint and is vulnerable to
;; version mismatches between the client language and GDAL.
;;
;; Some pure language implementations (that do not depend on GDAL) exist, such as
;; [geotiff.js](https://geotiffjs.github.io/) for JavaScript and [GeoTools](https://geotools.org/) in Java.
;; For the sake of
;; simplicity we recommend using interop with pure language GeoTIFF library where possible.
;;
;; ##### TODOs:
;; - GeoTools / Java interop example
;; - geotiff.js / JavaScript interop example 
;; - Example of a containerized Python bindings with GDAL setup.
;;
;; 
;; #### NetCDF
;; NetCDF is a format for metadata enhanced, compressed, multidimensional arrays.
;; Often used for weather, climate, remote sensing and
;; oceanographic data.
;; Both [GeoTools](https://geotools.org/) and [NetCDF-Java](https://docs.unidata.ucar.edu/netcdf-java) are acceptable ways to to handle and create
;; netCDF files in Java-hosted Clojure. In JS we can read NetCDF files with
;; [netcdfjs](https://github.com/cheminfo/netcdfjs)
;; ##### TODOs:
;; - GeoTools interop example
;; - NetCDF-Java example
;; - Which one is best?
;; - netcdfjs example (maybe interactive with scittle/react/clay)
;;
;; #### HDF
;; Another widely used multidimensional array format in the scientific GIS world.
;;
;; [HDF in clojure](https://scicloj.github.io/clojure-data-scrapbook/projects/data-formats/hdf/index.html)
;; #### Zarr
;; ##### TODO:
;; - how to read in Clojure
;; 
;; ### Vector
;; #### GeoJSON
;; - json->edn
;; - spec validation for geoJSON
;; - review [FarmLogs/geojson](https://github.com/FarmLogs/geojson)
;; #### Shapefile
;; - [ovid](https://github.com/willcohen/ovid)
;; #### Notes:
;; Maybe [Factual/geo](https://github.com/Factual/geo) is good for some of this?
;;
;; ## Cloud Optimized
;; ### PMTiles
;; - Reading: Timeverse PMtiles (Java interop)
;; - Creation: CLI / Babashka?
;; ### Notes:
;; [The Cloud Native Geo guide](https://guide.cloudnativegeo.org/) may be a useful starting
;; pount for some of this.
;;
^:kindly/hide-code
(kind/image {:src "cogeo-formats-table.png"
             :style {:width "30em"}})
;; *Image from https:/guide.cloudnativegeo.org* 
;;
;; ## Image processing
;; Routing / Network analysis
;; ### Some articles to review
;; - [GTFS](https://en.wikipedia.org/wiki/GTFS)
;; - [MATSim](https://github.com/matsim-org)
;;
;; ## Spatial DBs
;; - postGIS
;; - MBTiles / sqllite
;;
;; ## Spatial reference systems
;; ### Coordinate system conversions
;; - [Coordinate Systems article](https://mgimond.github.io/Spatial/chp09_0.html) maybe a good starting point for a Clojure oriented article on the same.

;; ### H3
;; - again, [Factual/geo](https://github.com/factual/geo) may be a good starting point.
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
;; - [Clojure Maps Examples](https://github.com/joannecheng/clojure-map-examples)
^:kindly/hide-code
(comment
  ,
  (require '[scicloj.clay.v2.api :as clay])

  (clay/make! {:source-path "src/gis/roadmap.clj"
               :live-reload :toggle}))

