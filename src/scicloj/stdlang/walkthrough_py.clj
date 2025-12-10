^{:kindly/hide-code true
  :clay             {:title  "Stdlang: Python Walkthrough"
                     :quarto {;;:author   :to-be-determined
                              :type     :draft
                              :date     "2025-12-06"
                              ;;:image    ""
                              :category :libs
                              :tags     [:stdlang]}}}
(ns scicloj.stdlang.walkthrough-py
  (:require
   [std.lang :as l]
   [scicloj.kindly.v4.kind :as kind]
   [scicloj.kindly.v4.api :as kindly]))

;; # Python Walkthrough

;; Welcome to the walkthrough of std.lang and it's interaction with the python runtime.
;; Ideally, the reader should have at least some experience with both clojure and python
;; in order to get the most out of the tutorial as the library allows for seamless interop between
;; a clojure runtime and a python one - whether it is on the server side - node, quickjs, osascript - as well as on the browser and other embedded js environments.


;; ## Setup

;; Let us briefly explore the std.lang transpiler.

;; std.lang can be used in different ways:
;; - generate code for different languages
;; - run the code in different runtimes of those languages

;; To specify a way to use it, we use `l/script`. This will create a runtime
;; for evaluation.

^:kind/println
(l/script :python
  {:require [[xt.lang.base-lib :as k]
             [xt.lang.base-iter :as it]]})

;; It is now possible to transpile lisp forms to code:

(!.py
  (+ 1 2 3))

;; If more than one environment is required, `l/script+` is a way to create an annex
;; that 

;; In For example, let us define the following two annexes, named `:code` and `:live`.

;; Here we define `:code` as a way to use the transpiler
;; to generate Python code, but not use it in any runtime.

^:kind/println
(l/script+ [:code :python]
  {:require [[xt.lang.base-lib :as k]
             [xt.lang.base-iter :as it]]})

;; Here we define `:live` as a way to use the transpiler
;; go generate Python code, and run it in a Node.js runtime.

^:kind/println
(l/script+ [:live :python]
  {:runtime :basic
   :require [[xt.lang.base-lib :as k]
             [xt.lang.base-iter :as it]]})

;; Let us now use these two ways for basic arithmetic.

[ ;; No runtime, just generating code:
 (l/! [:code] (+ 1 2))
 ;; Generating, running in Node.js:
 (l/! [:live] (+ 1 2))]

^:kindly/hide-code
(defn display-output-python-code [code]
  (kind/md
   (format "```python\n%s\n```" code)))

^:kindly/hide-code
(defn display-output-clj-code [code]
  (kind/md
   (format "```clj\n%s\n```" code)))

^:kindly/hide-code
(defn display-output-format [& forms]
  (with-meta
    {:column-names [:form
                    :code
                    :live]
     :row-vectors (->> forms
                       (mapv (fn [[form code-output rt-output]]
                               [(display-output-clj-code (pr-str form))
                                (display-output-python-code  code-output)
                                (display-output-clj-code (pr-str rt-output))])))}
    {:kind/table true
     :kindly/hide-code true}))

^:kindly/hide-code
(defmacro display-output-table [& forms]
  
  (with-meta
    {:column-names [:form
                    :code
                    :live]
     :row-vectors (->> forms
                       (mapv (fn [form]
                               `[(display-output-clj-code (pr-str (quote ~form)))
                                 (display-output-python-code  (pr-str (l/! [:code] ~form)))
                                 (pr-str (l/! [:live] ~form))])))}
    {:kind/table true
     :kindly/hide-code true}))


;; # Types

;; ## Types - Primitives

;; ### Null

(display-output-format
 ['nil
  (l/! [:code] nil)
  (l/! [:live] nil)])

;; ### Boolean

(display-output-format
 ['true
  (l/! [:code] true)
  (l/! [:live] true)]
 ['false
  (l/! [:code] false)
  (l/! [:live] false)])

;; ### Strings

(display-output-format
 ['"3"
  (l/! [:code] "3")
  (l/! [:live] "3")]
 ["Hello World"
  (l/! [:code] "Hello World")
  (l/! [:live] "Hello World")])


;; ### Numbers

(display-output-format
 ['3
  (l/! [:code] 3)
  (l/! [:live] 3)]
 ['1.5
  (l/! [:code] 1.5)
  (l/! [:live] 1.5)]
 ['1.54444444444444
  (l/! [:code] 1.54444444444444)
  (l/! [:live] 1.54444444444444)])


;; ### Regex

(display-output-format
 [#"^[Hh]ello d$"
  (l/! [:code] #"^[Hh]ello d$")
  (l/! [:live] #"^[Hh]ello d$")])

;; ## Types - Collection

;; ### Lists

(display-output-format
 ['[1 2 3 4]
  (l/! [:code] [1 2 3 4])
  (l/! [:live] [1 2 3 4])]
 ['["hello" ["world"]]
  (l/! [:code] ["hello" ["world"]])
  (l/! [:live] ["hello" ["world"]])])

;; ### Dicts

(display-output-format
 ['{:a 1 :b 2 :c 3}
  (l/! [:code] {:a 1 :b 2 :c 3})
  (l/! [:live] {:a 1 :b 2 :c 3})]
 ['{:a {:b {:c 3}}}
  (l/! [:code] {:a {:b {:c 3}}})
  (l/! [:live] {:a {:b {:c 3}}})])

;; ### Tuples

(display-output-format
 [''(1 2 3 4)
  (l/! [:code] '(1 2 3 4))
  (l/! [:live] '(1 2 3 4))]
 [''("hello" ["world"])
  (l/! [:code] '("hello" ["world"]))
  (l/! [:live] '("hello" ["world"]))])


;; ### Tuples

(display-output-format
 [#{1 2 3 4}
  (l/! [:code] #{1 2 3 4})
  (l/! [:live] #{1 2 3 4})]
 [#{"hello" "world"}
  (l/! [:code] #{"hello" "world"})
  (l/! [:live] #{"hello" "world"})])

;; ### Objects - tab

(display-output-format
 ['(tab ["a" 1] ["b" 2] ["c" 3])
  (l/! [:code] (tab ["a" 1] ["b" 2] ["c" 3]))
  (l/! [:live] (tab ["a" 1] ["b" 2] ["c" 3]))])


;; ## Types - Checks

;; # Operations

;; ## Operations - Assignment

;; ### Var

(display-output-format
 ['(do (var x 1)
       x)
  (l/! [:code]
    (do (var x 1)
        x))
  (l/! [:live]
    (do (var x 1)
        x))])

;; ### Reassign

(display-output-format
 ['(do (var x 1)
       (:= x 10)
       x)
  (l/! [:code]
    (do (var x 1)
        (:= x 10)
        x))
  (l/! [:live]
    (do (var x 1)
        (:= x 10)
        x))])


;; ## Operations - Logic

;; ### Negation
(display-output-format
 ['(not true)
  (l/! [:code] (not true))
  (l/! [:live] (not true))]
 ['(not false)
  (l/! [:code] (not false))
  (l/! [:live] (not false))]
 ['(not 1)
  (l/! [:code] (not 1))
  (l/! [:live] (not 1))]
 ['(not 0)
  (l/! [:code] (not 0))
  (l/! [:live] (not 0))])

;; ### Or
(display-output-format
 ['(or 0 false)
  (l/! [:code] (or 0 false))
  (l/! [:live] (or 0 false))]
 ['(or 1 false)
  (l/! [:code] (or 1 false))
  (l/! [:live] (or 1 false))]
 ['(or 0 true)
  (l/! [:code] (or 0 true))
  (l/! [:live] (or 0 true))])

;; ### And
(display-output-format
 ['(and 0 false)
  (l/! [:code] (and 0 false))
  (l/! [:live] (and 0 false))]
 ['(and 1 false)
  (l/! [:code] (and 1 false))
  (l/! [:live] (and 1 false))]
 ['(and 0 true)
  (l/! [:code] (and 0 true))
  (l/! [:live] (and 0 true))])

;; ### Ternary
(display-output-format
 ['(:? true 1 2)
  (l/! [:code] (:? true 1 2))
  (l/! [:live] (:? true 1 2))]
 ['(:? (or 0 0) 1 2)
  (l/! [:code] (:? (or 0 0) 1 2))
  (l/! [:live] (:? (or 0 0) 1 2))])


;; ## Operations - Math

;; ### Addition

(display-output-format
 ['(+ 1 2 3)
  (l/! [:code] (+ 1 2 3))
  (l/! [:live] (+ 1 2 3))]
 ['(+ 1 10)
  (l/! [:code] (+ 1 10))
  (l/! [:live] (+ 1 10))])

;; ### Subtraction 

(display-output-format
 ['(- 10)
  (l/! [:code] (- 10))
  (l/! [:live] (- 10))]
 ['(- 10 1.1)
  (l/! [:code] (- 10 1.1))
  (l/! [:live] (- 10 1.1))]
 ['(- 4 3 2 1.1)
  (l/! [:code] (- 4 3 2 1.1))
  (l/! [:live] (- 4 3 2 1.1))])

;; ### Multiplication

(display-output-format
 ['(* 10 20)
  (l/! [:code] (* 10 20))
  (l/! [:live] (* 10 20))]
 ['(* 4 3.3 2.2 1.1)
  (l/! [:code] (* 4 3.3 2.2 1.1))
  (l/! [:live] (* 4 3.3 2.2 1.1))])

;; ### Division

(display-output-format
 ['(/ 10)
  (l/! [:code] (/ 10))
  (l/! [:live] (/ 10))]
 ['(/ 10 20)
  (l/! [:code] (/ 10 20))
  (l/! [:live] (/ 10 20))]
 ['(/ 4 3.3 2.2 1.1)
  (l/! [:code] (/ 4 3.3 2.2 1.1))
  (l/! [:live] (/ 4 3.3 2.2 1.1))])

;; ### Pow

(display-output-format
 ['(pow 10 2)
  (l/! [:code] (pow 10 2))
  (l/! [:live] (pow 10 2))]
 ['(pow 0.5 0.2)
  (l/! [:code] (pow 0.5 0.2))
  (l/! [:live] (pow 0.5 0.2))])

;; ### Mod

(display-output-format
 ['(mod 1123 7)
  (l/! [:code] (mod 1123 7))
  (l/! [:live] (mod 1123 7))]
 ['(mod 1123 7.1)
  (l/! [:code] (mod 1123 7.1))
  (l/! [:live] (mod 1123 7.1))])


;; ## Operations - Comparison

;; ### Equals

(display-output-format
 ['(== 1 1)
  (l/! [:code] (== 1 1))
  (l/! [:live] (== 1 1))]
 ['(== 1 "1")
  (l/! [:code] (== 1 "1"))
  (l/! [:live] (== 1 "1"))]
 ['(== "hello" "hello")
  (l/! [:code] (== "hello" "hello"))
  (l/! [:live] (== "hello" "hello"))])

;; ### Not Equals

(display-output-format
 ['(not= 1 2)
  (l/! [:code] (not= 1 2))
  (l/! [:live] (not= 1 2))]
 ['(not= 1 "1")
  (l/! [:code] (not= 1 "1"))
  (l/! [:live] (not= 1 "1"))]
 ['(not= "hello" "hello")
  (l/! [:code] (not= "hello" "hello"))
  (l/! [:live] (not= "hello" "hello"))])

;; ### Less Than

(display-output-format
 ['(< 1 2)
  (l/! [:code] (< 1 2))
  (l/! [:live] (< 1 2))]
 ['(< 1 1)
  (l/! [:code] (< 1 1))
  (l/! [:live] (< 1 1))])

;; ### Less Than Equals

(display-output-format
 ['(<= 1 2)
  (l/! [:code] (<= 1 2))
  (l/! [:live] (<= 1 2))]
 ['(<= 1 1)
  (l/! [:code] (<= 1 1))
  (l/! [:live] (<= 1 1))])

;; ### Greater Than

(display-output-format
 ['(> 3 2)
  (l/! [:code] (> 3 2))
  (l/! [:live] (> 3 2))]
 ['(> 3 3)
  (l/! [:code] (> 3 3))
  (l/! [:live] (> 3 3))])

;; ### Greater Than Equals

(display-output-format
 ['(>= 3 2)
  (l/! [:code] (>= 3 2))
  (l/! [:live] (>= 3 2))]
 ['(>= 3 3)
  (l/! [:code] (>= 3 3))
  (l/! [:live] (>= 3 3))])

;; ## Operations - Counter

;; ### Increment

(display-output-format
 ['(do (var x 1)
       (:++ x)
       x)
  (l/! [:code]
    (do (var x 1)
        (:++ x)
        x))
  (l/! [:live]
    (do (var x 1)
        (:++ x)
        x))])

;; ### Increment By

(display-output-format
 ['(do (var x 1)
       (:+= x 10)
       x)
  (l/! [:code]
    (do (var x 1)
        (:+= x 10)
        x))
  (l/! [:live]
    (do (var x 1)
        (:+= x 10)
        x))])

;; ### Decrement

(display-output-format
 ['(do (var x 5)
       (:-- x)
       x)
  (l/! [:code]
    (do (var x 5)
        (:-- x)
        x))
  (l/! [:live]
    (do (var x 5)
        (:-- x)
        x))])

;; ### Decrement By

(display-output-format
 ['(do (var x 5)
       (:-= x 50)
       x)
  (l/! [:code]
    (do (var x 5)
        (:-= x 50)
        x))
  (l/! [:live]
    (do (var x 5)
        (:-= x 50)
        x))])

;; ### Multiply By

(display-output-format
 ['(do (var x 5)
       (:*= x 50)
       x)
  (l/! [:code]
    (do (var x 5)
        (:*= x 50)
        x))
  (l/! [:live]
    (do (var x 5)
        (:*= x 50)
        x))])

;; ## Operations - Bitwise

;; ### Bitwise Or

(display-output-format
 ['(b:| 7 8)
  (l/! [:code]
    (b:| 7 8))
  (l/! [:live]
    (b:| 7 8))]
 ['(b:| 7 7)
  (l/! [:code]
    (b:| 7 7))
  (l/! [:live]
    (b:| 7 7))]
 ['(b:| 7 0)
  (l/! [:code]
    (b:| 7 0))
  (l/! [:live]
    (b:| 7 0))])

;; ### Bitwise And

(display-output-format
 ['(b:& 7 8)
  (l/! [:code]
    (b:& 7 8))
  (l/! [:live]
    (b:& 7 8))]
 ['(b:& 7 7)
  (l/! [:code]
    (b:& 7 7))
  (l/! [:live]
    (b:& 7 7))]
 ['(b:& 7 0)
  (l/! [:code]
    (b:& 7 0))
  (l/! [:live]
    (b:& 7 0))])

;; ### Bitwise Xor

(display-output-format
 ['(b:xor 7 8)
  (l/! [:code]
    (b:xor 7 8))
  (l/! [:live]
    (b:xor 7 8))]
 ['(b:xor 7 7)
  (l/! [:code]
    (b:xor 7 7))
  (l/! [:live]
    (b:xor 7 7))]
 ['(b:xor 7 0)
  (l/! [:code]
    (b:xor 7 0))
  (l/! [:live]
    (b:xor 7 0))])

;; ### Bitshift Right

(display-output-format
 ['(b:>> 128 3)
  (l/! [:code]
    (b:>> 128 3))
  (l/! [:live]
    (b:>> 128 3))])


;; ### Bitshift Left

(display-output-format
 ['(b:<< 128 3)
  (l/! [:code]
    (b:<< 128 3))
  (l/! [:live]
    (b:<< 128 3))])

;; ## Operations - Functions

(display-output-format
 ['(fn [x y] (return (+ x y)))
  (l/! [:code]
    (fn [x y] (return (+ x y))))
  (l/! [:live]
    (fn [x y] (return (+ x y))))]
 ['(do (var hello (fn [x y] (return (+ x y))))
       (hello 1 2))
  (l/! [:code]
    (do (var hello (fn [x y] (return (+ x y))))
        (hello 1 2)))
  (l/! [:live]
    (do (var hello (fn [x y] (return (+ x y))))
        (hello 1 2)))])

;; ## Operations - Blocks

;; ### if block

(display-output-format
 ['(do (var arr [1 2 3 4 5])
       (var out)
       (if (< (x:len arr) 10)
         (:= out true)
         (:= out false))
       out)
  (l/! [:code]
    (do (var arr [1 2 3 4 5])
        (var out)
        (if (< (x:len arr) 10)
          (:= out true)
          (:= out false))
        out))
  (l/! [:live]
    (do (var arr [1 2 3 4 5])
        (var out)
        (if (< (x:len arr) 10)
          (:= out true)
          (:= out false))
        out))])

;; ### cond block

(display-output-format
 ['(do (var arr [1 2 3 4 5])
       (var out)
       (cond (< (x:len arr) 5)
             (:= out "1")

             (< (x:len arr) 10)
             (:= out "2")
             
             :else
             (:= out "3"))
       out)
  (l/! [:code]
    (do (var arr [1 2 3 4 5])
       (var out)
       (cond (< (x:len arr) 5)
             (:= out "1")

             (< (x:len arr) 10)
             (:= out "2")
             
             :else
             (:= out "3"))
       out))
  (l/! [:live]
    (do (var arr [1 2 3 4 5])
       (var out)
       (cond (< (x:len arr) 5)
             (:= out "1")

             (< (x:len arr) 10)
             (:= out "2")
             
             :else
             (:= out "3"))
       out))])

;; ### while block
(display-output-format
 ['(do (var x [])
       (var i 0)
       (while (< i 5)
         (x:arr-push x i)
         (:++ i))
       x)
  (l/! [:code]
    (do (var x [])
       (var i 0)
       (while (< i 5)
         (x:arr-push x i)
         (:++ i))
       x))
  (l/! [:live]
    (do (var x [])
        (var i 0)
        (while (< i 5)
          (x:arr-push x i)
          (:++ i))
        x))])

;; ### for block
(display-output-format
 ['(do (var arr [])
       (for [(var i 1) (< i 5) (:++ i)]
         (x:arr-push arr i))
       arr)
  (l/! [:code]
    (do (var arr [])
       (for [(var i 1) (< i 5) (:++ i)]
         (x:arr-push arr i))
       arr))
  (l/! [:live]
    (do (var arr [])
       (for [(var i 1) (< i 5) (:++ i)]
         (x:arr-push arr i))
       arr))])

;; ### case block
(display-output-format
 ['(do (var arr 1)
       (var out)
       (case arr
         1 (do (:= out 1)
               (break))
         2 (do (:= out 2)
               (break)))
       out)
  (l/! [:code]
    (do (var arr 1)
        (var out)
        (case arr
          1 (do (:= out 1)
                (break))
          2 (do (:= out 2)
                (break)))
        out))
  (l/! [:live]
    (do (var arr 1)
        (var out)
        (case arr
          1 (do (:= out 1)
                (break))
          2 (do (:= out 2)
                (break)))
        out))])


;; ### try/catch block
(display-output-format
 ['(do (var out "hello")
       (try
         (throw 1)
         (catch e (:= out "world")))
       out)
  (l/! [:code]
    (do (var out "hello")
       (try
         (throw 1)
         (catch e (:= out "world")))
       out))
  (l/! [:live]
    (do (var out "hello")
       (try
         (throw 1)
         (catch e (:= out "world")))
       out))])

;; # Base Lib

;; ## Base Lib - For 

;; ### for:array

(display-output-format
 ['(do (var out := [])
       (k/for:array [e [1 2 3 4]]
         (if (> e 3)
           (break))
         (x:arr-push out e))
       out)
  (l/! [:code]
    (do (var out := [])
        (k/for:array [e [1 2 3 4]]
          (if (> e 3)
            (break))
          (x:arr-push out e))
        out))
  (l/! [:live]
    (do (var out := [])
        (k/for:array [e [1 2 3 4]]
          (if (> e 3)
            (break))
          (x:arr-push out e))
        out))])

;; ### for:object

(display-output-format
 ['(do
     (var out := [])
     (var obj := {:a 1 :b 2})
     (k/for:object [[k v] obj]
       (x:arr-push out [k v]))
     out)
  (l/! [:code]
    (do
     (var out := [])
     (var obj := {:a 1 :b 2})
     (k/for:object [[k v] obj]
       (x:arr-push out [k v]))
     out))
  (l/! [:live]
    (do
     (var out := [])
     (var obj := {:a 1 :b 2})
     (k/for:object [[k v] obj]
       (x:arr-push out [k v]))
     out))])

;; ### for:index

(display-output-format
 ['(do
     (var out := [])
     (k/for:index [i [0 10 2]]
       (x:arr-push out i))
     out)
  (l/! [:code]
    (do
     (var out := [])
     (k/for:index [i [0 10 2]]
       (x:arr-push out i))
     out))
  (l/! [:live]
    (do
     (var out := [])
     (k/for:index [i [0 10 2]]
       (x:arr-push out i))
     out))])

;; ### for:return

(display-output-format
 ['(do
    (var out)
    (var success (fn [cb]
                   (cb nil "OK")))
    (k/for:return [[ret err] (success (x:callback))]
      {:success (:= out ret)
       :error   (:= out err)})
    out)
  (l/! [:code]
    (do
    (var out)
    (var success (fn [cb]
                   (cb nil "OK")))
    (k/for:return [[ret err] (success (x:callback))]
      {:success (:= out ret)
       :error   (:= out err)})
    out))
  (l/! [:live]
    (do
    (var out)
    (var success (fn [cb]
                   (cb nil "OK")))
    (k/for:return [[ret err] (success (x:callback))]
      {:success (:= out ret)
       :error   (:= out err)})
    out))])


^:kindly/hide-code
(do 
  (l/annex:restart-all))

;; ## Base Lib - Util

;; ### invoke

(display-output-format
 ['(k/invoke k/add 1 2)
  (l/! [:code]
    (k/invoke k/add 1 2))
  (l/! [:live]
    (k/invoke k/add 1 2))])

;; ### unpack

(display-output-format
 ['[(k/unpack [1 2 3]) (k/unpack [4 5 6])]
  (l/! [:code]
    [(k/unpack [1 2 3]) (k/unpack [4 5 6])])
  (l/! [:live]
    [(k/unpack [1 2 3]) (k/unpack [4 5 6])])])

;; ### apply

(display-output-format
 ['(k/apply (fn:> [a b] (+ a b))
            [1 2])
  (l/! [:code]
    (k/apply (fn:> [a b] (+ a b))
             [1 2]))
  (l/! [:live]
    (k/apply (fn:> [a b] (+ a b))
             [1 2]))])

;; ### eval

(display-output-format
 ['(k/eval "1+2")
  (l/! [:code]
    (k/eval "1+2"))
  (l/! [:live]
    (k/eval "1+2"))]
 ['(k/apply k/eval ["1+2"])
  (l/! [:code]
    (k/apply k/eval ["1+2"]))
  (l/! [:live]
    (k/apply k/eval ["1+2"]))])


;; ### len

(display-output-format
 ['(k/len "1+2")
  (l/! [:code]
    (k/len "1+2"))
  (l/! [:live]
    (k/len "1+2"))]
 ['(k/apply k/len ["1+2"])
  (l/! [:code]
    (k/apply k/len ["1+2"]))
  (l/! [:live]
    (k/apply k/len ["1+2"]))])

;; ### cat

(display-output-format
 ['(k/cat "1+2" "+3")
  (l/! [:code]
    (k/cat "1+2" "+3"))
  (l/! [:live]
    (k/cat "1+2" "+3"))]
 #_['(k/apply k/cat ["1+2" "+3"])
  (l/! [:code]
    (k/apply k/cat ["1+2" "+3"]))
    (l/! [:live]
    (k/apply k/cat ["1+2" "+3"]))])


;; ### x:del

(display-output-format
 ['(do
     (var out {:a 1})
     (k/x:del (. out ["a"]))
     out)
  (l/! [:code]
    (do
     (var out {:a 1})
     (k/x:del (. out ["a"]))
     out))
  (l/! [:live]
    (do
      (var out {:a 1})
      (k/x:del (. out ["a"]))
      out))])

;; ### x:shell

(display-output-format
 ['(do
     (var cb {})
     (defn ^:inner call []
       (k/x:shell "ls" cb))
     (call))
  (l/! [:code]
    (do
     (var cb {})
     (defn ^:inner call []
       (k/x:shell "ls" cb))
     (call)))
  (l/! [:live]
    (do
     (var cb {})
     (defn ^:inner call []
       (k/x:shell "ls" cb))
     (call)))])

;; ### x:offset

(display-output-format
 ['(k/x:offset)
  (l/! [:code]
    (k/x:offset))
  (l/! [:live]
    (k/x:offset))])

;; ### x:offset-rev

(display-output-format
 ['(k/x:offset-rev)
  (l/! [:code]
    (k/x:offset-rev))
  (l/! [:live]
    (k/x:offset-rev))])

;; ### x:offset-len

(display-output-format
 ['(k/x:offset-len 10)
  (l/! [:code]
    (k/x:offset-len 10))
  (l/! [:live]
    (k/x:offset-len 10))])

;; ### x:offset-rlen

(display-output-format
 ['(k/x:offset-rlen 10)
  (l/! [:code]
    (k/x:offset-rlen 10))
  (l/! [:live]
    (k/x:offset-rlen 10))])

;; ### nil?

(display-output-format
 ['(k/nil? "hello")
  (l/! [:code]
    (k/nil? "hello"))
  (l/! [:live]
    (k/nil? "hello"))]
 ['(k/apply k/nil? ["hello"])
  (l/! [:code]
    (k/apply k/nil? ["hello"]))
  (l/! [:live]
    (k/apply k/nil? ["hello"]))])

;; ### not-nil?

(display-output-format
 ['(k/not-nil? "hello")
  (l/! [:code]
    (k/not-nil? "hello"))
  (l/! [:live]
    (k/not-nil? "hello"))]
 ['(k/apply k/not-nil? ["hello"])
  (l/! [:code]
    (k/apply k/not-nil? ["hello"]))
  (l/! [:live]
    (k/apply k/not-nil? ["hello"]))])

;; ### to-string

(display-output-format
 ['(k/to-string 1)
  (l/! [:code]
    (k/to-string 1))
  (l/! [:live]
    (k/to-string 1))]
 ['(k/apply k/to-string [1])
  (l/! [:code]
    (k/apply k/to-string [1]))
  (l/! [:live]
    (k/apply k/to-string [1]))])

;; ### to-number

(display-output-format
 ['(k/to-number "1.1")
  (l/! [:code]
    (k/to-number "1.1"))
  (l/! [:live]
    (k/to-number "1.1"))]
 ['(k/apply k/to-number ["1.1"])
  (l/! [:code]
    (k/apply k/to-number ["1.1"]))
  (l/! [:live]
    (k/apply k/to-number ["1.1"]))])


;; ### is-string?

(display-output-format
 ['(k/is-string? "1.1")
  (l/! [:code]
    (k/is-string? "1.1"))
  (l/! [:live]
    (k/is-string? "1.1"))]
 ['(k/apply k/is-string? ["1.1"])
  (l/! [:code]
    (k/apply k/is-string? ["1.1"]))
  (l/! [:live]
    (k/apply k/is-string? ["1.1"]))])

;; ### is-number?

(display-output-format
 ['(k/is-number? 1.1)
  (l/! [:code]
    (k/is-number? 1.1))
  (l/! [:live]
    (k/is-number? 1.1))]
 ['(k/apply k/is-number? [1.1])
  (l/! [:code]
    (k/apply k/is-number? [1.1]))
  (l/! [:live]
    (k/apply k/is-number? [1.1]))])

;; ### is-integer?

(display-output-format
 ['(k/is-integer? 1000)
  (l/! [:code]
    (k/is-integer? 1000))
  (l/! [:live]
    (k/is-integer? 1000))]
 ['(k/apply k/is-integer? [1000])
  (l/! [:code]
    (k/apply k/is-integer? [1000]))
  (l/! [:live]
    (k/apply k/is-integer? [1000]))])

;; ### is-boolean?

(display-output-format
 ['(k/is-boolean? false)
  (l/! [:code]
    (k/is-boolean? false))
  (l/! [:live]
    (k/is-boolean? false))]
 ['(k/apply k/is-boolean? [false])
  (l/! [:code]
    (k/apply k/is-boolean? [false]))
  (l/! [:live]
    (k/apply k/is-boolean? [false]))])

;; ### is-function?

(display-output-format
 ['(k/is-function? (fn [] (return 1)))
  (l/! [:code]
    (k/is-function? (fn [] (return 1))))
  (l/! [:live]
    (k/is-function? (fn [] (return 1))))]
 ['(k/apply k/is-function? [(fn [] (return 1))])
  (l/! [:code]
    (k/apply k/is-function? [(fn [] (return 1))]))
  (l/! [:live]
    (k/apply k/is-function? [(fn [] (return 1))]))])

;; ### is-array?

(display-output-format
 ['(k/is-array? [1 2 3 4 5])
  (l/! [:code]
    (k/is-array? [1 2 3 4 5]))
  (l/! [:live]
    (k/is-array? [1 2 3 4 5]))]
 ['(k/apply k/is-array? [[1 2 3 4 5]])
  (l/! [:code]
    (k/apply k/is-array? [[1 2 3 4 5]]))
  (l/! [:live]
    (k/apply k/is-array? [[1 2 3 4 5]]))])

;; ### is-object?

(display-output-format
 ['(k/is-object? {:a 1 :b 2})
  (l/! [:code]
    (k/is-object? {:a 1 :b 2}))
  (l/! [:live]
    (k/is-object? {:a 1 :b 2}))]
 ['(k/apply k/is-object? [{:a 1 :b 2}])
  (l/! [:code]
    (k/apply k/is-object? [{:a 1 :b 2}]))
  (l/! [:live]
    (k/apply k/is-object? [{:a 1 :b 2}]))])

;; ### type-native

(display-output-format
 ['(do
     [(k/type-native {})
      (k/type-native [1])
      (k/type-native (fn []))
      (k/type-native 1)
      (k/type-native "")
      (k/type-native true)])
  (l/! [:code]
    (do
      [(k/type-native {})
       (k/type-native [1])
       (k/type-native (fn []))
       (k/type-native 1)
       (k/type-native "")
       (k/type-native true)]))
  (l/! [:live]
    (do
      [(k/type-native {})
       (k/type-native [1])
       (k/type-native (fn []))
       (k/type-native 1)
       (k/type-native "")
       (k/type-native true)]))])

;; ### type-class

(display-output-format
 ['(do
     [(k/type-class {})
      (k/type-class [1])
      (k/type-class (fn []))
      (k/type-class 1)
      (k/type-class "")
      (k/type-class true)])
  (l/! [:code]
    (do
      [(k/type-class {})
       (k/type-class [1])
       (k/type-class (fn []))
       (k/type-class 1)
       (k/type-class "")
       (k/type-class true)]))
  (l/! [:live]
    (do
      [(k/type-class {})
       (k/type-class [1])
       (k/type-class (fn []))
       (k/type-class 1)
       (k/type-class "")
       (k/type-class true)]))])

;; ### print

(display-output-format
 ['(k/print "hello")
  (l/! [:code]
    (k/print "hello"))
  (l/! [:live]
    (k/print "hello"))]
 ['(k/apply k/print ["hello"])
  (l/! [:code]
    (k/apply k/print ["hello"]))
  (l/! [:live]
    (k/apply k/print ["hello"]))])

;; ### random

(display-output-format
 ['(k/random)
  (l/! [:code]
    (k/random))
  (l/! [:live]
    (k/random))]
 ['(k/apply k/random [])
  (l/! [:code]
    (k/apply k/random []))
  (l/! [:live]
    (k/apply k/random []))])

;; ### now-ms

(display-output-format
 ['(k/now-ms)
  (l/! [:code]
    (k/now-ms))
  (l/! [:live]
    (k/now-ms))]
 ['(k/apply k/now-ms [])
  (l/! [:code]
    (k/apply k/now-ms []))
  (l/! [:live]
    (k/apply k/now-ms []))])

;; ## Base Lib - Global

;; ### !:G

;; Accesses the global object

(display-output-format
 ['!:G
  (l/! [:code]
    !:G)
  (l/! [:live]
    !:G)]
 #_['(!:G CUSTOM)
  (l/! [:code]
    (!:G CUSTOM))
  (l/! [:live]
    (!:G CUSTOM))])

;; ### global-set

(display-output-format
 ['(do
     (k/global-set "HELLO" 1)
     (. !:G ["HELLO"]))
  (l/! [:code]
    (do
     (k/global-set "HELLO" 1)
     (. !:G ["HELLO"])))
  (l/! [:live]
    (do
     (k/global-set "HELLO" 1)
     (. !:G ["HELLO"])))])


;; ### global-has?

(display-output-format
 ['(do
     (k/global-set "HELLO" 1)
     (k/global-has? "HELLO"))
  (l/! [:code]
    (do
     (k/global-set "HELLO" 1)
     (k/global-has? "HELLO")))
  (l/! [:live]
    (do
      (k/global-set "HELLO" 1)
      (k/global-has? "HELLO")))])


;; ### global-del

(display-output-format
 ['(do
     (k/global-del "HELLO")
     (k/global-has? "HELLO"))
  (l/! [:code]
    (do
      (k/global-del "HELLO")
      (k/global-has? "HELLO")))
  (l/! [:live]
    (do
      (k/global-del "HELLO")
      (k/global-has? "HELLO")))])

^:kindly/hide-code
(do 
  (l/annex:restart-all))

;; ## Base Lib - String

;; ### get-char

(display-output-format
 ['(k/get-char "abc" 0)
  (l/! [:code]
    (k/get-char "abc" 0))
  (l/! [:live]
    (k/get-char "abc" 0))]
 ['(k/apply k/get-char ["abc" 0])
  (l/! [:code]
    (k/apply k/get-char ["abc" 0]))
  (l/! [:live]
    (k/apply k/get-char ["abc" 0]))])

;; ### gt-string

(display-output-format
 ['[(k/gt-string "a" "b")
    (k/gt-string "A" "a")]
  (l/! [:code]
    [(k/gt-string "a" "b")
     (k/gt-string "A" "a")])
  (l/! [:live]
    [(k/gt-string "a" "b")
     (k/gt-string "A" "a")])])

;; ### lt-string

(display-output-format
 ['[(k/lt-string "a" "b")
    (k/lt-string "A" "a")]
  (l/! [:code]
    [(k/lt-string "a" "b")
     (k/lt-string "A" "a")])
  (l/! [:live]
    [(k/lt-string "a" "b")
     (k/lt-string "A" "a")])])

;; ### split

(display-output-format
 ['(k/split "hello/world" "/")
  (l/! [:code]
    (k/split "hello/world" "/"))
  (l/! [:live]
    (k/split "hello/world" "/"))]
 ['(k/apply k/split ["hello/world" "/"])
  (l/! [:code]
    (k/apply k/split ["hello/world" "/"]))
  (l/! [:live]
    (k/apply k/split ["hello/world" "/"]))])

;; ### join

(display-output-format
 ['(k/join "/" ["hello" "world"])
  (l/! [:code]
    (k/join "/" ["hello" "world"]))
  (l/! [:live]
    (k/join "/" ["hello" "world"]))]
 ['(k/apply k/join ["/" ["hello" "world"]])
  (l/! [:code]
    (k/apply k/join ["/" ["hello" "world"]]))
  (l/! [:live]
    (k/apply k/join ["/" ["hello" "world"]]))])

;; ### replace

(display-output-format
 ['(k/replace "hello/world" "/" "_")
  (l/! [:code]
    (k/replace "hello/world" "/" "_"))
  (l/! [:live]
    (k/replace "hello/world" "/" "_"))]
 ['(k/apply k/replace ["hello/world" "/" "_"])
  (l/! [:code]
    (k/apply k/replace ["hello/world" "/" "_"]))
  (l/! [:live]
    (k/apply k/replace ["hello/world" "/" "_"]))])

;; ### index-of

(display-output-format
 ['(k/index-of "hello/world" "/")
  (l/! [:code]
    (k/index-of "hello/world" "/"))
  (l/! [:live]
    (k/index-of "hello/world" "/"))]
 ['(k/apply k/index-of ["hello/world" "/"])
  (l/! [:code]
    (k/apply k/index-of ["hello/world" "/"]))
  (l/! [:live]
    (k/apply k/index-of ["hello/world" "/"]))])

;; ### substring

(display-output-format
 ['[(k/substring "hello/world" 3)
    (k/substring "hello/world" 3 8)]
  (l/! [:code]
    [(k/substring "hello/world" 3)
     (k/substring "hello/world" 3 8)])
  (l/! [:live]
    [(k/substring "hello/world" 3)
     (k/substring "hello/world" 3 8)])]
 #_['(k/apply k/substring ["hello/world" 3 8])
    (l/! [:code]
      (k/apply k/substring ["hello/world" 3 8]))
    (l/! [:live]
      (k/apply k/substring ["hello/world" 3 8]))])

;; ### to-uppercase

(display-output-format
 ['(k/to-uppercase "hello")
  (l/! [:code]
    (k/to-uppercase "hello"))
  (l/! [:live]
    (k/to-uppercase "hello"))]
 ['(k/apply k/to-uppercase ["hello"])
  (l/! [:code]
    (k/apply k/to-uppercase ["hello"]))
  (l/! [:live]
    (k/apply k/to-uppercase ["hello"]))])

;; ### to-lowercase

(display-output-format
 ['(k/to-lowercase "hello")
  (l/! [:code]
    (k/to-lowercase "hello"))
  (l/! [:live]
    (k/to-lowercase "hello"))]
 ['(k/apply k/to-lowercase ["hello"])
  (l/! [:code]
    (k/apply k/to-lowercase ["hello"]))
  (l/! [:live]
    (k/apply k/to-lowercase ["hello"]))])

^:kindly/hide-code
(comment
  ;; ### to-fixed

  (display-output-format
   #_['(k/to-fixed 1.2 3)
      (l/! [:code]
        (k/to-fixed 1.2 3))
      (l/! [:live]
        (k/to-fixed 1.2 3))]
   #_['(k/apply k/to-fixed [1.2 3])
      (l/! [:code]
        (k/apply k/to-fixed [1.2 3]))
      (l/! [:live]
        (k/apply k/to-fixed [1.2 3]))])

  ;; ### trim

  (display-output-format
   ['(k/trim " \n  hello \n  ")
    (l/! [:code]
      (k/trim " \n  hello \n  "))
    (l/! [:live]
      (k/trim " \n  hello \n  "))]
   ['(k/apply k/trim [" \n  hello \n  "])
    (l/! [:code]
      (k/apply k/trim [" \n  hello \n  "]))
    (l/! [:live]
      (k/apply k/trim [" \n  hello \n  "]))])

  ;; ### trim-left

  (display-output-format
   ['(k/trim-left " \n  hello \n  ")
    (l/! [:code]
      (k/trim-left " \n  hello \n  "))
    (l/! [:live]
      (k/trim-left " \n  hello \n  "))]
   ['(k/apply k/trim-left [" \n  hello \n  "])
    (l/! [:code]
      (k/apply k/trim-left [" \n  hello \n  "]))
    (l/! [:live]
      (k/apply k/trim-left [" \n  hello \n  "]))])
  
  ;; ### trim-right

  (display-output-format
   ['(k/trim-right " \n  hello \n  ")
    (l/! [:code]
      (k/trim-right " \n  hello \n  "))
    (l/! [:live]
      (k/trim-right " \n  hello \n  "))]
   ['(k/apply k/trim-right [" \n  hello \n  "])
    (l/! [:code]
      (k/apply k/trim-right [" \n  hello \n  "]))
    (l/! [:live]
      (k/apply k/trim-right [" \n  hello \n  "]))]))

;; ### starts-with?

(display-output-format
 ['(k/starts-with? "Foo Bar" "Foo")
  (l/! [:code]
    (k/starts-with? "Foo Bar" "Foo"))
  (l/! [:live]
    (k/starts-with? "Foo Bar" "Foo"))])

;; ### ends-with?

(display-output-format
 ['(k/ends-with? "Foo Bar" "Bar")
  (l/! [:code]
    (k/ends-with? "Foo Bar" "Bar"))
  (l/! [:live]
    (k/ends-with? "Foo Bar" "Bar"))])

;; ### capitalize

(display-output-format
 ['(k/capitalize "hello")
  (l/! [:code]
    (k/capitalize "hello"))
  (l/! [:live]
    (k/capitalize "hello"))])

;; ### decapitalize

(display-output-format
 ['(k/decapitalize "HELLO")
  (l/! [:code]
    (k/decapitalize "HELLO"))
  (l/! [:live]
    (k/decapitalize "HELLO"))])

;; ### pad-left

(display-output-format
 ['(k/pad-left "000" 5 "-")
  (l/! [:code]
    (k/pad-left "000" 5 "-"))
  (l/! [:live]
    (k/pad-left "000" 5 "-"))])


;; ### pad-right

(display-output-format
 ['(k/pad-right "000" 5 "-")
  (l/! [:code]
    (k/pad-right "000" 5 "-"))
  (l/! [:live]
    (k/pad-right "000" 5 "-"))])


;; ### pad-lines

(display-output-format
 ['(k/pad-lines (k/join "\n"
                        ["hello"
                         "world"])
                2
                " ")
  (l/! [:code]
    (k/pad-lines (k/join "\n"
                         ["hello"
                          "world"])
                 2
                 " "))
  (l/! [:live]
    (k/pad-lines (k/join "\n"
                         ["hello"
                          "world"])
                2
                " "))])


;; ### split-long

(display-output-format
 ['(k/split-long "1234567890123456789012345678901234567890123456789012345678901234567890"
                 4)
  (l/! [:code]
    (k/split-long "1234567890123456789012345678901234567890123456789012345678901234567890"
                  4))
  (l/! [:live]
    (k/split-long "1234567890123456789012345678901234567890123456789012345678901234567890"
                  4))])


;; ## Base Lib - Encode

;; ### b64-encode

(display-output-format
 ['(k/b64-encode "hello")
  (l/! [:code]
    (k/b64-encode "hello"))
  (l/! [:live]
    (k/b64-encode "hello"))]
 ['(k/apply k/b64-encode ["hello"])
  (l/! [:code]
    (k/apply k/b64-encode ["hello"]))
  (l/! [:live]
    (k/apply k/b64-encode ["hello"]))])

;; ### b64-decode

(display-output-format
 ['(k/b64-decode "aGVsbG8=")
  (l/! [:code]
    (k/b64-decode "aGVsbG8="))
  (l/! [:live]
    (k/b64-decode "aGVsbG8="))]
 ['(k/apply k/b64-decode ["aGVsbG8="])
  (l/! [:code]
    (k/apply k/b64-decode ["aGVsbG8="]))
  (l/! [:live]
    (k/apply k/b64-decode ["aGVsbG8="]))])

^:kindly/hide-code
(comment
  ;; ### uri-encode

  (display-output-format
   ['(k/uri-encode "+.\n ")
    (l/! [:code]
      (k/uri-encode "+.\n "))
    (l/! [:live]
      (k/uri-encode "+.\n "))]
   ['(k/apply k/uri-encode ["+.\n "])
    (l/! [:code]
      (k/apply k/uri-encode ["+.\n "]))
    (l/! [:live]
      (k/apply k/uri-encode ["+.\n "]))])

  ;; ### uri-decode

  (display-output-format
   ['(k/uri-decode "%2B.%0A%20")
    (l/! [:code]
      (k/uri-decode "%2B.%0A%20"))
    (l/! [:live]
      (k/uri-decode "%2B.%0A%20"))]
   ['(k/apply k/uri-decode ["%2B.%0A%20"])
    (l/! [:code]
      (k/apply k/uri-decode ["%2B.%0A%20"]))
    (l/! [:live]
      (k/apply k/uri-decode ["%2B.%0A%20"]))]))

;; ### json-encode

(display-output-format
 ['(k/json-encode [1 2 {:a [{:b 3}]}])
  (l/! [:code]
    (k/json-encode [1 2 {:a [{:b 3}]}]))
  (l/! [:live]
    (k/json-encode [1 2 {:a [{:b 3}]}]))]
 ['(k/apply k/json-encode [[1 2 {:a [{:b 3}]}]])
  (l/! [:code]
    (k/apply k/json-encode [[1 2 {:a [{:b 3}]}]]))
  (l/! [:live]
    (k/apply k/json-encode [[1 2 {:a [{:b 3}]}]]))])

;; ### json-decode

(display-output-format
 ['(k/json-decode "[1,2,{\"a\":[{\"b\":3}]}]")
  (l/! [:code]
    (k/json-decode "[1,2,{\"a\":[{\"b\":3}]}]"))
  (l/! [:live]
    (k/json-decode "[1,2,{\"a\":[{\"b\":3}]}]"))]
 ['(k/apply k/json-decode ["[1,2,{\"a\":[{\"b\":3}]}]"])
  (l/! [:code]
    (k/apply k/json-decode ["[1,2,{\"a\":[{\"b\":3}]}]"]))
  (l/! [:live]
    (k/apply k/json-decode ["[1,2,{\"a\":[{\"b\":3}]}]"]))])

;; ### json-push

(display-output-format
 ['(k/json-push "[1,2,3]" "4")
  (l/! [:code]
    (k/json-push "[1,2,3]" "4"))
  (l/! [:live]
    (k/json-push "[1,2,3]" "4"))]
 ['(k/apply k/json-push ["[1,2,3]" "4"])
  (l/! [:code]
    (k/apply k/json-push ["[1,2,3]" "4"]))
  (l/! [:live]
    (k/apply k/json-push ["[1,2,3]" "4"]))])

;; ### json-push-first

(display-output-format
 ['(k/json-push-first "[1,2,3]" "0")
  (l/! [:code]
    (k/json-push-first "[1,2,3]" "0"))
  (l/! [:live]
    (k/json-push-first "[1,2,3]" "0"))]
 ['(k/apply k/json-push-first ["[1,2,3]" "0"])
  (l/! [:code]
    (k/apply k/json-push-first ["[1,2,3]" "0"]))
  (l/! [:live]
    (k/apply k/json-push-first ["[1,2,3]" "0"]))])

;; ## Base Lib - Symbol

;; ### sym-full

(display-output-format
 ['(k/sym-full "hello" "world")
  (l/! [:code]
    (k/sym-full "hello" "world"))
  (l/! [:live]
    (k/sym-full "hello" "world"))])

;; ### sym-name

(display-output-format
 ['(k/sym-name "hello/world")
  (l/! [:code]
    (k/sym-name "hello/world"))
  (l/! [:live]
    (k/sym-name "hello/world"))])

;; ### sym-ns

(display-output-format
 ['[(k/sym-ns "hello/world")
    #_(k/sym-ns "hello")]
  (l/! [:code]
    '[(k/sym-ns "hello/world")
      #_(k/sym-ns "hello")])
  (l/! [:live]
    '[(k/sym-ns "hello/world")
      #_(k/sym-ns "hello")])])

;; ### sym-pair

(display-output-format
 ['(k/sym-pair "hello/world")
  (l/! [:code]
    (k/sym-pair "hello/world"))
  (l/! [:live]
    (k/sym-pair "hello/world"))])

;; # Base Lib - Math

;; ## Base Lib - Math Basic

;; ### eq

(display-output-format
 ['[(k/eq 2 2) (k/eq 2 1)]
  (l/! [:code]
    [(k/eq 2 2) (k/eq 2 1)])
  (l/! [:live]
    [(k/eq 2 2) (k/eq 2 1)])]
 ['(k/apply k/eq [1 1])
  (l/! [:code]
    (k/apply k/eq [1 1]))
  (l/! [:live]
    (k/apply k/eq [1 1]))])

;; ### neq

(display-output-format
 ['[(k/neq 2 2) (k/neq 2 1)]
  (l/! [:code]
    [(k/neq 2 2) (k/neq 2 1)])
  (l/! [:live]
    [(k/neq 2 2) (k/neq 2 1)])]
 ['(k/apply k/neq [1 1])
  (l/! [:code]
    (k/apply k/neq [1 1]))
  (l/! [:live]
    (k/apply k/neq [1 1]))])

;; ### add

(display-output-format
 ['(k/add 1 2)
  (l/! [:code]
    (k/add 1 2))
  (l/! [:live]
    (k/add 1 2))]
 ['(k/apply k/add [1 2])
  (l/! [:code]
    (k/apply k/add [1 2]))
  (l/! [:live]
    (k/apply k/add [1 3]))])

;; ### sub 

(display-output-format
 ['(k/sub 1 2)
  (l/! [:code]
    (k/sub 1 2))
  (l/! [:live]
    (k/sub 1 2))]
 ['(k/apply k/sub [1 2])
  (l/! [:code]
    (k/apply k/sub [1 2]))
  (l/! [:live]
    (k/apply k/sub [1 2]))])

;; ### mul

(display-output-format
 ['(k/mul 10 10)
  (l/! [:code]
    (k/mul 10 10))
  (l/! [:live]
    (k/mul 10 10))]
 ['(k/apply k/mul [1 2])
  (l/! [:code]
    (k/apply k/mul [1 2]))
  (l/! [:live]
    (k/apply k/mul [1 2]))])

;; ### div

(display-output-format
 ['(k/div 10 2)
  (l/! [:code]
    (k/div 10 2))
  (l/! [:live]
    (k/div 10 2))]
 ['(k/apply k/div [1 2])
  (l/! [:code]
    (k/apply k/div [1 2]))
  (l/! [:live]
    (k/apply k/div [1 2]))])

;; ### gt

(display-output-format
 ['[(k/gt 2 2) (k/gt 2 1)]
  (l/! [:code]
    [(k/gt 2 2) (k/gt 2 1)])
  (l/! [:live]
    [(k/gt 2 2) (k/gt 2 1)])]
 ['(k/apply k/gt [1 2])
  (l/! [:code]
    (k/apply k/gt [1 2]))
  (l/! [:live]
    (k/apply k/gt [1 2]))])

;; ### lt

(display-output-format
 ['[(k/lt 2 2)  (k/lt 1 2)]
  (l/! [:code]
    [(k/lt 2 2)  (k/lt 1 2)])
  (l/! [:live]
    [(k/lt 2 2)  (k/lt 1 2)])]
 ['(k/apply k/lt [1 2])
  (l/! [:code]
    (k/apply k/lt [1 2]))
  (l/! [:live]
    (k/apply k/lt [1 2]))])

;; ### gte

(display-output-format
 ['[(k/gte 2 2) (k/gte 2 1)]
  (l/! [:code]
    [(k/gte 2 2) (k/gte 2 1)])
  (l/! [:live]
    [(k/gte 2 2) (k/gte 2 1)])]
 ['(k/apply k/gte [1 2])
  (l/! [:code]
    (k/apply k/gte [1 2]))
  (l/! [:live]
    (k/apply k/gte [1 2]))])

;; ### lte

(display-output-format
 ['[(k/lte 2 2)  (k/lte 1 2)]
  (l/! [:code]
    [(k/lte 2 2)  (k/lte 1 2)])
  (l/! [:live]
    [(k/lte 2 2)  (k/lte 1 2)])]
 ['(k/apply k/lte [1 2])
  (l/! [:code]
    (k/apply k/lte [1 2]))
  (l/! [:live]
    (k/apply k/lte [1 2]))])

;; ### neg

(display-output-format
 ['[(k/neg 1) (k/neg 0) (k/neg -1)]
  (l/! [:code]
    [(k/neg 1) (k/neg 0) (k/neg -1)])
  (l/! [:live]
    [(k/neg 1) (k/neg 0) (k/neg -1)])]
 ['(k/apply k/neg [1])
  (l/! [:code]
    (k/apply k/neg [1]))
  (l/! [:live]
    (k/apply k/neg [1]))])

;; ### inc

(display-output-format
 ['(k/inc 1)
  (l/! [:code]
    (k/inc 1))
  (l/! [:live]
    (k/inc 1))]
 ['(k/apply k/inc [1])
  (l/! [:code]
    (k/apply k/inc [1]))
  (l/! [:live]
    (k/apply k/inc [1]))])

;; ### dec

(display-output-format
 ['(k/dec 1)
  (l/! [:code]
    (k/dec 1))
  (l/! [:live]
    (k/dec 1))]
 ['(k/apply k/dec [1])
  (l/! [:code]
    (k/apply k/dec [1]))
  (l/! [:live]
    (k/apply k/dec [1]))])

;; ### pow

(display-output-format
 ['(k/pow 2 3)
  (l/! [:code]
    (k/pow 2 3))
  (l/! [:live]
    (k/pow 2 3))]
 ['(k/apply k/pow [5 6])
  (l/! [:code]
    (k/apply k/pow [5 6]))
  (l/! [:live]
    (k/apply k/pow [5 6]))])

;; ### quot

(display-output-format
 ['(k/quot 20 3)
  (l/! [:code]
    (k/quot 20 3))
  (l/! [:live]
    (k/quot 20 3))]
 ['(k/apply k/quot [50 6])
  (l/! [:code]
    (k/apply k/quot [50 6]))
  (l/! [:live]
    (k/apply k/quot [50 6]))])

;; ### sqrt

(display-output-format
 ['[(k/sqrt 4)(k/sqrt 1)]
  (l/! [:code]
    [(k/sqrt 4) (k/sqrt 1)])
  (l/! [:live]
    [(k/sqrt 4) (k/sqrt 1)])]
 ['(k/apply k/sqrt [16])
  (l/! [:code]
    (k/apply k/sqrt [16]))
  (l/! [:live]
    (k/apply k/sqrt [16]))])

;; ### exp

(display-output-format
 ['(k/exp 3)
  (l/! [:code]
    (k/exp 3))
  (l/! [:live]
    (k/exp 3))]
 ['(k/apply k/exp [6])
  (l/! [:code]
    (k/apply k/exp [6]))
  (l/! [:live]
    (k/apply k/exp [6]))])

;; ### loge

(display-output-format
 ['(k/loge 3)
  (l/! [:code]
    (k/loge 3))
  (l/! [:live]
    (k/loge 3))]
 ['(k/apply k/loge [6])
  (l/! [:code]
    (k/apply k/loge [6]))
  (l/! [:live]
    (k/apply k/loge [6]))])

;; ### log10

(display-output-format
 ['(k/log10 3)
  (l/! [:code]
    (k/log10 3))
  (l/! [:live]
    (k/log10 3))]
 ['(k/apply k/log10 [6])
  (l/! [:code]
    (k/apply k/log10 [6]))
  (l/! [:live]
    (k/apply k/log10 [6]))])

;; ### mod

(display-output-format
 ['(k/mod 20 3)
  (l/! [:code]
    (k/mod 20 3))
  (l/! [:live]
    (k/mod 20 3))]
 ['(k/apply k/mod [50 6])
  (l/! [:code]
    (k/apply k/mod [50 6]))
  (l/! [:live]
    (k/apply k/mod [50 6]))])

;; ### mod-pos

(display-output-format
 ['[(mod -11 10)
    (k/mod-pos -11 10)]
  (l/! [:code]
    [(mod -11 10)
     (k/mod-pos -11 10)])
  (l/! [:live]
    [(mod -11 10)
     (k/mod-pos -11 10)])])

;; ### mod-offset

(display-output-format
 ['[(k/mod-offset 20 280 360)
    (k/mod-offset 280 20 360)
    (k/mod-offset 280 -80 360)
    (k/mod-offset 20 -60 360)
    (k/mod-offset 60 30 360)]
  (l/! [:code]
    [(k/mod-offset 20 280 360)
     (k/mod-offset 280 20 360)
     (k/mod-offset 280 -80 360)
     (k/mod-offset 20 -60 360)
     (k/mod-offset 60 30 360)])
  (l/! [:live]
    [(k/mod-offset 20 280 360)
     (k/mod-offset 280 20 360)
     (k/mod-offset 280 -80 360)
     (k/mod-offset 20 -60 360)
     (k/mod-offset 60 30 360)])])

;; ## Base Lib - Math Checks

;; ### zero?

(display-output-format
 ['[(k/zero? 1)
    (k/zero? 0)]
  (l/! [:code]
    [(k/zero? 1)
     (k/zero? 0)])
  (l/! [:live]
    [(k/zero? 1)
     (k/zero? 0)])]
 ['(k/apply k/zero? [1])
  (l/! [:code]
    (k/apply k/zero? [1]))
  (l/! [:live]
    (k/apply k/zero? [1]))])

;; ### pos?

(display-output-format
 ['[(k/pos? 1)
    (k/pos? 0)]
  (l/! [:code]
    [(k/pos? 1)
     (k/pos? 0)])
  (l/! [:live]
    [(k/pos? 1)
     (k/pos? 0)])]
 ['(k/apply k/pos? [-1])
  (l/! [:code]
    (k/apply k/pos? [-1]))
  (l/! [:live]
    (k/apply k/pos? [-1]))])

;; ### neg?

(display-output-format
 ['[(k/neg? -1)
    (k/neg? 0)]
  (l/! [:code]
    [(k/neg? -1)
     (k/neg? 0)])
  (l/! [:live]
    [(k/neg? -1)
     (k/neg? 0)])]
 ['(k/apply k/neg? [-1])
  (l/! [:code]
    (k/apply k/neg? [-1]))
  (l/! [:live]
    (k/apply k/neg? [-1]))])

;; ### even?

(display-output-format
 ['[(k/even? 2)
    (k/even? 1)]
  (l/! [:code]
    [(k/even? 2)
     (k/even? 1)])
  (l/! [:live]
    [(k/even? 2)
     (k/even? 1)])]
 ['(k/apply k/even? [-1])
  (l/! [:code]
    (k/apply k/even? [-1]))
  (l/! [:live]
    (k/apply k/even? [-1]))])

;; ### odd?

(display-output-format
 ['[(k/odd? 2)
    (k/odd? 1)]
  (l/! [:code]
    [(k/odd? 2)
     (k/odd? 1)])
  (l/! [:live]
    [(k/odd? 2)
     (k/odd? 1)])]
 ['(k/apply k/odd? [-1])
  (l/! [:code]
    (k/apply k/odd? [-1]))
  (l/! [:live]
    (k/apply k/odd? [-1]))])

;; ## Base Lib - Math Util

;; ### abs

(display-output-format
 ['[(k/abs -1) (k/abs 1)]
  (l/! [:code]
    [(k/abs -1) (k/abs 1)])
  (l/! [:live]
    [(k/abs -1) (k/abs 1)])]
 ['(k/apply k/abs [-1])
  (l/! [:code]
    (k/apply k/abs [-1]))
  (l/! [:live]
    (k/apply k/abs [-1]))])

;; ### max

(display-output-format
 ['(k/max 1 2 3 2)
  (l/! [:code]
    (k/max 1 2 3 2))
  (l/! [:live]
    (k/max 1 2 3 2))]
 ['(k/apply k/max [1 2 3 2])
  (l/! [:code]
    (k/apply k/max [1 2 3 2]))
  (l/! [:live]
    (k/apply k/max [1 2 3 2]))])

;; ### min

(display-output-format
 ['(k/min 1 2 3 2)
  (l/! [:code]
    (k/min 1 2 3 2))
  (l/! [:live]
    (k/min 1 2 3 2))]
 ['(k/apply k/min [1 2 3 2])
  (l/! [:code]
    (k/apply k/min [1 2 3 2]))
  (l/! [:live]
    (k/apply k/min [1 2 3 2]))])

;; ### ceil

(display-output-format
 ['[(k/ceil -1.1) (k/ceil 1.1)]
  (l/! [:code]
    [(k/ceil -1.1) (k/ceil 1.1)])
  (l/! [:live]
    [(k/ceil -1.1) (k/ceil 1.1)])]
 ['(k/apply k/ceil [-1.1])
  (l/! [:code]
    (k/apply k/ceil [-1.1]))
  (l/! [:live]
    (k/apply k/ceil [-1.1]))])

;; ### floor

(display-output-format
 ['[(k/floor -1.1) (k/floor 1.1)]
  (l/! [:code]
    [(k/floor -1.1) (k/floor 1.1)])
  (l/! [:live]
    [(k/floor -1.1) (k/floor 1.1)])]
 ['(k/apply k/floor [-1.1])
  (l/! [:code]
    (k/apply k/floor [-1.1]))
  (l/! [:live]
    (k/apply k/floor [-1.1]))])

;; ### gcd

(display-output-format
 ['(k/gcd 10 6)
  (l/! [:code]
    (k/gcd 10 6))
  (l/! [:live]
    (k/gcd 10 6))])

;; ### lcm

(display-output-format
 ['(k/lcm 10 6)
  (l/! [:code]
    (k/lcm 10 6))
  (l/! [:live]
    (k/lcm 10 6))])

;; ### mix

(display-output-format
 ['(k/mix 100 20 0.1 nil)
  (l/! [:code]
    (k/mix 100 20 0.1 nil))
  (l/! [:live]
    (k/mix 100 20 0.1 nil))])

;; ### sign

(display-output-format
 ['[(k/sign -10) (k/sign 10)]
  (l/! [:code]
    [(k/sign -10) (k/sign 10)])
  (l/! [:live]
    [(k/sign -10) (k/sign 10)])])

;; ### round

(display-output-format
 ['[(k/round 0.9)
    (k/round 1.1)
    (k/round 1.49)
    (k/round 1.51)]
  (l/! [:code]
    [(k/round 0.9)
     (k/round 1.1)
     (k/round 1.49)
     (k/round 1.51)])
  (l/! [:live]
    [(k/round 0.9)
     (k/round 1.1)
     (k/round 1.49)
     (k/round 1.51)])])

;; ### clamp

(display-output-format
 ['[(k/clamp 0 5 6)
    (k/clamp 0 5 -1)
    (k/clamp 0 5 4)]
  (l/! [:code]
    [(k/clamp 0 5 6)
     (k/clamp 0 5 -1)
     (k/clamp 0 5 4)])
  (l/! [:live]
    [(k/clamp 0 5 6)
     (k/clamp 0 5 -1)
     (k/clamp 0 5 4)])])

;; ## Base Lib - Math Bitwise

;; ### bit-and

(display-output-format
 ['(k/bit-and 7 4)
  (l/! [:code]
    (k/bit-and 7 4))
  (l/! [:live]
    (k/bit-and 7 4))]
 ['(k/apply k/bit-and [7 4])
  (l/! [:code]
    (k/apply k/bit-and [7 4]))
  (l/! [:live]
    (k/apply k/bit-and [7 4]))])

;; ### bit-or

(display-output-format
 ['(k/bit-or 3 4)
  (l/! [:code]
    (k/bit-or 3 4))
  (l/! [:live]
    (k/bit-or 3 4))]
 ['(k/apply k/bit-or [3 4])
  (l/! [:code]
    (k/apply k/bit-or [3 4]))
  (l/! [:live]
    (k/apply k/bit-or [3 4]))])

;; ### bit-xor

(display-output-format
 ['(k/bit-xor 3 5)
  (l/! [:code]
    (k/bit-xor 3 5))
  (l/! [:live]
    (k/bit-xor 3 5))]
 ['(k/apply k/bit-xor [3 5])
  (l/! [:code]
    (k/apply k/bit-xor [3 5]))
  (l/! [:live]
    (k/apply k/bit-xor [3 5]))])

;; ### bit-lshift

(display-output-format
 ['(k/bit-lshift 7 1)
  (l/! [:code]
    (k/bit-lshift 7 1))
  (l/! [:live]
    (k/bit-lshift 7 1))]
 ['(k/apply k/bit-lshift [7 1])
  (l/! [:code]
    (k/apply k/bit-lshift [7 1]))
  (l/! [:live]
    (k/apply k/bit-lshift [7 1]))])

;; ### bit-rshift

(display-output-format
 ['(k/bit-rshift 7 1)
  (l/! [:code]
    (k/bit-rshift 7 1))
  (l/! [:live]
    (k/bit-rshift 7 1))]
 ['(k/apply k/bit-rshift [7 1])
  (l/! [:code]
    (k/apply k/bit-rshift [7 1]))
  (l/! [:live]
    (k/apply k/bit-rshift [7 1]))])

;; ### bit-count

(display-output-format
 ['[(k/bit-count 16)
    (k/bit-count 10)
    (k/bit-count 3)
    (k/bit-count 7)]
  (l/! [:code]
    [(k/bit-count 16)
     (k/bit-count 10)
     (k/bit-count 3)
     (k/bit-count 7)])
  (l/! [:live]
    [(k/bit-count 16)
     (k/bit-count 10)
     (k/bit-count 3)
     (k/bit-count 7)])])

;; ## Base Lib - Math Trigonometry

;; ### sin

(display-output-format
 ['[(k/sin (/ 3.14159 4))
    (k/sin (/ 3.14159 6))]
  (l/! [:code]
    [(k/sin (/ 3.14159 4))
     (k/sin (/ 3.14159 6))])
  (l/! [:live]
    [(k/sin (/ 3.14159 4))
     (k/sin (/ 3.14159 6))])]
 ['(k/apply k/sin [(/ 3.14159 4)])
  (l/! [:code]
    (k/apply k/sin [(/ 3.14159 4)]))
  (l/! [:live]
    (k/apply k/sin [(/ 3.14159 4)]))])

;; ### cos

(display-output-format
 ['[(k/cos (/ 3.14159 4))
    (k/cos (/ 3.14159 6))]
  (l/! [:code]
    [(k/cos (/ 3.14159 4))
     (k/cos (/ 3.14159 6))])
  (l/! [:live]
    [(k/cos (/ 3.14159 4))
     (k/cos (/ 3.14159 6))])]
 ['(k/apply k/cos [(/ 3.14159 4)])
  (l/! [:code]
    (k/apply k/cos [(/ 3.14159 4)]))
  (l/! [:live]
    (k/apply k/cos [(/ 3.14159 4)]))])

;; ### tan

(display-output-format
 ['[(k/tan (/ 3.14159 4))
    (k/tan (/ 3.14159 6))]
  (l/! [:code]
    [(k/tan (/ 3.14159 4))
     (k/tan (/ 3.14159 6))])
  (l/! [:live]
    [(k/tan (/ 3.14159 4))
     (k/tan (/ 3.14159 6))])]
 ['(k/apply k/tan [(/ 3.14159 4)])
  (l/! [:code]
    (k/apply k/tan [(/ 3.14159 4)]))
  (l/! [:live]
    (k/apply k/tan [(/ 3.14159 4)]))])

;; ### asin

(display-output-format
 ['[(k/asin 0.5)
    (k/asin 0.8)]
  (l/! [:code]
    [(k/asin 0.5)
     (k/asin 0.8)])
  (l/! [:live]
    [(k/asin 0.5)
     (k/asin 0.8)])]
 ['(k/apply k/asin [0.5])
  (l/! [:code]
    (k/apply k/asin [0.5]))
  (l/! [:live]
    (k/apply k/asin [0.5]))])

;; ### acos

(display-output-format
 ['[(k/acos 0.5)
    (k/acos 0.8)]
  (l/! [:code]
    [(k/acos 0.5)
     (k/acos 0.8)])
  (l/! [:live]
    [(k/acos 0.5)
     (k/acos 0.8)])]
 ['(k/apply k/acos [0.5])
  (l/! [:code]
    (k/apply k/acos [0.5]))
  (l/! [:live]
    (k/apply k/acos [0.5]))])

;; ### atan

(display-output-format
 ['[(k/atan 0.5)
    (k/atan 0.8)]
  (l/! [:code]
    [(k/atan 0.5)
     (k/atan 0.8)])
  (l/! [:live]
    [(k/atan 0.5)
     (k/atan 0.8)])]
 ['(k/apply k/atan [0.5])
  (l/! [:code]
    (k/apply k/atan [0.5]))
  (l/! [:live]
    (k/apply k/atan [0.5]))])

;; ### sinh

(display-output-format
 ['[(k/sinh (/ 3.14159 4))
    (k/sinh (/ 3.14159 6))]
  (l/! [:code]
    [(k/sinh (/ 3.14159 4))
     (k/sinh (/ 3.14159 6))])
  (l/! [:live]
    [(k/sinh (/ 3.14159 4))
     (k/sinh (/ 3.14159 6))])]
 ['(k/apply k/sinh [(/ 3.14159 4)])
  (l/! [:code]
    (k/apply k/sinh [(/ 3.14159 4)]))
  (l/! [:live]
    (k/apply k/sinh [(/ 3.14159 4)]))])

;; ### cosh

(display-output-format
 ['[(k/cosh (/ 3.14159 4))
    (k/cosh (/ 3.14159 6))]
  (l/! [:code]
    [(k/cosh (/ 3.14159 4))
     (k/cosh (/ 3.14159 6))])
  (l/! [:live]
    [(k/cosh (/ 3.14159 4))
     (k/cosh (/ 3.14159 6))])]
 ['(k/apply k/cosh [(/ 3.14159 4)])
  (l/! [:code]
    (k/apply k/cosh [(/ 3.14159 4)]))
  (l/! [:live]
    (k/apply k/cosh [(/ 3.14159 4)]))])

;; ### tanh

(display-output-format
 ['[(k/tanh (/ 3.14159 4))
    (k/tanh (/ 3.14159 6))]
  (l/! [:code]
    [(k/tanh (/ 3.14159 4))
     (k/tanh (/ 3.14159 6))])
  (l/! [:live]
    [(k/tanh (/ 3.14159 4))
     (k/tanh (/ 3.14159 6))])]
 ['(k/apply k/tanh [(/ 3.14159 4)])
  (l/! [:code]
    (k/apply k/tanh [(/ 3.14159 4)]))
  (l/! [:live]
    (k/apply k/tanh [(/ 3.14159 4)]))])


^:kindly/hide-code
(do 
  (l/annex:restart-all))

;; # Base Lib - Collection

;; ## Base Lib - Sequence

;; ### first

(display-output-format
 ['(k/first [1 2 3])
  (l/! [:code]
    (k/first [1 2 3]))
  (l/! [:live]
    (k/first [1 2 3]))]
 ['(k/apply [k/first [[1 2 3]]])
  (l/! [:code]
    (k/apply k/first [[1 2 3]]))
  (l/! [:live]
    (k/apply k/first [[1 2 3]]))])

;; ### second

(display-output-format
 ['(k/second [1 2 3])
  (l/! [:code]
    (k/second [1 2 3]))
  (l/! [:live]
    (k/second [1 2 3]))]
 ['(k/apply [k/second [[1 2 3]]])
  (l/! [:code]
    (k/apply k/second [[1 2 3]]))
  (l/! [:live]
    (k/apply k/second [[1 2 3]]))])

;; ### nth

(display-output-format
 ['(k/nth [1 2 3] 2)
  (l/! [:code]
    (k/nth [1 2 3] 2))
  (l/! [:live]
    (k/nth [1 2 3] 2))]
 ['(k/apply [k/nth [[1 2 3] 2]])
  (l/! [:code]
    (k/apply k/nth [[1 2 3] 2]))
  (l/! [:live]
    (k/apply k/nth [[1 2 3] 2]))])

;; ### last

(display-output-format
 ['(k/last [1 2 3])
  (l/! [:code]
    (k/last [1 2 3]))
  (l/! [:live]
    (k/last [1 2 3]))]
 ['(k/apply [k/last [[1 2 3]]])
  (l/! [:code]
    (k/apply k/last [[1 2 3]]))
  (l/! [:live]
    (k/apply k/last [[1 2 3]]))])

;; ### second-last

(display-output-format
 ['(k/second-last [1 2 3])
  (l/! [:code]
    (k/second-last [1 2 3]))
  (l/! [:live]
    (k/second-last [1 2 3]))]
 ['(k/apply [k/second-last [[1 2 3]]])
  (l/! [:code]
    (k/apply k/second-last [[1 2 3]]))
  (l/! [:live]
    (k/apply k/second-last [[1 2 3]]))])

;; ### get-idx

(display-output-format
 ['[(k/get-idx [1 2 3] 1)
    (k/get-idx [1 2 3] 2)]
  (l/! [:code]
    [(k/get-idx [1 2 3] 1)
    (k/get-idx [1 2 3] 2)])
  (l/! [:live]
    [(k/get-idx [1 2 3] 1)
    (k/get-idx [1 2 3] 2)])]
 ['(k/apply k/get-idx [[1 2 3] 1 nil])
  (l/! [:code]
    (k/apply k/get-idx [[1 2 3] 1 nil]))
  (l/! [:live]
    (k/apply k/get-idx [[1 2 3] 1 nil]))])

;; ### set-idx

(display-output-format
 ['(do (var out := [1 2 3 4 5])
       (k/set-idx out 2 5)
       out)
  (l/! [:code]
    (do (var out := [1 2 3 4 5])
        (k/set-idx out 2 5)
        out))
  (l/! [:live]
    (do (var out := [1 2 3 4 5])
        (k/set-idx out 2 5)
        out))]
 #_['(do (var out := [1 2 3 4 5])
         (k/apply k/set-idx [out 2 5])
         out)
    (l/! [:code]
      (do (var out := [1 2 3 4 5])
          (k/apply k/set-idx [out 2 5])
          out))
    (l/! [:live]
      (do (var out := [1 2 3 4 5])
          (k/apply k/set-idx [out 2 5])
          out))])

;; ### is-empty?

(display-output-format
 ['[(k/is-empty? nil)
    (k/is-empty? "")
    (k/is-empty? "123")
    (k/is-empty? [])
    (k/is-empty? [1 2 3])
    (k/is-empty? {})
    (k/is-empty? {:a 1 :b 2})]
  (l/! [:code]
    [(k/is-empty? nil)
     (k/is-empty? "")
     (k/is-empty? "123")
     (k/is-empty? [])
     (k/is-empty? [1 2 3])
     (k/is-empty? {})
     (k/is-empty? {:a 1 :b 2})])
  (l/! [:live]
    [(k/is-empty? nil)
     (k/is-empty? "")
     (k/is-empty? "123")
     (k/is-empty? [])
     (k/is-empty? [1 2 3])
     (k/is-empty? {})
     (k/is-empty? {:a 1 :b 2})])])

;; ## Base Lib - Keys

;; ### has-key?

(display-output-format
 ['[(k/has-key? {:a 1} "a")
    (k/has-key? {:a 1} "b")]
  (l/! [:code]
    [(k/has-key? {:a 1} "a")
     (k/has-key? {:a 1} "b")])
  (l/! [:live]
    [(k/has-key? {:a 1} "a")
     (k/has-key? {:a 1} "b")])]
 ['(k/apply k/has-key? [{:a 1} "a"])
  (l/! [:code]
    (k/apply k/has-key? [{:a 1} "a"]))
  (l/! [:live]
    (k/apply k/has-key? [{:a 1} "a"]))])

;; ### del-key

(display-output-format
 ['(do (var out := {:a 1 :b 2})
       (k/del-key out "a")
       out)
  (l/! [:code]
    (do (var out := {:a 1 :b 2})
        (k/del-key out "a")
        out))
  (l/! [:live]
    (do (var out := {:a 1 :b 2})
        (k/del-key out "a")
        out))]
 #_['(do (var out := {:a 1 :b 2})
       (k/apply k/del-key [out "a"])
       out)
  (l/! [:code]
    (do (var out := {:a 1 :b 2})
       (k/apply k/del-key [out "a"])
       out))
  (l/! [:live]
    (do (var out := {:a 1 :b 2})
       (k/apply k/del-key [out "a"])
       out))])

;; ### get-key

(display-output-format
 ['[(k/get-key {:a 1} "a")
    (k/get-key {:a 1} "b")]
  (l/! [:code]
    [(k/get-key {:a 1} "a")
     (k/get-key {:a 1} "b")])
  (l/! [:live]
    [(k/get-key {:a 1} "a")
     (k/get-key {:a 1} "b")])]
 #_['(k/apply k/get-key [{:a 1} "a"])
  (l/! [:code]
    (k/apply k/get-key [{:a 1} "a"]))
  (l/! [:live]
    (k/apply k/get-key [{:a 1} "a"]))]
 
 #_['(k/apply k/get-key [{:a 1} "b" 2])
  (l/! [:code]
    (k/apply k/get-key [{:a 1} "b" 2]))
  (l/! [:live]
    (k/apply k/get-key [{:a 1} "b" 2]))])

;; ### get-path

(display-output-format
 #_['[(k/get-path {:a {:b {:c 1}}} ["a" "b" "c"] nil)
    (k/get-path {:a 1} ["b"] 2)]
  (l/! [:code]
    [(k/get-path {:a {:b {:c 1}}} ["a" "b" "c"] nil)
    (k/get-path {:a 1} ["b"] 2)])
  (l/! [:live]
    [(k/get-path {:a {:b {:c 1}}} ["a" "b" "c"] nil)
    (k/get-path {:a 1} ["b"] 2)])])

;; ### set-key

(display-output-format
 ['(do (var out := {:a 1 :b 2})
       (k/set-key out "a" 5)
       out)
  (l/! [:code]
    (do (var out := {:a 1 :b 2})
        (k/set-key out "a" 5)
        out))
  (l/! [:live]
    (do (var out := {:a 1 :b 2})
        (k/set-key out "a" 5)
        out))]
 #_['(do (var out := {:a 1 :b 2})
       (k/apply k/set-key [out "a" 5])
       out)
  (l/! [:code]
    (do (var out := {:a 1 :b 2})
       (k/apply k/set-key [out "a" 5])
       out))
  (l/! [:live]
    (do (var out := {:a 1 :b 2})
        (k/apply k/set-key [out "a" 5])
       out))])

;; ### copy-key

(display-output-format
 ['(do (var out := {})
       (k/copy-key out {:a 1} "a")
       out)
  (l/! [:code]
    (do (var out := {})
        (k/copy-key out {:a 1} "a")
        out))
  (l/! [:live]
    (do (var out := {})
        (k/copy-key out {:a 1} "a")
        out))]
 ['(do (var out := {})
       (k/copy-key out {:a 1} ["c" "a"])
       out)
  (l/! [:code]
    (do (var out := {})
        (k/copy-key out {:a 1} ["c" "a"])
        out))
  (l/! [:live]
    (do (var out := {})
        (k/copy-key out {:a 1} ["c" "a"])
        out))]
 #_['(do (var out := {})
       (k/apply k/copy-key [out {:a 1 :b 2} "a"])
       out)
  (l/! [:code]
    (do (var out := {})
       (k/apply k/copy-key [out {:a 1 :b 2} "a"])
       out))
  (l/! [:live]
    (do (var out := {})
       (k/apply k/copy-key [out {:a 1 :b 2} "a"])
       out))])

;; ### swap-key

(display-output-format
 ['(do (var out := {:a 1 :b 2})
       (k/swap-key out "a" k/inc)
       out)
  (l/! [:code]
    (do (var out := {:a 1 :b 2})
        (k/swap-key out "a" k/inc)
        out))
  (l/! [:live]
    (do (var out := {:a 1 :b 2})
        (k/swap-key out "a" k/inc)
        out))])

;; ## Base Lib - Array

;; ### x:arr-push

(display-output-format
 ['(do
     (var out [1 2 3])
     (k/x:arr-push out 4)
     out)
  (l/! [:code]
    (do
      (var out [1 2 3])
      (k/x:arr-push out 4)
      out))
  (l/! [:live]
    (do
      (var out [1 2 3])
      (k/x:arr-push out 4)
      out))])

;; ### x:arr-pop

(display-output-format
 ['(do
     (var out [1 2 3])
     (k/x:arr-pop out)
     out)
  (l/! [:code]
    (do
     (var out [1 2 3])
     (k/x:arr-pop out)
     out))
  (l/! [:live]
    (do
     (var out [1 2 3])
     (k/x:arr-pop out)
     out))])

;; ### x:arr-push-first

(display-output-format
 ['(do
     (var out [1 2 3])
     (k/x:arr-push-first out 0)
     out)
  (l/! [:code]
    (do
     (var out [1 2 3])
     (k/x:arr-push-first out 0)
     out))
  (l/! [:live]
    (do
     (var out [1 2 3])
     (k/x:arr-push-first out 0)
     out))])

;; ### x:arr-pop-first

(display-output-format
 ['(do
     (var out [1 2 3])
     (k/x:arr-pop-first out)
     out)
  (l/! [:code]
    (do
     (var out [1 2 3])
     (k/x:arr-pop-first out)
     out))
  (l/! [:live]
    (do
     (var out [1 2 3])
     (k/x:arr-pop-first out)
     out))])

;; ### x:arr-insert

(display-output-format
 ['(do
     (var out [1 2 3])
     (k/x:arr-insert out (x:offset 2) "a")
     out)
  (l/! [:code]
    (do
     (var out [1 2 3])
     (k/x:arr-insert out (x:offset 2) "a")
     out))
  (l/! [:live]
    (do
     (var out [1 2 3])
     (k/x:arr-insert out (x:offset 2) "a")
     out))])

;; ### arr-lookup

(display-output-format
 ['(k/arr-lookup ["a" "b" "c"])
  (l/! [:code]
    (k/arr-lookup ["a" "b" "c"]))
  (l/! [:live]
    (k/arr-lookup ["a" "b" "c"]))])

;; ### arr-every

(display-output-format
 ['[(k/arr-every [1 2 3] k/odd?)
    (k/arr-every [1 3] k/odd?)]
  (l/! [:code]
    [(k/arr-every [1 2 3] k/odd?)
     (k/arr-every [1 3] k/odd?)])
  (l/! [:live]
    [(k/arr-every [1 2 3] k/odd?)
     (k/arr-every [1 3] k/odd?)])])


;; ### arr-some

(display-output-format
 ['[(k/arr-some [1 2 3] k/even?)
    (k/arr-some [1 3] k/even?)]
  (l/! [:code]
    [(k/arr-some [1 2 3] k/even?)
     (k/arr-some [1 3] k/even?)])
  (l/! [:live]
    [(k/arr-some [1 2 3] k/even?)
     (k/arr-some [1 3] k/even?)])])


;; ### arr-each

(display-output-format
 ['(do
     (var out [])
     (k/arr-each [1 2 3 4 5]
                 (fn [e]
                   (x:arr-push out (+ 1 e))))
     out)
  (l/! [:code]
    (do (var out [])
        (k/arr-each [1 2 3 4 5]
                    (fn [e]
                      (x:arr-push out (+ 1 e))))
        out))
  (l/! [:live]
    (do
      (var out [])
      (k/arr-each [1 2 3 4 5]
                  (fn [e]
                    (x:arr-push out (+ 1 e))))
      out))])

;; ### arr-omit

(display-output-format
 ['(k/arr-omit ["a" "b" "c" "d"]
               2)
  (l/! [:code]
    (k/arr-omit ["a" "b" "c" "d"]
                2))
  (l/! [:live]
    (k/arr-omit ["a" "b" "c" "d"]
                2))])

;; ### arr-reverse

(display-output-format
 ['(k/arr-reverse [1 2 3 4 5])
  (l/! [:code]
    (k/arr-reverse [1 2 3 4 5]))
  (l/! [:live]
    (k/arr-reverse [1 2 3 4 5]))])

;; ### arr-find

(display-output-format
 ['(k/arr-find [1 2 3 4 5] (fn:> [x] (== x 3)))
  (l/! [:code]
    (k/arr-find [1 2 3 4 5] (fn:> [x] (== x 3))))
  (l/! [:live]
    (k/arr-find [1 2 3 4 5] (fn:> [x] (== x 3))))])

;; ### arr-zip

(display-output-format
 ['(k/arr-zip ["a" "b" "c"]
              [1 2 3])
  (l/! [:code]
    (k/arr-zip ["a" "b" "c"]
               [1 2 3]))
  (l/! [:live]
    (k/arr-zip ["a" "b" "c"]
               [1 2 3]))])

;; ### arr-map

(display-output-format
 ['(k/arr-map [1 2 3 4 5] k/inc)
  (l/! [:code]
    (k/arr-map [1 2 3 4 5] k/inc))
  (l/! [:live]
    (k/arr-map [1 2 3 4 5] k/inc))])

;; ### arr-clone

(display-output-format
 ['(k/arr-clone [1 2 3])
  (l/! [:code]
    (k/arr-clone [1 2 3]))
  (l/! [:live]
    (k/arr-clone [1 2 3]))])

;; ### arr-append

(display-output-format
 ['(do
     (var out [1 2 3])
     (k/arr-append out [4 5])
     out)
  (l/! [:code]
    (do
     (var out [1 2 3])
     (k/arr-append out [4 5])
     out))
  (l/! [:live]
    (do
      (var out [1 2 3])
      (k/arr-append out [4 5])
      out))])

;; ### arr-slice

(display-output-format
 ['(k/arr-slice [1 2 3 4 5] 1 3)
  (l/! [:code]
    (k/arr-slice [1 2 3 4 5] 1 3))
  (l/! [:live]
    (k/arr-slice [1 2 3 4 5] 1 3))])

;; ### arr-rslice

(display-output-format
 ['(k/arr-rslice [1 2 3 4 5] 1 3)
  (l/! [:code]
    (k/arr-rslice [1 2 3 4 5] 1 3))
  (l/! [:live]
    (k/arr-rslice [1 2 3 4 5] 1 3))])

;; ### arr-tail

(display-output-format
 ['(k/arr-tail [1 2 3 4 5] 3)
  (l/! [:code]
    (k/arr-tail [1 2 3 4 5] 3))
  (l/! [:live]
    (k/arr-tail [1 2 3 4 5] 3))])

;; ### arr-mapcat

(display-output-format
 ['(k/arr-mapcat [1 2 3] (fn:> [k] [k k k]))
  (l/! [:code]
    (k/arr-mapcat [1 2 3] (fn:> [k] [k k k])))
  (l/! [:live]
    (k/arr-mapcat [1 2 3] (fn:> [k] [k k k])))])

;; ### arr-partition

(display-output-format
 ['(k/arr-partition [1 2 3 4 5 6 7 8 9 10]
                    3)
  (l/! [:code]
    (k/arr-partition [1 2 3 4 5 6 7 8 9 10]
                     3))
  (l/! [:live]
    (k/arr-partition [1 2 3 4 5 6 7 8 9 10]
                    3))])

;; ### arr-filter

(display-output-format
 ['(k/arr-filter [1 2 3 4 5] k/odd?)
  (l/! [:code]
    (k/arr-filter [1 2 3 4 5] k/odd?))
  (l/! [:live]
    (k/arr-filter [1 2 3 4 5] k/odd?))])

;; ### arr-keep

(display-output-format
 ['(k/arr-keep [1 2 3 4 5] (fn:> [x]
                             (:? (k/odd? x) x)))
  (l/! [:code]
    (k/arr-keep [1 2 3 4 5] (fn:> [x]
                              (:? (k/odd? x) x))))
  (l/! [:live]
    (k/arr-keep [1 2 3 4 5] (fn:> [x]
                              (:? (k/odd? x) x))))])

;; ### arr-keepf

(display-output-format
 ['(k/arr-keepf [1 2 3 4 5] k/odd? k/identity)
  (l/! [:code]
    (k/arr-keepf [1 2 3 4 5] k/odd? k/identity))
  (l/! [:live]
    (k/arr-keepf [1 2 3 4 5] k/odd? k/identity))])

;; ### arr-juxt

(display-output-format
 ['(k/arr-juxt [["a" 1] ["b" 2] ["c" 3]]
               k/first
               k/second)
  (l/! [:code]
    (k/arr-juxt [["a" 1] ["b" 2] ["c" 3]]
               k/first
               k/second))
  (l/! [:live]
    (k/arr-juxt [["a" 1] ["b" 2] ["c" 3]]
               k/first
               k/second))])

;; ### arr-foldl

(display-output-format
 ['(k/arr-foldl [1 2 3 4 5] k/add 0)
  (l/! [:code]
    (k/arr-foldl [1 2 3 4 5] k/add 0))
  (l/! [:live]
    (k/arr-foldl [1 2 3 4 5] k/add 0))])

;; ### arr-foldr

(display-output-format
 ['(k/arr-foldr [1 2 3 4 5] k/step-push [])
  (l/! [:code]
    (k/arr-foldr [1 2 3 4 5] k/step-push []))
  (l/! [:live]
    (k/arr-foldr [1 2 3 4 5] k/step-push []))])

;; ### arr-pipel

(display-output-format
 ['(k/arr-pipel [(fn:> [x] (* x 10))
                 (fn:> [x] (+ x 10))]
                1)
  (l/! [:code]
    (k/arr-pipel [(fn:> [x] (* x 10))
                  (fn:> [x] (+ x 10))]
                 1))
  (l/! [:live]
    (k/arr-pipel [(fn:> [x] (* x 10)) (fn:> [x] (+ x 10))] 1))])

;; ### arr-piper

(display-output-format
 ['(k/arr-piper [(fn:> [x] (* x 10))
                 (fn:> [x] (+ x 10))] 1)
  (l/! [:code]
    (k/arr-piper [(fn:> [x] (* x 10))
                  (fn:> [x] (+ x 10))] 1))
  (l/! [:live]
    (k/arr-piper [(fn:> [x] (* x 10))
                  (fn:> [x] (+ x 10))] 1))])

;; ### arr-group-by

(display-output-format
 ['(k/arr-group-by [["a" 1] ["a" 2] ["b" 3] ["b" 4]]
                   k/first
                   k/second)
  (l/! [:code]
    (k/arr-group-by [["a" 1] ["a" 2] ["b" 3] ["b" 4]]
                   k/first
                   k/second))
  (l/! [:live]
    (k/arr-group-by [["a" 1] ["a" 2] ["b" 3] ["b" 4]]
                   k/first
                   k/second))])

;; ### arr-range

(display-output-format
 ['[(k/arr-range 10)
    (k/arr-range [10])
    (k/arr-range [2 8])
    (k/arr-range [2 9 2])]
  (l/! [:code]
    [(k/arr-range 10)
     (k/arr-range [10])
     (k/arr-range [2 8])
     (k/arr-range [2 9 2])])
  (l/! [:live]
    [(k/arr-range 10)
     (k/arr-range [10])
     (k/arr-range [2 8])
     (k/arr-range [2 9 2])])])

;; ### arr-intersection

(display-output-format
 ['(k/arr-intersection ["a" "b" "c" "d"]
                       ["c" "d" "e" "f"])
  (l/! [:code]
    (k/arr-intersection ["a" "b" "c" "d"]
                        ["c" "d" "e" "f"]))
  (l/! [:live]
    (k/arr-intersection ["a" "b" "c" "d"]
                            ["c" "d" "e" "f"]))])

;; ### arr-difference

(display-output-format
 ['(k/arr-difference ["a" "b" "c" "d"]
                     ["c" "d" "e" "f"])
  (l/! [:code]
    (k/arr-difference ["a" "b" "c" "d"]
                      ["c" "d" "e" "f"]))
  (l/! [:live]
    (k/arr-difference ["a" "b" "c" "d"]
                      ["c" "d" "e" "f"]))])

;; ### arr-union 

(display-output-format
 ['(k/arr-union ["a" "b" "c" "d"]
                ["c" "d" "e" "f"])
  (l/! [:code]
    (k/arr-union ["a" "b" "c" "d"]
                 ["c" "d" "e" "f"]))
  (l/! [:live]
    (k/arr-union ["a" "b" "c" "d"]
                 ["c" "d" "e" "f"]))])

;; ### arr-sort

(display-output-format
 ['[(k/arr-sort [3 4 1 2] k/identity (fn:> [a b] (< a b)))
    (k/arr-sort [3 4 1 2] k/identity (fn:> [a b] (< b a)))
    (k/arr-sort [["c" 3] ["d" 4] ["a" 1] ["b" 2] ] k/first  (fn:> [a b] (x:arr-str-comp a b)))
    (k/arr-sort [["c" 3] ["d" 4] ["a" 1] ["b" 2] ] k/second (fn:> [a b] (< a b)))]
  (l/! [:code]
    [(k/arr-sort [3 4 1 2] k/identity (fn:> [a b] (< a b)))
    (k/arr-sort [3 4 1 2] k/identity (fn:> [a b] (< b a)))
    (k/arr-sort [["c" 3] ["d" 4] ["a" 1] ["b" 2] ] k/first  (fn:> [a b] (x:arr-str-comp a b)))
     (k/arr-sort [["c" 3] ["d" 4] ["a" 1] ["b" 2] ] k/second (fn:> [a b] (< a b)))])
  (l/! [:live]
    [(k/arr-sort [3 4 1 2] k/identity (fn:> [a b] (< a b)))
    (k/arr-sort [3 4 1 2] k/identity (fn:> [a b] (< b a)))
    (k/arr-sort [["c" 3] ["d" 4] ["a" 1] ["b" 2] ] k/first  (fn:> [a b] (x:arr-str-comp a b)))
    (k/arr-sort [["c" 3] ["d" 4] ["a" 1] ["b" 2] ] k/second (fn:> [a b] (< a b)))])])

;; ### arr-sorted-merge

(display-output-format
 ['[(k/arr-sorted-merge [1 2 3] [4 5 6] k/lt)
    (k/arr-sorted-merge [1 2 4] [3 5 6] k/lt)
    (k/arr-sorted-merge (k/arr-reverse [1 2 4])
                        (k/arr-reverse [3 5 6]) k/gt)]
  (l/! [:code]
    [(k/arr-sorted-merge [1 2 3] [4 5 6] k/lt)
     (k/arr-sorted-merge [1 2 4] [3 5 6] k/lt)
     (k/arr-sorted-merge (k/arr-reverse [1 2 4])
                         (k/arr-reverse [3 5 6]) k/gt)])
  (l/! [:live]
    [(k/arr-sorted-merge [1 2 3] [4 5 6] k/lt)
     (k/arr-sorted-merge [1 2 4] [3 5 6] k/lt)
     (k/arr-sorted-merge (k/arr-reverse [1 2 4])
                         (k/arr-reverse [3 5 6]) k/gt)])])

;; ### arr-shuffle

(display-output-format
 ['(k/arr-shuffle [1 2 3 4 5])
  (l/! [:code]
    (k/arr-shuffle [1 2 3 4 5]))
  (l/! [:live]
    (k/arr-shuffle [1 2 3 4 5]))])

;; ### arr-pushl

(display-output-format
 ['[(k/arr-pushl [1 2 3 4] 5 100)
    (k/arr-pushl [1 2 3 4] 5 4)]
  (l/! [:code]
    [(k/arr-pushl [1 2 3 4] 5 100)
     (k/arr-pushl [1 2 3 4] 5 4)])
  (l/! [:live]
    [(k/arr-pushl [1 2 3 4] 5 100)
     (k/arr-pushl [1 2 3 4] 5 4)])])

;; ### arr-pushr

(display-output-format
 ['[(k/arr-pushr [1 2 3 4] 5 100)
    (k/arr-pushr [1 2 3 4] 5 4)]
  (l/! [:code]
    [(k/arr-pushr [1 2 3 4] 5 100)
     (k/arr-pushr [1 2 3 4] 5 4)])
  (l/! [:live]
    [(k/arr-pushr [1 2 3 4] 5 100)
     (k/arr-pushr [1 2 3 4] 5 4)])])

;; ### arr-join

(display-output-format
 ['(k/arr-join ["1" "2" "3" "4"] " ")
  (l/! [:code]
    (k/arr-join ["1" "2" "3" "4"] " "))
  (l/! [:live]
    (k/arr-join ["1" "2" "3" "4"] " "))])

;; ### arr-interpose

(display-output-format
 ['(k/arr-interpose ["1" "2" "3" "4"] "XX")
  (l/! [:code]
    (k/arr-interpose ["1" "2" "3" "4"] "XX"))
  (l/! [:live]
    (k/arr-interpose ["1" "2" "3" "4"] "XX"))])

;; ### arr-repeat

(display-output-format
 ['[(k/arr-repeat "1" 4)]
  (l/! [:code]
    [(k/arr-repeat "1" 4)])
  (l/! [:live]
    [(k/arr-repeat "1" 4)])])

;; ### arr-random

(display-output-format
 ['(k/arr-random [1 2 3 4])
  (l/! [:code]
    (k/arr-random [1 2 3 4]))
  (l/! [:live]
    (k/arr-random [1 2 3 4]))])

;; ### arr-normalise

(display-output-format
 ['(k/arr-normalise [1 2 3 4])
  (l/! [:code]
    (k/arr-normalise [1 2 3 4]))
  (l/! [:live]
    (k/arr-normalise [1 2 3 4]))])

;; ### arr-sample

(display-output-format
 ['(k/arr-sample ["left" "right" "up" "down"]
                 [0.1 0.2 0.3 0.4])
  (l/! [:code]
    (k/arr-sample ["left" "right" "up" "down"]
                  [0.1 0.2 0.3 0.4]))
  (l/! [:live]
    (k/arr-sample ["left" "right" "up" "down"]
                  [0.1 0.2 0.3 0.4]))])

;; ### arrayify

(display-output-format
 ['[(k/arrayify 1)
    (k/arrayify [1])]
  (l/! [:code]
    [(k/arrayify 1)
     (k/arrayify [1])])
  (l/! [:live]
    [(k/arrayify 1)
     (k/arrayify [1])])])


;; ## Base Lib - Object

;; ### obj-empty?

(display-output-format
 ['[(k/obj-empty? {})
    (k/obj-empty? {:a 1})]
  (l/! [:code]
    [(k/obj-empty? {})
     (k/obj-empty? {:a 1})])
  (l/! [:live]
    [(k/obj-empty? {})
     (k/obj-empty? {:a 1})])])

;; ### obj-not-empty?

(display-output-format
 ['[(k/obj-not-empty? {})
    (k/obj-not-empty? {:a 1})]
  (l/! [:code]
    [(k/obj-not-empty? {})
     (k/obj-not-empty? {:a 1})])
  (l/! [:live]
    [(k/obj-not-empty? {})
     (k/obj-not-empty? {:a 1})])])

;; ### obj-first-key

(display-output-format
 ['(k/obj-first-key {:a 1})
  (l/! [:code]
    (k/obj-first-key {:a 1}))
  (l/! [:live]
    (k/obj-first-key {:a 1}))])

;; ### obj-first-val

(display-output-format
 ['(k/obj-first-val {:a 1})
  (l/! [:code]
    (k/obj-first-val {:a 1}))
  (l/! [:live]
    (k/obj-first-val {:a 1}))])

;; ### obj-keys

(display-output-format
 ['(k/obj-keys {:a 1 :b 2})
  (l/! [:code]
    (k/obj-keys {:a 1 :b 2}))
  (l/! [:live]
    (k/obj-keys {:a 1 :b 2}))])

;; ### obj-vals

(display-output-format
 ['(k/obj-vals {:a 1 :b 2})
  (l/! [:code]
    (k/obj-vals {:a 1 :b 2}))
  (l/! [:live]
    (k/obj-vals {:a 1 :b 2}))])

;; ### obj-pairs

(display-output-format
 ['(k/obj-pairs {:a 1 :b 2 :c 2})
  (l/! [:code]
    (k/obj-pairs {:a 1 :b 2 :c 2}))
  (l/! [:live]
    (k/obj-pairs {:a 1 :b 2 :c 2}))])

;; ### obj-clone

(display-output-format
 ['(k/obj-clone {:a 1 :b 2 :c 3})
  (l/! [:code]
    (k/obj-clone {:a 1 :b 2 :c 3}))
  (l/! [:live]
    (k/obj-clone {:a 1 :b 2 :c 3}))])

;; ### obj-assign

(display-output-format
 ['(do
     (var out := {:a 1})
     (k/obj-assign out {:b 2 :c 3})
     out)
  (l/! [:code]
    (do
      (var out := {:a 1})
      (k/obj-assign out {:b 2 :c 3})
      out))
  (l/! [:live]
    (do
      (var out := {:a 1})
      (k/obj-assign out {:b 2 :c 3})
      out))])

;; ### obj-assign-nested

(display-output-format
 ['[(k/obj-assign-nested {:a 1}
                         {:b 2})
    (k/obj-assign-nested {:a {:b {:c 1}}}
                         {:a {:b {:d 1}}})]
  (l/! [:code]
    [(k/obj-assign-nested {:a 1}
                          {:b 2})
     (k/obj-assign-nested {:a {:b {:c 1}}}
                          {:a {:b {:d 1}}})])
  (l/! [:live]
    [(k/obj-assign-nested {:a 1}
                          {:b 2})
     (k/obj-assign-nested {:a {:b {:c 1}}}
                          {:a {:b {:d 1}}})])])

;; ### obj-assign-with

(display-output-format
 ['(k/obj-assign-with {:a {:b true}}
                      {:a {:c true}}
                      k/obj-assign)
  (l/! [:code]
    (k/obj-assign-with {:a {:b true}}
                       {:a {:c true}}
                       k/obj-assign))
  (l/! [:live]
    (k/obj-assign-with {:a {:b true}}
                       {:a {:c true}}
                       k/obj-assign))])

;; ### obj-from-pairs

(display-output-format
 ['(k/obj-from-pairs [["a" 1] ["b" 2] ["c" 3]])
  (l/! [:code]
    (k/obj-from-pairs [["a" 1] ["b" 2] ["c" 3]]))
  (l/! [:live]
    (k/obj-from-pairs [["a" 1] ["b" 2] ["c" 3]]))])

;; ### obj-del

(display-output-format
 ['(k/obj-del {:a 1 :b 2 :c 3}
              ["a" "b"])
  (l/! [:code]
    (k/obj-del {:a 1 :b 2 :c 3}
               ["a" "b"]))
  (l/! [:live]
    (k/obj-del {:a 1 :b 2 :c 3}
               ["a" "b"]))])

;; ### obj-del-all

(display-output-format
 ['(k/obj-del-all {:a 1 :b 2 :c 3})
  (l/! [:code]
    (k/obj-del-all {:a 1 :b 2 :c 3}))
  (l/! [:live]
    (k/obj-del-all {:a 1 :b 2 :c 3}))])

;; ### obj-pick

(display-output-format
 ['(k/obj-pick {:a 1 :b 2 :c 3}
               ["a" "b"])
  (l/! [:code]
    (k/obj-pick {:a 1 :b 2 :c 3}
                ["a" "b"]))
  (l/! [:live]
    (k/obj-pick {:a 1 :b 2 :c 3}
                ["a" "b"]))])

;; ### obj-omit

(display-output-format
 ['(k/obj-omit {:a 1 :b 2 :c 3}
               ["a" "b"])
  (l/! [:code]
    (k/obj-omit {:a 1 :b 2 :c 3}
                ["a" "b"]))
  (l/! [:live]
    (k/obj-omit {:a 1 :b 2 :c 3}
                ["a" "b"]))])

;; ### obj-transpose

(display-output-format
 ['(k/obj-transpose {:a "x" :b "y" :c "z"})
  (l/! [:code]
    (k/obj-transpose {:a "x" :b "y" :c "z"}))
  (l/! [:live]
    (k/obj-transpose {:a "x" :b "y" :c "z"}))])

;; ### obj-nest

(display-output-format
 ['(k/obj-nest ["a" "b"] 1)
  (l/! [:code]
    (k/obj-nest ["a" "b"] 1))
  (l/! [:live]
    (k/obj-nest ["a" "b"] 1))])


;; ### obj-map

(display-output-format
 ['(k/obj-map {:a 1 :b 2 :c 3}
              k/inc)
  (l/! [:code]
    (k/obj-map {:a 1 :b 2 :c 3}
               k/inc))
  (l/! [:live]
    (k/obj-map {:a 1 :b 2 :c 3}
               k/inc))])

;; ### obj-filter

(display-output-format
 ['(k/obj-filter {:a 1 :b 2 :c 3}
                  k/odd?)
  (l/! [:code]
    (k/obj-filter {:a 1 :b 2 :c 3}
                  k/odd?))
  (l/! [:live]
    (k/obj-filter {:a 1 :b 2 :c 3}
                  k/odd?))])

;; ### obj-keep

(display-output-format
 ['(k/obj-keep {:a 1 :b 2 :c 3}
               (fn:> [x] (:? (k/odd? x) x))
               )
  (l/! [:code]
    (k/obj-keep {:a 1 :b 2 :c 3}
               (fn:> [x] (:? (k/odd? x) x))
               ))
  (l/! [:live]
    (k/obj-keep {:a 1 :b 2 :c 3}
               (fn:> [x] (:? (k/odd? x) x))
               ))])

;; ### obj-keepf

(display-output-format
 ['(k/obj-keepf {:a 1 :b 2 :c 3}
               k/odd? k/identity)
  (l/! [:code]
    (k/obj-keepf {:a 1 :b 2 :c 3}
                 k/odd? k/identity))
  (l/! [:live]
    (k/obj-keepf {:a 1 :b 2 :c 3}
               k/odd? k/identity))])

;; ### obj-intersection

(display-output-format
 ['(k/obj-intersection {:a true :b true}
                       {:c true :b true})
  (l/! [:code]
    (k/obj-intersection {:a true :b true}
                        {:c true :b true}))
  (l/! [:live]
    (k/obj-intersection {:a true :b true}
                       {:c true :b true}))])

;; ### obj-difference

(display-output-format
 ['[(k/obj-difference {:a true :b true}
                      {:c true :b true})
    (k/obj-difference {:c true :b true}
                      {:a true :b true})]
  (l/! [:code]
    [(k/obj-difference {:a true :b true}
                      {:c true :b true})
    (k/obj-difference {:c true :b true}
                      {:a true :b true})])
  (l/! [:live]
    [(k/obj-difference {:a true :b true}
                      {:c true :b true})
    (k/obj-difference {:c true :b true}
                      {:a true :b true})])])

;; ### obj-keys-nested

(display-output-format
 ['(k/obj-keys-nested {:a {:b {:c 1
                               :d 2}
                           :e {:f 4
                               :g 5}}}
                      [])
  (l/! [:code]
    (k/obj-keys-nested {:a {:b {:c 1
                               :d 2}
                           :e {:f 4
                               :g 5}}}
                       []))
  (l/! [:live]
    (k/obj-keys-nested {:a {:b {:c 1
                               :d 2}
                           :e {:f 4
                               :g 5}}}
                       []))])

;; ### to-flat

(display-output-format
 ['[(k/to-flat {:a 1 :b 2 :c 3})
    (k/to-flat (k/obj-pairs {:a 1 :b 2 :c 3}))]
  (l/! [:code]
    [(k/to-flat {:a 1 :b 2 :c 3})
     (k/to-flat (k/obj-pairs {:a 1 :b 2 :c 3}))])
  (l/! [:live]
    [(k/to-flat {:a 1 :b 2 :c 3})
     (k/to-flat (k/obj-pairs {:a 1 :b 2 :c 3}))])])

;; ### from-flat

(display-output-format
 ['(k/from-flat ["a" 1, "b" 2, "c" 3]
                k/step-set-key
                {})
  (l/! [:code]
    (k/from-flat ["a" 1, "b" 2, "c" 3]
                 k/step-set-key
                 {}))
  (l/! [:live]
    (k/from-flat ["a" 1, "b" 2, "c" 3]
                 k/step-set-key
                 {}))])

;; ### get-in

(display-output-format
 ['(k/get-in {:a {:b {:c 1}}}
             ["a" "b"])
  (l/! [:code]
    (k/get-in {:a {:b {:c 1}}}
              ["a" "b"]))
  (l/! [:live]
    (k/get-in {:a {:b {:c 1}}}
              ["a" "b"]))])

;; ### set-in

(display-output-format
 ['(do
     (var out {:a {:b {:c 1}}})
     (k/set-in out ["a" "b"] 2)
     out)
  (l/! [:code]
    (do
     (var out {:a {:b {:c 1}}})
     (k/set-in out ["a" "b"] 2)
     out))
  (l/! [:live]
    (do
     (var out {:a {:b {:c 1}}})
     (k/set-in out ["a" "b"] 2)
     out))])

;; ### eq-nested

(display-output-format
 ['[(k/eq-nested {:a {:b {:c 1}}}
                  {:a {:b {:c 1}}})
    (k/eq-nested {:a {:b {:c 1}}}
                  {:a {:b {:c 2}}})
    (k/eq-nested 1 1)
    (k/eq-nested 1 2)
    (k/eq-nested [1] [1])
    (k/eq-nested [1] [2])
    (k/eq-nested {:a [{:b {:c 1}}]}
                  {:a [{:b {:c 1}}]})
    (k/eq-nested {:a [{:b {:c 1}}]}
                  {:a [{:b {:c 2}}]})]
  (l/! [:code]
    [(k/eq-nested {:a {:b {:c 1}}}
                  {:a {:b {:c 1}}})
    (k/eq-nested {:a {:b {:c 1}}}
                  {:a {:b {:c 2}}})
    (k/eq-nested 1 1)
    (k/eq-nested 1 2)
    (k/eq-nested [1] [1])
    (k/eq-nested [1] [2])
    (k/eq-nested {:a [{:b {:c 1}}]}
                  {:a [{:b {:c 1}}]})
    (k/eq-nested {:a [{:b {:c 1}}]}
                 {:a [{:b {:c 2}}]})])
  (l/! [:live]
    [(k/eq-nested {:a {:b {:c 1}}}
                  {:a {:b {:c 1}}})
    (k/eq-nested {:a {:b {:c 1}}}
                  {:a {:b {:c 2}}})
    (k/eq-nested 1 1)
    (k/eq-nested 1 2)
    (k/eq-nested [1] [1])
    (k/eq-nested [1] [2])
    (k/eq-nested {:a [{:b {:c 1}}]}
                  {:a [{:b {:c 1}}]})
    (k/eq-nested {:a [{:b {:c 1}}]}
                 {:a [{:b {:c 2}}]})])])

;; ### obj-diff

(display-output-format
 ['(k/obj-diff {:a 1 :b 2}
               {:a 1 :c 2})
  (l/! [:code]
    (k/obj-diff {:a 1 :b 2}
                {:a 1 :c 2}))
  (l/! [:live]
    (k/obj-diff {:a 1 :b 2}
               {:a 1 :c 2}))])

;; ### obj-diff-nested

(display-output-format
 ['[(k/obj-diff-nested {:a 1 :b 2}
                       {:a 1 :c 2})
    (k/obj-diff-nested {:a 1 :b {:c 3}}
                       {:a 1 :b {:d 3}})
    (k/obj-diff-nested {:a 1 :b {:c {:d 3}}}
                       {:a 1 :b {:c {:e 3}}})]
  (l/! [:code]
    [(k/obj-diff-nested {:a 1 :b 2}
                       {:a 1 :c 2})
    (k/obj-diff-nested {:a 1 :b {:c 3}}
                       {:a 1 :b {:d 3}})
    (k/obj-diff-nested {:a 1 :b {:c {:d 3}}}
                       {:a 1 :b {:c {:e 3}}})])
  (l/! [:live]
    [(k/obj-diff-nested {:a 1 :b 2}
                       {:a 1 :c 2})
    (k/obj-diff-nested {:a 1 :b {:c 3}}
                       {:a 1 :b {:d 3}})
    (k/obj-diff-nested {:a 1 :b {:c {:d 3}}}
                       {:a 1 :b {:c {:e 3}}})])])

;; ### objify

;; decodes object if string

(display-output-format
 ['(k/objify "{}")
  (l/! [:code]
    (k/objify "{}"))
  (l/! [:live]
    (k/objify "{}"))])

;; ### clone-nested

(display-output-format
 ['(k/clone-nested {:a [1 2 3 {:b [4 5 6]}]})
  (l/! [:code]
    (k/clone-nested {:a [1 2 3 {:b [4 5 6]}]}))
  (l/! [:live]
    (k/clone-nested {:a [1 2 3 {:b [4 5 6]}]}))])

;; ### walk

(display-output-format
 ['(k/walk [1 {:a {:b 3}}]
        (fn [x]
          (return (:? (k/is-number? x)
                      (+ x 1)
                      x)))
        k/identity)
  (l/! [:code]
    (k/walk [1 {:a {:b 3}}]
        (fn [x]
          (return (:? (k/is-number? x)
                      (+ x 1)
                      x)))
        k/identity))
  (l/! [:live]
    (k/walk [1 {:a {:b 3}}]
        (fn [x]
          (return (:? (k/is-number? x)
                      (+ x 1)
                      x)))
        k/identity))])

;; ### get-data

(display-output-format
 ['(k/get-data
    {:a 1
     :b "hello"
     :c {:d [1 2 (fn:>)]
         :e "hello"
         :f {:g (fn:>)
             :h 2}}})
  (l/! [:code]
    (k/get-data
    {:a 1
     :b "hello"
     :c {:d [1 2 (fn:>)]
         :e "hello"
         :f {:g (fn:>)
             :h 2}}}))
  (l/! [:live]
    (k/get-data
    {:a 1
     :b "hello"
     :c {:d [1 2 (fn:>)]
         :e "hello"
         :f {:g (fn:>)
             :h 2}}}))])


;; ### get-spec

(display-output-format
 ['(k/get-spec
    {:a 1
     :b "hello"
     :c {:d [1 2 (fn:>)]
         :e "hello"
         :f {:g (fn:>)
             :h 2}}})
  (l/! [:code]
    (k/get-spec
    {:a 1
     :b "hello"
     :c {:d [1 2 (fn:>)]
         :e "hello"
         :f {:g (fn:>)
             :h 2}}}))
  (l/! [:live]
    (k/get-spec
    {:a 1
     :b "hello"
     :c {:d [1 2 (fn:>)]
         :e "hello"
         :f {:g (fn:>)
             :h 2}}}))])



;; # Iter Lib

;; ### for:iter

(display-output-format
 ['(do
     (var out  [])
     (for:iter [e [1 2 3 4]] (x:arr-push out (* 2 e)))
     out)
  (l/! [:code]
    (do
     (var out  [])
     (for:iter [e [1 2 3 4]] (x:arr-push out (* 2 e)))
     out))
  (l/! [:live]
    (do
      (var out  [])
      (for:iter [e [1 2 3 4]] (x:arr-push out (* 2 e)))
      out))])

;; ### iter-from-obj

(display-output-format
 ['(it/arr< (it/iter-from-obj {:a 1 :b 2}))
  (l/! [:code]
    (it/arr< (it/iter-from-obj {:a 1 :b 2})))
  (l/! [:live]
    (it/arr< (it/iter-from-obj {:a 1 :b 2})))])

;; ### iter-from-arr

(display-output-format
 ['(it/arr< (it/iter-from-arr [1 2 3 4 5]))
  (l/! [:code]
    (it/arr< (it/iter-from-arr [1 2 3 4 5])))
  (l/! [:live]
    (it/arr< (it/iter-from-arr [1 2 3 4 5])))])

;; ### iter-from

(display-output-format
 ['(it/arr< (it/iter-from [1 2 3 4 5]))
  (l/! [:code]
    (it/arr< (it/iter-from [1 2 3 4 5])))
  (l/! [:live]
    (it/arr< (it/iter-from [1 2 3 4 5])))])

;; ### iter

(display-output-format
 ['(it/iter [1 2 3 4 5])
  (l/! [:code]
    (it/iter [1 2 3 4 5]))
  (l/! [:live]
    (it/iter [1 2 3 4 5]))])


;; ### iter?

(display-output-format
 ['(it/iter? (it/iter []))
  (l/! [:code]
    (it/iter? (it/iter [])))
  (l/! [:live]
    (it/iter? (it/iter [])))])


;; ### iter-next

(display-output-format
 ['(it/iter-next (it/iter [1 2 3]))
  (l/! [:code]
    (it/iter-next (it/iter [1 2 3])))
  (l/! [:live]
    (it/iter-next (it/iter [1 2 3])))])

;; ### iter-has?

(display-output-format
 ['[(it/iter-has? 123)
    (it/iter-has? [1 2 3])]
  (l/! [:code]
    [(it/iter-has? 123)
     (it/iter-has? [1 2 3])])
  (l/! [:live]
    [(it/iter-has? 123)
     (it/iter-has? [1 2 3])])])

;; ### iter-native?

(display-output-format
 ['[(it/iter-native? (it/iter [1 2 3]))
    (it/iter-native? 1)]
  (l/! [:code]
    [(it/iter-native? (it/iter [1 2 3]))
     (it/iter-native? 1)])
  (l/! [:live]
    [(it/iter-native? (it/iter [1 2 3]))
    (it/iter-native? 1)])])

;; ### iter-eq

(display-output-format
 ['(do 
     (var eq-fn (fn:> [a b] (== a b)))
     [(it/iter-eq (it/iter [1 2 4 4])
                  (it/iter [1 2 4 4])
                  eq-fn)
      (it/iter-eq (it/iter [1 2 4 4])
                  (it/iter [1 2 3 4])
                  eq-fn)
      (it/iter-eq (it/iter [1 2 4])
                  (it/iter [1 2 4 4])
                  eq-fn)
      (it/iter-eq (it/iter [1 2 4 4])
                  (it/iter [1 2 4])
                  eq-fn)])
  (l/! [:code]
    (do 
     (var eq-fn (fn:> [a b] (== a b)))
     [(it/iter-eq (it/iter [1 2 4 4])
                  (it/iter [1 2 4 4])
                  eq-fn)
      (it/iter-eq (it/iter [1 2 4 4])
                  (it/iter [1 2 3 4])
                  eq-fn)
      (it/iter-eq (it/iter [1 2 4])
                  (it/iter [1 2 4 4])
                  eq-fn)
      (it/iter-eq (it/iter [1 2 4 4])
                  (it/iter [1 2 4])
                  eq-fn)]))
  (l/! [:live]
    (do 
     (var eq-fn (fn:> [a b] (== a b)))
     [(it/iter-eq (it/iter [1 2 4 4])
                  (it/iter [1 2 4 4])
                  eq-fn)
      (it/iter-eq (it/iter [1 2 4 4])
                  (it/iter [1 2 3 4])
                  eq-fn)
      (it/iter-eq (it/iter [1 2 4])
                  (it/iter [1 2 4 4])
                  eq-fn)
      (it/iter-eq (it/iter [1 2 4 4])
                  (it/iter [1 2 4])
                  eq-fn)]))])

;; ### iter-null

(display-output-format
 ['(it/arr< (it/iter-null))
  (l/! [:code]
    (it/arr< (it/iter-null)))
  (l/! [:live]
    (it/arr< (it/iter-null)))])

;; ### collect

(display-output-format
 ['(it/collect [1 2 3 4]
               k/step-push
               [])
  (l/! [:code]
    (it/collect [1 2 3 4]
                k/step-push
               []))
  (l/! [:live]
    (it/collect [1 2 3 4]
                k/step-push
                []))])

;; ### nil<

(display-output-format
 ['(it/nil< (it/iter [1 2 3 4]))
  (l/! [:code]
    (it/nil< (it/iter [1 2 3 4])))
  (l/! [:live]
    (it/nil< (it/iter [1 2 3 4])))])

;; ### arr<

(display-output-format
 ['(it/arr< (it/iter [1 2 3 4]))
  (l/! [:code]
    (it/arr< (it/iter [1 2 3 4])))
  (l/! [:live]
    (it/arr< (it/iter [1 2 3 4])))])

;; ### obj<

(display-output-format
 ['(it/obj< (it/iter [["a" 2] ["b" 4]]))
  (l/! [:code]
    (it/obj< (it/iter [["a" 2] ["b" 4]])))
  (l/! [:live]
    (it/obj< (it/iter [["a" 2] ["b" 4]])))])


;; ### take

(display-output-format
 ['(it/arr< (it/take 4 (it/iter [1 2 3 4 5 6 7])))
  (l/! [:code]
    (it/arr< (it/take 4 (it/iter [1 2 3 4 5 6 7]))))
  (l/! [:live]
    (it/arr< (it/take 4 (it/iter [1 2 3 4 5 6 7]))))])

;; ### constantly

(display-output-format
 ['(it/arr< (it/take 4 (it/constantly 1)))
  (l/! [:code]
    (it/arr< (it/take 4 (it/constantly 1))))
  (l/! [:live]
    (it/arr< (it/take 4 (it/constantly 1))))])

;; ### iterate

(display-output-format
 ['(it/arr< (it/take 4 (it/iterate k/inc 11)))
  (l/! [:code]
    (it/arr< (it/take 4 (it/iterate k/inc 11))))
  (l/! [:live]
    (it/arr< (it/take 4 (it/iterate k/inc 11))))])

;; ### repeatedly

(display-output-format
 ['(it/arr< (it/take 5 (it/repeatedly (fn [] (return 5)))))
  (l/! [:code]
    (it/arr< (it/take 5 (it/repeatedly (fn [] (return 5))))))
  (l/! [:live]
    (it/arr< (it/take 5 (it/repeatedly (fn [] (return 5))))))])

;; ### cycle

(display-output-format
 ['(it/arr< (it/take 5 (it/cycle [1 2 3])))
  (l/! [:code]
    (it/arr< (it/take 5 (it/cycle [1 2 3]))))
  (l/! [:live]
    (it/arr< (it/take 5 (it/cycle [1 2 3]))))])

;; ### range

(display-output-format
 ['(it/arr< (it/range [-10 -3]))
  (l/! [:code]
    (it/arr< (it/range [-10 -3])))
  (l/! [:live]
    (it/arr< (it/range [-10 -3])))])

;; ### drop

(display-output-format
 ['(it/arr< (it/drop 3 (it/range 10)))
  (l/! [:code]
    (it/arr< (it/drop 3 (it/range 10))))
  (l/! [:live]
    (it/arr< (it/drop 3 (it/range 10))))])

;; ### peek

(display-output-format
 ['(do
     (var out := [])
     (it/nil< (it/peek (fn [e]
                         (k/step-push out e))
                       [1 2 3 4 5]))
     out)
  (l/! [:code]
    (do
     (var out := [])
     (it/nil< (it/peek (fn [e]
                         (k/step-push out e))
                       [1 2 3 4 5]))
     out))
  (l/! [:live]
    (do
     (var out := [])
     (it/nil< (it/peek (fn [e]
                         (k/step-push out e))
                       [1 2 3 4 5]))
     out))])

;; ### map

(display-output-format
 ['(it/arr< (it/map k/inc [1 2 3]))
  (l/! [:code]
    (it/arr< (it/map k/inc [1 2 3])))
  (l/! [:live]
    (it/arr< (it/map k/inc [1 2 3])))])

;; ### mapcat

(display-output-format
 ['[(it/arr< (it/mapcat (fn:> [x] [x x]) [1 2 3]))
    (it/arr< (it/mapcat (fn:> [x] x)
                       [[1 2 3] [4 5 6]]))
    (it/arr< (it/mapcat (fn:> [x] (it/range x))
                       (it/range 4)))
    (it/arr< (it/mapcat (fn:> [x] x)
                       [(it/range 3) (it/range 3)]))]
  (l/! [:code]
    [(it/arr< (it/mapcat (fn:> [x] [x x]) [1 2 3]))
    (it/arr< (it/mapcat (fn:> [x] x)
                       [[1 2 3] [4 5 6]]))
    (it/arr< (it/mapcat (fn:> [x] (it/range x))
                       (it/range 4)))
    (it/arr< (it/mapcat (fn:> [x] x)
                       [(it/range 3) (it/range 3)]))])
  (l/! [:live]
    [(it/arr< (it/mapcat (fn:> [x] [x x]) [1 2 3]))
    (it/arr< (it/mapcat (fn:> [x] x)
                       [[1 2 3] [4 5 6]]))
    (it/arr< (it/mapcat (fn:> [x] (it/range x))
                       (it/range 4)))
    (it/arr< (it/mapcat (fn:> [x] x)
                       [(it/range 3) (it/range 3)]))])])

;; ### concat

(display-output-format
 ['(it/arr< (it/concat [(it/range 3)
                        (it/range [4 6])]))
  (l/! [:code]
    (it/arr< (it/concat [(it/range 3)
                         (it/range [4 6])])))
  (l/! [:live]
    (it/arr< (it/concat [(it/range 3)
                       (it/range [4 6])])))])

;; ### filter

(display-output-format
 ['(it/arr< (it/filter k/odd? [1 2 3 4]))
  (l/! [:code]
    (it/arr< (it/filter k/odd? [1 2 3 4])))
  (l/! [:live]
    (it/arr< (it/filter k/odd? [1 2 3 4])))])

;; ### keep

(display-output-format
 ['(it/arr< (it/keep (fn:> [x] (:? (k/odd? x) {:a x}))
                     [1 2 3 4]))
  (l/! [:code]
    (it/arr< (it/keep (fn:> [x] (:? (k/odd? x) {:a x}))
                      [1 2 3 4])))
  (l/! [:live]
    (it/arr< (it/keep (fn:> [x] (:? (k/odd? x) {:a x}))
                    [1 2 3 4])))])

;; ### partition

(display-output-format
 ['(it/arr< (it/partition 3 (it/range 10)))
  (l/! [:code]
    (it/arr< (it/partition 3 (it/range 10))))
  (l/! [:live]
    (it/arr< (it/partition 3 (it/range 10))))])

;; ### take-nth 

(display-output-format
 ['[(it/arr< (it/take-nth 2 (it/range 10)))
    (it/arr< (it/take-nth 3 (it/range 10)))
    (it/arr< (it/take-nth 4 (it/drop 1 (it/range 10))))]
  (l/! [:code]
    [(it/arr< (it/take-nth 2 (it/range 10)))
     (it/arr< (it/take-nth 3 (it/range 10)))
     (it/arr< (it/take-nth 4 (it/drop 1 (it/range 10))))])
  (l/! [:live]
    [(it/arr< (it/take-nth 2 (it/range 10)))
     (it/arr< (it/take-nth 3 (it/range 10)))
     (it/arr< (it/take-nth 4 (it/drop 1 (it/range 10))))])])
