(ns scicloj.clay.uncompiled2
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

(defonce app-state
  (r/atom {:style "scrambled"
           :eggs 2}))

(defn cycle-style []
  (swap! app-state update :style
         {"scrambled" "fried"
          "fried" "poached"
          "poached" "scrambled"}))

(defn add-egg []
  (swap! app-state update :eggs inc))

(defn remove-egg []
  (swap! app-state update :eggs #(max 1 (dec %))))

(defn breakfast-order []
  [:div.card.border-primary.mb-3 {:style {:max-width "30rem"}}
   [:div.card-header "Breakfast Order"]
   [:div.card-body
    [:div.mb-3
     [:label.form-label "Style: "
      [:strong (:style @app-state)]]
     [:div.d-grid.gap-2
      [:button.btn.btn-primary
       {:onClick cycle-style}
       "Change Style"]]]
    [:div.mb-3
     [:div
      [:label.form-label "Eggs:"]
      [:div.d-block.mb-2 (:eggs @app-state)]]
     [:div.btn-group
      [:button.btn.btn-secondary
       {:onClick remove-egg
        :disabled (= 1 (:eggs @app-state))}
       "−"]
      [:button.btn.btn-secondary
       {:onClick add-egg}
       "+"]]]
    [:div.text-muted
     (case (:style @app-state)
       "poached" "Water added for poaching..."
       "fried" "Turn n burn!"
       "scrambled" "Whisking away...")]]])

(rdom/render
  [breakfast-order]
  (js/document.getElementById "app2"))