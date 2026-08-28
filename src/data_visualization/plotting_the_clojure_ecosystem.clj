^{:kindly/hide-code true
  :clay {:title "Plotting the Clojure Ecosystem with Plotje"
         :quarto {:type :post
                  :author [:daslu]
                  :draft true
                  :date "2026-08-28"
                  :description "An introduction to Plotje, a composable plotting library built on Membrane, using Dewey's index of Clojure repositories as the running example"
                  :category :data-visualization
                  :tags [:datavis :grammar-of-graphics :plotje :membrane :dewey]
                  :keywords [:datavis]
                  :toc true
                  :toc-depth 2}}}
(ns data-visualization.plotting-the-clojure-ecosystem)

;; [Plotje](https://github.com/scicloj/plotje) is a plotting library for
;; Clojure, in the grammar-of-graphics tradition. You describe a plot by
;; saying which column of your data becomes which visual property, and
;; which drawing recipes are used. The description is a Clojure map.
;; Plotje turns it into drawing primitives with
;; [Membrane](https://github.com/phronmophobic/membrane), and Membrane
;; renders those to a target of your choosing: SVG markup by default, or
;; a raster image that Java2D draws in the JVM.
;;
;; This post is a tour. It starts with six rows typed out by hand and
;; works up to the [Dewey](https://github.com/phronmophobic/dewey)
;; dataset, Adrian Smith's weekly index of Clojure repositories on
;; GitHub.
;;
;; Plotje is alpha, and getting close to beta. That makes this a good
;; moment to ask for help: the core vocabulary is settled, the edges still
;; move, and anything that turns out to be wrong is cheapest to change
;; before it stabilizes. Please try to break it, and tell us what you
;; find.

;; ## Thanks
;;
;; Plotje grew out of the Scicloj community, and specifically out of the
;; [Real-World Data dev group](https://scicloj.github.io/docs/community/groups/real-world-data/),
;; which Timothy Pratley reinitiated and which meets weekly. Most of the
;; design questions in this post were argued out there or in the
;; [#plotje](https://clojurians.zulipchat.com/#narrow/channel/610149-plotje)
;; stream on the [Clojurians Zulip](https://clojurians.zulipchat.com/).
;;
;; Particular thanks to Adrian Smith, whose
;; [Membrane](https://github.com/phronmophobic/membrane) does all the
;; drawing here and whose Dewey dataset is the subject of every plot
;; below.
;;
;; To Timothy Pratley, who has drafted explorations and
;; conceptualizations at several stages of the design, and who has filed
;; a good share of the reports behind recent releases.
;;
;; To generateme, for [Tablecloth](https://scicloj.github.io/tablecloth/),
;; [Fastmath](https://github.com/generateme/fastmath),
;; [Clojure2D](https://github.com/Clojure2D/clojure2d) and
;; [Wadogo](https://github.com/scicloj/wadogo) -- Plotje depends on all
;; four -- and for the earlier research around
;; [Cljplot](https://github.com/generateme/cljplot).
;;
;; To Chris Nuernberger and Harold Hausman, for
;; [tech.ml.dataset](https://techascent.github.io/tech.ml.dataset/) and
;; [dtype-next](https://github.com/cnuernber/dtype-next) -- the dataset
;; infrastructure underneath Tablecloth, and so underneath every plot
;; here.
;;
;; To Carsten Behring, who has raised many of the questions and bug
;; reports that shaped recent releases; and to Cvetomir Dimov, Kira Howe,
;; Jon Anthony, respatialized, Bruce Durling and Teodor Heggelund for
;; years of conversation about what plotting in Clojure should feel
;; like.
;;
;; And to [Clojure Civitas](https://clojurecivitas.org/), which calls
;; itself a shared scratch space for publishing Clojure ideas without the
;; overhead of setting up a blog: start with code, make it work, then tell
;; the story. Much of Plotje's design was worked out there in public,
;; before the library existed:
;;
;; - [Implementing the Algebra of Graphics in Clojure](https://clojurecivitas.org/data_visualization/aog_in_clojure_part1.html)
;;   (December 2025), which called itself a draft for a future library API.
;; - [Representing Graphics as a Tree of Bindings](https://clojurecivitas.org/data_visualization/aog/algebra_of_data.html)
;;   and
;;   [Plotting Datoms: Queries as Visual Mappings](https://clojurecivitas.org/data_visualization/aog/datomframes.html)
;;   (January 2026), by Timothy Pratley.
;; - [Building a SPLOM using geom.viz](https://clojurecivitas.org/data_visualization/splom_tutorial.html)
;;   (January 2026), building by hand what a plotting library would have to
;;   provide.
;; - [Visual data summaries](https://clojurecivitas.org/data_visualization/aog/column_combinations.html)
;;   (January 2026), also by Timothy Pratley.
;; - [Composable Plotting in Clojure](https://clojurecivitas.org/data_visualization/aog/composable_plotting.html)
;;   (March 2026), the prototype Plotje grew out of.
;;
;; This post is written the same way: a [Clay](https://scicloj.github.io/clay/)
;; notebook, where the code that made each picture is the code you read.

;; The libraries this post uses:

(ns data-visualization.plotting-the-clojure-ecosystem
  (:require
   ;; Clojure -- reading a gzipped EDN file
   [clojure.edn]
   [clojure.java.io :as io]
   ;; Tablecloth -- dataset and column manipulation
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   ;; dtype-next -- vectorized column operations
   [tech.v3.datatype.datetime :as dtype-dt]
   [tech.v3.datatype.functional :as dfn]
   ;; Kindly -- notebook annotation standard
   [scicloj.kindly.v4.kind :as kind]
   ;; Plotje -- composable plotting
   [scicloj.plotje.api :as pj]))

;; ## Starting small
;;
;; Before any real data, the smallest useful thing: six Clojure libraries
;; and how they are doing on GitHub, written as a plain vector of maps.

(def a-few-libraries
  [{:library "re-frame"  :stars 5541 :forks 711 :license "MIT"}
   {:library "reagent"   :stars 4884 :forks 411 :license "MIT"}
   {:library "babashka"  :stars 4586 :forks 274 :license "EPL-1.0"}
   {:library "ring"      :stars 3880 :forks 525 :license "MIT"}
   {:library "hiccup"    :stars 2854 :forks 177 :license "EPL-1.0"}
   {:library "clj-kondo" :stars 1848 :forks 305 :license "EPL-1.0"}])

;; That is the whole input. There is no conversion step and no wrapper
;; type to build first:

(-> a-few-libraries
    (pj/lay-bar :library :stars))

;; A map of column vectors would have done as well, and so would a vector
;; of row vectors. Plotje reads whichever shape the data is already in and
;; passes it through `tc/dataset` on the way, so everything downstream
;; works on a [Tablecloth](https://scicloj.github.io/tablecloth/) dataset.
;; [Datasets, and why they matter](#datasets-and-why-they-matter) comes
;; back to when that is worth caring about.
;;
;; The same six rows again, as points rather than bars, colored by
;; license and labelled by name:

(-> a-few-libraries
    (pj/pose :stars :forks {:color :license})
    pj/lay-point
    (pj/lay-label {:text :library :nudge-y 18}))

;; The axes took their ranges and their titles from the columns without
;; being asked, and the color mapping produced both a palette and a
;; legend. You say which column means what, and the rest is filled in.
;;
;; That is also the first sight of `pj/pose`. Plotje calls the description
;; of a plot a **pose**, and here it carries the mapping that both layers
;; share. [A plot is a value](#a-plot-is-a-value), below, takes one apart;
;; until then, a pose is simply the value these pipelines pass along.
;;
;; The star counts are real, by the way. They were read out of the
;; dataset this post uses throughout.

;; ## Running this yourself
;;
;; Those two plots were evaluated, not pasted in as images, and the same
;; goes for every plot below. It is worth saying early how that is done,
;; because it is the part of the workflow you will spend your time in.
;;
;; ### Clay, interactively
;;
;; The recommended way is [Clay](https://scicloj.github.io/clay/), a
;; notebook tool that watches your namespace and shows the value of
;; whatever you evaluate in a browser window beside your editor. You keep
;; your normal REPL workflow -- evaluate a form, look at the picture,
;; change the form -- and Clay renders datasets as tables, plots as
;; pictures, and comment lines as prose. This post is a Clay notebook:
;; every plot in it came out of evaluating the form printed above it.
;;
;; Nothing in Plotje requires Clay. A pose carries a
;; [Kindly](https://scicloj.github.io/kindly/) annotation -- Kindly is the
;; standard that Clay and the other Clojure notebook tools share -- so the
;; same namespace works in whichever of them you prefer.
;;
;; ### Saving a plot to a file
;;
;; Take the bar chart from above and give it a name:

(def libraries-chart
  (-> a-few-libraries
      (pj/lay-bar :library :stars)))

;; `pj/save` writes it to a file:
;;
;; ```clojure
;; (pj/save libraries-chart "libraries.svg")
;; (pj/save libraries-chart "libraries.png")
;; ```
;;
;; The extension picks the format.
;;
;; If you want the value rather than the file, `pj/plot` hands it back.
;; The default is SVG as [Hiccup](https://github.com/weavejester/hiccup),
;; which is to say a vector -- here are its first two elements, the tag
;; and the attribute map:

(-> libraries-chart
    pj/plot
    (subvec 0 2))

;; With `{:format :bufimg}` the same pose comes back as a
;; `java.awt.image.BufferedImage`, to put in a Swing window or hand to
;; another library:

(pj/plot libraries-chart {:format :bufimg})
;;
;; ### What workflows might look like later
;;
;; Adrian Smith is building [Easel](https://github.com/phronmophobic/easel),
;; an editor and workbench written in Clojure on top of Membrane. A
;; Plotje plot can be represented as a Membrane component (more on that
;; [below](#membrane-and-the-stages-in-between)), so it can go into a
;; Membrane user interface as one element among others, with no SVG
;; string and no browser in between. Adrian has shown a few promising
;; demos of Easel and Plotje together in Real-World Data meetings.
;;
;; It is early days. Rendering the same plot into a notebook page today
;; and into a live desktop tool tomorrow is where this is heading.

;; ## The data
;;
;; Dewey crawls GitHub for Clojure projects and publishes the result as a
;; [weekly release](https://github.com/phronmophobic/dewey/releases). One
;; of the files is every repository it knows about, as gzipped EDN. This
;; is Adrian's own snippet for reading it:

(defn read-edn-gz [in]
  (with-open [in (io/input-stream in)
              gz (java.util.zip.GZIPInputStream/new in)
              rdr (io/reader gz)
              pbr (java.io.PushbackReader. rdr)]
    (clojure.edn/read pbr)))

;; The two timestamps arrive as strings, and `:parser-fn` is where a
;; [Tablecloth](https://scicloj.github.io/tablecloth/) dataset is told
;; what they really are. Doing it at read time
;; rather than later matters for a reason the rest of the post keeps
;; coming back to: a column's type decides how its values are plotted.

(def all-repos
  (tc/dataset
   (read-edn-gz
    "https://github.com/phronmophobic/dewey/releases/download/2026-08-16/all-repos.edn.gz")
   {:parser-fn {:created_at [:local-date-time "yyyy-MM-dd'T'HH:mm:ss'Z'"]
                :pushed_at  [:local-date-time "yyyy-MM-dd'T'HH:mm:ss'Z'"]}}))

;; That is a Tablecloth dataset now. `tc/shape` gives its row and column
;; counts:

(tc/shape all-repos)

;; The columns are more of GitHub's API than this post needs, and one, the
;; license, arrives as a nested map. So the next step is an ordinary
;; Tablecloth pipeline that keeps the ones this post uses, gives them
;; shorter names, flattens the license to its identifier, and adds a
;; derived column -- the number of days between the day a repository was
;; created and the day it was last pushed to, which is as close as this
;; data gets to how long the project was worked on:

(def repos
  (-> all-repos
      (tc/select-columns [:full_name :description :stargazers_count :forks_count
                          :open_issues_count :size :topics :license :archived
                          :created_at :pushed_at])
      (tc/rename-columns {:stargazers_count  :stars
                          :forks_count       :forks
                          :open_issues_count :issues
                          :created_at        :created
                          :pushed_at         :pushed})
      (tc/map-columns :license [:license] (fn [m] (or (:spdx_id m) "none")))
      (tc/add-column :life-days
                     (fn [ds] (dtype-dt/between (ds :created) (ds :pushed) :days)))))

repos

;; ## A few plots
;;
;; ### When was Clojure code written?
;;
;; Count the repositories by the month they were created:

(def by-month
  (-> repos
      (tc/map-columns :month [:created]
                      (fn [^java.time.LocalDateTime t]
                        (.withDayOfMonth (.toLocalDate t) 1)))
      (tc/group-by [:month])
      (tc/aggregate {:repos tc/row-count})
      (tc/order-by :month)))

by-month

;; Drawn as a line:

(-> by-month
    (pj/lay-line :month :repos)
    (pj/options {:title "Clojure repositories created on GitHub, by month"}))

;; The x axis is a calendar axis, ticked every two years rather than at
;; each of the two hundred and twenty months in the data. Nobody asked for
;; that. It follows from the type of the `:month` column:

(tcc/typeof (by-month :month))

;; [Datasets, and why they matter](#datasets-and-why-they-matter) comes
;; back to why that decides so much.
;;
;; The shape of the line is a story about the ecosystem: a fast climb
;; from 2009, a peak in the middle of the 2010s, a long decline since.
;; The last point is a partial month -- this snapshot was taken in the
;; middle of August 2026.

;; ### Stars and forks, with a legend and a tooltip
;;
;; Take the repositories with at least five hundred stars. The ones with
;; no forks are left out too, because both axes below get a log scale and
;; a log axis has no reading for zero:

(def popular
  (-> repos
      (tc/select-rows (fn [row] (and (>= (:stars row) 500)
                                     (pos? (:forks row)))))))

(tc/shape popular)

;; The same columns as `repos`, fewer of the rows. Stars against forks, both
;; counts being heavily skewed, so both axes on a log scale. The `:color`
;; mapping separates archived repositories from live ones, and the two
;; plot options at the end make the picture interactive:

(-> popular
    (pj/lay-point :stars :forks {:color :archived :alpha 0.6})
    (pj/scale :x :log)
    (pj/scale :y :log)
    (pj/options {:tooltip true
                 :brush   true
                 :title   "Stars and forks, repositories with 500+ stars"}))

;; Hover a point and it names its values; drag a rectangle and the points
;; inside it stay lit while the rest fade to a tenth of their opacity.
;; Both come out of the SVG itself: the values ride on each shape as
;; attributes, and Plotje embeds the small script that reads them. There
;; is no JavaScript library to add and nothing to set up.
;;
;; The legend was not asked for either. A `:color` mapping onto a column
;; of strings or booleans means "one color per distinct value, and a
;; legend saying which".
;;
;; Stars and forks track each other closely enough over three orders of
;; magnitude that the cloud reads as a band rather than a scatter -- the
;; median repository here has about one fork for every thirteen stars.
;; Archiving is rare at this end of the distribution: the legend has two
;; colors, and one of them is hard to find.

;; ### Licenses over time
;;
;; The license column is worth a picture of its own. Count repositories
;; by license and creation year, for the four licenses that dominate:

(def licensed-by-year
  (-> repos
      (tc/select-rows (fn [row] (contains? #{"EPL-1.0" "EPL-2.0" "MIT" "Apache-2.0"}
                                           (:license row))))
      (tc/add-column :year (fn [ds] (dtype-dt/long-temporal-field :years (ds :created))))
      (tc/group-by [:license :year])
      (tc/aggregate {:repos tc/row-count})
      (tc/order-by [:license :year])))

licensed-by-year

;; Faceted by license:

(-> licensed-by-year
    (pj/lay-line :year :repos {:color :license})
    (pj/facet :license))

;; `pj/facet` splits one plot into a panel per value of a column, with
;; the axes shared so that the panels can be read against each other.
;; EPL-1.0 -- Clojure's own license -- towers over the others in the
;; early 2010s and then collapses; MIT rises more slowly and holds up
;; better; Apache-2.0 stays modest throughout; EPL-2.0, published in
;; 2017, shows up as a short spike around 2019. The years here are the
;; years the repositories were created, not the years the licenses were
;; chosen, which is why a handful of older repositories carry a license
;; that did not exist when they started.
;;
;; ### How long does a repository last?
;;
;; `:life-days` is the gap between a repository's first day and its last
;; push:

(tcc/median (repos :life-days))

;; The middle Clojure repository on GitHub was last touched less than a
;; year after it appeared.
;;
;; Count, for each year, the share of that year's repositories that were
;; never pushed again after the first day, and the share never pushed
;; again after the first week. `tc/aggregate` gives one column per share,
;; and
;; `tc/pivot->longer` turns the two column names into a `:horizon` column
;; that `:color` can name:

(def abandonment
  (-> repos
      (tc/add-column :year (fn [ds] (dtype-dt/long-temporal-field :years (ds :created))))
      (tc/select-rows (fn [row] (<= 2009 (:year row) 2025)))
      (tc/group-by [:year])
      (tc/aggregate {:same-day   (fn [ds] (double (/ (count (filter (fn [d] (< d 1)) (ds :life-days)))
                                                     (tc/row-count ds))))
                     :first-week (fn [ds] (double (/ (count (filter (fn [d] (< d 7)) (ds :life-days)))
                                                     (tc/row-count ds))))})
      (tc/pivot->longer [:same-day :first-week]
                        {:target-columns    :horizon
                         :value-column-name :share})))

abandonment

;; Each horizon gets its own line:

(-> abandonment
    (pj/lay-line :year :share {:color :horizon})
    pj/lay-point
    (pj/options {:title "Share of a year's repositories never pushed to again"}))

;; Both lines fall through the early years and then stop moving. From
;; 2013 on, the first-week share stays between 14 and 18 per cent and the
;; same-day share between 9 and 12, while the number of new repositories
;; behind those percentages falls away. About one
;; Clojure repository in six is published and never touched again, and
;; that rate has not responded to anything the ecosystem has done in
;; thirteen years.
;;
;; The last year here is 2025, not 2026. A repository created three days
;; ago cannot yet have gone a week without a push, so the current year
;; would report an inflated share.
;;
;; That is the tour. The rest of the post is about how these plots are put
;; together, and why they are put together that way.

;; ## Background: the grammar of graphics
;;
;; Leland Wilkinson's *The Grammar of Graphics* (1999) made the case that
;; statistical charts are not a list of chart types but a small set of
;; parts that combine: data, a mapping from variables to visual
;; properties, marks, statistical transformations, scales, coordinate
;; systems, guides. Hadley Wickham's
;; [ggplot2](https://ggplot2.tidyverse.org/) turned that into a working R
;; library, and a generation of people learned to think in its
;; vocabulary. The browser libraries most of us reach for --
;; [Vega-Lite](https://vega.github.io/vega-lite/),
;; [Plotly](https://plotly.com/javascript/),
;; [ECharts](https://echarts.apache.org/) -- carry more or less of the
;; same ideas into JSON specifications.
;;
;; Clojure has several libraries in this tradition already.
;; [Hanami](https://github.com/jsa-aerial/hanami) brought Vega-Lite within
;; reach by making its specifications into templates with substitution
;; keys. [Tableplot](https://scicloj.github.io/tableplot/) put a layered,
;; pipeline-shaped API over both Vega-Lite and Plotly. Both compose plain
;; Clojure data, and in both the picture is eventually drawn by a browser.
;; [Cljplot](https://github.com/generateme/cljplot) went the other way and
;; drew on the JVM, through
;; [Clojure2D](https://github.com/Clojure2D/clojure2d), with its own
;; grammar of layers and scales. Plotje is not the first Clojure library
;; to do either of those things.
;;
;; What it settles on is a grammar of poses and layers, a five-stage
;; pipeline whose intermediate values are all plain data, and Membrane as
;; the drawing model -- which means several rendering backends behind one
;; description. From Julia's
;; [AlgebraOfGraphics.jl](https://aog.makie.org/stable/) it borrows the
;; reading in which you build a plot by combining values, rather than by
;; calling a chart function with many arguments.
;;
;; This is also why the API looks the way it does. ggplot2 spells
;; composition `+` and wraps mappings in
;; `aes()`, so that a bare column name can be written without R trying to
;; evaluate it. Neither piece of machinery is needed in Clojure. A
;; keyword is already a name that does not evaluate to a value, so a
;; mapping is a map; and each step is an ordinary function call on the
;; value the step before it returned, which `->` lets you read top to
;; bottom. Keep the grammar, and let ordinary function calls do the
;; combining.

;; ## A plot is a value
;;
;; Plotje calls the description of a plot a **pose**: the arrangement you
;; settle into before the picture is taken. The functions that build one
;; -- `pj/pose`, the `pj/lay-*` family, `pj/options`, `pj/scale`,
;; `pj/facet` -- each take a pose, or the data to start one, and return a
;; pose.
;;
;; Here is the monthly line from earlier, printed instead of drawn:

(-> by-month
    (pj/lay-line :month :repos)
    (pj/options {:title "Clojure repositories created on GitHub, by month"})
    kind/pprint)

;; The dataset sits on `:data`. The mapping from columns to visual
;; properties sits on `:mapping`: `:month` became the x aesthetic and
;; `:repos` became y. The layers sit on `:layers`, in the order they were
;; added. Plot options sit on `:opts`.
;;
;; A pose takes an `assoc`, and the result compares equal to the pose the
;; API would have built:

(= (-> a-few-libraries
       (pj/pose :stars :forks)
       pj/lay-point
       (assoc-in [:mapping :color] :license))
   (-> a-few-libraries
       (pj/pose :stars :forks {:color :license})
       pj/lay-point))

;; Use the API call in real code, since it is clearer and it normalizes
;; what you write.
;;
;; A pose does not need a dataset, either. Leave it out and what you have
;; is a template, to be applied later to any table with the right columns:

(def stars-vs-forks
  (-> (pj/pose)
      (pj/lay-point :stars :forks {:alpha 0.5})
      (pj/scale :x :log)
      (pj/scale :y :log)))

;; The same template, applied first to the six libraries from the top of
;; the post and then to the repositories with five hundred stars or more:

(pj/arrange
 [(-> (pj/with-data stars-vs-forks a-few-libraries)
      (pj/options {:title "Six libraries"}))
  (-> (pj/with-data stars-vs-forks popular)
      (pj/options {:title "289 repositories"}))])

;; ## Mappings and layers
;;
;; A pose holds two kinds of thing, and both run through the whole
;; library.
;;
;; A **mapping** says which column becomes which visual property: `:year`
;; becomes the x aesthetic, `:license` becomes color. A **layer type**
;; says how the result is drawn -- points, a line, a boxplot, a smooth --
;; and is a mark (the shape drawn), a stat (a computation that runs first)
;; and a position (how overlapping groups are adjusted).
;;
;; A **layer** is a layer type placed on a pose. It can carry mappings of
;; its own, and where a mapping is written turns out to decide a good
;; deal; that is the next section. A layer written without them uses the
;; pose's, which is how these two share one set of axes and one legend:

(-> licensed-by-year
    (pj/pose :year :repos {:color :license})
    pj/lay-line
    pj/lay-point)

;; Layers are drawn in the order they were added, so the points read on
;; top of the lines.
;;
;; Each registered layer type has its own `pj/lay-*` function:

(-> (pj/registered-layer-types)
    keys
    sort
    vec)

;; ## Scope
;;
;; Once mappings and layers are separate values, one question matters:
;; where do you write the mapping?
;;
;; Written on the pose, it reaches every layer on that pose. That is the
;; plot above, where the lines and the points are both split by license.
;;
;; Written on a layer, it stops there:

(-> licensed-by-year
    (pj/pose :year :repos)
    (pj/lay-point {:color :license})
    pj/lay-line)

;; The points are colored by license, and the line is a single stroke
;; zig-zagging through all four series at once, because the `:license`
;; mapping never reaches the line layer and so gives it nothing to split
;; by. It is an ugly plot on purpose: it is what "not grouped" looks
;; like.
;;
;; A layer can also refuse a mapping it would otherwise inherit, by
;; setting it to `nil`: writing `{:color nil}` on the point layer above
;; would leave the lines split by license and draw the points in one gray.
;;
;; This is lexical scope: mappings flow downward from pose to layer, and a
;; lower one overrides a higher one.
;;
;; Grouping follows the same rule. A categorical mapping splits the layers
;; that see it, and where you write the mapping decides which layers those
;; are. When a split is wanted without a visual encoding, `:group` is the
;; aesthetic for it: writing `{:group :license}` on the line layer above
;; gives the same four lines in a single color.

;; ## Identity
;;
;; The layer functions have a second form that names columns directly.
;; That raises a question a threading pipeline has to answer: when you
;; call a second layer function, does the new layer join the plot you
;; were building, or start a new one?
;;
;; The rule is that a layer call naming columns looks for the most recent
;; pose whose x and y mappings match, and attaches to it. These match, so
;; the points and the smoothed trend land on one set of axes:

(-> by-month
    (pj/lay-point :month :repos {:alpha 0.4})
    (pj/lay-smooth :month :repos))

;; These do not match, so the second call starts a new pose. What comes
;; back is no longer a single pose but a composite holding two of them --
;; the same counts against the year, and against the license:

(def two-plots
  (-> licensed-by-year
      (pj/lay-point :year :repos)
      (pj/lay-point :license :repos)))

(mapv :mapping (:poses two-plots))

;; Drawn, they become two panels sharing the repos axis, laid out as one
;; row:

two-plots

;; One `->` thread can build either shape, and what you wrote decides
;; which one you get. When you are not sure which you wrote, print it and
;; look.

;; ## Datasets, and why they matter
;;
;; The six libraries at the top of this post were a plain vector of maps.
;; Everything since has been a dataset. Both work, because Plotje coerces
;; whatever you hand it -- a map of columns, a vector of row maps, a vector
;; of row vectors -- into a dataset first. So when is the dataset worth
;; reaching for?
;;
;; Dewey records the GitHub topics each repository declares, as a vector
;; per row. Counting them is a Tablecloth pipeline:

(def top-topics
  (-> {:topic (mapcat identity (repos :topics))}
      tc/dataset
      (tc/group-by [:topic])
      (tc/aggregate {:repos tc/row-count})
      (tc/order-by :repos :desc)
      (tc/drop-rows (fn [row] (contains? #{"clojure" "clojurescript"} (:topic row))))
      (tc/head 8)))

top-topics

;; Plotting it needs nothing further:

(-> top-topics
    (pj/lay-bar :topic :repos))

;; And the same numbers as plain Clojure data -- a vector of row maps,
;; with no dataset in sight -- give the same picture:

(-> (vec (tc/rows top-topics :as-maps))
    (pj/lay-bar :topic :repos))

;; A **dataset** here means a
;; [tech.ml.dataset](https://techascent.github.io/tech.ml.dataset/)
;; table, usually built and manipulated through
;; [Tablecloth](https://scicloj.github.io/tablecloth/): a columnar table
;; backed by typed arrays, the Clojure counterpart of an R data frame or
;; a pandas DataFrame. There are a few reasons to reach for one directly.
;; In particular:
;;
;; **A column's type decides how its values are read.** The monthly line
;; earlier has a calendar axis because of how its `:month` column is
;; typed, which follows from the `:parser-fn` used when the data was read.
;; Write the same months as strings and the type goes with them:

(def by-month-as-strings
  (-> by-month
      (tc/map-columns :month [:month] (fn [d] (subs (str d) 0 7)))))

[(tcc/typeof (by-month :month))
 (tcc/typeof (by-month-as-strings :month))]

;; The same two hundred and twenty bars come out of either column. From
;; the dates:

(-> by-month
    (pj/lay-bar :month :repos))

;; And from the strings, which are two hundred and twenty categories and
;; so have to be told how many labels to draw:

(-> by-month-as-strings
    (pj/lay-bar :month :repos)
    (pj/scale :x {:n-ticks 8}))

;; Compare the tick labels. The calendar axis picked 2010, 2012, 2014 and
;; so on, because a date is a quantity and it can work out which years are
;; round ones. The categorical axis spaced eight labels evenly along a
;; list, which landed on 2010-09 and 2013-01 -- regular, and meaningless
;; as dates.
;;
;; Neither plot is wrong. They are answers to different questions, and the
;; column's type is where the question is asked. The same distinction
;; decides whether a color mapping gets a gradient or a palette, and how a
;; number is formatted on a tick label. When you hand over plain Clojure
;; data the types are inferred during coercion, and a column of `"2020"`
;; strings is a different plot from a column of `2020` integers. A dataset
;; is where you say which one you meant.
;;
;; **A dataset pipeline and a plot pipeline are the same pipeline.** Both
;; are `->` over a value, so reshaping and plotting are one expression
;; rather than two stages with a variable in between. Every plot in this
;; post that starts from `repos` is doing that.
;;
;; **A mapping names a column, so the data has to be in long form.** The
;; six libraries are a small example of the shape most tables arrive in:
;; `:stars` and `:forks` are two measurements of the same thing, sitting
;; in two columns. To draw both on one chart, `:color` needs a column that
;; says which measurement a number is, and there is no such column.
;; `tc/pivot->longer` makes one:

(def libraries-long
  (-> a-few-libraries
      tc/dataset
      (tc/pivot->longer [:stars :forks]
                        {:target-columns    :measure
                         :value-column-name :count})))

libraries-long

;; The column names became values in a `:measure` column, which is now
;; something a mapping can name:

(-> libraries-long
    (pj/lay-bar :library :count {:color :measure}))

;; Performance is another reason, and it starts to matter past a few
;; thousand rows. Columnar storage and typed arrays are what let Plotje
;; work on [dtype-next](https://github.com/cnuernber/dtype-next) buffers
;; rather than on sequences of boxed values.

;; ## Inference
;;
;; A pose does not have to be complete. Leave a choice out and Plotje
;; fills it in from the types of the columns you named.
;;
;; One numerical column, and no layer at all, becomes a histogram. Here is
;; the whole lifespan distribution behind that median, in years:

(-> repos
    (tc/map-columns :life-years [:life-days] (fn [d] (/ d 365.25)))
    (pj/pose :life-years))

;; A long tail reaching seventeen years, and most of the mass in the
;; first two.
;;
;; Two numerical columns become a scatter -- the same two columns the
;; labelled plot at the top of the post used, with nothing else said about
;; them:

(-> a-few-libraries
    (pj/pose :stars :forks))

;; A categorical column against a numerical one becomes a boxplot. Take
;; the five commonest values of `:license`:

(def licensed
  (-> repos
      (tc/select-rows (fn [row] (contains? #{"none" "MIT" "EPL-1.0" "EPL-2.0" "Apache-2.0"}
                                           (:license row))))))

(tc/shape licensed)

;; Most of the data, then. Against lifespan:

(-> licensed
    (pj/pose :license :life-days))

;; The median for repositories with no license is the lowest of the five
;; by a clear margin, and Apache-2.0's the highest, with MIT and the two
;; EPLs close together in between. Adding a license is not what keeps a
;; project alive -- it is a sign that somebody expected it to last, and
;; popularity is tangled up in the same thing, since the most starred
;; repositories outlive the least by a wide margin.
;;
;; None of those three stored a layer on the pose. `:layers` is empty in
;; all of them, and the drawing recipe is chosen when the plot is drawn.
;;
;; The principle is that inference fills in only what you left out. An
;; explicit choice is always kept, so naming a layer type over the same
;; two columns answers the same question a different way -- `pj/lay-summary`
;; draws the mean of each group with its standard error:

(-> licensed
    (pj/lay-summary :license :life-days))

;; The ordering survives, and the level does not: every mean is far above
;; its median, because a small number of decade-old repositories drag it
;; up. Which of the two pictures is the honest answer depends on the
;; question; switching between them was one word.

;; Inference covers marks, stats, color types, grouping, axis titles, and
;; the mapping itself for datasets of one to three columns. The
;; [Inference Rules](https://scicloj.github.io/plotje/plotje_book.inference_rules.html)
;; chapter lists the whole set.

;; ## Membrane, and the stages in between
;;
;; A pose is a description; a plot is the rendered result. Between them
;; Plotje
;; runs five stages, and each transition is a public function you can
;; call on its own.
;;
;; | Stage | Produced by | What it holds |
;; |:------|:------------|:--------------|
;; | Pose | `pj/pose`, `pj/lay-*`, `pj/options`, ... | the description you wrote |
;; | Draft | `pj/pose->draft` | the pose flattened: one entry per layer, with all inherited mappings merged in |
;; | Plan | `pj/draft->plan` | resolved geometry: domains, ticks, legends, the output of every stat |
;; | Membrane | `pj/plan->membrane` | drawing primitives placed on the canvas |
;; | Plot | `pj/membrane->plot` | the format-specific result: SVG Hiccup, a `BufferedImage` |
;;
;; `pj/plot` runs those five steps in order, and so do
;; `pj/draft`, `pj/plan` and `pj/membrane`, which stop early -- which is
;; how you inspect what a stage produced.
;;
;; The fourth stage is the one this section is named after.
;; [Membrane](https://github.com/phronmophobic/membrane) is Adrian
;; Smith's Clojure UI library, and its central idea is that a user
;; interface is data: a tree of shapes, text and images that different
;; backends know how to draw. Plotje's fourth stage produces one of those
;; trees.
;;
;; A `PlotjeMembrane` implements Membrane's UI protocols, so a plot can be
;; represented as a UI component. It renders through whichever backend you
;; point at it -- SVG and a Java2D `BufferedImage` are built in -- and it
;; can sit in a hand-built Membrane view, which is why putting a plot
;; inside an Easel window is a matter of placing a component rather than
;; embedding a picture.

;; ## Composite poses
;;
;; A pose describing one panel is a **leaf pose**. A pose holding other
;; poses is a **composite**, and it is the same kind of map. Composites
;; are what multi-panel output is made of, and there are three ways in.
;;
;; `pj/facet`, which we have already seen, splits one pose by a column.
;;
;; `pj/arrange` tiles poses you have already built. They need not be
;; related. Here are two views of the same repositories -- when they were
;; created, and when they were last pushed to:

(defn per-year [ds col]
  (-> ds
      (tc/add-column :year (fn [data] (dtype-dt/long-temporal-field :years (data col))))
      (tc/group-by [:year])
      (tc/aggregate {:repos tc/row-count})
      (tc/order-by :year)))

(pj/arrange
 [(-> repos
      (per-year :created)
      (pj/lay-bar :year :repos)
      (pj/options {:title "Created"}))
  (-> repos
      (per-year :pushed)
      (pj/lay-bar :year :repos)
      (pj/options {:title "Last pushed to"}))])

;; Creation peaked in the middle of the 2010s and has fallen ever since.
;; Last-push dates are spread far more evenly, and the tallest bar of all
;; is the current year -- the repositories that are still being worked
;; on, whenever they were started.
;;
;; `pj/cross` names two lists of columns and gives you every pair --
;; a scatterplot matrix in one call. All three counts are long-tailed, so
;; this takes the base-ten logarithm of each first:

(def logged
  (-> repos
      (tc/select-rows (fn [row] (and (>= (:stars row) 300)
                                     (pos? (:forks row))
                                     (pos? (:issues row)))))
      (tc/add-column :log-stars  (fn [ds] (dfn/log10 (ds :stars))))
      (tc/add-column :log-forks  (fn [ds] (dfn/log10 (ds :forks))))
      (tc/add-column :log-issues (fn [ds] (dfn/log10 (ds :issues))))))

logged

;; The logarithms are the last three columns. Crossed against each
;; other:

(-> logged
    (pj/pose (pj/cross [:log-stars :log-forks :log-issues]
                       [:log-stars :log-forks :log-issues])
             {:color :archived})
    (pj/options {:title "Stars, forks and open issues, base-ten logarithms"}))

;; Stars and forks move together tightly enough to read as one band. Open
;; issues track either of them, but loosely: attention and reuse are close
;; to the same measurement, while the size of a project's inbox is a
;; looser thing.
;;
;; The diagonal panels are histograms of each variable on its own.
;;
;; All three of `pj/facet`, `pj/arrange` and `pj/cross` return poses. A
;; composite can hold composites, and the outer pose's data and mapping
;; flow into the poses inside it -- the same scope rule as before, one
;; level up.

;; ## What comes next
;;
;; Open threads, raised in the `#plotje` stream on Zulip:
;;
;; **Relating marks back to rows.** Adrian Smith
;; [argued](https://clojurians.zulipchat.com/#narrow/channel/610149-plotje/topic/interactive.20plot.20requirements)
;; that the essential problem in interactive graphics is relating a drawn
;; shape back to the data it came from, and that a mark should be able to
;; say how it was derived -- this row, this column, this binning, this
;; scale. Work is under way on a plot index that answers exactly that:
;; which rows of your data a drawn mark came from, so that a click on a
;; histogram bar can name the rows in its bin. The tooltips earlier in
;; this post are the shallow version of the same idea.
;;
;; **ClojureScript.** Timothy Pratley
;; [asked](https://clojurians.zulipchat.com/#narrow/channel/610149-plotje/topic/ClojureScript.20parity)
;; about running Plotje in the browser, which would open up animation and
;; richer interaction. The obstacle is that the dataset layer is JVM
;; only. Nobody has taken it on.
;;
;; Beyond those, the ordinary work: more chart types, better legends,
;; and the items in the
;; [Known Limitations](https://scicloj.github.io/plotje/plotje_book.known_limitations.html)
;; chapter, which is deliberately explicit about what does not work yet.

;; ## Where it stands, and what would help
;;
;; ```clojure
;; org.scicloj/plotje {:mvn/version "0.10.1"}
;; ```
;;
;; Plotje is alpha and heading for beta. The most useful thing anyone can
;; do right now is take it somewhere it has not been -- your data, your
;; chart, your workflow -- and report what breaks or reads badly. A plot
;; that cannot be expressed, an option that behaves unexpectedly, an error
;; message that does not help: all of it is worth an issue or a message,
;; and all of it is cheaper to fix before the API settles.
;;
;; - The book: [scicloj.github.io/plotje](https://scicloj.github.io/plotje/)
;;   -- every example rendered, from a
;;   [Quickstart](https://scicloj.github.io/plotje/plotje_book.quickstart.html)
;;   through a
;;   [Gallery](https://scicloj.github.io/plotje/plotje_book.gallery.html)
;;   to the
;;   [Known Limitations](https://scicloj.github.io/plotje/plotje_book.known_limitations.html).
;; - The source and the issue tracker:
;;   [github.com/scicloj/plotje](https://github.com/scicloj/plotje).
;;   A good share of the most recent release came out of reports and
;;   threads from people using it.
;; - The conversation: the
;;   [#plotje](https://clojurians.zulipchat.com/#narrow/channel/610149-plotje)
;;   stream on the Clojurians Zulip, and the weekly
;;   [Real-World Data](https://scicloj.github.io/docs/community/groups/real-world-data/)
;;   meetings.
