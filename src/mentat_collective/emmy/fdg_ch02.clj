^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter Two"
                     :quarto {:author   :kloimhardt
                              :type     :draft
                              :description "Functional Differential Geometry: Chapter 2"
                              :sidebar  "emmy-fdg"
                              :date     "2026-01-29"
                              :image    "sicm_ch01.png"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch02
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda]]
            [civitas.repl :as repl]))

;; ## 2 Manifolds
;; A *manifold* is a generalization of our idea of a smooth surface embedded in Euclidean space.

^:kindly/hide-code
(def prod true) #_"used to check Emmy in Scittle kitchen"

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
  '[emmy.env :refer :all :exclude [print-expression Lagrangian-action find-path Lagrange-equations r->p
                                   R2 R2-rect R2-polar]])

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

(define R2 (make-manifold Rn 2))

;; Concerning the expression the `(define U (patch ’origin R2))`,
;; "We can avoid explicitly naming the patch"
;; as stated in footnote 6, p13 of the FDG book

;; ### 2.1 Coordinate Functions
(define R2-rect (coordinate-system-at R2 :rectangular :origin))
(define R2-polar (coordinate-system-at R2 :polar-cylindrical :origin))

(define R2-rect-chi (chart R2-rect))
(define R2-rect-chi-inverse (point R2-rect)) (define R2-polar-chi (chart R2-polar))
(define R2-polar-chi-inverse (point R2-polar))

(print-expression
  ((compose R2-polar-chi R2-rect-chi-inverse) (up 'x0 'y0)))

(print-expression
  ((compose R2-rect-chi R2-polar-chi-inverse) (up 'r0 'theta0)))

(print-expression
  ((D (compose R2-rect-chi R2-polar-chi-inverse)) (up 'r0 'theta0)))

;; ## 2.2 Manifold Functions

(define R2->R '(-> (UP Real Real) Real))

(define f
  (compose (literal-function 'f-rect R2->R) R2-rect-chi))

;; Footnote 8, p16
(define f-shorthand
  (literal-manifold-function 'f-rect R2-rect))

(define R2-rect-point (R2-rect-chi-inverse (up 'x0 'y0)))

(define corresponding-polar-point
  (R2-polar-chi-inverse
    (up (sqrt (+ (square 'x0) (square 'y0)))
        (atan 'y0 'x0))))

(print-expression
  (f R2-rect-point))

(print-expression
  (f corresponding-polar-point))

(define-coordinates (up x y) R2-rect)

(define-coordinates (up r theta) R2-polar)

(x (R2-rect-chi-inverse (up 'x0 'y0)))

(x (R2-polar-chi-inverse (up 'r0 'theta0)))

(r (R2-polar-chi-inverse (up 'r0 'theta0)))

(r (R2-rect-chi-inverse (up 'x0 'y0)))

(theta (R2-rect-chi-inverse (up 'x0 'y0)))

(define h (+ (* x (square r)) (cube y)))

(h R2-rect-point)

(h (R2-polar-chi-inverse (up 'r0 'theta0)))

(define-coordinates (up r theta) R2-polar)

((- r (* 2 'a (+ 1 (cos theta)))) ((point R2-rect) (up 'x 'y)))

((compose
  (chart S2-spherical)
  (point S2-Riemann)
  (chart R2-rect)
  (point R2-polar))
 (up 'rho 'theta))


;; requires emmy.util.def/careful-def for define-coordinates

;; (repl/scittle-sidebar)
