(ns scittle.pyodide.pandas-viz-demo
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [scittle.pyodide.pyodide-bridge :as pyodide]))

;; ============================================================================
;; Combined Pandas + Matplotlib Demo - Complete Data Science Workflows
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

(defonce code (r/atom "# Sales Trend Analysis - Complete Workflow
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

# Sample sales data over 12 months
data = {
    'Month': ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
              'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
    'Sales': [45000, 52000, 48000, 65000, 70000, 68000,
              75000, 82000, 79000, 88000, 95000, 102000],
    'Expenses': [30000, 35000, 33000, 40000, 42000, 41000,
                 45000, 48000, 46000, 52000, 55000, 58000]
}

df = pd.DataFrame(data)
df['Profit'] = df['Sales'] - df['Expenses']
df['Margin %'] = ((df['Profit'] / df['Sales']) * 100).round(1)

# Summary Statistics
print('<h3>📊 Monthly Performance Summary</h3>')
summary = df[['Sales', 'Expenses', 'Profit']].describe().round(0)
print(summary.to_html(border=0, classes='dataframe'))

# Trend Visualization
plt.figure(figsize=(12, 5))

plt.subplot(1, 2, 1)
plt.plot(df['Month'], df['Sales'], marker='o', linewidth=2,
         label='Sales', color='#10B981', markersize=8)
plt.plot(df['Month'], df['Expenses'], marker='s', linewidth=2,
         label='Expenses', color='#EF4444', markersize=8)
plt.plot(df['Month'], df['Profit'], marker='^', linewidth=2,
         label='Profit', color='#3B82F6', markersize=8)
plt.title('Sales Trend Analysis', fontsize=14, fontweight='bold')
plt.xlabel('Month', fontsize=11)
plt.ylabel('Amount ($)', fontsize=11)
plt.legend(loc='upper left')
plt.grid(True, alpha=0.3)
plt.xticks(rotation=45)

plt.subplot(1, 2, 2)
plt.bar(df['Month'], df['Profit'], color='#10B981', alpha=0.7, edgecolor='white', linewidth=1.5)
plt.title('Monthly Profit', fontsize=14, fontweight='bold')
plt.xlabel('Month', fontsize=11)
plt.ylabel('Profit ($)', fontsize=11)
plt.grid(axis='y', alpha=0.3)
plt.xticks(rotation=45)

plt.tight_layout()"))

(defonce output-html (r/atom nil))
(defonce output-image (r/atom nil))
(defonce output-text (r/atom "Click 'Run Workflow' to see the complete analysis"))
(defonce running? (r/atom false))
(defonce libs-loaded? (r/atom false))

;; Workflow examples combining pandas and matplotlib
(def workflow-examples
  {:sales-trend "# Sales Trend Analysis - Complete Workflow
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

# Sample sales data over 12 months
data = {
    'Month': ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
              'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
    'Sales': [45000, 52000, 48000, 65000, 70000, 68000,
              75000, 82000, 79000, 88000, 95000, 102000],
    'Expenses': [30000, 35000, 33000, 40000, 42000, 41000,
                 45000, 48000, 46000, 52000, 55000, 58000]
}

df = pd.DataFrame(data)
df['Profit'] = df['Sales'] - df['Expenses']
df['Margin %'] = ((df['Profit'] / df['Sales']) * 100).round(1)

# Summary Statistics
print('<h3>📊 Monthly Performance Summary</h3>')
summary = df[['Sales', 'Expenses', 'Profit']].describe().round(0)
print(summary.to_html(border=0, classes='dataframe'))

# Trend Visualization
plt.figure(figsize=(12, 5))

plt.subplot(1, 2, 1)
plt.plot(df['Month'], df['Sales'], marker='o', linewidth=2,
         label='Sales', color='#10B981', markersize=8)
plt.plot(df['Month'], df['Expenses'], marker='s', linewidth=2,
         label='Expenses', color='#EF4444', markersize=8)
plt.plot(df['Month'], df['Profit'], marker='^', linewidth=2,
         label='Profit', color='#3B82F6', markersize=8)
plt.title('Sales Trend Analysis', fontsize=14, fontweight='bold')
plt.xlabel('Month', fontsize=11)
plt.ylabel('Amount ($)', fontsize=11)
plt.legend(loc='upper left')
plt.grid(True, alpha=0.3)
plt.xticks(rotation=45)

plt.subplot(1, 2, 2)
plt.bar(df['Month'], df['Profit'], color='#10B981', alpha=0.7, edgecolor='white', linewidth=1.5)
plt.title('Monthly Profit', fontsize=14, fontweight='bold')
plt.xlabel('Month', fontsize=11)
plt.ylabel('Profit ($)', fontsize=11)
plt.grid(axis='y', alpha=0.3)
plt.xticks(rotation=45)

plt.tight_layout()"

   :product-analysis "# Product Performance Analysis
import pandas as pd
import matplotlib.pyplot as plt

# Product sales data by category
data = {
    'Product': ['Laptop', 'Phone', 'Tablet', 'Watch', 'Headphones',
                'Laptop', 'Phone', 'Tablet', 'Watch', 'Headphones',
                'Laptop', 'Phone', 'Tablet', 'Watch', 'Headphones'],
    'Quarter': ['Q1', 'Q1', 'Q1', 'Q1', 'Q1',
                'Q2', 'Q2', 'Q2', 'Q2', 'Q2',
                'Q3', 'Q3', 'Q3', 'Q3', 'Q3'],
    'Units': [150, 280, 120, 95, 210,
              180, 320, 140, 110, 245,
              210, 350, 155, 125, 280],
    'Revenue': [225000, 224000, 84000, 28500, 31500,
                270000, 256000, 98000, 33000, 36750,
                315000, 280000, 108500, 37500, 42000]
}

df = pd.DataFrame(data)
df['Avg_Price'] = (df['Revenue'] / df['Units']).round(0)

# Analysis by Product
print('<h3>📦 Product Performance by Quarter</h3>')
product_summary = df.groupby('Product').agg({
    'Units': 'sum',
    'Revenue': 'sum',
    'Avg_Price': 'mean'
}).round(0)
product_summary['Revenue'] = product_summary['Revenue'].astype(int)
print(product_summary.to_html(border=0, classes='dataframe'))

# Visualizations
plt.figure(figsize=(12, 5))

plt.subplot(1, 2, 1)
products = product_summary.index
units = product_summary['Units']
colors = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6']
plt.bar(products, units, color=colors, alpha=0.8, edgecolor='white', linewidth=2)
plt.title('Total Units Sold by Product', fontsize=14, fontweight='bold')
plt.xlabel('Product', fontsize=11)
plt.ylabel('Units Sold', fontsize=11)
plt.xticks(rotation=45, ha='right')
plt.grid(axis='y', alpha=0.3)

plt.subplot(1, 2, 2)
revenue = product_summary['Revenue']
plt.barh(products, revenue, color=colors, alpha=0.8, edgecolor='white', linewidth=2)
plt.title('Revenue by Product', fontsize=14, fontweight='bold')
plt.xlabel('Revenue ($)', fontsize=11)
plt.ylabel('Product', fontsize=11)
plt.grid(axis='x', alpha=0.3)

plt.tight_layout()"

   :regional-breakdown "# Regional Sales Breakdown with Multiple Charts
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# Regional sales data
data = {
    'Region': ['North', 'South', 'East', 'West'] * 3,
    'Quarter': ['Q1']*4 + ['Q2']*4 + ['Q3']*4,
    'Sales': [450000, 380000, 520000, 490000,
              480000, 410000, 560000, 520000,
              510000, 435000, 595000, 555000],
    'Customers': [1200, 950, 1400, 1300,
                  1280, 1020, 1480, 1350,
                  1350, 1080, 1560, 1420]
}

df = pd.DataFrame(data)
df['Avg_Sale'] = (df['Sales'] / df['Customers']).round(2)

# Pivot table for analysis
print('<h3>🗺️ Regional Performance Matrix</h3>')
pivot = df.pivot_table(values='Sales', index='Region', columns='Quarter', aggfunc='sum')
pivot['Total'] = pivot.sum(axis=1)
print(pivot.to_html(border=0, classes='dataframe'))

# Multiple visualizations
fig = plt.figure(figsize=(12, 8))

# Regional comparison
ax1 = plt.subplot(2, 2, 1)
regions = df.groupby('Region')['Sales'].sum()
colors = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444']
plt.bar(regions.index, regions.values, color=colors, alpha=0.8, edgecolor='white', linewidth=2)
plt.title('Total Sales by Region', fontsize=12, fontweight='bold')
plt.ylabel('Sales ($)', fontsize=10)
plt.xticks(rotation=45)
plt.grid(axis='y', alpha=0.3)

# Quarterly trend
ax2 = plt.subplot(2, 2, 2)
for region in ['North', 'South', 'East', 'West']:
    region_data = df[df['Region'] == region]
    plt.plot(region_data['Quarter'], region_data['Sales'],
             marker='o', linewidth=2, label=region, markersize=8)
plt.title('Quarterly Sales Trend', fontsize=12, fontweight='bold')
plt.ylabel('Sales ($)', fontsize=10)
plt.legend()
plt.grid(True, alpha=0.3)

# Customer growth
ax3 = plt.subplot(2, 2, 3)
customer_trend = df.groupby('Quarter')['Customers'].sum()
plt.plot(customer_trend.index, customer_trend.values,
         marker='o', linewidth=3, color='#8B5CF6', markersize=10)
plt.title('Total Customers by Quarter', fontsize=12, fontweight='bold')
plt.ylabel('Customers', fontsize=10)
plt.grid(True, alpha=0.3)

# Average sale value
ax4 = plt.subplot(2, 2, 4)
avg_sales = df.groupby('Region')['Avg_Sale'].mean()
plt.barh(avg_sales.index, avg_sales.values, color=colors, alpha=0.8, edgecolor='white', linewidth=2)
plt.title('Avg Sale per Customer', fontsize=12, fontweight='bold')
plt.xlabel('Average ($)', fontsize=10)
plt.grid(axis='x', alpha=0.3)

plt.tight_layout()"

   :time-series "# Time Series Analysis with Moving Averages
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# Daily sales data
np.random.seed(42)
dates = pd.date_range('2024-01-01', periods=90, freq='D')
trend = np.linspace(1000, 1500, 90)
seasonal = 200 * np.sin(np.linspace(0, 4*np.pi, 90))
noise = np.random.normal(0, 50, 90)
sales = trend + seasonal + noise

df = pd.DataFrame({
    'Date': dates,
    'Sales': sales
})

# Calculate moving averages
df['MA_7'] = df['Sales'].rolling(window=7).mean()
df['MA_30'] = df['Sales'].rolling(window=30).mean()

# Summary statistics
print('<h3>📅 Time Series Summary (90 Days)</h3>')
stats = pd.DataFrame({
    'Metric': ['Total Sales', 'Average Daily', 'Max Daily', 'Min Daily', 'Std Dev'],
    'Value': [
        f\"${df['Sales'].sum():,.0f}\",
        f\"${df['Sales'].mean():,.0f}\",
        f\"${df['Sales'].max():,.0f}\",
        f\"${df['Sales'].min():,.0f}\",
        f\"${df['Sales'].std():,.0f}\"
    ]
})
print(stats.to_html(index=False, border=0, classes='dataframe'))

# Visualization
plt.figure(figsize=(12, 6))

plt.subplot(2, 1, 1)
plt.plot(df['Date'], df['Sales'], label='Daily Sales',
         alpha=0.5, color='#9CA3AF', linewidth=1)
plt.plot(df['Date'], df['MA_7'], label='7-Day MA',
         color='#3B82F6', linewidth=2)
plt.plot(df['Date'], df['MA_30'], label='30-Day MA',
         color='#EF4444', linewidth=2)
plt.title('Sales with Moving Averages', fontsize=14, fontweight='bold')
plt.ylabel('Sales ($)', fontsize=11)
plt.legend()
plt.grid(True, alpha=0.3)

plt.subplot(2, 1, 2)
monthly = df.set_index('Date').resample('M')['Sales'].sum()
plt.bar(range(len(monthly)), monthly.values,
        color='#10B981', alpha=0.8, edgecolor='white', linewidth=2)
plt.title('Monthly Sales Totals', fontsize=14, fontweight='bold')
plt.xlabel('Month', fontsize=11)
plt.ylabel('Total Sales ($)', fontsize=11)
plt.xticks(range(len(monthly)), ['Jan', 'Feb', 'Mar'])
plt.grid(axis='y', alpha=0.3)

plt.tight_layout()"})

(defn load-libraries!
  []
  (js/console.log "🔍 Starting pandas + matplotlib load check...")
  (when (pyodide/ready?)
    (js/console.log "✓ Pyodide is ready, loading libraries...")
    (reset! output-text "Loading pandas and matplotlib (this may take 30-60 seconds)...")
    (-> (pyodide/load-packages "matplotlib")
        (.then (fn [_]
                 (js/console.log "✓ Matplotlib loaded")
                 (pyodide/load-packages "pandas")))
        (.then (fn [_]
                 (js/console.log "✓ Pandas loaded")
                 (reset! libs-loaded? true)
                 (reset! output-text "Libraries ready! Click 'Run Workflow' to see analysis")))
        (.catch (fn [err]
                  (js/console.error "✗ Failed to load libraries:" err)
                  (reset! output-text (str "Error loading libraries: " (.-message err))))))))

(defn run-workflow!
  []
  (when @libs-loaded?
    (reset! running? true)
    (reset! output-text "Running complete workflow...")
    (reset! output-html nil)
    (reset! output-image nil)
    ;; First, capture the HTML output (tables)
    (-> (pyodide/run-python-with-output @code)
        (.then (fn [html-result]
                 (reset! output-html (str html-result))
                 ;; Then, capture the plot
                 (pyodide/run-python-with-plot @code)))
        (.then (fn [plot-result]
                 (reset! output-image plot-result)
                 (reset! output-text "")
                 (reset! running? false)))
        (.catch (fn [err]
                  (reset! output-text (str "Error:\n" (.-message err)))
                  (reset! output-html nil)
                  (reset! output-image nil)
                  (reset! running? false))))))

(defn load-example!
  [example-key]
  (reset! code (get workflow-examples example-key))
  (reset! output-html nil)
  (reset! output-image nil)
  (reset! output-text "Example loaded. Click 'Run Workflow' to see the complete analysis"))

(defn main-component
  []
  [:div {:style {:max-width "1200px"
                 :margin "0 auto"
                 :padding "24px"}}
   [:h2 {:style {:font-size "28px"
                 :font-weight "bold"
                 :margin-bottom "8px"
                 :color (get-color "#111827" "#F3F4F6")}}
    "🎯 Complete Data Science Workflows"]
   [:p {:style {:color (get-color "#6B7280" "#9CA3AF")
                :margin-bottom "24px"
                :font-size "16px"}}
    "Combine Pandas data analysis with Matplotlib visualizations for complete end-to-end workflows!"]
   ;; Info banner
   [:div {:style {:background-color (if @libs-loaded?
                                      (get-color "#D1FAE5" "#064E3B")
                                      (get-color "#DBEAFE" "#1E3A8A"))
                  :border-left (str "4px solid " (if @libs-loaded?
                                                   (get-color "#10B981" "#34D399")
                                                   (get-color "#3B82F6" "#60A5FA")))
                  :padding "16px"
                  :margin-bottom "24px"
                  :border-radius "4px"}}
    [:p {:style {:font-size "14px"
                 :color (if @libs-loaded?
                          (get-color "#065F46" "#6EE7B7")
                          (get-color "#1E40AF" "#BFDBFE"))
                 :margin "0"}}
     (if @libs-loaded?
       "✓ Pandas & Matplotlib are loaded and ready!"
       "Loading pandas and matplotlib... This may take 30-60 seconds on first load.")]]
   ;; Workflow examples
   [:div {:style {:margin-bottom "16px"}}
    [:label {:style {:display "block"
                     :font-size "14px"
                     :font-weight "600"
                     :margin-bottom "8px"
                     :color (get-color "#374151" "#D1D5DB")}}
     "Complete Workflow Examples:"]
    [:div {:style {:display "flex"
                   :gap "8px"
                   :flex-wrap "wrap"}}
     [:button {:style {:padding "8px 16px"
                       :background-color (get-color "#F3F4F6" "#374151")
                       :color (get-color "#374151" "#D1D5DB")
                       :border (str "1px solid " (get-color "#D1D5DB" "#4B5563"))
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :sales-trend)}
      "📈 Sales Trend"]
     [:button {:style {:padding "8px 16px"
                       :background-color (get-color "#F3F4F6" "#374151")
                       :color (get-color "#374151" "#D1D5DB")
                       :border (str "1px solid " (get-color "#D1D5DB" "#4B5563"))
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :product-analysis)}
      "📦 Product Analysis"]
     [:button {:style {:padding "8px 16px"
                       :background-color (get-color "#F3F4F6" "#374151")
                       :color (get-color "#374151" "#D1D5DB")
                       :border (str "1px solid " (get-color "#D1D5DB" "#4B5563"))
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :regional-breakdown)}
      "🗺️ Regional Analysis"]
     [:button {:style {:padding "8px 16px"
                       :background-color (get-color "#F3F4F6" "#374151")
                       :color (get-color "#374151" "#D1D5DB")
                       :border (str "1px solid " (get-color "#D1D5DB" "#4B5563"))
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :time-series)}
      "📅 Time Series"]]]
   ;; Code editor
   [:div {:style {:margin-bottom "16px"}}
    [:label {:style {:display "block"
                     :font-size "14px"
                     :font-weight "600"
                     :margin-bottom "8px"
                     :color (get-color "#374151" "#D1D5DB")}}
     "Python Code (Pandas + Matplotlib):"]
    [:textarea {:value @code
                :on-change #(reset! code (.. % -target -value))
                :rows 20
                :style {:width "100%"
                        :padding "12px"
                        :font-family "monospace"
                        :font-size "13px"
                        :border (str "2px solid " (get-color "#D1D5DB" "#4B5563"))
                        :border-radius "6px"
                        :resize "vertical"
                        :background-color (get-color "#FFFFFF" "#1F2937")
                        :color (get-color "#111827" "#F3F4F6")
                        :line-height "1.5"}}]]
   ;; Run button
   [:button {:style {:width "100%"
                     :padding "14px 24px"
                     :background-color (cond
                                         @running? "#9CA3AF"
                                         (not @libs-loaded?) "#9CA3AF"
                                         :else "#8B5CF6")
                     :color "#FFFFFF"
                     :border "none"
                     :border-radius "6px"
                     :font-weight "600"
                     :font-size "16px"
                     :cursor (if (or @running? (not @libs-loaded?))
                               "not-allowed"
                               "pointer")
                     :margin-bottom "24px"}
             :on-click run-workflow!
             :disabled (or @running? (not @libs-loaded?))}
    (cond
      @running? "⏳ Running Workflow..."
      (not @libs-loaded?) "Loading Libraries..."
      :else "🎯 Run Complete Workflow")]
   ;; Output area
   [:div {:style {:background-color (get-color "#FFFFFF" "#1F2937")
                  :border (str "2px solid " (get-color "#E5E7EB" "#4B5563"))
                  :border-radius "8px"
                  :padding "20px"
                  :min-height "500px"}}
    [:label {:style {:display "block"
                     :font-size "14px"
                     :font-weight "600"
                     :margin-bottom "12px"
                     :color "#374151"}}
     "Analysis Results:"]
    (when (or @output-html @output-image)
      [:div
       ;; Tables section
       (when @output-html
         [:div {:style {:margin-bottom "24px"}}
          [:style
           (str "
.dataframe {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  font-size: 14px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
.dataframe thead tr {
  background-color: " (get-color "#F3F4F6" "#374151") ";
  text-align: left;
  font-weight: 600;
  color: " (get-color "#374151" "#E5E7EB") ";
}
.dataframe th,
.dataframe td {
  padding: 12px 15px;
  border-bottom: 1px solid " (get-color "#E5E7EB" "#4B5563") ";
  text-align: left;
  color: " (get-color "#111827" "#F3F4F6") ";
}
.dataframe tbody tr:hover {
  background-color: " (get-color "#F9FAFB" "#2D3748") ";
}
.dataframe tbody tr:last-of-type {
  border-bottom: 2px solid " (get-color "#D1D5DB" "#6B7280") ";
}
h3 {
  color: " (get-color "#374151" "#D1D5DB") ";
  font-size: 18px;
  font-weight: 600;
  margin: 24px 0 12px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid " (get-color "#E5E7EB" "#4B5563") ";
}")]
          [:div {:dangerouslySetInnerHTML {:__html @output-html}}]])
       ;; Visualization section
       (when @output-image
         [:div {:style {:margin-top "24px"
                        :padding-top "24px"
                        :border-top (str "2px solid " (get-color "#E5E7EB" "#4B5563"))}}
          [:h3 {:style {:color (get-color "#374151" "#D1D5DB")
                        :font-size "18px"
                        :font-weight "600"
                        :margin-bottom "16px"}}
           "📊 Visualizations"]
          [:div {:style {:text-align "center"
                         :background-color (get-color "#F9FAFB" "#1F2937")
                         :padding "20px"
                         :border-radius "8px"}}
           [:img {:src (str "data:image/png;base64," @output-image)
                  :alt "Data Visualization"
                  :style {:max-width "100%"
                          :height "auto"
                          :border-radius "4px"
                          :box-shadow "0 4px 12px rgba(0,0,0,0.1)"}}]]])])
    (when (and (not @output-html) (not @output-image))
      [:div {:style {:padding "60px 40px"
                     :text-align "center"
                     :color (get-color "#6B7280" "#9CA3AF")
                     :font-size "14px"}}
       [:p {:style {:margin "0"}} @output-text]])]])

;; Initialize libraries on mount - with retry logic
(defonce init-done?
  (do
    (js/console.log "🚀 Pandas + Matplotlib workflow demo mounted")
    (letfn [(try-load []
              (if (pyodide/ready?)
                (do
                  (js/console.log "✓ Pyodide ready, loading libraries...")
                  (load-libraries!))
                (do
                  (js/console.log "⏳ Pyodide not ready, retrying in 500ms...")
                  (js/setTimeout try-load 500))))]
      (js/setTimeout try-load 100))
    true))

(rdom/render [main-component] (js/document.getElementById "pandas-viz-demo"))
