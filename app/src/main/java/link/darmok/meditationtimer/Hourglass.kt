package link.darmok.meditationtimer

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The hourglass: a session measured by sand running from the upper bulb into the lower. The
 * sand runs at a steady rate, so its level falls slowly while the bulb is wide and quickly as
 * it narrows, as it does in a real glass. The sand above does not sink evenly: it runs out
 * from the middle, so a small pit opens there and widens, its sides as steep as sand will lie,
 * until it reaches the glass and what is left lines the bulb like a funnel. A thread of grains
 * falls from it while the timer runs. Below, the sand lands in a cone. Sand will only
 * lie so steep, its angle of repose, but falling on one spot it piles a little steeper than
 * that, until every so often the cone gives way and slumps back. The countdown sits above the
 * scene, out of its way.
 *
 * The glass and the sand are real: a photograph by Rocco Stoppoloni on Unsplash
 * (https://unsplash.com/photos/8lvbTjLai34), from which extras/bake_hourglass.py takes the
 * empty glass, as the light it catches, and a tile of its sand. The sand is poured here. How
 * much of it is where is worked out from the shape of the glass, so what has left the top is
 * what has arrived at the bottom.
 *
 * The glass is held between a turned wooden base and a top to match, each a collar and a
 * wider plate, which are drawn here. The camera was about level with the top of the glass and
 * looked down on its foot, and the wood is seen from the same place.
 */
object Hourglass {
    /** The picture's height as a part of the box, and its shape. */
    private const val HEIGHT = 0.88f
    private const val SHAPE = 620f / 1243f

    /**
     * The base, as parts of the picture's height: where the middle of its top is, the radius
     * and thickness of its collar and of the foot under it, and how much shorter than wide a
     * circle lying flat looks from where the camera was.
     */
    private const val BASE = 1160f / 1243f
    private const val COLLAR = 286f / 1243f
    private const val COLLAR_THICK = 24f / 1243f
    private const val FOOT = 320f / 1243f
    private const val FOOT_THICK = 30f / 1243f
    private const val FLAT = 0.19f

    /** The top: where the glass ends under it, and how flat a circle looks up there, nearer the camera's own height. */
    private const val GLASS_TOP = 45f / 1243f
    private const val FLAT_TOP = 0.07f

    /** How far the wood reaches above the picture and below it. */
    private const val OVER = GLASS_TOP - COLLAR_THICK - FOOT_THICK - FOOT * FLAT_TOP
    private const val UNDER = BASE + COLLAR_THICK + FOOT_THICK + FOOT * FLAT

    /** The pit above has widened to the glass when this much of the sand has run through. */
    private const val PIT_WIDE = 0.55f

    /** How far the sand is from the wood's colour towards the text's, and the base towards the background's, on top and at the side. */
    private const val SAND_TONE = 0.3f
    private const val BASE_TOP_TONE = 0.3f
    private const val BASE_SIDE_TONE = 0.5f

    /** The size of the sand's grain beside the baked tile's. */
    private const val GRAIN_SIZE = 0.5f

    // The inside of the glass, as the bake script has it: heights down the picture and half
    // widths either side of its middle, all as parts of the picture's height.
    private const val MIDDLE = 316f / 620f
    private const val CEILING = 60f / 1243f
    private const val TOP_SHOULDER = 387f / 1243f
    private const val NECK = 616f / 1243f
    private const val BOTTOM_SHOULDER = 800f / 1243f
    private const val FLOOR_EDGE = 1150f / 1243f           // where the floor's near corners begin
    private const val FLOOR = 1190f / 1243f
    private const val WIDE = 238f / 1243f
    private const val NARROW = 13f / 1243f
    private const val FLOOR_FRONT = 128f / 1243f           // half the floor's near edge

    /** Where the sand stands in the upper bulb at the start. */
    private const val FULL = 205f / 1243f

    /**
     * How far the funnel above falls and the cone below rises from the side of the glass to its
     * middle: the cone at the angle of repose, about 30 degrees, and when it has piled as
     * steep as it can, about 37.
     */
    private const val FUNNEL = 125f / 1243f
    private const val REPOSE = 138f / 1243f
    private const val STEEPEST = 180f / 1243f

    /** The cone gives way about this often, in seconds, and takes this long to slump. */
    private const val COLLAPSE_EVERY = 9f
    private const val SLUMP = 0.5f

    /** How strongly the glass is drawn, and how strongly the sand keeps the grain of the photograph. */
    private const val GLASS = 0.9f
    private const val GRAIN = 0.3f

    /** The glass's half width, as a part of [WIDE], this far above or below the neck. */
    private fun above(height: Float) = narrowing(height / (NECK - TOP_SHOULDER))
    private fun below(depth: Float) = narrowing(depth / (BOTTOM_SHOULDER - NECK))
    private fun narrowing(part: Float) = (NARROW + (WIDE - NARROW) * part.coerceIn(0f, 1f)) / WIDE

    private const val SLICES = 120

    /**
     * The sand above, when the bottom of its pit is [base] above the neck, the pit's sides rise
     * [hollow] in the glass's half width, and the sand around the pit lies level [rim] higher.
     */
    private fun sandAbove(base: Float, hollow: Float, rim: Float): Float {
        var sum = 0f
        val step = (NECK - CEILING) / SLICES
        for (i in 0 until SLICES) {
            val h = (i + 0.5f) * step
            val r = above(h)
            sum += when {
                h <= base -> r * r
                h > base + rim -> 0f
                else -> { val edge = min(r, (h - base) / hollow); r * r - edge * edge }
            }
        }
        return sum * step
    }

    /** The sand below, when its cone is [peak] above the floor at the middle and [fall] lower at the sides. */
    private fun sandBelow(peak: Float, fall: Float): Float {
        var sum = 0f
        val step = (FLOOR - NECK) / SLICES
        for (i in 0 until SLICES) {
            val g = (i + 0.5f) * step                    // height above the floor
            if (g >= peak) break
            val edge = min(below(FLOOR - NECK - g), (peak - g) / fall)
            sum += edge * edge
        }
        return sum * step
    }

    /** Finds where [amount] of [low]..[high] gives the [wanted] sand, when more of it gives more sand. */
    private inline fun solve(wanted: Float, low: Float, high: Float, amount: (Float) -> Float): Float {
        var a = low
        var b = high
        repeat(22) {
            val mid = (a + b) / 2
            if (amount(mid) < wanted) a = mid else b = mid
        }
        return (a + b) / 2
    }

    private val all = sandAbove(NECK - FULL, FUNNEL, 0f)
    private var solvedFor = -1f
    private var solvedFall = 0f
    private var base = NECK - FULL
    private var rim = 0f
    private var peak = 0f

    /** A number from 0 to 1 for each whole number, with no pattern to it. */
    private fun chance(n: Int): Float = (sin(n * 12.9898f) * 43758.547f).let { it - floor(it) }

    /**
     * How steeply the cone is piled at [time], from 0 (at rest) to 1 (about to give way): it
     * builds steadily, then slumps. The slumps come once in every [COLLAPSE_EVERY] seconds, but
     * at a different moment in each.
     */
    private fun piled(time: Float): Float {
        val turn = floor(time / COLLAPSE_EVERY).toInt()
        fun slumpAt(n: Int) = (n + 0.25f + 0.7f * chance(n)) * COLLAPSE_EVERY
        val next = if (time < slumpAt(turn)) slumpAt(turn) else slumpAt(turn + 1)
        val last = if (time < slumpAt(turn)) slumpAt(turn - 1) else slumpAt(turn)
        val built = (time - last) / (next - last)
        val slumping = 1 - ((time - last) / SLUMP).coerceIn(0f, 1f)
        return max(built, slumping * slumping * (3 - 2 * slumping))
    }

    private val inside = Path()
    private val sand = Path()
    private var builtFor = Size.Unspecified
    private var grainOf: ImageBitmap? = null
    private var grain: ShaderBrush? = null

    /**
     * Draws the glass with [fraction] of its sand run through. [time] is in seconds and only
     * moves the falling grains; [flow] is how freely they are running, from 0 to 1.
     */
    fun DrawScope.drawHourglass(
        glass: ImageBitmap, sandTile: ImageBitmap,
        fraction: Float, time: Float, flow: Float, ink: Color, paper: Color, wood: Color,
    ) {
        val sandColor = lerp(wood, ink, SAND_TONE)
        val box = size.minDimension
        val height = (box * HEIGHT).roundToInt()
        val width = (height * SHAPE).roundToInt()
        val left = ((size.width - width) / 2).roundToInt()
        val top = ((size.height - (UNDER - OVER) * height) / 2 - OVER * height).roundToInt()
        val middle = left + width * MIDDLE
        fun y(part: Float) = top + part * height
        fun x(half: Float, side: Int) = middle + side * half * height

        if (builtFor != size) {
            // The inside of the glass, down one side and back up the other.
            inside.rewind()
            inside.moveTo(x(WIDE, -1), y(CEILING))
            inside.lineTo(x(WIDE, -1), y(TOP_SHOULDER))
            inside.lineTo(x(NARROW, -1), y(NECK))
            inside.lineTo(x(WIDE, -1), y(BOTTOM_SHOULDER))
            inside.lineTo(x(WIDE, -1), y(FLOOR_EDGE))
            inside.lineTo(x(FLOOR_FRONT, -1), y(FLOOR))
            inside.lineTo(x(FLOOR_FRONT, 1), y(FLOOR))
            inside.lineTo(x(WIDE, 1), y(FLOOR_EDGE))
            inside.lineTo(x(WIDE, 1), y(BOTTOM_SHOULDER))
            inside.lineTo(x(NARROW, 1), y(NECK))
            inside.lineTo(x(WIDE, 1), y(TOP_SHOULDER))
            inside.lineTo(x(WIDE, 1), y(CEILING))
            inside.close()
            builtFor = size
        }
        if (grainOf !== sandTile) {
            val shader = ImageShader(sandTile, TileMode.Repeated, TileMode.Repeated)
            shader.setLocalMatrix(Matrix().apply { setScale(GRAIN_SIZE, GRAIN_SIZE) })
            grain = ShaderBrush(shader)
            grainOf = sandTile
        }
        val f = fraction.coerceIn(0f, 1f)
        val fall = REPOSE + (STEEPEST - REPOSE) * piled(time)
        if (f != solvedFor || fall != solvedFall) {
            // The pit opens as soon as the sand starts to run and deepens, and so widens, as
            // it goes; the levels follow from how much sand is left above and how much has
            // come down.
            rim = FUNNEL * (f / PIT_WIDE).coerceAtMost(1f)
            base = solve((1 - f) * all, -FUNNEL - 0.01f, NECK - FULL + 0.01f) { sandAbove(it, FUNNEL, rim) }
            peak = solve(f * all, 0f, FLOOR - NECK) { sandBelow(it, fall) }
            solvedFor = f
            solvedFall = fall
        }
        // The bottom of the pit and the point of the cone, rounded off a little.
        fun slope(across: Float) = sqrt(across * across + 0.004f) - 0.063f
        val grains = Beads.shaded(sandColor, GRAIN)
        val brush = grain!!

        // The base, from the bottom up: each part is a disc seen from a little above, its side
        // darker towards the edges where it turns away, and its top ringed like the end of a log.
        val topColor = lerp(wood, paper, BASE_TOP_TONE)
        val sideColor = lerp(wood, paper, BASE_SIDE_TONE)
        val edgeColor = lerp(wood, paper, 0.75f)
        val line = 1.dp.toPx()
        fun disc(topAt: Float, radius: Float, thick: Float, flat: Float) {
            val rx = radius * height
            val ry = rx * flat
            val cy = y(topAt)
            val side = Brush.horizontalGradient(
                0f to edgeColor, 0.25f to sideColor, 0.6f to sideColor, 1f to edgeColor,
                startX = middle - rx, endX = middle + rx,
            )
            drawOval(side, Offset(middle - rx, cy + thick * height - ry), Size(rx * 2, ry * 2))
            drawRect(side, Offset(middle - rx, cy), Size(rx * 2, thick * height))
            drawOval(topColor, Offset(middle - rx, cy - ry), Size(rx * 2, ry * 2))
            for (ring in floatArrayOf(0.86f, 0.66f, 0.42f)) {
                drawOval(
                    sideColor.copy(alpha = 0.5f), Offset(middle - rx * ring, cy - ry * ring),
                    Size(rx * ring * 2, ry * ring * 2), style = Stroke(line),
                )
            }
            drawOval(lerp(topColor, ink, 0.2f), Offset(middle - rx, cy - ry), Size(rx * 2, ry * 2), style = Stroke(line))
        }
        disc(BASE + COLLAR_THICK, FOOT, FOOT_THICK, FLAT)
        disc(BASE, COLLAR, COLLAR_THICK, FLAT)

        clipPath(inside) {
            if (f < 1f) {
                // The sand above: level, but for the pit in the middle.
                sand.rewind()
                sand.moveTo(x(WIDE, -1), y(NECK))
                for (i in -24..24) {
                    val across = i / 24f
                    sand.lineTo(x(WIDE * across, 1), y(NECK - base - min(rim, FUNNEL * slope(across))))
                }
                sand.lineTo(x(WIDE, 1), y(NECK))
                sand.close()
                clipRect(bottom = y(NECK)) { drawPath(sand, brush, colorFilter = grains) }
            }
            val heapTop = y(FLOOR - peak)
            if (flow > 0f && f < 1f) {
                // The grains on their way down: a hair-thin thread, and slightly denser runs
                // of sand going down it. They are long and far apart beside how far they move
                // between one picture and the next, or they would seem to stand still or climb.
                val width = (box * 0.0022f).coerceAtLeast(1.5f)
                val from = Offset(middle, y(NECK) - width)
                val to = Offset(middle, heapTop + width * 2)
                drawLine(sandColor.copy(alpha = 0.7f * flow.coerceAtMost(1f)), from, to, strokeWidth = width)
                val run = box * 0.03f
                drawLine(
                    sandColor.copy(alpha = 0.5f * flow.coerceAtMost(1f)), from, to, strokeWidth = width,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(run, run * 1.6f), run * 2.6f - (time * box * 0.3f) % (run * 2.6f)),
                )
            }
            if (f > 0f) {
                // The sand below: everything under a cone that is highest in the middle.
                sand.rewind()
                sand.moveTo(x(WIDE, -1), y(FLOOR))
                for (i in -24..24) {
                    val across = i / 24f
                    sand.lineTo(x(WIDE * across, 1), y(FLOOR - peak + fall * slope(across)))
                }
                sand.lineTo(x(WIDE, 1), y(FLOOR))
                sand.close()
                drawPath(sand, brush, colorFilter = grains)
            }
        }
        // The glass over the sand: the light it catches, in the colour of the text.
        drawImage(
            glass, dstOffset = IntOffset(left, top), dstSize = IntSize(width, height),
            alpha = GLASS, colorFilter = ColorFilter.tint(ink), filterQuality = FilterQuality.High,
        )
        // The top, over the end of the glass: its collar, and the plate on that.
        disc(GLASS_TOP - COLLAR_THICK, COLLAR, COLLAR_THICK, FLAT_TOP)
        disc(GLASS_TOP - COLLAR_THICK - FOOT_THICK, FOOT, FOOT_THICK, FLAT_TOP)
    }
}
