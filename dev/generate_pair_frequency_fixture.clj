(ns generate-pair-frequency-fixture
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def source-path
  (or (first *command-line-args*)
      "/Users/jamiep/Documents/subtlex/data/database-import/surface_form_lemma_pair_frequency_ranks.tsv"))

(def target-path
  (or (second *command-line-args*)
      "resources/language_learning/vocabulary_estimation/pair_frequency_fixture_v1.tsv"))

(def source-columns
  ["surface_form_lemma_pair_frequency_rank_id"
   "surface_form_id"
   "lemma_id"
   "pair_frequency_sn_sum"
   "pair_frequency_sn_sum_rank"])

(defn select-row [indexes line]
  (let [fields (str/split line #"\t" -1)]
    (str/join "\t" (map #(nth fields %) indexes))))

(with-open [reader (io/reader source-path)]
  (let [[header & rows] (line-seq reader)
        columns (str/split header #"\t" -1)
        indexes (mapv #(.indexOf columns %) source-columns)
        selected (take 8000 rows)]
    (when (some neg? indexes)
      (throw (ex-info "Required source column missing"
                      {:required source-columns :actual columns})))
    (when-not (= 8000 (count selected))
      (throw (ex-info "Source has fewer than 8,000 data rows" {})))
    (io/make-parents target-path)
    (spit target-path
          (str (str/join "\t" source-columns) "\n"
               (str/join "\n" (map #(select-row indexes %) selected))
               "\n"))))

(println target-path)
