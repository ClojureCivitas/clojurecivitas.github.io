(ns language-learning.vocabulary-estimation.article-equation-code-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def authored-root
  (io/file "src" "language_learning" "vocabulary_estimation"))

(defn authored-article-files []
  (->> (file-seq authored-root)
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".clj"))
       (filter #(str/includes? (slurp %) ":clay"))))

(defn occurrence-count [pattern text]
  (count (re-seq pattern text)))

(deftest every-display-equation-has-code-provenance-and-symbol-help
  (doseq [file (authored-article-files)
          :let [source (slurp file)
                equations (occurrence-count #"(?s)\$\$.*?\$\$" source)
                snippets (occurrence-count #"\([^\s/]+/equation-code-detail" source)
                provenance (occurrence-count #":kind\s+:(?:source|explanation)" source)
                symbol-sets (occurrence-count #":symbols\s+\[" source)]]
    (testing (.getPath file)
      (is (= equations snippets)
          "Every display equation must have one equation-code-detail")
      (is (= snippets provenance)
          "Every equation code detail must declare source or explanatory provenance")
      (is (= snippets symbol-sets)
          "Every equation code detail must define code-symbol help"))))

(deftest controls-are-installed-for-the-managed-namespace
  (let [metadata (slurp (io/file "site" "language_learning"
                                 "vocabulary_estimation" "_metadata.yml"))
        script (slurp (io/file "site" "vocabulary_article_controls_script.html"))
        style (slurp (io/file "site" "vocabulary_article_controls_style.html"))]
    (is (str/includes? metadata "vocabulary_article_controls_style.html"))
    (is (str/includes? metadata "vocabulary_article_controls_script.html"))
    (is (str/starts-with? script "<script>"))
    (is (str/ends-with? (str/trim script) "</script>"))
    (is (str/includes? script "civitas.vocabulary-articles.code-open"))
    (is (str/includes? script "localStorage"))
    (doseq [icon-class ["article-equation-title-icon"
                        "article-code-title-icon"
                        "article-math-help-title-icon"
                        "article-code-help-title-icon"
                        "article-text-help-title-icon"]]
      (is (str/includes? script icon-class))
      (is (str/includes? style icon-class)))))

(deftest article-only-helpers-are-hidden-from-published-output
  (let [source (slurp (io/file authored-root "beta_binomial_first_pass.clj"))]
    (is (re-find #"\^\{:kindly/hide-code true\s+:kindly/kind :kind/hidden\}\s+\(defn- resource-text" source)
        "The CSS resource helper code and returned Var must not render as article content")))

(deftest reading-controls-only-show-available-actions
  (let [script (slurp (io/file "site" "vocabulary_article_controls_script.html"))]
    (is (str/includes? script "const hasManageableContent ="))
    (is (str/includes? script "if (!hasManageableContent)"))
    (is (str/includes? script "toolbar.hidden = !hasAvailableControls"))
    (is (str/includes? script "codeAction.hidden = codeDetails.length === 0"))
    (is (str/includes? script "equationsAction.hidden = equationDetails.length === 0"))))
