^{:kindly/hide-code true
  :clay             {:title  "Convergence to Normal Distribution, independent of original distribution"
                     :quarto {:author   [:samumbach]
                              :type     :post
                              :date     "2025-06-27"
                              :category :clojure
                              :tags     [:clojure.math]}}}
(ns central-limit-theorem-different-distributions
  (:require [scicloj.tableplot.v1.plotly :as plotly]
            [tablecloth.api :as tc]))

;; We mentioned [last time](central_limit_theorem_convergence.html) that the result of combining more and more
;; events will approach the normal distribution, regardless of the shape of the original event distribution.
;; Let's try to demonstrate that visually.

;; Our previous definition of a random event is an example of a uniform distribution:

(defn event []
  (rand))

(defn event-sample-dataset [event-fn sample-count]
  {:index       (range sample-count)
   :event-value (repeatedly sample-count event-fn)})

(def uniform-ds (event-sample-dataset event 100000))

(defn histogram [ds]
  (-> ds
      (tc/dataset)
      (plotly/layer-histogram
       {:=x :event-value
        :=histnorm "count"
        :=histogram-nbins 40})
      (plotly/layer-point)))

(histogram uniform-ds)

;; If we combine several of these distributions, watch the shape of the distribution:

(defn avg [nums]
  (/ (reduce + nums) (count nums)))

(defn combined-event [number-of-events]
  (avg (repeatedly number-of-events event)))

(histogram (event-sample-dataset #(combined-event 2) 100000))

(histogram (event-sample-dataset #(combined-event 5) 100000))

(histogram (event-sample-dataset #(combined-event 20) 100000))

;; Let's try this again with a different shape of distribution:

(defn triangle-wave [x]
  (-> x (- 0.5) (Math/abs) (* 4.0)))

(-> (let [xs (range 0.0 1.01 0.01)
          ys (mapv triangle-wave xs)]
      (tc/dataset {:x xs :y ys}))
    (plotly/layer-point
     {:=x :x
      :=y :y}))

;; Generating samples from this distribution is more complicated than I initially
;; expected. This warrants a follow-up, but for now I'll just link to my source
;; for this method: [_Urban Operations Research_ by Richard C. Larson and Amedeo R. Odoni, Section 7.1.3 Generating Samples from Probability Distributions](https://web.mit.edu/urban_or_book/www/book/chapter7/7.1.3.html)
;; (see "The rejection method").

(defn sample-from-function [f x-min x-max y-min y-max]
  (loop []
    (let [x (+ x-min (* (rand) (- x-max x-min)))
          y (+ y-min (* (rand) (- y-max y-min)))]
      (if (<= y (f x))
        x
        (recur)))))

(defn event []
  (sample-from-function triangle-wave 0.0 1.0 0.0 2.0))

(def triangle-wave-ds (event-sample-dataset event 100000))

(histogram triangle-wave-ds)

;; Let's combine several of these distributions:

(histogram (event-sample-dataset #(combined-event 2) 100000))

(histogram (event-sample-dataset #(combined-event 5) 100000))

(histogram (event-sample-dataset #(combined-event 20) 100000))

;; I find these visuals surprisingly powerful because you can see the original
;; distribution "morph" into this characteristic shape.

;; The normal distribution holds a unique place in mathematics and in the world itself:
;; whenever you combine multiple independent and identically-distributed events,
;; the result will converge to the normal distribution as the number of
;; combined events increases.
