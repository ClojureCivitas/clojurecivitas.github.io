^:kindly/hide-code
(ns economics.wealth-distribution.unbounded.u3-labor-hours
  {:kindly/options {:static true
                    :kinds-that-hide-code #{:kind/var :kind/hiccup2 :kind/fn}}
   :clay {:title          "Unbounded: Labor Hours"
          :quarto         {:sidebar "unbounded"
                           :author      [:timothypratley]
                           :description "How rising prices and weak wage growth push more households into survival mode."
                           :abstract    "Rising prices and weak wage growth are turning full-time work into survival maintenance, leaving less security, mobility, and time to build a better life."
                           :date        "2026-09-23"
                           :type        :post
                           :category    :economics
                           :tags        [:tax :economics :growth]}}}
  (:require [babashka.fs :as fs]
            [scicloj.plotje.api :as pj]
            [tablecloth.api :as tc]))

;; ## Time Is the Ultimate Currency

;; The most important resource for progress is the time we invest in the future.
;; The time we spend thinking, imagining, learning, creating, and building a life beyond survival.
;; That is where future wealth is created: in ideas, skills, families, and futures.
;; People need time to learn something,
;; start something,
;; care for children,
;; change careers,
;; build relationships,
;; experiment,
;; recover,
;; participate in their community,
;; and solve problems.

;; `time → human development → knowledge → wealth`.

;; We measure prosperity in income, GDP, productivity, and consumption.
;; But underneath all of them is a more fundamental question:
;; *how much of a person's effort must be spent securing the foundations of a good life?*

;; Throughout history, our collective technology and resources have grown astonishingly.
;; Yet many people still have little time available for learning, creating, and building a better future.
;; Compared with previous generations, many people today need to invest more time in survival.
;; The most direct observation of this is in the time invested in securing housing.
;; It takes longer now to secure a home than it did 50 years ago.

(def data-dir
  (doto (fs/path "src/economics/wealth_distribution/unbounded/data/")
    (fs/create-dirs)))

(def decennial-census-home-value
  "Transcribe from https://www2.census.gov/programs-surveys/decennial/tables/time-series/coh-values/values-unadj.txt"
  (-> (tc/dataset {:year [2000 1990 1980 1970 1960 1950 1940]
                   :median-house-value [119600 79100 47200 17000 11900 7354 2938]})))

(def acs-census-home-value
  "Transcribed from census.gov ACS survey table B25077 median home value,
   1-year estimates."
  (-> [2024 360600
       2023 340200
       2022 320900
       2021 281400
       2020 253600
       2019 240500
       2018 229700
       2017 217600
       2016 205000
       2015 194500
       2014 181200
       2013 173900
       2012 171900
       2011 173600
       2010 179900
       2009 185400
       2008 197400
       2007 194300
       2006 185200
       2005 167500]
      (->> (partition 2))
      (tc/dataset)
      (tc/rename-columns [:year :median-house-value])))

(def median-house-prices
  (-> (tc/concat decennial-census-home-value acs-census-home-value)))

#_(-> median-house-prices
    (pj/lay-line :year :median-house-value))

(def f01ar-census-income
  "Download from https://www2.census.gov/programs-surveys/cps/tables/time-series/historical-income-families/f10ar.xlsx"
  (-> (tc/dataset (str (fs/path data-dir "f01ar.xlsx"))
                  {:dataset-name "family income"
                   :n-initial-skip-rows 9
                   :header-row?         false})
      (tc/drop-missing)
      (tc/head 80) ;; Only looking at the first table
      (tc/rename-columns [:year :n-k :lowest :second :third :fourth :top5])
      (tc/update-columns {:year (partial map #(-> %
                                                  (str)
                                                  (subs 0 4)
                                                  (Long/parseLong)))})
      (tc/add-columns {:median-household-income (fn [ds]
                                                  (map #(/ (+ %1 %2) 2.0)
                                                       (ds :second)
                                                       (ds :third)))})
      ;; backfill 1940 from https://www.nytimes.com/1943/04/30/archives/family-income-at-1231-this-was-median-figure-in-1939-census-bureau.html
      (tc/concat (tc/dataset {:year 1940
                              :median-household-income 1231}))))

#_(-> f01ar-census-income
    (pj/lay-line :year :median-household-income)
    (pj/options {:x-tick-angle -90}))

^{:kindly/options {:id :fig-home-value-to-income
                   :caption "Securing housing requires more time today. Family income excludes single-person households and unrelated cohabitants. Home values include only owner-occupied units, not the rental market. [^1]"}}
(-> (tc/left-join median-house-prices f01ar-census-income :year)
    (tc/add-columns {:ratio (fn [ds]
                              (map /
                                   (ds :median-house-value)
                                   (ds :median-household-income)))})
    (pj/lay-line :year :ratio)
    (pj/scale :y {:include 0})
    (pj/options {:title "Median Home Value to Median Family Income Ratio"
                 :x-label "Year"
                 :y-label "Ratio"}))

;; The labor time required to own a house far exceeds the ratio.
;; If you are only able to save 5% of your income, then the time required is at least 20x the ratio.
;; The bottom 90% of earners save roughly 4% of their income. [^2]
;; Typically one would rent while saving up for a deposit, take out a mortgage,
;; then pay interest and principle over 30 years.
;; The ratio is a way to compare the relative labor investment required in the past to now.
;; In the 70s, a family saving at 5% could buy a home with a 10% deposit after 4 years
;; Now it takes 6 years to save for a deposit.
;; Essentially the labor time required to own a home is 50% higher today,
;; regardless of the exact way you go about obtaining the home.
;; It takes working longer today to secure a home than it did in the past.

;; We can see the direct effects of this in the age of first time buyers,
;; and whether mortages are paid off before retirement. [^3]


;; | Era   | First Buy | Mortgage Ends | Years Debt-Free Before Retirement (65) |
;; | ----- | --------- | ------------- | -------------------------------------- |
;; | 1990s | Age 28    | Age 58        | 7 years                                |
;; | 2020s | Age 40    | Age 70        | –5 years (still paying)                |
;;
;; : First-time homebuyer timing by era {#tbl-homebuyer-timing}

;; Today, the median home owner needs more time to save for a deposit and will still have a mortgage when they retire.

;; ## The Squeeze in Hours

;; Many households are working hard and still falling behind.
;; Paychecks cover rent, food, and bills, but not much else.
;; There is little left for savings, investment, or emergencies.
;; Full-time work only buys survival, not progress.

;; > "The trouble with the rat race is that even if you win, you're still a rat." -- Lily Tomlin

;; Wages, housing, and basic services do not all rise together.
;; Fixed resources get bid up by people with surplus capital.
;; The result is lower affordability and lower social mobility.
;; People experience rent pressure, delayed family formation, and debt dependence.
;; Affordability is a crisis for many individuals even when aggregates look fine.

;; Above a certain income level, people can buy assets and compound wealth.
;; Below that level, people are mostly trying not to fall behind.
;; At the top, wealth is held in assets that continue to compound.
;; At the bottom, nearly all income is committed to consumption.

;; *What is the threshold where people can accumulate home equity and retirement savings?*

;; The answer varies widely with geography, household size, housing situation, and other factors.
;; According to the SmartAsset Salary Needed to Live Comfortably Study (2026),
;; a **single adult** needs to earn at least $80K to live comfortably,
;; and the threshold exceeds $100K in nearly half of the states,
;; reaching $150k in the most expensive cities.[^4]
;; The median individual income was $45K in 2024 for all individuals age 15+.[^5]
;; 16.7% of individuals earned over $100K in 2024.
;; *This suggests that roughly 80% of **individuals** earn less than the income needed for a comfortable life.*

;; For **families**, the median family income is $105K,
;; while living comfortably requires earning $190K,
;; the threshold exceeds $200K in 40 states,
;; reaching $410K in the most expensive cities.
;; $207k is the top 20% of family incomes.[^6]
;; *This suggests that roughly 80% of **families** earn less than the income needed for a comfortable life.*

;; ## The Golden Age of Capitalism

;; Prosperity does not require extreme wealth concentration.
;; The period 1945-1985 was one of the strongest periods economically and socially.
;; Many households could afford a rising standard of living.
;; Growth was broadly shared.
;; It was a rare period in history.
;; High taxation and broad public investment coexisted with strong growth.

;; Top income tax rates during this era ranged from 70% to 91%.
;; Yet economic growth remained robust because that tax revenue funded infrastructure, research, and education that improved productivity.
;; The economy flourished because capital was cycled through broad-based investment. [^7]

;; The effective tax rate on the wealthiest was higher, but so was their return on invested capital because public goods were abundant.
;; High taxes paired with productive public investment creates a positive feedback loop.

;; ## What Changed

;; From the 1980s onward, *the rules changed.*
;; Tax policy became more favorable to capital and high incomes.
;; More wealth accumulated among people who already owned assets.
;; That wealth competed for a limited supply of land, housing, and other assets, pushing prices higher.
;; At the same time, labor bargaining power weakened.
;; Productivity rose, but worker compensation did not keep pace.
;; The gains from economic growth flowed increasingly toward asset owners.

(def productivity-pay-gap
  (-> [1948  	100	100
       1949	102.2	105.4
       1950	108.9	111.8
       1951	111.5	110.2
       1952	114.3	114.4
       1953	119.9	119.6
       1954	120.3	123.7
       1955	128.8	128.4
       1956	132.4	134.2
       1957	135.9	136.4
       1958	135	137.5
       1959	143.4	142
       1960	148	144.3
       1961	146.6	147.6
       1962	155.7	151
       1963	160.7	154.7
       1964	167.4	158
       1965	171.1	161.6
       1966	179.3	165.1
       1967	180.1	166.8
       1968	186.3	170.6
       1969	189.7	175.1
       1970	190.1	176.4
       1971	200	181.1
       1972	204.7	189.6
       1973	214.8	193.8
       1974	207.3	188.1
       1975	209.3	186.9
       1976	216.6	187.3
       1977	219.4	191.5
       1978	219.5	195
       1979	221.7	195.1
       1980	215.7	188.3
       1981	217.9	186.8
       1982	215.4	187.3
       1983	218.7	189.2
       1984	225.5	188
       1985	229.9	187.2
       1986	234.2	187.4
       1987	234.5	187.8
       1988	237.6	186.3
       1989	239.7	186.8
       1990	241.6	186.8
       1991	238.9	184.5
       1992	249	186.7
       1993	253.4	188.3
       1994	255.6	188.8
       1995	255	187.5
       1996	258.3	187.6
       1997	259.3	188.3
       1998	265.7	192.9
       1999	273.8	196.9
       2000	273.2	196.9
       2001	278.4	200.4
       2002	290.7	206.3
       2003	294.2	209.2
       2004	307.3	210
       2005	316.2	210.2
       2006	319.1	209.6
       2007	321.4	212.5
       2008	319.2	212.6
       2009	327.4	222.4
       2010	338.7	224
       2011	341	223.7
       2012	340.1	219.9
       2013	342.4	221.4
       2014	344.2	223.7
       2015	352.1	228.1
       2016	354.6	231.5
       2017	357.8	231.2
       2018	364	233.2
       2019	367.3	237.7
       2020	373.2	241
       2021	394.5	248.4
       2022	391.6	244.3
       2023	391.9	243.5
       2024	401.1	247.9
       2025	406.9	251.9
       2026	418.8	254.9]
      (->> (partition 3))
      (tc/dataset)
      (tc/rename-columns [:Year :Productivity :Pay])))

^{:kindly/options {:caption "Since 1982, productivity has outpaced pay by nearly three to one.[^8]"
                   :id :fig-productivity-pay-gap}}
(-> productivity-pay-gap
    (pj/lay-line :Year :Productivity {:color "green"})
    (pj/lay-line {:color "blue" :y :Pay :overlay true})
    (pj/options {:title "The Productivity-Pay Gap"
                 :y-label "Index"})
    (pj/lay-rule-v {:x-intercept 1982})
    (pj/lay-rule-v {:x-intercept 2026 :color "red"})
    (pj/plot)
    (into [[:text {:x 400 :y 110 :fill "green"} "Productivity"]
           [:text {:x 425 :y 265 :fill "blue"} "Pay"]
           [:text {:x 530 :y 160 :fill "red"} "39%"]
           [:text {:x 255 :y 250} "13%"]]))

;; Productivity measures economic output per hour of work.
;; From 1948 until 1982, worker pay rose in step with productivity.
;; Since 1982, productivity has continued to rise, but worker compensation has lagged behind.
;; An increasing share of the gains from productivity has gone to owners rather than workers.

;; Productivity gains increase wealth for those who already own assets.
;; Asset owners experience rising asset prices as wealth.
;; People without assets receive a smaller share of economic gains while paying more to acquire the assets they need.
;; People without assets experience rising asset prices as rising costs.

;; Housing is the clearest manifestation of this.
;; Home prices have risen faster than median household income, making homes increasingly expensive relative to what people earn.

;; ## How We Got Here

;; Tax policy choices favored capital over labor.
;; Over decades, policy choices changed the distribution of wealth and bargaining power.
;; Yet political debate focuses on the symptoms rather than the structure.
;; Politicians argue about culture, identity, immigration, crime, inflation, and short-term relief.
;; These issues matter, but they also distract from a simpler question:
;; *Who owns the assets, and who participates in economic growth?*

;; Lower taxes on the wealthy and weaker institutions are allowing more of the gains from growth to concentrate.
;; We built a system in which wealth compounds faster for those who already own it than ordinary wages can grow for those who do not.
;; Wealth continues to flow upward, concentrating ownership and political power into fewer hands.
;; We got here through choices.
;; We can get somewhere else through choices too.

;; ::: {.callout-note}
;; ## Storytime

;; When I was a kid, I could spend an entire afternoon playing Taipan,
;; a trading game where I sailed between ports, bought cargo, avoided pirates, and tried to build my fortune.
;; I never looked at the clock.
;; But when my Mum asked me to spend five minutes cleaning up my room, suddenly time moved differently.
;; Five minutes felt endless.
;; The value of time depends on what that time is buying you.

;; These days I worry about my nephews and nieces.
;; They face more challenges than my brothers and I ever had.
;; How can that be, when the economy and technology have advanced so far?
;; Surely the younger generation should have it easier, with better prospects?
;; It wasn't until I looked at the labor costs of essentials that I realized what they are up against.
;; We really did have it easier.
;; The ratios changed.
;; Each hour worked buys less.
;; Someone can work the same 40-hour week as their parents did,
;; while their effective labor burden is higher because essentials consume more of their income.
;; My generation always had plenty of time to build the lives we wanted.
;; The next generation has only five minutes to spare.

;; My nephew now has a son.
;; When I think about his future, I don't want to imagine a world where hard work is no longer enough to build a secure life.
;; There isn't much time left before ownership and security become out of reach for an entire generation.
;; A future where people work hard their entire lives and die with nothing is not a future we should accept.
;; That generation will be left with only two options: radical, destructive change, or a lifetime of resignation.
;; We must find an alternative.

;; Time is precious.
;; A good society should give people time for exploration, creation, family, and growth.
;; When people are forced to spend all their time maintaining survival, society loses human potential.
;; A society that traps people in maintenance mode wastes human capability.
;; :::

;; ## The Social Cost

;; When more people are trapped in survival mode, society spends less energy on innovation and more on defense.
;; That is a direct cost to long-term growth.
;; When essential costs consume more of our income, they also consume more of our time.
;; A society that cannot spare people, time, or capital for experimentation will solve fewer problems.

;; In the next part, [Interactive Factors](u4_interactive_factors.html), we look at where inequality fits in the broader set of crises we face.

;; [^1]: Home values are median values for owner-occupied homes from the U.S. Census Bureau's
;;       [decennial census historical series for 1940-2000](https://www2.census.gov/programs-surveys/decennial/tables/time-series/coh-values/values-unadj.txt) and American Community Survey table B25077
;;       one-year estimates for 2005-2024. Family income is the midpoint of the Census Bureau's
;;       second- and third-income quintiles from [Historical Income](https://www2.census.gov/programs-surveys/cps/tables/time-series/historical-income-families/f10ar.xlsx) Table F-1, covering 1940-2024.
;; [^2]: See Saez, Emmanuel, and Gabriel Zucman. ["Wealth Inequality in the United States since 1913: Evidence from Capitalized Income Tax Data."](https://eml.berkeley.edu/~saez/SaezZucman2015.pdf) Quarterly Journal of Economics, 2016.
;; [^3]: National Association of Realtors [First-Time Home Buyer Share and Median Age](https://www.nar.realtor/press-releases/first-time-home-buyer-share-falls-to-historic-low-of-21-median-age-rises-to-40).
;; [^4]: SmartAsset, ["Salary Needed to Live Comfortably in the US: 2026 Edition,"](https://smartasset.com/data-studies/heres-the-salary-it-takes-to-live-comfortably-in-each-u-s-state-in-2026).
;;       State-level analysis for single adults and families of four using the 50/30/20 budget rule.
;; [^5]: U.S. Census Bureau, Current Population Survey, [PINC-01](https://www.census.gov/data/tables/time-series/demo/income-poverty/cps-pinc/pinc-01.html),
;;       "Selected Characteristics of People 15 Years and Over, by Total Money Income," 2024.
;; [^6]: U.S. Census Bureau, Historical Income Tables: Families, Table F-1,
;;       ["Income Limits for Each Fifth and Top 5 Percent of Families," 2024](https://www.census.gov/data/tables/time-series/demo/income-poverty/historical-income-families.html).
;; [^7]: See [Historical US Federal Individual Income Tax Rates & Brackets, 1862-2025](https://taxfoundation.org/data/all/federal/historical-income-tax-rates-brackets/) for the postwar top marginal rate history and the broader context of the mid-century tax regime.
;; [^8]: Series from EPI's [Productivity-Pay Tracker](https://www.epi.org/productivity-pay-gap/),
;;       based on Bureau of Labor Statistics data. It compares net productivity for the total economy
;;       with compensation (wages and benefits) for private-sector production and nonsupervisory workers.
;;       Both series are indexed to 1948 = 100. [Data and methodology](https://github.com/Economic/productivity_pay_gap).
