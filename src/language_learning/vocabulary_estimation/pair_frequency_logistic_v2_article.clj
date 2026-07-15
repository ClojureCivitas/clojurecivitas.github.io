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
  (str
   ":root{--pf-accent:#1464b5;--pf-accent-soft:#e6f1fb;--pf-fail:#a72f24;--pf-fail-soft:#fdeae7;--pf-warn:#8a5000;--pf-warn-soft:#fff0d8;--pf-success:#0f695f;--pf-success-soft:#e2f4f0;--pf-muted:#4f5b66}"
   ".quarto-dark{--pf-accent:#73b7ff;--pf-accent-soft:#173653;--pf-fail:#ff998f;--pf-fail-soft:#4a211e;--pf-warn:#ffc46f;--pf-warn-soft:#493216;--pf-success:#64d8c7;--pf-success-soft:#163d38;--pf-muted:#b9c7d2}"
   ".pf-callout{border:1px solid color-mix(in srgb,var(--pf-accent) 45%,var(--bs-border-color,#dee2e6));border-left:4px solid var(--pf-accent);background:color-mix(in srgb,var(--bs-body-bg,#fff) 90%,var(--pf-accent) 10%);color:var(--bs-body-color,#212529);padding:1rem 1.15rem;margin:1.35rem 0;border-radius:.35rem}"
   ".pf-callout.fail{border-color:color-mix(in srgb,var(--pf-fail) 60%,var(--bs-border-color,#dee2e6));border-left-color:var(--pf-fail);background:color-mix(in srgb,var(--bs-body-bg,#fff) 88%,var(--pf-fail) 12%)}"
   ".pf-callout.provisional{border-color:color-mix(in srgb,var(--pf-warn) 60%,var(--bs-border-color,#dee2e6));border-left-color:var(--pf-warn);background:color-mix(in srgb,var(--bs-body-bg,#fff) 90%,var(--pf-warn) 10%)}"
   ".pf-callout strong{display:block;margin-bottom:.3rem}.pf-callout p:last-child{margin-bottom:0}"
   ".pf-grid,.pf-definition-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,15rem),1fr));gap:1rem;margin:1.25rem 0}"
   ".pf-card,.pf-definition{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.55rem;padding:1rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529);overflow-wrap:anywhere}"
   ".pf-card h3{font-size:1rem;margin:0 0 .4rem}.pf-card p{margin:.25rem 0}.pf-definition dt{font-weight:800;color:var(--pf-accent)}.pf-definition dd{margin:.25rem 0 0}"
   ".pf-table-wrap{max-width:100%;overflow-x:auto;margin:1.25rem 0}.pf-table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}"
   ".pf-table th,.pf-table td{padding:.55rem .7rem;border-bottom:1px solid var(--bs-border-color,#dee2e6);text-align:right;white-space:nowrap}.pf-table th:first-child,.pf-table td:first-child{text-align:left}.pf-table thead th{border-bottom:2px solid var(--bs-border-color,#adb5bd)}"
   ".pf-explain-table th,.pf-explain-table td{white-space:normal;text-align:left;vertical-align:top;min-width:9rem}"
   ".pf-sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}.pf-code{overflow-wrap:anywhere}.pf-lab{border:1px solid var(--bs-border-color,#ced4da);border-radius:.65rem;padding:clamp(.8rem,3vw,1.3rem);min-width:0}"
   ".pf-pipeline{display:grid;grid-template-columns:repeat(9,minmax(0,auto));align-items:stretch;gap:.4rem;margin:1.25rem 0}.pf-stage{display:grid;align-content:center;min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.7rem;background:var(--bs-body-bg,#fff);text-align:center;overflow-wrap:anywhere}.pf-stage strong{display:block;color:var(--pf-accent)}.pf-stage small{color:var(--pf-muted)}.pf-arrow{display:grid;place-items:center;color:var(--pf-accent);font-size:1.35rem;font-weight:800}"
   ".pf-diagram{min-width:0;margin:1.25rem 0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.55rem;padding:clamp(.75rem,2vw,1rem);background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.pf-diagram-steps{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,9rem),1fr));gap:.65rem;margin:0;padding:0;list-style:none;counter-reset:pf-step}.pf-diagram-steps li{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.65rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 94%,var(--pf-accent) 6%);overflow-wrap:anywhere;counter-increment:pf-step}.pf-diagram-steps li::before{content:counter(pf-step);display:grid;place-items:center;width:1.55rem;height:1.55rem;margin-bottom:.4rem;border-radius:50%;background:var(--pf-accent);color:var(--bs-body-bg,#fff);font-weight:800}.quarto-dark .pf-diagram-steps li::before{color:#10212b}.pf-diagram-steps strong,.pf-lane strong{display:block;color:var(--pf-accent)}"
   ".pf-phase-diagram{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);gap:.65rem;align-items:center}.pf-phase-node,.pf-phase-branch{min-width:0}.pf-phase-node{border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.75rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 94%,var(--pf-accent) 6%);overflow-wrap:anywhere}.pf-phase-node strong{display:block;color:var(--pf-accent)}.pf-phase-branch{display:grid;gap:.55rem}.pf-workflow-lanes{display:grid;gap:.8rem}.pf-lane{min-width:0;border-left:4px solid var(--pf-accent);padding:.2rem 0 .2rem .8rem}.pf-lane h3{font-size:1rem;margin:.1rem 0 .55rem}"
   ".pf-odds-strip{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:.65rem;margin:1rem 0}.pf-odds-card{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.7rem;background:var(--bs-body-bg,#fff);text-align:center}.pf-odds-card strong,.pf-odds-card span{display:block}.pf-odds-card strong{color:var(--pf-accent)}.pf-odds-card span{font-variant-numeric:tabular-nums}"
   ".pf-status-pass{color:var(--pf-success);font-weight:800}.pf-status-fail{color:var(--pf-fail);font-weight:800}.pf-caption,.pf-note{font-size:.9rem;color:var(--pf-muted)}"
   ".series-toc{min-width:0;border:1px solid var(--bs-border-color,#ced4da);border-radius:.6rem;padding:clamp(.85rem,3vw,1.2rem);margin:0 0 1.4rem;background:var(--bs-body-bg,#fff)}.series-toc h2{font-size:1.2rem;margin:0 0 .55rem}.series-toc p{margin:0 0 .7rem}.series-toc ol{margin:0;padding-left:1.45rem}.series-toc li{padding:.18rem .45rem}.series-status{display:inline-block;margin-left:.35rem;font-size:.7rem;font-weight:700;letter-spacing:.04em;text-transform:uppercase;color:var(--pf-muted)}.series-current{margin:.35rem 0 .35rem -.7rem;border-left:4px solid var(--pf-accent);border-radius:.4rem;padding:.6rem .75rem!important;background:color-mix(in srgb,var(--bs-body-bg,#fff) 84%,var(--pf-accent) 16%);box-shadow:inset 0 0 0 1px color-mix(in srgb,var(--pf-accent) 35%,transparent);font-weight:700}.series-current>a{color:var(--pf-accent)}.series-current .series-status{border-radius:999px;padding:.18rem .48rem;background:var(--pf-accent);color:#fff}.quarto-dark .series-current .series-status{color:#10212b}"
   "@media(max-width:767px){.pf-pipeline,.pf-phase-diagram{grid-template-columns:minmax(0,1fr)}.pf-arrow{min-height:1rem}.pf-odds-strip{grid-template-columns:minmax(0,1fr)}}@media(max-width:575px){.pf-table th,.pf-table td{padding:.45rem}.pf-card{padding:.8rem}}")])

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
   [:li.series-current [:a {:href "pair_frequency_logistic_v2_article.html"
                            :aria-current "page"}
                        "Does Pair Frequency Predict Learner Responses?"]
    [:span.series-status "you are here"]]
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

;; ## The result first: useful, but not promoted
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

;; Three decision terms matter from the start:

^:kindly/hide-code
(kind/hiccup
 [:dl.pf-definition-grid
  [:div.pf-definition
   [:dt "Gate"]
   [:dd "A set of acceptance checks declared before seeing the results. Every check must pass."]]
  [:div.pf-definition
   [:dt "Promotion"]
   [:dd "Replacing the current target scorer, v1, with the candidate scorer, v2."]]
  [:div.pf-definition
   [:dt "Non-promotion"]
   [:dd "Keeping v1 because v2 failed the gate—without weakening the checks after seeing the result."]]])

;; The outcome is deliberately asymmetric: a candidate may teach us something
;; and still fail to earn operational trust. Here the historical gate used
;; logical **AND**: passing four checks would still have meant non-promotion.

^:kindly/hide-code
(kind/hiccup
 [:ol.article-chapter-map
  [:li [:strong "Construct"] [:br] "Turn corpus counts into one predictor per pair."]
  [:li [:strong "Curve"] [:br] "Connect frequency to probability through odds and log odds."]
  [:li [:strong "Infer"] [:br] "Use priors and a deterministic grid while separating latent and observed outcomes."]
  [:li [:strong "Select"] [:br] "Keep the balanced response-independent schedule unchanged."]
  [:li [:strong "Simulate"] [:br] "Separate replicates, phases, metrics, and seeds."]
  [:li [:strong "Gate"] [:br] "Apply all five precommitted checks."]
  [:li [:strong "Decide"] [:br] "Diagnose the failure and retain v1."]])

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
;; ## Real frequency values, not a real vocabulary pool

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

;; Here, **fixture** means a frozen input dataset that makes every simulation
;; reproducible. "Real" describes only its source ranks 1–8,000, pair IDs, and
;; observed `pair_frequency_sn_sum` values. The simulation does not use the
;; pairs' words, meanings, senses, contexts, distractors, or answer choices.
;;
;; Nor were these 8,000 pairs curated as a CEFR or Lexibench vocabulary pool:
;; they were taken by frequency rank, without selecting for learner relevance,
;; lexical coverage, or item quality. Their highly skewed scores are retained
;; instead of replaced by invented, evenly spaced frequencies, so the model
;; sees a realistic numerical predictor range, clustering, and long tail. The
;; resulting simulations test behaviour over that frequency distribution—not
;; performance on real Lexibench test items.

;; ### From corpus word-form counts to one lemma–form-pair score
;;
;; This section explains the raw frequency quantity used by the model below:
;; where its counts come from, why an ambiguous surface form must be divided
;; among lemmas, and what `pair_frequency_sn_sum` means after that division.
;;
;; 1. **Combine two counts for the surface form.** SUBTLEX-PL provides an
;;    upstream field called `freq.sn.sum`: the surface form's count in SUBTLEX-PL's
;;    subtitle corpus plus its count in the balanced-subcorpus National Corpus
;;    of Polish (BS–NCP). For a hypothetical surface form seen 700 times in the
;;    first corpus and 300 times in the second, this combined surface-form count
;;    is 1,000. At this point it belongs to the surface form, not to a particular
;;    lemma.
;; 2. **Split an ambiguous surface form using tagged uses.** SUBTLEX also records
;;    how often that surface form was tagged with each lemma and part of speech.
;;    Suppose 90 of every 100 tagged uses belong to lemma A and 10 belong to
;;    lemma B. The importer uses those shares to divide the combined count:
;;    900 for the `(surface form, lemma A)` pair and 100 for the `(surface form,
;;    lemma B)` pair.
;; 3. **Store one score per pair.** Those allocated values are
;;    `pair_frequency_sn_sum`. An unambiguous surface form receives its entire
;;    combined count; when several part-of-speech links point to the same lemma,
;;    their shares are added before storing the pair score.
;;
;; Giving both ambiguous pairs the full 1,000 would duplicate the same corpus
;; evidence and falsely make both look common. The split is performed on linear
;; counts because 900 + 100 still equals the original 1,000; logarithms and Zipf
;; values do not have that additive property. Only after the pair score is
;; constructed do we take its logarithm and z-score it to obtain $x_i$ below.
;;
;; This is an allocated frequency proxy, not a direct count observed separately
;; for each pair and not calibrated learner difficulty. It distinguishes
;; lemmas, not senses. It also assumes that SUBTLEX's lemma–part-of-speech shares
;; are a reasonable way to divide the combined total, even though that total
;; includes BS–NCP counts.

^:kindly/hide-code
(kind/hiccup
 [:figure
  [:div.pf-pipeline
   {:role "img"
    :aria-label "Frequency construction pipeline. Add subtitle and balanced-corpus surface-form counts. Split an ambiguous form among lemmas using tagged-use shares. Store one allocated pair score. Take its base-ten logarithm. Standardize it using the fixture mean and population standard deviation."}
   [:div.pf-stage [:strong "Corpus counts"] [:span "700 + 300"] [:small "surface form = 1,000"]]
   [:div.pf-arrow {:aria-hidden "true"} "→"]
   [:div.pf-stage [:strong "Ambiguous-form split"] [:span "90% / 10%"] [:small "tagged lemma shares"]]
   [:div.pf-arrow {:aria-hidden "true"} "→"]
   [:div.pf-stage [:strong "Pair score"] [:span "900 and 100"] [:small "pair_frequency_sn_sum"]]
   [:div.pf-arrow {:aria-hidden "true"} "→"]
   [:div.pf-stage [:strong "Log transform"] [:span "log₁₀(fᵢ)"] [:small "compress tenfold gaps"]]
   [:div.pf-arrow {:aria-hidden "true"} "→"]
   [:div.pf-stage [:strong "Z-score"] [:span "xᵢ = (log fᵢ − μ) / σ"] [:small "fixture-relative SD units"]]]
  [:figcaption.pf-caption
   "The first three steps conserve the original linear count. Logging and standardising happen only after the evidence has been allocated to pairs."]])

;; ### Why take the logarithm and then z-score?
;;
;; Raw pair-frequency scores span orders of magnitude and are strongly skewed
;; toward a few very frequent pairs. The base-10 logarithm makes multiplicative
;; differences easier to compare: 10 to 100 and 100 to 1,000 are both tenfold
;; increases, so each becomes a step of 1 on the log scale. This stops the
;; largest raw counts from dominating distance merely because their numbers are
;; large.
;;
;; Z-scoring then describes each log frequency relative to this fixed 8,000-pair
;; fixture. It subtracts the fixture-wide mean log frequency and divides by its
;; population standard deviation. The result uses standard-deviation units:
;; `0` means average log frequency in this fixture, `+1` means one standard
;; deviation above it, and `-1` means one below it. This gives the later
;; threshold $t$ and width $w$ an interpretable common scale instead of raw
;; corpus-count units.

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
   [:p "8,000 lemma–form-pair scores"]]])

;; For pair $i$, let $f_i$ be its `pair_frequency_sn_sum` value. The prose above
;; and the equation below describe the same two-step transformation:
;;
;; $$x_i =
;; \frac{\log_{10}(f_i)-\mu_{\log f}}{\sigma_{\log f}}.$$
;;
;; Here $\mu_{\log f}$ and $\sigma_{\log f}$ are the mean and population SD
;; shown in the Transform card. Higher $x_i$ means a more frequent pair within
;; this fixture. The transformation preserves the pair ordering; it does not
;; turn frequency into calibrated item difficulty or make scores independent of
;; the versioned pool used to calculate the mean and SD.

^:kindly/hide-code
(math/code-detail
 "code-frequency-transform"
 "Constructing the standardised pair-frequency predictor"
 [:div
  [:p "The transform logs every positive pair score, calculates the fixture-wide mean and population SD, then attaches x to each immutable pair record."]
  [:pre [:code "(defn frequency-transform [pairs]\n  (let [logs (mapv #(Math/log10 (:pair-frequency-sn-sum %)) pairs)\n        location (mean logs)\n        scale (population-sd logs)]\n    {:log10-mean location\n     :log10-population-sd scale\n     :pairs (mapv (fn [pair log-frequency]\n                    (assoc pair :x (/ (- log-frequency location) scale)))\n                  pairs logs)}))"]]
  [:p "The fixture validator separately rejects non-positive frequencies, duplicate pair IDs, and rank drift before this function runs."]
  [:p.article-code-source
   [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/pair_frequency_logistic_v2.cljc"}
    "View the transform and fixture validation"]]])
;;
;; ## From probability to a logistic curve
;;
;; A **probability** $p$ describes expected successes out of all comparable
;; cases. **Odds** compare successes with failures: $p/(1-p)$. A probability of
;; 0.9 means odds of 9 to 1; 0.1 means 1 to 9. **Log odds** take the natural
;; logarithm of the odds. They are negative below 50%, zero at 50%, and positive
;; above 50%. A logistic curve turns any real-valued log odds back into a
;; probability between 0 and 1.

^:kindly/hide-code
(kind/hiccup
 [:figure
  [:div.pf-odds-strip
   {:role "img"
    :aria-label "Three points on the logistic curve. Half a transition width below the threshold gives probability 0.1, odds 1 to 9, and log odds minus 2.20. At the threshold gives probability 0.5, odds 1 to 1, and log odds zero. Half a width above gives probability 0.9, odds 9 to 1, and log odds 2.20."}
   [:div.pf-odds-card [:strong "x = t − w/2"] [:span "p = 0.10"] [:span "odds 1:9"] [:span "log odds −2.20"]]
   [:div.pf-odds-card [:strong "x = t"] [:span "p = 0.50"] [:span "odds 1:1"] [:span "log odds 0"]]
   [:div.pf-odds-card [:strong "x = t + w/2"] [:span "p = 0.90"] [:span "odds 9:1"] [:span "log odds +2.20"]]]
  [:figcaption.pf-caption
   "The curve explorer below uses these same three anchor points. Move t to shift them together; move w to spread or compress them."]])
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
;; The threshold $t$ is a continuous position on the standardised-frequency
;; scale, not necessarily one observed pair or rank. If a pair has $x_i=t$, the
;; model assigns it $p_i=0.5$; otherwise $t$ lies between neighbouring pairs.
;; Because higher $x_i$ means greater frequency, pairs above the threshold get
;; probabilities above 50% and pairs below it get probabilities below 50% for a
;; fixed $t$ and $w$. The posterior keeps $t$ uncertain rather than identifying
;; one definitive "50% pair." The positive width $w$ is the predictor distance
;; from 10% to 90%; larger widths mean a gentler transition.

;; `v2/knowledge-probability` takes arguments in the order `(x, t, w)` and
;; returns the modelled probability, between `0` and `1`, that the learner knows
;; a pair at predictor position $x$. It does not draw a simulated known/unknown
;; response. The executable check below holds $t=0.7$ and $w=2.0$ fixed, then
;; evaluates the three annotated positions.

^:kindly/hide-code
(def curve-check
  {:at-threshold (v2/knowledge-probability 0.7 0.7 2.0)
   :one-half-width-below (v2/knowledge-probability -0.3 0.7 2.0)
   :one-half-width-above (v2/knowledge-probability 1.7 0.7 2.0)})

;; The returned map therefore contains probabilities `0.5`, `0.1`, and `0.9`.
;; This checks the curve's parameterisation; it is not fitted learner data.
;;
^:kindly/hide-code
(math/code-detail
 "code-logistic-probability"
 "Turning predictor position into knowing probability"
 [:div
  [:p "The stable logistic implementation avoids overflow for very large positive or negative log odds. Width must be positive."]
  [:pre [:code "(defn logistic [z]\n  (if (neg? z)\n    (let [e (Math/exp z)] (/ e (+ 1.0 e)))\n    (/ 1.0 (+ 1.0 (Math/exp (- z))))))\n\n(defn knowledge-probability [x threshold width]\n  (when-not (pos? width)\n    (throw (ex-info \"Transition width must be positive\" {:width width})))\n  (logistic (/ (* transition-logit-span (- x threshold)) width)))"]]
  [:p "The factor 2 log(9) makes the two half-width points exactly 0.1 and 0.9."]
  [:p.article-code-source
   [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/pair_frequency_logistic_v2.cljc"}
    "View the probability functions and parity tests"]]])
;;
;; ### Explore the curve
;;
;; Read the horizontal axis as pair frequency relative to this fixture and the
;; vertical axis as modelled knowing probability. Before moving a slider,
;; predict what will happen: threshold should slide the 50% point left or right;
;; width should change the distance between the 10% and 90% points.

^:kindly/hide-code
(kind/hiccup
 [:div.pf-lab
  [:div#pair-frequency-curve-explorer
   [:p "Loading the curve explorer…"]]
  [:noscript "This explorer needs JavaScript."]])
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
;; to `8`. A **prior** states relative plausibility before this learner's
;; responses. A **deterministic parameter grid** lists the same 13,041
;; $(t,w)$ candidates on every run. It replaces a continuous search with a
;; fixed approximation that can be replayed exactly.
;;
;; For a binary outcome $y_i$—1 for correct, 0 otherwise—the likelihood at one
;; grid point is:
;;
;; $$L(t,w)=\prod_i p_i^{y_i}(1-p_i)^{1-y_i}.$$

^:kindly/hide-code
(math/explanation
 "math-grid-likelihood"
 "The likelihood at one parameter-grid point"
 [["L(t,w)" "How well one threshold-and-width candidate predicts all observed binary outcomes."]
  ["∏ᵢ" "Multiply one response contribution for every tested pair i."]
  ["pᵢ" "The logistic knowing probability for tested pair i at this grid point."]
  ["yᵢ" "The observed binary outcome: 1 for correct and 0 for wrong or don’t-know in v2."]
  ["pᵢʸⁱ(1−pᵢ)¹⁻ʸⁱ" "Selects pᵢ after a correct response and 1−pᵢ otherwise."]]
 "In plain English: candidates that assign higher probability to the responses actually seen receive more likelihood weight.")
;;
;; The implementation adds log likelihoods to log prior weights, then uses
;; **log-sum-exp** to normalise them safely into posterior probabilities that
;; sum to 1. A wider grid check later repeats the calculation with 321×161
;; points to test whether the default numerical resolution changes the answer.

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

^:kindly/hide-code
(math/code-detail
 "code-parameter-grid-likelihood"
 "Weighting the deterministic parameter grid by response likelihood"
 [:div
  [:p "Each grid candidate receives its log prior plus the sum of Bernoulli log likelihoods. Normalisation is performed only after all candidates have been scored."]
  [:pre [:code "(defn posterior-grid [xs observations grid prior]\n  (let [parameters (parameter-grid xs grid prior)\n        log-weights\n        (mapv (fn [{:keys [threshold width log-prior]}]\n                (+ log-prior\n                   (log-likelihood observations threshold width)))\n              parameters)\n        weights (normalize-log-weights log-weights)]\n    (mapv #(assoc %1 :weight %2) parameters weights)))"]]
  [:p "The browser demonstration deliberately uses 41×21 points; the authoritative CLJ path keeps 161×81."]
  [:p.article-code-source
   [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/pair_frequency_logistic_v2.cljc"}
    "View the parameter grid, likelihood, and normalisation"]]])

;; ## What is observed and what is predicted
;;
;; **Latent knowledge** is the unobserved known-or-unknown state the model tries
;; to infer for each pair. An **observed outcome** is what the quiz recorded.
;; Those are not interchangeable: a learner can guess, slip, misunderstand a
;; context, or meet a poor distractor. A **prediction** is what the fitted model
;; says about an untested pair or future response; it is neither an observation
;; nor direct access to the latent state.
;;
;; Raw response events remain `:correct`, `:wrong`, or `:dont-know`. V2 still
;; maps correct to `1` and both other values to `0` for inference. Tested pair
;; outcomes are fixed; posterior-predictive simulation draws outcomes only for
;; untested pairs. The point estimate is the mean of the resulting whole-pool
;; totals. The reported 95% equal-tail credible interval runs from their 2.5th
;; to 97.5th percentiles: conditional on this model and the observed responses,
;; it is the central range of totals the model considers plausible. A fixed
;; random seed makes the endpoints reproducible. The interval is not a
;; guarantee that the learner's true total lies inside it.
;;
;; ### Residual variation and misspecification
;;
;; A **residual** is the difference left after the frequency curve has made its
;; prediction. Two pairs with the same $x$ can still differ because of the word,
;; context, answer choices, or learner history. The supported simulation added
;; independent mean-zero residual variation to log odds. That tests noisy local
;; departures, not persistent calibrated item effects.
;;
;; **Model misspecification** means the assumed probability model does not match
;; the process generating outcomes. The fitted v2 interval includes uncertainty
;; about $(t,w)$ and untested binary outcomes. It does **not** automatically
;; widen for a wrong curve shape, systematic residuals, guessing, slips,
;; context, distractors, or sense distinctions. Stress simulations introduce
;; some of those mismatches explicitly later.

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

^:kindly/hide-code
(kind/hiccup
 [:div#pair-frequency-response-inference-simulator
  [:p "Loading the response-and-schedule demonstration…"]
  [:noscript "This demonstration needs JavaScript. The seeded schedule remains response-independent."]])

^:kindly/hide-code
(math/code-detail
 "code-response-independent-schedule"
 "Creating a balanced schedule before any responses exist"
 [:div
  [:p "Each stratum is shuffled from the explicit seed. A round takes the next item from every queue, records its selection probability, then shuffles those eight items for presentation."]
  [:pre [:code "(defn selection-schedule [pairs strata-count seed]\n  (let [queues (selection-queues pairs strata-count seed)\n        rounds (apply min (map count queues))]\n    (loop [round-index 0\n           state (normalize-seed (+ seed 104729))\n           result []]\n      (if (= round-index rounds)\n        result\n        (let [selected\n              (mapv (fn [stratum-index queue]\n                      (assoc (nth queue round-index)\n                             :stratum-index stratum-index\n                             :round-index round-index\n                             :selection-probability\n                             (/ 1.0 (- (count queue) round-index))))\n                    (range strata-count) queues)\n              [ordered next-state] (shuffle-vector selected state)]\n          (recur (inc round-index) next-state\n                 (conj result ordered)))))))"]]
  [:p "Tests assert one item per stratum, no repeats, deterministic replay, and equality when only response data change."]
  [:p.article-code-source
   [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/pair_frequency_logistic_v2.cljc"}
    "View the complete selection schedule"]]])
;;
;; ### Try one seeded v1/v2 quiz

^:kindly/hide-code
(kind/hiccup
 [:div.pf-lab
  [:div#pair-frequency-simulation-lab
   [:p "Loading the seeded simulation lab…"]]
  [:noscript "This lab needs JavaScript."]
  [:script#pair-frequency-xs {:type "application/json"}
   (str "[" (str/join "," fixture-xs) "]")]])

;; The browser uses a bounded 41×21 grid and 300 predictive draws so the
;; mechanism stays interactive. The authoritative checks and large simulation
;; run in CLJ.
;;
;; ## Simulating a scorer before trusting it
;;
;; Simulation makes the hidden truth available because the program creates it.
;; A **scenario** states how knowledge and measurement are generated. A
;; **cell** is one exact combination of scenario settings. A **replicate** is
;; one newly drawn learner-pool under that cell. The **nominal target** is the
;; cell's intended expected known total before random draws; **realised truth**
;; is the actual count of the 8,000 latent binary outcomes in one replicate.
;; They are close on average but need not be equal.
;;
;; V1 and v2 receive the same schedule and response prefix in each replicate.
;; This is a **paired comparison**: differences between scorers are not mixed
;; with different simulated learners or questions.

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

^:kindly/hide-code
(def stress-result
  (edn/read-string
   (slurp (io/resource
           "language_learning/vocabulary_estimation/pair_frequency_logistic_v2_stress.edn"))))

^:kindly/hide-code
(def tuning-diagnostic-candidate
  (first (filter #(= (:rule %) (:rule held-out-candidate))
                 (:rules tuning-result))))

^:kindly/hide-code
(def simulation-phases
  [{:phase "Rule tuning"
    :cells (:supported-cell-count tuning-result)
    :replicates (:replicates-per-cell tuning-result)
    :learners (* (:supported-cell-count tuning-result)
                 (:replicates-per-cell tuning-result))
    :rules (count (:rules tuning-result))}
   {:phase "Held-out supported diagnostic"
    :cells (:supported-cell-count held-out-result)
    :replicates (:replicates-per-cell held-out-result)
    :learners (* (:supported-cell-count held-out-result)
                 (:replicates-per-cell held-out-result))
    :rules (count (:rules held-out-result))}
   {:phase "Untuned stress diagnostic"
    :cells (:stress-cell-count stress-result)
    :replicates (:replicates-per-cell stress-result)
    :learners (* (:stress-cell-count stress-result)
                 (:replicates-per-cell stress-result))
    :rules 1}])

^:kindly/hide-code
(def gate-inspector-data
  (let [v1-aggregate (get-in tuning-result [:v1 :aggregate])
        v2-aggregate (:aggregate tuning-diagnostic-candidate)]
    {:actual
     {:aggregate-coverage (* 100.0 (:coverage v2-aggregate))
      :minimum-cell-coverage (* 100.0
                                (:minimum-cell-coverage
                                 tuning-diagnostic-candidate))
      :aggregate-mae (:mae v2-aggregate)
      :maximum-cell-mae-ratio (* 100.0
                                 (:maximum-cell-mae-ratio
                                  tuning-diagnostic-candidate))
      :median-items (:median-items v2-aggregate)}
     :defaults
     {:aggregate-coverage 94.5
      :minimum-cell-coverage 94.0
      :aggregate-mae (/ (double (Math/round (* 10.0 (:mae v1-aggregate))))
                        10.0)
      :maximum-cell-mae-ratio 105.0
      :median-items (:median-items v1-aggregate)}}))

^:kindly/hide-code
(def gate-inspector-json
  (let [{actual :actual defaults :defaults} gate-inspector-data]
    (str
     "{\"actual\":{"
     "\"aggregateCoverage\":" (Double/toString (:aggregate-coverage actual)) ","
     "\"minimumCellCoverage\":" (Double/toString (:minimum-cell-coverage actual)) ","
     "\"aggregateMae\":" (Double/toString (:aggregate-mae actual)) ","
     "\"maximumCellMaeRatio\":" (Double/toString (:maximum-cell-mae-ratio actual)) ","
     "\"medianItems\":" (:median-items actual) "},"
     "\"defaults\":{"
     "\"aggregateCoverage\":" (Double/toString (:aggregate-coverage defaults)) ","
     "\"minimumCellCoverage\":" (Double/toString (:minimum-cell-coverage defaults)) ","
     "\"aggregateMae\":" (Double/toString (:aggregate-mae defaults)) ","
     "\"maximumCellMaeRatio\":" (Double/toString (:maximum-cell-mae-ratio defaults)) ","
     "\"medianItems\":" (:median-items defaults) "}}")))

;; ### What one simulation replicate did
;;
;; One replicate represents one possible learner-specific pattern of knowledge
;; across the complete 8,000-pair fixture. It is more than a simulated sequence
;; of quiz answers: because all 8,000 latent outcomes are generated first, the
;; simulation knows the learner's realised total and can check an estimate
;; against that otherwise hidden truth.
;;
;; 1. Start with the fixture's 8,000 real standardised log-frequency values.
;; 2. Choose one scenario cell: a target total, curve width, and residual or
;;    measurement condition. Numerically move the threshold until the
;;    scenario's no-random-residual expected total matches the target.
;; 3. For every pair, calculate its knowing probability and make one Bernoulli
;;    draw. The sum of those 8,000 binary outcomes is the realised truth for
;;    that replicate; it varies around the nominal target.
;; 4. Read the outcomes of the pairs in the phase's balanced, non-adaptive
;;    schedule. In stress scenarios, optionally flip some latent outcomes to
;;    create measured responses with false positives or false negatives.
;; 5. Feed exactly the same response prefix to v1 and v2 after each complete
;;    eight-item round. Each model produces a count estimate, a 95% interval,
;;    and a response log score.
;; 6. Apply a candidate stopping rule at those checkpoints. Record whether the
;;    final interval contains the realised 8,000-pair truth, the signed and
;;    absolute count error, interval width, response log score, and quiz length.

^:kindly/hide-code
(kind/hiccup
 [:figure.pf-diagram
  [:ol.pf-diagram-steps
   {:role "img"
    :aria-label "One simulation replicate in six steps. Start with the fixed 8,000-pair fixture and one scenario cell. Draw every latent outcome and save their sum as realised truth. Observe only pairs in the seeded balanced schedule, with declared measurement error if applicable. Score identical response prefixes with v1 and v2. Apply the candidate stopping rule. Compare both results with realised truth using coverage, error, interval width, log score, and quiz length."}
   [:li [:strong "Fixture + cell"] "Choose target, width, and residual or error condition."]
   [:li [:strong "Latent pool"] "Draw all 8,000 known-or-unknown outcomes; save their sum as realised truth."]
   [:li [:strong "Scheduled observations"] "Reveal seeded balanced pairs; apply only the declared measurement error."]
   [:li [:strong "Paired scoring"] "Give identical response prefixes to v1 and v2."]
   [:li [:strong "Stopping"] "Apply the same candidate rule at complete rounds."]
   [:li [:strong "Metrics"] "Compare intervals, errors, log scores, and lengths with realised truth."]]
  [:figcaption.pf-caption
   "The latent total and the observed response stream are separate. Pairing keeps scorer comparisons on the same simulated learner and schedule."]])

^:kindly/hide-code
(math/code-detail
 "code-simulated-replicate"
 "Generating one complete latent learner-pool and its measured responses"
 [:div
  [:p "The runner first draws every latent pair outcome and saves their sum. Only then does it look up the scheduled pair indexes and optionally flip measured outcomes."]
  [:pre [:code "(defn simulate-replicate\n  [xs selected\n   {:keys [threshold residual-sd false-positive false-negative\n           rank-error-maximum]\n    :or {false-positive 0.0 false-negative 0.0\n         rank-error-maximum 0.0}\n    :as scenario}\n   seed]\n  (let [rng (Random. (long seed))\n        known (byte-array pool-size)\n        true-total\n        (loop [index 0 total 0]\n          (if (= index pool-size)\n            total\n            (let [residual (if (zero? residual-sd)\n                             0.0\n                             (* residual-sd (.nextGaussian rng)))\n                  probability\n                  (latent-probability (nth xs index) scenario\n                                      threshold residual)\n                  outcome (if (< (.nextDouble rng) probability) 1 0)]\n              (aset-byte known index (byte outcome))\n              (recur (inc index) (+ total outcome)))))\n        responses (byte-array maximum-items)]\n    (dotimes [index maximum-items]\n      (let [pair-index (:pair-index (nth selected index))\n            known? (= 1 (aget known pair-index))\n            rank-error (* rank-error-maximum (/ pair-index 7999.0))]\n        (aset-byte responses index\n                   (byte (measured-outcome\n                          rng known?\n                          (+ false-positive rank-error)\n                          (+ false-negative rank-error))))))\n    {:truth true-total :responses responses}))"]]
  [:p "The first loop materialises all 8,000 latent outcomes. The second observes only the fixed schedule and applies any declared measurement-error mechanism."]
  [:p.article-code-source
   [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/pair_frequency_logistic_v2_gate.clj"}
    "View the complete replicate generator"]]])

;; Within a phase, one seeded 160-item schedule was reused for every cell and
;; replicate. This paired the model comparisons and made the run deterministic.
;; Tuning, held-out, and stress phases used different seeds and therefore
;; different schedules. A limitation is that each phase is still conditional
;; on one item schedule; these runs did not average over many independently
;; drawn selection schedules.

;; ### What varied in the 45 supported cells
;;
;; Tuning crossed all values in the following table. The target is exact for
;; the baseline curve before random pair residuals and Bernoulli draws, hence
;; "nominal": the realised total in any replicate is not forced to equal it.

^:kindly/hide-code
(kind/hiccup
 [:div.pf-table-wrap
  [:table.pf-table.pf-explain-table
   [:caption.pf-sr-only "Supported simulation-cell dimensions"]
   [:thead
    [:tr [:th {:scope "col"} "Dimension"]
     [:th {:scope "col"} "Values"]
     [:th {:scope "col"} "Question asked"]]]
   [:tbody
    [:tr [:th {:scope "row"} "Nominal known fraction"]
     [:td "10%, 30%, 50%, 70%, 90%"]
     [:td "Does performance hold for small, middle, and large vocabularies?"]]
    [:tr [:th {:scope "row"} "10%–90% curve width"]
     [:td "0.75, 1.5, 3.0 SD"]
     [:td "Does performance hold for steep and gradual frequency transitions?"]]
    [:tr [:th {:scope "row"} "Independent pair residual SD"]
     [:td "0, 0.5, 1.0 log-odds"]
     [:td "What if otherwise similar-frequency pairs differ for unmodelled reasons?"]]]]])

;; A residual was drawn independently for every pair in every replicate and
;; added to its log odds before the latent outcome was drawn. An SD of `0` is
;; the fitted v2 family exactly. At SD `1`, a one-SD residual multiplies the
;; pair's odds by about `e¹ = 2.72`, so this is substantial unmodelled
;; heterogeneity. It is not a persistent item calibration effect shared across
;; simulated learners.
;;
;; ### Three separated phases
;;
;; **Tuning** searches choices using one dataset. A **held-out evaluation** uses
;; fresh data and a different seed after choices are frozen. A **stress test**
;; deliberately generates conditions outside the candidate model. Reusing the
;; tuning seed or feeding diagnostic results back into the rule would erase
;; that separation.

^:kindly/hide-code
(kind/hiccup
 [:figure.pf-diagram
  [:div.pf-phase-diagram
   {:role "img"
    :aria-label "Three separated simulation phases. Tuning uses seed 2026071301, 45 cells, 500 replicates per cell, and evaluates 100 rules against five checks. No rule passes, so the diagnostic candidate is frozen. It then goes separately to a held-out supported diagnostic with seed 2026071302, 45 cells and 2,000 replicates per cell, and to an untuned stress diagnostic with seed 2026071305, 60 cells and 2,000 replicates per cell. Neither diagnostic feeds back into tuning."}
   [:div.pf-phase-node
    [:strong "1 · Tuning"]
    "45 cells × 500 · seed 2026071301"
    [:br]
    "Evaluate 100 rules against all five checks."]
   [:div.pf-arrow {:aria-hidden "true"} "→"]
   [:div.pf-phase-branch
    [:div.pf-phase-node
     [:strong "2 · Freeze"]
     "No rule passed. Freeze one diagnostic candidate; do not promote."]
    [:div.pf-phase-node
     [:strong "3a · Held-out"]
     "45 cells × 2,000 · seed 2026071302 · diagnosis only."]
    [:div.pf-phase-node
     [:strong "3b · Stress"]
     "60 cells × 2,000 · seed 2026071305 · diagnosis only."]]]
  [:figcaption.pf-caption
   "Separate data and seeds preserve the direction of learning: tuning may choose what to inspect; diagnostics never rewrite tuning."]])

^:kindly/hide-code
(kind/hiccup
 [:div.pf-table-wrap
  [:table.pf-table
   [:caption.pf-sr-only "Simulation phases and replicate counts"]
   [:thead
    [:tr [:th {:scope "col"} "Phase"]
     [:th {:scope "col"} "Cells"]
     [:th {:scope "col"} "Replicates / cell"]
     [:th {:scope "col"} "Simulated learner-pools"]
     [:th {:scope "col"} "Rules scored"]]]
   [:tbody
    (for [{:keys [phase cells replicates learners rules]} simulation-phases]
      [:tr {:key phase}
       [:th {:scope "row"} phase]
       [:td cells]
       [:td (format "%,d" replicates)]
       [:td (format "%,d" learners)]
       [:td rules]])
    [:tr
     [:th {:scope "row"} "Total"]
     [:td "150 cell-phases"]
     [:td "—"]
     [:td (format "%,d" (reduce + (map :learners simulation-phases)))]
     [:td "—"]]]]])

;; The 500-replicate tuning run generated 22,500 independent learner-pool
;; realisations—not 2.25 million. The same 22,500 response streams were rescored
;; under all 100 rules. Across the three phases the immutable artifacts record
;; 232,500 simulated learner-pools.

^:kindly/hide-code
(math/code-detail
 "code-read-edn-artifacts"
 "Reading immutable simulation-result artifacts at render time"
 [:div
  [:p "The article does not rerun 232,500 learner-pools during publication. It reads frozen EDN data produced by the separately seeded validation runs."]
  [:pre [:code "(def tuning-result\n  (edn/read-string\n   (slurp (io/resource\n           \"language_learning/vocabulary_estimation/\n            pair_frequency_logistic_v2_tuning.edn\"))))"]]
  [:p "The rendered tables, gate inspector payload, assertions, fixture ID, hash, seeds, and numerical-method record all come from those versioned resources."]
  [:p.article-code-source
   [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/tree/main/resources/language_learning/vocabulary_estimation"}
    "View the immutable EDN artifacts"]]])
;;
;; ### Metrics: what counted as better?
;;
;; **Coverage** is the fraction of repeated 95% intervals containing the
;; replicate's realised truth. **Calibration** means those intervals achieve
;; their stated long-run coverage under the tested scenario; it does not mean
;; every individual interval contains truth. **Bias** is mean signed error:
;; estimate minus truth. **Mean absolute error (MAE)** ignores direction and
;; averages the size of the count error.
;;
;; **Response log score** rewards probability assigned to the response that
;; occurred; larger (less negative) is better. It tests response prediction,
;; not count calibration by itself. **Quiz length** is the number of administered
;; items at stopping. **Monte Carlo standard error (MCSE)** describes noise from
;; a finite number of simulation replicates. For estimated coverage $\hat p$
;; from $n$ independent replicates:
;;
;; $$\operatorname{MCSE}(\hat p)\approx
;; \sqrt{\frac{\hat p(1-\hat p)}{n}}.$$

^:kindly/hide-code
(math/explanation
 "math-coverage-mcse"
 "Monte Carlo uncertainty in an estimated coverage proportion"
 [["p̂" "The observed fraction of simulated intervals containing realised truth."]
  ["n" "The number of independent replicates in the cell."]
  ["p̂(1−p̂)" "The Bernoulli variance implied by the observed coverage."]
  ["square root" "Converts the variance of the estimated fraction into a standard error."]]
 "MCSE measures simulation noise, not uncertainty about whether the simulated scenarios resemble real learners.")
;;
;; ## Precommitment: choose rules before results
;;
;; **Precommitment** means declaring candidate rules, metrics, thresholds, and
;; the all-checks-must-pass logic before seeing their results. It prevents a
;; near miss from becoming a pass by moving the finish line afterwards.
;;
;; ### How stopping rules were tuned
;;
;; The search crossed minimum lengths `24`, `32`, `40`, and `48`; interval
;; half-width targets `5%`, `7.5%`, `10%`, `12.5%`, and `15%` of the pool; and
;; soft caps `64`, `80`, `96`, `128`, and `160`. This produced 100 valid rules.
;; A rule stopped at the first complete-round checkpoint at which its width
;; target was met, or at its cap.

^:kindly/hide-code
(math/code-detail
 "code-stopping-rule-predicate"
 "Applying one candidate stopping rule at complete rounds"
 [:div
  [:p "Starting at the rule's minimum, the runner checks eight-item boundaries until precision is reached or the soft cap is reached."]
  [:pre [:code "(let [precision?\n      (<= (/ (- upper lower) 2.0)\n          (* target-half-width-ratio pool-size))\n      soft-maximum?\n      (>= items-tested soft-maximum-items)]\n  (if (or precision? soft-maximum?)\n    {:items-tested items-tested\n     :stopping-reason\n     (if precision? :precision-target :soft-maximum)}\n    (recur (+ items-tested strata-count))))"]]
  [:p "The 100 rules change only minimum, target half-width, and cap; the balanced schedule and scorers remain fixed."]
  [:p.article-code-source
   [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/pair_frequency_logistic_v2_gate.clj"}
    "View the stopping rule search"]]])
;;
;; Each rule had to pass all five checks below. Aggregate checks prevent a noisy
;; single cell from dominating; cellwise checks prevent good averages from
;; hiding a subgroup in which the new scorer is poorly calibrated or less
;; accurate than v1.

^:kindly/hide-code
(kind/hiccup
 [:div.pf-table-wrap
  [:table.pf-table.pf-explain-table
   [:caption.pf-sr-only "Precommitted v2 promotion requirements"]
   [:thead
    [:tr [:th {:scope "col"} "Check"]
     [:th {:scope "col"} "Requirement"]
     [:th {:scope "col"} "Purpose"]]]
   [:tbody
    [:tr [:th {:scope "row"} "Aggregate interval coverage"]
     [:td "≥94.5%"]
     [:td "Keep the nominal 95% interval honest overall"]]
    [:tr [:th {:scope "row"} "Every-cell interval coverage"]
     [:td "≥94%"]
     [:td "Protect each ability × width × residual condition"]]
    [:tr [:th {:scope "row"} "Aggregate MAE"]
     [:td "Lower than v1"]
     [:td "Improve average count accuracy"]]
    [:tr [:th {:scope "row"} "Every-cell MAE"]
     [:td "≤105% of v1"]
     [:td "Do not buy average gains with a large local regression"]]
    [:tr [:th {:scope "row"} "Median quiz length"]
     [:td "No longer than v1"]
     [:td "Do not require more answers for the gain"]]]]])

^:kindly/hide-code
(math/code-detail
 "code-five-part-gate"
 "Requiring all five promotion checks with logical AND"
 [:div
  [:p "The aggregate and cellwise checks are evaluated together. Clojure's and returns true only when every clause is true."]
  [:pre [:code "(and (>= (:coverage aggregate) 0.945)\n     (every? #(>= (:coverage %) 0.94) cells)\n     (< (:mae aggregate) (:mae v1-aggregate))\n     (every? true?\n             (map #(<= (:mae %1) (* 1.05 (:mae %2)))\n                  cells v1-cells))\n     (<= (:median-items aggregate)\n         (:median-items v1-aggregate)))"]]
  [:p "There is no score, majority vote, or discretionary override. One false clause prevents promotion."]
  [:p.article-code-source
   [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/pair_frequency_logistic_v2_gate.clj"}
    "View the gate calculation"]]])

;; ## Gate result: no rule passed
;;
;; No rule satisfied all five checks. The rule with the best worst-cell
;; coverage started at 48 items, targeted a 7.5%-of-pool half-width, and capped
;; at 64. In tuning it achieved 95.48% aggregate coverage and MAE 253.0, but its
;; worst cell covered only 92.4%, its worst cell's MAE was 22.0% above v1, and
;; its median length was 64 rather than v1's 40. It was therefore not eligible
;; for promotion.

^:kindly/hide-code
(math/code-detail
 "code-passing-rule-selection"
 "Selecting only among rules that already passed"
 [:div
  [:p "Selection first filters to passes? rules, then prefers the shortest by median and mean length with deterministic tie-breakers. With no passes, it returns nil."]
  [:pre [:code "(defn choose-rule [tuning]\n  (first\n   (sort-by\n    (juxt #(get-in % [:aggregate :median-items])\n          #(get-in % [:aggregate :mean-items])\n          #(get-in % [:rule :minimum-items])\n          #(get-in % [:rule :soft-maximum-items])\n          #(get-in % [:rule :target-half-width-ratio]))\n    (filter :passes? (:rules tuning)))))\n\n;; Historical result:\n(choose-rule tuning-result) ;=> nil"]]
  [:p "The best-worst-coverage rule was chosen only for diagnosis after the promotion gate had failed; it was never relabelled as a winner."]
  [:p.article-code-source
   [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/pair_frequency_logistic_v2_gate.clj"}
    "View passing-rule selection and its tests"]]])

;; ### Inspect the historical gate
;;
;; The five actual values below come from the immutable tuning artifact. Moving
;; a slider asks a counterfactual question only: “What if the threshold had been
;; different?” It never edits the EDN file, reruns the simulation, or changes
;; the recorded non-promotion decision. Reset restores the precommitted gate.

^:kindly/hide-code
(kind/hiccup
 [:div.pf-lab
  [:div#pair-frequency-gate-inspector
   [:p "Loading the gate inspector…"]]
  [:noscript "This inspector needs JavaScript."]
  [:script#pair-frequency-gate-data {:type "application/json"}
   gate-inspector-json]
  [:script {:type "application/x-scittle"
            :src "pair_frequency_logistic_v2.cljc"}]
  [:script {:type "application/x-scittle"
            :src "pair_frequency_logistic_v2_interactive.cljs"}]])
;;
;; At the historical defaults, only aggregate coverage and aggregate MAE pass.
;; Worst-cell coverage, worst-cell MAE ratio, and median length fail. The
;; overall decision is therefore **not promoted**: two passes out of five are
;; not enough, and even four would not have been enough.
;;
;; ## Held-out diagnosis after the failed gate
;;
;; Because there was no eligible rule, the 2,000-replicate-per-cell run below is
;; a **held-out diagnostic**, not a promotion gate. Following the declared
;; priority—coverage, then MAE, then length—it examines that least-bad coverage
;; rule: minimum 48, 7.5% target half-width, cap 64.
;;
;; "Coverage" is the percentage of replicates whose reported 95% interval
;; contains the realised 8,000-pair truth. Bias is mean estimate minus truth;
;; MAE is the mean absolute size of that error. The response log score measures
;; probability assigned to the observed answers; larger (less negative) is
;; better. It evaluates response prediction, not count calibration by itself.

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

^:kindly/hide-code
(math/code-detail
 "code-recorded-numerical-shortcut"
 "Recording the large-run approximation and refusing to excuse a near miss"
 [:div
  [:p "Every result artifact names the approximation and draw count, so a future rerun can reproduce or replace it without pretending the computation was exact."]
  [:pre [:code ":interval-method\n{:parameter-mixture :deterministic-systematic-grid-sampling\n :untested-pair-total :moment-matched-poisson-binomial-normal\n :draws 512}"]]
  [:p "The shortcut was declared and recorded, but it was not validated tightly enough to rescue a failed cell. A candidate near a threshold needs stronger numerical evidence, not a post-hoc waiver."]
  [:p.article-code-source
   [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/pair_frequency_logistic_v2_gate.clj"}
    "View the deterministic mixture interval and artifact metadata"]]])

;; Aggregate v2 coverage and MAE improved substantially, but the gate protects
;; against hiding weak cells in an average. Worst-cell coverage was `91.95%`,
;; worst-cell MAE was `21.1%` worse than v1, and median length was `64` rather
;; than `40`. The decision is therefore unambiguous: **retain v1**.
;;
;; Concretely, the weakest cell covered 1,839 of 2,000 realised truths and
;; missed 161. Its Monte Carlo standard error is about 0.61 percentage points,
;; so the 2.05-point miss against the 94% threshold is not a one- or two-draw
;; wobble. Aggregate MAE can still improve while one cell regresses: that is
;; exactly why both aggregate and cellwise checks were declared. A median of 64
;; means at least half of v2 diagnostic runs used 64 answers, versus a v1
;; median of 40.
;;
;; V1's poor aggregate coverage here is not evidence that v1 has passed a
;; realistic validation study. These supported scenarios deliberately generate
;; smooth frequency curves, so they are favourable to v2 and unfavourable to
;; v1's eight separate, sparsely observed rates—especially near 10% and 90%.
;; The question was narrower: even under favourable synthetic conditions, did
;; v2 meet absolute coverage requirements, avoid materially worse cells, and
;; shorten or preserve test length? It did not.
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
(kind/hiccup
 [:div.pf-table-wrap
  [:table.pf-table.pf-explain-table
   [:caption.pf-sr-only "Stress simulation mechanisms"]
   [:thead
    [:tr [:th {:scope "col"} "Stress"]
     [:th {:scope "col"} "How responses were generated"]
     [:th {:scope "col"} "What it probes"]]]
   [:tbody
    [:tr [:th {:scope "row"} "Non-logistic mixture"]
     [:td "Average of a steep curve (width 0.75, threshold t−0.6) and a gentle curve (width 3.0, threshold t+0.6)."]
     [:td "A monotone frequency relationship that one logistic curve cannot exactly represent."]]
    [:tr [:th {:scope "row"} "Frequency-related residual"]
     [:td "Add either −0.75x or +0.75x to the baseline log odds."]
     [:td "An omitted systematic frequency component that weakens or strengthens the fitted slope."]]
    [:tr [:th {:scope "row"} "False positive"]
     [:td "Flip a latent unknown pair to a correct response with probability 2%, 5%, or 10%."]
     [:td "Guessing-like responses; truth remains the latent known count."]]
    [:tr [:th {:scope "row"} "False negative"]
     [:td "Flip a latent known pair to an incorrect response with probability 2%, 5%, or 10%."]
     [:td "Slip or measurement failure; truth remains the latent known count."]]
    [:tr [:th {:scope "row"} "Rank-increasing error"]
     [:td "Increase both flip rates linearly from 0 at the most frequent end to 2%, 5%, or 10% at rank 8,000."]
     [:td "Measurement quality that worsens systematically toward rarer pairs."]]]]])

;; Each mechanism was crossed with all five nominal ability targets. The
;; mixture therefore contributed 5 cells, the two residual slopes 10, and each
;; three-rate error family 15: 60 cells and 120,000 new learner-pool
;; realisations in total.

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
;; ## Model validation is not a software build
;;
;; The **model-validation gate** asks whether simulated measurement performance
;; justifies promotion. An **automated test** asks whether code still behaves as
;; specified; Clojure's `clojure.test` framework records such assertions. A
;; **targeted render** rebuilds only this article for fast review. The
;; **full-site publication gate** renders the complete site immediately before
;; publishing, catching cross-page and shared-configuration failures. These are
;; different forms of evidence: perfect software tests cannot turn a failed
;; coverage check into a pass.
;;
;; The executable source is evaluated by
;; [Clay](https://scicloj.github.io/clay/), which emits the article input;
;; [Quarto](https://quarto.org/docs/cli/render.html) renders one page or a whole
;; project; and [Scittle](https://github.com/babashka/scittle) runs the bounded
;; ClojureScript interactions in the browser. The CLJ and CLJS suites check
;; parity and deterministic replay before visual checks cover controls,
;; accessibility, responsive layout, themes, and console errors.

^:kindly/hide-code
(kind/hiccup
 [:figure.pf-diagram
  [:div.pf-workflow-lanes
   {:role "img"
    :aria-label "Two evidence lanes meet at a versioned publication decision. The model lane moves from immutable fixture and seeded artifacts through the five-part validation gate to either promotion if every check passes or non-promotion if any check fails. The software lane moves from Clojure and ClojureScript source through automated tests, targeted rendering, separate-tab browser checks, and the full-site publication gate. Software success cannot override model-gate failure."}
   [:section.pf-lane
    [:h3 "Model evidence"]
    [:ol.pf-diagram-steps
     [:li [:strong "Versioned evidence"] "Immutable fixture, seeds, scenarios, and result artifacts."]
     [:li [:strong "Validation gate"] "Coverage, MAE, and length; all five checks required."]
     [:li [:strong "Decision"] "Promote only if all pass; otherwise retain v1."]]]
   [:section.pf-lane
    [:h3 "Software and publication evidence"]
    [:ol.pf-diagram-steps
     [:li [:strong "Automated tests"] "CLJ, CLJS, parity, and executable assertions."]
     [:li [:strong "Targeted render"] "Clay and Quarto rebuild the affected article."]
     [:li [:strong "Browser checks"] "Controls, console, layout, themes, and accessibility in a separate tab."]
     [:li [:strong "Publication gate"] "Render the full site immediately before push."]]]]
  [:figcaption.pf-caption
   "Both lanes must be trustworthy, but they answer different questions. Passing software checks cannot override failed model evidence."]])
;;
;; A later promoted scorer would still retain its versioned predecessor so a
;; **rollback** could restore the earlier target without rewriting old events or
;; articles. Here no rollback is needed: v2 never replaced v1.
;;
;; ## What was learned—and what comes next
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
;;
;; The next article will define and version how self-reported CEFR chooses a
;; lemma–surface-form-pair pool. Later articles will consider correlated form
;; pairs through latent lemma knowledge; model correct, wrong, and don't-know
;; separately with guessing and slips; calibrate complete item versions before
;; evaluating IRT and adaptive selection; and investigate multiple contexts or
;; senses only if stable sense identifiers and repeated observations make them
;; identifiable. Those are previews, not features silently added to v2.

^:kindly/hide-code
(kind/hiccup
 [:div.article-recap
  [:strong "Decision"]
  [:p "Pair frequency was useful enough to study further, but this continuous v2 scorer did not earn promotion. The gate stayed fixed, the negative result stayed visible, and v1 remained the target."]])

^{:kindly/hide-code true :kindly/kind :kind/hidden}
(do
  (assert (= 8000 (count fixture-pairs)))
  (assert (= [0.5 0.1 0.9]
             (mapv #(/ (double (Math/round (* 10.0 %))) 10.0)
                   [(:at-threshold curve-check)
                    (:one-half-width-below curve-check)
                    (:one-half-width-above curve-check)])))
  (assert (get-in grid-check [:convergence :passes?]))
  (assert (empty? (filter :passes? (:rules tuning-result))))
  (assert (= tuning-diagnostic-candidate
             (apply max-key :minimum-cell-coverage (:rules tuning-result))))
  (assert (= 0.9548444444444445
             (get-in tuning-diagnostic-candidate [:aggregate :coverage])))
  (assert (= 0.924 (:minimum-cell-coverage tuning-diagnostic-candidate)))
  (assert (= 253.03401249924173
             (get-in tuning-diagnostic-candidate [:aggregate :mae])))
  (assert (= 1.2204936105432285
             (:maximum-cell-mae-ratio tuning-diagnostic-candidate)))
  (assert (= 64 (get-in tuning-diagnostic-candidate
                        [:aggregate :median-items])))
  (assert (= 610.7162847330441 (get-in tuning-result [:v1 :aggregate :mae])))
  (assert (= 40 (get-in tuning-result [:v1 :aggregate :median-items])))
  (assert (= 232500 (reduce + (map :learners simulation-phases))))
  (assert (not (:passes? held-out-candidate)))
  (assert (= 1839 (long (* (:replicates-per-cell held-out-result)
                           (:minimum-cell-coverage held-out-candidate)))))
  (assert (= 60 (:stress-cell-count stress-result))))
