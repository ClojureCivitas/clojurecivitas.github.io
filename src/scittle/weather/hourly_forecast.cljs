(ns scittle.weather.hourly-forecast
  "Hourly weather forecast with interactive timeline and slider controls.
   Demonstrates horizontal scrolling, time formatting, and dynamic data display."
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
;; API Functions (Inline)
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

(defn get-hourly-forecast-url
  "Get hourly forecast URL for coordinates."
  [{:keys [lat lon on-success on-error]}]
  (fetch-json
   {:url (str "https://api.weather.gov/points/" lat "," lon)
    :on-success (fn [data]
                  (let [hourly-url (get-in data [:properties :forecastHourly])]
                    (on-success hourly-url)))
    :on-error on-error}))

(defn get-hourly-forecast-data
  "Get hourly forecast data from URL."
  [{:keys [url on-success on-error]}]
  (fetch-json
   {:url url
    :on-success on-success
    :on-error on-error}))

;; ============================================================================
;; Weather Icon Mapping
;; ============================================================================

(defn get-weather-icon
  "Map weather conditions to emoji icons."
  [short-forecast]
  (let [forecast-lower (str/lower-case (or short-forecast ""))]
    (cond
      (re-find #"thunder|tstorm" forecast-lower) "⛈️"
      (re-find #"rain|shower" forecast-lower) "🌧️"
      (re-find #"snow|flurr" forecast-lower) "❄️"
      (re-find #"sleet|ice" forecast-lower) "🌨️"
      (re-find #"fog|mist" forecast-lower) "🌫️"
      (re-find #"cloud" forecast-lower) "☁️"
      (re-find #"partly|mostly" forecast-lower) "⛅"
      (re-find #"clear|sunny" forecast-lower) "☀️"
      (re-find #"wind" forecast-lower) "💨"
      :else "🌤️")))

;; ============================================================================
;; Time Utilities
;; ============================================================================

(defn parse-iso-time
  "Parse ISO 8601 time string to JS Date."
  [iso-string]
  (js/Date. iso-string))

(defn format-hour-time
  "Format Date object to hour display (e.g., '2 PM', '11 AM')."
  [date]
  (let [hours (.getHours date)
        period (if (< hours 12) "AM" "PM")
        display-hour (cond
                       (= hours 0) 12
                       (> hours 12) (- hours 12)
                       :else hours)]
    (str display-hour " " period)))

(defn format-day-time
  "Format Date object to day and time (e.g., 'Fri 2 PM')."
  [date]
  (let [days ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat"]
        day-name (nth days (.getDay date))
        time (format-hour-time date)]
    (str day-name " " time)))

(defn is-current-hour?
  "Check if the given date is within the current hour."
  [date]
  (let [now (js/Date.)
        date-hour (.getHours date)
        now-hour (.getHours now)
        date-day (.getDate date)
        now-day (.getDate now)]
    (and (= date-day now-day)
         (= date-hour now-hour))))

;; ============================================================================
;; Temperature Conversion
;; ============================================================================

(defn fahrenheit-to-celsius
  "Convert Fahrenheit to Celsius"
  [f]
  (when f (* (- f 32) 0.5556)))

(defn fahrenheit-to-kelvin
  "Convert Fahrenheit to Kelvin"
  [f]
  (when f (+ (fahrenheit-to-celsius f) 273.15)))

(defn format-temp
  "Format temperature based on unit (F, C, or K)"
  [fahrenheit unit]
  (when fahrenheit
    (case unit
      "F" (str fahrenheit "°F")
      "C" (str (Math/round (fahrenheit-to-celsius fahrenheit)) "°C")
      "K" (str (Math/round (fahrenheit-to-kelvin fahrenheit)) "K")
      (str fahrenheit "°F"))))

(defn mps-to-mph
  "Convert meters per second to miles per hour"
  [mps]
  (when mps (* mps 2.237)))

;; ============================================================================
;; Styles
;; ============================================================================

(def container-style
  {:max-width "1200px"
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
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "6px"
   :font-size "14px"
   :flex "1"
   :min-width "120px"
   :background "var(--input-bg, #ffffff)"
   :color "var(--text-primary, #1f2937)"})

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
   :transition "all 0.2s"})

(def controls-bar-style
  {:display "flex"
   :justify-content "space-between"
   :align-items "center"
   :margin-bottom "20px"
   :flex-wrap "wrap"
   :gap "15px"})

(def slider-group-style
  {:display "flex"
   :gap "8px"
   :align-items "center"})

(def slider-label-style
  {:font-size "14px"
   :font-weight "500"
   :color "var(--text-secondary, #6b7280)"})

(def slider-button-style
  {:padding "6px 14px"
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "6px"
   :background "var(--button-bg, #f3f4f6)"
   :color "var(--button-text, #374151)"
   :cursor "pointer"
   :font-size "13px"
   :transition "all 0.2s"})

(def slider-button-active-style
  (merge slider-button-style
         {:background "#3b82f6"
          :color "white"
          :border-color "#3b82f6"}))

(def unit-toggle-group-style
  {:display "flex"
   :gap "8px"})

(def unit-button-style
  {:padding "6px 14px"
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "6px"
   :background "var(--button-bg, #f3f4f6)"
   :color "var(--button-text, #374151)"
   :cursor "pointer"
   :font-size "13px"
   :transition "all 0.2s"})

(def unit-button-active-style
  (merge unit-button-style
         {:background "#3b82f6"
          :color "white"
          :border-color "#3b82f6"}))

(def timeline-container-style
  {:overflow-x "auto"
   :overflow-y "hidden"
   :padding "10px 0"
   :margin "20px 0"
   :scroll-behavior "smooth"
   :-webkit-overflow-scrolling "touch"})

(def timeline-track-style
  {:display "flex"
   :gap "12px"
   :padding "5px"})

(def hour-card-style
  {:background "var(--card-secondary-bg, #f9fafb)"
   :border "1px solid var(--border-color, #e5e7eb)"
   :border-radius "10px"
   :padding "16px"
   :min-width "140px"
   :text-align "center"
   :flex-shrink "0"
   :transition "all 0.2s"
   :box-shadow "0 1px 3px var(--shadow-color, rgba(0,0,0,0.1))"})

(def hour-card-current-style
  (merge hour-card-style
         {:border "2px solid #3b82f6"
          :background "#eff6ff"
          :box-shadow "0 2px 8px rgba(59,130,246,0.3)"}))

(def hour-card-hover-style
  (merge hour-card-style
         {:box-shadow "0 4px 12px rgba(0,0,0,0.15)"
          :transform "translateY(-2px)"}))

(def time-label-style
  {:font-size "14px"
   :font-weight "600"
   :color "var(--text-primary, #1f2937)"
   :margin "0 0 8px 0"})

(def weather-icon-style
  {:font-size "36px"
   :margin "8px 0"})

(def temp-display-style
  {:font-size "24px"
   :font-weight "600"
   :color "#3b82f6"
   :margin "8px 0"})

(def precip-display-style
  {:font-size "12px"
   :color "#3b82f6"
   :margin "4px 0"})

(def wind-display-style
  {:font-size "12px"
   :color "#9ca3af"
   :margin "4px 0"})

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

(def location-display-style
  {:font-size "18px"
   :font-weight "500"
   :color "var(--text-primary, #1f2937)"
   :margin-bottom "15px"})

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

(defn loading-spinner
  []
  [:div {:style loading-style}
   [:div {:style {:font-size "40px" :margin-bottom "10px"}} "⏳"]
   [:div "Loading hourly forecast..."]])

(defn error-message [{:keys [message]}]
  [:div {:style error-style}
   [:strong "Error: "] message])

(defn location-input
  [{:keys [lat lon on-lat-change on-lon-change on-fetch]}]
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
              :style button-style}
     "Get Hourly Forecast"]]])

(defn quick-city-buttons
  [{:keys [cities on-select]}]
  [:div {:style quick-buttons-style}
   (for [city cities]
     ^{:key (:name city)}
     [:button
      {:style quick-button-style
       :on-click #(on-select city)}
      (:name city)])])

(defn controls-bar
  [{:keys [hours-to-show on-hours-change unit on-unit-change]}]
  [:div {:style controls-bar-style}
   [:div {:style slider-group-style}
    [:span {:style slider-label-style} "Show:"]
    (for [hours [6 12 24 48]]
      ^{:key hours}
      [:button
       {:style (if (= hours hours-to-show)
                 slider-button-active-style
                 slider-button-style)
        :on-click #(on-hours-change hours)}
       (str hours "h")])]

   [:div {:style unit-toggle-group-style}
    (for [u ["F" "C" "K"]]
      ^{:key u}
      [:button
       {:style (if (= u unit) unit-button-active-style unit-button-style)
        :on-click #(on-unit-change u)}
       u])]])

(defn hour-card
  [{:keys [period unit]}]
  (let [{:keys [startTime temperature windSpeed shortForecast
                probabilityOfPrecipitation]} period
        date (parse-iso-time startTime)
        is-current? (is-current-hour? date)
        precip-value (get-in probabilityOfPrecipitation [:value])
        icon (get-weather-icon shortForecast)
        wind-mph (when windSpeed
                   (let [wind-str (str windSpeed)
                         matches (re-find #"(\d+)" wind-str)]
                     (when matches (js/parseInt (second matches)))))]
    [:div
     {:style (if is-current?
               hour-card-current-style
               hour-card-style)}

     [:div {:style time-label-style}
      (if is-current? "🔵 Now" (format-hour-time date))]
     [:div {:style weather-icon-style} icon]
     [:div {:style temp-display-style} (format-temp temperature unit)]

     (when (and precip-value (> precip-value 0))
       [:div {:style precip-display-style}
        "💧 " precip-value "%"])

     (when wind-mph
       [:div {:style wind-display-style}
        "💨 " wind-mph " mph"])]))

(defn hourly-timeline [{:keys [periods unit hours-to-show timeline-ref]}]
  (let [displayed-periods (take hours-to-show periods)]
    [:div {:style timeline-container-style
           :ref timeline-ref}
     [:div {:style timeline-track-style}
      (for [period displayed-periods]
        ^{:key (:number period)}
        [hour-card {:period period :unit unit}])]]))

(defn forecast-display
  [{:keys [forecast-data
           location-name
           unit
           hours-to-show
           on-hours-change
           on-unit-change
           timeline-ref]}]
  (let [periods (get-in forecast-data [:properties :periods])]
    [:div
     [:div {:style location-display-style} "📍 " location-name]

     [controls-bar
      {:hours-to-show hours-to-show
       :on-hours-change on-hours-change
       :unit unit
       :on-unit-change on-unit-change}]

     [hourly-timeline
      {:periods periods
       :unit unit
       :hours-to-show hours-to-show
       :timeline-ref timeline-ref}]]))

;; ============================================================================
;; Main Component
;; ============================================================================

(defn main-component []
  (let [lat (r/atom "35.2271")
        lon (r/atom "-80.8431")
        location-name (r/atom "Charlotte, NC")
        forecast-data (r/atom nil)
        loading? (r/atom false)
        error (r/atom nil)
        unit (r/atom "F")
        hours-to-show (r/atom 24)
        timeline-ref (atom nil)

        scroll-to-current
        (fn []
          (when-let [container @timeline-ref]
            (when-let [current-card (.querySelector container "[style*='border: 2px solid']")]
              (.scrollIntoView current-card #js {:behavior "smooth" :inline "center"}))))

        fetch-forecast
        (fn []
          (reset! loading? true)
          (reset! error nil)
          (reset! forecast-data nil)

          (get-hourly-forecast-url
           {:lat @lat
            :lon @lon
            :on-success
            (fn [hourly-url]
              (get-hourly-forecast-data
               {:url hourly-url
                :on-success
                (fn [data]
                  (reset! forecast-data data)
                  (reset! loading? false)
                  ;; Scroll to current hour after a short delay
                  (js/setTimeout scroll-to-current 300))
                :on-error
                (fn [err]
                  (reset! error (str "Failed to fetch hourly forecast: " err))
                  (reset! loading? false))}))
            :on-error
            (fn [err]
              (reset! error (str "Failed to find location: " err))
              (reset! loading? false))}))

        select-city
        (fn [city]
          (reset! lat (str (:lat city)))
          (reset! lon (str (:lon city)))
          (reset! location-name (:name city))
          (fetch-forecast))]

    (fn []
      [:<>
       [theme-styles]
       [:div {:style container-style}
        [:div {:style header-style}
         [:h1 {:style title-style} "⏰ Hourly Weather Forecast"]
         [:p {:style subtitle-style}
          "Hour-by-hour predictions with interactive timeline"]]]

       [:div {:style card-style}
        [location-input
         {:lat @lat
          :lon @lon
          :on-lat-change #(reset! lat %)
          :on-lon-change #(reset! lon %)
          :on-fetch fetch-forecast}]

        [quick-city-buttons
         {:cities quick-cities
          :on-select select-city}]]

       (cond
         @loading? [loading-spinner]
         @error [error-message {:message @error}]
         @forecast-data [:div {:style card-style}
                         [forecast-display
                          {:forecast-data @forecast-data
                           :location-name @location-name
                           :unit @unit
                           :hours-to-show @hours-to-show
                           :on-hours-change #(reset! hours-to-show %)
                           :on-unit-change #(reset! unit %)
                           :timeline-ref timeline-ref}]])])))

;; ============================================================================
;; Mount
;; ============================================================================

(defn ^:export init []
  (when-let [el (js/document.getElementById "hourly-forecast-demo")]
    (rdom/render [main-component] el)))

(init)
