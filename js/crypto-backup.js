/**
 * Client-Side Backup & Restore with Web Crypto API (AES-GCM 256-bit + PBKDF2)
 *
 * Allows exporting personal discount codes and states either as:
 * 1. Encrypted JSON envelope (AES-GCM 256-bit, PBKDF2-HMAC-SHA256, 210,000 iterations)
 * 2. Plain JSON (schema-validated)
 */

import { normalizeUserRecord } from './db.js';

const BACKUP_SCHEMA_ID = 'unibo-student-perks-backup-v1';
const PBKDF2_ITERATIONS = 210000;

function bytesToBase64(bytes) {
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return window.btoa(binary);
}

function base64ToBytes(b64) {
  const binary = window.atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

async function deriveAesKey(passphrase, saltBytes) {
  const enc = new TextEncoder();
  const keyMaterial = await window.crypto.subtle.importKey(
    'raw',
    enc.encode(passphrase),
    'PBKDF2',
    false,
    ['deriveKey']
  );

  return window.crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: saltBytes,
      iterations: PBKDF2_ITERATIONS,
      hash: 'SHA-256'
    },
    keyMaterial,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

/**
 * Create a JSON string for backup download.
 *
 * @param {Array<any>} records
 * @param {string} [passphrase] Optional passphrase; if non-empty, encrypts via AES-GCM.
 * @returns {Promise<string>}
 */
export async function createBackupPayload(records, passphrase = '') {
  const cleanRecords = records.map((r) => normalizeUserRecord(r));
  const cleanPass = String(passphrase || '').trim();

  if (!cleanPass) {
    return JSON.stringify(
      {
        schema: BACKUP_SCHEMA_ID,
        encrypted: false,
        exportedAt: new Date().toISOString(),
        records: cleanRecords
      },
      null,
      2
    );
  }

  if (!window.crypto || !window.crypto.subtle) {
    throw new Error('Web Crypto API non disponibile in questo contesto.');
  }

  const salt = window.crypto.getRandomValues(new Uint8Array(16));
  const iv = window.crypto.getRandomValues(new Uint8Array(12));
  const key = await deriveAesKey(cleanPass, salt);

  const plaintextBytes = new TextEncoder().encode(JSON.stringify(cleanRecords));
  const ciphertextBuffer = await window.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    plaintextBytes
  );

  return JSON.stringify(
    {
      schema: BACKUP_SCHEMA_ID,
      encrypted: true,
      kdf: 'PBKDF2-SHA256',
      iterations: PBKDF2_ITERATIONS,
      cipher: 'AES-256-GCM',
      exportedAt: new Date().toISOString(),
      salt: bytesToBase64(salt),
      iv: bytesToBase64(iv),
      ciphertext: bytesToBase64(new Uint8Array(ciphertextBuffer))
    },
    null,
    2
  );
}

/**
 * Parse and validate an imported backup JSON string.
 *
 * @param {string} rawJsonText
 * @param {string} [passphrase]
 * @returns {Promise<Array<ReturnType<typeof normalizeUserRecord>>>}
 */
export async function parseBackupPayload(rawJsonText, passphrase = '') {
  if (typeof rawJsonText !== 'string' || rawJsonText.length > 2 * 1024 * 1024) {
    throw new Error('File di backup vuoto o troppo grande (max 2MB).');
  }

  let envelope;
  try {
    envelope = JSON.parse(rawJsonText);
  } catch {
    throw new Error('Il file selezionato non è un JSON valido.');
  }

  if (!envelope || typeof envelope !== 'object' || envelope.schema !== BACKUP_SCHEMA_ID) {
    throw new Error('Formato schema di backup non riconosciuto.');
  }

  let rawRecords;
  if (envelope.encrypted === true) {
    const cleanPass = String(passphrase || '').trim();
    if (!cleanPass) {
      throw new Error('Questo backup è cifrato: inserisci la passphrase per sbloccarlo.');
    }
    try {
      const salt = base64ToBytes(String(envelope.salt || ''));
      const iv = base64ToBytes(String(envelope.iv || ''));
      const ciphertext = base64ToBytes(String(envelope.ciphertext || ''));
      const key = await deriveAesKey(cleanPass, salt);
      const decryptedBuffer = await window.crypto.subtle.decrypt(
        { name: 'AES-GCM', iv },
        key,
        ciphertext
      );
      const decodedText = new TextDecoder().decode(decryptedBuffer);
      rawRecords = JSON.parse(decodedText);
    } catch {
      throw new Error('Decifratura fallita: passphrase errata o file di backup alterato.');
    }
  } else {
    rawRecords = envelope.records;
  }

  if (!Array.isArray(rawRecords) || rawRecords.length > 1000) {
    throw new Error('Contenuto record del backup non valido.');
  }

  return rawRecords.map((r) => normalizeUserRecord(r));
}
