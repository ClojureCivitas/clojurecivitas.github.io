# Clojure Civitas

<img src="notebooks/images/civitas-icon.svg" alt="Civitas Icon" align="right">

An open effort to structure learning resources with meaningful connections.

⚡ No setup – Clone this repo, make a new namespace, start coding.

✍️ Write as you code – Capture notes, results, and ideas as you go as comments.

🚀 Easy to share – Create a Pull Request, once merged it appears on the [Clojure Civitas Website](https://clojurecivitas.github.io).

🧠 Build shared knowledge – Your work becomes part of a community resource.

🧪 Use Clay – Your normal REPL workflow, but with tables, charts, markdown, and hiccup.

## Rationale

*Exploramus, Communicamus, Crescimus*<br>
<small>We explore, we share, we grow.</small>

See [About Clojure Civitas](https://clojurecivitas.github.io/about).

## Contributing

Your perspective matters.
Pull Requests invited, that's the point!

### Creating notebooks

Add a Clojure namespace or markdown file in the [`/notebooks`](notebooks) folder.

Add metadata on your namespace to set the title, author, and tags.
```clojure
^{:kindly/hide-code true                 ; don't show this code in the notebook
  :clay             {:title  "About Civitas Metadata"
                     :quarto {:author   :my-unique-id
                              :type     :post
                              :date     "2025-06-05"
                              :category :clojure
                              :tags     [:metadata :civitas]}}}
(ns my.namespace.example)
```

Configure your author profile in [clay.edn](clay.edn).

Images can be added to the same folder as the namespace,
and displayed in the notebook with markdown like `![caption](my-image.jpg)`.
The first image on the page is used as a preview in the blog listing,
unless a different image is listed in the metadata.

[Kindly](https://scicloj.github.io/kindly-noted/kindly) annotations in your notebook are rendered as visualizations.

```clojure
^kind/table
{:tables      ["clean layout" "easy to scan" "communicates clearly"]
 :charts      ["information-dense" "reveals insights" "pattern-focused"]
 :hiccup      ["build anything" "custom layouts" "unlimited flexibility"]
 :many-others ["see the examples" "creative uses" "visual variety"]}
```

**Optional:** [Setup your editor with Clay shortcuts](https://scicloj.github.io/clay/#setup) to visualize the notebook as you write.

**Optional:** Preview the final website.

```sh
clojure -M:clay -a [:markdown]
```

```sh
quarto preview site
```

[Quarto](https://quarto.org/) is the markdown publishing tool.

### Publish

Merged pull requests are automatically shown on the website.

To create a pull request

1. fork the repository
2. make and commit changes in a new branch
3. push the branch to your fork
4. and then open a pull request on GitHub to propose merging your changes into the Civitas main branch.

Please contact @timothypratley if you are having any difficulty submitting a notebook.

### See your page views

Publicly available [page view analytics](https://clojurecivitas.goatcounter.com/) indicate how widely your notebook is being shared.

### Building the database

Add to or modify [db.edn](quarto/db.edn).
The goal is to create a database of resources for learning.

## Design

Align with Clojure's values: simplicity, community, and tooling that helps you think.

### Notebook Namespace Selection

A notebook’s namespace serves as a clear, unique path to its content and follows **Clojure’s naming conventions**.

The namespace should emphasize **what the notebook is about**, not how it is categorized.
Think of it as a logical path that leads to a specific artifact or topic.
Classification elements such as tags, author, document type, level, or publication date belong in **metadata**, not the
namespace.

- **Start with an organization** if the notebook is about a library or tool maintained by one.  
  Examples: `scicloj`, `lambdaisland`.
- **Follow with the specific library or concept.**  
  Examples: `scicloj.clay`, `lambdaisland.kaocha`.
- If there is **no organization**, start directly with the library or tool name.  
  Examples: `hiccup`, `reagent`.
  If the notebook refers to an alternative implementation, **include the organization** to disambiguate.  
  Example: `lambdaisland.hiccup`.
- For **core Clojure topics**, use `clojure` as the root.  
  Examples: `clojure.lazy-sequences`, `clojure.transducers`.
- Add **segments** to describe the notebook’s content. These segments should:
    - Avoid name collisions.
    - Not duplicate metadata.
    - The last segment should be specific and descriptive. Prefer: `z-combinator-gambit`, avoid: `z-combinator`.
- **Events, communities, or topics** may also be used as the top-level namespace when appropriate.  
  Use discretion to determine whether a notebook is primarily about an artifact library,
  a concept, or an event.
- Namespaces must consist of more than one segment.

#### Metadata and Navigation

It may feel unintuitive not to group related content (e.g. an author’s blog series) by directory or namespace.
But this structure is intentional.
Linear sequences (e.g. blog posts by an author) will be **reconstructed from metadata**, not filenames or folders.
For example, a page showing all blog posts by an author is generated by filtering for `author`, `type = post`, and
`date`, and then ordering by date.

Namespaces prioritize **logical addressing** over ontological hierarchy.
This promotes flexibility at the cost of tidiness, but enables richer discovery through metadata and search.

Differentiation between posts, pages, and presentations is by `type` metadata (a Quarto page type convention).

#### Examples

| Namespace                                                               | Description                                                   |
|-------------------------------------------------------------------------|---------------------------------------------------------------|
| `scicloj.clay.clojure-notebooks-for-pythonistas`                        | Introduction to Clay for Python programmers.                  |
| `lambdaisland.kaocha.customization-tips-and-tricks`                     | Tips for fast iteration with Kaocha.                          |
| `lambdaisland.kaocha.up-and-running-on-ubuntu`                          | Kaocha setup guide for Ubuntu.                                |
| `clojure.transducers.how-it-works-explained-with-diagrams`              | Explains transducers with diagrams.                           |
| `clojure.lazy-sequences.detailed-explanation-by-example`                | In-depth example-driven guide to lazy sequences.              |
| `conferences.clojure-conj-2023.state-of-clojure.notes.from-the-backrow` | Notes on the "State of Clojure" talk at Clojure/Conj 2023.    |
| `hiccup.basic-html-generation`                                          | Tutorial on generating HTML with Hiccup.                      |
| `algorithms.graph.layout.force-directed-spring-simulation`              | On force-directed graph layout algorithms (library-agnostic). |
| `data-structures.datoms.all-about-eavt`                                 | EAVT indexing, not tied to any vendor.                        |
| `clojure.deps-edn.monorepo-setup-in-detail`                             | Monorepo setup using `deps.edn`.                              |
| `cursive.super-easy-debugging-techniques`                               | Debugging in Cursive IDE, for beginners.                      |
| `cognitect.datomic.cloud.how-we-scale-to-5million-users`                | Datomic Cloud scaling case study.                             |
| `reagent.component-lifecycle.a-tale-of-life-death-and-rebirth`          | A whimsical take on Reagent component lifecycles.             |

### File system organization

| Directory   | Description                                                          |
|-------------|----------------------------------------------------------------------|
| `src`       | Code for building a website and database                             |
| `notebooks` | Source root for notebooks (Clojure and Markdown), images, data files |
| `site`      | Static assets of the Quarto website                                  |

Non-Clojure files in `notebooks` will be synced to `site`.
Shared images can go in `images`, but prefer placing images and data files as siblings to your namespace under
`notebooks`.
All Clojure and Non-Clojure files in `notebooks` should go under a subdirectory,
so that it is clear they are not part of the static configuration of `site`.
When building the site, Clojure namespaces are built to markdown files under `site/{my/namespaced/notebook.qmd}`.
Subdirectories of `site` are gitignored and considered temporary build artifacts, safe to clean up.
Quarto builds all the markdown into HTML in `_site` for preview and deploy.
While developing, Clay uses `temp` to build and serve HTML files.

Goal: Align with Clojure’s code organization while allowing organic, practical growth.

### Topic organization

Follow the Quarto convention of categories, tags, and keywords.
Fixed categories; `community`, `algorithms`, `data`, `systems`, `libs`, `concepts`.
Tags; flexible, open-ended for finer-grained labeling (e.g. `frontend`, `reagent`).
Keywords; for SEO or search indexing; typically fewer and focused on discoverability.

Tags and metadata are the preferred organization principle:
[Categories, Links, and Tags](https://gwern.net/doc/philosophy/ontology/2005-04-shirky-ontologyisoverratedcategorieslinksandtags.html)

Goal: Constellations, not cabinets.

### Dependency management

A single `deps.edn` file is shared across all notebooks.

Pros:

* Simplifies website builds.
* Works for authoring as well as building.

Cons:

* Version conflicts must be manually resolved.
* Only one version per dependency.
* Notebooks aren’t self-contained.

Future:

* Support additional directories under `standalone` with their own `deps.edn`.
* Regression testing would help when versions update.

Goal: Minimize friction in authoring while ensuring publishable reproducibility.

## License

Copyright © 2025 Timothy Pratley

Distributed under the Eclipse Public License version 1.0.
