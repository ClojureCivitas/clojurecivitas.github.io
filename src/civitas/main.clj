(ns civitas.main
  (:require [civitas.make :as make])
  (:gen-class))

(defn -main
  [& args]
  (make/make-all)
  (System/exit 0))

(comment
  (make/make-all))
