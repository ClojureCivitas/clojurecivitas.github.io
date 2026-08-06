(ns math.stats.regression.linear
  (:require
   [fastmath.random :as rand]
   [fastmath.ml.regression :as reg]
   [math.stats.regression.mat :as mat]
   [scicloj.tableplot.v1.plotly :as plotly]
   [tablecloth.api :as tc]))

;; # Intro: The OG machine learning algorithm: Linear regression aka Ordinary least squares

;; > This is my attempt at refreshing my knowledge of pretty basic concepts by creating educational posts about them. Feynman style!

;; Linear regression is a solved problem. If you want to use it to solve a real world problem,
;; you probably want to look at [this article](https://scicloj.github.io/noj/noj_book.linear_regression_intro.html).
;; The code you see here will be optimized for readability and understanding rather than efficiency and stability.
;; Pre-requisites:
;; - Basic linear algebra and calculus
;; - A basic understanding of why you would want to draw a straight line through a bunch of points
;; - Basic clojure fluency (nothing fancy really, I promise)

;; In this post I want to help people, including me, to bridge the gap between theory and practice. I tend to forget how exactly linear regression works and,
;; given that it is supposed to be simple, this bugs me. What I often do in such a moment is look at the [Wikipedia page](https://en.wikipedia.org/wiki/Linear_regression).
;; This often helps to give me a rough idea, but if I had to explain to a five-year-old (that happens to have a basic mathematical education) how exactly it works, I would probably be in trouble. This is my attempt to remedy that - with the added pressure of a very public audience, so I don't quit halfway.

;; > NOTE: There is some debate about whether linear regression and ordinary (linear) least squares are truly the same.
;; > Comments are always welcome, I am happy to learn. However, in both cases, the concept seems to revolve about minimising some error, so if you don't like the naming I chose,
;; > you may just as well imagine this post was called "minimising the error" or something.

;; # Let's get started
;; We're going to ignore the statistical interpretation entirely and just look at this from the perspective of basic high-school math.
;;
;; We start with a linear equation system:
;; $$\mathbf{y} = \mathbf{X}\boldsymbol{\beta} + \boldsymbol{\epsilon}$$
;; where $y$ is our
;; dependent variable (the stuff that goes on the vertical axis) and $X$ is our
;; independent data (the "inputs", the stuff that goes on the x-axis).
;; $\beta$ is our parameters, fancy people nowadays call this the "model weights",
;; $\epsilon$ is an error term. We ignore entirely what that means, except for
;; acknowledging that we want to get rid of it. Since that doesn't always work
;; with real data (because X is not always invertible), we want to at least make
;; it as small as possible, i.e. we want to find $$\hat{\boldsymbol{\beta}} =
;; \arg\min_{\boldsymbol{\beta}} \|\mathbf{y} - \mathbf{X}\boldsymbol{\beta}\|^2
;; $$
;; TODO: Where did $\epsilon$ go?

;; > In case you don't remember: The double-pipe-symbol stands for the norm of a
;; > vector, which is a fancy word for "thing that behaves a bit like a length".
;; > In our simple case we use this one here, which behaves pretty much exactly
;; > like a lenghth as a human would imagine it visually. For a vector $v\in
;; > \mathbb{R}^n$, it is defined like this: ;; $$\|\mathbf{v}\|^2 =
;; > \sum_{i=1}^{n} |v_i|^2 = \sum_{i=1}^{n} v_i^2$$


;; From school, we know that an extreme point of a function can be found where
;; its slope is 0. To find the slope of a function, we calculate its derivative.
;; We can then set that to zero, which will help us find an extreme point
;; candidate. Of course, we don't always know yet whether that candidate is in
;; fact an extremum, and even if we do, we'd still have to figure out whether it
;; is a maximum or a minimum and whether it is the only one or whether there are
;; more. You might know how to do that from school for a single variable. Doing
;; it for multiple variables is not that much harder, but it requires
;; introducing a few more concepts. I will omit those here. For now you will
;; just have to believe me (or read about Hessian matrices - it's fun!) that we
;; are allowed to assume that that we will find a minimum by taking the
;; derivative of our error function and setting that to zero.


;; Now, how do we take the derivative of a function that takes a vector as an argument and returns a scalar?
;; We calculate the derivative of each entry!
;; To do that with the error function, let's first take a look at what entry k looks like:
;;$$ S(\beta_1, \beta_2, \ldots, \beta_p) = \sum_{i=1}^{n} \left( y_i - \sum_{j=1}^{p} X_{ij} \beta_j \right)^2$$

;; Now we calculate the derivative by just one of the components of the vector $\vec{\beta}$
;;$$\frac{\partial S}{\partial \beta_k} = -2 \sum_{i=1}^{n} X_{ik} \left( y_i - \sum_{j=1}^{p} X_{ij} \beta_j \right)$$

;; Note that I used the chain rule there. Look it up if you don't remember it.

;; Now try to isolate $\beta$:
;;$$\sum_{i=1}^{n} X_{ik} y_i = \sum_{j=1}^{p} \left( \sum_{i=1}^{n} X_{ik} X_{ij} \right) \beta_j$$
;; To be able to zoom out again, I am going to turn this back into matrix-vector notation (trust me, this is going to be more readable than this index-madness):
;; $$ X^T y = X^TX\bf{\beta}$$

;; Which, conceptionally means we get $\beta$ via inverting $X^TX$:
;; $$ (X^TX)^{-1}X^Ty=\beta$$

;;> I say conceptionally because for production usage you wouldn't want to invert the matrix to solve this - you'd much rather solve the equation system.
;;> For educational purposes I wont dive deeper into computational details. We indeed commit the felony of assuming that math works on a computer exactly as it does on paper to aid our learning journey.
;;
;;








;; # Enough maths, show me code!



;; To prove, or at least make it plausible, that the above theory works in principle, we need some kind of benchmark,
;; For that purpose, we are going to try and reproduce the results from [this scicloj docs page](https://scicloj.github.io/noj/noj_book.linear_regression_intro.html).
;; So lets just copy some code from there to generate data:



(def simple-linear-data
  (let [rng (rand/rng 1234)
        n 50
        a 2
        b -5]
    (-> {:x (repeatedly n #(rand/frandom rng 0 10))}
        tc/dataset
        (tc/map-columns :y
                        [:x]
                        (fn [x]
                          (+ (* a x)
                             b
                             (rand/grandom rng)))))))
;; Lets look at those:
(-> simple-linear-data
    plotly/layer-point)


;; We don't care for all the fancy stuff that gives us, we want plain clojure data structures!




(def xdata (-> simple-linear-data
               :x
               vec))

(def ydata (-> simple-linear-data
               :y
               vec))
;; Since we just have one independent variable, it is just a vector (a 1xm Matrix).

  ;; TODO But for some reason , its allowed (and necessary) to put a column of ones before that. (In fact TODO: that can be mathematically justified)

;; So actually we want

(def X (mapv #(vector 1.0 %) xdata))

(count X)

;; We need to build $X^TX$ first.
;; I am making quite a few simplifications here, that matter a lot, but for not turning this too much into a math lecture I will just mention them:
;; - Unit vectors as basis
;; - Inner product is $x\cdot y= \sum_{i=1}^{n}(x_i*y_i)$


;; Based on these assumptions, we can build our own little (highly inefficient, highly hardcoded to the 1D-data case, highly unstable)
;; matrix computation library:


(defn transpose [matrix]
  (apply mapv vector matrix))

(transpose [[1 2 3] [4 5 6]])

(defn inner
  "Inner product of two vectors.

  The inner product is also known as scalar product."
  [x y]
  (reduce + (mapv * x y)))

  ;; For a makeshift matrix product we have to transpose B to actually access the columns.
;; But then we can compute it as just a new matrix of inner product of each row and column:
(defn mult [A B]
  (let [bcols (transpose B)]
    (for [row A]
      (for [col bcols]
        (inner row col)))))

;; Using those we can now calculate X^TX:

(mult (transpose X) X)

;; I know I said earlier that computing the inverse explicitly is not great from a computational perspective.
;; It is however great from an educational perspective.
;; Therefore, I am going to show a way here to compute it, shamelessly exploiting the shape of our data to keep it really simple.
;; First of all, we need a function that defines multiplication of a matrix with a scalar - I like to call this scaling, because it, well, scales the matrix.
(defn scale-mat [factor A]
  (mapv (fn [row]
          (mapv #(* factor %) row))
        A))


;; We can now use that to implement a pretty neat formula for the inverse of a 2x2 matrix:


(defn invert [[[a b] [c d]]]
  (let [scale (/  1 (- (* a d) (* b c)))]
    (scale-mat scale [[d (- b)] [(- c) a]])))

;; Of course we want to see this in action:
(let [M [[1 2] [3 4]]
      inv (invert M)]
  (mult inv M))


;; The last piece that we are going to need is matrix-vector-multiplication.
;; I prefer the term "apply" though, because matrices are actually functions too.
(defn apply-matrix-to-vector [A x]
  (mapv #(inner % x) A))

(apply-matrix-to-vector [[1 0] [0 1]] [3 5])

;; Finally we can get that $\hat{\beta}$.
(let [y ydata
      xtx (mult (transpose X) X)
      xtxinv (invert xtx)
      xtxinvxt (mult xtxinv (transpose X))
      betahat (apply-matrix-to-vector xtxinvxt y)]
  betahat)

;; Of course those two numbers alone are a bit hard to judge.
;; Lets plot against the benchmark:
(def simple-linear-data-model
  (reg/lm
   ;; ys - a "column" sequence of `y` values:
   (simple-linear-data :y)
   ;; xss - a sequence of "rows", each containing `x` values:
   ;; (one `x` per row, in our case):
   (-> simple-linear-data
       (tc/select-columns [:x])
       tc/rows)
   ;; options
   {:names ["x"]}))


(-> simple-linear-data
    (plotly/layer-point {:=name "data"})
    (plotly/layer-smooth {:=name "prediction"}))
