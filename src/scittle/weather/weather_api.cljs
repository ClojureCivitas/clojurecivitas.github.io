(ns scittle.weather.weather-api
  "National Weather Service API integration with keyword arguments.

   All functions use keyword argument maps for clarity and flexibility.
   No API key required - completely free!

   Main API Reference: https://www.weather.gov/documentation/services-web-api"
  (:require [clojure.string :as str]))

;; ============================================================================
;; Constants
;; ============================================================================

(def weather-api-base-url
  "Base URL for NWS API"
  "https://api.weather.gov")

;; ============================================================================
;; Core Helper Functions
;; ============================================================================

(defn fetch-json
  "Fetch JSON data from a URL with error handling.

  Uses browser's native fetch API - no external dependencies.

  Args (keyword map):
    :url        - URL to fetch (string, required)
    :on-success - Success callback receiving parsed data (fn, required)
    :on-error   - Error callback receiving error message (fn, optional)

  The success callback receives the parsed JSON as a Clojure map.
  The error callback receives an error message string.

  Example:
    (fetch-json
      {:url \"https://api.weather.gov/points/40,-74\"
       :on-success #(js/console.log \"Data:\" %)
       :on-error #(js/console.error \"Failed:\" %)})"
  [{:keys [url on-success on-error]
    :or {on-error #(js/console.error "Fetch error:" %)}}]
  (-> (js/fetch url)
      (.then (fn [response]
               (if (.-ok response)
                 (.then (.json response)
                        (fn [data]
                          (on-success (js->clj data :keywordize-keys true))))
                 (on-error (str "HTTP Error: " (.-status response))))))
      (.catch (fn [error]
                (on-error (.-message error))))))

;; ============================================================================
;; Points API - Get forecast endpoints for coordinates
;; ============================================================================

(defn fetch-points
  "Get NWS grid points and forecast URLs for given coordinates.

  This is typically the first API call - it returns URLs for all weather
  products available at the given location.

  Args (keyword map):
    :lat        - Latitude (number, required, range: -90 to 90)
    :lon        - Longitude (number, required, range: -180 to 180)
    :on-success - Success callback receiving location data (fn, required)
    :on-error   - Error callback receiving error message (fn, optional)

  Success callback receives a map with:
    :forecast             - URL for 7-day forecast
    :forecastHourly       - URL for hourly forecast
    :forecastGridData     - URL for raw grid data
    :observationStations  - URL for nearby weather stations
    :forecastOffice       - NWS office identifier
    :gridId               - Grid identifier
    :gridX, :gridY        - Grid coordinates
    :city, :state         - Location name

  Example:
    (fetch-points
      {:lat 40.7128
       :lon -74.0060
       :on-success (fn [data]
                     (js/console.log \"City:\" (:city data))
                     (fetch-forecast {:url (:forecast data) ...}))
       :on-error (fn [error]
                   (js/console.error \"Error:\" error))})"
  [{:keys [lat lon on-success on-error]
    :or {on-error #(js/console.error "Points API error:" %)}}]
  (let [url (str weather-api-base-url "/points/" lat "," lon)]
    (fetch-json
     {:url url
      :on-success (fn [result]
                    (let [properties (get-in result [:properties])]
                      (on-success
                       {:forecast (:forecast properties)
                        :forecastHourly (:forecastHourly properties)
                        :forecastGridData (:forecastGridData properties)
                        :observationStations (:observationStations properties)
                        :forecastOffice (:forecastOffice properties)
                        :gridId (:gridId properties)
                        :gridX (:gridX properties)
                        :gridY (:gridY properties)
                        :city (:city (get-in result [:properties :relativeLocation :properties]))
                        :state (:state (get-in result [:properties :relativeLocation :properties]))})))
      :on-error on-error})))

;; ============================================================================
;; Forecast API - Get 7-day forecast
;; ============================================================================

(defn fetch-forecast
  "Fetch 7-day forecast from a forecast URL.

  Returns periods (typically 14 periods: day/night for 7 days).

  Args (keyword map):
    :url        - Forecast URL from points API (string, required)
    :on-success - Success callback receiving forecast periods (fn, required)
    :on-error   - Error callback receiving error message (fn, optional)

  Success callback receives a vector of period maps, each containing:
    :number              - Period number
    :name                - Period name (e.g., \"Tonight\", \"Friday\")
    :temperature         - Temperature value
    :temperatureUnit     - Unit (F or C)
    :windSpeed           - Wind speed string
    :windDirection       - Wind direction
    :icon                - Weather icon URL
    :shortForecast       - Brief description
    :detailedForecast    - Detailed description

  Example:
    (fetch-forecast
      {:url forecast-url
       :on-success (fn [periods]
                     (doseq [period (take 3 periods)]
                       (js/console.log (:name period) \"-\" (:shortForecast period))))
       :on-error (fn [error]
                   (js/console.error \"Forecast error:\" error))})"
  [{:keys [url on-success on-error]
    :or {on-error #(js/console.error "Forecast API error:" %)}}]
  (fetch-json
   {:url url
    :on-success (fn [result]
                  (on-success (get-in result [:properties :periods])))
    :on-error on-error}))

;; ============================================================================
;; Hourly Forecast API
;; ============================================================================

(defn fetch-hourly-forecast
  "Fetch hourly forecast from a forecast URL.

  Args (keyword map):
    :url        - Hourly forecast URL from points API (string, required)
    :on-success - Success callback receiving hourly periods (fn, required)
    :on-error   - Error callback receiving error message (fn, optional)

  Success callback receives a vector of hourly period maps.
  Each period has the same structure as the regular forecast.

  Example:
    (fetch-hourly-forecast
      {:url hourly-url
       :on-success (fn [periods]
                     (js/console.log \"Next 12 hours:\")
                     (doseq [period (take 12 periods)]
                       (js/console.log (:startTime period) \"-\" (:temperature period) \"°F\")))
       :on-error (fn [error]
                   (js/console.error \"Hourly forecast error:\" error))})"
  [{:keys [url on-success on-error]
    :or {on-error #(js/console.error "Hourly forecast API error:" %)}}]
  (fetch-json
   {:url url
    :on-success (fn [result]
                  (on-success (get-in result [:properties :periods])))
    :on-error on-error}))

;; ============================================================================
;; Observation Stations API
;; ============================================================================

(defn fetch-observation-stations
  "Get list of observation stations near a location.

  Args (keyword map):
    :url        - Observation stations URL from points API (string, required)
    :on-success - Success callback receiving station list (fn, required)
    :on-error   - Error callback receiving error message (fn, optional)

  Success callback receives a vector of station maps, each containing:
    :stationIdentifier - Station ID (e.g., \"KJFK\")
    :name              - Station name
    :elevation         - Elevation data

  Example:
    (fetch-observation-stations
      {:url stations-url
       :on-success (fn [stations]
                     (let [first-station (first stations)]
                       (js/console.log \"Nearest station:\" (:name first-station))
                       (fetch-current-observations
                         {:station-id (:stationIdentifier first-station)
                          :on-success ...})))
       :on-error (fn [error]
                   (js/console.error \"Stations error:\" error))})"
  [{:keys [url on-success on-error]
    :or {on-error #(js/console.error "Observation stations API error:" %)}}]
  (fetch-json
   {:url url
    :on-success (fn [result]
                  (on-success
                   (map #(get-in % [:properties])
                        (get-in result [:features]))))
    :on-error on-error}))

;; ============================================================================
;; Current Observations API
;; ============================================================================

(defn fetch-current-observations
  "Get current weather observations from a station.

  Args (keyword map):
    :station-id - Station identifier (string, required, e.g., \"KJFK\")
    :on-success - Success callback receiving observation data (fn, required)
    :on-error   - Error callback receiving error message (fn, optional)

  Success callback receives a map with current conditions:
    :temperature          - Current temperature with :value and :unitCode
    :dewpoint            - Dewpoint temperature
    :windDirection       - Wind direction in degrees
    :windSpeed           - Wind speed
    :barometricPressure  - Pressure
    :relativeHumidity    - Humidity percentage
    :visibility          - Visibility distance
    :textDescription     - Weather description
    :timestamp           - Observation time

  Example:
    (fetch-current-observations
      {:station-id \"KJFK\"
       :on-success (fn [obs]
                     (js/console.log \"Temperature:\"
                                     (get-in obs [:temperature :value]) \"°C\")
                     (js/console.log \"Conditions:\" (:textDescription obs)))
       :on-error (fn [error]
                   (js/console.error \"Observations error:\" error))})"
  [{:keys [station-id on-success on-error]
    :or {on-error #(js/console.error "Current observations API error:" %)}}]
  (let [url (str weather-api-base-url "/stations/" station-id "/observations/latest")]
    (fetch-json
     {:url url
      :on-success (fn [result]
                    (on-success (get-in result [:properties])))
      :on-error on-error})))

;; ============================================================================
;; Alerts API - Weather alerts
;; ============================================================================

(defn fetch-alerts-for-point
  "Fetch active weather alerts for a specific location.

  Args (keyword map):
    :lat        - Latitude (number, required)
    :lon        - Longitude (number, required)
    :on-success - Success callback receiving alerts list (fn, required)
    :on-error   - Error callback receiving error message (fn, optional)

  Success callback receives a vector of alert maps, each containing:
    :event       - Alert type (e.g., \"Tornado Warning\")
    :headline    - Brief headline
    :description - Full alert description
    :severity    - Severity level (Extreme/Severe/Moderate/Minor)
    :urgency     - Urgency level
    :certainty   - Certainty level
    :onset       - Start time
    :ends        - End time

  Example:
    (fetch-alerts-for-point
      {:lat 40.7128
       :lon -74.0060
       :on-success (fn [alerts]
                     (if (empty? alerts)
                       (js/console.log \"No active alerts\")
                       (doseq [alert alerts]
                         (js/console.log (:severity alert) \"-\" (:event alert)))))
       :on-error (fn [error]
                   (js/console.error \"Alerts error:\" error))})"
  [{:keys [lat lon on-success on-error]
    :or {on-error #(js/console.error "Alerts API error:" %)}}]
  (let [url (str weather-api-base-url "/alerts/active?point=" lat "," lon)]
    (fetch-json
     {:url url
      :on-success (fn [result]
                    (on-success
                     (map #(get-in % [:properties])
                          (get-in result [:features]))))
      :on-error on-error})))

;; ============================================================================
;; Complete Weather Data - Convenience function
;; ============================================================================

(defn fetch-complete-weather
  "Fetch comprehensive weather data for given coordinates.

  This is a convenience function that:
  1. Fetches points data
  2. Fetches 7-day forecast
  3. Fetches hourly forecast
  4. Fetches observation stations
  5. Fetches current observations from nearest station
  6. Fetches active alerts

  All data is collected and returned in a single callback.

  Args (keyword map):
    :lat        - Latitude (number, required)
    :lon        - Longitude (number, required)
    :on-success - Success callback receiving complete weather data (fn, required)
    :on-error   - Error callback receiving error message (fn, optional)

  Success callback receives a map with:
    :points   - Location and grid information
    :forecast - 7-day forecast periods
    :hourly   - Hourly forecast periods
    :stations - Nearby weather stations
    :current  - Current observations
    :alerts   - Active weather alerts

  Example:
    (fetch-complete-weather
      {:lat 40.7128
       :lon -74.0060
       :on-success (fn [weather]
                     (js/console.log \"Location:\"
                                     (get-in weather [:points :city]))
                     (js/console.log \"Current temp:\"
                                     (get-in weather [:current :temperature :value]))
                     (js/console.log \"Forecast periods:\"
                                     (count (:forecast weather))))
       :on-error (fn [error]
                   (js/console.error \"Complete weather error:\" error))})"
  [{:keys [lat lon on-success on-error]
    :or {on-error #(js/console.error "Complete weather error:" %)}}]
  (let [results (atom {})]
    ;; First get the points data
    (fetch-points
     {:lat lat
      :lon lon
      :on-success
      (fn [points-data]
        (swap! results assoc :points points-data)

         ;; Fetch 7-day forecast
        (when (:forecast points-data)
          (fetch-forecast
           {:url (:forecast points-data)
            :on-success (fn [forecast-data]
                          (swap! results assoc :forecast forecast-data))}))

         ;; Fetch hourly forecast
        (when (:forecastHourly points-data)
          (fetch-hourly-forecast
           {:url (:forecastHourly points-data)
            :on-success (fn [hourly-data]
                          (swap! results assoc :hourly hourly-data))}))

         ;; Fetch observation stations and current observations
        (when (:observationStations points-data)
          (fetch-observation-stations
           {:url (:observationStations points-data)
            :on-success
            (fn [stations-data]
              (swap! results assoc :stations stations-data)
                ;; Get current observations from first station
              (when-let [station-id (:stationIdentifier (first stations-data))]
                (fetch-current-observations
                 {:station-id station-id
                  :on-success (fn [obs-data]
                                (swap! results assoc :current obs-data)
                                   ;; Return all collected data
                                (on-success @results))})))}))

         ;; Fetch alerts for this point
        (fetch-alerts-for-point
         {:lat lat
          :lon lon
          :on-success (fn [alerts-data]
                        (swap! results assoc :alerts alerts-data))}))

      :on-error on-error})))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn get-weather-icon
  "Map NWS icon URLs to emoji representations.

  Args:
    icon-url - NWS icon URL (string)

  Returns:
    Emoji string representing the weather condition.

  Example:
    (get-weather-icon \"https://api.weather.gov/icons/land/day/rain\")
    ;; => \"🌧️\""
  [icon-url]
  (when icon-url
    (let [icon-name (-> icon-url
                        (str/split #"/")
                        last
                        (str/split #"\?")
                        first
                        (str/replace #"\..*$" ""))]
      (cond
        (str/includes? icon-name "skc") "☀️" ; Clear
        (str/includes? icon-name "few") "🌤️" ; Few clouds
        (str/includes? icon-name "sct") "⛅" ; Scattered clouds
        (str/includes? icon-name "bkn") "🌥️" ; Broken clouds
        (str/includes? icon-name "ovc") "☁️" ; Overcast
        (str/includes? icon-name "rain") "🌧️" ; Rain
        (str/includes? icon-name "snow") "❄️" ; Snow
        (str/includes? icon-name "tsra") "⛈️" ; Thunderstorm
        (str/includes? icon-name "fog") "🌫️" ; Fog
        (str/includes? icon-name "wind") "💨" ; Windy
        (str/includes? icon-name "hot") "🌡️" ; Hot
        (str/includes? icon-name "cold") "🥶" ; Cold
        :else "🌡️")))) ; Default
