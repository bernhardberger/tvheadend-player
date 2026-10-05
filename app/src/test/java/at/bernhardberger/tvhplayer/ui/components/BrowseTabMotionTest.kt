package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Real native tabs and production page host; label movement is not indicator movement. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalFoundationApi::class)
class BrowseTabMotionTest {
    @get:Rule val compose = createComposeRule()
    private val selected = mutableIntStateOf(0)
    private lateinit var motion: TabContentMotion
    private lateinit var view: View
    private val visits = mutableListOf<Visit>()
    private val labels = mutableMapOf<Int, LayoutCoordinates>()
    private lateinit var header: LayoutCoordinates
    private var rtl = false
    private var clicks = 0
    private var pillColor = 0
    private var focusScrollDistance = 0f

    @Test fun interruptedReturnSlidesInsteadOfSnappingLtr() = returnMotion(false)
    @Test fun interruptedReturnSlidesInsteadOfSnappingRtl() = returnMotion(true)

    @Test fun settledScheduleToArchiveAndBackSlide() {
        show()
        val origin = pageX()
        key(Key.DirectionRight)
        advance(64)
        assertTrue(pageX() > origin + 1f)
        assertTrue(peak(visits.last().color) in .02f.. .98f)
        advance(1000)
        assertEquals(origin, pageX(), .5f)
        key(Key.DirectionLeft)
        advance(64)
        assertTrue(pageX() < origin - 1f)
        assertTrue(peak(visits.last().color) in .02f.. .98f)
        advance(1000)
        assertEquals(origin, pageX(), .5f)
    }

    @Test fun unrenderedRoundTripRemainsStationary() {
        show()
        val origin = pageX()
        compose.runOnIdle { select(1); select(0) }
        advance(64)
        assertEquals(origin, pageX(), .5f)
        assertEquals(1, visits.size)
    }

    @Test fun overflowReversalLtr() = overflow(false, 1f)
    @Test fun overflowReversalRtl() = overflow(true, 1f)
    @Test fun overflowReversalLargeTextLtr() = overflow(false, 1.5f)
    @Test fun overflowReversalLargeTextRtl() = overflow(true, 1.5f)

    private fun returnMotion(rightToLeft: Boolean) {
        show(rightToLeft)
        val origin = pageX()
        val headerX = header.localToRoot(Offset.Zero).x
        val original = visits.single()
        key(if (rtl) Key.DirectionLeft else Key.DirectionRight)
        advance(64)
        println("outbound rtl=$rtl x=${pageX()} alpha=${peak(visits.last().color)} visits=${visits.size}")
        key(if (rtl) Key.DirectionRight else Key.DirectionLeft)
        advance(32)
        val first = pageX()
        val alpha = peak(visits.last().color)
        println("return rtl=$rtl origin=$origin first=$first alpha=$alpha")
        assertTrue("return visit must slide in the latest logical direction", (first - origin) * (if (rtl) -1f else 1f) < -1f)
        assertTrue("return visit must fade, not snap opaque", alpha in .02f.. .98f)
        advance(48)
        assertTrue(abs(pageX() - origin) < abs(first - origin))
        assertTrue(peak(visits.last().color) > alpha)
        assertEquals(headerX, header.localToRoot(Offset.Zero).x, .01f)
        assertFalse(original.owner.isCurrent)
        assertEquals(1, visits.count { it.owner.isCurrent })
        advance(1000)
        assertEquals(origin, pageX(), .5f)
    }

    private fun overflow(rightToLeft: Boolean, fontScale: Float) {
        show(rightToLeft, fontScale, overflow = true)
        val forward = if (rtl) Key.DirectionLeft else Key.DirectionRight
        val backward = if (rtl) Key.DirectionRight else Key.DirectionLeft
        repeat(5) { key(forward); advance(1000) }
        val before = labelX(5)
        key(backward)
        val samples = mutableListOf(labelX(5))
        val pills = mutableListOf(pillX() - labelX(5))
        val pages = mutableListOf(pageX())
        repeat(12) { frame ->
            advance(16)
            samples += labelX(5)
            pills += pillX() - labelX(5)
            pages += pageX()
            if (frame == 3) assertTrue("overflow page also fades", peak(visits.last().color) in .02f.. .98f)
        }
        advance(1000)
        val settled = labelX(5)
        println("overflow rtl=$rtl font=$fontScale labels=$samples final=$settled pillRelative=$pills pages=$pages")
        assertEquals("key dispatch must not reposition the viewport synchronously", before, samples.first(), .5f)
        assertAnimated(samples, settled)
        assertAnimated(pills, pillX() - labelX(5))
        assertTrue("page must move independently of the strip", pages.any { abs(it - pageX()) > 1f })
        compose.onNodeWithTag("tab-4").assertIsFocused()
        key(forward)
        advance(64)
        val midScroll = labelX(5)
        key(backward)
        val interrupted = mutableListOf(labelX(5))
        repeat(12) { advance(16); interrupted += labelX(5) }
        advance(1000)
        println("overflow interrupted rtl=$rtl font=$fontScale samples=$interrupted final=${labelX(5)}")
        assertEquals("reversal must not reposition synchronously", midScroll, interrupted.first(), .5f)
        assertAnimated(interrupted, labelX(5))
        assertEquals("latest reversal returns to the same viewport", settled, labelX(5), .5f)
        compose.onNodeWithTag("tab-4").assertIsFocused()
        key(Key.DirectionCenter)
        assertEquals("native OK delivery remains intact", 1, clicks)
    }

    private fun assertAnimated(samples: List<Float>, final: Float) {
        val distance = abs(final - samples.first())
        assertTrue("fixture must actually move", distance > 20f)
        assertTrue("motion needs multiple distinct intermediate positions",
            samples.distinct().count { abs(it - samples.first()) > 1f && abs(it - final) > 1f } >= 3)
        assertTrue("one frame must not consume the entire displacement",
            samples.zipWithNext().all { (a, b) -> abs(b - a) < distance * .8f })
    }

    private fun show(rightToLeft: Boolean = false, fontScale: Float = 1f, overflow: Boolean = false) {
        rtl = rightToLeft
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        val names = if (overflow) List(9) { "Long channel category $it" } else listOf("Schedule", "Archive")
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    pillColor = MaterialTheme.colorScheme.onSurface.toArgb()
                    focusScrollDistance = LocalBringIntoViewSpec.current.calculateScrollDistance(0f, 100f, 600f)
                    motion = rememberTabContentMotion(selected.intValue)
                    Column(Modifier.size(600.dp, 300.dp).background(Color.Black)) {
                        Text("Header", Modifier.onGloballyPositioned { header = it })
                        AppTabRow(selected.intValue, AppTabStyle.Page, Modifier.fillMaxWidth()) {
                            names.forEachIndexed { index, name ->
                                AppTab(
                                    selected = selected.intValue == index,
                                    label = name,
                                    labelModifier = Modifier.onGloballyPositioned { labels[index] = it },
                                    onFocus = { select(index) },
                                    onClick = { clicks++ },
                                    modifier = Modifier.testTag("tab-$index"),
                                )
                            }
                        }
                        TabContent(motion, selected.intValue, state = { selected.intValue }, modifier = Modifier.weight(1f)) { _, owner ->
                            val visit = remember(owner) {
                                Visit(owner, listOf(Color.Red, Color.Blue, Color.Green)[visits.size % 3]).also { visits += it }
                            }
                            Box(Modifier.padding(start = 280.dp, top = 40.dp).size(20.dp).background(visit.color)
                                .onGloballyPositioned { visit.coordinates = it })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tab-0").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
        compose.onNodeWithTag("tab-0").assertIsFocused()
        assertEquals("exercise the TV pivot policy, not the mobile default", -180f, focusScrollDistance, .01f)
        compose.mainClock.autoAdvance = false
    }

    private fun select(index: Int) {
        motion.select(index, (0..8).toList())
        selected.intValue = index
    }

    private fun key(key: Key) {
        compose.runOnIdle {
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key.nativeKeyCode))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key.nativeKeyCode))
        }
    }

    private fun advance(millis: Long) {
        repeat((millis / 16).toInt()) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.waitForIdle()
    }

    private fun pageX() = requireNotNull(visits.last().coordinates).localToRoot(Offset.Zero).x
    private fun labelX(index: Int) = labels.getValue(index).localToRoot(Offset.Zero).x

    // Sample the native indicator's solid top padding, away from label glyphs.
    // Subtracting a label's x isolates PillIndicator motion from viewport scrolling.
    private fun pillX(): Float {
        val bitmap = bitmap()
        val y = labels.getValue(5).localToRoot(Offset.Zero).y.toInt() - 4
        val pixels = (0 until 600).filter { bitmap.getPixel(it, y) == pillColor }
        assertTrue("native focused pill must be visible", pixels.isNotEmpty())
        return (pixels.first() + pixels.last()) / 2f
    }

    private fun peak(color: Color): Float {
        val bitmap = bitmap()
        var peak = 0f
        for (y in 100 until 300) for (x in 0 until 600) {
            val pixel = Color(bitmap.getPixel(x, y))
            val value = when (color) {
                Color.Red -> pixel.red - maxOf(pixel.green, pixel.blue)
                Color.Blue -> pixel.blue - maxOf(pixel.red, pixel.green)
                else -> pixel.green - maxOf(pixel.red, pixel.blue)
            }
            peak = maxOf(peak, value)
        }
        return peak
    }

    private fun bitmap(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private class Visit(val owner: TabOwner, val color: Color) {
        var coordinates: LayoutCoordinates? = null
    }
}
