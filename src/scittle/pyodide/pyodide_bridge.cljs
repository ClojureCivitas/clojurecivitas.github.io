(ns scittle.pyodide.pyodide-bridge
  (:require [reagent.core :as r]))

;; ============================================================================
;; Shared Pyodide Bridge for Scittle Demos
;; This provides a single source of truth for Pyodide state across all demos
;; ============================================================================

;; Global state - shared across all demos
(defonce pyodide-instance (r/atom nil))

(defonce loading? (r/atom false))

(defonce error (r/atom nil))

;; ============================================================================
;; Core Pyodide Functions
;; ============================================================================

(defn init!
  "Initialize Pyodide if not already initialized. Safe to call multiple times.
   Returns a promise that resolves when Pyodide is ready."
  []
  (cond
    ;; Already loaded - return resolved promise with instance
    @pyodide-instance
    (do
      (js/console.log "Pyodide already initialized")
      (js/Promise.resolve @pyodide-instance))
    ;; Currently loading - return existing promise (will resolve when done)
    @loading?
    (do
      (js/console.log "Pyodide is already loading, please wait...")
      (js/Promise.resolve nil))
    ;; Not started - start loading
    :else
    (do
      (js/console.log "Starting Pyodide initialization...")
      (reset! loading? true)
      (reset! error nil)
      (-> (js/loadPyodide
           #js {:indexURL "https://cdn.jsdelivr.net/pyodide/v0.28.3/full/"
                :fullStdLib false})
          (.then (fn [pyodide]
                   (js/console.log "✓ Pyodide loaded successfully")
                   (reset! pyodide-instance pyodide)
                   (reset! loading? false)
                   pyodide))
          (.catch (fn [err]
                    (js/console.error "✗ Failed to load Pyodide:" err)
                    (reset! error (.-message err))
                    (reset! loading? false)
                    (js/Promise.reject err)))))))

(defn get-instance
  "Get the current Pyodide instance (may be nil if not initialized)"
  []
  @pyodide-instance)

(defn ready?
  "Check if Pyodide is ready to use"
  []
  (some? @pyodide-instance))

(defn is-loading?
  "Check if Pyodide is currently loading"
  []
  @loading?)

(defn get-error
  "Get the current error message (if any)"
  []
  @error)

(defn status
  "Get current status as keyword: :not-started, :loading, :ready, or :error"
  []
  (cond
    @error :error
    @pyodide-instance :ready
    @loading? :loading
    :else :not-started))

;; ============================================================================
;; Convenience Functions
;; ============================================================================

(defn run-python
  "Execute Python code. Returns a promise resolving to the result.
   Automatically waits for Pyodide to be ready."
  [code]
  (if-let [pyodide @pyodide-instance]
    (.runPythonAsync pyodide code)
    (js/Promise.reject (js/Error. "Pyodide not initialized"))))

(defn load-packages
  "Load Python packages. Returns a promise.
   Example: (load-packages [\"matplotlib\" \"pandas\"])"
  [package-names]
  (if-let [pyodide @pyodide-instance]
    (if (sequential? package-names)
      (js/Promise.all
       (clj->js (map #(.loadPackage pyodide %) package-names)))
      (.loadPackage pyodide package-names))
    (js/Promise.reject (js/Error. "Pyodide not initialized"))))

(defn set-global
  "Set a global variable in Python namespace"
  [var-name value]
  (when-let [pyodide @pyodide-instance]
    (aset (.-globals pyodide) var-name value)))

(defn get-global
  "Get a global variable from Python namespace"
  [var-name]
  (when-let [pyodide @pyodide-instance]
    (aget (.-globals pyodide) var-name)))

(defn run-python-with-plot
  "Execute Python code that generates a matplotlib plot.
   Returns a promise resolving to a base64-encoded PNG image.
   The Python code should create a matplotlib figure."
  [code]
  (if-let [pyodide @pyodide-instance]
    (let [plot-code (str "
import io
import base64
import matplotlib
matplotlib.use('Agg')  # Use non-interactive backend
import matplotlib.pyplot as plt

# Clear any previous plots
plt.clf()
plt.close('all')

# User's plotting code
" code "

# Capture the plot
buf = io.BytesIO()
plt.savefig(buf, format='png', bbox_inches='tight', dpi=100)
buf.seek(0)
img_base64 = base64.b64encode(buf.read()).decode('utf-8')
buf.close()
plt.close('all')

# Return the base64 string
img_base64
")]
      (.runPythonAsync pyodide plot-code))
    (js/Promise.reject (js/Error. "Pyodide not initialized"))))

(defn run-python-with-output
  "Execute Python code and capture stdout (print statements).
   Returns a promise resolving to the captured output as a string.
   This is useful for pandas DataFrames that use print(df.to_html())."
  [code]
  (if-let [pyodide @pyodide-instance]
    (let [capture-code (str "
import sys
import io

# Capture stdout
captured_output = io.StringIO()
sys.stdout = captured_output

# User's code
" code "

# Get the captured output
output = captured_output.getvalue()

# Restore stdout
sys.stdout = sys.__stdout__

# Return the captured output
output
")]
      (.runPythonAsync pyodide capture-code))
    (js/Promise.reject (js/Error. "Pyodide not initialized"))))
