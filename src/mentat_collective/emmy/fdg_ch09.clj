^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter Nine"
                     :quarto {:author      :kloimhardt
                              :type        :post
                              :draft       true
                              :description "Functional Differential Geometry: Chapter 9"
                              :sidebar     "emmy-fdg"
                              :date        "2026-04-03"
                              :image       "sicm_ch01.png"
                              :category    :libs
                              :tags        [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch09
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial = test])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [let-scheme lambda] :as scheme]
            [civitas.repl :as repl]))

;; ## 9 Metrics
;; We often want to impose further structure on a manifold to allow us to define lengths and angles. This is done by generalizing the idea of the Euclidean dot product, which allows us to compute lengths of vectors and angles between vectors in traditional vector algebra.

^:kindly/hide-code
(def prod true) #_"used to check Emmy in Scittle kitchen"

^:kindly/hide-code
(kind/hiccup scheme/scittle-kitchen-hiccup)

^:kindly/hide-code
(defmacro define [& b]
  (list 'do
        (cons 'scheme/define b)
        (list 'kind/scittle (list 'quote (cons 'define b)))))

^:kindly/hide-code
(define emmy-env
  '[emmy.env :as e :refer :all :exclude [print-expression
                                         define-coordinates
                                         raise contract]])

^:kindly/hide-code
(define emmy-vector-field
  '[emmy.calculus.vector-field :as vf])

^:kindly/hide-code
(define emmy-structure
  '[emmy.structure :as s])

^:kindly/hide-code
(define emmy-aggregate
  '[emmy.util.aggregate :as ua])

^:kindly/hide-code
(define emmy-form-field
  '[emmy.calculus.form-field :as ff])

^:kindly/hide-code
(define emmy-operator
  '[emmy.operator :as o])

^:kindly/hide-code
(define emmy-basis
  '[emmy.calculus.basis :as b])

^{:kindly/hide-code true :kindly/kind kind/hidden}
(do
  (require emmy-env)
  (require emmy-vector-field)
  (require emmy-aggregate)
  (require emmy-form-field)
  (require emmy-structure)
  (require emmy-operator)
  (require emmy-basis))

^:kindly/hide-code
(kind/scittle
  '(do
     (require emmy-env)
     (require emmy-vector-field)
     (require emmy-aggregate)
     (require emmy-form-field)
     (require emmy-structure)
     (require emmy-operator)
     (require emmy-basis)))

^:kindly/hide-code
(defmacro define-coordinates [& b]
  (list 'do
        (cons 'emmy.env/define-coordinates b)
        (list 'kind/scittle (list 'quote (cons 'emmy.env/define-coordinates b)))))

^:kindly/hide-code
(define string-exp (comp str simplify))

^:kindly/hide-code
(defn reag-comp [b]
  (let [server-erg (string-exp (eval b))]
    (list 'kind/reagent
          [:div (list 'quote
                      (list 'let ['a (list 'string-exp b)]
                            [:div
                             (when (not prod)
                               [:div
                                [:tt 'a]
                                [:p (list 'str (list '= server-erg 'a))]])
                             [:tt server-erg]]))])))

^:kindly/hide-code
(defmacro print-expression [e]
  (if prod
    (list 'simplify e)
    (reag-comp e)))

^:kindly/hide-code
(kind/scittle
  '(def print-expression simplify))

^:kindly/hide-code
(def show-tex-fn (comp kind/tex emmy.expression.render/->TeX))

^:kindly/hide-code
(defmacro show-tex [e]
  (if prod
    (list 'show-tex-fn e)
    (reag-comp e)))

^:kindly/hide-code
(kind/scittle
  '(defn show-tex [e]
     (->infix e)))

^:kindly/hide-code
(defn show-expression-fn [e]
  (kind/tex (str "\\boxed{" (emmy.expression.render/->TeX e) "}")))

^:kindly/hide-code
(defmacro show-expression [e]
  (if prod
    (list 'show-expression-fn e)
    (reag-comp e)))

^:kindly/hide-code
(kind/scittle
  '(defn show-expression [e]
     (->infix (simplify e))))

;; lower is already defined in environment

(comment
  (define ((lower metric) u)
    (define (omega v) (metric v u))
    (ff/procedure->oneform-field omega))
  :end-comment)

(define (contract proc basis)
  (let ((vector-basis (basis->vector-basis basis))
        (oneform-basis (basis->oneform-basis basis)))
    (s/sumr proc vector-basis oneform-basis)))

(define (raise metric basis)
  (let ((gi (invert metric basis)))
    (lambda (omega)
            (contract (lambda (e_i w↑i)
                              (* (gi omega w↑i) e_i))
                      basis))))

;; ### Metric Compatibility

(define-coordinates (up theta phi) S2-spherical)

(define ((g-sphere R) u v)
  (* (square R)
     (+ (* (dtheta u) (dtheta v))
        (* (compose (square sin) theta)
           (dphi u)
           (dphi v)))))

(define S2-basis
  (coordinate-system->basis S2-spherical))

(print-expression
  ((Christoffel->symbols
     (metric->Christoffel-1 (g-sphere 'R) S2-basis))
   ((point S2-spherical) (up 'theta0 'phi0))))

(print-expression
  ((Christoffel->symbols
     (metric->Christoffel-2 (g-sphere 'R) S2-basis))
   ((point S2-spherical) (up 'theta0 'phi0))))

;; ### Metrics and Lagrange Equations

(define (metric->Lagrangian metric coordsys)
  (define (L state)
    (let ((q (ref state 1)) (qd (ref state 2)))
      (define v
        (components->vector-field (lambda (m) qd) coordsys))
      ((* 1/2 (metric v v)) ((point coordsys) q))))
  L)

(define (Lagrange-explicit L)
  (let ((P ((partial 2) L))
        (F ((partial 1) L)))
    (/ (- F (+ ((partial 0) P)
               (* ((partial 1) P) velocity)))
       ((partial 2) P))))

(print-expression
  (let-scheme ((metric (literal-metric 'g R3-rect))
               (q (typical-coords R3-rect))
               (L2 (metric->Lagrangian metric R3-rect)))
    (+ (* 1/2
          (((expt (partial 2) 2) (Lagrange-explicit L2))
           (up 't q (corresponding-velocities q))))
       ((Christoffel->symbols
          (metric->Christoffel-2 metric
                                 (coordinate-system->basis R3-rect)))
        ((point R3-rect) q)))))

;; ### For Two Dimensions

(define L2
  (metric->Lagrangian (literal-metric 'm R2-rect)
                      R2-rect))

(define (L1 state)
  (sqrt (* 2 (L2 state))))

(print-expression
  (determinant
    (((partial 2) ((partial 2) L2))
     (up 't (up 'x 'y) (up 'vx 'vy)))))

(print-expression
  (determinant
    (((partial 2) ((partial 2) L1))
     (up 't (up 'x 'y) (up 'vx 'vy)))))

(define (L1 state)
  (sqrt (square (velocity state))))

(print-expression
  (((Lagrange-equations L1)
    (up (literal-function 'x) (literal-function 'y)))
   't))

;; ### Reparametrization

(print-expression
  (let-scheme ((x (literal-function 'x))
               (y (literal-function 'y))
               (f (literal-function 'f))
               (E1 (Euler-Lagrange-operator L1)))
    ((- (compose E1
                 (Gamma (up (compose x f)
                            (compose y f))
                        4))
        (* (compose E1
                    (Gamma (up x y) 4)
                    f)
           (D f)))
     't)))

(print-expression
  (let-scheme ((q (up (literal-function 'x) (literal-function 'y)))
               (f (literal-function 'f)))
    ((- (compose (Euler-Lagrange-operator L2)
                 (Gamma (compose q f) 4))
        (* (compose (Euler-Lagrange-operator L2)
                    (Gamma q 4)
                    f)
           (expt (D f) 2)))
     't)))

;; ### Exercise 9.3: Curvature of a Spherical Surface

(define M (make-manifold S2-type 2 3))

(define spherical
  (coordinate-system-at M :spherical :north-pole))

(define-coordinates (up theta phi) spherical)

(define spherical-basis (coordinate-system->basis spherical))

(define ((spherical-metric r) v1 v2)
  (* (square r)
     (+ (* (dtheta v1) (dtheta v2))
        (* (square (sin theta))
           (dphi v1) (dphi v2)))))

;; [KLM:] the below trace2down is too simple for further use
;; you need to revert to e/trace2down for actual use

(define ((trace2down-simple metric-tensor basis) tensor)
  (let ((inverse-metric-tensor
          (invert metric-tensor basis)))
    (contract
      (lambda (v1 w1)
              (contract
                (lambda (v w)
                        (* (inverse-metric-tensor w1 w)
                           (tensor v v1)))
                basis))
      basis)))

;; ### General Relativity

;; ### Exercise 9.5: Newton’s Equations

(define-coordinates [t x y z] spacetime-rect)

(define spacetime-rect-basis
  (coordinate-system->basis spacetime-rect))

(define (Newton-metric M G c V)
  (let ((a
          (+ 1 (* (/ 2 (square c))
                  (compose V (up x y z))))))
    (define (g v1 v2)
      (+ (* -1 (square c) a (dt v1) (dt v2))
         (* (dx v1) (dx v2))
         (* (dy v1) (dy v2))
         (* (dz v1) (dz v2))))
    g))

(define (Newton-connection M G c V)
  (Christoffel->Cartan
    (metric->Christoffel-2 (Newton-metric M G c V)
                           spacetime-rect-basis)))

(define V (literal-function 'V '(-> (UP Real Real Real) Real)))
(define nabla
  (covariant-derivative
    (Newton-connection 'M 'G 'c (literal-function 'V '(-> (UP Real Real Real) Real)))))

(print-expression
  (((Ricci nabla (coordinate-system->basis spacetime-rect))
    d:dt d:dt)
   ((point spacetime-rect) (up 't 'x 'y 'z))))

(print-expression
  (+ (((partial 0) ((partial 0) V)) (up 'x 'y 'z))
     (((partial 1) ((partial 1) V)) (up 'x 'y 'z))
     (((partial 2) ((partial 2) V)) (up 'x 'y 'z))))

(define (Tdust rho)
  (define (T w1 w2)
    (* rho (w1 d:dt) (w2 d:dt)))
  T)

(print-expression
  (let-scheme ((g (Newton-metric 'M 'G 'c V)))
    (let ((T_ij ((drop2 g spacetime-rect-basis) (Tdust 'rho))))
      (let ((T ((e/trace2down g spacetime-rect-basis) T_ij)))
        ((- (T_ij d:dt d:dt) (* (/ 1 2) T (g d:dt d:dt)))
         ((point spacetime-rect) (up 't 'x 'y 'z)))))))

;; ### Exercise 9.6: Curvature of Schwarzschild Spacetime

(define-coordinates (up t r theta phi) spacetime-sphere)

(define (Schwarzschild-metric M G c)
  (let ((a (- 1 (/ (* 2 G M) (* (square c) r)))))
    (lambda (v1 v2)
            (+ (* -1 (square c) a (dt v1) (dt v2))
               (* (/ 1 a) (dr v1) (dr v2))
               (* (square r)
                  (+ (* (dtheta v1) (dtheta v2))
                     (* (square (sin theta))
                        (dphi v1) (dphi v2))))))))


;; ### Exercise 9.7: Circular Orbits in Schwarzschild Spacetime

(define (prime-meridian r omega)
  (compose (point spacetime-sphere)
           (lambda (t) (up t r (* omega t) 0))
           (chart R1-rect)))

;; ### Exercise 9.9: Friedmann-Lemaître-Robertson-Walker

(define (Einstein coordinate-system metric-tensor)
  (let ((basis (coordinate-system->basis coordinate-system))
         (connection
           (Christoffel->Cartan
             (metric->Christoffel-2 metric-tensor basis)))
         (nabla (covariant-derivative connection))
         (Ricci-tensor (Ricci nabla basis))
         (Ricci-scalar
           ((trace2down metric-tensor basis) Ricci-tensor)))
    (define (Einstein-tensor v1 v2)
      (- (Ricci-tensor v1 v2)
         (* 1/2 Ricci-scalar (metric-tensor v1 v2))))
    Einstein-tensor))

(define (Einstein-field-equation
          coordinate-system metric-tensor Lambda stress-energy-tensor)
  (let ((Einstein-tensor
          (Einstein coordinate-system metric-tensor)))
    (define EFE-residuals
      (- (+ Einstein-tensor (* Lambda metric-tensor))
         (* (/ (* 8 'pi 'G) (expt 'c 4))
            stress-energy-tensor)))
    EFE-residuals))


(define (FLRW-metric c k R)
  (let ((a (/ (square (compose R t)) (- 1 (* k (square r)))))
        (b (square (* (compose R t) r))))
    (define (g v1 v2)
      (+ (* -1 (square c) (dt v1) (dt v2))
         (* a (dr v1) (dr v2))
         (* b (+ (* (dtheta v1) (dtheta v2))
                 (* (square (sin theta))
                    (dphi v1) (dphi v2))))))
    g))

(define (Tperfect-fluid rho p c metric)
  (let ((basis (coordinate-system->basis spacetime-sphere))
         (inverse-metric (metric:invert metric basis)))
    (define (T w1 w2)
      (+ (* (+ (compose rho t)
               (/ (compose p t) (square c)))
            (w1 d:dt) (w2 d:dt))
         (* (compose p t) (inverse-metric w1 w2))))
    T))

(repl/scittle-sidebar)
