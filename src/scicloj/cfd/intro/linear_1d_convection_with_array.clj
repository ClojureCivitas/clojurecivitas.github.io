^{:kindly/hide-code true
  :clay
  {:title  "Simulating 1-D Convection in Clojure — From Equations to Arrays"
   :quarto {:author      :syungb
            :description "A quick exploration to simulate a classic fluid dynamics equation in Clojure using Java arrays."
            :type        :post
            :date        "2025-07-15"
            :category    :clojure
            :tags        [:computationalfluiddynamics :cfd :python :conversion]}}}
(ns scicloj.cfd.intro.linear-1d-convection-with-array
  (:require
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(def tex (comp kindly/hide-code kind/tex))
^:kindly/hide-code
(def compose-plot-data #(into [] (map (fn [x u] (hash-map :x x :y u)) % %2)))
;;
;; Earlier this year I gave a [talk](https://youtu.be/RXr9i-aw0lM?si=NpnGOHh8TjC1gufI) at the first online
;; [Scinoj Light Conference](https://scicloj.github.io/scinoj-light-1/), sharing a [ongoing project](https://github.com/scicloj/cfd-python-in-clojure)
;; to port [Computational Fluid Dynamics(CFD) learning materials](https://github.com/barbagroup/CFDPython)
;; from Python to Clojure.
;;
;; In this post, I'll demonstrate a simple one-dimensional linear [convection](https://en.wikipedia.org/wiki/Convection) simulation implemented
;; in Clojure using Java primitive arrays. This example shows a glimpse into the porting effort,
;; with a focus on expressing numerical simulations using only built-in Clojure functions.
;;
;; ## The Equation
;;
;; _(This section won't take up too much of your time...)_
;;
;; We're going to simulate the 1-D linear convection equation:
;;
(tex "\\frac{\\partial u }{\\partial t} + c \\frac{\\partial u}{\\partial x} = 0")
;;
;; This explains how the flow velocity `u` changes over time `t` and position `x`,
;; with `c` which is the wave speed.
;;
;; Instead of focusing on the physical interpretation(since I am no expert),
;; the main focus on this post will be its implementation side - expressing it numerically and coding it in **Clojure**!
;;
;; Using a finite-difference scheme, the discretized form becomes:
;;
(tex "\\frac{u_i^{n+1} - u_i^n}{\\Delta t} + c \\frac{u_i^n - u_{i-1}^n}{\\Delta x} = 0")
;;
;; Then, solving for `u_i^{n+1}` gives:
;;
(tex "u_i^{n+1} = n_i^n - c \\frac{\\Delta t}{\\Delta x}(u_i^n - u_{i-1}^n)")
;;
;; ## Initial Setup
;;
;; We begin by defining initial simulation parameters to start:
;;
;; * nx: number of sliced steps for spatial point `x` from `x-start` and `x-end`
;; * nt: number of time steps we want to propagate
;; * dx: each sliced `x` calculated from `dx = (x-end - x-start) / (nx - 1)`
;; * dt: sliced each time step
;; * c: speed of wave
;;
(def init-params
  {:x-start 0
   :x-end   2
   :nx      41
   :nt      25
   :dx      (/ (- 2 0) (dec 41))
   :dt      0.025
   :c       1})
;;
;; ### Creating the `x` Grid
;;
;; With the given `init-params` we defined earlier, we create a float-array of spatial points `x`:
;;
(def array-x (let [{:keys [nx x-start x-end]} init-params
                   arr  (float-array nx)
                   step (/ (- x-end x-start) (dec nx))]
               (dotimes [i nx]
                 (aset arr i (float (* i step))))
               arr))
;;
^:kindly/hide-code array-x
;;
;; ### Defining Initial Flow Velocity Condition
;;
;; The initial flow velocity(when `t = 0`) is 2 when `x ∈ [0.5, 1.0]`, and is 1 elsewhere:
;;
(def init-cond-fn #(float (if (and (>= % 0.5) (<= % 1.0)) 2 1)))
;;
(def array-u
  (let [nx (:nx init-params)
        u  (float-array nx)]
    (dotimes [i nx]
      (let [x-i (aget array-x i)
            u-i (init-cond-fn x-i)]
        (aset u i u-i)))
    u))

(def init-array-u (float-array array-u))
^:kindly/hide-code init-array-u
;;
;; We can visualize this initial `u`:
^:kindly/hide-code
(kind/vega-lite
  {:mark     "line"
   :width    500 :height 300
   :encoding {:x {:field "x" :type "quantitative"}
              :y {:field "y" :type "quantitative"}}
   :data     {:values (compose-plot-data array-x init-array-u)}})
;;
;; ### Wait, Why `dotimes`?
;;
;; Since we're working with mutable Java arrays(float-array), `dotimes` is an efficient choice here.
;; Because it gives direct, index-based iteration. And it pairs naturally with `aget` and `aset`
;; for reading and writing array values.
;;
;; ## Implementing and the Simulation
;;
;; With the initial setup complete, we now apply the discretized convection equation at each time step.
;;
;; ### Step Function
;;
;; Given the previous time step's `array-u` and the `init-params`,
;; We compute and mutate the flow velocity in-place:
;;
(defn mutate-linear-convection-u
  [array-u {:keys [nx c dx dt]}]
  (let [u_i (float-array array-u)]
    (dotimes [i (- nx 2)]
      (let [idx     (inc i)
            un-i    (aget u_i idx)
            un-i-1  (aget u_i i)
            new-u-i (float (- un-i (* c (/ dt dx) (- un-i un-i-1))))]
        (aset array-u idx new-u-i))))
  array-u)
;;
;; ### Time Integration
;;
;; We run the step function `nt` times to run our simulation over time.
;;
(defn simulate!
  [array-u {:keys [nt] :as init-params}]
  (loop [n 0]
    (if (= n nt)
      array-u
      (do (mutate-linear-convection-u array-u init-params) (recur (inc n))))))
;;
;; Finally, we visualize the resulting `array-u`:

(simulate! array-u init-params)

^:kindly/hide-code
(kind/vega-lite
  {:mark     "line"
   :width    500 :height 300
   :encoding {:x {:field "x" :type "quantitative"}
              :y {:field "y" :type "quantitative"}}
   :data     {:values (compose-plot-data array-x array-u)}})
;;
;; The plot shows how the flow velocity shifts from left to right over time,
;; while also becoming smoother. Nice!
;;
;; ## The Summary
;;
;; This Simple example demonstrates a simulation process using low-level Java primitive arrays in Clojure.
;;
;; Choosing this approach provided mutable, non-persistent data structures. While this deviates from idiomatic Clojure,
;; it offers significant performance benefits for big-size numerical simulations.
;; However, it comes with trade-offs; by opting for mutability, we give up the guarantees of immutability
;; and structural sharing, making the code less safe and more error-prone.
;;
;; As the porting project continues, we plan to evolve the design to better align with idiomatic Clojure principles.
;; Stay tuned!

(comment
  (require '[scicloj.clay.v2.api :as clay])
  (clay/make! {:source-path "scicloj/cfd/intro/linear_1d_convection_with_array.clj"}))