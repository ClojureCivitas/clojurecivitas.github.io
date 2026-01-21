^{:kindly/hide-code true
  :clay             {:title  "Volumetric Clouds"
                     :external-requirements ["Xorg"]
                     :quarto {:author   [:janwedekind]
                              :description "Procedural generation of volumetric clouds using different types of noise"
                              :image    "clouds.jpg"
                              :type     :post
                              :date     "2026-01-13"
                              :category :clojure
                              :tags     [:visualization]}}}

(ns volumetric-clouds.main
    (:require [scicloj.kindly.v4.kind :as kind]
              [clojure.math :refer (PI sqrt)]
              [midje.sweet :refer (fact facts tabular => roughly)]
              [fastmath.vector :refer (vec2 vec3 add mult sub div mag dot)]
              [tech.v3.datatype :as dtype]
              [tech.v3.tensor :as tensor]
              [tech.v3.datatype.functional :as dfn]
              [tablecloth.api :as tc]
              [scicloj.tableplot.v1.plotly :as plotly]
              [tech.v3.libs.buffered-image :as bufimg])
    (:import [javax.imageio ImageIO]
             [org.lwjgl.opengl GL11]
             [org.lwjgl.stb STBImageWrite]
             [org.lwjgl BufferUtils]
             [org.lwjgl.glfw GLFW]
             [org.lwjgl.opengl GL GL11 GL12 GL15 GL20 GL30 GL32 GL42]))


;; Procedural generation of volumetric clouds
;;
;; * Midje testing of shaders
;; * Generating OpenGL shaders using templates
;; * cloud shadows
;; * powder function
;; * video showing TDD with tmux
;;
;; References
;; * https://adrianb.io/2014/08/09/perlinnoise.html

;; # Worley noise

(defn make-noise-params
  [size divisions dimensions]
  {:size size :divisions divisions :cellsize (/ size divisions) :dimensions dimensions})


(fact "Noise parameter initialisation"
      (make-noise-params 512 8 2) => {:size 512 :divisions 8 :cellsize 64 :dimensions 2})


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


(let [points  (tensor/reshape (random-points (make-noise-params 512 8 2)) [(* 8 8)])
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


(def worley (worley-noise (make-noise-params 512 8 2)))

(def worley-norm (dfn/* (/ 255 (- (dfn/reduce-max worley) (dfn/reduce-min worley))) (dfn/- (dfn/reduce-max worley) worley)))

(bufimg/tensor->image worley-norm)

;; # Perlin noise

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
         ((random-gradients {:divisions 8 :dimensions 3}) 0 0 0) => (roughly-vec (vec3 (sqrt (/ 1 3)) (sqrt (/ 1 3)) (sqrt (/ 1 3))) 1e-6)))


(let [gradients (tensor/reshape (random-gradients (make-noise-params 512 8 2)) [(* 8 8)])
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


(def perlin (perlin-noise (make-noise-params 512 8 2)))

(def perlin-norm (dfn/* (/ 255 (- (dfn/reduce-max perlin) (dfn/reduce-min perlin))) (dfn/- perlin (dfn/reduce-min perlin))))

(bufimg/tensor->image perlin-norm)

;; # Combination of Worley and Perlin noise
(def perlin-worley-norm (dfn/+ (dfn/* 0.3 perlin-norm) (dfn/* 0.7 worley-norm)))

(bufimg/tensor->image (dfn/+ (dfn/* 0.5 perlin-norm) (dfn/* 0.5 worley-norm)))

;; # Interpolation
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

;; # Octaves of noise
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

;; # Testing shaders

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
  {:malli/schema [:=> [:cat :some] seqable?]}
  [buffer]
  (let [result (float-array (.limit buffer))]
    (.get buffer result)
    (.flip buffer)
    result))


(defn make-texture
  [width height]
  (let [texture (GL11/glGenTextures)]
    (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
    (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_RGBA32F width height)
    texture))


(defn read-texture
  [texture width height]
  (let [buffer (BufferUtils/createFloatBuffer (* height width 4))]
    (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
    (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGBA GL11/GL_FLOAT buffer)
    (seq (float-buffer->array buffer))))


(defmacro framebuffer-render
  [texture width height & body]
  `(let [fbo# (GL30/glGenFramebuffers)]
     (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo#)
     (GL11/glBindTexture GL11/GL_TEXTURE_2D ~texture)
     (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 ~texture 0)
     (GL20/glDrawBuffers (volumetric-clouds.main/make-int-buffer (int-array [GL30/GL_COLOR_ATTACHMENT0])))
     (GL11/glViewport 0 0 ~width ~height)
     (let [result# (do ~@body)]
       (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
       (GL30/glDeleteFramebuffers fbo#)
       result#)))


(defn render-pixel
  [vertex-source fragment-source]
  (let [vertices        (float-array [1.0 1.0 0.0, -1.0 1.0 0.0, -1.0 -1.0 0.0, 1.0 -1.0 0.0])
        indices         (int-array [0 1 2 3])
        vertex-shader   (make-shader vertex-source GL20/GL_VERTEX_SHADER)
        fragment-shader (make-shader fragment-source GL20/GL_FRAGMENT_SHADER)
        program         (make-program vertex-shader fragment-shader)
        point-attribute (GL20/glGetAttribLocation program "point")
        vao             (setup-vao vertices indices)
        texture         (make-texture 1 1)]
    (GL20/glVertexAttribPointer point-attribute 3 GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
    (GL20/glEnableVertexAttribArray point-attribute)
    (framebuffer-render texture 1 1
                        (GL20/glUseProgram program)
                        (GL11/glClearColor 1.0 0.5 0.25 1.0)
                        (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
                        (GL11/glDrawElements GL11/GL_QUADS (count indices) GL11/GL_UNSIGNED_INT 0))
    (let [result (read-texture texture 1 1)]
      (GL11/glDeleteTextures texture)
      (teardown-vao vao)
      (GL20/glDeleteProgram program)
      result)))


(render-pixel vertex-test fragment-test)

(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
