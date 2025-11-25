^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Infix Notation"
                     :quarto {:author   :kloimhardt
                              :type     :post
                              :sidebar  "emmy-fdg"
                              :description "Emmy can wear a different apparel should the occasion demand it."
                              :date     "2025-11-19"
                              :image    "fdg_ch01_ys.jpg"
                              :category :libs
                              :tags     [:emmy :infix :yamlscript :notation]}}}

(ns mentat-collective.emmy.fdg-ch01-ys
    (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                              time infinite? abs ref partial =])
    (:require [emmy.env :refer :all :exclude [D F->C]]
              [yamlscript.compiler :as ys]
              [scicloj.kindly.v4.api :as kindly]
              [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(def +++ identity)

^:kindly/hide-code
(def mul+ *)

^:kindly/hide-code
(def add+ +)

^:kindly/hide-code
(defn ysc [s]
  (ys/compile (str "!ys-0\n" s)))

^:kindly/hide-code
(defmacro ys [s]
  (list 'kindly/hide-code
        [(list 'kind/code s)
         (read-string (ysc s))]))

^:kindly/hide-code
(kind/hiccup
  [:div
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.emmy.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.cljs-ajax.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react-dom@18/umd/react-dom.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.reagent.js"}]
   [:script {:type "application/x-scittle" :src "scheme.cljc"}]])


^:kindly/hide-code
(kind/scittle
  '(require '[emmy.env :refer :all :exclude [D F->C]]))

^:kindly/hide-code
(def time first)

^:kindly/hide-code
(kind/scittle
  '(def time first))

^:kindly/hide-code
(kind/scittle
  '(def D partial))

;; The Clojure code below is taken from a [previous Civitas entry](https://clojurecivitas.github.io/mentat_collective/emmy/fdg_ch01.html) (the picture from [Emily's parser game](https://ifdb.org/viewgame?id=urxrv27t7qtu52lb)).

;; It is not necessary to understand what the Clojure code does. As this semantics does not matter here, it is rather the syntax, the notation, that should be compared and given attention to.


;; ## First Example

(kind/scittle
  '(defn Lfree [mass]
     (fn [[_ _ v]] (* 1/2 mass (square v)))))

;; The following infix notation indeed compiles to Clojure code and is equivalent to the above.

(ys "
defn LFree(mass):
  fn([_ _ v]): 1/2 * mass * square(v)
")

;; ## Another one

(kind/scittle
  '(defn sphere->R3 [R]
     (fn [[_ [theta phi]]]
       (up (* R (sin theta) (cos phi))
           (* R (sin theta) (sin phi))
           (* R (cos theta))))))

(ys "
defn sphere-to-R3(R):
  fn([_ [theta phi]]):
    up:
     R *: sin(theta) * cos(phi)
     R *: sin(theta) * sin(phi)
     R *: cos(theta)
")

;; The `*:` means nothing but normal multiplication.

;; ## Higher order functions

;; For the next infix, I introduce `call` (with obvious behaviour) and two aliases.

(do
  (defn call [f x] (f x))
  (def of call)
  (def at call))

(def D partial)

(ys "
defn F-to-C(F):
  fn(state):
    up:
     time: state
     F: state
     D(0).of(F).at(state) +:
       D(1).of(F).at(state) *
       velocity(state)
")

;; Again, the above `+:` is normal addition. With `of` and `at` like that, the above `D(0).of(F).at(state)` (which means "take the zeroth derivative of the function F at point state") translates into what are higher order functions in the Clojure version.

(kind/scittle
  '(defn F->C [F]
     (fn [state]
       (up (time state)
           (F state)
           (+ (((D 0) F) state)
              (* (((D 1) F) state)
                 (velocity state)))))))

;; ## Another one

(ys "
defn Lsphere(m R):
  compose:
    LFree: m
    F-to-C: sphere-to-R3(R)
")

(kind/scittle
  '(defn Lsphere [m R]
     (compose (Lfree m)
              (F->C (sphere->R3 R)))))

;; ## The proof is in the pudding

;; Emmy is a symbolic algebra system, `m:q` produces a symbol named `m`.

^:kindly/hide-code
(defmacro q [s] (list 'quote s))

(ys "
simplify:
  Lsphere(m:q R:q):
    up:
     =>: t:q
     up: theta:q phi:q
     up: thetadot:q phidot:q
")

(kind/reagent
  (vector :tt
    '(simplify
       ((Lsphere 'm 'R)
        (up 't
            (up 'theta 'phi)
            (up 'thetadot 'phidot))))))

;; Indeed the results are the same which proves the infix technically works.

;; ## Intermission

;; The infix notation I use here is `YS` (say "wise"), also called [YAMLScript](https://yamlscript.org/). I have read one opinion that says "I do not want my program to run through a YAML parser". This is a vantage point which is not applicable to Clay notebooks. A Clay notebook is not about maintaining a codebase, it is about conveying ideas and concepts. It is not about the writing of code, it is about the audience which reads the code.

;; And concerning reading code, YS has a lot to offer for the Clojure community. Namely that with YS we can communicate the idea of functional+immutable to that particular audience which is not even willing to read code that has the "parens in the wrong place".

;; I'd like to put into light a related project of mine. With [LisRoot](https://github.com/kloimhardt/LisRoot) I try to lure away the HEP-community from Python towards Clojure/jank. And here YS is the single best way to show code snippets that are functional+immutable. Those people who like that concept will eventually learn to install a code editor for slurp/barfing and then also cherish the nrepl as we all do.

;; I did not find the appropriate YS-compiler on Clojars (for Clay I do not want a dependency on the shared compiled library) , so to deps.edn, I added the following  git-sha:

^:kindly/hide-code
(kind/code "
yamlscript/core {:git/url \"https://github.com/yaml/yamlscript\"
                 :git/sha \"ed7adfbf90a39f379d5a7193bb2e4bdd7f0eecf8\"
                 :deps/root \"core\"}
")

;; ### Some more examples

;; ## 1
(ys "
defn L2(mass metric):
  fn(place velocity):
    1/2 *:
      mass *
      metric(velocity velocity).at(place)
")

(kind/scittle
  '(defn L2 [mass metric]
     (fn [place velocity]
       (* 1/2
          mass
          ((metric velocity velocity) place)))))


;; ## 2

^:kindly/hide-code
(def coordinate-system-to-vector-basis coordinate-system->vector-basis)

(ys "
defn Lc(mass metric coordsys):
  e =: coordinate-system-to-vector-basis(coordsys)
  fn([_ x v]):
    L2(mass metric): point(coordsys).at(x), (e * v)
")

;; The `,` above is a matter of taste and can also be omitted

(kind/scittle
  '(defn Lc [mass metric coordsys]
     (let [e (coordinate-system->vector-basis coordsys)]
       (fn [[_ x v]]
         ((L2 mass metric) ((point coordsys) x) (* e v))))))

;; ## 3

(ys "
the-metric =: literal-metric(g:q R2-rect)
")

(kind/scittle
  '(def the-metric (literal-metric 'g R2-rect)))


;; ## 4
(ys "
L =: Lc(m:q the-metric R2-rect)
")

(kind/scittle
  '(def L (Lc 'm the-metric R2-rect)))


;; ## and the pudding

(ys "
simplify:
  L:
    up:
      =>: t:q
      up: x:q y:q
      up: vx:q vy:q
")

(kind/reagent
  (vector :tt
    '(simplify
       (L (up 't
              (up 'x 'y)
              (up 'vx 'vy))))))
