
# Hypertrophy training - A short intro 

>NOTE: This is the first part of a series. It serves as an introduction to the
>problem domain of exercise and exercise tracking as well as a quick tour
>through an architecture that attempts to solve it. I will try to explain domain
>specific terms as much as possible so you don't need to be a gym rat to
>understand this. If you think I could do better in that regard, please reach
>out.

I have been doing what most people would call lifting weights or fitness training, for almost half of my life.
I like to call it hypertrophy training. Hypertrophy is a fancy term for "building muscle".
I think that word describes what I do pretty well, because I have stopped caring about the exact weight that I move,
I don't think about whether this will make me run faster, jump higher or something else.
For me, the seemingly simple, limited set of movements that I repeat indefinitely, along with the
intense burn in my muscles, are like a kind of meditation, a cathartic experience, that helps me unwind, forget
the struggles of life for a moment and just makes me feel great overall.
I tried many other sports, but this is the only thing I have been able to pursue consistently.
Reasons for that are likely: 
  * You can get pretty good results with very little time investment, if you know what you're doing
  * The injury risk is pretty low, again, if you know what you're doing 
  * Measuring progress and success is relatively simple - there are quite a few proxies available 
  * I find the science behind it pretty interesting
  
One of the most important things I learned on that journey was, that tracking your workouts is one of they keys to success.
It's easy to fool yourself otherwise, because you always want to do a bit more than last week (during a training block, of course there will be breaks, so called "deloads"), and at some point your body is going to disagree with you on that.

For me at least, "tracking" means noting down how many repetitions (reps) at what weight I did, per set, per exercise, per workout.
That would allow me to e.g. make sure that I put a bit more weight on the bar next time and hopefully complete the same number of reps with that.
Recently, I found that listening to your body a bit actually is helpful for long term success (and mental health, and consistency), so I started tracking
certain types of more subjective feedback as well. More detail on that later.


## The problem to be solved 
Now, for tracking your workouts in that way, you obviously need some tool. That is what this whole series of articles is going to be about.

Popular options for such a tool are: 
  * A simple paper notebook
  * A spreadsheet
  * A note taking app
  * A dedicated workout tracking app, most likely on a mobile device
  
  
The paper notebook and the plain note taking app are obviously attractive for their simplicity. When I used this approach, I did the following:
  * I entered my workout plan for the first week, lets say something like Squat, Squat, Squat, Deadlift, Deadlift
  * I trained, noted down what I did, done 
  * Then I could have calculated the next weights at the end of the week and entered those prescriptions for the next week. In reality, this mostly came down to just using whatever next higher weight was available (if I have been able to complete the planned amount of work previously) 
  
There is nothing wrong with that approach, if you can stay disciplined enough to always carry your notebook and keep it in order, track everything and don't use the
loose structure as a way to allow for all kinds of exceptions. The simple note taking app solves at least the portability problem for me, because my monkey brain finds
not forgetting the phone way easier, probably due to its higher dopamine yield.
But noting down workout logs is clunky on a phone, at least for me. I hate writing on a virtual keyboard, which makes the notes deteriorate into a swamp of abbreviations that becomes unmanageable quickly.

The spreadsheet is a solid middle ground between a specialized app and "just" plain text.
There are also great templates available for free, so you don't need to think about this yourself. Most are good enough, you can just pick one and get started.

It can even be used as just an addition to the first approach, because in theory, you could enter your notes in there so you have your workout logs as more
structured data and don't need to manually calculate your next week. The obvious drawback: You actually need to set a time slot aside to do that.
Which I personally have been able to maintain for about three weeks - not great. 
Which is why I rolled with the second option for a long time: Having my laptop with me during workouts, entering my data directly, during the workout, into a spreadsheet. This was possible because I have a home gym, so this is probably not for everyone. And even for me it was still too clunky - worked for some time, but I couldn't get rid of the feeling that there was upside potential left on the table.

Which led me to take the leap and start exploring mobile apps.
And as always when I think "surely this is a solved problem", I could not have been more wrong.
I found that in the FOSS space there is not really an app that lets me do what I want to do. There have been ok-ish apps in the past,
but even if I had been able to settle for one of them, I would be out of luck today, because they are no longer maintained (and I am not familiar at all
with android app development).
There is one paid app, that fits the bill pretty much exactly, but boy, that thing is expensive.

Which didn't stop me from trying it in the end and if I wasn't in the slightest tech savy I would probably
just accept the hefty price tag and stick with it, because it does exactly what I want.

Enter: An uncontrollable urge to build things (in clojure, preferrably).

This urge manifested in a plethora of attempts at a solution. Most of them involved some kind of frontend in the end, because I needed/wanted some kind of UI, on my phone, that remotely resembled that paid app. 
If you're interested in the evolution of today's approach, feel free to take a look at the repositories below, otherwise just skip to the next section.
  * https://github.com/Schroedingberg/romance-progression
  * https://github.com/Schroedingberg/progressive 
  * https://github.com/Schroedingberg/barbiff
  * https://github.com/Schroedingberg/muscleapi 

# An architecture for a simple (not easy) workout tracker/planner 

A tool for planning workouts and calculating progressions (the next workout) - avoiding all the stuff I don't like to do as much as possible (frontend work, webdev in general).

My default answer in terms of architecture, at least on the highest level, is functional core, imperative shell (FCIS).
Seems obvious, but for the sake of completeness and to establish a standard I can be held accountable against, I want to mention it explicitly.
Therefore, I'll have at least one namespace for domain logic. That's going to contain the "domain model" (a map, maybe with some vectors in it, no surprise there I guess) as well as a function that answers the question "Given this history of workout weeks, what's the next workout week going to look like?".
This will be treated in much more detail below.

And then, there needs to be some form of UI for me to look at the plan and enter data.
And of course a backend, that processes the entered data and populates the frontend with the next workouts to be done.

I'll leave it at that for the summary - I want you to be surprised when you reach the respective section 😆



## Domain logic
I have talked about the why and how in the introduction, so I will focus on drafting out the data model and what happens with the data here.

Since most of this is basically design decisions that may seem a bit arbitrary I subsume them under these **assumptions**:
    * The goal is to maximize hypertrophy
    * Training is structured in mesocycles (think: blocks of about eight weeks)
    * A mesocycle consists of at least one microcycle. In the real world, a microcycle will typically be a week
    * Each microcycle consists of a number of workouts.
    * Each workout consists of a number of exercises
    * An exercise consists of at least:
      * A name
      * A vector of target muscles (the level of detail is up to the user)
    * Planning happens from microcycle to microcycle, based on:
      * An initial plan, that contains the workouts for one microcycle. In addition the plan says with how many sets per exercise the user is going to start.
      * Performance data from previous microcycles
      * The initial data comes from the first microcycle - no weights or reps are entered into the plan.
        The user just enters their performance for each set. Then the progression algorithm, 
        which can be anything from just adding 2.5% to something more sophisticated,
        determines the next workouts prescribed values. That happens after finishing the microcycle.
        So the output of the progression function, after the first microcycle, is just a new microcycle, but each set
        now has prescribed values too.
        When determining the next microcycle's prescriptions, it can now compare the actually performed reps and weight the user entered
        with the prescriptions and adjust the target reps and number of sets accordingly.
        For example, if there's no progress for some time, adding a set could be suggested.
        Or removing one. The rules can be complex here. We'll start simple first and stick with just increasing or reducing the weight prescription.
        That's the "automatic" planning/progression part.
      * Optional feedback data, like "how long was i sore after the previous workout for that muscle group", "did I have joint pain", "how challenging was the number of sets" or "how do I rate the pump in the target muscle**
      

All of this is going to be done via a simple function that takes in data - a vector of workouts, representing a microcycle - and outputs the next microcycle.

If this is confusing right now, it might become clearer once we look at a few code snippets. For brevity, this will happen in one of the follow-up articles.


## UI - CalDaV and Tasks.org
**Disclaimer**: "UI" might be a bit of a stretch here. Actually this section rather describes to what lengths I am ready to go to avoid developing a UI and to leverage infrastructure I already have. 


Since I already have a nextcloud running and use its CalDAV api to manage my tasks via [this nice little app](https://tasks.org/) I want to reuse that.
I have a dedicated "Workouts" task list in there.
The idea is, that a simple script runs on a cron job, say at sunday night.
It checks for a plan in a directory and if it finds one, checks if there are any Workouts (that are not done yet) in that CalDAV list.
If not, it just populates the tasklist with the first microcycle.
It adds a top level task for each workout, with dates to fit into one week (my default setting for microcycle length).
Each of those has subtasks. One for each exercise.
And for each set, there's another subtask per exercise.

More data goes into the notes:
    * Exercise level feedback (pump, soreness, joint pain, workload) goes into the exercise note
    * Set level feedback - weight and reps - go into each set.
    * The notes don't enforce any structure and obviously, without any further processing, we will just end up with strings. Of course, I have an idea on how to solve this in place. Just read on 😄

The tree mesocycle-microcycle-workout-exercise is already structured by the CalDAV protocol. I have written a small parser for that, that might need some polishing but its a good start (finally, some code, yay!):

```clojure
(defn ->dav-tree
  [kv-pairs]
  (reduce
   (fn [stack [k v]]
     (case k
       :begin ;; On begin just put a a map on the stack, of type v.
       (conj stack {:type v :properties {} :components []})
       :end ;; On end, pop stack and put the map into the components vector of the next one (the target)
       ;; If we're already at the last element of the stack, it means we have reached the outermost nesting level.
       ;; In that case, just return that remaining element.
       ;; The :end branch is where the actual building of the result happens.
       (let [[h s] [(peek stack) (pop stack)]
             target (peek s)
             new-top (update-in target [:components] conj h)]
         (if (seq s)
           (conj (pop s) new-top)
           h))
       (let [[h s] [(peek stack) (pop stack)]]
         (conj s (assoc-in h [:properties k] v)))))
   '()
   kv-pairs))

```

To get usable (as in: clojure data structures) data out of the notes (and to write them back cleanly) we need a bit more power, since we might have to deal with upper/lower issues, spaces, etc. that come from smartphone keyboards. We want the syntax to be very simple, very constrained, but a bit forgiving so that it doesnt get in the way.
Therefore this gets an extra section. 

>NOTE: I have deliberately glossed over how the CalDAV interaction is going to work here and just assumed for now, that I have an sdk for that already.
> I have a mostly vibecoded implementation that for now is included with the codebase and I am quite happy with it for the moment.
> Since this is mostly a pretty standard thing, I will propably release that separately as a library at some point, because I haven't been able to find a really nice to use solution for that yet.


## Workout data microlanguage

Instead of writing a lot of words to describe how we map from CalDAV notes to data and back I will just provide a grammar:  
```

entry      = prescription | actual
prescription = ("p" | "prescribed") ws weight ws reps
actual       = ("a" | "actual") ws weight ws reps
            | weight ws reps

weight     = ("w" | "weight") ws number
reps       = ("r" | "reps") ws number

number     = digit+
digit      = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
ws         = " "+

```

Something like that should go a long way. And since we keep all the data, we can easily fix hiccups later on.
Using *instaparse*, we will probably save a lot of work here.

The grammar for the set-level feedback looks pretty similar - you can look it up in the codebase, as soon as that is published, if you're interested.

The idea behind this is, that at the beginning of a mesocycle, the first week will be populated with all the exercises, with just an empty "actual field", so you just enter what you did.
Afterwards, that data is going to be used to calculate the prescriptions for the next week.
This means, that you never have commit to a weight or a rep range during setting up your plan.
This is just going to be determined by what you do in the first week.
This is both comfortable to use and embodies my training philosophy - not comparing my self to some absolute number, but rather prioritize personal, individual progress. You have to start from where you are, not try to be where somebody else is.



## "How, where and when do we run all this?" or "The backend" 

Having the data model, the "UI" and the connection between the two (the CalDAV protocol) figured out, the rest is just orchestration and configuration:
I have a nextcloud server running and I will just set up a cronjob that talks to its CalDAV endpoints, pulling data from and pushing data to it.


## Summary

Simple, integrated into what I already use, and hopefully very little code.
And the best part: I keep all the data and run analyses as I please.
What could be better. Nerd fitness at its finest.

I will both link to the actual code and dive deeper into implementation details in the next article. Stay tuned!






