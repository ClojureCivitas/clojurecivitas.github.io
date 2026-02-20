^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter Seven"
                     :quarto {:author      :kloimhardt
                              :type        :draft
                              :description "Functional Differential Geometry: Chapter 7"
                              :sidebar     "emmy-fdg"
                              :date        "2026-02-20"
                              :image       "sicm_ch01.png"
                              :category    :libs
                              :tags        [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch07
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda] :as scheme]
            [civitas.repl :as repl]))

;; ## 7 Directional Derivatives
;; The vector field was a generalization of the directional derivative to functions on a manifold.

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
                                         phi]])

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
(define form-field
  '[emmy.calculus.form-field :as ff])


^{:kindly/hide-code true :kindly/kind kind/hidden}
(do
  (require emmy-env)
  (require emmy-vector-field)
  (require emmy-aggregate)
  (require form-field)
  (require emmy-structure))

^:kindly/hide-code
(kind/scittle
  '(do
     (require emmy-env)
     (require emmy-vector-field)
     (require emmy-aggregate)
     (require form-field)
     (require emmy-structure)))

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

(define ((F->directional-derivative F) v)
  (lambda (u)
          (lambda (f)
                  (lambda (m)
                          (define (g delta)
                            (- ((u f) m) (((((F v) delta) u) f) m)))
                          ((D g) 0)))))

;; ### 7.1 Lie Derivative
;; ### Vector Fields

;; the implementation of scheme/define does not support (((define x) y) z)

(define ((F-Lie phi) v)
  (lambda (delta)
          (pushforward-vector ((phi v) delta) ((phi v) (- delta)))))

(define ((phi coordsys order) v)
  (lambda (delta)
          (lambda (m)
                  ((point coordsys)
                   (series:sum (((exp (* delta v)) (chart coordsys)) m) order)))))

(define (Lie-directional coordsys order)
  (let ((Phi (phi coordsys order)))
    (F->directional-derivative (F-Lie Phi))))

(print-expression
  (let-scheme ((v (literal-vector-field 'v-rect R3-rect))
               (w (literal-vector-field 'w-rect R3-rect))
               (f (literal-manifold-function 'f-rect R3-rect)))
    ((- ((((Lie-directional R3-rect 2) v) w) f)
        ((commutator v w) f))
     ((point R3-rect) (up 'x0 'y0 'z0)))))

(define ((Lie-derivative-vector V) Y) (commutator V Y))

;; ### Properties of the Lie Derivative

(define a (literal-manifold-function 'alpha R3-rect))
(define b (literal-manifold-function 'beta R3-rect))
(define c (literal-manifold-function 'gamma R3-rect))
(define-coordinates (up x y z) R3-rect)

(define theta (+ (* a dx) (* b dy) (* c dz)))

(define omega
  (+ (* a (wedge dy dz))
     (* b (wedge dz dx))
     (* c (wedge dx dy))))

(define X (literal-vector-field 'X-rect R3-rect))
(define Y (literal-vector-field 'Y-rect R3-rect))
(define Z (literal-vector-field 'Z-rect R3-rect))
(define V (literal-vector-field 'V-rect R3-rect))

(define R3-rect-point
  ((point R3-rect) (up 'x0 'y0 'z0)))

(print-expression
  (((- ((Lie-derivative V) (d theta)) (d ((Lie-derivative V) theta)))
    X Y)
   R3-rect-point))

(print-expression
  (((- ((Lie-derivative V) (d omega)) (d ((Lie-derivative V) omega)))
    X Y Z)
   R3-rect-point))

(print-expression
  ((((- (commutator (Lie-derivative X) (Lie-derivative Y)) (Lie-derivative (commutator X Y)))
     theta) Z)
   R3-rect-point))

(print-expression
  ((((- (commutator (Lie-derivative X) (Lie-derivative Y)) (Lie-derivative (commutator X Y)))
     omega) Z V)
   R3-rect-point))

(define Jz (- (* x d:dy) (* y d:dx)))

(print-expression
  (vec (take 5
             ((((exp (* 'a (Lie-derivative Jz))) d:dy)
               (literal-manifold-function 'f-rect R3-rect))
              ((point R3-rect) (up 1 0 0))))))

(define ((L1 X) omega)
  (+ ((interior-product X) (d omega))
     (d ((interior-product X) omega))))

(print-expression
  ((- (((Lie-derivative X) omega) Y Z) (((L1 X) omega) Y Z))
   ((point R3-rect) (up 'x0 'y0 'z0))))

(define ((F-parallel omega phi coordsys) v)
  (lambda (delta)
          (lambda (u)
                  (lambda ( f)
                          (lambda (m)
                                  (let ((basis (coordinate-system->basis coordsys)))
                                    (let ((etilde (basis->oneform-basis basis))
                                          (e (basis->vector-basis basis)))
                                      (let ((m0 (((phi v) (- delta)) m)))
                                        (let ((Aij (+ (identity-like ((omega v) m0))
                                                      (* delta (- ((omega v) m0)))))
                                              (ui ((etilde u) m0)))
                                          (* ((e f) m) (* Aij ui)))))))))))

(define (covariant-derivative-vector omega coordsys order)
  (let ((Phi (phi coordsys order)))
    (F->directional-derivative
      (F-parallel omega Phi coordsys))))

(define ((covariant-derivative-vector Cartan) V)
  (lambda (U)
          (lambda (f)
                  (let ((basis (Cartan->basis Cartan))
                        (Cartan-forms (Cartan->forms Cartan)))
                    (let ((vector-basis (basis->vector-basis basis))
                          (oneform-basis (basis->oneform-basis basis)))
                      (let ((u-components (oneform-basis U)))
                        (* (vector-basis f)
                           (+ (V u-components)
                              (* (Cartan-forms V) u-components)))))))))

(define ((covariant-derivative-oneform Cartan) V)
  (lambda (tau)
          (lambda (U)
                  (let ((nabla-V ((covariant-derivative-vector Cartan) V)))
                    (- (V (tau U)) (tau (nabla-V U)))))))

;; the definition of get-rank, sigma and list-with-substituted-coord does work differtly in clj

(comment
  (define ((((covariant-derivative-form Cartan) V) tau) vs)
    (let ((k (get-rank tau))
          (nabla V ((covariant-derivative-vector Cartan) V)))
      (- (V (apply tau vs))
         (sigma (lambda (i) (apply tau
                                   (list-with-substituted-coord vs i (nabla V (list-ref vs i)))))
                0 (- k 1)))))
  :end-comment)

;; doesn not work in cljs ff/procedure->oneform-field

(comment
  (define (Cartan-transform Cartan basis-prime)
    (let ((basis (Cartan->basis Cartan))
          (forms (Cartan->forms Cartan))
          (prime-dual-basis (basis->oneform-basis basis-prime))
          (prime-vector-basis (basis->vector-basis basis-prime)))
      (let ((vector-basis (basis->vector-basis basis))
            (oneform-basis (basis->oneform-basis basis)))
        (let ((J-inv (s/mapr oneform-basis prime-vector-basis))
              (J (s/mapr prime-dual-basis vector-basis)))
          (let ((omega-prime-forms
                  (ff/procedure->oneform-field
                    (lambda (v)
                            (+ (* J (v J-inv))
                               (* J (* (forms v) J-inv))))
                    'omega-prime-forms)))
            (make-Cartan omega-prime-forms basis-prime))))))

  :end-comment)

(define R2-rect-basis (coordinate-system->basis R2-rect))
(define R2-polar-basis (coordinate-system->basis R2-polar))
(define-coordinates (up x y) R2-rect)
(define-coordinates (up r theta) R2-polar)

(define R2-rect-Christoffel
  (make-Christoffel
    (let ((zero (lambda (m) 0)))
      (down (down (up zero zero)
                  (up zero zero))
            (down (up zero zero)
                  (up zero zero))))
    R2-rect-basis))

(define R2-rect-Cartan
  (Christoffel->Cartan R2-rect-Christoffel))

(define R2-polar-Cartan
  (Cartan-transform R2-rect-Cartan R2-polar-basis))

(define circular (- (* x d:dy) (* y d:dx)))

(define f (literal-manifold-function 'f-rect R2-rect))
(define R2-rect-point ((point R2-rect) (up 'x0 'y0)))

(print-expression
  (((((covariant-derivative R2-rect-Cartan) d:dx)
     circular)
    f)
   R2-rect-point))

(print-expression
  ((d:dy f) R2-rect-point))

(print-expression
  (((((covariant-derivative R2-polar-Cartan) d:dx) circular) f)
   R2-rect-point))

(define V (literal-vector-field 'V-rect R2-rect))
(define W (literal-vector-field 'W-rect R2-rect))

(print-expression
  (((((- (covariant-derivative R2-rect-Cartan)
         (covariant-derivative R2-polar-Cartan))
      V)
     W)
    f)
   R2-rect-point))

;; ### 7.3 Parallel Transport

;; S2-spherical is in the environment

(comment
  (define sphere (make-manifold S2-type 2 3))

  (define S2-spherical
    (coordinate-system-at sphere :spherical :north-pole))

  :end-comment)

(define-coordinates t R1-rect)

(define-coordinates (up theta phi) S2-spherical)

(define S2-basis
  (coordinate-system->basis S2-spherical))

(define gamma
  (compose (point S2-spherical)
           (up (literal-function 'alpha)
               (literal-function 'beta))
           (chart R1-rect)))

(define basis-over-gamma
  (basis->basis-over-map gamma S2-basis))

(define u_gamma
  (* (up (compose (literal-function 'u↑0)
                  (chart R1-rect))
         (compose (literal-function 'u↑1 )
                  (chart R1-rect)))
     (basis->vector-basis basis-over-gamma)))

(define (S2-Christoffel basis theta)
  (let ((zero zero-manifold-function))
    (make-Christoffel
      (down (down
              (up zero zero)
              (up zero (/ 1 (tan theta))))
            (down
              (up zero (/ 1 (tan theta)))
              (up (- (* (sin theta)
                        (cos theta)))
                  zero)))
      basis)))

(define sphere-Cartan (Christoffel->Cartan (S2-Christoffel S2-basis theta)))

(print-expression
  (mapr
    (lambda (omega)
            ((omega
               (((covariant-derivative sphere-Cartan gamma)
                 d:dt)
                u_gamma))
             ((point R1-rect) 'tau)))
    (basis->oneform-basis basis-over-gamma)))

;; ### On a great circle

(define (g gamma Cartan)
  (let ((omega
          ((Cartan->forms
             (Cartan->Cartan-over-map Cartan gamma))
           ((differential gamma) d:dt))))
    (define (the-state-derivative)
      (lambda (state)
              (let ((t ((point R1-rect) (ref state 0)))
                    (u (ref state 1)))
                (up 1 (* -1 (omega t) u)))))
    the-state-derivative))

;; the implementation of scheme/define does not support ((define x) y) within let
(define ((transform tilt) coords)
  (let ((colat (ref coords 0))
        (long (ref coords 1)))
    (let ((x (* (sin colat) (cos long)))
          (y (* (sin colat) (sin long)))
          (z (cos colat)))
      (let ((vp ((rotate-x tilt) (up x y z))))
        (let ((colatp (acos (ref vp 2)))
              (longp (atan (ref vp 1) (ref vp 0))))
          (up colatp longp))))))

(define (tilted-path tilt)
  (define (coords t)
    ((transform tilt) (up (/ pi 2) t)))
  (compose (point S2-spherical)
           coords
           (chart R1-rect)))

(define pi-half (/ pi 2))

(print-expression
  ((state-advancer (g (tilted-path 1) sphere-Cartan))
   (up 0 (* ((D (transform 1)) (up pi-half 0)) (up 1 0)))
   pi-half))

(print-expression
  ((state-advancer (g (tilted-path 1) sphere-Cartan))
   (up 0 (* ((D (transform 1)) (up pi-half 0)) (up 1 0)))
   1))

(print-expression
  (* ((D (transform 1)) (up pi-half 1)) (up 1 0)))

;; ### 7.4 Geodesic Motion

(show-expression
  (simplify
    (((((covariant-derivative sphere-Cartan gamma)
        d:dt)
       ((differential gamma) d:dt))
      (chart S2-spherical)) ((point R1-rect) 't0))))

(define (Lfree s)
  (* 1/2 (square (velocity s))))

(define (sphere->R3 s)
  (let ((q (coordinate s)))
    (let ((theta (ref q 0))
          (phi (ref q 1)))
      (up (* (sin theta) (cos phi))
          (* (sin theta) (sin phi))
          (cos theta)))))

(define Lsphere
  (compose Lfree (F->C sphere->R3)))

(show-expression
  (simplify
    (((Lagrange-equations Lsphere)
      (up (literal-function 'alpha)
          (literal-function 'beta)))
     't)))

;; ### Exercise 7.1: Hamiltonian Evolution

(define Hsphere (Lagrangian->Hamiltonian Lsphere))

(print-expression
  ((phase-space-derivative Hsphere)
   (up 't (up 'theta 'phi) (down 'p_theta 'p_phi))))


(define state-space (make-manifold Rn 5))

(define states
  (coordinate-system-at state-space :rectangular :origin))

(define-coordinates
  (up t (up theta phi) (down p_theta p_phi)) states)

(repl/scittle-sidebar)
