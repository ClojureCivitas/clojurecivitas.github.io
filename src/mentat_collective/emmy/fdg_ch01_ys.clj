^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: YAMLScript for the world!"
                     :quarto {:author   :kloimhardt
                              :type     :post
                              :date     "2025-09-10"
                              :image    "sicm_ch01.png"
                              :category :libs
                              :tags     [:emmy :physics]}}}

(ns mentat-collective.emmy.fdg-ch01-ys
    (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                              time infinite? abs ref partial =])
    (:require [emmy.env :refer :all]
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
  '(require '[emmy.env :as e :refer :all :exclude [D F->C]]))

^:kindly/hide-code
(def time first)

^:kindly/hide-code
(kind/scittle
  '(def time first))

^:kindly/hide-code
(kind/scittle
  '(def D partial))

;; The Clojure code below is taken from the examples of [this previous post](https://clojurecivitas.github.io/mentat_collective/emmy/fdg_ch01.html). It is not necessary to understand what the Clojure code does, the purpose is comparison to the infix notation.

(kind/scittle
  '(defn Lfree [mass]
     (fn [[_ _ v]] (* 1/2 mass (square v)))))

;; The following infix notation indeed compiles to Clojure code and is equivalent to the above.

(ys "
defn LFree(mass):
  fn([_ _ v]): mass * 1/2 * square(v)
")

;; I proceed to the next infix

(ys "
defn sphere-to-R3(R):
  fn([_ [theta phi]]):
    up:
     =>: R * sin(theta) * cos(phi)
     =>: R * sin(theta) * sin(phi)
     =>: R * cos(theta)
")

;; which is the following in Clojure

(kind/scittle
  '(defn sphere->R3 [R]
     (fn [[_ [theta phi]]]
       (up (* R (sin theta) (cos phi))
           (* R (sin theta) (sin phi))
           (* R (cos theta))))))

(do
  (defn call [f x] (f x))
  (def of call)
  (def at call))

(ys "
defn F-to-C(F):
  fn(state):
    up:
     time: state
     F: state
     =>: D(0).of(F).at(state) + ( D(1).of(F).at(state) * velocity(state) )
")

(kind/scittle
  '(defn F->C [F]
     (fn [state]
       (up (time state)
           (F state)
           (+ (((D 0) F) state)
              (* (((D 1) F) state)
                 (velocity state)))))))

(ys "
defn Lsphere(m R):
  compose:
    LFree: m
    F-to-C: sphere-to-R3(R)
")

(kind/scittle
  '(defn Lsphere [m R]
     (compose (Lfree m) (F->C (sphere->R3 R)))))

(defmacro q [f] (list 'quote f))

(ys "
simplify:
  Lsphere(m:q R:q):
    up:
     =>: t:q
     up: theta:q phi:q
     up: thetadot:q phidot:q
")

^:kindly/hide-code
(defn show-expression [e] (kind/reagent [:tt e]))

(show-expression
  '(simplify
     ((Lsphere 'm 'R)
      (up 't (up 'theta 'phi) (up 'thetadot 'phidot)))))

(ys "
defn L2(mass metric):
  fn(place velocity): mass * 1/2 * metric(velocity velocity).at(place)
")

(kind/scittle
  '(defn L2 [mass metric]
     (fn [place velocity]
       (* 1/2 mass ((metric velocity velocity) place)))))

(def coordinate-system-to-vector-basis coordinate-system->vector-basis)

(ys "
defn Lc(mass metric coordsys):
  e =: coordinate-system-to-vector-basis(coordsys)
  fn([_ x v]):
    L2(mass metric): point(coordsys).at(x) (e * v)
")

(kind/scittle
  '(defn Lc [mass metric coordsys]
     (let [e (coordinate-system->vector-basis coordsys)]
       (fn [[_ x v]]
         ((L2 mass metric) ((point coordsys) x) (* e v))))))

(ys "
the-metric =: literal-metric(g:q R2-rect)
")

(kind/scittle
  '(def the-metric (literal-metric 'g R2-rect)))

(ys "
L =: Lc(m:q the-metric R2-rect)
")

(kind/scittle
  '(def L (Lc 'm the-metric R2-rect)))

(ys "
simplify:
  L:
    up:
      =>: t:q
      up: x:q y:q
      up: vx:q vy:q
")

(show-expression
  '(simplify
     (L (up 't (up 'x 'y) (up 'vx 'vy)))))

;; YAMLScript, no Clojars thus git-sha

^:kindly/hide-code
(kind/code "
yamlscript/core {:git/url \"https://github.com/yaml/yamlscript\"
                 :git/sha \"ed7adfbf90a39f379d5a7193bb2e4bdd7f0eecf8\"
                 :deps/root \"core\"}
")
