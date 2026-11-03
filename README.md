# UniBo & Student Perks Vault (PWA)

Progressive Web App (PWA) mobile-first, **100% statica**, progettata per raccogliere e gestire convenzioni commerciali, agevolazioni trasporti (TPER, Trenitalia, Italo, Itabus), musei, cinema, teatri e software/cloud gratuiti per studenti universitari in Italia (con focus specifico sull'**Università di Bologna - UniBo**).

> **Ambito (Scope)**: Include esclusivamente sconti commerciali, promozioni tech/cloud, abbonamenti e convenzioni trasporti/cultura. Sono **esclusi** bandi di concorso, borse di studio ed erogazioni ER.GO.

---

## Architettura di Sicurezza (Defense-in-Depth)

1. **Isolamento Totale dei Dati Personali (Zero Backend)**:
   - I codici coupon privati, i numeri di tessera, lo stato di utilizzo (`Nuovo`, `Sbloccato`, `Usato`, `Nascosto`) e le note personali risiedono **esclusivamente sul dispositivo** in `IndexedDB` (`StudentPerksVault`) con fallback automatico su `localStorage` e richiesta `navigator.storage.persist()` su Android.
2. **Content Security Policy (CSP) Blindata**:
   - L'`index.html` applica una CSP restrittiva (`default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-src 'none'; base-uri 'self'; form-action 'none'`).
   - Qualsiasi tentativo di esfiltrazione verso server esterni è bloccato nativamente dal browser.
3. **Prevenzione Categorica di Stored / DOM XSS**:
   - Zero utilizzo di `innerHTML`, `outerHTML`, `insertAdjacentHTML` o `eval`.
   - Tutti i nodi DOM vengono costruiti tramite `document.createElement` e `textContent` in `js/dom-safe.js`, e gli URL esterni vengono validati con `new URL()` accettando solo protocollo `https:`.
4. **Backup Locale Cifrato (AES-256-GCM)**:
   - Esportazione e importazione dello stato personale in JSON con cifratura opzionale client-side tramite Web Crypto API (`PBKDF2-HMAC-SHA256` a 210.000 iterazioni + `AES-GCM` a 256 bit).
5. **Zero Dipendenze Runtime / Zero CDN**:
   - Nessun framework JS esterno, nessun Google Fonts, nessun CDN di terze parti. Tutte le icone risiedono nello sprite vettoriale locale `assets/icons/sprite.svg`.

---

## Struttura del Repository

```text
├── .github/workflows/sync-discounts.yml  # Automazione settimanale GitHub Actions
├── assets/icons/                         # Sprite SVG locale e icone PWA installabili
├── css/app.css                           # Stile mobile-first AMOLED True Black / Light
├── data/
│   ├── curated.json                      # Sconti tech, streaming e convenzioni curati
│   ├── scraped_unibo.json                # Snapshot estratto dal portale pubblico UniBo
│   └── discounts.json                    # Catalogo unificato e validato servito alla PWA
├── js/
│   ├── app.js                            # Controller UI, filtri istantanei, modali
│   ├── db.js                             # Motore IndexedDB + fallback localStorage
│   ├── crypto-backup.js                  # Cifratura/decifratura backup AES-256-GCM
│   └── dom-safe.js                       # Costruzione DOM anti-XSS e sanitizzazione URL
├── scripts/
│   ├── sync_unibo.py                     # Scraper fail-safe e validatore schema JSON
│   └── requirements.txt                  # Dipendenze Python con version pinning esatto
├── index.html                            # App Shell con CSP rigorosa
├── manifest.webmanifest                  # Web App Manifest per Android (POCO F7)
└── sw.js                                 # Service Worker (Offline-ready)
```

---

## Sviluppo e Aggiornamento Locale

### 1. Aggiornare il catalogo (`discounts.json`)
```bash
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements.txt
.venv/bin/python scripts/sync_unibo.py
```

### 2. Avviare la PWA in locale
```bash
python3 -m http.server 8080 --bind 127.0.0.1
```
Poi apri `http://127.0.0.1:8080` nel browser.

### 3. Deploy su GitHub Pages
1. Esegui il push del repository su GitHub.
2. Vai in **Settings > Pages** e imposta **Source: Deploy from a branch** (`main` / `/ (root)`).
3. Su **POCO F7 (Chrome per Android)** apri l'URL di GitHub Pages e seleziona **Aggiungi a schermata Home / Installa app**.
