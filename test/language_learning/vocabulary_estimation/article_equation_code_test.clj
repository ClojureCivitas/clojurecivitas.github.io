(ns language-learning.vocabulary-estimation.article-equation-code-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [language-learning.vocabulary-estimation.article-controls :as controls]))

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
                equation-symbol-sets
                (occurrence-count
                 #"(?s)\([^\s/]+/equation-code-detail\s+\"[^\"]+\"\s+\"[^\"]+\"\s+\{[^}]*:symbols\s+\["
                 source)]]
    (testing (.getPath file)
      (is (= equations snippets)
          "Every display equation must have one equation-code-detail")
      (is (= snippets provenance)
          "Every equation code detail must declare source or explanatory provenance")
      (is (= snippets equation-symbol-sets)
          "Every equation code detail must define code-symbol help"))))

(deftest every-v2-display-equation-has-reader-math-help
  (let [source (slurp (io/file authored-root
                               "pair_frequency_logistic_v2_article.clj"))
        equations (occurrence-count #"(?s)\$\$.*?\$\$" source)
        explanation-registries
        (occurrence-count #"\([^\s/]+/explanation" source)]
    (is (= equations explanation-registries)
        "Every v2 display equation must define reader-facing math help")))

(deftest every-v2-code-block-has-reader-symbol-help
  (let [source (slurp (io/file authored-root
                               "pair_frequency_logistic_v2_article.clj"))
        code-details
        (occurrence-count #"\([^\s/]+/(?:equation-)?code-detail" source)
        symbol-sets (occurrence-count #":symbols\s+\[" source)]
    (is (= code-details symbol-sets)
        "Every v2 code disclosure must define reader-facing symbol help")))

(deftest v2-response-simulator-has-reader-driven-posterior-controls
  (let [interactive
        (slurp (io/file authored-root
                        "pair_frequency_logistic_v2_interactive.cljs"))]
    (doseq [contract ["record-response-demo!"
                      "posterior-joint-heatmap"
                      "Joint threshold × width posterior"
                      "Heatmap view"
                      ":aria-pressed"
                      "Maximum-probability cell"
                      "fixed probability-mass scale"
                      "posterior-marginal-chart"
                      "Threshold t posterior"
                      "Width w posterior"
                      "Correct"
                      "Wrong"
                      "Don't know"]]
      (is (str/includes? interactive contract)
          (str "Missing posterior simulator contract: " contract)))))

(deftest completed-seeded-quiz-shows-joint-and-marginal-posteriors
  (let [interactive
        (slurp (io/file authored-root
                        "pair_frequency_logistic_v2_interactive.cljs"))]
    (doseq [contract ["lab-posterior-view"
                      "Completed quiz posterior"
                      "quiz-joint-posterior"
                      "quiz-threshold-posterior-chart"
                      "quiz-width-posterior-chart"
                      "Previous means after 63 responses"]]
      (is (str/includes? interactive contract)
          (str "Missing completed-quiz posterior contract: " contract)))))

(deftest controls-installer-emits-one-toolbar-and-runtime
  (let [installed (controls/install)
        nodes (filter vector? (tree-seq coll? seq installed))
        toolbars (filter #(= :div.article-explanations-toolbar (first %))
                         nodes)
        styles (filter #(= :style (first %)) nodes)
        scripts (filter #(= :script (first %)) nodes)]
    (is (= 1 (count toolbars)))
    (is (= 1 (count styles)))
    (is (= 1 (count scripts)))
    (is (= {:type "application/x-scittle"
            :src "article_controls.cljs"}
           (second (first scripts))))
    (is (str/includes? (second (first styles))
                       ".article-equation-title-icon"))))

(deftest every-authored-article-installs-controls-once
  (doseq [file (authored-article-files)
          :let [source (slurp file)]]
    (testing (.getPath file)
      (is (= 1 (occurrence-count #"\(controls/install\)" source)))
      (is (str/includes?
           source
           "[language-learning.vocabulary-estimation.article-controls :as controls]"))
      (is (not (str/includes? source "math/styles")))
      (is (not (str/includes? source "math/global-controls"))))))

(deftest namespace-metadata-no-longer-injects-root-controls
  (let [metadata (slurp (io/file "site" "language_learning"
                                 "vocabulary_estimation" "_metadata.yml"))]
    (is (not (str/includes? metadata
                            "vocabulary_article_controls_style.html")))
    (is (not (str/includes? metadata
                            "vocabulary_article_controls_script.html")))
    (is (not (.exists (io/file "site"
                               "vocabulary_article_controls_style.html"))))
    (is (not (.exists (io/file "site"
                               "vocabulary_article_controls_script.html"))))))

(deftest controls-contract-remains-represented
  (let [installer (slurp (io/file authored-root "article_controls.clj"))
        runtime (slurp (io/file authored-root "article_controls.cljs"))
        style (slurp (io/file "resources" "language_learning"
                              "vocabulary_estimation" "article_controls.css"))]
    (doseq [contract ["article-explanations-toggle"
                      "article-code-toggle"
                      "article-equations-toggle"
                      "aria-labelledby"
                      "aria-describedby"
                      "aria-live"
                      "application/x-scittle"
                      "article_controls.cljs"]]
      (is (str/includes? installer contract)
          (str "Missing installer contract: " contract)))
    (doseq [contract ["civitas.vocabulary-articles.code-open"
                      "civitas.vocabulary-articles.equations-open"
                      "localStorage"
                      "details.article-code-detail"
                      "requestAnimationFrame"
                      "DOMContentLoaded"]]
      (is (str/includes? runtime contract)
          (str "Missing runtime contract: " contract)))
    (doseq [icon-class ["article-equation-title-icon"
                        "article-code-title-icon"
                        "article-math-help-title-icon"
                        "article-code-help-title-icon"
                        "article-text-help-title-icon"]]
      (is (str/includes? runtime icon-class))
      (is (str/includes? style icon-class)))))

(deftest article-only-helpers-are-hidden-from-published-output
  (let [source (slurp (io/file authored-root "beta_binomial_first_pass.clj"))]
    (is (re-find #"\^\{:kindly/hide-code true\s+:kindly/kind :kind/hidden\}\s+\(defn- resource-text" source)
        "The CSS resource helper code and returned Var must not render as article content")))

(deftest merged-article-code-uses-namespace-controls
  (let [source (slurp (io/file authored-root
                               "managing_brilliant_but_uneven_minds.clj"))]
    (is (str/includes? source
                       "[language-learning.vocabulary-estimation.math-explanations :as math]"))
    (is (re-find #"(?s)\(math/code-detail.*?\[:pre\.mw-code" source)
        "Article 0's clone commands must participate in the namespace code controls")))

(deftest math-explanations-retains-construction-only
  (let [source (slurp (io/file authored-root "math_explanations.clj"))]
    (is (not (str/includes? source "(defn styles")))
    (is (not (str/includes? source "(defn global-controls")))
    (is (str/includes? source "(defn equation-code-detail"))
    (is (str/includes? source "(defn terminology"))))

(deftest v2-article-compares-latent-evidence-and-both-models
  (let [article (slurp (io/file authored-root
                                "pair_frequency_logistic_v2_article.clj"))
        interactive (slurp (io/file authored-root
                                    "pair_frequency_logistic_v2_interactive.cljs"))]
    (is (str/includes? article "pair-frequency-model-scenarios"))
    (doseq [label ["Latent knowledge" "Observed evidence" "Model predictions"
                   "Smooth frequency signal" "Non-logistic mixture"
                   "10% false negatives"]]
      (is (str/includes? interactive label)))
    (is (str/includes? interactive "v1-step-path"))
    (is (str/includes? interactive ":v2-probability"))
    (is (str/includes? interactive
                       "(mount! \"pair-frequency-model-scenarios\""))))
