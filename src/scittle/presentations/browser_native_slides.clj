^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Building Browser-Native Presentations with Scittle"
         :quarto {:author [:burinc]
                  :description "Create interactive presentation systems without build tools using Scittle and Reagent"
                  :type :post
                  :date "2025-11-04"
                  :category :libs
                  :tags [:scittle
                         :clojurescript
                         :reagent
                         :presentations
                         :no-build]
                  :keywords [:scittle
                             :browser
                             :presentations
                             :hot-reload]}}}

(ns scittle.presentations.browser-native-slides
  (:require [scicloj.kindly.v4.kind :as kind]))

;; ## About This Project

;; This presentation system was born out of a practical need while preparing technical talks for the Clojure community. Like many developers, I found myself building various tools and demos to showcase Clojure/ClojureScript concepts effectively. During this process, I discovered that Scittle's browser-native approach could eliminate the typical build complexity that often holds people back from creating interactive presentations.

;; What started as a personal tool for my own presentations evolved into something I felt compelled to share. The Clojure community has given me so much through open-source libraries, helpful discussions, and shared knowledge. This project is my way of contributing back - offering a simple, accessible way for anyone to create beautiful, interactive presentations without fighting with build tools.

;; Whether you're preparing for a conference talk, a meetup presentation, or just want to create interactive documentation, I hope these examples inspire you and save you the hours I spent figuring out the details. The source code is fully available for you to learn from, adapt, and improve upon.

;; Let's dive into why this approach matters and how you can use it.


;; # Building Browser-Native Presentations with Scittle

;; ## The Problem

;; You want to create a technical presentation with live code examples.
;; Traditional ClojureScript requires:
;; - shadow-cljs or Figwheel configuration
;; - Build tool setup and dependencies
;; - Webpack or bundler configuration
;; - Minutes of compile time on every change
;; - Complex deployment process
;;
;; What if you could skip all that and just write ClojureScript in an HTML file?
;; **No build step. No configuration. Just pure browser-native code.**
;;
;; That's [Scittle](https://github.com/babashka/scittle).

;; ## What is Scittle?

;; Scittle is a ClojureScript interpreter that runs entirely in the browser.
;; Created by [Michiel Borkent](https://github.com/borkdude) (the genius behind Babashka),
;; it brings the simplicity of scripting to ClojureScript development.

;; **Think of it like this:**
;;
;; JavaScript has script tags that run immediately. Python has interactive notebooks.
;; Clojure has the REPL. **Scittle gives ClojureScript the same instant, no-build experience.**
;;
;; Write code, refresh browser, see results. No compilation. No bundling. Pure interpretation.

;; ## The Simplest Presentation

^:kindly/hide-code
(kind/code (slurp "src/scittle/presentations/minimal.cljs"))

;; ### See It Live

(kind/hiccup
 [:div#minimal-demo {:style {:min-height "500px"}}
  [:script {:type "application/x-scittle"
            :src "minimal.cljs"}]])

;; ## How It Works

;; The flow is straightforward:
;; 1. Load Scittle.js from a CDN
;; 2. Load the Reagent plugin
;; 3. Write ClojureScript in a script tag with type="application/x-scittle"
;; 4. Scittle interprets and executes the code
;; 5. Reagent renders React components
;; 6. Everything updates live in the browser!

;; Visually, the architecture looks like this:

(kind/mermaid
 "flowchart LR
    A[HTML] --> B[Scittle]
    B --> C[Reagent]
    C --> D[DOM]")

;; **Key insight:** Scittle evaluates ClojureScript directly in the browser.
;; No compilation. No bundling. Just interpretation.

;; ## Adding Keyboard Navigation

^:kindly/hide-code
(kind/code (slurp "src/scittle/presentations/keyboard_nav.cljs"))

(kind/hiccup
 [:div#keyboard-demo {:style {:min-height "550px"}}
  [:script {:type "application/x-scittle"
            :src "keyboard_nav.cljs"}]])

;; ## Thumbnail Overview

;; A professional touch for your presentations - let your audience see the big picture.
;; Thumbnails provide visual context and make navigation instant.
;;
;; **Why thumbnails matter:**
;; - Quick overview of all slides
;; - Jump to any section instantly
;; - See where you are in the presentation
;; - Makes long presentations more navigable

^:kindly/hide-code
(kind/code (slurp "src/scittle/presentations/thumbnails.cljs"))

;; ### Try the Thumbnail View

(kind/hiccup
 [:div#thumbnails-demo {:style {:min-height "550px"}}
  [:script {:type "application/x-scittle"
            :src "thumbnails.cljs"}]])

;; ## Live Code Editor

;; The killer feature: **evaluating code directly in your presentation**.
;; Scittle provides `scittle.core.eval_string` - a function that evaluates
;; ClojureScript code at runtime, in the browser.
;;
;; **Perfect for:**
;; - Interactive tutorials
;; - Live coding demonstrations
;; - Teaching ClojureScript
;; - API documentation with runnable examples
;; - Conference talks with audience participation

^:kindly/hide-code
(kind/code (slurp "src/scittle/presentations/code_editor.cljs"))

;; ### Try It Yourself

;; Type any ClojureScript code and click "Evaluate" to see the result!

(kind/hiccup
 [:div#code-editor-demo {:style {:min-height "700px"}}
  [:script {:type "application/x-scittle"
            :src "code_editor.cljs"}]])

;; ## Shadow-cljs vs Scittle: Choosing the Right Tool

;; Both are excellent tools, but they serve different purposes.
;; Choose based on your project's needs:

(kind/hiccup
 [:div.overflow-x-auto
  [:table.table.table-zebra.w-full
   [:thead
    [:tr
     [:th "Feature"]
     [:th "Shadow-cljs"]
     [:th "Scittle"]]]
   [:tbody
    [:tr
     [:td [:strong "Compilation"]]
     [:td "Ahead-of-time (AOT)"]
     [:td "Just-in-time (JIT)"]]
    [:tr
     [:td [:strong "Setup Required"]]
     [:td "Yes - deps.edn, config"]
     [:td "No - just HTML + CDN"]]
    [:tr
     [:td [:strong "Build Time"]]
     [:td "Seconds to minutes"]
     [:td "Instant (no build)"]]
    [:tr
     [:td [:strong "Bundle Size"]]
     [:td "Optimized, tree-shaken"]
     [:td "~1MB runtime + code"]]
    [:tr
     [:td [:strong "Performance"]]
     [:td "Fastest (compiled)"]
     [:td "Fast (interpreted)"]]
    [:tr
     [:td [:strong "Hot Reload"]]
     [:td "Yes (via tooling)"]
     [:td "Just refresh browser"]]
    [:tr
     [:td [:strong "npm Integration"]]
     [:td "Full support"]
     [:td "Limited (CDN only)"]]
    [:tr
     [:td [:strong "Code Splitting"]]
     [:td "Yes"]
     [:td "Manual only"]]
    [:tr
     [:td [:strong "Best For"]]
     [:td "Production apps, SPAs"]
     [:td "Prototypes, demos, docs"]]
    [:tr
     [:td [:strong "Learning Curve"]]
     [:td "Moderate"]
     [:td "Very low"]]
    [:tr
     [:td [:strong "Deployment"]]
     [:td "Build + host static files"]
     [:td "Copy HTML file anywhere"]]]]])

;; ### When to Use Scittle

;; **Perfect for:**
;; - Technical presentations with live demos
;; - Interactive documentation
;; - Quick prototypes and experiments
;; - Teaching ClojureScript to beginners
;; - Shareable code examples (CodePen style)
;; - Single-file applications

;; ### When to Use Shadow-cljs

;; **Better for:**
;; - Production web applications
;; - Large codebases with many dependencies
;; - Performance-critical applications
;; - Team projects requiring build tooling
;; - Apps needing npm package integration
;; - Progressive web apps (PWAs)

;; ## Best Practices & Tips

;; ### Structure Your Code

;; Keep your Scittle scripts organized and modular:

(kind/code
 ";; Good: Separate concerns
(ns my-app.core)

(defn render-slide [idx]
  ...)

(defn navigation-buttons []
  ...)

(defn main []
  (render-slide 0)
  (setup-keyboard-navigation!))")

;; ### Leverage CDN Libraries

;; Scittle has plugins for popular libraries:
;; - **Reagent** - React wrapper (`scittle.reagent`)
;; - **Re-frame** - State management (`scittle.re-frame`)
;; - **Promesa** - Promises (`scittle.promesa`)
;; - **ClojureScript.test** - Testing (`scittle.cljs-test`)

;; ### Use Browser DevTools

;; Scittle code is debuggable in Chrome/Firefox DevTools:
;; - Set breakpoints in your ClojureScript code
;; - Inspect atoms and state in the console
;; - Use `js/console.log` for quick debugging
;; - Check Network tab to ensure scripts load correctly

;; ### Performance Considerations

;; **Keep it snappy:**
;; - Minimize initial script size (under 100KB recommended)
;; - Use lazy loading for heavy computations
;; - Cache expensive calculations in atoms
;; - Consider memoization for pure functions
;; - Test on slower devices/connections

;; ## Common Gotchas & Solutions

;; ### 1. Namespace Loading Order

;; **Problem:** Trying to use a namespace before it's loaded.

(kind/code
 ";; ❌ Won't work
(ns my-app.core
  (:require [my-app.utils :as utils]))

;; ✅ External file must load first
;; In HTML:
;; <script type=\"application/x-scittle\" src=\"utils.cljs\"></script>
;; <script type=\"application/x-scittle\" src=\"core.cljs\"></script>")

;; ### 2. Async Operations

;; **Problem:** Scittle scripts load asynchronously.

(kind/code
 ";; ❌ Might fail
(js/document.addEventListener \"DOMContentLoaded\"
  (fn [] (start-app!)))

;; ✅ Better: Use window load event
(js/window.addEventListener \"load\"
  (fn [] (start-app!)))")

;; ### 3. CORS Issues

;; **Problem:** Loading scripts from different domains.
;;
;; **Solution:** Either:
;; - Host all files on the same domain
;; - Use a CORS proxy for development
;; - Configure server to allow CORS headers

;; ### 4. Not All npm Packages Work

;; Scittle can't use npm packages directly. Workarounds:
;; - Use CDN versions (unpkg.com, jsdelivr.com)
;; - Look for Scittle-compatible plugins
;; - Use browser-native APIs instead

;; ### 5. Error Messages

;; Scittle error messages can be cryptic. Tips:
;; - Check browser console for stack traces
;; - Use `(js/console.log)` liberally during development
;; - Test small code snippets in isolation
;; - Verify syntax with a Clojure linter

;; ## Resources & Next Steps

;; ### Official Documentation

;; - **Scittle Repository**: [github.com/babashka/scittle](https://github.com/babashka/scittle)
;; - **Scittle Plugins**: Available for Reagent, Re-frame, Promesa, and more
;; - **Babashka Book**: [book.babashka.org](https://book.babashka.org) - Covers Scittle usage
;; - **ClojureScript**: [clojurescript.org](https://clojurescript.org)

;; ### Example Projects

;; - **Maria.cloud**: Interactive ClojureScript learning platform
;; - **4Clojure**: Problem-solving platform using browser ClojureScript
;; - **Klipse**: Interactive code snippets for blogs
;; - **This Presentation!**: Available on GitHub

;; ### Community & Support

;; - **Clojurians Slack**: #scittle and #clojurescript channels
;; - **ClojureVerse**: Community forum for discussions
;; - **r/Clojure**: Reddit community
;; - **Clojure Mailing List**: Active community discussions

;; ### Try It Today!

;; Start building your first Scittle presentation:

(kind/code
 "<!DOCTYPE html>
<html>
<head>
  <script src=\"https://cdn.jsdelivr.net/npm/scittle@0.6.15/dist/scittle.js\"></script>
  <script src=\"https://cdn.jsdelivr.net/npm/scittle@0.6.15/dist/scittle.reagent.js\"></script>
</head>
<body>
  <div id=\"app\"></div>
  <script type=\"application/x-scittle\">
    (ns my-app.core
      (:require [reagent.core :as r]
                [reagent.dom :as rdom]))

    (defn app []
      [:div [:h1 \"Hello from Scittle!\"]])

    (rdom/render [app] (js/document.getElementById \"app\"))
  </script>
</body>
</html>")

;; ## Conclusion

;; **Scittle brings the joy back to ClojureScript development.**
;;
;; No more waiting for builds. No more configuration headaches.
;; Just open an HTML file and start coding.
;;
;; **Perfect for:**
;; - Learning ClojureScript
;; - Creating interactive presentations
;; - Building quick prototypes
;; - Teaching and documentation
;;
;; **Remember:**
;; - Use Shadow-cljs for production applications
;; - Use Scittle for everything else
;;
;; **Ready to build browser-native presentations?**
;; Fork this presentation and make it your own!
;;
;; ---
;;
;; **Thank you!** Questions? Find me on Clojurians Slack.
;;
;; *Built with Scittle, Reagent, and ❤️ for functional programming*
