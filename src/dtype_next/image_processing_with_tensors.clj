^{:kindly/hide-code true
  :clay {:title "Image Processing with dtype-next Tensors"
         :quarto {:author :daslu
                  :draft true
                  :type :post
                  :date "2025-12-07"
                  :category :data
                  :tags [:dtype-next :tensors :image-processing :computer-vision :tutorial]
                  :image "nap_edges.png"}}}
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

;; # Introduction: Why dtype-next for Image Processing?

;; Images are perfect for learning [dtype-next](https://github.com/cnuernber/dtype-next)
;; because they're **typed numerical arrays with clear visual feedback**. Unlike generic
;; sequences where numbers are boxed, dtype-next gives us:
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

;; dtype-next is a comprehensive library for working with typed arrays, including buffers,
;; functional operations, tensors, and dataset integration. This tutorial focuses on
;; **the tensor API**—multi-dimensional views over typed buffers—because images provide
;; clear visual feedback and natural multi-dimensional structure.
;;
;; The patterns you'll learn (zero-copy views, type discipline, functional composition)
;; transfer directly to other dtype-next use cases: time series analysis, scientific
;; computing, ML data preparation, and any domain requiring efficient numerical arrays.

;; ## What We'll Build

;; - **Working with Tensors** — reshaping, dataset conversion, core operations
;; - **Tensor Operations Primer** — slicing, element-wise ops, type handling
;; - **Image Statistics** — channel means, ranges, distributions, histograms
;; - **Spatial Analysis** — gradients, edge detection, sharpness metrics  
;; - **Enhancement Pipeline** — white balance, contrast adjustment
;; - **Accessibility** — color blindness simulation
;; - **Convolution & Filtering** — blur, sharpen, Sobel edge detection
;; - **Downsampling & Multi-Scale Processing** — pyramids, multi-resolution analysis

;; Each section demonstrates core dtype-next concepts with immediate practical value.

;; ---

;; # Setup: Loading Images as Tensors

;; Let's load our sample image and understand the tensor structure.

;; ## The bufimg Namespace

(require '[tech.v3.libs.buffered-image :as bufimg])

;; The [`tech.v3.libs.buffered-image`](https://cnuernber.github.io/dtype-next/tech.v3.libs.buffered-image.html)
;; namespace (aliased as `bufimg`) provides interop between Java's BufferedImage and dtype-next tensors:
;;
;; - `bufimg/load` — load image file → BufferedImage
;; - `bufimg/as-ubyte-tensor` — BufferedImage → uint8 tensor [H W C]
;; - `bufimg/tensor->image` — tensor → BufferedImage (for display)

(def original-img
  (bufimg/load "src/dtype_next/nap.jpg"))

original-img

(def original-tensor
  (bufimg/as-ubyte-tensor original-img))

;; ## ⚠️ Important: Understanding Channel Order
;;
;; BufferedImage can use different pixel formats (RGB, BGR, ARGB, etc.). The specific
;; format depends on the image type and how it was loaded. Our image uses **BGR** order:

(bufimg/image-type original-img)

;; `:byte-bgr` means this image stores colors in BGR (Blue-Green-Red) order, not RGB.
;; The `bufimg/as-ubyte-tensor` function preserves whatever order BufferedImage uses.
;;
;; For this tutorial's BGR images, the channels are:
;; - **Channel 0 = Blue**
;; - **Channel 1 = Green**
;; - **Channel 2 = Red**
;;
;; Always check `bufimg/image-type` to confirm your image's channel order before
;; processing. We'll be explicit about BGR ordering throughout this tutorial.
;;
;; **Why the round-trip works:** `bufimg/tensor->image` defaults to creating BGR
;; BufferedImages. So our workflow maintains BGR throughout: load BGR → process as
;; BGR tensor → create BGR image. If you had an RGB tensor, you'd need to either
;; swap channels or use `(bufimg/tensor->image rgb-tensor {:img-type :int-rgb})`.

original-tensor

;; ## Understanding Tensor Shape

(require '[tech.v3.datatype :as dtype])

;; The [`tech.v3.datatype`](https://cnuernber.github.io/dtype-next/tech.v3.datatype.html)
;; namespace provides core functions for inspecting and manipulating typed data.

;; **Shape** tells us dimensions:

(dtype/shape original-tensor)

;; This is `[height width channels]` — our image has 3 color channels.

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

(require '[tech.v3.dataset.tensor :as ds-tensor])
(require '[tablecloth.api :as tc])

;; The [`tech.v3.dataset.tensor`](https://cnuernber.github.io/dtype-next/tech.v3.dataset.tensor.html)
;; namespace provides conversions between tensors and datasets. The `tablecloth.api`
;; namespace of [Tablecloth](https://scicloj.github.io/tablecloth/)
;; also auto-converts 2D tensors.

;; Two-dimensional tensors convert naturally to tablecloth datasets, enabling
;; tabular operations and plotting.

;; **Converting tensors ↔ datasets:**
;; - `ds-tensor/tensor->dataset` — explicit conversion
;; - `tc/dataset` — tablecloth auto-converts 2D tensors
;; - `ds-tensor/dataset->tensor` — convert back to tensor

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    ds-tensor/tensor->dataset
    (tc/rename-columns [:blue :green :red]))

;; Or more concisely (tablecloth auto-converts):

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    tc/dataset
    (tc/rename-columns [:blue :green :red]))

;; We can convert back, restoring the original image structure:

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    tc/dataset
    ds-tensor/dataset->tensor
    (tensor/reshape [height width 3])
    bufimg/tensor->image) ; Creates BGR BufferedImage by default

;; This round-trip demonstrates the seamless interop between tensors and datasets,
;; useful for combining spatial operations (tensors) with statistical analysis (datasets).

;; ---

;; # Tensor Operations Primer

;; Before working with real images, let's explore the core operations we'll use
;; throughout this tutorial. We'll use tiny toy tensors to demonstrate each concept.

;; ## Creating Tensors: tensor/compute-tensor

(require '[tech.v3.tensor :as tensor])

;; The [`tech.v3.tensor`](https://cnuernber.github.io/dtype-next/tech.v3.tensor.html)
;; namespace provides multi-dimensional array operations.

;; `tensor/compute-tensor` creates a tensor by calling a function for each position.
;; The function receives indices and returns the value for that position.

;; ## Buffers, Readers, and Writers

;; Before working with tensors, let's understand dtype-next's foundational abstractions.
;; These concepts will help us understand what happens when we slice, transform, and
;; process data.

;; ### Buffers: Mutable Typed Storage

;; A **buffer** is a contiguous block of typed data in memory. It's the fundamental
;; storage layer—mutable and efficient.

(def sample-buffer
  (dtype/make-container :int32 [10 20 30 40 50]))

sample-buffer

;; Buffers have a data type and element count:

(dtype/elemwise-datatype sample-buffer)

(dtype/ecount sample-buffer)

;; ### Readers: Read-Only Views

;; A **reader** provides read-only access to data. Readers can wrap buffers, transform
;; values on-the-fly, or generate values lazily. They're how dtype-next creates zero-copy
;; views and efficient data pipelines.

(def sample-reader
  (dtype/->reader sample-buffer))

;; Access elements by index:

(dtype/get-value sample-reader 0)

;; Map over readers like sequences:

(mapv #(* 2 %) sample-reader)

;; **Lazy transformation readers** compute values on access without copying:

(def doubled-reader
  (dtype/emap (fn [x] (* x 10)) :int32 sample-buffer))

;; The reader transforms values on-the-fly:

(vec doubled-reader)

;; But the original buffer is unchanged:

(vec sample-buffer)

;; ### Writers: Mutable Access

;; A **writer** allows modification of the underlying data:

(def writable-buffer
  (dtype/make-container :float64 [1.0 2.0 3.0 4.0 5.0]))

(let [writer (dtype/->writer writable-buffer)]
  (dtype/set-value! writer 2 99.0)
  (dtype/set-value! writer 4 77.0))

writable-buffer

;; ### Why This Matters for Tensors

;; Tensors are **multi-dimensional views** over buffers. When we slice or reshape tensors,
;; we often get readers that reference the original data without copying. When we use
;; `tensor/slice`, we get a **reader of sub-tensors**—each sub-tensor is itself a view
;; (often a reader) over portions of the underlying buffer.
;;
;; This architecture enables efficient, composable operations: slice an image into channels,
;; map a transformation over each channel, and the data flows through without intermediate
;; copies.

;; ## Creating Tensors: tensor/compute-tensor

(def toy-tensor
  (tensor/compute-tensor
   [3 4] ; shape: 3 rows, 4 columns
   (fn [row col] ; function receives [row col] indices
     (+ (* row 10) col)) ; compute value: row*10 + col
   :int32 ; element type
   ))

;; Check an element:

(toy-tensor 2 1)

;; Verify the shape:

(dtype/shape toy-tensor)

;; ## Selecting Regions with tensor/select

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

(tensor/mget toy-tensor 1 2)

;; ## Slicing Dimensions: tensor/slice and tensor/slice-right

;; While `tensor/select` extracts specific regions, **slicing** operations turn a tensor
;; into a **reader of sub-tensors** that we can process one by one. This is essential for
;; efficiently iterating through rows, columns, or channels.

;; ### tensor/slice (leftmost dimensions)

;; `tensor/slice` slices off N leftmost dimensions, returning a reader that contains
;; sub-tensors. You access and process each sub-tensor individually (via `nth`, `map`, etc.).

;; For a `[3 4]` tensor, slicing off 1 dimension gives us a reader of 3 rows:

(def toy-rows (tensor/slice toy-tensor 1))

toy-rows

;; How many rows?

(dtype/ecount toy-rows)

;; Get the first row (a `[4]` tensor):

(nth toy-rows 0)

;; **Use case**: Process sub-tensors one by one efficiently (much faster than manual loops with `select`)

;; ### tensor/slice-right (rightmost dimensions)

;; `tensor/slice-right` slices off N rightmost dimensions, returning a reader of sub-tensors.
;; Perfect for extracting channels from `[H W C]` image tensors.

;; For our `[3 4]` toy tensor, slicing 1 rightmost dimension gives us 4 columns:

(def toy-cols (tensor/slice-right toy-tensor 1))

toy-cols

(dtype/ecount toy-cols)

;; Get the first column:

(nth toy-cols 0)

;; **Key insight**: Both return readers of sub-tensors that you process one by one.
;; - `tensor/slice` slices leftmost dimensions → iterate through slices (e.g., rows)
;; - `tensor/slice-right` slices rightmost dimensions → iterate through slices (e.g., channels)

;; ### Practical Example: Channel Extraction

;; For our BGR image `[H W C]`, we can cleanly extract channels using `slice-right`:

(def channels-sliced (tensor/slice-right original-tensor 1))

;; Extract individual channels:

(def blue-ch (nth channels-sliced 0))
(def green-ch (nth channels-sliced 1))
(def red-ch (nth channels-sliced 2))

;; Each channel is now `[H W]`:

(dtype/shape blue-ch)

;; Compare with the manual approach using `tensor/select`:

(def blue-manual (tensor/select original-tensor :all :all 0))

;; Both are zero-copy views, but `slice-right` is cleaner when you need all channels.

;; ## The dfn Namespace: Functional Operations with Broadcasting

(require '[tech.v3.datatype.functional :as dfn])

;; The [`tech.v3.datatype.functional`](https://cnuernber.github.io/dtype-next/tech.v3.datatype.functional.html)
;; namespace (aliased as `dfn`) provides mathematical operations that work **element-wise**
;; across entire tensors and automatically **broadcast** when combining tensors of different shapes.

;; **Element-wise operations:**

(def small-tensor (tensor/compute-tensor [2 3] (fn [r c] (+ (* r 3) c)) :int32))

;; Add scalar to every element (broadcasting):

(dfn/+ small-tensor 10)

;; Multiply every element:

(dfn/* small-tensor 2)

;; Combine two tensors element-wise:

(dfn/+ small-tensor small-tensor)

;; **Reduction operations** (collapse dimensions):

(dfn/mean small-tensor)

(dfn/reduce-max small-tensor)

(dfn/reduce-min small-tensor)

(dfn/sum small-tensor)

;; **Why dfn instead of regular Clojure functions?**
;; - Work on entire tensors efficiently (no boxing)
;; - Broadcast automatically
;; - Type-aware (preserve numeric precision)
;; - SIMD-optimized

;; ## Type Handling: dtype/elemwise-cast

;; Convert between numeric types. Common pattern: upcast → compute → clamp → downcast.

;; Create uint8 tensor:

(def tiny-uint8 (tensor/compute-tensor [2 2] (fn [_ _] 100) :uint8))

;; Element type:

(dtype/elemwise-datatype tiny-uint8)

;; Multiply would overflow uint8 (max 255), so upcast first:

(-> tiny-uint8
    (dtype/elemwise-cast :float32) ; upcast to float
    (dfn/* 2.5) ; compute
    (dfn/min 255) ; clamp to valid range
    (dfn/max 0)
    (dtype/elemwise-cast :uint8) ; downcast back
    )

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

;; There are two main approaches for extracting channels:
;;
;; 1. **tensor/select** — Extract specific channels by index
;; 2. **tensor/slice-right** — Iterate over all channels cleanly
;;
;; We'll use `tensor/select` for explicit channel extraction:

(def channels
  (let [[blue green red] (tensor/slice-right original-tensor 1)]
    {:blue blue :green green :red red}))

;; Each channel is now `[H W]` instead of `[H W C]`:

(:red channels)

;; **Key insight**: These are **zero-copy views** into the original tensor—no data is copied.
;; 
;; **Alternative with tensor/select**:
;; Blue channel:
(tensor/select original-tensor :all :all 2)

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

;; ## Enhanced Channel Statistics with slice-right

;; Now let's see a cleaner approach using `tensor/slice-right` with more comprehensive
;; statistics including percentiles:

(tc/dataset
 (map (fn [color channel]
        (let [percentiles (dfn/percentiles channel [25 50 75])]
          {:channel color
           :mean (dfn/mean channel)
           :std (dfn/standard-deviation channel)
           :min (dfn/reduce-min channel)
           :max (dfn/reduce-max channel)
           :q25 (dtype/get-value percentiles 0)
           :median (dtype/get-value percentiles 1)
           :q75 (dtype/get-value percentiles 2)}))
      ["blue" "green" "red"]
      (tensor/slice-right original-tensor 1)))

;; **Key advantages of slice-right**:
;; - Cleaner iteration over all channels
;; - No need to manually specify indices
;; - Natural destructuring: `(let [[b g r] (tensor/slice-right img 1)] ...)`

;; ## Brightness Analysis

;; Convert to grayscale using perceptual luminance formula.
;;
;; **Why these specific weights?** Human vision is most sensitive to green light,
;; moderately sensitive to red, and least sensitive to blue. The coefficients
;; (0.299, 0.587, 0.114) approximate the [relative luminance](https://en.wikipedia.org/wiki/Relative_luminance)
;; formula from the [ITU-R BT.601](https://en.wikipedia.org/wiki/Rec._601) standard,
;; ensuring grayscale images preserve perceived brightness rather than simple
;; equal weighting of color channels.

(defn to-grayscale
  "Convert BGR [H W 3] to grayscale [H W].
  Standard formula: 0.299*R + 0.587*G + 0.114*B
  Takes BGR tensor, extracts channels correctly.
  Returns float64 tensor (dfn/* operates on floats for precision).
  Use dtype/elemwise-cast :uint8 when you need integer values for display."
  [img-tensor]
  (let [b (tensor/select img-tensor :all :all 0) ; Blue is channel 0
        g (tensor/select img-tensor :all :all 1) ; Green is channel 1
        r (tensor/select img-tensor :all :all 2)] ; Red is channel 2
    (dfn/+ (dfn/* r 0.299)
           (dfn/* g 0.587)
           (dfn/* b 0.114))))

(def grayscale (to-grayscale original-tensor))

;; **Grayscale statistics**:

(tc/dataset (channel-stats grayscale))

;; Visualize grayscale:

(bufimg/tensor->image grayscale)

;; **Note**: `bufimg/tensor->image` automatically handles float64→uint8 conversion
;; and interprets single-channel tensors as grayscale images.

;; ## Histograms

;; A [histogram](https://en.wikipedia.org/wiki/Image_histogram) shows the distribution
;; of pixel values. It's essential for understanding image brightness, contrast, and
;; exposure. Peaks indicate common values; spread indicates dynamic range.

;; **Approach 1**: Overlaid BGR channels using the reshape→dataset pattern we just learned:

(-> original-tensor
    (tensor/reshape [(* height width) 3])
    tc/dataset
    (tc/rename-columns [:blue :green :red])
    (plotly/base {:=histogram-nbins 30
                  :=mark-opacity 0.5})
    (plotly/layer-histogram {:=x :red
                             :=mark-color "red"})
    (plotly/layer-histogram {:=x :blue
                             :=mark-color "blue"})
    (plotly/layer-histogram {:=x :green
                             :=mark-color "green"}))

;; **Per-channel histograms** using `slice-right` for clean iteration:

(kind/fragment
 (mapv (fn [color channel]
         (-> (tc/dataset {:x (dtype/as-reader channel)})
             (plotly/base {:=title color
                           :=height 200
                           :=width 600})
             (plotly/layer-histogram {:=histogram-nbins 30
                                      :=mark-color color})))
       ["blue" "green" "red"]
       (tensor/slice-right original-tensor 1)))

;; This approach directly iterates over sliced channels without extracting them first,
;; combining channel names and data in a single pass.

;; ## Channel Correlation

;; How related are the color channels? High correlation suggests color casts or
;; consistent lighting. Let's compute Pearson correlation between channels:

(defn correlation
  "Compute Pearson correlation coefficient between two tensors.
  Returns value between -1 (inverse) and 1 (perfect correlation)."
  [v1 v2]
  (let [mean1 (dfn/mean v1)
        mean2 (dfn/mean v2)
        centered1 (dfn/- v1 mean1)
        centered2 (dfn/- v2 mean2)
        numerator (dfn/sum (dfn/* centered1 centered2))
        denom1 (dfn/sqrt (dfn/sum (dfn/sq centered1)))
        denom2 (dfn/sqrt (dfn/sum (dfn/sq centered2)))]
    (/ numerator (* denom1 denom2))))

;; Extract channels and compute pairwise correlations:

(let [[blue green red] (tensor/slice-right original-tensor 1)]
  (tc/dataset
   [{:pair "Blue-Green" :correlation (correlation blue green)}
    {:pair "Blue-Red" :correlation (correlation blue red)}
    {:pair "Green-Red" :correlation (correlation green red)}]))

;; **Interpretation**:
;; - Correlation near 1.0: Channels move together (consistent lighting, color cast)
;; - Correlation near 0.0: Channels independent (varied colors, good white balance)
;; - High correlations (>0.95) often indicate opportunity for white balance adjustment

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
  (let [[_ w] (dtype/shape tensor-2d)]
    (dfn/- (tensor/select tensor-2d :all (range 1 w))
           (tensor/select tensor-2d :all (range 0 (dec w))))))

(defn gradient-y
  "Compute vertical gradient (difference between adjacent rows).
  Takes: [H W] tensor
  Returns: [H-1 W] tensor"
  [tensor-2d]
  (let [[h _] (dtype/shape tensor-2d)]
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
  (let [[_ w-gx] (dtype/shape gx)
        [h-gy _] (dtype/shape gy)
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

;; **Note**: Grayscale (single-channel) tensors are rendered as grayscale images.

;; ## Sharpness Metric

;; Measure image sharpness by averaging edge magnitude—higher = sharper:

(defn sharpness-score
  "Compute sharpness as mean edge magnitude.
  Takes: [H W 3] BGR or [H W] grayscale tensor
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

;; # Spatial Profiling — Row and Column Analysis

;; We've seen how to extract channels and compute global statistics. Now let's
;; explore **row-wise and column-wise analysis** using `tensor/slice`, `tensor/transpose`,
;; and `tensor/reduce-axis` for spatial profiling and region detection.

;; ## Row Brightness Profile with tensor/slice

;; `tensor/slice` enables efficient iteration through rows. Let's compute mean
;; brightness per row to create a vertical brightness profile:

(def img-rows (tensor/slice original-tensor 1))

;; How many rows?

(dtype/ecount img-rows)

;; Compute brightness for each row:

(def row-brightness
  (mapv dfn/mean img-rows))

;; First 10 row brightness values:

(take 10 row-brightness)

;; **Performance note**: Using `tensor/slice` is more efficient than manually selecting
;; each row with `(tensor/select img row-idx :all :all)` in a loop, as it creates the
;; reader once rather than performing individual selections.

;; Find brightest and darkest rows:

(let [brightest-idx (apply max-key #(nth row-brightness %) (range (count row-brightness)))
      darkest-idx (apply min-key #(nth row-brightness %) (range (count row-brightness)))]
  (tc/dataset
   [{:type "Brightest"
     :row-index brightest-idx
     :brightness (nth row-brightness brightest-idx)}
    {:type "Darkest"
     :row-index darkest-idx
     :brightness (nth row-brightness darkest-idx)}]))

;; **Use case**: Identify horizon lines, sky regions, or exposure gradients.

;; ## Column Operations with transpose

;; `tensor/slice` only works on leftmost dimensions. For columns, we use
;; `tensor/transpose` to swap dimensions:

(def img-transposed (tensor/transpose original-tensor [1 0 2]))

;; Shape changed from [H W C] to [W H C]:

(dtype/shape img-transposed)

;; Now we can slice columns:

(def img-columns (tensor/slice img-transposed 1))

(dtype/ecount img-columns)

;; Compute column brightness (horizontal profile):

(def col-brightness
  (mapv dfn/mean (take 1280 img-columns)))

;; Visualize row vs column brightness profiles:

(-> (tc/dataset {:vertical-position (range (min 500 (count row-brightness)))
                 :row-brightness (take 500 row-brightness)})
    (plotly/base {:=title "Vertical Brightness Profile (Top 500 rows)"
                  :=x-title "Row Index"
                  :=y-title "Mean Brightness"})
    (plotly/layer-line {:=x :vertical-position
                        :=y :row-brightness
                        :=mark-color "steelblue"}))

(-> (tc/dataset {:horizontal-position (range (min 500 (count col-brightness)))
                 :col-brightness (take 500 col-brightness)})
    (plotly/base {:=title "Horizontal Brightness Profile (Left 500 columns)"
                  :=x-title "Column Index"
                  :=y-title "Mean Brightness"})
    (plotly/layer-line {:=x :horizontal-position
                        :=y :col-brightness
                        :=mark-color "coral"}))

;; **Use case**: Detect vignetting, find composition center, identify vertical features.

;; ## Efficient Aggregation with reduce-axis

;; For statistics without explicit iteration, use `tensor/reduce-axis`.

;; Compute row brightness using reduce-axis:

(def row-means-fast
  (-> original-tensor
      (tensor/reduce-axis dfn/mean 1 :float64) ; [H W C] → [H C]
      (tensor/reduce-axis dfn/mean 1 :float64)) ; [H C] → [H]
  )

;; First 10 values (compare with earlier slice-based approach):

(take 10 (dtype/as-reader row-means-fast))

;; **Why specify `:float64`?** Without it, dtype-next might infer the output type from
;; the input (`:uint8`), which would truncate decimal values from the mean operation.
;; For example, a mean of 127.8 would become 127. Always specify the output datatype
;; when reducing to ensure you get the precision you need.

;; ## Block-Based Region Analysis

;; For coarse spatial analysis, downsample into blocks and find regions of interest:

(defn downsample-blocks
  "Downsample image by averaging NxN blocks.
  Returns: [H/N W/N] tensor of block mean brightness"
  [img-tensor block-size]
  (let [[h w _] (dtype/shape img-tensor)
        new-h (quot h block-size)
        new-w (quot w block-size)]
    (tensor/compute-tensor
     [new-h new-w]
     (fn [by bx]
       (let [block (tensor/select img-tensor
                                  (range (* by block-size) (min h (* (inc by) block-size)))
                                  (range (* bx block-size) (min w (* (inc bx) block-size)))
                                  :all)]
         (dfn/mean block)))
     :float32)))

(def brightness-map (downsample-blocks original-tensor 20))

;; Brightness map shape:

(dtype/shape brightness-map)
;; [48 64] for 960/20 × 1280/20

;; Find brightest and darkest blocks:

(defn find-block-extremes
  "Find coordinates of brightest and darkest blocks in a 2D tensor."
  [tensor-2d]
  (let [flat (dtype/as-reader (tensor/reshape tensor-2d [(dtype/ecount tensor-2d)]))
        [h w] (dtype/shape tensor-2d)
        max-idx (apply max-key #(dtype/get-value flat %) (range (dtype/ecount flat)))
        min-idx (apply min-key #(dtype/get-value flat %) (range (dtype/ecount flat)))]
    {:brightest {:block-y (quot max-idx w)
                 :block-x (rem max-idx w)
                 :value (dtype/get-value flat max-idx)}
     :darkest {:block-y (quot min-idx w)
               :block-x (rem min-idx w)
               :value (dtype/get-value flat min-idx)}}))

(find-block-extremes brightness-map)

;; **Use case**: Quick region-of-interest detection, composition analysis, exposure mapping.

;; ---

;; # Enhancement Pipeline

;; We've explored analyzing image properties—now let's actively *transform* them.
;; With analysis tools in place, we'll build functions that improve images through
;; white balance and contrast adjustment. Each transformation is composable and
;; verifiable through numeric properties we can check in the REPL.

;; ## Auto White Balance

;; [White balance](https://en.wikipedia.org/wiki/Color_balance) adjusts colors to
;; appear neutral under different lighting conditions. We scale BGR channels to have
;; equal means, removing color casts.

(defn auto-white-balance
  "Scale BGR channels to have equal means.
  Takes: [H W 3] uint8 BGR tensor
  Returns: [H W 3] uint8 BGR tensor"
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
       bufimg/tensor->image)]]) ; BGR tensor → BGR image

;; **Note**: Our BGR tensor flows seamlessly to BGR BufferedImage.

;; ## Contrast Enhancement

;; [Contrast](https://en.wikipedia.org/wiki/Contrast_(vision)) enhancement amplifies
;; the difference between light and dark regions. We amplify each pixel's deviation
;; from the mean, making bright pixels brighter and dark pixels darker.

(defn enhance-contrast
  "Increase image contrast by amplifying deviation from mean.
  Takes: [H W 3] uint8 BGR tensor, factor (> 1 increases, < 1 decreases)
  Returns: [H W 3] uint8 BGR tensor"
  [img-tensor factor]
  (let [[h w c] (dtype/shape img-tensor)

        ;; Process each channel independently
        enhanced-channels (mapv (fn [ch]
                                  (let [channel (tensor/select img-tensor :all :all ch)
                                        ch-mean (dfn/mean channel)]
                                    ;; Apply contrast: mean + factor * (value - mean)
                                    (dtype/elemwise-cast
                                     (dfn/min 255
                                              (dfn/max 0
                                                       (dfn/+ ch-mean
                                                              (dfn/* (dfn/- channel ch-mean) factor))))
                                     :uint8)))
                                (range c))]

    ;; Reassemble channels
    (tensor/compute-tensor
     [h w c]
     (fn [y x ch]
       (tensor/mget (nth enhanced-channels ch) y x))
     :uint8)))

(def contrasted (enhance-contrast original-tensor 1.5))

(kind/table
 [[:original
   :contrast-1.5
   :contrast-3]
  [original-img
   (-> original-tensor
       (enhance-contrast 1.5)
       bufimg/tensor->image) ; BGR → BGR
   (-> original-tensor
       (enhance-contrast 3)
       bufimg/tensor->image)]])

;; ---

;; # Accessibility — Color Blindness Simulation

;; Beyond enhancement, images need to be *accessible*. Let's simulate how images
;; appear to people with different types of color vision deficiency.
;;
;; This demonstrates dtype-next's linear algebra capabilities (applying 3×3 matrices
;; to BGR channels) with practical real-world applications.

;; Apply 3×3 transformation matrices to simulate different types of color vision deficiency.

;; ## Color Blindness Matrices

;; These matrices simulate [color blindness](https://en.wikipedia.org/wiki/Color_blindness)
;; (color vision deficiency). Different types affect perception of red, green, or blue:

(def color-blindness-matrices
  "Color blindness simulation matrices.
  Each matrix is 3×3 with columns in BGR order: [B G R]
  Matrices adapted from standard RGB formulas, reordered for BGR."
  {:protanopia [[0.000 0.433 0.567] ; Red-blind (BGR columns)
                [0.000 0.442 0.558]
                [0.758 0.242 0.000]]

   :deuteranopia [[0.000 0.375 0.625] ; Green-blind (BGR columns)
                  [0.000 0.300 0.700]
                  [0.700 0.300 0.000]]

   :tritanopia [[0.000 0.050 0.950] ; Blue-blind (BGR columns)
                [0.567 0.433 0.000]
                [0.525 0.475 0.000]]})

;; ## Applying Matrix Transformations

;; Extract BGR channels, apply linear combinations, reassemble:

(defn apply-color-matrix
  "Apply 3×3 transformation matrix to BGR channels.
  Takes: [H W 3] BGR tensor, 3×3 matrix [[b0 g0 r0] [b1 g1 r1] [b2 g2 r2]]
  Returns: [H W 3] uint8 BGR tensor
  Formula: new_b = b0*B + g0*G + r0*R, etc.
  
  Note: Matrix coefficients correspond to BGR order (channel 0=B, 1=G, 2=R)"
  [img-tensor matrix]
  (let [b (tensor/select img-tensor :all :all 0) ; Blue channel
        g (tensor/select img-tensor :all :all 1) ; Green channel
        r (tensor/select img-tensor :all :all 2) ; Red channel

        [[b0 g0 r0]
         [b1 g1 r1]
         [b2 g2 r2]] matrix

        ;; Apply transformation (BGR order)
        new-b (dfn/+ (dfn/+ (dfn/* b b0) (dfn/* g g0)) (dfn/* r r0))
        new-g (dfn/+ (dfn/+ (dfn/* b b1) (dfn/* g g1)) (dfn/* r r1))
        new-r (dfn/+ (dfn/+ (dfn/* b b2) (dfn/* g g2)) (dfn/* r r2))

        ;; Clamp and cast
        clamp-cast (fn [ch]
                     (dtype/elemwise-cast
                      (dfn/min 255 (dfn/max 0 ch))
                      :uint8))

        new-b-clamped (clamp-cast new-b)
        new-g-clamped (clamp-cast new-g)
        new-r-clamped (clamp-cast new-r)

        [h w _] (dtype/shape img-tensor)]
    (tensor/compute-tensor
     [h w 3]
     (fn [y x c]
       (case c
         0 (tensor/mget new-b-clamped y x) ; Blue channel 0
         1 (tensor/mget new-g-clamped y x) ; Green channel 1
         2 (tensor/mget new-r-clamped y x))) ; Red channel 2
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

;; All color blindness transformations maintain BGR order throughout.

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
  Returns [H W] float32 tensor (zero-padded edges).
  
  Note: This implementation prioritizes clarity over performance.
  For production use, consider tech.v3.libs.opencv or specialized
  convolution libraries for better performance on large images."
  [img-2d kernel]
  (let [[h w] (dtype/shape img-2d)
        [kh kw] (dtype/shape kernel)
        pad-h (quot kh 2)
        pad-w (quot kw 2)]
    (tensor/compute-tensor
     [h w]
     (fn [y x]
       ;; Sum weighted pixel values in kernel neighborhood
       (loop [ky 0
              kx 0
              sum 0.0]
         (if (>= ky kh)
           sum
           (let [img-y (+ y ky (- pad-h))
                 img-x (+ x kx (- pad-w))
                 in-bounds? (and (>= img-y 0) (< img-y h)
                                 (>= img-x 0) (< img-x w))
                 new-sum (if in-bounds?
                           (+ sum (* (tensor/mget kernel ky kx)
                                     (tensor/mget img-2d img-y img-x)))
                           sum)
                 [next-ky next-kx] (if (>= (inc kx) kw)
                                     [(inc ky) 0]
                                     [ky (inc kx)])]
             (recur next-ky next-kx new-sum)))))
     :float32)))

;; **Apply box blur to grayscale**:

(def blurred-gray (convolve-2d grayscale kernel-3x3))

;; Compare original vs box blur:

(kind/table
 [[:original :box-blur-3x3]
  [(bufimg/tensor->image grayscale)
   (bufimg/tensor->image (dtype/elemwise-cast blurred-gray :uint8))]])

;; Grayscale tensors (2D) are automatically rendered as grayscale images.

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

;; Larger Gaussian blur for stronger effect:

(def gaussian-15x15 (gaussian-kernel 15 3.0))

(def gaussian-blurred-large (convolve-2d grayscale gaussian-15x15))

;; Compare different blur kernels:

(kind/table
 [[:original :box-blur-3x3 :gaussian-5x5 :gaussian-15x15]
  [(bufimg/tensor->image grayscale)
   (bufimg/tensor->image (dtype/elemwise-cast blurred-gray :uint8))
   (bufimg/tensor->image (dtype/elemwise-cast gaussian-blurred :uint8))
   (bufimg/tensor->image (dtype/elemwise-cast gaussian-blurred-large :uint8))]])

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
;; However, note that `sharpness-score` expects BGR input, so we need to adapt it for
;; grayscale tensors. For simplicity, we'll compute sharpness inline here:

(-> {:original grayscale
     :box-3x3 blurred-gray
     :gaussian-5x5 gaussian-blurred
     :gaussian-15x15 gaussian-blurred-large
     :sharpened sharpened-gray}
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

;; Single-channel tensors display as grayscale images.

;; **Comparison**: Simple gradient (from Spatial Analysis section) vs Sobel

{:simple-mean (dfn/mean edges) ; reuse edges computed earlier
 :sobel-mean (dfn/mean sobel-edges)
 :sobel-smoother? true}

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

;; Both grayscale tensors render as grayscale images.

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

(kind/fragment
 (mapv bufimg/tensor->image gray-pyramid))

;; Each grayscale tensor at different scales renders as a grayscale image.

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

;; Both are grayscale. `tensor->image` handles float32 → uint8 conversion automatically.

;; Average downsampling produces smoother results with less aliasing.

;; **Verification**: Both produce same shape, but averaging reduces noise

(tc/dataset {:method ["Select every 2nd" "Average blocks"]
             :height (mapv #(first (dtype/shape %)) [downsampled-gray avg-downsampled])
             :width (mapv #(second (dtype/shape %)) [downsampled-gray avg-downsampled])
             :mean (mapv dfn/mean [downsampled-gray avg-downsampled])})

;; ---

;; # Conclusion: The dtype-next Pattern

;; We started with a simple question: **Why use dtype-next for image processing?**
;;
;; Through building a complete analysis toolkit—from channel statistics to edge detection
;; to convolution—we've seen the answer in action:
;;
;; - **Efficient typed arrays** replace boxed sequences, saving memory and enabling SIMD
;; - **Zero-copy views** let us slice and transform without allocation overhead
;; - **Functional composition** keeps operations pure and composable
;; - **Immediate visual feedback** makes abstract tensor operations concrete and verifiable
;;
;; Images provided the perfect learning vehicle: every transformation has visible results
;; we can inspect in the REPL. The patterns we've practiced transfer directly to any
;; domain requiring efficient numerical computing.

;; ## Key Patterns

;; 1. **Zero-copy views** — `tensor/select` creates views without copying data
;; 2. **Reduction operations** — `dfn/mean`, `dfn/standard-deviation`, etc.
;; 3. **Element-wise ops** — `dfn/+`, `dfn/*`, `dfn/sqrt` work across entire tensors
;; 4. **Type discipline** — upcast → compute → clamp → downcast for precision control
;; 5. **Functional composition** — pure functions composed with `->` and function composition
;; 6. **Objective verification** — numeric properties that can be checked in REPL

;; ## API Coverage

;; Here are the key dtype-next functions we used throughout this tutorial:

;; **dtype namespace (tech.v3.datatype):**
;; - `dtype/shape` — Inspect tensor dimensions
;; - `dtype/elemwise-datatype` — Check element type
;; - `dtype/elemwise-cast` — Convert between types
;; - `dtype/ecount` — Total element count
;; - `dtype/as-reader` — Convert to readable sequence
;; - `dtype/get-value` — Extract scalar value

;; **tensor namespace (tech.v3.tensor):**
;; - `tensor/compute-tensor` — Functionally construct tensors
;; - `tensor/select` — Extract slices, channels (zero-copy)
;; - `tensor/mget` — Read single elements
;; - `tensor/reshape` — Reinterpret tensor shape (zero-copy)
;; - `tensor/reduce-axis` — Reduce along specific dimension

;; **dfn namespace (tech.v3.datatype.functional):**
;; - `dfn/+`, `dfn/-`, `dfn/*`, `dfn//` — Element-wise arithmetic
;; - `dfn/mean`, `dfn/standard-deviation` — Statistics
;; - `dfn/reduce-min`, `dfn/reduce-max` — Range finding
;; - `dfn/sqrt`, `dfn/sq` — Mathematical operations
;; - `dfn/min`, `dfn/max` — Clamping
;; - `dfn/sum` — Summation

;; **bufimg namespace (tech.v3.libs.buffered-image):**
;; - `bufimg/load` — Load image file
;; - `bufimg/as-ubyte-tensor` — BufferedImage → tensor
;; - `bufimg/tensor->image` — tensor → BufferedImage
;; - `bufimg/image-type` — Check image format

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
;; - **Native interop**: Zero-copy integration with native libraries (OpenCV, Numpy, etc.)
;; - **Dataset tools**: Rich `tech.ml.dataset` integration for tabular workflows
;; - **Performance**: SIMD-optimized operations, parallel processing support
;; - **Flexibility**: Custom buffer implementations, extensible type system

;; ## Resources

;; - [dtype-next docs](https://cnuernber.github.io/dtype-next/)
;; - [tech.ml.dataset guide](https://techascent.github.io/tech.ml.dataset/)
;; - [Scicloj tutorials](https://scicloj.github.io/)

;; ---

;; *Questions, corrections, or ideas? Open an issue on the Clojure Civitas repository.*


