(() => {
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
      .split(/\s+/)
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
})();
