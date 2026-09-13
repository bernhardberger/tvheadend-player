package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class BrowseContentMotionTest {
    @get:Rule val compose = createComposeRule()
    private val selected = mutableIntStateOf(0)
    private val revision = mutableIntStateOf(0)
    private lateinit var motion: BrowseContentMotion
    private var bodiesCreated = 0
    private val owners = mutableListOf<BrowseTabOwner>()

    @Test fun contentMovesInBothDirectionsWhileHeaderStaysPut() = assertDirections(LayoutDirection.Ltr)

    @Test fun contentMotionFollowsRtlTabOrder() = assertDirections(LayoutDirection.Rtl)

    @Test fun sameFrameRoundTripDoesNotInventAnUnrenderedPage() {
        show()
        val origin = left("moving-content")
        compose.runOnIdle {
            select(1)
            select(2)
            select(0)
        }
        advance(32)
        assertEquals(origin, left("moving-content"), 0.5f)
        compose.onNodeWithTag("body-label").assertTextEquals("Content 0 revision 0")
        advance(1000)
        assertEquals(origin, left("moving-content"), 0.5f)
        assertEquals(1, bodiesCreated)
    }

    @Test fun refreshAndRepeatedSelectionDoNotRestartTheContentSlide() {
        show()
        val origin = left("moving-content")
        compose.runOnIdle { select(1) }
        advance(48)
        val first = left("moving-content")
        compose.runOnIdle {
            revision.intValue++
            select(1)
        }
        advance(48)
        val later = left("moving-content")
        assertTrue("Motion must keep advancing rather than restart", later < first)
        assertTrue(later >= origin)
        compose.onNodeWithTag("body-label").assertTextEquals("Content 1 revision 1")
        advance(1000)
        assertEquals(origin, left("moving-content"), 0.5f)
        assertEquals(2, bodiesCreated)
    }

    @Test fun externalDestinationReplacementDiscardsObsoleteMotion() {
        show()
        val origin = left("moving-content")
        compose.runOnIdle { select(1) }
        advance(32)
        compose.runOnIdle { selected.intValue = 2 }
        advance(32)
        assertEquals(origin, left("moving-content"), 0.5f)
        compose.runOnIdle { selected.intValue = 1 }
        advance(32)
        assertEquals(origin, left("moving-content"), 0.5f)
    }

    @Test fun pendingScopeKeepsTheDepartingPresentationBeforeNewRowsArrive() {
        show()
        val before = compose.onNodeWithTag("motion-canvas").captureToImage().toPixelMap()
        compose.runOnIdle {
            motion.select(1, listOf(0, 1, 2))
            revision.intValue++ // Live focus/details changed, but the rendered scope is still 0.
        }
        advance(32)
        val waiting = compose.onNodeWithTag("motion-canvas").captureToImage().toPixelMap()
        var changedPixels = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (before[x, y] != waiting[x, y]) changedPixels++
        }
        assertEquals("An unready destination must not rewrite the departing page", 0, changedPixels)
        compose.runOnIdle { selected.intValue = 1 }
        advance(1000)
        compose.onNodeWithTag("body-label").assertTextEquals("Content 1 revision 1")
    }

    @Test fun rejectedSelectionRestoresTheUnchangedAuthoritativeBody() {
        show()
        val origin = left("moving-content")
        compose.runOnIdle {
            motion.select(1, listOf(0, 1, 2))
            motion.acceptRendered(0) // The owner rejected 1 and still publishes 0.
            revision.intValue++
        }
        advance(32)
        compose.onNodeWithTag("body-label").assertTextEquals("Content 0 revision 1")
        assertEquals(origin, left("moving-content"), 0.5f)
        assertEquals(1, owners.count { it.isCurrent })
    }

    @Test fun anOldVisitCannotBecomeActiveAgainOnRapidReturn() {
        show()
        val old = owners.single()
        compose.runOnIdle {
            select(1)
            assertFalse(old.isCurrent)
        }
        advance(64)
        compose.runOnIdle { select(0) }
        advance(64)
        assertFalse(old.isCurrent)
        assertEquals(1, owners.count { it.isCurrent })
        advance(1000)
        compose.onNodeWithTag("body-label").assertTextEquals("Content 0 revision 0")
    }

    @Test fun leafUpdatesStayLocalAndTheirDepartingValueStaysVisible() {
        var bodyCommits = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                motion = rememberBrowseContentMotion(selected.intValue)
                val reader = remember { { revision.intValue } }
                Column(Modifier.size(300.dp, 200.dp).background(Color.Black).testTag("motion-canvas")) {
                    BrowseTabContent(motion, selected.intValue, state = { reader }) { source, _ ->
                        SideEffect { bodyCommits++ }
                        ColourLeaf(source)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            assertTrue("The observer must see the body before checking isolation", bodyCommits > 0)
            bodyCommits = 0
            revision.intValue = 1
        }
        advance(32)
        compose.onNodeWithTag("leaf-1").assertExists()
        assertEquals("A leaf read must not reexecute the body", 0, bodyCommits)
        compose.runOnIdle { select(1); revision.intValue = 2 }
        advance(64)
        assertTrue("The old green leaf must remain rendered", marker(Color.Green).second > 0.02f)
        assertTrue("The current blue leaf must render the new value", marker(Color.Blue).second > 0.02f)
    }

    @Composable
    private fun ColourLeaf(source: () -> Int) {
        val value = rememberBrowseTabReader(source)()
        val color = when (value) { 0 -> Color.Red; 1 -> Color.Green; else -> Color.Blue }
        Box(Modifier.padding(start = 140.dp).size(20.dp).background(color).testTag("leaf-$value"))
    }

    private fun assertDirections(direction: LayoutDirection) {
        show(direction)
        val origin = left("moving-content")
        val header = left("stationary-header")
        val sign = if (direction == LayoutDirection.Ltr) 1 else -1
        compose.runOnIdle { select(1) }
        advance(64)
        assertTrue(sign * (left("moving-content") - origin) > 1f)
        assertEquals(header, left("stationary-header"), 0.01f)
        val departing = marker(Color.Red)
        val arriving = marker(Color.Blue)
        // Pixels prove the old payload still exists despite its removed semantics.
        assertTrue(sign * (departing.first - 150f) < -1f)
        assertTrue(sign * (arriving.first - 150f) > 1f)
        assertTrue(departing.second in 0.02f..0.98f)
        assertTrue(arriving.second in 0.02f..0.98f)
        advance(48)
        assertTrue(marker(Color.Red).second < departing.second)
        assertTrue(marker(Color.Blue).second > arriving.second)
        advance(1000)
        assertEquals(origin, left("moving-content"), 0.5f)
        compose.runOnIdle { select(0) }
        advance(64)
        assertTrue(sign * (left("moving-content") - origin) < -1f)
        assertEquals(header, left("stationary-header"), 0.01f)
        advance(1000)
        assertEquals(origin, left("moving-content"), 0.5f)
        assertEquals(3, bodiesCreated)
    }

    private fun show(direction: LayoutDirection = LayoutDirection.Ltr) {
        compose.setContent {
            TVHeadendPlayerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    motion = rememberBrowseContentMotion(selected.intValue)
                    Column(Modifier.size(300.dp, 200.dp).background(Color.Black).testTag("motion-canvas")) {
                        Text("Tabs", modifier = Modifier.testTag("stationary-header"))
                        BrowseTabContent(
                            motion, selected.intValue,
                            state = { selected.intValue to revision.intValue },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) { frame, owner ->
                            remember { bodiesCreated++; owners.add(owner) }
                            val color = when (frame.first) { 0 -> Color.Red; 1 -> Color.Blue; else -> Color.Green }
                            Box(Modifier.padding(start = 140.dp).size(20.dp).background(color).testTag("moving-content"))
                            Text("Content ${frame.first} revision ${frame.second}", Modifier.testTag("body-label"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun select(index: Int) {
        motion.select(index, listOf(0, 1, 2))
        selected.intValue = index
    }

    private fun advance(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }

    private fun left(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().left.value

    private fun marker(color: Color): Pair<Float, Float> {
        val pixels = compose.onNodeWithTag("motion-canvas").captureToImage().toPixelMap()
        var sum = 0f
        var weightedX = 0f
        var peak = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val p = pixels[x, y]
            val value = when (color) {
                Color.Red -> p.red - maxOf(p.blue, p.green)
                Color.Green -> p.green - maxOf(p.red, p.blue)
                else -> p.blue - maxOf(p.red, p.green)
            }
            if (value > 0.01f) {
                sum += value
                weightedX += x * value
                peak = maxOf(peak, value)
            }
        }
        assertTrue("Expected visible $color departing/arriving marker", sum > 0f)
        return (weightedX / sum * 300f / pixels.width) to peak
    }
}
