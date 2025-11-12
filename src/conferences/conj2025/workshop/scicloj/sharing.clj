^{:kindly/hide-code true
  :clay             {:title  "Conj 2025 Workshop: Sharing your Data Analysis"
                     :quarto {:author      :timothypratley
                              :theme       "moon"
                              :description "Clay, making sharable documents, and how to publish them."
                              :type        :post
                              :date        "2025-11-12"
                              :category    :clay
                              :tags        [:clay :workflow]}}}
(ns conferences.conj2025.workshop.scicloj.sharing
  (:require [scicloj.tableplot.v1.plotly :as tp]
            [tablecloth.api :as tc]))

(ns conferences.conj2025.workshop.scicloj.sharing)

;; Clay
;;
;; 1. Data visualization
;; 2. Publishing

;; ::: {.notes}
;; Hello!

;; You've already been using Clay for data visualization
;; while doing data analysis.
;; Now we'll look closer into how to share the output that Clay produces.
;; :::

;; ## What's next

;; - How to share Clay notebooks
;; - Where Clay writes output
;; - Configuring Clay
;; - Simple hosting approaches (GitHub Pages + Actions)
;; - Other formats: PDFs, slides, books, websites
;; - An invitation to ClojureCivitas

;; ::: {.notes}
;; Please interrupt at anytime with questions or suggestions
;; if there are other topics you'd like to go deeper on.
;; :::

;; ## Why share your data analysis?

;; - Reports
;; - Decisions
;; - Insights

;; ::: {.notes}
;; It might be your job to send someone in your company a report,
;; or explain a decision.
;; Precise, technical, convincing communication.
;; With charts.
;; :::

;; ## Why with Clay?

;; - Reproducible
;; - Extensible
;; - Fun
;; - **Sharable**

;; ::: {.notes}
;; Writing code that makes documents is reproducible, extensible, and fun.
;; Clay is simple, it keeps out of your way, and makes something that can be shared.
;; Clay is a great way to build a report or explain a decision.
;; :::

;; ## Why share ideas?

;; - Write to think
;; - Be visible
;; - Ideas build civilization

;; ::: {.notes}
;; More broadly I want to encourage you to share your ideas publicly.
;; Writing makes your thinking visible.
;; Sharing your ideas is important.
;; That’s how we make progress.
;; It’s a prerequisite for survival.
;; It’s the foundation of human progress.
;; It fuels technology, communities, and civilization.
;; And sharing ideas with code is especially powerful.

;; My favourite way to share an idea is to write a blog post.
;; Of course that's just one use case,
;; and we'll be doing a deep dive today on many different ways you can share Clay output
;; like PDF reports, intranets, and slideshows,
;; and how you might host a website built with Clay.
;; :::

;; ## ClojureCivitas

;; - https://clojurecivitas.github.io
;; - Clone the repo
;; - Open the project
;; - Find `src/conferences/conj2025/workshop/scicloj/sharing.clj`
;; - run a REPL

;; ::: {.notes}
;; We'll also take a look at ClojureCivitas which is a shared blog space
;; for Clay notebooks like this one,
;; and the ones you have been experimenting with today.
;; Anyone can add a namespace to ClojureCivitas,
;; and it gets published as a blog article.
;; It's an easy way to share your notebook publicly.

;; If you would like to follow along,
;; trying the code and examples as we go,
;; then please navigate to the ClojureCivitas github page,
;; and clone the `clojurecivitas.github.io` project.
;; Open this file: `src/conferences/conj2025/workshop/scicloj/sharing.clj`.

;; Of course trying ClojureCivitas is optional,
;; you can try everything we discuss in your own project if you prefer.

;; Please ask for help if you get stuck, have any questions,
;; or need some time to catch up.
;; Anyone cloning the repo who would like me to wait?
;; :::

;; ## HTML output

;; Clay is a tool that turns a Clojure namespace into a Document.

;; ![Clay makes code into a document](/scicloj/clay/workshop/macroexpand2025.png)

;; `docs` or `temp`

;; ::: {.notes}
;; We've been rendering the code to HTML,
;; and Clay has been serving it on a localhost HTTP server from port 1971.
;; Now we want to share it with someone else.

;; Either we are going to email them something or host a server they can access.
;; We'll cover both in detail.

;; The first question is: where does Clay write the generated HTML?
;; By default, Clay targets `"docs"`, which matches GitHub Pages' "publish from /docs" option.
;; Committing `docs` to the repository is the simplest way to publish, but it mixes generated files into source control.
;; An alternative workflow that keeps your repo tidy is:
;; - Use `:base-target-path "temp"` (in `clay.edn` or your user config) for local builds
;; - Add a CI step that runs Clay and publishes the generated site artifact to GitHub Pages
;; This approach keeps generated files out of source control and gives you reproducible, CI-driven publishing.

;; - Commit `docs`: simple, manual control, but noisy history
;; - CI -> publish: cleaner repo, automatic deploys, slightly more setup
;; :::

;; ## Configuration review

(comment
  (scicloj.clay.v2.make/make!
    {:single-form "(+ 1 2)"
     :base-target-path "temp"}))

;; `clay.edn`
;;
;; ```
;; {:base-target-path "temp"}
;; ```

;; `~/.config/scicloj-clay/config.edn`

;; ```
;; {:base-target-path "temp"
;;  :browse :browser}
;; ```

;; ::: {.notes}
;; Speaking of which now would be a good time to talk a bit more about configuration.

;; Configuration can be passed as an argument of `make!` from the REPL.

;; Project level configuration is placed in a file `clay.edn` at the root of the project.
;; This is usually where you want configuration to go.
;; It will be merged with any argument configuration.
;; When you use the REPL commands like "Clay Make File",
;; this configuration will be merged in and cause Clay to target `temp`.

;; User level configuration is placed in a file `~/.config/scicloj-clay/config.edn`

;; Configuration is merged in order from user, then project, then arguments.
;; So this means for me Clay will always use a browser, and always target `temp`,
;; unless the project overrides it, or I pass something different as an argument to `make!`.

;; All of that just to say that the output of running Clay is probably going to one of `docs` or `temp`,
;; depending on what is in `clay.edn`.

;; OK — now is a good moment to look for an HTML file in the `docs` directory.
;; If you are lucky it will open fine,
;; but there's a good chance that some part of the page is not quite right.
;; A missing image or visualization.
;; :::

;; ## Problem

;; * The `mynamespace.html` page refers to other files
;; * Stylesheets, JavaScript, Images
;; * Absolute/relative URLs
;; * **Local JavaScript is blocked**

;; ::: {.notes}
;; The HTML that Clay produces is intended to be served from a host.
;; Great for hosting, but not so great for zipping up and emailing.
;; Opening a HTML file is not the same as viewing it on a server.

;; Feature request: A flag to embed everything as a fully self-contained HTML page.
;; Speak up about this in the `clay-dev` channel on Zulip if this is a feature you need,
;; it will encourage the devs to add it.

;; Workaround: Save the page to PDF.
;; Works pretty well, but not quite as nice as native PDF which we'll cover soon.
;; :::

;; ## Hosting HTML

;; Upload the contents of `docs` or `temp` to the host.

;; ::: {.notes}
;; If you plan to serve your HTML on a public or internal server then things are more straightforward.
;; This is the easy path.
;; Upload the contents of `docs` or `temp` to the host.
;; So long as the files make it onto the server, you should be good to go.
;; That's all there is to it.

;; Clay has a `clean` option which will remove other files in the target directory.
;; The CLI is a convenient way to make all files.
;; See also snippets.make-all
;; :::

;; ## Hosting on GitHub Pages

;; 1. Publish anything in `/docs`
;; 2. Publish a branch of your repository `ghpages`
;; 3. Publish with a GitHub Action

;; ::: {.notes}
;; There are of course many hosting options, and we will look at one in more detail.
;; GitHub Pages.
;; GitHub Pages publishing can be configured in project settings.
;; To host files on GitHub Pages you have three options to choose from:

;; The first two options are appealing because there is a manual step to "push" a version to the site.
;; If you want to try those, just update the settings and push to the repo.

;; The recommended way is a GitHub Action (3) because it automates the process and avoids source control churn.

;; Let's look at an example publishing flow (high level):
;; :::

;; ## Actions

;; 1) CI checks out the repository
;; 2) CI runs `clojure -M:clay -A:markdown` (or your chosen alias) to generate site files
;; 3) CI uploads the generated site and triggers GitHub Pages deployment

;; ::: {.notes}
;; The repository includes the workflow file `.github/workflows/render-and-publish.yml` which shows one concrete implementation.

;; Any questions?
;; :::

;; ## Formats

;; ```
;; {:format [:quarto :pdf]}
;; ```

;; ::: {.notes}
;; Clay can produce different formats.
;; Books, PDFs, websites, and slideshows.
;; PDFs in particular are great for emailing out a report.
;; We request the format with configuration: `:format [:quarto :pdf]`
;; Here are some formats to try:
;; :::

;; ## Try these out

;; * `[:html]`
;; * `[:gfm]`
;; * `[:quarto :html]`
;; * `[:quarto :revealjs]`
;; * `[:quarto :pdf]`

(comment
  (require 'scicloj.clay.v2.make)
  (scicloj.clay.v2.make/make!
    {:source-path "src/conferences/conj2025/workshop/scicloj/sharing.clj"
     :format [:html]})
  (scicloj.clay.v2.make/make!
    {:source-path "src/conferences/conj2025/workshop/scicloj/sharing.clj"
     :format [:gfm]})
  (scicloj.clay.v2.make/make!
    {:source-path "src/conferences/conj2025/workshop/scicloj/sharing.clj"
     :format [:quarto :html]})
  (scicloj.clay.v2.make/make!
    {:source-path "src/conferences/conj2025/workshop/scicloj/sharing.clj"
     :format [:quarto :revealjs]})
  (scicloj.clay.v2.make/make!
    {:source-path "src/conferences/conj2025/workshop/scicloj/sharing.clj"
     :format [:quarto :pdf]}))

;; ## REPL commands

;; Open the command palette and search for Clay.

;; ::: {.notes}
;; There are REPL commands for most, but not all of these formats.
;; If you use the command palette you should be able to find "Clay Make RevealJS" for example, but not "PDF".

;; Notice that Clay can produce Markdown, which is often the best route for publishing.
;; Markdown can be transformed by many other tools.

;; **Important:** It's not just the markdown from your comments,
;; it includes the results and visualizations as well.
;; :::

;; ## Quarto?

;; Many of those formats only work if you have Quarto installed.

;; ::: {.callout-tip}
;; Notice how `[:quarto :html]` renders this tip differently than `[:html]`.
;; this comment uses a Quarto specific markdown feature for callouts.
;; :::

;; ::: {.notes}
;; Clay integrates with Quarto,
;; which is a fully featured markdown publishing system based on Pandoc.
;; It’s quite popular in the scientific community.
;; Quarto has many authoring features like margin notes, callout blocks, and themes.
;; It is well documented, widely used and robust.

;; The way it works is that Clay makes markdown for Quarto to make into the final format.

;; I recommend using Quarto as it gives you a many powerful and easy to access authoring and publishing features.
;; :::

;; ## What can Quarto do?

;; Mostly similar to CommonMark, but with extra features like footnotes^[Quarto is a well established, widely used publishing solution].
;; See [Markdown reference](https://quarto.org/docs/authoring/markdown-basics.html) for the full feature set.
;; Themes, layouts, callouts, lightbox images, even discussion forums.

;; ## Why use Clay?

;; Clay is a simple approach that keeps out of your way.

;; |  | Notebooks | Clay |
;; |--|--|--|
;; | Visualizing | React application | HTML+JavaScript |
;; | Coding | Custom execution model | REPL |
;; | Publishing | Notebook | Markdown |
;; : Clay is _simple_

;; **Clay composes well with publishing via Quarto**

;; ::: {.notes}
;; I find this to be a very compelling way to share ideas.

;; Any questions?
;; Want to see a feature demonstrated?
;; Questions about configuration, Quarto, Markdown, formats, narrowing?
;; :::

;; ## ClojureCivitas

;; Shared blog space.

;; ClojureCivitas is a space for you to add a Clay notebook.

;; Maybe a data analysis, or anything you like.

;; https://clojurecivitas.github.io

;; ::: {.notes}
;; Let's have a look at some of the articles there.
;; As you can see there's a wide range of topics.

;; Let's take a look at the README to see how it works.

;; 30 authors have posted 70 articles there so far.

;; You can copy this approach to make your own site.
;; It's a working template, the only things that matter are `clay.edn` and `site/_quarto.yml`.
;; Let's look into those a bit.

;; I invite you to share something on ClojureCivitas that you built here today.
;; :::

;; ## Ideas for explorations

;; We could tidy up some of the existing experiments created today,
;; or code something new up right now.

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

;; ### Regression analysis?

;; ## Wrapping up

;; Feature suggestions?

;; Share your experiences?

;; Remaining questions can be asked in the [#clay-dev channel](https://clojurians.zulipchat.com/#narrow/channel/422115-clay-dev).

;; ## Scicloj

;; - Community
;; - Support system
;; - Zulip

;; ::: {.notes}
;; Scicloj is an open-source community with the goal of building the Clojure ecosystem,
;; exploring the relevance of Clojure in fields like education, science, and art.
;; Part of that is through developing tools, libraries, and learning resources.
;; The other part is through the meetings and mentorship to enable people to use Clojure effectively for their work.

;; Scicloj is a support system.
;; A community of people who share your interests.
;; People who care about similar problems,
;; with a practice of working together, being visible, trying things out in public, and exploring new domains.
;; Wherever you are going, the Scicloj support system can help you.
;; They hold regular meetups, working groups, and study groups.

;; Most of the discussion happens on Zulip.
;; Please reach out and connect.
;; :::
