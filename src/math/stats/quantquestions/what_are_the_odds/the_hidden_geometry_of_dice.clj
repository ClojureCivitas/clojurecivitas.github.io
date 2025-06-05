^{:kindly/hide-code true
  :clay             {:title  "The Hidden Geometry of Dice"
                     :quarto {:type     :post
                              :author   [:timothypratley]
                              :date     "2025-06-05"
                              :description "A simple probability puzzle turns into a journey through triangular numbers and tessellated hexagons."
                              :category :math
                              :tags     [:probability :stats]
                              :keywords [:quantquestions]}}}
(ns math.stats.quantquestions.what-are-the-odds.the-hidden-geometry-of-dice
  (:require [civitas.explorer.geometry :as geom]
            [civitas.explorer.svg :as svg]))

;; Welcome to What Are the Odds?
;; The show where we answer life’s important questions, like can I outsmart a six-sided die?
;; Today we’re starting small.
;; Two rolls of the dice and one burning question.
;; No magic formulas, just curiosity, some patient counting, and a faint hope that math is on our side.
;; Let’s roll.

;; > *Pop quiz:*
;; > You roll a fair 6-sided die twice.
;; > Calculate the probability that the value of the first roll is strictly less than the value of the second roll.

;; As I always like to say to my niece,
;; "the secret to answering any probability question is to enumerate the outcomes and count the ones we care about."

;; $P(Interesting) = InterestingOutcomes / TotalOutcomes$

;; Rolling 1 die once has 6 outcomes: 1 2 3 4 5 6.

;; Rolling 1 die twice gives us a combination of outcomes, let's write out a few.

;; ```
;; [1 1] [1 2] '... [1 6]
;; [2 1] [2 2] '...
;; ```

;; Following this pattern would produce 6 rows of 6 columns,
;; so there must be `36` outcomes.
;; We write down just enough of the pattern to figure out the best way to count it.
;; Now we count how many of those meet the criteria.

;; ```
;; [1 1 :no] [1 2 :yes] '...
;; [2 1 :no] [2 2 :no] [2 3 :yes] '...
;; ```

;; Logically we should see 5 yeses on the first row,
;; then 4, 3, 2, 1, and 0, which we can ignore.
;; Add them all up and we get `15`.

;; So the answer to the question is `15/36` which reduces to `5/12`,
;; dividing top and bottom by the greatest common divisor 3.

;; You might be thinking that it's not practical to enumerate everything all the time,
;; I should use the formulas of probability.
;; That's true, those are marvelous.
;; However, in my experience it is also easy to go wrong reasoning from formulas.
;; It's harder to go wrong starting with a counting problem,
;; then improving your method of counting.
;; You end up in the same place, but more confident in the answer.

;; The full enumeration of our simple 2 roll question as a counting problem is just big enough to be too tedious to use only counting.

(let [roll [1 2 3 4 5 6]]
  (for [i roll]
    (for [j roll]
      [i j])))

;; Identifying the pattern is enough to realize the answer

(+ 5 4 3 2 1)

;; What a marvelous pattern it is!
;; Predictable, but not flat.
;; Smooth, but not boring.
;; Recursive, and not obvious.

^:kindly/hide-code
(into [] (map #(reduce + (range (inc %)))) (range 1 21))

;; There's something special about this sequence.
;; Aren't those numbers just... pleasing in some way?

;; This sequence is called the triangular numbers.

;; ```
;;             .
;;            . .
;;           . . .
;;          . . . .
;; ```

;; You can find the first 10 or so numbers in your head,
;; and with some paper many more quite quickly.

;; ![So many dots](the_hidden_geometry_of_dice.jpg)

;; There is a formula for calculating the nth triangular number:
;; $T_n = 1 + 2 + 3 + \dots + n = \frac{n(n + 1)}{2}$

;; The 20th triangular number is `(20x21)/2 = (400+20)/2 = 210`.
;; Isn't it wonderful how there's so many different ways to find the same answer in math?

;; There is something curious about the formula; it divides by 2 but only produces integers.
;; How can we be certain we will only ever get an integer?
;; So mysterious.
;; `n(n+1)` is always even!
;; Let's think about that a bit more, if n is odd, then n+1 is even.
;; If n is even, then n+1 is odd.
;; One of the multiples is always even, meaning that 2 is a factor,
;; so the multiple must always have a factor 2, and be even.

;; It's easy now to imagine if we had a 1000 sided dice what the answer would be.
;; But be careful! For a 1000 side die, we want the 999nth triangular number:
;; $(999 \times 1000)/2 = (1000000-1000)/2 = 500000-500 = 499500$
;; and the total outcomes would be `1000x1000`, so the answer would be `0.4995`.
;; It's comforting to see that for a large range, we land closer to 50%.

;; The point is that once we know what we are counting,
;; it feels more obvious that we used the right formula to count it.

;; Triangular numbers show up in many situations,
;; my favorite is that they can be used to lay out hexagons.
;; The [code that draws Clojure Civitas hexagons](https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/civitas/explorer/geometry.clj)
;; is based on the triangular number formula.

^:kindly/hide-code
(let [layers 4
      r 80
      vr (* layers r 2)]
  ^:kind/hiccup
  [:svg {:xmlns   "http://www.w3.org/2000/svg"
         :viewBox [(- vr) (- vr) (* 2 vr) (* 2 vr)]
         :width   "100%"}
   (for [[x y] (->> (geom/cube-spiral layers)
                    (map geom/cube-to-cartesian-flat))]
     (let [x (* r x)
           y (* r y)]
       [:g {:transform (str "translate(" x "," y ")")}
        (svg/polygon {:fill "lightblue"} (geom/hex (* 0.9 r)))]))])

;; Triangular numbers also show up  in the number of pairs,
;; handshakes, edges in a complete graph, diagonals sum to triangular numbers,
;; square numbers as sums of consecutive odd numbers, differences of triangulars,
;; acceleration frames, smooth transitions, spacing.
;; Such a beautiful pattern that can be found in so many situations!

;; Until next time, may your dice be fair and your outcomes interesting.
