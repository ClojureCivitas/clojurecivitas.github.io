^{:kindly/hide-code true
  :clay             {:title  "Machine learning using Clojure, libpython-clj2, and Pytorch"
                     :external-requirements []
                     :quarto {:author   [:janwedekind]
                              :draft    true
                              :description "Machine learning explained using the parabola example"
                              :image    "parabola.png"
                              :type     :post
                              :date     "2026-05-24"
                              :category :ml
                              :tags     [:machine-learning]}}}

(ns mlp.main
    (:require [clojure.math :refer (PI cos sin exp to-radians)]
              [tablecloth.api :as tc]
              [scicloj.tableplot.v1.plotly :as plotly]
              [libpython-clj2.require :refer (require-python)]
              [libpython-clj2.python :refer (py.) :as py]))


(require-python '[torch :as torch]
                '[torch.nn :as nn]
                '[torch.utils.data :as data]
                '[torch.nn.functional :as F]
                '[torch.optim :as optim])

(torch/manual_seed 42)

(def ParabolaNet
  (py/create-class
    "ParabolaNet" [nn/Module]
    {"__init__"
     (py/make-instance-fn
       (fn [self n-hidden]
           (py. nn/Module __init__ self)
           (py/set-attrs!
             self
             {"fc1" (nn/Linear 1 n-hidden)
              "fc2" (nn/Linear n-hidden n-hidden)
              "fc3" (nn/Linear n-hidden n-hidden)
              "fc4" (nn/Linear n-hidden 1)})
           nil))
     "forward"
     (py/make-instance-fn
       (fn [self x]
           (let [x (py. self fc1 x)
                 x (F/sigmoid x)
                 x (py. self fc2 x)
                 x (F/sigmoid x)
                 x (py. self fc3 x)
                 x (F/sigmoid x)
                 x (py. self fc4 x)]
             x)))}))

(defmacro without-gradient
  [& body]
  `(let [no-grad# (torch/no_grad)]
     (try
       (py. no-grad# ~'__enter__)
       ~@body
       (finally
         (py. no-grad# ~'__exit__ nil nil nil)))))

(def extent 6.0)
(def n 32)
(def noise 1.0)
(def features (torch/sub (torch/mul (torch/rand [n 1]) (* 2 extent)) extent))
(def labels (torch/add (torch/mul features features) (torch/mul noise (torch/randn [n 1]))))

(def dataset (data/TensorDataset features labels))

(def train-size (int (* 0.8 n)))
(def dev-size (int (* 0.1 n)))
(def test-size (- n train-size dev-size))

(def splits (data/random_split dataset [train-size dev-size test-size]))
(def train-ds (nth splits 0))
(def dev-ds (nth splits 1))
(def test-ds (nth splits 2))

(def train-data-loader (data/DataLoader train-ds :batch_size 4 :shuffle true))
(def dev-data-loader (data/DataLoader dev-ds :batch_size 4 :shuffle true))

(defn average [numbers]
  (/ (reduce + numbers) (count numbers)))

(defn train-epoch
  [train-data-loader criterion model optimizer]
  (py. model train)
  (for [[features labels] train-data-loader]
       (do
         (py. optimizer zero_grad)
         (let [prediction (py. model __call__ features)
               loss       (py. criterion __call__ prediction labels)]
           (py. loss backward)
           (py. optimizer step)
           (py. loss item)))))

(defn dev-epoch
  [dev-data-loader criterion model]
  (py. model eval)
  (without-gradient
    (for [[features labels] dev-data-loader]
         (let [prediction (py. model __call__ features)
               loss       (py. criterion __call__ prediction labels)]
           (py. loss item)))))

(defn training-run
  [train-data-loader dev-data-loader epochs n-hidden lr]
  (let [model     (ParabolaNet n-hidden)
        optimizer (optim/SGD (py. model "parameters") :lr lr :weight_decay 0.0)
        criterion (nn/MSELoss)]
    (loop [epoch 1 train-losses [] dev-losses []]
          (let [train-loss (average (train-epoch train-data-loader criterion model optimizer))
                dev-loss   (average (dev-epoch dev-data-loader criterion model))]
            (if (< epoch epochs)
              (recur (inc epoch) (conj train-losses train-loss) (conj dev-losses dev-loss))
              {:model model :train-losses (conj train-losses train-loss) :dev-losses (conj dev-losses dev-loss)})))))

(def result (training-run train-data-loader dev-data-loader 5000 200 0.01))

(defn plot-model
  [features labels {:keys [model]}]
  (without-gradient
    (let [x   (range (- extent) (+ extent 0.01) 0.01)
          y   (map (fn [x] (py. (first (py. model __call__ (torch/tensor [x]))) item)) x)
          ds  (tc/dataset {:x x :y y})
          pts (tc/dataset {:x (map first (py/->jvm (py. features tolist)))
                           :y (map first (py/->jvm (py. labels tolist)))})]
      (-> ds
          (plotly/base {:=title "Model"})
          (plotly/layer-point {:=dataset pts :=x :x :=y :y :=name "data"})
          (plotly/layer-line {:=x :x :=y :y :=name "prediction"})))))



(defn smoothing
  [alpha]
  (fn [coll]
      (reductions (fn [prev-avg current] (+ (* alpha prev-avg) (* (- 1 alpha) current)))
                  (first coll)
                  (rest coll))))


(plot-model features labels result)

(defn plot-losses
  [{:keys [train-losses dev-losses]} smoothing-fn]
  (-> (tc/dataset {:x (range 1 (count train-losses)) :y (smoothing-fn train-losses)})
      (plotly/base {:=title "Losses"})
      (plotly/layer-line {:=x :x :=y :y :=name "training loss"})
      (plotly/layer-line {:=dataset (tc/dataset {:x (range 1 (count dev-losses)) :y (smoothing-fn dev-losses)})
                          :=x :x :=y :y :=name "dev loss"})))

(plot-losses result (smoothing 0.99))
