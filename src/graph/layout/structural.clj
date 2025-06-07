^{:clay {:quarto {:draft true}}}
(ns graph.layout.structural)

;; problem: graph layout algorithms return x, y, but remove the other data from nodes
;; the x,y need to be merged back into the graph.
;; idea: Structural merge (like Reagent, respecting keys) can combine layout and data

;; Reagent:
;; define component f (data)
;;
;; =>
;; f1 [[:div {:key 1} "apples"]
;;     [:div {:key 2} "pear"] ]
;;
;; =>
;; f2 [[:div {:key 2} "pears"]
;;     [:div {:key 1} "apples] ]
;; compute the updates,
;; with the keys: swap the dom elements and update pear to pears
;; without the keys: replace everything
;;

(defn pairs [coll]
  (map-indexed (fn [i x]
                 (or
                   (and (map? x) (contains? x :id) [[:id (get x :id)] x])
                   (and (map? x) (contains? x :key) [[:key (get x :key)] x])
                   (and (map? x) (contains? x :idx) [[:key (get x :idx)] x])
                   [[:idx i] x]))
               coll))

(defn merge-structure [a b]
  (cond
    (and (map? a) (map? b))
    (merge-with merge-structure a b)

    (and (sequential? a) (sequential? b))
    (let [a-pairs (pairs a)
          a-keys (into #{} (map first a-pairs))
          b-pairs (pairs b)
          b-map (into {} b-pairs)
          extra (for [[k v] b-pairs
                      :when (not (contains? a-keys k))]
                  v)]
      (-> (into (empty a)
                (map (fn [[k v]]
                       (merge-structure v (get b-map k))))
                a-pairs)
          (into extra)))

    :else (or b a)))

(def g
  {:a [{:key 1 :happy "yes"}
       {:key 2 :happy "no"}]})

(def layout
  {:a [{:key 2 :happy "absolutely" :x 1 :y 2}]})

(merge-structure g layout)
;; => {:a [{:key 1, :happy "yes"}
;;         {:key 2, :happy "absolutely", :x 1, :y 2}]}

;; Mixed keys and unkeys in sequences
(merge-structure
  {:nodes [{:key 1 :a "A1"}
           "raw"
           {:key 2 :b "B1"}]}
  {:nodes [{:key 2 :b "B2"}
           "raw-updated"
           {:key 1 :a "A2" :extra true}]})
;; => {:nodes [{:key 1 :a "A2" :extra true}
;         "raw-updated"
;         {:key 2 :b "B2"}]}

;; Sequences of unequal length, no keys
(merge-structure
  {:list [1 2 3]}
  {:list [10 20]})
;; => {:list [10 20 3]}

;; Deeply nested structure with partial key use
(merge-structure
  {:tree {:children [{:key 1 :label "A"}
                     {:label "B"}]}}
  {:tree {:children [{:label "A+" :x 1 :y 1}
                     {:label "B+" :x 2}]}})
;; => {:tree {:children [{:key 1 :label "A+" :x 1 :y 1}
;                        {:label "B+" :x 2}]}}

;; Vectors inside maps, mixed nesting
(merge-structure
  {:grid {:rows [[{:key :a :val 1}]
                 [{:key :b :val 2}]]}}
  {:grid {:rows [[{:key :a :x 5}]
                 [{:key :b :val 22 :y 9}]]}})
;; => {:grid {:rows [[{:key :a :val 1 :x 5}]
;;                   [{:key :b :val 22 :y 9}]]]}}

;; Mismatched structures — map vs primitive
(merge-structure
  {:a {:nested "yes"}}
  {:a "overwrite"})
;; => {:a "overwrite"}

;; List vs vector: compatible sequences
(merge-structure
  {:x [{:key 1 :val "v"}]}
  {:x '({:key 1 :extra true})})
;; => {:x [{:key 1 :val "v" :extra true}]}

;; Terminal values — nil handling
(merge-structure
  {:value "keep"}
  {:value nil})
;; => {:value "keep"}

;; Mismatched structure — missing keys
(merge-structure
  {:data [{:key 1 :a 1} {:key 2 :b 2}]}
  {:data [{:c 3}]})
;; => {:data [{:key 1 :a 1 :c 3}
;;            {:key 2 :b 2}]}

(defn merge-replace [a b]
  (cond
    (and (map? a) (map? b))
    (merge-with merge-replace a b)

    (and (sequential? a) (sequential? b))
    (let [a-map (pairs a)
          b-map (pairs b)
          keys  (distinct (concat (keys a-map) (keys b-map)))]
      (mapv (fn [k]
              (if (contains? b-map k)
                (merge-replace (get a-map k) (get b-map k))
                (get a-map k)))
            keys))

    ;; fallback
    :else b))
(merge-replace
  {:a [{:key 1 :value "old"} {:key 2 :value "old"}]
   :b {:deep {:z 1}}}
  {:a [{:key 2 :value "new"}]
   :b {:deep {:z 99}}})
;; => {:a [{:key 1 :value "old"} {:key 2 :value "new"}]
;;     :b {:deep {:z 99}}}



(defn merge-dissoc [a removals]
  (cond
    (and (map? a) (map? removals))
    (reduce-kv
      (fn [acc k rem]
        (if (contains? acc k)
          (let [val (get acc k)]
            (cond
              (and (map? val) (map? rem))
              (assoc acc k (merge-dissoc val rem))

              (and (sequential? val) (sequential? rem))
              (assoc acc k (merge-dissoc val rem))

              :else
              (dissoc acc k)))
          acc))
      a removals)

    (and (sequential? a) (sequential? removals))
    (let [a-map (pairs a)
          r-map (pairs removals)
          result (reduce-kv
                   (fn [m k v]
                     (if (contains? m k)
                       (let [val (get m k)]
                         (cond
                           (and (map? val) (map? v))
                           (assoc m k (merge-dissoc val v))

                           (and (sequential? val) (sequential? v))
                           (assoc m k (merge-dissoc val v))

                           :else
                           (dissoc m k)))
                       m))
                   a-map r-map)]
      (->> (sort-by key result)                             ; preserve order
           (mapv val)))

    :else a))                                               ; leave unchanged if shape doesn't match

(merge-dissoc
  {:a [{:key 1 :foo "yes" :bar 1}
       {:key 2 :foo "no"}]
   :b "keep me"}
  {:a [{:key 2 :foo "remove this"}]})
;; => {:a [{:key 1 :foo "yes" :bar 1}
;;         {:key 2}]
;;     :b "keep me"}
