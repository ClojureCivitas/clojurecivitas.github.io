^{:kindly/hide-code true
  :clay {:quarto {:title "Understanding Cancer Through Computation"
                  :description "Using computational art to understand cancer in the context of evolutionary developmental."
                  :draft true
                  :type :post
                  :date "2026-02-27"
                  :author "Benjamin Kamphaus"
                  :category :biology
                  :tags [:clojure :biology :creative-coding :science]
                  ;; :bibliography "references.bib"
                  }}}
(ns biodecahedron.civitas.cancer-through-computation
  (:require [scicloj.clay.v2.api :as clay]))


;; ## Understanding Cancer Through Computation
;;
;; In this talk, I will focus on explaining cancer as a systems phenomenon:
;; that is, an event which occurs in the context of multiple interacting parts.
;; Those parts interaction in a dynamic environment we have come to call 'multicellularity',
;; an 'organism', or even at times, names which encapsulate an even larger systems concept,
;; like 'holobiont' [@meyer1943typologische; @baedke2020holobiont; @margulis1990words].
;;
;; In my view the process of oncogenesis is best understood as an evolutionary process.
;; Not only in terms of cancer cells evolving genetically through selection,
;; but many other analogies and angles are helpful, from historical biogeography to
;; eco-evolutionary dynamics. I am certainly not the first to make this case
;; [@casas2011cancer; @chroni2021tumors]; it's been applied as a guiding treatment philosophy
;; in the clinic [@gatenby2020integrating]; @metts2024roadmap].
;;
;; One thing that I have found many computer scientists and programmers
;; don't realize, though, is that the field of evolutionary development in biology
;; (abbreviated with the obnoxiously cute shorthand 'Evo Devo'),
;; has greatly shaken up our understanding of the molecular drivers of the evolution of
;; multicellularity in the past several decades. This field has added
;; another ingredient to evolution, by fleshing out what had previously been
;; described as 'developmental constraint'
;; by Stephen J. Gould and others [@gould2020spandrels]. In these past decades, it came as
;; a surprise to biologists (but it should not be a surprise to
;; computer scientists, I don't think) that the same genes
;; are responsible for laying out the body plans of both vertebral and
;; arthropod lineages [@shubin2009deep], and most of these trace back to the core of the
;; animal lineage, or even to our single celled ancestors.
;; As we've sequenced genomes for many, many species,
;; more and more traits which appear distinct have turned out to be different products
;; of these same gene programs, often only slightly tweaked.
;;
;; If a programmer had been involved in formulating this theory,
;; we'd call this
;; 'developmental capacity', or 'developmental power,' not constraint.
;; These powerful gene toolkits have been favored and preserved by selection.
;; And remarkable adaptive radiations seen in the fossil record have been correlated
;; with changes in the expressive capacity of the gene toolkits. For instance,
;; the Cambrian explosion -- an emergence of previously unparalleled diversity
;; of animal forms -- was enabled by two whole genome doublings at the root
;; of the vertebral lineage, termed 1R/2R [@dehal2005two].
;;
;; And before anyone thinks I'm losing the plot here, the legacy of these
;; doublings underlies the progression of human cancers. _KRAS_, _NRAS_,
;; and _HRAS_, three of the most potent oncogenes, are homologous; we
;; trace their separation  back to this same event [@garcia2023origin].
;;
;; There's a long history of using fruit fly experiments
;; to elucidate components of the formation of body plans piecewise.
;; Drosophila geneticists have amassed amazing diagrams of gene regulatory networks and
;; signaling coordination [@davidson2002genomic]. Extending this work across organisms has shown the
;; exact molecular mechanisms fulfilling signaling processes: mechanisms that form patterns proposed
;; in the abstract by Turing and mathematicians before him [@turing1990chemical; @thompson1917growth].
;; We now know the molecules responsible
;; for striping patterns, fin and limb formation, and organ growth; these gene programs
;; provide both geometric alterations of layouts, and higher-order changes, like repeats and cycles.
;;
;; In this talk and article, however, I want to dig into something else:
;; One of the trickiest things to unpack in cancer is the loss of
;; the maintenance of cell fate. All these body plans -- these 'endless forms
;; most beautiful' -- to use Darwin's language [@darwin1859origin], as well as the title of
;; Sean B. Carrol's book on Evo Devo for popular audiences [@carroll2005endless] -- they are all
;; laid out by specific cell populations, different embryonic lineages, all stemming
;; from a single cell. These cell lineages are not genetic lineages, but emerge through
;; a series of filters and transformations applied over their genome --
;; the same genome they share with all other cells of the multicellular creatures
;; whose form they work together to express. And these
;; cells achieve their unique state through the regulation of gene
;; transcription.
;;
;; This process homology goes even deeper than anatomy. Roots of it are found
;; in the gene kit which allows colonies of single cell eukaryotes to form
;; different niches and signaling networks [@bylino2020evolution]. But these processes
;; are harder to see. They are not preserved as clearly in the fossil record;
;; their physical manifestations are often only apparent far downstream of their immediate
;; impacts, or through interactions with genes which drive more obvious
;; physical morphology. And the genes which drive these transcription regulating programs --
;; such as those of the Polycomb Repressor Complexes -- are significantly perturbed or
;; altered in the most aggressive and hard to treat cancers [@parreno2022mechanisms].
;;
;; We, however, can do better than gross anatomy. Computational art provides an
;; effective canvas upon which to illustrate the impact of these perturbations on
;; cell fate, thereby making the invisible visible. And the REPL provides us with
;; a rapid feedback loop for our process experimentation, one that would be the envy of any
;; Drosophila geneticist.
;;
;; Let's get started!
;;
;; ## Computational Art As a Window Into Biological Processes
;; ### Selection on Simple Traits (And its Limits)
;; ### Interactions, Circuits, Networks, Programs, and Higher Order Generation
;; ### Duplication, Divergence, Repurposing, and Optionality
;; ### Stem Cells & Differentiation
;; ### Lineage Fidelity: The Polycomb Repressor Complexes
;; ### Growing New Organs No One Asked For
;; ### Signaling, Tumor Ecology, and Microenvironments
;; ### Phenotypic Plasticity, Invasion, Metastasis and Treatment Resistance
;; ## Conclusion
;;

^:kindly/hide-code
(comment
  (clay/make! {:source-path "src/biodecahedron/civitas/cancer_through_computation.clj"
               :format [:quarto :html]}))
