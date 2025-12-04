^{:kindly/hide-code true
  :clay             {:title  "The Scittle Repl"
                     :quarto {:type     :post
                              :date     "2025-12-04"
                              :image    "GClef.svg"
                              :category :libs
                              :tags     [:scittle :repl]}}}
(ns mentat-collective.emmy.appendix-scittlerepl
  (:require
   [sci.nrepl.browser-server :as nrepl]
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind]))

(comment 1)

;; ## Starting a ClojureScript Repl in Clay

;; With this notebook running locally, uncomment the following line ...

;; (reset! start-scittle-nrepl-server? true)

;; ... and reload the notebook.

;; Then open an arbitrary (not .clj but) .cljs ClojureScript file.

;; In your editor, open a repl connection. For example in Emacs/Cider, this means the following 5 steps: (I) *(sesman-start)* (II) choose cider-connect-cljs (III) select localhost (IV) port 1339, followed by (V) the REPL type nbb.

;; Remark: in my setup, all this does not work when I connect my editor to a normal Clojure Repl as well. Instead of using a Clojure-Repl, to reload the notebook I start Clay with the file-watcher. And then ...

;; Voila, you have a nice ClojureScript Repl.

^:kindly/hide-code
(kind/hiccup
  [:img {:src "GClef.svg"}])

;; ## The inner workings of Scittle and sci.nrepl

;; The following code is just to reveal the inner workings, you should already be happily repling in your .cljs file.

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

(when (and start-scittle-nrepl-server?
           (not active-scittle-nrepl-server?))
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
