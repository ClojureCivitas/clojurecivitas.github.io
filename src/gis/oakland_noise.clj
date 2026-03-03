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
   [cheshire.core :as json]))

;; # Oakland Sound Pollution Mapping

;; ## Overview
;; Creating an animation/image series mapping approximate sound pollution levels across Oakland, California throughout a 24-hour cycle.

;; ## Data Sources
;; - **Road & Rail Geometries**: [Overpass QL API](https://wiki.openstreetmap.org/wiki/Overpass_API)
;; - **Traffic Patterns**: Standard traffic curves for temporal modeling
;;   - will start with basic Standard Diurnal Daily Curves
;;   - Look into []Caltrans PeMS](https://pems.dot.ca.gov/) data for more specific patterns if time allows
;;   - Another source for West Oakland truck traffic for the Port of Oakland: [WEST OAKLAND TRUCK SURVEY REPORT](https://www.baaqmd.gov/~/media/files/planning-and-research/care-program/final-west-oakland-truck-survey-report-dec-2009.pdf)

;; ## Approach

;; ### 1. Data Collection
;; - Query Overpass QL for Oakland road and rail network geometries
;; - Categorize road segments by type (highway, arterial, residential, etc.)
;; - Identify rail lines and stations, freight yards, airports, etc

;; ### 2. Base Decibel Mapping
;; - Assign base decibel levels to each road segment based on road type
;; - Factor in rail noise sources
;; - Reference standards:
;;   - Highway: ~70-80 dB
;;   - Arterial roads: ~60-70 dB
;;   - Residential streets: ~50-60 dB
;;   - Rail: ~80-90 dB (variable)

;; ### 3. Temporal Multiplier
;; - Apply traffic curves to model hour-by-hour variations
;; - Peak hours (7-9 AM, 5-7 PM): maximum multiplier
;; - Midday: moderate levels
;; - Night hours (11 PM - 5 AM): minimum multiplier

;; ### 4. Sound Propagation Model
;; Implement Inverse Square Law for line sources:
;;
;; ```
;; L₂ = L₁ - 10 · log₁₀(d₂/d₁)
;; ```
;;
;; Where:
;; - L₂ = sound level at distance d₂
;; - L₁ = sound level at distance d₁
;; - d₂ = target distance from source
;; - d₁ = reference distance from source
;;
;; This calculates sound attenuation as distance increases from the road/rail source.

;; ### 5. Output
;; - Generate 24 static images (one per half hour)
;; - Compile into animation showing sound levels throughout the day
;; - Color gradient representing decibel ranges

;; ## Technical Challenges
;; - Efficient spatial queries and indexing
;; - Handling overlapping sound sources
;; - Realistic traffic pattern modeling
;; - Performance optimization for city-wide calculations

;; ## Future Enhancements

;; ## Implementation Status
;; 🚧 Work in progress

;; ---
