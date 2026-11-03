# Manuale d'Uso e Manutenzione — UniBo & Perks Vault (PWA)

Questo documento è la guida di riferimento rapida e permanente per installare, usare, aggiornare e manutenere la Web App **UniBo & Perks Vault** sul tuo smartphone Android (**POCO F7**) e su PC Desktop (**Pop!_OS**).

---

## 1. Installazione della PWA

L'applicazione è una **Progressive Web App (PWA)** dotata di `manifest.webmanifest` e Service Worker (`sw.js`): una volta aperta e installata, funziona come un'app nativa a schermo intero (`standalone`) ed è consultabile **anche completamente offline** (es. in treno, in galleria o in montagna durante un'escursione).

### 📱 Su Android (POCO F7 — Google Chrome / Brave)
1. Apri Chrome sul tuo POCO F7 e visita l'URL della tua GitHub Pages (es. `https://<tuo-username>.github.io/<nome-repo>/`).
2. Tocca il menu in alto a destra (**⋮** con i tre puntini).
3. Seleziona **"Aggiungi a schermata Home"** (oppure **"Installa app"** se compare il banner automatico).
4. Conferma toccando **"Installa"**: l'icona **UniBo Perks** apparirà nel drawer delle app di Android e si aprirà a 120Hz con tema nativo **True Black AMOLED** senza barra degli indirizzi.

### 💻 Su PC Desktop (Linux Pop!_OS — Chrome / Chromium / Brave)
1. Apri l'URL dell'app (o `http://127.0.0.1:8080` in locale) su Chrome o Brave.
2. Nella barra degli indirizzi, sul lato destro, clicca sull'icona **"Installa UniBo & Student Perks Vault"** (simbolo di uno schermo con una freccia verso il basso).
3. Conferma su **"Installa"** per creare il lanciatore desktop indipendente.

---

## 2. Come Aggiungere un Nuovo Sconto a Mano

Il catalogo è diviso in due flussi:
- `data/curated.json` -> **Il file che modifichi tu a mano** per aggiungere coupon, sconti outdoor, abbonamenti AI, promozioni tech o convenzioni verificate.
- `data/scraped_unibo.json` -> **Il file aggiornato in automatico** dallo script Python con le convenzioni pubbliche estratte dal portale `unibo.it`.
- `data/discounts.json` -> **Il catalogo finale unificato** generato unendo `curated.json` + `scraped_unibo.json`.

### Procedura Rapida in 3 Passi
1. Apri il file `data/curated.json`.
2. Aggiungi un nuovo blocco JSON nella lista seguendo il template qui sotto.
3. Esegui lo script di validazione e unione (oppure fai direttamente `git push` e lascia fare alla GitHub Action):
   ```bash
   .venv/bin/python scripts/sync_unibo.py
   ```
   Lo script controllerà automaticamente che tutti i campi siano validi, che gli URL siano `https://` sicuri e aggiornerà `data/discounts.json`.

### Template JSON Commentato Campo per Campo

> **Nota**: il formato JSON standard non ammette commenti `//` dentro `data/curated.json`. Usa la tabella e l'esempio pulito qui sotto quando incolli una nuova voce.

```json
{
  "id": "salomon-splus-voucher-autunno",
  "title": "Salomon S/PLUS: -15% su Scarpe GORE-TEX & Zaini Trail",
  "provider": "Salomon",
  "category": "Outdoor & Trekking",
  "isUniboOfficial": false,
  "student_only": false,
  "discountBadge": "-15% Extra",
  "summary": "Sconto del 15% sulle linee X Ultra e Speedcross per gli iscritti gratuiti al programma Salomon S/PLUS, con spedizione e reso gratuiti.",
  "reliability": {
    "score": 5,
    "reason": "Promozione ufficiale verificata direttamente sul portale Salomon Italia."
  },
  "eligibility": [
    "Aperto a tutti tramite account gratuito Salomon S/PLUS",
    "Esclusi prodotti già in saldo oltre il 30%"
  ],
  "steps": [
    "Accedi al tuo profilo gratuito Salomon S/PLUS su salomon.com/it-it.",
    "Aggiungi al carrello le calzature o l'attrezzatura da trekking idonea.",
    "Copia il tuo codice coupon salvato nella Cassaforte Privata qui sotto e incollalo nel carrello."
  ],
  "url": "https://www.salomon.com/it-it/s-plus",
  "expiresAt": "2027-03-31",
  "source": "curated",
  "updatedAt": "2026-11-03"
}
```

### Riferimento dei Campi dello Schema

| Campo | Tipo | Obbligatorio | Descrizione e Valori Ammessi |
| :--- | :--- | :---: | :--- |
| `id` | `string` | Sì | Identificativo univoco in minuscolo con trattini (es. `"perplexity-pro-promo"`). **Non cambiarlo nel tempo** perché i codici privati salvati nel browser sono collegati a questo `id`. |
| `title` | `string` | Sì | Titolo chiaro dell'offerta (max 140 caratteri). |
| `provider` | `string` | Sì | Ente o brand che eroga lo sconto (es. `"Decathlon"`, `"GitHub Education"`, `"TPER / UniBo"`). |
| `category` | `string` | Sì | Deve essere **esattamente una** delle 7 categorie supportate:<br>• `"AI & Dev Tools"`<br>• `"Outdoor & Trekking"`<br>• `"Abbigliamento & Calzature"`<br>• `"Tech & Elettronica"`<br>• `"Trasporti"`<br>• `"Cultura/Svago"`<br>• `"Abbonamenti"` |
| `isUniboOfficial` | `boolean` | Sì | `true` se è una convenzione istituzionale dell'Università di Bologna, altrimenti `false`. |
| `student_only` | `boolean` | Sì | `true` se riservato a studenti universitari (mostra badge **Solo Studenti**).<br>`false` se accessibile a chiunque anche senza essere studenti (mostra badge **Per Tutti**). |
| `discountBadge` | `string` | Sì | Pillola evidenziata in alto a destra sulla card (es. `"100% Gratis"`, `"-20%"`, `"Buono 10€"`). |
| `summary` | `string` | Sì | Descrizione sintetica mostrata sulla card e in testa alla scheda dettaglio. |
| `reliability.score` | `integer` | Sì | Punteggio da `1` a `5` (es. `5` per fonti ufficiali certe). |
| `reliability.reason` | `string` | Sì | Motivazione sintetica dell'affidabilità dello sconto. |
| `eligibility` | `array<string>` | Sì | Lista puntata dei requisiti per accedere allo sconto. |
| `steps` | `array<string>` | Sì | Guida numerata passo-passo per sbloccare o riscattare il vantaggio. |
| `url` | `string` | Sì | Link ufficiale (deve iniziare con `https://`). |
| `expiresAt` | `string \| null` | Sì | Data di scadenza in formato ISO `"YYYY-MM-DD"` (es. `"2027-08-31"`) oppure `null` se l'offerta è sempre attiva/rinnovabile. |
| `source` | `string` | Sì | Imposta sempre `"curated"` per le voci aggiunte a mano. |
| `updatedAt` | `string` | Sì | Data di ultimo aggiornamento in formato `"YYYY-MM-DD"`. |

---

## 3. Filtri, Ricerca e Modalità "Studenti vs Per Tutti"

L'interfaccia è progettata per trovare qualsiasi vantaggio o codice in meno di 2 secondi:

1. **Selettore Destinatari in Alto (Audience Switcher)**:
   - **Tutti gli sconti**: vista completa che mostra sia le convenzioni universitarie sia le promozioni aperte a chiunque.
   - **Solo Studenti**: filtra istantaneamente le sole offerte con `student_only: true` (badge viola **Solo Studenti**).
   - **Aperti a Tutti**: mostra esclusivamente le offerte con `student_only: false` (badge ciano **Per Tutti**), utilissimo per coupon trekking, abbigliamento, piani free AI o offerte trasporti non vincolate all'iscrizione universitaria.
2. **Filtri per Categoria e Tag UniBo**:
   - Scorri orizzontalmente i chip per filtrare su **AI & Dev Tools**, **Outdoor & Trekking**, **Abbigliamento & Calzature**, **Tech & Elettronica**, **Trasporti**, **Cultura & Tempo Libero**, **Abbonamenti**, oppure attiva il filtro trasversale **Solo Convenzioni UniBo**.
3. **Filtri per Stato Personale**:
   - Ogni scheda può essere contrassegnata come **Nuovo**, **Sbloccato**, **Usato** o **Nascosto**.
   - Quando imposti uno sconto su **Nascosto**, scompare dalla vista principale (`Tutti (Visibili)`) per mantenere pulita la schermata; puoi rivederlo e ripristinarlo in qualsiasi momento toccando il filtro **Nascosto**.
4. **Copia Rapida 1-Tap**:
   - Non appena salvi un codice privato dentro una scheda, sulla card nella schermata principale compare un banner diretto **"Codice salvato in locale · [Copia codice]"** per copiarlo negli appunti senza nemmeno dover riaprire i dettagli.

---

## 4. Privacy, Cassaforte Locale e Backup Cifrato

### Come funziona l'isolamento sul dispositivo
- I tuoi codici privati, le note e gli stati (`Sbloccato`/`Usato`/`Nascosto`) **non vengono mai salvati nel repository Git** e **non lasciano mai il tuo browser**.
- Sono memorizzati nel database locale `IndexedDB` (`StudentPerksVault`) con copia di sicurezza sincrona in `localStorage` e protezione `navigator.storage.persist()` attiva per impedire ad Android di cancellarli durante la pulizia automatica della cache.
- La Content Security Policy (`connect-src 'self'`) blocca a livello del motore del browser qualsiasi trasmissione di dati verso server esterni.

### Come fare il Backup e Ripristino (Cambio Telefono o Browser)
1. Tocca il pulsante **"Backup"** in alto a destra nella barra dell'app.
2. **(Consigliato)** Inserisci una **Passphrase di cifratura** nel campo dedicato:
   - Se inserisci una password, l'app deriva localmente una chiave crittografica tramite `PBKDF2-HMAC-SHA256` (210.000 iterazioni) e cifra tutti i tuoi codici con **AES-256-GCM** prima di generare il file `.json`. In questo modo puoi salvare il file di backup anche su Google Drive, Telegram o email in totale sicurezza.
   - Se lasci il campo vuoto, viene esportato un file `.json` standard in chiaro.
3. Clicca su **"Esporta File di Backup (.json)"** per scaricare il file.
4. Per ripristinare i dati su un nuovo dispositivo o browser, apri **"Backup"**, inserisci la stessa passphrase (se il file era cifrato) e seleziona **"Importa da File di Backup (.json)"**.

---

## 5. Workflow GitHub & Sincronizzazione Automatica

### Come pubblicare e aggiornare su GitHub Pages
1. Collega il repository locale al tuo repository GitHub e fai push sul branch `main`:
   ```bash
   git add .
   git commit -m "feat: update discounts catalog and UI"
   git push origin main
   ```
2. Su GitHub, vai in **Settings > Pages** e sotto **Build and deployment** seleziona:
   - **Source**: `Deploy from a branch`
   - **Branch**: `main` / `/ (root)` -> **Save**.

### Come funziona e come verificare la GitHub Action
Il file `.github/workflows/sync-discounts.yml` esegue automaticamente lo script `scripts/sync_unibo.py` **ogni lunedì alle 06:30 UTC**:
- Scarica le pagine pubbliche istituzionali UniBo (*Trasporti*, *Tempo Libero*, *Risorse Informatiche*), scarta eventuali bandi ER.GO/borse di studio e valida i risultati.
- **Protezione Fail-Safe**: se il sito UniBo è temporaneamente offline o cambia layout restituendo 0 risultati, lo script **non sovrascrive** i dati con una lista vuota ma mantiene intatto l'ultimo snapshot valido (`data/scraped_unibo.json`) e tutte le tue voci manuali (`data/curated.json`).
- **Verifica e avvio manuale**:
  1. Vai nella tab **Actions** del tuo repository GitHub.
  2. Seleziona a sinistra il workflow **"Sync UniBo Public Discounts"**.
  3. Clicca su **"Run workflow"** -> **"Run workflow"** per lanciare una sincronizzazione immediata e verificare dal pallino verde che lo step `Run fail-safe UniBo scraper and catalog builder` termini con codice `0`.
