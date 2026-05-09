^{:kindly/hide-code true
  :clay             {:title  "Playing with SVGs with hiccup and clay"
                     :quarto {:author   [:llyons]
                              :type     :post
                              :date     "2026-05-09"
                              :category :clojure
                              :tags     [:art :svg :hiccup :clay]}}}

(ns art.gen-svg.spirals)

; Welcome!  
; Today we are going to build an svg starting from a simple polygon.
^:kind/hiccup
[:svg {:width "100%"
       :height "600"}
 [:polygon {:points "-10 -10 -10 10  10 10 10 -10"}]]

; We probably want the rectange in the center of the page.
; To accomplish this, we can use a viewbox:
^:kind/hiccup
[:svg {:width "100%"
       :height "600"
       :viewBox "-50 -50 100 100"}
 [:polygon {:points "-10 -10 -10 10  10 10 10 -10"}]]

; Now that there is a base polygon, we can rotate and/or scale it.  
; `:transform` is an svg attribute that we can pass to hiccup via a string.
^:kind/hiccup
[:svg {:width "100%"
       :height "600"
       :viewBox "-50 -50 100 100"}
 [:g {:transform (str "rotate(" 20 ") scale(" 3 ")")}
  [:polygon {:points "-10 -10 -10 10  10 10 10 -10"}]]]

; We'll want only the outline, so we will set the fill to none and add a stroke.
^:kind/hiccup
[:svg {:width "100%"
       :height "600"
       :viewBox "-50 -50 100 100"}
 [:g {:fill "none"
      :stroke "black"
      :stroke-width "0.5"}
  [:g {:transform (str "rotate(" 20 ") scale(" 3 ")")}
   [:polygon {:points "-10 -10 -10 10  10 10 10 -10"}]]]]

; Now lets do that many times over!
^:kind/hiccup
[:svg {:width "100%"
       :height "600"
       :viewBox "-50 -50 100 100"}
 [:g {:fill "none"
      :stroke "black"
      :stroke-width "0.5"}
  (for [x (range 36)]
    [:g {:transform (str "rotate(" (* 2 x) ") scale(" (* 0.25 x) ")")}
     [:polygon {:points "-10 -10 -10 10  10 10 10 -10"}]])]]

; We can invert the colors by adding a background.  
; To add the background, we will add one rectangle that covers the whole page, before our iteration.
^:kind/hiccup
[:svg {:width "100%"
       :height "600"
       :viewBox "-50 -50 100 100"}
 [:rect {:x -50
         :y -50
         :width 100
         :height 100
         :fill "black"}]
 [:g {:fill "none"
      :stroke "white"
      :stroke-width "0.5"}
  (for [x (range 36)]
    [:g {:transform (str "rotate(" (* 2 x) ") scale(" (* 0.25 x) ")")}
     [:polygon {:points "-10 -10 -10 10  10 10 10 -10"}]])]]