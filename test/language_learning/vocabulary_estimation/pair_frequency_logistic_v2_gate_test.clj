(ns language-learning.vocabulary-estimation.pair-frequency-logistic-v2-gate-test
  (:require [clojure.test :refer [deftest is]]
            [language-learning.vocabulary-estimation.pair-frequency-logistic-v2-gate :as gate]))

(deftest precommitted-grid-test
  (is (= 100 (count gate/tuning-rules)))
  (is (= 45 (count gate/supported-cells)))
  (is (= 60 (count gate/stress-cells))))

(deftest complete-round-stopping-test
  (let [checkpoints
        {32 {:v2 {:lower 3000 :upper 5000}}
         40 {:v2 {:lower 3300 :upper 4700}}}]
    (is (= {:lower 3300 :upper 4700
            :items-tested 40 :stopping-reason :precision-target}
           (gate/stop-at checkpoints
                         {:minimum-items 32
                          :target-half-width-ratio 0.10
                          :soft-maximum-items 40}
                         :v2)))))

(deftest shortest-passing-rule-test
  (let [slow {:rule {:minimum-items 40 :soft-maximum-items 80
                     :target-half-width-ratio 0.075}
              :aggregate {:median-items 64 :mean-items 60.0}
              :passes? true}
        fast {:rule {:minimum-items 32 :soft-maximum-items 64
                     :target-half-width-ratio 0.10}
              :aggregate {:median-items 40 :mean-items 38.0}
              :passes? true}
        failed (assoc fast :passes? false)]
    (is (= fast (gate/choose-rule {:rules [slow failed fast]})))
    (is (nil? (gate/choose-rule {:rules [failed]})))))
