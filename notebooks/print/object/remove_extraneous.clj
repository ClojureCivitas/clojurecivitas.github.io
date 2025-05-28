^{:clay {:title  "Clean object printing by removing extraneous"
         :quarto {:author   :timothypratley
                  :type     :post
                  :date     "2025-06-05"
                  :category :clojure
                  :tags     [:print-method :objects]}}}
(ns print.object.remove-extraneous
  (:require [clojure.string :as str])
  (:import (clojure.lang MultiFn)
           (java.io Writer)))

(set! *warn-on-reflection* true)

(defn remove-extraneous
  "Clojure compiles with unique names that include things like `/eval32352/` and `--4321`.
  These are rarely useful when printing a function.
  They can still be accessed via (class x) or similar."
  [s]
  (-> s
      (str/replace #"/eval\d+/" "/")
      (str/replace #"--\d+(/|$)" "$1")))

(defn format-class-name ^String [s]
  (let [[ns-str & names] (-> (remove-extraneous s)
                             (str/split #"/"))]
    (if (and ns-str names)
      (str (str/join "$" names))
      (-> s (str/split #"\.") (last)))))

(defn class-name
  [x]
  (-> x class .getName Compiler/demunge))

(defn object-str ^String [x]
  (str "#object [" (format-class-name (class-name x)) "]"))

(defn object-writer [x ^Writer w]
  (.write w (object-str x)))

(defn pr-str* [x]
  (let [original-method (get-method print-method Object)]
    (try
      (.addMethod ^MultiFn print-method Object object-writer)
      (pr-str x)
      (finally
        (.addMethod ^MultiFn print-method Object original-method)))))

(comment
  (pr-str* pr-str*)
  :-)
