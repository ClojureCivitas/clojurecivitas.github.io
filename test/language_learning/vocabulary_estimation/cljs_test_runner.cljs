(ns language-learning.vocabulary-estimation.cljs-test-runner
  (:require [cljs.test :as test]
            [language-learning.vocabulary-estimation.article-controls-test]
            [language-learning.vocabulary-estimation.pair-frequency-logistic-v2-test]))

(defmethod test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (test/successful? m)
    (set! (.-exitCode js/process) 1)))

(defn -main []
  (test/run-tests
   'language-learning.vocabulary-estimation.article-controls-test
   'language-learning.vocabulary-estimation.pair-frequency-logistic-v2-test))

(set! *main-cli-fn* -main)
