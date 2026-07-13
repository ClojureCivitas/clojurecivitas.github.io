^{:kindly/hide-code true
  :kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Bayes' Theorem, Revisited: Three Interactive Simulations"
         :quarto {:author :jamiep
                  :description "Rebuilding three earlier Bayesian simulations with Kindly, Scittle, Reagent, and accessible SVG."
                  :type :post
                  :date "2026-07-13"
                  :category :concepts
                  :tags [:bayesian-statistics :clojure :clojurescript :scittle :simulation]
                  :keywords [:bayes-theorem :grid-approximation :posterior-sampling :normal-distribution :data-visualisation]}}}

(ns language-learning.vocabulary-estimation.bayes-theorem-simulations
  (:require [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(kind/hiccup
 [:style
  "#title-block-header{padding-top:.75rem}#title-block-header h1{line-height:1.15;overflow-wrap:anywhere}mjx-container[display=true]{max-width:100%;overflow-x:auto;overflow-y:hidden}.bp-callout{border-left:4px solid #2780e3;background:#f2f7fc;color:#17202a;padding:1rem 1.15rem;margin:1.4rem 0;border-radius:.25rem}.bp-callout strong{display:block;margin-bottom:.3rem}.bp-simulator{margin:1.5rem 0}.bp-shell{border:1px solid #ced4da;border-radius:.65rem;padding:clamp(.8rem,3vw,1.3rem);min-width:0}.bp-shell h3{margin-top:0}.bp-shell h4{font-size:1rem}.bp-details{border:1px solid #dee2e6;border-radius:.45rem;margin:.85rem 0;background:var(--bs-body-bg,#fff)}.bp-details summary{cursor:pointer;font-weight:700;padding:.75rem 1rem}.bp-details>div{padding:0 1rem 1rem}.bp-controls{display:flex;align-items:end;gap:.65rem;flex-wrap:wrap;margin:1rem 0}.bp-controls label,.bp-field label{font-weight:700;font-size:.88rem}.bp-controls input[type=range]{width:min(15rem,100%);accent-color:#2780e3}.bp-button{border:1px solid #6c757d;border-radius:.35rem;padding:.55rem .85rem;font-weight:600;cursor:pointer;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.bp-button.bp-primary,.bp-button[aria-pressed=true]{border-color:#2780e3;background:#2780e3;color:#fff}.bp-button:disabled{opacity:.45;cursor:not-allowed}.bp-button:focus-visible,.bp-select:focus-visible,.bp-controls input:focus-visible{outline:3px solid color-mix(in srgb,#2780e3 45%,transparent);outline-offset:2px}.bp-select{border:1px solid #6c757d;border-radius:.35rem;padding:.5rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.bp-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,20rem),1fr));gap:1rem;margin:1rem 0}.bp-chart{min-width:0;border:1px solid #dee2e6;border-radius:.5rem;padding:.7rem;margin:0;background:var(--bs-body-bg,#fff)}.bp-chart h4{margin:.1rem 0 .2rem;font-size:.95rem;line-height:1.25}.bp-chart svg{display:block;width:100%;height:auto}.bp-caption,.bp-note{font-size:.86rem;color:var(--bs-secondary-color,#5c636a);margin:.4rem 0 0}.bp-stat{font-variant-numeric:tabular-nums;margin:.4rem 0}.bp-sample-sequence{font-family:var(--bs-font-monospace,monospace);font-size:.82rem;overflow-wrap:anywhere;max-height:6rem;overflow:auto;padding:.55rem;border-radius:.35rem;background:var(--bs-tertiary-bg,#f4f4f4)}.bp-axis{stroke:currentColor;stroke-opacity:.5}.bp-guide{stroke:currentColor;stroke-opacity:.11}.bp-line{fill:none;stroke:#2780e3;stroke-width:3;vector-effect:non-scaling-stroke}.bp-line-secondary{fill:none;stroke:#e69f00;stroke-width:2;stroke-dasharray:6 4;vector-effect:non-scaling-stroke}.bp-bar-water{fill:#2780e3}.bp-bar-land{fill:#c99052}.bp-points{fill:none;stroke:#2780e3;stroke-width:2;stroke-linecap:round;vector-effect:non-scaling-stroke}.bp-progress{width:100%;height:.55rem;accent-color:#2780e3}.bp-formula{font-family:var(--bs-font-monospace,monospace);overflow-wrap:anywhere}.bp-heat-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,17rem),1fr));gap:1rem}.bp-empty{display:grid;place-items:center;min-height:11rem;border:1px dashed #adb5bd;border-radius:.35rem;color:var(--bs-secondary-color,#5c636a);text-align:center;padding:1rem}.bp-sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}@media(max-width:575px){.bp-shell{padding:.75rem}.bp-controls{align-items:stretch}.bp-button{flex:1}.bp-chart{padding:.5rem}}"])

^:kindly/hide-code
(kind/hiccup
 [:style
  ".series-toc{min-width:0;border:1px solid #ced4da;border-radius:.6rem;padding:clamp(.85rem,3vw,1.2rem);margin:1.4rem 0;background:var(--bs-body-bg,#fff)}.series-toc h2{font-size:1.2rem;margin:0 0 .55rem}.series-toc p{margin:0 0 .7rem}.series-toc ol{margin:0;padding-left:1.45rem}.series-toc li{padding:.18rem 0}.series-status{display:inline-block;margin-left:.35rem;font-size:.7rem;font-weight:700;letter-spacing:.04em;text-transform:uppercase;color:var(--bs-secondary-color,#5c636a)}.bp-change-details>summary{font-size:1.3rem;line-height:1.2}.bp-change-details ul{margin-bottom:0}"])

;; I thought I'd reproduce my previous learning about [Bayes' theorem
;; simulations](https://jointprob.github.io/jointprob-shadow-cljs/#normal-distribution)
;; ([source code](https://github.com/jointprob/jointprob-shadow-cljs/tree/master/src/cljs))
;; as a Civitas article. I previously produced these simulations when I was
;; working with [Daniel's JointProb
;; group](https://scicloj.github.io/docs/community/groups/jointprob/) and used
;; visualisation tools that were easily available at the time. I'm pretty
;; confident that the current visualisation tools are going to do a lot better
;; job with this simulation, judging by how wonderful a job they did with [my
;; first Civitas article](beta_binomial_first_pass.html).

^:kindly/hide-code
(kind/hiccup
 [:nav.series-toc {:aria-labelledby "series-contents-heading"}
  [:h2#series-contents-heading "Series contents"]
  [:p
   [:strong "Revisiting basic Bayes' theorem and applying it to a real word problem: estimating vocabulary size from "]
   [:a {:href "https://lexibench.com/"} "Lexibench.com"]
   [:strong " quiz responses."]]
  [:ol
   [:li [:a {:href "bayes_theorem_simulations.html"}
         "Bayes' Theorem, Revisited: Three Interactive Simulations"]
    [:span.series-status "published"]]
   [:li [:a {:href "beta_binomial_first_pass.html"}
         "Estimating Vocabulary Size with a Simple Bayesian Model"]
    [:span.series-status "published"]]
   [:li "Does Pair Frequency Predict Learner Responses?"
    [:span.series-status "planned"]]
   [:li "From Self-Reported CEFR to a Versioned Lemma–Form-Pair Pool"
    [:span.series-status "planned"]]
   [:li "From Correlated Form Pairs to Latent Lemma Knowledge"
    [:span.series-status "planned"]]
   [:li "Modelling Correct, Wrong, and Don't-Know Separately"
    [:span.series-status "planned"]]
   [:li "Calibrating Items Before IRT and Adaptive Selection"
    [:span.series-status "planned"]]
   [:li "When Contexts and Senses Become Identifiable"
    [:span.series-status "planned"]]]])

^:kindly/hide-code
(kind/hiccup
 [:details.bp-details.bp-change-details
  [:summary "What I changed in this recreation"]
  [:div
   [:p "I kept the old application's complete three-part teaching sequence and its numerical grids, but rebuilt how it is delivered:"]
   [:ul
    [:li "The three routed Shadow-CLJS pages are now one executable Civitas article, so the globe update, posterior-sampling decision, and Gaussian update can be read as one argument."]
    [:li "The compiled Shadow-CLJS application is replaced by the same Kindly–Scittle–Reagent arrangement used in my first article. The mathematical core lives in this " [:code ".clj"] " article; browser state and controls live in one adjacent " [:code ".cljs"] " file."]
    [:li "Vega, Hanami, Semantic UI React, and MathJax React are removed. Kindly and Quarto render the prose and equations; semantic HTML supplies the controls; Reagent renders the visualisations directly as responsive SVG."]
    [:li "The original 201-point probability grid, four selectable priors, 200-item globe simulation, 2,000 animated posterior draws, 10,000-draw comparison, 41 × 41 Gaussian parameter grid, and complete adult-height dataset are preserved."]
    [:li "Previously ambient calls to " [:code "rand"] " are replaced by explicit seeds. Reset now replays the same globe data and posterior draws, making screenshots, explanations, and later regression checks reproducible."]
    [:li "The old long quotations from " [:em "Statistical Rethinking"] " are paraphrased while retaining their examples, section references, and attribution."]
    [:li "Every chart now has an SVG title and description; controls have associated labels and explicit keyboard behavior; status text is announced selectively; and the layout collapses without horizontal overflow on small screens."]
    [:li "The Gaussian heat maps use within-panel logarithmic opacity. This keeps low but non-zero plausibilities visible as the posterior concentrates. Their colour is therefore for comparing location and shape within a panel, not absolute density across panels."]
    [:li "I added direct " [:strong "Previous"] " and " [:strong "Next"] " controls to the height simulation and bounded its final update, avoiding the old end-of-data indexing edge case."]]
   [:p "The original project was a three-page Shadow-CLJS application using Vega, Hanami, Semantic UI React, and MathJax React. This version keeps the three lessons together in one executable Civitas article. Kindly renders the article, while Scittle and Reagent run the controls directly in the browser. The charts are responsive SVG with text alternatives, so there is no JavaScript build step and no charting wrapper between the data and the marks."]
   [:p "All simulated randomness below is seeded. Resetting a simulation replays the same sequence, which makes the explanations and any later checks repeatable."]]])

^:kindly/hide-code
(kind/hiccup
 [:div.bp-callout
  [:strong "What is being recreated"]
  "The globe-toss grid approximation, sampling from its posterior to make a decision under absolute loss, and sequential Gaussian updating for adult heights. The long quotations in the old application are paraphrased here and still attributed to their source."])

;; ## 1. Updating a probability with globe tosses
;;
;; The first example follows the globe thought experiment in section 2.2 of
;; Richard McElreath's *Statistical Rethinking*. Toss a small globe, catch it,
;; and note whether the point beneath your index finger is water or land. The
;; unknown parameter $p$ is the proportion of the globe covered by water.
;;
;; I approximate the continuous range of $p$ using 201 candidates:
;;
;; $$p \in \{0, 0.005, 0.010, \ldots, 0.995, 1\}.$$

(def probability-grid
  (mapv #(/ % 200.0) (range 201)))

(defn binomial-coefficient
  "Number of orderings containing k successes among n observations."
  [n k]
  {:pre [(<= 0 k n)]}
  (let [k (min k (- n k))]
    (reduce (fn [acc i]
              (* acc (/ (- (inc n) i) i)))
            1.0
            (range 1 (inc k)))))

(defn normalize-mean-one
  "Scale non-negative grid weights so their arithmetic mean is one."
  [weights]
  (let [mean-weight (/ (reduce + weights) (count weights))]
    (if (pos? mean-weight)
      (mapv #(/ % mean-weight) weights)
      (vec weights))))

(defn binomial-likelihood
  "Likelihood of water and land counts for one candidate p."
  [p water land]
  (* (binomial-coefficient (+ water land) water)
     (Math/pow p water)
     (Math/pow (- 1.0 p) land)))

(defn grid-posterior
  "Posterior grid weights for a prior and water/land observations."
  [prior water land]
  (->> probability-grid
       (mapv #(binomial-likelihood % water land))
       (mapv * prior)
       normalize-mean-one))

(def uniform-prior (vec (repeat 201 1.0)))

(def example-posterior
  (grid-posterior uniform-prior 6 3))

{:grid-points (count probability-grid)
 :example :six-water-three-land
 :posterior-mode (first (apply max-key second
                               (map vector probability-grid example-posterior)))}

;; For $W$ water observations and $L$ land observations, the probability of one
;; particular ordered sequence is
;;
;; $$p^W(1-p)^L.$$
;;
;; There are
;;
;; $$\binom{W+L}{W}=\frac{(W+L)!}{W!L!}$$
;;
;; orderings with the same counts, giving the binomial likelihood
;;
;; $$\Pr(W,L\mid p)=\binom{W+L}{W}p^W(1-p)^L.$$
;;
;; Bayes' theorem combines that likelihood with the prior:
;;
;; $$\Pr(p\mid W,L)=\frac{\Pr(W,L\mid p)\Pr(p)}{\Pr(W,L)},$$
;;
;; where the denominator is the average probability of the data across the
;; prior. On a grid, normalising the products performs the same job.
;;
;; The simulator restores all four priors from the old application: uniform,
;; step up, step down, and a symmetric ramp. Add water or land deliberately, or
;; let a seeded process whose true water probability is 0.6 generate up to 200
;; observations. The panels separate the previous posterior, the latest
;; observation's likelihood, the full likelihood, and the new posterior.

^:kindly/hide-code
(kind/hiccup
 [:div.bp-simulator
  [:div#globe-update-simulator
   [:p "Loading the globe-toss Bayesian update simulator…"]]
  [:noscript "This simulator needs JavaScript. The equations and executable Clojure above remain available without it."]])

;; The ordered-sequence likelihood and the likelihood of any ordering have the
;; same shape as functions of $p$: the binomial coefficient is constant for
;; fixed $W$ and $L$. Their raw vertical scales differ, but normalising either
;; curve produces the same visual shape. Keeping both panels makes that fact
;; visible rather than leaving it buried in the algebra.
;;
;; ## 2. Sampling from the posterior and choosing a decision
;;
;; Section 3.2 of *Statistical Rethinking* turns the globe problem into a bet.
;; Choose a value $d$ for the proportion of water. A perfect answer earns $100$,
;; but the payoff falls in proportion to the absolute error $|d-p|$. As in the
;; previous application, I make that concrete as a loss of $1 for every 0.01 of
;; error.
;;
;; The browser first generates a reproducible dataset of 100 globe observations
;; with true $p=0.6$, then calculates its grid posterior. For every candidate
;; decision $d$, the expected absolute loss is
;;
;; $$E[|d-p|\mid W,L] = \sum_p |d-p|\Pr(p\mid W,L).$$

(defn expected-absolute-loss
  "Expected absolute error at decision d for grid weights."
  [posterior d]
  (let [weight-total (reduce + posterior)]
    (/ (reduce + (map (fn [p weight]
                        (* (Math/abs (- d p)) weight))
                      probability-grid
                      posterior))
       weight-total)))

(def example-losses
  (mapv #(expected-absolute-loss example-posterior %)
        probability-grid))

(def example-minimum-loss
  (apply min-key second (map vector probability-grid example-losses)))

{:example :six-water-three-land
 :minimum-loss-decision (first example-minimum-loss)
 :expected-absolute-loss (second example-minimum-loss)}

;; The direct calculation iterates over decisions from 0 to 1 in steps of
;; 0.005. For each decision it multiplies the loss at every possible $p$ by that
;; $p$'s posterior weight and averages over the curve. The lowest point of the
;; resulting loss curve is the decision that minimises expected loss.
;;
;; We can reach the same answer by drawing possible values of $p$ from the
;; posterior. Under absolute loss, the posterior median is the optimal
;; decision. The interactive view animates up to 2,000 draws, then compares
;; their trace, 200-bin counts, and standardised density with a fixed 10,000-draw
;; simulation. Every visible summary uses the draws shown by that simulation.

^:kindly/hide-code
(kind/hiccup
 [:div.bp-simulator
  [:div#posterior-sampling-simulator
   [:p "Loading the posterior sampling and decision simulator…"]]
  [:noscript "This simulator needs JavaScript. The expected-loss calculation above remains available without it."]])

;; ## 3. Updating a Gaussian model for adult heights
;;
;; The final simulation follows section 4.3 of *Statistical Rethinking*. A
;; Gaussian distribution has two parameters: its mean $\mu$ and standard
;; deviation $\sigma$. There are infinitely many possible pairs, so the
;; original exercise considers a finite grid and ranks each candidate Gaussian
;; by how compatible it is with the observed adult heights.
;;
;; For an observed height $h$, a candidate $(\mu,\sigma)$ has density
;;
;; $$f(h\mid\mu,\sigma)=
;; \frac{1}{\sigma\sqrt{2\pi}}
;; \exp\left(-\frac{(h-\mu)^2}{2\sigma^2}\right).$$

(defn normal-density
  "Gaussian probability density at x."
  [x mean sd]
  (/ (Math/exp (/ (* -0.5 (Math/pow (- x mean) 2.0))
                  (Math/pow sd 2.0)))
     (* (Math/sqrt (* 2.0 Math/PI)) sd)))

(normal-density 151.765 155.0 8.0)

;; The grid matches the old simulation: 41 values of $\mu$ from 150 to 160 and
;; 41 values of $\sigma$ from 7 to 9, or 1,681 candidate Gaussians. The prior is
;; uniform over $\sigma$ and gives $\mu$ a Normal(178, 20) density. For each
;; height, the display shows three heat maps:
;;
;; 1. the posterior before considering the next height;
;; 2. the relative likelihood supplied by that height;
;; 3. the posterior after multiplying and normalising.
;;
;; Move one height at a time or play through the complete dataset. A logarithmic
;; colour scale keeps low but non-zero plausibilities visible after the
;; posterior becomes concentrated.

^:kindly/hide-code
(kind/hiccup
 [:div.bp-simulator
  [:div#gaussian-height-simulator
   [:p "Loading the sequential Gaussian updating simulator…"]]
  [:noscript "This simulator needs JavaScript. The Gaussian density and explanation above remain available without it."]
  [:script {:type "application/x-scittle"
            :src "bayes_theorem_simulations_interactive.cljs"}]])

;; ## What this recreation is for
;;
;; This is deliberately a record of my earlier learning. It does not
;; silently turn these examples into the vocabulary estimator described in my
;; first Civitas article; it exposes the probability tools that helped me get
;; there.
;;
;; ## Sources
;;
;; - Jamie Pratt and the JointProb group, [original interactive simulations](https://jointprob.github.io/jointprob-shadow-cljs/#normal-distribution) and [ClojureScript source](https://github.com/jointprob/jointprob-shadow-cljs/tree/master/src/cljs).
;; - Richard McElreath, *Statistical Rethinking*, examples from sections 2.2, 3.2, and 4.3. The prompts above are paraphrases.
;; - [My first Civitas article: *Estimating Vocabulary Size with a Simple Bayesian Model*](beta_binomial_first_pass.html).

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
  :verified)
