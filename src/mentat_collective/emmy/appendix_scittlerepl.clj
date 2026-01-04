^{:kindly/hide-code true
  :clay             {:title  "The Scittle Repl"
                     :quarto {:author   :kloimhardt
                              :type     :post
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

;; To start the nrepl server, in the local .clj file of this very notebook, uncomment the following line ...

;;```clojure
;; (nrepl/start! {:nrepl-port 1339 :websocket-port 1340})
;;```

;; ... and (re)load this notebook within your local Clay instance.

;; Then open an arbitrary (not .clj but) .cljs ClojureScript file.

;; In your local editor, open a Repl connection. For example in Emacs/Cider, this means the following 5 steps: (I) *(sesman-start)* (II) choose cider-connect-cljs (III) select localhost (IV) port 1339, followed by (V) the REPL type nbb.

;; Voila, you have a nice Scittle Repl.

^:kindly/hide-code
(kind/hiccup
  [:img {:src "GClef.svg"}])


;; ## The Scittle State

;; The executive summary is that I can only want you to open Clay in file-watcher mode and observe the following

^:kindly/hide-code
(kind/table
  {"Action"              ["Reload browser" "Save this very notebook .clj file" "Save any .cljs file"]
   "Clean Scittle state" ["yes" "yes" "no"]})


;; The Scittle Repl looses its state when the browser window is reloaded. This also means that the Scittle Repl looses its state when this very notebook .clj file is reloaded. As opposed to that, the Clojure/JVM state persists in any case. In fact, this very notebook, in using defonce, relies on a persistent Clojure/JVM state. But the possibility to produce a clean Scittle slate via browser reload can be of great advantage especially for the (domain-expert but) Repl-novice.

;; This notebook works best when Clay is started in file-watcher mode via `clojure -M:clay`. It is not a good idea to open, next to the Scittle Repl, also a Clojure/JVM Repl connection. At least in my setup, opening more than one Repl connection leads to unstable behaviour. Act at your own discretion.

;; With Clay's file watcher active, not only is this very notebook .clj file reloaded on change, but indeed also any Scittle .cljs file that is changed and saved is automatically reloaded. But as opposed to slate-cleaning browser-reload, saving a .cljs file keeps the Scittle state.

;; In sum, while saving a .cljs file keeps the Scittle state, saving this very notebook .clj file cleans the Scittle state. But as I can only want you to happily repl in .cljs files exclusively, further changing and loading of this very notebook is not necessary. Of course, you are free to change and save this very notebook .clj file as often as you like, but make sure all your .cljs files are saved before you do so. Any unsaved Scittle .cljs file keeps this very notebook from reloading on change.

;; ## The inner workings of Scittle and sci.nrepl

;; The following code is just to reveal the inner workings, you should already be able to happily repl in your .cljs file.

;; While this Scittle Repl works perfect with the standard Scittle distribution, scittle-kitchen poses problems with my setup.

(kind/hiccup
  [:div
   [:script "var SCITTLE_NREPL_WEBSOCKET_PORT = 1340"]
   ;;[:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.js"}]
   ;;[:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.nrepl.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.nrepl.js"}]
   ])

;; ## Also with Babashka

;; It is possible to start the nrepl server entirely without Java.

;; Make sure that, in your local directory, there exists the file [browser_server.clj](https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/mentat_collective/emmy/browser_server.clj)

;; Then in the terminal, start babashka in the following way

^:kindly/hide-code
(kind/code
  "bb -cp . -e \"(require '[browser-server :as nrepl]) (nrepl/start! {:nrepl-port 1339 :websocket-port 1340}) (deref (promise))\"")

;; For completeness, I also provide the Clojure CLI version

^:kindly/hide-code
(kind/code
  "clj -Sdeps \"{:deps {io.github.babashka/sci.nrepl {:mvn/version \\\"0.0.2\\\"}}}\" -M -e \"(require '[sci.nrepl.browser-server :as nrepl]) (nrepl/start! {:nrepl-port 1339 :websocket-port 1340})\"")
