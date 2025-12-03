(ns browser-server
;;originali name: sci.nrepl.browser-server
;;original file https://github.com/babashka/sci.nrepl/blob/main/src/sci/nrepl/browser_server.clj

  (:require
   [bencode.core :as bencode]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [org.httpkit.server :as httpkit])
  (:import
   [java.io PushbackInputStream EOFException BufferedOutputStream]
   [java.net ServerSocket]))

(set! *warn-on-reflection* true)

(defn- update-when [m k f]
  (if-let [v (get m k)]
    (assoc m k (f v))
    m))

(defn- coerce-bencode [x]
  (if (bytes? x)
    (String. ^bytes x)
    x))

(defn- read-bencode [in]
  (try (let [msg (bencode/read-bencode in)
             msg (zipmap (map keyword (keys msg))
                         (map coerce-bencode (vals msg)))]
         msg)
       (catch Exception e
         #_(def e e)
         (throw e))))

(defonce ^:private !last-ctx
  (volatile! nil))

(defn send-response [{:keys [out id session response]
                      :or {out (:out @!last-ctx)}}]
  (let [response (cond-> response
                   id (assoc :id id)
                   session (assoc :session session))]
    (bencode/write-bencode out response)
    (.flush ^java.io.OutputStream out)))

(defn- handle-clone [ctx]
  (let [id (str (java.util.UUID/randomUUID))]
    (send-response (assoc ctx
                          :response {"new-session" id "status" ["done"]}))))

(defonce nrepl-channel (atom nil))

(defn- response-handler [message]
  (let [{:as msg :keys [id session]} (edn/read-string message)]
    (send-response {:id id
                    :session session
                    :response (dissoc msg :id :session)})))

(defn- websocket-send! [msg]
  (when-let [chan @nrepl-channel]
    (httpkit/send! chan (str msg))))

(defn- handle-eval [{:as ctx :keys [msg session id send-fn] :or {send-fn websocket-send!}}]
  (vreset! !last-ctx ctx)
  (let [code (get msg :code)]
    (if (or (str/includes? code "clojure.main/repl-requires")
            (str/includes? code "System/getProperty"))
      (do
        (send-response (assoc ctx :response {"value" "nil"}))
        (send-response (assoc ctx :response {"status" ["done"]})))
      (send-fn {:op :eval
                :code code
                :id id
                :session session}))))

(defn- handle-load-file [ctx]
  (let [msg (get ctx :msg)
        code (get msg :file)
        msg (assoc msg :code code)]
    (handle-eval (assoc ctx :msg msg))))

(defn- handle-complete [{:keys [id session msg send-fn] :or {send-fn websocket-send!}}]
  (send-fn {:op :complete
            :id id
            :session session
            :symbol (get msg :symbol)
            :prefix (get msg :prefix)
            :ns (get msg :ns)}))

(defn- generically-handle-on-server [{:keys [id op session msg send-fn] :or {send-fn websocket-send!}}]
  (send-fn (merge msg
                  {:op op
                   :id id
                   :session session})))

(defn- handle-describe [ctx]
  (vreset! !last-ctx ctx)
  (generically-handle-on-server (assoc ctx :op :describe)))

(defn- session-loop [in out {:keys [opts]}]
  (loop []
    (when-let [msg (try
                     (let [msg (read-bencode in)]
                       msg)
                     (catch EOFException _
                       (when-not (:quiet opts)
                         (println "Client closed connection."))))]
      (let [ctx (cond-> {:out out :msg msg}
                  (:send-fn opts)
                  (assoc :send-fn (:send-fn opts)))
            id (get msg :id)
            session (get msg :session)
            ctx (assoc ctx :id id :session session)
            op (keyword (get msg :op))]
        (case op
          :clone (handle-clone ctx)
          :eval (handle-eval ctx)
          :describe (handle-describe ctx)
          :load-file (handle-load-file ctx)
          :complete (handle-complete ctx)
          (generically-handle-on-server (assoc ctx :op op))))
      (recur))))

(defn- listen [^ServerSocket listener {:as opts}]
  (println (str "nREPL server started on port " (:port opts) "..."))
  (let [client-socket (.accept listener)
        in (.getInputStream client-socket)
        in (PushbackInputStream. in)
        out (.getOutputStream client-socket)
        out (BufferedOutputStream. out)]
    (future
      (session-loop in out {:opts opts}))
    (recur listener opts)))

(defonce !socket (atom nil))

(defn start-nrepl-server! [{:keys [port] :as opts}]
  (let [port (or port 1339)
        inet-address (java.net.InetAddress/getByName "localhost")
        socket (new ServerSocket port 0 inet-address)
        _ (reset! !socket socket)]
    (future (listen socket opts))))

(defn stop-nrepl-server! []
  (when-let [socket @!socket]
    (.close ^ServerSocket socket)
    (reset! !socket nil)))

(defn- create-channel [req]
  (httpkit/as-channel req
                      {:on-open (fn [ch]
                                  (reset! nrepl-channel ch))
                       :on-close (fn [_ch _reason] (prn :close))
                       :on-receive
                       (fn [_ch message]
                         (prn :msg message)
                         (response-handler message))}))

(defn- app [{:as req}]
  (when (:websocket? req)
    (case (:uri req)
      "/_nrepl"
      (create-channel req))))

(defonce ^:private !server
  (atom nil))

(defn halt! []
  (when-let [{:keys [port stop-fn]} @!server]
    (stop-fn)
    (println (str "Webserver running on " port ", stopped."))
    (reset! !server nil)))

(defn start-websocket-server! [{:keys [port]}]
  (let [port (or port 1340)]
    (halt!)
    (try
      (reset! !server {:port port :stop-fn (httpkit/run-server #'app {:port port})})
      (println (str "Websocket server started on " port "..."))
      (catch Exception #_java.net.BindException e ;; TODO, add BindException to bb, done for 0.8.3
             (println "Port " port " not available, server not started!")
             (println (.getMessage e))))))

(defn start!
  [{:keys [nrepl-port websocket-port]
    :or {nrepl-port 1339
         websocket-port 1340}}]
  (start-nrepl-server! {:port nrepl-port})
  (start-websocket-server! {:port websocket-port}))
