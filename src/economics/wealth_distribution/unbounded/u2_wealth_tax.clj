^:kindly/hide-code
(ns economics.wealth-distribution.unbounded.u2-wealth-tax
  {:kindly/options {:static true
                    :kinds-that-hide-code #{:kind/var :kind/hiccup2 :kind/fn}}
   :clay {:title          "Unbounded: Wealth Tax"
          :quarto         {:sidebar "unbounded"
                           :author      [:timothypratley]
                           :description "How wealth tax proposals work, who would pay, and how much."
                           :abstract    "Taxing wealth above $50M restores tax parity between labor income and asset compounding."
                           :date        "2026-09-22"
                           :type        :post
                           :category    :economics
                           :tags        [:tax :economics :growth]}}}
  (:require [babashka.fs :as fs]
            [scicloj.plotje.api :as pj]
            [tablecloth.api :as tc]))

;; ## What Is a Wealth Tax?

;; A wealth tax is a tax on net assets above a high threshold.
;; If the threshold is \$50M, then a \$60M household is taxed only on the \$10M above the threshold.
;; The problem it fixes is that income tax covers work income, but not wealth growth that exceeds income.
;; Workers pay tax when they receive a paycheck.
;; The very wealthy invest in assets that grow, and that growth is not taxed as income.
;; We aren't talking about ordinary retirement wealth, but massive, snowballing wealth.
;; A wealth tax applies where income tax doesn't.

;; Beyond fairness, a wealth tax is the main mechanism that can limit extreme [wealth concentration](u5_wealth_concentration.html).

;; > "The hardest thing in the world to understand is the income tax." -- Albert Einstein

;; To get a more concrete picture of proposed wealth taxes:

;; | Feature | Zucman [^1] | Warren [^2] | Sanders [^3] | Biden [^4] |
;; | :--- | :--- | :--- | :--- | :--- |
;; | **Tax Base** | Total Wealth | Total Wealth | Total Wealth | Public Stock Growth [^5] |
;; | **Rate Structure** | >\$1B @ 2% | >\$50M @ 2% <br> >\$1B @ 3% | >\$1B @ 5% | >\$100M @ 25% |
;; | **Income Tax Relation** | Offset | Additive | Additive | Pre-Payment [^7] |
;; | **Expected Revenue** | \$230B | \$560B | \$440B | \$90B [^8] |
;;
;; : Proposed wealth tax designs {#tbl-wealth-tax-designs}

;; **If you own less than $50M in assets, you will not pay a wealth tax.**
;; The thresholds sit well above what one can save through work income alone.
;; If you hope to one day to achieve a net worth of more than \$50M, that future is not limited by a wealth tax.

;; You don't pay wealth tax, you benefit from it.
;; If you pay tax, you are paying more tax than you should be.
;; For households below the threshold, fixing inequality improves opportunities and living standards.
;; For households above the threshold, fixing inequality is the path to preserving wealth in a stable system.

;; Extreme wealth concentration weakens consumer demand, democratic legitimacy, innovation, and broad opportunity.
;; Workers should not face higher effective rates than ultra-wealthy asset holders.

;; Fixing inequality is good for everyone.
;; The wealthiest gain a more stable society, safer estates, and a healthier long term growth path.
;; Professionals can carrying less of the total tax burden.
;; The middle benefit from better access to assets.
;; The bottom can have better living standards.

;; *Public support for a wealth tax is between 60% to 80% in surveys.*
;; The support is broad, including among Republicans.[^9]
;; Yet there has been little action toward taxing wealth.
;; Instead, **tax cuts for the wealthy** continue to be enacted.
;; Partisan politics are used to distract from the basic issue of who pays tax.
;; I suspect public demand for a wealth tax would be even higher if more people realized how it benefits them,
;; and how much the ultra-wealthy are avoiding their share, as we explored in ["Who pays tax"](u1_who_pays_tax.html).

;; ## Why Does It Matter?

;; Only a society that embraces positive change can survive.
;; It's tempting to think that something as boring as taxation can't be a threat to our survival.
;; Our tax system is currently set up to concentrate wealth which has many dangerous consequences,
;; which we'll examine in more detail in subsequent articles.

;; For now, let's look at one particular danger that faces us; debt.
;; The government debt problem is serious.
;; U.S. government debt is around $40T, and budget deficits are increasing.[^10]
;; Tax revenue was $5T.
;; Of that $1T went directly to debt servicing.
;; **Of the tax you pay, 20% goes straight to treasury holders.**
;; The remainder of spending was $6T on public functions; social security, healthcare, defence, income security.
;; That leaves a deficit of $2T which is added to the outstanding debt.

;; The government is borrowing to maintain existing commitments,
;; while an increasing share of future revenue is already promised to creditors.
;; As debt service costs rise, they crowd out public services and investment.
;; The government will be forced into a hard adjustment:
;; benefit cuts, higher taxes on workers, inflationary finance, wealth taxation, or default.
;; A wealth tax does not remove the need for responsible budgeting,
;; but it makes the adjustment less dependent on worker taxation.

(def data-dir
  (doto (fs/path "src/economics/wealth_distribution/unbounded/data/")
    (fs/create-dirs)))

^{:kindly/options {:id :fig-federal-government-net-worth
                   :caption "U.S. federal government net worth has fallen from roughly negative $0.1T in 1945 to negative $25.3T in 2025. Federal net worth is not the same as gross government debt, but the long decline shows that public liabilities have grown far beyond the government's financial assets.[^11]"}}
(-> (tc/dataset (str (fs/path data-dir "federal-government-net-worth.csv")))
    (tc/update-columns {"Value" (partial map #(/ % 1000000000000.0))})
    (pj/pose)
    (pj/options {:x-label "Date"
                 :y-label "$ Trillions"
                 :title "US Federal Government Net Worth"}))

;; Because of the debt, the government net worth is deeply negative,
;; and the trend has worsened for decades.
;; The government has less and less capacity to maintain the foundations of a healthy society: public services, democratic institutions, shared infrastructure, crisis response, and investment in the future.

;; When a small number of people own most of the assets,
;; everyone else must pay more to access housing, businesses, and investment.
;; A tax on extreme fortunes limits the ability of enormous fortunes to compound without limit.

;; We need a fair tax system.
;; We need to slow the feedback loop that turns wealth into more wealth and influence.
;; We need a society with more room to respond to shared problems.

;; ## Objections

;; What are the main objections and risks associated with a wealth tax?

;; Despite being a fairly obvious concept, objections are raised.
;; Some are direct challenges to the mechanism; others are redirects toward adjacent policies that do not solve the taxation problem.

;; ### Fix Loopholes Instead

;; Fixing loopholes is important, but the scale matters.
;; There is not a trillion dollars of revenue hiding in loopholes.
;; Income from work is taxed much more heavily than wealth growth.
;; Billionaires are using the system correctly as it is currently written.

;; Estate, corporate, and capital gains taxes can all help.
;; They are useful, and deserve our support.
;; The Biden proposal was a fine change to capital gains taxation, but by itself it does not get billionaires paying tax at anything like the rate high earners pay.
;; The main difference between a "wealth tax" and a "wealth growth tax" is that the former can cap extreme wealth,
;; while the later can only slow it.

;; The real risk is distraction and division.
;; Improvements to existing tax law are complementary.
;; The problem that needs addressing is the distribution of wealth and taxation.
;; The clear, direct path includes a wealth tax.

;; ### Doesn't Work / Too Hard

;; Yes, it is hard, but necessary.
;; The alternatives are prolonged stagnation, crisis, weaker democracy, and eventual social rupture.

;; We can be confident it will work because of history.
;; The "golden age of capitalism" occurred when top tax rates were much higher, as we will examine in ["Labor Hours"](u3_labor_hours.html).[^12]
;; More recently, research from Denmark finds that wealth taxes reduced wealth concentration at the top.[^13]

;; Changing the system will require work.
;; The real risk is failing to do the work required to solve this problem.

;; ### Over-Regulation / Big Government

;; Wealth tax is sometimes conflated with government spending.
;; That is unnecessary and makes implementation harder.
;; Taxation and spending can each be as large or small as you prefer.
;; What matters is who gets taxed, and at what rate.
;; Whatever your political stance on government, you need a fair tax system.

;; The real risk is that wealth tax remains politicized and blocked.

;; ### Exodus

;; The assets are here.
;; U.S. citizens remain subject to U.S. tax law.
;; Billionaires can leave if they like, but they will still be obliged to pay their taxes.
;; Reporting requirements, exit taxes, and international cooperation can strengthen enforcement.
;; Moreover, mobility and opportunity are what attract future billionaires.
;; *Will the best come to a stagnant place, or somewhere they can grow?*

;; The real risk is losing our role as a destination for talent.

;; ### Disorderly Liquidation

;; Imagine a wealth tax was implemented today.
;; Billionaires might need to sell some assets.
;; Would this cause an economic collapse?
;; The feared catastrophe is that selling would cause a bubble to pop and asset prices to fall.
;; Any such bubble is the result of overvalued assets, not taxation.
;; Revaluation toward fair value is a good thing.
;; Assets shift from the 0.1% to the broader population, who become buyers.
;; A possible outcome of a bubble pop is wage cuts.
;; That is a labor market question, not a reason to preserve an unfair tax system.

;; The real risk is turmoil, and indeed a wealth tax must be introduced carefully.

;; ::: {.callout-note}
;; ## Storytime

;; We all hope to retire rich. I certainly do.
;; When I first heard about wealth taxes, my immediate question was: *Would this affect me?*
;; Seeing the proposed thresholds I realized I'll never have the fortune to worry about that.
;; That led to another question: *Do I know anyone who would?*

;; By definition, 1 in 100 households are in the top 1%, with around \$13M in wealth.
;; Surely I should know a few people who made it?
;; Across seven companies, I have worked closely with roughly 200 colleagues.
;; Perhaps one or two that are far wealthier than I realize.
;; Yet I would be surprised if even one of them have reached that level.
;; People who reach \$13M through work alone are exceptional.
;; As for \$50M, the minimum proposed wealth tax threshold, I really don't think there is anyone in my circles.

;; Our social circles aren't random samples of society.
;; People with extraordinary wealth inhabit different neighborhoods, different schools, different networks.
;; Fortunes of that size are only possible from inheritance, long-term business ownership, and large investment portfolios.
;; We are not talking about people who have high salaries, we are talking about people whose relationship to the economy is fundamentally different.
;; *A wealth tax does not tax high earners, rather it taxes extraordinary concentrations of accumulated wealth.*

;; The richest person I've ever encountered was the billionaire chairman and majority owner of the parent company to my employer.
;; Every quarter he would arrive in his luxury motorboat,
;; visit his corner office in the corporate headquarters next door to our building and host a meeting in the boardroom.
;; We were told to stay out of the corporate headquarters building on those days,
;; a rule I broke because I liked using the corporate gym at lunchtime.
;; I never met the chairman,
;; but I knew he was visiting because my boss rushed over to pull me out of the gym when my boss's boss spotted me there on his way to the boardroom.

;; By all accounts, the chairman was a highly respected, family-oriented, and well liked person.
;; He married into the family that founded the company, modernized and expanded it.
;; Under his leadership, the business grew to more than 120 operations globally,
;; becoming the largest privately held container terminal operator in the world.
;; He is exactly the kind of person a wealth tax applies to.
;; His fortune represents extraordinary success,
;; and I have no doubt that paying a modest tax on that wealth would leave him, and the company, immensely prosperous.
;; :::

;; ## Conclusion

;; A wealth tax fixes a hole in the tax system, making it fairer to you.
;; That hole is gigantic, and has very serious consequences.
;; You are paying too much tax and getting too little for it.
;; A wealth tax is not inherently scary or political.
;; Most counterarguments seek to distract away from the core problems of wealth and taxation distribution.

;; In the next part, [Labor Hours](u3_labor_hours.html), we look closer at how to quantify a good economy from an individual's perspective.

;; [^1]: [Gabriel Zucman Blueprint](https://www.gov.br/g20/pt-br/trilhas/trilha-de-financas/tributacao_internacional/2-blueprint-for-a-coordinated-minimum-effective-taxation-standard-for-ultra-high-net-worth-individuals-gabriel-zucman.pdf/@@download/file) (The global G20 standard proposal).
;; [^2]: [Elizabeth Warren Ultra-Millionaire Tax Act of 2026](https://www.congress.gov/bill/119th-congress/senate-bill/4246/text) (Official Legislative Bill Text).
;; [^3]: [Bernie Sanders Make Billionaires Pay Their Fair Share Act of 2026](https://www.congress.gov/bill/119th-congress/senate-bill/3956/text) (Official Legislative Bill Text).
;; [^4]: [Biden Billionaire Minimum Income Tax Act](https://www.congress.gov/bill/118th-congress/house-bill/6498/text) (Official Legislative Text).
;; [^5]: **Growth vs. Base**: Rather than taxing the entire pool of assets every year, Biden's proposal redefines paper asset growth (unrealized capital gains) as taxable income.
;; [^6]: **Why it is Flat**: Biden's proposal does not use graduated brackets. Anyone worth over \$100 million simply faces a uniform 25% minimum tax requirement on their total combined income and growth.
;; [^7]: **Pre-Payment Mechanism**: Payments made on paper growth function as an advance credit. When the asset is eventually sold, the billionaire does not owe duplicate capital gains tax on the growth they already paid for.
;; [^8]: **Revenue Restraints**: While a pure 25% growth tax implies ~\$250B+ in annual yields, Biden's legislative text limits annual collection to \$120B by exempting private/illiquid assets from annual tracking and allowing billionaires to pay out their growth balances via 5-to-9 year installment plans.
;; [^9]: “Impose an extra annual tax of 2% on wealth over $50 million, and 3% on wealth over $1 billion. This proposal would reduce the deficit by $200 billion a year. What is your recommendation?” Charge wealth tax: National 78%, Republicans 72%, Democrats 83% [Common Ground of the American People](https://publicconsultation.org/cgoap/).
;;       A 2024 polling roundup at [Inequality.org](https://inequality.org/article/extensive-polls-find-americans-support-taxing-the-wealthy/) reports that more than three out of five Americans supported a wealth tax across aggregated national polls.
;;       Pew found in 2026 that [61% of Americans say it bothers them a lot that wealthy people do not pay their fair share](https://www.pewresearch.org/short-reads/2026/04/06/top-tax-frustrations-for-americans-feeling-that-some-wealthy-people-corporations-dont-pay-fair-share/).
;; [^10]: The Treasury's official [Debt to the Penny dataset](https://fiscaldata.treasury.gov/datasets/debt-to-the-penny/debt-to-the-penny) reports total public debt outstanding.
;;        See also FRED's [Federal Debt: Total Public Debt (GFDEBTN)](https://fred.stlouisfed.org/series/GFDEBTN) and [Federal Surplus or Deficit (FYFSD)](https://fred.stlouisfed.org/series/FYFSD).
;; [^11]: Macrotrends [Federal Government Net Worth](https://www.macrotrends.net/4721/federal-government-net-worth).
;; [^12]: Historical federal top marginal income tax rates stayed far above current levels through much of the mid-20th century; see [Historical US Federal Individual Income Tax Rates & Brackets, 1862-2025](https://taxfoundation.org/data/all/federal/historical-income-tax-rates-brackets/).
;; [^13]: Jakobsen, Jakobsen, Kleven, and Zucman, [The Effects of Wealth Taxation on Wealth Accumulation and Wealth Inequality](https://equitablegrowth.org/the-effects-of-wealth-taxation-on-wealth-accumulation-and-wealth-inequality/), provides long-run micro evidence from Denmark on wealth tax responses.
