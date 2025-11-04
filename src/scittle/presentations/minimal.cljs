(ns scittle.presentations.minimal
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]))

(defonce current-slide (r/atom 0))

(def slides
  [{:title "Welcome to Scittle"
    :content "Build presentations without build tools"
    :bg "linear-gradient(to bottom right, #3b82f6, #9333ea)"}
   {:title "No Compilation Required"
    :content "ClojureScript runs directly in the browser"
    :bg "linear-gradient(to bottom right, #10b981, #14b8a6)"}
   {:title "Full Reagent Power"
    :content "Interactive components with React"
    :bg "linear-gradient(to bottom right, #f97316, #dc2626)"}
   {:title "Instant Feedback"
    :content "Changes appear immediately"
    :bg "linear-gradient(to bottom right, #ec4899, #f43f5e)"}])

(defn presentation
  []
  (let [{:keys [title content bg]} (get slides @current-slide)
        at-start? (zero? @current-slide)
        at-end? (= @current-slide (dec (count slides)))]
    [:div.flex.flex-col.h-full
     ;; Gradient content area
     [:div.flex-1.flex.flex-col.justify-center.p-6.rounded-lg
      {:style {:background bg}}
      [:div.text-center.text-white
       [:h1.text-4xl.font-bold.mb-3 title]
       [:p.text-xl.opacity-90 content]]]

     ;; Navigation centered below
     [:div.mt-4
      [:div.text-center.mb-3
       [:span.text-base.font-semibold
        (str (inc @current-slide) " / " (count slides))]]

      [:div.flex.gap-2.mx-auto {:style {:width "fit-content"}}
       [:button.btn.btn-sm.btn-primary
        {:on-click #(swap! current-slide dec)
         :disabled at-start?
         :class (when at-start? "btn-disabled")}
        "← Previous"]

       [:button.btn.btn-sm.btn-primary
        {:on-click #(swap! current-slide inc)
         :disabled at-end?
         :class (when at-end? "btn-disabled")}
        "Next →"]]]]))

(rdom/render [presentation] (js/document.getElementById "minimal-demo"))
