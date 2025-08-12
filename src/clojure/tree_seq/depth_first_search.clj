^{:kindly/hide-code true
  :clay {:title  "Depth-first search in Clojure (`tree-seq`)"
         :quarto {:type     :post
                  :author   [:harold]
                  :date     "2025-08-11"
                  :description "Step-by-step development of a depth-first search, using `tree-seq`, to solve a classic puzzle."
                  :image    "5q2.png"
                  :category :clojure
                  :tags     [:tree-seq :puzzle]
                  :keywords [:tree-seq :puzzle]}}}
(ns clojure.tree-seq.depth-first-search)

;; ![eight queens on a chessboard](5q2.png)

;; A [classic puzzle](https://en.wikipedia.org/wiki/Eight_queens_puzzle) involves placing eight queens on a chessboard so that no two are attacking each other.

;; Today, we search out such arrangements, in Clojure.

;; ---

;; Since no solution has two queens on the same rank, a nice way to represent the board with data is as a vector of numbers, each element of the vector a column index for the queen on that rank.

;; For example, the vector `[0 2]` would be a board with two queens, one in the corner and another a knight's move away.

(def board [0 2])

;; We can visualize boards by converting these vectors into so-called [FEN strings](https://en.wikipedia.org/wiki/Forsyth%E2%80%93Edwards_Notation), which can be converted into images by a web service provided by the caring strangers at [chessboardimage.com](https://chessboardimage.com/).

;; First, we obtain the elements of the FEN string as a sequence.

(for [i board] (str i "q" (- 7 i)))

;; FEN strings do not allow zeros (I do not make the rules).

(for [i board] (.replace (str i "q" (- 7 i)) "0" ""))


;; Each rank is delimited with a slash.

(->> (for [i board] (.replace (str i "q" (- 7 i)) "0" ""))
     (clojure.string/join "/"))

;; That goes straight into the chessboardimage.com URL

(->> (for [i board] (.replace (str i "q" (- 7 i)) "0" ""))
     (clojure.string/join "/")
     (format "https://chessboardimage.com/%s.png"))

;; ![two queens on a chessboard](2q5.png)

;; That is the body of a function that converts a board into an image

(defn board->image
  [board]
  (->> (for [i board] (.replace (str i "q" (- 7 i)) "0" ""))
       (clojure.string/join "/")
       (format "https://chessboardimage.com/%s.png")))

;; ---

;; To solve the puzzle, we build a tree of candidate solution boards, the children of each node being boards with a new queen added on the next rank to each square not under attack.

;; To find the squares under attack, we begin by computing the board's ranks.

(map-indexed vector board)

;; Each queen attacks up to three squares on the next rank, so for each slope `m` in -1, 0, 1 and each queen's rank and index, we produce three indexes under attack (`y=mx+b`).

(for [m [-1 0 1]
      [rank i] (map-indexed vector board)]
  (+ i (* m (- (count board) rank))))

;; To compute the candidate squares, we take the set of valid indexes and remove those under attack.

(->> (for [m [-1 0 1]
           [rank i] (map-indexed vector board)]
       (+ i (* m (- (count board) rank))))
     (apply disj (set (range 8))))

;; From those we produce a sequence of child boards.

(->> (for [m [-1 0 1]
           [rank i] (map-indexed vector board)]
       (+ i (* m (- (count board) rank))))
     (apply disj (set (range 8)))
     (map #(conj board %)))

;; That is the body of a function that takes a board, and produces child boards in the tree of candidate solutions.

(defn board->children
  [board]
  (->> (for [m [-1 0 1]
             [rank i] (map-indexed vector board)]
         (+ i (* m (- (count board) rank))))
       (apply disj (set (range 8)))
       (map #(conj board %))))

;; ---

;; We can enumerate all candidate boards with Clojure's `tree-seq`; a function of three arguments, the first is a predicate that is true for nodes with children.

;; In our case, we keep adding queens as long as a board has fewer than eight queens.

^{:kindly/hide-code true} (def ... nil)

(def boards (tree-seq #(< (count %) 8) ... ...))

;; The second argument to `tree-seq` is a function that given a node, produces a sequence of children.

;; We just wrote that function (`board->children`).

(def boards (tree-seq #(< (count %) 8) board->children ...))

;; The third argument to `tree-seq` is the root of the tree, an empty board `[]` will do.

(def boards (tree-seq #(< (count %) 8) board->children []))

;; The solutions to the puzzle are those boards with 8 queens on them.

(def solutions (filter #(= (count %) 8) boards))

;; Of which, there are this many...

(count solutions)

;; The forty-second such solution

(nth solutions 42)

;; As an image

(board->image (nth solutions 42))

;; ![eight queens on a chessboard](5q2.png)

;; 🙇
