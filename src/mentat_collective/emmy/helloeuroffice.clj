^{:kindly/hide-code true
  :clay             {:title  "Emmy and the EurOffice Spreadsheet"
                     :quarto {:author      :kloimhardt
                              :type        :post
                              :description "Coding Clojure with scittle-kitchen in the FOSS EurOffice suite"
                              :date        "2026-04-16"
                              :image       "helloemmy.gif"
                              :category    :math
                              :tags        [:emmy :euroffice :spreadsheet]}}}

(ns mentat-collective.emmy.helloeuroffice
  (:refer-clojure :exclude [+ - * / zero? compare divide numerator denominator
                            time infinite? abs ref partial =])
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [mentat-collective.emmy.scheme :refer [define let-scheme lambda] :as scheme]
            [emmy.env :refer :all]))

;; [EurOffice](https://github.com/Euro-Office), the AGPL licensed collaborative document editing software which runs its spreadsheets in the browser, can be extended via plugins using JavaScript.

;; Emmy, the Clojure Algebra System, compiles to ClojureScript and is included in [scittle-kitchen](https://timothypratley.github.io/scittle-kitchen/).

;; The animation below shows a spreadsheet using the [EurOffice-Emmy-plugin](https://github.com/kloimhardt/scittle_kitchen_plugin).

^:kindly/hide-code
(kind/hiccup
  [:img {:src "helloemmy.gif" :width "100%"}])

;; EurOffice is a fork of OnlyOffice. The Emmy-plugin is based on its [Html-plugin](https://api.onlyoffice.com/docs/plugin-and-macros/samples/plugins/get-and-paste-html/). The same Clojure calculations that are demonstrated in the spreadsheet are also shown below. They are explained in more detail in the prologue of the civitas-hosted [Emmy book](https://clojurecivitas.org/mentat_collective/emmy/fdg_prologue.html).

(define ((L-harmonic m k) local)
  (let ((q (coordinate local))
        (v (velocity local)))
    (- (* 1/2 m (square v))
       (* 1/2 k (square q)))))

(define (proposed-solution t)
  (* 'a (cos (+ (* 'omega t) 'phi))))

(->infix
  (simplify
    (((Lagrange-equations (L-harmonic 'm 'k))
      proposed-solution)
     't)))

;; Of course the EurOffice plugin processes not just Emmy but any Scittle-Clojure code.

;; At this point in time, while obviously possible, the local installation of EurOffice and subsequent registration of plugins is rather involved and not for the novice. It is described in the [Readme](https://github.com/kloimhardt/scittle_kitchen_plugin/blob/main/README.md). Nonetheless, my hope is that the evolving EurOffice stack will eventually provide an easy to use local plugin system. The Emmy-plugin will then be distributed to any EurOffice user as just a single easy-to-register zip-file.
