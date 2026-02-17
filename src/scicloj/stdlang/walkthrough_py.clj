^{:kindly/hide-code true
  :clay             {:title  "Stdlang: Python Walkthrough"
                     :quarto {;;:author   :to-be-determined
                              :type     :draft
                              :date     "2025-12-06"
                              ;;:image    ""
                              :category :libs
                              :tags     [:stdlang]}}}
(ns scicloj.stdlang.walkthrough-py)

;; # Python Walkthrough

;; Welcome to the walkthrough of std.lang and it's interaction with the python runtime.
;; Ideally, the reader should have at least some experience with both clojure and python
;; in order to get the most out of the tutorial as the library allows for seamless interop between
;; a clojure runtime and a python one - whether it is on the server side - node, quickjs, osascript - as well as on the browser and other embedded js environments.


;; ## Setup

;; Let us briefly explore the std.lang transpiler.

;; std.lang can be used in different ways:
;; - generate code for different languages
;; - run the code in different runtimes of those languages

;; To specify a way to use it, we use `l/script`. This will create a runtime
;; for evaluation.

;; see https://github.com/zcaudate-xyz/foundation-base for more info
