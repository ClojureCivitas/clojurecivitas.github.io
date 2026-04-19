^{:kindly/hide-code true
  :clay             {:title  "Proximal Policy Optimization with Clojure and Pytorch"
                     :external-requirements []
                     :quarto {:author   [:janwedekind]
                              :draft    true
                              :description "A Clojure port of XinJingHao's PPO implementation using libpython-clj2, Pytorch, and Quil"
                              :image    "pendulum.png"
                              :type     :post
                              :date     "2026-04-18"
                              :category :ml
                              :tags     [:physics :machine-learning :optimization :ppo :control]}}}

(ns ppo.main
    (:require [clojure.math :refer (PI cos sin to-radians)]
              [clojure.core.async :as async]
              [quil.core :as q]
              [quil.middleware :as m]
              [libpython-clj2.require :refer (require-python)]
              [libpython-clj2.python :refer (py.) :as py]))

(require-python '[builtins :as python]
                '[torch :as torch]
                '[torch.nn :as nn]
                '[torch.nn.functional :as F]
                '[torch.optim :as optim]
                '[torch.distributions :refer (Beta)])

;; ## Motivation
;;
;; Recently I started to look into the problem of reentry trajectory planning in the context of developing the [sfsim](https://store.steampowered.com/app/3687560/sfsim/) space flight simulator.
;; I had looked into reinforcement learning before and tried out Q-learning using the [lunar lander reference environment of OpenAI's gym library](https://gymnasium.farama.org/environments/box2d/lunar_lander/) (now maintained by the Farama Foundation).
;; However I had stability issues.
;; The algorithm would learn a strategy and then suddenly diverge again.
;;
;; More recently (2017) the [Proximal Policy Optimization (PPO) algorithm was published](https://arxiv.org/abs/1707.06347) and it has gained in popularity.
;; PPO is inspired by Trust Region Policy Optimization (TRPO) but is much easier to implement.
;; Most importantly PPO can handle continuous observation and action spaces.
;; The [Stable Baselines3](https://github.com/DLR-RM/stable-baselines3) Python library has a implementation of PPO, TRPO, and other reinforcement learning algorithms.
;; However I found [XinJingHao's PPO implementation](https://github.com/XinJingHao/PPO-Continuous-Pytorch/) which I found easier to follow.
;;
;; In order to use PPO with a simulation environment in Clojure and also in order to get a better understanding of PPO, I dediced to do an implementation of PPO in Clojure.
;;
;; ## Pendulum environment
;;
;; ![screenshot of pendulum environment](pendulum.png)
;;
;; First we implement a simple pendulum environment to test the PPO algorithm.
;; In order to be able to switch environments, we define a protocol according to the environment abstract class used in OpenAI's gym.
(defprotocol Environment
  (environment-update [this action])
  (environment-observation [this])
  (environment-done? [this])
  (environment-truncate? [this])
  (environment-reward [this action]))

;; Here is a configuration for testing the pendulum.
(def frame-rate 20)

(def config
  {:length  (/ 2.0 3.0)
   :max-speed 8.0
   :motor 6.0
   :gravitation 10.0
   :dt (/ 1.0 frame-rate)
   :save false
   :timeout 10.0
   :angle-weight 1.0
   :velocity-weight 0.1
   :control-weight 0.0001})

;; ### Setup
;;
;; A method to initialise the pendulum is defined.
(defn setup
  "Initialise pendulum"
  [angle velocity]
  {:angle          angle
   :velocity       velocity
   :t              0.0})

;; Same as in OpenAI's gym the angle is zero when the pendulum is pointing up.
;; Here a pendulum is initialised to be pointing down and with an angular velocity of 0.5.
(setup (/ PI 2) 0.5)

;; ### State updates
;;
;; The angular acceleration due to gravitation is implemented as follows.
(defn pendulum-gravity
  "Determine angular acceleration due to gravity"
  [gravitation length angle]
  (/ (* (sin angle) gravitation) length))

;; The angular acceleration depends on the gravitation, length of pendulum, and angle of pendulum.
(pendulum-gravity 9.81 1.0 0.0)
(pendulum-gravity 9.81 1.0 (/ PI 2))
(pendulum-gravity 9.81 2.0 (/ PI 2))

;; The motor is controlled using an input value between -1 and 1.
;; This value is simply multiplied with the maximum acceleration provided by the motor.
(defn motor-acceleration
  "Angular acceleration from motor"
  [control motor-acceleration]
  (* control motor-acceleration))

;; A simulation step of the pendulum is implemented as follows.
(defn update-state
  "Perform simulation step of pendulum"
  ([{:keys [angle velocity t]} {:keys [control]} {:keys [dt motor gravitation length max-speed]}]
   (let [gravity        (pendulum-gravity gravitation length angle)
         motor          (motor-acceleration control motor)
         t              (+ t dt)
         acceleration   (+ motor gravity)
         velocity       (max (- max-speed) (min max-speed (+ velocity (* acceleration dt))))
         angle          (+ angle (* velocity dt))]
     {:angle          angle
      :velocity       velocity
      :t              t})))

;; Here are a few examples for advancing the state in different situations.
(update-state {:angle PI :velocity 0.0 :t 0.0} {:control 0.0} config)
(update-state {:angle PI :velocity 0.1 :t 0.0} {:control 0.0} config)
(update-state {:angle (/ PI 2) :velocity 0.0 :t 0.0} {:control 0.0} config)
(update-state {:angle 0.0 :velocity 0.0 :t 0.0} {:control 1.0} config)

;; ### Observation
;;
;; The observation of the pendulum state uses cosinus and sinus of the angle to resolve the wrap around problem of angles.
;; The angular speed is normalized to be between -1 and 1.
(defn observation
  "Get observation from state"
  [{:keys [angle velocity]} {:keys [max-speed]}]
  [(cos angle) (sin angle) (/ velocity max-speed)])

;; The observation of the pendulum is a vector with 3 elements.
(observation {:angle 0.0 :velocity 0.0} config)
(observation {:angle 0.0 :velocity 0.5} config)
(observation {:angle (/ PI 2) :velocity 0.0} config)

;; Note that the observation needs to capture all information required for achieving the objective, because it the only information available to the policy for deciding on the next action.

;; ### Action
;;
;; The action of a pendulum is a vector with one element between 0 and 1.
;; The following method converts it to a action hashmap used by the pendulum environment.
(defn action
  "Convert array to action"
  [array]
  {:control (max -1.0 (min 1.0 (- (* 2.0 (first array)) 1.0)))})

;; The following examples show how the action vector is mapped to a control input between -1 and 1.
(action [0.0])
(action [0.5])
(action [1.0])

;; ### Termination
;;
;; The truncate method is used to stop a pendulum run after a specific amount of time.
(defn truncate?
  "Decide whether a run should be aborted"
  ([{:keys [t]} {:keys [timeout]}]
   (>= t timeout)))

(truncate? {:t 50.0} {:timeout 100.0})
(truncate? {:t 100.0} {:timeout 100.0})

;; It is also possible to define a termination condition.
;; For the pendulum environment we specify that it never terminates.
(defn done?
  "Decide whether pendulum achieved target state"
  ([_state _config]
   false))

;; ### Reward
;;
;; The following method normalizes an angle to be between -PI and +PI.
(defn normalize-angle
  "Angular deviation from up angle"
  [angle]
  (- (mod (+ angle PI) (* 2 PI)) PI))

;; We also need the square of a number.
(defn sqr
  "Square of number"
  [x]
  (* x x))

;; The reward function penalises deviation from the upright position, non-zero velocities, and non-zero control input.
(defn reward
  "Reward function"
  [{:keys [angle velocity]} {:keys [angle-weight velocity-weight control-weight]} {:keys [control]}]
  (- (+ (* angle-weight (sqr (normalize-angle angle)))
        (* velocity-weight (sqr velocity))
        (* control-weight (sqr control)))))

;; ### Environment protocol
;;
;; Finally we are able to implement the pendulum as a generic environment.
(defrecord Pendulum [config state]
  Environment
  (environment-update [_this input]
    (->Pendulum config (update-state state (action input) config)))
  (environment-observation [_this]
    (observation state config))
  (environment-done? [_this]
    (done? state config))
  (environment-truncate? [_this]
    (truncate? state config))
  (environment-reward [_this input]
    (reward state config (action input))))

;; The following factory method creates an environment with an initial random state covering all possible pendulum states.
(defn pendulum-factory
  []
  (let [angle     (- (rand (* 2.0 PI)) PI)
        max-speed (:max-speed config)
        velocity  (- (rand (* 2.0 max-speed)) max-speed)]
    (->Pendulum config (setup angle velocity))))

;; ### Visualisation
;;
;; The following method is used to draw the pendulum and visualise the motor control input.
(defn draw-state [{:keys [angle]} {:keys [control]}]
  (let [origin-x   (/ (q/width) 2)
        origin-y   (/ (q/height) 2)
        length     (* 0.5 (q/height) (:length config))
        pendulum-x (+ origin-x (* length (sin angle)))
        pendulum-y (- origin-y (* length (cos angle)))
        size       (* 0.05 (q/height))
        arc-radius (* (abs control) 0.2 (q/height))
        positive   (pos? control)
        tip-angle  (if positive 225 -45)]
    (q/frame-rate frame-rate)
    (q/background 255)
    (q/stroke-weight 5)
    (q/stroke 0)
    (q/fill 175)
    (q/line origin-x origin-y pendulum-x pendulum-y)
    (q/stroke-weight 1)
    (q/ellipse pendulum-x pendulum-y size size)
    (q/no-fill)
    (q/arc origin-x origin-y (* 2 arc-radius) (* 2 arc-radius) (to-radians -45) (to-radians 225))
    (q/with-translation [(+ origin-x (* (cos (to-radians tip-angle)) arc-radius)) (+ origin-y (* (sin (to-radians tip-angle)) arc-radius))]
      (q/with-rotation [(to-radians (if positive 225 -45))]
        (q/triangle 0 (if positive 10 -10) -5 0 5 0)))
    (when (:save config)
      (q/save-frame "frame-####.png"))))

;; ### Animation
;;
;; The following method animates the pendulum and facilitates mouse control.
(defn run []
  (let [done-chan   (async/chan)
        last-action (atom {:control 0.0})]
    (q/sketch
      :title "Inverted Pendulum with Mouse Control"
      :size [854 480]
      :setup #(setup PI 0.0)
      :update (fn [state]
                  (let [action {:control (min 1.0 (max -1.0 (- 1.0 (/ (q/mouse-x) (/ (q/width) 2.0)))))}
                        state  (update-state state action config)]
                    (when (done? state config) (async/close! done-chan))
                    (reset! last-action action)
                    state))
      :draw #(draw-state % @last-action)
      :middleware [m/fun-mode]
      :on-close (fn [& _] (async/close! done-chan)))
    (async/<!! done-chan))
  (System/exit 0))

;; ![manually controlled pendulum](manual.gif)

;; ## Neural networks
;;
;; PPO is a machine learning technique using backpropagation to learn the parameters of two neural networks.
;;
;; * The **actor** network takes an observation as an input and outputs the parameters of a probability distribution for sampling the next action to take.
;; * The **critic** takes an observation as an input and outputs the expected cumulative reward for the current state.
;;
;; ### Pytorch
;;
;; For implementing the neural networks and backpropagation, I am using the Python-Clojure bridge [libpython-clj2](https://github.com/clj-python/libpython-clj) and [Pytorch](https://pytorch.org/).
;; The Pytorch library is quite comprehensive, is free software, and you can find a lot of documentation on how to use it.
;; The default version of [Pytorch on pypi.org](https://pypi.org/project/torch/) comes with CUDA (Nvidia) GPU support.
;; There is also a [Pytorch wheel on AMD's website](https://rocm.docs.amd.com/projects/install-on-linux/en/latest/install/3rd-party/pytorch-install.html#use-a-wheels-package) which comes with [ROCm](https://rocm.docs.amd.com/projects/install-on-linux/en/latest/install/quick-start.html) support.
;; Here we are going to use a CPU version of Pytorch which is a much smaller install.
;;
;; You need to install [Python 3.10](https://www.python.org/) or later.
;; For package management we are going to use the [uv](https://docs.astral.sh/uv/) package manager.
;; The following *pyproject.toml* file is used to install Pytorch and NumPy.
;;
;; ```toml
;; [project]
;; name = "ppo"
;; version = "0.1.0"
;; description = "Proximal Policy Optimization"
;; authors = [{ name="Jan Wedekind", email="jan@wedesoft.de" }]
;; requires-python = ">=3.10.0"
;; dependencies = [
;;     "numpy",
;;     "torch",
;; ]
;;
;; [tool.uv]
;; python-preference = "only-system"
;;
;; [tool.uv.sources]
;; torch = { index = "pytorch" }
;; numpy = { index = "pytorch" }
;;
;; [[tool.uv.index]]
;; name = "pytorch"
;; url = "https://download.pytorch.org/whl/cpu"
;;
;; [build-system]
;; requires = ["setuptools", "wheel"]
;; build-backend = "setuptools.build_meta"
;; ```
;;
;; Note that we are specifying a custom repository index to get the CPU-only version of Pytorch.
;; Also we are using the system version of Python to prevent *uv* from trying to install its own version which lacks the *\_cython* module.
;; To freeze the dependencies and create a *uv.lock* file, you need to run
;;
;; ```bash
;; uv lock
;; ```
;;
;; You can install the dependencies using
;; ```bash
;; uv sync
;; ```
;;
;; In order to access Pytorch from Clojure you need to run the `clj` command via `uv`:
;;
;; ```bash
;; uv run clj
;; ```
;;
;; Now you should be able to import the Python modules using *require-python*.
(require-python '[builtins :as python]
                '[torch :as torch]
                '[torch.nn :as nn]
                '[torch.nn.functional :as F]
                '[torch.optim :as optim]
                '[torch.distributions :refer (Beta)])
