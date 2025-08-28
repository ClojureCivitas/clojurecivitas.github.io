---
author:
- name: Kira Howe
  url: https://kiramclean.com
type: post
title: My Dream Static Website Generator
date: 2021-04-28
site/group: Recent
site/tags:
  - static websites
  - software
---

I'm on the hunt for a simple tool to build simple websites. In my [last post](https://kiramclean.com/blog/there-is-so-much-low-hanging-fruit-in-web-development/) I explained a bit about what I mean by that and why I find the existing options unsatisfactory. It got me thinking about what my requirements are. I think it's good in general to think up front about how software should work. It helps me quickly pass on existing solutions before wasting tons of time learning a new thing only to discover it can't do what I need (because I didn't know what I needed until it was too late). It also helps me make better software.

I've made several different static site generators with different approaches and every time the reason I stop using them is because there's some feature I want that would be very complicated to add because it's at odds with some fundamental way the original system was designed. After a few iterations I feel like I finally have a pretty good grasp on my requirements, which is at least a start. The rest of this post explains what they are.

## Advanced markdown processing

Most static site generators render markdown to HTML, but most don't handle any extensions to basic markdown very well. I have some special things I need my static site generator to handle, including:
- **Typography**. I write in a plain text editor, so I need my site generator to properly process e.g. en dashes (–), em dashes (—), apostrophes (’), quote marks (“”), and other special characters and not just use the plain text versions that are the defaults in my editor.
- **Footnotes**. I don't often use footnotes, but when I do I do not want to faff with supertext links and manual anchor fragments.
- **Syntax highlighting**. Similarly, I don't often include code snippets in my posts, but I do occasionally and I find highlighted code much easier to read, so I'd like my blog to include syntax highlighted code snippets. I'm also weirdly obsessed with things working without JavaScript, so I need a tool that can process my posts to include the relevant markup and css at build time in order to have the smallest possible pages that also work without JS.

## Use open standards and best practices

By this I mean the final website should have no accessibility issues, no performance issues, and nothing extra being downloaded that is not necessary to load the text on the page. It should also use semantic HTML with all the right head tags to render properly in search results and social media. It should be styled with modern and minimal CSS, and, of course, have no JS for static content. Ideally all of these things would be checked on build so that an inaccessible, slow, bloated website simply cannot be made with this tool.

## Arbitrary data access

My main complaint with basically all of the current options for generating static websites is that the content and templates are all intermingled — severely complected, as a Clojurian would say. There are also major usability issues as a user trying to add new content to a website. Content and website structure should be fundamentally decoupled. That means:

- I shouldn't have to open my entire project including templates and config files to write a new blog post
- Not all the websites I make are blogs, so the tool should not dictate a particular website structure
- I shouldn't have to commit up front to a particular way of organizing the content of my website. Permalinks should be permanent and therefore fundamentally separate from page metadata like title, date, etc., as well as file metadata like parent directory.

### Blogging features

These should ideally fall out of the flexible requirements for accessing data I just described (for free), but there are a few specific blogging features I always need, so they get a special mention here:
- Tagging posts and index pages (including RSS feeds) for all posts with a given tag
- Grouping posts into arbitrary categories or series'
  - Easy links to next, previous, or related posts. This is really a special case of grouping, but called out separately for emphasis.
- Pagination for series or tags that accumulate a lot of posts

## Flexible templating and markup languages

I write mostly in either Org mode or Markdown, occasionally also Asciidoc. And sometimes I make a page that's so unique and incompatible with my existing layout I write it directly in HTML. My static site generator should be agnostic about markup language and support all of these seamlessly.

For designing the layout of the website it should use a well known but simple templating language. Something like Mustache, Liquid, or Nunchucks would be good, or considering my affinity for Clojure maybe Selmer or even Hiccup, though that might be prohibitively weird. The main point is that it should be simple to use and narrowly focused on templating. Concerns about munging data and post-processing content should be entirely separate from developing the layout of the website.

The layouts should also be sufficiently flexible to allow granular customization down to the page. There are many special cases where I want to include one extra tag in the head for example, or something special in the footer. The layout should support these kinds of customizations without requireing janky hacks that break the semantic grouping in the HTML.

## Smooth development experience

Speaking of developing the layout, I'm a spoiled millenial web developer and want a smooth developer experience. No janky scripts or custom build chains. I want a single command out of the box that I can run to get a development server that will watch my files and auto-reload the browser when they change. I'm not entirely sold on CSS and JS variants that require pre- or post-processing, but I think they're useful and ubiquitous enough the tool should probably support these as well.

Things like Less and Tailwind will probably fall out of fashion soon enough, but auto-prefixing and dead rule pruning are probably here to stay and should be included out of the box. Similarly I suspect the compile-to-JS language du jour will continue to change every year or two, but minifying and fingerprinting scripts are kind of minimum best practice now and should happen automatically. Same for image optimization to be honest, but now I'm really dreaming.

## Conclusion

This is my wishlist for my static website building experience. It follows a broad general pattern of being very flexible with data and the underlying model(s) the site uses, whilst being very rigid and prescriptive about standards for minimal markup. This fits my overall philosophy of programming. I basically think develoeprs should lean heavily on existing standards and best practices where they exist, so that the majority of development effort can be spent on the (ideally small) part of the project that truly is unique. One of my biggest complaints about the tech industry generally is how much time we waste reinventing wheels.

Thinking through how everything should work in advance like this is really helpful for me. The more software I write the more convinced I become that we should be spending vastly more time thinking about and designing software than we do. I get that there's a trade off between doing things well and getting things done, but there is also a difference between perfectionism and valuing quality.

So, hopefully someone will find this blog post and build my dream static website generator. Failing that, I will probably start working on yet another attempt of my own as my next side project.
