^{:kindly/hide-code true
  :clay {:title "The Algebra of Data"
         :quarto {:type :post
                  :author [:timothypratley]
                  :draft true
                  :date "2025-12-29"
                  :description "Exploring algebraic operators for generic data"
                  :category :data-visualization
                  :tags [:datavis]
                  :keywords [:datavis]}}}
(ns data-visualization.aog.algebra-of-data)

;; Inspired by [the algebra of graphics](../aog_in_clojure_part1.html).

;; ## Axioms

;; We shall take it as given that we are interested in combining sequence of maps:

[{:theme "dark"} {:font "serif"}]

;; AoG calls the maps "layers", but in this article I'll call them "configs".

;; ## Transitive operators

;; One way to combine configs is to concatenate them:

(def add
  (comp vec concat))

(add [{:theme "dark"}]
     [{:font "serif"}])

;; Another thing we can do is a cartesian merge:

(defn cross-merge
  ;; Given 2 sequences, merge all combinations
  ([xs ys]
   (vec (for [x xs
              y ys]
          (merge x y))))
  ;; Extended to work with multiple arguments
  ([xs ys & rest]
   (reduce cross-merge (cross-merge xs ys) rest))
  ([xs] xs)
  ([] []))

;; The standard case of cross-merging 2 configs

(cross-merge [{:theme "dark"}]
             [{:font "serif"}])

;; Extended to 3 configs

(cross-merge [{:theme "dark"}]
             [{:font "serif"}]
             [{:x :customer :y :order-count}])

;; ## Why is this interesting?

;; In short we can specify a larger structure with overlapping concerns concisely

(def a [{:theme "dark" :color "black"}])
(def b [{:theme "light" :color "white"}])
(def c [{:font "serif" :size 12}])
(def d [{:font "sans-serif" :size 14}])

(cross-merge (add a b) (add c d))

;; We gained some expressive power.

;; ## Constructors

;; Given we like configs due to their combinatorial expressiveness,
;; we may introduce some constructors to help us prepare configs concisely.

(defn theme [name color]
  [{:theme name :color color}])

(theme "dark" "black")

(defn font [name color]
  [{:font name :size color}])

(font "serif" 12)

(defn dims [x y]
  [{:x x :y y}])

(dims :customers :orders)

;; Now we can start combining our constructors and operators

(cross-merge (add (theme "dark" "black")
                  (font "serif" 12))
             (dims :customers :orders))

;; ## Threading

;; Rather than constructors, we could define "methods"
;; that accept configs, and cross-merge them with constructed configs.

(defn dims* [configs x y]
  (cross-merge configs [{:x x :y y}]))

(defn theme* [configs name color]
  (cross-merge configs [{:theme name :color color}]))

(defn font* [configs name color]
  (cross-merge configs [{:font name :size color}]))

(-> (theme "dark" "black")
    (font* "serif" 12))

;; A key collision implies addition
;; TODO: explain this idea in more detail,
;; TLDR: threading can represent almost everything

;; ## Unification

;; The most obvious thing we can do with "configs" is merge them together.

(def configs
  (cross-merge (add (theme "dark" "black")
                    (font "serif" 12))
               (add (dims :customers :orders)
                    (dims :products :orders))))

configs

(apply merge configs)

;; But doing so loses information (specifically additions).
;; Rather we might wish to detect conflicts where they occur.

(apply merge-with list configs)

;; Convert config map values into sets so merging can unify
;; multiple possible values per key without duplicates.
;; This is a simple form of "unification": collapsing alternatives.
(defn normalize-to-sets [m]
  (update-vals m hash-set))

;; Merge a sequence of config maps, unifying values as sets
;; (flat, unique per key).
(defn merge-as-sets [configs]
  (apply merge-with clojure.set/union
    (map normalize-to-sets configs)))

(merge-as-sets configs)

;; Why is this interesting?
;; Well in a chart for example we will have some properties
;; that are global, and some that are "layered".
;; This just demonstrates one process to convert an algebraic representation
;; into a structured representation.

;; ## Conclusion

;; I think these ideas are more broadly applicable than just graphics.
;; The only domain specific part about them is defining useful constructors,
;; and the unification behaviors.
