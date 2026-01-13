^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Browser-Native PDF Viewer with ClojureScript & Scittle"
         :quarto {:author [:burinc]
                  :description "Build a complete PDF viewer in the browser! Navigate PDFs, search text, adjust zoom, rotate pages, and switch themes - all without build tools or server-side rendering!"
                  :type :post
                  :date "2025-11-14"
                  :category :pdf
                  :image "pdf-viewer-01.png"
                  :tags [:scittle
                         :clojurescript
                         :reagent
                         :pdf
                         :pdfjs
                         :document-viewer
                         :browser-native
                         :no-build
                         :search
                         :text-extraction
                         :interactive]
                  :keywords [:pdf-viewer
                             :pdfjs-integration
                             :document-navigation
                             :text-search
                             :browser-pdf
                             :client-side-pdf
                             :pdf-rendering
                             :theme-switching
                             :zoom-controls
                             :page-rotation
                             :scittle-app]}}}

(ns scittle.pdf.pdf-viewer
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # Browser-Native PDF Viewer with ClojureScript & Scittle

;; ## About This Project

;; Need to display PDFs in your web application? Today, I'll show you how to build a complete PDF viewer using PDF.js, ClojureScript, and Scittle - with zero build tools!

;; This is part of my ongoing exploration of browser-native development with Scittle. Check out my previous articles:
;;
;; - [Web Audio API Playground](https://clojurecivitas.github.io/scittle/audio/audio_playground.html) - Sound synthesis without audio files
;; - [Build Galaga with ClojureScript & Scittle](https://clojurecivitas.github.io/scittle/games/galaga.html) - Classic arcade game
;; - [Build Asteroids with Scittle](https://clojurecivitas.github.io/scittle/games/asteroids_article.html) - Space shooter with physics
;; - [Browser-Native QR Code Scanner](https://clojurecivitas.github.io/scittle/qrcode/qr_code_scanner.html) - QR scanning with camera
;; - [Building Browser-Native Presentations](https://clojurecivitas.github.io/scittle/presentations/browser_native_slides.html) - Interactive presentation systems

;; This project demonstrates professional document handling entirely in the browser!

;; ## What We're Building

;; We'll create a comprehensive PDF viewer featuring:

;; - **PDF Loading** from URLs or file uploads
;; - **Page Navigation** with previous/next and jump-to-page
;; - **Zoom Controls** with fit-to-width, fit-to-page, and custom zoom
;; - **Page Rotation** in 90-degree increments
;; - **Text Search** across all pages with highlighting
;; - **Theme Switching** for comfortable reading (normal, dark, sepia, high-contrast)
;; - **Keyboard Shortcuts** for power users (Ctrl+F for search, Escape to close)
;; - **Mobile-Friendly** responsive design that works everywhere

;; All features work entirely in the browser - no server required!

;; ## Why PDF.js?

;; PDF.js offers unique advantages for web-based PDF viewing:

;; ### Browser-Native
;;
;; - No plugins or extensions required
;; - Works on all modern browsers
;; - Fully client-side processing
;; - No server-side dependencies

;; ### Full Control
;;
;; - Custom UI and styling
;; - Text extraction and search
;; - Page rendering control
;; - Theme customization

;; ### Open Source
;;
;; - Developed by Mozilla
;; - Active community support
;; - Well-documented API
;; - Free to use

;; ## Architecture Overview

^:kindly/hide-code
(kind/mermaid
 "graph TD
    A[User] --> B[File Upload / URL]
    B --> C[PDF.js Loader]
    C --> D[PDF Document]
    D --> E[Page Extraction]
    E --> F[Canvas Rendering]
    F --> G[Display]

    D --> H[Text Extraction]
    H --> I[Search Engine]
    I --> J[Highlight Results]
    J --> G

    K[Controls] --> L[Navigation]
    K --> M[Zoom]
    K --> N[Rotation]
    K --> O[Theme]

    L --> E
    M --> F
    N --> F
    O --> G

    style A fill:#4caf50
    style D fill:#2196f3
    style G fill:#ff9800")

;; ### Core Components

;; - **State Management**:
;; Reagent atoms track PDF state, current page, zoom, rotation, search results, and UI state
;; - **PDF.js Integration**:
;; Load and render PDF documents using Mozilla's PDF.js library
;; - **Canvas Rendering**: High-quality page rendering with HiDPI support
;; - **Text Extraction**: Extract text from pages for search functionality
;; - **Search Engine**: Find and highlight text matches across all pages
;; - **Theme System**: Apply CSS filters for different reading modes

;; ## PDF.js Integration

;; ### Loading PDF.js

;; PDF.js is loaded via CDN in the article (we'll see this later):

;; ```html
;; <script src="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js"></script>
;; <script>
;;   pdfjsLib.GlobalWorkerOptions.workerSrc =
;;     'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
;; </script>
;; ```

;; ### Loading a PDF Document

;; PDF.js provides a simple API for loading PDFs from URLs or File objects:

;; ```clojure
;; (defn load-pdf-document
;;   [& {:keys [source]}]
;;   (js/Promise.
;;    (fn [resolve reject]
;;      (if (and (exists? js/pdfjsLib) js/pdfjsLib)
;;        (cond
;;          ;; Load from URL
;;          (string? source)
;;          (let [loading-task (.getDocument js/pdfjsLib #js {:url source})]
;;            (-> (.-promise loading-task)
;;                (.then resolve reject)))
;;
;;          ;; Load from File object
;;          (instance? js/File source)
;;          (let [file-reader (js/FileReader.)]
;;            (set! (.-onload file-reader)
;;                  (fn [e]
;;                    (let [array-buffer (.. e -target -result)
;;                          loading-task (.getDocument js/pdfjsLib
;;                                                     #js {:data array-buffer})]
;;                      (-> (.-promise loading-task)
;;                          (.then resolve reject)))))
;;            (.readAsArrayBuffer file-reader source)))
;;        (reject (js/Error. "PDF.js not loaded"))))))
;; ```

;; **Key Points**:
;;
;; - Returns a JavaScript Promise for async loading
;; - Supports both URLs and File objects
;; - Uses keyword arguments for clarity
;; - Includes error handling

;; ## State Management with Reagent

;; ### Centralized State Atom

;; All PDF viewer state is stored in a single Reagent atom:

;; ```clojure
;; (defonce pdf-state
;;   (r/atom {:current-pdf       nil       ; PDF.js document object
;;            :current-page      1         ; Current page number
;;            :total-pages       0         ; Total pages in document
;;            :zoom-level        1.0       ; Zoom scale factor
;;            :rotation          0         ; Page rotation (0, 90, 180, 270)
;;            :loading?          false     ; Loading indicator
;;            :error             nil       ; Error message
;;            :fit-mode          :width    ; :width, :page, :actual, :custom
;;            :pdf-theme         :normal   ; Theme for reading
;;            :search-state      {...}}))  ; Search functionality state
;; ```

;; ### State Update Functions

;; Clean, functional state updates with keyword arguments:

;; ```clojure
;; (defn go-to-page! [page-num]
;;   (let [total (:total-pages @pdf-state)]
;;     (when (and (> page-num 0) (<= page-num total))
;;       (swap! pdf-state assoc :current-page page-num))))
;;
;; (defn set-zoom! [zoom]
;;   (swap! pdf-state assoc :zoom-level zoom :fit-mode :custom))
;;
;; (defn rotate-page! []
;;   (swap! pdf-state update :rotation #(mod (+ % 90) 360)))
;; ```

;; **Benefits**:

;; - Centralized state = easier debugging
;; - Pure functions = predictable updates
;; - Keyword arguments = self-documenting code
;; - Validation = prevents invalid states

;; ## Page Rendering

;; ### Canvas Rendering

;; PDF pages are rendered to HTML canvas elements:

;; ```clojure
;; (defn render-pdf-page
;;   [& {:keys [page canvas-elem scale rotation]}]
;;   (js/Promise.
;;    (fn [resolve reject]
;;      (try
;;        (let [viewport (.getViewport page #js {:scale scale
;;                                               :rotation rotation})
;;              context (.getContext canvas-elem "2d")
;;              output-scale (or (.-devicePixelRatio js/window) 1)
;;              width (* (.-width viewport) output-scale)
;;              height (* (.-height viewport) output-scale)]
;;          ;; Set canvas dimensions for HiDPI screens
;;          (set! (.-width canvas-elem) width)
;;          (set! (.-height canvas-elem) height)
;;          (set! (.. canvas-elem -style -width)
;;                (str (.-width viewport) "px"))
;;          (set! (.. canvas-elem -style -height)
;;                (str (.-height viewport) "px"))
;;          ;; Scale for HiDPI
;;          (when (not= output-scale 1)
;;            (.setTransform context output-scale 0 0 output-scale 0 0))
;;          ;; Render the page
;;          (let [render-context #js {:canvasContext context
;;                                    :viewport viewport}
;;                render-task (.render page render-context)]
;;            (-> (.-promise render-task)
;;                (.then #(resolve {:success true :page page}) reject))))
;;        (catch js/Error e (reject e))))))
;; ```

;; **Key Techniques**:
;;
;; - **HiDPI Support**: Scales canvas for retina displays
;; - **Viewport Control**: Manages page dimensions and Promise
;; - **rotation-based**: Async rendering with proper error handling
;; - **Keyword Arguments**: Clear parameter passing

;; ### Fit Modes

;; Calculate appropriate scale based on fit mode:

;; ```clojure
;; (defn calculate-scale-for-fit
;;   [& {:keys [page container-width container-height fit-mode rotation]}]
;;   (let [viewport (.getViewport page #js {:scale 1.0 :rotation rotation})
;;         page-width (.-width viewport)
;;         page-height (.-height viewport)]
;;     (case fit-mode
;;       :width (/ container-width page-width)    ; Fit to width
;;       :page (min (/ container-width page-width)  ; Fit entire page
;;                  (/ container-height page-height))
;;       :actual 1.0                              ; Actual size
;;       1.0)))                                   ; Default
;; ```

;; **Fit Modes**:
;;
;; - `:width` - Maximize width, may scroll vertically
;; - `:page` - Fit entire page in viewport
;; - `:actual` - Show at 100% scale (1:1)
;; - `:custom` - User-controlled zoom level

;; ## Text Search Implementation

;; ### Text Extraction

;; Extract searchable text from PDF pages:

;; ```clojure
;; (defn get-pdf-text [page]
;;   (-> (.getTextContent page)
;;       (.then (fn [text-content]
;;                (let [items (.-items text-content)]
;;                  (str/join " " (map #(.-str %) items)))))))
;; ```

;; ### Finding Matches

;; Search text with case-sensitivity support:

;; ```clojure
;; (defn find-text-matches [text query case-sensitive?]
;;   (if (or (empty? text) (empty? query))
;;     []
;;     (let [search-text (if case-sensitive? text (str/lower-case text))
;;           search-query (if case-sensitive? query (str/lower-case query))
;;           find-indexes (fn [string substring]
;;                          (loop [index 0 matches []]
;;                            (let [found (.indexOf string substring index)]
;;                              (if (= found -1)
;;                                matches
;;                                (recur (inc found)
;;                                       (conj matches found))))))
;;           positions (find-indexes search-text search-query)]
;;       (mapv (fn [index]
;;               {:index index
;;                :length (count query)
;;                :context (let [start (max 0 (- index 30))
;;                               end (min (count text)
;;                                       (+ index (count query) 30))]
;;                           (subs text start end))})
;;             positions))))
;; ```

;; **Search Features**:
;;
;; - Case-sensitive option
;; - Multiple matches per page
;; - Context extraction (30 chars before/after)
;; - Efficient indexOf-based search

;; ### Search All Pages

;; Search across entire document with caching:

;; ```clojure
;; (defn search-all-pages [pdf-doc query case-sensitive?]
;;   (let [total-pages (:total-pages @pdf-state)
;;         promises (mapv #(search-page pdf-doc % query case-sensitive?)
;;                        (range 1 (inc total-pages)))]
;;     (-> (js/Promise.all (clj->js promises))
;;         (.then (fn [results]
;;                  (filterv #(seq (:matches %))
;;                          (js->clj results :keywordize-keys true)))))))
;; ```

;; **Optimizations**:
;;
;; - Text caching per page
;; - Parallel page searches
;; - Filter out pages with no matches
;; - Return only pages with results

;; ## Theme System

;; ### CSS Filters for Reading Modes

;; Different themes use CSS filters to modify the rendered PDF:

;; ```clojure
;; (defn get-pdf-filter [theme]
;;   (case theme
;;     :dark "invert(0.88) hue-rotate(180deg) contrast(1.2) brightness(1.1)"
;;     :sepia "sepia(1) contrast(0.85) brightness(0.9) saturate(0.8)"
;;     :high-contrast "contrast(1.5) brightness(1.1) saturate(0.6)"
;;     "none"))
;; ```

;; **Theme Modes**:
;;
;; - **Normal**: No filter, original colors
;; - **Dark Mode**: Inverted colors for night reading
;; - **Sepia**: Warm, paper-like tone
;; - **High Contrast**: Enhanced contrast for accessibility

;; ### LocalStorage Persistence

;; Save user's theme preference:

;; ```clojure
;; (defn set-pdf-theme! [theme]
;;   (swap! pdf-state assoc :pdf-theme theme)
;;   (when (exists? js/localStorage)
;;     (.setItem js/localStorage "pdf-theme" (name theme))))
;;
;; (defn load-pdf-theme! []
;;   (when (exists? js/localStorage)
;;     (when-let [saved-theme (.getItem js/localStorage "pdf-theme")]
;;       (swap! pdf-state assoc :pdf-theme (keyword saved-theme)))))
;; ```

;; ## UI Components

;; ### Navigation Controls

;; Complete navigation interface with keyword arguments:

;; ```clojure
;; (defn navigation-controls [dark-mode?]
;;   (let [state @pdf-state
;;         current-page (:current-page state)
;;         total-pages (:total-pages state)
;;         zoom (:zoom-level state)
;;         fit-mode (:fit-mode state)]
;;     [:div {:style {...}}
;;      ;; Previous/Next buttons
;;      [:button {:on-click #(do (prev-page!)
;;                               (when-let [pdf (:current-pdf @pdf-state)]
;;                                 (load-and-render-page
;;                                   :pdf-doc pdf
;;                                   :page-num (:current-page @pdf-state)
;;                                   :canvas-id "pdf-canvas")))} "◂"]
;;      ;; Page indicator
;;      [:span (str current-page " / " total-pages)]
;;      ;; Zoom controls
;;      [:button {:on-click #(do (set-zoom! (* zoom 0.8))
;;                               (load-and-render-page ...))} "−"]
;;      ;; Fit mode buttons
;;      [:button {:on-click #(do (set-fit-mode! :width)
;;                               (load-and-render-page ...))} "⇔"]
;;      ;; Theme selector
;;      [pdf-theme-selector dark-mode?]]))
;; ```

;; **Features**:
;;
;; - Inline styles (no external CSS)
;; - Disabled state handling
;; - Visual feedback
;; - Keyboard shortcuts

;; ### Search Bar Component

;; Interactive search interface:

;; ```clojure
;; (defn search-bar [dark-mode?]
;;   (let [search-state (:search-state @pdf-state)
;;         local-query (r/atom (:query search-state))]
;;     (fn [dark-mode?]
;;       [:div {:style {:display (if (:active? search-state) "flex" "none")
;;                      ...}}
;;        [:input {:type "text"
;;                 :placeholder "Search in PDF..."
;;                 :value @local-query
;;                 :on-change #(reset! local-query (.. % -target -value))
;;                 :on-key-press #(when (= (.-key %) "Enter")
;;                                  (perform-search @local-query))}]
;;        [:button {:on-click #(perform-search @local-query)} "Search"]
;;        ;; Results counter
;;        (when (> (:total-matches search-state) 0)
;;          [:span (str (inc (:current-match-index search-state))
;;                     " / "
;;                     (:total-matches search-state))])
;;        ;; Previous/Next match buttons
;;        [:button {:on-click navigate-prev-match} "◂"]
;;        [:button {:on-click navigate-next-match} "▸"]
;;        ;; Case-sensitive toggle
;;        [:input {:type "checkbox"
;;                 :checked (:case-sensitive? search-state)
;;                 :on-change toggle-case-sensitive!}]])))
;; ```

;; ## Keyboard Shortcuts

;; ### Implementing Shortcuts

;; Keyboard event handling for power users:

;; ```clojure
;; (defn pdf-viewer []
;;   (r/with-let [_ (load-pdf-theme!)
;;                keyboard-handler
;;                (fn [e]
;;                  (cond
;;                    ;; Ctrl+F or Cmd+F - Open search
;;                    (and (or (.-ctrlKey e) (.-metaKey e))
;;                         (= (.-key e) "f"))
;;                    (do (.preventDefault e) (open-search!))
;;
;;                    ;; Escape - Close search
;;                    (and (= (.-key e) "Escape")
;;                         (get-in @pdf-state [:search-state :active?]))
;;                    (close-search!)))
;;                _ (.addEventListener js/document "keydown" keyboard-handler)]
;;     ;; Component body...
;;     (finally
;;       (.removeEventListener js/document "keydown" keyboard-handler))))
;; ```

;; **Supported Shortcuts**:
;;
;; - `Ctrl+F` / `Cmd+F` - Open search
;; - `Escape` - Close search
;; - `Enter` - Execute search
;; - Arrow keys in page jump input

;; ## Performance Optimizations

;; ### Text Caching

;; Cache extracted text to avoid re-extraction:

;; ```clojure
;; (defn cache-page-text! [page-num text]
;;   (swap! pdf-state assoc-in [:search-state :page-text-cache page-num] text))
;;
;; (defn get-cached-page-text [page-num]
;;   (get-in @pdf-state [:search-state :page-text-cache page-num]))
;; ```

;; ### HiDPI Rendering

;; Detect and handle high-resolution displays:

;; ```clojure
;; (let [output-scale (or (.-devicePixelRatio js/window) 1)
;;       width (* (.-width viewport) output-scale)
;;       height (* (.-height viewport) output-scale)]
;;   (set! (.-width canvas-elem) width)
;;   (set! (.-height canvas-elem) height)
;;   (when (not= output-scale 1)
;;     (.setTransform context output-scale 0 0 output-scale 0 0)))
;; ```

;; ### Lazy Page Loading

;; Only render current page, not entire document:

;; ```clojure
;; (defn load-and-render-page
;;   [& {:keys [pdf-doc page-num canvas-id]}]
;;   (-> (get-pdf-page pdf-doc page-num)  ; Load only requested page
;;       (.then (fn [page]
;;                (render-pdf-page :page page
;;                                 :canvas-elem canvas
;;                                 :scale scale
;;                                 :rotation rotation)))))
;; ```

;; ## Best Practices

;; ### Keyword Arguments

;; Use keyword arguments for clarity:

;; ```clojure
;; ;; Bad: positional arguments
;; (render-pdf-page page canvas 1.5 0)
;;
;; ;; Good: keyword arguments
;; (render-pdf-page :page page
;;                  :canvas-elem canvas
;;                  :scale 1.5
;;                  :rotation 0)
;; ```

;; ### Promise Handling

;; Use native JS Promises for async operations:

;; ```clojure
;; (js/Promise.
;;  (fn [resolve reject]
;;    (try
;;      ;; Async work...
;;      (resolve result)
;;      (catch js/Error e
;;        (reject e)))))
;; ```

;; ### Error Handling

;; Graceful error handling with user feedback:

;; ```clojure
;; (defn handle-pdf-load [pdf-doc]
;;   (set-pdf! pdf-doc)
;;   (-> (load-and-render-page :pdf-doc pdf-doc
;;                             :page-num 1
;;                             :canvas-id "pdf-canvas")
;;       (.catch (fn [err]
;;                 (set-error! (str "Failed to render: " (.-message err)))))))
;; ```

;; ## Try It Live!

;; The complete PDF viewer is embedded below. Upload a PDF or try the sample documents!

;; **Features to Try:**
;;
;; - Upload your own PDF or load a sample
;; - Navigate with previous/next or jump to a specific page
;; - Zoom in/out or use fit-to-width/page modes
;; - Rotate pages in 90-degree increments
;; - Search for text across all pages
;; - Switch themes for comfortable reading
;; - Try keyboard shortcuts (Ctrl+F for search)

^:kindly/hide-code
(kind/hiccup
 [:div {:style {:margin "2rem 0"
                :padding "2rem"
                :border "2px solid #e9ecef"
                :border-radius "8px"
                :background-color "#f8f9fa"}}
  ;; PDF.js CDN scripts
  [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js"}]
  [:script "pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';"]
  [:div#pdf-viewer-root {:style {:min-height "800px"}}]
  [:script {:type "application/x-scittle"
            :src "pdf_viewer.cljs"}]])

;; ## Extending the PDF Viewer

;; Here are ideas to enhance this PDF viewer:

;; ### 1. Annotation Tools

;; **Highlighting:**
;;
;; - Select text and add highlights
;; - Different highlight colors
;; - Export/import highlights

;; **Comments:**
;;
;; - Add notes to specific pages
;; - Comment threads
;; - Export annotations

;; ### 2. Advanced Navigation

;; **Table of Contents:**
;;
;; - Extract PDF outline/bookmarks
;; - Clickable TOC navigation
;; - Show current section

;; **Thumbnails:**
;;
;; - Generate page thumbnails
;; - Thumbnail grid view
;; - Quick navigation

;; ### 3. Document Management

;; **Multiple PDFs:**
;;
;; - Tab interface for multiple documents
;; - Compare PDFs side-by-side
;; - Merge PDF pages

;; **Document Info:**
;;
;; - Extract and display metadata
;; - Show file size and page count
;; - PDF version information

;; ### 4. Enhanced Search

;; **Regular Expressions:**
;;
;; - Support regex patterns
;; - Wildcard matching
;; - Advanced query syntax

;; **Search Options:**
;;
;; - Whole word matching
;; - Search in selection
;; - Search scope (current page vs all)

;; ### 5. Export Features

;; **Page Export:**
;;
;; ```clojure
;; (defn export-page-as-image [page]
;;   (-> (render-pdf-page :page page
;;                        :canvas-elem canvas
;;                        :scale 2.0)
;;       (.then (fn [_]
;;                (let [data-url (.toDataURL canvas "image/png")
;;                      link (.createElement js/document "a")]
;;                  (set! (.-href link) data-url)
;;                  (set! (.-download link) "page.png")
;;                  (.click link))))))
;; ```

;; **Print Functionality:**
;;
;; - Print current page
;; - Print range of pages
;; - Print with annotations

;; ### 6. Accessibility

;; **Screen Reader Support:**
;;
;; - Announce page changes
;; - Describe zoom level
;; - Read search results

;; **Keyboard Navigation:**
;;
;; - Arrow keys for pages
;; - Home/End for first/last page
;; - Tab through controls

;; ## Technical Highlights

;; ### Promise-Based Async

;; Native JavaScript Promises for async operations:

;; ```clojure
;; (defn load-pdf-document [& {:keys [source]}]
;;   (js/Promise.
;;    (fn [resolve reject]
;;      (if (string? source)
;;        (-> (load-from-url source)
;;            (.then resolve reject))
;;        (-> (load-from-file source)
;;            (.then resolve reject))))))
;; ```

;; ### Functional State Updates

;; Pure functions for state transformations:

;; ```clojure
;; ;; Pure function
;; (defn next-page! []
;;   (swap! pdf-state update :current-page
;;          #(min (inc %) (:total-pages @pdf-state))))
;;
;; ;; Use with swap!
;; (next-page!)
;; ```

;; ## Resources and Links

;; - [PDF.js Documentation](https://mozilla.github.io/pdf.js/) - Official PDF.js docs
;; - [PDF.js API Reference](https://mozilla.github.io/pdf.js/api/draft/) - Complete API reference
;; - [PDF Specification](https://www.adobe.com/devnet/pdf/pdf_reference.html) - PDF format specification
;; - [Scittle Documentation](https://github.com/babashka/scittle) - Scittle reference
;; - [Reagent Guide](https://reagent-project.github.io/) - Reagent documentation
;; - [Canvas API](https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API) - HTML Canvas reference

;; ## Conclusion

;; This PDF viewer demonstrates that sophisticated document handling is possible entirely in the browser using ClojureScript and Scittle. With zero build tools and minimal dependencies, we've created:

;; - A complete PDF rendering engine
;; - Text extraction and search functionality
;; - Multiple viewing modes and themes
;; - Professional navigation controls
;; - Keyboard shortcuts for power users

;; ### From Basic to Professional

;; What started as simple PDF loading became a foundation for:
;;
;; - **Document Management**: Upload and view any PDF
;; - **Text Analysis**: Extract and search document content
;; - **User Experience**: Responsive controls and themes
;; - **Accessibility**: Keyboard navigation and readable modes

;; The browser's capabilities offer immense potential. Whether you're:
;;
;; - Building document viewers
;; - Creating annotation tools
;; - Developing form fillers
;; - Exploring document analysis

;; This PDF viewer provides a solid foundation. The functional programming approach with Reagent atoms makes complex document interactions manageable, while keyword arguments keep the code readable and maintainable.

;; ### Key Insights

;; - **PDFs are Data**: PDF.js exposes document structure programmatically
;; - **Canvas is Powerful**: HTML canvas handles high-quality rendering
;; - **Text Extraction**: PDF.js provides full text access for search
;; - **CSS Filters**: Simple filters create professional reading modes
;; - **Promise-based**: Async operations handled with native Promises

;; Now go forth and build amazing document experiences! Upload PDFs, search content, switch themes, and explore the endless possibilities of browser-based document handling! 📄🔍✨

;; ---

;; *Want to see more browser-native ClojureScript projects? Check out my other articles on [ClojureCivitas](https://clojurecivitas.github.io/) where we explore PDFs, audio, games, and interactive applications - all without build tools!*
