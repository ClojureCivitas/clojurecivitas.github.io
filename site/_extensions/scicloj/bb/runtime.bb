(ns babqua.runtime
  "Babqua bb-side runtime: evaluates user code blocks, detects Kindly
  metadata on the result, and pre-renders the value into a payload
  that the Lua filter can drop straight into the AST.

  Hiccup→HTML and value→JSON happen here, on the Babashka side, where
  they're one library call. The Lua filter stays thin: dispatch by
  `:format`, manage CDN scripts, insert the pre-formed payload."
  (:require [hiccup2.core :as hiccup]
            [cheshire.core :as json]
            [clojure.string :as str]))

(declare render-by-kind)

;; String-typed kinds use the `^:kind/html ["..."]` vector-wrapping
;; convention because Clojure strings can't carry metadata. If the value
;; arrives as a vector and the kind is one of these, peel off the wrapper
;; before rendering.
(def ^:private string-kinds
  #{:kind/html :kind/md :kind/mermaid :kind/graphviz :kind/tex :kind/code})

(defn detect-kind
  "Return the Kindly kind of a value's metadata, or nil if none.
  Honors :kindly/kind first; otherwise picks the first metadata key in
  the `kind/...` namespace that's also truthy in the metadata map (the
  `^:kind/foo` reader-meta convention)."
  [m]
  (when m
    (or (:kindly/kind m)
        (some (fn [k]
                (when (and (keyword? k)
                           (= "kind" (namespace k))
                           (get m k))
                  k))
              (keys m)))))

(defn unwrap-string-kind [kind v]
  (if (and (string-kinds kind) (vector? v))
    (first v)
    v))

(defn- script-safe-json
  "Like `json/generate-string`, but escapes every `<` to `\\u003c` so the
  payload can be spliced into an inline `<script>` body without risk of
  user data containing `</script>` (or `<!--` / `<script>`) breaking out
  of the surrounding script element. The escape is invisible to a JSON
  parser — same value, just longer source."
  [v]
  (str/replace (json/generate-string v) "<" "\\u003c"))

(defn- render-table-html
  "Render tabular data as a plain HTML table.

  Accepts two shapes:
    {:column-names [...] :row-vectors [[...] ...]}    (Tablecloth-ish)
    [{...} {...} ...]                                  (sequence of maps)

  Returns nil for unrecognized shapes; caller surfaces an error."
  [v]
  (let [rows (cond
               (and (map? v) (:row-vectors v))
               (cons (:column-names v) (:row-vectors v))

               (and (sequential? v) (seq v) (every? map? v))
               (let [ks (vec (distinct (mapcat keys v)))]
                 (cons ks (map (fn [m] (mapv #(get m %) ks)) v))))]
    (when rows
      (str (hiccup/html
            [:table
             [:thead [:tr (for [c (first rows)] [:th (str c)])]]
             [:tbody (for [r (rest rows)]
                       [:tr (for [c r] [:td (str c)])])]])))))

(defn render-by-kind
  "Pick a Lua-side rendering format for `v` based on `kind`.

  Returns a map with at least `:format` and (usually) `:rendered`.
  Charts also carry `:lib` so the filter knows which CDN entry to load."
  [kind v _opts]
  (case kind
    :kind/hiccup
    {:format "raw-html"
     :rendered (str (hiccup/html v))}

    :kind/html
    {:format "raw-html"
     :rendered (str v)}

    :kind/md
    {:format "markdown"
     :rendered (str v)}

    :kind/hidden
    {:format "hidden"}

    :kind/mermaid
    {:format "mermaid"
     :rendered (str v)}

    :kind/graphviz
    {:format "graphviz"
     :rendered (str v)}

    :kind/tex
    {:format "tex"
     :rendered (str v)}

    :kind/code
    {:format "code-display"
     :rendered (str v)}

    :kind/vega-lite
    {:format "chart" :lib "vega-lite" :rendered (script-safe-json v)}

    :kind/plotly
    {:format "chart" :lib "plotly" :rendered (script-safe-json v)}

    :kind/echarts
    {:format "chart" :lib "echarts" :rendered (script-safe-json v)}

    :kind/cytoscape
    {:format "chart" :lib "cytoscape" :rendered (script-safe-json v)}

    :kind/highcharts
    {:format "chart" :lib "highcharts" :rendered (script-safe-json v)}

    :kind/table
    (if-let [html (render-table-html v)]
      {:format "raw-html" :rendered html}
      {:error (str ":kind/table: value is not recognized as table data. "
                   "Expected {:column-names [...] :row-vectors [[...] ...]} "
                   "or a sequence of maps. Got: " (pr-str v))})

    nil
    (if (var? v)
      ;; `(def x 42)` returns the var (#'user/x); rendering that adds
      ;; noise without information. Knitr and similar tools suppress
      ;; assignment results by default; do the same for top-level defs.
      ;; Override by attaching a kind, e.g. `^:kind/code (str (def x 42))`.
      {:format "hidden"}
      {:format "code-default" :rendered (pr-str v)})

    {:error (str "Unsupported kind: " (str kind)
                 ". Value: " (pr-str v))}))

(defn- read-source
  "Wrap the user's source in `(do ...)` and read it as a single form.
  Babashka's `read-string` doesn't have a stateless multi-form reader
  that doesn't fight with the IndexingReader protocol, so do-wrapping
  is the simple thing that works: the result of evaluating the do is
  the last form's value, which is exactly what we want."
  [s]
  (read-string (str "(do " s "\n)")))

(defn- numeric-options
  "Pick :width and :height out of the value's :kindly/options, dropping
  non-integer values so a stray `:width \"800px\"` doesn't crash the
  filter when it tries to splice into a CSS dimension."
  [opts]
  (into {}
        (filter (fn [[k v]]
                  (and (#{:width :height} k) (integer? v))))
        opts))

(defn eval-block
  "Evaluate `src` as a sequence of Clojure forms in the calling thread's
  current namespace. Returns a map ready for JSON serialization with one
  of these shapes:

    {:format ...   :rendered ... :kind ... :options {...} :stdout ...}
    {:error ...    :stack ...                              :stdout ...}

  Stdout written by the user code is captured into a StringWriter and
  returned, so `(println ...)` is visible in the rendered output."
  [src]
  (let [out (java.io.StringWriter.)
        result
        (try
          (let [form (read-source src)
                value (binding [*out* out] (eval form))
                m (meta value)
                kind (detect-kind m)
                opts (or (:kindly/options m) {})
                unwrapped (unwrap-string-kind kind value)
                rendered (render-by-kind kind unwrapped opts)
                num-opts (numeric-options opts)]
            (cond-> rendered
              kind (assoc :kind (str kind))
              (seq num-opts) (assoc :options
                                    (into {} (map (fn [[k v]] [(name k) v]))
                                          num-opts))))
          (catch Throwable e
            {:error (or (.getMessage e) (str e))
             ;; `.printStackTrace` with no args writes to `System.err`, so
             ;; `with-out-str` (which rebinds *out*) would capture nothing
             ;; and the rendered error block would be just the message.
             ;; Pass an explicit PrintWriter on the rebound *out* so the
             ;; trace lands in `:stack`.
             :stack (with-out-str
                      (.printStackTrace e (java.io.PrintWriter. *out*)))}))
        stdout-str (.toString out)
        stdout (when (seq stdout-str) stdout-str)]
    (cond-> result
      stdout (assoc :stdout stdout))))

(defn run-blocks
  "Evaluate `blocks` (a vector of {:src \"...\"} maps) in order, in the
  `user` namespace so defs persist across blocks."
  [blocks]
  (let [user-ns (or (find-ns 'user)
                    (do (create-ns 'user)
                        (binding [*ns* (find-ns 'user)]
                          (clojure.core/refer-clojure))
                        (find-ns 'user)))]
    (binding [*ns* user-ns]
      (mapv (fn [b] (eval-block (:src b))) blocks))))
