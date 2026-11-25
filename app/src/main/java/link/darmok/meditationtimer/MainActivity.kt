package link.darmok.meditationtimer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.border
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hardware volume keys adjust the stream the chimes play on.
        volumeControlStream = AudioManager.STREAM_MUSIC

        // Only needed to show the "timer running" notification; timers work without it.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { TimerApp() }
    }
}

// ── Theme ──────────────────────────────────────────────────────────

class Palette(
    val bg: Color,
    val bgDim: Color,
    val fg: Color,
    val btnBg: Color,
    val btnHover: Color,
    val modalBg: Color,
    val modalBorder: Color,
    val inputBg: Color,
    /** Outlines of cards, buttons and controls. */
    val border: Color,
    /** The one touch of colour: a muted sandalwood for counted beads. */
    val wood: Color,
    /** The lit side of the moon, and the faintly golden light in the air around it. */
    val moon: Color,
    val moonGlow: Color,
)

/** Everforest Dark */
private val Dark = Palette(
    bg = Color(0xFF2D353B), bgDim = Color(0xFF232A2E), fg = Color(0xFFD3C6AA),
    btnBg = Color(0xFF3D484D), btnHover = Color(0xFF475258),
    modalBg = Color(0xFF343F44), modalBorder = Color(0xFF475258), inputBg = Color(0xFF2D353B),
    border = Color(0xFF505C60),
    wood = Color(0xFF998670), moon = Color(0xFFC9BD9F), moonGlow = Color(0xFFDCC384),
)

/** The dark palette turned inside out: its ink is the paper, and its paper is the ink. */
private val Light = Palette(
    bg = Color(0xFFD3C6AA), bgDim = Color(0xFFDDCFB1), fg = Color(0xFF2D353B),
    btnBg = Color(0xFFC3B89F), btnHover = Color(0xFFB9AF99),
    modalBg = Color(0xFFCCC0A5), modalBorder = Color(0xFFB9AF99), inputBg = Color(0xFFD3C6AA),
    border = Color(0xFFADA38E),
    wood = Color(0xFF745F4C), moon = Color(0xFFEDE3C9), moonGlow = Color(0xFFF6E8BB),
)

private val LocalPalette = staticCompositionLocalOf { Dark }

// ── Screen ─────────────────────────────────────────────────────────

private val CardShape = RoundedCornerShape(20.dp)
private val ControlShape = RoundedCornerShape(20.dp)

private enum class Sheet { DURATION, SOUND, SETTINGS }

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TimerApp() {
    val timer by TimerEngine.timer.collectAsStateWithLifecycle()
    val theme by TimerEngine.theme.collectAsStateWithLifecycle()
    val dark = when (theme) {
        THEME_DARK -> true
        THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val keepScreenOn by TimerEngine.keepScreenOn.collectAsStateWithLifecycle()
    val ringOnly by TimerEngine.ringOnly.collectAsStateWithLifecycle()
    val ringStyle by TimerEngine.ringStyle.collectAsStateWithLifecycle()
    val p = if (dark) Dark else Light

    val activity = LocalContext.current as ComponentActivity
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    val running = timer.status == Status.RUNNING
    val idle = timer.status == Status.IDLE

    // Drives the countdown display; only ticks while running.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        while (running) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
    }

    // Only while counting down, so a paused timer left on a table doesn't hold the screen on.
    val view = LocalView.current
    DisposableEffect(keepScreenOn && running) {
        view.keepScreenOn = keepScreenOn && running
        onDispose { view.keepScreenOn = false }
    }

    // When a repeating timer goes straight into its next round, the finished picture fades away
    // and the fresh one fades in, rather than one snapping to the other. A round has repeated
    // when the deadline moves while the timer stays running.
    val lastSeen = remember { arrayOf(timer) }
    val repeatedAt = remember(timer.status, timer.endAt) {
        val before = lastSeen[0]
        lastSeen[0] = timer
        if (before.status == Status.RUNNING && running && before.endAt != timer.endAt) timer.endAt else null
    }
    val ringAlpha = remember { Animatable(1f) }
    var showFinished by remember(repeatedAt) { mutableStateOf(repeatedAt != null) }
    LaunchedEffect(repeatedAt) {
        if (repeatedAt == null) {
            ringAlpha.snapTo(1f)
        } else {
            ringAlpha.animateTo(0f, tween(1500))
            showFinished = false
            ringAlpha.animateTo(1f, tween(1500))
        }
    }

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var soundTab by remember { mutableStateOf(SoundTab.CHIME) }
    val minutes = timer.durationSec / 60

    // While running, the controls fade away a few seconds after the last touch, leaving the
    // ring and countdown (or just the ring). Any touch brings them back.
    var controlsVisible by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(running, touches, sheet) {
        controlsVisible = true
        if (running && sheet == null) {
            delay(4000)
            controlsVisible = false
        }
    }
    // With the controls faded, the status bar and navigation buttons go too. A touch brings
    // everything back; a swipe from the edge shows the bars briefly, as in any full-screen app.
    DisposableEffect(controlsVisible) {
        val bars = WindowCompat.getInsetsController(activity.window, view)
        if (controlsVisible) {
            bars.show(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val controlsAlpha by animateFloatAsState(
        if (controlsVisible) 1f else 0f, tween(if (controlsVisible) 250 else 1500), label = "controls",
    )
    val timeAlpha by animateFloatAsState(
        if (ringOnly && !controlsVisible) 0f else 1f, tween(if (controlsVisible) 250 else 1500), label = "time",
    )

    CompositionLocalProvider(LocalPalette provides p) {
        val scrollState = rememberScrollState()
        Box(
            Modifier
                .fillMaxSize()
                .background(p.bg)
                // Every touch is watched on its way down to the children, to bring back faded
                // controls. Normally nothing is consumed, so buttons and the ring get their taps.
                .pointerInput(Unit) {
                    val threshold = 56.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touches++
                        if (!controlsVisible) {
                            // Controls are faded out: this touch only brings them back. Swallow it
                            // so it can't press a button the user couldn't see.
                            controlsVisible = true
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                            return@awaitEachGesture
                        }
                        var sideways = false
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial)
                                .changes.firstOrNull { it.id == down.id } ?: break
                            // Once the finger has plainly dragged sideways, lifting it doesn't
                            // count as a tap on whatever is under it.
                            if (sideways) change.consume()
                            if (!change.pressed) break
                            val moved = change.position - down.position
                            if (abs(moved.x) > threshold && abs(moved.x) > abs(moved.y)) sideways = true
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            // Toolbar at the top, countdown in the middle, controls at the bottom in thumb reach.
            Column(
                Modifier
                    .fillMaxSize()
                    // Sized for the bars whether or not they are showing, so nothing shifts when
                    // they hide.
                    .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))
                    .widthIn(max = 560.dp)
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val remainingMs = timer.remainingAt(now)
                val text = formatTime(remainingMs)
                // Tap the time or the ring: pauses a running timer; otherwise opens the duration
                // drawer (when paused, choosing a new duration starts the session over).
                val timeLabel = if (running) "Pause" else "Set duration"
                val onTime = { if (running) TimerEngine.pause() else sheet = Sheet.DURATION }
                // The pictures that fill their square have their time above them; only the
                // rings have room for it inside.
                val timeAbove = ringStyle != RING_PLAIN && ringStyle != RING_ENSO && ringStyle != RING_BEADS

                // The top row: settings behind the three lines on the right and, for those
                // styles, the time small on the left.
                Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    if (timeAbove) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                                .clip(CardShape)
                                .clickable(role = Role.Button, onClickLabel = timeLabel, onClick = onTime)
                                .padding(horizontal = 15.dp, vertical = 8.dp),
                        ) {
                            val fontSize = if (text.length > 5) 32.sp else 40.sp
                            // Smoke can drift behind the digits: a rim of background colour around
                            // each one keeps it from touching their edges.
                            CountdownText(
                                text, fontSize, p.bg, Modifier.alpha(timeAlpha),
                                drawStyle = Stroke(with(LocalDensity.current) { 5.dp.toPx() }, join = StrokeJoin.Round),
                            )
                            CountdownText(text, fontSize, p.fg, Modifier.alpha(timeAlpha))
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .alpha(controlsAlpha)
                            .size(64.dp) // generous touch target around a small icon
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Settings") { sheet = Sheet.SETTINGS },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_settings), contentDescription = null,
                            modifier = Modifier.size(34.dp).alpha(0.8f), tint = p.fg,
                        )
                    }
                }

                CountdownRing(
                    fraction = if (showFinished) 1f else 1f - remainingMs / (timer.durationSec * 1000f),
                    alpha = { ringAlpha.value },
                    style = ringStyle,
                    running = running,
                    modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(0.8f).widthIn(max = 320.dp),
                    onClickLabel = timeLabel,
                    onClick = onTime,
                ) {
                    if (!timeAbove) {
                        // Three-digit minutes need a smaller size to stay inside the ring, and the
                        // painted rings are much thicker than the plain one.
                        val fontSize = when {
                            ringStyle != RING_PLAIN -> if (text.length > 5) 50.sp else 62.sp
                            text.length > 5 -> 58.sp
                            else -> 72.sp
                        }
                        CountdownText(text, fontSize, p.fg, Modifier.alpha(timeAlpha))
                    }
                }

                Column(Modifier.alpha(controlsAlpha), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Each setting on its own card.
                    Box(Modifier.fillMaxWidth().border(1.dp, p.border, CardShape).padding(8.dp)) {
                        ValueRow("Sound", chimeById(timer.chime).label) {
                            soundTab = SoundTab.CHIME
                            sheet = Sheet.SOUND
                        }
                    }
                    Box(Modifier.fillMaxWidth().border(1.dp, p.border, CardShape).padding(8.dp)) {
                        SwitchRow("Repeat", timer.repeat, onChange = TimerEngine::setRepeat)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Before a session starts there is nothing to reset, so the button sets
                        // the duration instead, where it is easier to find than a tap on the
                        // time. Only a paused session can be reset: a running one has to be
                        // paused first.
                        val idle = timer.status == Status.IDLE
                        AppButton(
                            if (idle) "Set" else "Reset",
                            onClick = { if (idle) sheet = Sheet.DURATION else TimerEngine.reset() },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            enabled = timer.status != Status.RUNNING,
                            fontSize = 18.sp,
                        )
                        AppButton(
                            text = when (timer.status) {
                                Status.IDLE -> "Start"
                                Status.RUNNING -> "Pause"
                                Status.PAUSED -> "Resume"
                            },
                            onClick = { if (running) TimerEngine.pause() else TimerEngine.start() },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }

        // Duration, sound (chime and volume) and the settings are full pages, drawn over the
        // timer. They close only with Done or back, so scrolling them can never dismiss them.
        FullPage(
            visible = sheet == Sheet.DURATION, onDone = { sheet = null },
            header = {
                // Fixed when the page opens: the first change resets the timer to idle.
                val restartsSession = remember { timer.status == Status.PAUSED }
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Duration", color = p.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (restartsSession) {
                        Text(
                            "Changing the duration restarts the session",
                            Modifier.alpha(0.7f), color = p.fg, fontSize = 14.sp,
                        )
                    }
                }
            },
        ) {
            DurationContent(initialMinutes = remember { minutes }, onChange = { TimerEngine.setDuration(it * 60) })
        }
        FullPage(
            visible = sheet == Sheet.SOUND, onDone = { sheet = null },
            extraButton = {
                AppButton(
                    "Listen", onClick = TimerEngine::testChime,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    fontSize = 18.sp,
                )
            },
            header = {
                Segments(SoundTab.entries.map { it.label }, soundTab.ordinal) { soundTab = SoundTab.entries[it] }
            },
        ) {
            when (soundTab) {
                SoundTab.CHIME -> ChimeList(selected = timer.chime)
                SoundTab.VOLUME -> VolumeContent()
            }
        }
        FullPage(visible = sheet == Sheet.SETTINGS, onDone = { sheet = null }) {
            OptionsContent()
        }
    }
}

@Composable
private fun CountdownText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    drawStyle: DrawStyle? = null,
) {
    Text(
        text, modifier,
        color = color, fontSize = fontSize, fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false,
        style = LocalTextStyle.current.copy(drawStyle = drawStyle),
    )
}

/**
 * Shows [fraction] of the session elapsed, in the chosen [style]: a ring filling clockwise from
 * the top, or a stick of incense burning down, which smokes while the timer is [running].
 */
@Composable
private fun CountdownRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    /** How solidly the picture is drawn, read only when drawing. */
    alpha: () -> Float = { 1f },
    style: String = RING_PLAIN,
    running: Boolean = false,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val fadePaint = remember { Paint() }
    val beadTiles = ImageBitmap.imageResource(R.drawable.beads)
    val incenseStrip = ImageBitmap.imageResource(R.drawable.incense)
    val incenseDish = ImageBitmap.imageResource(R.drawable.incense_dish)
    val candle = ImageBitmap.imageResource(R.drawable.candle)
    val candleDish = ImageBitmap.imageResource(R.drawable.candle_dish)
    val candleFlame = ImageBitmap.imageResource(R.drawable.candle_flame)
    val moon = ImageBitmap.imageResource(R.drawable.moon)
    // Smoke thins away when the timer stops and gathers again when it starts. Its clock only
    // runs while there is smoke to move, and only redraws the ring.
    val smoke by animateFloatAsState(
        if ((style == RING_INCENSE || style == RING_CANDLE) && running && fraction < 1f) 1f else 0f,
        tween(2000), label = "smoke",
    )
    val smokeTime = remember { mutableFloatStateOf(0f) }
    if (smoke > 0f) {
        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos {
                    smokeTime.floatValue += (it - last) / 1e9f
                    last = it
                }
            }
        }
    }
    Box(
        modifier
            .aspectRatio(1f)
            // Drawn before the clip: the painted ring is larger than the tap target.
            .drawBehind {
                val f = fraction.coerceIn(0f, 1f)
                // While fading, the whole picture is drawn into a layer and that is faded, so
                // its overlapping parts don't show through one another. The layer is wider
                // than the box, as some of the pictures are.
                val solid = alpha()
                val canvas = drawContext.canvas
                if (solid < 1f) {
                    fadePaint.alpha = solid
                    canvas.saveLayer(Rect(-size.width, -size.height, size.width * 2, size.height * 2), fadePaint)
                }
                when (style) {
                    RING_ENSO -> with(EnsoStroke) { drawEnso(f, ink = p.fg, track = p.btnBg) }
                    RING_INCENSE -> with(Incense) {
                        drawIncense(incenseStrip, incenseDish, f, smokeTime.floatValue, smoke, ink = p.fg, paper = p.bg)
                    }
                    RING_BEADS -> with(Beads) { drawBeads(beadTiles, f, ink = p.wood, track = p.btnBg) }
                    RING_CANDLE -> with(Candle) {
                        drawCandle(candle, candleDish, candleFlame, f, smokeTime.floatValue, smoke, ink = p.fg, paper = p.bg)
                    }
                    RING_LABYRINTH -> with(Labyrinth) { drawLabyrinth(f, ink = p.fg, track = p.btnHover) }
                    RING_MOON -> with(Moon) { drawMoon(moon, f, lit = p.moon, glow = p.moonGlow, ink = p.fg, paper = p.bg) }
                    else -> {
                        val stroke = 14.dp.toPx()
                        val inset = Offset(stroke / 2, stroke / 2)
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(p.btnBg, 0f, 360f, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
                        if (f > 0f) {
                            drawArc(
                                p.fg, startAngle = -90f, sweepAngle = 360f * f,
                                useCenter = false, topLeft = inset, size = arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
                if (solid < 1f) canvas.restore()
            }
            // The incense scene fills the square; its time is shown above it instead.
            .clip(if (style == RING_INCENSE || style == RING_CANDLE) RoundedCornerShape(20.dp) else CircleShape)
            .clickable(enabled = onClick != null, role = Role.Button, onClickLabel = onClickLabel) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ── Settings ───────────────────────────────────────────────────────

/** How the phone behaves while the timer runs, and how the timer looks. */
@Composable
private fun OptionsContent() {
    val context = LocalContext.current
    val theme by TimerEngine.theme.collectAsStateWithLifecycle()
    val doNotDisturb by TimerEngine.doNotDisturb.collectAsStateWithLifecycle()
    val keepScreenOn by TimerEngine.keepScreenOn.collectAsStateWithLifecycle()
    val ringOnly by TimerEngine.ringOnly.collectAsStateWithLifecycle()
    val ringStyle by TimerEngine.ringStyle.collectAsStateWithLifecycle()

    // Re-checked on every return to the app, since access is granted in system settings.
    var dndAccess by remember { mutableStateOf(QuietMode.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        dndAccess = QuietMode.hasAccess(context)
        onPauseOrDispose {}
    }

    // Shown as on only while the system permission is actually granted.
    SwitchRow(
        "Do Not Disturb", doNotDisturb && dndAccess,
        caption = "Silence calls and notifications while the timer runs",
        onChange = { on ->
            TimerEngine.setDoNotDisturb(on)
            if (on && !dndAccess) {
                // One-time grant in system settings; the switch turns on when the user returns.
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        },
    )
    SwitchRow(
        "Keep screen on", keepScreenOn,
        caption = "Stay lit while the timer runs",
        onChange = TimerEngine::setKeepScreenOn,
    )
    SwitchRow(
        "Hide countdown", ringOnly,
        caption = "Fade the time out along with the controls",
        onChange = TimerEngine::setRingOnly,
    )
    // Style: how the session's progress is drawn.
    Column(
        Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Style", color = LocalPalette.current.fg, fontSize = 16.sp)
        Segments(
            labels = listOf("Plain", "Ensō", "Beads", "Incense", "Labyrinth", "Candle", "Moon"),
            perRow = 3,
            selected = RING_STYLES.indexOf(ringStyle),
            onSelect = { TimerEngine.setRingStyle(RING_STYLES[it]) },
        )
    }
    // Theme: follow the phone, or stay light or dark.
    Column(
        Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Theme", color = LocalPalette.current.fg, fontSize = 16.sp)
        Segments(
            labels = listOf("System", "Light", "Dark"),
            selected = THEMES.indexOf(theme),
            onSelect = { TimerEngine.setTheme(THEMES[it]) },
        )
    }
}

/** The phone's media volume, and the chime's own level within it. */
@Composable
private fun VolumeContent() {
    val context = LocalContext.current
    val chimeVolume by TimerEngine.chimeVolume.collectAsStateWithLifecycle()

    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maxStep = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    fun stepOf(percent: Int) = (percent * maxStep / 100f).roundToInt()
    fun systemPercent() = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / maxStep).roundToInt()

    var systemVolume by remember { mutableIntStateOf(systemPercent()) }

    // Follow changes made elsewhere (volume keys, quick settings). Android volume has coarse
    // steps, so only resync when the actual step differs from what the slider already implies.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != stepOf(systemVolume)) {
                    systemVolume = systemPercent()
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    Column(
        Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VolumeRow("System", systemVolume, onChange = {
            systemVolume = it
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, stepOf(it), 0)
        })
        VolumeRow(
            "Chime", (chimeVolume * 100).roundToInt(),
            onChange = { TimerEngine.setChimeVolume(it / 100f) },
            onChangeFinished = TimerEngine::save,
        )
    }
}

private enum class SoundTab(val label: String) { CHIME("Chime"), VOLUME("Volume") }

/**
 * A segmented choice: tabs on the Sound page, the ring and theme choices in Settings. With
 * more choices than fit side by side, they wrap onto further rows of [perRow].
 */
@Composable
private fun Segments(labels: List<String>, selected: Int, perRow: Int = labels.size, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().border(1.dp, p.border, ControlShape).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.indices.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { i ->
                    val isSelected = i == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .then(if (isSelected) Modifier.border(1.dp, p.fg, RoundedCornerShape(16.dp)) else Modifier)
                            .selectable(isSelected, role = Role.Tab) { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            labels[i], Modifier.alpha(if (isSelected) 1f else 0.6f),
                            color = p.fg, fontSize = 16.sp,
                        )
                    }
                }
                // A short last row keeps its cells the same width as the rows above.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A page covering the whole screen: content that scrolls if it needs to, and buttons
 * fixed at the bottom. It fades in over the timer and closes only with Done or back, so
 * scrolling it never dismisses it.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FullPage(
    visible: Boolean,
    onDone: () -> Unit,
    extraButton: (@Composable RowScope.() -> Unit)? = null,
    /** Stays put at the top while the content scrolls. */
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    AnimatedVisibility(
        visible,
        // A quiet arrival: fades in while drifting up a short way, and leaves the same way.
        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 16 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 16 },
    ) {
        BackHandler(enabled = visible, onBack = onDone)
        Column(
            Modifier
                .fillMaxSize()
                .background(p.modalBg)
                // Touches stop here and never reach the timer screen underneath.
                .pointerInput(Unit) {}
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))
                .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            header?.invoke()
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                extraButton?.invoke(this)
                AppButton(
                    "Done", onClick = onDone,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    fontSize = 18.sp,
                )
            }
        }
    }
}


@Composable
private fun VolumeRow(label: String, percent: Int, onChange: (Int) -> Unit, onChangeFinished: () -> Unit = {}) {
    val p = LocalPalette.current
    Column {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp)) {
            Text(label, Modifier.weight(1f), color = p.fg, fontSize = 16.sp)
            Text("$percent%", Modifier.alpha(0.7f), color = p.fg, fontSize = 16.sp)
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onChangeFinished,
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = p.fg, activeTrackColor = p.fg, inactiveTrackColor = p.btnBg,
            ),
        )
    }
}

// ── Shared widgets ─────────────────────────────────────────────────

/** A full-width "label … switch" row; the whole row is the touch target. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, caption: String? = null, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(ControlShape)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.fg, fontSize = 16.sp)
            if (caption != null) Text(caption, Modifier.alpha(0.65f), color = p.fg, fontSize = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.bgDim, checkedTrackColor = p.fg,
                uncheckedThumbColor = p.fg, uncheckedTrackColor = p.btnBg,
                uncheckedBorderColor = p.border,
            ),
        )
    }
}

@Composable
private fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A disabled button stays in place, muted, and ignores taps. */
    enabled: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
) {
    val p = LocalPalette.current
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.35f)
            .heightIn(min = 48.dp)
            .clip(ControlShape)
            .border(1.dp, p.border, ControlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = p.fg,
            fontSize = fontSize, fontWeight = FontWeight.Normal, maxLines = 1,
        )
    }
}

/** A full-width "label … current value" row that opens somewhere to change the value. */
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(ControlShape)
            .clickable(role = Role.Button, onClickLabel = "Change", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, Modifier.weight(1f), color = p.fg, fontSize = 16.sp)
        Text(value, Modifier.alpha(0.7f), color = p.fg, fontSize = 16.sp, maxLines = 1)
    }
}

/** A large single-choice row; the selected one is outlined in the ink colour. */
@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(ControlShape)
            .then(if (selected) Modifier.border(1.dp, p.fg, ControlShape) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, color = p.fg, fontSize = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── Chime ──────────────────────────────────────────────────────────

/** Tapping a chime chooses it, silently; a separate Listen button sounds the chosen one. */
@Composable
private fun ChimeList(selected: String) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CHIMES.forEachIndexed { i, chime ->
            // Thin rule between the sounds and the silent vibrate options.
            if (chime.res == null && CHIMES.getOrNull(i - 1)?.res != null) {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(p.border),
                )
            }
            OptionRow(chime.label, selected = chime.id == selected) { TimerEngine.setChime(chime.id) }
        }
    }
}

// ── Duration ───────────────────────────────────────────────────────

private val WheelItemHeight = 56.dp

/** Keeps wheel over-scroll from scrolling the page that contains it. */
private val ConsumeLeftoverScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}

/**
 * Scroll wheel for any whole number of minutes, with shortcut buttons for the usual lengths.
 * Changes apply as the wheel moves, so leaving the page any way keeps what is shown.
 */
@Composable
private fun DurationContent(initialMinutes: Int, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()

    // Item i is minute i+1. Two items of padding above and below let the ends reach the centre.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialMinutes - 1)
    val itemPx = with(LocalDensity.current) { WheelItemHeight.toPx() }
    val centred by remember {
        derivedStateOf {
            val past = if (listState.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            (listState.firstVisibleItemIndex + past).coerceIn(0, MAX_CUSTOM_MINUTES - 1)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { centred }.collect { onChange(it + 1) }
    }

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // The usual lengths, in two rows of large buttons.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DURATION_PRESETS_MIN.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { preset ->
                        val selected = preset == centred + 1
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(ControlShape)
                                .border(1.dp, if (selected) p.fg else p.border, ControlShape)
                                .clickable(role = Role.Button, onClickLabel = "$preset minutes") {
                                    scope.launch { listState.animateScrollToItem(preset - 1) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$preset", color = p.fg,
                                fontSize = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(WheelItemHeight * 5), contentAlignment = Alignment.Center) {
            // Selection band
            Box(Modifier.fillMaxWidth().height(WheelItemHeight).border(1.dp, p.border, ControlShape))
            LazyColumn(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                contentPadding = PaddingValues(vertical = WheelItemHeight * 2),
                modifier = Modifier.fillMaxSize().nestedScroll(ConsumeLeftoverScroll),
            ) {
                items(MAX_CUSTOM_MINUTES) { i ->
                    val selected = i == centred
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(WheelItemHeight)
                            .clickable(interactionSource = null, indication = null) {
                                scope.launch { listState.animateScrollToItem(i) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${i + 1}",
                            Modifier.alpha(if (selected) 1f else 0.4f),
                            color = p.fg,
                            fontSize = if (selected) 34.sp else 24.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Text(
                "min", Modifier.offset(x = 72.dp).alpha(0.7f),
                color = p.fg, fontSize = 18.sp,
            )
        }
    }
}
