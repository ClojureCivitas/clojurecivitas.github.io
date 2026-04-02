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

(defn define-1 [h b]
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
    (list 'do (define-1 (second h) b) h)
    (define-1 h b)))

(defmacro lambda [h & b]
  (concat (list 'fn (into [] h)) (embrace-define b)))

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



  :end-comment)

(comment
  "does not work, but should"

  (let-scheme ((a 1))
    (let ((b 2))
      (+ a b)))

  (define (f3 x)
    (let ((a 1))
      (define ((f4 y) yy) ;; higher define here is the problem
        (lambda (z)
                (let ((b 2))
                  (+ y b z))))
      (+ (((f4 x) x) x) a)))

  :end-comment)

(comment

(defn unnest [v]
  (if (coll? (first v))
    (let [[head & tail] v]
      (conj (unnest head) (vec tail)))
    [v]))

(def unnest-tests
  [[[[[1 2] 3 4] 5 6]
    [[1 2] [3 4] [5 6]]]
   [[[1 2] 3 4]
    [[1 2] [3 4]]]])

(map (fn [[d e]] (= (unnest d) e)) unnest-tests)

(def cc (unnest aa))

(defn d0 [h b]
  (let [hh (list 'fn (first h))]
    (if (seq (rest h))
      (concat hh [(d0 (rest h) b)])
      (concat hh b))))

(map (fn [[x _]] (d0 (unnest x) [7 8])) unnest-tests)
;; => ((fn [1 2] (fn [3 4] (fn [5 6] 7 8))) (fn [1 2] (fn [3 4] 7 8)))


(defn d1 [h b]
  (let [hh (list 'defn (ffirst h) (vec (rest (first h))))]
    (if (seq (rest h))
      (concat hh [(d0 (rest h) b)])
      (concat hh b))))

(d1 [['name 9 0] [11 12]] [1 2])
;; => (defn name [9 0] (fn [11 12] 1 2))

(d1 [['name 9 0]] [1 2])
;; => (defn name [9 0] 1 2)

(defn d2 [head & body]
  (if (coll? head)
    (d1 (unnest head) body)
    (concat (list 'def head) body)))

(def d2-tests
  [[
    '(define (a x) x)
    '(defn a [x] x)
    ]
   [
    '(define (a x) (* 3 x))
    '(defn a [x] (* 3 x))
    ]
   [
    '(define ((a x) y) (* 3 x y))
    '(defn a [x]
       (fn [y] (* 3 x y)))
    ]
   [
    '(define ((a x z) y) (* 3 x y z))
    '(defn a [x z]
       (fn [y] (* 3 x y z)))
    ]
   [
    '(define ((a x z) y a) (* 7 4 5))
    '(defn a [x z]
       (fn [y a] (* 7 4 5)))
    ]
   [
    '(define (((a x z) y a) c d e) (* 7 4 5 d))
    '(defn a [x z]
       (fn [y a]
         (fn [c d e]
           (* 7 4 5 d))))
    ]
   ])

(map (fn [[x y]] (= (apply d2 (rest x)) y)) d2-tests)

  :end-comment)
