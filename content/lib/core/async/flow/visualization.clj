^{:kindly/hide-code true
  :clay             {:title  "Core Async Flow Visualization"
                     :quarto {:author   [:daslu :timothypratley]
                              :date     "2025-06-03"
                              :category :clojure
                              :tags     [:core.async :core.async.flow]}}}
(ns lib.core.async.flow.visualization
  (:require [clojure.datafy :as datafy]
            [clojure.string :as str]))

;; # Visualizing core.async.flows

(defn id-for [x]
  (cond (keyword? x) (str (symbol x))
        (vector? x) (str/join "_" (map id-for x))
        (string? x) x
        :else (str x)))

;; would be more interesting if we show the buffer state
(defn conn-table [flow]
  (let [{:keys [conns procs]} (datafy/datafy flow)
        all-proc-chans (into #{} cat conns)]
    ;; TODO: add channel state
    ^:kind/table
    {:row-maps (vec (for [[from to] conns]
                      {:source (id-for from)
                       :target (id-for to)}))}))

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

(defn elkg [flow]
  (let [{:keys [conns procs]} (datafy/datafy flow)
        all-proc-chans (into #{} cat conns)
        chans-as-ports true
        with-content false]
    {:id            "G"
     :layoutOptions {:elk.algorithm         "layered"
                     :elk.direction         "DOWN"
                     :elk.hierarchyHandling "INCLUDE_CHILDREN"}
     :children
     (for [[proc-key proc-chans] (group-by first all-proc-chans)]
       (let [{:keys [args proc]} (get procs proc-key)
             {:keys [desc]} proc
             {:keys [params ins outs]} desc
             width 100
             height 100
             content (when with-content
                       [{:id            (str (name proc-key) "_content")
                         :width         (- width 5)
                         :height        (- height 5)
                         ;; nope, do it by id
                         :layoutOptions {:content (str/join \newline
                                                            (for [[k param] params]
                                                              (str (name k) " (" (get args k) ") " param)))}}])
             children (for [[_ chan :as proc-chan] proc-chans]
                        {:id       (id-for proc-chan)
                         :width    20
                         :height   20
                         :labels   [{:text (name chan)}]
                         :children (vec (when with-content
                                          [{:id            (str (id-for proc-chan) "_content")
                                            :width         20
                                            :height        20
                                            ;; nope, do it by id
                                            :layoutOptions {:content (str (name chan)
                                                                          \newline \newline
                                                                          (or (get outs chan)
                                                                              (get ins chan)))}}]))})]
         {:id            (id-for proc-key)
          :width         100
          :height        100
          :layoutOptions {:org.eclipse.elk.nodeLabels.placement "OUTSIDE V_TOP H_LEFT"}
          :labels        [{:text (name proc-key)}]
          :children      (vec (concat content (when (not chans-as-ports) children)))

          :ports
          (vec (when chans-as-ports children))}))
     :edges
     (vec (for [[from to] conns]
            {:id      (id-for [from to])
             :sources [(id-for from)]
             :targets [(id-for to)]}))}))
