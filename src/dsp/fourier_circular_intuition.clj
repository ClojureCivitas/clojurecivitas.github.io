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
(def n-points 100)
(def angles (dfn/* (dfn// (range n-points) n-points) (* 2 Math/PI)))

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
                  :=width 400
                  :=height 400})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 8}))

;; This circle is our **fundamental object**. Everything else—sine waves, cosine waves,
;; complex exponentials—derives from this simple rotation.

;; ## Two Shadows: Projections of Rotation

;; Now imagine shining two lights on our rotating point:
;; - One light from **above**, casting a shadow on the horizontal axis
;; - Another light from the **side**, casting a shadow on the vertical axis
;;
;; As the point rotates, each shadow moves back and forth along its axis. Let's trace these
;; shadows over time.

(def time-points (dfn// (range n-points) 10.0)) ; Time in arbitrary units

;; Create dataset with both projections
(def projections-data
  (tc/dataset {:time time-points
               :horizontal x-positions ; Cosine - the horizontal shadow
               :vertical y-positions})) ; Sine - the vertical shadow

;; Visualize both shadows
(-> projections-data
    (plotly/base {:=x :time
                  :=y :horizontal
                  :=x-title "Time"
                  :=y-title "Shadow Position"
                  :=title "Two Shadows of One Rotation"
                  :=width 700
                  :=height 300})
    (plotly/layer-line {:=color "steelblue"
                        :=name "Horizontal shadow (cosine)"})
    (plotly/layer-line {:=y :vertical
                        :=color "orange"
                        :=name "Vertical shadow (sine)"}))

;; **Key insight**: The horizontal shadow traces **cosine**, the vertical shadow traces **sine**.
;; But they're not two separate things—they're two views of the **same circular motion**.
;;
;; Cosine and sine are **projections**, not primitives. The circle is more fundamental.

;; ## The Problem: Shadows Are Incomplete

;; Here's where things get interesting. Suppose I tell you: "I see a horizontal shadow moving
;; back and forth, reaching ±1." Can you tell me what the point on the circle is doing?
;;
;; **No!** The horizontal shadow alone is ambiguous. When the shadow is at +1, the point could be:
;; - At the rightmost point of the circle (3 o'clock position)
;; - Or it could have vertical position +0.5 (northeast somewhere)
;; - Or vertical position -0.5 (southeast somewhere)
;;
;; The shadow gives you **one coordinate**. To know where the point is on the circle, you need
;; **both** horizontal and vertical positions.

;; Let's visualize this ambiguity:

;; Three different rotations, same horizontal shadow
(def rotation-1 {:name "Starting at 3 o'clock" :phase 0.0})
(def rotation-2 {:name "Starting at 2 o'clock" :phase (/ Math/PI 6)})
(def rotation-3 {:name "Starting at 4 o'clock" :phase (- (/ Math/PI 6))})

(defn rotation-with-phase [phase]
  (let [t (dfn// (range 50) 5.0)
        theta (dfn/+ (dfn/* 2.0 Math/PI t) phase)]
    {:time t
     :horizontal (dfn/cos theta)
     :vertical (dfn/sin theta)}))

;; All three have the same frequency, different starting angles (phases)
(def phase-comparison-data
  (concat
   (map (fn [i] (assoc (rotation-with-phase 0.0)
                       :index i
                       :rotation (:name rotation-1)))
        (range 50))
   (map (fn [i] (assoc (rotation-with-phase (/ Math/PI 6))
                       :index i
                       :rotation (:name rotation-2)))
        (range 50))
   (map (fn [i] (assoc (rotation-with-phase (- (/ Math/PI 6)))
                       :index i
                       :rotation (:name rotation-3)))
        (range 50))))

;; Show horizontal projections - they look different!
(-> (tc/dataset (for [rot [rotation-1 rotation-2 rotation-3]
                      :let [{:keys [time horizontal]} (rotation-with-phase (:phase rot))]
                      i (range (count time))]
                  {:time (nth time i)
                   :horizontal (nth horizontal i)
                   :rotation (:name rot)}))
    (plotly/base {:=x :time
                  :=y :horizontal
                  :=color :rotation
                  :=x-title "Time"
                  :=y-title "Horizontal Shadow"
                  :=title "Same Speed, Different Starting Angles → Different Shadows"
                  :=width 700
                  :=height 300})
    (plotly/layer-line))

;; **Observation**: Even though all three rotations have the **same speed** (same frequency),
;; their horizontal shadows look different because they started at different angles.
;;
;; If we only track the horizontal shadow (cosine), we lose information about the **phase**
;; (starting angle). To fully describe the rotation, we need both shadows.

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
  (concat
   (let [{:keys [time position]} (rotation-at-frequency 1.0 2.0 50.0)]
     (map-indexed (fn [i _] {:time (nth time i)
                             :position (nth position i)
                             :frequency "1 Hz (slow)"})
                  time))
   (let [{:keys [time position]} (rotation-at-frequency 3.0 2.0 50.0)]
     (map-indexed (fn [i _] {:time (nth time i)
                             :position (nth position i)
                             :frequency "3 Hz (medium)"})
                  time))
   (let [{:keys [time position]} (rotation-at-frequency 5.0 2.0 50.0)]
     (map-indexed (fn [i _] {:time (nth time i)
                             :position (nth position i)
                             :frequency "5 Hz (fast)"})
                  time))))

(-> (tc/dataset freq-comparison)
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
(def composite-time (dfn// (range 200) 50.0))
(def component-1hz (dfn/cos (dfn/* 2.0 Math/PI 1.0 composite-time)))
(def component-3hz (dfn/* 0.6 (dfn/cos (dfn/* 2.0 Math/PI 3.0 composite-time))))
(def composite-signal (dfn/+ component-1hz component-3hz))

(def superposition-data
  (concat
   (map-indexed (fn [i _] {:time (nth composite-time i)
                           :amplitude (nth component-1hz i)
                           :component "1 Hz component"})
                composite-time)
   (map-indexed (fn [i _] {:time (nth composite-time i)
                           :amplitude (nth component-3hz i)
                           :component "3 Hz component"})
                composite-time)
   (map-indexed (fn [i _] {:time (nth composite-time i)
                           :amplitude (nth composite-signal i)
                           :component "Combined signal"})
                composite-time)))

(-> (tc/dataset superposition-data)
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

(def omega 2.0) ; Rotation speed (frequency)
(def demo-time (dfn// (range 100) 20.0))
(def demo-theta (dfn/* omega demo-time))

(def complex-plane-data
  (map-indexed (fn [i _]
                 {:real (Math/cos (nth demo-theta i))
                  :imag (Math/sin (nth demo-theta i))
                  :time (nth demo-time i)})
               demo-time))

(-> (tc/dataset complex-plane-data)
    (plotly/base {:=x :real
                  :=y :imag
                  :=color :time
                  :=x-title "Real Part (horizontal shadow)"
                  :=y-title "Imaginary Part (vertical shadow)"
                  :=title "Complex Plane: e^(iωt) Traces a Circle"
                  :=width 450
                  :=height 450})
    (plotly/layer-line)
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

;; ---
;;
;; **Next in the series**: 
;; - [Signal Transforms: A Comprehensive Guide](#) - Practical tools for FFT, DCT, wavelets in Clojure
;; - Fourier Theory Across Domains (upcoming) - Continuous, discrete, periodic, and finite cases unified
