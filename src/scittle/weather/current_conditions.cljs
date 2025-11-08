(ns scittle.weather.current-conditions
  "Display detailed current weather conditions with all available metrics.
   Demonstrates unit conversion, comprehensive data display, and state management."
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as str]))

;; ============================================================================
;; Theme Styles
;; ============================================================================

(defn theme-styles
  "CSS for light and dark mode support using Quarto's data-bs-theme attribute"
  []
  [:style "
    /* Light mode (default) */
    [data-bs-theme='light'] {
      --card-bg: #ffffff;
      --card-secondary-bg: #f9fafb;
      --input-bg: #ffffff;
      --text-primary: #1f2937;
      --text-secondary: #6b7280;
      --text-tertiary: #9ca3af;
      --border-color: #e5e7eb;
      --border-color-dark: #d1d5db;
      --button-bg: #f3f4f6;
      --button-hover: #e5e7eb;
      --button-text: #374151;
      --shadow-color: rgba(0, 0, 0, 0.1);
    }

    /* Dark mode */
    [data-bs-theme='dark'] {
      --card-bg: #1f2937;
      --card-secondary-bg: #111827;
      --input-bg: #111827;
      --text-primary: #f3f4f6;
      --text-secondary: #d1d5db;
      --text-tertiary: #9ca3af;
      --border-color: #374151;
      --border-color-dark: #4b5563;
      --button-bg: #374151;
      --button-hover: #4b5563;
      --button-text: #f3f4f6;
      --shadow-color: rgba(0, 0, 0, 0.3);
    }

    /* Hover states - applied universally */
    button:not(.btn-primary):hover {
      background: var(--button-hover) !important;
    }
  "])

;; ============================================================================
;; API Functions (Inline from weather_api.cljs)
;; ============================================================================

(defn fetch-json
  "Fetch JSON from URL with error handling."
  [{:keys [url on-success on-error]
    :or {on-error #(js/console.error "Fetch error:" %)}}]
  (-> (js/fetch url)
      (.then (fn [response]
               (if (.-ok response)
                 (.json response)
                 (throw (js/Error. (str "HTTP " (.-status response)))))))
      (.then (fn [data]
               (on-success (js->clj data :keywordize-keys true))))
      (.catch on-error)))

(defn get-weather-station
  "Get nearest weather station for coordinates."
  [{:keys [lat lon on-success on-error]}]
  (fetch-json
   {:url (str "https://api.weather.gov/points/" lat "," lon)
    :on-success (fn [data]
                  (let [station-url (get-in data [:properties :observationStations])]
                    (fetch-json
                     {:url station-url
                      :on-success (fn [stations]
                                    (let [station-id (-> stations
                                                         :features
                                                         first
                                                         :properties
                                                         :stationIdentifier)]
                                      (on-success station-id)))
                      :on-error on-error})))
    :on-error on-error}))

(defn get-latest-observations
  "Get latest observations from station."
  [{:keys [station-id on-success on-error]}]
  (fetch-json
   {:url (str "https://api.weather.gov/stations/" station-id "/observations/latest")
    :on-success on-success
    :on-error on-error}))

;; ============================================================================
;; Temperature Conversion Utilities
;; ============================================================================

(defn celsius-to-fahrenheit
  "Convert Celsius to Fahrenheit"
  [c]
  (when c (+ (* c 1.8) 32)))

(defn celsius-to-kelvin
  "Convert Celsius to Kelvin"
  [c]
  (when c (+ c 273.15)))

(defn format-temp
  "Format temperature based on unit (F, C, or K)"
  [celsius unit]
  (when celsius
    (case unit
      "F" (str (Math/round (celsius-to-fahrenheit celsius)) "°F")
      "C" (str (Math/round celsius) "°C")
      "K" (str (Math/round (celsius-to-kelvin celsius)) "K")
      (str (Math/round celsius) "°C"))))

;; ============================================================================
;; Wind & Distance Utilities
;; ============================================================================

(defn format-wind-direction
  "Convert degrees to compass direction"
  [degrees]
  (when degrees
    (let [directions ["N" "NNE" "NE" "ENE" "E" "ESE" "SE" "SSE"
                      "S" "SSW" "SW" "WSW" "W" "WNW" "NW" "NNW"]
          index (mod (Math/round (/ degrees 22.5)) 16)]
      (nth directions index))))

(defn meters-to-miles
  "Convert meters to miles"
  [m]
  (when m (* m 0.000621371)))

(defn mps-to-mph
  "Convert meters per second to miles per hour"
  [mps]
  (when mps (* mps 2.237)))

;; ============================================================================
;; Styles
;; ============================================================================

(def container-style
  {:max-width "800px"
   :margin "0 auto"
   :padding "20px"
   :font-family "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"})

(def card-style
  {:background "var(--card-bg, #ffffff)"
   :color "var(--text-primary, #1f2937)"
   :border "1px solid var(--border-color, #e5e7eb)"
   :border-radius "12px"
   :padding "24px"
   :margin-bottom "20px"
   :box-shadow "0 2px 8px var(--shadow-color, rgba(0,0,0,0.1))"})

(def header-style
  {:text-align "center"
   :margin-bottom "30px"})

(def title-style
  {:font-size "28px"
   :font-weight "600"
   :color "var(--text-primary, #1f2937)"
   :margin "0 0 10px 0"})

(def subtitle-style
  {:font-size "14px"
   :color "var(--text-tertiary, #9ca3af)"
   :margin 0})

(def input-group-style
  {:display "flex"
   :gap "10px"
   :margin-bottom "20px"
   :flex-wrap "wrap"})

(def input-style
  {:padding "10px 15px"
   :background "var(--input-bg, #ffffff)"
   :color "var(--text-primary, #1f2937)"
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "6px"
   :font-size "14px"
   :flex "1"
   :min-width "120px"
   :box-shadow "inset 0 1px 2px var(--shadow-color, rgba(0,0,0,0.05))"})

(def button-style
  {:padding "10px 20px"
   :background "#3b82f6"
   :color "white"
   :border "none"
   :border-radius "6px"
   :cursor "pointer"
   :font-size "14px"
   :font-weight "500"
   :transition "background 0.2s"})

(def button-hover-style
  (merge button-style {:background "#2563eb"}))

(def quick-buttons-style
  {:display "flex"
   :gap "8px"
   :flex-wrap "wrap"
   :margin-bottom "20px"})

(def quick-button-style
  {:padding "8px 16px"
   :background "var(--button-bg, #f3f4f6)"
   :color "var(--button-text, #374151)"
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "6px"
   :cursor "pointer"
   :font-size "13px"
   :font-weight "500"
   :transition "all 0.2s"
   :box-shadow "0 1px 2px var(--shadow-color, rgba(0,0,0,0.1))"})

(def temp-display-style
  {:text-align "center"
   :padding "30px 0"
   :border-bottom "1px solid #e0e0e0"})

(def large-temp-style
  {:font-size "72px"
   :font-weight "300"
   :color "var(--text-primary, #1f2937)"
   :margin "0"
   :line-height "1"})

(def condition-text-style
  {:font-size "20px"
   :color "var(--text-secondary, #6b7280)"
   :margin "10px 0 20px 0"})

(def unit-toggle-style
  {:display "flex"
   :justify-content "center"
   :gap "8px"
   :margin-top "15px"})

(def unit-button-style
  {:padding "6px 16px"
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "6px"
   :background "var(--button-bg, #f3f4f6)"
   :color "var(--button-text, #374151)"
   :cursor "pointer"
   :font-size "14px"
   :transition "all 0.2s"})

(def unit-button-active-style
  (merge unit-button-style
         {:background "#3b82f6"
          :color "white"
          :border-color "#3b82f6"}))

(def metrics-grid-style
  {:display "grid"
   :grid-template-columns "repeat(auto-fit, minmax(200px, 1fr))"
   :gap "20px"
   :padding "20px 0"})

(def metric-card-style
  {:padding "15px"
   :background "var(--card-secondary-bg, #f9fafb)"
   :border-radius "8px"
   :border "1px solid var(--border-color, #e5e7eb)"})

(def metric-label-style
  {:font-size "13px"
   :color "#9ca3af"
   :margin "0 0 5px 0"
   :font-weight "500"
   :text-transform "uppercase"
   :letter-spacing "0.5px"})

(def metric-value-style
  {:font-size "24px"
   :color "var(--text-primary, #1f2937)"
   :margin "0"
   :font-weight "500"})

(def station-info-style
  {:margin-top "20px"
   :padding-top "20px"
   :border-top "1px solid #e0e0e0"
   :font-size "13px"
   :color "#9ca3af"
   :text-align "center"})

(def loading-style
  {:text-align "center"
   :padding "40px"
   :color "#9ca3af"})

(def error-style
  {:background "#fef2f2"
   :border "1px solid #fecaca"
   :color "#dc2626"
   :padding "12px"
   :border-radius "6px"
   :margin "10px 0"})

;; ============================================================================
;; Quick City Locations
;; ============================================================================

(def quick-cities
  [{:name "Charlotte, NC" :lat 35.2271 :lon -80.8431}
   {:name "Miami, FL" :lat 25.7617 :lon -80.1918}
   {:name "Denver, CO" :lat 39.7392 :lon -104.9903}
   {:name "Seattle, WA" :lat 47.6062 :lon -122.3321}
   {:name "New York, NY" :lat 40.7128 :lon -74.0060}
   {:name "Los Angeles, CA" :lat 34.0522 :lon -118.2437}
   {:name "Chicago, IL" :lat 41.8781 :lon -87.6298}])

;; ============================================================================
;; Components
;; ============================================================================

(defn loading-spinner []
  [:div {:style loading-style}
   [:div {:style {:font-size "40px" :margin-bottom "10px"}} "⏳"]
   [:div "Loading current conditions..."]])

(defn error-message [{:keys [message]}]
  [:div {:style error-style}
   [:strong "Error: "] message])

(defn unit-toggle-buttons
  [{:keys [current-unit on-change]}]
  [:div {:style unit-toggle-style}
   (for [unit ["F" "C" "K"]]
     ^{:key unit}
     [:button
      {:style (if (= unit current-unit)
                unit-button-active-style
                unit-button-style)
       :on-click #(on-change unit)}
      unit])])

(defn metric-card
  [{:keys [label value]}]
  [:div {:style metric-card-style}
   [:div {:style metric-label-style} label]
   [:div {:style metric-value-style} (or value "—")]])

(defn temperature-display
  [{:keys [temp-c unit condition]}]
  [:div {:style temp-display-style}
   [:div {:style large-temp-style}
    (format-temp temp-c unit)]
   [:div {:style condition-text-style}
    (or condition "No data")]])

(defn metrics-grid [{:keys [observations unit]}]
  (let [props (:properties observations)
        temp-c (:value (:temperature props))
        dewpoint-c (:value (:dewpoint props))
        wind-speed-mps (:value (:windSpeed props))
        wind-dir (:value (:windDirection props))
        humidity (:value (:relativeHumidity props))
        pressure (:value (:barometricPressure props))
        visibility-m (:value (:visibility props))
        heat-index-c (:value (:heatIndex props))
        wind-chill-c (:value (:windChill props))]

    [:div {:style metrics-grid-style}
     [metric-card
      {:label "Humidity"
       :value (when humidity (str (Math/round humidity) "%"))}]

     [metric-card
      {:label "Wind"
       :value (when wind-speed-mps
                (str (Math/round (mps-to-mph wind-speed-mps)) " mph "
                     (when wind-dir
                       (str "from " (format-wind-direction wind-dir)))))}]

     [metric-card
      {:label "Pressure"
       :value (when pressure
                (str (Math/round (/ pressure 100)) " mb"))}]

     [metric-card
      {:label "Visibility"
       :value (when visibility-m
                (let [miles (meters-to-miles visibility-m)]
                  (str (Math/round miles) " mi")))}]

     [metric-card
      {:label "Dewpoint"
       :value (format-temp dewpoint-c unit)}]

     (when heat-index-c
       [metric-card
        {:label "Heat Index"
         :value (format-temp heat-index-c unit)}])

     (when wind-chill-c
       [metric-card
        {:label "Wind Chill"
         :value (format-temp wind-chill-c unit)}])]))

(defn station-info
  [{:keys [observations]}]
  (let [props (:properties observations)
        station (:station props)
        timestamp (:timestamp props)
        station-id (last (str/split station #"/"))]
    [:div {:style station-info-style}
     [:div "Station: " station-id]
     [:div "Last Updated: "
      (when timestamp
        (.toLocaleString (js/Date. timestamp)))]]))

(defn weather-display
  [{:keys [observations unit on-unit-change]}]
  (let [props (:properties observations)
        temp-c (:value (:temperature props))
        condition (:textDescription props)]
    [:div {:style card-style}
     [temperature-display
      {:temp-c temp-c
       :unit unit
       :condition condition}]

     [unit-toggle-buttons
      {:current-unit unit
       :on-change on-unit-change}]

     [metrics-grid
      {:observations observations
       :unit unit}]

     [station-info
      {:observations observations}]]))

(defn location-input
  [{:keys [lat
           lon
           on-lat-change
           on-lon-change
           on-fetch]}]
  [:div
   [:div {:style input-group-style}
    [:input {:type "number"
             :placeholder "Latitude"
             :value lat
             :on-change #(on-lat-change (.. % -target -value))
             :step "0.0001"
             :style input-style}]
    [:input {:type "number"
             :placeholder "Longitude"
             :value lon
             :on-change #(on-lon-change (.. % -target -value))
             :step "0.0001"
             :style input-style}]
    [:button {:on-click on-fetch
              :style button-style
              :on-mouse-over #(set! (.. % -target -style -background) "#2563eb")
              :on-mouse-out #(set! (.. % -target -style -background) "#3b82f6")}
     "Get Conditions"]]])

(defn quick-city-buttons
  [{:keys [cities on-select]}]
  [:div {:style quick-buttons-style}
   (for [city cities]
     ^{:key (:name city)}
     [:button
      {:style quick-button-style
       :on-click #(on-select city)}
      (:name city)])])

;; ============================================================================
;; Main Component
;; ============================================================================

(defn main-component []
  (let [lat (r/atom "35.2271")
        lon (r/atom "-80.8431")
        observations (r/atom nil)
        loading? (r/atom false)
        error (r/atom nil)
        unit (r/atom "F")

        fetch-conditions
        (fn []
          (reset! loading? true)
          (reset! error nil)
          (reset! observations nil)

          (get-weather-station
           {:lat @lat
            :lon @lon
            :on-success
            (fn [station-id]
              (get-latest-observations
               {:station-id station-id
                :on-success
                (fn [obs-data]
                  (reset! observations obs-data)
                  (reset! loading? false))
                :on-error
                (fn [err]
                  (reset! error (str "Failed to fetch observations: " err))
                  (reset! loading? false))}))
            :on-error
            (fn [err]
              (reset! error (str "Failed to find weather station: " err))
              (reset! loading? false))}))

        select-city
        (fn [city]
          (reset! lat (str (:lat city)))
          (reset! lon (str (:lon city)))
          (fetch-conditions))]

    (fn []
      [:div
       [theme-styles]
       [:div {:style container-style}
        [:div {:style header-style}
         [:h1 {:style title-style} "☀️ Current Weather Conditions"]
         [:p {:style subtitle-style}
          "Detailed weather metrics from NOAA National Weather Service"]]

        [:div {:style card-style}
         [location-input
          {:lat @lat
           :lon @lon
           :on-lat-change #(reset! lat %)
           :on-lon-change #(reset! lon %)
           :on-fetch fetch-conditions}]

         [quick-city-buttons
          {:cities quick-cities
           :on-select select-city}]]

        (cond
          @loading? [loading-spinner]
          @error [error-message {:message @error}]
          @observations [weather-display
                         {:observations @observations
                          :unit @unit
                          :on-unit-change #(reset! unit %)}])]])))

;; ============================================================================
;; Mount
;; ============================================================================

(defn ^:export init []
  (when-let [el (js/document.getElementById "current-conditions-demo")]
    (rdom/render [main-component] el)))

(init)
