^{:kindly/hide-code true
  :clay             {:title  "Beginning to build a browser game"
                     :quarto {:author   [:mattkleinsmith]
                              :category :clojure
                              :type     :post
                              :date     "2025-07-09"
                              :tags     [:game :browser]}}}
(ns games.beginning-to-build-a-browser-game)

; I'm new to Clojure. I asked my new friend Timothy how to make a square appear on the screen:

^{:kindly/hide-code true}
^:kind/code
(str "^:kind/hiccup
[:svg [:rect {:width 50 :height 50}]]")

^{:kindly/hide-code true}
^:kind/hiccup
[:svg {:style {:width 50 :height 50}}
 [:rect {:width 50 :height 50}]]

; Compared to HTML:

^{:kindly/hide-code true}
^:kind/code
(str "<svg><rect width=" 50 " height=" 50 " /></svg>")


; It's nice to not have to close tags, but I'm not sure about the colons, nor the dependencies.

; Dependencies aside, we are this square. And we want to be able to move. Timothy, how can I move?

; We'll separate the position data from the rendering:

(def world (atom {:player {:x 75 :y 75}}))

(defn render [world]
  ^:kind/hiccup
  [:svg {:style {:border "1px solid black"}}
   (for [[_entity-id {:keys [x y]}] world]
     [:rect {:width 50 :height 50 :x x :y y}])])

(render @world)

; We'll add an enemy to see how to work with `atom`:
(swap! world assoc :enemy {:x 130 :y 75})
(render @world)

; Let us be different shapes:
(defn render [world]
  ^:kind/hiccup
  [:svg {:style {:border "1px solid black"}}
   (for [[entity-id {:keys [x y]}] world]
     (if (= entity-id :player)
       [:rect {:width 50 :height 50 :fill "#62b133" :x x :y y :id "player"}]
       [:circle {:r 25 :fill "#5880d9" :cx x :cy y :id "enemy"}]))])
(render @world)

; Now would be a perfect time to use [Reagent](https://reagent-project.github.io/) to connect the user's keyboard to our world atom. However, I couldn't get it working with [Clay](https://scicloj.github.io/clay) (what we're using to render this page). The dependencies have won the first battle.

; Moving on, we'll resort to a `script` element. Clay lets us use JavaScript, but I couldn't get it to work with curly braces. Suffering a one-liner, we can now move, via WASD on our keyboard.

^:kind/hiccup
[:script #"document.addEventListener('keydown', e => (['w', 'W'].includes(e.key) && player.setAttribute('y', parseInt(player.getAttribute('y')) - 10) || ['a', 'A'].includes(e.key) && player.setAttribute('x', parseInt(player.getAttribute('x')) - 10) || ['s', 'S'].includes(e.key) && player.setAttribute('y', parseInt(player.getAttribute('y')) + 10) || ['d', 'D'].includes(e.key) && player.setAttribute('x', parseInt(player.getAttribute('x')) + 10)))"]
