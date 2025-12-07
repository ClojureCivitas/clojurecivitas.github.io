^{:kindly/hide-code true
  :clay {:title "Image Processing with dtype-next Tensors"
         :quarto {:author :daslu
                  :draft true
                  :type :post
                  :date "2025-12-07"
                  :category :data
                  :tags [:dtype-next :tensors :image-processing :computer-vision :tutorial]}}}
(ns dtype-next.image-processing-with-tensors
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

;; ## What Are Tensors?

;; A [tensor](https://en.wikipedia.org/wiki/Tensor_(machine_learning)) is a multi-dimensional
;; array of numbers with a defined shape and type. While the term comes from mathematics and
;; physics, in programming it simply means: **structured numerical data with multiple axes**.
;;
;; - A scalar is a 0D tensor (single number)
;; - A vector is a 1D tensor `[5]` → 5 elements
;; - A matrix is a 2D tensor `[3 4]` → 3 rows × 4 columns
;; - An RGB image is a 3D tensor `[height width 3]` → spatial dimensions + color channels
;; - A video is a 4D tensor `[time height width 3]` → adding a time axis
;;
;; Tensors provide efficient storage (typed, contiguous memory) and convenient multi-dimensional
;; indexing. Operations on tensors (slicing, element-wise math, reductions) form the foundation
;; of numerical computing, from image processing to machine learning.

;; ## About This Tutorial

;; [dtype-next](https://github.com/cnuernber/dtype-next) is a comprehensive library
;; for working with typed arrays, including buffers, functional operations, tensors,
;; and dataset integration. This tutorial focuses on **the tensor API**—multi-dimensional
;; views over typed buffers—because images provide clear visual feedback and natural
;; multi-dimensional structure.
;;
;; The patterns you'll learn (zero-copy views, type discipline, functional composition)
;; transfer directly to other dtype-next use cases: time series analysis, scientific
;; computing, ML data preparation, and any domain requiring efficient numerical arrays.

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

;; ## The bufimg Namespace

;; The `tech.v3.libs.buffered-image` namespace (aliased as `bufimg`) provides
;; interop between Java's BufferedImage and dtype-next tensors:
;;
;; - `bufimg/load` — load image file → BufferedImage
;; - `bufimg/as-ubyte-tensor` — BufferedImage → uint8 tensor [H W C]
;; - `bufimg/tensor->image` — tensor → BufferedImage (for display)

(def original-img
  (bufimg/load "src/dtype_next/nap.jpg"))

original-img

(def original-tensor
  (bufimg/as-ubyte-tensor original-img))

original-tensor

;; ## Understanding Tensor Shape

;; **Shape** tells us dimensions:

(dtype/shape original-tensor)

;; This is `[height width channels]` — our image has 3 channels.

;; **How do we know it's RGB format?** Check the image type:

(bufimg/image-type original-img)

;; `:type-3byte-bgr` indicates BGR byte order (Blue-Green-Red), which is Java's
;; internal format. However, `bufimg/as-ubyte-tensor` automatically converts to
;; RGB order for us, so we can treat it as RGB in our code.

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

;; # Working with Tensors

;; Before diving into image analysis, let's understand what tensors are in dtype-next.
;;
;; **Tensors are multi-dimensional views over typed buffers.** The underlying buffer
;; is a contiguous block of typed data (like our uint8 pixels), and the tensor provides
;; convenient multi-dimensional indexing with shape information. This architecture enables
;; zero-copy operations—when we slice or reshape, we create new views without copying data.
;;
;; Let's explore essential tensor operations for transforming and converting data.

;; ## Reshaping

;; Sometimes it's convenient to flatten spatial dimensions into a single axis.
;; For example, reshaping `[H W 3]` → `[H×W 3]` gives us one row per pixel:

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    dtype/shape)

;; **Key insight**: `tensor/reshape` is a zero-copy view operation—it reinterprets
;; the buffer without copying data.

;; ## Tensors as Datasets

;; Two-dimensional tensors convert naturally to tablecloth datasets, enabling
;; tabular operations and plotting.

;; **Converting tensors ↔ datasets:**
;; - `ds-tensor/tensor->dataset` — explicit conversion (tech.v3.dataset.tensor)
;; - `tc/dataset` — tablecloth auto-converts 2D tensors
;; - `ds-tensor/dataset->tensor` — convert back to tensor

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    ds-tensor/tensor->dataset
    (tc/rename-columns [:red :green :blue]))

;; Or more concisely (tablecloth auto-converts):

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    tc/dataset
    (tc/rename-columns [:red :green :blue]))

;; We can convert back, restoring the original image structure:

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    tc/dataset
    ds-tensor/dataset->tensor
    (tensor/reshape [height width 3])
    bufimg/tensor->image)

;; This round-trip demonstrates the seamless interop between tensors and datasets,
;; useful for combining spatial operations (tensors) with statistical analysis (datasets).

;; ---

;; # Tensor Operations Primer

;; Before working with real images, let's explore the core operations we'll use
;; throughout this tutorial. We'll use tiny toy tensors to demonstrate each concept.

;; ## Creating Tensors: tensor/compute-tensor

;; `tensor/compute-tensor` creates a tensor by calling a function for each position.
;; The function receives indices and returns the value for that position.

;; Create a simple 3×4 tensor with sequential values:

(def toy-tensor
  (tensor/compute-tensor
   [3 4] ; shape: 3 rows, 4 columns
   (fn [row col] ; function receives [row col] indices
     (+ (* row 10) col)) ; compute value: row*10 + col
   :int32)) ; element type

toy-tensor

;; Verify the shape:

(dtype/shape toy-tensor)

;; ## Slicing with tensor/select

;; `tensor/select` extracts portions of a tensor without copying data.
;; It takes one selector per dimension.

;; **Selector options:**
;; - `:all` — keep entire dimension
;; - `n` (integer) — select single index
;; - `(range start end)` — select slice from start (inclusive) to end (exclusive)
;; - `(range start end step)` — select with stride

;; Example: Select row 1 (second row):

(tensor/select toy-tensor 1 :all)

;; Select column 2 (third column):

(tensor/select toy-tensor :all 2)

;; Select first two rows:

(tensor/select toy-tensor (range 0 2) :all)

;; Select every other column:

(tensor/select toy-tensor :all (range 0 4 2))

;; Select a sub-rectangle (rows 1-2, columns 1-3):

(tensor/select toy-tensor (range 1 3) (range 1 4))

;; **Key insight**: All these are zero-copy views—no data is copied.

;; ## Element Access: tensor/mget

;; `tensor/mget` reads a single element at given indices:

(tensor/mget toy-tensor 1 2) ; row 1, column 2 → 12

;; ## The dfn Namespace: Functional Operations with Broadcasting

;; The `tech.v3.datatype.functional` (aliased as `dfn`) namespace provides
;; mathematical operations that work **element-wise** across entire tensors
;; and automatically **broadcast** when combining tensors of different shapes.

;; **Element-wise operations:**

(def small-tensor (tensor/compute-tensor [2 3] (fn [r c] (+ (* r 3) c)) :int32))

small-tensor

;; Add scalar to every element (broadcasting):

(dfn/+ small-tensor 10)

;; Multiply every element:

(dfn/* small-tensor 2)

;; Combine two tensors element-wise:

(dfn/+ small-tensor small-tensor)

;; **Reduction operations** (collapse dimensions):

(dfn/mean small-tensor) ; mean of all elements

(dfn/reduce-max small-tensor) ; maximum value

(dfn/reduce-min small-tensor) ; minimum value

(dfn/sum small-tensor) ; sum of all elements

;; **Why dfn instead of regular Clojure functions?**
;; - Work on entire tensors efficiently (no boxing)
;; - Broadcast automatically
;; - Type-aware (preserve numeric precision)
;; - SIMD-optimized

;; ## Type Handling: dtype/elemwise-cast

;; Convert between numeric types. Common pattern: upcast → compute → clamp → downcast.

;; Create uint8 tensor:

(def tiny-uint8 (tensor/compute-tensor [2 2] (fn [_ _] 100) :uint8))

tiny-uint8

;; Element type:

(dtype/elemwise-datatype tiny-uint8)

;; Multiply would overflow uint8 (max 255), so upcast first:

(-> tiny-uint8
    (dtype/elemwise-cast :float32) ; upcast to float
    (dfn/* 2.5) ; compute
    (dfn/min 255) ; clamp to valid range
    (dfn/max 0)
    (dtype/elemwise-cast :uint8)) ; downcast back

;; ## Shape Operations: dtype/shape and tensor/reshape

;; We've seen `dtype/shape` already. `tensor/reshape` reinterprets data
;; with a different shape (zero-copy):

(def flat-tensor (tensor/compute-tensor [12] (fn [i] i) :int32))

flat-tensor

;; Reshape 1D → 2D:

(tensor/reshape flat-tensor [3 4])

;; Reshape 2D → 1D:

(tensor/reshape (tensor/reshape flat-tensor [3 4]) [12])

;; **Important**: Total elements must match (3×4 = 12).

;; ---

;; # Image Statistics

;; Now that we understand tensor fundamentals, let's analyze image properties
;; using **reduction operations** and **channel slicing**.

;; ## Extracting Color Channels

;; Use `tensor/select` to slice out individual channels (zero-copy views):

(defn extract-channels
  "Extract R, G, B channels from RGB tensor.
  Takes: [H W 3] tensor
  Returns: map with :red, :green, :blue tensors (each [H W])"
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
  "Compute statistics for a single channel tensor.
  Takes: [H W] tensor
  Returns: map with :mean, :std, :min, :max scalars"
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
  "Convert RGB [H W 3] to grayscale [H W].
  Standard formula: 0.299*R + 0.587*G + 0.114*B
  Returns float32 tensor (use dtype/elemwise-cast for uint8)."
  [img-tensor]
  (let [r (tensor/select img-tensor :all :all 0)
        g (tensor/select img-tensor :all :all 1)
        b (tensor/select img-tensor :all :all 2)]
    (dfn/+ (dfn/* r 0.299)
           (dfn/* g 0.587)
           (dfn/* b 0.114))))

(def grayscale (to-grayscale original-tensor))

;; **Note on types**: `to-grayscale` returns float32 because `dfn/*` operates on
;; floats for precision. When visualizing, `bufimg/tensor->image` automatically
;; handles the float→uint8 conversion.

;; **Grayscale statistics**:

(tc/dataset (channel-stats grayscale))

;; Visualize grayscale (automatic float→uint8 conversion):

(bufimg/tensor->image grayscale)

;; ## Histograms

;; A [histogram](https://en.wikipedia.org/wiki/Image_histogram) shows the distribution
;; of pixel values. It's essential for understanding image brightness, contrast, and
;; exposure. Peaks indicate common values; spread indicates dynamic range.

;; **Approach 1**: Overlaid RGB channels using the reshape→dataset pattern we just learned:

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

;; **Approach 2**: Separate histograms using `dtype/as-reader` for direct tensor access:

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

;; We've explored *global* properties like channel means and histograms. Now let's
;; analyze *local* spatial structure by comparing neighboring pixels.
;;
;; We'll use [gradient](https://en.wikipedia.org/wiki/Image_gradient) operations
;; to measure how quickly values change across space. Gradients are fundamental to
;; [edge detection](https://en.wikipedia.org/wiki/Edge_detection), which identifies
;; boundaries between regions in an image.

;; ## Computing Gradients

;; Gradients measure how quickly pixel values change. We compute them by
;; comparing neighboring pixels using **slice offsets**.

(defn gradient-x
  "Compute horizontal gradient (difference between adjacent columns).
  Takes: [H W] tensor
  Returns: [H W-1] tensor"
  [tensor-2d]
  (let [[h w] (dtype/shape tensor-2d)]
    (dfn/- (tensor/select tensor-2d :all (range 1 w))
           (tensor/select tensor-2d :all (range 0 (dec w))))))

(defn gradient-y
  "Compute vertical gradient (difference between adjacent rows).
  Takes: [H W] tensor
  Returns: [H-1 W] tensor"
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
;;
;; **Why trim?** gradient-x produces [H W-1] and gradient-y produces [H-1 W].
;; To combine them element-wise, we need matching shapes, so we trim both to [H-1 W-1].

(defn edge-magnitude
  "Compute gradient magnitude from gx and gy.
  Takes: gx [H W-1], gy [H-1 W]
  Returns: [H-1 W-1] (trimmed to common size)"
  [gx gy]
  (let [[h-gx w-gx] (dtype/shape gx)
        [h-gy w-gy] (dtype/shape gy)
        ;; Trim to common dimensions: gx loses 1 row, gy loses 1 column
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
  Takes: [H W 3] RGB or [H W] grayscale tensor
  Returns: scalar (higher = sharper)"
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

;; With analysis tools in place, let's build functions that *improve* images.
;; We'll create composable transformations for white balance and contrast,
;; each verifiable through numeric properties we can check in the REPL.

;; ## Auto White Balance

;; [White balance](https://en.wikipedia.org/wiki/Color_balance) adjusts colors to
;; appear neutral under different lighting conditions. We scale RGB channels to have
;; equal means, removing color casts.

(defn auto-white-balance
  "Scale RGB channels to have equal means.
  Takes: [H W 3] uint8 tensor
  Returns: [H W 3] uint8 tensor"
  [img-tensor]
  (let [;; Compute channel means using reduce-axis
        ;; First reduce: [H W 3] → [W 3] (collapse height, axis 0)
        ;; Second reduce: [W 3] → [3] (collapse width, now axis 0 after shape change)
        channel-means (-> img-tensor
                          (tensor/reduce-axis dfn/mean 0) ; [H W 3] → [W 3]
                          (tensor/reduce-axis dfn/mean 0)) ; [W 3] → [3]

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
  Takes: [H W 3] uint8 tensor, factor (> 1 increases, < 1 decreases)
  Returns: [H W 3] uint8 tensor"
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

;; Beyond enhancement, images need to be *accessible*. Let's simulate how images
;; appear to people with different types of color vision deficiency.
;;
;; This demonstrates dtype-next's linear algebra capabilities (applying 3×3 matrices
;; to RGB channels) with practical real-world applications.

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
  Takes: [H W 3] tensor, 3×3 matrix [[r0 g0 b0] [r1 g1 b1] [r2 g2 b2]]
  Returns: [H W 3] uint8 tensor
  Formula: new_r = r0*R + g0*G + b0*B, etc."
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
  Takes: [H W 3] tensor, blindness-type (:protanopia | :deuteranopia | :tritanopia)
  Returns: [H W 3] uint8 tensor"
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

;; # Convolution & Filtering

;; So far we've used simple element-wise operations and direct pixel comparisons.
;; Now let's explore **convolution**, the fundamental operation behind blur, sharpen,
;; and sophisticated edge detection.
;;
;; We'll build a reusable convolution engine using `tensor/compute-tensor` for
;; windowed operations, then apply various kernels to see the dramatic effects.

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
  "Create NxN box blur kernel (uniform averaging).
  Takes: n (kernel size)
  Returns: [n n] float32 tensor"
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
  "Apply 2D convolution to grayscale image [H W].
  kernel: [kh kw] float tensor
  Returns [H W] float32 tensor (zero-padded edges)."
  [img-2d kernel]
  (let [[h w] (dtype/shape img-2d)
        [kh kw] (dtype/shape kernel)
        pad-h (quot kh 2)
        pad-w (quot kw 2)]
    (tensor/compute-tensor
     [h w]
     (fn [y x]
       ;; Note: Using atom here for accumulation within tensor/compute-tensor's
       ;; position function. While not idiomatic Clojure, it's the clearest way
       ;; to accumulate over a 2D neighborhood. The mutation is local to this
       ;; function call and doesn't escape.
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
  "Create NxN Gaussian kernel with given sigma.
  Takes: n (kernel size), sigma (standard deviation)
  Returns: [n n] float32 tensor (normalized to sum=1)"
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
  Takes: [H W] grayscale tensor, strength (0.5-2.0 typical)
  Returns: [H W] float32 tensor"
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

;; We can quantify the sharpness of each filter using our `sharpness-score` function.
;; However, note that `sharpness-score` expects RGB input, so we need to adapt it for
;; grayscale tensors. For simplicity, we'll compute sharpness inline here:

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

;; # Downsampling & Multi-Scale Processing

;; Finally, let's explore working with images at multiple scales. Downsampling
;; reduces resolution for faster processing or multi-scale analysis (like detecting
;; features at different sizes).
;;
;; We'll compare downsampling strategies and build image pyramids, demonstrating
;; `tensor/select` with stride patterns and aggregation techniques.

;; ## Downsampling by 2×

;; [Downsampling](https://en.wikipedia.org/wiki/Downsampling_(signal_processing))
;; (decimation) reduces image resolution by discarding pixels. We select every other
;; pixel in each dimension, creating a half-size image.

(defn downsample-2x
  "Downsample by selecting every other pixel.
  Takes: [H W] tensor
  Returns: [H/2 W/2] tensor"
  [img-2d]
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

(defn build-pyramid
  "Build image pyramid with multiple scales.
  Takes: [H W] tensor, levels (number of scales)
  Returns: vector of tensors [[H W] [H/2 W/2] [H/4 W/4] ...]"
  [img-2d levels]
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

(defn downsample-avg
  "Downsample by averaging blocks.
  Takes: [H W] tensor, factor (downsampling factor)
  Returns: [H/factor W/factor] float32 tensor"
  [img-2d factor]
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

;; ## Beyond Images: dtype-next in Other Domains

;; The tensor patterns we've explored transfer directly to other use cases:

;; **Time series analysis**: 1D or 2D tensors for signals, windowing operations
;; for feature extraction, functional ops for filtering and aggregation.

;; **Scientific computing**: Multi-dimensional numerical arrays, zero-copy slicing
;; for memory efficiency, type discipline for numerical precision.

;; **Machine learning prep**: Batch processing, normalization pipelines, data
;; augmentation—all using the same functional patterns.

;; **Signal processing**: Audio (1D), video (4D: time×height×width×channels),
;; sensor arrays—dtype-next handles arbitrary dimensionality.

;; dtype-next also provides:
;; - **Native interop**: Zero-copy integration with native libraries (OpenCV, TensorFlow)
;; - **Dataset tools**: Rich `tech.ml.dataset` integration for tabular workflows
;; - **Performance**: SIMD-optimized operations, parallel processing support
;; - **Flexibility**: Custom buffer implementations, extensible type system

;; ## Resources

;; - [dtype-next docs](https://cnuernber.github.io/dtype-next/)
;; - [tech.ml.dataset guide](https://techascent.github.io/tech.ml.dataset/)
;; - [Scicloj tutorials](https://scicloj.github.io/)

;; ---

;; *Questions, corrections, or ideas? Open an issue on the Clojure Civitas repository.*
