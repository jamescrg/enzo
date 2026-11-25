package link.darmok.meditationtimer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The kaleidoscope: a session as a pattern drawing in to a point. A few pale shards drift and
 * turn, slowly, and mirrors set round the middle make of them a pattern with six sides that is
 * never twice the same. Before the session it sits where the other pictures do. When
 * the timer runs and the controls fade it moves to the middle of the screen and opens out to
 * fill it, and from then on it draws steadily in, the dark closing round it, until at the end it is a point and is gone.
 * Whenever the controls come back it shows in its place again, as much smaller as the session
 * is further on.
 *
 * The shards only move while the timer runs.
 */
object Kaleidoscope {
    /** The pattern's radius when it sits in its ring, as a part of the ring's box. */
    private const val REACH = 0.48f

    /** How many times the mirrors repeat the pattern round the middle; each repeat is itself mirrored. */
    private const val FOLDS = 6

    /** The whole pattern turns once in this many seconds. */
    private const val TURN = 300f

    /**
     * One shard: how many sides it has and how big it is beside the window, the slow waves
     * (rate and starting point) that carry it in and out, round, and about itself, and how
     * solid it is.
     */
    private class Shard(
        val sides: Int, val size: Float,
        val outRate: Float, val outFrom: Float,
        val roundRate: Float, val roundFrom: Float,
        val spinRate: Float, val spinFrom: Float,
        val solid: Float,
    )

    private val shards = Random(11).let { r ->
        fun rate(slow: Float, quick: Float) = 2 * PI.toFloat() / (slow + (quick - slow) * r.nextFloat()) * if (r.nextBoolean()) 1 else -1
        fun from() = 2 * PI.toFloat() * r.nextFloat()
        List(8) {
            Shard(
                sides = 3 + r.nextInt(3), size = 0.18f + 0.22f * r.nextFloat(),
                outRate = rate(120f, 60f), outFrom = from(),
                roundRate = rate(150f, 80f), roundFrom = from(),
                spinRate = rate(200f, 90f), spinFrom = from(),
                solid = 0.09f + 0.08f * r.nextFloat(),
            )
        }
    }

    private val shape = Path()
    private val window = Path()

    /**
     * Draws the pattern with [fraction] of the session gone. At rest it fills a circle about
     * [ring] in a box [box] wide; [spread] takes it from there (0) to the middle of everything
     * there is to draw on, and filling it (1). The shards take their colours in turn from [colors];
     * [time] is in seconds and only moves them.
     */
    fun DrawScope.drawKaleidoscope(
        ring: Offset, box: Float, spread: Float, fraction: Float, time: Float,
        colors: List<Color>, ink: Color,
    ) {
        val f = fraction.coerceIn(0f, 1f)
        val rest = box * REACH
        val wide = spread.coerceIn(0f, 1f)
        val centre = ring + (size.center - ring) * wide
        // Far enough to reach the furthest corner.
        val far = hypot(max(centre.x, size.width - centre.x), max(centre.y, size.height - centre.y))
        val reach = (rest + (far - rest) * wide) * (1 - f)
        if (reach < 0.5f) return
        val open = reach

        window.rewind()
        window.addOval(Rect(centre, open))
        clipPath(window) {
            rotate(time * 360f / TURN, centre) {
                shards.forEachIndexed { i, shard ->
                    // Where the shard is: out from the middle (evenly over the window's area),
                    // round from the mirror, and turned about itself.
                    val out = reach * sqrt(0.5f + 0.5f * sin(time * shard.outRate + shard.outFrom))
                    val round = time * shard.roundRate + shard.roundFrom
                    val spin = time * shard.spinRate + shard.spinFrom
                    val x = centre.x + out * cos(round)
                    val y = centre.y + out * sin(round)
                    // Near the middle the mirrors crowd its copies together, so it is smaller there.
                    val reachOf = reach * shard.size * (0.3f + 0.7f * out / reach)
                    shape.rewind()
                    for (corner in 0 until shard.sides) {
                        val a = spin + 2 * PI.toFloat() * corner / shard.sides
                        val px = x + reachOf * cos(a)
                        val py = y + reachOf * sin(a)
                        if (corner == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
                    }
                    shape.close()
                    val color = colors[i % colors.size].copy(alpha = shard.solid)
                    for (fold in 0 until FOLDS) {
                        rotate(360f * fold / FOLDS, centre) {
                            drawPath(shape, color)
                            scale(1f, -1f, centre) { drawPath(shape, color) }
                        }
                    }
                }
            }
        }
        drawCircle(ink.copy(alpha = 0.3f), open, centre, style = Stroke(1.dp.toPx()))
    }
}
