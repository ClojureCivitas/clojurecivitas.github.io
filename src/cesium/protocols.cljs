;;this is a JANK AS F way
;;to wrap some protocol heavy code
;;in scittle
(ns clojure.protocols)

(in-ns 'clojure.core)

(def Object js/Object)
(def PersistentVector (type []))
(def PersistentMap (type {}))
(def PersistentSet (type #{}))
(def String js/String)
(def Array js/Array)
(def Symbol (type 'x))

(defprotocol ILookup
  (-lookup [this k] [this k not-found]))

(defprotocol INamed
  (-name [this]))

(defprotocol ISeqable
  (-seq [this]))

(defprotocol IMeta
  (-meta [this]))

;;this probably won't work unless we can find a way to
;;patch into eval...
#_
(defprotocol IFn
  "Protocol for adding the ability to invoke an object as a function.
  For example, a vector can also be used to look up a value:
  ([1 2 3 4] 1) => 2"
  (-invoke
    [this]
    [this a]
    [this a b]
    [this a b c]
    [this a b c d]
    [this a b c d e]
    [this a b c d e f]
    [this a b c d e f g]
    [this a b c d e f g h]
    [this a b c d e f g h i]
    [this a b c d e f g h i j]
    [this a b c d e f g h i j k]
    [this a b c d e f g h i j k l]
    [this a b c d e f g h i j k l m]
    [this a b c d e f g h i j k l m n]
    [this a b c d e f g h i j k l m n o]
    [this a b c d e f g h i j k l m n o p]
    [this a b c d e f g h i j k l m n o p q]
    [this a b c d e f g h i j k l m n o p q r]
    [this a b c d e f g h i j k l m n o p q r s]
    [this a b c d e f g h i j k l m n o p q r s t]
    [this a b c d e f g h i j k l m n o p q r s t rest]))

(extend-protocol ILookup
  Object
  (-lookup [this k] (get this k))
  (-lookup [this k not-found] (get this k not-found)))

(extend-protocol INamed
  Object
  (-name [this] (name this)))

(extend-protocol ISeqable
  Object
  (-seq [this] (seq this)))

(extend-protocol IMeta
   Object
  (-meta [this] (meta this)))
