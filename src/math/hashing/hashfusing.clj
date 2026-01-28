^{:kindly/hide-code true
  :clay {:title  "Hash Fusing"
         :quarto {:type     :post
                  :author   [:jclaggett]
                  :date     "2026-01-12"
                  :description "Exploring Hash Fusing via Upper Triangular Matrix Multiplication."
                  :category :math
                  :tags     [:matrix :data :hash]
                  :keywords [:data :hash :sha256 :matrix :fusing :merkle-tree]}}}
(ns math.hashing.hashfusing
  (:require [scicloj.kindly.v4.kind :as kind]))

;; This is an article about fusing hashes together while having the key
;; properties of associativity and non-commutativity. I describe the basic
;; approach of using matrix multiplication of Upper Triangle Matrices and then
;; show the results of two experiments showing the quality and limits of this
;; hash fusing approach.

;; The basic question this article tries to answer is: can fusing hashes
;; together provide unique identifiers for arbitrary data? The answer is no.
;; Specifically, the approach in this article can _not_ provide unique
;; identifiers for sufficiently large runs of repeated values. That is,
;; sufficiently low entropy data is not uniquely represented by fusing hashes
;; together.

;; The good news is that low entropy data may always be converted into high
;; entropy data either via compression or injecting noise. A practical fusing
;; operator can easily detect fuses of low entropy data and throw an exception
;; when fusing produces those 'bad' hash values.

;; ## Introduction

;; I have begun working on implementing distributed immutable data structures
;; as a way to efficiently share structured data between multiple clients. One
;; of the key challenges in this area is being able to efficiently reference
;; ordered collections of data. The usual approach is to use Merkle Trees which
;; have the limitation that they are non-associative and the shape of the tree
;; determines the final hash value at the root of the tree. But, since I plan
;; on using Finger Trees to efficiently represent ordered collections of data,
;; I need computed hashes that are insensitive to the tree shape. This means
;; that I need to be able to fuse hashes together in a way that is associative
;; and also non-commutative. As a starting point, I have been reading the HP
;; paper describing hash fusing via matrix multiplication:
;; [https://www.labs.hpe.com/techreports/2017/HPE-2017-08.pdf](https://www.labs.hpe.com/techreports/2017/HPE-2017-08.pdf)

;; ## Hash Fusing via Upper Triangular Matrix Multiplication

;; The basic approach of fusing two hashes is to represent each hash as an
;; upper triangular matrix and then to multiply the two matrices together.
;; Since matrix multiplication is associative but not commutative. The
;; following sections define hashes and build up the necessary functions to
;; perform hash fusing via upper triangular matrix multiplication.

;; ### Hash Representation

;; A hash is represented as a string of 64 hexadecimal values (256 bits). To
;; start with, here is a zero hash and a function to generate random hashes.

(def zero-hex (apply str (vec (repeat 64 0))))
^:kindly/hide-code zero-hex

(defn random-hex
  "Generate a random hex string representing length `n` bytes."
  [n]
  (let [hex-chars "0123456789abcdef"]
    (apply str (repeatedly (* 2 n) #(rand-nth hex-chars)))))

;; Generate some random hashes for testing:
(def a-hex (random-hex 32))
^:kindly/hide-code a-hex
(def b-hex (random-hex 32))
^:kindly/hide-code b-hex
(def c-hex (random-hex 32))
^:kindly/hide-code c-hex

;; To fuse two hashes, we convert each hash to an upper triangular matrix and
;; then multiply the two matrices together. The result is another upper
;; triangular matrix which can be converted back to a hash by taking the
;; elements above the main diagonal. The following several sections defines
;; this mapping between hashes and upper triangular matrices. For the
;; experiments below four different bit sizes of cells and four corresponding
;; matrices are defined.

;; 8 bit cells in a 9x9 matrix:
^:kindly/hide-code
(kind/tex
 "%% Example: 7x7 Upper Triangular Matrix for 16 bit cells
  %% representing a 256 bit hash
  hash
  \\to
  [ h_0, h_1, h_2, h_3, \\ldots, h_{31} ]
  \\to
  {\\begin{bmatrix}
   1 &  h_0 &  h_8 & h_{15} & h_{21} & h_{26} & h_{29} & h_{31} & h_{25} \\\\
   0 &    1 &  h_1 &    h_9 & h_{16} & h_{22} & h_{27} & h_{30} & h_{20} \\\\
   0 &    0 &    1 &    h_2 & h_{10} & h_{17} & h_{23} & h_{28} & h_{14} \\\\
   0 &    0 &    0 &      1 &    h_3 & h_{11} & h_{18} & h_{24} &    h_7 \\\\
   0 &    0 &    0 &      0 &      1 &    h_4 & h_{12} & h_{19} &      0 \\\\
   0 &    0 &    0 &      0 &      0 &      1 &    h_5 & h_{13} &      0 \\\\
   0 &    0 &    0 &      0 &      0 &      0 &      1 &    h_6 &      0 \\\\
   0 &    0 &    0 &      0 &      0 &      0 &      0 &      1 &      0 \\\\
   0 &    0 &    0 &      0 &      0 &      0 &      0 &      0 &      1
   \\end{bmatrix}}
  ")

;; 16 bit cells in a 7x7 matrix:
^:kindly/hide-code
(kind/tex
 "%% Example: 7x7 Upper Triangular Matrix for 16 bit cells
  %% representing a 256 bit hash
  hash
  \\to
  [ h_0, h_1, h_2, h_3, \\ldots, h_{15} ]
  \\to
  {\\begin{bmatrix}
   1 &  h_0 &  h_6 & h_{10} & h_{13} & h_{15} &  h_5 \\\\
   0 &    1 &  h_1 &  h_7 & h_{11} & h_{14} &    0 \\\\
   0 &    0 &    1 &  h_2 &  h_8 & h_{12} &    0 \\\\
   0 &    0 &    0 &    1 &  h_3 &  h_9 &    0 \\\\
   0 &    0 &    0 &    0 &    1 &  h_4 &    0 \\\\
   0 &    0 &    0 &    0 &    0 &    1 &    0 \\\\
   0 &    0 &    0 &    0 &    0 &    0 &    1
   \\end{bmatrix}}
  ")

;; 32 bit cells in a 5x5 matrix:
^:kindly/hide-code
(kind/tex
 "%% Example: 5x5 Upper Triangular Matrix for 32 bit cells
  %% representing a 256 bit hash
  hash
  \\to
  [ h_0, h_1, h_2, h_3, \\ldots, h_7 ]
  \\to
  {\\begin{bmatrix}
   1 & h_0 & h_4 & h_7 & h_6 \\\\
   0 &   1 & h_1 & h_5 & h_3 \\\\
   0 &   0 &   1 & h_2 &   0 \\\\
   0 &   0 &   0 &   1 &   0
   \\end{bmatrix}}
  ")

;; 64 bit cells in a 4x4 matrix:
^:kindly/hide-code
(kind/tex
 "%% Example: 4x4 Upper Triangular Matrix for 64 bit cells
  %% representing a 256 bit hash
  hash
  \\to
  [ h_0, h_1, h_2, h_3 ]
  \\to
  {\\begin{bmatrix}
   1 & h_0 & h_3 & h_2 \\\\
   0 & 1 & h_1 & 0 \\\\
   0 & 0 & 1 & 0 \\\\
   0 & 0 & 0 & 1
   \\end{bmatrix}}
  ")

;; ### Hex and Byte Vector Conversion Functions

;; The first type of conversion is between hex strings and byte vectors.

(defn hex->hash8
  "Convert a hex string to a byte vector."
  [s]
  (with-meta
    (->> s
         (partition 2)
         (map #(-> (apply str %)
                   (Integer/parseInt 16)))
         (vec))
    {:cell-size 8}))

(defn hash8->hex
  "Convert a byte vector to a hex string."
  [bytes]
  (->> bytes
       (map #(format "%02x" %))
       (apply str)))

(= a-hex (-> a-hex hex->hash8 hash8->hex))

;; ### Hash Conversion Functions

;; The next type of conversion is from bytes and larger word sizes.

(defn hash8->hash16
  "Convert a vector of 32 bytes into a vector of 16 2-byte words."
  [hash8]
  (with-meta
    (vec (map (fn [i]
                (+ (bit-shift-left (hash8 (* 2 i)) 8)
                   (hash8 (+ 1 (* 2 i)))))
              (range 16)))
    {:cell-size 16}))

(defn hash16->hash8
  "Convert a vector 2-byte words into a vector of bytes."
  [hash16]
  (with-meta
    (vec (mapcat (fn [word]
                   [(bit-and (bit-shift-right word 8) 0xFF)
                    (bit-and word 0xFF)])
                 hash16))
    {:cell-size 8}))

(= a-hex (-> a-hex hex->hash8 hash8->hash16 hash16->hash8 hash8->hex))

(defn hash8->hash32
  "Convert a vector of 32 bytes into a vector of 8 4-byte words."
  [hash8]
  (with-meta
    (vec (map (fn [i]
                (+ (bit-shift-left (hash8 (* 4 i)) 24)
                   (bit-shift-left (hash8 (+ 1 (* 4 i))) 16)
                   (bit-shift-left (hash8 (+ 2 (* 4 i))) 8)
                   (hash8 (+ 3 (* 4 i)))))
              (range 8)))
    {:cell-size 32}))

(defn hash32->hash8
  "Convert a vector of 4-byte words into a vector of bytes."
  [hash32]
  (with-meta
    (vec (mapcat (fn [word]
                   [(bit-and (bit-shift-right word 24) 0xFF)
                    (bit-and (bit-shift-right word 16) 0xFF)
                    (bit-and (bit-shift-right word 8) 0xFF)
                    (bit-and word 0xFF)])
                 hash32))
    {:cell-size 8}))

(= a-hex (-> a-hex hex->hash8 hash8->hash32 hash32->hash8 hash8->hex))

(defn hash8->hash64
  "Convert a vector of 32 bytes into a vector of 4 8-byte words."
  [hash8]
  (with-meta
    (vec (map (fn [i]
                (+ (bit-shift-left (hash8 (* 8 i)) 56)
                   (bit-shift-left (hash8 (+ 1 (* 8 i))) 48)
                   (bit-shift-left (hash8 (+ 2 (* 8 i))) 40)
                   (bit-shift-left (hash8 (+ 3 (* 8 i))) 32)
                   (bit-shift-left (hash8 (+ 4 (* 8 i))) 24)
                   (bit-shift-left (hash8 (+ 5 (* 8 i))) 16)
                   (bit-shift-left (hash8 (+ 6 (* 8 i))) 8)
                   (hash8 (+ 7 (* 8 i)))))
              (range 4)))
    {:cell-size 64}))

(defn hash64->hash8
  "Convert a vector of 8-byte words into a vector of bytes."
  [hash64]
  (with-meta
    (vec (mapcat (fn [word]
                   [(bit-and (bit-shift-right word 56) 0xFF)
                    (bit-and (bit-shift-right word 48) 0xFF)
                    (bit-and (bit-shift-right word 40) 0xFF)
                    (bit-and (bit-shift-right word 32) 0xFF)
                    (bit-and (bit-shift-right word 24) 0xFF)
                    (bit-and (bit-shift-right word 16) 0xFF)
                    (bit-and (bit-shift-right word 8) 0xFF)
                    (bit-and word 0xFF)])
                 hash64))
    {:cell-size 8}))

(= a-hex (-> a-hex hex->hash8 hash8->hash64 hash64->hash8 hash8->hex))

;; ### Matrix Conversion Functions

;; The following functions convert between byte vectors representing hashes and
;; four different upper triangular matrix sizes based on cell bit size.

(defn hash8->utm8
  "Convert a vector of 32 bytes into a 9x9 upper triangular matrix."
  [[h00 h01 h02 h03 h04 h05 h06 h07
    h08 h09 h10 h11 h12 h13 h14 h15
    h16 h17 h18 h19 h20 h21 h22 h23
    h24 h25 h26 h27 h28 h29 h30 h31]]
  (with-meta
    [[1 h00 h08 h15 h21 h26 h29 h31 h25]
     [0   1 h01 h09 h16 h22 h27 h30 h20]
     [0   0   1 h02 h10 h17 h23 h28 h14]
     [0   0   0   1 h03 h11 h18 h24 h07]
     [0   0   0   0   1 h04 h12 h19   0]
     [0   0   0   0   0   1 h05 h13   0]
     [0   0   0   0   0   0   1 h06   0]
     [0   0   0   0   0   0   0   1   0]
     [0   0   0   0   0   0   0   0   1]]
    {:cell-size 8}))

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
  (with-meta
    (mapv #(bit-and % 0xFF)
          [h00 h01 h02 h03 h04 h05 h06 h07
           h08 h09 h10 h11 h12 h13 h14 h15
           h16 h17 h18 h19 h20 h21 h22 h23
           h24 h25 h26 h27 h28 h29 h30 h31])
    {:cell-size 8}))

(defn hash16->utm16
  "Convert a vector of 16 2-byte words into a 7x7 upper triangular matrix."
  [[h00 h01 h02 h03 h04 h05 h06 h07
    h08 h09 h10 h11 h12 h13 h14 h15]]
  (with-meta
    [[1 h00 h06 h10 h13 h15 h05]
     [0   1 h01 h07 h11 h14   0]
     [0   0   1 h02 h08 h12   0]
     [0   0   0   1 h03 h09   0]
     [0   0   0   0   1 h04   0]
     [0   0   0   0   0   1   0]
     [0   0   0   0   0   0   1]]
    {:cell-size 16}))

(defn utm16->hash16
  "Convert a 7x7 upper triangular matrix back to a vector of 16 2-byte words."
  [[[_ h00 h06 h10 h13 h15 h05]
    [_   _ h01 h07 h11 h14   _]
    [_   _   _ h02 h08 h12   _]
    [_   _   _   _ h03 h09   _]
    [_   _   _   _   _ h04   _]
    [_   _   _   _   _   _   _]
    [_   _   _   _   _   _   _]]]
  (with-meta
    (mapv #(bit-and % 0xFFFF)
          [h00 h01 h02 h03 h04 h05 h06 h07
           h08 h09 h10 h11 h12 h13 h14 h15])
    {:cell-size 16}))

(defn hash32->utm32
  "Convert a vector of 8 4-byte words into a 5x5 upper triangular matrix."
  [[h00 h01 h02 h03 h04 h05 h06 h07]]
  (with-meta
    [[1 h00 h04 h07 h06]
     [0   1 h01 h05 h03]
     [0   0   1 h02   0]
     [0   0   0   1   0]
     [0   0   0   0   1]]
    {:cell-size 32}))

(defn utm32->hash32
  "Convert a 5x5 upper triangular matrix back to a vector of 8 4-byte words."
  [[[_ h00 h04 h07 h06]
    [_   _ h01 h05 h03]
    [_   _   _ h02   _]
    [_   _   _   _   _]
    [_   _   _   _   _]]]
  (with-meta
    (mapv #(bit-and % 0xFFFFFFFF)
          [h00 h01 h02 h03 h04 h05 h06 h07])
    {:cell-size 32}))

(defn hash64->utm64
  "Convert a vector of 4 8-byte words into a 4x4 upper triangular matrix."
  [[h00 h01 h02 h03]]
  (with-meta
    [[1 h00 h03 h02]
     [0   1 h01   0]
     [0   0   1   0]
     [0   0   0   1]]
    {:cell-size 64}))

(defn utm64->hash64
  "Convert a 4x4 upper triangular matrix back to a vector of 4 8-byte words."
  [[[_ h00 h03 h02]
    [_   _ h01   _]
    [_   _   _   _]
    [_   _   _   _]]]
  (with-meta
    [h00 h01 h02 h03]
    {:cell-size 64}))

;; ### Combined Conversion Functions

;; The following combined conversion functions convert between hex strings into upper
;; triangular matrices and back for the four different cell bit sizes.

(def hex->utm8
  "Convert a hex string to an upper triangular matrix with 8-bit cells."
  (comp hash8->utm8 hex->hash8))

(def utm8->hex
  "Convert an upper triangular matrix with 8-bit cells to a hex string."
  (comp hash8->hex utm8->hash8))

(= a-hex (-> a-hex hex->utm8 utm8->hex))

(def hex->utm16
  "Convert a hex string to an upper triangular matrix with 16-bit cells."
  (comp hash16->utm16 hash8->hash16 hex->hash8))

(def utm16->hex
  "Convert an upper triangular matrix with 16-bit cells to a hex string."
  (comp hash8->hex hash16->hash8 utm16->hash16))

(= a-hex (-> a-hex hex->utm16 utm16->hex))

(def hex->utm32
  "Convert a hex string to an upper triangular matrix with 32-bit cells."
  (comp hash32->utm32 hash8->hash32 hex->hash8))

(def utm32->hex
  "Convert an upper triangular matrix with 32-bit cells to a hex string."
  (comp hash8->hex hash32->hash8 utm32->hash32))

(= a-hex (-> a-hex hex->utm32 utm32->hex))

(def hex->utm64
  "Convert a hex string to an upper triangular matrix with 32-bit cells."
  (comp hash64->utm64 hash8->hash64 hex->hash8))

(def utm64->hex
  "Convert an upper triangular matrix with 64-bit cells to a hex string."
  (comp hash8->hex hash64->hash8 utm64->hash64))

(= a-hex (-> a-hex hex->utm64 utm64->hex))

(defn hex->utm
  "Convert a hex string to an upper triangular matrix with the given cell size
  (8, 16, 32, or 64 bits)."
  [hex cell-size]
  (case cell-size
    8  (hex->utm8 hex)
    16 (hex->utm16 hex)
    32 (hex->utm32 hex)
    64 (hex->utm64 hex)
    (throw (ex-info "Unsupported cell size for upper triangular matrix."
                    {:cell-size cell-size}))))

(defn utm->hex
  "Convert an upper triangular matrix with the given cell size (8, 16, 32,
  or 64 bits) to a hex string."
  [utm cell-size]
  (case cell-size
    8  (utm8->hex utm)
    16 (utm16->hex utm)
    32 (utm32->hex utm)
    64 (utm64->hex utm)
    (throw (ex-info "Unsupported cell size for upper triangular matrix."
                    {:cell-size cell-size}))))

(= a-hex (-> a-hex (hex->utm 32) (utm->hex 32)))

;; ### Upper Triangular Matrix Multiplication

;; This is the core function that performs the multiplication of two upper
;; triangular matrices. The multiplication ignores the lower triangular part of
;; of the matrices since they are always 0 (and always 1 on the main diagonal).

;; Note that unchecked math is enabled to ignore integer overflow since the
;; cells are treated as fixed size bit fields.
(set! *unchecked-math* true)

(defn utm-multiply
  "Multiply two upper triangular matrices `a` and `b`."
  [a b]
  (let [dim (count a)
        cell-size (-> a meta :cell-size)
        bit-mask (if (= cell-size 64)
                   -1
                   (dec (bit-shift-left 1 cell-size)))]
    (with-meta
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
                              (range i (inc j))))))))
      {:cell-size cell-size})))

;; #### Associativity & Non-Commutativity Properties

;; Show that upper triangular matrix multiplication is associative and
;; non-commutative. Associativity is necessary for hash fusing to work with
;; Finger Trees so that different tree shapes produce the same fused hash.
;; Non-commutativity is necessary for seqeuences of data where the order of
;; data affects the fused hash.

(-> (for [cell-size [8 16 32 64]]
      (let [a (hex->utm a-hex cell-size)
            b (hex->utm b-hex cell-size)
            c (hex->utm c-hex cell-size)
            ab (utm-multiply a b)
            ba (utm-multiply b a)
            bc (utm-multiply b c)
            ab*c (utm-multiply ab c)
            a*bc (utm-multiply a bc)]
        {:cell-size cell-size
         :associative? (= ab*c a*bc)
         :commutative? (= ab ba)}))
    (kind/table))

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
                     :uniques   #{}
                     :duplicates #{}}
                 16 {:acc (hex->utm16 zero-hex)
                     :uniques   #{}
                     :duplicates #{}}
                 32 {:acc (hex->utm32 zero-hex)
                     :uniques   #{}
                     :duplicates #{}}
                 64 {:acc (hex->utm64 zero-hex)
                     :uniques   #{}
                     :duplicates #{}}}
        results (reduce
                 (fn [results _]
                   (reduce
                    (fn [results cell-size]
                      (let [curr-acc (get-in results [cell-size :acc])
                            ;; randomly select one of the two hashes
                            selected-hash (if (< (rand) 0.5)
                                            (get-in utms [cell-size :a])
                                            (get-in utms [cell-size :b]))
                            ;; fuse the selected hash onto the accumulator
                            new-acc (utm-multiply curr-acc selected-hash)]
                        ;; update results with new accumulator and uniqueness info
                        (if (contains? (get-in results [cell-size :uniques]) new-acc)
                          (update-in results [cell-size :duplicates] conj new-acc)
                          (-> results
                              (assoc-in [cell-size :acc] new-acc)
                              (update-in [cell-size :uniques] conj new-acc)))))
                    results
                    [8 16 32 64]))
                 results
                 (range 10000))]
    ;; convert final results to totals of unique and duplicate hashes
    (->> results
         (map (fn [[cell-size {:keys [uniques duplicates]}]]
                {:cell-size   cell-size
                 :uniques     (count uniques)
                 :duplicates (count duplicates)}))
         (kind/table))))

;; ### Random Fuses Results

(random-fuses)

;; These results show that high entropy data can be well represented by fusing
;; hashes together. All four bit sizes for the cells of the upper triangular
;; matrices performed well with high entroy data. I have rerun this experiment
;; with millions of fuses and have never observed a duplicate hash being
;; produced. Since 64 bit cells fit in the smallest matrix, this is the fasted
;; to compute.

;; ## Experiment 2: Folded Fuses

;; This experiment is run with a hash fused together with itself (i.e. folding)
;; and then the result in turn fused with itself and so on. This folding is
;; repeated many times and the quality of the accumulator is measured both by
;; keeping track of global uniqueness after each fuse and by the uniform
;; distribution of bit values. Once the accumulator becomes zero the folding
;; stops and the number of folds to reach this state is recorded. Also, the
;; number of lower bits that are zero across all cells is recorded after each
;; fold.

(defn calc-zero-lower-bits
  "Calculate the number of lower bits that are zero across all upper cells in
  the upper triangular matrix."
  [utm]
  (let [cell-size (-> utm meta :cell-size)]
    (loop [mask 1 zero-lower-bits 0]
      (if (and (< zero-lower-bits cell-size)
               (->> (for [i (range (count utm))
                          j (range (inc i) (count utm))]
                      (get-in utm [i j]))
                    (map #(bit-and mask %))
                    (every? zero?)))
        (recur (bit-shift-left mask 1)
               (inc zero-lower-bits))
        zero-lower-bits))))

(defn folded-fuses
  "Perform folded fuses starting from the fixed hash `a-hex`, converted to an
  upper triangular matrix (UTM) for each cell size. After each fold, track how
  many lower bits of each cell are zero across all upper cells using
  `calc-zero-lower-bits`. Stop when all those lower bits are zero (i.e.,
  `zero-lower-bits` equals the cell size) or when 1000 folds have been
  performed. Report, for each cell size and fold, the fold count, the number
  of zero lower bits, and the resulting hash."
  []
  (let [;; convert hashes to utms for each bit size and store in a map
        results (reduce
                 (fn [results cell-size]
                   (let [fold (hex->utm a-hex cell-size)
                         zero-lower-bits (calc-zero-lower-bits fold)
                         fold-result
                         (loop [folds [{:fold fold
                                        :zero-lower-bits zero-lower-bits}]]
                           (let [{:keys [fold zero-lower-bits]} (last folds)]
                             (if (or (= zero-lower-bits cell-size)
                                     (>= (count folds) 1000))
                               folds
                               (let [fold (utm-multiply fold fold)
                                     zero-lower-bits (calc-zero-lower-bits fold)]
                                 (recur (conj folds
                                              {:fold fold
                                               :zero-lower-bits zero-lower-bits}))))))]
                     (assoc results cell-size fold-result)))
                 {}
                 [8 16 32 64])]
    ;; format results into a table
    (->> results
         (mapcat (fn [[cell-size folds]]
                   (map-indexed (fn [index {:keys [fold zero-lower-bits]}]
                                  {:cell-size      cell-size
                                   :fold-count     index
                                   :zero-lower-bits zero-lower-bits
                                   :fold (-> [:pre (utm->hex fold cell-size)]
                                             (kind/hiccup))})

                                folds)))
         (kind/table))))

;; ### Folded Fuses Results

(folded-fuses)

;; These results show that repeated folding devolves into a zero hash after
;; only a few folds. The number of bits in a cell nearly exactly defines the
;; number of folds needed before reaching a zero hash. Since each fold
;; represents a doubling of the number of fuses of the same value, folding
;; shows that fusing can _not_ reliably represent long runs of repeated values.

;; ## Conclusion

;; This article described a method for fusing hashes together using upper
;; triangular matrix multiplication. The method is associative and
;; non-commutative.

;; Experiment 1 showed that high entropy data can be well represented by fusing
;; hashes together and that the size of cell fields, from 8 to 64 bits, all
;; performed well with high entropy data. Since 64 bit cells fit in the
;; smallest matrix, this is the fasted to compute.

;; Experiment 2 showed that low entropy data, specifically long runs of
;; repeated values, can _not_ be well represented by fusing hashes together
;; since they all approach a zero hash. The rate of approach is determined by
;; the bit count in the cells. 64 bit cells were the most able to handle
;; repeated values.

;; In practice, hash fusing with upper triangular matrix multiplication is
;; okay to use if a check is made after each fuse to ensure the lower bits
;; (e.g., the lowest 32 bits) are not all zero. If this check fails, a
;; LowEntropyDataError can be raised to indicate the data being hashed has
;; insufficient entropy.

(defn high-entropy-fuse
  "Fuse two 256-bit hashes together via upper triangular matrix multiplication
  with 64-bit cells. Raise an error when the lower 32 bits of the result are
  all zero."
  [a-hex b-hex]
  (let [a (hex->utm64 a-hex)
        b (hex->utm64 b-hex)
        ab (utm-multiply a b)]
    (if (->> (for [i (range (count a))
                   j (range (inc i) (count a))]
               (get-in ab [i j]))
             (map #(bit-and 0xFFFFFFFF %))
             (every? zero?))
      (throw (ex-info "Low entropy data detected during hash fusing."
                      {:a a-hex :b b-hex :fused (utm64->hex ab)}))
      (utm64->hex ab))))

;; Example of high entropy data fusing successfully:
(high-entropy-fuse a-hex b-hex)

;; Example of low entropy data causing an error:
(try
  (high-entropy-fuse zero-hex zero-hex)
  (catch Exception e
         (.getMessage e)))

;; ## Appendix: Why Low Entropy Data Fails (AI Explanation)

;; _The following explanation was generated by Copilot in response to the
;; question: "Why does low entropy data fail to be represented by hash fusing
;; via upper triangular matrix multiplication?"_

;; The reason low entropy data fails to be represented by hash fusing is that
;; the multiplication of upper triangular matrices causes the lower bits of the
;; resulting matrix to rapidly approach zero when the same matrix is repeatedly
;; multiplied by itself. This is because the multiplication operation involves
;; summing products of the matrix elements, and when the same matrix is used
;; repeatedly, the contributions to the lower bits become increasingly
;; negligible compared to the higher bits. As a result, after a certain number
;; of multiplications, the lower bits of the resulting matrix become zero,
;; leading to a loss of information and the inability to distinguish between
;; different inputs that produce the same low-entropy pattern. This phenomenon
;; highlights the limitations of using hash fusing for low-entropy data, as it
;; fails to provide unique identifiers for such data.
