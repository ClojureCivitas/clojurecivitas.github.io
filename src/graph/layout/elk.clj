^{:kindly/hide-code true
  :clay             {:title  "Eclipse Layout Kernel"
                     :quarto {:author   :timothypratley
                              :draft    true
                              :category :clojure
                              :tags     [:graphs :graph-layout]}}}
(ns graph.layout.elk
  (:require [clojure.data.json :as json]
            [graph.layout.elk-svg :as elk-svg]
            [graph.layout.structural :as structural])
  (:import (org.eclipse.elk.core RecursiveGraphLayoutEngine)
           (org.eclipse.elk.core.util BasicProgressMonitor)
           (org.eclipse.elk.graph ElkNode)
           (org.eclipse.elk.graph.json ElkGraphJson)))

(defn ^ElkNode elk [g]
  (-> (json/write-str g)
      (ElkGraphJson/forGraph)
      (.toElk)))

(defn unelk [^ElkNode g]
  (-> (ElkGraphJson/forGraph g)
      (.toJson)
      (json/read-str {:key-fn keyword})))

(defn layout [orig]
  (let [G (elk orig)]
    (.layout (RecursiveGraphLayoutEngine.) G (BasicProgressMonitor.))
    (->> (unelk G)
         (structural/merge-structure orig))))

;; Example

(-> {:id            "root",
     :layoutOptions {:elk.algorithm         "layered"
                     :elk.direction         "RIGHT"
                     :elk.hierarchyHandling "INCLUDE_CHILDREN"},
     :children      [{:id       "node1",
                      ;; node1 label is hidden under node2
                      ;; can treat labels as nodes as a workaround,
                      ;; or label outside/above, or something
                      ;; groups label outside, nodes inside
                      :labels   [{:text "node1"}]
                      :width    50,
                      :height   50,
                      :children [{:id     "node2",
                                  :labels [{:text "node2"}]
                                  :width  20,
                                  :height 20}]}
                     {:id     "node3",
                      :labels [{:text "node3"}]
                      :width  50,
                      :height 50}]}
    (layout)
    (elk-svg/render-graph)
    (delay))

;; TODO: Maybe these are only for layered
(def algorithm-k "org.eclipse.elk.algorithm")
(def default-layout-algorithm "org.eclipse.elk.layered")
(def default-layout-options
  {"org.eclipse.elk.algorithm"                                    "org.eclipse.elk.layered"
   "org.eclipse.elk.direction"                                    "DOWN"
   "org.eclipse.elk.hierarchyHandling"                            "INCLUDE_CHILDREN"
   "org.eclipse.elk.interactive"                                  true
   "org.eclipse.elk.layered.cycleBreaking.strategy"               "DEPTH_FIRST"
   "org.eclipse.elk.layered.crossingMinimization.strategy"        "LAYER_SWEEP"
   "org.eclipse.elk.layered.crossingMinimization.semiInteractive" true
   "org.eclipse.elk.radial.compactor"                             "RADIAL_COMPACTION"})
