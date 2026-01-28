;;The subject of this book is motion and the mathematical tools used to describe
;;it.

;;Centuries of careful observations of the motions of the planets revealed
;;regularities in those motions, allowing accurate predictions of phenomena such
;;as eclipses and conjunctions. The effort to formulate these regularities and
;;ultimately to understand them led to the development of mathematics and to the
;;discovery that mathematics could be effectively used to describe aspects of the
;;physical world. That mathematics can be used to describe natural phenomena is a
;;remarkable fact.

;;A pin thrown by a juggler takes a rather predictable path and rotates in a
;;rather predictable way. In fact, the skill of juggling depends crucially on this
;;predictability. It is also a remarkable discovery that the same mathematical
;;tools used to describe the motions of the planets can be used to describe the
;;motion of the juggling pin.

;;Classical mechanics describes the motion of a system of particles, subject to
;;forces describing their interactions. Complex physical objects, such as juggling
;;pins, can be modeled as myriad particles with fixed spatial relationships
;;maintained by stiff forces of interaction.

;;There are many conceivable ways a system could move that never occur. We can
;;imagine that the juggling pin might pause in midair or go fourteen times around
;;the head of the juggler before being caught, but these motions do not happen.
;;How can we distinguish motions of a system that can actually occur from other
;;conceivable motions? Perhaps we can invent some mathematical function that
;;allows us to distinguish realizable motions from among all conceivable motions.

;;The motion of a system can be described by giving the position of every piece of
;;the system at each moment. Such a description of the motion of the system is
;;called a /configuration path/; the configuration path specifies the
;;configuration as a function of time. The juggling pin rotates as it flies
;;through the air; the configuration of the juggling pin is specified by giving
;;the position and orientation of the pin. The motion of the juggling pin is
;;specified by giving the position and orientation of the pin as a function of
;;time.

;;The path-distinguishing function that we seek takes a configuration path as an
;;input and produces some output. We want this function to have some
;;characteristic behavior when its input is a realizable path. For example, the
;;output could be a number, and we could try to arrange that this number be zero
;;only on realizable paths. Newton's equations of motion are of this form; at each
;;moment Newton's differential equations must be satisfied.

;;However, there is an alternate strategy that provides more insight and power: we
;;could look for a path-distinguishing function that has a minimum on the
;;realizable paths---on nearby unrealizable paths the value of the function is
;;higher than it is on the realizable path. This is the /variational strategy/:
;;for each physical system we invent a path-distinguishing function that
;;distinguishes realizable motions of the system by having a stationary point for
;;each realizable path[fn:1]. For a great variety of systems realizable motions of
;;the system can be formulated in terms of a variational principle.[fn:2]

;;Mechanics, as invented by Newton and others of his era, describes the motion of
;;a system in terms of the positions, velocities, and accelerations of each of the
;;particles in the system. In contrast to the Newtonian formulation of mechanics,
;;the variational formulation of mechanics describes the motion of a system in
;;terms of aggregate quantities that are associated with the motion of the system
;;as a whole.

;;In the Newtonian formulation the forces can often be written as derivatives of
;;the potential energy of the system. The motion of the system is determined by
;;considering how the individual component particles respond to these forces. The
;;Newtonian formulation of the equations of motion is intrinsically a
;;particle-by-particle description.

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

;;### 1.1 Configuration Spaces

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

;;  As a system evolves with time, the constituent particles move subject to the
;;  constraints. The motion of each constituent particle is specified by
;;  describing the changing configuration. Thus, the motion of the system may be
;;  described as evolving along a path in configuration space. The configuration
;;  path may be specified by a function, the configuration-path function, which
;;  gives the configuration of the system at any time.

;;### Exercise 1.1: Degrees of freedom

;;    For each of the mechanical systems described below, give the number of
;;    degrees of freedom of the configuration space.

;;    - *a.* Three juggling pins.

;;    - *b.* A spherical pendulum, consisting of a point mass (the pendulum bob)
;;    hanging from a rigid massless rod attached to a fixed support point. The
;;    pendulum bob may move in any direction subject to the constraint imposed by
;;    the rigid rod. The point mass is subject to the uniform force of gravity.

;;    - *c.* A spherical double pendulum, consisting of one point mass hanging
;;      from a rigid massless rod attached to a second point mass hanging from a second
;;    massless rod attached to a fixed support point. The point masses are subject
;;    to the uniform force of gravity.

;;    - *d.* A point mass sliding without friction on a rigid curved wire.

;;    - *e.* A top consisting of a rigid axisymmetric body with one point on the
;;    symmetry axis of the body attached to a fixed support, subject to a uniform
;;    gravitational force.

;;    - *f.* The same as *e*, but not axisymmetric.
;;## 1.2 Generalized Coordinates

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

;;  The number of coordinates need not be the same as the dimension of the
;;  configuration space, though there must be at least that many. We may choose to
;;  work with more parameters than necessary, but then the parameters will be
;;  subject to constraints that restrict the system to possible configurations,
;;  that is, to elements of the configuration space.

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

;;  The motion of the system can be described by a configuration path $γ$ mapping
;;  time to configuration-space points. Corresponding to the configuration path is
;;  a *coordinate path* $q = \chi \circ \gamma$ mapping time to tuples of
;;  generalized coordinates.[fn:9] If there is more than one degree of freedom the
;;  coordinate path is a structured object: $q$ is a tuple of component coordinate
;;  path functions $q^i = \chi^i \circ \gamma$. At each instant of time $t$, the
;;  values $q(t) = \left(q^0(t),\, \ldots,\, q^{n−1}(t) \right)$ are the
;;  generalized coordinates of a configuration.

;;  The derivative $Dq$ of the coordinate path $q$ is a function[fn:10] that gives
;;  the rate of change of the configuration coordinates at a given time: $Dq(t)
;;  = (Dq^{0}(t), ..., Dq^{n−1}(t))$. The rate of change of a generalized
;;  coordinate is called a *generalized velocity*.

;;### Exercise 1.2: Generalized coordinates

;;    For each of the systems in exercise 1.1, specify a system of generalized
;;    coordinates that can be used to describe the behavior of the system.

;;## 1.3 The Principle of Stationary Action

;;  Let us suppose that for each physical system there is a path-distinguishing
;;  function that is stationary on realizable paths. We will try to deduce some of
;;  its properties.

;;### Experience of motion

;;   Our ordinary experience suggests that physical motion can be described by
;;   configuration paths that are continuous and smooth.[fn:11] We do not see the
;;   juggling pin jump from one place to another. Nor do we see the juggling pin
;;   suddenly change the way it is moving.

;;   Our ordinary experience suggests that the motion of physical systems does not
;;   depend upon the entire history of the system. If we enter the room after the
;;   juggling pin has been thrown into the air we cannot tell when it left the
;;   juggler's hand. The juggler could have thrown the pin from a variety of
;;   places at a variety of times with the same apparent result as we walk through
;;   the door.[fn:12] So the motion of the pin does not depend on the details of
;;   the history.

;;   Our ordinary experience suggests that the motion of physical systems is
;;   deterministic. In fact, a small number of parameters summarize the important
;;   aspects of the history of the system and determine its future evolution. For
;;   example, at any moment the position, velocity, orientation, and rate of
;;   change of the orientation of the juggling pin are enough to completely
;;   determine the future motion of the pin.

;;### Realizable paths

;;   From our experience of motion we develop certain expectations about
;;   realizable configuration paths. If a path is realizable, then any segment of
;;   the path is a realizable path segment. Conversely, a path is realizable if
;;   every segment of the path is a realizable path segment. The realizability of
;;   a path segment depends on all points of the path in the segment. The
;;   realizability of a path segment depends on every point of the path segment in
;;   the same way; no part of the path is special. The realizability of a path
;;   segment depends only on points of the path within the segment; the
;;   realizability of a path segment is a local property.

;;   So the path-distinguishing function aggregates some local property of the
;;   system measured at each moment along the path segment. Each moment along the
;;   path must be treated in the same way. The contributions from each moment
;;   along the path segment must be combined in a way that maintains the
;;   independence of the contributions from disjoint subsegments. One method of
;;   combination that satisfies these requirements is to add up the contributions,
;;   making the path-distinguishing function an integral over the path segment of
;;   some local property of the path.[fn:13]

;;   So we will try to arrange that the path-distinguishing function, constructed
;;   as an integral of a local property along the path, assumes a stationary value
;;   for any realizable path. Such a path-distinguishing function is traditionally
;;   called an *action* for the system. We use the word “action” to be consistent
;;   with common usage. Perhaps it would be clearer to continue to call it
;;   “path-distinguishing function,” but then it would be more difficult for
;;   others to know what we were talking about.[fn:14]

;;   In order to pursue the agenda of variational mechanics, we must invent action
;;   functions that are stationary on the realizable trajectories of the systems
;;   we are studying. We will consider actions that are integrals of some local
;;   property of the configuration path at each moment. Let $q = χ ∘ γ$ be a
;;   coordinate path in the configuration space; $q(t)$ are the coordinates of
;;   the configuration at time $t$. Then the action of a segment of the path in
;;   the time interval from $t_{1}$ to $t_{2}$ is[fn:15]

;; $$
;; {S\lbrack q\rbrack(t_{1},t_{2}) = {\int_{t_{1}}^{t_{2}}{F\lbrack q\rbrack.}}  \tag{1.1}}
;; $$

;;   where $F[q]$ is a function of time that measures some local property of the
;;   path. It may depend upon the value of the function $q$ at that time and the
;;   value of any derivatives of $q$ at that time.[fn:16]

;;The configuration path can be locally described at a moment in terms of the
;;   coordinates, the rate of change of the coordinates, and all the higher
;;   derivatives of the coordinates at the given moment. Given this information
;;   the path can be reconstructed in some interval containing that moment.[fn:17]
;;   Local properties of paths can depend on no more than the local description of
;;   the path.

;;   The function $F$ measures some local property of the coordinate path $q$. We
;;   can decompose $F[q]$ into two parts: a part that measures some property of
;;   a local description and a part that extracts a local description of the path
;;   from the path function. The function that measures the local property of the
;;   system depends on the particular physical system; the method of construction
;;   of a local description of a path from a path is the same for any system. We
;;   can write $F[q]$ as a composition of these two functions:[fn:18]

;;   $$
;;   {F\lbrack q\rbrack = L \circ \Gamma\lbrack q\rbrack.} \tag{1.2}
;;   $$

;;   The function Γ takes the coordinate path and produces a function of time
;;   whose value is an ordered tuple containing the time, the coordinates at that
;;   time, the rate of change of the coordinates at that time, and the values of
;;   higher derivatives of the coordinates evaluated at that time. For the path
;;   $q$ and time $t$:

;;   $$
;;   {\Gamma\lbrack q\rbrack(t) = (t,q(t),Dq(t),\ldots).} \tag{1.3}
;;   $$

;;   We refer to this tuple, which includes as many derivatives as are needed, as
;;   the *local tuple*. The function $Γ[q]$ depends only on the coordinate path
;;   $q$ and its derivatives; the function $Γ[q]$ does not depend on $χ$ or the
;;   fact that $q$ is made by composing $χ$ with $γ$.

;;   The function $L$ depends on the specific details of the physical system being
;;   investigated, but does not depend on any particular configuration path. The
;;   function $L$ computes a real-valued local property of the path. We will find
;;   that $L$ needs only a finite number of components of the local tuple to
;;   compute this property: The path can be locally reconstructed from the full
;;   local description; that $L$ depends on a finite number of components of the
;;   local tuple guarantees that it measures a local property.[fn:19]

;;   The advantage of this decomposition is that the local description of the path
;;   is computed by a uniform process from the configuration path, independent of
;;   the system being considered. All of the system-specific information is
;;   captured in the function $L$.

;;   The function $L$ is called a *Lagrangian*[fn:20] for the system, and the
;;   resulting action,

;;   $$
;;   {S\lbrack q\rbrack(t_{1},t_{2}) = {\int_{t_{1}}^{t_{2}}{L \circ \Gamma\lbrack q\rbrack,}}} \tag{1.4}
;;   $$

;;   is called the *Lagrangian action*. For Lagrangians that depend only on time,
;;   positions, and velocities the action can also be written

;;   $$
;;   {S\lbrack q\rbrack(t_{1},t_{2}) = {\int_{t_{1}}^{t_{2}}{L(t,q(t),Dq(t))\, dt.}}} \tag{1.5}
;;   $$

;;   Lagrangians can be found for a great variety of systems. We will see that for
;;   many systems the Lagrangian can be taken to be the difference between kinetic
;;   and potential energy. Such Lagrangians depend only on the time, the
;;   configuration, and the rate of change of the configuration. We will focus on
;;   this class of systems, but will also consider more general systems from time
;;   to time.

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

;;   The *principle of stationary action* asserts that for each dynamical system
;;   we can cook up a Lagrangian such that a realizable path connecting the
;;   configurations at two times $t_{1}$ and $t_{2}$ is distinguished from all
;;   conceivable paths by the fact that the action $S[q](t_{1}, t_{2})$ is
;;   stationary with respect to variations of the path.[fn:21] For Lagrangians
;;   that depend only on the configuration and rate of change of configuration,
;;   the variations are restricted to those that preserve the configurations at
;;   $t_{1}$ and $t_{2}$.[fn:22]

;;### Exercise 1.3: Fermat optics

;;    Fermat observed that the laws of reflection and refraction could be
;;    accounted for by the following facts: Light travels in a straight line in
;;    any particular medium with a velocity that depends upon the medium. The path
;;    taken by a ray from a source to a destination through any sequence of media
;;    is a path of least total time, compared to neighboring paths. Show that
;;    these facts imply the laws of reflection and refraction.[fn:23]

;;## 1.4 Computing Actions

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

;;  Following Euler and Lagrange, the Lagrangian for the free particle is[fn:24]

;;  $$
;;  {L(t,x,v) = \frac{1}{2}m(v \cdot v),} \tag{1.6}
;;  $$

;;  where the formal parameter $x$ names a tuple of components of the position
;;  with respect to a given rectangular coordinate system, and the formal
;;  parameter $v$ names a tuple of velocity components.[fn:25]

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

;;  $$
;;  {q(t) = (x(t),y(t),z(t)).} \tag{1.7}
;;  $$

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

;;  $Dq(t) = (Dx(t),Dy(t),Dz(t)).$

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

;;    For a free particle an appropriate Lagrangian is[fn:36]

;;    $$
;;    {L(t,x,v) = \frac{1}{2}mv^{2}.} \tag{1.8)}
;;    $$

;;    Suppose that $x$ is the constant-velocity straight-line path of a free
;;    particle, such that $x_{a} = x(t_{a})$ and $x_{b} =
;;    x(t_{b})$. Show that the action on the solution path is

;;    $$
;;    {\frac{m}{2}\frac{{(x_{b} - x_{a})}^{2}}{t_{b} - t_{a}}.} \tag{1.9}
;;    $$

;;## Paths of minimum action

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
;;   The action for the varied path, with $ν(t) = (\sin t, \cos t, t^{2})$
;;   and $ϵ = 0.001$, is, as expected, larger than for the test path:

(print-expression
  ((varied-free-particle-action 3.0 test-path (up sin cos square) 0.0 10.0) 0.001))

;;   We can numerically compute the value of $ϵ$ for which the action is
;;   minimized. We search between, say, −2 and 1:[fn:39]

(print-expression
  (minimize
    (varied-free-particle-action 3.0 test-path (up sin cos square) 0.0 10.0) 
    -2.0 1.0))

;;   We find exactly what is expected---that the best value for $ϵ$ is
;;   zero,[fn:40] and the minimum value of the action is the action along the
;;   straight path.

;### Finding trajectories that minimize the action


;;   We have used the variational principle to determine if a given trajectory is
;;   realizable. We can also use the variational principle to find trajectories.
;;   Given a set of trajectories that are specified by a finite number of
;;   parameters, we can search the parameter space looking for the trajectory in
;;   the set that best approximates the real trajectory by finding one that
;;   minimizes the action. By choosing a good set of approximating functions we
;;   can get arbitrarily close to the real trajectory.[fn:41]

;;   One way to make a parametric path that has fixed endpoints is to use a
;;   polynomial that goes through the endpoints as well as a number of
;;   intermediate points. Variation of the positions of the intermediate points
;;   varies the path; the parameters of the varied path are the coordinates of the
;;   intermediate positions. The procedure `make-path` constructs such a path using
;;   a Lagrange interpolation polynomial. The procedure `make-path` is called with
;;   five arguments: `(make-path t0 q0 t1 q1 qs)`, where q0 and q1 are the
;;   endpoints, t0 and t1 are the corresponding times, and qs is a list of
;;   intermediate points.[fn:42]

;;   Having specified a parametric path, we can construct a parametric action that
;;   is just the action computed along the parametric path:


(define ((parametric-path-action Lagrangian t0 q0 t1 q1) qs)
  (let ((path (make-path t0 q0 t1 q1 qs)))
    (Lagrangian-action Lagrangian path t0 t1)))

;;   We can find approximate solution paths by finding parameters that minimize
;;   the action. We do this minimization with a canned multidimensional
;;   minimization procedure:[fn:43]

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

;;   To illustrate the use of this strategy, we will find trajectories of the
;;   harmonic oscillator, with Lagrangian[fn:44]

;;   $$
;;   {L(t,q,v) = \frac{1}{2}mv^{2} - \frac{1}{2}kq^{2},} \tag{1.10}
;;   $$

;;   for mass $m$ and spring constant $k$. This Lagrangian is implemented
;;   by[fn:45]

;;   *Figure 1.1* The difference between the polynomial
;;   approximation with minimum action and the actual trajectory taken by the
;;   harmonic oscillator. The abscissa is the time and the ordinate is the error.
;;   [comment MAK: you find the plot in the notebook SICM Ch01 Graphics]

(define ((L-harmonic m k) local)
  (let ((q (coordinate local))
        (v (velocity local)))
    (- (* 1/2 m (square v))
       (* 1/2 k (square q)))))

;;   We can find an approximate path taken by the harmonic oscillator for $m = 1$
;;   and $k = 1$ between $q(0) = 1$ and $q(π/2) = 0$ as follows:[fn:46]

(define q
  (find-path (L-harmonic 1.0 1.0) 0.0 1.0 :pi/2 0.0 3))

;;
;;   We know that the trajectories of this harmonic oscillator, for $m = 1$ and
;;   $k = 1$, are

;; $$
;;   {q(t) = A\,\,\text{cos}(t + \varphi)} \tag{1.11}
;; $$

;;   where the amplitude $A$ and the phase $φ$ are determined by the initial
;;   conditions. For the chosen endpoint conditions the solution is $q(t) =
;;   cos(t)$. The approximate path should be an approximation to cosine over the
;;   range from $0$ to $π/2$. (Figure 1.1) shows the error in the polynomial
;;   approximation produced by this process. The maximum error in the
;;   approximation with three intermediate points is less than $1.7 × 10^{−4}$. We
;;   find, as expected, that the error in the approximation decreases as the
;;   number of intermediate points is increased. For four intermediate points it
;;   is about a factor of 15 better.

;;   ### Exercise 1.5: Solution process

;;   We can watch the progress of the minimization by modifying the procedure
;;   parametric-path-action to plot the path each time the action is computed. Try
;;   this:

;;   ### Exercise 1.6: Minimizing action

;;   Suppose we try to obtain a path by minimizing an action for an impossible
;;   problem. For example, suppose we have a free particle and we impose endpoint
;;   conditions on the velocities as well as the positions that are inconsistent
;;   with the particle being free. Does the formalism protect itself from such an
;;   unpleasant attack? You may find it illuminating to program it and see what
;;   happens.

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

;;### Lagrange equations

;;   We will find that if $L$ is a Lagrangian for a system that depends on time,
;;   coordinates, and velocities, and if $q$ is a coordinate path for which the
;;   action $S[q](t_{1}, t_{2})$ is stationary (with respect to any
;;   variation in the path that keeps the endpoints of the path fixed), then

;;   $$
;;   {D(\partial_{2}L \circ \Gamma\lbrack q\rbrack) - \partial_{1}L \circ \Gamma\lbrack q\rbrack = 0.} \tag{1.12}
;;   $$

;;   Here $L$ is a real-valued function of a local tuple; $∂_{1}L and $∂_{2}L
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

;;   Lagrange's equations are traditionally written as a separate equation for
;;   each component of $q$:

;;   $$
;; {\frac{d}{dt}\frac{\partial
;;   L}{\partial{\dot{q}}^{i}} - \frac{\partial L}{\partial q^{i}} = 0} 
;;   \hspace{1cm}{i = 0,\ldots,n - 1.} 
;; $$

;;   In this way of writing Lagrange's equations the notation does not distinguish
;;   between $L$, which is a real-valued function of three variables $(t, q, \dot{q})$, 
;; and $L ∘ Γ[q]$, which is a real-valued function of one real variable
;;   $t$. If we do not realize this notational pun, the equations don't make sense
;;   as written --- $∂L/∂\dot{q}$
;;   is a function of three variables, so we must regard the arguments $q$, $\dot{q}$˙
;; as functions of $t$ before taking $d/dt$ of the expression. Similarly,
;;   $∂L/∂q$ is a function of three variables, which we must view as a function
;;   of $t$ before setting it equal to $d/dt\left( {\partial L/\partial\overset{˙}{q}} \right)$

;;   A correct use of the traditional notation is more explicit:

;; $$\frac{d}{d t}\left( \left.\frac{\partial L(t, w, \dot{w})}{\partial \dot{w}^{i}}
;; \right|_{\substack{ {w=q(t)} \\ {\dot{w}=\frac{d q(t)}{d t}} }}
;; \right)-\left.\frac{\partial L(t, w, \dot{w})}{\partial w^{i}}\right|_{ \substack{
;; w=q(t) \\ {\dot{w}=\frac{d q(t)}{d t}}} }=0.$$

;;   where $i = 0, ..., n − 1$. In these equations we see that the partial
;;   derivatives of the Lagrangian function are taken, then the path and its
;;   derivative are substituted for the position and velocity arguments of the
;;   Lagrangian, resulting in an expression in terms of the time.

;;### 1.5.1 Derivation of the Lagrange Equations
;;    We will show that the principle of stationary action implies that realizable
;;    paths satisfy the Euler--Lagrange equations.

;;#### A Direct Derivation

;;   Let $q$ be a realizable coordinate path from $(t_{1}, q(t_{1}))$ to
;;   $(t_{2}, q(t_{2}))$. Consider nearby paths $q$ + $ϵη$ where $η(t_{1})
;;   = η(t_{2}) = 0$. Let

;; $$
;\begin{array}{lcl}
;{g(\mathit{\epsilon})} & = & {S\lbrack q + \mathit{\epsilon}\eta\rbrack(t_{1},\,\, t_{2})} \\
;;& = & {\int_{t_{1}}^{t_{2}}{L(t,q(t) + \mathit{\epsilon}\eta(t),Dq(t) +
;;   \mathit{\epsilon}D\eta(t))dt.}} \\
;\end{array} \tag{1.13}
;$$


;;   Expanding as a power series in $ϵ$

;;   $$
;;   {g(\mathit{\epsilon}) = g(0) + \mathit{\epsilon}Dg(0) + \cdots} \tag{1.14}
;;   $$

;;   and using the chain rule we get

;; $$
;;\begin{array}{lcl}
;;{Dg(0)} & = & {\int_{t_{1}}^{t_{2}}{(\partial_{1}L(t,q(t),Dq(t))\eta(t))\,\, dt}} \\
;;&  & {+ {\int_{t_{1}}^{t_{2}}{(\partial_{2}L(t,q(t),Dq(t))D\eta(t))\,\, dt.}}} \\
;;\end{array}
;; \tag{1.15}
;;$$

;;   Integrating the second term by parts we obtain

;; $$
;;\begin{array}{lcl}
;;{Dg(0)} & = & 
;;   {\int_{t_{1}}^{t_{2}}{(\partial_{1}L(t,q(t),Dq(t))\eta(t))\,\, dt}} \\
;;&  & {+ \,\partial_{2}L(t,q(t),Dq(t)D\eta(t))\eta(t)|_{t_{1}}^{t_{2}}} \\
;;&  & {-
;;   {\int_{t_{1}}^{t_{2}}{\frac{d}{dt}(\partial_{2}L(t,q(t),Dq(t)))\eta(t)dt}}.}\\
;;\end{array}
;; \tag{1.16}
;; $$

;;   The increment $ΔS$ in the action due to the variation in the path is, to
;;   first order in $ϵ$, $ϵDg(0)$. Because $\eta$ is zero at the endpoints the
;;   integrated term is zero. Collecting together the other two terms, and
;;   reverting to functional notation, we find the increment to be

;;   $$
;;   {\Delta S = \mathit{\epsilon}{\int_{t_{1}}^{t_{2}}\left\{ \partial_{1}L \circ \Gamma\lbrack q\rbrack\, - \, D(\partial_{2}L \circ \Gamma\lbrack q\rbrack) \right\}}\eta.} \tag{1.17}
;;   $$

;;   If $ΔS$ is zero the action is stationary. We retain enough freedom in the
;;   choice of the variation that the factor in the integrand multiplying $\eta$
;;   is forced to be zero at each point along the path. We argue by contradiction:
;;   Suppose this factor were nonzero at some particular time. Then it would have
;;   to be nonzero in at least one of its components. But if we choose our $\eta$
;;   to be a bump that is nonzero only in that component in a neighborhood of that
;;   time, and zero everywhere else, then the integral will be nonzero. So we may
;;   conclude that the factor in curly brackets is identically zero and thus
;;   obtain Lagrange's equations:[fn:48]

;;   $$
;;   {D(\partial_{2}L \circ \Gamma\lbrack q\rbrack)\, - \,\partial_{1}L \circ \Gamma\lbrack q\rbrack = 0.} \tag{1.18}
;;   $$

;;#### The Variation Operator

;;   First we will develop tools for investigating how path-dependent functions
;;   vary as the paths are varied. We will then apply these tools to the action,
;;   to derive the Lagrange equations.

;;   Suppose that we have a function $f[q]$ that depends on a path $q$. How does
;;   the function vary as the path is varied? Let $q$ be a coordinate path and
;;   $q$ + $ϵη$ be a varied path, where the function $\eta$ is a path-like
;;   function that can be added to the path $q$, and the factor $ϵ$ is a scale
;;   factor. We define the *variation* $δ_{η}f[q]$ of the function $f$ on the path
;;   $q$ by[fn:49]

;;   $$
;;   {\delta_{\eta}f\lbrack q\rbrack = \underset{\mathit{\epsilon}\rightarrow 0}{\lim}\left( \frac{f\lbrack q + \mathit{\epsilon}\eta\rbrack - f\lbrack q\rbrack}{\mathit{\epsilon}} \right).} \tag{1.19}
;;   $$

;;   The variation of $f$ is a linear approximation to the change in the function
;;   $f$ for small variations in the path. The variation of $f$ depends on $\eta$.

;; A simple example is the variation of the identity path function: $I[q] =
;;    q$. Applying the definition, we find

;;    $$
;;    {\delta_{\eta}I\lbrack q\rbrack = \underset{\mathit{\epsilon}\rightarrow 0}{\lim}\left( \frac{(q + \mathit{\epsilon}\eta) - q}{\mathit{\epsilon}} \right) = \eta.} \tag{1.20}
;;    $$
