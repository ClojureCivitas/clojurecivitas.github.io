^{:clay {:title "Printing Objects in Clojure"}}
(ns clojure.plus.print.objects.objects-and-protocols
  (:require [clojure.core.async :as async]
            [clojure.datafy :as datafy]
            [clojure.string :as str]
            [core.async.flow.example.stats :as stats]))

;; # Printing Objects in Clojure
;;
;; The Clojure default for printing objects is noisy:

(Object.)

;; The syntax is `#object[CLASS-NAME HASH toString())]`
;; and as you can see, the toString of an Object is `CLASS-NAME@HASH`.
;; This can get pretty ugly:

(async/chan)

;; [clojure-plus](https://github.com/tonsky/clojure-plus) provides print-methods to improve printing many things.

(require 'clojure+.print)
^:kind/hidden
(clojure+.print/install-printers!)

;; Once activated, we can print functions, atoms, namespaces, and more sensibly

inc
(atom {})
*ns*

;; Clojure Plus adds printers for many types,
;; but no printer is provided for Object,
;; which remains as Clojure's default printing method.
;; There are plenty of objects left over that print messily.
;; Going back to our channel example, it still prints the same:

(async/chan)

;; It's not hard to provide an Object print-method:

(defmethod print-method Object [x ^java.io.Writer w]
  (.write w "#object ")
  (.write w (.getName (class x))))

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

;; Notice now that the string is not a valid symbol?
;; That's a problem if we follow the style of `clojure-plus` because it prints as tagged literals.
;;
;; `#object clojure.plus.print.objects.objects-and-protocols/%%/%%%` is not a valid Clojure expression.
;; In a sense it doesn't matter much if we are just printing, but it tends to confuse tools (and people).
;; So I recommend replacing all slashes after the namespace delimiter with `$` instead.
;; While we are at it... the namespace isn't helping us at all here.
;; Let's just show the name without the namespace.

(defn format-class-name [s]
  (let [[ns-str & names] (-> (remove-extraneous s)
                             (str/split #"/"))]
    (if (and ns-str names)
      (str/join "$" names)
      s)))

(-> (((fn aaa [] (fn bbb [] (fn ccc [])))))
    (class-name)
    (format-class-name))

;; That's really all we need.
;; Notice there is no `/` slash if we don't show the namespace, but we still can't use `/` because functions can nest arbitrarily.

;; Side note about including or omitting the namespace.
;; There are many ways to get whatever representation you want,
;; so changing the printing behavior doesn't prevent anything.
;; The question is just would you like to see it or not?
;; I would argue that it's almost never useful, just a distraction.
;; On the other hand, the current behavior for printing vars is fully qualified.
;; Whatever your preference on showing the namespace, it should probably be consistent across printers.

;; I'll assume you prefer including the namespace

(defn format-class-name [s]
  (let [[ns-str & names] (-> (remove-extraneous s)
                             (str/split #"/"))]
    (if (and ns-str names)
      (str ns-str "/" (str/join "$" names))
      s)))

;; Let's hook this up to the print-method for Object:

(defmethod print-method Object [x ^java.io.Writer w]
  (.write w "#object ")
  (.write w (-> (class-name x) (format-class-name))))

;; Notably this doesn't change function etc... because they are still using the `clojure-plus` print-method:

(((fn aaa [] (fn bbb [] (fn ccc [])))))

;; In my opinion, the fn and multi print-methods of `clojure-plus` should be changed to the cleaner output.

;; But it matters for our reify example:

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

(defmethod print-method Object [x ^java.io.Writer w]
  (let [class-name (.getName (class x))
        r (str/includes? class-name "$reify")
        p (when r (first (protocol-ns-match-names x)))]
    (.write w (if p
                "#reify "
                "#object "))
    (.write w (if p
                (str p)
                class-name))))

(stats/create-flow)

;; Showing the reified protocol isn't a big improvement, and probably not worth the performance.
;; Probably not worth including in `clojure-plus`.

;; Even if we don't care to improve reify (due to performance),
;; I think the Object printer should still be improved to align with the other printers.
;; Let's go back to the basic Object print-method without protocol detection.

(defmethod print-method Object [x ^java.io.Writer w]
  (.write w "#object ")
  (.write w (-> (class-name x) (format-class-name))))

;; O.K.
;; Are we giving up anything?
;; Remember we removed the unique identifiers like `/evalNNNNN/`.
;; When would those be useful?
;; Hold onto your hats!
;; We are about to try to find an Object by it's class-name:

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

^:kindly/hide-code
(comment "reset the printer back for testing")
^:kind/hidden ^:kindly/hide-code
(defmethod print-method Object [x ^java.io.Writer w]
  (#'clojure.core/print-object x w))
