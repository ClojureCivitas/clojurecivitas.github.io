^{:kindly/hide-code true
  :clay             {:title  "Five Pirates Treasure Splitting"
                     :quarto {:type     :post
                              :author   [:timothypratley]
                              :date     "2025-06-18"
                              :description "Sharing ideas and pirate treasure with ClojureCivitas."
                              :category :math
                              :tags     [:probability :stats]
                              :keywords [:quantquestions]}}}
(ns math.stats.quantquestions.what-are-the-odds.five-pirates-treasure)

;; Welcome back code champs, number ninjas, and data divers to the second episode of
;; “What are the Odds?” where we answer life’s important questions.

^:kind/video ^:kindly/hide-code
{:youtube-id   "lceazLPcSZg"
 :iframe-width "100%"}

;; I was out treasure hunting with four friends the other day,
;; and we found 100 gold coins.
;; To split the booty, I came up with a genius plan.
;; We take turns proposing how to divide the gold,
;; and if half the crew agrees, we go with it.
;; But if not I get nothing and the next person makes a proposal and so on.
;; To my surprise, the others loved the idea!
;; “A democracy of pirates!” said one.
;; “A fair and logical system!” said another.
;; Even our parrot nodded in approval.
;; Can you guess how many coins I walked away with?

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

;; ![Scallywag Pirate](five_pirates_treasure.jpg)

;; Well, it got me thinking,
;; what if sharing your ideas was as easy as tricking treasure hunters?
;; No sword fights.
;; No mutiny.
;; Just a fork, some code, a few comments, and a pull request.
;; Let’s try it out.
;; First I fork ClojureCivitas,
;; Open the project, add a namespace, write some code, add some comments.
;; Commit. Push. Pull request.
;; Once it is merged, it is automatically published to the website.
;; Doesn’t that look nice?
;; I can even track how many people are reading my idea in these [public analytics](https://clojurecivitas.goatcounter.com/).
;; If I want a quick preview, I use Clay to render the code and results to HTML.
;; I hope you have an idea to share with me,
;; Why not start a namespace and publish it this way?

;; Until next time, may your treasure split be fair and your adventures interesting.
