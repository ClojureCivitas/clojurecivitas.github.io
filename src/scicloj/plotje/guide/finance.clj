^:kindly/hide-code
(ns scicloj.plotje.guide.finance
  {:kindly/options {:kinds-that-hide-code #{:kind/var :kind/hiccup :kind/hiccup2 :kind/fn}}
   :clay {:title          "Financial Markets on Three Timeframes"
          :page-layout    :full
          :quarto         {:author      [:timothypratley]
                           :description "A chart dashboard in Clojure"
                           :date        "2026-11-05"
                           :image       "finance.png"
                           :type        :post
                           :category    :clay
                           :tags        [:clay :dashboard :layout :finance :clj-yfinance :plotje]}}}
  (:require [clj-yfinance.core :as yf]
            [clj-yfinance.dataset :as yfd]
            [scicloj.plotje.api :as pj]
            [scicloj.plotje.impl.defaults :as pd]
            [tablecloth.api :as tc])
  (:import (java.time Instant)))

;; What if you built your own financial dashboard? Here's one approach using Clojure.

(defn yahoo* [tickers opts]
  (-> (yfd/multi-ticker->dataset tickers opts)
      (tc/add-columns {:date (fn [ds]
                               (map #(Instant/ofEpochSecond %)
                                    (:timestamp ds)))})))

(def yahoo (memoize yahoo*))

(defn index-tickers [ds]
  (let [timestamps  (:timestamp ds)
        tickers     (:ticker ds)
        closes      (:adj-close ds)

        first-dates
        (->> (map vector timestamps tickers closes)
             (filter #(some? (nth % 2)))
             (group-by second)
             (map (fn [[ticker rows]]
                    [ticker (apply min (map first rows))]))
             (into {}))

        common-start (apply max (vals first-dates))

        bases
        (->> (map vector timestamps tickers closes)
             (filter (fn [[timestamp _ close]]
                       (and (>= timestamp common-start)
                            (some? close))))
             (reduce (fn [m [timestamp ticker close]]
                       (if (contains? m ticker)
                         m
                         (assoc m ticker close)))
                     {}))]
    (tc/add-column
     ds
     :indexed
     (map (fn [ticker close]
            (when (some? close)
              (/ close (bases ticker))))
          tickers
          closes))))

(defn plot [ds title]
  (-> ds
      ;; TODO: some charts should be indexed, some should also be inflation adjusted, some risk free adjusted
      (cond-> (not= title "Treasuries") (index-tickers))
      (pj/lay-line :date (if (= "Treasuries" title) :adj-close :indexed)
                   {:color :ticker})
      (pj/scale :x {:label "" :breaks []})
      (pj/scale :y {:label "" :breaks []})
      (pj/options {:legend-position :none
                   :theme {:bg "#00000000"}})))

(def securities
  {"Treasuries" {"13w" "^IRX"
                 "5y" "^FVX"
                 "10y" "^TNX"
                 "30y" "^TYX"}
   "Stocks"     {"S&P 500" "^GSPC"
                 "Nasdaq" "^IXIC"
                 "Euro Stoxx" "^STOXX50E"
                 "Nikkei 225" "^N225"}
   "Metals"     {"Gold" "GC=F"
                 "Copper" "HG=F"
                 "Steel" "SLX"}
   "Oil"        {"US Crude" "CL=F"
                 "Nat Gas" "NG=F"
                 "Diesel" "HO=F"
                 "Gasoline" "RB=F"}
   "Currencies" {"USD" "DX-Y.NYB"
                 "EUR" "EURUSD=X"
                 "JPY" "JPYUSD=X"
                 "AUD" "AUDUSD=X"}})

(defn rgba->hex [[r g b a]]
  (let [to-byte (fn [v] (Math/round (double (* 255 (or v 1.0)))))]
    (format "#%02X%02X%02X%02X"
            (to-byte r)
            (to-byte g)
            (to-byte b)
            (to-byte a))))

(defn hex-for [categories val]
  (rgba->hex (pd/color-for categories val)))

(defn market-quotes [title tickerm]
  [:div
   [:div {:style {:text-align "center"}}
    [:strong title]]
   [:div {:style {:display :grid
                  :grid-template-columns "1fr auto"
                  :gap "0 0"
                  :align-content :start}}
    (for [[label ticker] tickerm]
      (list
       [:div
        [:span {:class "plotje-plot"
                :style {:display :inline-block
                        :width "0.5rem"
                        :height "0.15rem"
                        :background (hex-for (vals tickerm) ticker)
                        :vertical-align "middle"
                        :margin-right "0.4rem"
                        :border-radius "2px"}}]
        [:span label]]
       [:div {:style {:text-align :right
                      :font-variant-numeric "tabular-nums"}}
        (format "%.3f" (yf/fetch-price ticker))]))]])

(defn market-charts [title tickerm]
  (let [tickers (vals tickerm)
        periods ["max" "5y" "6mo"]]
    (for [period periods]
      (plot (yahoo tickers {:period period}) title))))

(defn market-plots [instruments]
  ^:kind/hiccup
  [:div
   [:style ".plotje-plot, .js-plotly-plot { width: 100%; height: auto; }"]
   [:div {:style {:display :grid}}
    [:div {:style {:display :grid
                   :grid-template-columns "repeat(4, 1fr)"}}
     (for [header ["MAX" "5 YEAR" "6 MONTH" nil]]
       [:div {:style {:text-align "center"}} header])]
    (for [[title tickerm] instruments]
      [:div {:style {:display :grid
                     :grid-template-columns "repeat(4, 1fr)"}}
       (market-charts title tickerm)
       (market-quotes title tickerm)])]])

(market-plots securities)

(defn localized-timestamp-component [^Instant instant]
  ;; This renders to: <time datetime="2026-11-05T17:45:00Z" class="js-local-time">2026-11-05 17:45 UTC</time>
  [:time {:datetime (.toString instant)
          :class "js-local-time"}
   "Loading time..."])

^:kind/hiccup
[:strong "As of " (localized-timestamp-component (Instant/now))]

;; ---

;; Why three timeframes?
;;
;; Rather than zooming through an interactive chart, seeing three timeframes at once
;; shows you the whole picture: where we've been, the longer trend, and where we are now.

;; ::: {.callout-warning}
;; These charts are updated only when ClojureCivitas recompiles,
;; which is when a new article is added.
;; Run this notebook locally if you wish to see more recent data.
;; :::

;; Want to monitor different securities? Update the `securities` map below and re-render the notebook.

^:kind/pprint
securities

;; Built with [clj-yfinance](https://github.com/clojure-finance/clj-yfinance) by Matt Buehlmaier ![](https://avatars.githubusercontent.com/u/14025139?v=4){width=16}
;; for data, [Plotje](https://scicloj.github.io/plotje/) for charts, and [Clay](https://github.com/scicloj/clay) by Daniel Slutsky ![](https://avatars.githubusercontent.com/u/5673102?v=4){width=16}
;; for layout.

^:kind/hiccup
[:script "
document.addEventListener(\"DOMContentLoaded\", () => {
  const formatter = new Intl.DateTimeFormat(undefined, {
    dateStyle: \"medium\",
    timeStyle: \"short\"
  });

  document.querySelectorAll(\"time.js-local-time\").forEach(el => {
    const utcDate = new Date(el.dateTime);
    el.textContent = formatter.format(utcDate);
  });
});
"]
