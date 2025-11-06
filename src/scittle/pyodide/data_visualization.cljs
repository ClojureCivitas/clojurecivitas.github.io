(ns scittle.pyodide.data-visualization
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [scittle.pyodide.pyodide-bridge :as pyodide]))

;; ============================================================================
;; Matplotlib Data Visualization Demo
;; ============================================================================

(defonce code (r/atom "# Line Chart - Monthly Sales Data
import numpy as np

months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun']
sales = [45, 52, 48, 65, 70, 68]
expenses = [30, 35, 33, 40, 42, 41]

plt.figure(figsize=(10, 6))
plt.plot(months, sales, marker='o', linewidth=2, label='Sales', color='#10B981')
plt.plot(months, expenses, marker='s', linewidth=2, label='Expenses', color='#EF4444')
plt.xlabel('Month', fontsize=12)
plt.ylabel('Amount ($K)', fontsize=12)
plt.title('Monthly Sales vs Expenses', fontsize=14, fontweight='bold')
plt.legend()
plt.grid(True, alpha=0.3)"))

(defonce output-image (r/atom nil))

(defonce output-text (r/atom "Click 'Generate Chart' to visualize your data"))

(defonce running? (r/atom false))

(defonce matplotlib-loaded? (r/atom false))

;; Chart examples
(def chart-examples
  {:line-chart "# Line Chart - Monthly Sales Data
import numpy as np

months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun']
sales = [45, 52, 48, 65, 70, 68]
expenses = [30, 35, 33, 40, 42, 41]

plt.figure(figsize=(10, 6))
plt.plot(months, sales, marker='o', linewidth=2, label='Sales', color='#10B981')
plt.plot(months, expenses, marker='s', linewidth=2, label='Expenses', color='#EF4444')
plt.xlabel('Month', fontsize=12)
plt.ylabel('Amount ($K)', fontsize=12)
plt.title('Monthly Sales vs Expenses', fontsize=14, fontweight='bold')
plt.legend()
plt.grid(True, alpha=0.3)"

   :bar-chart "# Bar Chart - Product Sales Comparison
categories = ['Product A', 'Product B', 'Product C', 'Product D', 'Product E']
values = [75, 62, 88, 45, 92]
colors = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6']

plt.figure(figsize=(10, 6))
plt.bar(categories, values, color=colors, alpha=0.8)
plt.xlabel('Products', fontsize=12)
plt.ylabel('Sales (Units)', fontsize=12)
plt.title('Product Sales Comparison', fontsize=14, fontweight='bold')
plt.ylim(0, 100)
plt.grid(axis='y', alpha=0.3)"

   :scatter-plot "# Scatter Plot - Height vs Weight
import numpy as np

np.random.seed(42)
heights = np.random.normal(170, 10, 50)
weights = heights * 0.6 + np.random.normal(0, 5, 50)

plt.figure(figsize=(10, 6))
plt.scatter(heights, weights, alpha=0.6, c='#8B5CF6', s=100, edgecolors='white', linewidth=1.5)
plt.xlabel('Height (cm)', fontsize=12)
plt.ylabel('Weight (kg)', fontsize=12)
plt.title('Height vs Weight Distribution', fontsize=14, fontweight='bold')
plt.grid(True, alpha=0.3)"

   :pie-chart "# Pie Chart - Market Share
labels = ['Company A', 'Company B', 'Company C', 'Company D', 'Others']
sizes = [35, 25, 20, 12, 8]
colors = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#9CA3AF']
explode = (0.1, 0, 0, 0, 0)

plt.figure(figsize=(10, 6))
plt.pie(sizes, explode=explode, labels=labels, colors=colors,
        autopct='%1.1f%%', shadow=True, startangle=90)
plt.title('Market Share Distribution', fontsize=14, fontweight='bold')
plt.axis('equal')"})

(defn load-matplotlib!
  []
  (js/console.log "🔍 Starting matplotlib load check...")
  (js/console.log "Pyodide ready?" (pyodide/ready?))
  (when (pyodide/ready?)
    (js/console.log "✓ Pyodide is ready, loading matplotlib...")
    (reset! output-text "Loading matplotlib...")
    (-> (pyodide/load-packages "matplotlib")
        (.then (fn [result]
                 (js/console.log "✓ Matplotlib loaded successfully!" result)
                 (reset! matplotlib-loaded? true)
                 (reset! output-text "Matplotlib ready! Click 'Generate Chart' to visualize")))
        (.catch (fn [err]
                  (js/console.error "✗ Failed to load matplotlib:" err)
                  (reset! output-text (str "Error loading matplotlib: " (.-message err))))))))

(defn generate-chart!
  []
  (when @matplotlib-loaded?
    (reset! running? true)
    (reset! output-text "Generating chart...")
    (reset! output-image nil)
    (-> (pyodide/run-python-with-plot @code)
        (.then (fn [base64-img]
                 (reset! output-image base64-img)
                 (reset! output-text "")
                 (reset! running? false)))
        (.catch (fn [err]
                  (reset! output-text (str "Error:\n" (.-message err)))
                  (reset! output-image nil)
                  (reset! running? false))))))

(defn load-example!
  [example-key]
  (reset! code (get chart-examples example-key))
  (reset! output-image nil)
  (reset! output-text "Example loaded. Click 'Generate Chart' to visualize"))

(defn main-component
  []
  [:div {:style {:max-width "1024px"
                 :margin "0 auto"
                 :padding "24px"}}
   [:h2 {:style {:font-size "28px"
                 :font-weight "bold"
                 :margin-bottom "8px"
                 :color "#111827"}}
    "📊 Data Visualization with Matplotlib"]
   [:p {:style {:color "#6B7280"
                :margin-bottom "24px"
                :font-size "16px"}}
    "Create beautiful charts and visualizations using Python's matplotlib library, running entirely in your browser!"]
   ;; Info banner
   [:div {:style {:background-color (if @matplotlib-loaded? "#D1FAE5" "#DBEAFE")
                  :border-left (str "4px solid " (if @matplotlib-loaded? "#10B981" "#3B82F6"))
                  :padding "16px"
                  :margin-bottom "24px"
                  :border-radius "4px"}}
    [:p {:style {:font-size "14px"
                 :color (if @matplotlib-loaded? "#065F46" "#1E40AF")
                 :margin "0"}}
     (if @matplotlib-loaded?
       "✓ Matplotlib is loaded and ready!"
       "Loading matplotlib... This may take a moment on first load.")]]
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
               :on-click #(load-example! :line-chart)}
      "📈 Line Chart"]
     [:button {:style {:padding "8px 16px"
                       :background-color "#F3F4F6"
                       :color "#374151"
                       :border "1px solid #D1D5DB"
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :bar-chart)}
      "📊 Bar Chart"]
     [:button {:style {:padding "8px 16px"
                       :background-color "#F3F4F6"
                       :color "#374151"
                       :border "1px solid #D1D5DB"
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :scatter-plot)}
      "⚫ Scatter Plot"]
     [:button {:style {:padding "8px 16px"
                       :background-color "#F3F4F6"
                       :color "#374151"
                       :border "1px solid #D1D5DB"
                       :border-radius "6px"
                       :font-size "14px"
                       :cursor "pointer"
                       :font-weight "500"}
               :on-click #(load-example! :pie-chart)}
      "🥧 Pie Chart"]]]
   ;; Code editor
   [:div {:style {:margin-bottom "16px"}}
    [:label {:style {:display "block"
                     :font-size "14px"
                     :font-weight "600"
                     :margin-bottom "8px"
                     :color "#374151"}}
     "Python Code (Matplotlib):"]
    [:textarea {:value @code
                :on-change #(reset! code (.. % -target -value))
                :rows 15
                :style {:width "100%"
                        :padding "12px"
                        :font-family "monospace"
                        :font-size "14px"
                        :border "2px solid #D1D5DB"
                        :border-radius "6px"
                        :resize "vertical"
                        :background-color "#FFFFFF"
                        :color "#111827"}}]]
   ;; Generate button
   [:button {:style {:width "100%"
                     :padding "14px 24px"
                     :background-color (cond
                                         @running? "#9CA3AF"
                                         (not @matplotlib-loaded?) "#9CA3AF"
                                         :else "#3B82F6")
                     :color "#FFFFFF"
                     :border "none"
                     :border-radius "6px"
                     :font-weight "600"
                     :font-size "16px"
                     :cursor (if (or @running? (not @matplotlib-loaded?))
                               "not-allowed"
                               "pointer")
                     :margin-bottom "24px"}
             :on-click generate-chart!
             :disabled (or @running? (not @matplotlib-loaded?))}
    (cond
      @running? "Generating Chart..."
      (not @matplotlib-loaded?) "Loading Matplotlib..."
      :else "📊 Generate Chart")]
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
     "Visualization Output:"]
    (if @output-image
      [:div {:style {:text-align "center"}}
       [:img {:src (str "data:image/png;base64," @output-image)
              :alt "Generated Chart"
              :style {:max-width "100%"
                      :height "auto"
                      :border-radius "4px"
                      :box-shadow "0 2px 8px rgba(0,0,0,0.1)"}}]]
      [:div {:style {:padding "40px"
                     :text-align "center"
                     :color "#6B7280"
                     :font-size "14px"}}
       [:p {:style {:margin "0"}} @output-text]])]])

;; Initialize matplotlib on mount - with retry logic
(defonce init-done?
  (do
    (js/console.log "🚀 Data visualization component mounted")
    (letfn [(try-load []
              (if (pyodide/ready?)
                (do
                  (js/console.log "✓ Pyodide ready, loading matplotlib...")
                  (load-matplotlib!))
                (do
                  (js/console.log "⏳ Pyodide not ready, retrying in 500ms...")
                  (js/setTimeout try-load 500))))]
      (js/setTimeout try-load 100))
    true))

(rdom/render [main-component] (js/document.getElementById "data-visualization-demo"))
