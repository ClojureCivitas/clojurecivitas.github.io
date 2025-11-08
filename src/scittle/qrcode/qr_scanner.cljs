(ns scittle.qrcode.qr-scanner
  "QR Code Scanner - Browser-native QR code scanning with Scittle"
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

;; ============================================================================
;; State Management
;; ============================================================================

(defonce app-state
  (r/atom
   {:stream nil
    :qr-scanning? false
    :qr-results []
    :qr-scanner-interval nil
    :video-playing? false
    :error nil
    :scan-flash? false
    :toast-message nil
    :latest-result-id nil}))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn generate-id
  "Generate a unique ID for QR scan results"
  []
  (str (random-uuid)))

(defn format-timestamp
  "Format a timestamp into a readable string"
  [timestamp]
  (.toLocaleString (js/Date. timestamp)))

(defn get-video-constraints
  "Get getUserMedia constraints for video-only capture"
  []
  #js {:video true :audio false})

;; ============================================================================
;; Visual Feedback Functions
;; ============================================================================

(defn show-scan-flash!
  "Show a brief green flash to indicate successful scan"
  []
  (swap! app-state assoc :scan-flash? true)
  (js/setTimeout #(swap! app-state assoc :scan-flash? false) 300))

(defn show-toast!
  "Show a success toast message
  
  Usage:
    (show-toast! {:message \"QR Code detected!\"})"
  [{:keys [message duration]}]
  (let [display-duration (or duration 2000)]
    (swap! app-state assoc :toast-message message)
    (js/setTimeout #(swap! app-state assoc :toast-message nil) display-duration)))

(defn play-beep!
  "Play a simple beep sound using Web Audio API"
  []
  (try
    (let [audio-ctx (or (js/AudioContext.) (js/webkitAudioContext.))
          oscillator (.createOscillator audio-ctx)
          gain (.createGain audio-ctx)]
      (set! (.-type oscillator) "sine")
      (set! (.-frequency.value oscillator) 800)
      (set! (.-value (.-gain gain)) 0.3)
      (.connect oscillator gain)
      (.connect gain (.-destination audio-ctx))
      (.start oscillator)
      (.stop oscillator (+ (.-currentTime audio-ctx) 0.1)))
    (catch js/Error e
      ;; Silently fail if audio not available
      nil)))

;; ============================================================================
;; Media Stream Management
;; ============================================================================

(defn stop-stream!
  "Stop the current media stream and clean up"
  []
  (when-let [stream (:stream @app-state)]
    (doseq [track (.getTracks stream)]
      (.stop track))
    (swap! app-state assoc :stream nil :video-playing? false)))

(defn start-stream!
  "Start camera stream
  
  Usage:
    (start-stream! 
      {:on-success (fn [stream] ...)
       :on-error (fn [error] ...)})"
  [{:keys [on-success on-error]}]
  (stop-stream!)
  (-> (js/navigator.mediaDevices.getUserMedia (get-video-constraints))
      (.then (fn [stream]
               (swap! app-state assoc :stream stream :error nil)
               (when on-success (on-success stream)))
             (fn [err]
               (let [error-msg (str "Failed to start camera: " (.-message err))]
                 (swap! app-state assoc :error error-msg)
                 (when on-error (on-error error-msg)))))))

;; ============================================================================
;; QR Code Scanning Functions
;; ============================================================================

(defn scan-qr-code
  "Scan for QR code in video feed
  
  Usage:
    (scan-qr-code
      {:video-element video-el
       :canvas-element canvas-el
       :on-success (fn [qr-data] ...)})"
  [{:keys [video-element canvas-element on-success]}]
  (when (and video-element canvas-element)
    (let [ctx (.getContext canvas-element "2d" #js {:willReadFrequently true})
          width (.-videoWidth video-element)
          height (.-videoHeight video-element)]
      (when (and (> width 0) (> height 0))
        (set! (.-width canvas-element) width)
        (set! (.-height canvas-element) height)
        (.drawImage ctx video-element 0 0 width height)
        (let [image-data (.getImageData ctx 0 0 width height)
              code (js/jsQR (.-data image-data) width height)]
          (when code
            (let [qr-data (.-data code)
                  existing (some #(= (:data %) qr-data) (:qr-results @app-state))]
              (when-not existing
                (let [new-result {:id (generate-id)
                                  :data qr-data
                                  :timestamp (js/Date.now)}]
                  ;; Visual feedback
                  (show-scan-flash!)
                  (show-toast! {:message "✅ QR Code detected!"})
                  (play-beep!)

                  ;; Store result
                  (swap! app-state assoc :latest-result-id (:id new-result))
                  (swap! app-state update :qr-results conj new-result)

                  ;; Clear highlight after 2 seconds
                  (js/setTimeout #(swap! app-state assoc :latest-result-id nil) 2000)

                  (when on-success (on-success new-result)))))))))))

(defn start-qr-scanning!
  "Start continuous QR code scanning
  
  Usage:
    (start-qr-scanning!
      {:video-element video-el
       :canvas-element canvas-el
       :on-scan (fn [result] ...)})"
  [{:keys [video-element canvas-element on-scan]}]
  (when-not (:qr-scanning? @app-state)
    (let [scan-fn #(scan-qr-code {:video-element video-element
                                  :canvas-element canvas-element
                                  :on-success on-scan})
          interval-id (js/setInterval scan-fn 500)]
      (swap! app-state assoc
             :qr-scanning? true
             :qr-scanner-interval interval-id))))

(defn stop-qr-scanning!
  "Stop QR code scanning"
  []
  (when-let [interval-id (:qr-scanner-interval @app-state)]
    (js/clearInterval interval-id))
  (swap! app-state assoc
         :qr-scanning? false
         :qr-scanner-interval nil))

(defn clear-qr-results!
  "Clear all scanned QR results"
  []
  (swap! app-state assoc :qr-results []))

;; ============================================================================
;; Clipboard Functions
;; ============================================================================

(defn copy-to-clipboard!
  "Copy text to clipboard
  
  Usage:
    (copy-to-clipboard!
      {:text \"Hello World\"
       :on-success (fn [] ...)
       :on-error (fn [error] ...)})"
  [{:keys [text on-success on-error]}]
  (-> (js/navigator.clipboard.writeText text)
      (.then (fn []
               (when on-success (on-success)))
             (fn [err]
               (when on-error (on-error err))))))

;; ============================================================================
;; UI Components
;; ============================================================================

(defn qr-results-display
  "Display scanned QR code results"
  []
  (let [results (:qr-results @app-state)
        latest-id (:latest-result-id @app-state)]
    [:div.qr-results
     [:h3 {:style {:margin-top "1.5rem"
                   :margin-bottom "1rem"
                   :font-size "1.25rem"
                   :font-weight "600"}}
      "Scanned QR Codes"]
     [:button
      {:on-click clear-qr-results!
       :disabled (empty? results)
       :style {:padding "0.5rem 1rem"
               :margin-bottom "1rem"
               :background-color (if (empty? results) "#ccc" "#dc3545")
               :color "white"
               :border "none"
               :border-radius "4px"
               :cursor (if (empty? results) "not-allowed" "pointer")
               :font-size "0.875rem"}}
      "Clear All"]
     (if (empty? results)
       [:p {:style {:color "#6c757d"
                    :font-style "italic"}}
        "No QR codes scanned yet"]
       [:div.results-list
        (for [result (reverse results)]
          (let [is-latest? (= (:id result) latest-id)]
            ^{:key (:id result)}
            [:div.qr-result-item
             {:style {:margin-bottom "0.75rem"
                      :padding "1rem"
                      :border (if is-latest? "2px solid #198754" "1px solid #ddd")
                      :border-radius "6px"
                      :word-break "break-all"
                      :background-color (if is-latest? "#d1e7dd" "#f8f9fa")
                      :transition "all 0.3s ease"
                      :box-shadow (if is-latest? "0 0 10px rgba(25, 135, 84, 0.3)" "none")}}
             [:div {:style {:display "flex"
                            :justify-content "space-between"
                            :align-items "flex-start"
                            :gap "1rem"}}
              [:div {:style {:flex "1"}}
               (when is-latest?
                 [:div {:style {:color "#198754"
                                :font-weight "bold"
                                :font-size "0.75rem"
                                :margin-bottom "0.5rem"}}
                  "✨ NEW"])
               [:code {:style {:display "block"
                               :padding "0.5rem"
                               :background-color "white"
                               :border "1px solid #e9ecef"
                               :border-radius "4px"
                               :font-size "0.875rem"
                               :overflow-wrap "break-word"}}
                (:data result)]
               [:div {:style {:margin-top "0.5rem"}}
                [:small {:style {:color "#6c757d"
                                 :font-size "0.75rem"}}
                 (format-timestamp (:timestamp result))]]]
              [:button
               {:on-click #(copy-to-clipboard!
                            {:text (:data result)
                             :on-success (fn [] (js/alert "Copied to clipboard!"))
                             :on-error (fn [_] (js/alert "Failed to copy"))})
                :style {:padding "0.5rem 1rem"
                        :background-color "#0d6efd"
                        :color "white"
                        :border "none"
                        :border-radius "4px"
                        :cursor "pointer"
                        :font-size "0.875rem"
                        :white-space "nowrap"
                        :flex-shrink "0"}}
               "Copy"]]]))])]))

(defn qr-scanner-component
  "Main QR scanner component"
  []
  (let [state @app-state]
    [:div.qr-scanner
     {:style {:max-width "800px"
              :margin "0 auto"
              :padding "1rem"
              :position "relative"}}

     ;; Toast notification
     (when (:toast-message state)
       [:div.toast
        {:style {:position "fixed"
                 :top "2rem"
                 :right "2rem"
                 :padding "1rem 1.5rem"
                 :background-color "#198754"
                 :color "white"
                 :border-radius "8px"
                 :box-shadow "0 4px 12px rgba(0,0,0,0.15)"
                 :font-weight "600"
                 :z-index "9999"
                 :animation "slideIn 0.3s ease-out"}}
        (:toast-message state)])

     ;; Title
     [:h2 {:style {:margin-bottom "1.5rem"
                   :font-size "1.75rem"
                   :font-weight "700"
                   :color "#212529"}}
      "📷 QR Code Scanner"]

     ;; Error display
     (when (:error state)
       [:div {:style {:padding "1rem"
                      :margin-bottom "1rem"
                      :background-color "#f8d7da"
                      :border "1px solid #f5c2c7"
                      :border-radius "6px"
                      :color "#842029"}}
        [:strong "Error: "] (:error state)])

     ;; Control buttons
     [:div.controls
      {:style {:display "flex"
               :gap "0.5rem"
               :flex-wrap "wrap"
               :margin-bottom "1rem"}}
      [:button
       {:on-click #(start-stream! {})
        :disabled (boolean (:stream state))
        :style {:padding "0.75rem 1.5rem"
                :background-color (if (:stream state) "#6c757d" "#198754")
                :color "white"
                :border "none"
                :border-radius "6px"
                :cursor (if (:stream state) "not-allowed" "pointer")
                :font-size "1rem"
                :font-weight "500"}}
       "🎥 Start Camera"]

      [:button
       {:on-click (fn []
                    (when-let [video (js/document.getElementById "qr-video")]
                      (when-let [canvas (js/document.getElementById "qr-canvas")]
                        (start-qr-scanning! {:video-element video
                                             :canvas-element canvas}))))
        :disabled (or (not (:stream state)) (:qr-scanning? state))
        :style {:padding "0.75rem 1.5rem"
                :background-color (if (or (not (:stream state)) (:qr-scanning? state))
                                    "#6c757d"
                                    "#0d6efd")
                :color "white"
                :border "none"
                :border-radius "6px"
                :cursor (if (or (not (:stream state)) (:qr-scanning? state))
                          "not-allowed"
                          "pointer")
                :font-size "1rem"
                :font-weight "500"}}
       "🔍 Start Scanning"]

      [:button
       {:on-click stop-qr-scanning!
        :disabled (not (:qr-scanning? state))
        :style {:padding "0.75rem 1.5rem"
                :background-color (if (:qr-scanning? state) "#ffc107" "#6c757d")
                :color (if (:qr-scanning? state) "#000" "white")
                :border "none"
                :border-radius "6px"
                :cursor (if (:qr-scanning? state) "pointer" "not-allowed")
                :font-size "1rem"
                :font-weight "500"}}
       "⏸️ Stop Scanning"]

      [:button
       {:on-click (fn []
                    (stop-qr-scanning!)
                    (stop-stream!))
        :disabled (not (:stream state))
        :style {:padding "0.75rem 1.5rem"
                :background-color (if (:stream state) "#dc3545" "#6c757d")
                :color "white"
                :border "none"
                :border-radius "6px"
                :cursor (if (:stream state) "pointer" "not-allowed")
                :font-size "1rem"
                :font-weight "500"}}
       "🛑 Stop Camera"]]

     ;; Scanning indicator
     (when (:qr-scanning? state)
       [:div.scanning-indicator
        {:style {:padding "0.75rem"
                 :margin-bottom "1rem"
                 :background-color "#d1e7dd"
                 :border "1px solid #badbcc"
                 :border-radius "6px"
                 :color "#0f5132"
                 :font-weight "600"
                 :text-align "center"}}
        "📡 Scanning for QR codes..."])

     ;; Video container with flash effect
     [:div.video-container
      {:style {:position "relative"
               :margin-bottom "1.5rem"
               :border-radius "8px"
               :overflow "hidden"
               :background-color "#000"
               :box-shadow (if (:scan-flash? state)
                             "0 0 30px rgba(25, 135, 84, 0.8)"
                             "none")
               :transition "box-shadow 0.3s ease"}}
      (when (:scan-flash? state)
        [:div {:style {:position "absolute"
                       :top "0"
                       :left "0"
                       :right "0"
                       :bottom "0"
                       :background-color "rgba(25, 135, 84, 0.3)"
                       :z-index "10"
                       :pointer-events "none"}}])
      (when (:stream state)
        [:div
         [:video#qr-video
          {:ref (fn [el]
                  (when (and el (:stream state))
                    (set! (.-srcObject el) (:stream state))
                    (when-not (:video-playing? state)
                      (.then (.play el)
                             #(swap! app-state assoc :video-playing? true)
                             #(swap! app-state assoc
                                     :error (str "Video play error: " %))))))
           :autoPlay true
           :playsInline true
           :muted true
           :style {:width "100%"
                   :max-width "100%"
                   :display "block"
                   :border-radius "8px"}}]
         [:canvas#qr-canvas
          {:style {:display "none"}}]])]

     ;; Results display
     [qr-results-display]

     ;; Instructions
     [:div.info
      {:style {:margin-top "2rem"
               :padding "1rem"
               :background-color "#e7f3ff"
               :border-left "4px solid #0d6efd"
               :border-radius "4px"}}
      [:p {:style {:margin "0"
                   :color "#084298"
                   :font-size "0.875rem"}}
       "💡 " [:strong "How to use:"]
       " Click 'Start Camera' to access your webcam, then click 'Start Scanning'. "
       "Point your camera at any QR code and it will be automatically detected and decoded. "
       "You can copy the results to your clipboard by clicking the 'Copy' button."]]]))

;; ============================================================================
;; Mount point
;; ============================================================================

(defn ^:export mount-qr-scanner
  "Mount the QR scanner component to the DOM"
  []
  (when-let [el (js/document.getElementById "qr-scanner-app")]
    (rdom/render [qr-scanner-component] el)))

;; Auto-mount when script loads
(mount-qr-scanner)
