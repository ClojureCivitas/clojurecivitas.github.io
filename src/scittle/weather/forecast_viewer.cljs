(ns scittle.weather.forecast-viewer
  "7-day weather forecast with visual cards and period toggles.
   Demonstrates grid layouts, emoji icons, and responsive design."
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as str]))

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

(defn get-forecast-url
  "Get forecast URL for coordinates."
  [{:keys [lat lon on-success on-error]}]
  (fetch-json
   {:url (str "https://api.weather.gov/points/" lat "," lon)
    :on-success (fn [data]
                  (let [forecast-url (get-in data [:properties :forecast])]
                    (on-success forecast-url)))
    :on-error on-error}))

(defn get-forecast-data
  "Get forecast data from forecast URL."
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
;; Temperature Conversion
;; ============================================================================

(defn fahrenheit-to-celsius [f]
  "Convert Fahrenheit to Celsius"
  (when f (* (- f 32) 0.5556)))

(defn fahrenheit-to-kelvin [f]
  "Convert Fahrenheit to Kelvin"
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

;; ============================================================================
;; Styles
;; ============================================================================

(def container-style
  {:max-width "1200px"
   :margin "0 auto"
   :padding "20px"
   :font-family "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"})

(def card-style
  {:background "#ffffff"
   :border "1px solid #e0e0e0"
   :border-radius "12px"
   :padding "24px"
   :margin-bottom "20px"
   :box-shadow "0 2px 8px rgba(0,0,0,0.1)"})

(def header-style
  {:text-align "center"
   :margin-bottom "30px"})

(def title-style
  {:font-size "28px"
   :font-weight "600"
   :color "#1a1a1a"
   :margin "0 0 10px 0"})

(def subtitle-style
  {:font-size "14px"
   :color "#666"
   :margin 0})

(def input-group-style
  {:display "flex"
   :gap "10px"
   :margin-bottom "20px"
   :flex-wrap "wrap"})

(def input-style
  {:padding "10px 15px"
   :border "1px solid #ddd"
   :border-radius "6px"
   :font-size "14px"
   :flex "1"
   :min-width "120px"})

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
   :background "#f3f4f6"
   :border "1px solid #e5e7eb"
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

(def toggle-group-style
  {:display "flex"
   :gap "8px"})

(def toggle-button-style
  {:padding "8px 16px"
   :border "1px solid #ddd"
   :border-radius "6px"
   :background "#fff"
   :cursor "pointer"
   :font-size "14px"
   :transition "all 0.2s"})

(def toggle-button-active-style
  (merge toggle-button-style
         {:background "#3b82f6"
          :color "white"
          :border-color "#3b82f6"}))

(def forecast-grid-style
  {:display "grid"
   :grid-template-columns "repeat(auto-fill, minmax(200px, 1fr))"
   :gap "15px"
   :margin-top "20px"})

(def forecast-card-style
  {:background "#ffffff"
   :border "1px solid #e5e7eb"
   :border-radius "10px"
   :padding "20px"
   :text-align "center"
   :transition "all 0.2s"
   :box-shadow "0 1px 3px rgba(0,0,0,0.1)"})

(def forecast-card-hover-style
  (merge forecast-card-style
         {:box-shadow "0 4px 12px rgba(0,0,0,0.15)"
          :transform "translateY(-2px)"}))

(def period-name-style
  {:font-size "16px"
   :font-weight "600"
   :color "#1a1a1a"
   :margin "0 0 10px 0"})

(def weather-icon-style
  {:font-size "48px"
   :margin "10px 0"})

(def temp-style
  {:font-size "32px"
   :font-weight "600"
   :color "#3b82f6"
   :margin "10px 0"})

(def forecast-text-style
  {:font-size "14px"
   :color "#4b5563"
   :margin "10px 0 5px 0"
   :line-height "1.4"})

(def wind-style
  {:font-size "13px"
   :color "#6b7280"
   :margin "5px 0"})

(def precip-style
  {:font-size "13px"
   :color "#3b82f6"
   :margin "5px 0"
   :font-weight "500"})

(def loading-style
  {:text-align "center"
   :padding "40px"
   :color "#6b7280"})

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
   :color "#1a1a1a"
   :margin-bottom "10px"})

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
   [:div "Loading forecast data..."]])

(defn error-message [{:keys [message]}]
  [:div {:style error-style}
   [:strong "Error: "] message])

(defn location-input [{:keys [lat lon on-lat-change on-lon-change on-fetch]}]
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
     "Get Forecast"]]])

(defn quick-city-buttons [{:keys [cities on-select]}]
  [:div {:style quick-buttons-style}
   (for [city cities]
     ^{:key (:name city)}
     [:button
      {:style quick-button-style
       :on-click #(on-select city)
       :on-mouse-over #(set! (.. % -target -style -background) "#e5e7eb")
       :on-mouse-out #(set! (.. % -target -style -background) "#f3f4f6")}
      (:name city)])])

(defn controls-bar [{:keys [show-all? on-toggle-periods unit on-unit-change]}]
  [:div {:style controls-bar-style}
   [:div {:style toggle-group-style}
    [:button
     {:style (if-not show-all? toggle-button-active-style toggle-button-style)
      :on-click #(on-toggle-periods false)}
     "Show 7 Days"]
    [:button
     {:style (if show-all? toggle-button-active-style toggle-button-style)
      :on-click #(on-toggle-periods true)}
     "Show 14 Periods"]]

   [:div {:style toggle-group-style}
    (for [u ["F" "C" "K"]]
      ^{:key u}
      [:button
       {:style (if (= u unit) toggle-button-active-style toggle-button-style)
        :on-click #(on-unit-change u)}
       u])]])

(defn forecast-card [{:keys [period unit]}]
  (let [hovering? (r/atom false)]
    (fn [{:keys [period unit]}]
      (let [{:keys [name temperature windSpeed windDirection
                    shortForecast detailedForecast probabilityOfPrecipitation]} period
            precip-value (get-in probabilityOfPrecipitation [:value])
            icon (get-weather-icon shortForecast)]
        [:div
         {:style (if @hovering? forecast-card-hover-style forecast-card-style)
          :on-mouse-enter #(reset! hovering? true)
          :on-mouse-leave #(reset! hovering? false)}

         [:div {:style period-name-style} name]
         [:div {:style weather-icon-style} icon]
         [:div {:style temp-style} (format-temp temperature unit)]
         [:div {:style forecast-text-style} shortForecast]

         (when (and windSpeed (not= windSpeed ""))
           [:div {:style wind-style}
            "💨 " windSpeed " " (or windDirection "")])

         (when (and precip-value (> precip-value 0))
           [:div {:style precip-style}
            "💧 " precip-value "% chance"])]))))

(defn forecast-grid [{:keys [periods unit show-all?]}]
  (let [displayed-periods (if show-all? periods (take 7 periods))]
    [:div {:style forecast-grid-style}
     (for [period displayed-periods]
       ^{:key (:number period)}
       [forecast-card {:period period :unit unit}])]))

(defn forecast-display [{:keys [forecast-data location-name unit show-all? on-toggle-periods on-unit-change]}]
  (let [periods (get-in forecast-data [:properties :periods])]
    [:div
     [:div {:style location-display-style} "📍 " location-name]

     [controls-bar
      {:show-all? show-all?
       :on-toggle-periods on-toggle-periods
       :unit unit
       :on-unit-change on-unit-change}]

     [forecast-grid
      {:periods periods
       :unit unit
       :show-all? show-all?}]]))

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
        show-all? (r/atom false)

        fetch-forecast
        (fn []
          (reset! loading? true)
          (reset! error nil)
          (reset! forecast-data nil)

          (get-forecast-url
           {:lat @lat
            :lon @lon
            :on-success
            (fn [forecast-url]
              (get-forecast-data
               {:url forecast-url
                :on-success
                (fn [data]
                  (reset! forecast-data data)
                  (reset! loading? false))
                :on-error
                (fn [err]
                  (reset! error (str "Failed to fetch forecast: " err))
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
      [:div {:style container-style}
       [:div {:style header-style}
        [:h1 {:style title-style} "📅 7-Day Weather Forecast"]
        [:p {:style subtitle-style}
         "Visual forecast cards with detailed period information"]]

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
                           :show-all? @show-all?
                           :on-toggle-periods #(reset! show-all? %)
                           :on-unit-change #(reset! unit %)}]])])))

;; ============================================================================
;; Mount
;; ============================================================================

(defn ^:export init []
  (when-let [el (js/document.getElementById "forecast-viewer-demo")]
    (rdom/render [main-component] el)))

(init)
