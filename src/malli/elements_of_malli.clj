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
   [malli.util :as mu]
   [malli.transform :as mt]
   [malli.provider :as mp]
   [malli.error :as me]
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
  ([xs schema]
   (demo xs schema m/validator))
  ([xs schema worker]
   (let [validator (worker schema)]
     {:value (map pr-str xs)
      :valid? (map validator xs)})))

;; ---

;; # Abstract

;; The post goes over the elements of `metosin/malli`, a high performance, data driven, schema library for Clojure(Script)
;; Unlike plumatic/schema and clojure.spec, it contains additional
;; features such as coercion, explanation, generation, extension
;; mechanisms and more
;; It break down the elements of Malli, goes over its main features,
;; demonstrate how to use it effectively and touch on its potential
;; applications for data exploration

;; # Introduction

;; Malli is a high performance library for data driven schemas in Clojure(Script).


;; ## Schemas

;; Schemas are a way of specifying facts about data at a certain point.
;; In Clojure, we usually enforce them at system boundaries.
;; Additionally, they can be enforced at test time more pervasively
;; across the code base, and to render metadata that other tools like
;; clj-kondo can consume.
;; Malli is an alternative to clojure.spec and plumatic/schema, with
;; different design goals and considerations.

;; ## Data Driven

;; Malli's schema syntax is Just Data.
;; Schemas can be serialized, persisted and round tripped.
;; The main syntax is similar to hiccup

;; For example, validating a value with a schema:

(m/validate
 [:int {:min 1 :max 3}] ; this is the malli schema
 4)

;; ## High performance

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

;; # Mechanics

;; Before we can enjoy High Performance (TM), we need to learn _mechanics_

;; From a bird's eye view, Malli has several "types" of schemas:

;; - Base values - An integer, boolean, string, etc.
;; - Boxes - If you like types, all kinds of Kind1<a>, Kind2<a,b>, etc.
;; Concretely these can be negation (not int) a reference to another schema, repetition (vector of ints).
;; - Comparator schemas - equality, disequality, ordering, etc.
;; - Conjunctions - `and`, map descriptions, concatenation, tuples.
;; - Disjunctions - `or`, multi schemas (like multi methods), sequence alternations. These backtrack.

;; Sequence pattern schemas are orthogonal to this group but deserve a separate discussion.

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

;; ## Base Values

;; ### Predicate schemas

;; This is a long list, you don't have to remember all of them

^:kindly/vector
'[any? some? number? integer? int? pos-int? neg-int? nat-int? pos? neg? float? double?
  boolean? string? ident? simple-ident? qualified-ident? keyword? simple-keyword?
  qualified-keyword? symbol? simple-symbol? qualified-symbol? uuid? uri? inst? seqable?
  indexed? map? vector? list? seq? char? set? nil? false? true?
  zero? coll? associative? sequential? ifn? fn?
  rational? ratio? bytes? decimal?]

;; Predicate schemas don't receive any arguments.

;; ### Type schemas

(keys (m/type-schemas))

;; string, int, float and double receive min/max properties
^:kind/table
(demo
 [(apply str (repeat 3 "a"))
  (apply str (repeat 9 "a"))
  (apply str (repeat 20 "a"))]
 [:string {:min 5 :max 10}])

;; ## Boxes!

;; > what's in the box?!

;; ### Seqable, every, vector, oh my

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

;; ### Maybe Not

^:kind/table
(demo
 [2 nil "nil"]
 [:maybe :int])

(m/validate [:not :int] 'cthulhu)

;; ### References and schemas schemas

;; While this requires getting into registries (LATER), consider this example

(m/validate
  [:schema ;; schema schema
   {:registry ;; registry in properties
    {::cons ;; definition of cons schema
     [:maybe [:tuple pos-int? [:ref ::cons]]]}} ;; self reference
   [:ref ::cons] ;; argument to schema schema is a schema. reference to schema from registry
   ]
  [16 [64 [26 [1 [13 nil]]]]])

;; ## Comparators

[:>
 :>=
 :<
 :<=
 :=
 :not=]

;; ### Egal

;; That's your ground single value. Some(1)

(m/validate [:= 1] 1)
(m/validate [:= 1] "1")

;; It's counterpart, is anything but
(m/validate [:not= 1] 1)
(m/validate [:not= 1] "1")

;; ### Everything else

(m/validate [:> 1] 2)
(m/validate [:> 1] 1)

;; ## Conjunctions

;; ### Tuples

;; We've seen a tuple before, but for completeness

(m/validate [:tuple :int :boolean] [1 true])

;; ### Maps

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

;; ## Disjunctions

;; ### or(n) - Unions

;; when a value can be one of several things

^:kind/table
(demo
 [:foo 'foo "foo"]
 [:or :keyword :symbol])

;; That's neat, but how can we tell them apart?
;; Use an orn (or named) and a parser

^:kind/table
(demo
 [:foo 'foo "foo"]
 [:orn [:a :keyword] [:b :symbol]]
 m/parser)

;; ### Enums - closed sets

^:kind/table
(demo
 [1 2 3 4]
 [:enum 1 2 3])

;; ### multi - discriminating unions

;; multi schemas are very flexible, but their most common use case will be with a map and discriminating field.
;; That field will often be an event type or version.

;; For example, let's look at the Binance user data stream

;;```json
;; {
;;  "e": "GRID_UPDATE", // Event Type
;;  "T": 1669262908216, // Transaction Time
;;  "E": 1669262908218, // Event Time
;;  "gu": {
;; 			  "si": 176057039, // Strategy ID
;; 			  "st": "GRID", // Strategy Type
;; 			  "ss": "WORKING", // Strategy Status
;; 			  "s": "BTCUSDT", // Symbol
;; 			  "r": "-0.00300716", // Realized PNL
;; 			  "up": "16720", // Unmatched Average Price
;; 			  "uq": "-0.001", // Unmatched Qty
;; 			  "uf": "-0.00300716", // Unmatched Fee
;; 			  "mp": "0.0", // Matched PNL
;; 			  "ut": 1669262908197 // Update Time
;; 		    }
;; }
;; {
;;  "e":"CONDITIONAL_ORDER_TRIGGER_REJECT",      // Event Type
;;  "E":1685517224945,      // Event Time
;;  "T":1685517224955,      // me message send Time
;;  "or":{
;;        "s":"ETHUSDT",      // Symbol
;;        "i":155618472834,      // orderId
;;        "r":"Due to the order could not be filled immediately, the FOK order has been rejected. The order will not be recorded in the order history",      // reject reason
;;        }
;;  }
;;```

(def grid-update
  {:e "GRID_UPDATE",
   :T 1669262908216,
   :E 1669262908218,
   :gu {:r "-0.00300716",
        :uq "-0.001",
        :s "BTCUSDT",
        :up "16720",
        :uf "-0.00300716",
        :st "GRID",
        :ss "WORKING",
        :ut 1669262908197,
        :mp "0.0",
        :si 176057039}})

(def conditional-order-trigger-reject
  {:e "CONDITIONAL_ORDER_TRIGGER_REJECT",
   :E 1685517224945,
   :T 1685517224955,
   :or {:s "ETHUSDT",
        :i 155618472834,
        :r "Due to the order could not be filled immediately, the FOK order has been rejected. The order will not be recorded in the order history"}})

;; Notice how we have a different schema for each event type:

(def BinanceUserEvent
  [:multi {:dispatch :e}
   ["GRID_UPDATE"
    [:map
     [:e [:= "GRID_UPDATE"]]
     [:T {:min 0} :int]
     [:E {:min 0} :int]
     [:gu [:map
           [:r :string]
           [:uq :string]
           [:s :string]
           [:up :string]
           [:uf :string]
           [:st :string] ; can and should be enum
           [:ss [:enum "NEW" "WORKING" "CANCELLED" "EXPIRED"]]
           [:ut {:min 0} :int]
           [:mp :string]
           [:si {:min 0} :int]]]]]
   ["CONDITIONAL_ORDER_TRIGGER_REJECT"
    [:map
     [:e [:= "CONDITIONAL_ORDER_TRIGGER_REJECT"]]
     [:T {:min 0} :int]
     [:E {:min 0} :int]
     [:or [:map
           [:s :string]
           [:i {:min 0} :int]
           [:r :string]]]]]])

(m/validate BinanceUserEvent grid-update)
(m/parse BinanceUserEvent grid-update)
(m/parse BinanceUserEvent conditional-order-trigger-reject)

;; ### Schema transformations

;; Notice how the previous schema has common elements? Can we pull them apart?

;; We can bring in merge from malli utils schemas

(def BaseEvent
  [:map
   [:T {:min 0} :int]
   [:E {:min 0} :int]])

(def BinanceUserEvent2
  [:multi {:dispatch :e}
   ["GRID_UPDATE"
    [:merge
     BaseEvent
     [:map
      [:e [:= "GRID_UPDATE"]]
      [:gu [:map
            [:r :string]
            [:uq :string]
            [:s :string]
            [:up :string]
            [:uf :string]
            [:st :string] ; can and should be enum
            [:ss [:enum "NEW" "WORKING" "CANCELLED" "EXPIRED"]]
            [:ut {:min 0} :int]
            [:mp :string]
            [:si {:min 0} :int]]]]]]
   ["CONDITIONAL_ORDER_TRIGGER_REJECT"
    [:merge
     BaseEvent
     [:map
      [:e [:= "CONDITIONAL_ORDER_TRIGGER_REJECT"]]
      [:or [:map
            [:s :string]
            [:i {:min 0} :int]
            [:r :string]]]]]]])

(def registry (merge (mu/schemas) (m/default-schemas)))
(m/validate BinanceUserEvent2 grid-update {:registry registry})
(m/parse BinanceUserEvent2 grid-update {:registry registry})

;; ## Schema Workers

;; Now that we're more familiar with schemas' building blocks, we can go
;; slightly deeper into what we can do with them.

;; ### Validator

;; We've already seen validation, `m/validate` calls `m/validator` to
;; get a validation function and invokes it.
;; A validator is a function which returns true if the data conforms to the schema.

(m/validate BinanceUserEvent2 grid-update {:registry registry})

;; ### Parser

;; we've also seen parsing at work.

;; Schemas supporting alternation also support tagged alternation:
;; - or -> orn
;; - alt -> altn

;; multi, being a discriminating union, tags its cases by default as we've seen.

(m/parse BinanceUserEvent2 conditional-order-trigger-reject {:registry registry})

;; Another case of elements that can be tagged is the concatenation schema.
;; It represents a sequence of elements, and behaves like a regular expression:

(m/parse
 [:catn
  [:nums [:* :int]]
  [:flag :boolean]
  [:opts [:* :string]]]
 [1 2 3 true "a" "b"])


(m/parse
 [:catn
  [:nums [:* :int]]
  [:flag :boolean]
  [:opts [:* :string]]]
 [true "a" "b"])

;; ### Explainer

;; Life is not just about the happy path.
;; In that case, we might need to communicate to our users, or ourselves, what's wrong with the data at hand.
;; For that, we can explain the error

;; No error, no explanation

(m/explain BinanceUserEvent2 grid-update {:registry registry})

;; Yes error, yes explanation

(m/explain BinanceUserEvent2 (dissoc grid-update :E) {:registry registry})

;; That's not very human readable, is it?
;; Let's use the malli.error ns

(-> BinanceUserEvent2
    (m/explain (dissoc grid-update :E) {:registry registry})
    me/humanize)

;; That's more like it

;; You can customize your error messages, including making them dynamic,
;; and localize them according to locale.
;; That's neat but we won't go into it now. It's all in the README.

;; ### Transformers, more than meets the eye

;; Transformers are a deep cup, and once you've reached its bottom, you may never be the same.
;; To avoid any adverse side effects, we'll satisfy ourselves with a few sips.

;; Direct your attention back to the Binance User Event scheme. Some fields in it could clearly be some accurate number types, but since the web and JSON are limited, they are serialized as strings to avoid precision and rounding errors.
;; Our schemas should ideally deal in the world of ought and not IS, meaning we should describe what the data means to the best of our abilities and help it get there

;; A string is not an int

(m/validate :int "1")

;; But it could be with a transformer

(m/decode :int "1" mt/string-transformer)

;; Wouldn't it be nice if we could use big decimals instead of strings?
;; but we could

(def BinanceUserEvent3
  [:multi {:dispatch :e}
   ["GRID_UPDATE"
    [:merge
     BaseEvent
     [:map
      [:e [:= "GRID_UPDATE"]]
      [:gu [:map
            [:r decimal?]
            [:uq decimal?]
            [:s :string]
            [:up decimal?]
            [:uf decimal?]
            [:st :string] ; can and should be enum
            [:ss [:enum "NEW" "WORKING" "CANCELLED" "EXPIRED"]]
            [:ut {:min 0} :int]
            [:mp decimal?]
            [:si {:min 0} :int]]]]]]
   ["CONDITIONAL_ORDER_TRIGGER_REJECT"
    [:merge
     BaseEvent
     [:map
      [:e [:= "CONDITIONAL_ORDER_TRIGGER_REJECT"]]
      [:or [:map
            [:s :string]
            [:i {:min 0} :int]
            [:r :string]]]]]]])

;; We validate and..

(m/validate BinanceUserEvent3 grid-update {:registry registry})

;; That didn't work
;; Let's decode first
(def decoded (m/decode BinanceUserEvent3 grid-update {:registry registry} mt/string-transformer))
(m/validate BinanceUserEvent3 decoded {:registry registry})

;; Neato

;; If we want to fuse the decoding and validation step, we can use a coercer

(m/coerce
 BinanceUserEvent3
 grid-update
 mt/string-transformer
 {:registry registry})

;; If the value passes muster, we get a coerced value back, otherwise
;; coercion will fail in a configurable fashion.
