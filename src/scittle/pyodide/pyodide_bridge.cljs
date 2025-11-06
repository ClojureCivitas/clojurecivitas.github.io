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

;; ============================================================================
;; CSV & DataFrame Functions
;; ============================================================================

(defn load-csv-to-dataframe
  "Load CSV content into a Pandas DataFrame.

   Parameters:
   - csv-content: String content of CSV file
   - options: Map of parsing options
     - :df-name - Name for the DataFrame variable (default: 'df')
     - :delimiter - CSV delimiter (default: ',')
     - :has-header - Whether CSV has header row (default: true)

   Returns promise resolving to:
   {:success true :df-name 'df'} or
   {:success false :error 'error message'}

   Example:
   (load-csv-to-dataframe csv-content {:df-name \"sales_data\"})"
  [csv-content & {:keys [df-name delimiter has-header]
                  :or {df-name "df"
                       delimiter ","
                       has-header true}}]
  (if-let [pyodide @pyodide-instance]
    (-> ;; Store CSV content in Pyodide namespace
     (js/Promise.resolve
      (aset (.-globals pyodide) "__csv_content__" csv-content))
     (.then (fn []
              ;; Load pandas if not already loaded
              (.loadPackage pyodide "pandas")))
     (.then (fn []
              ;; Parse CSV and create DataFrame
              (let [header-param (if has-header "header=0" "header=None")
                    python-code (str "
import pandas as pd
import io
import json

def load_dataframe():
    try:
        df_name = '" df-name "'
        df = pd.read_csv(
            io.StringIO(__csv_content__),
            delimiter='" delimiter "',
            " header-param "
        )
        # Store in global namespace
        globals()[df_name] = df

        result = {
            'success': True,
            'df_name': df_name,
            'shape': list(df.shape),
            'columns': list(df.columns),
            'dtypes': {col: str(dtype) for col, dtype in df.dtypes.items()}
        }
        return json.dumps(result)
    except Exception as e:
        import traceback
        error_msg = str(e) if str(e) else 'Unknown error'
        return json.dumps({'success': False, 'error': error_msg, 'traceback': traceback.format_exc()})

load_dataframe()
")]
                (.runPythonAsync pyodide python-code))))
     (.then (fn [result]
              (js/console.log "Raw Python result:" result)
              (let [result-js (js->clj (js/JSON.parse result) :keywordize-keys true)]
                (if (:success result-js)
                  (do
                    (js/console.log "✓ DataFrame created:" (:df_name result-js))
                    result-js)
                  (do
                    (js/console.error "✗ DataFrame creation failed:" (:error result-js))
                    (when (:traceback result-js)
                      (js/console.error "Python traceback:" (:traceback result-js)))
                    result-js)))))
     (.catch (fn [err]
               (js/console.error "✗ CSV loading error:" err)
               {:success false :error (str "CSV loading failed: " (.-message err))})))
    (js/Promise.resolve
     {:success false :error "Pyodide not initialized"})))

(defn get-dataframe-info
  "Get detailed information about a DataFrame.

   Parameters:
   - df-name: Name of the DataFrame variable

   Returns promise with:
   {:success true
    :shape [rows cols]
    :columns [...]
    :dtypes {...}
    :null-counts {...}
    :memory-usage 'size in KB'}

   Example:
   (get-dataframe-info \"df\")"
  [df-name]
  (if-let [pyodide @pyodide-instance]
    (-> (.runPythonAsync pyodide (str "
import json

def get_info():
    try:
        info = {
            'success': True,
            'shape': list(" df-name ".shape),
            'columns': list(" df-name ".columns),
            'dtypes': {col: str(dtype) for col, dtype in " df-name ".dtypes.items()},
            'null_counts': " df-name ".isnull().sum().to_dict(),
            'memory_usage': round(" df-name ".memory_usage(deep=True).sum() / 1024, 2)
        }
        return json.dumps(info)
    except Exception as e:
        return json.dumps({'success': False, 'error': str(e)})

get_info()
"))
        (.then (fn [result]
                 (js->clj (js/JSON.parse result) :keywordize-keys true)))
        (.catch (fn [err]
                  {:success false :error (.-message err)})))
    (js/Promise.resolve
     {:success false :error "Pyodide not initialized"})))

(defn get-dataframe-preview
  "Get HTML preview of DataFrame.

   Parameters:
   - df-name: Name of the DataFrame variable
   - n-rows: Number of rows to display (default: 10)

   Returns promise with:
   {:success true :html 'HTML table string'}

   Example:
   (get-dataframe-preview \"df\" 20)"
  [df-name & {:keys [n-rows] :or {n-rows 10}}]
  (if-let [pyodide @pyodide-instance]
    (-> (.runPythonAsync pyodide (str "
import json

def get_preview():
    try:
        preview_html = " df-name ".head(" n-rows ").to_html(
            classes='dataframe-table',
            border=0,
            index=True,
            na_rep='NaN',
            float_format=lambda x: f'{x:.2f}' if pd.notna(x) else 'NaN'
        )
        return json.dumps({'success': True, 'html': preview_html})
    except Exception as e:
        return json.dumps({'success': False, 'error': str(e)})

get_preview()
"))
        (.then (fn [result]
                 (js->clj (js/JSON.parse result) :keywordize-keys true)))
        (.catch (fn [err]
                  {:success false :error (.-message err)})))
    (js/Promise.resolve
     {:success false :error "Pyodide not initialized"})))

(defn get-dataframe-statistics
  "Get statistical summary of DataFrame.

   Parameters:
   - df-name: Name of the DataFrame variable

   Returns promise with:
   {:success true :stats-html 'HTML table of statistics'}

   Example:
   (get-dataframe-statistics \"df\")"
  [df-name]
  (if-let [pyodide @pyodide-instance]
    (-> (.runPythonAsync pyodide (str "
import json

def get_stats():
    try:
        stats_html = " df-name ".describe().to_html(
            classes='stats-table',
            border=0,
            float_format=lambda x: f'{x:.2f}'
        )
        return json.dumps({'success': True, 'stats_html': stats_html})
    except Exception as e:
        return json.dumps({'success': False, 'error': str(e)})

get_stats()
"))
        (.then (fn [result]
                 (js->clj (js/JSON.parse result) :keywordize-keys true)))
        (.catch (fn [err]
                  {:success false :error (.-message err)})))
    (js/Promise.resolve
     {:success false :error "Pyodide not initialized"})))

(defn export-dataframe-to-csv
  "Export DataFrame to CSV string.

   Parameters:
   - df-name: Name of the DataFrame variable
   - include-index?: Include index column (default: false)

   Returns promise with:
   {:success true :csv 'CSV string'} or
   {:success false :error 'error message'}

   Example:
   (export-dataframe-to-csv \"df\")"
  [df-name & {:keys [include-index?] :or {include-index? false}}]
  (if-let [pyodide @pyodide-instance]
    (-> (.runPythonAsync pyodide (str "
import json

def export_csv():
    try:
        csv_str = " df-name ".to_csv(index=" (if include-index? "True" "False") ")
        return json.dumps({'success': True, 'csv': csv_str})
    except Exception as e:
        return json.dumps({'success': False, 'error': str(e)})

export_csv()
"))
        (.then (fn [result]
                 (js->clj (js/JSON.parse result) :keywordize-keys true)))
        (.catch (fn [err]
                  {:success false :error (.-message err)})))
    (js/Promise.resolve
     {:success false :error "Pyodide not initialized"})))
