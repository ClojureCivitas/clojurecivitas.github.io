(ns language-learning.vocabulary-estimation.pair-frequency-logistic-v2-test
  (:require [clojure.test :refer [deftest is testing]]
            [language-learning.vocabulary-estimation.pair-frequency-logistic-v2 :as v2]))

(def small-xs [-1.5 -0.8 -0.2 0.3 0.9 1.4])

(deftest response-collapse-test
  (is (= [1 0 0] (mapv v2/collapse-response [:correct :wrong :dont-know])))
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (v2/collapse-response :maybe))))

(deftest transform-test
  (let [pairs (mapv (fn [rank frequency]
                      {:source-row-rank rank
                       :pair-frequency-rank rank
                       :pair-frequency-sn-sum frequency})
                    (range 1 5) [1.0 10.0 100.0 1000.0])
        {:keys [log10-mean log10-population-sd pairs]}
        (v2/frequency-transform pairs)]
    (is (< (Math/abs (- log10-mean 1.5)) 1.0e-12))
    (is (< (Math/abs (- (v2/mean (map :x pairs)))) 1.0e-12))
    (is (< (Math/abs (- (v2/population-sd (map :x pairs)) 1.0)) 1.0e-12))
    (is (pos? log10-population-sd))))

(deftest curve-test
  (testing "threshold is the 50% point and width spans 10% to 90%"
    (is (< (Math/abs (- 0.5 (v2/knowledge-probability 0.7 0.7 2.0))) 1.0e-12))
    (is (< (Math/abs (- 0.1 (v2/knowledge-probability -0.3 0.7 2.0))) 1.0e-12))
    (is (< (Math/abs (- 0.9 (v2/knowledge-probability 1.7 0.7 2.0))) 1.0e-12)))
  (is (apply < (map #(v2/knowledge-probability % 0.0 1.5) small-xs))))

(deftest grid-normalization-test
  (let [grid (v2/prior-grid small-xs
                            {:threshold-points 21 :width-points 11
                             :minimum-width 0.25 :maximum-width 8.0}
                            v2/default-prior)]
    (is (= 231 (count grid)))
    (is (< (Math/abs (- 1.0 (reduce + (map :weight grid)))) 1.0e-12))))

(deftest fixed-observation-and-finite-pool-test
  (doseq [responses [(repeat 3 :correct) (repeat 3 :dont-know)]]
    (let [observations (mapv (fn [index response]
                               {:pair-index index :x (nth small-xs index)
                                :response response})
                             (range 3) responses)
          posterior (v2/posterior-grid small-xs observations
                                       {:threshold-points 31 :width-points 15
                                        :minimum-width 0.25 :maximum-width 8.0}
                                       v2/default-prior)
          summary (v2/posterior-predictive-summary
                   small-xs observations posterior 300 9876)]
      (is (<= (count (filter #{:correct} responses)) (:mean summary) 6.0))
      (is (<= 0 (:lower summary) (:upper summary) 6))
      (is (= summary (v2/posterior-predictive-summary
                      small-xs observations posterior 300 9876))))))

(deftest quantile-test
  (is (= 1 (v2/quantile [5 1 4 2 3] 0.0)))
  (is (= 3 (v2/quantile [5 1 4 2 3] 0.5)))
  (is (= 5 (v2/quantile [5 1 4 2 3] 1.0))))

(deftest threshold-solver-test
  (doseq [ratio [0.1 0.3 0.5 0.7 0.9]
          width [0.75 1.5 3.0]]
    (let [target (* ratio (count small-xs))
          threshold (v2/threshold-for-total small-xs width target)]
      (is (< (Math/abs (- target (v2/expected-total small-xs threshold width)))
             1.0e-9)))))

(deftest selection-is-balanced-and-response-independent-test
  (let [pairs (mapv (fn [rank]
                      {:pair-frequency-rank rank :lemma-id rank
                       :surface-form-id rank})
                    (range 1 25))
        schedule (v2/selection-schedule pairs 8 44)]
    (is (= 3 (count schedule)))
    (is (every? #(= (set (range 8)) (set (map :stratum-index %))) schedule))
    (is (= 24 (count (distinct (map :pair-frequency-rank (apply concat schedule))))))
    (is (= schedule (v2/selection-schedule pairs 8 44)))))

(deftest stopping-test
  (is (= :continue
         (:reason (v2/stopping-check
                   {:items-tested 31 :lower 0 :upper 6000 :pool-size 8000
                    :minimum-items 32 :round-size 8
                    :target-half-width-ratio 0.1 :soft-maximum-items 96}))))
  (is (= :precision-target
         (:reason (v2/stopping-check
                   {:items-tested 40 :lower 3300 :upper 4700 :pool-size 8000
                    :minimum-items 32 :round-size 8
                    :target-half-width-ratio 0.1 :soft-maximum-items 96}))))
  (is (= :soft-maximum
         (:reason (v2/stopping-check
                   {:items-tested 96 :lower 2000 :upper 6000 :pool-size 8000
                    :minimum-items 32 :round-size 8
                    :target-half-width-ratio 0.1 :soft-maximum-items 96})))))

(deftest clj-cljs-parity-reference-test
  (is (= [0.4190103327101193 0.047783309172637116 0.5481384760217168]
         (loop [state (v2/normalize-seed 20260713) values []]
           (if (= 3 (count values))
             values
             (let [[value next-state] (v2/uniform-draw state)]
               (recur next-state (conj values value)))))))
  (is (< (Math/abs (- 0.8175744761936437 (v2/logistic 1.5))) 1.0e-15)))
