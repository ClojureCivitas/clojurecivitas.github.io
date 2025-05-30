^{:kindly/hide-code true
  :clay             {:title  "Printing Objects and Protocols in Clojure"
                     :quarto {:author :timothypratley
                              :draft  true}}}
(ns clojure+.print.objects-and-protocols
  (:require [clojure.core.async :as async]
            [clojure.datafy :as datafy]
            [clojure.string :as str]
            [core.async.flow.example.stats :as stats]))

;; The Clojure default for printing objects is noisy.
;; Clojure's `print-method` for `Object` delegates to `clojure.core/print-object`

(defmethod print-method Object [x ^java.io.Writer w]
  (#'clojure.core/print-object x w))

(Object.)

;; The syntax is `#object[CLASS-NAME HASH toString())]`
;; and as you can see, the toString of an Object is `CLASS-NAME@HASH`.
;; This can get pretty ugly:

(async/chan)

;; [clojure-plus](https://github.com/tonsky/clojure-plus) provides print-methods to improve printing many things.

(comment
  (require 'clojure+.print)
  (clojure+.print/install-printers!))

;; Once activated, we can print functions, atoms, namespaces, and more sensibly.
;; Clojure Plus adds printers for many types,
;; but no printer is provided for Object,
;; which remains as Clojure's default printing method.
;; There are plenty of objects left over that print messily.

;; It's not hard to provide an Object print-method:

(defmethod print-method Object [x ^java.io.Writer w]
  (.write w "#object [")
  (.write w (.getName (class x)))
  (.write w "]"))

(async/chan)

;; Much nicer! In my opinion this is a big improvement.
;; Especially in the world of notebooks where we like to show things as we go,
;; but also just keeping a tidy REPL or looking into data that contains objects.

(stats/create-flow)

;; Hmmmm. not so nice. We'll dig into this further below.
;; But we also need to be aware that Clojure munges it's names to make Java valid names.
;; This matters for some things:

(-> ((fn %% [] (fn %%% [])))
    (class)
    (.getName))

;; Whoa, that's pretty gross. We'd prefer to demunge the names at least.

(defn class-name
  [x]
  (-> x class .getName Compiler/demunge))

(-> ((fn %% [] (fn %%% [])))
    (class-name))

;; Notice the `/evalNNNNN/` part?
;; To create a function, Clojure creates a new class.
;; The `/evalNNNNN/` counts every time it evaluates.
;; This is useful in the sense that it identifies the class for that evaluation.
;; But we almost never care for that detail (more on that later).
;; For the same reason our strangely named functions have `--NNNNN` appended to them,
;; because they are sub evaluations of the top-level evaluation.

;; Let's do away with that noise for the moment:

(defn remove-extraneous
  "Clojure compiles with unique names that include things like `/eval32352/` and `--4321`.
  These are rarely useful when printing a function.
  They can still be accessed via (class x) or similar."
  [s]
  (-> s
      (str/replace #"/eval\d+/" "/")
      (str/replace #"--\d+(/|$)" "$1")))

(-> ((fn %% [] (fn %%% [])))
    (class-name)
    (remove-extraneous))

;; Looking better, I can actually see the (strange) name of the functions.

(defn format-class-name ^String [s]
  (let [[ns-str & names] (-> (remove-extraneous s)
                             (str/split #"/"))]
    (if (and ns-str names)
      (str (str/join "$" names))
      (-> s (str/split #"\.") (last)))))

(-> (((fn aaa [] (fn bbb [] (fn ccc [])))))
    (class-name)
    (format-class-name))

;; Let's hook this up to the print-method for Object:

(defmethod print-method Object [x ^java.io.Writer w]
  (.write w "#object [")
  (.write w (-> (class-name x) (format-class-name)))
  (.write w "]"))

*ns*
(((fn aaa [] (fn bbb [] (fn ccc [])))))
(stats/create-flow)

;; What is this? It's a reified object that implements protocols.
;; We can see this by the $reify part at the end.
;; The description is not terrible, at least we know where it was made,
;; which hints that it must be a flow.
;; Can we do better?

;; AFAIK the only way to check what protocols an object satisfies
;; is to call `satisfies?` for every possible protocol:

(defn all-protocol-vars [x]
  (->> (all-ns)
       (mapcat ns-publics)
       (vals)
       (keep #(-> % meta :protocol))
       (distinct)
       (filter #(satisfies? @% x))))

;; On the one hand, this is concerning for performance.
;; On the other hand, at my REPL I don't care about that, it's faster than I can notice.
;; Leaving aside those concerns, it returns quite a long list...

(def stats-flow
  (stats/create-flow))

(all-protocol-vars stats-flow)

;; But notice that one of them; `#'clojure.core.async.flow.impl.graph/Graph`
;; just feels like it is the one we care about most.
;; Furthermore, it shares a similar namespace with the classname.
;; Let's try matching by the namespace...

(defn var-ns-name [v]
  (-> (meta v) (:ns) (ns-name)))

(defn ns-match? [p x]
  (-> (var-ns-name p)
      (str/starts-with? (.getPackageName (class x)))))

(defn protocol-ns-matches [x]
  (filter #(ns-match? % x) (all-protocol-vars x)))

(protocol-ns-matches stats-flow)

;; Nice.
;; In my opinion this is more representative of the object.
;; The `#'` out front is unnecessary and can be removed...

(defn var-sym [v]
  (let [m (meta v)]
    (symbol (str (ns-name (:ns m))) (str (:name m)))))

(defn protocol-ns-match-names [x]
  (->> (protocol-ns-matches x)
       (map var-sym)))

(protocol-ns-match-names stats-flow)

;; The other protocol of interest is Datafiable,
;; because it indicates I can get a data representation if I would like to.

(datafy/datafy stats-flow)

;; I think this one is so helpful that it should always be shown on objects,
;; regardless of their type of other protocols,
;; as a hint that it is possible to get more information.
;; I wouldn't want to print them as data by default, because it would be too spammy.
;; And checking Datafiable is much less of a performance concern.

(satisfies? clojure.core.protocols/Datafiable stats-flow)

;; But there is a big problem... **everything** is Datafiable...

(satisfies? clojure.core.protocols/Datafiable (Object.))

;; So there is no way for us to know whether `datafy/datafy` will do anything useful or not.
;; Sad.
;; But we can improve the print-method to show protocols,
;; bearing in mind it is a performance concern.

;; Showing the reified protocol isn't a big improvement, and probably not worth the performance.
;; Probably not worth including in `clojure-plus`.

;; Even if we don't care to improve reify (due to performance),
;; I think the Object printer should still be improved to align with the other printers.

;; Are we giving up anything?
;; Remember we removed the unique identifiers like `/evalNNNNN/`.
;; When would those be useful?
;; Hold onto your hats!
;; We are about to try to find an Object by a class-name:

(defn find-class [class-name]
  (try
    (Class/forName class-name false (clojure.lang.RT/baseLoader))
    (catch ClassNotFoundException _ nil)))

(defn ddd [x] (inc x))

(type (find-class (-> ddd (class) (.getName))))

;; Why would you want to do that?
;; I don't know, but it's pretty cool you have to admit.
;; What's also interesting is that we can get all Clojure classes:
;; https://danielsz.github.io/2021-05-12T13_24.html

(defn class-cache []
  (some-> (.getDeclaredField clojure.lang.DynamicClassLoader "classCache")
          (doto (.setAccessible true))
          (.get nil)))

(key (first (class-cache)))

;; And we can find them in memory a similar way:

(defn find-in-memory-class
  "Finds a class by name in the DynamicClassLoader's memory cache"
  [class-name]
  (let [method (.getDeclaredMethod clojure.lang.DynamicClassLoader
                                   "findInMemoryClass"
                                   (into-array Class [String]))
        _ (.setAccessible method true)]
    (.invoke method nil (into-array Object [class-name]))))

;; Right, but why would you want to do that?
;; Honestly I can't imagine a reason.
;; All of that to say, do we really want those unique identifiers printed out?
;; No! If we need to find them, we can always look them up another way.
;; We don't need them polluting our REPL output.
