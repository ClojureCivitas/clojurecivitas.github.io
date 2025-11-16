^{:kindly/hide-code true
  :clay {:title "Explanations are value laden" :quarto {:author :com.github/teodorlu :type :post :date "2025-11-15"}}}
(ns civitas.why.explanations-are-value-laden
  (:require [civitas.why.growing-explanations-together :as growing-explanations-together]
            [civitas.why.village.scene :as search-for-meaning]
            [clojure.string :as str]
            [scicloj.kindly.v4.kind :as kind]))

::search-for-meaning/anchor
::growing-explanations-together/anchor

^:kindly/hide-code
(defonce link-root "")

^:kindly/hide-code
(defn infer-html-location [anchor]
  ;; I found this way of doing "checked" links surprisingly pleasant.
  ;; Funnily enough, the *name* of the anchor (which we currently ignore) could (possibly) turn into a hash-link, eg "/civitas/.../scene#process" for ::search-for-meaning/process
  ;; But let's not be distracted by shiny, new ways of linking knowledge right now.
  ;;
  ;; Second problem: while this link works in production, Clay doesn't care about those URLs.
  ;; So we'll just redef the def from our REPL while writing this text.
  (str link-root
       (str/join
        "/"
        (->> (str/split (namespace anchor) #"\.")
             (map #(str/replace % #"-" "_"))))
       ".html"))

^:kindly/hide-code
(comment
  ;; "test".
  (def link-root "https://clojurecivitas.github.io/")
  (infer-html-location ::search-for-meaning/anchor)

  (require '[clojure.java.browse :refer [browse-url]])
  (mapv (comp browse-url infer-html-location)
        [::search-for-meaning/anchor ::growing-explanations-together/anchor])

  )

^:kindly/hide-code
(defn link [anchor linktext]
  [:a {:href (infer-html-location anchor)} linktext])

;; # Explanations are value laden

^:kindly/hide-code
(kind/hiccup [:p "Timothy Pratley recently wrote about "
              (link ::search-for-meaning/anchor
                    [:span "the pursuit of " [:em "meaning"]])
              " on civitas."
              " Before that; I argued that "
              (link ::growing-explanations-together/anchor
                    "Civitas is a great place to grow explanations together")
              "."
              ])

;; These two messages are connected.
;; Today, I explore how.
;;
;; What is whorthwile to explain? Anything? No.
;;
;; I can explain the number of grains of sand on a beach.
;; That explanation is *not interesting* compared to what I *could* be explaning.
;; I would rather explain how numbers with units help you gain clarity.
;; How you can use a drop of science to spice up your world of software development.
;; How a sliver of programming can help you move the world as a designer.
;;
;; I feel zero need to explain how to “nudge” people into making decisions they do not want to make.
;; I feel zero need to add more mess, chaos, advertisement and coercion into an already confusing and coercive world.
;; Why am I bombarded with messaging to change my behavior every place I see?
;; Why does this happen even when I use services I pay for?
;; I dispise this trend.
;;
;; So what?
;; Then what?
;; What should we do instead?
;;
;; Just as the will to explain comes from your values, you can value great explanations.
;; Instead of consuming coercive messaging, consider exploring.
;; Later, share the bits and pieces you discovered.
;; What was your journey?
;; Can you explain what you discovered?
;;
;; I spoke to a friend about great explanations last weekend.
;; “We need to explain together!”, I said.
;; “That is what it means to be human!”
;; “What about art?”, he said.
;; “That's a very good question”.
;;
;; After a brief detour into “art as digestion”, we returned to explanations.
;; Science is the pursuit of better explanations.
;;
;; “But science is scary!”, he retorted at once.
;;
;; No!
;; Let us not fear science.
;; Let us respect science and respect great scientists.
;; But let us forever not fear science.
;; We explain to each other all the time.
;; And those explanations will help others.
;; They will help ourselves.
;;
;; I value great explanations.
;; I value the pursuit of great explanations.
;; And I find it worthwhile to pursue great explanations with others.
;;
;; I may grasp something in a brief moment.
;; By turning that understanding into an explanation, the understanding can outlive the moment.
;; Knowing I can return to that understanding, I can move on.
