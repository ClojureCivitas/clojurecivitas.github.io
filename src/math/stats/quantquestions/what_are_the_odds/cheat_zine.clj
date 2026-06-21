^:kindly/hide-code
(ns math.stats.quantquestions.what-are-the-odds.cheat-zine
  {:clay {:title          "A Survival Themed Probability Cheatsheet Zine"
          ;; NOTE: `:static true` tells Clay to export SVGs as images so they can be compiled into the PDF.
          :kindly/options {:static true
                           :kinds-that-hide-code #{:kind/var :kind/hiccup}}
          ;; NOTE: Typst configuration. Typst is a PDF compiler.
          ;;       See the {=typst} block at the start of the content,
          ;;       where the zen-zine plugin is activated.
          :quarto         {:format {:typst {:papersize :us-letter
                                            :fontsize "8pt"}}
                           ;; NOTE: overriding theme to make more printer friendly
                           :theme       :flatly
                           :brand       false
                           :number-sections false
                           :image       "cheat_zine.png"
                           :author      [:timothypratley]
                           :description "A printable PDF of an 8-panel foldable probability zine built with Clay."
                           :abstract    "This zine sheet folds into 8 small pages. Space is tight, so it focuses on a few core ideas instead of full definitions; counting outcomes, choosing the right denominator, using AND/OR/NOT, and conditional probability. The layout and fold structure are handled by the zen-zine Typst plugin. The zine is for quick learning and quick checks of understanding, such as a short discussion with a nephew or niece."
                           :date        "2026-06-20"
                           :type        :post
                           :category    :stats
                           :tags        [:stats :probability :zine :print :typst]}}}
  ;; NOTE: Plotje output is SVG, which is perfect to use with `:static true` for PDF.
  (:require [scicloj.plotje.api :as pj]))

;; ```{=typst}
;; #import "@preview/zen-zine:0.4.0": zine8
;; #show: zine8
;; ```

;; # Will You Survive?

;; ### Island Survival Guide

;; You are stranded on an island.
;; The difference between rescue and disaster lies in the data.
;; This is your survival guide to probability and statistics.
;; Learn the patterns, and maybe you will get rescued.

;; ::: {.content-visible when-format="html"}
;; ::: {.callout-note}
;; This page is a companion to a printable foldable zine.
;; See [the PDF](cheat_zine.pdf) to view the 8-panel print layout.
;; :::
;; :::

;; ![](island.svg)

;; {{< pagebreak >}}

;; ## Enumerate and count

;; $P(E) = \frac{\text{favorable outcomes}}{\text{total outcomes}}$

;; Check 10 nearby trees.
;; Count the outcomes you care about, then divide by the number of trees.
;; Enumeration means listing possible outcomes so you can count them.

^:kindly/hide-code
(let [fruiting? #{1 7}
      step 22]
  (into
   ^:kind/hiccup
   [:svg {:viewBox "0 0 260 84" :width "100%" :style {:background "#ffffff"}}
    [:text {:x 120 :y 16 :text-anchor "middle" :font-size 14 :font-weight 700 :fill "#111827"}
     "Tree Fruiting Probability"]]
   (concat
    (for [i (range 10)
          :let [x (+ 20 (* i step))
                fruit? (contains? fruiting? i)]]
      [:g
       [:rect {:x x :y 28 :width 16 :height 16 :rx 3
               :fill (if fruit? "#fef3c7" "#e5e7eb")
               :stroke (if fruit? "#d97706" "#9ca3af")}]
       (when fruit?
         [:text {:x (+ x 8) :y 40 :text-anchor "middle" :font-size 8 :fill "#92400e"}
          "F"])])
    [[:text {:x 120 :y 62 :text-anchor "middle" :font-size 9 :fill "#92400e"}
      "Observed fruiting = 2/10"]])))

;; You sampled 10 trees, but the island has over 100 trees.
;; So $\frac{2}{10}=0.2$ is a sample-based guess: useful for describing what you observed and predicting future observations.

;; Statistics use past observations (like 2 of 10 trees) to estimate a probability.
;; Probability uses that estimate to predict what will happen at the next tree.
;; Probability rules show how to combine predictions into complex scenarios.

;; {{< pagebreak >}}

;; ## The AND Rule

;; $P(A \text{ and } B) = P(A) \times P(B)$

;; You make two foraging stops before dark; how much fruit should you expect?
;; Linked events shrink your chances; multiply the probabilities along the branches. Each requirement filters out more possibilities, so you need both to happen.

^:kindly/hide-code
(let [cell 12
  pad 34
  cols 10
  rows 10
  a-cols 2   ;; tree 1 fruiting = 2/10
  b-rows 2]  ;; tree 2 fruiting = 2/10
  (into
   ^:kind/hiccup
   [:svg {:viewBox "0 0 300 180" :width "100%" :style {:background "#ffffff"}}
    [:text {:x 130 :y 16 :text-anchor "middle" :font-size 14 :font-weight 700 :fill "#111827"}
     "AND is an intersection of outcomes"]
    [:text {:x 20 :y 97 :transform "rotate(-90 20 97)" :font-size 9 :fill "#374151"}
     "Tree2 fruiting"]
    [:text {:x 93 :y 172 :text-anchor "middle" :font-size 9 :fill "#374151"}
     "Tree1 fruiting"]]
     (concat
      (for [r (range rows)
        c (range cols)
        :let [x (+ pad (* c cell))
          y (+ 38 (* r cell))
          in-a? (< c a-cols)
          in-b? (< r b-rows)
          fill (cond
             (and in-a? in-b?) "#84cc16"  ;; A and B
             in-a? "#dbeafe"              ;; A only
             in-b? "#fef3c7"              ;; B only
             :else "#f3f4f6")
          stroke (if (and in-a? in-b?) "#4d7c0f" "#9ca3af")]]
    [:rect {:x x :y y :width 10 :height 10 :rx 1.5 :fill fill :stroke stroke}])
      [[:rect {:x 186 :y 146 :width 98 :height 20 :rx 4 :fill "#ecfccb" :stroke "#84cc16"}]
       [:text {:x 235 :y 159 :text-anchor "middle" :font-size 9 :fill "#365314"}
    "A and B = 4/100"]])))

;; If each tree has fruit chance $2/10$ (0.2), then
;; $P(\text{tree1 fruiting AND tree2 fruiting}) = 0.2 \times 0.2 = 0.04$ (4%).

;; {{< pagebreak >}}

;; ## The OR Rule

;; $P(A \text{ or } B) = P(A) + P(B)$

;; It's a new day! Time to collect more fruit.
;; A tree can be a coconut tree or a bananna tree, but not both at once.
;; When you visit a tree, the type of tree is mutually exclusive.

^:kindly/hide-code
(let [coconut? #{1 7}
  banana? #{3 8 9}
  step 22]
  (into
   ^:kind/hiccup
   [:svg {:viewBox "0 0 260 98" :width "100%" :style {:background "#ffffff"}}
    [:text {:x 130 :y 16 :text-anchor "middle" :font-size 14 :font-weight 700 :fill "#111827"}
     "Tree Types"]]
   (concat
    (for [i (range 10)
      :let [x (+ 20 (* i step))
        is-coconut? (contains? coconut? i)
        is-banana? (contains? banana? i)
        fill (cond
           is-coconut? "#fef3c7"
           is-banana? "#dbeafe"
           :else "#e5e7eb")
        stroke (cond
         is-coconut? "#d97706"
         is-banana? "#2563eb"
         :else "#9ca3af")
        label (cond
        is-coconut? "C"
        is-banana? "B"
        :else "O")
        text-fill (cond
            is-coconut? "#92400e"
            is-banana? "#1e40af"
            :else "#6b7280")]]
  [:g
   [:rect {:x x :y 32 :width 16 :height 16 :rx 3 :fill fill :stroke stroke}]
   [:text {:x (+ x 8) :y 43 :text-anchor "middle" :font-size 8 :fill text-fill}
    label]])
    [[:text {:x 58 :y 68 :text-anchor "middle" :font-size 8 :fill "#92400e"}
  "Coconut = 2/10"]
     [:text {:x 132 :y 68 :text-anchor "middle" :font-size 8 :fill "#1e40af"}
  "Banana = 3/10"]
     [:text {:x 208 :y 68 :text-anchor "middle" :font-size 8 :fill "#6b7280"}
  "Other = 5/10"]
     [:text {:x 130 :y 86 :text-anchor "middle" :font-size 9 :fill "#365314"}
  "Coconut or Banana = 5/10"]])))

;; A tree cannot be both types at once, so just add the probabilities.
;; $P(\text{coconut or banana}) = \frac{2}{10} + \frac{3}{10} = \frac{5}{10} = 0.5$.

;; If categories can overlap, adding $P(A)$ and $P(B)$ counts shared cases twice.
;; Subtract the overlap once to fix that double-counting.
;; Use $P(A \text{ or } B)=P(A)+P(B)-P(A \text{ and } B)$.

;; tall or fruiting = tall + fruiting - tall and fruiting.

;; {{< pagebreak >}}

;; ## The NOT Rule

;; $P(\text{not } E) = 1 - P(E)$

;; Night is coming; you need to find at least one fruit.
;; If you visit 3 trees with $P(fruit)=\frac{2}{10}$, what is the chance you get at least one fruit?
;; Use the NOT rule when the event you want is hard to count, but its opposite is easy.

^:kindly/hide-code
^:kind/hiccup
[:svg {:viewBox "0 0 520 190" :width "100%" :style {:background "#ffffff"}}
 [:text {:x 260 :y 16 :text-anchor "middle" :font-size 12 :font-weight 700 :fill "#111827"}
  "Solve the Easier Problem"]
 [:circle {:cx 40 :cy 95 :r 14 :fill "#0f766e"}]
 [:text {:x 40 :y 99 :text-anchor "middle" :font-size 8 :fill "white"} "V1"]
 [:circle {:cx 140 :cy 60 :r 12 :fill "#2563eb"}]
 [:text {:x 140 :y 64 :text-anchor "middle" :font-size 8 :fill "white"} "N"]
 [:circle {:cx 140 :cy 130 :r 12 :fill "#f59e0b"}]
 [:text {:x 140 :y 134 :text-anchor "middle" :font-size 8 :fill "#111827"} "F"]
 [:circle {:cx 240 :cy 35 :r 11 :fill "#2563eb"}]
 [:text {:x 240 :y 39 :text-anchor "middle" :font-size 7 :fill "white"} "NN"]
 [:circle {:cx 240 :cy 75 :r 11 :fill "#f59e0b"}]
 [:text {:x 240 :y 79 :text-anchor "middle" :font-size 7 :fill "#111827"} "NF"]
 [:circle {:cx 240 :cy 115 :r 11 :fill "#f59e0b"}]
 [:text {:x 240 :y 119 :text-anchor "middle" :font-size 7 :fill "#111827"} "FN"]
 [:circle {:cx 240 :cy 155 :r 11 :fill "#f59e0b"}]
 [:text {:x 240 :y 159 :text-anchor "middle" :font-size 7 :fill "#111827"} "FF"]
 [:line {:x1 52 :y1 90 :x2 128 :y2 65 :stroke "#2563eb" :stroke-width 1.8}]
 [:line {:x1 52 :y1 100 :x2 128 :y2 125 :stroke "#d97706" :stroke-width 1.8}]
 [:line {:x1 151 :y1 55 :x2 229 :y2 38 :stroke "#2563eb" :stroke-width 1.6}]
 [:line {:x1 151 :y1 65 :x2 229 :y2 72 :stroke "#d97706" :stroke-width 1.6}]
 [:line {:x1 151 :y1 125 :x2 229 :y2 118 :stroke "#d97706" :stroke-width 1.6}]
 [:line {:x1 151 :y1 135 :x2 229 :y2 152 :stroke "#d97706" :stroke-width 1.6}]
 [:line {:x1 251 :y1 30 :x2 336 :y2 22 :stroke "#2563eb" :stroke-width 1.4}]
 [:line {:x1 251 :y1 40 :x2 336 :y2 42 :stroke "#d97706" :stroke-width 1.4}]
 [:line {:x1 251 :y1 70 :x2 336 :y2 62 :stroke "#d97706" :stroke-width 1.4}]
 [:line {:x1 251 :y1 80 :x2 336 :y2 82 :stroke "#d97706" :stroke-width 1.4}]
 [:line {:x1 251 :y1 110 :x2 336 :y2 102 :stroke "#d97706" :stroke-width 1.4}]
 [:line {:x1 251 :y1 120 :x2 336 :y2 122 :stroke "#d97706" :stroke-width 1.4}]
 [:line {:x1 251 :y1 150 :x2 336 :y2 142 :stroke "#d97706" :stroke-width 1.4}]
 [:line {:x1 251 :y1 160 :x2 336 :y2 162 :stroke "#d97706" :stroke-width 1.4}]
 [:rect {:x 336 :y 16 :width 48 :height 12 :rx 3 :fill "#dbeafe" :stroke "#2563eb"}]
 [:text {:x 360 :y 24 :text-anchor "middle" :font-size 7 :fill "#1e40af"} "NNN"]
 [:rect {:x 336 :y 36 :width 48 :height 12 :rx 3 :fill "#fef3c7" :stroke "#d97706"}]
 [:text {:x 360 :y 44 :text-anchor "middle" :font-size 7 :fill "#92400e"} "NNF"]
 [:rect {:x 336 :y 56 :width 48 :height 12 :rx 3 :fill "#fef3c7" :stroke "#d97706"}]
 [:text {:x 360 :y 64 :text-anchor "middle" :font-size 7 :fill "#92400e"} "NFN"]
 [:rect {:x 336 :y 76 :width 48 :height 12 :rx 3 :fill "#fef3c7" :stroke "#d97706"}]
 [:text {:x 360 :y 84 :text-anchor "middle" :font-size 7 :fill "#92400e"} "NFF"]
 [:rect {:x 336 :y 96 :width 48 :height 12 :rx 3 :fill "#fef3c7" :stroke "#d97706"}]
 [:text {:x 360 :y 104 :text-anchor "middle" :font-size 7 :fill "#92400e"} "FNN"]
 [:rect {:x 336 :y 116 :width 48 :height 12 :rx 3 :fill "#fef3c7" :stroke "#d97706"}]
 [:text {:x 360 :y 124 :text-anchor "middle" :font-size 7 :fill "#92400e"} "FNF"]
 [:rect {:x 336 :y 136 :width 48 :height 12 :rx 3 :fill "#fef3c7" :stroke "#d97706"}]
 [:text {:x 360 :y 144 :text-anchor "middle" :font-size 7 :fill "#92400e"} "FFN"]
 [:rect {:x 336 :y 156 :width 48 :height 12 :rx 3 :fill "#fef3c7" :stroke "#d97706"}]
 [:text {:x 360 :y 164 :text-anchor "middle" :font-size 7 :fill "#92400e"} "FFF"]
 [:rect {:x 420 :y 26 :width 90 :height 16 :rx 4 :fill "#dbeafe" :stroke "#2563eb"}]
 [:text {:x 465 :y 37 :text-anchor "middle" :font-size 8 :fill "#1e40af"} "No fruit: NNN"]
 [:rect {:x 420 :y 50 :width 90 :height 16 :rx 4 :fill "#fef3c7" :stroke "#d97706"}]
 [:text {:x 465 :y 61 :text-anchor "middle" :font-size 8 :fill "#92400e"} "Has fruit: 7 paths"]]

;; $P(not fruit) = 1 - \frac{2}{10} = \frac{8}{10}$

;; $P(\text{no fruit in 3 trees}) = \frac{8}{10}\times\frac{8}{10}\times\frac{8}{10} = 0.512$.

;; Invert back to the original question:

;; $P(\text{at least one fruit}) = 1 - 0.512 = 0.488$.

;; {{< pagebreak >}}

;; ## The "Given That" Rule

;; $P(A \mid B) = \frac{P(A \text{ and } B)}{P(B)}$

;; The bar symbol $|$ means "given".

;; Dark clouds move in, so your wood-gathering plan must adapt to the weather you actually get.
;; Question prompt us to gather new statistics and probabilities.

^:kindly/hide-code
^:kind/hiccup
[:svg {:viewBox "0 0 560 300" :width "100%" :style {:background "#ffffff"}}
 [:text {:x 280 :y 22 :text-anchor "middle" :font-size 14 :font-weight 700 :fill "#111827"}
  "Island weather affects finding dry wood"]
 [:circle {:cx 70 :cy 150 :r 22 :fill "#0f766e"}]
 [:text {:x 70 :y 155 :text-anchor "middle" :font-size 10 :fill "white"} "All (100)"]
 [:line {:x1 92 :y1 140 :x2 175 :y2 90 :stroke "#0f766e" :stroke-width 2}]
 [:line {:x1 92 :y1 160 :x2 175 :y2 210 :stroke "#0f766e" :stroke-width 2}]
 [:circle {:cx 195 :cy 85 :r 22 :fill "#2563eb"}]
 [:text {:x 195 :y 84 :text-anchor "middle" :font-size 9 :fill "white"} "Rain"]
 [:text {:x 195 :y 95 :text-anchor "middle" :font-size 9 :fill "white"} "(40)"]
 [:circle {:cx 195 :cy 215 :r 22 :fill "#f59e0b"}]
 [:text {:x 195 :y 214 :text-anchor "middle" :font-size 8 :fill "#111827"} "Not Rain"]
 [:text {:x 195 :y 224 :text-anchor "middle" :font-size 8 :fill "#111827"} "(60)"]
 [:line {:x1 217 :y1 77 :x2 320 :y2 55 :stroke "#2563eb" :stroke-width 2}]
 [:line {:x1 217 :y1 93 :x2 320 :y2 115 :stroke "#2563eb" :stroke-width 2}]
 [:line {:x1 217 :y1 207 :x2 320 :y2 185 :stroke "#d97706" :stroke-width 2}]
 [:line {:x1 217 :y1 223 :x2 320 :y2 245 :stroke "#d97706" :stroke-width 2}]
 [:rect {:x 308 :y 35 :width 112 :height 30 :rx 6 :fill "#fef3c7" :stroke "#2563eb"}]
 [:text {:x 364 :y 54 :text-anchor "middle" :font-size 9 :fill "#92400e"} "Dry | Rain (8)"]
 [:rect {:x 308 :y 95 :width 112 :height 30 :rx 6 :fill "#dbeafe" :stroke "#2563eb"}]
 [:text {:x 364 :y 114 :text-anchor "middle" :font-size 9 :fill "#1e40af"} "Not Dry | Rain (32)"]
 [:rect {:x 308 :y 165 :width 112 :height 30 :rx 6 :fill "#fef3c7" :stroke "#d97706"}]
 [:text {:x 364 :y 184 :text-anchor "middle" :font-size 9 :fill "#92400e"} "Dry | Not Rain (54)"]
 [:rect {:x 308 :y 225 :width 112 :height 30 :rx 6 :fill "#dbeafe" :stroke "#d97706"}]
 [:text {:x 364 :y 244 :text-anchor "middle" :font-size 9 :fill "#1e40af"} "Not Dry | Not Rain (6)"]
 [:line {:x1 420 :y1 50 :x2 478 :y2 110 :stroke "#d97706" :stroke-width 1.5 :stroke-dasharray "4 3"}]
 [:line {:x1 420 :y1 180 :x2 478 :y2 110 :stroke "#d97706" :stroke-width 1.5 :stroke-dasharray "4 3"}]
 [:line {:x1 420 :y1 110 :x2 478 :y2 210 :stroke "#60a5fa" :stroke-width 1.5 :stroke-dasharray "4 3"}]
 [:line {:x1 420 :y1 240 :x2 478 :y2 210 :stroke "#60a5fa" :stroke-width 1.5 :stroke-dasharray "4 3"}]
 [:circle {:cx 500 :cy 110 :r 22 :fill "#fef3c7" :stroke "#d97706"}]
 [:text {:x 500 :y 107 :text-anchor "middle" :font-size 8 :fill "#92400e"} "Dry"]
 [:text {:x 500 :y 117 :text-anchor "middle" :font-size 8 :fill "#92400e"} "(62)"]
 [:circle {:cx 500 :cy 210 :r 22 :fill "#dbeafe" :stroke "#2563eb"}]
 [:text {:x 500 :y 207 :text-anchor "middle" :font-size 7 :fill "#1e40af"} "Not Dry"]
 [:text {:x 500 :y 217 :text-anchor "middle" :font-size 8 :fill "#1e40af"} "(38)"]]

;; Probability is about identifying the interesting set of outcomes.
;; A tree helps you enumerate and count interesting and possible outcomes.
;; For "dry wood given rain," count within rainy outcomes: $8/40$.
;; For "rain given dry wood," count within dry outcomes (all yellow): $8/62$.
;; It's a matter of filtering to cases where the condition is true, then count outcomes inside that filtered group.

;; {{< pagebreak >}}

;; ## Distribution Matters

;; Where will you camp?
;; Continuous variables like temperature can be any value.
;; Observations rarely spread evenly; instead, they cluster and vary.
;; Island A and Island B both average 75°F, yet Island A is highly predictable (72°F to 78°F) while Island B is highly volatile (30°F to 120°F).
;; To truly understand the situation, we must see the distribution, the shape of the data.

;; ::: {layout-ncol=2}
^:kindly/hide-code
(-> [{:temperature "30-44F" :days 0}
    {:temperature "45-59F" :days 0}
    {:temperature "60-74F" :days 38}
    {:temperature "75-89F" :days 62}
    {:temperature "90-104F" :days 0}
    {:temperature "105-119F" :days 0}
    {:temperature "120-134F" :days 0}]
  (pj/lay-value-bar :temperature :days)
  (pj/options {:title "Island A"}))

^:kindly/hide-code
(-> [{:temperature "30-44F" :days 2}
  {:temperature "45-59F" :days 4}
  {:temperature "60-74F" :days 8}
  {:temperature "75-89F" :days 10}
  {:temperature "90-104F" :days 7}
  {:temperature "105-119F" :days 4}
  {:temperature "120-134F" :days 2}]
  (pj/lay-value-bar :temperature :days)
  (pj/options {:title "Island B"}))
;; :::

;; A distribution reveals where observations cluster and how frequently they occur.
;; Variance quantifies this spread by measuring the average squared distance from the mean.
;; A wider spread generally implies higher variance, but the exact value depends on the range and the shape of the distribution.

;; {{< pagebreak >}}

;; ## Survival Quiz

;; ![](coconut.svg){width="50%" fig-align="center"}

;; Time to try to use your radio to contact help.

;; a. Your radio has a $\frac{2}{3}$ failure rate. What is the probability that your signal is successful?
;; b. Finding fruit and using the radio are independent events. If fruit is found $\frac{2}{10}$ of the time and the radio works $\frac{1}{3}$ of the time, what is the probability that both happen?
;; c. Rainfall data shows most days have 0 mm of rain, but one rare day has a 600 mm flood. How would you describe the shape of this distribution?
;; d. Land Area A has a height standard deviation of 2 ft (very flat). Land Area B has a height standard deviation of 80 ft (very rugged). Which area is more likely to contain a mountain peak?
