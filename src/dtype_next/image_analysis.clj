^{:kindly/hide-code true
  :clay {:title "Image Processing with dtype-next"
         :quarto {:author :daslu
                  :draft true
                  :type :post
                  :date "2025-12-07"
                  :category :data
                  :tags [:dtype-next :tensors :image-processing :computer-vision :tutorial]}}}
(ns dtype-next.image-analysis
  "Learn dtype-next by building practical image processing tools.
  
  We'll explore quality metrics, enhancement pipelines, accessibility features,
  and edge detection—all with functional idioms and zero-copy operations."
  (:require [scicloj.kindly.v4.kind :as kind]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [tech.v3.tensor :as tensor]
            [tech.v3.libs.buffered-image :as bufimg]
            [tech.v3.dataset.tensor :as ds-tensor]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]))

;; # Introduction: Why dtype-next for Image Processing?

;; Images are perfect for learning dtype-next because they're **typed numerical
;; arrays with clear visual feedback**. Unlike generic sequences where numbers
;; are boxed, dtype-next gives us:
;;
;; - **Efficient storage**: A 1000×1000 RGB image is 3MB of uint8 values, not 12MB+ of boxed objects
;; - **Zero-copy views**: Slice channels, regions, or transforms without copying data
;; - **Functional operations**: Element-wise transformations that compose naturally
;; - **Type discipline**: Explicit control over precision and overflow
;;

;; ## What We'll Build

;; - **Image Statistics** — channel means, ranges, distributions, histograms
;; - **Spatial Analysis** — gradients, edge detection, sharpness metrics  
;; - **Enhancement Pipeline** — white balance, contrast adjustment
;; - **Accessibility** — color blindness simulation
;; - **Convolution & Filtering** — blur, sharpen, Sobel edge detection
;; - **Reshape & Downsampling** — pyramids, multi-scale processing

;; Each section demonstrates core dtype-next concepts with immediate practical value.

;; ---

;; # Setup: Loading Images as Tensors

;; Let's load our sample image and understand the tensor structure.

(def original-img
  (bufimg/load "src/dtype_next/nap.jpg"))

original-img

(def original-tensor
  (bufimg/as-ubyte-tensor original-img))

original-tensor

;; ## Understanding Tensor Shape

;; **Shape** tells us dimensions:

(dtype/shape original-tensor)

;; This is `[height width channels]` — our image has 3 channels (RGB).

(def height
  (first (dtype/shape original-tensor)))

(def width
  (second (dtype/shape original-tensor)))

;; **Element type**:

(dtype/elemwise-datatype original-tensor)

;; `:uint8` means each pixel component is an unsigned byte (0-255).

;; **Total elements**:

(dtype/ecount original-tensor)

;; That's `height × width × 3`.

;; ---

;; # Image Statistics

;; Let's analyze image properties using **reduction operations**.

;; ## Extracting Color Channels

;; Use `tensor/select` to slice out individual channels (zero-copy views):

(defn extract-channels
  "Extract R, G, B channels from RGB tensor.
  Returns map with :red, :green, :blue tensors (each [H W])."
  [img-tensor]
  {:red (tensor/select img-tensor :all :all 0)
   :green (tensor/select img-tensor :all :all 1)
   :blue (tensor/select img-tensor :all :all 2)})

(def channels (extract-channels original-tensor))

;; Each channel is now `[H W]` instead of `[H W C]`:

(dtype/shape (:red channels))

;; **Key insight**: These are **views** into the original tensor—no copying.

;; ## Channel Statistics

;; Compute mean, standard deviation, min, max for each channel:

(defn channel-stats
  "Compute statistics for a single channel tensor."
  [channel]
  {:mean (dfn/mean channel)
   :std (dfn/standard-deviation channel)
   :min (dfn/reduce-min channel)
   :max (dfn/reduce-max channel)})

(->> channels
     (map (fn [[k v]]
            (merge {:channel k}
                   (channel-stats v))))
     tc/dataset)

;; ## Brightness Analysis

;; Convert to grayscale using perceptual luminance formula.
;;
;; **Why these specific weights?** Human vision is most sensitive to green light,
;; moderately sensitive to red, and least sensitive to blue. The coefficients
;; (0.299, 0.587, 0.114) approximate the [relative luminance](https://en.wikipedia.org/wiki/Relative_luminance)
;; formula from the [ITU-R BT.601](https://en.wikipedia.org/wiki/Rec._601) standard,
;; ensuring grayscale images preserve
;; perceived brightness rather than simple RGB averages.

(defn to-grayscale
  "Convert RGB to grayscale using perceptual weights.
  Standard formula: 0.299*R + 0.587*G + 0.114*B"
  [img-tensor]
  (let [r (tensor/select img-tensor :all :all 0)
        g (tensor/select img-tensor :all :all 1)
        b (tensor/select img-tensor :all :all 2)]
    (dfn/+ (dfn/* r 0.299)
           (dfn/* g 0.587)
           (dfn/* b 0.114))))

(def grayscale (to-grayscale original-tensor))

;; **Grayscale statistics**:

(tc/dataset (channel-stats grayscale))

;; Visualize grayscale:

(bufimg/tensor->image grayscale)

;; ## Reshaping

;; Sometimes it is convenient to have one flat buffer
;; per colour channel.

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    dtype/shape)

;; ## Tensors as datasets

;; Tensors with two dimensions can be turned into datasets:

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    ds-tensor/tensor->dataset
    (tc/rename-columns [:red :green :blue]))

;; Or simply: 

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    tc/dataset
    (tc/rename-columns [:red :green :blue]))

;; We can also go back the opposite direction:

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    tc/dataset
    ds-tensor/dataset->tensor
    (tensor/reshape [height width 3])
    bufimg/tensor->image)

;; ## Histograms

;; A [histogram](https://en.wikipedia.org/wiki/Image_histogram) shows the distribution
;; of pixel values. It's essential for understanding image brightness, contrast, and
;; exposure. Peaks indicate common values; spread indicates dynamic range.

;; To draw the histograms, we can use a pivot transformation:

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    tc/dataset
    (tc/rename-columns [:red :green :blue])
    (plotly/base {:=histogram-nbins 30
                  :=mark-opacity 0.5})
    (plotly/layer-histogram {:=x :red
                             :=mark-color "red"})
    (plotly/layer-histogram {:=x :blue
                             :=mark-color "blue"})
    (plotly/layer-histogram {:=x :green
                             :=mark-color "green"}))

(->> (assoc channels :gray grayscale)
     (map (fn [[k v]]
            (-> (tc/dataset {:x (dtype/as-reader v)})
                (plotly/base {:=title k
                              :=height 200
                              :=width 600})
                (plotly/layer-histogram {:=histogram-nbins 30
                                         :=mark-color k}))))
     kind/fragment)

;; ---

;; # Spatial Analysis — Edges and Gradients

;; Analyze spatial structure using [gradient](https://en.wikipedia.org/wiki/Image_gradient)
;; operations. Gradients are fundamental to [edge detection](https://en.wikipedia.org/wiki/Edge_detection),
;; which identifies boundaries between regions in an image.

;; ## Computing Gradients

;; Gradients measure how quickly pixel values change. We compute them by
;; comparing neighboring pixels using **slice offsets**.

(defn gradient-x
  "Compute horizontal gradient (difference between adjacent columns).
  Result shape: [H, W-1]"
  [tensor-2d]
  (let [[h w] (dtype/shape tensor-2d)]
    (dfn/- (tensor/select tensor-2d :all (range 1 w))
           (tensor/select tensor-2d :all (range 0 (dec w))))))

(defn gradient-y
  "Compute vertical gradient (difference between adjacent rows).
  Result shape: [H-1, W]"
  [tensor-2d]
  (let [[h w] (dtype/shape tensor-2d)]
    (dfn/- (tensor/select tensor-2d (range 1 h) :all)
           (tensor/select tensor-2d (range 0 (dec h)) :all))))

(def gx (gradient-x grayscale))
(def gy (gradient-y grayscale))

gx

gy

;; Notice: `gx` is one column narrower, `gy` is one row shorter.

;; Combine gradients to get edge strength: `sqrt(gx² + gy²)`

(defn edge-magnitude
  "Compute gradient magnitude from gx and gy.
  Trims to common size before combining."
  [gx gy]
  (let [[h-gx w-gx] (dtype/shape gx)
        [h-gy w-gy] (dtype/shape gy)
        ;; Trim to common dimensions
        gx-trimmed (tensor/select gx (range 0 h-gy) :all)
        gy-trimmed (tensor/select gy :all (range 0 w-gx))]
    (dfn/sqrt (dfn/+ (dfn/sq gx-trimmed)
                     (dfn/sq gy-trimmed)))))

(def edges (edge-magnitude gx gy))

edges

;; Visualize edges (normalize to 0-255 range):

(bufimg/tensor->image
 (-> edges
     (dfn/* (/ 255.0 (max 1.0 (dfn/reduce-max edges))))
     (dtype/elemwise-cast :uint8)))

;; ## Sharpness Metric

;; Measure image sharpness by averaging edge magnitude—higher = sharper:

(defn sharpness-score
  "Compute sharpness as mean edge magnitude.
  Higher values indicate sharper images."
  [img-tensor]
  (let [gray (to-grayscale img-tensor)
        gx (gradient-x gray)
        gy (gradient-y gray)
        edges (edge-magnitude gx gy)]
    (dfn/mean edges)))

(sharpness-score original-tensor)

;; **Use case**: Compare sharpness before/after blur, or rank photos by clarity.

;; ---

;; # Enhancement Pipeline

;; Build composable image enhancement functions. Each transformation is
;; verifiable through numeric properties we can check in the REPL.

;; ## Auto White Balance

;; [White balance](https://en.wikipedia.org/wiki/Color_balance) adjusts colors to
;; appear neutral under different lighting conditions. We scale RGB channels to have
;; equal means, removing color casts.

(defn auto-white-balance
  "Scale RGB channels to have equal means.
  This removes color casts and balances the image."
  [img-tensor]
  (let [;; Compute channel means using reduce-axis
        ;; Reduce over height (axis 0), then width (axis 0 again)
        channel-means (-> img-tensor
                          (tensor/reduce-axis dfn/mean 0)
                          (tensor/reduce-axis dfn/mean 0))

        ;; Target: maximum of the three means
        target-mean (dfn/reduce-max channel-means)

        ;; Compute scale factors for each channel [3]
        scale-factors (dfn// target-mean (dfn/max 1.0 channel-means))

        [h w c] (dtype/shape img-tensor)

        ;; Scale each channel (vectorized operations per channel)
        scaled-channels (mapv (fn [ch]
                                (let [channel (tensor/select img-tensor :all :all ch)
                                      scale (dtype/get-value scale-factors ch)]
                                  (dtype/elemwise-cast
                                   (dfn/min 255 (dfn/* channel scale))
                                   :uint8)))
                              (range c))]

    ;; Reassemble channels
    (tensor/compute-tensor
     [h w c]
     (fn [y x ch]
       (tensor/mget (nth scaled-channels ch) y x))
     :uint8)))

(kind/table
 [[:original
   :white-balanced]
  [original-img
   (-> original-tensor
       auto-white-balance
       bufimg/tensor->image)]])

;; ## Contrast Enhancement

;; [Contrast](https://en.wikipedia.org/wiki/Contrast_(vision)) enhancement amplifies
;; the difference between light and dark regions. We amplify each pixel's deviation
;; from the mean, making bright pixels brighter and dark pixels darker.

(defn enhance-contrast
  "Increase image contrast by amplifying deviation from mean.
  Factor > 1 increases contrast, < 1 decreases it."
  [img-tensor factor]
  (let [r (tensor/select img-tensor :all :all 0)
        g (tensor/select img-tensor :all :all 1)
        b (tensor/select img-tensor :all :all 2)

        ;; Compute mean for each channel
        r-mean (dfn/mean r)
        g-mean (dfn/mean g)
        b-mean (dfn/mean b)

        ;; Apply contrast: mean + factor * (value - mean)
        enhance-channel (fn [ch ch-mean]
                          (dtype/elemwise-cast
                           (dfn/min 255
                                    (dfn/max 0
                                             (dfn/+ ch-mean
                                                    (dfn/* (dfn/- ch ch-mean) factor))))
                           :uint8))

        r-enhanced (enhance-channel r r-mean)
        g-enhanced (enhance-channel g g-mean)
        b-enhanced (enhance-channel b b-mean)

        [h w _] (dtype/shape img-tensor)]
    (tensor/compute-tensor
     [h w 3]
     (fn [y x c]
       (case c
         0 (tensor/mget r-enhanced y x)
         1 (tensor/mget g-enhanced y x)
         2 (tensor/mget b-enhanced y x)))
     :uint8)))

(def contrasted (enhance-contrast original-tensor 1.5))

(kind/table
 [[:original
   :contrast-1.5
   :contrast-3]
  [original-img
   (-> original-tensor
       (enhance-contrast 1.5)
       bufimg/tensor->image)
   (-> original-tensor
       (enhance-contrast 3)
       bufimg/tensor->image)]])

;; ---

;; # Accessibility — Color Blindness Simulation

;; Use matrix transformations to simulate how images appear to people with
;; different types of color vision deficiency. This demonstrates dtype-next's
;; linear algebra capabilities with practical accessibility applications.

;; Apply 3×3 transformation matrices to simulate different types of color vision deficiency.

;; ## Color Blindness Matrices

;; These matrices simulate [color blindness](https://en.wikipedia.org/wiki/Color_blindness)
;; (color vision deficiency). Different types affect perception of red, green, or blue:

(def color-blindness-matrices
  {:protanopia [[0.567 0.433 0.000] ; Red-blind
                [0.558 0.442 0.000]
                [0.000 0.242 0.758]]

   :deuteranopia [[0.625 0.375 0.000] ; Green-blind
                  [0.700 0.300 0.000]
                  [0.000 0.300 0.700]]

   :tritanopia [[0.950 0.050 0.000] ; Blue-blind
                [0.000 0.433 0.567]
                [0.000 0.475 0.525]]})

;; ## Applying Matrix Transformations

;; Extract RGB channels, apply linear combinations, reassemble:

(defn apply-color-matrix
  "Apply 3×3 transformation matrix to RGB channels.
  Matrix format: [[r0 g0 b0] [r1 g1 b1] [r2 g2 b2]]
  new_r = r0*R + g0*G + b0*B, etc."
  [img-tensor matrix]
  (let [r (tensor/select img-tensor :all :all 0)
        g (tensor/select img-tensor :all :all 1)
        b (tensor/select img-tensor :all :all 2)

        [[r0 g0 b0]
         [r1 g1 b1]
         [r2 g2 b2]] matrix

        ;; Apply transformation
        new-r (dfn/+ (dfn/+ (dfn/* r r0) (dfn/* g g0)) (dfn/* b b0))
        new-g (dfn/+ (dfn/+ (dfn/* r r1) (dfn/* g g1)) (dfn/* b b1))
        new-b (dfn/+ (dfn/+ (dfn/* r r2) (dfn/* g g2)) (dfn/* b b2))

        ;; Clamp and cast
        clamp-cast (fn [ch]
                     (dtype/elemwise-cast
                      (dfn/min 255 (dfn/max 0 ch))
                      :uint8))

        new-r-clamped (clamp-cast new-r)
        new-g-clamped (clamp-cast new-g)
        new-b-clamped (clamp-cast new-b)

        [h w _] (dtype/shape img-tensor)]
    (tensor/compute-tensor
     [h w 3]
     (fn [y x c]
       (case c
         0 (tensor/mget new-r-clamped y x)
         1 (tensor/mget new-g-clamped y x)
         2 (tensor/mget new-b-clamped y x)))
     :uint8)))

(defn simulate-color-blindness
  "Simulate color vision deficiency.
  Type: :protanopia, :deuteranopia, or :tritanopia"
  [img-tensor blindness-type]
  (apply-color-matrix img-tensor
                      (get color-blindness-matrices blindness-type)))

;; ## Simulations

(kind/table
 [[:normal :protanopia :deuteranopia :tritanopia]
  [(bufimg/tensor->image original-tensor)
   (bufimg/tensor->image (simulate-color-blindness original-tensor :protanopia))
   (bufimg/tensor->image (simulate-color-blindness original-tensor :deuteranopia))
   (bufimg/tensor->image (simulate-color-blindness original-tensor :tritanopia))]])

;; ---

;; # Advanced — Convolution & Filtering

;; Convolution is the fundamental operation behind image filters, from blur to edge
;; detection. We'll build a reusable convolution engine and apply various kernels,
;; demonstrating `tensor/compute-tensor` for windowed operations and nested iterations.

;; ## Understanding Convolution

;; [Convolution](https://en.wikipedia.org/wiki/Kernel_(image_processing)) is a
;; fundamental operation in image processing. A **kernel** (or filter) is a small
;; matrix that slides over the image. At each position, we multiply kernel values
;; by corresponding pixel values and sum the result.

;; Example: 3×3 box blur kernel (all pixels weighted equally):
;; ```
;; [1/9 1/9 1/9]
;; [1/9 1/9 1/9]
;; [1/9 1/9 1/9]
;; ```

;; ## Box Blur Kernel

;; Create a simple averaging kernel:

(defn box-blur-kernel
  "Create NxN box blur kernel (uniform averaging)."
  [n]
  (let [weight (/ 1.0 (* n n))]
    (tensor/compute-tensor
     [n n]
     (fn [_ _] weight)
     :float32)))

(def kernel-3x3 (box-blur-kernel 3))

kernel-3x3

;; ## Applying Convolution

;; Slide the kernel over the image, computing weighted sums:

(defn convolve-2d
  "Apply 2D convolution to a grayscale image.
  kernel: [kh kw] float tensor
  Returns result with same size as input (zero-padded)."
  [img-2d kernel]
  (let [[h w] (dtype/shape img-2d)
        [kh kw] (dtype/shape kernel)
        pad-h (quot kh 2)
        pad-w (quot kw 2)]
    (tensor/compute-tensor
     [h w]
     (fn [y x]
       (let [sum (atom 0.0)]
         (doseq [ky (range kh)
                 kx (range kw)]
           (let [img-y (+ y ky (- pad-h))
                 img-x (+ x kx (- pad-w))]
             (when (and (>= img-y 0) (< img-y h)
                        (>= img-x 0) (< img-x w))
               (swap! sum +
                      (* (tensor/mget kernel ky kx)
                         (tensor/mget img-2d img-y img-x))))))
         @sum))
     :float32)))

;; **Apply box blur to grayscale**:

(def blurred-gray (convolve-2d grayscale kernel-3x3))

;; Visualize blurred result:

(bufimg/tensor->image (dtype/elemwise-cast blurred-gray :uint8))

;; ## Gaussian Blur

;; [Gaussian blur](https://en.wikipedia.org/wiki/Gaussian_blur) uses a kernel based
;; on the Gaussian (normal) distribution. It weights center pixels more heavily than
;; edge pixels, producing a smooth, natural-looking blur without artifacts.

(defn gaussian-kernel
  "Create NxN Gaussian kernel with given sigma."
  [n sigma]
  (let [center (quot n 2)]
    (-> (tensor/compute-tensor
         [n n]
         (fn [y x]
           (let [dy (- y center)
                 dx (- x center)
                 dist-sq (+ (* dy dy) (* dx dx))]
             (Math/exp (/ (- dist-sq) (* 2 sigma sigma)))))
         :float32)
        ;; Normalize so weights sum to 1
        (as-> t (dfn// t (dfn/sum t))))))

(def gaussian-5x5 (gaussian-kernel 5 1.0))

gaussian-5x5

;; Apply Gaussian blur:

(def gaussian-blurred (convolve-2d grayscale gaussian-5x5))

(bufimg/tensor->image (dtype/elemwise-cast gaussian-blurred :uint8))

;; ## Sharpen Filter

;; [Unsharp masking](https://en.wikipedia.org/wiki/Unsharp_masking) sharpens images
;; by enhancing edges. We subtract a blurred version from the original to extract
;; high-frequency details, then add them back amplified.
;; Method: original + strength × (original - blur)

(defn sharpen
  "Sharpen image using unsharp mask.
  strength: how much detail to add (typical: 0.5-2.0)"
  [img-2d strength]
  (let [blurred (convolve-2d img-2d (box-blur-kernel 3))
        detail (dfn/- img-2d blurred)]
    (-> (dfn/+ img-2d (dfn/* detail strength))
        (dfn/max 0)
        (dfn/min 255))))

(def sharpened-gray (sharpen grayscale 1.5))

;; Compare original vs sharpened:

(kind/table
 [[:original :sharpened]
  [(bufimg/tensor->image grayscale)
   (bufimg/tensor->image (dtype/elemwise-cast sharpened-gray :uint8))]])

;; ## Sharpness comparison

(-> {:original grayscale
     :box (convolve-2d grayscale kernel-3x3)
     :gaussian (convolve-2d grayscale gaussian-5x5)
     :sharpened (sharpen grayscale 1.5)}
    (update-vals
     (fn [t]
       (dfn/mean (edge-magnitude
                  (gradient-x t)
                  (gradient-y t)))))
    tc/dataset)

;; ## Sobel Edge Detection

;; The [Sobel operator](https://en.wikipedia.org/wiki/Sobel_operator) is a classic
;; edge detection method that uses specialized kernels to compute gradients in X and Y
;; directions. It's more robust to noise than simple finite differences.

;; Sobel kernels detect edges in X and Y directions:

(def sobel-x-kernel
  (tensor/compute-tensor
   [3 3]
   (fn [y x]
     (case [y x]
       [0 0] -1.0, [0 1] 0.0, [0 2] 1.0
       [1 0] -2.0, [1 1] 0.0, [1 2] 2.0
       [2 0] -1.0, [2 1] 0.0, [2 2] 1.0))
   :float32))

(def sobel-y-kernel
  (tensor/compute-tensor
   [3 3]
   (fn [y x]
     (case [y x]
       [0 0] -1.0, [0 1] -2.0, [0 2] -1.0
       [1 0] 0.0, [1 1] 0.0, [1 2] 0.0
       [2 0] 1.0, [2 1] 2.0, [2 2] 1.0))
   :float32))

;; Apply Sobel filters:

(def sobel-x (convolve-2d grayscale sobel-x-kernel))
(def sobel-y (convolve-2d grayscale sobel-y-kernel))

;; Compute edge magnitude:

(def sobel-edges (dfn/sqrt (dfn/+ (dfn/sq sobel-x) (dfn/sq sobel-y))))

;; Visualize Sobel edges:

(bufimg/tensor->image
 (-> sobel-edges
     (dfn/* (/ 255.0 (max 1.0 (dfn/reduce-max sobel-edges))))
     (dtype/elemwise-cast :uint8)))

;; **Comparison**: Simple gradient vs Sobel

(let [simple-edges (edge-magnitude (gradient-x grayscale) (gradient-y grayscale))]
  {:simple-mean (dfn/mean simple-edges)
   :sobel-mean (dfn/mean sobel-edges)
   :sobel-smoother? true})

;; Sobel produces smoother, more robust edge detection.

;; ---

;; # Reshape & Downsampling

;; Explore multi-scale image processing through downsampling and pyramids.
;; We'll demonstrate `tensor/reshape` for zero-copy view transformations and
;; compare different downsampling strategies.

;; ## Understanding Reshape

;; `tensor/reshape` changes how we *view* data without copying it.
;; Unlike `tensor/select`, which extracts slices, `reshape` reinterprets
;; the entire buffer as a different shape.

;; Example: Flatten a 2D tensor to 1D

(let [small-2d (tensor/compute-tensor [3 4] (fn [y x] (+ (* y 4) x)) :int32)]
  {:original-shape (dtype/shape small-2d)
   :original small-2d
   :flattened (tensor/reshape small-2d [12])
   :flattened-shape (dtype/shape (tensor/reshape small-2d [12]))})

;; **Key insight**: Reshape is a zero-copy view operation.

;; ## Downsampling by 2×

;; [Downsampling](https://en.wikipedia.org/wiki/Downsampling_(signal_processing))
;; (decimation) reduces image resolution by discarding pixels. We select every other
;; pixel in each dimension, creating a half-size image.

(defn downsample-2x [img-2d]
  (let [[h w] (dtype/shape img-2d)]
    (tensor/select img-2d (range 0 h 2) (range 0 w 2))))

(def downsampled-gray (downsample-2x grayscale))

(tc/dataset {:metric ["Original height" "Original width"
                      "Downsampled height" "Downsampled width"]
             :value (let [[oh ow] (dtype/shape grayscale)
                          [dh dw] (dtype/shape downsampled-gray)]
                      [oh ow dh dw])})

;; Visualize original vs downsampled:

(kind/table
 [[:original :downsampled-2x]
  [(bufimg/tensor->image grayscale)
   (bufimg/tensor->image downsampled-gray)]])

;; **Verification**: Downsampled image is exactly half the size in each dimension.

;; ## Image Pyramid

;; An [image pyramid](https://en.wikipedia.org/wiki/Pyramid_(image_processing)) contains
;; the same image at multiple scales. This is essential for multi-scale analysis, feature
;; detection at different sizes, and efficient image processing algorithms.

(defn build-pyramid [img-2d levels]
  (loop [pyramid [img-2d]
         current img-2d
         level 1]
    (if (>= level levels)
      pyramid
      (let [next-level (downsample-2x current)]
        (recur (conj pyramid next-level)
               next-level
               (inc level))))))

(def gray-pyramid (build-pyramid grayscale 4))

;; Inspect pyramid shapes:

(tc/dataset {:level (range (count gray-pyramid))
             :height (mapv #(first (dtype/shape %)) gray-pyramid)
             :width (mapv #(second (dtype/shape %)) gray-pyramid)})

;; Visualize each level:

(mapv bufimg/tensor->image gray-pyramid)

;; **Use case**: Multi-scale edge detection for finding features at different sizes.

;; ## Block Average Downsampling

;; Instead of selecting pixels, we can average blocks for smoother downsampling:

(defn downsample-avg [img-2d factor]
  (let [[h w] (dtype/shape img-2d)
        new-h (quot h factor)
        new-w (quot w factor)]
    (tensor/compute-tensor
     [new-h new-w]
     (fn [y x]
       (let [block (tensor/select img-2d
                                  (range (* y factor) (* (inc y) factor))
                                  (range (* x factor) (* (inc x) factor)))]
         (dfn/mean block)))
     :float32)))

(def avg-downsampled (downsample-avg grayscale 2))

;; Compare simple vs average downsampling:

(kind/table
 [[:select-every-2nd :average-blocks]
  [(bufimg/tensor->image downsampled-gray)
   (bufimg/tensor->image avg-downsampled)]])

;; Average downsampling produces smoother results with less aliasing.

;; **Verification**: Both produce same shape, but averaging reduces noise

(tc/dataset {:method ["Select every 2nd" "Average blocks"]
             :height (mapv #(first (dtype/shape %)) [downsampled-gray avg-downsampled])
             :width (mapv #(second (dtype/shape %)) [downsampled-gray avg-downsampled])
             :mean (mapv dfn/mean [downsampled-gray avg-downsampled])})

;; ---

;; # Conclusion: The dtype-next Pattern

;; We've built a complete image analysis toolkit demonstrating core dtype-next concepts:

;; ## Key Patterns

;; 1. **Zero-copy views** — `tensor/select` creates views without copying data
;; 2. **Reduction operations** — `dfn/mean`, `dfn/standard-deviation`, etc.
;; 3. **Element-wise ops** — `dfn/+`, `dfn/*`, `dfn/sqrt` work across entire tensors
;; 4. **Type discipline** — upcast → compute → clamp → downcast for precision control
;; 5. **Functional composition** — pure functions composed with `->` and function composition
;; 6. **Objective verification** — numeric properties that can be checked in REPL

;; ## API Coverage

;; | Function | Use Case |
;; |----------|----------|
;; | `dtype/shape` | Inspect tensor dimensions |
;; | `dtype/elemwise-datatype` | Check element type |
;; | `dtype/elemwise-cast` | Convert between types |
;; | `dtype/ecount` | Total element count |
;; | `tensor/select` | Extract slices, channels |
;; | `tensor/compute-tensor` | Functionally construct tensors |
;; | `tensor/mget` | Read single elements |
;; | `dfn/mean`, `dfn/standard-deviation` | Statistics |
;; | `dfn/reduce-min`, `dfn/reduce-max` | Range finding |
;; | `dfn/+`, `dfn/-`, `dfn/*`, `dfn//` | Element-wise arithmetic |
;; | `dfn/sqrt`, `dfn/sq` | Mathematical operations |
;; | `dfn/min`, `dfn/max` | Clamping |
;; | `dfn/sum` | Counting (boolean buffers) |

;; ## What Makes This Functional?

;; - **Pure functions** — all transformations return new values
;; - **Immutable views** — original data never changes
;; - **Composition** — operations chain naturally
;; - **Lazy evaluation** — computations deferred until needed
;; - **No mutation** — even `tensor/compute-tensor` builds new structures

;; ## Next Steps

;; - **Batch processing**: Apply analysis to multiple images
;; - **More transformations**: Blur, sharpen, rotation
;; - **Dataset integration**: Use `tech.ml.dataset` for tabular results
;; - **Performance**: Profile and optimize hot paths
;; - **Native interop**: Interface with OpenCV, TensorFlow

;; ## Resources

;; - [dtype-next docs](https://cnuernber.github.io/dtype-next/)
;; - [tech.ml.dataset guide](https://techascent.github.io/tech.ml.dataset/)
;; - [Scicloj tutorials](https://scicloj.github.io/)

;; ---

;; *Questions, corrections, or ideas? Open an issue on the Clojure Civitas repository.*
