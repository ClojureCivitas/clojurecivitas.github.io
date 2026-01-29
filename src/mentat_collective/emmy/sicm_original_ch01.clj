^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: SICM Chapter 1"
                     :quarto {:author   :kloimhardt
                              :type     :draft
                              :description "Structure and Interpretation of Classical Mechanics: Chapter 1"
                              :sidebar  "emmy-fdg"
                              :date     "2026-01-11"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.sicm-original-ch01
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda]]
            [civitas.repl :as repl]))

;; ## 1 Lagrangian Mechanics
;
;;> The purpose of mechanics is to describe how bodies change their position in
;;> space with “time.” I should load my conscience with grave sins against the
;;> sacred spirit of lucidity were I to formulate the aims of mechanics in this way,
;;> without serious reflection and detailed explanations. Let us proceed to disclose
;;> these sins.
;;>
;;> Albert Einstein, Relativity, the Special and General Theory, p.9

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
  '[emmy.env :refer :all :exclude [print-expression Lagrangian-action find-path Lagrange-equations r->p]])

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
    (list 'show-expression-fn e)
    (reag-comp e)))

^:kindly/hide-code
(kind/scittle
  '(defn show-expression [e]
     (->infix (simplify e))))

^:kindly/hide-code
(define velocities velocity)

^:kindly/hide-code
(define coordinates coordinate)

^:kindly/hide-code
(define vector-length count)

^:kindly/hide-code
(define time first)

;;The subject of this book is motion and the mathematical tools used to describe
;;it.
;;
;;Centuries of careful observations of the motions of the planets revealed
;;regularities in those motions, allowing accurate predictions of phenomena such
;;as eclipses and conjunctions. The effort to formulate these regularities and
;;ultimately to understand them led to the development of mathematics and to the
;;discovery that mathematics could be effectively used to describe aspects of the
;;physical world. That mathematics can be used to describe natural phenomena is a
;;remarkable fact.
;;
;;A pin thrown by a juggler takes a rather predictable path and rotates in a
;;rather predictable way. In fact, the skill of juggling depends crucially on this
;;predictability. It is also a remarkable discovery that the same mathematical
;;tools used to describe the motions of the planets can be used to describe the
;;motion of the juggling pin.
;;
;;Classical mechanics describes the motion of a system of particles, subject to
;;forces describing their interactions. Complex physical objects, such as juggling
;;pins, can be modeled as myriad particles with fixed spatial relationships
;;maintained by stiff forces of interaction.
;;
;;There are many conceivable ways a system could move that never occur. We can
;;imagine that the juggling pin might pause in midair or go fourteen times around
;;the head of the juggler before being caught, but these motions do not happen.
;;How can we distinguish motions of a system that can actually occur from other
;;conceivable motions? Perhaps we can invent some mathematical function that
;;allows us to distinguish realizable motions from among all conceivable motions.
;;
;;The motion of a system can be described by giving the position of every piece of
;;the system at each moment. Such a description of the motion of the system is
;;called a /configuration path/; the configuration path specifies the
;;configuration as a function of time. The juggling pin rotates as it flies
;;through the air; the configuration of the juggling pin is specified by giving
;;the position and orientation of the pin. The motion of the juggling pin is
;;specified by giving the position and orientation of the pin as a function of
;;time.
;;
;;The path-distinguishing function that we seek takes a configuration path as an
;;input and produces some output. We want this function to have some
;;characteristic behavior when its input is a realizable path. For example, the
;;output could be a number, and we could try to arrange that this number be zero
;;only on realizable paths. Newton's equations of motion are of this form; at each
;;moment Newton's differential equations must be satisfied.
;;
;;However, there is an alternate strategy that provides more insight and power: we
;;could look for a path-distinguishing function that has a minimum on the
;;realizable paths---on nearby unrealizable paths the value of the function is
;;higher than it is on the realizable path. This is the /variational strategy/:
;;for each physical system we invent a path-distinguishing function that
;;distinguishes realizable motions of the system by having a stationary point for
;;each realizable path[fn:1]. For a great variety of systems realizable motions of
;;the system can be formulated in terms of a variational principle.[fn:2]
;;
;;Mechanics, as invented by Newton and others of his era, describes the motion of
;;a system in terms of the positions, velocities, and accelerations of each of the
;;particles in the system. In contrast to the Newtonian formulation of mechanics,
;;the variational formulation of mechanics describes the motion of a system in
;;terms of aggregate quantities that are associated with the motion of the system
;;as a whole.
;;
;;In the Newtonian formulation the forces can often be written as derivatives of
;;the potential energy of the system. The motion of the system is determined by
;;considering how the individual component particles respond to these forces. The
;;Newtonian formulation of the equations of motion is intrinsically a
;;particle-by-particle description.
;;
;;In the variational formulation the equations of motion are formulated in terms
;;of the difference of the kinetic energy and the potential energy. The potential
;;energy is a number that is characteristic of the arrangement of the particles in
;;the system; the kinetic energy is a number that is determined by the velocities
;;of the particles in the system. Neither the potential energy nor the kinetic
;;energy depends on how those positions and velocities are specified. The
;;difference is characteristic of the system as a whole and does not depend on the
;;details of how the system is specified. So we are free to choose ways of
;;describing the system that are easy to work with; we are liberated from the
;;particle-by-particle description inherent in the Newtonian formulation.
;;
;;The variational formulation has numerous advantages over the Newtonian
;;formulation. The equations of motion for those parameters that describe the
;;state of the system are derived in the same way regardless of the choice of
;;those parameters: the method of formulation does not depend on the choice of
;;coordinate system. If there are positional constraints among the particles of a
;;system the Newtonian formulation requires that we consider the forces
;;maintaining these constraints, whereas in the variational formulation the
;;constraints can be built into the coordinates. The variational formulation
;;reveals the association of conservation laws with symmetries. The variational
;;formulation provides a framework for placing any particular motion of a system
;;in the context of all possible motions of the system. We pursue the variational
;;formulation because of these advantages.
;;
;;### 1.1 Configuration Spaces
;;
;;  Let us consider mechanical systems that can be thought of as composed of
;;  constituent point particles, with mass and position, but with no internal
;;  structure.[fn:3] Extended bodies may be thought of as composed of a large
;;  number of these constituent particles with specific spatial relationships
;;  among them. Extended bodies maintain their shape because of spatial
;;  constraints among the constituent particles. Specifying the position of all
;;  the constituent particles of a system specifies the /configuration/ of the
;;  system. The existence of constraints among parts of the system, such as those
;;  that determine the shape of an extended body, means that the constituent
;;  particles cannot assume all possible positions. The set of all configurations
;;  of the system that can be assumed is called the /configuration space/ of the
;;  system. The /dimension/ of the configuration space is the smallest number of
;;  parameters that have to be given to completely specify a configuration. The
;;  dimension of the configuration space is also called the number of /degrees of
;;  freedom/ of the system.[fn:4]
;;
;;  For a single unconstrained particle it takes three parameters to specify the
;;  configuration; a point particle has a three-dimensional configuration space.
;;  If we are dealing with a system with more than one point particle, the
;;  configuration space is more complicated. If there are /k/ separate particles
;;  we need 3/k/ parameters to describe the possible configurations. If there are
;;  constraints among the parts of a system the configuration is restricted to a
;;  lower-dimensional space. For example, a system consisting of two point
;;  particles constrained to move in three dimensions so that the distance between
;;  the particles remains fixed has a five-dimensional configuration space: thus
;;  with three numbers we can fix the position of one particle, and with two
;;  others we can give the position of the other particle relative to the first.
;;
;;  Consider a juggling pin. The configuration of the pin is specified if we give
;;  the positions of the atoms making up the pin. However, there exist more
;;  economical descriptions of the configuration. In the idealization that the
;;  juggling pin is truly rigid, the distances among all the atoms of the pin
;;  remain constant. So we can specify the configuration of the pin by giving the
;;  position of a single atom and the orientation of the pin. Using the
;;  constraints, the positions of all the other constituents of the pin can be
;;  determined from this information. The dimension of the configuration space of
;;  the juggling pin is six: the minimum number of parameters that specify the
;;  position in space is three, and the minimum number of parameters that specify
;;  an orientation is also three.
;;
;;  As a system evolves with time, the constituent particles move subject to the
;;  constraints. The motion of each constituent particle is specified by
;;  describing the changing configuration. Thus, the motion of the system may be
;;  described as evolving along a path in configuration space. The configuration
;;  path may be specified by a function, the configuration-path function, which
;;  gives the configuration of the system at any time.
;;
;;### Exercise 1.1: Degrees of freedom
;;
;;    For each of the mechanical systems described below, give the number of
;;    degrees of freedom of the configuration space.
;;
;;    - *a.* Three juggling pins.
;;
;;    - *b.* A spherical pendulum, consisting of a point mass (the pendulum bob)
;;    hanging from a rigid massless rod attached to a fixed support point. The
;;    pendulum bob may move in any direction subject to the constraint imposed by
;;    the rigid rod. The point mass is subject to the uniform force of gravity.
;;
;;    - *c.* A spherical double pendulum, consisting of one point mass hanging
;;      from a rigid massless rod attached to a second point mass hanging from a second
;;    massless rod attached to a fixed support point. The point masses are subject
;;    to the uniform force of gravity.
;;
;;    - *d.* A point mass sliding without friction on a rigid curved wire.
;;
;;    - *e.* A top consisting of a rigid axisymmetric body with one point on the
;;    symmetry axis of the body attached to a fixed support, subject to a uniform
;;    gravitational force.
;;
;;    - *f.* The same as *e*, but not axisymmetric.
;
;;## 1.2 Generalized Coordinates
;;
;;  In order to be able to talk about specific configurations we need to have a
;;  set of parameters that label the configurations. The parameters used to
;;  specify the configuration of the system are called the *generalized
;;  coordinates*. Consider an unconstrained free particle. The configuration of
;;  the particle is specified by giving its position. This requires three
;;  parameters. The unconstrained particle has three degrees of freedom. One way
;;  to specify the position of a particle is to specify its rectangular
;;  coordinates relative to some chosen coordinate axes. The rectangular
;;  components of the position are generalized coordinates for an unconstrained
;;  particle. Or consider an ideal planar double pendulum: a point mass
;;  constrained to be a given distance from a fixed point by a rigid rod, with a
;;  second mass constrained to be at a given distance from the first mass by
;;  another rigid rod, all confined to a vertical plane. The configuration is
;;  specified if the orientation of the two rods is given. This requires at least
;;  two parameters; the planar double pendulum has two degrees of freedom. One way
;;  to specify the orientation of each rod is to specify the angle it makes with a
;;  vertical plumb line. These two angles are generalized coordinates for the
;;  planar double pendulum.
;;
;;  The number of coordinates need not be the same as the dimension of the
;;  configuration space, though there must be at least that many. We may choose to
;;  work with more parameters than necessary, but then the parameters will be
;;  subject to constraints that restrict the system to possible configurations,
;;  that is, to elements of the configuration space.
;;
;;  For the planar double pendulum described above, the two angle coordinates are
;;  enough to specify the configuration. We could also take as generalized
;;  coordinates the rectangular coordinates of each of the masses in the plane,
;;  relative to some chosen coordinate axes. These are also fine coordinates, but
;;  we would have to explicitly keep in mind the constraints that limit the
;;  possible configurations to the actual geometry of the system. Sets of
;;  coordinates with the same dimension as the configuration space are easier to
;;  work with because we do not have to deal with explicit constraints among the
;;  coordinates. So for the time being we will consider only formulations where
;;  the number of configuration coordinates is equal to the number of degrees of
;;  freedom; later we will learn how to handle systems with redundant coordinates
;;  and explicit constraints.
;;
;;  In general, the configurations form a space $M$ of some dimension $n$. The
;;  $n$-dimensional configuration space can be parameterized by choosing a
;;  coordinate function $\chi$ that maps elements of the configuration space to
;;  $n$-tuples of real numbers.[fn:5] If there is more than one dimension, the
;;  function $\chi$ is a tuple of $n$ independent coordinate functions[fn:6]
;;  $χ^{i}$, $i= 0, ..., n − 1$, where each $χ^{i}$ is a real-valued function
;;  defined on some region of the configuration space.[fn:7] For a given
;;  configuration $m$ in the configuration space $M$ the values $χ^{i}(m)$ of
;;  the coordinate functions are the generalized coordinates of the configuration.
;;  These generalized coordinates permit us to identify points of the
;;  $n$-dimensional configuration space with $n$-tuples of real numbers.[fn:8] For
;;  any given configuration space, there are a great variety of ways to choose
;;  generalized coordinates. Even for a single point moving without constraints,
;;  we can choose rectangular coordinates, polar coordinates, or any other
;;  coordinate system that strikes our fancy.
;;
;;  The motion of the system can be described by a configuration path $γ$ mapping
;;  time to configuration-space points. Corresponding to the configuration path is
;;  a *coordinate path* $q = \chi \circ \gamma$ mapping time to tuples of
;;  generalized coordinates.[fn:9] If there is more than one degree of freedom the
;;  coordinate path is a structured object: $q$ is a tuple of component coordinate
;;  path functions $q^i = \chi^i \circ \gamma$. At each instant of time $t$, the
;;  values $q(t) = \left(q^0(t),\, \ldots,\, q^{n−1}(t) \right)$ are the
;;  generalized coordinates of a configuration.
;;
;;  The derivative $Dq$ of the coordinate path $q$ is a function[fn:10] that gives
;;  the rate of change of the configuration coordinates at a given time: $Dq(t)
;;  = (Dq^{0}(t), ..., Dq^{n−1}(t))$. The rate of change of a generalized
;;  coordinate is called a *generalized velocity*.
;;
;;### Exercise 1.2: Generalized coordinates
;;
;;    For each of the systems in exercise 1.1, specify a system of generalized
;;    coordinates that can be used to describe the behavior of the system.
;;
;;## 1.3 The Principle of Stationary Action
;;
;;  Let us suppose that for each physical system there is a path-distinguishing
;;  function that is stationary on realizable paths. We will try to deduce some of
;;  its properties.
;;
;;### Experience of motion
;;
;;   Our ordinary experience suggests that physical motion can be described by
;;   configuration paths that are continuous and smooth.[fn:11] We do not see the
;;   juggling pin jump from one place to another. Nor do we see the juggling pin
;;   suddenly change the way it is moving.
;;
;;   Our ordinary experience suggests that the motion of physical systems does not
;;   depend upon the entire history of the system. If we enter the room after the
;;   juggling pin has been thrown into the air we cannot tell when it left the
;;   juggler's hand. The juggler could have thrown the pin from a variety of
;;   places at a variety of times with the same apparent result as we walk through
;;   the door.[fn:12] So the motion of the pin does not depend on the details of
;;   the history.
;;
;;   Our ordinary experience suggests that the motion of physical systems is
;;   deterministic. In fact, a small number of parameters summarize the important
;;   aspects of the history of the system and determine its future evolution. For
;;   example, at any moment the position, velocity, orientation, and rate of
;;   change of the orientation of the juggling pin are enough to completely
;;   determine the future motion of the pin.
;;
;;### Realizable paths
;;
;;   From our experience of motion we develop certain expectations about
;;   realizable configuration paths. If a path is realizable, then any segment of
;;   the path is a realizable path segment. Conversely, a path is realizable if
;;   every segment of the path is a realizable path segment. The realizability of
;;   a path segment depends on all points of the path in the segment. The
;;   realizability of a path segment depends on every point of the path segment in
;;   the same way; no part of the path is special. The realizability of a path
;;   segment depends only on points of the path within the segment; the
;;   realizability of a path segment is a local property.
;;
;;   So the path-distinguishing function aggregates some local property of the
;;   system measured at each moment along the path segment. Each moment along the
;;   path must be treated in the same way. The contributions from each moment
;;   along the path segment must be combined in a way that maintains the
;;   independence of the contributions from disjoint subsegments. One method of
;;   combination that satisfies these requirements is to add up the contributions,
;;   making the path-distinguishing function an integral over the path segment of
;;   some local property of the path.[fn:13]
;;
;;   So we will try to arrange that the path-distinguishing function, constructed
;;   as an integral of a local property along the path, assumes a stationary value
;;   for any realizable path. Such a path-distinguishing function is traditionally
;;   called an *action* for the system. We use the word “action” to be consistent
;;   with common usage. Perhaps it would be clearer to continue to call it
;;   “path-distinguishing function,” but then it would be more difficult for
;;   others to know what we were talking about.[fn:14]
;;
;;   In order to pursue the agenda of variational mechanics, we must invent action
;;   functions that are stationary on the realizable trajectories of the systems
;;   we are studying. We will consider actions that are integrals of some local
;;   property of the configuration path at each moment. Let $q = χ ∘ γ$ be a
;;   coordinate path in the configuration space; $q(t)$ are the coordinates of
;;   the configuration at time $t$. Then the action of a segment of the path in
;;   the time interval from $t_{1}$ to $t_{2}$ is[fn:15]
;
;; $$
;; {S\lbrack q\rbrack(t_{1},t_{2}) = {\int_{t_{1}}^{t_{2}}{F\lbrack q\rbrack.}}  \tag{1.1}}
;; $$
;
;
;;   where $F[q]$ is a function of time that measures some local property of the
;;   path. It may depend upon the value of the function $q$ at that time and the
;;   value of any derivatives of $q$ at that time.[fn:16]
;;
;
;;The configuration path can be locally described at a moment in terms of the
;;   coordinates, the rate of change of the coordinates, and all the higher
;;   derivatives of the coordinates at the given moment. Given this information
;;   the path can be reconstructed in some interval containing that moment.[fn:17]
;;   Local properties of paths can depend on no more than the local description of
;;   the path.
;;
;;   The function $F$ measures some local property of the coordinate path $q$. We
;;   can decompose $F[q]$ into two parts: a part that measures some property of
;;   a local description and a part that extracts a local description of the path
;;   from the path function. The function that measures the local property of the
;;   system depends on the particular physical system; the method of construction
;;   of a local description of a path from a path is the same for any system. We
;;   can write $F[q]$ as a composition of these two functions:[fn:18]
;;
;;   $$
;;   {F\lbrack q\rbrack = L \circ \Gamma\lbrack q\rbrack.} \tag{1.2}
;;   $$
;;
;;   The function Γ takes the coordinate path and produces a function of time
;;   whose value is an ordered tuple containing the time, the coordinates at that
;;   time, the rate of change of the coordinates at that time, and the values of
;;   higher derivatives of the coordinates evaluated at that time. For the path
;;   $q$ and time $t$:
;;
;;   $$
;;   {\Gamma\lbrack q\rbrack(t) = (t,q(t),Dq(t),\ldots).} \tag{1.3}
;;   $$
;;
;;   We refer to this tuple, which includes as many derivatives as are needed, as
;;   the *local tuple*. The function $Γ[q]$ depends only on the coordinate path
;;   $q$ and its derivatives; the function $Γ[q]$ does not depend on $χ$ or the
;;   fact that $q$ is made by composing $χ$ with $γ$.
;;
;;   The function $L$ depends on the specific details of the physical system being
;;   investigated, but does not depend on any particular configuration path. The
;;   function $L$ computes a real-valued local property of the path. We will find
;;   that $L$ needs only a finite number of components of the local tuple to
;;   compute this property: The path can be locally reconstructed from the full
;;   local description; that $L$ depends on a finite number of components of the
;;   local tuple guarantees that it measures a local property.[fn:19]
;;
;;   The advantage of this decomposition is that the local description of the path
;;   is computed by a uniform process from the configuration path, independent of
;;   the system being considered. All of the system-specific information is
;;   captured in the function $L$.
;;
;;   The function $L$ is called a *Lagrangian*[fn:20] for the system, and the
;;   resulting action,
;;
;;   $$
;;   {S\lbrack q\rbrack(t_{1},t_{2}) = {\int_{t_{1}}^{t_{2}}{L \circ \Gamma\lbrack q\rbrack,}}} \tag{1.4}
;;   $$
;;
;;   is called the *Lagrangian action*. For Lagrangians that depend only on time,
;;   positions, and velocities the action can also be written
;;
;;   $$
;;   {S\lbrack q\rbrack(t_{1},t_{2}) = {\int_{t_{1}}^{t_{2}}{L(t,q(t),Dq(t))\, dt.}}} \tag{1.5}
;;   $$
;;
;;   Lagrangians can be found for a great variety of systems. We will see that for
;;   many systems the Lagrangian can be taken to be the difference between kinetic
;;   and potential energy. Such Lagrangians depend only on the time, the
;;   configuration, and the rate of change of the configuration. We will focus on
;;   this class of systems, but will also consider more general systems from time
;;   to time.
;;
;;   A realizable path of the system is to be distinguished from others by having
;;   stationary action with respect to some set of nearby unrealizable paths. Now
;;   some paths near realizable paths will also be realizable: for any motion of
;;   the juggling pin there is another that is slightly different. So when
;;   addressing the question of whether the action is stationary with respect to
;;   variations of the path we must somehow restrict the set of paths we are
;;   considering to contain only one realizable path. It will turn out that for
;;   Lagrangians that depend only on the configuration and rate of change of
;;   configuration it is enough to restrict the set of paths to those that have
;;   the same configuration at the endpoints of the path segment.
;;
;;   The *principle of stationary action* asserts that for each dynamical system
;;   we can cook up a Lagrangian such that a realizable path connecting the
;;   configurations at two times $t_{1}$ and $t_{2}$ is distinguished from all
;;   conceivable paths by the fact that the action $S[q](t_{1}, t_{2})$ is
;;   stationary with respect to variations of the path.[fn:21] For Lagrangians
;;   that depend only on the configuration and rate of change of configuration,
;;   the variations are restricted to those that preserve the configurations at
;;   $t_{1}$ and $t_{2}$.[fn:22]
;;
;;### Exercise 1.3: Fermat optics
;;
;;    Fermat observed that the laws of reflection and refraction could be
;;    accounted for by the following facts: Light travels in a straight line in
;;    any particular medium with a velocity that depends upon the medium. The path
;;    taken by a ray from a source to a destination through any sequence of media
;;    is a path of least total time, compared to neighboring paths. Show that
;;    these facts imply the laws of reflection and refraction.[fn:23]
;;
;;## 1.4 Computing Actions
;;
;;  To illustrate the above ideas, and to introduce their formulation as computer
;;  programs, we consider the simplest mechanical system---a free particle moving
;;  in three dimensions. Euler and Lagrange discovered that for a free particle
;;  the time integral of the kinetic energy over the particle's actual path is
;;  smaller than the same integral along any alternative path between the same
;;  points: a free particle moves according to the principle of stationary action,
;;  provided we take the Lagrangian to be the kinetic energy. The kinetic energy
;;  for a particle of mass $m$ and velocity $\overset{\rightarrow}{v}$ is 
;; $\frac{1}{2}mv^{2}$, where $v$ is the magnitude of
;;  $\overset{\rightarrow}{v}$
;;  . In this case we can choose the generalized coordinates to be the ordinary
;;  rectangular coordinates.
;;
;;  Following Euler and Lagrange, the Lagrangian for the free particle is[fn:24]
;;
;;  $$
;;  {L(t,x,v) = \frac{1}{2}m(v \cdot v),} \tag{1.6}
;;  $$
;;
;;  where the formal parameter $x$ names a tuple of components of the position
;;  with respect to a given rectangular coordinate system, and the formal
;;  parameter $v$ names a tuple of velocity components.[fn:25]
;;
;;  We can express this formula as a procedure:

(define ((L-free-particle mass) local)
  (let ((v (velocity local)))
    (* 1/2 mass (dot-product v v))))

;;  The definition indicates that `L-free-particle` is a procedure that takes mass
;;  as an argument and returns a procedure that takes a local tuple local,
;;  extracts the generalized velocity with the procedure velocity, and uses the
;;  velocity to compute the value of the Lagrangian.[fn:26]
;;
;;  Suppose we let $q$ denote a coordinate path function that maps time to
;;  position components:[fn:27]
;;
;;  $$
;;  {q(t) = (x(t),y(t),z(t)).} \tag{1.7}
;;  $$
;;
;;  We can make this definition[fn:28]

(define q
  (up (literal-function 'x)
      (literal-function 'y)
      (literal-function 'z)))

;;  where literal-function makes a procedure that represents a function of one
;;  argument that has no known properties other than the given symbolic name. The
;;  symbol q now names a procedure of one real argument (time) that produces a
;;  tuple of three components representing the coordinates at that time. For
;;  example, we can evaluate this procedure for a symbolic time t as follows:


(print-expression
  (q 't))

;;  The derivative of the coordinate path $Dq$ is the function that maps time to
;;  velocity components:
;;
;;  $Dq(t) = (Dx(t),Dy(t),Dz(t)).$
;;
;;  We can make and use the derivative of a function.[fn:29] For example, we can
;;  write:


(print-expression
  ((D q) 't))

;;  The function Γ takes a coordinate path and returns a function of time that
;;  gives the local tuple $(t, q(t), Dq(t), ...)$. We implement this Γ with
;;  the procedure Gamma.[fn:30] Here is what Gamma does:

(print-expression
 ((Gamma q) 't))


;;  So the composition $L ∘ Γ$ is a function of time that returns the value of the
;;  Lagrangian for this point on the path:[fn:31]


(show-expression
 ((compose (L-free-particle 'm) (Gamma q)) 't))

;;  The procedure `show-expression` simplifies the expression and uses $\TeX$ to
;;  display the result in traditional infix form. We use this method of display to
;;  make the boxed expressions in this book. The procedure show-expression also
;;  produces the prefix form, but we usually do not show this.[fn:32]
;;
;;
;;  According to equation (1.4) we can compute the Lagrangian action from time
;;  $t_{1}$ to time $t_{2}$ as:


(define (Lagrangian-action L q t1 t2)
  (definite-integral
    (compose L (Gamma q)) t1 t2))

;;  `Lagrangian-action` takes as arguments a procedure L that computes the
;;  Lagrangian, a procedure q that computes a coordinate path, and starting and
;;  ending times `t1` and `t2`. The `definite-integral` used here takes as arguments a
;;  function and two limits `t1` and `t2`, and computes the definite integral of the
;;  function over the interval from `t1` to `t2`.[fn:33] Notice that the definition of
;;  `Lagrangian-action` does not depend on any particular set of coordinates or even
;;  the dimension of the configuration space. The method of computing the action
;;  from the coordinate representation of a Lagrangian and a coordinate path does
;;  not depend on the coordinate system.
;;
;;  We can now compute the action for the free particle along a path. For example,
;;  consider a particle moving at uniform speed along a straight line $t ↦
;;  (4t + 7, 3t + 5, 2t + 1)$.[fn:34] We represent the path as a procedure

(define (test-path t)
  (up (+ (* 4 t) 7)
      (+ (* 3 t) 5)
      (+ (* 2 t) 1)))


;;  For a particle of mass 3, we obtain the action between /t/ = 0 and /t/ = 10
;;  as[fn:35]


(print-expression
  (Lagrangian-action (L-free-particle 3.0) test-path 0.0 10.0))

;;### Exercise 1.4: Lagrangian actions
;;
;;    For a free particle an appropriate Lagrangian is[fn:36]
;;
;;    $$
;;    {L(t,x,v) = \frac{1}{2}mv^{2}.} \tag{1.8)}
;;    $$
;;
;;    Suppose that $x$ is the constant-velocity straight-line path of a free
;;    particle, such that $x_{a} = x(t_{a})$ and $x_{b} =
;;    x(t_{b})$. Show that the action on the solution path is
;;
;;    $$
;;    {\frac{m}{2}\frac{{(x_{b} - x_{a})}^{2}}{t_{b} - t_{a}}.} \tag{1.9}
;;    $$
;;
;;## Paths of minimum action
;
;;   We already know that the actual path of a free particle is uniform motion in
;;   a straight line. According to Euler and Lagrange, the action is smaller along
;;   a straight-line test path than along nearby paths. Let $q$ be a straight-line
;;   test path with action $S\left[q\right](t_1, t_2)$. Let $q + \varepsilon \eta$
;;   be a nearby path, obtained from $q$ by adding a path variation $\eta$ scaled
;;   by the real parameter $\varepsilon$.[fn:37] The action on the varied path is
;;   $S\left[q + \varepsilon \eta\right](t_1, t_2)$. Euler and Lagrange found that
;;   $S\left[q + \varepsilon \eta\right](t_1, t_2) > S\left[q\right](t_1, t_2)$
;;   for any $\eta$ that is zero at the endpoints and for any small nonzero
;;   $\eta$.
;;
;;   Let's check this numerically by varying the test path, adding some amount of
;;   a test function that is zero at the endpoints $t = t_1$ and $t = t_2$. To
;;   make a function $\eta$ that is zero at the endpoints, given a sufficiently
;;   well-behaved function $ν$, we can use $η(t) = (t − t_{1})(t − t_{2})ν(t)$. This can be implemented:

(define ((make-eta nu t1 t2) t)
  (* (- t t1) (- t t2) (nu t)))


;;   We can use this to compute the action for a free particle over a path varied
;;   from the given path, as a function of $ϵ$:[fn:38]


(define ((varied-free-particle-action mass q nu t1 t2) eps)
  (let ((eta (make-eta nu t1 t2)))
    (Lagrangian-action (L-free-particle mass)
                       (+ q (* eps eta)) t1 t2)))
;
;   The action for the varied path, with $ν(t) = (\sin t, \cos t, t^{2})$
;   and $ϵ = 0.001$, is, as expected, larger than for the test path:

(print-expression
  ((varied-free-particle-action 3.0 test-path (up sin cos square) 0.0 10.0) 0.001))

;
;   We can numerically compute the value of $ϵ$ for which the action is
;   minimized. We search between, say, −2 and 1:[fn:39]
;

(print-expression
  (minimize
    (varied-free-particle-action 3.0 test-path (up sin cos square) 0.0 10.0) 
    -2.0 1.0))

;   We find exactly what is expected---that the best value for $ϵ$ is
;   zero,[fn:40] and the minimum value of the action is the action along the
;   straight path.
;
;### Finding trajectories that minimize the action


;;   We have used the variational principle to determine if a given trajectory is
;;   realizable. We can also use the variational principle to find trajectories.
;;   Given a set of trajectories that are specified by a finite number of
;;   parameters, we can search the parameter space looking for the trajectory in
;;   the set that best approximates the real trajectory by finding one that
;;   minimizes the action. By choosing a good set of approximating functions we
;;   can get arbitrarily close to the real trajectory.[fn:41]
;;
;;   One way to make a parametric path that has fixed endpoints is to use a
;;   polynomial that goes through the endpoints as well as a number of
;;   intermediate points. Variation of the positions of the intermediate points
;;   varies the path; the parameters of the varied path are the coordinates of the
;;   intermediate positions. The procedure `make-path` constructs such a path using
;;   a Lagrange interpolation polynomial. The procedure `make-path` is called with
;;   five arguments: `(make-path t0 q0 t1 q1 qs)`, where q0 and q1 are the
;;   endpoints, t0 and t1 are the corresponding times, and qs is a list of
;;   intermediate points.[fn:42]
;;
;;   Having specified a parametric path, we can construct a parametric action that
;;   is just the action computed along the parametric path:


^:kindly/hide-code
(define make-path lg/make-path)

(define ((parametric-path-action Lagrangian t0 q0 t1 q1) qs)
  (let ((path (make-path t0 q0 t1 q1 qs)))
    (Lagrangian-action Lagrangian path t0 t1)))

;;
;;   We can find approximate solution paths by finding parameters that minimize
;;   the action. We do this minimization with a canned multidimensional
;;   minimization procedure:[fn:43]
;;

(define (find-path Lagrangian t0 q0 t1 q1 n)
  (let ((initial-qs (linear-interpolants q0 q1 n)))
    (let ((minimizing-qs (multidimensional-minimize
                          (parametric-path-action Lagrangian t0 q0 t1 q1) initial-qs)))
      (make-path t0 q0 t1 q1 minimizing-qs))))


;;   The procedure `multidimensional-minimize` takes a procedure (in this case the
;;   value of the call to `parametric-path-action`) that computes the function to be
;;   minimized (in this case the action) and an initial guess for the parameters.
;;   Here we choose the initial guess to be equally spaced points on a straight
;;   line between the two endpoints, computed with `linear-interpolants`.
;;
;;   To illustrate the use of this strategy, we will find trajectories of the
;;   harmonic oscillator, with Lagrangian[fn:44]
;;
;;   $$
;;   {L(t,q,v) = \frac{1}{2}mv^{2} - \frac{1}{2}kq^{2},} \tag{1.10}
;;   $$
;;
;;   for mass $m$ and spring constant $k$. This Lagrangian is implemented
;;   by[fn:45]
;;
;;   *Figure 1.1* The difference between the polynomial
;;   approximation with minimum action and the actual trajectory taken by the
;;   harmonic oscillator. The abscissa is the time and the ordinate is the error.
;;   [comment MAK: you find the plot in the notebook SICM Ch01 Graphics]
;;

(define ((L-harmonic m k) local)
  (let ((q (coordinate local))
        (v (velocity local)))
    (- (* 1/2 m (square v))
       (* 1/2 k (square q)))))

;;
;;   We can find an approximate path taken by the harmonic oscillator for $m = 1$
;;   and $k = 1$ between $q(0) = 1$ and $q(π/2) = 0$ as follows:[fn:46]
;;

^:kindly/hide-code
(define pi-half (* 0.5 Math/PI))

(define q
  (find-path (L-harmonic 1.0 1.0) 0.0 1.0 pi-half 0.0 3))

;;
;;   We know that the trajectories of this harmonic oscillator, for $m = 1$ and
;;   $k = 1$, are
;;
;; $$
;;   {q(t) = A\,\,\text{cos}(t + \varphi)} \tag{1.11}
;; $$
;;
;;
;;   where the amplitude $A$ and the phase $φ$ are determined by the initial
;;   conditions. For the chosen endpoint conditions the solution is $q(t) =
;;   cos(t)$. The approximate path should be an approximation to cosine over the
;;   range from $0$ to $π/2$. (Figure 1.1) shows the error in the polynomial
;;   approximation produced by this process. The maximum error in the
;;   approximation with three intermediate points is less than $1.7 × 10^{−4}$. We
;;   find, as expected, that the error in the approximation decreases as the
;;   number of intermediate points is increased. For four intermediate points it
;;   is about a factor of 15 better.
;;
;;   ### Exercise 1.5: Solution process
;;
;;   We can watch the progress of the minimization by modifying the procedure
;;   parametric-path-action to plot the path each time the action is computed. Try
;;   this:

^:kindly/hide-code
(kind/code "(define win2 (frame 0.0 :pi/2 0.0 1.2))

(define ((parametric-path-action Lagrangian t0 q0 t1 q1) intermediate-qs)
  (let ((path (make-path t0 q0 t1 q1 intermediate-qs))) ;; display path
    (graphics-clear win2)
    (plot-function win2 path t0 t1 (/ (- t1 t0) 100)) ;; compute action
    (Lagrangian-action Lagrangian path t0 t1)))

(find-path (L-harmonic 1.0 1.0) 0.0 1.0 :pi/2 0.0 2)")
;; [comment MAK: SICMutils graphics is not implemented in Emmy]

;;   ### Exercise 1.6: Minimizing action
;;
;;   Suppose we try to obtain a path by minimizing an action for an impossible
;;   problem. For example, suppose we have a free particle and we impose endpoint
;;   conditions on the velocities as well as the positions that are inconsistent
;;   with the particle being free. Does the formalism protect itself from such an
;;   unpleasant attack? You may find it illuminating to program it and see what
;;   happens.
;;


;;## 1.5 The Euler--Lagrange Equations

;;  The principle of stationary action characterizes the realizable paths of
;;  systems in configuration space as those for which the action has a stationary
;;  value. In elementary calculus, we learn that the critical points of a function
;;  are the points where the derivative vanishes. In an analogous way, the paths
;;  along which the action is stationary are solutions of a system of differential
;;  equations. This system, called the *Euler--Lagrange equations* or just the
;;  *Lagrange equations*, is the link that permits us to use the principle of
;;  stationary action to compute the motions of mechanical systems, and to relate
;;  the variational and Newtonian formulations of mechanics.
;;
;;
;;
;;### Lagrange equations

;;   We will find that if $L$ is a Lagrangian for a system that depends on time,
;;   coordinates, and velocities, and if $q$ is a coordinate path for which the
;;   action $S[q](t_{1}, t_{2})$ is stationary (with respect to any
;;   variation in the path that keeps the endpoints of the path fixed), then
;;
;;   $$
;;   {D(\partial_{2}L \circ \Gamma\lbrack q\rbrack) - \partial_{1}L \circ \Gamma\lbrack q\rbrack = 0.} \tag{1.12}
;;   $$
;;
;;   Here $L$ is a real-valued function of a local tuple; $∂_{1}L$ and $∂_{2}L$
;;   denote the partial derivatives of $L$ with respect to its generalized
;;   position argument and generalized velocity argument respectively.[fn:47] The
;;   function $∂_{2}L$ maps a local tuple to a structure whose components are the
;;   derivatives of $L$ with respect to each component of the generalized
;;   velocity. The function $Γ[q]$ maps time to the local tuple: $Γ[q](t) =
;;   (t, q(t), Dq(t), ...)$. Thus the compositions $∂_{1}L ∘ Γ[q]$ and
;;   $∂_{2}L ∘ Γ[q]$ are functions of one argument, time. The Lagrange equations
;;   assert that the derivative of $∂_{2}L ∘ Γ[q]$ is equal to $∂_{1}L ∘
;;   Γ[q]$, at any time. Given a Lagrangian, the Lagrange equations form a system
;;   of ordinary differential equations that must be satisfied by realizable
;;   paths.
;;
;;   Lagrange's equations are traditionally written as a separate equation for
;;   each component of $q$:
;;
;;   $$
;; {\frac{d}{dt}\frac{\partial
;;   L}{\partial{\dot{q}}^{i}} - \frac{\partial L}{\partial q^{i}} = 0} 
;;   \hspace{1cm}{i = 0,\ldots,n - 1.} 
;; $$
;;
;;   In this way of writing Lagrange's equations the notation does not distinguish
;;   between $L$, which is a real-valued function of three variables $(t, q, \dot{q})$, 
;; and $L ∘ Γ[q]$, which is a real-valued function of one real variable
;;   $t$. If we do not realize this notational pun, the equations don't make sense
;;   as written --- $∂L/∂\dot{q}$
;;   is a function of three variables, so we must regard the arguments $q$, $\dot{q}$
;;   as functions of $t$ before taking $d/dt$ of the expression. Similarly,
;;   $∂L/∂q$ is a function of three variables, which we must view as a function
;;   of $t$ before setting it equal to $d/dt\left( {\partial L/\partial\dot{q}} \right)$
;;
;;   A correct use of the traditional notation is more explicit:
;;
;; $$\frac{d}{d t}\left( \left.\frac{\partial L(t, w, \dot{w})}{\partial \dot{w}^{i}}
;; \right|_{\substack{ {w=q(t)} \\ {\dot{w}=\frac{d q(t)}{d t}} }}
;; \right)-\left.\frac{\partial L(t, w, \dot{w})}{\partial w^{i}}\right|_{ \substack{
;; w=q(t) \\ {\dot{w}=\frac{d q(t)}{d t}}} }=0.$$
;;
;;   where $i = 0, ..., n − 1$. In these equations we see that the partial
;;   derivatives of the Lagrangian function are taken, then the path and its
;;   derivative are substituted for the position and velocity arguments of the
;;   Lagrangian, resulting in an expression in terms of the time.

;;### 1.5.1 Derivation of the Lagrange Equations

;; The executable Scheme code of the remaining chapters is
;; accessible via the sidebar as "SICM Ch01 Graphics".

;; Continue reading the text with the nice HTML version of the entire book:
;; https://tgvaughan.github.io/sicm

;; The content of this notebook has been manually converted from the following org-file:
;; https://raw.githubusercontent.com/mentat-collective/sicm-book/refs/heads/main/org/chapter001.org

;; Maybe in the future this notebook can be used as a pattern
;; to automate (to a certain extent) the conversion of the remaining chapters.


 (repl/scittle-sidebar)


^:kindly/hide-code
(comment
;;### 1.5.1 Derivation of the Lagrange Equations
;;    We will show that the principle of stationary action implies that realizable
;;    paths satisfy the Euler--Lagrange equations.
;;
;;#### A Direct Derivation

;;   Let $q$ be a realizable coordinate path from $(t_{1}, q(t_{1}))$ to
;;   $(t_{2}, q(t_{2}))$. Consider nearby paths $q$ + $ϵη$ where $η(t_{1})
;;   = η(t_{2}) = 0$. Let
;;

;; $$
;\begin{array}{lcl}
;{g(\mathit{\epsilon})} & = & {S\lbrack q + \mathit{\epsilon}\eta\rbrack(t_{1},\,\, t_{2})} \\
;;& = & {\int_{t_{1}}^{t_{2}}{L(t,q(t) + \mathit{\epsilon}\eta(t),Dq(t) +
;;   \mathit{\epsilon}D\eta(t))dt.}} \\
;\end{array} \tag{1.13}
;$$


;;   Expanding as a power series in $ϵ$
;;
;;   $$
;;   {g(\mathit{\epsilon}) = g(0) + \mathit{\epsilon}Dg(0) + \cdots} \tag{1.14}
;;   $$
;;
;;   and using the chain rule we get
;;
;; $$
;;\begin{array}{lcl}
;;{Dg(0)} & = & {\int_{t_{1}}^{t_{2}}{(\partial_{1}L(t,q(t),Dq(t))\eta(t))\,\, dt}} \\
;;&  & {+ {\int_{t_{1}}^{t_{2}}{(\partial_{2}L(t,q(t),Dq(t))D\eta(t))\,\, dt.}}} \\
;;\end{array}
;; \tag{1.15}
;;$$

;;
;;   Integrating the second term by parts we obtain

;; $$
;\begin{array}{lcl}
;{Dg(0)} & = & 
;;   {\int_{t_{1}}^{t_{2}}{(\partial_{1}L(t,q(t),Dq(t))\eta(t))\,\, dt}} \\
;&  & {+ \,\partial_{2}L(t,q(t),Dq(t)D\eta(t))\eta(t)|_{t_{1}}^{t_{2}}} \\
;&  & {-
;;   {\int_{t_{1}}^{t_{2}}{\frac{d}{dt}(\partial_{2}L(t,q(t),Dq(t)))\eta(t)dt}}.}\\
;;\end{array}
;; \tag{1.16}
;; $$

;;
;;
;;
;;   The increment $ΔS$ in the action due to the variation in the path is, to
;;   first order in $ϵ$, $ϵDg(0)$. Because $\eta$ is zero at the endpoints the
;;   integrated term is zero. Collecting together the other two terms, and
;;   reverting to functional notation, we find the increment to be
;;
;;   $$
;;   {\Delta S = \mathit{\epsilon}{\int_{t_{1}}^{t_{2}}\left\{ \partial_{1}L \circ \Gamma\lbrack q\rbrack\, - \, D(\partial_{2}L \circ \Gamma\lbrack q\rbrack) \right\}}\eta.} \tag{1.17}
;;   $$
;;
;;   If $ΔS$ is zero the action is stationary. We retain enough freedom in the
;;   choice of the variation that the factor in the integrand multiplying $\eta$
;;   is forced to be zero at each point along the path. We argue by contradiction:
;;   Suppose this factor were nonzero at some particular time. Then it would have
;;   to be nonzero in at least one of its components. But if we choose our $\eta$
;;   to be a bump that is nonzero only in that component in a neighborhood of that
;;   time, and zero everywhere else, then the integral will be nonzero. So we may
;;   conclude that the factor in curly brackets is identically zero and thus
;;   obtain Lagrange's equations:[fn:48]
;;
;;   $$
;;   {D(\partial_{2}L \circ \Gamma\lbrack q\rbrack)\, - \,\partial_{1}L \circ \Gamma\lbrack q\rbrack = 0.} \tag{1.18}
;;   $$
;;
;;#### The Variation Operator

;;   First we will develop tools for investigating how path-dependent functions
;;   vary as the paths are varied. We will then apply these tools to the action,
;;   to derive the Lagrange equations.
;;
;;   Suppose that we have a function $f[q]$ that depends on a path $q$. How does
;;   the function vary as the path is varied? Let $q$ be a coordinate path and
;;   $q$ + $ϵη$ be a varied path, where the function $\eta$ is a path-like
;;   function that can be added to the path $q$, and the factor $ϵ$ is a scale
;;   factor. We define the *variation* $δ_{η}f[q]$ of the function $f$ on the path
;;   $q$ by[fn:49]
;;
;;   $$
;;   {\delta_{\eta}f\lbrack q\rbrack = \underset{\mathit{\epsilon}\rightarrow 0}{\lim}\left( \frac{f\lbrack q + \mathit{\epsilon}\eta\rbrack - f\lbrack q\rbrack}{\mathit{\epsilon}} \right).} \tag{1.19}
;;   $$
;;
;;   The variation of $f$ is a linear approximation to the change in the function
;;   $f$ for small variations in the path. The variation of $f$ depends on $\eta$.

;; A simple example is the variation of the identity path function: $I[q] =
;;    q$. Applying the definition, we find

;;    $$
;;    {\delta_{\eta}I\lbrack q\rbrack = \underset{\mathit{\epsilon}\rightarrow 0}{\lim}\left( \frac{(q + \mathit{\epsilon}\eta) - q}{\mathit{\epsilon}} \right) = \eta.} \tag{1.20}
;;    $$

;;    It is traditional to write /δ_{η}I/[/q/] simply as /δq/. Another example is
;;    the variation of the path function that returns the derivative of the path.
;;    We have[fn:50]

;;    \begin{equation}
;;    {\delta_{\eta}g\lbrack q\rbrack = \underset{\mathit{\epsilon}\rightarrow 0}{\lim}\left( \frac{D(q + \mathit{\epsilon}\eta) - Dq}{\mathit{\epsilon}} \right) = D\eta\,\,\,\,\text{with}\,\,\,\, g\lbrack q\rbrack = Dq.} & {(1.21)}
;;    \end{equation}

;;    It is traditional to write /δ_{η}g/[/q/] as /δDq/.

;;    The variation may be represented in terms of a derivative. Let /g/(/ϵ/) =
;;    /f/[/q/ + /ϵη/]; then

;;    \begin{equation}
;;    {\delta_{\eta}f\lbrack q\rbrack = \underset{\mathit{\epsilon}\rightarrow 0}{\lim}\left( \frac{g(\mathit{\epsilon}) - g(0)}{\mathit{\epsilon}} \right) = Dg(0).} & {(1.22)}
;;    \end{equation}

;;    Variations have the following derivative-like properties. For path-dependent
;;    functions /f/ and /g/ and constant /c/:

;;    \begin{equation}
;;    {\delta_{\eta}(f\, g)\lbrack q\rbrack = \delta_{\eta}f\lbrack q\rbrack\,\, g\lbrack q\rbrack + f\lbrack q\rbrack\,\,\delta_{\eta}g\lbrack q\rbrack} & {(1.23)}
;;    \end{equation}

;;    \begin{equation}
;;    {\delta_{\eta}(f + g)\lbrack q\rbrack = \delta_{\eta}f\lbrack q\rbrack\, + \,\,\delta_{\eta}g\lbrack q\rbrack} & {(1.24)}
;;    \end{equation}

;;    \begin{equation}
;;    {\delta_{\eta}(cf)\lbrack q\rbrack = c\,\,\delta_{\eta}f\lbrack q\rbrack.} & {(1.25)}
;;    \end{equation}

;;    Let /F/ be a path-independent function and /g/ be a path-dependent function;
;;    then

;;    \begin{equation}
;;    {\delta_{\eta}h\lbrack q\rbrack = (DF \circ g\lbrack q\rbrack)\,\,\delta_{\eta}g\lbrack q\rbrack\,\,\,\text{with}\,\,\,\, h\lbrack q\rbrack = F \circ g\lbrack q\rbrack.} & {(1.26)}
;;    \end{equation}

;;    The operators /D/ (differentiation) and /δ/ (variation) commute in the
;;    following sense:

;;    $$D\begin{equation} {\delta_{\eta}f\lbrack q\rbrack = \delta_{\eta}g\lbrack
;;    q\rbrack\,\,\,\text{with}\,\,\,\, g\lbrack q\rbrack = D(f\lbrack q\rbrack).}
;;    & {(1.27)} \end{equation}

;;    Variations also commute with integration in a similar sense.

;;    If a path-dependent function /f/ is stationary for a particular path /q/ with
;;    respect to small changes in that path, then it must be stationary for a
;;    subset of those variations that results from adding small multiples of a
;;    particular function $\eta$ to /q/. So the statement /δ_{η}f/[/q/] = 0 for
;;    arbitrary $\eta$ implies the function /f/ is stationary for small variations
;;    of the path around /q/.

;;    *Exercise 1.7: Properties of* /δ/

;;    Show that /δ/ has the properties 1.23--1.27.

;;    *Exercise 1.8: Implementation of* /δ/

;;    *a.* Suppose we have a procedure f that implements a path-dependent function:
;;    for path q and time t it has the value ((f q) t). The procedure delta
;;    computes the variation (/δ_{η}f/)[/q/](/t/) as the value of the expression
;;    ((((delta eta) f) q) t). Complete the definition of delta:

;;     #+begin_src scheme
;; (define (((delta eta) f) q) ... )
;;     #+end_src

;;    *b.* Use your delta procedure to verify the properties of /δ/ listed in
;;    [[file:chapter001.html#Exe_1-7][exercise 1.7]] for simple functions such as implemented by the procedure
;;    f:[fn:51]

;;     #+begin_src scheme
;; (define (f q)
;;   (compose (literal-function 'F (-> (UP Real (UP* Real) (UP* Real)) Real))
;;            (Gamma q)))
;;     #+end_src

;;    This implements an /n/-degree-of-freedom path-dependent function that depends
;;    on the local tuple of the path at each moment. You can define a literal
;;    two-dimensional path by

;;    (define q (literal-function 'q (-> Real (UP Real Real))))

;;    You should compute both sides of the equalities and subtract the results. The
;;    answer should be zero.

;; ** A Derivation with the Variation Operator
;;    :PROPERTIES:
;;    :CUSTOM_ID: a-derivation-with-the-variation-operator
;;    :CLASS:    level2
;;    :END:
;;    The action is the integral of the Lagrangian along a path:

;;    \begin{equation}
;;    {S\lbrack q\rbrack(t_{1},t_{2}) = {\int_{t_{1}}^{t_{2}}{L \circ \Gamma\lbrack q\rbrack.}}} & {(1.28)}
;;    \end{equation}



;;    For a realizable path /q/ the variation of the action with respect to any
;;    variation $\eta$ that preserves the endpoints, /η/(/t/_{1}) = /η/(/t/_{2}) =
;;    0, is zero:

;;    \begin{equation}
;;    {\delta_{\eta}S\lbrack q\rbrack(t_{1},t_{2}) = 0.} & {(1.29)}
;;    \end{equation}

;;    Variation commutes with integration, so the variation of the action is

;;    \begin{equation}
;;    {\delta_{\eta}S\lbrack q\rbrack(t_{1},t_{2}) = {\int_{t_{1}}^{t_{2}}{\delta_{\eta}h\lbrack q\rbrack\,\,\,\,\text{where}\,\,\,\, h\lbrack q\rbrack = L \circ \Gamma\lbrack q\rbrack.}}} & {(1.30)}
;;    \end{equation}

;;    Using the fact that

;;    \begin{equation}
;;    {\delta_{\eta}\Gamma\lbrack q\rbrack(t) = (0,\eta(t),D\eta(t)),} & {(1.31)}
;;    \end{equation}

;;    which follows from equations ([[file:chapter001.html#disp_1.20][1.20]]) and ([[file:chapter001.html#disp_1.21][1.21]]), and using the chain rule for
;;    variations ([[file:chapter001.html#disp_1.26][1.26]]), we get[fn:52]

;;    $$\begin{array}{lll} {\delta_{\eta}S\lbrack q\rbrack(t_{1},t_{2})} & {=
;;    {\int_{t_{1}}^{t_{2}}{(D\, L \circ \Gamma\lbrack
;;    q\rbrack)\delta_{\eta}\Gamma\lbrack q\rbrack}}} & \\
;;    & {= {\int_{t_{1}}^{t_{2}}{((\partial_{1}L \circ \Gamma\lbrack
;;    q\rbrack)\eta + (\partial_{2}L \circ \Gamma\lbrack q\rbrack)D\eta).}}} &
;;    {(1.32)} \end{equation}

;;    Integrating the last term of equation ([[file:chapter001.html#disp_1.32][1.32]]) by parts gives

;;    $$\begin{array}{lll} {\delta_{\eta}S\lbrack q\rbrack(t_{1},t_{2}) =} &
;;    {(\partial_{2}L \circ \Gamma\lbrack q\rbrack)\eta|_{t_{1}}^{t_{2}}} & \\
;;    & {+ {\int_{t_{1}}^{t_{2}}{\left\{ (\partial_{1}L \circ \Gamma\lbrack
;;    q\rbrack) - D(\partial_{2}L \circ \Gamma\lbrack q\rbrack) \right\}\eta.}}} &
;;    {(1.33)} \end{equation}

;;    For our variation $\eta$ we have /η/(/t/_{1}) = /η/(/t/_{2}) = 0, so the
;;    first term vanishes.

;;    Thus the variation of the action is zero if and only if

;;    \begin{equation}
;;    {0 = {\int_{t_{1}}^{t_{2}}{\left\{ (\partial_{1}L \circ \Gamma\lbrack q\rbrack) - D(\partial_{2}L \circ \Gamma\lbrack q\rbrack) \right\}\,\eta.}}} & {(1.34)}
;;    \end{equation}



;;    The variation of the action is zero because, by assumption, /q/ is a
;;    realizable path. Thus ([[file:chapter001.html#disp_1.34][1.34]]) must be true for /any/ function $\eta$ that is
;;    zero at the endpoints. Since $\eta$ is arbitrary, except for being zero at
;;    the endpoints, the bracketed factor of the integrand is zero. So

;;    \begin{equation}
;;    {D(\partial_{2}L \circ \Gamma\lbrack q\rbrack) - (\partial_{1}L \circ \Gamma\lbrack q\rbrack) = 0.} & {(1.35)}
;;    \end{equation}

;;    This is just what we set out to obtain, the Lagrange equations.

;;    A path satisfying Lagrange's equations is one for which the action is
;;    stationary, and the fact that the action is stationary depends only on the
;;    values of /L/ at each point of the path (and at each point on nearby paths),
;;    not on the coordinate system we use to compute these values. So if the
;;    system's path satisfies Lagrange's equations in some particular coordinate
;;    system, it must satisfy Lagrange's equations in /any/ coordinate system. Thus
;;    the equations of variational mechanics are derived the same way in any
;;    configuration space and any coordinate system.

;; ** Harmonic oscillator
;;    :PROPERTIES:
;;    :CUSTOM_ID: harmonic-oscillator
;;    :CLASS:    level2
;;    :END:
;;    For an example, consider the harmonic oscillator. A Lagrangian is

;;    \begin{equation}
;;    {L(t,x,v) = \frac{1}{2}mv^{2} - \frac{1}{2}kx^{2}.} & {(1.36)}
;;    \end{equation}

;;    Then

;;    \begin{equation}
;;    {\partial_{1}L(t,x,v) = - kx\,\,\,\,\,\,\text{and}\,\,\,\,\partial_{2}L(t,x,v) = mv.} & {(1.37)}
;;    \end{equation}

;;    The Lagrangian is applied to a tuple of the time, a coordinate, and a
;;    velocity. The symbols /t/, /x/, and /v/ are arbitrary; they are used to
;;    specify formal parameters of the Lagrangian.

;;    Now suppose we have a configuration path /y/, which gives the coordinate of
;;    the oscillator /y/(/t/) for each time /t/. The initial segment of the
;;    corresponding local tuple at time /t/ is

;;    \begin{equation}
;;    {\Gamma\lbrack y\rbrack(t) = (t,y(t),Dy(t)).} & {(1.38)}
;;    \end{equation}

;;    So

;;    \begin{equation}
;;    {(\partial_{1}L \circ \Gamma\lbrack y\rbrack)(t) = - ky(t)\,\,\,\,\,\text{and}\,\,\,\,\,(\partial_{2}L \circ \Gamma\lbrack y\rbrack)(t) = mDy(t),} & {(1.39)}
;;    \end{equation}

;;    and

;;    \begin{equation}
;;    {D(\partial_{2}L \circ \Gamma\lbrack y\rbrack)(t) = mD^{2}y(t),} & {(1.40)}
;;    \end{equation}

;;    so the Lagrange equation is



;;    \begin{equation} {mD^{2}y(t) + ky(t) = 0,} & {(1.41)} \end{equation}

;;    which is the equation of motion of the harmonic oscillator.

;; ** Orbital motion
;;    :PROPERTIES:
;;    :CUSTOM_ID: orbital-motion
;;    :CLASS:    level2
;;    :END:
;;    As another example, consider the two-dimensional motion of a particle of mass
;;    /m/ orbiting a fixed center of attraction, with gravitational potential
;;    energy −/μ/r/, where /r/ is the distance to the center of attraction. This is
;;    called the /Kepler problem/.

;;    A Lagrangian for this problem is[fn:53]

;;    \begin{equation}
;;    {L(t;\xi,\eta;v_{\xi},v_{\eta}) = \frac{1}{2}m(v_{\xi}^{2} + v_{\eta}^{2}) + \frac{\mu}{\sqrt{\xi^{2} + \eta^{2}}},} & {(1.42)}
;;    \end{equation}

;;    where $\xi$ and $\eta$ are formal parameters for rectangular coordinates of
;;    the particle, and /v_{ξ}/ and /v_{η}/ are formal parameters for corresponding
;;    rectangular velocity components. Then

;;    $$\begin{array}{lll} {\partial_{1}L(t;\xi,\eta;v_{\xi},v_{\eta})} & {=
;;    \lbrack\partial_{1,0}L(t;\xi,\eta;v_{\xi},v_{\eta}),\partial_{1,1}L(t;\xi,\eta;v_{\xi},v_{\eta})\rbrack}
;;    & \\
;;    & {{= \left\lbrack {\frac{- \mu\xi}{{(\xi^{2} + \eta^{2})}^{3/2}},\frac{-
;;    \mu\eta}{{(\xi^{2} + \eta^{2})}^{3/2}}} \right\rbrack}.} & {(1.43)}
;;    \end{equation}

;;    Similarly,

;;    \begin{equation}
;;    {\partial_{2}L(t;\xi,\eta;v_{\xi},v_{\eta}) = \lbrack mv_{\xi},mv_{\eta}\rbrack.} & {(1.44)}
;;    \end{equation}

;;    Now suppose we have a configuration path /q/ = (/x/, /y/), so that the
;;    coordinate tuple at time /t/ is /q/(/t/) = (/x/(/t/), /y/(/t/)). The initial
;;    segment of the local tuple at time /t/ is

;;    \begin{equation}
;;    {\Gamma\lbrack q\rbrack(t) = (t;x(t),y(t);Dx(t),Dy(t)).} & {(1.45)}
;;    \end{equation}

;;    So

;;    $$\begin{array}{lll} {(\partial_{1}L \circ \Gamma\lbrack q\rbrack)(t)} & {=
;;    \left\lbrack {\frac{- \mu x(t)}{{({(x(t))}^{2} +
;;    {(y(t))}^{2})}^{3/2}},\frac{- \mu y(t)}{{({(x(t))}^{2} +
;;    {(y(t))}^{2})}^{3/2}}} \right\rbrack} & \\
;;    {(\partial_{2}\, L \circ \Gamma\lbrack q\rbrack)(t)} & {= \lbrack
;;    mDx(t),mDy(t)\rbrack} & {(1.46)} \end{equation}

;;    and

;;    \begin{equation}
;;    {D(\partial_{2}\, L \circ \Gamma\lbrack q\rbrack)(t) = \lbrack mD^{2}x(t),mD^{2}y(t)\rbrack.} & {(1.47)}
;;    \end{equation}



;;    The component Lagrange equations at time /t/ are

;;    \begin{equation}
;;    {mD^{2}x(t) + \frac{\mu x(t)}{{({(x(t))}^{2} + {(y(t))}^{2})}^{3/2}} = 0} & \\
;;    {mD^{2}y(t) + \frac{\mu y(t)}{{({(x(t))}^{2} + {(y(t))}^{2})}^{3/2}} = 0.} & {(1.48)}
;;    \end{equation}

;;    *Exercise 1.9: Lagrange's equations*

;;    Derive the Lagrange equations for the following systems, showing all of the
;;    intermediate steps as in the harmonic oscillator and orbital motion examples.

;;    *a.* An ideal planar pendulum consists of a bob of mass /m/ connected to a
;;    pivot by a massless rod of length /l/ subject to uniform gravitational
;;    acceleration /g/. A Lagrangian is

;;    $L(t,\theta,\overset{˙}{\theta}) =
;;    \frac{1}{2}ml^{2}{\overset{˙}{\theta}}^{2} + mgl\,\,\cos\theta$

;;    . The formal parameters of /L/ are /t/, /θ/, and

;;    $\overset{˙}{\theta}$

;;    ; /θ/ measures the angle of the pendulum rod to a plumb line and

;;    $\overset{˙}{\theta}$

;;    is the angular velocity of the rod.[fn:54]

;;    *b.* A particle of mass /m/ moves in a two-dimensional potential /V/ (/x/,
;;    /y/) = (/x/^{2} + /y/^{2})/2 + /x/^{2}/y/ − /y/^{3}/3, where /x/ and /y/ are
;;    rectangular coordinates of the particle. A Lagrangian is

;;    $L(t;x,y;v_{x},v_{y}) = \frac{1}{2}m\left( {v_{x}^{2} + v_{y}^{2}} \right) -
;;    V(x,y)$

;;    .

;;    *c.* A Lagrangian for a particle of mass /m/ constrained to move on a sphere
;;    of radius /R/ is

;;    $L(t;\theta,\varphi;\alpha,\beta) = \frac{1}{2}mR^{2}{({\alpha^{2} +
;;    {(\beta\,\sin\theta)}^{2}})}$

;;    . The angle /θ/ is the colatitude of the particle and /φ/ is the longitude;
;;    the rate of change of the colatitude is /α/ and the rate of change of the
;;    longitude is /β/.

;;    *Exercise 1.10: Higher-derivative Lagrangians*

;;    Derive Lagrange's equations for Lagrangians that depend on accelerations. In
;;    particular, show that the Lagrange equations for Lagrangians of the form
;;    /L/(/t, q,/ 

;;    q˙

;;    ,

;;    q¨

;;    ) with

;;    q¨

;;    terms are[fn:55]

;;    \begin{equation}
;;    {D^{2}(\partial_{3}L \circ \Gamma\lbrack q\rbrack) - D(\partial_{2}L \circ \Gamma\lbrack q\rbrack) + \partial_{1}L \circ \Gamma\lbrack q\rbrack) = 0.} & {(1.49)}
;;    \end{equation}

;;    In general, these equations, first derived by Poisson, will involve the
;;    fourth derivative of /q/. Note that the derivation is completely analogous to
;;    the derivation of the Lagrange equations without accelerations; it is just
;;    longer. What restrictions must we place on the variations so that the
;;    critical path satisfies a differential equation?

;; *** 1.5.2 Computing Lagrange's Equations
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-5-2
;;     :CLASS:    level3
;;     :END:
;;     The procedure for computing Lagrange's equations mirrors the functional
;;     expression ([[file:chapter001.html#disp_1.12][1.12]]), where the procedure Gamma implements Γ:[fn:56]

;;      #+begin_src scheme
;; (define ((Lagrange-equations Lagrangian) q)
;;   (- (D (compose ((partial 2) Lagrangian) (Gamma q)))
;;      (compose ((partial 1) Lagrangian) (Gamma q))))
;;      #+end_src

;;     The argument of Lagrange-equations is a procedure that computes a
;;     Lagrangian. The Lagrange-equations procedure returns a procedure that when
;;     applied to a path q returns a procedure of one argument (time) that computes
;;     the left-hand side of the Lagrange equations ([[file:chapter001.html#disp_1.12][1.12]]). These residual values
;;     are zero if q is a path for which the Lagrangian action is stationary.

;;     Observe that the Lagrange-equations procedure, like the Lagrange equations
;;     themselves, is valid for /any/ generalized coordinate system. When we write
;;     programs to investigate particular systems, the procedures that implement
;;     the Lagrangian function and the path /q/ will reflect the actual coordinates
;;     chosen to represent the system, but we use the same Lagrange-equations
;;     procedure in each case. This abstraction reflects the important fact that
;;     the method of derivation of Lagrange's equations from a Lagrangian is always
;;     the same; it is independent of the number of degrees of freedom, the
;;     topology of the configuration space, and the coordinate system used to
;;     describe points in the configuration space.

;; ** The free particle
;;    :PROPERTIES:
;;    :CUSTOM_ID: the-free-particle
;;    :CLASS:    level2
;;    :END:
;;    Consider again the case of a free particle. The Lagrangian is implemented by
;;    the procedure L-free-particle. Rather than numerically integrating and
;;    minimizing the action, as we did in [[file:chapter001.html#h1-5][section 1.4]], we can check Lagrange's
;;    equations for an arbitrary straight-line path /t/ ↦ (/at/ + /a/_{0}, /bt/ +
;;    /b/_{0}, /ct/ + /c/_{0}):



;;     #+begin_src scheme
;; (define (test-path t)
;;   (up (+ (* 'a t) 'a0) (+ (* 'b t) 'b0) (+ (* 'c t) 'c0)))

;; (((Lagrange-equations (L-free-particle 'm)) test-path) 't)
;;     #+end_src

;;     #+begin_src scheme
;; (down 0 0 0)
;;     #+end_src

;;    That the residuals are zero indicates that the test path satisfies the
;;    Lagrange equations.[fn:57]

;;    We can also apply the Lagrange-equations procedure to an arbitrary
;;    function:[fn:58]

;;    #+begin_src scheme
;; (show-expression
;;  (((Lagrange-equations (L-free-particle 'm))
;;    (literal-function 'x)) 't))

;; (* (((expt D 2) x) t) m)
;;    #+end_src

;;    $$mD^{2}x(t)$$

;;    The result is an expression containing the arbitrary time /t/ and mass /m/,
;;    so it is zero precisely when /D/^{2}/x/ = 0, which is the expected equation
;;    for a free particle.

;; ** The harmonic oscillator
;;    :PROPERTIES:
;;    :CUSTOM_ID: the-harmonic-oscillator
;;    :CLASS:    level2
;;    :END:
;;    Consider the harmonic oscillator again, with Lagrangian ([[file:chapter001.html#disp_1.10][1.10]]). We know that
;;    the motion of a harmonic oscillator is a sinusoid with a given amplitude,
;;    frequency, and phase:

;;    \begin{equation}
;;    {x(t) = a\,\,\cos(\omega t + \varphi).} & {(1.50)}
;;    \end{equation}



;;    Suppose we have forgotten how the constants in the solution relate to the
;;    mass /m/ and spring constant /k/ of the oscillator. Let's plug in the
;;    proposed solution and look at the residual:

;;     #+begin_src scheme
;; (define (proposed-solution t)
;;   (* 'A (cos (+ (* 'omega t) 'phi))))

;; (show-expression (((Lagrange-equations (L-harmonic 'm 'k)) proposed-solution)
;; 't))
;;     #+end_src

;;    $$\cos\,(\omega t + \varphi)\, A\,(k - m\omega^{2})$$

;;    The residual here shows that for nonzero amplitude, the only solutions
;;    allowed are ones where (/k/ − /mω/^{2}) = 0 or

;;    $\omega = \sqrt{k/m}.$

;;    *Exercise 1.11: Kepler's third law*

;;    A Lagrangian suitable for studying the relative motion of two particles, of
;;    masses /m/_{1} and /m/_{2}, with potential energy /V/, is:

;;     #+begin_src scheme
;; (define ((L-central-polar m V) local)
;;   (let ((q (coordinate local)) (qdot (velocity local)))
;;     (let ((r (ref q 0))
;;           (phi (ref q 1))
;;           (rdot (ref qdot 0))
;;           (phidot (ref qdot 1)))
;;       (- (* 1/2 m (+ (square rdot) (square (* r phidot))))
;;          (V r)))))
;;     #+end_src

;;    The argument m is the /reduced mass/ of the system

;;    \begin{equation}
;;    {m = \frac{m_{1}m_{2}}{m_{1} + m_{2}}.} & {(1.51)}
;;    \end{equation}

;;    For gravity, the potential energy function is

;;     #+begin_src scheme
;; (define ((gravitational-energy G m1 m2) r) (- (/ (* G m1 m2) r)))
;;     #+end_src

;;    where r is the distance between the two particles.

;;    Consider the simple situation of the particles in circular orbits around
;;    their common center of mass. Construct a circular orbit and plug it into the
;;    Lagrange equations. Show that the residual gives Kepler's law:

;;    \begin{equation}
;;    {n^{2}a^{3} = G(m_{1} + m_{2})} & {(1.52)}
;;    \end{equation}

;;    where /n/ is the angular frequency of the orbit and /a/ is the distance
;;    between the particles.



;;    *Exercise 1.12: Lagrange's Equations*

;;    Compute Lagrange's equations for the Lagrangians in [[file:chapter001.html#Exe_1-9][exercise 1.9]] using the
;;    Lagrange-equations procedure. Additionally, use the computer to perform each
;;    of the steps in the Lagrange-equations procedure and show the intermediate
;;    results. Relate these steps to the ones you showed in the hand derivation of
;;    [[file:chapter001.html#Exe_1-9][exercise 1.9]].

;;    *Exercise 1.13: Higher-derivative Lagrangians*

;;    *a.* Write a procedure to compute the Lagrange equations for Lagrangians that
;;    depend upon acceleration, as in [[file:chapter001.html#Exe_1-10][exercise 1.10]]. Note that Gamma can take an
;;    optional argument giving the length of the initial segment of the local tuple
;;    needed. The default length is 3, giving components of the local tuple up to
;;    and including the velocities.

;;    *b.* Use your procedure to compute the Lagrange equations for the Lagrangian

;;    $L(t,x,v,a) = - \frac{1}{2}mxa - \frac{1}{2}kx^{2}.$

;;    Do you recognize the resulting equation of motion?

;;    *c.* For more fun, write the general Lagrange equation procedure that takes a
;;    Lagrangian that depends on any number of derivatives, and the number of
;;    derivatives, to produce the required equations of motion.

;; * 1.6 How to Find Lagrangians
;;   :PROPERTIES:
;;   :CUSTOM_ID: h1-6a
;;   :CLASS:    level1
;;   :END:
;;   Lagrange's equations are a system of second-order differential equations. In
;;   order to use them to compute the evolution of a mechanical system, we must
;;   find a suitable Lagrangian for the system. There is no general way to
;;   construct a Lagrangian for every system, but there is an important class of
;;   systems for which we can identify Lagrangians in a straightforward way in
;;   terms of kinetic and potential energy. The key idea is to construct a
;;   Lagrangian /L/ such that Lagrange's equations are Newton's equations

;;   $\overset{\rightarrow}{F} = m\overset{\rightarrow}{a}$

;;   .

;;   Suppose our system consists of /N/ particles indexed by /α/, with mass /m_{α}/
;;   and vector position

;;   ${\overset{\rightarrow}{x}}_{\alpha}(t)$

;;   . Suppose further that the forces acting on the particles can be written in
;;   terms of a gradient of a potential energy

;;   $\mathcal{V}$

;;   that is a function of the positions of the particles and possibly time, but
;;   does not depend on the velocities. In other words, the force on particle /α/
;;   is

;;   ${\overset{\rightarrow}{F}}_{\alpha} = -
;;   {\overset{\rightarrow}{\nabla}}_{{\overset{\rightarrow}{x}}_{\alpha}}\,\mathcal{V}$

;;   , where

;;   ${\overset{\rightarrow}{\nabla}}_{{\overset{\rightarrow}{x}}_{\alpha}}\,\mathcal{V}$

;;   is the gradient of

;;   $\mathcal{V}$

;;   with respect to the position of the particle with index /α/. We can write
;;   Newton's equations as

;;   \begin{equation}
;;   {D(m_{\alpha}\, D{\overset{\rightarrow}{x}}_{\alpha})(t) + {\overset{\rightarrow}{\nabla}}_{{\overset{\rightarrow}{x}}_{\alpha}}\,\mathcal{V}(t,{\overset{\rightarrow}{x}}_{0}(t),\ldots,{\overset{\rightarrow}{x}}_{N - 1}(t)) = 0.} & {(1.53)}
;;   \end{equation}

;;   Vectors can be represented as tuples of components of the vectors on a
;;   rectangular basis. So

;;   ${\overset{\rightarrow}{x}}_{1}(t)$

;;   is represented as the tuple *x*_{1}(/t/). Let /V/ be the potential energy
;;   function expressed in terms of components:

;;   \begin{equation}
;;   {V(t;\mathbf{x}_{0}(t),\ldots,\mathbf{x}_{N - 1}(t)) = \mathcal{V}(t,{\overset{\rightarrow}{x}}_{0}(t),\ldots,{\overset{\rightarrow}{x}}_{N - 1}(t)).} & {(1.54)}
;;   \end{equation}

;;   Newton's equations are

;;   \begin{equation}
;;   {D(m_{\alpha}\, D\mathbf{x}_{\alpha})(t) + \partial_{1,\alpha}V(t;\mathbf{x}_{0}(t),\ldots,\mathbf{x}_{\alpha}(t),\ldots,\mathbf{x}_{N - 1}(t)) = 0,} & {(1.55)}
;;   \end{equation}

;;   where ∂_{1,/α/}/V/ is the partial derivative of /V/ with respect to the
;;   *x*_{/α/}(/t/) argument slot.

;;   To form the Lagrange equations we collect all the position components of all
;;   the particles into one tuple /x/(/t/), so /x/(/t/) = (*x*_{0}(/t/), ...,
;;   *x*_{/N/−1}(/t/)). The Lagrange equations for the coordinate path /x/ are

;;   \begin{equation}
;;   {D(\partial_{2}L \circ \Gamma\lbrack x\rbrack) - \partial_{1}L \circ \Gamma\lbrack x\rbrack = 0.} & {(1.56)}
;;   \end{equation}

;;   Observe that Newton's equations ([[file:chapter001.html#disp_1.55][1.55]]) are
;;   just the components of the Lagrange equations
;;   ([[file:chapter001.html#disp_1.56][1.56]]) if we choose /L/ to have the
;;   properties

;;   $$\begin{array}{lll} {(\partial_{2}L \circ \Gamma\lbrack x\rbrack)(t)} & {=
;;   \lbrack m_{0}D\mathbf{x}_{0}(t),\ldots,m_{N - 1}D\mathbf{x}_{N - 1}(t)\rbrack}
;;   & \\
;;   {(\partial_{1}L \circ \Gamma\lbrack x\rbrack)(t)} & {= \lbrack -
;;   \partial_{1,0}V(t,x(t)),\ldots, - \partial_{1,N - 1}V(t,x(t))\rbrack;} &
;;   {(1.57)} \end{equation}

;;   here /V/ (/t/, /x/(/t/)) = /V/ (/t/; *x*_{0}(/t/), ..., *x*_{/N/−1}(/t/)) and
;;   ∂_{1,/α/}/V/ (/t/, /x/(/t/)) is the tuple of the components of the derivative
;;   of /V/ with respect to the coordinates of the particle with index /α/,
;;   evaluated at time /t/ and coordinates /x/(/t/). These conditions are satisfied
;;   if for every *x*_{/α/} and *v*_{/α/}



;;   \begin{equation} {\partial_{2}L(t;\mathbf{x}_{0},\ldots,\mathbf{x}_{N -
;;   1};\mathbf{v}_{0},\ldots,\mathbf{v}_{N - 1})} & \\ {\,\,\,\,\,\,\,\,\,\,{=
;;   \lbrack m_{0}\mathbf{v}_{0},\ldots,m_{N - 1}\mathbf{v}_{N - 1}\rbrack}} &
;;   {(1.58)} \end{equation}

;;   and

;;   \begin{equation}
;;   {\partial_{1}L(t;\mathbf{x}_{0},\ldots,\mathbf{x}_{N - 1};\mathbf{v}_{0},\ldots,\mathbf{v}_{N - 1})} & \\
;;   {\text{           } = \lbrack - \partial_{1,0}V(t,x),\ldots, - \partial_{1,N - 1}V(t,x)\rbrack,} & {(1.59)}
;;   \end{equation}

;;   where /x/ = (*x*_{0}, ..., *x*_{/N/−1}). One choice for /L/ that has the
;;   required properties
;;   ([[file:chapter001.html#disp_1.58][1.58]]--[[file:chapter001.html#disp_1.59][1.59]])
;;   is

;;   \begin{equation}
;;   {L(t,x,v) = \frac{1}{2}{\sum\limits_{\alpha}{m_{\alpha}v_{\alpha}^{2} - V(t,x),}}} & {(1.60)}
;;   \end{equation}

;;   where

;;   $v_{\alpha}^{2}$

;;   is the sum of the squares of the components of *v*_{/α/}.[fn:59]

;;   The first term is the kinetic energy, conventionally denoted /T/. So this
;;   choice for the Lagrangian is /L/(/t/, /x/, /v/) = /T/ (/t/, /x/, /v/)−/V/
;;   (/t/, /x/), the difference of the kinetic and potential energy. We will often
;;   extend the arguments of the potential energy function to include the
;;   velocities so that we can write /L/ = /T/ − /V/.[fn:60]

;; ** Hamilton's principle
;;    :PROPERTIES:
;;    :CUSTOM_ID: hamiltons-principle
;;    :CLASS:    level2
;;    :END:
;;    Given a system of point particles for which we can identify the force as the
;;    (negative) derivative of a potential energy /V/ that is independent of
;;    velocity, we have shown that the system evolves along a path that satisfies
;;    Lagrange's equations with /L/ = /T/ − /V/. Having identified a Lagrangian for
;;    this class of systems, we can restate the principle of stationary action in
;;    terms of energies. This statement is known as /Hamilton's principle/: A
;;    point-particle system for which the force is derived from a
;;    velocity-independent potential energy evolves along a path /q/ for which the
;;    action

;;    $S\lbrack q\rbrack(t_{1},t_{2}) = {\int_{t_{1}}^{t_{2}}{L \circ \Gamma\lbrack
;;    q\rbrack}}$



;;    is stationary with respect to variations of the path /q/ that leave the
;;    endpoints fixed, where /L/ = /T/ − /V/ is the difference between kinetic and
;;    potential energy.[fn:61]

;;    It might seem that we have reduced Lagrange's equations to nothing more than

;;    $\overset{\rightarrow}{F} = m\overset{\rightarrow}{a}$

;;    , and indeed, the principle is motivated by comparing the two equations for
;;    this special class of systems. However, the Lagrangian formulation of the
;;    equations of motion has an important advantage over

;;    $\overset{\rightarrow}{F} = m\overset{\rightarrow}{a}$

;;    . Our derivation used the rectangular components *x*_{/α/} of the positions
;;    of the constituent particles for the generalized coordinates, but if the
;;    system's path satisfies Lagrange's equations in some particular coordinate
;;    system, it must satisfy the equations in /any/ coordinate system. Thus we see
;;    that /L/ = /T/ − /V/ is suitable as a Lagrangian with /any/ set of
;;    generalized coordinates. The equations of variational mechanics are derived
;;    the same way in any configuration space and any coordinate system. In
;;    contrast, the Newtonian formulation is based on elementary geometry: In order
;;    for

;;    $D^{2}\overset{\rightarrow}{x}(t)$

;;    to be meaningful as an acceleration,

;;    $\overset{\rightarrow}{x}(t)$

;;    must be a vector in physical space. Lagrange's equations have no such
;;    restriction on the meaning of the coordinate /q/. The generalized coordinates
;;    can be any parameters that conveniently describe the configurations of the
;;    system.



;; ** Constant acceleration
;;    :PROPERTIES:
;;    :CUSTOM_ID: constant-acceleration
;;    :CLASS:    level2
;;    :END:
;;    Consider a particle of mass /m/ in a uniform gravitational field with
;;    acceleration /g/. The potential energy is /mgh/ where /h/ is the height of
;;    the particle. The kinetic energy is just

;;    $\frac{1}{2}mv^{2}$

;;    . A Lagrangian for the system is the difference of the kinetic and potential
;;    energies. In rectangular coordinates, with /y/ measuring the vertical
;;    position and /x/ measuring the horizontal position, the Lagrangian is

;;    $L(t;x,y;v_{x},v_{y}) = \frac{1}{2}m\left( {v_{x}^{2} + v_{y}^{2}} \right) -
;;    mgy$

;;    . We have[fn:62]

;;     #+begin_src scheme
;; (define ((L-uniform-acceleration m g) local) (let ((q
;;    (coordinate local)) (v (velocity local))) (let ((y (ref q 1))) (- (* 1/2 m
;;    (square v)) (* m g y))))) (show-expression (((Lagrange-equations
;;    (L-uniform-acceleration 'm 'g)) (up (literal-function 'x) (literal-function
;; 'y))) 't))
;;     #+end_src

;;    $$\begin{bmatrix} {mD^{2}x\,(t)} {gm + mD^{2}y\,(t)} \end{bmatrix}$$

;;    This equation describes unaccelerated motion in the horizontal direction
;;    (/mD/^{2}/x/(/t/) = 0) and constant acceleration in the vertical direction
;;    (/mD/^{2}/y/(/t/) = −/gm/).

;; ** Central force field
;;    :PROPERTIES:
;;    :CUSTOM_ID: central-force-field
;;    :CLASS:    level2
;;    :END:
;;    Consider planar motion of a particle of mass /m/ in a central force field,
;;    with an arbitrary potential energy /U/(/r/) depending only upon the distance
;;    /r/ to the center of attraction. We will derive the Lagrange equations for
;;    this system in both rectangular coordinates and polar coordinates.

;;    In rectangular coordinates (/x/, /y/), with origin at the center of
;;    attraction, the potential energy is

;;    $V(t;x,y) = U(\sqrt{x^{2} + y^{2}})$

;;    and the kinetic energy is

;;    $T(t;x,y;v_{x},v_{y}) = \frac{1}{2}m\left( {v_{x}^{2} + v_{y}^{2}} \right)$

;;    . A Lagrangian for the system is /L/ = /T/ − /V/:

;;    \begin{equation}
;;    {L(t;x,y;v_{x},v_{y}) = \frac{1}{2}m\left( {v_{x}^{2} + v_{y}^{2}} \right) - U(\sqrt{x^{2} + y^{2}}).} & {(1.61)}
;;    \end{equation}

;;    As a procedure:

;;     #+begin_src scheme
;; (define ((L-central-rectangular m U) local) (let ((q
;;    (coordinate local)) (v (velocity local))) (- (* 1/2 m (square v)) (U (sqrt
;;    (square q))))))
;;     #+end_src

;;    The Lagrange equations are

;;     #+begin_src scheme
;; (show-expression (((Lagrange-equations
;;    (L-central-rectangular 'm (literal-function 'U))) (up (literal-function 'x)
;;    (literal-function 'y))) 't))
;;     #+end_src

;;    $$\left\lbrack \begin{array}{l} {mD^{2}x(t) + \frac{DU\left(
;;    \sqrt{{(y\,\,(t))}^{2} + {(x\,\,(t))}^{2}} \right)\,
;;    x(t)}{\sqrt{{(y\,\,(t))}^{2} + {(x\,\,(t))}^{2}}}} {mD^{2}y(t) +
;;    \frac{DU\left( \sqrt{{(x\,\,(t))}^{2} + {(y\,\,(t))}^{2}} \right)\,
;;    y(t)}{\sqrt{{(x\,\,(t))}^{2} + {(y\,\,(t))}^{2}}}} \end{equation}
;;    \right\rbrack$$

;;    We can rewrite these Lagrange equations as:

;;    \begin{equation}
;;    {mD^{2}x(t) = - \frac{x(t)}{r(t)}DU(r(t))} & {(1.62)}
;;    \end{equation}

;;    \begin{equation}
;;    {mD^{2}y(t) = - \frac{y(t)}{r(t)}DU(r(t)),} & {(1.63)}
;;    \end{equation}

;;    where

;;    $r(t) = \sqrt{{(x(t))}^{2} + {(y(t))}^{2}}$

;;    . We can interpret these as follows. The particle is subject to a radially
;;    directed force with magnitude −/DU/(/r/). Newton's equations equate the force
;;    with the product of the mass and the acceleration. The two Lagrange equations
;;    are just the rectangular components of Newton's equations.



;;    We can describe the same system in polar coordinates. The relationship
;;    between rectangular coordinates $(x, y)$ and polar coordinates $(r, \varphi)$
;;    is

;;    \begin{equation}
;;    \begin{aligned}
;;    {x = r\cos \varphi} & \\
;;    {y = r\sin \varphi} & {(1.64)}
;;    \end{aligned}
;;    \end{equation}

;;    The relationship of the generalized velocities is derived from the coordinate
;;    transformation. Consider a configuration path that is represented in both
;;    rectangular and polar coordinates. Let

;;    $\widetilde{x}$

;;    and

;;    $\widetilde{y}$

;;    be components of the rectangular coordinate path, and let

;;    $\widetilde{r}$

;;    and

;;    $\widetilde{\varphi}$

;;    be components of the corresponding polar coordinate path. The rectangular
;;    components at time /t/ are

;;    $(\widetilde{x}(t),\widetilde{y}(t))$

;;    and the polar coordinates at time /t/ are

;;    $(\widetilde{r}(t),\widetilde{\varphi}(t))$

;;    . They are related by ([[file:chapter001.html#disp_1.64][1.64]]):

;;    \begin{equation}
;;    {\widetilde{x}(t) = \widetilde{r}(t)\cos\widetilde{\varphi}(t)} & \\
;;    {\widetilde{y}(t) = \widetilde{r}(t)\sin\widetilde{\varphi}(t).} & {(1.65)}
;;    \end{equation}

;;    The rectangular velocity at time /t/ is

;;    $(D\widetilde{x}(t),\,\, D\widetilde{y}(t))$

;;    . Differentiating ([[file:chapter001.html#disp_1.65][1.65]]) gives the relationship among the velocities

;;    $$\begin{array}{lll} {D\widetilde{x}(t)} & {=
;;    D\widetilde{r}(t)\,\cos\,\widetilde{\varphi}(t) -
;;    \widetilde{r}(t)D\widetilde{\varphi}(t)\,\sin\,\widetilde{\varphi}(t)} & \\
;;    {D\widetilde{y}(t)} & {= D\widetilde{r}(t)\,\sin\,\widetilde{\varphi}(t) +
;;    \widetilde{r}(t)D\widetilde{\varphi}(t)\,\cos\,\widetilde{\varphi}(t).} &
;;    {(1.66)} \end{equation}

;;    These relations are valid for any configuration path at any moment, so we can
;;    abstract them to relations among coordinate representations of an arbitrary
;;    velocity. Let /v_{x}/ and /v_{y}/ be the rectangular components of the
;;    velocity and

;;    $\overset{˙}{r}$

;;    and

;;    $\overset{˙}{\varphi}$

;;    be the rate of change of /r/ and /φ/. Then

;;    \begin{equation}
;;    {v_{x} = \overset{˙}{r}\cos\varphi - r\overset{˙}{\varphi}\sin\varphi} & \\
;;    {v_{y} = \overset{˙}{r}\sin\varphi + r\overset{˙}{\varphi}\cos\varphi.} & {(1.67)}
;;    \end{equation}

;;    The kinetic energy is

;;    $\frac{1}{2}m(v_{x}^{2} + v_{y}^{2})$

;;    :

;;    \begin{equation}
;;    {T(t;r,\varphi;\overset{˙}{r},\overset{˙}{\varphi}) = \frac{1}{2}m\left( {{\overset{˙}{r}}^{2} + r^{2}{\overset{˙}{\varphi}}^{2}} \right),} & {(1.68)}
;;    \end{equation}

;;    and the Lagrangian is

;;    \begin{equation}
;;    {L(t;r,\varphi;\overset{˙}{r},\overset{˙}{\varphi}) = \frac{1}{2}m\left( {{\overset{˙}{r}}^{2} + r^{2}{\overset{˙}{\varphi}}^{2}} \right) - U(r).} & {(1.69)}
;;    \end{equation}



;;    We express this Lagrangian as follows:

;;     #+begin_src scheme
;; (define ((L-central-polar m U) local)
;;   (let ((q (coordinate local))
;;         (qdot (velocity local)))
;;     (let ((r (ref q 0))
;;           (phi (ref q 1))
;;           (rdot (ref qdot 0))
;;           (phidot (ref qdot 1)))
;;       (- (* 1/2 m (+ (square rdot)
;;                      (square (* r phidot))) )
;;          (U r)))))
;;     #+end_src

;;    Lagrange's equations are

;;     #+begin_src scheme
;; (show-expression
;;  (((Lagrange-equations
;;     (L-central-polar 'm (literal-function 'U)))
;;    (up (literal-function 'r)
;;        (literal-function 'phi))) 't))
;;     #+end_src

;;    $$\left\lbrack \begin{array}{l} {mD^{2}r\,(t) -
;;    mr\,(t)\,{(D\varphi\,\,(t))}^{2} + DU\,(r\,(t))} {2mDr\,(t)\, r\,(t)\,
;;    D\varphi\,(t) + mD^{2}\varphi\,(t)\,{(r\,(t))}^{2}} \end{equation}
;;    \right\rbrack$$

;;    We can interpret the first equation as saying that the product of the mass
;;    and the radial acceleration is the sum of the force due to the potential and
;;    the centrifugal force. The second equation can be interpreted as saying that
;;    the derivative of the angular momentum /mr/^{2}/Dφ/ is zero, so angular
;;    momentum is conserved.

;;    Note that we used the same Lagrange-equations procedure for the derivation in
;;    both coordinate systems. Coordinate representations of the Lagrangian are
;;    different for different coordinate systems, and the Lagrange equations in
;;    different coordinate systems look different. Yet the same method is used to
;;    derive the Lagrange equations in any coordinate system.

;;    *Exercise 1.14: Coordinate-independence of Lagrange equations*

;;    Check that the Lagrange equations for central force motion in polar
;;    coordinates and in rectangular coordinates are equivalent. Determine the
;;    relationship among the second derivatives by substituting paths into the
;;    transformation equations and computing derivatives, then substitute these
;;    relations into the equations of motion.



;; *** 1.6.1 Coordinate Transformations
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-6-1
;;     :CLASS:    level3
;;     :END:
;;     The motion of a system is independent of the coordinates we use to describe
;;     it. This coordinate-free nature of the motion is apparent in the action
;;     principle. The action depends only on the value of the Lagrangian along the
;;     path and not on the particular coordinates used in the representation of the
;;     Lagrangian. We can use this property to find a Lagrangian in one coordinate
;;     system in terms of a Lagrangian in another coordinate system.

;;     Suppose we have a mechanical system whose motion is described by a
;;     Lagrangian /L/ that depends on time, coordinates, and velocities. And
;;     suppose we have a coordinate transformation /F/ such that /x/ = /F/ (/t/,
;;     /x/′). The Lagrangian /L/ is expressed in terms of the unprimed coordinates.
;;     We want to find a Lagrangian /L/′ expressed in the primed coordinates that
;;     describes the same system. One way to do this is to require that the value
;;     of the Lagrangian along any configuration path be independent of the
;;     coordinate system. If /q/ is a path in the unprimed coordinates and /q/′ is
;;     the corresponding path in primed coordinates, then the Lagrangians must
;;     satisfy:

;;     \begin{equation}
;;     {L\prime \circ \Gamma\lbrack q\prime\rbrack = L \circ \Gamma\lbrack q\rbrack.} & {(1.70)}
;;     \end{equation}

;;     We have seen that the transformation from rectangular to polar coordinates
;;     implies that the generalized velocities transform in a certain way. The
;;     velocity transformation can be deduced from the requirement that a path in
;;     polar coordinates and a corresponding path in rectangular coordinates are
;;     consistent with the coordinate transformation. In general, the requirement
;;     that paths in two different coordinate systems be consistent with the
;;     coordinate transformation can be used to deduce how all of the components of
;;     the local tuple transform. Given a coordinate transformation /F/, let /C/ be
;;     the corresponding function that maps local tuples in the primed coordinate
;;     system to corresponding local tuples in the unprimed coordinate system:

;;     \begin{equation}
;;     {C \circ \Gamma\lbrack q\prime\rbrack = \Gamma\lbrack q\rbrack.} & {(1.71)}
;;     \end{equation}

;;     We will deduce the general form of /C/ below.



;;     Given such a local-tuple transformation /C/, a Lagrangian /L/′ that
;;     satisfies equation ([[file:chapter001.html#disp_1.70][1.70]]) is

;;     \begin{equation}
;;     {L\prime = L \circ C} & {(1.72)}
;;     \end{equation}

;;     We can see this by substituting for /L/′ in equation ([[file:chapter001.html#disp_1.70][1.70]]):

;;     \begin{equation}
;;     {L\prime \circ \Gamma\lbrack q\prime\rbrack = L \circ C \circ \Gamma\lbrack q\prime\rbrack = L \circ \Gamma\lbrack q\rbrack.} & {(1.73)}
;;     \end{equation}

;;     To find the local-tuple transformation /C/ given a coordinate transformation
;;     /F/, we deduce how each component of the local tuple transforms. The
;;     coordinate transformation specifies how the coordinate component of the
;;     local tuple transforms

;;     \begin{equation}
;;     {x = F(t,x\prime).} & {(1.74)}
;;     \end{equation}

;;     The generalized-velocity component of the local-tuple transformation can be
;;     deduced as follows. Let /q/ and /q/′ be the same configuration path
;;     expressed in the two coordinate systems. Substituting these paths into the
;;     coordinate transformation and computing the derivative, we find

;;     \begin{equation}
;;     {Dq(t) = \partial_{0}F(t,q\prime(t)) + \partial_{1}F(t,q\prime(t))Dq\prime(t).} & {(1.75)}
;;     \end{equation}

;;     Through any point there is always a path of any given velocity, so we may
;;     generalize and conclude that along corresponding coordinate paths the
;;     generalized velocities satisfy

;;     \begin{equation}
;;     {v = \partial_{0}F(t,x\prime) + \partial_{1}F(t,x\prime)v\prime.} & {(1.76)}
;;     \end{equation}

;;     If needed, rules for higher-derivative components of the local tuple can be
;;     determined in a similar fashion. The local-tuple transformation that takes a
;;     local tuple in the primed system to a local tuple in the unprimed system is
;;     constructed from the component transformations:

;;     $$\begin{array}{lll} {(t,x,v,\ldots)} & {= C(t,x\prime,v\prime,\ldots)} & \\
;;     & {= (t,F(t,x\prime),\,\,\partial_{0}F(t,x\prime) +
;;     \partial_{1}F(t,x\prime)v\prime,\ldots).} & {(1.77)} \end{equation}

;;     So if we take the Lagrangian /L/′ to be

;;     \begin{equation}
;;     {L\prime = L \circ C,} & {(1.78)}
;;     \end{equation}



;;     then the action has a value that is independent of the coordinate system
;;     used to compute it. The configuration path of stationary action does not
;;     depend on which coordinate system is used to describe the path. The Lagrange
;;     equations derived from these Lagrangians will in general look very different
;;     from one another, but they must be equivalent.

;;     *Exercise 1.15: Equivalence*

;;     Show by direct calculation that the Lagrange equations for /L/′ are
;;     satisfied if the Lagrange equations for /L/ are satisfied.

;;     Given a coordinate transformation /F/, we can use
;;     ([[file:chapter001.html#disp_1.77][1.77]]) to find the function /C/ that
;;     transforms local tuples. The procedure F->C implements this:[fn:63]

;;      #+begin_src scheme
;; (define ((F->C F) local) (up (time local) (F local)
;;     (+ (((partial 0) F) local) (* (((partial 1) F) local) (velocity local)))))

;;      #+end_src

;;     As an illustration, consider the transformation from polar to rectangular
;;     coordinates, /x/ = /r/ cos /φ/ and /y/ = /r/ sin /φ/, with the following
;;     implementation:

;;      #+begin_src scheme
;; (define (p->r local) (let ((polar-tuple (coordinate local))) (let ((r (ref
;; polar-tuple 0)) (phi (ref polar-tuple 1))) (let ((x
;;     (* r (cos phi))) (y (* r (sin phi)))) (up x y)))))
;;      #+end_src

;;     In terms of the polar coordinates and the rates of change of the polar
;;     coordinates, the rates of change of the rectangular components are[fn:64]



;;      #+begin_src scheme
;; (show-expression (velocity ((F->C p->r) (up 't (up 'r 'phi) (up 'rdot
;; 'phidot)))))
;;      #+end_src

;;     $$\begin{pmatrix} {- \overset{˙}{\varphi}r\sin\,(\varphi) +
;;     \overset{˙}{r}\cos\,(\varphi)} {\overset{˙}{\varphi}r\cos\,(\varphi) +
;;     \overset{˙}{r}\sin\,(\varphi)} \end{pmatrix}$$

;;     We can use F->C to find the Lagrangian for central force motion in polar
;;     coordinates from the Lagrangian in rectangular components, using equation
;;     ([[file:chapter001.html#disp_1.72][1.72]]):

;;         #+begin_src scheme
;; (define (L-central-polar m U) (compose
;;     (L-central-rectangular m U) (F->C p->r))) (show-expression ((L-central-polar
;;     'm (literal-function 'U)) (up 't (up 'r 'phi) (up 'rdot 'phidot)))) {%

;;         #+end_src

;;     $$\frac{1}{2}m{\overset{˙}{\varphi}}^{2}r^{2} +
;;     \frac{1}{2}m{\overset{˙}{r}}^{2} - U(r)$$

;;     The result is the same as Lagrangian
;;     ([[file:chapter001.html#disp_1.69][1.69]]).

;;     *Exercise 1.16: Central force motion*

;;     Find Lagrangians for central force motion in three dimensions in rectangular
;;     coordinates and in spherical coordinates. First, find the Lagrangians
;;     analytically, then check the results with the computer by generalizing the
;;     programs that we have presented.

;; ** Coriolis and centrifugal forces
;;    :PROPERTIES:
;;    :CUSTOM_ID: coriolis-and-centrifugal-forces
;;    :CLASS:    level2
;;    :END:
;;    The equations of motion of a free particle in a rotating coordinate system
;;    have additional terms. Consider a free particle moving in two dimensions. A
;;    Lagrangian is:

;;     #+begin_src scheme
;; (define ((L-free-rectangular m) local) (let ((vx (ref
;;    (velocities local) 0)) (vy (ref (velocities local) 1))) (* 1/2 m (+ (square
;; vx) (square vy)))))
;;     #+end_src

;;    The rotation will be easy to describe in polar coordinates, so we transform
;;    to polar coordinates:



;;     #+begin_src scheme
;; (define (L-free-polar m) (compose (L-free-rectangular m) (F->C p->r)))
;;     #+end_src

;;    Now we can make a simple time-dependent transformation to rotating
;;    coordinates, with rate of rotation Omega:

;;     #+begin_src scheme
;; (define ((F Omega) local) (let ((t (time local)) (r
;;    (ref (coordinates local) 0)) (theta (ref (coordinates local) 1))) (up r (+
;;    theta (* Omega t))))) (define (L-rotating-polar m Omega) (compose
;;    (L-free-polar m) (F->C (F Omega))))
;;     #+end_src

;;    Now let's transform back to rectangular coordinates:

;;     #+begin_src scheme
;; (define (L-rotating-rectangular m Omega) (compose
;;    (L-rotating-polar m Omega) (F->C r->p)))
;;     #+end_src

;;    The new Lagrangian, in the rotating rectangular coordinate system is:

;;     #+begin_src scheme
;; ((L-rotating-rectangular 'm 'Omega) (up 't (up 'x_r 'y_r) (up 'xdot_r
;; 'ydot_r))) (+ (* 1/2 (expt Omega 2) m (expt x_r 2)) (* 1/2
;;    (expt Omega 2) m (expt y_r 2)) (* -1 Omega m xdot_r y_r) (* Omega m ydot_r
;; x_r) (* 1/2 m (expt xdot_r 2)) (* 1/2 m (expt ydot_r 2)))
;;     #+end_src

;;    Although the transformation of coordinates is time dependent the resulting
;;    Lagrangian is independent of time.

;;    The Lagrange equations for the free particle in the rotating coordinate
;;    system have force terms involving the angular velocity Ω:

;;     #+begin_src scheme
;; (((Lagrange-equations (L-rotating-rectangular 'm 'Omega)) (up (literal-function
;; 'x r) (literal-function 'y r))) 't) (down (+
;;    (* -1 (expt Omega 2) m (x_r t)) (* -2 Omega m ((D y_r) t)) (* m (((expt D 2)
;;    x_r) t))) (+ (* -1 (expt Omega 2) m (y_r t)) (* 2 Omega m ((D x_r) t)) (* m
;;    (((expt D 2) y_r) t))))
;;     #+end_src



;;    The terms that are proportional to Ω^{2} are called /centrifugal force/
;;    terms, and the ones that are proportional to Ω are called /Coriolis/ force
;;    terms. Note that the centrifugal force terms are radial, pointing away from
;;    the center of rotation. These additional force terms are derived from the
;;    corresponding terms in the Lagrangian. The terms in the Lagrangian that are
;;    proportional to Ω^{2} can be thought of as the negation of a /centrifugal
;;    potential energy/.

;;    Because this is a free particle the velocity in the original unrotated
;;    coordinates is constant. In rotating coordinates, the Coriolis terms describe
;;    an acceleration that is perpendicular to the velocity, causing the trajectory
;;    to curve.

;; *** 1.6.2 Systems with Rigid Constraints
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-6-2
;;     :CLASS:    level3
;;     :END:
;;     We have found that /L/ = /T/ − /V/ is a suitable Lagrangian for a system of
;;     point particles subject to forces derived from a potential. Extended bodies
;;     can sometimes be conveniently idealized as a system of point particles
;;     connected by rigid constraints. We will find that /L/ = /T/ − /V/, expressed
;;     in irredundant coordinates, is also a suitable Lagrangian for modeling
;;     systems of point particles with rigid constraints. We will first illustrate
;;     the method and then provide a justification.

;; ** Lagrangians for rigidly constrained systems
;;    :PROPERTIES:
;;    :CUSTOM_ID: lagrangians-for-rigidly-constrained-systems
;;    :CLASS:    level2
;;    :END:
;;    The system is presumed to be made of /N/ point masses, indexed by /α/, in
;;    ordinary three-dimensional space. The first step is to choose a convenient
;;    set of irredundant generalized coordinates /q/ and redescribe the system in
;;    terms of these. In terms of the generalized coordinates the rectangular
;;    coordinates of particle /α/ are

;;    \begin{equation}
;;    {\mathbf{x}_{\alpha} = f_{\alpha}(t,q).} & {(1.79)}
;;    \end{equation}

;;    For irredundant coordinates /q/ all the coordinate constraints are built into
;;    the functions /f_{α}/. We deduce the relationship of the generalized
;;    velocities /v/ to the velocities of the constituent particles *v*_{/α/} by
;;    inserting path functions into equation
;;    ([[file:chapter001.html#disp_1.79][1.79]]), differentiating, and abstracting
;;    to arbitrary velocities (see [[file:chapter001.html#h3_1-6-1][section
;;    1.6.1]]). We find

;;    \begin{equation}
;;    {\mathbf{v}_{\alpha} = \partial_{0}f_{\alpha}(t,q) + \partial_{1}f_{\alpha}(t,q)v.} & {(1.80)}
;;    \end{equation}

;;    We use equations ([[file:chapter001.html#disp_1.79][1.79]]) and
;;    ([[file:chapter001.html#disp_1.80][1.80]]) to express the kinetic energy in
;;    terms of the generalized coordinates and velocities. Let

;;    $\widetilde{T}$

;;    be the kinetic energy as a function of the rectangular coordinates and
;;    velocities:

;;    \begin{equation}
;;    {\widetilde{T}(t;\mathbf{x}_{0},\ldots,\mathbf{x}_{N - 1};\mathbf{v}_{0},\ldots,\mathbf{v}_{N - 1}) = {\sum\limits_{\alpha}{\frac{1}{2}m_{\alpha}\mathbf{v}_{\alpha}^{2},}}} & {(1.81)}
;;    \end{equation}

;;    where

;;    $\mathbf{v}_{\alpha}^{2}$

;;    is the squared magnitude of *v*_{/α/}. As a function of the generalized
;;    coordinate tuple /q/ and the generalized velocity tuple /v/, the kinetic
;;    energy is

;;    $$\begin{array}{lll} {T(t,q,v)} & {=
;;    \widetilde{T}(t,f(t,q),\partial_{0}f(t,q) + \partial_{1}f(t,q)v)} & \\
;;    & {=
;;    {\sum\limits_{\alpha}{\frac{1}{2}m_{\alpha}{(\partial_{0}f_{\alpha}(t,q) +
;;    \partial_{1}f_{\alpha}(t,q)v)}^{2}.}}} & {(1.82)} \end{equation}

;;    Similarly, we use equation ([[file:chapter001.html#disp_1.79][1.79]]) to
;;    reexpress the potential energy in terms of the generalized coordinates. Let

;;    $\widetilde{V}(t,x)$

;;    be the potential energy at time /t/ in the configuration specified by the
;;    tuple of rectangular coordinates /x/. Expressed in generalized coordinates
;;    the potential energy is

;;    \begin{equation}
;;    {V(t,q,v) = \widetilde{V}(t,f(t,q)).} & {(1.83)}
;;    \end{equation}

;;    We take the Lagrangian to be the difference of the kinetic energy and the
;;    potential energy: /L/ = /T/ − /V/.

;; ** A pendulum driven at the pivot
;;    :PROPERTIES:
;;    :CUSTOM_ID: a-pendulum-driven-at-the-pivot
;;    :CLASS:    level2
;;    :END:
;;    Consider a pendulum (see [[file:chapter001.html#Fig_1-2][figure 1.2]]) of
;;    length /l/ and mass /m/, modeled as a point mass, supported by a pivot that
;;    is driven in the vertical direction by a given function of time /y_{s}/.

;;    The dimension of the configuration space for this system is one; we choose
;;    /θ/, shown in [[file:chapter001.html#Fig_1-2][figure 1.2]], as the
;;    generalized coordinate.

;;    The position of the bob is given, in rectangular coordinates, by

;;    $$\begin{array}{llll} {x = l\sin\theta} & \text{and} & {y = y_{s}(t) -
;;    l\cos\theta.} & {(1.84)} \end{equation}

;;    The velocities are

;;    $$\begin{array}{llll} {v_{x} = l\overset{˙}{\theta}\cos\theta} & \text{and} &
;;    {v_{y} = Dy_{s}(t) + l\overset{˙}{\theta}\sin\theta,} & {(1.85)}
;;    \end{equation}



;;    #+caption: *Figure 1.2* The pendulum is driven by vertical motion of the
;;    pivot. The pivot slides on the /y/-axis. Although the bob is drawn as a blob
;;    it is modeled as a point mass. The bob is acted on by the uniform
;;    acceleration /g/ of gravity in the negative /ŷ/ direction.
;;    [[file:images/Art_P138.jpg]]

;;    obtained by differentiating along a path and abstracting to velocities at the
;;    moment.

;;    The kinetic energy is

;;    $\widetilde{T}(t;x,y;v_{x},v_{y}) = \frac{1}{2}m(v_{x}^{2} + v_{y}^{2})$

;;    . Expressed in generalized coordinates the kinetic energy is

;;    \begin{equation}
;;    {T(t,\theta,\overset{˙}{\theta}) = \frac{1}{2}m(l^{2}{\overset{˙}{\theta}}^{2} + {(Dy_{s}(t))}^{2} + 2lDy_{s}(t)\overset{˙}{\theta}\sin\theta).} & {(1.86)}
;;    \end{equation}

;;    The potential energy is

;;    $\widetilde{V}(t;x,y) = mgy$

;;    . Expressed in generalized coordinates the potential energy is

;;    \begin{equation}
;;    {V(t,\theta,\overset{˙}{\theta}) = gm(y_{s}(t) - l\cos\theta).} & {(1.87)}
;;    \end{equation}

;;    A Lagrangian is /L/ = /T/ − /V/ :

;;    $$\begin{array}{lll} {L(t,\theta,\overset{˙}{\theta})} & {=
;;    \frac{1}{2}m(l^{2}{\overset{˙}{\theta}}^{2} + {(Dy_{s}(t))}^{2} + 2l\,
;;    Dy_{s}(t)\overset{˙}{\theta}\sin\theta)} & \\
;;    & {\text{   } - gm(y_{s}(t) - l\cos\theta).} & {(1.88)} \end{equation}



;;    The Lagrangian is expressed as

;;     #+begin_src scheme
;; (define ((T-pend m l g ys) local) (let ((t (time local)) (theta (coordinate
;; local)) (thetadot (velocity local))) (let ((vys (D ys))) (* 1/2 m (+ (square (*
;; l thetadot)) (square (vys t)) (* 2 l (vys t) thetadot (sin
;; theta))))))) (define ((V-pend m l g ys) local) (let ((t (time
;; local)) (theta (coordinate local))) (* m g (- (ys t) (* l (cos theta))))))
;;    (define L-pend (- T-pend V-pend))
;;     #+end_src

;;    Lagrange's equation for this system is

;;     #+begin_src scheme (show-expression (((Lagrange-equations (L-pend 'm 'l 'g
;; (literal-function 'y_s))) (literal-function 'theta)) 't)) #+end_src %}

;;    $$D^{2}\theta\,(t)\, l^{2}m + D^{2}y_{s}\,(t)\,\,\sin\,(\theta(t))lm +
;;    \sin\,(\theta(t))\, glm$$

;;    *Exercise 1.17: Bead on a helical wire*

;;    A bead of mass /m/ is constrained to move on a frictionless helical wire. The
;;    helix is oriented so that its axis is horizontal. The diameter of the helix
;;    is /d/ and its pitch (turns per unit length) is /h/. The system is in a
;;    uniform gravitational field with vertical acceleration /g/. Formulate a
;;    Lagrangian that describes the system and find the Lagrange equations of
;;    motion.

;;    *Exercise 1.18: Bead on a triaxial surface*

;;    A bead of mass /m/ moves without friction on a triaxial ellipsoidal surface.
;;    In rectangular coordinates the surface satisfies

;;    \begin{equation}
;;    {\frac{x^{2}}{a^{2}} + \frac{y^{2}}{b^{2}} + \frac{z^{2}}{c^{2}} = 1} & {(1.89)}
;;    \end{equation}

;;    for some constants /a/, /b/, and /c/. Identify suitable generalized
;;    coordinates, formulate a Lagrangian, and find Lagrange's equations.



;;    #+caption: *Figure 1.3* A two-bar linkage is modeled by three point
;;    masses connected by rigid massless struts. This linkage is subject to a
;;    uniform vertical gravitational acceleration. [[file:images/Art_P146.jpg]]

;;    #+caption: *Figure 1.4* This pendulum is pivoted on a point particle of
;;    mass /m/_{1} that is allowed to slide on a horizontal rail. The pendulum bob
;;    is a point particle of mass /m/_{2} that is acted on by the vertical force of
;;    gravity. [[file:images/Art_P147.jpg]]

;;    *Exercise 1.19: Two-bar linkage*

;;    The two-bar linkage shown in [[file:chapter001.html#Fig_1-3][figure 1.3]] is
;;    constrained to move in the plane. It is composed of three small massive
;;    bodies interconnected by two massless rigid rods in a uniform gravitational
;;    field with vertical acceleration /g/. The rods are pinned to the central body
;;    by a hinge that allows the linkage to fold. The system is arranged so that
;;    the hinge is completely free: the members can go through all configurations
;;    without collision. Formulate a Lagrangian that describes the system and find
;;    the Lagrange equations of motion. Use the computer to do this, because the
;;    equations are rather big.

;;    *Exercise 1.20: Sliding pendulum*

;;    Consider a pendulum of length /l/ attached to a support that is free to move
;;    horizontally, as shown in [[file:chapter001.html#Fig_1-4][figure 1.4]]. Let
;;    the mass of the support be /m/_{1} and the mass of the pendulum bob be
;;    /m/_{2}. Formulate a Lagrangian and derive Lagrange's equations for this
;;    system.



;; ** Why it works
;;    :PROPERTIES:
;;    :CUSTOM_ID: why-it-works
;;    :CLASS:    level2
;;    :END:
;;    In this section we show that /L/ = /T/ − /V/ is in fact a suitable Lagrangian
;;    for rigidly constrained systems. We do this by requiring that the Lagrange
;;    equations be equivalent to the Newtonian vectorial dynamics with vector
;;    constraint forces.[fn:65]

;;    We consider a system of particles. The particle with index /α/ has mass
;;    /m_{α}/ and position

;;    ${\overset{\rightarrow}{x}}_{\alpha}(t)$

;;    at time /t/. There may be a very large number of these particles, or just a
;;    few. Some of the positions may also be specified functions of time, such as
;;    the position of the pivot of a driven pendulum. There are rigid position
;;    constraints among some of the particles. We assume that all of these
;;    constraints are of the form

;;    \begin{equation}
;;    {({\overset{\rightarrow}{x}}_{\alpha}(t) - {\overset{\rightarrow}{x}}_{\beta}(t)) \cdot ({\overset{\rightarrow}{x}}_{\alpha}(t) - {\overset{\rightarrow}{x}}_{\beta}(t)) = l_{\alpha\beta}^{2};} & {(1.90)}
;;    \end{equation}

;;    that is, the distance between particles /α/ and /β/ is /l_{αβ}/.

;;    The Newtonian equation of motion for particle /α/ says that the mass times
;;    the acceleration of particle /α/ is equal to the sum of the potential forces
;;    and the constraint forces. The potential forces are derived as the negative
;;    gradient of the potential energy, and may depend on the positions of the
;;    other particles and the time. The constraint forces

;;    ${\overset{\rightarrow}{F}}_{\alpha\beta}$

;;    are the vector constraint forces associated with the rigid constraint between
;;    particle /α/ and particle /β/. So

;;    \begin{equation}
;;    {D(m_{\alpha}D{\overset{\rightarrow}{x}}_{\alpha})(t)} & \\
;;    {\text{       } = - {\overset{\rightarrow}{\nabla}}_{\overset{\rightarrow}{x}\alpha}\mathcal{V}(t,{\overset{\rightarrow}{x}}_{0}(t),\ldots,{\overset{\rightarrow}{x}}_{N - 1}(t)) + {\sum\limits_{\{\beta|\beta\leftrightarrow\alpha\}}{{\overset{\rightarrow}{F}}_{\alpha\beta}(t),}}} & {(1.91)}
;;    \end{equation}

;;    where in the summation /β/ ranges over only those particle indices for which
;;    there are rigid constraints with the particle indexed by /α/; we use the
;;    notation /β/ ↔ /α/ for the relation that there is a rigid constraint between
;;    the indicated particles.



;;    The force of constraint is directed along the line between the particles, so
;;    we may write

;;    \begin{equation}
;;    {{\overset{\rightarrow}{F}}_{\alpha\beta}(t) = F_{\alpha\beta}(t)\frac{{\overset{\rightarrow}{x}}_{\beta}(t) - {\overset{\rightarrow}{x}}_{\alpha}(t)}{l_{\alpha\beta}}} & {(1.92)}
;;    \end{equation}

;;    where /F_{αβ}/(/t/) is the scalar magnitude of the tension in the constraint
;;    at time /t/. Note that

;;    ${\overset{\rightarrow}{F}}_{\alpha\beta} = -
;;    {\overset{\rightarrow}{F}}_{\beta\alpha}$

;;    . In general, the scalar constraint forces change as the system evolves.

;;    Formally, we can reproduce Newton's equations with the Lagrangian[fn:66]

;;    $$\begin{array}{lll} {L(t;x,F;\overset{˙}{x},\overset{˙}{F}) =} &
;;    {\sum\limits_{\alpha}{\frac{1}{2}m_{\alpha}\mathbf{\overset{˙}{x}}_{\alpha}^{2} -
;;    V(t,x)}} & \\
;;    & {- {\sum\limits_{\{\alpha,\beta|\alpha <
;;    \beta,\alpha\leftrightarrow\beta\}}{\frac{F_{\alpha\beta}}{2l_{\alpha\beta}}\lbrack{(\mathbf{x}_{\beta} -
;;    \mathbf{x}_{\alpha})}^{2} - l_{\alpha\beta}^{2}\rbrack}}} & {(1.93)}
;;    \end{equation}

;;    where the constraint forces are being treated as additional generalized
;;    coordinates. Here /x/ is a structure composed of all the rectangular
;;    components *x*_{/α/} of all the

;;    ${\overset{\rightarrow}{x}}_{\alpha},\overset{˙}{x}$

;;    is a structure composed of all the rectangular components

;;    $\mathbf{\overset{˙}{x}}_{\alpha}$

;;    of all the velocity vectors

;;    ${\overset{\rightarrow}{v}}_{\alpha}$

;;    , and /F/ is a structure composed of all the /F_{αβ}/. The velocity of /F/
;;    does not appear in the Lagrangian, and /F/ itself appears only linearly. So
;;    the Lagrange equations associated with /F/ are

;;    \begin{equation}
;;    {{(\mathbf{x}_{\beta}(t) - \mathbf{x}_{\alpha}(t))}^{2} - l_{\alpha\beta}^{2} = 0} & {(1.94)}
;;    \end{equation}

;;    but this is just a restatement of the constraints. The Lagrange equations for
;;    the coordinates of the particles are Newton's equations
;;    ([[file:chapter001.html#disp_1.91][1.91]])

;;    $$\begin{array}{lll} {D(mD\mathbf{x}_{\alpha})(t)} & {= -
;;    \partial_{1,\alpha}V(t,x(t))} & \\
;;    & {\,\,\,\,\,\,\,\, +
;;    {\sum\limits_{\{\beta|\alpha\leftrightarrow\beta\}}{F_{\alpha\beta}(t)\frac{\mathbf{x}_{\beta}(t) -
;;    \mathbf{x}_{\alpha}(t)}{l_{\alpha\beta}}}}.} & {(1.95)} \end{equation}



;;    Now that we have a suitable Lagrangian, we can use the fact that Lagrangians
;;    can be reexpressed in any generalized coordinates to find a simpler
;;    Lagrangian. The strategy is to choose a new set of coordinates for which many
;;    of the coordinates are constants and the remaining coordinates are
;;    irredundant.

;;    Let /q/ be a tuple of generalized coordinates that specify the degrees of
;;    freedom of the system without redundancy. Let /c/ be a tuple of other
;;    generalized coordinates that specify the distances between particles for
;;    which constraints are specified. The /c/ coordinates will have constant
;;    values. The combination of /q/ and /c/ replaces the redundant rectangular
;;    coordinates /x/.[fn:67] In addition, we still have the /F/ coordinates, which
;;    are the scalar constraint forces. Our new coordinates are the components of
;;    /q/, /c/, and /F/.

;;    There exist functions /f_{α}/ that give the rectangular coordinates of the
;;    constituent particles in terms of /q/ and /c/:

;;    \begin{equation}
;;    {\mathbf{x}_{\alpha} = f_{\alpha}(t,q,c).} & {(1.96)}
;;    \end{equation}

;;    To reexpress the Lagrangian in terms of /q/, /c/, and /F/, we need to find
;;    *v*_{/α/} in terms of the generalized velocities

;;    q˙

;;    and

;;    c˙

;;    : we do this by differentiating /f_{α}/ along a path and abstracting to
;;    arbitrary velocities (see [[file:chapter001.html#h3_1-6-1][section 1.6.1]]):

;;    \begin{equation}
;;    {\mathbf{v}_{\alpha} = \partial_{0}f_{\alpha}(t,q,c) + \partial_{1}f_{\alpha}(t,q,c)\,\,\overset{˙}{q} + \partial_{2}f_{\alpha}(t,q,c)\,\,\overset{˙}{c}.} & {(1.97)}
;;    \end{equation}

;;    Substituting these into Lagrangian
;;    ([[file:chapter001.html#disp_1.93][1.93]]), and using

;;    \begin{equation}
;;    {c_{\alpha\beta}^{2} = {(\mathbf{x}_{\beta} - \mathbf{x}_{\alpha})}^{2},} & {(1.98)}
;;    \end{equation}

;;    we find

;;    \begin{equation}
;;    {L\prime(t;q,c,F;\overset{˙}{q},\overset{˙}{c},\overset{˙}{F})} & \\
;;    {\,\,\,\,\,\,\,\,\,\, = {\sum\limits_{\alpha}{\frac{1}{2}m_{\alpha}{(\partial_{0}f_{\alpha}(t,q,c) + \partial_{1}f_{\alpha}(t,q,c)\,\,\overset{˙}{q} + \partial_{2}f_{\alpha}(t,q,c)\,\,\overset{˙}{c})}^{2}}}} & \\
;;    {\,\,\,\,\,\,\,\,\,\,\,\,\,\,\,\,\,\, - V(t,f(t,q,c)) - {\sum\limits_{\{\alpha,\beta|\alpha < \beta,\alpha\leftrightarrow\beta\}}{\frac{F_{\alpha\beta}}{2l_{\alpha\beta}}\lbrack c_{\alpha\beta}^{2} - l_{\alpha\beta}^{2}\rbrack.}}} & {(1.99)}
;;    \end{equation}



;;    The Lagrange equations are derived by the usual procedure. Rather than write
;;    out all the gory details, let's think about how it will go.

;;    The Lagrange equations associated with /F/ just restate the constraints:

;;    \begin{equation}
;;    {0 = c_{\alpha\beta}^{2}(t) - l_{\alpha\beta}^{2}} & {(1.100)}
;;    \end{equation}

;;    and consequently we know that along a solution path, /c/(/t/) = /l/ and
;;    /Dc/(/t/) = /D/^{2}/c/(/t/) = 0. We can use this result to simplify the
;;    Lagrange equations associated with /q/ and /c/.

;;    The Lagrange equations associated with /q/ are the same as if they were
;;    derived from the Lagrangian[fn:68]

;;    $$\begin{array}{lll} {L''(t,q,\overset{˙}{q})} & {=
;;    {\sum\limits_{\alpha}{\frac{1}{2}m_{\alpha}{(\partial_{0}f_{\alpha}(t,q,l) +
;;    \partial_{1}f_{\alpha}(t,q,l)\,\,\overset{˙}{q})}^{2}}}} & \\
;;    & {\,\,\, - V(t,f(t,q,l)),} & {(1.101)} \end{equation}

;;    but this is exactly /T/ − /V/ where /T/ and /V/ are computed from the
;;    generalized coordinates /q/, with fixed constraints. Notice that the
;;    constraint forces do not appear in the Lagrange equations for /q/ because in
;;    the Lagrange equations they are multiplied by a term that is identically zero
;;    on the solution paths. So the Lagrange equations for /T/ − /V/ with
;;    irredundant generalized coordinates /q/ and fixed constraints are equivalent
;;    to Newton's equations with vector constraint forces.

;;    The Lagrange equations for /c/ can be used to find the constraint forces. The
;;    Lagrange equations are a big mess so we will not show them explicitly, but in
;;    general they are equations in /D/^{2}/c/, /Dc/, and /c/ that will depend upon
;;    /q/, /Dq/, and /F/. The dependence on /F/ is linear, so we can solve for /F/
;;    in terms of the solution path /q/ and /Dq/, with /c/ = /l/ and /Dc/ =
;;    /D/^{2}/c/ = 0.

;;    If we are not interested in the constraint forces, we can abandon the full
;;    Lagrangian ([[file:chapter001.html#disp_1.99][1.99]]) in favor of Lagrangian
;;    ([[file:chapter001.html#disp_1.101][1.101]]), which is equivalent as far as
;;    the evolution of the generalized coordinates /q/ is concerned.

;;    #+caption: *Figure 1.5* A rigid rod of length /l/ constrains two massive
;;    particles in a plane. [[file:images/Art_P166.jpg]]

;;    The same derivation goes through even if the lengths /l_{αβ}/ specified in
;;    the interparticle distance constraints are a function of time. It can also be
;;    generalized to allow distance constraints to time-dependent positions, by
;;    making some of the positions of particles

;;    ${\overset{\rightarrow}{x}}_{\beta}$

;;    be specified functions of time.

;;    *Exercise 1.21: A dumbbell*

;;    In this exercise we will recapitulate the derivation of the Lagrangian for
;;    constrained systems for a particular simple system.

;;    Consider two massive particles in the plane constrained by a massless rigid
;;    rod to remain a distance /l/ apart, as in
;;    [[file:chapter001.html#Fig_1-5][figure 1.5]]. There are apparently four
;;    degrees of freedom for two massive particles in the plane, but the rigid rod
;;    reduces this number to three.

;;    We can uniquely specify the configuration with the redundant coordinates of
;;    the particles, say /x/_{0}(/t/), /y/_{0}(/t/) and /x/_{1}(/t/), /y/_{1}(/t/).
;;    The constraint (/x/_{1}(/t/) − /x/_{0}(/t/))^{2} + (/y/_{1}(/t/) −
;;    /y/_{0}(/t/))^{2} = /l/^{2} eliminates one degree of freedom.

;;    *a.* Write Newton's equations for the balance of forces for the four
;;    rectangular coordinates of the two particles, given that the scalar tension
;;    in the rod is /F/.

;;    *b.* Write the formal Lagrangian

;;    $L(t;x_{0},y_{0},x_{1},y_{1},F;{\overset{˙}{x}}_{0},{\overset{˙}{y}}_{0},{\overset{˙}{x}}_{1},{\overset{˙}{y}}_{1},\overset{˙}{F})$

;;    such that Lagrange's equations will yield the Newton's equations you derived
;;    in part *a*.

;;    *c.* Make a change of coordinates to a coordinate system with center of mass
;;    coordinates /x/_{CM}, /y/_{CM}, angle /θ/, distance between the particles
;;    /c/, and tension force /F/. Write the Lagrangian in these coordinates, and
;;    write the Lagrange equations.



;;    *d.* You may deduce from one of these equations that /c/(/t/) = /l/. From
;;    this fact we get that /Dc/ = 0 and /D/^{2}/c/ = 0. Substitute these into the
;;    Lagrange equations you just computed to get the equation of motion for
;;    /x/_{CM}, /y/_{CM}, /θ/.

;;    *e.* Make a Lagrangian (= /T/ − /V/) for the system described with the
;;    irredundant generalized coordinates /x/_{CM}, /y/_{CM}, /θ/ and compute the
;;    Lagrange equations from this Lagrangian. They should be the same equations as
;;    you derived for the same coordinates in part *d*.

;;    *Exercise 1.22: Driven pendulum*

;;    Show that the Lagrangian ([[file:chapter001.html#disp_1.93][1.93]]) can be
;;    used to describe the driven pendulum
;;    ([[file:chapter001.html#h3_1-6-2][section 1.6.2]]), where the position of the
;;    pivot is a specified function of time: Derive the equations of motion using
;;    the Newtonian constraint force prescription, and show that they are the same
;;    as the Lagrange equations. Be sure to examine the equations for the
;;    constraint forces as well as the position of the pendulum bob.

;;    *Exercise 1.23: Fill in the details*

;;    Show that the Lagrange equations for Lagrangian
;;    ([[file:chapter001.html#disp_1.101][1.101]]) are the same as the Lagrange
;;    equations for Lagrangian ([[file:chapter001.html#disp_1.99][1.99]]) with the
;;    substitution /c/(/t/) = /l/, /Dc/(/t/) = /D/^{2}/c/(/t/) = 0.

;;    *Exercise 1.24: Constraint forces*

;;    Find the tension in an undriven planar pendulum.

;; *** 1.6.3 Constraints as Coordinate Transformations
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-6-3
;;     :CLASS:    level3
;;     :END:
;;     The derivation of a Lagrangian for a constrained system involves steps that
;;     are analogous to those in the derivation of a coordinate transformation.

;;     We can make a Lagrangian for the unconstrained system of particles in
;;     rectangular coordinates. In general there will be more coordinates than real
;;     degrees of freedom; the constraints will eliminate the redundancy. We then
;;     choose a convenient set of irredundant generalized coordinates that
;;     incorporate the constraints to describe our system. We express the redundant
;;     rectangular coordinates and velocities in terms of the irredundant
;;     generalized coordinates and generalized velocities, and we use these
;;     transformations to reexpress the Lagrangian in the generalized coordinates.

;;     To carry out a coordinate transformation we specify how the configuration of
;;     a system expressed in one set of generalized coordinates can be reexpressed
;;     in terms of another set of generalized coordinates. We then determine the
;;     transformation of generalized velocities implied by the transformation of
;;     generalized coordinates. A Lagrangian that is expressed in terms of one of
;;     the sets of generalized coordinates can then be reexpressed in terms of the
;;     other set of generalized coordinates.

;;     These are really two applications of the same process, so we can make
;;     Lagrangians for constrained systems by composing a Lagrangian for
;;     unconstrained particles with a coordinate transformation that incorporates
;;     the constraint. Our deduction that /L/ = /T/ − /V/ is a suitable Lagrangian
;;     for a constrained systems was in fact based on a coordinate transformation
;;     from a set of coordinates subject to constraints to a set of irredundant
;;     coordinates plus constraint coordinates that are constant.

;;     Let *x*_{/α/} be the tuple of rectangular components of the constituent
;;     particle with index /α/, and let *v*_{/α/} be its velocity. The Lagrangian

;;     \begin{equation}
;;     {L_{f}(t;\mathbf{x}_{0},\ldots,\mathbf{x}_{N - 1};\mathbf{v}_{0},\ldots,\mathbf{v}_{N - 1})} & \\
;;     {\,\,\,\,\,\,\, = {\sum\limits_{\alpha}{\frac{1}{2}m_{\alpha}\mathbf{v}_{\alpha}^{2} - V(t;\mathbf{x}_{0},\ldots,\mathbf{x}_{N - 1};\mathbf{v}_{0},\ldots,\mathbf{v}_{N - 1})}}} & {(1.102)}
;;     \end{equation}

;;     is the difference of kinetic and potential energies of the constituent
;;     particles. This is a suitable Lagrangian for a set of unconstrained free
;;     particles with potential energy /V/.

;;     Let /q/ be a tuple of irredundant generalized coordinates and /v/ be the
;;     corresponding generalized velocity tuple. The coordinates /q/ are related to
;;     *x*_{/α/}, the coordinates of the constituent particles, by *x*_{/α/} =
;;     /f_{α}/(/t/, /q/), as before. The constraints among the constituent
;;     particles are taken into account in the definition of the /f_{α}/. Here we
;;     view this as a coordinate transformation. What is unusual about this as a
;;     coordinate transformation is that the dimension of /x/ is not the same as
;;     the dimension of /q/. From this coordinate transformation we can find the
;;     local-tuple transformation function (see
;;     [[file:chapter001.html#h3_1-6-1][section 1.6.1]])

;;     \begin{equation}
;;     {(t;\mathbf{x}_{0},\ldots,\mathbf{x}_{N - 1};\mathbf{v}_{0},\ldots,\mathbf{v}_{N - 1}) = C(t,q,v).} & {(1.103)}
;;     \end{equation}

;;     A Lagrangian for the constrained system can be obtained from the Lagrangian
;;     for the unconstrained system by composing it with the local-tuple
;;     transformation function from constrained coordinates to unconstrained
;;     coordinates:

;;     \begin{equation}
;;     {L = L_{f} \circ C.} & {(1.104)}
;;     \end{equation}

;;     The constraints enter only in the transformation.

;;     To illustrate this we will find a Lagrangian for the driven pendulum
;;     introduced in [[file:chapter001.html#h3_1-6-2][section 1.6.2]]. As we saw on
;;     [[file:chapter001.html#p40][page 40]], the /T/ − /V/ Lagrangian for a free
;;     particle of mass /m/ in a vertical plane subject to a gravitational
;;     potential with acceleration /g/ is

;;     \begin{equation}
;;     {L_{f}\left( {t;x,y;v_{x},v_{y}} \right) = \frac{1}{2}m(v_{x}^{2} + v_{y}^{2}) - mgy,} & {(1.105)}
;;     \end{equation}

;;     where /y/ measures the height of the point mass. A program that computes
;;     this Lagrangian is

;;      #+begin_src scheme
;; (define ((L-uniform-acceleration m g) local) (let ((q
;;     (coordinate local)) (v (velocity local))) (let ((y (ref q 1))) (- (* 1/2 m
;;     (square v)) (* m g y)))))
;;      #+end_src

;;     The coordinate transformation from generalized coordinate /θ/ to rectangular
;;     coordinates is /x/ = /l/ sin /θ/, /y/ = /y_{s}/(/t/) − /l/ cos /θ/, where
;;     /l/ is the length of the pendulum and /y_{s}/ gives the height of the
;;     support as a function of time. It is interesting that the drive enters only
;;     through the specification of the constraints. A program implementing this
;;     coordinate transformation is

;;      #+begin_src scheme
;; (define ((dp-coordinates l y_s) local) (let ((t (time local)) (theta (coordinate
;; local))) (let ((x (* l (sin theta))) (y (- (y_s t) (* l (cos theta))))) (up x
;; y))))
;;      #+end_src

;;     Using F->C we can deduce the local-tuple transformation and define the
;;     Lagrangian for the driven pendulum by composition:

;;      #+begin_src scheme
;; (define (L-pend m l g y_s) (compose
;;     (L-uniform-acceleration m g) (F->C (dp-coordinates l y_s))))
;;      ,#+end_src  %}



;;     The Lagrangian is

;;      ,#+begin_src scheme
;; (show-expression ((L-pend 'm 'l 'g (literal-function 'y_s)) (up 't 'theta
;; 'thetadot)))
;;      #+end_src

;;     $$glm\,\cos\,(\theta) - gmy_{s}\,(t) +
;;     \frac{1}{2}l^{2}m{\overset{˙}{\theta}}^{2} +
;;     lm\overset{˙}{\theta}Dy_{s}\,(t)\sin\left( \theta \right) +
;;     \frac{1}{2}m{(Dy_{s}(t))}^{2}$$

;;     This is the same as the Lagrangian of equation
;;     ([[file:chapter001.html#disp_1.88][1.88]]) on
;;     [[file:chapter001.html#p51][page 51]].

;;     We have found a very interesting decomposition of the Lagrangian for
;;     constrained systems. One part consists of the difference of the kinetic and
;;     potential energy of the constituents. The other part describes the
;;     constraints that are specific to the configuration of a particular system.

;;     *Exercise 1.25: Foucault pendulum Lagrangian*

;;     A Foucault pendulum is a long-period pendulum of length /l/ and mass /m/
;;     that is suspended at a height /l/ above the surface of the Earth (radius
;;     /R/) at colatitude /ϕ/. If the pendulum is released, at rest, with nonzero
;;     displacement from the local vertical, it will oscillate in an apparent
;;     plane. However, the apparent plane of oscillation precesses as the Earth
;;     rotates. The Earth rotates with angular speed Ω.

;;     One way to specify the position of the bob is to erect a Foucault pendulum
;;     at the North Pole and rotate it to a point on the surface of the Earth at
;;     the appropriate colatitude and a fixed longitude. Because the Earth is
;;     rotating this is a time-varying transformation. There are two parts of this
;;     transformation.

;;     First, we relate the generalized coordinates /θ/ and /λ/ to the coordinates
;;     of the pendulum bob with the pendulum at the North pole. Let /θ/ be the
;;     angle of the bob relative to the line through the center of the Earth and
;;     let /λ/ be the precession angle. The rectangular coordinates of the bob for
;;     a pendulum at the North Pole are:

;;     /x/_{0} = /l/ sin /θ/ cos /λ/

;;     /y/_{0} = /l/ sin /θ/ sin /λ/

;;     /z/_{0} = (/R/ + /l/) − /l/ cos /θ/.

;;     Next, we rotate the pendulum to its actual location at colatitude /ϕ/. We
;;     can choose the longitude to be zero, so the angular position of the support
;;     of the bob is rotated by Ω/t/. The transformation of coordinates is:[fn:69]

;;     $(x,y,z) = R_{z}(\Omega t)R_{y}(\phi)(x_{0},y_{0},z_{0}).$



;;     This second transformation can be implemented with the following code:

;;      #+begin_src scheme
;; ((compose (Rz (* 'Omega 't)) (Ry 'phi)) (up 'x_0 'y_0 'z_0)) (up (+ (* x_0 (cos
;; phi) (cos (* Omega t))) (* z_0 (sin phi) (cos (* Omega t))) (* -1 y_0 (sin (*
;; Omega t)))) (+ (* x_0 (cos phi) (sin (* Omega t))) (* z_0 (sin phi) (sin (*
;; Omega t))) (* y_0 (cos (* Omega t)))) (+ (* -1 x_0 (sin phi)) (* z_0 (cos
;; phi))))
;;      #+end_src

;;     Construct a coordinate transformation F from these parts that you can use
;;     with F->C to compose with the free Lagrangian for a particle in a
;;     gravitational potential to make a Lagrangian for the Foucault pendulum. The
;;     Newtonian potential energy is −/GMm/r/, where /r/ is the distance of the bob
;;     from the center of the Earth, and /M/ is the mass of the Earth.

;; *** 1.6.4 The Lagrangian Is Not Unique
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-6-4
;;     :CLASS:    level3
;;     :END:
;;     Lagrangians are not in a one-to-one relationship with physical
;;     systems---many Lagrangians can be used to describe the same physical system.
;;     In this section we will demonstrate this by showing that the addition to the
;;     Lagrangian of a “total time derivative” of a function of the coordinates and
;;     time does not change the paths of stationary action or the equations of
;;     motion deduced from the action principle.

;; ** Total time derivatives
;;    :PROPERTIES:
;;    :CUSTOM_ID: total-time-derivatives
;;    :CLASS:    level2
;;    :END:
;;    Let's first explain what we mean by a “total time derivative.” Let /F/ be a
;;    function of time and coordinates. The function /F/ on the path at time /t/ is

;;    \begin{equation}
;;    {(F \circ \Gamma\lbrack q\rbrack)(t) = F(t,q(t)).} & {(1.106)}
;;    \end{equation}

;;    So, by the chain rule

;;    \begin{equation}
;;    {D(F \circ \Gamma\lbrack q\rbrack)(t) = \partial_{0}F(t,q(t)) + \partial_{1}F(t,q(t))Dq(t).} & {(1.107)}
;;    \end{equation}

;;    More formally, the time derivative of /F/ along a path /q/ is

;;    \begin{equation}
;;    {D(F \circ \Gamma\lbrack q\rbrack) = (DF \circ \Gamma\lbrack q\rbrack)\, D\Gamma\lbrack q\rbrack.} & {(1.108)}
;;    \end{equation}

;;    Because /F/ depends only on time and coordinates, we have

;;    \begin{equation}
;;    {DF \circ \Gamma\lbrack q\rbrack = \lbrack\partial_{0}F \circ \Gamma\lbrack q\rbrack,\partial_{1}F \circ \Gamma\lbrack q\rbrack\rbrack.} & {(1.109)}
;;    \end{equation}



;;    So we need only the first two components of /D/Γ[/q/],

;;    \begin{equation}
;;    {(D\Gamma\lbrack q\rbrack)(t) = (1,Dq(t),D^{2}q(t),\ldots),} & {(1.110)}
;;    \end{equation}

;;    to form the product

;;    $$\begin{array}{lll} {D(F \circ \Gamma\lbrack q\rbrack)} & {= \partial_{0}F
;;    \circ \Gamma\lbrack q\rbrack + (\partial_{1}F \circ \Gamma\lbrack
;;    q\rbrack)Dq} & \\
;;    & {= (\partial_{0}F + (\partial_{1}F)\overset{˙}{Q}) \circ \Gamma\lbrack
;;    q\rbrack,} & {(1.111)} \end{equation}

;;    where

;;    $\overset{˙}{Q} = I_{2}$

;;    is a selector function:[fn:70]

;;    $c = \overset{˙}{Q}(a,b,c)$

;;    , so

;;    $Dq = \overset{˙}{Q} \circ \Gamma\lbrack q\rbrack$

;;    .

;;    The function

;;    \begin{equation}
;;    {D_{t}F = \partial_{0}F + (\partial_{1}F)\overset{˙}{Q}} & {(1.112)}
;;    \end{equation}

;;    is called the /total time derivative/ of /F/ ; it is a function of three
;;    arguments: the time, the generalized coordinates, and the generalized
;;    velocities.

;;    In general, the total time derivative of a local-tuple function /F/ is that
;;    function /D_{t}F/ that when composed with a local-tuple path is the time
;;    derivative of the composition of the function /F/ with the same local-tuple
;;    path:

;;    \begin{equation}
;;    {D_{t}F \circ \Gamma\lbrack q\rbrack = D(F \circ \Gamma\lbrack q\rbrack).} & {(1.113)}
;;    \end{equation}

;;    The total time derivative /D_{t}F/ is explicitly given by

;;    $$\begin{array}{lll} {D_{t}F(t,q,v,a,\ldots) =} &
;;    {\partial_{0}F(t,q,v,a,\ldots)} & \\
;;    & {+ \partial_{1}F(t,q,v,a,\ldots)v} & \\
;;    & {+ \partial_{2}F(t,q,v,a,\ldots)a + \cdots,} & {(1.114)} \end{equation}

;;    where we take as many terms as needed to exhaust the arguments of /F/.

;;    *Exercise 1.26: Properties of* /D_{t}/

;;    The total time derivative /D_{t}F/ is not the derivative of the function /F/.
;;    Nevertheless, the total time derivative shares many properties with the
;;    derivative. Demonstrate that /D_{t}/ has the following properties for
;;    local-tuple functions /F/ and /G/, number /c/, and a function /H/ with domain
;;    containing the range of /G/.



;;    *a.* /D_{t}/(/F/ + /G/) = /D_{t}F/ + /D_{t}G/

;;    *b.* /D_{t}/(/cF/) = /cD_{t}F/

;;    *c.* /D_{t}/(/FG/) = /FD_{t}G/ + (/D_{t}F/)/G/

;;    *d.* /D_{t}/(/H/ ∘ /G/) = (/DH/ ∘ /G/)/D_{t}G/

;; ** Adding total time derivatives to Lagrangians
;;    :PROPERTIES:
;;    :CUSTOM_ID: adding-total-time-derivatives-to-lagrangians
;;    :CLASS:    level2
;;    :END:
;;    Consider two Lagrangians /L/ and /L/′ that differ by the addition of a total
;;    time derivative of a function /F/ that depends only on the time and the
;;    coordinates

;;    \begin{equation}
;;    {L\prime = L + D_{t}F.} & {(1.115)}
;;    \end{equation}

;;    The corresponding action integral is

;;    $$\begin{array}{lll} {S\prime\lbrack q\rbrack(t_{1},t_{2})} & {=
;;    {\int_{t_{1}}^{t_{2}}{L\prime \circ \Gamma\lbrack q\rbrack}}} & \\
;;    & {= {\int_{t_{1}}^{t_{2}}{(L + D_{t}F) \circ \Gamma\lbrack q\rbrack}}} & \\
;;    & {= {\int_{t_{1}}^{t_{2}}{L \circ \Gamma\lbrack q\rbrack +
;;    {\int_{t_{1}}^{t_{2}}{D(F \circ \Gamma\lbrack q\rbrack)}}}}} & \\
;;    & {= S\lbrack q\rbrack(t_{1},t_{2}) + (F \circ \Gamma\lbrack
;;    q\rbrack)|_{t_{1}}^{t_{2}}.} & {(1.116)} \end{equation}

;;    The variational principle states that the action integral along a realizable
;;    trajectory is stationary with respect to variations of the trajectory that
;;    leave the configuration at the endpoints fixed. The action integrals
;;    /S/[/q/](/t/_{1}, /t/_{2}) and /S/′[/q/](/t/_{1}, /t/_{2}) differ by a term

;;    \begin{equation}
;;    {(F \circ \Gamma\lbrack q\rbrack)|_{t_{1}}^{t_{2}} = F(t_{2},q(t_{2})) - F(t_{1},q(t_{1}))} & {(1.117)}
;;    \end{equation}

;;    that depends only on the coordinates and time at the endpoints and these are
;;    not allowed to vary. Thus, if /S/[/q/](/t/_{1}, /t/_{2}) is stationary for a
;;    path, then /S/′[/q/](/t/_{1}, /t/_{2}) will also be stationary. So either
;;    Lagrangian can be used to distinguish the realizable paths.

;;    The addition of a total time derivative to a Lagrangian does not affect
;;    whether the action is stationary for a given path. So if we have two
;;    Lagrangians that differ by a total time derivative, the corresponding
;;    Lagrange equations are equivalent in that the same paths satisfy each.
;;    Moreover, the additional terms introduced into the action by the total time
;;    derivative appear only in the endpoint condition and thus do not affect the
;;    Lagrange equations derived from the variation of the action, so the Lagrange
;;    equations are the same. The Lagrange equations are not changed by the
;;    addition of a total time derivative to a Lagrangian.

;;    *Exercise 1.27: Lagrange equations for total time derivatives*

;;    Let /F/ (/t/, /q/) be a function of /t/ and /q/ only, with total time
;;    derivative

;;    \begin{equation}
;;    {D_{t}F = \partial_{0}F + (\partial_{1}F)\overset{˙}{Q}.} & {(1.118)}
;;    \end{equation}

;;    Show explicitly that the Lagrange equations for /D_{t}F/ are identically
;;    zero, and thus that the addition of /D_{t}F/ to a Lagrangian does not affect
;;    the Lagrange equations.

;;    The driven pendulum provides a nice illustration of adding total time
;;    derivatives to Lagrangians. The equation of motion for the driven pendulum
;;    (see [[file:chapter001.html#h3_1-6-2][section 1.6.2]]),

;;    \begin{equation}
;;    {ml^{2}D^{2}\theta(t) + ml(g + D^{2}y_{s}(t))\,\sin\theta(t) = 0,} & {(1.119)}
;;    \end{equation}

;;    has an interesting and suggestive interpretation: it is the same as the
;;    equation of motion of an undriven pendulum, except that the acceleration of
;;    gravity /g/ is augmented by the acceleration of the pivot /D/^{2}/y_{s}/.
;;    This intuitive interpretation was not apparent in the Lagrangian derived as
;;    the difference of the kinetic and potential energies in
;;    [[file:chapter001.html#h3_1-6-2][section 1.6.2]]. However, we can write an
;;    alternate Lagrangian with the same equation of motion that is as easy to
;;    interpret as the equation of motion:

;;    \begin{equation}
;;    {L\prime(t,\theta,\overset{˙}{\theta}) = \frac{1}{2}ml^{2}{\overset{˙}{\theta}}^{2} + ml(g + D^{2}y_{s}(t))\,\cos\theta.} & {(1.120)}
;;    \end{equation}

;;    With this Lagrangian it is apparent that the effect of the accelerating pivot
;;    is to modify the acceleration of gravity. Note, however, that it is not the
;;    difference of the kinetic and potential energies. Let's compare the two
;;    Lagrangians for the driven pendulum. The difference Δ/L/ = /L/ − /L/′ is

;;    $$\begin{array}{lll} {\Delta L(t,\theta,\overset{˙}{\theta}) =} &
;;    {\frac{1}{2}m{(Dy_{s}(t))}^{2} + mlDy_{s}(t)\overset{˙}{\theta}\,\sin\theta}
;;    & \\
;;    & {- gmy_{s}(t) - mlD^{2}y_{s}(t)\,\cos\theta.} & {(1.121)} \end{equation}

;;    The two terms in Δ/L/ that depend on neither /θ/ nor

;;    $\overset{˙}{\theta}$

;;    do not affect the equations of motion. The remaining two terms are the total
;;    time derivative of the function /F/ (/t/, /θ/) = −/mlDy_{s}/(/t/) cos /θ/,
;;    which does not depend on

;;    $\overset{˙}{\theta}$

;;    . The addition of such terms to a Lagrangian does not affect the equations of
;;    motion.

;; ** Properties of total time derivatives
;;    :PROPERTIES:
;;    :CUSTOM_ID: properties-of-total-time-derivatives
;;    :CLASS:    level2
;;    :END:
;;    If the local-tuple function /G/, with arguments (/t/, /q/, /v/), is the total
;;    time derivative of a function /F/, with arguments (/t/, /q/), then /G/ must
;;    have certain properties.

;;    From equation ([[file:chapter001.html#disp_1.112][1.112]]), we see that /G/
;;    must be linear in the generalized velocities

;;    \begin{equation}
;;    {G(t,q,v) = G_{0}(t,q,v) + G_{1}(t,q,v)\, v} & {(1.122)}
;;    \end{equation}

;;    where neither /G/_{1} nor /G/_{0} depends on the generalized velocities:
;;    ∂_{2}/G/_{1} = ∂_{2}/G/_{0} = 0.

;;    If /G/ is the total time derivative of /F/ then /G/_{1} = ∂_{1}/F/ and
;;    /G/_{0} = ∂_{0}/F/, so

;;    \begin{equation}
;;    {\partial_{0}G_{1} = \partial_{0}\partial_{1}F} & \\
;;    {\partial_{1}G_{0} = \partial_{1}\partial_{0}F.} & {(1.123)}
;;    \end{equation}

;;    The partial derivative with respect to the time argument does not have
;;    structure, so ∂_{0}∂_{1}/F/ = ∂_{1}∂_{0}/F/. So if /G/ is the total time
;;    derivative of /F/ then

;;    \begin{equation}
;;    {\partial_{0}G_{1} = \partial_{1}G_{0}.} & {(1.124)}
;;    \end{equation}

;;    Furthermore, /G/_{1} = ∂_{1}/F/, so

;;    \begin{equation}
;;    {\partial_{1}G_{1} = \partial_{1}\partial_{1}F.} & {(1.125)}
;;    \end{equation}

;;    If there is more than one degree of freedom these partials are actually
;;    structures of partial derivatives with respect to each coordinate. The
;;    partial derivatives with respect to two different coordinates must be the
;;    same independent of the order of the differentiation. So ∂_{1}/G/_{1} must be
;;    symmetric.

;;    Note that we have not shown that these conditions are sufficient for
;;    determining that a function is a total time derivative, only that they are
;;    necessary.



;;    *Exercise 1.28: Identifying total time derivatives*

;;    For each of the following functions, either show that it is not a total time
;;    derivative or produce a function from which it can be derived.

;;    *a.* /G/(/t/, /x/, /v/_{/x/}) = /mv_{x}/

;;    *b.* /G/(/t/, /x/, /v_{x}/) = /mv_{x}/ cos /t/

;;    *c.* /G/(/t/, /x/, /v_{x}/) = /v_{x}/ cos /t/ − /x/ sin /t/

;;    *d.* /G/(/t/, /x/, /v_{x}/) = /v_{x}/ cos /t/ + /x/ sin /t/

;;    *e.* /G/(/t/; /x/, /y/; /v_{x}/, /v_{y}/) = 2(/xv_{x}/ + /yv_{y}/) cos /t/ −
;;    (/x/^{2} + /y/^{2}) sin /t/

;;    *f.* /G/(/t/; /x/, /y/; /v_{x}/, /v_{y}/) = 2(/xv_{x}/ + /yv_{y}/) cos /t/ −
;;    (/x/^{2} + /y/^{2}) sin /t/ + /y/^{3}/v_{x}/ + /xv_{y}/

;;    *Exercise 1.29: Galilean invariance of kinetic energy*

;;    We have taken the kinetic energy of a set of particles indexed by /α/ to be

;;    $\sum_{\alpha}{\frac{1}{2}m_{\alpha}v_{\alpha}^{2}}$

;;    . This form is Galilean invariant.

;;    *a.* Start with a Lagrangian for free particles, which is only the sum of
;;    their kinetic energies:

;;    \begin{equation}
;;    {L(t,x,v) = {\sum\limits_{\alpha}{\frac{1}{2}m_{\alpha}v_{\alpha}^{2}.}}} & {(1.126)}
;;    \end{equation}

;;    Carry out a coordinate transformation from old to new coordinates that
;;    consists of a shift and a uniform translation

;;    \begin{equation}
;;    {x_{\alpha} = x_{\alpha}^{\prime} + \Delta x + \Delta vt.} & {(1.127)}
;;    \end{equation}

;;    Derive the Lagrangian in new coordinates.

;;    *b.* The new Lagrangian can be put in the form

;;    $\sum_{\alpha}{\frac{1}{2}m_{\alpha}{(v_{\alpha}^{\prime})}^{2}}$

;;    plus some additional terms. Show that the additional terms are a total time
;;    derivative.

;;    Thus the kinetic energy can be taken to be

;;    $\sum_{\alpha}{\frac{1}{2}m_{\alpha}v_{\alpha}^{2}}$

;;    in any uniformly moving coordinate system.

;; * 1.7 Evolution of Dynamical State
;;   :PROPERTIES:
;;   :CUSTOM_ID: h1-6b
;;   :CLASS:    level1
;;   :END:
;;   Lagrange's equations are ordinary differential equations that the path must
;;   satisfy. They can be used to test if a proposed path is a realizable path of
;;   the system. However, we can also use them to develop a path, starting with
;;   initial conditions.

;;   The /state/ of a system is defined to be the information that must be
;;   specified for the subsequent evolution to be determined. Remember our juggler:
;;   he or she must throw the pin in a certain way for it to execute the desired
;;   motion. The juggler has control of the initial position and orientation of the
;;   pin, and the initial velocity and spin of the pin. Our experience with
;;   juggling and similar systems suggests that the initial configuration and the
;;   rate of change of the configuration are sufficient to determine the subsequent
;;   motion. Other systems may require higher derivatives of the configuration.

;;   For Lagrangians that are written in terms of a set of generalized coordinates
;;   and velocities we have shown that Lagrange's equations are second-order
;;   ordinary differential equations. If the differential equations can be solved
;;   for the highest-order derivatives and if the differential equations satisfy
;;   the Lipschitz conditions,[fn:71] then there is a unique solution to the
;;   initial-value problem: given values of the solution and the lower derivatives
;;   of the solution at a particular moment, there is a unique solution function.
;;   Given irredundant coordinates the Lagrange equations satisfy these
;;   conditions.[fn:72] Thus a trajectory is determined by the generalized
;;   coordinates and the generalized velocities at any time. This is the
;;   information required to specify the dynamical state.

;;   A complete local description of a path consists of the path and all of its
;;   derivatives at a moment. The complete local description of a path can be
;;   reconstructed from an initial segment of the local tuple, given a prescription
;;   for computing higher-order derivatives of the path in terms of lower-order
;;   derivatives. The state of the system is specified by that initial segment of
;;   the local tuple from which the rest of the complete local description can be
;;   deduced. The complete local description gives us the path near that moment.
;;   Actually, all we need is a rule for computing the next higher derivative; we
;;   can get all the rest from this. Assume that the state of a system is given by
;;   the tuple (/t/, /q/, /v/). If we are given a prescription for computing the
;;   acceleration /a/ = /A/(/t/, /q/, /v/), then

;;   \begin{equation}
;;   {D^{2}q = A \circ \Gamma\lbrack q\rbrack,} & {(1.128)}
;;   \end{equation}

;;   and we have as a consequence

;;   \begin{equation}
;;   {D^{3}q = D(A \circ \Gamma\lbrack q\rbrack) = D_{t}A \circ \Gamma\lbrack q\rbrack,} & {(1.129)}
;;   \end{equation}

;;   and so on. So the higher-derivative components of the local tuple are given by
;;   functions

;;   $D_{t}A,\,\,\, D_{t}^{2}A,\ldots$

;;   . Each of these functions depends on lower-derivative components of the local
;;   tuple. All we need to deduce the path from the state is a function that gives
;;   the next-higher derivative component of the local description from the state.
;;   We use the Lagrange equations to find this function.

;;   First, we expand the Lagrange equations

;;   $\partial_{1}L \circ \Gamma\lbrack q\rbrack = D(\partial_{2}L \circ
;;   \Gamma\lbrack q\rbrack)$

;;   so that the second derivative appears explicitly:

;;   $\begin{array}{l} {\partial_{1}L \circ \Gamma\lbrack q\rbrack}
;;   \\ {\,\,\,\,\,\,\,\,\, = \partial_{0}\partial_{2}L \circ \Gamma\lbrack
;;   q\rbrack + (\partial_{1}\partial_{2}L \circ \Gamma\lbrack q\rbrack)\, Dq +
;;   (\partial_{2}\partial_{2}L \circ \Gamma\lbrack q\rbrack)\, D^{2}q.}
;;   \\ \end{equation}$

;;   Solving this system for /D/^{2}/q/, one obtains the generalized acceleration
;;   along a solution path /q/:

;;   $\begin{array}{l} {D^{2}q =}
;;   \\ {\,\,\,\,\,\,\,\,\,{\lbrack\partial_{2}\partial_{2}L \circ \Gamma\lbrack
;;   q\rbrack\rbrack}^{- 1}\lbrack\partial_{1}L \circ \Gamma\lbrack q\rbrack -
;;   (\partial_{1}\partial_{2}L \circ \Gamma\lbrack q\rbrack)\, Dq -
;;   \partial_{0}\partial_{2}L \circ \Gamma\lbrack q\rbrack\rbrack} \end{equation}$

;;   where [∂_{2}∂_{2}/L/ ∘ Γ] is a structure that can be represented by a
;;   symmetric square matrix, so we can compute its inverse.[fn:73] The function
;;   that gives the acceleration is

;;   \begin{equation}
;;   {A = {(\partial_{2}\partial_{2}L)}^{- 1}\lbrack\partial_{1}L - \partial_{0}\partial_{2}L - (\partial_{1}\partial_{2}L)\overset{˙}{Q}\rbrack,} & {(1.130)}
;;   \end{equation}

;;   where

;;   $\overset{˙}{Q} = I_{2}$

;;   is the velocity component selector.



;;   That initial segment of the local tuple that specifies the state is called the
;;   local state tuple, or, more simply, the state tuple.[fn:74]

;;   We can express the function that gives the acceleration as a function of the
;;   state tuple as the following procedure. It takes a procedure that computes the
;;   Lagrangian, and returns a procedure that takes a state tuple as its argument
;;   and returns the acceleration.[fn:75]

;;    #+begin_src scheme
;; (define (Lagrangian->acceleration L) (let ((P
;;   ((partial 2) L)) (F ((partial 1) L))) (solve-linear-left ((partial 2) P) (- F
;;   (+ ((partial 0) P) (* ((partial 1) P) velocity))))))
;;    #+end_src

;;   Once we have a way of computing the acceleration from the coordinates and the
;;   velocities, we can give a prescription for computing the derivative of the
;;   state as a function of the state. For the state (/t/, /q/(/t/), /Dq/(/t/)) at
;;   the moment /t/ the derivative of the state is (1, /Dq/(/t/), /D/^{2}/q/(/t/))
;;   = (1, /Dq/(/t/), /A/(/t/, /q/(/t/), /Dq/(/t/))). The procedure
;;   Lagrangian->state-derivative takes a Lagrangian and returns a procedure that
;;   takes a state and returns the derivative of the state:

;;    #+begin_src scheme
;; (define (Lagrangian->state-derivative L) (let
;;   ((acceleration (Lagrangian->acceleration L))) (lambda (state) (up 1 (velocity
;; state) (acceleration state)))))
;;    #+end_src

;;   We represent a state by an up tuple of the components of that initial segment
;;   of the local tuple that determine the state.



;;   For example, the parametric state derivative for a harmonic oscillator is

;;    #+begin_src scheme
;; (define (harmonic-state-derivative m k)
;;   (Lagrangian->state-derivative (L-harmonic m k))) ((harmonic-state-derivative
;; 'm 'k) (up 't (up 'x 'y) (up 'v_x 'v_y))) (up 1 (up v_x v_y) (up (/ (* -1 k x)
;; m) (/ (* -1 k y) m)))
;;    #+end_src

;;   The Lagrange equations are a second-order system of differential equations
;;   that constrain realizable paths /q/. We can use the state derivative to
;;   express the Lagrange equations as a first-order system of differential
;;   equations that constrain realizable coordinate paths /q/ and velocity paths
;;   /v/:

;;    #+begin_src scheme
;; (define ((Lagrange-equations-first-order L) q v) (let
;;   ((state-path (qv->state-path q v))) (- (D state-path) (compose
;;   (Lagrangian->state-derivative L) state-path)))) (define ((qv->state-path q v)
;; t) (up t (q t) (v t)))
;;    #+end_src

;;   For example, we can find the first-order form of the equations of motion of a
;;   two-dimensional harmonic oscillator:

;;    #+begin_src scheme
;; (show-expression (((Lagrange-equations-first-order
;;   (L-harmonic 'm 'k)) (up (literal-function 'x) (literal-function 'y)) (up
;;   (literal-function 'v_x) (literal-function 'v_y))) 't))
;;    #+end_src

;;   $$\begin{pmatrix} 0 \\ \begin{pmatrix} {Dx(t) - v_{x}(t)} {Dy(t) - v_{y}(t)}
;;   \end{pmatrix} \begin{pmatrix} {\frac{kx(t)}{m} + Dv_{x}(t)}
;;   \\ {\frac{ky(t)}{m} + Dv_{y}(t)} \end{pmatrix} \end{pmatrix}$$



;;   #+caption: *Figure 1.6* The input to the system derivative is the state.
;;   The function /A/ gives the acceleration as a function of the components that
;;   determine the state. The output of the system derivative is the derivative of
;;   the state. The integrator takes the derivative of the state as its input and
;;   produces the integrated state, starting at the initial conditions. Notice how
;;   the second-order system is put into first-order form by the routing of the
;;   /Dq/(/t/) components in the system derivative. [[file:images/Art_P208.jpg]]

;;   The zero in the first element of the structure of the Lagrange equations
;;   residuals is just the tautology that time advances uniformly: the time
;;   function is just the identity, so its derivative is one and the residual is
;;   zero. The equations in the second element constrain the velocity path to be
;;   the derivative of the coordinate path. The equations in the third element give
;;   the rate of change of the velocity in terms of the applied forces.

;; ** Numerical integration
;;    :PROPERTIES:
;;    :CUSTOM_ID: numerical-integration
;;    :CLASS:    level2
;;    :END:
;;    A set of first-order ordinary differential equations that give the state
;;    derivative in terms of the state can be integrated to find the state path
;;    that emanates from a given initial state. Numerical integrators find
;;    approximate solutions of such differential equations by a process illustrated
;;    in [[file:chapter001.html#Fig_1-6][figure 1.6]]. The state derivative
;;    produced by Lagrangian->state-derivative can be used by a package that
;;    numerically integrates systems of first-order ordinary differential
;;    equations.

;;    The procedure state-advancer can be used to find the state of a system at a
;;    specified time, given an initial state, which includes the initial time, and
;;    a parametric state-derivative procedure.[fn:76] For example, to advance the
;;    state of a two-dimensional harmonic oscillator we write[fn:77]

;;     #+begin_src scheme
;; ((state-advancer harmonic-state-derivative 2.0 1.0)
;;    (up 1.0 (up 1.0 2.0) (up 3.0 4.0)) 10.0 1.0e-12) (up 11.0 (up
;; 3.7127916645844437 5.420620823651583) (up 1.6148030925459782
;; 1.8189103724750855))
;;     #+end_src

;;    The arguments to state-advancer are a parametric state derivative,
;;    harmonic-state-derivative, and the state-derivative parameters (mass 2 and
;;    spring constant 1). A procedure is returned that takes an initial state, (up
;;    1 (up 1 2) (up 3 4)); a time increment, 10; and a relative error tolerance,
;;    1.0e-12. The output is an approximation to the state at the specified final
;;    time.

;;    Consider the driven pendulum described in
;;    [[file:chapter001.html#h3_1-6-2][section 1.6.2]] with a periodic drive. We
;;    choose /y_{s}/(/t/) = /A/ cos /ωt/.

;;     #+begin_src scheme
;; (define ((periodic-drive amplitude frequency phase) t)
;;    (* amplitude (cos (+ (* frequency t) phase)))) (define
;;    (L-periodically-driven-pendulum m l g A omega) (let ((ys (periodic-drive A
;; omega 0))) (L-pend m l g ys)))
;;     #+end_src



;;    Lagrange's equation for this system is

;;     #+begin_src scheme
;; (show-expression (((Lagrange-equations
;;    (L-periodically-driven-pendulum 'm 'l 'g 'A 'omega)) (literal-function
;; 'theta)) 't))
;;     #+end_src

;;    $$D^{2}\theta(t)l^{2}m - \cos(\omega t)\,\sin\,(\theta(t))\, Alm\omega^{2} +
;;    \sin\,(\theta(t))\, glm$$

;;    The parametric state derivative for the periodically driven pendulum is

;;     #+begin_src scheme
;; (define (pend-state-derivative m l g A omega)
;;    (Lagrangian->state-derivative (L-periodically-driven-pendulum m l g A
;; omega))) (show-expression ((pend-state-derivative 'm 'l 'g 'A 'omega) (up 't
;; 'theta 'thetadot)))
;;     #+end_src

;;    $$\begin{pmatrix} 1 \\ \overset{˙}{\theta} {\frac{A\omega^{2}\cos\,(\omega
;;    t)\,\sin\,(\theta)}{l} - \frac{g\sin\,(\theta)}{l}} \end{pmatrix}$$

;;    To examine the evolution of the driven pendulum we need a mechanism that
;;    evolves a system for some interval while monitoring aspects of the system as
;;    it evolves. The procedure evolve provides this service, using state-advancer
;;    repeatedly to advance the state to the required moments. The procedure evolve
;;    takes a parametric state derivative and its parameters and returns a
;;    procedure that evolves the system from a specified initial state to a number
;;    of other times, monitoring some aspect of the state at those times. To
;;    generate a plot of the angle versus time we make a monitor procedure that
;;    generates the plot as the evolution proceeds:[fn:78]

;;     #+begin_src scheme
;; (define ((monitor-theta win) state) (let ((theta
;;    ((principal-value :pi) (coordinate state)))) (plot-point win (time state)
;;    theta))) (define plot-win (frame 0.0 100.0 :-pi :pi)) ((evolve
;;    pend-state-derivative 1.0 ;m=1kg 1.0 ;l=1m 9.8 ;g=9.8m/s^2 0.1 ;a=1/10 m (*
;;    2.0 (sqrt 9.8)) ) ;omega (up 0.0 ;t0=0 1.0 ;theta0=1 radian 0.0) ;thetadot0=0
;;    radians/s (monitor-theta plot-win) 0.01 ;step between plotted points 100.0
;;    ;final time 1.0e-13) ;local error tolerance
;;     #+end_src

;;    [[#Fig_1-7][Figure 1.7]] shows the angle /θ/ versus time for a couple of
;;    orbits for the driven pendulum. The initial conditions for the two runs are
;;    the same except that in one the bob is given a tiny velocity equal to
;;    10^{−10}m/s, about one atom width per second. The initial segments of the two
;;    orbits are indistinguishable. After about 75 seconds the two orbits diverge
;;    and become completely different. This extreme sensitivity to tiny changes in
;;    initial conditions is characteristic of what is called /chaotic behavior/.
;;    Later, we will investigate this example further, using other tools such as
;;    Lyapunov exponents, phase space, and Poincaré sections.



;;    #+caption: *Figure 1.7* Orbits of the driven pendulum. The angle /θ/ is
;;    plotted against time. Because angles are periodic, this plot may be thought
;;    of as being wound around a cylinder. The upper plot shows the results of a
;;    simulation with initial conditions /θ/ = 1 and ¶ $\overset{˙}{\theta} = 0$ ¶
;;    . The orbit oscillates for a while, then circulates, then resumes
;;    oscillating. In the lower plot we show the result for a slightly different
;;    initial angular velocity, ¶ $\overset{˙}{\theta} = 10^{- 10}$ ¶ . The initial
;;    behavior is indistinguishable from the top figure, but the two trajectories
;;    become uncorrelated after the transition between oscillation and circulation.
;;    This extreme sensitivity to initial conditions is characteristic of systems
;;    with chaotic behavior. [[file:images/Art_P211.jpg]]



;;    *Exercise 1.30: Orbits in a central potential*

;;    A Lagrangian for planar motion of a particle of mass /m/ in a central field
;;    with potential energy /V/ (/r/) = −/βr^{α}/ is

;;    $L(t;r,\theta;\overset{˙}{r},\overset{˙}{\theta}) =
;;    \frac{1}{2}m({\overset{˙}{r}}^{2} + {(r\overset{˙}{\theta})}^{2}) + V(r).$

;;    *a.* Write a program to evolve the motion of a particle subject to this
;;    Lagrangian and display the orbit in the plane.

;;    *b.* Evolve this system with /α/ = +2 (harmonic oscillator). Observe that it
;;    describes an ellipse with its center at the origin, for a wide variety of
;;    initial conditions.

;;    *c.* Evolve this system with /α/ = −1 (Newtonian gravity). Observe that it
;;    describes an ellipse with a focus at the origin, for a wide variety of
;;    initial conditions.

;;    *d.* Evolve this system with /α/ = +1/4. Observe that it describes a trefoil
;;    with its center at the origin.

;;    *Exercise 1.31: Foucault pendulum evolution*

;;    If a Foucault pendulum is erected at the North Pole, it will precess exactly
;;    once in a day. If it is erected at the Equator it will not precess at all. It
;;    is widely reported that the precession rate is proportional to the cosine of
;;    the colatitude.

;;    *a.* Evolve the Foucault pendulum, using the Lagrangian you constructed in
;;    [[file:chapter001.html#Exe_1-25][exercise 1.25]] on
;;    [[file:chapter001.html#p62][page 62]]. You should look at the precession
;;    angle /λ/ as a function of time.

;;    *b.* How does the rate of precession compare to the predicted rate? You
;;    should expect to see an error caused by the fact that the local vertical, as
;;    defined by a plumb bob, is not directed to the center of the Earth.

;;    *c.* Let Δ/ϕ/ be the angle between the local vertical and the direction to
;;    the center of the Earth. How does the precession rate compare to the
;;    predicted precession rate with the colatitude corrected to /ϕ/ − Δ/ϕ/? Is
;;    this perfect?

;; * 1.8 Conserved Quantities
;;   :PROPERTIES:
;;   :CUSTOM_ID: h1-6c
;;   :CLASS:    level1
;;   :END:
;;   A function of the state of the system that is constant along a solution path
;;   is called a /conserved quantity/ or a /constant of motion/.[fn:79] If /C/ is a
;;   conserved quantity, then

;;   \begin{equation}
;;   {D(C \circ \Gamma\lbrack q\rbrack) = D_{t}C \circ \Gamma\lbrack q\rbrack = 0} & {(1.131)}
;;   \end{equation}



;;   for solution paths /q/. In this section, we will investigate systems with
;;   symmetry and find that symmetries are associated with conserved quantities.
;;   For instance, linear momentum is conserved in a system with translational
;;   symmetry, angular momentum is conserved if there is rotational symmetry,
;;   energy is conserved if the system does not depend on the origin of time. We
;;   first consider systems for which a coordinate system can be chosen that
;;   expresses the symmetry naturally, and later discuss systems for which no
;;   coordinate system can be chosen that simultaneously expresses all symmetries.

;; *** 1.8.1 Conserved Momenta
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-8-1
;;     :CLASS:    level3
;;     :END:
;;     If a Lagrangian /L/(/t/, /q/, /v/) does not depend on some particular
;;     coordinate /q^{i}/, then

;;     \begin{equation}
;;     {{(\partial_{1}L)}_{i} = 0,} & {(1.132)}
;;     \end{equation}

;;     and the corresponding /i/th component of the Lagrange equations is

;;     \begin{equation}
;;     {{(D(\partial_{2}L \circ \Gamma\lbrack q\rbrack))}_{i} = 0.} & {(1.133)}
;;     \end{equation}

;;     The derivative of a component is equal to the component of the derivative,
;;     so this is the same as

;;     \begin{equation}
;;     {D({(\partial_{2}L)}_{i} \circ \Gamma\lbrack q\rbrack) = 0,} & {(1.134)}
;;     \end{equation}

;;     and we can see that

;;     \begin{equation}
;;     {\mathcal{P}_{i} = {(\partial_{2}L)}_{i}} & {(1.135)}
;;     \end{equation}

;;     is a conserved quantity. The function

;;     $\mathcal{P}$

;;     is called the /momentum state function/. The value of the momentum state
;;     function is the /generalized momentum/. We refer to the /i/th component of
;;     the generalized momentum as the momentum /conjugate/ to the /i/th
;;     coordinate.[fn:80] The momenta depend on the choice of Lagrangian used to
;;     describe the system.[fn:81] A generalized coordinate component that does not
;;     appear explicitly in the Lagrangian is called a /cyclic coordinate/. The
;;     generalized momentum component conjugate to any cyclic coordinate is a
;;     constant of the motion. Its value is constant along realizable paths; it may
;;     have different values on different paths. As we will see, momentum is an
;;     important quantity even when it is not conserved.

;;     Given the coordinate path /q/ and the Lagrangian /L/, the momentum path /p/
;;     is

;;     \begin{equation}
;;     {p = \partial_{2}L \circ \Gamma\lbrack q\rbrack = \mathcal{P} \circ \Gamma\lbrack q\rbrack,} & {(1.136)}
;;     \end{equation}

;;     with components

;;     \begin{equation}
;;     {p_{i} = \mathcal{P}_{i} \circ \Gamma\lbrack q\rbrack.} & {(1.137)}
;;     \end{equation}

;;     The momentum path is well defined for any path /q/. If the path is
;;     realizable and the Lagrangian does not depend on /q^{i}/, then /p_{i}/ is a
;;     constant function

;;     \begin{equation}
;;     {Dp_{i} = 0.} & {(1.138)}
;;     \end{equation}

;;     The constant value of /p_{i}/ may be different for different trajectories.

;; ** Examples of conserved momenta
;;    :PROPERTIES:
;;    :CUSTOM_ID: examples-of-conserved-momenta
;;    :CLASS:    level2
;;    :END:
;;    The free-particle Lagrangian

;;    $L(t,x,v) = \frac{1}{2}mv^{2}$

;;    is independent of /x/. So the momentum state function,

;;    $\mathcal{P}(t,q,v) = mv$

;;    , is conserved along realizable paths. The momentum path /p/ for the
;;    coordinate path /q/ is

;;    $p(t) = \mathcal{P} \circ \Gamma\lbrack q\rbrack(t) = m\, Dq(t)$

;;    . For a realizable path /Dp/(/t/) = 0. For the free particle the usual linear
;;    momentum is conserved for realizable paths.

;;    For a particle in a central force field
;;    ([[file:chapter001.html#h1-6a][section 1.6]]), the Lagrangian

;;    $L(t;r,\varphi;\overset{˙}{r},\overset{˙}{\varphi}) =
;;    \frac{1}{2}m({\overset{˙}{r}}^{2} + r^{2}{\overset{˙}{\varphi}}^{2}) - V(r)$

;;    depends on /r/ but is independent of /φ/. The momentum state function is

;;    $\mathcal{P}\left( {t;r,\varphi;\overset{˙}{r},\overset{˙}{\varphi}} \right)
;;    = \lbrack m\overset{˙}{r},mr^{2}\overset{˙}{\varphi}\rbrack.$

;;    It has two components. The first component, the “radial momentum,” is not
;;    conserved. The second component, the “angular momentum,” is conserved along
;;    any solution trajectory.



;;    If the central-potential problem had been expressed in rectangular
;;    coordinates, then all of the coordinates would have appeared in the
;;    Lagrangian. In that case there would not be any obvious conserved quantities.
;;    Nevertheless, the motion of the system does not depend on the choice of
;;    coordinates, so the angular momentum is still conserved.

;;    We see that there is great advantage in making a judicious choice for the
;;    coordinate system. If we can choose the coordinates so that a symmetry of the
;;    system is reflected in the Lagrangian by the absence of some coordinate
;;    component, then the existence of a corresponding conserved quantity will be
;;    evident.[fn:82]

;; *** 1.8.2 Energy Conservation
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-8-2
;;     :CLASS:    level3
;;     :END:
;;     Momenta are conserved by the motion if the Lagrangian does not depend on the
;;     corresponding coordinate. There is another constant of the motion, the
;;     energy, if the Lagrangian

;;     $L(t,q,\overset{˙}{q})$

;;     does not depend explicitly on the time: ∂_{0}/L/ = 0.

;;     Consider the time derivative of the Lagrangian along a solution path /q/:

;;     \begin{equation}
;;     {D(L \circ \Gamma\lbrack q\rbrack) = \partial_{0}L \circ \Gamma\lbrack q\rbrack + (\partial_{1}L \circ \Gamma\lbrack q\rbrack)Dq + (\partial_{2}L \circ \Gamma\lbrack q\rbrack)D^{2}q.} & {(1.139)}
;;     \end{equation}

;;     Using Lagrange's equations to rewrite the second term yields

;;     \begin{equation}
;;     {D(L \circ \Gamma\lbrack q\rbrack) = (\partial_{0}L) \circ \Gamma\lbrack q\rbrack + D(\partial_{2}L \circ \Gamma\lbrack q\rbrack)Dq + (\partial_{2}L \circ \Gamma\lbrack q\rbrack)D^{2}q.} & {(1.140)}
;;     \end{equation}

;;     Isolating ∂_{0}/L/ and combining the other terms we get

;;     $$\begin{array}{lll} {(\partial_{0}L) \circ \Gamma\lbrack q\rbrack} & {= D(L
;;     \circ \Gamma\lbrack q\rbrack) - D((\partial_{2}L \circ \Gamma\lbrack
;;     q\rbrack)Dq)} & \\
;;     & {= D(L \circ \Gamma\lbrack q\rbrack) - D((\partial_{2}L \circ
;;     \Gamma\lbrack q\rbrack)(\overset{˙}{Q} \circ \Gamma\lbrack q\rbrack))} & \\
;;     & {= D((L - \mathcal{P}\overset{˙}{Q}) \circ \Gamma\lbrack q\rbrack),} &
;;     {(1.141)} \end{equation}

;;     where, as before,

;;     $\overset{˙}{Q}$

;;     selects the velocity from the state. So we see that if ∂_{0}/L/ = 0 then

;;     \begin{equation}
;;     {\mathcal{E} = \mathcal{P}\overset{˙}{Q} - L} & {(1.142)}
;;     \end{equation}



;;     is conserved along realizable paths. The function /ℰ/ is called the /energy
;;     state function/.[fn:83] The energy state function for a system depends on
;;     the choice of Lagrangian used to describe the system.[fn:84] Let /E/ = /ℰ/ ∘
;;     Γ[/q/] denote the energy function on the path /q/. The energy function has a
;;     constant value along any realizable trajectory if the Lagrangian has no
;;     explicit time dependence; the energy /E/ may have a different value for
;;     different trajectories. A system that has no explicit time dependence is
;;     called /autonomous/.

;;     Given a Lagrangian procedure L, we may construct the energy function:

;;      #+begin_src scheme
;; (define (Lagrangian->energy L) (let ((P ((partial 2) L))) (- (* P velocity) L)))
;;      #+end_src

;; ** Energy in terms of kinetic and potential energies
;;    :PROPERTIES:
;;    :CUSTOM_ID: energy-in-terms-of-kinetic-and-potential-energies
;;    :CLASS:    level2
;;    :END:
;;    In some cases the energy can be written as the sum of kinetic and potential
;;    energies. Suppose the system is composed of particles with rectangular
;;    coordinates *x*_{/α/}, the movement of which may be subject to constraints,
;;    and that these rectangular coordinates are some functions of the generalized
;;    coordinates /q/ and possibly time /t/: *x*_{/α/} = /f_{α}/(/t/, /q/). We form
;;    the Lagrangian as /L/ = /T/ − /V/ and compute the kinetic energy in terms of
;;    /q/ by writing the rectangular velocities in terms of the generalized
;;    velocities:

;;    \begin{equation}
;;    {\mathbf{v}_{\alpha} = \partial_{0}f_{\alpha}(t,q) + \partial_{1}f_{\alpha}(t,q)v.} & {(1.143)}
;;    \end{equation}

;;    The kinetic energy is

;;    \begin{equation}
;;    {T\left( {t,q,v} \right) = \frac{1}{2}{\sum_{\alpha}{m_{\alpha}v_{\alpha}^{2},}}} & {(1.144)}
;;    \end{equation}

;;    where /v_{α}/ is the magnitude of *v*_{/α/}.

;;    If the /f/_{/α/} functions do not depend explicitly on time (∂_{0}/f_{α}/ =
;;    0), then the rectangular velocities are homogeneous functions of the
;;    generalized velocities of degree 1, and /T/ is a homogeneous function of the
;;    generalized velocities of degree 2, because it is formed by summing the
;;    square of homogeneous functions of degree 1. If /T/ is a homogeneous function
;;    of degree 2 in the generalized velocities then

;;    \begin{equation}
;;    {\mathcal{P}\overset{˙}{Q} = \left( {\partial_{2}T} \right)\overset{˙}{Q} = 2T,} & {(1.145)}
;;    \end{equation}

;;    where the second equality follows from Euler's theorem on homogeneous
;;    functions.[fn:85] The energy state function is

;;    \begin{equation}
;;    {\mathcal{E} = \mathcal{P}\overset{˙}{Q} - L = 2T - T + V.} & {(1.146)}
;;    \end{equation}

;;    So if /f_{α}/ is independent of time, the energy function can be rewritten

;;    \begin{equation}
;;    {\mathcal{E} = 2T - T + V = T + V.} & {(1.147)}
;;    \end{equation}

;;    Notice that if /V/ depends on time the energy is still the sum of the kinetic
;;    energy and potential energy, but the energy is not conserved.

;;    The energy state function is always well defined, whether or not it can be
;;    written in the form /T/ + /V/, and whether or not it is conserved along
;;    realizable paths.

;;    *Exercise 1.32: Time-dependent constraints*

;;    An analogous result holds when the /f_{α}/ depend explicitly on time.

;;    *a.* Show that in this case the kinetic energy contains terms that are linear
;;    in the generalized velocities.

;;    *b.* By adding a total time derivative, show that the Lagrangian can be
;;    written in the form /L/ = /A/ − /B/, where /A/ is a homogeneous quadratic
;;    form in the generalized velocities and /B/ is independent of velocity.

;;    *c.* Show, using Euler's theorem, that the energy function is /ℰ/ = /A/+/B/.

;;    An example in which terms that were linear in the velocity were removed from
;;    the Lagrangian by adding a total time derivative has already been given: the
;;    driven pendulum.



;;    *Exercise 1.33: Falling off a log*

;;    A particle of mass /m/ slides off a horizontal cylinder of radius /R/ in a
;;    uniform gravitational field with acceleration /g/. If the particle starts
;;    close to the top of the cylinder with zero initial speed, with what angular
;;    velocity does it leave the cylinder? Use the method of incorporating
;;    constraint forces that we introduced in
;;    [[file:chapter001.html#h3_1-6-2][section 1.6.2]], together with conservation
;;    of energy.

;; *** 1.8.3 Central Forces in Three Dimensions
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-8-3
;;     :CLASS:    level3
;;     :END:
;;     One important physical system is the motion of a particle in a central field
;;     in three dimensions, with an arbitrary potential energy /V/ (/r/) depending
;;     only on the radius. We will describe this system in spherical coordinates
;;     /r/, /θ/, and /φ/, where /θ/ is the colatitude and /φ/ is the longitude. The
;;     kinetic energy has three terms:

;;     $$T(t;r,\theta,\varphi;\overset{˙}{r},\overset{˙}{\theta},\overset{˙}{\varphi})
;;     = \frac{1}{2}m({\overset{˙}{r}}^{2} + r^{2}{\overset{˙}{\theta}}^{2} +
;;     r^{2}{(\sin\theta)}^{2}{\overset{˙}{\varphi}}^{2}).$$

;;     As a procedure:

;;      #+begin_src scheme
;; (define ((T3-spherical m) state) (let ((q (coordinate state)) (qdot (velocity
;; state))) (let ((r (ref q 0)) (theta (ref q 1)) (rdot
;;     (ref qdot 0)) (thetadot (ref qdot 1)) (phidot (ref qdot 2))) (* 1/2 m (+
;;     (square rdot) (square (* r thetadot)) (square (* r (sin theta) phidot)))))))

;;      #+end_src

;;     A Lagrangian is then formed by subtracting the potential energy:

;;         #+begin_src scheme
;; (define (L3-central m Vr) (define (Vs state) (let ((r
;;     (ref (coordinate state) 0))) (Vr r))) (- (T3-spherical m) Vs)) {%

;;         #+end_src

;;     Let's first look at the generalized forces (the derivatives of the
;;     Lagrangian with respect to the generalized coordinates). We compute these
;;     with a partial derivative with respect to the coordinate argument of the
;;     Lagrangian:

;;      #+begin_src scheme
;; (show-expression (((partial 1) (L3-central 'm
;;     (literal-function 'V))) (up 't (up 'r 'theta 'phi) (up 'rdot 'thetadot
;; 'phidot))))
;;      #+end_src

;;     $$\begin{bmatrix} {m{\overset{˙}{\varphi}}^{2}r\,{(\sin\,(\theta))}^{2} +
;;     mr{\overset{˙}{\theta}}^{2} - DV\,(r)}
;;     \\ {m{\overset{˙}{\varphi}}^{2}r^{2}\cos\,(\theta)\,\sin\,(\theta)} 0
;;     \\ \end{bmatrix}$$

;;     The /φ/ component of the force is zero because /φ/ does not appear in the
;;     Lagrangian (it is a cyclic coordinate). The corresponding momentum component
;;     is conserved. Compute the momenta:

;;      #+begin_src scheme
;; (show-expression (((partial 2) (L3-central 'm
;;     (literal-function 'V))) (up 't (up 'r 'theta 'phi) (up 'rdot 'thetadot
;; 'phidot))))
;;      #+end_src

;;     $$\begin{bmatrix} {m\overset{˙}{r}} {mr^{2}\overset{˙}{\theta}}
;;     \\ {mr^{2}\overset{˙}{\varphi}{(\sin\,(\theta))}^{2}} \end{bmatrix}$$

;;     The momentum conjugate to /φ/ is conserved. This is the /z/ component of the
;;     angular momentum

;;     $\overset{\rightarrow}{r} \times (m\overset{\rightarrow}{v})$

;;     , for vector position

;;     $\overset{\rightarrow}{r}$

;;     and linear momentum

;;     $m\overset{\rightarrow}{v}$

;;     . We can show this by writing the /z/ component of the angular momentum in
;;     spherical coordinates:

;;      #+begin_src scheme
;; (define ((ang-mom-z m) rectangular-state) (let ((xyz
;;     (coordinate rectangular-state)) (v (velocity rectangular-state))) (ref
;;     (cross-product xyz (* m v)) 2))) (define (s->r spherical-state) (let ((q
;;     (coordinate spherical-state))) (let ((r (ref q 0)) (theta (ref q 1)) (phi
;;     (ref q 2))) (let ((x (* r (sin theta) (cos phi))) (y (* r (sin theta) (sin
;; phi))) (z (* r (cos theta)))) (up x y z)))))
;;      #+end_src

;;         #+begin_src scheme
;; (show-expression ((compose (ang-mom-z 'm) (F->C s->r)) (up 't (up 'r 'theta
;; 'phi) (up 'rdot 'thetadot 'phidot)))) {%

;;         #+end_src

;;     $$mr^{2}\overset{˙}{\varphi}{(\sin\,(\theta))}^{2}$$

;;     The choice of the /z/-axis is arbitrary, so the conservation of any
;;     component of the angular momentum implies the conservation of all
;;     components. Thus the total angular momentum is conserved. We can choose the
;;     /z/-axis so all of the angular momentum is in the /z/ component. Since

;;     $\overset{\rightarrow}{x} \cdot (\overset{\rightarrow}{x} \times
;;     \overset{\rightarrow}{v}) = \overset{\rightarrow}{v} \cdot
;;     (\overset{\rightarrow}{x} \times \overset{\rightarrow}{x}) = 0$

;;     , the motion is confined to the plane perpendicular to the angular momentum:
;;     /θ/ = /π//2, and

;;     $\overset{˙}{\theta} = 0$

;;     . Planar motion in a central-force field was discussed in
;;     [[file:chapter001.html#h1-6a][section 1.6]].

;;     We can also see that the energy state function computed from the Lagrangian
;;     for a central field is in fact /T/ + /V/ :

;;      #+begin_src scheme
;; (show-expression ((Lagrangian->energy (L3-central 'm
;;     (literal-function 'V))) (up 't (up 'r 'theta 'phi) (up 'rdot 'thetadot
;; 'phidot))))
;;      #+end_src

;;     $$\frac{1}{2}m{\overset{˙}{\varphi}}^{2}r^{2}{(\sin\,(\theta))}^{2} +
;;     \frac{1}{2}mr^{2}{\overset{˙}{\theta}}^{2} +
;;     \frac{1}{2}m{\overset{˙}{r}}^{2} + V\,(r)$$

;;     The energy is conserved because the Lagrangian has no explicit time
;;     dependence.

;;     *Exercise 1.34: Driven spherical pendulum*

;;     A spherical pendulum is a massive bob, subject to uniform gravity, that may
;;     swing in three dimensions, but remains at a given distance from the pivot.
;;     Formulate a Lagrangian for a spherical pendulum driven by vertical motion of
;;     the pivot. What symmetry(ies) can you find? Find coordinates that express
;;     the symmetry(ies). What is conserved? Give analytic expression(s) for the
;;     conserved quantity(ies).

;; *** 1.8.4 The Restricted Three-Body Problem
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-8-4
;;     :CLASS:    level3
;;     :END:
;;     Consider the situation of two bodies of masses /M/_{0} and /M/_{1} in
;;     circular orbit about their common center of mass. What is the behavior of a
;;     third particle, gravitationally attracted to the other two, that must move
;;     in the plane of their circular orbit? Assume that the third particle has
;;     such small mass that we can neglect its effect on the orbits of the two
;;     massive particles.

;;     The third particle, of mass /m/, moves in a field derived from a
;;     time-varying gravitational potential energy. We have:

;;      #+begin_src scheme
;; (define ((L0 m V) local) (let ((t (time local)) (q
;;     (coordinates local)) (v (velocities local))) (- (* 1/2 m (square v)) (V t
;; q))))
;;      #+end_src

;;     Let /a/ be the constant distance between the two bodies. If we put the
;;     center of mass at the origin of the coordinate system then the distances of
;;     the two particles from the origin are:

;;     \begin{equation}
;;     {a_{0} = \frac{M_{1}}{M_{0} + M_{1}}a\,\,\text{and}\,\, a_{1} = \frac{M_{0}}{M_{0} + M_{1}}a} & {(1.148)}
;;     \end{equation}

;;     Each massive particle revolves in a circle about their common center of mass
;;     with angular frequency Ω. The radii of the circles are the distances given
;;     above. Kepler's law gives the angular frequency of the orbit:

;;     \begin{equation}
;;     {\Omega^{2}a^{3} = G(M_{0} + M_{1})} & {(1.149)}
;;     \end{equation}

;;     We choose our axes so that at /t/ = 0 the body with mass /M/_{1} is on the
;;     positive

;;     $\widehat{x}$

;;     axis and the body with mass /M/_{0} is on the negative

;;     $\widehat{x}$

;;     axis. The gravitational potential energy function is:

;;      #+begin_src scheme
;; (define ((V a GM0 GM1 m) t xy) (let ((Omega (sqrt (/
;;     (+ GM0 GM1) (expt a 3)))) (a0 (* (/ GM1 (+ GM0 GM1)) a)) (a1 (* (/ GM0 (+
;;     GM0 GM1)) a))) (let ((x (ref xy 0)) (y (ref xy 1)) (x0 (* -1 a0 (cos (*
;;     Omega t)))) (y0 (* -1 a0 (sin (* Omega t)))) (x1 (* +1 a1 (cos (* Omega
;;     t)))) (y1 (* +1 a1 (sin (* Omega t))))) (let ((r0 (sqrt (+ (square (- x x0))
;;     (square (- y y0))))) (r1 (sqrt (+ (square (- x x1)) (square (- y y1)))))) (-
;;     (+ (/ (* GM0 m) r0) (/ (* GM1 m) r1)))))))
;;      #+end_src



;;     It is convenient to examine the motion of the third particle in a rotating
;;     coordinate system where the massive particles are fixed. We can place the
;;     rotating axes so that the two massive particles are on the

;;     $\widehat{x}\prime$

;;     axis, and we can choose the rotating and nonrotating axes to be coincident
;;     at /t/ = 0. We can transform to the rotating rectangular coordinates as we
;;     did on [[file:chapter001.html#p48][page 48]]. The resulting Lagrangian is
;;     the Lagrangian for the free particle with the addition of two gravitational
;;     potential energy terms:

;;     \begin{equation}
;;     {L_{r}(t;x_{r},y_{r};{\overset{˙}{x}}_{r},{\overset{˙}{y}}_{r})} & \\
;;     {\,\,\,\,\,\,\,\,\,\, = \frac{1}{2}m\left( {{\overset{˙}{x}}_{r}^{2} + {\overset{˙}{y}}_{r}^{2}} \right) + \frac{1}{2}m\Omega^{2}(x_{r}^{2} + y_{r}^{2}) + m\Omega(x_{r}{\overset{˙}{y}}_{r} - {\overset{˙}{x}}_{r}y_{r})} & \\
;;     {\,\,\,\,\,\,\,\,\,\,\,\,\,\, + \frac{GM_{0}m}{r_{0}} + \frac{GM_{1}m}{r_{1}}} & {(1.150)}
;;     \end{equation}

;;     where now

;;     $r_{0}^{2} = {(x_{r} + a_{0})}^{2} + y_{r}^{2}\,\,\text{and}\,\, r_{1}^{2} =
;;     \left( {x_{r} - a_{1}} \right)^{2} + y_{r}^{2}$

;;     . As a program we can write:

;;      #+begin_src scheme
;; (define ((LR3B m a GM0 GM1) local) (let ((q
;;     (coordinates local)) (qdot (velocities local)) (Omega (sqrt (/ (+ GM0 GM1)
;;     (expt a 3)))) (a0 (* (/ GM1 (+ GM0 GM1)) a)) (a1 (* (/ GM0 (+ GM0 GM1)) a)))
;;     (let ((x (ref q 0)) (y (ref q 1)) (xdot (ref qdot 0)) (ydot (ref qdot 1)))
;;     (let ((r0 (sqrt (+ (square (+ x a0)) (square y)))) (r1 (sqrt (+ (square (- x
;;     a1)) (square y))))) (+ (* 1/2 m (square qdot)) (* 1/2 m (square Omega)
;;     (square q)) (* m Omega (- (* x ydot) (* xdot y))) (/ (* GM0 m) r0) (/ (* GM1
;; m) r1))))))
;;      #+end_src

;;     Notice that the Lagrangian in rotating coordinates is independent of time.
;;     So the energy state function defined by this Lagrangian is a conserved
;;     quantity. Let's compute it. It is clearest if we express the result in terms
;;     of Ω, /a/_{0}, and /a/_{1}, so we make those into explicit parameters of the
;;     Lagrangian:

;;         #+begin_src scheme
;; (define ((LR3B1 m a0 a1 Omega GM0 GM1) local) (let
;;     ((q (coordinates local)) (qdot (velocities local))) (let ((x (ref q 0)) (y
;;     (ref q 1)) (xdot (ref qdot 0)) (ydot (ref qdot 1))) (let ((r0 (sqrt (+
;;     (square (+ x a0)) (square y)))) (r1 (sqrt (+ (square (- x a1)) (square
;;     y))))) (+ (* 1/2 m (square qdot)) (* 1/2 m (square Omega) (square q)) (* m
;;     Omega (- (* x ydot) (* xdot y))) (/ (* GM0 m) r0) (/ (* GM1 m) r1)))))) {%

;;         #+end_src

;;     And we compute the energy state function (with a bit of hand
;;     simplification):

;;      #+begin_src scheme
;; ((Lagrangian->energy (LR3B1 'm 'a_0 'a_1 'Omega 'GM_0 'GM_1)) (up 't (up 'x_r
;; 'y_r) (up 'v_r^x 'v_r^y))) (+ (* 1/2 m (expt v_r^x 2)) (* 1/2 m (expt v_r^y
;; 2)) (/ (* -1 GM_0 m) (sqrt (+ (expt (+ x_r a_0) 2)
;;     (expt y_r 2)))) (/ (* -1 GM_1 m) (sqrt (+ (expt (- x_r a_1) 2) (expt y_r
;; 2)))) (* -1/2 m (expt Omega 2) (expt x_r 2)) (* -1/2 m (expt Omega 2) (expt y_r
;; 2)))
;;      #+end_src

;;     If we separate this into a velocity-dependent part and a
;;     velocity-independent part we get

;;     \begin{equation}
;;     {\mathcal{E}(t;x_{r},y_{r};{\overset{˙}{x}}_{r},{\overset{˙}{y}}_{r}) = \frac{1}{2}m({\overset{˙}{x}}_{r}^{2} + {\overset{˙}{y}}_{r}^{2}) + mU_{r}(x_{r},y_{r})} & {(1.151)}
;;     \end{equation}

;;     where

;;     \begin{equation}
;;     {U_{r}(x_{r},y_{r}) = - \left( {\frac{GM_{0}}{r_{0}} + \frac{GM_{1}}{r_{1}} + \frac{1}{2}\Omega^{2}(x_{r}^{2} + y_{r}^{2})} \right).} & {(1.152)}
;;     \end{equation}

;;     This constant of motion of the restricted three-body problem is called the
;;     /Jacobi constant/.[fn:86] Notice that the energy function is a positive
;;     definite quadratic form in the components of the velocity (in rotating
;;     coordinates) plus a function that depends only on the rotating coordinates.
;;     Note that the energy state function does not have terms that are linear in
;;     the velocities

;;     ${\overset{˙}{x}}_{r}$

;;     and

;;     ${\overset{˙}{y}}_{r}$

;;     , although such terms appear in the Lagrangian
;;     ([[file:chapter001.html#disp_1.150][1.150]]).



;;     *Exercise 1.35: Restricted equations of motion*

;;     Derive the Lagrange equations for the restricted three-body problem, given
;;     the Lagrangian ([[file:chapter001.html#disp_1.150][1.150]]). Identify the
;;     Coriolis and centrifugal force terms in your equations of motion.

;; *** 1.8.5 Noether's Theorem
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-8-5
;;     :CLASS:    level3
;;     :END:
;;     We have seen that if a system has a symmetry and a coordinate system can be
;;     chosen so that the Lagrangian does not depend on the coordinate associated
;;     with that symmetry, then there is a conserved quantity associated with the
;;     symmetry. However, there are more general symmetries that no coordinate
;;     system can fully express. For example, motion in a central potential is
;;     spherically symmetric (the dynamical system is invariant under rotations
;;     about any axis), but the expression of the Lagrangian for the system in
;;     spherical coordinates exhibits symmetry around only one axis. More
;;     generally, a Lagrangian has a symmetry if there is a coordinate
;;     transformation that leaves the Lagrangian unchanged. A continuous symmetry
;;     is a parametric family of symmetries. Noether proved that for any continuous
;;     symmetry there is a conserved quantity.

;;     Consider a parametric coordinate transformation

;;     $\widetilde{F}$

;;     with parameter /s/:

;;     \begin{equation}
;;     {x = \widetilde{F}(s)(t,x\prime).} & {(1.153)}
;;     \end{equation}

;;     To this parametric coordinate transformation there corresponds a parametric
;;     state transformation

;;     $\widetilde{C}$

;;     :

;;     \begin{equation}
;;     {(t,x,v) = \widetilde{C}(s)(t,x\prime,v\prime).} & {(1.154)}
;;     \end{equation}

;;     We require that the transformation

;;     $\widetilde{F}(0)$

;;     be the identity coordinate transformation

;;     $x\prime = \widetilde{F}(0)(t,x\prime)$

;;     , and as a consequence

;;     $\widetilde{C}(0)$

;;     is the identity state transformation

;;     $(t,x\prime,v\prime) = \widetilde{C}(0)(t,x\prime,v\prime)$

;;     . The Lagrangian /L/ has a continuous symmetry corresponding to

;;     $\widetilde{F}$

;;     if it is invariant under the transformations

;;     \begin{equation}
;;     {\widetilde{L}(s) = L \circ \widetilde{C}(s) = L} & {(1.155)}
;;     \end{equation}

;;     for any /s/. The Lagrangian /L/ is the same function as the transformed
;;     Lagrangian

;;     $\widetilde{L}(s)$

;;     .



;;     That

;;     $\widetilde{L}(s) = L$

;;     for any /s/ implies

;;     $D\widetilde{L}(s) = 0$

;;     . Explicitly,

;;     $\widetilde{L}(s)$

;;     is

;;     \begin{equation}
;;     {\widetilde{L}(s)(t,x\prime,v\prime) = L(t,\widetilde{F}(s)(t,x\prime),D_{t}(\widetilde{F}(s))(t,x\prime,v\prime)),} & {(1.156)}
;;     \end{equation}

;;     where we have rewritten the velocity component of

;;     $\widetilde{C}(s)$

;;     in terms of the total time derivative. The derivative of

;;     $\widetilde{L}$

;;     is zero:

;;     $$\begin{array}{lll} 0 & {= D\widetilde{L}(s)(t,x\prime,v\prime)} & \\
;;     & {= \partial_{1}L(t,x,v)\,\,(D\widetilde{F})(s)(t,x\prime) +
;;     \partial_{2}L(t,x,v)\,\, D_{t}(D\widetilde{F}(s))(t,x\prime),} & {(1.157)}
;;     \\
;;     \end{equation}

;;     where we have used the fact that[fn:87]

;;     \begin{equation}
;;     {D_{t}(D\widetilde{F}(s)) = DG(s)\,\,\,\text{with}\,\,\, G\left( s \right) = D_{t}(\widetilde{F}(s)).} & {(1.158)}
;;     \end{equation}

;;     On a realizable path /q/ we can use the Lagrange equations to rewrite the
;;     first term of equation ([[file:chapter001.html#disp_1.157][1.157]]):

;;     $$\begin{array}{lll} {0 =} & {(D_{t}\partial_{2}L \circ \Gamma\lbrack
;;     q\rbrack)\,\,((D\widetilde{F})(s) \circ \Gamma\lbrack q\prime\rbrack)} & \\
;;     & {\, + (\partial_{2}L \circ \Gamma\lbrack
;;     q\rbrack)\,\,(D_{t}(D\widetilde{F}(s)) \circ \Gamma\lbrack q\prime\rbrack).}
;;     & {(1.159)} \end{equation}

;;     For /s/ = 0 the paths /q/ and /q/′ are the same, because

;;     $\widetilde{F}(0)$

;;     is the identity, so Γ[/q/] = Γ[/q/′] and this equation becomes

;;     $$\begin{array}{lll} 0 & {= ((D_{t}\partial_{2}L)\,\,((D\widetilde{F})(0)) +
;;     (\partial_{2}L)\,\,(D_{t}(D\widetilde{F}(0)))) \circ \Gamma\lbrack q\rbrack}
;;     & \\
;;     & {= D_{t}((\partial_{2}L)\,\,(D\widetilde{F}(0))) \circ \Gamma\lbrack
;;     q\rbrack.} & {(1.160)} \end{equation}

;;     Thus the state function

;;     $\mathcal{I}$

;;     ,

;;     \begin{equation}
;;     {\mathcal{I} = (\partial_{2}L)(D\widetilde{F}(0)),} & {(1.161)}
;;     \end{equation}

;;     is conserved along solution trajectories. This conserved quantity is called
;;     /Noether's integral/. It is the product of the momentum and a vector
;;     associated with the symmetry.



;; ** Illustration: motion in a central potential
;;    :PROPERTIES:
;;    :CUSTOM_ID: illustration-motion-in-a-central-potential
;;    :CLASS:    level2
;;    :END:
;;    For example, consider the central-potential Lagrangian in rectangular
;;    coordinates:

;;    \begin{equation}
;;    {L(t;x,y,z;v_{x}.v_{y},v_{z})} & \\
;;    {\,\,\,\,\,\,\,\,\,\,\, = \frac{1}{2}m\,\left( v_{x}^{2} + v_{y}^{2} + v_{z}^{2} \right) - U\left( \sqrt{x^{2} + y^{2} + z^{2}} \right),} & {(1.162)}
;;    \end{equation}

;;    and a parametric rotation /R_{z}/(/s/) about the /z/ axis

;;    $$\begin{matrix} {\begin{pmatrix} x \\ y \\ z \\ \end{pmatrix} =
;;    R_{z}(s)\,\begin{pmatrix} {x\prime} {y\prime} {z\prime} \\ \end{pmatrix}
;;    = \begin{pmatrix} {x\prime\cos s - y\prime\sin s} \\ {x\prime\sin s +
;;    y\prime\cos s} {z\prime} \end{pmatrix}.} & {(1.163)} \end{matrix}$$

;;    The rotation is an orthogonal transformation so

;;    \begin{equation}
;;    {x^{2} + y^{2} + z^{2} = {(x\prime)}^{2} + {(y\prime)}^{2} + {(z\prime)}^{2}.} & {(1.164)}
;;    \end{equation}

;;    Differentiating along a path, we get

;;    \begin{equation}
;;    {(v_{x},v_{y},v_{z}) = R_{z}(s)(v_{x}^{\prime},v_{y}^{\prime},v_{z}^{\prime}),} & {(1.165)}
;;    \end{equation}

;;    so the velocities also transform by an orthogonal transformation, and

;;    $v_{x}^{2} + v_{y}^{2} + v_{z}^{2} = {(v_{x}^{\prime})}^{2} +
;;    {(v_{y}^{\prime})}^{2} + {(v_{z}^{\prime})}^{2}$

;;    . Thus

;;    \begin{equation}
;;    {L\prime(t;x\prime,y\prime,z\prime;v_{x}^{\prime},v_{y}^{\prime},v_{z}^{\prime})} & \\
;;    {\,\,\,\,\,\,\,\,\,\, = \frac{1}{2}m\,\left( {{(v_{x}^{\prime})}^{2} + {(v_{y}^{\prime})}^{2} + {(v_{z}^{\prime})}^{2}} \right)} & \\
;;    {\,\,\,\,\,\,\,\,\,\,\,\,\,\, - U\left( \sqrt{{(x\prime)}^{2} + {(y\prime)}^{2} + {(z\prime)}^{2}} \right),} & {(1.166)}
;;    \end{equation}

;;    and we see that /L/′ is precisely the same function as /L/.

;;    The momenta are

;;    \begin{equation}
;;    {\partial_{2}L(t;x,y,z;v_{x},v_{y},v_{z}) = \lbrack mv_{x},mv_{y},mv_{z}\rbrack} & {(1.167)}
;;    \end{equation}

;;    and

;;    \begin{equation}
;;    {D\widetilde{F}(0)(t;x,y,z) = D{\widetilde{R}}_{z}(0)(x,y,z) = (y, - x,0).} & {(1.168)}
;;    \end{equation}

;;    So the Noether integral is

;;    $$\begin{array}{lll} {\mathcal{I}(t;x,y,z;v_{x},v_{y},v_{z})} & {=
;;    ((\partial_{2}L)(D\widetilde{F}(0)))(t;x,y,z;v_{x},v_{y},v_{z})} & \\
;;    & {= m(yv_{x} - xv_{y}),} & {(1.169)} \end{equation}



;;    which we recognize as minus the /z/ component of the angular momentum:

;;    $\overset{\rightarrow}{x} \times (m\overset{\rightarrow}{v})$

;;    . Since the Lagrangian is preserved by any continuous rotational symmetry,
;;    all components of the vector angular momenta are conserved for the
;;    central-potential problem.

;;    The procedure calls ((Rx angle-x) q), ((Ry angle-y) q), and ((Rz angle-z) q)
;;    rotate the rectangular tuple q about the indicated axis by the indicated
;;    angle.[fn:88] We use these to make a parametric coordinate transformation
;;    F-tilde:

;;     #+begin_src scheme
;; (define (F-tilde angle-x angle-y angle-z) (compose (Rx angle-x) (Ry angle-y) (Rz
;; angle-z) coordinate))
;;     #+end_src

;;    A Lagrangian for motion in a central potential is

;;     #+begin_src scheme
;; (define ((L-central-rectangular m U) state) (let ((q
;;    (coordinate state)) (v (velocity state))) (- (* 1/2 m (square v)) (U (sqrt
;;    (square q))))))
;;     #+end_src

;;    The Noether integral is then

;;     #+begin_src scheme
;; (define the-Noether-integral (let ((L
;;    (L-central-rectangular 'm (literal-function 'U)))) (* ((partial 2) L) ((D
;; F-tilde) 0 0 0)))) (the-Noether-integral (up 't (up 'x 'y 'z) (up 'vx 'vy
;; 'vz))) (down (+ (* m vy z) (* -1 m vz y)) (+ (* m vz x) (* -1 m vx z)) (+ (* m
;; vx y) (* -1 m vy x)))
;;     #+end_src

;;    We get all three components of the angular momentum.



;;    *Exercise 1.36: Noether integral*

;;    Consider motion on an ellipsoidal surface. The surface is specified by:

;;    $x^{2}/a^{2} + y^{2}/b^{2} + z^{2}/c^{2} = 1$

;;    Formulate a Lagrangian for frictionless motion on this surface. Assume that
;;    two of the axes of the ellipsoid are equal: /b/ = /c/.

;;    Using angular coordinates (/θ/, /ϕ/), where /θ/ is colatitude from the
;;    /ẑ/-axis, and /ϕ/ is longitude measured from the

;;    $\widehat{x}$

;;    -axis, formulate a Lagrangian that captures the symmetry of this ellipsoid:
;;    rotational symmetry around the

;;    $\widehat{x}$

;;    -axis. Formulate a parametric transformation that represents this symmetry
;;    and show that the Lagrangian you formulated is invariant under this
;;    transformation. Compute the Noether integral associated with this symmetry.

;;    Note that the choice of coordinates does not build in this symmetry.

;; * 1.9 Abstraction of Path Functions
;;   :PROPERTIES:
;;   :CUSTOM_ID: h1-6d
;;   :CLASS:    level1
;;   :END:
;;   An essential step in the derivation of the local-tuple transformation function
;;   /C/ from the coordinate transformation /F/ was the deduction of the
;;   relationship between the velocities in the two coordinate systems. We did this
;;   by inserting coordinate paths into the coordinate transformation function /F/,
;;   differentiating, and then generalizing the results on the path to arbitrary
;;   velocities at a moment. The last step is an example of a more general problem
;;   of abstracting a local-tuple function from a path function. Given a function
;;   /f/ of a local tuple, a corresponding path-dependent function

;;   $\overline{f}\lbrack q\rbrack\,\text{is}\,\overline{f}\lbrack q\rbrack = f
;;   \circ \Gamma\lbrack q\rbrack$

;;   . Given

;;   $\overline{f}$

;;   , how can we reconstitute /f/? The local-tuple function /f/ depends on only a
;;   finite number of components of the local tuple, and

;;   $\overline{f}$

;;   depends only on the corresponding local components of the path. So

;;   $\overline{f}$

;;   has the same value for all paths that have that number of components of the
;;   local tuple in common. Given

;;   $\overline{f}$

;;   we can reconstitute /f/ by taking the argument of /f/, which is a finite
;;   initial segment of a local tuple, constructing a path that has this local
;;   description, and finding the value of

;;   $\overline{f}$

;;   for this path.

;;   Two paths that have the same local description up to the /n/th derivative are
;;   said to /osculate with order n contact/. For example, a path and the truncated
;;   power series representation of the path up to order /n/ have order /n/
;;   contact; if fewer than /n/ derivatives are needed by a local-tuple function,
;;   the path and the truncated power series representation are equivalent. Let /O/
;;   be a function that generates an osculating path with the given local-tuple
;;   components. So /O/(/t/, /q/, /v/, ...)(/t/) = /q/, /D/(/O/(/t/, /q/, /v/,
;;   ...))(/t/) = /v/, and in general

;;   \begin{equation}
;;   {(t,q,v,\ldots) = \Gamma\lbrack O(t,q,v,\ldots)\rbrack(t).} & {(1.170)}
;;   \end{equation}

;;   The number of components of the local tuple that are required is finite, but
;;   unspecified. One way of constructing /O/ is through the truncated power series

;;   \begin{equation}
;;   {O(t,q,v,a,\ldots)(t\prime) = q + v(t\prime - t) + \frac{1}{2}a{(t\prime - t)}^{2} + \cdots,} & {(1.171)}
;;   \end{equation}

;;   where the number of terms is the same as the number of components of the local
;;   tuple that are specified.

;;   Given the path function

;;   $\overline{f}$

;;   we reconstitute the /f/ function as follows. We take the argument of /f/ and
;;   construct an osculating path with this local description. Then the value of
;;   /f/ is the value of

;;   $\overline{f}$

;;   for this osculating path:

;;   $$\begin{array}{lll} {f(t,q,v,\ldots)} & {= (f \circ \Gamma\lbrack
;;   O(t,q,v,\ldots)\rbrack)(t)} & \\
;;   & {= \overline{f}\lbrack O(t,q,v,\ldots)\rbrack(t).} & {(1.172)}
;;   \end{equation}

;;   Let

;;   $\overline{\Gamma}$

;;   be the function that takes a path function and returns the corresponding
;;   local-tuple function:[fn:89]

;;   \begin{equation}
;;   {f = \overline{\Gamma}(\overline{f}).} & {(1.173)}
;;   \end{equation}

;;   From equation ([[file:chapter001.html#disp_1.172][1.172]]) we see that

;;   \begin{equation}
;;   {\overline{\Gamma}(\overline{f})(t,q,v,\ldots) = \overline{f}\lbrack O(t,q,v,\ldots)\rbrack(t).} & {(1.174)}
;;   \end{equation}

;;   The procedure Gamma-bar implements the function

;;   $\overline{\Gamma}$

;;   that reconstitutes a path-dependent function into a local-tuple function:

;;    #+begin_src scheme
;; (define ((Gamma-bar f-bar) local) ((f-bar
;;   (osculating-path local)) (time local)))
;;    #+end_src



;;   The procedure osculating-path takes a number of local components and returns a
;;   path with these components; it is implemented as a power series.

;;   We can use Gamma-bar to construct the procedure F->C that takes a coordinate
;;   transformation F and generates the procedure that transforms local tuples. The
;;   procedure F->C constructs a path-dependent procedure f-bar that takes a
;;   coordinate path in the primed system and returns the local tuple of the
;;   corresponding path in the unprimed coordinate system. It then uses Gamma-bar
;;   to abstract f-bar to arbitrary local tuples in the primed coordinate
;;   system.[fn:90]

;;    #+begin_src scheme
;; (define (F->C F) (define (C local) (let ((n
;;   (vector-length local))) (define (f-bar q-prime) (define q (compose F (Gamma
;; q-prime))) (Gamma q n)) ((Gamma-bar f-bar) local))) C) (show-expression ((F->C
;; p->r) (up 't (up 'r 'theta) (up 'rdot 'thetadot))))
;;    #+end_src

;;   $$\begin{pmatrix} t \\ \begin{pmatrix} {r\cos\,(\theta)} {r\sin\,(\theta)}
;;   \\ \end{pmatrix} \begin{pmatrix} {- r\overset{˙}{\theta}\sin\,(\theta) +
;;   \overset{˙}{r}\cos\,(\theta)} {r\overset{˙}{\theta}\cos\,(\theta) +
;;   \overset{˙}{r}\sin\,(\theta)} \end{pmatrix} \end{pmatrix}$$



;;   Notice that in this definition of F->C we do not explicitly calculate any
;;   derivatives. The calculation that led up to the state transformation
;;   ([[file:chapter001.html#disp_1.77][1.77]]) is not needed.

;;   We can also use

;;   $\overline{\Gamma}$

;;   to make an elegant formula for computing the total time derivative /D_{t}F/ of
;;   the function /F/ :

;;   $$\begin{array}{llll} {D_{t}F = \overline{\Gamma}(\overline{G}),} &
;;   \text{with} & {\overline{G}\lbrack q\rbrack = D(F \circ \Gamma\lbrack
;;   q\rbrack).} & {(1.175)} \end{equation}

;;   The total time derivative can be expressed as a program:

;;    #+begin_src scheme
;; (define (Dt F) (define (DtF state) (let ((n
;;   (vector-length state))) (define (DF-on-path q) (D (compose F (Gamma q (- n
;; 1))))) ((Gamma-bar DF-on-path) state))) DtF)
;;    #+end_src

;;   Given a procedure F implementing a local-tuple function and a path q, we
;;   construct a new procedure (compose F (Gamma q)). The procedure DF-on-path
;;   implements the derivative of this function of time. We then abstract this off
;;   the path with Gamma-bar to give the total time derivative.

;;   *Exercise 1.37: Velocity transformation*

;;   Use the procedure Gamma-bar to construct a procedure that transforms
;;   velocities given a coordinate transformation. Apply this procedure to the
;;   procedure p->r to deduce (again) equation
;;   ([[file:chapter001.html#disp_1.67][1.67]]) on [[file:chapter001.html#p42][page
;;   42]].

;; ** Lagrange equations at a moment
;;    :PROPERTIES:
;;    :CUSTOM_ID: lagrange-equations-at-a-moment
;;    :CLASS:    level2
;;    :END:
;;    Given a Lagrangian, the Lagrange equations test paths to determine whether
;;    they are realizable paths of the system. The Lagrange equations relate the
;;    path and its derivatives. The fact that the Lagrange equations must be
;;    satisfied at each moment suggests that we can abstract the Lagrange equations
;;    off the path and write them as relations among the local-tuple components of
;;    realizable paths.

;;    Let

;;    $\overline{\mathcal{E}}$

;;    [/L/] be the path-dependent function that produces the residuals of the
;;    Lagrange equations ([[file:chapter001.html#disp_1.12][1.12]]) for the
;;    Lagrangian /L/:

;;    \begin{equation}
;;    {\overline{\mathcal{E}}\lbrack L\rbrack\lbrack q\rbrack = D(\partial_{2}L \circ \Gamma\lbrack q\rbrack) - \partial_{1}L \circ \Gamma\lbrack q\rbrack.} & {(1.176)}
;;    \end{equation}



;;    Realizable paths /q/ satisfy the Lagrange equations

;;    \begin{equation}
;;    {\overline{\mathcal{E}}\lbrack L\rbrack\lbrack q\rbrack = 0.} & {(1.177)}
;;    \end{equation}

;;    The path-dependent Lagrange equations can be converted to local Lagrange
;;    equations using

;;    $\overline{\Gamma}$

;;    :

;;    \begin{equation}
;;    {\mathcal{E}\lbrack L\rbrack = \overline{\Gamma}(\overline{\mathcal{E}}\lbrack L\rbrack).} & {(1.178)}
;;    \end{equation}

;;    The operator

;;    $\mathcal{E}$

;;    is called the /Euler--Lagrange operator/. In terms of this operator the
;;    Lagrange equations are

;;    \begin{equation}
;;    {\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack = 0.} & {(1.179)}
;;    \end{equation}

;;    The Euler--Lagrange operator is explicitly

;;    \begin{equation}
;;    {\mathcal{E}\lbrack L\rbrack = D_{t}\partial_{2}L - \partial_{1}L.} & {(1.180)}
;;    \end{equation}

;;    The procedure Euler-Lagrange-operator implements

;;    $\mathcal{E}$

;;    :

;;     #+begin_src scheme
;; (define (Euler-Lagrange-operator L) (- (Dt
;;    ((partial 2) L)) ((partial 1) L))).
;;     #+end_src

;;    For example, applied to the Lagrangian for the harmonic oscillator, we have

;;     #+begin_src scheme
;; ((Euler-Lagrange-operator (L-harmonic 'm 'k)) (up 't 'x 'v 'a)) (+ (* a m) (* k
;; x))
;;     #+end_src

;;    Notice that the components of the local tuple are individually specified.
;;    Using equation ([[file:chapter001.html#disp_1.179][1.179]]), the Lagrange
;;    equations for the harmonic oscillator are

;;     #+begin_src scheme
;; ((compose (Euler-Lagrange-operator (L-harmonic 'm 'k)) (Gamma (literal-function
;; 'x) 4)) 't) (+ (* k (x t)) (* m (((expt D 2) x) t)))
;;     #+end_src

;;    *Exercise 1.38: Properties of* E

;;    Let /F/ and /G/ be two Lagrangian-like functions of a local tuple, /C/ be a
;;    local-tuple transformation function, and /c/ a constant. Demonstrate the
;;    following properties:

;;    *a.*

;;    $\mathcal{E}\lbrack F + G\rbrack = \mathcal{E}\lbrack F\rbrack +
;;    \mathcal{E}\lbrack G\rbrack$



;;    *b.*

;;    $\mathcal{E}\lbrack cF\rbrack = c\mathcal{E}\lbrack F\rbrack$

;;    *c.*

;;    $\mathcal{E}\lbrack FG\rbrack = \mathcal{E}\lbrack F\rbrack G +
;;    F\mathcal{E}\lbrack G\rbrack + (D_{t}F)\partial_{2}G + \partial_{2}F(D_{t}G)$

;;    *d.*

;;    $\mathcal{E}\lbrack F \circ C\rbrack = D_{t}(DF \circ C)\partial_{2}C + DF
;;    \circ C\mathcal{E}\lbrack C\rbrack$

;; * 1.10 Constrained Motion
;;   :PROPERTIES:
;;   :CUSTOM_ID: h1-6e
;;   :CLASS:    level1
;;   :END:
;;   An advantage of the Lagrangian approach is that coordinates can often be
;;   chosen that exactly describe the freedom of the system, automatically
;;   incorporating any constraints. We may also use coordinates that have more
;;   freedom than the system actually has and consider explicit constraints among
;;   the coordinates. For example, the planar pendulum has a one-dimensional
;;   configuration space. We have formulated this problem using the angle from the
;;   vertical as the configuration coordinate. Alternatively, we may choose to
;;   represent the pendulum as a body moving in the plane, constrained to be on the
;;   circle of the correct radius around the pivot. We would like to have valid
;;   descriptions for both choices and show they are equivalent. In this section we
;;   develop tools to handle problems with explicit constraints. The constraints
;;   considered here are more general than those used in the demonstration that the
;;   Lagrangian for systems with rigid constraints can be written as the difference
;;   of kinetic and potential energies (see [[file:chapter001.html#h3_1-6-2][section 1.6.2]]).

;;   Suppose the configuration of a system with /n/ degrees of freedom is specified
;;   by /n/ + 1 coordinates and that configuration paths /q/ are constrained to
;;   satisfy some relation of the form

;;   \begin{equation}
;;   {\varphi(t,q(t),Dq(t)) = 0.} & {(1.181)}
;;   \end{equation}

;;   How do we formulate the equations of motion? One approach would be to use the
;;   constraint equation to eliminate one of the coordinates in favor of the rest;
;;   then the evolution of the reduced set of generalized coordinates would be
;;   described by the usual Lagrange equations. The equations governing the
;;   evolution of coordinates that are not fully independent should be equivalent.

;;   We can address the problem of formulating equations of motion for systems with
;;   redundant coordinates by returning to the action principle. Realizable paths
;;   are distinguished from other paths by having stationary action. Stationary
;;   refers to the fact that the action does not change with certain small
;;   variations of the path. What variations should be considered? We have seen
;;   that velocity-independent rigid constraints can be used to eliminate redundant
;;   coordinates. In the irredundant coordinates we distinguished realizable paths
;;   by using variations that by construction satisfy the constraints. Thus in the
;;   case where constraints can be used to eliminate redundant coordinates we can
;;   restrict the variations in the path to those that are consistent with the
;;   constraints.

;;   So how does the restriction of the possible variations affect the argument
;;   that led to Lagrange's equations (refer to [[file:chapter001.html#h1-6][section 1.5]])? Actually most of the
;;   calculation is unaffected. The condition that the action is stationary still
;;   reduces to the conditions ([[file:chapter001.html#disp_1.17][1.17]]) or ([[file:chapter001.html#disp_1.34][1.34]]):

;;   \begin{equation}
;;   {0 = {\int_{t_{1}}^{t_{2}}{\left\{ {(\partial_{1}L \circ \Gamma\lbrack q\rbrack) - D(\partial_{2}L \circ \Gamma\lbrack q\rbrack)} \right\}\eta.}}} & {(1.182)}
;;   \end{equation}

;;   At this point we argued that because the variations $\eta$ are arbitrary
;;   (except for conditions at the endpoints), the only way for the integral to be
;;   zero is for the integrand to be zero. Furthermore, the freedom in our choice
;;   of $\eta$ allowed us to deduce that the factor multiplying $\eta$ in the
;;   integrand must be identically zero, thereby deriving Lagrange's equations.

;;   Now the choice of $\eta$ is not completely free. We can still deduce from the
;;   arbitrariness of $\eta$ that the integrand must be zero,[fn:91] but we can no
;;   longer deduce that the factor multiplying $\eta$ is zero (only that the
;;   projection of this factor onto acceptable variations is zero). So we have

;;   \begin{equation}
;;   {\left\{ {(\partial_{1}L \circ \Gamma\lbrack q\rbrack) - D(\partial_{2}L \circ \Gamma\lbrack q\rbrack)} \right\}\eta = 0,} & {(1.183)}
;;   \end{equation}

;;   with $\eta$ subject to the constraints.

;;   A path /q/ satisfies the constraint if

;;   $\overline{\varphi}\lbrack q\rbrack = \varphi \circ \Gamma\lbrack q\rbrack =
;;   0$

;;   . The constraint must be satisfied even for the varied path, so we allow only
;;   variations $\eta$ for which the variation of the constraint is zero:

;;   \begin{equation}
;;   {\delta_{\eta}(\overline{\varphi}) = 0.} & {(1.184)}
;;   \end{equation}



;;   We can say that the variation must be “tangent” to the constraint surface.
;;   Expanding this with the chain rule, a variation $\eta$ is tangent to the
;;   constraint surface /φ/ if

;;   \begin{equation}
;;   {(\partial_{1}\varphi \circ \Gamma\lbrack q\rbrack)\,\eta + (\partial_{2}\varphi \circ \Gamma\lbrack q\rbrack)\, D\eta = 0.} & {(1.185)}
;;   \end{equation}

;;   Note that these are functions of time; the variation at a given time is
;;   tangent to the constraint at that time.

;; *** 1.10.1 Coordinate Constraints
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-10-1
;;     :CLASS:    level3
;;     :END:
;;     Consider constraints that do not depend on velocities:

;;     $\partial_{2}\varphi = 0.$

;;     In this case the variation is tangent to the constraint surface if

;;     \begin{equation}
;;     {(\partial_{1}\varphi \circ \Gamma)\,\,\,\eta = 0.} & {(1.186)}
;;     \end{equation}

;;     Together, equations ([[file:chapter001.html#disp_1.183][1.183]]) and ([[file:chapter001.html#disp_1.186][1.186]]) should determine the motion, but how
;;     do we eliminate $\eta$? The residual of the Lagrange equations is
;;     orthogonal[fn:92] to any $\eta$ that is orthogonal to the normal to the
;;     constraint surface. A vector that is orthogonal to all vectors orthogonal to
;;     a given vector is parallel to the given vector. Thus, the residual of
;;     Lagrange's equations is parallel to the normal to the constraint surface;
;;     the two must be proportional:

;;     \begin{equation}
;;     {D(\partial_{2}L \circ \Gamma\lbrack q\rbrack) - \partial_{1}L \circ \Gamma\lbrack q\rbrack = \lambda(\partial_{1}\varphi \circ \Gamma\lbrack q\rbrack).} & {(1.187)}
;;     \end{equation}

;;     That the two vectors are parallel everywhere along the path does not
;;     guarantee that the proportionality factor is the same at each moment along
;;     the path, so the proportionality factor /λ/ is some function of time, which
;;     may depend on the path under consideration. These equations, with the
;;     constraint equation /φ/ ∘ Γ[/q/] = 0, are the governing equations. These
;;     equations are sufficient to determine the path /q/ and to eliminate the
;;     unknown function /λ/.



;; ** Now watch this
;;    :PROPERTIES:
;;    :CUSTOM_ID: now-watch-this
;;    :CLASS:    level2
;;    :END:
;;    Suppose we form an augmented Lagrangian by treating /λ/ as one of the
;;    coordinates:

;;    \begin{equation}
;;    {L\prime(t;q,\lambda;\overset{˙}{q},\overset{˙}{\lambda}) = L(t,q,\overset{˙}{q}) + \lambda\varphi(t,q,\overset{˙}{q}).} & {(1.188)}
;;    \end{equation}

;;    The Lagrange equations associated with the coordinates /q/ are just the
;;    modified Lagrange equations ([[file:chapter001.html#disp_1.187][1.187]]), and the Lagrange equation associated
;;    with /λ/ is just the constraint equation. (Note that

;;    $\overset{˙}{\lambda}$

;;    does not appear in the augmented Lagrangian.) So the Lagrange equations for
;;    this augmented Lagrangian fully encapsulate the modification to the Lagrange
;;    equations that is imposed by the addition of an explicit coordinate
;;    constraint, at the expense of introducing extra degrees of freedom. Notice
;;    that this Lagrangian is of the same form as the Lagrangian (equation [[file:chapter001.html#disp_1.93][1.93]])
;;    that we used in the derivation of /L/ = /T/ − /V/ for rigid systems
;;    ([[file:chapter001.html#h3_1-6-2][section 1.6.2]]).

;; ** Alternatively
;;    :PROPERTIES:
;;    :CUSTOM_ID: alternatively
;;    :CLASS:    level2
;;    :END:
;;    How do we know that we have enough information to eliminate the unknown
;;    function /λ/ from equations ([[file:chapter001.html#disp_1.187][1.187]]), or
;;    that the extra degree of freedom introduced in Lagrangian
;;    ([[file:chapter001.html#disp_1.188][1.188]]) is purely formal?

;;    If /λ/ can be written as a composition of a state-dependent function with the
;;    path: /λ/ = Λ ∘ Γ[/q/] then it is redundant as a degree of freedom. Consider
;;    the Lagrangian

;;    \begin{equation}
;;    {L'' = L + \Lambda\varphi.} & {(1.189)}
;;    \end{equation}

;;    This new Lagrangian has no extra degrees of freedom. The Lagrange equations
;;    for /L/″ are the Lagrange equations for /L/ with additional terms arising
;;    from the product Λ/φ/. Applying the Euler--Lagrange operator

;;    $\mathcal{E}$

;;    (see [[file:chapter001.html#h1-6d][section 1.9]]) to this Lagrangian
;;    gives[fn:93]

;;    $$\begin{array}{lll} {\mathcal{E}\lbrack L''\rbrack} & {= \mathcal{E}\lbrack
;;    L\rbrack + \mathcal{E}\lbrack\Lambda\varphi\rbrack} & \\
;;    & {= \mathcal{E}\lbrack L\rbrack + \Lambda\mathcal{E}\lbrack\varphi\rbrack +
;;    \mathcal{E}\lbrack\Lambda\rbrack\varphi + D_{t}\Lambda\partial_{2}\varphi +
;;    \partial_{2}\Lambda D_{t}\varphi.} & {(1.190)} \end{equation}

;;    Composition of

;;    $\mathcal{E}$

;;    [/L/″] with Γ[/q/] gives the Lagrange equations for the path /q/. Using the
;;    fact that the constraint is satisfied on the path /φ/ ∘ Γ[/q/] = 0 and
;;    consequently /D_{t}φ/ ∘ Γ[/q/] = 0, we have



;;    \begin{equation} {\mathcal{E}\lbrack L''\rbrack \circ \Gamma\lbrack q\rbrack}
;;    & \\ {\text{       } = \mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack
;;    q\rbrack + \lambda(\mathcal{E}\lbrack\varphi\rbrack \circ \Gamma\lbrack
;;    q\rbrack) + D\lambda(\partial_{2}\varphi \circ \Gamma\lbrack q\rbrack),} &
;;    {(1.191)} \end{equation}

;;    where we have used /λ/ = Λ ∘ Γ[/q/]. If we now use the fact that we are
;;    dealing only with coordinate constraints, ∂_{2}/φ/ = 0, then

;;    \begin{equation}
;;    {\mathcal{E}\lbrack L''\rbrack \circ \Gamma\lbrack q\rbrack = \mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack + \lambda(\mathcal{E}\lbrack\varphi\rbrack \circ \Gamma\lbrack q\rbrack).} & {(1.192)}
;;    \end{equation}

;;    The Lagrange equations are the same as those derived from the augmented
;;    Lagrangian /L/′. The difference is that now we see that /λ/ = Λ ∘ Γ[/q/] is
;;    determined by the unaugmented state. This is the same as saying that /λ/ can
;;    be eliminated.

;;    Considering only the formal validity of the Lagrange equations for the
;;    augmented Lagrangian, we could not deduce that /λ/ could be written as the
;;    composition of a state-dependent function Λ with Γ[/q/]. The explicit
;;    Lagrange equations derived from the augmented Lagrangian depend on the
;;    accelerations /D/^{2}/q/ as well as /λ/, so we cannot deduce separately that
;;    either is the composition of a state-dependent function and Γ[/q/]. However,
;;    now we see that /λ/ is such a composition. This allows us to deduce that
;;    /D/^{2}/q/ is also a state-dependent function composed with the path. The
;;    evolution of the system is determined from the dynamical state.

;; ** The pendulum using constraints
;;    :PROPERTIES:
;;    :CUSTOM_ID: the-pendulum-using-constraints
;;    :CLASS:    level2
;;    :END:
;;    The pendulum can be formulated as the motion of a massive particle in a
;;    vertical plane subject to the constraint that the distance to the pivot is
;;    constant (see [[file:chapter001.html#Fig_1-8][figure 1.8]]).

;;    In this formulation, the kinetic and potential energies in the Lagrangian are
;;    those of an unconstrained particle in a uniform gravitational acceleration. A
;;    Lagrangian for the unconstrained particle is

;;    \begin{equation}
;;    {L(t;x,y;v_{x},v_{y}) = \frac{1}{2}m\left( v_{x}^{2} + v_{y}^{2} \right) - mgy.} & {(1.193)}
;;    \end{equation}

;;    The constraint that the pendulum moves in a circle of radius /l/ about the
;;    pivot is[fn:94]

;;    \begin{equation}
;;    {x^{2} + y^{2} - l^{2} = 0.} & {(1.194)}
;;    \end{equation}



;;    The augmented Lagrangian is

;;    \begin{equation}
;;    {L\prime(t;x,y,\lambda;v_{x},v_{y},\overset{˙}{\lambda}) = \frac{1}{2}m\left( v_{x}^{2} + v_{y}^{2} \right) - mgy + \lambda\left( x^{2} + y^{2} - l^{2} \right).} & {(1.195)}
;;    \end{equation}

;;    The Lagrange equations for the augmented Lagrangian are

;;    \begin{equation}
;;    {mD^{2}x - 2\lambda x = 0} & {(1.196)}
;;    \end{equation}

;;    \begin{equation}
;;    {mD^{2}y + mg - 2\lambda y = 0} & {(1.197)}
;;    \end{equation}

;;    \begin{equation}
;;    {x^{2} + y^{2} - l^{2} = 0.} & {(1.198)}
;;    \end{equation}

;;    These equations are sufficient to solve for the motion of the pendulum.

;;    It should not be surprising that these equations simplify if we switch to
;;    “polar” coordinates

;;    \begin{equation}
;;    {x = r\sin\theta\text{       }y = - r\cos\theta.} & {(1.199)}
;;    \end{equation}

;;    Substituting this into the constraint equation, we determine that /r/ = /l/,
;;    a constant. Forming the derivatives and substituting into the other two
;;    equations, we find

;;    \begin{equation}
;;    {ml(\cos\theta\, D^{2}\theta - \sin\theta\,{(D\theta)}^{2}) - 2\lambda\sin\theta = 0} & {(1.200)}
;;    \end{equation}

;;    \begin{equation}
;;    {ml(\sin\theta\, D^{2}\theta + \cos\theta\,{(D\theta)}^{2}) + mg + 2\lambda\cos\theta = 0.} & {(1.201)}
;;    \end{equation}

;;    Multiplying the first by cos /θ/ and the second by sin /θ/ and adding, we
;;    find

;;    \begin{equation}
;;    {mlD^{2}\theta + mg\sin\theta = 0,} & {(1.202)}
;;    \end{equation}

;;    which we recognize as the correct equation for the pendulum. This is the same
;;    as the Lagrange equation for the pendulum using the unconstrained generalized
;;    coordinate /θ/. For completeness, we can find /λ/ in terms of the other
;;    variables:

;;    \begin{equation}
;;    {\lambda = \frac{mD^{2}x}{2x} = - \frac{1}{2l}(mg\cos\theta + ml{(D\theta)}^{2}).} & {(1.203)}
;;    \end{equation}

;;    This confirms that /λ/ is really the composition of a function of the state
;;    with the state path. Notice that 2/lλ/ is a force---it is the sum of the
;;    outward component of the gravitational force and the centrifugal force. Using
;;    this interpretation in the two coordinate equations of motion, we see that
;;    the terms involving /λ/ are the forces that must be applied to the
;;    unconstrained particle to make it move on the circle required by the
;;    constraints. Equivalently, we may think of 2/lλ/ as the tension in the
;;    pendulum rod that holds the mass.[fn:95]

;;    #+caption: *Figure 1.8* We can formulate the behavior of a pendulum as
;;    motion in the plane, constrained to a circle about the pivot.
;;    [[file:images/Art_P316.jpg]]

;; ** Building systems from parts
;;    :PROPERTIES:
;;    :CUSTOM_ID: building-systems-from-parts
;;    :CLASS:    level2
;;    :END:
;;    The method of using augmented Lagrangians to enforce constraints on dynamical
;;    systems provides a way to analyze a compound system by combining the results
;;    of the analysis of the parts of the system and the coupling between them.

;;    Consider the compound spring-mass system shown at the top of [[file:chapter001.html#Fig_1-9][figure 1.9]]. We
;;    could analyze this as a monolithic system with two configuration coordinates
;;    /x/_{1} and /x/_{2}, representing the extensions of the springs from their
;;    equilibrium lengths /X/_{1} and /X/_{2}.

;;    An alternative procedure is to break the system into several parts. In our
;;    spring-mass system we can choose two parts: one is a spring and mass attached
;;    to the wall, and the other is a spring and mass with its attachment point at
;;    an additional configuration coordinate $\xi$. We can formulate a Lagrangian
;;    for each part separately. We can then choose a Lagrangian for the composite
;;    system as the sum of the two component Lagrangians with a constraint $\xi$ =
;;    /X/_{1} + /x/_{1} to accomplish the coupling.



;;    Let's see how this works. The Lagrangian for the subsystem attached to the
;;    wall is

;;    \begin{equation}
;;    {L_{1}(t,x_{1},{\overset{˙}{x}}_{1}) = \frac{1}{2}m_{1}{\overset{˙}{x}}_{1}^{2} - \frac{1}{2}k_{1}x_{1}^{2}} & {(1.204)}
;;    \end{equation}

;;    and the Lagrangian for the subsystem that attaches to it is

;;    \begin{equation}
;;    {L_{2}(t;\xi,x_{2};\overset{˙}{\xi},{\overset{˙}{x}}_{2}) = \frac{1}{2}m_{2}{(\overset{˙}{\xi} + {\overset{˙}{x}}_{2})}^{2} - \frac{1}{2}k_{2}x_{2}^{2}.} & {(1.205)}
;;    \end{equation}

;;    We construct a Lagrangian for the system composed from these parts as a sum
;;    of the Lagrangians for each of the separate parts, with a coupling term to
;;    enforce the constraint:

;;    \begin{equation}
;;    {L(t;x_{1},x_{2},\xi,\lambda;{\overset{˙}{x}}_{1},{\overset{˙}{x}}_{2},\overset{˙}{\xi},\overset{˙}{\lambda})} & \\
;;    {\text{         } = L_{1}(t,x_{1},{\overset{˙}{x}}_{1}) + L_{2}(t;\xi,x_{2};\overset{˙}{\xi},{\overset{˙}{x}}_{2})} & \\
;;    {\text{         }\text{         } + \lambda(\xi - (X_{1} + x_{1})).} & {(1.206)}
;;    \end{equation}

;;    Thus we can write Lagrange's equations for the four configuration
;;    coordinates, in order, as follows:

;;    $$\begin{aligned} {m_{1}D^{2}x_{1} = - k_{1}x_{1} - \lambda} & {(1.207)}
;;    \\ \end{aligned}$$

;;    $$\begin{aligned} {m_{2}(D^{2}\xi + D^{2}x_{2}) = - k_{2}x_{2}} & {(1.208)}
;;    \\ \end{aligned}$$

;;    $$\begin{aligned} {m_{2}(D^{2}\xi + D^{2}x_{2}) = \lambda} & {(1.209)}
;;    \\ \end{aligned}$$

;;    $$\begin{aligned} {0 = \xi - (X_{1} + x_{1}).} & {(1.210)} \end{aligned}$$

;;    Notice that in this system /λ/ is the force of constraint holding the system
;;    together. We can now eliminate the “glue” coordinates $\xi$ and /λ/ to obtain
;;    the equations of motion in the coordinates /x/_{1} and /x/_{2}:

;;    \begin{equation}
;;    {m_{1}D^{2}x_{1} + m_{2}(D^{2}x_{1} + D^{2}x_{2}) + k_{1}x_{1} = 0} & {(1.211)}
;;    \end{equation}

;;    \begin{equation}
;;    {m_{2}(D^{2}x_{1} + D^{2}x_{2}) + k_{2}x_{2} = 0.} & {(1.212)}
;;    \end{equation}

;;    This strategy can be generalized. We can make a library of primitive
;;    components. Each component may be characterized by a Lagrangian with
;;    additional degrees of freedom for the /terminals/ where that component may be
;;    attached to others. We then can construct composite Lagrangians by combining
;;    components, using constraints to glue together the terminals.



;;    #+caption: *Figure 1.9* A compound spring-mass system is decomposed into
;;    two subsystems. We have two springs coupling two masses that can move
;;    horizontally. The equilibrium positions of the springs are /X/_{1} and
;;    /X/_{2}. The systems are coupled by the coordinate constraint /ξ/ = /X/_{1} +
;;    /x/_{1}. [[file:images/Art_P326.jpg]]

;;    *Exercise 1.39: Combining Lagrangians*

;;    *a.* Make another primitive component, compatible with the spring-mass
;;    structures described in this section. For example, make a pendulum that can
;;    attach to the spring-mass system. Build a combination and derive the
;;    equations of motion. Be careful, the algebra is horrible if you choose bad
;;    coordinates.

;;    *b.* For a nice little project, construct a family of compatible mechanical
;;    parts, characterized by appropriate Lagrangians, that can be combined in a
;;    variety of ways to make interesting mechanisms. Remember that in a good
;;    language the result of combining pieces should be a piece of the same kind
;;    that can be further combined with other pieces.



;;    *Exercise 1.40: Bead on a triaxial surface*

;;    Consider again the motion of a bead constrained to move on a triaxial surface
;;    ([[file:chapter001.html#Exe_1-18][exercise 1.18]]). Reformulate this using rectangular coordinates as the
;;    generalized coordinates with an explicit constraint that the bead must stay
;;    on the surface. Find a Lagrangian and show that the Lagrange equations are
;;    equivalent to those found in [[file:chapter001.html#Exe_1-18][exercise 1.18]].

;;    *Exercise 1.41: Motion of a tiny golf ball*

;;    Consider the motion of a golf ball idealized as a point mass constrained to a
;;    frictionless smooth surface of varying height /h/(/x/, /y/) in a uniform
;;    gravitational field with acceleration /g/.

;;    *a.* Find an augmented Lagrangian for this system, and derive the equations
;;    governing the motion of the point mass in /x/ and /y/.

;;    *b.* Under what conditions is this approximated by a potential function /V/
;;    (/x/, /y/) = /mgh/(/x/, /y/)?

;;    *c.* Assume that /h/(/x/, /y/) is axisymmetric about /x/ = /y/ = 0. Can you
;;    find such an /h/ that yields motions with closed orbits?

;; *** 1.10.2 Derivative Constraints
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-10-2
;;     :CLASS:    level3
;;     :END:
;;     Here we investigate velocity-dependent constraints that are “total time
;;     derivatives” of velocity-independent constraints. The methods presented so
;;     far do not apply because the constraint is velocity-dependent.

;;     Consider a velocity-dependent constraint /ψ/ = 0. That /ψ/ is a total time
;;     derivative means that there exists a velocity-independent function /φ/ such
;;     that

;;     \begin{equation}
;;     {\psi \circ \Gamma\lbrack q\rbrack = D(\varphi \circ \Gamma\lbrack q\rbrack).} & {(1.213)}
;;     \end{equation}

;;     That /φ/ is velocity-independent means ∂_{2}/φ/ = 0. As state functions the
;;     relationship between /ψ/ and /φ/ is

;;     \begin{equation}
;;     {\psi = D_{t}\varphi = \partial_{0}\varphi + \partial_{1}\varphi\overset{˙}{Q}.} & {(1.214)}
;;     \end{equation}

;;     Given a /ψ/ we can find /φ/ by solving this linear partial differential
;;     equation. The solution is determined up to a constant, so /ψ/ = 0 implies
;;     /φ/ = /K/ for some constant /K/. On the other hand, if we knew /φ/ = /K/
;;     then /ψ/ = 0 follows. Thus the velocity-dependent constraint /ψ/ = 0 is
;;     equivalent to the velocity-independent constraint /φ/ = /K/, and we know how
;;     to find Lagrange equations for such systems.



;;     If /L/ is a Lagrangian for the unconstrained problem, the Lagrange equations
;;     with the constraint /φ/ = /K/ are

;;     \begin{equation}
;;     {\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack + \lambda\,(\mathcal{E}\lbrack\varphi\rbrack \circ \Gamma\lbrack q\rbrack) = 0,} & {(1.215)}
;;     \end{equation}

;;     where /λ/ is a function of time that will be eliminated during the solution
;;     process. The constant /K/ does not affect the Lagrange equations. The
;;     function /φ/ is independent of velocity, ∂_{2}/φ/ = 0, so the Lagrange
;;     equations become

;;     \begin{equation}
;;     {\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack - \lambda(\partial_{1}\varphi \circ \Gamma\lbrack q\rbrack) = 0.} & {(1.216)}
;;     \end{equation}

;;     From equation ([[file:chapter001.html#disp_1.214][1.214]]) we see that

;;     \begin{equation}
;;     {\partial_{1}\varphi = \partial_{2}\psi,} & {(1.217)}
;;     \end{equation}

;;     so the Lagrange equations with the constraint /ψ/ = 0 are

;;     \begin{equation}
;;     {\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack = \lambda(\partial_{2}\psi \circ \Gamma\lbrack q\rbrack).} & {(1.218)}
;;     \end{equation}

;;     The important feature is that we can write the Lagrange equations directly
;;     in terms of /ψ/ without having to produce /φ/. But the validity of these
;;     Lagrange equations depends on the existence of /φ/.

;;     It turns out that the augmented Lagrangian trick also works here. These
;;     Lagrange equations are given if we augment the Lagrangian with the
;;     constraint /ψ/ multiplied by a function of time /λ/′:

;;     \begin{equation}
;;     {L\prime = L + \lambda\prime\psi.} & {(1.219)}
;;     \end{equation}

;;     The Lagrange equations for /L/′ turn out to be

;;     \begin{equation}
;;     {\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack = - D\lambda\prime(\partial_{2}\psi \circ \Gamma\lbrack q\rbrack),} & {(1.220)}
;;     \end{equation}

;;     which, with the identification /λ/ = −/Dλ/′, are the same as Lagrange
;;     equations ([[file:chapter001.html#disp_1.218][1.218]]).

;;     Sometimes a problem can be naturally formulated in terms of
;;     velocity-dependent constraints. The formalism we have developed will handle
;;     any velocity-dependent constraint that can be written in terms of the
;;     derivative of a coordinate constraint. Such a constraint is called an
;;     /integrable constraint/. Any system for which the constraints can be put in
;;     the form of a coordinate constraint, or are already in that form, is called
;;     a /holonomic system/.



;;     #+caption: *Figure 1.10* A massive hoop rolling, without slipping, down
;;     an inclined plane. [[file:images/Art_P335.jpg]]

;;     *Exercise 1.42: Augmented Lagrangian*

;;     Show that the augmented Lagrangian
;;     ([[file:chapter001.html#disp_1.219][1.219]]) does lead to the Lagrange
;;     equations ([[file:chapter001.html#disp_1.220][1.220]]), taking into account
;;     the fact that /ψ/ is a total time derivative of /φ/.

;; ** Goldstein's hoop
;;    :PROPERTIES:
;;    :CUSTOM_ID: goldsteins-hoop
;;    :CLASS:    level2
;;    :END:
;;    Here we consider a problem for which the constraint can be represented as a
;;    time derivative of a coordinate constraint: a hoop of mass /M/ and radius /R/
;;    rolling, without slipping, down a (one-dimensional) inclined plane (see
;;    [[file:chapter001.html#Fig_1-10][figure 1.10]]).[fn:96]

;;    We will formulate this problem in terms of the two coordinates /θ/, the
;;    rotation of an arbitrary point on the hoop from an arbitrary reference
;;    direction, and /x/, the linear progress down the inclined plane. The
;;    constraint is that the hoop does not slip. Thus a change in /θ/ is exactly
;;    reflected in a change in /x/; the constraint function is

;;    \begin{equation}
;;    {\psi(t;x,\theta;\overset{˙}{x},\overset{˙}{\theta}) = R\overset{˙}{\theta} - \overset{˙}{x}.} & {(1.221)}
;;    \end{equation}

;;    This constraint is phrased as a relation among generalized velocities, but it
;;    could be integrated to get /x/ = /Rθ/ + /c/. We may form our augmented
;;    Lagrangian with either the integrated constraint or its derivative.



;;    The kinetic energy has two parts, the energy of rotation of the hoop and the
;;    energy of the motion of its center of mass.[fn:97] The potential energy of
;;    the hoop decreases as the height decreases. Thus we may write the augmented
;;    Lagrangian:

;;    \begin{equation}
;;    {L(t;x,\theta,\lambda;\overset{˙}{x},\overset{˙}{\theta},\lambda)} & \\
;;    {\,\,\,\,\,\,\,\, = \frac{1}{2}M\, R^{2}{\overset{˙}{\theta}}^{2} + \frac{1}{2}M\,{\overset{˙}{x}}^{2} + M\, gx\sin\varphi + \lambda(R\overset{˙}{\theta} - \overset{˙}{x}).} & {(1.222)}
;;    \end{equation}

;;    Lagrange's equations are

;;    \begin{equation}
;;    {M\, D^{2}x - D\lambda = M\, g\sin\varphi} & {(1.223)}
;;    \end{equation}

;;    \begin{equation}
;;    {M\, R^{2}D^{2}\theta + R\, D\lambda = 0} & {(1.224)}
;;    \end{equation}

;;    \begin{equation}
;;    {R\, D\theta - Dx = 0.} & {(1.225)}
;;    \end{equation}

;;    And by differentiation of the third Lagrange equation we obtain

;;    \begin{equation}
;;    {D^{2}x = RD^{2}\theta.} & {(1.226)}
;;    \end{equation}

;;    By combining these equations we can solve for the dynamical quantities of
;;    interest. For this case of a rolling hoop the linear acceleration

;;    \begin{equation}
;;    {D^{2}x = \frac{1}{2}g\sin\varphi} & {(1.227)}
;;    \end{equation}

;;    is just half of what it would have been if the mass had just slid down a
;;    frictionless plane without rotating. Note that for this hoop /D/^{2}/x/ is
;;    independent of both /M/ and /R/. We see from the Lagrange equations that /Dλ/
;;    can be interpreted as the friction force involved in enforcing the
;;    constraint. The frictional force of constraint is

;;    \begin{equation}
;;    {D\lambda = \frac{1}{2}M\, g\sin\varphi} & {(1.228)}
;;    \end{equation}

;;    and the angular acceleration is

;;    \begin{equation}
;;    {D^{2}\theta = \frac{1}{2}\frac{g}{R}\sin\varphi.} & {(1.229)}
;;    \end{equation}



;; *** 1.10.3 Nonholonomic Systems
;;     :PROPERTIES:
;;     :CUSTOM_ID: h3_1-10-3
;;     :CLASS:    level3
;;     :END:
;;     Systems with constraints that are not integrable are termed /non-holonomic
;;     systems/. A constraint is not integrable if it cannot be written in terms of
;;     an equivalent coordinate constraint. An example of a nonholonomic system is
;;     a ball rolling without slipping in a bowl. As the ball rolls it must turn so
;;     that its surface does not move relative to the bowl at the point of contact.
;;     This looks as if it might establish a relation between the location of the
;;     ball in the bowl and the orientation of the ball, but it doesn't. The ball
;;     may return to the same place in the bowl with different orientations
;;     depending on the intervening path it has taken. As a consequence, the
;;     constraints cannot be used to eliminate any coordinates.

;;     What are the equations of motion governing nonholonomic systems? For the
;;     restricted set of systems with nonholonomic constraints that are linear in
;;     the velocities, it is widely reported[fn:98] that the equations of motion
;;     are as follows. Let /ψ/ have the form

;;     \begin{equation}
;;     {\psi(t,q,v) = G_{1}(t,q)v + G_{2}(t,q),} & {(1.230)}
;;     \end{equation}

;;     a state function that is linear in the velocities. We assume /ψ/ is not a
;;     total time derivative. If /L/ is a Lagrangian for the unconstrained system,
;;     then the equations of motion are asserted to be

;;     \begin{equation}
;;     {\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack = \lambda(G_{1} \circ \Gamma\lbrack q\rbrack) = \lambda(\partial_{2}\psi \circ \Gamma\lbrack q\rbrack).} & {(1.231)}
;;     \end{equation}

;;     With the constraint /ψ/ = 0, the system is completely specified and the
;;     evolution of the system is determined. Note that these equations are
;;     identical to the Lagrange equations ([[file:chapter001.html#disp_1.218][1.218]]) for the case that /ψ/ is a total
;;     time derivative, but here the derivation of those equations is no longer
;;     valid.

;;     An essential step in the derivation of the Lagrange equations for coordinate
;;     constraints /φ/ = 0 with ∂_{2}/φ/ = 0 was to note that two conditions must
;;     be satisfied:

;;     $$(\begin{equation} {\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack
;;     q\rbrack)\eta = 0,} & {(1.232)} \end{equation}

;;     and

;;     $$(\begin{equation} {\partial_{1}\varphi \circ \Gamma\lbrack q\rbrack)\eta =
;;     0.} & {(1.233)} \end{equation}



;;     Because

;;     $\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack$

;;     is orthogonal to $\eta$ and $\eta$ is constrained to be orthogonal to
;;     ∂_{1}/φ/ ∘ Γ[/q/], the two must be parallel at each moment:

;;     \begin{equation}
;;     {\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack = \lambda(\partial_{1}\varphi \circ \Gamma\lbrack q\rbrack).} & {(1.234)}
;;     \end{equation}

;;     The Lagrange equations for derivative constraints were derived from this.

;;     This derivation does not go through if the constraint function depends on
;;     velocity. In this case, for a variation $\eta$ to be consistent with the
;;     velocity-dependent constraint function /ψ/ it must satisfy (see equation
;;     [[file:chapter001.html#disp_1.185][1.185]])

;;     \begin{equation}
;;     {(\partial_{1}\psi \circ \Gamma\lbrack q\rbrack)\eta + (\partial_{2}\psi \circ \Gamma\lbrack q\rbrack)D\eta = 0.} & {(1.235)}
;;     \end{equation}

;;     We may no longer eliminate $\eta$ by the same argument, because $\eta$ is no
;;     longer orthogonal to ∂_{1}/ψ/ ∘ Γ[/q/], and we cannot rewrite the constraint
;;     as a coordinate constraint because /ψ/ is, by assumption, not integrable.

;;     The following is the derivation of the nonholonomic equations from Arnold et
;;     al. [[[file:bibliography.html#bib_6][6]]], translated into our notation. Define a “virtual velocity” $\xi$ to
;;     be any velocity satisfying

;;     $$(\begin{equation} {\partial_{2}\psi \circ \Gamma\lbrack q\rbrack)\xi = 0.}
;;     & {(1.236)} \end{equation}

;;     The “principle of d'Alembert--Lagrange,” according to Arnold, states that

;;     \begin{equation}
;;     {(\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack)\xi = 0,} & {(1.237)}
;;     \end{equation}

;;     for any virtual velocity $\xi$. Because $\xi$ is arbitrary except that it is
;;     required to be orthogonal to ∂_{2}/ψ/ ∘ Γ[/q/] and any such $\xi$ is
;;     orthogonal to

;;     $\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack$

;;     , then ∂_{2}/ψ/ ∘ Γ[/q/] must be parallel to

;;     $\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack$

;;     . So

;;     \begin{equation}
;;     {\mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack = \lambda(\partial_{2}\psi \circ \Gamma\lbrack q\rbrack),} & {(1.238)}
;;     \end{equation}

;;     which are the nonholonomic equations.

;;     To convert the stationary action equations to the equations of Arnold we
;;     must do the following. To get from equation ([[file:chapter001.html#disp_1.232][1.232]]) to equation ([[file:chapter001.html#disp_1.237][1.237]]), we
;;     must replace $\eta$ by $\xi$. However, to get from equation ([[file:chapter001.html#disp_1.235][1.235]]) to
;;     equation ([[file:chapter001.html#disp_1.236][1.236]]), we must set $\eta = 0$ and replace /Dη/ by $\xi$. All
;;     “derivations” of the nonholonomic equations have similar identifications. It
;;     comes down to this: the nonholonomic equations do not follow from the action
;;     principle. They are something else. Whether they are correct or not depends
;;     on whether or not they agree with experiment.

;;     For systems with either coordinate constraints or derivative constraints, we
;;     have found that the Lagrange equations can be derived from a Lagrangian that
;;     is augmented with the constraint. However, if the constraints are not
;;     integrable the Lagrange equations for the augmented Lagrangian are not the
;;     same as the non-holonomic system (equations [[file:chapter001.html#disp_1.231][1.231]]).[fn:99] Let /L/′ be an
;;     augmented Lagrangian with non-integrable constraint /ψ/:

;;     \begin{equation}
;;     {L\prime(t;q,\lambda;\overset{˙}{q},\overset{˙}{\lambda}) = L(t,q,\overset{˙}{q}) + \lambda\psi(t,q,\overset{˙}{q});} & {(1.239)}
;;     \end{equation}

;;     then the Lagrange equations associated with the coordinates are

;;     \begin{equation}
;;     {0 = \mathcal{E}\lbrack L\rbrack \circ \Gamma\lbrack q\rbrack} & \\
;;     {\,\,\,\,\, + D\lambda(\partial_{2}\psi \circ \Gamma\lbrack q\rbrack) + \lambda D(\partial_{2}\psi \circ \Gamma\lbrack q\rbrack) - \lambda(\partial_{1}\psi \circ \Gamma\lbrack q\rbrack).} & {(1.240)}
;;     \end{equation}

;;     The Lagrange equation associated with /λ/ is just the constraint equation

;;     \begin{equation}
;;     {\psi \circ \Gamma\lbrack q\rbrack = 0.} & {(1.241)}
;;     \end{equation}

;;     An interesting feature of these equations is that they involve both /λ/ and
;;     /Dλ/. Thus the usual state variables /q/ and /Dq/, with the constraint, are
;;     not sufficient to determine a full set of initial conditions for the derived
;;     Lagrange equations; we need to specify an initial value for /λ/ as well.

;;     In general, for any particular physical system, equations ([[file:chapter001.html#disp_1.231][1.231]]) and
;;     ([[file:chapter001.html#disp_1.240][1.240]]) are not the same, and in fact they have different solutions. It is
;;     not apparent that either set of equations accurately models the physical
;;     system. The first approach to nonholonomic systems is not justified by
;;     extension of the arguments for the holo-nomic case and the other is not
;;     fully determined. Perhaps this indicates that the models are inadequate,
;;     that more details of how the constraints are maintained need to be
;;     specified.



;; * 1.11 Summary
;;   :PROPERTIES:
;;   :CUSTOM_ID: h1-6f
;;   :CLASS:    level1
;;   :END:
;;   To analyze a mechanical system we construct an action function that gives us a
;;   way to distinguish realizable motions from other conceivable motions of the
;;   system. The action function is constructed so as to be stationary only on
;;   paths describing realizable motions, with respect to variations of the path.
;;   This is the /principle of stationary action/. The principle of stationary
;;   action is a coordinate-independent specification of the realizable paths. For
;;   systems with or without constraints we may choose any system of coordinates
;;   that uniquely determines the configuration of the system.

;;   An action is an integral of a function, the /Lagrangian/, along the path. For
;;   many systems an appropriate Lagrangian is the difference of the kinetic energy
;;   and the potential energy of the system. The choice of a Lagrangian for a
;;   system is not unique.

;;   For any system for which we have a Lagrangian action we can formulate a system
;;   of ordinary differential equations, the Lagrange equations, that is satisfied
;;   by any realizable path. The method of deriving the Lagrange equations from the
;;   Lagrangian is independent of the coordinate system used to formulate the
;;   Lagrangian. One freedom we have in formulation is that the addition of a total
;;   time derivative to a Lagrangian for a system yields another Lagrangian that
;;   has the same Lagrange equations.

;;   The Lagrange equations are a set of ordinary differential equations: there is
;;   a finite state that summarizes the history of the system and is sufficient to
;;   determine the future. There is an effective procedure for evolving the motion
;;   of the system from a state at an instant. For many systems the state is
;;   determined by the coordinates and the rate of change of the coordinates at an
;;   instant.

;;   If there are continuous symmetries in a physical system there are conserved
;;   quantities associated with them. If the system can be formulated in such a way
;;   that the symmetries are manifest in missing coordinates in the Lagrangian,
;;   then there are conserved momenta conjugate to those coordinates. If the
;;   Lagrangian is independent of time then there is a conserved energy.



;; * 1.12 Projects
;;   :PROPERTIES:
;;   :CUSTOM_ID: h1-6g
;;   :CLASS:    level1
;;   :END:
;;   *Exercise 1.43: A numerical investigation*

;;   Consider a pendulum: a mass /m/ supported on a massless rod of length /l/ in a
;;   uniform gravitational field. A Lagrangian for the pendulum is

;;   $L(t,\theta,\overset{˙}{\theta}) = \frac{m}{2}{(l\overset{˙}{\theta})}^{2} +
;;   mgl\cos\theta.$

;;   For the pendulum, the period of the motion depends on the amplitude. We wish
;;   to find trajectories of the pendulum with a given frequency. Three methods of
;;   doing this present themselves: (1) solution by the principle of least action,
;;   (2) numerical integration of Lagrange's equation, and (3) analytic solution
;;   (which requires some exposure to elliptic functions). We will carry out all
;;   three and compare the solution trajectories.

;;   Consider the parameters /m/ = 1 kg, /l/ = 1 m, /g/ = 9.8 m s^{−2}. The
;;   frequency of small-amplitude oscillations is

;;   $\omega_{0} = \sqrt{g/l}$

;;   . Let's find the nontrivial solution that has the frequency

;;   $\omega_{1} = \frac{4}{5}\omega_{0}$

;;   .

;;   *a.* The angle is periodic in time, so a Fourier series representation is
;;   appropriate. We can choose the origin of time so that a zero crossing of the
;;   angle is at time zero. Since the potential is even in the angle, the angle is
;;   an odd function of time. Thus we need only a sine series. Since the angle
;;   returns to zero after one-half period, the angle is an odd function of time
;;   about the midpoint. Thus only odd terms of the series are present:

;;   $\theta(t) = {\sum\limits_{n = 1}^{m}{A_{n}\sin((2n - 1)\omega_{1}t).}}$

;;   The amplitude of the trajectory is

;;   $A = \theta_{\max} = {\sum_{n = 1}^{\infty}{{( - 1)}^{n + 1}A_{n}}}$

;;   .

;;   Find approximations to the first few coefficients /A/_{/n/} by minimizing the
;;   action. You will have to write a program similar to the find-path procedure in
;;   [[file:chapter001.html#h1-5][section 1.4]]. Watch out: there is more than one
;;   trajectory that minimizes the action.

;;   *b.* Write a program to numerically integrate Lagrange's equations for the
;;   trajectories of the pendulum. The trouble with using numerical integration to
;;   solve this problem is that we do not know how the frequency of the motion
;;   depends on the initial conditions. So we have to guess, and then gradually
;;   improve our guess. Define a function

;;   $\Omega(\overset{˙}{\theta})$

;;   that numerically computes the frequency of the motion as a function of the
;;   initial angular velocity (with /θ/ = 0). Find the trajectory by solving

;;   $\Omega(\overset{˙}{\theta}) = \omega$

;;   for the initial angular velocity of the desired trajectory. Methods of solving
;;   this equation include successive bisection, minimizing the squared residual,
;;   etc.---choose one.



;;   *c.* Now let's formulate the analytic solution for the frequency as a function
;;   of amplitude. The period of the motion is simply

;;   $T = 4{\int_{0}^{T/4}{dt =
;;   4}}{\int_{0}^{A}\frac{1}{\overset{˙}{\theta}}}d\theta.$

;;   Using the energy, solve for

;;   $\overset{˙}{\theta}$

;;   in terms of the amplitude /A/ and /θ/ to write the required integral
;;   explicitly. This integral can be written in terms of elliptic functions, but
;;   in a sense this does not solve the problem---we still have to compute the
;;   elliptic functions. Let's avoid this excursion into elliptic functions and
;;   just do the integral numerically using the procedure definite-integral. We
;;   still have the problem that we can specify the amplitude /A/ and get the
;;   frequency; to solve our problem we need to solve the inverse problem, but that
;;   can be done as in part *b*.

;;   *Exercise 1.44: Double pendulum behavior*

;;   Consider the ideal double pendulum shown in
;;   [[file:chapter001.html#Fig_1-11][figure 1.11]].

;;   *a.* Formulate a Lagrangian to describe the dynamics. Derive the equations of
;;   motion in terms of the given angles /θ/_{1} and /θ/_{2}. Put the equations
;;   into a form appropriate for numerical integration. Assume the following system
;;   parameters:

;;   | /g/     | = | 9.8 m s^{−2} |
;;   | /l/_{1} | = | 1.0 m        |
;;   | /l/_{2} | = | 0.9 m        |
;;   | /m/_{1} | = | 1.0 kg       |
;;   | /m/_{2} | = | 3.0 kg       |

;;   *b.* Prepare graphs showing the behavior of each angle as a function of time
;;   when the system is started with the following initial conditions:

;;   | /θ/_{1}(0)                     | = | /π//2 rad    |
;;   | /θ/_{2}(0)                     | = | /π/ rad      |
;;   | ${\overset{˙}{\theta}}_{1}(0)$ | = | 0 rad s^{−1} |
;;   | ${\overset{˙}{\theta}}_{2}(0)$ | = | 0 rad s^{−1} |

;;   Make the graphs extend to 50 seconds.

;;   *c.* Make a graph of the behavior of the energy of your system as a function
;;   of time. The energy should be conserved. How good is the conservation you
;;   obtained?

;;   *d.* Make a new Lagrangian, for two identical uncoupled double pendulums.
;;   (Both pendulums should have the same masses and lengths.) Your new Lagrangian
;;   should have four degrees of freedom. Give initial conditions for one pendulum
;;   to be the same as in the experiment of part *b* and give initial conditions
;;   for the other pendulum with the /m/_{2} bob 10^{−10} m higher than before. The
;;   motions of the two pendulums will diverge as time progresses. Plot the
;;   logarithm of the absolute value of the difference of the positions of the
;;   /m/_{2} bobs in your two pendulums against the time. What do you see?



;;   #+caption: *Figure 1.11* The double pendulum is pinned in two joints so
;;   that its members are free to move in a plane. [[file:images/Art_P363.jpg]]

;;   *e.* Repeat the previous comparison, but this time use the base initial
;;   conditions:

;;   | /θ/_{1}(0)                     | = | /π//2 rad    |
;;   | /θ/_{2}(0)                     | = | 0 rad        |
;;   | ${\overset{˙}{\theta}}_{1}(0)$ | = | 0 rad s^{−1} |
;;   | ${\overset{˙}{\theta}}_{2}(0)$ | = | 0 rad s^{−1} |

;;   What do you see here?


;; * Footnotes

;; [fn:1] A /stationary point/ of a function is a point where the function's value
;; does not vary as the input is varied. Local maxima or minima are stationary
;; points.

;; [fn:2] The variational formulation successfully describes all of the Newtonian
;; mechanics of particles and rigid bodies. The variational formulation has also
;; been usefully applied in the description of many other systems such as classical
;; electrodynamics, the dynamics of inviscid fluids, and the design of mechanisms
;; such as four-bar linkages. In addition, modern formulations of quantum mechanics
;; and quantum field theory build on many of the same concepts. However, it appears
;; that not all dynamical systems have a variational formulation. For example,
;; there is no simple prescription to apply the variational apparatus to systems
;; with dissipation, though in special cases variational methods can still be used.

;; [fn:3]We often refer to a point particle with mass but no internal structure as
;; a /point mass/.

;; [fn:4] Strictly speaking, the dimension of the configuration space and the
;; number of degrees of freedom are not the same. The number of degrees of freedom
;; is the dimension of the space of configurations that are “locally accessible.”
;; For systems with integrable constraints the two are the same. For systems with
;; non-integrable constraints the configuration dimension can be larger than the
;; number of degrees of freedom. For further explanation see the discussion of
;; systems with non-integrable constraints in [[file:chapter001.html#h3_1-10-3][section 1.10.3]]. Apart from that
;; discussion, all of the systems we consider have integrable constraints (they are
;; “holonomic”). This is why we have chosen to blur the distinction between the
;; number of degrees of freedom and the dimension of the configuration space.

;; [fn:5] A tuple is an ordered list of elements. An element may itself be a tuple.

;; [fn:6] A tuple of functions that all have the same domain is itself a function
;; on that domain: Given a point in the domain, the value of the tuple of functions
;; is a tuple of the values of the component functions at that point.

;; [fn:7] The use of superscripts to index the coordinate components is
;; traditional, even though there is potential confusion with exponents. We use
;; zero-based indexing.

;; [fn:8] More precisely, the generalized coordinates identify open subsets of the
;; configuration space with open subsets of *R*/^{n}/. It may require more than one
;; set of generalized coordinates to cover the entire configuration space. For
;; example, if the configuration space is a two-dimensional sphere, we could have
;; one set of coordinates that maps (a little more than) the northern hemisphere to
;; a disk, and another set that maps (a little more than) the southern hemisphere
;; to a disk, with a strip near the equator common to both coordinate systems. A
;; space that can be locally parameterized by smooth coordinate functions is called
;; a /differentiable manifold/.

;; [fn:9] Here $\circ$ denotes composition of functions:

;; $$(f \circ g)(t) = f(g(t))$$

;; [fn:10] The derivative of a function $f$ is a function, denoted $Df$. Our
;; notational convention is that $D$ is a high-precedence operator. Thus $D$
;; operates on the adjacent function before any other application occurs: $Df(x)$
;; is the same as $(Df)(x)$.

;; [fn:11] Experience with systems on an atomic scale suggests that at this scale
;; systems do not travel along well-defined configuration paths. To describe the
;; evolution of systems on the atomic scale we employ quantum mechanics. Here, we
;; restrict attention to systems for which the motion is well described by a smooth
;; configuration path.

;; [fn:12] Extrapolation of the orbit of the Moon backward in time cannot determine
;; the point at which it was placed on this trajectory. To determine the origin of
;; the Moon we must supplement dynamical evidence with other physical evidence such
;; as chemical compositions.

;; [fn:13] We suspect that this argument can be promoted to a precise constraint on
;; the possible ways of making this path-distinguishing function.

;; [fn:14] Historically, Huygens was the first to use the term “action” in
;; mechanics, referring to “the effect of a motion.” This is an idea that came from
;; the Greeks. In his manuscript “Dynamica” (1690) Leibniz enunciated a “Least
;; Action Principle” using the “harmless action,” which was the product of mass,
;; velocity, and the distance of the motion. Leibniz also spoke of a “violent
;; action” in the case where things collided.

;; [fn:15] A definite integral of a real-valued function $f$ of a real argument is
;; written

;;   $\int_{a}^{b}f$

;;   . This can also be written

;;   ${\int_{a}^{b}f}(x)dx$

;;   . The first notation emphasizes that a function is being integrated.

;; [fn:16] Traditionally, square brackets are put around functional arguments. In
;; this case, the square brackets remind us that the value of /S/ may depend on the
;; function /q/ in complicated ways, such as through its derivatives.

;; [fn:17] In the case of a real-valued function, the value of the function and its
;; derivatives at some point can be used to construct a power series. For
;; sufficiently nice functions (real analytic), the power series constructed in
;; this way converges in some interval containing the point. Not all functions can
;; be locally represented in this way. For example, the function /f/(/x/) =
;; exp(−1//x/^{2}), with /f/(0) = 0, is zero and has all derivatives zero at /x/ =
;; 0, but this infinite number of derivatives is insufficient to determine the
;; function value at any other point.

;; [fn:18] In our notation the application of a path-dependent function to its path
;; is of higher precedence than the composition, so /L/ ∘ Γ[/q/] = /L/ ∘ (Γ[/q/]).

;; [fn:19] We will later discover that an initial segment of the local tuple is
;; sufficient to determine the future evolution of the system. That a configuration
;; and a finite number of derivatives determine the future means that there is a
;; way of determining all of the rest of the derivatives of the path from the
;; initial segment.

;; [fn:20] The classical Lagrangian plays a fundamental role in the path-integral
;; formulation of quantum mechanics (due to Dirac and Feynman), where the complex
;; exponential of the classical action yields the relative probability amplitude
;; for a path. The Lagrangian is the starting point for the Hamiltonian formulation
;; of mechanics (discussed in chapter 3), which is also essential in the
;; Schrödinger and Heisenberg formulations of quantum mechanics and in the
;; Boltzmann--Gibbs approach to statistical mechanics.

;; [fn:21] The principle becomes the “principle of least action” if the path is
;; sufficiently short. In the more general case the action is stationary. The term
;; “principle of least action” is also commonly used to refer to a result, due to
;; Maupertuis, Euler, and Lagrange, which says that free particles move along paths
;; for which the integral of the kinetic energy is minimized among all paths with
;; the given endpoints. Correspondingly, the term “action” is sometimes used to
;; refer specifically to the integral of the kinetic energy. (Actually, Euler and
;; Lagrange used the /vis viva/, or twice the kinetic energy.)

;; [fn:22] Other ways of stating the principle of stationary action make it sound
;; teleological and mysterious. For instance, one could imagine that the system
;; considers all possible paths from its initial configuration to its final
;; configuration and then chooses the one with the smallest action. Indeed, the
;; underlying vision of a purposeful, economical, and rational universe played no
;; small part in the philosophical considerations that accompanied the initial
;; development of mechanics. The earliest action principle that remains part of
;; modern physics is Fermat's principle, which states that the path traveled by a
;; light ray between two points is the path that takes the least amount of time.
;; Fermat formulated this principle around 1660 and used it to derive the laws of
;; reflection and refraction. Motivated by this, the French mathematician and
;; astronomer Pierre-Louis Moreau de Maupertuis enunciated the principle of least
;; action as a grand unifying principle in physics. In his /Essai de cosmologie/
;; (1750) Maupertuis appealed to this principle of “economy in nature” as evidence
;; of the existence of God, asserting that it demonstrated “God's intention to
;; regulate physical phenomena by a general principle of the highest perfection.”
;; For a historical perspective on Maupertuis's, Euler's, and Lagrange's roles in
;; the formulation of the principle of least action, see [[[file:bibliography.html#bib_28][28]]].

;; [fn:23] For reflection the angle of incidence is equal to the angle of
;; reflection. Refraction is described by Snell's law: when light passes from one
;; medium to another, the ratio of the sines of the angles made to the normal to
;; the interface is the inverse of the ratio of the refractive indices of the
;; media. The refractive index is the ratio of the speed of light in the vacuum to
;; the speed of light in the medium.

;; [fn:24] Here we are making a function definition. A definition specifies the
;; value of the function for arbitrarily chosen formal parameters. One may change
;; the name of a formal parameter, so long as the new name does not conflict with
;; any other symbol in the definition. For example, the following definition
;; specifies exactly the same free-particle Lagrangian:

;;   $L(a,b,c) = \frac{1}{2}m(c \cdot c).$

;; [fn:25] The Lagrangian is formally a function of the local tuple, but any
;; particular Lagrangian depends only on a finite initial segment of the local
;; tuple. We define functions of local tuples by explicitly declaring names for the
;; elements of the initial segment of the local tuple that includes the elements
;; upon which the function depends.

;; [fn:26]  We represent the local tuple as a composite data structure, the
;; components of which are the time, the generalized coordinates, the generalized
;; velocities, and possibly higher derivatives. We do not want to be bothered by
;; the details of packing and unpacking the components into these structures, so we
;; provide utilities for doing this.

;; [fn:27] Be careful. The /x/ in the definition of /q/ is not the same as the /x/
;; that was used as a formal parameter in the definition of the free-particle
;; Lagrangian above. There are only so many letters in the alphabet, so we are
;; forced to reuse them. We will be careful to indicate where symbols are given new
;; meanings.

;; [fn:28] A tuple of coordinate or velocity components is made with the procedure
;; up. Component i of the tuple q is (ref q i). All indexing is zero based. The
;; word up is to remind us that in mathematical notation these components are
;; indexed by superscripts. There are also down tuples of components that are
;; indexed by subscripts. See the appendix on notation.

;; The constructor up is also used to package the time, the coordinates, and the
;; velocities into a data structure representing a local tuple. The selectors time,
;; coordinate, and velocity extract the appropriate pieces from the local
;; structure. The procedure time is the same as the procedure (component 0), and
;; similarly coordinate is (component 1) and velocity is (component 2).

;; [fn:29] Derivatives of functions yield functions. For example, ((D cube) 2) =>
;; 12 and ((D cube) 'a) => (_{*} 3 (expt a 2)).

;; [fn:30] Although Γ produces an arbitrarily long local tuple, our procedure Gamma
;; produces by default only the first three elements. If a longer local tuple is
;; needed, Gamma can be given the length of the required tuple as an extra
;; argument.

;; [fn:31] In our system, arithmetic operators are generic over symbolic
;; expressions as well as numeric values; arithmetic procedures can work uniformly
;; with numbers or expressions. For example, given the procedure (define (cube x)
;; (_{*} x x x)) we can obtain its value for a number (cube 2) => 8 or for a
;; literal symbol (cube 'a) => (_{*} a a a).

;; [fn:32] For very complicated expressions the prefix notation of Scheme is often
;; better, but simplification is almost always useful. We can separate the
;; functions of simplification and infix display. We will see examples of this
;; later.

;; [fn:33] Scmutils includes a variety of numerical integration procedures. The
;; examples in this section were computed by rational-function extrapolation of
;; Euler--MacLaurin formulas with a relative error tolerance of 10^{−10}.

;; [fn:34] For a real physical situation we would have to specify units for these
;; quantities, but in this illustration we leave them unspecified.

;; [fn:35] Here we use numerals with decimal points to specify the parameters. This
;; forces the representations to be floating point, which is efficient for
;; numerical calculation. If symbolic algebra is to be done it is essential that
;; the numbers be exact integers or rational fractions, so that expressions can be
;; reliably reduced to lowest terms. Such numbers are specified without a decimal
;; point.

;; [fn:36] The squared magnitude of the velocity is $\overset{\rightarrow}{v} \cdot
;;  \overset{\rightarrow}{v}$

;;   , the vector dot product of the velocity with itself, so we write simply
;;   /v/^{2} = /v/ · /v/.

;; [fn:37] Note that we are doing arithmetic on functions. We extend the arithmetic
;; operations so that the combination of two functions of the same type (same
;; domains and ranges) is the function on the same domain that combines the values
;; of the argument functions in the range. For example, if /f/ and /g/ are
;; functions of /t/, then /fg/ is the function /t/ ↦ /f/(/t/)/g/(/t/). A constant
;; multiple of a function is the function whose value is the constant times the
;; value of the function for each argument: /cf/ is the function /t/ ↦ /cf/(/t/).

;; [fn:38] Note that we are adding procedures. Paralleling our extension of
;; arithmetic operations to functions, arithmetic operations are extended to
;; compatible procedures.

;; [fn:39] The arguments to minimize are a procedure implementing the univariate
;; function in question, and the lower and upper bounds of the region to be
;; searched. Scmutils includes a choice of methods for numerical minimization; the
;; one used here is Brent's algorithm, with an error tolerance of 10^{−5}. The
;; value returned by minimize is a list of three numbers: the first is the argument
;; at which the minimum occurred, the second is the minimum obtained, and the third
;; is the number of iterations of the minimization algorithm required to obtain the
;; minimum.

;; [fn:40] Yes, /-1.5987211554602254e-14/ is zero for the tolerance required of the
;; minimizer. And /435.0000000000237/ is arguably the same as /435/ obtained
;; before.

;; [fn:41] There are lots of good ways to make such a parametric set of
;; approximating trajectories. One could use splines or higher-order interpolating
;; polynomials; one could use Chebyshev polynomials; one could use Fourier
;; components. The choice depends upon the kinds of trajectories one wants to
;; approximate.

;; [fn:42] Here is one way to implement make-path:

;;    #+begin_src scheme
;; (define (make-path t0 q0 t1 q1 qs)
;;   (let ((n (length qs)))
;;     (let ((ts (linear-interpolants t0 t1 n)))
;;       (Lagrange-interpolation-function
;;        (append (list q0) qs (list q1))
;;        (append (list t0) ts (list t1))))))
;;    #+end_src

;; The procedure linear-interpolants produces a list of elements that linearly
;; interpolate the first two arguments. We use this procedure here to specify ts,
;; the n evenly spaced intermediate times between t0 and t1 at which the path will
;; be specified. The parameters being adjusted, qs, are the positions at these
;; intermediate times. The procedure Lagrange-interpolation-function takes a list
;; of values and a list of times and produces a procedure that computes the
;; Lagrange interpolation polynomial that passes through these points.

;; [fn:43] The minimizer used here is the Nelder--Mead downhill simplex method. As
;; usual with numerical procedures, the interface to the nelder-mead procedure is
;; complex, with lots of optional parameters to let the user control errors
;; effectively. For this presentation we have specialized nelder-mead by wrapping
;; it in the more palatable multidimensional-minimize. Unfortunately, you will have
;; to learn to live with complicated numerical procedures someday.

;; [fn:44] Don't worry. We know that you don't yet know why this is the right
;; Lagrangian. We will get to this in [[file:chapter001.html#h1-6a][section 1.6]].

;; [fn:45] The square of a structure of components is defined to be the sum of the
;; squares of the individual components.

;; [fn:46] By convention, named constants have names that begin with a colon. The
;; constants named :pi and :-pi are what we would expect from their names.

;; [fn:47] The derivative or partial derivative of a function that takes structured
;; arguments is a new function that takes the same number and type of arguments.
;; The range of this new function is itself a structure with the same number of
;; components as the argument with respect to which the function is differentiated.
;; See the appendix on notation for more.

;; [fn:48] To make this argument more precise requires careful analysis.

;; [fn:49] The variation operator /δ_{η}/ is like the derivative operator in that
;; it acts on the immediately following function: /δ_{η}f/[/q/] = (/δ_{η}f/)[/q/].

;; [fn:50] We separate out the definition of /g/: We cannot substitute /Dq/ for
;; /g/[/q/] in /δ_{η}g/[/q/] because /δ_{η}/ applies to /g/ not /g/[/q/].

;; [fn:51] The type of a literal function is described by a function signature. The
;; default function signature is (-> Real Real) indicating a real-valued function
;; of a real argument. In this case F is declared as a function that is shaped like
;; a Lagrangian, with an unspecified number of degrees of freedom. For more
;; information about function signatures see the appendix on notation.

;; [fn:52] A function of multiple arguments is considered a function of an up tuple
;; of its arguments. Thus, the derivative of a function of multiple arguments is a
;; down tuple of the partial derivatives of that function with respect to each of
;; the arguments. So in the case of a Lagrangian /L/,

;; $$DL(t,q,v) = \lbrack\partial_{0}L(t,q,v),\partial_{1}L(t,q,v),\partial_{2}L(t,q,v)\rbrack.$$

;; [fn:53] When we write a definition that names the components of the local tuple,
;; we indicate that these are grouped into time, position, and velocity components
;; by separating the groups with semicolons.

;; [fn:54] The symbol $\overset{˙}{\theta}$

;; is just a mnemonic symbol; the dot over the /θ/ does not indicate
;; differentiation. To define /L/ we could have just as well have written:

;; $$L(a,b,c) = \frac{1}{2}ml^{2}c^{2} + mgl\cos b$$

;; . However, we use a dotted symbol to remind us that the argument matching a
;; formal parameter, such as

;;   $\overset{˙}{\theta}$

;; , is a rate of change of an angle, such as /θ/.

;; [fn:55] In traditional notation these equations read

;; $$\frac{d^{2}}{dt^{2}}\frac{\partial L}{\partial\overset{¨}{q}} -
;; \frac{d}{dt}\frac{\partial L}{\partial\overset{˙}{q}} + \frac{\partial
;; L}{\partial q} = 0.$$

;; [fn:56] The Lagrange-equations procedure uses the operations (partial 1) and
;; (partial 2), which implement the partial derivative operators with respect to
;; the second and third argument positions (those with indices 1 and 2).

;; [fn:57] There is a Lagrange equation for every degree of freedom. The residuals of
;; all the equations are zero if the path is realizable. The residuals are arranged
;; in a down tuple because they result from derivatives of the Lagrangian with
;; respect to argument slots that take up tuples. See the appendix on notation.

;; [fn:58] Observe that the second derivative is indicated as the square of the
;; derivative operator (expt D 2). Arithmetic operations in Scmutils extend over
;; operators as well as functions.

;; [fn:59] Remember that /x/ and /v/ are just formal parameters of the Lagrangian.
;; This /x/ is not the path /x/ used earlier in the derivation, though it could be
;; the value of that path at a particular time.

;; [fn:60] We can always give a function extra arguments that are not used so that
;; it can be algebraically combined with other functions of the same shape.

;; [fn:61] William Rowan Hamilton formulated the fundamental variational principle
;; for time-independent systems in 1834--1835. Jacobi gave this principle the name
;; “Hamilton's principle.” For systems subject to generic, nonstationary
;; constraints Hamilton's principle was investigated in 1848 by Ostrogradsky, and
;; in the Russian literature Hamilton's principle is often called the
;; Hamilton--Ostrogradsky principle.

;; Hamilton (1805--1865) was a brilliant mathematician. His early work on geometric
;; optics (based on Fermat's principle) was so impressive that he was elected to
;; the post of Professor of Astronomy at Trinity College and Royal Astronomer of
;; Ireland while he was still an undergraduate. He produced two monumental works of
;; mathematics. His discovery of quaternions revitalized abstract algebra and
;; sparked the development of vector techniques in physics. His 1835 memoir “On a
;; General Method in Dynamics” put variational mechanics on a firm footing, finally
;; giving substance to Maupertuis's vaguely stated Principle of Least Action of 100
;; years before. Hamilton also wrote poetry and carried on an extensive
;; correspondence with Wordsworth, who advised him to put his energy into writing
;; mathematics rather than poetry.

;; In addition to the formulation of the fundamental variational principle,
;; Hamilton also emphasized the analogy between geometric optics and mechanics, and
;; stressed the importance of the momentum variables (which were earlier introduced
;; by Lagrange and Cauchy), leading to the “canonical” form of mechanics discussed
;; in chapter 3.

;; [fn:62] When applied to a tuple, square means the sum of the squares of the
;; components of the tuple.

;; [fn:63] As described in footnote [fn:26] above, the procedure up constructs a local
;; tuple from an initial segment of time, coordinates, and velocities.

;; [fn:64] We hope you appreciate the T_{E}X magic here. Symbols with carets,
;; underlines, the names of Greek letters, and those terminating in the characters
;; “dot” are converted by show-expression to the corresponding T_{E}X expression.

;; [fn:65] We will simply accept the Newtonian procedure for systems with rigid
;; constraints and find Lagrangians that are equivalent. Of course, actual bodies
;; are never truly rigid, so we may wonder what detailed approximations have to be
;; made to treat them as if they were truly rigid. For instance, a more satisfying
;; approach would be to replace the rigid distance constraints by very stiff
;; springs. We could then immediately write the Lagrangian as /L/ = /T/ − /V/, and
;; we should be able to /derive/ the Newtonian procedure for systems with rigid
;; constraints as an approximation. However, this is too complicated to do at this
;; stage, so we accept the Newtonian idealization.

;; [fn:66] This Lagrangian is purely formal and does not represent a model of the
;; constraint forces. In particular, note that the constraint terms do not add up
;; to a potential energy with a minimum when the constraints are exactly satisfied.
;; Rather, the constraint terms in the Lagrangian are zero when the constraint is
;; satisfied, and can be either positive or negative depending on whether the
;; distance between the particles is larger or smaller than the constraint
;; distance.

;; [fn:67] Typically the number of components of /x/ is equal to the sum of the
;; number of components of /q/ and /c/; adding a strut removes a degree of freedom
;; and adds a distance constraint. However, there are singular cases in which the
;; addition of single strut can remove more than a single degree of freedom. We do
;; not consider the singular cases here.

;; [fn:68] Consider a function /g/ of, say, three arguments, and let /g/_{0} be a
;; function of two arguments satisfying /g/_{0}(/x/, /y/) = /g/(/x/, /y/, 0). Then
;; (∂_{0}/g/_{0})(/x/, /y/) = (∂_{0}/g/)(/x/, /y/, 0). The substitution of a value
;; in an argument commutes with the taking of the partial derivative with respect
;; to a different argument. In deriving the Lagrange equations for /q/ we can set
;; /c/ = /l/ and

;;   $\overset{˙}{c} = 0$

;;   in the Lagrangian, but we cannot do this in deriving the Lagrange equations
;;   associated with /c/, because we have to take derivatives with respect to those
;;   arguments.

;; [fn:69] /R_{z}/(/α/) yields a function that rotates its argument about the /ẑ/
;; axis by the angle /α/, and /R_{y}/ is similar.

;; [fn:70] Components of a tuple structure, such as the value of Γ[/q/](/t/), can
;; be selected with selector functions: /I_{i}/ gets the element with index /i/
;; from the tuple.

;; [fn:71] The Lipschitz condition is that the rate of change of the state is
;; bounded by a constant in an open set around each state. See [[[file:bibliography.html#bib_25][25]]] for a good
;; treatment of the Lipschitz condition.

;; [fn:72] If the coordinates are redundant we cannot, in general, solve for the
;; highest-order derivative. However, since we can transform to irredundant
;; coordinates, since we can solve the initial-value problem in the irredundant
;; coordinates, and since we can construct the redundant coordinates from the
;; irredundant coordinates, we can in general solve the initial-value problem for
;; redundant coordinates. The only hitch is that we cannot specify arbitrary
;; initial conditions: the initial conditions must be consistent with the
;; constraints.

;; [fn:73] We may encounter singularities that make this matrix uninvertable, but
;; in real systems these singularities are isolated and can be avoided by changing
;; coordinates.

;; [fn:74] For Lagrangians that depend on time, coordinates, and velocities the
;; state is specified by time, coordinates, and velocities. However, if a
;; Lagrangian depends on the first four components of the local tuple (time,
;; coordinates, velocities, and accelerations) the state of the system will be
;; specified by the first five components of the local tuple.

;; [fn:75] The procedure solve-linear-left multiplies its second argument by the
;; inverse of its first argument on the left. So, if /u/ = /Mv/ then /v/ =
;; /M/^{−1}/u/; (solve-linear-left M u) produces v.

;; [fn:76] The Scmutils system provides a variety of numerical integration routines
;; that can be accessed through this interface. These include quality-controlled
;; Runge--Kutta and Bulirsch--Stoer. The default integration method is
;; Bulirsch--Stoer.

;; [fn:77] The procedure state-advancer automatically compiles state-derivative
;; procedures the first time they are encountered. The first time a new state
;; derivative is used there is a delay while compilation occurs.

;; [fn:78] The results are plotted in a plot window created by the procedure frame
;; with arguments xmin, xmax, ymin, and ymax that specify the limits of the
;; plotting area. Points are added to the plot with the procedure plot-point that
;; takes a plot window and the abscissa and ordinate of the point to be plotted.

;; The procedure principal-value is used to reduce an angle to a standard interval.
;; The argument to principal-value is the point at which the circle is to be cut.
;; Thus (principal-value :pi) is a procedure that reduces an angle /θ/ to the
;; interval −/π/ ≤ /θ < /π/./

;; [fn:79] In older literature conserved quantities are sometimes called /first
;; integrals/.

;; [fn:80] Observe that we indicate a component of the generalized momentum with a
;; subscript, and a component of the generalized coordinates with a superscript.
;; These conventions are consistent with those commonly used in tensor algebra,
;; which is sometimes helpful in working out complex problems.

;; [fn:81] For example, we may construct equivalent Lagrangians that differ only by
;; a total time derivative. The momentum state functions for these Lagrangians are
;; different.

;; [fn:82] In general, conserved quantities in a physical system are associated
;; with continuous symmetries, whether or not one can find a coordinate system in
;; which the symmetry is apparent. This powerful notion was formalized and a
;; theorem linking conservation laws with symmetries was proved by Noether early in
;; the 20th century. See [[file:chapter001.html#h3_1-8-5][section 1.8.5]] on Noether's theorem.

;; [fn:83] The sign of the energy state function is a matter of convention.

;; [fn:84] We may construct equivalent Lagrangians that differ only by a total time
;; derivative. The energy state functions for these Lagrangians are different.

;; [fn:85] A function /f/ is homogenous of degree /n/ if and only if /f/(/ax/) =
;; /a^{n}f/(/x/). Euler's theorem says that if /f/ is a homogeneous function of
;; degree /n/, then /Df/(/x/)/x/ = /nf/(/x/). The proof is as follows: Let
;; /g_{x}/(/a/) = /f/(/ax/). Then /Dg_{x}/(/a/) = /Df/(/ax/)/x/. But /g_{x}/(/a/) =
;; /a^{n}f/(/x/) by the definition of homogeneity. Therefore /Dg_{x}/(/a/) =
;; /na/^{/n/−1}/f/(/x/). Equating these, we find /Df/(/ax/)/x/ =
;; /na/^{/n/−1}/f/(/x/). Specializing to /a/ = 1 we obtain /Df/(/x/)/x/ = /nf/(/x/)
;; as required.

;; [fn:86] Traditionally the Jacobi constant is defined as /C_{J}/ = −2/ℰ/.

;; [fn:87] The total time derivative is like a derivative with respect to a
;; real-number argument in that it does not generate structure, so it can commute
;; with derivatives that generate structure. Be careful, though: it may not commute
;; with some derivatives for other reasons. For example,

;; $$D_{t}\partial_{1}(\widetilde{F}(s))$$

;; is the same as

;; $$\partial_{1}D_{t}(\widetilde{F}(s))$$

;; , but

;; $$D_{t}\partial_{2}(\widetilde{F}(s))$$

;; is not the same as

;; $$\partial_{2}D_{t}(\widetilde{F}(s))$$

;; . The reason is that

;; $$\widetilde{F}(s)$$

;; does not depend on the velocity, but

;; $$D_{t}(\widetilde{F}(s))$$

;; does.

;; [fn:88] The definition of the procedure =Rx= is

;; #+begin_src scheme
;; (define ((Rx angle) q)
;;   (let ((ca (cos angle))
;;         (sa (sin angle)))
;;     (let ((x (ref q 0))
;;           (y (ref q 1))
;;           (z (ref q 2)))
;;       (up x
;;           (- (* ca y) (* sa z))
;;           (+ (* sa y) (* ca z))))))
;; #+end_src

;; The definitions of =Ry= and =Rz= are similar. See footnote [fn:69].

;; [fn:89] The local-tuple function /f/ is the same as the local-tuple function

;; $$\overline{\Gamma}(\overline{f})$$

;; where

;;   $\overline{f}\lbrack q\rbrack = f \circ \Gamma\lbrack q\rbrack$

;; . On the other hand, the path function

;; $$\overline{f}\lbrack q\rbrack$$

;; and the path function

;; $$\overline{\Gamma}(\overline{f}) \circ \Gamma\lbrack q\rbrack$$

;; are not necessarily the same because

;; $$\overline{f}\lbrack q\rbrack$$

;; may require more components of the local tuple than are provided by default. For
;; example, the Lagrange equations involve accelerations, as well as time,
;; coordinates, and velocities. If Γ[/q/] is extended to the appropriate number of
;; components then the two are equivalent. (See footnote [fn:90].)

;; [fn:90] This F->C is more general than the code introduced on [[file:chapter001.html#p46][page 46]] in that it
;; allows computation of transformations of higher derivatives of the local tuple,
;; if required.

;; To make this work, Gamma is also extended to generate more elements of the local
;; tuple than are needed for Lagrangians that depend on time, coordinates, and
;; velocities. Here Gamma is given one more argument than it usually has. This
;; optional argument gives the length of the initial segment of the local tuple
;; needed. The default length is 3, giving components of the local tuple up to and
;; including the velocities.

;; [fn:91] Given any acceptable variation, we may make another acceptable variation
;; by multiplying the given one by a bump function that emphasizes any particular
;; time interval.

;; [fn:92] We take two tuple-valued functions of time to be orthogonal if at each
;; instant the dot product of the tuples is zero. Similarly, tuple-valued functions
;; are considered parallel if at each moment one of the tuples is a scalar multiple
;; of the other. The scalar multiplier is in general a function of time.

;; [fn:93] Recall that the Euler--Lagrange operator $\mathcal{E}$

;; has the property

;; $$\mathcal{E}\,\lbrack FG\rbrack = F\,\mathcal{E}\lbrack G\rbrack +
;; \mathcal{E}\lbrack F\rbrack G + D_{t}F\,\partial_{2}G + \partial_{2}F\,
;; D_{t}G.$$

;; [fn:94] This constraint has the same form as those used in the demonstration
;; that /L/ = /T/ − /V/ can be used for rigid systems. Here it is a particular
;; example of a more general set of constraints.

;; [fn:95] Indeed, if we had scaled the constraint equations as we did in the
;; discussion of Newtonian constraint forces, we could have identified /λ/ with the
;; the magnitude of the constraint force /F/. However, though /λ/ will in general
;; be related to the constraint forces it will not be one of them. We chose to
;; leave the scaling as it naturally appeared rather than make things turn out
;; artificially pretty.

;; [fn:96] This example appears in [[[file:bibliography.html#bib_20][20]]], pp. 49--51.

;; [fn:97] We will see in chapter 2 how to compute the kinetic energy of rotation,
;; but for now the answer is

;; $$\frac{1}{2}M\, R^{2}{\overset{˙}{\theta}}^{2}.$$

;; [fn:98] For some treatments of nonholonomic systems see, for example, Whittaker
;; [[[file:bibliography.html#bib_46][46]]], Goldstein [[[file:bibliography.html#bib_20][20]]], Gantmakher [[[file:bibliography.html#bib_19][19]]], or Arnold et al. [[[file:bibliography.html#bib_6][6]]].

;; [fn:99] Arnold et al. [[[file:bibliography.html#bib_6][6]]] call the variational mechanics with the constraints
;; added to the Lagrangian /Vakonomic mechanics/.

  :end-comment)

