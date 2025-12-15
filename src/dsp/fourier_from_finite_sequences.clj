^{:kindly/hide-code true
  :clay {:title "Fourier Transforms from First Principles: The Discrete Story"
         :quarto {:author :daslu
                  :draft true
                  :type :post
                  :date "2025-12-15"
                  :category :data
                  :tags [:dsp :fourier :dft :dct :dst :complex-numbers :rotation :discrete]
                  :image "superposition.png"}}}
(ns dsp.fourier-from-finite-sequences
  "Building Fourier intuition from finite sequences and rotations."
  (:require [fastmath.signal :as sig]
            [fastmath.transform :as t]
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

;; ## Introduction

;; If you work with data—audio, images, sensor readings—you've encountered the Fourier
;; transform. It's described as "decomposing signals into frequencies," but that explanation
;; often obscures more than it reveals. Why complex numbers? What are frequencies, really?
;; Why are there different versions (DFT, DCT, DST)?
;;
;; This post builds the complete intuition from scratch, starting with the simplest possible
;; question: **You have a finite list of numbers. What's a useful way to represent them?**

;; *This post is part of the [Scicloj DSP Study Group](https://scicloj.github.io/docs/community/groups/dsp-study/).*

;; ## Libraries and Namespaces

;; This exploration uses several libraries from the Clojure scientific computing ecosystem:

;; - **[Fastmath](https://github.com/generateme/fastmath)** - Mathematical and signal processing functions
;; - **[dtype-next](https://github.com/cnuernber/dtype-next)** - High-performance numerical computing
;; - **[Tablecloth](https://github.com/scicloj/tablecloth)** - Ergonomic data processing
;; - **[Kindly](https://github.com/scicloj/kindly)** - Literate programming visualization protocol
;; - **[Tableplot](https://github.com/scicloj/tableplot)** - Grammar of graphics for Plotly

(require ; Signal processing utilities
 '[fastmath.signal :as sig]
 ;; Transform functions via [JTransforms](https://github.com/wendykierp/JTransforms)
 '[fastmath.transform :as t]
 ;; High-performance array operations
 '[tech.v3.datatype :as dtype]
 ;; Functional numerical operations
 '[tech.v3.datatype.functional :as dfn]
 ;; Data manipulation and datasets
 '[tablecloth.api :as tc]
 ;; Specifying visual kinds
 '[scicloj.kindly.v4.kind :as kind]
 ;; Layered grammar-of-graphics plotting
 '[scicloj.tableplot.v1.plotly :as plotly])

                                        ;

;; ## The Starting Point: Eight Numbers

;; Suppose you record eight temperature readings, one per hour:

(def temperatures [20.0 22.0 25.0 24.0 21.0 19.0 18.0 19.0])

;; Visualize this as points:

(-> (tc/dataset {:hour (range 8)
                 :temperature temperatures})
    (plotly/base {:=x :hour
                  :=y :temperature
                  :=x-title "Hour"
                  :=y-title "Temperature (°C)"
                  :=title "Eight Temperature Readings"
                  :=width 700
                  :=height 300})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 10})
    plotly/plot)

;; **Question**: Can we represent these eight numbers differently—in a way that reveals
;; patterns like "daily cycle" or "average temperature"?
;;
;; **Answer**: Yes (as we'll discover). Any eight numbers can be represented as a sum of **rotations at different speeds**.

;; ## From Finite to Periodic: The Circle Emerges

;; Here's the key insight: eight measurements **implicitly define a periodic pattern**.
;; After hour 7, the cycle repeats. Hour 8 = hour 0, hour 9 = hour 1, and so on.
;;
;; *(This periodicity is an **assumption** we're making—a choice of representation. We could
;; use other bases like polynomials or wavelets that make different assumptions. But the
;; periodic assumption is natural for cyclic phenomena and leads to the elegant frequency
;; decomposition we're about to explore.)*

;; Visualize this by arranging the measurements around a circle:

(def angles (dfn/* (dfn// (range 8) 8.0) (* 2 Math/PI)))
(def circle-x (dfn/cos angles))
(def circle-y (dfn/sin angles))

(-> (tc/dataset {:x circle-x
                 :y circle-y
                 :temperature temperatures
                 :hour (mapv str (range 8))})
    (plotly/base {:=x :x
                  :=y :y
                  :=color :temperature
                  :=text :hour
                  :=x-title "Position X"
                  :=y-title "Position Y"
                  :=title "Measurements Arranged on a Circle"
                  :=width 500
                  :=height 500})
    (plotly/layer-point {:=mark-size 15})
    (plotly/layer-line {:=mark-color "gray" :=mark-opacity 0.3})
    plotly/plot
    (assoc-in [:layout :yaxis :scaleanchor] "x"))

;; Each hour corresponds to an **angle** around the circle: hour 0 at 0°, hour 1 at 45°,
;; hour 2 at 90°, etc. The pattern **wraps around**.
;;
;; **Fourier methods use circles and rotations to analyze signals which are assumed to be periodic.**

;; ## Rotation as the Fundamental Process

;; Imagine a point traveling around this circle at constant speed. Its position over time
;; traces out a **periodic pattern**.

;; Create one full rotation (slowest possible frequency for N=8):

(def rotation-time (dfn// (range 200) 25.0))
(def rotation-angle (dfn/* 2.0 Math/PI (dfn// rotation-time 8.0)))

;; The point's position has two components:
(def horizontal-position (dfn/cos rotation-angle))
(def vertical-position (dfn/sin rotation-angle))

;; Show both views: circular path and horizontal projection

(def rotation-viz
  (tc/dataset {:time rotation-time
               :x horizontal-position
               :y vertical-position}))

(-> rotation-viz
    (plotly/base {:=x :x
                  :=y :y
                  :=x-title "Horizontal Position"
                  :=y-title "Vertical Position"
                  :=title "Point Rotating on Circle"
                  :=width 450
                  :=height 450})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 6 :=mark-opacity 0.5})
    plotly/plot
    (assoc-in [:layout :yaxis :scaleanchor] "x"))

;; Now watch what happens if we track just the **horizontal position** over time:

(-> rotation-viz
    (plotly/base {:=x :time
                  :=y :x
                  :=x-title "Time (hours)"
                  :=y-title "Horizontal Position"
                  :=title "Horizontal Shadow of Rotation"
                  :=width 700
                  :=height 300})
    (plotly/layer-line {:=mark-color "steelblue"}))

;; **This is a cosine wave**—the horizontal shadow of circular motion.

;; ## Why We Need Both Shadows

;; Here's the crucial question: If I tell you the horizontal position is 0.5, can you
;; tell me where the point is on the circle?
;;
;; **No!** It could be at:
;; - (0.5, +0.866) — upper right
;; - (0.5, -0.866) — lower right
;;
;; (The vertical position ±0.866 comes from the circle constraint: $0.5^2 + y^2 = 1$, so $y = \pm\sqrt{0.75} \approx \pm 0.866$.)
;;
;; You need **both** the horizontal and vertical positions to uniquely specify the point.
;;
;; This is why **complex numbers** appear in Fourier transforms. A complex number
;; packages two real numbers:
;; - **Real part**: horizontal position (cosine)
;; - **Imaginary part**: vertical position (sine)
;;
;; Complex numbers aren't just a convenient notation choice—they're the natural 
;; mathematical object for rotation because their algebra matches the geometry. 
;; When you multiply two complex numbers, you're literally composing rotations 
;; (angles add, magnitudes multiply).

;; ### Complex Multiplication = Rotation

;; Here's what makes complex numbers **more than just pairs of real numbers**:
;;
;; You could represent rotations as 2D vectors $(x, y)$, but then **multiplication has no
;; geometric meaning**. What does $(x_1, y_1) \times (x_2, y_2)$ mean?
;;
;; Complex multiplication is **rotation and scaling**. To see why, remember that any point
;; can be described two ways:
;; - **Cartesian**: (x, y) — how far right, how far up
;; - **Polar**: (r, θ) — how far from origin, what angle
;;
;; In polar form, multiplication is simple: **(r₁, θ₁) × (r₂, θ₂) = (r₁×r₂, θ₁+θ₂)**
;; — multiply distances, add angles. That's rotation!
;;
;; The formula we'll use converts this polar rule into Cartesian coordinates.
;; It looks complicated because we're translating between two coordinate systems,
;; but it's doing something simple: adding angles when you multiply.

;; Multiply two complex numbers
(defn complex-mult [z1 z2]
  (let [r1 (get z1 :real 0.0)
        i1 (get z1 :imag 0.0)
        r2 (get z2 :real 0.0)
        i2 (get z2 :imag 0.0)]
    {:real (- (* r1 r2) (* i1 i2))
     :imag (+ (* r1 i2) (* i1 r2))}))

;; Let's see multiplication as rotation with concrete examples:

;; Example 1: Multiply by -1 (180° rotation)

(def rotated-180
  (complex-mult {:real 1.0 :imag 0.0} ; Point at 0°
                {:real -1.0 :imag 0.0})) ; -1 is at 180°

rotated-180

;; Example 2: Multiply by i (90° rotation)

(def rotated-90
  (complex-mult {:real 1.0 :imag 0.0} ; Point at 0°
                {:real 0.0 :imag 1.0})) ; i is at 90°

rotated-90

;; Example 3: Multiply by -i (-90° rotation)

(def rotated-neg-90
  (complex-mult {:real 1.0 :imag 0.0} ; Point at 0°
                {:real 0.0 :imag -1.0})) ; -i is at -90° (or 270°)

rotated-neg-90

;; Example 4: Multiply by point on unit circle at arbitrary angle
;; (The **unit circle** is the circle with radius 1 centered at the origin—all points at distance 1 from (0,0))

(def z-rotated
  (complex-mult {:real 1.0 :imag 1.0} ; 45° from x-axis, magnitude √2
                {:real 0.0 :imag 1.0})) ; i = 90° rotation (i is on the unit circle)

z-rotated

;; **Complex multiplication = rotation:**

(kind/table
 [{:multiply-by "-1"
   :angle "180°"
   :effect "Rotate 180°"
   :example (str "(" (format "%.1f" (:real rotated-180)) ", " (format "%.1f" (:imag rotated-180)) ")")}
  {:multiply-by "i"
   :angle "90°"
   :effect "Rotate 90° CCW"
   :example (str "(" (format "%.1f" (:real rotated-90)) ", " (format "%.1f" (:imag rotated-90)) ")")}
  {:multiply-by "-i"
   :angle "-90° (270°)"
   :effect "Rotate 90° CW"
   :example (str "(" (format "%.1f" (:real rotated-neg-90)) ", " (format "%.1f" (:imag rotated-neg-90)) ")")}
  {:multiply-by "Any unit circle point"
   :angle "θ"
   :effect "Rotate by θ"
   :example (str "(" (format "%.1f" (:real z-rotated)) ", " (format "%.1f" (:imag z-rotated)) ")")}])

;; Visualize rotation by multiplying several points by i (90° counterclockwise):

(def original-points
  [{:real 1.0 :imag 0.2}
   {:real 0.7 :imag 0.4}
   {:real 0.4 :imag 0.6}
   {:real 0.2 :imag 0.8}])

(def rotated-points
  (mapv #(complex-mult % {:real 0.0 :imag 1.0}) original-points))

(def rotation-demo-data
  (tc/concat
   ;; Original points
   (tc/dataset (map-indexed
                (fn [i p]
                  {:x (:real p)
                   :y (:imag p)
                   :type "Original points"
                   :label (str "P" i)})
                original-points))
   ;; Rotated points
   (tc/dataset (map-indexed
                (fn [i p]
                  {:x (:real p)
                   :y (:imag p)
                   :type "After ×i (rotated 90°)"
                   :label (str "P" i "′")})
                rotated-points))))

(-> rotation-demo-data
    (plotly/base {:=x :x
                  :=y :y
                  :=color :type
                  :=mark-symbol :type
                  :=x-title "Real Part"
                  :=y-title "Imaginary Part"
                  :=title "Multiplying by i Rotates All Points 90° Counterclockwise"
                  :=width 500
                  :=height 500})
    (plotly/layer-point {:=mark-size 14})
    plotly/plot
    (assoc-in [:layout :yaxis :scaleanchor] "x"))

;; **Key insight:** Multiplying by a number on the unit circle (magnitude 1) performs a pure rotation
;; (no scaling—just rotation).

;; ### Euler's Formula: The Compact Notation

;; You'll often see rotations written using **[Euler's formula](https://en.wikipedia.org/wiki/Euler%27s_formula)**:
;;
;; **$e^{i\theta} = \cos(\theta) + i \cdot \sin(\theta)$**
;;
;; This expresses a point at angle $\theta$ on the unit circle. It's not a new concept—it's
;; just compact representation for what we've been doing: the real part is $\cos(\theta)$ (horizontal)
;; and the imaginary part is $\sin(\theta)$ (vertical).
;;
;; The exponential is powerful because it makes the rotation properties explicit:
;; - Multiplication by $e^{i\theta}$ is rotation by angle $\theta$
;; - Multiplying $e^{i\theta_1} \times e^{i\theta_2} = e^{i(\theta_1+\theta_2)}$ composes rotations—angles add
;; - Multiplication by $e^{-i\theta}$ is rotation in the opposite direction (conjugate)
;;
;; We'll use this notation when we write the DFT formula, but remember: it's describing the
;; same geometric rotation we've been visualizing.

;; ## Different Speeds: The Frequency Spectrum

;; So far we've seen one rotation completing every 8 hours (one full cycle per period).
;; But we can have rotations at **different speeds**:
;; - 0 rotations per period (constant/DC component)
;; - 1 rotation per period (fundamental frequency)
;; - 2 rotations per period (first harmonic)
;; - 3 rotations per period (second harmonic)
;; - ... up to N/2 rotations per period ([Nyquist frequency](https://en.wikipedia.org/wiki/Nyquist_frequency) - the fastest oscillation detectable with N samples)

;; Let's visualize three different rotation speeds:

(defn rotation-at-frequency [k n-samples sample-rate desc]
  "Create rotation at frequency k for N-point DFT"
  (let [t (dfn// (range n-samples) sample-rate)
        theta (dfn/* 2.0 Math/PI k (dfn// t 8.0))]
    (tc/dataset
     {:time t
      :position (dfn/cos theta)
      :frequency k
      :description desc})))

(-> (tc/concat
     (rotation-at-frequency 0 200 25.0 "k=0 (DC, no rotation)")
     (rotation-at-frequency 1 200 25.0 "k=1 (1 cycle per period)")
     (rotation-at-frequency 2 200 25.0 "k=2 (2 cycles per period)"))
    (plotly/base {:=x :time
                  :=y :position
                  :=color :description
                  :=x-title "Time (hours)"
                  :=y-title "Horizontal Position"
                  :=title "Different Rotation Speeds"
                  :=width 700
                  :=height 350})
    (plotly/layer-line))

;; **Frequency** is just **how many times the point goes around the circle during one period**.
;; That's it. No mysterious wave physics—just counting rotations.

;; ### What k=N/2 Means: The Nyquist Frequency

;; **k=N/2** (for our 8-sample signal, k=4) is the **fastest oscillation detectable**. It rotates
;; N/2 times per period, which means it alternates every single sample: +1, -1, +1, -1, ...
;;
;; Why is this the limit? Because with discrete samples, you can only detect changes between sample
;; points. If a rotation goes faster than "flip every sample," it becomes indistinguishable from
;; slower rotations—a phenomenon called **[aliasing](https://en.wikipedia.org/wiki/Aliasing)**.
;;
;; **Example**: Imagine sampling a rotation at 6 cycles per period with 8 samples. The sample points
;; land at angles: 0°, 270°, 180°, 90°, 0°, 270°, 180°, 90°. But these are the **same angles** you'd
;; get from 2 cycles per period going the opposite direction! You can't tell them apart from samples alone.
;;
;; The Nyquist frequency k=N/2 is the boundary: anything faster gets "folded back" and appears as a slower frequency.

;; ## The Fundamental Theorem: Superposition

;; Here's the remarkable fact: **any** periodic pattern can be created by adding up
;; rotations at these specific speeds.
;;
;; **Why these N frequencies?** Think of it like mixing colors. To make any color on a screen,
;; you need exactly 3 independent values (red, green, blue). More than 3 is redundant, less than 3
;; can't make all colors. It's a **3-dimensional** space.
;;
;; Similarly:
;; - Eight temperature readings = 8 independent numbers
;; - You need exactly 8 independent "ingredients" to reconstruct any pattern
;; - These 8 rotation frequencies (k=0,1,2,...,7) are those ingredients
;; - They're **independent** (orthogonal—we'll explain this shortly) - each contributes something unique
;; - Together they can build any possible 8-sample pattern
;;
;; Our eight temperatures? They're the sum of (at most) eight rotating points, each
;; going at a different speed (k = 0, 1, 2, ..., 7).

;; Let's demonstrate by building a pattern from just two rotations:

(def demo-time (dfn// (range 200) 25.0))
(def rotation-1 (dfn/* 1.0 (dfn/cos (dfn/* 2.0 Math/PI 1.0 (dfn// demo-time 8.0)))))
(def rotation-2 (dfn/* 0.5 (dfn/cos (dfn/* 2.0 Math/PI 2.0 (dfn// demo-time 8.0)))))
(def combined (dfn/+ rotation-1 rotation-2))

(-> (tc/concat
     (tc/dataset {:time demo-time :value rotation-1 :component "k=1 (amplitude 1.0)"})
     (tc/dataset {:time demo-time :value rotation-2 :component "k=2 (amplitude 0.5)"})
     (tc/dataset {:time demo-time :value combined :component "Combined signal"}))
    (plotly/base {:=x :time
                  :=y :value
                  :=color :component
                  :=x-title "Time"
                  :=y-title "Value"
                  :=title "Superposition: Adding Rotations"
                  :=width 700
                  :=height 350})
    (plotly/layer-line))

;; The combined signal looks more complex, but it's just two rotations added together.
;; **Any** pattern with period 8 can be built this way, using up to 8 different rotation speeds.

;; ## The DFT: Finding the Rotations

;; Now flip the question: given eight numbers, **which rotations are hidden inside**?
;;
;; This is what the **Discrete Fourier Transform (DFT)** computes. For each possible
;; rotation speed k, it asks: "How much of rotation k is present?"
;;
;; The answer is a **complex number** $c_k$ that tells you:
;; - **Magnitude** (written $|c_k|$, the distance from origin): How strong is this rotation?
;; - **Phase** (written $\arg(c_k)$, the starting angle): What angle did it start at?

;; Apply DFT to our temperatures:

(def dft-transformer (t/transformer :real :fft))
(def temp-spectrum (t/forward-1d dft-transformer temperatures))

;; Extract magnitude (strength) and phase (starting angle)
(defn spectrum-analysis [dft-result]
  (let [n (/ (count dft-result) 2)]
    (mapv (fn [k]
            (let [real (nth dft-result (* 2 k))
                  imag (nth dft-result (inc (* 2 k)))
                  magnitude (Math/sqrt (+ (* real real) (* imag imag)))
                  phase (Math/atan2 imag real)]
              {:frequency k
               :magnitude magnitude
               :phase-degrees (Math/toDegrees phase)
               :real real
               :imag imag}))
          (range n))))

(def temp-analysis (spectrum-analysis temp-spectrum))

;; Visualize the frequency spectrum:

(-> (tc/dataset temp-analysis)
    (plotly/base {:=x :frequency
                  :=y :magnitude
                  :=x-title "Frequency (cycles per 8 hours)"
                  :=y-title "Magnitude"
                  :=title "Temperature Spectrum: Which Rotations Are Present?"
                  :=width 700
                  :=height 300})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 10}))

;; **Reading this spectrum:**
;; - k=0: The constant component (average temperature, also called DC for "direct current" from electrical engineering)
;; - k=1: Largest oscillating component—one cycle per period (the daily pattern)
;; - k=2, k=3: Smaller contributions (higher harmonics—multiples of the base frequency)
;;
;; **Why is k=1 the dominant oscillation?** The k=0 component (DC) is large because it represents
;; the sum of all temperatures, but it's not an oscillation—it's just the baseline. Among the
;; oscillating components (k≥1), k=1 has by far the largest magnitude, revealing a strong daily cycle.

;; The DFT has revealed that our temperatures are dominated by a **daily cycle** (k=1),
;; with some higher-frequency variation.

;; ## Interactive Exploration: Building Intuition

;; Let's build signals with known frequency content and verify the DFT finds them.

;; Helper function to extract magnitudes from DFT results:

(defn extract-magnitudes [spectrum n]
  (mapv (fn [k]
          (let [real (nth spectrum (* 2 k))
                imag (nth spectrum (inc (* 2 k)))]
            {:frequency k
             :magnitude (Math/sqrt (+ (* real real) (* imag imag)))}))
        (range n)))

;; For these examples, create a 1-second signal sampled at 100 Hz:

(def sample-rate 100.0)
(def duration 1.0)
(def n-samples (int (* sample-rate duration)))
(def sample-times (dfn// (range n-samples) sample-rate))

;; ### Example 1: Sum of Two Pure Frequencies

;; Create a signal with exactly two frequency components: 5 Hz and 15 Hz

(def signal-two-freqs
  (dfn/+ (dfn/sin (dfn/* 2.0 Math/PI 5.0 sample-times))
         (dfn/* 0.5 (dfn/sin (dfn/* 2.0 Math/PI 15.0 sample-times)))))

(-> (tc/dataset {:time (vec sample-times) :value (vec signal-two-freqs)})
    (plotly/base {:=x :time
                  :=y :value
                  :=x-title "Time (seconds)"
                  :=y-title "Amplitude"
                  :=title "Signal: 5 Hz + 0.5×15 Hz"
                  :=width 700
                  :=height 250})
    (plotly/layer-line {:=mark-color "purple"}))

;; The time-domain signal looks complex, but the frequency spectrum should show
;; exactly two peaks:

(def spectrum-two-freqs (t/forward-1d dft-transformer signal-two-freqs))
(def mags-two-freqs (extract-magnitudes spectrum-two-freqs 30))

(-> (tc/dataset mags-two-freqs)
    (plotly/base {:=x :frequency
                  :=y :magnitude
                  :=x-title "Frequency (Hz)"
                  :=y-title "Magnitude"
                  :=title "Spectrum Reveals Two Components"
                  :=width 700
                  :=height 300})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 8}))

;; **Perfect!** Two sharp peaks at k=5 (magnitude ≈50) and k=15 (magnitude ≈25).
;; The magnitudes reflect the amplitudes: 5 Hz has amplitude 1.0, 15 Hz has amplitude 0.5.
;; (The factor of N/2 = 50 comes from DFT scaling conventions.)

;; ### Example 2: Noisy Signal

;; Real-world signals contain noise. Let's add random noise to our two-frequency signal:

(def noise (dfn/* 0.3 (dfn/- (repeatedly n-samples rand) 0.5)))
(def signal-noisy (dfn/+ signal-two-freqs noise))

(-> (tc/dataset {:time (vec sample-times) :value (vec signal-noisy)})
    (plotly/base {:=x :time
                  :=y :value
                  :=x-title "Time (seconds)"
                  :=y-title "Amplitude"
                  :=title "Noisy Signal: 5 Hz + 15 Hz + Random Noise"
                  :=width 700
                  :=height 250})
    (plotly/layer-line {:=mark-color "darkred"}))

;; In the time domain, the pattern is obscured. But the frequency domain reveals structure:

(def spectrum-noisy (t/forward-1d dft-transformer signal-noisy))
(def mags-noisy (extract-magnitudes spectrum-noisy 30))

(-> (tc/dataset mags-noisy)
    (plotly/base {:=x :frequency
                  :=y :magnitude
                  :=x-title "Frequency (Hz)"
                  :=y-title "Magnitude"
                  :=title "Spectrum: Signal Peaks Above Noise Floor"
                  :=width 700
                  :=height 300})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 6}))

;; **Observation**: The 5 Hz and 15 Hz peaks are still clearly visible, rising above
;; a "noise floor" (the baseline level from random contributions).
;;
;; **This is the power of frequency analysis**: patterns hidden in noisy time-domain data
;; become obvious peaks in the spectrum. The DFT separates signal from noise.

;; ### Example 3: Non-Integer Frequency (Leakage in Action)

;; Create a signal at 7.3 Hz (doesn't align with DFT bins)

(def signal-non-integer (dfn/sin (dfn/* 2.0 Math/PI 7.3 sample-times)))
(def spectrum-non-integer (t/forward-1d dft-transformer signal-non-integer))
(def mags-non-integer (extract-magnitudes spectrum-non-integer 20))

(-> (tc/dataset mags-non-integer)
    (plotly/base {:=x :frequency
                  :=y :magnitude
                  :=x-title "Frequency (Hz)"
                  :=y-title "Magnitude"
                  :=title "Non-Integer Frequency: Leakage Spreads Energy"
                  :=width 700
                  :=height 300})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 8}))

;; **Leakage**: Instead of a single peak at 7.3, energy spreads across k=7 and k=8,
;; with smaller contributions to neighbors. The signal "doesn't fit" the DFT's
;; assumption of periodicity, so it requires multiple basis frequencies to approximate.
;;
;; **Solutions**:
;; 1. **Windowing**: Apply Hann/Hamming window to reduce sidelobes (we'll explore this technique later)
;; 2. **Zero-padding**: Increase FFT size to create finer frequency bins
;; 3. **Interpolation**: Use parabolic interpolation to estimate true peak location
;;
;; For practical applications, understanding leakage is crucial for interpreting spectra correctly.

;; ## Measuring Alignment: The Inner Product

;; Before we see how the DFT computes its coefficients, we need one key mathematical idea:
;; **How do we measure if two patterns "align"**?
;;
;; The answer is the **inner product** (also called dot product). For complex numbers,
;; we use the **conjugate** of the first pattern:
;;
;; **$\langle a, b \rangle = \sum_{n} \overline{a_n} \cdot b_n$**
;;
;; The bar over $a_n$ means complex conjugate: flip the sign of the imaginary part.
;;
;; For real numbers, the conjugate does nothing, so this reduces to ordinary multiplication.
;; For complex numbers (rotations), conjugating reverses the rotation direction—this
;; "counter-rotation" makes matching frequencies "stand still," turning them into
;; something easy to measure.
;;
;; This operation has deep geometric meaning:
;;
;; - **Aligned patterns** (move together): large positive sum
;; - **Perpendicular patterns** (unrelated): sum ≈ 0
;; - **Opposite patterns** (move inversely): large negative sum

;; Let's see this concretely with three simple examples:

(def pattern-a [1.0 2.0 3.0 2.0 1.0 0.0 -1.0 0.0])

;; Pattern that aligns with a (same shape)
(def pattern-aligned [0.5 1.0 1.5 1.0 0.5 0.0 -0.5 0.0])
(def inner-aligned (dfn/sum (dfn/* pattern-a pattern-aligned)))
;; Result: large positive number

;; Pattern perpendicular to a (shifted by 90°)
(def pattern-perpendicular [0.0 -1.0 0.0 1.0 2.0 3.0 2.0 1.0])
(def inner-perpendicular (dfn/sum (dfn/* pattern-a pattern-perpendicular)))
;; Result: close to zero

;; Pattern opposite to a (inverted)
(def pattern-opposite [-1.0 -2.0 -3.0 -2.0 -1.0 0.0 1.0 0.0])
(def inner-opposite (dfn/sum (dfn/* pattern-a pattern-opposite)))
;; Result: large negative number

(kind/table
 [{:comparison "Aligned (same direction)"
   :inner-product (format "%.2f" inner-aligned)
   :meaning "Patterns move together"}
  {:comparison "Perpendicular (unrelated)"
   :inner-product (format "%.2f" inner-perpendicular)
   :meaning "No correlation"}
  {:comparison "Opposite (inverted)"
   :inner-product (format "%.2f" inner-opposite)
   :meaning "Patterns move inversely"}])

;; **This is similar to correlation**: measuring whether two sequences "march together."
;; (Correlation also involves standardization, but the core idea is the same.)
;;
;; **Key insight**: The magnitude of the inner product tells you how strongly the patterns align.
;; Large absolute value = strong relationship, small value = unrelated patterns.

;; ### Why N Specific Frequencies?

;; Here's the key mathematical fact: the N rotation speeds we use **don't interfere with each other**.
;; They're like independent directions—mathematicians call this "orthogonal."
;;
;; Let's see this concretely. Take the real parts (cosine components) of two rotations
;; at different frequencies and compute their inner product:

(def freq-1-rotation (dfn/cos (dfn/* 2.0 Math/PI 1.0 (dfn// (range 8) 8.0))))
(def freq-2-rotation (dfn/cos (dfn/* 2.0 Math/PI 2.0 (dfn// (range 8) 8.0))))
(def inner-product-diff-freq (dfn/sum (dfn/* freq-1-rotation freq-2-rotation)))

inner-product-diff-freq

;; The result is zero (or very close)! They're **perpendicular** in the same sense
;; that the x-axis and y-axis are perpendicular. They don't overlap, don't interfere.
;;
;; **Why does this happen?** Frequency k divides the circle into k equal segments. When you
;; multiply two different frequencies (say k₁=1 and k₂=2), you're overlaying two different
;; circular grids. Because both grids are based on whole-cycle divisions, their product has
;; perfect rotational symmetry. Summing around the complete circle, the symmetrical positive
;; and negative regions cancel exactly. Both frequencies complete whole numbers of cycles in
;; N samples, and that complete-cycle symmetry is what guarantees orthogonality.
;;
;; **This is why the DFT works:**
;; - You have N samples → N independent values to specify
;; - You need N independent "building blocks" (like needing 3 colors - red, green, blue - to make any color)
;; - These N rotation frequencies are your building blocks
;; - They're independent (orthogonal) so measuring one doesn't affect the others
;; - The inner product cleanly separates them: "How much of frequency k is present?"
;;
;; This orthogonality guarantees that:
;; 1. Any N-sample signal can be built from these N frequencies (you have enough building blocks)
;; 2. The decomposition is unique (each frequency component is independent)
;; 3. Reconstruction works perfectly (no information lost)

;; ## The DFT Formula: Measuring Alignment with Rotations

;; Now we can understand what the DFT does. The formula is:
;;
;; **$\text{DFT}[k] = \sum_{n=0}^{N-1} x[n] \cdot e^{-i2\pi kn/N}$**
;;
;; Notice the **negative** sign in the exponent. This is the key to the "treadmill test."
;;
;; **The Treadmill Analogy:** Imagine you want to know if someone is running at exactly 5 mph.
;; You put them on a treadmill running **backwards** at 5 mph:
;; - If they're running at exactly 5 mph: they stay in one place (perfect match!)
;; - If they're running at 4 mph: they drift backwards
;; - If they're running at 6 mph: they drift forwards
;;
;; **The DFT does the same thing:**
;; - Your signal might be "rotating" at various speeds
;; - To measure frequency k, you rotate **backwards** (clockwise) at speed k using $e^{-i2\pi kn/N}$
;; - If the signal contains that frequency, the counter-rotation makes it "stand still" (becomes constant)
;; - A constant pattern has large inner product (all values add constructively)
;; - The magnitude tells you how strong that frequency was
;;
;; **The negative sign = "de-rotation"**: We're spinning the coordinate system backwards to see
;; which frequency appears stationary. This transforms the detection problem into something simple:
;; measuring a constant (non-rotating) component.
;;
;; This is an **inner product** between your signal $x[n]$ and rotation $e^{-i2\pi kn/N}$ at frequency $k$.
;;
;; **What it's measuring**: "How much does my signal align with rotation at frequency k?"
;;
;; - Large magnitude → signal strongly correlates with this rotation
;; - Small magnitude → signal doesn't oscillate at this frequency
;;
;; The DFT computes this inner product for every possible rotation frequency.
;;
;; **Implementation note:** Computing this formula directly requires $O(N^2)$ operations
;; (N frequencies × N samples). The **Fast Fourier Transform (FFT)** uses algebraic
;; factorization to reduce this to $O(N \log N)$—a 100× speedup for 1024 samples.
;; The FFT doesn't change *what* is computed, only *how fast*. See the companion post
;; [FFT Library Comparison](fft_comparison.html) for Clojure/JVM implementations.

;; Let's manually compute DFT[1] to see this:

(defn manual-dft-component [signal k]
  (let [N (count signal)]
    (reduce (fn [sum n]
              (let [angle (* -1.0 2.0 Math/PI k n (/ 1.0 N))
                    rotation-real (Math/cos angle)
                    rotation-imag (Math/sin angle)
                    sample (nth signal n)]
                {:real (+ (:real sum) (* sample rotation-real))
                 :imag (+ (:imag sum) (* sample rotation-imag))}))
            {:real 0.0 :imag 0.0}
            (range N))))

(def manual-k1 (manual-dft-component temperatures 1))

(kind/table
 [{:component "Real part"
   :value (format "%.4f" (:real manual-k1))}
  {:component "Imaginary part"
   :value (format "%.4f" (:imag manual-k1))}
  {:component "Magnitude"
   :value (format "%.4f"
                  (Math/sqrt (+ (* (:real manual-k1) (:real manual-k1))
                                (* (:imag manual-k1) (:imag manual-k1)))))}
  {:component "Library result (for comparison)"
   :value (format "%.4f" (:magnitude (nth temp-analysis 1)))}])

;; The formula is doing exactly what we said: measuring how well the signal matches each rotation.
;; ## Windowing and Edge Effects: The Implicit Assumption

;; Remember our key assumption: **the signal is periodic**. We treat our 8 temperatures
;; as if hour 8 = hour 0, hour 9 = hour 1, etc.
;;
;; But what if the signal doesn't actually repeat smoothly? What if there's a sharp
;; discontinuity where the end meets the beginning?
;;
;; This creates **spectral leakage**: energy from one frequency "leaks" into neighboring
;; frequencies, creating spurious peaks in the spectrum.

;; ### Demonstration: A Pure Sine Wave

;; Let's create a pure sine wave at exactly 10 Hz (using our 1-second, 100 Hz sample rate from earlier):

;; Case 1: Frequency that fits perfectly (10 Hz - exactly 10 cycles in 1 second)
(def sine-perfect (dfn/sin (dfn/* 2.0 Math/PI 10.0 sample-times)))

;; Case 2: Frequency that doesn't fit (10.5 Hz - creates discontinuity)
(def sine-leaky (dfn/sin (dfn/* 2.0 Math/PI 10.5 sample-times)))

;; Visualize the signals at the boundaries

(-> (tc/concat
     (tc/dataset {:time (vec sample-times) :value (vec sine-perfect) :signal "10 Hz (perfect fit)"})
     (tc/dataset {:time (vec sample-times) :value (vec sine-leaky) :signal "10.5 Hz (discontinuous)"}))
    (plotly/base {:=x :time
                  :=y :value
                  :=color :signal
                  :=x-title "Time (seconds)"
                  :=y-title "Amplitude"
                  :=title "Pure Sine Waves: Perfect vs Leaky"
                  :=width 700
                  :=height 300})
    (plotly/layer-line))

;; **Notice**: The 10 Hz signal ends exactly where it began (smooth loop). The 10.5 Hz
;; signal has a discontinuity—it ends mid-cycle.
;;
;; When we compute the DFT, we're implicitly wrapping this signal. The 10.5 Hz case
;; creates a sharp jump, which requires **many frequencies** to represent.

;; Compare their spectra

(def spectrum-perfect (t/forward-1d dft-transformer sine-perfect))
(def spectrum-leaky (t/forward-1d dft-transformer sine-leaky))

(def mags-perfect (extract-magnitudes spectrum-perfect 30))
(def mags-leaky (extract-magnitudes spectrum-leaky 30))

(-> (tc/concat
     (tc/dataset (map #(assoc % :signal "10 Hz (perfect fit)") mags-perfect))
     (tc/dataset (map #(assoc % :signal "10.5 Hz (leaky)") mags-leaky)))
    (plotly/base {:=x :frequency
                  :=y :magnitude
                  :=color :signal
                  :=x-title "Frequency (Hz)"
                  :=y-title "Magnitude"
                  :=title "Spectral Leakage: Perfect Fit vs Discontinuity"
                  :=width 700
                  :=height 350})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 6}))

;; **Interpretation:**
;; - **Perfect fit (10 Hz)**: Single sharp peak at k=10 (exactly the frequency we put in)
;; - **Leaky (10.5 Hz)**: Energy spreads across many frequencies—the discontinuity creates
;;   harmonics that "pollute" the spectrum
;;
;; This is **spectral leakage**: the implicit rectangular window (abrupt cutoff at boundaries)
;; creates artifacts in the frequency domain.

;; ### The Solution: Window Functions

;; To reduce leakage, we can multiply the signal by a **window function** that smoothly
;; tapers to zero at the edges, eliminating the discontinuity.
;;
;; Common windows include [Hann](https://en.wikipedia.org/wiki/Window_function#Hann_and_Hamming_windows), 
;; [Hamming](https://en.wikipedia.org/wiki/Window_function#Hann_and_Hamming_windows), and 
;; [Blackman](https://en.wikipedia.org/wiki/Window_function#Blackman_window). The tradeoff: 
;; reduced leakage but wider main lobe (slightly blurred frequency resolution).

;; Apply Hann window to the leaky signal
(def hann-window
  (let [indices (range n-samples)
        normalized (dfn// indices (double (dec n-samples)))]
    (dfn/* 0.5
           (dfn/- 1.0
                  (dfn/cos (dfn/* 2.0 Math/PI normalized))))))

(def sine-windowed (dfn/* sine-leaky hann-window))

;; Visualize what windowing does to the signal:
(-> (tc/concat
     (tc/dataset {:time sample-times
                  :value sine-leaky
                  :signal "Original 10.5 Hz sine"})
     (tc/dataset {:time sample-times
                  :value hann-window
                  :signal "Hann window"})
     (tc/dataset {:time sample-times
                  :value sine-windowed
                  :signal "Windowed result"}))
    (plotly/base {:=x :time
                  :=y :value
                  :=color :signal
                  :=x-title "Time (s)"
                  :=y-title "Amplitude"
                  :=title "How Windowing Tapers the Signal"
                  :=width 700
                  :=height 350})
    (plotly/layer-line))

;; Notice how the window smoothly tapers the sine wave to zero at both edges,
;; eliminating the discontinuity that causes leakage.

;; Now let's see the effect in the frequency domain:
(def spectrum-windowed (t/forward-1d dft-transformer sine-windowed))
(def mags-windowed (extract-magnitudes spectrum-windowed 30))

(-> (tc/concat
     (tc/dataset (map #(assoc % :signal "No window (leaky)") mags-leaky))
     (tc/dataset (map #(assoc % :signal "Hann window") mags-windowed)))
    (plotly/base {:=x :frequency
                  :=y :magnitude
                  :=color :signal
                  :=x-title "Frequency (Hz)"
                  :=y-title "Magnitude"
                  :=title "Windowing Reduces Leakage"
                  :=width 700
                  :=height 350})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 6}))

;; **Result**: The Hann window concentrates energy near the true frequency (10.5 Hz),
;; reducing the "grass" of spurious peaks. The main lobe is wider (blurrier), but the
;; sidelobes (leakage) are dramatically suppressed.
;;
;; **Practical takeaway**: When analyzing real signals that don't naturally loop,
;; apply a window function before the DFT to reduce spectral artifacts.

;; ## Two Views of the Same Decomposition

;; We've seen the DFT returns complex numbers (magnitude + phase). But we can also view this
;; as sines and cosines. Let's carefully trace how the two representations connect.
;;
;; **Start with the DFT formula:**
;;
;; **$\text{DFT}[k] = \sum_{n=0}^{N-1} x[n] \cdot e^{-i2\pi kn/N}$**
;;
;; **Step 1: Expand using Euler's formula.** Remember $e^{-i\theta} = \cos(\theta) - i \cdot \sin(\theta)$.
;; Notice the **negative** exponent puts a minus sign in front of sine:
;;
;; **$e^{-i2\pi kn/N} = \cos\left(\frac{2\pi kn}{N}\right) - i \cdot \sin\left(\frac{2\pi kn}{N}\right)$**
;;
;; **Step 2: Substitute into the DFT formula:**
;;
;; **$\text{DFT}[k] = \sum_{n=0}^{N-1} x[n] \cdot \left[\cos\left(\frac{2\pi kn}{N}\right) - i \cdot \sin\left(\frac{2\pi kn}{N}\right)\right]$**
;;
;; **Step 3: Separate real and imaginary parts:**
;;
;; **$\text{DFT}[k] = \sum_{n=0}^{N-1} x[n] \cos\left(\frac{2\pi kn}{N}\right) - i \cdot \sum_{n=0}^{N-1} x[n] \sin\left(\frac{2\pi kn}{N}\right)$**
;;
;; **Step 4: Define the cosine and sine coefficients:**
;;
;; Let $a_k = \sum_{n=0}^{N-1} x[n] \cos\left(\frac{2\pi kn}{N}\right)$ (the cosine coefficient)
;;
;; Let $b_k = \sum_{n=0}^{N-1} x[n] \sin\left(\frac{2\pi kn}{N}\right)$ (the sine coefficient)
;;
;; **Step 5: Write the DFT in terms of these coefficients:**
;;
;; **$\text{DFT}[k] = a_k - i \cdot b_k$**
;;
;; **The minus sign comes from the negative exponent in the DFT formula!**
;;
;; For reconstruction (going back to time domain), we use the **inverse** relationship:
;;
;; **$x[n] = \sum_{k=0}^{N-1} a_k \cos\left(\frac{2\pi kn}{N}\right) + b_k \sin\left(\frac{2\pi kn}{N}\right)$**
;;
;; Here the cosine and sine terms **add** (no minus sign) because we're building the signal
;; from its components, not decomposing it.

;; Let's extract the sine and cosine coefficients from our temperature spectrum:

(defn extract-sine-cosine-form [dft-result]
  (let [n (/ (count dft-result) 2)]
    (mapv (fn [k]
            (let [real (nth dft-result (* 2 k))
                  imag (nth dft-result (inc (* 2 k)))]
              {:frequency k
               :cosine-coeff real ; aₖ = real part
               :sine-coeff (- imag)})) ; bₖ = -imaginary part
          (range n))))

(def sine-cosine-decomp (extract-sine-cosine-form temp-spectrum))

;; Let's visualize what this means for the dominant frequency (k=1, the daily cycle):

(def k1-data (first (filter #(= 1 (:frequency %)) sine-cosine-decomp)))
(def a1 (:cosine-coeff k1-data))
(def b1 (:sine-coeff k1-data))

(def vis-time (dfn// (range 400) 50.0))
(def k1-cosine-component (dfn/* a1 (dfn/cos (dfn/* 2.0 Math/PI 1.0 (dfn// vis-time 8.0)))))
(def k1-sine-component (dfn/* b1 (dfn/sin (dfn/* 2.0 Math/PI 1.0 (dfn// vis-time 8.0)))))
(def k1-combined (dfn/+ k1-cosine-component k1-sine-component))

(-> (tc/concat
     (tc/dataset {:time vis-time :value k1-cosine-component :component (str "Cosine part: " (format "%.1f" a1) "·cos(2πt/8)")})
     (tc/dataset {:time vis-time :value k1-sine-component :component (str "Sine part: " (format "%.1f" b1) "·sin(2πt/8)")})
     (tc/dataset {:time vis-time :value k1-combined :component "Combined (cosine + sine)"}))
    (plotly/base {:=x :time
                  :=y :value
                  :=color :component
                  :=x-title "Time (hours)"
                  :=y-title "Value"
                  :=title "Frequency k=1: Cosine + Sine = Complete Rotation"
                  :=width 700
                  :=height 350})
    (plotly/layer-line))

;; **What you're seeing**: The cosine and sine components (horizontal and vertical
;; shadows of rotation) add together to create a wave that starts at a specific angle.
;; Neither alone could produce this pattern—you need both!
;;
;; The relationship between representations:
;; - **Strength (magnitude)**: $\sqrt{a_k^2 + b_k^2}$ — how strong is this frequency?
;; - **Starting angle (phase)**: where the combined wave begins its cycle
;; - **Complex form**: $c_k = a_k - i \cdot b_k$ — packages both into one rotation
;; - **Sine/Cosine form**: $a_k \cos + b_k \sin$ — separates the two shadows

;; Visualize the geometric relationship for k=1:

(def k1-magnitude (Math/sqrt (+ (* a1 a1) (* b1 b1))))
(def k1-phase-rad (Math/atan2 (- b1) a1))

(-> (tc/concat
     ;; Right triangle edges
     (tc/dataset {:x [0.0 a1] :y [0.0 0.0] :line "Cosine (a₁)"})
     (tc/dataset {:x [a1 a1] :y [0.0 (- b1)] :line "Sine (b₁)"})
     (tc/dataset {:x [0.0 a1] :y [0.0 (- b1)] :line "Magnitude"})
     ;; Points
     (tc/dataset {:x 0.0 :y 0.0 :point "Origin"}
                 {:x a1 :y (- b1) :point (format "DFT[1] = (%.1f, %.1f)" a1 (- b1))}))
    (plotly/base {:=x-title "Cosine Coefficient (a₁)"
                  :=y-title "−Sine Coefficient (−b₁)"
                  :=title (format "Geometric View: Magnitude = %.2f, Phase = %.1f°"
                                  k1-magnitude
                                  (Math/toDegrees k1-phase-rad))
                  :=width 500
                  :=height 500})
    (plotly/layer-line {:=x :x :=y :y :=group :line :=color :line})
    (plotly/layer-point {:=x :x :=y :y :=mark-size 10 :=mark-color "black"})
    plotly/plot
    (assoc-in [:layout :yaxis :scaleanchor] "x"))

;; **Geometric interpretation:**
;; - Horizontal leg = $a_k$ (cosine coefficient)
;; - Vertical leg = $-b_k$ (negative sine coefficient, due to DFT formula)
;; - Hypotenuse = magnitude $\sqrt{a_k^2 + b_k^2}$
;; - Angle from horizontal = phase
;;
;; This is why magnitude and phase are just another way to describe the same point!
;; Cartesian coordinates (a, b) vs polar coordinates (magnitude, phase).

;; Here are all the frequencies with their cosine and sine contributions:

(kind/table
 (map (fn [{:keys [frequency cosine-coeff sine-coeff]}]
        {:frequency frequency
         :cosine-coeff cosine-coeff
         :sine-coeff sine-coeff
         :interpretation
         (cond
           (zero? frequency) (format "DC offset: %.2f°C baseline temperature" cosine-coeff)
           (= 1 frequency) (format "Daily cycle: %.2f°C cosine amplitude, %.2f°C sine amplitude" cosine-coeff sine-coeff)
           :else (format "Frequency %d: %.2f°C cosine, %.2f°C sine" frequency cosine-coeff sine-coeff))})
      sine-cosine-decomp))

;; **What the numbers mean**:
;; - **Cosine coefficient** ($a_k$): How much the pattern starts at its maximum (like $\cos$ at t=0)
;; - **Sine coefficient** ($b_k$): How much the pattern starts rising from zero (like $\sin$ at t=0)
;; - For example, k=1 (daily cycle): $3.18 \cos + (-1.59) \sin$ means a wave that starts near its peak and slightly descending
;; - The **magnitude** $\sqrt{a_k^2 + b_k^2}$ tells you the total strength regardless of starting phase

;; ## Real Signals: A Special Mirror Pattern

;; Our temperatures are **real numbers** (not complex). This places a constraint on
;; the DFT coefficients: **[Hermitian symmetry](https://en.wikipedia.org/wiki/Hermitian_function)** (a special mirror pattern).
;;
;; **$\text{DFT}[k] = \overline{\text{DFT}[N-k]}$**
;;
;; (The bar notation $\overline{z}$ means the conjugate: flip the sign of the imaginary part.)

;; ### Why This Must Be True

;; Remember "Why We Need Both Shadows"? Knowing only the horizontal position (0.5) doesn't tell us
;; if the point is upper right (0.5, +0.866) or lower right (0.5, -0.866).
;;
;; For real signals, we only observe the **horizontal projection** (cosine). But rotations happen in
;; **both directions**:
;;
;; - **Frequency $k$**: rotating counterclockwise $k$ times per period
;; - **Frequency $N-k$**: rotating counterclockwise $N-k$ times = **clockwise** $k$ times
;;
;; (Since $N$ full rotations brings you back to start, $N-k$ counterclockwise = $k$ clockwise.)
;;
;; **Key observation**: Clockwise and counterclockwise rotations at the same speed produce:
;; - **Same horizontal shadow**: $\cos(2\pi k n/N) = \cos(-2\pi k n/N)$ (cosine is even)
;; - **Opposite vertical shadows**: $\sin(2\pi k n/N) = -\sin(-2\pi k n/N)$ (sine is odd)
;;
;; Therefore, for a real signal (which only "sees" the horizontal projection):
;; - Real part of $\text{DFT}[k]$ = Real part of $\text{DFT}[N-k]$ (same cosine)
;; - Imaginary part of $\text{DFT}[k]$ = **negative** of imaginary part of $\text{DFT}[N-k]$ (opposite sines)
;;
;; **This is exactly the conjugate relationship**: $\text{DFT}[k] = \overline{\text{DFT}[N-k]}$
;;
;; Geometrically: clockwise vs counterclockwise at frequency $k$ are mirror images across the horizontal axis.

;; ### Practical Consequences

;; This symmetry means:
;; - We only need to store $N/2+1$ complex coefficients (not all $N$)
;; - Each complex coefficient has 2 real values (real + imaginary parts), except k=0 and k=N/2 which are real-only
;; - Total: $N$ independent real values (same as the N-sample input)
;; - Half the frequencies are redundant (mirror images of the other half)

;; For our $N=8$ temperatures:
;; - $\text{DFT}[0]$ (DC): real only
;; - $\text{DFT}[1]$ and $\text{DFT}[7]$: mirror images
;; - $\text{DFT}[2]$ and $\text{DFT}[6]$: mirror images
;; - $\text{DFT}[3]$ and $\text{DFT}[5]$: mirror images
;; - $\text{DFT}[4]$ (Nyquist - highest frequency): real only

;; The library already exploits this, returning only k=0,1,2,3 (the redundancy is removed).

;; ## Symmetry Simplifications: DCT and DST

;; Now we can appreciate what DCT and DST achieve. For a general signal, you need
;; **both** sines and cosines (both $a_k$ and $b_k$). But if your data has **symmetry**,
;; one of them becomes zero!

;; ### Even Symmetry → DCT

;; If your signal mirrors at the boundaries (even symmetric), you only need **cosines**:

(def even-signal [5.0 4.0 3.0 2.0 2.0 3.0 4.0 5.0])

;; The signal mirrors around its center. Using 0-based indexing: $x[n] = x[N-1-n]$
;; Check: $x[0]=5 = x[7]=5$ ✓, $x[1]=4 = x[6]=4$ ✓, $x[2]=3 = x[5]=3$ ✓, etc.

(def dct-transformer (t/transformer :real :dct))
(def even-dct (t/forward-1d dct-transformer even-signal))

;; The DCT returns N real coefficients (no imaginary parts needed)
;; because even functions = cosines only

(-> (tc/dataset {:frequency (range (count even-dct))
                 :coefficient (vec even-dct)})
    (plotly/base {:=x :frequency
                  :=y :coefficient
                  :=x-title "Frequency"
                  :=y-title "DCT Coefficient"
                  :=title "DCT: Cosine-Only Decomposition"
                  :=width 700
                  :=height 300})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 10}))

;; **Why DCT for compression (JPEG, MP3)?** Even extension at boundaries is smooth,
;; so most energy concentrates in low frequencies. High frequencies can be discarded.

;; ### Odd Symmetry → DST

;; If your signal has odd symmetry ($x[n] = -x[N-n]$) and zero boundaries, you only
;; need **sines**:

(def odd-signal [0.0 4.0 3.0 2.0 0.0 -2.0 -3.0 -4.0])

(def dst-transformer (t/transformer :real :dst))
(def odd-dst (t/forward-1d dst-transformer odd-signal))

(-> (tc/dataset {:frequency (range (count odd-dst))
                 :coefficient (vec odd-dst)})
    (plotly/base {:=x :frequency
                  :=y :coefficient
                  :=x-title "Frequency"
                  :=y-title "DST Coefficient"
                  :=title "DST: Sine-Only Decomposition"
                  :=width 700
                  :=height 300})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 10}))

;; **Why DST for [PDEs](https://en.wikipedia.org/wiki/Partial_differential_equation)?** 
;; Differential equations (equations describing how things change in space and time) 
;; with zero boundary conditions (value = 0 at edges) naturally produce odd-symmetric solutions.

;; ## The Unified Picture

;; Every transform is answering: "Which rotations are in this signal?"
;;
;; But they make different assumptions:

(kind/table
 [{:transform "DFT"
   :input-symmetry "None (periodic)"
   :basis "$e^{i\\omega t}$ (cos + i·sin)"
   :output "Complex"
   :use-case "General analysis, convolution"
   :example "Analyzing recorded audio to find pitch, filtering signals, detecting frequencies in sensor data"}
  {:transform "DCT"
   :input-symmetry "Even (mirrors)"
   :basis "$\\cos(\\omega t)$"
   :output "Real"
   :use-case "Compression (JPEG, MP3)"
   :example "Compressing photos (JPEG images), audio files (MP3, AAC), video codecs"}
  {:transform "DST"
   :input-symmetry "Odd (negates)"
   :basis "$\\sin(\\omega t)$"
   :output "Real"
   :use-case "PDEs with zero boundaries"
   :example "Simulating vibrating guitar strings, heat diffusion with fixed endpoints, quantum mechanics"}])

;; **The foundation**: All transforms decompose periodic patterns into rotations.
;; The differences are in which rotations you allow (based on symmetry constraints).

;; ## Practical Implications

;; Armed with this understanding, the practical rules make sense:

;; **When to use DFT:**
;; - General signals without special symmetry
;; - Frequency analysis (find dominant frequencies)
;; - [Convolution](https://en.wikipedia.org/wiki/Convolution) (filtering, matching patterns)
;; - When starting angle (phase) information matters

;; **When to use DCT:**
;; - Compression (smooth boundaries → concentrated spectrum)
;; - Image/audio codecs (JPEG, MP3)
;; - When you know data has even-like symmetry

;; **When to use DST:**
;; - Solving differential equations with zero boundary conditions
;; - [Vibration analysis](https://en.wikipedia.org/wiki/Vibration) (strings or beams with fixed endpoints)
;; - When physical constraints enforce odd symmetry

;; ## The Core Insight

;; **Fourier transforms are about rotations—waves are the shadows.**
;;
;; Sine and cosine are what you see when you project circular motion onto axes.
;; The circle is more fundamental.
;;
;; When you have N numbers:
;; - They define a periodic pattern (wraps around)
;; - Any periodic pattern = sum of rotations at specific speeds
;; - Finding those rotations = Fourier transform
;; - Complex numbers = natural coordinates for rotation
;; - Symmetry = shortcuts (fewer rotations needed)
;;
;; This is why discrete Fourier analysis works. Not because of wave physics or
;; differential equations, but because of a simple geometric fact: **circles repeat**.

;; ## Reconstruction: The Inverse Transform

;; One final demonstration: we can perfectly reconstruct the original signal from
;; the frequency components.

(def reconstructed (t/reverse-1d dft-transformer temp-spectrum))

(def comparison-data
  (tc/concat
   (tc/dataset {:hour (range 8) :temperature temperatures :signal "Original"})
   (tc/dataset {:hour (range 8) :temperature (vec reconstructed) :signal "Reconstructed"})))

(-> comparison-data
    (plotly/base {:=x :hour
                  :=y :temperature
                  :=color :signal
                  :=x-title "Hour"
                  :=y-title "Temperature (°C)"
                  :=title "Perfect Reconstruction from Frequency Domain"
                  :=width 700
                  :=height 300})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 10}))

;; The reconstruction is perfect (within numerical precision). We've taken eight numbers,
;; decomposed them into rotations, and reassembled them—no information lost.
;;
;; In fact, not only is the signal preserved, but **energy is preserved** too.
;; The "energy" (sum of squared values) in the time domain equals the energy in the
;; frequency domain (up to a scaling factor). This is called **Parseval's theorem**:
;; another consequence of the orthogonality of the DFT basis functions.
;;
;; This is the power of the Fourier perspective: **different representation, same information,
;; new insights**.
