(ns language-learning.vocabulary-estimation.article-controls-test
  (:require [cljs.test :refer [deftest is testing]]
            [language-learning.vocabulary-estimation.article-controls :as controls]))

(deftest stored-preference-test
  (testing "missing storage uses the declared fallback"
    (is (true? (controls/stored-preference nil true)))
    (is (false? (controls/stored-preference nil false))))
  (testing "only the persisted string true opens a disclosure"
    (is (true? (controls/stored-preference "true" false)))
    (is (false? (controls/stored-preference "false" true)))
    (is (false? (controls/stored-preference "invalid" true)))))

(deftest disclosure-state-transition-test
  (is (false? (controls/next-disclosure-open? [true true] false)))
  (is (true? (controls/next-disclosure-open? [true false] false)))
  (is (true? (controls/next-disclosure-open? [] false)))
  (is (false? (controls/next-disclosure-open? [] true))))

(deftest available-controls-test
  (is (= {:toolbar false :help false :code false :equations false}
         (controls/available-controls
          {:explanation-count 0 :code-count 0 :equation-count 0})))
  (is (= {:toolbar true :help true :code false :equations true}
         (controls/available-controls
          {:explanation-count 2 :code-count 0 :equation-count 1}))))

(deftest namespace-loads-without-a-dom-test
  (is (false? (controls/dom-available?))))
