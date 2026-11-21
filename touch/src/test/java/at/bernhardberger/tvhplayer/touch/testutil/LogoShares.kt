package at.bernhardberger.tvhplayer.touch.testutil

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.IntRect
import kotlin.math.abs
import org.junit.Assert.assertTrue

/** The color of [markedLogos], found nowhere else in the app's drawing. */
internal val LogoMark = Color(0xFFFF00FF)

/** A square logo and a wide one as opaque [LogoMark] blocks, so the drawn logo can be told from its ground. */
internal fun markedLogos(): List<BitmapPainter> = listOf(ImageBitmap(100, 100), ImageBitmap(400, 100)).map {
    Canvas(it).drawRect(Rect(0f, 0f, it.width.toFloat(), it.height.toFloat()), Paint().apply { color = LogoMark })
    BitmapPainter(it)
}

/**
 * The logo rule of the touch design (section 13): the [LogoMark] logo drawn in [slot] of this image stays within
 * 76% of the slot's width and 62% of its height, reaches that box on one side ("contain") and is centered.
 */
internal fun PixelMap.assertLogoFitsItsShare(name: String, slot: IntRect = IntRect(0, 0, width, height)) {
    val drawn = (slot.left until slot.right).flatMap { x -> (slot.top until slot.bottom).map { y -> x to y } }
        .filter { (x, y) -> this[x, y] == LogoMark }
    assertTrue("$name: no logo drawn in its slot", drawn.isNotEmpty())
    val left = drawn.minOf { it.first } - slot.left
    val right = slot.right - 1 - drawn.maxOf { it.first }
    val top = drawn.minOf { it.second } - slot.top
    val bottom = slot.bottom - 1 - drawn.maxOf { it.second }
    val width = slot.width - left - right
    val height = slot.height - top - bottom
    assertTrue("$name: width $width of ${slot.width}", width <= slot.width * WIDTH_SHARE + 1)
    assertTrue("$name: height $height of ${slot.height}", height <= slot.height * HEIGHT_SHARE + 1)
    assertTrue(
        "$name: $width×$height does not fill its box on either side",
        width >= slot.width * WIDTH_SHARE - 2 || height >= slot.height * HEIGHT_SHARE - 2,
    )
    assertTrue("$name: off center horizontally", abs(left - right) <= 1)
    assertTrue("$name: off center vertically", abs(top - bottom) <= 1)
}

private const val WIDTH_SHARE = 0.76f
private const val HEIGHT_SHARE = 0.62f
