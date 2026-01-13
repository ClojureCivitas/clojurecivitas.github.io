^{:kindly/hide-code true
  :clay             {:title  "The Scittle Repl"
                     :quarto {:author   :kloimhardt
                              :type     :post
                              :description "This Clay notebook has its Scittle nRepl built in, so you can connect your editor to its ClojureScript side."
                              :date     "2025-12-04"
                              :image    "appendix_scittlerepl.png"
                              :category :libs
                              :tags     [:scittle :repl]}}}
(ns mentat-collective.emmy.appendix-scittlerepl
  (:require
   [nrepl.core :as repl]
   [sci.nrepl.browser-server :as nrepl]
   [scicloj.kindly.v4.api :as kindly]
   [scicloj.kindly.v4.kind :as kind]))

;; This HTML page, as any Civitas notebook, is hosted by [Clay](https://github.com/scicloj/clay). The notebook is meant to be changed in a local Civitas instance. This is because it is all about connecting your local editor to the browser, i.e. the ClojureScript side of this notebook.It is about sending code from your editor to an in browser process.

;; ## Starting the Scittle Repl of this Clay Notebook

;; To start the nrepl server, in the local .clj file of this very notebook, uncomment the following line ...

;;```clojure
;; (nrepl/start! {:nrepl-port 1339 :websocket-port 1340})
;;```

;; ... and (re)load this notebook within your local Clay instance. Then again, comment the line above and reload the notebook (again).

;; Below you should see a hash-map that contains the entry `:value "3"`

(defn connect-and-eval
  [host port code]
  (with-open [conn (repl/connect :host host :port port)] ; Establishes the connection
    (-> (repl/client conn 1000) ; Creates a client with a message timeout
        (repl/message {:op "eval" :code code}) ; Sends an "eval" message
        (doall)
        (clojure.pprint/pprint)
        #_(repl/response-values))))

(if (not @@#'nrepl/!socket)
  "not connected"
  (connect-and-eval "localhost" 1339 "(+ 1 2)"))


;; Next, open an arbitrary (not .clj but) .cljs ClojureScript file.

;; In your local editor, open a Repl connection. For example in Emacs/Cider, this means the following 5 steps: (I) *(sesman-start)* (II) choose cider-connect-cljs (III) select localhost (IV) port 1339, followed by (V) the REPL type nbb.

;; Voila, you have a nice Scittle Repl.

^:kindly/hide-code
(kind/hiccup
  [:img {:src "GClef.svg"}])

;; ## Some Explanations

;; - Scittle is the ClojureScript runtime executing in the browser
;; - `browser-server` is a "broker" process that receives instructions from your editor (send form, send file), sends it to Scittle, and returns the result.
;; - The reason browser-server exists is because editors have nRepl integrations, but nothing to talk to Scittle directly.
;; - Scittle is listening on a websocket which is different from an nRepl socket. Nontheless, it performs the same function.

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

;; ## With Clay, you can talk to Scittle without nRepl, but it won't answer (yet)
;; Clay already has a websocket connection from the Clojure process to the HTML page (and therefore Scittle). This is how Clay loads .cljs files when in live-reload mode. It sends the code over the websocket and calls Scittle. `scicloj.clay.v2.server/scittle-eval-string!` is called when a cljs file is changed, and `scicloj.clay.v2.server/communication-script` contains the JavaScript that does `window.scittle.core.eval_string(code);`
;; So there is already a direct bridge from the Clojure Clay process to the Scittle process. However the current implementation does not send a result back to Clay.

;; ## The inner workings of Scittle and sci.nrepl

;; The following code is just to reveal the inner workings, you should already be able to happily repl in your .cljs file.

(kind/hiccup
  [:div
   [:script "var SCITTLE_NREPL_WEBSOCKET_PORT = 1340"]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.28-59/dist/scittle.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen@0.7.28-59/dist/scittle.nrepl.js"}]
   ;;[:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.js"}]
   ;;[:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.nrepl.js"}]
   ])

;; ## Connect to Scittle and the browser without Java

;; The main advantage of this nRepl method is that it is possible to start the nRepl server entirely without Java (and thus entirely without Clay).

;; Make sure that, in your local directory, there exists the file [browser_server.clj](https://github.com/ClojureCivitas/clojurecivitas.github.io/blob/main/src/mentat_collective/emmy/browser_server.clj)

;; Then in the terminal, start [Babashka](https://babashka.org) in the following way

^:kindly/hide-code
(kind/code
  "bb -cp . -e \"(require '[browser-server :as nrepl]) (nrepl/start! {:nrepl-port 1339 :websocket-port 1340}) (deref (promise))\"")

;; For completeness, I also provide the Clojure CLI version

^:kindly/hide-code
(kind/code
  "clj -Sdeps \"{:deps {io.github.babashka/sci.nrepl {:mvn/version \\\"0.0.2\\\"}}}\" -M -e \"(require '[sci.nrepl.browser-server :as nrepl]) (nrepl/start! {:nrepl-port 1339 :websocket-port 1340})\"")
