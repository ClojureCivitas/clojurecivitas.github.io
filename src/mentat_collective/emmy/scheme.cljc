(ns mentat-collective.emmy.scheme)

(defn walk [inner outer form]
  (cond
    (list? form) (outer (apply list (map inner form)))
    (seq? form)  (outer (doall (map inner form)))
    (coll? form) (outer (into (empty form) (map inner form)))
    :else        (outer form)))

(defn postwalk [f form]
  (walk (partial postwalk f) f form))

(defn postwalk-replace [smap form]
  (postwalk (fn [x] (if (contains? smap x) (smap x) x)) form))

(defmacro let-scheme [b & e]
  (concat (list 'let (into [] (apply concat b))) e))

(defmacro define-1 [h & b]
  (let [body (postwalk-replace {'let 'let-scheme} b)]
    (if (coll? h)
      (if (coll? (first h))
        (list 'defn (ffirst h) (into [] (rest (first h)))
              (concat (list 'fn (into [] (rest h))) body))
        (concat (list 'defn (first h) (into [] (rest h)))
                body))
      (concat (list 'def h) body))))

(defmacro define [h & b]
  (if (and (coll? h) (= (first h) 'tex-inspect))
    (list 'do
          (concat ['define-1 (second h)] b)
          h)
    (concat ['define-1 h] b)))

(defmacro lambda [h b]
  (list 'fn (into [] h) b))
