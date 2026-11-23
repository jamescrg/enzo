package link.darmok.meditationtimer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RawRes

/**
 * All samples are CC0 from Freesound.org (see README). Ids match the desktop app's settings.
 */
data class Chime(
    val id: String,
    val label: String,
    /** Sound sample, or null for the silent vibrate-only option. */
    @RawRes val res: Int?,
    val maxVolume: Float = 1f,
)

// Ordered highest pitch to lowest
val CHIMES = listOf(
    Chime("wind", "Tingsha", R.raw.tingsha, maxVolume = 0.2f),
    Chime("chime", "Chime", R.raw.chime),
    Chime("bell", "Singing Bowl", R.raw.singing_bowl),
    Chime("gong", "Singing Bowl 2", R.raw.deep_gong),
    Chime("moktak", "Moktak", R.raw.moktak),
    Chime("vibrate", "Vibrate only", res = null),
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
            vibrateTwice(context)
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

    /** Two short buzzes. Sent as an alarm so it still fires with the screen off. */
    private fun vibrateTwice(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1)
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
