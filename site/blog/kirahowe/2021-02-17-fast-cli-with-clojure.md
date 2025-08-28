---
author:
- name: Kira Howe
  url: https://kiramclean.com
type: post
date: 2021-02-18
title: Building A Fast Command Line App With Clojure
site/group: Recent
site/tags:
    - clojure
    - software
    - graalvm
    - command-line app
---

_<time datetime="2021-02-18">Originally published on February 18, 2021</time>_

Like most developers, I always have about a hundred ideas for little tools or apps I wish existed. Every once in a while I get the time and energy to magic one of them into existence. Clojure is my language of choice these days, but at first glance it's not super well suited to building little command-line apps (which is usually what I start with). Some things that make it not an obvious first choice:

- Slow JVM startup time. It usually takes a second or two or more to fire up a JVM, which is an unacceptable startup penalty if your whole app is just a little utility meant to run fast.

- No coherent ecosystem. The Clojure community is very averse to batteries-included solutions. There are good reasons why, a main one being that we spend vastly more time maintaining apps than setting them up so we should aggressively avoid including non-essential dependencies, which add to our maintenance burden. This is fair and does work out really well for long-running projects (which is most Clojure projects). But, this means it's often frustrating and slow to get a new project started, compared to some languages at least. There's no `clojure new-cli-app` type command you can just run to get a new app that works in 1 second.

This post is about how to build a command-line app with Clojure, using tools.deps and GraalVM.

_I assume you already have Clojure (including the CLI) and GraalVM installed. I use [jabba](https://github.com/shyiko/jabba) to manage JVMs on my machine, and installed and setup a GraalVM by running  `jabba install graalvm-ce-java11@20.3.0` and then `jabba use graalvm-ce-java11@20.3`. You also need the [GraalVM native image](https://www.graalvm.org/reference-manual/native-image/) utility, which I installed with `gu install native-image`._

## GraalVM and Clojure community to the rescue

GraalVM is basically a super fast JVM, which solves the problem of slow startup time. You can use it to build a standalone executable out of your Clojure app that will run instantly.

Even though there's no batteries-included way to manage Clojure projects, the community has put together a lot of great tools and guides the cover all the bases. The community seems to be converging around the [official Clojure CLI](https://clojure.org/guides/deps_and_cli) and associated tooling
as the preferred way to manage Clojure projects. It's extremely well designed, like most things Clojure, but, also like most things Clojure, it's very bare-bones. It's _not_ an all-in-one command-line utility you can use to manage your whole project, like the angular or rails CLIs (which I didn't appreciate nearly enough in my former life 😢). You need to configure the Clojure CLI itself for it to be useful, but luckily that's really straightforward to do. What follows are the steps I did to make a new skeleton command-line app in Clojure. It follows the steps from [this great guide](https://github.com/BrunoBonacci/graalvm-clojure/blob/master/doc/clojure-graalvm-native-binary.md), but I included the actual commands here because I use the Clojure CLI (`clj`) instead of `lein` to run things.

## 1. Make a new Clojure project

I use Sean Corfield's [`clj-new`](https://github.com/seancorfield/clj-new) project to initialize new Clojure projects. Install it for your environment according the instructions in his README, then run `clj -X:new :template app :name kiramclean/test-cli` to generate a new Clojure project (but replace `kiramclean/test-cli` with `<your-name>/<project-name>`).

## 2. Make an uberjar

The app template from `clj-new` includes a default namespace that just prints "Hello, World!" and an alias for building an uberjar, which is just a java app that includes all the dependencies it needs so it can run on its own without worrying about what's installed or not on the host.

Run `clj -X:uberjar` in your app directory, which should build a `test-cli.jar`. You can run your app now like `java -jar test-cli.jar`, and cry about how slow it is.

## 3. Make a standalone executable with GraalVM

Now you can use GraalVM to turn your uberjar into a snappy CLI. Run this magic command (note the names — the `-jar` option is the location of the uberjar you just made and `-H:Name=` is the name of your future executable).

```
native-image --report-unsupported-elements-at-runtime \
             --initialize-at-build-time \
             --no-server \
             --no-fallback \
             -jar test-cli.jar \
             -H:Name=test-cli
```

It takes a while on my machine for that to finish, but once it does you're good to go! You should have a standalone executable now that you can run from your terminal, which executes your Clojure app natively, and is way faster than running the jar on a regular JVM! Cool.

```
❯ time java -jar test-cli.jar
Hello, World!
java -jar test-cli.jar  4.31s user 1.10s system 113% cpu 4.792 total

❯ time ./test-cli
Hello, World!
./test-cli  0.05s user 0.01s system 70% cpu 0.086 total
```

## That's all for now

I made an executable `bin/build` script in my project with this in it to make the two steps above simpler:

```
#!/bin/bash

echo "Build jar..."

clj -X:uberjar

echo "Nativize it..."

native-image --report-unsupported-elements-at-runtime \
             --initialize-at-build-time \
             --no-server \
             --no-fallback \
             -jar test-cli.jar \
             -H:Name=./test-cli

echo "Success! Good to run ./test-cli"
```

The next thing I want to do is add some command-line options and a help menu, but this is already getting kind of long, so I'll leave it here for now. Happy coding 🙂
