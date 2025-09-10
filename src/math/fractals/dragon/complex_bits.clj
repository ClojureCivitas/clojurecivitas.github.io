^{:kindly/hide-code true
  :clay {:title  "Dragon Curve Fractal - Complex & Bits"
         :quarto {:type     :post
                  :author   [:harold]
                  :date     "2025-09-10"
                  :draft    true
                  :description "A familiar image is re-discovered in a sequence of complex numbers and the binary representation of the natural numbers."
                  :image    "dragon-curve.png"
                  :category :math
                  :tags     [:fractals :dragon :curve :complex :imaginary :binary]
                  :keywords [:fractals :dragon :curve :complex :imaginary :binary]}}}
(ns math.fractals.dragon.complex-bits
  (:require [clojure.string :as s]
            [fastmath.complex :as c]
            [scicloj.kindly.v4.kind :as kind]))

;; ### Part 1: Complex Numbers

;; Clojure's [fastmath](https://generateme.github.io/fastmath/notebooks/notebooks/complex_quaternion/index.html) library provides [complex numbers](https://en.wikipedia.org/wiki/Complex_number).

;; For example, if we let `a=b=1`, then

^:kindly/hide-code
(kind/tex "a+bi=1+1i")


(c/complex 1 1)

;; Here's a fun fact about powers of one more than the imaginary unit.

^:kindly/hide-code
(kind/tex "(1+i)^k=\\sqrt{2}^ke^{ik\\pi/4}")

;; One does not need to be Euler to sense spinning,

;; and `k` from `(0 1 2 3 ...)` gives a sequence of points.

(def pts
  (for [k (range)]
    (c/pow (c/complex 1 1)
           (c/complex k 0))))

;; The first few of which are...

(take 10 pts)

;; I plot, therefore I am:

(kind/hiccup
 (into [:svg {:width "256" :height "256" :viewBox "-24 -24 48 48"}
        [:rect {:x -24 :y -24 :width 48 :height 48 :fill :#f8f8f8}]]
       (concat (for [[x y] (take 10 pts)]
                 [:line {:x1 0 :y1 0 :x2 x :y2 y :stroke :#222}])
               (for [[x y] (take 10 pts)]
                 [:circle {:cx x :cy y :r 1.5 :fill :#88f}]))))

;; Spinning indeed.

;; ---

;; ### Part 2: Binary Bits

;; In computers, numbers are made of bits.

(defn bit
  [n pos]
  (bit-and (int (/ n (Math/pow 2 pos))) 1))

(for [pos (reverse (range 8))]
  (bit 42 pos))

;; Hidden in the bits is a draconiform structure, we use them to carefully sum the `pts`.

(defn dragon-by-bits
  ([n] (dragon-by-bits n (count (Integer/toBinaryString n))))
  ([n pos]
   (letfn [(a [n pos]
             ;; We move according to pts, occasionally turning
             (if (zero? (bit n pos))
               c/ZERO
               (let [turn? (if (zero? (bit n (inc pos)))
                             c/ONE
                             c/I)
                     pt (nth pts pos)] ;; (1+i)^pos
                 (c/mult turn? pt))))
           (m [n pos]
             ;; When the bits flip, we turn
             (if (= (bit n pos) (bit n (inc pos)))
               c/ONE
               c/I))]
     (if (>= pos 0)
       (c/add (a n pos)
              (c/mult (m n pos)
                      (dragon-by-bits n (dec pos))))
       c/ZERO))))

;; ---

;; ### Part 3: A Glimpse

;; > "It does not do to leave a live dragon out of your calculations, if you live near him."
;; >
;; > -- J.R.R. Tolkien

(let [dragon-pts (for [n (range 512)] (dragon-by-bits n))
      furthest (* 1.15 (apply max (map Math/abs (flatten dragon-pts))))
      [l t w h] [(- furthest) (- furthest) (* 2 furthest) (* 2 furthest)]]
  (kind/hiccup
   [:svg {:width "512" :height "512" :viewBox (s/join " " [l t w h])
          :shape-rendering :crispEdges}
    [:rect {:x l :y t :width w :height h :fill :#f8f8f8}]
    [:path {:d (reduce (fn [eax [x y]]
                         (str eax " L" x " " y))
                       (let [[x y] (first dragon-pts)]
                         (str "M" x " " y))
                       (rest dragon-pts))
            :vector-effect "non-scaling-stroke"
            :stroke :#222
            :fill :none}]]))

;; Inspired by this [lovely rosettacode gnuplot code](https://rosettacode.org/wiki/Dragon_curve#Version_#1.).
