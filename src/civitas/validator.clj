^{:clay {:quarto {:draft true}}}
(ns civitas.validator
  (:require [clojure.spec.alpha :as s]))

(s/def ::level #{0 1 2 3})
(s/def ::format #{:reference :interactive-book :video :library-docs :problem-set :community})
(s/def ::topic #{:core :web :data-sci :concurrency :tooling :testing :performance})

(s/def ::resource
  (s/keys :req-un [::id ::title ::url ::format ::topics ::level]
          :opt-un [::depends-on ::description]))

(defn validate-db [db]
  (s/valid? ::resources (:resources db)))
