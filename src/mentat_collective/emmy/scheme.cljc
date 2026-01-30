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

(defn define->let [h b1 b2]
  (list 'let
        (vector (first h)
                (list 'fn (into [] (rest h))
                      (last b1)))
        b2))

(defn embrace-define [b]
  (if (and (coll? b) (coll? (first b)) (= (ffirst b) 'define))
    [(define->let (second (first b))
       (embrace-define (rest (rest (first b))))
       (last b))]
    b))

(defmacro let-scheme [b & e]
  (concat (list 'let (into [] (apply concat b)))
          (embrace-define e)))

(defmacro define-1 [h & b]
  (let [body (->> b
                  (postwalk-replace {'let 'let-scheme})
                  (embrace-define))]
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

(comment
  (define (f a b)
    (define (h i j)
      (define (g n m)
        (+ n m))
      (+ (g i j) j))
    (+ (h a b) b))

  (f 1 2)

  (define (fu a b)
    (define (h i j)
      (let ((u 1))
        (define (g n m)
          (+ n m))
        (+ (g i j) u)))
    (+ (h a b) b))

  (fu 1 2)


  (define (g x y)
    (+ 3 4))

  (g 4 5)

  (embrace-define
    ['(define (g i j)
        (define (h n m)
          (+ n m))
        (+ (h i j) 7))
     '(+ (g a b) c d)])

  (define emmy-env 3)

  emmy-env

  :end-comment)
