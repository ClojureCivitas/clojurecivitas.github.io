^{:kindly/hide-code true     ; don't render this code to the HTML document
  :clay             {:title  "About Transliteration"
                     :quarto {:author   :echeran
                              :type     :post
                              :date     "2025-06-08"
                              :category :clojure
                              :tags     [:internationalization :i18n :transliteration :text
                                         :string :transformation]}}}
(ns internationalization.transliteration
  (:require [clj-thamil.format :as fmt]))

;; Transliteration is about systematically converting the way in which text encodes
;; language (or information) from one writing system (or convention or format) to
;; another.
;;
;; We most commonly think of this for human languages, when converting the sounds
;; spoken in a language from one writing system to another (ex: Chinese language
;; sounds written as ideographs into English language sounds written in the Latin
;; script).
;;
;; The idea of transliteration can be thought of more generically for computers
;; that need to transform text or even file formats.


(def translit-map
  "This map defines a transliteration scheme for transforming text, in this case,
  from Latin script character sequences (of English words) into emojis.

  We define our transformation mappings in a map. In this way, it looks a lot like an
  input to the Clojure `replace` function. This map will be used as an input for the prefix tree
  (a.k.a. trie) data struture used to convert."
  {"happy" "🙂"
   "happier" "😀"
   "happiest" "😄"})

(def translit-trie
  "Create the prefix tree (a.k.a. trie) data structure based on our transliteration mappings
  map that defines our transliteration."
  (fmt/make-trie translit-map))

;; A prefix tree is also called a trie. A prefix tree is a way to store a collection of
;; sequences (ex: strings) efficiently when there is a lot of overlapping prefixes among
;; the strings.
;;
;; A dictionary for an alphabetic language is a good example of when a prefix tree is
;; efficient in space. Imagine all of the words in a single page of the dictionary.
;; It could look like "cat", "catamaran", "catamount", "category", "caternary", etc.
;; It could instead be stored as:
;;
;; ```
;; c - a - t *
;;            a - m
;;                   a - r - a - n *
;;                   o - u - n - t *
;;            e
;;               g - o - r - y *
;;               r - n - a - r - y *
;; ```

;; Why would we use a prefix tree? Even if the source text patterns in the replacement rules are
;; overlapping, we could perform replacement without a tree if we order the replacement rules
;; by the source text pattern, such that a pattern that contains another pattern is applied earlier.
;; However, to perform this ordering in a globally scalable way would effectively require
;; constructing a prefix tree. Furthermore, a map of rules better models the notion of rules being
;; independent data that are not complected with other rules. Also, as the number of rules increases,
;; there may be performance benefits in terms of lookup in a prefix tree versus attempting to apply
;; all rules in the ruleset sequentially.

;; Let's introspect into our prefix tree. Let's see which input strings have a
(fmt/in-trie? translit-trie "hap")
(fmt/in-trie? translit-trie "happy")
(fmt/in-trie? translit-trie "happier")
(fmt/in-trie? translit-trie "happiest")
(fmt/in-trie? translit-trie "happiest!")

(def s "Hello, world! Happiness is not being happiest or happier than the rest, but instead just being happy.")

(defn convert
  "Use our translit-trie to convert the input string into the output string"
  [s]
  (->> (fmt/str->elems translit-trie s)
       (apply str)))

(def converted
  "Create the converted string according to our transliteration rules."
  (convert s))

converted

;; It's worth noting that a prefix tree, when used to do transliteration conversions, is
;; effectively the finite state machine (FSM) needed to parse and transform.
;;
;; For next time: What if we implicitly did that same conversion by constructing a regular expression (regex)
;; that can match on the input patterns. Could that be equally fast, or faster than our naive Clojure
;; implementation? A regex might work like so:
;;
;; ```js
;; let text = "this is a test";
;; const replacementMap = { 'th': 'X', 't': 'Y' };
;;
;; let result = text.replace(/th|t/g, (match) => {
;;                                                return replacementMap[match];
;;                                                });
;;
;; console.log(result);
;; ```