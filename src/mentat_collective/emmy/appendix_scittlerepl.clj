^{:kindly/hide-code true
  :clay             {:title  "scittle repl"
                     :quarto {:type     :draft
                              ;; :sidebar  "emmy-fdg"
                              :date     "2025-11-12"
                              :image    nil
                              :category :libs
                              :tags     [:emmy :physics]}}}
(ns mentat-collective.emmy.appendix-scittlerepl
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator
                            denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(kind/hiccup
  [:div
   [:script "var SCITTLE_NREPL_WEBSOCKET_PORT = 1340"]
   ;;[:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.js"}]
   ;;[:script {:src "https://cdn.jsdelivr.net/npm/scittle-kitchen/dist/scittle.nrepl.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.js"}]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.nrepl.js"}]
   ])

;; make sure that, in your local directory, there exists the file [browser_server](https://clojurecivitas.github.io/mentat_collective/emmy/browser_server.html)

;; Then in the terminal start babashka in the following way
^:kindly/hide-code
(kind/code
  "bb -cp . -e \"(require '[browser-server :as nrepl]) (nrepl/start! {:nrepl-port 1339 :websocket-port 1340}) (deref (promise))\"")


;; then open a (not .clj but) .cljs file in your editor

;; In Cider, choose cider-connect-cljs, select localhost, port 1339, followed by the REPL type nbb .
