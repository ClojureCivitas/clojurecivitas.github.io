(ns learning.turtle.intro)

;; # Learning Clojure Programming in a Beginner-Friendly Way
;;
;; Tim and I were revisiting thoughts about [Power Turtle](https://power-turtle.com/),
;; which we created and [talked about in 2017](https://youtu.be/Y3-53JoGPE4?si=PPwOxwh63Rb2qse0).
;;
;; * what makes it work
;;   * interactivity
;;     * Japanese colleague (Yoshikatsu)'s uncle's friend is a teacher. Saw that boys got really engaged by trying to crash/push things
;;       * Yoshikatsu's observation: learning is through hands-on playing
;;       * Yoshikatsu is a successful game app maker
;;       * Observation of the difference in what engaged boys vs. girls (girls liked the storytelling aspect in an exercise to create animation)
;;     * How do you ensure an interactive environment is set up to support the different avenues of interest
;;       * storytelling; extreme pushing of boundaries; etc?
;;     * Reflection: it's easy to be humbled & surprised at what aspects of a task people latch onto
;;       * This is the value of the open-ended nature of Logo's interactive environment
;;   * visual
;;     * A picture is worth 1000 words
;; * notebook style of programming
;;   * worth thinking of this as different from REPL (even if similar)
;;   * examples
;;     * Clay
;;     * Maria Cloud
;;   * there is a storytelling aspect by virtue of there being text
;;   * regarding clojure-turtle: the explanatory text lends itself well to a notebook env
;;   * open question: can a notebook handle animation?
;;   * downsides:
;;     * more specific
;;     * more complicated (setup & use) - these could be overcome
;; * notebook style vs. Blockly style
;;   * Neil Fraser - concern about Blockly become a walled garden unwittingly
;;     * did a lot with Blockly Games to help learners to escape the walled garden and learn more of other types of programming
;;   * In my own words, block-style coding is like a crutch: helps, but limits getting better
;;   * Observations on Maria Cloud
;;     - not very well used (is that true?)
;;     - can't use your own text editor
;;       - whereas Clay lets you
;;       - although for a beginner, maybe that's okay/good
;;   * generally: UI Block coding / Notebook environment vs. REPL/text coding
;;     - if in a beginner-friendly environment (block coding / notebook), you still need to enable users to progress to text coding
;; * Notebook + Turtle
;;   * nowhere in the wild that combines turtle with notebooks
;;   * Pros & Cons of options
;;     * Maria Cloud - output webpage is entirely editable and executable, but not sure how to integrate new deps into env
;;     * Clay - setup requires non-beginner knowledge (eng-level), output is static
;;     * Power Turtle - point of comparison: no setup, no storytelling aspect
;;   * Maria Cloud option
;;     * can we include power-turtle editing environments' deps into project?
;;     * can we include localized (translated) form names in your native language, ex: using cban?
;;       * can we adapat cban to be a plugin for deps.edn as we did for Leiningen (lein-cban)?
;;     * Maria Cloud makes it easy to create
;;     * Maria Cloud doesn't distinguish between creating & publishing & editing mode
;; * Lessons

* What are we doing here?
  - Premise
  - Objective / encouragement of exploratory attitude
    * secretly in service of effective learning, but don't tell the kids

* Syntax

* Basic commands
  - notion of pen: penup pendown
  - walking while drawing with pen down: fd rt lt bk
    * start rt & lt angles with 90
  - screen commands: clear home
  - at this point, the student has full control over the canvas equivalent to an Etch a Sketch
  - then explore things beyond Etch a Sketch -> basic math
  - in fact, give students an Etch a Sketch to play with in case they have not done so before
    * this helps break down the distinction between physical world & computer
    * important point in the whole Mindstorms theorization of turtle's intent & scope
  - explore
    * draw a rectangle, square
    * what else can you draw?
  - basic math
    * rt lt angles 180, 270, 360
    * what if you want turn only halfway right?
    * how did you compute that?
    * what if you want to turn just 1/4 or 1/8 the way right?
      - secretly, now we have non-integer numbers, and that's less fun and harder to figure out, assuming 45 wasn't already tricky for some
    * can you get the computer to compute that for you?
    * syntax for division
    * syntax for addition, subtraction, multiplication
      - although these are not yet super motivated in an obvious way
  - explore
    * draw other shapes
      * show an image and have them match it. kind of like Tangrams, but super simple
      * first: right triangle
      * next: diamond (square on its side) - Western students understand "baseball diamond"
      * universal (Western-oriented?) house shape
      * any other universal pictures / icons that are recognizable
    * introduce arc
      * draw a circle
      * draw a semicircle
      * draw an "eye" (2 touching quarter circles)
    * open explore more shapes
    * locale specific shapes
      * Tamil - kolam
      * dreamcatchers
      * letters in the language (ex: first letter of your name)
      * etc.

* Repetition

* Functions

* HOFs
  * repeat

* Recursion
  * Stick, twig, branch, tree exercise



