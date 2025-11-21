; traction -- presentations without slides
; Copyright 2011 (c) Chris Houser. All rights reserved.
;
; This program is free software: you can redistribute it and/or modify
; it under the terms of the GNU General Public License as published by
; the Free Software Foundation, either version 3 of the License, or
; (at your option) any later version.
;
; This program is distributed in the hope that it will be useful,
; but WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
; GNU General Public License for more details.

(ns civitas.why.village.traction
  (:require [clojure.string :as str]))

(def svg (js/document.getElementById "app"))

(defn parameterize [start end]
  (let [diff (- end start)]
    (fn [t] (+ start (* t diff)))))

(defn pt*m [x y m]
  (-> (new js/DOMPoint x y)
      (.matrixTransform m)))

(defn step-viewbox
  "Computes a viewBox that frames `target` in `svg`, scaled by `scale`,
   and adjusted to match the SVG's aspect ratio so nothing is cropped.
   - scale < 1: zoom in (tighter)
   - scale > 1: zoom out (looser)"
  ([target]
   (let [scale 0.8
         bbt (.getBBox target)
         bbx (.-x bbt)
         bby (.-y bbt)
         bbw (.-width bbt)
         ;; HACK: for some reason some slides bounding box is too tall
         ;;bbh (.-height bbt)
         bbh (/ bbw 1.1547)
         bbm (-> (.getScreenCTM svg)
                 (.inverse)
                 (.multiply (.getScreenCTM target)))
         top-left (pt*m bbx bby bbm)
         bottom-right (pt*m (+ bbx bbw) (+ bby bbh) bbm)
         x (.-x top-left)
         y (.-y top-left)
         w (- (.-x bottom-right) (.-x top-left))
         h (- (.-y bottom-right) (.-y top-left))
         cx (+ x (/ w 2))
         cy (+ y (/ h 2))
         ;; apply zoom scaling
         w* (* w scale)
         h* (* h scale)
         ;; adjust to match viewport aspect ratio
         viewport-ar (/ (.-clientWidth svg)
                        (.-clientHeight svg))
         box-ar (/ w* h*)
         [final-w final-h] (if (> viewport-ar box-ar)
                             ;; viewport wider — extend width
                             [(* h* viewport-ar) h*]
                             ;; viewport taller — extend height
                             [w* (/ w* viewport-ar)])
         ;; recenter around original midpoint
         final-x (- cx (/ final-w 2))
         final-y (- cy (/ final-h 2))]
     {:x final-x
      :y final-y
      :width final-w
      :height final-h})))

(defn new-transition [start end]
  (if (number? start)
    (if (= start end)
      start
      (parameterize start end))
    (zipmap (keys start)
            (map new-transition (vals start) (vals end)))))

(defn compute-animation [obj t]
  (cond
    (fn? obj) (obj t)
    (map? obj) (zipmap (keys obj) (map #(compute-animation % t) (vals obj)))
    :else obj))

(defn limit-step [old-step steps f]
  (max 0 (min (dec (count steps)) (f old-step))))

(defn apply-world [w]
  (doseq [[k v] w]
    (cond
      (= :view k)
      (let [{:keys [x y width height]} v]
        (.setAttribute svg "viewBox" (str x "," y "," width "," height)))
      (= :duration k)
      nil
      :else
      (let [elem (js/document.getElementById k)]
        (when (:opacity v)
          (set! (.-opacity (.-style elem)) (:opacity v)))))))

(defonce computed-steps (atom nil))
(defonce world (atom nil))
(defonce step (atom 0))
(defonce transition (atom nil))

(defn anim []
  (let [trans @transition
        {:keys [start duration]} (meta trans)
        t (min 1 (/ (- (js/Date.) start) (max duration 500)))]
    (reset! world (compute-animation trans t))
    (apply-world @world)
    (when (< t 1)
      (js/requestAnimationFrame anim))))

(defn alter-step [f]
  (let [i (swap! step limit-step @computed-steps f)
        current-world @world
        target-world (nth @computed-steps i)
        id (str "step" i)]
    (when (not= current-world target-world)
      (-> js/document .-location .-hash (set! (str \# id)))
      (reset! transition
              (with-meta
                (new-transition current-world target-world)
                {:start (js/Date.) :duration (:duration target-world)}))
      (js/requestAnimationFrame anim))))

(defn set-step [i]
  (alter-step (constantly i)))

(defn movement-svg [ev svg]
  (let [from-screen (.inverse (.getScreenCTM svg))
        cx (.-clientX ev)
        cy (.-clientY ev)
        mx (or (some-> (.-deltaX ev) -)
               (.-movementX ev))
        my (or (some-> (.-deltaY ev) -)
               (.-movementY ev))
        start (js/DOMPoint.fromPoint #js{:x (- cx mx)
                                         :y (- cy my)})
        end (js/DOMPoint.fromPoint #js{:x cx
                                       :y cy})
        a (.matrixTransform start from-screen)
        b (.matrixTransform end from-screen)
        dx (- (.-x b) (.-x a))
        dy (- (.-y b) (.-y a))]
    [dx dy]))

(defn set-viewbox! [svg x y width height]
  (.setAttribute svg "viewBox" (str x "," y "," width "," height)))

(defn get-viewbox [svg]
  (map js/parseFloat (str/split (.getAttribute svg "viewBox") #",")))

(defn pan-viewbox [ev svg]
  (when-let [[x y width height] (get-viewbox svg)]
    (let [[dx dy] (movement-svg ev svg)
          x' (- x dx)
          y' (- y dy)]
      (set-viewbox! svg x' y' width height))))

(defn zoom-viewbox [svg [dx dy] point]
  (when-let [[x y width height] (get-viewbox svg)]
    (let [delta (if (> (abs dx) (abs dy))
                  dx
                  dy)]
      (when (not (zero? delta))
        (let [;; Wheel event sizes are not standardized
              scale-factor 1.05
              scale (cond
                      (pos? delta) scale-factor
                      (neg? delta) (/ 1 scale-factor))
              w' (* width scale)
              h' (* height scale)

              ;; Keep the point under the cursor fixed in place.
              ;; Distance from view-box to cursor determines how much to pan.
              ;; Consider 0 distance (cursor is at origin), no pan.
              ;; Consider cursor is at (width,height), full pan.
              ;; Normalized to the same coord system as x,y,width,height
              dw (- width w')
              dh (- height h')
              [pdx pdy] (if point
                          [(* (/ (- (.-x point) x) width) dw)
                           (* (/ (- (.-y point) y) height) dh)]
                          [(/ dw 2.0)
                           (/ dh 2.0)])
              x' (+ x pdx)
              y' (+ y pdy)]
          (set-viewbox! svg x' y' w' h'))))))

(defn coords
  "Normalize a mouse event to the svg layout coord system (x,y,width,height)"
  [point svg]
  (let [from-screen (.inverse (.getScreenCTM svg))
        dom-point (js/DOMPoint.fromPoint point)]
    (.matrixTransform dom-point from-screen)))

(defn listen* [t f]
  (js/addEventListener t f #js{:passive false}))

(defn explore-listen [svg]
  (listen* "wheel"
           (fn [ev]
             (.preventDefault ev)
             (let [touchpad? (if (.-wheelDeltaY ev)
                               (= (.-wheelDeltaY ev) (* -3 (.-deltaY ev)))
                               (zero? (.-deltaMode ev)))]
               (if (or (.-ctrlKey ev)
                       (not touchpad?))
                 (zoom-viewbox svg [(.-deltaX ev) (.-deltaY ev)] (coords ev svg))
                 (pan-viewbox ev svg)))))
  (listen* "pointermove"
           (fn [ev]
             (cond (or (.-altKey ev)
                       (not= 0 (.-buttons ev)))
                   (pan-viewbox ev svg)

                   (.-ctrlKey ev)
                   (zoom-viewbox svg (movement-svg ev svg) nil)))))

(defn compute-steps []
  (let [steps (.querySelectorAll svg "[data-step]")
        views (into []
                    (map (fn [step]
                           {:view (step-viewbox step)}))
                    steps)]
    (conj views (first views))))

(defn init! []
  (reset! computed-steps (compute-steps))
  (reset! world (nth @computed-steps 0))
  (apply-world @world)

  (comment
    "if you prefer to click through..."
    (events/listen svg "click"
                   #(alter-step (if (< 512 (.-clientX %)) inc dec))))

  (when-let [[_ step] (re-find #"^#step(\d+)" js/document.location.hash)]
    (js/requestAnimationFrame #(set-step (js/parseInt step))))

  (explore-listen svg)

  (listen* "keydown"
           (fn [e]
             (prn "KEYDOWN" (.-key e))
             (condp = (.-key e)
               " " (do (.stopPropagation e)
                       (.preventDefault e)
                       (alter-step inc))
               "ArrowRight" (do (.stopPropagation e)
                                (.preventDefault e)
                                (alter-step inc))
               "ArrowLeft" (do (.stopPropagation e)
                               (.preventDefault e)
                               (alter-step dec))
               nil))))

(defonce started
  (init!))
