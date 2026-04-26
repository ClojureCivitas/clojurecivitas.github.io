^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter Ten"
                     :quarto {:author      :kloimhardt
                              :type        :post
                              :draft       true
                              :description "Functional Differential Geometry: Chapter 10"
                              :sidebar     "emmy-fdg"
                              :date        "2026-04-04"
                              :image       "sicm_ch01.png"
                              :category    :libs
                              :tags        [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch10
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial = test])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [let-scheme lambda] :as scheme]
            [civitas.repl :as repl]))

;; ## 10 Hodge Star and Electrodynamics
;; The Hodge dual is useful for the elegant formalization of electrodynamics.

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
                                         gradient curl divergence
                                         Laplacian phi]])

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

(define (gradient metric basis)
  (compose (raise metric basis) d))

(define (curl metric orthonormal-basis)
  (let ((star (Hodge-star metric orthonormal-basis))
        (sharp (raise metric orthonormal-basis))
        (flat (lower metric)))
    (compose sharp star d flat)))

(define (divergence metric orthonormal-basis)
  (let ((star (Hodge-star metric orthonormal-basis))
        (flat (lower metric)))
    (compose star d star flat)))

(define (((divergence-c Cartan) v) point)
  (let ((basis (Cartan->basis Cartan))
        (nabla (covariant-derivative Cartan)))
    (contract
      (lambda (ei wi)
              ((wi ((nabla ei) v)) point))
      basis)))

(define (Laplacian metric orthonormal-basis)
  (compose (divergence metric orthonormal-basis)
           (gradient metric orthonormal-basis)))

(define spherical R3-rect)

(define-coordinates (up r theta phi) spherical)

(define R3-spherical-point
  ((point spherical) (up 'r0 'theta0 'phi0)))

(define (spherical-metric v1 v2)
  (+ (* (dr v1) (dr v2))
     (* (square r)
        (+ (* (dtheta v1) (dtheta v2))
           (* (expt (sin theta) 2)
              (dphi v1) (dphi v2))))))


(define e_0 d:dr)

(define e_1 (* (/ 1 r) d:dtheta))

(define e_2 (* (/ 1 (* r (sin theta))) d:dphi))

(define orthonormal-spherical-vector-basis
  (down e_0 e_1 e_2))

(define orthonormal-spherical-oneform-basis
  (vector-basis->dual orthonormal-spherical-vector-basis
                      spherical))

(define orthonormal-spherical-basis
  (make-basis orthonormal-spherical-vector-basis
              orthonormal-spherical-oneform-basis))

(print-expression
  ((orthonormal-spherical-oneform-basis
     ((gradient spherical-metric orthonormal-spherical-basis)
      (literal-manifold-function 'f spherical)))
   R3-spherical-point))

(define v
  (+ (* (literal-manifold-function 'v↑0 spherical) e_0)
     (* (literal-manifold-function 'v↑1 spherical) e_1)
     (* (literal-manifold-function 'v↑2 spherical) e_2)))

(print-expression
  ((orthonormal-spherical-oneform-basis
     ((curl spherical-metric orthonormal-spherical-basis) v))
   R3-spherical-point))

(print-expression
  (((divergence spherical-metric orthonormal-spherical-basis) v)
   R3-spherical-point))

(print-expression
  (((Laplacian spherical-metric orthonormal-spherical-basis)
    (literal-manifold-function 'f spherical))
   R3-spherical-point))

;; ### The Wave Equation

(define SR R4-rect)
(define-coordinates (up ct x y z) SR)
(define an-event ((point SR) (up 'ct0 'x0 'y0 'z0)))

(define a-vector
  (+ (* (literal-manifold-function 'v↑t SR) d:dct)
     (* (literal-manifold-function 'v↑x SR) d:dx)
     (* (literal-manifold-function 'v↑y SR) d:dy)
     (* (literal-manifold-function 'v↑z SR) d:dz)))

(define (g-Minkowski u v)
  (+ (* -1 (dct u) (dct v))
     (* (dx u) (dx v))
     (* (dy u) (dy v))
     (* (dz u) (dz v))))

(print-expression
  ((g-Minkowski a-vector a-vector) an-event))

(define SR-vector-basis (coordinate-system->vector-basis SR))

((g-Minkowski SR-vector-basis SR-vector-basis) an-event)

(define p (literal-manifold-function 'phi SR))

(define SR-vector-basis (down (* (/ 1 'c) d:dct) d:dx d:dy d:dz))

(define SR-oneform-basis (up (* 'c dct) dx dy dz))

(define SR-basis
  (make-basis SR-vector-basis
              SR-oneform-basis))

(print-expression
  (((Laplacian g-Minkowski SR-basis) p) an-event))


;; ### Electrodynamics

(define (Faraday Ex Ey Ez Bx By Bz)
  (+ (* Ex (wedge dx dct))
     (* Ey (wedge dy dct))
     (* Ez (wedge dz dct))
     (* Bx (wedge dy dz))
     (* By (wedge dz dx))
     (* Bz (wedge dx dy))))

(define (Maxwell Ex Ey Ez Bx By Bz)
  (+ (* -1 Bx (wedge dx dct))
     (* -1 By (wedge dy dct))
     (* -1 Bz (wedge dz dct))
     (* Ex (wedge dy dz))
     (* Ey (wedge dz dx))
     (* Ez (wedge dx dy))))

(define SR-star (Hodge-star g-Minkowski SR-basis))

(print-expression
  (((- (SR-star (Faraday 'Ex 'Ey 'Ez 'Bx 'By 'Bz))
       (Maxwell 'Ex 'Ey 'Ez 'Bx 'By 'Bz))
    (literal-vector-field 'u SR)
    (literal-vector-field 'v SR))
   an-event))

(define (J charge-density Ix Iy Iz)
  (- (* (/ 1 'c) (+ (* Ix dx) (* Iy dy) (* Iz dz)))
     (* charge-density 'c dct)))


(define F
  (Faraday (literal-manifold-function 'Ex SR)
           (literal-manifold-function 'Ey SR)
           (literal-manifold-function 'Ez SR)
           (literal-manifold-function 'Bx SR)
           (literal-manifold-function 'By SR)
           (literal-manifold-function 'Bz SR)))

(define four-current
  (J (literal-manifold-function 'rho SR)
     (literal-manifold-function 'Ix SR)
     (literal-manifold-function 'Iy SR)
     (literal-manifold-function 'Iz SR)))

;; ### Maxwell’s Equations

(print-expression
  (((d F) d:dx d:dy d:dz) an-event))

(print-expression
  (((d F) d:dct d:dy d:dz) an-event))

(print-expression
  (((d F) d:dct d:dz d:dx) an-event))

(print-expression
  (((d F) d:dct d:dx d:dy) an-event))

(print-expression
  (((- (d (SR-star F)) (* 4 'pi (SR-star four-current)))
    d:dx d:dy d:dz)
   an-event))

(print-expression
  (((- (d (SR-star F)) (* 4 'pi (SR-star four-current)))
    d:dct d:dy d:dz)
   an-event))

(print-expression
  (((- (d (SR-star F)) (* 4 'pi (SR-star four-current)))
    d:dct d:dz d:dx)
   an-event))

(print-expression
  (((- (d (SR-star F)) (* 4 'pi (SR-star four-current)))
    d:dct d:dx d:dy)
   an-event))


;; ### Lorentz Force

(define E
  (up (literal-manifold-function 'Ex SR)
      (literal-manifold-function 'Ey SR)
      (literal-manifold-function 'Ez SR)))

(define B
  (up (literal-manifold-function 'Bx SR)
      (literal-manifold-function 'By SR)
      (literal-manifold-function 'Bz SR)))

(define V (up 'V_x 'V_y 'V_z))

(print-expression
  (* 'q (+ (E an-event) (cross-product V (B an-event)))))

;; eta-inverse is not defined, so these will not run.

(comment
  (define (Force charge F four-velocity component)
    (* -1 charge
       (contract (lambda (a b)
                         (contract (lambda (e w)
                                           (* (w four-velocity)
                                              (F e a)
                                              (eta-inverse b component)))
                                   SR-basis))
                 SR-basis)))

  (print-expression
    ((Force 'q F d:dct dx) an-event))

  (define (Ux beta)
    (+ (* (/ 1 (sqrt (- 1 (square beta)))) d:dct)
       (* (/ beta (sqrt (- 1 (square beta)))) d:dx)))

  (print-expression
    ((Force 'q F (Ux 'v:c) dy) an-event))

  :end-comment)

(repl/scittle-sidebar)
