(ns scittle.pyodide.code-editor
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [scittle.pyodide.pyodide-bridge :as pyodide]))

;; ============================================================================
;; Interactive Python Code Editor
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

(defonce code (r/atom "# Try some Python code!\nimport math\n\nradius = 5\narea = math.pi * radius ** 2\nprint(f'Circle area: {area:.2f}')"))

(defonce output (r/atom ""))

(defonce running? (r/atom false))

(defn run-code!
  []
  (reset! running? true)
  (reset! output "Running...")
  (-> (pyodide/run-python
       (str "import sys\n"
            "from io import StringIO\n"
            "sys.stdout = StringIO()\n"
            @code "\n"
            "sys.stdout.getvalue()"))
      (.then (fn [result]
               (reset! output (if (empty? result)
                                "✓ Code executed successfully (no output)"
                                result))
               (reset! running? false)))
      (.catch (fn [err]
                (reset! output (str "❌ Error:\n" (.-message err)))
                (reset! running? false)))))

(defn main-component
  []
  [:div {:style {:max-width "768px"
                 :margin "0 auto"
                 :padding "24px"}}
   [:h2 {:style {:font-size "24px"
                 :font-weight "bold"
                 :margin-bottom "16px"
                 :color (get-color "#111827" "#F3F4F6")}}
    "Python Code Editor 💻"]

   [:div {:style {:background-color (get-color "#FEF3C7" "#78350F")
                  :border-left (str "4px solid " (get-color "#F59E0B" "#FCD34D"))
                  :padding "16px"
                  :margin-bottom "24px"}}
    [:p {:style {:font-size "14px"
                 :color (get-color "#374151" "#FDE68A")}}
     "Write Python code below and click Run to execute it in your browser!"]]

   ;; Code editor
   [:div {:style {:margin-bottom "16px"}}
    [:label {:style {:display "block"
                     :font-size "14px"
                     :font-weight "600"
                     :margin-bottom "8px"
                     :color (get-color "#374151" "#D1D5DB")}}
     "Python Code:"]
    [:textarea {:value @code
                :on-change #(reset! code (.. % -target -value))
                :rows 10
                :style {:width "100%"
                        :padding "12px"
                        :font-family "monospace"
                        :font-size "14px"
                        :background-color (get-color "#FFFFFF" "#1F2937")
                        :color (get-color "#111827" "#F3F4F6")
                        :border (str "2px solid " (get-color "#D1D5DB" "#4B5563"))
                        :border-radius "6px"
                        :resize "vertical"}}]]

   ;; Run button
   [:button {:style {:width "100%"
                     :padding "12px 24px"
                     :background-color (if @running?
                                         (get-color "#9CA3AF" "#6B7280")
                                         (get-color "#10B981" "#059669"))
                     :color "#FFFFFF"
                     :border "none"
                     :border-radius "6px"
                     :font-weight "600"
                     :font-size "16px"
                     :cursor (if @running? "not-allowed" "pointer")
                     :margin-bottom "16px"}
             :on-click run-code!
             :disabled (or @running? (not (pyodide/ready?)))}
    (cond
      @running? "Running..."
      (not (pyodide/ready?)) "Initialize Pyodide First"
      :else "Run Code")]

   ;; Output display
   [:div {:style {:margin-bottom "16px"}}
    [:label {:style {:display "block"
                     :font-size "14px"
                     :font-weight "600"
                     :margin-bottom "8px"
                     :color (get-color "#374151" "#D1D5DB")}}
     "Output:"]
    [:pre {:style {:background-color (get-color "#F9FAFB" "#1F2937")
                   :color (get-color "#1F2937" "#F3F4F6")
                   :padding "16px"
                   :border-radius "6px"
                   :border (str "2px solid " (get-color "#E5E7EB" "#4B5563"))
                   :font-family "monospace"
                   :font-size "14px"
                   :min-height "120px"
                   :white-space "pre-wrap"
                   :overflow-x "auto"}}
     @output]]])

(rdom/render [main-component] (js/document.getElementById "code-editor-demo"))
