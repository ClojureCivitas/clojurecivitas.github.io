^{:kindly/hide-code true
  :clay {:title "The Circle and Its Shadows: Fourier Intuition for Real Signals"
         :quarto {:author :daslu
                  :draft true
                  :type :post
                  :date "2025-12-14"
                  :category :data
                  :tags [:dsp :fourier :intuition :complex-numbers :rotation :mathematics]}}}
(ns dsp.fourier-circular-intuition
  "A visual, intuitive introduction to why the Fourier transform uses complex numbers."
  (:require [fastmath.signal :as sig]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [tablecloth.api :as tc]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.tableplot.v1.plotly :as plotly]))

^:kindly/hide-code
(kind/hiccup
 [:style
  ".clay-dataset {
  max-height:400px;
  overflow-y: auto;
}
.printedClojure {
  max-height:400px;
  overflow-y: auto;
}
"])

;; # The Circle and Its Shadows

;; If you've encountered the [Fourier transform](https://en.wikipedia.org/wiki/Fourier_transform),
;; you've probably seen the formula with its mysterious **e^(iωt)** term and wondered: why
;; [complex numbers](https://en.wikipedia.org/wiki/Complex_number)? Why not just use regular
;; sine and cosine functions?
;;
;; The answer isn't a mathematical trick—it reveals something profound about waves, rotation,
;; and the nature of periodic processes. This post builds that intuition from the ground up,
;; using only geometry and motion.
;;
;; **No prerequisites required** beyond knowing what sine and cosine look like. No signal
;; processing background needed. We'll discover together why complex numbers are the natural
;; language of waves.

;; ## The Fundamental Process: A Point Going in Circles

;; Let's start with the simplest periodic motion: a point traveling counterclockwise around
;; a circle at constant speed.

;; Generate one complete rotation
(def n-points 400)
(def angles (dfn/* (dfn// (range n-points) (double n-points)) (* 2 Math/PI)))

;; Position on the circle: (cos θ, sin θ)
(def x-positions (dfn/cos angles))
(def y-positions (dfn/sin angles))

;; Visualize the circular path
(-> (tc/dataset {:x x-positions :y y-positions})
    (plotly/base {:=x :x
                  :=y :y
                  :=x-title "Horizontal Position"
                  :=y-title "Vertical Position"
                  :=title "A Point Rotating on a Circle"
                  :=width 450
                  :=height 400})
    plotly/layer-line
    (plotly/layer-point {:=mark-size 8
                         :=mark-opacity 0.5})
    plotly/plot
    (assoc-in [:layout :showlegend] false))

;; This circle is our **fundamental object**. Everything else—sine waves, cosine waves,
;; complex exponentials—derives from this simple rotation.

;; ## Two Shadows: Projections of Rotation

;; Now imagine shining two lights on our rotating point:
;; - One light from **above**, casting a shadow on the horizontal axis
;; - Another light from the **side**, casting a shadow on the vertical axis
;;
;; As the point rotates, each shadow moves back and forth along its axis. Let's trace these
;; shadows over time.

(def time-points
  ;; Normalized time 0 to 1
  (dfn// (range n-points) (double n-points))) 

;; Create dataset with both projections
(def projections-data
  (tc/dataset {:time time-points
               ;; Cosine - the horizontal shadow
               :horizontal x-positions
               ;; Sine - the vertical shadow
               :vertical y-positions})) 

;; Visualize both shadows
(-> projections-data
    (plotly/base {:=x :time
                  :=y :horizontal
                  :=x-title "Time"
                  :=y-title "Shadow Position"
                  :=title "Two Shadows of One Rotation"
                  :=width 700
                  :=height 300})
    (plotly/layer-line {:=mark-color "steelblue"
                        :=name "Horizontal shadow (cosine)"})
    (plotly/layer-line {:=y :vertical
                        :=mark-color "orange"
                        :=name "Vertical shadow (sine)"}))

;; **Key insight**: The horizontal shadow traces **cosine**, the vertical shadow traces **sine**.
;; But they're not two separate things—they're two views of the **same circular motion**.
;;
;; Cosine and sine are **projections**, not primitives. The circle is more fundamental.

;; ## The Problem: One Shadow Loses Direction

;; Here's the fundamental issue. Imagine two points rotating at the same speed but in
;; **opposite directions**:
;; - Point A: rotating **counterclockwise** (upward when moving right)
;; - Point B: rotating **clockwise** (downward when moving right)
;;
;; Watch their horizontal shadows. At any moment when both shadows are at the same position,
;; **you cannot tell which direction each point is rotating** by looking at horizontal position
;; alone.
;;
;; The horizontal shadow (cosine) captures **frequency** (speed of rotation) but loses
;; **direction** (sign of the vertical component). You need **both shadows** to distinguish
;; clockwise from counterclockwise.

;; Let's see this with a concrete example that matters for Fourier decomposition.

;; Two different signals, same frequency, different "direction"
(def demo-time (dfn// (range 400) 100.0))
(def freq 2.0) ; 2 Hz

;; Signal A: cos(2πft) - rotation starting at 3 o'clock, going counterclockwise
(def signal-a (dfn/cos (dfn/* 2.0 Math/PI freq demo-time)))

;; Signal B: cos(2πft + π) = -cos(2πft) - rotation starting at 9 o'clock, going counterclockwise
;; This is like rotating in the opposite direction
(def signal-b (dfn/* -1.0 (dfn/cos (dfn/* 2.0 Math/PI freq demo-time))))

;; Visualize both signals
(-> (tc/concat
     (tc/dataset {:time demo-time :amplitude signal-a :signal "Signal A: cos(2πft)"})
     (tc/dataset {:time demo-time :amplitude signal-b :signal "Signal B: -cos(2πft)"}))
    (plotly/base {:=x :time
                  :=y :amplitude
                  :=color :signal
                  :=x-title "Time (seconds)"
                  :=y-title "Amplitude"
                  :=title "Two Different Signals at Same Frequency"
                  :=width 700
                  :=height 300})
    (plotly/layer-line))

;; **Key observation**: These are **different signals** - one is the negative of the other.
;; But if we only look at their **magnitude spectrum** (which ignores phase), they appear
;; identical.
;;
;; Let's prove this with a more dramatic example using actual Fourier decomposition.

;; ## Demonstrating Information Loss: Why Phase Matters

;; Here are two completely different signals at the same frequency:

;; Signal 1: cos(2πft) - starts at maximum, phase = 0
(def t-demo (dfn// (range 400) 100.0))
(def signal-cos (dfn/cos (dfn/* 2.0 Math/PI 5.0 t-demo)))

;; Signal 2: sin(2πft) = cos(2πft - π/2) - starts at zero rising, phase = -π/2
(def signal-sin (dfn/sin (dfn/* 2.0 Math/PI 5.0 t-demo)))

;; Visualize both
(-> (tc/concat
     (tc/dataset {:time t-demo :amplitude signal-cos :signal "cos(2π·5t) - starts at max"})
     (tc/dataset {:time t-demo :amplitude signal-sin :signal "sin(2π·5t) - starts at zero"}))
    (plotly/base {:=x :time
                  :=y :amplitude
                  :=color :signal
                  :=x-title "Time"
                  :=y-title "Amplitude"
                  :=title "Different Signals, Same Frequency and Magnitude"
                  :=width 700
                  :=height 300})
    (plotly/layer-line))

;; **Critical fact**: These signals have:
;; ✓ Same frequency (5 Hz)
;; ✓ Same magnitude spectrum (both will show amplitude 1.0 at 5 Hz)
;; ✗ Different phase spectrum (0° vs -90°)
;; ✗ **Completely different values at every time point!**

;; Check the difference:
(def difference-at-start (dfn/- (first signal-cos) (first signal-sin)))
;; cos(0) - sin(0) = 1.0 - 0.0 = 1.0

(kind/hiccup
 [:div
  [:p [:strong "At t=0:"]]
  [:ul
   [:li (str "cos signal: " (format "%.3f" (first signal-cos)))]
   [:li (str "sin signal: " (format "%.3f" (first signal-sin)))]
   [:li (str "difference: " (format "%.3f" difference-at-start))]]])

;; **The fatal problem**: If you run a Fourier transform and **throw away the phase** (keeping
;; only magnitudes), you cannot tell these signals apart. You've lost the information about
;; whether the rotation started at 3 o'clock (cosine) or 12 o'clock going right (sine).
;;
;; Without phase, the Fourier transform becomes **non-invertible** - you cannot reconstruct
;; the original signal. This isn't a mathematical quirk - it's fundamental information loss.
;;
;; Complex numbers solve this by preserving **both** the horizontal shadow (real part) and
;; vertical shadow (imaginary part), which together uniquely specify the rotation and its
;; starting angle.

;; ## The Speed of Rotation: Frequency

;; So far we've looked at rotation at one speed. But real-world signals are combinations of
;; rotations at **different speeds**.
;;
;; The **speed of rotation** is what we call [frequency](https://en.wikipedia.org/wiki/Frequency):
;; - Slow rotation = low frequency (low musical pitch, long period)
;; - Fast rotation = high frequency (high musical pitch, short period)

;; Let's visualize rotations at different speeds:

(defn rotation-at-frequency [freq duration sample-rate]
  (let [n (int (* duration sample-rate))
        t (dfn// (range n) sample-rate)
        theta (dfn/* 2.0 Math/PI freq t)]
    {:time t
     :position (dfn/cos theta)
     :frequency freq}))

(def freq-comparison
  (let [{:keys [time position]} (rotation-at-frequency 1.0 2.0 50.0)
        {time-3 :time position-3 :position} (rotation-at-frequency 3.0 2.0 50.0)
        {time-5 :time position-5 :position} (rotation-at-frequency 5.0 2.0 50.0)]
    (tc/concat
     (tc/dataset {:time time :position position :frequency "1 Hz (slow)"})
     (tc/dataset {:time time-3 :position position-3 :frequency "3 Hz (medium)"})
     (tc/dataset {:time time-5 :position position-5 :frequency "5 Hz (fast)"}))))

(-> freq-comparison
    (plotly/base {:=x :time
                  :=y :position
                  :=color :frequency
                  :=x-title "Time (seconds)"
                  :=y-title "Horizontal Shadow"
                  :=title "Different Rotation Speeds = Different Frequencies"
                  :=width 700
                  :=height 300})
    (plotly/layer-line))

;; **Key concept**: Frequency is just **how fast the point goes around the circle**.
;; Higher frequency = faster rotation = more cycles per second.

;; ## Combining Rotations: Superposition

;; Here's where it gets beautiful. Imagine **multiple points** rotating at different speeds,
;; all on their own circles. Now imagine **adding up all their horizontal shadows** at each
;; moment in time.
;;
;; This is [superposition](https://en.wikipedia.org/wiki/Superposition_principle): the combined
;; shadow is just the sum of individual shadows.

;; Create a composite signal: 1 Hz + 3 Hz
(def composite-time (dfn// (range 400) 100.0))
(def component-1hz (dfn/cos (dfn/* 2.0 Math/PI 1.0 composite-time)))
(def component-3hz (dfn/* 0.6 (dfn/cos (dfn/* 2.0 Math/PI 3.0 composite-time))))
(def composite-signal (dfn/+ component-1hz component-3hz))

(def superposition-data
  (tc/concat
   (tc/dataset {:time composite-time :amplitude component-1hz :component "1 Hz component"})
   (tc/dataset {:time composite-time :amplitude component-3hz :component "3 Hz component"})
   (tc/dataset {:time composite-time :amplitude composite-signal :component "Combined signal"})))

(-> superposition-data
    (plotly/base {:=x :time
                  :=y :amplitude
                  :=color :component
                  :=x-title "Time (seconds)"
                  :=y-title "Amplitude"
                  :=title "Superposition: Adding Two Rotations"
                  :=width 700
                  :=height 350})
    (plotly/layer-line))

;; **Profound realization**: Any complex wave pattern can be built by **adding up circular
;; motions at different speeds**. The combined shadow (the signal we see) is just the sum
;; of many rotating points, each going at its own pace.

;; ## The Fourier Idea: Decomposition

;; Now flip the question around. Given a signal—any wiggly line varying over time—can we
;; **decompose** it back into its constituent rotations?
;;
;; **Can we find which circles, rotating at which speeds, when added together, produce this signal?**
;;
;; This is the Fourier transform. It answers: "What rotations are hidden in this signal?"
;;
;; For each possible frequency ω:
;; - Is there a rotation at speed ω contributing to this signal?
;; - If yes, how strong is it (amplitude)?
;; - At what angle did it start (phase)?

;; To answer these questions, we need to describe each rotation completely. And we've already
;; seen the problem: tracking only the horizontal shadow (cosine) loses the phase information.
;;
;; **We need both shadows**.

;; ## Why Complex Numbers: Embracing Both Shadows

;; This is where [complex numbers](https://en.wikipedia.org/wiki/Complex_number) enter—not as
;; mathematical abstraction, but as **practical necessity**.
;;
;; A complex number packages **two real numbers** together:
;; - **Real part**: the horizontal shadow (cosine projection)
;; - **Imaginary part**: the vertical shadow (sine projection)
;;
;; By using complex numbers, we can represent the **complete rotation**, not just one projection.

;; Here's the beautiful notation. A point rotating at frequency ω is described by:
;;
;; **e^(iωt) = cos(ωt) + i·sin(ωt)**
;;
;; This is [Euler's formula](https://en.wikipedia.org/wiki/Euler%27s_formula). It says:
;; - Real part = cos(ωt) = horizontal shadow
;; - Imaginary part = sin(ωt) = vertical shadow
;; - Together = complete rotation at frequency ω

;; Let's visualize what this means in the complex plane:


(def omega
  ;; Rotation speed (frequency)
  2.0)
(def demo-time (dfn// (range 400) 80.0))
(def demo-theta (dfn/* omega demo-time))

(def complex-plane-data
  (tc/dataset {:real (dfn/cos demo-theta)
               :imag (dfn/sin demo-theta)
               :time demo-time}))

(-> complex-plane-data
    (plotly/base {:=x :real
                  :=y :imag
                  :=color :time
                  :=x-title "Real Part (horizontal shadow)"
                  :=y-title "Imaginary Part (vertical shadow)"
                  :=title "Complex Plane: e^(iωt) Traces a Circle"
                  :=width 450
                  :=height 450})
    (plotly/layer-point {:=mark-size 6}))

;; **What you're seeing**: Each point represents a moment in time. The real coordinate is the
;; horizontal shadow, the imaginary coordinate is the vertical shadow. As time progresses,
;; the point traces out a circle—the actual rotation.
;;
;; **The complex number IS the rotating point**. Not a representation of it, not an encoding—
;; it literally is the geometric object we started with.

;; ## Why This Matters: The Fourier Transform

;; When you compute the Fourier transform of a real-valued signal, it returns **complex numbers**
;; because it's telling you about **rotations**:
;;
;; "At frequency ω, there's a rotation with:
;; - Strength (amplitude): √(real² + imag²)
;; - Starting angle (phase): arctan(imag/real)
;; - Complete description: real + i·imag"
;;
;; If the Fourier transform only returned real numbers, it would be like reporting "there's
;; rotation at 3 Hz" without saying which direction it started. You'd lose the phase, making
;; the transform **non-invertible**—you couldn't reconstruct the original signal.

;; ## The Physical Reality

;; This isn't just mathematics. **Real physical processes are circular**:
;; - A pendulum sweeps out circular motion (projected onto linear swing)
;; - An electromagnetic wave is rotating electric and magnetic fields
;; - A guitar string vibrates because molecules rotate in circular patterns
;; - Even quantum mechanics: particles described by rotating complex amplitudes
;;
;; Complex numbers aren't "imaginary" or artificial. They're the natural way to describe
;; **rotation in a plane**. Real numbers can only capture one axis at a time—they give you
;; shadows, not the substance.

;; ## A Glimpse Forward

;; This post focused on the **continuous Fourier transform** for real-world signals varying
;; smoothly over time. But the circular intuition extends further:
;;
;; - **Fourier series**: Periodic signals decomposed into rotations (working on the circle itself)
;; - **Discrete Fourier Transform (DFT)**: Finite digital signals (rotations on a finite cyclic group)
;; - **FFT**: The fast algorithm for computing DFT (organizational efficiency, same math)
;;
;; Future posts will explore these connections, clarifying how they all relate to the same
;; fundamental idea: **decomposition into circular motions**.

;; ## The Core Insight

;; **Waves are rotations**.
;;
;; Sine and cosine aren't the fundamental objects—they're **shadows of rotation**, projections
;; onto horizontal and vertical axes. The circle is simpler, more fundamental.
;;
;; Complex numbers aren't mysterious. They're just a way to hold **both projections simultaneously**,
;; preserving the complete circular motion. When Fourier transforms return complex numbers,
;; they're telling you about rotations: which frequencies are present, how strong they are,
;; and at what angle they started.
;;
;; Real numbers give you shadows. Complex numbers give you the circle itself.
