(ns mentat-collective.emmy.spell
  (:require [emmy.env :as e :refer :all]
            [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]))

(defn is-equal! [a b]
  (when (zero? (simplify (- a b))) a))

(defn solves! [a f]
  (when (zero? (simplify (f a))) a))

(defn postfix? [ctx ex]
  ((-> ctx :Schlusselworte :postfix)
   (str (last ex))))

(defn infix? [ctx ex]
  ((-> ctx :Schlusselworte :infix)
   (str (second ex))))

(defn infix-function? [ctx ex]
  ((-> ctx :Schlusselworte :infix-function) (str (second ex))))

(defn ifx-fn-mp-decon? [ctx ex]
  (and (infix-function? ctx ex)
       ((-> ctx :Schlusselworte :infix-function-map-deconstrucion)
        (str (second (last ex))))))

(defn ifx-fn-reverse? [ctx ex]
  (and (infix-function? ctx ex)
       ((-> ctx :Schlusselworte :infix-function-reverse)
        (str (second (last ex))))))

(defn notext? [ctx smb]
  ((-> ctx :Schlusselworte :notext)
   (str smb)))

(defn npow [x n]
  (apply * (repeat n x)))

(defn mypow [x n]
  (if (integer? n)
    (npow x n)
    (expt x n)))

(defn n-transpose [m]
  (apply mapv vector m))

(defn r-transpose [x]
  (reverse (n-transpose x)))

(defn mround [x] (floor (+ x 0.5)))

(defn to-double [x]
  (let [n (exp -30)]
    (- (+ x n) n)))

(defn round [x dec]
  (to-double (/ (mround (* x (mypow 10 dec))) (mypow 10 dec))))

(defn str-to-int [i]
  ;; in maria.cloud
  #_(int i)
  (Integer/parseInt i))

(def plus +)
(def minus -)
(def mal *)
(def times *)
(def von *)
(def dot *)
(def durch /)
(def hoch mypow)
(def power mypow)
(def null 0)
(def Ein 1)
(def Milliard (fn [x] (* x (mypow 10 9))))
(def stel (fn [x y] (/ x y)))
(defn Komma [& lst]
  (let [a (apply str (rest lst))]
    (to-double (+ (first lst) (/ (str-to-int a) (mypow 10 (count a)))))))

(def comma Komma)

(defn sw [ctx ex]
  (cond
    (not (coll? ex)) ex
    (ifx-fn-mp-decon? ctx ex)
    (list (list 'fn [{:keys (first (last ex))}] (first ex))
          (last (last ex)))
    (ifx-fn-reverse? ctx ex)
    (list (list 'fn [(last (last ex))] (first ex))
          (first (last ex)))
    (infix-function? ctx ex)
    (list (list 'fn [(first (last ex))] (first ex))
          (last (last ex)))
    (postfix? ctx ex)
    (conj (butlast ex) (last ex))
    (infix? ctx ex)
    (conj (rest (rest ex)) (first ex) (second ex))
    :else            ex))

(defn bx [ctx ex]
  (if (not (coll? ex)) ex
      (str "\\fbox{"
           (apply str
                  (interpose " " (remove #(notext? ctx %) ex)))
           "}")))

(defn maxcount [xs mini]
  (apply max (conj (map #(-> (second %) :style :count) (filter coll? xs)) mini)))

(defn b [p] {:style {:border "1px solid gray" :padding (str p "px") :count p}})

(defn hx [ctx ex]
  (if (not (coll? ex))
    (str " " ex " ")
    (let [rex (remove #(notext? ctx %) ex)]
      (into [:span (b (+ 3 (maxcount rex 2)))] rex))))

(defn table-formula? [ctx ex]
  (and (coll? (last ex))
       ((-> ctx :Schlusselworte :infix-function) (str (first (last ex))))))

(defn threadzero [ctx ex]
  (if (table-formula? ctx ex)
    (if (> (count ex) 2)
      (cons (threadzero ctx (butlast ex)) (last ex))
      (cons (first ex) (last ex)))
    ex))

(def gctx {:Schlusselworte
           {:infix
            #{"plus" "minus" "mal" "von" "dot" "durch" "hoch" "Ein" "Milliard"
              "Komma"
              "times" "power" "comma"}
            :postfix
            #{"stel"}
            :infix-function
            #{"mit" "und" "with" "and"}
            :infix-function-map-deconstrucion
            #{"aus" "being"}
            :infix-function-reverse
            #{"für" "for"}
            :notext
            #{"dot"}}
           :Schulwissen
           {:e  euler
            :pi pi}})

(defn my-walk
  [inner outer form]
  (cond
    (list? form)      (outer (apply list (map inner form)))
    (map-entry? form) form
    (seq? form)       (outer (doall (map inner form)))
    (record? form)    (outer (reduce (fn [r x] (conj r (inner x))) form form))
    (coll? form)      (outer (into (empty form) (map inner form)))
    :else             (outer form)))

(defn postwalk
  [f form]
  (my-walk (partial postwalk f) f form))

(defmacro calcbox [ex & dbg]
  (let [swe (postwalk #(sw gctx %) (threadzero gctx ex))
        bxe (if (vector? ex)
              (->> (cons (first ex) (apply concat (rest ex)))
                   (map (fn [mex] (postwalk #(bx gctx %) mex)))
                   (interpose " \\\\ ")
                   (apply str))
              (postwalk #(bx gctx %) ex))
        hxe (if (vector? ex)
              (->> (cons (first ex) (apply concat (rest ex)))
                   (map (fn [mex] (postwalk #(hx gctx %) mex)))
                   (map #(vector :p [:div %]))
                   (into [:p]))
              [:p (postwalk #(hx gctx %) ex)])]
    {:code   `'~swe
     :calc   (if (seq dbg) (into [] dbg) swe)
     :tex    bxe
     :hiccup hxe}))

