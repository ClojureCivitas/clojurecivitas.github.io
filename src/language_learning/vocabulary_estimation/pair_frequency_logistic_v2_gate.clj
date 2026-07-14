(ns language-learning.vocabulary-estimation.pair-frequency-logistic-v2-gate
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [language-learning.vocabulary-estimation.pair-frequency-logistic-v2 :as v2])
  (:import [java.util Random]))

(set! *warn-on-reflection* true)

(def fixture-resource
  "language_learning/vocabulary_estimation/pair_frequency_fixture_v1.tsv")

(def tuning-seed 2026071301)
(def held-out-seed 2026071302)
(def pool-size 8000)
(def strata-count 8)
(def maximum-items 160)
(def z-975 1.959963984540054)
(def gate-predictive-draws 512)

(defonce ^ThreadLocal weight-scratch
  (ThreadLocal/withInitial
   (reify java.util.function.Supplier
     (get [_] (double-array 0)))))

(defn thread-weight-scratch ^doubles [size]
  (let [^doubles current (.get weight-scratch)]
    (if (= size (alength current))
      current
      (let [replacement (double-array size)]
        (.set weight-scratch replacement)
        replacement))))

(def v1-rule
  {:minimum-items 32
   :target-half-width-ratio 0.10
   :soft-maximum-items 96})

(def tuning-rules
  (vec
   (for [minimum-items [24 32 40 48]
         target-half-width-ratio [0.05 0.075 0.10 0.125 0.15]
         soft-maximum-items [64 80 96 128 160]
         :when (<= minimum-items soft-maximum-items)]
     {:minimum-items minimum-items
      :target-half-width-ratio target-half-width-ratio
      :soft-maximum-items soft-maximum-items})))

(def supported-cells
  (vec
   (for [expected-ratio [0.1 0.3 0.5 0.7 0.9]
         width [0.75 1.5 3.0]
         residual-sd [0.0 0.5 1.0]]
     {:scenario :supported-logistic
      :expected-ratio expected-ratio
      :width width
      :residual-sd residual-sd})))

(def stress-cells
  (vec
   (mapcat
    (fn [expected-ratio]
      (concat
       [{:scenario :non-logistic-mixture
         :curve :mixture
         :expected-ratio expected-ratio
         :width 1.5
         :residual-sd 0.0}]
       (for [slope [-0.75 0.75]]
         {:scenario :frequency-related-residual
          :expected-ratio expected-ratio
          :width 1.5
          :residual-sd 0.0
          :systematic-residual-slope slope})
       (for [error-rate [0.02 0.05 0.10]]
         {:scenario :false-positive
          :expected-ratio expected-ratio
          :width 1.5
          :residual-sd 0.0
          :false-positive error-rate})
       (for [error-rate [0.02 0.05 0.10]]
         {:scenario :false-negative
          :expected-ratio expected-ratio
          :width 1.5
          :residual-sd 0.0
          :false-negative error-rate})
       (for [error-rate [0.02 0.05 0.10]]
         {:scenario :rank-increasing-measurement-error
          :expected-ratio expected-ratio
          :width 1.5
          :residual-sd 0.0
          :rank-error-maximum error-rate})))
    [0.1 0.3 0.5 0.7 0.9])))

(def diagnostic-rule
  {:minimum-items 48
   :target-half-width-ratio 0.075
   :soft-maximum-items 64})

(defn load-fixture []
  (let [pairs (-> (io/resource fixture-resource)
                  slurp
                  v2/parse-fixture
                  v2/validate-fixture)
        transformed (v2/frequency-transform pairs)]
    (update transformed :pairs
            (fn [pairs]
              (mapv #(assoc %1 :pair-index %2) pairs (range))))))

(defn flatten-schedule [pairs seed]
  (->> (v2/selection-schedule pairs strata-count seed)
       (take (quot maximum-items strata-count))
       (mapcat identity)
       vec))

(defn point-moments [xs {:keys [threshold width]}]
  (let [pair-count (count xs)]
    (loop [index 0 total 0.0 variance 0.0]
      (if (= index pair-count)
        [total variance]
        (let [probability (v2/knowledge-probability
                           (nth xs index) threshold width)]
          (recur (inc index)
                 (+ total probability)
                 (+ variance (* probability (- 1.0 probability)))))))))

(defn double-array-of [values]
  (double-array (map double values)))

(defn log-sum-exp-array [^doubles values]
  (let [size (alength values)
        maximum (loop [index 0 result Double/NEGATIVE_INFINITY]
                  (if (= index size)
                    result
                    (recur (inc index) (max result (aget values index)))))]
    (+ maximum
       (Math/log
        (loop [index 0 total 0.0]
          (if (= index size)
            total
            (recur (inc index)
                   (+ total (Math/exp (- (aget values index) maximum))))))))))

(defn build-grid-cache [xs grid]
  (let [parameters (v2/parameter-grid xs grid v2/default-prior)
        moments (doall (pmap #(point-moments xs %) parameters))
        slopes (double-array-of
                (map #(/ v2/transition-logit-span (:width %)) parameters))
        intercepts (double-array-of
                    (map #(- (* (/ v2/transition-logit-span (:width %))
                                (:threshold %)))
                         parameters))
        log-priors (double-array-of (map :log-prior parameters))]
    {:grid grid
     :size (count parameters)
     :slopes slopes
     :intercepts intercepts
     :log-priors log-priors
     :prior-log-normalizer (log-sum-exp-array log-priors)
     :pool-means (double-array-of (map first moments))
     :pool-variances (double-array-of (map second moments))}))

(defn checkpoint-cache
  [xs selected {:keys [size slopes intercepts log-priors pool-means
                       pool-variances] :as grid-cache}]
  (assoc grid-cache
         :checkpoints
         (into {}
               (for [items-tested (range 24 (inc maximum-items) strata-count)]
                 (let [tested (subvec selected 0 items-tested)
                       base (double-array size)
                       untested-means (double-array size)
                       untested-variances (double-array size)]
                   (dotimes [grid-index size]
                     (let [slope (aget ^doubles slopes grid-index)
                           intercept (aget ^doubles intercepts grid-index)
                           [normalizer tested-mean tested-variance]
                           (loop [remaining (seq tested)
                                  normalizer 0.0
                                  tested-mean 0.0
                                  tested-variance 0.0]
                             (if-not remaining
                               [normalizer tested-mean tested-variance]
                               (let [x (nth xs (:pair-index (first remaining)))
                                     eta (+ (* slope x) intercept)
                                     probability (v2/logistic eta)]
                                 (recur (next remaining)
                                        (+ normalizer (v2/softplus eta))
                                        (+ tested-mean probability)
                                        (+ tested-variance
                                           (* probability (- 1.0 probability)))))))]
                       (aset-double base grid-index
                                    (- (aget ^doubles log-priors grid-index)
                                       normalizer))
                       (aset-double untested-means grid-index
                                    (- (aget ^doubles pool-means grid-index)
                                       tested-mean))
                       (aset-double untested-variances grid-index
                                    (max 0.0
                                         (- (aget ^doubles pool-variances grid-index)
                                            tested-variance)))))
                   [items-tested
                    {:base base
                     :untested-means untested-means
                     :untested-variances untested-variances}])))))

(defn observed-statistics [xs selected responses items-tested]
  (loop [index 0 correct 0 sum-correct-x 0.0]
    (if (= index items-tested)
      {:correct correct :sum-correct-x sum-correct-x}
      (let [outcome (aget ^bytes responses index)]
        (recur (inc index)
               (+ correct outcome)
               (+ sum-correct-x
                  (if (= outcome 1)
                    (nth xs (:pair-index (nth selected index)))
                    0.0)))))))

(defn normal-interval [mean variance minimum maximum]
  (let [half-width (* z-975 (Math/sqrt (max 0.0 variance)))]
    [(max minimum (- mean half-width))
     (min maximum (+ mean half-width))]))

(defn cdf-index [^doubles cumulative probability]
  (loop [lower 0 upper (dec (alength cumulative))]
    (if (>= lower upper)
      lower
      (let [middle (quot (+ lower upper) 2)]
        (if (<= probability (aget cumulative middle))
          (recur lower middle)
          (recur (inc middle) upper))))))

(defn seeded-mixture-interval
  [^doubles cumulative ^doubles untested-means ^doubles variances correct seed]
  (let [[offset state-after-offset] (v2/uniform-draw (v2/normalize-seed seed))]
    (loop [draw-index 0 state state-after-offset totals []]
      (if (= draw-index gate-predictive-draws)
        [(v2/quantile totals 0.025) (v2/quantile totals 0.975)]
        (let [grid-probability
              (/ (+ draw-index offset) gate-predictive-draws)
              grid-index (cdf-index cumulative grid-probability)
              [u1 state-1] (v2/uniform-draw state)
              [u2 next-state] (v2/uniform-draw state-1)
              standard-normal
              (* (Math/sqrt (* -2.0 (Math/log (max u1 1.0e-15))))
                 (Math/cos (* 2.0 Math/PI u2)))
              draw (long
                    (Math/round
                     (+ correct
                        (aget untested-means grid-index)
                        (* standard-normal
                           (Math/sqrt (max 0.0
                                           (aget variances grid-index)))))))]
          (recur (inc draw-index) next-state
                 (conj totals (max correct (min pool-size draw)))))))))

(defn score-v2
  [{:keys [size slopes intercepts prior-log-normalizer checkpoints]}
   items-tested correct sum-correct-x seed]
  (let [{:keys [base untested-means untested-variances]}
        (get checkpoints items-tested)
        ^doubles cumulative (thread-weight-scratch size)
        maximum-log-weight
        (loop [index 0 result Double/NEGATIVE_INFINITY]
          (if (= index size)
            result
            (let [log-weight (+ (aget ^doubles base index)
                                (* (aget ^doubles slopes index) sum-correct-x)
                                (* (aget ^doubles intercepts index) correct))]
              (recur (inc index) (max result log-weight)))))
        [weight-total mean-total second-total]
        (loop [index 0 weight-total 0.0 mean-total 0.0 second-total 0.0]
          (if (= index size)
            [weight-total mean-total second-total]
            (let [log-weight (+ (aget ^doubles base index)
                                (* (aget ^doubles slopes index) sum-correct-x)
                                (* (aget ^doubles intercepts index) correct))
                  relative-log-weight (- log-weight maximum-log-weight)]
              (if (< relative-log-weight -40.0)
                (do
                  (aset-double cumulative index 0.0)
                  (recur (inc index) weight-total mean-total second-total))
                (let [weight (Math/exp relative-log-weight)
                      conditional-mean
                      (+ correct (aget ^doubles untested-means index))
                      conditional-variance
                      (aget ^doubles untested-variances index)]
                  (aset-double cumulative index weight)
                  (recur (inc index)
                         (+ weight-total weight)
                         (+ mean-total (* weight conditional-mean))
                         (+ second-total
                            (* weight
                               (+ conditional-variance
                                  (* conditional-mean conditional-mean))))))))))
        estimate (/ mean-total weight-total)
        variance (max 0.0 (- (/ second-total weight-total)
                             (* estimate estimate)))
        _ (loop [index 0 running 0.0]
            (when (< index size)
              (let [next-running (+ running (aget cumulative index))]
                (aset-double cumulative index (/ next-running weight-total))
                (recur (inc index) next-running))))
        [lower upper]
        (seeded-mixture-interval cumulative untested-means
                                 untested-variances correct seed)
        log-normalizer (+ maximum-log-weight (Math/log weight-total))]
    {:estimate estimate
     :lower lower
     :upper upper
     :interval-width (- upper lower)
     :log-score (/ (- log-normalizer prior-log-normalizer) items-tested)}))

(defn score-v1 [selected ^bytes responses items-tested]
  (let [n-per-stratum (quot items-tested strata-count)
        ^longs correct-by-stratum
        (loop [index 0 counts (long-array strata-count)]
          (if (= index items-tested)
            counts
            (let [stratum (:stratum-index (nth selected index))]
              (aset-long counts stratum
                         (+ (aget counts stratum) (aget responses index)))
              (recur (inc index) counts))))
        correct (reduce + (seq correct-by-stratum))
        [estimate variance]
        (loop [stratum 0 estimate 0.0 variance 0.0]
          (if (= stratum strata-count)
            [estimate variance]
            (let [k (aget correct-by-stratum stratum)
                  alpha (+ 1.0 k)
                  beta (+ 1.0 (- n-per-stratum k))
                  untested (- 1000 n-per-stratum)
                  posterior-mean (/ alpha (+ alpha beta))
                  stratum-mean (+ k (* untested posterior-mean))
                  stratum-variance
                  (/ (* untested alpha beta (+ alpha beta untested))
                     (* (+ alpha beta) (+ alpha beta)
                        (+ alpha beta 1.0)))]
              (recur (inc stratum)
                     (+ estimate stratum-mean)
                     (+ variance stratum-variance)))))
        [lower upper] (normal-interval estimate variance correct pool-size)
        log-score
        (loop [index 0
               seen (long-array strata-count)
               known (long-array strata-count)
               total 0.0]
          (if (= index items-tested)
            (/ total items-tested)
            (let [stratum (:stratum-index (nth selected index))
                  outcome (aget responses index)
                  probability (/ (+ 1.0 (aget known stratum))
                                 (+ 2.0 (aget seen stratum)))]
              (aset-long seen stratum (inc (aget seen stratum)))
              (when (= outcome 1)
                (aset-long known stratum (inc (aget known stratum))))
              (recur (inc index) seen known
                     (+ total
                        (Math/log (if (= outcome 1)
                                    probability
                                    (- 1.0 probability))))))))]
    {:estimate estimate
     :lower lower
     :upper upper
     :interval-width (- upper lower)
     :log-score log-score}))

(defn measured-outcome [^Random rng known? false-positive false-negative]
  (cond
    (and known? (< (.nextDouble rng) false-negative)) 0
    (and (not known?) (< (.nextDouble rng) false-positive)) 1
    known? 1
    :else 0))

(defn latent-probability
  [x {:keys [curve width systematic-residual-slope]} threshold residual]
  (if (= curve :mixture)
    (+ (* 0.5 (v2/knowledge-probability x (- threshold 0.6) 0.75))
       (* 0.5 (v2/knowledge-probability x (+ threshold 0.6) 3.0)))
    (v2/logistic
     (+ (v2/linear-predictor x threshold width)
        (* (double (or systematic-residual-slope 0.0)) x)
        residual))))

(defn scenario-threshold [xs scenario target]
  (loop [lower (- (apply min xs) 20.0)
         upper (+ (apply max xs) 20.0)
         iteration 0]
    (let [midpoint (/ (+ lower upper) 2.0)
          total (reduce + 0.0
                        (map #(latent-probability % scenario midpoint 0.0) xs))]
      (if (= iteration 80)
        midpoint
        (if (> total target)
          (recur midpoint upper (inc iteration))
          (recur lower midpoint (inc iteration)))))))

(defn simulate-replicate
  [xs selected {:keys [threshold residual-sd false-positive false-negative
                       rank-error-maximum]
                :or {false-positive 0.0 false-negative 0.0
                     rank-error-maximum 0.0}
                :as scenario}
   seed]
  (let [rng (Random. (long seed))
        known (byte-array pool-size)
        true-total
        (loop [index 0 total 0]
          (if (= index pool-size)
            total
            (let [residual (if (zero? residual-sd)
                             0.0
                             (* residual-sd (.nextGaussian rng)))
                  probability (latent-probability (nth xs index) scenario
                                                  threshold residual)
                  outcome (if (< (.nextDouble rng) probability) 1 0)]
              (aset-byte known index (byte outcome))
              (recur (inc index) (+ total outcome)))))
        responses (byte-array maximum-items)]
    (dotimes [index maximum-items]
      (let [pair-index (:pair-index (nth selected index))
            known? (= 1 (aget known pair-index))
            rank-error (* rank-error-maximum (/ pair-index 7999.0))]
        (aset-byte responses index
                   (byte (measured-outcome rng known?
                                           (+ false-positive rank-error)
                                           (+ false-negative rank-error))))))
    {:truth true-total :responses responses}))

(defn checkpoint-scores [xs selected cache ^bytes responses seed item-counts]
  (into {}
        (for [items-tested item-counts
              :let [{:keys [correct sum-correct-x]}
                    (observed-statistics xs selected responses items-tested)]]
          [items-tested
           {:v1 (score-v1 selected responses items-tested)
            :v2 (score-v2 cache items-tested correct sum-correct-x
                          (+ seed items-tested))}])))

(defn stop-at [checkpoint-results rule model]
  (let [{:keys [minimum-items target-half-width-ratio soft-maximum-items]} rule]
    (loop [items-tested minimum-items]
      (let [{:keys [lower upper] :as score}
            (get-in checkpoint-results [items-tested model])
            precision? (<= (/ (- upper lower) 2.0)
                           (* target-half-width-ratio pool-size))
            soft-maximum? (>= items-tested soft-maximum-items)]
        (if (or precision? soft-maximum?)
          (assoc score
                 :items-tested items-tested
                 :stopping-reason (if precision?
                                    :precision-target
                                    :soft-maximum))
          (recur (+ items-tested strata-count)))))))

(defn result-metrics [truth result]
  {:covered? (<= (:lower result) truth (:upper result))
   :error (- (:estimate result) truth)
   :absolute-error (Math/abs (double (- (:estimate result) truth)))
   :interval-width (:interval-width result)
   :log-score (:log-score result)
   :items-tested (:items-tested result)
   :stopping-reason (:stopping-reason result)})

(defn summarize-metrics [metrics]
  (let [count* (count metrics)
        sorted-lengths (vec (sort (map :items-tested metrics)))
        reasons (frequencies (map :stopping-reason metrics))]
    {:replicates count*
     :coverage (/ (count (filter :covered? metrics)) (double count*))
     :bias (/ (reduce + (map :error metrics)) count*)
     :mae (/ (reduce + (map :absolute-error metrics)) count*)
     :mean-interval-width (/ (reduce + (map :interval-width metrics)) count*)
     :mean-log-score (/ (reduce + (map :log-score metrics)) count*)
     :median-items (nth sorted-lengths (quot count* 2))
     :mean-items (/ (reduce + sorted-lengths) (double count*))
     :stopping-reasons reasons}))

(defn cell-key [cell]
  (into (sorted-map) cell))

(defn rule-checkpoints [{:keys [minimum-items soft-maximum-items]}]
  (range minimum-items (inc soft-maximum-items) strata-count))

(defn simulate-cell [xs selected cache cell replicate-count base-seed rules]
  (let [threshold (scenario-threshold
                   xs cell (* pool-size (:expected-ratio cell)))
        scenario (assoc cell :threshold threshold)
        item-counts (vec (sort (distinct (mapcat rule-checkpoints
                                                 (conj (vec rules) v1-rule)))))
        replicates
        (mapv (fn [replicate-index]
                (let [replicate-seed (+ base-seed
                                        (* 1000003 (hash (cell-key cell)))
                                        replicate-index)
                      {:keys [truth responses]}
                      (simulate-replicate xs selected scenario
                                          replicate-seed)]
                  {:truth truth
                   :checkpoints (checkpoint-scores xs selected cache responses
                                                   replicate-seed item-counts)}))
              (range replicate-count))
        v1-metrics
        (mapv (fn [{:keys [truth checkpoints]}]
                (result-metrics truth (stop-at checkpoints v1-rule :v1)))
              replicates)
        v2-by-rule
        (into {}
              (for [rule rules]
                [rule
                 (summarize-metrics
                  (mapv (fn [{:keys [truth checkpoints]}]
                          (result-metrics truth (stop-at checkpoints rule :v2)))
                        replicates))]))]
    {:cell cell
     :v1 (summarize-metrics v1-metrics)
     :v2-by-rule v2-by-rule}))

(defn aggregate-summaries [summaries]
  (let [replicates (reduce + (map :replicates summaries))]
    {:replicates replicates
     :coverage (/ (reduce + (map #(* (:coverage %) (:replicates %)) summaries))
                  replicates)
     :bias (/ (reduce + (map #(* (:bias %) (:replicates %)) summaries))
              replicates)
     :mae (/ (reduce + (map #(* (:mae %) (:replicates %)) summaries))
             replicates)
     :mean-interval-width
     (/ (reduce + (map #(* (:mean-interval-width %) (:replicates %)) summaries))
        replicates)
     :mean-log-score
     (/ (reduce + (map #(* (:mean-log-score %) (:replicates %)) summaries))
        replicates)
     :mean-items
     (/ (reduce + (map #(* (:mean-items %) (:replicates %)) summaries))
        replicates)
     :median-items
     (let [expanded (sort (mapcat (fn [summary]
                                    (repeat (:replicates summary)
                                            (:median-items summary)))
                                  summaries))]
       (nth (vec expanded) (quot replicates 2)))}))

(defn tuning-table [cell-results rules]
  (let [v1-cells (mapv :v1 cell-results)
        v1-aggregate (aggregate-summaries v1-cells)]
    {:v1 {:aggregate v1-aggregate :cells v1-cells}
     :rules
     (mapv (fn [rule]
             (let [cells (mapv #(get-in % [:v2-by-rule rule]) cell-results)
                   aggregate (aggregate-summaries cells)
                   passes?
                   (and (>= (:coverage aggregate) 0.945)
                        (every? #(>= (:coverage %) 0.94) cells)
                        (< (:mae aggregate) (:mae v1-aggregate))
                        (every? true?
                                (map #(<= (:mae %1) (* 1.05 (:mae %2)))
                                     cells v1-cells))
                        (<= (:median-items aggregate)
                            (:median-items v1-aggregate)))]
               {:rule rule
                :aggregate aggregate
                :minimum-cell-coverage (apply min (map :coverage cells))
                :maximum-cell-mae-ratio
                (apply max (map #(/ (:mae %1) (:mae %2)) cells v1-cells))
                :passes? passes?}))
           rules)}))

(defn choose-rule [tuning]
  (first
   (sort-by (juxt #(get-in % [:aggregate :median-items])
                  #(get-in % [:aggregate :mean-items])
                  #(get-in % [:rule :minimum-items])
                  #(get-in % [:rule :soft-maximum-items])
                  #(get-in % [:rule :target-half-width-ratio]))
            (filter :passes? (:rules tuning)))))

(defn run-supported
  [{:keys [replicates seed grid output-path phase rules]
    :or {replicates 500
         seed tuning-seed
         grid v2/default-grid
         output-path "resources/language_learning/vocabulary_estimation/pair_frequency_logistic_v2_gate.edn"
         phase :tuning}}]
  (let [rules (or rules tuning-rules)
        {:keys [pairs] :as fixture} (load-fixture)
        xs (mapv :x pairs)
        selected (flatten-schedule pairs seed)
        started (System/nanoTime)
        grid-cache (build-grid-cache xs grid)
        cache (checkpoint-cache xs selected grid-cache)
        cell-results (doall
                      (pmap #(simulate-cell xs selected cache % replicates seed rules)
                            supported-cells))
        tuning (tuning-table cell-results rules)
        result {:algorithm-id v2/algorithm-id
                :fixture-id v2/fixture-id
                :fixture-sha256 v2/fixture-sha256
                :phase phase
                :seed seed
                :replicates-per-cell replicates
                :supported-cell-count (count supported-cells)
                :grid grid
                :interval-method
                {:parameter-mixture :deterministic-systematic-grid-sampling
                 :untested-pair-total :moment-matched-poisson-binomial-normal
                 :draws gate-predictive-draws}
                :fixture-transform (select-keys fixture
                                                [:log10-mean
                                                 :log10-population-sd])
                :v1 (:v1 tuning)
                :rules (:rules tuning)
                :chosen (choose-rule tuning)
                :elapsed-seconds
                (/ (- (System/nanoTime) started) 1.0e9)}]
    (io/make-parents output-path)
    (spit output-path (with-out-str (pprint/pprint result)))
    result))

(defn stress-summaries [cell-results rule]
  (into (sorted-map)
        (for [[scenario results] (group-by #(get-in % [:cell :scenario])
                                           cell-results)
              :let [v1-cells (mapv :v1 results)
                    v2-cells (mapv #(get-in % [:v2-by-rule rule]) results)]]
          [scenario
           {:cells (count results)
            :v1 (aggregate-summaries v1-cells)
            :v2 (aggregate-summaries v2-cells)
            :minimum-cell-coverage (apply min (map :coverage v2-cells))
            :maximum-cell-mae-ratio
            (apply max (map #(/ (:mae %1) (:mae %2)) v2-cells v1-cells))}])))

(defn run-stress
  [{:keys [replicates seed grid output-path]
    :or {replicates 2000
         seed 2026071305
         grid v2/default-grid
         output-path
         "resources/language_learning/vocabulary_estimation/pair_frequency_logistic_v2_stress.edn"}}]
  (let [{:keys [pairs] :as fixture} (load-fixture)
        xs (mapv :x pairs)
        selected (flatten-schedule pairs seed)
        started (System/nanoTime)
        cache (checkpoint-cache xs selected (build-grid-cache xs grid))
        cell-results
        (doall
         (pmap #(simulate-cell xs selected cache % replicates seed
                               [diagnostic-rule])
               stress-cells))
        result {:algorithm-id v2/algorithm-id
                :fixture-id v2/fixture-id
                :fixture-sha256 v2/fixture-sha256
                :phase :untuned-stress
                :seed seed
                :replicates-per-cell replicates
                :stress-cell-count (count stress-cells)
                :rule diagnostic-rule
                :grid grid
                :interval-method
                {:parameter-mixture :deterministic-systematic-grid-sampling
                 :untested-pair-total :moment-matched-poisson-binomial-normal
                 :draws gate-predictive-draws}
                :fixture-transform (select-keys fixture
                                                [:log10-mean
                                                 :log10-population-sd])
                :by-scenario (stress-summaries cell-results diagnostic-rule)
                :elapsed-seconds
                (/ (- (System/nanoTime) started) 1.0e9)}]
    (io/make-parents output-path)
    (spit output-path (with-out-str (pprint/pprint result)))
    result))

(defn -main [& args]
  (let [options (if-let [path (first args)]
                  (edn/read-string (slurp path))
                  {})
        result (run-supported options)]
    (prn (select-keys result
                      [:phase :replicates-per-cell :supported-cell-count
                       :grid :chosen :elapsed-seconds]))
    (shutdown-agents)))
