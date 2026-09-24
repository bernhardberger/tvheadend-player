package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dense list draws to the screen bottom beneath a fade, but native focus scrolling
 * must keep the focused row inside the readable band above it (and below the top
 * reserve). The spec is pure policy so this covers it without a composition.
 */
@OptIn(ExperimentalFoundationApi::class)
class ChannelsListViewportTest {
    private val container = 460f
    private val top = 4f
    private val bottom = 56f
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
        bottomInsetPx = bottom,
    )

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
    fun rowAboveTheTopReserveScrollsDownToTheReserve() {
        assertEquals(-4f, spec.calculateScrollDistance(offset = 0f, size = 56f, containerSize = container))
        assertEquals(-24f, spec.calculateScrollDistance(offset = -20f, size = 56f, containerSize = container))
    }

    @Test
    fun delegateSeesTheInsetViewportSoAPivotPolicyStaysAboveTheFade() {
        var seen: Triple<Float, Float, Float>? = null
        val recording = InsetBringIntoViewSpec(
            delegate = object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                    seen = Triple(offset, size, containerSize)
                    return 7f
                }
            },
            topInsetPx = top,
            bottomInsetPx = bottom,
        )

        assertEquals(7f, recording.calculateScrollDistance(offset = 100f, size = 56f, containerSize = container))
        assertEquals(Triple(100f - top, 56f, container - top - bottom), seen)
    }
}
