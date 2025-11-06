(ns scittle.pyodide.time-series-demo
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [scittle.pyodide.pyodide-bridge :as pyodide]))

;; ============================================================================
;; Time Series Analysis Demo
;; Moving averages, trends, seasonality analysis
;; ============================================================================

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
import matplotlib.pyplot as plt
import numpy as np
import io
import base64
import json

# Load data
df = pd.read_csv(io.StringIO(csv_content))
df['Date'] = pd.to_datetime(df['Date'])
df = df.sort_values('Date')

plots = []

# 1. Price with Moving Averages
plt.figure(figsize=(12, 6))
plt.plot(df['Date'], df['Price'], label='Price', linewidth=2, color='steelblue', marker='o', markersize=4)

# Calculate moving averages
df['MA_5'] = df['Price'].rolling(window=5).mean()
df['MA_10'] = df['Price'].rolling(window=10).mean()

plt.plot(df['Date'], df['MA_5'], label='5-Day MA', linewidth=2, linestyle='--', color='orange')
plt.plot(df['Date'], df['MA_10'], label='10-Day MA', linewidth=2, linestyle='--', color='red')

plt.title('Stock Price with Moving Averages', fontsize=16, fontweight='bold')
plt.xlabel('Date', fontsize=12)
plt.ylabel('Price ($)', fontsize=12)
plt.legend()
plt.grid(True, alpha=0.3)
plt.xticks(rotation=45)
plt.tight_layout()

buf = io.BytesIO()
plt.savefig(buf, format='png', dpi=100, facecolor='white')
buf.seek(0)
plots.append({
    'title': 'Price with Moving Averages',
    'data': base64.b64encode(buf.read()).decode()
})
plt.close()

# 2. Daily Returns
df['Returns'] = df['Price'].pct_change() * 100
plt.figure(figsize=(12, 6))
colors = ['green' if x > 0 else 'red' for x in df['Returns']]
plt.bar(range(len(df)), df['Returns'], color=colors, alpha=0.7)
plt.axhline(y=0, color='black', linestyle='-', linewidth=0.5)
plt.title('Daily Returns (%)', fontsize=16, fontweight='bold')
plt.xlabel('Day', fontsize=12)
plt.ylabel('Return (%)', fontsize=12)
plt.grid(True, alpha=0.3, axis='y')
plt.tight_layout()

buf = io.BytesIO()
plt.savefig(buf, format='png', dpi=100, facecolor='white')
buf.seek(0)
plots.append({
    'title': 'Daily Returns',
    'data': base64.b64encode(buf.read()).decode()
})
plt.close()

# 3. Volume Analysis
plt.figure(figsize=(12, 6))
plt.bar(df['Date'], df['Volume'], color='coral', alpha=0.7, edgecolor='black')
plt.title('Trading Volume', fontsize=16, fontweight='bold')
plt.xlabel('Date', fontsize=12)
plt.ylabel('Volume', fontsize=12)
plt.xticks(rotation=45)
plt.grid(True, alpha=0.3, axis='y')
plt.tight_layout()

buf = io.BytesIO()
plt.savefig(buf, format='png', dpi=100, facecolor='white')
buf.seek(0)
plots.append({
    'title': 'Trading Volume',
    'data': base64.b64encode(buf.read()).decode()
})
plt.close()

# 4. Volatility (Rolling Std)
df['Volatility'] = df['Returns'].rolling(window=5).std()
plt.figure(figsize=(12, 6))
plt.plot(df['Date'], df['Volatility'], linewidth=2, color='purple', marker='o', markersize=4)
plt.title('5-Day Rolling Volatility', fontsize=16, fontweight='bold')
plt.xlabel('Date', fontsize=12)
plt.ylabel('Volatility (Std Dev of Returns)', fontsize=12)
plt.grid(True, alpha=0.3)
plt.xticks(rotation=45)
plt.tight_layout()

buf = io.BytesIO()
plt.savefig(buf, format='png', dpi=100, facecolor='white')
buf.seek(0)
plots.append({
    'title': 'Rolling Volatility',
    'data': base64.b64encode(buf.read()).decode()
})
plt.close()

# Statistics
stats = {
    'mean_price': float(df['Price'].mean()),
    'std_price': float(df['Price'].std()),
    'min_price': float(df['Price'].min()),
    'max_price': float(df['Price'].max()),
    'mean_volume': float(df['Volume'].mean()),
    'total_return': float(((df['Price'].iloc[-1] / df['Price'].iloc[0]) - 1) * 100),
    'avg_daily_return': float(df['Returns'].mean()),
    'volatility': float(df['Returns'].std())
}

json.dumps({'success': True, 'plots': plots, 'stats': stats})
"
                      ;; Sensor analysis
                      "
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import io
import base64
import json

# Load data
df = pd.read_csv(io.StringIO(csv_content))
df['Timestamp'] = pd.to_datetime(df['Timestamp'])
df = df.sort_values('Timestamp')
df['Hour'] = df['Timestamp'].dt.hour

plots = []

# 1. Temperature Trend
plt.figure(figsize=(12, 6))
plt.plot(df['Hour'], df['Temperature'], marker='o', linewidth=2,
         markersize=6, color='orangered', label='Temperature')

# Add moving average
df['Temp_MA'] = df['Temperature'].rolling(window=3).mean()
plt.plot(df['Hour'], df['Temp_MA'], linewidth=2, linestyle='--',
         color='darkred', label='3-Hour MA')

plt.title('Temperature Over 24 Hours', fontsize=16, fontweight='bold')
plt.xlabel('Hour of Day', fontsize=12)
plt.ylabel('Temperature (°C)', fontsize=12)
plt.legend()
plt.grid(True, alpha=0.3)
plt.tight_layout()

buf = io.BytesIO()
plt.savefig(buf, format='png', dpi=100, facecolor='white')
buf.seek(0)
plots.append({
    'title': 'Temperature Trend',
    'data': base64.b64encode(buf.read()).decode()
})
plt.close()

# 2. Humidity Trend
plt.figure(figsize=(12, 6))
plt.plot(df['Hour'], df['Humidity'], marker='s', linewidth=2,
         markersize=6, color='dodgerblue', label='Humidity')

df['Humid_MA'] = df['Humidity'].rolling(window=3).mean()
plt.plot(df['Hour'], df['Humid_MA'], linewidth=2, linestyle='--',
         color='darkblue', label='3-Hour MA')

plt.title('Humidity Over 24 Hours', fontsize=16, fontweight='bold')
plt.xlabel('Hour of Day', fontsize=12)
plt.ylabel('Humidity (%)', fontsize=12)
plt.legend()
plt.grid(True, alpha=0.3)
plt.tight_layout()

buf = io.BytesIO()
plt.savefig(buf, format='png', dpi=100, facecolor='white')
buf.seek(0)
plots.append({
    'title': 'Humidity Trend',
    'data': base64.b64encode(buf.read()).decode()
})
plt.close()

# 3. Temperature vs Humidity (Scatter)
plt.figure(figsize=(10, 8))
scatter = plt.scatter(df['Temperature'], df['Humidity'],
                     c=df['Hour'], cmap='viridis',
                     s=100, alpha=0.6, edgecolors='black')
plt.colorbar(scatter, label='Hour of Day')

# Add trend line
z = np.polyfit(df['Temperature'], df['Humidity'], 1)
p = np.poly1d(z)
plt.plot(df['Temperature'], p(df['Temperature']),
         'r--', linewidth=2, label=f'Trend: y={z[0]:.2f}x+{z[1]:.2f}')

plt.title('Temperature vs Humidity Relationship', fontsize=16, fontweight='bold')
plt.xlabel('Temperature (°C)', fontsize=12)
plt.ylabel('Humidity (%)', fontsize=12)
plt.legend()
plt.grid(True, alpha=0.3)
plt.tight_layout()

buf = io.BytesIO()
plt.savefig(buf, format='png', dpi=100, facecolor='white')
buf.seek(0)
plots.append({
    'title': 'Temperature vs Humidity',
    'data': base64.b64encode(buf.read()).decode()
})
plt.close()

# 4. Daily Pattern (Heatmap style)
fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 8))

# Temperature pattern
ax1.bar(df['Hour'], df['Temperature'], color='orangered', alpha=0.7, edgecolor='black')
ax1.set_title('Temperature Pattern', fontsize=14, fontweight='bold')
ax1.set_xlabel('Hour of Day', fontsize=11)
ax1.set_ylabel('Temperature (°C)', fontsize=11)
ax1.grid(True, alpha=0.3, axis='y')

# Humidity pattern
ax2.bar(df['Hour'], df['Humidity'], color='dodgerblue', alpha=0.7, edgecolor='black')
ax2.set_title('Humidity Pattern', fontsize=14, fontweight='bold')
ax2.set_xlabel('Hour of Day', fontsize=11)
ax2.set_ylabel('Humidity (%)', fontsize=11)
ax2.grid(True, alpha=0.3, axis='y')

plt.tight_layout()

buf = io.BytesIO()
plt.savefig(buf, format='png', dpi=100, facecolor='white')
buf.seek(0)
plots.append({
    'title': 'Daily Patterns',
    'data': base64.b64encode(buf.read()).decode()
})
plt.close()

# Statistics
stats = {
    'mean_temp': float(df['Temperature'].mean()),
    'std_temp': float(df['Temperature'].std()),
    'min_temp': float(df['Temperature'].min()),
    'max_temp': float(df['Temperature'].max()),
    'mean_humid': float(df['Humidity'].mean()),
    'std_humid': float(df['Humidity'].std()),
    'correlation': float(df['Temperature'].corr(df['Humidity']))
}

json.dumps({'success': True, 'plots': plots, 'stats': stats})
")]
    (-> (pyodide/set-global "csv_content" csv-content)
        (.then (fn []
                 (pyodide/load-packages ["pandas" "matplotlib" "numpy"])))
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
  [:div {:style {:background-color "#FFFFFF"
                 :border "1px solid #E5E7EB"
                 :border-radius "8px"
                 :padding "20px"
                 :margin-bottom "20px"}}
   [:h3 {:style {:font-size "18px"
                 :font-weight "bold"
                 :margin-bottom "12px"
                 :color "#111827"}}
    "📊 Select Dataset"]
   [:div {:style {:display "grid"
                  :grid-template-columns "1fr 1fr"
                  :gap "12px"}}
    [:button {:style {:padding "16px"
                      :background-color (if (= @current-dataset :stock) "#3B82F6" "#FFFFFF")
                      :color (if (= @current-dataset :stock) "#FFFFFF" "#374151")
                      :border (str "2px solid " (if (= @current-dataset :stock) "#3B82F6" "#D1D5DB"))
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
                      :background-color (if (= @current-dataset :sensor) "#10B981" "#FFFFFF")
                      :color (if (= @current-dataset :sensor) "#FFFFFF" "#374151")
                      :border (str "2px solid " (if (= @current-dataset :sensor) "#10B981" "#D1D5DB"))
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
       [:div {:style {:background-color "#FFFFFF"
                      :border "1px solid #E5E7EB"
                      :border-radius "8px"
                      :padding "20px"
                      :margin-bottom "20px"}}
        [:h3 {:style {:font-size "18px"
                      :font-weight "bold"
                      :margin-bottom "12px"
                      :color "#111827"}}
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
                     :border "4px solid #E5E7EB"
                     :border-top-color "#3B82F6"
                     :border-radius "50%"
                     :animation "spin 1s linear infinite"}}]
      [:p {:style {:font-size "16px"
                   :color "#6B7280"
                   :font-weight "500"}}
       "Analyzing time series data..."]
      [:style "@keyframes spin { to { transform: rotate(360deg); } }"]]]))

(defn error-display
  []
  (when @error-msg
    [:div {:style {:background-color "#FEE2E2"
                   :border "1px solid #FCA5A5"
                   :border-radius "8px"
                   :padding "16px"
                   :margin-bottom "20px"}}
     [:h4 {:style {:font-size "16px"
                   :font-weight "600"
                   :color "#991B1B"
                   :margin-bottom "8px"}}
      "⚠️ Error"]
     [:p {:style {:font-size "14px"
                  :color "#DC2626"
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
                 :-webkit-text-fill-color "transparent"}}
    "📈 Time Series Analysis"]
   [:p {:style {:color "#6B7280"
                :margin-bottom "24px"
                :font-size "16px"}}
    "Analyze temporal patterns with moving averages, trends, and volatility"]
   [:div {:style {:background-color "#EFF6FF"
                  :border-left "4px solid #3B82F6"
                  :padding "16px"
                  :margin-bottom "24px"}}
    [:p {:style {:font-size "14px"
                 :color "#1E40AF"
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
                    :border-top "1px solid #E5E7EB"
                    :color "#6B7280"
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
    (-> (pyodide/init!)
        (.then (fn []
                 (js/console.log "✓ Pyodide ready, loading packages...")
                 (pyodide/load-packages ["pandas" "matplotlib" "numpy"])))
        (.then (fn []
                 (js/console.log "✓ Packages loaded, running initial analysis...")
                 (reset! ready? true)
                 (analyze-time-series! sample-stock-data :stock))))
    true))

(rdom/render [main-component] (js/document.getElementById "time-series-demo"))
