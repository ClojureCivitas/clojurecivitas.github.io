(ns instaparse.grammar-of-physics.rigid-body
  (:require [instaparse.core :as insta]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]))

(defonce state (atom {}))

(def physics-grammar
  "composite    = stmt*
<stmt>        = expr <';'>
<expr>        = primitive | fn | body | constraint | scope | label
scope         = <'{'> composite <'}'> attrs?
body          = shape attrs?
  shape       = ('rectangle' | 'circle' | 'polygon' | 'trapezoid' | 'fromVertices') primitive*
<primitive>   = number | symbol | call
constraint    = node edge node attrs?
  <node>      = (symbol | body)
  edge        = rigid | spring | pin | rope
  rigid       = <'--'>
  spring      = <'%%'>
  pin         = <'-o-' | '-.-'>
  rope        = <'~~'>
attrs         = <'['> (flag | attr)* <']'>
flag          = symbol
attr          = symbol <'='> (primitive | vector)
  vector      = <'['> primitive* <']'>
label         = symbol <':'> expr
fn            = <'('> params <'=>'> expr <')'>
params        = symbol*
call          =  <'('> symbol expr* <')'>
symbol        = #'[a-zA-Z#_$*+/-][a-zA-Z0-9_$*+/-]*'
number        = #'[0-9]+(\\.[0-9]+)?'
")

(def examples
  ["ramp: rectangle 360 100 600 10 [isStatic, angle=5, color=#5881D8];
platform-y: 300;
platform: rectangle 550 platform-y 400 10 [isStatic, color=#62B132];
domino: (x, y => rectangle x y 10 50);
(grid 9 1 (i, j => (domino (+ 350 (* i 50)) (- platform-y (* (+ j 1) 100)))));
ball: (=> circle 100 20 20 [friction=0, frictionAir=0, inertia=Infinity, color=#91DC47]);
(repeat 5 ball);
(grid 6 6 (i, j => polygon 400 400 (+ 3 i) 10 [friction=0, frictionAir=0]));
box: rectangle 100 200 30 30 [color=#8FB5FE, isStatic=true];
hex: (c => polygon 150 300 6 20 [color=c]);
a: (hex #8FB5FE);
b: (hex #B5EAD7);
c: (hex #C7CEEA);
d: (hex #FF9AA2);
e: (hex #FFB7B2);
f: (hex #FFDAC1);
g: (hex #E2F0CB);
a -- b [length=60];
a -- c [length=60];
a -- d [length=60];
a -- e [length=60];
a -- f [length=60];
a -- g [length=60];
a %% box [length=100];
"
   ;; Bridge with Springs
   "left: rectangle 100 250 200 20 [isStatic, color=#8FB5FE];
right: rectangle 700 250 200 20 [isStatic, color=#8FB5FE];
left-bridge: rectangle 300 200 80 40 [color=#FFD166];
right-bridge: rectangle 500 200 80 40 [color=#FFD166];
left-bridge -- left [color=#073B4C];
right-bridge -- right [color=#073B4C];
left-bridge %% right-bridge [color=#06D6A0, length=50];
(repeat 7 (=> polygon 400 100 6 30 [color=#91DC47]));

"
   ;; Mirrors
   "
laser: (x, y => {rectangle 0 0 100 20 [isStatic, color=#e63946];
                 lens: rectangle 50 0 10 10 [isStatic, color=#22223b];}
                [x=x, y=y]);
l1: (laser 100 250 0);
splitter: (x, y, phi => rectangle x y 50 10 [isStatic, isSensor, angle=phi, color=#f4a261]);
s1: (splitter 250 250 45);
mirror: (x, y, phi => rectangle x y 50 5 [isStatic, angle=phi, color=#457b9d]);
m1: (mirror 400 250 -45);
m2: (mirror 250 100 -45);
s2: (splitter 400 100 45);
detector: (x, y, phi => rectangle x y 70 10 [isStatic, isSensor, angle=phi, color=#2a9d8f]);
d1: (detector 500 100 90);
lens -- s1 [color=green];
s1 -- m1 [color=cyan];
s1 -- m2 [color=blue];
m1 -- s2 [color=cyan];
m2 -- s2 [color=blue];
s2 -- d1 [color=green];
dust: (=> polygon 10 10 3 3 [restitution=1, friction=0, mass=0, frictionAir=0, inertia=Infinity, frictionStatic=0]);
(repeat 200 dust);
"
   ;; Newton's Cradle
   "ceiling: rectangle 400 100 500 20 [isStatic, color=#8FB5FE];
(grid 6 1 (i, j => circle (+ 200 (* i 50)) 150 25 [friction=0, frictionAir=0, inertia=Infinity, color=#91DC47] -- ceiling [length=200, pointB=[(- (* i 50) 150) 0]]));"

   ;; Seesaw
   "fulcrum: rectangle 400 400 20 100 [isStatic, color=#8FB5FE, isSensor];
plank: rectangle 400 290 400 20 [color=#FFD166];
plank -o- fulcrum;
(repeat 7 (=> polygon 400 100 6 30 [color=#91DC47]));
"])

(def body-shapes
  {:rectangle [:x :y :width :height]
   :circle [:x :y :radius]
   :polygon [:x :y :sides :radius]
   :trapezoid [:x :y :width :height :slope]
   :fromVertices [:x :y :vertices]})

(declare eval-expr)

(def ast-simplifications
  "AST simplifications don't require an environment for evaluation"
  {:symbol symbol
   :number edn/read-string
   :edge first
   :attr (fn [k v] [(keyword k) v])
   :flag (fn [k] [(keyword k) true])
   :params vector
   :shape vector
   :fn (fn [params body]
         (fn [env & args]
           (eval-expr (update env :labels merge (zipmap params args)) body)))})

(def entity-plural
  {:body :bodies
   :constraint :constraints
   :composite :composites})

(defn perculate
  "Entities are placed in the environment (a composite)"
  [env {:as entity :keys [type]}]
  [(update env (entity-plural type) (fnil conj []) entity) entity])

(defn update-last [v f & args]
  (apply update v (dec (count v)) f args))

(declare eval-composite)

(defn eval-map [env kvs]
  (reduce (fn [[env m] [k arg]]
            (let [[env v] (eval-expr env arg)]
              [env (assoc m k v)]))
          [env {}]
          kvs))

(defn eval-vector [env xs]
  (reduce (fn [[env vs] v]
            (let [[env v] (eval-expr env v)]
              [env (conj vs v)]))
          [env []]
          xs))

(def core
  "Built in functions"
  {'+ (fn [env & args]
        [env (apply + args)])
   '- (fn [env & args]
        [env (apply - args)])
   '* (fn [env & args]
        [env (apply * args)])
   '/ (fn [env & args]
        [env (apply / args)])
   'repeat (fn [env n f]
             (loop [n n
                    [env body] [env nil]]
               (if (<= n 0)
                 [env body]
                 (recur (dec n) (f env)))))
   'grid (fn [env nx ny f]
           (loop [[env body] [env nil]
                  [x y] [0 0]]
             (if (>= y ny)
               [env body]
               (recur (f env x y)
                      (if (< (inc x) nx)
                        [(inc x) y]
                        [0 (inc y)])))))})

(defn eval-expr
  "Evaluates expr in env and returns [env result]
   such that the environment may be updated as a side effect"
  [env expr]
  (cond
    (symbol? expr) [env (or (get-in env [:labels expr]) (str expr))]
    (vector? expr)
    (let [[tag & xs] expr]
      (case tag
        :body (let [[[shape & args] attrs] xs
                    shape (keyword shape)
                    params (get body-shapes shape)
                    [env args] (eval-map env (map vector params args))
                    [env attrs] (eval-map env (rest attrs))
                    body (into {:type :body
                                :shape shape
                                :label (gensym)}
                               [args attrs])]
                (perculate env body))
        :constraint (let [[from constraint to attrs] xs
                          [env attrs] (eval-map env (rest attrs))
                          [env from] (if (symbol? from)
                                       [env from]
                                       (let [[env body] (eval-expr env from)]
                                         [env (:label body)]))
                          [env to] (if (symbol? to)
                                     [env to]
                                     (let [[env body] (eval-expr env to)]
                                       [env (:label body)]))]
                      (perculate env (into {:type :constraint
                                            :from from
                                            :to to
                                            :constraint constraint}
                                           attrs)))
        :composite (eval-composite env xs)
        :scope (let [[stmts attrs] xs
                     [env attrs] (eval-map env (rest attrs))
                     [result-env _result] (eval-composite (apply dissoc env (vals entity-plural)) stmts)
                     composite (merge (select-keys result-env (cons :type (vals entity-plural)))
                                      attrs)]
                 (perculate env composite))
        :label (let [[label subexpr] xs
                     [env v] (eval-expr env subexpr)
                     env (if (map? v)
                           (update env (get entity-plural (:type v)) update-last assoc :label label)
                           (assoc-in env [:labels label] v))]
                 [env v])
        :vector (let [[env [x y]] (eval-vector env xs)]
                  [env {:x x, :y y}])
        :fn (let [[params subexpr] xs]
              [env (fn [env & args]
                     ;; extend the environment with param bindings
                     (eval-expr (update env :labels merge (zipmap params args))
                                subexpr))])
        :call (let [[fn-name & args] xs
                    fn-val (or (get-in env [:labels fn-name])
                               (get core fn-name)
                               (throw (ex-info (str "Function not found: " fn-name)
                                               {:id ::function-not-found
                                                :fn-name fn-name
                                                :env env})))
                    [env arg-vals] (eval-vector env args)
                    [fn-env result] (apply fn-val env arg-vals)
                    ;; only keep entities created, not labels or params
                    env (merge env (select-keys fn-env (vals entity-plural)))]
                [env result])
        (throw (ex-info (str "Unexpected tag " tag) {:id ::unexpected-node
                                                     :expr expr}))))
    :else [env expr]))


(defn eval-composite
  ([env stmts]
   (reduce (fn [[env _result] stmt]
             (eval-expr env stmt))
           [(assoc env :type :composite) nil]
           stmts)))

;; --- MatterJS world setup ---

(def world-width 800)
(def world-height 450)

(defn deg2rad [a]
  (/ (* a js/Math.PI) 180))

(defn create-bodies
  "Hydrates a data representation of a world into `parent`.
   Returns an index of labels to body objects suitable for creating constraints with."
  [parent {:keys [bodies composites]} index]
  (let [index (reduce (fn [index {:as body :keys [shape label]}]
                        (assert (contains? body-shapes shape) (str "Unknown body shape: " shape))
                        (let [params (body-shapes shape)
                              args (map body params)
                              remaining-props (apply dissoc body :shape params)
                              m {:restitution 0.9
                                 :friction    0.1}
                              props (merge m remaining-props)
                              props (cond-> props
                                      (:angle props) (update :angle deg2rad)
                                      (:color props) (assoc-in [:render :fillStyle] (:color props)))
                              ctor (aget js/Matter "Bodies" (name shape))
                              b (doto (apply ctor (concat args [(clj->js props)]))
                                  ;;(-> (.-id) (set! (name id)))
                                  (-> (.-shape) (set! (name shape)))
                                  (->> (js/Matter.Composite.add parent)))]
                          (if label
                            (assoc index label b)
                            index)))
                      index
                      bodies)
        index (reduce (fn [index composite]
                        (let [c (js/Matter.Composite.create (clj->js (dissoc composite :bodies :composites :constraints)))
                              {:keys [x y angle scale]} composite
                              index (create-bodies c composite index)]
                          (when (or x y) (js/Matter.Composite.translate c #js {:x (or x 0) :y (or y 0)}))
                          (when angle (js/Matter.Composite.rotate c (deg2rad angle)))
                          (when scale (js/Matter.Composite.scale c scale))
                          (js/Matter.Composite.add parent c)
                          index))
                      index
                      composites)]
    index))

(defn create-constraints
  "Bodies need to exist before constraints can be added"
  [parent {:as props :keys [type]} index]
  (case type
    :composite (let [{:keys [composites constraints]} props]
                 (doseq [child (concat composites constraints)]
                   ;; TODO: not really the right parent, we need to find the composite in the parent
                   (create-constraints parent child index))
                 parent)
    :constraint (let [{:keys [from constraint to]} props
                      bodyA (get index (if (map? from) (:label from) from))
                      bodyB (get index (if (map? to) (:label to) to))
                      m (case constraint
                          :rigid {:stiffness 1}
                          :spring {:stiffness 0.01}
                          :pin {:stiffness 1, :length 0}
                          :rope {:damping 0.7, :stiffness 0.3})
                      props (-> (merge m props)
                                (dissoc :from :to :constraint)
                                (assoc :bodyA bodyA
                                       :bodyB bodyB)
                                (cond->
                                 (:color props) (assoc-in [:render :strokeStyle] (:color props))))]
                  (when (and bodyA bodyB)
                    (doto (js/Matter.Constraint.create (clj->js props))
                      (->> (js/Matter.Composite.add parent))))
                  parent)))

(defn create-world [w x]
  (let [index (create-bodies w x {})]
    (create-constraints w x index)))

;; Add some walls to keep everything inside the view
(defn add-boundaries [world width height]
  (let [options (clj->js {:isStatic    true
                          :restitution 0.9
                          :friction    0.1
                          :label       "Wall"})
        x-mid (/ width 2.0)
        y-mid (/ height 2.0)
        thickness y-mid
        t2 (/ thickness 2.0)]
    (doseq [[x y rw rh] [[(- 0 t2) y-mid thickness height]
                         [(+ width t2) y-mid thickness height]
                         [x-mid (- 0 t2) width thickness]
                         [x-mid (+ height t2) width thickness]]]
      (js/Matter.World.add world
                           (js/Matter.Bodies.rectangle x y rw rh options)))))

(defn stop! []
  (let [{:keys [render runner world engine]} @state]
    (when render
      (js/Matter.Render.stop render)
      (some-> render (.-canvas) (.remove))
      (set! (.-canvas render) nil)
      (set! (.-context render) nil)
      (set! (.-textures render) #js {}))
    (when runner (js/Matter.Runner.stop runner))
    (when world (js/Matter.World.clear world))
    (when engine (js/Matter.Engine.clear engine)))
  :stopped)

(def element-id "matter-canvas-container")

(defn start! [world-data]
  (stop!)
  (let [engine (js/Matter.Engine.create #js {:gravity #js {:y 1}})
        world (.-world engine)
        element (or (js/document.getElementById element-id)
                    (throw (ex-info (str "Element not found: " element-id)
                                    {:id ::element-not-found
                                     :element-id element-id})))
        client-width (.-clientWidth element)
        scale (/ world-width client-width)
        client-height (/ world-height scale)
        render (js/Matter.Render.create #js {:element element
                                             :engine engine
                                             :options #js {:width client-width
                                                           :height client-height
                                                           :hasBounds  true
                                                           :wireframes false
                                                           :background "#f8fafc"}})
        mouse (doto (js/Matter.Mouse.create (.-canvas render))
                (js/Matter.Mouse.setScale #js {:x scale :y scale}))
        mouse-constraint (js/Matter.MouseConstraint.create engine #js {:mouse mouse})
        runner (js/Matter.Runner.create)]
    (js/Matter.World.add world mouse-constraint)
    (set! (.. render -bounds -min -x) 0)
    (set! (.. render -bounds -min -y) 0)
    (set! (.. render -bounds -max -x) world-width)
    (set! (.. render -bounds -max -y) (inc world-height))
    (swap! state assoc
           :engine engine
           :world world
           :render render
           :runner runner
           :mouse mouse)
    (add-boundaries world world-width world-height)
    (create-world world world-data)
    ;;(rematter/set-bounds render world-width world-height)
    (js/Matter.Runner.run runner engine)
    (js/Matter.Render.run render)
    (js/console.log "Started" world))
  :started)

(defn $ [id]
  (js/document.getElementById id))

(defonce resizer
  (js/window.addEventListener
   "resize"
   (fn []
     (let [{:keys [render mouse]} @state
           w (.-clientWidth ($ element-id))
           scale (/ world-width w)
           h (/ world-height scale)]
       (-> render .-options .-width (set! w))
       (-> render .-options .-height (set! h))
       (-> render .-canvas .-width (set! w))
       (-> render .-canvas .-height (set! h))
       (js/Matter.Mouse.setScale mouse (clj->js {:x scale
                                                 :y scale}))
       (js/Matter.Render.setPixelRatio render js/window.devicePixelRatio)))))

(defn report-error [result]
  (let [e ($ "parse-error")]
    (if (and result (not= result ""))
      (do
        (set! (.-textContent e) result)
        (set! (.. e -style -display) "block"))
      (do
        (set! (.-textContent e) "")
        (set! (.. e -style -display) "none")))))

(defn report-ast [result]
  (set! (.-textContent ($ "ast")) result))

(defn parse! []
  (let [result ((:parser @state) (.-value ($ "program")))]
    (if (insta/failure? result)
      (report-error (with-out-str (println (insta/get-failure result))))
      (try
        (report-ast (with-out-str (pprint/pprint result)))
        (->> (insta/transform ast-simplifications result)
             (eval-composite {})
             (first)
             (start!))
        (report-error nil)
        (catch :default ex
          (report-error (str ex)))))))

(set! (.-explode js/window)
      (fn []
        (doseq [b (js/Matter.Composite.allBodies (:world @state))
                :when (not (.-isStatic b))]
          (js/Matter.Body.setVelocity b (clj->js {:x (- (rand 50) 25),
                                                  :y (- (rand 50) 25)})))))

(set! (.-rotate js/window)
  (fn []
    (let [center-x (/ world-width 2)
          center-y (/ world-height 2)
          angle (/ js/Math.PI 12)]
      (js/Matter.Composite.rotate (:world @state) angle (clj->js {:x center-x :y center-y})))))

(set! (.-evalProgram js/window) parse!)

(defn set-example! [n]
  (set! (.-value ($ "program")) (examples n)))

(set! (.-loadExample js/window)
      (fn [n]
        (set-example! n)
        (parse!)))

(defn report-grammar-error [result]
  (let [e ($ "grammar-error")]
    (if (and result (not= result ""))
      (do
        (set! (.-textContent e) result)
        (set! (.. e -style -display) "block"))
      (do
        (set! (.-textContent e) "")
        (set! (.. e -style -display) "none")))))

(defn set-grammar! [grammar]
  (try
    (swap! state assoc :parser (insta/parser grammar :auto-whitespace :comma))
    (report-grammar-error nil)
    (catch :default ex
      (report-grammar-error (str ex)))))

(set! (.-instaparse js/window)
      (fn []
        (set-grammar! (.-value ($ "grammar")))
        (parse!)))

(defn reset-grammar! []
  (set! (.-value ($ "grammar")) physics-grammar)
  (set-grammar! physics-grammar))

(set! (.-resetGrammar js/window) reset-grammar!)

;; Kick it all off

(set-example! 0)
(reset-grammar!)
(parse!)
