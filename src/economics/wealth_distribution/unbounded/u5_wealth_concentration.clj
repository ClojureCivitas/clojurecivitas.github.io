^:kindly/hide-code
(ns economics.wealth-distribution.unbounded.u5-wealth-concentration
  {:kindly/options {:static true
                    :kinds-that-hide-code #{:kind/var :kind/hiccup2 :kind/fn}}
  :clay {:title          "Unbounded: Wealth Concentration"
          :hide-code      true
          :quarto         {:sidebar "unbounded"
                           :author      [:timothypratley]
                           :description "What is the current distribution of wealth in the United States, and how is it changing."
                           :abstract    "Wealth concentration is accelerating. Policy choices are allowing top end wealth to compound faster than the rest."
                           :date        "2026-09-25"
                           :type        :post
                           :category    :economics
                           :tags        [:tax :economics :growth]}}}
  (:require [babashka.fs :as fs]
            [clojure.math :as m]
            [clojure.string :as str]
            [fastmath.interpolation :as in]
            [scicloj.plotje.api :as pj]
            [tablecloth.api :as tc])
  (:import [java.net URLConnection]
           [java.util Base64]))

;; ## The Current Situation

;; Wealth concentration is the highest it has ever been, and rising.
;; The super-rich are out competing you for the real resources that shape your opportunities.
;; They also buy influence over the people who write the rules.

(def data-dir
  (doto (fs/path "src/economics/wealth_distribution/unbounded/data/")
    (fs/create-dirs)))

(def top-20-share-rti
  "downloaded from https://realtimeinequality.org/"
  (tc/dataset (str (fs/path data-dir "realtimeinequality-wealth-share.csv"))))

^{:kindly/options {:id :fig-top-20-wealth
                   :caption "About 20 people controlled 1.5 percent of all wealth in 2025, versus 0.1 percent in 1980. An astonishing 15x concentration of wealth.[^1]"}}
(-> top-20-share-rti
    (tc/rename-columns {"\"Year\"" "Year"})
    (tc/add-columns {"Wealth Share %" (fn [ds]
                                        (map #(* 100 %) (ds "Real Wealth Share")))})
    (pj/lay-line "Year" "Wealth Share %")
    (pj/options {:title "Share of Total Wealth Held by the Top 0.00001%"}))

(def household-wealth-cuny
  (tc/dataset
   {:percentile [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 20 25 30 35 40 45 50 55 60 65 70 75 80 85 90 95 96 97 98 99 99.9]
    :net-worth  [-76700 -45700 -26690 -14970 -9800 -4620 -900 1 181 450 1000 2502 4000 5180 6500 13500 27000 51400 78850 110030 147200 192700 250320 312560 402500 491600 659000 888600 1242100 1936900 3795600 4694300 6178000 8406000 13615400 62125130]}))

(def individual-wealth-cuny
  "Adult equal-split method.
   While actual household sizes vary,
   dividing by two is a reliable shortcut because U.S. Census data shows the nation averages roughly two adults per household.
   At a macro level, single-person homes and larger families balance each other out,
   making a divisor of two an accurate baseline for the whole population."
  (-> household-wealth-cuny
      (tc/update-columns {:net-worth (partial map #(/ % 2.0))})))

(def individual-wealth-rti
  (tc/dataset (str (fs/path data-dir "realtimeinequality-wealth-data.csv"))))

;; But how does that concentration look across the entire population?
;; Let's visualize the wealth of every individual.

(def billionaires
  "To update this data, use a browser to navigate to https://forbes.com/real-time-billionaires/
   then copy paste to the JavaScript console the code in forbes.js in this directory
   it will save a json file that you can place in the data directory"
  (tc/dataset (fs/file data-dir "billionaires.json")))

(def individual-wealth-rti-latest
  (-> individual-wealth-rti
      (tc/rename-columns {"\"Year\"" "Year"})
      (tc/group-by "Year")
      (tc/groups->seq)
      (last)
      (tc/order-by "Real Wealth Threshold" :desc)))

(def cohort-percentile-upper
  {"Top 0.00001%" 0.00001
   "Top 0.0001%" 0.0001
   "Top 0.001%" 0.001
   "Top 0.01%" 0.01
   "Top 0.1%" 0.1
   "Top 1%" 1.0
   "Top 10%" 10.0
   "Next 9%" 10.0
   "Middle 40%" 50.0
   "Next 40%" 50.0
   "Bottom 50%" 100.0})

(def cohort-percentile-lower
  {"Top 0.1%" 0.0
   "Remaining top 1%" 0.1
   "Next 9%" 1.0
   "Next 40%" 10.0
   "Bottom 50%" 50.0})

(def human-pows [[12 "T"]
                 [9 "B"]
                 [6 "M"]
                 [3 "K"]
                 [0 ""]])

(defn money [num]
  (let [base-pow (int (m/floor (m/log10 num)))
        [base-pow suffix] (first (filter (fn [[base _]] (>= base-pow base)) human-pows))
        value    (float (/ num (m/pow 10 base-pow)))]
    (str (format "$%.1f" value) suffix)))

(def ineq
  (-> individual-wealth-rti-latest
      (tc/rename-columns {"Real Wealth Threshold" :net-worth})
      (tc/add-columns {:percentile (fn [ds]
                                     (->> (get ds "Group")
                                          (map #(str/replace % #"-.*$" ""))
                                          (map #(or (cohort-percentile-upper %) %))
                                          (map #(- 100.0 %))))
                       :tag (fn [ds]
                              (->> (:net-worth ds)
                                   (map money)
                                   (map #(str (- 100.0 %1) "% " %2) (:percentile ds))))})
      (tc/select-columns [:percentile :net-worth :tag])))

(def wealth-forbes
  (->  billionaires
       (tc/select-rows (fn [{:strs [countryOfCitizenship
                                    finalWorth]}]
                         (and
                          (= countryOfCitizenship "United States")
                          (> finalWorth 1000.0))))
       ;;(tc/head 3)
       (tc/rename-columns {"finalWorth" :net-worth
                           "personName" :tag})
       (tc/update-columns {:net-worth (partial map #(* % 1000000.0))})
       (tc/select-columns [:net-worth :tag])
       (tc/add-columns {:percentile (fn [ds]
                                      (map #(- 100.0 (/ % 100000000.0))
                                           (range (count (:net-worth ds)))))})))

(def individual-wealth-comb
  (-> (tc/concat ineq individual-wealth-cuny wealth-forbes)
      (tc/unique-by :percentile)
      (tc/order-by :percentile)))

^{:kindly/options {:id :fig-us-individual-wealth-distribution
                   :caption "The full U.S. wealth distribution is a right angle: a tiny top slice captures enormous wealth."}}
(-> individual-wealth-comb
    (tc/update-columns {:net-worth (partial map #(/ % 1000000000.0))})
    (pj/lay-line :percentile :net-worth {:size 0.5})
    (pj/lay-point)
    (pj/options {:title "US Individual Wealth Distribution"
                 :y-label "Net worth $billions"
                 :x-label "People ordered by wealth percentile"})
    (pj/plot)
    (into [[:text {:x 465 :y 100} "Elon ($930B)"]
           [:path {:d "M 525,80 L 555,65"
                   :stroke "currentColor"}]
           [:text {:x 420 :y 200} "Jeff ($370B)"]
           [:path {:d "M 505,210 L 555,225"
                   :stroke "currentColor"}]
           [:text {:x 430 :y 310} "Billionaire ($1B)"]
           [:path {:d "M 528,317 L 558,332"
                   :stroke "currentColor"}]
           [:text {:x 260 :y 300} "Median ($97K)"]
           [:path {:d "M 318.5,310 L 318.5,330"
                   :stroke "currentColor"}]
           [:text {:x 80 :y 310} "7% ($-40K to 0)"]
           [:path {:d "M 110,320 L 110,330"
                   :stroke "currentColor"}]]))

;; You probably have not seen a chart like this before.
;; Two things make it strange.
;; First, the concentration is so extreme that the "curve" is a right angled corner.
;; Second, the picture is stitched together from several datasets.[^2]

;; ::: {.callout-note title="Household vs Individual"}
;; These charts are normalized to individuals rather than households.
;; For household wealth, generally you can multiply the cutoffs by 2,
;; except for billionaires (where wealth is typically not split with a spouse equally).
;; :::

;; Net worths under $1B are visually indistinguishable.
;; For the sake of trying to see the curve, let's exclude billionaires.
;; Doing so zooms in by 1000x.

^{:kindly/options {:id :fig-us-wealth-excluding-billionaires
                   :caption "Even after excluding billionaires, the wealth curve remains a right angle."}}
(-> individual-wealth-comb
    (tc/head 38)
    (tc/update-columns {:net-worth (partial map #(/ % 1000000.0))})
    (pj/lay-line :percentile :net-worth {:size 0.5})
    (pj/lay-point)
    (pj/options {:title "US Individual Wealth Distribution excluding Billionaires"
                 :y-label "Net worth $millions"
                 :x-label "People ordered by wealth"})
    (pj/plot)
    (into [[:text {:x 425 :y 100} "0.001% ($1B+)"]
           [:path {:d "M 525,80 L 555,65"
                   :stroke "currentColor"}]
           [:text {:x 430 :y 250} "0.01% ($197.7M)"]
           [:path {:d "M 528,257 L 558,277"
                   :stroke "currentColor"}]
           [:text {:x 450 :y 310} "0.1% ($29.6M)"]
           [:path {:d "M 528,317 L 558,327"
                   :stroke "currentColor"}]
           [:text {:x 260 :y 300} "Median ($97K)"]
           [:path {:d "M 318.5,310 L 318.5,330"
                   :stroke "currentColor"}]
           [:text {:x 80 :y 310} "7% ($-40K to 0)"]
           [:path {:d "M 110,320 L 110,330"
                   :stroke "currentColor"}]]))

;; At this scale the distribution is still a right angle.
;; So now let's exclude everyone in the top 0.1% (over $29.6M).
;; Doing so zooms in by another 30x, 30000x in total.

^{:kindly/options {:id :fig-us-wealth-excluding-top-0.1
                   :caption "Removing the top 0.1% leaves a very steep curve, indicating that concentration remains severe below multimillionaires."}}
(-> individual-wealth-comb
    (tc/head 36)
    (tc/update-columns {:net-worth (partial map #(/ % 1000000.0))})
    (pj/lay-line :percentile :net-worth {:size 0.5})
    (pj/lay-point)
    (pj/options {:title "US Individual Wealth Distribution excluding 0.1%"
                 :y-label "Net worth $millions"
                 :x-label "People ordered by wealth"})
    (pj/plot)
    (into [[:text {:x 430 :y 100} "0.1% ($29.6M)"]
           [:path {:d "M 525,80 L 560,60"
                   :stroke "currentColor"}]
           [:text {:x 450 :y 250} "1% ($6.0M)"]
           [:path {:d "M 528,257 L 558,277"
                   :stroke "currentColor"}]
           [:text {:x 400 :y 310} "10% ($1.2M)"]
           [:path {:d "M 490,317 L 510,323"
                   :stroke "currentColor"}]
           [:text {:x 260 :y 300} "Median ($97K)"]
           [:path {:d "M 318.5,310 L 318.5,330"
                   :stroke "currentColor"}]
           [:text {:x 80 :y 310} "7% ($-40K to 0)"]
           [:path {:d "M 110,320 L 110,330"
                   :stroke "currentColor"}]]))

;; It is telling that we can only now begin to "see" the distribution curve.
;; Even then, we are still mostly looking at the top 10% of it.
;; Let's zoom in again by excluding the top 1% (over $6.0M).
;; Doing so zooms in by another 5x, zoom is now at 150000x in total.

^{:kindly/options {:id :fig-us-wealth-excluding-top-1
                   :caption "Once the top 1% is removed, the distribution still curves steeply upward."}}
(-> individual-wealth-comb
    (tc/head 35)
    (tc/update-columns {:net-worth (partial map #(/ % 1000000.0))})
    (pj/lay-line :percentile :net-worth {:size 0.5})
    (pj/lay-point)
    (pj/options {:title "US Wealth Distribution excluding the Top 1%"
                 :y-label "Net Worth $Millions"
                 :x-label "People ordered by wealth"})
    (pj/plot)
    (into [[:text {:x 430 :y 100} "1% ($6.0M)"]
           [:path {:d "M 525,80 L 560,60"
                   :stroke "currentColor"}]]))

;; Even at this ultra zoomed in scale the wealth curve is steep.
;; To see what is happening at the bottom and middle,
;; we need to zoom in even further by excluding the top 10% (over $1.2M).
;; Zoom is now at 1000000x.

^{:kindly/options {:id :fig-us-wealth-excluding-top-10
                   :caption "60% of adult individuals have less net worth than their share of government liabilities."}}
(-> individual-wealth-comb
    (tc/head 30)
    (tc/update-columns {:net-worth (partial map #(/ % 1000000.0))})
    (pj/lay-line :percentile :net-worth {:size 0.5})
    (pj/scale :x {:domain [-5 105]})
    (pj/lay-point)
    (pj/lay-rule-h {:y-intercept 0.156 :color "red" :stroke-dash :dashed})
    (pj/options {:title "US Wealth Distribution excluding the Top 10%"
                 :y-label "Net Worth $Millions"
                 :x-label "People ordered by wealth"})
    (pj/plot)
    (into [[:text {:x 80 :y 290 :fill "red"} "Government Debt"]
           [:text {:x 400 :y 100} "10% ($1.2M)"]
           [:path {:d "M 475,80 L 510,60"
                   :stroke "currentColor"}]]))

;; Now we can see more clearly that 7% of adults in the US have a negative net worth.
;; What is worse is that when we look at government wealth,
;; the [treasury balance sheet](https://fiscal.treasury.gov/accounting/us-financial-report/balance-sheets)
;; is at net negative 41.7T, which equates to 156K per adult individual.
;; In a very real sense we do carry this government debt.
;; 20% of the tax you pay goes directly to the interest on this debt
;; as explored in [Who Pays Tax?](u1_who_pays_tax.html).
;; Through this lens, 60% of adult individuals have negative net worth.

;; It's truely dizzying to try to imagine 1000000x zoom.
;; The wealth curve spans several orders of magnitude, making its scale difficult to grasp.
;; A video visualization by politizane,
;; [Wealth Inequality in America](https://www.youtube.com/watch?v=2GxlL5-0m_g),
;; gives more time to the gravity of the sense of the scale.

;; Now we will look at the full range on a log scale.
;; This compresses the steepness so the whole distribution fits on one chart.
;; We did not begin with this view because log scales are easy to misread,
;; and they can create a false sense of familiarity.

(def household-count 131000000)
(def adult-count 267000000)
(def total-wealth 181600000000000)
(def mean-wealth (/ total-wealth adult-count))

(defn power-law [gini]
  (let [alpha (/ (+ 1 gini) (* 2 gini))
        x-m (* mean-wealth (/ (- alpha 1) alpha))
        exponent (/ 1.0 alpha)
        percentiles (concat (range 100)
                            [99.9 99.99 99.999 99.9999 99.99999
                             (- 100.0 (/ 100.0 adult-count))])]
    ;; Map over ranks to compute net-worth and format the output
    {:percentile percentiles
     :net-worth (mapv (fn [p]
                        (* x-m (Math/pow (/ 100.0 (- 100.0 p)) exponent)))
                      percentiles)}))

(defn calculate-auc [datapoints total-population]
  (let [sorted-points (tc/order-by datapoints :percentile)
        percentile-to-population-factor (/ total-population 100.0)]
    (reduce +
            (map (fn [{p1 :percentile, w1 :net-worth}
                      {p2 :percentile, w2 :net-worth}]
                   (let [dp (- p2 p1)
                         dx (* dp percentile-to-population-factor)
                         avg-wealth (* 0.5 (+ w1 w2))]
                     (* dx avg-wealth)))
                 (tc/rows sorted-points :as-maps)
                 (rest (tc/rows sorted-points :as-maps))))))

#_(calculate-auc individual-wealth-comb adult-count)

^{:kindly/options {:id :fig-us-wealth-log-scale
                   :caption "A log-scale view allows the entire wealth distribution can be seen."}}
(-> individual-wealth-comb
    (tc/select-rows (fn [{:keys [net-worth]}]
                      (>= net-worth 1)))
    (tc/update-columns {:net-worth (partial map #(/ % 1000000.0))})
    (pj/lay-line :percentile :net-worth {:size 0.5})
    (pj/lay-point)
    #_(pj/lay-line :percentile :net-worth
                 {:data (-> (power-law 0.5)
                            (tc/update-columns {:net-worth (partial map #(/ % 1000000.0))}))
                  ;;:stroke-dash :dashed
                  :color "green"})
    (pj/lay-rule-h {:y-intercept 0.156 :color "red" :stroke-dash :dashed})
    (pj/scale :y :log)
    (pj/scale :x {:domain [-5 105]})
    (pj/options {:title "Log Scale US Individual Wealth Distribution"
                 :y-label "Net worth $millions"
                 :x-label "People ordered by wealth"})
    (pj/plot)
    (into [#_[:text {:x 350 :y 200 :fill "green"} "Gini 0.5"]
           [:text {:x 100 :y 230 :fill "red"} "Government Debt"]]))

;; Negative net worths cannot be shown on a log scale.
;; Even so, the chart makes it clear that this is not a fair competitive market distribution.
;; The bottom half is in an extremely weak position.

;; What does the very top of the wealth curve look like?

(def rich-data
  (-> billionaires
      (tc/select-columns ["personName" "finalWorth" "squareImage" "countryOfCitizenship"])
      (tc/select-rows (fn [{:strs [countryOfCitizenship]}]
                        (= countryOfCitizenship "United States")))
      (tc/update-columns {"finalWorth" (partial map #(/ % 1000.0))
                          "personName" (partial map #(str/replace % " & family" ""))
                          "squareImage" (partial map #(if (or (str/blank? %) (str/starts-with? % "http"))
                                                        %
                                                        (str "https:" %)))})
      (tc/head 20)
      (tc/order-by "finalWorth" :asc)))

(defn image-url->data-uri
  [url]
  (with-open [in (.openStream (java.net.URL. url))]
    (let [bytes (.readAllBytes in)
          mime (or (URLConnection/guessContentTypeFromStream
                    (java.io.ByteArrayInputStream. bytes))
                   (URLConnection/guessContentTypeFromName url)
                   "image/jpeg")
          b64 (.encodeToString (Base64/getEncoder) bytes)]
      (str "data:" mime ";base64," b64))))

;; ::: {#fig-forbes-rich-list}

^{:kindly/options {:static false
                   :caption "hello?"}}
(let [rich-pose (-> rich-data
                    (pj/lay-bar "finalWorth" "personName")
                    (pj/options {:title "The Forbes Rich List (September 17, 2026)"
                                 :x-label "Net Worth $Billions"
                                 :y-label "Person"}))
      panel (-> rich-pose pj/frames :panels first)
      coords (map (partial pj/to-drawing panel)
                  (rich-data "finalWorth")
                  (rich-data "personName"))
      images (map image-url->data-uri (rich-data "squareImage"))
      r 30
      offset (cycle [0 r])]
  (into (pj/plot rich-pose)
        (for [[[x y] url dx] (map vector coords images offset)]
          [:image {:x (+ 330 dx)
                   :y (- y (/ r 2))
                   :href url
                   :width r
                   :height r
                   :style {:clip-path "circle(50%)"}}])))

;; U.S. billionaires.[^3]
;; :::

;; There are almost a thousand billionaires in the US.

^:kind/table
(-> billionaires
    (tc/select-columns ["personName" "finalWorth" #_"squareImage" "countryOfCitizenship"])

    (tc/select-rows (fn [{:strs [countryOfCitizenship finalWorth]}]
                      (and
                       (= countryOfCitizenship "United States")
                       (> finalWorth 1000.0))))
    (tc/update-columns {"finalWorth" (partial map #(* % 1000000.0))
                        "personName" (partial map #(str/replace % " & family" ""))})
    (tc/aggregate {:billionaire-count #(count (% "finalWorth"))
                   :total-wealth #(-> (reduce + (% "finalWorth"))
                                      (money))}))

;; Has it always been like this? No.

(def dfa-zip
  "https://www.federalreserve.gov/releases/z1/dataviz/download/zips/dfa.zip"
  (fs/path data-dir "dfa.zip"))

(comment
  ;; Execute this manually to get the latest data
  (require '[babashka.fs :as fs]
           '[babashka.http-client :as http])
  (let [dfa-url "https://www.federalreserve.gov/releases/z1/dataviz/download/zips/dfa.zip"]
    (-> (http/get dfa-url {:as :stream})
        (:body)
        (fs/copy dfa-zip))))

^:kind/hidden
(fs/unzip dfa-zip data-dir {:replace-existing true})

(def categories
  {"TopPt1" "Top 0.1%"
   "RemainingTop1" "Remaining top 1%"
   "Next9" "Next 9%"
   "Next40" "Next 40%"
   "Bottom50" "Bottom 50%"})

(def networth
  (-> (tc/dataset (str (fs/path data-dir "dfa-networth-levels-detail.csv")))
      (tc/select-columns #{"Date" "Category" "Net worth" "Household count" "Minimum Wealth Cutoff"})
      (tc/update-columns {"Category" (partial map categories)
                          "Date" (partial map (fn [date]
                                                (let [[_ year quarter] (re-matches #"(\d{4}):Q([1-4])" date)
                                                      y (parse-long year)
                                                      q (parse-long quarter)
                                                      quarter-end-month (case q 1 3 2 6 3 9 4 12)]
                                                  (.atEndOfMonth (java.time.YearMonth/of y quarter-end-month)))))})
      (tc/group-by "Date")
      (tc/add-column "Share"
                     (fn [group]
                       (let [net-worths (get group "Net worth")
                             total (reduce + net-worths)]
                         (map #(-> % (/ total) (* 100.0)) net-worths))))
      (tc/ungroup)
      (tc/group-by "Category")
      (tc/add-column "Net worth growth"
                     (fn [group]
                       (let [yrs 1
                             qtrs (* yrs 4)
                             nws (get group "Net worth")]
                         (concat (repeat qtrs nil)
                                 (map (fn [a b]
                                        (-> (- b a)
                                            (/ a yrs)
                                            (* 100.0)))
                                      nws
                                      (drop qtrs nws))))))
      (tc/add-column "Share growth"
                     (fn [group]
                       (let [shares (get group "Share")
                             first-share (first shares)]
                         (map (fn [share]
                                (-> (- share first-share)
                                    (/ first-share)
                                    (* 100.0)))
                              shares))))
      (tc/ungroup)
      (tc/rename-columns {"Category" "Cohort"})
      (tc/add-column "Percentile"
                     (fn [ds]
                       (map cohort-percentile-upper (get ds "Cohort"))))
      (tc/add-column "PercentileLower"
                     (fn [ds]
                       (map cohort-percentile-lower (get ds "Cohort"))))
      (tc/order-by ["Date" "Percentile"])))

;; To get a feel for how wealth concentration has been changing,
;; here is a plot of the wealth share growth by cohort.

^{:kindly/options {:caption "The wealthiest are growing the fastest.[^4]"
                   :id :fig-wealth-share-growth}}
(-> networth
    (pj/lay-line "Date" "Share growth" {:color "Cohort"})
    (pj/options {:title "Growth of Share of Total Wealth per Cohort"}))

;; The Federal Reserve series starts in 1989 and shows that over the past 37 years,
;; the top 0.1% of wealth holders have increased their share of total US wealth by roughly 68%.

;; However, due to the inherent difficulty of capturing peak fortunes via traditional survey data,
;; alternative methodologies suggest this figure is heavily understated.
;; Utilizing administrative IRS data via the tax capitalization method,
;; economists Saez and Zucman estimate that the top 0.1%'s relative wealth share growth is actually closer to 100% to 200% over the same period.[^5]

;; While the top 0.1% has experienced immense gains,
;; the concentration at the apex 0.00001% (representing roughly the 20 to 35 wealthiest individuals) has grown even more exponentially.
;; According to analysis by Gabriel Zucman, this elite tier saw their share of total U.S. household wealth skyrocket,
;; growing their share by 15x over the last 45 years, as shown in @fig-top-20-wealth.

(def latest-networth
  (-> networth
      (tc/group-by "Date")
      (tc/groups->seq)
      (->> (filter #(seq (remove nil? (tc/column % "Minimum Wealth Cutoff")))))
      (last)
      (tc/order-by "PercentileLower")))

#_(pj/lay-bar latest-networth "Cohort" "Share")

;; When wealth accumulates in a few hands, the rest of society competes for a smaller pool of resources.
;; That makes housing, capital, and opportunity more expensive for everyone else.
;; Wealth starts acting like a black hole, pulling in property, equity, and political influence.

;; > "Where wealth accumulates, men decay." -- Oliver Goldsmith

;; Capital gets pushed toward holding existing assets rather than solving new problems.
;; The economy becomes more about preserving position than creating new value.
;; That is the static trap.
;; Assets and resources grow slowly as a total pool while concentrated capital compounds rapidly.
;; The economy then asks most people to compete for whatever is left over.

;; Over history, the share of total wealth held by the Top is increasing.

#_(let [distribution-chart
      (fn [title cohort-data]
        (-> cohort-data
            (pj/lay-line :percentile :wealth-share)
            (pj/options {:title title
                         :x-label "Wealth percentile"
                         :y-label "Share of total household wealth (%)"})))
      current (tc/rename-columns latest-networth {"Cohort" :percentile
                                                  "Share" :wealth-share})
      broad-middle {:percentile (mapv str [0.1 1.0 9.0 40.0 50.0])
                    :wealth-share [5.0 10.0 25.0 35.0 25.0]}
      capped-elite {:percentile (mapv str [0.1 1.0 9.0 40.0 50.0])
                    :wealth-share [3.0 7.0 20.0 35.0 35.0]}]
  (pj/arrange [(distribution-chart "broad middle" current)
               (distribution-chart "broad middle" broad-middle)
               (distribution-chart "capped elite" capped-elite)]))

(defn cumsum2 [ds x y]
  (-> ds
      (tc/cumsum (str "Cumulative " x) x)
      (tc/cumsum (str "Cumulative " y) y)))

(defn cumulative-share [f]
  (-> networth
      (tc/group-by "Date")
      (tc/groups->seq)
      (f)
      (tc/order-by "Percentile" :desc)
      (->> (tc/concat (tc/dataset {"Cohort" "Zero"
                                   "Share" 0.0
                                   "Percentile" 0.0})))
      (cumsum2 "Percentile" "Share")))

(def current-cumulative-share
  (cumulative-share last))

(defn interp-ds [ds x y t a b step]
  (let [in (in/interpolation t
                             (get ds x)
                             (get ds y))
        ps (range a b step)]
    (tc/dataset {x ps
                 y (map in ps)})))

(defn inter-line [ds x y]
  (-> ds
      (pj/lay-point x y
                    {:data ds})
      (pj/lay-line x y
                   {:stroke-dash :dashed
                    :size 0.5
                    :data (interp-ds ds
                                     x y
                                     :monotone
                                     0.0 100.01 0.1)})))

#_(inter-line current-cumulative-share "Cumulative Percentile" "Cumulative Share")

(def initial-share
  (-> networth
      (tc/select-columns ["Date" "Cohort" "Share"])
      (tc/group-by "Date")
      (tc/groups->seq)
      (first)
      (tc/update-columns {"Date" (partial map str)})))

(def current-share
  (-> networth
      (tc/select-columns ["Date" "Cohort" "Share"])
      (tc/group-by "Date")
      (tc/groups->seq)
      (last)
      (tc/update-columns {"Date" (partial map str)})))

(def historic-best-share
  "source from https://eml.berkeley.edu/~saez/saez-zucmanNBER14wealth.pdf"
  (tc/dataset
   {"Date" (repeat 5 "1978-06-30")
    "Cohort" ["Top 0.1%" "Remaining top 1%" "Next 9%" "Next 40%" "Bottom 50%"]
    "Share" [7.0 12.0 25.0 53.5 2.5]}))

^{:kindly/options {:id :fig-wealth-share-by-cohort
                   :caption "Half of the upper middle's share was redistributed to the top 10%."}}
(-> (tc/concat current-share initial-share historic-best-share)
    (tc/add-column "percentile" (fn [ds]
                                  (map cohort-percentile-lower (ds "Cohort"))))
    (tc/order-by ["Cohort" "percentile"])
    (pj/lay-bar "Share" "Cohort" {:color "Date"})
    (pj/lay-rule-h {:y-intercept 2.5 :stroke-dash :dashed})
    (pj/lay-text {:x 45 :y 2.7 :text "Growing ↑"})
    (pj/lay-text {:x 45 :y 2.3 :text "Shrinking ↓"})
    (pj/options {:title "The Rich get Richer and the Rest get Poorer"
                 :x-label "Share %"
                 :y-label "Wealth Percentile"}))

;; The ability to compound wealth is unevenly distributed.

;; The economy is influenced by geography, technology, infrastructure, culture, and global events.
;; Government policy still has outsized influence over how the gains are distributed.
;; Tax policy, regulation, and public spending all affect growth, stability, and living standards.
;; Tax policy matters most here because it determines whether capital is rewarded for circulating or for hiding.

;; Extreme wealth concentration changes how the economy behaves.
;; It shrinks access to land, housing, infrastructure, and other fixed resources.
;; It also lets passive wealth compound faster than most wages can grow.
;; That makes the economy feel static for everyone outside the top.
;; Only a very small slice at the top is capturing the strongest gains.
;; Most people experience the system as tighter, more expensive, and less forgiving.
;; Affordability, mobility, and living standards all sit downstream of that shape.
;; Wealth distribution is the root thing.

;; The United States is under dangerous stress.
;; The evidence is clear: political instability has worsened, trust in institutions is low, and the government struggles to function.
;; We have all the resources we need, yet we are heading deeper into avoidable crises.

#_(tc/dataset "src/economics/wealth_distribution/unbounded/ddf--datapoints--worth--by--person--time.csv")
#_(tc/dataset "src/economics/wealth_distribution/unbounded/ddf--entities--person.csv")

;; In a healthy society, people should be able to move up by effort and contribution.
;; When asset ownership becomes too concentrated, that opportunity is reduced.
;; There is no plausible path to build assets and move up the ladder.
;; The system stops rewarding broad participation and only rewards possession.
;; Today entry into the top 1% already requires asset ownership rather than salary alone.
;; Inheritance and equity ownership matter more than work.

;; ::: {.callout-note}
;; ## Storytime

;; When I was a kid, I spent countless afternoons playing Tiapan, a trading game set in Southeast Asia.
;; I sailed between ports, bought cargo, dodged pirates, and tried to make a fortune.
;; It didn't take long to discover the best strategy.
;; *Smuggle opium.*
;; I bought it by the shipload.
;; I transported it across the map.
;; I sold tonnes and tonnes of it.
;; I bought cannons to protect my ship, and defeated the navies of entire countries.
;; I had no idea what opium was.
;; When my brother explained that it was a highly addictive drug, I was horrified.
;; I was just trying to win the game.
;; The game rewards snowballing advantages,
;; and I didn't understand the consequences.

;; These days I find myself wondering what our own economic game rewards.
;; When I first looked into wealth distribution,
;; the main charts I found showed how much wealth each group owned over time.
;; I remember thinking,
;; "What's the big deal?"
;; Most discussions about wealth concentration revolve around percentages and statistics.
;; I find that hard to connect with.

;; It wasn't until I started plotting growth shares that something clicked.
;; I realized what was actually happening.
;; Every system rewards something.
;; Our game rewards snowballing wealth.
;; And the winners are pulling away.
;; :::

;; ## Conclusion

;; Wealth concentration is the outcome of compounding asset ownership.
;; Wealth is the most concentrated it has ever been, and continues to concentrate.
;; Extreme inequality is dangerous, with negative social and economic effects.
;; History offers severe warnings about the consequences.

;; In the next part, [Economic Growth](u6_economic_growth.html), we think harder about how inequality impacts growth.

;; [^1]: Top 0.00001% wealth share from [Realtime Inequality](https://realtimeinequality.org/).
;; [^2]: The chart combines lower and middle percentile wealth data from [CUNY](https://stonecenter.gc.cuny.edu/changes-in-household-wealth-and-income-1989-2022-an-analysis-by-arthur-b-kennickell/), top-end percentiles from [Realtime Inequality](https://realtimeinequality.org/), and individual billionaires from [Forbes](https://forbes.com/real-time-billionaires/).
;; [^3]: [Forbes' annual real-time billionaire list](https://www.forbes.com/real-time-billionaires/) provides the source for the U.S. billionaire count and the top-end wealth values used in the chart.
;; [^4]: The Federal Reserve's [Distributional Financial Accounts](https://www.federalreserve.gov/releases/z1/dataviz/dfa.htm) provide the cohort-level wealth-share series.
;; [^5]: Tax-capitalization studies by [Saez and Zucman](https://eml.berkeley.edu/~saez/saez-zucmanNBER14wealth.pdf) estimate that top-end wealth shares grew even faster than survey-based measures suggest over the same period.
