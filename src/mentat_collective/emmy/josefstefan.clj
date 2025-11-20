^{:kindly/hide-code true
  :clay             {:title  "Emmy, the Algebra System: Infix Notation"
                     :quarto {:author      :kloimhardt
                              :type        :post
                              :description "Emmy can wear a different apparel should the occasion demand it."
                              :date        "2025-11-19"
                              :image       "fdg_ch01_ys.jpg"
                              :category    :libs
                              :tags        [:emmy :infix :yamlscript]}}}
(ns mentat-collective.emmy.josefstefan
  (:refer-clojure :exclude [+ - * / = abs compare zero? ref partial
                            times numerator denominator infinite?])
  (:require [emmy.env :as e :refer :all :exclude [F->C times]]
            [mentat-collective.emmy.spell :refer :all]
            [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]))

;; Alrady the pronounciation of the latin letters $x$ ("ex"), $y$ ("wye") and $z$ ("zee") is peculiar to the teutonic tongue. In textbooks, the associated greek letters are usially $\xi$ ("xi"), $\eta$ ("eta") and $\zeta$ ("zeta"). This needs practice. All the more so that there is this greek $\chi$ ("chi").

;; I'd like to make a table for this:

^:kindly/hide-code
(kind/table
  [["xi" (kind/md "$\\xi$")]
   ["eta" (kind/md "$\\eta$")]
   ["zeta" (kind/md "$\\zeta$")]
   ["chi" (kind/md "$\\chi$")]])

^:kindly/hide-code
(def html kind/hiccup)

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
(def constants {:eta 'eta :zeta 'zeta})

^:kindly/hide-code
(tex (:calc (dp-formel2 'xi constants )))

;; How on earth do you pronounce that? Here we go

^:kindly/hide-code
(kind/hiccup (:hiccup (dp-formel2 0 constants)))

;; You already get a hint that the xi-$\xi$ is a temperature measured in degree Celsius. But what are the constants eta-$\eta$ and zeta-$\zeta$?

;; ## The formula of Dulong&Petit

;; To reveal that, I define the same function, but this time including the numbers Dulong&Petit gave us

(defn dp-formula [Celsius]
  (calcbox [((((eta power x) minus 1) times zeta)
             with
             [x in Celsius])
            [and [(1 comma 0 0 77) for eta]]
            [and [(2 comma 0 2) for zeta]]]))

;; This reads as

^:kindly/hide-code
(kind/hiccup (:hiccup (dp-formula 0)))

;; The formula prints like this

(tex (:calc (dp-formula 'xi)))

;; I can now also calculate a number, e.g. for 100 degrees

(:calc (dp-formel1 100))

;; But what does this number mean?

;; # 2 J. Stefan: Über die Beziehung zwischen der Wärmestrahlung und der Temperatur

;;Temperatur in Celsius
(def temper [80 100 120 140 160 180 200 220 240])

;; Dulong und Petit angegebene Messdaten (extrapoliert aus Messungen)
(def dp-meas [1.74 2.30 3.02 3.88 4.89 6.10 7.40 8.81 10.69])

;; von D&P angegeben als nach ihrer Heuristik gerechnet
(def dp-theor [1.72 2.33 3.05 3.89 4.87 6.03 7.34 8.89 10.68])

;; vom Stefan berechnete Werte
(def st-theor [1.66 2.30 3.05 3.92 4.93 6.09 7.42 8.92 10.62])

(defn dp-formel [Celsius]
  (calcbox [((((A hoch x) minus 1) mal M)
             mit
             [x in Celsius])
            [und [(1 Komma 0 0 77) für A]]
            [und [(2 Komma 0 2) für M]]]))
(html (:hiccup (dp-formel 0)))

(def tb2 (map (fn [[T m t]]
                [T m (round (- t m) 2) t (round (:calc (dp-formel T)) 2)])
              (n-transpose [temper dp-meas dp-theor])))

;; Temperatur Messwert Differenz RechenWert_D&P Meine-Rechnung

tb2

(defn stefan-formel [Celsius]
  (calcbox [((((T plus x) hoch 4 )
              minus
              (T hoch 4))
             mal
             B)
            [mit [x in Celsius]]
            [und [(Ein (6 Milliard) stel) für B]]
            [und [273 für T]]]))
(html (:hiccup (stefan-formel 0)))

(def pow_273_4 (calcbox [(((T mal T) mal T) mal T) [mit [T gleich 273]]]))
(html (:hiccup pow_273_4))

(:calc pow_273_4)

;; Unten passt die Differenz mit Paper überein, auch Meine-Rechnung lt. Paper gerechnet passt
(def tb7 (map (fn [[T m st-t]]
                [T m (round (- st-t m) 2) st-t
                 (round (:calc (stefan-formel T)) 2)])
              (n-transpose [temper dp-meas st-theor])))

;; Temperatur Messwert Differenz Rechenwert_St Meine-Rechnung
tb7

(defn minsec [sec]
  (mapv mround [(quot sec 60) (mod sec 60)]))

(defn speed->sec [sp]
  (* (/ 20.0 sp) 60))

(defn tb3 [tbl]
  (map (fn [tb]
         (map #(minsec (speed->sec %)) tb))
       tbl))

;; Umrechnung von Messwerten und Rechnungen nach Zeitdauer -- MinutenSekunden, Absteigende Temperatur

(tb3 (r-transpose [dp-meas dp-theor st-theor]))

(def Minuten 'Minuten)
(def Minute 'Minute)
(def Sekunden 'Sekunden)
(def Sekunde 'Sekunde)
(def Eine 'Eine)
(def und 'und)
(def ist 'ist)
(def Null 'Null)

(defn text-minute [m]
  (case m
    0 [Null Minuten]
    1 [Eine Minute]
    [m Minuten]))

(defn text-sekunde [m]
  (case m
    0 [Null Sekunden]
    1 [Eine Sekunde]
    [m Sekunden]))

(defn tb4 [tb]
  (mapv (fn [[m s]] [(text-minute m) und (text-sekunde s)])
        tb))

;; Zeitdauer der Messwerte in Worten
(tb4 (map first (tb3 (r-transpose [dp-meas]))))

(def minus- 'minus)
(def plus+ 'plus)

(defn tb6 [tb]
  (mapv (fn [[[am as] [bm bs]]]
          [[bs
            (if (neg? (- as bs)) minus- plus+)
            (abs (- as bs))]
           ist
           as])
        tb))
;; Differenz der Sekunden: D&P-Rechnung Differenz D&P-Messung
(tb6 (tb3 (r-transpose [dp-meas dp-theor])))

;; Differenz der Sekunden: St-Rechnung Differenz D&P-Messung
(tb6 (tb3 (r-transpose [dp-meas st-theor])))

;; # 3 Kleine Fingerübungen

(def bsp0 (calcbox (1 plus 3)))
(html (:hiccup bsp0))

(:calc bsp0)

;; praktisch zum debuggen: der generierte code
(:code bsp0)

(def bsp1 (calcbox (2 plus 1) "andere Rechnung" (+ 4 5)))
(html (:hiccup bsp1))

;; noch praktischer zum debuggen: calculation unterdrücken
(:calc bsp1)

(def bsp2 (calcbox ((X plus 1) mit [X gleich [(Y plus 2) mit [Y gle=ich 3]]])))
(html (:hiccup bsp2))

(:calc bsp2)

(def bsp3 (calcbox ( [(X plus Y) mit [X ist-gleich 3]] mit [Y ist 2]) ))
;; es ist wurst ob glei=ch, ist-gleich, -in-: nur ein Füllwort
(html (:hiccup bsp3))

(:calc bsp3)

(defn bsp4 [Sekunden Y]
  (calcbox ((X plus Y) mit [X -in- Sekunden])))

(html (:hiccup (bsp4 0 0)))

(:calc (bsp4 5 6))

(defn bsp5 [{:keys [Schulwissen]}]
  (calcbox ((e hoch pi) mit [[e pi] aus Schulwissen])))
(html (:hiccup (bsp5 gctx)))

(def bsp6 (calcbox [(X plus Y) [mit [X gleich (Y plus 7)]] [mit [Y gle=ich 3]]]))
(html (:hiccup bsp6))

;; # 4 deBroglie Wavelength

;; ## Internal Vibarations
;; as always with Einstein, we start with E = mc^2
(defn E0 [m] (* m 'c 'c))

;; deBroglies first hypothesis was to assume that every particle has a hypothetical
;; internal vibration at frequency nu0 which relates to the rest energy
;; in rest frame of particle (only there this energy-frequency relation holds)
(defn nu_naught [E0] (/ E0 'h))

;; particle travels at velocity v
(defn v [beta] (* beta 'c))
(defn beta [v] (/ v 'c))
(defn gamma [beta] (/ 1 (sqrt (- 1 (* beta beta)))))

;; time dilation: internal vibration is slower for observer.
;; so the frequency-energy relation does not hold: the frequency indeed decreases
;; instead of increasing with energy.
;; this is the conundrum deBroglie solved. so hang on.
(defn nu_one [nu_naught gamma] (/ nu_naught gamma))

;; sine formula for internal vibration. we do not know what exactly vibrates
;; so we set the amplitude to one
(defn internal-swing [nu_one]
  (fn [t] (sin (* 2 'pi nu_one t))))

;; calculate the phase of the internal swing at particle point x = v * t
(defn internal-phase [nu_one x v]
  (asin ((internal-swing nu_one) (/ x v))))

(is-equal! (* 2 'pi 'nu_one (/ 'x 'v))
           (internal-phase 'nu_one 'x 'v))

;; personal note: to me, this is the sine-part of a standing wave, the standing vibration.

;; ## A general Wave
;; now for something completely different: general definition of a wave
(defn wave [omega k]
  (fn [x t] (sin (- (* omega t) (* k x)))))

;; with the usual definition of omega
(defn omega [nu] (* 2 'pi nu))

;; and the simplest possible definition for the wave-vector k:
;; a dispersion free wave traveling at phase-velocity V
(defn k [omega V] (/ omega V))

;; calculate the phase of the wave
(defn wave-phase [nu x t V]
  (asin ((wave (omega nu) (k (omega nu) V)) x t)))

(is-equal! (* 2 'pi 'nu (- 't (/ 'x 'V)))
           (wave-phase 'nu 'x 't 'V))

;; ## Phase difference

;; calculate the phase difference between the vibration
;; and some wave at time t = x / v
;; as a function of the ratio of the frequencies

(defn phase-difference [r x v nu V]
  (- (internal-phase (* r nu) x v)
     (wave-phase nu x (/ x v) V)))

(is-equal! (* 2 'pi 'nu (+ (* (- 'r 1) (/ 'x 'v)) (/ 'x 'V)))
           (phase-difference 'r 'x 'v 'nu 'V))

;; state the general ratio of frequencies that keeps
;; the vabration of the particle in phase with some wave of velocity V
;; in terms of the velocity of the particle

(solves! (@(defn nu-ratio-in-phase [v V] (- 1 (/ v V)))
          'v 'V)
         (fn [r] (phase-difference r 'x 'v 'nu 'V)))

;; the Energy of the particle for the observer

(defn E [E0 gamma] (* E0 gamma))

;; we assume the deBroglie wave has the frequency: energy devided by Planck's constant.
;; reminder: this relation holds in every frame of reference,
;; especially for the observer who is not in the rest frame.
(defn nu [E] (/ E 'h))

;; now that nu is set, calculate the physically viable ratio of the frequencies
;; in terms of beta
(defn physical-nu-ratio [beta]
  (/ (nu_one (nu_naught 'E0) (gamma beta))
     (nu (E 'E0 (gamma beta)))))

(is-equal! (- 1 (* 'beta 'beta))
           (physical-nu-ratio 'beta))

;; state the value of the physical phase-velocity V
;; that keeps the vibration and the deBroglie wave in phase
;; in terms of the particle velocity v
(solves! (@(defn phase-velocity [beta] (/ 'c beta))
          'beta)
         (fn [V] (- (physical-nu-ratio 'beta)
                    (nu-ratio-in-phase (v 'beta) V))))

;;note: the phase-velocity is always greater than the speed of light. It is independent
;; of the position x and the mass of the particle

;; the relativistic momentum is defined as

(defn p [m v gamma]
  (* m v gamma))

;; calculate the deBroglie wavelength (by dividing the phase-velocity by the frequency)
;; and show that it indeed is h devided by the momentum
(def de-broglie-wavelength
  (/ (phase-velocity (beta 'v))
     (nu (E (E0 'm) 'gamma))))

(is-equal! (/ 'h (p 'm 'v 'gamma))
           de-broglie-wavelength)

;; personal note: one can see this upside down. V and nu define not only the deBroglie phase wave but also a standing wave (standing vibration). and the intersection of the two waves gives the trajectory of the particle (and hence it's velocity v). Intersection meaning the points where the phase of the wave and the sine-part of the standing vibration-wave have the same value. The mass of the particle is then given by the deBroglie-wavelength and the v. Mass is thus a constant of the motion, the same value in every frame.

(println "Success!!")
