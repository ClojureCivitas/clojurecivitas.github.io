(ns language-learning.vocabulary-estimation.pair-frequency-logistic-v2-fixture-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [language-learning.vocabulary-estimation.pair-frequency-logistic-v2 :as v2])
  (:import [java.math BigInteger]
           [java.security MessageDigest]))

(def fixture-resource
  "language_learning/vocabulary_estimation/pair_frequency_fixture_v1.tsv")

(defn sha256 [bytes]
  (format "%064x"
          (BigInteger. 1 (.digest (MessageDigest/getInstance "SHA-256") bytes))))

(deftest fixture-identity-and-transform-test
  (let [bytes (.getBytes (slurp (io/resource fixture-resource)) "UTF-8")
        pairs (-> (String. bytes "UTF-8") v2/parse-fixture v2/validate-fixture)
        {:keys [log10-mean log10-population-sd pairs]}
        (v2/frequency-transform pairs)
        xs (mapv :x pairs)]
    (is (= v2/fixture-sha256 (sha256 bytes)))
    (is (= 8000 (count pairs)))
    (is (= [8000 8000] ((juxt :pair-frequency-rank :source-row-rank) (last pairs))))
    (is (< (Math/abs (- log10-mean 3.9736691516418343)) 1.0e-12))
    (is (< (Math/abs (- log10-population-sd 0.4310781933721919)) 1.0e-12))
    (is (< (Math/abs (v2/mean xs)) 1.0e-12))
    (is (< (Math/abs (- 1.0 (v2/population-sd xs))) 1.0e-12))))
