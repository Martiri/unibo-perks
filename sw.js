/**
 * Service Worker — UniBo & Student Perks Vault
 *
 * Security & Offline Strategy:
 * - Same-origin only: ignores any non-GET or cross-origin requests.
 * - Stale-While-Revalidate for `./data/discounts.json` so the app loads
 *   instantly offline while keeping the catalog fresh when online.
 * - Cache-First with network fallback for the static App Shell.
 */

const CACHE_VERSION = 'unibo-perks-v1.4.0';
const APP_SHELL_ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './css/app.css',
  './js/app.js',
  './js/db.js',
  './js/dom-safe.js',
  './js/crypto-backup.js',
  './assets/icons/sprite.svg',
  './assets/icons/icon-192.png',
  './assets/icons/icon-512.png',
  './assets/icons/apple-touch-icon.png',
  './data/discounts.json',
  './data/platforms.json'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_VERSION).then((cache) => cache.addAll(APP_SHELL_ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys
          .filter((key) => key !== CACHE_VERSION)
          .map((oldKey) => caches.delete(oldKey))
      )
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') {
    return;
  }

  const url = new URL(request.url);
  // Defense-in-depth: never intercept or cache cross-origin requests
  if (url.origin !== self.location.origin) {
    return;
  }

  // Stale-While-Revalidate for the catalog & platforms JSON
  if (url.pathname.endsWith('/data/discounts.json') || url.pathname.endsWith('/data/platforms.json')) {
    event.respondWith(
      caches.open(CACHE_VERSION).then(async (cache) => {
        const cachedResponse = await cache.match(request);
        const networkFetch = fetch(request)
          .then((networkResponse) => {
            if (networkResponse && networkResponse.status === 200) {
              cache.put(request, networkResponse.clone());
            }
            return networkResponse;
          })
          .catch(() => null);

        return cachedResponse || (await networkFetch) || new Response('[]', {
          status: 200,
          headers: { 'Content-Type': 'application/json; charset=utf-8' }
        });
      })
    );
    return;
  }

  // Cache-First for App Shell assets
  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) {
        return cached;
      }
      return fetch(request).then((response) => {
        if (response && response.status === 200 && response.type === 'basic') {
          const clone = response.clone();
          caches.open(CACHE_VERSION).then((cache) => cache.put(request, clone));
        }
        return response;
      });
    })
  );
});
