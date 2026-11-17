(ns scicloj.clay.ideas.uije3
  (:require [replicant.dom :as r]))

(defn branch? [x]
  (and (vector? x)
       (keyword? (first x))))

(defmulti enhance-tag (fn [tag attrs state] tag))
(defmethod enhance-tag :default [tag attrs state]
  (if attrs
    [tag attrs]
    [tag]))

(defn state-from-ui [state node]
  (cond
    (branch? node)
    (let [[_ & [x & more :as xs]] node
          attrs (when (map? x) x)
          children (if attrs more xs)
          patch (:state attrs)]
      (when (contains? attrs :state)
        (when-not (map? patch)
          (throw (ex-info (str "state must be a map, got " (type patch))
                          {:id ::state-must-be-map
                           :state patch}))))
      (reduce state-from-ui (merge state patch) children))

    (seq? node)
    (reduce state-from-ui state node)

    :else state))

(defn next-state [state ui]
  (dissoc (state-from-ui state ui) :click))

(defn enhance-hiccup [node state]
  (cond
    (branch? node)
    (let [[tag & [x & more :as xs]] node
          attrs (when (map? x) x)
          children (if attrs more xs)]
      (-> (enhance-tag tag (some-> attrs (dissoc :state)) state)
          (into (map #(enhance-hiccup % state) children))))

    (seq? node)
    (map #(enhance-hiccup % state) node)

    :else node))

(defmethod enhance-tag :input [_ {:as attrs :keys [name label]} state]
  (let [input-attrs (cond-> {}
                      name (assoc :on {:input [:input name]}
                                  :value (get state name))
                      true (merge (dissoc attrs :label)))]
    (cond->> [:input.form-control input-attrs]
      (and name label) (conj [:div.mb-3
                              [:label.form-label {:for name} label]]))))

(defmethod enhance-tag :select [_ {:as attrs :keys [name options label]} state]
  (let [select-attrs (cond-> {}
                       name (assoc :on {:change [:change name]}
                                   :value (get state name))
                       true (merge (dissoc attrs :options :label)))]
    (cond->> [:select.form-select select-attrs
              (for [c options]
                (if (vector? c)
                  [:option {:value (first c)} (second c)]
                  [:option {:value c} c]))]
      (and name label) (conj [:div.mb-3
                              [:label.form-label {:for name} label]]))))

(defmethod enhance-tag :button [_ {:as attrs :keys [name]} state]
  [:button.btn.btn-default (cond-> {}
                             name (assoc :on {:click [:click name]})
                             true (merge (dissoc attrs :name)))])

(defmethod enhance-tag :row [_ {:as attrs} state]
  [:div (merge {:style {:display :flex}} (dissoc attrs :name))])

(defmethod enhance-tag :col [_ {:as attrs} state]
  [:div (merge {:style {:display :flex :flex-direction :column}} (dissoc attrs :name))])

(defn create-div [id]
  (doto (.createElement js/document "div")
    (-> (.-id) (set! id))
    (->> (.appendChild (.-body js/document)))))

(defn extract-event-data [ctx [event k]]
  (let [target (.-target (:replicant/dom-event ctx))
        value (.-value target)]
    (case event
      :input {k (if (and (#{"range" "number"} (.-type target))
                         (not (js/Number.isNaN (.-valueAsNumber target))))
                  (.-valueAsNumber target)
                  value)}
      :change {k value}
      {event k})))

(defn dispatch!
  "Replicant has one gobal dispatch,
   so we need to find the relevant uije dispatch."
  [ctx ev]
  (loop [node (.. (:replicant/dom-event ctx) -target)]
    (cond
      (nil? node) (throw (ex-info "No UIJE root" {:id ::no-uije-root
                                                  :ev ev}))
      (.-uijeDispatch node) ((.-uijeDispatch node) ctx ev)
      :else (recur (.-parentNode node)))))

(defn uije [ui-var]
  (let [id (or (some-> ui-var meta :name str) "uije-app")
        root (or (js/document.getElementById id)
                 (create-div id))
        *state (atom {})
        render! (fn [event]
                  (let [ui (ui-var (merge @*state event))
                        state (next-state @*state ui)]
                    (reset! *state state)
                    (r/render root (enhance-hiccup ui state))))
        handle-event! (fn [ctx dispatch-args]
                        (let [ev (extract-event-data ctx dispatch-args)]
                          (when-not (contains? ev :click)
                            (swap! *state merge ev))
                          (render! ev)))]
    (set! (.-uijeDispatch root) handle-event!)
    (r/set-dispatch! dispatch!)
    (render! {})))
