^{:kindly/hide-code true
  :clay             {:title  "Bell State Circuit"
                     :quarto {:author :ludgersolbach
                              :draft true
                              :type :post
                              :image "bell_state_circuit.svg"
                              :date "2025-10-10"}}}
(ns qclojure.examples.bell-state-circuit 
  (:require
   [scicloj.kindly.v4.kind :as kind]))

;; # Bell State Circuit Example
;; This example demonstrates how to create and visualize a Bell state circuit and how to simulate
;; a quantum computer executing the circuit, using both an ideal simulator and a hardware simulator,
;; on classical hardware using QClojure.
;;
;; ## What is QClojure?
;; QClojure is a Clojure library for quantum computing that provides tools to create,
;; execute, and visualize quantum circuits. It allows users to define quantum circuits using a
;; high-level, functional programming approach.
;; QClojure supports various quantum gates, measurements, and state manipulations, making it
;; suitable for building quantum algorithms and exploring quantum computing concepts.
;; It comes with a variety of quantum algorithms and provides simulators to run quantum circuits
;; and algorithms on classical hardware.
;;
;; With extensions (e.g. for Amazon Braket), QClojure enables users to run their quantum
;; circuits on real quantum hardware.
;;
;; To use QClojure, you have to include it as a dependency in your Clojure
;; project. Please use the [latest version](https://clojars.org/org.soulspace/qclojure).
;;
;; ## What is a Quantum Computer?
;; A quantum computer is a type of computing device that leverages the principles of quantum mechanics
;; to perform computations. Unlike classical computers, which use bits as the basic unit of information
;; (0s and 1s), quantum computers use quantum bits or qubits. Qubits can exist in multiple states simultaneously
;; due to a property called superposition. This allows quantum computers to process a vast number of
;; possibilities at once.
;; Another key property of quantum computers is entanglement, where the state of one qubit can be
;; directly related to the state of another, regardless of the distance between them. This phenomenon
;; enables quantum computers to perform certain calculations much more efficiently than classical computers.
;;
;; ## What is a Quantum Circuit?
;; A quantum circuit is a model for quantum computation in which a computation is represented as a
;; sequence of quantum gates, which are the quantum analogs of classical logic gates. Quantum circuits
;; manipulate qubits through these gates to perform operations and transformations on their quantum states.
;; Quantum circuits are typically visualized using circuit diagrams, where qubits are represented as
;; horizontal lines and quantum gates as symbols placed along these lines.
;; Quantum circuits can be used to implement quantum algorithms, which are designed to solve specific
;; problems more efficiently than classical algorithms. Examples of quantum algorithms include Shor's
;; algorithm for factoring large numbers and Grover's algorithm for searching unsorted databases.
;;
;; ## What is a Quantum Gate?
;; A quantum gate is a fundamental building block of quantum circuits, analogous to classical logic gates
;; used in classical computing. Quantum gates manipulate the state of qubits, which are the basic
;; units of quantum information. Unlike classical bits that can be either 0 or 1,
;; qubits can exist in a superposition of states, allowing quantum gates to perform complex operations.
;; Quantum gates are represented as unitary matrices, which ensure that the operations they perform
;; are reversible.
;;
;; Common quantum gates include:
;; - **Hadamard Gate (H)**: Creates superposition by transforming a qubit from a definite state (|0⟩ or |1⟩)
;;   into an equal superposition of both states.
;; - **Pauli-X Gate (X)**: Also known as the quantum NOT gate, it flips the state of a qubit (|0⟩ to |1⟩ and vice versa).
;; - **Pauli-Y Gate (Y)**: Similar to the X gate but also introducess a phase shift.
;; - **Pauli-Z Gate (Z)**: Introduces a phase flip to the |1⟩ state while leaving the |0⟩ state unchanged.
;; - **CNOT Gate (Controlled NOT)**: A two-qubit gate that flips the state of the target qubit if the control
;;   qubit is in the state |1⟩. It is essential for creating entanglement between qubits. 
;;
;; ## What is a Bell State?
;; A Bell state is a specific quantum state of two qubits that represents the simplest and most
;; well-known example of quantum entanglement. The Bell states are maximally entangled states
;; and are used in various quantum information protocols, including quantum teleportation and
;; superdense coding.
;; Bell states are fundamental in the study of quantum mechanics and quantum computing,
;; illustrating the non-classical correlations that can exist between quantum systems.
;;
;; There are four different Bell states, but the most commonly referenced one is:
;; |Φ+⟩ = (|00⟩ + |11⟩) / √2
;;
;; This state indicates that if one qubit is measured to be in the state |0⟩, the other qubit will
;; also be in the state |0⟩, and similarly for the state |1⟩, demonstrating perfect correlation between
;; the two qubits.
;; The probability of measuring either |00⟩ or |11⟩ is equal, each with a probability of 0.5,
;; which means that other combinations like |01⟩ or |10⟩ will never be observed in this state.
;;
;; ## Creating the Bell State Circuit
;; The following code creates a simple quantum circuit that generates a Bell state.
;; First, we need to require the necessary namespaces from QClojure.
(require '[org.soulspace.qclojure.domain.state :as state]
         '[org.soulspace.qclojure.domain.circuit :as circuit]
         '[org.soulspace.qclojure.application.visualization :as viz]
         '[org.soulspace.qclojure.adapter.visualization.ascii :as ascii]
         '[org.soulspace.qclojure.adapter.visualization.svg :as svg])

;; Next, we create a quantum circuit with two qubits and apply the necessary quantum gates
;; to generate the Bell state. We use the Hadamard gate (H) on the first qubit to create
;; superposition, followed by a CNOT gate to entangle the two qubits.

(def bell-state-circuit
  (-> (circuit/create-circuit 2 "Bell State Circuit" "Creates a Bell state.")
      (circuit/h-gate 0)
      (circuit/cnot-gate 0 1)))

;; We can visualize the circuit as ASCII art for the REPL.
^kind/code
(viz/visualize-circuit :ascii bell-state-circuit)

;; For notebooks and documents, we can also visualize the circuit as SVG.
^kind/hiccup
(viz/visualize-circuit :svg bell-state-circuit)

;; ## Executing the Bell State Circuit
;; To quickly test the circuit in the REPL, we can use the execute-circuit function
;; from the circuit namespace.
(def result (circuit/execute-circuit bell-state-circuit))

;; The result is a map that contains the final state of the qubits after executing the circuit.
result

;; ## Using Simulators to Execute the Circuit
;; We can also use a quantum backend to execute the circuit with more options.
;; QClojure provides two different simulator backends: an ideal simulator backend
;; and a hardware simulator backend.
;; The ideal simulator simulates the quantum circuit without any noise or errors,
;; while the hardware simulator simulates the quantum circuit with noise and errors
;; that are present in real quantum hardware.
;;
;; First, we need to require the necessary namespaces for the simulators.
(require 
 '[org.soulspace.qclojure.application.backend :as backend]
 '[org.soulspace.qclojure.adapter.backend.ideal-simulator :as ideal-sim]
 '[org.soulspace.qclojure.adapter.backend.hardware-simulator :as hw-sim])

;; Let's first use the ideal simulator to execute the Bell state circuit.
(def ideal-simulator (ideal-sim/create-simulator))

;; We define some options for the execution, such as the results we want to obtain.
;; In this case, we want to measure the qubits 100 times (shots).
(def options {:result-specs {:measurements {:shots 100}}})

;; Now we can execute the circuit using the ideal simulator and the defined options.
(def ideal-result
  (backend/execute-circuit ideal-simulator bell-state-circuit options))

;; The result is a map that contains the measurement results and other information
;; about the execution.
ideal-result

;; We can visualize the frequencies of the measurements obtained from the
;; ideal simulator as a histogram.
^kind/hiccup
(viz/visualize-measurement-histogram :svg (get-in ideal-result [:results :measurement-results :frequencies]))

;; Now we you the hardware simulator to execute the Bell state circuit.
;; The hardware simulator simulates the quantum circuit with noise and errors
;; that are present in real quantum hardware.
(def hardware-simulator (hw-sim/create-hardware-simulator))

;; We can also select a specific quantum device to simulate. We choose the
;; IBM Lagos quantum device for this example. The IBM Lagos is a 7-qubit quantum
;; computer that is available on the IBM Quantum Experience platform.
(backend/select-device hardware-simulator :ibm-lagos)

;; We execute the circuit using the hardware simulator and the defined options.
(def hardware-result
  (backend/execute-circuit hardware-simulator bell-state-circuit options))

;; Here is the result of the hardware simulation.
hardware-result

;; We can visualize the result of the hardware simulation as a histogram of the
;; measurement frequencies to compare it with the ideal simulation result.
^kind/hiccup
(viz/visualize-measurement-histogram :svg (get-in hardware-result [:results :measurement-results :frequencies]))

;; We results are probabilistic, so we may not get exactly the same results every time we
;; execute the circuit. However, we should see that the results from the ideal simulator
;; are closer to the expected Bell state results (|00⟩ and |11⟩ with similar counts) compared to the
;; hardware simulator, which may show some deviations due to noise and errors.
;; This demonstrates the impact of quantum noise and errors on the execution of quantum circuits
;; on real quantum hardware.
;;
;; ## Conclusion
;; In this example, we created a simple quantum circuit that generates a Bell state,
;; visualized the circuit, and executed it using both an ideal simulator and a hardware
;; simulator provided by QClojure. We observed the differences in the measurement results
;; between the two simulators, highlighting the effects of noise and errors in quantum computing.
