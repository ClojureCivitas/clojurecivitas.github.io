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


