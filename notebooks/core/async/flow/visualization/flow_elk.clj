^{:kindly/hide-code true
  :clay             {:quarto {:title    "Flow to ELK data transformation"
                              :author   :timothypratley
                              :category :clojure
                              :tags     [:core.async :core.async.flow]}}}

(ns core.async.flow.visualization.flow-elk
  (:require [clojure.datafy :as datafy]
            [clojure.string :as str]))

(defn id-for [x]
  (cond (keyword? x) (str (symbol x))
        (vector? x) (str/join "_" (map id-for x))
        (string? x) x
        :else (str x)))



;; problem: graph layout algorithms have different requirements
;; complex: label placement may be important, handled by layout, different for parents
;; complex: ports may be useful or unsupported
;; problem: html nodes need to be measured for layout
;; problem: my data is not the layout is not the view
;; problem: arbitrary data is not transferred
;; problem: visualizations are trees, graphs are not always trees

;; idea: A standard **view model** for graphs (not trees)
;; the view model should just be nodes and edges
;; - port nodes with special properties
;; - what about groups? nodes that have children
;; - ordering parents have children... not a nesting
;;    -- the viewmodel can calculate what are the parents of x, what are the children
;;    -- nodes can have a parent identifier. we represent the relation not the hierarchy
;; ** ELK models are hierarchy <-- problematic

;; When do we care?
;;
;; A (parents #{B})
;; B (parents #{C})
;; C
;; D (parents #{B, C})
;;
;; "Parents are an edge with a special property"
;;
;; B -> A
;; C -> B
;; C -> D
;; B -> D
;;
;; We represent them as groups
;;
;; Trick: layout with parent edges, don't draw them, but do create borders.
;; Problem: incorrect overlaps, can be solved with Veroni

;; Everything is a node or edge,
;; some logic to determine how it's displayed, and ordering
;; Can be **general**
;; Output from layout algorithms will need to be transformed


;; idea: Structural merge (like Reagent, respecting keys) can combine layout and data

;; Reagent:
;; define component f (data)
;;
;; =>
;; f1 [ [:div {:key 1} "apples"]
;;      [:div {:key 2} "pear"] ]
;;
;; =>
;; f2 [ [:div {:key 2} "pears"]
;;      [:div {:key 1} "apples] ]
;; compute the updates,
;; with the keys: swap the dom elements and update pear to pears
;; without the keys: replace everything
;;
;; Structural merge:
;;
;; (merge-structure
;; {:a [{:key 1, :happy "yes"} {:key 2, :happy "no"}]}
;; {:a [{:key 2, :happy "absolutely"}]})
;; =>
;; {:a [{:key 1, :happy "yes"} {:key 2, :happy "absolutely"}]}
;;
;; (merge-structure g (layout g))
;; => adds x and y, keeps my data.
;;
;; tree
;; {:children [{:label ... }]}
;; layout
;; {:children [{:label ..., :x 1, :y 2}]
;;
;; (merge structure
;;
;; (merge-dissoc
;; removals
;;
;; (merge-replace
;; replacement
;;
;; supporting functions (optional)
;; {:a #(...)}
