(ns scittle.pyodide.interactive-data-analysis
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [scittle.pyodide.pyodide-bridge :as pyodide]))

;; ============================================================================
;; Interactive Data Analysis Demo
;; Upload CSV, analyze with Pandas, visualize results
;; ============================================================================

;; Sample CSV data for quick testing
(def sample-sales-csv
  "Date,Product,Region,Sales,Profit
2024-01-01,Widget A,North,1200,450
2024-01-02,Widget B,South,800,200
2024-01-03,Widget C,East,1500,600
2024-01-04,Widget A,West,950,380
2024-01-05,Widget B,North,1100,420
2024-01-06,Widget C,South,1350,540
2024-01-07,Widget A,East,1150,460
2024-01-08,Widget B,West,900,270
2024-01-09,Widget C,North,1450,580
2024-01-10,Widget A,South,1050,420")

;; State
(defonce current-csv (r/atom ""))

(defonce df-info (r/atom nil))

(defonce df-preview (r/atom nil))

(defonce df-stats (r/atom nil))

(defonce loading? (r/atom false))

(defonce error-msg (r/atom nil))

(defonce pandas-ready? (r/atom false))

;; ============================================================================
;; Data Processing Functions
;; ============================================================================

(defn load-pandas!
  "Load pandas package"
  []
  (reset! loading? true)
  (reset! error-msg nil)
  (-> (pyodide/load-packages "pandas")
      (.then (fn []
               (reset! pandas-ready? true)
               (reset! loading? false)
               (js/console.log "✓ Pandas loaded")))
      (.catch (fn [err]
                (reset! error-msg (str "Failed to load pandas: " (.-message err)))
                (reset! loading? false)))))

(defn analyze-csv!
  "Analyze the uploaded CSV data"
  [csv-content]
  (reset! loading? true)
  (reset! error-msg nil)
  (-> (pyodide/load-csv-to-dataframe csv-content :df-name "df")
      (.then (fn [result]
               (if (:success result)
                 ;; Load all analysis data in parallel
                 (js/Promise.all
                  (clj->js [(pyodide/get-dataframe-info "df")
                            (pyodide/get-dataframe-preview "df" :n-rows 10)
                            (pyodide/get-dataframe-statistics "df")]))
                 (do
                   (reset! loading? false)
                   (reset! error-msg (:error result))
                   (js/Promise.reject (:error result))))))
      (.then (fn [results]
               (let [info (aget results 0)
                     preview (aget results 1)
                     stats (aget results 2)]
                 (reset! df-info info)
                 (reset! df-preview preview)
                 (reset! df-stats stats)
                 (reset! loading? false)
                 (js/console.log "✓ Analysis complete"))))
      (.catch (fn [err]
                (reset! error-msg (str "Analysis failed: " err))
                (reset! loading? false)
                (js/console.error "✗ Analysis error:" err)))))

(defn download-csv!
  "Export and download the DataFrame as CSV"
  []
  (-> (pyodide/export-dataframe-to-csv "df" :include-index? false)
      (.then (fn [result]
               (when (:success result)
                 (let [csv-data (:csv result)
                       blob (js/Blob. (clj->js [csv-data])
                                      #js {:type "text/csv"})
                       url (js/URL.createObjectURL blob)
                       link (.createElement js/document "a")]
                   (set! (.-href link) url)
                   (set! (.-download link) (str "dataframe-export-"
                                                (.toISOString (js/Date.))
                                                ".csv"))
                   (.appendChild (.-body js/document) link)
                   (.click link)
                   (.removeChild (.-body js/document) link)
                   (js/URL.revokeObjectURL url)))))
      (.catch (fn [err]
                (js/console.error "Export failed:" err)))))

;; ============================================================================
;; UI Components
;; ============================================================================

(defn status-indicator
  []
  (let [status (pyodide/status)
        loading @loading?
        badge-config (cond
                       loading {:color "#3B82F6" :text "Processing..."}
                       (= status :error) {:color "#EF4444" :text "Error"}
                       (and (= status :ready) @pandas-ready?) {:color "#10B981" :text "Ready"}
                       (= status :ready) {:color "#F59E0B" :text "Loading Pandas..."}
                       (= status :loading) {:color "#3B82F6" :text "Loading Pyodide..."}
                       :else {:color "#6B7280" :text "Not Started"})]
    [:div {:style {:display "flex"
                   :align-items "center"
                   :gap "12px"
                   :padding "12px 16px"
                   :background-color "#F9FAFB"
                   :border-radius "8px"
                   :margin-bottom "20px"}}
     [:div {:style {:display "flex"
                    :align-items "center"
                    :gap "8px"}}
      [:div {:style {:width "10px"
                     :height "10px"
                     :border-radius "50%"
                     :background-color (:color badge-config)}}]
      [:span {:style {:font-size "14px"
                      :font-weight "600"
                      :color "#374151"}}
       "Status: " (:text badge-config)]]]))

(defn file-upload-section
  []
  [:div {:style {:background-color "#FFFFFF"
                 :border "2px dashed #D1D5DB"
                 :border-radius "8px"
                 :padding "24px"
                 :margin-bottom "20px"
                 :text-align "center"}}
   [:h3 {:style {:font-size "18px"
                 :font-weight "bold"
                 :margin-bottom "12px"
                 :color "#111827"}}
    "📤 Upload CSV Data"]
   [:p {:style {:color "#6B7280"
                :margin-bottom "16px"
                :font-size "14px"}}
    "Upload a CSV file or use the sample data below"]

   ;; File input
   [:input {:type "file"
            :accept ".csv"
            :style {:margin-bottom "16px"}
            :on-change (fn [e]
                         (let [file (-> e .-target .-files (aget 0))]
                           (when file
                             (let [reader (js/FileReader.)]
                               (set! (.-onload reader)
                                     (fn [e]
                                       (let [content (-> e .-target .-result)]
                                         (reset! current-csv content)
                                         (when @pandas-ready?
                                           (analyze-csv! content)))))
                               (.readAsText reader file)))))}]
   ;; Sample data button
   [:div {:style {:margin-top "12px"}}
    [:button {:style {:padding "10px 20px"
                      :background-color "#8B5CF6"
                      :color "#FFFFFF"
                      :border "none"
                      :border-radius "6px"
                      :font-weight "600"
                      :cursor "pointer"
                      :font-size "14px"}
              :on-click (fn []
                          (reset! current-csv sample-sales-csv)
                          (when @pandas-ready?
                            (analyze-csv! sample-sales-csv)))
              :disabled (not @pandas-ready?)}
     "📊 Use Sample Sales Data"]]])

(defn dataframe-info-panel
  []
  (when-let [info @df-info]
    (when (:success info)
      (let [[rows cols] (:shape info)
            memory (:memory_usage info)]
        [:div {:style {:background-color "#EFF6FF"
                       :border "1px solid #BFDBFE"
                       :border-radius "8px"
                       :padding "20px"
                       :margin-bottom "20px"}}
         [:h3 {:style {:font-size "18px"
                       :font-weight "bold"
                       :margin-bottom "16px"
                       :color "#1E40AF"}}
          "📊 DataFrame Information"]
         ;; Stats grid
         [:div {:style {:display "grid"
                        :grid-template-columns "repeat(3, 1fr)"
                        :gap "12px"
                        :margin-bottom "16px"}}
          [:div {:style {:background-color "#FFFFFF"
                         :padding "12px"
                         :border-radius "6px"
                         :text-align "center"}}
           [:div {:style {:font-size "12px"
                          :color "#6B7280"
                          :margin-bottom "4px"}}
            "Rows"]
           [:div {:style {:font-size "24px"
                          :font-weight "bold"
                          :color "#3B82F6"}}
            rows]]
          [:div {:style {:background-color "#FFFFFF"
                         :padding "12px"
                         :border-radius "6px"
                         :text-align "center"}}
           [:div {:style {:font-size "12px"
                          :color "#6B7280"
                          :margin-bottom "4px"}}
            "Columns"]
           [:div {:style {:font-size "24px"
                          :font-weight "bold"
                          :color "#10B981"}}
            cols]]
          [:div {:style {:background-color "#FFFFFF"
                         :padding "12px"
                         :border-radius "6px"
                         :text-align "center"}}
           [:div {:style {:font-size "12px"
                          :color "#6B7280"
                          :margin-bottom "4px"}}
            "Memory"]
           [:div {:style {:font-size "24px"
                          :font-weight "bold"
                          :color "#8B5CF6"}}
            memory " KB"]]]
         ;; Column details
         [:div
          [:h4 {:style {:font-size "14px"
                        :font-weight "600"
                        :margin-bottom "8px"
                        :color "#374151"}}
           "Column Types:"]
          [:div {:style {:display "grid"
                         :grid-template-columns "repeat(2, 1fr)"
                         :gap "8px"}}
           (for [[col dtype] (:dtypes info)]
             ^{:key col}
             [:div {:style {:background-color "#FFFFFF"
                            :padding "8px 12px"
                            :border-radius "4px"
                            :display "flex"
                            :justify-content "space-between"
                            :font-size "13px"}}
              [:span {:style {:color "#374151"}} col]
              [:span {:style {:color "#6B7280"
                              :font-family "monospace"
                              :font-size "11px"}}
               dtype]])]]]))))

(defn dataframe-preview
  []
  (when-let [preview @df-preview]
    (when (:success preview)
      [:div {:style {:background-color "#FFFFFF"
                     :border "1px solid #E5E7EB"
                     :border-radius "8px"
                     :padding "20px"
                     :margin-bottom "20px"
                     :overflow-x "auto"}}
       [:h3 {:style {:font-size "18px"
                     :font-weight "bold"
                     :margin-bottom "12px"
                     :color "#111827"}}
        "👁️ Data Preview (First 10 Rows)"]
       [:div {:dangerouslySetInnerHTML {:__html (:html preview)}}]
       [:style "
         .dataframe-table {
           width: 100%;
           border-collapse: collapse;
           font-size: 14px;
           font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
         }
         .dataframe-table th, .dataframe-table td {
           padding: 10px 12px;
           text-align: left;
           border-bottom: 1px solid #E5E7EB;
         }
         .dataframe-table th {
           background-color: #F3F4F6;
           font-weight: 600;
           color: #374151;
         }
         .dataframe-table tr:hover {
           background-color: #F9FAFB;
         }
       "]])))

(defn dataframe-statistics
  []
  (when-let [stats @df-stats]
    (when (:success stats)
      [:div {:style {:background-color "#FFFFFF"
                     :border "1px solid #E5E7EB"
                     :border-radius "8px"
                     :padding "20px"
                     :margin-bottom "20px"
                     :overflow-x "auto"}}
       [:h3 {:style {:font-size "18px"
                     :font-weight "bold"
                     :margin-bottom "12px"
                     :color "#111827"}}
        "📈 Statistical Summary"]
       [:div {:dangerouslySetInnerHTML {:__html (:stats_html stats)}}]
       [:style "
         .stats-table {
           width: 100%;
           border-collapse: collapse;
           font-size: 14px;
           font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
         }
         .stats-table th, .stats-table td {
           padding: 10px 12px;
           text-align: left;
           border-bottom: 1px solid #E5E7EB;
         }
         .stats-table th {
           background-color: #F3F4F6;
           font-weight: 600;
           color: #374151;
         }
         .stats-table tr:hover {
           background-color: #F9FAFB;
         }
       "]])))

(defn export-section
  []
  (when @df-info
    [:div {:style {:background-color "#F0FDF4"
                   :border "1px solid #BBF7D0"
                   :border-radius "8px"
                   :padding "16px"
                   :margin-bottom "20px"
                   :display "flex"
                   :justify-content "space-between"
                   :align-items "center"}}
     [:div
      [:h4 {:style {:font-size "16px"
                    :font-weight "600"
                    :color "#166534"
                    :margin-bottom "4px"}}
       "💾 Export Data"]
      [:p {:style {:font-size "14px"
                   :color "#15803D"
                   :margin "0"}}
       "Download the processed DataFrame as CSV"]]
     [:button {:style {:padding "10px 20px"
                       :background-color "#10B981"
                       :color "#FFFFFF"
                       :border "none"
                       :border-radius "6px"
                       :font-weight "600"
                       :cursor "pointer"
                       :font-size "14px"}
               :on-click download-csv!}
      "📥 Download CSV"]]))

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

(defn loading-spinner
  []
  (when @loading?
    [:div {:style {:display "flex"
                   :justify-content "center"
                   :align-items "center"
                   :padding "40px"}}
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
       "Analyzing data..."]
      [:style "@keyframes spin { to { transform: rotate(360deg); } }"]]]))

(defn main-component
  []
  [:div {:style {:max-width "1024px"
                 :margin "0 auto"
                 :padding "24px"}}
   [:h1 {:style {:font-size "32px"
                 :font-weight "bold"
                 :margin-bottom "8px"
                 :background "linear-gradient(to right, #3B82F6, #8B5CF6)"
                 :-webkit-background-clip "text"
                 :-webkit-text-fill-color "transparent"}}
    "🐍 Interactive Data Analysis"]
   [:p {:style {:color "#6B7280"
                :margin-bottom "24px"
                :font-size "16px"}}
    "Upload CSV data and analyze it with Pandas - all in your browser!"]
   [:div {:style {:background-color "#EFF6FF"
                  :border-left "4px solid #3B82F6"
                  :padding "16px"
                  :margin-bottom "24px"}}
    [:p {:style {:font-size "14px"
                 :color "#1E40AF"
                 :margin "0"}}
     "💡 This demo shows how to upload CSV files, create Pandas DataFrames, "
     "and perform data analysis entirely in the browser using Pyodide."]]
   [status-indicator]
   ;; Show file upload when ready
   (when (and (pyodide/ready?) @pandas-ready?)
     [file-upload-section])
   ;; Error display
   [error-display]
   ;; Loading spinner
   [loading-spinner]
   ;; Analysis results
   (when (and (not @loading?) @df-info)
     [:div
      [dataframe-info-panel]
      [dataframe-preview]
      [dataframe-statistics]
      [export-section]])
   ;; Footer
   [:div {:style {:margin-top "32px"
                  :padding-top "24px"
                  :border-top "1px solid #E5E7EB"
                  :color "#6B7280"
                  :font-size "14px"}}
    [:p "✨ " [:strong "Features:"]]
    [:ul {:style {:margin "8px 0"
                  :padding-left "24px"}}
     [:li "Upload CSV files from your computer"]
     [:li "Parse data into Pandas DataFrames"]
     [:li "View DataFrame information (shape, columns, types)"]
     [:li "Preview first 10 rows of data"]
     [:li "Generate statistical summaries"]
     [:li "Export processed data as CSV"]]
    [:p {:style {:margin-top "16px"}}
     "🚀 All processing happens client-side - no server required!"]]])

;; ============================================================================
;; Initialize
;; ============================================================================

(defonce init-done?
  (do
    (js/console.log "🚀 Interactive Data Analysis demo mounted")
    ;; Wait for Pyodide to be ready, then load pandas
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

(rdom/render [main-component] (js/document.getElementById "interactive-data-analysis"))
