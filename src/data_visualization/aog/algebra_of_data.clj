^{:kindly/hide-code true
  :clay {:title "Representing Graphics as a Tree of Bindings"
         :quarto {:type :post
                  :author [:timothypratley]
                  :draft true
                  :date "2026-01-06"
                  :description "Programs are abstract trees with lexical scope.
A graphic can be considered a tree of bindings, where data, mappings, and geometry behave like variables."
                  :category :data-visualization
                  :tags [:datavis :algebra]
                  :keywords [:datavis :composition :operators]}}}
(ns data-visualization.aog.algebra-of-data
  (:require [clojure.set :as set]
            [clojure.string :as str]))

;; Inspired by [the algebra of graphics](../aog_in_clojure_part1.html).

;; ## Idea

;; Implement Grammar-of-Graphics as a tree of scoped bindings.

;; In most Grammar-of-Graphics systems (e.g. ggplot2, AoG),
;; layers are presented as a flat collection.
;; Algebraic operations such as crossing and nesting are resolved early,
;; collapsing the expression into a linear structure.
;; Instead we may chose to preserve the algebra as an explicit tree of scoped bindings.
;; Lexical scope for data, mappings, and geometry

;; The main advantage of retaining the tree is structural customization.
;; With a tree we may add paths, annotations, and markdown in the same structure as the graphics.
;; Note that SVG is a tree of groups, styles, geometries, and transforms.
;; Why not include a custom SVG element inside a graphic?
;; This idea does not preclude other targets, it rather recommends a tree based data representation.

;; Programs are trees with lexical scope.
;; A graphic can be considered a tree of bindings, where data, mappings, and geometry behave like variables.
;; Representing graphics as a scoped tree makes the grammar feel more like a program rather than layers.

;; Hiccup provides a natural representation for trees.

;; ### Data

;; For the purposes of this article we will consider "data" to be a sequence of "rows".
;; This definition was chosen for illustrative purposes only.
;; More generally it is better to work with a dataframe like `tech.ml.dataset`,
;; and it does not materially change the implementation.

(def penguins
  [{:species "Adelie" :bill_length 29.1 :bill_depth 18.7 :sex "MALE"}
   {:species "Adelie" :bill_length 33.5 :bill_depth 15.4 :sex "FEMALE"}
   {:species "Chinstrap" :bill_length 43.5 :bill_depth 17.9 :sex "FEMALE"}
   {:species "Gentoo" :bill_length 47.3 :bill_depth 13.8 :sex "MALE"}])

;; ### Tree Representation

;; A "graphic object" represents everything we need to plot something,
;; represented as a hiccup tree:

(def bill-scatter-line
  [:graphic {:data penguins
             :mappings {:x :bill_length, :y :bill_depth}
             :geometry [:line :point]}])

;; This is hiccup tree of tag `:graphic`,
;; attributes for rendering,
;; and no children.
;; This is all we need to make a basic plot.

(defn plot-basic [g]
  (let [{:keys [data mappings geometry]} (g 1)
        {:keys [x y]} mappings
        xys (mapv (juxt x y) data)]
    (for [geom geometry]
      (case geom
        :point (for [[x y] xys]
                 [:circle {:r 2, :cx x, :cy y, :fill "lightblue"}])
        :line [:path {:d (str "M " (str/join ","
                                             (first xys))
                              " L " (str/join " "
                                              (map #(str/join "," %)
                                                   (rest xys))))}]))))

;; Placing this in an SVG with a coordinate system we see the plot:

^:kind/hiccup
[:svg {:width "100%"
       :viewbox [0 0 50 50]}
 (into [:g {:stroke "lightgreen", :fill "none"}]
       (plot-basic bill-scatter-line))]

;; For this basic situation, isn't it nice that we didn't need very much at all?
;; This basic example already has a combination, the scatter plot is overlayed on top of the line plot.
;; A library however can help calculating a coordinate frame, putting axis in place,
;; combining visualizations, while also covering a wide range of use cases.

;; Let's think about our first more interesting combination,
;; two plots that share the same data but have different mappings:

(def bill-mapping-combo
  [:graphic {:data penguins
             :geometry [:line :point]}
   [:graphic {:mappings {:x :bill_length, :y :bill_length}}]
   [:graphic {:mappings {:x :bill_length, :y :bill_depth}}]
   [:graphic {:mappings {:x :bill_depth, :y :bill_length}}]
   [:graphic {:mappings {:x :bill_depth, :y :bill_depth}}]])

;; Looking at this representation it is a tree where the parent has `:data` bound,
;; and children which have different `:mappings` bound.
;; The implication is that the scope is inherited from the parent, and overwritten locally.
;; So to render this graphic we need do a tree walk merging attributes down.

(defn compile-graphic-basic
  ([g] (compile-graphic-basic g nil))
  ([g scope]
   (let [g (update g 1 #(merge scope %))]
     (into
      [:g (when (set/subset? #{:data :mappings :geometry}
                             (set (keys (g 1))))
            (plot-basic g))]
      (for [child (subvec g 2)]
        (compile-graphic-basic child (g 1)))))))

;; Here the node attrs are at index 1, and the node children are everything after the attrs.
;; Later in the article I will introduce some helpers to make node manipulation more explicit.

^:kind/hiccup
[:svg {:width "100%"
       :viewbox [0 0 50 50]}
 [:g {:stroke "lightgreen", :fill "none"}
  (compile-graphic-basic bill-mapping-combo)]]

;; The tree representation is concise, expresses combinations,
;; and is open to inclusion of arbitrary nodes.
;; Such a representation is convenient as an input to custom renderers.
;; It is in a form that can target multiple implementations.

;; Comparing this to the classical definition of "graphic layers",
;; the tree is not yet boiled down to complete representations.
;; That process is implied and must be followed when compiling to a target.
;; The compilation process can go one step further by flattening to be exactly the same as the classical definition.
;; The point is that there are benefits from exposing the structure and concise tree of scopes representation.
;; The intermediate representation is valuable precisely because it hasn't been compiled yet.
;; It's inspectable, modifiable, and open to extension.

;; ::: {.callout-note}
;; I've shown a concrete compilation to SVG.
;; It would be interesting to explore a template or term rewriting translation instead.
;; :::

;; Producing an intermediate representation as a tree
;; empowers users with greater flexibility and clearer documentation.
;; Vega demonstrated the power of explicit, composable data specifications.

;; ## A Minimal User Interface

;; I think that the tree representation is already quite comfortable for expressing plots.
;; Consider for a moment that we could have used Clojure to great effect in constructing our combo tree.

(def bill-mapping-combo-alternative
  (into [:graphic {:data penguins
                   :geometry [:line :point]}]
        (for [x [:bill_length :bill_depth]
              y [:bill_length :bill_depth]]
          [:graphic {:mappings {:x x, :y y}}])))

(= bill-mapping-combo bill-mapping-combo-alternative)

;; This is quite a reasonable way to construct graphics, using Clojure to do tree manipulation.
;; The scoping captures much of the compositional requirements.
;; It's worth noting that using an enlive style structure `{:type :graphic, :attrs {} :children [{:type :graphic}]}`
;; may be more comfortable than hiccup for some users, and that it is possible to convert between enlive and hiccup.
;; Later we'll introduce some basic tree manipulation helpers.

;; For now we can realize some obvious constructor helpers:

(defn graphic* [attrs & children]
  (into [:graphic attrs] children))

(defn data* [d]
  (graphic* {:data d}))

(defn xy* [x y]
  (graphic* {:mappings {:x x, :y y}}))

(defn scatter* []
  (graphic* {:geometry [:point]}))

(defn line* []
  (graphic* {:geometry [:line]}))

(defn merge-attrs* [x y]
  (cond (and (or (nil? x) (map? x))
             (or (nil? y) (map? y)))
        (merge-with merge-attrs* x y)
        (and (sequential? x) (sequential? y))
        (concat x y)
        :else y))

(defn merge-graphics*
  ([a b] (update a 1 merge-attrs* (b 1)))
  ([a b & more]
   (reduce merge-graphics* (merge-graphics* a b) more)))

;; ::: {.callout-note}
;; The `function*` notation is just for this notebook to indicate that these functions are for the minimal interface.
;; :::

;; Now we can have quite a nice compact expression:

(def compact-plot
  (-> (merge-graphics* (data* penguins)
                       (line*)
                       (scatter*))
      (into (for [x [:bill_length :bill_depth]
                  y [:bill_length :bill_depth]]
              (xy* x y)))))

(= compact-plot bill-mapping-combo)

;; And of course that results in the same visualization:

^:kind/hiccup
[:svg {:width "100%"
       :viewbox [0 0 50 50]}
 [:g {:stroke "lightgreen", :fill "none"}
  (compile-graphic-basic compact-plot)]]

;; This is starting to look very similar to the AoG interface.
;; We can see that `merge-graphics*` is a bit like `cross` (except it doesn't consider children),
;; and that `into` is a bit like `blend` (except we also implicitly inherited scope).
;; Next we'll explore how we can build a more or less aligned interface.

;; ## Mathematical helpers

;; Can we have even more expressive composition,
;; inspired by ggplot, with elegant syntax that enables intuitive chart building?

;; Let's dig deeper into the goal to provide composable functions that return graphics, enabling threading style:

;; ```
;; (-> (data penguins)
;;     (xy :bill-length :bill-width)
;;     (line)
;;     (scatter))
;; ```

;; ### Core operations

;; Behind the user interface we may implement the core combination operators:

;; blend (+)
;; : Creates a new scope combining children graphics additively,
;;   no attribute merging.
;;   Represents overlay or separate scopes.

;; cross (×)
;; : Forms the Cartesian product of independent specifications.
;;   Merges attributes of scopes, and cross-product merges children.
;;   Used to complete a layer by merging data, mappings and geometries.

;; nest (▷)
;; : Introduces data partitioning (faceting).
;;   Establishes a new scope with children graphics,
;;   each with a subset of the data and an annotation of its facet role.

;; ### Binding rule

;; User-facing constructors (data, xy, geometry, etc.) do not need to force the user to choose between operators.

;; If a binding collides with an existing binding that defines the chart itself, it blends (adds a new graphic).
;; If it introduces an orthogonal dimension, it crosses (merges a graphic).
;; If it partitions data, it nests (adds new subset graphics).

;; Changing x/y defines a new chart → blend

;; Adding color / shape / size refines the same chart → blend

;; Adding a new geometry → cross

;; Splitting data by a variable → nest

;; This matches ggplot intuition: you don’t get a new chart when you add color, but you do when you facet.

;; The binding rule is highly domain specific.

;; ### Axioms

;; 1. The tree structure defines lexical scope:
;;    child graphics inherit attributes from parents,
;;    and can override (shadow) attributes locally.
;; 2. blend (+) is associative and commutative, with identity empty graphic.
;; 3. cross (×) is associative and commutative when attributes are disjoint, with identity empty graphic.
;; 4. cross distributes over blend:
;;    cross(a, blend(b, c)) = blend(cross(a, b), cross(a, c))
;; 5. nest (▷) is associative but not commutative.
;; 6. bind decides whether to combine within the lexical scope or add a new lexical scope.

;; ### Tree manipulation helpers

;; A graphic is a hiccup node.
;; We need some helpers for inspecting and modifying a hiccup node.

(defn graphic [attrs & children]
  (into [:graphic attrs] children))

(defn concatv
  "Like concat but preserves the source type (vector/set/list)."
  ([] [])
  ([a] a)
  ([a & more]
   (if (or (vector? a) (set? a))
     (reduce into a more)
     (apply concat a more))))

(defn merge-attrs [x y]
  (cond (and (or (nil? x) (map? x))
             (or (nil? y) (map? y)))
        (merge-with merge-attrs x y)
        (and (sequential? x) (sequential? y))
        (concatv x y)
        :else y))

(defn attrs [g]
  (get g 1))

(defn update-attrs [g f & args]
  (apply update g 1 f args))

(defn children [g]
  (subvec g 2))

(defn add-child [g child]
  (conj g child))

(defn replace-children [g children]
  (into (subvec g 0 2) children))

;; These helpers allow us to perform tree operations abstractly rather than manipulating a hiccup vector directly.

;; ### Core Operator Implementations

;; These abstractions will be called by the user interface,
;; or may optionally be used as helpers to constructing a tree.

;; ::: {.callout-warning}
;; Proceed with caution:
;; These operator implementations do not currently pass their scope through,
;; and are thus faulty.
;; I'm posting the code for illustrative purposes only.
;; They should convey their scope and then we can strip the scope out later for the compact view,
;; optionally keeping the fully scoped version for compilation.
;; :::

(defn blend
  ([] (graphic {}))
  ([a] a)
  ([a & gs] (apply graphic {} a gs)))

;; For a tree representation we can think of blend as creating a new graphic that has the children we are juxtaposing.

(defn cross
  ([] (graphic {}))
  ([a b]
   (let [crossed-children (vec (for [ca (children a)
                                     cb (children b)]
                                 (cross ca cb)))
         children (or (not-empty crossed-children)
                      (not-empty (children a))
                      (not-empty (children b)))]
     (-> (update-attrs a merge-attrs (attrs b))
         (replace-children children))))
  ([a b & more]
   (reduce cross (cross a b) more)))

;; Cross is mostly about merging the attributes of nodes.
;; The tricky part is where children are present, those need to be cartesian merged.

(declare data)

(defn nest
  "Partitions the data in g1 based on the variable specified in g2's mappings.
   Returns a graphic with children as new graphic objects, one per data partition (facet)."
  [a b]
  (let [partition-key (-> b attrs :mappings :by)
        data-to-partition (-> a attrs :data)
        grouped-data (group-by partition-key data-to-partition)
        facet-role (or (:facet-role (:options b)) :facet-col)]
    (apply blend
           (for [[group-value subset-data] grouped-data]
             (-> (data subset-data)
                 (assoc-in [:options facet-role] group-value))))))

;; Possibly use a grouping function rather than creating subsets in the future?

(defn collisions [a b]
  (set/intersection (set (keys (attrs a)))
                    (set (keys (attrs b)))))

;; ::: {.callout-warning}
;; Collisions is domain specific.
;; Are there other domains with sensible collision rules?
;; data and xy are collisions, but color should not create a new plot (changing it is an error).
;; We need some logic to handle collisions correctly if we go down this path.
;; :::

(defn bind [a b]
  (if (not-empty (collisions a b))
    (add-child a b)
    (cross a b)))

;; We imagine a more complete version of bind that also recognizes when to nest and blend.
;; Also I'm not sure that it should ever blend (what's the use case, and how should it be expressed?)
;; Recall that conjoining children is adding them to existing scope, whereas blend is similar but pairs graphics in an unscoped parent.

;; ## User interface

;; These functions will be the main way to create a chart by threading.
;; It's an open question whether that's a good idea.
;; They all rely on the behavior of `bind` to choose the right thing to do.
;; The constructor helpers can be called without any context (creating a graphic),
;; or with graphic that is being built up.
;; Here I'm using the term graphic loosely, it is not a **complete** graphic, just a partial definition.
;; Furthermore other graphics can be appended that will be explicitly combined in a possibly different way depending on the situation.

;; Data

(defn data
  ([d] (graphic {:data d}))
  ([g d & gs] (bind g (apply blend (data d) gs))))

;; Mappings

(defn xy
  ([x y] (graphic {:mappings {:x x :y y}}))
  ([g x y & gs] (bind g (apply blend (xy x y) gs))))

(defn color
  ([c] (graphic {:mappings {:color c}}))
  ([g c & gs] (bind g (apply blend (color c) gs))))

;; Geometry

(defn scatter
  ([] (graphic {:geometry [:point]}))
  ([g & gs] (bind g (apply blend (scatter) gs))))

(defn line
  ([] (graphic {:geometry [:line]}))
  ([g & gs] (bind g (apply blend (line) gs))))

;; Facet

(defn facet
  ([by] (graphic {:mappings {:by by}}))
  ([g by] (nest g (facet by))))

(defn title
  ([t] (graphic {:title t}))
  ([g t & gs] (apply blend (title t) g gs)))

;; Title is a bit different.
;; We only need one title on the graphic.
;; It doesn't need to be inherited.
;; Do we want to create a layer for the title, or put it in `:options` like ggplot?

(declare compile-graphic)

;; Rendering

(defn plot-svg
  [g] (compile-graphic g))

;; At this stage I suspect that a minimal interface requiring explicit calls to `cross`,
;; and `add-child` may be better than threading in the sense of being more obvious and inspectable.
;; If that is the case, more work should be invested there to make it complete.

;; ## A Tree Compiler Implementation

;; This is `plot-basic` revisited to use the tree manipulation helpers.
;; The only point is that it abstracts the hiccup shape away.

(defn coords [data mappings]
  (let [{:keys [x y]} mappings]
    (mapv (juxt x y) data)))

(defn plot-geoms [g]
  (let [{:keys [data mappings geometry]} (attrs g)
        xys (coords data mappings)]
    (for [geom geometry]
      (case geom
        :point (for [[x y] xys]
                 [:circle {:r 2, :cx x, :cy y, :fill "lightblue"}])
        :line [:path {:d (str "M " (str/join ","
                                             (first xys))
                              " L " (str/join " "
                                              (map #(str/join "," %)
                                                   (rest xys))))}]))))

(defn compile-graphic
  ([g] (compile-graphic g nil))
  ([g scope]
   (let [g (update-attrs g #(merge-attrs scope %))]
     (into
      [:g {:roll :graphic}
       (plot-geoms g)]
      (for [child (children g)]
        (compile-graphic child (attrs g)))))))

;; In the future maybe make a multi-method that dispatches by node type.
;; This would smoothly combine non-graphic nodes like native SVG hiccup,
;; or custom nodes that render markdown or something else.

(comment
  (defmulti compile-node first)
  (defmethod compile-node :graphic [[_ attrs & children]]
    [:g (compile-node children)])
  (defmethod compile-node :md [[_ attrs & children]]))


;; ## Conceptual Usage

;; This is just conceptual at this stage, scope passing would be necessary to make them work.
;; I'm listing them here mainly for inspiration and to think about whether we like them.

(def bills-chart
  (-> (data penguins)
      (title "Penguin Bills")
      (xy :bill_length :bill_depth)
      (scatter)
      (color :species)
      (nest (graphic {:mappings {:by :island}}))
      (add-child
       [:path {:d "M20 20 L120 20"
               :stroke "black"
               :stroke-dasharray "4 2"}])
      (plot-svg)))

#_(plot-svg bills-chart)

(def scatter-plot
  (-> (data penguins)
      (xy :bill_length :bill_depth)
      (scatter)))

(def mean-line-data
  [{:avg_length 44.0 :avg_depth 16.5}])

(def mean-line-plot
  (-> (data mean-line-data)
      (xy :avg_length :avg_depth)
      (line)))

(def blended-graphic
  (blend scatter-plot mean-line-plot))

(def crossed-graphic
  (cross scatter-plot (color :species)))

;; ## Conclusion

;; I think that the idea of using a tree of scopes as the intermediary representation
;; is interesting and may be a useful bridge between the Algebra-of-Graphics theory
;; and a practical, familiar structure.

;; I've posted this article a little prematurely to explore some design options for building a plotting library for Clojure.
;; I'd love to hear your thoughts on what parts (if any) make sense to pursue,
;; and what parts to avoid.
