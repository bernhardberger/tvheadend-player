package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Rows may draw beneath either fade, but native focus scrolling must keep the focused
 * row in the readable band and restore the original top boundary without oscillation.
 * The spec is pure policy so this covers it without a composition.
 */
@OptIn(ExperimentalFoundationApi::class)
class ChannelsListViewportTest {
    private val container = 416f // 540 - (32dp heading inset + 36dp heading + 12dp gap + 44dp scopes)
    private val top = 4f
    private val scrolledTop = 56f
    private val bottom = 56f
    private var scrollBackAvailable = 0f
    // The foundation default's minimal-scroll rule, restated: it is internal in the library.
    private val minimalScroll = object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
            val trailingEdge = offset + size
            return when {
                offset >= 0 && trailingEdge <= containerSize -> 0f
                offset < 0 && trailingEdge > containerSize -> 0f
                offset < 0 -> offset
                else -> trailingEdge - containerSize
            }
        }
    }
    private val spec = InsetBringIntoViewSpec(
        delegate = minimalScroll,
        topInsetPx = top,
        scrolledTopInsetPx = scrolledTop,
        bottomInsetPx = bottom,
        scrollBackAvailablePx = { scrollBackAvailable },
    )

    // Foundation Android 1.11.4's native leanback pivot, including the large-child fallback.
    // The integrated evidence test enables FEATURE_LEANBACK to exercise the actual delegate.
    private val tvPivot = object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
            val childSize = abs(offset + size - offset)
            val initialTarget = .3f * containerSize
            val target = if (childSize <= containerSize && containerSize - initialTarget < childSize) {
                containerSize - childSize
            } else initialTarget
            return offset - target
        }
    }
    private val pivotSpec = InsetBringIntoViewSpec(
        delegate = tvPivot,
        topInsetPx = top,
        scrolledTopInsetPx = scrolledTop,
        bottomInsetPx = bottom,
        scrollBackAvailablePx = { scrollBackAvailable },
    )

    @Test
    fun tvPivotSmallForwardRequestsAtTheBoundaryRemainSettled() {
        // The boundary target is 110.8; the scrolled target is 147.2. Moving between
        // them used to alternate a positive boundary request and its negative inverse.
        for (size in listOf(64f, 83.2f)) {
            for (offset in listOf(111f, 120f, 140f, 147f)) {
                scrollBackAvailable = 0f
                repeat(8) {
                    val distance = pivotSpec.calculateScrollDistance(offset - scrollBackAvailable, size, container)
                    assertEquals("no unstable forward request at offset=$offset size=$size", 0f, distance, .001f)
                    scrollBackAvailable += distance
                }
            }
        }
    }

    @Test
    fun tvPivotGenuineForwardMovementConvergesAndRemainsSettled() {
        val distance = pivotSpec.calculateScrollDistance(240f, 64f, container)
        assertEquals(92.8f, distance, .001f)
        scrollBackAvailable += distance
        assertTrue(scrollBackAvailable > 0f)
        repeat(8) {
            assertEquals(0f, pivotSpec.calculateScrollDistance(240f - scrollBackAvailable, 64f, container), .001f)
        }
    }

    @Test
    fun tvPivotBackwardReturnToTheBoundaryDoesNotRestartForwardMovement() {
        scrollBackAvailable = 29.2f
        val distance = pivotSpec.calculateScrollDistance(140f - scrollBackAvailable, 64f, container)
        assertEquals(-29.2f, distance, .001f)
        scrollBackAvailable += distance
        repeat(8) {
            assertEquals(0f, pivotSpec.calculateScrollDistance(140f, 64f, container), .001f)
        }
    }

    @Test
    fun tvPivotRestorationClampedAtTheBoundaryRemainsSettled() {
        scrollBackAvailable = Float.POSITIVE_INFINITY // The viewport before restoration.
        val requestedOffset = pivotSpec.calculateScrollDistance(top, 64f, container, Float.POSITIVE_INFINITY)
        // Restoring the third row cannot put it at the 147.2px pivot: its original
        // top is only 140px, so LazyColumn clamps the destination at the start.
        val restoredScroll = (140f - top + requestedOffset).coerceAtLeast(0f)
        assertEquals(0f, restoredScroll, .001f)
        assertEquals(0f, pivotSpec.calculateScrollDistance(140f, 64f, container, restoredScroll), .001f)
        scrollBackAvailable = restoredScroll
        repeat(8) {
            assertEquals(0f, pivotSpec.calculateScrollDistance(140f, 64f, container), .001f)
        }
    }

    @Test
    fun rowInsideTheReadableBandDoesNotScroll() {
        assertEquals(0f, spec.calculateScrollDistance(offset = 64f, size = 56f, containerSize = container))
        // Exactly touching the bottom reserve still counts as visible.
        assertEquals(0f, spec.calculateScrollDistance(offset = container - bottom - 56f, size = 56f, containerSize = container))
    }

    @Test
    fun rowReachingIntoTheBottomFadeScrollsUpByTheOverlap() {
        val rowTop = container - bottom - 40f // 16px of the 56px row would sit under the reserve
        assertEquals(16f, spec.calculateScrollDistance(offset = rowTop, size = 56f, containerSize = container))
    }

    @Test
    fun scrolledRowAboveTheFadeScrollsDownToTheReadableBand() {
        scrollBackAvailable = Float.POSITIVE_INFINITY
        assertEquals(-56f, spec.calculateScrollDistance(offset = 0f, size = 64f, containerSize = container))
        assertEquals(-76f, spec.calculateScrollDistance(offset = -20f, size = 64f, containerSize = container))
        assertEquals(0f, spec.calculateScrollDistance(offset = scrolledTop, size = 64f, containerSize = container))
    }

    @Test
    fun firstRowClampsToTheActualBoundaryAndThenStopsRequestingScroll() {
        scrollBackAvailable = 20f
        assertEquals(-20f, spec.calculateScrollDistance(offset = top - 20f, size = 64f, containerSize = container))
        scrollBackAvailable = 0f
        assertEquals(0f, spec.calculateScrollDistance(offset = top, size = 64f, containerSize = container))
        // The native 1.05 shape can extend above the unscaled 4dp reserve at rest.
        assertEquals(0f, spec.calculateScrollDistance(offset = 2.4f, size = 67.2f, containerSize = container))
    }

    @Test
    fun scrollPositionIsReadForEachCalculationWithoutRecreatingTheSpec() {
        assertEquals(0f, spec.calculateScrollDistance(offset = top, size = 64f, containerSize = container))
        scrollBackAvailable = 100f
        assertEquals(-52f, spec.calculateScrollDistance(offset = top, size = 64f, containerSize = container))
        scrollBackAvailable = 0f
        assertEquals(0f, spec.calculateScrollDistance(offset = top, size = 64f, containerSize = container))
    }

    @Test
    fun explicitRestorationUsesTheDestinationBoundaryRatherThanTheOldViewport() {
        assertEquals(-52f, spec.calculateScrollDistance(top, 64f, container, Float.POSITIVE_INFINITY))
        scrollBackAvailable = Float.POSITIVE_INFINITY
        assertEquals(0f, spec.calculateScrollDistance(top, 64f, container, 0f))
    }

    @Test
    fun scrolledViewportKeepsTheExistingBottomSafety() {
        scrollBackAvailable = Float.POSITIVE_INFINITY
        assertEquals(16f, spec.calculateScrollDistance(container - bottom - 48f, 64f, container))
    }

    @Test
    fun otherInsetViewportsKeepTheirFixedInsetsWithoutOptingIntoScrollState() {
        val fixed = InsetBringIntoViewSpec(minimalScroll, top, bottom)
        assertEquals(-4f, fixed.calculateScrollDistance(offset = 0f, size = 56f, containerSize = container))
        assertEquals(-24f, fixed.calculateScrollDistance(offset = -20f, size = 56f, containerSize = container))
    }

    @Test
    fun delegateSeesTheInsetViewportSoAPivotPolicyStaysAboveTheFade() {
        val seen = mutableListOf<Triple<Float, Float, Float>>()
        val recording = InsetBringIntoViewSpec(
            delegate = object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                    seen += Triple(offset, size, containerSize)
                    return 7f
                }
            },
            topInsetPx = top,
            scrolledTopInsetPx = scrolledTop,
            bottomInsetPx = bottom,
            scrollBackAvailablePx = { scrollBackAvailable },
        )

        assertEquals(7f, recording.calculateScrollDistance(offset = 100f, size = 56f, containerSize = container))
        assertEquals(listOf(
            Triple(100f - top, 56f, container - top - bottom),
            Triple(100f - scrolledTop, 56f, container - scrolledTop - bottom),
        ), seen)
        seen.clear()
        scrollBackAvailable = Float.POSITIVE_INFINITY
        assertEquals(7f, recording.calculateScrollDistance(offset = 100f, size = 56f, containerSize = container))
        assertEquals(listOf(Triple(100f - scrolledTop, 56f, container - scrolledTop - bottom)), seen)
    }
}
