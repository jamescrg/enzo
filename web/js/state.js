// The timer itself, with nothing of the browser in it. A port of TimerState.kt: the same
// rules, tested by the same cases (web/test/state.test.js).

export const IDLE = 'idle';
export const RUNNING = 'running';
export const PAUSED = 'paused';

export const MAX_CUSTOM_MINUTES = 180;
export const DURATION_PRESETS_MIN = [5, 10, 15, 20, 25, 30];
export const DEFAULT_CHIME = 'bell';

/**
 * A timer. Times are milliseconds on one clock (Date.now() in the app), so a running timer is
 * defined by its deadline rather than by a counter that has to be decremented: however long the
 * page was asleep, the time left is right the moment it wakes.
 *
 * Timers are never changed, only replaced: each function returns a new one, or the same one
 * if nothing changed.
 */
export function newTimer({ durationSec = 25 * 60, repeat = false, chime = DEFAULT_CHIME } = {}) {
    return {
        durationSec, repeat, chime,
        status: IDLE,
        remainingMs: durationSec * 1000,   // time left; meaningful while not running
        endAt: 0,                          // deadline; meaningful while running
    };
}

export function remainingAt(t, now) {
    if (t.status !== RUNNING) return t.remainingMs;
    return Math.min(Math.max(t.endAt - now, 0), t.durationSec * 1000);
}

/** Start from idle (full duration) or resume from pause. */
export function started(t, now) {
    if (t.status === IDLE) return { ...t, status: RUNNING, endAt: now + t.durationSec * 1000 };
    if (t.status === PAUSED) return { ...t, status: RUNNING, endAt: now + t.remainingMs };
    return t;
}

export function paused(t, now) {
    return t.status === RUNNING ? { ...t, status: PAUSED, remainingMs: remainingAt(t, now) } : t;
}

export function reset(t) {
    return { ...t, status: IDLE, remainingMs: t.durationSec * 1000 };
}

/** Duration can only change while idle. */
export function withDuration(t, seconds) {
    return t.status === IDLE ? { ...t, durationSec: seconds, remainingMs: seconds * 1000 } : t;
}

/**
 * Returns the same timer if it has not finished by `now`; otherwise the timer after
 * completion: restarted if repeating, else idle showing 00:00.
 */
export function advanced(t, now) {
    if (t.status !== RUNNING || now < t.endAt) return t;
    if (t.repeat) return { ...t, endAt: now + t.durationSec * 1000 };
    return { ...t, status: IDLE, remainingMs: 0 };
}

export function formatTime(remainingMs) {
    const totalSeconds = Math.floor((remainingMs + 999) / 1000);
    const pad = (n) => String(n).padStart(2, '0');
    return `${pad(Math.floor(totalSeconds / 60))}:${pad(totalSeconds % 60)}`;
}
