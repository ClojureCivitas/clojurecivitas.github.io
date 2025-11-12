^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Classical Mechanics Chapter One"
                     :quarto {:author   :kloimhardt
                              :type     :post
                              :date     "2025-09-10"
                              :image    "sicm_ch01.png"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.sicm-ch01
    (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator infinite? abs ref partial =])
    (:require [emmy.env :refer :all :exclude [r->p]]
              [emmy.mechanics.lagrange :as lg]
              [emmy.numerical.minimize :as mn]
              [mentat-collective.emmy.scheme :refer :all]
              [scicloj.kindly.v4.api :as kindly]
              [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(def velocities velocity)

^:kindly/hide-code
(def coordinates coordinate)

^:kindly/hide-code
(def vector-length count)

^:kindly/hide-code
(defn time [state] (first state))

^:kindly/hide-code
(def tex (comp kind/tex emmy.expression.render/->TeX simplify))

^:kindly/hide-code
(def md
  (comp kindly/hide-code kind/md))

(md "The following examples are taken from the MIT open-access book [Structure and Interpretation of Classical Mechanics (SICM)](https://mitp-content-server.mit.edu/books/content/sectbyfn/books_pres_0/9579/sicm_edition_2.zip/chapter001.html).")

(md "Another notebook can be found on the [Road to Reality website](https://reality.mentat.org/essays/reality/introduction#welcome-to-the-road-to-reality!) by [Sam Ritchie](https://roadtoreality.substack.com/p/the-first-executable-essay-is-live), the author (along with [Colin Smith](https://github.com/littleredcomputer)) of [Emmy, the Computer Algebra System](https://emmy.mentat.org).")

;; In adopting MIT-Scheme's `(define ...)`, I trust that Clojure people will bridge that gap quickly
;; while being sure of the gratitude of all readers of the immutable, dense book.

(md "## 1.4 Computing Actions")
(md "First task: Calculate the action for the free particle along a path. Consider the particle moving at uniform speed along a straight line.")

(define (test-path t)
  (up (+ (* 4 t) 7)
      (+ (* 3 t) 5)
      (+ (* 2 t) 1)))

(Lagrangian-action (lg/L-free-particle 3.0) test-path 0.0 10.0)

(md "#### Paths of minimum Action")
(md "Show that the action is smaller along a straight-line test path than along nearby paths")

(define ((make-η ν t1 t2) t)
  (* (- t t1) (- t t2) (ν t)))

(define ((varied-free-particle-action mass q ν t1 t2) ε)
  (let ((η (make-η ν t1 t2)))
    (Lagrangian-action (lg/L-free-particle mass)
                       (+ q (* ε η)) t1 t2)))

((varied-free-particle-action 3.0 test-path (up sin cos square) 0.0 10.0) 0.01)

(md "Simulate lots of the paths in this manner. Proof that the minimum value of the action is the action along the straight path. For this proof it suffices that some optimization parameter is close to zero (need not be exactly zero).")

(minimize (varied-free-particle-action 3.0 test-path (up sin cos square) 0.0 10.0) -2 1)

^:kindly/hide-code
(do
  (defn points->plot [paths x-axis-name y-axis-name]
    (let [coord-encoding (fn [coord] {:field coord :type "quantitative" :scale {:zero false}})
          paths-to-data  (flatten
                          (map (fn [[id data]]
                                 (map (fn [[t x y z]]
                                        {:id id :t t :x x :y y :z z}) data)) paths))]
      {:$schema "https://vega.github.io/schema/vega-lite/v2.json"
       :data    {:values paths-to-data}
       :encoding
       {:x (coord-encoding x-axis-name)
        :y (coord-encoding y-axis-name)}
       :layer
       [{:mark "line"
         :encoding
         {:order {:field "t" :type "temporal"}
          :color {:field "id" :type "nominal"}}}
        {:mark {:type "point" :filled true}}]}))

  (defn linear-interpolants-a [t0 t1 nofsteps]
    (map float (flatten [t0 (linear-interpolants t0 t1 (- nofsteps 2)) t1])))

  (defn make-path-txyz [fn_t t0 t1 nofsteps]
    (mapv #(cons % (.v (fn_t %)))
          (linear-interpolants-a t0 t1 nofsteps)))

  (defn points-xy->plot [paths]
    (points->plot paths "x" "y"))

  (defn points-xz->plot [paths]
    (points->plot paths "x" "z"))

  (defn points-tz->plot [paths]
    (points->plot paths "t" "z"))

  :end_do)

^:kindly/hide-code
(comment
 (def werte {"sb" [[0 7 5 1] [1 11 8 10]]
            "uv" [[2 9 2 4] [3 3  9 7]]})

(kind/vega-lite (points-xz->plot werte)))

(md "Create data to plot two straight paths in the xy plane. One path is along the x axis (name: path-along-x), the second path leads in all directions.")

(define (path-along-x t)
  (up (+ (* 5 t) 1)
      (* 0 t)
      (* 0 t)))

(define path-points-1
  (hash-map "a straight x" (make-path-txyz path-along-x 0 10 8)
            "b book" (make-path-txyz test-path 0 10 8)))

(md "Plot the two paths")

(kind/vega-lite
  (points-xy->plot path-points-1))

(md "Create two variations of the path-along-x. Calculate the action. Show once again that the Lagrangian-action is indeed smallest for the straight path.")

(define (make-varied-path ε t0 t1)
 (+ path-along-x (* ε (make-η (up #(* 0 %) identity #(* 5.0 (sin %))) t0 t1))))

(define small-varied-path (make-varied-path 0.01 0 10))
(define large-varied-path (make-varied-path 0.02 0 10))

[(Lagrangian-action (lg/L-free-particle 3.0) path-along-x 0.0 10.0)
 (Lagrangian-action (lg/L-free-particle 3.0) small-varied-path 0.0 10.0)
 (Lagrangian-action (lg/L-free-particle 3.0) large-varied-path 0.0 10.0)]

(md "Create data to plot the three paths in the xz plane along with their actions.")

(define path-points-2
  (hash-map "path-along-x" (make-path-txyz path-along-x 0 10 8)
            "small-varied-path" (make-path-txyz small-varied-path 0 10 24)
            "large-varied-path" (make-path-txyz large-varied-path 0 10 32)))

(md "Plot the three paths.")

(kind/vega-lite
  (points-xz->plot path-points-2))

(md "#### Finding trajectories that minimize the action")
(md "The SICM library provides a procedure that constructs a one dimensional path (along, say, the z axis) using an interpolation polynomial: `(make-path t0 q0 t1 q1 qs)`, where q0 and q1 are the endpoints, t0 and t1 are the corresponding times, and qs is a list of intermediate points. I give an example (note that the result, `initial-path`, is itself a function):")

(define pi-half (* 0.5 Math/PI))
(define initial-qs [0.1 0.2 0.2])
(define initial-path (lg/make-path 0 1.0 pi-half 0.0 initial-qs))

(md "Construct a parametric action that is just the action computed along that parametric path. Find approximate solution paths of a free particle and the harmonic oszillator respectively (hint: use the SICM procedure `multidimensional-minimize`).")

(define ((parametric-path-actn Lagrangian t0 q0 t1 q1) qs)
  (let ((path (lg/make-path t0 q0 t1 q1 qs)))
    (Lagrangian-action Lagrangian path t0 t1)))

(define (fnd-path Lagrangian t0 q0 t1 q1 initial-qs)
  (let ((minimizing-qs
          (mn/multidimensional-minimize
            (parametric-path-actn Lagrangian t0 q0 t1 q1)
            initial-qs)))
    (lg/make-path t0 q0 t1 q1 minimizing-qs)))

(define free-path
  (fnd-path (lg/L-free-particle 3.0) 0.0 1.0 pi-half 0.0 initial-qs))

(define harmonic-path
  (fnd-path (lg/L-harmonic 1.0 1.0) 0.0 1.0 pi-half 0.0 initial-qs))

(md "Make a plot of these one dimensional paths, this time not in the x-z plane but in the t-z plane. This shows that, upon optimization, the initial-path turns into a streight line and a sinusoidal curve respectively.")

(define (make-path-tz fn_t t0 t1 nofsteps)
  (map (lambda (t) (up t 0 0 (fn_t t)))
       (linear-interpolants-a t0 t1 nofsteps)))

(define plot-3
  (let ((i (make-path-tz initial-path 0 pi-half 50))
        (f (make-path-tz free-path 0 pi-half 50))
        (h (make-path-tz harmonic-path 0 pi-half 50)))
    (hash-map "orange" i "blue" f "red" h)))

(kind/vega-lite
  (points-tz->plot plot-3))

(md "Show that your numerically attained harmonic-path approximates the well known analytic solution.")

(kind/vega-lite
  (points-tz->plot
    (hash-map "diff"
              (make-path-tz (lambda (t) (- (cos t) (harmonic-path t)))
                            0 pi-half 35))))

(md "Calculate the Lagrange equation of the harmonic oszillator.")

(tex (((Lagrange-equations (lg/L-harmonic 'm 'k)) (literal-function 'q)) 't))

(md "## 1.5 The Euler-Lagrange Equations")
(md "### 1.5.2 Computing Lagrange's Equations")
(md "#### The free particle")
(md "State the dynamic equation of motion (i.e. the Lagrange equation a.k.a Newton's second law) of the free particle.")

(tex (((Lagrange-equations (lg/L-free-particle 'm)) (literal-function 'q)) 't))

(md "Check that an arbitrary straight-line path satisfies this equation, i.e. that inserting a straight line for q(t)
 gives identically zero (strictly speaking the zero covector of three dimensions).")

(do
  (define (straight-line t)
    (up (+ (* 'a t) 'a0)
        (+ (* 'b t) 'b0)
        (+ (* 'c t) 'c0)))

  (tex (((Lagrange-equations (lg/L-free-particle 'm)) straight-line) 't)))

(md "#### The harmonic oscillator")
(md "State the dynamic equation of motion for the harmonic oszillator with arbitrary mass and spring constant.")

(tex (((Lagrange-equations (lg/L-harmonic 'm 'k)) (literal-function 'q)) 't))

(md "Plug in a sinusoid with arbitrary amplitude $A$, frequency $\\omega$ and phase $\\phi$ and show that the only solutions allowed are ones where $\\omega = \\sqrt{k/m}$ ")

(do
  (define (proposed-solution t)
    (* 'A (cos (+ (* 'omega t) 'φ))))

  (tex (((Lagrange-equations (lg/L-harmonic 'm 'k)) proposed-solution) 't)))

(md "#### Exercise 1.11: Kepler's third law")
(md "Show that a planet in circular orbit satisfies Kepler's third law $n^2a^3=G(M_1+m_2)$, where $n$ is the angular frequency of the orbit and $a$ is the distance between sun and planet. (Hint: use the reduced mass to construct the Lagrangian)")

(define ((gravitational-energy G M_1 m_2) r)
  (- (/ (* G M_1 m_2) r)))

(define (circle t)
  (up 'a (* 'n t)))

(tex
  (((Lagrange-equations
      (lg/L-central-polar
        (/ (* 'M_1 'm_2) (+ 'M_1 'm_2))
        (gravitational-energy 'G 'M_1 'm_2))) circle) 't))

(md "## 1.6 How to find Lagrangians")
(md "#### Central force field")
(md "State the dynamic equation of motion for the uniform acceleration and the central potential, the latter in rectangular as well as in polar coordinates.")

(tex (up
       (((Lagrange-equations
           (lg/L-uniform-acceleration 'm 'g))
         (up (literal-function 'x)
             (literal-function 'y)))
        't)

       (((Lagrange-equations
           (lg/L-central-rectangular 'm (literal-function 'U)))
         (up (literal-function 'x)
             (literal-function 'y)))
        't)

       (((Lagrange-equations
           (lg/L-central-polar 'm (literal-function 'U)))
         (up (literal-function 'r)
             (literal-function 'phi)))
        't)))

(md "### 1.6.1 Coordinate transformations")
(md "Calculate the $[\\dot x \\space \\dot y]$ velocity vector in polar coordinates.")

(tex (velocity ((F->C p->r) (->local 't (up 'r 'φ) (up 'rdot 'φdot)))))

(md "Calculate the lagrangian in polar coordinates twice. Once directly and once via the lagrangian in rectangular coordinates.")

(define (L-alternate-central-polar m U)
  (compose (lg/L-central-rectangular m U) (F->C p->r)))

(tex
  (up ((lg/L-central-polar 'm (literal-function 'U))
       (->local 't (up 'r 'φ) (up 'rdot 'φdot)))
      ((L-alternate-central-polar 'm (literal-function 'U))
       (->local 't (up 'r 'φ) (up 'rdot 'φdot)))))

(md "#### Coriolis and centrifugal forces")
(md "State, in cartesian coordinates, the Lagrangian for the two dimensional free particle in a rotating coordinate system.")

(define L-free-rectangular lg/L-free-particle)

(define (L-free-polar m)
 (compose (L-free-rectangular m) (F->C p->r)))

(define ((F Omega) local)
  (let ((t (time local))
        (r (ref (coordinates local) 0))
        (theta (ref (coordinates local) 1)))
    (up r (+ theta (* Omega t)))))

(define (L-rotating-polar m Omega)
 (compose (L-free-polar m) (F->C (F Omega))))

(define (r->p local)
  (let ((rect-tuple (coordinate local)))
    (let ((x (ref rect-tuple 0))
          (y (ref rect-tuple 1)))
      (let ((r (sqrt (square rect-tuple)))
            (phi (atan (/ y x))))
        (up r phi)))))

(define (L-rotating-rectangular m Omega)
  (compose (L-rotating-polar m Omega) (F->C r->p)))

(tex ((L-rotating-rectangular 'm 'Omega)
      (up 't (up 'x_r 'y_r) (up 'xdot_r 'ydot_r))))

;; speed up the calculation using a Clojure macro

(defmacro Lrrsm []
  (list 'fn ['m 'Omega]
        (list 'fn [['t ['x_r 'y_r] ['xdot_r 'ydot_r]]]
              (clojure.edn/read-string
               (prn-str
                (simplify ((L-rotating-rectangular 'm 'Omega)
                           (up 't (up 'x_r 'y_r) (up 'xdot_r 'ydot_r)))))))))

(define L-rotating-rectangular-simp (Lrrsm))

(tex ((L-rotating-rectangular-simp 'm 'Omega)
      (up 't (up 'x_r 'y_r) (up 'xdot_r 'ydot_r))))

(md "Derive the equations of motion, in which the centrifugal and the coriolis force appear.")

(tex
  (((Lagrange-equations (L-rotating-rectangular 'm 'Omega))
     (up (literal-function 'x_r) (literal-function 'y_r)))
     't))

;; speed up the calculation
(tex
  (((Lagrange-equations (L-rotating-rectangular-simp 'm 'Omega))
     (up (literal-function 'x_r) (literal-function 'y_r)))
     't))

(md "Plot a clockwise rotating path. (Hints: (1) Use the SICM function \"Gamma\" to create the triple $(t \\: (x \\: y) \\: (\\dot{x} \\: \\dot{y}))$ which can be transformed, (2) the angular frequency must be negative)")

(define (test-path-2d t)
  (up
   (+ (* 3 t) 7)
   (+ (* 5 t) 11)))

;; again: use Clojure macro as a speed up method
(defmacro rpm []
  (list 'fn ['Omega 't]
        (clojure.edn/read-string
         (prn-str
          (simplify
           ((F->C p->r)
            ((F->C (F 'Omega))
             ((F->C r->p)
              ((Gamma test-path-2d) 't)))))))))

(define rotate-path (rpm))

(define (rotating-path-2d Omega)
  (up (lambda (t) (ref (rotate-path Omega t) 1 0))
      (lambda (t) (ref (rotate-path Omega t) 1 1))))

(do
  (define NegativeOm -3)

  (kind/vega-lite
    (points-xy->plot
      (hash-map "rotating-path-2d"
                (make-path-txyz (rotating-path-2d NegativeOm) 0 3 25)))))

(md "Show that this path indeed satiesfies the equations of motion in a rotating coordinate system.")

(do
  (define Om 'Omega)
  (define MinusOm (* -1 Om))

  (simplify
    (((Lagrange-equations (L-rotating-rectangular-simp 'm Om))
      (rotating-path-2d MinusOm))
     't)))

(md "### 1.6.2 Systems with rigid constraints")
(md "#### A pendulum driven at the pivot")
(md "State Lagrange’s equation for this system.")

(define ((T-pend m l g ys) local)
  (let ((t (time local))
        (theta (coordinate local))
        (thetadot (velocity local)))
    (let ((vys (D ys)))
      (* 1/2 m
         (+ (square (* l thetadot))
            (square (vys t))
            (* 2 l (vys t) thetadot (sin theta)))))))

(define ((V-pend m l g ys) local)
  (let ((t (time local))
        (theta (coordinate local)))
    (* m g (- (ys t) (* l (cos theta))))))

(define L-pend (- T-pend V-pend))

(define θ (literal-function 'θ))
(define y_s (literal-function 'y_s))

(tex
  (((Lagrange-equations (L-pend 'm 'l 'g y_s)) θ) 't))

(md "State the Lagrangian")

(tex
  ((L-pend 'm 'l 'g y_s) (->local 't 'θ 'θdot)))

(md "### 1.6.3 Constraints as Coordinate Transformations")
(md "Derive the previous result by using coordinate transformations.")

(define ((L-uniform-acceleration m g) local)
  (let ((q (coordinate local))
        (v (velocity local)))
    (let ((y (ref q 1)))
      (- (* 1/2 m (square v)) (* m g y)))))

(define ((dp-coordinates l y_s) local)
  (let ((t (time local))
        (theta (coordinate local)))
    (let ((x (* l (sin theta)))
          (y (- (y_s t) (* l (cos theta)))))
      (up x y))))

(define (L-pend2 m l g y_s)
  (comp (L-uniform-acceleration m g)
        (F->C (dp-coordinates l y_s))))

(tex
  ((L-pend2 'm 'l 'g y_s) (->local 't 'θ 'θdot)))

(md "### 1.8.3 Central Forces in Three Dimensions")
(md "Calculate the z-component of the angular momentum of an arbitrary path in rectangular and spherical coordinates.")

(define rectangular-state (up 't
                           (up 'x 'y 'z)
                           (up 'xdot 'ydot 'zdot)))

(define spherical-state (up 't
                         (up 'r 'θ 'φ)
                         (up 'rdot 'θdot 'φdot)))

(define ((ang-mom-z m) local)
  (let ((xyz (coordinates local))
        (v (velocities local)))
    (ref (cross-product xyz (* m v)) 2)))

#_(defn ang-mom-z [m]
  (fn [[_ xyz v]]
    (get (cross-product xyz (* m v)) 2)))

(tex
  (up
    ((ang-mom-z 'm) rectangular-state)
    ((compose (ang-mom-z 'm) (F->C s->r)) spherical-state)))

(md "Using spherical coordinates, calculate the generalized forces and the generalized momenta of a planet moving in a central potential. Thus show that the momentum conjugate to the third coordinate $\\phi$ is (1) conserved (because the respective force is zero) and (2) identical the z-component of the angular momentum.")

(define V (literal-function 'V))

(define (T3-spherical m)
 (compose (L-free-rectangular m) (F->C s->r)))

(define (L3-central m Vr)
  (let ((Vs (lambda (state)
                    (let ((r (ref (coordinate state) 0)))
                      (Vr r)))))
    (- (T3-spherical m) Vs)))

(tex
  (up
    (((partial 1) (L3-central 'm V)) spherical-state)
    (((partial 2) (L3-central 'm V)) spherical-state)))

(md "Show that the energy state function computed from the Lagrangian for a central field is in fact T + V.")

(tex
  (up
    ((T3-spherical 'm) (->local 't (up 'r 'θ 'φ) (up 'rdot 'θdot 'φdot)))
    ((Lagrangian->energy (L3-central 'm V)) spherical-state)))
