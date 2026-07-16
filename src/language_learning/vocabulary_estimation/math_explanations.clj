(ns language-learning.vocabulary-estimation.math-explanations
  (:require [clojure.string :as str]
            [scicloj.kindly.v4.kind :as kind]))

(defn- code-detail-node [id purpose classes attributes body]
  (let [summary-id (str id "--summary")
        body-id (str id "--body")]
    [:details
     (merge {:id id
             :class (str/join
                     " " (into ["article-code-detail"] classes))}
            attributes)
     [:summary
      {:id summary-id :aria-controls body-id}
      (str "Code detail: " purpose)]
     [:div.article-code-detail-body
      {:id body-id :role "region" :aria-labelledby summary-id}
      body]]))

(declare code-symbol-registry)

(defn code-detail
  "Render one closed, inline implementation detail with an independent native
  disclosure control. Body is arbitrary Hiccup, so callers may mix prose,
  preformatted code, and a source link. Supplying :symbols adds optional
  reader-facing help beside matching code identifiers."
  ([id purpose body]
   (kind/hiccup (code-detail-node id purpose [] {} body)))
  ([id purpose {:keys [symbols]} body]
   {:pre [(seq symbols)
          (every? (fn [[symbol definition]]
                    (and (seq symbol) (seq definition)))
                  symbols)]}
   (kind/hiccup
    (code-detail-node
     id purpose [] {}
     (into [:div (code-symbol-registry id symbols)]
           (if (= :div (first body)) (rest body) [body]))))))

(defn- code-symbol-registry [id symbols]
  (into
   [:div.code-symbol-explanation-registry
    {:data-code-symbol-group id}]
   (map-indexed
    (fn [index [symbol definition]]
      [:details.article-explanation.code-symbol-explanation
       {:id (str id "--code-symbol-" (inc index))
        :data-anchor-term symbol
        :data-help-label symbol}
       [:summary [:code.article-explanation-term symbol]]
       [:div.article-explanation-body [:p definition]]])
    symbols)))

(defn equation-code-detail
  "Render the required closed code disclosure for one display equation.

  Provenance must be either {:kind :source :label ... :href ...}, for code
  copied from the repository, or {:kind :explanation :label ...}, for a direct
  code explanation using the implementation's symbol names. Both forms require
  :symbols pairs so code identifiers receive the same optional help as equation
  symbols."
  [id purpose {:keys [kind label href symbols]} body]
  {:pre [(#{:source :explanation} kind)
         (seq label)
         (or (= :explanation kind) (seq href))
         (seq symbols)
         (every? (fn [[symbol definition]]
                   (and (seq symbol) (seq definition)))
                 symbols)]}
  (let [source? (= :source kind)
        provenance-node
        [:p.article-code-provenance
         [:strong (if source? "Source: " "Code explanation: ")]
         (if source?
           [:a {:href href} label]
           label)]]
    (kind/hiccup
     (code-detail-node
      id purpose ["article-equation-code"]
      {:data-equation-code "true"
       :data-code-provenance (name kind)}
      (into [:div provenance-node (code-symbol-registry id symbols)]
            (if (= :div (first body)) (rest body) [body]))))))

(defn- math-item
  [id term definition]
  [:details.article-explanation.math-explanation
   {:id id :data-help-label term}
   [:summary
    [:code.article-explanation-term term]]
   [:div.article-explanation-body
    [:p definition]]])

(defn explanation
  ([id title terms]
   (explanation id title terms nil))
  ([id title terms note]
   (kind/hiccup
    (into
     [:div.article-explanation-registry.math-explanation-registry
      {:data-explanation-group id :data-explanation-title title}]
     (concat
      (map-indexed (fn [index [term definition]]
                     (math-item (str id "--term-" (inc index))
                                term definition))
                   terms)
      (when note
        [[:details.article-explanation.math-explanation.equation-context
          {:id (str id "--context") :data-help-label "Equation context"}
          [:summary
           [:span.article-explanation-term "About this equation"]]
          [:div.article-explanation-body
           [:p note]]]]))))))

(defn terminology
  [id category _label title terms]
  (kind/hiccup
   (into
    [:div.article-explanation-registry.term-explanation-registry
     {:data-explanation-group id :data-explanation-title title}]
    (map-indexed
     (fn [index [term definition anchor-term]]
       [:details
        {:id (str id "--term-" (inc index))
         :class (str "article-explanation term-explanation " (name category))
         :data-anchor-term (or anchor-term term)
         :data-help-label term}
        [:summary
         [:span.article-explanation-term term]]
        [:div.article-explanation-body
         [:p definition]]])
     terms))))
