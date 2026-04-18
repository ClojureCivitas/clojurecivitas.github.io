^{:kindly/hide-code true
  :clay             {:title  "Proximal Policy Optimization with Clojure and Pytorch"
                     :external-requirements []
                     :quarto {:author   [:janwedekind]
                              :draft    true
                              :description "A Clojure port of Jinghao's PPO implementation using Pytorch and Quil"
                              :image    "pendulum.png"
                              :type     :post
                              :date     "2026-04-18"
                              :category :ml
                              :tags     [:physics :machine-learning :optimization :ppo :control]}}}

(ns ppo.main
    (:require [libpython-clj2.require :refer (require-python)]))


(require-python '[torch :as torch])


;; ![pendulum](pendulum.png)
