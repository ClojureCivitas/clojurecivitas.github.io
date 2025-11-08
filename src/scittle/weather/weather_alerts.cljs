(ns scittle.weather.weather-alerts
  "Display active weather alerts with severity-based styling and expandable details.
   Demonstrates conditional rendering, color coding, and alert management."
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

(defn get-alerts-for-point
  "Get active weather alerts for coordinates."
  [{:keys [lat lon on-success on-error]}]
  (fetch-json
   {:url (str "https://api.weather.gov/alerts/active?point=" lat "," lon)
    :on-success on-success
    :on-error on-error}))

;; ============================================================================
;; Severity Utilities
;; ============================================================================

;; ============================================================================
;; Theme Styles
;; ============================================================================

(defn theme-styles
  "CSS for light and dark mode support using Quarto's data-bs-theme attribute"
  []
  [:style "
    /* Light mode (default) - inherits from parent */
    [data-bs-theme='light'],
    [data-bs-theme='light'] * {
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

    /* Dark mode - inherits from parent */
    [data-bs-theme='dark'],
    [data-bs-theme='dark'] * {
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

    /* Also check for Quarto's dark class on body */
    .quarto-dark,
    .quarto-dark * {
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

(defn get-severity-color
  "Map severity level to color."
  [severity]
  (case (str/lower-case (or severity "unknown"))
    "extreme" "#b91c1c" ; Dark red
    "severe" "#ea580c" ; Orange
    "moderate" "#ca8a04" ; Yellow/Gold
    "minor" "#65a30d" ; Green
    "var(--text-secondary, #6b7280)")) ; Gray for unknown

(defn get-urgency-badge-color
  "Map urgency level to badge color."
  [urgency]
  (case (str/lower-case (or urgency "unknown"))
    "immediate" "#dc2626"
    "expected" "#f59e0b"
    "future" "#3b82f6"
    "var(--text-secondary, #6b7280)"))

(defn get-certainty-badge-color
  "Map certainty level to badge color."
  [certainty]
  (case (str/lower-case (or certainty "unknown"))
    "observed" "#059669"
    "likely" "#3b82f6"
    "possible" "#8b5cf6"
    "var(--text-secondary, #6b7280)"))

(defn get-alert-icon
  "Map alert event to emoji icon."
  [event]
  (let [event-lower (str/lower-case (or event ""))]
    (cond
      (re-find #"tornado" event-lower) "🌪️"
      (re-find #"hurricane" event-lower) "🌀"
      (re-find #"flood" event-lower) "🌊"
      (re-find #"fire" event-lower) "🔥"
      (re-find #"heat" event-lower) "🌡️"
      (re-find #"winter|snow|ice|blizzard" event-lower) "❄️"
      (re-find #"wind" event-lower) "💨"
      (re-find #"thunder|lightning" event-lower) "⚡"
      (re-find #"fog" event-lower) "🌫️"
      :else "⚠️")))

;; ============================================================================
;; Time Utilities
;; ============================================================================

(defn format-alert-time
  "Format ISO time to readable format."
  [iso-string]
  (when iso-string
    (let [date (js/Date. iso-string)]
      (.toLocaleString date "en-US"
                       #js {:month "short"
                            :day "numeric"
                            :hour "numeric"
                            :minute "2-digit"}))))

;; ============================================================================
;; Styles
;; ============================================================================

(def container-style
  {:max-width "1000px"
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
   :background "var(--button-bg, #f3f4f6)"
   :color "var(--button-text, #374151)"
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "6px"
   :cursor "pointer"
   :font-size "13px"
   :transition "all 0.2s"})

(def alerts-container-style
  {:display "flex"
   :flex-direction "column"
   :gap "15px"})

(defn alert-card-style [severity-color]
  {:background "var(--card-bg, #ffffff)"
   :color "var(--text-primary, #1f2937)"
   :border-left (str "4px solid " severity-color)
   :border-radius "8px"
   :padding "20px"
   :box-shadow "0 2px 6px var(--shadow-color, rgba(0,0,0,0.1))"
   :transition "all 0.2s"})

(def alert-header-style
  {:display "flex"
   :justify-content "space-between"
   :align-items "flex-start"
   :margin-bottom "12px"
   :gap "15px"})

(def alert-title-section-style
  {:flex "1"})

(def alert-event-style
  {:font-size "20px"
   :font-weight "600"
   :color "var(--text-primary, #1f2937)"
   :margin "0 0 8px 0"
   :display "flex"
   :align-items "center"
   :gap "10px"})

(def alert-headline-style
  {:font-size "14px"
   :color "var(--text-secondary, #6b7280)"
   :margin "0 0 12px 0"
   :line-height "1.4"})

(def badges-container-style
  {:display "flex"
   :gap "8px"
   :flex-wrap "wrap"
   :margin-bottom "12px"})

(defn badge-style [color]
  {:background color
   :color "white"
   :padding "4px 12px"
   :border-radius "12px"
   :font-size "12px"
   :font-weight "600"
   :text-transform "uppercase"
   :letter-spacing "0.5px"})

(def expand-button-style
  {:background "transparent"
   :border "1px solid var(--border-color-dark, #d1d5db)"
   :border-radius "6px"
   :padding "8px 16px"
   :cursor "pointer"
   :font-size "13px"
   :color "var(--text-secondary, #6b7280)"
   :transition "all 0.2s"
   :display "flex"
   :align-items "center"
   :gap "8px"})

(def expanded-details-style
  {:margin-top "15px"
   :padding-top "15px"
   :border-top "1px solid var(--border-color, #e5e7eb)"})

(def description-style
  {:font-size "14px"
   :color "var(--text-secondary, #6b7280)"
   :line-height "1.6"
   :margin "0 0 15px 0"
   :white-space "pre-wrap"})

(def time-info-style
  {:display "grid"
   :grid-template-columns "repeat(auto-fit, minmax(200px, 1fr))"
   :gap "10px"
   :margin-top "15px"
   :padding "12px"
   :background "var(--card-secondary-bg, #f9fafb)"
   :border "1px solid var(--border-color, #e5e7eb)"
   :border-radius "6px"})

(def time-item-style
  {:font-size "13px"
   :color "var(--text-secondary, #6b7280)"})

(def time-label-style
  {:font-weight "600"
   :color "var(--text-primary, #1f2937)"})

(def no-alerts-style
  {:text-align "center"
   :padding "40px"
   :color "var(--text-tertiary, #9ca3af)"})

(def no-alerts-icon-style
  {:font-size "64px"
   :margin-bottom "15px"})

(def no-alerts-text-style
  {:font-size "18px"
   :font-weight "500"
   :color "var(--text-primary, #1f2937)"
   :margin "0 0 8px 0"})

(def no-alerts-subtext-style
  {:font-size "14px"
   :color "var(--text-tertiary, #9ca3af)"})

(def loading-style
  {:text-align "center"
   :padding "40px"
   :color "var(--text-tertiary, #9ca3af)"})

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
   :margin-bottom "20px"})

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
   {:name "Chicago, IL" :lat 41.8781 :lon -87.6298}
   {:name "Oklahoma City, OK" :lat 35.4676 :lon -97.5164} ; Often has alerts
   {:name "Kansas City, MO" :lat 39.0997 :lon -94.5786}]) ; Tornado alley

;; ============================================================================
;; Components
;; ============================================================================

(defn loading-spinner []
  [:div {:style loading-style}
   [:div {:style {:font-size "40px" :margin-bottom "10px"}} "⏳"]
   [:div "Loading weather alerts..."]])

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
     "Check Alerts"]]])

(defn quick-city-buttons [{:keys [cities on-select]}]
  [:div {:style quick-buttons-style}
   (for [city cities]
     ^{:key (:name city)}
     [:button
      {:style quick-button-style
       :on-click #(on-select city)
       :on-mouse-over #(set! (.. % -target -style -background) "#4b5563")
       :on-mouse-out #(set! (.. % -target -style -background) "#374151")}
      (:name city)])])

(defn alert-card-component [{:keys [alert]}]
  (let [expanded? (r/atom false)
        props (:properties alert)
        {:keys [event headline description severity urgency certainty
                onset expires effective]} props
        icon (get-alert-icon event)
        severity-color (get-severity-color severity)]

    (fn [{:keys [alert]}]
      [:div {:style (alert-card-style severity-color)}

       ;; Alert Header
       [:div {:style alert-header-style}
        [:div {:style alert-title-section-style}
         [:h3 {:style alert-event-style}
          [:span icon]
          [:span event]]
         [:p {:style alert-headline-style} headline]

         ;; Badges
         [:div {:style badges-container-style}
          [:span {:style (badge-style severity-color)} severity]
          [:span {:style (badge-style (get-urgency-badge-color urgency))} urgency]
          [:span {:style (badge-style (get-certainty-badge-color certainty))} certainty]]]]

       ;; Expand/Collapse Button
       [:button
        {:style expand-button-style
         :on-click #(swap! expanded? not)
         :on-mouse-over #(set! (.. % -target -style -background) "#374151")
         :on-mouse-out #(set! (.. % -target -style -background) "transparent")}
        [:span (if @expanded? "▼" "▶")]
        [:span (if @expanded? "Hide Details" "View Details")]]

       ;; Expanded Details
       (when @expanded?
         [:div {:style expanded-details-style}
          [:div {:style description-style} description]

          [:div {:style time-info-style}
           (when onset
             [:div {:style time-item-style}
              [:span {:style time-label-style} "Effective: "]
              (format-alert-time onset)])

           (when expires
             [:div {:style time-item-style}
              [:span {:style time-label-style} "Expires: "]
              (format-alert-time expires)])]])])))

(defn no-alerts-display [{:keys [location-name]}]
  [:div {:style no-alerts-style}
   [:div {:style no-alerts-icon-style} "✅"]
   [:div {:style no-alerts-text-style} "No Active Weather Alerts"]
   [:div {:style no-alerts-subtext-style}
    "There are currently no weather alerts for " location-name]])

(defn alerts-display [{:keys [alerts-data location-name]}]
  (let [features (:features alerts-data)
        alert-count (count features)]
    [:div
     [:div {:style location-display-style}
      "⚠️ Weather Alerts for " location-name
      (when (> alert-count 0)
        (str " (" alert-count " active)"))]

     (if (empty? features)
       [no-alerts-display {:location-name location-name}]
       [:div {:style alerts-container-style}
        (for [alert features]
          ^{:key (:id alert)}
          [alert-card-component {:alert alert}])])]))

;; ============================================================================
;; Main Component
;; ============================================================================

(defn main-component []
  (let [lat (r/atom "35.2271")
        lon (r/atom "-80.8431")
        location-name (r/atom "Charlotte, NC")
        alerts-data (r/atom nil)
        loading? (r/atom false)
        error (r/atom nil)

        fetch-alerts
        (fn []
          (reset! loading? true)
          (reset! error nil)
          (reset! alerts-data nil)

          (get-alerts-for-point
           {:lat @lat
            :lon @lon
            :on-success
            (fn [data]
              (reset! alerts-data data)
              (reset! loading? false))
            :on-error
            (fn [err]
              (reset! error (str "Failed to fetch alerts: " err))
              (reset! loading? false))}))

        select-city
        (fn [city]
          (reset! lat (str (:lat city)))
          (reset! lon (str (:lon city)))
          (reset! location-name (:name city))
          (fetch-alerts))]

    (fn []
      [:div {:style container-style}
       [:div {:style header-style}
        [:h1 {:style title-style} "⚠️ Weather Alerts"]
        [:p {:style subtitle-style}
         "Active weather warnings and advisories with severity-based styling"]]

       [:div {:style card-style}
        [location-input
         {:lat @lat
          :lon @lon
          :on-lat-change #(reset! lat %)
          :on-lon-change #(reset! lon %)
          :on-fetch fetch-alerts}]

        [quick-city-buttons
         {:cities quick-cities
          :on-select select-city}]]

       (cond
         @loading? [loading-spinner]
         @error [error-message {:message @error}]
         @alerts-data [:div {:style card-style}
                       [alerts-display
                        {:alerts-data @alerts-data
                         :location-name @location-name}]])])))

;; ============================================================================
;; Mount
;; ============================================================================

(defn ^:export init []
  (when-let [el (js/document.getElementById "weather-alerts-demo")]
    (rdom/render [main-component] el)))

(init)
