package link.darmok.meditationtimer

enum class Status { IDLE, RUNNING, PAUSED }

const val MAX_CUSTOM_MINUTES = 180
val DURATION_PRESETS_MIN = listOf(5, 10, 15, 20, 25, 30)

/**
 * The timer. Times are in the android.os.SystemClock.elapsedRealtime() timebase, so a running
 * timer is defined by its deadline rather than by a counter that has to be decremented.
 */
data class TimerState(
    val durationSec: Int = 25 * 60,
    val repeat: Boolean = false,
    val chime: String = DEFAULT_CHIME,
    val status: Status = Status.IDLE,
    /** Time left; meaningful while not RUNNING. */
    val remainingMs: Long = durationSec * 1000L,
    /** Deadline; meaningful while RUNNING. */
    val endAt: Long = 0,
) {
    private val durationMs get() = durationSec * 1000L

    fun remainingAt(now: Long): Long =
        if (status == Status.RUNNING) (endAt - now).coerceIn(0, durationMs) else remainingMs

    /** Start from idle (full duration) or resume from pause. */
    fun started(now: Long): TimerState = when (status) {
        Status.IDLE -> copy(status = Status.RUNNING, endAt = now + durationMs)
        Status.PAUSED -> copy(status = Status.RUNNING, endAt = now + remainingMs)
        Status.RUNNING -> this
    }

    fun paused(now: Long): TimerState =
        if (status == Status.RUNNING) copy(status = Status.PAUSED, remainingMs = remainingAt(now)) else this

    fun reset(): TimerState = copy(status = Status.IDLE, remainingMs = durationMs)

    /** Duration can only change while idle, as in the desktop app. */
    fun withDuration(seconds: Int): TimerState =
        if (status == Status.IDLE) copy(durationSec = seconds, remainingMs = seconds * 1000L) else this

    /**
     * Returns `this` (same instance) if the timer has not finished by [now]; otherwise the
     * post-completion state: restarted if repeating, else reset, idle and ready to start again.
     */
    fun advanced(now: Long): TimerState = when {
        status != Status.RUNNING || now < endAt -> this
        repeat -> copy(endAt = now + durationMs)
        else -> reset()
    }
}

fun formatTime(remainingMs: Long): String {
    val totalSeconds = (remainingMs + 999) / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
