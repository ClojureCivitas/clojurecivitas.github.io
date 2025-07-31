^{:kindly/hide-code true
  :clay             {:title  "Presenting your namespace"
                     :format [:quarto :revealjs]
                     :quarto {:author      :timothypratley
                              :description "Did you know your blog post can be a slideshow?"
                              :type        :post
                              :date        "2025-07-30"
                              :image       "presenting_presentations.jpg"
                              :category    :clay
                              :tags        [:clay :workflow]}}}
(ns scicloj.clay.presenting-presentations
  (:require [scicloj.kindly.v4.kind :as kind]
            [scicloj.tableplot.v1.plotly :as plotly]
            [scicloj.metamorph.ml.rdatasets :as rdatasets]))

^:kindly/hide-code
(kind/hiccup
  [:div {:style "text-align: center; font-size: 2.5em;"}
   "When sharing ideas" [:br]
   "💡"
   [:br]
   "less is more"])

;; ## How to make a slideshow

;; Add a little metadata to your namespace 🪄

;; ```clojure
;; ^{:clay {:format [:quarto :revealjs]}
;; (ns my.awesome.idea)
;; ...
;; ```

;; ## The slides

;; Write comments

;; ```clojure
;; ;; Markdown **in my comments**
;; ```

;; Code

(+ 1 2)

;; and headings

;; ```clojure
;; ;; Each heading is a slide
;; ```

;; ## Turn data into HTML

(kind/hiccup
  [:svg {:width "100%"}
   [:circle {:r 40 :cx 50 :cy 50 :fill "lightblue"}]
   [:circle {:r 20 :cx 50 :cy 50 :fill "lightgreen"}]])

;; ## Powerful visualizations

(-> (rdatasets/datasets-iris)
    (plotly/layer-point
      {:=x :sepal-length
       :=y :sepal-width}))

;; ## That's it!

;; Create `src/your/idea.clj` as a Clojure namespace

;; Submit a PR to the [ClojureCivitas repo](https://github.com/ClojureCivitas/clojurecivitas.github.io)

;; Voilà — you've published your presentation.

;; 🌱

;; *Why not turn your next idea into a slideshow?*
