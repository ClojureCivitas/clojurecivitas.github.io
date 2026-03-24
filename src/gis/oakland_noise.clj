^{:kindly/hide-code true
  :clay             {:title  "Oakland Sound Pollution Mapping"
                     :quarto {:author   :heather-mf
                              :draft    true
                              :type     :draft
                              :date     "2026-02-26"
                              :category :gis
                              :tags     [:gis :sound :visualization :oakland]}}}

(ns gis.oakland-noise
  (:require
   [scicloj.kindly.v4.kind :as kind]
   [scicloj.clay.v2.api :as clay]
   [clj-http.client :as client]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [tablecloth.api :as tc]))

;; # Oakland Sound Pollution Mapping

;; Oakland is loud. At 7am the I-880 corridor wakes up in a roar — freight trucks
;; heading to the Port, commuters stacking up through the Fruitvale interchange.
;; By 3am most of the city quiets to a hum, punctuated by the occasional BART train
;; or a container ship horn drifting in off the estuary.
;;
;; This notebook builds an animated map of Oakland's approximate sound environment
;; across a full 24-hour cycle. Not measured data, but *modeled* sound: derived from
;; road and rail geometry, infrastructure type, traffic volume curves, and acoustic
;; physics.

;; ## The Ingredients
;;
;; - **Road & rail geometry** from [OpenStreetMap via Overpass QL](https://wiki.openstreetmap.org/wiki/Overpass_API) —
;;   every highway, arterial, residential street, rail line, and runway in the city
;; - **Diurnal traffic curves** — standard hour-by-hour volume multipliers,
;;   with a nod to [Caltrans PeMS](https://pems.dot.ca.gov/) and the
;;   [West Oakland Truck Survey](https://www.baaqmd.gov/~/media/files/planning-and-research/care-program/final-west-oakland-truck-survey-report-dec-2009.pdf)
;;   for the port's unusual freight patterns
;; - **Acoustic physics** — the inverse square law for line sources tells us how
;;   sound attenuates with distance; logarithmic addition lets us combine
;;   overlapping sources correctly

;; ## The Model
;;
;; Each road segment gets a *base decibel level* by type — motorways around 80 dB,
;; residential streets around 50 dB, rail at 85 dB, runways at 95 dB. That base
;; level is scaled hour-by-hour by a volume multiplier drawn from traffic curves,
;; then propagated outward using the line-source attenuation formula:
;;
;; ```
;; L₂ = L₁ - 10 · log₁₀(d₂/d₁)
;; ```
;;
;; Roads behave as line sources (coefficient 10), not point sources (coefficient 20).
;; Where a highway runs adjacent to a rail line, their contributions combine
;; logarithmically — 70 dB + 70 dB ≈ 73 dB, not 140.

;; ## Output
;;
;; 24 frames — one per hour — compiled into an animation. A color gradient maps
;; decibel ranges to hue: the city breathes visually across the day, loud corridors
;; pulsing at rush hour, residential neighborhoods dimming to near-quiet overnight.

;; ## Why Clojure
;;
;; The whole pipeline — fetch, transform, model, render — lives in one literate
;; namespace. The physics functions are pure data transformations, easy to inspect
;; and sanity-check at the REPL. Clay renders everything as an interactive notebook.
;; The interesting unsolved parts — spatial propagation, grid rasterization — are
;; the ones worth talking about.

;; ## Future Directions
;;
;; - Incorporate measured data from community sensors or the EPA monitoring network
;; - Model building attenuation (concrete and wood absorb differently)
;; - West Oakland deserves its own treatment: the port, the 880, and BART all
;;   converge there, and it's a documented environmental justice issue
;; - Synthesize an approximate soundscape for a given location and hour — hear
;;   what the map is showing

;; ---

;; ## Task 1: Overpass API Client

(def oakland-bbox
  "Bounding box [south west north east]"
  [37.7 -122.3 37.9 -122.1])

(def cache-file "src/gis/osm-cache.json")

(defn- build-overpass-query [bbox tags]
  (let [[south west north east] bbox
        tag-filters (str/join "\n"
                               (map (fn [tag]
                                      (str "  way[\"" tag "\"]("
                                           south "," west "," north "," east ");"))
                                    tags))]
    (str "[out:json];\n(\n" tag-filters "\n);\nout geom;")))

(defn- clean-element
  "Extract geometry and relevant attributes from a raw Overpass element."
  [{:keys [id tags geometry]}]
  (when (seq geometry)
    {:id          id
     :highway     (get tags :highway)
     :railway     (get tags :railway)
     :aeroway     (get tags :aeroway)
     :maxspeed    (get tags :maxspeed)
     :coordinates (mapv (fn [{:keys [lon lat]}] [lon lat]) geometry)}))

(defn fetch-osm-data
  "Fetch OSM ways for bbox and tag keys. Caches result to cache-file."
  ([] (fetch-osm-data oakland-bbox ["highway" "railway" "aeroway"]))
  ([bbox tags]
   (if (.exists (io/file cache-file))
     (do
       (println "Using cached OSM data from" cache-file)
       (keep clean-element (:elements (json/parse-string (slurp cache-file) true))))
     (do
       (println "Fetching OSM data from Overpass API...")
       (let [query    (build-overpass-query bbox tags)
             response (client/post "https://overpass-api.de/api/interpreter"
                                   {:form-params {:data query}
                                    :as          :json})
             data     (:body response)]
         (spit cache-file (json/generate-string data))
         (println "Cached to" cache-file)
         (keep clean-element (:elements data)))))))

;; ## Task 2: Static Domain Data

(def highway-base-db
  "Base decibel level (at 1m from road centreline) by OSM highway/railway/aeroway type."
  {"motorway"    80
   "trunk"       78
   "primary"     70
   "secondary"   65
   "tertiary"    60
   "residential" 50
   "service"     45
   "rail"        85
   "runway"      95
   "taxiway"     78
   "aerodrome"   88})

(def diurnal-multipliers
  [0.05 0.05 0.05 0.05 0.10  ; 00:00 - 04:00
   0.30 0.60 1.00 1.00 0.90  ; 05:00 - 09:00
   0.75 0.70 0.70 0.70 0.75  ; 10:00 - 14:00
   0.85 1.05 1.05 0.90 0.70  ; 15:00 - 19:00
   0.50 0.30 0.15 0.10])     ; 20:00 - 23:00

;; Volume multipliers are converted to dB adjustments via 10·log₁₀(multiplier).
;; (A volume multiplier of 1.05 → +0.2 dB; 0.05 → -13 dB.)

(def diurnal-ds
  (tc/dataset {:hour              (range 24)
               :volume-multiplier diurnal-multipliers
               :db-adjustment     (mapv #(* 10 (Math/log10 (max % 1e-10)))
                                        diurnal-multipliers)}))

diurnal-ds

;; ## Task 3: Physics Engine

(defn calculate-attenuation
  "Inverse square law for a line source (road or rail).
   Returns sound level L₂ at distance d₂ given L₁ at reference distance d₁.
     L₂ = L₁ - 10·log₁₀(d₂ / d₁)"
  [l1 d1 d2]
  (- l1 (* 10 (Math/log10 (/ d2 d1)))))

(defn combined-decibels
  "Logarithmic sum of simultaneous decibel sources.
   70 dB + 70 dB ≈ 73 dB (not 140).
     L_total = 10·log₁₀(Σ 10^(Lᵢ/10))"
  [levels]
  (* 10 (Math/log10 (reduce + (map #(Math/pow 10 (/ % 10)) levels)))))

;; Sanity checks:

;; Two equal 70 dB sources → ~73 dB
(combined-decibels [70 70])

;; Attenuation from 1 m to 10 m → 70 dB
(calculate-attenuation 80 1 10)

;; ## Task 4: Smoke Test — Road Network coloured by Base dB

(defn- road-type [{:keys [highway railway aeroway]}]
  (or aeroway railway highway "unknown"))

(defn- element->geojson-feature [elem]
  (let [rtype   (road-type elem)
        base-db (get highway-base-db rtype 55)]
    {:type       "Feature"
     :properties {:highway rtype :base_db base-db}
     :geometry   {:type        "LineString"
                  :coordinates (:coordinates elem)}}))

(defn osm->geojson [elements]
  {:type     "FeatureCollection"
   :features (mapv element->geojson-feature elements)})

(def osm-data (fetch-osm-data))

^kind/vega-lite
{:$schema "https://vega.github.io/schema/vega-lite/v5.json"
 :width   600
 :height  500
 :title   "Oakland Road/Rail Network — Base Decibel Level"
 :params  [{:name   "grid"
            :select {:type "interval" :translate true :zoom true}
            :bind   "scales"}]
 :data    {:values (vec (:features (osm->geojson osm-data)))}
 :mark    {:type "geoshape" :filled false :strokeWidth 1}
 :encoding
 {:color   {:field "properties.base_db" :type "quantitative"
            :scale {:scheme "reds" :domain [45 90]}
            :legend {:title "Base dB"}}
  :tooltip [{:field "properties.highway" :type "nominal" :title "Type"}
            {:field "properties.base_db" :type "quantitative" :title "Base dB"}]}
 :projection {:type "mercator"}}
