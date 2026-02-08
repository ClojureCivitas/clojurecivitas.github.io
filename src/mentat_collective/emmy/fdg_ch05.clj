^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter Five"
                     :quarto {:author   :kloimhardt
                              :type     :draft
                              :description "Functional Differential Geometry: Chapter 5"
                              :sidebar  "emmy-fdg"
                              :date     "2026-02-08"
                              :image    "sicm_ch01.png"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch05
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda] :as scheme]
            [civitas.repl :as repl]))

;; ## 5 Integration
;; We know how to integrate real-valued functions of a real variable. We want to extend this idea to manifolds, in such a way that the integral is independent of the coordinate system used to compute it.

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
(kind/scittle
  '(defmacro define-coordinates [& b]
     (cons 'emmy.env/define-coordinates b)))


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

;; ### 3-Dimensional Euclidean Space
(define-coordinates (up x y z) R3-rect)
(define R3-rect-point ((point R3-rect) (up 'x0 'y0 'z0)))

(define u (+ (* 'u↑0 d:dx) (* 'u↑1 d:dy)))
(define v (+ (* 'v↑0 d:dx) (* 'v↑1 d:dy)))

(print-expression
  (((wedge dx dy) u v) R3-rect-point))

(define-coordinates (up r theta z) R3-cyl)

(define a (+ (* 'a↑0 d:dr) (* 'a↑1 d:dtheta)))
(define b (+ (* 'b↑0 d:dr) (* 'b↑1 d:dtheta)))

(print-expression
  (((wedge dr dtheta) a b) ((point R3-cyl) (up 'r0 'theta0 'z0))))

(define u (+ (* 'u↑0 d:dx) (* 'u↑1 d:dy) (* 'u↑2 d:dz)))
(define v (+ (* 'v↑0 d:dx) (* 'v↑1 d:dy) (* 'v↑2 d:dz)))
(define w (+ (* 'w↑0 d:dx) (* 'w↑1 d:dy) (* 'w↑2 d:dz)))

(print-expression
  (((wedge dx dy dz) u v w) R3-rect-point))

(print-expression
  (- (((wedge dx dy dz) u v w) R3-rect-point)
     (determinant
       (matrix-by-rows (list 'u↑0 'u↑1 'u↑2)
                       (list 'v↑0 'v↑1 'v↑2)
                       (list 'w↑0 'w↑1 'w↑2)))))

;; ### Computing Exterior Derivatives

(define a (literal-manifold-function 'alpha R3-rect))
(define b (literal-manifold-function 'beta R3-rect))
(define c (literal-manifold-function 'gamma R3-rect))

(define theta (+ (* a dx) (* b dy) (* c dz)))

(define X (literal-vector-field 'X-rect R3-rect))
(define Y (literal-vector-field 'Y-rect R3-rect))

(print-expression
  (((- (d theta)
       (+ (wedge (d a) dx)
          (wedge (d b) dy)
          (wedge (d c) dz))) X Y)
       R3-rect-point))

(define omega
  (+ (* a (wedge dy dz))
     (* b (wedge dz dx)) (* c (wedge dx dy))))

(define Z (literal-vector-field 'Z-rect R3-rect))

(print-expression
  (((- (d omega)
       (+ (wedge
            (d a) dy dz) (wedge (d b) dz dx)
          (wedge (d c) dx dy))) X Y Z)
       R3-rect-point))

;; ### Properties of Exterior Derivatives

(print-expression
  (((d (d theta)) X Y Z) R3-rect-point))


;; ### 5.4 Vector Integral Theorems

(define v (literal-vector-field 'v-rect R2-rect))
(define w (literal-vector-field 'w-rect R2-rect))

(define alpha (literal-function 'alpha R2->R))
(define beta (literal-function 'beta R2->R))

(define R2-rect-basis (coordinate-system->basis R2-rect))

(print-expression
  (let-scheme
    ((dx (ref (basis->oneform-basis R2-rect-basis) 0))
     (dy (ref (basis->oneform-basis R2-rect-basis) 1)))
    (((- (d (+ (* (compose alpha (chart R2-rect)) dx)
               (* (compose beta (chart R2-rect)) dy)))
         (* (compose (- ((partial 0) beta)
                        ((partial 1) alpha))
                     (chart R2-rect))
            (wedge dx dy)))
         v w)
         R2-rect-point)))

(define a (literal-manifold-function 'a-rect R3-rect))
(define b (literal-manifold-function 'b-rect R3-rect))
(define c (literal-manifold-function 'c-rect R3-rect))

(define flux-through-boundary-element
  (+ (* a (wedge dy dz))
     (* b (wedge dz dx))
     (* c (wedge dx dy))))

(define production-in-volume-element
        (* (+ (d:dx a) (d:dy b) (d:dz c))
           (wedge dx dy dz)))

(define X (literal-vector-field 'X-rect R3-rect))
(define Y (literal-vector-field 'Y-rect R3-rect))
(define Z (literal-vector-field 'Z-rect R3-rect))

(print-expression
  (((- production-in-volume-element
       (d flux-through-boundary-element))
       X Y Z)
       R3-rect-point))

(repl/scittle-sidebar)
