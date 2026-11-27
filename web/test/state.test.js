// The cases of TimerStateTest.kt. Run from web/ with: npm test
import test from 'node:test';
import assert from 'node:assert/strict';
import {
    IDLE, RUNNING, PAUSED, newTimer, remainingAt, started, paused, withDuration, advanced, formatTime,
} from '../js/state.js';

const fiveMin = newTimer({ durationSec: 300 });

test('counts down from the deadline', () => {
    const t = started(fiveMin, 1_000);
    assert.equal(t.status, RUNNING);
    assert.equal(remainingAt(t, 1_000), 300_000);
    assert.equal(remainingAt(t, 61_000), 240_000);
    assert.equal(advanced(t, 300_999), t);
});

test('pause and resume keep the remaining time', () => {
    const p = paused(started(fiveMin, 0), 100_000);
    assert.equal(p.status, PAUSED);
    assert.equal(remainingAt(p, 999_999), 200_000);

    const resumed = started(p, 500_000);
    assert.equal(remainingAt(resumed, 500_000), 200_000);
    assert.equal(resumed.endAt, 700_000);
});

test('completion goes idle at zero, then restarts from full', () => {
    const done = advanced(started(fiveMin, 0), 300_000);
    assert.equal(done.status, IDLE);
    assert.equal(remainingAt(done, 300_000), 0);
    assert.equal(remainingAt(started(done, 400_000), 400_000), 300_000);
});

test('repeat restarts on completion', () => {
    const again = advanced(started({ ...fiveMin, repeat: true }, 0), 300_100);
    assert.equal(again.status, RUNNING);
    assert.equal(again.endAt, 600_100);
});

test('duration only changes while idle', () => {
    assert.equal(withDuration(fiveMin, 600).durationSec, 600);
    assert.equal(withDuration(fiveMin, 600).remainingMs, 600_000);
    assert.equal(withDuration(started(fiveMin, 0), 600).durationSec, 300);
    assert.equal(withDuration(paused(started(fiveMin, 0), 10), 600).durationSec, 300);
});

test('formats like the Android app', () => {
    assert.equal(formatTime(1_500_000), '25:00');
    assert.equal(formatTime(1), '00:01');
    assert.equal(formatTime(0), '00:00');
    assert.equal(formatTime(10_800_000), '180:00');
});
