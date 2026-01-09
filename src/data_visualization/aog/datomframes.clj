^{:kindly/hide-code true
  :clay {:title "Plotting Datoms: Queries as Visual Mappings"
         :quarto {:type :post
                  :author [:timothypratley]
                  :date "2026-01-08"
                  :description "A query-first approach makes relational data available for visualization."
                  :image "datomframes.svg"
                  :category :data-visualization
                  :tags [:datavis :datascript]
                  :keywords [:datavis :algebra :datascript :queries :datoms]}}}
(ns data-visualization.aog.datomframes)

;; Most plotting libraries operate on dataframes analogous to a table with rows and columns.
;; But what if our plots operated on a Datomic style database of facts instead?
;;
;; DataScript is an in-memory database of datoms (entity-attribute-value triples).
;; A plot query can select exactly the facts we want to visualize, binding them to visual channels.
;; The query itself becomes the mapping: which attributes become x, y, color, or relationships.

(require '[datascript.core :as d])

;; Why query-based plotting?
;; Databases are relational.
;; Entities can have arbitrary attributes and relationships to other entities.
;; Queries let us express things that don't fit in a single table: joins across entities,
;; aggregations, derived relationships.
;; A query-first approach makes relational data available for visualization.

;; First: some tiny datoms to play with.

(def penguins
  [{:species "Adelie" :bill_length 22.1 :bill_depth 22.7 :sex "MALE"}
   {:species "Adelie" :bill_length 31.5 :bill_depth 14.4 :sex "FEMALE"}
   {:species "Adelie" :bill_length 35.8 :bill_depth 19.2 :sex "MALE"}
   {:species "Chinstrap" :bill_length 39.5 :bill_depth 27.9 :sex "FEMALE"}
   {:species "Chinstrap" :bill_length 46.2 :bill_depth 26.8 :sex "MALE"}
   {:species "Gentoo" :bill_length 49.1 :bill_depth 14.2 :sex "FEMALE"}])

(def penguin-db
  (let [conn (d/create-conn)]
    (d/transact! conn penguins)
    @conn))

;; Think of the database as many tiny facts. Each fact says: "entity has attribute value".

penguin-db

;; How to plot a database:

(def default-palette
  ["#2563eb" "#f97316" "#10b981" "#a855f7" "#ef4444" "#14b8a6" "#f59e0b" "#6b7280"])

(defn color-scale
  "Given a sequence of category values,
   assign consistent colors from the default palette."
  [categories]
  (let [domain (distinct categories)
        colors (cycle default-palette)]
    (into {} (map vector domain colors))))

(defn plot-basic [g]
  (let [{:keys [db query geometry]} (g 1)
        results (vec (d/q query db))]
    (for [geom geometry]
      (case geom
        :point (let [color-map (color-scale (map last results))]
                 (for [[x y color] results]
                   [:circle {:r 2, :cx x, :cy y
                             :fill (get color-map color "gray")}]))
        :line (for [[x1 y1 x2 y2] results]
                [:line {:x1 x1, :y1 y1, :x2 x2, :y2 y2}])))))

;; To specify a plot, we provide a query

(def bill-scatter
  [:graphic {:db penguin-db
             :query '{:find [?x ?y ?color]
                      :where [[?e :species ?color]
                              [?e :bill_length ?x]
                              [?e :bill_depth ?y]]}
             :geometry [:point]}])

;; The coordinates fall out of the query bindings; geometry only chooses how to render them:

;; Wrap it in a tiny SVG viewport to see the result:

^:kind/hiccup
[:svg {:width "100%" :height "300"
       :viewBox "10 10 30 30"
       :xmlns   "http://www.w3.org/2000/svg"}
 [:g {:stroke "gray", :fill "none"}
  (plot-basic bill-scatter)]]

;; We can also query for relationships between entities.
;; For example, pairs of penguins from the same species:

(def same-species-different-sex
  [:graphic {:db penguin-db
             :query '{:find [?x1 ?y1 ?x2 ?y2]
                      :where [[?e1 :species ?s]
                              [?e2 :species ?s]
                              [(not= ?e1 ?e2)]
                              [?e1 :sex ?sex1]
                              [?e2 :sex ?sex2]
                              [(not= ?sex1 ?sex2)]
                              [?e1 :bill_length ?x1]
                              [?e1 :bill_depth ?y1]
                              [?e2 :bill_length ?x2]
                              [?e2 :bill_depth ?y2]]}
             :geometry [:line]}])

;; This small example shows that the mapping lives in the query.
;; Queries can bind points and relationships between entities.
;; Each geometry expects a specific binding shape (points: [x y color], lines: [x1 y1 x2 y2]),
;; so the query and geometry must agree on that contract.
;; The novelty here is expressiveness: a relational query can yield edges,
;; something dataframe workflows don't model directly.

^:kind/hiccup
[:svg {:width "100%" :height "300"
       :viewBox "10 10 30 30"
       :xmlns   "http://www.w3.org/2000/svg"}
 [:g {:stroke "gray", :fill "none"}
  (plot-basic same-species-different-sex)
  (plot-basic bill-scatter)]]

;; Plots as queries let us say more.
;; Points and edges can be defined by a query.
;; The novelty in this example is that relationships can be a first‑class things to draw.
