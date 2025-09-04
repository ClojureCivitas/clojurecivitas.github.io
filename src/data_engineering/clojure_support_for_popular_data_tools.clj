^{:kindly/hide-code true     ; don't render this code to the HTML document
  :clay             {:title  "Clojure Support for Popular Data Tools: A Data Engineer's Perspective, and a New Clojure API for Snowflake"
                     :quarto {:author   :alza-bitz
                              :draft    true           ; remove to publish
                              :type     :post
                              :date     "2025-09-04"
                              :category :clojure
                              :tags     [:metadata :civitas]}}}
(ns data-engineering.clojure-support-for-popular-data-tools)

;; In this article I look at the extent of Clojure support for some popular on-cluster data processing tools that Clojure users might need for their data engineering or data science tasks. Then for [Snowflake](https://snowflake.com) in particular **I go further and present a new Clojure API.**

;; Why is the level of Clojure support important? As an example, consider that [Scicloj](https://scicloj.org) is mostly focused on in-memory processing. As such, if you need to work with a large dataset it will be necessary to compute on-cluster and extract a smaller result before continuing your data science task locally.

;; However, without sufficient Clojure support for on-cluster processing, anyone needing that facility for their data science or data engineering task would be forced to reach outside the Clojure ecosystem. That adds complexity in terms of interop, compatibility and overall stack requirements.

;; With that in mind, let's examine the level of Clojure support for some popular on-cluster data processing tools. For each tool I selected its official Clojure library if one exists, or if not the most popular and well-known community-supported alternative with at least 100 stars and 10 contributors on GitHub. I then used the following criteria against the library to classify it as "supported" or "support unknown":

;; 1. CI/CD build passing
;; 1. Most recent commit less than 12 months ago
;; 1. Most recent release less than 12 months ago
;; 1. Maintainers responded to any issue or question less than 12 months ago
;; 1. Maintainers either accepted or rejected any PR less than 12 months ago

;; If I couldn't find any such library at all, I classified it as having "no support".

;; | Tool Category | Supported | Support Unknown | No Support |
;; |---------------|---------------------|--------------|---------------|
;; | **On-cluster batch processing** | | 1. [Spark](https://spark.apache.org) (see [Spark Interop with Geni](#spark_interop_with_geni) below) | |
;; | **On-cluster stream processing** | | 2. [Kafka Streams](https://kafka.apache.org/documentation/streams) (see [Kafka Interop with Jackdaw](#kafka_interop_with_jackdaw) below) | 3. [Spark Structured Streaming](https://spark.apache.org/streaming),\<br\>4. [Flink](https://flink.apache.org) |
;; | **On-cluster batch and stream processing** | | | 5. [Databricks](https://databricks.com) (see [Spark Interop with Geni](#spark_interop_with_geni) below),\<br\>6. [Snowflake](https://snowflake.com) (see [Snowflake Interop](#snowflake_interop_with_a_new_clojure_api!) below) |

;; Please note, I don't wish to make any critical judgments based on either the summary analysis above or the more detailed analysis below. The goal is to understand the situation with respect to Clojure support and highlight any gaps, although I suppose I am also inadvertently highlighting the difficulties of maintaining open source software!

;; ### Spark Interop with Geni

;; [Geni](https://github.com/zero-one-group/geni) is the go-to library for Spark interop. Some months back, I was motivated to evaluate the coverage of Spark features. In particular, I wanted to understand what would be involved to support [Spark Connect](https://spark.apache.org/spark-connect/) as it would reduce the complexity of computing on-cluster directly from the Clojure REPL.

;; However, I found a number of issues that would need to be addressed in order to support Spark Connect and Databricks: 

;; 1.  Problems with the [default session](https://github.com/zero-one-group/geni/issues/345).
;; 1.  Problems with [support for Databricks](https://github.com/zero-one-group/geni/issues/356), although I suspect this is related to point 1.

;; Also, in general by my criteria the support classification is "support unknown":
;; 1.  CI/CD build [failing.](https://github.com/zero-one-group/geni/actions)
;; 1.  Version [0.0.42 api docs broken](https://cljdoc.org/d/zero.one/geni/0.0.42/doc/readme%20%20https://cljdoc.org/builds/73977), also affects version 0.0.41
;; 1. No commits since November 2023.
;; 1. No releases since November 2023.
;; 1. No PRs accepted or rejected since November 2023.
;; 1. No response when attempting to contact the author or maintainers.

;; ### Kafka Interop with Jackdaw

;; [Jackdaw](https://github.com/FundingCircle/jackdaw) is the go-to library for Kafka interop. However, by my criteria the support classification is also "support unknown":

;; 1. No commits since August 2024.
;; 1. No releases since December 2023.
;; 1. No PRs accepted or rejected since August 2024. As a further example, [here's a PR](https://github.com/FundingCircle/jackdaw/pull/374) raised in May 2024 but not yet commented on either way by the maintainers.

;; ### Snowflake Interop with a New Clojure API!

;; Although the [Snowpark](https://docs.snowflake.com/en/developer-guide/snowpark/java) library has Java and Scala bindings, it doesn't provide anything for Clojure. As such, it's currently not possible to interact with Snowflake using the Clojure way.

;; To address this gap, I decided to try my hand at creating a [Clojure API for Snowflake](https://github.com/alza-bitz/snowflake-clj) as part of a broader effort to improve the overall situation regarding Clojure support for popular data tools.

;; The aim is to validate this approach as a foundation for enabling a wide range of data science or data engineering use cases from the Clojure REPL, in situations where Snowflake is the data warehouse of choice.

;; The [README](https://github.com/alza-bitz/snowpark-clj/blob/main/README.md) provides usage examples for all the current features, but I've copied the essential ones here to illustrate the API:

;; #### Feature 1. Load data from local and save to a Snowflake table

(require '[clojure.repl.deps :refer [add-lib]])
(add-lib 'io.github.alza-bitz/snowpark-clj {:git/url "https://github.com/alza-bitz/snowpark-clj.git"
                                            :git/sha "7856d9ca2080b188f9feec115ca709d3f54877b0"})

(require '[snowpark-clj.core :as sp])

;; Sample data
(def employee-data
  [{:id 1 :name "Alice" :age 25 :department "Engineering" :salary 75000}
   {:id 2 :name "Bob" :age 30 :department "Marketing" :salary 65000}
   {:id 3 :name "Charlie" :age 35 :department "Engineering" :salary 80000}])

;; Create session and save data
(with-open [session (sp/create-session "src/data_engineering/snowflake.edn")]
  (-> (sp/create-dataframe session employee-data)
      (sp/save-as-table "employees" :overwrite)))

;; #### Feature 2. Compute over Snowflake table(s) on-cluster to produce a smaller result for local processing

(with-open [session (sp/create-session "src/data_engineering/snowflake.edn")]
  (let [table-df (sp/table session "employees")]
    (-> table-df
        (sp/filter (sp/gt (sp/col table-df :salary) (sp/lit 70000)))
        (sp/select [:name :salary])
        (sp/collect))))

;; As an early-stage proof-of-concept, it only covers the essential parts of the underlying API without being too concerned with performance or completeness. Other more advanced features are noted and planned, pending further elaboration.

;; **I hope you find it useful and I welcome any feedback or contributions!**