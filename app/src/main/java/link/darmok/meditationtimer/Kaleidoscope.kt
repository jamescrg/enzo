package link.darmok.meditationtimer

import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The kaleidoscope: not a measure of the session, only something to rest the eyes on while it
 * goes. Pale shards of cut glass, triangles, squares and pentagons, drift and turn, slowly,
 * and mirrors set round the middle make of them a pattern with six sides that is never twice
 * the same.
 *
 * Before the session it shows in a circle where the other pictures are. When the timer runs
 * and the controls fade, the circle moves to the middle of the screen and widens until the
 * pattern covers it all. Nothing is made bigger: the pattern goes on outwards as finely as it
 * began, and the circle only uncovers more of it.
 *
 * The shards only move while the timer runs.
 */
object Kaleidoscope {
    /** The circle's radius at rest, as a part of its box. */
    private const val REACH = 0.48f

    /** How many times the mirrors repeat the pattern round the middle; each repeat is itself mirrored. */
    private const val FOLDS = 6

    /** The whole pattern turns once in this many seconds. */
    private const val TURN = 300f

    /** How many shards lie within the circle at rest (they lie as thickly further out), and the most there can be. */
    private const val AT_REST = 8
    private const val MOST = 130

    /** How far a shard wanders in and out from its place, in circles at rest. */
    private const val WANDER = 0.3f

    /**
     * One shard: how many sides it has, how big it is and how far out its place is (both in
     * circles at rest), the slow waves (rate and starting point) that carry it in and out,
     * round, and about itself, and how solid it is.
     */
    private class Shard(
        val sides: Int, val size: Float, val place: Float,
        val outRate: Float, val outFrom: Float,
        val roundRate: Float, val roundFrom: Float,
        val spinRate: Float, val spinFrom: Float,
        val solid: Float,
    )

    private val shards = Random(11).let { r ->
        fun rate(slow: Float, quick: Float) = 2 * PI.toFloat() / (slow + (quick - slow) * r.nextFloat()) * if (r.nextBoolean()) 1 else -1
        fun from() = 2 * PI.toFloat() * r.nextFloat()
        List(MOST) { i ->
            // Each takes its turn further out, so that they lie evenly over the whole pattern;
            // those further out go round more slowly, to cover the same ground.
            val place = sqrt((i + r.nextFloat()) / AT_REST)
            Shard(
                sides = 3 + r.nextInt(3), size = 0.18f + 0.22f * r.nextFloat(), place = place,
                outRate = rate(120f, 60f), outFrom = from(),
                roundRate = rate(150f, 80f) / max(1f, place), roundFrom = from(),
                spinRate = rate(200f, 90f), spinFrom = from(),
                solid = 0.09f + 0.08f * r.nextFloat(),
            )
        }
    }

    /** Half of one of the pattern's repeats: the wedge between two mirrors, which is all that has to be worked out. */
    private const val WEDGE = PI.toFloat() / FOLDS

    /** The wedge is drawn a little wider than it is, by this angle, so that the copies of it overlap and no gap shows between them. */
    private const val SEAM = 0.003f

    /** Room round the edge of the picture of the wedge, in pixels. */
    private const val MARGIN = 8f

    /**
     * Drawing each shard as a shape of its own is slow, there are so many. Where the phone can
     * take them (Android 10 and later), they are all handed over at once as one list of
     * triangles: the corners of each ([points], two numbers a corner) and its colour ([tints]).
     */
    private val batched = Build.VERSION.SDK_INT >= 29
    private var points = FloatArray(1 shl 16)
    private var tints = IntArray(1 shl 15)
    private var filled = 0
    private val plain = Paint()

    private fun triangle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, argb: Int) {
        if (filled + 3 > tints.size) {
            points = points.copyOf(points.size * 2)
            tints = tints.copyOf(tints.size * 2)
        }
        val at = filled * 2
        points[at] = ax
        points[at + 1] = ay
        points[at + 2] = bx
        points[at + 3] = by
        points[at + 4] = cx
        points[at + 5] = cy
        tints[filled] = argb
        tints[filled + 1] = argb
        tints[filled + 2] = argb
        filled += 3
    }

    private val shape = Path()
    private val cornerX = FloatArray(6)
    private val cornerY = FloatArray(6)
    private val window = Path()
    private val sector = Path()

    /**
     * Draws the pattern. At rest it shows in a circle about [ring] in a box [box] wide;
     * [spread] takes it from there (0) to the middle of everything there is to draw on, and
     * covering it (1). The shards take their colours in turn from [colors], on [paper]; [time]
     * is in seconds and only moves them.
     *
     * Every shard is seen twelve times over, but only the wedge between two mirrors is drawn
     * shard by shard, into [wedge]; the rest is that picture, turned and turned over, as it is
     * in a kaleidoscope.
     */
    fun DrawScope.drawKaleidoscope(
        wedge: GraphicsLayer, ring: Offset, box: Float, spread: Float, time: Float,
        colors: List<Color>, ink: Color, paper: Color,
    ) {
        val rest = box * REACH
        val wide = spread.coerceIn(0f, 1f)
        val centre = ring + (size.center - ring) * wide
        // Far enough to reach the furthest corner, from here and from wherever else it may be.
        fun farFrom(from: Offset) = hypot(max(from.x, size.width - from.x), max(from.y, size.height - from.y))
        val open = rest + (farFrom(centre) - rest) * wide
        val most = max(farFrom(ring), farFrom(size.center))
        val low = -SEAM
        val high = WEDGE + SEAM

        wedge.compositingStrategy = CompositingStrategy.Offscreen
        wedge.record(IntSize(ceil(most + 2 * MARGIN).toInt(), ceil(most * sin(high) + 2 * MARGIN).toInt())) {
            drawRect(paper)
            filled = 0
            for (i in 0 until MOST) {
                val shard = shards[i]
                val out = rest * abs(shard.place + WANDER * sin(time * shard.outRate + shard.outFrom))
                // Near the middle the mirrors crowd its copies together, so it is smaller there.
                val reach = rest * shard.size * (0.3f + 0.7f * min(1f, out / rest))
                if (out - reach > open) continue
                val about = time * shard.roundRate + shard.roundFrom
                val spin = time * shard.spinRate + shard.spinFrom
                val color = colors[i % colors.size].copy(alpha = shard.solid)
                val n = shard.sides
                for (copy in 0 until 2 * FOLDS) {
                    // The copies: turned to each of the repeats, as it is and turned over.
                    val side = if (copy % 2 == 0) 1 else -1
                    val turned = (copy / 2) * 2 * WEDGE
                    var at = side * about + turned
                    at -= 2 * PI.toFloat() * round(at / (2 * PI.toFloat()))
                    // Only those that reach into the wedge are wanted.
                    val gap = if (at < low) low - at else if (at > high) at - high else 0f
                    val away = if (gap < PI.toFloat() / 2) out * sin(gap) else out
                    if (away > reach + 1f) continue
                    val x = MARGIN + out * cos(at)
                    val y = MARGIN + out * sin(at)
                    for (corner in 0 until n) {
                        val a = side * (spin + 2 * PI.toFloat() * corner / n) + turned
                        cornerX[corner] = x + reach * cos(a)
                        cornerY[corner] = y + reach * sin(a)
                    }
                    if (batched) {
                        // As a fan of triangles about the shard's middle.
                        val argb = color.toArgb()
                        for (corner in 0 until n) {
                            val next = (corner + 1) % n
                            triangle(x, y, cornerX[corner], cornerY[corner], cornerX[next], cornerY[next], argb)
                        }
                    } else {
                        shape.rewind()
                        shape.moveTo(cornerX[0], cornerY[0])
                        for (corner in 1 until n) shape.lineTo(cornerX[corner], cornerY[corner])
                        shape.close()
                        drawPath(shape, color)
                    }
                }
            }
            if (batched && filled > 0) {
                drawIntoCanvas {
                    it.nativeCanvas.drawVertices(
                        android.graphics.Canvas.VertexMode.TRIANGLES, filled * 2, points, 0, null, 0, tints, 0, null, 0, 0, plain,
                    )
                }
            }
        }

        // The wedge as it lies on the screen, reaching past the circle.
        val long = open * 1.1f
        sector.rewind()
        sector.moveTo(centre.x, centre.y)
        sector.lineTo(centre.x + long * cos(low), centre.y + long * sin(low))
        sector.lineTo(centre.x + long * cos(high), centre.y + long * sin(high))
        sector.close()
        window.rewind()
        window.addOval(Rect(centre, open))
        clipPath(window) {
            for (fold in 0 until FOLDS) {
                rotate(time * 360f / TURN + 360f * fold / FOLDS, centre) {
                    clipPath(sector) { translate(centre.x - MARGIN, centre.y - MARGIN) { drawLayer(wedge) } }
                    scale(1f, -1f, centre) {
                        clipPath(sector) { translate(centre.x - MARGIN, centre.y - MARGIN) { drawLayer(wedge) } }
                    }
                }
            }
        }
        if (wide < 1f) drawCircle(ink.copy(alpha = 0.3f * (1 - wide)), open, centre, style = Stroke(1.dp.toPx()))
    }
}
