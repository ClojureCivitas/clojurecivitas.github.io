^:kindly/hide-code
(ns economics.wealth-distribution.unbounded.u0-introduction
  {:kindly/options {:kinds-that-hide-code #{:kind/var :kind/hiccup2 :kind/fn}}
  :clay {:title          "Unbounded: Progress, Economic Growth, and Society"
          :quarto         {:sidebar "unbounded"
                           :author      [:timothypratley]
                           :description "How wealth concentration prevents innovation, productivity, and long-run social progress."
                           :abstract    "Progress grows through the circulation of ideas, resources, and opportunity. Extreme wealth concentration disrupts circulation and limits our economic growth."
                           :date        "2026-06-20"
                           :type        :post
                           :category    :economics
                           :tags        [:tax :economics :growth]}}})

;; ## Unbounded Growth

;; In *The Beginning of Infinity*, David Deutsch explains that progress happens when we create better descriptions of how the world works.
;; A healthy society is one that never stops trying to improve.
;; While new problems will always appear, they can always be solved.
;; This requires us to actively clear the way for new ideas rather than just protecting what we already have.
;; *I believe the best way to do this is by making our thinking visible and working together.*

;; This series, *Unbounded*, explores how we can keep growth open-ended.
;; When everyone has a chance to participate in the economy we get more ideas, more productivity, and more opportunity.
;; Extreme wealth concentration blocks innovation and limits broad prosperity.

;; Politicians often talk about growth to justify policies that increase inequality.
;; Instead of funding new ideas, money tends to move toward the top and stay there.
;; When wealth is concentrated society is less able to adapt, experiment, and correct mistakes.

;; > "We may have democracy, or we may have wealth concentrated in the hands of a few, but we cannot have both." -- Louis Brandeis

;; High wealth concentration hurts the economy because the wealthiest buy assets rather than spending their money.
;; This reduces the circulation of resources needed for a healthy economy and makes the whole system more fragile.
;; Extreme inequality hurts our shared standard of living.
;; When we limit opportunity, we lose out on millions of people who could be solving our biggest problems.
;; If we ignore inequality, people lose trust in the future.
;; Such conditions stall and regress growth.

;; Growth is unbounded when there is a corrective mechanism against excessive concentration.
;; A wealth tax is that corrective measure.

;; ## Central Claims

;; I have divided the argument into separate posts so each idea can be examined clearly,
;; and so you can focus on the information most relevant to you.

;; These are the central claims made throughout the series:
;;
;; 1. **[Who Pays Tax](u1_who_pays_tax.html)** - The tax system taxes labor income more than asset growth.
;; 2. **[Wealth Tax](u2_wealth_tax.html)** - A wealth tax restores parity between labor income and asset compounding.
;; 3. **[Labor Hours](u3_labor_hours.html)** - Rising costs and weak wage progression are turning full-time work into survival maintenance, reducing mobility and prosperity.
;; 4. **[Interactive Factors](u4_interactive_factors.html)** - Wealth distribution is a root factor behind growth, resilience, and living standards.
;; 5. **[Wealth Concentration](u5_wealth_concentration.html)** - Wealth concentration is accelerating.
;; 6. **[Economic Growth](u6_economic_growth.html)** - Concentrated wealth suppresses growth by diverting capital away from broad demand and productive experimentation.
;; 7. **[How to Get a Wealth Tax](u7_how_to_get_a_wealth_tax.html)** - Wealth-tax legislation is only possible through political pressure.

;; ![Civilization relies on knowledge-sharing for unbounded growth](unbounded.svg){#fig-unbounded-series}

;; ::: {.callout-note}
;; ## Storytime

;; I spent the bulk of my career on projects that enriched companies at the expense of workers through automation.
;; I kept hearing "growth" and "creative destruction" used as if they justified everything, but I could not shake the feeling that something was missing.
;; My nephews and nieces have entered a workforce that offers none of the opportunities I had.
;; Humanity is on a long-term positive trajectory, driven by knowledge creation and sharing,
;; yet we are clearly self-inflicting unnecessary pain in the short term.

;; Reading *The Trading Game* by Gary Stevenson provided an epiphany: **we are ignoring distribution.**
;; From my background in maths, statistics, and systems modeling, I recognized how critical this error is.
;; When distribution is ignored, we fail to understand the situation.
;; As I investigated wealth distribution and the impact of this omission in financial models,
;; I was shocked to my core by how dramatically wealth has concentrated over my lifetime,
;; and how far it distorts our view of our economy.

;; To solve this and other crises we face, and to return to a truly positive long-term trajectory, **we need knowledge creation and sharing.**
;; The [SciCloj](https://scicloj.github.io/) and [ClojureCivitas](https://clojurecivitas.org/) habit of building models in public leads to shared, inspectable reasoning.
;; That motivates me to build diagrams and Clojure notebooks,
;; so I can see the mechanisms more clearly and communicate them to those willing to look.
;; :::

;; ## Start Here

;; In the first part, [Who Pays Tax](u1_who_pays_tax.html), we will examine how the current taxation system is unfair to you.
