// Sound, and staying awake to make it.
//
// The Android app keeps a running timer alive with a foreground service. A web page has
// nothing like that: with the screen off, or the browser in the background, a page that is
// doing nothing is put to sleep and its timers stop. The one thing a browser does keep running
// is a page that is playing audio. So a session is played, from Start to the chime, as one
// piece of audio in a single <audio> element: a hush that loops for as long as the timer
// counts down, and then the chime in the same element. The phone treats it like a music
// player, shows it on the lock screen, and lets the chime sound when the time comes.

import { DEFAULT_CHIME } from './state.js';

// Bells and bowls first, roughly highest pitch to lowest, then wood, then the rest. Ids match
// the Android app's settings. `max` is each chime's own level (maxVolume in Chimes.kt), which
// is already baked into its file by build-assets.sh.
export const CHIMES = [
    { id: 'wind', label: 'Tingsha', file: 'tingsha', max: 0.2 },
    { id: 'chime', label: 'Chime', file: 'chime', max: 0.12 },
    { id: 'bell', label: 'Bright Bowl', file: 'bowl_bright', max: 0.2 },
    { id: 'gong', label: 'Deep Bowl', file: 'bowl_deep', max: 0.2 },
    { id: 'keisu', label: 'Keisu', file: 'keisu', max: 0.12 },
    { id: 'bell_chapel', label: 'Chapel Bell', file: 'bell_chapel', max: 0.5 },
    { id: 'moktak', label: 'Moktak (long)', file: 'moktak', max: 1 },
    { id: 'moktak_short', label: 'Moktak (short)', file: 'moktak_short', max: 1 },
    { id: 'clappers', label: 'Clappers', file: 'clappers', max: 1 },
    { id: 'beep', label: 'Beep', file: 'beep', max: 1 },
    // On and off durations in ms, starting with on.
    { id: 'vibrate', label: 'Vibrate', vibration: [500, 250, 500] },
    { id: 'vibrate_long', label: 'Long vibrate', vibration: [4500] },
    // No sound and no vibration: the end is only shown on screen.
    { id: 'silent', label: 'Silent' },
];

export const canVibrate = typeof navigator !== 'undefined' && 'vibrate' in navigator;

export function chimeById(id) {
    return CHIMES.find((c) => c.id === id) ?? CHIMES.find((c) => c.id === DEFAULT_CHIME);
}

/**
 * An iPhone ignores the volume a page sets on its audio and plays everything at the level of
 * the phone's own buttons. Where that is so, the app has no chime volume to offer.
 */
export const volumeSettable = (() => {
    try {
        const probe = new Audio();
        probe.volume = 0.5;
        return probe.volume === 0.5;
    } catch {
        return false;
    }
})();

/**
 * The slider and the chime's own level work as on Android: the chime plays at the slider's
 * level, but never above its own. The file is already at its own level, so what is left to
 * apply is the part of it the slider allows.
 */
function gainFor(chime, volume) {
    return Math.min(Math.max(volume, 0), chime.max) / chime.max;
}

// ── The hush ───────────────────────────────────────────────────────

/**
 * Thirty seconds that loop while the timer counts down. It is not quite digital silence: a
 * browser may count a page playing true silence as playing nothing, and put it to sleep. This
 * is a 20 Hz tone at a five-hundredth of full level, which is below what a phone's speaker can
 * make and what an ear can hear, made here rather than downloaded.
 */
function makeHush() {
    const rate = 8000, seconds = 30, samples = rate * seconds;
    const buffer = new ArrayBuffer(44 + samples * 2);
    const view = new DataView(buffer);
    const text = (at, s) => [...s].forEach((ch, i) => view.setUint8(at + i, ch.charCodeAt(0)));
    text(0, 'RIFF'); view.setUint32(4, 36 + samples * 2, true); text(8, 'WAVE');
    text(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
    view.setUint32(24, rate, true); view.setUint32(28, rate * 2, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true);
    text(36, 'data'); view.setUint32(40, samples * 2, true);
    for (let i = 0; i < samples; i++) {
        view.setInt16(44 + i * 2, Math.round(66 * Math.sin(2 * Math.PI * 20 * i / rate)), true);
    }
    return URL.createObjectURL(new Blob([buffer], { type: 'audio/wav' }));
}

// ── The session's audio element ────────────────────────────────────

let element = null;
let hushUrl = null;
/** What the element is doing: nothing, holding the page awake, or sounding a chime. */
let mode = 'off';
/** After a chime: go back to the hush (the timer is running again) or stop. */
let holdAfterChime = false;
let onTick = () => {};

function session() {
    if (element) return element;
    element = new Audio();
    element.preload = 'auto';
    element.setAttribute('playsinline', '');
    // The audio keeps moving when the page's own timers have been slowed down, so it is also
    // what checks the clock.
    element.addEventListener('timeupdate', () => onTick());
    element.addEventListener('ended', () => {
        if (mode !== 'chime') return;
        if (holdAfterChime) startHush(); else stop();
    });
    element.addEventListener('error', () => {
        if (mode === 'chime') { if (holdAfterChime) startHush(); else stop(); }
    });
    return element;
}

function startHush() {
    const el = session();
    hushUrl ??= makeHush();
    mode = 'hold';
    el.loop = true;
    el.volume = 1;
    el.src = hushUrl;
    el.play().catch(() => {});
}

function stop() {
    mode = 'off';
    if (!element) return;
    element.pause();
    element.removeAttribute('src');
    element.load();
    if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'none';
}

/** Called for every step of the session's audio, so the caller can check the clock. */
export function setTick(fn) { onTick = fn; }

/**
 * Begins holding the page awake. Must be called from a tap: a browser only lets a page start
 * audio when the user has just touched it. A chime still ringing from the last round is left
 * to finish first.
 */
export function hold() {
    if (mode === 'chime') { holdAfterChime = true; session().play().catch(() => {}); return; }
    startHush();
}

/** Stops holding the page awake; a chime that is ringing is left to ring out. */
export function release() {
    if (mode === 'chime') { holdAfterChime = false; return; }
    stop();
}

/** If the phone stopped the audio (a call, another app), start it again when the page is back. */
export function resume() {
    if (mode !== 'off' && element?.paused) element.play().catch(() => {});
}

// ── Chimes ─────────────────────────────────────────────────────────

// Each chime is fetched whole and played from memory, so it is ready at once, plays offline,
// and never needs the network at the moment the session ends.
const loaded = new Map();

export function prepare(chimeId) {
    const chime = chimeById(chimeId);
    if (!chime.file) return Promise.resolve(null);
    if (!loaded.has(chime.id)) {
        const url = `audio/${chime.file}.mp3`;
        loaded.set(chime.id, fetch(url)
            .then((r) => { if (!r.ok) throw new Error(r.status); return r.blob(); })
            .then((blob) => URL.createObjectURL(blob))
            .catch(() => { loaded.delete(chime.id); return url; }));
    }
    return loaded.get(chime.id);
}

/**
 * The session has ended: sound its chime. `running` says whether the timer has gone straight
 * into another round and still needs holding awake afterwards.
 */
export async function finish(chimeId, volume, running) {
    const chime = chimeById(chimeId);
    if (!chime.file) {
        if (chime.vibration && canVibrate) navigator.vibrate(chime.vibration);
        if (!running) stop();
        return;
    }
    const el = session();
    mode = 'chime';
    holdAfterChime = running;
    const url = await prepare(chime.id);
    if (mode !== 'chime') return;       // paused or reset while the sound was being fetched
    el.loop = false;
    el.volume = gainFor(chime, volume);
    el.src = url;
    el.play().catch(() => { if (holdAfterChime) startHush(); else stop(); });
}

const previews = new Set();

/** Sounds a chime to hear what it is like, cutting off any earlier one so they don't pile up. */
export async function preview(chimeId, volume) {
    stopPreviews();
    const chime = chimeById(chimeId);
    if (!chime.file) {
        if (chime.vibration && canVibrate) navigator.vibrate(chime.vibration);
        return;
    }
    const el = new Audio();
    previews.add(el);
    el.addEventListener('ended', () => previews.delete(el));
    el.volume = gainFor(chime, volume);
    // The sounds are fetched when the Sound page opens, so this is ready within the tap.
    el.src = await prepare(chime.id);
    if (previews.has(el)) el.play().catch(() => previews.delete(el));
}

export function stopPreviews() {
    previews.forEach((el) => el.pause());
    previews.clear();
    if (canVibrate) navigator.vibrate(0);
}

// ── Lock screen ────────────────────────────────────────────────────

/**
 * What the phone shows for the session on its lock screen and among its notifications, where
 * Android shows the running timer: the name, how far along it is, and a pause button.
 */
export function showSession({ running, durationSec, elapsedSec, onPause, onPlay }) {
    if (!('mediaSession' in navigator)) return;
    const ms = navigator.mediaSession;
    try {
        ms.metadata = new MediaMetadata({
            title: 'Tacet',
            artist: running ? 'Timer running' : 'Timer paused',
            artwork: [{ src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' }],
        });
        ms.playbackState = running ? 'playing' : 'paused';
        ms.setActionHandler('pause', onPause);
        ms.setActionHandler('play', onPlay);
        ms.setPositionState({
            duration: durationSec, playbackRate: 1,
            position: Math.min(Math.max(elapsedSec, 0), durationSec),
        });
    } catch {
        // An older browser without some of this: the session still runs.
    }
}
