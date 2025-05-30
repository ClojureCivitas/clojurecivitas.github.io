^{:kindly/hide-code true
  :clay             {:title  "Clean object printing by removing extraneous"
                     :quarto {:author   :timothypratley
                              :type     :post
                              :date     "2025-06-05"
                              :category :clojure
                              :tags     [:print-method :objects]}}}
(ns clojure.print-object.remove-extraneous
  (:require [clojure.core.async :as async]
            [clojure.string :as str])
  (:import (clojure.lang MultiFn)
           (java.io Writer)))

^:kindly/hide-code ^:kind/hidden
(set! *warn-on-reflection* true)

;; The Clojure default for printing objects is noisy.
;; Clojure's `print-method` for `Object` delegates to `clojure.core/print-object`

(defmethod print-method Object [x ^java.io.Writer w]
  (#'clojure.core/print-object x w))

(Object.)

;; The syntax is `#object[CLASS-NAME HASH toString())]`
;; and as you can see, the toString of an Object is `CLASS-NAME@HASH`.
;; For most objects this becomes quite a long string.

(async/chan)

;; ![Objection!](objection.jpg)


;; Functions are printed as objects

(fn [x] x)

;; It's quite easy to miss the fact that it is a function as we are looking for a tiny little `fn` in a sea of text.
;; If, like me, you are fond of the [odd lambda calculus excursion](/code_interview/beating/with_stupid_stuff/z_combinator_gambit.html),
;; things get even more hectic.

((fn [x] (fn [v] ((x x) v))) (fn [y] y))

;; Yikes! what an eyesore.
;; This is not an academic issue specific to lambda calculus.
;; Any function created from inside a function is helpfully identifiable through the `fn$fn` nesting.
;; We create these quite regularly, and they are often printed in stack traces.
;; I'm sure you have seen them when you map an inline function across a seq, and there is a bug in the anonymous function.

(defn caesar-cipher [s]
  (mapv (fn add2 [x] (+ 2 x)) s))

(try (caesar-cipher "hello world")
     (catch Exception ex
       (vec (take 4 (.getStackTrace ex)))))

;; See that part `caesar_cipher$add2`?
;; That is **very** useful information.
;; It tells us that the exception was inside `add2`, which is inside `caesar-cipher`.
;; The stack trace doesn't print functions as objects,
;; but it illustrates that the thing that we care about is that they are a function,
;; what their name is, and whether they were created from inside another function.

;; Let's return to printing a function as an object.
;; An easy improvement is to demunge from Java names to Clojure names.
;; Demunging converts `_` to `-` and `$` to `/`, and munged characters like `+` which is `PLUS` in Java.

(defn class-name
  [x]
  (-> x class .getName Compiler/demunge))

(class-name ((fn [] (fn [y] y))))

;; Next, we don't need the eval identities.

(defn remove-extraneous
  "Clojure compiles with unique names that include things like `/eval32352/` and `--4321`.
  These are rarely useful when printing a function.
  They can still be accessed via (class x) or similar."
  [s]
  (-> s
      (str/replace #"/eval\d+/" "/")
      (str/replace #"--\d+(/|$)" "$1")))

(remove-extraneous (class-name ((fn [] (fn [y] y)))))

;; Much nicer.
;; I can actually read that!
;; I'm not particularly fond of the long namespace shown as the name is either defined in this namespace,
;; referred, or part of `clojure.core`.
;; The multiple slashes form invalid symbols which annoy me;
;; I prefer using `/` only for `namespace/name` separation and `$` as the name level delimiter:
;; `my.namespace/my$nested$name`.

(defn format-class-name ^String [s]
  (let [[ns-str & names] (-> (remove-extraneous s)
                             (str/split #"/"))]
    (if (and ns-str names)
      (str (str/join "$" names))
      (-> s (str/split #"\.") (last)))))

(format-class-name (remove-extraneous (class-name ((fn [] (fn [y] y))))))

;; So short, so sweet.
;; If it's a function, why call it an object?

(defn object-str ^String [x]
  (str (if (fn? x) "#fn" "#object")
       " [" (format-class-name (class-name x)) "]"))

(object-str ((fn [] (fn [y] y))))

(object-str (async/chan))

;; This is really all I care to know about when printing objects and functions,
;; and it matters inside notebooks,
;; where we want to print things, eval things that return objects and functions,
;; and datafy complex objects that contain other objects.
;; To print things without knowing if they are objects, functions, or data,
;; we can extend Clojure's `print-method`.

(defmethod print-method Object [x ^Writer w]
  (.write w (object-str x)))

((fn [] (fn [y] y)))

(async/chan)

;; You can require this namespace from other notebooks to turn on this nice, concise mode of object printing.

;; Happy notebooking!
