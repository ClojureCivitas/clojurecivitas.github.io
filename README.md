# Welcome to Clojure Civitas

<img src="civitas-icon.svg" alt="Civitas Icon" align="right">

An open effort to structure learning resources with meaningful connections.

Explore the [Clojure Civitas Website](https://timothypratley.github.io/civitas)

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1rem; margin: 2rem 0;">
  <div style="border-left: 3px solid green; padding-left: 1rem;">
    <h3>Non-linear exploration</h3>
    <p>Navigate your own path through interconnected topics.</p>
  </div>
  <div style="border-left: 3px solid blue; padding-left: 1rem;">
    <h3>Literate by design</h3>
    <p>Write notebooks and place them in context, linking knowledge.</p>
  </div>
  <div style="border-left: 3px solid orange; padding-left: 1rem;">
    <h3>Community supported growth</h3>
    <p>The best resources rise through collective refinement.</p>
  </div>
</div>

<div style="text-align: center; font-family: 'Georgia', serif; font-size: 1.5rem; margin: 2rem 0; color: #2e7d32;">
  Exploramus, Communicamus, Crescimus<br>
  <small style="font-size: 1rem; color: #555;">We explore, we share, we grow.</small>
</div>

## Contribute

Your perspective matters.

### Build the database

Add to or modify [db.edn](quarto/db.edn)

### Write notebooks

Add a notebook in the [`/content`](content) folder.

See the [Clay Documentation](https://scicloj.github.io/clay) for information on how to interactively visualize the notebook as you write it.

### Preview the full website

```sh
clojure -M:m
```

```sh
quarto preview site
```

### Publish

Merged pull requests are shown on the website via a workflow.

## Rationale

Learning technical subjects involves navigating complex webs of concepts.
Currently, resources for learning Clojure exist as isolated articles, unconnected tutorials, or flat lists that do not
capture how ideas relate to each other.
This makes it difficult for learners to determine logical next steps or see how topics connect.
Teachers and curriculum designers face similar challenges when trying to organize material in ways that reflect actual
dependencies and alternatives.

Civitas approaches this problem by implementing a structured knowledge base where resources are explicitly linked based
on their conceptual relationships.
The hexagonal grid interface provides a visual representation of these connections, allowing users to naturally discover
related content.
Each resource includes metadata to indicate prerequisites, alternatives, and deeper explorations.

This structure serves several practical purposes.
Learners can see multiple valid paths through the material.
Teachers can construct guided sequences while maintaining visibility of adjacent concepts.
The community can improve resources incrementally by adding or refining connections.
The system is designed to grow organically as new relationships are identified and documented.

The value lies not in any single feature, but in making the existing ecosystem of learning resources more navigable and
interconnected.

<img src="bees.jpg" alt="Honey bees behive" style="width: 100%;">

## Embracing alternatives

It is not a goal to centralize Clojure knowledge creation.
It is a goal to provide a welcoming pattern that works with other communities and users.
ClojureCivitas can be used as a library to simplify publishing your own garden of content.

## Design

### File system organization

| Directory           | Description                                                 |
|---------------------|-------------------------------------------------------------|
| `src`               | Code for building a website and database                    |
| `content`           | Notebooks (Clojure and Markdown), images, data files        |
| `content/posts`     | Time-anchored content (blog posts, announcements, analyses) |   
| `content/pages`     | Timeless or evolving content related to this site           |
| `content/tutorials` | Learning materials (tutorials, workshops, guides)           |
| `content/talks`     | Presentation slides and related materials                   |
| `content/ideas`     | Drafts, experiments, and work-in-progress content           |
| `site`              | Quarto configuration for the website                        |

Each of `posts`, `pages`, `tutorials`, `talks`, `ideas` is a source-root.
These directories make it easier to distinguish blog posts from everything else.

Namespace selection for notebooks follow standard Clojure namespace conventions to avoid conflicts.

Library-related notebooks: Use the library’s namespace prefix (e.g., scicloj.clay.v2.guide.getting-started).

Disambiguation: If a topic exists, qualify with additional context (e.g.,
scicloj.clay.v2.guide.getting-started.intellij).

Dependent utils/modules: Nest under a parent namespace (e.g., ...getting-started.index, ...getting-started.utils).

Civitas invokes [Clay](https://github.com/scicloj/clay) to convert Clojure notebooks into Markdown files, placing them in the `site` directory.
This `site` directory serves as a build folder for [Quarto](https://quarto.org/).
Avoid adding files directly to `site`.

Static assets (such as images or data files) should be placed in the `content` directory.
During the build process, any files in the `content` directory that are not Clojure files, are copied into the `site` directory.
Quarto then builds the Markdown in `site` into HTML, which is placed in `_site` before deploying it to GitHub Pages.

During development, Clay serves HTML from the `temp` directory, which is git ignored.

Goal: Align with Clojure’s code organization while allowing organic, practical growth.

### Topic organization

Notebooks are tagged with a primary topic (first in list) and optional additional topics.

Fixed top-level topics: `:community`, `:core`, `:data`, `:system`, `:tooling`, `:web`.

Subtopics: Semi-open (e.g., :web/frontend implies :web). Expect these to evolve.

Topics are one tagging method; others may emerge.

Tags want to be free:
https://gwern.net/doc/philosophy/ontology/2005-04-shirky-ontologyisoverratedcategorieslinksandtags.html

But also we can help curate the content with a good set of top-level topics.

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
