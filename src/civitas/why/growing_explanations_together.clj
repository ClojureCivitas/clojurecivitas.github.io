^{:kindly/hide-code true
  :clay {:title "Growing explanations together" :quarto {:author :com.github/teodorlu :draft true :type :post :date "2025-11-01"}}}
(ns civitas.why.growing-explanations-together
  (:require [clojure.edn]
            [scicloj.kindly.v4.kind :as kind]))

;; _reflections from a personal learning journey_


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## You should make a little home for yourself on the web!

;; “Civitas is stupid, everyone should just create their own static site!”
;; I read this one time.
;; And I think it's wrong!
;; I say it's wrong with conviction because I used to belive it was right.
;;
;; But first, what's the truth in that statement?
;;
;; Our mother's milk as Clojurians is simplicity.
;; Do create simple systems!
;; Don't complect!
;; Don't pull lots of dependencies!
;; By building with simpler pieces, you learn more.
;; You avoid frontend hell where the browser must load 53 MB of compiled Javascript to say "Welcome!"
;; This is how you build a simple system.
;;
;; The web is a wonderful place, and I encourage you strongly to make a part of it your own!
;; I find it *completely amazing that I can type up some random shit about [the kind of things I like to learn], make a URL for that idea, and give it to a friend.
;; How wonderful is that!
;; And how *quick* is that!
;; Before the World Wide Web, Tim Berners Lee had to print a piece of paper and give it to his colleague.
;; Delivering more knowledge required more paper.
;;
;; [the kind of things I like to learn]: https://play.teod.eu/computing-learning-designing-researching/
;;
;; A simple web site is just a folder of files.
;; You don't have to run any programs to make that!
;; Just write an HTML file and publish.
;; If you want to avoid writing plain HTML, you are in control, you can write a program to make the HTML.
;; For me personally, [Babashka] my tool of choice for that.
;; I can write the HTML generation program in the programming language I prefer (Clojure), and regenerate my web site quickly without a running JVM.
;; Oh, the joy!
;; The experience with Babashka-powered knowledge on the web got me so excited I had to present [Build Your Own Little Memex with Babashka] on the first Babashka conference.
;; 🤗
;; Absolutely recommended, you should *totally* do that yourself.
;;
;; [Babashka]: https://babashka.org/
;; [Build Your Own Little Memex with Babashka]: https://play.teod.eu/build-your-own-little-memex-with-babasha/
;;
;; But.
;;
;; After having written, ... let me check, ...

(let [index (clojure.edn/read-string (slurp "https://play.teod.eu/index/big.edn"))]
  (kind/hiccup [:p "After having written " (count index) " documents, what have I gained?"]))

;; - I've explored what happens when I let myself have ideas
;; - I've learned to build my own knowledge system
;; - … and I've learned that this is *my* thing.
;;   My site is heavily personalized, and works great for me.
;;   For others?
;;   I woudn't recommend using it.
;;   Consider stealing a piece or two or fishing for some inspiration, but *please do make your own thing*.
;;
;; Has it helped me build knowledge?
;; For me, it's superb: I've learned to learn.
;; Write an explanation for myself, then I understand it.
;; The two explanations I'm most happy with are [Easy, explicit parallellism with pipeline-blocking] (collaboration with [Ruben Svealdson])
;; and [Rainbow tables: what they are, and why we salt passwords before hashing, explained with Clojure] (which I decided to move from my site to Clerk.Garden).
;;
;; [Easy, explicit parallellism with pipeline-blocking]: https://play.teod.eu/clojure-easy-parallellism-with-pipeline-blocking/
;; [Ruben Svealdson]: https://github.com/rubensseva
;; [Rainbow tables: what they are, and why we salt passwords before hashing, explained with Clojure]: https://github.clerk.garden/teodorlu/lab/commit/1cfe71b8bf1b34ecbcf2fd6d1119985b49eab74c/src/rainbow_tables_2/


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Simple, great for learning—but ultimately isolated.
;;
;; My personal web site is great for myself, but not not great for others.
;; An old-time Lisper [once wrote][Curse of Lisp] the following:
;;
;; > Lisp allows you to just chuck things off so easily, and it is easy to take
;; > this for granted. I saw this 10 years ago when looking for a GUI to my
;; > Lisp. No problem, there were 9 different offerings. The trouble was that
;; > none of the 9 were properly documented and none were bug free. Basically
;; > each person had implemented his own solution and it worked for him so that
;; > was fine. This is a BBM attitude; it works for me and I understand it. It
;; > is also the product of not needing or wanting anybody else's help to do
;; > something.
;;
;; That's me!
;; I made my own tiny world, lived in it, and it was awesome.
;; But I couldn't invite anybody else in.


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Solving for cohesion and curation
;;
;; You exist in a context.
;; People might be interested in what you have to say, and they're also going to be interested in what others have to say.
;; If you're Alan Kay or Bret Victor, you may be able to create a microworld and invite people in, but most people aren't Alan Kay or Bret Victor!
;; That means people are going to be experiencing your creations in a context of other people's creations.
;;
;; That means my tiny, little static web site is never going to be someone else's world.
;; Sure, a curious reader may look around prompted by interest, but the reader will finish that trail of curiosity and finally, do something else.
;;
;; To reach more with the explanations we create, we want to ensure cohesion with other people's explanations, and curate the very good explanations.
;;
;; The Clojure Civitas that Timothy Pratley has created hits the bullseye for that aim.
;;
;; Everyone writes in the same Git repository.
;; That means you can follow one Git log and catch every change, should you want to.
;; You can write prose and Clojure, and share it.


;; ## Explanation playlists is the key.
;;
;; Curation has not yet been visibly tackled (as I see it), but the foundation has been laid.
;; In my talk _Build Your Own Little Memex with Babashka_,
;; , I argued that we need [knowledge playlists][knowledge playlist].
;; Today, I prefer the term _explanation playlist_.
;; I define an explanation playlist as
;;
;; > an ordered list of contextualized explanations that can be consumed from start to end.
;;
;; [knowledge playlist]: https://play.teod.eu/knowledge-playlist/
;;
;; What's the right medium for an explanation playlist?
;; Something special, right?
;; No!
;; It's just another explanation.
;; It weaves other explanations together, letting you more easily orient yourself in the bigger picture.
;; It's just yet another hypertext document.
;;
;; Clojure Civitas can help us beat the [Curse of Lisp], and give Clojure the reach it deserves.
;;
;; [Curse of Lisp]: https://winestockwebdesign.com/Essays/Lisp_Curse.html


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Consider contributing!
;;
;; After being very pleasantly surprised by the Noj-powered tooling that drives Clojure Civitas, I'm excited to lean into Civitas.
;; My personal web site continues to be a place of exploratory play, but now I have a goal for Clojure content:
;; Put it on Civitas.
;; On Civitas it can outlive me, and help grow a commons of knowledge, instead of yet another isolated island.
