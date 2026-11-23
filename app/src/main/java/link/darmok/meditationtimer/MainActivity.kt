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
import androidx.compose.animation.core.animateFloatAsState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.border
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
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
    val accent: Color,
    val btnBg: Color,
    val btnHover: Color,
    val modalBg: Color,
    val modalBorder: Color,
    val inputBg: Color,
)

/** Everforest Dark */
private val Dark = Palette(
    bg = Color(0xFF2D353B), bgDim = Color(0xFF232A2E), fg = Color(0xFFD3C6AA),
    accent = Color(0xFFA7C080), btnBg = Color(0xFF3D484D), btnHover = Color(0xFF475258),
    modalBg = Color(0xFF343F44), modalBorder = Color(0xFF475258), inputBg = Color(0xFF2D353B),
)

/** Seoul256 Light */
private val Light = Palette(
    bg = Color(0xFFE1E1E1), bgDim = Color(0xFFD9D9D9), fg = Color(0xFF3F3F3F),
    accent = Color(0xFF719872), btnBg = Color(0xFFD0D0D0), btnHover = Color(0xFFC0C0C0),
    modalBg = Color(0xFFE8E8E8), modalBorder = Color(0xFFC0C0C0), inputBg = Color(0xFFF0F0F0),
)

private val LocalPalette = staticCompositionLocalOf { Dark }

// ── Screen ─────────────────────────────────────────────────────────

private val CardShape = RoundedCornerShape(20.dp)
private val ControlShape = RoundedCornerShape(12.dp)

private enum class Sheet { DURATION, SETTINGS }

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TimerApp() {
    val timer by TimerEngine.timer.collectAsStateWithLifecycle()
    val dark by TimerEngine.darkTheme.collectAsStateWithLifecycle()
    val keepScreenOn by TimerEngine.keepScreenOn.collectAsStateWithLifecycle()
    val ringOnly by TimerEngine.ringOnly.collectAsStateWithLifecycle()
    val brushRing by TimerEngine.brushRing.collectAsStateWithLifecycle()
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

    var sheet by remember { mutableStateOf<Sheet?>(null) }
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
                // Swipe up anywhere to pull up settings. Watched on the way down to the children
                // and normally not consumed, so buttons and the ring still get their taps.
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
                        var opened = false
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial)
                                .changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val moved = change.position - down.position
                            val mostlyUp = moved.y < -threshold && -moved.y > abs(moved.x) * 2
                            // On a screen small enough to scroll, an upward drag scrolls first.
                            if (!opened && mostlyUp && !scrollState.canScrollForward) {
                                opened = true
                                sheet = Sheet.SETTINGS
                            }
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
                // Settings: tap this small ring (it echoes the timer), or swipe up anywhere.
                Box(
                    Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp, end = 8.dp)
                        .alpha(controlsAlpha)
                        .size(64.dp) // generous touch target around a small ring
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Settings") { sheet = Sheet.SETTINGS },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(24.dp).alpha(0.8f).border(2.5.dp, p.fg, CircleShape))
                }

                val remainingMs = timer.remainingAt(now)
                CountdownRing(
                    fraction = 1f - remainingMs / (timer.durationSec * 1000f),
                    brush = brushRing,
                    modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(0.8f).widthIn(max = 320.dp),
                    // Tap the time to change it. Locked once the timer has been started, as on desktop.
                    onClick = if (idle) ({ sheet = Sheet.DURATION }) else null,
                ) {
                    val text = formatTime(remainingMs)
                    Text(
                        text,
                        Modifier.alpha(timeAlpha),
                        color = p.fg,
                        // Three-digit minutes need a smaller size to stay inside the ring, and the
                        // painted ring is much thicker than the plain one.
                        fontSize = when {
                            brushRing -> if (text.length > 5) 50.sp else 62.sp
                            text.length > 5 -> 58.sp
                            else -> 72.sp
                        },
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false,
                    )
                }

                Column(Modifier.alpha(controlsAlpha), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.fillMaxWidth().background(p.bgDim, CardShape).padding(8.dp),
                    ) {
                        SwitchRow("Repeat", timer.repeat, onChange = TimerEngine::setRepeat)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppButton(
                            "Reset", onClick = TimerEngine::reset,
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
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
                            primary = true,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }

        when (sheet) {
            Sheet.DURATION -> DurationSheet(
                initialMinutes = minutes,
                onChange = { TimerEngine.setDuration(it * 60) },
                onDismiss = { sheet = null },
            )
            Sheet.SETTINGS -> SettingsSheet(onDismiss = { sheet = null })
            null -> {}
        }
    }
}

/** Ring showing [fraction] of the session elapsed, filling clockwise from the top. */
@Composable
private fun CountdownRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    brush: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    Box(
        modifier
            .aspectRatio(1f)
            // Drawn before the clip: the painted ring is larger than the tap target.
            .drawBehind {
                val f = fraction.coerceIn(0f, 1f)
                if (brush) {
                    with(EnsoStroke) { drawEnso(f, ink = p.fg, track = p.btnBg) }
                    return@drawBehind
                }
                val stroke = 10.dp.toPx()
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
            .clip(CircleShape)
            .clickable(enabled = onClick != null, role = Role.Button, onClickLabel = "Set duration") { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ── Settings ───────────────────────────────────────────────────────

/** Everything that isn't the timer itself: chime, volume, and how the phone behaves meanwhile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val timer by TimerEngine.timer.collectAsStateWithLifecycle()
    val chimeVolume by TimerEngine.chimeVolume.collectAsStateWithLifecycle()
    val dark by TimerEngine.darkTheme.collectAsStateWithLifecycle()
    val doNotDisturb by TimerEngine.doNotDisturb.collectAsStateWithLifecycle()
    val keepScreenOn by TimerEngine.keepScreenOn.collectAsStateWithLifecycle()
    val ringOnly by TimerEngine.ringOnly.collectAsStateWithLifecycle()
    val brushRing by TimerEngine.brushRing.collectAsStateWithLifecycle()

    // Re-checked on every return to the app, since access is granted in system settings.
    var dndAccess by remember { mutableStateOf(QuietMode.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        dndAccess = QuietMode.hasAccess(context)
        onPauseOrDispose {}
    }

    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maxStep = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    fun stepOf(percent: Int) = (percent * maxStep / 100f).roundToInt()
    fun systemPercent() = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / maxStep).roundToInt()

    var systemVolume by remember { mutableIntStateOf(systemPercent()) }
    var tab by remember { mutableStateOf(SettingsTab.CHIME) }

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Tall enough to fill the screen, so stop short of the status bar.
        modifier = Modifier.statusBarsPadding(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.modalBg,
        contentColor = p.fg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = p.btnHover) },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Segmented tabs
            Row(
                Modifier.fillMaxWidth().background(p.bgDim, ControlShape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SettingsTab.entries.forEach { t ->
                    val selected = t == tab
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) p.btnBg else Color.Transparent)
                            .selectable(selected, role = Role.Tab) { tab = t },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            t.label, Modifier.alpha(if (selected) 1f else 0.6f),
                            color = p.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Same height for every tab (sized for the chime list) so the sheet doesn't jump.
            Column(
                Modifier.padding(top = 12.dp).heightIn(min = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (tab) {
                    // Tapping a chime selects it and plays it, like a ringtone picker.
                    SettingsTab.CHIME -> CHIMES.forEachIndexed { i, chime ->
                        // Thin rule between the sounds and the silent vibrate options.
                        if (chime.res == null && CHIMES.getOrNull(i - 1)?.res != null) {
                            Box(
                                Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(p.btnHover),
                            )
                        }
                        OptionRow(chime.label, selected = chime.id == timer.chime) {
                            TimerEngine.setChime(chime.id)
                            TimerEngine.testChime()
                        }
                    }

                    SettingsTab.VOLUME -> Column(
                        Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp),
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
                        AppButton(
                            "Test chime", onClick = TimerEngine::testChime,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        )
                    }

                    SettingsTab.OPTIONS -> {
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
                            "Ring only", ringOnly,
                            caption = "Hide the countdown too when the controls fade",
                            onChange = TimerEngine::setRingOnly,
                        )
                        SwitchRow("Brushstroke ring", brushRing, onChange = TimerEngine::setBrushRing)
                        SwitchRow("Dark theme", dark, onChange = { TimerEngine.toggleTheme() })
                    }
                }
            }
        }
    }
}

private enum class SettingsTab(val label: String) { CHIME("Chime"), VOLUME("Volume"), OPTIONS("Options") }

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
                thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.btnBg,
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
                checkedThumbColor = p.bgDim, checkedTrackColor = p.accent,
                uncheckedThumbColor = p.fg, uncheckedTrackColor = p.btnBg,
                uncheckedBorderColor = p.btnHover,
            ),
        )
    }
}

@Composable
private fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
) {
    val p = LocalPalette.current
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(ControlShape)
            .background(if (primary) p.fg else p.btnBg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (primary) p.bgDim else p.fg,
            fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

/** A large single-choice row; the selected one is filled with the accent colour. */
@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(ControlShape)
            .background(if (selected) p.accent else Color.Transparent)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, color = if (selected) p.bgDim else p.fg, fontSize = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── Duration ───────────────────────────────────────────────────────

private val WheelItemHeight = 56.dp

/** Keeps wheel over-scroll from dragging the bottom sheet that contains it. */
private val ConsumeLeftoverScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}

/**
 * Scroll wheel for any whole number of minutes, with shortcut buttons for the usual lengths.
 * Changes apply as the wheel moves, so closing the sheet any way keeps what is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationSheet(initialMinutes: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.modalBg,
        contentColor = p.fg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = p.btnHover) },
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Duration", Modifier.padding(start = 16.dp),
                color = p.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DURATION_PRESETS_MIN.forEach { preset ->
                    val selected = preset == centred + 1
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(ControlShape)
                            .background(if (selected) p.accent else p.btnBg)
                            .clickable(role = Role.Button, onClickLabel = "$preset minutes") {
                                scope.launch { listState.animateScrollToItem(preset - 1) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$preset", color = if (selected) p.bgDim else p.fg,
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(WheelItemHeight * 5), contentAlignment = Alignment.Center) {
                // Selection band
                Box(Modifier.fillMaxWidth().height(WheelItemHeight).background(p.btnBg, ControlShape))
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
                                color = if (selected) p.accent else p.fg,
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

            AppButton(
                "Done",
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                primary = true,
                fontSize = 18.sp,
            )
        }
    }
}
