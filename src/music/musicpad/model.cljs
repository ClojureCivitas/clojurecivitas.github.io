(ns music.musicpad.model)

(def default-dims [400 400])

(def playback-seconds 3.5)

(def max-note-points 320)

(defn path->stroke-data [[_ attrs]]
  {:points (->> (:d attrs)
                (filter number?)
                (partition 2)
                (mapv vec))
   :style (select-keys attrs [:stroke
                              :stroke-width
                              :fill
                              :stroke-linecap
                              :stroke-linejoin])})

(defn drawing->data-view [s]
  (let [{:keys [title notes dims svg]} (:drawing s)]
    {:title title
     :notes notes
     :dims dims
     :strokes (mapv path->stroke-data svg)}))

(defn color->rgb [color]
  (let [hex (case color
              "black" "#000000"
              color)]
    (when (and (string? hex)
               (re-matches #"^#[0-9a-fA-F]{6}$" hex))
      {:r (js/parseInt (subs hex 1 3) 16)
       :g (js/parseInt (subs hex 3 5) 16)
       :b (js/parseInt (subs hex 5 7) 16)})))

(defn color->audio-profile [color]
  (let [{:keys [r g b]} (or (color->rgb color) {:r 0 :g 0 :b 0})
        brightness (/ (+ r g b) 765)
        near? (fn [a b]
                (<= (js/Math.abs (- a b)) 16))
        wave (cond
               (and (near? r g) (near? g b)) "sine"
               (and (>= r g) (>= r b)) "sawtooth"
               (and (>= g r) (>= g b)) "triangle"
               :else "square")]
    {:wave wave
     :brightness brightness}))

(defn midi->freq [midi]
  (* 440 (js/Math.pow 2 (/ (- midi 69) 12))))

(defn y->freq [y height]
  (let [clamped-y (-> y (max 0) (min height))
        ;; Top of canvas = high pitch, bottom = low pitch.
        midi (+ 48 (* (- 1 (/ clamped-y height)) 36))]
    (midi->freq midi)))

(defn sampled-points [points max-points]
  (let [step (max 1 (int (js/Math.ceil (/ (count points) max-points))))]
    (->> points
         (map-indexed vector)
         (keep (fn [[idx p]]
                 (when (zero? (mod idx step))
                   p))))))

(defn drawing->note-events [drawing]
  (let [[width height] (or (:dims drawing) default-dims)
        stroke->events
        (fn [{:keys [points style]}]
          (let [stroke-width (double (or (:stroke-width style) 3))
                {:keys [wave brightness]} (color->audio-profile (:stroke style))
                amp (min 0.2 (+ 0.04 (* stroke-width 0.01)))
                duration (max 0.05 (+ 0.03 (* stroke-width 0.008)))
                points* (sampled-points points max-note-points)]
            (for [[x y] points*]
              {:t (* playback-seconds (/ x width))
               :freq (y->freq y height)
               :duration duration
               :amp (* amp (+ 0.6 (* 0.5 brightness)))
               :wave wave})))]
    (->> (:strokes drawing)
         (mapcat stroke->events)
         (sort-by :t))))

(defn vec-remove
	"remove elem in coll"
	[v pos]
	(if (< pos (count v))
		(vec (concat (subvec v 0 pos) (subvec v (inc pos))))
		v))

(defn erase [drawing idx]
	(update drawing :svg vec-remove idx))

(defn remove-point [drawing element-idx path-idx]
	(update-in
	 drawing
	 [:svg element-idx 1 :d]
	 (fn [path]
		 (when-let [c (loop [cnt 0
												 idx 0
												 [x & remaining] path]
										(cond
											(nil? x) nil
											(and (number? x) (= path-idx idx)) cnt
											(number? x) (recur (+ cnt 2) (inc idx) (rest remaining))
											:else (recur (inc cnt) idx remaining)))]
			 (if (<= c 1)
				 (into
					['M (nth path 4) (nth path 5) 'L]
					(drop 6 path))
				 (-> path
						 (vec-remove c)
						 (vec-remove c)))))))

(defn erase-point [drawing element-idx path-idx]
	(if (<= (count (get-in drawing [:svg element-idx 1 :d]))
					6)
		(erase drawing element-idx)
		(remove-point drawing element-idx path-idx)))

(defn point-path-offset [path path-idx]
	(loop [cnt 0
				 idx 0
				 [x & remaining] path]
		(cond
			(nil? x) nil
			(and (number? x) (= path-idx idx)) cnt
			(number? x) (recur (+ cnt 2) (inc idx) (rest remaining))
			:else (recur (inc cnt) idx remaining))))

(defn move-point [drawing element-idx path-idx x y]
	(update-in drawing [:svg element-idx 1 :d]
						 (fn [path]
							 (if-let [c (point-path-offset path path-idx)]
								 (-> path
										 (assoc c x)
										 (assoc (inc c) y))
								 path))))

(defn new-state [{:keys [index values] :as history} value]
	(if (<= (dec (count values)) index)
		(-> history
				(update :index inc)
				(update :values conj value))
		(-> history
				(update :values assoc index value)
				(update :values subvec 0 (inc index)))))

(defn undo [{:keys [index] :as history}]
	(cond-> history
		(pos? index) (update :index dec)))

(defn redo [{:keys [index values] :as history}]
	(cond-> history
		(< index (dec (count values))) (update :index inc)))

(defn current [{:keys [index values]}]
	(values index))
