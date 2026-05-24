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
              "fc3" (nn/Linear n-hidden 1)})
           nil))
     "forward"
     (py/make-instance-fn
       (fn [self x]
           (let [x (py. self fc1 x)
                 x (F/sigmoid x)
                 x (py. self fc2 x)
                 x (F/sigmoid x)
                 x (py. self fc3 x)]
             x)))}))

(defmacro without-gradient
  [& body]
  `(let [no-grad# (torch/no_grad)]
     (try
       (py. no-grad# ~'__enter__)
       ~@body
       (finally
         (py. no-grad# ~'__exit__ nil nil nil)))))

(def model (ParabolaNet 20))
(def n 1000)
(def features (torch/sub (torch/mul (torch/rand [n 1]) 6) 3))
(def labels (torch/mul features features))

(def dataset (data/TensorDataset features labels))

(def train-size (int (* 0.8 n)))
(def dev-size (int (* 0.1 n)))
(def test-size (- n train-size dev-size))

(def splits (data/random_split dataset [train-size dev-size test-size]))
(def train-ds (nth splits 0))
(def dev-ds (nth splits 1))
(def test-ds (nth splits 2))

(def data-loader (data/DataLoader train-ds :batch_size 16 :shuffle true))

(def criterion (nn/MSELoss))
(def optimizer (optim/SGD (py. model "parameters") :lr 0.01 :weight_decay 0.0))

(py. model train)
(doseq [epoch (range 1000)]
       (doseq [[features labels] data-loader]
              (py. optimizer zero_grad)
              (let [prediction (py. model __call__ features)
                    loss       (py. criterion __call__ prediction labels)]
                (py. loss backward)
                (py. optimizer step)))
       ; (when (= (mod (inc epoch) 100) 0)
       ;   (println (str "epoch: " (inc epoch) " loss: " (py. loss item))))
       )


(without-gradient
  (let [x  (range -3.0 3.01 0.01)
        y  (map (fn [x] (py. (first (py. model __call__ (torch/tensor [x]))) item)) x)
        ds (tc/dataset {:x x :y y})]
   (-> ds
      (plotly/base {:=title "Model" :=mode "lines"})
      (plotly/layer-point {:=x :x :=y :y}))))
