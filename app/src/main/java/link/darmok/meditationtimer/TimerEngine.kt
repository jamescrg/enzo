package link.darmok.meditationtimer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        TimerEngine.init(this)
    }
}

/**
 * Process-wide timer state. Lives outside the Activity so the timer keeps running (kept alive
 * by [TimerService]) when the UI is gone. Main-thread only.
 */
object TimerEngine {
    private const val TICK_MS = 200L

    private val scope = MainScope()
    private lateinit var app: Application
    private var ticker: Job? = null

    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme

    private val _chimeVolume = MutableStateFlow(0.8f)
    val chimeVolume: StateFlow<Float> = _chimeVolume

    /** Silence calls and notifications while the timer runs. */
    private val _doNotDisturb = MutableStateFlow(false)
    val doNotDisturb: StateFlow<Boolean> = _doNotDisturb

    /** Keep the display awake while the timer runs. */
    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn

    private fun now() = SystemClock.elapsedRealtime()

    fun init(application: Application) {
        app = application
        load()
        // Nothing is running at process start, so undo Do Not Disturb left on by a killed session.
        QuietMode.end(app)
    }

    // ── Timer controls ────────────────────────────────────────────

    fun start() {
        ChimePlayer.dismissFinished(app)
        _timer.value = _timer.value.started(now())
        ContextCompat.startForegroundService(app, Intent(app, TimerService::class.java))
        if (ticker?.isActive != true) ticker = scope.launch { runTicker() }
    }

    fun pause() {
        _timer.value = _timer.value.paused(now())
    }

    fun reset() {
        ChimePlayer.dismissFinished(app)
        _timer.value = _timer.value.reset()
    }

    fun setDuration(seconds: Int) {
        if (seconds == _timer.value.durationSec) return
        _timer.value = _timer.value.withDuration(seconds)
        save()
    }

    fun setChime(chime: String) {
        _timer.value = _timer.value.copy(chime = chime)
        save()
    }

    fun setRepeat(repeat: Boolean) {
        _timer.value = _timer.value.copy(repeat = repeat)
        save()
    }

    private suspend fun runTicker() {
        while (_timer.value.status == Status.RUNNING) {
            val before = _timer.value
            val after = before.advanced(now())
            // advanced() returns the same instance unless the timer just completed.
            if (after !== before) {
                _timer.value = after
                playChime(before.chime)
            }
            delay(TICK_MS)
        }
    }

    // ── Global settings ───────────────────────────────────────────

    fun playChime(chime: String) = ChimePlayer.play(app, chime, _chimeVolume.value)

    /** Preview the selected chime, cutting off any earlier one so previews don't pile up. */
    fun testChime() {
        ChimePlayer.stopAll()
        playChime(_timer.value.chime)
    }

    fun toggleTheme() {
        _darkTheme.value = !_darkTheme.value
        save()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        save()
    }

    fun setDoNotDisturb(enabled: Boolean) {
        _doNotDisturb.value = enabled
        save()
        // Apply straight away if a session is already under way.
        if (enabled && _timer.value.status == Status.RUNNING) QuietMode.begin(app) else if (!enabled) QuietMode.end(app)
    }

    /** Call [save] when the user finishes adjusting. */
    fun setChimeVolume(volume: Float) {
        _chimeVolume.value = volume.coerceIn(0f, 1f)
    }

    // ── Persistence (same JSON shape as the desktop app's settings.json) ──

    private val prefs get() = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun save() {
        val t = _timer.value
        val json = JSONObject()
            .put("version", 3)
            .put("theme", if (_darkTheme.value) "dark" else "light")
            .put("chimeVolume", _chimeVolume.value.toDouble())
            .put("doNotDisturb", _doNotDisturb.value)
            .put("keepScreenOn", _keepScreenOn.value)
            .put("timers", JSONArray().put(
                JSONObject().put("duration", t.durationSec).put("repeat", t.repeat).put("chime", t.chime)
            ))
        prefs.edit().putString("json", json.toString()).apply()
    }

    private fun load() {
        val json = runCatching { JSONObject(prefs.getString("json", null) ?: "{}") }.getOrDefault(JSONObject())
        _darkTheme.value = json.optString("theme", "dark") != "light"
        _chimeVolume.value = json.optDouble("chimeVolume", 0.8).toFloat().coerceIn(0f, 1f)
        _doNotDisturb.value = json.optBoolean("doNotDisturb", false)
        _keepScreenOn.value = json.optBoolean("keepScreenOn", false)

        // Only one timer on Android; if several were saved, keep the first.
        val t = json.optJSONArray("timers")?.optJSONObject(0) ?: JSONObject()
        _timer.value = TimerState(
            durationSec = t.optInt("duration", 25 * 60).coerceIn(60, MAX_CUSTOM_MINUTES * 60),
            repeat = t.optBoolean("repeat", false),
            chime = chimeById(t.optString("chime")).id,
        )
    }
}
