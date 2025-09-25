^{:kindly/hide-code true
  :clay             {:title  "4Clojure puzzles with twotiles"
                     :quarto {:author   :kloimhardt
                              :type     :post
                              :draft    true
                              :date     "2025-09-25"
                              :image    ""
                              :category :concepts
                              :tags     [:blockly :graphical-programming]}}}

(ns games.twotiles-4clojure.clj
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
(defn create-ws [name height]
  [:div
   [:div {:id name, :style {:height height}}]
   [:script (str "var " name " = Blockly.inject('" name "',"
                 "{'sounds': false, 'scrollbars':false, 'trashcan':false})")]])

(md "## 4Clojure")

(defn pronounce [s]
  (mapcat (juxt count first)
          (partition-by identity s)))

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

^:kindly/hide-code
(def add-pr1 (add-ws 'js/pr1))
(add-pr1 's)
(add-pr1 'identity)
(add-pr1 '(partition-by :tiles/slot
                        :tiles/slot))
(kind/hiccup (create-ws "pr1" "300px"))

^:kindly/hide-code
(def add-pr2 (add-ws 'js/pr2))
(add-pr2 '(partition-by identity s))
(add-pr2 'first)
(add-pr2 'count)
(add-pr2 '(juxt :tiles/slot :tiles/slot))
(kind/hiccup (create-ws "pr2" "300px"))

^:kindly/hide-code
(def add-pr3 (add-ws 'js/pr3))
(add-pr3 '(partition-by identity s))
(add-pr3 '(juxt count first))
(add-pr3 '(:tiles/vert
           (mapcat :tiles/slot
                   :tiles/slot)))
(kind/hiccup (create-ws "pr3" "300px"))

^:kindly/hide-code
(def add-pr4 (add-ws 'js/pr4))
(add-pr4
  '(:tiles/vert
    (mapcat (juxt count first)
            (partition-by identity s))))
(add-pr4 '[s])
(add-pr4 'pronounce)
(add-pr4 '(defn :tiles/slot
            :tiles/slot
            :tiles/slot))
(kind/hiccup (create-ws "pr4" "300px"))

((add-ws 'js/pr5)
 '(defn pronounce [s]
    (:tiles/vert
     (mapcat (juxt count first)
             (partition-by identity
                           s)))))

(kind/hiccup (create-ws "pr5" "300px"))

(kind/scittle
  '(defn add-blocks [ws code]
      (js/addBlocks ws (str code))))

(kind/scittle '(add-blocks js/prend '(8 9 10)))

(kind/hiccup (create-ws "prend" "300px"))
