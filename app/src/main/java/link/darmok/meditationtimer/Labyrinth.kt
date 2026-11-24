package link.darmok.meditationtimer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin

/**
 * The labyrinth: a session walked as the single winding path of the labyrinth laid in the floor
 * of Chartres cathedral around 1200. There are no choices on it; the path goes in at the foot,
 * wanders through all eleven rings in each quarter, and arrives at the centre as the session
 * ends. The path itself is drawn, faintly, and inked as far as the session has come.
 *
 * The route was read from the diagram "Labyrinth 1 (from Nordisk familjebok).svg" (public
 * domain, Wikimedia Commons) and is drawn here from that list of turns alone.
 */
object Labyrinth {
    private const val RINGS = 11

    /** The clear space at the centre and the path's thickness, as parts of the half-width and of a ring. */
    private const val CENTRE = 0.13f
    private const val THICKNESS = 0.4f

    /**
     * The route: each step is a ring (1 outermost) and the quarter it crosses (bottom left, top
     * left, top right, bottom right), in the order they are walked.
     */
    private const val ROUTE =
        "5BL 6BL 11BL 11TL 10TL 10BL 9BL 8BL 7BL 7TL 8TL 9TL 9TR 10TR 11TR 11BR 10BR 9BR 8BR 8TR 7TR 6TR " +
            "6TL 5TL 4TL 4BL 3BL 2BL 1BL 1TL 2TL 3TL 3TR 4TR 5TR 5BR 4BR 3BR 2BR 2TR 1TR 1BR 6BR 7BR"

    // The four arms, numbered round from the entrance, and each quarter's two arms and the
    // angles between them, measured from straight down and growing to the right.
    private const val FOOT = 0
    private const val LEFT = 1
    private const val TOP = 2
    private const val RIGHT = 3

    private class Quarter(val low: Float, val high: Float, val lowArm: Int, val highArm: Int)

    private val quarters = mapOf(
        "BL" to Quarter(-90f, 0f, LEFT, FOOT),
        "TL" to Quarter(-180f, -90f, TOP, LEFT),
        "TR" to Quarter(90f, 180f, RIGHT, TOP),
        "BR" to Quarter(0f, 90f, FOOT, RIGHT),
    )
    private val steps = ROUTE.split(' ').map { it.dropLast(2).toInt() to quarters.getValue(it.takeLast(2)) }

    private val path = Path()
    private val inked = Path()
    private val measure = PathMeasure()
    private var builtFor = Size.Unspecified

    /** Lays the route out as one line from the edge of the box to its centre. */
    private fun build(size: Size) {
        val half = size.minDimension / 2
        val centre = Offset(size.width / 2, size.height / 2)
        val ring = half * (1 - CENTRE) / (RINGS + 0.5f)             // the distance between rings
        fun radius(n: Int) = half - ring * (n - 0.25f)
        path.rewind()
        path.moveTo(centre.x - ring / 2, centre.y + half)
        var arm = FOOT
        steps.forEachIndexed { i, (n, quarter) ->
            val r = radius(n)
            val leaving = if (quarter.lowArm == arm) quarter.highArm else quarter.lowArm
            // Where the path keeps to its ring across an arm, the arc runs right up to it. Where
            // it turns, the arc stops short: half a ring short of a side or top arm, and at the
            // foot, half a ring for the two straight lanes and a ring more for the hairpins
            // beside them.
            fun gap(at: Int, other: Int?): Float {
                if (other == n) return 0f
                return if (at == FOOT && other != null && abs(other - n) == 1) ring * 1.5f else ring * 0.5f
            }
            fun angle(at: Int, other: Int?): Float {
                val shy = asin(gap(at, other) / r) * 180f / PI.toFloat()
                return if (at == quarter.highArm) quarter.high - shy else quarter.low + shy
            }
            val from = angle(arm, steps.getOrNull(i - 1)?.first)
            val to = angle(leaving, steps.getOrNull(i + 1)?.first)
            path.arcTo(
                Rect(centre.x - r, centre.y - r, centre.x + r, centre.y + r),
                startAngleDegrees = 90f - from, sweepAngleDegrees = from - to, forceMoveTo = false,
            )
            arm = leaving
        }
        // Up the last lane and into the middle.
        path.lineTo(centre.x + ring / 2, centre.y + ring * 1.5f)
        path.quadraticTo(centre.x + ring / 2, centre.y, centre.x, centre.y)
        measure.setPath(path, false)
        builtFor = size
    }

    /** Draws the path in [track], and in [ink] as far as [fraction] of the way to the centre. */
    fun DrawScope.drawLabyrinth(fraction: Float, ink: Color, track: Color) {
        if (builtFor != size) build(size)
        val ring = size.minDimension / 2 * (1 - CENTRE) / (RINGS + 0.5f)
        val stroke = Stroke(ring * THICKNESS, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path, track, style = stroke)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            inked.rewind()
            measure.getSegment(0f, measure.length * f, inked, true)
            drawPath(inked, ink, style = stroke)
        }
    }
}
