^{:kindly/hide-code true
  :clay             {:title  "interactive"
                     :quarto {:author   :kloimhardt
                              :type     :draft
                              :description "Demo of reagent and tex"
                              :sidebar  "emmy-fdg"
                              :date     "2026-01-11"
                              :category :clay
                              :tags     [:browser :reagent]}}}

(ns mentat-collective.emmy.emmy_interactive
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define-1 let-scheme lambda]]))

^:kindly/hide-code
(def render-mode {:browser true})

^:kindly/hide-code
(kind/hiccup
  [:div
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.28-59/dist/scittle.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.28-59/dist/scittle.emmy.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.28-59/dist/scittle.cljs-ajax.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/react-dom@18/umd/react-dom.production.min.js", :crossorigin ""}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.28-59/dist/scittle.reagent.js"}]
   [:script {:type "application/x-scittle" :src "scheme.cljc"}]])

^:kindly/hide-code
(defmacro define [& b]
  (list 'do
        (cons 'mentat-collective.emmy.scheme/define b)
        (list 'kind/scittle (list 'quote (cons 'define b)))))

(define simplify identity)
(define ->infix identity)
(define down vector)

^:kindly/hide-code
(kind/scittle
  '(defn show-expression [e]
     (simplify e)))

^:kindly/hide-code
(kind/scittle
  '(defn show-tex-expression [e]
     (->infix (simplify e))))

^:kindly/hide-code
(kind/scittle
  '(defn show-tex [e]
     (->infix e)))

^:kindly/hide-code
(define (eq-transformation f)
  (lambda (tuple)
          (apply down (map f tuple))))

^:kindly/hide-code
(kind/scittle
  '(defn show-eq [tuple]
     (->infix (simplify (down (first tuple) '= (second tuple))))))

^:kindly/hide-code
(def tex (comp kind/tex emmy.expression.render/->TeX))

^:kindly/hide-code
(def tex-simp (comp tex simplify))

^:kindly/hide-code
(defn fn-show-eq [tuple]
  (tex-simp (down (first tuple) '= (second tuple))))

^:kindly/hide-code
(define show-exp (comp str simplify))

^:kindly/hide-code
(defn reag-comp [b]
  (let [server-erg (show-exp (eval b))]
    (list 'kind/reagent
          [:div (list 'quote
                      (list 'let ['a (list 'show-exp b)]
                            [:div
                             (when (:browser render-mode)
                               [:div
                                [:tt 'a]
                                [:p (list 'str (list '= server-erg 'a))]])
                             [:tt server-erg]]))])))

^:kindly/hide-code
(defmacro show-expression [e]
  (if (:browser render-mode)
    (reag-comp e)
    (list 'simplify e)
    ))

^:kindly/hide-code
(defmacro show-tex-expression [e]
  (if (:browser render-mode)
    (reag-comp e)
    (list 'tex-simp e)))

^:kindly/hide-code
(defmacro show-tex [e]
  (if (:browser render-mode)
    (reag-comp e)
    (list 'tex e)))

^:kindly/hide-code
(defmacro show-eq [e]
  (if (:browser render-mode)
    (reag-comp e)
    (list 'fn-show-eq e)))

(kind/tex "x^1")

(kind/hiccup [:div
              [:div#kt2 "2"]
              [:div#kt3 "3"]
              [:div#kt4 "4"]
              [:script "katex.render(\"x^2\", document.getElementById(\"kt2\"))"]])

(kind/scittle
  '(.render js/katex "x^3" (.getElementById js/document "kt3")))

(kind/scittle
  '(def *click-count (reagent.core/atom 4)))

(kind/scittle
  '(.render js/katex (str "x^" @*click-count) (.getElementById js/document "kt4")))

(kind/reagent
  ['(fn []
     [:div
      "The atom " [:code "*click-count"] " has value: "
      @*click-count ". "
      [:input {:type     "button" :value "Click me!"
               :on-click #(swap! *click-count inc)}]])])

(show-tex "x^2")
