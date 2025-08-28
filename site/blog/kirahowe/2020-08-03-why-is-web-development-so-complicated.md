---
author:
- name: Kira Howe
  url: https://kiramclean.com
type: post
slug: blog/why-are-websites-so-complicated-now
title: Why Are Websites So Complicated Now?
date: 2020-08-03
site/group: Tech
site/tags:
  - static websites
  - software
---

I first got into programming because I wanted to make my own websites. Back then
all it took was two files, an `index.html` and a style sheet. I recently decided
to take up blogging again and started looking around for a simple static site
generator. There are a couple of reasons why I avoided some other approaches.

- I have had negative experiences with Wordpress that make me not want to use it
  again. Plus it is way more sophisticated than I need for a simple place to
  publish some thoughts.
- I want unambiguous ownership of my blog. Less important but also relevant, I
  want to easily be able to control how it looks. This rules out quite a few
  popular batteries-included blogging platforms.
- I do not want to setup (or pay for) a server somewhere to serve a simple
  website with only static pages.

I've been writing software for the internet for some years now, but it's been a
while since I worked on anything like what we think of as a normal website. When
I started looking into how those are made these days, I was surprised to find
mostly complicated, bloated frameworks that nearly completely abstract away the
basic task of generating HTML.

It doesn't take multiple new languages, frameworks, or build tools to make a
really simple website.

## Content is not software

Frameworks are for people building sophisticated software that happens to run in
a browser. There is no need for them if all you want to do is put some content
on the internet. I will argue that if your main goal is publishing content you
don't even need JavaScript at all.

JavaScript frameworks mostly help by allowing us to write non-trivial software
in a language less terrible than JavaScript itself. They can also help with
things like managing state, reacting to user input, and communicating
asynchronously with a server to get new information onto a page without needing
to refresh the whole thing. None of these challenges are present when the main
purpose of a website is to display static information.

For that, all we should need is a language to markup our plain words into
something a browser can render, like HTML.

## A simple static site generator

I prefer writing software over configuring software, and I resent things that
are more complicated than they need to be. I'm a minimalist by nature and I am
against the direction our industry is currently headed -- shipping bloated and
buggy software by the ton -- but that's a topic for another day.

After some investigation and frustration trying to do simple things, like edit
canned templates or change the source and structure of my content, I of course
decided to write my own static site generator, like any sane developer would do.

For now all it does is convert my posts (which I write in markdown) into HTML
and insert that HTML into a template, then put those files in the right place.
As my blog grows I'll add more features, and maybe even make it useful enough
for someone else to use someday. For now it's a simple solution that works on my
machine, and was definitely more enjoyable for me to write than was faffing with
configuring the other tools I tried.

## A plea for simplicity

I get that a lot of people are not making simple websites anymore. There is some
seriously sophisticated software running in browsers now, which I understand is
the reason for the proliferation of JavaScript frameworks and build tools
currently plaguing our work lives.

A lot of people are still simply publishing content to the internet, though, and
we seem to have forgotten what a website is really made of. It's just HTML
(which is supposed to semantically mark up content to describe its <em>meaning
and structure</em>), and optional styling with CSS (to describe it's
<em>appearance</em>). These languages are not so complicated to write that we
need complex frameworks that add a dozen more layers between the content and the
final static pages. If all you want is to put some information on the internet,
see if an old fashioned HTML file might be all you need.
