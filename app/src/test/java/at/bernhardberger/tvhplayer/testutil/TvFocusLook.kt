package at.bernhardberger.tvhplayer.testutil

import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Summed brightness of [view] drawn now inside [area] (mdpi: dp are pixels). */
internal fun ComposeContentTestRule.drawnBrightness(view: View, area: Rect): Float = runOnIdle {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    var sum = 0f
    for (x in area.left.toInt() until area.right.toInt().coerceAtMost(bitmap.width)) {
        for (y in area.top.toInt() until area.bottom.toInt().coerceAtMost(bitmap.height)) {
            val pixel = bitmap.getPixel(x, y)
            sum += android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) +
                android.graphics.Color.blue(pixel)
        }
    }
    bitmap.recycle()
    sum
}

/** A TV is not in touch mode, the test default: only then does a view hand focus over as it attaches. */
internal fun AndroidComposeTestRule<*, ComponentActivity>.leaveTouchModeBeforeComposing() {
    runOnUiThread {
        ReflectionHelpers.callInstanceMethod<Boolean>(activity.window.decorView.parent, "ensureTouchModeLocally",
            ClassParameter.from(Boolean::class.javaPrimitiveType, false))
    }
}

/**
 * Not every control's focused look has a fixed value to expect: its own rendering once focus has
 * left and returned is the reference, and leaving must change it.
 */
internal fun SemanticsNodeInteraction.assertLooksAsWhenRefocused(name: String, away: () -> Unit, back: () -> Unit) {
    val arrival = assertIsFocused().captureToImage().toPixelMap()
    away()
    val left = captureToImage().toPixelMap()
    back()
    val returned = assertIsFocused().captureToImage().toPixelMap()
    assertTrue("leaving $name changes how it looks", pixelDifference(left, returned) > 0.01f)
    val changed = pixelDifference(arrival, returned)
    assertTrue("$name on arrival looks as after refocus, but ${changed * 100}% of its pixels differ", changed < 0.005f)
}

/** A focused control may change size, as an on-air Guide cell does when focus clips it: all of it differs then. */
private fun pixelDifference(a: PixelMap, b: PixelMap): Float {
    if (a.width != b.width || a.height != b.height) return 1f
    var changed = 0
    for (x in 0 until a.width) for (y in 0 until a.height) {
        val p = a[x, y]; val q = b[x, y]
        if (abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue) > 0.06f) changed++
    }
    return changed.toFloat() / (a.width * a.height)
}
