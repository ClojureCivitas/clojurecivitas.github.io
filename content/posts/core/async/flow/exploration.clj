(ns core.async.flow.exploration
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [core.async.flow.example.stats :as stats]
            [clojure.core.async.flow :as flow]
            [clojure.datafy :as datafy]))

;; # Visualizing core.async.flows

;; Clojure's async flows are Directed Acyclic Graphs (DAGs) of channel operations.
;; The new [flow-monitor](https://github.com/clojure/core.async.flow-monitor)
;; can visualize these flows.

;; ## What We'll Explore

;; 1. Basic flow structure (processes, channels, connections)
;; 2. Static visualization of a sample flow
;; 3. Evolution as the flow changes

#_(defmethod print-method clojure.lang.IObj [obj ^java.io.Writer w]
  )


(comment
  (defmethod print-method clojure.core.async.impl.buffers.SlidingBuffer [buf ^java.io.Writer w]
    (.write w (str "#sliding-buffer[size=" (.-n buf) "]")))
  (defmethod print-method clojure.core.async.flow.spi.ProcLauncher [launcher ^java.io.Writer w]
    (.write w "#proc-launcher[]"))
  (defmethod print-method clojure.core.async.impl.channels.ManyToManyChannel [ch ^java.io.Writer w]
    (.write w (str "#chan[buffer-size=" (if (.-buf ch) (count (.-buf ch)) 0) "]")))
  (defmethod print-method clojure.core.async.flow.impl.graph.Graph [graph ^java.io.Writer w]
    (.write w (pr-str (datafy/datafy graph)))))
#_(defmethod print-method clojure.core.protocols.Datafiable [x ^java.io.Writer w]
    (.write w (pr-str (datafy/datafy x))))

(defn protocols [x]
  (->> (class x)
       .getInterfaces
       (filter #(-> % .getName (.startsWith "clojure.lang.")))
       (map #(-> % .getSimpleName (clojure.string/replace #"^\$" "")))
       distinct))
stats/config
(async/chan 1)
(def stats-flow
  (flow/create-flow stats/config))
(protocols stats-flow)
(instance? clojure.core.async.flow.impl.graph.Graph stats-flow)

(defn ff [obj w]
  (let [protocols (->> (class obj) (.getInterfaces) (map #(.getName %)))]
    (.write w "#<reified ")
    (if (seq protocols)
      (do
        (.write w ":protocols ")
        (pr-str protocols w))
      (.write w ":no-protocols"))
    (.write w ">")))

(ff stats-flow *out*)
(.getInterfaces (class stats-flow))

(defn all-protocol-vars [x]
  (->> (all-ns)
       (mapcat ns-publics)
       (vals)
       (keep #(-> % meta :protocol))
       (distinct)
       (filter #(satisfies? @% x))))

(defn protocol-var-ns-name [p]
  (-> (meta p) (:ns) (ns-name)))

(map protocol-var-ns-name (all-protocol-vars stats-flow))

(defn ff [p x]
  (str/starts-with? (protocol-var-ns-name p)
                    (-> (class x) (.getPackageName))))

(filter
  #(ff % stats-flow)
  (all-protocol-vars stats-flow))


(defn conn-table [flow]
  (let [{:keys [conns procs]} (datafy/datafy flow)
        all-proc-chans (into #{} cat conns)]
    ;; TODO: add channel state
    ^:kind/table
    {:row-maps (vec (for [[from to] conns]
                      {:source (id-for from)
                       :target (id-for to)}))}))

(defn id-for [x]
  (cond (keyword? x) (str (symbol x))
        (vector? x) (str/join "_" (map id-for x))
        (string? x) x
        :else (str x)))

(defn proc-table [flow]
  (let [{:keys [conns procs]} (datafy/datafy flow)
        all-proc-chans (into #{} cat conns)]
    ^:kind/table
    {:column-names ["process" "start params" "in chans" "out chans"]
     :row-vectors  (for [[proc-key proc-chans] (group-by first all-proc-chans)]
                     (let [{:keys [args proc]} (get procs proc-key)
                           {:keys [desc]} proc
                           {:keys [params ins outs]} desc]
                       [(name proc-key)
                        ^:kind/hiccup
                        [:div
                         (for [[k param] params]
                           [:div
                            [:div [:strong (name k)] ": " (get args k)]
                            [:div param]])]
                        ^:kind/hiccup
                        [:div (for [[k v] ins]
                                [:div [:strong (name k)] ": " v])]
                        ^:kind/hiccup
                        [:div (for [[k v] outs]
                                [:div [:strong (name k)] ": " v])]]))}))
(proc-table stats-flow)

;; would be more interesting if we show the buffer state
(conn-table stats-flow)


(def chs (flow/start stats-flow))

@(flow/inject stats-flow [:aggregator :poke] [true])
@(flow/inject stats-flow [:aggregator :stat] ["abc1000"])   ;; trigger an alert
@(flow/inject stats-flow [:notifier :in] [:sandwich])

(def report-chan (:report-chan chs))
(flow/ping stats-flow)
(async/poll! report-chan)
(def error-chan (:error-chan chs))
(async/poll! error-chan)

;;(flow/stop stats-flow)
;;(async/close! stat-chan)

;; @(flow/inject stats-flow [:aggregator :poke] [true])

(datafy/datafy stats-flow)
