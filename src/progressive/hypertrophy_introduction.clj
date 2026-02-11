^{:kindly/hide-code true
  :clay             {:title  "Progressive —  Hypertrophy with clojure(script)"
                     :quarto {:author      :ameinel
                              :description "An introduction to progressive - a workout tracker"
                              :type        :post
                              :draft       true
                              :date        "2026-02-11"
                              :category    :fitness
                              :tags        [:fitness :sports :functional-programming :pwa]}}}

(ns progressive.hypertrophy-introduction)

;; ## What is Progressive?
;;
;; [Progressive](https://github.com/Schroedingberg/progressive) is a clojurescript app for tracking hypertrophy oriented workouts - some would also call it lifting weights, though technically that's not the core problem this solves.
;; The motivation is simple: Decide for a plan, train according to it, log your performance, get your next workout calculated based on established models for hypertrophy ("building muscle"). Then repeat.
;;
;; ## Why build another fitness tracker?
;;
;; I wanted something that is really simple, works on my phone, doesn't need a server and is FOSS. Surprisingly, that's hard to find. There are a few workout trackers on F-Droid, but none of them do what I need them to do. I'll go into detail about that later. Apart from these requirements, I wanted to hone my clojure skills.

;; ## Requirements
;;
;; 1. Data first: I wanted to get something that I can use as quickly as possible, while keeping it as simple as possible. Therefore, I needed a flat workout log - to collect the data. Some would call those pieces of data events. In fact, I tend to do that too, although with caution (it would be an overstatement to call this an event-sourced system).
;;
;; 2. Local first: I went through many design drafts until I arrived at the current design, which were all [server-side apps](https://github.com/Schroedingberg/romance-progression). While from a development perspective that is my comfort-zone, I soon arrived at the conclusion that for the problem I was trying to solve, the overhead of running a server was just not justifiable. So ultimately I decided to dive deep into the unknown realms of frontend development and build this as a pwa, using reagent. The core logic is agnostic of that, so I'll keep the frontend part for another post.
;;
;; 3. Flexible progression algorithms: This overlaps with 1. a bit, but it is important to mention this explicitly. There's a lot of research about how to progress through a block of workouts for ideal hypertrophy. At the same time, exercise science is surprisingly hard to do - if you look at studies you often find sample sizes that would disqualify the data in other research fields. Therefore you may well find that whatever awesome progression somebody comes up with, it may not work for you. Or maybe you just can't keep up with it and have to just train "good enough", so you don't really need your progressions calculated for you at all. Maybe you just want to log what you did and try to do a bit more, a bit heavier next time. In any case, I wanted to keep the progression calculation completely separate of the logging functionality.


;; ## Show me code!
;;
;; We don't want to just walk into the gym and randomly lift weights.
;; We need a plan! The largest unit that makes sense to me is a block of roughly four weeks, which the community calls a mesocycle. A mesocycle consists
;; of microcycles. So a block of four weeks usually has four microcycles (you could use any period length actually, but let's stick with what most people are used to). In each microcycle you do a number of workouts and each workout has exercises, of which you do a number of sets.
;; > NOTE: In this context, 'set' refers to a set of an exercise, which is the elementary unit of work I want to track. Each set represents a real world event like 'n repetitions at weight m for exercise X'. This might get confusing, because clojure also has a function 'set'. Whenever I am talking about sets in this context, I am referring to a set in the exercise sense, not in the mathematical or 'hash-set' sense. You have been warned!

;; Sounds pretty hierarchical, huh? Luckily, hierarchical data structures are something we're all really comfortable with.

;; A mesocycle might look like this (we show only the first week here for brevity):

{"Just squat twice per week, for two weeks" ;; This is what we call the mesocycle
 {0 ;; microcycle index
  {:monday
   {"Squat"
    [{:exercise-name "Squat"
      :muscle-groups [:quads]}
     {:exercise-name "Squat"
      :muscle-groups [:quads]}]}
   :thursday
   {"Squat"
    [{:exercise-name "Squat"
      :muscle-groups [:quads]}
     {:exercise-name "Squat"
      :muscle-groups [:quads]}]}}}}

;; Ok, that's roughly what we want the plan for a mesocycle to look like.
;; Naturally, we can't be bothered to type the full plan - it's going to be the same every week, fundamentally.
;; We need a kind of template, that lets us express what we want to do more concisely:

(def template
  {:name          "2x Minimal Full Body"
   :n-microcycles 4
   :workouts
   {:monday
    {:exercises {"Dumbbell Row"             {:n-sets 2 :muscle-groups [:back]}
                 "Dumbbell Press (Incline)" {:n-sets 2 :muscle-groups [:chest :shoulders]}
                 "Lying Dumbbell Curl"      {:n-sets 3 :muscle-groups [:biceps]}
                 "Back Raise"               {:n-sets 1 :muscle-groups [:hamstrings]}
                 "Reverse Lunge Dumbbell"   {:n-sets 2 :muscle-groups [:glutes :quads]}
                 "Sissy squat"              {:n-sets 2 :muscle-groups [:quads]}}}
    :thursday
    {:exercises {"Back Raise"                {:n-sets 1 :muscle-groups [:hamstrings]}
                 "Barbell Squat"             {:n-sets 2 :muscle-groups [:glutes :quads]}
                 "Bench press (Narrow Grip)" {:n-sets 2 :muscle-groups [:chest :triceps]}
                 "Pullup (Underhand Grip)"   {:n-sets 2 :muscle-groups [:back :biceps]}}}}})

;; Neat! Not much more code, but we have a lot more exercises. This is my current plan by the way!
;; Of course, to get a proper plan out of this, we need to expand this somehow:


(defn expand-exercises
  "Expand {:n-sets 3 ...} into a vector of 3 set maps."
  [{:keys [exercises]}]
  (reduce-kv
    (fn [m name {:keys [n-sets] :as ex}]
      (assoc m name (vec (repeat n-sets (-> ex (dissoc :n-sets) (assoc :exercise-name name))))))
    (array-map)
    exercises))

(defn ->plan
  "Expand a template into the full plan structure."
  [{:keys [name n-microcycles workouts]}]
  (let [expanded (update-vals workouts expand-exercises)]
    {name (into (sorted-map)
                (zipmap (range n-microcycles)
                        (repeat n-microcycles expanded)))}))
;; That will give us all the sets that we plan to do, as maps.
;; You might ask yourself by now: Why would I want to put all that information into those maps that represent a set (like for instance the exercise name)?
;; Glad you ask! Of course, this is not a random choice. I'll explain that next.
;; TODO: Explain event structure
