^{:kindly/hide-code true
  :clay             {:title  "Beginning to build a browser game"
                     :quarto {:author   [:mattkleinsmith]
                              :category :clojure
                              :tags     [:game :browser]}}}
(ns games.beginning-to-build-a-browser-game)

; Today we are pairing. We are messing around with Clojure.

; Instead of HTML tags, we're using Clojure data structures to say what we mean.
; With tags, we need to close them. It's nice to not need to close them.
(def thing [:svg {:style {:border "1px solid red"}}
            [:rect {:width 50 :height 50 :stroke :blue}]])

; This tells Clay (our renderer) to render the HTML.
^:kind/hiccup
thing

^:kind/hiccup
[:div [:button "Click me"] thing]

; Let's create a function that parametrizes thing
(defn create-thing [x y]
  ^:kind/hiccup
  [:svg {:style {:border "1px solid red"}}
   [:rect {:width 50 :height 50 :stroke :blue :x x :y y}]])
(create-thing 50 50)

; Let's create the world
(def world (atom {}))

; Let's add something to the world
(swap! world assoc :player {:x 75 :y 75})

; Wait, did `assoc` work? Yes.
@world

(defn render-the-world [world]
  ^:kind/hiccup
  [:svg {:style {:border "1px solid red"}}
   (for [[_entity-id {:keys [x y]}] world]
     [:rect {:width 50 :height 50 :stroke :blue :x x :y y}])])

(render-the-world @world)

; An enemy
(swap! world assoc :enemy {:x 130 :y 75})

; Show the enemy
(render-the-world @world)

; Let's look different from our enemy
(defn render-the-world [world]
  ^:kind/hiccup
  [:svg {:style {:border "1px solid black"}}
   (for [[entity-id {:keys [x y]}] world]
     (if (= entity-id :player)
       [:rect {:width 50 :height 50 :fill "#62b133" :x x :y y}]
       [:circle {:r 25 :fill "#5880d9" :cx x :cy y}]))])
(render-the-world @world)

; It was nice to work with Clojure syntax as opposed to JavaScript.
; The only boilerplate I/we felt was the Clojure colons: ":"
; The code is nice to read. I'm just looking at what I meant to say.
; The things I care about are listed. There they are.

