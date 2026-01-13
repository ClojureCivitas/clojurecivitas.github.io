^{:kindly/hide-code true
  :clay             {:title  "4Clojure puzzles with twotiles"
                     :quarto {:author   :kloimhardt
                              :type     :post
                              :date     "2025-09-25"
                              :image    "twotiles_4clojure.png"
                              :category :concepts
                              :tags     [:blockly :graphical-programming]}}}

(ns games.twotiles-4clojure
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(def md
  (comp kindly/hide-code kind/md))

^:kindly/hide-code
(kind/hiccup
  [:div
   [:script {:src "https://unpkg.com/blockly/blockly_compressed.js"}]
   [:script {:src "https://unpkg.com/blockly/msg/en.js"}]
   [:script {:src "https://kloimhardt.github.io/twotiles/twotiles_core.js"}]
   [:script (str "var parse = scittle.core.eval_string(twotiles.parse_clj);"
                 "var addBlocks = (ws, code) =>"
                 "Blockly.Xml.appendDomToWorkspace(Blockly.utils.xml.textToDom(parse(code)), ws);")]
   [:script "Blockly.defineBlocksWithJsonArray(twotiles.blocks);"]])

^:kindly/hide-code
(defn ws-hiccup [name height]
  [:div
   [:div {:id name, :style {:height height}}]
   [:script (str "var " name " = Blockly.inject('" name "',"
                 "{'sounds': false, 'scrollbars':false, 'trashcan':false})")]])

^:kindly/hide-code
(def create-ws (comp kindly/hide-code ws-hiccup))

(md "## 4Clojure Problem 110, Pronunciation")

(md "Produce a \"pronunciation\" of a sequence of numbers. For example, [1 1] is pronounced as [2 1] (\"two ones\"), which in turn is pronounced as [1 2 1 1] (\"one two, one one\"). The solution below is taken from the [4Clojure website](https://4clojure.oxal.org)." )

(defn pronounce
  [numbers]
  (mapcat (juxt count first)
          (partition-by identity
                        numbers)))

(doall [(pronounce [1])
        (pronounce [1 1])
        (pronounce [2 1])])

^:kindly/hide-code
(defn add-ws [ws]
  (fn [code]
    (kindly/hide-code
      (kind/scittle
        (list 'js/addBlocks
              ws
              (str code))))))

(md "To memorize this solution, complete the following set of graphical puzzles. This is most fun on mobile devices. Maybe you want to scroll through the workspaces first, to get the idea.")

^:kindly/hide-code
(def add-pr1 (add-ws 'js/pr1))
(add-pr1 'numbers)
(add-pr1 'identity)
(add-pr1 '(:tiles/vert
           (partition-by :tiles/slot
                         :tiles/slot)))
(kind/hiccup (create-ws "pr1" "300px"))


(md "Step two")
^:kindly/hide-code
(def add-pr2 (add-ws 'js/pr2))
(add-pr2 '(:tiles/vert
           (partition-by identity
                         numbers)))
(add-pr2 'first)
(add-pr2 'count)
(add-pr2 '(juxt :tiles/slot :tiles/slot))
(kind/hiccup (create-ws "pr2" "300px"))

(md "Step three")
^:kindly/hide-code
(def add-pr3 (add-ws 'js/pr3))
(add-pr3 '(:tiles/vert
           (partition-by identity numbers)))
(add-pr3 '(juxt count first))
(add-pr3 '(:tiles/vert
           (mapcat :tiles/slot
                   :tiles/slot)))
(kind/hiccup (create-ws "pr3" "300px"))


(md "Step four")
^:kindly/hide-code
(def add-pr4 (add-ws 'js/pr4))
(add-pr4
  '(:tiles/vert
    (mapcat (juxt count first)
            (:tiles/vert
             (partition-by identity
                           numbers)))))
(add-pr4 '[numbers])
(add-pr4 'pronounce)
(add-pr4 '(defn :tiles/slot
            :tiles/slot
            :tiles/slot))
(kind/hiccup (create-ws "pr4" "300px"))

(md "The final result")
((add-ws 'js/pr5)
 '(defn pronounce [numbers]
    (:tiles/vert
     (mapcat (juxt count first)
             (:tiles/vert
              (partition-by identity
                            numbers))))))

(kind/hiccup (create-ws "pr5" "300px"))


(md "## 4Clojure Problem 85, Powerset")

(md "Write a function which generates the power set of a given set. The power set of a set x is the set of all subsets of x, including the empty set and x itself.")

(defn powerset
  [original-set]
  (-> (fn [result e]
        (into result
              (map (fn [x]
                     (conj x e))
                   result)))
      (reduce (hash-set #{ })
              original-set)))

(powerset (hash-set 1 2))

^:kindly/hide-code
(kind/scittle
  '(set! (.-add_ps js/window)
         (fn [code]
           (js/addBlocks js/ps code))))

^:kindly/hide-code
(defn btn-hiccup [tupels]
  (into [:div]
        (map-indexed (fn [i cs]
                       [:button
                        {:onClick
                         (reduce (fn [r c]
                                   (str r "add_ps('" c "');"))
                                 ""
                                 cs)}
                        (str "Step " (inc i))])
                     tupels)))

(md "Again, complete the according puzzles step by step. This time, there is only one singe workspace. Start by clicking the first button.")

^:kindly/hide-code
(kind/hiccup
  (btn-hiccup
    [['(:tiles/vert (map :tiles/slot :tiles/slot))
      '(:tiles/vert (fn [x] :tiles/slot))
      'e
      'x
      '(conj :tiles/slot :tiles/slot)
      'result
      ]
     ['(:tiles/vert (fn [result e] :tiles/slot))
      '(:tiles/vert (into :tiles/slot :tiles/slot))
      'result]
     ['(:tiles/vert (-> :tiles/slot :tiles/slot))
      '(:tiles/vert (reduce :tiles/slot :tiles/slot))
      '(hash-set #{})
      'original-set]]))

(kind/hiccup (create-ws "ps" "500px"))
