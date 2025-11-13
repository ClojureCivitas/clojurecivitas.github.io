^{:kindly/hide-code true
  :clay {:title "DSP Study Group - Reading audio data from WAV-files"
         :quarto {:author [:daslu :onbreath]
                  :description "Exploring WAV-files for DSP in Clojure."
                  :category :clojure
                  :type :post
                  :date "2025-11-09"
                  :tags [:dsp :math :music]
                  :image "wav.png"
                  :draft true}}}
(ns dsp.wav-files
  (:require [scicloj.kindly.v4.kind :as kind]
            [clojure.java.io :as io]
            [tech.v3.datatype.functional :as dfn]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly])
  (:import (javax.sound.sampled AudioFileFormat
                                AudioInputStream
                                AudioSystem)
           (java.io InputStream)
           (java.nio ByteBuffer
                     ByteOrder)))

;; **Exploration from the [Scicloj DSP Study Group](https://scicloj.github.io/docs/community/groups/dsp-study/)**
;; *Second meeting - Nov. 08th 2025 and some follow-up investigation*

;; Welcome! These are notes from our second study group session, where
;; we're learning digital signal processing together using
;; Clojure. We're following the excellent book
;; [**Think DSP** by Allen B. Downey](https://greenteapress.com/wp/think-dsp/) (available free online).
;;
;; **Huge thanks to Professor Downey** for writing such an accessible and free introduction to DSP, and for sharing with us the work-in-progress notebooks of [Think DSP 2](https://allendowney.github.io/ThinkDSP2/index.html).

;; Along with this study group came the idea to have an online
;; creative coding festival around Clojure in the first months of
;; 2026. In this meeting we spent some time brainstorming on how that
;; might look and what the scope could be. The remaining time of the
;; session we looked into downloading and reading WAV-files in
;; Clojure.

;; ## Why WAV Files?
;;
;; The notebooks in Think DSP 2 work with WAV files loaded from GitHub
;; as a basis for further processing, so we need a way to load these
;; as well. After obtaining the file, we need to get at the audio data
;; it contains.

;; ## Simplified WAV Format

;; First, let's take a superficial look at what data WAV files
;; contain, before we dive into getting the data. A simple WAV file
;; consists of a header and pure audio data following it. There are
;; several iterations on specifications for the WAV format and the
;; format allows for quite some flexibility in placing different
;; metadata in the file, as well as different encodings.

^:kindly/hide-code
(kind/mermaid
 "---
config:
    theme: 'forest'
---

block
    columns 1
    block:wav
        columns 5
        block:HeaderId
            columns 1
            HeaderLabel[\"Header\"]
        end

        block:F1
            columns 1
            FrameLabel1[\"Frame\"]
        end

        block:F2
            columns 1
            FrameLabel2[\"Frame\"]
        end

        block:F3
            columns 1
            FrameLabel3[\"Frame\"]
        end

        block:FN
            columns 1
            FrameLabelN[\"...\"]
        end
    end")


;; The WAV (Waveform Audio File Format) file format is a
;; RIFF (Resource Interchange File Format) file which stores data in
;; **chunks**. Each **chunk** consists of a **tag** and **data**. Lets
;; consider a partial example, which corresponds to the way the WAV
;; file we want to read is arranged:

^:kindly/hide-code
(kind/mermaid
 "---
config:
    theme: 'forest'
---

block
    columns 1
    block:wav
        columns 3
        block:HeaderId
            columns 1
            HeaderLine1[\"RIFF\"]
            HeaderLine2[\"WAVE\"]
        end

        block:HeaderId2
            columns 1
            HeaderLine3[\"fmt \"]
            HeaderLine4[\"1\"]
            HeaderLine5[\"44100\"]
            HeaderLine6[\"16\"]
        end

        block:data
            columns 1
            DataLabel[\"data\"]
            ChanF1[\"ch0\"]
            ChanF2[\"ch0\"]
            ChanF2[\"ch0\"]
            ChanF3[\"ch0\"]
            ChanFN[\"...\"]
        end
    end")

;; The header comprises of the **tag** `RIFF`, its **chunk** tagged
;; with the specific format `WAVE` and a **subchunk** `fmt `, which
;; describes the contained audio data.  This represents some of the
;; header information in a WAV file with a single, 16-bit mono sound
;; channel and 44.100 samples per second.

;; As we learned in the [first session](https://clojurecivitas.github.io/dsp/intro.html)
;; of the DSP study group:
;; > Sound waves are continuous vibrations in the air. To work with them on a computer,
;; > we need to **sample** them - take measurements at regular intervals. The **sample rate**
;; > tells us how many measurements per second. CD-quality audio uses 44,100 samples per second.

;; These **samples** are stored in the WAV files `data` tagged
;; **subchunk**. Since this is mono sound, there is one **frame** with
;; one **channel** per **sample**. For multiple **channels**, each
;; **frame** consists of all channels and their respective **sample**.

;; ## Libraries We're Using
;;
;; - **[Kindly](https://scicloj.github.io/kindly-noted/kindly)** - Visualization protocol that renders our data as interactive HTML elements (through Clay)
;; - **[Kindly](https://scicloj.github.io/kindly-noted/kindly)** - Visualization protocol that renders our data as interactive HTML elements (through Clay)
;; - **[dtype-next](https://github.com/cnuernber/dtype-next)** - Efficient numerical arrays and vectorized operations (like NumPy for Clojure)
;; - **[Tablecloth](https://scicloj.github.io/tablecloth/)** - DataFrame library for data manipulation and transformation
;; - **[Tableplot](https://scicloj.github.io/tableplot/)** - Declarative plotting library built on Plotly
;; - **[javax.sound.sampled](https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/sound/sampled/package-summary.html)** - Some classes from the Java standard libraries sound package to read WAV Files.

(require '[scicloj.kindly.v4.kind :as kind]
         '[clojure.java.io :as io]
         '[tech.v3.datatype.functional :as dfn]
         '[tablecloth.api :as tc]
         '[scicloj.tableplot.v1.plotly :as plotly])
^:kindly/hide-code
(kind/code
 "(import '(javax.sound.sampled AudioFileFormat
                              AudioInputStream
                              AudioSystem)
        '(java.io InputStream)
        '(java.nio ByteBuffer
                   ByteOrder))")


;; ## Downloading a WAV File
(defn copy [uri file]
  (with-open [in (io/input-stream uri)
              out (io/output-stream file)]
    (io/copy in out)))

^:kindly/hide-code
(def tuning-fork-file
  "18871__zippi1__sound-bell-440hz.wav")

^:kindly/hide-code
(def tuning-fork-url
  (str "https://github.com/AllenDowney/ThinkDSP/raw/master/code/" tuning-fork-file))

^:kindly/hide-code
(def tuning-fork-file
  "18871__zippi1__sound-bell-440hz.wav")

^:kindly/hide-code
(def tuning-fork-file-compressed
  "18871__zippi1__sound-bell-440hz-compressed.wav")

^:kindly/hide-code
(def tuning-fork-path
  (str "src/dsp/" tuning-fork-file))

^:kindly/hide-code
(def tuning-fork-path-compressed
  (str "src/dsp/" tuning-fork-file-compressed))

(copy tuning-fork-url tuning-fork-path)

;; ## Playing a WAV File
;;
;; Kindly can embed a player with a URL, but the sample is extremely
;; loud (it is a tuning fork struck in front of a microphone), so we
;; don't embed this player.
^:kindly/hide-code
(kind/code "(kind/audio {:src tuning-fork-url})")

;; Here we use a compressed and loudness normalized version of the
;; original file, so you can safely listen to it.
(kind/audio {:src tuning-fork-file-compressed})

;; ## Reading Metadata from the WAV File
;;
;; We define a function to collect some metadata from the file.
(defn audio-format [^InputStream is]
  (let [file-format (AudioSystem/getAudioFileFormat is)
        format      (.getFormat file-format)]
    {:is-big-endian?   (.isBigEndian format)
     :channels         (.getChannels format)
     :sample-rate      (.getSampleRate format)
     :sample-size-bits (.getSampleSizeInBits format)
     :frame-length     (.getFrameLength file-format)
     :encoding         (str (.getEncoding format))}))

(with-open [wav-stream (io/input-stream tuning-fork-path)]
  (def wav-format
    (audio-format wav-stream)))

wav-format

;; `:is-big-endian?` specifies the byte order of audio data with more
;; than 8 `:sample-size-bits`. `:sample-size-bits` is the number of
;; bits comprising a sample. The `:frame-length` is the total amount
;; of frames contained in the audio data.

;; We don't use much of that information for now, but it'll let us
;; peek at what kind of WAV file we're working with in the future and
;; we can use the information to extend our function for extracting
;; audio data, which we define next.

;; ## Reading Audio Data from the WAV File
;;
;; The bulk of work here is handled by the ``AudionInputStream``, but
;; since it only reads bytes for us, we have to put these together
;; into the correct datatype for each frame manually. For now we just
;; put the data for 16-bit mono WAV files into a short-array.
(defn audio-data [^InputStream is]
  (let [{:keys [frame-length]} (audio-format is)
        format                 (-> (AudioSystem/getAudioFileFormat is)
                                   AudioFileFormat/.getFormat)
        ^bytes audio-bytes     (with-open [ais (AudioInputStream. is format frame-length)]
                                 (AudioInputStream/.readAllBytes ais))
        audio-shorts           (short-array frame-length)
        bb                     (ByteBuffer/allocate 2)]
    (dotimes [i frame-length]
      (ByteBuffer/.clear bb)
      (.order bb ByteOrder/LITTLE_ENDIAN)
      (.put bb ^byte (aget audio-bytes (* 2 i)))
      (.put bb ^byte (aget audio-bytes (inc (* 2 i))))
      (aset-short audio-shorts i (.getShort bb 0)))
    audio-shorts))

(with-open [wav-stream (io/input-stream tuning-fork-path)]
  (def wav-shorts
    (audio-data wav-stream)))

;; The difference between the WAV file bytes and the audio data we
;; read is 44 bytes, which is the size of the default header and
;; container.
(with-open [wav-stream (io/input-stream tuning-fork-path)]
  (- (count (.readAllBytes wav-stream))
     (* 2 (count wav-shorts))))

;; ## Striking the Fork
;;
;; Now that we have read the data we can reduce its amplitude, so we
;; can listen to it safely.
^kind/audio
{:samples (dfn// wav-shorts 4000000.0)
 :sample-rate (:sample-rate wav-format)}

;; In fact, the function `audio-data` above is quite similar to how [Clay](https://github.com/scicloj/clay/blob/main/src/scicloj/clay/v2/item.clj#L420) writes the audio data to a file for us to listen to in the browser, just the reverse of what we did for reading.

;; ## Visualizing Waves
;;
;; Let's take a look at the sound of a tuning fork.
(let [{:keys [frame-length sample-rate]} wav-format]
  (-> {:time (dfn// (range frame-length)
                    sample-rate)
       :value wav-shorts}
      tc/dataset
      (plotly/layer-line {:=x :time
                          :=y :value})))

;; ## What we learned
;;
;; In the second session and some pairing beyond we prepared for our
;; forthcoming sessions on Think DSP by:
;; - **WAV file format** - Learning about the structure of simple WAV files
;; - **File download** - Downloading files with Java
;; - **WAV file metadata** - Reading metadata of a WAV file
;; - **WAV file audio data** - Reading the bytes in the audio data container and converting them to an appropriate data type
;;
;; ## Next Steps
;;
;; In our next study group meetings, we'll explore the book step by step, and learn more about sounds and signals,
;; harmonics and the Forier transform, non-periodic signals and spectograms, noise and filtering, and more.
;;
;; Join us at the [Scicloj DSP Study Group](https://scicloj.github.io/docs/community/groups/dsp-study/)!
;;
;; ---
;;
;; *Again, huge thanks to Allen B. Downey for Think DSP. If you find this resource valuable,
;; consider [supporting his work](https://greenteapress.com/wp/) or sharing it with others.*
