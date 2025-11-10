^{:kindly/hide-code true
  :clay {:title "Tableplot Tutorial: Customizing Plots with Parameter Substitution"
         :quarto {:author [:daslu]
                  :description "Learn how to customize Tableplot visualizations using substitution parameters."
                  :category :clojure
                  :type :post
                  :draft true
                  :date "2025-11-10"
                  :tags [:data-visualization :tableplot :tutorial]}}}
(ns data-visualization.tableplot-parameter-flow
  (:require [scicloj.kindly.v4.kind :as kind]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]))

^:kindly/hide-code
(kind/hiccup
 [:style
  ".printedClojure {
  max-height:600px;
  overflow-y: auto;
}"])

;; ## Introduction
;;
;; [Tableplot](https://scicloj.github.io/tableplot/) is a declarative plotting library
;; that makes it easy to create interactive visualizations from tabular data. One of its
;; most powerful features is **parameter substitution** - the ability to customize plot
;; appearance and behavior by passing parameters that override defaults.
;;
;; This tutorial is a brief intro to this feature.

;; ## The Challenge: Customizing Grid Colors
;;
;; Let's start with a basic dataset and plot.

;; Assume we have some data.

(def sample-data
  (tc/dataset {:x [1 2 3 4 5]
               :y [2 4 3 5 7]}))

sample-data

;; In this tutorial, we'll use Tableplot's [Plotly.js](https://plotly.com/javascript/) API,
;; which generates interactive Plotly.js visualizations. Tableplot also supports other backends
;; like Vega-Lite and an experimental transpilation API.

;; We can make a basic line plot. This is really easy, because the
;; `:x` and `:y` columns are used by default for the plot's axes.

(-> sample-data
    plotly/layer-line)

;; Assume that we now wish to colour the grid lines: vertical by green,
;; horizontal by red. After all, what would be a
;; better way to teach Tufte's [data-ink ratio](https://infovis-wiki.net/wiki/Data-Ink_Ratio) principle than doing exactly
;; what it asks us to avoid, by adding some chartjunk?

;; Here are a few ways we can do it.

;; ### A brief look inside
;;
;; *(can skip on first read)*

;; By default, when used in [Kindly](https://scicloj.github.io/kindly-noted/)-compatible
;; tools like [Clay](https://scicloj.github.io/clay/) and in Clojure Civitas posts,
;; Tableplot's plots are configured to be displayed visually.

;; But we can also change the `kind` annotation, so that we can see them as plain
;; Clojure data structures.

(-> sample-data
    plotly/layer-line
    kind/pprint)

;; You see, what API functions such as `plotly/layer-line` generate are
;; certain maps called
;; [templates](https://github.com/jsa-aerial/hanami?tab=readme-ov-file#templates-substitution-keys-and-transformations),
;; a brilliant concept from the [Hanami](https://github.com/jsa-aerial/hanami) library.

;; This is not the resulting Plotly.js specification yet.
;; It is a potential for it, specifying lots of partial intermediate
;; values, called substitution keys.
;; By Tableplot's convention, substitution keys are keywords beginning with `=`,
;; such as `:=layout` or `:=mark-color`.
;;
;; **Why templates?** They separate *what you want* (data mappings, colors, sizes)
;; from *how to render it* (the actual Plotly.js specification). This gives you
;; flexibility: you can override specific details or let defaults handle everything.
;;
;; Substitution keys can have default values, which can also be functions computing
;; them from the values defined by other keys. On the user side,
;; we may override any of these, as we'll see below.

;; What if we actually want to see not the template, but the resulting
;; Plotly.js specification? This is what `plotly/plot` is for.

(-> sample-data
    plotly/layer-line
    plotly/plot
    kind/pprint)

;; ## Using the relevant substitution keys

;; Sometimes, what we need can be precisely specified in Tableplot.
;; You may find the following in Tableplot's
;; [Plotly API reference](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#yaxis-gridcolor):

;; - [`:=xaxis-gridcolor`](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#xaxis-gridcolor) - The color for the x axis grid lines
;; - [`:=yaxis-gridcolor`](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#yaxis-gridcolor) - The color for the y axis grid lines

;; To use them, you can add a `base` before your plot layers,
;; and configure it with these keys:

(-> sample-data
    (plotly/base {:=xaxis-gridcolor "green"
                  :=yaxis-gridcolor "red"})
    plotly/layer-line)

;; ### A brief look inside
;;
;; *(can skip on first read)*

;; Let us see what actually has changed in the 
;; resulting specification:

(-> sample-data
    (plotly/base {:=xaxis-gridcolor "green"
                  :=yaxis-gridcolor "red"})
    plotly/layer-line
    plotly/plot
    kind/pprint)

;; ## Overriding a broader-scope key

;; Sometimes, you will not find exactly what you need in Tableplot's
;; parameter system. Plotly.js itself will always be richer and more
;; flexible.

;; Imagine that the above `:=xaxis-gridcolor` & `:=yaxis-gridcolor` would
;; not be supported.

;; If you read about
;; [Styling and Coloring Axes and the Zero-Line](https://plotly.com/javascript/axes/#styling-and-coloring-axes-and-the-zero-line)
;; in the Plotly.js docs, you will
;; see that, under the `layout` part of the specification,
;; you can specificy `gridcolor` for each of the axes.

;; In Tableplot, we can specify the whole layout using `:=layout`, and thus
;; have anything we need in there.

;; - [`:=layout`](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#layout) - The layout part of the resulting Plotly.js specification

(-> sample-data
    (plotly/base {:=layout {:xaxis {:gridcolor "green"}
                            :yaxis {:gridcolor "red"}}})
    plotly/layer-line)

;; Notice that a few other details of the aesthetics have changed,
;; like the plot's background color.

;; That is what happens when we override the whole `:layout`.
;; It is a powerful option, that you may or may not like, depending on
;; your use case.

;; ### A brief look inside
;;
;; *(can skip on first read)*

;; Let us see what happens:

(-> sample-data
    (plotly/base {:=layout {:xaxis {:gridcolor "green"}
                            :yaxis {:gridcolor "red"}}})
    plotly/layer-line
    plotly/plot
    kind/pprint)

;; As expected this time, the layout is small and simple,
;; just what you specified.

;; By the way, if you read further in that link to the docs, you will see
;; that `:=layout` depends on `:=xaxis-gridcolor` and `:=yaxis-gridcolor`,
;; among other things. When we specified those narrow-scope keys
;; in our previous example, we actually went through affecting the
;; broad-scope key, `:=layout`.

;; ## Direct Manipulation After `plotly/plot`

;; Sometimes, you wish to work with Plotly.js high-level notions,
;; but in a more refined way, preserving most of what we have.

;; Can we do it?

;; Of course, the answer has been was in front of us the whole time:
;; [It's just data](https://www.youtube.com/watch?v=jlPaby7suOc&t=1000s).

;; We do not need to use Tableplot's API for everything.
;; After we call `plotly/plot`, we can process the actual Plotly.js
;; specification, as data.

(-> sample-data
    plotly/layer-line
    plotly/plot
    (assoc-in [:layout :xaxis :gridcolor] "green")
    (assoc-in [:layout :yaxis :gridcolor] "red"))

;; ### A brief look inside
;;
;; *(can skip on first read)*

;; You already know what to expect here:

(-> sample-data
    plotly/layer-line
    plotly/plot
    (assoc-in [:layout :xaxis :gridcolor] "green")
    (assoc-in [:layout :yaxis :gridcolor] "red")
    kind/pprint)

;; ## Summary
;;
;; Tableplot's parameter substitution system gives you three levels of control:
;;
;; 1. **Specific substitution keys** (`:=xaxis-gridcolor`, `:=yaxis-gridcolor`)
;;    - ✅ Most convenient and discoverable
;;    - ✅ Preserves all other defaults
;;    - ❌ Limited to what Tableplot explicitly supports
;;
;; 2. **Broad-scope keys** (`:=layout`)
;;    - ✅ Full Plotly.js flexibility
;;    - ✅ Declarative, within Tableplot's API
;;    - ❌ Overrides ALL defaults for that scope
;;
;; 3. **Direct data manipulation** (`assoc-in` after `plotly/plot`)
;;    - ✅ Complete control
;;    - ✅ Surgical precision - only change what you want
;;    - ❌ More verbose
;;    - ❌ Leaves Tableplot's template system
;;
;; The key insight: **it's all just data**. Templates with substitution keys give you
;; flexibility without magic. You can always drop down to plain Clojure data manipulation
;; when needed.
;;
;; For more examples and the complete API reference, see the
;; [Tableplot documentation](https://scicloj.github.io/tableplot/).
