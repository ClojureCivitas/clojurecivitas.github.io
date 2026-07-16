(ns language-learning.vocabulary-estimation.article-controls
  (:require [clojure.string :as str]))

(def storage-keys
  {:code "civitas.vocabulary-articles.code-open"
   :equations "civitas.vocabulary-articles.equations-open"})

(defn stored-preference
  "Interpret the string returned by localStorage, retaining the fallback when
  the key is absent."
  [stored-value fallback]
  (if (nil? stored-value)
    fallback
    (= "true" stored-value)))

(defn next-disclosure-open?
  "Choose the next bulk-disclosure state from the visible states or fallback."
  [open-states fallback]
  (if (seq open-states)
    (not (every? true? open-states))
    (not fallback)))

(defn available-controls
  "Return the visibility decision for each toolbar action."
  [{:keys [explanation-count code-count equation-count]}]
  (let [help (pos? explanation-count)
        code (pos? code-count)
        equations (pos? equation-count)]
    {:toolbar (or help code equations)
     :help help
     :code code
     :equations equations}))

(defn dom-available? []
  (exists? js/document))

(defn- q [root selector]
  (when root (.querySelector root selector)))

(defn- q-all [root selector]
  (if root
    (vec (js->clj (js/Array.from (.querySelectorAll root selector))))
    []))

(defn- data-value [element key]
  (when element
    (aget (.-dataset element) key)))

(defn- read-preference [key fallback]
  (try
    (stored-preference (.getItem (.-localStorage js/window) key) fallback)
    (catch :default _error fallback)))

(defn- write-preference! [key value]
  (try
    (.setItem (.-localStorage js/window) key (str value))
    (catch :default _error
      nil)))

(def fallback-toolbar-html
  (str
   "<p><strong id=\"article-explanations-heading\">Reading controls</strong>"
   "<span class=\"article-explanations-description\" id=\"article-explanations-description\">Open or close help, code, and equations. Choices for code and equations apply across this article namespace.</span>"
   "<span class=\"article-explanations-status\" id=\"article-explanations-status\" aria-live=\"polite\">Help is hidden.</span></p>"
   "<div class=\"article-reading-action\" id=\"article-help-action\">"
   "<button class=\"article-explanations-toggle\" id=\"article-explanations-toggle\" type=\"button\" aria-pressed=\"false\" aria-describedby=\"article-explanations-description article-explanations-status\">Show all help</button></div>"
   "<div class=\"article-reading-action\" id=\"article-code-action\">"
   "<button class=\"article-code-toggle\" id=\"article-code-toggle\" type=\"button\" aria-pressed=\"false\" aria-describedby=\"article-explanations-description article-code-status\">Show all code</button>"
   "<span class=\"article-code-status\" id=\"article-code-status\" aria-live=\"polite\">Code is hidden.</span></div>"
   "<div class=\"article-reading-action\" id=\"article-equations-action\">"
   "<button class=\"article-equations-toggle\" id=\"article-equations-toggle\" type=\"button\" aria-pressed=\"true\" aria-describedby=\"article-explanations-description article-equations-status\">Hide all equations</button>"
   "<span class=\"article-equations-status\" id=\"article-equations-status\" aria-live=\"polite\">Equations are shown.</span></div>"))

(defn- ensure-toolbar! [article-main]
  (or
   (.getElementById js/document "article-explanations-toggle")
   (let [toolbar (.createElement js/document "div")
         series-navigation (q article-main ":scope > .series-toc")]
     (set! (.-className toolbar) "article-explanations-toolbar")
     (.setAttribute toolbar "role" "region")
     (.setAttribute toolbar "aria-labelledby" "article-explanations-heading")
     (set! (.-innerHTML toolbar) fallback-toolbar-html)
     (if series-navigation
       (.insertAdjacentElement series-navigation "afterend" toolbar)
       (.insertAdjacentElement article-main "afterbegin" toolbar))
     (q toolbar "#article-explanations-toggle"))))

(defn- make-help-button! [{:keys [label controls classes]
                           :or {classes []}}]
  (let [help (.createElement js/document "button")]
    (set! (.-type help) "button")
    (set! (.-className help)
          (str/join " " (into ["article-help-icon"] classes)))
    (set! (.-textContent help) "?")
    (.setAttribute help "aria-label" label)
    (.setAttribute help "title" label)
    (.setAttribute help "aria-controls" (str/join " " controls))
    (.setAttribute help "aria-expanded" "false")
    help))

(def title-icon-classes
  {:equation "article-equation-title-icon"
   :code "article-code-title-icon"
   :math-help "article-math-help-title-icon"
   :code-help "article-code-help-title-icon"
   :text-help "article-text-help-title-icon"})

(defn- decorate-title! [summary kind glyph]
  (when (and summary
             (nil? (q summary ":scope > .article-box-title-icon")))
    (let [icon (.createElement js/document "span")]
      (set! (.-className icon)
            (str "article-box-title-icon " (title-icon-classes kind)))
      (set! (.-textContent icon) glyph)
      (.setAttribute icon "aria-hidden" "true")
      (.insertBefore summary icon (.-firstChild summary)))))

(defn- ensure-layout! [anchor]
  (let [existing (.closest anchor ".article-explanation-layout")]
    (if (and existing
             (identical? anchor
                         (q existing
                            ":scope > .article-explanation-anchor")))
      existing
      (let [layout (.createElement js/document "div")
            slot (.createElement js/document "div")]
        (set! (.-className layout) "article-explanation-layout")
        (set! (.-className slot) "article-explanation-slot")
        (.add (.-classList anchor) "article-explanation-anchor")
        (.insertBefore (.-parentElement anchor) layout anchor)
        (.appendChild layout anchor)
        (.appendChild layout slot)
        layout))))

(defn- layout-slot [layout]
  (q layout ":scope > .article-explanation-slot"))

(defn- contains-display-equation? [node]
  (boolean
   (and node
        (q node "span.math.display, mjx-container[display=true]"))))

(defn- preceding-equation [registry]
  (loop [cursor registry
         depth 0]
    (when (and cursor (.-parentElement cursor) (< depth 4))
      (let [found
            (loop [candidate (.-previousElementSibling cursor)]
              (cond
                (nil? candidate) nil
                (contains-display-equation? candidate) candidate
                :else (recur (.-previousElementSibling candidate))))]
        (or found
            (when-not (.matches (.-parentElement cursor) "section")
              (recur (.-parentElement cursor) (inc depth))))))))

(def boundary-pattern #"[A-Za-z0-9‐‑‒–—-]")

(defn- boundary? [character]
  (or (str/blank? character)
      (nil? (re-find boundary-pattern character))))

(defn- excluded-text-node? [node]
  (boolean
   (and (.-parentElement node)
        (.closest
         (.-parentElement node)
         (str "script,style,pre,code,svg,details,.article-explanations-toolbar,"
              ".article-explanation-registry,.article-help-icon")))))

(defn- normalise-term [text]
  (-> text
      (.toLocaleLowerCase)
      (str/replace #"[‐‑‒–—]" "-")))

(defn- term-match-in-node [node needle]
  (let [haystack (normalise-term (or (.-nodeValue node) ""))]
    (loop [index (.indexOf haystack needle)]
      (when-not (neg? index)
        (let [after-index (+ index (count needle))
              plural (and (= "s" (.charAt haystack after-index))
                          (boundary? (.charAt haystack (inc after-index))))]
          (if (and (boundary? (.charAt haystack (dec index)))
                   (or (boundary? (.charAt haystack after-index)) plural))
            {:node node
             :index index
             :length (+ (count needle) (if plural 1 0))}
            (recur (.indexOf haystack needle (inc index)))))))))

(defn- first-term-match [root text]
  (let [walker (.createTreeWalker js/document root (.-SHOW_TEXT js/NodeFilter))
        needle (normalise-term text)]
    (loop [node (.nextNode walker)]
      (when node
        (or (when-not (excluded-text-node? node)
              (term-match-in-node node needle))
            (recur (.nextNode walker)))))))

(defn- code-symbol-match-in-node [node needle]
  (let [haystack (.toLocaleLowerCase (or (.-nodeValue node) ""))]
    (loop [index (.indexOf haystack needle)]
      (when-not (neg? index)
        (let [after-index (+ index (count needle))]
          (if (and (boundary? (.charAt haystack (dec index)))
                   (boundary? (.charAt haystack after-index)))
            {:node node :index index :length (count needle)}
            (recur (.indexOf haystack needle (inc index)))))))))

(defn- first-code-symbol-match [root text]
  (let [walker (.createTreeWalker js/document root (.-SHOW_TEXT js/NodeFilter))
        needle (.toLocaleLowerCase text)]
    (loop [node (.nextNode walker)]
      (when node
        (or (code-symbol-match-in-node node needle)
            (recur (.nextNode walker)))))))

(defn- anchor-block-for [node]
  (or (some-> (.-parentElement node)
              (.closest "p,blockquote,li,figcaption,h2,h3,h4"))
      (.-parentElement node)))

(defn- append-inline-help! [{:keys [node index length]} help]
  (let [after (.splitText node (+ index length))]
    (.insertBefore (.-parentNode after) help after)))

(defn- prepare-math-registries! [registries]
  (doseq [[registry-index registry] (map-indexed vector registries)]
    (let [items (q-all registry ":scope > details.article-explanation")
          anchor (preceding-equation registry)]
      (when (and anchor (seq items))
        (let [accent-class (str "accent-" (inc (mod registry-index 6)))
              layout (ensure-layout! anchor)
              slot (layout-slot layout)
              title (or (data-value registry "explanationTitle")
                        "this equation")]
          (.add (.-classList layout) "equation-explanation-layout")
          (doseq [item items]
            (.add (.-classList item) accent-class)
            (.appendChild slot item))
          (.add (.-classList anchor) "equation-help-anchor")
          (aset (.-dataset anchor) "equationTitle" title)
          (.appendChild
           anchor
           (make-help-button!
            {:label (str "Explain " title)
             :controls (mapv #(.-id %) items)
             :classes ["equation-help-icon" accent-class]}))
          (.remove registry))))))

(defn- prepare-term-registries! [registries article-main]
  (doseq [registry registries]
    (let [items (q-all registry ":scope > details.article-explanation")
          search-root (or (.closest registry "section") article-main)]
      (doseq [item items]
        (let [anchor-text (or (data-value item "anchorTerm")
                              (data-value item "helpLabel") "")
              match (first-term-match search-root anchor-text)
              anchor (if match
                       (anchor-block-for (:node match))
                       (.-previousElementSibling registry))]
          (when anchor
            (let [layout (ensure-layout! anchor)
                  help-label (or (data-value item "helpLabel") anchor-text)
                  category (if (.contains (.-classList item) "lexical")
                             "lexical" "design")
                  help (make-help-button!
                        {:label (str "Explain " help-label)
                         :controls [(.-id item)]
                         :classes ["term-help-icon" category]})]
              (.appendChild (layout-slot layout) item)
              (if match
                (append-inline-help! match help)
                (.appendChild anchor help))))))
      (.remove registry))))

(defn- prepare-code-registries! [registries]
  (doseq [registry registries]
    (let [detail (.closest registry "details.article-code-detail")
          code (q detail "pre code")]
      (when (and detail code)
        (let [slot (.createElement js/document "div")
              items (q-all registry ":scope > details.article-explanation")]
          (set! (.-className slot)
                "article-explanation-slot article-code-explanation-slot")
          (.insertAdjacentElement (.closest code "pre") "afterend" slot)
          (doseq [item items]
            (let [symbol (or (data-value item "anchorTerm")
                             (data-value item "helpLabel") "")
                  match (first-code-symbol-match code symbol)
                  help (make-help-button!
                        {:label (str "Explain code symbol " symbol)
                         :controls [(.-id item)]
                         :classes ["code-help-icon"]})]
              (.appendChild slot item)
              (if match
                (append-inline-help! match help)
                (.appendChild code help))))
          (.remove registry))))))

(defn- prepare-equation-details! [code-details]
  (let [equation-code-details
        (filterv #(.contains (.-classList %) "article-equation-code")
                 code-details)]
    (doseq [detail equation-code-details]
      (when-let [anchor (preceding-equation detail)]
        (let [summary (q detail ":scope > summary")
              title (-> (or (some-> summary .-textContent) "")
                        (str/replace #"^Code detail:\s*" "")
                        str/trim)]
          (when (and (seq title)
                     (str/blank? (data-value anchor "equationTitle")))
            (aset (.-dataset anchor) "equationTitle" title)))))
    (doseq [detail code-details]
      (decorate-title! (q detail ":scope > summary") :code "</>"))
    (let [display-math (q-all js/document
                              "span.math.display, mjx-container[display=true]")
          targets
          (->> display-math
               (map #(or (.closest % ".equation-explanation-layout")
                         (.closest % "p")
                         (.-parentElement %)))
               (remove nil?)
               distinct
               vec)
          equation-details
          (mapv
           (fn [index target]
             (let [detail (.createElement js/document "details")
                   summary (.createElement js/document "summary")
                   body (.createElement js/document "div")
                   titled-child (q target "[data-equation-title]")
                   title (or (data-value target "equationTitle")
                             (data-value titled-child "equationTitle")
                             (str "Equation " (inc index)))]
               (set! (.-className detail) "article-equation-detail")
               (set! (.-open detail) true)
               (set! (.-textContent summary) (str "Equation: " title))
               (decorate-title! summary :equation "∑")
               (set! (.-className body) "article-equation-detail-body")
               (.insertBefore (.-parentElement target) detail target)
               (.appendChild detail summary)
               (.appendChild detail body)
               (.appendChild body target)
               detail))
           (range)
           targets)]
      (when (not= (count equation-details) (count equation-code-details))
        (.error js/console
                (str "Managed article contract: found "
                     (count equation-details)
                     " display equations but "
                     (count equation-code-details)
                     " equation code disclosures.")))
      equation-details)))

(defn- initialise! []
  (when-let [article-main
             (or (.getElementById js/document "quarto-document-content")
                 (q js/document "main"))]
    (.add (.-classList (.-body js/document)) "vocabulary-article-managed")
    (let [math-registries (q-all js/document ".math-explanation-registry")
          term-registries (q-all js/document ".term-explanation-registry")
          code-symbol-registries
          (q-all js/document ".code-symbol-explanation-registry")
          code-details (q-all js/document "details.article-code-detail")
          manageable?
          (or (seq math-registries)
              (seq term-registries)
              (seq code-symbol-registries)
              (seq code-details)
              (q js/document
                 (str "details.article-explanation, span.math.display, "
                      "mjx-container[display=true]")))]
      (if-not manageable?
        (do
          (some-> (q js/document ".article-explanations-toolbar") .remove)
          (some-> (q js/document ".article-explanation-rail") .remove)
          (.remove (.-classList (.-body js/document))
                   "article-explanations-open")
          (.remove (.-classList article-main) "article-explanations-open"))
        (let [global-button (ensure-toolbar! article-main)]
          (when-not (= "true" (data-value global-button "initialized"))
            (let [margin-query (.matchMedia js/window "(min-width: 1280px)")
                  toolbar (.closest global-button
                                    ".article-explanations-toolbar")
                  help-action
                  (or (.getElementById js/document "article-help-action")
                      (.closest global-button ".article-reading-action"))
                  code-action (.getElementById js/document
                                               "article-code-action")
                  code-button (.getElementById js/document
                                               "article-code-toggle")
                  code-status (.getElementById js/document
                                               "article-code-status")
                  equations-action (.getElementById js/document
                                                    "article-equations-action")
                  equations-button (.getElementById js/document
                                                    "article-equations-toggle")
                  equations-status (.getElementById js/document
                                                    "article-equations-status")]
              (prepare-math-registries! math-registries)
              (prepare-term-registries! term-registries article-main)
              (prepare-code-registries! code-symbol-registries)
              (let [equation-details (prepare-equation-details! code-details)
                    code-preference
                    (atom (read-preference (:code storage-keys) false))
                    equations-preference
                    (atom (read-preference (:equations storage-keys) true))]
                (doseq [item code-details]
                  (set! (.-open item) @code-preference))
                (doseq [item equation-details]
                  (set! (.-open item) @equations-preference))
                (let [explanations
                      (q-all js/document "details.article-explanation")
                      math-explanations
                      (filterv #(.contains (.-classList %) "math-explanation")
                               explanations)
                      terminology-explanations
                      (filterv #(.contains (.-classList %) "term-explanation")
                               explanations)
                      code-symbol-explanations
                      (filterv
                       #(.contains (.-classList %) "code-symbol-explanation")
                       explanations)
                      _
                      (doseq [item explanations]
                        (let [summary (q item ":scope > summary")]
                          (cond
                            (.contains (.-classList item)
                                       "code-symbol-explanation")
                            (decorate-title! summary :code-help "</>")

                            (.contains (.-classList item) "math-explanation")
                            (decorate-title! summary :math-help "∑")

                            :else
                            (decorate-title! summary :text-help "?"))))
                      help-buttons (q-all js/document ".article-help-icon")
                      heading (.getElementById js/document
                                               "article-explanations-heading")
                      description (.getElementById
                                   js/document
                                   "article-explanations-description")
                      status (.getElementById js/document
                                              "article-explanations-status")
                      item-origins (js/Map.)
                      item-anchors (js/Map.)
                      rail (.createElement js/document "aside")
                      rail-guide (.createElement js/document "section")
                      rail-guide-heading (.createElement js/document "strong")
                      rail-guide-text (.createElement js/document "p")
                      rail-hide-all (.createElement js/document "button")
                      rail-stack (.createElement js/document "div")
                      placement-frame (atom nil)]
                  (doseq [item explanations]
                    (let [slot (.closest item ".article-explanation-slot")
                          layout (when slot
                                   (.closest slot
                                             ".article-explanation-layout"))
                          anchor
                          (or (q layout
                                 ":scope > .article-explanation-anchor")
                              (some-> slot
                                      (.closest ".article-code-detail")
                                      (q "pre")))]
                      (.set item-origins item slot)
                      (.set item-anchors item anchor)))
                  (set! (.-className rail) "article-explanation-rail")
                  (.setAttribute rail "aria-label" "Expanded explanations")
                  (set! (.-className rail-guide)
                        "article-explanation-rail-guide")
                  (set! (.-textContent rail-guide-heading)
                        "Using explanation cards")
                  (set! (.-textContent rail-guide-text)
                        (str "Cards relevant to the text in view scroll into "
                             "place automatically. You can also scroll these "
                             "cards independently. Select an open card to hide it."))
                  (set! (.-type rail-hide-all) "button")
                  (set! (.-className rail-hide-all)
                        "article-explanation-hide-all")
                  (set! (.-textContent rail-hide-all)
                        "Hide all explanations")
                  (.appendChild rail-guide rail-guide-heading)
                  (.appendChild rail-guide rail-guide-text)
                  (.appendChild rail-guide rail-hide-all)
                  (set! (.-className rail-stack)
                        "article-explanation-rail-stack")
                  (.appendChild rail rail-guide)
                  (.appendChild rail rail-stack)
                  (.appendChild (.-body js/document) rail)
                  (letfn [(controlled-items [help]
                            (->> (str/split
                                  (or (.getAttribute help "aria-controls") "")
                                  #"\s+")
                                 (remove str/blank?)
                                 (map #(.getElementById js/document %))
                                 (remove nil?)
                                 vec))
                          (set-open! [item open]
                            (set! (.-open item) open))
                          (layouts []
                            (q-all js/document
                                   ".article-explanation-layout"))
                          (arrange-for-viewport! []
                            (if (.-matches margin-query)
                              (doseq [item explanations]
                                (.appendChild rail-stack item))
                              (doseq [item explanations]
                                (when-let [origin (.get item-origins item)]
                                  (.appendChild origin item)))))
                          (relevant-open-item []
                            (let [viewport-centre (/ (.-innerHeight js/window) 2)
                                  considered-anchors (js/Set.)]
                              (:item
                               (reduce
                                (fn [{:keys [distance] :as nearest} item]
                                  (if-not (.-open item)
                                    nearest
                                    (let [anchor (.get item-anchors item)]
                                      (if (or (nil? anchor)
                                              (.has considered-anchors anchor))
                                        nearest
                                        (let [_ (.add considered-anchors anchor)
                                              rect (.getBoundingClientRect anchor)
                                              candidate-distance
                                              (js/Math.abs
                                               (- (/ (+ (.-top rect)
                                                        (.-bottom rect))
                                                     2)
                                                  viewport-centre))]
                                          (if (< candidate-distance distance)
                                            {:item item
                                             :distance candidate-distance}
                                            nearest))))))
                                {:item nil
                                 :distance (.-POSITIVE_INFINITY js/Number)}
                                explanations))))
                          (centre-relevant-explanation! []
                            (doseq [item explanations]
                              (.remove (.-classList item) "is-relevant"))
                            (when (.-matches margin-query)
                              (when-let [relevant (relevant-open-item)]
                                (.add (.-classList relevant) "is-relevant")
                                (let [rail-rect (.getBoundingClientRect rail)
                                      viewport-centre-within-rail
                                      (- (/ (.-innerHeight js/window) 2)
                                         (.-top rail-rect))
                                      target-scroll
                                      (- (+ (.-offsetTop relevant)
                                            (/ (.-offsetHeight relevant) 2))
                                         viewport-centre-within-rail)]
                                  (set! (.-scrollTop rail)
                                        (js/Math.max 0 target-scroll))))))
                          (schedule-placement! []
                            (when (nil? @placement-frame)
                              (reset!
                               placement-frame
                               (.requestAnimationFrame
                                js/window
                                (fn []
                                  (reset! placement-frame nil)
                                  (centre-relevant-explanation!))))))
                          (sync! []
                            (let [open-items (filterv #(.-open %) explanations)
                                  all-open
                                  (and (seq explanations)
                                       (= (count open-items)
                                          (count explanations)))
                                  open-math
                                  (count (filter #(.-open %) math-explanations))
                                  open-terms
                                  (count
                                   (filter #(.-open %)
                                           terminology-explanations))
                                  open-code-symbols
                                  (count
                                   (filter #(.-open %)
                                           code-symbol-explanations))
                                  availability
                                  (available-controls
                                   {:explanation-count (count explanations)
                                    :code-count (count code-details)
                                    :equation-count (count equation-details)})]
                              (when toolbar
                                (set! (.-hidden toolbar)
                                      (not (:toolbar availability))))
                              (doseq [help help-buttons]
                                (let [targets (controlled-items help)]
                                  (.setAttribute
                                   help "aria-expanded"
                                   (str (and (seq targets)
                                             (every? #(.-open %) targets))))))
                              (doseq [layout (layouts)]
                                (let [slot (layout-slot layout)
                                      has-open
                                      (some #(and (.-open %)
                                                  (identical?
                                                   (.get item-origins %)
                                                   slot))
                                            explanations)]
                                  (when slot
                                    (.toggle (.-classList slot)
                                             "has-open" (boolean has-open)))))
                              (.toggle (.-classList rail) "has-open"
                                       (boolean (seq open-items)))
                              (.toggle (.-classList (.-body js/document))
                                       "article-explanations-open"
                                       (boolean (seq open-items)))
                              (.toggle (.-classList article-main)
                                       "article-explanations-open"
                                       (boolean (seq open-items)))
                              (.setAttribute global-button "aria-pressed"
                                             (str (boolean all-open)))
                              (set! (.-textContent global-button)
                                    (str (if all-open "Hide" "Show")
                                         " all help"))
                              (when heading
                                (set! (.-textContent heading)
                                      "Reading controls"))
                              (let [count-parts
                                    (cond-> []
                                      (seq math-explanations)
                                      (conj
                                       (str (count math-explanations)
                                            " mathematical item"
                                            (when-not
                                             (= 1 (count math-explanations)
                                                "s"))))
                                      (seq terminology-explanations)
                                      (conj
                                       (str (count terminology-explanations)
                                            " terminology item"
                                            (when-not
                                             (= 1
                                                (count terminology-explanations)
                                                "s"))))
                                      (seq code-symbol-explanations)
                                      (conj
                                       (str (count code-symbol-explanations)
                                            " code symbol"
                                            (when-not
                                             (= 1
                                                (count code-symbol-explanations)
                                                "s")))))
                                    progress-parts
                                    (cond-> []
                                      (seq math-explanations)
                                      (conj
                                       (str open-math " of "
                                            (count math-explanations)
                                            " mathematical item"
                                            (when-not
                                             (= 1 (count math-explanations)
                                                "s"))))
                                      (seq terminology-explanations)
                                      (conj
                                       (str open-terms " of "
                                            (count terminology-explanations)
                                            " terminology item"
                                            (when-not
                                             (= 1
                                                (count terminology-explanations)
                                                "s"))))
                                      (seq code-symbol-explanations)
                                      (conj
                                       (str open-code-symbols " of "
                                            (count code-symbol-explanations)
                                            " code symbol"
                                            (when-not
                                             (= 1
                                                (count code-symbol-explanations)
                                                "s")))))]
                                (when help-action
                                  (set! (.-hidden help-action)
                                        (not (:help availability))))
                                (when description
                                  (let [description-parts
                                        (cond-> []
                                          (seq explanations)
                                          (conj
                                           "Use ? for symbol and term help.")
                                          (and (seq code-details)
                                               (seq equation-details))
                                          (conj
                                           (str "Code and equation choices "
                                                "persist across every page in "
                                                "this authored namespace."))
                                          (and (seq code-details)
                                               (empty? equation-details))
                                          (conj
                                           (str "The code choice persists across "
                                                "every page in this authored "
                                                "namespace."))
                                          (and (empty? code-details)
                                               (seq equation-details))
                                          (conj
                                           (str "The equation choice persists "
                                                "across every page in this "
                                                "authored namespace.")))]
                                    (set! (.-textContent description)
                                          (str/join " " description-parts))))
                                (when status
                                  (set! (.-hidden status)
                                        (empty? explanations))
                                  (set! (.-textContent status)
                                        (cond
                                          (empty? explanations)
                                          "No optional help on this page."

                                          (empty? open-items)
                                          (str (str/join " and " count-parts)
                                               " "
                                               (if (= 1 (count explanations))
                                                 "is" "are")
                                               " hidden.")

                                          :else
                                          (str (str/join " and "
                                                         progress-parts)
                                               " shown.")))))
                              (let [open-code-count
                                    (count (filter #(.-open %) code-details))
                                    all-code-open
                                    (if (seq code-details)
                                      (= open-code-count (count code-details))
                                      @code-preference)]
                                (when code-action
                                  (set! (.-hidden code-action)
                                        (not (:code availability))))
                                (when code-button
                                  (.setAttribute code-button "aria-pressed"
                                                 (str all-code-open))
                                  (set! (.-textContent code-button)
                                        (if all-code-open
                                          "Hide all code" "Show all code")))
                                (when code-status
                                  (set! (.-textContent code-status)
                                        (if (seq code-details)
                                          (str open-code-count "/"
                                               (count code-details)
                                               " code sections open.")
                                          (str "No code here; global preference "
                                               "is "
                                               (if @code-preference
                                                 "show" "hide")
                                               ".")))))
                              (let [open-equation-count
                                    (count
                                     (filter #(.-open %) equation-details))
                                    all-equations-open
                                    (if (seq equation-details)
                                      (= open-equation-count
                                         (count equation-details))
                                      @equations-preference)]
                                (when equations-action
                                  (set! (.-hidden equations-action)
                                        (not (:equations availability))))
                                (when equations-button
                                  (.setAttribute equations-button
                                                 "aria-pressed"
                                                 (str all-equations-open))
                                  (set! (.-textContent equations-button)
                                        (if all-equations-open
                                          "Hide all equations"
                                          "Show all equations")))
                                (when equations-status
                                  (set! (.-textContent equations-status)
                                        (if (seq equation-details)
                                          (str open-equation-count "/"
                                               (count equation-details)
                                               " equation sections open.")
                                          (str "No equations here; global "
                                               "preference is "
                                               (if @equations-preference
                                                 "show" "hide")
                                               ".")))))))]
                    (.addEventListener
                     global-button "click"
                     (fn []
                       (let [should-open
                             (next-disclosure-open?
                              (mapv #(.-open %) explanations) false)]
                         (doseq [item explanations]
                           (set-open! item should-open))
                         (sync!)
                         (schedule-placement!))))
                    (when code-button
                      (.addEventListener
                       code-button "click"
                       (fn []
                         (let [should-open
                               (next-disclosure-open?
                                (mapv #(.-open %) code-details)
                                @code-preference)]
                           (reset! code-preference should-open)
                           (doseq [item code-details]
                             (set-open! item should-open))
                           (write-preference! (:code storage-keys)
                                              should-open)
                           (sync!)))))
                    (when equations-button
                      (.addEventListener
                       equations-button "click"
                       (fn []
                         (let [should-open
                               (next-disclosure-open?
                                (mapv #(.-open %) equation-details)
                                @equations-preference)]
                           (reset! equations-preference should-open)
                           (doseq [item equation-details]
                             (set-open! item should-open))
                           (write-preference! (:equations storage-keys)
                                              should-open)
                           (sync!)))))
                    (.addEventListener
                     rail-hide-all "click"
                     (fn []
                       (doseq [item explanations]
                         (set-open! item false))
                       (sync!)
                       (schedule-placement!)))
                    (doseq [help help-buttons]
                      (.addEventListener
                       help "click"
                       (fn []
                         (let [targets (controlled-items help)
                               should-open
                               (some #(not (.-open %)) targets)]
                           (doseq [item targets]
                             (set-open! item (boolean should-open)))
                           (sync!)
                           (schedule-placement!)))))
                    (doseq [item explanations]
                      (.addEventListener
                       item "click"
                       (fn [event]
                         (let [target (.-target event)
                               interactive
                               (and (instance? js/Element target)
                                    (.closest
                                     target
                                     "a,button,input,select,textarea"))]
                           (when (and (.-open item) (not interactive))
                             (.preventDefault event)
                             (set-open! item false)
                             (sync!)
                             (schedule-placement!)))))
                      (.addEventListener
                       item "toggle"
                       (fn []
                         (sync!)
                         (schedule-placement!))))
                    (doseq [item code-details]
                      (.addEventListener item "toggle" sync!))
                    (doseq [item equation-details]
                      (.addEventListener item "toggle" sync!))
                    (.addEventListener
                     margin-query "change"
                     (fn []
                       (arrange-for-viewport!)
                       (sync!)
                       (schedule-placement!)))
                    (.addEventListener js/window "scroll" schedule-placement!
                                       #js {:passive true})
                    (.addEventListener js/window "resize" schedule-placement!)
                    (.addEventListener
                     js/window "storage"
                     (fn [event]
                       (cond
                         (= (:code storage-keys) (.-key event))
                         (let [open (= "true" (.-newValue event))]
                           (reset! code-preference open)
                           (doseq [item code-details]
                             (set-open! item open))
                           (sync!))

                         (= (:equations storage-keys) (.-key event))
                         (let [open (not= "false" (.-newValue event))]
                           (reset! equations-preference open)
                           (doseq [item equation-details]
                             (set-open! item open))
                           (sync!)))))
                    (when-let [fonts (.-fonts js/document)]
                      (when-let [ready (.-ready fonts)]
                        (.then ready schedule-placement!)))
                    (.setTimeout js/window schedule-placement! 250)
                    (.setTimeout js/window schedule-placement! 1000)
                    (aset (.-dataset global-button) "initialized" "true")
                    (arrange-for-viewport!)
                    (sync!)
                    (schedule-placement!)))))))))))

(defn- start! []
  (if (= "loading" (.-readyState js/document))
    (.addEventListener js/document "DOMContentLoaded" initialise!
                       #js {:once true})
    (initialise!)))

(when (dom-available?)
  (start!))
