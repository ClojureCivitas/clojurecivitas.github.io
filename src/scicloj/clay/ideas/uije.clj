(ns scicloj.clay.ideas.uije
  {:clay {:title          "Clay dashboard as extended hiccup"
          :quarto         {:author      [:timothypratley]
                           :description "Exploring how Clay could help host a dashboard DSL."
                           :abstract    "Hiccup is the ultimate UI data specification."
                           :date        "2026-10-09"
                           :type        :post
                           :category    :clay
                           :tags        [:clay :dashboard :layout]}}}
  (:require [clojure.walk :as walk]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.kindly.v4.api :as kindly]
            [scicloj.plotje.api :as pj]))

;; Uije is not a library, it's a belief system.

(let [size 400
      dark "darkgreen"
      light "lightblue"
      dot-r 0.35
      r  50
      r2 (/ r 2)
      dr (* dot-r r2)
      d  (str "M 50,0"
              " A " r  "," r  " 0 0 1 50,100"  ; right semicircle
              " A " r2 "," r2 " 0 0 1 50,50"   ; bottom lobe (left half)
              " A " r2 "," r2 " 0 0 0 50,0"    ; top lobe (right half)
              " Z")]
  ^:kind/hiccup
  ^{:kindly/options {:static true}}
  [:svg {:class "plotje-plot"
         :viewBox "-50 -50 200 200"
         :width size
         :height size
         :role "img"
         :aria-label "yin-yang"}
   [:g {:stroke dark
        :stroke-width 2}
    [:circle {:cx 50 :cy 50 :r r :fill dark}]
    [:path {:d d :fill light}]
    [:circle {:cx 50 :cy 25 :r dr :fill light}]
    [:circle {:cx 50 :cy 75 :r dr :fill dark}]]])

;; Hiccup is the ultimate UI data specification.
;; Instead of inventing yet another UI DSL,
;; can we embrace the one we have?

^:kind/hiccup [:style ".plotje-plot {width:100%; height:auto}"]

;; Dashboards are data representations of a UI.

(def my-dashboard
  [:ui {:state {:data1 {:a [1 2 3]
                        :b [4 5 6]}
                :data2 {:c ["A" "B" "C"]
                        :d [7 8 9]}}}
   [:row [:plot :data1] [:plot :data2]]])

;; Dashboards reify their widgets into a UI.
;; But we don't need to know HTML to make an interesting UI,
;; so long as we have primitives like `:row` and `:plot`.

;; Kindly is an existing way to turn data into visualizations.
;; Especially `:kind/hiccup`, which allow nesting of hiccup and visualizations:

^:kind/hiccup
[:div {:style {:display :grid
               :grid-template-columns "1fr 1fr"}}
 [:style ".plotje-plot {width:100%; height:auto}"]
 (pj/plot {:a [1 2 3]
           :b [4 5 6]})
 (pj/plot {:a [1 2 3]
           :b [4 5 6]})]

;; But `pj/plot` is a function application that needs to be abstracted into data, into `:plot`.

(def my-chart
  [:plot {:x [1 2 3 4 5]
          :y [9 8 1 2 6]}])

;; We can process hiccup expressions with special tags like `:plot` into visualizations:

(defmulti uiup first)
(defmethod uiup :default [x] x)
(defmethod uiup :plot [[_ arg]]
  (pj/pose arg))

;; Let's make a helper to apply all the abstracted visualizations:

(defn uije [ui]
  (walk/postwalk
   (fn [x]
     (if (vector? x)
       (uiup x)
       x))
   (kind/hiccup ui)))

;; For now we will use this overly simple walk helper for illustrative purposes.
;; Walking hiccup needs a bit more care than this.
;; Clay has a reference implementation that handles some important edge cases.

;; Let's render our UI.

(uije my-chart)

;; Amazing! We got a chart.
;; The important point is that `my-chart` is purely data.
;; Our `uije` helper interpreted it into calling Plotje to make the visualization.
;; In a way this is considered a Domain Specific Language.

;; Layout can also be abstracted:

(defmethod uiup :col [[_ & xs]]
  (-> [:div {:style {:display :flex, :flex-direction :column}}]
      (into xs)
      (kind/hiccup)))

(defmethod uiup :row [[_ & xs]]
  (-> [:div {:style {:display :flex}}]
      (into xs)
      (kind/hiccup)))

(def my-simple-dashboard
  [:col
   [:h3 "Plots, in a custom layout"]
   [:plot {:x [1 2 3]
           :y [9 5 7]}]
   [:row
    [:plot {:a [1 2 3]
            :b [4 5 6]}]
    [:plot {:c [1 2 3]
            :d [11.1 8.2 9.2]}]]])

(uije my-simple-dashboard)

;; That's a concise UI description from just a few hiccup extensions.

;; Notice that we can use `:h3` to create headings?
;; Because everything is hiccup, all of HTML is available to you,
;; if you wish to use it.

;; But wait, there's more!

(def my-simple-dashboard2
  [:col
   [:h3 "My super dashboard"]
   [:row
    [:plot {:a [1 2 3]
            :b [4 5 6]}]
    ^:kind/table
    {:features ["Plots!" "Kinds!" "Layout!"]
     :value [100 100 100]}]])

(uije my-simple-dashboard2)

;; We didn't have to do anything to support tables...
;; Kindly already has a data representation for them.
;; Clay already renders many, many kinds that are useful.
;; It just doesn't have one for Plotje yet.

;; In a way, an alternative to having the `uiup` multimethod
;; would be to have a way to extensibly add kinds.
;; Currently this is possible, but undocumented and tedious.

;; The hiccup style `[:plot ..]` may be preferable to some users.
;; Infact we might choose to provide similar mappings for all kinds,
;; something like `[:kind/table ...]`

(defmethod uiup :default [[tag data :as v]]
  (if (kindly/known-kinds tag)
    (kindly/attach-kind-to-value data tag)
    v))

(def my-simple-dashboard3
  [:col
   [:h3 "Hiccup style kind usage"]
   [:kind/graphviz
    "digraph D {
        A [shape=diamond]
        B [shape=box]
        C [shape=circle]
        A -> B
        A -> C
        A -> D
      }"]])

(uije my-simple-dashboard3)

;; Great! We can treat kinds as hiccup tags.

;; Now it makes more sense to add Plotje as a kind:

(defmethod uiup :kind/plotje [[_ arg]]
  (pj/pose arg))

(uije [:kind/plotje {:x [1 2 3]
                     :y [9 5 7]}])

;; Perhaps `:col` and `:row` should also be `:kind/col` and `:kind/row`.
;; I'm not sure it matters much, but we might imagine using them
;; outside of a dashboard UI.
;; A Kindly UI specification may wish to be more explicit (`:kind/row`).
;; A more opinionated DSL may prefer to be concise (`:row`).

;; Dashboards have data bindings.

(def my-dashboard4
  [:kind/ui {:state {:data1 {:x [1 2 3]
                             :y [9 5 7]}}}
   [:h3 "My fancy dashboard"]
   [:kind/plotje :data1]])

;; Handling bindings requires making a more complete walk function.
;; Not only bindings, but server/client spanning bindings.
;; We can imagine achieving this with Datastar combined with Clay server features,
;; as outlined in [Serving webapps from your REPL](/scicloj/clay/webserver/datastar.html).
;; For now let's only observe that conceptually, it's possible.

;; Thinking about bindings brings us back to an interesting symmetry...
;; `:kind/ui` is... a Kindly kind.
;; The function `uije` is an implementation to render `:kind/ui`.
;; In a way, we already have `uije`.
;; It is the hiccup tree walker that is part of Clay.

;; Let's revisit the features we discussed, asking what is required to access them:

;; 1. *Installing custom kinds* is currently possible through a combination of
;;    `scicloj.clay.v2.prepare/add-preparer!` and `scicloj.kindly-advice.v1.api/add-advisor!`.
;;    Undocumented, but possible.
;;    We should document the existing way,
;;    and/or provide a more convenient wrapper to install both in one step.
;;    A guiding question is, can the Plotje library install a `:kind/plotje`?
;;    Yes, it could, as a side-effect of loading some initialization namespace or function.
;;    Is it convenient? Not yet.
;; 2. *Handling of kind tags in hiccup expressions* would be a new feature.
;;    I propose that we add this feature to Clay as a convenience intended for UI creation.
;; 3. *`:kind/row` and `:kind/col`* would be new features.
;;    I think we should start trying them and see what limitations we hit.
;; 4. *Server/client bindings* needs further experimentation.
;;    Here be dragons.
