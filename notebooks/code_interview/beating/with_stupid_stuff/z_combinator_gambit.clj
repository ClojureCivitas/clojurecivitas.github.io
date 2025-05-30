^{:kindly/hide-code true
  :clay             {:quarto {:title    "The Z-Combinator Gambit"
                              :type     :post
                              :image    "z-combinator.jpg"
                              :author   [:chouser :timothypratley]
                              :date     "2025-05-25"
                              :category :clojure
                              :tags     [:lambda-calculus :code-interview]
                              :keywords [:z-combinator]}}}
(ns code-interview.beating.with-stupid-stuff.z-combinator-gambit
  (:require [clojure.print-object.remove-extraneous]))

;; Welcome back code champs, number ninjas, and data divers to our first episode of Beating Code Interviews with Stupid Stuff.
;; People often send me emails asking, "How can I use lambda calculus to impress people?"
;; Today, we find out.

;; ![Programmer staring at Z-combinator](z-combinator.jpg)

;; I have an interview with ZCorp lined up in 5 minutes,
;; and our challenge is to only use anonymous functions.
;; No defn, no loops, and definitely no self-reference.
;; I’ll allow myself the occasional def for brevity,
;; but beyond that, we’ll be running on pure lambda calculus.

;; **20 minutes later**

;; > Hey, sorry to keep you waiting.
;; > I just got out of a more important meeting.
;; > I’m kind of a big deal here at ZCorp.
;; > Why don’t you tell me a *little* bit about yourself?

;; Born of binary, raised on algorithms, I walk the path of lambda...

;; > Riiiight... Let’s just start with the warm-up problem.
;; > Show me how you would reverse a list.

;; Ah, the timeless list reversal.
;; Deceptively simple, perilously deep.
;; We must first define our purpose.

(fn [SELF LIST])

;; > We’re just writing a function, and it only needs to take a list...

;; Not just any function, my friend, but one that knows itself.
;; To know yourself is to find your fixed point.

(def REV
  (fn [SELF LIST]
    (if (empty? LIST)
      []
      (conj (SELF SELF (rest LIST))
            (first LIST)))))
(REV REV [1 2 3 4 5])

;; `SELF` is an input to itself, the logic of reversal.

;; > Ok let's just move on to the next problem, creating a Fibonacci sequence.

;; Oh no, our definition of reverse is intertwined with recursion.
;; Let's factor that out:

;; We need to lift our `SELF`

(def REV'
  (fn [SELF]
    (fn [LIST]
      (if (empty? LIST)
        []
        (conj (SELF (rest LIST))
              (first LIST))))))
;; ```clojure
;; ((REV' REV') [1 2 3 4 5])
;; ```
;; **error**

;; Oh, no...
;; `SELF` doesn't take `LIST`,
;; it's a function that returns a function that operates on `LIST`,
;; and the argument to `SELF` is... `SELF`.
;; Therefore, we need to give it `(SELF SELF)`.

(def REV''
  (fn [SELF]
    (fn [LIST]
      (if (empty? LIST)
        []
        (conj ((SELF SELF) (rest LIST))
              (first LIST))))))

((REV'' REV'') [1 2 3 4 5])

;; > That's a confusing way to write it

;; Quite right, because it's not obvious what `(SELF SELF)` is.
;; We need to extract it out.
;; What we want is:

(def REV-LOGIC
  (fn [SELF]
    (fn [LIST]
      (if (empty? LIST)
        []
        (conj (SELF (rest LIST))
              (first LIST))))))

;; > Believe me when I say that is not what I meant...

;; Oh, right.
;; Now `SELF = (SELF SELF)`.

;; > Not what I meant, and also that sounds impossible.

;; But identity is the identity of itself:

(identity 1)

((identity identity) 1)

;; > O.K. sure, but that's a special case.

(((identity identity) (identity identity)) 1)

;; > This is an identity crisis.

;; We just need to find the right conditions for
;; `(SELF SELF) = SELF`.

(REV-LOGIC REV-LOGIC)

;; > Well, it's a function! That much is clear...

#_((REV-LOGIC REV-LOGIC) [1 2 3 4 5])

;; But it doesn't work, because `(REV-LOGIC REV-LOGIC) =/= REV-LOGIC.`
;; Let's try something easier:

(def FIX
  (fn [LOGIC]
    ;; return something like identity where self application does not change it
    #_FIXED))

;; `FIX` takes the logic function, and makes a function such that
;; `(FIXED (FIX LOGIC)) = FIXED`
;;
;; `(FIXED FIXED) => FIXED`
;; which means that
;; `((FIX LOGIC) (FIX LOGIC)) = (FIX LOGIC)`

;; > Right, that sounds way easier... **shaking head in disbelief**

;; Exactly! Because we just reverse it:
;; `(FIX F) = ((FIX F) (FIX F))`

;; > Why did you call it `FIX`?

;; Well, it was broken before right?

;; > I'm starting to think that you are the broken one.

(def FIX
  (fn [LOGIC]
    ((FIX LOGIC) (FIX LOGIC))))

;; But `FIX` can still see itself.
;; We need to parameterize the use of `FIXED`

(def FIX
  (fn [LOGIC]
    ((fn [FIXED]
       (LOGIC (FIXED FIXED)))
     (fn [FIXED]
       (LOGIC (FIXED FIXED))))))

;; There, I fixed it.

;; > What is fixed?

;; `FIXED` is `(FIXED FIXED)`, obviously.

;; > Obviously. **raises hands in dispair**

;; Because `(FIX F) = ((FIX F) (FIX F))`, it was your idea to refactor remember?

;; ```clojure
;; (FIX REV-LOGIC)
;; ```
;; **stack overflow**

;; > Everything looks to be inside out now.

;; Oh, you are right, we can't pass `(FIXED FIXED)` as an argument because it will be evaluated first.
;; Thanks for the tip.

;; > Can we fix it? **slaps self**

;; Instead of calling `(FIXED FIXED)` we need a function that will create `(FIXED FIXED)`
;; when it's needed, after `LOGIC` gets called.
;; `LOGIC` needs to take itself as it's argument,
;; so the function we pass to `LOGIC` should look very much like `LOGIC`,
;; but of course without any actual logic in it.

;; > That actually sounds logical.

;; `LOGIC` is a function of itself, returning a function that acts on a value:

#_(LOGIC (fn SELF [VALUE]
           ((FIXED FIXED) VALUE)))

;; > didn't you say that `(FIXED FIXED) = FIXED`?

;; Yes but only after we `FIX` it.
;; Fixing it requires us to go from `FIXED` to `(FIXED FIXED)` remember?

;; > Ah sure...

;; So while we are fixing logic, let's replace `(LOGIC (FIXED FIXED))`
;; with our deferring function.

(def FIX
  (fn [LOGIC]
    ((fn [FIXED]
       (LOGIC (fn SELF [VALUE]
                ((FIXED FIXED) VALUE))))
     (fn [FIXED]
       (LOGIC (fn SELF [VALUE]
                ((FIXED FIXED) VALUE)))))))

;; Did you know this is called continuation passing style?

;; > CSP?

;; No, that's communicating subprocesses.

;; > That's confusing.

;; Isn't it!?  Fortunately, we are about to be unconfused.

(FIX REV-LOGIC)

;; > At least it didn't blow up this time...

((FIX REV-LOGIC) [1 2 3 4 5])

;; > Nice, that's the right answer.

;; Even nicer is that our fixed logic behaves like identity now:

((REV-LOGIC (FIX REV-LOGIC)) [1 2 3 4 5])

((REV-LOGIC (REV-LOGIC (FIX REV-LOGIC))) [1 2 3 4 5])

;; > I can't believe something so ridiculous actually works.

;; Yes it is ridiculous to have all those silly names. Let's fix that:

(def Z
  (fn [F]
    ((fn [X]
       (F (fn [V] ((X X) V))))
     (fn [X]
       (F (fn [V] ((X X) V)))))))

;; You are not your variables.
;; Rename them, rebind them.
;; Your essence is invariant.

((Z REV-LOGIC) [1 2 3 4 5])

;; > Wait, we are meant to be doing Fibonacci, remember?

;; We are factoring out our `LOGIC`.

;; > It looks to me like you doubled the code, that's not great refactoring.
;; > Using single letters make it totally unreadable.

;; Hmmm, there does seem to be a lot of doubling.
;; We can factor out a function for `f => (f f)`.

(def REPLICATE "Omega, the self-devouring serpent"
  (fn [F]
    (F F)))

;; The replication of identity is itself.

((REPLICATE identity) 1)

;; But test not the serpent lightly

;; ```clojure
;; (REPLICATE REPLICATE)
;; ```
;; **stack overflow**

;; The replication of replication is eternal.
;; Now we can clean up that duplication.

(def Z
  (fn [LOGIC]
    (REPLICATE (fn [X]
                 (LOGIC (fn [V] ((X X) V)))))))

((Z REV-LOGIC) [1 2 3 4 5])

;; > That's not really any clearer...

;; Very well, we can keep extracting.

(def DEFER "Eta, the patient one"
  (fn [LOGIC]
    (fn [VALUE]
      ((REPLICATE LOGIC) VALUE))))

;; If the infinite is deferred, is it infinite?

(def FOLD "Zeta, weaver of logic, bringer of finitude"
  (fn [LOGIC]
    (REPLICATE (fn [SELF]
                 (LOGIC (DEFER SELF))))))

;; OMEGA diverges, ZETA folds, LOGIC writes QED.

((FOLD REV-LOGIC) [1 2 3 4 5])

;; That's much nicer, I'm so glad you suggested using longer names.

;; > Can we write Fibonacci, *please*?

;; Oh, that's easy now!

(def FIB-LOGIC
  (fn [SELF]
    (fn [[B A :as FIBS]]
      (if (> B 10)
        FIBS
        (SELF (concat [(+ A B) B] FIBS))))))

((FOLD FIB-LOGIC) [1 1])

;; > That's all backward!!

;; Oh, my mistake

((FOLD REV-LOGIC) ((FOLD FIB-LOGIC) [1 1]))

;; > You can't be serious...
;; > This is ridiculous.
;; > We'll be here forever if you keep this up.

;; I love that idea!
;; An infinite sequence is exactly what we need...

(def FIB-LOGIC-FOREVER
  (fn [SELF]
    (fn [A]
      (fn [B]
        (lazy-seq
          (cons A ((SELF B) (+ A B))))))))

(take 20 (((FOLD FIB-LOGIC-FOREVER) 1) 1))

;; That's so nice.

;; > Oh look at the time! I have a more important meeting to go to!
;; **disconnects**

;; Ouch, Rough.
;; ZCorp never got back to me, so let’s update the scoreboard as a loss.

^:kind/table ^:kindly/hide-code
{:Interviews [1]
 :Wins       [0]
 :GGs        [0]}

;; That’s all for today.
;; Until next time, keep on coding.
