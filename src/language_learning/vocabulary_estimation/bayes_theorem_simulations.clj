^{:kindly/hide-code true
  :kindly/options {:html/deps [:scittle :reagent]}
  :clay {:hide-info-line true
         :title "Bayes' Theorem from Uncertainty to Decision"
         :quarto {:author :jamiep
                  :description "A visual, interactive introduction to Bayesian updating, posterior sampling, decisions, and Gaussian grid approximation."
                  :type :post
                  :date "2026-07-13"
                  :category :concepts
                  :tags [:bayesian-statistics :clojure :clojurescript :scittle :simulation]
                  :keywords [:bayes-theorem :grid-approximation :posterior-sampling :normal-distribution :data-visualisation]}}}

(ns language-learning.vocabulary-estimation.bayes-theorem-simulations
  (:require [language-learning.vocabulary-estimation.math-explanations :as math]
            [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(kind/hiccup
 [:style
  (str
   ":root{--bp-accent:#1464b5;--bp-accent-soft:#e5f1fb;--bp-warm:#a34f00;--bp-warm-soft:#fff0df;--bp-muted:#4f5b66}"
   ".quarto-dark{--bp-accent:#73b7ff;--bp-accent-soft:#173653;--bp-warm:#ffc27a;--bp-warm-soft:#4a2d12;--bp-muted:#b9c7d2}"
   "#title-block-header{padding-top:.75rem}"
   "#title-block-header h1{line-height:1.15;overflow-wrap:anywhere}"
   "mjx-container[display=true]{max-width:100%;overflow-x:auto;overflow-y:hidden}"
   ".bp-callout{border:1px solid color-mix(in srgb,var(--bp-accent) 45%,var(--bs-border-color,#dee2e6));border-left:4px solid var(--bp-accent);background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 90%,var(--bp-accent) 10%);color:var(--bs-body-color,#212529);padding:1rem 1.15rem;margin:1.4rem 0;border-radius:.35rem}"
   ".bp-callout strong{display:block;margin-bottom:.3rem}"
   ".bp-simulator{margin:1.5rem 0}"
   ".bp-shell{border:1px solid var(--bs-border-color,#ced4da);border-radius:.65rem;padding:clamp(.8rem,3vw,1.3rem);min-width:0;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}"
   ".bp-shell h3{margin-top:0}.bp-shell h4{font-size:1rem}"
   ".bp-details,.bp-predict{border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;margin:.85rem 0;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}"
   ".bp-details summary,.bp-predict summary{cursor:pointer;font-weight:700;padding:.75rem 1rem}"
   ".bp-details>div,.bp-predict>div{padding:0 1rem 1rem}"
   ".bp-predict{border-left:4px solid var(--bp-warm);background:color-mix(in srgb,var(--bs-body-bg,#fff) 91%,var(--bp-warm) 9%)}"
   ".bp-controls{display:flex;align-items:end;gap:.65rem;flex-wrap:wrap;margin:1rem 0}"
   ".bp-controls label,.bp-field label{font-weight:700;font-size:.88rem}"
   ".bp-controls input[type=range]{width:min(15rem,100%);accent-color:var(--bp-accent)}"
   ".bp-button{border:1px solid var(--bs-border-color,#6c757d);border-radius:.35rem;padding:.55rem .85rem;font-weight:600;cursor:pointer;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}"
   ".bp-button.bp-primary,.bp-button[aria-pressed=true]{border-color:#1464b5;background:#1464b5;color:#fff}"
   ".quarto-dark .bp-button.bp-primary,.quarto-dark .bp-button[aria-pressed=true]{border-color:#73b7ff;background:#73b7ff;color:#10212b}"
   ".bp-button:disabled{opacity:.45;cursor:not-allowed}"
   ".bp-button:focus-visible,.bp-select:focus-visible,.bp-controls input:focus-visible,.bp-details summary:focus-visible,.bp-predict summary:focus-visible{outline:3px solid color-mix(in srgb,var(--bp-accent) 50%,transparent);outline-offset:2px}"
   ".bp-select{border:1px solid var(--bs-border-color,#6c757d);border-radius:.35rem;padding:.5rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}"
   ".bp-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,20rem),1fr));gap:1rem;margin:1rem 0}"
   ".bp-chart{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.5rem;padding:.7rem;margin:0;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}"
   ".bp-chart h4{margin:.1rem 0 .2rem;font-size:.95rem;line-height:1.25}"
   ".bp-chart svg{display:block;width:100%;height:auto}"
   ".bp-caption,.bp-note{font-size:.86rem;color:var(--bp-muted);margin:.4rem 0 0}"
   ".bp-stat{font-variant-numeric:tabular-nums;margin:.4rem 0}"
   ".bp-sample-sequence{font-family:var(--bs-font-monospace,monospace);font-size:.82rem;overflow-wrap:anywhere;max-height:6rem;overflow:auto;padding:.55rem;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.35rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 92%,var(--bp-accent) 8%);color:var(--bs-body-color,#212529)}"
   ".bp-axis{stroke:currentColor;stroke-opacity:.5}.bp-guide{stroke:currentColor;stroke-opacity:.11}"
   ".bp-line{fill:none;stroke:var(--bp-accent);stroke-width:3;vector-effect:non-scaling-stroke}"
   ".bp-line-secondary{fill:none;stroke:var(--bp-warm);stroke-width:2;stroke-dasharray:6 4;vector-effect:non-scaling-stroke}"
   ".bp-bar-water{fill:var(--bp-accent)}.bp-bar-land{fill:var(--bp-warm)}"
   ".bp-points{fill:none;stroke:var(--bp-accent);stroke-width:2;stroke-linecap:round;vector-effect:non-scaling-stroke}"
   ".bp-progress{width:100%;height:.55rem;accent-color:var(--bp-accent)}"
   ".bp-formula{font-family:var(--bs-font-monospace,monospace);overflow-wrap:anywhere}"
   ".bp-heat-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,17rem),1fr));gap:1rem}"
   ".bp-empty{display:grid;place-items:center;min-height:11rem;border:1px dashed var(--bs-border-color,#adb5bd);border-radius:.35rem;color:var(--bp-muted);text-align:center;padding:1rem}"
   ".bp-sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}"
   ".bp-process-strip{display:grid;grid-template-columns:repeat(7,minmax(0,auto));align-items:stretch;gap:.4rem;margin:1rem 0}"
   ".bp-process-step{display:grid;align-content:center;min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.65rem;background:var(--bs-body-bg,#fff);text-align:center;overflow-wrap:anywhere}"
   ".bp-process-step strong{display:block}.bp-process-step small{color:var(--bp-muted)}"
   ".bp-process-symbol{display:grid;place-items:center;font-size:1.3rem;font-weight:800;color:var(--bp-accent)}"
   ".bp-reading-guide{border-left:4px solid var(--bp-accent);padding:.2rem 0 .2rem 1rem;margin:1rem 0}"
   ".bp-reading-guide h3{font-size:1rem;margin:.1rem 0 .4rem}.bp-reading-guide ul{margin-bottom:.2rem}"
   ".bp-definition-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,15rem),1fr));gap:.7rem;margin:1rem 0}"
   ".bp-definition{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.75rem;background:var(--bs-body-bg,#fff)}"
   ".bp-definition dt{font-weight:800;color:var(--bp-accent)}.bp-definition dd{margin:.25rem 0 0}"
   ".bp-engineering-caption{text-align:center;color:var(--bp-muted);font-size:.88rem}"
   ".series-toc{min-width:0;border:1px solid var(--bs-border-color,#ced4da);border-radius:.6rem;padding:clamp(.85rem,3vw,1.2rem);margin:0 0 1.4rem;background:var(--bs-body-bg,#fff)}"
   ".series-toc h2{font-size:1.2rem;margin:0 0 .55rem}.series-toc p{margin:0 0 .7rem}.series-toc ol{margin:0;padding-left:1.45rem}.series-toc li{padding:.18rem .45rem}"
   ".series-status{display:inline-block;margin-left:.35rem;font-size:.7rem;font-weight:700;letter-spacing:.04em;text-transform:uppercase;color:var(--bp-muted)}"
   ".series-current{margin:.35rem 0 .35rem -.7rem;border-left:4px solid var(--bp-accent);border-radius:.4rem;padding:.6rem .75rem!important;background:color-mix(in srgb,var(--bs-body-bg,#fff) 84%,var(--bp-accent) 16%);box-shadow:inset 0 0 0 1px color-mix(in srgb,var(--bp-accent) 35%,transparent);font-weight:700}.series-current>a{color:var(--bp-accent)}.series-current .series-status{border-radius:999px;padding:.18rem .48rem;background:var(--bp-accent);color:#fff}.quarto-dark .series-current .series-status{color:#10212b}"
   "@media(max-width:767px){.bp-process-strip{grid-template-columns:minmax(0,1fr)}.bp-process-symbol{min-height:1.2rem}.bp-shell{padding:.75rem}.bp-controls{align-items:stretch}.bp-button{flex:1}.bp-chart{padding:.5rem}}")])

^:kindly/hide-code
(math/styles)

^:kindly/hide-code
(kind/hiccup
 [:nav.series-toc {:aria-labelledby "series-contents-heading"}
  [:h2#series-contents-heading "Bayes-to-Lexibench series"]
  [:p "This article supplies the probability tools used by the later vocabulary-estimation articles."]
  [:ol
   [:li.series-current
    [:a {:href "bayes_theorem_simulations.html" :aria-current "page"}
     "Bayes' theorem from uncertainty to decision"]
    [:span.series-status "you are here"]]
   [:li [:a {:href "beta_binomial_first_pass.html"} "Estimating vocabulary size with a simple Bayesian model"] [:span.series-status "published"]]
   [:li [:a {:href "pair_frequency_logistic_v2_article.html"} "Does pair frequency predict learner responses?"] [:span.series-status "published"]]
   [:li "From Self-Reported CEFR to a Versioned Lemma–Form-Pair Pool" [:span.series-status "planned"]]
   [:li "From Correlated Form Pairs to Latent Lemma Knowledge" [:span.series-status "planned"]]
   [:li "Modelling Correct, Wrong, and Don't-Know Separately" [:span.series-status "planned"]]
   [:li "Calibrating Items Before IRT and Adaptive Selection" [:span.series-status "planned"]]
   [:li "When Contexts and Senses Become Identifiable" [:span.series-status "planned"]]]])

;; A probability model is a disciplined way to reason when one answer is
;; unknown. It does not remove uncertainty. It records candidate answers,
;; states how observations would arise under each candidate, and updates their
;; relative plausibility when data arrive.
;;
;; This tutorial rebuilds three simulations I first made with Daniel Slutsky's
;; [JointProb group](https://scicloj.github.io/docs/community/groups/jointprob/).
;; The examples come from sections 2.2, 3.2, and 4.3 of Richard McElreath's
;; *Statistical Rethinking*. No statistics or programming background is
;; assumed. Each chapter asks one practical question:

^:kindly/hide-code
(kind/hiccup
 [:ol.article-chapter-map
  [:li [:strong "Update"] [:br] "How should observations change uncertainty?"]
  [:li [:strong "Sample and decide"] [:br] "How can a distribution guide an action?"]
  [:li [:strong "Scale up"] [:br] "How does the same update work with two unknown parameters?"]
  [:li [:strong "Reproduce"] [:br] "How do code, seeds, tests, and rendering make the lesson checkable?"]])

^:kindly/hide-code
(math/global-controls)

;; ## The five ideas under every update
;;
;; Suppose I do not know what proportion of a globe is covered by water.
;; **Uncertainty** means more than one answer remains credible. A **probability**
;; is a number from 0 to 1 used here to describe how strongly the model supports
;; an event or candidate. The unknown water proportion, called $p$, is a
;; **parameter**: a quantity the model tries to learn. A **model** is the set of
;; assumptions connecting that parameter to possible observations. The
;; observed water and land outcomes are the **data**.

^:kindly/hide-code
(kind/hiccup
 [:dl.bp-definition-grid
  [:div.bp-definition [:dt "Uncertainty"] [:dd "Several answers are still possible."]]
  [:div.bp-definition [:dt "Probability"] [:dd "A 0-to-1 numerical description of uncertainty inside a stated model."]]
  [:div.bp-definition [:dt "Parameter"] [:dd "An unknown quantity in the model, such as the water proportion p."]]
  [:div.bp-definition [:dt "Model"] [:dd "Assumptions saying how data could be generated for each parameter value."]]
  [:div.bp-definition [:dt "Data"] [:dd "Recorded observations used to compare the candidates."]]])

;; I cannot calculate with every decimal between 0 and 1 directly in this
;; simple demonstration, so I use **grid approximation**: replace the continuous
;; range by 201 equally spaced candidate values.
;;
;; $$p \in \{0, 0.005, 0.010, \ldots, 0.995, 1\}.$$
;;
;; Read this as: “$p$ is one of 201 candidates from zero to one, spaced by
;; 0.005.” A finer grid would approximate the continuous range more closely but
;; would require more calculation.

^:kindly/hide-code
(math/explanation
 "math-grid-candidates"
 "The discrete grid of candidate water proportions"
 [["p" "A candidate value for the unknown proportion of the globe covered by water; it must lie between 0 and 1."]
  ["∈" "‘Is an element of’ or ‘is one of the values in.’"]
  ["{…}" "Braces list the allowed candidate values rather than one continuous interval."]
  ["0.005" "The distance between adjacent candidates. Including both endpoints gives 201 grid points."]]
 "The grid is a numerical approximation; the underlying proportion is still conceived as continuous.")

^:kindly/hide-code
(def probability-grid
  (mapv #(/ % 200.0) (range 201)))

^:kindly/hide-code
(math/code-detail
 "code-probability-grid"
 "Constructing the 201-point probability grid"
 [:div
  [:p "Clojure's " [:code "range"] " produces the integers 0 through 200. Dividing each by 200 gives 0, 0.005, …, 1. The result is stored as a vector so its order is stable."]
  [:pre [:code "(def probability-grid\n  (mapv #(/ % 200.0) (range 201)))"]]
  [:p "Check: " [:code "(count probability-grid)"] " must equal 201, and the assertion at the end of this article enforces it."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/bayes_theorem_simulations.clj"} "View the complete executable Clojure article"]]])

;; Before data, each candidate receives a **prior** weight. After data, the
;; **likelihood** says how compatible those data are with each candidate. Their
;; product is an **unnormalised weight**: useful for ranking, but not yet a set
;; of probabilities that sums to 1. **Normalisation** divides every product by
;; their total. The resulting **posterior** is the updated distribution over
;; candidates.

^:kindly/hide-code
(kind/hiccup
 [:div.bp-process-strip {:role "img" :aria-label "Prior multiplied by likelihood produces unnormalised weights, which are normalised to produce the posterior"}
  [:div.bp-process-step [:strong "Prior"] [:small "before these data"]]
  [:div.bp-process-symbol {:aria-hidden "true"} "×"]
  [:div.bp-process-step [:strong "Likelihood"] [:small "what the data favour"]]
  [:div.bp-process-symbol {:aria-hidden "true"} "="]
  [:div.bp-process-step [:strong "Raw weights"] [:small "relative support"]]
  [:div.bp-process-symbol {:aria-hidden "true"} "→"]
  [:div.bp-process-step [:strong "Posterior"] [:small "normalised update"]]])

;; ## 1. Update: learning from globe tosses
;;
;; Imagine tossing and catching a globe. The point under a finger is recorded
;; as water or land. If a candidate says water is common, water observations
;; should be less surprising under it. This is the likelihood's job.
;;
;; For $W$ water observations and $L$ land observations, one particular ordered
;; sequence has probability
;;
;; $$p^W(1-p)^L.$$
;;
;; In plain English: multiply $p$ once for every water result and $1-p$ once
;; for every land result. This assumes the observations are independent once
;; $p$ is fixed.

^:kindly/hide-code
(math/explanation
 "math-ordered-sequence"
 "Probability of one particular water–land sequence"
 [["p" "The probability that one independent toss lands on water."]
  ["1 − p" "The complementary probability that one toss lands on land."]
  ["W" "The number of water observations in the sequence."]
  ["L" "The number of land observations in the sequence."]
  ["p^W(1 − p)^L" "The product of all water and land probability contributions."]]
 "This expression describes one specified ordering, such as W–L–W.")

^:kindly/hide-code
(defn binomial-coefficient
  "Number of orderings containing k successes among n observations."
  [n k]
  {:pre [(<= 0 k n)]}
  (let [k (min k (- n k))]
    (reduce (fn [acc i]
              (* acc (/ (- (inc n) i) i)))
            1.0
            (range 1 (inc k)))))

;; When only the counts matter, there are
;;
;; $$\binom{W+L}{W}=\frac{(W+L)!}{W!L!}$$
;;
;; possible orderings. Read this as: choose which $W$ of the $W+L$ positions
;; contain water. The exclamation mark means factorial—for example,
;; $3!=3\times2\times1$. Multiplying the ordering count by the probability of
;; one ordering gives the **binomial likelihood**:
;;
;; $$\Pr(W,L\mid p)=\binom{W+L}{W}p^W(1-p)^L.$$
;;
;; Read this as: “if candidate $p$ were fixed, what probability would this
;; model assign to seeing these water and land counts?” Once the data are fixed
;; and candidates vary, the same expression is called a likelihood.

^:kindly/hide-code
(math/explanation
 "math-binomial-likelihood"
 "The binomial likelihood"
 [["Pr(W,L | p)" "The probability of the observed counts, conditional on candidate p."]
  ["|" "‘Given’ or ‘conditional on.’"]
  ["(W + L choose W)" "The number of distinct sequences with the same counts."]
  ["n!" "n factorial: multiply the positive integers from n down to 1."]
  ["p^W(1 − p)^L" "The probability of one particular ordering."]]
 "The ordering count is constant across p for fixed data, so it changes scale but not the likelihood curve's shape.")

^:kindly/hide-code
(defn normalize-mean-one
  "Scale non-negative grid weights so their arithmetic mean is one."
  [weights]
  (let [mean-weight (/ (reduce + weights) (count weights))]
    (if (pos? mean-weight)
      (mapv #(/ % mean-weight) weights)
      (vec weights))))

^:kindly/hide-code
(defn binomial-likelihood
  "Likelihood of water and land counts for one candidate p."
  [p water land]
  (* (binomial-coefficient (+ water land) water)
     (Math/pow p water)
     (Math/pow (- 1.0 p) land)))

^:kindly/hide-code
(defn grid-posterior
  "Posterior grid weights for a prior and water/land observations."
  [prior water land]
  (->> probability-grid
       (mapv #(binomial-likelihood % water land))
       (mapv * prior)
       normalize-mean-one))

^:kindly/hide-code
(def uniform-prior (vec (repeat 201 1.0)))

^:kindly/hide-code
(def example-posterior
  (grid-posterior uniform-prior 6 3))

^:kindly/hide-code
(def example-posterior-mode
  (first (apply max-key second
                (map vector probability-grid example-posterior))))

^:kindly/hide-code
(kind/hiccup
 [:p.bp-note
  [:span.article-marker "Check"]
  (str "The executable six-water, three-land example uses "
       (count probability-grid) " candidates and peaks at p = "
       example-posterior-mode ".")])

;; Bayes' theorem names this update:
;;
;; $$\Pr(p\mid W,L)=\frac{\Pr(W,L\mid p)\Pr(p)}{\Pr(W,L)}.$$
;;
;; Read it from right to left: multiply the prior support for candidate $p$ by
;; the likelihood of the observations under that candidate, then divide by the
;; overall probability of the observations so all posterior probabilities sum
;; to 1. On this finite grid, normalising all products performs that division.

^:kindly/hide-code
(math/explanation
 "math-bayes-theorem"
 "Bayes’ theorem for the water proportion"
 [["Pr(p | W,L)" "The posterior support for p after observing W water and L land outcomes."]
  ["Pr(W,L | p)" "The likelihood of those observations under candidate p."]
  ["Pr(p)" "The prior support for p before these observations."]
  ["Pr(W,L)" "The overall or marginal probability of the data; it normalises the products."]
  ["likelihood × prior" "The unnormalised posterior weight for candidate p."]]
 "On a finite grid, divide each likelihood-times-prior product by the sum of all products.")

^:kindly/hide-code
(math/code-detail
 "code-normalise-posterior"
 "Normalising likelihood-times-prior weights"
 [:div
  [:p "The pipeline evaluates the likelihood at every candidate, multiplies candidate by candidate with the prior, then applies one common scale factor. Scaling to mean 1 rather than sum 1 preserves the same posterior shape used by the charts."]
  [:pre [:code "(defn grid-posterior [prior water land]\n  (->> probability-grid\n       (mapv #(binomial-likelihood % water land))\n       (mapv * prior)\n       normalize-mean-one))"]]
  [:p "Check: the article asserts that the 201 weights' arithmetic mean is 1 to floating-point tolerance."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/bayes_theorem_simulations.clj"} "View the complete posterior implementation"]]])

;; A sequential update needs no new rule. After one observation, the posterior
;; contains everything this model carries forward about $p$. It therefore
;; becomes the next observation's prior: yesterday's posterior is today's
;; prior. The dynamic strip inside the simulator shows the current prior,
;; latest-observation likelihood, raw product, and normalised posterior.

^:kindly/hide-code
(kind/hiccup
 [:details.bp-predict
  [:summary "Predict before revealing: what should one water observation do?"]
  [:div
   [:p "First make a prediction. Should candidates near p = 0, p = 0.5, or p = 1 gain the most relative support?"]
   [:p [:strong "Reveal:"] " one water observation has likelihood p, so it favours larger p values. A land observation has likelihood 1 − p and favours smaller values."]]])

^:kindly/hide-code
(kind/hiccup
 [:div.bp-reading-guide
  [:h3 "How to read the globe simulator"]
  [:ul
   [:li "Horizontal position is candidate water proportion p, from 0 to 1."]
   [:li "Curve height is relative support or likelihood; it is not the probability of one exact p."]
   [:li "Use Water or Land for deliberate evidence. Random sample uses hidden true p = 0.6."]
   [:li "Change the prior to see its influence early; add data to see the likelihood increasingly dominate."]]
  [:p.bp-caption "The two full-likelihood charts differ by a constant multiplier, so their normalised shapes match."]])

^:kindly/hide-code
(kind/hiccup
 [:div.bp-simulator
  [:div#globe-update-simulator
   [:p "Loading the globe-toss Bayesian update simulator…"]]
  [:noscript "This simulator needs JavaScript. The equations and executable Clojure above remain available without it."]])

^:kindly/hide-code
(math/code-detail
 "code-seeded-reset"
 "Seeded random globe observations and reset"
 [:div
  [:p "A random seed is a starting number for a repeatable pseudo-random sequence. Reset creates the generator from the same seed again, so it replays the same simulated observations. That is deterministic replay: identical versioned inputs and seed produce identical results."]
  [:pre [:code "(def update-seed 20260713)\n\n(defn generated-observation! []\n  (if (< (uniform! @update-rng) 0.6) :water :land))\n\n(defn reset-update! []\n  (cancel-update-timer!)\n  (reset! update-rng (make-rng update-seed))\n  (let [prior (:prior @update-state)\n        speed (:speed @update-state)]\n    (reset! update-state\n            (assoc initial-update-state :prior prior :speed speed))))"]]
  [:p "The browser keeps the sequence already drawn in browser state—data held in memory for the current page. Clearing replaces that state and rewinds the generator."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/bayes_theorem_simulations_interactive.cljs"} "View the complete browser implementation"]]])

^:kindly/hide-code
(kind/hiccup
 [:div.article-recap
  [:strong "Chapter 1 recap"]
  [:p "Build: list candidate values and specify a prior and likelihood. Check: normalise and inspect the update after known observations. Decide: carry the posterior forward as the prior for the next observation."]])

;; ## 2. Sample: turning uncertainty into a decision
;;
;; A posterior is a distribution, not automatically a single estimate. To act,
;; we must say what action is available and what mistakes cost. A **decision**
;; is the chosen action. A **loss function** assigns a cost to each possible
;; decision–truth pair. Change the loss and the best estimate can change even
;; when the posterior does not.
;;
;; Here the decision $d$ is a claimed water proportion. A perfect answer earns
;; $100; every 0.01 of absolute error loses $1. The **expected absolute loss**
;; is
;;
;; $$E[|d-p|\mid W,L] = \sum_p |d-p|\Pr(p\mid W,L).$$
;;
;; Read this as: for each possible $p$, calculate how far decision $d$ misses,
;; weight that error by the posterior support for $p$, and add. Choose the $d$
;; with the smallest weighted average error.

^:kindly/hide-code
(math/explanation
 "math-expected-absolute-loss"
 "Posterior expected absolute loss"
 [["d" "A candidate decision: the water proportion chosen for the bet."]
  ["p" "One possible true water proportion on the grid."]
  ["|d − p|" "Absolute error: the non-negative distance between decision and possible truth."]
  ["E[… | W,L]" "Expected value after conditioning on the observed counts."]
  ["Σ_p" "Add one weighted error for every candidate p."]
  ["Pr(p | W,L)" "The posterior probability weight assigned to candidate p."]]
 "The decision with the smallest posterior-weighted average absolute error is preferred.")

^:kindly/hide-code
(defn expected-absolute-loss
  "Expected absolute error at decision d for grid weights."
  [posterior d]
  (let [weight-total (reduce + posterior)]
    (/ (reduce + (map (fn [p weight]
                        (* (Math/abs (- d p)) weight))
                      probability-grid
                      posterior))
       weight-total)))

^:kindly/hide-code
(def example-losses
  (mapv #(expected-absolute-loss example-posterior %)
        probability-grid))

^:kindly/hide-code
(def example-minimum-loss
  (apply min-key second (map vector probability-grid example-losses)))

^:kindly/hide-code
(kind/hiccup
 [:p.bp-note
  [:span.article-marker "Check"]
  (str "For the executable six-water, three-land example, the grid decision "
       "minimising expected absolute loss is d = "
       (first example-minimum-loss) ".")])

;; Directly evaluating every $d$ works on this small grid. Another route is
;; **posterior sampling**: draw candidate values with frequency proportional to
;; their posterior probability. The cloud of draws approximates the
;; distribution. Under absolute loss, the posterior median minimises expected
;; loss.
;;
;; Repeating random draws to approximate a distribution or numerical result is
;; **Monte Carlo approximation**. More draws reduce simulation noise, but do
;; not repair a poor model. This interaction animates 2,000 draws and compares
;; them with a fixed 10,000-draw run.

^:kindly/hide-code
(kind/hiccup
 [:details.bp-predict
  [:summary "Predict before revealing: where should the sample median settle?"]
  [:div
   [:p "Inspect the posterior from 100 globe observations. Predict whether its median will settle below, near, or above the hidden true p = 0.6."]
   [:p [:strong "Then test it:"] " start slowly enough to see individual draws, increase the speed, and compare the live median with the direct loss minimum."]]])

^:kindly/hide-code
(kind/hiccup
 [:div.bp-reading-guide
  [:h3 "How to read the sampling simulator"]
  [:ul
   [:li "The first row fixes one 100-observation dataset and shows its posterior and loss curve."]
   [:li "The trace plots draw order vertically by sampled p; the histogram counts draws; the density rescales those counts."]
   [:li "The animated and fixed panels use different seeded streams but the same posterior."]
   [:li "Watch the running median wobble, then stabilise as the number of draws increases."]]])

^:kindly/hide-code
(kind/hiccup
 [:div.bp-simulator
  [:div#posterior-sampling-simulator
   [:p "Loading the posterior sampling and decision simulator…"]]
  [:noscript "This simulator needs JavaScript. The expected-loss calculation above remains available without it."]])

^:kindly/hide-code
(math/code-detail
 "code-weighted-sampling"
 "Drawing grid values in proportion to posterior weight"
 [:div
  [:p "First normalise the 201 weights so they sum to 1. Draw a uniform number between 0 and 1, then walk through cumulative posterior mass until it reaches that number. Return the corresponding grid value."]
  [:pre [:code "(defn weighted-grid-sample! [rng]\n  (let [target (uniform! rng)]\n    (loop [index 0\n           cumulative (first fixed-posterior-mass)]\n      (if (or (>= cumulative target)\n              (= index (dec (count probability-grid))))\n        (nth probability-grid index)\n        (recur (inc index)\n               (+ cumulative\n                  (nth fixed-posterior-mass (inc index))))))))"]]
  [:p "The fixed 10,000 draws are computed once from their own seed. The animated stream is reset independently, so interaction never changes the comparison result."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/bayes_theorem_simulations_interactive.cljs"} "View the complete posterior-sampling implementation"]]])

^:kindly/hide-code
(kind/hiccup
 [:div.article-recap
  [:strong "Chapter 2 recap"]
  [:p "Build: define the action and its loss. Check: compare the direct grid minimum with a seeded Monte Carlo approximation. Decide: report the estimate appropriate to the declared loss, not a context-free ‘best’ number."]])

;; ## 3. Scale up: a Gaussian model with two parameters
;;
;; The globe model had one parameter. Adult height introduces two. A
;; **Gaussian distribution**—the familiar symmetric bell shape—is described by
;; its **mean** $\mu$, which locates the centre, and **standard deviation**
;; $\sigma$, which measures typical spread around the centre. Its **density**
;; describes relative concentration near a height; because height is
;; continuous, density at one exact point is not itself a probability.
;;
;; For observed height $h$, the Gaussian density under candidate
;; $(\mu,\sigma)$ is
;;
;; $$f(h\mid\mu,\sigma)=
;; \frac{1}{\sigma\sqrt{2\pi}}
;; \exp\left(-\frac{(h-\mu)^2}{2\sigma^2}\right).$$
;;
;; Read it as: density is highest when $h$ is near candidate mean $\mu$ and
;; falls as the squared distance grows; candidate spread $\sigma$ controls how
;; quickly it falls. For a fixed observed height, comparing this density across
;; parameter pairs gives the likelihood.

^:kindly/hide-code
(math/explanation
 "math-gaussian-density"
 "Gaussian density for one observed height"
 [["f(h | μ,σ)" "The density at height h for the candidate Gaussian described by μ and σ."]
  ["h" "The observed adult height."]
  ["μ" "The candidate mean, locating the distribution's centre."]
  ["σ" "The positive candidate standard deviation, controlling spread."]
  ["π" "Pi, appearing in the Gaussian normalising factor."]
  ["exp(a)" "The exponential function e raised to power a."]
  ["(h − μ)^2" "The squared distance of the observation from the candidate mean."]]
 "For fixed h, this density is the observation's likelihood across candidate μ and σ pairs.")

^:kindly/hide-code
(defn normal-density
  "Gaussian probability density at x."
  [x mean sd]
  (/ (Math/exp (/ (* -0.5 (Math/pow (- x mean) 2.0))
                  (Math/pow sd 2.0)))
     (* (Math/sqrt (* 2.0 Math/PI)) sd)))

^:kindly/hide-code
(def example-height-density
  (normal-density 151.765 155.0 8.0))

;; The preserved grid contains 41 means from 150 to 160 cm crossed with 41
;; standard deviations from 7 to 9 cm: $41\times41=1{,}681$ candidate models.
;; Each observed height supplies a likelihood surface. Multiplying that surface
;; by the preceding posterior and normalising produces the next posterior—the
;; same sequential rule as before, now across a two-dimensional grid.

^:kindly/hide-code
(math/code-detail
 "code-gaussian-update"
 "Sequentially updating the 41 × 41 Gaussian grid"
 [:div
  [:p "The cache starts with one prior surface. For each required height, the browser computes all 1,681 likelihoods, multiplies them pointwise by the last posterior, normalises, and appends both surfaces."]
  [:pre [:code "(defn ensure-gaussian-step! [target-posterior-count]\n  (loop []\n    (let [{:keys [posteriors likelihoods]} @gaussian-cache\n          observed-count (dec (count posteriors))]\n      (when (< observed-count target-posterior-count)\n        (let [likelihood (height-likelihood\n                          (nth adult-heights observed-count))\n              posterior (normalize-mean-one\n                         (mapv * (peek posteriors) likelihood))]\n          (reset! gaussian-cache\n                  {:posteriors (conj posteriors posterior)\n                   :likelihoods (conj likelihoods likelihood)})\n          (recur))))))"]]
  [:p "Caching avoids recomputing earlier steps. Previous and Next only change which already reproducible update is displayed."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/bayes_theorem_simulations_interactive.cljs"} "View the complete Gaussian-grid implementation and adult-height data"]]])

^:kindly/hide-code
(kind/hiccup
 [:details.bp-predict
  [:summary "Predict before revealing: what will repeated heights do to the grid?"]
  [:div
   [:p "Before pressing Next, predict whether plausible parameter pairs will spread across the map or concentrate. Which direction should a relatively short height pull the mean?"]
   [:p [:strong "Reveal by stepping:"] " the likelihood for one height is broad, but repeated multiplication concentrates posterior support where one mean–spread pair explains the complete dataset reasonably well."]]])

^:kindly/hide-code
(kind/hiccup
 [:div.bp-reading-guide
  [:h3 "How to read the height simulator"]
  [:ul
   [:li "Horizontal position is candidate mean μ; vertical position is candidate standard deviation σ."]
   [:li "Within each panel, stronger opacity means greater relative support. The scale is logarithmic."]
   [:li "Compare location and concentration, not the absolute shade between different panels."]
   [:li "Read left to right: posterior before this height, this height's likelihood, posterior after the update."]]])

^:kindly/hide-code
(kind/hiccup
 [:div.bp-simulator
  [:div#gaussian-height-simulator
   [:p "Loading the sequential Gaussian updating simulator…"]]
  [:noscript "This simulator needs JavaScript. The Gaussian density and explanation above remain available without it."]
  [:script {:type "application/x-scittle"
            :src "bayes_theorem_simulations_interactive.cljs"}]])

^:kindly/hide-code
(kind/hiccup
 [:div.article-recap
  [:strong "Chapter 3 recap"]
  [:p "Build: cross candidate means and standard deviations into a two-parameter grid. Check: inspect each likelihood and the before/after surfaces. Decide: retain the complete posterior surface, not only its highest cell."]])

;; ## 4. Make the lesson reproducible
;;
;; An executable article joins an explanation to calculations that can be run
;; again. **Source code** is the human-readable set of instructions stored in
;; the `.clj` and `.cljs` files. A **runtime** is the software environment that
;; executes those instructions. **Browser state** is the current in-memory data
;; behind controls, such as accumulated tosses or the selected step.
;;
;; A **render** turns source into a reader-facing artifact. Here Clay evaluates
;; the Clojure article using Kindly's display conventions and writes QMD
;; (Quarto Markdown); Quarto turns QMD into HTML; Scittle executes the
;; ClojureScript in the browser; Reagent updates semantic HTML and accessible
;; SVG as browser state changes.

^:kindly/hide-code
(kind/mermaid
 "flowchart LR
    A[Clojure article] --> B[Clay / Kindly]
    B --> C[QMD]
    C --> D[Quarto HTML]
    D --> E[Scittle / Reagent]
    E --> F[semantic HTML + SVG]")

^:kindly/hide-code
(kind/hiccup
 [:p.bp-engineering-caption
  "The source-to-browser path. Clay, Scittle, and Quarto behavior follows their official documentation; links appear in Sources."])

;; **Accessibility** means people can perceive and operate the article through
;; different senses and input methods. Each SVG has a programmatic title and
;; description; every control has a visible label and keyboard behavior; colour
;; is paired with position, text, line style, or opacity; layouts reflow on
;; narrow screens.
;;
;; A **regression check** asks whether an established property still holds
;; after a change. The assertions below protect the 201-point grid, combinatorial
;; count, normalisation, decision, and positive density. The browser checks add
;; interaction, console, layout, focus, label, and theme verification. A random
;; seed makes simulations replayable, so a changed result can be distinguished
;; from ordinary random variation.

^:kindly/hide-code
(math/code-detail
 "code-browser-mounts"
 "Mounting all three browser components"
 [:div
  [:p "A mount point is an empty, uniquely identified HTML element reserved for an interactive component. On page load, the browser finds each preserved ID and asks Reagent to render the matching component there."]
  [:pre [:code "(defn ^:export mount []\n  (when-let [root (js/document.getElementById\n                   \"globe-update-simulator\")]\n    (rdom/render [globe-update-simulator] root))\n  (when-let [root (js/document.getElementById\n                   \"posterior-sampling-simulator\")]\n    (rdom/render [posterior-sampling-simulator] root))\n  (when-let [root (js/document.getElementById\n                   \"gaussian-height-simulator\")]\n    (rdom/render [gaussian-height-simulator] root)))"]]
  [:p "The conditional lookup lets the same source load safely even if one mount is absent. The three IDs remain unchanged from the published article."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/bayes_theorem_simulations_interactive.cljs"} "View the complete mounting and browser code"]]])

^:kindly/hide-code
(kind/hiccup
 [:div.article-recap
  [:strong "From Bayes to vocabulary estimation"]
  [:p "The next article reuses the same structure: candidate knowing rates receive priors; quiz responses supply likelihoods; posteriors predict untested items; seeded draws describe uncertainty; and an explicit stopping decision depends on the intended measurement task."]
  [:p [:a {:href "beta_binomial_first_pass.html"} "Continue to the stratified Beta–binomial vocabulary model →"]]])

;; ## Sources
;;
;; - Jamie Pratt and the JointProb group, [original interactive simulations](https://jointprob.github.io/jointprob-shadow-cljs/#normal-distribution) and [ClojureScript source](https://github.com/jointprob/jointprob-shadow-cljs/tree/master/src/cljs).
;; - Richard McElreath, *Statistical Rethinking*, examples from sections 2.2, 3.2, and 4.3. The prompts here are paraphrases.
;; - [Clay documentation](https://scicloj.github.io/clay/) on executable Clojure documents and Quarto output.
;; - [Scittle documentation](https://scicloj.github.io/scittle/) on running ClojureScript in a browser.
;; - [Quarto HTML documentation](https://quarto.org/docs/output-formats/html-basics.html) on rendering HTML output.
;; - W3C Web Accessibility Initiative, [SVG accessibility guidance](https://www.w3.org/WAI/tutorials/images/tips/) and [accessible names and descriptions](https://www.w3.org/WAI/ARIA/apg/practices/names-and-descriptions/).

^:kindly/hide-code
(do
  (assert (= 201 (count probability-grid)))
  (assert (< (Math/abs (- 84.0 (binomial-coefficient 9 6))) 1.0e-12))
  (assert (< (Math/abs (- 1.0
                          (/ (reduce + example-posterior)
                             (count example-posterior))))
             1.0e-12))
  (assert (= 0.645 (first example-minimum-loss)))
  (assert (pos? (normal-density 151.765 155.0 8.0)))
  (kind/hiccup
   [:p.bp-note
    [:span.article-marker "Regression check"]
    "All executable assertions passed."]))
