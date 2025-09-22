^{:kindly/hide-code true
  :clay             {:title  "OpenGL Visualization with LWJGL"
                     :external-requirements ["Xorg"]
                     :quarto {:author   [:janwedekind]
                              :type     :post
                              :date     "2025-09-09"
                              :category :clojure
                              :tags     [:visualization]}}}
(ns opengl-visualization.main
    (:require [clojure.java.io :as io]
              [clojure.math :refer (to-radians)])
    (:import [javax.imageio ImageIO]
             [java.awt.image BufferedImage]
             [org.lwjgl BufferUtils]
             [org.lwjgl.glfw GLFW]
             [org.lwjgl.opengl GL GL11 GL13 GL15 GL20 GL30]
             [org.lwjgl.stb STBImage STBImageWrite]))

;; ## Getting dependencies
;;
;; We need to get some things and we can use add-libs to fetch dependencies

;; ```Clojure
;; (add-libs {'org.lwjgl/lwjgl                      {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl$natives-linux        {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-opengl               {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-opengl$natives-linux {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-glfw                 {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-glfw$natives-linux   {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-stb                  {:mvn/version "3.3.6"}
;;            'org.lwjgl/lwjgl-stb$natives-linux    {:mvn/version "3.3.6"}})
;; (require '[clojure.java.io :as io])
;; (import '[javax.imageio ImageIO]
;;         '[org.lwjgl BufferUtils]
;;         '[org.lwjgl.glfw GLFW]
;;         '[org.lwjgl.opengl GL GL11 GL15 GL20 GL30]
;;         '[org.lwjgl.stb STBImageWrite])
;; ```

;; ## Creating the window
;;
;; Next we choose the window width and height.
(def window-width 640)
(def window-height 480)

;; We define a function to create a temporary file name.
(defn tmpdir
  []
  (System/getProperty "java.io.tmpdir"))

(defn tmpname
  []
  (str (tmpdir) "/civitas-" (java.util.UUID/randomUUID) ".tmp"))

;; The following function is used to create screenshots for this article.
;; We read the pixels, write them to a temporary file using the STB library and then convert it to an ImageIO object.
(defn screenshot
  []
  (let [filename (tmpname)
        buffer   (java.nio.ByteBuffer/allocateDirect (* 4 window-width window-height))]
    (GL11/glReadPixels 0 0 window-width window-height GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
    (STBImageWrite/stbi_write_png filename window-width window-height 4 buffer (* 4 window-width))
    (-> filename io/file (ImageIO/read))))

;; We need to initialize the GLFW library.
(GLFW/glfwInit)

;; Now we create an invisible window.
;; You can create a visisble window if you want to by not setting the visibility hint to false.
(def window
  (do
    (GLFW/glfwDefaultWindowHints)
    (GLFW/glfwWindowHint GLFW/GLFW_DEPTH_BITS 24)
    (GLFW/glfwWindowHint GLFW/GLFW_STENCIL_BITS 8)
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
;; A simple test is to set a clear color, clear depth, and clear the window.
(do
  (GL11/glClearColor 1.0 0.5 0.25 1.0)
  (GL11/glClearDepth 0.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
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

;; The following code shows a simple vertex shader which passes through the vertex coordinates.
(def vertex-source "#version 130
in vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

;; In the fragment shader we use the pixel coordinates to output a color ramp.
;; The uniform variable iResolution will later be set to the window resolution.
(def fragment-source "#version 130
uniform vec2 iResolution;
out vec4 fragColor;
void main()
{
  fragColor = vec4(gl_FragCoord.xy / iResolution.xy, 0, 1);
}")

;; Let's compile the shaders and link the program.
(def vertex-shader (make-shader vertex-source GL20/GL_VERTEX_SHADER))
(def fragment-shader (make-shader fragment-source GL20/GL_FRAGMENT_SHADER))
(def program (make-program vertex-shader fragment-shader))

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
;; We define a vertex array object (VAO) which acts like a context for the vertex and index buffer.
(def vao (GL30/glGenVertexArrays))
(GL30/glBindVertexArray vao)

;; We define a vertex buffer object (VBO) which contains the vertex data.
(def vbo (GL15/glGenBuffers))
(GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
(def vertices-buffer (make-float-buffer vertices))
(GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices-buffer GL15/GL_STATIC_DRAW)

;; We also define an index buffer object (IBO) which contains the index data.
(def idx (GL15/glGenBuffers))
(GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER idx)
(def indices-buffer (make-int-buffer indices))
(GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER indices-buffer GL15/GL_STATIC_DRAW)

;; The data of each vertex is defined by 3 floats (x, y, z).
;; We need to specify the layout of the vertex buffer object so that OpenGL knows how to interpret it.
(do
  (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "point") 3 GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
  (GL20/glEnableVertexAttribArray 0))

;; ### Rendering the quad
;;
;; We select the program and define the uniform variable iResolution.
(do
  (GL20/glUseProgram program)
  (GL20/glUniform2f (GL20/glGetUniformLocation program "iResolution") window-width window-height))

;; Since the correct VAO is already bound, we are now ready to draw the quad.
(GL11/glDrawElements GL11/GL_QUADS (count indices) GL11/GL_UNSIGNED_INT 0)
(screenshot)

;; ### Finishing up
;;
;; We only delete the program since we are going to use the vertex buffer in the next example.
(GL20/glDeleteProgram program)

;; ## Rendering the Moon
;; ### Getting the NASA data
;;
;; Download lunar color image
(defn download [url target]
  (with-open [in (io/input-stream url)
              out (io/output-stream target)]
    (io/copy in out)))

(def moon-tif "src/opengl_visualization/lroc_color_poles_8k.tif")

(when (not (.exists (io/file moon-tif)))
  (download "https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/lroc_color_poles_8k.tif" moon-tif))

;; Use ImageIO to convert it to PNG
(def moon-png "src/opengl_visualization/lroc_color_poles_8k.png")
(when (not (.exists (io/file moon-png)))
  (ImageIO/write (ImageIO/read (io/file moon-tif)) "png" (io/file moon-png)))

;; ### Create a texture
;;
;; Loading the image
(do
  (def width (int-array 1))
  (def height (int-array 1))
  (def channels (int-array 1))
  (def buffer (STBImage/stbi_load moon-png width height channels 4)))

;; ### Set up the texture
(do
  (def texture (GL11/glGenTextures))
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
  (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA (aget width 0) (aget height 0)
                     0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT)
  (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
  (STBImage/stbi_image_free buffer))

;; ### Rendering the texture
(def vertex-tex "#version 130
in vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(def fragment-tex "#version 130
uniform vec2 iResolution;
uniform sampler2D moon;
out vec4 fragColor;
void main()
{
  fragColor = texture(moon, gl_FragCoord.xy / iResolution.xy);
}")

(def vertex-tex-shader (make-shader vertex-tex GL20/GL_VERTEX_SHADER))
(def fragment-tex-shader (make-shader fragment-tex GL20/GL_FRAGMENT_SHADER))
(def tex-program (make-program vertex-tex-shader fragment-tex-shader))

(do
  (GL20/glVertexAttribPointer (GL20/glGetAttribLocation tex-program "point") 3 GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
  (GL20/glEnableVertexAttribArray 0))

(do
  (GL20/glUseProgram tex-program)
  (GL20/glUniform2f (GL20/glGetUniformLocation tex-program "iResolution") window-width window-height)
  (GL20/glUniform1i (GL20/glGetUniformLocation tex-program "moon") 0)
  (GL13/glActiveTexture GL13/GL_TEXTURE0)
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture))

(GL11/glDrawElements GL11/GL_QUADS 4 GL11/GL_UNSIGNED_INT 0)
(screenshot)

;; ### Finishing up
(do
  (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers idx)
  (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers vbo)
  (GL30/glBindVertexArray 0)
  (GL15/glDeleteBuffers vao)
  (GL20/glDeleteProgram program))

;;; ## Render a 3D cube
;;;
;;; ### Create vertex data
(def vertices-cube
  (float-array [-1.0 -1.0 -1.0
                 1.0 -1.0 -1.0
                 1.0  1.0 -1.0
                -1.0  1.0 -1.0
                -1.0 -1.0  1.0
                 1.0 -1.0  1.0
                 1.0  1.0  1.0
                -1.0  1.0  1.0]))

;; The index array defines the order of the vertices.
(def indices-cube
  (int-array [0 1 2 3
              7 6 5 4
              0 3 7 4
              5 6 2 1
              3 2 6 7
              4 5 1 0]))

;; ### Initialize vertex buffer array
;;
;; We define a vertex array object (VAO) which acts like a context for the vertex and index buffer.
(def vao-cube (GL30/glGenVertexArrays))
(GL30/glBindVertexArray vao-cube)

;; We define a vertex buffer object (VBO) which contains the vertex data.
(def vbo-cube (GL15/glGenBuffers))
(GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-cube)
(def vertices-buffer-cube (make-float-buffer vertices-cube))
(GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices-buffer-cube GL15/GL_STATIC_DRAW)

;; We also define an index buffer object (IBO) which contains the index data.
(def idx-cube (GL15/glGenBuffers))
(GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER idx-cube)
(def indices-buffer-cube (make-int-buffer indices-cube))
(GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER indices-buffer-cube GL15/GL_STATIC_DRAW)

;;; ### Shader program mapping texture onto cube
;;;
;;; We first define a vertex shader, which takes cube coordinates, rotates, translates, and projects them.
(def vertex-moon "#version 130
uniform float fov;
uniform float alpha;
uniform float beta;
uniform float distance;
uniform vec2 iResolution;
in vec3 point;
out vec3 vpoint;
void main()
{
  // Rotate by alpha around y axis
  vec3 ps = vec3(point.x * cos(alpha) - point.z * sin(alpha), point.y, point.x * sin(alpha) + point.z * cos(alpha));
  // Rotate by beta around x axis
  vec3 pss = vec3(ps.x, ps.y * cos(beta) - ps.z * sin(beta), ps.y * sin(beta) + ps.z * cos(beta));
  // Translate
  vec3 psss = pss + vec3(0, 0, distance);
  // Projection
  float f = 1.0 / tan(fov / 2.0);
  float aspect = iResolution.x / iResolution.y;
  float proj_x = psss.x / psss.z * f;
  float proj_y = psss.y / psss.z * f * aspect;
  float proj_z = psss.z / (2.0 * distance);
  gl_Position = vec4(proj_x, proj_y, proj_z, 1);
  vpoint = point;
}")

;;; The fragment shader maps the texture onto the cube.
(def fragment-moon "#version 130
#define PI 3.1415926535897932384626433832795
uniform sampler2D moon;
in vec3 vpoint;
out vec4 fragColor;
void main()
{
  // Convert vpoint to lat, lon
  float lon = atan(vpoint.z, vpoint.x) / (2.0 * PI) + 0.5;
  float lat = atan(vpoint.y, length(vpoint.xz)) / PI + 0.5;
  fragColor = texture(moon, vec2(lon, lat));
}")

(def vertex-shader-moon (make-shader vertex-moon GL30/GL_VERTEX_SHADER))
(def fragment-shader-moon (make-shader fragment-moon GL30/GL_FRAGMENT_SHADER))
(def program-moon (make-program vertex-shader-moon fragment-shader-moon))

(do
  (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program-moon "point") 3 GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
  (GL20/glEnableVertexAttribArray 0))

;; ### Rendering the cube
;;
;; Set uniforms
(do
  (GL20/glUseProgram program-moon)
  (GL20/glUniform2f (GL20/glGetUniformLocation program-moon "iResolution") window-width window-height)
  (GL20/glUniform1f (GL20/glGetUniformLocation program-moon "fov") (to-radians 60.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-moon "alpha") (to-radians -30.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-moon "beta") (to-radians 20.0))
  (GL20/glUniform1f (GL20/glGetUniformLocation program-moon "distance") 5.0)
  (GL20/glUniform1i (GL20/glGetUniformLocation program-moon "moon") 0)
  (GL13/glActiveTexture GL13/GL_TEXTURE0)
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture))

;; Enable depth testing and render.
(do
  (GL11/glEnable GL11/GL_CULL_FACE)
  (GL11/glCullFace GL11/GL_BACK)
  (GL11/glClearColor 0.0 0.0 0.0 1.0)
  (GL11/glClearDepth 1.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (GL11/glDrawElements GL11/GL_QUADS (count indices-cube) GL11/GL_UNSIGNED_INT 0)
  (screenshot))

;; ### Finishing up
(do
  (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers idx-cube)
  (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers vbo-cube)
  (GL30/glBindVertexArray 0)
  (GL15/glDeleteBuffers vao))

;; ## Approximating a sphere


(GL20/glDeleteProgram program)
(GL11/glDeleteTextures texture)

;; ### Finalizing GLFW
;;
;; When we are finished, we destroy the window.
(GLFW/glfwDestroyWindow window)

;; Finally we terminate use of the GLFW library.
(GLFW/glfwTerminate)
