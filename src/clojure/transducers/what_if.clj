^{:kindly/hide-code true
  :clay             {:title  "What if... we were taught transducers first?"
                     :exception-continue true
                     :quarto {:author   :seancorfield
                              :type     :post
                              :date     "2025-05-31"
                              :category :clojure
                              :tags     [:transducers]}}}
(ns clojure.transducers.what-if)

;; Most Clojure tutorials start out with sequence functions like `map`,
;; `filter` etc, and then explain how to avoid some of the problems that
;; lazy sequences can cause. Transducers tend to be introduced later as a
;; more advanced topic, but I'd argue that they could (and should) be taught
;; earlier, and instead treat lazy sequences as an advanced topic.

;; What if... we were taught transducers first?

;; We're typically taught to use `map` or `filter` on a sequence or collection
;; to produce a new sequence -- and there's often a comment that `map` applied
;; to a vector does not produce a vector. With transducers, one of the key
;; concepts is that the transformation is separated from the input and
;; also from the output.

;; Let's start out with the `sequence` function, just to show how we can go
;; straight to a sequence of results:

(sequence (map inc) (range 5))

;; `sequence` works with multiple collections, like `map`:

(sequence (map *) (range 5) (range 5) (range 5))
(sequence (map vector) (range 5) (range 5) (range 5))

;; How about chaining several transformations together? We can use `eduction`
;; (but see the caveats below about `eduction`):

(eduction (filter even?) (map inc) (range 10))

;; Let's look at producing different types of output, using `into`:

(into [] (map inc) (range 5))
(into #{} (map inc) (range 5))

;; Under the hood, `into` uses `conj` so if you use a list, the order is
;; reversed (because `conj` onto a list prepends items, whereas `conj` onto
;; a vector appends items):

(into () (map inc) (range 5))

;; For the next level of control, we can use `transduce` to specify how to
;; combine the results, as well as what we start with initially:

(transduce (map inc) conj [] (range 5))
(transduce (map inc) conj #{} (range 5))
(transduce (map inc) conj () (range 5))

;; We might be tempted to use `cons` here, but its argument order is different
;; from `conj` so this will fail:

(transduce (map inc) cons () (range 5))

;; Okay, well, let's use an anonymous function to reverse the order of the
;; arguments:

(transduce (map inc) #(cons %2 %1) () (range 5))

;; Why is it trying to call our `cons` wrapper with a single argument? In addition to
;; separating the transformation from the output, `transduce` also has a
;; "completion" step, which is performed on the final result. A convenience
;; function called `completing` can be used to wrap the function here to
;; provide a "no-op" completion:

(transduce (map inc) (completing #(cons %2 %1)) () (range 5))

;; `completing` lets us provide a "completion" function (instead of the
;; default which is `identity`) so we could reverse the result:

(transduce (map inc) (completing #(cons %2 %1) reverse) () (range 5))

;; Instead of producing a collection result, we can also use `transduce` to
;; compute results in other ways:

(transduce (map inc) + 0 (range 5))
(transduce (map inc) * 1 (range 5))

;; The way `(transduce xf f init coll)` works is pretty much like this:

;; ``` clojure
;; (let [rf (xf f)]
;;   (rf (reduce rf init coll)))
;; ```

;; Note how `xf` and `f` are combined to create a single "reducing function"
;; (which is a 2-arity function that takes an accumulator and a value), for
;; the `reduce` and then also applied to the result as a 1-arity function,
;; which is what `completing` does for us in the above example.

;; Now let's circle back to chaining transformations, while also controlling
;; the output type. We can use `comp` for this. As a recap, here's our
;; `eduction` from earlier:

(eduction (filter even?) (map inc) (range 10))

;; We can compose multiple transducers:

(comp (filter even?) (map inc))

;; Let's give this a name:

(def evens+1 (comp (filter even?) (map inc)))

(into [] evens+1 (range 10))
(into #{} evens+1 (range 10))

;; We glossed over the result of `eduction` earlier -- it produced a sequence
;; because we printed it out, but it is a "reducible" that has captured both
;; its input and the series of transformations to apply, so we could pass it
;; directly to `into` or `transduce` as if it were a collection:

(into [] (eduction (filter even?) (map inc) (range 10)))
(into [] (eduction evens+1 (range 10)))

;; Because it is a "reducible", it only does work when it is consumed, so it
;; is "lazy" in that sense, but it is not a lazy sequence. We can get a lazy
;; sequence from a transducer using `sequence`, if we want, or we can rely
;; on `into` and `transduce` etc being eager. In addition, `eduction` performs
;; the transformations each time it is consumed:

(let [s (eduction (map #(inc (doto % println))) (range 5))]
  [(into [] s)
   (into [] s)])

;; That will print 0 1 2 3 4 twice, because the `eduction` is consumed twice.
;; Compare that behavior to `sequence`, which produces a lazy sequence and
;; caches its results:

(let [s (sequence (map #(inc (doto % println))) (range 5))]
  [(into [] s)
   (into [] s)])

;; This will only print 0 1 2 3 4 once.

;; In conclusion,
;; by separating the transformation from the input and the output, we gain
;; expressive power, flexibility, and reuse: we can compose transducers, we
;; can apply them to any input that produces values, and consume the results
;; in any way we like.

;; For example, transducers can be used in several different ways with
;; `core.async` channels:

;; * [on a `chan`nel](https://clojure.github.io/core.async/clojure.core.async.html#var-chan)
;; * [in a `pipeline`](https://clojure.github.io/core.async/clojure.core.async.html#var-pipeline)
;; * [or consumed with `transduce`](https://clojure.github.io/core.async/clojure.core.async.html#var-transduce)
