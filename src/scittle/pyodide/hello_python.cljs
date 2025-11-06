(ns scittle.pyodide.hello-python
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [scittle.pyodide.pyodide-bridge :as pyodide]))

;; ============================================================================
;; Simple Pyodide "Hello World" Demo
;; Uses shared pyodide-bridge for state management
;; ============================================================================

;; Local state (output only - Pyodide state is in bridge)
(defonce output (r/atom "Click 'Initialize Pyodide' to start..."))

;; ============================================================================
;; Pyodide Functions
;; ============================================================================

(defn init-pyodide!
  []
  (js/console.log "Button clicked - starting initialization...")
  (reset! output "Loading Pyodide... This may take a few seconds...")
  (try
    (let [promise (pyodide/init!)]
      (js/console.log "Got promise from pyodide/init!:" promise)
      (-> promise
          (.then (fn [pyodide-inst]
                   (js/console.log "Pyodide init promise resolved:" pyodide-inst)
                   (reset! output "✓ Pyodide loaded successfully! Try running Python code below.")))
          (.catch (fn [err]
                    (js/console.error "Pyodide init promise rejected:" err)
                    (reset! output (str "✗ Error loading Pyodide: " (.-message err)))))))
    (catch :default e
      (js/console.error "Exception in init-pyodide!:" e)
      (reset! output (str "✗ Exception: " (.-message e))))))

(defn run-python [code]
  (if (pyodide/ready?)
    (-> (pyodide/run-python code)
        (.then (fn [result]
                 (reset! output (str "Result: " result))))
        (.catch (fn [err]
                  (reset! output (str "Error: " (.-message err))))))
    (reset! output "Please initialize Pyodide first!")))

;; ============================================================================
;; UI Components
;; ============================================================================

(defn status-badge
  []
  (let [status (pyodide/status)
        badge-style (case status
                      :not-started {:background-color "#E5E7EB" :color "#374151"}
                      :loading {:background-color "#DBEAFE" :color "#1E40AF"}
                      :ready {:background-color "#D1FAE5" :color "#065F46"}
                      :error {:background-color "#FEE2E2" :color "#991B1B"}
                      {:background-color "#E5E7EB" :color "#374151"})
        badge-text (case status
                     :not-started "Not Started"
                     :loading "Loading..."
                     :ready "Ready"
                     :error "Error"
                     "Unknown")]
    [:div {:style {:display "flex"
                   :align-items "center"
                   :gap "8px"
                   :margin-bottom "16px"}}
     [:div {:style {:font-size "14px" :font-weight "600"}} "Pyodide Status:"]
     [:span {:style (merge {:padding "6px 12px"
                            :border-radius "9999px"
                            :font-size "12px"
                            :font-weight "500"}
                           badge-style)}
      badge-text]]))

(defn example-buttons
  []
  [:div {:style {:display "grid"
                 :grid-template-columns "1fr 1fr"
                 :gap "8px"
                 :margin-bottom "16px"}}
   [:button {:style {:padding "8px 16px"
                     :background-color "#3B82F6"
                     :color "#FFFFFF"
                     :border "none"
                     :border-radius "6px"
                     :font-weight "600"
                     :cursor "pointer"
                     :font-size "14px"}
             :on-click #(run-python "print('Hello from Python!')")
             :disabled (not (pyodide/ready?))}
    "Hello Python"]
   [:button {:style {:padding "8px 16px"
                     :background-color "#10B981"
                     :color "#FFFFFF"
                     :border "none"
                     :border-radius "6px"
                     :font-weight "600"
                     :cursor "pointer"
                     :font-size "14px"}
             :on-click #(run-python "2 + 2")
             :disabled (not (pyodide/ready?))}
    "Calculate 2 + 2"]
   [:button {:style {:padding "8px 16px"
                     :background-color "#8B5CF6"
                     :color "#FFFFFF"
                     :border "none"
                     :border-radius "6px"
                     :font-weight "600"
                     :cursor "pointer"
                     :font-size "14px"}
             :on-click #(run-python "import sys\nsys.version")
             :disabled (not (pyodide/ready?))}
    "Python Version"]
   [:button {:style {:padding "8px 16px"
                     :background-color "#F97316"
                     :color "#FFFFFF"
                     :border "none"
                     :border-radius "6px"
                     :font-weight "600"
                     :cursor "pointer"
                     :font-size "14px"}
             :on-click #(run-python "list(range(1, 11))")
             :disabled (not (pyodide/ready?))}
    "List 1-10"]])

(defn output-display
  []
  [:div.bg-gray-900.text-green-400.p-4.rounded-lg.font-mono.text-sm.min-h-24.whitespace-pre-wrap
   @output])

(defn main-component
  []
  [:div {:style {:max-width "768px"
                 :margin "0 auto"
                 :padding "24px"}}
   [:h2 {:style {:font-size "24px"
                 :font-weight "bold"
                 :margin-bottom "16px"}}
    "Hello Python! 🐍"]
   [:div {:style {:background-color "#EFF6FF"
                  :border-left "4px solid #3B82F6"
                  :padding "16px"
                  :margin-bottom "24px"}}
    [:p {:style {:font-size "14px" :color "#374151"}}
     "This demo shows the simplest possible Pyodide integration. "
     "Click the button below to load the Python interpreter in your browser, "
     "then try the example buttons!"]]
   [status-badge]
   [:button {:style {:width "100%"
                     :padding "12px 24px"
                     :background "linear-gradient(to right, #3B82F6, #8B5CF6)"
                     :color "#FFFFFF"
                     :border "none"
                     :border-radius "8px"
                     :font-weight "600"
                     :font-size "16px"
                     :cursor "pointer"
                     :margin-bottom "16px"
                     :opacity (if (pyodide/is-loading?) "0.5" "1")}
             :on-click init-pyodide!
             :disabled (pyodide/is-loading?)}
    (if (pyodide/is-loading?)
      "Loading Pyodide..."
      "Initialize Pyodide")]
   (when (= (pyodide/status) :ready)
     [example-buttons])
   [output-display]
   (when (= (pyodide/status) :ready)
     [:div {:style {:margin-top "16px" :font-size "12px" :color "#6B7280"}}
      [:p "✓ Pyodide is running entirely in your browser"]
      [:p "✓ No server required"]
      [:p "✓ Full Python 3.11 interpreter"]])])

;; ============================================================================
;; Mount - Render when script loads
;; ============================================================================

(rdom/render [main-component] (js/document.getElementById "hello-python-demo"))
