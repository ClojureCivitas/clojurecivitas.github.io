(ns scittle.pdf.pdf-viewer
  "Browser-native PDF viewer with search, navigation, and theming"
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as str]))

;; ============================================================================
;; State Management
;; ============================================================================

(defonce pdf-state
  (r/atom {:current-pdf nil
           :pdf-url nil
           :current-page 1
           :total-pages 0
           :zoom-level 1.0
           :rotation 0
           :loading? false
           :error nil
           :fit-mode :width
           :pdf-theme :normal
           :search-state {:active? false
                          :query ""
                          :case-sensitive? false
                          :results []
                          :current-match-index 0
                          :total-matches 0
                          :searching? false
                          :page-text-cache {}}}))

(defn reset-state! []
  (reset! pdf-state
          {:current-pdf nil
           :pdf-url nil
           :current-page 1
           :total-pages 0
           :zoom-level 1.0
           :rotation 0
           :loading? false
           :error nil
           :fit-mode :width
           :pdf-theme :normal
           :search-state {:active? false
                          :query ""
                          :case-sensitive? false
                          :results []
                          :current-match-index 0
                          :total-matches 0
                          :searching? false
                          :page-text-cache {}}}))

;; State update functions
(defn set-loading! [loading?]
  (swap! pdf-state assoc :loading? loading?))

(defn set-error! [error]
  (swap! pdf-state assoc :error error :loading? false))

(defn set-pdf! [pdf-doc]
  (swap! pdf-state assoc
         :current-pdf pdf-doc
         :total-pages (.-numPages pdf-doc)
         :loading? false
         :error nil))

(defn go-to-page! [page-num]
  (let [total (:total-pages @pdf-state)]
    (when (and (> page-num 0) (<= page-num total))
      (swap! pdf-state assoc :current-page page-num))))

(defn next-page! []
  (swap! pdf-state update :current-page
         #(min (inc %) (:total-pages @pdf-state))))

(defn prev-page! []
  (swap! pdf-state update :current-page #(max (dec %) 1)))

(defn set-zoom! [zoom]
  (swap! pdf-state assoc :zoom-level zoom :fit-mode :custom))

(defn rotate-page! []
  (swap! pdf-state update :rotation #(mod (+ % 90) 360)))

(defn set-fit-mode! [mode]
  (swap! pdf-state assoc :fit-mode mode))

(defn set-pdf-theme! [theme]
  (swap! pdf-state assoc :pdf-theme theme)
  (when (exists? js/localStorage)
    (.setItem js/localStorage "pdf-theme" (name theme))))

(defn load-pdf-theme! []
  (when (exists? js/localStorage)
    (when-let [saved-theme (.getItem js/localStorage "pdf-theme")]
      (swap! pdf-state assoc :pdf-theme (keyword saved-theme)))))

;; Search state functions
(defn toggle-search! []
  (swap! pdf-state update-in [:search-state :active?] not))

(defn open-search! []
  (swap! pdf-state assoc-in [:search-state :active?] true))

(defn close-search! []
  (swap! pdf-state update :search-state
         #(assoc % :active? false :current-match-index 0)))

(defn set-search-query! [query]
  (swap! pdf-state assoc-in [:search-state :query] query))

(defn set-searching! [searching?]
  (swap! pdf-state assoc-in [:search-state :searching?] searching?))

(defn toggle-case-sensitive! []
  (swap! pdf-state update-in [:search-state :case-sensitive?] not))

(defn set-search-results! [results]
  (let [total (reduce #(+ %1 (count (:matches %2))) 0 results)]
    (swap! pdf-state update :search-state
           #(assoc % :results results
                   :total-matches total
                   :current-match-index 0
                   :searching? false))))

(defn clear-search-results! []
  (swap! pdf-state update :search-state
         #(assoc % :results []
                 :total-matches 0
                 :current-match-index 0
                 :page-text-cache {})))

(defn cache-page-text! [page-num text]
  (swap! pdf-state assoc-in [:search-state :page-text-cache page-num] text))

(defn get-cached-page-text [page-num]
  (get-in @pdf-state [:search-state :page-text-cache page-num]))

(defn next-search-match! []
  (let [total (get-in @pdf-state [:search-state :total-matches])
        current (get-in @pdf-state [:search-state :current-match-index])]
    (when (and (> total 0) (< current (dec total)))
      (swap! pdf-state update-in [:search-state :current-match-index] inc))))

(defn prev-search-match! []
  (let [current (get-in @pdf-state [:search-state :current-match-index])]
    (when (> current 0)
      (swap! pdf-state update-in [:search-state :current-match-index] dec))))

;; ============================================================================
;; PDF.js Utilities
;; ============================================================================

(defn load-pdf-document
  "Load PDF from URL or File object"
  [& {:keys [source]}]
  (js/Promise.
   (fn [resolve reject]
     (if (and (exists? js/pdfjsLib) js/pdfjsLib)
       (try
         (cond
           (string? source)
           (let [loading-task (.getDocument js/pdfjsLib #js {:url source})]
             (-> (.-promise loading-task)
                 (.then resolve reject)))

           (instance? js/File source)
           (let [file-reader (js/FileReader.)]
             (set! (.-onload file-reader)
                   (fn [e]
                     (let [array-buffer (.. e -target -result)
                           loading-task (.getDocument js/pdfjsLib #js {:data array-buffer})]
                       (-> (.-promise loading-task)
                           (.then resolve reject)))))
             (.readAsArrayBuffer file-reader source))

           :else
           (reject (js/Error. "Invalid PDF source")))
         (catch js/Error e
           (reject e)))
       (reject (js/Error. "PDF.js library not loaded"))))))

(defn get-pdf-page
  "Get a specific page from PDF document"
  [& {:keys [pdf-doc page-num]}]
  (.getPage pdf-doc page-num))

(defn get-pdf-text
  "Extract text content from a PDF page"
  [& {:keys [page]}]
  (-> (.getTextContent page)
      (.then (fn [text-content]
               (let [items (.-items text-content)]
                 (str/join " " (map #(.-str %) items)))))))

(defn calculate-scale-for-fit
  [& {:keys [page container-width container-height fit-mode rotation]}]
  (let [viewport (.getViewport page #js {:scale 1.0 :rotation rotation})
        page-width (.-width viewport)
        page-height (.-height viewport)]
    (case fit-mode
      :width (/ container-width page-width)
      :page (min (/ container-width page-width)
                 (/ container-height page-height))
      :actual 1.0
      1.0)))

(defn render-pdf-page
  [& {:keys [page canvas-elem scale rotation]}]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [viewport (.getViewport page #js {:scale scale :rotation rotation})
             context (.getContext canvas-elem "2d")
             output-scale (or (.-devicePixelRatio js/window) 1)
             width (* (.-width viewport) output-scale)
             height (* (.-height viewport) output-scale)]
         (set! (.-width canvas-elem) width)
         (set! (.-height canvas-elem) height)
         (set! (.. canvas-elem -style -width) (str (.-width viewport) "px"))
         (set! (.. canvas-elem -style -height) (str (.-height viewport) "px"))
         (when (not= output-scale 1)
           (.setTransform context output-scale 0 0 output-scale 0 0))
         (let [render-context #js {:canvasContext context :viewport viewport}
               render-task (.render page render-context)]
           (-> (.-promise render-task)
               (.then #(resolve {:success true :page page}) reject))))
       (catch js/Error e (reject e))))))

;; ============================================================================
;; Search Functionality
;; ============================================================================

(defn find-text-matches
  "Find all occurrences of query in text"
  [& {:keys [text query case-sensitive?]}]
  (if (or (empty? text) (empty? query))
    []
    (let [search-text (if case-sensitive? text (str/lower-case text))
          search-query (if case-sensitive? query (str/lower-case query))
          find-indexes (fn [string substring]
                         (loop [index 0 matches []]
                           (let [found (.indexOf string substring index)]
                             (if (= found -1)
                               matches
                               (recur (inc found) (conj matches found))))))
          positions (find-indexes search-text search-query)]
      (mapv (fn [index]
              {:index index
               :length (count query)
               :context (let [start (max 0 (- index 30))
                              end (min (count text) (+ index (count query) 30))]
                          (subs text start end))})
            positions))))

(defn search-page
  "Search for query in a specific PDF page"
  [& {:keys [pdf-doc page-num query case-sensitive?]}]
  (js/Promise.
   (fn [resolve reject]
     (if-let [cached-text (get-cached-page-text page-num)]
       (resolve {:page-num page-num
                 :text cached-text
                 :matches (find-text-matches :text cached-text
                                             :query query
                                             :case-sensitive? case-sensitive?)})
       (-> (get-pdf-page :pdf-doc pdf-doc :page-num page-num)
           (.then (fn [page]
                    (-> (get-pdf-text :page page)
                        (.then (fn [text]
                                 (cache-page-text! page-num text)
                                 (resolve {:page-num page-num
                                           :text text
                                           :matches (find-text-matches :text text
                                                                       :query query
                                                                       :case-sensitive? case-sensitive?)})))
                        (.catch reject))))
           (.catch reject))))))

(defn search-all-pages
  "Search for query across all pages in PDF"
  [& {:keys [pdf-doc query case-sensitive?]}]
  (let [total-pages (:total-pages @pdf-state)
        promises (mapv #(search-page :pdf-doc pdf-doc
                                     :page-num %
                                     :query query
                                     :case-sensitive? case-sensitive?)
                       (range 1 (inc total-pages)))]
    (-> (js/Promise.all (clj->js promises))
        (.then (fn [results]
                 (filterv #(seq (:matches %)) (js->clj results :keywordize-keys true)))))))

;; ============================================================================
;; Highlighting
;; ============================================================================

(defn clear-all-highlights []
  (let [highlights (.querySelectorAll js/document ".pdf-search-highlight, .pdf-match-badge")]
    (.forEach highlights (fn [elem] (.remove elem)))))

(defn show-match-indicator
  "Show match count indicator badge on canvas"
  [& {:keys [canvas-elem page-num match-count]}]
  (when (and canvas-elem (> match-count 0))
    (let [parent (.-parentElement canvas-elem)]
      (when-let [existing (.querySelector parent ".pdf-match-badge")]
        (.remove existing))
      (let [badge (.createElement js/document "div")]
        (set! (.-className badge) "pdf-match-badge")
        (set! (.-innerHTML badge) (str match-count " match" (when (> match-count 1) "es")))
        (set! (.. badge -style -position) "absolute")
        (set! (.. badge -style -top) "10px")
        (set! (.. badge -style -right) "10px")
        (set! (.. badge -style -padding) "4px 8px")
        (set! (.. badge -style -backgroundColor) "#3b82f6")
        (set! (.. badge -style -color) "white")
        (set! (.. badge -style -borderRadius) "12px")
        (set! (.. badge -style -fontSize) "12px")
        (set! (.. badge -style -fontWeight) "bold")
        (set! (.. badge -style -zIndex) "2")
        (set! (.. badge -style -pointerEvents) "none")
        (.appendChild parent badge)))))

(defn update-page-highlights []
  (let [current-page (:current-page @pdf-state)
        search-results (get-in @pdf-state [:search-state :results])
        canvas (.getElementById js/document "pdf-canvas")]
    (when canvas
      (clear-all-highlights)
      (when-let [page-result (first (filter #(= (:page-num %) current-page) search-results))]
        (show-match-indicator :canvas-elem canvas
                              :page-num current-page
                              :match-count (count (:matches page-result)))))))

(defn perform-search
  "Perform search with given query"
  [& {:keys [query]}]
  (when-let [pdf (:current-pdf @pdf-state)]
    (let [case-sensitive? (get-in @pdf-state [:search-state :case-sensitive?])]
      (set-search-query! query)
      (set-searching! true)
      (clear-search-results!)
      (-> (search-all-pages :pdf-doc pdf
                            :query query
                            :case-sensitive? case-sensitive?)
          (.then (fn [results]
                   (set-search-results! results)
                   (when (seq results)
                     (go-to-page! (:page-num (first results))))
                   (update-page-highlights)
                   results))
          (.catch (fn [err]
                    (set-searching! false)
                    (set-error! (str "Search failed: " (.-message err)))))))))

(defn navigate-next-match []
  (next-search-match!)
  (let [results (get-in @pdf-state [:search-state :results])
        current-index (get-in @pdf-state [:search-state :current-match-index])]
    (loop [page-results results
           accumulated 0]
      (when-let [page-result (first page-results)]
        (let [page-match-count (count (:matches page-result))
              new-total (+ accumulated page-match-count)]
          (if (< current-index new-total)
            (go-to-page! (:page-num page-result))
            (recur (rest page-results) new-total)))))))

(defn navigate-prev-match []
  (prev-search-match!)
  (navigate-next-match))

(defn clear-search []
  (clear-search-results!)
  (close-search!)
  (clear-all-highlights))

;; ============================================================================
;; PDF Loading and Rendering
;; ============================================================================

(defonce ui-state (r/atom {:container-width 800 :container-height 600}))

(defn load-and-render-page
  [& {:keys [pdf-doc page-num canvas-id]}]
  (-> (get-pdf-page :pdf-doc pdf-doc :page-num page-num)
      (.then (fn [page]
               (let [canvas (.getElementById js/document canvas-id)
                     state @pdf-state
                     zoom (:zoom-level state)
                     rotation (:rotation state)
                     fit-mode (:fit-mode state)]
                 (when canvas
                   (let [scale (if (= fit-mode :custom)
                                 zoom
                                 (calculate-scale-for-fit
                                  :page page
                                  :container-width (:container-width @ui-state)
                                  :container-height (:container-height @ui-state)
                                  :fit-mode fit-mode
                                  :rotation rotation))]
                     (-> (render-pdf-page :page page
                                          :canvas-elem canvas
                                          :scale scale
                                          :rotation rotation)
                         (.then (fn [_]
                                  (update-page-highlights)
                                  (js/setTimeout update-page-highlights 150)))
                         (.catch (fn [err]
                                   (js/console.error "Render error:" err)))))))))
      (.catch (fn [err]
                (js/console.error "Page load error:" err)))))

;; ============================================================================
;; UI Components
;; ============================================================================

(defn get-pdf-filter [theme]
  (case theme
    :dark "invert(0.88) hue-rotate(180deg) contrast(1.2) brightness(1.1)"
    :sepia "sepia(1) contrast(0.85) brightness(0.9) saturate(0.8)"
    :high-contrast "contrast(1.5) brightness(1.1) saturate(0.6)"
    "none"))

(defn pdf-theme-selector [dark-mode?]
  (let [current-theme (:pdf-theme @pdf-state)]
    [:div {:style {:display "flex" :align-items "center" :gap "5px"}}
     [:label {:style {:font-size "16px"
                      :color (if dark-mode? "#e2e8f0" "#4a5568")
                      :line-height "1"}} "☾"]
     [:select {:value (name current-theme)
               :on-change #(do
                             (set-pdf-theme! (keyword (.. % -target -value)))
                             (when-let [pdf (:current-pdf @pdf-state)]
                               (load-and-render-page :pdf-doc pdf
                                                     :page-num (:current-page @pdf-state)
                                                     :canvas-id "pdf-canvas")))
               :style {:padding "5px"
                       :border-radius "3px"
                       :border (if dark-mode? "1px solid #718096" "1px solid #cbd5e0")
                       :background (if dark-mode? "#4a5568" "#ffffff")
                       :color (if dark-mode? "#e2e8f0" "#2d3748")
                       :font-size "13px"
                       :cursor "pointer"}}
      [:option {:value "normal"} "Normal"]
      [:option {:value "dark"} "Dark Mode"]
      [:option {:value "sepia"} "Sepia"]
      [:option {:value "high-contrast"} "High Contrast"]]]))

(defn search-bar
  [dark-mode?]
  (let [search-state (:search-state @pdf-state)
        local-query (r/atom (:query search-state))]
    (fn [dark-mode?]
      (let [search-state (:search-state @pdf-state)]
        [:div {:style {:display (if (:active? search-state) "flex" "none")
                       :align-items "center"
                       :gap "8px"
                       :padding "8px"
                       :background (if dark-mode? "#2d3748" "#f5f5f5")
                       :border-radius "5px"
                       :margin "8px 0"
                       :border (if dark-mode? "1px solid #4a5568" "1px solid #e2e8e2")}}
         [:input {:type "text"
                  :placeholder "Search in PDF..."
                  :value @local-query
                  :on-change #(reset! local-query (.. % -target -value))
                  :on-key-press #(when (= (.-key %) "Enter")
                                   (perform-search :query @local-query))
                  :style {:flex 1
                          :padding "6px 10px"
                          :border (if dark-mode? "1px solid #718096" "1px solid #cbd5e0")
                          :background (if dark-mode? "#4a5568" "#ffffff")
                          :color (if dark-mode? "#e2e8f0" "#2d3748")
                          :border-radius "4px"
                          :font-size "14px"}
                  :auto-focus true}]
         [:button {:on-click #(perform-search :query @local-query)
                   :disabled (or (:searching? search-state) (empty? @local-query))
                   :style {:padding "6px 12px"
                           :background (if (or (:searching? search-state) (empty? @local-query))
                                         "#94a3b8" "#3182ce")
                           :color "white"
                           :border "none"
                           :border-radius "4px"
                           :cursor (if (or (:searching? search-state) (empty? @local-query))
                                     "not-allowed" "pointer")
                           :font-size "13px"}}
          (if (:searching? search-state) "Searching..." "Search")]
         (when (> (:total-matches search-state) 0)
           [:span {:style {:font-size "13px"
                           :color (if dark-mode? "#e2e8f0" "#2d3748")
                           :white-space "nowrap"}}
            (str (inc (:current-match-index search-state)) " / " (:total-matches search-state))])
         (when (> (:total-matches search-state) 0)
           [:<>
            [:button {:on-click #(do (navigate-prev-match)
                                     (when-let [pdf (:current-pdf @pdf-state)]
                                       (load-and-render-page :pdf-doc pdf
                                                             :page-num (:current-page @pdf-state)
                                                             :canvas-id "pdf-canvas"))
                                     (js/setTimeout update-page-highlights 150))
                      :disabled (= (:current-match-index search-state) 0)
                      :style {:padding "6px 10px"
                              :background (if dark-mode? "#4a5568" "#ffffff")
                              :color (if dark-mode? "#e2e8f0" "#2d3748")
                              :border (if dark-mode? "1px solid #718096" "1px solid #cbd5e0")
                              :border-radius "4px"
                              :cursor (if (= (:current-match-index search-state) 0) "not-allowed" "pointer")
                              :font-size "14px"}
                      :title "Previous"} "◂"]
            [:button {:on-click #(do (navigate-next-match)
                                     (when-let [pdf (:current-pdf @pdf-state)]
                                       (load-and-render-page :pdf-doc pdf
                                                             :page-num (:current-page @pdf-state)
                                                             :canvas-id "pdf-canvas"))
                                     (js/setTimeout update-page-highlights 150))
                      :disabled (= (:current-match-index search-state) (dec (:total-matches search-state)))
                      :style {:padding "6px 10px"
                              :background (if dark-mode? "#4a5568" "#ffffff")
                              :color (if dark-mode? "#e2e8f0" "#2d3748")
                              :border (if dark-mode? "1px solid #718096" "1px solid #cbd5e0")
                              :border-radius "4px"
                              :cursor (if (= (:current-match-index search-state) (dec (:total-matches search-state)))
                                        "not-allowed" "pointer")
                              :font-size "14px"}
                      :title "Next"} "▸"]])
         [:label {:style {:display "flex" :align-items "center" :gap "4px" :font-size "13px"
                          :color (if dark-mode? "#e2e8f0" "#2d3748") :cursor "pointer"}}
          [:input {:type "checkbox"
                   :checked (:case-sensitive? search-state)
                   :on-change toggle-case-sensitive!}]
          "Aa"]
         (when (> (:total-matches search-state) 0)
           [:button {:on-click #(do (clear-search) (reset! local-query ""))
                     :style {:padding "6px" :background "transparent" :color "#ef4444"
                             :border "none" :cursor "pointer" :font-size "14px"}
                     :title "Clear"} "✕"])
         [:button {:on-click #(do (close-search!) (reset! local-query ""))
                   :style {:padding "6px" :background "transparent"
                           :color (if dark-mode? "#e2e8f0" "#2d3748")
                           :border "none" :cursor "pointer" :font-size "16px"}
                   :title "Close"} "✕"]]))))

(defn page-jump-input
  [dark-mode?]
  (let [total-pages (:total-pages @pdf-state)
        jump-input (r/atom "")]
    (fn [dark-mode?]
      [:div {:style {:display "flex" :gap "3px" :align-items "center"}}
       [:input {:type "number" :min 1 :max total-pages :placeholder "#"
                :value @jump-input
                :on-change #(reset! jump-input (.. % -target -value))
                :on-key-press #(when (= (.-key %) "Enter")
                                 (let [page-num (js/parseInt @jump-input)]
                                   (when (and (>= page-num 1) (<= page-num total-pages))
                                     (go-to-page! page-num)
                                     (when-let [pdf (:current-pdf @pdf-state)]
                                       (load-and-render-page :pdf-doc pdf
                                                             :page-num page-num
                                                             :canvas-id "pdf-canvas"))
                                     (reset! jump-input ""))))
                :style {:width "50px" :height "32px" :padding "6px 8px"
                        :border (if dark-mode? "1px solid #718096" "1px solid #cbd5e0")
                        :background (if dark-mode? "#4a5568" "#ffffff")
                        :color (if dark-mode? "#e2e8f0" "#2d3748")
                        :border-radius "4px" :font-size "13px"
                        :text-align "center" :box-sizing "border-box"}}]
       [:button {:on-click #(let [page-num (js/parseInt @jump-input)]
                              (when (and (>= page-num 1) (<= page-num total-pages))
                                (go-to-page! page-num)
                                (when-let [pdf (:current-pdf @pdf-state)]
                                  (load-and-render-page :pdf-doc pdf
                                                        :page-num page-num
                                                        :canvas-id "pdf-canvas"))
                                (reset! jump-input "")))
                 :disabled (or (:loading? @pdf-state) (empty? @jump-input))
                 :style {:padding "6px 10px" :background "#3182ce" :color "white"
                         :border "1px solid #3182ce" :border-radius "4px"
                         :font-size "13px" :font-weight "500"
                         :cursor (if (or (:loading? @pdf-state) (empty? @jump-input))
                                   "not-allowed" "pointer")}} "Go"]])))

(defn navigation-controls
  [dark-mode?]
  (let [state @pdf-state
        current-page (:current-page state)
        total-pages (:total-pages state)
        zoom (:zoom-level state)
        fit-mode (:fit-mode state)]
    [:div {:style {:display "flex" :align-items "center" :gap "5px" :padding "8px"
                   :background (if dark-mode? "#2d3748" "#f5f5f5")
                   :border-radius "5px" :flex-wrap "wrap"}}
     [:div {:style {:display "flex" :align-items "center" :gap "5px"}}
      [:button {:on-click #(do (prev-page!)
                               (when-let [pdf (:current-pdf @pdf-state)]
                                 (load-and-render-page :pdf-doc pdf
                                                       :page-num (:current-page @pdf-state)
                                                       :canvas-id "pdf-canvas")))
                :disabled (or (<= current-page 1) (:loading? state))
                :style {:padding "6px 10px" :font-size "14px"
                        :background (if dark-mode? "#4a5568" "#ffffff")
                        :color (if dark-mode? "#e2e8f0" "#2d3748")
                        :border (if dark-mode? "1px solid #718096" "1px solid #cbd5e0")
                        :border-radius "4px"
                        :cursor (if (or (<= current-page 1) (:loading? state)) "not-allowed" "pointer")}
                :title "Previous"} "◂"]
      [:span {:style {:font-size "13px"
                      :color (if dark-mode? "#e2e8f0" "#2d3748")
                      :min-width "60px" :text-align "center"}}
       (str current-page " / " total-pages)]
      [:button {:on-click #(do (next-page!)
                               (when-let [pdf (:current-pdf @pdf-state)]
                                 (load-and-render-page :pdf-doc pdf
                                                       :page-num (:current-page @pdf-state)
                                                       :canvas-id "pdf-canvas")))
                :disabled (or (>= current-page total-pages) (:loading? state))
                :style {:padding "6px 10px" :font-size "14px"
                        :background (if dark-mode? "#4a5568" "#ffffff")
                        :color (if dark-mode? "#e2e8f0" "#2d3748")
                        :border (if dark-mode? "1px solid #718096" "1px solid #cbd5e0")
                        :border-radius "4px"
                        :cursor (if (or (>= current-page total-pages) (:loading? state)) "not-allowed" "pointer")}
                :title "Next"} "▸"]
      [page-jump-input dark-mode?]]
     [:div {:style {:display "flex" :align-items "center" :gap "3px"}}
      [:button {:on-click #(do (set-zoom! (* zoom 0.8))
                               (when-let [pdf (:current-pdf @pdf-state)]
                                 (load-and-render-page :pdf-doc pdf
                                                       :page-num current-page
                                                       :canvas-id "pdf-canvas")))
                :disabled (:loading? state)
                :style {:padding "6px 10px" :font-size "16px"
                        :background (if dark-mode? "#4a5568" "#ffffff")
                        :color (if dark-mode? "#e2e8f0" "#2d3748")
                        :border (if dark-mode? "1px solid #718096" "1px solid #cbd5e0")
                        :border-radius "4px"
                        :cursor (if (:loading? state) "not-allowed" "pointer")}
                :title "Zoom out"} "−"]
      [:span {:style {:font-size "12px" :min-width "45px" :text-align "center"
                      :color (if dark-mode? "#e2e8f0" "#2d3748")}}
       (str (Math/round (* zoom 100)) "%")]
      [:button {:on-click #(do (set-zoom! (* zoom 1.2))
                               (when-let [pdf (:current-pdf @pdf-state)]
                                 (load-and-render-page :pdf-doc pdf
                                                       :page-num current-page
                                                       :canvas-id "pdf-canvas")))
                :disabled (:loading? state)
                :style {:padding "6px 10px" :font-size "16px"
                        :background (if dark-mode? "#4a5568" "#ffffff")
                        :color (if dark-mode? "#e2e8f0" "#2d3748")
                        :border (if dark-mode? "1px solid #718096" "1px solid #cbd5e0")
                        :border-radius "4px"
                        :cursor (if (:loading? state) "not-allowed" "pointer")}
                :title "Zoom in"} "+"]]
     [:div {:style {:display "flex" :gap "3px"}}
      [:button {:on-click #(do (set-fit-mode! :width)
                               (when-let [pdf (:current-pdf @pdf-state)]
                                 (load-and-render-page :pdf-doc pdf
                                                       :page-num current-page
                                                       :canvas-id "pdf-canvas")))
                :style {:padding "6px 10px" :font-size "14px"
                        :background (if (= fit-mode :width) "#3182ce"
                                        (if dark-mode? "#4a5568" "#ffffff"))
                        :color (if (= fit-mode :width) "white"
                                   (if dark-mode? "#e2e8f0" "#2d3748"))
                        :border (if (= fit-mode :width) "1px solid #3182ce"
                                    (if dark-mode? "1px solid #718096" "1px solid #cbd5e0"))
                        :border-radius "4px"
                        :cursor (if (:loading? state) "not-allowed" "pointer")}
                :disabled (:loading? state)
                :title "Fit width"} "⇔"]
      [:button {:on-click #(do (set-fit-mode! :page)
                               (when-let [pdf (:current-pdf @pdf-state)]
                                 (load-and-render-page :pdf-doc pdf
                                                       :page-num current-page
                                                       :canvas-id "pdf-canvas")))
                :style {:padding "6px 10px" :font-size "14px"
                        :background (if (= fit-mode :page) "#3182ce"
                                        (if dark-mode? "#4a5568" "#ffffff"))
                        :color (if (= fit-mode :page) "white"
                                   (if dark-mode? "#e2e8f0" "#2d3748"))
                        :border (if (= fit-mode :page) "1px solid #3182ce"
                                    (if dark-mode? "1px solid #718096" "1px solid #cbd5e0"))
                        :border-radius "4px"
                        :cursor (if (:loading? state) "not-allowed" "pointer")}
                :disabled (:loading? state)
                :title "Fit page"} "▭"]
      [:button {:on-click #(do (set-fit-mode! :actual)
                               (when-let [pdf (:current-pdf @pdf-state)]
                                 (load-and-render-page :pdf-doc pdf
                                                       :page-num current-page
                                                       :canvas-id "pdf-canvas")))
                :style {:padding "6px 10px" :font-size "12px" :font-weight "bold"
                        :background (if (= fit-mode :actual) "#3182ce"
                                        (if dark-mode? "#4a5568" "#ffffff"))
                        :color (if (= fit-mode :actual) "white"
                                   (if dark-mode? "#e2e8f0" "#2d3748"))
                        :border (if (= fit-mode :actual) "1px solid #3182ce"
                                    (if dark-mode? "1px solid #718096" "1px solid #cbd5e0"))
                        :border-radius "4px"
                        :cursor (if (:loading? state) "not-allowed" "pointer")}
                :disabled (:loading? state)
                :title "Actual size"} "1:1"]]
     [:div {:style {:display "flex" :gap "3px"}}
      [:button {:on-click toggle-search!
                :style {:padding "6px 10px"
                        :background (if (get-in @pdf-state [:search-state :active?]) "#3182ce"
                                        (if dark-mode? "#4a5568" "#ffffff"))
                        :color (if (get-in @pdf-state [:search-state :active?]) "white"
                                   (if dark-mode? "#e2e8f0" "#2d3748"))
                        :border (if (get-in @pdf-state [:search-state :active?]) "1px solid #3182ce"
                                    (if dark-mode? "1px solid #718096" "1px solid #cbd5e0"))
                        :border-radius "4px" :cursor "pointer"}
                :title "Search"} "🔍"]
      [:button {:on-click #(do (rotate-page!)
                               (when-let [pdf (:current-pdf @pdf-state)]
                                 (load-and-render-page :pdf-doc pdf
                                                       :page-num current-page
                                                       :canvas-id "pdf-canvas")))
                :disabled (:loading? state)
                :style {:padding "6px 10px"
                        :background (if dark-mode? "#4a5568" "#ffffff")
                        :color (if dark-mode? "#e2e8f0" "#2d3748")
                        :border (if dark-mode? "1px solid #718096" "1px solid #cbd5e0")
                        :border-radius "4px"
                        :cursor (if (:loading? state) "not-allowed" "pointer")}
                :title "Rotate"} "↻"]]
     [:div {:style {:display "flex" :align-items "center" :gap "3px"}}
      [pdf-theme-selector dark-mode?]]]))

(defn handle-pdf-load
  "Handle successful PDF load"
  [& {:keys [pdf-doc]}]
  (set-pdf! pdf-doc)
  (load-and-render-page :pdf-doc pdf-doc :page-num 1 :canvas-id "pdf-canvas"))

(defn load-pdf-url
  "Load PDF from a URL"
  [& {:keys [url]}]
  (reset-state!)
  (set-loading! true)
  (swap! pdf-state assoc :pdf-url url)
  (-> (load-pdf-document :source url)
      (.then #(handle-pdf-load :pdf-doc %))
      (.catch (fn [err]
                (set-error! (str "Failed to load PDF: " (.-message err)))))))

(defn handle-file-upload [event]
  (let [file (-> event .-target .-files (aget 0))]
    (when (and file (= (.-type file) "application/pdf"))
      (reset-state!)
      (set-loading! true)
      (swap! pdf-state assoc :pdf-file file)
      (-> (load-pdf-document :source file)
          (.then #(handle-pdf-load :pdf-doc %))
          (.catch (fn [err]
                    (set-error! (str "Failed to load file: " (.-message err)))))))))

;; Sample PDFs
(def sample-pdfs
  {:hello "https://raw.githubusercontent.com/mozilla/pdf.js/master/examples/learning/helloworld.pdf"
   :research "https://raw.githubusercontent.com/mozilla/pdf.js/master/web/compressed.tracemonkey-pldi-09.pdf"
   :geotopo "https://raw.githubusercontent.com/py-pdf/sample-files/main/009-pdflatex-geotopo/GeoTopo-komprimiert.pdf"})

;; ============================================================================
;; Main Component
;; ============================================================================

(defn pdf-viewer []
  (r/with-let [_ (load-pdf-theme!)
               keyboard-handler (fn [e]
                                  (cond
                                    (and (or (.-ctrlKey e) (.-metaKey e)) (= (.-key e) "f"))
                                    (do (.preventDefault e) (open-search!))
                                    (and (= (.-key e) "Escape")
                                         (get-in @pdf-state [:search-state :active?]))
                                    (close-search!)))
               _ (.addEventListener js/document "keydown" keyboard-handler)]
    (let [state @pdf-state
          pdf-loaded? (and (exists? js/pdfjsLib) js/pdfjsLib)
          pdf-theme (:pdf-theme state)
          ;; Determine if we're in a dark theme for controls
          dark-mode? (not= pdf-theme :normal)
          ;; Dynamic backgrounds and colors based on theme for better night reading
          container-bg (case pdf-theme
                         :dark "#1e1e1e"
                         :sepia "#2a231d"
                         :high-contrast "#0d0d0d"
                         "white") ; Normal mode - white
          title-color (case pdf-theme
                        :dark "#e0e0e0"
                        :sepia "#d4c5b0"
                        :high-contrast "#ffffff"
                        "#333") ; Normal mode - dark gray
          canvas-bg (case pdf-theme
                      :dark "#2d2d2d"
                      :sepia "#3d3228"
                      :high-contrast "#1a1a1a"
                      "#f5f5f5") ; Normal mode - light gray instead of white
          ;; Outer wrapper background for dark theme padding area
          wrapper-bg (case pdf-theme
                       :dark "#0f0f0f"
                       :sepia "#1a1410"
                       :high-contrast "#000000"
                       "transparent") ; Normal mode - transparent (no background)
          ;; Apply background to the root mounting element as well
          _ (when-let [root (.getElementById js/document "pdf-viewer-root")]
              (set! (.-backgroundColor (.-style root)) wrapper-bg))]
      ;; Outer wrapper provides dark background padding in dark modes
      [:div {:style {:background wrapper-bg :padding "20px"}}
       [:div {:style {:max-width "1200px" :margin "0 auto" :padding "20px"
                      :background container-bg :border-radius "8px"
                      :box-shadow "0 2px 8px rgba(0,0,0,0.1)"}}
        [:h2 {:style {:color title-color :margin-bottom "20px"}} "📄 PDF Viewer"]
        (if pdf-loaded?
          [:<>
           [:div {:style {:margin-bottom "20px" :display "flex" :gap "10px" :flex-wrap "wrap"}}
            [:input {:type "file" :accept "application/pdf" :on-change handle-file-upload
                     :style {:display "none"} :id "pdf-file-input"}]
            [:label {:for "pdf-file-input"
                     :style {:padding "10px 20px" :background "#28a745" :color "white"
                             :border-radius "5px" :cursor "pointer"}} "📁 Upload PDF"]
            [:button {:on-click #(load-pdf-url :url (:hello sample-pdfs))
                      :style {:padding "10px 20px" :background "#17a2b8" :color "white"
                              :border "none" :border-radius "5px" :cursor "pointer"}}
             "Hello World"]
            [:button {:on-click #(load-pdf-url :url (:research sample-pdfs))
                      :style {:padding "10px 20px" :background "#17a2b8" :color "white"
                              :border "none" :border-radius "5px" :cursor "pointer"}}
             "Research Paper"]
            [:button {:on-click #(load-pdf-url :url (:geotopo sample-pdfs))
                      :style {:padding "10px 20px" :background "#17a2b8" :color "white"
                              :border "none" :border-radius "5px" :cursor "pointer"}}
             "GeoTopo Book"]]
           (when (:error state)
             [:div {:style {:padding "10px" :background "#f8d7da" :color "#721c24"
                            :border "1px solid #f5c6cb" :border-radius "5px" :margin-bottom "10px"}}
              (:error state)])
           (when (:loading? state)
             [:div {:style {:text-align "center" :padding "20px"}} "Loading PDF..."])
           (when (:current-pdf state)
             [:<>
              [navigation-controls dark-mode?]
              [search-bar dark-mode?]])
           [:div {:style {:border "1px solid #dee2e6" :border-radius "5px" :padding "10px"
                          :margin-top "10px" :text-align "center" :overflow "auto"
                          :min-height "400px" :background canvas-bg}}
            [:canvas {:id "pdf-canvas"
                      :style {:max-width "100%" :height "auto"
                              :filter (get-pdf-filter (:pdf-theme state))}}]]]
          [:div {:style {:padding "20px" :background "#fff3cd" :color "#856404"
                         :border "1px solid #ffeeba" :border-radius "5px"}}
           [:h3 "⚠️ PDF.js Not Loaded"]
           [:p "Please refresh the page. PDF.js is required for this viewer."]])]])
    (finally
      (.removeEventListener js/document "keydown" keyboard-handler))))

;; Mount component
(defn ^:export mount []
  (when-let [root (.getElementById js/document "pdf-viewer-root")]
    (rdom/render [pdf-viewer] root)))

;; Auto-mount
(mount)
