^:kindly/hide-code
(ns economics.wealth-distribution.unbounded.u4-interactive-factors
  {:kindly/options {:static true
                    :kinds-that-hide-code #{:kind/var :kind/hiccup2 :kind/fn}}
   :clay {:title          "Unbounded: Interactive Factors"
          :quarto         {:sidebar "unbounded"
                           :author      [:timothypratley]
                           :description "A systems view of wealth concentration, growth, stability, governance, and opportunity."
                           :abstract    "Knowledge sharing is the positive feedback loop behind growth, resilience, and living standards. Wealth concentration is a negative feedback loop that concentrates both economic and political power."
                           :date        "2026-06-20"
                           :type        :post
                           :category    :economics
                           :tags        [:tax :economics :growth]}}})

;; ## Modeling Economic Interactive Factors

;; The Interactive Factors Framework (IFF) models a situation by naming factors and the causal relationships between them.
;; The goal is to find the positive and negative cycles we can break or reinforce.

;; ::: {#fig-iff-base}

;; ![Standard of living, system stability, and wealth concentration feed back into each other through causal loops.](iff-base.svg)

;; Interactive Economic Factors (nodes) and Causes (arrows)
;; :::

;; Many factors matter, but Standard of Living, System Stability, and Wealth Concentration affect us directly in tangible ways.
;; They also influence many of the other factors through feedback loops.
;; They are both outcomes of the system and drivers of its future behavior.
;; Importantly, they are inter-related, sometimes indirectly, with each other.

;; Standard of Living is a strong direct cause of System Stability,
;; whereas Wealth Concentration affects System Stability via Class Mobility and Government Policy.
;; System Stability affects Wealth Concentration via Asset Stores, which are only possible in a stable system.
;; Standard of Living is indirectly affected by Wealth Concentration via Asset Stores, which affects Housing Affordability.

;; Highlighted in red is the most concerning negative self-reinforcing cycle:
;; Wealth Concentration causes Asset Stores, causes Capital Income, causes Wealth Concentration.
;; Wealth Concentration enables disproportionate influence on Government Policy.
;; Wealth Concentration limits Productive Capacity because of the competition for Asset Stores.

;; Highlighted in green are the positive self-reinforcing cycles:
;; Labor Income causes Consumer Spending, causes Productive Capacity, causes Labor Income.
;; Innovation causes Knowledge, causes Productive Capacity, causes Innovation.
;; Knowledge creation and sharing are strong positive influences on other factors.

;; I arranged the factors so the most familiar concerns appear first.
;; Living Standards and Labor Income are immediate,
;; while Productive Capacity acts as a hub; it is central to progress, causing both positive and negative side effects.

;; We may choose to use a different arrangement that emphasises the role of Wealth Concentration.

;; ::: {#fig-iff-wealth-concentration}

;; ![Capital and political power are accumulated, amplified, and concentrated](iff-wealth-concentration.svg)

;; Wealth Concentration Factors (nodes) and Causes (arrows)
;; :::

;; This is the same diagram rearranged to highlight the role of Wealth Concentration.

;; Wealth is control over productive assets: land, housing, businesses, and capital.
;; Wealth can be invested, compounded, and passed between generations.
;; That makes it a durable source of economic and political influence.
;; Extreme Wealth Concentration constrains and corrupts the system.
;; Taxation is the primary factor that can moderate it.

;; Let's turn our attention to the positive knowledge loop.

;; ::: {#fig-iff-knowledge}

;; ![Knowledge creation and sharing form the positive feedback loop that expands productivity, experimentation, and long-run economic progress.](iff-knowledge.svg)

;; Knowledge Factors (nodes) and Causes (arrows)
;; :::

;; Knowledge is humanity's greatest compounding asset.
;; By creating and sharing ideas, we build better tools, solve harder problems,
;; and expand what future generations are capable of achieving.
;; This cycle drives science, technology, institutions, and economic progress.

;; That is why tools for thought like Clojure, Clay, and ClojureCivitas matter to me.
;; That is why blogging, sharing ideas, making diagrams, and supporting community discussion matter to me.

;; > "You never change things by fighting the existing reality. To change something, build a new model that makes the existing model obsolete." -- Buckminster Fuller

;; ::: {.callout-note}
;; ## Storytime

;; Near the end of 2024 I was on a Zoom call with a friend, coding together on [Clay](https://scicloj.github.io/clay),
;; when an air raid siren erupted.
;; They had to leave the call and quickly relocate to an underground shelter to avoid incoming rockets.
;; A few minutes earlier we had been discussing code, ideas, and how to build better tools for sharing knowledge.
;; Then, suddenly, the only priority was staying alive.
;; This situation makes me question the problems I choose to write about.
;; I have friends living through war.
;; What can be more urgent than that?

;; War is a terrible outcome of concentrated power.
;; Wealth concentration shapes how we collectively respond to crises.
;; Extreme wealth concentration shifts economic and political power into the hands of a tiny elite.
;; With that influence comes the ability to shape public policy, investment, and the stories we hear in the news.
;; The elite choose which industries receive funding, and which problems are neglected.
;; They determine whether we invest in weapons or healthcare, datacenters or education, fossil fuels or climate resilience.
;; And the result of that influence is further wealth concentration.
;; Wealth concentration is both a cause and a consequence of the social crises we face.

;; Concentrating power and resources away from broad participation makes our systems more fragile.
;; Diverse societies are resilient because many people contribute ideas, investments, and solutions.
;; When wealth and decision-making become concentrated, fewer perspectives shape the future, reducing our ability to respond to new challenges.

;; There is another cycle at work: knowledge creation, sharing, experimentation, and innovation.
;; When more people can participate, more possibilities are explored.
;; This is the positive feedback loop that drives economic growth and human progress.
;; Protecting and expanding this cycle is critical to our future.

;; **Prosperity comes from participation.**\
;; **Fragility comes from concentration.**

;; To understand these cycles, I needed a way to see the system as a whole.
;; Being able to rearrange the analysis was eye-opening.
;; Changing the arrangement changed the questions I could ask and the answers I could see.
;; Moving pieces around changed which relationships were most visible.
;; Clusters, cycles, and feedback loops that were hidden in one arrangement became obvious in another.
;; In complex systems, the outcomes we care about often emerge only after many steps.
;; Diagrams are a tool for thinking.
;; They let us follow chains of cause and effect, and discover feedback loops.
;; The way we arrange factors influences where our attention is drawn.
;; :::

;; ## Conclusion

;; Human progress comes from a positive compounding loop of knowledge.
;; Sharing your ideas is a positive driver for our society.
;; Extreme wealth concentration creates a competing loop that captures resources and reduces participation.
;; Inequality is a root cause and result of many of the crises we face.
;; Reducing wealth concentration enables positive change across many social issues.

;; In the next part, [Wealth Concentration](u5_wealth_concentration.html), we quantify how large the problem is.
