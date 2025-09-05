^{:kindly/hide-code true
  :clay             {:title  "Mermaid.js"
                     :format [:quarto :html]
                     :quarto {:author      :emilbengtsson
                              :description "Explain complex concepts with mermaid.js"
                              :type        :post
                              :date        "2025-09-05"
                              :image       "mermaid.png"
                              :category    :clay
                              :tags        [:clay :workflow]}}}
(ns scicloj.clay.mermaid
  (:require [scicloj.kindly.v4.kind :as kind]))


;; ## A picture is worth a thousand words

(kind/mermaid "flowchart LR
 Concept --> easy{Easy to understand?}
 easy -->|Yes| Understanding
 easy -->|No| Diagram
 easy -->|No| wall(Wall of text)
 Diagram --> Understanding
 wall -->|Didn't get it| Concept
 wall -->|Finally| Understanding")

;;
;; With [mermaid.js](https://mermaid.js.org/) you can now use words to generate
;; a picture. One of the trickier aspects in explaining complex ideas/concepts,
;; is the infliction point where words start to pile up towards dizzying heights
;; and you lose sight of what what you wanted to communicate.
