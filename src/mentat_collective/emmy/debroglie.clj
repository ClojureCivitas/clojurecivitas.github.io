^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: deBroglie wavelength"
                     :quarto {:author   :kloimhardt
                              :type     :draft
                              :description "A particle riding its wave whose length depends on the former's momentum"
                              :sidebar  "emmy-fdg"
                              :date     "2026-01-27"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.debroglie
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda] :as scheme]
            [civitas.repl :as repl]))

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
  '[emmy.env :refer :all :exclude [Lagrange-equations r->p]])

^{:kindly/hide-code true :kindly/kind kind/hidden}
(do
  (require emmy-env))

^:kindly/hide-code
(kind/scittle
  '(do
     (require emmy-env)))

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
(defmacro is-equal [a b]
  (list 'if (list '= (list 'simplify (list '- a b)) 0)
        (list 'show-tex a)
        "not equal"))

^:kindly/hide-code
(kind/scittle
  '(defn is-equal [a b]
     (if (= (simplify (- a b)) 0)
       (show-tex a)
       "not equal")))

^:kindly/hide-code
(defmacro solves [a f]
  (list 'if (list '= (list 'simplify (list f a)) 0)
    (list 'show-tex (list '* (list 'symbol "root:") a))
    "does not solve"))

^:kindly/hide-code
(kind/scittle
  '(defn solves [a f]
     (if (= (simplify (f a)) 0)
       (show-tex (* (symbol "root:") a))
       "does not solve")))

;; In the following I follow deBroglies arguments. There is also a [version with infix notation](https://kloimhardt.github.io/blog/hamiltonmechanics/2024/09/13/debroglie.html).

;; ### Internal Vibrations
;;as always with Einstein, we start with E = mc^2

(define (E0 m) (* m (square 'c)))

(show-tex
  (E0 'm))

;; deBroglies first hypothesis was to assume that every particle has a hypothetical internal vibration at frequency nu0 which relates to the rest energy in rest frame of particle (only there this energy-frequency relation holds)

(define (nu_naught E0) (/ E0 'h))

;; particle travels at velocity vp

(define (vp beta) (* beta  'c))

(define (beta v) (/ v 'c))

(define (gamma beta) (/ 1 (sqrt (- 1 (square beta)))))

;; time dilation: internal vibration is slower for observer. so the frequency-energy relation does not hold: the frequency indeed decreases instead of increasing with energy. this is the conundrum deBroglie solved. so hang on.

(define (nu_one nu_naught gamma) (/ nu_naught gamma))

;; sine formula for internal vibration. we do not know what exactly vibrates so we set the amplitude to one

(define ((internal-swing nu_one) t)
 (sin (* 2 'pi nu_one t)))

(show-tex
  ((internal-swing 'nu_one) 't))

;; calculate the phase of the internal swing at particle point x = v * t

(define ((internal-phase nu_one v) x)
 (asin ((internal-swing nu_one) (/ x v))))

(is-equal (* 2 'pi 'nu_one (/ 'x 'v))
          ((internal-phase 'nu_one 'v) 'x))

;; personal note: to me, this is the sine-part of a standing wave, the standing vibration.

;;### A general Wave

;; now for something completely different: general definition of a wave

(define ((wave omega k) x t)
 (sin (- (* omega t) (* k x))))

;; with the usual definition of omega

(define (omega nu) (* 2 'pi nu))

;; and the simplest possible definition for the wave-vector k: a dispersion free wave traveling at phase-velocity V

(define (k omega V) (/ omega V))

;; calculate the phase of the wave

(define ((wave-phase nu V) x t)
 (asin ((wave (omega nu) (k (omega nu) V)) x t)))

(is-equal (* 2 'pi 'nu (- 't (/ 'x 'V)))
          ((wave-phase 'nu 'V) 'x 't))

;; ### Phase difference
;; calculate the phase difference between the vibration and some wave at time t = x / v as a function of the ratio of the frequencies

(define ((phase-difference x v nu V) ratio)
  (- ((internal-phase (* ratio nu) v) x)
     ((wave-phase nu V) x (/ x v))))

(is-equal (* 2 'pi 'nu (+ (* (- 'ratio 1) (/ 'x 'v)) (/ 'x 'V)))
          ((phase-difference 'x 'v 'nu 'V) 'ratio))

;; state the general ratio of frequencies that keeps the vibration of the particle in phase with some wave of velocity V in terms of the velocity of the particle

(define (phase-ratio v V) (- 1 (/ v V)))

(solves (phase-ratio 'v 'V)
        (phase-difference 'x 'v 'nu 'V))

;; the Energy of the particle for the observer

(define (Ev E0 gamma) (* E0 gamma))

;; we assume the deBroglie wave has the frequency: energy divided by Planck's constant. reminder: this relation holds in every frame of reference, especially for the observer who is not in the rest frame.

(define (nu Ev) (/ Ev 'h))

;; now that nu is set, calculate the physically viable ratio of the frequencies in terms of beta

(define (physical-ratio beta)
  (/ (nu_one (nu_naught 'E0) (gamma beta))
     (nu (Ev 'E0 (gamma beta)))))

(is-equal (- 1 (square 'beta))
          (physical-ratio 'beta))

;; state, in terms of the particle velocity beta, the value of the physical phase-velocity V that keeps the vibration and the deBroglie wave in phase

(define (phase-velocity beta) (/ 'c beta))

(solves (phase-velocity 'beta)
        (lambda (V) (- (physical-ratio 'beta)
                       (phase-ratio (vp 'beta) V))))

;; note: the phase-velocity is always greater than the speed of light. It is independent of the position x and the mass of the particle

;; the relativistic momentum is defined as

(define (p m v gamma)
 (* m v gamma))

;; calculate the deBroglie wavelength (by dividing the phase-velocity by the frequency) and show that it indeed is h divided by the momentum

(define de-broglie-wavelength
  (/ (phase-velocity (beta 'v))
     (nu (Ev (E0 'm) 'gamma))))

(is-equal (/ 'h (p 'm 'v 'gamma))
          de-broglie-wavelength)

(repl/scittle-sidebar)
