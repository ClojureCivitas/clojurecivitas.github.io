#!/usr/bin/env bb
;; babqua-lifecycle.bb — manage the persistent bb nREPL process used by
;; Babqua's preview mode.
;;
;; Usage:
;;   bb babqua-lifecycle.bb start    — start nREPL if not running, print port
;;   bb babqua-lifecycle.bb stop     — stop nREPL, clean up files
;;   bb babqua-lifecycle.bb status   — print port if running, exit 1 if not
;;
;; State files live at the project root (resolved by walking up from cwd
;; looking for an existing .babqua-pid, or BABQUA_PROJECT_ROOT, else cwd):
;;   .babqua-pid           — PID of the spawned `bb nrepl-server`
;;   .babqua-nrepl-port    — port the nREPL is listening on
;;   .babqua-bb.log        — combined stdout+stderr of the spawned process
;;
;; Defensive choices: atomic file writes, PID validation before
;; kill, cleanup-on-death monitor, project-root sense check.

(ns babqua.lifecycle
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

;; ----- project root resolution -----------------------------------------

(defn- resolve-project-root []
  (or (System/getenv "BABQUA_PROJECT_ROOT")
      (loop [d (fs/cwd)]
        (cond
          (nil? d) (str (fs/cwd))
          (fs/exists? (fs/path d ".babqua-pid")) (str d)
          (= (str d) "/") (str (fs/cwd))
          :else (recur (fs/parent d))))))

(def project-root (delay (resolve-project-root)))

(defn- guard-root []
  (when (or (= @project-root "/") (str/blank? @project-root))
    (binding [*out* *err*]
      (println (str "[babqua-lifecycle] ERROR: refusing to operate with "
                    "project-root=" (pr-str @project-root))))
    (System/exit 1)))

(defn- pid-file []  (str (fs/path @project-root ".babqua-pid")))
(defn- port-file [] (str (fs/path @project-root ".babqua-nrepl-port")))
(defn- log-file []  (str (fs/path @project-root ".babqua-bb.log")))

;; ----- atomic IO -------------------------------------------------------

(defn- atomic-write! [path content]
  (let [tmp (str path ".tmp." (System/currentTimeMillis) "." (rand-int 1000000))]
    (spit tmp content)
    (fs/move tmp path {:replace-existing true})))

(defn- read-pid []
  (when (fs/exists? (pid-file))
    (try (Long/parseLong (str/trim (slurp (pid-file))))
         (catch Exception _
           (binding [*out* *err*]
             (println "[babqua-lifecycle] WARN: corrupt PID file, removing"))
           (fs/delete-if-exists (pid-file))
           (fs/delete-if-exists (port-file))
           nil))))

;; ----- process probing -------------------------------------------------

(defn- pid-alive? [pid]
  (try (zero? (:exit @(p/process ["kill" "-0" (str pid)]
                                 {:out :discard :err :discard})))
       (catch Exception _ false)))

;; A "babqua-managed" process is one whose argv contains "nrepl-server".
;; We don't try to fingerprint further — pidfile + project-root anchoring
;; already prevents most cross-talk. Defends only against the recycled-PID
;; race where the OS assigned our old PID to an unrelated user process.
(defn- babqua-process? [pid]
  (try (let [{:keys [exit out]}
             @(p/process ["ps" "-o" "args=" "-p" (str pid)]
                         {:out :string :err :discard})]
         (and (zero? exit)
              (str/includes? (or out "") "nrepl-server")))
       (catch Exception _ false)))

;; ----- start -----------------------------------------------------------

(defn- already-running? []
  (when-let [pid (read-pid)]
    (and (pid-alive? pid) (babqua-process? pid))))

(defn- write-loud! [lines]
  (binding [*out* *err*]
    (let [sep (apply str (repeat 64 "="))]
      (println sep)
      (doseq [l lines] (println (str "[babqua-lifecycle] " l)))
      (println sep))))

(defn cmd-start []
  (guard-root)
  (when (already-running?)
    (let [port (when (fs/exists? (port-file))
                 (str/trim (slurp (port-file))))]
      (binding [*out* *err*]
        (println (str "[babqua-lifecycle] Already running on port "
                      (or port "?") " (PID " (read-pid) ")")))
      (when port (println port))
      (System/exit (if port 0 1))))

  ;; Stale files from a dead session?
  (when (read-pid)
    (fs/delete-if-exists (pid-file))
    (fs/delete-if-exists (port-file)))

  (spit (log-file) "")
  (binding [*out* *err*]
    (println "[babqua-lifecycle] Starting bb nREPL..."))

  (let [proc (p/process {:cmd ["bb" "nrepl-server" "localhost:0"]
                         :out (java.io.File. (log-file))
                         :err :out
                         :dir @project-root})
        pid (.pid (:proc proc))]
    (atomic-write! (pid-file) (str pid))

    ;; Poll log file for the "Started nREPL server at host:port" line.
    ;; Generous deadline because cold bb under load can take a moment.
    (let [deadline (+ (System/currentTimeMillis) 30000)
          port (loop []
                 (cond
                   (not (pid-alive? pid))
                   (do (write-loud! ["bb died during startup. Check"
                                     (log-file)])
                       (fs/delete-if-exists (pid-file))
                       nil)

                   (> (System/currentTimeMillis) deadline)
                   (do (write-loud! ["Timed out (30s) waiting for nREPL."
                                     (str "Check " (log-file))])
                       (try (.destroy (:proc proc)) (catch Exception _))
                       (fs/delete-if-exists (pid-file))
                       nil)

                   :else
                   (let [content (try (slurp (log-file))
                                      (catch Exception _ ""))]
                     (if-let [m (re-find #"Started nREPL server at [^:]+:(\d+)"
                                         content)]
                       (second m)
                       (do (Thread/sleep 100) (recur))))))]
      (when-not port (System/exit 1))
      (atomic-write! (port-file) port)
      (write-loud!
       [(str "bb nREPL on port " port " (PID " pid ")")
        (str "Stop with: bb " (or (System/getenv "BABQUA_LIFECYCLE_PATH")
                                  "_extensions/bb/babqua-lifecycle.bb")
             " stop")])
      (println port))))

;; ----- stop ------------------------------------------------------------

(defn- escalate-after-sigterm [pid]
  (loop [i 0]
    (cond
      (not (pid-alive? pid)) :gone
      (>= i 10)
      (try @(p/process ["kill" "-9" (str pid)] {:out :discard :err :discard})
           (catch Exception _ nil))
      :else (do (Thread/sleep 100) (recur (inc i))))))

(defn cmd-stop []
  (guard-root)
  (let [pid (read-pid)]
    (cond
      (nil? pid)
      (binding [*out* *err*]
        (println (str "[babqua-lifecycle] No PID file at " (pid-file)
                      " — nothing to stop."))
        (fs/delete-if-exists (port-file)))

      (not (pid-alive? pid))
      (do (binding [*out* *err*]
            (println "[babqua-lifecycle] PID file references dead process; cleaning up."))
          (fs/delete-if-exists (pid-file))
          (fs/delete-if-exists (port-file)))

      (not (babqua-process? pid))
      (binding [*out* *err*]
        (println (str "[babqua-lifecycle] WARN: PID " pid
                      " is not an nrepl-server; refusing to kill."))
        (fs/delete-if-exists (pid-file))
        (fs/delete-if-exists (port-file)))

      :else
      (do (binding [*out* *err*]
            (println (str "[babqua-lifecycle] Stopping bb nREPL (PID " pid ")")))
          (try @(p/process ["kill" (str pid)] {:out :discard :err :discard})
               (catch Exception _ nil))
          (escalate-after-sigterm pid)
          (fs/delete-if-exists (pid-file))
          (fs/delete-if-exists (port-file))
          (binding [*out* *err*]
            (println "[babqua-lifecycle] Stopped."))))))

;; ----- status ----------------------------------------------------------

(defn cmd-status []
  (guard-root)
  (let [pid (read-pid)]
    (cond
      (and pid (pid-alive? pid) (babqua-process? pid)
           (fs/exists? (port-file)))
      (do (println (str "running on port " (str/trim (slurp (port-file)))
                        " (PID " pid ")"))
          (System/exit 0))

      :else
      (do (println "not running")
          (System/exit 1)))))

;; ----- dispatch --------------------------------------------------------

(case (first *command-line-args*)
  "start"  (cmd-start)
  "stop"   (cmd-stop)
  "status" (cmd-status)
  (do (binding [*out* *err*]
        (println "Usage: babqua-lifecycle.bb {start|stop|status}"))
      (System/exit 2)))
