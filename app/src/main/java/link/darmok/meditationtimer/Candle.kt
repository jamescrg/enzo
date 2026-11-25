package link.darmok.meditationtimer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The candle: a session measured by one candle burning down. It stands in a dish, is lit when
 * the timer starts, glows and wavers while the timer runs, and has burned down into the dish
 * when the session ends. The countdown sits above the scene, out of its way.
 *
 * All of it is real. The candle and its dish are from a photograph by Tim Umphreys on Unsplash
 * (https://unsplash.com/photos/An_j14lRl5k), and the flame from "candle flame closeup" by
 * lisafree54, CC0 (https://www.flickr.com/photos/136594255@N06/24857435811);
 * extras/bake_candle.py cuts them out. The candle picture is its dripped top above a length of
 * its side that repeats, so the candle can be any height. Candle and dish are drawn quietly;
 * when the flame is lit, the wax near it glows as it does in the photograph, and it is the
 * flame and that glow the eye goes to.
 */
object Candle {
    /** Rows of the candle picture holding its top, and the repeating length of side below. */
    private const val CAP_ROWS = 335
    private const val SIDE_ROWS = 800

    /**
     * The dish's width and foot, as parts of the box. The rest follows the photograph: how wide
     * the candle picture is beside the dish, where the middle of the candle is in each picture,
     * and how far down the dish picture the candle goes out of sight behind the near rim.
     */
    private const val DISH_WIDTH = 0.5f
    private const val DISH_BOTTOM = 0.97f
    private const val CANDLE_WIDTH = 0.2398f
    private const val CANDLE_MIDDLE = 0.5537f
    private const val DISH_MIDDLE = 0.4953f
    private const val DISH_RIM = 0.492f

    /** As parts of the box: the candle's height above the rim when new and when spent. */
    private const val TALL = 0.7f
    private const val STUB = 0.03f

    /** The wick's and the flame's height, as parts of the box. */
    private const val WICK = 0.014f
    private const val FLAME = 0.14f

    /**
     * How far the candle and dish stand out from the background, from 0 (not at all) to 1 (as
     * far as the text does), and how strongly they keep the photograph's light and shade.
     */
    private const val TONE = 0.3f
    private const val SHADING = 0.3f

    /** The wax at the flame glows this far towards the text colour, fading out over [GLOW_REACH] of the box. */
    private const val GLOW_TONE = 0.8f
    private const val GLOW_REACH = 0.42f

    private val layer = Paint()

    /**
     * Draws the candle with [fraction] of it burned away. [time] is in seconds and only moves
     * the flame; [lit] is how fully it is alight, from 0 to 1.
     */
    fun DrawScope.drawCandle(
        candle: ImageBitmap, dish: ImageBitmap, flame: ImageBitmap,
        fraction: Float, time: Float, lit: Float, ink: Color, paper: Color,
    ) {
        val box = size.minDimension
        val f = fraction.coerceIn(0f, 1f)
        val middle = size.width / 2
        val dishWidth = (box * DISH_WIDTH).roundToInt()
        val dishHeight = (dishWidth.toFloat() * dish.height / dish.width).roundToInt()
        val dishLeft = (middle - dishWidth * DISH_MIDDLE).roundToInt()
        val dishTop = (box * DISH_BOTTOM).roundToInt() - dishHeight
        val rim = dishTop + (dishHeight * DISH_RIM).roundToInt()          // where the candle goes out of sight
        val width = (dishWidth * CANDLE_WIDTH).roundToInt()
        val left = (middle - width * CANDLE_MIDDLE).roundToInt()
        val scale = width.toFloat() / candle.width
        val cap = (CAP_ROWS * scale).roundToInt()
        val tile = (SIDE_ROWS * scale).roundToInt()
        val top = rim - (box * (TALL + (STUB - TALL) * f)).roundToInt()
        val quiet = Beads.shaded(lerp(paper, ink, TONE), SHADING)
        val burning = lit > 0f && f < 1f
        val breath = 0.85f + 0.15f * sin(time * 3.3f) * sin(time * 1.1f + 2f)

        drawImage(
            dish, dstOffset = IntOffset(dishLeft, dishTop), dstSize = IntSize(dishWidth, dishHeight),
            colorFilter = quiet, filterQuality = FilterQuality.High,
        )

        // The candle, down to the rim: its side repeated up from the foot so the grain stays put
        // as the top comes down, and its dripped top above. Near the end the top itself sinks
        // behind the rim.
        fun wax(filter: ColorFilter) = clipRect(bottom = rim.toFloat()) {
            clipRect(top = (top + cap).toFloat()) {
                var y = rim
                while (y > top + cap) {
                    y -= tile
                    drawImage(
                        candle,
                        srcOffset = IntOffset(0, CAP_ROWS), srcSize = IntSize(candle.width, SIDE_ROWS),
                        dstOffset = IntOffset(left, y), dstSize = IntSize(width, tile),
                        colorFilter = filter, filterQuality = FilterQuality.High,
                    )
                }
            }
            drawImage(
                candle,
                srcOffset = IntOffset(0, 0), srcSize = IntSize(candle.width, CAP_ROWS),
                dstOffset = IntOffset(left, top), dstSize = IntSize(width, cap),
                colorFilter = filter, filterQuality = FilterQuality.High,
            )
        }
        wax(quiet)
        if (burning) {
            // The flame shines through the wax: the same candle again, bright, kept only near
            // the top and fading away below it.
            val reach = box * GLOW_REACH
            val bounds = Rect(left.toFloat(), top.toFloat(), (left + width).toFloat(), rim.toFloat())
            val canvas = drawContext.canvas
            canvas.saveLayer(bounds, layer)
            wax(Beads.shaded(lerp(paper, ink, GLOW_TONE), SHADING))
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = (lit * breath).coerceIn(0f, 1f)), 1f to Color.Transparent,
                    startY = top.toFloat(), endY = top + reach,
                ),
                topLeft = bounds.topLeft, size = bounds.size, blendMode = BlendMode.DstIn,
            )
            canvas.restore()
        }

        val wickFoot = Offset(middle, top + cap * 0.03f)
        val wickTip = Offset(middle, wickFoot.y - box * WICK)
        if (wickFoot.y < rim) {
            drawLine(lerp(paper, ink, 0.5f), wickFoot, wickTip, strokeWidth = (width * 0.03f).coerceAtLeast(2f), cap = StrokeCap.Round)
        }

        if (burning) {
            // The flame stands on the wick and never holds still: it stretches, leans and
            // brightens a little, on a few slow waves that do not keep step.
            val tall = 1f + 0.05f * sin(time * 5.1f + 2f) * sin(time * 1.7f)
            val wide = 1f + 0.03f * sin(time * 7.3f) * sin(time * 2.9f + 1f)
            val lean = 2.5f * sin(time * 1.9f) * sin(time * 0.7f + 0.5f)
            val height = box * FLAME
            val flameWidth = height * flame.width / flame.height
            val foot = Offset(middle, wickTip.y + height * 0.1f)
            val heart = Offset(middle, foot.y - height * 0.45f)
            // Its light in the air around it: a wide faint halo and a closer, stronger one.
            for ((radius, strength) in listOf(height * 2.6f to 0.10f, height * 1.1f to 0.22f)) {
                drawCircle(
                    Brush.radialGradient(
                        0f to ink.copy(alpha = strength * lit * breath), 1f to ink.copy(alpha = 0f),
                        center = heart, radius = radius,
                    ),
                    radius = radius, center = heart,
                )
            }
            rotate(lean, foot) {
                scale(wide * lit, tall * lit, foot) {
                    // The flame's outline is the photograph's. Inside it, it is one even
                    // light down to its foot, which thins until it can be seen through, most of
                    // all around the wick.
                    val flameTop = foot.y - height
                    val bounds = Rect(foot.x - flameWidth / 2, flameTop, foot.x + flameWidth / 2, foot.y)
                    val canvas = drawContext.canvas
                    canvas.saveLayer(bounds, layer)
                    drawImage(
                        flame,
                        dstOffset = IntOffset(bounds.left.roundToInt(), flameTop.roundToInt()),
                        dstSize = IntSize(flameWidth.roundToInt(), height.roundToInt()),
                        alpha = lit.coerceAtMost(1f), filterQuality = FilterQuality.High,
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0.6f to ink,
                            1f to ink.copy(alpha = 0.12f),
                            startY = flameTop, endY = foot.y,
                        ),
                        topLeft = bounds.topLeft, size = bounds.size, blendMode = BlendMode.SrcIn,
                    )
                    // The dark heart of the flame, around the wick, where nothing is burning yet.
                    val heartOfWick = Offset(foot.x, foot.y - height * 0.16f)
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color.Black.copy(alpha = 0.6f), 1f to Color.Transparent,
                            center = heartOfWick, radius = height * 0.17f,
                        ),
                        radius = height * 0.17f, center = heartOfWick, blendMode = BlendMode.DstOut,
                    )
                    canvas.restore()
                }
            }
        }
    }
}
