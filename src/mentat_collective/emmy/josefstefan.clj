^{:kindly/hide-code true
  :clay             {:title  "How to pronounce greek letters"
                     :quarto {:author      :kloimhardt
                              :type        :post
                              :description "Josef Stefan's fourth-power law, written in both greek and english."
                              :date        "2025-11-27"
                              :image       "josefstefan.png"
                              :category    :libs
                              :tags        [:emmy :physics :notation]}}}
(ns mentat-collective.emmy.josefstefan
  (:refer-clojure :exclude [+ - * / = abs compare zero? ref partial
                            times numerator denominator infinite?])
  (:require [emmy.env :as e :refer :all :exclude [F->C times]]
            [mentat-collective.emmy.spell :refer :all]
            [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]))

;; The greek letters $\xi$ ("xi"), $\eta$ ("eta") and $\zeta$ ("zeta") are often used in textbooks as counterparts to the latin x,y and z. This needs practice. All the more so that there also are the two greeks $\chi$ ("chi") and $\omega$ ("omega").

;; I'd like to make a table for this:

^:kindly/hide-code
(kind/table
  [["xi" (kind/md "$\\xi$")]
   ["eta" (kind/md "$\\eta$")]
   ["zeta" (kind/md "$\\zeta$")]
   ["chi" (kind/md "$\\chi$")]
   ["omega" (kind/md "$\\omega$")]]
  )

;; For practice, I start with a widely forgotten formula by Messieurs Dulong and Petit

^:kindly/hide-code
(def tex (comp kind/tex emmy.expression.render/->TeX))

^:kindly/hide-code
(defn dp-formel2 [Celsius constants]
  (calcbox [((((eta power xi) minus 1) times zeta)
             with
             [xi in Celsius])
            [and [[eta zeta] being constants]]]))

^:kindly/hide-code
(def greek-alphabet {:eta 'eta :zeta 'zeta :chi 'chi :omega 'omega})

^:kindly/hide-code
(tex (:calc (dp-formel2 'xi greek-alphabet)))

;; How on earth do you pronounce that? Here we go

^:kindly/hide-code
(kind/hiccup [:blockquote (:hiccup (dp-formel2 0 greek-alphabet))])

;; Included is the hint that the xi-$\xi$ is a temperature measured in degree Celsius. But what are the constants eta-$\eta$ and zeta-$\zeta$?

;; ## The formula of Dulong&Petit

;; I define a function which includes the sought-for numbers

(defn dp-formula [Celsius]
  (calcbox [((((eta power xi) minus 1) times zeta)
             with
             [xi in Celsius])
            [and [(1 comma 0 0 77) for eta]]
            [and [(2 comma 0 2) for zeta]]]))

;; This reads as

(kind/hiccup [:blockquote (:hiccup (dp-formula 0))])

;; The formula prints like this

(tex (:calc (dp-formula 'xi)))

;; I can now also calculate a number, e.g. for 100 °C

(:calc (dp-formula 100))

;; But what does this number mean? It is degrees per minute. In 1817, Dulong and Petit measured the rate of change with time of the indicated temperature on a previously heated mercury-in-glass thermometer with a spherical bulb placed centrally in a spherical enclosure held at zero degrees Celsius.

;; These were their measured values

^:kindly/hide-code
(def temper [80 100 120 140 160 180 200 220 240])

^:kindly/hide-code
(def dp-meas [1.74 2.30 3.02 3.88 4.89 6.10 7.40 8.81 10.69])

^:kindly/hide-code
(kind/table
  {"°C"       temper
   "°C / min" dp-meas})

;; So, when the thermometer was at 100°C, within the first minute it lost 2.3 degrees due to radiation. In that case, their formula was pretty accurate. But a man named Pouillet used the formula to estimate the temperature of the sun, got a value of some 1700 degrees, and that seemed pretty low to a certain Josef Stefan. He re-published the above data and proposed his famous fourth-power law on the relationship between heat-radiation and temperature on pages 391-428 of the "Sitzungsberichte der Kaiserlichen Akademie der Wissenschaften, Mathematisch-Naturwissenschaftliche Classe, Neunundsiebzigster Band, Wien, 1879".

;; ## The law of Stefan

(defn stefan-law [Celsius constants]
  (calcbox [((((chi plus xi) power 4 )
              minus
              (chi power 4))
             times
             omega)
            [with [xi in Celsius]]
            [and [[chi omega] being constants]]]))

^:kindly/hide-code
(tex (:calc (stefan-law 'xi greek-alphabet)))

^:kindly/hide-code
(kind/hiccup [:blockquote (:hiccup (stefan-law 0 greek-alphabet))])

;; We need to set omega-$\omega$ to one six billionth. The other constant is given by the absolute zero temperature, chi-$\chi$ = 273.

;; As an exposition, we calculate the fourth-power of 273.
;; The result is a pretty big number.

^:kindly/hide-code
(def pow_273_4 (calcbox [(((chi times chi) times chi) times chi) [with [chi equals 273]]]))

^:kindly/hide-code
(kind/hiccup [:blockquote (:hiccup pow_273_4)])

^:kindly/hide-code
(:calc pow_273_4)

;; I can imagine that in the 19th century, without having computers, to fit some data it took considerable guts to take on a fourth power law.

(defn stefan-law-numbers [Celsius]
  (calcbox [((((chi plus xi) power 4 )
              minus
              (chi power 4))
             times
             omega)
            [with [xi in Celsius]]
            [and [(one (6 billion) th) for omega]]
            [and [273 for chi]]]))

^:kindly/hide-code
(kind/hiccup [:blockquote (:hiccup (stefan-law-numbers 0))])

^:kindly/hide-code
(tex (:calc (stefan-law-numbers 'xi)))

;; Stefan's Law passes the first test in fitting the data as well as the old model.

^:kindly/hide-code
(kind/table
  {"°C"          temper
   "°C / min"    dp-meas
   "D&P-formula" (map #(round (:calc (dp-formula %)) 2) temper)
   "Stefan law"  (map #(round (:calc (stefan-law-numbers %)) 2) temper)})

;; With his new formula, Josef Stefan estimated the lower bound of the temperature of the sun to be around 5600 °C which means he was pretty much bang-on within some 100 degrees.
