^{:kindly/hide-code true
  :clay             {:title  "Proximal Policy Optimization with Clojure and Pytorch"
                     :external-requirements []
                     :quarto {:author   [:janwedekind]
                              :draft    true
                              :description "A Clojure port of XinJingHao's PPO implementation using Pytorch and Quil"
                              :image    "pendulum.png"
                              :type     :post
                              :date     "2026-04-18"
                              :category :ml
                              :tags     [:physics :machine-learning :optimization :ppo :control]}}}

(ns ppo.main
    (:require [libpython-clj2.require :refer (require-python)]))

(require-python '[torch :as torch])

;; Recently I started to look into the problem of reentry trajectory planning in the context of developing the [sfsim](https://store.steampowered.com/app/3687560/sfsim/) space flight simulator.
;; I had looked into reinforcement learning before and tried out Q-learning using the [lunar lander reference environment of OpenAI's gym library](https://gymnasium.farama.org/environments/box2d/lunar_lander/).
;; However I had stability issues.
;; The algorithm would learn a strategy and then suddenly diverge again.
;;
;; More recently (2017) the Proximal Policy Optimization (PPO) algorithm was published and it has gained in popularity.
;; PPO is inspired by Trust Region Policy Optimization (TRPO) but is much easier to implement.
;; The [Stable Baselines3](https://github.com/DLR-RM/stable-baselines3) Python library has a implementation of PPO, TRPO, and other reinforcement learning algorithms.
;; However I found [XinJingHao's PPO implementation](https://github.com/XinJingHao/PPO-Continuous-Pytorch/) which I found easier to follow.
;;
;; ![pendulum](pendulum.png)
