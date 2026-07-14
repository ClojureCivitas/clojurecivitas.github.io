(ns language-learning.vocabulary-estimation.pair-frequency-logistic-v2
  (:require [clojure.string :as str]))

(def algorithm-id :continuous-pair-frequency-logistic-v2)
(def algorithm-version 2)
(def fixture-id "subtlex-pl-pair-frequency-source-ranks-1-8000-v1")
(def fixture-sha256
  "72f8a84b6e98fb15c868e046afa209035d405b1cdcb3906c25949ca99bdd579a")
(def credible-mass 0.95)
(def default-seed 20260713)
(def transition-logit-span (* 2.0 (Math/log 9.0)))

(def default-prior
  {:threshold-mean 0.0
   :threshold-sd 2.5
   :log-width-mean (Math/log 2.0)
   :log-width-sd 0.6})

(def default-grid
  {:threshold-points 161
   :width-points 81
   :minimum-width 0.25
   :maximum-width 8.0})

(def allowed-responses #{:correct :wrong :dont-know})

(defn collapse-response [response]
  (case response
    :correct 1
    :wrong 0
    :dont-know 0
    (throw (ex-info "Unknown raw response"
                    {:response response :allowed allowed-responses}))))

(defn parse-long* [s]
  #?(:clj (Long/parseLong s)
     :cljs (let [n (js/Number s)]
             (when-not (and (js/Number.isInteger n) (js/Number.isSafeInteger n))
               (throw (ex-info "Invalid integer" {:value s})))
             n)))

(defn parse-double* [s]
  #?(:clj (Double/parseDouble s)
     :cljs (let [n (js/Number s)]
             (when-not (js/Number.isFinite n)
               (throw (ex-info "Invalid number" {:value s})))
             n)))

(def fixture-columns
  [:source-row-rank :surface-form-id :lemma-id :pair-frequency-sn-sum
   :pair-frequency-rank])

(defn parse-fixture
  "Parse the versioned five-column TSV fixture without doing I/O."
  [tsv]
  (let [[header & lines] (str/split-lines tsv)
        expected-header (str/join "\t"
                                  ["surface_form_lemma_pair_frequency_rank_id"
                                   "surface_form_id"
                                   "lemma_id"
                                   "pair_frequency_sn_sum"
                                   "pair_frequency_sn_sum_rank"])]
    (when-not (= expected-header header)
      (throw (ex-info "Unexpected pair-frequency fixture header"
                      {:expected expected-header :actual header})))
    (mapv (fn [line]
            (let [[source-rank surface-id lemma-id frequency frequency-rank]
                  (str/split line #"\t" -1)]
              (zipmap fixture-columns
                      [(parse-long* source-rank)
                       (parse-long* surface-id)
                       (parse-long* lemma-id)
                       (parse-double* frequency)
                       (parse-long* frequency-rank)])))
          (remove str/blank? lines))))

(defn validate-fixture [pairs]
  (when-not (= 8000 (count pairs))
    (throw (ex-info "Fixture must contain source ranks 1 through 8,000"
                    {:count (count pairs)})))
  (when-not (= (count pairs)
               (count (distinct (map (juxt :surface-form-id :lemma-id) pairs))))
    (throw (ex-info "Fixture pair IDs must be unique" {})))
  (doseq [[expected {:keys [source-row-rank pair-frequency-rank
                            pair-frequency-sn-sum]}]
          (map vector (range 1 8001) pairs)]
    (when-not (= expected source-row-rank pair-frequency-rank)
      (throw (ex-info "Fixture ranks must be consecutive and aligned"
                      {:expected expected
                       :source-row-rank source-row-rank
                       :pair-frequency-rank pair-frequency-rank})))
    (when-not (pos? pair-frequency-sn-sum)
      (throw (ex-info "Pair frequency must be positive"
                      {:rank expected :frequency pair-frequency-sn-sum}))))
  pairs)

(defn mean [xs]
  (/ (reduce + 0.0 xs) (count xs)))

(defn population-sd [xs]
  (let [m (mean xs)]
    (Math/sqrt (/ (reduce + 0.0 (map #(let [d (- % m)] (* d d)) xs))
                  (count xs)))))

(defn frequency-transform
  "Attach log10 frequency and its population-standardized predictor."
  [pairs]
  (let [logs (mapv #(Math/log10 (:pair-frequency-sn-sum %)) pairs)
        location (mean logs)
        scale (population-sd logs)]
    (when-not (pos? scale)
      (throw (ex-info "Frequency transform needs non-constant values" {})))
    {:log10-mean location
     :log10-population-sd scale
     :pairs (mapv (fn [pair log-frequency]
                    (assoc pair
                           :log10-pair-frequency log-frequency
                           :x (/ (- log-frequency location) scale)))
                  pairs logs)}))

(defn logistic [z]
  (if (neg? z)
    (let [e (Math/exp z)] (/ e (+ 1.0 e)))
    (/ 1.0 (+ 1.0 (Math/exp (- z))))))

(defn knowledge-probability [x threshold width]
  (when-not (pos? width)
    (throw (ex-info "Transition width must be positive" {:width width})))
  (logistic (/ (* transition-logit-span (- x threshold)) width)))

(defn linear-predictor [x threshold width]
  (/ (* transition-logit-span (- x threshold)) width))

(defn softplus [z]
  (if (pos? z)
    (+ z (Math/log1p (Math/exp (- z))))
    (Math/log1p (Math/exp z))))

(defn expected-total [xs threshold width]
  (reduce + 0.0 (map #(knowledge-probability % threshold width) xs)))

(defn threshold-for-total
  "Find the threshold giving a requested expected total by deterministic bisection."
  [xs width target]
  {:pre [(seq xs) (<= 0.0 target (double (count xs))) (pos? width)]}
  (loop [lower (- (apply min xs) 20.0)
         upper (+ (apply max xs) 20.0)
         iteration 0]
    (let [midpoint (/ (+ lower upper) 2.0)
          total (expected-total xs midpoint width)]
      (if (= iteration 80)
        midpoint
        (if (> total target)
          (recur midpoint upper (inc iteration))
          (recur lower midpoint (inc iteration)))))))

(defn linspace [lower upper n]
  (if (= n 1)
    [lower]
    (mapv #(+ lower (* (/ % (double (dec n))) (- upper lower)))
          (range n))))

(defn logspace [lower upper n]
  (mapv #(Math/exp %)
        (linspace (Math/log lower) (Math/log upper) n)))

(defn normal-log-density [x location scale]
  (let [z (/ (- x location) scale)]
    (- (- (/ (* z z) 2.0))
       (Math/log scale)
       (* 0.5 (Math/log (* 2.0 Math/PI))))))

(defn grid-spec
  ([xs] (grid-spec xs default-grid))
  ([xs {:keys [threshold-points width-points minimum-width maximum-width]}]
   {:thresholds (linspace (- (apply min xs) 2.0)
                          (+ (apply max xs) 2.0)
                          threshold-points)
    :widths (logspace minimum-width maximum-width width-points)}))

(defn parameter-grid
  ([xs] (parameter-grid xs default-grid default-prior))
  ([xs grid prior]
   (let [{:keys [thresholds widths]} (grid-spec xs grid)
         {:keys [threshold-mean threshold-sd log-width-mean log-width-sd]} prior]
     (mapv (fn [[threshold width]]
             (let [log-width (Math/log width)]
               {:threshold threshold
                :width width
                :log-prior (+ (normal-log-density threshold threshold-mean threshold-sd)
                              (normal-log-density log-width log-width-mean log-width-sd))}))
           (for [threshold thresholds width widths] [threshold width])))))

(defn log-bernoulli-eta [outcome eta]
  (if (= 1 outcome)
    (- (softplus (- eta)))
    (- (softplus eta))))

(defn log-likelihood [observations threshold width]
  (reduce + 0.0
          (map (fn [{:keys [x response]}]
                 (log-bernoulli-eta (collapse-response response)
                                    (linear-predictor x threshold width)))
               observations)))

(defn normalize-log-weights [log-weights]
  (let [maximum (apply max log-weights)
        relative (mapv #(Math/exp (- % maximum)) log-weights)
        total (reduce + 0.0 relative)]
    (when-not (pos? total)
      (throw (ex-info "Posterior grid has zero total weight" {})))
    (mapv #(/ % total) relative)))

(defn posterior-grid
  ([xs observations] (posterior-grid xs observations default-grid default-prior))
  ([xs observations grid prior]
   (let [parameters (parameter-grid xs grid prior)
         log-weights (mapv (fn [{:keys [threshold width log-prior]}]
                             (+ log-prior
                                (log-likelihood observations threshold width)))
                           parameters)
         weights (normalize-log-weights log-weights)]
     (mapv #(assoc %1 :weight %2) parameters weights))))

(defn prior-grid
  ([xs] (prior-grid xs default-grid default-prior))
  ([xs grid prior]
   (let [parameters (parameter-grid xs grid prior)
         weights (normalize-log-weights (mapv :log-prior parameters))]
     (mapv #(assoc %1 :weight %2) parameters weights))))

(defn quantile [xs probability]
  {:pre [(seq xs) (<= 0.0 probability 1.0)]}
  (let [ordered (vec (sort xs))
        index (long (Math/floor (* probability (dec (count ordered)))))]
    (nth ordered index)))

(defn weighted-mean [entries value-fn]
  (reduce + 0.0 (map #(* (:weight %) (value-fn %)) entries)))

(defn weighted-quantile [entries value-fn probability]
  {:pre [(seq entries) (<= 0.0 probability 1.0)]}
  (loop [remaining (sort-by value-fn entries) cumulative 0.0]
    (let [entry (first remaining)
          next-cumulative (+ cumulative (:weight entry))]
      (if (or (nil? (next remaining)) (<= probability next-cumulative))
        (value-fn entry)
        (recur (next remaining) next-cumulative)))))

(defn prior-predictive-expected-summary
  "Summarize prior uncertainty in the expected finite-pool total."
  [xs prior]
  (let [grid (mapv (fn [{:keys [threshold width] :as point}]
                     (assoc point :expected-total
                            (expected-total xs threshold width)))
                   (prior-grid xs default-grid prior))]
    {:mean (weighted-mean grid :expected-total)
     :lower (weighted-quantile grid :expected-total 0.025)
     :median (weighted-quantile grid :expected-total 0.5)
     :upper (weighted-quantile grid :expected-total 0.975)}))

;; Park-Miller state is explicit so CLJ and CLJS replay the same sequence.
(def rng-modulus 2147483647)
(def rng-multiplier 48271)

(defn normalize-seed [seed]
  (let [state (mod (long seed) rng-modulus)]
    (if (zero? state) 1 state)))

(defn uniform-draw [state]
  (let [next-state (mod (* rng-multiplier state) rng-modulus)]
    [(/ (dec next-state) 2147483646.0) next-state]))

(defn categorical-draw [state entries]
  (let [[u next-state] (uniform-draw state)
        entries (if (contains? (first entries) :cumulative-weight)
                  entries
                  (second
                   (reduce (fn [[total result] entry]
                             (let [next-total (+ total (:weight entry))]
                               [next-total
                                (conj result
                                      (assoc entry :cumulative-weight next-total))]))
                           [0.0 []]
                           entries)))]
    [(loop [lower 0 upper (dec (count entries))]
       (if (>= lower upper)
         (nth entries lower)
         (let [middle (quot (+ lower upper) 2)]
           (if (<= u (:cumulative-weight (nth entries middle)))
             (recur lower middle)
             (recur (inc middle) upper)))))
     next-state]))

(defn cumulative-weights [entries]
  (second
   (reduce (fn [[total result] entry]
             (let [next-total (+ total (:weight entry))]
               [next-total (conj result (assoc entry :cumulative-weight next-total))]))
           [0.0 []]
           entries)))

(defn bernoulli-draw [state probability]
  (let [[u next-state] (uniform-draw state)]
    [(if (< u probability) 1 0) next-state]))

(defn tested-indexes [observations]
  (let [indexes (map :pair-index observations)]
    (when-not (= (count indexes) (count (distinct indexes)))
      (throw (ex-info "Duplicate tested pair" {:pair-indexes indexes})))
    (set indexes)))

(defn predictive-grid
  "Attach exact finite-pool conditional mean and Bernoulli variance per grid point."
  [xs observations posterior]
  (let [tested (tested-indexes observations)
        correct (reduce + 0 (map #(collapse-response (:response %)) observations))
        pair-count (count xs)]
    (mapv (fn [{:keys [threshold width] :as point}]
            (let [[probability-total variance-total]
                  (loop [index 0 probability-total 0.0 variance-total 0.0]
                    (if (= index pair-count)
                      [probability-total variance-total]
                      (if (contains? tested index)
                        (recur (inc index) probability-total variance-total)
                        (let [probability
                              (knowledge-probability (nth xs index) threshold width)]
                          (recur (inc index)
                                 (+ probability-total probability)
                                 (+ variance-total
                                    (* probability (- 1.0 probability))))))))]
              (assoc point
                     :predictive-mean (+ correct probability-total)
                     :predictive-variance variance-total)))
          posterior)))

(defn posterior-predictive-draws
  "Sample grid uncertainty and only untested pair outcomes. Returns integer totals."
  [xs observations posterior draw-count seed]
  (let [tested (tested-indexes observations)
        correct (reduce + 0 (map #(collapse-response (:response %)) observations))
        posterior (cumulative-weights posterior)]
    (loop [draw-index 0 state (normalize-seed seed) totals []]
      (if (= draw-index draw-count)
        totals
        (let [[{:keys [threshold width]} state-after-grid]
              (categorical-draw state posterior)
              [untested-total final-state]
              (reduce-kv
               (fn [[total current-state] index x]
                 (if (contains? tested index)
                   [total current-state]
                   (let [[known? next-state]
                         (bernoulli-draw current-state
                                         (knowledge-probability x threshold width))]
                     [(+ total known?) next-state])))
               [0 state-after-grid]
               xs)]
          (recur (inc draw-index) final-state
                 (conj totals (+ correct untested-total))))))))

(defn posterior-predictive-summary
  ([xs observations posterior draw-count seed]
   (posterior-predictive-summary xs observations posterior draw-count seed
                                 credible-mass))
  ([xs observations posterior draw-count seed mass]
   (let [predictive (predictive-grid xs observations posterior)
         draws (posterior-predictive-draws xs observations posterior draw-count seed)
         tail (/ (- 1.0 mass) 2.0)]
     {:mean (weighted-mean predictive :predictive-mean)
      :lower (quantile draws tail)
      :upper (quantile draws (- 1.0 tail))
      :credible-mass mass
      :draw-count draw-count
      :seed seed})))

(defn doubled-grid [grid]
  (-> grid
      (update :threshold-points #(- (* 2 %) 1))
      (update :width-points #(- (* 2 %) 1))))

(defn grid-convergence [coarse-summary doubled-summary]
  {:mean-difference (Math/abs (- (:mean coarse-summary) (:mean doubled-summary)))
   :lower-difference (Math/abs (- (:lower coarse-summary) (:lower doubled-summary)))
   :upper-difference (Math/abs (- (:upper coarse-summary) (:upper doubled-summary)))
   :passes? (and (< (Math/abs (- (:mean coarse-summary) (:mean doubled-summary))) 10.0)
                 (< (Math/abs (- (:lower coarse-summary) (:lower doubled-summary))) 25.0)
                 (< (Math/abs (- (:upper coarse-summary) (:upper doubled-summary))) 25.0))})

(defn near-equal-strata [pairs strata-count]
  (let [ordered (vec (sort-by (juxt :pair-frequency-rank
                                    #(str (:lemma-id %) ":" (:surface-form-id %)))
                              pairs))
        base (quot (count ordered) strata-count)
        extra (mod (count ordered) strata-count)]
    (loop [index 0 stratum 0 result []]
      (if (= stratum strata-count)
        result
        (let [size (+ base (if (< stratum extra) 1 0))
              next-index (+ index size)]
          (recur next-index (inc stratum)
                 (conj result (subvec ordered index next-index))))))))

(defn shuffle-vector [values seed]
  (loop [result (vec values)
         index (dec (count values))
         state (normalize-seed seed)]
    (if (<= index 0)
      [result state]
      (let [[u next-state] (uniform-draw state)
            swap-index (long (Math/floor (* u (inc index))))
            a (nth result index)
            b (nth result swap-index)]
        (recur (assoc result index b swap-index a)
               (dec index)
               next-state)))))

(defn selection-queues [pairs strata-count seed]
  (loop [strata (near-equal-strata pairs strata-count)
         state (normalize-seed seed)
         queues []]
    (if-not (seq strata)
      queues
      (let [[queue next-state] (shuffle-vector (first strata) state)]
        (recur (next strata) next-state (conj queues queue))))))

(defn selection-schedule
  "Return balanced, non-adaptive rounds with the probability at selection time."
  [pairs strata-count seed]
  (let [queues (selection-queues pairs strata-count seed)
        rounds (apply min (map count queues))]
    (loop [round-index 0 state (normalize-seed (+ seed 104729)) result []]
      (if (= round-index rounds)
        result
        (let [selected (mapv (fn [stratum-index queue]
                               (assoc (nth queue round-index)
                                      :stratum-index stratum-index
                                      :round-index round-index
                                      :selection-probability
                                      (/ 1.0 (- (count queue) round-index))))
                             (range strata-count) queues)
              [ordered next-state] (shuffle-vector selected state)]
          (recur (inc round-index) next-state (conj result ordered)))))))

(defn stopping-check
  [{:keys [items-tested lower upper pool-size voluntary? minimum-items
           round-size target-half-width-ratio soft-maximum-items]
    :or {voluntary? false}}]
  (let [complete-round? (zero? (mod items-tested round-size))
        assess? (and complete-round? (>= items-tested minimum-items))
        half-width (/ (- upper lower) 2.0)
        precision? (and assess?
                        (<= half-width (* target-half-width-ratio pool-size)))
        soft-maximum? (and assess? (>= items-tested soft-maximum-items))
        reason (cond
                 voluntary? :voluntary
                 precision? :precision-target
                 soft-maximum? :soft-maximum
                 :else :continue)]
    {:assess? assess?
     :complete-round? complete-round?
     :half-width half-width
     :recommended-stop? (not= :continue reason)
     :reason reason}))

(defn apply-measurement-error [probability false-positive false-negative]
  (+ (* probability (- 1.0 false-negative))
     (* (- 1.0 probability) false-positive)))

(defn simulate-responses
  "Generate binary-backed raw outcomes from supplied item residuals and RNG state."
  [xs pair-indexes {:keys [threshold width residuals false-positive false-negative]
                    :or {residuals {} false-positive 0.0 false-negative 0.0}}
   seed]
  (loop [indexes (seq pair-indexes) state (normalize-seed seed) responses []]
    (if-not indexes
      responses
      (let [index (first indexes)
            x (nth xs index)
            residual (double (get residuals index 0.0))
            latent (logistic (+ (/ (* transition-logit-span (- x threshold)) width)
                                residual))
            observed (apply-measurement-error latent false-positive false-negative)
            [known? next-state] (bernoulli-draw state observed)]
        (recur (next indexes) next-state
               (conj responses {:pair-index index
                                :x x
                                :response (if (= 1 known?) :correct :dont-know)}))))))
