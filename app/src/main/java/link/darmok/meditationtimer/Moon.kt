package link.darmok.meditationtimer

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * The moon: a session measured as the moon measures a month, from the first thin crescent of
 * the new moon, whose sighting begins each month of the Islamic calendar, to the full moon as
 * the session ends. The rest of the disc is there all along, barely lit.
 *
 * The moon is real: the photograph "Moon nearside LRO.jpg" by NASA/GSFC/Arizona State
 * University, public domain, from Wikimedia Commons
 * (https://commons.wikimedia.org/wiki/File:Moon_nearside_LRO.jpg), baked into a grey disc
 * (res/drawable-nodpi/moon.png) by extras/bake_moon.py. It is a mosaic lit evenly all over, so
 * the light and the shadow of the phase are put on here.
 */
object Moon {
    /** The disc's width, as a part of the box. */
    private const val WIDTH = 0.8f

    /** How far the unlit disc stands out from the background, and how strongly each side keeps the photograph's detail. */
    private const val DARK_TONE = 0.1f
    private const val DARK_SHADING = 0.1f
    private const val LIT_SHADING = 0.6f

    /**
     * The phase at the start of the session and how much of the month the session covers, as
     * parts of the way from new moon to full: it starts as a crescent and is full just before
     * the end.
     */
    private const val FIRST = 0.16f
    private const val SPAN = 0.78f

    /**
     * The nimbus: how strong it is at the limb of a full moon, how far beyond the limb it
     * reaches, and how far towards the lit side it sits at new moon, as parts of the radius.
     */
    private const val NIMBUS = 0.3f
    private const val NIMBUS_REACH = 0.45f
    private const val NIMBUS_ASIDE = 0.3f

    /** The softness of the edge of the light, as a part of the disc's radius. */
    private const val SOFT = 0.05f

    private val layer = Paint()
    private val light = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    private var softFor = 0f
    private val path = Path()

    /** Draws the moon [fraction] of the way from crescent to full, its lit side in [lit] and its nimbus in [glow]. */
    fun DrawScope.drawMoon(moon: ImageBitmap, fraction: Float, lit: Color, glow: Color, ink: Color, paper: Color) {
        val width = (size.minDimension * WIDTH).roundToInt()
        val topLeft = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - width) / 2).roundToInt())
        val centre = Offset(topLeft.x + width / 2f, topLeft.y + width / 2f)
        val radius = width / 2f
        val dstSize = IntSize(width, width)
        val phase = PI.toFloat() * (FIRST + SPAN * fraction.coerceIn(0f, 1f))

        // The nimbus: the moon's light in the air around it. It comes from the lit side, so it
        // is faint and lies off to that side around a crescent, and grows brighter, wider and
        // more evenly round as the moon fills. The disc is drawn over its middle.
        val full = (1f - cos(phase)) / 2f
        val aside = radius * NIMBUS_ASIDE * (1f - full)
        val reachOut = radius * (1f + NIMBUS_REACH * (0.6f + 0.4f * full))
        val strength = NIMBUS * (0.35f + 0.65f * full)
        val limb = (radius - aside) / reachOut
        val heart = Offset(centre.x + aside, centre.y)
        drawCircle(
            Brush.radialGradient(
                0f to glow.copy(alpha = strength), limb to glow.copy(alpha = strength),
                limb + (1f - limb) * 0.4f to glow.copy(alpha = strength * 0.3f), 1f to glow.copy(alpha = 0f),
                center = heart, radius = reachOut,
            ),
            radius = reachOut, center = heart,
        )

        drawImage(
            moon, dstOffset = topLeft, dstSize = dstSize,
            colorFilter = Beads.shaded(lerp(paper, ink, DARK_TONE), DARK_SHADING), filterQuality = FilterQuality.High,
        )

        // The lit part lies between the right-hand limb and the terminator, which is half an
        // ellipse: bulging right while the moon is a crescent, flat at half moon, left after.
        // Both are taken a little outside the disc, so the soft edge stays off the limb.
        val soft = radius * SOFT
        val reach = radius + soft * 2
        val bulge = reach * cos(phase)
        path.rewind()
        path.moveTo(centre.x, centre.y - reach)
        path.arcTo(Rect(centre, reach), -90f, 180f, false)
        val half = abs(bulge).coerceAtLeast(0.5f)
        path.arcTo(
            Rect(centre.x - half, centre.y - reach, centre.x + half, centre.y + reach),
            90f, if (bulge > 0f) -180f else 180f, false,
        )
        path.close()
        if (softFor != soft) {
            light.maskFilter = BlurMaskFilter(soft, BlurMaskFilter.Blur.NORMAL)
            softFor = soft
        }

        val bounds = Rect(topLeft.x.toFloat(), topLeft.y.toFloat(), (topLeft.x + width).toFloat(), (topLeft.y + width).toFloat())
        clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
            val canvas = drawContext.canvas
            canvas.saveLayer(bounds, layer)
            drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), light) }
            drawImage(
                moon, dstOffset = topLeft, dstSize = dstSize,
                colorFilter = Beads.shaded(lit, LIT_SHADING), filterQuality = FilterQuality.High,
                blendMode = BlendMode.SrcIn,
            )
            canvas.restore()
        }
    }
}
