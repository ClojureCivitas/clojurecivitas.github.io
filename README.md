# Clojure Civitas

<img src="src/images/civitas-icon.svg" alt="Civitas Icon" align="right">

Clojure Civitas is a shared [blog](https://clojurecivitas.github.io/posts.html) space for your ideas and explorations.
Clojure Civitas makes it easy for you to publish Clojure ideas and explorations without the overhead of setting up a new project, blog, or repo.
Whether you're sketching out a quick experiment or writing a deeper post,
just fork this repo, add a namespace, write, commit and submit a pull request.
This is your shared scratch space.

Think. Code. Share.

⚡ No setup – Clone this repo, make a new namespace, start coding.

✍️ Write as you code – Capture notes, results, and ideas as you go as comments.

🚀 Easy to share – Create a Pull Request, once merged it appears on the [Clojure Civitas Website](https://clojurecivitas.github.io).

🧠 Build shared knowledge – Your work becomes part of a community resource.

🧪 Visualize – Your normal REPL workflow, but with tables, charts, markdown, and hiccup.

## Rationale

Literate programming is fun.
We want more people to experience it.
Why markdown in code? We value reproducible artifacts.

See [Clojure Civitas Rationale](https://clojurecivitas.github.io/about#rationale) for more detail.

## Contributing

*Exploramus, Communicamus, Crescimus*<br>
<small>We explore, we share, we grow.</small>

Your perspective matters.
Pull Requests invited, that's the point!

### Creating Posts and Pages

Add a Clojure namespace or markdown file in the [`/src`](src) folder.

> [!TIP]
> If you prefer to write Markdown instead of Clojure,
> put your markdown file in [`/site`](site) instead.
> For a personal blog, you can put files in [`/site/blog`](site/blog).

Add metadata on your namespace to set the title, author, and tags.

```clojure
^{:kindly/hide-code true     ; don't render this code to the HTML document
  :clay             {:title  "About Civitas Metadata"
                     :quarto {:author   :my-unique-id
                              :draft    true           ; remove to publish
                              :type     :post
                              :date     "2025-06-05"
                              :category :clojure
                              :tags     [:metadata :civitas]}}}
(ns my.namespace.example)
```

> [!TIP]
> The `:draft true` flag keeps it out of the posts list, but it will be available as a draft which can only be viewed by using the full URL.
> This can help if you aren't confident about the posting process or would like feedback before sharing.
> Otherwise, I recommend going YOLO and not worrying about drafts.

Configure your author profile in [site/db.edn](site/db.edn).

Images can be added to the same folder as the namespace,
and displayed with markdown like `;; ![caption](my-image.jpg)`.
The first image on the page is used as a preview in the blog listing,
unless a different image is listed in the metadata.

### Adding Visualizations

[Kindly](https://scicloj.github.io/kindly-noted/kindly) annotations are rendered as visualizations.
You add code like this:

```clojure
^kind/table
{:tables      ["clean layout" "easy to scan" "communicates clearly"]
 :charts      ["information-dense" "reveals insights" "pattern-focused"]
 :hiccup      ["build anything" "custom layouts" "unlimited flexibility"]
 :many-others ["see the examples" "creative uses" "visual variety"]}
```

Then you can render the table interactively, and it will also be rendered in the final post.

See [Clay examples](https://scicloj.github.io/clay/clay_book.examples.html) to see what's possible.

### Preview your namespace as a Webpage **(Optional, Recommended)**

Clay is ready to interactively render your namespace as HTML, you just need to ask it to "Clay Make File".

**REPL commands to render as you write**

| Editor   | Integration Details                                                                 |
|----------|-------------------------------------------------------------------------------------|
| Cursive  | "Clay Make File" and similar REPL commands added automatically                      |
| Calva    | Install "Calva Power Tools" extension for "Clay Make File" and similar commands     |
| Emacs    | See [clay.el](https://github.com/scicloj/clay.el)                                   |
| Neovim   | See [clay.nvim](https://github.com/radovanne/clay.nvim)                             |

**CLI alternative**

If you prefer not to use editor integration, you can run:

```
clojure -M:clay
```

Will live reload your changes (see [command line](https://scicloj.github.io/clay/#cli) for more details)

**REPL alternative**

If you prefer invoking Clay from the REPL,
see [scicloj.clay.v2.snippets/make-ns-html!](https://github.com/scicloj/clay/blob/main/src/scicloj/clay/v2/snippets.clj)
and the [Clay API documentation](https://scicloj.github.io/clay/#api).

### Previewing the Website with Quarto **(Optional, Not Required)**

The published site goes through a longer process of producing markdown then HTML.
[Quarto](https://quarto.org/) is the markdown publishing tool.

**Using editor integration**

You can use the "Clay Make File Quarto" command to generate the necessary files via Quarto.
It takes a little bit longer, but will show the styles of the published site.
You may need to do a preparatory command-line markdown build once to get the necessary files in place.

**Using the command line**

```sh
clojure -M:clay -A:markdown
```

```sh
quarto preview site
```

(`bb site-preview` will run these two commands for you.)

This will open a browser displaying the site locally.

If you would like to preview a single file, then add the path relative to `src`:

```sh
clojure -M:clay -A:markdown games/beginning_to_build_a_browser_game.clj
```

### Publish

Create a pull request:

1. fork the repository
2. make changes in a new branch and commit them
3. push the branch to your fork
4. open a pull request

Your pull request will be quickly reviewed to prevent abuse and then merged.
Reviews are minimal, our goal is to get your changes published ASAP.
Once merged, namespaces are automatically published to the website.

Please contact [@timothypratley](https://github.com/timothypratley) if you are having any difficulty submitting a PR.

### Check Your Page Views

Publicly available [page view analytics](https://clojurecivitas.goatcounter.com/) indicate how widely your idea is being shared.

### Editing the Civitas Explorer Database

An open effort to structure learning resources with meaningful connections.
Add to or modify [site/db.edn](site/db.edn).
The goal is to create a database of resources for learning.
See the [explorer](https://clojurecivitas.github.io/civitas/explorer.html).

### Large data, slow calculations, private credentials

You can render a `qmd` file locally (see preview instructions above).
If you `git add -f site/my-ns/my-post.qmd`,
it will prevent the source `src/my-ns/my-post.clj` file from executing in the publish process.
Only you will run the code locally (where you have secrets and large files available).

If your notebook displays locally stored images, you will also need to commit these. Quarto puts them under `site/my-ns/my-post_files`.

See [Some notebooks should only be run locally](https://clojurecivitas.github.io/scicloj/clay/skip_if_unchanged_example.html) for more detail.

### Styling and other features

Pages are rendered with [Quarto](https://quarto.org).
To see what styling options are available, check the [Quarto HTML Reference](https://quarto.org/docs/reference/formats/html.html).
Anything you can do there, you can do in comment markdown and namespace metadata annotation.

## Design

Align with Clojure's values: simplicity, community, and tooling that helps you think.

### Namespace Selection

A namespace serves as a clear, unique path to its content and follows **Clojure’s naming conventions**.

The namespace should emphasize **what the narrative is about**, not how it is categorized.
Think of it as a logical path that leads to a specific artifact or topic.
Classification elements such as tags, author, document type, level, or publication date belong in **metadata**.

- **Start with an organization** if the narrative is about a library or tool maintained by one.
  Examples: `scicloj`, `lambdaisland`.
- **Follow with the specific library or concept.**
  Examples: `scicloj.clay`, `lambdaisland.kaocha`, `lambdaisland.hiccup`.
- If there is **no organization**, start directly with the library or tool name.
  Examples: `hiccup`, `reagent`.
- For **core Clojure topics**, use `clojure` as the root.
  Examples: `clojure.lazy-sequences`, `clojure.transducers`.
- Add **segments** to further qualify the namespace. These segments should:
    - Avoid name collisions.
    - Not duplicate metadata.
    - The last segment should be extra specific and descriptive. Prefer: `z-combinator-gambit`, avoid: `z-combinator`.
- **Events, communities, or topics** may also be used as the top-level namespace when appropriate.
  Use discretion to determine if the narrative is primarily about an artifact library, a concept, or an event.
- Namespaces must consist of more than one segment.

#### Choosing a Namespace Examples

| Namespace                                                               | Description                                                   |
|-------------------------------------------------------------------------|---------------------------------------------------------------|
| `lambdaisland.kaocha.customization-tips-and-tricks`                     | Tips for fast iteration with Kaocha.                          |
| `clojure.lazy-sequences.detailed-explanation-by-example`                | In-depth example-driven guide to lazy sequences.              |
| `algorithms.graph.layout.force-directed-spring-simulation`              | On force-directed graph layout algorithms (library-agnostic). |
| `reagent.component-lifecycle.a-tale-of-life-death-and-rebirth`          | A whimsical take on Reagent component lifecycles.             |

#### Metadata and Navigation

It may feel unintuitive not to group related content (e.g. an author’s blog series) by directory or namespace.
But this structure is intentional.
Linear sequences (e.g. blog posts by an author) will be **reconstructed from metadata**, not filenames or folders.
For example, a page showing all blog posts by an author is generated by filtering for `author`, `type = post`, and
`date`, and then ordering by date.

Namespaces prioritize **logical addressing** over ontological hierarchy.
This promotes flexibility at the cost of tidiness, but enables richer discovery through metadata and search.

Differentiation between posts, pages, and presentations is by `type` metadata (a Quarto page type convention).

#### Exception: Personal blog posts

The `blog` directories (in `src` and `site`) are exceptions to the topic oriented namespacing.
We invite personal blogs, serialized writing, or other content where organizing by author and date is more convenient than by topic or namespace.

For example: `site/blog/myname/2025-08-28-my-post.md`

This structure is an intentional exception to the namespace organization.
We want it to be easy for contributors to migrate or maintain personal blogs.

### File system organization

| Directory   | Description                                                  |
|-------------|--------------------------------------------------------------|
| `src`       | Source root for namespaces, markdown, images, and data files |
| `site`      | Static assets of the Quarto website                          |
| `site/blog` | Personal writings                                            |

Non-Clojure files in `src` will be synced to `site`.
Shared images can go in `src/images`,
but prefer placing images and data files as siblings to your namespace under `src`.
All files in `src` should go under a subdirectory,
so that it is clear they are not part of the static configuration of `site`.
Clojure namespaces are built to markdown files under `site/{my/namespaced/awesome-idea.qmd}`.
Subdirectories of `site` are git ignored and considered temporary build artifacts, safe to clean up.
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

A single `deps.edn` file is shared.
Contributors are encouraged to add dependencies as needed.

Pros:

* Simplifies website builds.
* Works for authoring as well as building.

Cons:

* Version conflicts must be manually resolved.
* Only one version per dependency.
* Pages and posts aren’t self-contained.

Future:

* Support additional directories under `standalone` with their own `deps.edn`.
* Regression testing would help when versions update.

Goal: Minimize friction in authoring while ensuring publishable reproducibility.

## Deployment

See [.github/workflows/render-and-publish.yml](.github/workflows/render-and-publish.yml)

## License

Copyright © 2025 Timothy Pratley

Distributed under the Eclipse Public License version 1.0.
