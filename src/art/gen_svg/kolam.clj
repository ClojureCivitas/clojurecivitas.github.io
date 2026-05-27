^:kindly/hide-code
(ns art.gen-svg.kolam
  {:clay {:title          "Designing Kolam in Clojure Through Visit Order and Tangency"
          :kindly/options {:kinds-that-hide-code #{:kind/var :kind/hiccup}}
          :quarto         {:author      [:timothypratley]
                           :description "A geometry notebook about drawing curved paths inspired by South Indian floor art"
                           :type        :post
                           :date        "2026-05-25"
                           :reference-location :margin
                           :category    :art
                           :tags        [:art :svg :generative :kolam]}}}
  (:require [clojure.string :as str]
            [civitas.why.village.color :as village-color]))

(def kolam-theme
  ;; Reuse the established Civitas palette for article-wide visual consistency.
  (let [p village-color/palette]
    {:paper           (nth p 0)
     :paper-soft      (nth p 10)
     :ink             (nth p 12)
     :dot             (nth p 11)
     :route-primary   (nth p 11)
     :route-secondary (nth p 8)
     :route-tertiary  (nth p 9)
     :wireframe       (nth p 11)
     :marker-in       (nth p 9)
     :marker-out      (nth p 8)
     :marker-start    "#1976d2"   ; standard blue
     :marker-end      "#d32f2f"   ; standard red
     :marker-stroke   (nth p 12)}))

(defn theme [k]
  (get kolam-theme k))

;; Kolam is a geometric floor-art tradition rooted in South India.
;; ^[Definition: Kolam (Tamil: கோலம்) is a South Indian threshold-art practice, often organized around dots and continuous curves, with forms ranging from quick daily motifs to elaborate festival compositions. References: [Kolam (art)](https://en.wikipedia.org/wiki/Kolam), [South India](https://en.wikipedia.org/wiki/South_India).]
;; At first glance, it looks intricate.
;; At second glance, it looks inevitable.
;; The lines seem to know where they are going.

;; ![Welcome kolam at the entrance to a home](kolam-reference.jpg)

;; Many people draw kolams every day as a practice of welcome and care.
;; They are often made with rice flour, which feeds ants and insects, which in turn feed birds.
;; ^[Context: Everyday kolam practice differs by region, household, material, and occasion.]

;; Generative-art often chases novelty first.
;; In kolam practice, continuity usually comes before variation.
;; If you repeat the same rules over time, new variations still appear.

;; Most kolam constructions begin from a dot field.
;; Dots are the quiet part of the story.
;; Curves are the dramatic part.

;; ## What's This About?

;; This article is about a drawing recipe: start with a grid, choose a dot path, and pass by the dots to the side.

;; By the end, you should be able to:

;; - Make interesting visuals from small, deliberate rule changes.
;; - Use a kolam programming model: visit order, tangency, and turns.
;; - Reuse the core helpers in this notebook to design your own variations.

;; This notebook focuses on one geometry-first subset of kolam practice.
;; The code model here is a respectful lens, not a complete account of kolam.

;; We start from a finished kolam, peel it into parts,
;; then recombine those parts.

;; ## Core Idea

;; ::: {.lead}
;; > Kolams are visitation paths through a grid of nodes.
;; > The path moves around dots, not through them.
;; :::

;; Here is the simplest way to think about this system.
;; We shape kolam with two choices and one rule:

;; 1) Visit order chooses structure (which dots connect and where crossings can occur).
;; 2) Swap policy chooses whether you keep the same side at each step or swap sides as you move to the next dot.
;; 3) Wrap rule: once you arrive at a side, the path rounds the dot smoothly before continuing.

(defn fmt [n]
  (format "%.2f" (double n)))

(defn pt [[x y]]
  (str (fmt x) "," (fmt y)))

(defn v+ [[ax ay] [bx by]]
  [(+ ax bx) (+ ay by)])

(defn v* [[x y] s]
  [(* x s) (* y s)])

(defn v- [[ax ay] [bx by]]
  [(- ax bx) (- ay by)])

(defn magnitude [[x y]]
  (Math/sqrt (+ (* x x) (* y y))))

(defn normalize [v]
  (let [m (magnitude v)]
    (if (zero? m)
      [0.0 0.0]
      (v* v (/ 1.0 m)))))

(defn dot->xy
  ([dot]
   (dot->xy dot 10))
  ([[gx gy] spacing]
   [(* spacing (dec gx))
    (* spacing (dec gy))]))

(defn dot->index
  "Row-major node index (1-based) relative to grid width."
  [grid-width [gx gy]]
  (+ gx (* (dec gy) grid-width)))

(defn index-path-str [grid-width dots]
  (str/join " -> " (map #(dot->index grid-width %) dots)))

(defn inner [[ax ay] [bx by]]
  (+ (* ax bx) (* ay by)))

(defn det2d [[ax ay] [bx by]]
  (- (* ax by) (* ay bx)))

(defn turn-sign [dir-in dir-out]
  (let [[ix iy] (normalize dir-in)
        [ox oy] (normalize dir-out)]
    (det2d [ix iy] [ox oy])))

(defn unit-step [from to]
  (let [delta (v- to from)
        length (magnitude delta)]
    (when (zero? length)
      (throw (ex-info "Consecutive dots must be distinct." {:from from :to to})))
    (v* delta (/ 1.0 length))))

(defn side-normal [unit-dir side]
  ;; In SVG's y-down coordinates, this preserves the visual :left/:right convention.
  (let [[fx fy] unit-dir
        left [fy (- fx)]
        right [(- fy) fx]]
    (case side
      :left right
      :right left
      right)))

(defn segment-touch-points [from to radius side sign alternate-tangent-side?]
  (let [u (unit-step from to)
        base-normal (side-normal u side)
        n (v* base-normal sign)]
    (if alternate-tangent-side?
      ;; Opposite-side mode: use the internal common tangent of equal-radius circles.
      (let [dvec (v- to from)
            d (magnitude dvec)
            udir (if (> d 1.0e-9) (v* dvec (/ 1.0 d)) [1.0 0.0])
            c (min 1.0 (/ (* 2.0 radius) (max d 1.0e-9)))
            s (Math/sqrt (max 0.0 (- 1.0 (* c c))))
            v (v* (v+ (v* udir c)
                      (v* n s))
                  radius)]
        {:from (v+ from v)
         :to (v- to v)})
      ;; Same-side mode: use external common tangent (same offset at both ends).
      (let [v (v* n radius)]
        {:from (v+ from v)
         :to (v+ to v)}))))

(defn arc-center-for-flags [from to radius large-arc sweep]
  (let [[x1 y1] from
        [x2 y2] to
        dx (/ (- x1 x2) 2.0)
        dy (/ (- y1 y2) 2.0)
        rx (double radius)
        ry (double radius)
        lambda (+ (/ (* dx dx) (* rx rx))
                  (/ (* dy dy) (* ry ry)))
        scale (if (> lambda 1.0)
                (Math/sqrt lambda)
                1.0)
        rx (* rx scale)
        ry (* ry scale)
        num (- (* rx rx ry ry)
               (* rx rx dy dy)
               (* ry ry dx dx))
        den (+ (* rx rx dy dy)
               (* ry ry dx dx))]
    (if (or (zero? den)
            (< (magnitude (v- from to)) 1.0e-9))
      nil
      (let [sign (if (= large-arc sweep) -1.0 1.0)
            coef (* sign (Math/sqrt (max 0.0 (/ num den))))
            cxp (* coef (/ (* rx dy) ry))
            cyp (* coef (/ (* -1.0 ry dx) rx))
            mx (/ (+ x1 x2) 2.0)
            my (/ (+ y1 y2) 2.0)]
        [(+ cxp mx) (+ cyp my)]))))

(defn choose-arc-flags [center from to dir-in dir-out]
  (let [u-from (normalize (v- from center))
        u-to (normalize (v- to center))
        ;; For y-down coordinates, sweep=1 corresponds to clockwise motion.
        tangent-for-sweep (fn [u sweep]
                            (let [[ux uy] u]
                              (if (= sweep 1)
                                [(- uy) ux]
                                [uy (- ux)])))
        sweep-score (fn [sweep]
                      (+ (inner dir-in (tangent-for-sweep u-from sweep))
                         (inner dir-out (tangent-for-sweep u-to sweep))))
        sweep (if (>= (sweep-score 1) (sweep-score 0)) 1 0)
        radius (magnitude (v- from center))
        candidates (for [large-arc [0 1]
                         :let [arc-center (arc-center-for-flags from to radius large-arc sweep)]
                         :when arc-center]
                     {:large-arc large-arc
                      :sweep sweep
                      :rank-distance (magnitude (v- arc-center center))})
        best (first (sort-by (juxt :rank-distance :large-arc)
                             candidates))]
    (if best
      (select-keys best [:large-arc :sweep])
      {:large-arc 0
       :sweep sweep})))

(defn append-line [state to]
  (update state :parts conj (str "L " (pt to))))

(defn append-arc [state to radius large-arc sweep]
  (update state :parts conj (str "A " (fmt radius) " " (fmt radius) " 0 " large-arc " " sweep " " (pt to))))

(defn normalized-dot-seq [dots]
  (let [dots (vec dots)]
    (if (and (<= 2 (count dots)) (= (first dots) (peek dots)))
      (vec (butlast dots))
      dots)))

(defn viewbox-from-grid
  [{:keys [grid spacing padding]
    :or {spacing 10
         padding 10}}]
  (let [[w h] grid
        span-x (* spacing (max 0 (dec w)))
        span-y (* spacing (max 0 (dec h)))
        vb-x (- padding)
        vb-y (- padding)
        vb-w (+ span-x (* 2 padding))
        vb-h (+ span-y (* 2 padding))]
    (format "%.2f %.2f %.2f %.2f"
            (double vb-x)
            (double vb-y)
            (double vb-w)
            (double vb-h))))

(defn dots-grid
  ([w h]
   (dots-grid w h 10 1 (theme :dot)))
  ([w h spacing]
   (dots-grid w h spacing 1 (theme :dot)))
  ([w h spacing dot-radius]
   (dots-grid w h spacing dot-radius (theme :dot)))
  ([w h spacing dot-radius dot-color]
   [:g {}
    (for [x (range w)
          y (range h)]
      [:circle {:r dot-radius
                :cx (* x spacing)
                :cy (* y spacing)
                :fill dot-color
                :stroke "none"
                :fill-opacity 0.9}])]))

(defn kolam [& args]
  (let [[opts body] (if (map? (first args))
                      [(first args) (rest args)]
                      [{} args])
        {:keys [grid background]} opts
        effective-grid (or grid [3 3])
        resolved-style (cond-> {:width "100%"}
                         background (assoc :background background))
        resolved-viewbox (viewbox-from-grid {:grid effective-grid})]
    (into ^:kind/hiccup [:svg {:viewBox resolved-viewbox
                               :style resolved-style}]
          (concat
           (when grid
             (let [[w h] grid]
               [(dots-grid w h)]))
           body))))

(defn kolam-route-core [points radius side alternate-tangent-side?]
  (let [n (count points)]
    (when (< n 2)
      (throw (ex-info "A closed kolam needs at least two distinct dots." {:count n})))
    (let [segment-count n
          segment-signs (mapv (fn [i]
                                (if (and alternate-tangent-side? (odd? i))
                                  -1.0
                                  1.0))
                              (range segment-count))
          segment-touches (mapv (fn [i]
                                  (segment-touch-points (nth points i)
                                                        (nth points (mod (inc i) n))
                                                        radius
                                                        side
                                                        (nth segment-signs i)
                                                        alternate-tangent-side?))
                                (range segment-count))
          start (get-in segment-touches [(dec n) :to])
          final-anchor start
          initial {:parts [(str "M " (pt start))]
                   :markers [[:start start]]}]
      (loop [i 0
             state initial]
        (if (< i n)
          (let [prev (nth points (mod (dec i) n))
                curr (nth points i)
                next (nth points (mod (inc i) n))
                dir-in (unit-step prev curr)
                dir-out (unit-step curr next)
                turn (turn-sign dir-in dir-out)
                tpin (get-in segment-touches [(mod (dec i) n) :to])
                tpout (get-in segment-touches [i :from])
                {:keys [large-arc sweep]} (choose-arc-flags curr tpin tpout dir-in dir-out)
                tangency-separated? (> (magnitude (v- tpout tpin)) 1.0e-6)
                state-with-markers (update state :markers into [[:tpin tpin] [:tpout tpout]])
                state (if (zero? i)
                        state-with-markers
                        (append-line state-with-markers tpin))]
            (cond
              (zero? turn)
              ;; For collinear segments, arc is the default unless the tangency
              ;; points coincide (degenerate case where line/arc are equivalent).
              (if tangency-separated?
                (recur (inc i) (append-arc state tpout radius large-arc sweep))
                (recur (inc i) state))

              :else
              (recur (inc i) (append-arc state tpout radius large-arc sweep))))
          (let [state (append-line state final-anchor)]
            {:d (str/join "\n" (:parts state))
             :markers (conj (:markers state) [:end final-anchor])
             :segment-touches segment-touches}))))))

(defn kolam-route [dots {:keys [start-side alternate-tangent-side?]
                         :or {start-side :left alternate-tangent-side? false}}]
  (let [normalized-dots (normalized-dot-seq dots)
        points (mapv dot->xy normalized-dots)]
    (kolam-route-core points 3 start-side alternate-tangent-side?)))

(defn kolam-visit
  "Primary renderer."
  ([dots]
   (kolam-visit dots {:stroke     (theme :route-primary)
                      :start-side :left}))
  ([dots {:keys [stroke start-side show-points? show-arcs? show-endpoints? alternate-tangent-side?]
          :or   {stroke                  (theme :route-primary)
                 start-side              :left
                 show-points?            true
                 show-arcs?              true
                 show-endpoints?         true
                 alternate-tangent-side? false}}]
   (let [{:keys [d markers segment-touches]} (kolam-route dots {:start-side              start-side
                                                                :alternate-tangent-side? alternate-tangent-side?})
         point-markers
         (when show-points?
           ;; Marker legend:
           ;; - blue   (:start): path start anchor
           ;; - red    (:end):   path end/closure anchor
           ;; - orange (:tpin):  incoming tangency point at a dot
           ;; - purple (:tpout): outgoing tangency point at a dot
           (concat
            ;; Draw tangent markers first so they keep a uniform appearance.
            (for [[kind [x y]] markers
                  :when        (= kind :tpin)]
              [:circle {:cx     x
                        :cy     y
                        :r      0.55
                        :stroke "none"
                        :fill   (theme :marker-in)}])
            (for [[kind [x y]] markers
                  :when        (= kind :tpout)]
              [:circle {:cx     x
                        :cy     y
                        :r      0.55
                        :stroke "none"
                        :fill   (theme :marker-out)}])
            ;; Draw start/end last so larger blue/red markers remain prominent.
            (for [[k [x y]] markers
                  :when     (and show-endpoints? (= k :start))]
              [:circle {:cx           x
                        :cy           y
                        :r            0.92
                        :stroke       (theme :marker-stroke)
                        :stroke-width 0.18
                        :fill         (theme :marker-start)}])
            (for [[k [x y]] markers
                  :when     (and show-endpoints? (= k :end))]
              [:circle {:cx           x
                        :cy           y
                        :r            0.38
                        :stroke       (theme :marker-stroke)
                        :stroke-width 0.18
                        :fill         (theme :marker-end)}])))
         path-node
         (if show-arcs?
           [:path {:stroke          stroke
                   :stroke-width    (when show-points? 0.9)
                   :stroke-linecap  "round"
                   :stroke-linejoin "round"
                   :fill            "none"
                   :d               d}]
           [:g {}
            (for [{:keys [from to]} segment-touches
                  :let              [[x1 y1] from
                                     [x2 y2] to]]
              [:line {:x1              x1
                      :y1              y1
                      :x2              x2
                      :y2              y2
                      :fill            "none"
                      :stroke          stroke
                      :stroke-width    (when show-points? 0.9)
                      :stroke-linecap  "round"
                      :stroke-linejoin "round"}])])]
     (if show-points?
       [:g {}
        path-node
        point-markers]
       path-node))))

(defn kolam-example
  "Render a kolam plus per-path textual descriptors.
     Paths are maps containing at least :dots, plus any kolam-visit options."
  [{:keys [grid background paths]}]
  (let [[grid-width _] grid
        default-strokes [(theme :route-primary)
                         (theme :route-secondary)
                         (theme :route-tertiary)
                         (theme :marker-start)
                         (theme :marker-end)
                         (theme :wireframe)]
        enriched-paths (map-indexed (fn [i path]
                                      (if (:stroke path)
                                        path
                                        (assoc path :stroke (nth default-strokes
                                                                 (mod i (count default-strokes))))))
                                    paths)]
    ^:kind/hiccup
    [:div {:class "d-grid gap-2"}
     (kolam {:grid grid
             :background background}
            (into [:g {}]
                  (for [{:keys [dots] :as path} enriched-paths]
                    (kolam-visit dots
                                 (merge {:show-points? false}
                                        (dissoc path :dots :label))))))
     [:div {:class "d-grid gap-2"}
      (for [[i {:keys [dots label alternate-tangent-side? start-side stroke]}]
            (map-indexed vector enriched-paths)]
        [:div {:class "card border-start border-4"
               :style (str "border-left-color:" stroke ";")}
         [:div {:class "card-body py-2 px-3"}
          [:div {:class "d-flex flex-wrap align-items-center gap-2 mb-2"}
           [:span {:class "d-inline-block rounded-circle border"
                   :title (str "path color " stroke)
                   :style (str "width:0.85rem;height:0.85rem;background-color:" stroke ";")}]
           [:strong {:class "small"}
            (or label (str "Path " (inc i)))]
           [:span {:class (str "badge rounded-pill "
                               (if alternate-tangent-side?
                                 "text-bg-success"
                                 "text-bg-secondary"))}
            (str "alternating " (if alternate-tangent-side? "yes" "no"))]
           [:span {:class "badge rounded-pill text-bg-light border"}
            (str "start " (name (or start-side :left)))]]
          [:div {:class "font-monospace small text-break"}
           (str "Route: " (index-path-str grid-width dots))]]])]]))


;; ## Glossary

;; This section keeps only the terms needed for the walkthrough.
;; Detailed geometry terms are introduced later in "How the Drawing Model Works."

;; Dot
;; : A grid point in the visit list.

;; Route
;; : Also called visit order: the ordered list of dots we follow (for example, 1 -> 2 -> 1).
;;   The rendered line then wraps around those dots in that order.

;; Visit order
;; : Same sequence as the route.
;;   This defines structure: which dots are connected as neighbors, where revisits occur,
;;   and which crossings are possible.

;; Tangent point
;; : Where a segment touches a dot-circle at distance `radius`.
;;   These are the anchor points used to connect straight segments and smooth arcs.

;; Start side
;; : The side used for the first segment (left or right).
;;   This sets the initial orientation of the path and can flip the visual posture.

;; Alternate tangent side
;; : Leave each dot on one side and arrive at the next dot on the opposite side.
;;   In practice, this often introduces crossings and produces tighter woven-looking routes.


;; ## Guided Walkthrough

;; Here is the outline of what we are about to walk through:

;; 1. Start with one simple route on a dot grid.
;; 2. Make small rule changes and see how the shape responds.
;; 3. Compare a few route families across small dot sets.
;; 4. Reorder the same dots to get different crossings.
;; 5. End by layering multiple routes into one composition.

;; ::: {.callout-note}

;; ### How to Read This Section

;; Every figure in this section is generated from code.
;; You can read this top to bottom like a regular article,
;; or treat it as a lab notebook and run it in your REPL.
;;
;; Source code: [this notebook](https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/art/gen_svg/kolam.clj) and [repository root](https://github.com/ClojureCivitas/clojurecivitas.github.io).
;; :::

;; This is the target pattern we will build up to:

;; ::: {#fig-hero-payoff}
(kolam-example
 {:grid [5 5]
  :background (theme :paper-soft)
  :paths [{:label "Outer ring"
           :dots [[1 1] [2 1] [3 1] [4 1] [5 1]
                  [5 2] [5 3] [5 4] [5 5] [4 5]
                  [3 5] [2 5] [1 5] [1 4] [1 3] [1 2] [1 1]]
           :stroke (theme :route-primary)
           :start-side :left
           :alternate-tangent-side? true
           :show-points? false}
          {:label "Inner ring"
           :dots [[2 2] [3 2] [4 2] [4 3] [4 4] [3 4] [2 4] [2 3] [2 2]]
           :stroke (theme :route-secondary)
           :start-side :right
           :alternate-tangent-side? true
           :show-points? false}
          {:label "Diagonal ring"
           :dots [[3 1] [4 2] [5 3] [4 4] [3 5] [2 4] [1 3] [2 2] [3 1]]
           :stroke (theme :route-tertiary)
           :start-side :left
           :alternate-tangent-side? false
           :show-points? false}]})
;; Destination preview: layered routes sharing one dot field.
;; :::

;; ### Start with the Dot Grid

;; Now start from the simplest version.
;; No curves yet. Just dots.

;; ::: {#fig-dots}
(kolam {:grid [3 3]})
;; Reference grid
;; :::

;; ### Routing Concepts

;; Let's quickly run through how routing concepts work together to construct a kolam.

;; ::: {#fig-build-sequence-strip}
^:kind/hiccup
[:div
 (let [route [[1 1] [2 1] [1 3] [1 2] [3 3] [2 3] [3 1] [3 2] [1 1]]
       center-route (fn [dots]
                      (mapv (fn [[x y]] [(inc x) (inc y)]) dots))
       centered-route (center-route route)
       centered-overlay [[3 1] [4 2] [5 3] [4 4] [3 5] [2 4] [1 3] [2 2] [3 1]]
       route-index-path (index-path-str 3 route)
       polyline-points (->> route
                            (map #(dot->xy % 10))
                            (map pt)
                            (str/join " "))
       frame-row (fn [n title desc fig]
                   [:div {:class "border rounded-2 p-1"
                          :style "display:grid; grid-template-columns:minmax(120px,1fr) minmax(130px,1fr); align-items:center; column-gap:10px;"}
                    [:div
                     [:div {:class "small fw-semibold"}
                      [:span {:class "badge rounded-pill text-bg-light border me-1"} (str n)]
                      title]
                     (when desc
                       [:p {:class "small text-muted mb-0 mt-1"
                            :style "line-height:1.2;"}
                        desc])]
                    [:div
                     [:div {:style "max-width:130px; margin:0 auto;"}
                      fig]]])]
   [:div {:class "d-grid gap-1"}
    (frame-row 1 "Dot grid"
               "A neutral dot field before we draw any route. We can assign each dot an index (1-9) so the path is easy to state."
               (kolam {:grid [3 3]}))
    (frame-row 2 "Visit order"
               (str "The same dots, now connected in the order we will follow: " route-index-path ".")
               (kolam {:grid [3 3]}
                      [:polyline {:points polyline-points
                                  :fill "none"
                                  :stroke (theme :wireframe)
                                  :stroke-width 0.8
                                  :stroke-linecap "round"
                                  :stroke-linejoin "round"}]))
    (frame-row 3 "Tangency"
               "The two marker colors show tangent touch points on neighboring dot-circles. Each straight connector runs from an outgoing touch point to the next incoming touch point, which sets up smooth curve transitions."
               (kolam {:grid [3 3]}
                      [:g {}
                       (kolam-visit route {:stroke (theme :route-primary)
                                           :start-side :left
                                           :alternate-tangent-side? false
                                           :show-arcs? false
                                           :show-points? true
                                           :show-endpoints? false})]))
    (frame-row 4 "Smooth turns"
               "Arc joins replace the straight tangency connectors, producing a continuous flowing route."
               (kolam {:grid [3 3]}
                      (kolam-visit route {:stroke (theme :route-primary)
                                          :start-side :left
                                          :alternate-tangent-side? false
                                          :show-points? false})))
    (frame-row 5 "Alternate sides"
               "Tangency alternates by segment, which changes crossings and shape."
               (kolam {:grid [3 3]}
                      (kolam-visit route {:stroke (theme :route-primary)
                                          :start-side :left
                                          :alternate-tangent-side? true
                                          :show-points? false})))
    (frame-row 6 "Layering"
               "Reuse the previous route, then overlay a second symmetric route to build a richer composition."
               (kolam {:grid [5 5]}
                      [:g {}
                       (kolam-visit centered-route {:stroke (theme :route-primary)
                                                    :start-side :left
                                                    :alternate-tangent-side? true
                                                    :show-points? false})
                       (kolam-visit centered-overlay
                                    {:stroke (theme :route-secondary)
                                     :start-side :right
                                     :alternate-tangent-side? true
                                     :show-points? false})]))])]
;; A six-concept overview, from dots to a layered pattern.
;; :::

;; ### Comparing Side Alternation

;; The first two panels use a minimal two-node route.
;; The third and fourth use a longer multi-node route to show how side policy changes complex paths.

;; ::: {#fig-policy-comparison-grid}
^:kind/hiccup
[:div {:style "display:grid; grid-template-columns:repeat(4,1fr); gap:8px;"}
 [:div
  (kolam {:grid [3 3]}
         (kolam-visit [[1 1] [2 1] [1 1]]
                      {:start-side :left
                       :alternate-tangent-side? false
                       :show-points? false}))
  [:div {:style "font-size:0.8rem; text-align:center; color:#374151;"} "same-side"]]
 [:div
  (kolam {:grid [3 3]}
         (kolam-visit [[1 1] [2 1] [1 1]]
                      {:start-side :left
                       :alternate-tangent-side? true
                       :show-points? false}))
  [:div {:style "font-size:0.8rem; text-align:center; color:#374151;"} "alternating-side"]]
 [:div
  (kolam {:grid [3 3]}
         (kolam-visit [[1 1] [2 1] [1 3] [1 2] [3 3] [2 3] [3 1] [3 2] [1 1]]
                      {:start-side :right
                       :alternate-tangent-side? true
                       :show-points? false}))
  [:div {:style "font-size:0.8rem; text-align:center; color:#374151;"} "complex alternating"]]
 [:div
  (kolam {:grid [3 3]}
         (kolam-visit [[1 1] [2 1] [1 3] [1 2] [3 3] [2 3] [3 1] [3 2] [1 1]]
                      {:start-side :right
                       :alternate-tangent-side? false
                       :show-points? false}))
  [:div {:style "font-size:0.8rem; text-align:center; color:#374151;"} "complex same-side"]]]
;; Comparison of side alternation choice.
;; :::

;; ### Revisiting dots

;; Paths can revisit the same dot.

;; Consider the path: 1 -> 2 -> 3 -> 2 -> 1.
;; Revisiting the middle node is intentional.
;; The return pass uses the opposite side of that node.

;; ::: {#fig-three-dots-alternating-revisit}
(kolam-example
 {:grid [3 3]
  :paths [{:label "Three-dot alternating revisit"
           :dots [[1 1] [2 1] [3 1] [2 1] [1 1]]
           :start-side :left
           :alternate-tangent-side? true
           :show-points? false}]})
;; Three-dot alternating route with a deliberate revisit at the middle node.
;; :::

;; These all intentionally revisit the center, showing how "double-backing"
;; can produce useful structure.

;; ::: {#fig-revisit-motifs}
^:kind/hiccup
[:div {:style "display:grid; grid-template-columns:repeat(3,1fr); gap:8px;"}
 [:div
  (kolam {:grid [3 3]}
         (kolam-visit [[1 1] [2 2] [3 3] [2 2]]
                      {:start-side :left
                       :alternate-tangent-side? true
                       :show-points? false}))
  [:div {:style "font-size:0.8rem; text-align:center; color:#374151;"} "diag revisit"]]
 [:div
  (kolam {:grid [3 3]}
         (kolam-visit [[1 2] [2 2] [3 2] [2 2]]
                      {:start-side :left
                       :alternate-tangent-side? true
                       :show-points? false}))
  [:div {:style "font-size:0.8rem; text-align:center; color:#374151;"} "horizontal revisit"]]
 [:div
  (kolam {:grid [3 3]}
         (kolam-visit [[2 1] [2 2] [2 3] [2 2]]
                      {:start-side :left
                       :alternate-tangent-side? true
                       :show-points? false}))
  [:div {:style "font-size:0.8rem; text-align:center; color:#374151;"} "vertical revisit"]]]
;; Three revisit motifs centered on one reused node.
;; :::


;; ### Overlaying paths

;; Rather than restricting ourselves to one path,
;; we can combine multiple paths in one kolam.

;; Here's an example of combining a diagonal cross and an inner cross
;; to get a star-like crossing field.

;; ::: {#fig-star-overlay-revisit}
(kolam-example
 {:grid [3 3]
  :paths [{:label "Diagonal cross"
           :dots [[1 1] [2 2] [3 3] [2 2] [1 3] [2 2] [3 1] [2 2] [1 1]]
           :stroke (theme :route-primary)
           :start-side :right
           :alternate-tangent-side? true}
          {:label "Inner cross"
           :dots [[1 2] [2 2] [2 1] [2 2] [3 2] [2 2] [2 3] [2 2]]
           :stroke (theme :route-secondary)
           :start-side :right
           :alternate-tangent-side? true}]})
;; Star-style overlay from a diagonal cross and an inner cross.
;; :::

;; Here is a more complex overlay of multiple spokes that share one center,
;; and double back through that center on the opposite side.

;; ::: {#fig-radial-revisit-plate}
(kolam-example
 {:grid [5 5]
  :paths [{:label "Diagonal cross"
           :dots [[2 2] [3 3] [4 4] [3 3] [2 4] [3 3] [4 2] [3 3] [2 2]]
           :stroke (theme :route-primary)
           :start-side :left
           :alternate-tangent-side? true}
          {:label "North-south cross"
           :dots [[3 1] [3 3] [3 5] [3 3] [1 3] [3 3] [5 3] [3 3] [3 1]]
           :stroke (theme :route-secondary)
           :start-side :right
           :alternate-tangent-side? true}
          {:label "Short cross"
           :dots [[3 2] [3 3] [4 3] [3 3] [2 3] [3 3] [3 4] [3 3] [3 2]]
           :stroke (theme :route-tertiary)
           :start-side :left
           :alternate-tangent-side? true}]})
;; Center-driven spokes.
;; :::


;; ### Visualizing Control Points and Route Structure

;; ::: {.callout-tip}

;; #### Control Point Legend

;; - **Start anchor** (blue): where the path begins (large marker)
;; - **End anchor** (red): where the path closes (small marker)
;; - **Tangent in** (coral): incoming tangency point at a dot (marker-in)
;; - **Tangent out** (mint): outgoing tangency point at a dot (marker-out)
;;
;; (Colors: blue = marker-start, red = marker-end, coral = marker-in, mint = marker-out)
;; :::


;; ::: {#fig-baseline-multi-dot-route}
(kolam-example
 {:grid [3 3]
  :paths [{:label "Baseline route"
           :dots [[1 1] [2 1] [1 3] [1 2] [3 3]
                  [2 3] [3 1] [3 2] [1 1]]
           :start-side :left
           :show-points? true}]})
;; Baseline multi-dot route using left-side tangency.
;; :::


;; #### Toggle Alternation on the Baseline Route


;; ::: {#fig-alternating-variant-baseline}
(kolam-example
 {:grid [3 3]
  :paths [{:label "Baseline alternating"
           :dots [[1 1] [2 1] [1 3] [1 2] [3 3]
                  [2 3] [3 1] [3 2] [1 1]]
           :start-side :left
           :alternate-tangent-side? true
           :show-points? true}]})
;; Baseline route with alternating tangent side enabled.
;; :::


;; #### Switch the Start Side to Right


;; ::: {#fig-right-start-variant}
(kolam-example
 {:grid [3 3]
  :paths [{:label "Baseline right-start"
           :dots [[1 1] [2 1] [1 3] [1 2] [3 3]
                  [2 3] [3 1] [3 2] [1 1]]
           :start-side :right
           :show-points? true}]})
;; Right-start variant of the baseline route.
;; :::


;; #### Combine Right Start with Alternation


;; In this variant, segment exits and next-dot entries use opposite sides.

;; ::: {#fig-right-start-alternating}
(kolam-example
 {:grid [3 3]
  :paths [{:label "Right-start alternating"
           :dots [[1 1] [2 1] [1 3] [1 2] [3 3]
                  [2 3] [3 1] [3 2] [1 1]]
           :start-side :right
           :alternate-tangent-side? true
           :show-points? true}]})
;; Right-start route with alternating segment-side tangency.
;; :::


;; #### Change the Visit Order


;; This different visit order creates different crossings.

;; ::: {#fig-different-visit-order}
(kolam-example
 {:grid [3 3]
  :paths [{:label "Different visit order"
           :dots [[1 1] [3 2] [1 3] [2 1] [3 3]
                  [1 2] [3 1] [2 3] [1 1]]
           :start-side :right
           :show-points? true}]})
;; Different visit order to highlight structure and crossing changes.
;; :::


;; #### Layered Patterns

;; Layered 4x4 motif: three symmetric routes in one composition.
;; This avoids forcing one long stroke and gives cleaner radial structure.

;; ::: {#fig-larger-grid-denser-weave}
(kolam-example
 {:grid [4 4]
  :background (theme :paper-soft)
  :paths [{:label "Outer orbit"
           :dots [[1 1] [2 1] [3 1] [4 1]
                  [4 2] [4 3] [4 4] [3 4]
                  [2 4] [1 4] [1 3] [1 2] [1 1]]
           :stroke (theme :route-tertiary)
           :start-side :left
           :alternate-tangent-side? true
           :show-points? true}
          {:label "Inner orbit"
           :dots [[2 2] [3 2] [3 3] [2 3] [2 2]]
           :stroke (theme :route-secondary)
           :start-side :right
           :alternate-tangent-side? true
           :show-points? true}
          {:label "Cross-radius orbit"
           :dots [[2 1] [3 1] [4 2] [4 3]
                  [3 4] [2 4] [1 3] [1 2] [2 1]]
           :stroke (theme :route-primary)
           :start-side :left
           :alternate-tangent-side? false
           :show-points? true}]})
;; Layered 4x4 composition with perimeter, core, and cross-radius routes.
;; :::


;; ### Composition Insights

;; Two practical discoveries emmerged while creating this gallery.
;; Neither is fancy; both matter a lot in practice.

;; 1) Not visiting the center can improve balance.
;; Leaving it empty creates breathing room and makes symmetry easier.
;; This only applies to odd sized grids, as even sized grids have no dot at the center.

;; 2) More than one unconnected path is often cleaner than one forced stroke.
;; Forcing continuity can create awkward detours and uneven density.
;; Splitting into coordinated paths gives better control:
;; one can carry perimeter rhythm, another can shape the core,
;; and a third can tie them together.


(def outer-ring
  [[1 1] [2 1] [3 1] [4 1] [5 1]
   [5 2] [5 3] [5 4] [5 5] [4 5]
   [3 5] [2 5] [1 5] [1 4] [1 3] [1 2] [1 1]])

(def inner-ring
  [[2 2] [3 2] [4 2] [4 3] [4 4] [3 4] [2 4] [2 3] [2 2]])

(def diamond-ring
  [[3 1] [4 2] [5 3] [4 4] [3 5] [2 4] [1 3] [2 2] [3 1]])

(def corner-petals
  [[[1 1] [2 1] [1 1]]
   [[5 1] [4 1] [5 1]]
   [[5 5] [4 5] [5 5]]
   [[1 5] [2 5] [1 5]]])

(def side-petals
  [[[3 1] [3 2] [3 1]]
   [[5 3] [4 3] [5 3]]
   [[3 5] [3 4] [3 5]]
   [[1 3] [2 3] [1 3]]])

(def gallery-motifs
  ;; Each motif is a vector of routes rendered together in one kolam tile.
  [[outer-ring inner-ring]
   [outer-ring diamond-ring]
   (into [outer-ring inner-ring] corner-petals)
   (into [inner-ring diamond-ring] side-petals)])

(defn transform-route [w h xf route]
  ;; Reflect or rotate a route of [col row] coordinates within a w×h grid.
  (mapv (fn [[c r]]
          (case xf
            :id      [c r]
            :rot-90  [(- (inc h) r) c]
            :flip-h  [(- (inc w) c) r]
            :flip-v  [c (- (inc h) r)]
            :rot-180 [(- (inc w) c) (- (inc h) r)]
            :rot-270 [r (- (inc w) c)]))
        route))

(defn swap-point [p a b]
  (cond
    (= p a) b
    (= p b) a
    :else p))

(defn perturb-point [mode p]
  ;; Symmetry-preserving pair swaps for row-wise variation.
  (case mode
    :identity p
    :inner-lr (-> p
                  (swap-point [2 2] [4 2])
                  (swap-point [2 3] [4 3])
                  (swap-point [2 4] [4 4]))
    :inner-tb (-> p
                  (swap-point [2 2] [2 4])
                  (swap-point [3 2] [3 4])
                  (swap-point [4 2] [4 4]))
    :diagonal-twist (-> p
                        (swap-point [2 2] [4 4])
                        (swap-point [2 4] [4 2])
                        (swap-point [1 3] [3 1])
                        (swap-point [3 5] [5 3]))
    p))

(defn perturb-route [mode route]
  (mapv #(perturb-point mode %) route))

(def gallery-row-recipes
  [{:xf :id
    :perturb :identity
    :opts {:start-side :left :alternate-tangent-side? true}}
   {:xf :rot-90
    :perturb :inner-lr
    :opts {:start-side :left :alternate-tangent-side? true}}
   {:xf :rot-180
    :perturb :inner-tb
    :opts {:start-side :right :alternate-tangent-side? true}}
   {:xf :rot-270
    :perturb :diagonal-twist
    :opts {:start-side :right :alternate-tangent-side? false}}])

;; ::: {#fig-route-gallery}
^:kind/hiccup
[:div {:style "display:grid; grid-template-columns:repeat(4,1fr); gap:6px;"}
 (for [i (range 4)
       j (range 4)]
   (let [{:keys [xf perturb opts]} (nth gallery-row-recipes i)
         routes (mapv #(->> %
                            (transform-route 5 5 xf)
                            (perturb-route perturb))
                      (nth gallery-motifs j))]
     [:div
      (kolam {:grid [5 5]
              :background (theme :paper)}
             [:g {}
              (for [route routes]
                (kolam-visit route (assoc opts
                                          :show-points? false)))])]))]
;; 4x4 route gallery over a 5x5 grid with symmetric transforms and perturbations.
;; :::

;; ## Why I Built This

;; A big reason I got interested in kolam is educational: finding better ways
;; to invite young people into programming. Turtle graphics is a great
;; starting point, but kolam feels more immediately compelling.
;; Both approaches are built from a small, logical set of primitives.
;; ^[Context: Turtle-graphics learning traditions show how simple movement rules can produce expressive structure. That background helps explain why kolam modeling can be taught through explicit movement choices such as visit order, side policy, and turn rules. References: [Mindstorms (Papert)](https://en.wikipedia.org/wiki/Mindstorms:_Children,_Computers,_and_Powerful_Ideas), [Turtle graphics](https://en.wikipedia.org/wiki/Turtle_graphics).]

;; Kolam is a promising programming canvas.
;; Visit order and side alternation policy are explicit and composable.
;; One big downside is pedagogical: turtle tasks offer more obvious
;; on-ramps for introducing repetition and functions than kolams.
;; Kolams have many rich graph concepts to explore,
;; however it's not immediately clear how to motivate basic programming features like functions.

;; ## Kolam, Script, and Context

;; This work also clarified a cultural difference in emphasis. A lot of Western
;; "generative art" language centers novelty, surprise, and style exploration.
;; Kolam practice often centers continuity, repetition, care, and daily ritual,
;; where variation stays within a rule template.

;; Kolam is a Tamil word (கோலம், kolam), and the practice is
;; deeply rooted in Tamil culture while also appearing across South India under
;; related regional names.
;; ^[Note: kolam terminology is culturally situated and partially overlaps with broader South Asian threshold-art terms. Usage in this article: "kolam" refers to Tamil-rooted practices that motivate these geometric examples. References: [Tamil language](https://en.wikipedia.org/wiki/Tamil_language), [Kolam (art)](https://en.wikipedia.org/wiki/Kolam), [Rangoli](https://en.wikipedia.org/wiki/Rangoli).]

;; At the letterform level, kolam geometry resonates with the
;; visual rhythm of Tamil script, which uses many rounded, loop-like curves.
;; Tamil is a Dravidian language primarily spoken in Tamil Nadu and Puducherry in
;; southern India, and also widely spoken in Sri Lanka and the global Tamil
;; diaspora (including Singapore, where it is an official language).
;; During Margazhi^[Definition: Margazhi is a month in the Tamil calendar, about mid-December to mid-January. Reference: [Tamil calendar](https://en.wikipedia.org/wiki/Tamil_calendar).],
;; kolam practice becomes especially visible in many Tamil communities, with
;; larger and more elaborate threshold drawings. Alongside classical pulli/sikku
;; forms, artists also explore Tamil letter kolam compositions (ezhuthu kolam),
;; shaping letters such as "அ" or "உ", and sometimes words like "தமிழ்",
;; through dot-based curve grammars.
;; ^[Definition: pulli kolam uses dots as structural anchors; sikku kolam emphasizes continuous knot-like looping around dots; ezhuthu kolam composes forms from script-like letter shapes. Classification note: these terms distinguish families of construction rules.]
;; One short phrase that fits the spirit here is:

;; "தமிழ் எழுத்து வளைவுகள் நிறைந்தது" (Tamil script is rich with curves).

;; Tamil script and kolam both tend toward rounded, continuous strokes.

;; Kolam is interesting because small local choices create strong global
;; structure. The same dot field can produce very different outcomes by changing
;; only visit order, tangent-side policy, or turn handling. It sits at a useful
;; intersection of craft and geometry: you can reason about continuity and
;; closure like a formal system, while still working by eye for rhythm,
;; proportion, and balance.

;; ## Embellishments

;; Kolams often include embellishments beyond the base path structure.
;; Here we explore one way to add restrained decorative motifs
;; while keeping the same geometry model.

;; ::: {#fig-quick-embellishments}
(kolam {:grid [6 6]
        :background (theme :paper-soft)}
       (let [leaf-path "M 0 -3.0 C 0.70 -1.30 0.70 1.30 0 3.0 C -0.70 1.30 -0.70 -1.30 0 -3.0 Z"
             petal-stroke (theme :route-secondary)
             center-angles [0 30 60 90 120 150 180 210 240 270 300 330]
             center-radius 4.1
             center-petal-specs
             (for [a center-angles
                   :let [theta (Math/toRadians a)
                         ox (* center-radius (Math/cos theta))
                         oy (* center-radius (Math/sin theta))
                         rot (+ a 90)]]
               [(+ 25 ox) (+ 25 oy) rot])
             outer-angles [0 60 120 180 240 300]
             outer-radius 3.2
             outer-flower-centers (mapv #(dot->xy % 10) [[2 2] [5 2] [2 5] [5 5]])
             outer-petal-specs
             (mapcat (fn [[cx cy]]
                       (for [a outer-angles
                             :let [theta (Math/toRadians a)
                                   ox (* outer-radius (Math/cos theta))
                                   oy (* outer-radius (Math/sin theta))
                                   rot (+ a 90)]]
                         [(+ cx ox) (+ cy oy) rot]))
                     outer-flower-centers)]
         (into [:g {}
                (kolam-visit [[1 1] [2 1] [3 1] [4 1] [5 1] [6 1]
                              [6 2] [6 3] [6 4] [6 5] [6 6] [5 6]
                              [4 6] [3 6] [2 6] [1 6] [1 5] [1 4]
                              [1 3] [1 2] [1 1]]
                             {:stroke (theme :route-primary)
                              :start-side :left
                              :alternate-tangent-side? true
                              :show-points? false})
                (kolam-visit [[2 2] [3 2] [4 2] [5 2] [5 3]
                              [5 4] [5 5] [4 5] [3 5] [2 5]
                              [2 4] [2 3] [2 2]]
                             {:stroke (theme :route-tertiary)
                              :start-side :right
                              :alternate-tangent-side? true
                              :show-points? false})
                (kolam-visit [[3 1] [4 2] [5 3] [6 4] [5 5]
                              [4 6] [3 5] [2 4] [1 3] [2 2] [3 1]]
                             {:stroke (theme :route-secondary)
                              :start-side :left
                              :alternate-tangent-side? false
                              :show-points? false})]
               (concat
                [[:circle {:cx 25 :cy 25 :r 4.0
                           :fill "none"
                           :stroke (theme :route-secondary)
                           :stroke-width 0.30
                           :stroke-opacity 0.75}]
                 [:circle {:cx 25 :cy 25 :r 1.0
                           :fill (theme :route-secondary)
                           :fill-opacity 0.70
                           :stroke "none"}]
                 [:circle {:cx 10 :cy 10 :r 0.85 :fill (theme :route-tertiary) :fill-opacity 0.60 :stroke "none"}]
                 [:circle {:cx 40 :cy 10 :r 0.85 :fill (theme :route-tertiary) :fill-opacity 0.60 :stroke "none"}]
                 [:circle {:cx 10 :cy 40 :r 0.85 :fill (theme :route-tertiary) :fill-opacity 0.60 :stroke "none"}]
                 [:circle {:cx 40 :cy 40 :r 0.85 :fill (theme :route-tertiary) :fill-opacity 0.60 :stroke "none"}]]
                (for [[cx cy rot] center-petal-specs]
                  [:g {:transform (format "translate(%s %s) rotate(%s)" cx cy rot)}
                   [:path {:d leaf-path
                           :fill (theme :route-secondary)
                           :fill-opacity 0.10
                           :stroke petal-stroke
                           :stroke-width 0.44
                           :stroke-opacity 0.90}]])
                (for [[cx cy rot] outer-petal-specs]
                  [:g {:transform (format "translate(%s %s) rotate(%s)" cx cy rot)}
                   [:path {:d leaf-path
                           :fill (theme :route-tertiary)
                           :fill-opacity 0.08
                           :stroke (theme :route-tertiary)
                           :stroke-width 0.36
                           :stroke-opacity 0.88}]])))))
;; Layered kolam with a center flower and four outer floral accents.
;; :::


;; ## How the Drawing Model Works

;; This section explains how the code draws kolam paths.
;; It covers one geometric subset of kolam practice:
;; dot-avoiding linework, and line/arc segments.

;; When the path turns, the code must choose between possible arc directions and sizes to ensure smooth, continuous curves.
;; The SVG `sweep` and `large-arc` flags control these choices, determining whether the arc bends clockwise or counterclockwise, and whether it takes the shorter or longer route between points.

;; ::: {.callout-tip}

;; ### Quick Definitions

;; - External tangent: touches two equal circles on the same side.
;; - Internal tangent: touches two equal circles on opposite sides.
;; - Segment: the connection (curve or line) between two consecutive dots in the route.
;; - Tangency: the property that each segment touches the dot-circle at a single, smooth point, ensuring a continuous path.
;; - Arc: a curved segment (SVG arc) used to smoothly connect tangency points when the path turns.
;; - Closure: the property that a closed route rejoins its starting anchor, forming a continuous loop.
;; - Collinear/turn: collinear means the path continues straight through a dot; a turn means the path changes direction at the dot.
;; - `sweep` flag (SVG arc): chooses clockwise vs counter-clockwise direction.
;; - `large-arc` flag (SVG arc): chooses shorter vs longer arc branch.
;; - C0 continuity: the path stays connected (no gaps).
;; - G1 continuity: tangent direction matches at smooth joins (visual smoothness).
;; :::

;; ### Path View and Coordinate View

;; Two equivalent views describe the same route:

;; - Path view: an ordered visit list over indexed dots (for example 1 -> 2 -> 1).
;; - Coordinate view: the same dots written as positions.
;;   With spacing s, [gx gy] maps to [(gx-1)*s, (gy-1)*s].
;;   Example: index 1 -> [1 1] -> [0 0].

;; Path view is usually easier for design.
;; Coordinate view is what geometry and SVG commands use.
;; In other words, path order drives structure, while coordinates drive drawing.

;; Worked micro-example (2-dot baseline intuition):

;; - Dots: [1 1] -> [2 1] -> [1 1], spacing=10, radius=3, start side=:left.
;; - Centers become [0,0] and [10,0], so segment directions are horizontal.
;; - Same-side mode gives a pill-like envelope around both dots.
;; - Alternating mode gives a figure-eight crossing.
;; - Visit order is identical in both cases; tangent-side policy is what changes.

;; Output shape:

;; - Input: ordered dots [gx gy], with spacing, radius, and side policy.
;; - Output: an SVG path composed of absolute M/L/A commands.
;; ^[Definition: SVG path commands M, L, and A encode move, line, and arc primitives. Implementation link: this model's kolam flow is built directly from those primitives, so geometry choices map transparently to SVG syntax. Reference: [MDN SVG path](https://developer.mozilla.org/en-US/docs/Web/SVG/Tutorial/Paths).]

;; ### Segment Tangency Rules

;; For each segment from dot[i] to dot[i+1], compute touchpoints together
;; (segment-touch-points), one on each dot-circle.

;; - Same-side mode (alternate-tangent-side? = false): use the external common tangent.
;; - Alternating mode (alternate-tangent-side? = true): use the internal common tangent.
;; ^[Definition: equal-radius circle pairs admit external (same-side) and internal (opposite-side) common tangents. Geometric consequence: the alternation toggle switches between these tangent families, directly changing crossing behavior. Reference: [Tangent lines to circles](https://en.wikipedia.org/wiki/Tangent_lines_to_circles).]

;; This segment-level construction is the key stability rule:
;; connector lines are tangent by construction.


;; ### Turn Rules

;; At each interior dot, we look at the direction coming in and the direction going out:
;;
;; - If the path continues straight (the directions are collinear), we simply connect with a straight segment (unless the tangency points coincide, in which case we may skip the arc).
;; - If the path turns, we draw a smooth arc to connect the outgoing and incoming tangency points.
;;
;; The path draws a line to the incoming tangency point, then an arc to the outgoing tangency point when needed.
;;
;; - The direction (sweep) and size (large-arc) of the arc are chosen to ensure a smooth, continuous path.
;; - U-turns and other special cases reuse the same arc-selection logic.

;; ### Closure and Guarantees

;; - Start anchor is the previous segment's final touchpoint.
;; - Closed routes rejoin that start anchor by construction.
;; - Path continuity is C0 everywhere and G1 at non-collinear line/arc joins.
;; ^[Definition: C0 means no gaps; G1 means tangent direction matches across joins even if curvature magnitude changes. Visual result: linework reads as continuous and intentional, with smooth transitions. Reference: [Smoothness](https://en.wikipedia.org/wiki/Smoothness).]
;; - radius < spacing / 2 is a local clearance heuristic, not a full
;;   non-self-intersection guarantee.
;; - Traversal order choice is independent from geometric rendering rules.


;; ## Conclusion

;; We worked through a clear geometric framework for constructing kolam patterns.
;; Paths are represented as ordered visits to dots.
;; Tangency at each segment ensures mathematical precision and visual continuity.
;; Arcs are used to handle turns smoothly.
;; The notebook enables study, modification, and generation of kolam designs.

;; Kolams are a study in creativity, care, and connection.
;; Working with these forms brings both mathematical satisfaction and a sense of beauty.
;; I hope this notebook helps you appreciate and enjoy the art of kolam.
