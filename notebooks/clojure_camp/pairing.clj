^{:clay
  {:quarto {:title    "A few examples of Transducers"
            :type     :post
            :author   [:seancorfield :timothypratley]
            :date     "2025-05-30"
            :category :clojure
            :tags     [:transducers :lazy-sequences :xforms]
            :keywords [:transducers]}}}
(ns clojure-camp.pairing)

;; let's work on a post together!

;; this produces a lazy sequence of strings:
(map #(str "Hello, " % "!") ["Alice" "Bob" "Charlie"])

;; here's the non-lazy transducer-based version:
(into []
      (map #(str "Hello, " % "!"))
      ["Alice" "Bob" "Charlie"])

;; map with a single function argument produces a transducer:
(map #(str "Hello, " % "!"))

;; we can build up xforms (transformers) using the `comp` function:
(def xf (comp (filter #(> (count %) 3))
              (map #(str "Hello, " % "!"))))

;; and now use it with `into`:
(into []
      xf ; Bob gets filtered out
      ["Alice" "Bob" "Charlie"])

;; "traditional" SQL-like rows of columns:
^:kind/table
[{:name "Alice" :age 30 :city "Wonderland"}
 {:name "Bob" :age 25 :city "Builderland"}
 {:name "Charlie" :age 35 :city "Chocolate Factory"}]

;; more columnar format:
^:kind/table
{:name ["Alice" "Bob" "Charlie"]
 :age [30 25 35]
 :city ["Wonderland" "Builderland" "Chocolate Factory"]}

;; and display it as a portal widget:
^:kind/portal
{:name ["Alice" "Bob" "Charlie"]
 :age [30 25 35]
 :city ["Wonderland" "Builderland" "Chocolate Factory"]}
