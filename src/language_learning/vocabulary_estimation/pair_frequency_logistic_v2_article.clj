^{:kindly/hide-code true
  :kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Does Pair Frequency Predict Learner Responses?"
         :quarto {:author :jamiep
                  :description "A provisional continuous difficulty model before item calibration—and the simulation gate it failed."
                  :type :post
                  :date "2026-07-13"
                  :category :concepts
                  :tags [:bayesian-statistics :language-learning :clojure :simulation]
                  :keywords [:vocabulary-estimation :logistic-regression :pair-frequency :model-validation]}}}

(ns language-learning.vocabulary-estimation.pair-frequency-logistic-v2-article
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [language-learning.vocabulary-estimation.math-explanations :as math]
            [language-learning.vocabulary-estimation.pair-frequency-logistic-v2 :as v2]
            [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(kind/hiccup
 [:style
  ".pf-callout{border-left:4px solid #2780e3;background:#f2f7fc;color:#17202a;padding:1rem 1.15rem;margin:1.35rem 0;border-radius:.3rem}.pf-callout.fail{border-color:#c44536;background:#fff1ef}.pf-callout.provisional{border-color:#e69f00;background:#fff8e6}.pf-callout strong{display:block;margin-bottom:.3rem}.pf-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,15rem),1fr));gap:1rem;margin:1.25rem 0}.pf-card{min-width:0;border:1px solid #dee2e6;border-radius:.55rem;padding:1rem;background:var(--bs-body-bg,#fff)}.pf-card h3{font-size:1rem;margin:0 0 .4rem}.pf-card p{margin:.25rem 0}.pf-table-wrap{overflow-x:auto;margin:1.25rem 0}.pf-table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}.pf-table th,.pf-table td{padding:.55rem .7rem;border-bottom:1px solid #dee2e6;text-align:right;white-space:nowrap}.pf-table th:first-child,.pf-table td:first-child{text-align:left}.pf-table thead th{border-bottom:2px solid #adb5bd}.pf-sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}.pf-code{overflow-wrap:anywhere}.pf-lab{border:1px solid #ced4da;border-radius:.65rem;padding:clamp(.8rem,3vw,1.3rem);min-width:0}.series-toc{min-width:0;border:1px solid #ced4da;border-radius:.6rem;padding:clamp(.85rem,3vw,1.2rem);margin:1.4rem 0;background:var(--bs-body-bg,#fff)}.series-toc h2{font-size:1.2rem;margin:0 0 .55rem}.series-toc p{margin:0 0 .7rem}.series-toc ol{margin:0;padding-left:1.45rem}.series-toc li{padding:.18rem 0}.series-status{display:inline-block;margin-left:.35rem;font-size:.7rem;font-weight:700;letter-spacing:.04em;text-transform:uppercase;color:#4f5b66}.quarto-dark .series-status{color:#b9c7d2}@media(max-width:575px){.pf-table th,.pf-table td{padding:.45rem}.pf-card{padding:.8rem}}"])

^:kindly/hide-code
(math/styles)

^:kindly/hide-code
(kind/hiccup
 [:nav.series-toc {:aria-labelledby "series-contents-heading"}
  [:h2#series-contents-heading "Series contents"]
  [:p [:strong "Learning the theory and developing a scorer for "]
   [:a {:href "https://lexibench.com/"} "Lexibench.com"]]
  [:ol
   [:li [:a {:href "bayes_theorem_simulations.html"}
         "Bayes' Theorem, Revisited: Three Interactive Simulations"]
    [:span.series-status "published"]]
   [:li [:a {:href "beta_binomial_first_pass.html"}
         "Estimating Vocabulary Size with a Simple Bayesian Model"]
    [:span.series-status "published"]]
   [:li [:a {:href "pair_frequency_logistic_v2_article.html"}
         "Does Pair Frequency Predict Learner Responses?"]
    [:span.series-status "current"]]
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
(math/global-controls)

;; ## A useful model that did not earn promotion
;;
;; The first post estimated a separate knowing rate in each of eight frequency
;; strata. This follow-up removes those artificial difficulty steps. It asks
;; whether one continuous pair-frequency curve can support better inference
;; while **item selection remains balanced, non-adaptive, and unchanged**.

^:kindly/hide-code
(kind/hiccup
 [:div.pf-callout.fail
  [:strong "Result: do not promote v2"]
  "No candidate stopping rule passed the precommitted tuning gate. The target contract therefore remains stratified Beta–binomial v1. This is a negative model-development result, not evidence that frequency contains no signal."])

;; External evidence makes frequency a reasonable provisional predictor, but
;; does not calibrate Lexibench's Polish lemma–surface-form pairs:
;;
;; - [Mandera et al.](https://biblio.ugent.be/publication/5878584) showed that
;;   Polish corpus-frequency measures predict lexical-decision performance, and
;;   that subtitle and written-corpus measures contribute differently.
;; - [Hashimoto](https://scholarsarchive.byu.edu/facpub/6673/) found only a
;;   moderate relationship (`r = .50`, `r² = .25`) between frequency and Rasch
;;   word difficulty for 403 English learners.
;; - [Culligan](https://eric.ed.gov/?id=EJ1081136) found that log frequency from
;;   large corpora outperformed simpler corpus proxies considered in that study,
;;   while direct testing explained difficulty better.
;; - [Ha, Nguyen, and Stoeckel](https://journals.sagepub.com/doi/10.1177/02655322241263628)
;;   found age of exposure and contextual distinctiveness ahead of frequency in
;;   their random-forest analysis of meaning-recall difficulty.
;; - [Hoshino](https://link.springer.com/article/10.1186/2229-0443-3-16)
;;   showed that distractor type and usable context alter multiple-choice item
;;   difficulty.
;;
;; The defensible claim is therefore modest: **frequency is a plausible proxy,
;; not a calibrated item-difficulty scale**.
;;
;; ## A real but deliberately non-lexical fixture

^:kindly/hide-code
(def fixture-text
  (slurp (io/resource
          "language_learning/vocabulary_estimation/pair_frequency_fixture_v1.tsv")))

^:kindly/hide-code
(def fixture-pairs
  (-> fixture-text v2/parse-fixture v2/validate-fixture))

^:kindly/hide-code
(def transformed-fixture (v2/frequency-transform fixture-pairs))

^:kindly/hide-code
(def fixture-xs (mapv :x (:pairs transformed-fixture)))

;; The fixture contains source ranks 1–8,000 and their real
;; `pair_frequency_sn_sum` values. It is **not** a CEFR pool, a Lexibench pool,
;; or a lexically curated inventory. It exists only to preserve the actual
;; shape of the frequency predictor during simulation.

^:kindly/hide-code
(kind/hiccup
 [:div.pf-grid
  [:section.pf-card
   [:h3 "Versioned fixture"]
   [:p [:strong "ID: "] v2/fixture-id]
   [:p.pf-code [:strong "SHA-256: "] v2/fixture-sha256]]
  [:section.pf-card
   [:h3 "Transform"]
   [:p "mean(log₁₀ frequency): "
    (format "%.6f" (:log10-mean transformed-fixture))]
   [:p "population SD: "
    (format "%.6f" (:log10-population-sd transformed-fixture))]]
  [:section.pf-card
   [:h3 "Bounds after z-scoring"]
   [:p (format "%.3f to %.3f" (apply min fixture-xs) (apply max fixture-xs))]
   [:p "8 × 1,000 rank-stratified pairs"]]])

;; For pair $i$:
;;
;; $$x_i = z\!\left(\log_{10}(\text{pair-frequency}_{i})\right)$$

^:kindly/hide-code
(math/explanation
 "math-standardized-frequency"
 "The pair-frequency predictor"
 [["i" "The index identifying one lemma–surface-form pair."]
  ["pair-frequency_i" "The corpus frequency recorded for pair i; it distinguishes lemma–form pairs, not word senses."]
  ["log₁₀" "Base-10 logarithm. It compresses the large, skewed differences between raw frequencies."]
  ["z(a)" "Z-standardisation: subtract the fixture mean of a and divide by its population standard deviation."]
  ["x_i" "The resulting standardised log-frequency predictor for pair i, measured in standard-deviation units."]]
 "Higher x_i means higher corpus pair frequency within this fixed, versioned pool. It is still a proxy, not calibrated item difficulty.")
;;
;; $$p_i = \operatorname{logistic}\!\left(
;;     2\log(9)\frac{x_i-t}{w}\right).$$

^:kindly/hide-code
(math/explanation
 "math-logistic-curve"
 "The continuous knowing-probability curve"
 [["p_i" "The modelled probability that the learner knows pair i under the v2 binary response model."]
  ["logistic(a)" "The function 1 / (1 + exp(−a)), which maps any real number to a probability between 0 and 1."]
  ["x_i" "Pair i’s standardised log-frequency predictor."]
  ["t" "The learner-specific threshold. When x_i = t, the model gives p_i = 0.5."]
  ["w" "A positive width controlling how gradual the transition is; it spans the predictor distance from p = 0.1 to p = 0.9."]
  ["x_i − t" "Pair i’s predictor position relative to the learner threshold."]
  ["log(9)" "The natural logarithm of 9. Together with the factor 2, it makes t − w/2 map to 0.1 and t + w/2 map to 0.9."]]
 "Larger w produces a gentler curve. Higher x_i relative to t produces a larger modelled knowing probability.")
;;
;; The threshold $t$ is the predictor value where $p_i=0.5$. The positive
;; width $w$ is the predictor distance from 10% to 90%. Larger widths mean a
;; gentler transition.

(def curve-check
  {:at-threshold (v2/knowledge-probability 0.7 0.7 2.0)
   :one-half-width-below (v2/knowledge-probability -0.3 0.7 2.0)
   :one-half-width-above (v2/knowledge-probability 1.7 0.7 2.0)})

;; The executable check returns probabilities `0.5`, `0.1`, and `0.9`.
;;
;; ## Priors and deterministic grid
;;
;; The versioned defaults are:
;;
;; $$t \sim \operatorname{Normal}(0, 2.5)$$

^:kindly/hide-code
(math/explanation
 "math-threshold-prior"
 "The prior for the learner threshold"
 [["t" "The standardised-frequency threshold where the learner’s modelled knowing probability is 0.5."]
  ["∼" "“Is distributed as.” The threshold is uncertain before responses are observed."]
  ["Normal(0, 2.5)" "A Gaussian prior with mean 0 and standard deviation 2.5 on the standardised-frequency scale."]
  ["0" "The centre of the prior, equal to the pool’s mean log frequency after z-standardisation."]
  ["2.5" "The prior standard deviation; its large size allows thresholds well beyond the observed predictor range."]]
 "This is a provisional modelling choice, not a threshold distribution learned from Lexibench users.")
;;
;; $$\log(w) \sim \operatorname{Normal}(\log(2), 0.6).$$

^:kindly/hide-code
(math/explanation
 "math-width-prior"
 "The prior for the curve width"
 [["w" "The positive predictor distance over which knowing probability rises from 0.1 to 0.9."]
  ["log(w)" "The natural logarithm of w. Modelling it makes every implied value of w positive."]
  ["∼" "“Is distributed as.” It expresses prior uncertainty about the width."]
  ["Normal(log(2), 0.6)" "A Gaussian prior on log width, with mean log(2) and standard deviation 0.6."]
  ["log(2)" "The prior centre on the log scale, corresponding to median width w = 2 on the original scale."]
  ["0.6" "The prior standard deviation in log-width units."]]
 "On the original scale this is a log-normal prior: widths are asymmetric around 2 and can never be zero or negative.")
;;
;; These are provisional. The posterior is evaluated on 161 threshold points
;; from `min(x)-2` to `max(x)+2`, crossed with 81 log-spaced widths from `0.25`
;; to `8`. Log weights are normalized with log-sum-exp.

^:kindly/hide-code
(def wider-prior
  (assoc v2/default-prior :threshold-sd 5.0 :log-width-sd 1.2))

^:kindly/hide-code
(def prior-summaries
  {:default (v2/prior-predictive-expected-summary fixture-xs v2/default-prior)
   :wider (v2/prior-predictive-expected-summary fixture-xs wider-prior)})

^:kindly/hide-code
(kind/hiccup
 [:div.pf-table-wrap
  [:table.pf-table
   [:caption.pf-sr-only "Prior-predictive expected pair totals"]
   [:thead [:tr [:th {:scope "col"} "Prior"]
            [:th {:scope "col"} "Mean"]
            [:th {:scope "col"} "2.5%"]
            [:th {:scope "col"} "Median"]
            [:th {:scope "col"} "97.5%"]]]
   [:tbody
    (for [[label summary] prior-summaries]
      [:tr {:key (name label)}
       [:th {:scope "row"} (if (= label :default) "Default" "2× wider SDs")]
       [:td (format "%,.0f" (:mean summary))]
       [:td (format "%,.0f" (:lower summary))]
       [:td (format "%,.0f" (:median summary))]
       [:td (format "%,.0f" (:upper summary))]])]]])

;; The prior is broad on the count scale; doubling both prior standard
;; deviations makes it more extreme. Neither prior was learned from Lexibench
;; responses.
;;
;; ### Explore the curve

^:kindly/hide-code
(kind/hiccup
 [:div.pf-lab
  [:div#pair-frequency-curve-explorer
   [:p "Loading the curve explorer…"]]
  [:noscript "This explorer needs JavaScript."]])

;; ## What is observed and what is predicted
;;
;; Raw response events remain `:correct`, `:wrong`, or `:dont-know`. V2 still
;; maps correct to `1` and both other values to `0` for inference. Tested pair
;; outcomes are fixed; posterior-predictive simulation draws outcomes only for
;; untested pairs. The point estimate is the posterior-predictive mean and the
;; interval is a deterministic seeded 95% equal-tail interval.
;;
;; The fitted model assumes that unmodelled pair and complete-item effects have
;; conditional mean zero. Its credible interval does **not** include uncertainty
;; from violating that assumption. Context, distractors, guessing, slips, and
;; sense distinctions remain outside this model.

^:kindly/hide-code
(def grid-check
  (edn/read-string
   (slurp (io/resource
           "language_learning/vocabulary_estimation/pair_frequency_logistic_v2_grid_check.edn"))))

^:kindly/hide-code
(kind/hiccup
 [:div.pf-grid
  [:section.pf-card
   [:h3 "Default grid"]
   [:p (format "Mean %,.1f" (get-in grid-check [:coarse :mean]))]
   [:p (format "95%% ETI %,d–%,d"
               (get-in grid-check [:coarse :lower])
               (get-in grid-check [:coarse :upper]))]]
  [:section.pf-card
   [:h3 "Doubled grid"]
   [:p (format "Mean %,.1f" (get-in grid-check [:doubled :mean]))]
   [:p (format "95%% ETI %,d–%,d"
               (get-in grid-check [:doubled :lower])
               (get-in grid-check [:doubled :upper]))]]
  [:section.pf-card
   [:h3 "Difference"]
   [:p (format "Mean %.4f pair"
               (get-in grid-check [:convergence :mean-difference]))]
   [:p (format "Endpoints %,d and %,d pairs"
               (get-in grid-check [:convergence :lower-difference])
               (get-in grid-check [:convergence :upper-difference]))]
   [:p [:strong "Passes <10 / <25 tolerances"]]]])

;; ## Selection remains v1
;;
;; Every attempt still creates eight response-independent queues from equal-count
;; rank strata. Each complete round selects one unseen pair per stratum and
;; records its inclusion probability. Responses update inference only; they do
;; not alter item order. Adaptive selection remains a non-goal.
;;
;; ### Try one seeded v1/v2 quiz

^:kindly/hide-code
(kind/hiccup
 [:div.pf-lab
  [:div#pair-frequency-simulation-lab
   [:p "Loading the seeded simulation lab…"]]
  [:noscript "This lab needs JavaScript."]
  [:script#pair-frequency-xs {:type "application/json"}
   (str "[" (str/join "," fixture-xs) "]")]
  [:script {:type "application/x-scittle"
            :src "pair_frequency_logistic_v2.cljc"}]
  [:script {:type "application/x-scittle"
            :src "pair_frequency_logistic_v2_interactive.cljs"}]])

;; The browser uses a bounded 41×21 grid and 300 predictive draws so the
;; mechanism stays interactive. The authoritative checks and large simulation
;; run in CLJ.
;;
;; ## The precommitted promotion gate

^:kindly/hide-code
(def tuning-result
  (edn/read-string
   (slurp (io/resource
           "language_learning/vocabulary_estimation/pair_frequency_logistic_v2_tuning.edn"))))

^:kindly/hide-code
(def held-out-result
  (edn/read-string
   (slurp (io/resource
           "language_learning/vocabulary_estimation/pair_frequency_logistic_v2_held_out.edn"))))

^:kindly/hide-code
(def held-out-candidate (first (:rules held-out-result)))

;; Tuning covered 45 supported cells: expected totals near 10%, 30%, 50%, 70%,
;; and 90%; widths `0.75`, `1.5`, and `3.0`; and zero-mean pair residual SDs
;; `0`, `0.5`, and `1.0`. Every cell used 500 replicates. The search crossed 100
;; complete-round rules.
;;
;; No rule satisfied all four requirements. Because there was no eligible rule,
;; the 2,000-replicate-per-cell run below is a **held-out diagnostic**, not a
;; promotion gate. Following the declared priority—coverage, then MAE, then
;; length—it examines the least-bad coverage rule: minimum 48, 7.5% target
;; half-width, cap 64.

^:kindly/hide-code
(kind/hiccup
 [:div.pf-table-wrap
  [:table.pf-table
   [:caption.pf-sr-only "Held-out supported-scenario comparison"]
   [:thead
    [:tr [:th {:scope "col"} "Measure"]
     [:th {:scope "col"} "v1"]
     [:th {:scope "col"} "v2 diagnostic"]
     [:th {:scope "col"} "Requirement"]]]
   [:tbody
    (let [v1 (get-in held-out-result [:v1 :aggregate])
          v2 (:aggregate held-out-candidate)]
      (list
       [:tr [:th {:scope "row"} "Aggregate coverage"]
        [:td (format "%.2f%%" (* 100 (:coverage v1)))]
        [:td (format "%.2f%%" (* 100 (:coverage v2)))]
        [:td "≥94.5%"]]
       [:tr [:th {:scope "row"} "Worst-cell coverage"]
        [:td "—"]
        [:td (format "%.2f%%" (* 100 (:minimum-cell-coverage held-out-candidate)))]
        [:td "≥94%"]]
       [:tr [:th {:scope "row"} "MAE"]
        [:td (format "%,.1f" (:mae v1))]
        [:td (format "%,.1f" (:mae v2))]
        [:td "v2 aggregate lower"]]
       [:tr [:th {:scope "row"} "Worst-cell v2/v1 MAE"]
        [:td "—"]
        [:td (format "%.1f%%" (* 100 (:maximum-cell-mae-ratio held-out-candidate)))]
        [:td "≤105%"]]
       [:tr [:th {:scope "row"} "Mean interval width"]
        [:td (format "%,.0f" (:mean-interval-width v1))]
        [:td (format "%,.0f" (double (:mean-interval-width v2)))]
        [:td "reported"]]
       [:tr [:th {:scope "row"} "Mean response log score"]
        [:td (format "%.3f" (:mean-log-score v1))]
        [:td (format "%.3f" (:mean-log-score v2))]
        [:td "higher is better"]]
       [:tr [:th {:scope "row"} "Median items"]
        [:td (:median-items v1)]
        [:td (:median-items v2)]
        [:td "v2 ≤ v1"]]))]]])

^:kindly/hide-code
(kind/hiccup
 [:div.pf-callout.provisional
  [:strong "Large-run numerical shortcut"]
  "The CLJ gate runner integrates parameter uncertainty on the full 161×81 grid, then uses 512 deterministic systematic grid samples and a moment-matched normal approximation to each untested Poisson-binomial total. The exact finite-pool scorer above performs pair-level Bernoulli simulation. The shortcut is recorded in the result artifact and is another reason not to promote a near-miss."])

;; Aggregate v2 coverage and MAE improved substantially, but the gate protects
;; against hiding weak cells in an average. Worst-cell coverage was `91.95%`,
;; worst-cell MAE was `21.1%` worse than v1, and median length was `64` rather
;; than `40`. The decision is therefore unambiguous: **retain v1**.
;;
;; ## Untuned stress diagnostics
;;
;; I froze the same diagnostic rule, then ran 2,000 replicates per cell without
;; retuning. Five ability targets were crossed with a non-logistic mixture,
;; positive and negative frequency-related residuals, separate false-positive
;; and false-negative rates at 2%, 5%, and 10%, and measurement error increasing
;; with rank. The stopping reason in every run remained either
;; `:precision-target` or `:soft-maximum` under the frozen 48 / 7.5% / 64 rule.

^:kindly/hide-code
(def stress-result
  (edn/read-string
   (slurp (io/resource
           "language_learning/vocabulary_estimation/pair_frequency_logistic_v2_stress.edn"))))

^:kindly/hide-code
(def stress-labels
  {:non-logistic-mixture "Non-logistic mixture"
   :frequency-related-residual "Frequency-related residual"
   :false-positive "False positives (2/5/10%)"
   :false-negative "False negatives (2/5/10%)"
   :rank-increasing-measurement-error "Error increasing with rank"})

^:kindly/hide-code
(kind/hiccup
 [:div.pf-table-wrap
  [:table.pf-table
   [:caption.pf-sr-only "Untuned v2 stress diagnostics"]
   [:thead
    [:tr [:th {:scope "col"} "Stress"]
     [:th {:scope "col"} "Cells"]
     [:th {:scope "col"} "Coverage"]
     [:th {:scope "col"} "Worst cell"]
     [:th {:scope "col"} "Bias"]
     [:th {:scope "col"} "MAE"]
     [:th {:scope "col"} "Interval width"]
     [:th {:scope "col"} "Log score"]
     [:th {:scope "col"} "Median items"]]]
   [:tbody
    (for [[scenario {:keys [cells v2 minimum-cell-coverage]}]
          (:by-scenario stress-result)]
      [:tr {:key (name scenario)}
       [:th {:scope "row"} (get stress-labels scenario (name scenario))]
       [:td cells]
       [:td (format "%.1f%%" (* 100 (:coverage v2)))]
       [:td (format "%.1f%%" (* 100 minimum-cell-coverage))]
       [:td (format "%,.1f" (:bias v2))]
       [:td (format "%,.1f" (:mae v2))]
       [:td (format "%,.0f" (double (:mean-interval-width v2)))]
       [:td (format "%.3f" (:mean-log-score v2))]
       [:td (:median-items v2)]])]]])

;; The frequency-related residual and mixture groups retained good aggregate
;; coverage, but their worst-cell MAE still exceeded v1 by more than 5%.
;; Measurement error produced severe worst-cell undercoverage. Stress results
;; therefore reinforce the non-promotion decision; they were not used to revise
;; the rule.
;;
;; ## What was learned
;;
;; 1. Replacing eight independent rates with one continuous curve greatly
;;    improves aggregate MAE and response log score under related simulations.
;; 2. Mean-zero pair residuals are not harmless at every ability/width cell.
;;    Aggregate coverage concealed poor cells.
;; 3. A useful difficulty proxy is not automatically a safe scorer.
;; 4. The next change must be a new version, not a quiet retuning of v2 after
;;    seeing held-out results.
;;
;; `continuous-pair-frequency-logistic-v2` remains an experimental, replayable
;; checkpoint. The current target stays `stratified-beta-binomial-v1`.

^:kindly/hide-code
(do
  (assert (= 8000 (count fixture-pairs)))
  (assert (= [0.5 0.1 0.9]
             (mapv #(/ (double (Math/round (* 10.0 %))) 10.0)
                   [(:at-threshold curve-check)
                    (:one-half-width-below curve-check)
                    (:one-half-width-above curve-check)])))
  (assert (get-in grid-check [:convergence :passes?]))
  (assert (empty? (filter :passes? (:rules tuning-result))))
  (assert (not (:passes? held-out-candidate)))
  (assert (= 60 (:stress-cell-count stress-result)))
  :verified-negative-result)
