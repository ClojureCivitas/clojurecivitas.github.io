^:kindly/hide-code
^{:kindly/options {:html/deps [:scittle :reagent]}
  :clay {:title "Clojure Conj 2025 - Two Lies and a Truth Trivia"
         :quarto {:author [:burinc]
                  :description "Test your knowledge about Clojure Conj 2025! View photos from the conference and guess which statement is true. An interactive trivia game built with ClojureScript and Scittle."
                  :type :post
                  :date "2025-11-15"
                  :category :conferences
                  :image "media/conj-2025_0815.png"
                  :tags [:clojure-conj
                         :scittle
                         :clojurescript
                         :reagent
                         :trivia
                         :interactive
                         :conference
                         :no-build
                         :game]
                  :keywords [:clojure-conj-2025
                             :conference-photos
                             :trivia-game
                             :two-lies-one-truth
                             :interactive-slideshow
                             :reagent-atoms
                             :functional-programming
                             :browser-game]}}}

(ns scittle.conj-2025.trivia-slideshow
  (:require [scicloj.kindly.v4.kind :as kind]))

;; Welcome to an interactive trivia game based on photos from Clojure Conj 2025!
;;
;; ## How to Play
;;
;; - View each photo from the conference
;; - Read three statements about the photo or the event
;; - Two statements are FALSE (lies) and one is TRUE
;; - Click the statement you believe is TRUE
;; - Track your score as you progress through the slideshow
;; - See your final results at the end!
;;
;; ## The Game

^:kindly/hide-code
(kind/hiccup
 [:div {:style {:margin "2rem 0"
                :padding "2rem"
                :border "2px solid #e9ecef"
                :border-radius "8px"
                :background-color "#f8f9fa"}}
  [:div#trivia-app-root {:style {:min-height "600px"}}]
  [:script {:type "application/x-scittle"
            :src "trivia_slideshow.cljs"}]])

;; ## About the Conference
;;
;; Clojure Conj is the original Clojure conference, bringing together the global Clojure community
;; to share ideas, learn from each other, and celebrate the power of functional programming.
;;
;; The 2025 edition featured talks on cutting-edge topics, hands-on workshops, and plenty of
;; opportunities to connect with fellow Clojurians.
;;
;; ## 🎉 Call to Participation - Add Your Memories to This Game!
;;
;; **Were you at Clojure Conj 2025?** I'd love to have your photos and stories in this trivia game!
;; This is a wonderful opportunity to:
;;
;; - **Learn about contributing to Clojure Civitas** - Get hands-on experience with open source contribution
;; - **Share your conference experience** - Your unique perspective makes this project richer
;; - **Practice your ClojureScript skills** - The code structure is intentionally simple and approachable
;; - **Connect with the community** - Contributing is a great way to engage with fellow Clojurians
;;
;; ### How to Contribute (It's Easy!):
;;
;; 1. **Submit a photo or two** from the conference
;;    - Talks, speakers, hallway conversations, the venue, activities - anything that captures your experience!
;;
;; 2. **Create two LIES and one TRUTH** about your photo
;;    - Make it interesting and fun - think about what might surprise people!
;;
;; 3. **Look at the source code** - It's very simple!
;;    - Check out [trivia_slideshow.cljs](https://github.com/clojurecivitas/clojurecivitas.github.io/blob/main/src/scittle/conj_2025/trivia_slideshow.cljs)
;;    - The structure is straightforward - just maps with image paths and question data
;;
;; 4. **Create a PR** to the [Clojure Civitas repository](https://github.com/clojurecivitas/clojurecivitas.github.io)
;;    - First-time contributors very welcome!
;;    - We're here to help if you have questions
;;
;; ### Example Entry Structure:
;;
;; ```clojure
;; {:image "/scittle/conj_2025/media/your-photo.png"
;;  :question "What happened in this moment?"
;;  :options ["This is a false statement (lie #1)"
;;            "This is the TRUE statement!"
;;            "This is also false (lie #2)"]
;;  :correct-index 1  ;; index of the true statement
;;  :explanation "Here's why the truth is true!"}
;; ```
;;
;; ### A Small Apology...
;;
;; If we met at the conference and had wonderful conversations but didn't get a chance to snap a photo together -
;; I sincerely apologize! Sometimes we get so caught up in the moment, enjoying great discussions about Clojure,
;; functional programming, or life in general, that we completely forget to capture it with a photo.
;;
;; If that was us, please don't hesitate to reach out. I'd still love to include a note about our conversation
;; or perhaps we can connect at the next conference!
;;
;; ---
;;
;; ## Personal Reflections
;;
;; ### An Absolute Blast at Clojure Conj 2025!
;;
;; I had the incredible honor of both attending and speaking at Clojure Conj 2025, and honestly -
;; this was one of the most memorable conference experiences I've ever had.
;;
;; Huge, heartfelt thanks to the organizers for not only putting together such an outstanding event,
;; but also for inviting me to speak and share my work with this wonderful community. The care, thoughtfulness,
;; and dedication that went into every detail of the conference was truly remarkable. You created something special.
;;
;; ### The Real Magic: Conversations and Connections
;;
;; While the talks were excellent - and there were SO many brilliant presentations covering everything from
;; cutting-edge language features to real-world applications - what I'll treasure most are the conversations.
;;
;; The **hallway conversations** were absolutely incredible. Those spontaneous discussions between sessions,
;; during coffee breaks, over meals, and at evening events - that's where the real magic happened:
;;
;; - Deep technical dives into functional programming patterns and Clojure internals
;; - Inspiring exchanges about the future of the language and ecosystem
;; - Finally meeting in person people I've only known through GitHub, Slack, and Clojurists Together
;; - Making genuine friendships that I know will last well beyond the conference
;; - Learning about the amazing projects people are building - from startups to enterprise applications
;; - Sharing war stories, debugging tales, and those "aha!" moments we've all experienced with Clojure
;;
;; The Clojure community is special. People are welcoming, intellectually curious, thoughtful, and kind.
;; There's a genuine warmth and openness that you don't always find in tech communities.
;;
;; ### To Anyone Thinking About Attending
;;
;; If you're reading this and you've never been to Clojure Conj - or any Clojure conference -
;; I cannot encourage you enough to attend next year!
;;
;; It doesn't matter if you're:
;;
;; - A Clojure beginner just getting started with the language
;; - An experienced developer looking to deepen your expertise
;; - Someone curious about functional programming but not yet committed
;; - Interested in what makes this community tick
;; - Working with Clojure professionally or just as a hobby
;;
;; You'll find a place for yourself here. The community is incredibly welcoming to newcomers and veterans alike.
;; You'll learn, grow, make connections, and probably have way more fun than you expected.
;;
;; The combination of world-class technical content, hands-on workshops, and the opportunity to connect
;; with passionate, smart, and friendly people makes it worth every minute.
;;
;; I traveled all the way from Sydney, Australia - a journey that took me through Los Angeles with a layover
;; before finally arriving in Charlotte, NC. Yes, it's literally the opposite side of the world. But you know what?
;; It was absolutely worth it. Every single mile. The connections I made, the knowledge I gained, and the
;; experience of being part of this community in person made the long journey completely worthwhile.
;;
;; — Burin Choomnuan
