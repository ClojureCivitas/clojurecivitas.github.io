(ns scittle.pyodide.code-executor
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [scittle.pyodide.pyodide-bridge :as pyodide]))

;; ============================================================================
;; Interactive Python Code Executor
;; Uses shared pyodide-bridge for Pyodide state
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

;; Local state - only UI/output state, Pyodide state is in bridge
(defonce code (r/atom "# Write your Python code here\nprint('Hello, World!')\n\n# Try some calculations\nresult = 2 + 2\nprint(f'2 + 2 = {result}')"))

(defonce output (r/atom ""))

(defonce error (r/atom nil))

(defonce executing? (r/atom false))

;; ============================================================================
;; Pyodide Functions
;; ============================================================================

(defn init-pyodide!
  []
  (-> (pyodide/init!)
      (.then (fn [_]
               (reset! output "Pyodide loaded! Ready to execute Python code.")
               (js/console.log "✓ Pyodide initialized")))
      (.catch (fn [err]
                (reset! error (.-message err))
                (js/console.error "Failed to load Pyodide:" err)))))

(defn capture-output
  "Setup Python stdout capture"
  [pyodide-inst]
  (.runPython pyodide-inst "
import sys
import io

class OutputCapture:
    def __init__(self):
        self.output = []

    def write(self, text):
        if text and text != '\\n':
            self.output.append(text)

    def flush(self):
        pass

    def get_output(self):
        return ''.join(self.output)

    def clear(self):
        self.output = []

_output_capture = OutputCapture()
sys.stdout = _output_capture
sys.stderr = _output_capture
"))

(defn run-python-code
  []
  (when-let [pyodide-inst (pyodide/get-instance)]
    (reset! executing? true)
    (reset! error nil)
    (-> ;; Clear previous output
     (js/Promise.resolve
      (.runPython pyodide-inst "_output_capture.clear()"))
     (.then (fn []
              ;; Execute user code
              (.runPythonAsync pyodide-inst @code)))
     (.then (fn [result]
              ;; Get captured output
              (let [captured-output (.runPython pyodide-inst "_output_capture.get_output()")
                    result-str (str result)]
                (reset! output
                        (str captured-output
                             (when (and (seq result-str)
                                        (not= result-str "undefined")
                                        (not= result-str "None"))
                               (str "\n→ " result-str))))
                (reset! executing? false))))
     (.catch (fn [err]
               (reset! error (.-message err))
               (reset! output "")
               (reset! executing? false))))))

;; ============================================================================
;; UI Components
;; ============================================================================

(defn status-indicator
  []
  (let [status (pyodide/status)]
    [:div.flex.items-center.justify-between.mb-4
     [:div.flex.items-center.gap-2
      [:span.text-sm.font-semibold "Status:"]
      [:span.px-3.py-1.rounded-full.text-xs.font-medium
       (case status
         :not-started {:class "bg-gray-200 text-gray-700"
                       :text "Not Initialized"}
         :loading {:class "bg-blue-200 text-blue-700 animate-pulse"
                   :text "Loading..."}
         :ready {:class "bg-green-200 text-green-700"
                 :text "Ready"}
         :error {:class "bg-red-200 text-red-700"
                 :text "Error"})]]

     (when (= status :ready)
       [:div.flex.gap-2
        [:button.px-4.py-2.bg-green-500.text-white.rounded.hover:bg-green-600.disabled:opacity-50.text-sm.font-medium
         {:on-click run-python-code
          :disabled @executing?}
         (if @executing?
           "Running..."
           "▶ Run Code")]

        [:button.px-3.py-2.bg-gray-500.text-white.rounded.hover:bg-gray-600.text-sm
         {:on-click #(do (reset! output "")
                         (reset! error nil))}
         "Clear Output"]])]))

(defn code-editor
  []
  (let [status (pyodide/status)]
    [:div.mb-4
     [:label.block.text-sm.font-semibold.mb-2.text-gray-700
      "Python Code Editor"]
     [:textarea.w-full.h-64.p-4.border-2.border-gray-300.rounded-lg.font-mono.text-sm.focus:outline-none.focus:border-blue-500
      {:value @code
       :on-change #(reset! code (.. % -target -value))
       :disabled (not= status :ready)
       :placeholder "Write your Python code here..."
       :style {:background-color (if (= status :ready)
                                   (get-color "#ffffff" "#1F2937")
                                   (get-color "#f5f5f5" "#374151"))
               :color (get-color "#111827" "#F3F4F6")
               :resize "vertical"}}]]))

(defn output-display
  []
  [:div.mb-4
   [:label.block.text-sm.font-semibold.mb-2.text-gray-700
    "Output"]
   (if @error
     [:div.bg-red-50.border-2.border-red-300.rounded-lg.p-4
      [:div.text-xs.font-semibold.text-red-700.mb-2 "Error:"]
      [:pre.text-sm.text-red-800.whitespace-pre-wrap.font-mono
       @error]]
     [:div.bg-gray-900.text-green-400.rounded-lg.p-4.min-h-32.font-mono.text-sm.whitespace-pre-wrap
      (if (seq @output)
        @output
        [:span.text-gray-500 "Output will appear here..."])])])

(defn example-snippets
  []
  (let [status (pyodide/status)]
    [:div.mb-4
     [:label.block.text-sm.font-semibold.mb-2.text-gray-700
      "Quick Examples (click to load)"]
     [:div.grid.grid-cols-2.md:grid-cols-3.gap-2
      [:button.px-3.py-2.bg-blue-100.text-blue-700.rounded.hover:bg-blue-200.text-xs.disabled:opacity-50
       {:on-click #(reset! code "# Basic arithmetic\nfor i in range(1, 6):\n    print(f'{i} squared = {i**2}')")
        :disabled (not= status :ready)}
       "Loop Example"]
      [:button.px-3.py-2.bg-purple-100.text-purple-700.rounded.hover:bg-purple-200.text-xs.disabled:opacity-50
       {:on-click #(reset! code "# List comprehension\nnumbers = [1, 2, 3, 4, 5]\nsquares = [n**2 for n in numbers]\nprint(f'Numbers: {numbers}')\nprint(f'Squares: {squares}')")
        :disabled (not= status :ready)}
       "List Comprehension"]
      [:button.px-3.py-2.bg-green-100.text-green-700.rounded.hover:bg-green-200.text-xs.disabled:opacity-50
       {:on-click #(reset! code "# Function definition\ndef fibonacci(n):\n    if n <= 1:\n        return n\n    return fibonacci(n-1) + fibonacci(n-2)\n\nfor i in range(10):\n    print(f'fib({i}) = {fibonacci(i)}')")
        :disabled (not= status :ready)}
       "Fibonacci"]
      [:button.px-3.py-2.bg-yellow-100.text-yellow-700.rounded.hover:bg-yellow-200.text-xs.disabled:opacity-50
       {:on-click #(reset! code "# Dictionary operations\ndata = {'name': 'Alice', 'age': 30, 'city': 'NYC'}\nfor key, value in data.items():\n    print(f'{key}: {value}')")
        :disabled (not= status :ready)}
       "Dictionary"]
      [:button.px-3.py-2.bg-pink-100.text-pink-700.rounded.hover:bg-pink-200.text-xs.disabled:opacity-50
       {:on-click #(reset! code "# String manipulation\ntext = 'Hello Python World'\nprint(f'Original: {text}')\nprint(f'Upper: {text.upper()}')\nprint(f'Words: {text.split()}')\nprint(f'Reversed: {text[::-1]}')")
        :disabled (not= status :ready)}
       "Strings"]
      [:button.px-3.py-2.bg-indigo-100.text-indigo-700.rounded.hover:bg-indigo-200.text-xs.disabled:opacity-50
       {:on-click #(reset! code "# Classes\nclass Dog:\n    def __init__(self, name):\n        self.name = name\n    \n    def bark(self):\n        return f'{self.name} says Woof!'\n\ndog = Dog('Rex')\nprint(dog.bark())")
        :disabled (not= status :ready)}
       "Classes"]]]))

(defn main-component
  []
  (let [status (pyodide/status)
        pyodide-error (pyodide/get-error)]
    [:div.max-w-4xl.mx-auto.p-6
     [:h2.text-3xl.font-bold.mb-2 "Interactive Python Executor 🐍"]
     [:p.text-gray-600.mb-6
      "Write and execute Python code directly in your browser. No server required!"]

     (when (= status :not-started)
       [:div.bg-yellow-50.border-l-4.border-yellow-500.p-4.mb-6
        [:p.text-sm.text-gray-700.mb-3
         "Click the button below to initialize Pyodide. This will download ~8MB of Python runtime (one-time load)."]
        [:button.px-6.py-3.bg-gradient-to-r.from-blue-500.to-purple-600.text-white.rounded-lg.font-semibold.hover:from-blue-600.hover:to-purple-700
         {:on-click #(do (init-pyodide!)
                         ;; Setup output capture after init
                         (js/setTimeout
                          (fn []
                            (when-let [pyodide-inst (pyodide/get-instance)]
                              (capture-output pyodide-inst)))
                          1000))}
         "Initialize Python Runtime"]])
     (when (= status :loading)
       [:div.bg-blue-50.border-l-4.border-blue-500.p-4.mb-6
        [:div.flex.items-center.gap-3
         [:div.animate-spin.rounded-full.h-6.w-6.border-b-2.border-blue-500]
         [:p.text-sm.text-gray-700
          "Loading Pyodide... This may take a few seconds on first load."]]])
     (when (= status :ready)
       [:div
        [status-indicator]
        [example-snippets]
        [code-editor]
        [output-display]
        [:div.bg-gray-50.border.border-gray-200.rounded-lg.p-4.mt-6
         [:h3.text-sm.font-semibold.mb-2.text-gray-700 "Features:"]
         [:ul.text-xs.text-gray-600.space-y-1.list-disc.list-inside
          [:li "Full Python 3.11 interpreter running in WebAssembly"]
          [:li "Standard library available (math, random, datetime, etc.)"]
          [:li "Captures print() output and return values"]
          [:li "Syntax errors and exceptions shown inline"]
          [:li "All execution happens client-side in your browser"]]]])
     (when (= status :error)
       [:div.bg-red-50.border-l-4.border-red-500.p-4.mb-6
        [:p.text-sm.text-red-700.mb-2 "Failed to load Pyodide:"]
        [:p.text-xs.text-red-600.font-mono pyodide-error]])]))

;; ============================================================================
;; Mount - Render when script loads
;; ============================================================================

(rdom/render [main-component] (js/document.getElementById "python-executor-demo"))
