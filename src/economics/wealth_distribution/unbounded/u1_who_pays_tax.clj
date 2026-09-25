^:kindly/hide-code
^{:kindly/options {:static true
                   :kinds-that-hide-code #{:kind/var :kind/hiccup2 :kind/fn}}}
(ns economics.wealth-distribution.unbounded.u1-who-pays-tax
  {:clay {:title          "Unbounded: Who Pays Tax?"
          :quarto         {:sidebar "unbounded"
                           :css     "styles.css"
                           :author      [:timothypratley]
                           :description "Who pays how much tax, and why labor income is taxed more than asset growth."
                           :abstract    "The tax system taxes labor income, not asset growth. The wealthiest pay less tax than you do."
                           :date        "2026-06-20"
                           :type        :post
                           :category    :economics
                           :tags        [:tax :economics :growth]}}}
  (:require [scicloj.plotje.api :as pj]
            [tablecloth.api :as tc]
            [economics.wealth-distribution.unbounded.u5-wealth-concentration :as u5]))

;; ## Tax Revenues

;; Workers pay the vast majority of tax collected by the government.
;; In 2025, $5T tax revenue was collected In the United States.
;; Corporations paid less than 9% of the total,
;; and capital gains made up less than 5%.
;; The vast majority of tax revenue comes from Income and Payroll attributable to workers.

(def w "#9FC0DE")
(def wd "#547A9E")
(def o "#FFB347")
(def od "#B86B00")

(def tax-revenue-split
  {:receipt (reverse ["Income Tax (Wages, Salaries, Ordinary Income)"
                      "Social Insurance and Retirement (Payroll)"
                      "Capital Gains Tax (Realized Asset Profits)"
                      "Corporation Income Taxes"
                      "Customs Duties (Tariffs - Refunds)"
                      "All Other Receipts (Excise, Estate, etc.)"])
   :attributable (reverse [:workers :workers :owners :owners :workers :workers])
   :c (reverse [w w o o w w])
   :revenue-b (reverse [2417.94 1748.29 238.10 452.09 28.87 183.31])
   :source (reverse ["CBO outlook/publication tables - https://www.cbo.gov/publication/61882"
                     "USAFacts federal receipts tracker - https://usafacts.org/answers/how-much-does-the-us-federal-government-collect/country/united-states/"
                     "CBO realizations outlook model - https://www.cbo.gov/system/files/2026-02/61882-Outlook-2026.pdf"
                     "CBO monthly budget review - https://www.cbo.gov/system/files/2025-11/61307-MBR-FY25-final.pdf"
                     "Tax Notes + CBP litigation-adjusted net - https://www.taxnotes.com/research/federal/legislative-documents/congressional-budget-office-reports/cbo-estimates-955-billion-deficit-through-april/7vt0p"
                     "CBO monthly budget review - https://www.cbo.gov/system/files/2025-11/61307-MBR-FY25-final.pdf"])})

^{:kindly/options {:id :fig-tax-revenue-split
                   :caption "In 2025, income and payroll taxes dominated federal receipts, while capital gains and corporation taxes were much smaller sources of revenue.[^1]"}}
(-> tax-revenue-split
    (tc/dataset)
    (tc/add-columns {:revenue-text (fn [ds]
                                     (map #(u5/money (* 1000000000.0 %)) (ds :revenue-b)))})
    (pj/lay-bar :revenue-b :receipt {:color {:column :c :scale false}})
    (pj/lay-label {:text :revenue-text})
    (pj/options {:title "2025 Federal Tax Revenue"
                 :x-label "Revenue (USD billions)"
                 :y-label "Receipt category"
                 :legend-position :none}))

;; How much are the wealthy contributing?

;; There is limited data showing how much tax people pay relative to the wealth they own.
;; The best data I could find comes from a study of 2019.
;; The study includes federal, state, and local taxes, corporate taxes, and taxes on capital gains.
;; In that year, the bottom 50% of the population owned 2% of the wealth and paid 3% of all taxes.
;; The next 40% owned 21% of the wealth and paid 46% of all taxes.
;; The next 9% owned 37% of the wealth and paid 26% of all taxes.
;; Billionaires owned 7% of the wealth and paid just 3% of all taxes.

(defn share [ds k]
  (let [total (double (reduce + (ds k)))]
    (map #(* 100.0 (/ % total)) (ds k))))

(defn pct [ds k]
  (map #(format "%d%%" (Math/round %)) (ds k)))

(defn exclusive
  "Convert nested cohort totals into exclusive cohort totals by subtracting
   the next (internal) cohort, but only when it shares the same methodology.
   Rows must be ordered from the largest cohort to the smallest.
   The last row of each methodology chain keeps its full total."
  [totals methodologies]
  (map (fn [total next-total subtract?]
         (if subtract? (- total next-total) total))
       totals
       (concat (rest totals) [0])
       (concat (map = methodologies (rest methodologies)) [false])))

(defn split-all
  "Splits ds's first ('all') row into 'bottom 50%' and 'next 40%' rows, dividing
   its :exclusive-wealth and :exclusive-tax by the given fraction for 'bottom
   50%' (the remainder goes to 'next 40%')."
  [ds wealth-frac tax-frac]
  (let [all-row (tc/head ds 1)
        top50 (tc/update-columns all-row
                                 {:n (partial map #(/ % 2))
                                  :total-wealth (partial map #(* % (- 1.0 wealth-frac)))
                                  :total-tax (partial map #(* % (- 1.0 tax-frac)))
                                  :group (partial map (constantly "top 50%"))})]
    (tc/concat all-row
               top50
               (tc/tail ds (dec (tc/row-count ds))))))

(def tax-burdens-2019-table-1
  "Transcribed from Estimating Tax Burdens by Wealth Group https://www.irs.gov/pub/irs-soi/24rpestimatingtaxburdens.pdf
   The first 6 rows (all .. top .001%) come from SOI linked individual tax data.
   The last 3 rows (top .0002% .. top .00005%) are Forbes-based estimates averaged
   over several years, so they are not strictly nested inside the SOI cohorts.
   The 'all' row is further split into 'bottom 50%' and 'next 40%' using DFA wealth
   shares and IRS SOI tax shares (footnote ^4), since the source study has no such split."
  (-> {:group ["all" "top 10%" "top 1%" "top .1%" "top .01%" "top .001%" "top .0002%" "top .0001%" "top .00005%"]
       :methodology [:soi :soi :soi :soi :soi :soi :forbes :forbes :forbes]
       :n [183700000 18370000 1837000 183700 18370 1837 367 184 92]
       :wealth [503.491 3804.8 18793.0 93902.0 485268.0 2318864.0 8014621.0 12915870.0 20770599.0]
       :agi [65.444 273.4 1093.0 4963.0 21314.0 98851.0 172669.0 244632.0 344917.0]
       :charitable-deductions [1.382 8.8 52.0 348.0 2120.0 11190.0 41768.0 66192.0 105291.0]
       :income-and-fica-taxes [17.44 82.6 389.0 1887.0 8064.0 30597.0 49623.0 66864.0 87043.0]
       :estate-inheritance-and-gift-taxes [0.117 1.2 12.0 112.0 578.0 2681.0 2354.0 3368.0 3477.0]
       :corporate-income-taxes [1.627 12.0 62.0 352.0 2088.0 12790.0 44204.0 71237.0 114559.0]}
      (tc/dataset)
      ;; Show only the top .0001% Forbes row; the three tip slivers add visual noise.
      ;; As the last :forbes row it keeps its full cohort total, so its displayed
      ;; rate blends the top .00005% with the rest of the cohort.
      (tc/select-rows (comp (complement #{"top .0002%" "top .00005%"}) :group))
      ;; DFA: bottom 50% have 2.5% wealth; IRS SOI: bottom 50% pay 3.3% tax (see ^4)
      (tc/add-columns (array-map
                       :total-wealth (fn [ds]
                                       (map * (ds :wealth) (ds :n)))
                       :total-tax (fn [ds]
                                    (->> (map + (ds :income-and-fica-taxes)
                                              (ds :estate-inheritance-and-gift-taxes)
                                              (ds :corporate-income-taxes))
                                         (map * (ds :n))))))
      (split-all 0.025 0.033)
      (tc/add-columns (array-map
                       :exclusive-group ["50%" "next 40%" "next 9%" "top 1%" "top 0.1%" "top 0.01%" "top 0.001%" "top 0.0001%"]
                       :exclusive-wealth (fn [ds]
                                           (exclusive (:total-wealth ds) (:methodology ds)))
                       :exclusive-tax (fn [ds]
                                        (exclusive (:total-tax ds) (:methodology ds)))
                       :tax-rate-on-wealth (fn [ds]
                                             (map #(* 100.0 (/ %1 %2))
                                                  (ds :exclusive-tax) (ds :exclusive-wealth)))
                       :wealth-share (fn [ds]
                                       (share ds :exclusive-wealth))
                       :wealth-share-text (fn [ds]
                                            (pct ds :wealth-share))
                       :tax-share (fn [ds]
                                    (share ds :exclusive-tax))
                       :tax-share-text (fn [ds]
                                         (pct ds :tax-share))
                       :tax-intensity (fn [ds]
                                        (map / (ds :tax-share) (ds :wealth-share)))
                       :end (fn [ds]
                              (reductions + (:wealth-share ds)))
                       :start (fn [ds]
                                (cons 0.0 (butlast (:end ds))))
                       :mid (fn [ds]
                              (map #(/ (+ %1 %2) 2.0) (:start ds) (:end ds)))))))

(def overall-tax-rate-on-wealth
  "The overall tax rate on wealth (as a percentage) comes straight from the `all` row: total taxes divided by total wealth."
  (let [{:keys [total-tax total-wealth]} (first (tc/rows tax-burdens-2019-table-1 :as-maps))]
    (* 100.0 (/ total-tax total-wealth))))

^{:kindly/options {:id :fig-tax-rate-on-wealth-by-cohort
                   :caption "Each area's width is a cohort's share of total wealth. Its height is the effective tax rate paid on that wealth, so its area is that cohort's share of all taxes. Cohorts are exclusive.[^2]"}}
(-> tax-burdens-2019-table-1
    (tc/pivot->longer [:start :end] {:value-column-name :cumulative-wealth})
    (pj/lay-area :cumulative-wealth :tax-rate-on-wealth {:color :group})
    (pj/overlay)
    (pj/lay-text {:text :exclusive-group
                  :x :end
                  :y :tax-rate-on-wealth
                  :align-x :right
                  :offset-x 7
                  :offset-y 7
                  :data tax-burdens-2019-table-1})
    ;; Billionaire line: the top .001% cohort averages over $2B per person,
    ;; so everything from its band rightward is billionaire territory.
    (pj/lay-rule-v {:x-intercept (nth (:start tax-burdens-2019-table-1) 6)
                    :stroke-dash :dashed
                    :color "gray"})
    (pj/lay-text {:text "billionaires  →"
                  :x (nth (:start tax-burdens-2019-table-1) 6)
                  :y 7
                  :align-x :center
                  :color "gray"
                  :offset-x -5
                  :offset-y 12})
    ;; The bands get too narrow for share labels past "top .1%" (first 5 rows only).
    (pj/lay-text {:text :wealth-share-text
                  :x :mid
                  :y 0.3
                  :align-x :center
                  :data tax-burdens-2019-table-1})
    (pj/lay-label {:text :tax-share-text
                   :x :mid
                   :align-x :center
                   :y :y
                   :data (tc/add-columns tax-burdens-2019-table-1
                                         {:y (fn [ds]
                                               (map #(/ % 2.0) (:tax-rate-on-wealth ds)))})})
    (pj/lay-rule-h {:y-intercept overall-tax-rate-on-wealth
                    :stroke-dash :dashed
                    :color "green"})
    (pj/lay-text {:text "average tax rate on all wealth"
                  :x 40
                  :y overall-tax-rate-on-wealth
                  :color "green"
                  :offset-y -7})
    (pj/scale :x {:domain [0 100]})
    (pj/scale :y {:breaks [2 4 6 8]})
    (pj/options {:title "2019 Tax Burdens by Wealth Group"
                 :x-label "Cumulative wealth share %"
                 :y-label "Tax rate on wealth %"
                 :legend-position :none}))

;; People below the top 10% of wealth ownership contribute half of the entire tax revenue,
;; paying a 3x higher tax rate relative to their wealth than the top 10%,
;; and 8x higher than the average billionaire.
;; Another way to think about it is that if everyone payed the same tax rate relative to wealth (the dotted line in the chart), 90% of americans would pay 50% less tax.

;; Much of the income tax burden falls on high-earning professionals, such as corporate executives, specialized surgeons, senior software engineers, and law partners.
;; These individuals earn salaries or partnership income that is taxed immediately at the highest marginal rate (up to 37% federally).[^4]
;; They have high income, but they are still building their wealth and saving for retirement.
;; By contrast, the truly wealthiest individuals rarely earn standard salaries.
;; Their wealth compounds via unrealized asset growth, such as stocks, commercial real estate, and business equity.
;; The tax code only taxes realized transactions, like selling a stock.
;; A billionaire whose net worth increases by \$20M in a year owes \$0 in income tax on that growth if they do not sell assets.[^3]
;; "Buy, Borrow, Die" is a strategy to minimize tax outlay.
;; Going into debt is preferable over realizing immediate gains,
;; as the debt repayment can be spread over a longer timeline.
;; When required to sell assets, federal long term capital gains caps out at 20%, almost half the top income rate of 37%.
;; Everyone with sufficient assets is incentivized to live off untaxed liquidity.

;; The richest households can keep compounding untaxed.
;; Corporations are taxed on the profit that remains from revenue after deducting the costs of producing it.
;; Meanwhile the majority of individuals are taxed on our income, regardless of our living costs.
;; There is something perverse about this.
;; *Why is wealth growth untaxed?*

^{:kindly/options {:id :fig-tax-intensity
                   :caption "The Next 40% group is taxed most intensly, while billionaires are taxed the least."}}
(-> tax-burdens-2019-table-1
    (pj/lay-bar :tax-intensity :exclusive-group)
    (pj/options {:title "2019 Tax Intensity"
                 :y-label "Exclusive wealth group"
                 :x-label "Wealth Share / Tax Share"}))

;; The more wealth you own, the less tax you pay relative to the growth of that wealth.
;; Some billionaires have paid less than 1% of their wealth growth as tax.[^5]
;; Why should a dollar earned by working be treated differently from a dollar earned by owning?
;; Why is there this distinction between income and wealth growth?

;; The main "reason" given is that valuation and liquidity create practical problems.[^6]
;; But these are not reasons why wealth growth should not be taxable.
;; We don't excuse income tax because someone spent their income.
;; You can't say, "I don't have enough cash to pay my tax because I spent it."
;; Should someone with a billion-dollar net worth be allowed to avoid wealth growth tax because they aren't willing to liquidate any assets to pay it?
;; I'm not talking about wealth growth below millions of dollars here.
;; Retirees should be able to retire comfortably, untaxed on their nest egg.
;; I'm talking about the wealthiest 0.1% of individuals with over $62M of wealth,
;; whose assets compound every year.

;; > "The difference between death and taxes is that death doesn't get worse every time Congress meets." -- Will Rogers

;; Why the focus on tax relative to wealth?
;; Wealth growth is a form of income that is taxed far less than work income.
;; The size of this undertaxed income is proportional to wealth.

^{:kindly/options {:id :fig-wealth-grows
                   :caption "Wealth Growth is highly variable with a long term rate between 5-10%, and favours the wealthiest.[^7]"}}
(-> u5/networth
    ;;(tc/tail 60)
    (tc/select-rows (fn [row]
                      (and (get row "Net worth growth")
                           (#{"Next 40%" "Top 0.1%"} (get row "Cohort")))))
    (pj/lay-line "Date" "Net worth growth" {:color "Cohort"})
    (pj/scale :color {:values [o w]})
    (pj/lay-smooth {:alpha 0.5
                    :color {:column "Cohort"}
                    :stroke-dash :dashed
                    :confidence-band true})
    (pj/options {:legend-position :none
                 :title "Wealth Grows"
                 :y-label "Net worth growth %"})
    (pj/plot)
    (into [[:text {:x 165 :y 100 :fill od} "Top 0.1% wealth grows at 9% per year"]
           [:text {:x 165 :y 320 :fill wd} "Next 40% wealth grows at 6% per year"]]))

;; The current progressive income tax is progressive only across wage income.
;; The wealth growth of the wealthiest is significant, undertaxed income.

;; ::: {.callout-note}
;; ## Storytime

;; In my neighborhood, there are lots of dog owners.
;; Most of them are responsible enough to bag their dog's poop.
;; But some of them bag it up only if people are watching,
;; because they don't want to be seen leaving a mess behind.
;; Then, when nobody is looking, they toss the bag and keep walking.
;; *Why do I have to take their package to the bin instead?*
;; The thing that bothers me is that *when someone avoids a responsibility, the responsibility doesn't disappear.*
;; Someone else picks it up.

;; I've always known that the ultra-rich bypass income tax.
;; What shocked me was realizing how enourmous their untaxed wealth growth is.
;; More importantly, if their wealth growth was taxed at the same rate as income tax,
;; it would be a quarter of tax revenue!
;; That's when it hit me: *I've spent my career paying my share, and theirs.*
;; The amount they aren't paying is so enormous that it changed how I think about my own taxes.

;; I had to sit with that for a while.
;; It's hard to reason about exponential growth and enormous accumulations of wealth.
;; The numbers are so far outside everyday experience that it's difficult to wrap my head around.

;; It reminds me of when I first learnt about evolution in high school.
;; At the time, a million years felt like an impossible number.
;; How could I even begin to understand how long that was?
;; Later I learned that we don't need to imagine a million years directly.
;; We understand it through models:
;; generations, mutation rates, and predictable probabilities.
;; The scale itself remains beyond my intuition.

^{:kindly/options {:id :fig-wealth-scale-visual
                   :caption "The scale of wealth concentration collapses into a tiny sliver at the top when represented visually, making the distribution hard to intuit from ordinary experience."}}
^:kind/hiccup2
[:svg {:width "100%"
       :viewBox "0 0 1000 250"
       :role "img"
       :aria-label "A horizontal bar with a light majority and a dark sliver representing the top 0.1%."}
 [:g {:fill "#8b7246"}
  [:rect {:x 0
          :y 25
          :width 1000
          :height 40}]
  [:rect {:x 0
          :y 75
          :width 100
          :height 40}]
  [:rect {:x 0
          :y 125
          :width 10
          :height 40}]
  [:rect {:x 0
          :y 175
          :width 1
          :height 40}]]
 [:g {:fill "currentColor"
      :font-size 24
      :dominant-baseline "middle"
      :text-anchor "middle"}
  [:text {:x 50
          :y 45} "1000"]
  [:text {:x 50
          :y 95} "100"]
  [:text {:x 50
          :y 145} "10"]
  [:text {:x 50
          :y 195} "1"]]]

;; The same is true for wealth.
;; A billion dollars is not something I can simply imagine.
;; I can compare things within the same order of magnitude,
;; but ratios above 1000 are meaningless.
;; I need a model that breaks the scale down into comparisons I can understand.
;; :::

;; ## Conclusion

;; The responsibility to maintain society falls mostly on 40% of the population.
;; Those with the greatest capacity to accumulate wealth face the least tax.
;; Why should the growth of wealth be treated differently from income earnt by working?

;; In the next part, [Wealth Tax](u2_wealth_tax.html), we'll see how a wealth tax will affect you.

;; [^1]: Federal receipt totals are from the Congressional Budget Office's [Monthly Budget Review: Fiscal Year 2025](https://www.cbo.gov/system/files/2025-11/61307-MBR-FY25-final.pdf).
;; [^2]: Cohort wealth and tax estimates are from the IRS research paper [Estimating Tax Burdens by Wealth Group](https://www.irs.gov/pub/irs-soi/24rpestimatingtaxburdens.pdf) (2019, Table 1). Cohorts below the top 0.001% are exclusive bands derived by subtracting nested cohorts; the top 0.0001% cohort is a Forbes-based estimate averaged over several years.
;; [^3]: ProPublica's summary of ultrawealthy tax-avoidance techniques, [Ten Ways Billionaires Avoid Taxes on an Epic Scale](https://www.propublica.org/article/billionaires-tax-avoidance-techniques-irs-files), explains how unrealized gains and borrowing against appreciated assets let large fortunes avoid ordinary income-tax treatment.
;; [^4]: The current top federal individual income tax bracket is [37%](https://taxfoundation.org/data/all/federal/historical-income-tax-rates-brackets/).
;; [^5]: ProPublica's [The Secret IRS Files: Trove of Never-Before-Seen Records Reveal How the Wealthiest Avoid Income Tax](https://www.propublica.org/article/the-secret-irs-files-trove-of-never-before-seen-records-reveal-how-the-wealthiest-avoid-income-tax) documents the low effective tax rates of the country's wealthiest households.
;; [^6]: The OECD's [The Role and Design of Net Wealth Taxes in the OECD](https://www.oecd.org/en/publications/the-role-and-design-of-net-wealth-taxes-in-the-oecd_9789264290303-en/full-report/component-6.html) discusses the valuation and liquidity challenges of taxing wealth.
;; [^7]: Wealth levels from [Federal Reserve Distributional Financial Accounts](https://www.federalreserve.gov/releases/z1/dataviz/dfa/distribute/chart/).
