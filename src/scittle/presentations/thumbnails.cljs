(ns scittle.presentations.thumbnails
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]))

(defonce current-slide (r/atom 0))
(defonce show-thumbnails? (r/atom false))

(def slides
  [{:title "Thumbnail Overview"
    :content "Click 'Show Thumbnails' to see all slides at once"
    :bg "linear-gradient(to bottom right, #3b82f6, #9333ea)"}
   {:title "Quick Navigation"
    :content "Jump to any slide instantly"
    :bg "linear-gradient(to bottom right, #10b981, #14b8a6)"}
   {:title "Visual Context"
    :content "See where you are in the presentation"
    :bg "linear-gradient(to bottom right, #f59e0b, #f97316)"}
   {:title "Professional Touch"
    :content "Makes presentations feel polished"
    :bg "linear-gradient(to bottom right, #ef4444, #dc2626)"}
   {:title "Easy to Build"
    :content "Just render slides in a grid"
    :bg "linear-gradient(to bottom right, #8b5cf6, #9333ea)"}
   {:title "Scittle Powered"
    :content "No build tools needed!"
    :bg "linear-gradient(to bottom right, #ec4899, #f43f5e)"}])

(defn thumbnail-view []
  [:div.fixed.inset-0.bg-black.bg-opacity-75.z-50.overflow-auto
   {:on-click #(reset! show-thumbnails? false)}
   [:div.container.mx-auto.p-8
    [:div.grid.grid-cols-3.gap-4
     (for [[idx slide] (map-indexed vector slides)]
       ^{:key idx}
       [:div.cursor-pointer.transform.transition.hover:scale-105
        {:on-click (fn [e]
                     (.stopPropagation e)
                     (reset! current-slide idx)
                     (reset! show-thumbnails? false))}
        [:div.card.shadow-lg.rounded-lg
         {:class (when (= idx @current-slide) "ring-4 ring-blue-500")
          :style {:background (:bg slide)
                  :min-height "150px"}}
         [:div.card-body.text-white.text-center
          [:h3.text-lg.font-bold.mb-2 (:title slide)]
          [:p.text-sm.opacity-90 (:content slide)]
          [:div.text-xs.mt-2.opacity-75
           (str "Slide " (inc idx) "/" (count slides))]]]])]]])

(defn presentation
  []
  (let [{:keys [title content bg]} (get slides @current-slide)]
    [:div
     [:div.flex.flex-col.h-full
      ;; Gradient content area
      [:div.flex-1.flex.flex-col.justify-center.p-6.rounded-lg
       {:style {:background bg}}
       [:div.text-center.text-white
        [:h1.text-4xl.font-bold.mb-3 title]
        [:p.text-xl.opacity-90 content]]]
      ;; Navigation section - all centered
      [:div.mt-4
       ;; Show Thumbnails button - centered
       [:div.mb-4.text-center
        [:button.btn.btn-sm.btn-secondary
         {:on-click #(reset! show-thumbnails? true)}
         "📋 Show Thumbnails"]]
       ;; Centered page counter and navigation
       [:div
        [:div.text-center.mb-3
         [:span.text-base.font-semibold
          (str (inc @current-slide) " / " (count slides))]]
        [:div.flex.gap-2.mx-auto {:style {:width "fit-content"}}
         [:button.btn.btn-sm.btn-primary
          {:on-click #(swap! current-slide dec)
           :disabled (zero? @current-slide)
           :class (when (zero? @current-slide) "btn-disabled")}
          "←"]
         [:button.btn.btn-sm.btn-primary
          {:on-click #(swap! current-slide inc)
           :disabled (= @current-slide (dec (count slides)))
           :class (when (= @current-slide (dec (count slides))) "btn-disabled")}
          "→"]]]]]
     ;; Thumbnail overlay
     (when @show-thumbnails?
       [thumbnail-view])]))

(rdom/render [presentation] (js/document.getElementById "thumbnails-demo"))
