^{:kindly/hide-code true
  :clay             {:title  "Factorization of Eratosthenes"
                     :quarto {:author   [:timschafer]
                              :type     :post
                              :date     "2025-05-28"
                              :category :clojure
                              :tags     [:clojure.math]}}}
(ns math.primes.factorization.sieve-augmented
  (:require [clojure.math :as m]))

;; Adapts the
;; [Sieve of Eratosthenes](https://en.wikipedia.org/wiki/Sieve_of_Eratosthenes)
;; to prime factorize numbers through `n`.

(defn prime-factors [n]
  (reduce
   (fn [factors prime]
     (if (= 1 (count (nth factors prime)))
       (reduce
        (fn [factors multiple]
          (let [[composite-divisor :as divisors] (nth factors multiple)]
            (if (< prime composite-divisor)
              (let [remaining-divisor (/ composite-divisor prime)
                    remaining-divisors (nth factors remaining-divisor)
                    prime-divisors (cons prime (rest divisors))]
                (assoc
                 factors multiple
                 (if (< 1 (count remaining-divisors))
                   (concat remaining-divisors prime-divisors)
                   (cons remaining-divisor prime-divisors))))
              factors)))
        factors
        (range (* prime prime) (inc n) prime))
       factors))
   (mapv list (range (inc n)))
   (range 2 (inc (m/sqrt n)))))

(prime-factors 13)
