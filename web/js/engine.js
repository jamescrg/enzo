// The app's state: the timer and the settings, kept in one place and saved in the browser.
// A port of TimerEngine.kt.

import * as audio from './audio.js';
import {
    IDLE, RUNNING, PAUSED, MAX_CUSTOM_MINUTES, newTimer, started, paused, reset as resetTimer,
    withDuration, advanced, remainingAt,
} from './state.js';

export const THEMES = ['system', 'light', 'dark'];

/** How progress is drawn: a plain ring, a painted ensō, a round of beads, or one of the pictures. */
export const RING_STYLES = ['plain', 'enso', 'beads', 'incense', 'labyrinth', 'candle', 'moon', 'kaleidoscope'];
export const RING_STYLE_LABELS = ['Plain', 'Ensō', 'Beads', 'Incense', 'Labyrinth', 'Candle', 'Moon', 'Kaleidoscope'];

const TICK_MS = 200;
const STORAGE_KEY = 'settings';

export const state = {
    timer: newTimer(),
    theme: 'system',
    chimeVolume: 0.8,
    /** Keep the display awake while the timer runs. */
    keepScreenOn: false,
    /** Hide the countdown along with the controls while running, leaving only the picture. */
    ringOnly: false,
    ringStyle: 'enso',
};

const listeners = new Set();

/** Calls `fn` after every change, with the state as it was before. */
export function subscribe(fn) { listeners.add(fn); }

function change(patch) {
    const before = { ...state };
    Object.assign(state, patch);
    listeners.forEach((fn) => fn(before));
}

const now = () => Date.now();

// ── Timer controls ────────────────────────────────────────────

let ticker = 0;

/** Must be called from a tap, so that the browser lets the session's audio begin. */
export function start() {
    dismissFinished();
    if (state.timer.status === RUNNING) return;
    change({ timer: started(state.timer, now()) });
    audio.prepare(state.timer.chime);
    audio.hold();
    showOnLockScreen();
    if (!ticker) ticker = setInterval(tick, TICK_MS);
}

export function pause() {
    if (state.timer.status !== RUNNING) return;
    change({ timer: paused(state.timer, now()) });
    // Nothing is held awake while paused, so a paused timer that gets forgotten costs nothing.
    audio.release();
    stopTicker();
}

export function reset() {
    dismissFinished();
    change({ timer: resetTimer(state.timer) });
    audio.release();
    stopTicker();
}

function stopTicker() {
    clearInterval(ticker);
    ticker = 0;
}

/**
 * Checks the clock, and ends the session if its time has come. Called several times a second
 * while running, by the page's own timer and by the audio that keeps the page awake, and
 * whenever the page comes back into view: whichever of them is still being let run.
 */
export function tick() {
    const before = state.timer;
    const after = advanced(before, now());
    if (after === before) return;       // advanced() returns the same timer unless it just completed
    change({ timer: after });
    const running = after.status === RUNNING;
    audio.finish(before.chime, state.chimeVolume, running);
    if (before.chime === 'silent') showFinished();
    if (running) showOnLockScreen(); else stopTicker();
}

function showOnLockScreen() {
    const t = state.timer;
    audio.showSession({
        running: t.status === RUNNING,
        durationSec: t.durationSec,
        elapsedSec: t.durationSec - remainingAt(t, now()) / 1000,
        onPause: pause,
        onPlay: start,
    });
}

/**
 * Sets a new session length. Allowed when idle, and when paused, where it abandons the paused
 * session and starts over at the new length. Ignored while running.
 */
export function setDuration(seconds) {
    if (seconds === state.timer.durationSec) return;
    if (state.timer.status === PAUSED) reset();
    change({ timer: withDuration(state.timer, seconds) });
    save();
}

export function setChime(chime) {
    change({ timer: { ...state.timer, chime } });
    audio.prepare(chime);
    // The silent ending is a notice on the screen, which the phone has to be asked to allow.
    if (chime === 'silent' && 'Notification' in window && Notification.permission === 'default') {
        Promise.resolve(Notification.requestPermission()).catch(() => {});
    }
    save();
}

export function setRepeat(repeat) {
    change({ timer: { ...state.timer, repeat } });
    save();
}

// ── Global settings ───────────────────────────────────────────

/** Preview the selected chime. */
export function testChime() {
    audio.preview(state.timer.chime, state.chimeVolume);
}

export function setTheme(theme) {
    change({ theme: THEMES.includes(theme) ? theme : 'system' });
    save();
}

export function setRingStyle(style) {
    change({ ringStyle: RING_STYLES.includes(style) ? style : 'enso' });
    save();
}

export function setRingOnly(enabled) {
    change({ ringOnly: enabled });
    save();
}

export function setKeepScreenOn(enabled) {
    change({ keepScreenOn: enabled });
    save();
}

/** Call save() when the user finishes adjusting. */
export function setChimeVolume(volume) {
    change({ chimeVolume: Math.min(Math.max(volume, 0), 1) });
}

// ── The silent ending ─────────────────────────────────────────

const FINISHED_TAG = 'finished';

/**
 * Silent option: a notice that shows on the screen (and the lock screen) with no sound or
 * vibration. Only if the phone allows this page notices; the end is shown in the app either way.
 */
async function showFinished() {
    try {
        if (!('Notification' in window) || Notification.permission !== 'granted') return;
        const registration = await navigator.serviceWorker?.ready;
        await registration?.showNotification('Tacet', {
            body: 'Session complete', tag: FINISHED_TAG, silent: true, icon: 'icons/icon-192.png',
        });
    } catch {
        // No notices here.
    }
}

/** Clears a leftover "Session complete" notice, e.g. when the next session starts. */
async function dismissFinished() {
    try {
        const registration = await navigator.serviceWorker?.getRegistration();
        const notices = await registration?.getNotifications({ tag: FINISHED_TAG });
        notices?.forEach((n) => n.close());
    } catch {
        // No notices here.
    }
}

// ── Persistence (same JSON shape as the Android app's settings) ──

export function save() {
    const t = state.timer;
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({
            version: 3,
            theme: state.theme,
            chimeVolume: state.chimeVolume,
            keepScreenOn: state.keepScreenOn,
            ringOnly: state.ringOnly,
            ringStyle: state.ringStyle,
            timers: [{ duration: t.durationSec, repeat: t.repeat, chime: t.chime }],
        }));
    } catch {
        // Private browsing, or storage full: the settings last for this visit only.
    }
}

function load() {
    let json = {};
    try { json = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') ?? {}; } catch { /* start fresh */ }
    const number = (value, fallback) => (Number.isFinite(value) ? value : fallback);
    const clamp = (x, lo, hi) => Math.min(Math.max(x, lo), hi);

    state.theme = THEMES.includes(json.theme) ? json.theme : 'system';
    state.chimeVolume = clamp(number(json.chimeVolume, 0.8), 0, 1);
    state.keepScreenOn = json.keepScreenOn === true;
    state.ringOnly = json.ringOnly === true;
    state.ringStyle = RING_STYLES.includes(json.ringStyle) ? json.ringStyle : 'enso';

    const t = json.timers?.[0] ?? {};
    let chime = audio.chimeById(t.chime);
    // A phone that cannot vibrate is not offered the vibrate endings.
    if (chime.vibration && !audio.canVibrate) chime = audio.chimeById(null);
    state.timer = newTimer({
        durationSec: clamp(Math.round(number(t.duration, 25 * 60)), 60, MAX_CUSTOM_MINUTES * 60),
        repeat: t.repeat === true,
        chime: chime.id,
    });
}

load();
audio.setTick(tick);
document.addEventListener('visibilitychange', () => {
    if (document.visibilityState !== 'visible') return;
    tick();
    if (state.timer.status === RUNNING) audio.resume();
});

export { IDLE, RUNNING, PAUSED };
