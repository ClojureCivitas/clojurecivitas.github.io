^{:kindly/hide-code true
  :clay {:title  "Negative Sets as Data"
         :quarto {:type        :post
                  :author      [:jclaggett]
                  :date        "2026-03-21"
                  :description "Cofinite sets with one sentinel key: all set operations reduce to three map primitives."
                  :category    :math
                  :tags        [:sets :data-structures :maps]
                  :keywords    [:cofinite :negative-sets :set-operations :maps :complement]}}}
(ns math.sets.negative-sets
  (:require [scicloj.kindly.v4.kind :as kind]))

;; Finite sets are everywhere in programming. But sometimes you need
;; the *complement* of a set — "everything except these elements."
;; This comes up in access control, type systems, query filters,
;; permission models, and anywhere you work with open-world assumptions.

;; The usual approach is to introduce a separate type: a tagged union,
;; a wrapper class, or a special algebra with case analysis at every
;; operation. This adds complexity and makes composition harder.

;; This article presents a simpler approach: represent both finite and
;; cofinite sets as plain maps, distinguished by a single sentinel key.
;; All set operations — union, intersection, difference, complement,
;; membership — reduce to three map primitives. No special types or
;; wrapper objects are needed.

;; Clojure obviously offers sets as a built-in type — but they are just a thin
;; wrapper around maps. For the rest of this article we will work directly with
;; maps, since they are the underlying representation of sets. This approach
;; also more clearly shows that the same idea applies to any language with
;; associative data structures.

;; ## The Key Insight

;; A **positive set** is a map where every key maps to itself:

;; ```
;; #{a b c}  →  {a a, b b, c c}
;; ```

;; A **negative set** (cofinite set) represents "everything except these
;; elements." It is the same map structure, but with one additional entry:
;; a distinguished sentinel key `:neg` that maps to itself:

;; ```
;; "everything except a and b"  →  {:neg :neg, a a, b b}
;; ```

;; The sentinel is just data. It flows through map operations like any other
;; key. This is the entire trick.

;; Note that whichever value is used to denote negative sets is no longer
;; available as a normal element so choose wisely.

;; ## Implementation

;; We need exactly three map primitives. These are standard operations
;; available in any language with associative data structures:

(defn map-merge
  "Add B's keys not already in A."
  [a b]
  (merge b a))

(defn map-keep
  "Keep only A's keys that are also in B."
  [a b]
  (select-keys a (keys b)))

(defn map-remove
  "Remove A's keys that are also in B."
  [a b]
  (reduce dissoc a (keys b)))

;; That's it for primitives. Everything else is built from these three.

;; ### Constructing Sets

;; A positive set maps each element to itself:

(defn pos-set
  "Create a positive set from elements."
  [& elements]
  (zipmap elements elements))

;; A negative set is the same, but with the `:neg` sentinel:

(defn neg-set
  "Create a negative set (complement) from excluded elements."
  [& excluded]
  (assoc (apply pos-set excluded) :neg :neg))

;; Some useful constants:

(def empty-set
  "The empty set (positive, no elements)."
  {})

(def universal-set
  "The universal set (negative, no exclusions)."
  {:neg :neg})

;; Note: the empty set is just an empty map. The universal set is
;; `{:neg :neg}` — "everything except nothing."

;; ### Predicates

(defn negative?
  "Is this a negative (cofinite) set?"
  [s]
  (contains? s :neg))

(defn member?
  "Is x a member of set s?"
  [s x]
  (if (negative? s)
    (not (contains? s x))    ;; negative: member if NOT listed
    (contains? s x)))

;; For positive sets, membership is the usual map lookup. For negative
;; sets, the logic inverts — an element is a member if it is *not*
;; present in the exclusion list.

;; Let's verify:

(def vowels (pos-set :a :e :i :o :u))
(def consonants (neg-set :a :e :i :o :u))

^kind/table
{:vowels      {:member-a (member? vowels :a)
               :member-b (member? vowels :b)
               :member-z (member? vowels :z)}
 :consonants  {:member-a (member? consonants :a)
               :member-b (member? consonants :b)
               :member-z (member? consonants :z)}}

;; `vowels` is a positive set containing `:a :e :i :o :u`.
;; `consonants` is a negative set excluding `:a :e :i :o :u` —
;; in other words, every letter *except* the vowels.
;; Same data, same structure, inverted meaning.

;; ### Complement

;; Complementing a set is just toggling the `:neg` key:

(defn complement-set
  "Toggle between positive and negative."
  [s]
  (if (negative? s)
    (dissoc s :neg)
    (assoc s :neg :neg)))

;; This is O(1). No structural rebuild. The `:neg` key acts as a
;; single bit that flips the interpretation of the entire set.

;; Verify that complement round-trips:

(= consonants (complement-set vowels))
(= vowels (complement-set (complement-set vowels)))

;; ## The Operation Table

;; Here is where things get interesting. Every set operation —
;; union, intersection, and difference — can be expressed as one of
;; our three map primitives. Which primitive to use depends only on
;; whether each operand is positive or negative:

^kind/table
[{:A "+" :B "+" :union "(map-merge A B)" :intersect "(map-keep A B)"  :difference "(map-remove A B)"}
 {:A "-" :B "-" :union "(map-keep A B)"  :intersect "(map-merge A B)" :difference "(map-remove B A)"}
 {:A "+" :B "-" :union "(map-remove B A)" :intersect "(map-remove A B)" :difference "(map-keep A B)"}
 {:A "-" :B "+" :union "(map-remove A B)" :intersect "(map-remove B A)" :difference "(map-merge A B)"}]

;; Read each row as: "when A has this polarity and B has that polarity,
;; use this map primitive."

;; Notice the symmetry. merge and keep appear once and remove appears twice in
;; a complementary pattern in each column. The four cases are exhaustive and
;; mutually exclusive — every combination of positive and negative sets is
;; covered. The `neg` sentinel key participates in the map operations
;; naturally, so the result automatically has the correct polarity.

;; ### Why Does This Work?

;; Consider **union of two positive sets**. The union of `{a, b}` and
;; `{b, c}` should be `{a, b, c}`. As maps, this is
;; `merge({a:a, b:b}, {b:b, c:c})` = `{a:a, b:b, c:c}`. ✓

;; Now consider **union of two negative sets**. The union of
;; "everything except {a, b}" and "everything except {b, c}" should be
;; "everything except {b}" — only elements excluded from *both* stay
;; excluded. As maps, this is
;; `keep({neg:neg, a:a, b:b}, {neg:neg, b:b, c:c})`
;; = `{neg:neg, b:b}`. ✓

;; The sentinel `:neg` is in both maps, so `keep` preserves it.
;; The result is still a negative set — correct!

;; For the **mixed cases**: the union of a positive and a negative set
;; is always a negative set (it contains "everything except..." some
;; smaller set). Intersection of a positive and negative set is always
;; positive (it's a filtered subset). The operations work out because
;; the `:neg` key flows through the map primitives naturally.

;; ## Implementation of Set Operations

;; Define a bonus operation: map-remove with the arguments flipped. As shown in
;; the table, this is needed for the negative-negative case of difference:
(defn map-remove-right
  "Remove B's keys that are also in A."
  [a b]
  (map-remove b a))

;; The dispatch is a simple 2×2 case on polarity:

(defn- dispatch
  "Select the map operation based on polarity of a and b.
   ops is [pos-pos, neg-neg, pos-neg, neg-pos]."
  [[pp pn np nn] a b]
  (condp = [(negative? a) (negative? b)]
    [false false] (pp a b)
    [false true] (pn a b)
    [true false] (np a b)
    [true true] (nn a b)))

(def set-union
  "Union of two sets (positive or negative)."
  (partial dispatch [map-merge map-remove-right map-remove map-keep]))

(def set-intersection
  "Intersection of two sets (positive or negative)."
  (partial dispatch [map-keep map-remove map-remove-right map-merge]))

(def set-difference
  "Difference of two sets (positive or negative)."
  (partial dispatch [map-remove map-keep map-merge map-remove-right]))

;; That's the entire implementation. Three primitives, three operations,
;; four cases each. Let's verify with examples.

;; ## Verification

;; ### Positive × Positive

;; The familiar case: ordinary finite sets.

(def abc (pos-set :a :b :c))
(def bcd (pos-set :b :c :d))

^kind/table
[{:operation "A ∪ B" :result (set-union abc bcd)        :expected "{a b c d}"}
 {:operation "A ∩ B" :result (set-intersection abc bcd) :expected "{b c}"}
 {:operation "A \\ B" :result (set-difference abc bcd)  :expected "{a}"}]

;; ### Negative × Negative

;; Two cofinite sets. "Everything except {a,b}" and "everything except {b,c}":

(def not-ab (neg-set :a :b))
(def not-bc (neg-set :b :c))

^kind/table
[{:operation "A ∪ B"
  :result (set-union not-ab not-bc)
  :expected "¬{b} — everything except b"}
 {:operation "A ∩ B"
  :result (set-intersection not-ab not-bc)
  :expected "¬{a b c} — everything except a,b,c"}
 {:operation "A \\ B"
  :result (set-difference not-ab not-bc)
  :expected "{c} — just c"}]

;; That last one is worth pausing on. The difference of two negative
;; sets can produce a *positive* set! "Everything except {a,b}" minus
;; "everything except {b,c}" = "things that are excluded from B but
;; not from A" = `{c}`. The `:neg` sentinel is absent from the result
;; because `remove(B, A)` removes the `:neg` key (present in both).

;; ### Mixed: Positive × Negative

(def ab (pos-set :a :b))
(def not-bc' (neg-set :b :c))

^kind/table
[{:operation "pos ∪ neg"
  :result (set-union ab not-bc')
  :expected "¬{c} — everything except c"}
 {:operation "pos ∩ neg"
  :result (set-intersection ab not-bc')
  :expected "{a} — just a"}
 {:operation "pos \\ neg"
  :result (set-difference ab not-bc')
  :expected "{b} — just b"}]

;; ### Mixed: Negative × Positive

^kind/table
[{:operation "neg ∪ pos"
  :result (set-union not-bc' ab)
  :expected "¬{c} — everything except c"}
 {:operation "neg ∩ pos"
  :result (set-intersection not-bc' ab)
  :expected "{a} — just a"}
 {:operation "neg \\ pos"
  :result (set-difference not-bc' ab)
  :expected "¬{a b c} — everything except a,b,c"}]

;; ## Properties

;; Let's verify some algebraic properties hold:

;; ### De Morgan's Laws

;; `complement(A ∪ B) = complement(A) ∩ complement(B)`

(let [a (pos-set :a :b :c)
      b (pos-set :b :c :d)]
  ^kind/table
  [{:law "complement(A ∪ B) = complement(A) ∩ complement(B)"
    :holds? (= (complement-set (set-union a b))
               (set-intersection (complement-set a) (complement-set b)))}
   {:law "complement(A ∩ B) = complement(A) ∪ complement(B)"
    :holds? (= (complement-set (set-intersection a b))
               (set-union (complement-set a) (complement-set b)))}])

;; ### Identity Elements

^kind/table
[{:law "A ∪ ∅ = A"
  :holds? (= abc (set-union abc empty-set))}
 {:law "A ∩ U = A"
  :holds? (= abc (set-intersection abc universal-set))}
 {:law "A ∪ U = U"
  :holds? (= universal-set (set-union abc universal-set))}
 {:law "A ∩ ∅ = ∅"
  :holds? (= empty-set (set-intersection abc empty-set))}]

;; ### Self-Complement

^kind/table
[{:law "A ∪ complement(A) = U"
  :holds? (= universal-set (set-union abc (complement-set abc)))}
 {:law "A ∩ complement(A) = ∅"
  :holds? (= empty-set (set-intersection abc (complement-set abc)))}
 {:law "complement(∅) = U"
  :holds? (= universal-set (complement-set empty-set))}
 {:law "complement(U) = ∅"
  :holds? (= empty-set (complement-set universal-set))}]

;; All the standard set algebra laws hold. This isn't a coincidence —
;; it's a consequence of the representation being a faithful encoding
;; of Boolean algebra over the power set.

;; ## Why This Matters

;; **It's just maps.** No new types, wrappers or separate code
;; paths. Any system that can store and manipulate maps can represent
;; both finite and cofinite sets. Serialization, hashing, comparison,
;; indexing — they all come for free from the underlying map.

;; **Composition is natural.** Because the sentinel key participates in
;; operations like any other key, you can freely mix positive and
;; negative sets in chains of operations without explicit polarity
;; tracking. The polarity of the result emerges from the operation.

;; **Complement is O(1).** Toggling one key. Not rebuilding a structure,
;; or wrapping in a `Not(...)` node or allocating a new type.

;; **Three primitives are enough.** `merge`, `keep`, `remove` on maps. These
;; are available in every language supporting associative maps. The 4×3
;; dispatch table is small enough to memorize.

;; ## The Broader Context

;; Cofinite sets appear in the literature under various names —
;; co-sets, complementary representations, "open world" sets. They
;; typically require special algebraic treatment. The insight here is
;; that by encoding the polarity *within* the data (as a sentinel key),
;; the algebra reduces to plain map operations.

;; This representation was developed as part of
;; [Dacite](https://github.com/jclaggett/dacite), a content-addressed
;; data structure system. In a content-addressed store, the sentinel
;; key costs zero additional storage — both key and value slots point
;; to the same hash. The set operations compose with the store's
;; existing map operations, requiring no special support.

;; But the idea is general. Anywhere you have maps, you have both
;; finite and cofinite sets.

;; ## Summary

;; One sentinel key. Three map primitives. Complete set algebra.

;; | Concept | Representation |
;; |---------|----------------|
;; | Positive set `{a, b}` | `{a: a, b: b}` |
;; | Negative set "all except {a, b}" | `{:neg :neg, a: a, b: b}` |
;; | Empty set | `{}` |
;; | Universal set | `{:neg :neg}` |
;; | Complement | Toggle `:neg` key |
;; | Membership | pos: `contains? = true` · neg: `contains? = false` |
;; | All operations | Three map primitives × four polarity cases |
