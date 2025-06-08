^{:kindly/hide-code true
  :clay             {:title  "Core Async Flow Visualization"
                     :quarto {:author   [:daslu :timothypratley]
                              :type     :post
                              :draft    true
                              :date     "2025-05-17"
                              :category :clojure
                              :tags     [:core.async :core.async.flow]}}}
(ns core.async.flow.example.flow-show
  (:require [clojure.datafy :as datafy]
            [clojure.string :as str]
            [graph.layout.elk :as elk]
            [graph.layout.elk-svg :as elk-svg]))

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

(defn elkg [flow {:keys [show-chans chans-as-ports with-content proc-width proc-height chan-width chan-height]
                  :or   {show-chans     true
                         chans-as-ports true
                         with-content   false
                         proc-width     60
                         proc-height    30
                         chan-width     30
                         chan-height    12}}]
  (let [{:keys [conns procs chans]} (datafy/datafy flow)
        {:keys [ins outs error report]} chans
        all-proc-chans (into #{} cat conns)
        global-chans [{:id     "report"
                       :width  chan-width
                       :height chan-height
                       :labels [{:text (str "report" "(" (-> report :buffer :count) ")")}]}
                      {:id     "error"
                       :width  chan-width
                       :height chan-height
                       :labels [{:text (str "error" "(" (-> error :buffer :count) ")")}]}]
        proc-nodes (for [[proc-key proc-chans] (group-by first all-proc-chans)]
                     (let [{:keys [args proc]} (get procs proc-key)
                           {:keys [desc]} proc
                           {:keys [params]} desc
                           content (when with-content
                                     [{:id            (str (name proc-key) "_content")
                                       :width         (- proc-width 5)
                                       :height        (- proc-height 5)
                                       ;; nope, do it by id
                                       :layoutOptions {:content (str/join \newline
                                                                          (for [[k param] params]
                                                                            (str (name k) " (" (get args k) ") " param)))}}])
                           chans (for [[_ chan-k :as proc-chan] proc-chans
                                       :let [chan-name (name chan-k)
                                             {:keys [buffer]} (or (get outs chan-k)
                                                                  (get ins chan-k))]]
                                   {:id     (id-for proc-chan)
                                    :width  chan-width
                                    :height chan-height
                                    :labels [{:text (str chan-name " (" (:count buffer) ")")}]})]
                       {:id            (id-for proc-key)
                        :width         proc-width
                        :height        proc-height
                        :layoutOptions {:org.eclipse.elk.nodeLabels.placement "OUTSIDE V_TOP H_LEFT"}
                        :labels        [{:text (name proc-key)}]
                        :children      (vec (concat content
                                                    (when (and show-chans (not chans-as-ports))
                                                      chans)))

                        :ports
                        (vec (when (and show-chans chans-as-ports)
                               chans))}))]
    {:id            "G"
     :layoutOptions {:elk.algorithm         "layered"
                     :elk.direction         "RIGHT"
                     :elk.hierarchyHandling "INCLUDE_CHILDREN"}
     :children      (into proc-nodes global-chans)
     :edges
     (vec (if show-chans
            (concat
              (for [[from to] conns]
                {:id      (id-for [from to])
                 :sources [(id-for from)]
                 :targets [(id-for to)]})
              (for [[p] procs, c ["report" "error"]]
                {:id (id-for [p c])
                 :sources [(id-for p)]
                 :targets [c]}))
            (for [[[from] [to]] conns]
              {:id      (id-for [from to])
               :sources [(id-for from)]
               :targets [(id-for to)]})))}))

(defn flow-svg [flow options]
  (-> (elkg flow options)
      (elk/layout)
      (elk-svg/render-graph)))
