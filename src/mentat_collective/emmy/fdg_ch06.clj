^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter Six"
                     :quarto {:author   :kloimhardt
                              :type     :draft
                              :description "Functional Differential Geometry: Chapter 6"
                              :sidebar  "emmy-fdg"
                              :date     "2026-02-09"
                              :image    "sicm_ch01.png"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch06
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda] :as scheme]
            [civitas.repl :as repl]))

;; ## 6 Over a Map
;; To deal with motion on manifolds we need to think about paths on manifolds and vectors along these paths.

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
                                         Lagrange-equations r->p R2 define-coordinates
                                         Jacobian S2 S2-spherical pullback-function
                                         differential pullback-vector-field]])

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

;; See Ch3: `procedure->vector-field` not accessible in Scittle,
;; but vector-field->vector-field-over-map is standard

(comment
  (define ((vector-field->vector-field-over-map mu:N->M) v-on-M)
          (procedure->vector-field
            (lambda (f-on-M)
                    (compose (v-on-M f-on-M) mu:N->M)))))

;; ### Differential of a Map

;; here I use lambda, because my define does not support three nestings

(define ((differential mu) v)
  (lambda (f)
    (v (compose f mu))))

;; ### 6.2 One-Form Fields Over a Map
(comment
(define ((form-field->form-field-over-map mu:N->M) w-on-M)
  (define (make-fake-vector-field V-over-mu n)
    (define ((u f) m)
      ((V-over-mu f) n))
    (procedure->vector-field u))
  (procedure->nform-field
    (lambda vectors-over-map
       (lambda (n)
          ((apply w-on-M
             (map (lambda (V-over-mu)
                     (make-fake-vector-field V-over-mu n))
                  vectors-over-map))
          (mu:N->M n))))
    (get-rank w-on-M)))
:end-comment)

;;### 6.3 Basis Fields Over a Map

(define S2 (make-manifold S2-type 2 3))

(define S2-spherical
  (coordinate-system-at S2 :spherical :north-pole))


(define-coordinates (up theta phi) S2-spherical)

(define S2-basis (coordinate-system->basis S2-spherical))

(define mu
  (compose (point S2-spherical)
           (up (literal-function 'theta) (literal-function 'phi))
           (chart R1-rect)))

(define S2-basis-over-mu (basis->basis-over-map mu S2-basis))

(define h
  (literal-manifold-function 'h-spherical S2-spherical))

(print-expression
  (((basis->vector-basis S2-basis-over-mu) h)
   ((point R1-rect) 't0)))

(print-expression
  (((basis->oneform-basis S2-basis-over-mu)
    (basis->vector-basis S2-basis-over-mu))
   ((point R1-rect) 't0)))

;; ### Components of the Velocity

(define-coordinates t e/R1-rect)

(print-expression
  (((basis->oneform-basis S2-basis-over-mu)
    ((differential mu) d:dt))
   ((point R1-rect) 't0)))

;; ### Pullback and Pushforward of a Function

(define ((pullback-function mu:N->M) f-on-M)
  (compose f-on-M mu:N->M))

;; ### Pushforward of a Vector Field

(comment
(define ((pushforward-vector mu:N->M mu-inverse:M->N) v-on-N)
  (procedure->vector-field
    (lambda (f)
            (compose (v-on-N (compose f mu:N->M)) mu-inverse:M->N))))
)

;; ### Pullback of a Vector Field

(define (pullback-vector-field mu:N->M mu-inverse:M->N)
  (pushforward-vector mu-inverse:M->N mu:N->M))


;; ### Pullback of a Form Field

(comment
  (define ((pullback-form mu:N->M) omega-on-M)
    (let ((k (get-rank omega-on-M)))
      (if (= k 0)
        ((pullback function mu:N->M) omega-on-M)
        (procedure->nform-field
          (lambda vectors-on-N
                  (apply ((form-field->form-field-over-map mu:N->M)
                          omega-on-M)
                         (map (differential mu:N->M) vectors-on-N)))
          k))))
  )

;; ### Properties of Pullback

(define mu (literal-manifold-map 'MU R2-rect R3-rect))

(define f (literal-manifold-function 'f-rect R3-rect))

(define X (literal-vector-field 'X-rect R2-rect))

(print-expression
  (((- ((pullback mu) (d f)) (d ((pullback mu) f))) X)
   ((point R2-rect) (up 'x0 'y0))))

(define theta (literal-oneform-field 'THETA R3-rect))

(define Y (literal-vector-field 'Y-rect R2-rect))

(print-expression
  (((- ((pullback mu) (d theta)) (d ((pullback mu) theta))) X Y)
   ((point R2-rect) (up 'x0 'y0))))

(repl/scittle-sidebar)
