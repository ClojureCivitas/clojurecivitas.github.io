(ns scittle.pyodide.pandas-demo
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [scittle.pyodide.pyodide-bridge :as pyodide]))

;; ============================================================================
;; Pandas DataFrame Analysis Demo
;; ============================================================================

(defonce code (r/atom "# Create a sample sales DataFrame
import pandas as pd
import numpy as np

# Sample data
data = {
    'Product': ['Widget A', 'Widget B', 'Widget C', 'Widget D', 'Widget E'],
    'Sales': [1200, 800, 1500, 950, 1100],
    'Profit': [450, 200, 600, 380, 420],
    'Region': ['North', 'South', 'East', 'West', 'North']
}

df = pd.DataFrame(data)

# Display as HTML table
print(df.to_html(index=False, border=0, classes='dataframe'))"))

(defonce output-html (r/atom nil))

(defonce output-text (r/atom "Click 'Run Analysis' to see results"))

(defonce running? (r/atom false))

(defonce pandas-loaded? (r/atom false))

;; Example code snippets
(def examples
  {:basic-dataframe "# Create a sample sales DataFrame
import pandas as pd
import numpy as np

# Sample data
data = {
    'Product': ['Widget A', 'Widget B', 'Widget C', 'Widget D', 'Widget E'],
    'Sales': [1200, 800, 1500, 950, 1100],
    'Profit': [450, 200, 600, 380, 420],
    'Region': ['North', 'South', 'East', 'West', 'North']
}

df = pd.DataFrame(data)

# Display as HTML table
print(df.to_html(index=False, border=0, classes='dataframe'))"

   :statistics "# Statistical Analysis
import pandas as pd
import numpy as np

# Create sample data
data = {
    'Product': ['A', 'B', 'C', 'D', 'E'],
    'Sales': [1200, 800, 1500, 950, 1100],
    'Profit': [450, 200, 600, 380, 420],
    'Margin': [37.5, 25.0, 40.0, 40.0, 38.2]
}

df = pd.DataFrame(data)

# Get descriptive statistics
stats = df[['Sales', 'Profit', 'Margin']].describe()

# Display statistics
print('<h4>Descriptive Statistics:</h4>')
print(stats.to_html(border=0, classes='dataframe'))

# Show correlations
print('<h4>Correlation Matrix:</h4>')
corr = df[['Sales', 'Profit', 'Margin']].corr()
print(corr.to_html(border=0, classes='dataframe'))"

   :filtering "# Data Filtering and Selection
import pandas as pd

# Create sample data
data = {
    'Product': ['Widget A', 'Widget B', 'Widget C', 'Widget D', 'Widget E'],
    'Sales': [1200, 800, 1500, 950, 1100],
    'Profit': [450, 200, 600, 380, 420],
    'Region': ['North', 'South', 'East', 'West', 'North']
}

df = pd.DataFrame(data)

# Filter high performers (Sales > 1000)
high_performers = df[df['Sales'] > 1000]

print('<h4>High Performers (Sales > 1000):</h4>')
print(high_performers.to_html(index=False, border=0, classes='dataframe'))

# Filter by region
north_sales = df[df['Region'] == 'North']

print('<h4>North Region Sales:</h4>')
print(north_sales.to_html(index=False, border=0, classes='dataframe'))"

   :grouping "# GroupBy Analysis
import pandas as pd

# Create sample data with multiple regions
data = {
    'Product': ['A', 'B', 'A', 'B', 'A', 'B', 'C', 'C'],
    'Region': ['North', 'North', 'South', 'South', 'East', 'East', 'North', 'South'],
    'Sales': [100, 150, 120, 180, 140, 160, 200, 175],
    'Profit': [30, 45, 36, 54, 42, 48, 60, 52]
}

df = pd.DataFrame(data)

print('<h4>Original Data:</h4>')
print(df.to_html(index=False, border=0, classes='dataframe'))

# Group by Product
print('<h4>Sales by Product:</h4>')
product_sales = df.groupby('Product')[['Sales', 'Profit']].sum()
print(product_sales.to_html(border=0, classes='dataframe'))

# Group by Region
print('<h4>Sales by Region:</h4>')
region_sales = df.groupby('Region')[['Sales', 'Profit']].sum()
print(region_sales.to_html(border=0, classes='dataframe'))"

   :transformations "# Data Transformations
import pandas as pd

# Create sample data
data = {
    'Product': ['Widget A', 'Widget B', 'Widget C', 'Widget D'],
    'Sales': [1200, 800, 1500, 950],
    'Cost': [750, 600, 900, 570]
}

df = pd.DataFrame(data)

# Add calculated columns
df['Profit'] = df['Sales'] - df['Cost']
df['Margin %'] = ((df['Profit'] / df['Sales']) * 100).round(1)
df['Profit Rank'] = df['Profit'].rank(ascending=False).astype(int)

# Sort by profit
df = df.sort_values('Profit', ascending=False)

print('<h4>Enhanced Sales Report:</h4>')
print(df.to_html(index=False, border=0, classes='dataframe'))"})

(defn load-pandas!
  []
  (js/console.log "🔍 Starting pandas load check...")
  (js/console.log "Pyodide ready?" (pyodide/ready?))
  (when (pyodide/ready?)
    (js/console.log "✓ Pyodide is ready, loading pandas...")
    (reset! output-text "Loading pandas (this may take 30-60 seconds)...")
    (-> (pyodide/load-packages "pandas")
        (.then (fn [result]
                 (js/console.log "✓ Pandas loaded successfully!" result)
                 (reset! pandas-loaded? true)
                 (reset! output-text "Pandas ready! Click 'Run Analysis' to see results")))
        (.catch (fn [err]
                  (js/console.error "✗ Failed to load pandas:" err)
                  (reset! output-text (str "Error loading pandas: " (.-message err))))))))

(defn run-analysis!
  []
  (when @pandas-loaded?
    (reset! running? true)
    (reset! output-text "Running analysis...")
    (reset! output-html nil)
    (-> (pyodide/run-python-with-output @code)
        (.then (fn [result]
                 (reset! output-html (str result))
                 (reset! output-text "")
                 (reset! running? false)))
        (.catch (fn [err]
                  (reset! output-text (str "Error:\n" (.-message err)))
                  (reset! output-html nil)
                  (reset! running? false))))))

(defn load-example!
  [example-key]
  (reset! code (get examples example-key))
  (reset! output-html nil)
  (reset! output-text "Example loaded. Click 'Run Analysis' to see results"))

(defn main-component
  []
  [:div {:style {:max-width "1024px"
                 :margin "0 auto"
                 :padding "24px"}}
   [:h2 {:style {:font-size "28px"
                 :font-weight "bold"
                 :margin-bottom "8px"
                 :color "#111827"}}
    "🐼 DataFrame Analysis with Pandas"]
   [:p {:style {:color "#6B7280"
                :margin-bottom "24px"
                :font-size "16px"}}
    "Analyze and manipulate data using Python's pandas library for powerful data science workflows in your browser!"]
   ;; Info banner
   [:div {:style {:background-color (if @pandas-loaded? "#D1FAE5" "#DBEAFE")
                  :border-left (str "4px solid " (if @pandas-loaded? "#10B981" "#3B82F6"))
                  :padding "16px"
                  :margin-bottom "24px"
                  :border-radius "4px"}}
    [:p {:style {:font-size "14px"
                 :color (if @pandas-loaded? "#065F46" "#1E40AF")
                 :margin "0"}}
     (if @pandas-loaded?
       "✓ Pandas is loaded and ready!"
       "Loading pandas... This may take 30-60 seconds on first load (pandas is ~15MB).")]]
   ;; Example buttons
   [:div {:style {:margin-bottom "16px"}}
    [:label {:style {:display "block"
                     :font-size "14px"
                     :font-weight "600"
                     :margin-bottom "8px"
                     :color "#374151"}}
     "Quick Examples:"]
    [:div {:style {:display "flex"
                   :gap "8px"
                   :flex-wrap "wrap"}}
     [:button {:style {:padding "8px 16px"
                       :background-color "#F3F4F6"
                       :color "#374151"
                       :border "1px solid #D1D5DB"
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :basic-dataframe)}
      "📋 Basic DataFrame"]
     [:button {:style {:padding "8px 16px"
                       :background-color "#F3F4F6"
                       :color "#374151"
                       :border "1px solid #D1D5DB"
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :statistics)}
      "📊 Statistics"]
     [:button {:style {:padding "8px 16px"
                       :background-color "#F3F4F6"
                       :color "#374151"
                       :border "1px solid #D1D5DB"
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :filtering)}
      "🔍 Filtering"]
     [:button {:style {:padding "8px 16px"
                       :background-color "#F3F4F6"
                       :color "#374151"
                       :border "1px solid #D1D5DB"
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :grouping)}
      "📈 Grouping"]
     [:button {:style {:padding "8px 16px"
                       :background-color "#F3F4F6"
                       :color "#374151"
                       :border "1px solid #D1D5DB"
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :transformations)}
      "🔄 Transformations"]]]
   ;; Code editor
   [:div {:style {:margin-bottom "16px"}}
    [:label {:style {:display "block"
                     :font-size "14px"
                     :font-weight "600"
                     :margin-bottom "8px"
                     :color "#374151"}}
     "Python Code (Pandas):"]
    [:textarea {:value @code
                :on-change #(reset! code (.. % -target -value))
                :rows 18
                :style {:width "100%"
                        :padding "12px"
                        :font-family "monospace"
                        :font-size "14px"
                        :border "2px solid #D1D5DB"
                        :border-radius "6px"
                        :resize "vertical"
                        :background-color "#FFFFFF"
                        :color "#111827"}}]]
   ;; Run button
   [:button {:style {:width "100%"
                     :padding "14px 24px"
                     :background-color (cond
                                         @running? "#9CA3AF"
                                         (not @pandas-loaded?) "#9CA3AF"
                                         :else "#10B981")
                     :color "#FFFFFF"
                     :border "none"
                     :border-radius "6px"
                     :font-weight "600"
                     :font-size "16px"
                     :cursor (if (or @running? (not @pandas-loaded?))
                               "not-allowed"
                               "pointer")
                     :margin-bottom "24px"}
             :on-click run-analysis!
             :disabled (or @running? (not @pandas-loaded?))}
    (cond
      @running? "Running Analysis..."
      (not @pandas-loaded?) "Loading Pandas..."
      :else "🐼 Run Analysis")]
   ;; Output area
   [:div {:style {:background-color "#FFFFFF"
                  :border "2px solid #E5E7EB"
                  :border-radius "8px"
                  :padding "20px"
                  :min-height "400px"}}
    [:label {:style {:display "block"
                     :font-size "14px"
                     :font-weight "600"
                     :margin-bottom "12px"
                     :color "#374151"}}
     "Analysis Results:"]
    (if @output-html
      [:div {:style {:overflow-x "auto"}}
       ;; Add custom CSS for pandas tables
       [:style "
.dataframe {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  font-size: 14px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
.dataframe thead tr {
  background-color: #F3F4F6;
  text-align: left;
  font-weight: 600;
  color: #374151;
}
.dataframe th,
.dataframe td {
  padding: 12px 15px;
  border-bottom: 1px solid #E5E7EB;
}
.dataframe tbody tr:hover {
  background-color: #F9FAFB;
}
.dataframe tbody tr:last-of-type {
  border-bottom: 2px solid #D1D5DB;
}
h4 {
  color: #374151;
  font-size: 16px;
  font-weight: 600;
  margin: 20px 0 10px 0;
}"]
       [:div {:dangerouslySetInnerHTML {:__html @output-html}}]]
      [:div {:style {:padding "40px"
                     :text-align "center"
                     :color "#6B7280"
                     :font-size "14px"}}
       [:p {:style {:margin "0"}} @output-text]])]])

;; Initialize pandas on mount - with retry logic
(defonce init-done?
  (do
    (js/console.log "🚀 Pandas demo component mounted")
    (letfn [(try-load []
              (if (pyodide/ready?)
                (do
                  (js/console.log "✓ Pyodide ready, loading pandas...")
                  (load-pandas!))
                (do
                  (js/console.log "⏳ Pyodide not ready, retrying in 500ms...")
                  (js/setTimeout try-load 500))))]
      (js/setTimeout try-load 100))
    true))

(rdom/render [main-component] (js/document.getElementById "pandas-demo"))
