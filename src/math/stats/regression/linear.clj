^{:kindly/hide-code true
  :clay             {:title  "Learn linear least squares with me"
                     :quarto {:author   [:ameinel]
                              :type     :post
                              :date     "2026-08-26"
                              :category :clojure
                              :tags     [:clojure.math]}}}

(ns math.stats.regression.linear

  (:require
   [fastmath.random :as rand]
   [fastmath.ml.regression :as reg]
   [scicloj.tableplot.v1.plotly :as plotly]
   [tablecloth.api :as tc]))


;; # Intro

;; > This is my attempt at refreshing my knowledge of pretty basic concepts by creating educational posts about them. Feynman style!

;; Linear regression is a solved problem. If you want to use it to solve a real world problem,
;; you probably want to look at [this article](https://scicloj.github.io/noj/noj_book.linear_regression_intro.html).
;; The code you see here will be optimized for readability and understanding rather than efficiency and stability.
;; Pre-requisites:
;; - Linear algebra and calculus, roughly high-school level
;; - A basic understanding of why you would want to draw a straight line through a bunch of points
;; - Clojure fluency (nothing fancy really, I promise)

;; In this post I want to help people, including me, to bridge the gap between theory and practice. I tend to forget how exactly linear regression works and,
;; given that it is supposed to be simple, this bugs me. What I often do in such a moment is look at the [Wikipedia page](https://en.wikipedia.org/wiki/Linear_regression).
;; This often helps to give me a rough idea, but if I had to explain to a five-year-old (that happens to have a basic mathematical education) how exactly it works, I would probably be in trouble. This is my attempt to remedy that - with the added pressure of a very public audience, so I don't quit halfway.

;; > NOTE: There is some debate about whether linear regression and ordinary (linear) least squares are truly the same.
;; > Comments are always welcome, I am happy to learn. However, in both cases, the concept seems to revolve around minimising some error, so if you don't like the naming I chose,
;; > you may just as well imagine this post was called "minimising the error" or something.

;; # Let's get started
;; We're going to ignore the statistical interpretation entirely and just look at this from the perspective of basic high-school math.
;;
;; We start with a linear equation system:
;; $$\mathbf{y} = \mathbf{X}\boldsymbol{\beta}$$
;; where $y$ is our
;; dependent variable (the stuff that goes on the vertical axis) and $X$ is our
;; independent data (the "inputs", the stuff that goes on the x-axis).
;; $\beta$ is our parameters, fancy people nowadays call this the "model weights".

;; In our simple 1D case, this expands to:
;;
;; $$
;; \begin{bmatrix} y_1 \\ y_2 \\ \vdots \\ y_n \end{bmatrix}
;; =
;; \begin{bmatrix} 1 & x_1 \\ 1 & x_2 \\ \vdots & \vdots \\ 1 & x_n \end{bmatrix}
;; \begin{bmatrix} \beta_0 \\ \beta_1 \end{bmatrix}
;; $$

;; The column of ones gives us the intercept $\beta_0$. Without it, we would be forcing the line through the origin, which is rarely what we want.

;; This can't always be solved with real data in X.

;; So the best we can do, if we must produce something close to a solution,
;; is to minimize the error. We will call that $S$ here.
;; The error is the distance between the left hand side and the right hand side of our equation system,
;; so $$ S(\beta) =\|\mathbf{y} - \mathbf{X}\boldsymbol{\beta}\|^2 $$
;; To make it as small as possible, we want to find $$\hat{\boldsymbol{\beta}} =
;; \arg\min_{\boldsymbol{\beta}} S(\beta)
;; $$

;; > In case you don't remember: The double-pipe-symbol stands for the norm of a
;; > vector, which is a fancy word for "thing that behaves a bit like a length".
;; > In our simple case we use this one here, which behaves pretty much exactly
;; > like a length as a human would imagine it visually. For a vector $v\in
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

;; > I deliberately switch between a single $\beta$ as an argument to S and several indexed $\beta_k$. Mathematically this doesn't make a huge difference and I use whichever fits what I want to illustrate best in this article. Usually I prefer to put as much information as possible into one function argument, but for this illustration this would be a bit unwieldy.
;; > If we write the full function signature, with domain and range, we see that in both cases $S:\mathbb{R}^p \longrightarrow \mathbb{R}

;; Now we calculate the derivative by just one of the components of the vector $\vec{\beta}$
;;$$\frac{\partial S}{\partial \beta_k} = -2 \sum_{i=1}^{n} X_{ik} \left( y_i - \sum_{j=1}^{p} X_{ij} \beta_j \right)$$

;; Note that I used the chain rule there. Look it up if you don't remember it.

;; Now try to isolate $\beta$:
;;$$\sum_{i=1}^{n} X_{ik} y_i = \sum_{j=1}^{p} \left( \sum_{i=1}^{n} X_{ik} X_{ij} \right) \beta_j$$
;; To be able to zoom out again, I am going to turn this back into matrix-vector notation (trust me, this is going to be more readable than this index-madness):
;; $$ X^T y = X^TX\bf{\beta}$$

;; > We arrive at the matrix notation by just writing all the equations (every $k$, from 1 to $p$) down. Look up matrix multiplication on wikipedia if you are lost here.
;; > If you wonder where the transposition ($X^T$) comes from: No worries, this trips me up all the time as well. If you write everything out by hand as mentioned before, you will see it, because it is the only way the dimensions match. But if you want to just read on, it is ok to trust me on this for now.

;; Which, conceptually means we get $\beta$ via inverting $X^TX$:
;; $$ (X^TX)^{-1}X^Ty=\beta$$

;;> I say conceptually because for production usage you wouldn't want to invert the matrix to solve this - you'd much rather solve the equation system.
;;> For educational purposes I won't dive deeper into computational details. We indeed commit the felony of assuming that math works on a computer exactly as it does on paper to aid our learning journey. If you need to solve equation systems like these in production, look into QR-Decomposition, SVD or similar - and probably don't build it yourself.
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
;; Since we just have one independent variable, x is just a vector (a 1xm Matrix).
;; That is pretty inconvenient, because then $X^TX$ would just be a number.
;; Luckily, to put a line through our data, we need to find the intercept too, as explained above.
;; That means we need to put a column of ones in front of our data:

(def X (mapv #(vector 1.0 %) xdata))

(take 5 X)
;; Phew, back in matrix-land.

;; We are going to build $X^TX$ now.
;; I am making quite a few simplifications here, that matter a lot, but for not turning this too much into a math lecture I will just mention them:
;; - Unit vectors as basis
;; - Inner product is $x\cdot y= \sum_{i=1}^{n}(x_i*y_i)$


;; Based on these assumptions, we can build our own little (highly inefficient, highly hardcoded to the 1D-data case, highly unstable)
;; matrix computation library:


;; We're going to do quite a bit of transposition, so a function that does that for us will come in handy:
(defn transpose [matrix]
  (apply mapv vector matrix))

(transpose [[1 2 3] [4 5 6]])
;; Great, transposition works.

;; Now the inner product.
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
(def betahat
  (let [y ydata
        xtx (mult (transpose X) X)
        xtxinv (invert xtx)
        xtxinvxt (mult xtxinv (transpose X))]
    (apply-matrix-to-vector xtxinvxt y)))


;; Now let's see how our handmade OLS stacks up against the production model.
;; We compute predictions from our $\hat{\beta}$ and overlay both fits.

(def my-predictions
  (let [[b0 b1] betahat]
    (tc/dataset {:x xdata
                 :y-hat (mapv #(+ b0 (* b1 %)) xdata)})))

;; For contrast, a deliberately wrong model — just to prove the plot is real:
(def wrong-predictions
  (tc/dataset {:x xdata
               :y-hat (mapv #(+ 0 (* -1 %)) xdata)}))

(-> simple-linear-data
    (plotly/layer-point {:=name "data"})
    (plotly/layer-smooth {:=name "fastmath"})
    (plotly/layer-line {:=dataset my-predictions
                        :=x :x
                        :=y :y-hat
                        :=name "handmade OLS"
                        :=mark-color "red"
                        :=mark-opacity 0.7})
    (plotly/layer-line {:=dataset wrong-predictions
                        :=x :x
                        :=y :y-hat
                        :=name "obviously wrong"
                        :=mark-color "orange"
                        :=mark-opacity 0.7}))

;; As we can see from the plot, in this case my naive implementation matches the serious implementation pretty closely.
;; I couldn't believe it myself, so I included a totally wrong line, just to make sure that there's no error in the visualization.
;; Since the data is created on the fly, ymmv. If the random number generator creates weird data, this might break my algorithm, while fastmath might be able to handle it. (Challenge: Can you create data that will make the lines look visibly different?)
;;That's it for today. You should now no longer have to wonder how to go from data and functions to vectors and matrices - in this simple case at least.
;; # Follow-up ideas
;; Writing this was fun. I might do it again, with slight variations, such as:
;; - Weaker assumptions -> transition to nonlinear problems (with the someday-perspective of looking at neural networks and other "modern" stuff)
;; - Extensions of the method: Regularization, uncertainty quantification.
;; - More abstract explanation: This one basically already started with finite-dimensional vector spaces. In my opinion, the whole topic looks much prettier as orthogonal projection to a subspace of a Hilbert space.
;; - Naive FEM in clojure,to not lock ourselves too much into the data science perspective and explore more of an engineering/computational science perspective.

;; Feel free to reach out to me on the Clojurians Slack (@Snuffles) if you want to discuss this post or suggest a follow-up topic.

;; # More resources
;; * https://dragan.rocks/articles/17/Clojure-Numerics-1-Use-Matrices-Efficiently - If you want to do this properly, computation-wise
;; * https://linear.axler.net/LADR4e.pdf - My favourite linear algebra book. Available free online.
;; * https://scicloj.github.io/noj/ - Another great library (that I seemingly used here for benchmarking, even though I did not truly know what I was doing)
