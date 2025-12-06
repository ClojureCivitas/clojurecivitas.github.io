^{:kindly/hide-code true
  :clay             {:title  "Linear 1D Advection"
                     :quarto {:author   :luke-zeitlin
                              :image    "osc.png"
                              :type     :post
                              :date     "2025-12-06"
                              :category :physics
                              :tags     [:simulation :math :physics]}}}
(ns physics.numerical-methods
  (:require
   [scicloj.kindly.v4.kind :as kind]))


^:kindly/hide-code
(kind/scittle
 '(defn plot-wave
   "Render wave points as SVG path"
   [points width height]
   (let [path-data (apply str
                          (str "M " (first (first points)) " " (second (first points)) " ")
                          (for [[x y] (rest points)]
                            (str "L " x " " y " ")))]
     [:svg {:width width :height height}
      [:path {:d path-data
              :stroke "blue"
              :stroke-width 2
              :fill "none"}]])))

^:kindly/hide-code
(kind/scittle
 '(defn make-centered-display
    "Display function centered at given y-coordinate (for periodic waves)"
    [height]
    (let [center-y (/ height 2)]
      (fn [[x-points u]]
        (map-indexed (fn [j u-val]
                       [(nth x-points j) (+ center-y u-val)])
                     u)))))

^:kindly/hide-code
(kind/scittle
 '(defn make-baseline-display
    "Display function with baseline offset from bottom (for pulses)"
    [height]
    (let [baseline-offset 15]
     (fn [[x-points u]]
       (let [baseline (- height baseline-offset)]
         (map-indexed (fn [j u-val]
                        [(nth x-points j) (- baseline u-val)])
                      u))))))


^:kindly/hide-code
(kind/scittle
 '(defn wave-slider
    "Reagent component with time slider for wave visualization"
    [update-fn width height max-t step-t display-fn]
    (fn []
      (let [t (reagent.core/atom 0)]
        (fn []
          [:div {:style {:width width}}
           [:label "Time (t): " [:strong @t]]
           [:input {:type "range"
                    :min 0
                    :max max-t
                    :step step-t
                    :value @t
                    :on-change #(reset! t (-> % .-target .-value js/parseFloat))
                    :style {:width "100%"}}]
           (plot-wave (-> @t update-fn ((display-fn height))) width height)])))))

;; # Numerical Methods with the Advection Equation

;; The 1D linear advection equation:


^:kindly/hide-code
(kind/tex
 "\\frac{\\partial u}{\\partial t} + c \\frac{\\partial u}{\\partial x} = 0")

;; Where $c$ determines how fast the wave propagates (negative values propagate backwards).
;;
;; If we know the initial condition, the exact solution for a given $t$ is simply:

^:kindly/hide-code
(kind/tex
 "u(x, t) = u_0(x - ct)")

;; This shows that the profile of the wave remains the same but it is translated along
;; the x-axis over time.
;;
;; > **Note:** Display logic for the charts below has been elided. The complete code can be found [on GitHub](https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/physics/wave_equation.clj)

(kind/scittle
 '(defn xs->sin-ys
    [amplitude frequency xs]
    (mapv #(* amplitude (js/Math.sin (* % frequency))) xs)))

(kind/scittle
 '(defn advected-sine-wave
    [width height amplitude frequency c t]
    (let [dx 2
          xs (vec (range 0 (inc width) dx))
          shifted-xs (map #(- % (* c t)) xs)
          u-vals (xs->sin-ys amplitude frequency shifted-xs)]
      [xs u-vals])))


^:kindly/hide-code
(kind/reagent
 ['(let [width 300
         height 100
         amplitude 30
         frequency 0.05
         c 20
         slider-interval 0.1
         max-t 20]
     (wave-slider
      (fn [t] (advected-sine-wave width height amplitude frequency c t))
      width height max-t slider-interval
      make-centered-display))])

;; ## Numerical methods
;; We already have an exact solution, so there is no need to approximate
;; it with numerical methods. However, this is a nice, simple example to use as an
;; experiment to build intuition for the trade-offs of different numerical methods
;; that will be important for more complicated PDEs that do not have analytical
;; solutions.
;; ### Forward-Time Central-Space Euler Method
;; Our 1D advection system is time dependent, so let's try to use a FTCS method
;; to solve it.
;;
;; First we discretise the grid:

^:kindly/hide-code
(kind/tex "t_0, t_1, t_2, \\ldots \\text{ where } t_n = t_0 + n \\cdot \\Delta t")

^:kindly/hide-code
(kind/tex "x_0, x_1, x_2, \\ldots \\text{ where } x_j = x_0 + j \\cdot \\Delta x")


;; Then we substitute the derivatives with finite differences:

;; #### Time derivative substitution (Forward Euler)
;; The change of the value $u$ at a given time is approximately the difference between
;; the value at the next time and the current value, divided by the time interval.

^:kindly/hide-code
(kind/tex "\\frac{\\partial u}{\\partial t} \\approx \\frac{u_j^{n+1} - u_j^n}{\\Delta t}")

;; #### Space derivative substitution (Central difference)
;; The spatial derivative is approximated using the centered difference between neighbouring points.
^:kindly/hide-code
(kind/tex "\\frac{\\partial u}{\\partial x} \\approx \\frac{u_{j+1}^n - u_{j-1}^n}{2\\Delta x}")

;; #### The full substitution into the PDE

^:kindly/hide-code
(kind/tex "\\frac{\\partial u}{\\partial t} + c\\frac{\\partial u}{\\partial x} = 0 \\quad \\Rightarrow \\quad \\frac{u_j^{n+1} - u_j^n}{\\Delta t} + c\\frac{u_{j+1}^n - u_{j-1}^n}{2\\Delta x} = 0")

;; Rearrange in terms of the next step $u_j^{n+1}$ and introduce the Courant number $C = c\Delta t/\Delta x$:
^:kindly/hide-code
(kind/tex "u_j^{n+1} = u_j^n - \\frac{C}{2}(u_{j+1}^n - u_{j-1}^n)")


(kind/scittle
 '(defn ftcs-update
    "FTCS update function (unconditionally unstable for advection)"
    [u C n-points]
    (vec (for [j (range n-points)]
           (let [j-left (mod (dec j) n-points)
                 j-right (mod (inc j) n-points)
                 u-left (nth u j-left)
                 u-right (nth u j-right)
                 u-center (nth u j)]
             (- u-center
                (* (/ C 2) (- u-right u-left))))))))

^:kindly/hide-code
(kind/scittle
 '(defn precompute-advection
    "Generic precompute for advection with any method and initial condition"
    [width height c max-t dx dt initial-u-fn update-fn]
    (let [C (/ (* c dt) dx)
          n-steps (int (/ max-t dt))
          x-points (vec (range 0 (inc width) dx))
          n-points (count x-points)
          u0 (initial-u-fn x-points)
          all-steps (loop [step 0
                           u u0
                           steps [u0]]
                      (if (>= step n-steps)
                        steps
                        (let [u-next (update-fn u C n-points)]
                          (recur (inc step) u-next (conj steps u-next)))))]
      (fn [t]
        (let [step-index (min (int (/ t dt)) (dec (count all-steps)))]
          [x-points (nth all-steps step-index)])))))


^:kindly/hide-code
(kind/reagent
 ['(fn []
     (let [width 300
           height 100
           amplitude 30
           frequency 0.05
           c 20
           slider-interval 0.1
           dx 2
           dt 0.01
           max-t 10
           init-fn (partial xs->sin-ys amplitude frequency)
           wave-lookup (precompute-advection width
                                             height
                                             c
                                             max-t
                                             dx
                                             dt
                                             init-fn
                                             ftcs-update)]
       (wave-slider wave-lookup
                    width
                    height
                    max-t
                    slider-interval
                    make-centered-display)))])


;; ### Instability of FTCS for advection

;; We can see that this method is unstable. FTCS is not well suited to advection
;; because it is not directional. It looks both left and right. However, advection
;; is directional - information is flowing from left to right.
;;
;; #### FTCS with a Gaussian wave
;; Let's test FTCS with a different wave shape - a Gaussian pulse - to see if the
;; instability is specific to sinusoidal waves or applies more generally.

(kind/scittle
 '(defn make-gaussian-initial
    "Create Gaussian pulse initial condition function"
    [amplitude center sigma]
    (fn [x-points]
      (vec (for [x x-points]
             (let [diff (- x center)]
               (* amplitude (js/Math.exp (- (/ (* diff diff) (* 2 sigma sigma)))))))))))


^:kindly/hide-code
(kind/reagent
 ['(fn []
     (let [width 300
           height 100
           amplitude 30
           center 60
           sigma 7
           c 20
           slider-interval 0.1
           dx 2
           dt 0.01
           max-t 10
           init-fn (make-gaussian-initial amplitude center sigma)
           wave-lookup (precompute-advection width
                                             height
                                             c
                                             max-t
                                             dx
                                             dt
                                             init-fn
                                             ftcs-update)]
       (wave-slider wave-lookup
                    width
                    height
                    max-t
                    slider-interval
                    make-baseline-display)))])


;; We can see that the Gaussian pulse also degrades over time, confirming that the
;; instability is not specific to sinusoidal waves.
;;
;; #### Courant Number
;; The Courant number shows how fast information is traveling through the grid.
;; A high Courant number $C > 1$ indicates instability because the FTCS doesn't look
;; far enough forward and backward to propagate the information.
;; However, in both examples above $C = c\Delta t/\Delta x = 20 \times 0.01/2 = 0.1$ which is relatively low.
;; Despite this low Courant number, we still observe instability - suggesting the problem
;; is more fundamental to the FTCS method when applied to advection.
;;
;; #### Von Neumann Stability Analysis
;; In the example above the signal is a simple sinusoid. Since it is unstable for this,
;; it is intuitive to expect that it will be unstable for other, more complicated
;; signals since they can be expressed as a Fourier series. We can demonstrate this with
;; Fourier Stability Analysis which tells how much each *mode* of the signal
;; is amplified from one step to the next.
;;
;; We assume the solution can be written as a Fourier mode:

^:kindly/hide-code
(kind/tex "u_j^n = A^n e^{ikj\\Delta x}")

;; Where:
;; - $k$ is the wavenumber (spatial frequency)
;; - $A$ is the amplification factor (what we want to find)
;; - $j$ is the spatial index
;; - $n$ is the time level
;;
;; ##### Deriving the amplification factor A
;; Substitute the Fourier mode into the FTCS formula:

^:kindly/hide-code
(kind/tex "A^{n+1} e^{ikj\\Delta x} = A^n e^{ikj\\Delta x} - \\frac{C}{2}\\left[A^n e^{ik(j+1)\\Delta x} - A^n e^{ik(j-1)\\Delta x}\\right]")

;; Divide both sides by $A^n e^{ikj\Delta x}$. Simplify (using $A^{n+1}/A^n = A$ and $e^{ikx_1}/e^{ikx_2} = e^{ik(x_1-x_2)}$):

^:kindly/hide-code
(kind/tex "A = 1 - \\frac{C}{2}\\left[e^{ik\\Delta x} - e^{-ik\\Delta x}\\right]")

;; Using Euler's formula $e^{i\theta} - e^{-i\theta} = 2i \sin(\theta)$:

^:kindly/hide-code
(kind/tex "A = 1 - iC\\sin(k\\Delta x)")

;; ##### Computing the magnitude
;; For stability, we need $|A| \leq 1$. It is easier to compute the magnitude squared:

^:kindly/hide-code
(kind/tex "|A|^2 = 1^2 + (C\\sin(k\\Delta x))^2 = 1 + C^2\\sin^2(k\\Delta x)")

;; ##### Unconditional instability
;; Since $\sin^2(k\Delta x) \geq 0$, we always have $|A|^2 \geq 1$, with equality only for the
;; trivial mode $k = 0$. This proves **FTCS is unconditionally unstable** for
;; advection - errors will grow exponentially regardless of how small we make C!
;;
;; In our simulation with $C = 0.1$, a typical mode has:

^:kindly/hide-code
(kind/tex "|A|^2 \\approx 1 + 0.1^2 = 1.01")

;; This means errors grow by ~1% per time step, compounding over 1000 steps to
;; produce the degradation visible in the visualization above.
;;
;; ## Upwind Method
;; The fundamental problem with FTCS is that it uses a central difference in space,
;; looking both left and right equally. However, for advection with $c > 0$, information
;; flows from left to right - the solution at point $j$ depends only on what's upstream
;; (to the left).
;;
;; The **upwind scheme** respects this directionality by using a one-sided difference
;; in the direction opposite to the flow (looking backward/upwind).
;;
;; ### Upwind Discretization
;; For $c > 0$ (rightward flow), we use a backward difference in space:

^:kindly/hide-code
(kind/tex "\\frac{\\partial u}{\\partial x} \\approx \\frac{u_j^n - u_{j-1}^n}{\\Delta x}")

;; This yields the upwind scheme:

^:kindly/hide-code
(kind/tex "\\frac{u_j^{n+1} - u_j^n}{\\Delta t} + c\\frac{u_j^n - u_{j-1}^n}{\\Delta x} = 0")

;; Rearranging:

^:kindly/hide-code
(kind/tex "u_j^{n+1} = u_j^n - C(u_j^n - u_{j-1}^n)")

;; Where $C = c\Delta t/\Delta x$ is the same Courant number as before.
;;
;; ### Upwind Stability
;; The upwind method is **conditionally stable**: it requires $C \leq 1$ (the CFL condition).
;; With our parameters $C = 0.1$, the upwind method should be stable!

(kind/scittle
 '(defn upwind-update
    "Upwind update function for c > 0 (conditionally stable: requires C ≤ 1)"
    [u C n-points]
    (vec (for [j (range n-points)]
           (let [j-left (mod (dec j) n-points)
                 u-left (nth u j-left)
                 u-center (nth u j)]
             (- u-center
                (* C (- u-center u-left))))))))


^:kindly/hide-code
(kind/reagent
 ['(fn []
     (let [width 300
           height 100
           amplitude 30
           center 60
           sigma 7
           c 20
           slider-interval 0.1
           dx 2
           dt 0.01
           max-t 10
           init-fn (make-gaussian-initial amplitude center sigma)
           wave-lookup (precompute-advection width
                                             height
                                             c
                                             max-t
                                             dx
                                             dt
                                             init-fn
                                             upwind-update)]
       (wave-slider wave-lookup
                    width
                    height
                    max-t
                    slider-interval
                    make-baseline-display)))])


;; ### Stability vs Accuracy
;; The upwind method is **stable** (no oscillations!) but you'll notice it's **dissipative** -
;; the wave gradually loses amplitude and gets smoother over time. This is the trade-off:
;; - FTCS: unstable but non-dissipative
;; - Upwind: stable but dissipative (first-order accurate in space)
;;
;; The upwind method adds numerical diffusion that smooths the solution. For accurate
;; long-time simulations, higher-order methods like *Lax-Wendroff* or *flux-limiting* schemes
;; are preferred.

^:kindly/hide-code
(comment
  ,
  (require '[scicloj.clay.v2.api :as clay])

  (clay/make! {:source-path "physics/numerical_methods.clj"
               :live-reload :toggle}))
