(ns scittle.weather.complete-dashboard
  "Full-featured weather dashboard integrating all previous demos.
   Demonstrates tab navigation, settings management, and comprehensive weather display."
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
;; API Functions (Consolidated)
;; ============================================================================

(defn fetch-json
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

(defn get-all-weather-data
  "Fetch all weather data for a location."
  [{:keys [lat lon on-success on-error]}]
  (fetch-json
   {:url (str "https://api.weather.gov/points/" lat "," lon)
    :on-success
    (fn [points-data]
      (let [props (:properties points-data)
            forecast-url (:forecast props)
            hourly-url (:forecastHourly props)
            station-url (:observationStations props)]
        ;; Fetch all data in parallel
        (let [results (atom {:forecast nil :hourly nil :station-id nil :observations nil})
              complete-check (fn []
                               (when (and (:forecast @results)
                                          (:hourly @results)
                                          (:observations @results))
                                 (on-success @results)))]

          ;; Fetch forecast
          (fetch-json
           {:url forecast-url
            :on-success (fn [data]
                          (swap! results assoc :forecast data)
                          (complete-check))
            :on-error on-error})

          ;; Fetch hourly
          (fetch-json
           {:url hourly-url
            :on-success (fn [data]
                          (swap! results assoc :hourly data)
                          (complete-check))
            :on-error on-error})

          ;; Fetch station and observations
          (fetch-json
           {:url station-url
            :on-success (fn [stations]
                          (let [station-id (-> stations :features first :properties :stationIdentifier)]
                            (swap! results assoc :station-id station-id)
                            (fetch-json
                             {:url (str "https://api.weather.gov/stations/" station-id "/observations/latest")
                              :on-success (fn [obs]
                                            (swap! results assoc :observations obs)
                                            (complete-check))
                              :on-error on-error})))
            :on-error on-error}))))
    :on-error on-error}))

(defn get-alerts
  [{:keys [lat lon on-success on-error]}]
  (fetch-json
   {:url (str "https://api.weather.gov/alerts/active?point=" lat "," lon)
    :on-success on-success
    :on-error on-error}))

;; ============================================================================
;; Utilities (from previous demos)
;; ============================================================================

(defn celsius-to-fahrenheit
  [c]
  (when c (+ (* c 1.8) 32)))

(defn fahrenheit-to-celsius
  [f]
  (when f (* (- f 32) 0.5556)))

(defn fahrenheit-to-kelvin
  [f]
  (when f (+ (fahrenheit-to-celsius f) 273.15)))

(defn format-temp
  [fahrenheit unit]
  (when fahrenheit
    (case unit
      "F" (str fahrenheit "°F")
      "C" (str (Math/round (fahrenheit-to-celsius fahrenheit)) "°C")
      "K" (str (Math/round (fahrenheit-to-kelvin fahrenheit)) "K")
      (str fahrenheit "°F"))))

(defn get-weather-icon
  [short-forecast]
  (let [forecast-lower (str/lower-case (or short-forecast ""))]
    (cond
      (re-find #"thunder|tstorm" forecast-lower) "⛈️"
      (re-find #"rain|shower" forecast-lower) "🌧️"
      (re-find #"snow|flurr" forecast-lower) "❄️"
      (re-find #"cloud" forecast-lower) "☁️"
      (re-find #"partly|mostly" forecast-lower) "⛅"
      (re-find #"clear|sunny" forecast-lower) "☀️"
      :else "🌤️")))

;; ============================================================================
;; Styles
;; ============================================================================

(def app-container-style
  {:max-width "1400px"
   :margin "0 auto"
   :padding "20px"
   :font-family "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"})

(def header-card-style
  {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
   :color "white"
   :border-radius "12px"
   :padding "30px"
   :margin-bottom "20px"
   :box-shadow "0 4px 12px rgba(0,0,0,0.15)"})

(def app-title-style
  {:font-size "32px"
   :font-weight "700"
   :margin "0 0 10px 0"
   :display "flex"
   :align-items "center"
   :gap "15px"})

(def location-header-style
  {:font-size "18px"
   :opacity "0.9"
   :margin "10px 0 5px 0"})

(def last-updated-style
  {:font-size "14px"
   :opacity "0.8"})

(def card-style
  {:background "var(--card-bg, #ffffff)"
   :color "var(--text-primary, #1f2937)"
   :border "1px solid var(--border-color, #e5e7eb)"
   :border-radius "12px"
   :padding "24px"
   :margin-bottom "20px"
   :box-shadow "0 2px 8px var(--shadow-color, rgba(0,0,0,0.1))"})

(def location-search-style
  {:display "grid"
   :grid-template-columns "1fr 1fr auto"
   :gap "10px"
   :margin-bottom "15px"})

(def input-style
  {:padding "12px 15px"
   :background "var(--input-bg, #ffffff)"
   :color "var(--text-primary, #1f2937)"
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "8px"
   :font-size "14px"
   :box-shadow "inset 0 1px 2px var(--shadow-color, rgba(0,0,0,0.05))"})

(def button-primary-style
  {:padding "12px 24px"
   :background "#3b82f6"
   :color "white"
   :border "none"
   :border-radius "8px"
   :cursor "pointer"
   :font-size "14px"
   :font-weight "600"
   :transition "all 0.2s"
   :box-shadow "0 2px 4px rgba(0,0,0,0.3)"})

(def quick-cities-grid-style
  {:display "grid"
   :grid-template-columns "repeat(auto-fill, minmax(140px, 1fr))"
   :gap "10px"
   :margin-top "15px"})

(def city-button-style
  {:padding "10px"
   :background "var(--button-bg, #f3f4f6)"
   :color "var(--button-text, #374151)"
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "8px"
   :cursor "pointer"
   :font-size "13px"
   :font-weight "500"
   :text-align "center"
   :transition "all 0.2s"
   :box-shadow "0 1px 2px var(--shadow-color, rgba(0,0,0,0.1))"})

(def tabs-container-style
  {:display "flex"
   :gap "5px"
   :border-bottom "2px solid #374151"
   :margin-bottom "20px"})

(defn tab-style [active?]
  {:padding "12px 24px"
   :background (if active? "#3b82f6" "transparent")
   :color (if active? "white" "#6b7280")
   :border "none"
   :border-bottom (if active? "2px solid #3b82f6" "2px solid transparent")
   :cursor "pointer"
   :font-size "14px"
   :font-weight (if active? "600" "500")
   :transition "all 0.2s"
   :border-radius "8px 8px 0 0"})

(def settings-bar-style
  {:display "flex"
   :justify-content "space-between"
   :align-items "center"
   :padding "15px"
   :background "var(--card-secondary-bg, #f9fafb)"
   :border-radius "8px"
   :margin-bottom "20px"
   :flex-wrap "wrap"
   :gap "15px"})

(def setting-group-style
  {:display "flex"
   :align-items "center"
   :gap "10px"})

(def setting-label-style
  {:font-size "14px"
   :font-weight "500"
   :color "var(--text-secondary, #6b7280)"})

(def toggle-buttons-style
  {:display "flex"
   :gap "5px"})

(defn toggle-button-style [active?]
  {:padding "6px 14px"
   :background (if active? "#3b82f6" "var(--button-bg, #f3f4f6)")
   :color (if active? "white" "var(--button-text, #374151)")
   :border (if active? "1px solid #3b82f6" "1px solid var(--border-color-dark, #d1d5db)")
   :border-radius "6px"
   :cursor "pointer"
   :font-size "13px"
   :transition "all 0.2s"})

(def current-summary-style
  {:display "grid"
   :grid-template-columns "auto 1fr"
   :gap "30px"
   :align-items "center"})

(def large-temp-section-style
  {:text-align "center"})

(def temp-display-style
  {:font-size "72px"
   :font-weight "300"
   :color "var(--text-primary, #1f2937)"
   :line-height "1"
   :margin "0"})

(def condition-text-style
  {:font-size "18px"
   :color "var(--text-tertiary, #9ca3af)"
   :margin "10px 0"})

(def metrics-quick-grid-style
  {:display "grid"
   :grid-template-columns "repeat(auto-fit, minmax(150px, 1fr))"
   :gap "15px"})

(def metric-item-style
  {:padding "12px"
   :background "var(--card-secondary-bg, #f9fafb)"
   :border "1px solid var(--border-color, #e5e7eb)"
   :border-radius "8px"})

(def metric-label-style
  {:font-size "12px"
   :color "#9ca3af"
   :margin-bottom "5px"
   :text-transform "uppercase"
   :font-weight "600"})

(def metric-value-style
  {:font-size "20px"
   :color "var(--text-primary, #1f2937)"
   :font-weight "500"})

(def forecast-mini-grid-style
  {:display "grid"
   :grid-template-columns "repeat(auto-fit, minmax(120px, 1fr))"
   :gap "12px"})

(def forecast-mini-card-style
  {:padding "15px"
   :background "var(--card-secondary-bg, #f9fafb)"
   :border "1px solid var(--border-color, #e5e7eb)"
   :border-radius "8px"
   :text-align "center"})

(def loading-style
  {:text-align "center"
   :padding "60px"
   :color "#9ca3af"})

;; ============================================================================
;; Quick Cities
;; ============================================================================

(def quick-cities
  [{:name "Charlotte, NC" :lat 35.2271 :lon -80.8431}
   {:name "Miami, FL" :lat 25.7617 :lon -80.1918}
   {:name "Denver, CO" :lat 39.7392 :lon -104.9903}
   {:name "Seattle, WA" :lat 47.6062 :lon -122.3321}
   {:name "New York, NY" :lat 40.7128 :lon -74.0060}
   {:name "Los Angeles, CA" :lat 34.0522 :lon -118.2437}
   {:name "Chicago, IL" :lat 41.8781 :lon -87.6298}
   {:name "Phoenix, AZ" :lat 33.4484 :lon -112.0740}
   {:name "Boston, MA" :lat 42.3601 :lon -71.0589}])

;; ============================================================================
;; Components
;; ============================================================================

(defn loading-spinner []
  [:div {:style loading-style}
   [:div {:style {:font-size "60px" :margin-bottom "20px"}} "🌐"]
   [:div {:style {:font-size "18px"}} "Loading weather data..."]])

(defn location-search-panel [{:keys [lat lon on-lat-change on-lon-change on-fetch cities on-city-select]}]
  [:div {:style card-style}
   [:h3 {:style {:margin "0 0 15px 0" :font-size "18px"}} "📍 Location"]

   [:div {:style location-search-style}
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
              :style button-primary-style}
     "Get Weather"]]

   [:div {:style quick-cities-grid-style}
    (for [city cities]
      ^{:key (:name city)}
      [:button
       {:style city-button-style
        :on-click #(on-city-select city)}
       (:name city)])]])

(defn settings-bar [{:keys [unit on-unit-change]}]
  [:div {:style settings-bar-style}
   [:div {:style setting-group-style}
    [:span {:style setting-label-style} "Temperature Unit:"]
    [:div {:style toggle-buttons-style}
     (for [u ["F" "C" "K"]]
       ^{:key u}
       [:button
        {:style (toggle-button-style (= u unit))
         :on-click #(on-unit-change u)}
        u])]]

   [:div {:style {:font-size "13px" :color "#9ca3af"}}
    "🔄 Auto-refresh: Off"]])

(defn tabs-navigation [{:keys [active-tab on-tab-change]}]
  [:div {:style tabs-container-style}
   (for [tab [{:id :current :label "☀️ Current"}
              {:id :forecast :label "📅 7-Day"}
              {:id :hourly :label "⏰ Hourly"}
              {:id :alerts :label "⚠️ Alerts"}]]
     ^{:key (:id tab)}
     [:button
      {:style (tab-style (= active-tab (:id tab)))
       :on-click #(on-tab-change (:id tab))}
      (:label tab)])])

(defn current-conditions-view [{:keys [observations unit]}]
  (let [props (get-in observations [:properties])
        temp-c (get-in props [:temperature :value])
        temp-f (when temp-c (Math/round (celsius-to-fahrenheit temp-c)))
        condition (:textDescription props)
        humidity (get-in props [:relativeHumidity :value])
        wind-speed (get-in props [:windSpeed :value])
        pressure (get-in props [:barometricPressure :value])]

    [:div
     [:div {:style current-summary-style}
      [:div {:style large-temp-section-style}
       [:div {:style {:font-size "48px" :margin-bottom "10px"}}
        (get-weather-icon condition)]
       [:div {:style temp-display-style}
        (format-temp temp-f unit)]
       [:div {:style condition-text-style} condition]]

      [:div {:style metrics-quick-grid-style}
       [:div {:style metric-item-style}
        [:div {:style metric-label-style} "Humidity"]
        [:div {:style metric-value-style}
         (when humidity (str (Math/round humidity) "%"))]]

       [:div {:style metric-item-style}
        [:div {:style metric-label-style} "Wind"]
        [:div {:style metric-value-style}
         (when wind-speed (str (Math/round (* wind-speed 2.237)) " mph"))]]

       [:div {:style metric-item-style}
        [:div {:style metric-label-style} "Pressure"]
        [:div {:style metric-value-style}
         (when pressure (str (Math/round (/ pressure 100)) " mb"))]]]]]))

(defn forecast-view [{:keys [forecast unit]}]
  (let [periods (take 7 (get-in forecast [:properties :periods]))]
    [:div {:style forecast-mini-grid-style}
     (for [period periods]
       ^{:key (:number period)}
       [:div {:style forecast-mini-card-style}
        [:div {:style {:font-weight "600" :margin-bottom "8px"}}
         (:name period)]
        [:div {:style {:font-size "36px" :margin "10px 0"}}
         (get-weather-icon (:shortForecast period))]
        [:div {:style {:font-size "24px" :font-weight "600" :color "#3b82f6"}}
         (format-temp (:temperature period) unit)]
        [:div {:style {:font-size "13px" :color "#9ca3af" :margin-top "8px"}}
         (:shortForecast period)]])]))

(defn hourly-view [{:keys [hourly unit]}]
  (let [periods (take 12 (get-in hourly [:properties :periods]))]
    [:div {:style forecast-mini-grid-style}
     (for [period periods]
       ^{:key (:number period)}
       (let [date (js/Date. (:startTime period))
             hours (.getHours date)
             display-hour (if (= hours 0) 12 (if (> hours 12) (- hours 12) hours))
             period-label (if (< hours 12) "AM" "PM")]
         [:div {:style forecast-mini-card-style}
          [:div {:style {:font-weight "600" :margin-bottom "8px"}}
           (str display-hour " " period-label)]
          [:div {:style {:font-size "32px" :margin "8px 0"}}
           (get-weather-icon (:shortForecast period))]
          [:div {:style {:font-size "20px" :font-weight "600" :color "#3b82f6"}}
           (format-temp (:temperature period) unit)]]))]))

(defn alerts-view [{:keys [alerts]}]
  (let [features (:features alerts)]
    (if (empty? features)
      [:div {:style {:text-align "center" :padding "40px"}}
       [:div {:style {:font-size "64px"}} "✅"]
       [:div {:style {:font-size "18px" :font-weight "500" :margin-top "15px"}}
        "No Active Weather Alerts"]
       [:div {:style {:font-size "14px" :color "#9ca3af" :margin-top "8px"}}
        "All clear! No weather warnings or advisories."]]

      [:div
       (for [alert features]
         (let [props (:properties alert)]
           ^{:key (:id alert)}
           [:div {:style {:padding "20px"
                          :background "#fff3cd"
                          :border-left "4px solid #f59e0b"
                          :border-radius "8px"
                          :margin-bottom "15px"}}
            [:h4 {:style {:margin "0 0 10px 0" :color "#92400e"}}
             (:event props)]
            [:p {:style {:margin "0" :color "#78350f"}}
             (:headline props)]]))])))

(defn weather-dashboard [{:keys [weather-data location-name unit on-unit-change]}]
  (let [active-tab (r/atom :current)
        {:keys [observations forecast hourly alerts]} weather-data]

    (fn [{:keys [weather-data location-name unit on-unit-change]}]
      [:div
       [settings-bar
        {:unit unit
         :on-unit-change on-unit-change}]

       [tabs-navigation
        {:active-tab @active-tab
         :on-tab-change #(reset! active-tab %)}]

       [:div {:style card-style}
        (case @active-tab
          :current [current-conditions-view
                    {:observations (:observations weather-data)
                     :unit unit}]
          :forecast [forecast-view
                     {:forecast (:forecast weather-data)
                      :unit unit}]
          :hourly [hourly-view
                   {:hourly (:hourly weather-data)
                    :unit unit}]
          :alerts [alerts-view
                   {:alerts (or (:alerts weather-data) {:features []})}])]])))

;; ============================================================================
;; Main Component
;; ============================================================================

(defn main-component []
  (let [lat (r/atom "35.2271")
        lon (r/atom "-80.8431")
        location-name (r/atom "Charlotte, NC")
        weather-data (r/atom nil)
        loading? (r/atom false)
        unit (r/atom "F")
        last-updated (r/atom nil)

        fetch-all-weather
        (fn []
          (reset! loading? true)
          (reset! weather-data nil)

          (get-all-weather-data
           {:lat @lat
            :lon @lon
            :on-success
            (fn [data]
              ;; Also fetch alerts
              (get-alerts
               {:lat @lat
                :lon @lon
                :on-success (fn [alerts]
                              (reset! weather-data (assoc data :alerts alerts))
                              (reset! last-updated (js/Date.))
                              (reset! loading? false))
                :on-error (fn [_]
                            (reset! weather-data (assoc data :alerts {:features []}))
                            (reset! last-updated (js/Date.))
                            (reset! loading? false))}))
            :on-error
            (fn [err]
              (js/alert (str "Failed to fetch weather: " err))
              (reset! loading? false))}))

        select-city
        (fn [city]
          (reset! lat (str (:lat city)))
          (reset! lon (str (:lon city)))
          (reset! location-name (:name city))
          (fetch-all-weather))]

    (fn []
      [:div
       [theme-styles]
       [:div {:style app-container-style}
        [:div {:style header-card-style}
         [:h1 {:style app-title-style}
          [:span "☀️"] [:span "Weather Dashboard"]]
         (when @location-name
           [:div {:style location-header-style}
            "📍 " @location-name])
         (when @last-updated
           [:div {:style last-updated-style}
            "Last updated: " (.toLocaleTimeString @last-updated)])]

        [location-search-panel
         {:lat @lat
          :lon @lon
          :on-lat-change #(reset! lat %)
          :on-lon-change #(reset! lon %)
          :on-fetch fetch-all-weather
          :cities quick-cities
          :on-city-select select-city}]

        (cond
          @loading? [loading-spinner]
          @weather-data [weather-dashboard
                         {:weather-data @weather-data
                          :location-name @location-name
                          :unit @unit
                          :on-unit-change #(reset! unit %)}])]])))

;; ============================================================================
;; Mount
;; ============================================================================

(defn ^:export init []
  (when-let [el (js/document.getElementById "complete-dashboard-demo")]
    (rdom/render [main-component] el)))

(init)
