(ns clojure.plus.print.objects.objects-and-protocols
  (:require [clojure.core.async :as async]
            [clojure.datafy :as datafy]
            [clojure.string :as str]
            [core.async.flow.example.stats :as stats]))

;; Clojure plus includes print-methods to improve printing objects

(require 'clojure+.print)
^:kind/hidden
(clojure+.print/install!)

;; Once activated, we can print functions, atoms, namespaces, and more sensibly

inc
(atom {})
*ns*

;; Clojure Plus only adds printers for pre-defined types.
;; No printer is provided for Object,
;; so it falls back to Clojure's default printing method:

(defmethod print-method Object [x ^java.io.Writer w]
  (#'clojure.core/print-object x w))

;; There are plenty of objects left over that print messily

(def my-chan
  (async/chan))

my-chan

;; Clojure's Object print-method calls print-object,
;; which prints the ugly [type-str hash-str type-str].

;; Clojure Plus could clean that up like this:

(defmethod print-method Object [x ^java.io.Writer w]
  (.write w "#object ")
  (.write w (.getName (class x))))

my-chan

;; Much nicer!
;; This solves the majority of "left over" situations.
;; However, there are still some ugly ones left...

(def stats-flow
  (stats/create-flow))

stats-flow

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

;; But there is a big problem... everything is Datafiable...

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

my-chan
stats-flow

;; Even if we don't care to improve reify (due to performance),
;; I think the Object printer should still be improved to align with the other printers.
