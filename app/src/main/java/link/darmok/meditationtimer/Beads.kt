package link.darmok.meditationtimer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The beads ring: a loop of prayer beads, counted off one by one as the session runs. Counting
 * is what such beads are for in many traditions, so the form is left plain and unnamed: a cord,
 * a round of beads, and one larger bead at the top where the round begins and ends.
 *
 * The beads are real ones. Their wood grain and the light across them come from the photograph
 * "Rosary 2006-01-16.jpg" by Daniel Tibi, public domain, from Wikimedia Commons
 * (https://commons.wikimedia.org/wiki/File:Rosary_2006-01-16.jpg), baked into a strip of grey
 * tiles (res/drawable-nodpi/beads.png) by extras/bake_beads.py. Each tile is mid-grey on
 * average and is drawn as a theme colour, lightened and darkened by the tile.
 */
object Beads {
    /** Beads in the round, not counting the larger one at the top: a quarter of a mala's 108. */
    const val COUNT = 27

    /** The larger bead's width, in ordinary beads. */
    private const val MARKER = 1.35f

    /** Tiles in the strip: nine for the round, used in turn, and the last for the larger bead. */
    private const val TILES = 10

    /** How strongly a tile lightens and darkens the colour, counted and uncounted. */
    private const val INK_SHADING = 0.5f
    private const val TRACK_SHADING = 0.22f

    /** The part of a bead's share of the session over which it changes from uncounted to counted. */
    private const val FADE = 0.5f

    /**
     * Draws the round in [track], with the beads counted so far in [ink]. Counting starts with
     * the larger bead at the top and runs clockwise; the bead being counted changes gradually.
     */
    fun DrawScope.drawBeads(tiles: ImageBitmap, fraction: Float, ink: Color, track: Color) {
        val gap = MARKER + 0.15f
        val slotAngle = 2 * PI.toFloat() / (COUNT + gap)
        // Sized so the larger bead just fits inside the box.
        val ringRadius = size.minDimension / 2 / (1 + MARKER * 0.47f * slotAngle)
        val bead = ringRadius * slotAngle * 0.94f
        val tile = tiles.height

        fun draw(index: Int, slot: Float, diameter: Float, color: Color, shading: Float, alpha: Float) {
            val a = -PI.toFloat() / 2 + slot * slotAngle
            val px = diameter.roundToInt()
            drawImage(
                tiles,
                srcOffset = IntOffset(index * tile, 0), srcSize = IntSize(tile, tile),
                dstOffset = IntOffset(
                    (center.x + ringRadius * cos(a) - px / 2f).roundToInt(),
                    (center.y + ringRadius * sin(a) - px / 2f).roundToInt(),
                ),
                dstSize = IntSize(px, px),
                alpha = alpha,
                colorFilter = shaded(color, shading),
                filterQuality = FilterQuality.High,
            )
        }

        // The cord.
        drawCircle(track, ringRadius, center, style = Stroke(bead * 0.07f))

        // The larger bead is counted first, then the round. Each bead takes an equal share of
        // the session and is fully inked partway through its share, so the last one is plainly
        // lit before the session ends.
        val counted = fraction.coerceIn(0f, 1f) * (COUNT + 1)
        fun inked(step: Int) = ((counted - step) / FADE).coerceIn(0f, 1f)

        draw(TILES - 1, 0f, bead * MARKER, track, TRACK_SHADING, 1f)
        inked(0).let { if (it > 0f) draw(TILES - 1, 0f, bead * MARKER, ink, INK_SHADING, it) }
        for (i in 0 until COUNT) {
            val slot = gap / 2 + 0.5f + i
            val variant = i % (TILES - 1)
            draw(variant, slot, bead, track, TRACK_SHADING, 1f)
            inked(i + 1).let { if (it > 0f) draw(variant, slot, bead, ink, INK_SHADING, it) }
        }
    }

    /** Turns a grey tile into [color], lighter where the tile is above mid-grey and darker below. */
    private fun shaded(color: Color, shading: Float): ColorFilter {
        fun offset(channel: Float) = channel * 255f - shading * 127.5f
        return ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    shading, 0f, 0f, 0f, offset(color.red),
                    0f, shading, 0f, 0f, offset(color.green),
                    0f, 0f, shading, 0f, offset(color.blue),
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }
}
