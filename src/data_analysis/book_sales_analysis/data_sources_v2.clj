(ns data-analysis.book-sales-analysis.data-sources-v2
  (:require [tablecloth.api :as tc]
            [data-analysis.book-sales-analysis.core-helpers-v2 :as helpers]))

;; ## Central Data Loading
;; All CSV datasets are loaded here with consistent names and simple tc/dataset calls

;; ### Main Orders Data (WooCommerce exports)

#_(def anonymized-presentation-ds
  "Anonymized customers full dataset of all orders - for presentation purposes
   ❗ NOT FOR SHARING"
  (tc/dataset
   (helpers/merge-csvs
    ["data/anonymized-customers-only-presentation-v2.csv"]
    {:header? true
     :separator ","
     :key-fn #(keyword (helpers/sanitize-str %))})))

(def anonymized-shareable-ds
  "Fully anonymized slice of db - for sharing purposes
   ✅ SAFE TO SHARE"
  (tc/dataset
   (helpers/merge-csvs
    ["src/data_analysis/book_sales_analysis/anonymized-all-sliced-sharing-V2.csv"]
    {:header? true
     :separator ","
     :key-fn #(keyword (helpers/sanitize-str %))})))

;; ### Book Metadata

;; ## Quick Access
;; Most commonly used datasets with short aliases

(def orders-share anonymized-shareable-ds)
#_(def orders-slides anonymized-presentation-ds)

(def corr-matrix-precalculated 
  (tc/dataset "src/data_analysis/book_sales_analysis/corr-matrix.nippy"))

corr-matrix-precalculated

(prn "Data sources loaded.")