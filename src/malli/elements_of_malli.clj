^:kindly/hide-code
^{:clay {:title  "Elements of Malli"
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
   [clojure.data :as data]
   [malli.core :as m]
   [malli.util :as mu]
   [malli.transform :as mt]
   [malli.provider :as mp]
   [malli.generator :as mg]
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
 {:min 0}
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
;; coercion will fail in a configurable fashion and explode by default.

;; The opposite of decoding is encoding. Some values must be converted to other types before getting sent over the wire. Big decimals to strings, java Instants to string or UNIX timestamps, etc.

(let [[only-in-decoded only-in-encoded in-both] (data/diff decoded (m/encode BinanceUserEvent3 decoded {:registry registry} mt/string-transformer))]
  (def only-in-encoded only-in-encoded)
  (def only-in-decoded only-in-decoded)
  (def in-both in-both))

only-in-decoded
only-in-encoded

;; Integers got encoded as strings, decimals didn't

;; This could potentially be an issue for malli

;; Transformers are actually interceptors and can be composed and
;; chained in a variety of interesting ways. Interesting in how
;; confusing they are, so if you can avoid the non trivial use cases,
;; please do.

;; # Generators

;; We can generate data based on our schema

(mg/generate BinanceUserEvent3 {:registry registry})

;; Generators come preconfigured for all built in schemas so you can
;; often generate sample data for your systems or functions, or use your
;; schemas for property based testing.

;; # Providers

;; Providers take a collection of sample data and infer its schema

;; Take this sample of bybit trade data

(def sample-ticks
  [{:type "snapshot", :topic "publicTrade.BTCUSDT", :ts 1760687689578, :data [{:L "MinusTick", :v "0.002", :T 1760687689577, :s "BTCUSDT", :RPI false, :BT false, :seq 466892370942, :S "Sell", :p "105653.50", :i "93ba826d-6b81-5b72-931d-63875d54c7e4"}]}
   {:type "snapshot", :topic "publicTrade.BTCUSDT", :ts 1760687689768, :data [{:L "ZeroMinusTick", :v "0.018", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "fafc47e0-2819-5c06-96d2-5768cbeb3051"} {:L "ZeroMinusTick", :v "0.012", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "ec3f9fda-5a95-5d11-90f1-4283554b8da4"} {:L "ZeroMinusTick", :v "0.094", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "dc961e7e-5aec-5ed6-a9f6-0ac5897c6a67"} {:L "ZeroMinusTick", :v "0.110", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "360d016c-4d56-5c15-9df2-f6ba8890087c"} {:L "ZeroMinusTick", :v "0.001", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "1590886e-b196-5a7b-a6c7-bc5c8fb745d3"} {:L "ZeroMinusTick", :v "0.078", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "8d0bb4e6-e06f-5b81-ae07-b90c0b837a34"} {:L "ZeroMinusTick", :v "0.018", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "232d8f6f-bb53-59d3-b65e-730e435e4899"} {:L "ZeroMinusTick", :v "0.001", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "bf3269fa-c2cb-5593-98d9-c5226304ede9"} {:L "ZeroMinusTick", :v "0.292", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "8df9fd38-bb97-5632-a614-573e19ac2507"} {:L "ZeroMinusTick", :v "0.070", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "e5daf591-3582-5b77-a768-12c9e1b0445a"} {:L "ZeroMinusTick", :v "0.159", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "cc98c5df-b110-550d-98a7-efbefd66910e"} {:L "ZeroMinusTick", :v "0.684", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "e1d0e730-61fc-5e86-8c97-0eec249e4780"} {:L "ZeroMinusTick", :v "0.121", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "5fc09172-51f0-50f2-947f-724369549dd6"} {:L "ZeroMinusTick", :v "0.098", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "91d4c814-bbbf-56b4-92d6-77e258563c4c"} {:L "ZeroMinusTick", :v "0.120", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "a8a069eb-c394-5687-a024-dab98724c252"} {:L "ZeroMinusTick", :v "0.344", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "8ad6c8df-6370-59ff-b44d-b27ef7d9b62c"} {:L "ZeroMinusTick", :v "0.037", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "ccb5562f-dd86-5a59-9636-0954a6d5b64e"} {:L "ZeroMinusTick", :v "0.480", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "05af0c08-9419-5192-a89c-62691508e7ef"} {:L "ZeroMinusTick", :v "0.335", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "eeb18f9b-67fd-590b-b88c-48f8e6446074"} {:L "ZeroMinusTick", :v "0.180", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "71beffe9-78d3-599c-8bee-5f537e5cc503"} {:L "ZeroMinusTick", :v "0.052", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "31837edf-7f6b-54ef-957e-1808eab3ab47"} {:L "ZeroMinusTick", :v "0.034", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "5d02fae3-d30d-5b7b-8c7f-98bb3a81c733"} {:L "ZeroMinusTick", :v "0.007", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.50", :i "a6df6d9d-db5e-5578-b0e3-188fce09815e"} {:L "MinusTick", :v "0.021", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.30", :i "c4d464a6-fb20-52f3-ae43-a4e5f588d961"} {:L "MinusTick", :v "0.033", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.10", :i "c5587dd8-781c-5313-a4b0-8027567f76e3"} {:L "MinusTick", :v "0.054", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105653.00", :i "f399338c-acbe-5d6a-b4be-104d48f52613"} {:L "MinusTick", :v "0.054", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105651.00", :i "39474de8-0575-584d-869c-c122499f2948"} {:L "MinusTick", :v "0.001", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105650.70", :i "2f027b0f-a9c3-50dd-905f-87fbe18ec26b"} {:L "MinusTick", :v "0.012", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105650.30", :i "6e1f3f52-efcc-540d-b861-c444697fbb3e"} {:L "MinusTick", :v "0.001", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105650.00", :i "ee2595c6-9f26-56fc-9657-0d0e249b2372"} {:L "MinusTick", :v "0.118", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105649.90", :i "ad2e2db6-a4a9-5441-b508-85c5c77c5781"} {:L "ZeroMinusTick", :v "0.024", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105649.90", :i "8a369055-4bae-53de-9d12-72718063844a"} {:L "ZeroMinusTick", :v "0.300", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105649.90", :i "73a8fbbf-3c1c-50cc-901c-526e7e49688c"} {:L "ZeroMinusTick", :v "0.353", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105649.90", :i "a67e9394-2e7e-5851-9f30-f00c631dfe6e"} {:L "MinusTick", :v "1.000", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105647.70", :i "d109112f-fb88-5574-b3eb-92bcf2781587"} {:L "MinusTick", :v "0.012", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105647.20", :i "486f76fa-cf63-5685-aa56-cc351839c89e"} {:L "MinusTick", :v "0.001", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105647.10", :i "f93e22c4-bc7d-54a6-9810-4644294d843f"} {:L "MinusTick", :v "0.001", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105646.70", :i "42aa3bec-5653-50ca-9dea-4f52c6700be6"} {:L "MinusTick", :v "0.004", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105645.50", :i "03e38529-924c-5de5-9ff1-791dca4946c8"} {:L "ZeroMinusTick", :v "1.000", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105645.50", :i "ba77df19-f71e-5564-9ec5-6bebb6c56ea7"} {:L "ZeroMinusTick", :v "0.019", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105645.50", :i "f1f5568f-ef0d-54ff-885d-d6180e3faf6f"} {:L "MinusTick", :v "0.004", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105644.80", :i "6950961a-a04c-5686-a88b-99a91868aeb7"} {:L "ZeroMinusTick", :v "0.004", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105644.80", :i "1e73fc89-5809-51df-8827-dd41b714268a"} {:L "ZeroMinusTick", :v "1.000", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105644.80", :i "4758e224-f702-55e1-b64d-7810f484f872"} {:L "ZeroMinusTick", :v "0.597", :T 1760687689766, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371220, :S "Sell", :p "105644.80", :i "63c45c73-b146-5f06-a220-fe6f3789716f"}]}
   {:type "snapshot", :topic "publicTrade.BTCUSDT", :ts 1760687689773, :data [{:L "PlusTick", :v "0.001", :T 1760687689771, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371322, :S "Sell", :p "105648.00", :i "bbdd826e-f257-5da9-85f8-1e639459e842"}]}
   {:type "snapshot", :topic "publicTrade.BTCUSDT", :ts 1760687689774, :data [{:L "MinusTick", :v "0.012", :T 1760687689772, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371409, :S "Buy", :p "105647.80", :i "4cfd148d-8e47-5f75-b1a8-8236fab529ff"}]}
   {:type "snapshot", :topic "publicTrade.BTCUSDT", :ts 1760687689774, :data [{:L "MinusTick", :v "0.001", :T 1760687689772, :s "BTCUSDT", :RPI false, :BT false, :seq 466892371416, :S "Sell", :p "105645.80", :i "40eb0a6e-1828-566a-b2d2-1ce5380bf00d"}]}])

(def inferred (mp/provide sample-ticks))
inferred

;; Even if this isn't the ideal schema we'd want to work with, it's a good rough starting spot

(def idealized
  [:map
   [:type [:= "snapshot"]]
   [:topic :string]
   [:ts :int]
   [:data
    [:vector
     [:map
      [:L [:enum "PlusTick" "MinusTick" "ZeroMinusTick"]]
      [:v [decimal? {:min 0}]]
      [:T :int]
      [:s :string]
      [:RPI :boolean]
      [:BT :boolean]
      [:seq :int]
      [:S :string]
      [:p [decimal? {:min 0}]]
      [:i :uuid]]]]])

(m/coerce idealized (first sample-ticks) mt/string-transformer)

;; That's nicer.

;; Another nice aspect of providers is even if we only have one sample,
;; they can save us some boilerplate in typing out the schema

(mp/provide [(first grid-update)])

;; That's how I filled out the grid update schema for this example.

;; # Function Schemas

;; ```clojure
;; (defn square [x] (* x x))
;; (m/=> square [:=> [:cat int?] nat-int?])
;;
;; (defn plus
;;   ([x] x)
;;   ([x y] (+ x y)))
;;
;; (m/=> plus [:function
;;             [:=> [:cat int?] int?]
;;             [:=> [:cat int? int?] int?]])
;;
;; (defn plus1
;;   "Adds one to the number"
;;   {:malli/schema [:=> [:cat :int] :int]}
;;   [x] (inc x))
;; ```

;; These, or inline annotations, can be used to emit clj-kondo configurations or instrument live functions

;; #### emit clj-kondo configurations

;; ```clojure
;; (require '[malli.clj-kondo :as mc])
;; (mc/emit!)
;; ```

;; #### Instrument functions

;; To instrument and report functions:
;; (require '[malli.dev :as dev])
;; (require '[malli.dev.pretty :as pretty])
;; (dev/start! {:report (pretty/reporter)})

;; to only instrument functions, for instances such as test time:
;; ```clojure
;; (require '[malli.instrument :as mi])
;; ```

;; # Custom Schemas

;; ## Case study - URL schema

;; We already have a URI schema, but let's assume we need one for a URL.

;; Instead of doing

[:and :string [:fn (fn [x] (try (java.net.URL. x) (catch Exception _ false)))]]

;; We can use the most common schema constructor, `-simple-schema`

(defn -url-schema
  []
  (m/-simple-schema
   {:type :url,
    :pred (fn [x] (instance? java.net.URL x))}))

(m/validate (-url-schema) (java.net.URL. "https://logseq.com"))

;; What about strings, though? Add a transformer:


(defn -url-schema
  []
  (m/-simple-schema
   {:type :url,
    :pred (fn [x] (instance? java.net.URL x))
    :type-properties
    {:decode/string
     (fn [s]
       (when (string? s)
         (try (java.net.URL. s)
              (catch Exception _ s))))}}))

(m/coerce (-url-schema) "https://logseq.com" mt/string-transformer)

;; # Recommendations and Best Practices (The Meta)

;; ## Names

;; Choosing names is hard. Choose names that map to domain entities and attributes:

;; ```clojure
;; IntRange100 => CampaignID
;; User
;; Version
;; CampaignName
;; CharacterAlignment
;; ```

;; #### Why?
;;   - Domain Modeling
;;   - What do schemas *mean*?
;;   - They model our domain with types and predicates
;;   - Similar to a type system
;;   - Types reflect the domain entities and data
;;   - We don't have `IntegerInRange100` entity or property
;; #### Reuse
;;   - `MySchema` can be used in more than one place.
;;   - By naming the domain entity we make it possible to be reused correctly
;;   - One change propagates across the code base.
;;   - No need to change every occurrence of `:campaign-id`

;; #### How?

(def CampaignID [:int {:max 100 :min 0}])
(def Message [:map [:campaign-id CampaignID]])

;; IF for some esoteric reason it appears more than once, feel free to define that range

(def IntRange0To100 [:int {:max 100 :min 0}])
(def CampaignID IntRange0To100)
(def Foo IntRange0To100)
(def Message [:map [:campaign-id CampaignID] [:foo Foo]])

;; ### Use good case
;; - Use `PascalCase` for `def` forms
;;   - `camelCase` is not `PascalCase`
;; - Acronyms have a consistent case
;;   - `url` `:url` `URL`, not `Url`
;; -   Therefor `UserID` not `UserId`
;; - Use `::snake-case` or `"PascalCase"` for schema references
;; ####  Example
;; ```clojure
;; QueryParams
;; ::query-params
;; ```
;; #### Don't
;;   - `SHOUT-CASE`
;;     - Have some style
;;     - This is Clojure, everything in `def` is constant anyway
;;   - `snake-case`
;;     - It is good to provide a way to visually distinguish domain model definitions from any values
;; - Rationale
;;   - Need a way to visually distinguish schemas
;;   - The convention across the programming world is naming types and domain entities in `PascalCase`

;; Schema references as strings or keywords for custom schema types require a registry, see the cons example above.

;; ### Avoid noisy names
;; - No need to add a `-schema` suffix to everything
;; - A good and consistent naming convention can make it clear
;; - Works well with using a good case
;; - Compare `User` vs. `USER-SCHEMA`
;; - This advice is doubly relevant if all the schemas are in a `*.schema` namespace. DRY.

;; ## Prefer the narrowest schema
;;   - Always ask - does this make sense
;; ### Example

;; ```clojure
;; [:map
;;  [:messages-recieved int?]]
;; ```

;; - Is it possible to have received a negative number of messages?
;; - Is it legal in our domain model to have received 0?
;; - `nat-int?` and `pos-int?` are a fit for those cases

;; ## [Make Illegal States Impossible](https://www.youtube.com/watch?v=IcgmSRJHu_8)

;; If you're into that

;; #### Example - Versions
;; ```clojure
;; (def Version [:enum 1 2 3])
;; (def MessageV1 [:map [:version Version]])
;; ,,,
;; [:multi {:dispatch :version}
;;  [1 MessageV1]
;;  [2 MessageV2]
;;  ,,,]
;; ```

;; Where's the hole?

;; Is this a valid version 1 message?

;; ```clojure
;; {:version 3}
;; ```

;; #### Example - optional keys
;; 		- Ingest messages from two sources
;; ```clojure
;; [:map
;;  ,,, ;; common
;;  :from-a {:optional true} schema-a
;;  :from-b {:optional true} schema-b]
;; ```
;; Pat ourselves on the back because we cover two options with one schema

;; - Wrong! This schema covers 4 options, 2 of them are incorrect!
;; - We can find this out when we generate values

;; Split:
;; ```clojure
;; (def Common  [:map ,,,])
;; (def FromA (mu/merge Common [:map [:from-a schema-a]]))
;; (def FromB (mu/merge Common [:map [:from-b schema-b]]))
;; (def Message [:or FromA FromB])
;; ```

;; This also gives better generators

;; ## Prefer built ins to ad-hoc schemas

;; #### numbers

;; Type schemas are flexible

;; Good:

[:int {:min 0 :max 100}]

;; meh
[:and int? [:>= 0] [:<= 100]]

;; Bad:
[:and
 int?
 [:fn
  {:error/message "should be in range 1-100"}
  #(and (<= 0 %) (<= % 100))]]

;; Sometimes predicates are sufficient
[:int {:min 0}]
pos-int?

;; #### Strings

;; Good

[:string {:max 256}]

;; Bad

[:and
 string?
 [:fn
  {:error/message "should be < 256 characters"}
  #(>= 256 (count %))]]

;; - Prefer defining new schemas over functions
;; - Prefer decoding over string validation - add a decoder
