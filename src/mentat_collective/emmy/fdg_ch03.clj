^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter Three"
                     :quarto {:author   :kloimhardt
                              :type     :draft
                              :description "Functional Differential Geometry: Chapter 3"
                              :sidebar  "emmy-fdg"
                              :date     "2026-01-29"
                              :image    "sicm_ch01.png"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch03
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda] :as scheme]
            [civitas.repl :as repl]))

;; ## 3 Vector Fields and One-Form Fields
;; We want a way to think about how a function varies on a manifold.

^:kindly/hide-code
(def prod true) #_"used to check Emmy in Scittle kitchen"

^:kindly/hide-code
(kind/hiccup scheme/scittle-kitchen-hiccup)

^:kindly/hide-code
(defmacro define [& b]
  (list 'do
        (cons 'mentat-collective.emmy.scheme/define b)
        (list 'kind/scittle (list 'quote (cons 'define b)))))

^:kindly/hide-code
(define emmy-env
  '[emmy.env :as e :refer :all :exclude [print-expression Lagrangian-action find-path
                                   Lagrange-equations r->p
                                   R2 define-coordinates]])

^:kindly/hide-code
(define emmy-vector-field
  '[emmy.calculus.vector-field :as vf])

^{:kindly/hide-code true :kindly/kind kind/hidden}
(do
  (require emmy-env)
  (require emmy-vector-field))

^:kindly/hide-code
(kind/scittle
  '(do
     (require emmy-env)
     (require emmy-vector-field)))

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
    e
    (reag-comp e)))

^:kindly/hide-code
(kind/scittle
  '(def print-expression identity))

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
    (list 'show-tex-expression-fn e)
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

;; ## 3.1 Vector Fields

(define v
  (components->vector-field
    (up (literal-function 'b0 R2->R)
        (literal-function 'b1 R2->R))
    R2-rect))

(print-expression
  ((v (literal-manifold-function 'f-rect R2-rect)) R2-rect-point))

(print-expression
  ((v (chart R2-rect)) R2-rect-point))

;; #### [Comment MAK concerning components->vector-field]

;; The function `procedure->vector-field` seems to be missing in Scittle

;; Clojure interns has `procedure->vector-field`
(comment
  (ns-interns 'emmy.calculus.vector-field))

;; ClojureScript interns lack `procedure->vector-field`
(comment
  (kind/reagent
    ['(fn []
        [:tt (str (ns-interns 'emmy.calculus.vector-field))])]))

;; The function `components->vector-field` exists as a standard, so we can
;; leave it here as merely a comment

(comment
  (define (components->vector-field components coordsys)
    (define (v f)
      (compose (* (D (compose f (point coordsys)))
                  components)
               (chart coordsys)))
    (vf/procedure->vector-field v)))


;; ### Coordinate Representation

;; need to check the form below, fortunately `coordinatize` is also standard

(comment
  (define (coordinatize v coordsys)
    (define ((coordinatized-v f) x)
      (let ((b (compose (v (chart coordsys)) (point coordsys))))
        (* ((D f) x) (b x))))
    (make-operator coordinatized-v)))

(print-expression
  (((e/coordinatize v R2-rect) (literal-function 'f-rect R2->R))
   (up 'x0 'y0)))

;; ## 3.2 Coordinate-Basis Vector Fields

(define-coordinates (up x y) R2-rect)

(define-coordinates (up r theta) R2-polar)

(print-expression
  ((d:dx (square r)) R2-rect-point))

(print-expression
  (((+ d:dx (* 2 d:dy)) (+ (square r) (* 3 x))) R2-rect-point))

;; ## 3.3 Integral Curves

(define circular (- (* x d:dy) (* y d:dx)))

(print-expression
  (take 6
        (seq
          (((exp (* 't circular)) (chart R2-rect))
           ((point R2-rect) (up 1 0))))))

(print-expression
  ((((e/evolution 6) 'delta-t circular) (chart R2-rect))
   ((point R2-rect) (up 1 0))))

;; ## 3.5 Coordinate-Basis One-Form Fields

(define omega
  (e/components->oneform-field
    (down (literal-function 'a_0 R2->R)
          (literal-function 'a_1 R2->R))
    R2-rect))

(print-expression
  ((omega (down d:dx d:dy)) R2-rect-point))

(define omega-alt (e/literal-oneform-field 'a R2-rect))

(print-expression
  (((d (literal-manifold-function 'f-rect R2-rect))
    (coordinate-system->vector-basis R2-rect))
   R2-rect-point))

(print-expression
  (((d (literal-manifold-function 'f-polar R2-polar))
    (coordinate-system->vector-basis R2-rect))
   ((point R2-polar) (up 'r 'theta))))

(define-coordinates (up x y) R2-rect)

(print-expression
  ((dx d:dy) R2-rect-point))

(print-expression
  ((dx d:dx) R2-rect-point))

(print-expression
  ((dx circular) R2-rect-point))

(print-expression
  ((dy circular) R2-rect-point))

(print-expression
  ((dr circular) R2-rect-point))

(print-expression
  ((dtheta circular) R2-rect-point))

(define f (literal-manifold-function 'f-rect R2-rect))

(print-expression
  (((- circular d:dtheta) f) R2-rect-point))

;; ### Coordinate Transformations

(define omega (literal-oneform-field 'a R2-rect))

(define v (literal-vector-field 'b R2-rect))

(print-expression
  ((omega v) R2-rect-point))

(repl/scittle-sidebar)
