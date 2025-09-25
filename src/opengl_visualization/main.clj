^{:kindly/hide-code true
  :clay             {:title  "OpenGL Visualization with LWJGL"
                     :external-requirements ["Xorg"]
                     :quarto {:author   [:janwedekind]
                              :description "Using LWJGL's OpenGL bindings and Fastmath to render data from NASA's CGI Moon Kit"
                              :image    "moon.jpg"
                              :type     :post
                              :date     "2025-09-24"
                              :category :clojure
                              :tags     [:visualization]}}}
(ns opengl-visualization.main
    (:require [clojure.java.io :as io]
              [clojure.math :refer (PI to-radians)]
              [fastmath.vector :refer (vec3 sub add mult normalize)])
    (:import [javax.imageio ImageIO]
             [org.lwjgl BufferUtils]
             [org.lwjgl.glfw GLFW]
             [org.lwjgl.opengl GL GL11 GL13 GL15 GL20 GL30]
             [org.lwjgl.stb STBImageWrite]))

;; ## Getting dependencies
;;
;; First we need to get some libraries and we can use add-libs to fetch them.

;; ```Clojure
;; (add-libs {'org.lwjgl/lwjgl                      {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl$natives-linux        {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-opengl               {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-opengl$natives-linux {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-glfw                 {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-glfw$natives-linux   {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-stb                  {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-stb$natives-linux    {:mvn/version "3.3.6"}
;;            'generateme/fastmath                  {:mvn/version "3.0.0-alpha3"}})
;; (require '[clojure.java.io :as io]
;;          '[clojure.math :refer (PI to-radians)]
;;          '[fastmath.vector :refer (vec3 sub add mult normalize)])
;; (import '[javax.imageio ImageIO]
;;         '[org.lwjgl BufferUtils]
;;         '[org.lwjgl.glfw GLFW]
;;         '[org.lwjgl.opengl GL GL11 GL13 GL15 GL20 GL30]
;;         '[org.lwjgl.stb STBImageWrite])
;; ```

;; ## Creating the window
;;
;; Next we choose the window width and height.
(def window-width 640)
(def window-height 480)

(def radius 1737.4)

;; We define a function to get the temporary directory.
(defn tmpdir
  []
  (System/getProperty "java.io.tmpdir"))

;; And then a function to get a temporary file name.
(defn tmpname
  []
  (str (tmpdir) "/civitas-" (java.util.UUID/randomUUID) ".tmp"))

;; The following function is used to create screenshots for this article.
;; We read the pixels, write them to a temporary file using the STB library and then convert it to an ImageIO object.
(defn screenshot
  []
  (let [filename (tmpname)
        buffer   (java.nio.ByteBuffer/allocateDirect (* 4 window-width window-height))]
    (GL11/glReadPixels 0 0 window-width window-height
                       GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
    (STBImageWrite/stbi_write_png filename window-width window-height 4
                                  buffer (* 4 window-width))
    (-> filename io/file (ImageIO/read))))

;; We need to initialize the GLFW library.
(GLFW/glfwInit)

;; Now we create an invisible window.
;; You can create a visisble window if you want to by not setting the visibility hint to false.
(def window
  (do
    (GLFW/glfwDefaultWindowHints)
    (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
    (GLFW/glfwCreateWindow window-width window-height "Invisible Window" 0 0)))

;; If you have a visible window, you can show it as follows.
;; ```Clojure
;; (GLFW/glfwShowWindow window)
;; ```
;;
;; Note that if you are using a visible window, you always need to swap buffers after rendering.
;; ```Clojure
;; (GLFW/glfwSwapBuffers window)
;; ```

;; Next we need to set up OpenGL rendering for this window.
(do
  (GLFW/glfwMakeContextCurrent window)
  (GL/createCapabilities))

;; ## Basic rendering
;; ### Clearing the window
;;
;; A simple test is to set a clear color and clear the window.
(do
  (GL11/glClearColor 1.0 0.5 0.25 1.0)
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
  (screenshot))

;; ### Creating shader programs
;;
;; We define a convenience function to compile a shader and handle any errors.
(defn make-shader [source shader-type]
  (let [shader (GL20/glCreateShader shader-type)]
    (GL20/glShaderSource shader source)
    (GL20/glCompileShader shader)
    (when (zero? (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS))
      (throw (Exception. (GL20/glGetShaderInfoLog shader 1024))))
    shader))

;; We also define a convenience function to link a program and handle any errors.
(defn make-program [& shaders]
  (let [program (GL20/glCreateProgram)]
    (doseq [shader shaders]
           (GL20/glAttachShader program shader)
           (GL20/glDeleteShader shader))
    (GL20/glLinkProgram program)
    (when (zero? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
      (throw (Exception. (GL20/glGetProgramInfoLog program 1024))))
    program))

;; The following code shows a simple vertex shader which passes through vertex coordinates.
(def vertex-source "
#version 130

in vec3 point;

void main()
{
  gl_Position = vec4(point, 1);
}")

;; In the fragment shader we use the pixel coordinates to output a color ramp.
;; The uniform variable iResolution will later be set to the window resolution.
(def fragment-source "
#version 130

uniform vec2 iResolution;
out vec4 fragColor;

void main()
{
  fragColor = vec4(gl_FragCoord.xy / iResolution.xy, 0, 1);
}")

;; Let's compile the shaders and link the program.
(do
  (def vertex-shader (make-shader vertex-source GL20/GL_VERTEX_SHADER))
  (def fragment-shader (make-shader fragment-source GL20/GL_FRAGMENT_SHADER))
  (def program (make-program vertex-shader fragment-shader)))

;; **Note:** It is beyond the topic of this talk, but you can set up a Clojure function to test an OpenGL shader function by using a probing fragment shader and rendering to a one pixel texture.
;; Please see my article [Test Driven Development with OpenGL](https://www.wedesoft.de/software/2022/07/01/tdd-with-opengl/) for more information!

;; ### Creating vertex buffer data
;;
;; To provide the shader program with vertex data we are going to define just a single quad consisting of four vertices.
;;
;; First we define a macro and use it to define convenience functions for converting arrays to LWJGL buffer objects.
(defmacro def-make-buffer [method create-buffer]
  `(defn ~method [data#]
     (let [buffer# (~create-buffer (count data#))]
       (.put buffer# data#)
       (.flip buffer#)
       buffer#)))

(do
  (def-make-buffer make-float-buffer BufferUtils/createFloatBuffer)
  (def-make-buffer make-int-buffer BufferUtils/createIntBuffer)
  (def-make-buffer make-byte-buffer BufferUtils/createByteBuffer))

;; We define a simple background quad spanning the entire window.
;; We use normalised device coordinates (NDC) which are between -1 and 1.
(def vertices
  (float-array [ 1.0  1.0 0.0
                -1.0  1.0 0.0
                -1.0 -1.0 0.0
                 1.0 -1.0 0.0]))

;; The index array defines the order of the vertices.
(def indices
  (int-array [0 1 2 3]))

;; ### Setting up the vertex buffer
;;
;; We add a convenience function to setup VAO, VBO, and IBO.
;;
;; * We define a vertex array object (VAO) which acts like a context for the vertex and index buffer.
;; * We define a vertex buffer object (VBO) which contains the vertex data.
;; * We also define an index buffer object (IBO) which contains the index data.
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
;; Now we use the function to setup the VAO, VBO, and IBO.
(def vao (setup-vao vertices indices))

;; The data of each vertex is defined by 3 floats (x, y, z).
;; We need to specify the layout of the vertex buffer object so that OpenGL knows how to interpret it.
(do
  (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "point") 3
                              GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
  (GL20/glEnableVertexAttribArray 0))

;; ### Rendering the quad
;;
;; We select the program and define the uniform variable iResolution.
(do
  (GL20/glUseProgram program)
  (GL20/glUniform2f (GL20/glGetUniformLocation program "iResolution")
                    window-width window-height))

;; Since the correct VAO is already bound from the earlier example, we are now ready to draw the quad.
(GL11/glDrawElements GL11/GL_QUADS (count indices) GL11/GL_UNSIGNED_INT 0)
(screenshot)

;; This time the quad shows a color ramp!

;; ### Finishing up
;;
;; We only delete the program since we are going to reuse the VAO in the next example.
(GL20/glDeleteProgram program)

;; ## Rendering a Texture
;; ### Getting the NASA data
;;
;; We define a function to download a file from the web.
(defn download [url target]
  (with-open [in (io/input-stream url)
              out (io/output-stream target)]
    (io/copy in out)))

;; If it does not exist, we download the lunar color map from the [NASA CGI Moon Kit](https://svs.gsfc.nasa.gov/4720/).
(do
  (def moon-tif "src/opengl_visualization/lroc_color_poles_2k.tif")
  (when (not (.exists (io/file moon-tif)))
    (download
      "https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/lroc_color_poles_2k.tif"
      moon-tif)))

;; ### Create a texture
;;
;; Next we load the image using ImageIO.
(do
  (def color (ImageIO/read (io/file moon-tif)))
  (def color-raster (.getRaster color))
  (def color-width (.getWidth color-raster))
  (def color-height (.getHeight color-raster))
  (def color-channels (.getNumBands color-raster))
  (def color-pixels (int-array (* color-width color-height color-channels)))
  (.getPixels color-raster 0 0 color-width color-height color-pixels)
  [color-width color-height color-channels])

;; Then we create an OpenGL texture from the RGB data.
(do
  (def texture-color (GL11/glGenTextures))
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture-color)
  (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA color-width color-height 0
                     GL11/GL_RGB GL11/GL_UNSIGNED_BYTE
                     (make-byte-buffer (byte-array (map unchecked-byte color-pixels))))
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT)
  (GL11/glBindTexture GL11/GL_TEXTURE_2D 0))

;; ### Rendering the texture
;;
;; We are going to use the vertex pass through shader again.
(def vertex-tex "
#version 130

in vec3 point;

void main()
{
  gl_Position = vec4(point, 1);
}")

;; The fragment shader now uses the texture function to lookup color values from a texture.
(def fragment-tex "
#version 130

uniform vec2 iResolution;
uniform sampler2D moon;
out vec4 fragColor;

void main()
{
  fragColor = texture(moon, gl_FragCoord.xy / iResolution.xy);
}")

;; We compile and link the shaders to create a program.
(do
  (def vertex-tex-shader (make-shader vertex-tex GL20/GL_VERTEX_SHADER))
  (def fragment-tex-shader (make-shader fragment-tex GL20/GL_FRAGMENT_SHADER))
  (def tex-program (make-program vertex-tex-shader fragment-tex-shader)))

;; We need to set up the layout of the vertex data again.
(do
  (GL20/glVertexAttribPointer (GL20/glGetAttribLocation tex-program "point") 3
                              GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
  (GL20/glEnableVertexAttribArray 0))

;; We set the resolution and bind the texture to the texture slot number 0.
(do
  (GL20/glUseProgram tex-program)
  (GL20/glUniform2f (GL20/glGetUniformLocation tex-program "iResolution")
                    window-width window-height)
  (GL20/glUniform1i (GL20/glGetUniformLocation tex-program "moon") 0)
  (GL13/glActiveTexture GL13/GL_TEXTURE0)
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture-color))

;; The quad now is textured!
(do
  (GL11/glDrawElements GL11/GL_QUADS (count indices) GL11/GL_UNSIGNED_INT 0)
  (screenshot))

;; ### Finishing up
;;
;; We create a convenience function to tear down the VAO, VBO, and IBO.
(defn teardown-vao [{:keys [vao vbo ibo]}]
  (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers ibo)
  (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers vbo)
  (GL30/glBindVertexArray 0)
  (GL15/glDeleteBuffers vao))

;; We tear down the quad.
(teardown-vao vao)

;; We also delete the program.
(GL20/glDeleteProgram tex-program)

;;; ## Render a 3D cube
;;;
;;; ### Create vertex data
;;;
;;; If we want to render a cube, we need to define 8 vertices.
(def vertices-cube
  (float-array [-1.0 -1.0 -1.0
                 1.0 -1.0 -1.0
                 1.0  1.0 -1.0
                -1.0  1.0 -1.0
                -1.0 -1.0  1.0
                 1.0 -1.0  1.0
                 1.0  1.0  1.0
                -1.0  1.0  1.0]))

;; The cube is made up of 6 quads, with 4 vertex indices per quad.
;; So we require 6 * 4 = 24 indices.
(def indices-cube
  (int-array [0 1 2 3
              7 6 5 4
              0 3 7 4
              5 6 2 1
              3 2 6 7
              4 5 1 0]))

;; ### Initialize vertex buffer array
;;
;; We use the function from earlier to set up the VAO, VBO, and IBO.
(def vao-cube (setup-vao vertices-cube indices-cube))

;;; ### Shader program mapping texture onto cube
;;;
;;; We first define a vertex shader, which takes cube coordinates, rotates, translates, and projects them.
(def vertex-moon "
#version 130

uniform float fov;
uniform float alpha;
uniform float beta;
uniform float distance;
uniform vec2 iResolution;
in vec3 point;
out vec3 vpoint;

void main()
{
  // Rotate and translate vertex
  mat3 rot_y = mat3(vec3(cos(alpha), 0, sin(alpha)),
                    vec3(0, 1, 0),
                    vec3(-sin(alpha), 0, cos(alpha)));
  mat3 rot_x = mat3(vec3(1, 0, 0),
                    vec3(0, cos(beta), -sin(beta)),
                    vec3(0, sin(beta), cos(beta)));
  vec3 p = rot_x * rot_y * point + vec3(0, 0, distance);

  // Project vertex creating normalized device coordinates
  float f = 1.0 / tan(fov / 2.0);
  float aspect = iResolution.x / iResolution.y;
  float proj_x = p.x / p.z * f;
  float proj_y = p.y / p.z * f * aspect;
  float proj_z = p.z / (2.0 * distance);

  // Output to shader pipeline.
  gl_Position = vec4(proj_x, proj_y, proj_z, 1);
  vpoint = point;
}")

;;; The fragment shader maps the texture onto the cube.
(def fragment-moon "
#version 130

#define PI 3.1415926535897932384626433832795

uniform sampler2D moon;
in vec3 vpoint;
out vec4 fragColor;

vec2 lonlat(vec3 p)
{
  float lon = atan(p.x, -p.z) / (2.0 * PI) + 0.5;
  float lat = atan(p.y, length(p.xz)) / PI + 0.5;
  return vec2(lon, lat);
}

vec3 color(vec2 lonlat)
{
  return texture(moon, lonlat).rgb;
}

void main()
{
  fragColor = vec4(color(lonlat(vpoint)).rgb, 1);
}")

;; We compile and link the shaders.
(do
  (def vertex-shader-moon (make-shader vertex-moon GL30/GL_VERTEX_SHADER))
  (def fragment-shader-moon (make-shader fragment-moon GL30/GL_FRAGMENT_SHADER))
  (def program-moon (make-program vertex-shader-moon fragment-shader-moon)))

;; We need to set up the memory layout again.
(do
  (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program-moon "point") 3
                              GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
  (GL20/glEnableVertexAttribArray 0))

;; ### Rendering the cube
;;
;; This shader program requires setup of several uniforms and a texture.
(do
  (GL20/glUseProgram program-moon)
  (GL20/glUniform2f (GL20/glGetUniformLocation program-moon "iResolution")
                    window-width window-height)
  (GL20/glUniform1f (GL20/glGetUniformLocation program-moon "fov") (to-radians 25.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-moon "alpha") (to-radians 30.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-moon "beta") (to-radians -20.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-moon "distance") 10.0)
  (GL20/glUniform1i (GL20/glGetUniformLocation program-moon "moon") 0)
  (GL13/glActiveTexture GL13/GL_TEXTURE0)
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture-color))

;; We enable back face culling to only render the front faces of the cube.
;; Then we clear the window and render the cube.
(do
  (GL11/glEnable GL11/GL_CULL_FACE)
  (GL11/glCullFace GL11/GL_BACK)
  (GL11/glClearColor 0.0 0.0 0.0 1.0)
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
  (GL11/glDrawElements GL11/GL_QUADS (count indices-cube) GL11/GL_UNSIGNED_INT 0)
  (screenshot))

;; This looks interesting but it is not a good approximation of the moon.

;; ### Finishing up
;;
;; To finish up we delete the vertex data for the cube.
(teardown-vao vao-cube)

;; ## Approximating a sphere
;;
;; ### Creating the vertex data
;;
;; First we partition the vertex data and convert the triplets to 8 Fastmath vectors.
(def points
  (map #(apply vec3 %)
       (partition 3 vertices-cube)))
points

;; Then we use the index array to get the coordinates of the first corner of each face resulting in 6 Fastmath vectors.
(def corners
  (map (fn [[i _ _ _]] (nth points i))
       (partition 4 indices-cube)))
corners

;; We get the first spanning vector of each face by subtracting the second corner from the first.
(def u-vectors
  (map (fn [[i j _ _]] (sub (nth points j) (nth points i)))
       (partition 4 indices-cube)))
u-vectors

;; We get the second spanning vector of each face by subtracting the fourth corner from the first.
(def v-vectors
  (map (fn [[i _ _ l]] (sub (nth points l) (nth points i)))
       (partition 4 indices-cube)))
v-vectors

;; We can now use vector math to subsample the faces and project the points onto a sphere by normalizing the vectors and multiplying with the moon radius.
(defn sphere-points [n c u v]
  (for [j (range (inc n)) i (range (inc n))]
       (mult (normalize (add c (add (mult u (/ i n)) (mult v (/ j n))))) radius)))
;; Subdividing once results in 9 corners for a cube face.
(sphere-points 2 (nth corners 0) (nth u-vectors 0) (nth v-vectors 0))

;; We also need a function to generate the indices for the quads.
(defn sphere-indices [n face]
  (for [j (range n) i (range n)]
       (let [offset (+ (* face (inc n) (inc n)) (* j (inc n)) i)]
         [offset (inc offset) (+ offset n 2) (+ offset n 1)])))
;; Subdividing once results in 4 quads for a cube face.
(sphere-indices 2 0)

;; ### Rendering a coarse approximation of the sphere.
;;
;; We subdivide once (n=2) and create a VAO with the data.
(do
  (def n 2)
  (def vertices-sphere (float-array (flatten (map (partial sphere-points n)
                                                  corners u-vectors v-vectors))))
  (def indices-sphere (int-array (flatten (map (partial sphere-indices n) (range 6)))))
  (def vao-sphere (setup-vao vertices-sphere indices-sphere)))

;; The layout needs to be configured again.
(do
  (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program-moon "point") 3
                              GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
  (GL20/glEnableVertexAttribArray 0))

;; The distance needs to be increased, because the points are on a sphere with the radius of the moon.
(GL20/glUniform1f (GL20/glGetUniformLocation program-moon "distance") (* radius 10.0))

;; Rendering the mesh now results in a better approximation of a sphere.
(do
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
  (GL11/glDrawElements GL11/GL_QUADS (count indices-sphere) GL11/GL_UNSIGNED_INT 0)
  (screenshot))

(teardown-vao vao-sphere)

;; ### Rendering a fine approximation of the sphere.
;;
;; To get a high quality approximation we subdivide more and create a VAO with the data. (do
(do
  (def n2 16)
  (def vertices-sphere-high (float-array (flatten (map (partial sphere-points n2) corners u-vectors v-vectors))))
  (def indices-sphere-high (int-array (flatten (map (partial sphere-indices n2) (range 6)))))
  (def vao-sphere-high (setup-vao vertices-sphere-high indices-sphere-high)))

;; We set up the vertex layout again.
(do
  (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program-moon "point") 3
                              GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
  (GL20/glEnableVertexAttribArray 0))

;; Rendering the mesh now results in a spherical mesh with a texture.
(do
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
  (GL11/glDrawElements GL11/GL_QUADS (count indices-sphere-high) GL11/GL_UNSIGNED_INT 0)
  (screenshot))

(GL20/glDeleteProgram program-moon)

;; ## Adding ambient and diffuse reflection
;;
;; In order to introduce lighting we add ambient and diffuse lighting to the fragment shader.
;; We use the ambient and diffuse lighting from the [Phong shading model](https://learnopengl.com/Lighting/Basic-Lighting).
;;
;; * The ambient light is a constant value.
;; * The diffuse light is calculated using the dot product of the light vector and the normal vector.
(def fragment-moon-diffuse "
#version 130

#define PI 3.1415926535897932384626433832795

uniform vec3 light;
uniform float ambient;
uniform float diffuse;
uniform sampler2D moon;
in vec3 vpoint;
out vec4 fragColor;

vec2 lonlat(vec3 p)
{
  float lon = atan(p.x, -p.z) / (2.0 * PI) + 0.5;
  float lat = atan(p.y, length(p.xz)) / PI + 0.5;
  return vec2(lon, lat);
}

vec3 color(vec2 lonlat)
{
  return texture(moon, lonlat).rgb;
}

void main()
{
  float phong = ambient + diffuse * max(0.0, dot(light, normalize(vpoint)));
  fragColor = vec4(color(lonlat(vpoint)) * phong, 1);
}")

;; We reuse the vertex shader from the previous example and the new fragment shader.
(do
  (def vertex-shader-diffuse (make-shader vertex-moon GL30/GL_VERTEX_SHADER))
  (def fragment-shader-diffuse (make-shader fragment-moon-diffuse GL30/GL_FRAGMENT_SHADER))
  (def program-diffuse (make-program vertex-shader-diffuse fragment-shader-diffuse)))

;; We set up the vertex data layout again.
(do
  (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program-diffuse "point") 3
                              GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
  (GL20/glEnableVertexAttribArray 0))

;; A normalized light vector is defined.
(def light (normalize (vec3 -1 0 -1)))

;; Before rendering we need to set up the various uniform values.
(do
  (GL20/glUseProgram program-diffuse)
  (GL20/glUniform2f (GL20/glGetUniformLocation program-diffuse "iResolution")
                    window-width window-height)
  (GL20/glUniform1f (GL20/glGetUniformLocation program-diffuse "fov") (to-radians 20.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-diffuse "alpha") (to-radians 0.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-diffuse "beta") (to-radians 0.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-diffuse "distance") (* radius 10.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-diffuse "ambient") 0.0)
  (GL20/glUniform1f (GL20/glGetUniformLocation program-diffuse "diffuse") 1.6)
  (GL20/glUniform3f (GL20/glGetUniformLocation program-diffuse "light")
                    (light 0) (light 1) (light 2))
  (GL20/glUniform1i (GL20/glGetUniformLocation program-diffuse "moon") 0)
  (GL13/glActiveTexture GL13/GL_TEXTURE0)
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture-color))

;; Finally we are ready to render the mesh with diffuse shading.
(do
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
  (GL11/glDrawElements GL11/GL_QUADS (count indices-sphere-high) GL11/GL_UNSIGNED_INT 0)
  (screenshot))

;; Afterwards we delete the shader program.
(GL20/glDeleteProgram program-diffuse)

;; ## Using normal mapping
;; ### Load elevation data into texture
;;
;; In the final section we also want to add normal mapping in order to get realistic shading of craters.
;;
;; The lunar elevation data is downloaded from NASA's website.
(do
  (def moon-ldem "src/opengl_visualization/ldem_4.tif")
  (when (not (.exists (io/file moon-ldem)))
    (download "https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/ldem_4.tif"
              moon-ldem)))

;; The image is read using ImageIO and the floating point elevation data is extracted.
(do
  (def ldem (ImageIO/read (io/file moon-ldem)))
  (def ldem-raster (.getRaster ldem))
  (def ldem-width (.getWidth ldem))
  (def ldem-height (.getHeight ldem))
  (def ldem-pixels (float-array (* ldem-width ldem-height)))
  (do (.getPixels ldem-raster 0 0 ldem-width ldem-height ldem-pixels) nil)
  (def resolution (/ (* 2.0 PI radius) ldem-width))
  [ldem-width ldem-height]
  )

;; The floating point pixel data is converted into a texture
(do
  (def texture-ldem (GL11/glGenTextures))
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture-ldem)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT)
  (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL30/GL_R32F ldem-width ldem-height 0
                     GL11/GL_RED GL11/GL_FLOAT ldem-pixels))

;; ### Create shader program with normal mapping
;;
;; We reuse the vertex shader from the previous section.
;;
;; The fragment shader this time is more involved.
;;
;; * A horizon matrix with normal, tangent, and bitangent vectors is computed.
;; * The elevation is sampled in four directions from the current 3D point.
;; * The elevation values are used to create two surface vectors.
;; * The cross product of the surface vectors is computed and normalized to get the normal vector.
;; * This perturbed normal vector is now used to compute diffuse lighting.
(def fragment-normal "
#version 130

#define PI 3.1415926535897932384626433832795

uniform vec3 light;
uniform float ambient;
uniform float diffuse;
uniform float resolution;
uniform sampler2D moon;
uniform sampler2D ldem;
in vec3 vpoint;
in mat3 horizon;
out vec4 fragColor;

vec3 orthogonal_vector(vec3 n)
{
  vec3 b;
  if (abs(n.x) <= abs(n.y)) {
    if (abs(n.x) <= abs(n.z))
      b = vec3(1, 0, 0);
    else
      b = vec3(0, 0, 1);
  } else {
    if (abs(n.y) <= abs(n.z))
      b = vec3(0, 1, 0);
    else
      b = vec3(0, 0, 1);
  };
  return normalize(cross(n, b));
}

mat3 oriented_matrix(vec3 n)
{
  vec3 o1 = orthogonal_vector(n);
  vec3 o2 = cross(n, o1);
  return mat3(n, o1, o2);
}

vec2 lonlat(vec3 p)
{
  float lon = atan(p.x, -p.z) / (2.0 * PI) + 0.5;
  float lat = atan(p.y, length(p.xz)) / PI + 0.5;
  return vec2(lon, lat);
}

vec3 color(vec2 lonlat)
{
  return texture(moon, lonlat).rgb;
}

float elevation(vec3 p)
{
  return texture(ldem, lonlat(p)).r;
}

vec3 normal(mat3 horizon, vec3 p)
{
  vec3 pl = p + horizon * vec3(0, -1,  0) * resolution;
  vec3 pr = p + horizon * vec3(0,  1,  0) * resolution;
  vec3 pu = p + horizon * vec3(0,  0, -1) * resolution;
  vec3 pd = p + horizon * vec3(0,  0,  1) * resolution;
  vec3 u = horizon * vec3(elevation(pr) - elevation(pl), 2 * resolution, 0);
  vec3 v = horizon * vec3(elevation(pd) - elevation(pu), 0, 2 * resolution);
  return normalize(cross(u, v));
}

void main()
{
  mat3 horizon = oriented_matrix(normalize(vpoint));
  float phong = ambient + diffuse * max(0.0, dot(light, normal(horizon, vpoint)));
  fragColor = vec4(color(lonlat(vpoint)).rgb * phong, 1);
}")

;; We reuse the vertex shader from the previous example and the new fragment shader.
(do
  (def vertex-shader-normal (make-shader vertex-moon GL30/GL_VERTEX_SHADER))
  (def fragment-shader-normal (make-shader fragment-normal GL30/GL_FRAGMENT_SHADER))
  (def program-normal (make-program vertex-shader-normal fragment-shader-normal)))

;; We set up the vertex data layout again.
(do
  (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program-normal "point") 3
                              GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
  (GL20/glEnableVertexAttribArray 0))

;; Apart from the uniform values we also need to set up two textures this time: the color texture and the elevation texture.
(do
  (GL20/glUseProgram program-normal)
  (GL20/glUniform2f (GL20/glGetUniformLocation program-normal "iResolution")
                    window-width window-height)
  (GL20/glUniform1f (GL20/glGetUniformLocation program-normal "fov") (to-radians 20.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-normal "alpha") (to-radians 0.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-normal "beta") (to-radians 0.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-normal "distance") (* radius 10.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-normal "resolution") resolution)
  (GL20/glUniform1f (GL20/glGetUniformLocation program-normal "ambient") 0.0)
  (GL20/glUniform1f (GL20/glGetUniformLocation program-normal "diffuse") 1.6)
  (GL20/glUniform3f (GL20/glGetUniformLocation program-normal "light")
                    (light 0) (light 1) (light 2))
  (GL20/glUniform1i (GL20/glGetUniformLocation program-normal "moon") 0)
  (GL20/glUniform1i (GL20/glGetUniformLocation program-normal "ldem") 1)
  (GL13/glActiveTexture GL13/GL_TEXTURE0)
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture-color)
  (GL13/glActiveTexture GL13/GL_TEXTURE1)
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture-ldem))

;; Finally we are ready to render the mesh with normal mapping.
(do
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
  (GL11/glDrawElements GL11/GL_QUADS (count indices-sphere-high) GL11/GL_UNSIGNED_INT 0)
  (screenshot))

;; Afterwards we delete the shader program and the vertex data.
(GL20/glDeleteProgram program-normal)
(teardown-vao vao-sphere-high)

;; And the textures.
(GL11/glDeleteTextures texture-color)
(GL11/glDeleteTextures texture-ldem)

;; ## Finalizing GLFW
;;
;; When we are finished, we destroy the window.
(GLFW/glfwDestroyWindow window)

;; Finally we terminate use of the GLFW library.
(GLFW/glfwTerminate)

;; I hope you liked this 3D graphics example.
;;
;; Note that in practise you will
;;
;; * use higher resolution data and map the data onto texture tiles
;; * generate textures containing normal maps offline
;; * create a multiresolution map
;; * use tessellation to increase the mesh resolution
;; * use elevation data to deform the mesh
;;
;; Thanks to [Timothy Pratley](https://timothypratley.blogspot.com/p/httpswww.html) for helping getting this post online.
