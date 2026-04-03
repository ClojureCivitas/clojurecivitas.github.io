^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter Eleven"
                     :quarto {:author      :kloimhardt
                              :type        :post
                              :draft       true
                              :description "Functional Differential Geometry: Chapter 11"
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

;; ## 11 Special Relativity
;; Einsten noticed that Maxwell’s equations were inconsistent with Galilean relativity.

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
                                         four-tuple->space
                                         make-four-tuple
                                         four-tuple->ct
                                         proper-space-interval
                                         proper-time-interval
                                         general-boost
                                         general-boost2
                                         extended-rotation
                                         add-v:cs
                                         ]])

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

;; ### Implementation

(define (make-four-tuple ct space)
  (up ct (ref space 0) (ref space 1) (ref space 2)))

(define (four-tuple->ct v) (ref v 0))

(define (four-tuple->space v)
  (up (ref v 1) (ref v 2) (ref v 3)))

(define (proper-space-interval four-tuple)
  (sqrt (- (square (four-tuple->space four-tuple))
           (square (four-tuple->ct four-tuple)))))

(define (proper-time-interval four-tuple)
  (sqrt (- (square (four-tuple->ct four-tuple))
           (square (four-tuple->space four-tuple)))))

(define ((general-boost beta) xi-p)
  (let ((gamma (expt (- 1 (square beta)) (/ -1 2))))
    (let ((factor (/ (- gamma 1) (square beta))))
      (let ((xi-p-time (four-tuple->ct xi-p))
            (xi-p-space (four-tuple->space xi-p)))
        (let ((beta-dot-xi-p (dot-product beta xi-p-space)))
          (make-four-tuple
            (* gamma (+ xi-p-time beta-dot-xi-p))
            (+ (* gamma beta xi-p-time)
               xi-p-space
               (* factor beta beta-dot-xi-p))))))))

(print-expression
  (- (proper-space-interval
       ((general-boost (up 'vx 'vy 'vz))
        (make-four-tuple 'ct (up 'x 'y 'z))))
     (proper-space-interval
       (make-four-tuple 'ct (up 'x 'y 'z)))))

(define ((general-boost2 direction v:c) four-tuple-prime)
  (let ((delta-ct-prime (four-tuple->ct four-tuple-prime))
        (delta-x-prime (four-tuple->space four-tuple-prime)))
    (let ((betasq (square v:c)))
      (let ((bx (dot-product direction delta-x-prime))
            (gamma (/ 1 (sqrt (- 1 betasq)))))
        (let ((alpha (- gamma 1)))
          (let ((delta-ct
                  (* gamma (+ delta-ct-prime (* bx v:c))))
                (delta-x
                  (+ (* gamma v:c direction delta-ct-prime)
                     delta-x-prime
                     (* alpha direction bx))))
            (make-four-tuple delta-ct delta-x)))))))


;; ### Rotations

(define ((extended-rotation R) xi)
  (make-four-tuple
    (four-tuple->ct xi)
    (R (four-tuple->space xi))))

;; the following calculation does not seem to stop.

(comment
  (print-expression
    (let-scheme ((beta (up 'bx 'by 'bz))
                 (xi (make-four-tuple 'ct (up 'x 'y 'z)))
                 (R (compose
                      (rotate-x 'theta)
                      (rotate-y 'phi)
                      (rotate-z 'psi)))
                 (R-inverse (compose
                              (rotate-z (- 'psi))
                              (rotate-y (- 'phi))
                              (rotate-x (- 'theta)))))
      (- ((general-boost beta) xi)
         ((compose (extended-rotation R-inverse)
                   (general-boost (R beta))
                   (extended-rotation R))
          xi))))

  :end-comment)

;; Special Relativity Frames

(define ((coordinates->event ancestor-frame this-frame
                             boost-direction v:c origin)
         coords)
  ((point ancestor-frame)
   (make-SR-coordinates ancestor-frame
                        (+ ((general-boost2 boost-direction v:c) coords)
                           origin))))

(define ((event->coordinates ancestor-frame this-frame
                             boost-direction v:c origin)
         event)
  (make-SR-coordinates this-frame
                       ((general-boost2 (- boost-direction) v:c)
                        (- ((chart ancestor-frame) event) origin))))

;; NOTE: this seems to be the wrong definition, using the default environment

(comment
  (define make-SR-frame
    (frame-maker coordinates->event event->coordinates)))

;; ### Velocity Addition Formula

(define home (base-frame-maker 'home 'home))

(define A
  (e/make-SR-frame 'A home
                 (up 1 0 0)
                 'va:c
                 (make-SR-coordinates home (up 0 0 0 0))))

(define B
  (e/make-SR-frame 'B A
                 (up 1 0 0)
                 'vb:c
                 (make-SR-coordinates A (up 0 0 0 0))))


(print-expression
  (let-scheme ((B-origin-home-coords
                 ((chart home)
                  ((point B)
                   (make-SR-coordinates B (up 'ct 0 0 0))))))
    (/ (ref B-origin-home-coords 1)
       (ref B-origin-home-coords 0))))

(define (add-v:cs va:c vb:c)
  (/ (+ va:c vb:c)
     (+ 1 (* va:c vb:c))))

;; ### Twin Paradox

(define start-event
  ((point home)
   (make-SR-coordinates home (up 0 0 0 0))))


(define outgoing
  (e/make-SR-frame 'outgoing       ; for debugging
                 home            ; base frame
                 (up 1 0 0)      ; x direction
                 (/ 24 25)           ; velocity as fraction of c
                 ((chart home)
                  start-event)))

(define traveller-at-turning-point-event
  ((point home)
   (e/make-SR-coordinates home
                          (up (* 'c 25) (* 25 (/ 24 25) 'c) 0 0))))

(print-expression
  (- ((chart outgoing) traveller-at-turning-point-event)
     ((chart outgoing) start-event)))

(print-expression
  (- ((chart home) traveller-at-turning-point-event)
     ((chart home) start-event)))

(print-expression
  (proper-time-interval
    (- ((chart outgoing) traveller-at-turning-point-event)
       ((chart outgoing) start-event))))

(print-expression
  (proper-time-interval
    (- ((chart home) traveller-at-turning-point-event)
       ((chart home) start-event))))

(define halfway-at-home-event
  ((point home)
   (e/make-SR-coordinates home (up (* 'c 25) 0 0 0))))

(print-expression
  (proper-time-interval
    (- ((chart home) halfway-at-home-event)
       ((chart home) start-event))))

(print-expression
  (proper-time-interval
    (- ((chart outgoing) halfway-at-home-event)
       ((chart outgoing) start-event))))

(define home-at-outgoing-turning-point-event
  ((point outgoing)
   (e/make-SR-coordinates
     outgoing
     (up (* 7 'c) (* 7 (/ -24 25) 'c) 0 0))))

(print-expression
  (proper-time-interval
    (- ((chart home) home-at-outgoing-turning-point-event)
       ((chart home) start-event))))

(define incoming
  (e/make-SR-frame 'incoming home
                   (up -1 0 0) (/ 24 25)
                   ((chart home)
                    traveller-at-turning-point-event)))

(define end-event
  ((point home)
   (e/make-SR-coordinates home (up (* 'c 50) 0 0 0))))

(print-expression
  (- ((chart incoming) end-event)
     (make-SR-coordinates incoming
                          (up (* 'c 7) 0 0 0))))
(print-expression
  (- ((chart home) end-event)
     ((chart home)
      ((point incoming)
       (make-SR-coordinates incoming
                            (up (* 'c 7) 0 0 0))))))

(print-expression
  (+ (proper-time-interval
       (- ((chart outgoing) traveller-at-turning-point-event)
          ((chart outgoing) start-event)))
     (proper-time-interval
       (- ((chart incoming) end-event)
          ((chart incoming) traveller-at-turning-point-event)))))

(print-expression
  (proper-time-interval
    (- ((chart home) end-event)
       ((chart home) start-event))))

(define home-at-incoming-turning-point-event
  ((point incoming)
   (e/make-SR-coordinates incoming
                          (up 0 (* 7 (/ -24 25) 'c) 0 0))))

(print-expression
  (proper-time-interval
    (- ((chart home) end-event)
       ((chart home) home-at-incoming-turning-point-event))))

(repl/scittle-sidebar)
