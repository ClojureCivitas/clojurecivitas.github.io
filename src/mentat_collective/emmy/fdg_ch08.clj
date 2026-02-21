^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter Eight"
                     :quarto {:author      :kloimhardt
                              :type        :draft
                              :description "Functional Differential Geometry: Chapter 8"
                              :sidebar     "emmy-fdg"
                              :date        "2026-02-20"
                              :image       "sicm_ch01.png"
                              :category    :libs
                              :tags        [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch08
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial = test])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda] :as scheme]
            [civitas.repl :as repl]))

;; ## 8 Curvature
;; If the intrinsic curvature of a manifold is not zero, a vector parallel- transported around a small loop will end up different from the vector that started.

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
                                         Riemann-curvature Riemann Ricci
                                         torsion-vector torsion
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


^{:kindly/hide-code true :kindly/kind kind/hidden}
(do
  (require emmy-env)
  (require emmy-vector-field)
  (require emmy-aggregate)
  (require emmy-form-field)
  (require emmy-structure)
  (require emmy-operator))

^:kindly/hide-code
(kind/scittle
  '(do
     (require emmy-env)
     (require emmy-vector-field)
     (require emmy-aggregate)
     (require emmy-form-field)
     (require emmy-structure)
     (require emmy-operator)))

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

(define ((Riemann-curvature nabla) w v)
  (- (commutator (nabla w) (nabla v))
     (nabla (commutator w v))))

(define ((Riemann nabla) omega u w v)
  (omega (((Riemann-curvature nabla) w v) u)))


(define S2-basis
  (coordinate-system->basis S2-spherical))

(define-coordinates (up theta phi) S2-spherical)

(define (S2-Christoffel basis theta)
  (let ((zero zero-manifold-function))
    (make-Christoffel
      (down (down
              (up zero zero)
              (up zero (/ 1 (tan theta))))
            (down
              (up zero (/ 1 (tan theta)))
              (up (- (* (sin theta)
                        (cos theta)))
                  zero)))
      basis)))

(define sphere-Cartan (Christoffel->Cartan (S2-Christoffel S2-basis theta)))

(print-expression
  (((Riemann (covariant-derivative sphere-Cartan))
    dphi d:dtheta d:dphi d:dtheta)
   ((point S2-spherical) (up 'theta0 'phi0))))

;; ### 8.1 Explicit Transport
;; ### Verification in Two Dimensions

(define (make-state sigma u) (vector sigma u))

(define (Sigma state) (ref state 0))

(define (U-select state) (ref state 1))

(define Chi-inverse (point R2-rect)) (define Chi (chart R2-rect))

(define general-Cartan-2
  (Christoffel->Cartan
    (literal-Christoffel-2 'Gamma R2-rect)))

(define ((Du v) state)
  (let ((CF (Cartan->forms general-Cartan-2)))
    (* -1
       ((CF v) (Chi-inverse (Sigma state))) (U-select state))))

(define ((Dsigma v) state)
  ((v Chi) (Chi-inverse (Sigma state))))

(define ((g v) state)
  (make-state ((Dsigma v) state) ((Du v) state)))

(define (L v)
  (define (l h)
    (lambda (state)
            (* ((D h) state) ((g v) state)))) (o/make-operator l))

(define result
  (let ((U (literal-vector-field 'U-rect R2-rect))
        (W (literal-vector-field 'W-rect R2-rect))
        (V (literal-vector-field 'V-rect R2-rect))
        (sigma (up 'sigma0 'sigma1)))
    (let ((nabla (covariant-derivative general-Cartan-2))
          (m (Chi-inverse sigma)))
      (let ((s (make-state sigma ((U Chi) m))))
        (- (((- (commutator (L V) (L W))
                (L (commutator V W)))
             U-select)
            s)
           (((((Riemann-curvature nabla) W V) U) Chi) m))))))

(print-expression result)

;; ### Geometrically

(define ((curvature-from-transport Cartan) w v)
  (lambda (u)
          (lambda (f)
                  (let ((CF (Cartan->forms Cartan))
                        (basis (Cartan->basis Cartan))
                        (fi (basis->oneform-basis basis))
                        (ei (basis->vector-basis basis)))
                    (* (ei f)
                       (+ (* (- (- (w (CF v)) (v (CF w)))
                                (CF (commutator w v)))
                             (fi u))
                          (- (* (CF w) (* (CF v) (fi u)))
                             (* (CF v) (* (CF w) (fi u))))))))))

(define (test coordsys Cartan)
  (let ((m (typical-point coordsys))
        (u (literal-vector-field 'u-coord coordsys))
        (w (literal-vector-field 'w-coord coordsys))
        (v (literal-vector-field 'v-coord coordsys))
        (f (literal-manifold-function 'f-coord coordsys)))
    (let ((nabla (covariant-derivative Cartan)))
      (- (((((curvature-from-transport Cartan) w v) u) f) m)
         (((((Riemann-curvature nabla) w v) u) f) m)))))

(print-expression
  (test R2-rect general-Cartan-2))

(print-expression
  (test R2-polar general-Cartan-2))

;; ### Terms of the Riemann Curvature

(define result
  (let ((U (literal-vector-field 'U-rect R2-rect))
        (V (literal-vector-field 'V-rect R2-rect))
        (W (literal-vector-field 'W-rect R2-rect))
        (nabla (covariant-derivative general-Cartan-2))
        (sigma (up 'sigma0 'sigma1)))
    (let ((m (Chi-inverse sigma)))
      (let ((s (make-state sigma ((U Chi) m))))
        (- (((commutator (L W) (L V)) U-select) s)
           ((((commutator (nabla W) (nabla V)) U) Chi)
            m))))))

(print-expression result)

(define result
  (let ((U (literal-vector-field 'U-rect R2-rect))
        (V (literal-vector-field 'V-rect R2-rect))
        (W (literal-vector-field 'W-rect R2-rect))
        (nabla (covariant-derivative general-Cartan-2))
        (sigma (up 'sigma0 'sigma1)))
    (let ((m (Chi-inverse sigma)))
      (let ((s (make-state sigma ((U Chi) m))))
        (- (((commutator (L W) (L V)) U-select) s)
           ((((nabla (commutator W V)) U) Chi)
            m))))))

(print-expression result)

;; ### Ricci Curvature

(define ((Ricci nabla basis) u v)
  (contract (lambda (ei wi) ((Riemann nabla) wi u ei v))
            basis))

;; ### Exercise 8.2: Pseudosphere

(define (pseudosphere q)
  (let ((t (ref q 0)) (theta (ref q 1)))
    (up (* (sech t) (cos theta))
        (* (sech t) (sin theta))
        (- t (tanh t)))))


;; ### Torsion


(define ((torsion-vector nabla) u v)
  (- (- ((nabla u) v) ((nabla v) u))
     (commutator u v)))

(define ((torsion nabla) omega u v)
  (omega ((torsion-vector nabla) u v)))

(print-expression
  (for [x [d:dtheta d:dphi]
        y [d:dtheta d:dphi]]
    (simplify
      ((((torsion-vector (covariant-derivative sphere-Cartan))
         x y)
        (literal-manifold-function 'f S2-spherical))
       ((point S2-spherical) (up 'theta0 'phi0))))))

;; ### Longitude Lines on a Sphere

(define T d:dtheta)
(define U d:dphi)
(define omega (literal-oneform-field 'omega S2-spherical))
(define m ((point S2-spherical) (up 'theta0 'phi0)))
(define S2C (S2-Christoffel S2-basis theta))
(define Cartan (Christoffel->Cartan S2C))
(define nabla (covariant-derivative Cartan))

(print-expression
  ((omega (((covariant-derivative Cartan) T) T)) m))

(define f (literal-manifold-function 'f S2-spherical))

(print-expression
  (((commutator U T) f) m))

(print-expression
  (let-scheme ((X (literal-vector-field 'X-sphere S2-spherical))
               (Y (literal-vector-field 'Y-sphere S2-spherical)))
    ((((torsion-vector nabla) X Y) f) m)))

(print-expression
  ((+ (omega ((nabla T) ((nabla T) U)))
      ((Riemann nabla) omega T U T))
   m))

(print-expression
  ((dphi ((nabla T) U)) m))

(print-expression
  ((dphi ((nabla T) ((nabla T) U))) m))


(define ((delta R) phi theta Delta-phi)
  (* R (sin theta) Delta-phi))

(print-expression
  (((partial 1) (delta 'R)) 'phi0 'theta0 'Delta-phi))

(define phi-hat
  (* (/ 1 (sin theta)) d:dphi))

(print-expression
  ((dphi (* (((partial 1) (delta 'R))
             'phi0 'theta0 'Delta-phi)
            phi-hat))
   m))

(print-expression
  (((partial 1) ((partial 1) (delta 'R)))
   'phi0 'theta0 'Delta-phi))

(print-expression
  ((dphi (* (((partial 1) ((partial 1) (delta 'R)))
             'phi0 'theta0 'Delta-phi)
            phi-hat))
   m))

;; ### Bianchi Identities

;; in the book, the following is R4-rect, but calculations take too long
(define coord-sys R2-rect)

(define nabla
  (covariant-derivative
    (Christoffel->Cartan
      (symmetrize-Christoffel
        (literal-Christoffel-2 'C coord-sys)))))

(define omega (literal-oneform-field 'omega-rect coord-sys))
(define X (literal-vector-field 'X-rect coord-sys))
(define Y (literal-vector-field 'Y-rect coord-sys))
(define Z (literal-vector-field 'Z-rect coord-sys))
(define V (literal-vector-field 'V-rect coord-sys))

(print-expression
  (((torsion nabla) omega X Y)
   (typical-point coord-sys)))

(define ((cyclic-sum f) x y z)
  (+ (f x y z)
     (f y z x)
     (f z x y)))

(print-expression
  (((cyclic-sum
      (lambda (x y z)
              ((Riemann nabla) omega x y z)))
    X Y Z)
   (typical-point coord-sys)))

(print-expression
  (((cyclic-sum
      (lambda (x y z)
              (((nabla x) (Riemann nabla))
               omega V y z)))
    X Y Z)
   (typical-point coord-sys)))

;; Things get more complicated when there is torsion. We can make a general connection, which has torsion:

(define nabla
  (covariant-derivative
    (Christoffel->Cartan
      (literal-Christoffel-2 'C coord-sys))))

(define R (Riemann nabla))
(define T (torsion-vector nabla))

(define (TT omega x y)
  (omega (T x y)))

(print-expression
  (((cyclic-sum
      (lambda (x y z)
              (- (R omega x y z)
                 (+ (omega (T (T x y) z))
                    (((nabla x) TT) omega y z)))))
    X Y Z)
   (typical-point coord-sys)))

(print-expression
  (((cyclic-sum
      (lambda (x y z)
              (+ (((nabla x) R) omega V y z)
                 (R omega V (T x y) z))))
    X Y Z)
   (typical-point coord-sys)))

(repl/scittle-sidebar)
