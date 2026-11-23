package link.darmok.meditationtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TimerStateTest {
    private val fiveMin = TimerState(durationSec = 300)

    @Test
    fun countsDownFromDeadline() {
        val t = fiveMin.started(now = 1_000)
        assertEquals(Status.RUNNING, t.status)
        assertEquals(300_000, t.remainingAt(1_000))
        assertEquals(240_000, t.remainingAt(61_000))
        assertSame(t, t.advanced(300_999))
    }

    @Test
    fun pauseAndResumeKeepRemainingTime() {
        val paused = fiveMin.started(0).paused(100_000)
        assertEquals(Status.PAUSED, paused.status)
        assertEquals(200_000, paused.remainingAt(999_999))

        val resumed = paused.started(500_000)
        assertEquals(200_000, resumed.remainingAt(500_000))
        assertEquals(700_000, resumed.endAt)
    }

    @Test
    fun completionGoesIdleAtZeroThenRestartsFromFull() {
        val done = fiveMin.started(0).advanced(300_000)
        assertEquals(Status.IDLE, done.status)
        assertEquals(0, done.remainingAt(300_000))
        assertEquals(300_000, done.started(400_000).remainingAt(400_000))
    }

    @Test
    fun repeatRestartsOnCompletion() {
        val again = fiveMin.copy(repeat = true).started(0).advanced(300_100)
        assertEquals(Status.RUNNING, again.status)
        assertEquals(600_100, again.endAt)
    }

    @Test
    fun durationOnlyChangesWhileIdle() {
        assertEquals(600, fiveMin.withDuration(600).durationSec)
        assertEquals(600_000, fiveMin.withDuration(600).remainingMs)
        assertEquals(300, fiveMin.started(0).withDuration(600).durationSec)
        assertEquals(300, fiveMin.started(0).paused(10).withDuration(600).durationSec)
    }

    @Test
    fun formatsLikeDesktop() {
        assertEquals("25:00", formatTime(1_500_000))
        assertEquals("00:01", formatTime(1))
        assertEquals("00:00", formatTime(0))
        assertEquals("180:00", formatTime(10_800_000))
    }
}
