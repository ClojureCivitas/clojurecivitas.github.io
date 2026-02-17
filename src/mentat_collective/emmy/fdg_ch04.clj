^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter Four"
                     :quarto {:author   :kloimhardt
                              :type     :draft
                              :description "Functional Differential Geometry: Chapter 4"
                              :sidebar  "emmy-fdg"
                              :date     "2026-02-06"
                              :image    "sicm_ch01.png"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch04
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda] :as scheme]
            [civitas.repl :as repl]))

;; ## 4 Basis Fields
;; A vector field may be written as a linear combination of basis vector fields.

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
  '[emmy.env :as e :refer :all :exclude [print-expression Lagrangian-action find-path
                                   Lagrange-equations r->p
                                   R2 define-coordinates Jacobian]])

^:kindly/hide-code
(define emmy-vector-field
  '[emmy.calculus.vector-field :as vf])

^:kindly/hide-code
(define emmy-structure
  '[emmy.structure :as s])

^{:kindly/hide-code true :kindly/kind kind/hidden}
(do
  (require emmy-env)
  (require emmy-vector-field)
  (require emmy-structure))

^:kindly/hide-code
(kind/scittle
  '(do
     (require emmy-env)
     (require emmy-vector-field)
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

^:kindly/hide-code
(define R2->R '(-> (UP Real Real) Real))

^:kindly/hide-code
(define R2-rect-chi-inverse (point R2-rect))

^:kindly/hide-code
(define R2-rect-point (R2-rect-chi-inverse (up 'x0 'y0)))

^:kindly/hide-code
(define-coordinates (up x y) R2-rect)

(define e0
  (+ (* (literal-manifold-function 'e0x R2-rect) d:dx)
     (* (literal-manifold-function 'e0y R2-rect) d:dy)))

(define e1
  (+ (* (literal-manifold-function 'e1x R2-rect) d:dx)
     (* (literal-manifold-function 'e1y R2-rect) d:dy)))

(define e-vector-basis (down e0 e1))

(define e-dual-basis
  (vector-basis->dual e-vector-basis R2-polar))

(print-expression
  ((e-dual-basis e-vector-basis) R2-rect-point))

(define v
  (* (up (literal-manifold-function 'b↑0 R2-rect)
         (literal-manifold-function 'b↑1 R2-rect)) 
     e-vector-basis))

(print-expression
  ((e-dual-basis v) R2-rect-point))

;; ## 4.1 Change of Basis

(define (Jacobian to-basis from-basis) 
  (s/mapr (basis->oneform-basis to-basis)
          (basis->vector-basis from-basis)))

(define b-rect
  ((coordinate-system->oneform-basis R2-rect)
   (literal-vector-field 'b R2-rect)))

(define b-polar
  (* (Jacobian (coordinate-system->basis R2-polar)
               (coordinate-system->basis R2-rect))
     b-rect))

(print-expression
  (b-polar ((point R2-rect) (up 'x0 'y0))))

(print-expression
(((coordinate-system->oneform-basis R2-polar) 
  (literal-vector-field 'b R2-rect))
 ((point R2-rect) (up 'x0 'y0))))

;; ## 4.3 Commutators

(print-expression
(let-scheme ((polar-basis (coordinate-system->basis R2-polar)) 
            (polar-vector-basis (basis->vector-basis polar-basis)) 
            (polar-dual-basis (basis->oneform-basis polar-basis))
            (f (literal-manifold-function 'f-rect R2-rect)))
  ((- ((commutator e0 e1) f)
      (* (- (e0 (polar-dual-basis e1))
            (e1 (polar-dual-basis e0))) (polar-vector-basis f)))
      R2-rect-point)))

(define-coordinates (up x y z) R3-rect)

(define Jz (- (* x d:dy) (* y d:dx))) 
(define Jx (- (* y d:dz) (* z d:dy))) 
(define Jy (- (* z d:dx) (* x d:dz)))

(define g (literal-manifold-function 'g-rect R3-rect))
(define R3-rect-point ((point R3-rect) (up 'x0 'y0 'z0)))

(print-expression
  (((+ (commutator Jx Jy) Jz) g) R3-rect-point))

(print-expression
  (((+ (commutator Jy Jz) Jx) g) R3-rect-point))

(print-expression
  (((+ (commutator Jz Jx) Jy) g) R3-rect-point))

(define-coordinates (up theta phi psi) Euler-angles)

;; equation 4.29
(define e_x (+ (* (cos phi) d:dtheta)
               (* -1 (/ (* (sin phi) (cos theta)) (sin theta)) d:dphi)
               (* (/ (sin phi) (sin theta)) d:dpsi)))

;; equation 4.30
(define e_y (+ (/ (* (cos phi) (cos theta) d:dphi) (sin theta))
               (* (sin phi) d:dtheta)
               (* -1 (/ (cos phi) (sin theta)) d:dpsi)))

;; equation 4.31
(define e_z d:dphi)

(define f (literal-manifold-function 'f-Euler Euler-angles))

(define SO3-point ((point Euler-angles) (up 'theta 'phi 'psi)))

(print-expression
  (((+ (commutator e_x e_y) e_z) f) SO3-point))

(print-expression
  (((+ (commutator e_y e_z) e_x) f) SO3-point))

(print-expression
  (((+ (commutator e_z e_x) e_y) f) SO3-point))

(repl/scittle-sidebar)
