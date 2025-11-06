^{:kindly/hide-code true
  :clay             {:title  "Horizontals"
                     :quarto {:author      :generateme
                              :description "2D Geometric structures"
                              :category    :clojure
                              :type        :post
                              :date        "2025-11-05"
                              :tags        [:generative :art :math :clojure2d :rendering]}}}
(ns generative-art.horizontals.horizontals
  (:require [scicloj.kindly.v4.kind :as kind]))

;; ![](result1.jpg)

;; ---

^{:kind/hidden true :kindly/hide-code true}
(do
  (set! *warn-on-reflection* true)
  (set! *unchecked-math* :warn-on-boxed))

(require '[fastmath.core :as m]
         '[fastmath.fields :as f]
         '[fastmath.vector :as v]
         '[fastmath.random :as r]
         '[fastmath.signal :as s]
         '[clojure2d.core :as c2d]
         '[clojure2d.color :as c]
         '[clojure2d.pixels :as p]
         '[clojure2d.extra.utils :as utls]
         '[clojure2d.extra.overlays :as ovrls]
         '[clojure2d.extra.signal :as sig])

(import '[fastmath.vector Vec2])

;; # The idea

;; This is my generative and math art project from 2023. The main idea is to draw horizontal lines which length is determinded by underlined vector field. Lenght of the line is calculated from magnitude, color is taken from discrete palette and based on angle of the resulting field vector. There is also some postprocessing to add some spice to the result.

;; # Vector fields

;; The basis for our work is any 2D to 2D function which I will call vector fields (actually some of them are not vector fields from math or physics point of view, but...). `fastmath.fields` defines almost 400 of them, most of them can be parametrized. For example (I'm skipping the formula, which is for our case not important):

(def collideoscope (f/field :collideoscope))

(collideoscope (v/vec2 1 2))

;; `collideoscope` field can be parametrized, `f/parametrization` generates random config for given field.

(f/parametrization :collideoscope)

;; To take full control over given vector field, we can create it with custom parametrization. By default it's random. Second argument controls something called `amount`, usually it's just a scaling factor for the result (default is `1.0`).

(def collideoscope-1 (f/field :collideoscope 1.0 {:a 0.532115242771691, :num -8}))

(collideoscope-1 (v/vec2 1 2))

;; Let's see how collideoscope looks like, we will draw transformed square $[-3,-3]\times[3,3]$.

(defn draw-field
  ([field] (draw-field field 4.0))
  ([field ^double scale]
   (let [scale- (m/- scale)]
     (c2d/with-canvas [c (c2d/canvas 400 400)]
       (c2d/set-background c :black)
       (c2d/set-color c (c/gray 200 50))
       (dotimes [_ 250000]
         (let [x (r/drand -3.0 3.0)
               y (r/drand -3.0 3.0)
               vin (v/vec2 x y)
               ^Vec2 vout (field vin)
               xout (m/norm (.x vout) scale- scale 0.0 400.0)
               yout (m/norm (.y vout) scale- scale 0.0 400.0)]
           (c2d/point c xout yout)))
       (c2d/to-image c)))))

(kind/table
 [["Random collideoscope" "collideoscope-1"]
  [(draw-field collideoscope)
   (draw-field collideoscope-1)]])

;; ## Random vector field

;; For our purpose we will reach for random field with random configuration. For that we will use `f/combine` which combine randomly selected in various ways (for example by adding them or composing). We need as much variety as we can.

;; Here is an example of such field.

(f/random-configuration)

;; I'll use some prepared configs by me here (some of randomly selected fields are just pure noise)

(def cfg1 {:type :operation, :name :comp, :var1 {:type :variation, :name :log, :amount 1.0, :config {}}, :var2 {:type :variation, :name :jacelk, :amount 1.0, :config {:k 0.09735248378321981}}, :amount 1.0})

(def cfg2 {:type :operation, :name :add, :var1 {:type :variation, :name :truchetfill, :amount 1.4281670374599877, :config {:pexponent 0.447236341437409, :arc-width 0.945881107473416, :seed 254.95482819972815}}, :var2 {:type :variation, :name :truchethexcrop, :amount -0.848869589411096, :config {:seed 1450236720, :inv false, :mode 2, :wd 0.3167790772641451}}, :amount 0.4391672879562406})

(def cfg3 {:type :operation, :name :comp, :var1 {:type :variation, :name :curl, :amount 1.0, :config {:c1 0.9862329229741469, :c2 -0.8549787205715997}}, :var2 {:type :variation, :name :truchethexcrop, :amount 1.0, :config {:seed -1657738434, :inv false, :mode 2, :wd 0.0837947808619492}}, :amount 1.0})

(kind/table
 [[(draw-field (f/combine cfg1))
   (draw-field (f/combine cfg2))
   (draw-field (f/combine cfg3))]])

;; Some of the fields are purely random function, we can remove such from our selection by binding `*skip-random-fields*` dynamic var to `true`.

;; # Step 1 - PoC

;; Let's make the first proof of concept using previously defined collideoscope field.
;; We traverse the vector field along `y` axis and calculate what should be a `step` along `x` axis. The `step` variable controls also line length which is drawn on a canvas. `step` is calculated from a magnitude of the resulting vector mapped to range between `0.5` and `1.0`. This way we can build horizontal stripes build from line segments.

;; For colors we follow similar procedure but we rely on the vector angle rather than its length. Angle is taken from the position where line starts.

(defn horizontals-1
  ([field] (horizontals-1 field 400))
  ([field ^long size]
   (let [field-domain-min -3.0
         field-domain-max 3.0]
     (c2d/with-canvas [canv (c2d/canvas size size)]
       (c2d/set-background canv (c/gray 240))
       (doseq [screen-y (range size)
               :let [field-coord-y (m/norm screen-y 0 size field-domain-min field-domain-max)
                     step (-> (v/vec2 field-domain-min field-coord-y) ;; take a point from the left border
                              (field) ;; apply vector field
                              (v/mag) ;; calculate vector length
                              (m/sin) ;; apply sin to wrap length between -1.0 and 1.0 
                              (m/sq) ;; square it to get values from 0.0 to 1.0
                              (m/inc) ;; increment to get values from 1.0 to 2.0
                              (m/* 0.5))]] ;; take half to get values from 0.5 to 1.0
         (loop [line-start field-domain-min]
           (when (m/< line-start field-domain-max)
             (let [line-end (m/+ line-start step)
                   screen-line-start (m/norm line-start field-domain-min field-domain-max 0 size)
                   screen-line-end (m/norm line-end field-domain-min field-domain-max 0 size)
                   color (-> (v/vec2 line-start field-coord-y) ;; position of the line beginning
                             (field) ;; apply vector field
                             (v/heading) ;; calculate vector angle
                             (m/sin) ;; apply sin to wrap angle between -1.0 and 1.0
                             (m/sq) ;; square it to get values from 0.0 to 1.0
                             (m/* 255.0) ;; multiply to get RGB values
                             (c/gray))]  ;; convert to actual gray color
               (c2d/set-color canv color)
               (c2d/line canv screen-line-start screen-y screen-line-end screen-y)
               (recur line-end)))))
       (c2d/to-image canv)))))

(horizontals-1 collideoscope-1)

;; # Step 2 - Discrete color palette

;; Now we want to introduce some colors, instead of continuous grayscale we will use discrete colors from some predefined [palette](https://clojure2d.github.io/clojure2d/docs/static/palettes/COLOURlovers.html).

(-> (c/palette 0) utls/palette->image (c2d/resize 600 100))

(defn horizontals-2
  ([field] (horizontals-2 field 400))
  ([field ^long size]
   (let [palette (c/palette 0)
         palette-last-index (m/dec (count palette))
         field-domain-min -3.0
         field-domain-max 3.0]
     (c2d/with-canvas [canv (c2d/canvas size size)]
       (c2d/set-background canv (c/gray 240))
       (doseq [screen-y (range size)
               :let [field-coord-y (m/norm screen-y 0 size field-domain-min field-domain-max)
                     step (-> (v/vec2 field-domain-min field-coord-y) ;; take a point from the left border
                              (field) ;; apply vector field
                              (v/mag) ;; calculate vector length
                              (m/sin) ;; apply sin to wrap length between -1.0 and 1.0 
                              (m/sq) ;; square it to get values from 0.0 to 1.0
                              (m/inc) ;; increment to get values from 1.0 to 2.0
                              (m/* 0.5))]] ;; take half to get values from 0.5 to 1.0
         (loop [line-start field-domain-min]
           (when (m/< line-start field-domain-max) ;; end when outside a field
             (let [line-end (m/+ line-start step) ;; find line endpoint
                   screen-line-start (m/norm line-start field-domain-min field-domain-max 0 size)
                   screen-line-end (m/norm line-end field-domain-min field-domain-max 0 size)
                   color (-> (v/vec2 line-start field-coord-y) ;; position of the line beginning
                             (field) ;; apply vector field
                             (v/heading) ;; calculate vector angle
                             (m/sin) ;; apply sin to wrap angle between -1.0 and 1.0
                             (m/norm -1.0 1.0 0 palette-last-index) ;; map to a palette size
                             (m/round) ;; snap to palette index (we need an integer)
                             (palette))] ;; get color
               (c2d/set-color canv color)
               (c2d/line canv screen-line-start screen-y screen-line-end screen-y)
               (recur line-end)))))
       (c2d/to-image canv)))))

(horizontals-2 collideoscope-1)

;; # Step 3 - Randomization

;; So the last element is randomization of some parameters

;; * `field-domain-min` and `field-domain-max` - select randomly from `-12` to `-1`
;; * generate random palette, by just calling `c/palette` function
;; * select field randomly by calling `f/combine` on the result of `(f/random-configuration)`
;; * step range scaling can be random as well

;; For that we will create another function which randomly builds config for our system.

(defn random-configuration
  ([] (random-configuration {}))
  ([custom-config]
   (binding [f/*skip-random-fields* true]
     (let [field-domain-min (r/drand -12.0 -1.0)]
       (merge {:field-domain-min field-domain-min
               :field-domain-max (m/- field-domain-min)
               :palette (c/palette)
               :field-config (f/random-configuration)
               :step-scale (r/drand 0.2 1.5)}
              custom-config)))))

;; We want to construct our function to accept also prepared `field` instead of `field-config`

(defn horizontals-3
  ([] (horizontals-3 (random-configuration)))
  ([config] (horizontals-3 config 400))
  ([{:keys [^double field-domain-min
            ^double field-domain-max
            palette
            field-config field
            ^double step-scale]} ^long size]
   (let [field (or field (f/combine field-config))
         palette-last-index (m/dec (count palette))]
     (c2d/with-canvas [canv (c2d/canvas size size)]
       (c2d/set-background canv (c/gray 240))
       (doseq [screen-y (range size)
               :let [field-coord-y (m/norm screen-y 0 size field-domain-min field-domain-max)
                     step (-> (v/vec2 field-domain-min field-coord-y) ;; take a point from the left border
                              (field) ;; apply vector field
                              (v/mag) ;; calculate vector length
                              (m/sin) ;; apply sin to wrap length between -1.0 and 1.0 
                              (m/sq) ;; square it to get values from 0.0 to 1.0
                              (m/inc) ;; increment to get values from 1.0 to 2.0
                              (m/* step-scale))]] ;; take half to get values from 0.5 to 1.0
         (loop [line-start field-domain-min]
           (when (m/< line-start field-domain-max) ;; end when outside a field
             (let [line-end (m/+ line-start step) ;; find line endpoint
                   screen-line-start (m/norm line-start field-domain-min field-domain-max 0 size)
                   screen-line-end (m/norm line-end field-domain-min field-domain-max 0 size)
                   color (-> (v/vec2 line-start field-coord-y) ;; position of the line beginning
                             (field)     ;; apply vector field
                             (v/heading) ;; calculate vector angle
                             (m/sin) ;; apply sin to wrap angle between -1.0 and 1.0
                             (m/norm -1.0 1.0 0 palette-last-index) ;; map to a palette size
                             (m/round) ;; snap to palette index (we need an integer)
                             (palette))] ;; get color
               (c2d/set-color canv color)
               (c2d/line canv screen-line-start screen-y screen-line-end screen-y)
               (recur line-end)))))
       (c2d/to-image canv)))))

;; Let's set `collideoscope-1` as our field temporarily.

(kind/table
 [[(horizontals-3 (random-configuration {:field collideoscope-1}))
   (horizontals-3 (random-configuration {:field collideoscope-1}))
   (horizontals-3 (random-configuration {:field collideoscope-1}))]])

;; And the result for previously configured fields.

(kind/table
 [[(horizontals-3 (random-configuration {:field-config cfg1}))
   (horizontals-3 (random-configuration {:field-config cfg2}))
   (horizontals-3 (random-configuration {:field-config cfg3}))]])

;; # Step 4 - Postprocessing

;; We want to add something to finish our work. Clojure2d allows to filter pixels or apply overlay to make result more interesting. We experiment here with some analog touch, noise overlay blending, CRT scanlines etc.

(def horizontals-config {:field-domain-min -1.8
                       :field-domain-max 1.8
                       :step-scale 0.85
                       :palette [[213.0 62.0 79.0 255.0]
                                 [158.0 1.0 66.0 255.0]
                                 [244.0 109.0 67.0 255.0]
                                 [253.0 174.0 97.0 255.0]
                                 [254.0 224.0 139.0 255.0]
                                 [230.0 245.0 152.0 255.0]
                                 [171.0 221.0 164.0 255.0]
                                 [102.0 194.0 165.0 255.0]
                                 [50.0 136.0 189.0 255.0]
                                 [94.0 79.0 162.0 255.0]]
                       :field collideoscope-1})

(def horizontals-image (horizontals-3 horizontals-config))

horizontals-image

;; ## Noise

;; The first is a gaussian noise overlayed on the image. Smaller noise image will be resized later which gives paper-like canvas. `:alpha` parameter controls alpha channel.

(def noise-300 (ovrls/noise-overlay 300 300 {:alpha 60}))

noise-300

(ovrls/render-noise horizontals-image noise-300)

;; ## RGB scanlines

;; The other option is to add RGB scanlines. The effect simulates look of the CRT screen.

(ovrls/render-rgb-scanlines horizontals-image)

;; ## Signal processing

;; The last method is to treat our image as a signal and process it through some audio filter. We will use biquadratic low-pass filter, which will blur edges horizontally and will creates a look like from an analog TV. 

;; First of all we want to convert our image to a collection of pixels. By pixels I understand arrays of color space channels (RGBA by default). `fastmath.pixels` contains functions which operate on pixels directly, like apply functions to colors (`p/filter-colors`) and color channels (`p/filter-channels`).

;; Second, we want to operate on analog TV color space. It can be any of luma based but our choice is a [YPbPr](https://en.wikipedia.org/wiki/YPbPr) which is used in *component video*.

;; Third, we need to convert our pixels into a signal, ie. linear array containing raw planar data, 8-bit signed. We want also, retrigger our filter each line. This is done by `sig/effects-filter` function from `clojure2d.extra.signal` which takes care of of all of the process (converting pixels to signal, apply filter, and converting back to pixels)

;; Digital audio filters are defined in `fastmath.signal` namespace. The result of the `s/effect` is an object which keeps filter state and operates on single sample. Effects can be composed with `s/compose-effects`.

;; Let's define a filter.
;; `:biquad-lp` accepts a rate (`:fs`), cutoff (`:fc`) and bandwidth (`:bw`). Let's set cutoff to 10%. 

(def biquad-lp (s/effect :biquad-lp {:fs 10000 :fc 1000 :bw 1}))

(biquad-lp 0.5)

;; So let's combine all together:

(->> (p/to-pixels horizontals-image) ;; convert image to pixels
     (p/filter-colors c/to-YPbPr*) ;; convert colorspace from sRGB to YPbPr (with values from 0-255)
     (p/filter-channels (sig/effects-filter biquad-lp 400)) ;; apply biquad-lp filter, retrigger each line
     (p/filter-colors c/from-YPbPr*) ;; convert back to sRGB
     (c2d/to-image))

;; # All together

;; Let's create our system which every call will generate randomly an image with postprocessing

(defn horizontals
  ([] (horizontals 400))
  ([^long image-size]
   (let [noise-size (m/round (m/* image-size 0.9))
         noise (ovrls/noise-overlay noise-size noise-size {:alpha 60})]
     (->> (horizontals-3 (random-configuration) image-size)
          (p/to-pixels)
          (p/filter-colors c/to-YPbPr*)
          (p/filter-channels (sig/effects-filter biquad-lp image-size))
          (p/filter-colors c/from-YPbPr*)
          (c2d/to-image)
          (ovrls/render-noise noise)
          (ovrls/render-rgb-scanlines)))))

;; Here is a result of several call to `horizontals`

;; ![](result1.jpg)
;; ![](result2.jpg)
;; ![](result3.jpg)
;; ![](result4.jpg)
;; ![](result5.jpg)
;; ![](result6.jpg)
;; ![](result7.jpg)
;; ![](result8.jpg)
;; ![](result9.jpg)

;; And finally full size image (1000x1000)

;; ![example](result_full.jpg)

;; Experiment yourself!

(comment
  (def h (horizontals 1000))
  (utls/show-image h)
  (c2d/save-image h "src/generative_art/horizontals/result_full.jpg"))
