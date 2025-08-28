---
author:
- name: Kira Howe
  url: https://kiramclean.com
type: post
title: There Is So Much Low Hanging Fruit In Web Development
date: 2021-04-24
site/group: Recent
site/tags:
  - static websites
  - software
---

I am perpetually annoyed at how frustrating it is to build simple websites. Fair warning, this post is a bit ranty. I will explore technical solutions to these problems in a follow up post. But this one sets the stage first to explain why I spend so much time working on and thinking about things that appear to be easy, solved problems.

There's a certain kind of website that lands in a kind of uncanny valley, being at once

- too simple to justify running a server around the clock just to keep it available
- not visited enough to justify paying very much at all to keep online
- too bespoke and un-blog-like enough to be a massive PITA to build using existing static website generation tools like jekyll or hugo
- complicated enough to be too brittle to build from a few cobbled together scripts

I contend that there is currently no tool that makes it easy to build accessible, modern, fast, static websites that are any more complicated than a very plain-looking blog.

To be fair, maybe this is because this just isn't as easy a task as it seems, which is why so many people earn a literal fortune doing it. And that's fair. If you want a nice website, spending a few thousand dollars on a professional developer and designer is the way to go. But what about all the millions of people who just have some dumb essays they want to publish? Or some random idea to try out to see if it will stick? It's really annoying to do right now.

I'm sad that there is no better solution than the drag-and-drop website builders backed by javascript frameworks that make database-driven websites to serve what is essentially static content. This has resulted in an internet where the vast majority of websites are [inaccessible](https://www.deque.com/blog/research-shows-internet-is-unavailable-to-blind-users/) (80% of websites have significant accessibility issues), [slow](https://trinity.one/insights/user-experience/page-speed-conversion-2019-statistics/) (only 15% of websites operate at an acceptable page speed), and [bloated](https://www.thinkwithgoogle.com/intl/en-ca/marketing-strategies/app-and-mobile/mobile-page-speed-new-industry-benchmarks/) (70% of pages are over 1MB).

When I first got into web development I was shocked to discover that the vast majority of the industry is pretty ok with this state of affairs. I do bring up these concerns with my fellow tech people from time to time but the answer I overwhelmingly get reflects the kind of half-baked libertarian free-market indifference that has thoroughly permeated the industry: "something something capitalism if people are making money everything must be fine".

I disagree. Everything is not fine. Whether something makes money is not the only measure that matters. It's true that there are many companies and people that have made a fortune polluting the internet with inaccesible, slow, and bloated websites. That doesn't make it ok.

Anyway, believe it or not complaining about problems is not my favourite passtime. I spend most of my waking hours thinking about and working on solutions to problems, but I also spend a fair amount of time trying to decide which problems to focus on. All of this got me thinking about what key features I need in a website generator, which I'll write down next.
