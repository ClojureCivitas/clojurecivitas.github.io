(ns language-learning.vocabulary-estimation.math-explanations
  (:require [clojure.string :as str]
            [scicloj.kindly.v4.kind :as kind]))

(defn styles []
  (kind/hiccup
   [:style
    (str
     ".article-explanations-toolbar{display:grid;grid-template-columns:minmax(0,1fr) repeat(3,auto);align-items:center;gap:.8rem;min-width:0;border:1px solid color-mix(in srgb,var(--bs-body-color,#212529) 22%,transparent);border-left:4px solid #2780e3;border-radius:.35rem;padding:.8rem 1rem;margin:1.25rem 0;background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 88%,#2780e3 12%);color:var(--bs-body-color,#212529)}"
     ".article-explanations-toolbar p{min-width:0;margin:0;overflow-wrap:anywhere}"
     ".article-explanations-toolbar strong{color:inherit}"
     ".article-explanations-toggle,.article-code-toggle,.article-equations-toggle{border:1px solid #2780e3;border-radius:.35rem;padding:.5rem .8rem;font-weight:700;cursor:pointer;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}"
     ".article-explanations-toggle[aria-pressed=true],.article-code-toggle[aria-pressed=true],.article-equations-toggle[aria-pressed=true]{background:#1464b5;color:#fff}"
     ".article-explanations-toggle:focus-visible,.article-code-toggle:focus-visible,.article-equations-toggle:focus-visible{outline:3px solid color-mix(in srgb,#2780e3 50%,transparent);outline-offset:3px}"
     ".article-help-icon:focus-visible,.article-explanation summary:focus-visible,.article-code-detail summary:focus-visible{outline:3px solid color-mix(in srgb,var(--explanation-accent,#2780e3) 55%,transparent);outline-offset:3px}"
     ".article-explanations-description,.article-explanations-status,.article-code-status,.article-equations-status{display:block;margin-top:.2rem;font-size:.84rem;color:#3f4b55}"
     ".article-reading-action{display:grid;gap:.2rem;min-width:min(100%,12rem)}"
     ".article-code-detail{min-width:0;margin:1rem 0;border:1px solid color-mix(in srgb,#6f42c1 45%,var(--bs-border-color,#dee2e6));border-radius:.55rem;background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 94%,#6f42c1 6%);color:var(--bs-body-color,#212529)}"
     "details.article-code-detail>summary{padding:.72rem .85rem;font-weight:750;cursor:pointer;color:var(--bs-body-color,#212529)!important;overflow-wrap:anywhere}"
     ".article-code-detail[open] summary{border-bottom:1px solid color-mix(in srgb,#6f42c1 35%,transparent)}"
     ".article-code-detail-body{min-width:0;padding:.8rem .9rem 1rem}"
     ".article-code-detail-body>*:first-child{margin-top:0}.article-code-detail-body>*:last-child{margin-bottom:0}"
     ".article-code-detail pre{max-width:100%;overflow:auto;margin:.75rem 0;padding:.75rem;border-radius:.4rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 84%,var(--bs-body-color,#212529) 16%)}"
     ".article-code-detail code{white-space:pre-wrap;overflow-wrap:anywhere}"
     ".article-code-source{font-size:.86rem}"
     ".article-code-provenance{margin:0 0 .7rem;font-size:.86rem}"
     ".article-code-provenance strong{color:inherit}"
     ".article-equation-code{margin-top:.35rem;margin-bottom:1.35rem}"
     ".article-equation-detail{min-width:0;margin:1rem 0 .35rem;border:1px solid color-mix(in srgb,#2780e3 42%,var(--bs-border-color,#dee2e6));border-radius:.55rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 96%,#2780e3 4%)}"
     ".article-equation-detail>summary{padding:.7rem .85rem;font-weight:750;cursor:pointer;color:var(--bs-body-color,#212529)!important}"
     ".article-equation-detail[open]>summary{border-bottom:1px solid color-mix(in srgb,#2780e3 30%,transparent)}"
     ".article-equation-detail-body{min-width:0;padding:.45rem .85rem .7rem}"
     ".article-code-explanation-slot{margin:.45rem 0 0}"
     ".article-code-detail pre .article-help-icon{margin-left:.35rem;vertical-align:middle}"
     ".article-chapter-map{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,11rem),1fr));gap:.65rem;margin:1.25rem 0;padding:0;list-style:none;counter-reset:chapter-map}"
     ".article-chapter-map li{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.5rem;padding:.75rem;background:var(--bs-body-bg,#fff);overflow-wrap:anywhere;counter-increment:chapter-map}"
     ".article-chapter-map li::before{content:counter(chapter-map);display:grid;place-items:center;width:1.6rem;height:1.6rem;margin-bottom:.45rem;border-radius:50%;background:#1464b5;color:#fff;font-weight:800}"
     ".article-marker{display:inline-block;margin:.2rem .35rem .2rem 0;border-radius:999px;padding:.18rem .58rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 82%,#2780e3 18%);color:var(--bs-body-color,#212529);font-size:.78rem;font-weight:800;letter-spacing:.03em;text-transform:uppercase}"
     ".article-recap{min-width:0;border:1px solid color-mix(in srgb,#0f695f 45%,var(--bs-border-color,#dee2e6));border-left:4px solid #0f695f;border-radius:.5rem;padding:1rem 1.1rem;margin:1.35rem 0;background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 91%,#0f695f 9%);color:var(--bs-body-color,#212529)}"
     ".article-explanation-layout,.article-explanation-anchor,.article-explanation-slot{min-width:0}"
     ".article-explanation-layout{margin:0 0 .35rem}"
     ".article-help-icon{--explanation-accent:#155f9f;display:inline-grid;place-items:center;width:1.2rem;height:1.2rem;margin-left:.25rem;padding:0;border:1px solid var(--explanation-accent);border-radius:50%;background:var(--bs-body-bg,#fff);color:var(--explanation-accent);font:800 .75rem/1 system-ui,sans-serif;vertical-align:.14em;cursor:pointer}"
     ".article-help-icon.lexical{--explanation-accent:#0f695f}"
     ".article-help-icon.design{--explanation-accent:#5b3f8f}"
     ".article-help-icon.accent-2,.article-explanation.accent-2{--explanation-accent:#a24700}"
     ".article-help-icon.accent-3,.article-explanation.accent-3{--explanation-accent:#7d3a9c}"
     ".article-help-icon.accent-4,.article-explanation.accent-4{--explanation-accent:#0f695f}"
     ".article-help-icon.accent-5,.article-explanation.accent-5{--explanation-accent:#9a2f5f}"
     ".article-help-icon.accent-6,.article-explanation.accent-6{--explanation-accent:#6c5b00}"
     ".article-help-icon[aria-expanded=true]{background:var(--explanation-accent);color:#fff}"
     ".article-explanation-slot{display:grid;gap:.6rem}"
     ".article-explanation{--explanation-accent:#155f9f;display:none;min-width:0;margin:0}"
     ".article-explanation.term-explanation.lexical{--explanation-accent:#0f695f}"
     ".article-explanation.term-explanation.design{--explanation-accent:#5b3f8f}"
     ".article-explanation[open]{display:block;border:2px solid var(--explanation-accent);border-radius:.55rem;background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 92%,var(--explanation-accent) 8%);color:var(--bs-body-color,#212529);box-shadow:0 0 0 3px color-mix(in srgb,var(--explanation-accent) 14%,transparent);cursor:pointer}"
     ".article-explanation summary{display:block;max-width:100%;border-bottom:1px solid color-mix(in srgb,var(--explanation-accent) 45%,transparent);padding:.62rem .75rem;font-weight:700;cursor:pointer;color:var(--bs-body-color,#212529);background:transparent;overflow-wrap:anywhere}"
     ".article-explanation-term{font-weight:750;color:inherit;white-space:normal;overflow-wrap:anywhere}"
     ".article-explanation-body{min-width:0;padding:.7rem .75rem .8rem}"
     ".article-explanation-body p{min-width:0;margin:0;overflow-wrap:anywhere}"
     ".article-explanation.is-relevant{box-shadow:0 0 0 4px color-mix(in srgb,var(--explanation-accent) 32%,transparent)}"
     ".article-explanation-rail{display:none}"
     ".article-explanation-rail-guide{display:none}"
     ".article-explanation-rail-guide p{margin:.3rem 0 .65rem;font-size:.82rem;line-height:1.4}"
     ".article-explanation-hide-all{width:100%;border:1px solid #1464b5;border-radius:.35rem;padding:.45rem .65rem;background:#1464b5;color:#fff;font-weight:700;cursor:pointer}"
     ".article-explanation-hide-all:focus-visible{outline:3px solid color-mix(in srgb,#2780e3 50%,transparent);outline-offset:3px}"
     ".quarto-dark .article-help-icon,.quarto-dark .article-explanation{--explanation-accent:#73b7ff}"
     ".quarto-dark .article-help-icon.lexical,.quarto-dark .article-explanation.term-explanation.lexical{--explanation-accent:#40c9b7}"
     ".quarto-dark .article-help-icon.design,.quarto-dark .article-explanation.term-explanation.design{--explanation-accent:#b79ae5}"
     ".quarto-dark .article-help-icon.accent-2,.quarto-dark .article-explanation.accent-2{--explanation-accent:#ffad66}"
     ".quarto-dark .article-help-icon.accent-3,.quarto-dark .article-explanation.accent-3{--explanation-accent:#d7a6f2}"
     ".quarto-dark .article-help-icon.accent-4,.quarto-dark .article-explanation.accent-4{--explanation-accent:#40c9b7}"
     ".quarto-dark .article-help-icon.accent-5,.quarto-dark .article-explanation.accent-5{--explanation-accent:#ff8fbd}"
     ".quarto-dark .article-help-icon.accent-6,.quarto-dark .article-explanation.accent-6{--explanation-accent:#e2cf5b}"
     ".quarto-dark .article-explanations-description,.quarto-dark .article-explanations-status,.quarto-dark .article-code-status,.quarto-dark .article-equations-status{color:#b9c7d2}"
     ".quarto-dark .article-help-icon[aria-expanded=true]{color:#10212b}"
     "#quarto-document-content{transition:transform .2s ease}"
     "@media(min-width:1280px){"
     "body.article-explanations-open #quarto-document-content{transform:translateX(-8rem)}"
     ".article-explanation-layout{position:relative}"
     ".article-explanation-rail{position:fixed;left:calc(50% + 17.25rem);top:5rem;z-index:2;display:block;width:20rem;height:calc(100dvh - 5rem);overflow-y:auto;overscroll-behavior:contain;scrollbar-width:thin;pointer-events:none}"
     ".article-explanation-rail.has-open{pointer-events:auto}"
     ".article-explanation-rail.has-open>.article-explanation-rail-guide{position:sticky;top:.4rem;z-index:4;display:block;margin:0 0 .7rem;border:1px solid color-mix(in srgb,#2780e3 55%,var(--bs-border-color,#dee2e6));border-radius:.55rem;padding:.7rem .75rem;background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 88%,#2780e3 12%);color:var(--bs-body-color,#212529);box-shadow:0 .2rem .65rem color-mix(in srgb,#000 18%,transparent)}"
     ".article-explanation-rail-stack{display:grid;gap:.6rem;min-width:0;padding:50vh 0}"
     ".equation-help-anchor{position:relative}"
     ".equation-help-anchor>.equation-help-icon{position:absolute;left:calc(100% + .15rem);top:50%;z-index:3;margin:0;transform:translateY(-50%)}"
     "}"
     "@media(max-width:1279px){.article-explanation-slot.has-open{margin:.55rem 0 1.25rem}.equation-help-icon{display:grid;margin:.35rem auto 0}}"
     "@media(max-width:991px){.article-explanations-toolbar{grid-template-columns:minmax(0,1fr) repeat(3,minmax(0,1fr))}.article-explanations-toolbar>p{grid-column:1/-1}}"
     "@media(max-width:767px){.article-explanations-toolbar{grid-template-columns:minmax(0,1fr);align-items:stretch}.article-explanations-toolbar>p{grid-column:auto}.article-explanations-toggle,.article-code-toggle,.article-equations-toggle{width:100%}.article-reading-action{width:100%}}")]))

(defn global-controls []
  (kind/hiccup
   [:div.article-explanations-toolbar
    {:role "region" :aria-labelledby "article-explanations-heading"}
    [:p
     [:strong#article-explanations-heading "Reading controls"]
     [:span.article-explanations-description
      {:id "article-explanations-description"}
      "Help explains symbols and terms. Code details show how the examples were built. These controls are independent."]
     [:span.article-explanations-status
      {:id "article-explanations-status" :aria-live "polite"}
      "All explanation items are hidden by default."]]
    [:div#article-help-action.article-reading-action
     [:button.article-explanations-toggle
      {:id "article-explanations-toggle"
       :type "button"
       :aria-pressed "false"
       :aria-describedby "article-explanations-description article-explanations-status"}
      "Show all help"]]
    [:div.article-reading-action
     {:id "article-code-action"}
     [:button.article-code-toggle
      {:id "article-code-toggle"
       :type "button"
       :aria-pressed "false"
       :aria-describedby "article-explanations-description article-code-status"}
      "Show all code details"]
     [:span.article-code-status
      {:id "article-code-status" :aria-live "polite"}
      "Code is hidden."]]
    [:div.article-reading-action
     {:id "article-equations-action"}
     [:button.article-equations-toggle
      {:id "article-equations-toggle"
       :type "button"
       :aria-pressed "true"
       :aria-describedby "article-explanations-description article-equations-status"}
      "Hide equations"]
     [:span.article-equations-status
      {:id "article-equations-status" :aria-live "polite"}
      "Equations are shown."]]]))

(defn- code-detail-node [id purpose classes attributes body]
  (let [summary-id (str id "--summary")
        body-id (str id "--body")]
    [:details
     (merge {:id id
             :class (str/join
                     " " (into ["article-code-detail"] classes))}
            attributes)
     [:summary
      {:id summary-id :aria-controls body-id}
      (str "Code detail: " purpose)]
     [:div.article-code-detail-body
      {:id body-id :role "region" :aria-labelledby summary-id}
      body]]))

(defn code-detail
  "Render one closed, inline implementation detail with an independent native
  disclosure control. Body is arbitrary Hiccup, so callers may mix prose,
  preformatted code, and a source link."
  [id purpose body]
  (kind/hiccup (code-detail-node id purpose [] {} body)))

(defn- code-symbol-registry [id symbols]
  (into
   [:div.code-symbol-explanation-registry
    {:data-code-symbol-group id}]
   (map-indexed
    (fn [index [symbol definition]]
      [:details.article-explanation.code-symbol-explanation
       {:id (str id "--code-symbol-" (inc index))
        :data-anchor-term symbol
        :data-help-label symbol}
       [:summary [:code.article-explanation-term symbol]]
       [:div.article-explanation-body [:p definition]]])
    symbols)))

(defn equation-code-detail
  "Render the required closed code disclosure for one display equation.

  Provenance must be either {:kind :source :label ... :href ...}, for code
  copied from the repository, or {:kind :explanation :label ...}, for a direct
  code explanation using the implementation's symbol names. Both forms require
  :symbols pairs so code identifiers receive the same optional help as equation
  symbols."
  [id purpose {:keys [kind label href symbols]} body]
  {:pre [(#{:source :explanation} kind)
         (seq label)
         (or (= :explanation kind) (seq href))
         (seq symbols)
         (every? (fn [[symbol definition]]
                   (and (seq symbol) (seq definition)))
                 symbols)]}
  (let [source? (= :source kind)
        provenance-node
        [:p.article-code-provenance
         [:strong (if source? "Source: " "Code explanation: ")]
         (if source?
           [:a {:href href} label]
           label)]]
    (kind/hiccup
     (code-detail-node
      id purpose ["article-equation-code"]
      {:data-equation-code "true"
       :data-code-provenance (name kind)}
      (into [:div provenance-node (code-symbol-registry id symbols)]
            (if (= :div (first body)) (rest body) [body]))))))

(defn- math-item
  [id term definition]
  [:details.article-explanation.math-explanation
   {:id id :data-help-label term}
   [:summary
    [:code.article-explanation-term term]]
   [:div.article-explanation-body
    [:p definition]]])

(defn explanation
  ([id title terms]
   (explanation id title terms nil))
  ([id title terms note]
   (kind/hiccup
    (into
     [:div.article-explanation-registry.math-explanation-registry
      {:data-explanation-group id :data-explanation-title title}]
     (concat
      (map-indexed (fn [index [term definition]]
                     (math-item (str id "--term-" (inc index))
                                term definition))
                   terms)
      (when note
        [[:details.article-explanation.math-explanation.equation-context
          {:id (str id "--context") :data-help-label "Equation context"}
          [:summary
           [:span.article-explanation-term "About this equation"]]
          [:div.article-explanation-body
           [:p note]]]]))))))

(defn terminology
  [id category _label title terms]
  (kind/hiccup
   (into
    [:div.article-explanation-registry.term-explanation-registry
     {:data-explanation-group id :data-explanation-title title}]
    (map-indexed
     (fn [index [term definition anchor-term]]
       [:details
        {:id (str id "--term-" (inc index))
         :class (str "article-explanation term-explanation " (name category))
         :data-anchor-term (or anchor-term term)
         :data-help-label term}
        [:summary
         [:span.article-explanation-term term]]
        [:div.article-explanation-body
         [:p definition]]])
     terms))))
