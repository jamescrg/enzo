// The screen: a port of MainActivity.kt. The state lives in engine.js; this draws it and
// passes on the taps.

import * as engine from './engine.js';
import { state, IDLE, RUNNING, PAUSED, THEMES, RING_STYLES, RING_STYLE_LABELS } from './engine.js';
import { remainingAt, formatTime, DURATION_PRESETS_MIN, MAX_CUSTOM_MINUTES } from './state.js';
import { CHIMES, chimeById, canVibrate, volumeSettable, prepare, stopPreviews } from './audio.js';
import { drawStyle, drawKaleidoscope, loadPictures, MOVING_STYLES } from './draw.js';

const $ = (id) => document.getElementById(id);
const clamp = (x, lo, hi) => Math.min(Math.max(x, lo), hi);

// ── Theme ──────────────────────────────────────────────────────────
// The colours the pictures are drawn in; the rest of the page takes its colours from style.css.

const rgb = (hex) => [hex >> 16 & 255, hex >> 8 & 255, hex & 255];
const PALETTES = {
    /** Everforest Dark */
    dark: {
        bg: rgb(0x2D353B), fg: rgb(0xD3C6AA), btnBg: rgb(0x3D484D), btnHover: rgb(0x475258),
        // The one touch of colour: a muted sandalwood for counted beads.
        wood: rgb(0x998670),
        // The lit side of the moon, and the faintly golden light in the air around it.
        moon: rgb(0xC9BD9F), moonGlow: rgb(0xDCC384),
    },
    /** The dark palette turned inside out. */
    light: {
        bg: rgb(0xD3C6AA), fg: rgb(0x2D353B), btnBg: rgb(0xC3B89F), btnHover: rgb(0xB9AF99),
        wood: rgb(0x745F4C), moon: rgb(0xEDE3C9), moonGlow: rgb(0xF6E8BB),
    },
};
const THEME_COLORS = { dark: '#2D353B', light: '#D3C6AA' };

const systemLight = window.matchMedia('(prefers-color-scheme: light)');
let palette = PALETTES.dark;

function applyTheme() {
    const theme = state.theme === 'system' ? (systemLight.matches ? 'light' : 'dark') : state.theme;
    document.documentElement.dataset.theme = theme;
    document.querySelector('meta[name="theme-color"]').content = THEME_COLORS[theme];
    palette = PALETTES[theme];
}
systemLight.addEventListener('change', () => { applyTheme(); requestFrame(); });

// ── Fades ──────────────────────────────────────────────────────────
// A few values that move smoothly from where they are to where they should be.

/** Material's standard easing, cubic-bezier(0.4, 0, 0.2, 1), as the Android app's fades use. */
function ease(x) {
    const bez = (t, a, b) => 3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t;
    let lo = 0, hi = 1;
    for (let i = 0; i < 16; i++) {
        const mid = (lo + hi) / 2;
        if (bez(mid, 0.4, 0.2) < x) lo = mid; else hi = mid;
    }
    return bez((lo + hi) / 2, 0, 1);
}

const fades = [];
function fade(value) {
    const f = { value, from: value, to: value, start: 0, ms: 0, done: null };
    fades.push(f);
    return f;
}
function fadeTo(f, to, ms, done = null) {
    Object.assign(f, { from: f.value, to, start: performance.now(), ms, done });
    requestFrame();
}
function snapTo(f, value) {
    Object.assign(f, { value, from: value, to: value, ms: 0, done: null });
}
function stepFade(f, ts) {
    if (!f.ms) return;
    const t = clamp((ts - f.start) / f.ms, 0, 1);
    f.value = f.from + (f.to - f.from) * ease(t);
    if (t >= 1) {
        const done = f.done;
        Object.assign(f, { value: f.to, ms: 0, done: null });
        done?.();
    }
}

const controlsFade = fade(1);   // the controls, which fade away while the timer runs
const timeFade = fade(1);       // the countdown, which goes with them if the user wants
const pictureFade = fade(1);    // the picture, which fades out and in between repeats
const smokeFade = fade(0);      // how fully the candle is alight, or the incense smoking

// ── The controls come and go ───────────────────────────────────────
// While running, the controls fade away a few seconds after the last touch, leaving the
// picture and countdown (or just the picture). Any touch brings them back.

let openPage = null;
let controlsVisible = true;
let hideTimer = 0;

function showControls() {
    clearTimeout(hideTimer);
    if (!controlsVisible) {
        controlsVisible = true;
        fadeTo(controlsFade, 1, 250);
    }
    syncTimeFade();
    if (state.timer.status === RUNNING && !openPage) {
        hideTimer = setTimeout(() => {
            controlsVisible = false;
            fadeTo(controlsFade, 0, 1500);
            syncTimeFade();
        }, 4000);
    }
}

function syncTimeFade() {
    const to = state.ringOnly && !controlsVisible ? 0 : 1;
    if (timeFade.to !== to) fadeTo(timeFade, to, controlsVisible ? 250 : 1500);
}

// Every touch is watched on its way down to the buttons. With the controls faded out, a touch
// only brings them back: it is swallowed, so it can't press a button the user couldn't see.
// And once a finger has plainly dragged sideways, lifting it doesn't count as a tap.
let swallow = false;
let sideways = false;
let down = null;

document.addEventListener('pointerdown', (e) => {
    swallow = !controlsVisible;
    sideways = false;
    down = { id: e.pointerId, x: e.clientX, y: e.clientY };
    showControls();
}, true);
document.addEventListener('pointermove', (e) => {
    if (!down || e.pointerId !== down.id) return;
    const dx = Math.abs(e.clientX - down.x), dy = Math.abs(e.clientY - down.y);
    if (dx > 56 && dx > dy) sideways = true;
}, true);
document.addEventListener('click', (e) => {
    if (swallow || sideways) {
        e.stopPropagation();
        e.preventDefault();
    }
    swallow = sideways = false;
}, true);
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && openPage) closePage();
    showControls();
}, true);

// ── Keep screen on ─────────────────────────────────────────────────
// Only while counting down, so a paused timer left on a table doesn't hold the screen on. The
// browser lets go of the lock whenever the page is hidden, so it is asked for again each time
// the page comes back.

const canKeepScreenOn = 'wakeLock' in navigator;
let wakeLock = null;
let wakeLockAsked = false;

async function syncWakeLock() {
    if (!canKeepScreenOn) return;
    const want = state.keepScreenOn && state.timer.status === RUNNING && document.visibilityState === 'visible';
    if (want && !wakeLock && !wakeLockAsked) {
        wakeLockAsked = true;
        try {
            wakeLock = await navigator.wakeLock.request('screen');
            wakeLock.addEventListener('release', () => { wakeLock = null; });
        } catch {
            // Refused, as it is when the battery is low: the timer runs all the same.
        }
        wakeLockAsked = false;
        syncWakeLock();     // in case things changed while waiting
    } else if (!want && wakeLock) {
        const lock = wakeLock;
        wakeLock = null;
        lock.release().catch(() => {});
    }
}

// ── Drawing ────────────────────────────────────────────────────────

const picture = $('picture');
const backdrop = $('backdrop');
const ringBox = picture.parentElement;
const timeIn = $('time-in');
const timeAbove = $('time-above');
const timeAboveSpans = [...timeAbove.querySelectorAll('span')];

/** The picture shows a finished session, while it fades out before a repeat. */
let showFinished = false;
let smokeTime = 0;
let kaleidoscopeTime = 0;
let kaleidoscopeWait = 0;
let shown = {};

const density = () => Math.min(window.devicePixelRatio || 1, 3);

function fit(canvas, width, height, dpr) {
    const w = Math.round(width * dpr), h = Math.round(height * dpr);
    if (canvas.width !== w || canvas.height !== h) {
        canvas.width = w;
        canvas.height = h;
    }
}

function fraction(now) {
    if (showFinished) return 1;
    return 1 - remainingAt(state.timer, now) / (state.timer.durationSec * 1000);
}

/** Puts on the screen everything that changes from moment to moment. */
function render(dt) {
    const now = Date.now();
    const t = state.timer;
    const style = state.ringStyle;
    const running = t.status === RUNNING;
    const f = fraction(now);

    // The countdown. Three-digit minutes need a smaller size to stay inside the ring, and the
    // painted rings are much thicker than the plain one.
    const text = formatTime(remainingAt(t, now));
    const long = text.length > 5;
    if (shown.text !== text || shown.style !== style) {
        timeIn.textContent = text;
        timeIn.style.fontSize = style === 'plain' ? (long ? '58px' : '72px') : (long ? '50px' : '62px');
        timeAboveSpans.forEach((span) => { span.textContent = text; });
        timeAbove.classList.toggle('long', long);
        shown.text = text;
        shown.style = style;
    }

    const root = document.documentElement.style;
    if (shown.controls !== controlsFade.value) root.setProperty('--controls-alpha', shown.controls = controlsFade.value);
    if (shown.time !== timeFade.value) root.setProperty('--time-alpha', shown.time = timeFade.value);

    // The candle lights, and the incense begins to smoke, when the timer starts; both die
    // away when it stops.
    const smoking = MOVING_STYLES.has(style) && running && f < 1 ? 1 : 0;
    if (smokeFade.to !== smoking) fadeTo(smokeFade, smoking, 2000);
    if (smokeFade.value > 0) smokeTime += dt;

    const dpr = density();
    const box = ringBox.clientWidth;
    if (shown.alpha !== pictureFade.value) {
        picture.style.opacity = backdrop.style.opacity = shown.alpha = pictureFade.value;
    }

    fit(picture, box * 2, box * 2, dpr);
    const ctx = picture.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, picture.width, picture.height);
    ctx.setTransform(dpr, 0, 0, dpr, box / 2 * dpr, box / 2 * dpr);
    drawStyle(ctx, style, box, f, smokeTime, smokeFade.value, palette, dpr);

    // The kaleidoscope lies behind the whole screen, centred on the ring. Its shards drift
    // while the timer runs, and slowly enough that some thirty pictures a second are plenty.
    const width = window.innerWidth, height = window.innerHeight;
    fit(backdrop, width, height, dpr);
    const back = backdrop.getContext('2d');
    back.setTransform(1, 0, 0, 1, 0, 0);
    back.clearRect(0, 0, backdrop.width, backdrop.height);
    if (style === 'kaleidoscope') {
        if (running && f < 1) {
            kaleidoscopeWait += dt;
            if (kaleidoscopeWait >= 0.03) {
                kaleidoscopeTime += kaleidoscopeWait;
                kaleidoscopeWait = 0;
            }
        }
        const ring = ringBox.getBoundingClientRect();
        back.setTransform(dpr, 0, 0, dpr, 0, 0);
        drawKaleidoscope(
            back, width, height, ring.left + ring.width / 2, ring.top + ring.height / 2,
            Math.min(ring.width, ring.height), 1 - controlsFade.value, f, kaleidoscopeTime, palette,
        );
    }

    // Whether the picture is still moving: if so it is drawn again at once, and if it is only
    // counting down, ten times a second is enough.
    return fades.some((x) => x.ms) || smokeFade.value > 0 || (style === 'kaleidoscope' && running && f < 1);
}

let frameRequest = 0;
let slowTimer = 0;
let lastFrame = 0;

function requestFrame() {
    if (frameRequest) return;
    clearTimeout(slowTimer);
    slowTimer = 0;
    frameRequest = requestAnimationFrame(frame);
}

function frame(ts) {
    frameRequest = 0;
    const dt = lastFrame ? Math.min((ts - lastFrame) / 1000, 0.25) : 0;
    fades.forEach((f) => stepFade(f, ts));
    const moving = render(dt);
    if (moving) {
        lastFrame = ts;
        requestFrame();
    } else {
        lastFrame = 0;
        if (state.timer.status === RUNNING && !frameRequest) {
            slowTimer = setTimeout(() => { slowTimer = 0; requestFrame(); }, 100);
        }
    }
}

window.addEventListener('resize', requestFrame);
new ResizeObserver(requestFrame).observe(ringBox);
document.addEventListener('visibilitychange', () => {
    syncWakeLock();
    if (document.visibilityState === 'visible') requestFrame();
});

// ── Small parts ────────────────────────────────────────────────────

function element(tag, props = {}, ...children) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (key === 'class') el.className = value;
        else if (key === 'onclick') el.addEventListener('click', value);
        else el.setAttribute(key, value);
    }
    el.append(...children);
    return el;
}

/**
 * A segmented choice. Returns a function that marks one of the choices as the selected one.
 */
function segments(container, labels, perRow, onSelect) {
    container.className = 'segments';
    container.setAttribute('role', 'tablist');
    const buttons = [];
    for (let start = 0; start < labels.length; start += perRow) {
        const row = element('div');
        const inRow = labels.slice(start, start + perRow);
        inRow.forEach((label, k) => {
            const i = start + k;
            const button = element('button', { class: 'segment', type: 'button', role: 'tab', onclick: () => onSelect(i) },
                element('span', {}, label));
            // In a short last row, the last choice takes the room that is left over.
            if (k === inRow.length - 1) button.style.setProperty('--weight', 1 + perRow - inRow.length);
            buttons.push(button);
            row.append(button);
        });
        container.append(row);
    }
    return (selected) => buttons.forEach((b, i) => b.setAttribute('aria-selected', i === selected));
}

function switchRow(id, onChange) {
    const row = $(id);
    row.addEventListener('click', () => onChange(row.getAttribute('aria-checked') !== 'true'));
    return (checked) => row.setAttribute('aria-checked', checked);
}

// ── Pages ──────────────────────────────────────────────────────────
// Each is an entry in the browser's history while it is open, so the phone's back button or
// gesture closes it, as on Android.

const timerScreen = $('screen');
const pages = { duration: $('page-duration'), sound: $('page-sound'), settings: $('page-settings') };
const onOpen = {};

function openPageNamed(name) {
    if (openPage) return;
    openPage = name;
    onOpen[name]?.();
    pages[name].classList.add('open');
    pages[name].inert = false;
    timerScreen.inert = true;
    history.pushState({ page: name }, '');
    showControls();
}

function closePage() {
    if (!openPage) return;
    if (history.state?.page) history.back(); else pageClosed();
}

function pageClosed() {
    if (!openPage) return;
    pages[openPage].classList.remove('open');
    pages[openPage].inert = true;
    timerScreen.inert = false;
    openPage = null;
    stopPreviews();
    showControls();
}

window.addEventListener('popstate', pageClosed);
document.querySelectorAll('.done').forEach((button) => button.addEventListener('click', closePage));
// A page left open by a reload is not open any more.
if (history.state?.page) history.replaceState(null, '');

// ── Timer screen ───────────────────────────────────────────────────

// Tap the time or the ring: pauses a running timer; otherwise opens the duration page (when
// paused, choosing a new duration starts the session over).
function onTime() {
    if (state.timer.status === RUNNING) engine.pause(); else openPageNamed('duration');
}
$('ring').addEventListener('click', onTime);
timeAbove.addEventListener('click', onTime);

$('open-settings').addEventListener('click', () => openPageNamed('settings'));
$('sound-row').addEventListener('click', () => { showSoundTab(0); openPageNamed('sound'); });
const setRepeatSwitch = switchRow('repeat-row', engine.setRepeat);

// Before a session starts there is nothing to reset, so the button sets the duration instead,
// where it is easier to find than a tap on the time. Only a paused session can be reset: a
// running one has to be paused first.
$('reset').addEventListener('click', () => {
    if (state.timer.status === IDLE) openPageNamed('duration'); else engine.reset();
});
$('start').addEventListener('click', () => {
    if (state.timer.status === RUNNING) engine.pause(); else engine.start();
});

// ── Duration page ──────────────────────────────────────────────────
// Scroll wheel for any whole number of minutes, with shortcut buttons for the usual lengths.
// Changes apply as the wheel moves, so leaving the page any way keeps what is shown.

const ROW = 56;
const wheel = $('wheel');
const wheelRows = [];
const presetButtons = new Map();
let centred = -1;       // row i is minute i + 1

const smooth = () => (window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth');
const wheelTo = (minute, behavior = smooth()) => wheel.scrollTo({ top: (minute - 1) * ROW, behavior });

for (const preset of DURATION_PRESETS_MIN) {
    const button = element('button', {
        class: 'preset', type: 'button', 'aria-label': `${preset} minutes`, onclick: () => wheelTo(preset),
    }, String(preset));
    presetButtons.set(preset, button);
    $('presets').append(button);
}
for (let minute = 1; minute <= MAX_CUSTOM_MINUTES; minute++) {
    const row = element('div', { onclick: () => wheelTo(minute) }, String(minute));
    wheelRows.push(row);
    wheel.append(row);
}

function setCentred(index) {
    wheelRows[centred]?.classList.remove('selected');
    centred = index;
    wheelRows[centred].classList.add('selected');
    presetButtons.forEach((button, preset) => button.setAttribute('aria-pressed', preset === centred + 1));
}

wheel.addEventListener('scroll', () => {
    if (openPage !== 'duration') return;
    const index = clamp(Math.round(wheel.scrollTop / ROW), 0, MAX_CUSTOM_MINUTES - 1);
    if (index === centred) return;
    setCentred(index);
    engine.setDuration((index + 1) * 60);
});

onOpen.duration = () => {
    // Fixed when the page opens: the first change resets the timer to idle.
    $('duration-note').hidden = state.timer.status !== PAUSED;
    const minutes = Math.round(state.timer.durationSec / 60);
    setCentred(minutes - 1);
    wheelTo(minutes, 'auto');
};

// ── Sound page ─────────────────────────────────────────────────────

// Tapping a chime chooses it, silently; a separate Listen button sounds the chosen one.
const chimeButtons = new Map();
{
    const list = $('chime-list');
    let previous = null;
    for (const chime of CHIMES) {
        if (chime.vibration && !canVibrate) continue;
        if (!chime.file && previous?.file) list.append(element('div', { class: 'rule' }));
        const button = element('button', {
            class: 'option', type: 'button', role: 'radio', onclick: () => engine.setChime(chime.id),
        }, chime.label);
        chimeButtons.set(chime.id, button);
        list.append(button);
        previous = chime;
    }
    list.setAttribute('role', 'radiogroup');
}

// The chime's own level. A web page cannot set the phone's volume, so there is no System
// slider here; and where the phone will not let a page set even its own (an iPhone), there is
// no Volume tab at all.
let setSoundTab = () => {};
function showSoundTab(index) {
    setSoundTab(index);
    $('chime-list').hidden = index !== 0;
    $('volume').hidden = index !== 1;
}
if (volumeSettable) setSoundTab = segments($('sound-tabs'), ['Chime', 'Volume'], 2, showSoundTab);
else $('sound-tabs').hidden = true;

const slider = $('volume-slider');
slider.addEventListener('input', () => engine.setChimeVolume(slider.value / 100));
slider.addEventListener('change', engine.save);
$('listen').addEventListener('click', engine.testChime);

// The sounds are fetched as the page opens, so that Listen answers at once.
onOpen.sound = () => CHIMES.forEach((chime) => prepare(chime.id));

// ── Settings page ──────────────────────────────────────────────────
// How the phone behaves while the timer runs, and how the timer looks. Android's Do Not
// Disturb switch is not here: a web page cannot silence a phone.

const setKeepScreenOnSwitch = switchRow('keep-screen-on', engine.setKeepScreenOn);
$('keep-screen-on').hidden = !canKeepScreenOn;
const setHideCountdownSwitch = switchRow('hide-countdown', engine.setRingOnly);
const setStyleChoice = segments($('style-choice'), RING_STYLE_LABELS, 3, (i) => engine.setRingStyle(RING_STYLES[i]));
const setThemeChoice = segments($('theme-choice'), ['System', 'Light', 'Dark'], 3, (i) => engine.setTheme(THEMES[i]));

// Keeping the app on the home screen, where it opens full screen like any other app. Android
// offers to do it; on an iPhone the user has to, from the Share menu.
{
    const standalone = navigator.standalone === true || window.matchMedia('(display-mode: standalone)').matches;
    const iPhone = /iPad|iPhone|iPod/.test(navigator.userAgent) ||
        (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    if (iPhone && !standalone) {
        $('install-note').textContent =
            'To keep Quiet on your home screen, open it in Safari, tap Share, then Add to Home Screen. ' +
            'It then opens like any app, with or without a connection.';
        $('install').hidden = false;
    }
    let offer = null;
    window.addEventListener('beforeinstallprompt', (e) => {
        e.preventDefault();
        offer = e;
        $('install-note').textContent = 'Quiet can be kept on your home screen, where it opens like any app, with or without a connection.';
        $('install-button').hidden = false;
        $('install').hidden = false;
    });
    $('install-button').addEventListener('click', async () => {
        if (!offer) return;
        offer.prompt();
        await offer.userChoice.catch(() => {});
        offer = null;
        $('install').hidden = true;
    });
    window.addEventListener('appinstalled', () => { $('install').hidden = true; });
}

// ── Keeping the page in step with the state ────────────────────────

const START_LABELS = { [IDLE]: 'Start', [RUNNING]: 'Pause', [PAUSED]: 'Resume' };

/** Puts on the page everything that only changes when the state does. */
function sync() {
    const t = state.timer;
    const running = t.status === RUNNING;
    const style = state.ringStyle;
    // The pictures that fill their square have their time above them; only the rings have
    // room for it inside.
    const above = style !== 'plain' && style !== 'enso' && style !== 'beads';
    const timeLabel = running ? 'Pause' : 'Set duration';

    timeAbove.hidden = !above;
    timeIn.hidden = above;
    timeAbove.setAttribute('aria-label', timeLabel);
    $('ring').setAttribute('aria-label', timeLabel);
    $('ring').classList.toggle('square', MOVING_STYLES.has(style));

    $('sound-value').textContent = chimeById(t.chime).label;
    setRepeatSwitch(t.repeat);
    $('reset').textContent = t.status === IDLE ? 'Set' : 'Reset';
    $('reset').disabled = running;
    $('start').textContent = START_LABELS[t.status];

    chimeButtons.forEach((button, id) => button.setAttribute('aria-checked', id === t.chime));
    const percent = Math.round(state.chimeVolume * 100);
    $('volume-value').textContent = `${percent}%`;
    if (Number(slider.value) !== percent) slider.value = percent;

    setKeepScreenOnSwitch(state.keepScreenOn);
    setHideCountdownSwitch(state.ringOnly);
    setStyleChoice(RING_STYLES.indexOf(style));
    setThemeChoice(THEMES.indexOf(state.theme));
}

engine.subscribe((before) => {
    const t = state.timer, was = before.timer;
    if (t.status !== was.status || t.endAt !== was.endAt) {
        // When a repeating timer goes straight into its next round, the finished picture fades
        // away and the fresh one fades in, rather than one snapping to the other. A round has
        // repeated when the deadline moves while the timer stays running.
        if (was.status === RUNNING && t.status === RUNNING) {
            showFinished = true;
            fadeTo(pictureFade, 0, 1500, () => {
                showFinished = false;
                fadeTo(pictureFade, 1, 1500);
            });
        } else {
            showFinished = false;
            snapTo(pictureFade, 1);
        }
    }
    if (t.status !== was.status) {
        showControls();
        syncWakeLock();
    }
    if (state.keepScreenOn !== before.keepScreenOn) syncWakeLock();
    if (state.ringOnly !== before.ringOnly) syncTimeFade();
    if (state.theme !== before.theme) applyTheme();
    sync();
    requestFrame();
});

applyTheme();
showSoundTab(0);
sync();
requestFrame();
loadPictures().then(requestFrame);
document.fonts?.ready.then(requestFrame);

// Kept by the browser after the first visit, so the app opens without a connection. Begun once
// the page has what it needs, so the two do not compete for the server.
if ('serviceWorker' in navigator) {
    const register = () => setTimeout(() => navigator.serviceWorker.register('sw.js').catch(() => {}), 1500);
    if (document.readyState === 'complete') register(); else addEventListener('load', register, { once: true });
}
