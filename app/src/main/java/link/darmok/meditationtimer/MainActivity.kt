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
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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

@Composable
fun TimerApp() {
    val timers by TimerEngine.timers.collectAsStateWithLifecycle()
    val dark by TimerEngine.darkTheme.collectAsStateWithLifecycle()
    val chimeVolume by TimerEngine.chimeVolume.collectAsStateWithLifecycle()
    val p = if (dark) Dark else Light

    val activity = LocalContext.current as ComponentActivity
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // Drives the countdown displays; only ticks while something is running.
    val anyRunning = timers.any { it.status == Status.RUNNING }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(anyRunning) {
        while (anyRunning) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
    }

    var customDurationFor by remember { mutableStateOf<TimerState?>(null) }

    CompositionLocalProvider(LocalPalette provides p) {
        Box(Modifier.fillMaxSize().background(p.bg), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .safeDrawingPadding()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    AppButton("Test Chime", onClick = TimerEngine::testChime, fontSize = 12.sp)
                    AppButton(if (dark) "Light" else "Dark", onClick = TimerEngine::toggleTheme, fontSize = 12.sp)
                }

                VolumePanel(chimeVolume)

                timers.forEach { timer ->
                    TimerCard(
                        timer = timer,
                        now = now,
                        removable = timers.size > 1,
                        onCustomDuration = { customDurationFor = timer },
                    )
                }

                if (timers.size < MAX_TIMERS) {
                    AppButton("+ Add Timer", onClick = TimerEngine::addTimer, fontSize = 13.sp)
                }
            }
        }

        customDurationFor?.let { timer ->
            CustomDurationDialog(
                initialMinutes = (timer.durationSec / 60f).roundToInt(),
                onDismiss = { customDurationFor = null },
                onOk = { minutes ->
                    TimerEngine.setDuration(timer.id, minutes * 60)
                    customDurationFor = null
                },
            )
        }
    }
}

// ── Volume ─────────────────────────────────────────────────────────

@Composable
private fun VolumePanel(chimeVolume: Float) {
    val p = LocalPalette.current
    val context = LocalContext.current
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
        Modifier
            .fillMaxWidth()
            .background(p.bgDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
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

@Composable
private fun VolumeRow(label: String, percent: Int, onChange: (Int) -> Unit, onChangeFinished: () -> Unit = {}) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label, Modifier.width(50.dp).alpha(0.8f),
            color = p.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
        Slider(
            value = percent.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onChangeFinished,
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.btnBg,
            ),
        )
        Text(
            "$percent%", Modifier.width(38.dp).alpha(0.7f),
            color = p.fg, fontSize = 12.sp, textAlign = TextAlign.End,
        )
    }
}

// ── Timer card ─────────────────────────────────────────────────────

private const val CUSTOM = -1

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerCard(timer: TimerState, now: Long, removable: Boolean, onCustomDuration: () -> Unit) {
    val p = LocalPalette.current
    val running = timer.status == Status.RUNNING
    val idle = timer.status == Status.IDLE

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.bgDim)
            .drawBehind {
                if (running) drawRect(p.accent, size = Size(3.dp.toPx(), size.height))
            },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                formatTime(timer.remainingAt(now)),
                color = p.fg, fontSize = 48.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    text = when (timer.status) {
                        Status.IDLE -> "Start"
                        Status.RUNNING -> "Pause"
                        Status.PAUSED -> "Resume"
                    },
                    onClick = { if (running) TimerEngine.pause(timer.id) else TimerEngine.start(timer.id) },
                    modifier = Modifier.width(110.dp),
                    accent = true,
                )
                AppButton("Reset", onClick = { TimerEngine.reset(timer.id) }, modifier = Modifier.width(110.dp))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val minutes = timer.durationSec / 60
                val isPreset = timer.durationSec % 60 == 0 && minutes in DURATION_PRESETS_MIN
                Dropdown(
                    options = DURATION_PRESETS_MIN.map { it to "$it min" } +
                        (CUSTOM to if (isPreset) "Custom..." else "Custom (${minutes}m)"),
                    selected = if (isPreset) minutes else CUSTOM,
                    enabled = idle,
                    onSelect = { if (it == CUSTOM) onCustomDuration() else TimerEngine.setDuration(timer.id, it * 60) },
                )
                Dropdown(
                    options = CHIMES.map { it.id to it.label },
                    selected = timer.chime,
                    enabled = idle,
                    onSelect = { TimerEngine.setChime(timer.id, it) },
                )
                Row(
                    Modifier
                        .height(40.dp)
                        .toggleable(timer.repeat, role = Role.Checkbox) { TimerEngine.setRepeat(timer.id, it) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Checkbox(
                        checked = timer.repeat,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = p.accent, uncheckedColor = p.btnHover, checkmarkColor = p.bgDim,
                        ),
                    )
                    Text("Repeat", color = p.fg, fontSize = 13.sp)
                }
            }
        }

        if (removable) {
            Text(
                "×",
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Remove timer") { TimerEngine.removeTimer(timer.id) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .alpha(0.5f),
                color = p.fg, fontSize = 22.sp,
            )
        }
    }
}

// ── Shared widgets ─────────────────────────────────────────────────

@Composable
private fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    fontSize: TextUnit = 14.sp,
) {
    val p = LocalPalette.current
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) p.accent else p.btnBg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (accent) p.bgDim else p.fg, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun <T> Dropdown(options: List<Pair<T, String>>, selected: T, enabled: Boolean, onSelect: (T) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)

    Box(Modifier.alpha(if (enabled) 1f else 0.5f)) {
        Row(
            Modifier
                .height(40.dp)
                .clip(shape)
                .background(p.btnBg)
                .border(1.dp, if (open) p.accent else p.modalBorder, shape)
                .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                options.firstOrNull { it.first == selected }?.second.orEmpty(),
                color = p.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(if (open) "▴" else "▾", Modifier.alpha(0.6f), color = p.fg, fontSize = 12.sp)
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = p.modalBg) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                DropdownMenuItem(
                    text = { Text(label, fontSize = 14.sp) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) p.accent else Color.Transparent),
                    colors = MenuDefaults.itemColors(textColor = if (isSelected) p.bgDim else p.fg),
                )
            }
        }
    }
}

@Composable
private fun CustomDurationDialog(initialMinutes: Int, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    val p = LocalPalette.current
    val initial = initialMinutes.toString()
    // Start with the current value selected so typing replaces it.
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    val shape = RoundedCornerShape(10.dp)

    // As on desktop: an out-of-range value just closes the dialog without changing anything.
    fun submit() {
        val minutes = field.text.toIntOrNull()
        if (minutes != null && minutes in 1..MAX_CUSTOM_MINUTES) onOk(minutes) else onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 280.dp)
                .background(p.modalBg, shape)
                .border(1.dp, p.modalBorder, shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Custom Duration", color = p.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "Enter duration in minutes (1–$MAX_CUSTOM_MINUTES):",
                Modifier.alpha(0.8f), color = p.fg, fontSize = 13.sp,
            )
            BasicTextField(
                value = field,
                onValueChange = { new -> if (new.text.length <= 3 && new.text.all(Char::isDigit)) field = new },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .background(p.inputBg, RoundedCornerShape(6.dp))
                    .border(1.dp, p.accent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                singleLine = true,
                textStyle = TextStyle(color = p.fg, fontSize = 15.sp),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                AppButton("Cancel", onClick = onDismiss, fontSize = 13.sp)
                AppButton("OK", onClick = ::submit, accent = true, fontSize = 13.sp)
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}
