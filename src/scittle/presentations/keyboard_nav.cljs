(ns scittle.presentations.keyboard-nav
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]))

(defonce current-slide (r/atom 0))

(def slides
  [{:title "Keyboard Navigation"
    :subtitle "Professional Presentation Control"
    :content "Use arrow keys to navigate through slides smoothly"
    :bg "linear-gradient(to bottom right, #3b82f6, #9333ea)"
    :layout :intro}
   {:title "Arrow Key Controls"
    :content "Simple keyboard shortcuts for navigation"
    :bg "linear-gradient(to bottom right, #10b981, #14b8a6)"
    :layout :keys
    :keys [{:key "←" :action "Previous slide"}
           {:key "→" :action "Next slide"}
           {:key "Home" :action "First slide"}
           {:key "End" :action "Last slide"}]}
   {:title "Implementation"
    :subtitle "Just a few lines of code"
    :bg "linear-gradient(to bottom right, #f97316, #dc2626)"
    :layout :features
    :features ["Single event listener"
               "Works with Scittle out of the box"
               "No dependencies needed"
               "Instant hot reload"]}
   {:title "Ready to Build?"
    :subtitle "Start creating interactive presentations today"
    :bg "linear-gradient(to bottom right, #ec4899, #f43f5e)"
    :layout :summary
    :points ["✓ No build step"
             "✓ No compilation"
             "✓ Pure browser magic"]}])

(defn handle-keydown
  [e]
  (let [key (.-key e)
        max-idx (dec (count slides))]
    (case key
      "ArrowRight" (swap! current-slide #(min max-idx (inc %)))
      "ArrowLeft" (swap! current-slide #(max 0 (dec %)))
      "Home" (reset! current-slide 0)
      "End" (reset! current-slide max-idx)
      nil)))

;; Add keyboard listener
(.addEventListener js/document "keydown" handle-keydown)

(defn keys-display
  []
  (let [slide (get slides @current-slide)]
    [:div.mt-6.max-w-md.mx-auto
     [:div.space-y-3
      (for [{:keys [key action]} (:keys slide)]
        ^{:key key}
        [:div.rounded-lg.p-4.text-center
         {:style {:background "rgba(20, 184, 166, 0.3)"}}
         [:div.text-3xl.font-bold.mb-2.text-white key]
         [:div.text-sm.text-white action]])]]))

(defn features-display
  []
  (let [slide (get slides @current-slide)]
    [:div.mt-6.max-w-2xl.mx-auto
     [:div.space-y-3
      (for [feature (:features slide)]
        ^{:key feature}
        [:div.rounded-lg.p-4.text-center
         {:style {:background "rgba(249, 115, 22, 0.3)"}}
         [:div.text-base.text-white feature]])]]))

(defn summary-display
  []
  (let [slide (get slides @current-slide)]
    [:div.mt-6.max-w-2xl.mx-auto
     [:div.space-y-3
      (for [point (:points slide)]
        ^{:key point}
        [:div.rounded-lg.p-4.text-center.text-base.text-white
         {:style {:background "rgba(236, 72, 153, 0.3)"}}
         point])]]))

(defn presentation
  []
  (let [{:keys [title subtitle content bg layout]} (get slides @current-slide)
        at-start? (zero? @current-slide)
        at-end? (= @current-slide (dec (count slides)))]
    [:div.flex.flex-col.h-full
     ;; Gradient content area
     [:div.flex-1.flex.flex-col.justify-center.p-6.rounded-lg
      {:style {:background bg}}
      [:div.text-center.text-white
       [:h1.text-4xl.font-bold.mb-3 title]
       (when subtitle
         [:p.text-xl.opacity-90.mb-6 subtitle])
       (when content
         [:p.text-lg.opacity-90.mb-4 content])
       ;; Layout-specific content
       (case layout
         :intro nil
         :keys [keys-display]
         :features [features-display]
         :summary [summary-display]
         nil)]]
     ;; Navigation centered below
     [:div.w-full.mt-4.pb-2
      [:div.text-center.mb-3
       [:div.text-base.font-semibold
        (str (inc @current-slide) " / " (count slides))]
       [:div.text-xs.text-gray-500.mt-1
        "Try arrow keys: ← →"]]
      [:div.flex.gap-4.mx-auto {:style {:width "fit-content"}}
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

(rdom/render [presentation] (js/document.getElementById "keyboard-demo"))
