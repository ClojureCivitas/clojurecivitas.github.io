^{:kindly/hide-code true
  :kindly/options {:html/deps [:scittle :reagent]}
  :clay {:hide-info-line true
         :title "Estimating Vocabulary Size: A Stratified Beta–Binomial First Pass"
         :quarto {:author :jamiep
                  :description "A learning-in-public first pass at estimating receptive vocabulary from stratified responses with a Beta–binomial model."
                  :type :post
                  :date "2026-07-12"
                  :category :concepts
                  :tags [:bayesian-statistics :language-learning :clojure :scittle]
                  :keywords [:vocabulary-estimation :beta-binomial :posterior-predictive :stratified-sampling]}}}

(ns language-learning.vocabulary-estimation.beta-binomial-first-pass
  (:require [fastmath.random :as random]
            [language-learning.vocabulary-estimation.math-explanations :as math]
            [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(kind/hiccup
 [:style
  (str
   ":root{--ve-accent:#1464b5;--ve-accent-soft:#e5f1fb;--ve-warm:#9a4b00;--ve-warm-soft:#fff0df;--ve-purple:#694aa6;--ve-muted:#4f5b66}"
   ".quarto-dark{--ve-accent:#73b7ff;--ve-accent-soft:#173653;--ve-warm:#ffc27a;--ve-warm-soft:#4a2d12;--ve-purple:#c5a7ff;--ve-muted:#b9c7d2}"
   "#title-block-header{padding-top:.75rem}#title-block-header h1{line-height:1.15;overflow-wrap:anywhere}"
   "mjx-container[display=true]{max-width:100%;overflow-x:auto;overflow-y:hidden}"
   ".ve-callout{border:1px solid color-mix(in srgb,var(--ve-accent) 45%,var(--bs-border-color,#dee2e6));border-left:4px solid var(--ve-accent);background:color-mix(in srgb,var(--bs-body-bg,#fff) 90%,var(--ve-accent) 10%);color:var(--bs-body-color,#212529);padding:1rem 1.15rem;margin:1.4rem 0;border-radius:.35rem}"
   ".ve-callout.provisional{border-color:color-mix(in srgb,var(--ve-warm) 60%,var(--bs-border-color,#dee2e6));border-left-color:var(--ve-warm);background:color-mix(in srgb,var(--bs-body-bg,#fff) 90%,var(--ve-warm) 10%)}"
   ".ve-callout strong{display:block;margin-bottom:.3rem}.ve-callout p:last-child{margin-bottom:0}"
   ".ve-grid,.ve-definition-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,15rem),1fr));gap:.75rem;margin:1.25rem 0}"
   ".ve-card,.ve-definition{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.5rem;padding:.85rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529);overflow-wrap:anywhere}"
   ".ve-card h3{font-size:1rem;margin-top:0}.ve-definition dt{font-weight:800;color:var(--ve-accent)}.ve-definition dd{margin:.25rem 0 0}"
   ".ve-table-wrap{max-width:100%;overflow-x:auto;margin:1.25rem 0}.ve-table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}"
   ".ve-table th,.ve-table td{padding:.55rem .7rem;border-bottom:1px solid var(--bs-border-color,#dee2e6);text-align:right;white-space:nowrap}.ve-table th:first-child,.ve-table td:first-child{text-align:left}.ve-table thead th{border-bottom:2px solid var(--bs-border-color,#adb5bd)}"
   ".ve-figure{margin:1.5rem 0;padding:1rem;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.5rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.ve-figure svg{display:block;width:100%;height:auto}"
   ".ve-caption,.ve-note{font-size:.9rem;color:var(--ve-muted);margin:.75rem 0 0}.ve-simulator{margin:1.5rem 0}"
   ".ve-reading-guide{border-left:4px solid var(--ve-accent);padding:.2rem 0 .2rem 1rem;margin:1rem 0}.ve-reading-guide h3{font-size:1rem;margin:.1rem 0 .35rem}.ve-reading-guide ul{margin-bottom:.2rem}"
   ".ve-hierarchy{display:grid;grid-template-columns:minmax(8rem,.7fr) auto minmax(0,1.5fr);gap:.7rem;align-items:center}.ve-hierarchy-node{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.7rem;text-align:center;background:var(--bs-body-bg,#fff);overflow-wrap:anywhere}.ve-hierarchy-node strong{display:block;color:var(--ve-accent)}"
   ".ve-hierarchy-arrow{font-size:1.5rem;color:var(--ve-accent);font-weight:800}.ve-form-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.5rem}.ve-sense-gap{grid-column:1/-1;border:2px dashed var(--ve-warm);border-radius:.45rem;padding:.65rem;text-align:center;color:var(--bs-body-color,#212529);background:color-mix(in srgb,var(--bs-body-bg,#fff) 92%,var(--ve-warm) 8%)}"
   ".ve-workflow-caption{text-align:center;color:var(--ve-muted);font-size:.88rem}.ve-sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}"
   "@media(max-width:767px){.ve-hierarchy{grid-template-columns:minmax(0,1fr)}.ve-hierarchy-arrow{transform:rotate(90deg);text-align:center}.ve-form-list{grid-template-columns:minmax(0,1fr)}}"
   "@media(max-width:575px){.ve-table th,.ve-table td{padding:.45rem}.ve-figure{padding:.65rem}}")])

^:kindly/hide-code
(math/styles)

^:kindly/hide-code
(kind/hiccup
 [:style
  (str
   ".ve-round-shell,.ve-stop-shell{min-width:0;border:1px solid var(--bs-border-color,#ced4da);border-radius:.65rem;padding:clamp(.8rem,3vw,1.3rem);background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}"
   ".ve-round-shell h3,.ve-stop-shell h3{margin-top:0}.ve-round-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:.55rem;margin:1rem 0}.ve-round-card{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.65rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 94%,var(--ve-accent) 6%);overflow-wrap:anywhere}.ve-round-card strong,.ve-round-card span,.ve-round-card small{display:block}.ve-round-card small{color:var(--ve-muted);margin-top:.25rem}.ve-round-progress{width:100%;height:.55rem;accent-color:var(--ve-accent)}.ve-round-status{min-height:2.8rem;font-variant-numeric:tabular-nums}"
   ".ve-stop-banner{display:flex;align-items:baseline;gap:.35rem;flex-wrap:wrap;border-left:4px solid var(--ve-accent);border-radius:.35rem;padding:.65rem .8rem;margin:.8rem 0;background:color-mix(in srgb,var(--bs-body-bg,#fff) 90%,var(--ve-accent) 10%)}.ve-stop-banner.is-counterfactual{border-left-color:var(--ve-warm);background:color-mix(in srgb,var(--bs-body-bg,#fff) 90%,var(--ve-warm) 10%)}"
   ".ve-stop-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,14rem),1fr));gap:.8rem;margin:1rem 0}.ve-stop-field{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.65rem}.ve-stop-field>div{display:flex;justify-content:space-between;align-items:baseline;gap:.5rem}.ve-stop-field label{font-weight:700}.ve-stop-field output{font-variant-numeric:tabular-nums;color:var(--ve-muted);text-align:right}.ve-stop-field input{width:100%;accent-color:var(--ve-accent)}.ve-stop-result{border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.75rem;margin:1rem 0}.ve-stop-result ul{margin:.5rem 0 0}.ve-sampling-button:disabled{opacity:.5;cursor:not-allowed}.ve-stop-field input:focus-visible{outline:3px solid color-mix(in srgb,var(--ve-accent) 50%,transparent);outline-offset:3px}"
   "@media(max-width:767px){.ve-round-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:400px){.ve-round-grid{grid-template-columns:minmax(0,1fr)}}")])

^:kindly/hide-code
(kind/hiccup
 [:style
  ".series-toc{min-width:0;border:1px solid var(--bs-border-color,#ced4da);border-radius:.6rem;padding:clamp(.85rem,3vw,1.2rem);margin:0 0 1.4rem;background:var(--bs-body-bg,#fff)}.series-toc h2{font-size:1.2rem;margin:0 0 .55rem}.series-toc p{margin:0 0 .7rem}.series-toc ol{margin:0;padding-left:1.45rem}.series-toc li{padding:.18rem .45rem}.series-status{display:inline-block;margin-left:.35rem;font-size:.7rem;font-weight:700;letter-spacing:.04em;text-transform:uppercase;color:var(--ve-muted)}.series-current{margin:.35rem 0 .35rem -.7rem;border-left:4px solid var(--ve-accent);border-radius:.4rem;padding:.6rem .75rem!important;background:color-mix(in srgb,var(--bs-body-bg,#fff) 84%,var(--ve-accent) 16%);box-shadow:inset 0 0 0 1px color-mix(in srgb,var(--ve-accent) 35%,transparent);font-weight:700}.series-current>a{color:var(--ve-accent)}.series-current .series-status{border-radius:999px;padding:.18rem .48rem;background:var(--ve-accent);color:#fff}.quarto-dark .series-current .series-status{color:#10212b}"])

^:kindly/hide-code
(kind/hiccup
 [:nav.series-toc {:aria-labelledby "series-contents-heading"}
  [:h2#series-contents-heading "Series contents"]
  [:p "The second article applies the previous Bayesian tools to one deliberately narrow measurement problem."]
  [:ol
   [:li [:a {:href "bayes_theorem_simulations.html"}
         "Bayes' theorem from uncertainty to decision"]
    [:span.series-status "published"]]
   [:li.series-current [:a {:href "beta_binomial_first_pass.html"
                            :aria-current "page"}
                        "Estimating vocabulary size: a stratified Beta–binomial first pass"]
    [:span.series-status "you are here"]]
   [:li [:a {:href "pair_frequency_logistic_v2_article.html"}
         "Does pair frequency predict learner responses?"]
    [:span.series-status "published"]]
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

;; A test result is meaningful only after its target is defined. **Measurement**
;; is the disciplined act of connecting recorded observations to that target.
;; It is not the same as attaching a number to a person.
;;
;; I am building [**Lexibench**](https://lexibench.com/), but the model in this
;; article is a historical first-pass target scorer. It is **not a description
;; of the scorer currently deployed at Lexibench**. Correct probability
;; calculations cannot rescue a vague target or an unsuitable item pool.

^:kindly/hide-code
(kind/hiccup
 [:ol.article-chapter-map
  [:li [:strong "Define"] [:br] "What exactly would the reported count mean?"]
  [:li [:strong "Sample"] [:br] "How can eight balanced strata represent a fixed pool?"]
  [:li [:strong "Update"] [:br] "How do responses change uncertainty within each stratum?"]
  [:li [:strong "Predict"] [:br] "How do tested and untested pairs combine into a finite total?"]
  [:li [:strong "Decide"] [:br] "When is the estimate precise enough to stop?"]
  [:li [:strong "Reproduce"] [:br] "How do fixtures, seeds, tests, and publication gates protect the result?"]])

;; ## 1. Define: what is being measured?
;;
;; The casual question “How many words do you know?” hides several choices.
;; Does *ran* count separately from *run*? Does one meaning of *bank* count
;; separately from another? Which language inventory supplies the denominator?
;; This first pass cannot answer every version of that question. It answers one
;; narrower, reproducible version.
;;
;; The estimand here is:
;;
;; > **Receptive knowledge of lemma–surface-form pairs in a fixed, versioned
;; > pool.**
;;
;; A lemma such as *run* may have several surface forms. The NKJP and SUBTLEX
;; frequency data used to rank a pair does not distinguish senses. The item
;; curation process mitigates this by rejecting ambiguous or overly
;; context-dependent pairs and placing each remaining surface form in a short,
;; commonly heard sentence showing a normal use. This makes unusual senses less
;; likely to add difficulty, but it is a design safeguard, not evidence that
;; sense has no effect. The context and translation select an intended meaning
;; for the canonical item, but they remain **measurement metadata**; this model
;; does not pretend it has estimated sense-specific knowledge.

^:kindly/hide-code
(math/terminology
 "language-terminology"
 :lexical
 "Language terms"
 "Lexical terms used in this model"
 [["Receptive knowledge" "Recognising and understanding a form when it is encountered, rather than being able to produce it unaided."]
  ["Estimand" "The precisely defined quantity a measurement procedure aims to estimate. Here it is a count of known lemma–surface-form pairs in one fixed pool."]
  ["Lemma" "A dictionary headword used to group related inflected forms. For example, the English lemma run groups forms such as run, runs, ran, and running."]
  ["Surface form" "The exact written form that appears in text or in a question, such as ran or running."]
  ["Lemma–surface-form pair" "One lemma ID linked to one surface-form ID—the countable unit in this model. For example: lemma run + surface form ran."]
  ["Sense" "One distinct meaning of a lemma, such as run meaning “move quickly” versus a machine that “runs.” The frequency table has no sense IDs, so the model cannot estimate sense-specific knowledge."]
  ["Canonical item" "One fixed, versioned quiz question for a pair. Its context selects the intended meaning for measurement, but does not create a sense-specific frequency count."]])

^:kindly/hide-code
(kind/hiccup
 [:figure.ve-figure
  [:div.ve-hierarchy
   {:role "img"
    :aria-label "The lemma run links to four separately counted lemma–surface-form pairs: run, runs, ran, and running. NKJP and SUBTLEX pair frequencies have no sense identifier; item curation favours common, everyday usage to reduce sense-related difficulty."}
   [:div.ve-hierarchy-node [:strong "Lemma"] "run"]
   [:div.ve-hierarchy-arrow {:aria-hidden "true"} "→"]
   [:div.ve-form-list
    [:div.ve-hierarchy-node [:strong "Pair 1"] "run + run"]
    [:div.ve-hierarchy-node [:strong "Pair 2"] "run + runs"]
    [:div.ve-hierarchy-node [:strong "Pair 3"] "run + ran"]
    [:div.ve-hierarchy-node [:strong "Pair 4"] "run + running"]
    [:div.ve-sense-gap
     [:strong "Missing layer: senses"]
     [:br]
     "NKJP and SUBTLEX pair frequencies have no sense ID. Item curation favours common, everyday usage to reduce sense-related difficulty, but the pair count is not sense-specific."]]]
  [:figcaption.ve-caption
   "The estimand counts the four lemma–form pairs separately. It does not claim four independent lemmas or sense-specific frequencies."]])

;; This target **can** support a qualified statement such as “estimated known
;; pairs in pool P, version V, under algorithm A.” It **cannot** by itself report
;; a universal count of “words known,” productive vocabulary, sense-specific
;; knowledge, or overall language proficiency. The narrowing is useful because
;; it supplies a denominator, a pool version, and repeatable items.

^:kindly/hide-code
(kind/hiccup
 [:div.ve-callout
  [:strong "Verified mathematics"]
  "Once the outcome, prior, and likelihood are fixed, the Beta posterior and posterior-predictive calculation below follow exactly."])

^:kindly/hide-code
(kind/hiccup
 [:div.ve-callout.provisional
  [:strong "Provisional modelling choice"]
  "Provisional means chosen as a testable starting assumption, not established by the data. The pair inventory, canonical contexts, frequency strata, response mapping, prior, and stopping threshold all need empirical validation."])

;; ## 2. Sample: balanced rounds from a fixed pool
;;
;; For exposition, imagine a fixed, versioned pool split by frequency rank into eight
;; strata with the same number of pairs. A round samples one item uniformly at
;; random from each stratum. Later answers do not change which item is selected:
;; **selection remains non-adaptive**.
;;
;; The full pool is the **population** about which this test makes its claim. The
;; much smaller set of administered items is the **sample**. Sampling is needed
;; because asking all 8,000 questions would defeat the purpose of an estimate.

^:kindly/hide-code
(math/terminology
 "test-design-terminology"
 :design
 "Test-design terms"
 "Terms used in the selection schedule"
 [["Pool" "The complete fixed, versioned set of lemma–surface-form pairs that this test is allowed to estimate."]
  ["Population" "Every pair in the fixed pool about which this particular estimate makes a claim."]
  ["Sample" "The smaller set of pool items actually administered to the learner."]
  ["Pair" "Shorthand here for one lemma–surface-form pair—not two arbitrary words and not a word sense."]
  ["Frequency rank" "The position of a pair after ordering the source corpus data from most frequent to least frequent. Rank 1 is most frequent; rank is a proxy, not calibrated learner difficulty."]
  ["Stratum" "One of eight equal-count bands formed from adjacent frequency ranks." "strata"]
  ["Item" "A versioned quiz question for one pair, including its context, intended answer, and distractors."]
  ["Non-adaptive" "Earlier answers update the estimate but do not change which later items are selected."]
  ["Adaptive" "Later items are selected using earlier answers or the learner’s current estimated knowledge."]])
;;
;; An adaptive test does change later item selection in response to earlier
;; answers. In principle, it can choose the next question expected to be most
;; informative about the learner's current knowledge, avoiding many questions
;; that look much too easy or much too hard. That can shorten a test, but only if
;; the item-difficulty model is trustworthy.
;;
;; I therefore want to keep selection non-adaptive while collecting response
;; data from real learners. Those data should reveal the relative difficulty of
;; the actual questions—including context effects and departures from frequency
;; rank—before I implement an adaptive test. Adapting sooner would risk steering
;; the test with assumptions that have not yet been checked against learners.

^:kindly/hide-code
(kind/mermaid
 "flowchart LR
    P[Fixed versioned pool] --> S1[Stratum 1<br/>most frequent]
    P --> S2[Stratum 2]
    P --> S3[Stratum 3]
    P --> S4[Stratum 4]
    P --> S5[Stratum 5]
    P --> S6[Stratum 6]
    P --> S7[Stratum 7]
    P --> S8[Stratum 8<br/>least frequent]
    S1 --> R[One random item from each<br/>8-item round]
    S2 --> R
    S3 --> R
    S4 --> R
    S5 --> R
    S6 --> R
    S7 --> R
    S8 --> R")

^:kindly/hide-code
(kind/hiccup
 [:p.ve-caption
  "Frequency rank partitions the illustrative pool; this diagram does not claim that rank is a calibrated item-difficulty scale."])

;; **Frequency rank** is only a proxy: a convenient observable used in place of
;; an unobserved quantity. Here it stands in for item difficulty, but it has not
;; been calibrated against learner responses. Equal-count strata balance the
;; sample across that proxy; they do not prove equal difficulty inside a band.
;;
;; ### Watch the fixed schedule
;;
;; Select items one at a time below. Read across the eight cards: each round
;; takes exactly one unseen item from every stratum. The sample responses are
;; shown, but the “next item” labels advance according to the pre-existing
;; queues, never according to those responses.

^:kindly/hide-code
(kind/hiccup
 [:div.ve-simulator
  [:div#balanced-round-simulator
   [:p "Loading the balanced-round demonstration…"]]
  [:noscript "This animation needs JavaScript. The fixed eight-stratum schedule above remains authoritative."]])

^:kindly/hide-code
(kind/hiccup
 [:div.article-recap
  [:span.article-marker "Build / Check / Decide"]
  [:p "Build: version a finite population and queue unseen items in eight frequency bands. Check: every completed round contains one item from each band, with no repeats. Decide: keep selection non-adaptive until a learner-calibrated difficulty model exists."]])

;; ## 3. Record first, collapse only for v1 inference
;;
;; The interface keeps three raw response values:
;; `:correct`, `:wrong`, and `:dont-know`. Stage one maps a correct response to
;; “known” and both other responses to “not known.” Keeping the raw outcomes
;; distinct lets a later model treat guessing, slips, and explicit uncertainty
;; differently without corrupting the original data.

^:kindly/hide-code
(defn collapse-response
  "Map a raw response to the binary stage-one likelihood outcome."
  [response]
  (case response
    :correct 1
    :wrong 0
    :dont-know 0
    (throw (ex-info "Unknown raw response"
                    {:response response
                     :allowed #{:correct :wrong :dont-know}}))))

^:kindly/hide-code
(defn collapse-responses
  "Return the sufficient statistics {:k correct :n attempted}."
  [responses]
  {:k (reduce + (map collapse-response responses))
   :n (count responses)})

;; The mapping keeps `:wrong` and `:dont-know` as different raw keys even though
;; their stage-one likelihood contribution is the same.
;;
;; A **Bernoulli trial** has two outcomes for this model: 1 for correct and 0
;; for not correct. That binary value is derived for inference; it does not
;; overwrite the original event. This separation is what permits a later model
;; to distinguish a wrong answer from an explicit “don't know.”

^:kindly/hide-code
(math/code-detail
 "code-raw-response-mapping"
 "Mapping raw responses without discarding the events"
 [:div
  [:p "The pure function returns the v1 binary outcome. Unknown values fail loudly instead of silently entering the model."]
  [:pre [:code "(defn collapse-response [response]\n  (case response\n    :correct 1\n    :wrong 0\n    :dont-know 0\n    (throw (ex-info \"Unknown raw response\"\n                    {:response response}))))"]]
  [:p "Storage retains the original keyword; only the input to this first inference model is collapsed."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/beta_binomial_first_pass.clj"} "View the executable article source"]]])

;; ## 4. Update: Beta in, Beta out within one stratum
;;
;; A sequence of Bernoulli trials can be summarized by a **binomial count**:
;; $k$ correct responses among $n$ attempts. A **Beta distribution** describes
;; uncertainty about a probability between 0 and 1. It has two positive shape
;; parameters, $\alpha$ and $\beta$. Choosing a Beta prior makes the update
;; **conjugate**: after binomial data, the posterior is another Beta
;; distribution, so no numerical approximation is needed for this step.
;;
;; Let $p_s$ be the knowing rate in stratum $s$. This first pass gives every
;; stratum the same prior:
;;
;; $$p_s \sim \operatorname{Beta}(1,1).$$

^:kindly/hide-code
(math/explanation
 "math-beta-prior"
 "The prior knowing-rate distribution for one stratum"
 [["p_s" "The learner’s unknown knowing rate in stratum s: the proportion of that stratum’s fixed pairs the learner knows."]
  ["s" "The stratum index. This first model has eight separate frequency-rank strata."]
  ["∼" "“Is distributed as.” It describes uncertainty about p_s, not an equality to one number."]
  ["Beta(1,1)" "A Beta distribution with shape parameters α = 1 and β = 1. Its density is uniform from 0 to 1."]
  ["α, β" "The Beta distribution’s two positive shape parameters. In this Bernoulli model they update like prior correct and not-correct counts."]]
 "Beta(1,1) is the deliberately simple v1 prior; calling it uniform does not make it universally uninformative.")
;;
;; That density is uniform over rates from zero to one. I am **not** claiming
;; that it is universally “uninformative”; parameterization and context matter.
;; If $k$ of $n$ sampled pairs are correct, the Bernoulli/binomial likelihood is
;; proportional to $p_s^k(1-p_s)^{n-k}$, so:
;;
;; $$p_s \mid k,n \sim \operatorname{Beta}(1+k,1+n-k).$$
;;
;; Read this as: “start with one prior count on each side; add the $k$
;; correct responses to $\alpha$ and the $n-k$ not-correct responses to
;; $\beta$.” For example, three correct among four attempts changes
;; `Beta(1,1)` into `Beta(4,2)`.

^:kindly/hide-code
(math/explanation
 "math-beta-posterior"
 "The updated knowing-rate distribution"
 [["p_s | k,n" "The knowing rate in stratum s after conditioning on the observed response counts k and n."]
  ["|" "“Given” or “conditional on.” Everything to its right is treated as observed information."]
  ["k" "The number of correct responses in this stratum."]
  ["n" "The total number of tested pairs in this stratum."]
  ["n − k" "The number of tested responses treated as not known by the v1 inference model."]
  ["1 + k" "The posterior α parameter: prior α = 1 plus correct responses."]
  ["1 + n − k" "The posterior β parameter: prior β = 1 plus not-correct responses."]
  ["Beta(…)" "The posterior stays in the Beta family because the Beta prior is conjugate to the Bernoulli/binomial likelihood."]]
 "The complete response history reduces to k and n for this update, although the original correct, wrong, and don’t-know events remain stored separately.")

^:kindly/hide-code
(defn posterior-parameters
  "Beta posterior parameters for a Beta(alpha,beta) prior and k of n correct."
  ([k n] (posterior-parameters 1.0 1.0 k n))
  ([alpha beta k n]
   {:pre [(<= 0 k n) (pos? alpha) (pos? beta)]}
   {:alpha (+ alpha k)
    :beta (+ beta (- n k))}))

^:kindly/hide-code
(math/code-detail
 "code-beta-posterior-parameters"
 "Updating the two Beta shape parameters"
 [:div
  [:p "This function is pure: the same prior and counts always return the same posterior parameters, and no external state changes."]
  [:pre [:code "(defn posterior-parameters\n  ([k n] (posterior-parameters 1.0 1.0 k n))\n  ([alpha beta k n]\n   {:pre [(<= 0 k n) (pos? alpha) (pos? beta)]}\n   {:alpha (+ alpha k)\n    :beta (+ beta (- n k))}))"]]
  [:p "The precondition rejects impossible counts and non-positive Beta parameters before they can contaminate a result."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/beta_binomial_first_pass.clj"} "View the complete posterior update"]]])

;; `{:alpha 4.0, :beta 2.0}` is the entire update for three correct among four.
;; “Posterior” names the
;; updated density for $p_s$. The “likelihood” is the information supplied by
;; the last response as a function of $p_s$; it is not itself a posterior.
;;
;; ### Try the update
;;
;; Press **Correct**, **Wrong**, or **Don't know**. The accessible SVG compares the uniform
;; prior, the previous posterior, the last-response likelihood, and the current
;; posterior. Curve height is **density**, not the probability of one exact
;; value of $p$.

^:kindly/hide-code
(kind/hiccup
 [:div.ve-reading-guide
  [:h3 "How to read the chart"]
  [:ul
   [:li "Horizontal position is a candidate knowing rate from 0 to 1."]
   [:li "Curve height is probability density: where the distribution concentrates, not the probability of one exact decimal."]
   [:li "The solid current curve is the result after the latest response; the dashed curves show what was multiplied to obtain it."]]])

^:kindly/hide-code
(kind/hiccup
 [:div.ve-simulator
  [:div#beta-binomial-simulator
   [:p "Loading the Bayesian update simulator…"]]
  [:noscript "This simulator needs JavaScript. The statistical core above remains readable without it."]])

;; ## 5. Predict: from knowing rates to an untested finite pool
;;
;; For a stratum containing $N_s$ pairs, of which $n_s$ were tested and $k_s$
;; were correct:
;;
;; 1. draw $p_s$ from the posterior;
;; 2. draw the number known among the untested pairs from
;;    $\operatorname{Binomial}(N_s-n_s,p_s)$;
;; 3. add the $k_s$ observed correct pairs;
;; 4. sum the eight stratum totals.
;;
;; $$U_s \mid p_s \sim \operatorname{Binomial}(N_s-n_s,p_s),
;; \qquad T=\sum_{s=1}^{8}(k_s+U_s).$$
;;
;; Read this as: “predict $U_s$ known pairs only among the $N_s-n_s$ untested
;; pairs; add the already observed correct pairs $k_s$ once; then sum all eight
;; strata.” Tested-not-correct pairs contribute zero and are never predicted
;; again. This avoids both double counting and pretending an observed response
;; is still unknown.

^:kindly/hide-code
(math/explanation
 "math-finite-pool-prediction"
 "Posterior prediction for the remaining finite pool"
 [["U_s" "The predicted number known among the untested pairs in stratum s."]
  ["N_s" "The fixed number of pairs in stratum s."]
  ["n_s" "The number of pairs already tested in stratum s."]
  ["p_s" "One knowing rate drawn from stratum s's Beta posterior."]
  ["k_s" "The observed correct count, added directly rather than predicted again."]
  ["T" "One complete posterior-predictive draw of total known pairs across all eight strata."]
  ["Σ" "Sum the parenthesized stratum total for s = 1 through 8."]]
 "The binomial draw concerns exactly the untested finite remainder, not a new infinite population and not the tested items.")
;;
;; The following functions are the executable version. Fastmath supplies the
;; Beta and binomial distributions; a seeded Mersenne Twister makes the rendered
;; result reproducible.

^:kindly/hide-code
(defn posterior-predictive-stratum
  "Draw known pairs in one stratum, including observed correct pairs."
  [rng {:keys [pool-size k n]}]
  (let [{:keys [alpha beta]} (posterior-parameters k n)
        posterior (random/distribution :beta
                                       {:alpha alpha :beta beta :rng rng})
        p (random/sample posterior)
        untested (- pool-size n)
        predicted (random/distribution :binomial
                                       {:trials untested :p p :rng rng})]
    (+ k (random/sample predicted))))

^:kindly/hide-code
(defn posterior-predictive-total
  "Draw and sum known-pair counts across strata."
  ([strata] (posterior-predictive-total 20260712 strata))
  ([seed strata]
   (let [rng (random/rng :mersenne seed)]
     (reduce + (mapv #(posterior-predictive-stratum rng %) strata)))))

^:kindly/hide-code
(math/code-detail
 "code-finite-pool-draw"
 "Making one finite-pool posterior-predictive draw"
 [:div
  [:p "The draw keeps the three groups separate: observed correct, observed not-correct, and untested. Only the untested group is simulated."]
  [:pre [:code "(defn posterior-predictive-stratum\n  [rng {:keys [pool-size k n]}]\n  (let [{:keys [alpha beta]} (posterior-parameters k n)\n        p (random/sample\n           (random/distribution :beta\n             {:alpha alpha :beta beta :rng rng}))\n        untested (- pool-size n)\n        predicted (random/sample\n                   (random/distribution :binomial\n                     {:trials untested :p p :rng rng}))]\n    (+ k predicted)))"]]
  [:p "A random seed initializes the pseudo-random generator. Supplying the same fixture and seed reproduces the same draw sequence."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/beta_binomial_first_pass.clj"} "View the complete seeded prediction functions"]]])

^:kindly/hide-code
(defn quantile
  "Nearest-rank-style quantile from a finite numeric sample."
  [xs probability]
  {:pre [(seq xs) (<= 0.0 probability 1.0)]}
  (let [ordered (vec (sort xs))
        i (long (Math/floor (* probability (dec (count ordered)))))]
    (nth ordered i)))

^:kindly/hide-code
(defn equal-tail-quantiles
  "Return the central equal-tail interval from a finite sample."
  ([xs] (equal-tail-quantiles xs 0.95))
  ([xs mass]
   (let [tail (/ (- 1.0 mass) 2.0)]
     {:lower (quantile xs tail)
      :upper (quantile xs (- 1.0 tail))})))

^:kindly/hide-code
(math/code-detail
 "code-quantile-selection"
 "Selecting equal-tail interval endpoints"
 [:div
  [:p "Sort the finite totals, convert a probability to an index, and select that ordered value. A 95% equal-tail interval uses probabilities 0.025 and 0.975."]
  [:pre [:code "(defn quantile [xs probability]\n  (let [ordered (vec (sort xs))\n        i (long (Math/floor\n                 (* probability (dec (count ordered)))))]\n    (nth ordered i)))\n\n(equal-tail-quantiles draws 0.95)"]]
  [:p "This function selects quantiles from Monte Carlo draws. The exact reference interval below instead selects where the cumulative exact discrete probability crosses the same two cut points."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/beta_binomial_first_pass.clj"} "View the quantile implementation"]]])

;; ## 6. Combine: a pedagogical 8,000-pair pool
;;
;; **8,000 is an illustration, not a current CEFR or Lexibench pool size.** It
;; consists of eight 1,000-pair strata. After four complete rounds, suppose the
;; correct counts are `[4 4 3 3 2 1 1 0]`.

^:kindly/hide-code
(def worked-correct-counts [4 4 3 3 2 1 1 0])

^:kindly/hide-code
(def worked-strata
  (mapv (fn [k] {:pool-size 1000 :k k :n 4})
        worked-correct-counts))

^:kindly/hide-code
(defn analytic-stratum-mean
  "Posterior-predictive mean for one stratum."
  [{:keys [pool-size k n]}]
  (let [{:keys [alpha beta]} (posterior-parameters k n)]
    (+ k (* (- pool-size n) (/ alpha (+ alpha beta))))))

^:kindly/hide-code
(math/code-detail
 "code-analytic-stratum-mean"
 "Calculating a stratum's posterior-predictive mean"
 [:div
  [:p "The Beta posterior mean is alpha divided by alpha plus beta. Multiply it by the untested count, then add observed correct pairs exactly once."]
  [:pre [:code "(defn analytic-stratum-mean\n  [{:keys [pool-size k n]}]\n  (let [{:keys [alpha beta]}\n        (posterior-parameters k n)]\n    (+ k\n       (* (- pool-size n)\n          (/ alpha (+ alpha beta))))))"]]
  [:p "This is analytic: it follows directly from the distribution's expectation and has no simulation noise."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/beta_binomial_first_pass.clj"} "View the worked calculation"]]])

^:kindly/hide-code
(def worked-analytic-mean
  (long (reduce + (map analytic-stratum-mean worked-strata))))

;; The **mean** is the probability-weighted average of all possible totals under
;; the model. It is **4,334 pairs** here. Row by row:

^:kindly/hide-code
(kind/hiccup
 [:div.ve-table-wrap
  [:table.ve-table
   [:caption.ve-sr-only "Worked example by frequency-rank stratum"]
   [:thead
    [:tr [:th {:scope "col"} "Stratum"]
     [:th {:scope "col"} "Pool"]
     [:th {:scope "col"} "Correct / tested"]
     [:th {:scope "col"} "Posterior"]
     [:th {:scope "col"} "Mean known"]]]
   [:tbody
    (for [[i row] (map-indexed vector worked-strata)]
      [:tr {:key i}
       [:th {:scope "row"} (inc i)]
       [:td (:pool-size row)]
       [:td (str (:k row) " / " (:n row))]
       [:td (let [{:keys [alpha beta]}
                  (posterior-parameters (:k row) (:n row))]
              (str "Beta(" (long alpha) ", " (long beta) ")"))]
       [:td (format "%,.0f" (analytic-stratum-mean row))]])
    [:tr
     [:th {:scope "row"} "Total"]
     [:td "8,000"]
     [:td "18 / 32"]
     [:td "—"]
     [:td (format "%,d" worked-analytic-mean)]]]]])

;; A point mean hides **uncertainty**: the model still supports a range of
;; totals. Here an exact calculation is possible because every stratum has a
;; finite number of outcomes. For each possible untested count $x$, the
;; Beta–binomial probability is
;;
;; $$P(U_s=x\mid k_s,n_s)=
;; {m_s \choose x}
;; \frac{B(x+\alpha_s,m_s-x+\beta_s)}{B(\alpha_s,\beta_s)},
;; \qquad m_s=N_s-n_s.$$
;;
;; Read this as: “assign an exact probability to every possible known count
;; among the untested pairs, using the updated Beta uncertainty.” Convolving—the
;; discrete equivalent of adding—all eight probability lists produces an exact
;; distribution for the total. The **95% equal-tail credible interval** removes
;; 2.5% probability from each tail. Its endpoints are **3,404–5,249**.

^:kindly/hide-code
(math/explanation
 "math-beta-binomial-pmf"
 "Exact probability of one untested stratum count"
 [["P(U_s = x | k_s,n_s)" "The posterior-predictive probability that exactly x untested pairs are known in stratum s."]
  ["m_s" "The untested remainder N_s − n_s; 996 in every worked-example stratum."]
  ["choose(m_s,x)" "The number of ways x known outcomes can occur among m_s untested pairs."]
  ["B(·,·)" "The Beta function; the ratio integrates over uncertainty in p_s rather than plugging in one rate."]
  ["α_s, β_s" "The two posterior Beta parameters for stratum s."]]
 "Calculating all x values and adding the eight independent stratum distributions yields the exact finite-pool total distribution.")
;;
;; Reader-facing, I would report:
;;
;; > **About 4,330 known pairs, with a 95% credible interval of roughly
;; > 3,400–5,250, conditional on this model and fixed pool.**
;;
;; “95% credible interval” is not “95% certainty that every modelling choice is
;; right.” It describes posterior uncertainty conditional on the specified
;; model, prior, observed responses, and pool.

^:kindly/hide-code
(kind/hiccup
 [:figure.ve-figure
  [:svg {:viewBox "-24 -24 848 198"
         :role "img"
         :aria-labelledby "worked-ci-title worked-ci-desc"}
   [:title#worked-ci-title "Worked-example posterior mean and 95 percent credible interval"]
   [:desc#worked-ci-desc "An interval from 3,404 to 5,249 known pairs, with posterior mean 4,334, on a scale from zero to 8,000."]
   [:line {:x1 55 :x2 745 :y1 80 :y2 80 :stroke "#6c757d" :stroke-width 2}]
   (for [x [0 2000 4000 6000 8000]]
     (let [px (+ 55 (* 690 (/ x 8000.0)))]
       [:g {:key x}
        [:line {:x1 px :x2 px :y1 74 :y2 86 :stroke "#6c757d"}]
        [:text {:x px :y 110 :text-anchor "middle" :font-size 15 :fill "currentColor"}
         (format "%,d" x)]]))
   [:line {:x1 (+ 55 (* 690 (/ 3404 8000.0)))
           :x2 (+ 55 (* 690 (/ 5249 8000.0)))
           :y1 55 :y2 55 :stroke "#2780e3" :stroke-width 12
           :stroke-linecap "round"}]
   [:circle {:cx (+ 55 (* 690 (/ 4334 8000.0))) :cy 55 :r 9
             :fill "#c44536" :stroke "white" :stroke-width 2}]
   [:text {:x 400 :y 25 :text-anchor "middle" :font-size 16 :font-weight 600 :fill "currentColor"}
    "3,404 — 4,334 — 5,249"]]
  [:figcaption.ve-caption
   "Blue: central 95% credible interval. Red: posterior-predictive mean. Scale: pairs known in the fixed 8,000-pair pool."]])

;; ### Watch complete posterior-predictive draws accumulate
;;
;; One **complete draw** below passes through all eight strata. In each stratum
;; it draws a knowing rate from that stratum's Beta posterior, predicts the
;; known count among its 996 untested pairs, adds the observed correct pairs,
;; and finally sums the eight counts.
;;
;; The first view exposes the newest complete draw rather than showing only its
;; total. The second retains every complete draw as one dot. Its live mean and
;; interval therefore use exactly the dots you can see—none are hidden in an
;; aggregate. Choose 10, 20, or 50 draws per second; changing speed does not
;; change the seeded sequence.

^:kindly/hide-code
(kind/hiccup
 [:div.ve-reading-guide
  [:h3 "How to read the posterior-predictive simulator"]
  [:ul
   [:li "In the newest draw, each row separates tested correct, tested not-correct, and predicted untested pairs."]
   [:li "The tested groups are fixed by observed events. Only the untested count is drawn."]
   [:li "In the lower chart, one dot is one complete eight-stratum total; the line and marker summarize only the visible dots."]]])

^:kindly/hide-code
(kind/hiccup
 [:style
  ".ve-posterior-sampler{border:1px solid var(--bs-border-color,#ced4da);border-radius:.65rem;padding:clamp(.8rem,3vw,1.3rem);min-width:0;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.ve-sampling-intro{max-width:50rem}.ve-sampling-controls{display:flex;align-items:end;justify-content:space-between;gap:1rem;flex-wrap:wrap;margin:1rem 0}.ve-rate-fieldset{border:0;padding:0;margin:0;min-width:0}.ve-rate-fieldset legend{font-size:.85rem;font-weight:700;margin-bottom:.4rem}.ve-button-row{display:flex;gap:.5rem;flex-wrap:wrap}.ve-sampling-button{border:1px solid var(--bs-border-color,#6c757d);border-radius:.35rem;padding:.55rem .85rem;font-weight:600;cursor:pointer;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.ve-sampling-button[aria-pressed=true],.ve-sampling-button.ve-primary{border-color:#1464b5;background:#1464b5;color:#fff}.quarto-dark .ve-sampling-button[aria-pressed=true],.quarto-dark .ve-sampling-button.ve-primary{border-color:#73b7ff;background:#73b7ff;color:#10212b}.ve-sampling-button:focus-visible{outline:3px solid color-mix(in srgb,var(--ve-accent) 45%,transparent);outline-offset:2px}.ve-sampling-progress{width:100%;height:.55rem;accent-color:var(--ve-accent)}.ve-sampling-grid{display:grid;grid-template-columns:minmax(0,1fr);gap:1rem;margin-top:1rem}.ve-sample-panel{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.5rem;padding:clamp(.65rem,2vw,1rem);margin:0;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}.ve-sample-panel h4{font-size:1rem;margin:0 0 .25rem}.ve-sample-panel svg{display:block;width:100%;height:auto}.ve-sample-stat{font-variant-numeric:tabular-nums;margin:.2rem 0 .65rem}.ve-sample-note{font-size:.85rem;color:var(--ve-muted);margin:.5rem 0 0}.ve-empty-sample{display:grid;place-items:center;min-height:13rem;border:1px dashed var(--bs-border-color,#adb5bd);border-radius:.35rem;color:var(--ve-muted);text-align:center;padding:1rem}.ve-sampling-status{font-variant-numeric:tabular-nums;margin:.4rem 0}.ve-dot{fill:var(--ve-accent);fill-opacity:.72}.ve-dot-latest{fill:var(--ve-warm);stroke:var(--bs-body-bg,#fff);stroke-width:1.5}.ve-sample-axis{stroke:currentColor;stroke-opacity:.45}.ve-sample-guide{stroke:currentColor;stroke-opacity:.1}.ve-latest-line{stroke:var(--ve-accent);stroke-width:5;stroke-linecap:round}.ve-latest-dot{fill:var(--ve-warm);stroke:var(--bs-body-bg,#fff);stroke-width:2}.ve-draw-breakdown{width:100%;border-collapse:collapse;font-size:.82rem;font-variant-numeric:tabular-nums;margin-top:.75rem}.ve-draw-breakdown th,.ve-draw-breakdown td{padding:.35rem .45rem;border-bottom:1px solid var(--bs-border-color,#dee2e6);text-align:right;white-space:nowrap}.ve-draw-breakdown th:first-child{text-align:left}.ve-draw-breakdown-wrap{max-width:100%;overflow-x:auto}@media(max-width:575px){.ve-sampling-controls{align-items:stretch}.ve-sampling-controls>.ve-button-row{width:100%}.ve-sampling-controls>.ve-button-row .ve-sampling-button{flex:1}.ve-sampling-button{padding:.55rem .7rem}}"])

^:kindly/hide-code
(kind/hiccup
 [:div.ve-simulator
  [:div#posterior-sampling-simulator
   [:p "Loading the posterior-predictive sampling simulator…"]]
  [:noscript "This simulator needs JavaScript. The seeded numerical check below remains available without it."]
  [:script {:type "application/x-scittle"
            :src "beta_binomial_first_pass_interactive.cljs"}]])

;; The 500 browser draws are for seeing the mechanism, not for replacing the
;; larger verification run. Early live intervals are especially noisy.

;; ### A seeded numerical check
;;
;; The Monte Carlo calculation below is an independent numerical check on the
;; analytic mean and exact reference interval, not the source of those values.

^:kindly/hide-code
(def simulation-draws 100000)

^:kindly/hide-code
(def worked-simulation
  (let [rng (random/rng :mersenne 20260712)]
    (vec
     (repeatedly simulation-draws
                 #(reduce +
                          (mapv (fn [stratum]
                                  (posterior-predictive-stratum rng stratum))
                                worked-strata))))))

^:kindly/hide-code
(def worked-simulation-summary
  {:draws simulation-draws
   :mean (double (/ (reduce + worked-simulation) simulation-draws))
   :equal-tail-95 (equal-tail-quantiles worked-simulation)})

;; Small differences are Monte Carlo error. The verification tolerance used for
;; this draft is ±20 pairs for the mean and ±40 pairs for each endpoint.
;;
;; ## 7. Decide: when should the test stop?
;;
;; The stopping rule is also provisional:
;;
;; - ask at least 32 items (four complete rounds);
;; - reassess only after another complete eight-item round;
;; - target a 95% interval half-width near 10% of the pool;
;; - use 96 items as a soft maximum;
;; - always allow the learner to stop voluntarily.
;;
;; The **minimum length** prevents an unstable early estimate from triggering a
;; precision rule. **Round boundaries** preserve the same number of sampled
;; items per stratum. The **precision target** asks whether the interval's
;; half-width is at most 10% of the pool. The **soft maximum** recommends
;; stopping at 96 without making continued participation impossible. A
;; **voluntary stop** overrides all statistical criteria because the learner
;; remains in control.

^:kindly/hide-code
(defn stopping-check
  "Evaluate the provisional rule at an eight-item round boundary."
  [{:keys [items-tested interval pool-size voluntary?]
    :or {voluntary? false}}]
  (let [[lower upper] interval
        complete-round? (zero? (mod items-tested 8))
        assess? (and complete-round? (>= items-tested 32))
        half-width (/ (- upper lower) 2.0)
        target? (and assess? (<= half-width (* 0.10 pool-size)))
        soft-max? (and assess? (>= items-tested 96))]
    {:complete-round? complete-round?
     :assess? assess?
     :half-width half-width
     :target? target?
     :soft-max? soft-max?
     :stop? (or voluntary? target? soft-max?)}))

^:kindly/hide-code
(math/code-detail
 "code-stopping-decision"
 "Applying the v1 stopping decision"
 [:div
  [:p "Assessment occurs only at complete eight-item rounds and only after the 32-item minimum. The precision target and soft maximum are separate reasons to recommend stopping."]
  [:pre [:code "(let [complete-round? (zero? (mod items-tested 8))\n      assess? (and complete-round?\n                   (>= items-tested 32))\n      half-width (/ (- upper lower) 2.0)\n      target? (and assess?\n                   (<= half-width (* 0.10 pool-size)))\n      soft-max? (and assess?\n                     (>= items-tested 96))]\n  {:stop? (or voluntary? target? soft-max?)})"]]
  [:p "The complete function also returns each intermediate condition, making the final recommendation explainable and testable."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/beta_binomial_first_pass.clj"} "View the stopping predicate and examples"]]])

^:kindly/hide-code
(def stopping-examples
  [{:items-tested 24 :interval [3000 5200] :pool-size 8000}
   {:items-tested 32 :interval [3404 5249] :pool-size 8000}
   {:items-tested 36 :interval [3500 5100] :pool-size 8000}
   {:items-tested 40 :interval [3550 5050] :pool-size 8000}
   {:items-tested 96 :interval [3500 5300] :pool-size 8000}])

^:kindly/hide-code
(kind/hiccup
 [:div.ve-table-wrap
  [:table.ve-table
   [:caption.ve-sr-only "Examples of the provisional stopping rule"]
   [:thead
    [:tr [:th {:scope "col"} "Items"]
     [:th {:scope "col"} "Complete round?"]
     [:th {:scope "col"} "Assess?"]
     [:th {:scope "col"} "Half-width"]
     [:th {:scope "col"} "Stop?"]]]
   [:tbody
    (for [{:keys [items-tested] :as example} stopping-examples
          :let [{:keys [complete-round? assess? half-width stop?]}
                (stopping-check example)]]
      [:tr {:key items-tested}
       [:th {:scope "row"} items-tested]
       [:td (if complete-round? "Yes" "No")]
       [:td (if assess? "Yes" "No")]
       [:td (format "%,.0f" half-width)]
       [:td (if stop? "Yes" "No")]])]]])

;; At 36 items the interval happens to be narrow enough, but the function does
;; not assess it: 36 is not the end of an eight-item round. At 96, the soft
;; maximum recommends stopping even when the width target is missed.

;; ### Explore the rule without rewriting history
;;
;; The controls below begin at the authoritative v1 defaults: minimum 32,
;; interval half-width target 10% of the pool, and soft cap 96. Changing those
;; three values creates a **counterfactual teaching scenario** only. It does not
;; alter this article's recorded model, examples, assertions, or scorer.

^:kindly/hide-code
(kind/hiccup
 [:div.ve-simulator
  [:div#stopping-rule-explorer
   [:p "Loading the teaching-only stopping-rule explorer…"]]
  [:noscript "This explorer needs JavaScript. The authoritative v1 defaults and stopping table above remain available."]])

^:kindly/hide-code
(kind/hiccup
 [:div.article-recap
  [:span.article-marker "Build / Check / Decide"]
  [:p "Build: compute the exact finite-pool distribution and a qualified estimate. Check: reproduce it with a seeded simulation and inspect stopping only at round boundaries. Decide: stop for voluntary choice, adequate precision after the minimum, or the soft maximum—without changing the rule after seeing one learner's result."]])
;;
;; ## 8. Reproduce: keep the model separate from the machinery
;;
;; A trustworthy calculation needs a boundary between the mathematical model
;; and software that reads files, draws charts, or responds to clicks. A **pure
;; function** returns an output determined only by its inputs. A **side effect**
;; changes or observes something outside that return value, such as a database,
;; clock, random generator, file, or browser state. The scorer's mathematics
;; should remain pure; storage, UI, clocks, and unseeded randomness belong at the
;; boundary. Clojure encourages this separation without pretending all useful
;; programs are side-effect-free.
;;
;; The worked example is also a **fixture**: a small, named input with expected
;; outputs used repeatedly in checks. An **immutable version** is never edited
;; after events refer to it; a changed pool or item receives a new identifier.
;; The original `correct`, `wrong`, and `dont-know` events remain unchanged so a
;; later algorithm can replay them. A **seed** turns pseudo-random prediction
;; into a repeatable function of versioned inputs.

^:kindly/hide-code
(kind/hiccup
 [:dl.ve-definition-grid
  [:div.ve-definition [:dt "Parity test"] [:dd "The same fixture is scored in Clojure on the JVM and ClojureScript in the browser; both implementations must agree on the protected behavior."]]
  [:div.ve-definition [:dt "Build"] [:dd "Transform source and dependencies into artifacts that another tool can execute or publish."]]
  [:div.ve-definition [:dt "Render"] [:dd "Evaluate the executable article and turn its structured source into the final page readers see."]]
  [:div.ve-definition [:dt "CI"] [:dd "Continuous integration: automated builds and tests run on repository changes rather than relying only on one workstation."]]
  [:div.ve-definition [:dt "Release gate"] [:dd "A required check that must pass before publication; here the final gate includes a full-site render."]]])

^:kindly/hide-code
(kind/mermaid
 "flowchart LR
    E[Estimand] --> V[Versioned pool<br/>and raw events]
    V --> P[Pure scorer]
    P --> S[Seeded prediction]
    S --> T[CLJ and CLJS<br/>parity tests]
    T --> R[Rendered article]
    R --> B[Browser checks]
    B --> G[Publication gate]")

^:kindly/hide-code
(kind/hiccup
 [:p.ve-workflow-caption
  "Model/software workflow. Every arrow carries explicit, versioned evidence forward; a later presentation layer does not redefine the estimand or scorer."])

^:kindly/hide-code
(def worked-fixture
  {:fixture-version "beta-binomial-v1-worked-2026-07-12"
   :algorithm-version "beta-binomial-v1"
   :pool-id "pedagogical-8000-v1"
   :seed 20260712
   :strata worked-strata})

^:kindly/hide-code
(defn score-worked-fixture
  "Deterministic scoring shell for the versioned pedagogical fixture."
  [{:keys [seed strata]}]
  {:analytic-mean (long (reduce + (map analytic-stratum-mean strata)))
   :one-seeded-draw (posterior-predictive-total seed strata)})

^:kindly/hide-code
(math/code-detail
 "code-pure-boundary-fixture"
 "Keeping a pure scoring boundary and deterministic fixture"
 [:div
  [:p "All changing inputs are explicit data. The function creates its seeded generator internally, returns a value, and neither reads nor writes application storage."]
  [:pre [:code "(def worked-fixture\n  {:fixture-version \"beta-binomial-v1-worked-2026-07-12\"\n   :algorithm-version \"beta-binomial-v1\"\n   :pool-id \"pedagogical-8000-v1\"\n   :seed 20260712\n   :strata worked-strata})\n\n(defn score-worked-fixture [{:keys [seed strata]}]\n  {:analytic-mean\n   (long (reduce + (map analytic-stratum-mean strata)))\n   :one-seeded-draw\n   (posterior-predictive-total seed strata)})"]]
  [:p "Calling this function twice with the same fixture must return equal maps; the final regression checks enforce that deterministic replay."]
  [:p.article-code-source [:a {:href "https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/language_learning/vocabulary_estimation/beta_binomial_first_pass.clj"} "View the fixture, scorer, and assertions"]]])

;; The article itself is a separate presentation path: Clay evaluates the
;; Clojure source, writes QMD, Quarto renders HTML, and Scittle/Reagent supplies
;; browser interactions. Browser checks cover accessible labels, both colour
;; themes, responsive widths, control behavior, and console errors. **CI** can
;; automate repeatable tests, but a passing software build is not evidence that
;; a provisional measurement assumption is true.
;;
;; ## What this model postpones
;;
;; A small coherent model is a useful place to begin, not a useful place to end.
;; The next steps are:
;;
;; - use continuous pair frequency as a **difficulty proxy** rather than treating
;;   eight bins as calibrated difficulty — that is the next post;
;; - define and version the conversion among CEFR descriptors, lemmas, and pair
;;   inventories;
;; - aggregate pair probabilities hierarchically into latent lemma knowledge;
;; - use all three response outcomes in a guessing/slip model;
;; - investigate multiple contexts, item calibration, item-response theory, and
;;   adaptive selection.
;;
;; In particular, I am not using a naive independent-form formula as a final
;; lemma estimator. Forms of the same lemma are related, contexts vary in
;; informativeness, and a latent-variable model should express that structure.

^:kindly/hide-code
(kind/hiccup
 [:div.article-recap
  [:span.article-marker "Checkpoint"]
  [:p "The v1 model now has a precise estimand, balanced non-adaptive sampling, lossless raw events, eight conjugate Beta updates, finite-pool posterior prediction, an exact qualified interval, seeded numerical checks, and an explicit stopping rule. Every modelling choice remains provisional. The next article tests one refinement—continuous pair frequency—without silently rewriting this checkpoint."]])
;;
;; ## Sources and further reading
;;
;; - Gelman et al., [*Bayesian Data Analysis*](https://sites.stat.columbia.edu/gelman/book/), for posterior and posterior-predictive reasoning.
;; - [Fastmath random-distribution documentation](https://generateme.github.io/fastmath/fastmath.random.html), for the executable Beta, binomial, sampling, and inverse-CDF interfaces used here.
;; - [Apache Commons Math distribution API](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/distribution/package-summary.html), the distribution implementation underneath this Fastmath path.
;; - Council of Europe, [CEFR Companion Volume (2020)](https://rm.coe.int/cefr-companion-volume-with-new-descriptors-2020/16809ea0d4), for the broader language-proficiency framework; it does not define this pair-count estimand.
;; - Brysbaert & New, [SUBTLEX-US frequency norms](https://doi.org/10.3758/BRM.41.4.977), for evidence that contextual word frequency can be useful; this does not make frequency rank a calibrated item-difficulty scale.
;; - [Clojure's functional-programming overview](https://clojure.org/about/functional_programming), for immutable data and the functional-core/side-effect boundary described here.
;; - [Clay documentation](https://scicloj.github.io/clay/), [Quarto HTML documentation](https://quarto.org/docs/output-formats/html-basics.html), and the [Scittle repository](https://github.com/babashka/scittle), for the article build and browser runtime.
;; - [GitHub Actions documentation](https://docs.github.com/en/actions/get-started/understand-github-actions), for the CI terminology.
;;
;; This is a learning-in-public checkpoint. Follow-up posts will replace the
;; roughest assumptions one at a time, beginning with continuous frequency.

^:kindly/hide-code
(def regression-status
  (do
    (assert (= {:alpha 5.0 :beta 1.0} (posterior-parameters 4 4)))
    (assert (= 0 (collapse-response :wrong)
               (collapse-response :dont-know)))
    (assert (not= :wrong :dont-know))
    (assert (= 4334 worked-analytic-mean))
    (assert (every? #(not (:assess? (stopping-check
                                     {:items-tested %
                                      :interval [0 8000]
                                      :pool-size 8000})))
                    [33 34 35 36 37 38 39]))
    (assert (< (Math/abs (- (:mean worked-simulation-summary) 4334.0)) 20.0))
    (let [{:keys [lower upper]} (:equal-tail-95 worked-simulation-summary)]
      (assert (< (Math/abs (- lower 3404)) 40))
      (assert (< (Math/abs (- upper 5249)) 40)))
    (assert (= (score-worked-fixture worked-fixture)
               (score-worked-fixture worked-fixture)))
    :verified))

^:kindly/hide-code
(kind/hiccup
 [:p.article-recap
  [:span.article-marker "Regression check"]
  " All executable assertions passed, including the preserved 4,334 mean, Monte Carlo tolerance around the exact 3,404–5,249 interval, round-boundary behavior, and deterministic fixture replay."])
