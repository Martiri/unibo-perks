/**
 * Main Application Controller — UniBo & Student Perks Vault
 *
 * Security invariants:
 * - Zero innerHTML / outerHTML / eval
 * - All DOM nodes created via `el()` and `svgIcon()`
 * - All user secrets remain in IndexedDB/localStorage on device
 */

import { el, svgIcon, clearChildren, sanitizeExternalUrl } from './dom-safe.js';
import { vaultDB } from './db.js';
import { createBackupPayload, parseBackupPayload } from './crypto-backup.js';

const STATUS_META = {
  new: { label: 'Nuovo', badgeClass: 'status-new', icon: 'spark' },
  unlocked: { label: 'Sbloccato', badgeClass: 'status-unlocked', icon: 'unlock' },
  used: { label: 'Usato', badgeClass: 'status-used', icon: 'check' },
  hidden: { label: 'Nascosto', badgeClass: 'status-hidden', icon: 'eye-off' }
};

const state = {
  catalog: [],
  platforms: [],
  userMap: new Map(),
  searchQuery: '',
  audienceFilter: 'all',    // 'all', 'student_only', 'general_only'
  selectedCategory: 'all',
  selectedCircuit: 'all',
  onlyUnibo: false,
  selectedStatus: 'active', // 'active' (all except hidden), 'new', 'unlocked', 'used', 'hidden'
  sortBy: 'recommended',    // 'recommended', 'expiry', 'alpha'
  activeDiscountId: null
};

/**
 * Sanitize and normalize a catalog item loaded from `data/discounts.json`.
 */
function sanitizeCatalogItem(raw) {
  if (!raw || typeof raw !== 'object' || typeof raw.id !== 'string') {
    return null;
  }
  const score = Math.min(5, Math.max(1, Number(raw.reliability?.score) || 5));
  const studentOnly = raw.student_only === undefined ? true : Boolean(raw.student_only);
  const isUnibo = Boolean(raw.isUniboOfficial);
  const defaultCircuit = isUnibo ? 'UniBo' : (studentOnly ? 'SheerID / Diretto' : 'Brand Club (Tutti)');
  return {
    id: String(raw.id).trim(),
    title: String(raw.title || 'Agevolazione').trim(),
    provider: String(raw.provider || 'Partner').trim(),
    category: String(raw.category || 'Cultura/Svago').trim(),
    circuit: String(raw.circuit || defaultCircuit).trim(),
    isUniboOfficial: isUnibo,
    student_only: studentOnly,
    discountBadge: String(raw.discountBadge || 'Vantaggio').trim(),
    summary: String(raw.summary || '').trim(),
    reliability: {
      score,
      reason: String(raw.reliability?.reason || 'Fonte verificata.').trim()
    },
    eligibility: Array.isArray(raw.eligibility) ? raw.eligibility.map((x) => String(x).trim()) : [],
    steps: Array.isArray(raw.steps) ? raw.steps.map((x) => String(x).trim()) : [],
    url: sanitizeExternalUrl(String(raw.url || 'https://www.unibo.it')),
    expiresAt: typeof raw.expiresAt === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(raw.expiresAt) ? raw.expiresAt : null,
    source: String(raw.source || 'curated').trim(),
    updatedAt: String(raw.updatedAt || '').trim()
  };
}

/**
 * Sanitize and normalize a platform directory entry loaded from `data/platforms.json`.
 */
function sanitizePlatformItem(raw) {
  if (!raw || typeof raw !== 'object' || typeof raw.id !== 'string') {
    return null;
  }
  return {
    id: String(raw.id).trim(),
    name: String(raw.name || 'Piattaforma').trim(),
    circuitKey: String(raw.circuitKey || 'UniBo').trim(),
    type: String(raw.type || 'Circuito').trim(),
    cost: String(raw.cost || 'Gratuito').trim(),
    student_only: raw.student_only === undefined ? true : Boolean(raw.student_only),
    verificationMethod: String(raw.verificationMethod || '').trim(),
    coveredCategories: Array.isArray(raw.coveredCategories)
      ? raw.coveredCategories.map((x) => String(x).trim())
      : [],
    pros: String(raw.pros || '').trim(),
    cons: String(raw.cons || '').trim(),
    url: sanitizeExternalUrl(String(raw.url || 'https://www.unibo.it'))
  };
}

function getUserRecord(discountId) {
  return (
    state.userMap.get(discountId) || {
      discountId,
      status: 'new',
      privateCode: '',
      personalNotes: '',
      updatedAt: ''
    }
  );
}

function formatExpiry(dateStr) {
  if (!dateStr) return 'Sempre attiva / Rinnovo annuale';
  try {
    const [y, m, d] = dateStr.split('-').map(Number);
    const dt = new Date(y, m - 1, d);
    return `Scade il ${dt.toLocaleDateString('it-IT', { day: '2-digit', month: 'short', year: 'numeric' })}`;
  } catch {
    return `Scadenza: ${dateStr}`;
  }
}

export function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = el('div', { className: `toast toast-${type}`, role: 'status' }, [
    svgIcon(type === 'error' ? 'close' : 'check', 'icon icon-sm'),
    el('span', { textContent: message })
  ]);
  container.appendChild(toast);

  window.setTimeout(() => {
    toast.classList.add('toast-hide');
    window.setTimeout(() => toast.remove(), 260);
  }, 2800);
}

async function copyToClipboard(text, label = 'Codice copiato negli appunti') {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    showToast(label, 'success');
  } catch {
    showToast('Impossibile copiare automaticamente', 'error');
  }
}

function getFilteredAndSortedCatalog() {
  const q = state.searchQuery.toLowerCase().trim();

  const filtered = state.catalog.filter((item) => {
    const uRec = getUserRecord(item.id);

    // Status filter
    if (state.selectedStatus === 'active') {
      if (uRec.status === 'hidden') return false;
    } else if (uRec.status !== state.selectedStatus) {
      return false;
    }

    // Audience filter (Tutti gli sconti vs Solo Studenti vs Aperti a Tutti)
    if (state.audienceFilter === 'student_only' && !item.student_only) {
      return false;
    }
    if (state.audienceFilter === 'general_only' && item.student_only) {
      return false;
    }

    // Category filter
    if (state.selectedCategory !== 'all' && item.category !== state.selectedCategory) {
      return false;
    }

    // Circuit / Platform filter
    if (state.selectedCircuit !== 'all' && item.circuit !== state.selectedCircuit) {
      return false;
    }

    // UniBo official filter
    if (state.onlyUnibo && !item.isUniboOfficial) {
      return false;
    }

    // Instant search query
    if (q) {
      const haystack = `${item.title} ${item.provider} ${item.summary} ${item.discountBadge} ${item.category} ${item.circuit}`.toLowerCase();
      if (!haystack.includes(q)) return false;
    }

    return true;
  });

  filtered.sort((a, b) => {
    if (state.sortBy === 'expiry') {
      const expA = a.expiresAt || '9999-12-31';
      const expB = b.expiresAt || '9999-12-31';
      if (expA !== expB) return expA.localeCompare(expB);
    } else if (state.sortBy === 'alpha') {
      return a.title.localeCompare(b.title, 'it');
    } else {
      // 'recommended': Unlocked with code first, then UniBo official, then reliability score
      const recA = getUserRecord(a.id);
      const recB = getUserRecord(b.id);
      const hasCodeA = recA.privateCode ? 1 : 0;
      const hasCodeB = recB.privateCode ? 1 : 0;
      if (hasCodeA !== hasCodeB) return hasCodeB - hasCodeA;
      if (a.isUniboOfficial !== b.isUniboOfficial) return a.isUniboOfficial ? -1 : 1;
      if (a.reliability.score !== b.reliability.score) return b.reliability.score - a.reliability.score;
    }
    return a.title.localeCompare(b.title, 'it');
  });

  return filtered;
}

function updateStatsBar() {
  let unlockedCount = 0;
  let savedCodesCount = 0;
  for (const item of state.catalog) {
    const rec = getUserRecord(item.id);
    if (rec.status === 'unlocked' || rec.status === 'used') unlockedCount++;
    if (rec.privateCode) savedCodesCount++;
  }

  const statTotal = document.getElementById('stat-total');
  const statUnlocked = document.getElementById('stat-unlocked');
  const statCodes = document.getElementById('stat-codes');

  if (statTotal) statTotal.textContent = String(state.catalog.length);
  if (statUnlocked) statUnlocked.textContent = String(unlockedCount);
  if (statCodes) statCodes.textContent = String(savedCodesCount);
}

function renderAudienceBadge(item) {
  if (item.student_only) {
    return el('span', { className: 'badge badge-student', title: 'Riservato a studenti universitari' }, [
      svgIcon('unibo', 'icon icon-xs'),
      el('span', { textContent: 'Solo Studenti' })
    ]);
  }
  return el('span', { className: 'badge badge-public', title: 'Accessibile a chiunque, anche non studenti' }, [
    svgIcon('users', 'icon icon-xs'),
    el('span', { textContent: 'Per Tutti' })
  ]);
}

function renderCard(item) {
  const uRec = getUserRecord(item.id);
  const statusInfo = STATUS_META[uRec.status] || STATUS_META.new;

  const badgesRow = el('div', { className: 'card-badges' }, [
    el('span', { className: 'badge badge-category', textContent: item.category }),
    renderAudienceBadge(item),
    item.isUniboOfficial
      ? el('span', { className: 'badge badge-unibo' }, [
          svgIcon('unibo', 'icon icon-xs'),
          el('span', { textContent: 'UniBo' })
        ])
      : el('span', { className: 'badge badge-circuit', title: 'Circuito / Piattaforma di verifica' }, [
          el('span', { textContent: item.circuit })
        ]),
    el('span', { className: `badge ${statusInfo.badgeClass}` }, [
      svgIcon(statusInfo.icon, 'icon icon-xs'),
      el('span', { textContent: statusInfo.label })
    ])
  ]);

  let codeQuickBar = null;
  if (uRec.privateCode) {
    const copyBtn = el(
      'button',
      {
        type: 'button',
        className: 'btn-copy-inline',
        title: 'Copia codice privato negli appunti',
        'aria-label': `Copia codice per ${item.title}`
      },
      [svgIcon('copy', 'icon icon-xs'), el('span', { textContent: 'Copia codice' })]
    );
    copyBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      copyToClipboard(uRec.privateCode, `Codice per ${item.provider} copiato!`);
    });

    codeQuickBar = el('div', { className: 'card-code-banner' }, [
      el('span', { className: 'code-banner-label' }, [
        svgIcon('key', 'icon icon-xs'),
        el('span', { textContent: 'Codice salvato in locale' })
      ]),
      copyBtn
    ]);
  }

  const card = el(
    'article',
    {
      className: `discount-card ${uRec.status === 'used' ? 'card-is-used' : ''}`,
      tabindex: '0',
      role: 'button',
      'aria-label': `${item.title} - ${item.discountBadge}`
    },
    [
      el('div', { className: 'card-top' }, [
        badgesRow,
        el('span', { className: 'discount-pill', textContent: item.discountBadge })
      ]),
      el('h2', { className: 'card-title', textContent: item.title }),
      el('p', { className: 'card-provider', textContent: item.provider }),
      el('p', { className: 'card-summary', textContent: item.summary }),
      codeQuickBar,
      el('div', { className: 'card-footer' }, [
        el('span', { className: 'reliability-tag', title: item.reliability.reason }, [
          svgIcon('star', 'icon icon-xs star-gold'),
          el('span', { textContent: `Affidabilità ${item.reliability.score}/5` })
        ]),
        el('span', { className: 'expiry-tag' }, [
          svgIcon('calendar', 'icon icon-xs'),
          el('span', { textContent: item.expiresAt ? formatExpiry(item.expiresAt) : 'Sempre attiva' })
        ])
      ])
    ]
  );

  const openDetail = () => openDetailModal(item.id);
  card.addEventListener('click', openDetail);
  card.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      openDetail();
    }
  });

  return card;
}

function renderCatalogGrid() {
  const grid = document.getElementById('discounts-grid');
  const resultsCount = document.getElementById('results-count');
  if (!grid) return;

  clearChildren(grid);
  const items = getFilteredAndSortedCatalog();

  if (resultsCount) {
    resultsCount.textContent = `${items.length} ${items.length === 1 ? 'offerta trovata' : 'offerte trovate'}`;
  }

  if (items.length === 0) {
    const resetBtn = el(
      'button',
      { type: 'button', className: 'btn btn-secondary' },
      'Ripristina tutti i filtri'
    );
    resetBtn.addEventListener('click', () => {
      state.searchQuery = '';
      state.audienceFilter = 'all';
      state.selectedCategory = 'all';
      state.selectedCircuit = 'all';
      state.onlyUnibo = false;
      state.selectedStatus = 'active';
      const searchInput = document.getElementById('search-input');
      if (searchInput) searchInput.value = '';
      syncFilterButtonsUI();
      renderCatalogGrid();
    });

    grid.appendChild(
      el('div', { className: 'empty-state' }, [
        svgIcon('search', 'icon icon-lg'),
        el('h3', { textContent: 'Nessuna offerta corrisponde ai filtri selezionati' }),
        el('p', {
          textContent: 'Prova a cambiare il selettore Studenti/Tutti, il circuito, la categoria o i termini di ricerca.'
        }),
        resetBtn
      ])
    );
    return;
  }

  const fragment = document.createDocumentFragment();
  for (const item of items) {
    fragment.appendChild(renderCard(item));
  }
  grid.appendChild(fragment);
}

function openDetailModal(discountId) {
  const item = state.catalog.find((d) => d.id === discountId);
  if (!item) return;

  state.activeDiscountId = discountId;
  const uRec = getUserRecord(discountId);
  const modal = document.getElementById('detail-modal');
  const body = document.getElementById('detail-modal-body');
  if (!modal || !body) return;

  clearChildren(body);

  const closeBtn = el(
    'button',
    {
      type: 'button',
      className: 'btn-icon modal-close-btn',
      'aria-label': 'Chiudi scheda dettaglio'
    },
    [svgIcon('close', 'icon')]
  );
  closeBtn.addEventListener('click', closeDetailModal);

  const headerSection = el('div', { className: 'modal-header' }, [
    el('div', { className: 'modal-badges' }, [
      el('span', { className: 'badge badge-category', textContent: item.category }),
      renderAudienceBadge(item),
      item.isUniboOfficial
        ? el('span', { className: 'badge badge-unibo' }, [
            svgIcon('unibo', 'icon icon-xs'),
            el('span', { textContent: 'Convenzione Ufficiale UniBo' })
          ])
        : el('span', { className: 'badge badge-circuit' }, [
            el('span', { textContent: `Circuito: ${item.circuit}` })
          ]),
      el('span', { className: 'discount-pill', textContent: item.discountBadge })
    ]),
    closeBtn
  ]);

  // Status selector buttons
  const statusButtonsContainer = el('div', { className: 'status-selector', role: 'group', 'aria-label': 'Stato convenzione' });
  for (const [statusKey, meta] of Object.entries(STATUS_META)) {
    const btn = el(
      'button',
      {
        type: 'button',
        className: `status-chip ${uRec.status === statusKey ? 'active' : ''}`,
        dataset: { status: statusKey }
      },
      [svgIcon(meta.icon, 'icon icon-xs'), el('span', { textContent: meta.label })]
    );
    btn.addEventListener('click', async () => {
      const current = getUserRecord(item.id);
      const updated = await vaultDB.saveRecord({
        ...current,
        discountId: item.id,
        status: statusKey
      });
      state.userMap.set(item.id, updated);
      for (const child of statusButtonsContainer.children) {
        child.classList.toggle('active', child.dataset.status === statusKey);
      }
      updateStatsBar();
      renderCatalogGrid();
      showToast(`Stato impostato su "${meta.label}"`);
    });
    statusButtonsContainer.appendChild(btn);
  }

  // Private code & notes vault form
  const codeInput = el('input', {
    type: 'password',
    id: 'vault-code-input',
    className: 'vault-input',
    placeholder: 'Incolla qui il tuo codice coupon / numero tessera...',
    autocomplete: 'off',
    spellcheck: 'false',
    maxlength: '256',
    value: uRec.privateCode
  });

  const toggleRevealBtn = el(
    'button',
    {
      type: 'button',
      className: 'btn btn-ghost btn-sm',
      title: 'Mostra o nascondi codice'
    },
    'Mostra'
  );
  toggleRevealBtn.addEventListener('click', () => {
    const isHidden = codeInput.type === 'password';
    codeInput.type = isHidden ? 'text' : 'password';
    toggleRevealBtn.textContent = isHidden ? 'Nascondi' : 'Mostra';
  });

  const copyCodeBtn = el(
    'button',
    {
      type: 'button',
      className: 'btn btn-secondary btn-sm'
    },
    [svgIcon('copy', 'icon icon-xs'), el('span', { textContent: 'Copia' })]
  );
  copyCodeBtn.addEventListener('click', () => {
    const val = codeInput.value.trim();
    if (!val) {
      showToast('Nessun codice presente da copiare', 'error');
      return;
    }
    copyToClipboard(val, 'Codice privato copiato negli appunti');
  });

  const notesTextarea = el('textarea', {
    id: 'vault-notes-input',
    className: 'vault-textarea',
    rows: '2',
    maxlength: '1500',
    placeholder: 'Note personali (es. scadenza rinnovo, email usata, condizioni)...'
  });
  notesTextarea.value = uRec.personalNotes;

  const saveVaultBtn = el(
    'button',
    { type: 'button', className: 'btn btn-primary btn-sm' },
    [svgIcon('lock', 'icon icon-xs'), el('span', { textContent: 'Salva nel Dispositivo' })]
  );

  saveVaultBtn.addEventListener('click', async () => {
    const current = getUserRecord(item.id);
    const newCode = codeInput.value.trim();
    const newNotes = notesTextarea.value.trim();
    const nextStatus = newCode && current.status === 'new' ? 'unlocked' : current.status;

    const updated = await vaultDB.saveRecord({
      discountId: item.id,
      status: nextStatus,
      privateCode: newCode,
      personalNotes: newNotes
    });
    state.userMap.set(item.id, updated);

    for (const child of statusButtonsContainer.children) {
      child.classList.toggle('active', child.dataset.status === nextStatus);
    }
    updateStatsBar();
    renderCatalogGrid();
    showToast('Dati salvati localmente in IndexedDB');
  });

  const vaultBox = el('section', { className: 'vault-box' }, [
    el('div', { className: 'vault-box-header' }, [
      el('div', { className: 'vault-box-title' }, [
        svgIcon('shield', 'icon icon-sm'),
        el('h3', { textContent: 'Cassaforte Codice Privato (100% On-Device)' })
      ]),
      el('span', { className: 'vault-badge', textContent: vaultDB.engine })
    ]),
    el('div', { className: 'vault-code-row' }, [codeInput, toggleRevealBtn, copyCodeBtn]),
    notesTextarea,
    el('div', { className: 'vault-actions-row' }, [
      el('span', {
        className: 'vault-hint',
        textContent: 'Mai inviato in rete. Protetto da CSP connect-src self.'
      }),
      saveVaultBtn
    ])
  ]);

  // Reliability & Eligibility
  const reliabilityBox = el('div', { className: 'info-block reliability-block' }, [
    el('div', { className: 'reliability-score-line' }, [
      svgIcon('star', 'icon icon-sm star-gold'),
      el('strong', { textContent: `Affidabilità ${item.reliability.score}/5` }),
      el('span', { className: 'text-muted', textContent: `· ${formatExpiry(item.expiresAt)}` })
    ]),
    el('p', { textContent: item.reliability.reason })
  ]);

  const eligibilityList = el(
    'ul',
    { className: 'detail-list' },
    item.eligibility.map((req) => el('li', { textContent: req }))
  );

  const stepsList = el(
    'ol',
    { className: 'detail-steps' },
    item.steps.map((step) => el('li', { textContent: step }))
  );

  const externalLinkBtn = el(
    'a',
    {
      href: item.url,
      className: 'btn btn-primary btn-block modal-cta-link'
    },
    [
      el('span', { textContent: 'Apri Pagina Ufficiale / Portale' }),
      svgIcon('external', 'icon icon-sm')
    ]
  );

  body.append(
    headerSection,
    el('h2', { className: 'modal-title', textContent: item.title }),
    el('p', { className: 'modal-provider', textContent: item.provider }),
    el('p', { className: 'modal-summary', textContent: item.summary }),
    el('div', { className: 'modal-section' }, [
      el('h3', { className: 'section-label', textContent: 'Stato Personale' }),
      statusButtonsContainer
    ]),
    vaultBox,
    reliabilityBox,
    el('div', { className: 'modal-section' }, [
      el('h3', { className: 'section-label', textContent: 'Requisiti di Eleggibilità' }),
      eligibilityList
    ]),
    el('div', { className: 'modal-section' }, [
      el('h3', { className: 'section-label', textContent: 'Guida Passo-Passo per Sbloccarlo' }),
      stepsList
    ]),
    externalLinkBtn
  );

  modal.classList.remove('hidden');
  modal.setAttribute('aria-hidden', 'false');
  document.body.classList.add('modal-open');
}

function closeDetailModal() {
  const modal = document.getElementById('detail-modal');
  if (!modal) return;
  modal.classList.add('hidden');
  modal.setAttribute('aria-hidden', 'true');
  document.body.classList.remove('modal-open');
  state.activeDiscountId = null;
}

function syncFilterButtonsUI() {
  document.querySelectorAll('[data-audience]').forEach((btn) => {
    const isSelected = btn.getAttribute('data-audience') === state.audienceFilter;
    btn.classList.toggle('active', isSelected);
    btn.setAttribute('aria-pressed', String(isSelected));
  });

  document.querySelectorAll('[data-category]').forEach((btn) => {
    const isSelected = btn.getAttribute('data-category') === state.selectedCategory;
    btn.classList.toggle('active', isSelected);
    btn.setAttribute('aria-pressed', String(isSelected));
  });

  document.querySelectorAll('[data-circuit]').forEach((btn) => {
    const isSelected = btn.getAttribute('data-circuit') === state.selectedCircuit;
    btn.classList.toggle('active', isSelected);
    btn.setAttribute('aria-pressed', String(isSelected));
  });

  document.querySelectorAll('[data-status-filter]').forEach((btn) => {
    const isSelected = btn.getAttribute('data-status-filter') === state.selectedStatus;
    btn.classList.toggle('active', isSelected);
    btn.setAttribute('aria-pressed', String(isSelected));
  });

  const uniboBtn = document.getElementById('filter-unibo-only');
  if (uniboBtn) {
    uniboBtn.classList.toggle('active', state.onlyUnibo);
    uniboBtn.setAttribute('aria-pressed', String(state.onlyUnibo));
  }
}

function renderPlatformsDirectory(closePlatformsCallback) {
  const container = document.getElementById('platforms-directory-list');
  if (!container) return;
  clearChildren(container);

  const fragment = document.createDocumentFragment();
  for (const plat of state.platforms) {
    const filterBtn = el(
      'button',
      {
        type: 'button',
        className: 'btn btn-secondary btn-sm'
      },
      [
        svgIcon('search', 'icon icon-xs'),
        el('span', { textContent: `Filtra offerte ${plat.circuitKey}` })
      ]
    );

    filterBtn.addEventListener('click', () => {
      state.selectedCircuit = plat.circuitKey;
      state.audienceFilter = 'all';
      state.selectedCategory = 'all';
      state.onlyUnibo = false;
      syncFilterButtonsUI();
      renderCatalogGrid();
      if (typeof closePlatformsCallback === 'function') {
        closePlatformsCallback();
      }
      showToast(`Filtro circuito attivato: ${plat.circuitKey}`);
    });

    const regLink = el(
      'a',
      {
        href: plat.url,
        className: 'btn btn-primary btn-sm'
      },
      [
        el('span', { textContent: 'Apri Portale / Registrati' }),
        svgIcon('external', 'icon icon-xs')
      ]
    );

    const card = el('article', { className: 'platform-card' }, [
      el('div', { className: 'platform-card-top' }, [
        el('h3', { className: 'platform-card-title', textContent: plat.name }),
        el('div', { className: 'card-badges' }, [
          el('span', { className: 'badge badge-circuit', textContent: plat.circuitKey }),
          plat.student_only
            ? el('span', { className: 'badge badge-student' }, [
                svgIcon('unibo', 'icon icon-xs'),
                el('span', { textContent: 'Studenti' })
              ])
            : el('span', { className: 'badge badge-public' }, [
                svgIcon('users', 'icon icon-xs'),
                el('span', { textContent: 'Anche Non Studenti' })
              ]),
          el('span', { className: 'discount-pill', textContent: plat.cost })
        ])
      ]),
      el('div', { className: 'platform-meta-grid' }, [
        el('div', { className: 'platform-meta-row' }, [
          el('strong', { textContent: 'Metodo di Verifica: ' }),
          el('span', { textContent: plat.verificationMethod })
        ]),
        el('div', { className: 'platform-meta-row' }, [
          el('strong', { textContent: 'Punti di Forza: ' }),
          el('span', { textContent: plat.pros })
        ]),
        el('div', { className: 'platform-meta-row' }, [
          el('strong', { textContent: 'Limiti / Note: ' }),
          el('span', { textContent: plat.cons })
        ])
      ]),
      el('div', { className: 'platform-actions' }, [filterBtn, regLink])
    ]);

    fragment.appendChild(card);
  }

  container.appendChild(fragment);
}

function setupPlatformsModal() {
  const modal = document.getElementById('platforms-modal');
  const openBtnHeader = document.getElementById('btn-open-platforms');
  const openBtnBanner = document.getElementById('btn-open-platforms-banner');
  const closeBtn = document.getElementById('btn-close-platforms');
  const closeBottomBtn = document.getElementById('btn-close-platforms-bottom');

  if (!modal) return null;

  let lastFocused = null;

  const closePlatforms = () => {
    if (modal.classList.contains('hidden')) return;
    modal.classList.add('hidden');
    modal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('modal-open');
    if (lastFocused && typeof lastFocused.focus === 'function') {
      lastFocused.focus();
    }
  };

  const openPlatforms = () => {
    lastFocused = document.activeElement;
    renderPlatformsDirectory(closePlatforms);
    modal.classList.remove('hidden');
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    if (closeBtn) closeBtn.focus();
  };

  if (openBtnHeader) openBtnHeader.addEventListener('click', openPlatforms);
  if (openBtnBanner) openBtnBanner.addEventListener('click', openPlatforms);
  if (closeBtn) closeBtn.addEventListener('click', closePlatforms);
  if (closeBottomBtn) closeBottomBtn.addEventListener('click', closePlatforms);

  modal.addEventListener('click', (e) => {
    if (e.target === modal) closePlatforms();
  });

  return { closePlatforms };
}

function setupBackupModal() {
  const backupModal = document.getElementById('backup-modal');
  const openBtn = document.getElementById('btn-open-backup');
  const closeBtn = document.getElementById('btn-close-backup');
  const exportBtn = document.getElementById('btn-export-backup');
  const importInput = document.getElementById('input-import-file');
  const passInput = document.getElementById('backup-passphrase');

  if (!backupModal || !openBtn) return;

  const closeBackup = () => {
    backupModal.classList.add('hidden');
    backupModal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('modal-open');
  };

  openBtn.addEventListener('click', () => {
    backupModal.classList.remove('hidden');
    backupModal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
  });

  if (closeBtn) closeBtn.addEventListener('click', closeBackup);
  backupModal.addEventListener('click', (e) => {
    if (e.target === backupModal) closeBackup();
  });

  if (exportBtn) {
    exportBtn.addEventListener('click', async () => {
      try {
        const records = await vaultDB.getAllList();
        const passphrase = passInput ? passInput.value.trim() : '';
        const jsonPayload = await createBackupPayload(records, passphrase);
        const blob = new Blob([jsonPayload], { type: 'application/json;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const today = new Date().toISOString().slice(0, 10);
        const suffix = passphrase ? 'encrypted' : 'plain';
        const link = el('a', {
          download: `unibo-perks-backup-${suffix}-${today}.json`
        });
        link.href = url;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
        showToast(
          passphrase
            ? 'Backup cifrato AES-256-GCM esportato con successo'
            : 'Backup JSON in chiaro esportato con successo'
        );
      } catch (err) {
        showToast(err.message || 'Errore durante esportazione', 'error');
      }
    });
  }

  if (importInput) {
    importInput.addEventListener('change', async (e) => {
      const file = e.target.files && e.target.files[0];
      if (!file) return;
      try {
        const rawText = await file.text();
        const passphrase = passInput ? passInput.value.trim() : '';
        const records = await parseBackupPayload(rawText, passphrase);
        const count = await vaultDB.importRecords(records);
        state.userMap = await vaultDB.getAllMap();
        updateStatsBar();
        renderCatalogGrid();
        showToast(`Importati ${count} record personali con successo!`);
        closeBackup();
      } catch (err) {
        showToast(err.message || 'Errore durante importazione backup', 'error');
      } finally {
        importInput.value = '';
      }
    });
  }
}

function setupHelpModal() {
  const helpModal = document.getElementById('help-modal');
  const openBtn = document.getElementById('btn-open-help');
  const closeBtn = document.getElementById('btn-close-help');
  const closeBottomBtn = document.getElementById('btn-close-help-bottom');
  const accordion = document.getElementById('help-accordion');

  if (!helpModal || !openBtn) return;

  let lastFocusedElement = null;

  const openHelp = () => {
    lastFocusedElement = document.activeElement;
    helpModal.classList.remove('hidden');
    helpModal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    if (closeBtn) closeBtn.focus();
  };

  const closeHelp = () => {
    if (helpModal.classList.contains('hidden')) return;
    helpModal.classList.add('hidden');
    helpModal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('modal-open');
    if (lastFocusedElement && typeof lastFocusedElement.focus === 'function') {
      lastFocusedElement.focus();
    }
  };

  openBtn.addEventListener('click', openHelp);
  if (closeBtn) closeBtn.addEventListener('click', closeHelp);
  if (closeBottomBtn) closeBottomBtn.addEventListener('click', closeHelp);

  helpModal.addEventListener('click', (e) => {
    if (e.target === helpModal) closeHelp();
  });

  // Accessible Focus Trap inside Help Modal
  helpModal.addEventListener('keydown', (e) => {
    if (e.key !== 'Tab') return;
    const focusables = helpModal.querySelectorAll(
      'button:not([disabled]), [href], input:not([disabled]), [tabindex]:not([tabindex="-1"])'
    );
    if (focusables.length === 0) return;
    const first = focusables[0];
    const last = focusables[focusables.length - 1];

    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  });

  // Accordion toggle logic
  if (accordion) {
    const triggers = accordion.querySelectorAll('.accordion-trigger');
    triggers.forEach((trigger) => {
      trigger.addEventListener('click', () => {
        const parentItem = trigger.closest('.accordion-item');
        if (!parentItem) return;
        const isOpen = parentItem.classList.contains('is-open');

        // Toggle selected item
        parentItem.classList.toggle('is-open', !isOpen);
        trigger.setAttribute('aria-expanded', String(!isOpen));
      });
    });
  }

  return { closeHelp };
}

function applyTheme(theme) {
  const safeTheme = theme === 'light' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', safeTheme);
  vaultDB.setThemePreference(safeTheme);

  const metaTheme = document.querySelector('meta[name="theme-color"]');
  if (metaTheme) {
    metaTheme.setAttribute('content', safeTheme === 'dark' ? '#07080c' : '#f6f7fb');
  }

  const themeBtn = document.getElementById('btn-theme-toggle');
  if (themeBtn) {
    clearChildren(themeBtn);
    themeBtn.appendChild(svgIcon(safeTheme === 'dark' ? 'sun' : 'moon', 'icon'));
    themeBtn.setAttribute(
      'aria-label',
      safeTheme === 'dark' ? 'Passa al tema chiaro' : 'Passa al tema scuro AMOLED'
    );
  }
}

async function loadCatalog() {
  try {
    const [discountsResp, platformsResp] = await Promise.all([
      fetch('./data/discounts.json', { cache: 'no-cache' }),
      fetch('./data/platforms.json', { cache: 'no-cache' })
    ]);

    if (!discountsResp.ok) {
      throw new Error(`HTTP ${discountsResp.status}`);
    }
    const rawList = await discountsResp.json();
    if (!Array.isArray(rawList)) {
      throw new Error('Formato discounts.json non valido');
    }
    state.catalog = rawList.map(sanitizeCatalogItem).filter(Boolean);

    if (platformsResp.ok) {
      const rawPlatforms = await platformsResp.json();
      if (Array.isArray(rawPlatforms)) {
        state.platforms = rawPlatforms.map(sanitizePlatformItem).filter(Boolean);
      }
    }
  } catch {
    showToast('Modalità offline o errore lettura catalogo', 'error');
  }
}

async function initApp() {
  await vaultDB.init();
  applyTheme(vaultDB.getThemePreference());

  const storageStatusEl = document.getElementById('storage-engine-status');
  if (storageStatusEl) {
    const persistLabel = vaultDB.isPersistent ? 'Persistente' : 'Isolato';
    storageStatusEl.textContent = `Vault: ${vaultDB.engine} (${persistLabel})`;
  }

  state.userMap = await vaultDB.getAllMap();
  await loadCatalog();

  updateStatsBar();
  syncFilterButtonsUI();
  renderCatalogGrid();

  // Event Listeners
  const searchInput = document.getElementById('search-input');
  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      state.searchQuery = e.target.value || '';
      renderCatalogGrid();
    });
  }

  const sortSelect = document.getElementById('sort-select');
  if (sortSelect) {
    sortSelect.addEventListener('change', (e) => {
      state.sortBy = e.target.value;
      renderCatalogGrid();
    });
  }

  document.querySelectorAll('[data-audience]').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.audienceFilter = btn.getAttribute('data-audience') || 'all';
      syncFilterButtonsUI();
      renderCatalogGrid();
    });
  });

  document.querySelectorAll('[data-category]').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.selectedCategory = btn.getAttribute('data-category') || 'all';
      syncFilterButtonsUI();
      renderCatalogGrid();
    });
  });

  document.querySelectorAll('[data-circuit]').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.selectedCircuit = btn.getAttribute('data-circuit') || 'all';
      syncFilterButtonsUI();
      renderCatalogGrid();
    });
  });

  document.querySelectorAll('[data-status-filter]').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.selectedStatus = btn.getAttribute('data-status-filter') || 'active';
      syncFilterButtonsUI();
      renderCatalogGrid();
    });
  });

  const uniboBtn = document.getElementById('filter-unibo-only');
  if (uniboBtn) {
    uniboBtn.addEventListener('click', () => {
      state.onlyUnibo = !state.onlyUnibo;
      syncFilterButtonsUI();
      renderCatalogGrid();
    });
  }

  const themeBtn = document.getElementById('btn-theme-toggle');
  if (themeBtn) {
    themeBtn.addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme') || 'dark';
      applyTheme(current === 'dark' ? 'light' : 'dark');
    });
  }

  const detailModal = document.getElementById('detail-modal');
  if (detailModal) {
    detailModal.addEventListener('click', (e) => {
      if (e.target === detailModal) closeDetailModal();
    });
  }

  const platformsController = setupPlatformsModal();
  const helpController = setupHelpModal();
  setupBackupModal();

  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      closeDetailModal();
      if (platformsController) platformsController.closePlatforms();
      if (helpController) helpController.closeHelp();
      const backupModal = document.getElementById('backup-modal');
      if (backupModal && !backupModal.classList.contains('hidden')) {
        backupModal.classList.add('hidden');
        backupModal.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('modal-open');
      }
    }
  });

  // PWA Install Prompt Handler
  let deferredInstallPrompt = null;
  const installBtn = document.getElementById('btn-install-pwa');

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredInstallPrompt = e;
    if (installBtn) {
      installBtn.classList.remove('hidden');
    }
  });

  if (installBtn) {
    installBtn.addEventListener('click', async () => {
      if (!deferredInstallPrompt) {
        showToast('Installazione guidata: apri il menu ⋮ di Chrome e tocca "Installa app"');
        return;
      }
      deferredInstallPrompt.prompt();
      const choice = await deferredInstallPrompt.userChoice;
      if (choice.outcome === 'accepted') {
        showToast('Installazione avviata con successo!', 'success');
        installBtn.classList.add('hidden');
      }
      deferredInstallPrompt = null;
    });
  }

  window.addEventListener('appinstalled', () => {
    deferredInstallPrompt = null;
    if (installBtn) installBtn.classList.add('hidden');
    showToast('UniBo Perks installata correttamente!', 'success');
  });

  // Register Service Worker for offline PWA support
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('https://martiri.github.io/unibo-perks/sw.js', { scope: 'https://martiri.github.io/unibo-perks/' }).catch(() => {});
        // Ignore SW error on file:// protocol
      });
    });
  }
}

document.addEventListener('DOMContentLoaded', initApp);
