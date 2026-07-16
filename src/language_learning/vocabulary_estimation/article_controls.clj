(ns language-learning.vocabulary-estimation.article-controls
  (:require [clojure.java.io :as io]
            [scicloj.kindly.v4.kind :as kind]))

(defn- resource-text [filename]
  (slurp
   (io/resource
    (str "language_learning/vocabulary_estimation/" filename))))

(defn- toolbar []
  [:div.article-explanations-toolbar
   {:role "region" :aria-labelledby "article-explanations-heading"}
   [:p
    [:strong#article-explanations-heading "Reading controls"]
    [:span#article-explanations-description.article-explanations-description
     "Open or close help, code, and equations. Choices for code and equations apply across this article namespace."]
    [:span#article-explanations-status.article-explanations-status
     {:aria-live "polite"}
     "Help is hidden."]]
   [:div#article-help-action.article-reading-action
    [:button#article-explanations-toggle.article-explanations-toggle
     {:type "button"
      :aria-pressed "false"
      :aria-describedby "article-explanations-description article-explanations-status"}
     "Show all help"]]
   [:div#article-code-action.article-reading-action
    [:button#article-code-toggle.article-code-toggle
     {:type "button"
      :aria-pressed "false"
      :aria-describedby "article-explanations-description article-code-status"}
     "Show all code"]
    [:span#article-code-status.article-code-status
     {:aria-live "polite"}
     "Code is hidden."]]
   [:div#article-equations-action.article-reading-action
    [:button#article-equations-toggle.article-equations-toggle
     {:type "button"
      :aria-pressed "true"
      :aria-describedby "article-explanations-description article-equations-status"}
     "Hide all equations"]
    [:span#article-equations-status.article-equations-status
     {:aria-live "polite"}
     "Equations are shown."]]])

(defn install
  "Install the controls stylesheet, explicit toolbar, and Scittle runtime."
  []
  (kind/hiccup
   [:div.article-controls-installation
    [:style (resource-text "article_controls.css")]
    (toolbar)
    [:script {:type "application/x-scittle"
              :src "article_controls.cljs"}]]))
