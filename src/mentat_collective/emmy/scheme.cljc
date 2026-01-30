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

(def scittle-kitchen-hiccup
  [:div
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.30-64/dist/scittle.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.30-64/dist/scittle.emmy.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.30-64/dist/scittle.cljs-ajax.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react-dom@18/umd/react-dom.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.30-64/dist/scittle.reagent.js"}]
   [:script {:type "application/x-scittle" :src "scheme.cljc"}]])


(defn define->let [h b1 b2]
  (list 'let (list (list (first h) (list 'lambda (rest h) b1))) b2))

(define->let '(g i j) '(plus i j) '(plus 4 5))

(defn embrace-define [h & b]
  (if (= (ffirst b) 'define)
    (define->let (nth (first b) 1) (nth (first b) 2) (last b))
    b))

(embrace-define '(f a b c d)
   '(define (g i j)
     (+ i j))
   '(+ (g a b) c d))
