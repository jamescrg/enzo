// Keeps the whole app in the browser after the first visit, so it opens with no connection.
//
// Everything is answered from the copy kept here, at once, and fetched again in the background
// for next time. So a new version put on the server shows the second time the app is opened
// after that, with nothing here to change: only add a file to FILES when the app gains one.

const CACHE = 'quiet';
const FILES = [
    './',
    'style.css',
    'manifest.webmanifest',
    'js/app.js', 'js/audio.js', 'js/draw.js', 'js/engine.js', 'js/enso-data.js', 'js/state.js',
    'img/beads.png', 'img/candle.png', 'img/candle_dish.png', 'img/candle_flame.png',
    'img/incense.png', 'img/incense_dish.png', 'img/moon.png',
    'audio/tingsha.mp3', 'audio/chime.mp3', 'audio/bowl_bright.mp3', 'audio/bowl_deep.mp3',
    'audio/keisu.mp3', 'audio/bell_chapel.mp3', 'audio/moktak.mp3', 'audio/moktak_short.mp3',
    'audio/clappers.mp3', 'audio/beep.mp3',
    'icons/favicon.png', 'icons/apple-touch-icon.png', 'icons/icon-192.png', 'icons/icon-512.png',
    'icons/icon-maskable-512.png',
];

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Fetches one file for keeping. A server that limits how fast one visitor may ask (429) is
 * asked again after a pause, rather than the whole install failing for it.
 */
async function fetchToKeep(file) {
    for (let attempt = 0; ; attempt++) {
        const response = await fetch(new Request(file, { cache: 'no-cache' }));
        if (response.ok && !response.redirected) return response;
        if (response.status !== 429 || attempt >= 8) throw new Error(`${file}: ${response.status}`);
        await wait(1000);
    }
}

// One file at a time, so as not to arrive at the server as a burst of thirty requests.
self.addEventListener('install', (event) => {
    event.waitUntil((async () => {
        const cache = await caches.open(CACHE);
        for (const file of FILES) await cache.put(file, await fetchToKeep(file));
        await self.skipWaiting();
    })());
});

self.addEventListener('activate', (event) => {
    // The copy kept under the app's earlier name is dropped.
    event.waitUntil(caches.delete('tacet').then(() => self.clients.claim()));
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    // Only this app's own files, asked for whole. (The app plays its sounds from memory, so
    // the browser never asks for part of one.)
    if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin || request.headers.has('range')) return;

    // Any page of the app is the one page.
    const key = request.mode === 'navigate' ? './' : request;
    event.respondWith((async () => {
        const cache = await caches.open(CACHE);
        const kept = await cache.match(key, { ignoreSearch: true });
        const fresh = fetch(key, { cache: 'no-cache' }).then((response) => {
            // A redirect is the server's login page standing in for the file: never kept.
            if (response.ok && !response.redirected) cache.put(key, response.clone());
            return response;
        });
        if (!kept) return fresh;
        event.waitUntil(fresh.catch(() => {}));
        return kept;
    })());
});
