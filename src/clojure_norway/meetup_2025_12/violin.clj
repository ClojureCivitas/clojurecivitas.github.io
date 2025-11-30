(ns clojure-norway.meetup-2025-12.violin
  (:require [scicloj.kindly.v4.kind :as kind]
            [clojure.java.io :as io]
            [babashka.fs :as fs]))


;; ## Data source
;;
;; [violin tremolo G#2.aif](https://freesound.org/people/ldk1609/sounds/56085/)
;; by [ldk1609](https://freesound.org/people/ldk1609/)

;; We converted it from '.aiff' format to `.wav`
;; for familiarity:
;; ```bash
;; ffmpeg -i 56085__ldk1609__violin-tremolo-g2.aiff violin-tremolo-g2.wav
;; ```

(def violin-file-name
  "violin-tremolo-g2.wav")

(require '[babashka.fs :as fs])

(def violin-file-path
  (fs/file (fs/parent *file*)
           violin-file-name))

;; ## Listening to the file

(kind/audio
 {:src violin-file-name})

;; ## Reading the Wav file as data

;; [Reading WAV files](https://clojurecivitas.github.io/dsp/wav_files.html).

(import '(javax.sound.sampled AudioFileFormat
                              AudioInputStream
                              AudioSystem)
        '(java.io InputStream)
        '(java.nio ByteBuffer
                   ByteOrder))

(defn audio-format [^InputStream is]
  (let [file-format (AudioSystem/getAudioFileFormat is)
        format      (.getFormat file-format)]
    {:is-big-endian?   (.isBigEndian format)
     :channels         (.getChannels format)
     :sample-rate      (.getSampleRate format)
     :sample-size-bits (.getSampleSizeInBits format)
     :frame-length     (.getFrameLength file-format)
     :encoding         (str (.getEncoding format))}))

(require '[clojure.java.io :as io])

(def wav-format
  (with-open [wav-stream (io/input-stream violin-file-path)]
    (audio-format wav-stream)))
