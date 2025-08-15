^:kindly/hide-code
^{:clay {:title  "What's the Buzz in Charlotte? A Pre-Conj Data Dive"
         :external-requirements ["Service_Requests_311.csv"]
         :quarto {:author      [:ezmiller]
                  :description "Getting to know our host city for Clojure/conj 2025. We dive into 311 service data to see what's on the minds of Charlotte's citizens and get a taste of our hands-on data analysis workshop."
                  :type        :post
                  :date        "2025-08-15"
                  :category    :conj
                  :tags        [:clay :workflow :conj]
                  :draft       true}}}
(ns conferences.conj2025.workshop.scicloj.buzz
  (:require [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]
            [babashka.fs :as fs]
            [camel-snake-kebab.core :as csk]))

;; This November 12th, Clojure enthusiasts will gather in **Charlotte, North Carolina**
;; for Clojure/conj 2025.
;; For many of us, it's a cherished yearly tradition of code,
;; conversations, and that special *"this is my people"* feeling.

;; ::: {.callout-note}
;; Fun fact: during the Revolutionary War, a British general called Charlotte a "hornet's nest of rebellion"
;; for its fierce resistance.
;; That same spirit of passionate engagement seems to live on in the city's civic life today,
;; on display using the hornet as their city emblem.
;; :::

;; In the spirit of exploration that defines our community, let's get to know our host city
;; before we even arrive.
;; What can 3 million service requests tell us about the heart of Charlotte?

;; ## A Glimpse into Charlotte's Data

;; Let's find out by diving into the city's 311 system, which captures the pulse
;; of city life through non-emergency service requests.
;; Every pothole report, noise complaint, and fallen tree notification tells a story.

;; Downloaded from [Charlotte Service Requests](https://data.charlottenc.gov/datasets/charlotte::service-requests-311/explore?location=35.265099%2C-80.810750%2C9.72).

(defonce Charlotte-311
  (tc/dataset (fs/file (fs/home) "Downloads" "Service_Requests_311.csv")
              {:key-fn csk/->kebab-case-keyword}))

;; Let's see what columns we have

(tc/column-names Charlotte-311)

;; I wonder what the request types are

(tc/group-by Charlotte-311 :request-type)

;; Let's see what are the most common requests

(def frequent-requests
  (-> Charlotte-311
      (tc/group-by :request-type)
      (tc/aggregate {:frequency tc/row-count})
      (tc/order-by :frequency :desc)))

frequent-requests

;; The most frequent request type is about non-recyclable items.
;; It suggests Charlotte residents care about recycling
;; but might need clearer guidelines about what can be recycled.
;; The high volume of requests also shows that people feel comfortable
;; asking the city for guidance.
;; Data like this helps cities improve their communication and services.

(-> frequent-requests
    (plotly/base {:=title "Charlotte 311 Service Requests"})
    (plotly/layer-bar {:=x-title "Request Type"
                       :=y :frequency
                       :=y-title "Count"}))

;; That's a long tail you have there.
;; Let's focus on the top 5.

(-> frequent-requests
    (tc/select-rows (range 5))
    (plotly/base {:=title "Charlotte 311 Service Requests"})
    (plotly/layer-bar {:=x :$group-name
                       :=x-title "Request Type"
                       :=y :frequency
                       :=y-title "Count"}))

;; This brief analysis gives us a snapshot of daily life in Charlotte, revealing a community
;; that's actively engaged in civic life and recycling. It's a perfect backdrop for the
;; kind of thoughtful problem-solving we cherish in the Clojure community.

;; ![Clean data, clean streets. A city after our own hearts.](buzz.webp)

;; ## Join Us at the Workshop

;; This journey from a raw CSV file to a clear visualization is exactly what our
;; **Empowering Data Analysis through SciCloj** [workshop](https://www.2025.clojure-conj.org/workshops) is all about.
;; Led by me, Ethan Miller, you'll start your Conj experience by diving into practical,
;; hands-on data analysis and come away with new ideas to carry back home.

;; **You will learn how to:**

;; - Load and explore real-world datasets
;; - Create clear, reproducible analyses
;; - Share your insights through interactive notebooks
;; - Leverage Clojure's immutable data structures for data science

;; We can't wait to explore data with you. See you in Charlotte!
