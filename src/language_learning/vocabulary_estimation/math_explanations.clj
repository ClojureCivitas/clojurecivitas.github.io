(ns language-learning.vocabulary-estimation.math-explanations
  (:require [scicloj.kindly.v4.kind :as kind]))

(defn styles []
  (kind/hiccup
   [:style
    (str
     ".article-explanations-toolbar{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:.8rem;min-width:0;border:1px solid color-mix(in srgb,var(--bs-body-color,#212529) 22%,transparent);border-left:4px solid #2780e3;border-radius:.35rem;padding:.8rem 1rem;margin:1.25rem 0;background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 88%,#2780e3 12%);color:var(--bs-body-color,#212529)}"
     ".article-explanations-toolbar p{min-width:0;margin:0;overflow-wrap:anywhere}"
     ".article-explanations-toolbar strong{color:inherit}"
     ".article-explanations-toggle,.article-code-toggle{border:1px solid #2780e3;border-radius:.35rem;padding:.5rem .8rem;font-weight:700;cursor:pointer;background:var(--bs-body-bg,#fff);color:var(--bs-body-color,#212529)}"
     ".article-explanations-toggle[aria-pressed=true],.article-code-toggle[aria-pressed=true]{background:#1464b5;color:#fff}"
     ".article-explanations-toggle:focus-visible,.article-code-toggle:focus-visible{outline:3px solid color-mix(in srgb,#2780e3 50%,transparent);outline-offset:3px}"
     ".article-help-icon:focus-visible,.article-explanation summary:focus-visible,.article-code-detail summary:focus-visible{outline:3px solid color-mix(in srgb,var(--explanation-accent,#2780e3) 55%,transparent);outline-offset:3px}"
     ".article-explanations-description,.article-explanations-status,.article-code-status{display:block;margin-top:.2rem;font-size:.84rem;color:#3f4b55}"
     ".article-reading-action{display:grid;gap:.2rem;min-width:min(100%,12rem)}"
     ".article-code-detail{min-width:0;margin:1rem 0;border:1px solid color-mix(in srgb,#6f42c1 45%,var(--bs-border-color,#dee2e6));border-radius:.55rem;background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 94%,#6f42c1 6%);color:var(--bs-body-color,#212529)}"
     "details.article-code-detail>summary{padding:.72rem .85rem;font-weight:750;cursor:pointer;color:var(--bs-body-color,#212529)!important;overflow-wrap:anywhere}"
     ".article-code-detail[open] summary{border-bottom:1px solid color-mix(in srgb,#6f42c1 35%,transparent)}"
     ".article-code-detail-body{min-width:0;padding:.8rem .9rem 1rem}"
     ".article-code-detail-body>*:first-child{margin-top:0}.article-code-detail-body>*:last-child{margin-bottom:0}"
     ".article-code-detail pre{max-width:100%;overflow:auto;margin:.75rem 0;padding:.75rem;border-radius:.4rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 84%,var(--bs-body-color,#212529) 16%)}"
     ".article-code-detail code{white-space:pre-wrap;overflow-wrap:anywhere}"
     ".article-code-source{font-size:.86rem}"
     ".article-chapter-map{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,11rem),1fr));gap:.65rem;margin:1.25rem 0;padding:0;list-style:none;counter-reset:chapter-map}"
     ".article-chapter-map li{min-width:0;border:1px solid var(--bs-border-color,#dee2e6);border-radius:.5rem;padding:.75rem;background:var(--bs-body-bg,#fff);overflow-wrap:anywhere;counter-increment:chapter-map}"
     ".article-chapter-map li::before{content:counter(chapter-map);display:grid;place-items:center;width:1.6rem;height:1.6rem;margin-bottom:.45rem;border-radius:50%;background:#1464b5;color:#fff;font-weight:800}"
     ".article-marker{display:inline-block;margin:.2rem .35rem .2rem 0;border-radius:999px;padding:.18rem .58rem;background:color-mix(in srgb,var(--bs-body-bg,#fff) 82%,#2780e3 18%);color:var(--bs-body-color,#212529);font-size:.78rem;font-weight:800;letter-spacing:.03em;text-transform:uppercase}"
     ".article-recap{min-width:0;border:1px solid color-mix(in srgb,#0f695f 45%,var(--bs-border-color,#dee2e6));border-left:4px solid #0f695f;border-radius:.5rem;padding:1rem 1.1rem;margin:1.35rem 0;background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 91%,#0f695f 9%);color:var(--bs-body-color,#212529)}"
     ".article-explanation-layout,.article-explanation-anchor,.article-explanation-slot{min-width:0}"
     ".article-explanation-layout{margin:0 0 .35rem}"
     ".article-help-icon{--explanation-accent:#155f9f;display:inline-grid;place-items:center;width:1.2rem;height:1.2rem;margin-left:.25rem;padding:0;border:1px solid var(--explanation-accent);border-radius:50%;background:var(--bs-body-bg,#fff);color:var(--explanation-accent);font:800 .75rem/1 system-ui,sans-serif;vertical-align:.14em;cursor:pointer}"
     ".article-help-icon.lexical{--explanation-accent:#0f695f}"
     ".article-help-icon.design{--explanation-accent:#5b3f8f}"
     ".article-help-icon.accent-2,.article-explanation.accent-2{--explanation-accent:#a24700}"
     ".article-help-icon.accent-3,.article-explanation.accent-3{--explanation-accent:#7d3a9c}"
     ".article-help-icon.accent-4,.article-explanation.accent-4{--explanation-accent:#0f695f}"
     ".article-help-icon.accent-5,.article-explanation.accent-5{--explanation-accent:#9a2f5f}"
     ".article-help-icon.accent-6,.article-explanation.accent-6{--explanation-accent:#6c5b00}"
     ".article-help-icon[aria-expanded=true]{background:var(--explanation-accent);color:#fff}"
     ".article-explanation-slot{display:grid;gap:.6rem}"
     ".article-explanation{--explanation-accent:#155f9f;display:none;min-width:0;margin:0}"
     ".article-explanation.term-explanation.lexical{--explanation-accent:#0f695f}"
     ".article-explanation.term-explanation.design{--explanation-accent:#5b3f8f}"
     ".article-explanation[open]{display:block;border:2px solid var(--explanation-accent);border-radius:.55rem;background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 92%,var(--explanation-accent) 8%);color:var(--bs-body-color,#212529);box-shadow:0 0 0 3px color-mix(in srgb,var(--explanation-accent) 14%,transparent);cursor:pointer}"
     ".article-explanation summary{display:block;max-width:100%;border-bottom:1px solid color-mix(in srgb,var(--explanation-accent) 45%,transparent);padding:.62rem .75rem;font-weight:700;cursor:pointer;color:var(--bs-body-color,#212529);background:transparent;overflow-wrap:anywhere}"
     ".article-explanation-term{font-weight:750;color:inherit;white-space:normal;overflow-wrap:anywhere}"
     ".article-explanation-body{min-width:0;padding:.7rem .75rem .8rem}"
     ".article-explanation-body p{min-width:0;margin:0;overflow-wrap:anywhere}"
     ".article-explanation.is-relevant{box-shadow:0 0 0 4px color-mix(in srgb,var(--explanation-accent) 32%,transparent)}"
     ".article-explanation-rail{display:none}"
     ".article-explanation-rail-guide{display:none}"
     ".article-explanation-rail-guide p{margin:.3rem 0 .65rem;font-size:.82rem;line-height:1.4}"
     ".article-explanation-hide-all{width:100%;border:1px solid #1464b5;border-radius:.35rem;padding:.45rem .65rem;background:#1464b5;color:#fff;font-weight:700;cursor:pointer}"
     ".article-explanation-hide-all:focus-visible{outline:3px solid color-mix(in srgb,#2780e3 50%,transparent);outline-offset:3px}"
     ".quarto-dark .article-help-icon,.quarto-dark .article-explanation{--explanation-accent:#73b7ff}"
     ".quarto-dark .article-help-icon.lexical,.quarto-dark .article-explanation.term-explanation.lexical{--explanation-accent:#40c9b7}"
     ".quarto-dark .article-help-icon.design,.quarto-dark .article-explanation.term-explanation.design{--explanation-accent:#b79ae5}"
     ".quarto-dark .article-help-icon.accent-2,.quarto-dark .article-explanation.accent-2{--explanation-accent:#ffad66}"
     ".quarto-dark .article-help-icon.accent-3,.quarto-dark .article-explanation.accent-3{--explanation-accent:#d7a6f2}"
     ".quarto-dark .article-help-icon.accent-4,.quarto-dark .article-explanation.accent-4{--explanation-accent:#40c9b7}"
     ".quarto-dark .article-help-icon.accent-5,.quarto-dark .article-explanation.accent-5{--explanation-accent:#ff8fbd}"
     ".quarto-dark .article-help-icon.accent-6,.quarto-dark .article-explanation.accent-6{--explanation-accent:#e2cf5b}"
     ".quarto-dark .article-explanations-description,.quarto-dark .article-explanations-status,.quarto-dark .article-code-status{color:#b9c7d2}"
     ".quarto-dark .article-help-icon[aria-expanded=true]{color:#10212b}"
     "#quarto-document-content{transition:transform .2s ease}"
     "@media(min-width:1280px){"
     "body.article-explanations-open #quarto-document-content{transform:translateX(-8rem)}"
     ".article-explanation-layout{position:relative}"
     ".article-explanation-rail{position:fixed;left:calc(50% + 17.25rem);top:5rem;z-index:2;display:block;width:20rem;height:calc(100dvh - 5rem);overflow-y:auto;overscroll-behavior:contain;scrollbar-width:thin;pointer-events:none}"
     ".article-explanation-rail.has-open{pointer-events:auto}"
     ".article-explanation-rail.has-open>.article-explanation-rail-guide{position:sticky;top:.4rem;z-index:4;display:block;margin:0 0 .7rem;border:1px solid color-mix(in srgb,#2780e3 55%,var(--bs-border-color,#dee2e6));border-radius:.55rem;padding:.7rem .75rem;background:var(--bs-body-bg,#fff);background:color-mix(in srgb,var(--bs-body-bg,#fff) 88%,#2780e3 12%);color:var(--bs-body-color,#212529);box-shadow:0 .2rem .65rem color-mix(in srgb,#000 18%,transparent)}"
     ".article-explanation-rail-stack{display:grid;gap:.6rem;min-width:0;padding:50vh 0}"
     ".equation-help-anchor{position:relative}"
     ".equation-help-anchor>.equation-help-icon{position:absolute;left:calc(100% + .15rem);top:50%;z-index:3;margin:0;transform:translateY(-50%)}"
     "}"
     "@media(max-width:1279px){.article-explanation-slot.has-open{margin:.55rem 0 1.25rem}.equation-help-icon{display:grid;margin:.35rem auto 0}}"
     "@media(max-width:767px){.article-explanations-toolbar{grid-template-columns:minmax(0,1fr);align-items:stretch}.article-explanations-toggle,.article-code-toggle{width:100%}.article-reading-action{width:100%}}")]))

(def ^:private explanation-controls-script
  "(() => {
  const initialise = () => {
    const globalButton = document.getElementById('article-explanations-toggle');
    if (!globalButton || globalButton.dataset.initialized === 'true') return;

    const articleMain = document.getElementById('quarto-document-content') || document.querySelector('main');
    const marginQuery = window.matchMedia('(min-width: 1280px)');
    const mathRegistries = Array.from(document.querySelectorAll('.math-explanation-registry'));
    const termRegistries = Array.from(document.querySelectorAll('.term-explanation-registry'));
    const codeDetails = Array.from(document.querySelectorAll('details.article-code-detail'));
    const codeAction = document.getElementById('article-code-action');
    const codeButton = document.getElementById('article-code-toggle');
    const codeStatus = document.getElementById('article-code-status');

    const makeHelpButton = ({label, controls, classes = []}) => {
      const help = document.createElement('button');
      help.type = 'button';
      help.className = ['article-help-icon', ...classes].join(' ');
      help.textContent = '?';
      help.setAttribute('aria-label', label);
      help.setAttribute('title', label);
      help.setAttribute('aria-controls', controls.join(' '));
      help.setAttribute('aria-expanded', 'false');
      return help;
    };

    const ensureLayout = (anchor) => {
      const existing = anchor.closest('.article-explanation-layout');
      if (existing && existing.querySelector(':scope > .article-explanation-anchor') === anchor) {
        return existing;
      }
      const layout = document.createElement('div');
      layout.className = 'article-explanation-layout';
      const slot = document.createElement('div');
      slot.className = 'article-explanation-slot';
      anchor.classList.add('article-explanation-anchor');
      anchor.parentElement.insertBefore(layout, anchor);
      layout.appendChild(anchor);
      layout.appendChild(slot);
      return layout;
    };

    const layoutSlot = (layout) => layout.querySelector(':scope > .article-explanation-slot');
    const containsDisplayEquation = (node) => Boolean(
      node && node.querySelector('span.math.display, mjx-container[display=true]')
    );
    const precedingEquation = (registry) => {
      let cursor = registry;
      for (let depth = 0; cursor && cursor.parentElement && depth < 4; depth += 1) {
        let candidate = cursor.previousElementSibling;
        while (candidate) {
          if (containsDisplayEquation(candidate)) return candidate;
          candidate = candidate.previousElementSibling;
        }
        if (cursor.parentElement.matches('section')) break;
        cursor = cursor.parentElement;
      }
      return null;
    };

    mathRegistries.forEach((registry, registryIndex) => {
      const items = Array.from(registry.querySelectorAll(':scope > details.article-explanation'));
      const anchor = precedingEquation(registry);
      if (!anchor || items.length === 0) return;
      const accentClass = `accent-${(registryIndex % 6) + 1}`;
      const layout = ensureLayout(anchor);
      layout.classList.add('equation-explanation-layout');
      const slot = layoutSlot(layout);
      items.forEach((item) => {
        item.classList.add(accentClass);
        slot.appendChild(item);
      });
      anchor.classList.add('equation-help-anchor');
      const title = registry.dataset.explanationTitle || 'this equation';
      const help = makeHelpButton({
        label: `Explain ${title}`,
        controls: items.map((item) => item.id),
        classes: ['equation-help-icon', accentClass]
      });
      anchor.appendChild(help);
      registry.remove();
    });

    const excludedTextNode = (node) => Boolean(node.parentElement && node.parentElement.closest(
      'script,style,pre,code,svg,details,.article-explanations-toolbar,.article-explanation-registry,.article-help-icon'
    ));
    const normaliseTerm = (text) => text.toLocaleLowerCase().replace(/[‐‑‒–—]/g, '-');
    const isBoundary = (character) => !character || !/[A-Za-z0-9‐‑‒–—-]/.test(character);
    const firstTermMatch = (root, text) => {
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
      const needle = normaliseTerm(text);
      let node;
      while ((node = walker.nextNode())) {
        if (excludedTextNode(node)) continue;
        const haystack = normaliseTerm(node.nodeValue || '');
        let index = haystack.indexOf(needle);
        while (index >= 0) {
          const afterIndex = index + needle.length;
          const plural = haystack[afterIndex] === 's' && isBoundary(haystack[afterIndex + 1]);
          if (isBoundary(haystack[index - 1]) && (isBoundary(haystack[afterIndex]) || plural)) {
            return {node, index, length: needle.length + (plural ? 1 : 0)};
          }
          index = haystack.indexOf(needle, index + 1);
        }
      }
      return null;
    };
    const anchorBlockFor = (node) => node.parentElement?.closest('p,blockquote,li,figcaption,h2,h3,h4') || node.parentElement;
    const appendInlineHelp = (match, help) => {
      const after = match.node.splitText(match.index + match.length);
      after.parentNode.insertBefore(help, after);
    };

    termRegistries.forEach((registry) => {
      const items = Array.from(registry.querySelectorAll(':scope > details.article-explanation'));
      const searchRoot = registry.closest('section') || articleMain;
      items.forEach((item) => {
        const anchorText = item.dataset.anchorTerm || item.dataset.helpLabel || '';
        const match = firstTermMatch(searchRoot, anchorText);
        const fallbackAnchor = registry.previousElementSibling;
        const anchor = match ? anchorBlockFor(match.node) : fallbackAnchor;
        if (!anchor) return;
        const layout = ensureLayout(anchor);
        layoutSlot(layout).appendChild(item);
        const helpLabel = item.dataset.helpLabel || anchorText;
        const category = item.classList.contains('lexical') ? 'lexical' : 'design';
        const help = makeHelpButton({
          label: `Explain ${helpLabel}`,
          controls: [item.id],
          classes: ['term-help-icon', category]
        });
        if (match) appendInlineHelp(match, help);
        else anchor.appendChild(help);
      });
      registry.remove();
    });

    const explanations = Array.from(document.querySelectorAll('details.article-explanation'));
    const mathExplanations = explanations.filter((item) => item.classList.contains('math-explanation'));
    const terminologyExplanations = explanations.filter((item) => item.classList.contains('term-explanation'));
    const helpButtons = Array.from(document.querySelectorAll('.article-help-icon'));
    const heading = document.getElementById('article-explanations-heading');
    const description = document.getElementById('article-explanations-description');
    const status = document.getElementById('article-explanations-status');
    const itemOrigins = new Map();
    const itemAnchors = new Map();
    explanations.forEach((item) => {
      const slot = item.closest('.article-explanation-slot');
      const layout = slot?.closest('.article-explanation-layout');
      itemOrigins.set(item, slot);
      itemAnchors.set(item, layout?.querySelector(':scope > .article-explanation-anchor') || null);
    });
    const rail = document.createElement('aside');
    rail.className = 'article-explanation-rail';
    rail.setAttribute('aria-label', 'Expanded explanations');
    const railGuide = document.createElement('section');
    railGuide.className = 'article-explanation-rail-guide';
    const railGuideHeading = document.createElement('strong');
    railGuideHeading.textContent = 'Using explanation cards';
    const railGuideText = document.createElement('p');
    railGuideText.textContent = 'Cards relevant to the text in view scroll into place automatically. You can also scroll these cards independently. Select an open card to hide it.';
    const railHideAll = document.createElement('button');
    railHideAll.type = 'button';
    railHideAll.className = 'article-explanation-hide-all';
    railHideAll.textContent = 'Hide all explanations';
    railGuide.appendChild(railGuideHeading);
    railGuide.appendChild(railGuideText);
    railGuide.appendChild(railHideAll);
    const railStack = document.createElement('div');
    railStack.className = 'article-explanation-rail-stack';
    rail.appendChild(railGuide);
    rail.appendChild(railStack);
    document.body.appendChild(rail);

    const controlledItems = (help) => (help.getAttribute('aria-controls') || '')
      .split(/\\s+/)
      .filter(Boolean)
      .map((id) => document.getElementById(id))
      .filter(Boolean);
    const setOpen = (item, open) => {
      item.open = open;
    };
    const layouts = () => Array.from(document.querySelectorAll('.article-explanation-layout'));
    const arrangeForViewport = () => {
      if (marginQuery.matches) {
        explanations.forEach((item) => railStack.appendChild(item));
      } else {
        explanations.forEach((item) => itemOrigins.get(item)?.appendChild(item));
      }
    };
    const relevantOpenItem = () => {
      const viewportCentre = window.innerHeight / 2;
      let relevant = null;
      let nearestDistance = Number.POSITIVE_INFINITY;
      const consideredAnchors = new Set();
      explanations.forEach((item) => {
        if (!item.open) return;
        const anchor = itemAnchors.get(item);
        if (!anchor || consideredAnchors.has(anchor)) return;
        consideredAnchors.add(anchor);
        const rect = anchor.getBoundingClientRect();
        const distance = Math.abs((rect.top + rect.bottom) / 2 - viewportCentre);
        if (distance < nearestDistance) {
          relevant = item;
          nearestDistance = distance;
        }
      });
      return relevant;
    };
    const centreRelevantExplanation = () => {
      explanations.forEach((item) => item.classList.remove('is-relevant'));
      if (!marginQuery.matches) return;
      const relevant = relevantOpenItem();
      if (!relevant) return;
      relevant.classList.add('is-relevant');
      const railRect = rail.getBoundingClientRect();
      const viewportCentreWithinRail = window.innerHeight / 2 - railRect.top;
      const targetScroll = relevant.offsetTop + relevant.offsetHeight / 2 - viewportCentreWithinRail;
      rail.scrollTop = Math.max(0, targetScroll);
    };
    let placementFrame = null;
    const schedulePlacement = () => {
      if (placementFrame !== null) return;
      placementFrame = window.requestAnimationFrame(() => {
        placementFrame = null;
        centreRelevantExplanation();
      });
    };
    const sync = () => {
      const openItems = explanations.filter((item) => item.open);
      const allOpen = explanations.length > 0 && openItems.length === explanations.length;
      const openMath = mathExplanations.filter((item) => item.open).length;
      const openTerms = terminologyExplanations.filter((item) => item.open).length;

      helpButtons.forEach((help) => {
        const targets = controlledItems(help);
        help.setAttribute('aria-expanded', String(targets.length > 0 && targets.every((item) => item.open)));
      });
      layouts().forEach((layout) => {
        const slot = layoutSlot(layout);
        const hasOpen = explanations.some((item) => item.open && itemOrigins.get(item) === slot);
        if (slot) slot.classList.toggle('has-open', hasOpen);
      });
      rail.classList.toggle('has-open', openItems.length > 0);
      document.body.classList.toggle('article-explanations-open', openItems.length > 0);
      if (articleMain) articleMain.classList.toggle('article-explanations-open', openItems.length > 0);
      globalButton.setAttribute('aria-pressed', String(allOpen));
      globalButton.textContent = `${allOpen ? 'Hide' : 'Show'} all help`;
      if (heading) heading.textContent = 'Reading controls';
      const bothKinds = mathExplanations.length > 0 && terminologyExplanations.length > 0;
      const countParts = [];
      const progressParts = [];
      if (mathExplanations.length > 0) {
        countParts.push(`${mathExplanations.length} mathematical item${mathExplanations.length === 1 ? '' : 's'}`);
        progressParts.push(`${openMath} of ${mathExplanations.length} mathematical item${mathExplanations.length === 1 ? '' : 's'}`);
      }
      if (terminologyExplanations.length > 0) {
        countParts.push(`${terminologyExplanations.length} terminology item${terminologyExplanations.length === 1 ? '' : 's'}`);
        progressParts.push(`${openTerms} of ${terminologyExplanations.length} terminology item${terminologyExplanations.length === 1 ? '' : 's'}`);
      }
      if (description) {
        const helpInstruction = bothKinds
          ? 'Use the ? beside a term or equation for optional explanations.'
          : mathExplanations.length > 0
            ? 'Use the ? beside an equation for optional explanations.'
            : 'Use the ? beside a term for an optional definition.';
        description.textContent = codeDetails.length > 0
          ? `${helpInstruction} Code details remain inline and independent.`
          : helpInstruction;
      }
      if (status) {
        status.textContent = openItems.length === 0
          ? `${countParts.join(' and ')} ${explanations.length === 1 ? 'is' : 'are'} hidden.`
          : `${progressParts.join(' and ')} shown.`;
      }

      const openCodeCount = codeDetails.filter((item) => item.open).length;
      const allCodeOpen = codeDetails.length > 0 && openCodeCount === codeDetails.length;
      if (codeAction) codeAction.hidden = codeDetails.length === 0;
      if (codeButton) {
        codeButton.setAttribute('aria-pressed', String(allCodeOpen));
        codeButton.textContent = allCodeOpen ? 'Hide all code details' : 'Show all code details';
      }
      if (codeStatus) codeStatus.textContent = `${openCodeCount}/${codeDetails.length} code details open.`;
    };

    globalButton.addEventListener('click', () => {
      const shouldOpen = !explanations.every((item) => item.open);
      explanations.forEach((item) => setOpen(item, shouldOpen));
      sync();
      schedulePlacement();
    });
    if (codeButton) {
      codeButton.addEventListener('click', () => {
        const shouldOpen = !codeDetails.every((item) => item.open);
        codeDetails.forEach((item) => setOpen(item, shouldOpen));
        sync();
      });
    }
    railHideAll.addEventListener('click', () => {
      explanations.forEach((item) => setOpen(item, false));
      sync();
      schedulePlacement();
    });
    helpButtons.forEach((help) => {
      help.addEventListener('click', () => {
        const targets = controlledItems(help);
        const shouldOpen = targets.some((item) => !item.open);
        targets.forEach((item) => setOpen(item, shouldOpen));
        sync();
        schedulePlacement();
      });
    });
    explanations.forEach((item) => {
      item.addEventListener('click', (event) => {
        const interactive = event.target instanceof Element && event.target.closest('a,button,input,select,textarea');
        if (!item.open || interactive) return;
        event.preventDefault();
        setOpen(item, false);
        sync();
        schedulePlacement();
      });
      item.addEventListener('toggle', () => {
        sync();
        schedulePlacement();
      });
    });
    codeDetails.forEach((item) => item.addEventListener('toggle', sync));
    marginQuery.addEventListener('change', () => {
      arrangeForViewport();
      sync();
      schedulePlacement();
    });
    window.addEventListener('scroll', schedulePlacement, {passive: true});
    window.addEventListener('resize', schedulePlacement);
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(schedulePlacement);
    window.setTimeout(schedulePlacement, 250);
    window.setTimeout(schedulePlacement, 1000);
    globalButton.dataset.initialized = 'true';
    arrangeForViewport();
    sync();
    schedulePlacement();
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialise, {once: true});
  } else {
    initialise();
  }
})();")

(defn global-controls []
  (kind/hiccup
   [:div.article-explanations-toolbar
    {:role "region" :aria-labelledby "article-explanations-heading"}
    [:p
     [:strong#article-explanations-heading "Reading controls"]
     [:span.article-explanations-description
      {:id "article-explanations-description"}
      "Help explains symbols and terms. Code details show how the examples were built. These controls are independent."]
     [:span.article-explanations-status
      {:id "article-explanations-status" :aria-live "polite"}
      "All explanation items are hidden by default."]]
    [:div.article-reading-action
     [:button.article-explanations-toggle
      {:id "article-explanations-toggle"
       :type "button"
       :aria-pressed "false"
       :aria-describedby "article-explanations-description article-explanations-status"}
      "Show all help"]]
    [:div.article-reading-action
     {:id "article-code-action"}
     [:button.article-code-toggle
      {:id "article-code-toggle"
       :type "button"
       :aria-pressed "false"
       :aria-describedby "article-explanations-description article-code-status"}
      "Show all code details"]
     [:span.article-code-status
      {:id "article-code-status" :aria-live "polite"}
      "0/0 code details open."]]
    [:script explanation-controls-script]]))

(defn code-detail
  "Render one closed, inline implementation detail with an independent native
  disclosure control. Body is arbitrary Hiccup, so callers may mix prose,
  preformatted code, and a source link."
  [id purpose body]
  (let [summary-id (str id "--summary")
        body-id (str id "--body")]
    (kind/hiccup
     [:details.article-code-detail
      {:id id}
      [:summary
       {:id summary-id :aria-controls body-id}
       (str "Code detail: " purpose)]
      [:div.article-code-detail-body
       {:id body-id :role "region" :aria-labelledby summary-id}
       body]])))

(defn- math-item
  [id term definition]
  [:details.article-explanation.math-explanation
   {:id id :data-help-label term}
   [:summary
    [:code.article-explanation-term term]]
   [:div.article-explanation-body
    [:p definition]]])

(defn explanation
  ([id title terms]
   (explanation id title terms nil))
  ([id title terms note]
   (kind/hiccup
    (into
     [:div.article-explanation-registry.math-explanation-registry
      {:data-explanation-group id :data-explanation-title title}]
     (concat
      (map-indexed (fn [index [term definition]]
                     (math-item (str id "--term-" (inc index))
                                term definition))
                   terms)
      (when note
        [[:details.article-explanation.math-explanation.equation-context
          {:id (str id "--context") :data-help-label "Equation context"}
          [:summary
           [:span.article-explanation-term "About this equation"]]
          [:div.article-explanation-body
           [:p note]]]]))))))

(defn terminology
  [id category _label title terms]
  (kind/hiccup
   (into
    [:div.article-explanation-registry.term-explanation-registry
     {:data-explanation-group id :data-explanation-title title}]
    (map-indexed
     (fn [index [term definition anchor-term]]
       [:details
        {:id (str id "--term-" (inc index))
         :class (str "article-explanation term-explanation " (name category))
         :data-anchor-term (or anchor-term term)
         :data-help-label term}
        [:summary
         [:span.article-explanation-term term]]
        [:div.article-explanation-body
         [:p definition]]])
     terms))))
