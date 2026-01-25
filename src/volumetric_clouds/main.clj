^{:kindly/hide-code true
  :clay             {:title  "Volumetric Clouds with Clojure and LWJGL"
                     :external-requirements ["Xorg"]
                     :quarto {:author   [:janwedekind]
                              :draft    true
                              :description "Procedural generation of volumetric clouds using different types of noise"
                              :image    "clouds.jpg"
                              :type     :post
                              :date     "2026-01-24"
                              :category :clojure
                              :tags     [:visualization]}}}

(ns volumetric-clouds.main
    (:require [clojure.math :refer (PI sqrt tan to-radians pow)]
              [midje.sweet :refer (fact facts tabular => roughly)]
              [fastmath.vector :refer (vec2 vec3 add mult sub div mag dot normalize)]
              [fastmath.matrix :refer (mat->float-array mulm mulv inverse rotation-matrix-3d-x rotation-matrix-3d-y)]
              [tech.v3.datatype :as dtype]
              [tech.v3.tensor :as tensor]
              [tech.v3.datatype.functional :as dfn]
              [tablecloth.api :as tc]
              [scicloj.tableplot.v1.plotly :as plotly]
              [tech.v3.libs.buffered-image :as bufimg]
              [comb.template :as template])
    (:import [org.lwjgl.opengl GL11]
             [org.lwjgl BufferUtils]
             [org.lwjgl.glfw GLFW]
             [org.lwjgl.opengl GL GL11 GL12 GL13 GL15 GL20 GL30 GL32 GL42]))


;; # Procedural generation of volumetric clouds

;; ## Worley noise


(defn make-noise-params
  [size divisions dimensions]
  {:size size :divisions divisions :cellsize (/ size divisions) :dimensions dimensions})


(fact "Noise parameter initialisation"
      (make-noise-params 256 8 2) => {:size 256 :divisions 8 :cellsize 32 :dimensions 2})


(defn vec-n
  ([x y] (vec2 x y))
  ([x y z] (vec3 x y z)))


(facts "Generic vector function for creating 2D and 3D vectors"
       (vec-n 2 3) => (vec2 2 3)
       (vec-n 2 3 1) => (vec3 2 3 1))


(defn random-point-in-cell
  [{:keys [cellsize]} & args]
  (let [random-seq (repeatedly #(rand cellsize))
        dimensions (count args)]
    (add (mult (apply vec-n (reverse args)) cellsize)
         (apply vec-n (take dimensions random-seq)))))


(facts "Place random point in a cell"
       (with-redefs [rand (fn [s] (* 0.5 s))]
         (random-point-in-cell {:cellsize 1} 0 0) => (vec2 0.5 0.5)
         (random-point-in-cell {:cellsize 2} 0 0) => (vec2 1.0 1.0)
         (random-point-in-cell {:cellsize 2} 0 3) => (vec2 7.0 1.0)
         (random-point-in-cell {:cellsize 2} 2 0) => (vec2 1.0 5.0)
         (random-point-in-cell {:cellsize 2} 2 3 5) => (vec3 11.0 7.0 5.0)))


(defn random-points
  [{:keys [divisions dimensions] :as params}]
  (tensor/clone (tensor/compute-tensor (repeat dimensions divisions) (partial random-point-in-cell params))))


(facts "Greate grid of random points"
       (let [params-2d (make-noise-params 32 8 2)
             params-3d (make-noise-params 32 8 3)]
         (with-redefs [rand (fn [s] (* 0.5 s))]
           (dtype/shape (random-points params-2d)) => [8 8]
           ((random-points params-2d) 0 0) => (vec2 2.0 2.0)
           ((random-points params-2d) 0 3) => (vec2 14.0 2.0)
           ((random-points params-2d) 2 0) => (vec2 2.0 10.0)
           (dtype/shape (random-points params-3d)) => [8 8 8]
           ((random-points params-3d) 2 3 5) => (vec3 22.0 14.0 10.0))))


(let [points  (tensor/reshape (random-points (make-noise-params 256 8 2)) [(* 8 8)])
      scatter (tc/dataset {:x (map first points) :y (map second points)})]
  (-> scatter
      (plotly/base {:=title "Random points"})
      (plotly/layer-point {:=x :x :=y :y})))


(defn mod-vec
  [{:keys [size]} v]
  (let [size2 (/ size 2)
        wrap  (fn [x] (-> x (+ size2) (mod size) (- size2)))]
    (apply vec-n (map wrap v))))


(facts "Wrap around components of vector to be within -size/2..size/2"
       (mod-vec {:size 8} (vec2 2 3)) => (vec2 2 3)
       (mod-vec {:size 8} (vec2 5 2)) => (vec2 -3 2)
       (mod-vec {:size 8} (vec2 2 5)) => (vec2 2 -3)
       (mod-vec {:size 8} (vec2 -5 2)) => (vec2 3 2)
       (mod-vec {:size 8} (vec2 2 -5)) => (vec2 2 3)
       (mod-vec {:size 8} (vec3 2 3 1)) => (vec3 2 3 1)
       (mod-vec {:size 8} (vec3 5 2 1)) => (vec3 -3 2 1)
       (mod-vec {:size 8} (vec3 2 5 1)) => (vec3 2 -3 1)
       (mod-vec {:size 8} (vec3 2 3 5)) => (vec3 2 3 -3)
       (mod-vec {:size 8} (vec3 -5 2 1)) => (vec3 3 2 1)
       (mod-vec {:size 8} (vec3 2 -5 1)) => (vec3 2 3 1)
       (mod-vec {:size 8} (vec3 2 3 -5)) => (vec3 2 3 3))


(defn mod-dist
  [params a b]
  (mag (mod-vec params (sub b a))))


(tabular "Wrapped distance of two points"
         (fact (mod-dist {:size 8} (vec2 ?ax ?ay) (vec2 ?bx ?by)) => ?result)
         ?ax ?ay ?bx ?by ?result
         0   0   0   0   0.0
         0   0   2   0   2.0
         0   0   5   0   3.0
         0   0   0   2   2.0
         0   0   0   5   3.0
         2   0   0   0   2.0
         5   0   0   0   3.0
         0   2   0   0   2.0
         0   5   0   0   3.0)


(defn wrap-get
  [t & args]
  (if (> (count (dtype/shape t)) (count args))
    (apply tensor/select t (map mod args (dtype/shape t)))
    (apply t (map mod args (dtype/shape t)))))


(facts "Wrapped lookup of tensor values"
       (let [t (tensor/compute-tensor [4 6] vec2)]
         (wrap-get t 2 3) => (vec2 2 3)
         (wrap-get t 2 7) => (vec2 2 1)
         (wrap-get t 5 3) => (vec2 1 3)
         (wrap-get (wrap-get t 5) 3) => (vec2 1 3)))


(defn division-index
  [{:keys [cellsize]} x]
  (int (/ x cellsize)))


(facts "Convert coordinate to division index"
       (division-index {:cellsize 4} 3.5) => 0
       (division-index {:cellsize 4} 7.5) => 1)


(defn neighbours
  [& args]
  (if (seq args)
    (mapcat (fn [v] (map (fn [delta] (into [(+ (first args) delta)] v)) [-1 0 1])) (apply neighbours (rest args)) )
    [[]]))


(facts "Get neighbouring indices"
       (neighbours) => [[]]
       (neighbours 3) => [[2] [3] [4]]
       (neighbours 1 10) => [[0 9] [1 9] [2 9] [0 10] [1 10] [2 10] [0 11] [1 11] [2 11]])


(defn worley-noise
  [{:keys [size dimensions] :as params}]
  (let [random-points (random-points params)]
    (tensor/clone
      (tensor/compute-tensor (repeat dimensions size)
                             (fn [& coords]
                                 (let [center   (map #(+ % 0.5) coords)
                                       division (map (partial division-index params) center)]
                                   (apply min
                                          (for [neighbour (apply neighbours division)]
                                               (mod-dist params (apply vec-n (reverse center))
                                                         (apply wrap-get random-points neighbour))))))
                             :double))))


(def worley (worley-noise (make-noise-params 256 8 2)))

(def worley-norm (dfn/* (/ 255 (- (dfn/reduce-max worley) (dfn/reduce-min worley))) (dfn/- (dfn/reduce-max worley) worley)))

(bufimg/tensor->image worley-norm)

;; ## Perlin noise
;; https://adrianb.io/2014/08/09/perlinnoise.html

(defn random-gradient
  [& args]
  (loop [args args]
        (let [random-vector (apply vec-n (map (fn [_x] (- (rand 2.0) 1.0)) args))
              vector-length (mag random-vector)]
          (if (and (> vector-length 0.0) (<= vector-length 1.0))
            (div random-vector vector-length)
            (recur args)))))


(defn roughly-vec
  [expected error]
  (fn [actual]
      (<= (mag (sub actual expected)) error)))


(facts "Create unit vector with random direction"
       (with-redefs [rand (constantly 0.5)]
         (random-gradient 0 0) => (roughly-vec (vec2 (- (sqrt 0.5)) (- (sqrt 0.5))) 1e-6))
       (with-redefs [rand (constantly 1.5)]
         (random-gradient 0 0) => (roughly-vec (vec2 (sqrt 0.5) (sqrt 0.5)) 1e-6)))


(defn random-gradients
 [{:keys [divisions dimensions]}]
 (tensor/clone (tensor/compute-tensor (repeat dimensions divisions) random-gradient)))


(facts "Random gradients"
       (with-redefs [rand (constantly 1.5)]
         (dtype/shape (random-gradients {:divisions 8 :dimensions 2})) => [8 8]
         ((random-gradients {:divisions 8 :dimensions 2}) 0 0) => (roughly-vec (vec2 (sqrt 0.5) (sqrt 0.5)) 1e-6)
         (dtype/shape (random-gradients {:divisions 8 :dimensions 3})) => [8 8 8]
         ((random-gradients {:divisions 8 :dimensions 3}) 0 0 0) => (vec3 (/ 1 (sqrt 3)) (/ 1 (sqrt 3)) (/ 1 (sqrt 3)))))


(let [gradients (tensor/reshape (random-gradients (make-noise-params 256 8 2)) [(* 8 8)])
      points    (tensor/reshape (tensor/compute-tensor [8 8] (fn [y x] (vec2 x y))) [(* 8 8)])
      scatter   (tc/dataset {:x (mapcat (fn [point gradient] [(point 0) (+ (point 0) (* 0.5 (gradient 0))) nil]) points gradients)
                             :y (mapcat (fn [point gradient] [(point 1) (+ (point 1) (* 0.5 (gradient 1))) nil]) points gradients)})]
  (-> scatter
      (plotly/base {:=title "Random gradients" :=mode "lines"})
      (plotly/layer-point {:=x :x :=y :y})))


(defn frac
  [x]
  (- x (Math/floor x)))


(facts "Fractional part of floating point number"
       (frac 0.25) => 0.25
       (frac 1.75) => 0.75
       (frac -0.25) => 0.75)


(defn cell-pos
  [{:keys [cellsize]} point]
  (apply vec-n (map frac (div point cellsize))))


(facts "Relative position of point in a cell"
       (cell-pos {:cellsize 4} (vec2 2 3)) => (vec2 0.5 0.75)
       (cell-pos {:cellsize 4} (vec2 7 5)) => (vec2 0.75 0.25)
       (cell-pos {:cellsize 4} (vec3 7 5 2)) => (vec3 0.75 0.25 0.5))


(defn corner-vectors
  [{:keys [dimensions] :as params} point]
  (let [cell-pos (cell-pos params point)]
    (tensor/compute-tensor (repeat dimensions 2) (fn [& args] (sub cell-pos (apply vec-n (reverse args)))))))


(facts "Compute relative vectors from cell corners to point in cell"
       (let [v2 (corner-vectors {:cellsize 4 :dimensions 2} (vec2 7 6))
             v3 (corner-vectors {:cellsize 4 :dimensions 3} (vec3 7 6 5))]
         (v2 0 0) => (vec2 0.75 0.5)
         (v2 0 1) => (vec2 -0.25 0.5)
         (v2 1 0) => (vec2 0.75 -0.5)
         (v2 1 1) => (vec2 -0.25 -0.5)
         (v3 0 0 0) => (vec3 0.75 0.5 0.25)))


(defn corner-gradients
  [{:keys [dimensions] :as params} gradients point]
  (let [division (map (partial division-index params) point)]
    (tensor/compute-tensor (repeat dimensions 2) (fn [& coords] (apply wrap-get gradients (map + (reverse division) coords))))))


(facts "Get 2x2 tensor of gradients from a larger tensor using wrap around"
       (let [gradients2 (tensor/compute-tensor [4 6] (fn [y x] (vec2 x y)))
             gradients3 (tensor/compute-tensor [4 6 8] (fn [z y x] (vec3 x y z))) ]
         ((corner-gradients {:cellsize 4 :dimensions 2} gradients2 (vec2 9 6)) 0 0) => (vec2 2 1)
         ((corner-gradients {:cellsize 4 :dimensions 2} gradients2 (vec2 9 6)) 0 1) => (vec2 3 1)
         ((corner-gradients {:cellsize 4 :dimensions 2} gradients2 (vec2 9 6)) 1 0) => (vec2 2 2)
         ((corner-gradients {:cellsize 4 :dimensions 2} gradients2 (vec2 9 6)) 1 1) => (vec2 3 2)
         ((corner-gradients {:cellsize 4 :dimensions 2} gradients2 (vec2 23 15)) 1 1) => (vec2 0 0)
         ((corner-gradients {:cellsize 4 :dimensions 3} gradients3 (vec3 9 6 3)) 0 0 0) => (vec3 2 1 0)))


(defn influence-values
  [gradients vectors]
  (tensor/compute-tensor (repeat (count (dtype/shape gradients)) 2)
                         (fn [& args] (dot (apply gradients args) (apply vectors args))) :double))


(facts "Compute influence values from corner vectors and gradients"
       (let [gradients2 (tensor/compute-tensor [2 2] (fn [_y x] (vec2 x 10)))
             vectors2   (tensor/compute-tensor [2 2] (fn [y _x] (vec2 1 y)))
             influence2 (influence-values gradients2 vectors2)
             gradients3 (tensor/compute-tensor [2 2 2] (fn [z y x] (vec3 x y z)))
             vectors3   (tensor/compute-tensor [2 2 2] (fn [_z _y _x] (vec3 1 10 100)))
             influence3 (influence-values gradients3 vectors3)]
         (influence2 0 0) => 0.0
         (influence2 0 1) => 1.0
         (influence2 1 0) => 10.0
         (influence2 1 1) => 11.0
         (influence3 1 1 1) => 111.0))


(defn ease-curve
  [t]
  (-> t (* 6.0) (- 15.0) (* t) (+ 10.0) (* t t t)))


(facts "Monotonously increasing function with zero derivative at zero and one"
       (ease-curve 0.0) => 0.0
       (ease-curve 0.25) => (roughly 0.103516 1e-6)
       (ease-curve 0.5) => 0.5
       (ease-curve 0.75) => (roughly 0.896484 1e-6)
       (ease-curve 1.0) => 1.0)


(-> (tc/dataset {:t (range 0.0 1.025 0.025) :ease (map ease-curve (range 0.0 1.025 0.025))})
    (plotly/base {:=title "Ease Curve"})
    (plotly/layer-line {:=x :t :=y :ease}))


(defn interpolation-weights
  ([params point]
   (interpolation-weights (cell-pos params point)))
  ([pos]
   (if (seq pos)
     (let [w1   (- 1.0 (last pos))
           w2   (last pos)
           elem (interpolation-weights (butlast pos))]
       (tensor/->tensor [(dfn/* (ease-curve w1) elem) (dfn/* (ease-curve w2) elem)]))
     1.0)))


(facts "Interpolation weights"
       (let [weights2 (interpolation-weights {:cellsize 8} (vec2 2 7))
             weights3 (interpolation-weights {:cellsize 8} (vec3 2 7 3))]
         (weights2 0 0) => (roughly 0.014391 1e-6)
         (weights2 0 1) => (roughly 0.001662 1e-6)
         (weights2 1 0) => (roughly 0.882094 1e-6)
         (weights2 1 1) => (roughly 0.101854 1e-6)
         (weights3 0 0 0) => (roughly 0.010430 1e-6)))


(defn perlin-sample
  [params gradients point]
  (let [gradients (corner-gradients params gradients point)
        vectors   (corner-vectors params point)
        influence (influence-values gradients vectors)
        weights   (interpolation-weights params point)]
    (dfn/reduce-+ (dfn/* weights influence))))


(defn perlin-noise
  [{:keys [size dimensions] :as params}]
  (let [gradients (random-gradients params)]
    (tensor/clone
      (tensor/compute-tensor (repeat dimensions size)
                             (fn [& args]
                                 (let [center (add (apply vec-n (reverse args)) (apply vec-n (repeat dimensions 0.5)))]
                                   (perlin-sample params gradients center)))
                             :double))))


(def perlin (perlin-noise (make-noise-params 256 8 2)))

(def perlin-norm (dfn/* (/ 255 (- (dfn/reduce-max perlin) (dfn/reduce-min perlin))) (dfn/- perlin (dfn/reduce-min perlin))))

(bufimg/tensor->image perlin-norm)

;; ## Combination of Worley and Perlin noise
(def perlin-worley-norm (dfn/+ (dfn/* 0.3 perlin-norm) (dfn/* 0.7 worley-norm)))

(bufimg/tensor->image (dfn/+ (dfn/* 0.5 perlin-norm) (dfn/* 0.5 worley-norm)))

;; ## Interpolation
(defn interpolate
  [tensor & args]
  (if (seq args)
    (let [x  (first args)
          xc (- x 0.5)
          xf (frac xc)
          x0 (int (Math/floor xc))]
      (+ (* (- 1.0 xf) (apply interpolate (wrap-get tensor      x0 ) (rest args)))
         (*        xf  (apply interpolate (wrap-get tensor (inc x0)) (rest args)))))
    tensor))


(facts "Interpolate values of tensor"
       (let [x2 (tensor/compute-tensor [4 6] (fn [_y x] x))
             y2 (tensor/compute-tensor [4 6] (fn [y _x] y))
             x3 (tensor/compute-tensor [4 6 8] (fn [_z _y x] x))
             y3 (tensor/compute-tensor [4 6 8] (fn [_z y _x] y))
             z3 (tensor/compute-tensor [4 6 8] (fn [z _y _x] z))]
         (interpolate x2 2.5 3.5) => 3.0
         (interpolate y2 2.5 3.5) => 2.0
         (interpolate x2 2.5 4.0) => 3.5
         (interpolate y2 3.0 3.5) => 2.5
         (interpolate x2 0.0 0.0) => 2.5
         (interpolate y2 0.0 0.0) => 1.5
         (interpolate x3 2.5 3.5 5.5) => 5.0
         (interpolate y3 2.5 3.5 3.0) => 3.0
         (interpolate z3 2.5 3.5 5.5) => 2.0))

;; ## Octaves of noise
(defn fractal-brownian-motion
  [base octaves & args]
  (let [scales (take (count octaves) (iterate #(* 2 %) 1))]
    (reduce + 0.0 (map (fn [amplitude scale] (* amplitude (apply base (map #(* scale %) args)))) octaves scales))))


(facts "Fractal Brownian motion"
       (let [base1 (fn [x] (if (>= (mod x 2.0) 1.0) 1.0 0.0))
             base2 (fn [y x] (if (= (Math/round (mod y 2.0)) (Math/round (mod x 2.0))) 0.0 1.0))]
         (fractal-brownian-motion base2 [1.0] 0 0) => 0.0
         (fractal-brownian-motion base2 [1.0] 0 1) => 1.0
         (fractal-brownian-motion base2 [1.0] 1 0) => 1.0
         (fractal-brownian-motion base2 [1.0] 1 1) => 0.0
         (fractal-brownian-motion base2 [0.5] 0 1) => 0.5
         (fractal-brownian-motion base2 [] 0 1) => 0.0
         (fractal-brownian-motion base2 [0.0 1.0] 0 0) => 0.0
         (fractal-brownian-motion base2 [0.0 1.0] 0.0 0.5) => 1.0
         (fractal-brownian-motion base2 [0.0 1.0] 0.5 0.0) => 1.0
         (fractal-brownian-motion base2 [0.0 1.0] 0.5 0.5) => 0.0
         (fractal-brownian-motion base1 [1.0] 0) => 0.0
         (fractal-brownian-motion base1 [1.0] 1) => 1.0
         (fractal-brownian-motion base1 [0.0 1.0] 0.0) => 0.0
         (fractal-brownian-motion base1 [0.0 1.0] 0.5) => 1.0))


(defn remap
  [value low1 high1 low2 high2]
  (dfn/+ low2 (dfn/* (dfn/- value low1) (/ (- high2 low2) (- high1 low1)))))


(tabular "Remap values of tensor"
       (fact ((remap (tensor/->tensor [?value]) ?low1 ?high1 ?low2 ?high2) 0) => ?expected)
       ?value ?low1 ?high1 ?low2 ?high2 ?expected
       0      0     1      0     1      0
       1      0     1      0     1      1
       0      0     1      2     3      2
       1      0     1      2     3      3
       2      2     3      0     1      0
       3      2     3      0     1      1
       1      0     2      0     4      2)


(defn clamp
  [value low high]
  (dfn/max low (dfn/min value high)))


(tabular "Clamp values of tensor"
       (fact ((clamp (tensor/->tensor [?value]) ?low ?high) 0) => ?expected)
       ?value ?low ?high ?expected
       2      2    3      2
       3      2    3      3
       0      2    3      2
       4      2    3      3)


(defn octaves
  [n decay]
  (let [series (take n (iterate #(* % decay) 1.0))
        sum    (apply + series)]
    (mapv #(/ % sum) series)))


(octaves 4 0.5)


(defn noise-octaves
  [tensor octaves low high]
  (tensor/clone
    (clamp
      (remap
        (tensor/compute-tensor (dtype/shape tensor)
                               (fn [& args]
                                   (apply fractal-brownian-motion
                                     (partial interpolate tensor)
                                     octaves
                                     (map #(+ % 0.5) args)))
                               :double)
        low high 0 255)
      0 255)))

(bufimg/tensor->image (noise-octaves worley-norm (octaves 4 0.6) 120 230))

(bufimg/tensor->image (noise-octaves perlin-norm (octaves 4 0.6) 120 230))

(bufimg/tensor->image (noise-octaves perlin-worley-norm (octaves 4 0.6) 120 230))


;; ## Testing shaders

(GLFW/glfwInit)

(def window-width 640)
(def window-height 480)

(GLFW/glfwDefaultWindowHints)
(GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
(def window (GLFW/glfwCreateWindow window-width window-height "Invisible Window" 0 0))

(GLFW/glfwMakeContextCurrent window)
(GL/createCapabilities)


(defn make-shader [source shader-type]
  (let [shader (GL20/glCreateShader shader-type)]
    (GL20/glShaderSource shader source)
    (GL20/glCompileShader shader)
    (when (zero? (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS))
      (throw (Exception. (GL20/glGetShaderInfoLog shader 1024))))
    shader))


(defn make-program [& shaders]
  (let [program (GL20/glCreateProgram)]
    (doseq [shader shaders]
           (GL20/glAttachShader program shader)
           (GL20/glDeleteShader shader))
    (GL20/glLinkProgram program)
    (when (zero? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
      (throw (Exception. (GL20/glGetProgramInfoLog program 1024))))
    program))


(defn make-program-with-shaders
  [vertex-shader-sources fragment-shader-sources]
  (let [vertex-shaders   (map #(make-shader % GL20/GL_VERTEX_SHADER) vertex-shader-sources)
        fragment-shaders (map #(make-shader % GL20/GL_FRAGMENT_SHADER) fragment-shader-sources)
        program          (apply make-program (concat vertex-shaders fragment-shaders))]
    program))


(def vertex-test "
#version 130
in vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")


(def fragment-test "
#version 130
out vec4 fragColor;
void main()
{
  fragColor = vec4(1, 1, 1, 1);
}")


(defmacro def-make-buffer [method create-buffer]
  `(defn ~method [data#]
     (let [buffer# (~create-buffer (count data#))]
       (.put buffer# data#)
       (.flip buffer#)
       buffer#)))

(def-make-buffer make-float-buffer BufferUtils/createFloatBuffer)
(def-make-buffer make-int-buffer BufferUtils/createIntBuffer)
(def-make-buffer make-byte-buffer BufferUtils/createByteBuffer)

(defn setup-vao [vertices indices]
  (let [vao (GL30/glGenVertexArrays)
        vbo (GL15/glGenBuffers)
        ibo (GL15/glGenBuffers)]
    (GL30/glBindVertexArray vao)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
    (GL15/glBufferData GL15/GL_ARRAY_BUFFER (make-float-buffer vertices)
                       GL15/GL_STATIC_DRAW)
    (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER ibo)
    (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER (make-int-buffer indices)
                       GL15/GL_STATIC_DRAW)
    {:vao vao :vbo vbo :ibo ibo}))


(defn teardown-vao [{:keys [vao vbo ibo]}]
  (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers ibo)
  (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers vbo)
  (GL30/glBindVertexArray 0)
  (GL15/glDeleteBuffers vao))


(defn float-buffer->array
  "Convert float buffer to flaot array"
  [buffer]
  (let [result (float-array (.limit buffer))]
    (.get buffer result)
    (.flip buffer)
    result))


(defn make-texture-2d
  [width height]
  (let [texture (GL11/glGenTextures)]
    (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
    (GL11/glTexParameteri GL12/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
    (GL11/glTexParameteri GL12/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
    (GL11/glTexParameteri GL12/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
    (GL11/glTexParameteri GL12/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT)
    (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_RGBA32F width height)
    texture))


(defn read-texture-2d
  [texture width height]
  (let [buffer (BufferUtils/createFloatBuffer (* height width 4))]
    (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
    (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGBA GL11/GL_FLOAT buffer)
    (float-buffer->array buffer)))


(defmacro framebuffer-render
  [texture width height & body]
  `(let [fbo# (GL30/glGenFramebuffers)]
     (try
       (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo#)
       (GL11/glBindTexture GL11/GL_TEXTURE_2D ~texture)
       (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 ~texture 0)
       (GL20/glDrawBuffers (volumetric-clouds.main/make-int-buffer (int-array [GL30/GL_COLOR_ATTACHMENT0])))
       (GL11/glViewport 0 0 ~width ~height)
       ~@body
       (finally
         (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
         (GL30/glDeleteFramebuffers fbo#)))))


(defn setup-point-attribute
  [program]
  (let [point-attribute (GL20/glGetAttribLocation program "point")]
    (GL20/glVertexAttribPointer point-attribute 3 GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
    (GL20/glEnableVertexAttribArray point-attribute)))


(defn setup-quad-vao
  []
  (let [vertices (float-array [1.0 1.0 0.0, -1.0 1.0 0.0, -1.0 -1.0 0.0, 1.0 -1.0 0.0])
        indices  (int-array [0 1 2 3])]
    (setup-vao vertices indices)))


(defmacro render-array
  [width height & body]
  `(let [texture# (volumetric-clouds.main/make-texture-2d ~width ~height)]
     (try
       (volumetric-clouds.main/framebuffer-render texture# ~width ~height ~@body)
       (volumetric-clouds.main/read-texture-2d texture# ~width ~height)
       (finally
         (GL11/glDeleteTextures texture#)))))


(defn render-pixel
  [vertex-sources fragment-sources]
  (let [program (make-program-with-shaders vertex-sources fragment-sources)
        vao     (setup-quad-vao)]
    (setup-point-attribute program)
    (try
      (render-array 1 1
                    (GL20/glUseProgram program)
                    (GL11/glDrawElements GL11/GL_QUADS 4 GL11/GL_UNSIGNED_INT 0))
      (finally
        (teardown-vao vao)
        (GL20/glDeleteProgram program)))))


(render-pixel [vertex-test] [fragment-test])


;; ## Noise octaves shader

(def noise-mock
"#version 130
float noise(vec3 idx)
{
  ivec3 v = ivec3(floor(idx.x), floor(idx.y), floor(idx.z)) % 2;
  return ((v.x == 1) == (v.y == 1)) == (v.z == 1) ? 1.0 : 0.0;
}")


(def noise-probe
  (template/fn [x y z]
"#version 130
out vec4 fragColor;
float noise(vec3 idx);
void main()
{
  fragColor = vec4(noise(vec3(<%= x %>, <%= y %>, <%= z %>)));
}"))


(tabular "Test noise mock"
         (fact (nth (render-pixel [vertex-test] [noise-mock (noise-probe ?x ?y ?z)]) 0) => ?result)
         ?x ?y ?z ?result
         0  0  0  0.0
         1  0  0  1.0
         0  1  0  1.0
         1  1  0  0.0
         0  0  1  1.0
         1  0  1  0.0
         0  1  1  0.0
         1  1  1  1.0)


(def noise-octaves
  (template/fn [octaves]
"#version 130
out vec4 fragColor;
float noise(vec3 idx);
float octaves(vec3 idx)
{
  float result = 0.0;
<% (doseq [multiplier octaves] %>
  result += <%= multiplier %> * noise(idx);
  idx *= 2.0;
<%= ) %>
  return result;
}"))


(def octaves-probe
  (template/fn [x y z]
"#version 130
out vec4 fragColor;
float octaves(vec3 idx);
void main()
{
  fragColor = vec4(octaves(vec3(<%= x %>, <%= y %>, <%= z %>)));
}"))


(tabular "Test octaves of noise"
         (fact (first (render-pixel [vertex-test] [noise-mock (noise-octaves ?octaves) (octaves-probe ?x ?y ?z)]))
               => ?result)
         ?x  ?y ?z ?octaves  ?result
         0   0  0  [1.0]     0.0
         1   0  0  [1.0]     1.0
         1   0  0  [0.5]     0.5
         0.5 0  0  [0.0 1.0] 1.0
         0.5 0  0  [0.0 1.0] 1.0
         1   0  0  [1.0 0.0] 1.0)


;; ## Shader for intersecting a ray with a box

(def ray-box
"#version 130
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction)
{
  vec3 inv_dir = 1.0 / direction;
  vec3 smin = (box_min - origin) * inv_dir;
  vec3 smax = (box_max - origin) * inv_dir;
  vec3 s1 = min(smin, smax);
  vec3 s2 = max(smin, smax);
  float s_near = max(max(s1.x, s1.y), s1.z);
  float s_far = min(min(s2.x, s2.y), s2.z);
  if (isinf(s_near) || isinf(s_far))
    return vec2(0.0, 0.0);
  else
    return vec2(max(s_near, 0.0), max(0.0, s_far));
}")


(def ray-box-probe
  (template/fn [ox oy oz dx dy dz]
"#version 130
out vec4 fragColor;
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
void main()
{
  vec3 box_min = vec3(-1, -1, -1);
  vec3 box_max = vec3(1, 1, 1);
  vec3 origin = vec3(<%= ox %>, <%= oy %>, <%= oz %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  fragColor = vec4(ray_box(box_min, box_max, origin, direction), 0, 0);
}"))


(tabular "Test intersection of ray with box"
         (fact ((juxt first second) (render-pixel [vertex-test] [ray-box (ray-box-probe ?ox ?oy ?oz ?dx ?dy ?dz)]))
               => ?result)
         ?ox ?oy ?oz ?dx ?dy ?dz ?result
         -2   0   0   1   0   0  [1.0 3.0]
         -2   0   0   2   0   0  [0.5 1.5]
         -2   2   2   1   0   0  [0.0 0.0]
          0  -2   0   0   1   0  [1.0 3.0]
          0  -2   0   0   2   0  [0.5 1.5]
          2  -2   2   0   1   0  [0.0 0.0]
          0   0  -2   0   0   1  [1.0 3.0]
          0   0  -2   0   0   2  [0.5 1.5]
          2   2  -2   0   0   1  [0.0 0.0]
          0   0   0   1   0   0  [0.0 1.0]
          2   0   0   1   0   0  [0.0 0.0])


;; ## Shader for light transfer through clouds

(def fog
  (template/fn [v]
"#version 130
float fog(vec3 idx)
{
  return <%= v %>;
}"))


(def cloud-transfer
  (template/fn [noise step]
"#version 130
#define STEP <%= step %>
float <%= noise %>(vec3 idx);
float in_scatter(vec3 point, vec3 direction);
float shadow(vec3 point);
vec4 cloud_transfer(vec3 origin, vec3 direction, vec2 interval)
{
  vec4 result = vec4(0, 0, 0, 0);
  for (float t = interval.x + 0.5 * STEP; t < interval.y; t += STEP) {
    vec3 point = origin + direction * t;
    float density = <%= noise %>(point);
    float transmittance = exp(-density * STEP);
    vec3 color = vec3(in_scatter(point, direction) * shadow(point));
    result.rgb += color * (1.0 - result.a) * (1.0 - transmittance);
    result.a = 1.0 - (1.0 - result.a) * transmittance;
  };
  return result;
}"))


(def constant-scatter
"#version 130
float in_scatter(vec3 point, vec3 direction)
{
  return 1.0;
}")


(def no-shadow
"#version 130
float shadow(vec3 point)
{
  return 1.0;
}")


(def cloud-transfer-probe
  (template/fn [a b]
"#version 130
out vec4 fragColor;
vec4 cloud_transfer(vec3 origin, vec3 direction, vec2 interval);
void main()
{
  vec3 origin = vec3(0, 0, 0);
  vec3 direction = vec3(1, 0, 0);
  vec2 interval = vec2(<%= a %>, <%= b %>);
  fragColor = cloud_transfer(origin, direction, interval);
}"))


(defn roughly-vector
  [expected error]
  (fn [actual]
      (and (== (count expected) (count actual))
           (<= (apply + (mapv (fn [a b] (* (- b a) (- b a))) actual expected)) (* error error)))))


(tabular "Test cloud transfer"
         (fact (seq (render-pixel [vertex-test] [(fog ?density) constant-scatter no-shadow (cloud-transfer "fog" ?step) (cloud-transfer-probe ?a ?b)]))
               => (roughly-vector ?result 1e-3))
         ?a ?b ?step ?density ?result
         0  0  1     0.0      [0.0 0.0 0.0 0.0]
         0  1  1     1.0      [0.632 0.632 0.632 0.632]
         0  1  0.5   1.0      [0.632 0.632 0.632 0.632]
         0  1  0.5   0.5      [0.393 0.393 0.393 0.393])


;; ## Rendering of fog box

(def fragment-cloud
"#version 130
uniform vec2 resolution;
uniform vec3 light;
uniform mat3 rotation;
uniform float focal_length;
uniform float distance;
out vec4 fragColor;
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
vec4 cloud_transfer(vec3 origin, vec3 direction, vec2 interval);
void main()
{
  vec3 direction = normalize(rotation * vec3(gl_FragCoord.xy - 0.5 * resolution, focal_length));
  vec3 origin = rotation * vec3(0, 0, -distance);
  vec2 interval = ray_box(vec3(-0.5, -0.5, -0.5), vec3(0.5, 0.5, 0.5), origin, direction);
  vec4 transfer = cloud_transfer(origin, direction, interval);
  vec3 background = mix(vec3(0.125, 0.125, 0.25), vec3(1, 1, 1), pow(dot(direction, light), 1000.0));
  fragColor = vec4(background * (1.0 - transfer.a) + transfer.rgb, 1.0);
}")


(defn setup-fog-uniforms
  [program width height]
  (let [rotation     (mulm (rotation-matrix-3d-y (to-radians 40.0)) (rotation-matrix-3d-x (to-radians -20.0)))
        focal-length (/ (* 0.5 width) (tan (to-radians 30.0)))
        light        (normalize (vec3 6 1 10))]
    (GL20/glUseProgram program)
    (GL20/glUniform2f (GL20/glGetUniformLocation program "resolution") width height)
    (GL20/glUniform3f (GL20/glGetUniformLocation program "light") (light 0) (light 1) (light 2))
    (GL20/glUniformMatrix3fv (GL20/glGetUniformLocation program "rotation") true
                             (make-float-buffer (mat->float-array rotation)))
    (GL20/glUniform1f (GL20/glGetUniformLocation program "focal_length") focal-length)
    (GL20/glUniform1f (GL20/glGetUniformLocation program "distance") 2.0)))


(defn render-fog
  [width height]
  (let [fragment-sources [ray-box constant-scatter no-shadow (cloud-transfer "fog" 0.01) (fog 1.0) fragment-cloud]
        program          (make-program-with-shaders [vertex-test] fragment-sources)
        vao              (setup-quad-vao)]
    (setup-point-attribute program)
    (try
      (render-array width height
                    (setup-fog-uniforms program width height)
                    (GL11/glDrawElements GL11/GL_QUADS 4 GL11/GL_UNSIGNED_INT 0))
      (finally
        (teardown-vao vao)
        (GL20/glDeleteProgram program)))))


(defn rgba-array->bufimg [data width height]
  (-> data tensor/->tensor (tensor/reshape [height width 4]) (tensor/select :all :all [2 1 0]) (dfn/* 255) (clamp 0 255)))


(bufimg/tensor->image (rgba-array->bufimg (render-fog 640 480) 640 480))


;; ## Rendering of 3D noise

(defn float-array->texture3d
  [data size]
  (let [buffer  (make-float-buffer data)
        texture (GL11/glGenTextures)]
    (GL11/glBindTexture GL12/GL_TEXTURE_3D texture)
    (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
    (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
    (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
    (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT)
    (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL12/GL_TEXTURE_WRAP_R GL11/GL_REPEAT)
    (GL12/glTexImage3D GL12/GL_TEXTURE_3D 0 GL30/GL_R32F size size size 0 GL11/GL_RED GL11/GL_FLOAT buffer)
    texture))


(def noise3d (dfn/- (dfn/* 0.3 (perlin-noise (make-noise-params 32 4 3)))
                    (dfn/* 0.7 (worley-noise (make-noise-params 32 4 3)))))
(def noise-3d-norm (dfn/* (/ 1.0 (- (dfn/reduce-max noise3d) (dfn/reduce-min noise3d))) (dfn/- noise3d (dfn/reduce-min noise3d))))
(def noise-texture (float-array->texture3d (dtype/->float-array noise-3d-norm) 32))


(def noise-shader
"#version 130
uniform sampler3D noise3d;
float noise(vec3 idx)
{
  return texture(noise3d, idx).r;
}")


(defn setup-noise-uniforms
  [program width height]
  (setup-fog-uniforms program width height)
  (GL20/glUniform1i (GL20/glGetUniformLocation program "noise3d") 0)
  (GL13/glActiveTexture GL13/GL_TEXTURE0)
  (GL11/glBindTexture GL12/GL_TEXTURE_3D noise-texture))


(defn render-noise
  [width height & cloud-shaders]
  (let [fragment-sources (concat cloud-shaders [ray-box fragment-cloud])
        program          (make-program-with-shaders [vertex-test] fragment-sources)
        vao              (setup-quad-vao)]
    (try
      (setup-point-attribute program)
      (render-array width height
                    (setup-noise-uniforms program width height)
                    (GL11/glDrawElements GL11/GL_QUADS 4 GL11/GL_UNSIGNED_INT 0))
      (finally
        (teardown-vao vao)
        (GL20/glDeleteProgram program)))))


(bufimg/tensor->image (rgba-array->bufimg (render-noise 640 480 constant-scatter no-shadow (cloud-transfer "noise" 0.01) noise-shader) 640 480))


;; ## Remap and clamp 3D noise

(def remap-clamp
"#version 130
float remap_clamp(float value, float low1, float high1, float low2, float high2)
{
  float t = (value - low1) / (high1 - low1);
  return clamp(low2 + t * (high2 - low2), low2, high2);
}")


(def remap-probe
  (template/fn [value low1 high1 low2 high2]
"#version 130
out vec4 fragColor;
float remap_clamp(float value, float low1, float high1, float low2, float high2);
void main()
{
  fragColor = vec4(remap_clamp(<%= value %>, <%= low1 %>, <%= high1 %>, <%= low2 %>, <%= high2 %>));
}"))


(tabular "Remap and clamp input parameter values"
       (fact (first (render-pixel [vertex-test] [remap-clamp (remap-probe ?value ?low1 ?high1 ?low2 ?high2)]))
             => ?expected)
       ?value ?low1 ?high1 ?low2 ?high2 ?expected
       0      0     1      0     1      0.0
       1      0     1      0     1      1.0
       0      0     1      2     3      2.0
       1      0     1      2     3      3.0
       2      2     3      0     1      0.0
       3      2     3      0     1      1.0
       1      0     2      0     4      2.0
       0      1     2      1     2      1.0
       3      1     2      1     2      2.0)


(def remap-noise
  (template/fn [base low1 high1 high2]
"#version 130
float <%= base %>(vec3 idx);
float remap_clamp(float value, float low1, float high1, float low2, float high2);
float remap_noise(vec3 idx)
{
  return remap_clamp(<%= base %>(idx), <%= low1 %>, <%= high1 %>, 0.0, <%= high2 %>);
}"))


(def cloud-strength 6.5)


(bufimg/tensor->image (rgba-array->bufimg (render-noise 640 480 constant-scatter no-shadow (cloud-transfer "remap_noise" 0.01) remap-clamp (remap-noise "noise" 0.45 0.9 cloud-strength) noise-shader) 640 480))


;; ## Octaves of 3D noise

(bufimg/tensor->image (rgba-array->bufimg (render-noise 640 480 constant-scatter no-shadow (cloud-transfer "remap_noise" 0.01) remap-clamp (remap-noise "octaves" 0.45 0.9 cloud-strength) (noise-octaves (octaves 4 0.5)) noise-shader) 640 480))


;; ## Mie scattering

(def mie-scatter
  (template/fn [g]
"#version 450 core
#define M_PI 3.1415926535897932384626433832795
#define ANISOTROPIC 0.25
#define G <%= g %>
uniform vec3 light;
float mie(float mu)
{
  return 3 * (1 - G * G) * (1 + mu * mu) / (8 * M_PI * (2 + G * G) * pow(1 + G * G - 2 * G * mu, 1.5));
}
float in_scatter(vec3 point, vec3 direction)
{
  return mix(1.0, mie(dot(light, direction)), ANISOTROPIC);
}"))


(def mie-probe
  (template/fn [mu]
"#version 450 core
out vec4 fragColor;
float mie(float mu);
void main()
{
  float result = mie(<%= mu %>);
  fragColor = vec4(result, 0, 0, 1);
}"))


(tabular "Shader function for scattering phase function"
         (fact (first (render-pixel [vertex-test] [(mie-scatter ?g) (mie-probe ?mu)])) => (roughly ?result 1e-6))
         ?g  ?mu ?result
         0   0   (/ 3 (* 16 PI))
         0   1   (/ 6 (* 16 PI))
         0  -1   (/ 6 (* 16 PI))
         0.5 0   (/ (* 3 0.75) (* 8 PI 2.25 (pow 1.25 1.5)))
         0.5 1   (/ (* 6 0.75) (* 8 PI 2.25 (pow 0.25 1.5))))


(bufimg/tensor->image (rgba-array->bufimg (render-noise 640 480 (mie-scatter 0.76) no-shadow (cloud-transfer "remap_noise" 0.01) remap-clamp (remap-noise "octaves" 0.45 0.9 cloud-strength) (noise-octaves (octaves 4 0.5)) noise-shader) 640 480))


;; ## Self-shading of clouds
(def shadow
  (template/fn [noise step]
"#version 130
#define STEP <%= step %>
uniform vec3 light;
float <%= noise %>(vec3 idx);
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float shadow(vec3 point)
{
  vec2 interval = ray_box(vec3(-0.5, -0.5, -0.5), vec3(0.5, 0.5, 0.5), point, light);
  float result = 1.0;
  for (float t = interval.x + 0.5 * STEP; t < interval.y; t += STEP) {
    float density = <%= noise %>(point + t * light);
    float transmittance = exp(-density * STEP);
    result *= transmittance;
  };
  return result;
}"))


(bufimg/tensor->image (rgba-array->bufimg (render-noise 640 480 (mie-scatter 0.76) (shadow "remap_noise" 0.01) (cloud-transfer "remap_noise" 0.01) remap-clamp (remap-noise "octaves" 0.45 0.9 cloud-strength) (noise-octaves (octaves 4 0.5)) noise-shader) 640 480))

;; ## Tidy up
(GL11/glBindTexture GL12/GL_TEXTURE_3D 0)
(GL11/glDeleteTextures noise-texture)

(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)

;; ## Further topics
;; * vertical cloud profile
;; * powder function
;; * curl noise
;; * deep opacity maps https://www.wedesoft.de/software/2023/05/03/volumetric-clouds/
