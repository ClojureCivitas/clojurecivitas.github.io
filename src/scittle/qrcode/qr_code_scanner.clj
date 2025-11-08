^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Browser-Native QR Code Scanner with Scittle"
         :quarto {:author [:burinc]
                  :description "Build a QR code scanner that runs entirely in your browser using Scittle and ClojureScript - no build tools, no backend, just pure browser magic!"
                  :type :post
                  :date "2025-11-08"
                  :category :libs
                  :tags [:scittle
                         :clojurescript
                         :reagent
                         :qrcode
                         :camera
                         :webrtc
                         :no-build]
                  :keywords [:qr-code
                             :scanner
                             :camera
                             :webrtc
                             :scittle
                             :browser-native
                             :getUserMedia
                             :jsQR]}}}

(ns scittle.qrcode.qr-code-scanner
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # Browser-Native QR Code Scanner with Scittle

;; ## About This Project

;; This is part of my ongoing exploration of **browser-native development with Scittle**. In my previous articles on [Building Browser-Native Presentations](https://clojurecivitas.github.io/scittle/presentations/browser_native_slides.html), [Python + ClojureScript Integration](https://clojurecivitas.github.io/scittle/pyodide/pyodide_integration.html), and [Free Weather Data with NWS API](https://clojurecivitas.github.io/scittle/weather/weather_nws_integration.html), I've been demonstrating how to create powerful, interactive applications without any build tools.

;; Today, I'm excited to show you how to build a **QR code scanner** that runs entirely in your browser using:

;; - **Scittle** - ClojureScript interpreter (no compilation needed!)
;; - **Reagent** - React wrapper for ClojureScript
;; - **jsQR** - Pure JavaScript QR code library
;; - **WebRTC** - Native browser camera access
;; - **Canvas API** - Image processing

;; What makes this special? **Zero backend, zero build tools, zero configuration**. Just open an HTML file in your browser and start scanning QR codes!

;; ## Why QR Codes?

;; QR codes are everywhere in our daily lives:

;; - 📱 **Mobile payments** - Venmo, PayPal, Cash App
;; - 🍔 **Restaurant menus** - Contactless ordering
;; - 🎫 **Event tickets** - Concerts, movies, flights
;; - 📦 **Package tracking** - Shipping labels, inventory
;; - 🔐 **Authentication** - 2FA setup, WiFi passwords
;; - 🌐 **Website links** - Marketing, product info

;; Having the ability to scan and decode QR codes directly in a web application opens up countless possibilities for:

;; - Inventory management systems
;; - Event check-in applications
;; - Product information displays
;; - Authentication flows
;; - Contact information sharing
;; - And much more!

;; ## How QR Code Scanning Works

;; Let's understand the technology behind QR code scanning in a browser:

^:kindly/hide-code
(kind/mermaid
 "graph TB
    A[User clicks Start Camera] --> B[Request Camera Permission]
    B --> C{Permission Granted?}
    C -->|Yes| D[getUserMedia - Video Stream]
    C -->|No| E[Show Error]
    D --> F[Video Element Plays Stream]
    F --> G[Click Start Scanning]
    G --> H[500ms Interval Loop]
    H --> I[Capture Frame to Canvas]
    I --> J[Get ImageData from Canvas]
    J --> K[jsQR Library Decodes QR]
    K --> L{QR Code Found?}
    L -->|Yes| M[Store Result if Not Duplicate]
    L -->|No| H
    M --> N[Display in Results List]
    N --> H")

;; ### The Technology Stack

;; **1. WebRTC getUserMedia API**

;; Modern browsers provide access to camera and microphone through the `getUserMedia` API:

^:kindly/hide-code
(kind/code
 ";; Request camera access
(js/navigator.mediaDevices.getUserMedia
  #js {:video true :audio false})")

;; This is the same technology used by:
;; - Video conferencing apps (Zoom, Google Meet)
;; - Social media camera features (Instagram, Snapchat)
;; - WebRTC peer-to-peer video calls

;; **2. HTML5 Canvas API**

;; The Canvas API allows us to:
;; - Capture video frames as images
;; - Extract pixel data for processing
;; - Draw overlays and visual feedback

^:kindly/hide-code
(kind/code
 ";; Capture video frame to canvas
(let [ctx (.getContext canvas \"2d\")]
  (.drawImage ctx video-element 0 0 width height)
  (let [image-data (.getImageData ctx 0 0 width height)]
    ;; Now we have raw pixel data for QR detection
    ))")

;; **3. jsQR Library**

;; jsQR is a pure JavaScript QR code detection library that:
;; - Works entirely in the browser (no server needed)
;; - Processes ImageData from canvas
;; - Returns decoded QR code content
;; - Handles various QR code versions and error correction levels

^:kindly/hide-code
(kind/code
 ";; Decode QR code from image data
(let [code (js/jsQR pixel-data width height)]
  (when code
    (let [decoded-text (.-data code)]
      ;; Use the decoded text
      )))")

;; ## Browser Compatibility

;; This QR scanner works in all modern browsers:

;; - ✅ **Chrome/Edge** - Full support
;; - ✅ **Firefox** - Full support
;; - ✅ **Safari** - Full support (iOS 11+)
;; - ✅ **Opera** - Full support
;; - ⚠️ **Internet Explorer** - Not supported (no getUserMedia)

;; ### Mobile Support

;; The scanner works great on mobile devices:

;; - **iOS Safari** - Works with camera permission
;; - **Android Chrome** - Works with camera permission
;; - **Mobile Firefox** - Works with camera permission

;; The key is using `playsInline` attribute on the video element to prevent fullscreen video on iOS.

;; ## Understanding the Code Architecture

;; Let's break down how our QR scanner is structured:

;; ### State Management

;; We use a single Reagent atom to manage all application state:

^:kindly/hide-code
(kind/code
 "(defonce app-state
  (r/atom
   {:stream nil              ; Camera MediaStream
    :qr-scanning? false      ; Scanning active?
    :qr-results []           ; Array of scanned results
    :qr-scanner-interval nil ; setInterval ID
    :video-playing? false    ; Video ready?
    :error nil}))            ; Error message")

;; This simple state management pattern:
;; - Keeps everything in one place
;; - Makes state updates predictable
;; - Works perfectly with Reagent's reactivity

;; ### Keyword Arguments Pattern

;; Throughout the code, we use **keyword arguments** for better readability:

^:kindly/hide-code
(kind/code
 ";; Traditional positional arguments (harder to read)
(start-stream on-success-fn on-error-fn)

;; Keyword arguments (self-documenting!)
(start-stream!
  {:on-success (fn [stream] ...)
   :on-error (fn [error] ...)})")

;; Benefits of keyword arguments:
;;
;; - **Self-documenting** - Clear what each parameter does
;; - **Flexible** - Order doesn't matter
;; - **Optional parameters** - Easy to add defaults
;; - **Future-proof** - Add new options without breaking existing code

;; ### Core Functions

;; The scanner has several key functions:

;; **Camera Management:**
;;
;; - `start-stream!` - Request camera access with callbacks
;; - `stop-stream!` - Clean up camera resources

;; **Scanning Functions:**
;;
;; - `scan-qr-code` - Capture frame and detect QR codes
;; - `start-qr-scanning!` - Begin continuous scanning loop
;; - `stop-qr-scanning!` - Stop the scanning loop

;; **Utility Functions:**
;;
;; - `copy-to-clipboard!` - Copy results to clipboard
;; - `clear-qr-results!` - Clear all scanned results
;; - `generate-id` - Create unique IDs for results
;; - `format-timestamp` - Format scan timestamps

;; ## Privacy and Security

;; When building camera applications, privacy is crucial:

;; **🔒 What We Do Right:**

;; - **Permission-based** - Camera only activates after user approval
;; - **Client-side only** - No data sent to servers
;; - **Visual feedback** - Clear indicators when camera is active
;; - **Easy control** - Start/Stop buttons clearly visible
;; - **No storage** - Results cleared when you refresh the page

;; **💡 Best Practices:**

;; 1. Always request permission before camera access
;; 2. Provide clear visual feedback when camera is active
;; 3. Give users easy ways to stop the camera
;; 4. Don't automatically start camera without user action
;; 5. Explain what you're doing with the data

;; ## The Complete Implementation

;; Here's our full QR scanner implementation with keyword arguments throughout:

^:kindly/hide-code
(kind/code (slurp "src/scittle/qrcode/qr_scanner.cljs"))

;; ## Try It Live!

;; Ready to scan some QR codes? The scanner is embedded below. Here's how to use it:

;; **Step-by-step guide:**

;; 1. **Click "🎥 Start Camera"** - Your browser will ask for camera permission
;; 2. **Grant permission** - Allow camera access when prompted
;; 3. **Click "🔍 Start Scanning"** - The scanner will begin analyzing frames
;; 4. **Point at QR code** - Hold a QR code in front of your camera
;; 5. **View results** - Decoded content appears below automatically
;; 6. **Copy to clipboard** - Click "Copy" button to copy any result

;; **Tips for best results:**

;; - 💡 Good lighting helps QR detection
;; - 📏 Keep QR code in the center of frame
;; - 🎯 Hold steady for a second to ensure detection
;; - 📱 Works great on mobile devices too!

;; **Need a QR code to test?** Try these:

;; - Generate one at [qr-code-generator.com](https://www.qr-code-generator.com/)
;; - Use your phone's WiFi QR share feature
;; - Many products have QR codes on their packaging
;; - Your phone's wallet apps likely have QR codes

;; **Or scan these sample QR codes right now:**

;; **ClojureCivitas Blog** - Browse our collection of Clojure articles and tutorials:

(kind/hiccup
 [:div {:style {:margin "1rem 0"}}
  [:img {:src "clojurecivitas-link.png"
         :alt "ClojureCivitas Blog QR Code"
         :style {:max-width "200px"
                 :height "auto"
                 :border "1px solid #ddd"
                 :border-radius "8px"
                 :padding "0.5rem"
                 :background "white"}}]])

;; Link: [https://clojurecivitas.github.io/posts.html](https://clojurecivitas.github.io/posts.html)

;; **Clojure/conj 2025** - Join us at the premier Clojure conference:

(kind/hiccup
 [:div {:style {:margin "1rem 0"}}
  [:img {:src "clojure-conj-link.png"
         :alt "Clojure/conj 2025 QR Code"
         :style {:max-width "200px"
                 :height "auto"
                 :border "1px solid #ddd"
                 :border-radius "8px"
                 :padding "0.5rem"
                 :background "white"}}]])

;; Link: [https://2025.clojure-conj.org/](https://2025.clojure-conj.org/)

(kind/hiccup
 [:div {:style {:margin "2rem 0"
                :padding "2rem"
                :border "2px solid #e9ecef"
                :border-radius "8px"
                :background-color "#f8f9fa"}}
  [:div#qr-scanner-app {:style {:min-height "600px"}}]
  [:script {:src "https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.min.js"}]
  [:script {:type "application/x-scittle"
            :src "qr_scanner.cljs"}]])

;; ## Real-World Use Cases

;; Now that you've seen how it works, here are some practical applications:

;; ### 1. Event Check-in System

^:kindly/hide-code
(kind/code
 ";; Example: Track event attendees
(defn event-checkin []
  (let [attendees (r/atom #{})]
    (fn []
      [:div
       [qr-scanner-component
        {:on-scan (fn [result]
                   (let [ticket-id (:data result)]
                     (if (@attendees ticket-id)
                       (js/alert \"Already checked in!\")
                       (do
                         (swap! attendees conj ticket-id)
                         (js/alert \"Welcome!\")))))}]
       [:p \"Checked in: \" (count @attendees)]])))")

;; ### 2. Inventory Management

^:kindly/hide-code
(kind/code
 ";; Example: Scan product barcodes/QR codes
(defn inventory-scanner []
  (let [items (r/atom [])]
    (fn []
      [:div
       [qr-scanner-component
        {:on-scan (fn [result]
                   (swap! items conj
                          {:code (:data result)
                           :time (js/Date.now)}))}]
       [:h3 \"Scanned Items: \" (count @items)]
       (for [item @items]
         ^{:key (:time item)}
         [:div (:code item)])])))")

;; ### 3. Authentication/2FA Setup

^:kindly/hide-code
(kind/code
 ";; Example: Scan 2FA QR codes
(defn twofa-setup []
  (let [secret (r/atom nil)]
    (fn []
      [:div
       (if @secret
         [:p \"Secret key: \" @secret]
         [qr-scanner-component
          {:on-scan (fn [result]
                     ;; Parse otpauth:// URLs
                     (reset! secret (:data result)))}])])))")

;; ### 4. WiFi Network Sharing

^:kindly/hide-code
(kind/code
 ";; Example: Parse WiFi QR codes
(defn parse-wifi-qr [text]
  ;; Format: WIFI:T:WPA;S:NetworkName;P:Password;;
  (when (clojure.string/starts-with? text \"WIFI:\")
    (let [parts (clojure.string/split text #\";\")
          parse-part (fn [part]
                      (let [[k v] (clojure.string/split part #\":\")]
                        {(keyword k) v}))]
      (apply merge (map parse-part parts)))))

(defn wifi-scanner []
  [qr-scanner-component
   {:on-scan (fn [result]
              (when-let [wifi (parse-wifi-qr (:data result))]
                (js/alert (str \"Network: \" (:S wifi)))))}])")

;; ## Extending the Scanner

;; Here are some ideas to enhance the QR scanner:

;; ### Add History Persistence

;; Store scan history in localStorage:

^:kindly/hide-code
(kind/code
 "(defn save-to-localstorage! [results]
  (.setItem js/localStorage
            \"qr-history\"
            (js/JSON.stringify (clj->js results))))

(defn load-from-localstorage []
  (when-let [stored (.getItem js/localStorage \"qr-history\")]
    (js->clj (js/JSON.parse stored) :keywordize-keys true)))")

;; ### Add Visual Feedback

;; Highlight detected QR codes with canvas overlay:

^:kindly/hide-code
(kind/code
 "(defn draw-qr-overlay [ctx code]
  (let [location (.-location code)
        tl (.-topLeftCorner location)
        tr (.-topRightCorner location)
        br (.-bottomRightCorner location)
        bl (.-bottomLeftCorner location)]
    (set! (.-strokeStyle ctx) \"#00ff00\")
    (set! (.-lineWidth ctx) 4)
    (.beginPath ctx)
    (.moveTo ctx (.-x tl) (.-y tl))
    (.lineTo ctx (.-x tr) (.-y tr))
    (.lineTo ctx (.-x br) (.-y br))
    (.lineTo ctx (.-x bl) (.-y bl))
    (.closePath ctx)
    (.stroke ctx)))")

;; ### Add Sound Effects

;; Play a sound when QR code is detected:

^:kindly/hide-code
(kind/code
 "(defn play-scan-sound []
  (let [audio (js/Audio. \"scan-beep.mp3\")]
    (.play audio)))

(defn scan-qr-code [{:keys [video-element canvas-element on-success]}]
  (when (and video-element canvas-element)
    ;; ... scanning logic ...
    (when code
      (play-scan-sound)  ; Add sound feedback
      (on-success new-result))))")

;; ### Add Export Functionality

;; Export scanned results as CSV or JSON:

^:kindly/hide-code
(kind/code
 "(defn export-as-csv [results]
  (let [csv (str \"Timestamp,Data\\n\"
                 (clojure.string/join \"\\n\"
                   (map (fn [r]
                         (str (:timestamp r) \",\" (:data r)))
                        results)))
        blob (js/Blob. [csv] #js {:type \"text/csv\"})
        url (.createObjectURL js/URL blob)
        a (.createElement js/document \"a\")]
    (set! (.-href a) url)
    (set! (.-download a) \"qr-results.csv\")
    (.click a)))")

;; ### Add QR Code Generation

;; Use a library like qrcode.js to generate QR codes:

^:kindly/hide-code
(kind/code
 ";; Using QRCode.js library
(defn generate-qr-code [text container-id]
  (js/QRCode. (.getElementById js/document container-id)
              #js {:text text
                   :width 256
                   :height 256}))")

;; ## Performance Considerations

;; When building real-time camera applications, performance matters:

;; **Optimization Tips:**

;; 1. **Scan interval** - We scan every 500ms. Adjust based on your needs:
;;    - Faster (100-200ms) = More responsive but higher CPU usage
;;    - Slower (1000ms) = Lower CPU usage but might miss quick scans

;; 2. **Canvas size** - Smaller canvas = faster processing:

^:kindly/hide-code
(kind/code
 ";; Scale down for faster processing
(defn scan-qr-code [{:keys [video-element canvas-element]}]
  (let [scale 0.5  ; Process at 50% resolution
        width (* scale (.-videoWidth video-element))
        height (* scale (.-videoHeight video-element))]
    ;; ... rest of scanning logic ...))")

;; 3. **Duplicate detection** - We check for duplicates before adding results:

^:kindly/hide-code
(kind/code
 "(let [existing (some #(= (:data %) qr-data)
                       (:qr-results @app-state))]
   (when-not existing
     ;; Only add if not duplicate
     (swap! app-state update :qr-results conj new-result)))")

;; 4. **Cleanup** - Always stop intervals and streams when done:

^:kindly/hide-code
(kind/code
 "(defn cleanup! []
  (stop-qr-scanning!)
  (stop-stream!))")

;; ## Browser DevTools Tips

;; When debugging camera applications:

;; **Chrome DevTools:**

;; 1. **Simulate camera** - Settings → Content Settings → Camera
;; 2. **Inspect video** - Right-click video element → Inspect
;; 3. **Network throttling** - Test on slower connections
;; 4. **Mobile emulation** - Test responsive behavior

;; **Firefox DevTools:**

;; 1. **Camera permissions** - about:permissions
;; 2. **Console logging** - Monitor camera events
;; 3. **Responsive design mode** - Test mobile layouts

;; ## Wrapping Up

;; **What We've Built:**

;; We created a complete QR code scanner that:

;; - ✅ Runs entirely in the browser (no backend!)
;; - ✅ Uses zero build tools (pure Scittle!)
;; - ✅ Has clean keyword-argument APIs
;; - ✅ Works on desktop and mobile
;; - ✅ Respects user privacy
;; - ✅ Is fully extensible

;; **Key Takeaways:**

;; 1. **Browser APIs are powerful** - Camera, Canvas, Clipboard, etc.
;; 2. **Scittle removes friction** - No compilation, instant feedback
;; 3. **Keyword arguments improve clarity** - Self-documenting code
;; 4. **Privacy matters** - Always be transparent with camera usage
;; 5. **Real-world applications** - QR codes are everywhere!

;; **What's Next?**

;; You can extend this scanner in many ways:

;; - Add barcode support (using different detection library)
;; - Implement history and favorites
;; - Add URL preview before opening links
;; - Create multi-scanner for bulk operations
;; - Add AR overlays and animations
;; - Integrate with your backend APIs

;; **Resources:**

;; - [jsQR Library](https://github.com/cozmo/jsQR) - QR code detection
;; - [MDN getUserMedia](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia) - Camera API docs
;; - [MDN Canvas API](https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API) - Canvas reference
;; - [Scittle Documentation](https://github.com/babashka/scittle) - Learn more about Scittle
;; - [Reagent Guide](https://reagent-project.github.io/) - React in ClojureScript

;; **Thank You!**

;; I hope this tutorial inspires you to build your own camera-based applications. The combination of modern browser APIs, ClojureScript, and Scittle makes it incredibly easy to create powerful, interactive experiences without any build tools or backend infrastructure.

;; Have fun scanning! 📸✨

;; ---

;; *All source code is available in the article above. Copy, modify, and share freely!*

;; ## Connect & Share

;; **Questions or ideas?** I'd love to hear what you build with this scanner! Connect with me to share your projects, ask questions, or just say hi:

(kind/hiccup
 [:div {:style {:margin "1.5rem 0"}}
  [:img {:src "linkedin-link.png"
         :alt "Connect on LinkedIn"
         :style {:max-width "200px"
                 :height "auto"
                 :border "1px solid #ddd"
                 :border-radius "8px"
                 :padding "0.5rem"
                 :background "white"}}]])

;; Scan the QR code above or connect with me on [LinkedIn](https://linkedin.com/in/burinc) - I'm always excited to discuss Clojure, browser-native development, and innovative web applications!

;; Share your QR scanner projects with the community and let's build amazing things together! 🚀
