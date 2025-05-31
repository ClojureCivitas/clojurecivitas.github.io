(ns klipse.interactive-test.can-clay-klipse)

;; [Klipse](https://github.com/viebel/klipse)
;; turns code blocks into an interactive editor with eval capabilities.
;; [Clojure-doc](clojure-doc.org) is an excellent example of how this can provide an interactive experience,
;; where readers are encouraged to change the code and see the results in the browser.

;; Clay intentionally produces static HTML and Markdown,
;; but we can still use Klipse JavaScript to turn code blocks into interactive REPLs.

^:kind/hiccup
[:div
 [:link {:rel "stylesheet"
         :type "text/css"
         :href "https://storage.googleapis.com/app.klipse.tech/css/codemirror.css"}]
 [:script "window.klipse_settings = { selector: '.eval-clojure' };"]]

^{:kindly/options {:class "eval-clojure"}}
(let [f (fn [x]
          (* 2 x))]
  (map f (range 0 10)))

^:kind/hiccup
[:script {:src "https://storage.googleapis.com/app.klipse.tech/plugin/js/klipse_plugin.js"}]
