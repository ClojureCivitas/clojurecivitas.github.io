^{:clay {:quarto {:draft true}}}
(ns graph.layout.view-model)

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
