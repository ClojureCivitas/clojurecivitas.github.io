^{:kindly/hide-code true
  :clay {:title "Composable Graphics: Blend, Cross, and Nest"
         :quarto {:type :post
                  :author [:timothypratley]
                  :draft true
                  :date "2025-12-29"
                  :description "Algebraic operators for composing graphic specifications"
                  :category :data-visualization
                  :tags [:datavis :algebra]
                  :keywords [:datavis :composition :operators]}}}
(ns data-visualization.aog.algebra-of-data
  (:require [clojure.test :refer [deftest is]]))

;; Inspired by [the algebra of graphics](../aog_in_clojure_part1.html).

;; ## Axioms

;; We shall take it as given that we are interested in combining maps:

;; The graphic object structure:

;; ```
;; {:data :dataframe
;;  :mappings {:x :x :y :y}
;;  :geometries []
;;  :options {}
;;  :layers []}
;; ```

;; There are 3 operators we will use to combine them:

(declare blend cross nest)

;; blend
;; : adds a layer; concatinates two or more layers of graphic objects
;; cross
;; : merges layers cartesianally
;; nest
;; : divides a graphic object into smaller graphics by facet

;; ## Graphic Objects

;; The goal is to match some data with how to visualize it

(def penguins
  [{:species "Adelie" :bill_length 39.1 :bill_depth 18.7 :sex "MALE"}
   {:species "Adelie" :bill_length 39.5 :bill_depth 17.4 :sex "FEMALE"}
   {:species "Chinstrap" :bill_length 46.5 :bill_depth 17.9 :sex "FEMALE"}
   {:species "Gentoo" :bill_length 47.3 :bill_depth 13.8 :sex "MALE"}])

(defn graphic [data mappings geometries options]
  {:data data
   :mappings mappings
   :geometries geometries
   :options options})

(graphic #'penguins
         {:x :bill_length :y :bill_depth}
         [:point]
         {:title "Penguin bills"})

;; We don't intend to use `graphic` directly very often,
;; but rather to compose smaller parts together.
;; A graphic can be nil, contain just one key-value, or several.
;; Later in "Constructors" we will demostrate using smaller graphics objects.

;; Graphic objects may contain layers of other graphic objects.

;; ## Operators

(defn blend
  "Concatinates or creates layers from multiple graphics
   into a single graphic with multiple layers."
  ([] nil)
  ([g] g)
  ([g1 g2]
   (let [layers1 (get g1 :layers [g1])
         layers2 (get g2 :layers [g2])
         merged-options (merge (:options g1) (:options g2))]
     {:layers (vec (concat layers1 layers2))
      :options merged-options}))
  ([g1 g2 & more]
   (reduce blend (blend g1 g2) more)))

(def scatter-plot
  (graphic #'penguins
           {:x :bill_length :y :bill_depth}
           [:point]
           {:title "Bill Dimensions"}))

scatter-plot

(def mean-line-data
  [{:avg_length 44.0 :avg_depth 16.5}])

(def mean-line-plot
  (graphic #'mean-line-data
           {:x :avg_length :y :avg_depth}
           [:line]
           {}))

mean-line-plot

(def blended-graphic
  (blend scatter-plot mean-line-plot))

blended-graphic

;; Another thing we can do is a cartesian merge:

(defn merge-layer [a b]
  (let [mappings   (merge (:mappings a) (:mappings b))
        geometries (vec (concat (or (:geometries a) [])
                                (or (:geometries b) [])))
        options    (merge (:options a) (:options b))
        data       (or (:data b) (:data a))]
    (-> a
        (merge b)
        (assoc :data data
               :mappings mappings
               :geometries geometries
               :options options)
        (dissoc :layers))))

(defn cross
  "Merges specifications from g1 and g2 within a single layer.
   Layers undergo a cartesian merge."
  ([] nil)
  ([g] g)
  ([g1 g2]
   {:layers (vec (for [a (or (:layers g1) [g1])
                       b (or (:layers g2) [g2])]
                   (merge-layer a b)))
    :options (merge (:options g1) (:options g2))})
  ([g1 g2 & more]
   (reduce cross (cross g1 g2) more)))

(def base-plot
  (graphic #'penguins
           {:x :bill_length :y :bill_depth}
           [:point]
           {:title "Penguin Bill Dimensions by Species and Sex"}))

(def color-mapping
  {:mappings {:color :species}})

(def shape-mapping
  {:mappings {:shape :sex}})

;; Usage:

(def crossed-graphic
  (cross base-plot color-mapping shape-mapping))

;; When drawn, points have both color and shape determined by data.

crossed-graphic

(defn nest
  "Partitions the data in g1 based on the variable specified in g2's mappings.
   Returns a sequence of new graphic objects, one per data partition (facet)."
  [g1 g2]
  (let [partition-key (-> g2 :mappings :by)
        data-to-partition @(:data g1)
        grouped-data (group-by partition-key data-to-partition)
        facet-role (or (:facet-role (:options g2)) :facet-col)]
    (map (fn [[group-value subset-data]]
           (-> g1
               (assoc :data subset-data)
               (assoc-in [:options facet-role] group-value)
               (update :mappings dissoc :by)))
         grouped-data)))

(def base-scatter
  (graphic #'penguins
           {:x :bill_length :y :bill_depth}
           [:point]
           {:title "Dimensions by Sex"}))

(def nesting-specifier
  {:mappings {:by :sex}
   :options  {:facet-role :facet-col}})

;; Usage:

(def nested-graphics
  (nest base-scatter nesting-specifier))

;; A sequence of two graphic objects:

nested-graphics

;; ## Constructors

;; Given we like configs due to their combinatorial expressiveness,
;; we may introduce some constructors to help us prepare configs concisely.

;; The behavior of all constructors is that they take their specific inputs,
;; followed optionally by a graphic to compose with (as the last argument).
;; This makes them convenient to use with ->> threading.
;; There are many constructors and the code is boring and repetative,
;; but on the plus side, it provides a concrete interface of what is possible.

(defn scatter [& gs]
  (let [g (last gs)
        m {:geometries [:point]}]
    (if (or (seq (:layers g))
            (some-> g :geometries (not= (:geometries m))))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn line [& gs]
  (let [g (last gs)
        m {:geometries [:line]}]
    (if (or (seq (:layers g))
            (some-> g :geometries (not= (:geometries m))))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn bar [& gs]
  (let [g (last gs)
        m {:geometries [:bar]}]
    (if (or (seq (:layers g))
            (some-> g :geometries (not= (:geometries m))))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn area [& gs]
  (let [g (last gs)
        m {:geometries [:area]}]
    (if (or (seq (:layers g))
            (some-> g :geometries (not= (:geometries m))))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn histogram [& gs]
  (let [g (last gs)
        m {:geometries [:histogram]}]
    (if (or (seq (:layers g))
            (some-> g :geometries (not= (:geometries m))))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn boxplot [& gs]
  (let [g (last gs)
        m {:geometries [:boxplot]}]
    (if (or (seq (:layers g))
            (some-> g :geometries (not= (:geometries m))))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn smooth [& gs]
  (let [g (last gs)
        m {:geometries [:smooth]}]
    (if (or (seq (:layers g))
            (some-> g :geometries (not= (:geometries m))))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn data [x & gs]
  (let [g (last gs)
        d {:data x}]
    (if (some-> g :data (not= x))
      (blend g (apply cross d (butlast gs)))
      (apply cross d gs))))

(defn xy [x y & gs]
  (let [g (last gs)
        m {:mappings {:x x :y y}}]
    (if (some-> g :mappings (not= (:mappings m)))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn color [x & gs]
  (let [g (last gs)
        m {:mappings {:color x}}]
    (if (some-> g :mappings :color (not= x))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn size [x & gs]
  (let [g (last gs)
        m {:mappings {:size x}}]
    (if (some-> g :mappings :size (not= x))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn alpha [x & gs]
  (let [g (last gs)
        m {:mappings {:alpha x}}]
    (if (some-> g :mappings :alpha (not= x))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn fill [x & gs]
  (let [g (last gs)
        m {:mappings {:fill x}}]
    (if (some-> g :mappings :fill (not= x))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn stroke [x & gs]
  (let [g (last gs)
        m {:mappings {:stroke x}}]
    (if (some-> g :mappings :stroke (not= x))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn group [x & gs]
  (let [g (last gs)
        m {:mappings {:group x}}]
    (if (some-> g :mappings :group (not= x))
      (blend g (apply cross m (butlast gs)))
      (apply cross m gs))))

(defn facet-col [x & gs]
  (let [spec {:mappings {:by x} :options {:facet-role :facet-col}}]
    (nest (last gs) (apply cross spec (butlast gs)))))

(defn facet-row [x & gs]
  (let [spec {:mappings {:by x} :options {:facet-role :facet-row}}]
    (nest (last gs) (apply cross spec (butlast gs)))))

(defn xlim [min-val max-val & gs]
  (let [g (last gs)
        o {:options {:xlim [min-val max-val]}}]
    (if (some-> g :options :xlim (not= [min-val max-val]))
      (blend g (apply cross o (butlast gs)))
      (apply cross o gs))))

(defn ylim [min-val max-val & gs]
  (let [g (last gs)
        o {:options {:ylim [min-val max-val]}}]
    (if (some-> g :options :ylim (not= [min-val max-val]))
      (blend g (apply cross o (butlast gs)))
      (apply cross o gs))))

(defn xscale [scale-type & gs]
  (let [g (last gs)
        o {:options {:xscale scale-type}}]
    (if (some-> g :options :xscale (not= scale-type))
      (blend g (apply cross o (butlast gs)))
      (apply cross o gs))))

(defn yscale [scale-type & gs]
  (let [g (last gs)
        o {:options {:yscale scale-type}}]
    (if (some-> g :options :yscale (not= scale-type))
      (blend g (apply cross o (butlast gs)))
      (apply cross o gs))))

(defn theme [theme-name & gs]
  (let [g (last gs)
        o {:options {:theme theme-name}}]
    (if (some-> g :options :theme (not= theme-name))
      (blend g (apply cross o (butlast gs)))
      (apply cross o gs))))

(defn width [w & gs]
  (let [g (last gs)
        o {:options {:width w}}]
    (if (some-> g :options :width (not= w))
      (blend g (apply cross o (butlast gs)))
      (apply cross o gs))))

(defn height [h & gs]
  (let [g (last gs)
        o {:options {:height h}}]
    (if (some-> g :options :height (not= h))
      (blend g (apply cross o (butlast gs)))
      (apply cross o gs))))

(defn legend [position & gs]
  (let [g (last gs)
        o {:options {:legend position}}]
    (if (some-> g :options :legend (not= position))
      (blend g (apply cross o (butlast gs)))
      (apply cross o gs))))

(defn title [t & gs]
  (let [g (last gs)
        o {:options {:title t}}]
    (if (some-> g :options :title (not= t))
      (blend g (apply cross o (butlast gs)))
      (apply cross o gs))))

;; ## Constructor examples

;; Constructors can be used without a graphic

(bar)

;; Constructors take arbitrary graphics to cross with

(bar (title "hello"))

(bar (title "hello") (xy :species :bill-depth-mm))

;; ## Threading

;; We can think of a threaded expression as a sequence of terms
;; synonymous with graphics objects.
;; The challenge is how to group those terms.

;; The most common operation is cross:

(->> (data #'penguins)
     (xy :bill-depth-mm :bill-length-mm))

;; Observe that having a `scatter` and a `bar` implies the use of `blend`.

(->> (scatter)
     (bar))

;; Which means they can be composed with ->>

(->> (xy :x :y)
     (bar))

(->> (xy :x :y)
     (bar (title "hello")))

;; Thread last is important because it allows implied blend to occur,
;; with a crossed graphic.

(->> (xy :x :y)
     (scatter)
     (bar (title "hello")))

(->> (data #'penguins)
     (xy :bill-depth-mm :bill-length-mm)
     (scatter)
     (bar (xy :species :bill-depth-mm)))


;; Rather than constructors, we could define "methods"
;; that accept configs, and cross-merge them with constructed configs.

;; ## Unification

;; We can lift common datasets out into a named or indexed dataset.
;; Possibly there are other things that can be collapsed.
;; It may be essentential that we do so.
;; currently the data key is lost when it is moved to a layer.
