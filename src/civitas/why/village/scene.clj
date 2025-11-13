^:kindly/hide-code
(ns civitas.why.village.scene
  {:clay {:title          "The Search for Meaning Through Collaboration and Code"
          :kindly/options {:kinds-that-hide-code #{:kind/var :kind/hiccup}}
          :quarto         {:author   [:timothypratley]
                           ;;:description ""
                           :page-layout :full
                           :theme "none"
                           :navbar   false
                           :draft    true
                           :type     :page
                           :date     "2025-11-12"
                           :category :collaboration
                           :tags     [:clojure :writing :workflow :motivation :community]}}}
  (:require [civitas.db :as db]
            [civitas.explorer.geometry :as geom]
            [civitas.explorer.svg :as svg]
            [civitas.why.village.color :as color]
            [civitas.why.village.markup :as mu]
            [clojure.math :as math]
            [clojure.string :as str]
            [scicloj.kindly.v4.kind :as kind]))

(kind/hiccup
  '([:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.js"}]
    [:script {:src  "traction.cljs"
              :type "application/x-scittle"}]
    [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
    [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "true"}]
    [:link {:href "https://fonts.googleapis.com/css2?family=Luxurious+Roman&display=swap" :rel "stylesheet"}]
    [:style "body {
    background: darkgreen;
    font-family: \"Luxurious Roman\", \"Times New Roman\", Times, Georgia, serif;
  }
  h1,h2,h3,h4,h5 {
    font-family: \"Luxurious Roman\", \"Times New Roman\", Times, Georgia, serif;
  }
  table {
    width: 100%;
}
  pre {
    filter: invert(25%) hue-rotate(180deg);
  }
"]))

(def col
  {:white   "#FFFFFF"
   :dgreen  "#62B132"
   :dblue   "#5881D8"
   :lgreen  "#91DC47"
   :lblue   "#8FB5FE"
   :cred    "#F26767"
   :gyellow "#FFCD52"
   :cbrown  "#A86F40"
   :lgray   "#E0E0E0"
   :mgray   "#808080"
   :ddblue  "#2F4179"
   :sstone  "#D6D6D6"
   :swhite  "#F0F4F8"})

(def defs
  [:defs
   [:clipPath {:id "hex"}
    [:polygon {:points (str/join " "
                                 (map #(str/join "," %) (geom/hex 5)))}]]
   [:pattern#writing {:width "2" :height "2"
                      :patternUnits "userSpaceOnUse"}
    [:path {:d "M0,1 L2,1" :stroke "#444" :stroke-width "0.05" :opacity "0.4"}]
    [:path {:d "M0,1.2 L2,1.2" :stroke "#666" :stroke-width "0.05" :opacity "0.3"}]
    [:path {:d "M0,0.8 L2,0.8" :stroke "#333" :stroke-width "0.03" :opacity "0.5"}]]
   [:pattern#cobble {:width            "2" :height "2"
                     :patternUnits     "userSpaceOnUse"
                     :patternTransform "scale(1,0.5) rotate(30)"}
    [:rect {:x "0" :y "0" :width "2" :height "2" :fill "#d9d9d9"}]
    [:rect {:x "1" :y "1" :width "1" :height "1" :fill "#bfbfbf"}]]])

(def √3 (math/sqrt 3))
(def sin30 0.5)
(def cos30 (/ √3 2.0))
(def π2 (/ math/PI 2.0))

(defn iso [[x y z]]
  [(* (- x y) cos30)
   (+ (* (+ x y) sin30) (- z))])

(defn circle-verts [r n]
  (let [angle-step (/ (* 2 math/PI) n)
        camera (- math/PI (/ math/PI 4))]
    (vec (for [i (range n)]
           [(* r (math/cos (+ camera (* i angle-step))))
            (* r (math/sin (+ camera (* i angle-step))))
            0]))))

(defn translate-verts [vs translation]
  (mapv (fn [pts]
          (mapv + pts translation))
        vs))

(defn rotate-verts [vs [rx ry rz]]
  (let [cosx (math/cos rx), sinx (math/sin rx)
        cosy (math/cos ry), siny (math/sin ry)
        cosz (math/cos rz), sinz (math/sin rz)]
    (mapv
      (fn [[x y z]]
        (let [[y z] [(+ (* y cosx) (* z (- sinx)))
                     (+ (* y sinx) (* z cosx))]
              [x z] [(+ (* x cosy) (* z siny))
                     (+ (* z cosy) (* (- x) siny))]
              [x y] [(+ (* x cosz) (* (- y) sinz))
                     (+ (* x sinz) (* y cosz))]]
          [x y z]))
      vs)))

(defn aligned-faces [n offset]
  (for [i (range n)]
    [(+ offset i)
     (+ offset (mod (inc i) n))
     (+ offset n (mod (inc i) n))
     (+ offset n i)]))

(defn circle-mesh [r n color role]
  [:mesh {:role     role
          :vertices (circle-verts r n)
          :faces    [(vec (range n))]
          :colors   (repeat n color)}])

(defn house-mesh [w d h rh]
  [:mesh {:role     "house"
          :vertices [[0 0 0] [w 0 0] [w d 0] [0 d 0]        ; bottom
                     [0 0 h] [w 0 h] [w d h] [0 d h]        ; top of walls
                     [0 (/ d 2) (+ h rh)] [w (/ d 2) (+ h rh)]] ; roof ridge
          :faces    [[0 1 2 3]                              ; floor
                     [3 0 4 7]                              ; back side wall NS
                     [2 3 7 6]                              ; back wall EW
                     [0 1 5 4]                              ; front wall EW
                     [1 2 6 5]                              ; front side wall NS

                     ;; roof faces
                     [7 4 8]                                ; back-left triangle
                     [6 7 8 9]                              ; back roof
                     [4 5 9 8]                              ; front roof
                     [5 6 9]                                ; front-right triangle
                     ]
          :colors   [(:ddblue col)
                     (:gyellow col)
                     (:gyellow col)
                     (:gyellow col)
                     (:gyellow col)
                     (:cred col)
                     (:cred col)
                     (:cred col)
                     (:cred col)]}])

(defn granary-mesh [r h rh n]
  (let [bottom-verts (circle-verts r n)
        top-verts (-> (circle-verts r n)
                      (translate-verts [0 0 h]))
        cone-top [0 0 (+ h rh)]
        side-faces (aligned-faces n 0)
        bottom-face (vec (range n))
        cone-faces (for [i (range n)]
                     [(+ i n) (+ (mod (inc i) n) n) (+ n n)])]
    [:mesh {:role     "granary"
            :vertices (vec (concat bottom-verts top-verts [cone-top]))
            :faces    (vec (concat [bottom-face] side-faces cone-faces))
            :colors   (vec (concat
                             [(:ddblue col)]
                             (repeat n (:gyellow col))
                             (repeat n (:cred col))))}]))

(defn aqueduct-mesh [length width height arch-width n]
  (let [segment-len (/ length n)
        ;; arch vertices
        pillar-verts
        (vec (mapcat
               (fn [i]
                 (let [x0 (+ (* i segment-len) (* 0.5 segment-len))
                       x1 (+ x0 arch-width)
                       ;; front leg
                       v0 [x0 0 0] v1 [x1 0 0] v2 [x1 0 height] v3 [x0 0 height]
                       ;; back leg
                       v4 [x0 width 0] v5 [x1 width 0] v6 [x1 width height] v7 [x0 width height]]
                   [v0 v1 v2 v3 v4 v5 v6 v7]))
               (range n)))
        ;; top beam vertices
        top-verts [[0 0 height] [length 0 height] [length width height] [0 width height]
                   [0 0 (+ height 1.0)] [length 0 (+ height 1.0)] [length width (+ height 1.0)] [0 width (+ height 1.0)]]
        vertices (into pillar-verts top-verts)
        ;; helper to offset indices
        offset-pillar (fn [i] (* i 8))
        ;; faces for arches
        pillar-faces (vec (mapcat
                            (fn [i]
                              (let [o (offset-pillar i)]
                                [[o (+ o 1) (+ o 2) (+ o 3)]
                                 [(+ o 3) o (+ o 4) (+ o 7)]
                                 [(+ o 1) (+ o 2) (+ o 6) (+ o 5)]
                                 [(+ o 4) (+ o 5) (+ o 6) (+ o 7)]]))
                            (range n)))
        ;; top beam face
        top-index (* n 8)
        o top-index
        top-face [[o (+ o 1) (+ o 2) (+ o 3)]               ;; bottom of gutter
                  [o (+ o 1) (+ o 5) (+ o 4)]               ;; side wall
                  [(+ o 2) (+ o 3) (+ o 7) (+ o 6)]         ;; side wall
                  ]]
    [:mesh {:role     "aqueduct"
            :vertices vertices
            :faces    (into pillar-faces top-face)
            :colors   (vec (concat
                             (repeat (* 2 n) (:gyellow col)) ;; arch legs
                             (repeat (* 2 n) (:gyellow col)) ;; arch legs
                             [(:ddblue col)]
                             (repeat (* 2 n) (:gyellow col)) ;; arch legs
                             ))}                            ;; arch legs
     ]))

(defn oblong-mesh [x y z w d h color role]
  (let [half-w (/ w 2)
        half-d (/ d 2)
        vertices [[(- x half-w) (- y half-d) z] [(+ x half-w) (- y half-d) z]
                  [(+ x half-w) (+ y half-d) z] [(- x half-w) (+ y half-d) z]
                  [(- x half-w) (- y half-d) (+ z h)] [(+ x half-w) (- y half-d) (+ z h)]
                  [(+ x half-w) (+ y half-d) (+ z h)] [(- x half-w) (+ y half-d) (+ z h)]]
        faces [[0 1 2 3]
               [0 1 5 4]
               [3 0 4 7]
               [4 5 6 7]
               [2 3 7 6]
               [1 2 6 5]]
        colors (repeat 6 color)]
    [:mesh {:role     role
            :vertices vertices
            :faces    faces
            :colors   colors}]))

(defn roof-mesh [x y z w d rh color role]
  ;; x, y, z = center of roof base
  ;; w = width (x), d = depth (y), rh = ridge height
  (let [hx (/ w 2)
        hy (/ d 2)
        ;; base corners
        bl [(- x hx) (- y hy) z]                            ;; back-left
        br [(+ x hx) (- y hy) z]                            ;; back-right
        fr [(+ x hx) (+ y hy) z]                            ;; front-right
        fl [(- x hx) (+ y hy) z]                            ;; front-left
        ;; ridge vertices along depth
        ridge-left [(- x hx) 0 (+ z rh)]
        ridge-right [(+ x hx) 0 (+ z rh)]
        vertices [bl br fr fl ridge-left ridge-right]
        faces [[0 3 4]                                      ;; back-left triangle
               [1 0 4 5]                                    ;; back roof quad
               [3 2 5 4]                                    ;; front roof quad
               [2 1 5]]                                     ;; front-right triangle
        colors (repeat 4 color)]
    [:mesh {:role     role
            :vertices vertices
            :faces    faces
            :colors   colors}]))

(defn temple-mesh []
  [:mesh {:role "temple"}
   ;; base plinth
   (oblong-mesh 0 0 0 10 8 1 (:sstone col) "base")
   ;; pillars
   (oblong-mesh -4 -3 1 0.5 0.5 4 (:swhite col) "pillar")
   (oblong-mesh 4 -3 1 0.5 0.5 4 (:swhite col) "pillar")
   (oblong-mesh 1.33 -3 1 0.5 0.5 4 (:swhite col) "pillar")
   (oblong-mesh -1.33 -3 1 0.5 0.5 4 (:swhite col) "pillar")
   (oblong-mesh 4 -3 1 0.5 0.5 4 (:swhite col) "pillar")
   (oblong-mesh 4 0 1 0.5 0.5 4 (:swhite col) "pillar")
   (oblong-mesh 4 3 1 0.5 0.5 4 (:swhite col) "pillar")
   (oblong-mesh 1.33 3 1 0.5 0.5 4 (:swhite col) "pillar")
   (oblong-mesh -1.33 3 1 0.5 0.5 4 (:swhite col) "pillar")
   (oblong-mesh -4 3 1 0.5 0.5 4 (:swhite col) "pillar")
   (oblong-mesh -4 0 1 0.5 0.5 4 (:swhite col) "pillar")
   ;; roof slab
   (roof-mesh 0 0 5 10 8 2 (:swhite col) "pediment")])

(defn forum-mesh []
  [:mesh {:role "forum"}
   (oblong-mesh 0 0 0 10 8 1 (:sstone col) "base")
   (oblong-mesh 0 0 1 8 6 1 (:sstone col) "base")
   (oblong-mesh 0 0 2 6 4 1 (:sstone col) "base")])

(defn obelisk-mesh
  [r h rh n]
  (let [bottom-verts (circle-verts r n)
        top-verts (-> (circle-verts (* 0.5 r) n)
                      (translate-verts [0 0 h]))
        cone-top [0 0 (+ h rh)]
        side-faces (aligned-faces n 0)
        bottom-face (vec (range n))
        cone-faces (for [i (range n)]
                     [(+ i n) (+ (mod (inc i) n) n) (+ n n)])]
    [:mesh {:role     "lighthouse"
            :vertices (vec (concat bottom-verts top-verts [cone-top]))
            :faces    (vec (concat [bottom-face] side-faces cone-faces))
            :colors   (vec (concat
                             [(:ddblue col)]
                             (repeat n (:swhite col))
                             (repeat n (:swhite col))))}]))

(defn cylinder [r h n color]
  (let [bottom (circle-verts r n)
        top (-> (circle-verts r n)
                (translate-verts [0 0 h]))
        faces (aligned-faces n 0)]
    [:mesh {:role     "cylinder"
            :vertices (into bottom top)
            :faces    faces
            :colors   (repeat (+ n n) color)}]))

(defn colosseum-mesh [r h n]
  (let [outer-bottom (circle-verts r n)
        outer-top (-> (circle-verts r n)
                      (translate-verts [0 0 h]))
        seating-top (-> (circle-verts r n)
                        (translate-verts [0 0 (* 0.7 h)]))
        seating-mid (-> (circle-verts (* 0.5 r) n)
                        (translate-verts [0 0 (* 0.3 h)]))
        inner-bottom (circle-verts (* 0.5 r) n)

        half-n (quot n 2)
        [outer-faces-far outer-faces-near] (split-at half-n (aligned-faces n 0))
        seat-faces (aligned-faces n (* 2 n))
        inner-faces (aligned-faces n (* 3 n))
        bottom-face (vec (reverse (range n)))]
    [:mesh {:role     "colosseum"
            :vertices (vec (concat outer-bottom outer-top seating-top seating-mid inner-bottom))
            :faces    (vec (concat [bottom-face] outer-faces-far
                                   inner-faces seat-faces
                                   outer-faces-near))
            :colors   (vec (concat
                             [(:cbrown col)]
                             (repeat half-n (:sstone col))
                             (repeat n (:sstone col))
                             (repeat n "url(#cobble)")
                             (repeat (- n half-n) (:swhite col))))}]))

(defn index-ranges [counts]
  (let [offsets (reductions + 0 counts)]
    (mapv (fn [[start end]]
            (vec (range start end)))
          (partition 2 1 offsets))))

(defn cart-mesh []
  (let [length 4.0
        l2 (/ length 2.0)
        width 2.0
        w2 (/ width 2.0)
        h 2.0
        base [[(- w2) (- l2) h]
              [w2 (- l2) h]
              [w2 l2 h]
              [(- w2) l2 h]]
        wheel (-> (circle-verts w2 10)
                  (rotate-verts [0 π2 0]))
        back-wheel (translate-verts wheel [(- w2) 0 h])
        front-wheel (translate-verts wheel [w2 0 h])
        pole [[-0.1 l2 0]
              [0 length 0]
              [0.1 l2 0]]
        back-pole (translate-verts pole [(+ (- w2) 0.1) 0 h])
        front-pole (translate-verts pole [(- w2 0.1) 0 h])]
    [:mesh {:role     "cart"
            :vertices (vec (concat back-wheel base front-wheel back-pole front-pole))
            :faces    (index-ranges [(count wheel)
                                     (count base)
                                     (count wheel)
                                     (count pole)
                                     (count pole)])
            :colors   [(:cbrown col)
                       (:cbrown col)
                       (:cbrown col)
                       (:cbrown col)
                       (:cbrown col)]}]))

(defn fire-mesh []
  (let [n 6
        r 3
        bottom-verts (translate-verts (circle-verts r n) [0 0 1])
        cone-top [0 0 10]
        bottom-face (vec (range n))
        cone-faces (for [i (range n)]
                     [i (mod (inc i) n) n])]
    [:mesh {:role     "fire"
            :vertices (conj bottom-verts cone-top)
            :faces    (vec (concat [bottom-face] cone-faces))
            :colors   (vec (repeat (+ n 1) (color/palette 9)))}]))

(defn t [x]
  (if (and (vector? x) (keyword (first x)))
    (let [[tag attrs & children] x]
      [:g
       (case tag
         :mesh (let [{:keys [vertices faces colors]} attrs]
                 (for [[face color] (map vector faces colors)]
                   (svg/polygon {:fill color}
                                (for [idx face]
                                  (iso (get vertices idx)))))))
       (map t children)])
    x))

(defn pillar [attrs]
  [:g attrs
   (t (oblong-mesh 0 0 -1 3 3 1 (:sstone col) "slate"))
   (t (cylinder 1 10 6 (:sstone col)))
   (t (oblong-mesh 0 0 10 3 3 1 (:sstone col) "slate"))])

(defn authors []
  (let [authors (->> (:author @db/db)
                     (sort-by :name))
        n (count authors)
        colors (cycle (take 11 color/palette))
        r 6]
    [:g
     [:g {:transform "translate(0,-8)"}
      (mu/mo (str n " authors"))]
     [:g {:transform "scale(0.8)"}
      (for [[{:keys [image]} [x y] color] (map vector authors (geom/spiral 100) colors)]
        [:g {:transform (str "translate(" (* r x) "," (* r y) ")")}
         (svg/polygon {:fill         color
                       :stroke       (:dblue col)
                       :stroke-width 1}
                      (geom/hex r))
         [:image {:x                   -5
                  :y                   -5
                  :width               10
                  :height              10
                  :href                image
                  :clip-path           "url(#hex)"
                  :preserveAspectRatio "xMidYMid slice"}]])]]))

(defn posts []
  (let [r 3
        n 80
        colors (cycle (take 11 color/palette))]
    [:g
     [:g {:transform "translate(0,-8)"}
      (mu/mo (str n " articles"))]
     [:g {:transform "scale(0.8)"}
      (for [[color [x y]] (take n (map vector colors (geom/spiral 100)))]
        [:g {:transform (str "translate(" (* r x) "," (* r y) ")")}
         (svg/polygon {:fill         color
                       :stroke       (:dblue col)
                       :stroke-width 1}
                      (geom/hex r))])]]))

(defn maze []
  (let [wall-thickness 1
        wall-height 2.0
        cell-size 20.0
        c2 (/ cell-size 2.0)
        z 0
        walls (concat
                [[c2 0 (+ cell-size wall-thickness) wall-thickness]]
                (for [x (range 0 (inc cell-size) (/ cell-size 5))]
                  [x c2 wall-thickness (- cell-size wall-thickness)])
                [[c2 cell-size (+ cell-size wall-thickness) wall-thickness]])]
    (into
      [:g {:role "maze"}
       (t (oblong-mesh c2 c2 -2 (+ cell-size wall-thickness) (+ cell-size wall-thickness) 2 (:mgray col) "floor"))]
      (for [[x y w d] walls]
        (t (oblong-mesh x y z w d wall-height (:sstone col) "wall"))))))

(defn tile []
  (kind/hiccup
    [:svg {:id      "app"
           :xlmns   "http://www.w3.org/2000/svg"
           :viewBox "-40 -40 80 80"
           :width   "100%"}
     (let [r 40]
       [:g {:transform "rotate(90)"}
        (svg/polygon {:fill (:dgreen col)} (geom/hex (- r 2)))])]))

(def slides
  [
   ;; Introduction
   [{:fill (color/palette 1)}
    [:g
     (t (circle-mesh 15 6 "url(#cobble)" "circle"))
     (t (circle-mesh 10 10 (:dblue col) "Clojure"))
     (t (cylinder 10 1 10 (:sstone col)))
     (for [[x y w d h rh] [[-27 -18 6 4 3 1]
                           [-20 -15 6 4 3 1]
                           [-32 -13 6 4 3 1]
                           [-25 -10 6 4 3 1]
                           [-38 -7 6 4 3 1]
                           [-30 0 6 4 3 1]
                           [10 -20 8 4 3 1]
                           [15 20 8 4 3 1]]]
       [:g {:transform (str "translate(" x "," y ")")}
        (t (house-mesh w d h rh))])]]

   [{:fill (color/palette 1)}
    [:g {}
     (mu/mo "_meaning_

**collaboration**

`code`")
     [:g {:transform (str "translate(1,15)")}
      (t (circle-mesh 7 6 "url(#cobble)" "circle"))
      (t (oblong-mesh 0 0 0 1 11 1 (color/palette 11) "log"))
      (t (oblong-mesh 0 0 0 11 1 1 (color/palette 11) "log"))
      (t (fire-mesh))]]]

   [{:fill (color/palette 1)}
    [:g {}
     (mu/mo "## Code")
     [:g {:transform "translate(0,15)"}
      (t (circle-mesh 25 6 "url(#cobble)" "circle"))
      (t (temple-mesh))]]]

   [{:fill (color/palette 1)}
    [:g {}
     (mu/mo "## Meaning")
     [:g {:transform "translate(-15,25) rotate(-135)"}
      [:image {:href  "chisel-svgrepo-com.svg"
               :x     -10
               :width 20}]]
     [:g {:transform "translate(-12,15) rotate(-30)"}
      [:image {:href  "hammer-svgrepo-com.svg"
               :x     10
               :width 20}]]]]

   ;; Why it matters
   [{:fill (color/palette 11)}
    [:g {}
     [:g {:transform "translate(0,5)"}
      (t (oblong-mesh 0 20 0 15 15 10 (:lblue col) "good"))
      (t (oblong-mesh 20 0 0 15 15 10 (:gyellow col) "evil"))
      [:image {:href "mask-happly-svgrepo-com.svg"
               :transform "skewX(-30) scale(1,0.5) rotate(45) translate(-20,4) scale(0.02)"}]

      [:image {:href "mask-sad-svgrepo-com.svg"
               :transform "skewX(-30) scale(1,0.5) rotate(45) translate(5,-20) scale(0.02)"}]

      ]
     (mu/md "_“There is only one good, knowledge,\\
and one evil, ignorance.”_")]]

   [{:fill (color/palette 11)}
    [:g {}
     [:g {:transform "translate(0,40)"}
      (mu/md "Change")]
     (t (oblong-mesh 0 0 4 20 20 1 "url(#cobble)" "slate"))
     (for [[x y rot] [[0 -12 -90]
                      [4 -10 0]
                      [8 -8 -20]
                      [12 -6 90]]]
       (pillar {:transform (str "translate(" x "," y ") rotate(" rot ")")}))]]

   [{:fill (color/palette 11)}
    [:g {}
     [:g {}
      [:g {:transform "translate(0,40)"}
       (mu/md "Construction")]
      (t (oblong-mesh 0 0 4 20 20 1 "url(#cobble)" "slate"))
      (for [[x y] [[0 -12]
                   [4 -10]
                   [8 -8]
                   [12 -6]]]
        (pillar {:transform (str "translate(" x "," y ")")}))

      #_#_(t (oblong-mesh 0 0 0 13 3 1 (:sstone col) "slate"))
              (t (oblong-mesh 0 0 1 10 1 10 (:sstone col) "slate"))]]]

   [{:fill (:dgreen col)}
    [:g
     (mu/md "## Sharing")
     [:g {:transform "translate(20,-10)"}
      (t (granary-mesh 6 6 3 6))]]]

   [{:fill (:dgreen col)}
    [:g
     (mu/md "## Blogging")
     [:g {:transform "translate(-20,10) scale(2)"}
      (t (forum-mesh))]]]

   [{:fill (:dgreen col)}
    [:g
     (let [w 3, l 1, h 2]
       (for [i (range 5)
             j (range 5)
             k (range 3)]
         (t (oblong-mesh (* i w) (* j l) (* k h) w l h (:sstone col) "block"))))
     [:g {:transform "translate(-10,10) scale(2)"}
      (t (cart-mesh))]]]

   [{:fill (:dgreen col)}
    [:g
     [:g {:transform "translate(9,4)"}
      (t (oblong-mesh 0 0 -1 30 30 0 "url(#cobble)" "platform"))
      (t (circle-mesh 10 4 (:ddblue col) "pool"))
      (t (cylinder 10 1 4 (:sstone col)))]
     [:g {:transform "translate(10,3)"}
      (t (aqueduct-mesh -44 3 6 2 4))]]]

   [{:fill (:dgreen col)}
    [:g {}
     (t (oblong-mesh 0 0 -4 50 50 0 "url(#cobble)" "platform"))
     (t (oblong-mesh 0 0 -3 25 25 1 (:sstone col) "platform"))
     (t (oblong-mesh 0 0 -2 20 20 1 (:sstone col) "platform"))
     (t (oblong-mesh 0 0 -1 15 15 1 (:sstone col) "platform"))
     (t (temple-mesh))]]

   [{:fill (:dgreen col)}
    [:g {}
     (mu/md "## Documents")
     [:g {:transform "translate(0,25)"}
      (t (oblong-mesh 0 0 0 15 1 20 (:sstone col) "slate"))
      [:path {:transform "skewY(30) scale(0.5,1) translate(-5,-15) scale(0.02)"
              :fill "none"
              :stroke-width 50
              :d "M292.9 384c7.3-22.3 21.9-42.5 38.4-59.9 32.7-34.4 52.7-80.9 52.7-132.1 0-106-86-192-192-192S0 86 0 192c0 51.2 20 97.7 52.7 132.1 16.5 17.4 31.2 37.6 38.4 59.9l201.7 0zM288 432l-192 0 0 16c0 44.2 35.8 80 80 80l32 0c44.2 0 80-35.8 80-80l0-16zM184 112c-39.8 0-72 32.2-72 72 0 13.3-10.7 24-24 24s-24-10.7-24-24c0-66.3 53.7-120 120-120 13.3 0 24 10.7 24 24s-10.7 24-24 24z"}]
      ]]]

   [{:fill (:dgreen col)}
    [:g {}
     (mu/md "_Verba volant,\\
scripta manent_")

     (t (oblong-mesh 1 -1 -5 1 1 5 (:cbrown col) "leg"))
     (t (oblong-mesh 8 -1 -5 1 1 5 (:cbrown col) "leg"))
     (t (oblong-mesh 1 11 -5 1 1 5 (:cbrown col) "leg"))
     (t (oblong-mesh 8 11 -5 1 1 5 (:cbrown col) "leg"))
     (t (oblong-mesh 5 5 0 10 15 1 (:cbrown col) "table"))

     [:g {:transform "translate(1,1) scale(0.5)"}
      (let [w 2, l 3, h 1]
        (for [i (range 3)
              j (range 3)
              k (range 3)]
          (t (oblong-mesh (* i w) (* j l) (* k h) w l h (:gyellow col) "block"))))]

     [:g {:stroke-width 0.2}
      (for [[x y] [[0 5]
                   [0 6]
                   [1 6]
                   [1.5 5.5]
                   [-0.5 5.5]
                   [-0.5 5]
                   [-1 5.5]
                   [-2 5]]]
        [:g {:transform (str "translate(" x "," y ")")}
         (t (circle-mesh 0.3 10 (:gyellow col) "coin"))])]]]

   [{:fill (:dgreen col)}
    [:g {}
     [:g {:transform "translate(0,20)"}
      (let [w 10, l 20, h 2]
        (for [i (range 1)
              j (range 1)
              k (range 5)]
          (t (oblong-mesh (* i w) (* j l) (* k h) w l h (:sstone col) "block"))))]
     [:path {:transform "skewX(-30) scale(1,0.5) rotate(45) translate(13,4) scale(0.02)"
             :fill "none"
             :stroke-width 50
             :d "M292.9 384c7.3-22.3 21.9-42.5 38.4-59.9 32.7-34.4 52.7-80.9 52.7-132.1 0-106-86-192-192-192S0 86 0 192c0 51.2 20 97.7 52.7 132.1 16.5 17.4 31.2 37.6 38.4 59.9l201.7 0zM288 432l-192 0 0 16c0 44.2 35.8 80 80 80l32 0c44.2 0 80-35.8 80-80l0-16zM184 112c-39.8 0-72 32.2-72 72 0 13.3-10.7 24-24 24s-24-10.7-24-24c0-66.3 53.7-120 120-120 13.3 0 24 10.7 24 24s-10.7 24-24 24z"}]

     (mu/md "_“I cannot teach anybody anything.\\
I can only make them think.”_")]]

   ;; Clay

   [{:fill (color/palette 3)}
    [:g {}
     (mu/mo "## Clay")
     [:image {:x     -10 :y 5
              :width 20
              :href  "Clay.svg"}]]]

   [{:fill (color/palette 3)}
    [:g {}
     [:g {:transform "translate(0,-10)"}
      (mu/mo "Clojure namespace => Document")]
     [:image {:x     -30
              :y     -20
              :width 60
              :href  "/scicloj/clay/workshop/macroexpand2025.png"}]]]

   [{:fill (color/palette 3)}
    [:g {}
     [:image {:x    -30 :y -14 :width 60
              :href "reduce.png"}]]]

   [{:fill (color/palette 3)}
    [:g {}
     (mu/mo "```
;; **Markdown** comments.
```

---

**Markdown** comments.
")]]

   [{:fill (color/palette 3)}
    [:g {}
     (mu/mo "```
;; ![](/images/civitas-icon.svg)
```

---
")
     [:image {:width 20
              :x     -10 :y -5
              :href  "/images/civitas-icon.svg"}]]]

   [{:fill (color/palette 3)}
    [:g {}
     (mu/mo {:text-align "left"} "```
^:kind/table
{:types   [\"Table\" \"Chart\" \"Image\"]
 :purpose [\"Summarize data\"
           \"Enable comparison\"
           \"Enhance appeal\"]}
```

---

| Table | Chart | Image |
|--|--|--|
| Summarize data | Enable comparison | Enhance appeal |

")]]

   [{:fill (color/palette 0)}
    [:g {}
     [:image {:width 30
              :x     -15
              :y     -15
              :href  "/scicloj/clay/mermaid.png"}]]]

   [{:fill (color/palette 0)}
    [:g {}
     (mu/mo "Charts")
     [:image {:width 50
              :x     -25
              :y     -15
              :href  "/scicloj/cfd/data_viz/overlayplot.png"}]]]

   [{:fill (color/palette 0)}
    [:g {}
     [:image {:width 50
              :x     -25
              :y     -20
              :href  "long-tail.png"}]]]

   [{:fill (color/palette 0)}
    [:g {}
     [:image {:width 50
              :x     -25
              :y     -35
              :href  "sample-plot.png"}]]]

   [{:fill (color/palette 0)}
    [:g {}
     (mu/mo "println pprint hidden html **hiccup**
reagent scittle emmy-viewers graphviz mermaid
md tex code edn vega vega-lite echarts cytoscape **plotly**
htmlwidgets-plotly htmlwidgets-ggplotly
video observable highcharts image dataset smile-model
var test seq vector set map
**table** portal fragment fn test-last")]]

   [{:fill (color/palette 0)}
    [:g {}
     (mu/mo {:text-align "left"} "```
^:kind/hiccup
[:svg {:width \"100%\"}
 [:circle {:r 40 :fill \"lightblue\"}]
 [:circle {:r 20 :fill \"lightgreen\"}]]
```")
     [:circle {:r 10 :cy 10 :fill (:lblue col)}]
     [:circle {:r 5 :cy 10 :fill (:lgreen col)}]]]


   [{:fill (color/palette 0)}
    [:g {}
     (mu/mo {:text-align "left"} "```
^:kind/hiccup
[:script {:type \"application/x-scittle\"
          :src  \"awesome.cljs\"}]
```

## Scittle
")
     [:image {:width 20
              :x     -10
              :y     10
              :href  "sci-logo.png"}]]]

   [{:fill (color/palette 0)}
    [:g {}
     (mu/mo {} "Interactive pages")
     [:image {:width 60
              :x     -30
              :y     -13
              :href  "scittlerepl.png"}]]]

   [{:fill (:lgreen col)}
    [:g {}
     (mu/mo "Notebook?")
     [:image {:width 20
              :x -10
              :y 0
              :href "scroll-svgrepo-com.svg"}]]]

   [{:fill (:lgreen col)}
    [:g {}
     (mu/mo "Publishing")
     [:g {:transform "translate(0,20)"}
      (t (oblong-mesh 0 0 0 13 3 1 (:sstone col) "slate"))
      (t (oblong-mesh 0 0 1 10 1 20 (:sstone col) "slate"))
      [:path {:transform "skewY(30) scale(0.5,1) translate(-5,-15) scale(0.02)"
              :fill "none"
              :stroke-width 50
              :d "M292.9 384c7.3-22.3 21.9-42.5 38.4-59.9 32.7-34.4 52.7-80.9 52.7-132.1 0-106-86-192-192-192S0 86 0 192c0 51.2 20 97.7 52.7 132.1 16.5 17.4 31.2 37.6 38.4 59.9l201.7 0zM288 432l-192 0 0 16c0 44.2 35.8 80 80 80l32 0c44.2 0 80-35.8 80-80l0-16zM184 112c-39.8 0-72 32.2-72 72 0 13.3-10.7 24-24 24s-24-10.7-24-24c0-66.3 53.7-120 120-120 13.3 0 24 10.7 24 24s-10.7 24-24 24z"}]
      ]]]

   [{:fill (:lgreen col)}
    [:g {}
     (mu/mo "Quarto")
     [:image {:width 60
              :x     -30
              :y     -10
              :href  "sidenotes.png"}]
     [:g {:transform "translate(-10,30) scale(-2,2)"}
      (t (cart-mesh))]
     [:g {:transform "translate(15,20)"}
      (t (circle-mesh 10 6 "url(#cobble)" "circle"))
      (t (obelisk-mesh 2 12 2 4))]]]

   [{:fill (:lgreen col)}
    [:g {}
     (mu/mo "Formats")
     [:image {:width 60
              :x     -30
              :y     -10
              :href  "formats.png"}]]]

   [{:fill (:lgreen col)}
    [:g {}
     [:image {:width 50
              :x     -25
              :y     -30
              :href  "pdfexample.png"}]]]

   [{:fill (:lgreen col)}
    [:g {}
     [:image {:width 20
              :x     -15
              :y     -30
              :href  "cljdoc-logo.svg"}]
     [:image {:width 50
              :x     -25
              :y     -10
              :href  "cljdoc.png"}]

     ]]

   [{:fill (:lgreen col)}
    [:g {}
     [:g {:transform "translate(0,-15)"}
      (mu/mo "## Literate?")]
     [:rect {:fill (:sstone col)
             :x -36
             :y -18
             :width 36 :height 37}]
     [:image {:href "md2code.png"
              :x -33
              :y -15
              :width 30}]
     [:rect {:fill (:sstone col)
             :x 2
             :y -13
             :width 36 :height 25}]
     [:image {:href "code2md.png"
              :stroke "white"
              :stroke-width 5
              :style {:border "5px solid white"}
              :x 5
              :y -10
              :width 30}]]]

   [{:fill (:lgreen col)}
    [:g {}
     (mu/mo "Reproducible

Sharable

Useful

Fun")
     [:g {:transform "translate(-20,15) rotate(-30)"}
      [:image {:href  "chisel-svgrepo-com.svg"
               :x     -10
               :width 20}]]
     [:g {:transform "translate(5,5) rotate(30)"}
      [:image {:href  "hammer-svgrepo-com.svg"
               :x     10
               :width 20}]]]]

   [{:fill (color/palette 4)}
    [:g {}
     (mu/mo {:text-align "left"} "|  | Notebooks | Clay |
|--|--|--|
| **Visualizing** |	React |	HTML+JavaScript |
| **Coding**	|	Cells | REPL |
| **Publishing** | Notebook | Markdown |")]]

   [{:fill (color/palette 4)}
    [:g {:transform "translate(0,9)"}
     (mu/mo "_“Best notebook experience I’ve had”_\\
\\
_“I love this way of building up an idea”_\\
\\
_“This was way easier than I expected”_")]]

   [{:fill (color/palette 4)}
    [:g {}
     (mu/mo "```
org.scicloj/clay
{:mvn/version \"2.0.2\"}

scicloj.clay.v2.make/make!

{:source-path \"myns.clj\"}

{:single-form '(+ 1 2)}
```")]]

   [{:fill (color/palette 4)}
    [:g {}
     [:g {:transform "translate(0,-10)"}]
     [:image {:x     -30 :y -15
              :width 60
              :href  "clay-commands.png"}]]]

   [{:fill (color/palette 4)}
    [:g {}
     [:image {:x     -30 :y -15
              :width 60
              :href  "/scicloj/clay/workshop/macroexpand2025-editor.png"}]]]

   [{:fill (color/palette 4)}
    [:g {}
     [:image {:x     -30 :y -15
              :width 60
              :href  "make-form.png"}]]]

   [{:fill (color/palette 4)}
    [:g {}
     [:g {}
      (mu/mo "External Browser")]
     [:image {:x     -30 :y -15
              :width 60
              :href  "browser.png"}]]]

   [{:fill (color/palette 4)}
    [:g
     [:g {:transform "translate(10,5)"}
      (mu/mo {:text-align "left"} "Calva

Cider

Conjure

Cursive")]


     [:g {:transform "translate(0,-15) scale(0.05)"}
      [:path {:fill (:cbrown col)
              :d    "M471.6 21.7c-21.9-21.9-57.3-21.9-79.2 0L368 46.1 465.9 144 490.3 119.6c21.9-21.9 21.9-57.3 0-79.2L471.6 21.7zm-299.2 220c-6.1 6.1-10.8 13.6-13.5 21.9l-29.6 88.8c-2.9 8.6-.6 18.1 5.8 24.6s15.9 8.7 24.6 5.8l88.8-29.6c8.2-2.7 15.7-7.4 21.9-13.5L432 177.9 334.1 80 172.4 241.7zM96 64C43 64 0 107 0 160L0 416c0 53 43 96 96 96l256 0c53 0 96-43 96-96l0-96c0-17.7-14.3-32-32-32s-32 14.3-32 32l0 96c0 17.7-14.3 32-32 32L96 448c-17.7 0-32-14.3-32-32l0-256c0-17.7 14.3-32 32-32l96 0c17.7 0 32-14.3 32-32s-14.3-32-32-32L96 64z"}]]]]

   [{:fill (color/palette 4)}
    [:g {}
     [:g {}
      (mu/mo "## Live reload

\"Clay watch\"

```
clojure -M -m scicloj.clay.v2.main
```")]]]

   [{:fill (color/palette 12)}
    [:g {}
     [:image {:x     -30 :y -15
              :width 60
              :href  "whatif.png"}]]]

   ;; Sharing

   [{:fill (color/palette 12)}
    [:g {}
     (mu/md "## ClojureCivitas

Shared blog space
[https://clojurecivitas.github.io/](https://clojurecivitas.github.io/)")
     [:image {:x     -10 :y 10
              :width 20
              :href  "/images/civitas-icon.svg"}]]]

   [{:fill (color/palette 12)}
    [:g {}
     [:image {:x     -28 :y -30
              :width 56
              :href  "civitas-website.png"}]]]

   [{:fill (color/palette 12)}
    [:g {}
     [:image {:x     -28 :y -32
              :width 56
              :href  "morecivitas.png"}]]]

   [{:fill (color/palette 12)}
    [:g {}
     [:image {:x     -28 :y -25
              :width 56
              :href  "depth-first.png"}]]]

   [{:fill (color/palette 12)}
    [:g {}
     [:image {:x     -28 :y -25
              :width 56
              :href  "interactive.png"}]]]

   [{:fill (color/palette 12)}
    [:g {}
     [:image {:x     -28 :y -32
              :width 56
              :href  "mermaid.png"}]]]

   [{:fill (:lblue col)}
    [:g {}
     (authors)]]

   [{:fill (:lblue col)}
    [:g {}
     (posts)]]

   [{:fill (:lblue col)}
    [:g {}
     (mu/mo "Growing Audience")

     [:g {:transform "translate(-16,-5) scale(0.05)"}
      [:path {:fill (:dblue col)
              :d    "M160 96C124.7 96 96 124.7 96 160L96 480C96 515.3 124.7 544 160 544L480 544C515.3 544 544 515.3 544 480L544 160C544 124.7 515.3 96 480 96L160 96zM192 200C192 186.7 202.7 176 216 176C353 176 464 287 464 424C464 437.3 453.3 448 440 448C426.7 448 416 437.3 416 424C416 313.5 326.5 224 216 224C202.7 224 192 213.3 192 200zM192 296C192 282.7 202.7 272 216 272C299.9 272 368 340.1 368 424C368 437.3 357.3 448 344 448C330.7 448 320 437.3 320 424C320 366.6 273.4 320 216 320C202.7 320 192 309.3 192 296zM192 416C192 398.3 206.3 384 224 384C241.7 384 256 398.3 256 416C256 433.7 241.7 448 224 448C206.3 448 192 433.7 192 416z"}]]]]

   [{:fill (:lblue col)}
    [:g {}
     (t (circle-mesh 30 6 (color/palette 0) "circle"))
     (t (circle-mesh 25 6 (:dgreen col) "circle"))
     (t (granary-mesh 6 6 3 6))]]

   [{:fill (:lblue col)}
    [:g {}
     (t (circle-mesh 30 10 (color/palette 11) "circle"))
     [:g {:transform "translate(0,-10)"}
      (maze)]]]

   [{:fill (:lblue col)}
    [:g {}
     (mu/mo "Big and small")
     [:g {:transform "translate(0,5)"}
      (t (oblong-mesh 0 0 -4 45 45 0 (:dgreen col) "platform"))]
     [:g {:transform "translate(-15,5)"}
      (t (colosseum-mesh 10 8 10))]
     [:g {:transform "translate(25,10)"}
      (t (house-mesh 4 2 1 0.5))]
     [:g {:transform "translate(15,10)"}
      (t (house-mesh 4 2 1 0.5))]
     [:g {:transform "translate(20,5)"}
      (t (house-mesh 4 2 1 0.5))]]]

   [{:fill (:lblue col)}
    [:g {}
     (mu/mo "clojurecivitas.github.io")
     [:g {:transform "scale(0.5) translate(0,20)"}
      (t (oblong-mesh 0 0 0 10 10 10 (:dblue col) "good"))
      (t (oblong-mesh 10 0 0 5 5 5 (:lgreen col) "evil"))
      (t (oblong-mesh -10 -20 0 7 7 7 (:dblue col) "good"))
      (t (oblong-mesh 0 20 0 10 10 10 (:gyellow col) "evil"))
      (t (oblong-mesh 10 -20 0 10 10 10 (:lgreen col) "good"))
      (t (oblong-mesh 30 0 0 3 3 3 (:lgreen col) "good"))
      (t (oblong-mesh 20 20 0 7 7 7 (:gyellow col) "evil"))
      (t (oblong-mesh -25 0 0 10 10 10 (:dblue col) "good"))
      (t (oblong-mesh 0 35 0 3 3 3 (:gyellow col) "evil"))]]]

   [{:fill (:lblue col)}
    [:g {}
     (mu/mo "_“I am neither Athenian nor Greek,\\
but a citizen of the world.”_

Citizenship in a community, with rights and responsibilities
")
     [:g {:transform "translate(-9,5) scale(0.05)"}
      [:path {:fill (:dblue col)
              :d "M0 64C0 28.7 28.7 0 64 0L320 0c35.3 0 64 28.7 64 64l0 384c0 35.3-28.7 64-64 64L64 512c-35.3 0-64-28.7-64-64L0 64zM96 408c0 13.3 10.7 24 24 24l144 0c13.3 0 24-10.7 24-24s-10.7-24-24-24l-144 0c-13.3 0-24 10.7-24 24zM278.6 208c-4.8 26.4-21.5 48.7-44.2 61.2 6.7-17 11.2-38 12.6-61.2l31.6 0zm-173.1 0l31.6 0c1.4 23.1 6 44.2 12.6 61.2-22.7-12.5-39.4-34.8-44.2-61.2zm76.4 55c-6.2-13.4-11.1-32.5-12.7-55l45.8 0c-1.6 22.5-6.5 41.6-12.7 55-4.5 9.6-8.2 13.8-10.2 15.5-2-1.7-5.7-5.8-10.2-15.5zm0-142c4.5-9.6 8.2-13.8 10.2-15.5 2 1.7 5.7 5.8 10.2 15.5 6.2 13.4 11.1 32.5 12.7 55l-45.8 0c1.6-22.5 6.5-41.6 12.7-55zm96.7 55L247 176c-1.4-23.1-6-44.2-12.6-61.2 22.7 12.5 39.4 34.8 44.2 61.2zM137 176l-31.6 0c4.8-26.4 21.5-48.7 44.2-61.2-6.7 17-11.2 38-12.6 61.2zm183 16a128 128 0 1 0 -256 0 128 128 0 1 0 256 0z"}]]]]

   [{:fill (:ddblue col)}
    [:g {}
     (mu/md "## Scicloj")
     [:g {:transform "translate(-16,-5) scale(0.05)"}
      [:path {:fill (:lblue col)
              :d "M320 16a104 104 0 1 1 0 208 104 104 0 1 1 0-208zM96 88a72 72 0 1 1 0 144 72 72 0 1 1 0-144zM0 416c0-70.7 57.3-128 128-128 12.8 0 25.2 1.9 36.9 5.4-32.9 36.8-52.9 85.4-52.9 138.6l0 16c0 11.4 2.4 22.2 6.7 32L32 480c-17.7 0-32-14.3-32-32l0-32zm521.3 64c4.3-9.8 6.7-20.6 6.7-32l0-16c0-53.2-20-101.8-52.9-138.6 11.7-3.5 24.1-5.4 36.9-5.4 70.7 0 128 57.3 128 128l0 32c0 17.7-14.3 32-32 32l-86.7 0zM472 160a72 72 0 1 1 144 0 72 72 0 1 1 -144 0zM160 432c0-88.4 71.6-160 160-160s160 71.6 160 160l0 16c0 17.7-14.3 32-32 32l-256 0c-17.7 0-32-14.3-32-32l0-16z"}]]]]

   [{:fill (:ddblue col)}
    [:g {}
     (t (oblong-mesh -15 -25 0 7 7 7 (:lblue col) "good"))
     (t (oblong-mesh -10 20 0 10 10 10 (:gyellow col) "evil"))
     (t (oblong-mesh 10 -20 0 10 10 10 (:lgreen col) "good"))
     (t (oblong-mesh 30 0 0 3 3 3 (:lgreen col) "good"))
     (t (oblong-mesh 20 20 0 7 7 7 (:gyellow col) "evil"))
     (t (oblong-mesh -25 0 0 10 10 10 (:lblue col) "good"))
     (t (oblong-mesh 0 35 0 3 3 3 (:gyellow col) "evil"))
     (t (oblong-mesh 15 -10 0 5 5 5 (:lgreen col) "evil"))
     (t (oblong-mesh 0 10 -25 10 10 10 (:lblue col) "good"))
     [:image {:x     -10
              :y     -15
              :width 20
              :href  "sci-cloj-logo-transparent.png"}]]]

   [{:fill (:ddblue col)}
    [:g {}
     (mu/md "## Noj

**Dataframes, Math, Charts, Publishing**")
     [:image {:x     5 :y -35
              :width 20
              :href  "/scicloj/noj/intro/Noj-icon.svg"}]
     [:g {:transform "translate(0,30) scale(4)"}
      (t (cart-mesh))]]]

   [{:fill "#171742"}
    [:g {}
     [:image {:x     -20 :y -30
              :width 40
              :href  "clojurecamp.png"}]
     [:g {:transform "translate(0,40)"}
      (mu/fo {:style (merge mu/default {:color (color/palette 0)})}
             [:div
              [:div "Opt-in for pairing next week?"]
              [:div
               [:div
                [:label
                 [:input {:type "radio" :checked true}]
                 [:span.label "Yes"]]
                " "
                [:label
                 [:input {:type "radio"}]
                 [:span.label "No"]]]]])]]]

   [{:fill "#171742"}
    [:g {}
     (mu/md "_“Strong minds discuss ideas,\\
average minds discuss events,\\
weak minds discuss people.”_")
     [:g {:transform "translate(0,20)"}
      (t (circle-mesh 7 6 "url(#cobble)" "circle"))
      (t (oblong-mesh 0 0 0 1 11 1 (color/palette 11) "log"))
      (t (oblong-mesh 0 0 0 11 1 1 (color/palette 11) "log"))
      (t (fire-mesh))]]]

   [{:fill "#171742"}
    [:g {}
     [:g {:transform "translate(0,0)"}
      (t (circle-mesh 14 12 (:gyellow col) "circle"))
      (t (circle-mesh 12 6 (:dgreen col) "circle"))
      (t (circle-mesh 4 4 "url(#cobble)" "circle"))
      (t (obelisk-mesh 2 14 2 4))]]]

   [{:fill (:dgreen col)}
    [:g {:transform "translate(7,3)"}
     (t (circle-mesh 10 4 (:ddblue col) "pool"))
     (t (cylinder 10 1 4 (:sstone col)))
     [:g {:transform "translate(2,-1)"}
      (t (aqueduct-mesh -44 3 6 2 4))]
     [:g {:transform "translate(-45,-10)"}
      (t (house-mesh 4 2 2 0.5))]
     [:g {:transform "translate(-35,-10)"}
      (t (house-mesh 5 2 2 0.5))]
     [:g {:transform "translate(-40,-5)"}
      (t (house-mesh 4 3 2 0.5))]
     [:g {:transform "translate(-50,-5)"}
      (t (house-mesh 4 3 2 0.5))]
     [:g {:transform "translate(-25,15)"}
      (t (oblong-mesh 0 0 -1 15 15 1 (:sstone col) "platform"))
      (t (temple-mesh))]
     [:g {:transform "translate(0,-25)"}
      (t (granary-mesh 3 3 1 6))]
     [:g {:transform "translate(22,-10)"}
      (t (circle-mesh 10 6 "url(#cobble)" "circle"))
      (t (obelisk-mesh 2 12 2 4))]
     [:g {:transform "translate(10,20)"}
      (t (cart-mesh))]]]])

(kind/hiccup
  [:svg {:id      "app"
         :xlmns   "http://www.w3.org/2000/svg"
         :viewBox "-400 -400 800 800"
         :style   {:width       "100vw"
                   :height      "100vh"
                   :margin-left "50%"
                   :transform   "translateX(-50%)"
                   :display     "block"}}
   defs
   [:g {:stroke-linejoin "round"
        :stroke          "black"
        :stroke-width    0.25
        :data-step       true}
    ;; increase the bounds
    [:circle {:stroke "none" :fill "none" :cx -600 :cy -500 :r 10}]
    [:circle {:stroke "none" :fill "none" :cx 600 :cy 500 :r 10}]
    (let [r 50]
      (for [[[attrs slide] [x y]] (map vector slides (geom/spiral 100))]
        [:g {:data-step true
             :transform (str "translate(" (* r x) "," (* r y) ")")}
         (svg/polygon attrs (geom/hex (- r 2)))
         slide]))]])
