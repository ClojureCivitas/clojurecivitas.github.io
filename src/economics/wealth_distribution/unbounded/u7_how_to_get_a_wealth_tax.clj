^:kindly/hide-code
(ns economics.wealth-distribution.unbounded.u7-how-to-get-a-wealth-tax
  {:kindly/options {:static true
                    :kinds-that-hide-code #{:kind/var :kind/hiccup2 :kind/fn :kind/graphviz}}
   :clay {:title          "Unbounded: How to Get a Wealth Tax"
          :quarto         {:sidebar "unbounded"
                           :image   "path_to_law.svg"
                           :author      [:timothypratley]
                           :description "How pressure can lead to wealth tax legislation."
                           :abstract    "Wealth tax legislation needs single policy political pressure."
                           :date        "2026-09-27"
                           :type        :post
                           :category    :economics
                           :tags        [:tax :economics :growth]}}})

;; # Political Priority

;; A policy can be popular and still not become law.
;; People generally support taxing the wealthy more fairly,[^1]
;; yet the actual policy agenda is still dominated by tax cuts and carve-outs for the very rich.[^2]

;; > "Politics is the art of looking for trouble, finding it everywhere, diagnosing it incorrectly and applying the wrong remedies." -- Groucho Marx

;; It's important.
;; If we don't succeed, the middle class will run out of money,
;; our government will run out of money,
;; and what comes after will be an unnecessary upheaval.
;; Unbounded growth requires a fairer tax system.

;; ## Popularity Is Not Enough

;; Popular support for a wealth tax continues to fail to translate into political action.
;; The major parties continue to campaign on derivative issues instead.

;; ::: {#fig-path-to-law .graphviz fig-align="center"}

^:kind/graphviz
["digraph support_to_law {
  graph [rankdir=TB, bgcolor=\"transparent\", nodesep=0.25, ranksep=0.55]
  node [shape=invtrapezium, style=filled]

  support [label=\"Public Support\"]
  priority [label=\"Priority in Campaign\"]
  vote [label=\"Votes\"]
  pressure [label=\"Public Pressure\"]
  majority [label=\"Legislative Majority\"]
  law [label=\"Enacted Law\", width=1.6, fillcolor=\"#bfdbfe\"]

  support -> priority
  priority -> vote
  vote -> pressure
  pressure -> majority
  majority -> law
}"]

;; The path to law requires sustained pressure.
;; :::

;; Wealth tax proposals should be bi-partisan.
;; Tax policy addresses system stability, fair contribution, and productive growth.
;; Every party should have answers on these issues.
;; It requires sustained constituent pressure to keep the focus on distribution.

;; Concentrated wealth buys influence, patience, and narrative control.
;; Politicians often avoid talking about tax policy.
;; Politicians prefer symbolic fights over structural fights because the structural fights threaten existing power.

;; ## Pressure

;; Popular support is not enough to create political accountability.
;; Consider the Ultra-Millionaire Tax Act of 2026.[^3]
;; Senator Elizabeth Warren introduced the bill with 10 Senate cosponsors.
;; It was referred to the Finance Committee on March 26, 2026.
;; The bill has not received a Senate vote.
;; Its progress depends on decisions made by congressional leadership and the committee.

;; The government has the power to implement a wealth tax.
;; Congress has procedures for advancing legislation held in committee.
;; Budget reconciliation can expedite qualifying tax legislation.
;; A House discharge petition can bring a bill to the floor.
;; Congress must pass the legislation.
;; But these powers are not being put to use.

;; We can identify the 11 senators who supported the Ultra-Millionaire Tax Act of 2026.
;; But what about the other 89?
;; What alternative proposals have they introduced?
;; What action have they taken toward fairer taxation?
;; Where can voters see their positions?
;; Their positions are not equally visible.
;; We may never get a recorded vote or a clear explanation of why action did not occur.

;; A wealth tax needs more than public support.
;; It needs politicians to make it a legislative priority.
;; We, the voters, need to hold them accountable.

;; ## One Policy

;; Single issue movements have won in the past by changing incentives.
;; The Anti-Slavery movement used swing blocs.
;; The Anti-Saloon League used one metric across party lines.
;; Focused pressure was able to overcome government inaction.

;; The demand has to be simple and hard to evade.
;; Voters should ask one question:
;; *Did this politician help move wealth taxation forward?*

;; Affordability, mobility, stagnation, and instability all point back to distribution.
;; Distribution is the root problem, and wealth taxation has to be a top priority.

;; **Research.** Learn the arguments.
;; **Share.** Use facts and comparisons.
;; **Vote.** Hold politicians accountable for inaction.
;; Be inclusive, patient, and focused.

;; We need to be clear about what we want, not who we want to deliver it.
;; No single politician can do this alone.
;; But a clear policy goal can do what charisma cannot:
;; *it gives people something specific to coordinate around, something concrete to reward or oppose.*

;; ::: {.callout-note}
;; ## Storytime

;; When I was onsite implementing shipping container handling automation, I could never get my way.
;; I had ideas about how things should work, but I had no influence.
;; I assumed that if I had the best ideas, people would naturally listen.
;; They didn't.

;; A rogue of a man, a ship's captain turned technologist,
;; taught me possibly the most important lesson of my life: *Food is the key to everything.*
;; He was a character.
;; Somehow he always had the best rental car,
;; the best stories,
;; and a way of finding himself in the middle of everything.
;; He could walk into a room of executives,
;; then sit down with the wharfies and share a beer,
;; and somehow everyone respected him.
;; He knew everyone.
;; He remembered everyone's stories.
;; He had a story for everyone,
;; a joke for every situation.
;; He always landed on his feet.

;; I once asked him what his secret was.
;; He gave me a wink and said:
;; "I brought the rental guy some fresh crabs.
;; I took our boss to the best restaurant and expensed it so he didn't have to.
;; When people know you care about them, they will work with you."
;; At first I thought he had just figured out how to work the system.
;; But I eventually realized he understood something deeper:
;; *influence comes from creating value for other people.*

;; From that day forward I nominated myself the team caterer.
;; Every morning on the drive out to the terminal I would stop and buy fresh bread, salad, coldcuts, fruits,
;; and put on a platter for the team.
;; All expensed to the company of course, and happily so.
;; Everyone loved it.
;; Suddenly people listened to me,
;; helped me when I needed it,
;; and I had influence.
;; I hadn't convinced anyone.
;; I just made them lunch.

;; I used to think that if enough people agreed on something,
;; eventually it would happen,
;; I just had to explain why I was right.
;; But agreement is not enough.
;; Something has to turn that agreement into action.

;; I'm not sure what to do.
;; I don't have the time or energy to spend hounding politicians.
;; For now I've invested some time in sharing my concerns in this series.
;; But I can see that addressing wealth concentration requires more than understanding the problem.
;; It requires people coordinating around a shared goal.
;; I'm still trying to figure out what that looks like.
;; What do you think we should do?
;; :::

;; ## Conclusion

;; Economic growth is being hampered by inequality.
;; Inequality is a root cause and result of many of the crises we face today.
;; Breaking the negative feedback cycle can help our civilization progress, build knowledge, and grow.
;; Wealth tax is the most direct way to address inequality.
;; There is popular support, but that is not enough to achieve legislation.
;; Demand more from your politicians, regardless of party or platform.
;; Convert broad agreement into procedural pressure.

;; The final part, [References](u8_references.html), lists the books, datasets, and researchers that informed this series.

;; [^1]: “Impose an extra annual tax of 2% on wealth over $50 million, and 3% on wealth over $1 billion. This proposal would reduce the deficit by $200 billion a year. What is your recommendation?” Charge wealth tax: National 78%, Republicans 72%, Democrats 83% [Common Ground of the American People](https://publicconsultation.org/cgoap/).
;;       A 2024 polling roundup at [Inequality.org](https://inequality.org/article/extensive-polls-find-americans-support-taxing-the-wealthy/) reports that more than three out of five Americans supported a wealth tax across aggregated national polls.
;;       Pew found in 2026 that [61% of Americans say it bothers them a lot that wealthy people do not pay their fair share](https://www.pewresearch.org/short-reads/2026/04/06/top-tax-frustrations-for-americans-feeling-that-some-wealthy-people-corporations-dont-pay-fair-share/).
;; [^2]: The latest major U.S. tax package has been criticized for delivering the largest gains to upper-income households and business owners; see [Big Ugly Tax Scam Act Analysis](https://americansfortaxfairness.org/big-ugly-tax-scam-act-analysis/).
;; [^3]: The 2026 [Ultra-Millionaire Tax Act](https://www.congress.gov/bill/119th-congress/senate-bill/4246/text) is a concrete example of a stand-alone wealth-tax bill with thresholds, enforcement rules, and valuation provisions.
;;       See the list of [cosponsors](https://www.congress.gov/bill/119th-congress/senate-bill/4246/cosponsors).
