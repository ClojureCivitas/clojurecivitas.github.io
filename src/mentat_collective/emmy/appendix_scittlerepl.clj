^{:kindly/hide-code true
  :clay             {:title  "The Scittle Repl"
                     :quarto {:type     :post
                              :date     "2025-12-04"
                              :image    "appendix_scittlerepl.png"
                              :category :libs
                              :tags     [:scittle :repl]}}}
(ns mentat-collective.emmy.appendix-scittlerepl
  (:require
   [sci.nrepl.browser-server :as nrepl]
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind]))

;; This Clay notebook has its Scittle nrepl built in, so you can connect your editor to its ClojureScript side.

;; ## Starting the Scittle Repl of this Clay Notebook

;; With this notebook running locally, and seeing this text in the browser window (i.e. notebook is loaded once), uncomment the following line ...

;; (reset! start-scittle-nrepl-server? true)

;; ... and reload the notebook.

;; Then open an arbitrary (not .clj but) .cljs ClojureScript file.

;; In your editor, open a repl connection. For example in Emacs/Cider, this means the following 5 steps: (I) *(sesman-start)* (II) choose cider-connect-cljs (III) select localhost (IV) port 1339, followed by (V) the REPL type nbb.

;; Remark: in my setup, to make this work, there must not be another Clojure Repl connection. Because several connections seem to confuse each other, instead of using a Clojure/JVM-Repl, to reload the notebook I start Clay with the file-watcher. With the file-watcher, I make sure that I save any .cljs file before changing this very .clj file of this notebook. An unsaved .cljs file keeps this .clj file from reloading on change. If you keep things tidy, then ...

;; Voila, you have a nice Scittle Repl.

^:kindly/hide-code
(kind/hiccup
  [:img {:src "GClef.svg"}])

;; ## The inner workings of Scittle and sci.nrepl

;; The following code is just to reveal the inner workings, you should already be happily repling in your .cljs file.

;; While this Scittle Repl works perfect with the standard Scittle distribution, scittle-kitchen poses problems with my setup.

(kind/hiccup
  [:div
   [:script "var SCITTLE_NREPL_WEBSOCKET_PORT = 1340"]
   ;;[:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.js"}]
   ;;[:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.nrepl.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.nrepl.js"}]
   ])

(defonce start-scittle-nrepl-server? (atom false))

(defonce active-scittle-nrepl-server? (atom false))

(when (and @start-scittle-nrepl-server?
           (not @active-scittle-nrepl-server?))
  (reset! active-scittle-nrepl-server? true)
  (nrepl/start! {:nrepl-port 1339 :websocket-port 1340})
  :end_when)

;; ## Also with Babashka

;; It is possible to start the repl server entirely without Java.

;; Make sure that, in your local directory, there exists the file [browser_server.clj](https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/mentat_collective/emmy/browser_server.clj)

;; Then in the terminal, start babashka in the following way

^:kindly/hide-code
(kind/code
  "bb -cp . -e \"(require '[browser-server :as nrepl]) (nrepl/start! {:nrepl-port 1339 :websocket-port 1340}) (deref (promise))\"")

;; For completeness, also the Clojure CLI version

^:kindly/hide-code
(kind/code
  "clj -Sdeps \"{:deps {io.github.babashka/sci.nrepl {:mvn/version \\\"0.0.2\\\"}}}\" -M -e \"(require '[sci.nrepl.browser-server :as nrepl]) (nrepl/start! {:nrepl-port 1339 :websocket-port 1340})\"")
