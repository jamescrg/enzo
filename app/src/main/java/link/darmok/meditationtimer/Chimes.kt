package link.darmok.meditationtimer

import android.content.Context
import android.media.MediaPlayer
import android.os.PowerManager
import androidx.annotation.RawRes

/**
 * All samples are CC0 from Freesound.org (see README). Ids match the desktop app's settings.
 */
data class Chime(
    val id: String,
    val label: String,
    @RawRes val res: Int,
    val maxVolume: Float = 1f,
)

// Ordered highest pitch to lowest
val CHIMES = listOf(
    Chime("wind", "Tingsha", R.raw.tingsha, maxVolume = 0.2f),
    Chime("chime", "Chime", R.raw.chime),
    Chime("bell", "Singing Bowl", R.raw.singing_bowl),
    Chime("gong", "Singing Bowl 2", R.raw.deep_gong),
    Chime("moktak", "Moktak", R.raw.moktak),
)

const val DEFAULT_CHIME = "bell"

fun chimeById(id: String): Chime = CHIMES.find { it.id == id } ?: CHIMES.first { it.id == DEFAULT_CHIME }

object ChimePlayer {
    // Hold references so players aren't garbage-collected mid-sound.
    private val playing = mutableSetOf<MediaPlayer>()

    /** Plays on the media stream; overlapping chimes mix, as on desktop. */
    fun play(context: Context, chimeId: String, volume: Float) {
        val chime = chimeById(chimeId)
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

    fun stopAll() {
        playing.forEach { it.release() }
        playing.clear()
    }
}
