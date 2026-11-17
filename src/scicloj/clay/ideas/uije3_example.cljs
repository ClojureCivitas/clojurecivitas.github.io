(ns scicloj.clay.ideas.uije3-example
  (:require [scicloj.clay.ideas.uije3 :as uj]))

(defn greet-ui [{:keys [a b] :as state}]
  [:col {:state state}
   [:strong (str "Hello: " a ", " b)]
   [:row
    [:input {:name :a}]
    [:input {:name :b}]
    [:button {:name :c} "Swap"]]])

(defn greet-event [{:keys [a b click] :as state}]
  (cond-> state
    (= click :c) (assoc :a b :b a)))

(def greet (comp greet-ui greet-event))

(uj/uije #'greet)
