^{:kindly/hide-code true     ; don't render this code to the HTML document
  :clay             {:title  "More on transliteration"
                     :quarto {:author   :echeran
                              :type     :post
                              :date     "2025-06-22"
                              :category :clojure
                              :tags     [:internationalization :i18n :transliteration :text
                                         :string :transformation :regex :icu :tree :graph :traversal]}}}
(ns internationalization.transliteration2
  (:require [clj-thamil.format :as fmt]
            [clj-thamil.format.convert :as cvt]
            [clojure.string :as str]))

;; In the last post on [transliteration](transliteration.html), I introduced the idea of transliteration
;; as implemented in programming, and pointed out that the process of transforming text is more general.
;; In that regard, the implementation that works for one use case will work for another. Now, the
;; question is what is the most efficient and appropriate implementation?
;;
;; I talked about a prefix tree as easy for storing the sub-/strings to match on. However, in my pure
;; Clojure implementation of a prefix tree, which is implemented using nested maps, the performance is
;; slow. Very slow! But that's not a reflection of Clojure, which is a language that is very practical
;; and optimizes what it can. And the ethos of Clojure programming follows the maxim in programming,
;; stemming
;; [from early Unix, of "make it work, make it right, make it fast"](https://wiki.c2.com/?MakeItWorkMakeItRightMakeItFast).
;; As such, we should think about how to make this fast.
;;
;; Tim asked me why this text transformation couldn't have been implemented in a regex, and doing so
;; would certainly make it fast. For example, to transliterate Tamil language text in Latin script into
;; the Tamil script, my existing implementation would look like:
(def s "vaNakkam. padippavarkaLukku n-anRi.")
(def expected "வணக்கம். படிப்பவர்களுக்கு நன்றி.")
(cvt/romanized->தமிழ் s)
(assert (= expected (cvt/romanized->தமிழ் s)))

;; That transliteration is converting Latin script into Tamil script in a somewhat predictable and intuitive
;; way, such that: `a` -> அ, `aa` -> ஆ, ..., `k` -> க், `ng` -> ங், etc. Tim's point is that you can
;; detect the input substrings using the regex, and then feed the matching substring occurrences into
;; a replacement map to get the translation. His previous pseudocode in JS looked like this:
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
;; He is taking into account the caveat that some of the substrings will overlap or be a superstring of
;; other substrings, and therefore, order matters so that the right "rule" (match + replace) is triggered.
;;
;; This should work. Let's try it. In the "romanized->தமிழ்" function, where the word "romanized" really
;; should be "Latin" for the name of the script, the conversions are more or less defined
;; [here](https://github.com/echeran/clj-thamil/blob/78bb810b2ac73cf05d027b52528ba30118e3720e/src/clj_thamil/format/convert.cljc#L25):
;; Let's just reuse it!
cvt/romanized-தமிழ்-phoneme-map
;; Now to handle the caveat. As you can see, `"t"` is a substring of `"th"`, and both are keys in the map.
;; We effectively have to do a topological sort or some other graph traversal based on which
;; keys are substrings of which other ones. In this particular case, a shortcut that is a huge hack
;; (because it cannot possibly be generalizable) would be to sort the match strings in order of longest to shortest
;; en route to constructing our regex string:
(->> (keys cvt/romanized-தமிழ்-phoneme-map)
     (sort-by count)
     reverse)
;; Our regex string will end up looking like:
(->> (keys cvt/romanized-தமிழ்-phoneme-map)
     (sort-by count)
     reverse
     (interpose \|)
     (apply str))
;; Our regex would be formed by feeding it to `re-pattern`:
(def regex (re-pattern (->> (keys cvt/romanized-தமிழ்-phoneme-map)
                            (sort-by count)
                            reverse
                            (interpose \|)
                            (apply str))))
;; We can do segmentation on the input string based on the transliteration/transformation
;; substring match keys:
(re-seq regex s)
;; We can't naively just transform the strings that match, however. Ex: you would lose the
;; whitespace and punctuation in this example.
(->> (re-seq regex s)
     (map cvt/romanized-தமிழ்-phoneme-map)
     fmt/phonemes->str)
;; So we need to adjust our regex to be smart enough to have a "default branch" that
;; matches the next character if nothing else matches. We do this by appending the match all
;; shortcut `.` to the end of the giant pattern alternation:
(def regex (re-pattern (str (->> (keys cvt/romanized-தமிழ்-phoneme-map)
                                 (sort-by count)
                                 reverse
                                 (interpose \|)
                                 (apply str))
                            "|.")))
;; Now, we get non-matching characters in the output
(->> (re-seq regex s)
     (map #(or (cvt/romanized-தமிழ்-phoneme-map %) %))
     fmt/phonemes->str)
;; And for that matter, since the `.` regex alternation pattern matches a single
;; character anyways, and you're always doing a lookup on what is returned by the
;; regex, we can remove any 1-character length strings from the regex pattern without
;; change in functionality:
(def regex (re-pattern (str (->> (keys cvt/romanized-தமிழ்-phoneme-map)
                                 (sort-by count)
                                 reverse
                                 (remove #(= 1 (count %)))
                                 (interpose \|)
                                 (apply str))
                            "|.")))
;; Check that the output is the same:
(->> (re-seq regex s)
     (map #(or (cvt/romanized-தமிழ்-phoneme-map %) %))
     fmt/phonemes->str)

;; Let's see that the new regex is faster than the slightly older regex, and that
;; they are indeed faster than the unoptimized pure Clojure prefix tree implementation.
(def regex1 (re-pattern (str (->> (keys cvt/romanized-தமிழ்-phoneme-map)
                                 (sort-by count)
                                 reverse
                                 (interpose \|)
                                 (apply str))
                            "|.")))
(def regex2 (re-pattern (str (->> (keys cvt/romanized-தமிழ்-phoneme-map)
                                 (sort-by count)
                                 reverse
                                 (remove #(= 1 (count %)))
                                 (interpose \|)
                                 (apply str))
                            "|.")))

(def NUM-REPS 100)
(time (dotimes [_ NUM-REPS]
        (cvt/romanized->தமிழ் s)))
(time (dotimes [_ NUM-REPS]
        (->> (re-seq regex1 s)
             (map #(or (cvt/romanized-தமிழ்-phoneme-map %) %))
             fmt/phonemes->str)))
(time (dotimes [_ NUM-REPS]
        (->> (re-seq regex2 s)
             (map #(or (cvt/romanized-தமிழ்-phoneme-map %) %))
             fmt/phonemes->str)))

;; Well, this is surprising. I assumed that the regex implementation would be
;; significantly faster. Let's try to investigate.
;;
;; Maybe the difference is less than we thought because `fmt/phonemes->str` is
;; suspiciously inefficient (and also based on the prefix tree code). So what if
;; we strike that out from the above expressions that were timed?
(time (dotimes [_ NUM-REPS]
        (->> (re-seq regex2 s)
             (map #(or (cvt/romanized-தமிழ்-phoneme-map %) %))
             str/join)))
;; So `fmt/phonemes->str` is the culprit. And the implementation of it uses prefix tree
;; code, which is ripe for optimization, perhaps similar to what we just proved
;; here?

