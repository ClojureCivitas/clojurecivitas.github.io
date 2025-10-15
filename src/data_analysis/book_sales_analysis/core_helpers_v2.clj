(ns data-analysis.book-sales-analysis.core-helpers-v2
  (:import [java.text Normalizer Normalizer$Form]
           [java.io ByteArrayInputStream ObjectInputStream])
  (:require [tablecloth.api :as tc]
            [tech.v3.dataset :as ds]
            [tablecloth.column.api :as tcc]
            [clojure.string :as str]
            [java-time.api :as jt]
            [fastmath.stats :as stats]))

;; ## Data Transformation Functions
;; Common data processing functions used across multiple analysis files

;; ### Scicloj Helpers

(defn merge-csvs [file-list options]
  (->> (mapv #(tc/dataset % options) file-list)
       (apply tc/concat)))

;; ### Column and Content Sanitizers

(defn sanitize-str
  "Sanitizes a string for use as a slug or identifier.
  Replaces underscores and spaces with hyphens, removes diacritics and parentheses, and converts to lower-case.
  Intended for general-purpose text like book titles."
  [s]
  (if (or (nil? s) (empty? s))
    s
    (let [hyphens (str/replace s #"_" "-")
          trimmed (str/trim hyphens)
          nfd-normalized (Normalizer/normalize trimmed Normalizer$Form/NFD)
          no-diacritics (str/replace nfd-normalized #"\p{InCombiningDiacriticalMarks}+" "")
          no-spaces (str/replace no-diacritics #" " "-")
          no-brackets (str/replace no-spaces #"\(|\)" "")
          lower-cased (str/lower-case no-brackets)]
      lower-cased)))


(defn sanitize-column-name-str
  "Sanitizes a string for use as a dataset column name.
  More aggressive than `sanitize-str`, it also converts slashes to hyphens, collapses multiple hyphens, 
  and removes special substrings like '(YYYY-MM)'."
  [s]
  (if (or (nil? s) (empty? s))
    s
    (-> s
        (str/replace #"\(YYYY-MM\)" "")  ; special removal
        str/trim
        (str/lower-case)
        (str/replace #"_" "-")        ; underscore to hyphens
        (str/replace #" " "-")
        (str/replace #"\/" "-")       ; slash to hyphens
        (str/replace #"-{2,}" "-")    ; multiple hyphens to one                   
        (#(Normalizer/normalize % Normalizer$Form/NFD)) ; nfd-normalized 
        (str/replace #"\p{InCombiningDiacriticalMarks}+" "")        ; no-diacritics 
        (str/replace #"\(|\)" ""))))

(defn sanitize-category-str
  "Sanitizes a string representing categories.
  Similar to other sanitizers, but specifically handles comma-separated lists by removing the space 
  after a comma (e.g., 'a, b' -> 'a,b')."
  [s]
  (if (or (nil? s) (empty? s))
    s
    (-> s
        str/trim
        (str/lower-case)
        (str/replace #"\,\s" ",")     ; underscore to hyphens
        (str/replace #"\s" "-")
        (str/replace #"\/" "-")       ; slash to hyphens
        (str/replace #"-{2,}" "-")    ; multiple hyphens to one                   
        (#(Normalizer/normalize % Normalizer$Form/NFD)) ; nfd-normalized 
        (str/replace #"\p{InCombiningDiacriticalMarks}+" "")        ; no-diacritics (dočasně)
        (str/replace #"\(|\)" ""))))

(defn parse-book-name [s]
  (-> s ;; proti parse-books bere jen řetězec
      (str/replace #"," "")
      (str/replace #"\+" "")
      (str/trim)
      sanitize-category-str
      (str/replace #"^3" "k3")
      (str/replace #"^5" "k5")))


(defn parse-csv-date [date-str]
  (let [month-names ["led" "úno" "bře" "dub" "kvě" "čvn" "čvc" "srp" "zář" "říj" "lis" "pro"]
        pad-month #(format "%02d" %)
        parse-full-date (fn [s]
                          (let [month (Integer/parseInt (subs s 3 5))]
                            (str (subs s 6 10) "-01-" (pad-month month))))
        parse-short-date (fn [s]
                           (let [[month-str year-str] (str/split s #"\.")
                                 month (inc (.indexOf month-names month-str))
                                 year (+ 2000 (Integer/parseInt year-str))]
                             (str year "-01-" (pad-month month))))]
    (try
      (jt/local-date "yyyy-dd-MM"
                     (if (> (count date-str) 6)
                       (parse-full-date date-str)
                       (parse-short-date date-str)))
      (catch Exception _
        (str "Chyba: " date-str)))))

(defn parse-books-from-list
  "Parses a book names from string `s` separated by commas into vector of cleaned keywords."
  [s]
  (if (seq s) (->> (str/split s #",\s\d+")
                   (map #(str/replace % #"\d*×\s" ""))
                   (map #(str/replace % #"," ""))
                   (map #(str/replace % #"\(A\+E\)|\[|\]|komplet|a\+e|\s\(P\+E\+A\)|\s\(e\-kniha\)|\s\(P\+E\)|\s\(P\+A\)|\s\(E\+A\)|papír|papir|audio|e\-kniha|taška" ""))
                   (map #(str/replace % #"\+" ""))
                   (map #(str/trim %))
                   (map sanitize-str)
                   (map #(str/replace % #"\-\-.+$" "")) 
                   (map #(str/replace % #"\-+$" "")) 
                   (map #(str/replace % #"^3" "k3"))
                   (map #(str/replace % #"^5" "k5"))
                   (remove (fn [item] (some (fn [substr] (str/includes? (name item) substr))
                                            ["balicek" "poukaz" "zapisnik" "limitovana-edice" "taska" "aktualizovane-vydani" "cd" "puvodni-vydani/neni-skladem"
                                             "merch"])))
                   distinct
                   (mapv keyword))
      nil))

;; ### Metadata Enriching and Convenience Functions

(defn czech-author? [book-title]
  (let [czech-books #{:k30-hodin
                      :k365-anglickych-cool-fraz-a-vyrazov
                      :k365-anglickych-cool-frazi-a-vyrazu
                      :bulbem-zachranare
                      :hacknuta-cestina
                      :handmade-byznys
                      :hot
                      :hry-site-porno
                      :jak-na-site
                      :jak-sbalit-zenu-2.0
                      :konec-prokrastinace
                      :let-your-english-september
                      :myty-a-nadeje-digitalniho-sveta
                      :na-volne-noze
                      :napoleonuv-vojak
                      :nedelni-party-s-picassem
                      :restart-kreativity
                      :sport-je-bolest
                      :stat-se-investorem
                      :temne-pocatky-ceskych-dejin
                      :uc-jako-umelec
                      :velka-kniha-fuckupu
                      :zamilujte-se-do-anglictiny
                      :pretizeny
                      :od-chaosu-ke-smyslu
                      :very-hard-orechy
                      :heureka!}]
    (if (str/starts-with? (str book-title) "book") ;; this is a part used to add flags of Czech books into fully anonymized dataset
      (rand-int 2)
      (if (contains? czech-books (keyword book-title)) 1 0))))

;; ### One-Hot Encoding Functions

   
(defn onehot-encode-by-customers ;; FIXME needs refactor and simplification :)
  "One-hot encode dataset aggregated by customer. 
   Each customer gets one row with 0/1 values for each book they bought.
   Used for market basket analysis, customer segmentation, etc."
  [raw-ds]
  (let [;; First, aggregate all purchases by customer
        customer+orders (-> raw-ds
                            (ds/drop-missing :zakaznik)
                            (tc/drop-rows #(= "" (str/trim (:zakaznik %))))
                            (ds/drop-missing :produkt-produkty)
                            (tc/group-by [:zakaznik])
                            (tc/aggregate {:all-products #(str/join ", " (tc/column % :produkt-produkty))})
                            (tc/rename-columns {:summary :all-products}))
        ;; Get all unique books from all the lines
        all-titles (->> (tc/column customer+orders :all-products)
                        (mapcat parse-books-from-list)
                        distinct
                        sort)
        ;; For each customer create one aggregated row with all purchases in 0/1 format
        customers->rows (map
                         (fn [customer-row]
                           (let [customer-name (:zakaznik customer-row)
                                 books-bought-set (set (parse-books-from-list (:all-products customer-row)))
                                 one-hot-map (reduce (fn [acc book]
                                                       (assoc acc book (if (contains? books-bought-set book) 1 0)))
                                                     {}
                                                     all-titles)]
                             (merge {:zakaznik customer-name}
                                    one-hot-map)))
                         (tc/rows customer+orders :as-maps))
        ;; Create new dataset from one-hot data
        one-hot-ds (tc/dataset customers->rows)]
    ;; Return dataset with one-hot encoding
    one-hot-ds))


;; ### Statistical Functions for Apriori Analysis

(defn calculate-support
  "Calculate support for a given itemset in a one-hot-encoded dataset.
   Support = (rows containing itemset) / (total rows)"
  [dataset itemset]
  (let [total-transactions (tc/row-count dataset)
        transactions-with-itemset (-> dataset
                                      (tc/select-rows (fn [row] (every? #(not (zero? (get row %))) itemset)))
                                      tc/row-count)]
    (if (zero? total-transactions)
      0.0
      (double (/ transactions-with-itemset total-transactions)))))


^:kindly/hide-code
(defn calculate-adaptive-coefficient
  "Calculates adaptive coefficient for popularity bias correction."
  [rules popularity-index]
  (let [median-lift (tcc/median (map :lift rules))
        max-popularity (apply max (vals popularity-index))
        ;; Calculate coefficient that would reduce a rule with max popularity
        ;; items on both sides by approximately 50% of median lift
        target-coefficient (/ (* 0.5 median-lift) (* 2 max-popularity))]
    target-coefficient))

^:kindly/hide-code
(defn improved-adjusted-lift
  "Lift adjustment using adaptive popularity bias correction."
  [rule popularity-index adaptive-coefficient]
  (let [base-lift (:lift rule)
        antecedent-items (:antecedent rule)
        consequent-items (seq (:consequent rule))
        ;; Average popularity of items in antecedent
        antecedent-popularity (tcc/mean (vals (select-keys popularity-index antecedent-items)))
        ;; Average popularity of items in consequent
        consequent-popularity (tcc/mean (vals (select-keys popularity-index consequent-items)))
        ;; Dampening factor for popular items
        popularity-penalty (+ 1 (* adaptive-coefficient
                                   (+ antecedent-popularity consequent-popularity)))]
    ;; Divide lift by penalty (popular combinations get reduced lift)
    (assoc rule :lift (double (/ base-lift popularity-penalty)))))


;; ### Visuals

(defn color-hex [support min-support max-support]
  (let [min-opacity 20
        max-opacity 255
        ;; Map support from [min-support, max-support] to [min-opacity, max-opacity]
        opacity (if (= min-support max-support)
                  ;; Handle edge case where min and max are the same
                  (int (/ (+ min-opacity max-opacity) 2))
                  (int (+ min-opacity
                          (* (- max-opacity min-opacity)
                             (/ (- support min-support)
                                (- max-support min-support))))))
        ;; Ensure opacity stays within bounds
        clamped-opacity (min max-opacity (max min-opacity opacity))
        hex-opacity (format "%02x" clamped-opacity)]
    (str "#c1ab55" hex-opacity)))


;; ### Correlation functions

(defn corr-a-x-b
  "Creates a correlation matrix with book columns and the added :book column \n
   - `ds` is dataset \n
   Example: \n
  => _unnamed [2 3]: \n
    |          :a |          :b | :book | 
    |------------:|------------:|-------| 
    |  1.00000000 | -0.12121831 |    :a | 
    | -0.12121831 |  1.00000000 |    :b |"
  [ds]
  (let
   [columns (tc/column-names ds)
    clean-ds (-> ds
                 (tc/drop-columns [:zakaznik]))]
    (-> (zipmap columns (stats/correlation-matrix (tc/columns clean-ds)))
        tc/dataset
        (tc/add-column :book columns))))

(defn corr-3-col
  "Creates a correlation matrix with two columns of books \n
  => _unnamed [4 3]: \n
      | :book-0 | :book-1 | :correlation |
      |---------|---------|-------------:|
      |      :a |      :a |   1.00000000 |
      |      :a |      :b |  -0.12121831 |
      |      :b |      :a |  -0.12121831 |
      |      :b |      :b |   1.00000000 | \n
  - `flatten` is used here to make a linear sequence of numbers which should match corresponding variable names. \n
  -  Since we make pairs of names `((for...[a b])` creates a cartesian product) we need to separate these to individual columns, tc/seperate-column does the trick, refer: https://scicloj.github.io/tablecloth/#separate"
  [ds]
  (let [names (tc/column-names ds)
        mat (flatten (stats/correlation-matrix (tc/columns ds)))]
    (-> (tc/dataset {:book (for [a names b names] [a b])
                     :correlation mat})
        (tc/separate-column :book)
        (tc/rename-columns  {":book-0" :titul-knihy
                             ":book-1" :book-1}))))


;; ### Export helper functions from other namespaces for convenience

(def sanitize-str sanitize-str)
(def merge-csvs merge-csvs)
(def parse-books-from-list parse-books-from-list)
(def sanitize-column-name-str sanitize-column-name-str)
(def parse-csv-date parse-csv-date)

(println "Core helpers loaded.")