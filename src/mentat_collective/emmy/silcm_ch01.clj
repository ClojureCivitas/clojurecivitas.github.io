^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Lorentz Covariant Mechanics"
                     :quarto {:author   :kloimhardt
                              :type     :draft
                              :description "A single particle in the four flat dimensions of Special Relativity"
                              :sidebar  "emmy-fdg"
                              :date     "2026-01-11"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.silcm-ch01
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda]]
            [civitas.repl :as repl]))

;; I investigate a Lorentz covariant Lagrangian that is not widely known. It is discussed in the textbooks of Greiner, "Systems of Particles and Hamiltonian Mechanics" (chapter 21), and also [on-line in: Cline, "Variational Principles in Classical Mechanics"](https://phys.libretexts.org/Bookshelves/Classical_Mechanics/Variational_Principles_in_Classical_Mechanics_(Cline)/17%3A_Relativistic_Mechanics/17.06%3A_Lorentz-Invariant_Formulation_of_Lagrangian_Mechanics).

;; Then I derive, along deBroglie's argument, the momentum-wavelength relation of a free particle.

#_"TODO: Make a button for @path-dimension to make dimensions switchable from 4 -> 2"

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
  '[emmy.env :refer :all :exclude [Lagrange-equations r->p]])

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
(kind/scittle
  '(defn show-expression [e]
     (simplify e)))

^:kindly/hide-code
(kind/scittle
  '(defn show-tex-expression [e]
     (->infix (simplify e))))

^:kindly/hide-code
(kind/scittle
  '(defn show-tex [e]
     (->infix e)))

^:kindly/hide-code
(define (eq-transformation f)
  (lambda (tuple)
          (apply down (map f tuple))))

^:kindly/hide-code
(kind/scittle
  '(defn show-eq [tuple]
     (->infix (simplify (down (first tuple) '= (second tuple))))))

^:kindly/hide-code
(def tex (comp kind/tex emmy.expression.render/->TeX))

^:kindly/hide-code
(def tex-simp (comp tex simplify))

^:kindly/hide-code
(defn fn-show-eq [tuple]
  (tex-simp (down (first tuple) '= (second tuple))))

^:kindly/hide-code
(define show-exp (comp str simplify))

^:kindly/hide-code
(defn reag-comp [b]
  (let [server-erg (show-exp (eval b))]
    (list 'kind/reagent
          [:div (list 'quote
                      (list 'let ['a (list 'show-exp b)]
                            [:div
                             (when (not prod)
                               [:div
                                [:tt 'a]
                                [:p (list 'str (list '= server-erg 'a))]])
                             [:tt server-erg]]))])))

^:kindly/hide-code
(defmacro show-expression [e]
  (if prod
    (list 'simplify e)
    (reag-comp e)))

^:kindly/hide-code
(defmacro show-tex-expression [e]
  (if prod
    (list 'tex-simp e)
    (reag-comp e)))

^:kindly/hide-code
(defmacro show-tex [e]
  (if prod
    (list 'tex e)
    (reag-comp e)))

^:kindly/hide-code
(defmacro show-eq [e]
  (if prod
    (list 'fn-show-eq e)
    (reag-comp e)))

^:kindly/hide-code
(define velocities velocity)

^:kindly/hide-code
(define coordinates coordinate)

^:kindly/hide-code
(define vector-length count)

^:kindly/hide-code
(define time first)

;; ## Extended Lagrangian

;; I start with the conventional case

(define ((L-free-particle mass) local)
  (let ((v (velocity local)))
    (* 1/2 mass (square v))))

(define (test-path t)
  (up (+ (* 4 t) 7)
      (+ (* 3 t) 5)
      (+ (* 2 t) 1)))

(show-tex-expression
  (test-path 't))

(Lagrangian-action (L-free-particle 3.0) test-path 0.0 10.0)

;; The extended Lagrangian:

(define (fourtuple->t v)
  (ref v 0))

(define (fourtuple->space v)
  (apply up (rest v)))

(define ((Lc-free-particle mass c) local)
  (let ((v (velocity local)))
    (* 1/2 mass (square c)
       (- (* (/ 1 (square c))
             (square (fourtuple->space v)))
          (square (fourtuple->t v))
          1))))

;; According to chapter 4.3.2 of Struckmeier "Extended Lagrange and Hamilton Formalism for Point Mechanics and Covariant Hamilton Field Theory", this "case is largely disregarded in the literature covering the extended Hamilton- Lagrange formalism (cf., for instance, Lanczos, 1949; Dirac, 1950; Synge, 1960; Goldstein et al., 2002; Johns, 2005), namely there exist extended Lagrangians that are related to a given conventional Lagrangian without being homogeneous forms in the n + 1 velocities".

(define (Lc-test-path s)
  (up s
      (+ (* 4 s) 7)
      (+ (* 3 s) 5)
      (+ (* 2 s) 1)))

(show-tex-expression
  (Lc-test-path 's))

(Lagrangian-action (Lc-free-particle 3.0 1.0) Lc-test-path 0.0 10.0)

;; That's 30 less, let's see where we lost those 30

(show-tex-expression
  ((Gamma test-path) 't))

;; Can you guess the following? (if not, use the sidebar!)

^{:kindly/kind :kind/hidden}
(show-tex-expression
  ((Gamma Lc-test-path) 's))

^:kindly/hide-code
(define path-dimension (atom 2))

^:kindly/hide-code
(define three-path
  (case @path-dimension
    4
    (up (literal-function 'x)
        (literal-function 'y)
        (literal-function 'z))
    3
    (up (literal-function 'x)
        (literal-function 'y))
    2
    (up (literal-function 'x))))

(show-tex-expression
  ((Gamma three-path) 't))

(show-tex-expression
  ((compose (L-free-particle 'm) (Gamma three-path)) 't))

^:kindly/hide-code
(define four-path
  (case @path-dimension
    4
    (up identity
        (literal-function 'x)
        (literal-function 'y)
        (literal-function 'z))
    3
    (up identity
        (literal-function 'x)
        (literal-function 'y))
    2
    (up identity
        (literal-function 'x))))

(show-tex-expression
  ((Gamma four-path) 's))

;; Note the number 1 on top of the last 4-tuple. This will be relaxed later.

(show-tex-expression
  ((compose (Lc-free-particle 'm_0 'c) (Gamma four-path)) 's))

;; In calculating the Lagrangian action above, $m_0$ was $3$, $c$ set to $1$ and time was $10$. Thus the $-30$ stems from the $-m_0 c^2$.

(define (Lc-momentum Lagrangian)
  ((partial 2) Lagrangian))

(define ((Lagrange-momentum Lagrangian) w)
  (compose (Lc-momentum Lagrangian) (Gamma w)))

(define ((Lagrange-equations Lagrangian) w)
  (- (D ((Lagrange-momentum Lagrangian) w))
     (compose ((partial 1) Lagrangian) (Gamma w))))

(show-tex-expression
  (((Lagrange-equations (L-free-particle 'm)) three-path) 't))

(show-tex-expression
  (((Lagrange-equations (Lc-free-particle 'm_0 'c)) four-path) 's))

;; The first $0$ is because we set $t=s$ for the path. This reduces the extended Lagrangian to the "conventional" case. Reassuring. Now we can allow arbitrary functions $t(s)$, making the path truely 4-dimensional.

^:kindly/hide-code
(define Lc-path
  (case @path-dimension
    4
    (up (literal-function 't)
        (literal-function 'x)
        (literal-function 'y)
        (literal-function 'z))
    3
    (up (literal-function 't)
        (literal-function 'x)
        (literal-function 'y))
    2
    (up (literal-function 't)
        (literal-function 'x))))

(show-tex-expression
  (((Lagrange-equations (Lc-free-particle 'm_0 'c)) Lc-path) 's))

(show-tex-expression
  (((Lagrange-momentum (Lc-free-particle 'm_0 'c)) Lc-path) 's))

;;State the Lagrangian.

(show-tex-expression
  ((compose (Lc-free-particle 'm_0 'c) (Gamma Lc-path)) 's))

;; According to Struckmeier p80, "In contrast to the non-relativistic description, the constant rest energy term $− 1/2 mc^2$ in the extended Lagrangian is essential". I guess this constant term makes the Lagrangian "not being a homogeneous form in the velocities".

;; It is very interesting that a constant term can lead to a new formalism. Cline: "It provides a plausible manifestly-covariant Lagrangian for the one-body system, but serious problems exist extending this to the N-body system when N>1".

;; Nobody possesses a Lorentz covariant N-body mechanics. Goldstein "Classical Mechanics", chapter 7.9: "Hitherto, there does not exist a satisfying description of the relativistic many body system."

;; ### The Constraint on the four dimensional path

;; The Lagranian above looks very nice because it is symmtreic in t, x, y, z. But there is a caveat which ultimately brings it in line with the orthodox formulation.

;; Non-homogeneous but symmetric Lagrangians of this type require a general implicit constraint: $L - pv = 0$ (Cline Eq. 17.6.12, Struckmeier Eq. 21.9)

(define (Lc-Lagrange-constraint Lagrangian)
  (- Lagrangian (* (Lc-momentum Lagrangian) velocity)))

;; Applied to the free particle (and multiplication with a constant factor), leads to (Struckmeier Eq. 21.12):

(define Lc-constraint-s
  (down (constantly 0)
        (compose (* (/ 2 (square 'c) 'm_0)
                    (Lc-Lagrange-constraint (Lc-free-particle 'm_0 'c)))
                 (Gamma Lc-path))))

^:kindly/hide-code
(show-eq
  (Lc-constraint-s 's))

;; The above is a constraint, i.e. needs to be zero, needs to vanish for all physical paths.

;; For the free particle, we should think of it as a constraint on p, a constraint known as the mass shell or on-shell condition, usually written (thereby omitting the c) as $-p^2 = m^2$. This on-shell constraint is the correct version of the popular $E = mc^2$. Let's show that the above constraint indeed is the same as $-p^2 = m^2c^2$:

(define p
  ((Lagrange-momentum (Lc-free-particle 'm_0 'c)) Lc-path))

(show-tex-expression
  (p 's))

;; (for defining the next "square", notice that for a 4-vector this involves a minus sign)

(define ((foursquare c) v)
  (- (square (fourtuple->space v))
     (/ (square (fourtuple->t v)) (square c))))

(show-eq
  (down
    (* (square 'm_0) (square 'c))
    (- ((foursquare 'c) (p 's)))))

;; Contrary to constraints that depend on position, this constraint (being about velocity) cannot be easily treated via Lagrange multipliers.

;; We proceed to explicitely calculate $(Dt(s))^2$

(define t (literal-function 't))

(define Dt-squared
  ((eq-transformation
     #(- (square (D t)) %))
   Lc-constraint-s))

^:kindly/hide-code
(show-eq
  (Dt-squared 's))

;; We now upgrade the parameter $s$ to a function $s(t)$ which is the inverse of $t(s)$, $s = t^{-1}$.

(define s (literal-function 's))

;; Inverse means that $t(s(y)) = y$. While in the math rendering there is a minor risk of confusion, in the underlying code we do not need the auxilary `y`, the function `t` and the symbol `'t` are clearly distinct:

(define t°s (down (comp t s) identity))

(show-eq
  (t°s 't))

;; The derivative results in the number `one`

(show-eq
  ((D t°s) 't))

;; With this interlude, we return to the constraint. Just by formally replacing $s \rightarrow s(t)$, it reads as

(show-eq
  (Dt-squared (s 't)))

;; We'd like to get rid of the function `t` and only keep the symbol/parameter `'t`. For this, we multiply the above equation by $(Ds(t))^2$

(define eq1
  ((eq-transformation
     #(* ((square (D s)) 't) %))
   (* (Dt-squared (s 't)))))

(show-eq eq1)

;; We introduce the following slighly sloppy notation

(show-eq
  ((down three-path (comp three-path s)) 't))

;; The derivative of this definition is
(show-eq
  ((D (down three-path (comp three-path s))) 't))

;; With this the constraint becomes

^:kindly/hide-code
(define constraint-1
  (down 1 (/ (+ (* (square 'c)
                   ((square (D (literal-function 's))) 't))
                ((square (D three-path)) 't))
             (square 'c))))

(show-eq
  constraint-1)

;; Remember that, as shown above, the number `one` appears because of $s = t^{-1}$

;; Isolating $Ds(t)$ on the right hand side

(show-eq
  ((eq-transformation
     #(sqrt (- % (/ ((square (D three-path)) 't) (square 'c)))))
   constraint-1))

;; The above equation, which is just another expression of the constraint $L - pv = 0$, is conventionally written as below, introducing the famous $\gamma$ factor of Special Relativity.

^:kindly/hide-code
(kind/tex
  "\\frac{ds}{dt} = \\sqrt{1- \\frac{v^2}{c^2}} = \\sqrt{1 - \\beta ^ 2} = \\frac{1}{\\gamma}")

;; Thus, the function $s(t)$ is nothing but the proper time.

;; ## The dynamic mass

(define ((Lc-dynamic mass c) local)
  (let ((q (coordinate local))
        (v (velocity local)))
    (* 1/2
       (- (* (ref q 2)
             (square (ref v 1)))
          (* (ref q 2) (square c)
             (square (ref v 0)))
          (* (/ (square mass) (ref q 2)) (square c))))))

(define dynamic-path
  (up (literal-function 't)
      (literal-function 'x)
      (literal-function 'sigma)))

;; For the record, I'd like to state the momentum of this particle, it will reveal an aha later.

(show-tex-expression
  ((foursquare 'c)
   (((Lagrange-momentum (Lc-dynamic 'm 'c)) dynamic-path) 's)))

;; That said, we move on to the equations of motion are

(define dynamic-equations
  ((Lagrange-equations (Lc-dynamic 'm 'c)) dynamic-path))

(show-tex-expression
  (dynamic-equations 's))

;; We do not know yet what the first two equations means, but the last equation will tell.

(define sigma
  (let ((sigma-sq (square (literal-function 'sigma)))
        (half-m2-c2 (* 1/2 (square 'c) (square 'm))))
    ((eq-transformation
       (fn [eq]
         (sqrt
           (* half-m2-c2
              (/ 1
                 (/ (+ (* sigma-sq eq)
                       half-m2-c2)
                    sigma-sq))))))
     (down 0 (ref dynamic-equations 2)))))

(show-eq
  (sigma 's))

;; Taking the $-p^2 = m^2$ on-shell constraint from above, we see that $\sigma(s) = m$

(show-eq
  (((eq-transformation
       #(sqrt (+ (square 'c) (* (square 'c) %))))
    Lc-constraint-s)
   's))

;; If you look at the equations of motion above, when you let $\sigma(s) \rightarrow m$, you get the motions of the free particle. All this can be achived much easier by directly setting $\sigma(s) = m(s) = m$ in the calculation of the equations of motion.

(define (on-shell-path m)
  (up (literal-function 't)
      (literal-function 'x)
      (constantly m)))


(show-tex-expression
  (((Lagrange-equations (Lc-dynamic 'm 'c))
    (on-shell-path 'm))
   's))

;; Indeed these are the euqations of motion of the free farticle plus the on-shell constraint.


;; ## The case of the Photon

;; As stated above, the momentum for any mass in general is

(show-tex-expression
  ((foursquare 'c)
   (((Lagrange-momentum (Lc-dynamic 'm 'c)) dynamic-path) 's)))

;; We can set $m$ to zero in the Lagrangian

(show-tex-expression
  (((Lagrange-equations (Lc-dynamic 0 'c))
    dynamic-path)
   's))

;; Whatever the first two equations of motion mean in this zero-mass case, we see from the last equation that the square of the momentum $p^2$ of the massless particle will be zero.

#_"TODO solve the avove equation and find out if it means ds/dt = gamma or so"

;; ## The deBroglie wavelength

^:kindly/hide-code
(kind/scittle
  '(defn is-equal [a b]
     (if (= (simplify (- a b)) 0)
       (show-tex a)
       "not equal")))

^:kindly/hide-code
(kind/scittle
  '(defn solves [a f]
     (if (= (simplify (f a)) 0)
       (show-tex (* (symbol "root:") a))
       "does not solve")))

^:kindly/hide-code
(defmacro is-equal [a b]
  (list 'if (list '= (list 'simplify (list '- a b)) 0)
        (list 'show-tex a)
        "not equal"))

^:kindly/hide-code
(defmacro solves [a f]
  (list 'if (list '= (list 'simplify (list f a)) 0)
    (list 'show-tex (list '* (list 'symbol "root:") a))
    "does not solve"))

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

#_(repl/scittle-sidebar)
