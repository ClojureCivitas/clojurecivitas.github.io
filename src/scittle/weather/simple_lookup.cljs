(ns scittle.weather.simple-lookup
  "Simple weather lookup demo - minimal example showing basic API usage.
   
   This is the simplest possible weather app:
   - Two input fields for coordinates
   - One button to fetch weather
   - Display location and temperature
   
   Demonstrates:
   - Basic API call with keyword arguments
   - Loading state
   - Error handling
   - Minimal Reagent UI"
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as str]))

;; ============================================================================
;; Inline API Functions (simplified for this demo)
;; ============================================================================

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

(defn fetch-json
  "Fetch JSON from URL with error handling."
  [{:keys [url on-success on-error]}]
  (-> (js/fetch url)
      (.then (fn [response]
               (if (.-ok response)
                 (.then (.json response)
                        (fn [data]
                          (on-success (js->clj data :keywordize-keys true))))
                 (on-error (str "HTTP Error: " (.-status response))))))
      (.catch (fn [error]
                (on-error (.-message error))))))

(defn fetch-weather-data
  "Fetch basic weather data for coordinates."
  [{:keys [lat lon on-success on-error]}]
  (let [points-url (str "https://api.weather.gov/points/" lat "," lon)]
    (fetch-json
     {:url points-url
      :on-success
      (fn [points-result]
         ;; Got points, now get forecast
        (let [properties (get-in points-result [:properties])
              forecast-url (:forecast properties)
              city (:city (get-in points-result [:properties :relativeLocation :properties]))
              state (:state (get-in points-result [:properties :relativeLocation :properties]))]

           ;; Fetch the forecast
          (fetch-json
           {:url forecast-url
            :on-success
            (fn [forecast-result]
              (let [periods (get-in forecast-result [:properties :periods])
                    first-period (first periods)]
                (on-success
                 {:city city
                  :state state
                  :temperature (:temperature first-period)
                  :temperatureUnit (:temperatureUnit first-period)
                  :shortForecast (:shortForecast first-period)
                  :detailedForecast (:detailedForecast first-period)})))
            :on-error on-error})))
      :on-error on-error})))

;; ============================================================================
;; Styles
;; ============================================================================

(def card-style
  {:background "var(--card-bg, #ffffff)"
   :border "1px solid var(--border-color, #e5e7eb)"
   :border-radius "8px"
   :padding "20px"
   :box-shadow "0 2px 4px var(--shadow-color, rgba(0,0,0,0.1))"
   :margin-bottom "20px"})

(def input-style
  {:width "100%"
   :padding "10px"
   :background "var(--input-bg, #ffffff)"
   :color "var(--text-primary, #1f2937)"
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "4px"
   :font-size "14px"
   :margin-bottom "10px"
   :box-shadow "inset 0 1px 2px var(--shadow-color, rgba(0,0,0,0.1))"})

(def button-style
  {:background "#2196f3"
   :color "white"
   :border "none"
   :padding "12px 24px"
   :border-radius "4px"
   :cursor "pointer"
   :font-size "16px"
   :font-weight "500"
   :width "100%"
   :transition "background 0.3s, transform 0.1s"
   :box-shadow "0 2px 4px rgba(0,0,0,0.1)"})

(def button-hover-style
  (merge button-style
         {:background "#1976d2"
          :box-shadow "0 4px 8px rgba(0,0,0,0.15)"}))

(def button-disabled-style
  (merge button-style
         {:background "#ccc"
          :cursor "not-allowed"
          :box-shadow "none"}))

;; ============================================================================
;; Components
;; ============================================================================

(defn loading-spinner
  "Simple loading indicator."
  []
  [:div {:style {:text-align "center"
                 :padding "40px"}}
   [:div {:style {:display "inline-block"
                  :width "40px"
                  :height "40px"
                  :border "4px solid #f3f3f3"
                  :border-top "4px solid #2196f3"
                  :border-radius "50%"
                  :animation "spin 1s linear infinite"}}]
   [:style "@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }"]
   [:p {:style {:margin-top "15px"
                :color "var(--text-tertiary, #9ca3af)"}}
    "Fetching weather data..."]])

(defn error-display
  "Display error message."
  [{:keys [error on-retry]}]
  [:div {:style (merge card-style
                       {:background "#ffebee"
                        :border "1px solid #ef5350"})}
   [:h4 {:style {:margin-top 0
                 :color "#c62828"}}
    "⚠️ Error"]
   [:p {:style {:color "var(--text-tertiary, #9ca3af)"}}
    error]
   (when on-retry
     [:button {:on-click on-retry
               :style {:background "#f44336"
                       :color "white"
                       :border "none"
                       :padding "8px 16px"
                       :border-radius "4px"
                       :cursor "pointer"
                       :margin-top "10px"}}
      "Try Again"])])

(defn weather-result
  "Display weather results."
  [{:keys [data]}]
  [:div {:style card-style}
   [:h2 {:style {:margin-top 0
                 :color "#2196f3"}}
    "📍 " (:city data) ", " (:state data)]

   [:div {:style {:text-align "center"
                  :margin "30px 0"}}
    [:div {:style {:font-size "48px"
                   :font-weight "bold"
                   :color "var(--text-primary, #1f2937)"}}
     (:temperature data) "°" (:temperatureUnit data)]
    [:div {:style {:font-size "18px"
                   :color "var(--text-tertiary, #9ca3af)"
                   :margin-top "10px"}}
     (:shortForecast data)]]

   [:div {:style {:background "var(--card-secondary-bg, #f9fafb)"
                  :padding "15px"
                  :border-radius "4px"
                  :margin-top "20px"}}
    [:p {:style {:margin 0
                 :line-height 1.6
                 :color "var(--text-secondary, #6b7280)"}}
     (:detailedForecast data)]]])

(defn input-form
  "Input form for coordinates."
  [{:keys [lat lon on-lat-change on-lon-change on-submit loading? disabled?]}]
  [:div {:style card-style}
   [:h3 {:style {:margin-top 0}}
    "🌍 Enter Coordinates"]
   [:p {:style {:color "var(--text-tertiary, #9ca3af)"
                :font-size "14px"
                :margin-bottom "15px"}}
    "Enter latitude and longitude to get weather data"]

   [:div
    [:label {:style {:display "block"
                     :margin-bottom "5px"
                     :color "var(--text-secondary, #6b7280)"
                     :font-weight "500"}}
     "Latitude"]
    [:input {:type "number"
             :step "0.0001"
             :placeholder "e.g., 40.7128"
             :value @lat
             :on-change #(on-lat-change (.. % -target -value))
             :disabled loading?
             :style (merge input-style
                           (when loading? {:opacity 0.6}))}]]

   [:div
    [:label {:style {:display "block"
                     :margin-bottom "5px"
                     :color "var(--text-secondary, #6b7280)"
                     :font-weight "500"}}
     "Longitude"]
    [:input {:type "number"
             :step "0.0001"
             :placeholder "e.g., -74.0060"
             :value @lon
             :on-change #(on-lon-change (.. % -target -value))
             :disabled loading?
             :style (merge input-style
                           (when loading? {:opacity 0.6}))}]]

   [:button {:on-click on-submit
             :disabled (or loading? disabled?)
             :style (cond
                      loading? button-disabled-style
                      disabled? button-disabled-style
                      :else button-style)}
    (if loading?
      "Loading..."
      "Get Weather")]])

(defn quick-locations
  "Quick access buttons for major cities."
  [{:keys [on-select loading?]}]
  [:div {:style {:margin-top "20px"}}
   [:p {:style {:color "var(--text-tertiary, #9ca3af)"
                :font-size "14px"
                :margin-bottom "10px"}}
    "Or try these cities:"]
   [:div {:style {:display "flex"
                  :flex-wrap "wrap"
                  :gap "8px"}}
    (for [[city lat lon] [["Charlotte, NC" 35.2271 -80.8431]
                          ["Miami, FL" 25.7617 -80.1918]
                          ["Denver, CO" 39.7392 -104.9903]
                          ["New York, NY" 40.7128 -74.0060]
                          ["Los Angeles, CA" 34.0522 -118.2437]
                          ["Chicago, IL" 41.8781 -87.6298]]]
      ^{:key city}
      [:button {:on-click #(on-select lat lon)
                :disabled loading?
                :style {:padding "6px 12px"
                        :background (if loading? "var(--button-bg, #f3f4f6)" "var(--button-bg, #f3f4f6)")
                        :color (if loading? "var(--text-tertiary, #9ca3af)" "#60a5fa")
                        :border (if loading? "1px solid var(--border-color, #e5e7eb)" "2px solid #60a5fa")
                        :border-radius "20px"
                        :cursor (if loading? "not-allowed" "pointer")
                        :font-size "13px"
                        :font-weight "500"
                        :transition "all 0.2s"
                        :box-shadow (when-not loading? "0 1px 3px var(--shadow-color, rgba(0,0,0,0.1))")}}
       city])]])

;; ============================================================================
;; Main Component
;; ============================================================================

(defn main-component
  "Main weather lookup component."
  []
  (let [lat (r/atom "35.2271")
        lon (r/atom "-80.8431")
        loading? (r/atom false)
        error (r/atom nil)
        weather-data (r/atom nil)

        fetch-weather (fn [latitude longitude]
                        (reset! loading? true)
                        (reset! error nil)
                        (reset! weather-data nil)

                        (fetch-weather-data
                         {:lat latitude
                          :lon longitude
                          :on-success (fn [data]
                                        (reset! loading? false)
                                        (reset! weather-data data))
                          :on-error (fn [err]
                                      (reset! loading? false)
                                      (reset! error err))}))]

    (fn []
      [:<>
       [theme-styles]
       [:div {:style {:max-width "600px"
                      :margin "0 auto"
                      :padding "20px"
                      :font-family "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"}}
        [:h1 {:style {:text-align "center"
                      :color "var(--text-primary, #1f2937)"
                      :margin-bottom "30px"}}
         "☀️ Simple Weather Lookup"]]

       ;; Input Form
       [input-form
        {:lat lat
         :lon lon
         :on-lat-change #(reset! lat %)
         :on-lon-change #(reset! lon %)
         :on-submit #(when (and (not (str/blank? @lat))
                                (not (str/blank? @lon)))
                       (fetch-weather @lat @lon))
         :loading? @loading?
         :disabled? (or (str/blank? @lat)
                        (str/blank? @lon))}]

       ;; Quick Locations
       [quick-locations
        {:on-select (fn [latitude longitude]
                      (reset! lat (str latitude))
                      (reset! lon (str longitude))
                      (fetch-weather latitude longitude))
         :loading? @loading?}]

       ;; Loading State
       (when @loading?
         [loading-spinner])

       ;; Error Display
       (when @error
         [error-display
          {:error @error
           :on-retry #(when (and (not (str/blank? @lat))
                                 (not (str/blank? @lon)))
                        (fetch-weather @lat @lon))}])

       ;; Weather Results
       (when @weather-data
         [weather-result {:data @weather-data}])

       ;; Instructions
       (when (and (not @loading?)
                  (not @error)
                  (not @weather-data))
         [:div {:style {:text-align "center"
                        :margin-top "40px"
                        :color "var(--text-tertiary, #9ca3af)"
                        :font-size "14px"}}
          [:p "Enter coordinates above or click a city to get started"]
          [:p {:style {:margin-top "10px"}}
           "Uses the free NWS API - no API key required!"]])])))

;; ============================================================================
;; Mount
;; ============================================================================

(defn ^:export init []
  (rdom/render [main-component]
               (js/document.getElementById "simple-lookup-demo")))

;; Auto-initialize when script loads
(init)
