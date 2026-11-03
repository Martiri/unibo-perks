/**
 * Local-Only Vault Storage Engine (IndexedDB primary + localStorage fallback)
 *
 * All private discount codes, usage statuses, and personal notes remain 100%
 * isolated on the user's device. Requests persistent storage on Android.
 */

const DB_NAME = 'StudentPerksVault';
const DB_VERSION = 1;
const STORE_NAME = 'user_state';
const LS_FALLBACK_KEY = 'unibo_perks_user_state_v1';
const LS_PREFS_KEY = 'unibo_perks_ui_prefs_v1';

export const ALLOWED_STATUSES = new Set(['new', 'unlocked', 'used', 'hidden']);

/**
 * Validate and sanitize a single user state entry.
 *
 * @param {any} raw
 * @returns {{discountId: string, status: string, privateCode: string, personalNotes: string, updatedAt: string}}
 */
export function normalizeUserRecord(raw) {
  if (!raw || typeof raw !== 'object') {
    throw new Error('Record utente non valido.');
  }
  const discountId = String(raw.discountId || '').trim().slice(0, 100);
  if (!discountId || !/^[a-zA-Z0-9_-]+$/.test(discountId)) {
    throw new Error('Identificativo sconto (discountId) non valido.');
  }
  const status = ALLOWED_STATUSES.has(raw.status) ? raw.status : 'new';
  const privateCode = String(raw.privateCode || '').trim().slice(0, 256);
  const personalNotes = String(raw.personalNotes || '').trim().slice(0, 1500);
  const updatedAt =
    typeof raw.updatedAt === 'string' && !Number.isNaN(Date.parse(raw.updatedAt))
      ? raw.updatedAt
      : new Date().toISOString();

  return {
    discountId,
    status,
    privateCode,
    personalNotes,
    updatedAt
  };
}

class LocalVaultDB {
  constructor() {
    /** @type {IDBDatabase|null} */
    this.db = null;
    this.engine = 'localStorage';
    this.isPersistent = false;
  }

  async init() {
    // Request persistent storage on Android Chrome so OS doesn't evict IndexedDB under pressure
    if (navigator.storage && typeof navigator.storage.persist === 'function') {
      try {
        this.isPersistent = await navigator.storage.persist();
      } catch {
        this.isPersistent = false;
      }
    }

    if (!('indexedDB' in window)) {
      this.engine = 'localStorage';
      return this;
    }

    try {
      this.db = await new Promise((resolve, reject) => {
        const request = window.indexedDB.open(DB_NAME, DB_VERSION);
        request.onupgradeneeded = (event) => {
          const db = event.target.result;
          if (!db.objectStoreNames.contains(STORE_NAME)) {
            db.createObjectStore(STORE_NAME, { keyPath: 'discountId' });
          }
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error('IndexedDB open error'));
      });
      this.engine = 'IndexedDB';
    } catch {
      this.db = null;
      this.engine = 'localStorage';
    }

    return this;
  }

  /**
   * Retrieve all user state records mapped by discountId.
   *
   * @returns {Promise<Map<string, ReturnType<typeof normalizeUserRecord>>>}
   */
  async getAllMap() {
    const map = new Map();
    const list = await this.getAllList();
    for (const item of list) {
      map.set(item.discountId, item);
    }
    return map;
  }

  /**
   * Retrieve all user state records as an array.
   *
   * @returns {Promise<Array<ReturnType<typeof normalizeUserRecord>>>}
   */
  async getAllList() {
    if (this.db) {
      try {
        const rows = await new Promise((resolve, reject) => {
          const tx = this.db.transaction(STORE_NAME, 'readonly');
          const store = tx.objectStore(STORE_NAME);
          const req = store.getAll();
          req.onsuccess = () => resolve(req.result || []);
          req.onerror = () => reject(req.error);
        });
        const valid = [];
        for (const row of rows) {
          try {
            valid.push(normalizeUserRecord(row));
          } catch {
            // Skip malformed records
          }
        }
        return valid;
      } catch {
        // Fallback to localStorage
      }
    }

    return this._readLocalStorageList();
  }

  /**
   * Save or update a single discount's user state.
   *
   * @param {Partial<ReturnType<typeof normalizeUserRecord>> & {discountId: string}} record
   */
  async saveRecord(record) {
    const normalized = normalizeUserRecord({
      ...record,
      updatedAt: new Date().toISOString()
    });

    // Always mirror to localStorage as secondary safety net on mobile
    this._saveToLocalStorage(normalized);

    if (this.db) {
      await new Promise((resolve, reject) => {
        const tx = this.db.transaction(STORE_NAME, 'readwrite');
        const store = tx.objectStore(STORE_NAME);
        store.put(normalized);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
      });
    }

    return normalized;
  }

  /**
   * Bulk import validated user records (merging or replacing).
   *
   * @param {Array<any>} records
   * @returns {Promise<number>} Count of imported records
   */
  async importRecords(records) {
    if (!Array.isArray(records)) {
      throw new Error('Il backup deve contenere una lista di record.');
    }
    const validated = records.map((r) => normalizeUserRecord(r));

    for (const item of validated) {
      this._saveToLocalStorage(item);
    }

    if (this.db) {
      await new Promise((resolve, reject) => {
        const tx = this.db.transaction(STORE_NAME, 'readwrite');
        const store = tx.objectStore(STORE_NAME);
        for (const item of validated) {
          store.put(item);
        }
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
      });
    }

    return validated.length;
  }

  _readLocalStorageList() {
    try {
      const raw = window.localStorage.getItem(LS_FALLBACK_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') return [];
      const list = [];
      for (const val of Object.values(parsed)) {
        try {
          list.push(normalizeUserRecord(val));
        } catch {
          // Ignore invalid entry
        }
      }
      return list;
    } catch {
      return [];
    }
  }

  _saveToLocalStorage(normalizedRecord) {
    try {
      const raw = window.localStorage.getItem(LS_FALLBACK_KEY);
      const dict = raw ? JSON.parse(raw) : {};
      dict[normalizedRecord.discountId] = normalizedRecord;
      window.localStorage.setItem(LS_FALLBACK_KEY, JSON.stringify(dict));
    } catch {
      // Ignore quota errors if IndexedDB succeeded
    }
  }

  getThemePreference() {
    try {
      const prefs = JSON.parse(window.localStorage.getItem(LS_PREFS_KEY) || '{}');
      return prefs.theme === 'light' || prefs.theme === 'dark' ? prefs.theme : 'dark';
    } catch {
      return 'dark';
    }
  }

  setThemePreference(theme) {
    try {
      const safeTheme = theme === 'light' ? 'light' : 'dark';
      window.localStorage.setItem(LS_PREFS_KEY, JSON.stringify({ theme: safeTheme }));
    } catch {
      // Ignore storage error
    }
  }
}

export const vaultDB = new LocalVaultDB();
