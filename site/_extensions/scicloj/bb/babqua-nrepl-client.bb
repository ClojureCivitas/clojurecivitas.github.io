#!/usr/bin/env bb
;; babqua-nrepl-client.bb — bencode client used by the Lua filter when a
;; persistent bb nREPL is running. Connects to the port given on argv,
;; forwards the Clojure code on stdin as a single eval message, and
;; relays the resulting :out chunks to its own stdout (where the filter
;; reads them with the same JSON-extraction logic as one-shot mode).
;;
;; Why a separate script rather than nREPL talk in Lua: pandoc's Lua
;; doesn't ship luasocket, so direct TCP from the filter isn't portable.
;; Babqua already needs bb anyway, and bb has bencode.core built in, so
;; one bb spawn per render delegates the protocol work to a context
;; that has the libraries.
;;
;; Usage:
;;   echo "(println :hi)" | bb babqua-nrepl-client.bb 12345

(ns babqua.nrepl-client
  (:require [bencode.core :as b])
  (:import [java.io PushbackInputStream]
           [java.net Socket SocketException]))

(defn- coerce
  "bencode.core hands back byte[] for strings and recursively-nested maps
  / vectors. Convert to plain strings/maps so equality and lookup work."
  [v]
  (cond
    (bytes? v)      (String. ^bytes v "UTF-8")
    (map? v)        (into {} (map (fn [[k v]] [(coerce k) (coerce v)])) v)
    (sequential? v) (mapv coerce v)
    :else           v))

(defn- exit-fatal! [msg]
  (binding [*out* *err*] (println (str "[babqua-nrepl-client] " msg)))
  (System/exit 1))

(defn -main [& args]
  (let [port (try (Integer/parseInt (first args))
                  (catch Exception _ nil))
        _    (when-not port
               (exit-fatal! "Usage: babqua-nrepl-client.bb <port>"))
        code (slurp *in*)
        sock (try (Socket. "127.0.0.1" ^int port)
                  (catch Exception e
                    (exit-fatal! (str "Could not connect to nREPL on "
                                      port ": " (.getMessage e)))))
        out  (.getOutputStream sock)
        in   (PushbackInputStream. (.getInputStream sock))]
    (try
      (b/write-bencode out {"op" "eval" "code" code "id" "1"})
      (loop [chunks []]
        (let [resp   (try (coerce (b/read-bencode in))
                          (catch SocketException _ nil)
                          (catch java.io.EOFException _ nil))
              status (set (or (get resp "status") []))]
          (cond
            (nil? resp)
            (exit-fatal! "nREPL connection closed unexpectedly")

            (get resp "out")
            (recur (conj chunks (get resp "out")))

            (get resp "err")
            (do (binding [*out* *err*] (print (get resp "err")) (flush))
                (recur chunks))

            ;; "ex" is set when eval threw at the top level; surface and
            ;; let the filter's outer try/catch JSON path produce the
            ;; babqua/fatal envelope from any captured :out.
            (or (contains? status "done")
                (contains? status "error"))
            (do (doseq [s chunks] (print s))
                (flush))

            :else
            (recur chunks))))
      (finally
        (try (.close sock) (catch Exception _ nil))))))

(apply -main *command-line-args*)
