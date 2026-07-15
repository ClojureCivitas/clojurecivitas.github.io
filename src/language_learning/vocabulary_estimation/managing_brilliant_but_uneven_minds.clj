^{:kindly/hide-code true
  :clay {:hide-info-line true
         :title "Managing Brilliant but Uneven Minds: My Theory-to-Algorithm Workflow"
         :quarto {:author :jamiep
                  :description "Why managing fast, uneven AI agents requires explicit assumptions, executable research, and independent validation gates."
                  :type :post
                  :date "2026-07-15"
                  :category :concepts
                  :tags [:ai :research-workflow :language-learning :clojure]
                  :keywords [:coding-agents :theory-to-algorithm :executable-research :model-validation]}}}

(ns language-learning.vocabulary-estimation.managing-brilliant-but-uneven-minds
  (:require [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(kind/hiccup
 [:style
  (str
   ":root{--mw-accent:#1464b5;--mw-accent-soft:#e5f1fb;--mw-warm:#9a4b00;--mw-warm-soft:#fff0df;--mw-success:#0f695f;--mw-success-soft:#e2f4f0;--mw-muted:#4f5b66}"
   ".quarto-dark{--mw-accent:#73b7ff;--mw-accent-soft:#173653;--mw-warm:#ffc27a;--mw-warm-soft:#4a2d12;--mw-success:#64d8c7;--mw-success-soft:#163d38;--mw-muted:#b9c7d2}"
   "#title-block-header{padding-top:.75rem}#title-block-header h1{line-height:1.15;overflow-wrap:anywhere}"
   ".mw-opening{border-left:5px solid var(--mw-accent);border-radius:.45rem;padding:clamp(1rem,3vw,1.4rem);margin:0 0 1.5rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 87%,var(--mw-accent) 13%);font-size:1.08rem;line-height:1.65}.mw-opening p:last-child{margin-bottom:0}"
   ".mw-grid,.mw-definition-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,15rem),1fr));gap:.85rem;margin:1.25rem 0}"
   ".mw-card,.mw-definition{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.55rem;padding:1rem;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529);overflow-wrap:anywhere}.mw-card h3{font-size:1rem;margin:0 0 .45rem;color:var(--mw-accent)}.mw-card p:last-child,.mw-card ul:last-child{margin-bottom:0}"
   ".mw-definition dt{font-weight:800;color:var(--mw-accent)}.mw-definition dd{margin:.25rem 0 0}"
   ".mw-callout{border:1px solid color-mix(in srgb,var(--mw-accent) 45%,var(--bs-border-color,#dee2e6));border-left:4px solid var(--mw-accent);background:color-mix(in srgb,var(--bs-body-bg,#fff) 91%,var(--mw-accent) 9%);padding:1rem 1.15rem;margin:1.35rem 0;border-radius:.4rem}.mw-callout.warm{border-color:color-mix(in srgb,var(--mw-warm) 55%,var(--bs-border-color,#dee2e6));border-left-color:var(--mw-warm);background:color-mix(in srgb,var(--bs-body-bg,#fff) 91%,var(--mw-warm) 9%)}.mw-callout.success{border-color:color-mix(in srgb,var(--mw-success) 55%,var(--bs-border-color,#dee2e6));border-left-color:var(--mw-success);background:color-mix(in srgb,var(--bs-body-bg,#fff) 91%,var(--mw-success) 9%)}.mw-callout strong{display:block;margin-bottom:.3rem}.mw-callout p:last-child{margin-bottom:0}"
   ".mw-table-wrap{max-width:100%;overflow-x:auto;margin:1.25rem 0}.mw-table{width:100%;border-collapse:collapse}.mw-table th,.mw-table td{padding:.65rem .75rem;border-bottom:1px solid var(--bs-border-color,#dee2e6);text-align:left;vertical-align:top;min-width:8rem}.mw-table thead th{border-bottom:2px solid var(--bs-border-color,#adb5bd)}.mw-table code{overflow-wrap:anywhere;white-space:normal}"
   ".mw-code{max-width:100%;overflow-x:auto;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.45rem;padding:.75rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 94%,var(--mw-accent) 6%)}"
   ".mw-cycle-caption,.mw-note{font-size:.9rem;color:var(--mw-muted)}.mw-cycle-caption{text-align:center}"
   ".series-toc{min-width:0;border:1px solid var(--bs-border-color,#ced4da);border-radius:.6rem;padding:clamp(.85rem,3vw,1.2rem);margin:0 0 1.4rem;background:var(--bs-body-bg,#fff)}.series-toc h2{font-size:1.2rem;margin:0 0 .55rem}.series-toc p{margin:0 0 .7rem}.series-toc ol{margin:0;padding-left:1.65rem}.series-toc li{padding:.18rem .45rem}.series-status{display:inline-block;margin-left:.35rem;font-size:.7rem;font-weight:700;letter-spacing:.04em;text-transform:uppercase;color:var(--mw-muted)}.series-current{margin:.35rem 0 .35rem -.7rem;border-left:4px solid var(--mw-accent);border-radius:.4rem;padding:.6rem .75rem!important;background:color-mix(in srgb,var(--bs-body-bg,#fff) 84%,var(--mw-accent) 16%);box-shadow:inset 0 0 0 1px color-mix(in srgb,var(--mw-accent) 35%,transparent);font-weight:700}.series-current>a{color:var(--mw-accent)}.series-current .series-status{border-radius:999px;padding:.18rem .48rem;background:var(--mw-accent);color:#fff}.quarto-dark .series-current .series-status{color:#10212b}"
   "@media(max-width:575px){.mw-table th,.mw-table td{padding:.5rem}.mw-card{padding:.8rem}}")])

^:kindly/hide-code
(kind/hiccup
 [:nav.series-toc {:aria-labelledby "series-contents-heading"}
  [:h2#series-contents-heading "Theory to vocabulary-estimation series"]
  [:p "Learning the theory, making the assumptions explicit, and developing a scorer for "
   [:a {:href "https://lexibench.com/"} "Lexibench.com"] "."]
  [:ol {:start 0}
   [:li.series-current
    [:a {:href "managing_brilliant_but_uneven_minds.html"
         :aria-current "page"}
     "Managing brilliant but uneven minds: my theory-to-algorithm workflow"]
    [:span.series-status "you are here"]]
   [:li [:a {:href "bayes_theorem_simulations.html"}
         "Bayes' theorem from uncertainty to decision"]
    [:span.series-status "published"]]
   [:li [:a {:href "beta_binomial_first_pass.html"}
         "Estimating vocabulary size with a simple Bayesian model"]
    [:span.series-status "published"]]
   [:li [:a {:href "pair_frequency_logistic_v2_article.html"}
         "Does pair frequency predict learner responses?"]
    [:span.series-status "published"]]
   [:li "From Self-Reported CEFR to a Versioned Lemma–Form-Pair Pool"
    [:span.series-status "planned"]]
   [:li "From Correlated Form Pairs to Latent Lemma Knowledge"
    [:span.series-status "planned"]]
   [:li "Modelling Correct, Wrong, and Don't-Know Separately"
    [:span.series-status "planned"]]
   [:li "Calibrating Items Before IRT and Adaptive Selection"
    [:span.series-status "planned"]]
   [:li "When Contexts and Senses Become Identifiable"
    [:span.series-status "planned"]]]])

^:kindly/hide-code
(kind/hiccup
 [:div.mw-opening
  [:p "I am genuinely excited. I now have brilliant but profoundly uneven minds at my command: a new natural-language interface to the digital world and to a vast body of online knowledge. I can call on ten agents concurrently and watch them work at superhuman speed on some narrow tasks. The job description of a knowledge worker is changing accordingly. A central question is no longer only “How do I do this work?” but “How do I become an effective manager of these interesting minds?” Their speed, range, and stamina are extraordinary. Their judgment, context, and accountability are not. The fascinating new craft is learning how to work around those weaknesses and use those superhuman abilities to produce good code and good knowledge work—not AI slop."]])

^:kindly/hide-code
(kind/hiccup
 [:ol.article-chapter-map
  [:li [:strong "Manage"] [:br] "Treat agent output as delegated work, not revealed truth."]
  [:li [:strong "Recognise disruption"] [:br] "Ask what a new cost and capability curve changes."]
  [:li [:strong "Define"] [:br] "Turn users' tacit expectations into an explicit estimand."]
  [:li [:strong "Ground"] [:br] "Separate theory already applied from ideas still to test."]
  [:li [:strong "Cycle"] [:br] "Version assumptions, evidence, candidates, and decisions."]
  [:li [:strong "Inspect"] [:br] "Publish the workflow and its complete case study together."]
  [:li [:strong "Learn"] [:br] "Borrow useful practices without outsourcing responsibility."]])

;; ## The management job
;;
;; Calling these systems “minds” is deliberately provocative, but it is not a
;; claim that they are people. It describes the experience of delegating a
;; messy intellectual task, receiving a surprising interpretation, challenging
;; it, and watching another attempt arrive in seconds. Natural language is the
;; interface, but the useful agents are not confined to chat. They can search,
;; read a repository, run programs, inspect a browser, edit an article, and
;; compare the result with an explicit test.
;;
;; The speed is real, but “superhuman” needs a boundary. An agent can transform
;; text, search a codebase, or try variations far faster than I can. It does not
;; thereby acquire a superhuman understanding of my users, my product, the
;; consequences of a wrong decision, or the scientific status of a claim. Ten
;; agents can also make ten fast, plausible mistakes. Parallelism multiplies a
;; good process; it does not create one.

^:kindly/hide-code
(kind/hiccup
 [:div.mw-grid
  [:section.mw-card
   [:h3 "What the agents are unusually good at"]
   [:ul
    [:li "Rapid search and synthesis across code, documents, and tools."]
    [:li "Turning a clear specification into several concrete alternatives."]
    [:li "Writing, running, and revising code in short feedback loops."]
    [:li "Replaying routine checks without fatigue."]]]
  [:section.mw-card
   [:h3 "What remains dangerously uneven"]
   [:ul
    [:li "Confidently filling gaps with a plausible invention."]
    [:li "Optimising the task it heard instead of the outcome I meant."]
    [:li "Losing context or treating a recent source as established authority."]
    [:li "Announcing completion before the whole result has been checked."]]]
  [:section.mw-card
   [:h3 "What the human manager must own"]
   [:ul
    [:li "The purpose, users, definitions, priorities, and acceptable risk."]
    [:li "Which sources and evidence deserve authority."]
    [:li "Permission boundaries and irreversible decisions."]
    [:li "Independent review and the final claim that the work is good enough."]]]])

;; The “junior developer” analogy is useful if it changes behaviour. I do not
;; hand a junior colleague one vague sentence, merge whatever appears, and then
;; blame the colleague. I explain the outcome, let questions reveal ambiguity,
;; divide work into inspectable slices, provide tools, and require tests and
;; review. Agents need an even more explicit version of that management because
;; they are extraordinarily productive, do not accumulate responsibility, and
;; may repeat the same kind of mistake in a fresh, fluent form.
;;
;; This is why established engineering practices matter more, not less. Clear
;; requirements, version control, small changes, executable examples, test-first
;; regression work, peer review, least privilege, and rollback are mechanisms
;; for turning fallible effort into dependable output. An LLM can help perform
;; nearly every step. It cannot be the reason that a step is trusted.
;;
;; ## A book for this moment: *The Innovator's Dilemma*
;;
;; I have not yet read Clayton M. Christensen's
;; [*The Innovator's Dilemma*](https://store.hbr.org/product/the-innovator-s-dilemma-with-a-new-foreword-when-new-technologies-cause-great-firms-to-fail/10706),
;; and I am excited to. Its central idea seems exceptionally relevant to the
;; present moment. Successful organisations can make sensible decisions—listen
;; to their best customers, improve the products those customers value, demand
;; attractive margins—and still miss a disruptive change. The early disruptive
;; offering is often worse on the dimensions the established market already
;; rewards. It may instead be cheaper, simpler, more convenient, or useful to a
;; new or overlooked group. As it improves, it can redraw the market and its
;; value network before the incumbent's normal decision machinery knows how to
;; take it seriously.
;;
;; That is more precise than using *disruptive* as a synonym for impressive new
;; technology. Christensen's dilemma is that the incumbent's apparently good
;; management can rationally reject the small, low-margin, initially inferior
;; opportunity. The structures that made the organisation excellent at the old
;; game can prevent it from learning the new one.

^:kindly/hide-code
(kind/hiccup
 [:div.mw-callout.success
  [:strong "Popular and authoritative—but not scripture"]
  [:p "The book deserves to be called a management classic. Harvard Business Review Press describes it as a Wall Street Journal and Businessweek bestseller and one of the Economist's six most important business books. Harvard Business School records that it was also a New York Times bestseller and won the 1997 Global Business Book Award; Christensen was a Harvard Business School professor whose work put “disruptive innovation” into the managerial vocabulary."]
  [:p "That establishes extraordinary influence and practical authority. It does not make every case or extrapolation settled science. Later research has refined, disputed, and frequently complained about misuse of the theory. That distinction is itself relevant to this series: a powerful framework should generate sharper questions and testable predictions, not become a label that excuses us from evidence."]])

;; Applying the book to today's knowledge-worker disruption is my inference,
;; not a claim that Christensen predicted coding agents. AI systems are still
;; worse than skilled humans on many dimensions by which professional work has
;; traditionally been judged. At the same time, they radically change the cost,
;; speed, availability, and accessibility of producing software, analysis,
;; writing, designs, and operational work. A person who demands that an agent
;; first reproduce an expert's entire old job, unaided and without error, may
;; miss the new workflow forming around the technology.
;;
;; The useful question is not simply “Will AI replace white-collar jobs?” It is:
;; **which units of work, value networks, and organisational habits change when
;; competent intellectual labour becomes abundant, fast, conversational, and
;; uneven?** Established organisations are optimised around scarce human
;; attention, meetings, hand-offs, and implementation time. Agent-native work
;; can make specification, judgment, validation, and responsibility the new
;; bottlenecks. Knowledge workers are both the managers of this disruptive
;; capability and part of the sector being disrupted by it.
;;
;; ## From an expectation to an estimand
;;
;; My concrete case is a vocabulary-size scorer for
;; [Lexibench](https://lexibench.com/). I want to encode an algorithm that does
;; what learners reasonably expect, in a rigorous and inspectable way. That
;; sentence hides the hardest work. Users do not arrive with one formal,
;; unanimous definition. I have to turn likely expectations into hypotheses,
;; state them, show learners what the result means, check whether they interpret
;; it as intended, and version the model if the target changes.
;;
;; A useful technical word here is **estimand**: the quantity an algorithm
;; claims to estimate. “How many words do you know?” is not yet an estimand. It
;; does not say what counts as a word, which meanings count, what population of
;; vocabulary provides the denominator, or what evidence counts as knowledge.
;; An authoritative answer cannot be more precise than its question.
;;
;; Consider the dictionary form, or **lemma**, of a Polish word. Does knowing
;; the lemma mean knowing every possible inflected surface form? Or does it mean
;; recognising the intended meaning across the surface forms that account for,
;; say, 70% of the lemma's observed occurrences? The 70% is deliberately an
;; example, not a threshold smuggled into the current scorer. It exposes a real
;; choice between exhaustive type coverage and frequency-weighted coverage of
;; language people are likely to encounter.
;;
;; Polish makes this choice impossible to ignore. Nouns, adjectives, verbs,
;; numerals, and other word classes change form to express grammatical
;; information. The
;; [Grammatical Dictionary of Polish](https://sgjp.pl/about/) describes the
;; inflection of hundreds of thousands of lexemes through millions of textual
;; word forms. One lemma can therefore connect to many surface forms, sometimes
;; with the same spelling serving several grammatical functions. “One word” is
;; not a harmless database row.

^:kindly/hide-code
(kind/hiccup
 [:div.mw-table-wrap
  [:table.mw-table
   [:caption "Hidden choices behind a vocabulary-size result"]
   [:thead
    [:tr [:th {:scope "col"} "Question"]
     [:th {:scope "col"} "A tempting shortcut"]
     [:th {:scope "col"} "What must become explicit"]]]
   [:tbody
    [:tr
     [:th {:scope "row"} "What is counted?"]
     [:td "Words"]
     [:td "Lemmas, surface forms, lemma–form pairs, meanings, or versioned test items."]]
    [:tr
     [:th {:scope "row"} "What does “know” mean?"]
     [:td "Answered correctly once"]
     [:td "Passive recognition, production, repeated recognition across forms, or a latent probability."]]
    [:tr
     [:th {:scope "row"} "What does a response prove?"]
     [:td "Correct equals known"]
     [:td "The roles of guessing, slips, context, distractors, ambiguity, and item quality."]]
    [:tr
     [:th {:scope "row"} "What is the total?"]
     [:td "The language"]
     [:td "A fixed, versioned pool with declared inclusion and exclusion rules."]]
    [:tr
     [:th {:scope "row"} "When can the test stop?"]
     [:td "After a convenient number"]
     [:td "A specified uncertainty target, minimum evidence, and maximum burden."]]]]])

;; The current first-pass estimand is deliberately narrower:
;;
;; > **Receptive knowledge of lemma–surface-form pairs in a fixed, versioned
;; > pool.**
;;
;; This is passive receptive vocabulary: whether a learner recognises an
;; intended meaning, not whether they can produce the form in speech or writing.
;; Context and answer choices help operationalise one intended meaning, but the
;; current frequency data does not identify senses and the scorer does not
;; pretend to estimate sense-specific knowledge. Nor does pair knowledge yet
;; aggregate into a principled latent claim that the learner “knows the lemma.”
;;
;; Those are not embarrassing omissions to conceal. They are assumptions and
;; open questions to publish. Learners should see qualified results that say
;; what was estimated, from which pool, by which algorithm version, with what
;; uncertainty. User research then checks whether those words create the
;; understanding I intended. Transparency is necessary; comprehension is the
;; test of whether the transparency worked.
;;
;; ## Theory already used, and theory still to earn
;;
;; LLMs are fabulous research tools for this work. They can map unfamiliar
;; terminology, propose search strategies, compare papers, challenge an
;; assumption, explain a derivation several ways, and help translate a model
;; contract into executable code. They are discovery and synthesis engines,
;; not scientific authorities. A fluent summary sends me to the primary source;
;; it does not replace it.

^:kindly/hide-code
(kind/hiccup
 [:div.mw-grid
  [:section.mw-card
   [:h3 "Applied in the published work"]
   [:ul
    [:li "Measurement discipline: name the estimand, population, conditions, and exclusions."]
    [:li "Bayesian inference: update uncertainty from responses and predict untested pairs."]
    [:li "Stratified sampling: balance observations across a provisional frequency proxy."]
    [:li "Sequential stopping: stop at round boundaries when a declared interval target or soft maximum is reached."]
    [:li "Precommitted simulation validation: freeze scenarios, metrics, thresholds, and seeds before held-out evidence."]]]
  [:section.mw-card
   [:h3 "Important, but not yet claimed"]
   [:ul
    [:li "A versioned conversion from CEFR and corpus evidence into a learner-relevant pool."]
    [:li "Hierarchical models connecting correlated surface-form evidence to latent lemma knowledge."]
    [:li "Separate guessing, slipping, wrong, and don't-know processes."]
    [:li "Calibrated item difficulty, item-response theory, and adaptive selection."]
    [:li "Multiple contexts or senses only when stable identifiers and repeated evidence make them identifiable."]]]])

;; The practical goal is to reach useful accuracy while testing as few items as
;; possible. That is not permission to claim the present scorer is optimal.
;; The current selector is balanced and non-adaptive; frequency is a proxy, not
;; calibrated item difficulty. Calibration, IRT, and adaptive testing belong to
;; later candidates, each with evidence that can fail.
;;
;; ## The research cycle, in public
;;
;; The unit of progress is not “ask an AI for the answer.” It is one versioned,
;; explainable refinement. I begin with the smallest defensible model, then
;; change one important assumption at a time. The old model remains runnable.
;; A failed candidate remains evidence rather than disappearing into a rewritten
;; story.

^:kindly/hide-code
(kind/mermaid
 "flowchart LR
    U[User expectation<br/>as a hypothesis] --> E[Explicit estimand<br/>and exclusions]
    E --> T[Relevant theory<br/>and assumptions]
    T --> B[Executable<br/>baseline]
    B --> C[One versioned<br/>candidate]
    C --> G[Precommitted<br/>validation gate]
    G --> D{All required<br/>checks pass?}
    D -->|yes| P[Promote with<br/>replay + rollback]
    D -->|no| N[Do not promote;<br/>preserve evidence]
    P --> R[Publish reasoning,<br/>code, evidence, decision]
    N --> R
    R --> L[Learner interpretation<br/>and feedback]
    L --> U")

^:kindly/hide-code
(kind/hiccup
 [:p.mw-cycle-caption
  "Agents can contribute throughout the loop. Human responsibility for the target, authority, gate, and decision does not move with the work."])

;; Three gates answer different questions:

^:kindly/hide-code
(kind/hiccup
 [:dl.mw-definition-grid
  [:div.mw-definition
   [:dt "Model validation"]
   [:dd "Does the candidate earn promotion for the declared estimand under the frozen scenarios and thresholds?"]]
  [:div.mw-definition
   [:dt "Software validation"]
   [:dd "Does the implementation behave as specified, replay deterministically, and agree across its supported runtimes?"]]
  [:div.mw-definition
   [:dt "Publication validation"]
   [:dd "Can a reader inspect the claim and evidence through working pages, controls, links, labels, layouts, and themes?"]]])

;; This separation has already mattered. The first scorer estimates an
;; independent knowing rate in each of eight frequency strata. A second
;; candidate replaced those steps with a continuous pair-frequency curve. The
;; candidate improved aggregate coverage and mean absolute error in its related
;; simulations. It still failed precommitted worst-cell coverage, worst-cell
;; error, and test-length checks. The code worked. The article rendered. The
;; model did not earn promotion, so the first version remained the target.
;;
;; That negative result is the workflow doing its job. Without a frozen gate,
;; a very productive agent—or an enthusiastic human—could select the attractive
;; aggregate numbers, retune after seeing the result, and publish a polished
;; success story. The antidote to AI slop is not merely better prose. It is an
;; evidence structure that makes inconvenient results hard to erase.
;;
;; ## What is in the public repository?
;;
;; I have published the complete worked example as the
;; [theory-to-algorithm workflow repository](https://github.com/jamiepratt/theory-to-algorithm-workflow).
;; It is both a reusable method and one inspectable vocabulary-estimation case
;; study. Clojure, Clay, Quarto, Civitas, and Git submodules are the technologies
;; used in this example; none is a requirement of the method.

^:kindly/hide-code
(kind/hiccup
 [:div.mw-table-wrap
  [:table.mw-table
   [:caption "Repository map"]
   [:thead
    [:tr [:th {:scope "col"} "Area"]
     [:th {:scope "col"} "What it contains"]
     [:th {:scope "col"} "Why it exists"]]]
   [:tbody
    [:tr
     [:th {:scope "row"} [:code "README.md"] " and " [:code "docs/workflow/"]]
     [:td "The lifecycle, validation lanes, limits, and adaptation guide."]
     [:td "A human-readable method that can be copied to another theory-to-algorithm problem."]]
    [:tr
     [:th {:scope "row"} [:code "CONTEXT.md"] " and " [:code "CONTEXT-MAP.md"]]
     [:td "Stable vocabulary, invariants, authority order, and routes to deeper context."]
     [:td "Give each fresh agent the small amount of context it must not invent."]]
    [:tr
     [:th {:scope "row"} [:code ".agents/research-workflow.json"]]
     [:td "Repository roles, article mapping, commands, commit order, and browser checks."]
     [:td "A versioned profile keeps reusable tools free of workstation-specific assumptions."]]
    [:tr
     [:th {:scope "row"} [:code ".agents/skills/"] " and GitHub templates"]
     [:td "Repeatable preview, publication, review, model-experiment, and pull-request interfaces."]
     [:td "Make important process steps executable and reviewable instead of relying on memory."]]
    [:tr
     [:th {:scope "row"} [:code "docs/language-learning/"]]
     [:td "The current scoring contract, status, decisions, and links through the evidence chain."]
     [:td "State exactly what an implementation target claims and does not claim."]]
    [:tr
     [:th {:scope "row"} [:code "clojurecivitas.github.io/"]]
     [:td "A pinned submodule containing executable articles, model code, fixtures, immutable evidence, tests, and rendering configuration."]
     [:td "Connect the public explanation to the exact publication history and evidence it describes."]]]]])

;; A new project can replace the case-study material, edit the profile, and
;; retain the lifecycle and validation separation. A curious reader can instead
;; follow the vocabulary example end to end: the current contract, the first
;; executable model, the preserved failed candidate, its deterministic evidence,
;; and the tests that protect replay.

^:kindly/hide-code
(kind/hiccup
 [:pre.mw-code
  [:code "git clone --recurse-submodules https://github.com/jamiepratt/theory-to-algorithm-workflow.git\ncd theory-to-algorithm-workflow\npython3 .agents/scripts/research_workflow.py validate"]])

;; ## Two practical influences
;;
;; [Matt Pocock](https://www.mattpocock.com/) has published an excellent
;; [full workflow walkthrough](https://www.youtube.com/watch?v=-QFHIoCo-Ko)
;; alongside his broader [YouTube channel](https://www.youtube.com/c/mattpocockuk).
;; I recommend his approach because it treats agent output as engineering work:
;; interrogate a vague request before implementation, write down the destination,
;; cut work into vertical slices that create real feedback, and use established
;; testing and review practices. The agent is a tremendously productive junior
;; developer, not an oracle and not the person accountable for the system.
;;
;; [Kun Chen's](https://www.youtube.com/channel/UCb69t9ZkE5z1KvCmfJoaifA)
;; [agentic-engineering walkthrough](https://www.youtube.com/watch?v=iQyg-KypKAA)
;; is another important influence. His captain-and-crew framing takes the
;; management analogy seriously: invest human attention at the beginning in
;; deciding and explaining what should be built, delegate implementation across
;; isolated agents, and invest again at the end in validation. His emphasis on
;; visual artefacts is especially useful when agents can generate information
;; faster than a human can review walls of text.
;;
;; I am not trying to reproduce either workflow ritual for ritual. The durable
;; lesson is to design the organisation around the agents' actual strengths and
;; weaknesses. Requirements, context, tools, isolation, feedback, validation,
;; versioned evidence, and rollback are the management system.
;;
;; ## The work ahead
;;
;; We have suddenly gained access to a strange abundance of fast intellectual
;; labour. The temptation is to ask for more output. The interesting work is to
;; build better judgment around that output: decide what deserves to exist,
;; clarify what it should mean, construct evidence that can contradict us, and
;; preserve the reasoning so another person can inspect it.
;;
;; That is why I am doing this research cycle in public. The vocabulary scorer
;; is useful in its own right, but it is also a demanding test of the broader
;; workflow. It forces natural-language expectations, linguistic categories,
;; statistical theory, code, evidence, user communication, and operational
;; decisions into contact. Every hidden assumption that becomes visible is
;; progress. Every candidate that fails honestly is progress too.
;;
;; I am excited by the minds now available to us, by Christensen's warning that
;; good old-world management can miss a new value network, and by the new job
;; of learning how to manage this capability well. The standard should not be
;; whether an agent produced something astonishingly quickly. It should be
;; whether humans and agents together produced something worth trusting.
;;
;; ## Sources and further reading
;;
;; - Clayton M. Christensen,
;;   [*The Innovator's Dilemma*](https://store.hbr.org/product/the-innovator-s-dilemma-with-a-new-foreword-when-new-technologies-cause-great-firms-to-fail/10706),
;;   Harvard Business Review Press, for the book's central argument and publisher-recorded reception.
;; - [Harvard Business School's account of Christensen's work and influence](https://www.hbs.edu/news/releases/clayton-christensen-obituary),
;;   including the book's New York Times bestseller status and 1997 Global Business Book Award.
;; - Si and Chen,
;;   [“A literature review of disruptive innovation: What it is, how it works and where it goes”](https://www.sciencedirect.com/science/article/pii/S0923474820300163),
;;   for the theory's influence, refinements, disputes, and frequent misuse.
;; - Woliński et al.,
;;   [“The Online Version of Grammatical Dictionary of Polish”](https://aclanthology.org/L16-1412/),
;;   and the [dictionary itself](https://sgjp.pl/about/), for explicit Polish lexeme and inflection data.
;; - [The public theory-to-algorithm workflow](https://github.com/jamiepratt/theory-to-algorithm-workflow),
;;   including its current scoring contract, workflow docs, complete case study, and adaptation guide.
;; - [Bayes' theorem from uncertainty to decision](bayes_theorem_simulations.html),
;;   [the stratified Beta–binomial first pass](beta_binomial_first_pass.html), and
;;   [the continuous-frequency candidate and non-promotion decision](pair_frequency_logistic_v2_article.html).
