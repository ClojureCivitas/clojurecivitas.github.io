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

(declare d2)

(defn b0 [body]
  (let [bodymap (group-by (fn [d]
                            (and (coll? d) (= (first d) 'define)))
                          body)]
    (if (seq (bodymap true))
      (let [letvec (into []
                         (mapcat (fn [a]
                                   (let [thedefn (d2 (rest a))
                                         a1 (rest thedefn)]
                                     (if (= (first thedefn) 'def)
                                       (into [] a1)
                                       [(first a1) (cons 'fn (rest a1))])))
                                 (bodymap true)))]
        [(concat
           (list 'let letvec)
           (bodymap false))])
      body)))

(defn unnest [v]
  (if (coll? (first v))
    (let [[head & tail] v]
      (conj (unnest head) (vec tail)))
    [v]))

(defn d0 [h b]
  (let [hh (list 'fn (first h))]
    (if (seq (rest h))
      (concat hh [(d0 (rest h) b)])
      (concat hh b))))

(defn d1 [h b]
  (let [hh (list 'defn (ffirst h) (vec (rest (first h))))]
    (if (seq (rest h))
      (concat hh [(d0 (rest h) b)])
      (concat hh b))))

(defn d2 [[head & body]]
  (if (coll? head)
    (d1 (unnest head) (b0 body))
    (concat (list 'def head) (b0 body))))

(defn pwrp [b]
  (postwalk-replace {'let 'let-scheme} b))

(defmacro define [h & b]
  (if (and (coll? h) (= (first h) 'tex-inspect))
    (list 'do (d2 (concat [(second h)] (pwrp b))) h)
    (d2 (concat [h] (pwrp b)))))

(defmacro let-scheme [b & e]
  (concat (list 'let (into [] (apply concat b)))
          (b0 (pwrp e))))

(defmacro lambda [h & b]
  (concat (list 'fn (into [] h)) (b0 (pwrp b))))

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

  (define emmy-env 3)

  emmy-env

  (define (f1 F)
    (lambda (v)
            (define (g delta)
              (+ delta v F))
            (g 0)))

  ((f1 7) 1)

  (define (f3 x)
    (let ((a 1))
      (define (f4 y) ;; no higher define here
        (lambda (z)
                (let ((b 2))
                  (+ y b z))))
      (+ ((f4 x) x) a)))

(f3 5)

  (let-scheme ((a 1))
    (let ((b 2))
      (+ a b)))

  (define (f3 x)
    (let ((a 1))
      (define ((f4 y) yy)
        (lambda (z)
                (let ((b 2))
                  (+ y b z))))
      (+ (((f4 x) x) x) a)))

  (f3 5)
  :end-comment)

(comment

(def unnest-tests
  [[[[[1 2] 3 4] 5 6]
    [[1 2] [3 4] [5 6]]]
   [[[1 2] 3 4]
    [[1 2] [3 4]]]])

(map (fn [[d e]] (= (unnest d) e)) unnest-tests)

(map (fn [[x _]] (d0 (unnest x) [7 8])) unnest-tests)
;; => ((fn [1 2] (fn [3 4] (fn [5 6] 7 8))) (fn [1 2] (fn [3 4] 7 8)))

(d1 [['name 9 0] [11 12]] [1 2])
;; => (defn name [9 0] (fn [11 12] 1 2))

(d1 [['name 9 0]] [1 2])
;; => (defn name [9 0] 1 2)

(def d2-tests
  [['(a 7)
    '(def a 7)
    ]
   [
    '((a x) x)
    '(defn a [x] x)
    ]
   [
    '((a x) (* 3 x))
    '(defn a [x] (* 3 x))
    ]
   [
    '(((a x) y) (* 3 x y))
    '(defn a [x]
       (fn [y] (* 3 x y)))
    ]
   [
    '(((a x z) y) (* 3 x y z) (+ 4 5))
    '(defn a [x z]
       (fn [y] (* 3 x y z) (+ 4 5)))
    ]
   [
    '(((a x z) y a) (* 7 4 5))
    '(defn a [x z]
       (fn [y a] (* 7 4 5)))
    ]
   [
    '((((a x z) y a) c d e) (* 7 4 5 d))
    '(defn a [x z]
       (fn [y a]
         (fn [c d e]
           (* 7 4 5 d))))
    ]
   ])

(map (fn [[x y]] (= (d2 x) y)) d2-tests)

(def b0-test
  [[[7]
    [7]]
   [['(+ 1 2)]
    ['(+ 1 2)]
    ]
   [['(+ 1 2) '(+ 8 2)]
    ['(+ 1 2) '(+ 8 2)]
    ]
   [['(define (f x) x) '(define (g y) (* 2 y)) '(f (g 3))]
    ['(let [f (fn [x] x)
           g (fn [y] (* 2 y))]
       (f (g 3)))]
    ]
   [['(define (f x) x) '(define y 3) '(f (g y))]
    ['(let [f (fn [x] x)
            y 3]
        (f (g y)))]
    ]
   ])

(map (fn [[x y]] (= (b0 x) y)) b0-test)

(def d2-tests2
  [['((h z) (define (f x) x) (define (g y) (* 2 y)) (* z (f (g 3))) 99)
    '(defn h [z]
       (let [f (fn [x] x)
             g (fn [y] (* 2 y))]
         (* z (f (g 3)))
         99))]
   ['((f x) (define y 1) 7)
    '(defn f [x] (let [y 1] 7))]
   ])

(map (fn [[x y]] (= (d2 x) y)) d2-tests2)

(macroexpand-1 '(define (f x) (define (g y) (let ((a 1)) a))))
;; => (defn f [x] (let [g (fn [y] (let-scheme ((a 1)) a))]))

(clojure.walk/macroexpand-all
  '(define (f x) (define (g y) (let ((a 1)) a))))
;; => (def f (fn* ([x] (let* [g (fn* ([y] (let* [a 1] a)))]))))

(clojure.walk/macroexpand-all
  '(define (f x) (define (g y) (let ((a 1)) (define b a) b))))
;; => (def f (fn* ([x] (let* [g (fn* ([y] (let* [a 1] (let* [b a] b))))]))))

(clojure.walk/macroexpand-all
  '(define (metric->Lagrangian metric coordsys)
     (define (L state)
       (let ((q (ref state 1)) (qd (ref state 2)))
         (define v
           (components->vector-field (lambda (m) qd) coordsys))
         ((* 1/2 (metric v v)) ((point coordsys) q))))
     L))
;; =>
#_(def metric->Lagrangian
  (fn* ([metric coordsys]
        (let* [L (fn* ([state]
                       (let* [q (ref state 1)
                              qd (ref state 2)]
                         (let* [v (components->vector-field
                                    (fn* ([m] qd)) coordsys)]
                           ((* 1/2 (metric v v)) ((point coordsys) q))))))] L))))
  :end-comment)





