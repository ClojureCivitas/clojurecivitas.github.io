(ns scittle.conj-2025.trivia-slideshow
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn merge-styles
  "Safely merges multiple style maps"
  [& styles]
  (apply merge (filter map? styles)))

;; ============================================================================
;; Trivia Data
;; ============================================================================

(def trivia-slides
  "Collection of trivia questions with images from Clojure Conj 2025"
  [{:image "/scittle/conj_2025/media/conj-2025_0735.png"
    :question "About this conference venue..."
    :options ["The conference was held in a converted warehouse in Austin"
              "The conference was held in Charlotte, North Carolina"
              "The conference was held at a beach resort in Miami"]
    :correct-index 1
    :explanation "Clojure Conj 2025 took place in Charlotte, North Carolina"}

   {:image "/scittle/conj_2025/media/conj-2025_0739.png"
    :question "About the conference badge..."
    :options ["Badges were written in Clojure code"
              "Attendees received personalized name badges"
              "Everyone had to wear anonymous number badges"]
    :correct-index 1
    :explanation "Personal name badges help foster connections and community!"}

   {:image "/scittle/conj_2025/media/conj-2025_0740.png"
    :question "About conference registration..."
    :options ["Registration happened only on the first day morning"
              "You could register online before the event"
              "Registration required solving a Clojure coding challenge"]
    :correct-index 1
    :explanation "Online registration makes attending conferences convenient!"}

   {:image "/scittle/conj_2025/media/conj-2025_0747.png"
    :question "About the attendees..."
    :options ["Over 1,000 developers attended the conference"
              "Attendees came from over 20 different countries"
              "Every attendee received a free MacBook Pro"]
    :correct-index 1
    :explanation "Clojure Conj attracts a diverse international crowd!"}

   {:image "/scittle/conj_2025/media/conj-2025_0749.png"
    :question "About the conference schedule..."
    :options ["The schedule was revealed only on the day of the conference"
              "The schedule was published in advance online"
              "Every session started at random times"]
    :correct-index 1
    :explanation "Having the schedule in advance helps attendees plan!"}

   {:image "/scittle/conj_2025/media/conj-2025_0750.png"
    :question "About the venue facilities..."
    :options ["The venue had multiple rooms for concurrent sessions"
              "All sessions happened in a single giant auditorium"
              "Sessions were held outdoors in a park"]
    :correct-index 0
    :explanation "Multiple tracks allow attendees to choose sessions!"}

   {:image "/scittle/conj_2025/media/conj-2025_0751.png"
    :question "About session recordings..."
    :options ["All sessions were recorded for later viewing"
              "Photography and recording were strictly forbidden"
              "Only keynotes were recorded"]
    :correct-index 0
    :explanation "Session recordings help spread knowledge!"}

   {:image "/scittle/conj_2025/media/conj-2025_0753.png"
    :question "About coffee breaks..."
    :options ["Coffee and refreshments were available throughout the day"
              "Attendees had to leave the venue for coffee"
              "Only water was provided"]
    :correct-index 0
    :explanation "Coffee breaks are essential for networking!"}

   {:image "/scittle/conj_2025/media/conj-2025_0756.png"
    :question "About speaker diversity..."
    :options ["Only men were allowed to speak"
              "The conference welcomed speakers of all backgrounds"
              "Speakers had to be certified Clojure experts"]
    :correct-index 1
    :explanation "Diverse perspectives make conferences richer!"}

   {:image "/scittle/conj_2025/media/conj-2025_0758.png"
    :question "About technical level..."
    :options ["Sessions ranged from beginner to advanced topics"
              "All sessions were only for expert developers"
              "Content was exclusively for complete beginners"]
    :correct-index 0
    :explanation "A mix of difficulty levels ensures everyone learns!"}

   {:image "/scittle/conj_2025/media/conj-2025_0759.png"
    :question "About audience interaction..."
    :options ["Audience members were encouraged to engage and ask questions"
              "Silence was mandatory during all sessions"
              "Interaction was only allowed via written notes"]
    :correct-index 0
    :explanation "Active participation enhances learning for everyone!"}

   {:image "/scittle/conj_2025/media/conj-2025_0760.png"
    :question "About networking opportunities..."
    :options ["Attendees communicated only through REPL sessions"
              "There were dedicated networking breaks and social events"
              "Networking was forbidden by conference rules"]
    :correct-index 1
    :explanation "Clojure Conj provides great networking opportunities!"}

   {:image "/scittle/conj_2025/media/conj-2025_0761.png"
    :question "About the code of conduct..."
    :options ["There was no code of conduct"
              "The conference had a code of conduct to ensure a welcoming environment"
              "The code of conduct was written entirely in Clojure"]
    :correct-index 1
    :explanation "A code of conduct helps create a safe environment!"}

   {:image "/scittle/conj_2025/media/conj-2025_0762.png"
    :question "About session length..."
    :options ["All sessions were exactly 3 hours long"
              "Sessions varied in length (talks, workshops, etc.)"
              "Every session was limited to 5 minutes"]
    :correct-index 1
    :explanation "Different formats accommodate different content types!"}

   {:image "/scittle/conj_2025/media/conj-2025_0764.png"
    :question "About hands-on learning..."
    :options ["The conference included interactive workshops"
              "All learning was purely theoretical"
              "Laptops were banned from the venue"]
    :correct-index 0
    :explanation "Hands-on workshops provide practical experience!"}

   {:image "/scittle/conj_2025/media/conj-2025_0766.png"
    :question "About conference presentations..."
    :options ["All presentations used only live coding, no slides"
              "Speakers used slides, demos, and various presentation formats"
              "Presentations were delivered via interpretive dance"]
    :correct-index 1
    :explanation "Speakers use diverse presentation styles!"}

   {:image "/scittle/conj_2025/media/conj-2025_0769.png"
    :question "About the keynote speakers..."
    :options ["There were no keynote presentations"
              "Featured keynote speakers kicked off each day"
              "Keynotes were only for sponsors"]
    :correct-index 1
    :explanation "Keynote speakers deliver inspiring talks!"}

   {:image "/scittle/conj_2025/media/conj-2025_0774.png"
    :question "About conference sponsors..."
    :options ["Sponsors help make the conference possible"
              "The conference had zero sponsors"
              "Sponsors were not acknowledged in any way"]
    :correct-index 0
    :explanation "Sponsors provide crucial support!"}

   {:image "/scittle/conj_2025/media/conj-2025_0782.png"
    :question "About conference accessibility..."
    :options ["The venue was chosen with accessibility in mind"
              "Accessibility was not considered"
              "Only the parking lot was accessible"]
    :correct-index 0
    :explanation "Ensuring accessibility is an important priority!"}

   {:image "/scittle/conj_2025/media/conj-2025_0783.png"
    :question "About note-taking..."
    :options ["Attendees were free to take notes on laptops or paper"
              "Note-taking was forbidden for security reasons"
              "Notes could only be taken in Clojure code"]
    :correct-index 0
    :explanation "Taking notes helps attendees retain knowledge!"}

   {:image "/scittle/conj_2025/media/conj-2025_0784.png"
    :question "About the expo hall..."
    :options ["Companies showcased their products and services"
              "There was no expo or vendor area"
              "The expo only sold conference merchandise"]
    :correct-index 0
    :explanation "Expo halls provide opportunities to learn about tools!"}

   {:image "/scittle/conj_2025/media/conj-2025_0787.png"
    :question "About panel discussions..."
    :options ["Panel discussions featured multiple experts discussing topics"
              "Panels were banned from the conference"
              "Only solo presentations were allowed"]
    :correct-index 0
    :explanation "Panel discussions offer diverse perspectives!"}

   {:image "/scittle/conj_2025/media/conj-2025_0793.png"
    :question "About social events..."
    :options ["Evening social events helped attendees connect informally"
              "Socializing was discouraged after sessions"
              "All social events were virtual only"]
    :correct-index 0
    :explanation "Social events are where great connections are made!"}

   {:image "/scittle/conj_2025/media/conj-2025_0794.png"
    :question "About conference badges..."
    :options ["Badges helped identify attendees, speakers, and organizers"
              "Everyone wore identical unmarked badges"
              "Badges were considered old-fashioned and not used"]
    :correct-index 0
    :explanation "Different badge types help identify roles!"}

   {:image "/scittle/conj_2025/media/conj-2025_0796.png"
    :question "About live streaming..."
    :options ["Some sessions may have been live streamed"
              "Live streaming was prohibited"
              "Every session was available only via live stream"]
    :correct-index 0
    :explanation "Live streaming extends reach to remote attendees!"}

   {:image "/scittle/conj_2025/media/conj-2025_0804.png"
    :question "About session feedback..."
    :options ["Attendees could provide feedback on sessions"
              "Feedback was not collected"
              "Feedback could only be negative"]
    :correct-index 0
    :explanation "Session feedback helps improve future conferences!"}

   {:image "/scittle/conj_2025/media/conj-2025_0805.png"
    :question "About the speaker lineup..."
    :options ["Only Rich Hickey was allowed to speak"
              "The conference featured talks from community members and experts"
              "All talks were pre-recorded videos from the 1990s"]
    :correct-index 1
    :explanation "Clojure Conj showcases diverse voices!"}

   {:image "/scittle/conj_2025/media/conj-2025_0810.png"
    :question "About the conference duration..."
    :options ["The conference lasted one full day"
              "The conference spanned multiple days"
              "The conference was exactly 24 hours non-stop"]
    :correct-index 1
    :explanation "Clojure Conj is a multi-day event!"}

   {:image "/scittle/conj_2025/media/conj-2025_0812.png"
    :question "About the opening ceremony..."
    :options ["The conference began with an opening ceremony and welcome"
              "Sessions started immediately with no introduction"
              "Opening ceremonies lasted 6 hours"]
    :correct-index 0
    :explanation "Opening ceremonies set the tone!"}

   {:image "/scittle/conj_2025/media/conj-2025_0815.png"
    :question "About the closing ceremony..."
    :options ["The conference ended with closing remarks and thanks"
              "The conference just stopped with no conclusion"
              "Closing ceremonies were mandatory 3-hour events"]
    :correct-index 0
    :explanation "Closing ceremonies celebrate achievements!"}

   {:image "/scittle/conj_2025/media/conj-2025_0819.png"
    :question "About future conferences..."
    :options ["Conference feedback helps shape future events"
              "Future conferences are never discussed"
              "Every conference is planned in complete isolation"]
    :correct-index 0
    :explanation "Community input makes each conference better!"}

   {:image "/scittle/conj_2025/media/conj-2025_0821.png"
    :question "About the conference venue location..."
    :options ["The venue was conveniently located with nearby hotels"
              "The venue was on a remote island with no access"
              "Location was kept secret until the day before"]
    :correct-index 0
    :explanation "Convenient locations make travel easier!"}

   {:image "/scittle/conj_2025/media/conj-2025_0822.png"
    :question "About session timing..."
    :options ["Sessions were timed to prevent running over schedule"
              "Every session could last as long as the speaker wanted"
              "All sessions ended randomly mid-sentence"]
    :correct-index 0
    :explanation "Keeping to schedule ensures attendees can plan their day!"}

   {:image "/scittle/conj_2025/media/conj-2025_0823.png"
    :question "About conference size..."
    :options ["The conference size balanced intimacy with diversity"
              "Over 50,000 people attended"
              "Only 3 people total attended"]
    :correct-index 0
    :explanation "Conference size affects the experience!"}

   {:image "/scittle/conj_2025/media/conj-2025_0824.png"
    :question "About industry representation..."
    :options ["Attendees came from various industries using Clojure"
              "Only one specific industry was represented"
              "Industry professionals were not welcome"]
    :correct-index 0
    :explanation "Seeing how Clojure is used across industries provides insights!"}

   {:image "/scittle/conj_2025/media/conj-2025_0826.png"
    :question "About collaboration..."
    :options ["The conference encouraged collaboration and knowledge sharing"
              "Collaboration was strictly forbidden"
              "Attendees competed in elimination rounds"]
    :correct-index 0
    :explanation "Collaboration is a core value!"}

   {:image "/scittle/conj_2025/media/conj-2025_0832.png"
    :question "About inclusivity..."
    :options ["The conference aimed to be inclusive and welcoming to all"
              "Only certain people were allowed to attend"
              "Inclusivity was not considered important"]
    :correct-index 0
    :explanation "An inclusive environment benefits everyone!"}

   {:image "/scittle/conj_2025/media/conj-2025_0834.png"
    :question "About community building..."
    :options ["The conference helped strengthen the Clojure community"
              "Community building was discouraged"
              "Everyone worked in isolation"]
    :correct-index 0
    :explanation "Conferences are essential for building communities!"}

   {:image "/scittle/conj_2025/media/conj-2025_0835.png"
    :question "About live demos..."
    :options ["Demos could only be pre-recorded videos"
              "Speakers often included live coding and demonstrations"
              "Demonstrations were not allowed for safety reasons"]
    :correct-index 1
    :explanation "Live coding and demos are thrilling!"}

   {:image "/scittle/conj_2025/media/conj-2025_0837.png"
    :question "About technical difficulties..."
    :options ["Backup plans existed for technical issues"
              "Technical problems resulted in immediate cancellation"
              "Technology never has problems"]
    :correct-index 0
    :explanation "Good planning includes backup plans!"}

   {:image "/scittle/conj_2025/media/conj-2025_0838.png"
    :question "About international attendees..."
    :options ["International attendees were welcomed from around the world"
              "Only local residents could attend"
              "International travel was prohibited"]
    :correct-index 0
    :explanation "International diversity enriches conferences!"}

   {:image "/scittle/conj_2025/media/conj-2025_0857.png"
    :question "About Q&A sessions..."
    :options ["Questions had to be submitted in writing 2 weeks in advance"
              "Audience members could ask questions after presentations"
              "Questions were forbidden to avoid controversy"]
    :correct-index 1
    :explanation "Q&A sessions allow deeper exploration!"}

   {:image "/scittle/conj_2025/media/conj-2025_0858.png"
    :question "About conference energy..."
    :options ["Conferences generate excitement and energy in the community"
              "Low energy is the goal"
              "Energy drinks were banned"]
    :correct-index 0
    :explanation "The energy at conferences is contagious!"}

   {:image "/scittle/conj_2025/media/conj-2025_0859.png"
    :question "About post-conference connections..."
    :options ["Many attendees stay connected after the conference"
              "All connections end immediately when the conference ends"
              "Staying in touch was against the rules"]
    :correct-index 0
    :explanation "Conferences spark lasting professional relationships!"}

   {:image "/scittle/conj_2025/media/conj-2025_0860.png"
    :question "About the conference community..."
    :options ["Attendees compete against each other for prizes"
              "The Clojure community is known for being welcoming and collaborative"
              "Networking is discouraged to maintain focus"]
    :correct-index 1
    :explanation "The Clojure community is renowned for its friendly spirit!"}

   ;; New questions for additional photos
   {:image "/scittle/conj_2025/media/conj-2025_9001.png"
    :question "About hallway conversations..."
    :options ["Hallway conversations are often where the best insights happen"
              "Talking in hallways was prohibited to prevent noise"
              "Hallways were reserved only for sponsors"]
    :correct-index 0
    :explanation "Some say the best content at conferences happens in hallway conversations!"}

   {:image "/scittle/conj_2025/media/conj-2025_9002.png"
    :question "About conference meals..."
    :options ["All attendees had to bring their own food"
              "Meals and snacks were provided for attendees"
              "Food was only available to VIP ticket holders"]
    :correct-index 1
    :explanation "Providing meals helps attendees focus on learning and networking!"}

   {:image "/scittle/conj_2025/media/conj-2025_9003.png"
    :question "About speaker preparation..."
    :options ["Speakers were given resources and support to prepare great talks"
              "Speakers had to improvise everything on stage"
              "Only slides created in Emacs were permitted"]
    :correct-index 0
    :explanation "Conference organizers help speakers deliver their best content!"}

   {:image "/scittle/conj_2025/media/conj-2025_9004.png"
    :question "About conference swag..."
    :options ["Conference merchandise and swag helped build community spirit"
              "Swag bags were illegal at this conference"
              "Only speakers received any conference items"]
    :correct-index 0
    :explanation "Conference swag is a fun way to remember the event!"}

   {:image "/scittle/conj_2025/media/conj-2025_9005.png"
    :question "About conference photos..."
    :options ["Attendees were encouraged to share photos and memories"
              "All photography was banned for privacy reasons"
              "Photos could only be taken in black and white"]
    :correct-index 0
    :explanation "Sharing photos helps spread the excitement and builds community!"}

   {:image "/scittle/conj_2025/media/conj-2025_9006.png"
    :question "About learning outcomes..."
    :options ["Attendees leave with new knowledge, connections, and inspiration"
              "The goal was to confuse everyone as much as possible"
              "Learning was discouraged in favor of entertainment only"]
    :correct-index 0
    :explanation "Great conferences transform attendees through learning and connection!"}

   {:image "/scittle/conj_2025/media/conj-2025_9007.png"
    :question "About big announcements at the Conj..."
    :options ["Clojure 2.0 is coming in 2026"
              "Datomic will now use SQL syntax"
              "A documentary about Clojure is in the works"]
    :correct-index 2
    :explanation "It's important to tell the story of this wonderful ecosystem and community!"}])

;; ============================================================================
;; Game State
;; ============================================================================

(def game-state
  (r/atom {:mode nil ; :game or :slideshow - starts with mode selection
           :current-slide 0
           :answers []
           :score 0
           :game-complete? false
           :show-result nil}))

;; ============================================================================
;; Game Logic
;; ============================================================================

(defn total-slides []
  (count trivia-slides))

(defn current-slide-data []
  (nth trivia-slides (:current-slide @game-state)))

(defn handle-answer
  "Handles when user selects an answer"
  [option-index]
  (when (nil? (:show-result @game-state))
    (let [{:keys [correct-index]} (current-slide-data)
          is-correct? (= option-index correct-index)
          current-idx (:current-slide @game-state)]
      (swap! game-state update :answers
             (fn [answers]
               (let [padded (vec (concat answers (repeat (- (inc current-idx) (count answers)) nil)))]
                 (assoc padded current-idx option-index))))
      (when is-correct?
        (swap! game-state update :score inc))
      (swap! game-state assoc :show-result (if is-correct? :correct :incorrect)))))

(defn next-slide
  "Moves to next slide or completes game"
  []
  (let [next-idx (inc (:current-slide @game-state))]
    (if (>= next-idx (total-slides))
      (swap! game-state assoc :game-complete? true)
      (swap! game-state assoc
             :current-slide next-idx
             :show-result nil))))

(defn previous-slide
  "Moves to previous slide"
  []
  (when (> (:current-slide @game-state) 0)
    (swap! game-state assoc
           :current-slide (dec (:current-slide @game-state))
           :show-result nil)))

(defn restart-game
  "Restarts the trivia game"
  []
  (reset! game-state {:mode :game
                      :current-slide 0
                      :answers []
                      :score 0
                      :game-complete? false
                      :show-result nil}))

(defn start-slideshow
  "Starts slideshow mode"
  []
  (reset! game-state {:mode :slideshow
                      :current-slide 0
                      :answers []
                      :score 0
                      :game-complete? false
                      :show-result nil}))

(defn back-to-menu
  "Returns to mode selection menu"
  []
  (reset! game-state {:mode nil
                      :current-slide 0
                      :answers []
                      :score 0
                      :game-complete? false
                      :show-result nil}))

(defn get-answer-for-slide
  "Gets the user's answer for a specific slide"
  [slide-idx]
  (get (:answers @game-state) slide-idx))

;; ============================================================================
;; UI Components
;; ============================================================================

(defn image-display
  "Displays the conference photo"
  [image-path]
  [:div {:style {:flex "1"
                 :display "flex"
                 :align-items "center"
                 :justify-content "center"
                 :padding "20px"}}
   [:img {:src image-path
          :alt "Conference photo"
          :style {:max-width "100%"
                  :max-height "500px"
                  :border-radius "12px"
                  :box-shadow "0 4px 12px rgba(0,0,0,0.15)"
                  :object-fit "contain"
                  :image-orientation "from-image"}}]])

(defn mode-selection
  "Mode selection screen - choose between game or slideshow"
  []
  [:div {:style {:padding "40px"
                 :max-width "800px"
                 :margin "0 auto"
                 :text-align "center"}}
   [:h1 {:style {:color "#4caf50"
                 :font-size "42px"
                 :margin-bottom "20px"}}
    "🎯 Clojure Conj 2025"]

   [:p {:style {:font-size "18px"
                :color "#666"
                :margin-bottom "40px"
                :line-height "1.6"}}
    "Choose how you'd like to explore the conference photos and memories"]

   [:div {:style {:display "flex"
                  :gap "20px"
                  :flex-wrap "wrap"
                  :justify-content "center"
                  :margin-top "30px"}}
    ;; Game Mode Card
    [:div {:style {:flex "1"
                   :min-width "280px"
                   :max-width "350px"
                   :background "white"
                   :border-radius "16px"
                   :padding "30px"
                   :box-shadow "0 4px 16px rgba(0,0,0,0.1)"
                   :transition "transform 0.2s ease, box-shadow 0.2s ease"
                   :cursor "pointer"}
           :on-mouse-enter #(do
                              (set! (.. % -target -style -transform) "translateY(-4px)")
                              (set! (.. % -target -style -boxShadow) "0 8px 24px rgba(76, 175, 80, 0.2)"))
           :on-mouse-leave #(do
                              (set! (.. % -target -style -transform) "translateY(0)")
                              (set! (.. % -target -style -boxShadow) "0 4px 16px rgba(0,0,0,0.1)"))
           :on-click restart-game}
     [:div {:style {:font-size "64px"
                    :margin-bottom "15px"}}
      "🎮"]
     [:h2 {:style {:color "#333"
                   :font-size "24px"
                   :margin-bottom "15px"}}
      "Game Mode"]
     [:p {:style {:color "#666"
                  :font-size "15px"
                  :line-height "1.6"
                  :margin-bottom "20px"}}
      "Test your knowledge with our interactive trivia game! Two lies and a truth - can you spot the truth?"]
     [:button {:style {:padding "12px 24px"
                       :background "#4caf50"
                       :color "white"
                       :border "none"
                       :border-radius "8px"
                       :font-size "16px"
                       :font-weight "600"
                       :cursor "pointer"
                       :width "100%"
                       :box-shadow "0 2px 8px rgba(76, 175, 80, 0.3)"
                       :transition "all 0.2s ease"}
               :on-mouse-enter #(set! (.. % -target -style -background) "#45a049")
               :on-mouse-leave #(set! (.. % -target -style -background) "#4caf50")}
      "Start Game"]]

    ;; Slideshow Mode Card
    [:div {:style {:flex "1"
                   :min-width "280px"
                   :max-width "350px"
                   :background "white"
                   :border-radius "16px"
                   :padding "30px"
                   :box-shadow "0 4px 16px rgba(0,0,0,0.1)"
                   :transition "transform 0.2s ease, box-shadow 0.2s ease"
                   :cursor "pointer"}
           :on-mouse-enter #(do
                              (set! (.. % -target -style -transform) "translateY(-4px)")
                              (set! (.. % -target -style -boxShadow) "0 8px 24px rgba(33, 150, 243, 0.2)"))
           :on-mouse-leave #(do
                              (set! (.. % -target -style -transform) "translateY(0)")
                              (set! (.. % -target -style -boxShadow) "0 4px 16px rgba(0,0,0,0.1)"))
           :on-click start-slideshow}
     [:div {:style {:font-size "64px"
                    :margin-bottom "15px"}}
      "🖼️"]
     [:h2 {:style {:color "#333"
                   :font-size "24px"
                   :margin-bottom "15px"}}
      "Slideshow Mode"]
     [:p {:style {:color "#666"
                  :font-size "15px"
                  :line-height "1.6"
                  :margin-bottom "20px"}}
      "Relax and enjoy the conference photos with descriptions. Browse at your own pace."]
     [:button {:style {:padding "12px 24px"
                       :background "#2196f3"
                       :color "white"
                       :border "none"
                       :border-radius "8px"
                       :font-size "16px"
                       :font-weight "600"
                       :cursor "pointer"
                       :width "100%"
                       :box-shadow "0 2px 8px rgba(33, 150, 243, 0.3)"
                       :transition "all 0.2s ease"}
               :on-mouse-enter #(set! (.. % -target -style -background) "#1976d2")
               :on-mouse-leave #(set! (.. % -target -style -background) "#2196f3")}
      "Start Slideshow"]]]])

(defn option-button
  "Renders a single answer option button"
  [option-text index selected? correct-index show-result?]
  (let [is-correct? (= index correct-index)
        is-selected? (= selected? index)
        button-color (cond
                       (and show-result? is-selected? is-correct?) "#4caf50"
                       (and show-result? is-selected? (not is-correct?)) "#f44336"
                       (and show-result? is-correct?) "#81c784"
                       :else "#e0e0e0")
        text-color (if (and show-result? (or is-selected? is-correct?))
                     "white"
                     "#333")]
    [:button {:on-click (when-not show-result?
                          #(handle-answer index))
              :disabled show-result?
              :style {:padding "20px"
                      :margin "10px 0"
                      :width "100%"
                      :background button-color
                      :color text-color
                      :border "none"
                      :border-radius "8px"
                      :cursor (if show-result? "default" "pointer")
                      :font-size "16px"
                      :font-weight (if (and show-result? is-correct?) "600" "400")
                      :text-align "left"
                      :transition "all 0.3s ease"
                      :box-shadow (cond
                                    (and show-result? is-correct?) "0 4px 12px rgba(76, 175, 80, 0.3)"
                                    (and show-result? is-selected? (not is-correct?)) "0 4px 12px rgba(244, 67, 54, 0.3)"
                                    :else "0 2px 4px rgba(0,0,0,0.1)")}}
     option-text
     (when (and show-result? is-correct?)
       [:span {:style {:margin-left "10px"}} "✓"])
     (when (and show-result? is-selected? (not is-correct?))
       [:span {:style {:margin-left "10px"}} "✗"])]))

(defn trivia-question-panel
  "Displays the question and answer options"
  []
  (let [{:keys [question options correct-index explanation]} (current-slide-data)
        {:keys [show-result current-slide answers]} @game-state
        selected-answer (get-answer-for-slide current-slide)]
    [:div {:style {:flex "1"
                   :display "flex"
                   :flex-direction "column"
                   :padding "20px"
                   :max-width "600px"}}
     [:h3 {:style {:margin-bottom "20px"
                   :font-size "24px"
                   :color "#333"}}
      question]

     (when-not show-result
       [:p {:style {:margin-bottom "20px"
                    :color "#666"
                    :font-size "14px"
                    :font-style "italic"}}
        "Two statements are lies, one is the truth. Pick the truth!"])

     [:div {:style {:margin-bottom "20px"}}
      (for [[idx option] (map-indexed vector options)]
        ^{:key idx}
        [option-button option idx selected-answer correct-index show-result])]

     (when show-result
       [:div {:style {:padding "15px"
                      :background (if (= show-result :correct) "#e8f5e9" "#ffebee")
                      :border-radius "8px"
                      :margin-top "15px"}}
        [:p {:style {:margin "0"
                     :color (if (= show-result :correct) "#2e7d32" "#c62828")
                     :font-weight "600"
                     :margin-bottom "10px"}}
         (if (= show-result :correct)
           "🎉 Correct! You found the truth!"
           "❌ Not quite...")]
        [:p {:style {:margin "0"
                     :color "#555"
                     :font-size "14px"}}
         explanation]])]))

(defn navigation-controls
  "Navigation buttons for the slideshow"
  []
  (let [{:keys [current-slide show-result]} @game-state]
    [:div {:style {:display "flex"
                   :justify-content "space-between"
                   :align-items "center"
                   :padding "20px"
                   :gap "20px"}}
     [:button {:on-click previous-slide
               :disabled (= current-slide 0)
               :style {:padding "12px 24px"
                       :background (if (= current-slide 0) "#e0e0e0" "#2196f3")
                       :color (if (= current-slide 0) "#999" "white")
                       :border "none"
                       :border-radius "6px"
                       :cursor (if (= current-slide 0) "not-allowed" "pointer")
                       :font-weight "600"
                       :font-size "16px"}}
      "← Previous"]

     [:div {:style {:text-align "center"}}
      [:p {:style {:margin "0"
                   :font-size "18px"
                   :font-weight "600"
                   :color "#333"}}
       (str "Slide " (inc current-slide) " of " (total-slides))]
      [:p {:style {:margin "5px 0 0 0"
                   :font-size "14px"
                   :color "#666"}}
       (str "Score: " (:score @game-state) "/" (total-slides))]]

     [:button {:on-click next-slide
               :disabled (nil? show-result)
               :style {:padding "12px 24px"
                       :background (if show-result "#2196f3" "#e0e0e0")
                       :color (if show-result "white" "#999")
                       :border "none"
                       :border-radius "6px"
                       :cursor (if show-result "pointer" "not-allowed")
                       :font-weight "600"
                       :font-size "16px"}}
      (if (= current-slide (dec (total-slides)))
        "Finish →"
        "Next →")]]))

(defn game-summary
  "Final summary screen showing total score and review"
  []
  (let [{:keys [score answers]} @game-state
        percentage (Math/round (* 100 (/ score (total-slides))))
        grade (cond
                (>= percentage 90) {:letter "A+" :color "#4caf50" :message "Outstanding! You really know your Clojure Conj!"}
                (>= percentage 80) {:letter "A" :color "#66bb6a" :message "Excellent work! You paid attention!"}
                (>= percentage 70) {:letter "B" :color "#ffa726" :message "Good job! Not bad at all!"}
                (>= percentage 60) {:letter "C" :color "#ff9800" :message "Decent effort! Room for improvement!"}
                :else {:letter "D" :color "#f44336" :message "Better luck next time!"})]
    [:div {:style {:padding "40px"
                   :max-width "800px"
                   :margin "0 auto"
                   :text-align "center"}}
     [:h2 {:style {:color "#4caf50"
                   :font-size "36px"
                   :margin-bottom "10px"}}
      "🎉 Epic Trivia Complete!"]

     [:div {:style {:margin "30px auto"
                    :width "200px"
                    :height "200px"
                    :border-radius "50%"
                    :background (str "linear-gradient(135deg, " (:color grade) " 0%, " (:color grade) "dd 100%)")
                    :display "flex"
                    :flex-direction "column"
                    :align-items "center"
                    :justify-content "center"
                    :box-shadow "0 8px 24px rgba(0,0,0,0.15)"}}
      [:div {:style {:font-size "64px"
                     :font-weight "700"
                     :color "white"
                     :margin-bottom "5px"}}
       (:letter grade)]
      [:div {:style {:font-size "24px"
                     :font-weight "600"
                     :color "white"}}
       (str score "/" (total-slides))]]

     [:p {:style {:font-size "20px"
                  :color "#666"
                  :margin-bottom "30px"
                  :font-weight "500"}}
      (:message grade)]

     [:div {:style {:background "#f5f5f5"
                    :padding "20px"
                    :border-radius "12px"
                    :margin-bottom "30px"}}
      [:p {:style {:margin "0 0 10px 0"
                   :font-size "18px"
                   :color "#333"}}
       (str "You got " score " out of " (total-slides) " questions correct")]
      [:p {:style {:margin "0"
                   :font-size "18px"
                   :color "#333"
                   :font-weight "600"}}
       (str "That's " percentage "%!")]]

     [:div {:style {:display "flex"
                    :gap "15px"
                    :justify-content "center"
                    :flex-wrap "wrap"}}
      [:button {:on-click restart-game
                :style {:padding "15px 40px"
                        :background "#4caf50"
                        :color "white"
                        :border "none"
                        :border-radius "8px"
                        :cursor "pointer"
                        :font-size "18px"
                        :font-weight "600"
                        :box-shadow "0 4px 12px rgba(76, 175, 80, 0.3)"
                        :transition "all 0.2s ease"}
                :on-mouse-enter #(set! (.. % -target -style -transform) "translateY(-2px)")
                :on-mouse-leave #(set! (.. % -target -style -transform) "translateY(0)")}
       "🔄 Play Again"]
      [:button {:on-click back-to-menu
                :style {:padding "15px 40px"
                        :background "#757575"
                        :color "white"
                        :border "none"
                        :border-radius "8px"
                        :cursor "pointer"
                        :font-size "18px"
                        :font-weight "600"
                        :box-shadow "0 4px 12px rgba(117, 117, 117, 0.3)"
                        :transition "all 0.2s ease"}
                :on-mouse-enter #(set! (.. % -target -style -transform) "translateY(-2px)")
                :on-mouse-leave #(set! (.. % -target -style -transform) "translateY(0)")}
       "🏠 Back to Menu"]]]))

(defn slideshow-viewer
  "Simple slideshow viewer without game mechanics"
  []
  (let [lightbox-open? (r/atom false)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [handle-key (fn [e]
                           (let [key (.-key e)]
                             (case key
                               "ArrowLeft" (do (.preventDefault e)
                                               (when (> (:current-slide @game-state) 0)
                                                 (previous-slide)))
                               "ArrowRight" (do (.preventDefault e)
                                                (when (< (:current-slide @game-state) (dec (total-slides)))
                                                  (next-slide)))
                               "Escape" (when @lightbox-open?
                                          (.preventDefault e)
                                          (reset! lightbox-open? false))
                               nil)))]
          (js/document.addEventListener "keydown" handle-key)
          (aset this "keyHandler" handle-key)))

      :component-will-unmount
      (fn [this]
        (when-let [handler (aget this "keyHandler")]
          (js/document.removeEventListener "keydown" handler)))

      :reagent-render
      (fn []
        (let [{:keys [current-slide]} @game-state
              {:keys [image]} (current-slide-data)]
          [:div {:class "trivia-container"}
           ;; Lightbox overlay
           (when @lightbox-open?
             [:div {:style {:position "fixed"
                            :top 0
                            :left 0
                            :right 0
                            :bottom 0
                            :background "rgba(0,0,0,0.95)"
                            :z-index 9999
                            :display "flex"
                            :align-items "center"
                            :justify-content "center"
                            :cursor "pointer"
                            :animation "fadeIn 0.2s ease"}
                    :on-click #(reset! lightbox-open? false)}
              [:div {:style {:position "relative"
                             :max-width "95vw"
                             :max-height "95vh"}}
               [:img {:src image
                      :alt "Conference photo - full size"
                      :style {:max-width "95vw"
                              :max-height "95vh"
                              :object-fit "contain"
                              :image-orientation "from-image"}}]
               [:div {:style {:position "absolute"
                              :top "10px"
                              :right "10px"
                              :background "rgba(255,255,255,0.9)"
                              :color "#333"
                              :border "none"
                              :border-radius "50%"
                              :width "40px"
                              :height "40px"
                              :display "flex"
                              :align-items "center"
                              :justify-content "center"
                              :font-size "24px"
                              :cursor "pointer"
                              :font-weight "bold"}}
                "×"]]])

           ;; Title
           [:div {:style {:text-align "center"
                          :margin-bottom "30px"}}
            [:h1 {:class "game-title"
                  :style {:color "#2196f3"
                          :font-size "32px"
                          :margin-bottom "10px"}}
             "🖼️ Clojure Conj 2025 Photo Gallery"]
            [:p {:class "game-subtitle"
                 :style {:color "#666"
                         :font-size "16px"}}
             "Browse through conference memories"]]

           [:div {:class "trivia-card"}
            ;; Image section - full width
            [:div {:style {:display "flex"
                           :align-items "center"
                           :justify-content "center"
                           :padding "40px"
                           :min-height "500px"}}
             [:img {:src image
                    :alt "Conference photo"
                    :on-click #(reset! lightbox-open? true)
                    :style {:max-width "100%"
                            :max-height "600px"
                            :border-radius "12px"
                            :box-shadow "0 4px 12px rgba(0,0,0,0.15)"
                            :object-fit "contain"
                            :image-orientation "from-image"
                            :cursor "pointer"
                            :transition "transform 0.2s ease, box-shadow 0.2s ease"}
                    :on-mouse-enter #(do
                                       (set! (.. % -target -style -transform) "scale(1.02)")
                                       (set! (.. % -target -style -boxShadow) "0 8px 24px rgba(0,0,0,0.25)"))
                    :on-mouse-leave #(do
                                       (set! (.. % -target -style -transform) "scale(1)")
                                       (set! (.. % -target -style -boxShadow) "0 4px 12px rgba(0,0,0,0.15)"))}]]

            ;; Navigation
            [:div {:class "trivia-nav"
                   :style {:padding "20px"
                           :border-top "1px solid #e0e0e0"}}
             [:div {:style {:text-align "center"
                            :margin-bottom "15px"}}
              [:p {:class "trivia-slide-info"}
               (str "Photo " (inc current-slide) " of " (total-slides))]
              [:p {:style {:margin "5px 0 0 0"
                           :font-size "13px"
                           :color "#999"
                           :font-style "italic"}}
               "Click image to view full size • Use ← → arrow keys to navigate"]]

             [:div {:style {:display "flex"
                            :justify-content "space-between"
                            :gap "15px"}}
              [:button {:on-click previous-slide
                        :disabled (= current-slide 0)
                        :style {:padding "12px 24px"
                                :background (if (= current-slide 0) "#e0e0e0" "#2196f3")
                                :color (if (= current-slide 0) "#999" "white")
                                :border "none"
                                :border-radius "6px"
                                :cursor (if (= current-slide 0) "not-allowed" "pointer")
                                :font-weight "600"
                                :font-size "16px"
                                :flex "1"}}
               "← Previous"]

              [:button {:on-click back-to-menu
                        :style {:padding "12px 24px"
                                :background "#757575"
                                :color "white"
                                :border "none"
                                :border-radius "6px"
                                :cursor "pointer"
                                :font-weight "600"
                                :font-size "16px"
                                :flex "0 0 auto"}}
               "🏠 Menu"]

              (if (= current-slide (dec (total-slides)))
                [:button {:on-click back-to-menu
                          :style {:padding "12px 24px"
                                  :background "#4caf50"
                                  :color "white"
                                  :border "none"
                                  :border-radius "6px"
                                  :cursor "pointer"
                                  :font-weight "600"
                                  :font-size "16px"
                                  :flex "1"}}
                 "✓ Finish"]
                [:button {:on-click next-slide
                          :style {:padding "12px 24px"
                                  :background "#2196f3"
                                  :color "white"
                                  :border "none"
                                  :border-radius "6px"
                                  :cursor "pointer"
                                  :font-weight "600"
                                  :font-size "16px"
                                  :flex "1"}}
                 "Next →"])]]]]))})))

;; ============================================================================
;; Main Component
;; ============================================================================

(defn trivia-game
  "Main trivia game component"
  []
  (let [{:keys [mode game-complete?]} @game-state]
    [:div
     ;; Add responsive styles
     [:style "
      /* Base styles - only style our root container, not body */
      #trivia-app-root {
        margin: 0;
        padding: 0;
        background: #fafafa;
        min-height: 100vh;
      }
      
      .trivia-container {
        padding: 20px;
        max-width: 1200px;
        margin: 0 auto;
        font-family: system-ui, -apple-system, sans-serif;
        background: transparent;
        min-height: 80vh;
      }
      .trivia-content {
        display: flex;
        flex-wrap: wrap;
        min-height: 400px;
      }
      .trivia-card {
        background: white;
        border-radius: 16px;
        box-shadow: 0 4px 16px rgba(0,0,0,0.1);
        overflow: hidden;
      }
      .trivia-image {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 20px;
        min-width: 300px;
      }
      .trivia-image img {
        max-width: 100%;
        max-height: 500px;
        border-radius: 12px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        object-fit: contain;
        image-orientation: from-image;
      }
      .trivia-questions {
        flex: 1;
        display: flex;
        flex-direction: column;
        padding: 20px;
        max-width: 600px;
        min-width: 300px;
      }
      .trivia-question-text {
        margin-bottom: 20px;
        font-size: 24px;
        color: #333;
      }
      .trivia-instruction {
        margin-bottom: 20px;
        color: #666;
        font-size: 14px;
        font-style: italic;
      }
      .trivia-slide-info {
        margin: 0;
        font-size: 18px;
        font-weight: 600;
        color: #333;
      }
      .trivia-score-info {
        margin: 5px 0 0 0;
        font-size: 14px;
        color: #666;
      }
      
      /* Dark mode */
      @media (prefers-color-scheme: dark) {
        #trivia-app-root {
          background: #0d0d0d;
        }
        
        .trivia-container {
          background: transparent;
        }
        .trivia-card {
          background: #2d2d2d;
          box-shadow: 0 4px 16px rgba(0,0,0,0.4);
        }
        .trivia-image img {
          box-shadow: 0 4px 12px rgba(0,0,0,0.5);
        }
        .trivia-question-text {
          color: #e0e0e0;
        }
        .trivia-instruction {
          color: #b0b0b0;
        }
        .trivia-slide-info {
          color: #e0e0e0;
        }
        .trivia-score-info {
          color: #b0b0b0;
        }
        .trivia-option-button {
          background: #3d3d3d !important;
          color: #e0e0e0 !important;
          box-shadow: 0 2px 4px rgba(0,0,0,0.3) !important;
        }
        .trivia-option-button:disabled {
          background: #3d3d3d !important;
        }
        .trivia-option-button.correct {
          background: #4caf50 !important;
          color: white !important;
          box-shadow: 0 4px 12px rgba(76, 175, 80, 0.4) !important;
        }
        .trivia-option-button.incorrect {
          background: #f44336 !important;
          color: white !important;
          box-shadow: 0 4px 12px rgba(244, 67, 54, 0.4) !important;
        }
        .trivia-option-button.correct-answer {
          background: #81c784 !important;
          color: white !important;
        }
        .game-title {
          color: #66bb6a !important;
        }
        .game-subtitle {
          color: #b0b0b0 !important;
        }
      }
      
      /* Mobile styles */
      @media (max-width: 768px) {
        .trivia-container {
          padding: 10px;
        }
        .trivia-content {
          flex-direction: column;
          min-height: auto;
        }
        .trivia-image {
          width: 100%;
          padding: 10px;
          min-width: unset;
        }
        .trivia-image img {
          max-height: 300px;
        }
        .trivia-questions {
          width: 100%;
          padding: 10px;
          max-width: none;
          min-width: unset;
        }
        .trivia-question-text {
          font-size: 20px !important;
        }
        .trivia-option-button {
          padding: 15px !important;
          font-size: 14px !important;
        }
        .trivia-nav {
          padding: 15px !important;
        }
        .trivia-nav button {
          padding: 12px 20px !important;
          font-size: 15px !important;
        }
        .game-title {
          font-size: 24px !important;
        }
        .game-subtitle {
          font-size: 14px !important;
        }
      }
      
      /* Very small mobile */
      @media (max-width: 480px) {
        .trivia-container {
          padding: 5px;
        }
        .trivia-image img {
          max-height: 200px;
        }
        .trivia-question-text {
          font-size: 18px !important;
        }
        .trivia-option-button {
          padding: 12px !important;
          font-size: 13px !important;
        }
        .trivia-nav {
          padding: 10px !important;
        }
        .trivia-nav button {
          padding: 10px 16px !important;
          font-size: 14px !important;
        }
        .game-title {
          font-size: 20px !important;
        }
      }"]

     ;; Route based on mode
     (cond
       ;; No mode selected - show mode selection
       (nil? mode)
       [mode-selection]

       ;; Game mode - show game or summary
       (= mode :game)
       (if game-complete?
         [game-summary]
         [:div {:class "trivia-container"}
          [:div {:style {:text-align "center"
                         :margin-bottom "30px"}}
           [:h1 {:class "game-title"
                 :style {:color "#4caf50"
                         :font-size "32px"
                         :margin-bottom "10px"}}
            "🎯 Clojure Conj 2025 Epic Trivia"]
           [:p {:class "game-subtitle"
                :style {:color "#666"
                        :font-size "16px"}}
            "Two Lies and a Truth Edition"]]

          [:div {:class "trivia-card"}
           [:div {:class "trivia-content"}
            ;; Image section
            [:div {:class "trivia-image"}
             [:img {:src (:image (current-slide-data))
                    :alt "Conference photo"}]]

            ;; Questions section  
            [:div {:class "trivia-questions"}
             (let [{:keys [question options correct-index explanation]} (current-slide-data)
                   {:keys [show-result current-slide answers]} @game-state
                   selected-answer (get-answer-for-slide current-slide)]
               [:<>
                [:h3 {:class "trivia-question-text"}
                 question]

                (when-not show-result
                  [:p {:class "trivia-instruction"}
                   "Two statements are lies, one is the truth. Pick the truth!"])

                [:div {:style {:margin-bottom "20px"}}
                 (for [[idx option] (map-indexed vector options)]
                   ^{:key idx}
                   (let [is-correct? (= idx correct-index)
                         is-selected? (= selected-answer idx)
                         button-class (str "trivia-option-button"
                                           (when (and show-result is-selected? is-correct?) " correct")
                                           (when (and show-result is-selected? (not is-correct?)) " incorrect")
                                           (when (and show-result is-correct? (not is-selected?)) " correct-answer"))
                         button-color (cond
                                        (and show-result is-selected? is-correct?) "#4caf50"
                                        (and show-result is-selected? (not is-correct?)) "#f44336"
                                        (and show-result is-correct?) "#81c784"
                                        :else "#e0e0e0")
                         text-color (if (and show-result (or is-selected? is-correct?))
                                      "white"
                                      "#333")]
                     [:button {:class button-class
                               :on-click (when-not show-result
                                           #(handle-answer idx))
                               :disabled show-result
                               :style {:padding "20px"
                                       :margin "10px 0"
                                       :width "100%"
                                       :background button-color
                                       :color text-color
                                       :border "none"
                                       :border-radius "8px"
                                       :cursor (if show-result "default" "pointer")
                                       :font-size "16px"
                                       :font-weight (if (and show-result is-correct?) "600" "400")
                                       :text-align "left"
                                       :transition "all 0.3s ease"
                                       :box-shadow (cond
                                                     (and show-result is-correct?) "0 4px 12px rgba(76, 175, 80, 0.3)"
                                                     (and show-result is-selected? (not is-correct?)) "0 4px 12px rgba(244, 67, 54, 0.3)"
                                                     :else "0 2px 4px rgba(0,0,0,0.1)")}}
                      option
                      (when (and show-result is-correct?)
                        [:span {:style {:margin-left "10px"}} "✓"])
                      (when (and show-result is-selected? (not is-correct?))
                        [:span {:style {:margin-left "10px"}} "✗"])]))]

                (when show-result
                  [:div {:style {:padding "15px"
                                 :background (if (= show-result :correct) "#e8f5e9" "#ffebee")
                                 :border-radius "8px"
                                 :margin-top "15px"}}
                   [:p {:style {:margin "0"
                                :color (if (= show-result :correct) "#2e7d32" "#c62828")
                                :font-weight "600"
                                :margin-bottom "10px"}}
                    (if (= show-result :correct)
                      "🎉 Correct! You found the truth!"
                      "❌ Not quite...")]
                   [:p {:style {:margin "0"
                                :color "#555"
                                :font-size "14px"}}
                    explanation]])])]]

           ;; Navigation
           (let [{:keys [current-slide show-result]} @game-state]
             [:div {:class "trivia-nav"
                    :style {:padding "20px"}}
              ;; Slide info on top
              [:div {:style {:text-align "center"
                             :margin-bottom "15px"}}
               [:p {:class "trivia-slide-info"}
                (str "Slide " (inc current-slide) " of " (total-slides))]
               [:p {:class "trivia-score-info"}
                (str "Score: " (:score @game-state) "/" (total-slides))]]

              ;; Buttons below
              [:div {:style {:display "flex"
                             :justify-content "space-between"
                             :gap "15px"}}
               [:button {:on-click previous-slide
                         :disabled (= current-slide 0)
                         :style {:padding "12px 24px"
                                 :background (if (= current-slide 0) "#e0e0e0" "#2196f3")
                                 :color (if (= current-slide 0) "#999" "white")
                                 :border "none"
                                 :border-radius "6px"
                                 :cursor (if (= current-slide 0) "not-allowed" "pointer")
                                 :font-weight "600"
                                 :font-size "16px"
                                 :flex "1"}}
                "← Previous"]

               [:button {:on-click back-to-menu
                         :style {:padding "12px 24px"
                                 :background "#757575"
                                 :color "white"
                                 :border "none"
                                 :border-radius "6px"
                                 :cursor "pointer"
                                 :font-weight "600"
                                 :font-size "16px"
                                 :flex "0 0 auto"}}
                "🏠"]

               [:button {:on-click next-slide
                         :disabled (nil? show-result)
                         :style {:padding "12px 24px"
                                 :background (if show-result "#2196f3" "#e0e0e0")
                                 :color (if show-result "white" "#999")
                                 :border "none"
                                 :border-radius "6px"
                                 :cursor (if show-result "pointer" "not-allowed")
                                 :font-weight "600"
                                 :font-size "16px"
                                 :flex "1"}}
                (if (= current-slide (dec (total-slides)))
                  "Finish →"
                  "Next →")]]])]])

       ;; Slideshow mode
       (= mode :slideshow)
       [slideshow-viewer])]))

;; ============================================================================
;; App Initialization
;; ============================================================================

(defn ^:export init
  "Initialize and mount the trivia game app"
  []
  (when-let [root (js/document.getElementById "trivia-app-root")]
    (rdom/render [trivia-game] root)))

(init)
