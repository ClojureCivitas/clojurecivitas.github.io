(ns scittle.presentations.code-editor
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]))

(defonce code-input (r/atom "(+ 1 2 3)"))
(defonce output (r/atom ""))
(defonce error-msg (r/atom nil))

(defn eval-code!
  []
  (reset! error-msg nil)
  (try
    (let [result (js/scittle.core.eval_string @code-input)]
      (reset! output (pr-str result)))
    (catch js/Error e
      (reset! error-msg (.-message e))
      (reset! output ""))))

(defn code-editor
  []
  [:div.container.mx-auto.p-8
   [:div.card.shadow-lg
    [:div.card-body
     [:h2.text-2xl.font-bold.mb-4 "Live ClojureScript Editor"]
     [:p.text-gray-600.mb-4
      "Type ClojureScript code and click 'Evaluate' to see the result"]
     ;; Code input
     [:div.mb-4
      [:label.block.text-sm.font-semibold.mb-2 "Code:"]
      [:textarea.form-control.font-mono.text-sm
       {:value @code-input
        :on-change #(reset! code-input (-> % .-target .-value))
        :rows 6
        :placeholder "Enter ClojureScript code..."}]]
     ;; Evaluate button
     [:button.btn.btn-primary.mb-4
      {:on-click eval-code!}
      "▶ Evaluate"]
     ;; Output
     [:div
      [:label.block.text-sm.font-semibold.mb-2 "Result:"]
      (if @error-msg
        [:div.alert.alert-error
         [:strong "Error: "] @error-msg]
        [:pre.bg-gray-100.p-4.rounded.overflow-x-auto
         [:code.text-sm @output]])]

     ;; Examples
     [:div.mt-6
      [:p.text-sm.font-semibold.mb-2 "Try these examples:"]
      [:div.flex.flex-wrap.gap-2
       [:button.btn.btn-sm.btn-outline
        {:on-click #(reset! code-input "(+ 1 2 3 4 5)")}
        "Addition"]
       [:button.btn.btn-sm.btn-outline
        {:on-click #(reset! code-input "(map inc [1 2 3 4])")}
        "Map"]
       [:button.btn.btn-sm.btn-outline
        {:on-click #(reset! code-input "(filter even? (range 10))")}
        "Filter"]
       [:button.btn.btn-sm.btn-outline
        {:on-click #(reset! code-input "(reduce + (range 1 11))")}
        "Reduce"]
       [:button.btn.btn-sm.btn-outline
        {:on-click #(reset! code-input "(defn greet [name]\n  (str \"Hello, \" name \"!\"))\n\n(greet \"Scittle\")")}
        "Function"]]]]]
   [:div.card.shadow-lg.mt-6
    [:div.card-body
     [:h3.text-lg.font-bold.mb-2 "How It Works"]
     [:ul.list-disc.list-inside.space-y-2.text-gray-700
      [:li "Scittle provides " [:code.bg-gray-100.px-1 "scittle.core.eval_string"]]
      [:li "Evaluates ClojureScript code in the browser"]
      [:li "No compilation step - instant feedback"]
      [:li "Perfect for interactive documentation"]
      [:li "Great for teaching and demonstrations"]]]]])

(rdom/render [code-editor] (js/document.getElementById "code-editor-demo"))
