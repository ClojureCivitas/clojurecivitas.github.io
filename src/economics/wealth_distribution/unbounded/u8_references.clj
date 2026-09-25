^:kindly/hide-code
(ns economics.wealth-distribution.unbounded.u8-references
  {:kindly/options {:static true
                    :kinds-that-hide-code #{:kind/var :kind/hiccup2 :kind/fn}}
   :clay {:title          "Unbounded: References"
          :quarto         {:sidebar "unbounded"
                           :author      [:timothypratley]
                           :description "Sources on economics, wealth, taxation, and progress"
                           :abstract    "Economic progress, wealth distribution, and taxation are connected questions that require careful research."
                           :date        "2026-06-20"
                           :type        :post
                           :category    :economics
                           :tags        [:tax :economics :growth]}}})

;; ## Why This Chapter Exists

;; The Unbounded series covers policy, economics, history, and philosophy.
;; This chapter lists the books, research, and datasets behind the arguments made.
;; Readers are encouraged to research the evidence and explore the source code to create their own visualizations and discoveries.

;; > "Some people use statistics as a drunken man uses a lamppost - for support rather than illumination." -- Andrew Lang

;; ## Books

;; **The Beginning of Infinity**, *David Deutsch*.
;; The philosophical foundation for treating progress as error correction and problem solving.
;; Supports the idea that institutions should help people generate and test better ideas.

;; **Economics: The User's Guide**, *Ha-Joon Chang*.
;; Presents economics as a collection of competing models rather than a single doctrine.
;; Supports comparing mechanisms and policy trade-offs.

;; **The Trading Game**, *Gary Stevenson*.
;; Offers a practitioner's view of modern financial markets.
;; Illustrates how financial markets diverge from household outcomes.

;; **Capital in the Twenty-First Century**, *Thomas Piketty*.
;; Provides a long-term view of wealth concentration and the role of taxation.
;; Informs the role of compounding, distribution, and stability.

;; ## Papers

;; **Wealth Inequality in the United States since 1913: Evidence from Capitalized Income Tax Data** (Saez and Zucman, 2016).
;; Establishes the capitalization method and provides a long-term series of U.S. wealth concentration.
;; [UC Berkeley repository PDF](https://eml.berkeley.edu/~saez/SaezZucman2015.pdf)

;; **Rethinking Capital and Wealth Taxation** (Piketty, Saez, and Zucman, 2023).
;; Combines comprehensive income taxation, inheritance taxation, and progressive wealth taxation in one framework.
;; [Manuscript PDF](https://gabriel-zucman.eu/files/PikettySaezZucman2023.pdf)

;; **The Effects of Wealth Taxation on Wealth Accumulation and Wealth Inequality** (Jakobsen, Jakobsen, Kleven, and Zucman, 2018).
;; Examines behavioral responses to wealth taxation using Danish data.
;; [Working paper portal](https://equitablegrowth.org/the-effects-of-wealth-taxation-on-wealth-accumulation-and-wealth-inequality/)

;; ## Data

;; **Saez-Piketty-Zucman distributional national accounts (DINA).**
;; A key source for distributional trends and tax-incidence analysis.
;; Start here: [Gabriel Zucman DINA page](https://gabriel-zucman.eu/usdina/)
;; Download tables directly: [Distributional tables](https://gabriel-zucman.eu/files/PSZ2022AppendixTablesII(Distrib).xlsx)
;;
;; **World Inequality Database (WID).**
;; Provides international comparisons and long-term inequality data.
;; [WID data portal](https://wid.world/data/)
;;
;; **Federal Reserve Distributional Financial Accounts (DFA).**
;; U.S. wealth distribution by percentile group.
;; [DFA explorer](https://www.federalreserve.gov/releases/z1/dataviz/dfa/)
;; [Raw DFA CSV zip](https://www.federalreserve.gov/releases/z1/dataviz/download/zips/dfa.zip)
;;
;; **Economic Policy Institute Productivity-Pay Tracker.**
;; Documents the divergence between productivity and typical worker compensation since the 1970s.
;; [Productivity-Pay Gap tracker](https://www.epi.org/productivity-pay-gap/)
;;
;; **FRED housing and income series.**
;; Public data for reconstructing housing affordability over time.
;; [Median Sales Price of Houses Sold for the United States (MSPUS)](https://fred.stlouisfed.org/series/MSPUS)
;; [Real Median Household Income in the United States (MEHOINUSA672N)](https://fred.stlouisfed.org/series/MEHOINUSA672N)
;;
;; **U.S. Treasury Fiscal Data.**
;; Sources for debt, deficits, receipts, and outlays.
;; [Debt to the Penny](https://fiscaldata.treasury.gov/datasets/debt-to-the-penny/debt-to-the-penny)
;; [Monthly Treasury Statement](https://fiscaldata.treasury.gov/datasets/monthly-treasury-statement/receipts-and-outlays)
;;
;; **Congress.gov bill texts for current wealth-tax proposals.**
;; Tax thresholds, rates, enforcement, and valuation rules.
;; [Ultra-Millionaire Tax Act of 2026 (S.4246)](https://www.congress.gov/bill/119th-congress/senate-bill/4246/text)
;; [Make Billionaires Pay Their Fair Share Act (S.3956)](https://www.congress.gov/bill/119th-congress/senate-bill/3956)
;;
;; **Pew Research Center tax attitudes.**
;; Provides nonpartisan polling on tax fairness and public concerns.
;; [Top tax frustrations for Americans (2026)](https://www.pewresearch.org/short-reads/2026/04/06/top-tax-frustrations-for-americans-feeling-that-some-wealthy-people-corporations-dont-pay-fair-share/)
;;
;; **ProPublica investigative reporting on ultrawealthy tax minimization.**
;; Documents strategies such as borrowing against appreciated assets instead of selling them.
;; [Ten Ways Billionaires Avoid Taxes on an Epic Scale](https://www.propublica.org/article/billionaires-tax-avoidance-techniques-irs-files)

;; ## Reports

;; **World Inequality Report** (World Inequality Lab).
;; A major cross-country analysis of income and wealth concentration.
;; [World Inequality Lab / WID portal](https://wid.world/)

;; **Taxation and Inequality** (OECD, 2024).
;; Examines tax design, inequality, and enforcement for high-net-worth individuals.
;; [OECD report PDF](https://www.oecd.org/content/dam/oecd/en/publications/reports/2024/07/taxation-and-inequality_b7cf450c/8dbf9a62-en.pdf)

;; ## Tech

;; These articles were written as Clojure notebooks, rendered with [Clay](https://scicloj.github.io/clay/) and Quarto.
;; Most of the visualizations were created using the [Plotje library](https://scicloj.github.io/plotje/).
;; These libraries are packaged in the [Noj toolkit for data science](https://scicloj.github.io/noj/).

;; ![Clojure's data science toolkit](noj.png){.fig-noj}

;; ## Critical Thinking and Why It Matters

;; Critical thinking means testing claims against evidence, definitions, and alternatives.
;; Ask what a number measures, what it leaves out, and what other explanations fit.
;; Inequality debates mix moral language, technical measurements, and political narratives.
;; Clear definitions, traceable evidence, and a willingness to revise claims help us distinguish convincing arguments from misleading ones.

;; ::: {.callout-note}
;; ## Storytime

;; I started looking into wealth concentration to help a friend have better conversations about it.
;; I expected to spend a few evenings reading, gather some useful facts, and move on.
;; *Instead, I fell down a rabbit hole.*

;; If you'd like to keep exploring wealth distribution,
;; these were the resources I found most useful.
;; :::

;; ## Conclusion

;; Knowledge grows when we share what we learn,
;; test ideas together, and build on each other's discoveries.
;; Read critically, follow the evidence, and explore the code.
;; You may discover something that changes how you see the world.
;; I hope these resources help you do exactly that.
