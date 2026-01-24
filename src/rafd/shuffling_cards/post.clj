^{:kindly/hide-code true
  :clay {:title  "Analyzing Card Shuffles "
         :quarto {:author   :rafd
                  :description "Which card shuffling method is the most effective? Monte Carlo simulation and data visualization of different real-world shuffling algorithms."
                  :draft true
                  :type     :post
                  :date     "2026-01-23"
                  :category :data-visualization
                  :tags     [:simulation :stats :datavis]}}}
(ns rafd.shuffling-cards.post
  (:require
   [scicloj.kindly.v4.kind :as kind]))


;; I have an existing "notebook" here:
;; https://shuffling.rafal.dittwald.com/
;; TODO to convert to civitas

;; Notes from a previous meetup talk I gave:

;; Adventure down the rabbit hole of shuffling
;; A while back
;;     was playing a card game w/ with my friends,
;;          I noticed everyone shuffled cards differently
;;
;;    Think for a moment: how do you shuffle? How many times do you shuffle?
;;      Interleave
;;      Overhand
;;      Milk
;;      Pull
;;
;;   Asked myself:
;;      Are some ways of shuffling “better” than others?
;;               What is “better”?
;;                     Better end result? (more random)
;;                          What is “more random”?
;;                     More efficient (time wise)
;;
;; We can treat real-world card shuffling like a computer algorithm:
;;
;;        list -> list, with the output being in a “random order”
;;
;;       “random” :any arrangement of cards is equally likely”
;;
;;
;;
;;
;;
;;
;;
;;
;;
;;
;;    When analysing different algorithms, we could:
;; Try to prove that it is correct  (ie. “provably-correct”)
;; Treat it like a black box, and analyze the results
;;
;; For (a),
;;     Want some measure of what is “a good shuffle”?
;;     Need a mathematical model
;;     Do some fancy math to prove
;;
;;    Ex. riffle shuffle
;;            => 7x for 52 cards
;;            => 6x for 52 cards
;;
;; BUT, are the measures any good?
;;
;;    Turns out...
;;
;;     New Age Solitaire Test
;;            Defined a solitaire game that is sensitive to interwoven rising sequences,
;;            b/c of mechanics of riffle shuffle, 7x: game is won 80%, but should be 50%
;;
;;     Birthday Test
;;           => 11 or 12 riffle shuffles
;;
;;      Often, Single measures are poor
;;            (mathematicians like them b/c it’s a single thing to prove)
;;             ex. an “average” doesn’t tell you the whole story of the distribution
;;
;;
;; So how can we compare lots of different shuffles?
;;
;;
;; I ran across a visualization by Mike Bostock (creator of D3 visualization library)
;;         Explores bad implementations of shuffling algorithms
;;
;; And so I figured…
;;      we could do the same thing for physical shuffling
;;
;;
;; So, here’s the idea:
;;    Let’s explore real card shuffling by…
;;       using code to implement a shuffle + monte-carlo simulation + data visualization
;;
;;       clojure + reagent  make for a good “notebook” environment
;;
;;
;;
;;
;; Let’s understand the visualization
;;     Probability bias matrix   (similar to confusion matrix)
;;
;;     identity
;;     overhand
;;     milk
;;     riffle
;;     Fisher-yates
;;
;;
;; Only in single-case ---- lets Monte Carlo!
;;    (explain legend)
;;
;;
;;
;; What about multiple shuffles after the other?
;;
;;
;; OVERHAND
;;
;;   (explain set up)
;;          shuffles are repeated “matrix” transformations
;;
;;
;;    How many to get sufficiently random? Math guys say: 5000+
;;
;;
;;
;; SLOPPY MILK
;;     After 4x seems to look good
;;         BUT…
;;
;;     Notice: last card does not move
;;
;;
;; PERFECT RIFFLE
;;    “Perfect” => deterministic, no randomness
;;    → 8 times cycles
;;
;;
;; NEAR-PERFECT RIFFLE
;;  ‘ + source of randomness: which one on top
;;
;;    hard to see, but has consecutive ascending runs
;;
;; SLOPPY RIFFLE
;;    ‘ + source of randomness
;;         where cut
;;         1 or 2 interleaves
;;
;;    how many interleaves?
;;
;;       math said: 6 or 7
;;       ....
;;       Confirmed!  (with no fancy math)
;;
;;
;;
;; COMBINATIONS????
;;    Overhand + RIFFLE + MILK
;;
;;
;; Lot’s more to explore here, but out of time
;;
;; BONUS:
;;    Sometimes down the rabbit hole… discover ART!
;;
;;
;;
;; Learnings:
;;
;;    Simulation + visualization
;;          Quick & easy way to get insight
;;
;;     Shuffles have many different failure modes
;;
;;    Overhand shuffle is terrible
;;
;;    Riffle shuffle is better
;;        52 cards: at least 6 times
;;        N cards: 1.5 log2 N
;;        but, streaky
;;
;;  “Perfect shuffles”: bad
;;        “Sloppy”: better
;;
;;         # of times needed “quality of randomness needed” => depends on game
;;
;;     Alternative:
;;       Pluck from middle
;;       Smoosh  (not covered)
;;
;;
;;     Maybe a combination? For you to try
;;
;;
