(ns language-learning.vocabulary-estimation.test-runner
  (:require [clojure.test :as test]
            [language-learning.vocabulary-estimation.pair-frequency-logistic-v2-fixture-test]
            [language-learning.vocabulary-estimation.pair-frequency-logistic-v2-gate-test]
            [language-learning.vocabulary-estimation.pair-frequency-logistic-v2-test]))

(defn -main [& _]
  (let [{:keys [fail error]}
        (test/run-tests
         'language-learning.vocabulary-estimation.pair-frequency-logistic-v2-test
         'language-learning.vocabulary-estimation.pair-frequency-logistic-v2-fixture-test
         'language-learning.vocabulary-estimation.pair-frequency-logistic-v2-gate-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
