^{:kindly/hide-code true
  :clay             {:title  "Bioscoop, a DSL for FFmpeg"
                     :quarto {:author   [:danielsz]
                              :description "Quickstart for Clojurians"
                              :draft false
                              :type     :post
                              :date     "2025-10-27"
                              :category :clojure
                              :tags     [:creative_coding]}}}
(ns bioscoop.quickstart
  (:require [scicloj.kindly.v4.kind :as kind]
            [bioscoop.quickstart :as qs]))

;; This document serves as a quickstart for the Bioscoop library. It is geared toward Clojurians as it assumes knowledge of namespaces, the REPL, etc. 

;; Bioscoop is a DSL to program FFmpeg's filtergraphs. Start by requiring the Bioscoop macros and the `bioscoop.built-in` namespace.

(require '[bioscoop.macro :refer [bioscoop defgraph]]
         '[bioscoop.built-in :refer [help]])

;; Let's build a simple animation. We will start with a black background. The _color_ filter provides that.

(bioscoop (color))

;; Note that we didn't require _color_. It is available thanks to the `bioscoop.built_in` namespace, alongside all of the other filters.

;; If you need help with the parameters that a filter accepts, type in the REPL:

^:kind/hidden
(help "color")

;; Bioscoop recognizes the same parameters as FFmpeg, with a caveat. In FFmpeg, some filters define the same parameter twice, once fully spelled out, and once in shorthand form (as if the syntax wasn't terse enough). For example, _w_ and _width_. Bioscoop doesn't accept the shorthand version *by design*.

;; On top of that black canvas, we are going to draw text twice per second, positioned randomly. In order to achieve that, we will refer the _x_ and _y_ coordinates to an expression instead of fixed values. 

(bioscoop (drawtext {:text "bioscoop" :fontcolor "white" :x "'mod(random(0)*10000,W-tw)'" :y "'mod(random(1)*10000,H-th)'"}))

;; The bioscoop macro accepts a subset of Clojure. You can use _let_ bindings and all of _clojure.core_. It returns a data structure, the internal representation of a filtergraph. 

;; Putting everything together, we can write:

(bioscoop (chain (color {:duration 5})
                 (drawtext {:text "bioscoop"
                            :fontcolor "white"
                            :x "'mod(random(0)*10000,W-tw)'"
                            :y "'mod(random(1)*10000,H-th)'"})
                 (fps {:fps "2"})))

;; Or, if we need a handle on the filtergraph, we can use _defgraph_ which will intern a Var with a name of our choosing.

(defgraph filtergraph (chain (color {:duration 5})
                             (drawtext {:text "bioscoop"
                                        :x "'mod(random(0)*10000,W-tw)'"
                                        :y "'mod(random(1)*10000,H-th)'"})
                             (fps {:fps "2"})))

;; To convert the internal data structure, or the handle, back to a filtergraph, we use _to-ffmpeg_:

(require '[bioscoop.render :refer [to-ffmpeg]])
(to-ffmpeg filtergraph)

;; Finally, the filtergraph is ready to be fed to FFmpeg. While there is a helper in Bioscoop, how and where you run the FFmpeg command is entirely up to you. For example,  you can display the animation in the terminal with `ffplay -f lavfi -i` followed by the filtergraph.
;;
;; *Note:* FFplay is a companion player that is most often installed together with FFMpeg.

;; ![Bioscoop](bioscoop.gif)
;;
;; And voilà!

;; Content creators often reuse a preamble in their broadcasts. This use case is an excellent opportunity to demonstrate how Bioscoop provides means of composition.
;; It also demonstrates how you reuse definitions across namespace boundaries.

^:kind/hidden
(in-ns 'bioscoop.masterpiece)
^{:kindly/hide-code true :kindly/kind :kind/hidden}
(clojure.core/refer-clojure)
(require '[bioscoop.quickstart :refer [filtergraph]]
         '[bioscoop.macro :refer [bioscoop defgraph]]
         '[bioscoop.built-in])

;; We will simulate the actual video content with a FFMpeg dummy filter, _testsrc_. The intro is our filtergraph referred to in the `bioscoop.quickstart` namespace.

(defgraph masterpiece (testsrc))
(bioscoop (compose [filtergraph ["intro"]]
                   [masterpiece ["masterpiece"]]
                   [["intro"] ["masterpiece"] (concat {:n 2})]))

;; ![Masterpiece](masterpiece.gif)
;;

;; And voilà, again!
;;
;; What are you going to make? We'll be happy to link to **your** masterpiece in Bioscoop's [gallery](https://github.com/danielsz/bioscoop#gallery). 



