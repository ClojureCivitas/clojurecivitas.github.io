(ns civitas.why.village.color)

(def palette
  ["#FCFFE0"                                                ;  0
   "#D0F040"                                                ;  1
   "#E6E6FA"                                                ;  2
   "#D0F7FF"                                                ;  3
   "#FCECC5"                                                ;  4
   "#FCE2F7"                                                ;  5
   "#E0FFD0"                                                ;  6
   "#F0D0E0"                                                ;  7
   "#40D0A0"                                                ;  8
   "#FF8080"                                                ;  9
   "#F0F0C8"                                                ; 10
   "#AFA050"                                                ; 11
   "#040622"                                                ; 12
   ])

(def scicloj-palette-descriptions
  ;; fill-color description text-color
  [;; Clojure logo colors
   ["#FFFFFF" "white background" "black"]
   ["#62B132" "Clojure green" "white"]
   ["#5881D8" "Clojure blue" "white"]
   ["#91DC47" "Clojure light green" "black"]
   ["#8FB5FE" "Clojure light blue" "black"]
   ;; Contrasting colors that work well with the Clojure logo colors
   ["#F26767" "coral red" "white"]
   ["#FFCD52" "golden yellow" "black"]
   ["#A86F40" "chocolate brown" "white"]
   ["#E0E0E0" "light gray" "black"]
   ["#808080" "medium gray" "white"]
   ["#2F4179" "midnight blue" "white"]])

(def scicloj-palette
  (mapv first scicloj-palette-descriptions))
