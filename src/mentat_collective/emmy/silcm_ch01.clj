^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Classical Mechanics Chapter One"
                     :quarto {:author   :kloimhardt
                              :type     :post
                              :sidebar  "emmy-fdg"
                              :date     "2025-09-10"
                              :image    "sicm_ch01.png"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.silcm-ch01
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda]]
            [civitas.repl :as repl]))

;; The following examples are taken from the MIT open-access book [Structure and Interpretation of Classical Mechanics (SICM)](https://mitp-content-server.mit.edu/books/content/sectbyfn/books_pres_0/9579/sicm_edition_2.zip/chapter001.html).

^:kindly/hide-code
(def prod true) #_"if you set `prod` to `false`, the browser will be busy calculating, be patient"

^:kindly/hide-code
(def md
  (comp kindly/hide-code kind/md))

(md "Another notebook can be found on the [Road to Reality website](https://reality.mentat.org/essays/reality/introduction#welcome-to-the-road-to-reality!) by [Sam Ritchie](https://roadtoreality.substack.com/p/the-first-executable-essay-is-live), the author (along with [Colin Smith](https://github.com/littleredcomputer)) of [Emmy, the Computer Algebra System](https://emmy.mentat.org).")

;; For interactivity, I have some
;; [SICM graphical puzzles](https://kloimhardt.github.io/cljtiles.html?page=SICM001).

;; In adopting MIT-Scheme's `(define ...)`, I trust that Clojure people will bridge that gap quickly
;; while being sure of the gratitude of all readers of the immutable, dense book.

^:kindly/hide-code
(kind/hiccup
  [:div
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.28-59/dist/scittle.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.28-59/dist/scittle.emmy.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.28-59/dist/scittle.cljs-ajax.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react-dom@18/umd/react-dom.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.28-59/dist/scittle.reagent.js"}]
   [:script {:type "application/x-scittle" :src "scheme.cljc"}]])

^:kindly/hide-code
(defmacro define [& b]
  (list 'do
        (cons 'mentat-collective.emmy.scheme/define b)
        (list 'kind/scittle (list 'quote (cons 'define b)))))

^:kindly/hide-code
(define emmy-env
  '[emmy.env :refer :all :exclude [Lagrange-equations r->p]])

^:kindly/hide-code
(define emmy-lg
  '[emmy.mechanics.lagrange :as lg])

^:kindly/hide-code
(define emmy-mn
  '[emmy.numerical.minimize :as mn])

^{:kindly/hide-code true :kindly/kind kind/hidden}
(do
  (require emmy-env)
  (require emmy-lg)
  (require emmy-mn))

^:kindly/hide-code
(kind/scittle
  '(do
     (require emmy-env)
     (require emmy-lg)
     (require emmy-mn)))

^:kindly/hide-code
(kind/scittle
  '(defn show-expression [e]
     (simplify e)))

^:kindly/hide-code
(kind/scittle
  '(defn show-tex-expression [e]
     (->infix (simplify e))))

^:kindly/hide-code
(def tex (comp kind/tex emmy.expression.render/->TeX simplify))

^:kindly/hide-code
(define show-exp (comp str simplify))

^:kindly/hide-code
(defn reag-comp [b]
  (let [server-erg (show-exp (eval b))]
    (list 'kind/reagent
          [:div (list 'quote
                      (list 'let ['a (list 'show-exp b)]
                            [:div
                             (when (not prod)
                               [:div
                                [:tt 'a]
                                [:p (list 'str (list '= server-erg 'a))]])
                             [:tt server-erg]]))])))

^:kindly/hide-code
(defmacro show-expression [e]
  (if prod
    (list 'simplify e)
    (reag-comp e)))

^:kindly/hide-code
(defmacro show-tex-expression [e]
  (if prod
    (list 'tex e)
    (reag-comp e)))

^:kindly/hide-code
(define velocities velocity)

^:kindly/hide-code
(define coordinates coordinate)

^:kindly/hide-code
(define vector-length count)

^:kindly/hide-code
(define time first)

(define ((L-free-particle mass) local)
  (let ((v (velocity local)))
    (* 1/2 mass (square v))))

(define (test-path t)
  (up (+ (* 4 t) 7)
      (+ (* 3 t) 5)
      (+ (* 2 t) 1)))

(show-tex-expression
  (test-path 't))

(Lagrangian-action (L-free-particle 3.0) test-path 0.0 10.0)

;; Greiner "Classical Mechanics: Systems of Paticles and Hamiltonian Mechanics"

(define ((Lc-free-particle mass c) local)
  (let ((v (velocity local)))
    (* 1/2 mass (square c)
       (- (* (/ 1 (square c))
             (+ (square (ref v 1))
                #_(square (ref v 2))
                #_(square (ref v 3))))
          (square (ref v 0))
          1))))

(define (Lc-test-path s)
  (up s
      (+ (* 4 s) 7)
      (+ (* 3 s) 5)
      (+ (* 2 s) 1)))

(show-tex-expression
  (Lc-test-path 's))

(Lagrangian-action (Lc-free-particle 3.0 1.0) Lc-test-path 0.0 10.0)

;; That's quite nice, let's see where we lost the 30

(show-tex-expression
  ((Gamma test-path) 't))

(define three-path (up (literal-function 'x)
                       (literal-function 'y)
                       (literal-function 'z)))

(show-tex-expression
  ((Gamma three-path) 't))

(show-tex-expression
  ((compose (L-free-particle 'm) (Gamma three-path)) 't))

;; Can you guess the following?

^{:kindly/hide-code true :kindly/kind :kind/hidden}
(show-tex-expression
  ((Gamma Lc-test-path) 's))

(define four-path (up identity
                      (literal-function 'x)
                      (literal-function 'y)
                      (literal-function 'z)))

(show-tex-expression
  ((Gamma four-path) 's))

(show-tex-expression
  ((compose (Lc-free-particle 'm_0 'c) (Gamma four-path)) 's))

(define ((Lagrange-equations Lagrangian) w)
  (- (D (compose ((partial 2) Lagrangian) (Gamma w)))
     (compose ((partial 1) Lagrangian) (Gamma w))))

(show-tex-expression
  (((Lagrange-equations (L-free-particle 'm)) three-path) 't))

(show-tex-expression
  (((Lagrange-equations (Lc-free-particle 'm_0 'c)) four-path) 's))

#_(define Lc-path (up (literal-function 't)
                    (literal-function 'x)
                    (literal-function 'y)
                    (literal-function 'z)))

(define Lc-path (up (literal-function 't)
                    (literal-function 'x)))

(show-tex-expression
  (((Lagrange-equations (Lc-free-particle 'm_0 'c)) Lc-path) 's))

;;finally state the Lagrangian

(show-tex-expression
  ((compose (Lc-free-particle 'm_0 'c) (Gamma Lc-path)) 's))

;; Eq. 21.9 reads as L-p*v = 0
(define ((Lc-Lagrange-constraint Lagrangian) w)
  (let ((gamma  (Gamma w))
        (v (ref gamma 2))
        (L (compose Lagrangian gamma))
        (p (compose ((partial 2) Lagrangian) gamma)))
    (- L (* p v))))

;; This is Eq. 21.9; by multiplicationg this with a constant factor, Eq. 21.12

(show-tex-expression
  (((* (/ 2 (square 'c) 'm_0)
       (Lc-Lagrange-constraint (Lc-free-particle 'm_0 'c)))
    Lc-path)
   's))
(def LL (L-free-particle 'm))

((compose LL (Gamma three-path)) 't)

((compose (+ LL (* ((partial 2) LL) velocity)) (Gamma three-path)) 't)

(define (Lc1-Lagrange-constraint Lagrangian)
  (- Lagrangian (* ((partial 2) Lagrangian) velocity)))

(define Lc1-constr (compose (* (/ 2 (square 'c) 'm_0)
                               (Lc1-Lagrange-constraint (Lc-free-particle 'm_0 'c))) (Gamma Lc-path)))

(show-tex-expression
  (Lc1-constr 's))

(def t-prime (sqrt (+ (- Lc1-constr)
                      (square (ref (ref (Gamma Lc-path) 2) 0)))))

(show-tex-expression
  (t-prime 's))

(show-tex-expression
  ((D t-prime) 's))

(define (Lc-constraint-particle lmda L constraint)
  (+ L (* lmda (constraint L))))

(define (constraint-L lmda m c)
  (Lc-constraint-particle lmda
                          (Lc-free-particle 'm_0 'c)
                          Lc1-Lagrange-constraint))

(show-tex-expression
  ((compose
     (constraint-L 1 'm_0 'c)
     (Gamma Lc-path))
   's))

(def eq1 ((Lagrange-equations (constraint-L 'lambda 'm_0 'c)) Lc-path))

(show-tex-expression
  (eq1 's))
