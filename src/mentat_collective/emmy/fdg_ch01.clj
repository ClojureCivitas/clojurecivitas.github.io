^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differentail Geometry Chapter One"
                     :quarto {:author   :kloimhardt
                              :type     :post
                              :date     "2025-09-10"
                              :image    "sicm_ch01.png"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch01
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [civitas.repl :as repl]))

(kind/hiccup
  [:div
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.emmy.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.cljs-ajax.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react-dom@18/umd/react-dom.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.reagent.js"}]])

^:kindly/hide-code
(def md
  (comp kindly/hide-code kind/md))

(md "The following examples are taken from the open-access book [Structure and Interpretation of Classical Mechanics (SICM)](https://mitp-content-server.mit.edu/books/content/sectbyfn/books_pres_0/9579/sicm_edition_2.zip/chapter001.html).")

(kind/scittle
  '(defn walk [inner outer form]
    (cond
      (list? form) (outer (apply list (map inner form)))
      (seq? form)  (outer (doall (map inner form)))
      (coll? form) (outer (into (empty form) (map inner form)))
      :else        (outer form))))

(kind/scittle
  '(defn postwalk [f form]
    (walk (partial postwalk f) f form)))

(kind/scittle
  '(defn postwalk-replace [smap form]
    (postwalk (fn [x] (if (contains? smap x) (smap x) x)) form)))

(kind/scittle
  '(defmacro let-scheme [b & e]
    (concat (list 'let (into [] (apply concat b))) e)))

(kind/scittle
  '(defmacro define-1 [h & b]
    (let [body (postwalk-replace {'let 'let-scheme} b)]
      (if (coll? h)
        (if (coll? (first h))
          (list 'defn (ffirst h) (into [] (rest (first h)))
                (concat (list 'fn (into [] (rest h))) body))
          (concat (list 'defn (first h) (into [] (rest h)))
                  body))
        (concat (list 'def h) body)))))

(kind/scittle
  '(defmacro define [h & b]
    (if (and (coll? h) (= (first h) 'tex-inspect))
      (list 'do
            (concat ['define-1 (second h)] b)
            h)
      (concat ['define-1 h] b))))

(kind/scittle
  '(defmacro lambda [h b]
    (list 'fn (into [] h) b)))

(kind/scittle
  '(require '[emmy.env :refer :all :exclude [Lagrange-equations Gamma]]))

(kind/scittle
  '(def show-expression (comp ->infix simplify)))

(kind/scittle
  '(def velocities velocity))

(kind/scittle
  '(def coordinates coordinate))

(kind/scittle
  '(def vector-length count))

(kind/scittle
  '(defn time [state] (first state)))

(defmacro define [& b]
  (list 'kind/scittle (list 'quote (cons 'define b))))

(defmacro show-expression [& b]
  (list 'kind/reagent [:p (list 'quote (cons 'show-expression b))]))

(kind/scittle '(declare Gamma))

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

(repl/scittle-sidebar)
