^{:kindly/hide-code true
  :clay             {:title  "Convergence of Random Events"
                     :quarto {:author   [:samumbach :timothypratley]
                              :type     :post
                              :date     "2025-06-25"
                              :category :clojure
                              :tags     [:clojure.math]}}}
(ns central-limit-theorem-convergence)

;; Life is full of random events.

;; We learn that multiple coin flips are "independent events" -- no matter whether the past flip was heads or tails, the next flip is 50/50.
;; (So why do they show the last few results at the routlette table? Hint: Don't play routlette.)
;; We learn that about half of babies are male and half female, so chances are 50/50 that your new little sibling will be a boy or a girl.

;; I found the answer to "Of my 8 children, what are the chances that 4 are girls and 4 are boys?" counterintuitive.
;; The central limit theorem is crucial to intuition around this question.

;; When I initially encountered the Monte Hall problem, the correct answer wasn't obvious or intuitive, but the mathemetical explanation is surprisingly understandable. We'll try here to make the central limit theorem more understandable as well.


;; Start with a single random event -- value drawn from [0.0, 1.0)
(rand)

;; One way to combine random events is to take the average:
(defn avg [nums]
  (/ (reduce + nums) (count nums)))

(avg [0.0 1.0])

;; Let's try taking the average of several events together:

(avg [(rand) (rand)])
(avg [(rand) (rand) (rand)])

;; This is getting repetitive. We can make the computer repeat for us:

(avg (repeatedly 3 rand))


;; The more events that you average, the closer the result comes to 0.5:

(avg (repeatedly 30 rand))
(avg (repeatedly 300 rand))


;; Let's try taking several events together:

(defn event []
  (rand))

(event)

(defn combined-event [number-of-events]
  (avg (repeatedly number-of-events event)))

(combined-event 1)
(combined-event 2)
(combined-event 5)


;; Let's look at a series of multiple of these combined event

(repeatedly 5 #(combined-event 2))

(repeatedly 5 #(combined-event 5))

(repeatedly 5 #(combined-event 10))


;; As we combine a larger number of events, the values cluster more closely to the middle of the original distribution.

;; And regardless of the shape of the original event distribution, the result of combining more and more events
;; will approach the normal distribution -- it's a unique function toward which these combinations always
;; converge.

;; This is true for both continuous variables (like `(rand)`) or discrete variables (like dice `(rand-nth [1 2 3 4 5 6])`),
;; and it's true even for oddly shaped distributions. When you combine enough of them, they take on the character of the bell-shaped curve.

;; Learn More at [3Blue1Brown - But what is the Central Limit Theorem?](https://www.youtube.com/watch?v=zeJD6dqJ5lo)
