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
 * Process-wide timer state. Lives outside the Activity so timers keep running (kept alive by
 * [TimerService]) when the UI is gone. Main-thread only.
 */
object TimerEngine {
    private const val TICK_MS = 200L

    private val scope = MainScope()
    private lateinit var app: Application
    private var nextId = 0
    private var ticker: Job? = null

    private val _timers = MutableStateFlow<List<TimerState>>(emptyList())
    val timers: StateFlow<List<TimerState>> = _timers

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme

    private val _chimeVolume = MutableStateFlow(0.8f)
    val chimeVolume: StateFlow<Float> = _chimeVolume

    private fun now() = SystemClock.elapsedRealtime()

    fun init(application: Application) {
        app = application
        load()
    }

    // ── Timer list ────────────────────────────────────────────────

    fun addTimer() {
        if (_timers.value.size >= MAX_TIMERS) return
        _timers.value += TimerState(id = nextId++)
        save()
    }

    fun removeTimer(id: Int) {
        if (_timers.value.size <= 1) return
        _timers.value = _timers.value.filterNot { it.id == id }
        save()
    }

    private inline fun update(id: Int, transform: (TimerState) -> TimerState) {
        _timers.value = _timers.value.map { if (it.id == id) transform(it) else it }
    }

    // ── Per-timer controls ────────────────────────────────────────

    fun start(id: Int) {
        update(id) { it.started(now()) }
        ContextCompat.startForegroundService(app, Intent(app, TimerService::class.java))
        if (ticker?.isActive != true) ticker = scope.launch { runTicker() }
    }

    fun pause(id: Int) = update(id) { it.paused(now()) }

    fun reset(id: Int) = update(id) { it.reset() }

    fun setDuration(id: Int, seconds: Int) {
        update(id) { it.withDuration(seconds) }
        save()
    }

    fun setChime(id: Int, chime: String) {
        update(id) { it.copy(chime = chime) }
        save()
    }

    fun setRepeat(id: Int, repeat: Boolean) {
        update(id) { it.copy(repeat = repeat) }
        save()
    }

    private suspend fun runTicker() {
        while (_timers.value.any { it.status == Status.RUNNING }) {
            val now = now()
            val before = _timers.value
            val after = before.map { it.advanced(now) }
            _timers.value = after
            // advanced() returns the same instance unless the timer just completed.
            before.zip(after).forEach { (b, a) -> if (a !== b) playChime(b.chime) }
            delay(TICK_MS)
        }
    }

    // ── Global settings ───────────────────────────────────────────

    fun playChime(chime: String) = ChimePlayer.play(app, chime, _chimeVolume.value)

    fun testChime() = playChime(_timers.value.firstOrNull()?.chime ?: DEFAULT_CHIME)

    fun toggleTheme() {
        _darkTheme.value = !_darkTheme.value
        save()
    }

    /** Call [save] when the user finishes adjusting. */
    fun setChimeVolume(volume: Float) {
        _chimeVolume.value = volume.coerceIn(0f, 1f)
    }

    // ── Persistence (same JSON shape as the desktop app's settings.json) ──

    private val prefs get() = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun save() {
        val json = JSONObject()
            .put("version", 3)
            .put("theme", if (_darkTheme.value) "dark" else "light")
            .put("chimeVolume", _chimeVolume.value.toDouble())
            .put("timers", JSONArray(_timers.value.map {
                JSONObject().put("duration", it.durationSec).put("repeat", it.repeat).put("chime", it.chime)
            }))
        prefs.edit().putString("json", json.toString()).apply()
    }

    private fun load() {
        val json = runCatching { JSONObject(prefs.getString("json", null) ?: "{}") }.getOrDefault(JSONObject())
        _darkTheme.value = json.optString("theme", "dark") != "light"
        _chimeVolume.value = json.optDouble("chimeVolume", 0.8).toFloat().coerceIn(0f, 1f)

        val saved = json.optJSONArray("timers") ?: JSONArray()
        val loaded = (0 until minOf(saved.length(), MAX_TIMERS)).mapNotNull { i ->
            val t = saved.optJSONObject(i) ?: return@mapNotNull null
            TimerState(
                id = nextId++,
                durationSec = t.optInt("duration", 25 * 60).coerceIn(60, MAX_CUSTOM_MINUTES * 60),
                repeat = t.optBoolean("repeat", false),
                chime = chimeById(t.optString("chime")).id,
            )
        }
        _timers.value = loaded.ifEmpty { listOf(TimerState(id = nextId++)) }
    }
}
