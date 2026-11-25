package link.darmok.meditationtimer

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The incense stick: a session measured the old way, by one stick burning down. It stands in a
 * cast iron bowl, burns from the top, keeps a little ash on its glowing end until that drops
 * into the bowl, and smokes while the timer runs. The countdown sits above the scene, out of
 * its way.
 *
 * The bowl is real: it is cut from a product photograph of a cast iron incense bowl by
 * extras/bake_incense_dish.py, and the stick stands in its holder where, and at the angle, the
 * stick in that photograph does. It is drawn faintly, so that the stick and not the bowl is
 * what the eye goes to. The stick and its ash come from the photograph "Incense in
 * India.jpg", CC0, from Wikimedia Commons
 * (https://commons.wikimedia.org/wiki/File:Incense_in_India.jpg), baked into a grey strip
 * (res/drawable-nodpi/incense.png, ash above a repeating length of stick) by
 * extras/bake_incense.py. The smoke is drawn: a few soft
 * ribbons that rise together from the ember and wander apart as they climb.
 */
object Incense {
    /** Rows of the strip holding the ash, and the repeating length of stick below them. */
    private const val ASH_ROWS = 96
    private const val BODY_ROWS = 256

    /** The stick's width, as a part of the box. */
    private const val WIDTH = 0.015f

    /**
     * The bowl, as parts of the box: its width, its left edge and its feet. The stick goes into
     * the holder in the middle at [SOCKET_X], [SOCKET_Y] of the bowl picture, and leans as far
     * from upright as the stick in the photograph.
     */
    private const val DISH_WIDTH = 0.4f
    private const val DISH_LEFT = 0.3f
    private const val DISH_BOTTOM = 0.97f
    private const val SOCKET_X = 0.501f
    private const val SOCKET_Y = 0.271f
    private const val LEAN_DEGREES = 2.8f

    /**
     * How far the dish stands out from the background, from 0 (not at all) to 1 (as far as the
     * text does), and how strongly it keeps the photograph's light and shade.
     */
    private const val DISH_TONE = 0.24f
    private const val DISH_SHADING = 0.26f

    /** The stick's length, as a part of the box. It is solid incense and burns to the holder. */
    private const val LENGTH = 0.67f

    /** Ash breaks off in pieces this long, in stick widths, leaving at least [ASH_KEPT] of one standing. */
    private const val ASH_PIECE = 4f
    private const val ASH_KEPT = 0.35f

    /** The ember is the text colour warmed this far towards the orange of a live coal. */
    private const val EMBER_WARMTH = 0.15f
    private val COAL = Color(0xFFE87A3E)

    /** How far the smoke climbs before it has thinned to nothing, as a part of the box. */
    private const val SMOKE_HEIGHT = 0.5f
    private const val SMOKE_POINTS = 40
    private const val RIBBONS = 3

    /** Each ribbon is drawn three times, wider and fainter, to soften its edges. */
    private val LAYER_WIDTHS = floatArrayOf(1f, 2.3f, 4.2f)
    private val LAYER_ALPHAS = floatArrayOf(0.20f, 0.10f, 0.05f)

    private const val TAU = 2 * PI.toFloat()
    private val path = Path()
    private val end = Path()

    /** Makes [end] an upright bar whose top corners are rounded by [radius]. */
    private fun roundedTop(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        end.rewind()
        end.addRoundRect(
            RoundRect(left, top, right, bottom, topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius))
        )
    }
    private val centres = FloatArray(SMOKE_POINTS + 1)
    private val halves = FloatArray(SMOKE_POINTS + 1)

    /**
     * Draws the stick with [fraction] of it burned away. [time] is in seconds and only moves the
     * smoke and the ember's glow; [smoke] is how strongly it is smoking, from 0 to 1.
     */
    fun DrawScope.drawIncense(
        strip: ImageBitmap, dish: ImageBitmap, fraction: Float, time: Float, smoke: Float, ink: Color, paper: Color,
    ) {
        val box = size.minDimension
        val f = fraction.coerceIn(0f, 1f)
        val width = (box * WIDTH).roundToInt().coerceAtLeast(3).toFloat()
        val dishWidth = (box * DISH_WIDTH).roundToInt()
        val dishHeight = (dishWidth.toFloat() * dish.height / dish.width).roundToInt()
        val dishLeft = (box * DISH_LEFT).roundToInt()
        val dishTop = (box * DISH_BOTTOM).roundToInt() - dishHeight
        val foot = Offset(dishLeft + dishWidth * SOCKET_X, dishTop + dishHeight * SOCKET_Y)
        val lean = LEAN_DEGREES * PI.toFloat() / 180f
        val length = box * LENGTH
        val burned = f * length
        val ashColor = lerp(paper, ink, 0.42f)

        drawImage(
            dish, dstOffset = IntOffset(dishLeft, dishTop), dstSize = IntSize(dishWidth, dishHeight),
            colorFilter = Beads.shaded(lerp(paper, ink, DISH_TONE), DISH_SHADING), filterQuality = FilterQuality.High,
        )

        // Ash on the burning end grows until a piece breaks off and drops into the bowl.
        val piece = width * ASH_PIECE
        val fallen = if (f <= 0f) 0 else floor(burned / piece - ASH_KEPT).toInt().coerceAtLeast(0)
        val standing = if (f >= 1f) 0f else burned - fallen * piece

        // The stick is drawn upright and leaned over about the point where it enters the dish.
        rotate(LEAN_DEGREES, foot) {
            val left = (foot.x - width / 2).roundToInt()
            val tipY = foot.y - length + burned

            // What is left of the stick: the strip's stick tile, repeated up from the bottom so
            // the grain stays put as the top burns away.
            val tile = (width * BODY_ROWS / strip.width).roundToInt()
            val stick = Beads.shaded(lerp(paper, ink, 0.6f), 0.35f)
            // A stick that has not been lit ends in a rounded tip, as it was rolled; once it is
            // burning it ends flat, at the ember.
            roundedTop(left.toFloat(), tipY, left + width, foot.y, if (f <= 0f) width / 2 else 0f)
            clipPath(end) {
                var y = foot.y.roundToInt()
                while (y > tipY) {
                    y -= tile
                    drawImage(
                        strip,
                        srcOffset = IntOffset(0, ASH_ROWS), srcSize = IntSize(strip.width, BODY_ROWS),
                        dstOffset = IntOffset(left, y), dstSize = IntSize(width.toInt(), tile),
                        colorFilter = stick, filterQuality = FilterQuality.High,
                    )
                }
            }

            if (f > 0f && f < 1f) {
                // Ash still standing on the burning end.
                if (standing >= 1f) {
                    // Its end is rounded where the last piece broke away.
                    roundedTop(left.toFloat(), tipY - standing, left + width, tipY, min(width / 2, standing))
                    clipPath(end) {
                        drawImage(
                            strip,
                            srcOffset = IntOffset(0, 0), srcSize = IntSize(strip.width, ASH_ROWS),
                            dstOffset = IntOffset(left, (tipY - standing).roundToInt()),
                            dstSize = IntSize(width.toInt(), standing.roundToInt()),
                            colorFilter = Beads.shaded(ashColor, 0.25f), filterQuality = FilterQuality.High,
                        )
                    }
                }
                // The ember: a bright band where the stick meets the ash, with a glow that breathes.
                val breath = 0.8f + 0.2f * sin(time * 2.1f) * sin(time * 1.3f + 1f)
                val glow = (0.45f + 0.55f * smoke) * breath
                val ember = Offset(foot.x, tipY)
                val warm = lerp(ink, COAL, EMBER_WARMTH)
                // Its light in the air around it: a wide faint halo and a closer, stronger one.
                for ((radius, strength) in listOf(width * 9f to 0.10f, width * 3.6f to 0.5f)) {
                    drawCircle(
                        Brush.radialGradient(
                            0f to warm.copy(alpha = strength * glow), 1f to warm.copy(alpha = 0f),
                            center = ember, radius = radius,
                        ),
                        radius = radius, center = ember,
                    )
                }
                drawRect(warm, Offset(left.toFloat(), tipY - width * 0.35f), Size(width, width * 0.7f))
            }
        }

        if (smoke > 0f && f > 0f && f < 1f) {
            val reach = length - burned + min(standing, width)
            drawSmoke(foot + Offset(sin(lean), -cos(lean)) * reach, time, smoke, ink, box, width)
        }
    }

    /**
     * Smoke rising from [origin]: ribbons that leave the ember as one thin column, then sway
     * and part. The sway is a few waves travelling upward, small near the ember and wide at the
     * top, so the smoke climbs steadily and never repeats exactly.
     */
    private fun DrawScope.drawSmoke(origin: Offset, t: Float, strength: Float, color: Color, box: Float, width: Float) {
        val height = box * SMOKE_HEIGHT
        fun wave(turns: Float, speed: Float, s: Float, phase: Float) = sin(TAU * (turns * s - speed * t) + phase)
        val fade = Brush.verticalGradient(
            0f to Color.Transparent, 0.04f to color, 0.4f to color.copy(alpha = 0.55f), 1f to Color.Transparent,
            startY = origin.y, endY = origin.y - height,
        )
        for (r in 0 until RIBBONS) {
            for (i in 0..SMOKE_POINTS) {
                val s = i / SMOKE_POINTS.toFloat()
                val together = 0.55f * wave(1.2f, 0.17f, s, 0f) + 0.35f * wave(2.6f, 0.29f, s, 1.7f) +
                    0.2f * wave(4.9f, 0.43f, s, 4.1f)
                val apart = 0.5f * wave(1.9f, 0.23f, s, r * 2.1f) + 0.3f * wave(3.7f, 0.37f, s, r * 4.4f)
                val lean = 0.5f * sin(t * 0.13f + 0.6f) * s
                centres[i] = origin.x + box * 0.11f * s.pow(1.4f) * (together + 1.2f * s * apart + lean)
                halves[i] = width * 0.3f + box * 0.012f * s * (1f + 0.5f * wave(2.2f, 0.2f, s, r.toFloat()))
            }
            for (layer in LAYER_WIDTHS.indices) {
                path.rewind()
                for (i in 0..SMOKE_POINTS) {
                    val x = centres[i] - halves[i] * LAYER_WIDTHS[layer]
                    val y = origin.y - height * i / SMOKE_POINTS
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                for (i in SMOKE_POINTS downTo 0) {
                    path.lineTo(centres[i] + halves[i] * LAYER_WIDTHS[layer], origin.y - height * i / SMOKE_POINTS)
                }
                path.close()
                drawPath(path, fade, alpha = LAYER_ALPHAS[layer] * strength)
            }
        }
    }
}
