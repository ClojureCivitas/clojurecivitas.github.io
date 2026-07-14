(ns verify-pair-frequency-logistic-v2
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [language-learning.vocabulary-estimation.pair-frequency-logistic-v2 :as v2]))

(def fixture-resource
  "language_learning/vocabulary_estimation/pair_frequency_fixture_v1.tsv")

(def output-path
  "resources/language_learning/vocabulary_estimation/pair_frequency_logistic_v2_grid_check.edn")

(defn fixture []
  (let [pairs (-> (io/resource fixture-resource)
                  slurp
                  v2/parse-fixture
                  v2/validate-fixture)
        {:keys [pairs] :as transformed} (v2/frequency-transform pairs)]
    (assoc transformed :pairs
           (mapv #(assoc %1 :pair-index %2) pairs (range)))))

(defn check-grid []
  (let [{:keys [pairs]} (fixture)
        xs (mapv :x pairs)
        selected (->> (v2/selection-schedule pairs 8 v2/default-seed)
                      (take 6)
                      (mapcat identity)
                      (mapv :pair-index))
        threshold (v2/threshold-for-total xs 1.5 4000.0)
        observations (v2/simulate-responses
                      xs selected {:threshold threshold :width 1.5}
                      2026071303)
        coarse-posterior (v2/posterior-grid xs observations)
        coarse-summary (v2/posterior-predictive-summary
                        xs observations coarse-posterior 20000 2026071304)
        doubled (v2/doubled-grid v2/default-grid)
        doubled-posterior (v2/posterior-grid xs observations doubled
                                             v2/default-prior)
        doubled-summary (v2/posterior-predictive-summary
                         xs observations doubled-posterior 20000 2026071304)
        result {:fixture-id v2/fixture-id
                :fixture-sha256 v2/fixture-sha256
                :observations (mapv #(select-keys % [:pair-index :response])
                                    observations)
                :default-grid v2/default-grid
                :doubled-grid doubled
                :posterior-draws 20000
                :seed 2026071304
                :coarse coarse-summary
                :doubled doubled-summary
                :convergence (v2/grid-convergence coarse-summary
                                                  doubled-summary)}]
    (io/make-parents output-path)
    (spit output-path (with-out-str (pprint/pprint result)))
    result))

(prn (select-keys (check-grid) [:coarse :doubled :convergence]))
