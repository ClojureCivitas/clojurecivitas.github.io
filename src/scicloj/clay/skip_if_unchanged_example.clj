^{:kindly/hide-code true
  :clay
  {:title  "Some Civitas notebooks should only be run locally"
   :quarto {:author      :daslu
            :description "A demonstration of our practice with some Civitas notebooks that cannot be run in GitHub Pages."
            :image       "skip-if-unchanged.jpg"
            :type        :post
            :date        "2025-07-25"
            :category    :clay
            :tags        [:clay :workflow]
            :draft       true}}}
(ns scicloj.clay.skip-if-unchanged-example)

;; (Work-In-Progress Draft)

;; Usually, when we wish to create Clojure Civitas posts, we enjoy the fact
;; that Civitas runs our notebooks in the GitHub Actions as it renders the website.

;; While this is the default behavior, sometimes, we cannot expect our notebooks
;; to be run in GitHub Actions. For example, they may depend on a local file
;; or service.

;; This notebook, for example, assumes that you have a local secrets file,
;; and it will not work without it!

(slurp "/home/daslu/my-secret.txt")

;; If you are the author of such a notebook, the recommended practice is to
;; render the notebook locally usinc Clay in Quarto `.qmd` format, and include
;; that file in your Pull Request.

;; The `.qmd` file is all that Civitas needs to include your notebook in the
;; website. As long as the `.qmd` file is included in the PR, and is not older than
;; your source `.clj` file, Civitas will just rely on it and not even try
;; to generate it in GitHub Actions.

;; To do that, you will need to make the file locally with a Quarto target.

;; Here are two ways to do that:

;; 1. Use the command line.
;; Note that here, we use the path to the notebook, relative to `src`.
;; ```sh
;; clojure -M:clay -A:markdown scicloj/clay/skip_if_unchanged_example.clj
;; ```

;; 2. ... Or do the same in Clojure code.
(comment
  (require '[scicloj.clay.v2.api :as clay])
  (clay/make! {:source-path "scicloj/clay/skip_if_unchanged_example.clj"
               :aliases [:markdown]}))



;; Now, need to `git add` the generated `qmd` file.
;; Here is how.

;; (WIP)
