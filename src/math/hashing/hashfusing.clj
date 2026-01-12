^{:kindly/hide-code true
  :clay {:title  "Hash Fusing"
         :quarto {:type     :post
                  :author   [:jclaggett]
                  :date     "2026-01-12"
                  :description "Exploring Hash Fusing via Upper Triangular Matrix Multiplication."
                  :category :math
                  :tags     [:matrix :data :hash]
                  :keywords [:data :hash :sha256 :matrix :fusing :merkle-tree]
                  :draft    true}}}
(ns math.hashing.hashfusing
  (:require [scicloj.kindly.v4.kind :as kind]))

;; The basic question this article tries to answer is: Can fusing hashes
;; together provide unique identifiers for arbitrary data? The answer is no.
;; Specifically, we can not provide unique identifiers for sufficiently large
;; repeated sequences of values. That is, sufficiently low entrory dtat is not
;; reprentable by fusing hashes together. The good news is that low entropy
;; data is compressible.

;; This is an article about fusing hashes together while having the key
;; properties of associativity and non-communativity. I describe the basic
;; approach of using matrix multiplication of Upper Triangle Matricies and then
;; show the results of two types experiments showing the quality and limits of
;; this hash fusing approach.

;; ## Introduction

;; I have begun working on implementing distributed immutable data structures
;; as a way to efficiently share structured data between multiple clients. One
;; of the key challenges in this area is being able to efficiently reference
;; ordered collections of data. The usual apprach is to use Merkle Trees but,
;; since the immuable data structure for orderd collections is based on finger
;; trees, I need references that are insensivite to tree shape. This means that
;; I need to be able to fuse hashes together in a way that is associative but
;; not communative. As a starting point, I have been reading the HP paper
;; describing hash fusing via matrix multiplication:
;; https://www.labs.hpe.com/techreports/2017/HPE-2017-08.pdf

;; ## Hash Fusing via Upper Triangular Matrix Multiplication

;; The basic approach of fusing two hashes is to represent each hash as an
;; upper triangular matrix. For example, if we have a hash that is 4 bytes
;; long, we can represent it as a 4x4 upper triangular matrix like this:

(set! *unchecked-math* true)

(defn random-hex
  "Generate a random hex string representing length `n` bytes."
  [n]
  (let [hex-chars "0123456789abcdef"]
    (apply str (repeatedly (* 2 n) #(rand-nth hex-chars)))))

(defn hex->hash8
  "Convert a hex string to a byte vector."
  [s]
  (->> s
       (partition 2)
       (map #(-> (apply str %)
                 (Integer/parseInt 16)))
       (vec)))

(defn hash8->hex
  "Convert a byte vector to a hex string."
  [bytes]
  (->> bytes
       (map #(format "%02x" %))
       (apply str)))

(def zero-hex
  "Generate a zero hash of given byte length."
  (-> (repeat 32 0)
      hash8->hex))

(def h1
  (hex->hash8 "a1a2a3a4"))

^:kindly/hide-code
(kind/table
 {:row-vectors (->> h1
                    (partition 2)
                    (map #(apply str %))
                    (vec))})
(def m1
  [[1 (h1 0) (h1 1) (h1 2)]
   [0 1 (h1 3) 0]
   [0 0 1 0]
   [0 0 0 1]])

^:kindly/hide-code
(kind/table
 {:row-vectors m1})

;; To fuse two hashes, we convert each hash to its corresponding upper
;; triangular matrix and then multiply the two matrices together. The result
;; is another upper triangular matrix which can be converted back to a hash by
;; taking the elements above the main diagonal.

(defn hash8->utm8
  "Convert a vector of 32 bytes into a 9x9 upper triangular matrix."
  [[h00 h01 h02 h03 h04 h05 h06 h07
    h08 h09 h10 h11 h12 h13 h14 h15
    h16 h17 h18 h19 h20 h21 h22 h23
    h24 h25 h26 h27 h28 h29 h30 h31]]
  [[1 h00 h08 h15 h21 h26 h29 h31 h25]
   [0   1 h01 h09 h16 h22 h27 h30 h20]
   [0   0   1 h02 h10 h17 h23 h28 h14]
   [0   0   0   1 h03 h11 h18 h24 h07]
   [0   0   0   0   1 h04 h12 h19   0]
   [0   0   0   0   0   1 h05 h13   0]
   [0   0   0   0   0   0   1 h06   0]
   [0   0   0   0   0   0   0   1   0]
   [0   0   0   0   0   0   0   0   1]])

(defn hash16->utm16
  "Convert a vector of 16 2-byte words into a 7x7 upper triangular matrix."
  [[h00 h01 h02 h03 h04 h05 h06 h07
    h08 h09 h10 h11 h12 h13 h14 h15]]
  [[1 h00 h06 h10 h13 h15 h05]
   [0   1 h01 h07 h11 h14   0]
   [0   0   1 h02 h08 h12   0]
   [0   0   0   1 h03 h09   0]
   [0   0   0   0   1 h04   0]
   [0   0   0   0   0   1   0]
   [0   0   0   0   0   0   1]])

(defn hash32->utm32
  "Convert a vector of 8 4-byte words into a 5x5 upper triangular matrix."
  [[h00 h01 h02 h03 h04 h05 h06 h07]]
  [[1 h00 h04 h07 h06]
   [0   1 h01 h05 h03]
   [0   0   1 h02   0]
   [0   0   0   1   0]
   [0   0   0   0   1]])

(defn hash64->utm64
  "Convert a vector of 4 8-byte words into a 4x4 upper triangular matrix."
  [[h00 h01 h02 h03]]
  [[1 h00 h03 h02]
   [0   1 h01   0]
   [0   0   1   0]
   [0   0   0   1]])

(defn utm8->hash8
  "Convert a 9x9 upper triangular matrix back to a vector of 32 bytes."
  [[[_ h00 h08 h15 h21 h26 h29 h31 h25]
    [_   _ h01 h09 h16 h22 h27 h30 h20]
    [_   _   _ h02 h10 h17 h23 h28 h14]
    [_   _   _   _ h03 h11 h18 h24 h07]
    [_   _   _   _   _ h04 h12 h19   _]
    [_   _   _   _   _   _ h05 h13   _]
    [_   _   _   _   _   _   _ h06   _]
    [_   _   _   _   _   _   _   _   _]
    [_   _   _   _   _   _   _   _   _]]]
  (mapv #(bit-and % 0xFF)
        [h00 h01 h02 h03 h04 h05 h06 h07
         h08 h09 h10 h11 h12 h13 h14 h15
         h16 h17 h18 h19 h20 h21 h22 h23
         h24 h25 h26 h27 h28 h29 h30 h31]))

(defn utm16->hash16
  "Convert a 7x7 upper triangular matrix back to a vector of 16 2-byte words."
  [[[_ h00 h06 h10 h13 h15 h05]
    [_   _ h01 h07 h11 h14   _]
    [_   _   _ h02 h08 h12   _]
    [_   _   _   _ h03 h09   _]
    [_   _   _   _   _ h04   _]
    [_   _   _   _   _   _   _]
    [_   _   _   _   _   _   _]]]
  (mapv #(bit-and % 0xFFFF)
        [h00 h01 h02 h03 h04 h05 h06 h07
         h08 h09 h10 h11 h12 h13 h14 h15]))

(defn utm32->hash32
  "Convert a 5x5 upper triangular matrix back to a vector of 8 4-byte words."
  [[[_ h00 h04 h07 h06]
    [_   _ h01 h05 h03]
    [_   _   _ h02   _]
    [_   _   _   _   _]
    [_   _   _   _   _]]]
  (mapv #(bit-and % 0xFFFFFFFF)
        [h00 h01 h02 h03 h04 h05 h06 h07]))

(defn utm64->hash64
  "Convert a 4x4 upper triangular matrix back to a vector of 4 8-byte words."
  [[[_ h00 h03 h02]
    [_   _ h01   _]
    [_   _   _   _]
    [_   _   _   _]]]
  [h00 h01 h02 h03])

(defn utm-multiply
  "Multiply two upper triangular matrices `a` and `b`. Each cell value is
  constrained by `bit-size`"
  [a b]
  (let [dim (count a)
        bit-mask (-> Long/MAX_VALUE dec)]
    (vec (for [i (range dim)]
           (vec (for [j (range dim)]
                  (cond
                    (< j i) 0
                    (= j i) 1
                    :else
                    (reduce (fn [sum k]
                              (-> sum
                                  (+ (* (get-in a [i k])
                                        (get-in b [k j])))
                                  (bit-and bit-mask)))
                            0
                            (range i (inc j))))))))))

(defn hash8->hash16
  "Convert a vector of 32 bytes into a vector of 16 2-byte words."
  [hash8]
  (vec (map (fn [i]
              (+ (bit-shift-left (hash8 (* 2 i)) 8)
                 (hash8 (+ 1 (* 2 i)))))
            (range 16))))

(defn hash8->hash32
  "Convert a vector of 32 bytes into a vector of 8 4-byte words."
  [hash8]
  (vec (map (fn [i]
              (+ (bit-shift-left (hash8 (* 4 i)) 24)
                 (bit-shift-left (hash8 (+ 1 (* 4 i))) 16)
                 (bit-shift-left (hash8 (+ 2 (* 4 i))) 8)
                 (hash8 (+ 3 (* 4 i)))))
            (range 8))))

(defn hash8->hash64
  "Convert a vector of 32 bytes into a vector of 4 8-byte words."
  [hash8]
  (vec (map (fn [i]
              (+ (bit-shift-left (hash8 (* 8 i)) 56)
                 (bit-shift-left (hash8 (+ 1 (* 8 i))) 48)
                 (bit-shift-left (hash8 (+ 2 (* 8 i))) 40)
                 (bit-shift-left (hash8 (+ 3 (* 8 i))) 32)
                 (bit-shift-left (hash8 (+ 4 (* 8 i))) 24)
                 (bit-shift-left (hash8 (+ 5 (* 8 i))) 16)
                 (bit-shift-left (hash8 (+ 6 (* 8 i))) 8)
                 (hash8 (+ 7 (* 8 i)))))
            (range 4))))

(defn hash16->hash8
  "Convert a vector 2-byte words into a vector of bytes."
  [hash16]
  (vec (mapcat (fn [word]
                 [(bit-and (bit-shift-right word 8) 0xFF)
                  (bit-and word 0xFF)])
               hash16)))

(defn hash32->hash8
  "Convert a vector of 4-byte words into a vector of bytes."
  [hash32]
  (vec (mapcat (fn [word]
                 [(bit-and (bit-shift-right word 24) 0xFF)
                  (bit-and (bit-shift-right word 16) 0xFF)
                  (bit-and (bit-shift-right word 8) 0xFF)
                  (bit-and word 0xFF)])
               hash32)))

(defn hash64->hash8
  "Convert a vector of 8-byte words into a vector of bytes."
  [hash64]
  (vec (mapcat (fn [word]
                 [(bit-and (bit-shift-right word 56) 0xFF)
                  (bit-and (bit-shift-right word 48) 0xFF)
                  (bit-and (bit-shift-right word 40) 0xFF)
                  (bit-and (bit-shift-right word 32) 0xFF)
                  (bit-and (bit-shift-right word 24) 0xFF)
                  (bit-and (bit-shift-right word 16) 0xFF)
                  (bit-and (bit-shift-right word 8) 0xFF)
                  (bit-and word 0xFF)])
               hash64)))

(def hex->utm8
  "Convert a hex string to an upper triangular matrix with 8-bit cells."
  (comp hash8->utm8 hex->hash8))

(def hex->utm16
  "Convert a hex string to an upper triangular matrix with 16-bit cells."
  (comp hash16->utm16 hash8->hash16 hex->hash8))

(def hex->utm32
  "Convert a hex string to an upper triangular matrix with 32-bit cells."
  (comp hash32->utm32 hash8->hash32 hex->hash8))

(def hex->utm64
  "Convert a hex string to an upper triangular matrix with 32-bit cells."
  (comp hash64->utm64 hash8->hash64 hex->hash8))

(def utm8->hex
  "Convert an upper triangular matrix with 8-bit cells to a hex string."
  (comp hash8->hex utm8->hash8))

(def utm16->hex
  "Convert an upper triangular matrix with 16-bit cells to a hex string."
  (comp hash8->hex hash16->hash8 utm16->hash16))

(def utm32->hex
  "Convert an upper triangular matrix with 32-bit cells to a hex string."
  (comp hash8->hex hash32->hash8 utm32->hash32))

(def utm64->hex
  "Convert an upper triangular matrix with 64-bit cells to a hex string."
  (comp hash8->hex hash64->hash8 utm64->hash64))

(defn with-conversion
  "Use given converters and return a high order function that converts incoming
  parameters and return value."
  [to-fn from-fn]
  (fn
    [f & args]
    (->> args
         (map to-fn)
         (apply f)
         (from-fn))))

(def apply-hash8
  "Return a fn that takes `f` and applies it to 32-byte hashes, converting them
  and then applying `f` to them."
  (with-conversion hex->utm8 utm8->hex))

(def apply-hash16
  "Return a fn that takes 32-byte hashes, converts them into a utm of 2-byte
  words per cell and then applies `f` to them."
  (with-conversion hex->utm16 utm16->hex))

(def apply-hash32
  "Return a fn that takes 32-byte hashes, converts them into a utm of 4-byte
  words per cell and then applies `f` to them."
  (with-conversion hex->utm32 utm32->hex))

(def apply-hash64
  "Return a fn that takes 32-byte hashes, converts them into a utm of 8-byte
  words per cell and then applies `f` to them."
  (with-conversion hex->utm64 utm64->hex))

;; ### Example Multiplications

;; Here are some example multiplications of two random 32-byte hashes using
;; different bit sizes for the cells of the upper triangular matrices.

(let [a (random-hex 32)
      b (random-hex 32)]
  (kind/table
   {:row-vectors
    {:a a
     :b b
     :b8 (apply-hash8 utm-multiply a b)
     :b16 (apply-hash16 utm-multiply a b)
     :b32 (apply-hash32 utm-multiply a b)
     :b64 (apply-hash64 utm-multiply a b)}}))

;; ## Experiment 1: Random Fuses

;; This experiment is run with two different hashes which are fused onto an
;; accumulator iteratively. For each iteration, one of the two hashes is
;; randomly selected and is fused to the accumulator. This random fusing is
;; repeated many times and the quality of the accumulator is measured both by
;; keeping track of global uniqueness after each fuse and by the uniform
;; distribution of bit values.

(defn random-fuses
  "Perform random fuses of two hashes onto an accumulator keeping track of all
  unique hashes produced and all duplicate hashes produced. Repeat this for all
  four utm sizes based on cell bit size."
  []
  (let [hashes {:a (random-hex 32)
                :b (random-hex 32)}
        ;; convert hashes to utms for each bit size and store in a map
        utms {8  (update-vals hashes hex->utm8)
              16 (update-vals hashes hex->utm16)
              32 (update-vals hashes hex->utm32)
              64 (update-vals hashes hex->utm64)}
        results {8  {:acc (hex->utm8 zero-hex)
                     :unique   #{}
                     :duplicates #{}}
                 16 {:acc (hex->utm16 zero-hex)
                     :unique   #{}
                     :duplicates #{}}
                 32 {:acc (hex->utm32 zero-hex)
                     :unique   #{}
                     :duplicates #{}}
                 64 {:acc (hex->utm64 zero-hex)
                     :unique   #{}
                     :duplicates #{}}}
        results (reduce
                 (fn [results _]
                   (reduce
                    (fn [results bit-size]
                      (let [curr-acc (get-in results [bit-size :acc])
                            ;; randomly select one of the two hashes
                            selected-hash (if (< (rand) 0.5)
                                            (get-in utms [bit-size :a])
                                            (get-in utms [bit-size :b]))
                            ;; fuse the selected hash onto the accumulator
                            new-acc (utm-multiply curr-acc selected-hash)]
                        ;; update results with new accumulator and uniqueness info
                        (if (contains? (get-in results [bit-size :unique]) new-acc)
                          (update-in results [bit-size :duplicates] conj new-acc)
                          (-> results
                              (assoc-in [bit-size :acc] new-acc)
                              (update-in [bit-size :unique] conj new-acc)))))
                    results
                    [8 16 32 64]))
                 results
                 (range 10000))]
    ;; convert final results to totals of unique and duplicate hashes
    (->> results
         (map (fn [[bit-size {:keys [unique duplicates]}]]
                {:bit-size   bit-size
                 :unique     (count unique)
                 :duplicates (count duplicates)}))
         (kind/table))))
(random-fuses)

;; ## Experiment 2: Folded Fuses

;; This experiment is run with a hash fused together with itself (i.e. folding)
;; and then the result in turn fused with itself and so on. This folding is
;; repeated many times and the quality of the accumulator is measured both by
;; keeping track of global uniqueness after each fuse and by the uniform
;; distribution of bit values. Once the lower bits of the accumulator become all
;; zero, the folding stops and the number of folds to reach this state is
;; recorded.

(defn zero-in-low-bits?
  "Return true if the lower bits of the utm are all zero."
  [utm bit-size]
  (let [bit-mask (bit-shift-right Long/MAX_VALUE (- 64 bit-size))]
    (every?
     (fn [[i j]]
       (or (<= j i)
           (zero? (bit-and bit-mask (get-in utm [i j])))))
     (for [i (range (count utm))
           j (range (count utm))]
       [i j]))))

(defn repeat-fold
  [starting-utm low-bit-size]
  (loop [new-fold starting-utm
         folds []]
    (let [folds (conj folds new-fold)]
      (if (or (zero-in-low-bits? new-fold low-bit-size)
              (>= (count folds) 100))
        folds
        (recur (utm-multiply new-fold new-fold)
               folds)))))

(defn folded-fuses
  "Perform folded fuses starting with a given hash and keeping track of of when
  folding produces the zero hash. Continue folding until the zero hash is found
  or 1000 folds occur and report the number of folds performed. Repeat this for
  all four utm sizes based on cell bit size and for different lower bit sizes."
  []
  (let [hash (random-hex 32)
        starting-utms {8  (hex->utm8 hash)
                       16 (hex->utm16 hash)
                       32 (hex->utm32 hash)
                       64 (hex->utm64 hash)}
        utm->hex-fns {8  utm8->hex
                      16 utm16->hex
                      32 utm32->hex
                      64 utm64->hex}
        result
        (reduce
         (fn [result bit-size]
           (assoc result bit-size
                  (reduce
                   (fn [result lower-bit-divider]
                     (let [utm->hex (get utm->hex-fns bit-size)
                           low-bit-size (quot bit-size lower-bit-divider)]
                       (assoc result low-bit-size
                              (->> (repeat-fold (get starting-utms bit-size)
                                                low-bit-size)
                                   (map utm->hex)))))
                   {}
                   [1 2 3 4 8])))
         {}
         [8 16 32 64])]

    ;; return a result with values converted back to hex strings
    (->> (for [[bit-size folds-map] result
               [low-bit-size folds] folds-map]
           {:bit-size           bit-size
            :low-bit-size  low-bit-size
            :folds-to-zero (if (< (count folds) 100) (count folds) 'NA)
            :folds folds})
         (kind/table))))
(folded-fuses)

;; ## Conclusion

;; This article has described a method for fusing hashes together using upper
;; triangular matrix multiplication. The method is associative and non-commutative.

;; Final conclusion is to use 64 bit cells with 32 lower bits to tolarate many
;; repeated values.
