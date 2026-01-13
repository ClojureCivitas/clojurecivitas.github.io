(ns scittle.pyodide.time-series-demo
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [scittle.pyodide.pyodide-bridge :as pyodide]))

;; ============================================================================
;; Time Series Analysis Demo
;; Moving averages, trends, seasonality analysis
;; ============================================================================

;; ============================================================================
;; Dark Mode Support
;; ============================================================================

(defn dark-mode?
  "Check if dark mode is currently active by looking for 'quarto-dark' class on body"
  []
  (when-let [body (js/document.querySelector "body")]
    (.contains (.-classList body) "quarto-dark")))

(defn get-color
  "Get appropriate color based on dark mode state"
  [light-color dark-color]
  (if (dark-mode?) dark-color light-color))

;; Sample time series data
(def sample-stock-data
  "Date,Price,Volume
2024-01-01,100.50,1500000
2024-01-02,102.30,1600000
2024-01-03,101.80,1400000
2024-01-04,103.50,1700000
2024-01-05,105.20,1800000
2024-01-08,104.80,1600000
2024-01-09,106.50,1900000
2024-01-10,108.00,2000000
2024-01-11,107.20,1800000
2024-01-12,109.50,2100000
2024-01-15,108.80,1900000
2024-01-16,110.20,2200000
2024-01-17,112.00,2300000
2024-01-18,111.50,2100000
2024-01-19,113.20,2400000
2024-01-22,112.80,2200000
2024-01-23,114.50,2500000
2024-01-24,116.00,2600000
2024-01-25,115.30,2400000
2024-01-26,117.50,2700000
2024-01-29,116.80,2500000
2024-01-30,118.20,2800000")

(def sample-sensor-data
  "Timestamp,Temperature,Humidity
2024-01-01 00:00,20.5,65
2024-01-01 01:00,20.2,66
2024-01-01 02:00,19.8,67
2024-01-01 03:00,19.5,68
2024-01-01 04:00,19.2,69
2024-01-01 05:00,19.0,70
2024-01-01 06:00,19.5,68
2024-01-01 07:00,20.5,65
2024-01-01 08:00,21.8,62
2024-01-01 09:00,23.2,58
2024-01-01 10:00,24.5,55
2024-01-01 11:00,25.8,52
2024-01-01 12:00,26.5,50
2024-01-01 13:00,27.0,48
2024-01-01 14:00,26.8,49
2024-01-01 15:00,26.2,51
2024-01-01 16:00,25.5,53
2024-01-01 17:00,24.8,55
2024-01-01 18:00,23.5,58
2024-01-01 19:00,22.5,61
2024-01-01 20:00,21.8,63
2024-01-01 21:00,21.2,64
2024-01-01 22:00,20.8,65
2024-01-01 23:00,20.5,66")

;; State
(defonce current-dataset (r/atom :stock))

(defonce plots (r/atom []))

(defonce loading? (r/atom false))

(defonce error-msg (r/atom nil))

(defonce ready? (r/atom false))

;; ============================================================================
;; Analysis Functions
;; ============================================================================

(defn analyze-time-series!
  "Analyze time series data with moving averages and trends"
  [csv-content dataset-type]
  (reset! loading? true)
  (reset! error-msg nil)
  (reset! plots [])

  (let [python-code (if (= dataset-type :stock)
                      ;; Stock analysis
                      "
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import io
import base64
import json

try:
    # Load the CSV data
    df = pd.read_csv(io.StringIO(csv_content))
    df['Date'] = pd.to_datetime(df['Date'])
    df = df.sort_values('Date')
    
    # Calculate moving averages
    df['MA_5'] = df['Price'].rolling(window=5, min_periods=1).mean()
    df['MA_10'] = df['Price'].rolling(window=10, min_periods=1).mean()
    
    # Calculate returns and volatility
    df['Returns'] = df['Price'].pct_change() * 100
    df['Volatility'] = df['Returns'].rolling(window=5, min_periods=1).std()
    
    plots = []
    
    # Plot 1: Price with Moving Averages
    plt.figure(figsize=(10, 5))
    plt.plot(df['Date'], df['Price'], label='Price', linewidth=2, color='#3B82F6')
    plt.plot(df['Date'], df['MA_5'], label='5-Day MA', linestyle='--', color='#10B981')
    plt.plot(df['Date'], df['MA_10'], label='10-Day MA', linestyle='--', color='#F59E0B')
    plt.title('Stock Price with Moving Averages', fontsize=14, fontweight='bold')
    plt.xlabel('Date')
    plt.ylabel('Price ($)')
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.xticks(rotation=45)
    plt.tight_layout()
    buf = io.BytesIO()
    plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
    buf.seek(0)
    plots.append({
        'title': '📈 Stock Price with Moving Averages',
        'data': base64.b64encode(buf.read()).decode('utf-8')
    })
    plt.close()
    
    # Plot 2: Daily Returns
    plt.figure(figsize=(10, 4))
    plt.bar(df['Date'], df['Returns'], color=['#EF4444' if x < 0 else '#10B981' for x in df['Returns']], alpha=0.6)
    plt.axhline(y=0, color='black', linestyle='-', linewidth=0.8)
    plt.title('Daily Returns (%)', fontsize=14, fontweight='bold')
    plt.xlabel('Date')
    plt.ylabel('Returns (%)')
    plt.grid(True, alpha=0.3, axis='y')
    plt.xticks(rotation=45)
    plt.tight_layout()
    buf = io.BytesIO()
    plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
    buf.seek(0)
    plots.append({
        'title': '📊 Daily Returns',
        'data': base64.b64encode(buf.read()).decode('utf-8')
    })
    plt.close()
    
    # Plot 3: Volume
    plt.figure(figsize=(10, 4))
    plt.bar(df['Date'], df['Volume'], color='#8B5CF6', alpha=0.6)
    plt.title('Trading Volume', fontsize=14, fontweight='bold')
    plt.xlabel('Date')
    plt.ylabel('Volume')
    plt.grid(True, alpha=0.3, axis='y')
    plt.xticks(rotation=45)
    plt.tight_layout()
    buf = io.BytesIO()
    plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
    buf.seek(0)
    plots.append({
        'title': '📊 Trading Volume',
        'data': base64.b64encode(buf.read()).decode('utf-8')
    })
    plt.close()
    
    # Plot 4: Volatility
    plt.figure(figsize=(10, 4))
    plt.plot(df['Date'], df['Volatility'], color='#F59E0B', linewidth=2)
    plt.fill_between(df['Date'], df['Volatility'], alpha=0.3, color='#F59E0B')
    plt.title('5-Day Rolling Volatility', fontsize=14, fontweight='bold')
    plt.xlabel('Date')
    plt.ylabel('Volatility (%)')
    plt.grid(True, alpha=0.3)
    plt.xticks(rotation=45)
    plt.tight_layout()
    buf = io.BytesIO()
    plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
    buf.seek(0)
    plots.append({
        'title': '📉 Rolling Volatility',
        'data': base64.b64encode(buf.read()).decode('utf-8')
    })
    plt.close()
    
    result = {
        'success': True,
        'plots': plots
    }
    output = json.dumps(result)
    
except Exception as e:
    import traceback
    result = {
        'success': False,
        'error': str(e),
        'traceback': traceback.format_exc()
    }
    output = json.dumps(result)

output
"
                      ;; Sensor analysis
                      "
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import io
import base64
import json

try:
    # Load the CSV data
    df = pd.read_csv(io.StringIO(csv_content))
    df['Timestamp'] = pd.to_datetime(df['Timestamp'])
    df = df.sort_values('Timestamp')
    
    # Calculate moving averages
    df['Temp_MA_3'] = df['Temperature'].rolling(window=3, min_periods=1).mean()
    df['Humidity_MA_3'] = df['Humidity'].rolling(window=3, min_periods=1).mean()
    
    # Calculate correlation
    temp_humidity_corr = df['Temperature'].corr(df['Humidity'])
    
    plots = []
    
    # Plot 1: Temperature with Moving Average
    plt.figure(figsize=(10, 4))
    plt.plot(df['Timestamp'], df['Temperature'], label='Temperature', linewidth=2, color='#EF4444')
    plt.plot(df['Timestamp'], df['Temp_MA_3'], label='3-Hour MA', linestyle='--', color='#F59E0B')
    plt.title('Temperature Over 24 Hours', fontsize=14, fontweight='bold')
    plt.xlabel('Time')
    plt.ylabel('Temperature (°C)')
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.xticks(rotation=45)
    plt.tight_layout()
    buf = io.BytesIO()
    plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
    buf.seek(0)
    plots.append({
        'title': '🌡️ Temperature Patterns',
        'data': base64.b64encode(buf.read()).decode('utf-8')
    })
    plt.close()
    
    # Plot 2: Humidity with Moving Average
    plt.figure(figsize=(10, 4))
    plt.plot(df['Timestamp'], df['Humidity'], label='Humidity', linewidth=2, color='#3B82F6')
    plt.plot(df['Timestamp'], df['Humidity_MA_3'], label='3-Hour MA', linestyle='--', color='#10B981')
    plt.title('Humidity Over 24 Hours', fontsize=14, fontweight='bold')
    plt.xlabel('Time')
    plt.ylabel('Humidity (%)')
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.xticks(rotation=45)
    plt.tight_layout()
    buf = io.BytesIO()
    plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
    buf.seek(0)
    plots.append({
        'title': '💧 Humidity Patterns',
        'data': base64.b64encode(buf.read()).decode('utf-8')
    })
    plt.close()
    
    # Plot 3: Temperature vs Humidity Correlation
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 4))
    
    # Scatter plot
    ax1.scatter(df['Temperature'], df['Humidity'], color='#8B5CF6', alpha=0.6, s=50)
    ax1.set_xlabel('Temperature (°C)')
    ax1.set_ylabel('Humidity (%)')
    ax1.set_title(f'Temperature vs Humidity (r={temp_humidity_corr:.2f})', fontweight='bold')
    ax1.grid(True, alpha=0.3)
    
    # Dual axis time series
    ax2_humidity = ax2.twinx()
    line1 = ax2.plot(df['Timestamp'], df['Temperature'], color='#EF4444', linewidth=2, label='Temperature')
    line2 = ax2_humidity.plot(df['Timestamp'], df['Humidity'], color='#3B82F6', linewidth=2, label='Humidity')
    ax2.set_xlabel('Time')
    ax2.set_ylabel('Temperature (°C)', color='#EF4444')
    ax2_humidity.set_ylabel('Humidity (%)', color='#3B82F6')
    ax2.tick_params(axis='y', labelcolor='#EF4444')
    ax2_humidity.tick_params(axis='y', labelcolor='#3B82F6')
    ax2.set_title('Inverse Correlation Pattern', fontweight='bold')
    plt.setp(ax2.xaxis.get_majorticklabels(), rotation=45)
    
    plt.tight_layout()
    buf = io.BytesIO()
    plt.savefig(buf, format='png', dpi=100, bbox_inches='tight')
    buf.seek(0)
    plots.append({
        'title': '🔗 Temperature-Humidity Correlation',
        'data': base64.b64encode(buf.read()).decode('utf-8')
    })
    plt.close()
    
    result = {
        'success': True,
        'plots': plots
    }
    output = json.dumps(result)
    
except Exception as e:
    import traceback
    result = {
        'success': False,
        'error': str(e),
        'traceback': traceback.format_exc()
    }
    output = json.dumps(result)

output
")]
    ;; Set global first (synchronous)
    (pyodide/set-global "csv_content" csv-content)
    ;; Then run the async chain
    (-> (pyodide/load-packages ["pandas" "matplotlib" "numpy"])
        (.then (fn []
                 (pyodide/run-python python-code)))
        (.then (fn [result]
                 (let [parsed (js->clj (js/JSON.parse result) :keywordize-keys true)]
                   (if (:success parsed)
                     (do
                       (reset! plots (:plots parsed))
                       (reset! loading? false)
                       (js/console.log "✓ Analysis complete"))
                     (do
                       (reset! error-msg "Analysis failed")
                       (reset! loading? false))))))
        (.catch (fn [err]
                  (reset! error-msg (str "Error: " (.-message err)))
                  (reset! loading? false)
                  (js/console.error "✗ Analysis error:" err))))))

;; ============================================================================
;; UI Components
;; ============================================================================

(defn dataset-selector
  []
  [:div {:style {:background-color (get-color "#FFFFFF" "#1F2937")
                 :border (str "1px solid " (get-color "#E5E7EB" "#4B5563"))
                 :border-radius "8px"
                 :padding "20px"
                 :margin-bottom "20px"}}
   [:h3 {:style {:font-size "18px"
                 :font-weight "bold"
                 :margin-bottom "12px"
                 :color (get-color "#111827" "#F3F4F6")}}
    "📊 Select Dataset"]
   [:div {:style {:display "grid"
                  :grid-template-columns "1fr 1fr"
                  :gap "12px"}}
    [:button {:style {:padding "16px"
                      :background-color (if (= @current-dataset :stock)
                                          (get-color "#3B82F6" "#2563EB")
                                          (get-color "#FFFFFF" "#1F2937"))
                      :color (if (= @current-dataset :stock)
                               "#FFFFFF"
                               (get-color "#374151" "#D1D5DB"))
                      :border (str "2px solid " (if (= @current-dataset :stock)
                                                  (get-color "#3B82F6" "#2563EB")
                                                  (get-color "#D1D5DB" "#4B5563")))
                      :border-radius "8px"
                      :font-weight "600"
                      :cursor "pointer"
                      :font-size "14px"
                      :text-align "left"}
              :on-click (fn []
                          (reset! current-dataset :stock)
                          (when @ready?
                            (analyze-time-series! sample-stock-data :stock)))}
     [:div "📈 Stock Data"]
     [:div {:style {:font-size "12px"
                    :opacity "0.8"
                    :margin-top "4px"}}
      "Price, volume, moving averages"]]

    [:button {:style {:padding "16px"
                      :background-color (if (= @current-dataset :sensor)
                                          (get-color "#10B981" "#059669")
                                          (get-color "#FFFFFF" "#1F2937"))
                      :color (if (= @current-dataset :sensor)
                               "#FFFFFF"
                               (get-color "#374151" "#D1D5DB"))
                      :border (str "2px solid " (if (= @current-dataset :sensor)
                                                  (get-color "#10B981" "#059669")
                                                  (get-color "#D1D5DB" "#4B5563")))
                      :border-radius "8px"
                      :font-weight "600"
                      :cursor "pointer"
                      :font-size "14px"
                      :text-align "left"}
              :on-click (fn []
                          (reset! current-dataset :sensor)
                          (when @ready?
                            (analyze-time-series! sample-sensor-data :sensor)))}
     [:div "🌡️ Sensor Data"]
     [:div {:style {:font-size "12px"
                    :opacity "0.8"
                    :margin-top "4px"}}
      "Temperature, humidity patterns"]]]])

(defn plot-display
  []
  (when (seq @plots)
    [:div {:style {:space-y "24px"}}
     (for [[idx plot] (map-indexed vector @plots)]
       ^{:key idx}
       [:div {:style {:background-color (get-color "#FFFFFF" "#1F2937")
                      :border (str "1px solid " (get-color "#E5E7EB" "#4B5563"))
                      :border-radius "8px"
                      :padding "20px"
                      :margin-bottom "20px"}}
        [:h3 {:style {:font-size "18px"
                      :font-weight "bold"
                      :margin-bottom "12px"
                      :color (get-color "#111827" "#F3F4F6")}}
         (:title plot)]
        [:img {:src (str "data:image/png;base64," (:data plot))
               :style {:max-width "100%"
                       :height "auto"
                       :border-radius "4px"
                       :box-shadow "0 1px 3px rgba(0,0,0,0.1)"}
               :alt (:title plot)}]])]))

(defn loading-spinner
  []
  (when @loading?
    [:div {:style {:display "flex"
                   :justify-content "center"
                   :align-items "center"
                   :padding "60px"}}
     [:div {:style {:display "flex"
                    :flex-direction "column"
                    :align-items "center"
                    :gap "16px"}}
      [:div {:style {:width "48px"
                     :height "48px"
                     :border (str "4px solid " (get-color "#E5E7EB" "#4B5563"))
                     :border-top-color (get-color "#3B82F6" "#60A5FA")
                     :border-radius "50%"
                     :animation "spin 1s linear infinite"}}]
      [:p {:style {:font-size "16px"
                   :color (get-color "#6B7280" "#9CA3AF")
                   :font-weight "500"}}
       "Analyzing time series data..."]
      [:style "@keyframes spin { to { transform: rotate(360deg); } }"]]]))

(defn error-display
  []
  (when @error-msg
    [:div {:style {:background-color (get-color "#FEE2E2" "#7F1D1D")
                   :border (str "1px solid " (get-color "#FCA5A5" "#EF4444"))
                   :border-radius "8px"
                   :padding "16px"
                   :margin-bottom "20px"}}
     [:h4 {:style {:font-size "16px"
                   :font-weight "600"
                   :color (get-color "#991B1B" "#FCA5A5")
                   :margin-bottom "8px"}}
      "⚠️ Error"]
     [:p {:style {:font-size "14px"
                  :color (get-color "#DC2626" "#FEE2E2")
                  :margin "0"}}
      @error-msg]]))

(defn main-component
  []
  [:div {:style {:max-width "1200px"
                 :margin "0 auto"
                 :padding "24px"}}
   [:h1 {:style {:font-size "32px"
                 :font-weight "bold"
                 :margin-bottom "8px"
                 :background "linear-gradient(to right, #3B82F6, #10B981)"
                 :-webkit-background-clip "text"
                 :-webkit-text-fill-color "transparent"
                 :background-clip "text"}}
    "📈 Time Series Analysis"]
   [:p {:style {:color (get-color "#6B7280" "#9CA3AF")
                :margin-bottom "24px"
                :font-size "16px"}}
    "Analyze temporal patterns with moving averages, trends, and volatility"]
   [:div {:style {:background-color (get-color "#EFF6FF" "#1E3A8A")
                  :border-left (str "4px solid " (get-color "#3B82F6" "#60A5FA"))
                  :padding "16px"
                  :margin-bottom "24px"}}
    [:p {:style {:font-size "14px"
                 :color (get-color "#1E40AF" "#BFDBFE")
                 :margin "0"}}
     "💡 Explore how time series data reveals patterns, trends, and relationships. "
     "This demo uses Pandas for data manipulation and Matplotlib for visualization."]]
   (when @ready?
     [dataset-selector])
   [error-display]
   [loading-spinner]
   [plot-display]
   ;; Footer
   (when (seq @plots)
     [:div {:style {:margin-top "32px"
                    :padding-top "24px"
                    :border-top (str "1px solid " (get-color "#E5E7EB" "#4B5563"))
                    :color (get-color "#6B7280" "#9CA3AF")
                    :font-size "14px"}}
      [:p "✨ " [:strong "Time Series Techniques:"]]
      [:ul {:style {:margin "8px 0"
                    :padding-left "24px"}}
       [:li "Moving averages for trend smoothing"]
       [:li "Daily returns and volatility analysis"]
       [:li "Correlation between variables"]
       [:li "Pattern detection and visualization"]]
      [:p {:style {:margin-top "16px"}}
       "🚀 All analysis runs in your browser using Pyodide!"]])])

;; ============================================================================
;; Initialize
;; ============================================================================

(defonce init-done?
  (do
    (js/console.log "🚀 Time Series Analysis demo mounted")
    ;; Wait for Pyodide to be ready, then load packages
    (letfn [(try-load []
              (if (pyodide/ready?)
                (do
                  (js/console.log "✓ Pyodide ready, loading packages...")
                  (-> (pyodide/load-packages ["pandas" "matplotlib" "numpy"])
                      (.then (fn []
                               (js/console.log "✓ Packages loaded, running initial analysis...")
                               (reset! ready? true)
                               (analyze-time-series! sample-stock-data :stock)))))
                (do
                  (js/console.log "⏳ Pyodide not ready, retrying in 500ms...")
                  (js/setTimeout try-load 500))))]
      (js/setTimeout try-load 100))
    true))

(rdom/render [main-component] (js/document.getElementById "time-series-analysis"))
