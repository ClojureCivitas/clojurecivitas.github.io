^{:kindly/hide-code true
  :clay             {:title  "Macroexpand 2025 Noj: Clay Workshop"
                     :quarto {:author      :timothypratley
                              :description "What is Clay, why use it, and how to use it."
                              :type        :post
                              :date        "2025-10-16"
                              :category    :clay
                              :tags        [:clay :workflow]}}}
(ns scicloj.clay.workshop.macroexpand2025
  (:require [scicloj.tableplot.v1.plotly :as tp]
            [tablecloth.api :as tc]
            [scicloj.kindly.v4.kind :as kind]))

(ns scicloj.clay.workshop.macroexpand2025)

;; Welcome to the [Macroexpand Clay Workshop](https://scicloj.github.io/macroexpand-2025/macroexpand_noj.html)!

;; The main goal of this workshop is to get you up and running with Clay.
;; I'll present an overview of Clay, and then we'll spend time together getting setup and trying it.
;; Please ask for help if you are stuck or have any questions.

;; ## What is Clay?

;; Clay is a tool that turns a Clojure namespace into a Document.

;; Clay renders code, results, comments, markdown and visualizations.

;; ![Clay makes code into a document](macroexpand2025.png)

;; > Useful for documentation, data exploration, visualization, and blogging.

;; > Bundled with Noj; the SciCloj collection of data science libraries.

;; ## What does Clay produce?

;; HTML or Markdown of comments, code, and values.
;; This HTML document was made from `src/scicloj/clay/workshop/macroexpand2025.clj`.

;; ### Code and results

;; Expressions are evaluated and the result shown in the document.

(reduce * [2 3 4 5])

;; ### Comments

;; The narrative of the document is written as comments.

;; Comments may contain Markdown.

;; ```
;; ;; This is a _markdown_ comment.
;; ```

;; This is a _markdown_ comment.

;; ```
;; ;; > Euler's identity is $e^{i\pi} + 1 = 0$
;; ```

;; > Euler's identity is $e^{i\pi} + 1 = 0$.

;; ```
;; ;; ![hammer and spanner](macroexpand2025.svg)
;; ```

;; ![hammer and spanner](macroexpand2025.svg)

;; CommonMark (Quarto).
;; Quarto has features like footnotes^[Quarto is a well established, widely used publishing solution].
;; See [Markdown reference](https://quarto.org/docs/authoring/markdown-basics.html) for the full feature set.

;; ### Visualizations

;; Values may be annotated as visualizations.
;; Clay can make tables, charts, and more.

^:kind/table
{:types   ["Table" "Chart" "Image" "JavaScript"]
 :purpose ["Summarize data"
           "Enable comparison"
           "Enhance appeal"
           "Add interactivity"]}

^:kind/echarts
{:title   {:text "Charts"}
 :tooltip {}
 :legend  {:data ["sales"]}
 :xAxis   {:data ["Shirts", "Cardigans", "Chiffons",
                  "Pants", "Heels", "Socks"]}
 :yAxis   {}
 :series  [{:name "sales"
            :type "bar"
            :data [5 20 36
                   10 10 20]}]}

^:kind/graphviz
["digraph D {
  A [shape=diamond]
  B [shape=box]
  C [shape=circle]

  A -> B [style=dashed, color=grey]
  A -> C [color=\"black:invis:black\"]
  A -> D [penwidth=5, arrowhead=none]
}"]

;; Many more (33) visualizations are supported, see [Clay Examples](https://scicloj.github.io/clay/clay_book.examples.html).

;; Hiccup as the "swiss army knife" enables anything HTML can do:

^:kind/hiccup
[:svg {:width "100%"}
 [:circle {:r 40 :cx 50 :cy 50 :fill "lightblue"}]
 [:circle {:r 20 :cx 50 :cy 50 :fill "lightgreen"}]]

;; Anything you can do in a web page can be expressed as hiccup, such as including JavaScript in the page.

^:kind/hiccup
[:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.js"}]

;; `^:kind/hiccup` is Clojure syntax for attaching metadata.
;; Kindly is a standard for annotation, and helpers for attaching metadata annotations.

(kind/hiccup
  [:svg {:width "100%"}
   [:circle {:r 40 :cx 50 :cy 50 :fill "lightblue"}]
   [:circle {:r 20 :cx 50 :cy 50 :fill "lightgreen"}]])

;; Metadata on values for visualization tools like Clay to know what to do with the value.

;; ## So - Notebooks?

;; Yes, Clay interleaves code, results, narrative and visualizations.

;; > Similar to Jupyter, Clerk, etc.

;; But, there is no execution model or environment.

;; > You write plain Clojure code, you can run it without Clay.

;; Clay is not a dependency of your code.

;; > Write with your normal workflow, sometimes using Clay to visualize as you go.

;; Clay takes code as input and makes a document.

;; > Produces **HTML** or Markdown which may be further processed into PDF, slides, or websites.

;; ## Why make documents from code?

;; We value reproducible artifacts

;; | Premise | Reason |
;; |--|--|
;; | Sharing ideas is important | Foundation of human progress. Fuels technology, communities, and civilization. |
;; | Code crystallizes thinking | Concrete, logical, reproducible, explorable, and extensible. |
;; | Edit code not documents | Thinking and coding is the hard part. |
;; | Publishing matters | Share your idea in a widely accessible formats. |

;; ## Why use Clay?

;; Clay is a simple approach that keeps out of your way.

;; |  | Notebooks | Clay |
;; |--|--|--|
;; | Visualizing | React application | HTML+JavaScript |
;; | Coding | Custom execution model | REPL |
;; | Publishing | Notebook | Markdown |
;; : Clay is _simple_

;; People really enjoy the workflow and results.

;; > "Best notebook experience I've had"

;; ## How to Clay

;; To use Clay is to send it some code.
;; Usually we also want to view the document.
;; Clay has a built-in server to show HTML.

;; ### REPL

;; Clay needs to be on the classpath to invoke it.

;; <a href="https://clojars.org/org.scicloj/clay" data-original-href="https://clojars.org/org.scicloj/clay"><img src="https://img.shields.io/clojars/v/org.scicloj/clay.svg" class="img-fluid" alt="Clojars Project"></a>

(comment
  (require 'scicloj.clay.v2.make)
  (scicloj.clay.v2.make/make! {:source-path "src/scicloj/clay/workshop/macroexpand2025.clj"}))

;; ### Editor integrations

;; The recommended way to call Clay is via REPL commands.
;; Common operations are making the current namespace, or showing a single visualization.
;; These operations can be found in [`scicloj.clay.v2.snippets`](https://github.com/scicloj/clay/blob/main/src/scicloj/clay/v2/snippets.clj).
;; The important ones are `make-ns-html!` and `make-form-html!`.

;; There are plugins for Calva, Cider, Conjure, and Cursive.
;; [See Clay Setup](https://scicloj.github.io/clay/#setup).

;; When active, you should be able to find the commands in the command palette
;; by searching for "Clay".
;; I highly recommend creating a custom keybinding that works with your configuration.

;; A browser window will be shown when you call Clay.

;; ![Cursive and Calva have an embedded viewer](macroexpand2025-editor.png)

;; ### Anyone stuck?

;; If you are having difficulty getting started,
;; you might find it easier to clone an existing project:

;; * [noj-v2-getting-started](https://github.com/scicloj/noj-v2-getting-started)
;; * [Clojure Civitas](https://github.com/ClojureCivitas/clojurecivitas.github.io)

;; These projects have some extra configuration in `.vscode` and `.idea`.

;; ### Live reload

;; Clay can watch for file changes and re-render the document.
;; Use the "Clay watch" REPL command to toggle live reload mode.

;; Create or edit a source file and it will be shown in the browser.

;; Alternatively you can launch Clay in live reload mode with

;; ```sh
;; clojure -M -m scicloj.clay.v2.main
;; ```

;; Or call `watch!` from the REPL:

(comment
  (require 'scicloj.clay.v2.snippets)
  (scicloj.clay.v2.snippets/watch! {}))

;; ## Ideas for explorations

;; Take some time to create your own namespace and use Clay to render it.
;; Here are some tiny ideas you might write about.

;; ### What is macroexpand?

;; Macros expand code at compile time.
;; An example of a macro is `and`.
;; The expression `(and true false)` expands into an `if` expression.

(macroexpand '(and true false))

;; ### Frequencies

;; Clojure has a very nifty inbuilt `frequencies` function.

(def freq
  (frequencies "frequencies takes a sequence like this string,
              and returns a map"))

freq

;; ### Charts

;; Try [TablePlot](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html)

(def scatter-ds
  (tc/dataset {:x [1 2 3 4 5]
               :y [10 20 15 25 18]
               :z [1 2 1 2 1]}))

(-> scatter-ds
    (tp/base {:=title "Sample Scatter Plot"})
    (tp/layer-point {:=x :x
                     :=y :y}))

;; ## Question time

;; Please speak up!
;; Stuck?
;; Need help?
;; Want to see a feature demonstrated?
;; Questions about configuration, Quarto, Markdown, formats, narrowing?
;; Feature suggestions?
;; Share your experiences?

;; Remaining questions can be asked in the [#clay-dev channel](https://clojurians.zulipchat.com/#narrow/channel/422115-clay-dev).
