^{:kindly/hide-code true
  :clay             {:title  "scittle repl"
                     :quarto {:type     :draft
                              ;; :sidebar  "emmy-fdg"
                              :date     "2025-11-12"
                              :image    "fdg_prologue.png"
                              :category :libs
                              :tags     [:emmy :physics]}}}
(ns mentat-collective.emmy.appendix-scittlerepl
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator
                            denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme]]
            [civitas.repl :as repl]))

^:kindly/hide-code
(kind/hiccup
  [:div
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.emmy.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.cljs-ajax.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react-dom@18/umd/react-dom.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.reagent.js"}]
   [:script {:type "application/x-scittle" :src "scheme.cljc"}]])

^:kindly/hide-code
(defmacro define [& b]
  (list 'do
        (cons 'mentat-collective.emmy.scheme/define b)
        (list 'kind/scittle (list 'quote (cons 'define b)))))

^:kindly/hide-code
(define emmy-require
  '[emmy.env :refer :all :exclude [Lagrange-equations Gamma]])

^:kindly/hide-code
(require emmy-require)

^:kindly/hide-code
(kind/scittle
  '(require emmy-require))

^:kindly/hide-code
(def show-expression-clj (comp ->infix simplify))

^:kindly/hide-code
(kind/scittle
  '(def show-expression (comp ->infix simplify)))

^:kindly/hide-code
(defmacro show-expression-sci [b]
  (list 'kind/reagent [:tt (list 'quote (list 'show-expression b))]))

^:kindly/hide-code
(defmacro show-expression [b]
  (let [serg (show-expression-clj (eval b))]
    (list 'kind/reagent
          [:div (list 'quote
                      (list 'let ['a (list 'show-expression b)]
                            (list 'if (list '= serg 'a)
                                  [:tt 'a]
                                  [:div
                                   [:tt 'a] ;; comment this in prod
                                   [:tt serg]])))])))

^:kindly/hide-code
(define _ (declare Gamma))

(define ((Lagrange-equations Lagrangian) w)
  (- (D (compose ((partial 2) Lagrangian) (Gamma w)))
     (compose ((partial 1) Lagrangian) (Gamma w))))

(define ((Gamma w) t)
  (up t (w t) ((D w) t)))

(define ((L-harmonic m k) local)
  (let ((q (coordinate local))
        (v (velocity local)))
    (- (* 1/2 m (square v))
       (* 1/2 k (square q)))))

(define (proposed-solution t)
  (* 'a (cos (+ (* 'omega t) 'phi))))

(show-expression
  (((Lagrange-equations (L-harmonic 'm 'k))
    proposed-solution)
   't))

(show-expression-clj
  (((Lagrange-equations (L-harmonic 'm 'k))
    (literal-function 'x))
   't))

(repl/scittle-sidebar)
