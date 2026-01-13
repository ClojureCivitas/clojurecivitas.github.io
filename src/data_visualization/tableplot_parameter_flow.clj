^{:kindly/hide-code true
  :clay {:title "Tableplot Tutorial: Customizing Plots with Parameter Substitution"
         :quarto {:author [:daslu]
                  :description "Learn how to customize Tableplot visualizations using substitution parameters."
                  :category :clojure
                  :type :post
                  :date "2025-11-11"
                  :tags [:data-visualization :tableplot :tutorial]
                  :image "tableplot-parameter-flow.png"}}}
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

;; ::: {.callout-tip collapse="true"}
;; ### Background: The Layered Grammar of Graphics
;;
;; Tableplot is inspired by the [**layered grammar of graphics**](https://vita.had.co.nz/papers/layered-grammar.html),
;; a framework for understanding and building statistical visualizations. Originally developed by
;; Leland Wilkinson and later refined by Hadley Wickham in ggplot2, the grammar views plots as
;; compositions of independent components: data, aesthetic mappings, geometric objects, scales,
;; coordinates, and facets.
;;
;; The challenge in implementing such a grammar is achieving multiple goals simultaneously:
;;
;; - **Succinct**: Simple things should be simple - sensible defaults for common cases
;; - **Declarative**: Describe *what* you want, not *how* to draw it
;; - **Flexible**: Support customization without sacrificing simplicity
;; - **Observable**: Make the details visible and understandable when needed
;; - **Extensible**: Allow users to work with internals without breaking abstractions
;;
;; Tableplot addresses these challenges by mostly adopting [Hanami](https://github.com/jsa-aerial/hanami)'s
;; solution as a starting point. Hanami introduced a template-based approach with substitution keys,
;; allowing hierarchical defaults: you can rely on conventions for quick plots, or override specific
;; details when needed. The templates approach makes the transformation process observable,
;; as we'll see in this tutorial.
;;
;; **Further reading:**

;; - [ggplot2: Elegant Graphics for Data Analysis](https://ggplot2-book.org/) - The definitive guide showing how to balance simplicity and flexibility
;; - [Demystifying stat_ layers in ggplot2](https://yjunechoe.github.io/posts/2020-09-26-demystifying-stat-layers-ggplot2/) - June Choe's exploration of how the grammar elegantly handles data transformations, with special focus on making the internals observable and extensible
;; - [Analyzing Data with Clojure (Kevin Lynagh, 2012)](https://www.youtube.com/watch?v=xyGggdg31mc) - An early Clojure attempt to handle the challenge of building a grammar of graphics

;; :::

;; ## A toy challenge: Customizing Grid Colors
;;
;; Let's start with a basic dataset and plot.

;; We will use:
;; [Kindly](https://scicloj.github.io/kindly-noted/) for annotating visualizations,
;; [Tablecloth](https://scicloj.github.io/tablecloth/) for table processing, and
;; [Tableplot](https://scicloj.github.io/tableplot/) for plotting.


(require 
 '[scicloj.kindly.v4.kind :as kind]
 '[tablecloth.api :as tc]
 '[scicloj.tableplot.v1.plotly :as plotly])

(def sample-data
  (tc/dataset {:x [1 2 3 4 5]
               :y [2 4 3 5 7]}))

;; In this tutorial, we'll use Tableplot's [Plotly.js](https://plotly.com/javascript/) API,
;; which generates interactive Plotly.js visualizations. Tableplot also supports other backends
;; like Vega-Lite and an experimental transpilation API.

;; We can make a basic plot with two layers.
;; This is really easy with our data, because the
;; `:x` and `:y` columns are used by default for the plot's axes.

(-> sample-data
    (plotly/layer-point {:=mark-size 20})
    plotly/layer-line)

;; ::: {.callout-tip collapse="true"}
;; ### A brief look inside

;; By default, when used in [Kindly](https://scicloj.github.io/kindly-noted/)-compatible
;; tools like [Clay](https://scicloj.github.io/clay/) and in Clojure Civitas posts,
;; Tableplot's plots are configured to be displayed visually.

;; But we can also change the `kind` annotation, so that we can see them as plain
;; Clojure data structures.

(-> sample-data
    (plotly/layer-point {:=mark-size 20})
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
    (plotly/layer-point {:=mark-size 20})
    plotly/layer-line
    plotly/plot
    kind/pprint)

;; :::

;; ### Goal

;; Assume that we now wish to colour the grid lines: vertical by green,
;; horizontal by red. After all, what would be a
;; better way to teach Tufte's [data-ink ratio](https://infovis-wiki.net/wiki/Data-Ink_Ratio) principle than doing exactly
;; what it asks us to avoid, by adding some chartjunk?

;; Here are three approaches, each with different tradeoffs.

;; ## Approach 1: Using the relevant substitution keys

;; Sometimes, what we need can be precisely specified in Tableplot.
;; You may find the following in Tableplot's
;; [Plotly API reference](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#yaxis-gridcolor):

;; - [`:=xaxis-gridcolor`](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#xaxis-gridcolor) - The color for the x axis grid lines
;; - [`:=yaxis-gridcolor`](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#yaxis-gridcolor) - The color for the y axis grid lines

;; To use them, you can add a `base` before your plot layers,
;; and configure it with these keys. We use `plotly/base` because its
;; parameters flow to all subsequent layers, which is useful when
;; composing multiple layers with shared settings.

(-> sample-data
    (plotly/base {:=xaxis-gridcolor "green"
                  :=yaxis-gridcolor "red"})
    (plotly/layer-point {:=mark-size 20})
    plotly/layer-line)

;; ::: {.callout-tip collapse="true"}
;; ### A brief look inside

;; Let us see what actually has changed in the 
;; resulting specification:

(-> sample-data
    (plotly/base {:=xaxis-gridcolor "green"
                  :=yaxis-gridcolor "red"})
    (plotly/layer-point {:=mark-size 20})
    plotly/layer-line
    plotly/plot
    kind/pprint)

;; :::

;; ## Approach 2: Overriding a broader-scope key

;; What if the specific keys you need don't exist in Tableplot yet?
;; 
;; Plotly.js itself will always be richer and more flexible than Tableplot's
;; parameter system.

;; Imagine that the above `:=xaxis-gridcolor` & `:=yaxis-gridcolor` would
;; not be supported.

;; If you read about
;; [Styling and Coloring Axes and the Zero-Line](https://plotly.com/javascript/axes/#styling-and-coloring-axes-and-the-zero-line)
;; in the Plotly.js docs, you will
;; see that, under the `layout` part of the specification,
;; you can specify `gridcolor` for each of the axes.

;; In Tableplot, we can specify the whole layout using `:=layout`, and thus
;; have anything we need in there.

;; - [`:=layout`](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#layout) - The layout part of the resulting Plotly.js specification

(-> sample-data
    (plotly/base {:=layout {:xaxis {:gridcolor "green"}
                            :yaxis {:gridcolor "red"}}})
    (plotly/layer-point {:=mark-size 20})
    plotly/layer-line)

;; ### Oh 🙄 

;; Notice that a few other details of the aesthetics have changed,
;; like the plot's background color.

;; That is what happens when we override the whole `:=layout`.
;; It is a powerful option, that you may or may not like, depending on
;; your use case.

;; ::: {.callout-tip collapse="true"}
;; ### A brief look inside

;; Let us see what happens:

(-> sample-data
    (plotly/base {:=layout {:xaxis {:gridcolor "green"}
                            :yaxis {:gridcolor "red"}}})
    (plotly/layer-point {:=mark-size 20})
    plotly/layer-line
    plotly/plot
    kind/pprint)

;; As expected this time, the layout is small and simple,
;; just what you specified.

;; By the way, if you read about 
;; [`:=layout`](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#layout)
;; in the docs, you will see
;; that `:=layout` depends on `:=xaxis-gridcolor` and `:=yaxis-gridcolor`,
;; among other things. When we specified those narrow-scope keys
;; in our previous example, we actually went through affecting the
;; broad-scope key, `:=layout`.

;; :::

;; ## Approach 3: Direct Manipulation After `plotly/plot`

;; The previous approaches work within Tableplot's API. But what if you need
;; more surgical control — to use Plotly.js concepts while preserving most defaults?

;; Of course we can do that!

;; Of course, the answer has been in front of us the whole time:
;; [It's just data](https://www.youtube.com/watch?v=jlPaby7suOc&t=1000s).

;; We do not need to use Tableplot's API for everything.
;; We can call [`plotly/plot`](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#plot)
;; to realize the actual Plotly.js specification, as data.
;; Then we can keep processing it using the Clojure standard library,
;; which has lovely functions like
;; [`assoc-in`](https://clojuredocs.org/clojure.core/assoc-in).

(-> sample-data
    (plotly/layer-point {:=mark-size 20})
    plotly/layer-line
    plotly/plot
    (assoc-in [:layout :xaxis :gridcolor] "green")
    (assoc-in [:layout :yaxis :gridcolor] "red"))

;; ::: {.callout-tip collapse="true"}
;; ### A brief look inside

;; Let us observe the transformation -- before and after the `assoc-in`:

(-> sample-data
    (plotly/layer-point {:=mark-size 20})
    plotly/layer-line
    plotly/plot
    kind/pprint)

(-> sample-data
    (plotly/layer-point {:=mark-size 20})
    plotly/layer-line
    plotly/plot
    (assoc-in [:layout :xaxis :gridcolor] "green")
    (assoc-in [:layout :yaxis :gridcolor] "red")
    kind/pprint)

;; :::

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
