// The pictures that show a session's progress: ports of the Android app's drawing code
// (EnsoStroke.kt, Beads.kt, Incense.kt, Labyrinth.kt, Candle.kt, Moon.kt, Kaleidoscope.kt),
// keeping its numbers. The sources of the photographs are in the README.
//
// Every picture is drawn in a square box whose top left corner is the origin; lengths are in
// CSS pixels and the canvas is already scaled for the screen's density. Colours are [r, g, b]
// with channels from 0 to 255.

import { ENSO_PATH, ENSO_HEAD_HAIRS, ENSO_INNER, ENSO_OUTER } from './enso-data.js';

const TAU = 2 * Math.PI;
const RAD = Math.PI / 180;

export const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
export const css = (c, alpha = 1) => `rgba(${Math.round(c[0])},${Math.round(c[1])},${Math.round(c[2])},${alpha})`;
const clamp = (x, lo, hi) => Math.min(Math.max(x, lo), hi);

// ── Pictures ───────────────────────────────────────────────────────

const PICTURES = ['beads', 'candle', 'candle_dish', 'candle_flame', 'incense', 'incense_dish', 'moon'];
const images = {};

/** Loads the photographs; resolves when all of them can be drawn. */
export function loadPictures() {
    return Promise.all(PICTURES.map((name) => new Promise((resolve) => {
        const img = new Image();
        img.onload = () => { images[name] = img; resolve(); };
        img.onerror = resolve;      // a missing picture leaves its style blank, not the app broken
        img.src = `img/${name}.png`;
    })));
}

const tinted = new Map();

/**
 * A grey picture turned into `color`: lighter where the picture is above mid-grey and darker
 * below, by `shading`. The pictures are mid-grey on average, so a theme colour comes out as
 * itself, with the photograph's light and shade on it. Made once for each colour and kept.
 */
function shaded(name, color, shading) {
    const img = images[name];
    if (!img) return null;
    const key = `${name}|${color.map(Math.round)}|${shading}`;
    let canvas = tinted.get(key);
    if (canvas) return canvas;
    if (tinted.size > 40) tinted.clear();       // colours only change with the theme
    canvas = document.createElement('canvas');
    canvas.width = img.naturalWidth;
    canvas.height = img.naturalHeight;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(img, 0, 0);
    const data = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const d = data.data;
    const offset = color.map((channel) => channel - shading * 127.5);
    for (let i = 0; i < d.length; i += 4) {
        const grey = d[i];
        d[i] = shading * grey + offset[0];
        d[i + 1] = shading * grey + offset[1];
        d[i + 2] = shading * grey + offset[2];
    }
    ctx.putImageData(data, 0, 0);
    tinted.set(key, canvas);
    return canvas;
}

// Spare canvases for the parts that are drawn apart and then laid on the picture.
const layers = new Map();

function layer(name, width, height, dpr) {
    let canvas = layers.get(name);
    if (!canvas) layers.set(name, canvas = document.createElement('canvas'));
    const w = Math.max(1, Math.ceil(width * dpr));
    const h = Math.max(1, Math.ceil(height * dpr));
    if (canvas.width !== w || canvas.height !== h) {
        canvas.width = w;
        canvas.height = h;
    }
    const ctx = canvas.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.globalCompositeOperation = 'source-over';
    ctx.globalAlpha = 1;
    ctx.clearRect(0, 0, w, h);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return ctx;
}

function radial(ctx, x, y, radius, stops) {
    const g = ctx.createRadialGradient(x, y, 0, x, y, radius);
    for (const [at, color] of stops) g.addColorStop(at, color);
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, TAU);
    ctx.fill();
}

// ── Plain ring ─────────────────────────────────────────────────────

function drawPlain(ctx, box, f, p) {
    const stroke = 14;
    const r = (box - stroke) / 2;
    ctx.lineWidth = stroke;
    ctx.strokeStyle = css(p.btnBg);
    ctx.lineCap = 'butt';
    ctx.beginPath();
    ctx.arc(box / 2, box / 2, r, 0, TAU);
    ctx.stroke();
    if (f > 0) {
        ctx.strokeStyle = css(p.fg);
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.arc(box / 2, box / 2, r, -Math.PI / 2, -Math.PI / 2 + TAU * f);
        ctx.stroke();
    }
}

// ── Ensō ───────────────────────────────────────────────────────────
// A drawn ensō, filled in from its thin tail to its heavy head as the timer runs. Nothing here
// imitates a brush stroke being painted; the shape is the artwork and progress only uncovers it.
// Angles are in degrees clockwise from 12 o'clock, around the middle of the ensō's opening, in
// the artwork's original orientation.

const ENSO = {
    cx: 73.79, cy: 75.58,       // middle of the opening, in the artwork's own coordinates
    maxRadius: 61.5,            // furthest the ink reaches from that middle
    gap: 205, sweep: 357,       // where the thin tail ends, and how far round the fill runs
    oversize: 1.06,             // drawn a little beyond its box, which is sized for the plain ring
    step: 3,                    // the edge tables hold a radius every 3 degrees
    capPoints: 32,
};
let ensoPath = null;

function ensoAt(table, degrees) {
    const pos = ((degrees % 360) + 360) % 360 / ENSO.step;
    const i = Math.floor(pos) % table.length;
    const t = pos - Math.floor(pos);
    return table[i] * (1 - t) + table[(i + 1) % table.length] * t;
}

function drawEnso(ctx, box, f, p) {
    if (!ensoPath) {
        // The stroke, plus fine bristle marks lying just off the two edges of the head.
        ensoPath = new Path2D(ENSO_PATH);
        ensoPath.addPath(new Path2D(ENSO_HEAD_HAIRS));
    }
    const { cx, cy, maxRadius, gap, sweep } = ENSO;
    const scale = box / 2 / maxRadius * ENSO.oversize;
    ctx.save();
    // The artwork is mirrored and turned so its thin tail starts at 12 o'clock and the fill
    // runs clockwise from the tail round to the heavy head.
    ctx.translate(box / 2 - cx * scale, box / 2 - cy * scale);
    ctx.scale(scale, scale);
    ctx.translate(cx, cy);
    ctx.rotate(gap * RAD);
    ctx.scale(-1, 1);
    ctx.translate(-cx, -cy);

    ctx.fillStyle = css(p.btnBg);
    ctx.fill(ensoPath);
    ctx.fillStyle = css(p.fg);
    if (f >= 1) {
        ctx.fill(ensoPath);
    } else if (f > 0) {
        // Everything below is in the artwork's own coordinates, where the fill runs
        // anticlockwise from the gap. Uncovered so far: a wedge, finished with a rounded front
        // as wide as the stroke there. The front's foremost point is the current position, so
        // the fill reaches the end of the head exactly as the session ends; the rounding
        // flattens out over the last stretch (and grows in over the first) to fit.
        const tip = gap - f * sweep;
        const tipMid = (ensoAt(ENSO_INNER, tip) + ensoAt(ENSO_OUTER, tip)) / 2;
        const degToLen = tipMid * RAD;
        const done = f * sweep * degToLen;
        const left = (1 - f) * sweep * degToLen;
        const reach = Math.min((ensoAt(ENSO_OUTER, tip) - ensoAt(ENSO_INNER, tip)) / 2, done, left);
        const back = tip + reach / degToLen;
        const inner = ensoAt(ENSO_INNER, back);
        const outer = ensoAt(ENSO_OUTER, back);
        const mid = (inner + outer) / 2;
        const half = (outer - inner) / 2 + 0.5;
        const sinBack = Math.sin(back * RAD);
        const cosBack = Math.cos(back * RAD);

        // The wedge and the front are each used as a window on the ink in turn; the ink is
        // solid, so where the two overlap nothing shows.
        ctx.save();
        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.arc(cx, cy, maxRadius * 1.5, (gap - 90) * RAD, (back - 90) * RAD, true);
        ctx.closePath();
        ctx.clip();
        ctx.fill(ensoPath);
        ctx.restore();

        // Half an ellipse standing on the wedge's edge: `half` across the stroke, `reach` ahead.
        ctx.save();
        ctx.beginPath();
        for (let i = 0; i <= ENSO.capPoints; i++) {
            const phi = Math.PI * (i / ENSO.capPoints - 0.5);
            const across = mid + half * Math.sin(phi);
            const ahead = reach * Math.cos(phi);
            const x = cx + across * sinBack - ahead * cosBack;
            const y = cy - across * cosBack - ahead * sinBack;
            if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        }
        ctx.closePath();
        ctx.clip();
        ctx.fill(ensoPath);
        ctx.restore();
    }
    ctx.restore();
}

// ── Beads ──────────────────────────────────────────────────────────
// A loop of prayer beads, counted off one by one as the session runs: a cord, a round of
// beads, and one larger bead at the top where the round begins and ends.

const BEADS = {
    count: 27,              // beads in the round, not counting the larger one: a quarter of a mala's 108
    marker: 1.35,           // the larger bead's width, in ordinary beads
    tiles: 10,              // nine for the round, used in turn, and the last for the larger bead
    inkShading: 0.5,
    trackShading: 0.22,
    fade: 0.5,              // the part of a bead's share of the session over which it is counted
};

function drawBeads(ctx, box, f, p) {
    const { count, marker, tiles } = BEADS;
    const gap = marker + 0.15;
    const slotAngle = TAU / (count + gap);
    // Sized so the larger bead just fits inside the box.
    const ringRadius = box / 2 / (1 + marker * 0.47 * slotAngle);
    const bead = ringRadius * slotAngle * 0.94;
    const centre = box / 2;
    const track = shaded('beads', p.btnBg, BEADS.trackShading);
    const ink = shaded('beads', p.wood, BEADS.inkShading);
    if (!track) return;
    const tile = track.height;

    function draw(strip, index, slot, diameter, alpha) {
        const a = -Math.PI / 2 + slot * slotAngle;
        const px = Math.round(diameter);
        ctx.globalAlpha = alpha;
        ctx.drawImage(
            strip, index * tile, 0, tile, tile,
            centre + ringRadius * Math.cos(a) - px / 2, centre + ringRadius * Math.sin(a) - px / 2, px, px,
        );
        ctx.globalAlpha = 1;
    }

    // The cord.
    ctx.strokeStyle = css(p.btnBg);
    ctx.lineWidth = bead * 0.07;
    ctx.beginPath();
    ctx.arc(centre, centre, ringRadius, 0, TAU);
    ctx.stroke();

    // The larger bead is counted first, then the round. Each bead takes an equal share of the
    // session and is fully inked partway through its share, so the last one is plainly lit
    // before the session ends.
    const counted = f * (count + 1);
    const inked = (step) => clamp((counted - step) / BEADS.fade, 0, 1);

    draw(track, tiles - 1, 0, bead * marker, 1);
    if (inked(0) > 0) draw(ink, tiles - 1, 0, bead * marker, inked(0));
    for (let i = 0; i < count; i++) {
        const slot = gap / 2 + 0.5 + i;
        const variant = i % (tiles - 1);
        draw(track, variant, slot, bead, 1);
        if (inked(i + 1) > 0) draw(ink, variant, slot, bead, inked(i + 1));
    }
}

// ── Labyrinth ──────────────────────────────────────────────────────
// The single winding path of the labyrinth laid in the floor of Chartres cathedral around
// 1200. The path goes in at the foot, wanders through all eleven rings in each quarter, and
// arrives at the centre as the session ends.

const LABYRINTH = { rings: 11, centre: 0.13, thickness: 0.4 };

// The route: each step is a ring (1 outermost) and the quarter it crosses (bottom left, top
// left, top right, bottom right), in the order they are walked.
const ROUTE =
    '5BL 6BL 11BL 11TL 10TL 10BL 9BL 8BL 7BL 7TL 8TL 9TL 9TR 10TR 11TR 11BR 10BR 9BR 8BR 8TR 7TR 6TR ' +
    '6TL 5TL 4TL 4BL 3BL 2BL 1BL 1TL 2TL 3TL 3TR 4TR 5TR 5BR 4BR 3BR 2BR 2TR 1TR 1BR 6BR 7BR';

// The four arms, numbered round from the entrance, and each quarter's two arms and the angles
// between them, measured from straight down and growing to the right.
const FOOT = 0, LEFT = 1, TOP = 2, RIGHT = 3;
const QUARTERS = {
    BL: { low: -90, high: 0, lowArm: LEFT, highArm: FOOT },
    TL: { low: -180, high: -90, lowArm: TOP, highArm: LEFT },
    TR: { low: 90, high: 180, lowArm: RIGHT, highArm: TOP },
    BR: { low: 0, high: 90, lowArm: FOOT, highArm: RIGHT },
};
const STEPS = ROUTE.split(' ').map((s) => ({ n: parseInt(s, 10), quarter: QUARTERS[s.slice(-2)] }));

let labyrinth = { box: 0, points: [], lengths: [] };

/** Lays the route out as one line from the edge of the box to its centre. */
function buildLabyrinth(box) {
    const half = box / 2;
    const c = half;
    const ring = half * (1 - LABYRINTH.centre) / (LABYRINTH.rings + 0.5);   // the distance between rings
    const radius = (n) => half - ring * (n - 0.25);
    const points = [[c - ring / 2, c + half]];
    let arm = FOOT;
    STEPS.forEach(({ n, quarter }, i) => {
        const r = radius(n);
        const leaving = quarter.lowArm === arm ? quarter.highArm : quarter.lowArm;
        // Where the path keeps to its ring across an arm, the arc runs right up to it. Where
        // it turns, the arc stops short: half a ring short of a side or top arm, and at the
        // foot, half a ring for the two straight lanes and a ring more for the hairpins
        // beside them.
        const gap = (at, other) => {
            if (other === n) return 0;
            return at === FOOT && other != null && Math.abs(other - n) === 1 ? ring * 1.5 : ring * 0.5;
        };
        const angle = (at, other) => {
            const shy = Math.asin(gap(at, other) / r) / RAD;
            return at === quarter.highArm ? quarter.high - shy : quarter.low + shy;
        };
        const from = angle(arm, STEPS[i - 1]?.n);
        const to = angle(leaving, STEPS[i + 1]?.n);
        const pieces = Math.max(2, Math.ceil(Math.abs(from - to) / 2));
        for (let k = 0; k <= pieces; k++) {
            const a = (90 - from + (from - to) * k / pieces) * RAD;
            points.push([c + r * Math.cos(a), c + r * Math.sin(a)]);
        }
        arm = leaving;
    });
    // Up the last lane and into the middle.
    const x0 = c + ring / 2, y0 = c + ring * 1.5;
    points.push([x0, y0]);
    for (let k = 1; k <= 12; k++) {
        const t = k / 12;
        points.push([
            (1 - t) * (1 - t) * x0 + 2 * t * (1 - t) * (c + ring / 2) + t * t * c,
            (1 - t) * (1 - t) * y0 + 2 * t * (1 - t) * c + t * t * c,
        ]);
    }
    const lengths = [0];
    for (let i = 1; i < points.length; i++) {
        lengths.push(lengths[i - 1] + Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]));
    }
    labyrinth = { box, points, lengths };
}

function drawLabyrinth(ctx, box, f, p) {
    if (labyrinth.box !== box) buildLabyrinth(box);
    const { points, lengths } = labyrinth;
    const ring = box / 2 * (1 - LABYRINTH.centre) / (LABYRINTH.rings + 0.5);
    ctx.lineWidth = ring * LABYRINTH.thickness;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    ctx.strokeStyle = css(p.btnHover);
    ctx.beginPath();
    points.forEach(([x, y], i) => (i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)));
    ctx.stroke();

    if (f > 0) {
        // Inked as far as the session has come.
        const walked = lengths[lengths.length - 1] * f;
        ctx.strokeStyle = css(p.fg);
        ctx.beginPath();
        ctx.moveTo(points[0][0], points[0][1]);
        for (let i = 1; i < points.length; i++) {
            if (lengths[i] <= walked) {
                ctx.lineTo(points[i][0], points[i][1]);
            } else {
                const t = (walked - lengths[i - 1]) / (lengths[i] - lengths[i - 1]);
                ctx.lineTo(
                    points[i - 1][0] + (points[i][0] - points[i - 1][0]) * t,
                    points[i - 1][1] + (points[i][1] - points[i - 1][1]) * t,
                );
                break;
            }
        }
        ctx.stroke();
    }
}

// ── Incense ────────────────────────────────────────────────────────
// One stick burning down. It stands in a cast iron bowl, burns from the top, keeps a little
// ash on its glowing end until that drops into the bowl, and smokes while the timer runs.

const INCENSE = {
    ashRows: 96, bodyRows: 256,     // rows of the strip holding the ash, and the repeating stick below
    width: 0.015,                   // the stick's width, as a part of the box
    dishWidth: 0.4, dishLeft: 0.3, dishBottom: 0.97,
    socketX: 0.501, socketY: 0.271, // where the stick goes into the holder, in the bowl picture
    leanDegrees: 2.8,
    dishTone: 0.24, dishShading: 0.26,
    length: 0.67,                   // the stick's length, as a part of the box
    ashPiece: 4, ashKept: 0.35,     // ash breaks off in pieces this long, in stick widths
    emberWarmth: 0.15, coal: [0xE8, 0x7A, 0x3E],
    smokeHeight: 0.5, smokePoints: 40, ribbons: 3,
    layerWidths: [1, 2.3, 4.2],     // each ribbon is drawn three times, wider and fainter
    layerAlphas: [0.20, 0.10, 0.05],
};

/** An upright bar whose top corners are rounded by `radius`, as the current path. */
function roundedTop(ctx, left, top, right, bottom, radius) {
    const r = Math.max(0, Math.min(radius, (right - left) / 2, bottom - top));
    ctx.beginPath();
    ctx.moveTo(left, bottom);
    ctx.lineTo(left, top + r);
    ctx.arcTo(left, top, left + r, top, r);
    ctx.lineTo(right - r, top);
    ctx.arcTo(right, top, right, top + r, r);
    ctx.lineTo(right, bottom);
    ctx.closePath();
}

function drawIncense(ctx, box, f, time, smoke, p) {
    const I = INCENSE;
    const strip = images.incense;
    const dish = images.incense_dish;
    if (!strip || !dish) return;
    const width = Math.max(Math.round(box * I.width), 3);
    const dishWidth = Math.round(box * I.dishWidth);
    const dishHeight = Math.round(dishWidth * dish.naturalHeight / dish.naturalWidth);
    const dishLeft = Math.round(box * I.dishLeft);
    const dishTop = Math.round(box * I.dishBottom) - dishHeight;
    const footX = dishLeft + dishWidth * I.socketX;
    const footY = dishTop + dishHeight * I.socketY;
    const lean = I.leanDegrees * RAD;
    const length = box * I.length;
    const burned = f * length;
    const ashColor = mix(p.bg, p.fg, 0.42);

    ctx.drawImage(shaded('incense_dish', mix(p.bg, p.fg, I.dishTone), I.dishShading), dishLeft, dishTop, dishWidth, dishHeight);

    // Ash on the burning end grows until a piece breaks off and drops into the bowl.
    const piece = width * I.ashPiece;
    const fallen = f <= 0 ? 0 : Math.max(Math.floor(burned / piece - I.ashKept), 0);
    const standing = f >= 1 ? 0 : burned - fallen * piece;

    // The stick is drawn upright and leaned over about the point where it enters the dish.
    ctx.save();
    ctx.translate(footX, footY);
    ctx.rotate(lean);
    ctx.translate(-footX, -footY);
    const left = Math.round(footX - width / 2);
    const tipY = footY - length + burned;

    // What is left of the stick: the strip's stick tile, repeated up from the bottom so the
    // grain stays put as the top burns away. A stick that has not been lit ends in a rounded
    // tip, as it was rolled; once it is burning it ends flat, at the ember.
    const tile = Math.round(width * I.bodyRows / strip.naturalWidth);
    const stick = shaded('incense', mix(p.bg, p.fg, 0.6), 0.35);
    ctx.save();
    roundedTop(ctx, left, tipY, left + width, footY, f <= 0 ? width / 2 : 0);
    ctx.clip();
    for (let y = Math.round(footY); y > tipY;) {
        y -= tile;
        ctx.drawImage(stick, 0, I.ashRows, strip.naturalWidth, I.bodyRows, left, y, width, tile);
    }
    ctx.restore();

    if (f > 0 && f < 1) {
        // Ash still standing on the burning end, rounded where the last piece broke away.
        if (standing >= 1) {
            ctx.save();
            roundedTop(ctx, left, tipY - standing, left + width, tipY, Math.min(width / 2, standing));
            ctx.clip();
            ctx.drawImage(
                shaded('incense', ashColor, 0.25), 0, 0, strip.naturalWidth, I.ashRows,
                left, Math.round(tipY - standing), width, Math.round(standing),
            );
            ctx.restore();
        }
        // The ember: a bright band where the stick meets the ash, with a glow that breathes.
        const breath = 0.8 + 0.2 * Math.sin(time * 2.1) * Math.sin(time * 1.3 + 1);
        const glow = (0.45 + 0.55 * smoke) * breath;
        const warm = mix(p.fg, I.coal, I.emberWarmth);
        // Its light in the air around it: a wide faint halo and a closer, stronger one.
        for (const [radius, strength] of [[width * 9, 0.10], [width * 3.6, 0.5]]) {
            radial(ctx, footX, tipY, radius, [[0, css(warm, strength * glow)], [1, css(warm, 0)]]);
        }
        ctx.fillStyle = css(warm);
        ctx.fillRect(left, tipY - width * 0.35, width, width * 0.7);
    }
    ctx.restore();

    if (smoke > 0 && f > 0 && f < 1) {
        const reach = length - burned + Math.min(standing, width);
        drawSmoke(ctx, footX + Math.sin(lean) * reach, footY - Math.cos(lean) * reach, time, smoke, p.fg, box, width);
    }
}

const smokeCentres = new Float32Array(INCENSE.smokePoints + 1);
const smokeHalves = new Float32Array(INCENSE.smokePoints + 1);

/**
 * Smoke rising from a point: ribbons that leave the ember as one thin column, then sway and
 * part. The sway is a few waves travelling upward, small near the ember and wide at the top,
 * so the smoke climbs steadily and never repeats exactly.
 */
function drawSmoke(ctx, ox, oy, t, strength, color, box, width) {
    const I = INCENSE;
    const n = I.smokePoints;
    const height = box * I.smokeHeight;
    const wave = (turns, speed, s, phase) => Math.sin(TAU * (turns * s - speed * t) + phase);
    const fade = ctx.createLinearGradient(0, oy, 0, oy - height);
    fade.addColorStop(0, css(color, 0));
    fade.addColorStop(0.04, css(color, 1));
    fade.addColorStop(0.4, css(color, 0.55));
    fade.addColorStop(1, css(color, 0));
    ctx.fillStyle = fade;
    for (let r = 0; r < I.ribbons; r++) {
        for (let i = 0; i <= n; i++) {
            const s = i / n;
            const together = 0.55 * wave(1.2, 0.17, s, 0) + 0.35 * wave(2.6, 0.29, s, 1.7) + 0.2 * wave(4.9, 0.43, s, 4.1);
            const apart = 0.5 * wave(1.9, 0.23, s, r * 2.1) + 0.3 * wave(3.7, 0.37, s, r * 4.4);
            const lean = 0.5 * Math.sin(t * 0.13 + 0.6) * s;
            smokeCentres[i] = ox + box * 0.11 * Math.pow(s, 1.4) * (together + 1.2 * s * apart + lean);
            smokeHalves[i] = width * 0.3 + box * 0.012 * s * (1 + 0.5 * wave(2.2, 0.2, s, r));
        }
        for (let l = 0; l < I.layerWidths.length; l++) {
            ctx.beginPath();
            for (let i = 0; i <= n; i++) {
                const x = smokeCentres[i] - smokeHalves[i] * I.layerWidths[l];
                const y = oy - height * i / n;
                if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
            }
            for (let i = n; i >= 0; i--) {
                ctx.lineTo(smokeCentres[i] + smokeHalves[i] * I.layerWidths[l], oy - height * i / n);
            }
            ctx.closePath();
            ctx.globalAlpha = I.layerAlphas[l] * strength;
            ctx.fill();
        }
    }
    ctx.globalAlpha = 1;
}

// ── Candle ─────────────────────────────────────────────────────────
// One candle burning down. It stands in a dish, is lit when the timer starts, glows and wavers
// while the timer runs, and has burned down into the dish when the session ends. The candle
// picture is its dripped top above a length of its side that repeats, so it can be any height.

const CANDLE = {
    capRows: 335, sideRows: 800,
    dishWidth: 0.5, dishBottom: 0.97,
    candleWidth: 0.2398, candleMiddle: 0.5537, dishMiddle: 0.4953, dishRim: 0.492,
    tall: 0.7, stub: 0.03,          // the candle's height above the rim when new and when spent
    wick: 0.014, flame: 0.14,
    tone: 0.3, shading: 0.3,
    glowTone: 0.8, glowReach: 0.42,
};

function drawCandle(ctx, box, f, time, lit, p, dpr) {
    const C = CANDLE;
    const candle = images.candle;
    const dish = images.candle_dish;
    const flame = images.candle_flame;
    if (!candle || !dish || !flame) return;
    const middle = box / 2;
    const dishWidth = Math.round(box * C.dishWidth);
    const dishHeight = Math.round(dishWidth * dish.naturalHeight / dish.naturalWidth);
    const dishLeft = Math.round(middle - dishWidth * C.dishMiddle);
    const dishTop = Math.round(box * C.dishBottom) - dishHeight;
    const rim = dishTop + Math.round(dishHeight * C.dishRim);       // where the candle goes out of sight
    const width = Math.round(dishWidth * C.candleWidth);
    const left = Math.round(middle - width * C.candleMiddle);
    const scale = width / candle.naturalWidth;
    const cap = Math.round(C.capRows * scale);
    const tile = Math.round(C.sideRows * scale);
    const top = rim - Math.round(box * (C.tall + (C.stub - C.tall) * f));
    const quietColor = mix(p.bg, p.fg, C.tone);
    const burning = lit > 0 && f < 1;
    const breath = 0.85 + 0.15 * Math.sin(time * 3.3) * Math.sin(time * 1.1 + 2);

    ctx.drawImage(shaded('candle_dish', quietColor, C.shading), dishLeft, dishTop, dishWidth, dishHeight);

    // The candle, down to the rim: its side repeated up from the foot so the grain stays put
    // as the top comes down, and its dripped top above. Near the end the top itself sinks
    // behind the rim.
    function wax(target, picture) {
        target.save();
        target.beginPath();
        target.rect(left - 1, -box, width + 2, rim + box);
        target.clip();
        target.save();
        target.beginPath();
        target.rect(left - 1, top + cap, width + 2, box * 2);
        target.clip();
        for (let y = rim; y > top + cap;) {
            y -= tile;
            target.drawImage(picture, 0, C.capRows, candle.naturalWidth, C.sideRows, left, y, width, tile);
        }
        target.restore();
        target.drawImage(picture, 0, 0, candle.naturalWidth, C.capRows, left, top, width, cap);
        target.restore();
    }
    wax(ctx, shaded('candle', quietColor, C.shading));

    if (burning && rim > top) {
        // The flame shines through the wax: the same candle again, bright, kept only near the
        // top and fading away below it.
        const reach = box * C.glowReach;
        const glow = layer('candle-glow', width, rim - top, dpr);
        glow.translate(-left, -top);
        wax(glow, shaded('candle', mix(p.bg, p.fg, C.glowTone), C.shading));
        const fade = glow.createLinearGradient(0, top, 0, top + reach);
        fade.addColorStop(0, `rgba(0,0,0,${clamp(lit * breath, 0, 1)})`);
        fade.addColorStop(1, 'rgba(0,0,0,0)');
        glow.globalCompositeOperation = 'destination-in';
        glow.fillStyle = fade;
        glow.fillRect(left, top, width, rim - top);
        ctx.drawImage(glow.canvas, left, top, glow.canvas.width / dpr, glow.canvas.height / dpr);
    }

    const wickFootY = top + cap * 0.03;
    const wickTipY = wickFootY - box * C.wick;
    if (wickFootY < rim) {
        ctx.strokeStyle = css(mix(p.bg, p.fg, 0.5));
        ctx.lineWidth = Math.max(width * 0.03, 2 / dpr);
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.moveTo(middle, wickFootY);
        ctx.lineTo(middle, wickTipY);
        ctx.stroke();
    }

    if (burning) {
        // The flame stands on the wick and never holds still: it stretches, leans and
        // brightens a little, on a few slow waves that do not keep step.
        const tall = 1 + 0.05 * Math.sin(time * 5.1 + 2) * Math.sin(time * 1.7);
        const wide = 1 + 0.03 * Math.sin(time * 7.3) * Math.sin(time * 2.9 + 1);
        const lean = 2.5 * Math.sin(time * 1.9) * Math.sin(time * 0.7 + 0.5);
        const height = box * C.flame;
        const flameWidth = height * flame.naturalWidth / flame.naturalHeight;
        const footX = middle;
        const footY = wickTipY + height * 0.1;
        const heartY = footY - height * 0.45;
        // Its light in the air around it: a wide faint halo and a closer, stronger one.
        for (const [radius, strength] of [[height * 2.6, 0.10], [height * 1.1, 0.22]]) {
            radial(ctx, footX, heartY, radius, [[0, css(p.fg, strength * lit * breath)], [1, css(p.fg, 0)]]);
        }

        // The flame's outline is the photograph's. Inside it, it is one even light down to
        // its foot, which thins until it can be seen through, most of all around the wick.
        const fl = layer('candle-flame', flameWidth, height, dpr);
        fl.globalAlpha = Math.min(lit, 1);
        fl.drawImage(flame, 0, 0, flameWidth, height);
        fl.globalAlpha = 1;
        const light = fl.createLinearGradient(0, 0, 0, height);
        light.addColorStop(0, css(p.fg));
        light.addColorStop(0.6, css(p.fg));
        light.addColorStop(1, css(p.fg, 0.12));
        fl.globalCompositeOperation = 'source-in';
        fl.fillStyle = light;
        fl.fillRect(0, 0, flameWidth, height);
        // The dark heart of the flame, around the wick, where nothing is burning yet.
        fl.globalCompositeOperation = 'destination-out';
        radial(fl, flameWidth / 2, height - height * 0.16, height * 0.17, [[0, 'rgba(0,0,0,0.6)'], [1, 'rgba(0,0,0,0)']]);

        ctx.save();
        ctx.translate(footX, footY);
        ctx.rotate(lean * RAD);
        ctx.scale(wide * lit, tall * lit);
        ctx.drawImage(fl.canvas, -flameWidth / 2, -height, flameWidth, height);
        ctx.restore();
    }
}

// ── Moon ───────────────────────────────────────────────────────────
// A session measured as the moon measures a month, from the first thin crescent to the full
// moon as the session ends. The rest of the disc is there all along, barely lit. The
// photograph is lit evenly all over, so the light and the shadow of the phase are put on here.

const MOON = {
    width: 0.8,
    darkTone: 0.1, darkShading: 0.1, litShading: 0.6,
    first: 0.16, span: 0.78,        // the phase at the start, and how much of the month a session covers
    nimbus: 0.3, nimbusReach: 0.45, nimbusAside: 0.3,
    soft: 0.05,                     // the softness of the edge of the light, as a part of the radius
};

function drawMoon(ctx, box, f, p, dpr) {
    const M = MOON;
    if (!images.moon) return;
    const width = Math.round(box * M.width);
    const left = Math.round((box - width) / 2);
    const cx = left + width / 2;
    const cy = cx;
    const radius = width / 2;
    const phase = Math.PI * (M.first + M.span * f);

    // The nimbus: the moon's light in the air around it. It comes from the lit side, so it is
    // faint and lies off to that side around a crescent, and grows brighter, wider and more
    // evenly round as the moon fills. The disc is drawn over its middle.
    const full = (1 - Math.cos(phase)) / 2;
    const aside = radius * M.nimbusAside * (1 - full);
    const reachOut = radius * (1 + M.nimbusReach * (0.6 + 0.4 * full));
    const strength = M.nimbus * (0.35 + 0.65 * full);
    const limb = (radius - aside) / reachOut;
    radial(ctx, cx + aside, cy, reachOut, [
        [0, css(p.moonGlow, strength)], [limb, css(p.moonGlow, strength)],
        [limb + (1 - limb) * 0.4, css(p.moonGlow, strength * 0.3)], [1, css(p.moonGlow, 0)],
    ]);

    ctx.drawImage(shaded('moon', mix(p.bg, p.fg, M.darkTone), M.darkShading), left, left, width, width);

    // The lit part lies between the right-hand limb and the terminator, which is half an
    // ellipse: bulging right while the moon is a crescent, flat at half moon, left after.
    // Both are taken a little outside the disc, so the soft edge stays off the limb.
    const soft = radius * M.soft;
    const reach = radius + soft * 2;
    const bulge = reach * Math.cos(phase);
    const half = Math.max(Math.abs(bulge), 0.5);

    // The shape of the light, with a soft edge. It is drawn far off to one side and only its
    // shadow, which is the shape blurred, falls on the layer: a blur every browser can do.
    const lit = layer('moon-light', width, width, dpr);
    const away = width * 4;
    lit.shadowColor = '#fff';
    lit.shadowBlur = 2 * (soft * 0.57735 + 0.5) * dpr;
    lit.shadowOffsetX = away * dpr;
    lit.fillStyle = '#fff';
    lit.beginPath();
    const mx = radius - away, my = radius;
    lit.moveTo(mx, my - reach);
    lit.arc(mx, my, reach, -Math.PI / 2, Math.PI / 2);
    if (bulge > 0) lit.ellipse(mx, my, half, reach, 0, Math.PI / 2, -Math.PI / 2, true);
    else lit.ellipse(mx, my, half, reach, 0, Math.PI / 2, 3 * Math.PI / 2, false);
    lit.closePath();
    lit.fill();
    lit.shadowColor = 'transparent';
    // The bright moon, kept only where the light falls.
    lit.globalCompositeOperation = 'source-in';
    lit.drawImage(shaded('moon', p.moon, M.litShading), 0, 0, width, width);
    ctx.drawImage(lit.canvas, left, left, lit.canvas.width / dpr, lit.canvas.height / dpr);
}

// ── Kaleidoscope ───────────────────────────────────────────────────
// A session as a pattern drawing in to a point. A few pale shards drift and turn, slowly, and
// mirrors set round the middle make of them a pattern with six sides that is never twice the
// same. Before the session it sits where the other pictures do. When the timer runs and the
// controls fade it moves to the middle of the screen and opens out to fill it, and from then
// on it draws steadily in, until at the end it is a point and is gone.

const KALEIDOSCOPE = { reach: 0.48, folds: 6, turn: 300 };

/** Kotlin's Random(seed), so the shards are the very ones the Android app draws. */
function kotlinRandom(seed) {
    let x = seed | 0, y = seed >> 31, z = 0, w = 0, v = ~seed;
    let addend = ((seed << 10) ^ (y >>> 4)) | 0;
    const nextInt = () => {
        let t = x;
        t ^= t >>> 2;
        x = y; y = z; z = w;
        const v0 = v;
        w = v0;
        t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4);
        v = t;
        addend = (addend + 362437) | 0;
        return (t + addend) | 0;
    };
    for (let i = 0; i < 64; i++) nextInt();
    const bits = (n) => nextInt() >>> (32 - n);
    return {
        nextFloat: () => bits(24) / 16777216,
        nextBoolean: () => bits(1) !== 0,
        nextInt(until) {
            for (;;) {
                const b = nextInt() >>> 1;
                const r = b % until;
                if (((b - r + (until - 1)) | 0) >= 0) return r;
            }
        },
    };
}

// One shard: how many sides it has and how big it is beside the window, the slow waves (rate
// and starting point) that carry it in and out, round, and about itself, and how solid it is.
const SHARDS = (() => {
    const r = kotlinRandom(11);
    const rate = (slow, quick) => {
        const period = slow + (quick - slow) * r.nextFloat();
        return TAU / period * (r.nextBoolean() ? 1 : -1);
    };
    const from = () => TAU * r.nextFloat();
    return Array.from({ length: 8 }, () => ({
        sides: 3 + r.nextInt(3), size: 0.18 + 0.22 * r.nextFloat(),
        outRate: rate(120, 60), outFrom: from(),
        roundRate: rate(150, 80), roundFrom: from(),
        spinRate: rate(200, 90), spinFrom: from(),
        solid: 0.09 + 0.08 * r.nextFloat(),
    }));
})();

/**
 * Draws the pattern on a canvas `width` by `height`, with `fraction` of the session gone. At
 * rest it fills a circle about (ringX, ringY) in a box `box` wide; `spread` takes it from
 * there (0) to the middle of the canvas, and filling it (1). `time` is in seconds and only
 * moves the shards.
 */
export function drawKaleidoscope(ctx, width, height, ringX, ringY, box, spread, fraction, time, p) {
    const K = KALEIDOSCOPE;
    const f = clamp(fraction, 0, 1);
    const rest = box * K.reach;
    const wide = clamp(spread, 0, 1);
    const cx = ringX + (width / 2 - ringX) * wide;
    const cy = ringY + (height / 2 - ringY) * wide;
    // Far enough to reach the furthest corner.
    const far = Math.hypot(Math.max(cx, width - cx), Math.max(cy, height - cy));
    const reach = (rest + (far - rest) * wide) * (1 - f);
    if (reach < 0.5) return;
    const colors = [p.fg, p.wood, p.moonGlow];

    ctx.save();
    ctx.beginPath();
    ctx.arc(cx, cy, reach, 0, TAU);
    ctx.clip();
    ctx.translate(cx, cy);
    ctx.rotate(time * TAU / K.turn);
    SHARDS.forEach((shard, i) => {
        // Where the shard is: out from the middle (evenly over the window's area), round from
        // the mirror, and turned about itself.
        const out = reach * Math.sqrt(0.5 + 0.5 * Math.sin(time * shard.outRate + shard.outFrom));
        const round = time * shard.roundRate + shard.roundFrom;
        const spin = time * shard.spinRate + shard.spinFrom;
        const x = out * Math.cos(round);
        const y = out * Math.sin(round);
        // Near the middle the mirrors crowd its copies together, so it is smaller there.
        const reachOf = reach * shard.size * (0.3 + 0.7 * out / reach);
        const shape = new Path2D();
        for (let corner = 0; corner < shard.sides; corner++) {
            const a = spin + TAU * corner / shard.sides;
            const px = x + reachOf * Math.cos(a);
            const py = y + reachOf * Math.sin(a);
            if (corner === 0) shape.moveTo(px, py); else shape.lineTo(px, py);
        }
        shape.closePath();
        ctx.fillStyle = css(colors[i % colors.length], shard.solid);
        for (let fold = 0; fold < K.folds; fold++) {
            ctx.save();
            ctx.rotate(TAU * fold / K.folds);
            ctx.fill(shape);
            ctx.scale(1, -1);
            ctx.fill(shape);
            ctx.restore();
        }
    });
    ctx.restore();

    ctx.strokeStyle = css(p.fg, 0.3);
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(cx, cy, reach, 0, TAU);
    ctx.stroke();
}

// ── The picture for a style ────────────────────────────────────────

/** The styles with something in them that moves while the timer runs. */
export const MOVING_STYLES = new Set(['incense', 'candle']);

/**
 * Draws `style` in a box `box` wide at the origin, with `fraction` of the session gone.
 * `time` (seconds) and `smoke` (0 to 1: how fully the flame or the smoke is going) only matter
 * to the moving styles. The kaleidoscope is not drawn here: it lies behind the whole screen.
 */
export function drawStyle(ctx, style, box, fraction, time, smoke, p, dpr) {
    const f = clamp(fraction, 0, 1);
    switch (style) {
        case 'enso': drawEnso(ctx, box, f, p); break;
        case 'beads': drawBeads(ctx, box, f, p); break;
        case 'incense': drawIncense(ctx, box, f, time, smoke, p); break;
        case 'labyrinth': drawLabyrinth(ctx, box, f, p); break;
        case 'candle': drawCandle(ctx, box, f, time, smoke, p, dpr); break;
        case 'moon': drawMoon(ctx, box, f, p, dpr); break;
        case 'kaleidoscope': break;
        default: drawPlain(ctx, box, f, p);
    }
}
