^{:kindly/hide-code true
  :clay {:title "Implementing the Algebra of Graphics in Clojure - Part 1"
         :quarto {:type :post
                  :author [:daslu]
                  :draft true
                  :date "2025-12-26"
                  :description "A Design Exploration for a plotting API"
                  :category :data-visualization
                  :tags [:datavis]
                  :keywords [:datavis]
                  :toc true
                  :toc-depth 4
                  :toc-expand 4}}}
(ns data-visualization.aog-in-clojure-part1
  (:require [tablecloth.api :as tc]
            [scicloj.kindly.v4.kind :as kind]))

^{:kindly/hide-code true
  :kindly/kind :kind/hiccup}
[:style
 ".clay-dataset {
  max-height:400px;
  overflow-y: auto;
}
.printedClojure {
  max-height:400px;
  overflow-y: auto;
}
"]

;; **A Design Exploration for a plotting API**
;;
;; This is the first post in a series documenting the design and implementation of a new
;; compositional plotting API for Clojure. We're exploring fresh approaches to declarative
;; data visualization, drawing inspiration from Julia's [AlgebraOfGraphics.jl](https://aog.makie.org/stable/)
;; while staying true to Clojure's values of simplicity and composability.
;;
;; Each post in this series combines narrative explanation with executable code — this is a
;; working Clojure notebook that can be used with
;; [Kindly](https://scicloj.github.io/kindly/)-compatible
;; tools like [Clay](https://scicloj.github.io/clay/).
;; Code examples are tested using
;; [Clay's test generation](https://scicloj.github.io/clay/clay_book.test_generation.html).
;; You'll see the API evolve from basic scatter plots through faceting, statistical transforms,
;; and support for multiple rendering backends. By the end, we'll have a complete prototype
;; that handles real-world plotting tasks while maintaining an inspectable design.

;; This project continues our work on the
;; [Tableplot](https://scicloj.github.io/tableplot/) plotting library.
;; You may consider the design and implementation here
;; *a draft* for a future library API.
;;
;; ### 📖 A Bit of Context: Tableplot's Journey
;;
;; Before we dive into the technical details, let's talk about where we're coming from.
;;
;; [Tableplot](https://scicloj.github.io/tableplot/) was created in mid-2024 as a pragmatic plotting solution for the
;; [Noj](https://scicloj.github.io/noj/) toolkit—Clojure's growing data science
;; and scientific computing ecosystem. We needed *something* that worked, and we
;; needed it soon. The goal was to add to Noj's offering:
;; a way to visualize data without leaving Clojure or reaching for external tools.
;;
;; Since then, Tableplot's current APIs 
;; ([`scicloj.tableplot.v1.hanami`](https://scicloj.github.io/tableplot/tableplot_book.hanami_walkthrough.html) and
;; [`scicloj.tableplot.v1.plotly`](https://scicloj.github.io/tableplot/tableplot_book.plotly_walkthrough.html)) 
;; have been used in quite a few serious projects.
;;
;; However, we never intended these APIs to be the final word on
;; plotting in Clojure. They were a decent compromise—pragmatic, functional,
;; good enough to be useful. Better designs have been waiting to be explored.
;;
;; ### Learning from the community

;; This post is inspired and affected by past conversations with a few Scicloj-community friends -- in particular:
;; Cvetomir Dimov, who helped idevelop Tableplot, Jon Anthony, who created Hanami,
;; Kira Howe, who initiated the Scicloj exploration of Grammar-of-Graphics a couple of years ago,
;; Harold Hausman, Teodor Heggelond, and most recently, Timoty Pratley, who have had very thoughtful comments about
;; the current Tableplot APIs and internals. Many others have affected out thinking about plotting
;;
;; The feedback from Tableplot users has been invaluable. **Thank you** to everyone
;; who took the time to file issues, ask questions, share use cases, and push the
;; library in directions we hadn't anticipated. Your patience with the rough edges
;; and your insights about what works (and what doesn't) have shaped this next iteration.
;;
;; ### The Real-World Data Dev Group
;;
;; This work is also happening in the context of the
;; [Real-World Data dev group](https://scicloj.github.io/docs/community/groups/real-world-data/),
;; recently reinitiated by Timothy Pratley with new spirit and a stronger focus on
;; open-source collaboration.
;;
;; With that context in mind, let's explore what we're building.

;; # Context & Motivation
;;
;; ## 📖 Why Explore a New Approach?
;;
;; Tableplot currently provides two visualization APIs: `scicloj.tableplot.v1.hanami`
;; for [Vega-Lite](https://vega.github.io/vega-lite/) visualizations, and 
;; `scicloj.tableplot.v1.plotly` for [Plotly.js](https://plotly.com/javascript/).
;; Both have proven useful in real projects, but they've also revealed some constraints
;; that are worth thinking about fresh.
;;
;; The `hanami` API extends the original Hanami library, inheriting its template
;; substitution system with uppercase keywords like `:X` and `:Y`. This brings
;; backwards compatibility requirements that affect both APIs, since `plotly`
;; shares some infrastructure with `hanami`. When you want to change something
;; fundamental, you have to consider the impact across multiple layers.
;;
;; Each API is also tied to a specific rendering target. If you choose `hanami`,
;; you get Vega-Lite—which works for many use cases but has limitations
;; with certain coordinate systems. If you choose `plotly`, you get interactivity
;; but rendering static images programmatically becomes tricky. When you hit a
;; limitation of your chosen rendering target, switching means learning a different API.
;;
;; The intermediate representation between the API and the renderers uses Hanami
;; templates. Template substitution is flexible, but it can be
;; open-ended and difficult to understand when debugging. Error messages sometimes
;; reference template internals rather than your code, and it's not always clear
;; which substitution parameters are valid in which contexts.
;;
;; The APIs also currently expect [tech.ml.dataset](https://github.com/techascent/tech.ml.dataset) 
;; datasets. If you have a simple Clojure map or vector, you need to convert it 
;; first—even for simple visualizations.
;;
;; ## 📖 What We're Exploring
;;
;; Some of these limitations will be addressed within the current APIs themselves—
;; we're actively working on improvements. But as we always intended, it's valuable
;; to explore fresh solutions in parallel. A fresh design lets us ask questions
;; that are harder to answer incrementally: Can we design an API where the intermediate
;; representation is plain maps that are easy to inspect and manipulate with standard
;; operations, while working harmoniously with both a functional interface and multiple
;; rendering targets?
;;
;; This notebook prototypes an alternative compositional API to explore these ideas.
;; It's not meant to replace the existing Tableplot APIs—it might become an additional
;; namespace coexisting with `hanami` and `plotly`, or it might inform future design
;; decisions. This is for Tableplot maintainers, contributors, and curious users who
;; want to provide early feedback on the approach.

                                        ;
;; # 📖 Reading This Document
;;
;; Throughout this document, section headers use emojis to indicate the type of content:
;;
;; - **📖 Narrative sections** - Explanatory text, context, and design discussions
;; - **⚙️ Implementation sections** - Code that implements features (functions, multimethods, helpers)
;; - **🧪 Example sections** - Demonstrations showing the API in action
;;
;; This convention helps you navigate the document and quickly find what you're looking for:
;; conceptual explanations (📖), working code (⚙️), or usage examples (🧪).

;; # Setup

;; ## ⚙️ Dependencies
;;
;; This notebook relies on several libraries from the Clojure data science ecosystem.
;; Here's what we use and why:

(ns data-visualization.aog-in-clojure-part1
  (:require
   ;; Tablecloth - Dataset manipulation
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]

   ;; Kindly - Notebook visualization protocol
   [scicloj.kindly.v4.kind :as kind]

   ;; thi.ng/geom-viz - Static SVG visualization
   [thi.ng.geom.viz.core :as viz]
   [thi.ng.geom.svg.core :as svg]

   ;; Fastmath - Statistical computations
   [fastmath.ml.regression :as regr]
   [fastmath.stats :as stats]

   ;; Malli - Schema validation
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]

   ;; RDatasets - Example datasets
   [scicloj.metamorph.ml.rdatasets :as rdatasets]))

;; [**Tablecloth**](https://scicloj.github.io/tablecloth/) provides our dataset API, wrapping 
;; [tech.ml.dataset](https://github.com/techascent/tech.ml.dataset) with a
;; friendly interface. We use it to manipulate data and access column types.
;;
;; **Key insight**: Tablecloth provides column type information via `tablecloth.column.api/typeof`,
;; which eliminates the need for complex type inference.
;;
;; [**Kindly**](https://scicloj.github.io/kindly-noted/) is the visualization protocol that lets this notebook render
;; plots in different environments ([Clay](https://scicloj.github.io/clay/), [Portal](https://github.com/djblue/portal), etc.). Each rendering target returns
;; a Kindly-wrapped spec.
;;
;; [**thi.ng/geom**](https://github.com/thi-ng/geom) gives us the static SVG target for visualizations that can be saved as images.
;; We specifically use
;; [geom.viz](https://github.com/thi-ng/geom/blob/feature/no-org/org/examples/viz/demos.org) for data visualization.
;;
;; [**Fastmath**](https://github.com/generateme/fastmath) handles our statistical computations, particularly linear
;; regression. It's a comprehensive math library for Clojure.
;;
;; [**Malli**](https://github.com/metosin/malli) provides schema validation for plot specifications.
;; We define schemas for layers, aesthetics, and plot types to catch errors early
;; and generate helpful error messages. Validation is optional but recommended.
;;
;; [**RDatasets**](https://vincentarelbundock.github.io/Rdatasets/articles/data.html) provides classic datasets (penguins, mtcars, iris) for examples.
;; It is made available in Clojure through [metamorph.ml](https://github.com/scicloj/metamorph.ml).

;; # Inspiration: AlgebraOfGraphics.jl
;;
;; This design is inspired by Julia's [AlgebraOfGraphics.jl](https://aog.makie.org/stable/),
;; a visualization library that builds on decades of experience from systems
;; like [ggplot2](https://ggplot2.tidyverse.org/) while introducing clear compositional principles.
;;
;; As the [AoG philosophy](https://aog.makie.org/stable/philosophy) states:

;; > "In a declarative framework, the user needs to express the _question_, and the 
;; > library will take care of creating the visualization."
;;
;; This approach of 
;; ["describing a higher-level 'intent' how your tabular data should be transformed"](https://aog.makie.org/dev/tutorials/intro-i)
;; aligns naturally with Clojure's functional and declarative tendencies—something
;; we've seen in libraries like Hanami, Tableplot, and others in the ecosystem.
;;
;; We chose AoG because it seemed well-thought, small enough to grasp and reproduce, while still being
;; reasonably complete in its scope.

;; ## 📖 Glossary: Visualization Terminology
;;
;; Before diving deeper, let's clarify some terms we'll use throughout:
;;
;; **Domain** - The extent of input values from your data. For a column of bill lengths 
;; containing values 32.1, 45.3, and 59.6, the domain extends from 32.1 to 59.6. This is *data space*.
;;
;; **Range** - The extent of output values in the visualization. For an x-axis that spans 
;; from pixel 50 to pixel 550, the range extends from 50 to 550. This is *visual space*.
;;
;; **Scale** - The mapping function from domain to range. A [linear scale](https://en.wikipedia.org/wiki/Scale_(ratio))
;; maps data value 32.1 → pixel 50, and 59.6 → pixel 550, with proportional mapping in between.
;; Other scale types include logarithmic, time, and categorical.
;;
;; **Aesthetic** - A visual property that can encode data. Common aesthetics: `x` position, 
;; `y` position, `color`, `size`, `shape`, `alpha` (opacity). Each aesthetic needs 
;; a scale to map data to visual values.
;;
;; **Mapping** - The connection between a data column and an aesthetic. "Map `:bill-length` 
;; to `x` aesthetic" means "use the bill-length values to determine x positions."
;;
;; **Coordinate System** - The spatial framework for positioning marks. [Cartesian](https://en.wikipedia.org/wiki/Cartesian_coordinate_system) 
;; (standard x/y grid), [polar](https://en.wikipedia.org/wiki/Polar_coordinate_system) (angle/radius), 
;; and [geographic](https://en.wikipedia.org/wiki/Geographic_coordinate_system) (latitude/longitude) 
;; interpret positions differently and affect domain computation.
;;
;; **Statistical Transform** - A computation that derives new data from raw data. Examples: 
;; [linear regression](https://en.wikipedia.org/wiki/Linear_regression) (fit line), 
;; [histogram](https://en.wikipedia.org/wiki/Histogram) (bin and count), 
;; [kernel density](https://en.wikipedia.org/wiki/Kernel_density_estimation) (smooth distribution).
;;
;; **Layer** (in AoG sense) - A composable unit containing data, mappings, visual marks, 
;; and optional transforms. Different from ggplot2's "layer" (which means overlaid visuals).
;;
;; **Rendering Target** - The library that produces final output: thi.ng/geom, 
;; Vega-Lite, or Plotly.

;; ## 📖 Core Insight: Layers + Operations
;;
;; AlgebraOfGraphics.jl treats visualization with two key ideas:
;;
;; **1. Everything is a [layer](https://aog.makie.org/dev/tutorials/intro-i#Layers:-data,-mapping,-visual-and-transformation)**: 
;; Data, mappings, visuals, and transformations all compose the same way
;;
;; - Data sources
;; - Aesthetic mappings (x → column, color → column)
;; - Visual marks (scatter, line, bar)
;; - Statistical transformations (regression, smoothing)
;;
;; *(Note: This is a different notion from "layers" in
;; the [layered grammar of graphics](https://vita.had.co.nz/papers/layered-grammar.html) used by ggplot2 and Vega-Lite,
;; where layers specifically refer to overlaid visual marks. In AoG, "layer" is the fundamental 
;; compositional unit containing data, mappings, visuals, and transformations.)*
;;
;; **2. Two operations**: `*` (product) and `+` (sum)
;;
;; - `*` **merges** layers together (composition)
;;   ```julia
;;   data(penguins) * mapping(:bill_length, :bill_depth) * visual(Scatter)
;;   # → Single layer with all properties combined
;;   ```
;;
;; - `+` **overlays** layers (overlay)
;;   ```julia
;;   visual(Scatter) + visual(Lines)
;;   # → Two separate visual marks on same plot
;;   ```
;;
;; **3. Distributive property**: `a * (b + c) = (a * b) + (a * c)`
;;
;;   ```julia
;;   data(penguins) * mapping(:bill_length, :bill_depth) * 
;;       (visual(Scatter) + linear())
;;   # ↓ expands to
;;   (data(penguins) * mapping(:bill_length, :bill_depth) * visual(Scatter)) + 
;;   (data(penguins) * mapping(:bill_length, :bill_depth) * linear())
;;   ```
;;
;; This allows factoring out common properties and applying them to
;; multiple plot types without repetition.

;; ## 📖 Comparison to ggplot2
;;
;; ggplot2 uses `+` for everything:
;; ```r
;; ggplot(data, aes(x, y)) + 
;;   geom_point() +
;;   geom_smooth()
;; ```
;;
;; AlgebraOfGraphics separates concerns:
;; ```julia
;; data(df) * mapping(:x, :y) * (visual(Scatter) + smooth())
;; ```
;;
;; **Why two operators?** The separation brings clarity—`*` means "combine properties" 
;; while `+` means "overlay visuals"—and enables composability.

;; ## 📖 Translating to Clojure
;;
;; Julia's approach relies on custom `*` and `+` operators defined on Layer types,
;; using multiple dispatch to handle different type combinations with object-oriented
;; layer representations. This works in Julia's type system.
;;
;; One of our goals here would be bringing this compositional approach while staying
;; true to Clojure idioms: using plain data structures (maps, not objects), enabling
;; standard library operations like `merge`, `assoc`, and `filter`, maintaining the
;; compositional benefits, and making the intermediate representation transparent and
;; inspectable. How do we get the compositional approach of AoG while keeping everything
;; as simple Clojure data?

;; ## 📖 The Algebraic Foundation in Clojure
;;
;; Remarkably, Clojure's standard `merge` and `concat` already exhibit distributive-like
;; properties similar to Julia's `*` and `+`:

;; **Merge distributes over concat:**
(let [shared {:data :penguins}
      layers [{:plottype :scatter} {:plottype :line}]]
  (map #(merge shared %) layers))

;; This mirrors the algebraic property from AoG:
;; ```julia
;;   data(penguins) * (scatter + line) 
;;     = (data(penguins) * scatter) + (data(penguins) * line)
;; ```

;; **Concat preserves structure:**
(concat [{:x :a}] [{:y :b}])

;; **Merge combines properties:**
(merge {:x :a} {:y :b})

;; So we can build `=*` (merge-based) and `=+` (concat-based) that preserve
;; these algebraic properties while working with plain Clojure data. The algebra
;; isn't imposed artificially—it emerges naturally from Clojure's data operations.

;; # Design Overview
;;
;; Before diving into implementation, let's establish what we're building and how it works.
;; This section shows the core data structures and composition model.

;; ## 📖 The Internal Representation (IR)
;;
;; At the heart of this design is a simple data structure: **plot specifications are maps**.
;;
;; A complete plot spec looks like this:

(kind/pprint
 {:=layers [{:=data penguins
             :=x :bill-length-mm
             :=y :bill-depth-mm
             :=plottype :scatter}]
  :=target :geom
  :=width 800
  :=height 600})

;; **Two kinds of properties:**
;;
;; 1. **Layer-level properties** (inside `:=layers` vector) - what to plot:
;;    - `:=data` - the dataset
;;    - `:=x`, `:=y` - aesthetic mappings
;;    - `:=color`, `:=alpha` - visual properties
;;    - `:=plottype` - what kind of mark (`:scatter`, `:line`, `:histogram`)
;;
;; 2. **Plot-level properties** (outside `:=layers`) - how to render:
;;    - `:=target` - rendering backend (`:geom`, `:vl`, `:plotly`)
;;    - `:=width`, `:=height` - dimensions
;;    - `:=scale-x`, `:=scale-y` - custom axis scales
;;    - `:=facet` - faceting specification
;;
;; Each layer is a flat map with distinctive `:=` keys. Why the `=` prefix?
;; It prevents collision with data columns - `:=x` is the aesthetic, `:x` could be your data.

;; ## 📖 The API: Building Plot Specs
;;
;; You rarely write these maps by hand. Instead, constructor functions build them for you:

;; **Layer constructors** return `{:=layers [...]}`:
;; ```clj
;; (data penguins) ; => {:=layers [{:=data penguins}]}
;; (mapping :x :y) ; => {:=layers [{:=x :x :=y :y}]}
;; (scatter) ; => {:=layers [{:=plottype :scatter}]}
;; ```

;; **Plot-level constructors** return property maps:
;; ```clj
;; (target :geom) ; => {:=target :geom}
;; (size 800 600) ; => {:=width 800 :=height 600}
;; (scale :x {:domain [0 100]}) ; => {:=scale-x {:domain [0 100]}}
;; ```

;; ## 📖 How Composition Works
;;
;; Two operators combine these specs, mirroring AoG's algebraic approach:

;; ### The `=*` operator (merge/product)
;;
;; Merges layer properties via cross-product and combines plot-level properties:

;; ```clj
;; (=* {:=layers [{:=x :bill-length}]}
;;     {:=layers [{:=y :bill-depth}]})
;; => {:=layers [{:=x :bill-length :=y :bill-depth}]}
;; ```

;; ```clj
;; (=* {:=layers [{:=data penguins}]}
;;     {:=target :geom})
;; => {:=layers [{:=data penguins}] :=target :geom}
;; ```

;; When both sides have layers, `=*` creates the cross-product:
;; ```clj
;; (=* {:=layers [{:=x :a}]}
;;     {:=layers [{:=y :b} {:=y :c}]})
;; => {:=layers [{:=x :a :=y :b} {:=x :a :=y :c}]}
;; ```

;; ### The `=+` operator (overlay/sum)
;;
;; Concatenates layers with inheritance and merges plot-level properties:

;; ```clj
;; (=+ {:=layers [{:=plottype :scatter}]}
;;     {:=layers [{:=plottype :linear}]})
;; => {:=layers [{:=plottype :scatter} {:=plottype :linear}]}
;; ```

;; Layers on the right inherit properties from common ancestor:
;; ```clj
;; (=+ (=* (data penguins) (mapping :x :y) (scatter))
;;     (linear))
;; ```
;; Both scatter and linear inherit the data and mapping

;; ### Threading macro style
;;
;; Most constructors detect whether they're threading into a spec or standalone:

;; ```clj
;; (-> penguins ;; dataset → {:=layers [{:=data penguins}]}
;;     (mapping :x :y) ;; thread → merges into existing spec
;;     (scatter) ;; thread → merges :=plottype
;;     (target :vl) ;; thread → adds plot-level property
;;     plot) ;; render
;; ```

;; This is equivalent to:
;; ```clj
;; (plot
;;  (=* (data penguins)
;;      (mapping :x :y)
;;      (scatter)
;;      (target :vl)))
;; ```

;; ## 📖 Why This Works: Flat Structure + Standard Merge
;;
;; The key enabling detail: **layers are flat maps, so standard `merge` composes them**.
;;
;; Consider what happens with standard merge:

(merge {:=x :bill-length :=color :species}
       {:=y :bill-depth :=alpha 0.5})

;; Nothing is lost. All properties preserved. This is why `=*` can use
;; standard `merge` internally for layer composition.
;;
;; Compare to a nested structure (what we DON'T do):

(def nested-layer-example
  {:transformation nil
   :data {:bill-length-mm [39.1 39.5 40.3]
          :bill-depth-mm [18.7 17.4 18.0]
          :species [:adelie :adelie :adelie]}
   :positional [:bill-length-mm :bill-depth-mm]
   :named {:color :species}
   :attributes {:alpha 0.5}})

(merge {:positional [:x] :named {:color :species}}
       {:positional [:y] :named {:size :body-mass}})
;; Lost `:x` and `:color!`

;; With nested maps, you'd need a custom `merge-layer` function that knows
;; about your structure. With flat maps, standard Clojure functions work.
;; This means users can manipulate specs with `assoc`, `update`, `dissoc`,
;; `filter`, `map` - all the standard tools.

;; Here's our flat structure for comparison:

(def flat-layer-example
  {:=data {:bill-length-mm [39.1 39.5 40.3]
           :bill-depth-mm [18.7 17.4 18.0]
           :species [:adelie :adelie :adelie]}
   :=x :bill-length-mm
   :=y :bill-depth-mm
   :=color :species
   :=alpha 0.5
   :=plottype :scatter})

;; ## 📖 How Plots are Displayed
;;
;; Plot specifications are plain Clojure data structures (maps with `:=layers` key).
;; To render a plot, use `plot` as the final step in your pipeline.
;; This will create a value annotated by the [Kindly](https://scicloj.github.io/kindly) standard, that can be thus rendered by
;; Kindly-compatible tools such as [Clay](https://scicloj.github.io/clay/).
;;
;; ```clojure
;; ;; Threading style (recommended):
;; (-> penguins
;;     (mapping :bill-length-mm :bill-depth-mm)
;;     (scatter)
;;     plot)
;;
;; ;; Algebraic style:
;; (plot
;;   (=* (data penguins)
;;       (mapping :bill-length-mm :bill-depth-mm)
;;       (scatter)))
;;
;; ;; Inspect the raw spec before rendering:
;; (kind/pprint
;;   (-> penguins
;;       (mapping :bill-length-mm :bill-depth-mm)
;;       (scatter)))
;; ```

;; ## 📖 Implementation Status
;;
;; This notebook implements a working prototype with:
;;
;; **Core features:**
;; - ✅ Compositional operators (`=*`, `=+`) with threading-macro support
;; - ✅ Plain Clojure data (maps, vectors - no dataset required)
;; - ✅ Type-aware grouping (categorical aesthetics create groups)
;; - ✅ Three rendering targets (`:geom`, `:vl`, `:plotly`) with feature parity
;; - ✅ Malli schemas with validation and helpful error messages
;;
;; **Plot types & transforms:**
;; - ✅ Scatter, line, histogram
;; - ✅ Linear regression (with grouping support)
;; - ✅ Faceting (row, column, and grid layouts)
;; - ✅ Custom scale domains
;;
;; **Not yet implemented** (compared to `tableplot.v1.plotly`):
;; - ⚠️ Bar, box, violin, density, smooth, heatmap, text, segment
;; - ⚠️ Additional aesthetics: size, symbol/shape, fill, line-width  
;; - ⚠️ Color gradients: Continuous color scales for numerical columns (currently uses categorical colors only)
;; - ⚠️ Legends: Automatic legend generation for color, size, and other aesthetics
;; - ⚠️ Transforms: kernel density, loess/spline smoothing, correlation
;; - ⚠️ Coordinate systems: 3D, polar, geographic
;; - ⚠️ Advanced layouts: subplots, secondary axes, insets
;; - ⚠️ Interactivity: hover templates, click events, selections
;;
;; **Want to see it in action?** Skip ahead to [Basic Scatter Plots](#basic-scatter-plots) 🎯
;; to see the API working, or continue reading for implementation details.

;; # Implementation Architecture
;;
;; The Design Overview showed *what* we're building and *how* composition works.
;; This section dives into implementation details: how we handle multiple rendering
;; targets, leverage type information, and decide what to compute versus delegate.

;; ## 📖 Rendering Targets
;;
;; **Why support multiple rendering targets?**
;;
;; Different rendering libraries have complementary strengths:
;; - **`:geom`** - Static SVG, easy to save
;; - **`:vl`** - Interactive, but limited coordinate systems
;; - **`:plotly`** - 3D support, but tricky static export
;;
;; By keeping plot specs target-agnostic, you can switch backends to work around
;; limitations without rewriting your visualization code. Write once, render anywhere.
;;
;; **The three targets in detail:**
;;
;; **`:geom`** ([thi.ng/geom-viz](https://github.com/thi-ng/geom))
;; - Static SVG output
;; - Easy to save to files and embed in documents
;; - Requires explicit domain computation (we compute all domains)
;; - Best for: Publication-quality static graphics
;;
;; **`:vl`** ([Vega-Lite](https://vega.github.io/vega-lite/))
;; - Interactive web visualizations with tooltips, zoom, pan
;; - Declarative JSON specification
;; - Excellent automatic layout and domain inference (we only specify custom domains)
;; - Some coordinate system limitations (no native 3D, limited polar support)
;; - Best for: Exploratory data analysis, dashboards
;;
;; **`:plotly`** ([Plotly.js](https://plotly.com/javascript/))
;; - Interactive with strong 3D support
;; - Imperative structure (arrays of traces)
;; - Good automatic layout (we only specify custom domains)
;; - Static export requires additional tooling
;; - Best for: 3D visualizations, complex interactivity
;;
;; **How this works:**
;;
;; 1. Plot specs are backend-agnostic (just data and mappings)
;; 2. The `plot` function dispatches on `:=target` using a multimethod
;; 3. Each backend has `render-layer` methods that convert layers to target-specific format
;; 4. Type-based dispatch: `[target plottype]` → `[:geom :scatter]`, `[:vl :histogram]`, etc.
;;
;; **Specifying the target:**
;;
;; ```clojure
;; ;; 1. Include target in the spec:
;; (-> penguins (mapping :x :y) (scatter) (target :geom) plot)
;;
;; ;; 2. Pass target as argument to plot:
;; (-> penguins (mapping :x :y) (scatter) (plot :geom))
;;
;; ;; These are equivalent - the argument overrides spec's :=target if both present
;; ```
;;
;; **Switching targets:**
;;
;; ```clojure
;; ;; Same spec, different renderings:
;; (def my-plot (-> penguins (mapping :x :y) (scatter)))
;;
;; (plot my-plot :geom)    ;; Static SVG for publication
;; (plot my-plot :vl)      ;; Interactive for exploration
;; (plot my-plot :plotly)  ;; Interactive with zoom/pan
;; ```

;; ## 📖 Delegation in Practice
;;
;; Since we support multiple rendering targets, we need to decide what to compute
;; ourselves versus what to delegate to each target library.
;;
;; **Core Principle: Statistical transforms require domain computation. Everything else delegates.**
;;
;; ### What We Compute
;;
;; **1. Statistical Transforms**
;; - Histogram binning (compute bins, counts before rendering)
;; - Linear regression (fit models, generate predicted points)
;; - Future: density estimation, smoothing, aggregations
;;
;; Why? Transforms derive new data from raw data and need domain information.
;; A histogram needs x-axis extent to compute bin boundaries. A regression needs
;; to know the range to generate fitted points.
;;
;; **2. Domains (Target-Dependent)**
;; - **`:geom`**: We compute ALL domains (geom.viz requires explicit domains)
;; - **`:vl`** and **`:plotly`**: We ONLY specify custom domains (they infer defaults)
;;
;; Why the difference? Geom.viz is a lower-level library that needs explicit scales.
;; Vega-Lite and Plotly have sophisticated automatic domain inference.
;;
;; **3. Type Information and Grouping**
;; - Leverage Tablecloth's `tcc/typeof` for column types
;; - Determine grouping from categorical aesthetics (`:=color :species` → group by species)
;; - Apply grouping to statistical transforms (one regression per group)
;;
;; ### What We Delegate
;;
;; **1. Axis Rendering**
;; - Tick placement and spacing
;; - "Nice number" rounding for tick labels
;; - Axis labels and titles
;; - Grid lines
;;
;; **2. Visual Layout**
;; - Range computation (data → pixel coordinates)
;; - Margin calculations
;; - Legend placement and formatting
;; - Scale merging across multiple layers
;;
;; **3. Interactivity (for `:vl` and `:plotly`)**
;; - Tooltips
;; - Zoom and pan
;; - Hover effects
;; - Click handlers
;;
;; ### Rationale
;;
;; **Why compute transforms ourselves?**
;;
;; 1. **Consistency** - Visualizations should match statistical computations in
;;    our Clojure libraries (fastmath, tablecloth). If we compute a regression
;;    in our code, the plotted line should use the same algorithm.
;;
;; 2. **Efficiency** - Especially with browser-based targets, we want to send
;;    summaries (20 histogram bars) rather than raw data (1M points). Transform
;;    first, then send compact results.
;;
;; 3. **Correctness** - Statistical transforms are complex and need to be right.
;;    We control the implementation and can test it thoroughly.
;;
;; **Why delegate rendering to targets?**
;;
;; 1. **They're better at it** - Vega-Lite, Plotly, and geom.viz have sophisticated
;;    algorithms for tick placement, label formatting, and visual layout developed
;;    over years. Why reimplement?
;;
;; 2. **Leverage their strengths** - Each target has unique capabilities
;;    (Plotly's 3D, Vega-Lite's interactivity, geom.viz's SVG precision). By
;;    delegating, we get all these features for free.
;;
;; 3. **Maintainability** - Visual rendering is complex and evolves. Let the
;;    experts handle it while we focus on the API and data transformations.

;; ## 📖 Type-Driven Behavior
;;
;; **Why column types matter**
;;
;; Tablecloth provides column type information via `tcc/typeof` for free. Rather than
;; requiring users to explicitly declare how each column should be treated, we use
;; type information to make intelligent default choices. This enables a more ergonomic
;; API while maintaining correctness.

(tcc/typeof (penguins :species)) ;; => :string (categorical)
(tcc/typeof (penguins :bill-length-mm)) ;; => :float64 (continuous)

;; **Type-driven behaviors:**
;;
;; ### 1. Grouping for Statistical Transforms
;;
;; Statistical transforms like linear regression often need to group data by
;; categorical variables. When you map `:=color :species`, you probably want
;; separate regression lines for each species.
;;
;; **Grouping rules:**
;; - Categorical aesthetics (`:=color :species`) create groups for transforms
;; - Continuous aesthetics are visual-only (no grouping)
;; - Explicit `:=group` aesthetic for manual control
;;
;; **Example:**
;; ```clojure
;; (-> penguins
;;     (mapping :bill-length-mm :bill-depth-mm {:color :species})
;;     (=+ (scatter) (linear)))
;; ```
;; Automatically produces:
;; - One scatter layer with points colored by species
;; - Three regression lines (one per species)
;;
;; ### 2. Color Scales
;;
;; When mapping a column to the color aesthetic, we choose the appropriate color scale:
;;
;; - **Categorical columns** (`:string`, `:keyword`) → Discrete color palette
;;   - Use distinct, perceptually-separated colors
;;   - Each category gets a unique color from the palette
;;   - Example: Species (Adelie, Gentoo, Chinstrap) → 3 distinct colors
;;
;; - **Continuous columns** (`:float64`, `:int64`) → Color gradient *(not yet implemented)*
;;   - Use sequential or diverging color scales
;;   - Map values smoothly across a color range
;;   - Example: Temperature (0°C to 40°C) → Blue to red gradient
;;
;; **Current implementation:** Only categorical colors are supported. Continuous
;; color gradients are planned for a future version.
;;
;; ### 3. Default Histogram Bins *(future)*
;;
;; The optimal number of histogram bins depends on data characteristics:
;; - **Continuous data** → Use Sturges' or Freedman-Diaconis rule
;; - **Discrete data** → One bin per unique value
;;
;; ### 4. Axis Types *(delegated to rendering targets)*
;;
;; Rendering libraries need to know axis types for proper formatting:
;; - **Categorical** → Evenly spaced tick marks, one per category
;; - **Continuous** → Algorithmically determined tick positions, "nice numbers"
;; - **Temporal** → Date/time-aware formatting and spacing
;;
;; We infer these from column types and pass them to rendering targets.

;; # Malli Schemas
;;
;; Before diving into the API implementation, we define the schemas that validate
;; our plot specifications. These schemas serve three purposes:
;;
;; 1. **Documentation** - They formally specify what constitutes a valid plot
;; 2. **Runtime validation** - They catch errors early with clear messages
;; 3. **Type safety** - They ensure plot construction is correct
;;
;; **You can skip this section** and jump directly to [Basic Scatter Plots](#basic-scatter-plots) 🎯
;; to see the API in action. Come back here later when you want to understand
;; validation details or see what fields are available.
;;
;; **Key schema:** `PlotSpec` defines the complete plot structure with `:=layers`
;; and plot-level properties (`:=target`, `:=width`, `:=height`, scales, etc.).

;; ## ⚙️ Malli Registry Setup
;;
;; Create a registry that includes both default schemas and malli.util schemas.
;; This enables declarative schema utilities like `:merge`, `:union`, `:select-keys`.

(def registry
  "Malli registry with default schemas and util schemas (for :merge, etc.)"
  (merge (m/default-schemas) (mu/schemas)))

;; ## ⚙️ Core Type Schemas

(def DataType
  "Schema for column data types.
  
  - :quantitative - Continuous numeric values
  - :nominal - Categorical/discrete unordered values
  - :ordinal - Categorical/discrete ordered values
  - :temporal - Date/time values"
  [:enum :quantitative :nominal :ordinal :temporal])

(def PlotType
  "Schema for plot/mark types."
  [:enum :scatter :line :area :bar :histogram])

(def Transformation
  "Schema for statistical transformations."
  [:enum :linear :smooth :density :bin :histogram])

(def BinsMethod
  "Schema for histogram binning methods."
  [:or
   [:enum :sturges :sqrt :rice :freedman-diaconis]
   pos-int?])

;; ## ⚙️ Data Schemas

(def Dataset
  "Schema for dataset input.
  
  Accepts:
  - Plain Clojure map with keyword keys and sequential values
  - Vector of maps (row-oriented data)
  - tech.ml.dataset (tablecloth dataset)"
  [:or
   [:map-of :keyword [:sequential any?]]
   [:sequential map?]
   [:fn {:error/message "Must be a tablecloth dataset"}
    tc/dataset?]])

;; ## ⚙️ Aesthetic Schemas

(def ColumnReference
  "Schema for referencing a column in the dataset."
  :keyword)

(def ColumnOrConstant
  "Schema for aesthetics that can be either mapped to a column or set to a constant value.
  
  - Keyword → map from column (e.g., :species)
  - Other value → constant (e.g., \"red\", 0.5)"
  [:or ColumnReference string? number? boolean?])

(def PositionalAesthetic
  "Schema for x or y positional aesthetics."
  [:maybe ColumnReference])

(def ColorAesthetic
  "Schema for color aesthetic.
  
  Can be:
  - Column reference for mapping
  - Constant color string
  - nil (no color mapping)"
  [:maybe ColumnOrConstant])

(def SizeAesthetic
  "Schema for size aesthetic."
  [:maybe ColumnOrConstant])

(def AlphaAttribute
  "Schema for alpha/opacity attribute (constant only)."
  [:maybe [:double {:min 0.0 :max 1.0}]])

(def FacetAesthetic
  "Schema for faceting aesthetics (row, col)."
  [:maybe ColumnReference])

;; ## ⚙️ Scale Schemas

(def ScaleTransform
  "Schema for scale transformations."
  [:enum :identity :log :sqrt])

(def ScaleDomain
  "Schema for scale domain specification."
  [:or
   ;; Continuous domain: [min max]
   [:tuple number? number?]
   ;; Categorical domain: vector of values
   [:vector any?]])

(def ScaleSpec
  "Schema for scale specification."
  [:map
   [:domain {:optional true} ScaleDomain]
   [:transform {:optional true} ScaleTransform]])

;; ## ⚙️ Backend Schemas

(def Backend
  "Schema for rendering backend selection."
  [:enum :geom :vl :plotly])

;; ## ⚙️ Layer Schema

(def BaseLayer
  "Base layer fields shared across all plot types."
  [:map
   ;; Data (required for most layers)
   [:=data {:optional true} Dataset]

   ;; Other aesthetics
   [:=color {:optional true} ColorAesthetic]
   [:=size {:optional true} SizeAesthetic]

   ;; Faceting
   [:=row {:optional true} FacetAesthetic]
   [:=col {:optional true} FacetAesthetic]

   ;; Attributes (constant visual properties)
   [:=alpha {:optional true} AlphaAttribute]

   ;; Transformation
   [:=transformation {:optional true} Transformation]

   ;; Histogram-specific
   [:=bins {:optional true} BinsMethod]

   ;; Scales
   [:=scale-x {:optional true} ScaleSpec]
   [:=scale-y {:optional true} ScaleSpec]
   [:=scale-color {:optional true} ScaleSpec]])

(def Layer
  "Schema for a complete layer specification with plottype-specific requirements.
  
  Uses :multi to dispatch on :=plottype and enforce different requirements:
  - :scatter, :line, :area require both :=x and :=y
  - :bar, :histogram require :=x (y is optional)
  - nil (no plottype) allows incomplete layers for composition
  
  This replaces the nested conditionals in validate-layer with declarative schemas."
  (m/schema
   [:multi {:dispatch :=plottype}

    ;; Scatter requires both x and y
    [:scatter
     [:merge
      BaseLayer
      [:map
       [:=plottype [:enum :scatter]]
       [:=x PositionalAesthetic]
       [:=y PositionalAesthetic]]]]

    ;; Line requires both x and y
    [:line
     [:merge
      BaseLayer
      [:map
       [:=plottype [:enum :line]]
       [:=x PositionalAesthetic]
       [:=y PositionalAesthetic]]]]

    ;; Bar requires x, y optional
    [:bar
     [:merge
      BaseLayer
      [:map
       [:=plottype [:enum :bar]]
       [:=x PositionalAesthetic]
       [:=y {:optional true} PositionalAesthetic]]]]

    ;; Histogram requires x, y optional
    [:histogram
     [:merge
      BaseLayer
      [:map
       [:=plottype [:enum :histogram]]
       [:=x PositionalAesthetic]
       [:=y {:optional true} PositionalAesthetic]]]]

    ;; Area requires both x and y
    [:area
     [:merge
      BaseLayer
      [:map
       [:=plottype [:enum :area]]
       [:=x PositionalAesthetic]
       [:=y PositionalAesthetic]]]]

    ;; Incomplete layer (no plottype) - for composition
    [nil
     [:merge
      BaseLayer
      [:map
       [:=plottype {:optional true} [:maybe nil?]]
       [:=x {:optional true} PositionalAesthetic]
       [:=y {:optional true} PositionalAesthetic]]]]]
   {:registry registry}))

(def Layers
  "Schema for one or more layers.
  
  - Single layer map
  - Vector of layer maps"
  [:or Layer [:vector Layer]])

(def PlotSpec
  "Schema for a plot specification (complete or partial).
  
  A plot spec is a map that can contain:
  - Layers: Vector of layer maps (optional - allows partial specs)
  - Plot-level properties: target, width, height
  - Plot-level scales (optional)
  
  All fields are optional to support composition via =* and =+."
  [:map
   ;; Layers (optional - allows partial specs with just plot-level properties)
   [:=layers {:optional true} [:vector Layer]]

   ;; Plot-level properties (all optional)
   [:=target {:optional true} Backend]
   [:=width {:optional true} pos-int?]
   [:=height {:optional true} pos-int?]

   ;; Plot-level scales (optional)
   [:=scale-x {:optional true} ScaleSpec]
   [:=scale-y {:optional true} ScaleSpec]
   [:=scale-color {:optional true} ScaleSpec]])

;; # Validation Helpers
;;
;; These helper functions validate plot specs and layers, providing clear error messages
;; when something goes wrong. They're used internally by the API constructors and also
;; available for debugging your plots.
;;
;; **Feel free to skip ahead** to [Basic Scatter Plots](#basic-scatter-plots) 🎯 to see
;; the API working - these validation helpers will make more sense once you've seen
;; what they're validating.

;; ## ⚙️ Core Validation Functions

(defn validate
  "Validate a value against a schema.
  
  Returns:
  - nil if valid
  - Humanized error map if invalid
  
  Example:
  (validate Layer {:=data {:x [1 2 3]} :=plottype :scatter})
  ;; => nil (valid)
  
  (validate Layer {:=plottype :invalid})
  ;; => {:=plottype [\"should be one of: :scatter, :line, :area, :bar, :histogram\"]}"
  [schema value]
  (when-not (m/validate schema value)
    (me/humanize (m/explain schema value))))

(defn validate!
  "Validate a value against a schema, throwing on error.
  
  Throws ex-info with humanized error message if invalid.
  
  Example:
  (validate! Layer my-layer)"
  [schema value]
  (when-let [errors (validate schema value)]
    (throw (ex-info "Validation failed"
                    {:errors errors
                     :value value}))))

(defn valid?
  "Check if a value is valid according to a schema.
  
  Returns boolean.
  
  Example:
  (valid? Layer my-layer)"
  [schema value]
  (m/validate schema value))

;; ## ⚙️ Layer-Specific Validation

(defn validate-layer
  "Validate a layer with context-aware checks.
  
  Performs:
  1. Schema validation (structure + plottype-specific requirements via :multi)
  2. Data column validation (columns exist) - runtime check
  
  Returns nil if valid, error map if invalid."
  [layer]
  ;; Schema validation now handles both structure AND plottype-specific requirements
  (or
   (when-let [schema-errors (validate Layer layer)]
     {:type :schema-error
      :errors schema-errors
      :message "Layer validation failed"})

   ;; Data column validation (runtime check - can't be done in schema)
   (when-let [data (:=data layer)]
     (let [column-keys (cond
                         ;; Tablecloth dataset
                         (tc/dataset? data)
                         (set (tc/column-names data))

                         ;; Vector of maps (row-oriented)
                         (and (vector? data) (map? (first data)))
                         (set (keys (first data)))

                         ;; Map of columns (column-oriented)
                         (map? data)
                         (set (keys data))

                         ;; Unknown format, skip validation
                         :else
                         nil)

           ;; Collect all column references from aesthetics
           aesthetic-cols (filter keyword?
                                  [(:=x layer)
                                   (:=y layer)
                                   (when (keyword? (:=color layer)) (:=color layer))
                                   (when (keyword? (:=size layer)) (:=size layer))
                                   (:=row layer)
                                   (:=col layer)])

           ;; Find missing columns
           missing-cols (when column-keys
                          (remove column-keys aesthetic-cols))]

       (when (and column-keys (seq missing-cols))
         {:type :missing-columns
          :missing (vec missing-cols)
          :available (vec (sort column-keys))
          :message (str "Columns not found in dataset: " (vec missing-cols)
                        "\nAvailable columns: " (vec (sort column-keys)))})))

   ;; All validations passed
   nil))

(defn validate-layer!
  "Validate a layer, throwing on error.
  
  Throws ex-info with detailed error information."
  [layer]
  (when-let [error (validate-layer layer)]
    (throw (ex-info (:message error "Layer validation failed")
                    error))))

(defn validate-layers
  "Validate one or more layers.
  
  Returns nil if all valid, map of errors otherwise."
  [layers]
  (let [layer-vec (if (vector? layers) layers [layers])
        errors (keep-indexed (fn [idx layer]
                               (when-let [error (validate-layer layer)]
                                 [idx error]))
                             layer-vec)]
    (when (seq errors)
      {:type :layers-validation-failed
       :errors (into {} errors)})))

(defn validate-layers!
  "Validate one or more layers, throwing on first error."
  [layers]
  (when-let [errors (validate-layers layers)]
    (throw (ex-info "Layer validation failed"
                    errors))))

;; # API Implementation
;;
;; This section implements the core API: the composition operators (`=*`, `=+`),
;; constructors (`data`, `mapping`, `scatter`, etc.), and the rendering function.
;; These are the functions you'll actually call when creating plots.
;;
;; **Want to see it in action first?** Jump to [Basic Scatter Plots](#basic-scatter-plots) 🎯
;; to start creating plots, then come back here if you want to understand how
;; the API is implemented.

;; ## ⚙️ Helper Functions

(defn- plot-spec?
  "Check if x is a plot spec (map with :=layers or plot-level keys).
  
  Plot specs are maps that have at least one key starting with :=
  Uses Malli validation as a fallback check for well-formed specs."
  [x]
  (and (map? x)
       ;; Has at least one := key
       (some #(-> % name first (= \=))
             (keys x))
       ;; Validates against PlotSpec schema (additional safety check)
       (valid? PlotSpec x)))

;; ## ⚙️ Renderer

(defmulti plot-impl
  "Internal multimethod for plot dispatch."
  (fn [spec opts]
    (or (:target opts)
        (:=target spec)
        :geom)))

(defn plot
  "Render a plot specification to a visualization.

  Use `plot` as the final step in a threading chain or wrap a composition:

  Args:
  - spec: Plot spec map with :=layers
  - opts: Optional map with:
    - :width - Width in pixels (default 600)
    - :height - Height in pixels (default 400)
    - :target - Rendering target (:geom, :vl, :plotly)

  The rendering target is determined by:
  1. :target in opts (highest priority)
  2. `:=target` key in spec (set via `target` function)
  3. :geom (static SVG) as default

  Returns:
  - Kindly-wrapped visualization specification

  Examples:
  ;; Threading style (recommended):
  (-> penguins
      (mapping :bill-length-mm :bill-depth-mm)
      (scatter)
      plot)
  
  ;; Composition style:
  (plot (=* (data penguins)
            (mapping :x :y)
            (scatter)))
  
  ;; With options:
  (-> penguins
      (mapping :x :y)
      (scatter)
      (plot {:width 800 :height 600}))"
  ([spec]
   (plot-impl spec {}))
  ([spec opts]
   (plot-impl spec opts)))

(defn- ensure-vec
  "Wrap single items in a vector if not already sequential.
  
  This enables auto-wrapping for ergonomic API:
  - (=* data mapping geom) instead of (=* [data] [mapping] [geom])
  
  Args:
  - x: Either a vector/seq of layers, or a single layer map
  
  Returns:
  - Vector of layers"
  [x]
  (if (sequential? x) x [x]))

;; ## ⚙️ Composition Operators

(defn =*
  "Merge plot specifications (composition).
  
  Handles both layer cross-product and plot-level merge:
  - Layers: Cross-product merge (for [a b], [c d] -> [merge(a,c) merge(a,d) merge(b,c) merge(b,d)])
  - Plot-level properties: Right-side wins
  
  Examples:
  (=* {:=layers [{:=x :a}]} {:=layers [{:=y :b}]})
  ;; => {:=layers [{:=x :a :=y :b}]}
  
  (=* {:=layers [{:=x :a}]} {:=target :geom})
  ;; => {:=layers [{:=x :a}] :=target :geom}"
  ([x] x)
  ([x y]
   (let [;; Extract layers from each spec (default to empty if no :=layers key)
         x-layers (get x :=layers [])
         y-layers (get y :=layers [])

         ;; Extract plot-level properties (everything except :=layers)
         x-plot (dissoc x :=layers)
         y-plot (dissoc y :=layers)

         ;; Cross-product merge of layers
         ;; This implements the "multiplication" semantics:
         ;; - (data d) * (mapping :x :y) => layer with both data and mapping
         ;; - Each layer from x combines with each layer from y
         merged-layers (cond
                         ;; Both have layers: full cross-product
                         ;; Example: [a b] * [c d] => [a+c, a+d, b+c, b+d]
                         (and (seq x-layers) (seq y-layers))
                         (vec (for [a x-layers, b y-layers]
                                (merge a b)))

                         ;; Only x has layers: keep them unchanged
                         (seq x-layers) x-layers

                         ;; Only y has layers: keep them unchanged
                         (seq y-layers) y-layers

                         ;; Neither has layers: result has no layers
                         :else [])

         ;; Merge plot-level properties (right side wins for conflicts)
         ;; This handles properties like :=target, :=width, :=height, scales
         merged-plot (merge x-plot y-plot)]

     ;; Return combined spec
     (cond-> merged-plot
       (seq merged-layers) (assoc :=layers merged-layers))))
  ([x y & more]
   ;; Multi-arg: reduce left-to-right
   (reduce =* (=* x y) more)))

;; Test helper: check if result is a valid layer vector
(defn- valid-layers?
  "Check if x is a valid vector of layers using Malli validation.
  
  Note: This specifically checks for a vector of layers, not a single layer."
  [x]
  (valid? [:vector Layer] x))

(defn =+
  "Combine multiple plot specifications for overlay (sum).
  
  Concatenates layers and merges plot-level properties (last wins).
  
  When used in threading form (-> base (=+ spec1 spec2 ...)), merges each spec
  with the base's layer properties before concatenating, so layers inherit
  data/aesthetics from the base. The base layer itself is not included unless
  it has a plottype.
  
  Examples:
  (=+ {:=layers [{:=plottype :scatter}]} {:=layers [{:=plottype :line}]})
  ;; => {:=layers [{:=plottype :scatter} {:=plottype :line}]}
  
  (-> (mapping penguins :x :y)
      (=+ (scatter) (linear)))
  ;; => {:=layers [{:=data penguins :=x :x :=y :y :=plottype :scatter}
  ;;               {:=data penguins :=x :x :=y :y :=plottype :line :=transformation :linear}]}"
  [& specs]
  (if (= (count specs) 1)
    ;; Single spec: return as-is
    (first specs)

    ;; Multiple specs: overlay with inheritance
    (let [[first-spec & rest-specs] specs
          first-layers (:=layers first-spec [])
          first-plot-props (dissoc first-spec :=layers)

          ;; Extract a "base layer" from first spec to merge with subsequent specs
          ;; This enables inheritance: (-> base (=+ layer1 layer2))
          ;; where layer1 and layer2 inherit properties from base
          base-layer (when (seq first-layers) (first first-layers))

          ;; Check if base layer is complete (has a plottype)
          ;; If not, it's just providing shared properties (data, aesthetics)
          base-has-plottype? (and base-layer (:=plottype base-layer))

          ;; For each subsequent spec, merge with base layer before extracting layers
          ;; This implements inheritance: each new layer gets base's data/aesthetics
          merged-rest-layers (mapcat (fn [spec]
                                       (let [spec-layers (:=layers spec [])]
                                         (if (and base-layer (seq spec-layers))
                                           ;; Merge each layer from this spec with base layer
                                           ;; This is what makes (-> base (=+ s1 s2)) work
                                           (map #(merge base-layer %) spec-layers)
                                           ;; No base layer or no spec layers, just use spec layers
                                           spec-layers)))
                                     rest-specs)

          ;; Combine layers: include first-layers only if base has plottype
          ;; (if base is incomplete, it's only for inheritance, not rendering)
          all-layers (vec (if base-has-plottype?
                            (concat first-layers merged-rest-layers)
                            merged-rest-layers))

          ;; Merge all plot-level properties (right side wins for conflicts)
          ;; This handles :=target, :=width, :=height, scales
          all-plot-props (apply merge first-plot-props (map #(dissoc % :=layers) rest-specs))]

      (cond-> all-plot-props
        (seq all-layers) (assoc :=layers all-layers)))))

;; ## 🧪 Composition Operator Examples
;;
;; These examples demonstrate how the `=*` and `=+` operators work with simplified
;; plot specs. They focus on the composition mechanics rather than creating actual
;; visualizations. For complete, working examples with real data and plots, see
;; [Basic Scatter Plots](#basic-scatter-plots) 🎯.

;; ### 🧪 Example: =* Cross-Product Composition

;; The `=*` operator performs cross-product merge of layers:

(kind/pprint
 (=* {:=layers [{:=x :a}]}
     {:=layers [{:=y :b}]}))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 1)
                       (= (:=x (first (:=layers %))) :a)
                       (= (:=y (first (:=layers %))) :b))])

;; Multiple layers create cross-products:

(kind/pprint
 (=* {:=layers [{:=x :a} {:=x :b}]}
     {:=layers [{:=y :c} {:=y :d}]}))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 4)
                       ;; Should have all combinations: (a,c), (a,d), (b,c), (b,d)
                       (some (fn [layer] (and (= (:=x layer) :a) (= (:=y layer) :c))) (:=layers %))
                       (some (fn [layer] (and (= (:=x layer) :a) (= (:=y layer) :d))) (:=layers %))
                       (some (fn [layer] (and (= (:=x layer) :b) (= (:=y layer) :c))) (:=layers %))
                       (some (fn [layer] (and (= (:=x layer) :b) (= (:=y layer) :d))) (:=layers %)))])

;; ### 🧪 Example: =* Merges Plot-Level Properties

;; Plot-level properties (non-layer keys) merge with right-side wins:

(kind/pprint
 (=* {:=layers [{:=x :a}] :=target :geom}
     {:=layers [{:=y :b}] :=width 800}))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=target %) :geom)
                       (= (:=width %) 800)
                       (= (count (:=layers %)) 1)
                       (= (:=x (first (:=layers %))) :a)
                       (= (:=y (first (:=layers %))) :b))])

;; Right-side wins for conflicting plot-level properties:

(kind/pprint
 (=* {:=target :geom :=width 400}
     {:=target :vl :=height 300}))

(kind/test-last [#(and (map? %)
                       (= (:=target %) :vl)
                       (= (:=width %) 400)
                       (= (:=height %) 300))])

;; ### 🧪 Example: =+ Overlay Without Inheritance

;; The `=+` operator concatenates layers when both specs have plottypes:

(kind/pprint
 (=+ {:=layers [{:=plottype :scatter :=alpha 0.5}]}
     {:=layers [{:=plottype :line :=color :blue}]}))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 2)
                       (= (:=plottype (first (:=layers %))) :scatter)
                       (= (:=plottype (second (:=layers %))) :line)
                       (= (:=alpha (first (:=layers %))) 0.5)
                       (= (:=color (second (:=layers %))) :blue))])

;; ### 🧪 Example: =+ Inheritance Pattern

;; When first spec has incomplete layer (no plottype), it provides shared properties:

(kind/pprint
 (=+ {:=layers [{:=data {:x [1 2 3] :y [4 5 6]} :=x :x :=y :y}]}
     {:=layers [{:=plottype :scatter}]}
     {:=layers [{:=plottype :line}]}))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 2)
                       ;; Both layers inherit data and aesthetics from base
                       (= (:=data (first (:=layers %))) {:x [1 2 3] :y [4 5 6]})
                       (= (:=data (second (:=layers %))) {:x [1 2 3] :y [4 5 6]})
                       (= (:=x (first (:=layers %))) :x)
                       (= (:=x (second (:=layers %))) :x)
                       (= (:=y (first (:=layers %))) :y)
                       (= (:=y (second (:=layers %))) :y)
                       ;; Each has its own plottype
                       (= (:=plottype (first (:=layers %))) :scatter)
                       (= (:=plottype (second (:=layers %))) :line))])

;; This is the pattern used in threading forms:
;; (-> (mapping data :x :y) (=+ (scatter) (linear)))

;; ### 🧪 Example: =+ Merges Plot-Level Properties

(kind/pprint
 (=+ {:=layers [{:=plottype :scatter}] :=target :geom :=width 600}
     {:=layers [{:=plottype :line}] :=height 400}))

(kind/test-last [#(and (map? %)
                       (= (:=target %) :geom)
                       (= (:=width %) 600)
                       (= (:=height %) 400)
                       (= (count (:=layers %)) 2))])

;; ## ⚙️ Constructors

(defn data
  "Attach data to a layer.

  Accepts:
  - tech.ml.dataset datasets
  - Maps of vectors: {:x [1 2 3] :y [4 5 6]}
  - Vector of maps: [{:x 1 :y 4} {:x 2 :y 5}]

  Returns a plot spec map with :=layers containing a layer map with :=data.
  
  When called with spec as first arg, merges data into those layers."
  ([dataset]
   (validate! Dataset dataset)
   {:=layers [{:=data dataset}]})
  ([spec dataset]
   (=* spec (data dataset))))

;; Examples:
(data {:x [1 2 3] :y [4 5 6]})

(data [{:x 1 :y 4} {:x 2 :y 5} {:x 3 :y 6}])

;; Works with datasets too (shown later when we load penguins)

(defn =key
  "Add a = sign to the name of a given keyword."
  [k]
  (->> k name (str "=") keyword))

(defn mapping
  "Define aesthetic mappings from data columns to visual properties.
  
  Args:
  - x, y: Column names (keywords) for positional aesthetics
  - named: (optional) Map of other aesthetics
  
  Returns a plot spec map with :=layers containing a mapping layer.
  
  When called with spec-or-data as first arg:
  - If plot spec (map with :=layers keys): merges mapping into those layers
  - If data: converts to layer first, then adds mapping"
  ([x y]
   {:=layers [(into {} (filter (fn [[_ v]] (some? v))
                               {:=x x :=y y}))]})
  ([x y named]
   (if (map? named)
     ;; Regular 3-arg: mapping :x :y {:color :species}
     {:=layers [(into {} (filter (fn [[_ v]] (some? v))
                                 (merge {:=x x :=y y}
                                        (update-keys named =key))))]}
     ;; Threading-friendly: (-> spec (mapping :x :y))
     (let [spec-or-data x
           x-field y
           y-field named
           spec (if (plot-spec? spec-or-data)
                  spec-or-data
                  (data spec-or-data))]
       (=* spec (mapping x-field y-field)))))
  ([first-arg x y named]
   ;; Threading-friendly: (-> spec (mapping :x :y {:color :species}))
   (let [spec (if (plot-spec? first-arg)
                first-arg
                (data first-arg))]
     (=* spec (mapping x y named)))))

;; Examples:
(mapping :bill-length-mm :bill-depth-mm)

(mapping :bill-length-mm :bill-depth-mm {:color :species})

(mapping :wt :mpg {:color :cyl :size :hp})

(defn facet
  "Add faceting to a plot specification.
  
  Args:
  - facet-spec: Map with :row and/or :col keys specifying faceting variables
  
  Returns layer spec with faceting properties.
  
  When called with spec as first arg, merges faceting into that spec.
  
  Examples:
  (facet {:col :species})
  (facet {:row :sex :col :island})
  
  Threading-friendly:
  (-> penguins (mapping :x :y) (scatter) (facet {:col :species}))"
  ([facet-spec]
   (let [facet-keys (update-keys facet-spec =key)]
     {:=layers [facet-keys]}))
  ([spec facet-spec]
   (=* spec (facet facet-spec))))

(defn scale
  "Specify scale properties for an aesthetic.
  
  Args:
  - aesthetic: Keyword like :x, :y, :color
  - opts: Map with scale options:
    - :domain - [min max] for continuous, or vector of categories
    - :transform - :log, :sqrt, :identity (default)
  
  Returns a plot-level property map with scale specification.
  
  When called with spec as first arg, merges scale into that spec.
  
  Examples:
  (scale :x {:domain [0 100]})
  (scale :y {:transform :log})
  
  Threading-friendly:
  (-> penguins (mapping :x :y) (scatter) (scale :x {:domain [30 65]}))"
  ([aesthetic opts]
   (let [scale-key (keyword (str "=scale-" (name aesthetic)))]
     {scale-key opts}))
  ([spec aesthetic opts]
   (=* spec (scale aesthetic opts))))

(defn target
  "Specify the rendering target for plot.
  
  Args:
  - target-kw: One of :geom (static SVG), :vl (Vega-Lite), or :plotly (Plotly.js)
  
  Returns a plot-level property map (no :=layers).
  
  When called with spec as first arg, merges target into that spec.
  
  See Multi-Target Rendering section for usage examples."
  ([target-kw]
   {:=target target-kw})
  ([spec target-kw]
   (=* spec (target target-kw))))

(defn size
  "Specify width and height for the plot.
  
  Args:
  - width: Plot width in pixels
  - height: Plot height in pixels
  
  Returns a plot-level property map (no :=layers).
  
  When called with spec as first arg, merges size into that spec.
  
  Examples:
  (size 800 600)
  (-> penguins (mapping :x :y) (scatter) (size 800 600))"
  ([width height]
   {:=width width :=height height})
  ([spec width height]
   (=* spec (size width height))))

;; ## 🧪 Plot-Level Properties Examples
;;
;; Plot-level properties configure rendering and scales for the entire plot,
;; as opposed to layer-level properties that apply to individual visual marks.
;; In practice, you'll typically compose these with data, mappings, and geoms
;; using `=*`. These isolated examples show what each property constructor returns.

;; ### 🧪 Example: target Constructor

;; The `target` constructor sets the rendering backend:

(kind/pprint
 (target :vl))

(kind/test-last [#(and (map? %)
                       (= (:=target %) :vl)
                       (not (contains? % :=layers)))])

;; Threading form merges target with existing spec:

(kind/pprint
 (-> {:=layers [{:=data {:x [1 2 3] :y [4 5 6]}}]}
     (target :plotly)))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=target %) :plotly)
                       (= (:=data (first (:=layers %))) {:x [1 2 3] :y [4 5 6]}))])

;; ### 🧪 Example: scale Constructor

;; The `scale` constructor sets custom domains:

(kind/pprint
 (scale :x {:domain [0 100]}))

(kind/test-last [#(and (map? %)
                       (contains? % :=scale-x)
                       (= (get-in % [:=scale-x :domain]) [0 100])
                       (not (contains? % :=layers)))])

;; Multiple scales can be composed:

(kind/pprint
 (=* (scale :x {:domain [0 100]})
     (scale :y {:domain [0 50]})))

(kind/test-last [#(and (map? %)
                       (contains? % :=scale-x)
                       (contains? % :=scale-y)
                       (= (get-in % [:=scale-x :domain]) [0 100])
                       (= (get-in % [:=scale-y :domain]) [0 50]))])

;; Threading form merges scale with plot spec:

(kind/pprint
 (-> {:=layers [{:=x :a :=y :b}]}
     (scale :x {:domain [30 60]})))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (contains? % :=scale-x)
                       (= (get-in % [:=scale-x :domain]) [30 60]))])

;; ### 🧪 Example: size Constructor

;; The `size` constructor sets plot dimensions:

(kind/pprint
 (size 800 600))

(kind/test-last [#(and (map? %)
                       (= (:=width %) 800)
                       (= (:=height %) 600)
                       (not (contains? % :=layers)))])

;; Threading form merges size with plot spec:

(kind/pprint
 (-> {:=layers [{:=x :a :=y :b}]}
     (size 1000 500)))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=width %) 1000)
                       (= (:=height %) 500))])

;; ### 🧪 Example: Composing Multiple Plot-Level Properties

;; All plot-level properties compose via `=*`:

(kind/pprint
 (=* (target :vl)
     (size 800 600)
     (scale :x {:domain [0 100]})))

(kind/test-last [#(and (map? %)
                       (= (:=target %) :vl)
                       (= (:=width %) 800)
                       (= (:=height %) 600)
                       (contains? % :=scale-x))])

;; # Core Implementation: Helpers & Rendering Infrastructure
;;
;; This section contains the foundational helper functions and the core
;; rendering infrastructure for the :geom target. These are used by all
;; plot types but aren't part of the user-facing API.

;; ## Helper Functions & Utilities
;;
;; Before we can render examples, we need basic implementation.
;; This version follows the minimal delegation strategy.

;; ### ⚙️ Helper Functions

(defn- ensure-dataset
  "Convert data to a tablecloth dataset if it isn't already.

  Accepts:
  - tech.ml.dataset datasets (passed through)
  - Maps of vectors: {:x [1 2 3] :y [4 5 6]}
  - Vector of maps: [{:x 1 :y 4} {:x 2 :y 5}]

  Returns a tablecloth dataset."
  [data]
  (cond
    (tc/dataset? data) data

    (map? data)
    (let [values (vals data)]
      (when-not (every? sequential? values)
        (throw (ex-info "Map data must have sequential values (vectors or lists)"
                        {:data data
                         :invalid-keys (filter #(not (sequential? (get data %))) (keys data))})))
      (tc/dataset data))

    (and (sequential? data) (every? map? data))
    (tc/dataset data)

    :else
    (throw (ex-info "Data must be a dataset, map of vectors, or vector of maps"
                    {:data data
                     :type (type data)}))))

(defn- validate-column-exists
  "Validate that a column exists in a dataset.
  
  Args:
  - dataset: tech.ml.dataset instance
  - col-key: Keyword for column name
  - context: String describing where this column is used (for error messages)
  
  Throws informative exception if column doesn't exist.
  Returns col-key if valid (for threading)."
  [dataset col-key context]
  (when col-key
    (let [col-names (set (tc/column-names dataset))]
      (when-not (contains? col-names col-key)
        (let [;; Find similar column names (simple string similarity)
              col-name-str (name col-key)
              similar-cols (filter (fn [available-col]
                                     (let [available-str (name available-col)
                                           ;; Check for substring match or similar length
                                           substring-match? (or (.contains available-str col-name-str)
                                                                (.contains col-name-str available-str))
                                           similar-length? (< (Math/abs (- (count available-str)
                                                                           (count col-name-str)))
                                                              3)]
                                       (or substring-match? similar-length?)))
                                   col-names)

              ;; Build error message with helpful suggestions
              error-msg (str "Column " col-key " not found in dataset")
              suggestion (if (seq similar-cols)
                           (str "Did you mean one of: " (pr-str (vec (sort similar-cols))) "?")
                           (str "Available columns: " (pr-str (vec (sort col-names)))))]

          (throw (ex-info error-msg
                          {:column col-key
                           :context context
                           :available-columns (vec (sort col-names))
                           :similar-columns (when (seq similar-cols) (vec (sort similar-cols)))
                           :suggestion suggestion}))))))
  col-key)

(defn- validate-columns
  "Validate multiple columns exist in a dataset.
  
  Args:
  - dataset: tech.ml.dataset instance
  - col-keys: Collection of column keywords
  - context: String describing where these columns are used
  
  Throws informative exception if any column doesn't exist.
  Returns col-keys if all valid (for threading)."
  [dataset col-keys context]
  (doseq [col-key col-keys]
    (validate-column-exists dataset col-key context))
  col-keys)

(defn- validate-layer-columns
  "Validate that all aesthetic mappings in a layer reference existing columns.
  
  Checks :=x, :=y, :=color, :=row, :=col, :=group.
  
  Args:
  - layer: Layer map with aesthetic mappings
  
  Throws informative exception if any referenced column doesn't exist.
  Returns layer if valid (for threading)."
  [layer]
  (when-let [data (:=data layer)]
    (let [dataset (ensure-dataset data)
          aesthetics [:=x :=y :=color :=row :=col]
          ;; Collect all column references
          cols (keep #(get layer %) aesthetics)
          ;; Handle :=group which can be keyword or vector
          group-cols (let [g (:=group layer)]
                       (cond
                         (keyword? g) [g]
                         (vector? g) g
                         :else []))]
      ;; Validate all referenced columns
      (doseq [col-key (concat cols group-cols)]
        (validate-column-exists dataset col-key
                                (str "aesthetic mapping in layer: "
                                     (pr-str (select-keys layer aesthetics)))))))
  layer)

(defn- split-by-facets
  "Split a layer's data by facet variables.
  
  Returns: Vector of {:row-label r, :col-label c, :layer layer-with-subset} maps
  If no faceting, returns vector with single element containing original layer.
  Labels are nil for dimensions without faceting."
  [layer]
  (let [col-var (:=col layer)
        row-var (:=row layer)
        data (:=data layer)]

    (if-not (or col-var row-var)
      ;; No faceting - return original layer
      [{:row-label nil :col-label nil :layer layer}]

      ;; Has faceting
      (let [dataset (ensure-dataset data)

            ;; Get unique values for each facet dimension
            col-categories (when col-var (sort (distinct (get dataset col-var))))
            row-categories (when row-var (sort (distinct (get dataset row-var))))

            ;; Create all combinations
            combinations (cond
                           ;; Both row and col
                           (and row-var col-var)
                           (for [r row-categories
                                 c col-categories]
                             {:row-label r :col-label c})

                           ;; Only col
                           col-var
                           (for [c col-categories]
                             {:row-label nil :col-label c})

                           ;; Only row
                           row-var
                           (for [r row-categories]
                             {:row-label r :col-label nil}))]

        ;; For each combination, filter the data
        (mapv (fn [{:keys [row-label col-label]}]
                (let [filtered (cond-> dataset
                                 row-var (tc/select-rows
                                          (fn [row]
                                            (= (get row row-var) row-label)))
                                 col-var (tc/select-rows
                                          (fn [row]
                                            (= (get row col-var) col-label))))
                      new-layer (assoc layer :=data filtered)]
                  {:row-label row-label
                   :col-label col-label
                   :layer new-layer}))
              combinations)))))

(defn- has-faceting?
  "Check if any layer has faceting."
  [layers-vec]
  (some #(or (:=col %) (:=row %)) layers-vec))

(defn- organize-by-facets
  "Organize multiple layers by their facet groups.
  
  Returns: Vector of {:row-label r, :col-label c, :layers [layers-for-this-facet]}
  All layers must have the same facet specification (or no faceting)."
  [layers-vec]
  (if-not (has-faceting? layers-vec)
    ;; No faceting - return all layers in single group
    [{:row-label nil :col-label nil :layers layers-vec}]

    ;; Has faceting - split each layer and group by facet
    (let [;; Step 1: Split each layer by its facet variables
          ;; Each layer becomes multiple {:row-label :col-label :layer} maps
          all-split (mapcat split-by-facets layers-vec)

          ;; Step 2: Group split layers by their facet labels
          ;; Creates map: [row-label col-label] -> [{:layer ...} ...]
          by-labels (group-by (juxt :row-label :col-label) all-split)

          ;; Step 3: Get unique row and column labels (sorted for consistency)
          row-labels (sort (distinct (map :row-label all-split)))
          col-labels (sort (distinct (map :col-label all-split)))

          ;; Step 4: Create all combinations in row-major order
          ;; This ensures consistent panel ordering for rendering
          combinations (for [r row-labels
                             c col-labels]
                         [r c])]

      ;; Step 5: For each combination, collect all layers for that facet
      ;; Result: Vector of facet groups, each containing all layers for that facet
      (mapv (fn [[r c]]
              {:row-label r
               :col-label c
               ;; Extract just the layer maps from the grouped data
               :layers (mapv :layer (get by-labels [r c]))})
            combinations))))

;; Type information helpers (for grouping logic)

(defn- infer-from-values
  "Simple fallback type inference for plain Clojure data."
  [values]
  (cond
    (every? number? values) :continuous
    (some #(instance? java.time.temporal.Temporal %) values) :temporal
    :else :categorical))

(defn- categorical-type?
  "Check if a column type should be treated as categorical.

  Categorical types create groups for statistical transforms.
  Continuous and temporal types are visual-only by default."
  [col-type]
  (contains? #{:string :keyword :boolean :symbol :text} col-type))

(defn- get-grouping-columns
  "Determine which columns should be used for grouping statistical transforms.

  Returns a vector of column keywords that create groups:
  - :=group (explicit, can be keyword or vector)
  - :=color (if categorical)
  - :=col (facet column, if categorical)
  - :=row (facet row, if categorical)

  Logic:
  1. Explicit :=group always included (supports single keyword or vector)
  2. Categorical aesthetics (:color, :col, :row) create groups
  3. Continuous/temporal aesthetics are visual-only, don't create groups

  Returns vector of column keywords, or empty vector if no grouping."
  [layer dataset]
  (let [explicit-group (:=group layer)
        color-col (:=color layer)
        col-facet (:=col layer)
        row-facet (:=row layer)

        ;; Helper to check if a column is categorical
        ;; Uses Tablecloth's tcc/typeof when available, falls back to value inspection
        categorical? (fn [col-key]
                       (when (and col-key dataset)
                         (let [col-type (try
                                          ;; Primary: Get type from Tablecloth metadata
                                          (tcc/typeof (get dataset col-key))
                                          (catch Exception _
                                            ;; Fallback: Infer from values for plain Clojure data
                                            (infer-from-values (get dataset col-key))))]
                           ;; Check if type indicates categorical data
                           (categorical-type? col-type))))

        ;; Collect all grouping columns in order of precedence
        grouping-cols (cond-> []
                        ;; 1. Explicit group (highest priority, can be keyword or vector)
                        explicit-group
                        (into (if (vector? explicit-group) explicit-group [explicit-group]))

                        ;; 2. Color aesthetic if categorical
                        ;; (continuous color scales don't create groups)
                        (categorical? color-col)
                        (conj color-col)

                        ;; 3. Column facet if categorical
                        (categorical? col-facet)
                        (conj col-facet)

                        ;; 4. Row facet if categorical
                        (categorical? row-facet)
                        (conj row-facet))]

    ;; Remove duplicates while preserving order
    ;; (e.g., if :color and :col both reference same column)
    (vec (distinct grouping-cols))))

(defn- layer->points
  "Convert layer to point data for rendering.
  
  Validates column existence and handles missing data gracefully.
  
  Args:
  - layer: Layer map with :=data and aesthetic mappings
  
  Returns: Sequence of point maps with :x, :y (optional), :color (optional), :group (optional)"
  [layer]
  (let [data (:=data layer)]
    (when-not data
      (throw (ex-info "Layer missing :=data"
                      {:layer (dissoc layer :=data)})))

    (let [dataset (ensure-dataset data)
          _ (validate-layer-columns layer) ;; Validate all columns exist

          x-col (:=x layer)
          _ (when-not x-col
              (throw (ex-info "Layer missing :=x mapping"
                              {:layer (select-keys layer [:=data :=y :=color])})))

          x-vals (vec (get dataset x-col))

          ;; Y is optional for some plot types (histograms)
          y-col (:=y layer)
          y-vals (when y-col (vec (get dataset y-col)))

          ;; Color is optional
          color-col (:=color layer)
          color-vals (when color-col (vec (get dataset color-col)))

          ;; Get grouping columns for statistical transforms
          grouping-cols (get-grouping-columns layer dataset)

          ;; Extract values for each grouping column
          grouping-vals (when (seq grouping-cols)
                          (mapv #(vec (get dataset %)) grouping-cols))

          ;; Check for empty data
          n (count x-vals)]

      (when (zero? n)
        (throw (ex-info "Dataset is empty (no rows)"
                        {:layer-data-summary {:x-column x-col
                                              :y-column y-col
                                              :row-count 0}})))

      ;; Build point maps
      (map-indexed (fn [i _]
                     (cond-> {:x (nth x-vals i)}
                       y-vals (assoc :y (nth y-vals i))
                       color-vals (assoc :color (nth color-vals i))
                       ;; Create composite group key from all grouping columns
                       (seq grouping-cols)
                       (assoc :group (mapv #(nth % i) grouping-vals))))
                   x-vals))))

(defmulti apply-transform
  "Apply statistical transform to layer points.

  Dispatches on the :=transformation key in the layer.

  Handles grouping: if points contain :group key, applies transform per group.

  Returns structured result based on transformation type:
  - nil (no transform): {:type :raw :points points}
  - :linear: {:type :regression :points points :fitted fitted-points} or :grouped map
  - :histogram: {:type :histogram :points points :bars bar-specs} or :grouped map"
  (fn [layer points] (:=transformation layer)))

;; Default method: no transformation (scatter, line, etc.)
(defmethod apply-transform nil
  [layer points]
  {:type :raw
   :points points})

(defmulti transform->domain-points
  "Convert transform result to points for domain computation.
  
  Dispatches on the :type key in the transform result."
  (fn [transform-result] (:type transform-result)))

;; Raw points (no transformation)
(defmethod transform->domain-points :raw
  [transform-result]
  (:points transform-result))

;; ### ⚙️ Visual Theme Constants
;;
;; These constants define the ggplot2-compatible visual theme used across
;; all rendering targets. Extracted here for maintainability.

;; ggplot2-compatible color palette for categorical variables
(def theme
  "Global theme configuration for plots.
  
  Contains:
  - :colors - Categorical color palette (ggplot2-compatible)
  - :background - Plot background color
  - :grid - Grid line color
  - :default-mark - Default mark/line color
  - :plot-width - Default plot width
  - :plot-height - Default plot height
  - :panel-margin-left - Left margin for plot panel
  - :panel-margin-right - Right margin for plot panel
  - :panel-margin-top - Top margin for plot panel
  - :panel-margin-bottom - Bottom margin for plot panel
  - :facet-label-offset - Offset for facet labels (top/bottom)
  - :facet-label-side-offset - Offset for facet labels (left/right)"
  {:colors ["#F8766D" "#00BA38" "#619CFF" "#F564E3"]
   :background "#EBEBEB"
   :grid "#FFFFFF"
   :default-mark "#333333"
   :plot-width 600
   :plot-height 400
   :panel-margin-left 50
   :panel-margin-right 50
   :panel-margin-top 50
   :panel-margin-bottom 50
   :facet-label-offset 30
   :facet-label-side-offset 20})

(defn- color-scale
  "ggplot2-like color scale for categorical data."
  [categories]
  (zipmap categories (cycle (:colors theme))))

;; ## Geom Target Rendering
;;
;; Core rendering infrastructure for the :geom (thi.ng/geom) target.

;; ### ⚙️ Core Rendering Multimethods
;;
;; The render-layer multimethod dispatches on [target plottype-or-transform].
;; This allows us to define each rendering strategy separately and introduce
;; them incrementally as examples need them.
;;
;; Design: Transform computation (apply-transform) is target-independent and shared.
;; Rendering is target-specific - each [target plottype] combination is a separate method.
;; This makes it easy to add new targets: define `[:vl :scatter]`, `[:plotly :scatter]`, etc.

(defmulti render-layer
  "Render a layer for a specific target.
  
  Dispatches on [target plottype-or-transform], where plottype-or-transform
  is the transformation type if present, otherwise the plottype.
  
  Parameters:
  - target: rendering target (:geom, :vl, :plotly)
  - layer: layer specification map
  - transform-result: result from apply-transform
  - alpha: opacity value
  - context: (optional) context map with plot-level properties like :custom-x-domain, :custom-y-domain"
  (fn [target layer transform-result alpha & [context]]
    [target (or (:=transformation layer) (:=plottype layer))]))

;; ### ⚙️ Geom Target Rendering Methods

(defmethod render-layer [:geom :line]
  [target layer transform-result alpha & [context]]
  (let [points (:points transform-result)
        color-groups (group-by :color points)]
    (if (> (count color-groups) 1)
      ;; Colored lines
      (let [colors (color-scale (keys color-groups))]
        (mapv (fn [[color group-points]]
                {:values (mapv (fn [p] [(:x p) (:y p)]) group-points)
                 :layout viz/svg-line-plot
                 :attribs {:stroke (get colors color)
                           :stroke-width 2
                           :opacity alpha}})
              color-groups))
      ;; Simple line
      [{:values (mapv (fn [p] [(:x p) (:y p)]) points)
        :layout viz/svg-line-plot
        :attribs {:stroke (:default-mark theme)
                  :stroke-width 2
                  :opacity alpha}}])))

;; ### ⚙️ Simple :geom Target (Delegating Domain Computation)

(defn- infer-domain
  "Infer domain from data values.
  
  For delegation: We compute RAW domain (just min/max).
  thi.ng/geom handles 'nice numbers' and tick placement.
  
  Args:
  - values: Sequence of values (numeric or categorical)
  
  Returns: 
  - For numeric: [min max] vector
  - For categorical: vector of distinct values
  - For empty: [0 1] as fallback (prevents rendering errors)
  - For single value: [value-10% value+10%] to provide visual range
  
  Edge cases handled:
  - Empty data returns [0 1]
  - Single value returns expanded range
  - All identical values returns expanded range
  - Mixed types returns categorical domain"
  [values]
  (cond
    ;; Empty data - use fallback domain
    (empty? values)
    [0 1]

    ;; Numeric data
    (every? number? values)
    (let [v-min (apply min values)
          v-max (apply max values)]
      (if (= v-min v-max)
        ;; All values identical - expand domain by 10% for visual clarity
        (let [expansion (max 1 (* 0.1 (Math/abs v-min)))]
          [(- v-min expansion) (+ v-max expansion)])
        ;; Normal range
        [v-min v-max]))

    ;; Categorical or mixed data
    :else
    (vec (distinct values))))

(defn- render-single-panel
  "Render a single plot panel (for use in both faceted and non-faceted plots).
  
  Args:
  - layers: Vector of layers to render in this panel
  - x-domain, y-domain: Domain for x and y axes [min max]
  - width, height: Panel dimensions in pixels
  - x-offset, y-offset: Horizontal and vertical offsets for this panel in pixels
  
  Returns: Map with :background, :plot, :hist-rects keys
  
  The function handles:
  - Multi-layer composition
  - Statistical transforms via render-layer multimethod
  - Histogram rectangles separately from regular viz data
  - Proper scaling and axis setup"
  [layers x-domain y-domain width height x-offset y-offset]
  (let [x-range (clojure.core/- (second x-domain) (first x-domain))
        y-range (clojure.core/- (second y-domain) (first y-domain))
        ;; Use 20% of range for major gridlines
        x-major (max 1 (clojure.core/* x-range 0.2))
        y-major (max 1 (clojure.core/* y-range 0.2))

        ;; Calculate panel boundaries using constants
        panel-left (clojure.core/+ (:panel-margin-left theme) x-offset)
        panel-right (clojure.core/+ panel-left
                                    (clojure.core/- width
                                                    (clojure.core/+ (:panel-margin-left theme) (:panel-margin-right theme))))
        panel-top (clojure.core/+ (:panel-margin-top theme) y-offset)
        panel-bottom (clojure.core/+ panel-top
                                     (clojure.core/- height
                                                     (clojure.core/+ (:panel-margin-top theme) (:panel-margin-bottom theme))))

        ;; Create axes
        x-axis (viz/linear-axis
                {:domain x-domain
                 :range [panel-left panel-right]
                 :major x-major
                 :pos panel-bottom})
        y-axis (viz/linear-axis
                {:domain y-domain
                 :range [panel-bottom panel-top]
                 :major y-major
                 :pos panel-left})

        ;; Process each layer using render-layer multimethod
        layer-data (mapcat (fn [layer]
                             (let [points (layer->points layer)
                                   alpha (or (:=alpha layer) 1.0)
                                   transform-result (apply-transform layer points)]
                               (render-layer :geom layer transform-result alpha nil)))
                           layers)

        ;; Separate regular viz data from histogram rectangles
        {viz-data true rect-data false} (group-by #(not= (:type %) :rect) layer-data)

        ;; Create the plot spec for regular data
        plot-spec {:x-axis x-axis
                   :y-axis y-axis
                   :grid {:attribs {:stroke (:grid theme) :stroke-width 1}}
                   :data (vec viz-data)}

        ;; Background rectangle for this panel
        bg-rect (svg/rect [panel-left panel-top]
                          (clojure.core/- width
                                          (clojure.core/+ (:panel-margin-left theme) (:panel-margin-right theme)))
                          (clojure.core/- height
                                          (clojure.core/+ (:panel-margin-top theme) (:panel-margin-bottom theme)))
                          {:fill (:background theme)
                           :stroke (:grid theme)
                           :stroke-width 1})

        ;; Convert histogram rectangles to SVG
        x-scale (fn [x] (clojure.core/+ panel-left
                                        (clojure.core/* (/ (clojure.core/- x (first x-domain))
                                                           (clojure.core/- (second x-domain) (first x-domain)))
                                                        (clojure.core/- width
                                                                        (clojure.core/+ (:panel-margin-left theme) (:panel-margin-right theme))))))
        y-scale (fn [y] (clojure.core/- panel-bottom
                                        (clojure.core/* (/ (clojure.core/- y (first y-domain))
                                                           (clojure.core/- (second y-domain) (first y-domain)))
                                                        (clojure.core/- height
                                                                        (clojure.core/+ (:panel-margin-top theme) (:panel-margin-bottom theme))))))

        hist-rects (mapv (fn [r]
                           (svg/rect [(x-scale (:x-min r)) (y-scale (:height r))]
                                     (clojure.core/- (x-scale (:x-max r)) (x-scale (:x-min r)))
                                     (clojure.core/- (y-scale 0) (y-scale (:height r)))
                                     (:attribs r)))
                         (or rect-data []))]

    {:background bg-rect
     :plot (viz/svg-plot2d-cartesian plot-spec)
     :hist-rects hist-rects}))

(defn- get-scale-domain
  "Extract custom domain for an aesthetic from plot spec, or return nil if not specified.
  
  Args:
  - spec: Plot specification map (not layers-vec)
  - aesthetic: Keyword like :x, :y, :color
  
  Returns:
  - Domain vector [min max] or nil if not specified"
  [spec aesthetic]
  (let [scale-key (keyword (str "=scale-" (name aesthetic)))]
    (get-in spec [scale-key :domain])))

;; Render plot using thi.ng/geom to static SVG.
;;
;; This method handles:
;; - Single and multi-layer plots
;; - Faceting (column, row, and grid)
;; - Custom scale domains
;; - Statistical transforms (via apply-transform multimethod)
;; - Proper error messages for invalid data
;;
;; Args:
;; - layers: Vector of layer maps or single layer map
;; - opts: Map with :width and :height options
;;
;; Returns: Kindly-wrapped HTML containing SVG
(defmethod plot-impl :geom
  [spec opts]
  ;; Extract layers from spec
  (let [layers-vec (get spec :=layers [])
        _ (validate-layers! layers-vec)

        ;; Extract plot-level properties (spec first, then opts, then defaults)
        width (or (:=width spec) (:width opts) (:plot-width theme))
        height (or (:=height spec) (:height opts) (:plot-height theme))

        ;; Organize layers by facets
        facet-groups (organize-by-facets layers-vec)

        ;; Determine grid dimensions
        row-labels (distinct (map :row-label facet-groups))
        col-labels (distinct (map :col-label facet-groups))
        num-rows (count row-labels)
        num-cols (count col-labels)
        is-faceted? (or (> num-rows 1) (> num-cols 1))

        ;; Calculate panel dimensions
        panel-width (/ width num-cols)
        panel-height (/ height num-rows)

        ;; Check for custom scale domains
        custom-x-domain (get-scale-domain spec :x)
        custom-y-domain (get-scale-domain spec :y)

        ;; Compute transformed points for ALL facets (for shared domain)
        all-transformed-points
        (mapcat (fn [{:keys [layers]}]
                  (mapcat (fn [layer]
                            (let [points (layer->points layer)
                                  transform-result (apply-transform layer points)]
                              (transform->domain-points transform-result)))
                          layers))
                facet-groups)

        ;; Compute domains (use custom if provided, otherwise infer)
        x-vals (keep :x all-transformed-points)
        y-vals (keep :y all-transformed-points)
        x-domain (or custom-x-domain (infer-domain x-vals))
        y-domain (or custom-y-domain (infer-domain y-vals))

        ;; Validate domains
        valid? (and (vector? x-domain) (vector? y-domain)
                    (every? number? x-domain) (every? number? y-domain))]

    (if-not valid?
      (kind/hiccup
       [:div {:style {:padding "20px"
                      :background-color "#fff3cd"
                      :border "1px solid #ffc107"
                      :border-radius "4px"}}
        [:h4 {:style {:margin-top "0" :color "#856404"}} "⚠️ Cannot Render Plot"]
        [:p "The plot could not be rendered due to one of the following reasons:"]
        [:ul
         [:li "Dataset is empty (no data rows)"]
         [:li "Data contains non-numeric values where numbers are expected"]
         [:li "Column mappings reference non-existent columns"]]
        [:p [:strong "Domains computed:"]]
        [:pre {:style {:background-color "#f8f9fa" :padding "10px" :border-radius "4px"}}
         (pr-str {:x-domain x-domain :y-domain y-domain})]
        [:p [:em "Tip: Use " [:code "kind/pprint"] " to inspect layer specifications and verify column names."]]])

      ;; Render panels
      (let [;; Create row/col position lookup
            row-positions (zipmap row-labels (range num-rows))
            col-positions (zipmap col-labels (range num-cols))

            ;; Render each facet panel with grid position
            panels (mapv
                    (fn [{:keys [row-label col-label layers]}]
                      (let [row-idx (get row-positions row-label 0)
                            col-idx (get col-positions col-label 0)
                            x-offset (clojure.core/* col-idx panel-width)
                            y-offset (clojure.core/* row-idx panel-height)
                            panel (render-single-panel layers x-domain y-domain
                                                       panel-width panel-height
                                                       x-offset y-offset)]
                        (assoc panel
                               :row-label row-label
                               :col-label col-label
                               :row-idx row-idx
                               :col-idx col-idx
                               :x-offset x-offset
                               :y-offset y-offset)))
                    facet-groups)

            ;; Collect all elements
            all-backgrounds (mapv :background panels)
            all-plots (mapv :plot panels)
            all-hist-rects (mapcat :hist-rects panels)

            ;; Add facet labels if faceted
            facet-labels (when is-faceted?
                           (concat
                            ;; Column labels (top)
                            (when (> num-cols 1)
                              (map (fn [col-label]
                                     (let [col-idx (get col-positions col-label)
                                           label-x (clojure.core/+ (clojure.core/* col-idx panel-width)
                                                                   (/ panel-width 2))]
                                       (svg/text [label-x (:facet-label-offset theme)] (str col-label)
                                                 {:text-anchor "middle"
                                                  :font-family "Arial, sans-serif"
                                                  :font-size 12
                                                  :font-weight "bold"})))
                                   col-labels))

                            ;; Row labels (left side)
                            (when (> num-rows 1)
                              (map (fn [row-label]
                                     (let [row-idx (get row-positions row-label)
                                           label-y (clojure.core/+ (clojure.core/* row-idx panel-height)
                                                                   (/ panel-height 2))]
                                       (svg/text [(:facet-label-side-offset theme) label-y] (str row-label)
                                                 {:text-anchor "middle"
                                                  :font-family "Arial, sans-serif"
                                                  :font-size 12
                                                  :font-weight "bold"
                                                  :transform (str "rotate(-90 " (:facet-label-side-offset theme) " " label-y ")")})))
                                   row-labels))))

            ;; Combine into single SVG
            svg-elem (apply svg/svg
                            {:width width :height height}
                            (concat all-backgrounds
                                    all-plots
                                    all-hist-rects
                                    (or facet-labels [])))]

        (kind/html (svg/serialize svg-elem))))))

;; We now have enough implementation to show basic scatter plots.

;; # Example Datasets
;;
;; Now that we have the core implementation in place, let's load the datasets
;; we'll use to demonstrate the API. The following examples will show how the
;; compositional approach works with real data.

;; Palmer Penguins - 344 observations, 3 species
(def penguins (tc/drop-missing (rdatasets/palmerpenguins-penguins)))

penguins

;; Motor Trend Car Road Tests - 32 automobiles
(def mtcars (rdatasets/datasets-mtcars))

mtcars

;; Fisher's Iris - 150 flowers, 3 species
(def iris (rdatasets/datasets-iris))

iris

;; ## Type Information
;;
;; Tablecloth provides precise type information for each column, which we use
;; for type-aware grouping and aesthetic mapping.

{:bill-length-type (tcc/typeof (penguins :bill-length-mm))
 :species-type (tcc/typeof (penguins :species))
 :island-type (tcc/typeof (penguins :island))}

;; Notice: We get precise type information (`:float64`, `:string`) without
;; examining values. This eliminates the need for complex type inference.

;; # Basic Scatter Plots
;; 
;; Simple scatter plots demonstrating the core API.

;; ## ⚙️ Implementation

;; ### ⚙️ Constructor

(defn scatter
  "Create a scatter plot layer.
  
  A scatter plot displays individual data points as marks (circles) positioned
  according to their x and y values. Useful for showing relationships between
  two continuous variables or the distribution of discrete points.
  
  Args (when provided):
  - attrs-or-spec: Either a map of attributes {:alpha 0.5} or a plot spec to compose with
  - attrs: (2-arg form) Map of visual attributes like {:alpha 0.5}
  
  Attributes:
  - :alpha - Opacity (0.0 to 1.0, default 1.0)
  
  Returns:
  - Plot spec map with :=layers containing a scatter layer
  
  Forms:
  
  1. No args - Returns basic scatter layer:
     (scatter)
     ;; => {:=layers [{:=plottype :scatter}]}
  
  2. With attributes - Returns scatter layer with custom attributes:
     (scatter {:alpha 0.5})
     ;; => {:=layers [{:=plottype :scatter :=alpha 0.5}]}
  
  3. Threading form - Composes with existing spec:
     (-> (data penguins)
         (mapping :bill-length-mm :bill-depth-mm)
         (scatter))
     ;; => {:=layers [{:=data penguins :=x ... :=y ... :=plottype :scatter}]}
  
  4. Threading with attributes:
     (-> (data penguins)
         (mapping :bill-length-mm :bill-depth-mm)
         (scatter {:alpha 0.7}))
  
  Example - basic scatter plot:
  (-> penguins
      (mapping :bill-length-mm :bill-depth-mm)
      (scatter))
  
  Example - semi-transparent points:
  (-> penguins
      (mapping :bill-length-mm :bill-depth-mm)
      (scatter {:alpha 0.5}))
  
  Example - colored by species:
  (-> penguins
      (mapping :bill-length-mm :bill-depth-mm {:color :species})
      (scatter))"
  ([]
   {:=layers [{:=plottype :scatter}]})
  ([attrs-or-spec]
   (if (plot-spec? attrs-or-spec)
     (=* attrs-or-spec (scatter))
     (let [result (merge {:=plottype :scatter}
                         (update-keys attrs-or-spec =key))]
       {:=layers [result]})))
  ([spec attrs]
   ;; Threading-friendly: (-> spec (scatter {:alpha 0.5}))
   (=* spec (scatter attrs))))

;; ### ⚙️ Rendering Multimethod

(defmethod render-layer [:geom :scatter]
  [target layer transform-result alpha & [context]]
  (let [points (:points transform-result)
        color-groups (group-by :color points)]
    (if (> (count color-groups) 1)
      ;; Colored scatter
      (let [colors (color-scale (keys color-groups))]
        (mapv (fn [[color group-points]]
                {:values (mapv (fn [p] [(:x p) (:y p)]) group-points)
                 :layout viz/svg-scatter-plot
                 :attribs {:fill (get colors color)
                           :stroke (get colors color)
                           :stroke-width 0.5
                           :opacity alpha}})
              color-groups))
      ;; Simple scatter
      [{:values (mapv (fn [p] [(:x p) (:y p)]) points)
        :layout viz/svg-scatter-plot
        :attribs {:fill (:default-mark theme)
                  :stroke (:default-mark theme)
                  :stroke-width 0.5
                  :opacity alpha}}])))

;; ## 🧪 Examples

;; ### 🧪 Example 1: Simple Scatter Plot (Delegated Domain)

;; **Algebraic style using `=*`:**

;; Inspect the spec:
(kind/pprint
 (=* (data penguins)
     (mapping :bill-length-mm :bill-depth-mm)
     (scatter)))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (map? (first (:=layers %)))
                       (contains? (first (:=layers %)) :=data)
                       (contains? (first (:=layers %)) :=x)
                       (contains? (first (:=layers %)) :=y)
                       (contains? (first (:=layers %)) :=plottype)
                       (= (:=plottype (first (:=layers %))) :scatter))])

;; Render the plot:
(plot
 (=* (data penguins)
     (mapping :bill-length-mm :bill-depth-mm)
     (scatter)))

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **Threading macro style (equivalent):**

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=x (first (:=layers %))) :bill-length-mm)
                       (= (:=y (first (:=layers %))) :bill-depth-mm)
                       (= (:=plottype (first (:=layers %))) :scatter))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; Both forms produce identical results. The threading style is often more
;; natural in Clojure, while the `=*` style makes the composition operator explicit.

;; **What happens here**:

;; 1. We create a plot spec with data, mapping, and plottype
;; 2. The `=*` operator returns a plot spec annotated to auto-display as a plot
;; 3. We DON'T compute X/Y domains
;; 4. Rendering target receives data and computes domain itself
;; 5. This is simpler - we delegate what rendering targets do well.

;; **Inspecting the raw plot specification**:
(kind/pprint
 (=* (data penguins)
     (mapping :bill-length-mm :bill-depth-mm)
     (scatter)))

;; Notice the `:=layers` key containing layer maps with `:=data`, `:=x`, `:=y`, `:=plottype` keys.
;; This is the compositional plot specification before rendering.

;; ### 🧪 Example 2: Plain Clojure Data Structures

;; The API works with plain Clojure data structures, not just datasets.
;; Two formats are supported:

;; **Map of vectors** (most common for columnar data):
;; Inspect the spec:
(kind/pprint
 (=* (data {:x [1 2 3 4 5]
            :y [2 4 6 8 10]})
     (mapping :x :y)
     (scatter)))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=x (first (:=layers %))) :x)
                       (= (:=y (first (:=layers %))) :y)
                       (map? (:=data (first (:=layers %)))))])

;; Render the plot:
(plot
 (=* (data {:x [1 2 3 4 5]
            :y [2 4 6 8 10]})
     (mapping :x :y)
     (scatter)))

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **Vector of maps** (row-oriented data):
;; Inspect the spec:
(kind/pprint
 (=* (data [{:x 1 :y 2}
            {:x 2 :y 4}
            {:x 3 :y 6}
            {:x 4 :y 8}
            {:x 5 :y 10}])
     (mapping :x :y)
     (scatter)))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=x (first (:=layers %))) :x)
                       (= (:=y (first (:=layers %))) :y))])

;; Render the plot:
(plot
 (=* (data [{:x 1 :y 2}
            {:x 2 :y 4}
            {:x 3 :y 6}
            {:x 4 :y 8}
            {:x 5 :y 10}])
     (mapping :x :y)
     (scatter)))

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What happens here**:

;; 1. Plain Clojure data (no tech.ml.dataset required)
;; 2. Both formats convert seamlessly to datasets internally
;; 3. Type inference works by examining values
;; 4. Helpful errors if data format is invalid

;; This is useful for quick exploration or when working with simple data
;; that doesn't need the full power of tech.ml.dataset.

;; ### 🧪 Example 2b: Threading-Macro Style
;;
;; All the API functions support Clojure's threading macro (`->`), providing
;; a more natural, pipeline-style syntax. This is especially useful for complex
;; visualizations where you're building up layers incrementally.

;; **Simple scatter plot**:
;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=x (first (:=layers %))) :bill-length-mm)
                       (= (:=y (first (:=layers %))) :bill-depth-mm)
                       (= (:=plottype (first (:=layers %))) :scatter))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **With color aesthetic**:
;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (scatter)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=color (first (:=layers %))) :species)
                       (= (:=x (first (:=layers %))) :bill-length-mm))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (scatter)
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; ### 🧪 Example 2c: Semi-Transparent Points
;;
;; Control visual attributes like opacity using the attributes parameter.
;; This is useful when you have overlapping points and want to show density.

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter {:alpha 0.5})
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=alpha (first (:=layers %))) 0.5)
                       (= (:=plottype (first (:=layers %))) :scatter))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter {:alpha 0.5})
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **Combining attributes with aesthetics**:
;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (scatter {:alpha 0.7})
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=alpha (first (:=layers %))) 0.7)
                       (= (:=color (first (:=layers %))) :species))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (scatter {:alpha 0.7})
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; ### 🧪 Example 2d: Scale Customization
;;
;; **Combining scale customization**:
;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (scale :x {:domain [30 65]})
    (scale :y {:domain [12 23]})
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (get-in % [:=scale-x :domain]) [30 65])
                       (= (get-in % [:=scale-y :domain]) [12 23]))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (scale :x {:domain [30 65]})
    (scale :y {:domain [12 23]})
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **Works with plain data too**:
;; Inspect the spec:
(-> {:x [1 2 3 4 5]
     :y [2 4 6 8 10]}
    (mapping :x :y)
    (scatter)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=x (first (:=layers %))) :x)
                       (= (:=plottype (first (:=layers %))) :scatter))])

;; Render the plot:
(-> {:x [1 2 3 4 5]
     :y [2 4 6 8 10]}
    (mapping :x :y)
    (scatter)
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What's happening under the hood**:
;;
;; 1. Each function detects whether its first argument is a plot spec
;;    or data (dataset, map-of-vectors, vector-of-maps)
;; 2. If data: converts to a plot spec first via `(data ...)`
;; 3. If a plot spec: merges the new specification using `=*`
;; 4. Everything returns plot specs, so threading works naturally
;;
;; **Both styles work**:
;;
;; You can use compositional style with `=*`:
;; ```clojure
;; (=* (data penguins) (mapping :x :y) (scatter))
;; ```
;;
;; Or threading style with `->`:
;; ```clojure
;; (-> penguins (mapping :x :y) (scatter))
;; ```
;;
;; They produce identical results. Choose whichever feels more natural for your use case.

;; ### 🧪 Example 3: Type Information in Action

;; Now let's see how we can use Tablecloth's type information.
;; We get types for free, no complex inference needed.

;; Note: infer-from-values is defined above with other type helpers

(defn infer-scale-type
  "Infer scale type from values in a layer."
  [layer aesthetic]
  (let [data (:=data layer)
        dataset (ensure-dataset data)
        col-key (get layer aesthetic)
        values (when col-key (tc/column dataset col-key))]
    (cond
      (nil? values) nil
      (every? number? values) :continuous
      (some #(instance? java.time.temporal.Temporal %) values) :temporal
      :else :categorical)))

(let [spec (=* (data penguins)
               (mapping :bill-length-mm :bill-depth-mm {:color :species})
               (scatter))
      layer (first (:=layers spec))]
  {:x-type (infer-scale-type layer :=x)
   :y-type (infer-scale-type layer :=y)
   :color-type (infer-scale-type layer :=color)})

;; Notice: Type inference is instant (O(1) lookup from Tablecloth metadata),
;; not O(n) value examination.

;; **Why this matters:** Column types influence many plotting decisions—scale types,
;; grouping behavior for statistical transforms, default visual encodings, and more.
;; Getting types efficiently from metadata rather than examining values makes the
;; API faster and more predictable.

;; With linear regression implemented, we can overlay trend lines.

;; # Linear Regression
;; 
;; Statistical transformation: computing and overlaying regression lines.

;; ## ⚙️ Implementation

;; ### ⚙️ Constructor

(defn linear
  "Add linear regression transformation layer.

  Computes the best-fit line through data points using linear regression.
  When combined with a categorical color aesthetic, computes separate
  regression lines for each group automatically.

  The regression is computed using ordinary least squares (OLS) and rendered
  as a line layer. This is a statistical transform - it derives new data
  (the fitted line) from the raw data points.

  Args (when provided):
  - spec-or-data: Plot spec to compose with, or data to create spec from

  Returns:
  - Plot spec map with :=layers containing a linear regression layer

  Forms:

  1. No args - Returns basic linear regression layer:
     (linear)
     ;; => {:=layers [{:=transformation :linear :=plottype :line}]}

  2. Threading with existing spec:
     (-> (data penguins)
         (mapping :bill-length-mm :bill-depth-mm)
         (linear))
     ;; => Adds regression layer to spec

  3. Composing with data directly:
     (linear penguins)
     ;; => {:=layers [{:=data penguins :=transformation :linear :=plottype :line}]}

  Example - simple linear regression:
  (-> penguins
      (mapping :bill-length-mm :bill-depth-mm)
      (linear))

  Example - regression with scatter overlay:
  (-> penguins
      (mapping :bill-length-mm :bill-depth-mm)
      (=+ (scatter {:alpha 0.5})
          (linear)))

  Example - grouped regression by species:
  (-> penguins
      (mapping :bill-length-mm :bill-depth-mm {:color :species})
      (=+ (scatter {:alpha 0.5})
          (linear)))
  ;; => Computes separate regression line for each species"
  ([]
   (let [result {:=transformation :linear
                 :=plottype :line}]
     {:=layers [result]}))
  ([spec-or-data]
   (let [spec (if (plot-spec? spec-or-data)
                spec-or-data
                (data spec-or-data))]
     (=* spec (linear)))))

;; ### ⚙️ Compute Function

(defn- compute-linear-regression
  "Compute linear regression using fastmath.

  Args:
  - points: Sequence of point maps with :x and :y keys

  Returns: Vector of 2 points representing the fitted line, or nil if regression fails.

  Edge cases:
  - Returns nil if fewer than 2 points
  - Returns nil if all x values are identical (vertical line, undefined slope)
  - Returns nil if all y values are identical (returns horizontal line at y mean)
  - Returns nil if regression computation fails"
  [points]
  (when (>= (count points) 2)
    (let [x-vals (mapv :x points)
          y-vals (mapv :y points)
          x-min (apply min x-vals)
          x-max (apply max x-vals)]

      ;; Check for degenerate cases
      (cond
        ;; All x values identical - can't fit a line
        (= x-min x-max)
        nil

        ;; All y values identical - return horizontal line
        (apply = y-vals)
        (let [y-val (first y-vals)]
          [{:x x-min :y y-val}
           {:x x-max :y y-val}])

        ;; Normal case - fit regression
        :else
        (try
          (let [xss (mapv vector x-vals)
                model (regr/lm y-vals xss)
                intercept (:intercept model)
                slope (first (:beta model))]
            ;; Check for valid regression coefficients
            (when (and (number? intercept) (number? slope)
                       (not (Double/isNaN intercept))
                       (not (Double/isNaN slope))
                       (not (Double/isInfinite slope)))
              ;; A straight line only needs 2 points (start and end)
              [{:x x-min :y (clojure.core/+ intercept (clojure.core/* slope x-min))}
               {:x x-max :y (clojure.core/+ intercept (clojure.core/* slope x-max))}]))
          (catch Exception e
            ;; Log the error for debugging but don't crash
            nil))))))

;; ### ⚙️ Transform Multimethod

;; Apply linear regression transform to points.
;;
;; Handles both single and grouped regression based on `:group` key in points.
;;
;; Args:
;; - `layer`: Layer map containing transformation specification
;; - `points`: Sequence of point maps with `:x`, `:y`, and optional `:group` keys
;;
;; Returns:
;; - For ungrouped: `{:type :regression :points points :fitted [p1 p2]}`
;; - For grouped: `{:type :grouped-regression :points points :groups {group-val {:fitted [...] :points [...]}}}`
;;
;; Edge cases:
;; - Returns original points if regression fails (< 2 points, degenerate data)
;; - Handles `nil` fitted values gracefully (skipped during rendering)
(defmethod apply-transform :linear
  [layer points]
  (when-not (seq points)
    (throw (ex-info "Cannot compute linear regression on empty dataset"
                    {:layer-transform (:=transformation layer)
                     :point-count 0})))

  (let [has-groups? (some :group points)]
    (if has-groups?
      ;; Group-wise regression
      (let [grouped (group-by :group points)
            group-results (into {}
                                (map (fn [[group-val group-points]]
                                       [group-val
                                        {:fitted (compute-linear-regression group-points)
                                         :points group-points}])
                                     grouped))]
        {:type :grouped-regression
         :points points
         :groups group-results})
      ;; Single regression
      (let [fitted (compute-linear-regression points)]
        {:type :regression
         :points points
         :fitted (or fitted points)}))))

;; ### ⚙️ Domain Points Multimethods

;; Single regression
(defmethod transform->domain-points :regression
  [transform-result]
  (:fitted transform-result))

;; Grouped regression
(defmethod transform->domain-points :grouped-regression
  [transform-result]
  (mapcat (fn [{:keys [fitted]}] fitted)
          (vals (:groups transform-result))))

;; ### ⚙️ Rendering Multimethod

(defmethod render-layer [:geom :linear]
  [target layer transform-result alpha & [context]]
  (let [transform-type (:type transform-result)]
    (case transform-type
      ;; Single regression line
      :regression
      (let [fitted (:fitted transform-result)]
        [{:values (mapv (fn [p] [(:x p) (:y p)]) fitted)
          :layout viz/svg-line-plot
          :attribs {:stroke (:default-mark theme)
                    :stroke-width 2
                    :opacity alpha}}])

      ;; Grouped regression lines
      :grouped-regression
      (let [groups (:groups transform-result)
            colors (color-scale (keys groups))]
        (mapv (fn [[group-val {:keys [fitted]}]]
                (when fitted
                  {:values (mapv (fn [p] [(:x p) (:y p)]) fitted)
                   :layout viz/svg-line-plot
                   :attribs {:stroke (get colors group-val)
                             :stroke-width 2
                             :opacity alpha}}))
              groups)))))

;; ## 🧪 Examples

;; ### 🧪 Example 4: Multi-Layer Composition (Scatter + Linear Regression)

;; Multi-layer plots using the `=+` operator:

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (=+ (scatter {:alpha 0.5})
        (linear))
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (vector? (:=layers %))
                       (= (count (:=layers %)) 2)
                       (= (:=plottype (first (:=layers %))) :scatter)
                       (= (:=transformation (second (:=layers %))) :linear)
                       (= (:=alpha (first (:=layers %))) 0.5))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (=+ (scatter {:alpha 0.5})
        (linear))
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; ### 🧪 Example 5: Grouped Linear Regression (Color Aesthetic)

;; When a categorical aesthetic (`:color`) is used, linear regression computes
;; separate regression lines for each group:

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (=+ (scatter {:alpha 0.6})
        (linear))
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 2)
                       ;; Both layers have color aesthetic
                       (= (:=color (first (:=layers %))) :species)
                       (= (:=color (second (:=layers %))) :species)
                       ;; Second layer is linear transform
                       (= (:=transformation (second (:=layers %))) :linear))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (=+ (scatter {:alpha 0.6})
        (linear))
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What happens here**:

;; 1. Dataset is grouped by `:species` (categorical column detected via Tablecloth's `tcc/typeof`)
;; 2. Three separate OLS regressions computed (one per species)
;; 3. Three regression lines rendered with matching colors
;; 4. Demonstrates type-aware grouping for statistical transforms

;; Histograms demonstrate statistical transforms that derive new data from the input.

;; # Histograms
;;
;; Statistical transformation: binning continuous data and counting occurrences.

;; ## ⚙️ Implementation

;; ### ⚙️ Constructor

(defn histogram
  "Add histogram transformation layer.

  Bins continuous data into intervals and counts the number of observations
  in each bin. The histogram visualizes the distribution of a single variable.

  This is a statistical transform that requires domain computation - it needs
  to know the data range before determining bin edges. When combined with a
  categorical aesthetic (e.g., :color), computes separate histograms for each
  group.

  Args (when provided):
  - opts-or-spec: Either options map {:bins 20} or plot spec to compose with
  - opts: (2-arg form) Options map

  Options:
  - :bins - Binning method (default :sturges):
    - :sturges - Sturges' formula (good for normal distributions)
    - :sqrt - Square root of n (simple, works for most cases)
    - :rice - Rice's rule (for larger datasets)
    - :freedman-diaconis - Freedman-Diaconis rule (robust to outliers)
    - Integer - Explicit number of bins (e.g., 20)

  Returns:
  - Plot spec map with :=layers containing a histogram layer

  Forms:

  1. No args - Returns basic histogram with default binning:
     (histogram)
     ;; => {:=layers [{:=transformation :histogram :=plottype :bar :=bins :sturges}]}

  2. With options - Custom bin count or method:
     (histogram {:bins 20})
     (histogram {:bins :sqrt})

  3. Threading form:
     (-> penguins
         (mapping :bill-length-mm nil)
         (histogram))

  4. Threading with options:
     (-> penguins
         (mapping :bill-length-mm nil)
         (histogram {:bins 30}))

  Example - basic histogram:
  (-> penguins
      (mapping :bill-length-mm nil)
      (histogram))

  Example - custom bin count:
  (-> penguins
      (mapping :bill-length-mm nil)
      (histogram {:bins 20}))

  Example - grouped histogram by species:
  (-> penguins
      (mapping :bill-length-mm nil {:color :species})
      (histogram))
  ;; => Separate histogram for each species with shared domain

  Note: Histogram only requires :=x mapping, :=y is computed from bin counts."
  ([]
   {:=layers [{:=transformation :histogram
               :=plottype :bar
               :=bins :sturges}]})
  ([opts-or-spec]
   (if (plot-spec? opts-or-spec)
     (=* opts-or-spec (histogram))
     (let [result (merge {:=transformation :histogram
                          :=plottype :bar
                          :=bins :sturges}
                         (update-keys opts-or-spec =key))]
       {:=layers [result]})))
  ([spec opts]
   (=* spec (histogram opts))))

;; ### ⚙️ Compute Function

(defn- compute-histogram
  "Compute histogram bins using fastmath.stats/histogram.

  Args:
  - points: Sequence of point maps with :x key
  - bins-method: Binning method - :sturges, :sqrt, :rice, :freedman-diaconis, or integer count

  Returns: Vector of bar specifications with x-min, x-max, x-center, and height.
           Returns nil if data is invalid (empty, non-numeric, or all identical values).

  Edge cases:
  - Returns nil if no points
  - Returns nil if x values are not all numeric
  - Returns nil if all x values are identical (single value, can't bin)"
  [points bins-method]
  (when (seq points)
    (let [x-vals (mapv :x points)]
      ;; Validate all values are numeric
      (when (every? number? x-vals)
        ;; Check for degenerate case: all values identical
        (let [x-min (apply min x-vals)
              x-max (apply max x-vals)]
          (when-not (= x-min x-max)
            ;; Normal case - compute histogram
            (try
              (let [hist-result (stats/histogram x-vals (or bins-method :sturges))]
                (mapv (fn [bin]
                        (let [bin-min (:min bin)
                              bin-max (:max bin)]
                          {:x-min bin-min
                           :x-max bin-max
                           :x-center (clojure.core// (clojure.core/+ bin-min bin-max) 2.0)
                           :height (:count bin)}))
                      (:bins-maps hist-result)))
              (catch Exception e
                ;; Return nil if histogram computation fails
                nil))))))))

;; ### ⚙️ Transform Multimethod

;; Apply histogram transform to points.
;;
;; Bins continuous x values and counts occurrences per bin.
;; Handles both single and grouped histograms based on `:group` key in points.
;;
;; Args:
;; - `layer`: Layer map containing `:=bins` specification
;; - `points`: Sequence of point maps with `:x` and optional `:group` keys
;;
;; Returns:
;; - For ungrouped: `{:type :histogram :points points :bars [{:x-min :x-max :x-center :height}...]}`
;; - For grouped: `{:type :grouped-histogram :points points :groups {group-val {:bars [...] :points [...]}}}`
;;
;; Edge cases:
;; - Returns `nil` bars if `compute-histogram` fails (empty, non-numeric, or identical values)
;; - Histogram with `nil` bars will not render (graceful degradation)
(defmethod apply-transform :histogram
  [layer points]
  (when-not (seq points)
    (throw (ex-info "Cannot compute histogram on empty dataset"
                    {:layer-transform (:=transformation layer)
                     :point-count 0})))

  (let [has-groups? (some :group points)]
    (if has-groups?
      ;; Group-wise histogram
      (let [grouped (group-by :group points)
            group-results (into {}
                                (map (fn [[group-val group-points]]
                                       [group-val
                                        {:bars (compute-histogram group-points (:=bins layer))
                                         :points group-points}])
                                     grouped))]
        {:type :grouped-histogram
         :points points
         :groups group-results})
      ;; Single histogram
      (let [bins-method (:=bins layer)
            bars (compute-histogram points bins-method)]
        {:type :histogram
         :points points
         :bars bars}))))

;; ### ⚙️ Domain Points Multimethods

(defmethod transform->domain-points :histogram
  [transform-result]
  (mapcat (fn [bar]
            [{:x (:x-min bar) :y 0}
             {:x (:x-max bar) :y (:height bar)}])
          (:bars transform-result)))

(defmethod transform->domain-points :grouped-histogram
  [transform-result]
  (mapcat (fn [{:keys [bars]}]
            (mapcat (fn [bar]
                      [{:x (:x-min bar) :y 0}
                       {:x (:x-max bar) :y (:height bar)}])
                    bars))
          (vals (:groups transform-result))))

;; ### ⚙️ Rendering Multimethod

(defmethod render-layer [:geom :histogram]
  [target layer transform-result alpha & [context]]
  (let [transform-type (:type transform-result)]
    (case transform-type
      ;; Single histogram
      :histogram
      (let [bars (:bars transform-result)]
        (mapv (fn [bar]
                {:type :rect
                 :x-min (:x-min bar)
                 :x-max (:x-max bar)
                 :height (:height bar)
                 :attribs {:fill (:default-mark theme)
                           :stroke (:grid theme)
                           :stroke-width 1
                           :opacity alpha}})
              bars))

      ;; Grouped histogram
      :grouped-histogram
      (let [groups (:groups transform-result)
            colors (color-scale (keys groups))]
        (mapcat (fn [[group-val {:keys [bars]}]]
                  (when bars
                    (mapv (fn [bar]
                            {:type :rect
                             :x-min (:x-min bar)
                             :x-max (:x-max bar)
                             :height (:height bar)
                             :attribs {:fill (get colors group-val)
                                       :stroke (:grid theme)
                                       :stroke-width 1
                                       :opacity alpha}})
                          bars)))
                groups)))))

;; ## 🧪 Examples

;; ### 🧪 Simple Histogram

;; Basic histogram showing distribution of bill lengths:

(-> penguins
    (mapping :bill-length-mm nil)
    (histogram)
    plot)

;; **What happens here**:

;; 1. We map only `:bill-length-mm` to x (no y mapping for histograms)
;; 2. Histogram transform bins the data using Sturges' rule
;; 3. We compute: domain → bin edges → bin counts
;; 4. Rendering target receives bars (bin ranges + heights), not raw points
;; 5. This shows why we can't fully delegate - we need domain before binning.

;; ### 🧪 Custom Bin Count

;; Try different binning methods:

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm nil)
    (histogram {:bins 15})
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=transformation (first (:=layers %))) :histogram)
                       (= (:=bins (first (:=layers %))) 15))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm nil)
    (histogram {:bins 15})
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; ### 🧪 Histogram Binning Methods

;; Different binning algorithms are supported:

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm nil)
    (histogram {:bins :sqrt})
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=bins (first (:=layers %))) :sqrt))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm nil)
    (histogram {:bins :sqrt})
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; # Grouping & Color
;; 
;; Type-aware grouping: categorical colors create groups for statistical transforms.

;; ## 🧪 Example 5: Grouping with Categorical Color

;; When you map a **categorical** variable to color, it automatically creates groups
;; for statistical transforms. This matches AlgebraOfGraphics.jl and ggplot2 behavior.

;; **Categorical color → grouped regression**:
;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (=+ (scatter {:alpha 0.5})
        (linear))
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 2)
                       (= (:=color (first (:=layers %))) :species)
                       (= (:=transformation (second (:=layers %))) :linear))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (=+ (scatter {:alpha 0.5})
        (linear))
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What happens here**:

;; 1. `:species` is categorical (`:string` type in Tablecloth)
;; 2. System automatically groups by `:species` for the linear transform
;; 3. Computes 3 separate regression lines (Adelie, Chinstrap, Gentoo)
;; 4. Each group gets a different color from the ggplot2 palette
;; 5. Scatter points are also colored by species

;; **Categorical color → grouped histogram**:

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm nil {:color :species :alpha 0.7})
    (histogram)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=transformation (first (:=layers %))) :histogram)
                       (= (:=color (first (:=layers %))) :species)
                       (= (:=alpha (first (:=layers %))) 0.7))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm nil {:color :species :alpha 0.7})
    (histogram)
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What happens here**:

;; 1. Creates 3 separate histograms (one per species)
;; 2. Each histogram uses the same binning method
;; 3. Bars are colored by species
;; 4. Alpha transparency (0.7) lets you see overlapping bars
;; 5. This is different from faceting - bars can overlap/stack

;; ## 🧪 Example 6: Continuous Color (No Grouping)

;; When you map a **continuous** variable to color, it creates a visual gradient
;; but does NOT create groups for statistical transforms.

;; **Continuous color → single regression with gradient**:
;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :body-mass-g})
    (=+ (scatter {:alpha 0.5})
        (linear))
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 2)
                       (= (:=color (first (:=layers %))) :body-mass-g)
                       (= (:=transformation (second (:=layers %))) :linear))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :body-mass-g})
    (=+ (scatter {:alpha 0.5})
        (linear))
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What happens here**:

;; 1. `:body-mass-g` is continuous (`:int64` type in Tablecloth)
;; 2. System does NOT group by body mass
;; 3. Computes a single regression line across all points
;; 4. Scatter points are colored by body mass (continuous gradient)
;; 5. The regression line shows overall trend, ignoring color grouping

;; This is the key semantic difference:
;; - Categorical aesthetics → semantic grouping (affects computations)
;; - Continuous aesthetics → visual mapping only (no grouping)

;; ## 🧪 Example 7: Explicit Grouping Override

;; You can explicitly control grouping using the `:group` aesthetic.
;; This lets you group by one variable while coloring by another.

;; **Explicit :group aesthetic**:
;; Inspect the spec:
(-> mtcars
    (mapping :wt :mpg {:group :cyl})
    (=+ (scatter)
        (linear))
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 2)
                       (= (:=group (first (:=layers %))) :cyl)
                       (= (:=transformation (second (:=layers %))) :linear))])

;; Render the plot:
(-> mtcars
    (mapping :wt :mpg {:group :cyl})
    (=+ (scatter)
        (linear))
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What happens here**:

;; 1. `:cyl` (cylinders) could be treated as continuous (it's numeric)
;; 2. But we explicitly group by `:cyl` using `:group`
;; 3. Computes separate regression lines for 4, 6, and 8 cylinder cars
;; 4. No color mapping, so all points/lines use default color

;; **Group different from color**:
;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :sex :group :species})
    (=+ (scatter {:alpha 0.5})
        (linear))
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=color (first (:=layers %))) :sex)
                       (= (:=group (first (:=layers %))) :species)
                       (= (:=transformation (second (:=layers %))) :linear))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :sex :group :species})
    (=+ (scatter {:alpha 0.5})
        (linear))
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What happens here**:

;; 1. Color by `:sex` (2 colors: male/female)
;; 2. Group by `:species` (3 regression lines: Adelie/Chinstrap/Gentoo)
;; 3. Points are colored by sex, but regressions computed per species
;; 4. This shows that grouping and color are independent concepts

;; # Faceting
;;
;; Small multiples: splitting data across rows and columns for comparison.

;; ## 📖 Architectural Considerations
;;
;; Implementing faceting has exposed several important design questions:
;;
;; ### 📖 1. Statistical Transforms Must Be Per-Facet
;;
;; **Example**: Histogram of bill-length faceted by species
;; - Semantically: User expects 3 separate histograms (one per species)
;; - Implementation: Must split data by `:species` BEFORE computing histograms
;; - Implication: Can't compute transforms before knowing about facets
;;
;; **Current architecture**: We apply transforms in `plot-impl` after extracting points
;; **Needed**: Apply transforms to each facet group separately
;;
;; ### 📖 2. Domain Computation: Shared vs Free Scales
;;
;; **Shared scales** (ggplot2 default):
;; - All facets use same x/y domain
;; - Pro: Easy to compare across facets
;; - Con: Facets with fewer observations may have compressed ranges
;;
;; **Free scales**:
;; - Each facet optimizes its own domain
;; - Pro: Each facet uses full visual range
;; - Con: Harder to compare magnitudes across facets
;;
;; **Decision**: Start with shared scales (simpler), add `:scales` option later
;;
;; ### 📖 3. Delegation Strategy: Control vs Leverage
;;
;; **Our principle**: We control semantics, targets handle presentation
;;
;; **What we control**:
;; - Data splitting by facet variable
;; - Per-facet transform computation
;; - Domain computation (shared or free)
;; - Color scale consistency
;;
;; **What targets can handle**:
;; - `:geom` - we compute layout positions manually
;; - `:vl`/`:plotly` - could use their grid layout features
;;
;; ### 📖 4. Rendering Architecture for :geom
;;
;; **Challenge**: thi.ng/geom-viz doesn't support multi-panel layouts
;;
;; **Needed**:

;; 1. Calculate panel dimensions (width / num-facets)
;; 2. Calculate panel positions (x-offsets)
;; 3. Create mini-plot for each facet at its position
;; 4. Add facet labels  
;; 5. Combine into single SVG
;;
;; **Complexity**: Significant refactoring of plot-impl :geom
;;
;; ### 📖 5. The Core Insight
;;
;; **Faceting reveals the same pattern as statistical transforms**:
;; - We MUST control the semantics (data splitting, per-group computation)
;; - But we CAN delegate presentation (layout, when targets support it)
;; - This validates our minimal delegation strategy
;;
;; **For statistical transforms + faceting**:
;; ```clojure
;; (plot (facet (=* (data penguins)
;;                  (mapping :bill-length-mm nil)
;;                  (histogram))
;;              {:col :species}))
;; ```
;;
;; This requires:

;; 1. Split data by species (3 groups)
;; 2. Compute histogram for EACH group (per-facet transforms)
;; 3. Collect domains across all groups (shared scales)
;; 4. Render 3 mini-histograms side-by-side
;;
;; **This is our value proposition**: Compute on JVM, send summaries to browser.
;; With faceting, even more important!"

;; ## 📖 Design Decisions
;;
;; After prototyping, we've decided:
;;
;; 1. **We control semantics** - Split data, compute transforms per-facet, manage domains
;; 2. **Targets handle presentation** - Layout and rendering (when they can)
;; 3. **Shared scales by default** - All facets use same domain (easier comparison)
;; 4. **Statistical transforms per-facet** - Histogram by species = 3 separate histograms
;;
;; ## ⚙️ Implementation Strategy
;;
;; 1. `split-by-facets` - Groups data by facet variable(s)
;; 2. Apply transforms to each facet group separately
;; 3. Compute domains across all facets (for shared scales)
;; 4. Render each facet as mini-plot
;;
;; For `:geom` target - compute layout positions manually
;; For `:vl`/`:plotly` targets - could use their grid layout features

;; ## 🧪 Examples

;; ### 🧪 Example 10: Simple Column Faceting
;;
;; Facet a scatter plot by species - this creates 3 side-by-side plots.
;; Inspect the spec:
(kind/pprint
 (=* (data penguins)
     (mapping :bill-length-mm :bill-depth-mm)
     (scatter)
     (facet {:col :species})))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=col (first (:=layers %))) :species))])

;; Render the plot:
(plot
 (=* (data penguins)
     (mapping :bill-length-mm :bill-depth-mm)
     (scatter)
     (facet {:col :species})))

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; Faceted histogram - per-species histograms with shared scales:

(-> penguins
    (mapping :bill-length-mm nil)
    (histogram)
    (facet {:col :species})
    plot)

;; ### 🧪 Example 11: Row Faceting
;;
;; Facet by rows creates vertically stacked panels

;; Inspect the spec:
(kind/pprint
 (=* (data penguins)
     (mapping :bill-length-mm :bill-depth-mm)
     (scatter)
     (facet {:row :species})))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=row (first (:=layers %))) :species))])

;; Render the plot:
(plot
 (=* (data penguins)
     (mapping :bill-length-mm :bill-depth-mm)
     (scatter)
     (facet {:row :species})))

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; ### 🧪 Example 12: Row × Column Grid Faceting
;;
;; Create a 2D grid of facets.
;; This creates a 3×2 grid (3 islands × 2 sexes = 6 panels)

;; Inspect the spec:
(kind/pprint
 (=* (data penguins)
     (mapping :bill-length-mm :bill-depth-mm)
     (scatter)
     (facet {:row :island :col :sex})))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=row (first (:=layers %))) :island)
                       (= (:=col (first (:=layers %))) :sex))])

;; Render the plot:
(plot
 (=* (data penguins)
     (mapping :bill-length-mm :bill-depth-mm)
     (scatter)
     (facet {:row :island :col :sex})))

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What happens here**:

;; 1. Data split by both `:island` (3 values) and `:sex` (2 values)
;; 2. Creates 3×2 = 6 panels in a grid
;; 3. Column labels at top, row labels on left (rotated)
;; 4. Shared scales across all panels for easy comparison
;; 5. Per-panel rendering with proper x and y offsets

;; ### 🧪 Example 12b: Multiple Grouping Columns (Color + Faceting)
;;
;; When you combine color grouping with faceting, statistical transforms
;; group by BOTH aesthetics. This example shows linear regression grouped
;; by species (color) AND island (facet column), computing separate
;; regressions for each (species × island) combination.

(plot
 (=* (data penguins)
     (mapping :bill-length-mm
              :bill-depth-mm
              {:color :species})
     (=+ (scatter {:alpha 0.5})
         (linear))
     (facet {:col :island})))

;; equivalently:
;; Inspect the spec:
(-> (data penguins)
    (mapping :bill-length-mm
             :bill-depth-mm
             {:color :species})
    (=+ (scatter {:alpha 0.5})
        (linear))
    (facet {:col :island})
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 2)
                       (= (:=col (first (:=layers %))) :island)
                       (= (:=color (first (:=layers %))) :species))])

;; Render the plot:
(-> (data penguins)
    (mapping :bill-length-mm
             :bill-depth-mm
             {:color :species})
    (=+ (scatter {:alpha 0.5})
        (linear))
    (facet {:col :island})
    plot)

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What happens here**:
;;
;; 1. Faceting by `:island` creates 3 panels (one per island)
;; 2. Color by `:species` creates 3 groups (one per species)
;; 3. Linear regression groups by BOTH: 3 species × 3 islands = 9 regressions
;; 4. Each panel shows 3 colored regression lines (one per species on that island)
;; 5. This demonstrates that `get-grouping-columns` returns `[:species :island]`
;;
;; **Implementation detail**: The `get-grouping-columns` function collects all
;; categorical aesthetics that create groups (:=color, :=col, :=row, :=group)
;; and returns a vector. Transforms then group by the combination of all these columns.

;; ### 🧪 Example 13: Custom Scale Domains
;;
;; Override auto-computed domains to control axis ranges

;; Force y-axis to start at 0
;; Inspect the spec:
(kind/pprint
 (=* (data mtcars)
     (mapping :wt :mpg)
     (scatter)
     (scale :y {:domain [0 40]})))

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (get-in % [:=scale-y :domain]) [0 40]))])

;; Render the plot:
(plot
 (=* (data mtcars)
     (mapping :wt :mpg)
     (scatter)
     (scale :y {:domain [0 40]})))

(kind/test-last [#(and (vector? %)
                       (string? (first %))
                       (clojure.string/includes? (first %) "<svg"))])

;; **What happens here**:

;; 1. Y-axis forced to extend from 0 to 40 instead of auto-computed range from 10.4 to 33.9
;; 2. Useful for starting axes at meaningful values (like 0)
;; 3. Custom domains compose via `=*` operator

;; Custom domains on both axes
(plot
 (=* (data penguins)
     (mapping :bill-length-mm :bill-depth-mm)
     (scatter)
     (scale :x {:domain [30 65]})
     (scale :y {:domain [10 25]})))

;; **What happens here**:

;; 1. Both axes use custom ranges
;; 2. Zooms into a specific region of the data
;; 3. Useful for focusing on areas of interest or ensuring consistent scales across multiple plots

;; # Multi-Target Rendering
;; 
;; Same API, different backends: :geom (SVG), :vl (Vega-Lite), :plotly (Plotly.js).

;; ## ⚙️ Implementation: Vega-Lite Target

;; ### ⚙️ Vega-Lite Helper Functions

(defn- layer->vl-data
  "Convert layer data to Vega-Lite data format."
  [layer]
  (let [data (:=data layer)
        dataset (ensure-dataset data)
        x-col (:=x layer)
        y-col (:=y layer)
        color-col (:=color layer)
        row-col (:=row layer)
        col-col (:=col layer)
        rows (tc/rows dataset :as-maps)]
    (mapv (fn [row]
            (cond-> {}
              x-col (assoc (keyword (name x-col)) (get row x-col))
              y-col (assoc (keyword (name y-col)) (get row y-col))
              color-col (assoc (keyword (name color-col)) (get row color-col))
              row-col (assoc (keyword (name row-col)) (get row row-col))
              col-col (assoc (keyword (name col-col)) (get row col-col))))
          rows)))

(defn- layer->vl-encoding
  "Create Vega-Lite encoding for a layer."
  [layer context]
  (let [x-col (:=x layer)
        y-col (:=y layer)
        color-col (:=color layer)
        alpha (:=alpha layer)
        custom-x-domain (:custom-x-domain context)
        custom-y-domain (:custom-y-domain context)
        ;; Build tooltip fields
        tooltip-fields (cond-> []
                         x-col (conj {:field (name x-col) :type "quantitative"})
                         y-col (conj {:field (name y-col) :type "quantitative"})
                         color-col (conj {:field (name color-col) :type "nominal"}))]
    (cond-> {}
      x-col (assoc :x (cond-> {:field (name x-col) :type "quantitative"}
                        true (assoc :scale (merge {:zero false}
                                                  (when custom-x-domain {:domain custom-x-domain})))))
      y-col (assoc :y (cond-> {:field (name y-col) :type "quantitative"}
                        true (assoc :scale (merge {:zero false}
                                                  (when custom-y-domain {:domain custom-y-domain})))))
      color-col (assoc :color {:field (name color-col)
                               :type "nominal"
                               :scale {:range (:colors theme)}})
      alpha (assoc :opacity {:value alpha})
      (seq tooltip-fields) (assoc :tooltip tooltip-fields))))

;; ### ⚙️ Vega-Lite Target Rendering Methods

(defmethod render-layer [:vl :scatter]
  [target layer transform-result alpha & [context]]
  (let [vl-data (layer->vl-data layer)]
    [{:mark "circle"
      :data {:values vl-data}
      :encoding (layer->vl-encoding layer context)}]))

(defmethod render-layer [:vl :linear]
  [target layer transform-result alpha & [context]]
  (let [transform-type (:type transform-result)]
    (case transform-type
      ;; Single regression - send computed line
      :regression
      (let [fitted (:fitted transform-result)
            fitted-data (mapv (fn [p]
                                {(keyword (name (:=x layer))) (:x p)
                                 (keyword (name (:=y layer))) (:y p)})
                              fitted)]
        [{:mark "line"
          :data {:values fitted-data}
          :encoding (layer->vl-encoding layer context)}])

      ;; Grouped regression - one line per group
      :grouped-regression
      (let [groups (:groups transform-result)
            grouping-cols (get-grouping-columns layer (ensure-dataset (:=data layer)))]
        (mapv (fn [[group-val {:keys [fitted]}]]
                (when fitted
                  (let [;; Create map of grouping column names to values
                        group-map (zipmap (map #(keyword (name %)) grouping-cols)
                                          (if (vector? group-val) group-val [group-val]))
                        group-fitted-data (mapv (fn [p]
                                                  (merge {(keyword (name (:=x layer))) (:x p)
                                                          (keyword (name (:=y layer))) (:y p)}
                                                         group-map))
                                                fitted)]
                    {:mark "line"
                     :data {:values group-fitted-data}
                     :encoding (layer->vl-encoding layer context)})))
              groups)))))

(defmethod render-layer [:vl :histogram]
  [target layer transform-result alpha & [context]]
  (let [transform-type (:type transform-result)]
    (case transform-type
      ;; Single histogram - send computed bars
      :histogram
      (let [bars (:bars transform-result)
            bar-data (mapv (fn [bar]
                             {:bin-start (:x-min bar)
                              :bin-end (:x-max bar)
                              :count (:height bar)})
                           bars)]
        [{:mark "bar"
          :data {:values bar-data}
          :encoding {:x {:field "bin-start"
                         :type "quantitative"
                         :bin {:binned true :step (- (:x-max (first bars)) (:x-min (first bars)))}
                         :axis {:title (name (:=x layer))}}
                     :x2 {:field "bin-end"}
                     :y {:field "count" :type "quantitative"}
                     :tooltip [{:field "bin-start" :type "quantitative" :title "Min"}
                               {:field "bin-end" :type "quantitative" :title "Max"}
                               {:field "count" :type "quantitative" :title "Count"}]}}])

      ;; Grouped histogram - bars per group
      :grouped-histogram
      (let [groups (:groups transform-result)
            grouping-cols (get-grouping-columns layer (ensure-dataset (:=data layer)))]
        (mapcat (fn [[group-val {:keys [bars]}]]
                  (when bars
                    (let [;; Create map of grouping column names to values
                          group-map (zipmap (map #(keyword (name %)) grouping-cols)
                                            (if (vector? group-val) group-val [group-val]))
                          bar-data (mapv (fn [bar]
                                           (merge {:bin-start (:x-min bar)
                                                   :bin-end (:x-max bar)
                                                   :count (:height bar)}
                                                  group-map))
                                         bars)
                          ;; Add all grouping columns to tooltip
                          tooltip-fields (concat [{:field "bin-start" :type "quantitative" :title "Min"}
                                                  {:field "bin-end" :type "quantitative" :title "Max"}
                                                  {:field "count" :type "quantitative" :title "Count"}]
                                                 (map #(hash-map :field (name %) :type "nominal")
                                                      grouping-cols))]
                      [{:mark "bar"
                        :data {:values bar-data}
                        :encoding (merge
                                   {:x {:field "bin-start"
                                        :type "quantitative"
                                        :bin {:binned true :step (- (:x-max (first bars)) (:x-min (first bars)))}
                                        :axis {:title (name (:=x layer))}}
                                    :x2 {:field "bin-end"}
                                    :y {:field "count" :type "quantitative"}
                                    :tooltip tooltip-fields}
                                   ;; Use first grouping column for color (typically :color aesthetic)
                                   (when (seq grouping-cols)
                                     {:color {:field (name (first grouping-cols)) :type "nominal"}}))}])))
                groups)))))

(defmethod plot-impl :vl
  [spec opts]
  ;; Extract layers from spec
  (let [layers-vec (get spec :=layers [])
        _ (validate-layers! layers-vec)

        ;; Extract plot-level properties (spec first, then opts, then defaults)
        width (or (:=width spec) (:width opts) 600)
        height (or (:=height spec) (:height opts) 400)

        ;; Check for faceting
        is-faceted? (has-faceting? layers-vec)
        row-var (when is-faceted? (some :=row layers-vec))
        col-var (when is-faceted? (some :=col layers-vec))

        ;; Check for custom scale domains
        custom-x-domain (get-scale-domain spec :x)
        custom-y-domain (get-scale-domain spec :y)

        ;; Create context map for render-layer
        context {:custom-x-domain custom-x-domain
                 :custom-y-domain custom-y-domain}

        ;; Process each layer using render-layer multimethod
        vl-layers (mapcat (fn [layer]
                            (let [points (layer->points layer)
                                  alpha (or (:=alpha layer) 1.0)
                                  transform-result (apply-transform layer points)]
                              (render-layer :vl layer transform-result alpha context)))
                          layers-vec)

        ;; Remove nils from nested groups (e.g., grouped regression)
        vl-layers (remove nil? (flatten vl-layers))

        ;; ggplot2-compatible theme config
        ggplot2-config {:view {:stroke "transparent"}
                        :background (:background theme)
                        :axis {:gridColor (:grid theme)
                               :domainColor (:grid theme)
                               :tickColor (:grid theme)}
                        :mark {:color (:default-mark theme)}}

        ;; Build final spec
        spec (cond
               ;; Faceted plot
               is-faceted?
               (let [;; For faceted plots, remove :data from individual layers
                     ;; Data goes at TOP level for VL faceting, not inside spec
                     layers-without-data (mapv #(dissoc % :data) vl-layers)
                     all-data (mapcat layer->vl-data layers-vec)

                     ;; Count actual number of unique facet values
                     first-layer (first layers-vec)
                     dataset (ensure-dataset (:=data first-layer))
                     num-cols (if col-var
                                (count (distinct (tc/column dataset col-var)))
                                1)
                     num-rows (if row-var
                                (count (distinct (tc/column dataset row-var)))
                                1)]
                 (cond-> {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
                          :data {:values all-data}
                          :width (int (/ width num-cols))
                          :height (int (/ height num-rows))
                          :config ggplot2-config
                          :spec {:layer layers-without-data}}
                   col-var (assoc :facet {:column {:field (name col-var) :type "nominal"}})
                   row-var (assoc-in [:facet :row] {:field (name row-var) :type "nominal"})))

               ;; Multi-layer plot
               (> (count vl-layers) 1)
               {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
                :width width
                :height height
                :config ggplot2-config
                :layer vl-layers}

               ;; Single layer plot
               :else
               (merge {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
                       :width width
                       :height height
                       :config ggplot2-config}
                      (first vl-layers)))]

    (kind/vega-lite spec)))

;; ## ⚙️ Implementation: Plotly Target

;; ### ⚙️ Plotly Target Rendering Methods

(defmethod render-layer [:plotly :scatter]
  [target layer transform-result alpha & [context]]
  (let [points (:points transform-result)
        color-col (:=color layer)]
    (if color-col
      ;; Grouped scatter - one trace per group
      (let [color-groups (group-by :color points)]
        (map-indexed
         (fn [idx [color-val group-points]]
           {:type "scatter"
            :mode "markers"
            :x (mapv :x group-points)
            :y (mapv :y group-points)
            :name (str color-val)
            :marker {:color (get (:colors theme) idx (:default-mark theme))
                     :size 8}})
         color-groups))
      ;; Single scatter trace
      [{:type "scatter"
        :mode "markers"
        :x (mapv :x points)
        :y (mapv :y points)
        :marker {:color (:default-mark theme) :size 8}
        :showlegend false}])))

(defmethod render-layer [:plotly :linear]
  [target layer transform-result alpha & [context]]
  (let [transform-type (:type transform-result)]
    (case transform-type
      ;; Single regression line
      :regression
      (let [fitted (:fitted transform-result)]
        [{:type "scatter"
          :mode "lines"
          :x (mapv :x fitted)
          :y (mapv :y fitted)
          :line {:color (:default-mark theme) :width 2}
          :showlegend false}])

      ;; Grouped regression - one line per group
      :grouped-regression
      (let [groups (:groups transform-result)]
        (map-indexed
         (fn [idx [group-val {:keys [fitted]}]]
           (when fitted
             {:type "scatter"
              :mode "lines"
              :x (mapv :x fitted)
              :y (mapv :y fitted)
              :name (str group-val " (fit)")
              :line {:color (get (:colors theme) idx (:default-mark theme))
                     :width 2}
              :showlegend false}))
         groups)))))

(defmethod render-layer [:plotly :histogram]
  [target layer transform-result alpha & [context]]
  (let [transform-type (:type transform-result)]
    (case transform-type
      ;; Single histogram
      :histogram
      (let [bars (:bars transform-result)]
        [{:type "bar"
          :x (mapv (fn [b] (/ (+ (:x-min b) (:x-max b)) 2)) bars)
          :y (mapv :height bars)
          :width (mapv (fn [b] (- (:x-max b) (:x-min b))) bars)
          :marker {:color (:default-mark theme)
                   :line {:color (:grid theme) :width 1}}
          :showlegend false}])

      ;; Grouped histogram - bars per group
      :grouped-histogram
      (let [groups (:groups transform-result)]
        (map-indexed
         (fn [idx [group-val {:keys [bars]}]]
           (when bars
             {:type "bar"
              :x (mapv (fn [b] (/ (+ (:x-min b) (:x-max b)) 2)) bars)
              :y (mapv :height bars)
              :width (mapv (fn [b] (- (:x-max b) (:x-min b))) bars)
              :name (str group-val)
              :marker {:color (get (:colors theme) idx (:default-mark theme))
                       :line {:color (:grid theme) :width 1}}}))
         groups)))))

(defmethod plot-impl :plotly
  [spec opts]
  ;; Extract layers from spec
  (let [layers-vec (get spec :=layers [])
        _ (validate-layers! layers-vec)

        ;; Extract plot-level properties (spec first, then opts, then defaults)
        width (or (:=width spec) (:width opts) 600)
        height (or (:=height spec) (:height opts) 400)

        ;; Check for faceting
        is-faceted? (has-faceting? layers-vec)
        row-var (when is-faceted? (some :=row layers-vec))
        col-var (when is-faceted? (some :=col layers-vec))

        ;; Check for custom scale domains
        custom-x-domain (get-scale-domain spec :x)
        custom-y-domain (get-scale-domain spec :y)

        ;; Create context map for render-layer
        context {:custom-x-domain custom-x-domain
                 :custom-y-domain custom-y-domain}

        ;; Process each layer using render-layer multimethod
        plotly-traces (mapcat (fn [layer]
                                (let [points (layer->points layer)
                                      alpha (or (:=alpha layer) 1.0)
                                      transform-result (apply-transform layer points)]
                                  (render-layer :plotly layer transform-result alpha context)))
                              layers-vec)

        ;; Remove nils from nested groups (e.g., grouped regression)
        plotly-traces (remove nil? plotly-traces)

        ;; Get column names for axis titles
        first-layer (first layers-vec)
        x-name (when-let [x-col (:=x first-layer)] (name x-col))
        y-name (when-let [y-col (:=y first-layer)] (name y-col))

        ;; ggplot2-compatible layout
        layout {:width width
                :height height
                :plot_bgcolor (:background theme)
                :paper_bgcolor (:background theme)
                :xaxis {:gridcolor (:grid theme)
                        :title (or x-name "x")
                        :range custom-x-domain}
                :yaxis {:gridcolor (:grid theme)
                        :title (or y-name "y")
                        :range custom-y-domain}
                :margin {:l 60 :r 30 :t 30 :b 60}}

        ;; Handle faceting
        spec (if is-faceted?
               (let [;; Get unique facet values
                     data (:=data first-layer)
                     dataset (ensure-dataset data)
                     row-vals (when row-var (sort (distinct (tc/column dataset row-var))))
                     col-vals (when col-var (sort (distinct (tc/column dataset col-var))))
                     num-rows (if row-var (count row-vals) 1)
                     num-cols (if col-var (count col-vals) 1)

                     ;; Create subplot traces
                     faceted-traces
                     (for [[row-idx row-val] (map-indexed vector (or row-vals [nil]))
                           [col-idx col-val] (map-indexed vector (or col-vals [nil]))]
                       (let [;; Filter data for this facet
                             filtered-data (cond-> dataset
                                             row-var (tc/select-rows #(= (get % row-var) row-val))
                                             col-var (tc/select-rows #(= (get % col-var) col-val)))
                             ;; Create layer with filtered data
                             facet-layer (assoc first-layer :=data filtered-data)
                             facet-points (layer->points facet-layer)
                             transform-result (apply-transform facet-layer facet-points)
                             subplot-idx (+ (* row-idx num-cols) col-idx 1)
                             ;; Plotly.js expects "x", "x2", "x3" not "xaxis", "xaxis2", "xaxis3"
                             xaxis-ref (if (= subplot-idx 1) "x" (str "x" subplot-idx))
                             yaxis-ref (if (= subplot-idx 1) "y" (str "y" subplot-idx))]
                         (case (:type transform-result)
                           :raw
                           {:type "scatter"
                            :mode "markers"
                            :x (mapv :x facet-points)
                            :y (mapv :y facet-points)
                            :xaxis xaxis-ref
                            :yaxis yaxis-ref
                            :marker {:color (:default-mark theme) :size 6}
                            :showlegend false}

                           :histogram
                           (let [bars (:bars transform-result)]
                             {:type "bar"
                              :x (mapv (fn [b] (/ (+ (:x-min b) (:x-max b)) 2)) bars)
                              :y (mapv :height bars)
                              :width (mapv (fn [b] (- (:x-max b) (:x-min b))) bars)
                              :xaxis xaxis-ref
                              :yaxis yaxis-ref
                              :marker {:color (:default-mark theme)
                                       :line {:color (:grid theme) :width 1}}
                              :showlegend false})

                           :grouped-histogram
                           ;; For faceted histograms, data is already filtered to single facet
                           ;; but transform still returns grouped result with one group
                           (let [groups (:groups transform-result)
                                 ;; Extract bars from the single group (first value in groups map)
                                 bars (:bars (second (first groups)))]
                             {:type "bar"
                              :x (mapv (fn [b] (/ (+ (:x-min b) (:x-max b)) 2)) bars)
                              :y (mapv :height bars)
                              :width (mapv (fn [b] (- (:x-max b) (:x-min b))) bars)
                              :xaxis xaxis-ref
                              :yaxis yaxis-ref
                              :marker {:color (:default-mark theme)
                                       :line {:color (:grid theme) :width 1}}
                              :showlegend false})

                           nil)))

                     ;; Create subplot layout
                     subplot-layout (merge layout
                                           {:grid {:rows num-rows :columns num-cols :pattern "independent"}
                                            :annotations
                                            (concat
                                             (when col-var
                                               (for [[idx val] (map-indexed vector col-vals)]
                                                 {:text (str val)
                                                  :xref "paper"
                                                  :yref "paper"
                                                  :x (/ (+ idx 0.5) num-cols)
                                                  :y 1.0
                                                  :xanchor "center"
                                                  :yanchor "bottom"
                                                  :showarrow false}))
                                             (when row-var
                                               (for [[idx val] (map-indexed vector row-vals)]
                                                 {:text (str val)
                                                  :xref "paper"
                                                  :yref "paper"
                                                  :x -0.05
                                                  :y (- 1.0 (/ (+ idx 0.5) num-rows))
                                                  :xanchor "right"
                                                  :yanchor "middle"
                                                  :showarrow false})))})]
                 {:data (remove nil? faceted-traces)
                  :layout subplot-layout})
               ;; Non-faceted
               {:data plotly-traces
                :layout layout})]

    (kind/plotly spec)))

;; ## 🧪 Vega-Lite Examples

;; ### 🧪 Example 14: Simple Scatter with Vega-Lite

;; Use `target` to select the `:vl` target (Vega-Lite):

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (target :vl)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=target %) :vl)
                       (= (:=x (first (:=layers %))) :bill-length-mm)
                       (= (:=y (first (:=layers %))) :bill-depth-mm))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (target :vl)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :data)
                       (contains? % :mark)
                       (contains? % :encoding))])

;; **What's different**:

;; 1. Interactive tooltips on hover
;; 2. Vega-Lite's polished default styling
;; 3. Same data, same API, different rendering

;; ### 🧪 Example 15: Multi-Layer with Vega-Lite

;; Scatter + regression works too:

;; Inspect the spec:
(-> mtcars
    (mapping :wt :mpg)
    (=+ (scatter)
        (linear))
    (target :vl)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 2)
                       (= (:=target %) :vl))])

;; Render the plot:
(-> mtcars
    (mapping :wt :mpg)
    (=+ (scatter)
        (linear))
    (target :vl)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :layer)
                       (= (count (:layer %)) 2))])

;; **What happens here**:

;; 1. Scatter plot rendered as VL `point` mark
;; 2. Regression computed on JVM (our delegation strategy!)
;; 3. Fitted line sent to VL as `line` mark
;; 4. VL layers them together

;; ### 🧪 Example 16: Color Mapping with Vega-Lite

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (=+ (scatter)
        (linear))
    (target :vl)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=color (first (:=layers %))) :species)
                       (= (:=target %) :vl))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (=+ (scatter)
        (linear))
    (target :vl)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :layer)
                       (>= (count (:layer %)) 2))])

;; **What happens here**:

;; 1. Color mapping becomes VL's color encoding
;; 2. VL provides interactive legend
;; 3. Regression computed per species (3 separate lines)
;; 4. Click legend to filter interactively.

;; **Histogram with Vega-Lite**:

(-> penguins
    (mapping :bill-length-mm nil)
    (histogram)
    (target :vl)
    plot)

;; **What happens here**:

;; 1. Histogram bins computed on JVM using fastmath
;; 2. Bar data sent to VL
;; 3. VL renders as bar chart
;; 4. Interactive tooltips show bin ranges and counts

;; ### 🧪 Example 17: Faceting with Vega-Lite

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (facet {:col :species})
    (target :vl)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=col (first (:=layers %))) :species)
                       (= (:=target %) :vl))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (facet {:col :species})
    (target :vl)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :facet)
                       (contains? % :spec))])

;; **What happens here**:

;; 1. Our API detects faceting specification
;; 2. Delegates to VL's native column faceting
;; 3. VL handles layout and labels
;; 4. Each panel is independently interactive

;; ### 🧪 Example 19: Grid Faceting with Vega-Lite

;; Grid faceting with custom dimensions using compositional `size`:

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (facet {:row :island :col :sex})
    (target :vl)
    (size 800 600)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=target %) :vl)
                       (= (:=row (first (:=layers %))) :island)
                       (= (:=col (first (:=layers %))) :sex)
                       (= (:=width %) 800)
                       (= (:=height %) 600))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (facet {:row :island :col :sex})
    (target :vl)
    (size 800 600)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :spec)
                       (contains? % :facet)
                       (= (-> % :facet :row :field) "island")
                       (= (-> % :facet :column :field) "sex"))])

;; **What happens here**:

;; 1. 2D grid faceting using VL's row × column faceting
;; 2. VL handles all layout computation
;; 3. Shared scales across all panels
;; 4. Interactive exploration across the grid

;; ### 🧪 Example 20: Custom Domains with Vega-Lite

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (scale :x {:domain [30 65]})
    (scale :y {:domain [10 25]})
    (target :vl)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (get-in % [:=scale-x :domain]) [30 65])
                       (= (:=target %) :vl))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (scale :x {:domain [30 65]})
    (scale :y {:domain [10 25]})
    (target :vl)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :encoding))])

;; **What happens here**:

;; 1. Custom domains passed to VL's scale specification
;; 2. VL respects our domain constraints
;; 3. Same composition semantics across targets

;; ## 📖 The Power of Backend Agnosticism
;;
;; **Key insight**: The flat map structure with `:=...` keys (explained in
;; [Design Exploration](#design-exploration)) creates a clean separation between
;; plot semantics and rendering implementation.
;;
;; **What we control** (across all targets):
;; - Statistical transforms (regression, histogram)
;; - Data transformations
;; - Composition semantics
;;
;; **What targets handle** (differently):
;; - `:geom` - Static SVG, manual layout, ggplot2-like styling
;; - `:vl` - Interactive web viz, native faceting, Vega-Lite styling
;; - `:plotly` - Rich interactivity, 3D support (coming soon)
;;
;; This is the payoff of our design choices:
;; - Flat maps compose with standard library
;; - Minimal delegation keeps implementation focused
;; - Backend-agnostic IR enables multiple rendering strategies

;; Now we can demonstrate multi-target rendering with the same spec.

;; ## 🧪 Plotly Examples

;; ### 🧪 Example 21: Simple Scatter with Plotly

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (target :plotly)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=target %) :plotly)
                       (= (:=x (first (:=layers %))) :bill-length-mm)
                       (= (:=y (first (:=layers %))) :bill-depth-mm))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (target :plotly)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :data)
                       (contains? % :layout)
                       (sequential? (:data %)))])

;; **What's different from :geom and :vl**:

;; 1. Hover tooltips showing exact x/y values
;; 2. Toolbar with zoom, pan, and download options
;; 3. Smooth animations and transitions
;; 4. Same ggplot2 theming (grey background, white grid)

;; ### 🧪 Example 22: Multi-Layer with Plotly

;; Inspect the spec:
(-> mtcars
    (mapping :wt :mpg)
    (=+ (scatter)
        (linear))
    (target :plotly)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (count (:=layers %)) 2)
                       (= (:=target %) :plotly))])

;; Render the plot:
(-> mtcars
    (mapping :wt :mpg)
    (=+ (scatter)
        (linear))
    (target :plotly)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :data)
                       (>= (count (:data %)) 2))])

;; **What happens here**:

;; 1. Scatter plot rendered as Plotly scatter trace
;; 2. Regression computed on JVM (our minimal delegation!)
;; 3. Both traces combined in single Plotly spec
;; 4. Interactive hover works for both layers

;; ### 🧪 Example 23: Color-Grouped Regression with Plotly

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (=+ (scatter)
        (linear))
    (target :plotly)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=color (first (:=layers %))) :species)
                       (= (:=target %) :plotly))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm {:color :species})
    (=+ (scatter)
        (linear))
    (target :plotly)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :data)
                       (>= (count (:data %)) 2))])

;; **What happens here**:

;; 1. Three scatter traces (one per species) with ggplot2 colors
;; 2. Three regression lines (computed per-group on JVM)
;; 3. Matching colors for scatter points and regression lines
;; 4. Interactive legend - click to show/hide species
;; 5. Demonstrates full composability with color aesthetics

;; **Simple Histogram with Plotly**:

(-> penguins
    (mapping :bill-length-mm nil)
    (histogram)
    (target :plotly)
    (size 500 400)
    plot)

;; **What happens here**:

;; 1. Histogram computed on JVM using Sturges' rule
;; 2. Pre-computed bins sent to Plotly as bar trace
;; 3. White bar borders (ggplot2 theme)
;; 4. Hover shows bin range and count

;; **Faceted Histogram with Custom Bins (Plotly)**:

(-> penguins
    (mapping :bill-length-mm nil)
    (histogram {:bins 12})
    (facet {:col :species})
    (target :plotly)
    (size 900 350)
    plot)

;; **What happens here**:

;; 1. Per-species histograms (computed on JVM)
;; 2. Faceted layout (3 columns)
;; 3. Shared y-axis for easy comparison
;; 4. Custom bin count (12 bins)
;; 5. Full interactivity with hover tooltips

;; ### 🧪 Example 24: Faceted Scatter with Plotly

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (facet {:col :species})
    (target :plotly)
    (size 800 400)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (:=target %) :plotly)
                       (= (:=col (first (:=layers %))) :species)
                       (= (:=width %) 800)
                       (= (:=height %) 400))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (facet {:col :species})
    (target :plotly)
    (size 800 400)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :data)
                       (contains? % :layout)
                       (sequential? (:data %))
                       (> (count (:data %)) 0))])

;; **What happens here**:

;; 1. Data split by species (3 facets)
;; 2. Each facet rendered in separate subplot
;; 3. Shared axes for easy comparison
;; 4. Species names as column headers
;; 5. Independent zoom/pan for each subplot

;; ### 🧪 Example 26: Custom Domains with Plotly

;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (scale :x {:domain [30 65]})
    (scale :y {:domain [10 25]})
    (target :plotly)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (contains? % :=layers)
                       (= (get-in % [:=scale-x :domain]) [30 65])
                       (= (:=target %) :plotly))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (scale :x {:domain [30 65]})
    (scale :y {:domain [10 25]})
    (target :plotly)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :layout)
                       (get-in % [:layout :xaxis :range]))])

;; **What happens here**:

;; 1. Custom domain constraints respected
;; 2. Zoom/pan constrained to specified ranges
;; 3. Same composition semantics across all targets

;; ## 🧪 Compositional Size Specification

;; Width and height can be specified compositionally using the `size` constructor,
;; just like `target`. This enables full threading and keeps plot dimensions as
;; part of the layer specification.

;; **Using the `size` constructor**:
;; Inspect the spec:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (facet {:row :island :col :sex})
    (target :vl)
    (size 800 600)
    kind/pprint)

(kind/test-last [#(and (map? %)
                       (= (:=width %) 800)
                       (= (:=height %) 600)
                       (= (:=target %) :vl))])

;; Render the plot:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (facet {:row :island :col :sex})
    (target :vl)
    (size 800 600)
    plot)

(kind/test-last [#(and (map? %)
                       (contains? % :facet)
                       (contains? % :spec))])

;; **What happens here**:

;; 1. `size` adds `:=width` and `:=height` to the plot spec (plot-level properties)
;; 2. `plot-impl` methods check spec first, then opts, then defaults
;; 3. Priority: `:=width` > `:width opts` > `(:plot-width theme)`
;; 4. Fully compositional - size is part of the plot spec, not external config

;; Size arguments can also be passed directly to the `plot` function:
(-> penguins
    (mapping :bill-length-mm :bill-depth-mm)
    (scatter)
    (facet {:row :island :col :sex})
    (target :vl)
    (plot {:width 800 :height 600}))

;; Both approaches work. The `size` constructor enables full threading and
;; treats dimensions as compositional layer properties.

;; # Validation Examples
;;
;; The API validates plot specs and layers automatically, so you typically don't
;; need to call validation functions directly. This section shows how validation
;; works and provides examples for debugging when something goes wrong. These
;; are advanced examples - feel free to skip if you just want to create plots.
;;
;; The Malli schemas enable validation at two points:
;; - Construction time
;; - Draw time

;; ## 🧪 Example 1: Valid Layer

;; A properly constructed layer passes validation silently
(validate Layer
          {:=data {:x [1 2 3] :y [4 5 6]}
           :=x :x
           :=y :y
           :=plottype :scatter
           :=alpha 0.7})

;; ## 🧪 Example 2: Invalid Alpha (Out of Range)

;; Alpha must be between 0.0 and 1.0
(validate Layer
          {:=data {:x [1 2 3]}
           :=plottype :scatter
           :=alpha 1.5})

;; ## 🧪 Example 3: Invalid Plot Type

;; Plot type must be one of the defined enums
(validate Layer
          {:=data {:x [1 2 3]}
           :=plottype :invalid-type})

;; ## 🧪 Example 4: Missing Required Aesthetics

;; Scatter plots require both x and y
(validate-layer
 {:=data {:x [1 2 3] :y [4 5 6]}
  :=x :x
  ;; Missing :=y!
  :=plottype :scatter})

;; ## 🧪 Example 5: Missing Column in Dataset

;; Column references must exist in the data
(validate-layer
 {:=data {:x [1 2 3]}
  :=x :x
  :=y :y ;; y column doesn't exist!
  :=plottype :scatter})

;; ## 🧪 Example 6: Programmatic Validation

;; Check validity without throwing
(valid? Layer {:=data {:x [1 2 3]} :=plottype :scatter})

(valid? Layer {:=plottype :invalid})

;; Get detailed error information
(validate Layer {:=plottype :invalid :=alpha 2.0})

;; # Design Discussions
;;
;; This section dives deeper into specific design decisions and their rationale.
;; If you're curious about why certain implementation choices were made, or want
;; to understand the tradeoffs considered, read on. Otherwise, feel free to skip
;; to the [Conclusion](#conclusion).

;; ## 📖 Map-Based IR: Separating Layers from Plot Configuration
;;
;; ### The Design Choice
;;
;; The internal representation uses a map structure with explicit separation:
;;
;; ```clojure
;; {:=layers [{:=data penguins 
;;             :=x :bill-length-mm 
;;             :=y :bill-depth-mm 
;;             :=plottype :scatter}]
;;  :=target :vl
;;  :=width 800
;;  :=height 600}
;; ```
;;
;; ### Why This Structure?
;;
;; **Clean separation of concerns:**
;; - **Layer properties** (`:=data`, `:=x`, `:=y`, `:=plottype`) describe what to visualize
;; - **Plot properties** (`:=target`, `:=width`, `:=height`, scales) describe how to render
;;
;; **Benefits:**

;; 1. **Inspectable** - `kind/pprint` shows clear structure
;; 2. **No duplication** - Plot config appears once, not in every layer
;; 3. **Composable** - `=*` and `=+` can merge both levels correctly
;; 4. **Extensible** - Easy to add new plot-level properties (themes, titles, etc.)
;;
;; ### How Operators Handle It
;;
;; **`=*` (merge):**
;; - Performs cross-product on `:=layers` vectors
;; - Merges plot-level properties (right side wins)
;;
;; **`=+` (overlay):**
;; - Concatenates `:=layers` vectors
;; - Merges plot-level properties (right side wins)
;;
;; **Constructors:**
;; - Layer constructors (`data`, `mapping`, `scatter`) return `{:=layers [...]}`
;; - Plot constructors (`target`, `size`, `scale`) return `{:=property value}`
;; - Both compose naturally via `=*`
;;
;; This design balances compositional elegance with practical clarity.

;; ### 📖 Multimethod Cartesian Expansion

;; The rendering system uses multimethod dispatch on `[target plottype]`:
;; ```clojure
;; (defmulti render-layer
;;   (fn [target layer transform-result alpha]
;;     [target (or (:=transformation layer) (:=plottype layer))]))
;; ```

;; With 3 targets (`:geom`, `:vl`, `:plotly`) and growing plottypes
;; (`:scatter`, `:line`, `:histogram`, `:linear`, ...),
;; this creates a cartesian product of methods.

;; **The Tension:**

;; **Benefits of cartesian dispatch:**
;; - Full control per combination - optimize each target/plottype pair
;; - Simple dispatch logic - just a vector
;; - Clear separation of concerns

;; **Costs of cartesian dispatch:**
;; - Method explosion: N targets × M plottypes = N×M methods
;; - Code repetition: Similar logic across targets (e.g., all targets render scatter similarly)
;; - Maintenance burden: Adding a new plottype requires implementing it for all targets
;; - Scalability concern: Currently ~10 methods, could reach 50+ as plottypes grow
;;   (box, violin, density, contour, area, ribbon, etc.)

;; **Alternatives to consider:**

;; 1. Two-level dispatch: Dispatch on target first, then on plottype within target-specific methods
;; 2. Shared abstractions: Default method that delegates to target-specific renderers
;; 3. Hierarchical dispatch: Use Clojure's derive/isa? to share implementations across targets
;; 4. Spec-based rendering: Translate to intermediate spec format, each target consumes specs

;; This design choice affects the library's extensibility and maintainability as
;; the number of supported plot types and rendering targets grows.

;; ### 📖 Key Naming Convention

;; Every layer and plot property uses the `:=` prefix:
;; ```clojure
;; {:=data penguins
;;  :=x :bill-length-mm
;;  :=y :bill-depth-mm
;;  :=color :species
;;  :=plottype :scatter}
;; ```

;; **Why the `:=` prefix?**

;; We need to distinguish between layer specification keys and data column names.
;; Without a distinctive prefix, `:color` could mean either "the color aesthetic
;; mapping" (layer spec) or "a column named color in the dataset" (data).

;; **Why `:=` specifically?**

;; 1. **Prevents collisions** - `:=color` (layer spec) is clearly distinct from `:color` (data column)
;; 2. **Visually distinctive** - The `=` character stands out, making specs easy to identify
;; 3. **Semantic connection** - The `=` suggests "assignment" or "setting" a property
;; 4. **Consistency** - Matches the convention used in other Tableplot APIs
;; 5. **Compact** - Shorter than namespace qualifiers like `:aog/color`

;; **Alternative considered:**
;; We initially considered namespaced keywords (`:aog/color`, `:aog/x`) but found
;; them too verbose for something that appears so frequently in plotting code.
;; The `:=` prefix provides the same collision protection with less visual weight.

;; ### 📖 Validation Timing

;; The API validates plot specifications at two points:

;; 1. **Construction time** - when calling constructors like `scatter`, `linear`, `mapping`
;; 2. **Draw time** - when calling `plot` to render the visualization

;; **Why validate at both points?**

;; **Construction-time validation:**
;; - **Fail fast** - errors caught immediately where they're created
;; - **Better error context** - stacktraces point to the exact construction site
;; - **Immediate feedback** - catch typos and invalid values right away
;; - Example: `(scatter {:alpha 2.0})` fails immediately (alpha must be 0.0-1.0)

;; **Draw-time validation:**
;; - **Final safety check** - ensures composed specs are valid before rendering
;; - **Catches composition errors** - validates the complete plot structure
;; - **Cross-layer validation** - checks that data columns exist, layers are compatible
;; - Example: Validates that `:=x :missing-column` references an actual column

;; **Benefits of dual validation:**
;; - Early errors are caught at construction (better DX)
;; - Late errors from composition are caught before rendering (safety net)
;; - Clear, actionable error messages at both stages

;; This approach prioritizes developer experience - validation is always on,
;; errors are caught as early as possible, and the system never renders invalid plots.

;; ### 📖 Type Inference Strategy

;; The API needs to determine column types to support type-aware grouping (categorical
;; aesthetics create groups, continuous ones don't). Rather than implementing custom
;; type inference, we delegate to Tablecloth.

;; **Implementation approach:**

;; ```clojure
;; (try
;;   ;; Primary: Get type from Tablecloth metadata
;;   (tcc/typeof (get dataset col-key))
;;   (catch Exception _
;;     ;; Fallback: Simple inference for plain Clojure data
;;     (infer-from-values (get dataset col-key))))
;; ```

;; **How it works:**

;; 1. **Tablecloth datasets (primary path)** - Use `tcc/typeof` to get precise type information
;;    - Returns types like `:float64`, `:string`, `:int32`, etc.
;;    - Free, accurate, no guessing required
;;    - This is why we convert plain data to datasets via `ensure-dataset`

;; 2. **Plain data (fallback path)** - Simple heuristic when Tablecloth isn't available
;;    - Numbers → `:continuous`
;;    - Temporal values → `:temporal`
;;    - Everything else → `:categorical`
;;    - Only used as a safety fallback, not the primary path

;; **Benefits of this approach:**

;; - **Leverages existing infrastructure** - Tablecloth already solves this well
;; - **No duplicate logic** - We don't reimplement type inference
;; - **User control** - Users who need precise types can construct typed datasets
;; - **Simple fallback** - Plain data still works, just with simple inference
;; - **Consistent behavior** - Same type system as the rest of Tablecloth ecosystem

;; ### 📖 Faceting Implementation Complexity

;; The current faceting implementation uses multiple helper functions with manual
;; grid organization:
;; - `split-by-facets` - splits layers by facet values
;; - `has-faceting?` - checks if any layer has facets
;; - `organize-by-facets` - creates the grid structure

;; **The Tension:**

;; The faceting code feels heavier and more imperative than the rest of the algebra:
;; ```clojure
;; (defn- organize-by-facets [layers-vec]
;;   ;; Complex nested logic to create facet grid
;;   ...)
;; ```

;; **Current approach (manual grid organization):**
;; - Full control over layout and rendering
;; - Can optimize for specific cases
;; - BUT: Complex code with nested loops
;; - BUT: Hard to extend (e.g., free scales, different facet layouts)
;; - BUT: Doesn't match the compositional spirit of `=*` and `=+`

;; **Question:** Is there a more compositional approach?
;; - Could transducers simplify the faceting operations?
;; - Can faceting be expressed more declaratively?
;; - Should faceting be part of the algebra rather than a special case?

;; The challenge: faceting is inherently about spatial layout (a grid), which is
;; different from the layer composition that `=*` and `=+` handle. Perhaps some
;; imperative complexity is unavoidable, or perhaps a different abstraction could
;; make it feel more algebraic.

;; ### 📖 Error Message Enhancement

;; The validation system provides structured error messages via Malli schemas and
;; custom validation helpers. Several enhancements have already been implemented:

;; **✅ Implemented enhancements:**

;; 1. **Plottype-specific requirements** - The Layer schema uses `:multi` dispatch to
;;    enforce different requirements per plot type:
;;    - Scatter/line/area require both `:=x` and `:=y`
;;    - Histogram/bar require `:=x` (`:=y` optional)
;;    - Missing required aesthetics produce clear Malli errors

;; 2. **Missing column detection** - `validate-layer` checks that aesthetic column
;;    references exist in the dataset:
;;    ```clojure
;;    "Columns not found in dataset: [:missing-col]
;;     Available columns: [:bill-length-mm :bill-depth-mm :species]"
;;    ```

;; 3. **Did-you-mean suggestions** - `validate-column!` suggests similar column names
;;    using substring matching and length similarity:
;;    ```clojure
;;    "Column :bill-lenght not found in dataset
;;     Did you mean one of: [:bill-length-mm]?"
;;    ```

;; 4. **Structured error data** - All errors include machine-readable `ex-data` for tooling

;; **🔮 Future enhancements to consider:**

;; 1. **Documentation links** - Point to relevant docs or examples
;;    ```clojure
;;    "Missing required aesthetic :=x. See: (doc scatter)"
;;    ```

;; 3. **Example snippets** - Show valid examples when validation fails
;;    ```clojure
;;    "Try: (scatter {:=x :col1 :=y :col2})"
;;    ```

;; The current error messages are functional and provide good context. Further
;; enhancements could improve the developer experience but aren't critical.

;; # Conclusion

;; ## 📖 What We've Explored
;;
;; This notebook demonstrates a composable graphics API with **minimal delegation**:
;;
;; **Core Design**:
;; - Layers as flat maps with `:=...` distinctive keys (see [Design Exploration](#design-exploration))
;; - Composition using `=*` (merge) and `=+` (overlay)
;; - Standard library operations work natively
;; - Backend-agnostic IR
;;
;; **Delegation Strategy**:
;; 1. ✅ **We compute**: Statistical transforms, domains (when needed), types (from Tablecloth)
;; 2. ❌ **We delegate**: Axis rendering, ranges, ticks, "nice numbers", layout
;;
;; **Key Wins**:
;; - Type information from Tablecloth (free!)
;; - Domain computation only for statistical transforms
;; - Leverage rendering target polish for rendering
;; - Simple, focused implementation

;; ## 📖 Where We Are

;; So that's the exploration. We set out to see if AlgebraOfGraphics.jl's compositional
;; approach could work in Clojure using plain maps and standard operations. The flat
;; structure with distinctive `:=` keys (detailed earlier) turned out well—standard `merge`
;; just works, everything stays inspectable, and the threading macros feel natural. Getting
;; type information from Tablecloth for free was a nice bonus that eliminated a lot of
;; complexity.

;; The minimal delegation strategy (we compute transforms, rendering targets handle
;; layout) seems about right. We need domains before we can bin histograms anyway,
;; so might as well compute them. Everything else—axis formatting, tick placement,
;; "nice numbers"—the existing libraries already do better than we would.

;; There are rough edges. The threading-macro polymorphism (detecting whether the first
;; arg is layers or data) adds some complexity. Faceting works but doesn't feel as
;; algebraically elegant as `=*` and `=+`. Error messages could be much better. And we
;; haven't tested with large datasets yet. But nothing here feels like a fundamental
;; blocker—more like things to improve as we learn from usage.

;; ## 📖 What's Missing

;; This is a prototype, not a complete plotting library. We've got scatter, line,
;; histogram, linear regression, and faceting working across three rendering targets.
;; That's enough to explore the design, but Tableplot's existing APIs handle many more
;; plot types, aesthetics, transforms, and coordinate systems. Those aren't abandoned,
;; just deferred until we know if this approach is worth pursuing.

;; ## 📖 What Comes Next

;; Honestly, we're not sure yet. This could become a new namespace in Tableplot,
;; or it could just inform improvements to the existing APIs, or it could stay as
;; a design study. The goal was to explore whether a compositional approach could
;; work with plain Clojure data structures—and it seems like it can. What we do
;; with that depends on what we hear from people who try it.

;; If you've read this far and have thoughts—especially if you use Tableplot or build
;; visualizations regularly—we'd be interested to hear them. Try the code, see what
;; feels right or awkward, share what's missing for your use cases. This is happening
;; in the [Real-World Data dev group](https://scicloj.github.io/docs/community/groups/real-world-data/)
;; context, so feedback actually shapes what gets built.

;; Thanks to the AlgebraOfGraphics.jl folks for the inspiration, to Tableplot users
;; for the feedback that motivated this exploration, and to the Real-World Data group
;; for the space to try new ideas.

;; ---
