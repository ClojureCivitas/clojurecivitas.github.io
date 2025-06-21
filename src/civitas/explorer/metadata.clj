^{:clay {:quarto {:draft true}}}
(ns civitas.explorer.metadata
  (:require [babashka.fs :as fs]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [markdown.core :as md]
            [clj-fuzzy.metrics :as fuzzy]))

(defn source-path-for [{:keys [topic id] :as notebook}]
  {:pre [id (seq topic)]}
  (str (fs/path "src"
                (str (symbol (first topic)))
                (str id ".md"))))

(defn spit-md [notebook]
  (str "---\n" (yaml/generate-string notebook) "---\n"))

(defn spit-notebook [{:keys [source-path] :as notebook}]
  (let [source-path (or source-path (source-path-for notebook))]
    (if (fs/exists? source-path)
      (println source-path "exists")
      (do
        (io/make-parents source-path)
        #_(spit source-path notebook)
        (println source-path "created")))))

(defn spit-all [notebooks]
  (run! spit-notebook notebooks))

(def Author
  [:map
   [:name :string]
   [:url {:optional true} :string]])

(def BlogPostFrontmatter
  [:map {:closed true}
   [:title :string]
   [:authors {:optional true} [:vector Author]]
   [:author {:optional true} Author]
   [:image {:optional true} :string]
   [:draft {:optional true} :boolean]
   [:publish-date {:optional true} inst?]
   [:last-modified-date {:optional true} inst?]
   [:tags {:optional true} [:vector :string]]
   [:categories {:optional true} [:vector :string]]
   [:description {:optional true} :string]
   [:slug {:optional true} :string]
   [:canonical-url {:optional true} :string]
   [:keywords {:optional true} [:vector :string]]
   [:layout {:optional true} :string]])

(def key-descriptions
  {:title              "The title of the blog post. Essential for SEO and user understanding."
   :authors            "A list of authors for the post. If multiple authors, this is necessary for proper attribution."
   :author             "The author information. Required for attribution."
   :image              "The URL to the featured image. Will be shown as your post preview."
   :draft              "Indicates whether the post is a draft. Should be set to true to prevent accidental publishing of incomplete posts."
   :publish-date       "The date and time the post should be published. Important for chronological ordering."
   :last-modified-date "The date and time the post was last modified. Important for knowing when an article was updated."
   :tags               "Keywords to categorize the content. Helps readers find relevant posts."
   :categories         "Broad categories to group content. Helps readers navigate a website by content."
   :description        "A brief description of the post, used for SEO."
   :slug               "The URL slug for the post. Important for URL structure and SEO."
   :canonical-url      "The canonical URL of the post. Prevents duplicate content issues."
   :keywords           "Additional keywords for SEO purposes."
   :layout             "The layout to use for this post, helps organize content with different visual layouts."})

(defn did-you-mean [key known-keys threshold]
  (some->> (filter #(<= (fuzzy/levenshtein (name key) (name %)) threshold) known-keys)
           (seq)
           (str/join " or ")
           (str "did you mean ")))

(defn warnings [front-matter]
  (->> (m/explain BlogPostFrontmatter front-matter)
       (me/humanize)
       (keep (fn [{[k] :path}]
               (some->> (or (get key-descriptions k)
                            (did-you-mean k (keys key-descriptions) 2))
                 (vector k))))))

(defn warn! [front-matter md-file]
  (when-let [ws (warnings front-matter)]
    (println "Front-matter warning:" md-file)
    (run! println ws)))

;; TODO: it might be more convenient to use Quarto to gather the metadata
;; ```
;; quarto list --to json
;; ```

(defn find-mds [site-dir]
  (map str (fs/glob site-dir "**.qmd")))

(defn front-matter [md-file]
  (-> (slurp md-file)
      (md/md-to-meta)
      (assoc :source-path md-file)
      (doto (warn! md-file))))

(defn front-matters [site-dir]
  (->> (find-mds site-dir)
       (mapv front-matter)))
