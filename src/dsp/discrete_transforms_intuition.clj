^{:kindly/hide-code true
  :clay {:title "Discrete Transforms: DFT, DCT, DST, and the Symmetry Story"
         :quarto {:author :daslu
                  :draft true
                  :type :post
                  :date "2025-12-15"
                  :category :data
                  :tags [:dsp :fourier :dft :dct :dst :discrete :transforms]}}}
(ns dsp.discrete-transforms-intuition
  "Understanding DFT, DCT, DST, and DHT through symmetry and boundary conditions."
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

;; # Discrete Transforms: DFT, DCT, DST, and the Symmetry Story

;; In a [previous post](fourier_circular_intuition.html), we explored why the Fourier
;; transform uses complex numbers: because waves are fundamentally circular motions,
;; and you need both horizontal and vertical projections (real and imaginary parts)
;; to capture the complete rotation.
;;
;; That story was about **continuous** signals—smooth functions varying over infinite time.
;; But in practice, we work with **discrete**, **finite** sequences: digital audio samples,
;; pixel intensities, sensor readings.
;;
;; This changes everything. When you have a **finite sequence**, you face new questions:
;; - What happens at the boundaries (the edges of your data)?
;; - How do you extend this finite sequence to make it periodic?
;; - Can you use simpler representations than complex numbers?
;;
;; The answers lead to a family of transforms: **DFT**, **DCT**, **DST**, and **DHT**.
;; Each makes different assumptions about symmetry and boundaries. Let's build the
;; intuition for when and why to use each.

;; ## From Continuous to Discrete: The Fundamental Shift

;; Start with a simple continuous signal: a smooth cosine wave.

(def continuous-time (dfn// (range 400) 100.0))
(def continuous-signal (dfn/cos (dfn/* 2.0 Math/PI 2.0 continuous-time)))

(-> (tc/dataset {:time continuous-time :amplitude continuous-signal})
    (plotly/base {:=x :time
                  :=y :amplitude
                  :=x-title "Time (seconds)"
                  :=y-title "Amplitude"
                  :=title "Continuous Signal: cos(2π·2t)"
                  :=width 700
                  :=height 250})
    (plotly/layer-line {:=mark-color "steelblue"}))

;; Now **sample** this signal at discrete points—like a microphone sampling audio
;; at 44.1 kHz.

(def n-samples 16)
(def sample-indices (range n-samples))
(def discrete-time (dfn// sample-indices 8.0))
(def discrete-signal (dfn/cos (dfn/* 2.0 Math/PI 2.0 discrete-time)))

(-> (tc/dataset {:time discrete-time :amplitude discrete-signal})
    (plotly/base {:=x :time
                  :=y :amplitude
                  :=x-title "Time (seconds)"
                  :=y-title "Amplitude"
                  :=title "Discrete Signal: 16 Samples"
                  :=width 700
                  :=height 250})
    (plotly/layer-point {:=mark-size 10})
    (plotly/layer-line {:=mark-color "steelblue" :=mark-opacity 0.3}))

;; **What changed**: We went from infinite smooth curve to **16 discrete values**.
;; These 16 numbers are all we have. No information about what happens between samples,
;; before the first sample, or after the last sample.

;; ## The Boundary Problem

;; To decompose this finite sequence into frequencies (like Fourier transform does),
;; we need to treat it as **one period of an infinite periodic signal**. But how do
;; we extend it beyond the boundaries?
;;
;; **Here's the key choice**: Different extensions create different symmetries, which
;; allow different representations.

;; ### Option 1: Periodic Extension (DFT)

;; Simply **repeat** the sequence: [a, b, c, d] → [a, b, c, d, a, b, c, d, ...]
;;
;; This is what the **DFT (Discrete Fourier Transform)** assumes.

;; Show periodic extension
(def original-signal [1.0 0.7 0.0 -0.7 -1.0 -0.7 0.0 0.7])
(def periodic-extension (vec (concat original-signal original-signal original-signal)))

(-> (tc/dataset {:index (range (count periodic-extension))
                 :amplitude periodic-extension
                 :type (vec (concat
                             (repeat 8 "Original")
                             (repeat 8 "Period 2")
                             (repeat 8 "Period 3")))})
    (plotly/base {:=x :index
                  :=y :amplitude
                  :=color :type
                  :=x-title "Sample Index"
                  :=y-title "Amplitude"
                  :=title "Periodic Extension (DFT Assumption)"
                  :=width 700
                  :=height 250})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 8}))

;; **Observation**: There's often a **discontinuity** at the boundary (last sample
;; doesn't match first sample). This creates high-frequency artifacts in the DFT
;; spectrum—spectral leakage.
;;
;; **Why both cos and sin**: Periodic extension has no special symmetry (unlike even/odd),
;; so you need **both** cosine and sine basis functions. You could write this with purely
;; real coefficients:
;;
;; x[n] = Σ aₖ·cos(2πkn/N) + Σ bₖ·sin(2πkn/N)
;;
;; But the DFT packages these together using **complex exponentials**:
;;
;; x[n] = Σ cₖ·e^(i2πkn/N)  where cₖ = aₖ - i·bₖ
;;
;; This isn't required—it's chosen for mathematical elegance and computational efficiency
;; (the FFT algorithm works naturally on complex exponentials, and the convolution theorem
;; is cleaner). The complex representation also gives us natural amplitude/phase form:
;; cₖ = |cₖ|·e^(iφₖ) where amplitude |cₖ| = √(aₖ² + bₖ²) and phase φₖ = arctan(-bₖ/aₖ).

;; ### Option 2: Even Symmetric Extension (DCT)

;; **Mirror** the sequence at the boundaries: [a, b, c, d] → [..., d, c, b, a, b, c, d, c, b, a, ...]
;;
;; This creates **even symmetry**: the extended signal is symmetric around each boundary.
;; This is what the **DCT (Discrete Cosine Transform)** assumes.

;; Show even symmetric extension
(def dct-signal [1.0 0.7 0.3 0.0 -0.3 -0.7 -1.0])
(def even-extension (vec (concat
                          (reverse dct-signal) ; mirror left
                          dct-signal
                          (reverse dct-signal)))) ; mirror right

(-> (tc/dataset {:index (range (count even-extension))
                 :amplitude even-extension
                 :type (vec (concat
                             (repeat 7 "Left mirror")
                             (repeat 7 "Original")
                             (repeat 7 "Right mirror")))})
    (plotly/base {:=x :index
                  :=y :amplitude
                  :=color :type
                  :=x-title "Sample Index"
                  :=y-title "Amplitude"
                  :=title "Even Symmetric Extension (DCT Assumption)"
                  :=width 700
                  :=height 250})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 8}))

;; **Key property**: Even functions can be represented using **cosines only** (no sines needed).
;; Why? Because cos(−x) = cos(x)—cosine itself is even symmetric.
;;
;; **Advantage**: No discontinuity at boundaries! The mirrored extension is smooth,
;; reducing spectral leakage. Also, all coefficients are **real numbers** (no complex arithmetic).
;;
;; **Use case**: Image compression (JPEG), audio compression, when boundary smoothness matters.

;; ### Option 3: Odd Symmetric Extension (DST)

;; **Mirror and negate**: [a, b, c, d] → [..., −d, −c, −b, 0, b, c, d, 0, −d, −c, −b, ...]
;;
;; This creates **odd symmetry**: f(−x) = −f(x). This is what the **DST (Discrete Sine Transform)** assumes.

;; Show odd symmetric extension
(def dst-signal [0.3 0.7 0.9 0.7 0.3])
(def odd-extension (vec (concat
                         (mapv - (reverse dst-signal)) ; mirror and negate left
                         dst-signal
                         (mapv - (reverse dst-signal))))) ; mirror and negate right

(-> (tc/dataset {:index (range (count odd-extension))
                 :amplitude odd-extension
                 :type (vec (concat
                             (repeat 5 "Left (negated)")
                             (repeat 5 "Original")
                             (repeat 5 "Right (negated)")))})
    (plotly/base {:=x :index
                  :=y :amplitude
                  :=color :type
                  :=x-title "Sample Index"
                  :=y-title "Amplitude"
                  :=title "Odd Symmetric Extension (DST Assumption)"
                  :=width 700
                  :=height 250})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 8}))

;; **Key property**: Odd functions can be represented using **sines only** (no cosines needed).
;; Why? Because sin(−x) = −sin(x)—sine itself is odd symmetric.
;;
;; **Note**: The signal must be **zero at the boundaries** for true odd symmetry.
;;
;; **Use case**: Solving differential equations with zero boundary conditions (heat equation,
;; wave equation), vibration analysis.

;; ## The Foundation: Real Signals and Hermitian Symmetry

;; Before comparing the transforms, we need one crucial fact: **when the input signal is real**
;; (which is almost always the case - audio samples, pixel values, sensor data), the DFT
;; coefficients have a special relationship called **Hermitian symmetry**:
;;
;; **DFT[k] = conjugate(DFT[N-k])**
;;
;; This means:
;; - Real parts are even: aₖ = a_{N-k}
;; - Imaginary parts are odd: bₖ = -b_{N-k}

;; Let's verify this with a real but asymmetric signal:

(def hermitian-demo [1.0 2.0 4.0 3.0 2.0 1.0 0.5 0.25])
(def hermitian-dft (t/forward-1d dft-transformer hermitian-demo))

;; The DFT only returns N/2+1 coefficients because of Hermitian symmetry
;; For N=8: returns k=0,1,2,3 (the library exploits the redundancy)
(def hermitian-coeffs
  (let [n (/ (count hermitian-dft) 2)]
    (mapv (fn [i]
            {:k i
             :real (nth hermitian-dft (* 2 i))
             :imag (nth hermitian-dft (inc (* 2 i)))})
          (range n))))

(kind/hiccup
 [:div
  [:p [:strong "Hermitian Symmetry in Action:"]]
  [:ul
   [:li (str "Input: 8 real samples")]
   [:li (str "DFT[0] (DC): " (format "%.2f" (:real (nth hermitian-coeffs 0)))
             " + " (format "%.2f" (:imag (nth hermitian-coeffs 0))) "i (purely real!)")]
   [:li (str "DFT[1]: " (format "%.2f" (:real (nth hermitian-coeffs 1)))
             " + " (format "%.2f" (:imag (nth hermitian-coeffs 1))) "i")]
   [:li (str "DFT[2]: " (format "%.2f" (:real (nth hermitian-coeffs 2)))
             " + " (format "%.2f" (:imag (nth hermitian-coeffs 2))) "i")]
   [:li (str "DFT[3]: " (format "%.2f" (:real (nth hermitian-coeffs 3)))
             " + " (format "%.2f" (:imag (nth hermitian-coeffs 3))) "i")]
   [:li "DFT[4] (Nyquist): Would be purely real (not shown)"]]
  [:p [:strong "Key insight:"] " For 8 real input samples, we only need to store "
   [:code "N/2+1 = 5"] " coefficients (not all 8), containing exactly 8 independent real values."]])

;; **Why this matters:**
;; - Input: N real numbers (N degrees of freedom)
;; - Naive DFT: N complex coefficients (2N real numbers - wasteful!)
;; - With Hermitian symmetry: Store N/2+1 coefficients = N real values (optimal!)
;;
;; **This is why DCT and DST exist:** They also store N real coefficients for N real samples.
;; They're just making different symmetry assumptions to achieve the same efficiency.

;; ## Connecting Back to Circular Motion

;; Remember the circular intuition: a rotating point has horizontal (cosine) and vertical
;; (sine) projections. For continuous signals, you need **both** to capture the complete rotation.
;;
;; But when you **impose symmetry** on a discrete finite sequence:
;; - **Even symmetry** → rotations that look the same forward and backward → cosines only (DCT)
;; - **Odd symmetry** → rotations that reverse when flipped → sines only (DST)
;; - **No symmetry** → arbitrary rotations → need both cos and sin (DFT, complex numbers)

;; Let's see this with actual transforms on a real signal.

;; ## Comparing Transforms on the Same Signal

;; Take a simple finite sequence and apply DFT, DCT, and DST.

(def test-signal [1.0 0.8 0.4 0.0 -0.4 -0.8 -1.0 -0.8])

;; Create transformers
(def dft-transformer (t/transformer :real :fft))
(def dct-transformer (t/transformer :real :dct))
(def dst-transformer (t/transformer :real :dst))

;; Apply transforms
(def dft-result (t/forward-1d dft-transformer test-signal))
(def dct-result (t/forward-1d dct-transformer test-signal))
(def dst-result (t/forward-1d dst-transformer test-signal))

;; DFT returns interleaved real/imaginary pairs - extract magnitudes
(defn dft-magnitudes [dft-result]
  (let [n (/ (count dft-result) 2)]
    (mapv (fn [i]
            (let [real (nth dft-result (* 2 i))
                  imag (nth dft-result (inc (* 2 i)))]
              (Math/sqrt (+ (* real real) (* imag imag)))))
          (range n))))

(def dft-mags (dft-magnitudes dft-result))

;; Visualize all three spectra
(-> (tc/concat
     (tc/dataset {:frequency (range (count dft-mags))
                  :magnitude dft-mags
                  :transform "DFT (complex)"})
     (tc/dataset {:frequency (range (count dct-result))
                  :magnitude (mapv #(Math/abs %) dct-result)
                  :transform "DCT (cosine-only)"})
     (tc/dataset {:frequency (range (count dst-result))
                  :magnitude (mapv #(Math/abs %) dst-result)
                  :transform "DST (sine-only)"}))
    (plotly/base {:=x :frequency
                  :=y :magnitude
                  :=color :transform
                  :=x-title "Frequency Bin"
                  :=y-title "Magnitude"
                  :=title "Transform Spectra Comparison"
                  :=width 700
                  :=height 350})
    (plotly/layer-line)
    (plotly/layer-point {:=mark-size 6}))

;; **Observation**: Each transform reveals different frequency structure because they
;; assume different boundary extensions. The DFT sees discontinuity (high frequencies),
;; DCT sees smooth even extension (concentrated low frequencies), DST sees odd extension.

;; ## The DHT: A Real Alternative to DFT

;; There's one more transform: **DHT (Discrete Hartley Transform)**. It's like DFT but
;; uses a **real basis** instead of complex exponentials:
;;
;; DFT uses: e^(i2πkn/N) = cos(2πkn/N) + i·sin(2πkn/N)
;; DHT uses: cos(2πkn/N) + sin(2πkn/N) [no imaginary unit!]
;;
;; **Properties**:
;; - Produces **real outputs** (not complex)
;; - Same information as DFT, just packaged differently
;; - Useful when you want real coefficients but periodic extension
;;
;; **Trade-off**: Less intuitive (what does "cos + sin" mean geometrically?) but
;; computationally simpler in some applications.

;; ## When to Use Each Transform

;; Here's the practical decision tree:

(kind/hiccup
 [:div
  [:h3 "Transform Selection Guide"]
  [:table {:style {:border-collapse "collapse" :width "100%"}}
   [:thead
    [:tr
     [:th {:style {:border "1px solid #ddd" :padding "8px" :background-color "#f2f2f2"}} "Transform"]
     [:th {:style {:border "1px solid #ddd" :padding "8px" :background-color "#f2f2f2"}} "Symmetry"]
     [:th {:style {:border "1px solid #ddd" :padding "8px" :background-color "#f2f2f2"}} "Output"]
     [:th {:style {:border "1px solid #ddd" :padding "8px" :background-color "#f2f2f2"}} "Best For"]]]
   [:tbody
    [:tr
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "DFT"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Periodic"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Complex"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "General signals, frequency analysis, convolution"]]
    [:tr
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "DCT"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Even"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Real"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Compression (JPEG, MP3), smooth boundaries"]]
    [:tr
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "DST"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Odd"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Real"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Zero boundary conditions, PDEs, vibrations"]]
    [:tr
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "DHT"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Periodic"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Real"]
     [:td {:style {:border "1px solid #ddd" :padding "8px"}} "Alternative to DFT when real outputs preferred"]]]]])

;; **Key insight**: DFT is most general (handles any signal), but DCT/DST exploit
;; symmetry to:
;; - Reduce computation (real-only arithmetic)
;; - Reduce artifacts (smooth boundary extension)
;; - Match physical boundary conditions

;; ## The Circular Foundation Still Holds

;; Does the "circle and shadows" intuition help here?
;;
;; **Yes, for DFT**: DFT is literally the discrete version of the continuous Fourier
;; transform. Instead of continuous rotation, you have a point jumping around the circle
;; at discrete time steps. You still need both horizontal (real) and vertical (imaginary)
;; coordinates.
;;
;; **Partially, for DCT/DST**: The symmetry constraints mean you only see "half the circle"—
;; either the horizontal motion (even, cosine) or vertical motion (odd, sine). The full
;; rotation is still there conceptually, but the symmetry lets you reconstruct everything
;; from just one projection.
;;
;; **Less clearly, for DHT**: The "cos + sin" basis doesn't have a simple geometric
;; interpretation as shadows. It's algebraically equivalent to DFT but geometrically murkier.

;; ## Practical Example: Why DCT for Image Compression?

;; JPEG uses DCT, not DFT. Why?

;; Imagine an 8×8 block of pixels. If you use DFT (periodic extension), the right edge
;; wraps to the left edge. But in natural images, the right and left edges are usually
;; **very different** → discontinuity → high-frequency artifacts → poor compression.
;;
;; DCT (even extension) mirrors the block at edges → smooth continuation → most energy
;; in low frequencies → high frequencies can be discarded → excellent compression.

;; Let's simulate this with a 1D slice:

(def image-slice [100.0 110.0 130.0 160.0 180.0 170.0 140.0 90.0])

;; DFT sees: [... 90 | 100 110 130 160 180 170 140 90 | 100 ...]
;;                   ↑ discontinuity (90→100)

;; DCT sees: [... 140 170 180 160 130 110 100 | 100 110 130 160 180 170 140 | 140 170 ...]
;;                                             ↑ smooth (100→100)

(def dft-image-result (t/forward-1d dft-transformer image-slice))
(def dct-image-result (t/forward-1d dct-transformer image-slice))

(def dft-image-mag (dft-magnitudes dft-image-result))

(-> (tc/concat
     (tc/dataset {:frequency (range (count dft-image-mag))
                  :magnitude dft-image-mag
                  :transform "DFT (periodic, discontinuous)"})
     (tc/dataset {:frequency (range (count dct-image-result))
                  :magnitude (mapv #(Math/abs %) dct-image-result)
                  :transform "DCT (even, smooth)"}))
    (plotly/base {:=x :frequency
                  :=y :magnitude
                  :=color :transform
                  :=x-title "Frequency Bin"
                  :=y-title "Magnitude"
                  :=title "DFT vs DCT on Image Data"
                  :=width 700
                  :=height 300})
    (plotly/layer-line {:=mark-width 2})
    (plotly/layer-point {:=mark-size 8}))

;; **Notice**: DFT has significant high-frequency content (the discontinuity creates
;; sharp edges in frequency domain). DCT concentrates energy in low frequencies—much
;; easier to compress by dropping high-frequency coefficients.

;; ## Summary: The Symmetry Principle

;; **Continuous Fourier Transform**: Infinite signal, rotations at all frequencies,
;; complex numbers essential.
;;
;; **Discrete transforms** (finite signals): Must assume some **boundary extension**.
;; The extension type determines the symmetry, which determines the transform:
;;
;; - **Periodic extension** → no symmetry → DFT (complex, general-purpose)
;; - **Even symmetric** → mirror at edges → DCT (real, compression-friendly)
;; - **Odd symmetric** → mirror + negate → DST (real, zero-boundary PDEs)
;; - **Real periodic** → periodic but real basis → DHT (alternative to DFT)
;;
;; The circular intuition from continuous Fourier still guides us: complex numbers capture
;; complete rotation, but when symmetry constrains the motion, one projection suffices.
;;
;; **Practical advice**: Start with DFT for general analysis. Switch to DCT when boundaries
;; matter (compression, smooth signals). Use DST when physics demands zero boundaries.
;; DHT is niche—stick with DFT unless you have specific needs for real-only arithmetic.
