^{:kindly/hide-code true
  :kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Estimating Vocabulary Size with a Simple Bayesian Model"
         :quarto {:author :jamiep
                  :description "A learning-in-public first pass at estimating receptive vocabulary from stratified responses with a Beta–binomial model."
                  :type :post
                  :date "2026-07-12"
                  :category :concepts
                  :tags [:bayesian-statistics :language-learning :clojure :scittle]
                  :keywords [:vocabulary-estimation :beta-binomial :posterior-predictive :stratified-sampling]}}}

(ns language-learning.vocabulary-estimation.beta-binomial-first-pass
  (:require [fastmath.random :as random]
            [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(kind/hiccup
 [:style
  "#title-block-header{padding-top:.75rem}#title-block-header h1{line-height:1.15;overflow-wrap:anywhere}.ve-callout{border-left:4px solid #2780e3;background:#f2f7fc;color:#17202a;padding:1rem 1.15rem;margin:1.4rem 0;border-radius:.25rem}.ve-callout.provisional{border-color:#e69f00;background:#fff8e6}.ve-callout strong{display:block;margin-bottom:.3rem}.ve-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,15rem),1fr));gap:1rem;margin:1.25rem 0}.ve-card{min-width:0;border:1px solid #dee2e6;border-radius:.5rem;padding:1rem;background:var(--bs-body-bg,#fff)}.ve-card h3{font-size:1rem;margin-top:0}.ve-table-wrap{overflow-x:auto;margin:1.25rem 0}.ve-table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}.ve-table th,.ve-table td{padding:.55rem .7rem;border-bottom:1px solid #dee2e6;text-align:right;white-space:nowrap}.ve-table th:first-child,.ve-table td:first-child{text-align:left}.ve-table thead th{border-bottom:2px solid #adb5bd}.ve-figure{margin:1.5rem 0;padding:1rem;border:1px solid #dee2e6;border-radius:.5rem}.ve-figure svg{display:block;width:100%;height:auto}.ve-caption{font-size:.9rem;color:var(--bs-secondary-color,#5c636a);margin:.75rem 0 0}.ve-simulator{margin:1.5rem 0}.ve-sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}@media(max-width:575px){.ve-table th,.ve-table td{padding:.45rem}.ve-figure{padding:.65rem}}"])

;; I am building [**Lexibench**](https://lexibench.com/), a vocabulary-testing
;; product. Much of its user interface is already in place, but its scorer is
;; being replaced. The model in this post is a deliberately small first pass: it
;; is **not deployed in Lexibench**.
;;
;; I reached it through an unusually useful learning loop. Codex acts as a tutor
;; and makes visual explanations; I annotate those explanations in Lavish; then
;; we repeat. Each pass exposes a mistaken assumption or a word I was using too
;; casually.

^:kindly/hide-code
(kind/mermaid
 "flowchart LR
    Q[Jamie asks a question] --> T[Codex tutors and builds a visual explanation]
    T --> A[Jamie annotates it in Lavish]
    A --> R[Codex revises the explanation]
    R --> U[Understanding improves]
    U --> Q")

;; This article records where that loop has taken me so far. I will distinguish
;; **verified mathematics** from **provisional modelling choices** throughout.
;; The distinction matters: correct algebra cannot make a poorly defined target
;; meaningful.
;;
;; ## First define what is being estimated
;;
;; The estimand here is:
;;
;; > **Receptive knowledge of lemma–surface-form pairs in a fixed, versioned
;; > pool.**
;;
;; A lemma such as *run* may have several surface forms. The frequency data I
;; have for a pair does not distinguish senses. Each pair therefore gets one
;; canonical item in context. The context and translation select an intended
;; meaning for that item, but they remain **measurement metadata**; this model
;; does not pretend it has estimated sense-specific knowledge.
;;
;; This is narrower than “How many words do you know?” That narrowing is useful:
;; it gives the count a denominator, a pool version, and a repeatable item.

^:kindly/hide-code
(kind/hiccup
 [:div.ve-callout
  [:strong "Verified mathematics"]
  "Once the outcome, prior, and likelihood are fixed, the Beta posterior and posterior-predictive calculation below follow exactly."])

^:kindly/hide-code
(kind/hiccup
 [:div.ve-callout.provisional
  [:strong "Provisional modelling choice"]
  "The pair inventory, canonical contexts, frequency strata, response mapping, prior, and stopping threshold all need empirical validation."])

;; ## A simplified non-adaptive test
;;
;; For exposition, imagine a cumulative pool split by frequency rank into eight
;; strata with the same number of pairs. A round samples one item uniformly at
;; random from each stratum. Later answers do not change which item is selected:
;; **selection remains non-adaptive**.
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

;; The interface keeps three raw response values:
;; `:correct`, `:wrong`, and `:dont-know`. Stage one maps a correct response to
;; “known” and both other responses to “not known.” Keeping the raw outcomes
;; distinct lets a later model treat guessing, slips, and explicit uncertainty
;; differently without corrupting the original data.

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

(defn collapse-responses
  "Return the sufficient statistics {:k correct :n attempted}."
  [responses]
  {:k (reduce + (map collapse-response responses))
   :n (count responses)})

(into {} (map (juxt identity collapse-response)
              [:correct :wrong :dont-know]))

;; The output confirms that `:wrong` and `:dont-know` remain different raw keys
;; even though their stage-one likelihood contribution is the same.
;;
;; ## One stratum: Beta in, Beta out
;;
;; Let $p_s$ be the knowing rate in stratum $s$. This first pass gives every
;; stratum the same prior:
;;
;; $$p_s \sim \operatorname{Beta}(1,1).$$
;;
;; That density is uniform over rates from zero to one. I am **not** claiming
;; that it is universally “uninformative”; parameterization and context matter.
;; If $k$ of $n$ sampled pairs are correct, the Bernoulli/binomial likelihood is
;; proportional to $p_s^k(1-p_s)^{n-k}$, so:
;;
;; $$p_s \mid k,n \sim \operatorname{Beta}(1+k,1+n-k).$$

(defn posterior-parameters
  "Beta posterior parameters for a Beta(alpha,beta) prior and k of n correct."
  ([k n] (posterior-parameters 1.0 1.0 k n))
  ([alpha beta k n]
   {:pre [(<= 0 k n) (pos? alpha) (pos? beta)]}
   {:alpha (+ alpha k)
    :beta (+ beta (- n k))}))

(posterior-parameters 3 4)

;; `{:alpha 4.0, :beta 2.0}` is the entire update. “Posterior” names the
;; updated density for $p_s$. The “likelihood” is the information supplied by
;; the last response as a function of $p_s$; it is not itself a posterior.
;;
;; ### Try the update
;;
;; Press **Correct** or **Don't know**. The accessible SVG compares the uniform
;; prior, the previous posterior, the last-response likelihood, and the current
;; posterior. Curve height is **density**, not the probability of one exact
;; value of $p$.

^:kindly/hide-code
(kind/hiccup
 [:div.ve-simulator
  [:div#beta-binomial-simulator
   [:p "Loading the Bayesian update simulator…"]]
  [:noscript "This simulator needs JavaScript. The statistical core above remains readable without it."]
  [:script {:type "application/x-scittle"
            :src "beta_binomial_first_pass_interactive.cljs"}]])

;; ## From a knowing rate to a vocabulary total
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
;; The following functions are the executable version. Fastmath supplies the
;; Beta and binomial distributions; a seeded Mersenne Twister makes the rendered
;; result reproducible.

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

(defn posterior-predictive-total
  "Draw and sum known-pair counts across strata."
  ([strata] (posterior-predictive-total 20260712 strata))
  ([seed strata]
   (let [rng (random/rng :mersenne seed)]
     (reduce + (mapv #(posterior-predictive-stratum rng %) strata)))))

(defn quantile
  "Nearest-rank-style quantile from a finite numeric sample."
  [xs probability]
  {:pre [(seq xs) (<= 0.0 probability 1.0)]}
  (let [ordered (vec (sort xs))
        i (long (Math/floor (* probability (dec (count ordered)))))]
    (nth ordered i)))

(defn equal-tail-quantiles
  "Return the central equal-tail interval from a finite sample."
  ([xs] (equal-tail-quantiles xs 0.95))
  ([xs mass]
   (let [tail (/ (- 1.0 mass) 2.0)]
     {:lower (quantile xs tail)
      :upper (quantile xs (- 1.0 tail))})))

;; ## Worked example: a pedagogical 8,000-pair pool
;;
;; **8,000 is an illustration, not a current CEFR or Lexibench pool size.** It
;; consists of eight 1,000-pair strata. After four complete rounds, suppose the
;; correct counts are `[4 4 3 3 2 1 1 0]`.

(def worked-correct-counts [4 4 3 3 2 1 1 0])

(def worked-strata
  (mapv (fn [k] {:pool-size 1000 :k k :n 4})
        worked-correct-counts))

(defn analytic-stratum-mean
  "Posterior-predictive mean for one stratum."
  [{:keys [pool-size k n]}]
  (let [{:keys [alpha beta]} (posterior-parameters k n)]
    (+ k (* (- pool-size n) (/ alpha (+ alpha beta))))))

(def worked-analytic-mean
  (long (reduce + (map analytic-stratum-mean worked-strata))))

worked-analytic-mean

;; The posterior-predictive mean is **4,334 pairs**. Row by row:

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

;; The exact discrete posterior-predictive distribution gives a 95% equal-tail
;; credible interval of **3,404–5,249**. Reader-facing, I would report:
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

(def worked-simulation-summary
  {:draws simulation-draws
   :mean (double (/ (reduce + worked-simulation) simulation-draws))
   :equal-tail-95 (equal-tail-quantiles worked-simulation)})

worked-simulation-summary

;; Small differences are Monte Carlo error. The verification tolerance used for
;; this draft is ±20 pairs for the mean and ±40 pairs for each endpoint.
;;
;; ## When should the test stop?
;;
;; The stopping rule is also provisional:
;;
;; - ask at least 32 items (four complete rounds);
;; - reassess only after another complete eight-item round;
;; - target a 95% interval half-width near 10% of the pool;
;; - use 96 items as a soft maximum;
;; - always allow the learner to stop voluntarily.

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
;;
;; ## Sources and further reading
;;
;; - Gelman et al., [*Bayesian Data Analysis*](https://sites.stat.columbia.edu/gelman/book/), for posterior and posterior-predictive reasoning.
;; - [Fastmath random-distribution documentation](https://generateme.github.io/fastmath/fastmath.random.html), for the executable Beta, binomial, sampling, and inverse-CDF interfaces used here.
;; - [Apache Commons Math distribution API](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/distribution/package-summary.html), the distribution implementation underneath this Fastmath path.
;; - Council of Europe, [CEFR Companion Volume (2020)](https://rm.coe.int/cefr-companion-volume-with-new-descriptors-2020/16809ea0d4), for the broader language-proficiency framework; it does not define this pair-count estimand.
;; - Brysbaert & New, [SUBTLEX-US frequency norms](https://doi.org/10.3758/BRM.41.4.977), for evidence that contextual word frequency can be useful; this does not make frequency rank a calibrated item-difficulty scale.
;;
;; This is a learning-in-public checkpoint. Follow-up posts will replace the
;; roughest assumptions one at a time, beginning with continuous frequency.

^:kindly/hide-code
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
  :verified)
