(ns dsp.seeing-red
  (:require [com.phronemophobic.clj-media :as clj-media]
            [com.phronemophobic.clj-media.model :as clj-media.model]
            [scicloj.kindly.v4.kind :as kind]
            [tech.v3.libs.buffered-image :as bufimg]
            [tech.v3.datatype :as dtype]
            [tech.v3.tensor :as tensor]
            [tech.v3.datatype.functional :as dfn]
            [tech.v3.dataset.tensor :as ds-tensor]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]))

^:kindly/hide-code
(kind/hiccup
 [:style
  ".clay-dataset {
  max-height:400px; 
  overflow-y: auto;
}
.printedClojure {
  max-height:400px; 
  overflow-y: auto;
}
"])

;; ## Example video

;; We keep one video for example at the Civitas repo:

(kind/video
 {:src "seeing-red-00000-7254825.mp4"})

;; ## Reading a video file

;; For the full analysis, we assume you have downloaded all videoas and saved them 

(def video-path
  "/workspace/datasets/seeing-red/videos/00000/7254825.mp4")

;; Let us explore it with clj-media:

(clj-media/probe video-path)

;; ## Converting the video to tensor structures

;; Using clj-media, we can reduce over frames:

(clj-media/frames
 (clj-media/file video-path)
 :video
 {:format (clj-media/video-format
           {:pixel-format
            :pixel-format/rgba})})

;; For example, let us extract the first
;; frame and convert it to an image:

(def first-image
  (first
   (into []
         (comp (take 1)
               (map clj-media.model/image))
         (clj-media/frames
          (clj-media/file video-path)
          :video
          {:format (clj-media/video-format
                    {:pixel-format
                     :pixel-format/rgba})}))))

(type first-image)

first-image

;; When converting to a tensor, we have the four
;; color components of `rgba` format:

(def first-tensor
  (bufimg/as-ubyte-tensor first-image))

(type first-tensor)

(dtype/shape first-tensor)

first-tensor

;; Let us view the 100th row of pixels as a table (flattening the height and width as one series).

(let [[height width] (take 2 (dtype/shape first-tensor))]
  (-> first-tensor
      (tensor/slice 1)
      (nth 100)
      ds-tensor/tensor->dataset
      (tc/rename-columns [:a :r :g :b])))

;; Let us plot the components

(let [[height width] (take 2 (dtype/shape first-tensor))]
  (-> first-tensor
      (tensor/slice 1)
      first
      ds-tensor/tensor->dataset
      (tc/rename-columns [:a :r :g :b])
      (tc/add-column :x (range))
      (plotly/base {:=mark-opacity 0.8
                    :=mark-size 5})
      (plotly/layer-line {:=y :r :=name "r" :=mark-color "red"})
      (plotly/layer-line {:=y :g :=name "g" :=mark-color "green"})
      (plotly/layer-line {:=y :b :=name "b" :=mark-color "blue"})
      (plotly/layer-line {:=y :a :=name "a" :=mark-color "black"})))


