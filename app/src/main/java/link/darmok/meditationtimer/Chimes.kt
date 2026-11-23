package link.darmok.meditationtimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RawRes
import androidx.core.app.NotificationCompat

/**
 * All samples are CC0 from Freesound.org (see README). Ids match the desktop app's settings.
 */
data class Chime(
    val id: String,
    val label: String,
    /** Sound sample, or null for the options that make no sound. */
    @RawRes val res: Int?,
    val maxVolume: Float = 1f,
    /** Off/on durations in ms (starting with off) for vibrate options; empty means no vibration. */
    val vibration: List<Long> = emptyList(),
)

// Ordered highest pitch to lowest
val CHIMES = listOf(
    Chime("wind", "Tingsha", R.raw.tingsha, maxVolume = 0.2f),
    // Recorded about 20 dB hotter than the bowls; scaled down to sit level with them.
    Chime("chime", "Chime", R.raw.chime, maxVolume = 0.12f),
    Chime("bell", "Singing Bowl", R.raw.singing_bowl),
    Chime("gong", "Singing Bowl 2", R.raw.deep_gong),
    Chime("moktak", "Moktak", R.raw.moktak),
    Chime("moktak_short", "Moktak (short)", R.raw.moktak_short),
    Chime("vibrate", "Vibrate", res = null, vibration = listOf(0, 500, 250, 500)),
    Chime("vibrate_long", "Long vibrate", res = null, vibration = listOf(0, 4500)),
    // No sound and no vibration: the end is only shown on screen.
    Chime("silent", "Silent", res = null),
)

const val DEFAULT_CHIME = "bell"

fun chimeById(id: String): Chime = CHIMES.find { it.id == id } ?: CHIMES.first { it.id == DEFAULT_CHIME }

object ChimePlayer {
    // Hold references so players aren't garbage-collected mid-sound.
    private val playing = mutableSetOf<MediaPlayer>()

    /** Plays on the media stream; overlapping chimes mix, as on desktop. */
    fun play(context: Context, chimeId: String, volume: Float) {
        val chime = chimeById(chimeId)
        if (chime.res == null) {
            if (chime.vibration.isEmpty()) showFinished(context) else vibrate(context, chime.vibration)
            return
        }
        val v = volume.coerceIn(0f, chime.maxVolume)
        val player = MediaPlayer.create(context, chime.res) ?: return
        playing += player
        val done = { playing -= player; player.release() }
        player.setOnCompletionListener { done() }
        player.setOnErrorListener { _, _, _ -> done(); true }
        // Let the sound ring out even if the last timer just ended and the service is stopping.
        player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
        player.setVolume(v, v)
        player.start()
    }

    private const val FINISHED_CHANNEL = "finished"
    private const val FINISHED_ID = 2

    /**
     * Silent option: a notification that pops up on screen (and lights the lock screen) with no
     * sound or vibration. Marked as an alarm so Do Not Disturb, which may still be on at the
     * moment the timer ends, lets it through.
     */
    private fun showFinished(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(FINISHED_CHANNEL, "Timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            FINISHED_ID,
            NotificationCompat.Builder(context, FINISHED_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("Session complete")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Clears a leftover "Session complete" notice, e.g. when the next session starts. */
    fun dismissFinished(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(FINISHED_ID)
    }

    /** Sent as an alarm so it still fires with the screen off. */
    private fun vibrate(context: Context, pattern: List<Long>) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        val effect = VibrationEffect.createWaveform(pattern.toLongArray(), -1)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    fun stopAll() {
        playing.forEach { it.release() }
        playing.clear()
    }
}
