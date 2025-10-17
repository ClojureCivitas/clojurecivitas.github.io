^:kindly/hide-code
^{:clay {:title  "Eelements of Malli"
         :quarto {:type        :post
                  :author      [:bsless]
                  :date        "2025-10-18"
                  :description "Elements of a high performance schema validation library"
                  :category    :libs
                  :tags        [:schema :spec]
                  :keywords    [:malli]}}}
(ns malli.elements-of-malli
  (:require
   [clojure.spec.alpha :as s]
   [babashka.http-client.websocket :as ws]
   [malli.core :as m]
   [clojure.edn :as edn]
   [jsonista.core :as json]
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind])
  (:import
   (java.util.concurrent ScheduledExecutorService Executors TimeUnit)))

^:kindly/hide-code
(def ws-uri "wss://stream.bybit.com/v5/public/linear")

^:kindly/hide-code
(defonce scheduled-executor
  (Executors/newScheduledThreadPool 1))

^:kindly/hide-code
(defn schedule!
  ([f period tu]
   (schedule! scheduled-executor f period tu))
  ([ex f period tu]
   (schedule! ex f 0 period tu))
  ([^ScheduledExecutorService scheduled-executor ^Runnable f delay period ^TimeUnit tu]
   (.scheduleAtFixedRate scheduled-executor f ^long delay ^long period tu)))

^:kindly/hide-code
(def buff (atom []))
^:kindly/hide-code
(comment
  (spit "out.edn" @buff)
  (reset! buff (clojure.edn/read-string (slurp "out.edn"))))

^:kindly/hide-code
(defn msg-handler
  [_ws ^java.nio.HeapCharBuffer data _last?]
  (let [msg (json/read-value (.toString data) json/keyword-keys-object-mapper)]
    (swap! buff conj msg)
    (println msg)))

^:kindly/hide-code
(comment
  (def ws (ws/websocket
           {:uri ws-uri
            :on-open (fn [_ws] (println 'open))
            :on-close (fn [_ws status reason] (println 'close status reason))
            :on-ping (fn [_ws data] (println 'ping data))
            :on-pong (fn [_ws data] (println 'pong data))
            :on-error (fn [_ws er] (println 'error er))
            :on-message #'msg-handler
            }))

  (def ping-task (schedule! (fn [] (ws/ping! ws (.getBytes ""))) 20 TimeUnit/SECONDS))
  (comment
    (.cancel ping-task true))

  (ws/send! ws (json/write-value-as-bytes {:op :subscribe :args ["publicTrade.BTCUSDT"]}))
  (ws/close! ws))

^:kindly/hide-code
(defn demo
  [xs schema]
  (let [validator (m/validator schema)]
    {:value xs
     :valid? (map validator xs)}))

;; ---

;; ## Abstract

;; The post goes over the elements of `metosin/malli`, a high performance, data driven, schema library for Clojure(Script)
;; Unlike plumatic/schema and clojure.spec, it contains additional
;; features such as coercion, explanation, generation, extension
;; mechanisms and more
;; It break down the elements of Malli, goes over its main features,
;; demonstrate how to use it effectively and touch on its potential
;; applications for data exploration

;; ## Introduction

;; Malli is a high performance library for data driven schemas in Clojure(Script).


;; ### Schemas

;; Schemas are a way of specifying facts about data at a certain point.
;; In Clojure, we usually enforce them at system boundaries.
;; Additionally, they can be enforced at test time more pervasively
;; across the code base, and to render metadata that other tools like
;; clj-kondo can consume.
;; Malli is an alternative to clojure.spec and plumatic/schema, with
;; different design goals and considerations.

;; ### Data Driven

;; Malli's schema syntax is Just Data.
;; Schemas can be serialized, persisted and round tripped.
;; The main syntax is similar to hiccup

;; For example, validating a value with a schema:

(m/validate
 [:int {:min 1 :max 3}] ; this is the malli schema
 4)

;; ### High performance

;; Even for a very simple use case

;; ```clojure
;; (def v (m/validator [:int {:min 1 :max 3}]))
;; (dotimes [_ 10] (time (dotimes [_ 1e7] (do (v 2) (v 4) (v 5)))))
;; ;; Elapsed time: 83.082345 msecs
;; (s/def ::v (s/and int? #(<= % 3) #(<= 1 %)))
;; (dotimes [_ 10] (time (dotimes [_ 1e7] (do (s/valid? ::v 2) (s/valid? ::v 4) (s/valid? ::v 5)))))
;; ;; Elapsed time: 1775.427095 msecs
;; ```

;; Malli is about 20x faster than clojure.spec

;; ## Mechanics

;; Before we can enjoy High Performance (TM), we need to learn _mechanics_

;; From a bird's eye view, Malli has several "types" of schemas:

;; - Base values - An integer, boolean, string, etc.
;; - Boxes - If you like types, all kinds of Kind1<a>, Kind2<a,b>, etc.
;; Concretely these can be negation (not int) a reference to another schema, repetition (vector of ints).
;; - Comparator schemas - equality, disequality, ordering, etc.
;; - Conjunctions - `and`, map descriptions, concatenation, tuples.
;; - Disjunctions - `or`, multi schemas (like multi methods), sequence alternations. These backtrack.

;; Schemas syntax can be:
;; - keywords: `:int`
;; - functions or vars: `int?`
;; - vector with properties and maybe children: `[:int {:min 1}]`, `[:map {:closed true} [:a :int]]`

;; Schemas themselves are either looked up in a registry or need to implement some protocols.

;; Importantly, malli contains mini "compilers" that derive worker functions from schemas.
;; We've already seen one above.
;; These compilers achieve higher performance than all other libraries and than invoking on the function directly.
;; compare

(m/validate [:int {:min 1 :max 3}] 4)
;; and
(def v (m/validator [:int {:min 1 :max 3}]))
(v 4)

;; The worker functions are:
;; - validator: Any -> Boolean.
;; - explainer: Any -> null | Explanation.
;; - parser/unparser: parser converts disjunctions to tagged tuples.
;; - encoder/decoder: decoder _tries_ to decode a value according to schema and supplied transformer. Encoder goes the other way.
;; - coercer: decodes then validates.

;; ### Base Values

;; #### Predicate schemas

;; This is a long list, you don't have to remember all of them

^:kindly/vector
'[any? some? number? integer? int? pos-int? neg-int? nat-int? pos? neg? float? double?
  boolean? string? ident? simple-ident? qualified-ident? keyword? simple-keyword?
  qualified-keyword? symbol? simple-symbol? qualified-symbol? uuid? uri? inst? seqable?
  indexed? map? vector? list? seq? char? set? nil? false? true?
  zero? coll? associative? sequential? ifn? fn?
  rational? ratio? bytes? decimal?]

;; Predicate schemas don't receive any arguments.

;; #### Type schemas

(keys (m/type-schemas))

;; string, int, float and double receive min/max properties
^:kind/table
(demo
 [(apply str (repeat 3 "a"))
  (apply str (repeat 9 "a"))
  (apply str (repeat 20 "a"))]
 [:string {:min 5 :max 10}])

;; ### Boxes!

;; > what's in the box?!

;; #### Seqable, every, vector, oh my

^:kind/table
(let [schemas [:vector :sequential :seqable :every :set]
      values [[1 2 3] (list 1 2 3) #{1 2 3} (range 1 4) (sorted-set 1 2 3)]]
  (into
   {:value values
    :type (map type values)}
   (map vector
        schemas
        (apply map list
               (for [value values]
                 (for [schema schemas]
                   (m/validate [schema :int] value)))))))

;; Note how schemas can be created programatically.
;; The only difference between a seqable and every is that every is _bounded_
(m/validate [:every :int] (conj (vec (range 1000)) nil))
(m/validate [:every :int] (concat (range 1000) [nil]))

;; We also have map-of, which works like you'd expect:

^:kind/table
(demo
 [{1 2 3 4} {1 2 3 :a} {3 4}]
 [:map-of {:min 2 :max 4} :int :int])

;; #### Maybe Not

^:kind/table
(demo
 [2 nil "nil"]
 [:maybe :int])

(m/validate [:not :int] 'cthulhu)

;; #### References and schemas schemas

;; While this requires getting into registries (LATER), consider this example

(m/validate
  [:schema ;; schema schema
   {:registry ;; registry in properties
    {::cons ;; definition of cons schema
     [:maybe [:tuple pos-int? [:ref ::cons]]]}} ;; self reference
   [:ref ::cons] ;; argument to schema schema is a schema. reference to schema from registry
   ]
  [16 [64 [26 [1 [13 nil]]]]])

;; ### Comparators

[:>
 :>=
 :<
 :<=
 :=
 :not=]

;; #### Egal

;; That's your ground single value. Some(1)

(m/validate [:= 1] 1)
(m/validate [:= 1] "1")

;; It's counterpart, is anything but
(m/validate [:not= 1] 1)
(m/validate [:not= 1] "1")

;; #### Everything else

(m/validate [:> 1] 2)
(m/validate [:> 1] 1)

;; ### Conjunctions

;; #### Tuples

;; We've seen a tuple before, but for completeness

(m/validate [:tuple :int :boolean] [1 true])

;; #### Maps

;; Maps are the bread and butter of information transfer in the Clojure
;; world, and frankly, around the web (what are JSON objects?).

;; A map schema consists of type, optional properties, and children.
;; Each child can be thought of as an entry schema, which is why I think of maps
;; as a conjunction of multiple entry schemas.

(m/validate
 [:map ; type
  {:closed true
   :registry {::c :boolean}
   } ; properties
  [:a :int] ; entry schema
  [:b :double] ; entry schema
  ::c ; reference to a schema (but not a reference schema)
  ]
 {:a 1 :b 2.3 ::c true})

;; It may be obvious, but important, all collections schemas nest

(def Address
  [:map
   [:id :string]
   [:tags [:set :keyword]]
   [:address
    [:map
     [:street :string]
     [:city :string]
     [:zip :int]
     [:lonlat [:tuple :double :double]]]]])

(m/validate
 Address
 {:id "Lillan"
  :tags #{:artesan :coffee :hotel}
  :address {:street "Ahlmanintie 29"
            :city "Tampere"
            :zip 33100
            :lonlat [61.4858322, 23.7854658]}})
