^{:kindly/hide-code true
  :clay             {:title  "Five Pirates Treasure Splitting"
                     :quarto {:type     :post
                              :author   [:timothypratley]
                              :date     "2025-06-18"
                              :category :math
                              :tags     [:probability :stats]
                              :keywords [:quantquestions]}}}
(ns math.stats.quantquestions.what-are-the-odds.five-pirates-treasure)

(def pirates
  ["Green Boots" "Red Rackham" "Blue Thunder" "Black Beard" "Tim"])

;; With only one pirate, they get all the booty
;; * Green Boots: 100

;; With 2 pirates, whoever proposes the split gets all the booty
;; * Red Rackham 100
;; * Green Boots 0

;; With 3 pirates, the pirate who would miss out in the next round is incentivised by 1 coin
;; * Blue Thunder 99
;; * Red Rackham  0
;; * Green Boots  1

;; With 4 pirates, 1 vote needed, the pirate who would miss out is incentivised by 1 coin
;; * Black Beard  99
;; * Blue Thunder 0
;; * Red Rackham  1
;; * Green Boots  0

;; With 5 pirates, 2 votes needed, the 2 pirates who would miss out in the next round get a coin each
;; * Tim          98
;; * Black Beard  0
;; * Blue Thunder 1
;; * Red Rackham  0
;; * Green Boots  1

;; Probably you can spot a pattern emerging here...

(defn split [coins pirates]
  (let [n (count pirates)]
    (-> (mapv vector
              (reverse pirates)
              (cycle (if (odd? n) [1 0] [0 1])))
        (assoc-in [0 1] (- coins (/ (if (odd? n) (dec n) n) 2))))))

(split 100 pirates)

^:kind/table
(split 100 pirates)
