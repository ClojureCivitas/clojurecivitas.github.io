^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Differential Geometry Chapter One"
                     :quarto {:type     :draft
                              :sidebar  "emmy-fdg"
                              :description "First chapter of the book \"Functional Differential Geometry\", the code is on-line executable."
                              :date     "2025-11-14"
                              :image    "fdg_ch01.png"
                              :category :libs
                              :tags [:emmy :physics]}}}
(ns mentat-collective.emmy.fdg-ch01
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator
                            denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme]]
            [civitas.repl :as repl]))

;; The code snippets are executable, copy-paste them to the sidebar of the page.
;; Original code and text are from the MIT open access book
;; [Functional Differential Geometry (FDG)](https://mitpress.mit.edu/9780262019347/functional-differential-geometry/).

;; The [Emmy](https://emmy.mentat.org) maintainer, [Sam Ritchie](https://roadtoreality.substack.com/), wrote the source for this page, namely the
;; [LaTex version of FDG](https://github.com/mentat-collective/fdg-book/blob/main/scheme/org/chapter001.org).

;; In addition to the sidebar, I also have some
;; [FDG graphical puzzles](https://kloimhardt.github.io/cljtiles.html?page=FDG001).

;; In adopting MIT-Scheme's `(define ...)`, I trust that Clojure people will bridge that gap quickly
;; while being sure of the gratitude of all readers of the immutable, dense book. So without further ado ...

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
  '[emmy.env :as e :refer :all :exclude [F->C Cartan]])

^:kindly/hide-code
(require emmy-require)

^:kindly/hide-code
(kind/scittle
  '(require emmy-require))

^:kindly/hide-code
(define show-exp (comp str simplify))

^:kindly/hide-code
(kind/scittle
  '(def show-expression show-exp))

^:kindly/hide-code
(defmacro show-expression [b & c]
  (case b
    :calc-on-server
    (list 'simplify (first c))
    :browser
    (list 'kind/reagent
          [:tt (list 'quote
                     (list 'show-exp (first c)))])
    (let [serg (show-exp (eval b))]
      (list 'kind/reagent
            [:div (list 'quote
                        (list 'let ['a (list 'show-exp b)]
                              (list 'if (list '= serg 'a)
                                    [:tt 'a]
                                    [:div
                                     ;; [:tt 'a] ;; comment this in prod
                                     [:tt serg]])))]))))

^:kindly/hide-code
(define velocities velocity)

^:kindly/hide-code
(define coordinates coordinate)

^:kindly/hide-code
(define time first)

;; > Philosophy is written in that great book which ever lies before our eyes---I
;; > mean the Universe---but we cannot understand it if we do not learn the language
;; > and grasp the symbols in which it is written. This book is written in the
;; > mathematical language, and the symbols are triangles, circles, and other
;; > geometrical figures without whose help it is impossible to comprehend a single
;; > word of it, without which one wanders in vain through a dark labyrinth.
;; >
;; > **Galileo Galilei [8]**

;; Differential geometry is a mathematical language that can be used to express
;; physical concepts. In this introduction we show a typical use of this language.
;; Do not panic! At this point we do not expect you to understand the details of
;; what we are showing. All will be explained as needed in the text. The purpose is
;; to get the flavor of this material.

;; At the North Pole inscribe a line in the ice perpendicular to the Greenwich
;; Meridian. Hold a stick parallel to that line and walk down the Greenwich
;; Meridian keeping the stick parallel to itself as you walk. (The phrase "parallel
;; to itself" is a way of saying that as you walk you keep its orientation
;; unchanged. The stick will be aligned East-West, perpendicular to your direction
;; of travel.) When you get to the Equator the stick will be parallel to the
;; Equator. Turn East, and walk along the Equator, keeping the stick parallel to
;; the Equator. Continue walking until you get to the 90◦E meridian. When you reach
;; the 90°E meridian turn North and walk back to the North Pole keeping the stick
;; parallel to itself. Note that the stick is perpendicular to your direction of
;; travel. When you get to the Pole note that the stick is perpendicular to the
;; line you inscribed in the ice. But you started with that stick parallel to that
;; line and you kept the stick pointing in the same direction on the Earth
;; throughout your walk --- how did it change orientation?

;; The answer is that you walked a closed loop on a curved surface. As seen in
;; three dimensions the stick was actually turning as you walked along the Equator,
;; because you always kept the stick parallel to the curving surface of the Earth.
;; But as a denizen of a 2-dimensional surface, it seemed to you that you kept the
;; stick parallel to itself as you walked, even when making a turn. Even if you had
;; no idea that the surface of the Earth was embedded in a 3-dimensional space you
;; could use this experiment to conclude that the Earth was not flat. This is a
;; small example of intrinsic geometry. It shows that the idea of parallel
;; transport is not simple. For a general surface it is necessary to explicitly
;; define what we mean by parallel.

;; If you walked a smaller loop, the angle between the starting orientation and the
;; ending orientation of the stick would be smaller. For small loops it would be
;; proportional to the area of the loop you walked. This constant of
;; proportionality is a measure of the curvature. The result does not depend on how
;; fast you walked, so this is not a dynamical phenomenon.

;; Denizens of the surface may play ball games. The balls are constrained to the
;; surface; otherwise they are free particles. The paths of the balls are governed
;; by dynamical laws. This motion is a solution of the Euler-Lagrange
;; equations[fn:1] for the free-particle Lagrangian with coordinates that
;; incorporate the constraint of living in the surface. There are coefficients of
;; terms in the EulerLagrange equations that arise naturally in the description of
;; the behavior of the stick when walking loops on the surface, connecting the
;; static shape of the surface with the dynamical behavior of the balls. It turns
;; out that the dynamical evolution of the balls may be viewed as parallel
;; transport of the ball's velocity vector in the direction of the velocity vector.
;; This motion by parallel transport of the velocity is called /geodesic motion/.

;; So there are deep connections between the dynamics of particles and the geometry
;; of the space that the particles move in. If we understand this connection we can
;; learn about dynamics by studying geometry and we can learn about geometry by
;; studying dynamics. We enter dynamics with a Lagrangian and the associated
;; Lagrange equations. Although this formulation exposes many important features of
;; the system, such as how symmetries relate to conserved quantities, the geometry
;; is not apparent. But when we express the Lagrangian and the Lagrange equations
;; in differential geometry language, geometric properties become apparent. In the
;; case of systems with no potential energy the Euler-Lagrange equations are
;; equivalent to the geodesic equations on the configuration manifold. In fact, the
;; coefficients of terms in the Lagrange equations are Christoffel coefficients,
;; which define parallel transport on the manifold. Let's look into this a bit.

;; ## Lagrange Equations

;; We write the Lagrange equations in functional notation[fn:2] as follows:

;; $$
;; D\left(\partial_{2} L \circ \Gamma[q]\right) - \partial_{1} L \circ \Gamma[q]=0.
;; $$

;; In SICM [19], Section 1.6.3, we showed that a Lagrangian describing the free
;; motion of a particle subject to a coordinate-dependent constraint can be
;; obtained by composing a free-particle Lagrangian with a function that describes
;; how dynamical states transform given the coordinate transformation that
;; describes the constraints.

;; A Lagrangian for a free particle of mass m and velocity v is just its kinetic
;; energy, $mv^2/2$. The procedure =Lfree= implements the free Lagrangian:[fn:3]

(define ((Lfree mass) state)
  (* 1/2 mass (square (velocity state))))

;; For us the dynamical state of a system of particles is a tuple of time,
;; coordinates, and velocities. The free-particle Lagrangian depends only on the
;; velocity part of the state.

;; For motion of a point constrained to move on the surface of a sphere the
;; configuration space has two dimensions. We can describe the position of the
;; point with the generalized coordinates colatitude and longitude. If the sphere
;; is embedded in 3-dimensional space the position of the point in that space can
;; be given by a coordinate transformation from colatitude and longitude to three
;; rectangular coordinates.

;; For a sphere of radius R the procedure =sphere->R3= implements the
;; transformation of coordinates from colatitude $\theta$ and longitude $\phi$ on
;; the surface of the sphere to rectangular coordinates in the embedding space.
;; (The $\hat{z}$ axis goes through the North Pole, and the Equator is in the plane
;; $z = 0$.)

(define ((sphere->R3 R) state)
  (let ((q (coordinate state)))
    (let ((theta (ref q 0)) (phi (ref q 1)))
      (up (* R (sin theta) (cos phi))
          (* R (sin theta) (sin phi))
          (* R (cos theta))))))

;; The coordinate transformation maps the generalized coordinates on the sphere to
;; the 3-dimensional rectangular coordinates. Given this coordinate transformation
;; we construct a corresponding transformation of velocities; these make up the
;; state transformation. The procedure =F->C= implements the derivation of a
;; transformation of states from a coordinate transformation:

(define ((F->C F) state)
  (up (time state)
      (F state)
      (+ (((partial 0) F) state)
         (* (((partial 1) F) state)
            (velocity state)))))

;; A Lagrangian governing free motion on a sphere of radius $R$ is then the
;; composition of the free Lagrangian with the transformation of states.

(define (Lsphere m R)
  (compose (Lfree m) (F->C (sphere->R3 R))))

;; So the value of the Lagrangian at an arbitrary dynamical state is:

(show-expression
  ((Lsphere 'm 'R)
   (up 't (up 'theta 'phi) (up 'thetadot 'phidot))))

;; or, in infix notation:

;;$$
;; \begin{equation}
;; {{1} \over {2}} {R}^{2} m {\dot {\phi}}^{2} {\left ( \sin\left( \theta \right) \right)}^{2} + {{1} \over {2}} {R}^{2} m {\dot {\theta}}^{2}
;; \end{equation}
;; $$

;; ## The Metric

;; Let's now take a step into the geometry. A surface has a metric which tells us
;; how to measure sizes and angles at every point on the surface. (Metrics are
;; introduced in Chapter 9.)

;; The metric is a symmetric function of two vector fields that gives a number for
;; every point on the manifold. (Vector fields are introduced in Chapter 3).
;; Metrics may be used to compute the length of a vector field at each point, or
;; alternatively to compute the inner product of two vector fields at each point.
;; For example, the metric for the sphere of radius $R$ is

;; $$
;; \begin{equation}
;; \mathsf{g}(\mathsf{u}, \mathsf{v})=R^{2} \mathsf{d} \theta(\mathsf{u})
;; \mathsf{d} \theta(\mathsf{v})+R^{2}(\sin \theta)^{2} \mathsf{d}
;; \phi(\mathsf{u}) \mathsf{d} \phi(\mathsf{v}),
;; \end{equation}
;; $$

;; where $\mathsf{u}$ and $\mathsf{v}$ are vector fields, and $\mathsf{d}\theta$
;; and $\mathsf{d}\phi$ are one-form fields that extract the named components of
;; the vector-field argument. (One-form fields are introduced in Chapter 3.) We can
;; think of $\mathsf{d}\theta(\mathsf{u})$ as a function of a point that gives the
;; size of the vector field $\mathsf{u}$ in the $\theta$ direction at the point.
;; Notice that $\mathsf{g}(\mathsf{u}, \mathsf{u})$ is a weighted sum of the
;; squares of the components of $\mathsf{u}$. In fact, if we identify

;; $$
;; \begin{align*}
;; &\mathsf{d} \theta(\mathsf{v})=\dot{\theta} \\
;; &\mathsf{d} \phi(\mathsf{v})=\dot{\phi},
;; \end{align*}
;; $$

;; then the coefficients in the metric are the same as the coefficients in the
;; value of the Lagrangian, equation (1.1), apart from a factor of $m/2$.

;; We can generalize this result and write a Lagrangian for free motion of a
;; particle of mass $m$ on a manifold with metric $\mathsf{g}$:

;; $$
;; \begin{equation}
;; L_{2}(x, v)=\sum_{i j} \frac{1}{2} m g_{i j}(x) v^{i} v^{j}
;; \end{equation}
;; $$

;; This is written using indexed variables to indicate components of the geometric
;; objects expressed with respect to an unspecified coordinate system. The metric
;; coefficients $g_{ij}$ are, in general, a function of the position coordinates
;; $x$, because the properties of the space may vary from place to place.

;; We can capture this geometric statement as a program:

(define ((L2 mass metric) place velocity)
  (* 1/2 mass ((metric velocity velocity) place)))

;; This program gives the Lagrangian in a coordinate-independent, geometric way. It
;; is entirely in terms of geometric objects, such as a place on the configuration
;; manifold, the velocity at that place, and the metric that describes the local
;; shape of the manifold. But to compute we need a coordinate system. We express
;; the dynamical state in terms of coordinates and velocity components in the
;; coordinate system. For each coordinate system there is a natural vector basis
;; and the geometric velocity vectors can be constructed by contracting the basis
;; with the components of the velocity. Thus, we can form a coordinate
;; representation of the Lagrangian.

(define ((Lc mass metric coordsys) state)
  (let ((x (coordinates state))
        (v (velocities state))
        (e (coordinate-system->vector-basis coordsys)))
    ((L2 mass metric) ((point coordsys) x) (* e v))))

;; The manifold point $\mathsf{m}$ represented by the coordinates $x$ is given by
;; =(define m ((point coordsys) x))=. The coordinates of $\mathsf{m}$ in a
;; different coordinate system are given by =((chart coordsys2) m)=. The manifold
;; point $\mathsf{m}$ is a geometric object that is the same point independent of
;; how it is specified. Similarly, the velocity vector $\mathsf{e}v$ is a geometric
;; object, even though it is specified using components $v$ with respect to the
;; basis $\mathsf{e}$. Both $v$ and $\mathsf{e}$ have as many components as the
;; dimension of the space so their product is interpreted as a contraction.

;; Let's make a general metric on a 2-dimensional real manifold:[fn:4]

(define the-metric (literal-metric 'g R2-rect))

;; The metric is expressed in rectangular coordinates, so the coordinate system is
;; =R2-rect=.[fn:5] The component functions will be labeled as subscripted ~g~s.

;; We can now make the Lagrangian for the system:

(define L (Lc 'm the-metric R2-rect))

;; And we can apply our Lagrangian to an arbitrary state:

(show-expression
  (L (up 't (up 'x 'y) (up 'vx 'vy))))

;; Compare this result with equation (1.3).

;; ## Euler-Lagrange Residuals

;; The Euler-Lagrange equations are satisfied on realizable paths. Let $\gamma$ be
;; a path on the manifold of configurations. (A path is a map from the
;; 1-dimensional real line to the configuration manifold. We introduce maps between
;; manifolds in Chapter 6.) Consider an arbitrary path:[fn:6]

(define gamma (literal-manifold-map 'q R1-rect R2-rect))

;; The values of $\gamma$ are points on the manifold, not a coordinate
;; representation of the points. We may evaluate =gamma= only on points of the
;; real-line manifold; =gamma= produces points on the $\mathbb{R}^2$ manifold. So
;; to go from the literal real-number coordinate ='t= to a point on the real line
;; we use =((point R1-rect) 't)= and to go from a point =m= in $\mathbb{R}^2$ to
;; its coordinate representation we use =((chart R2-rect) m)=. (The procedures
;; point and chart are introduced in Chapter 2.) Thus

(show-expression
  ((chart R2-rect) (gamma ((point R1-rect) 't))))

(define coordinate-path
  (compose (chart R2-rect) gamma (point R1-rect)))

(show-expression
  (coordinate-path 't))

;; Now we can compute the residuals of the Euler-Lagrange equations, but we get a
;; large messy expression that we will not show.[fn:7] However, we will save it to
;; compare with the residuals of the geodesic equations.

(define Lagrange-residuals
  (((Lagrange-equations L) coordinate-path) 't))

;; ## Geodesic Equations

;; Now we get deeper into the geometry. The traditional way to write the geodesic
;; equations is

;; $$
;; \begin{equation}
;; \nabla_{\mathsf{v}} \mathsf{v}=0
;; \end{equation}
;; $$

;; where $\nabla$ is a covariant derivative operator. Roughly, $\nabla_{\mathsf{v}}
;; \mathsf{w}$ is a directional derivative. It gives a measure of the variation of
;; the vector field $\mathsf{w}$ as you walk along the manifold in the direction of
;; $\mathsf{v}$. (We will explain this in depth in Chapter 7.) $\nabla_{\mathsf{v}}
;; \mathsf{v}=0$ is intended to convey that the velocity vector is
;; parallel-transported by itself. When you walked East on the Equator you had to
;; hold the stick so that it was parallel to the Equator. But the stick is
;; constrained to the surface of the Earth, so moving it along the Equator required
;; turning it in three dimensions. The $\nabla$ thus must incorporate the
;; 3-dimensional shape of the Earth to provide a notion of "parallel" appropriate
;; for the denizens of the surface of the Earth. This information will appear as
;; the "Christoffel coefficients" in the coordinate representation of the geodesic
;; equations.

;; The trouble with the traditional way to write the geodesic equations (1.4) is
;; that the arguments to the covariant derivative are vector fields and the
;; velocity along the path is not a vector field. A more precise way of stating
;; this relation is:

;; $$
;; \begin{equation}
;; \nabla^\gamma_{\partial/\partial\mathsf{t}} d\gamma\left(\partial/\partial \mathsf{t}\right) = 0.
;; \end{equation}
;; $$

;; (We know that this may be unfamiliar notation, but we will explain it in
;; Chapter 7.)

;; In coordinates, the geodesic equations are expressed

;; $$
;; \begin{equation}
;; D^{2} q^{i}(t)+\sum_{j k} \Gamma_{j k}^{i}(\gamma(t)) D q^{j}(t) D q^{k}(t)=0,
;; \end{equation}
;; $$

;; where $q(t)$ is the coordinate path corresponding to the manifold path $\gamma$,
;; and $\Gamma^i_{jk}\left(\mathsf{m}\right)$ are Christoffel coefficients. The
;; $\Gamma^i_{jk}\left(\mathsf{m}\right)$ describe the "shape" of the manifold
;; close to the manifold point $\mathsf{m}$. They can be derived from the metric
;; $g$.

;; We can get and save the geodesic equation residuals by:

^:kindly/hide-code
(kind/hidden
  (define-coordinates t e/R1-rect))

^:kindly/hide-code
(def Cartan
  (Christoffel->Cartan
    (metric->Christoffel-2
      the-metric
      (coordinate-system->basis R2-rect))))

(def geodesic-equation-residuals
  (((((covariant-derivative Cartan gamma) d:dt)
     ((differential gamma) d:dt))
    (chart R2-rect))
   ((point R1-rect) 't)))

;; where =d:dt= is a vector field on the real line[fn:8] and =Cartan= is a way of
;; encapsulating the geometry, as specified by the Christoffel coefficients.

;; [note KLM: I could not get d:dt to work in the sidebar. In [clj-tiles, FDG001 page 19](https://kloimhardt.github.io/cljtiles.html?page=FDG001) and [maria cloud](https://2.maria.cloud/gist/65d96869f9a901b243df06f996c1d707) the code works on-line]

;;The Christoffel coefficients are computed from the metric:

^:kindly/hide-code
(kind/code
  "(def Cartan
    (Christoffel->Cartan
      (metric->Christoffel-2
        the-metric
        (coordinate-system->basis R2-rect))))")

;; The two messy residual results that we did not show are related by the metric.
;; If we change the representation of the geodesic equations by "lowering" them
;; using the mass and the metric, we see that the residuals are equal:

(def metric-components
  (metric->components
   the-metric
   (coordinate-system->basis R2-rect)))

(show-expression :calc-on-server
  (- Lagrange-residuals
     (* (* 'm (metric-components (gamma ((point R1-rect) 't))))
        geodesic-equation-residuals)))

;; This establishes that for a 2-dimensional space the Euler-Lagrange equations are
;; equivalent to the geodesic equations. The Christoffel coefficients that appear
;; in the geodesic equation correspond to coefficients of terms in the
;; Euler-Lagrange equations. This analysis will work for any number of dimensions
;; (but will take your computer longer in higher dimensions, because the complexity
;; increases).

;; ### Exercise 1.1: Motion on a Sphere

;; The metric for a unit sphere, expressed in colatitude $\theta$ and longitude
;; $\phi$, is

;; $$
;; \mathsf{g}(\mathsf{u}, \mathsf{v})= \mathsf{d}\theta(\mathsf{u})\mathsf{d}\theta(\mathsf{v}) + (\sin \theta)^{2} \mathsf{d}\phi(\mathsf{u}) \mathsf{d} \phi(\mathsf{v}).
;; $$

;; Compute the Lagrange equations for motion of a free particle on the sphere and
;; convince yourself that they describe great circles. For example, consider motion
;; on the equator ($\theta = \pi/2$) and motion on a line of longitude ($\phi$ is
;; constant).

;; # Footnotes

;; [fn:8] We established =t= as a coordinate function on the rectangular
;; coordinates of the real line by

;; `(define-coordinates t R1-rect)`

;; This had the effect of also defining =d/dt= as a coordinate vector field and
;; =dt= as a one-form field on the real line.

;; [fn:7] For an explanation of equation residuals see page xvi.

;; [fn:6] The procedure =literal-manifold-map= makes a map from the manifold
;; implied by its second argument to the manifold implied by the third argument.
;; These arguments must be coordinate systems. The quoted symbol that is the first
;; argument is used to name the literal coordinate functions that define the map.

;; [fn:5] =R2-rect= is the usual rectangular coordinate system on the 2-dimensional
;; real manifold. (See Section 2.1, page 13.) We supply common coordinate systems
;; for n-dimensional real manifolds. For example, =R2-polar= is a polar coordinate
;; system on the same manifold.

;; [fn:4] The procedure =literal-metric= provides a metric. It is a general
;; symmetric function of two vector fields, with literal functions of the
;; coordinates of the manifold points for its coefficients in the given coordinate
;; system. The quoted symbol ='g= is used to make the names of the literal
;; coefficient functions. Literal functions are discussed in Appendix B.

;; [fn:3] An informal description of the Scheme programming language can be found
;; in Appendix A.

;; [fn:2] A short introduction to our functional notation, and why we have chosen
;; it, is given in the prologue: Programming and Understanding. More details can be
;; found in Appendix B

;; [fn:1] It is customary to shorten "Euler-Lagrange equations" to "Lagrange
;; equations." We hope Leonhard Euler is not disturbed.
;; (repl/scittle-sidebar)

(repl/scittle-sidebar)
