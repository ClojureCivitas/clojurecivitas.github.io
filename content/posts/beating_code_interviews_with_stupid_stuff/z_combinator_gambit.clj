(ns beating-code-interviews-with-stupid-stuff.z-combinator-gambit)

;; Most obvious solution

(defn REV [LIST]
  (if (empty? LIST)
    []
    (conj (REV (rest LIST))
          (first LIST))))
(REV [1 2 3 4 5])
;;=> [5 4 3 2 1]

;; See any issues? (stack overflow, and mutability hehehehe)

;; Extract the pattern (don't rely on REV)

(def REV'
  (fn [SELF LIST]
    (if (empty? LIST)
      []
      (conj (SELF SELF (rest LIST))
            (first LIST)))))
(REV' REV' [1 2 3 4 5])
;;=> [5 4 3 2 1]

;; `SELF` is an input to the function.
;; REV' decouples the recursive logic from the function name.

;; What about Fib?

;; Oh no, our definition of reverse is intertwined with recursion.
;; Let's factor that out:

;; We need to introduce some scope

(def REV''
  (fn [SELF]
    (fn [LIST]
      (if (empty? LIST)
        []
        (conj (SELF (rest LIST))
              (first LIST))))))
#_((REV'' REV'') [1 2 3 4 5])
;; error

;; Oh no...
;; SELF isn't a function that takes a list,
;; it's a function that returns that function that operates on LIST,
;; and the argument to SELF is... SELF.
;; Therefore, we need (SELF SELF).

(def REV''
  (fn [SELF]
    (fn [LIST]
      (if (empty? LIST)
        []
        (conj ((SELF SELF) (rest LIST))
              (first LIST))))))

((REV'' REV'') [1 2 3 4 5])
;;=> [5 4 3 2 1]

;; That's a confusing way to write it
;;
;; Quite right, because it's not obvious what (SELF SELF) is...
;; we need to extract it out.
;; What we want is:

(def REV-LOGIC
  (fn [SELF]
    (fn [LIST]
      (if (empty? LIST)
        []
        (conj (SELF (rest LIST))
              (first LIST))))))

;; > Believe me when I say that's not what I meant...
;; Oh right...
;; now SELF = (SELF SELF) and that's impossible.
;; > Not what I meant, but also not true, consider (identity identity)

((identity identity) 1)
;;=> 1

(((identity identity) (identity identity)) 1)
;;=>1

;; > Why did you do that?
;; Never-mind
;; So I agree it's not impossible,
;; we just need to find the right conditions for
;; (SELF SELF) = SELF
;; So what is (SELF SELF) in this brave new world you suggested
;; > Not remotely what I'm talking about...

(REV-LOGIC REV-LOGIC)

;; Well, it's a function! That much is clear...
;; > Oh no what are you doing now

#_((REV-LOGIC REV-LOGIC) [1 2 3 4 5])
;; but it doesn't work, because (REV-LOGIC REV-LOGIC) not= REV-LOGIC.
;; let's try something easier:

(def fix
  (fn [logic]
    ;; return something like identity where self application does not change it
    #_fixed))

;; where fix takes the logic function, and makes a function such that
;; (fixed (fix logic)) = fixed
;; (fixed fixed) => fixed
;; which means that
;; ((fix f) (fix f)) = (fix f)
;; > Right, that sounds way easier... *shaking head*
;; Exactly! because we just reverse it
;; (fix f) = ((fix f) (fix f))
;; > why did you call it fix?
;; well, it was broken before right?
;; > I still think it's broken

(def fix
  (fn [logic]
    ((fix logic) (fix logic))))

;; but fix can still see itself, we need to parameterize the use of fixed

(def fix
  (fn [logic]
    ((fn [fixed]
       (logic (fixed fixed)))
     (fn [fixed]
       (logic (fixed fixed))))))

;; There, I lifted it out.
;; > what is fixed?
;; fixed is (fixed fixed) obviously
;; > why?
;; because (fix f) = ((fix f) (fix f)), it was your idea remember?
;; > huh?? this just looks inside out to me

#_(fix REV-LOGIC)
;; <stack overflow>

;; your right, it is inside out.
;; well of course, before logic gets called, fixed is calling fixed is calling fixed infinitely
;; we can't pass (fixed fixed) as an argument because it will be evaluated first.
;; Thanks for the tip.
;; > can we fix it? <slaps self>
;; Instead of calling (fixed fixed) we need a function that will create (fixed fixed)
;; when it's needed, after logic gets called.
;; logic needs to take itself as it's argument,
;; so the function we pass to logic should look very much like logic,
;; but of course without any actual logic in it.
;; > very logical
;; logic is a function of itself, returning a function that acts on a value:

#_(logic (fn self [v]
         ((fixed fixed) v)))

;; > didn't you say that (fixed fixed) = fixed?
;; Yes but only after we fix it.
;; fixing it requires us to go from fixed to (fixed fixed) remember?
;; > ah sure...
;; so while we are fixing logic, let's replace (logic (fixed fixed))
;; with our deferring function

(def fix
  (fn [logic]
    ((fn [fixed]
       (logic (fn self [v]
                ((fixed fixed) v))))
     (fn [fixed]
       (logic (fn self [v]
                ((fixed fixed) v)))))))

;; did you know this is called continuation passing style?
;; > CSP?
;; No that's communicating subprocesses
;; > that's confusing.
;; Isn't it?

;; Fortunately, we are about to be unconfused!

(fix REV-LOGIC)

;; > at least it didn't blow up

((fix REV-LOGIC) [1 2 3 4 5])
;;=> [5 4 3 2 1]

;; > I can't believe something so ridiculous actually worked
;; you're right, all those silly names, let's fix that

(def Z
  (fn [f]
    ((fn [x]
       (f (fn [v] ((x x) v))))
     (fn [x]
       (f (fn [v] ((x x) v)))))))

((Z REV-LOGIC) [1 2 3 4 5])
;;=> [5 4 3 2 1]

;; > Yay, what has this got to do with Fibonacci?
;; We're factoring out our logic remember?
;; > It looks to me like you doubled the code, that's not great refactoring
;; Hmmm you got me there, there does seem to be a lot of doubling.
;; What if we had a function for f => (f f)

(def W
  (fn [f]
    (f f)))

(def Z
  (fn [f]
    (W (fn [x]
         (f (fn [v] ((x x) v)))))))


((Z REV-LOGIC) [1 2 3 4 5])
;;=> [5 4 3 2 1]

;; > That's not really better...
;; O.K. we can keep extracting

(def E
  (fn [f]
    (fn [v]
      ((f f) v))))

(def Z
  (fn [f]
    (W (fn [x]
         (f (E x))))))

((Z REV-LOGIC) [1 2 3 4 5])
;;=> [5 4 3 2 1]

;; That's much nicer, I'm so glad you suggested it
;; > Can we write Fibonacci?

;; Oh that's easy now!

(def FIB-LOGIC
  (fn [SELF]
    (fn [[b a :as fibs]]
      (if (> b 10)
        fibs
        (SELF (concat [(+ a b) b] fibs))))))
((Z FIB-LOGIC) [1 1])
;;=> (13 8 8 5 5 3 3 2 2 1 1 1)

;; > That's all backward!!
;; Oh my mistake
((Z REV-LOGIC) ((Z FIB-LOGIC) [1 1]))
;;=> [1 1 1 2 2 3 3 5 5 8 8 13]
;; > You can't be serious...
;; > this is ridiculous
;; > we'll be here forever if you keep this up
;; I love where you're head is at with this challenge
;; an infinite sequence is exactly what we need...

(def FIB-LOGIC-W
  (fn [SELF]
    (fn [A]
      (fn [B]
        (lazy-seq
          (cons A ((SELF B) (+ A B))))))))
(take 20 (((Z FIB-LOGIC-W) 1) 1))
;;=> (1 1 2 3 5 8 13 21 34 55 89 144 233 377 610 987 1597 2584 4181 6765)

;; that's so nice
;; > nice it is not.
