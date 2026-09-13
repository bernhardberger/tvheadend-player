package at.bernhardberger.tvhplayer.profiling

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(InternalComposeTracingApi::class, ExperimentalTestApi::class)
class ChannelsReadIsolationTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyProfileActivity>()

    @Test fun rowFocusUpdatesDetailsWithoutReexecutingChannelsRoots() {
        compose.waitUntilExactlyOneExists(hasTestTag("channel-row-1"), 5_000)
        if (compose.onAllNodes(hasText("Channels") and isFocused()).fetchSemanticsNodes().isNotEmpty()) {
            key(Key.DirectionRight)
        }
        compose.onNodeWithText("All channels").assertIsFocused()
        val roots = mutableListOf<String>()
        var bodies = 0
        var details = 0
        compose.runOnIdle {
            Composer.setTracer(object : CompositionTracer {
                override fun isTraceInProgress() = true
                override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                    when (info.substringBefore(" (")) {
                        "at.bernhardberger.tvhplayer.ui.screens.ChannelsScreen",
                        "at.bernhardberger.tvhplayer.ui.screens.ChannelsScreenContent" -> roots += info
                        "at.bernhardberger.tvhplayer.ui.screens.EpgDetailPane" -> details++
                        "at.bernhardberger.tvhplayer.ui.components.BrowseTabContent" -> bodies++
                    }
                }
                override fun traceEventEnd() = Unit
            })
        }
        try {
            // A real drawer round-trip changes the wrapper's inputs as well as content
            // ownership. Entry alone need not recompose the correctly isolated wrapper.
            key(Key.DirectionLeft)
            compose.onNode(hasText("Channels") and isFocused()).assertExists()
            key(Key.DirectionRight)
            compose.onNodeWithText("All channels").assertIsFocused()
            key(Key.DirectionDown)
            compose.onNodeWithTag("channel-row-1").assertIsFocused()
            compose.runOnIdle {
                assertEquals("The observer must see BOTH roots before the isolation check",
                    setOf("at.bernhardberger.tvhplayer.ui.screens.ChannelsScreen",
                        "at.bernhardberger.tvhplayer.ui.screens.ChannelsScreenContent"),
                    roots.map { it.substringBefore(" (") }.toSet())
                roots.clear()
                assertTrue("The observer must also see the real tab body", bodies > 0)
                bodies = 0
                details = 0
            }
            for ((direction, id) in listOf(Key.DirectionDown to 2, Key.DirectionDown to 3,
                Key.DirectionUp to 2, Key.DirectionUp to 1)) {
                key(direction)
                compose.onNodeWithTag("channel-row-$id").assertIsFocused()
                compose.onNodeWithTag("channels-detail-channel").assertTextEquals("Offline channel $id")
            }
            compose.runOnIdle {
                assertTrue("Details must actually update", details >= 4)
                assertEquals("Ordinary focus must not execute either Channels root: $roots", 0, roots.size)
                assertEquals("Focus must stay in the details/rows, not reexecute the tab body", 0, bodies)
            }
        } finally {
            compose.runOnIdle { Composer.setTracer(null) }
        }
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
