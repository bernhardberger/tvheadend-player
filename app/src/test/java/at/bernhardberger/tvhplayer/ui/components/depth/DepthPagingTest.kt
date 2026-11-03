package at.bernhardberger.tvhplayer.ui.components.depth

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A column given a page-key mapping pages its rows with CH+/CH-, clamped at both ends, as Settings does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class DepthPagingTest {
    @get:Rule val compose = createComposeRule()

    private val rowIds = (1..40).map { "row-$it" }
    private val navigation = DepthNavigationState(DepthStack(listOf(DepthFrame("root", "row-1"))))

    private fun show(paging: Boolean) {
        val level = DepthLevel("root", rowIds.map { id ->
            DepthRow(DepthItem(id)) { modifier, _ -> Box(modifier.width(300.dp).height(48.dp).focusable()) }
        }, heading = {}, initialItemId = "row-1")
        val levels = mapOf("root" to level)
        compose.setContent {
            DepthNavigation(navigation, levels, { level }, columnWidth = 340.dp, columnGap = 92.dp,
                contentPadding = PaddingValues(), modifier = Modifier.fillMaxSize(),
                pageKeyDirection = if (paging) ChannelNavigation::pageDirectionForKeyCode else null)
        }
        compose.waitForIdle()
    }

    private fun focusedIndex() = compose.runOnIdle { rowIds.indexOf(navigation.stack.active.focusedItemId) }

    private fun press(code: Int) {
        compose.onRoot().performKeyInput { pressKey(Key(code)) }
        compose.waitForIdle()
    }

    @Test fun channelKeysPageTheRowsAndClamp() {
        show(paging = true)
        assertEquals(0, focusedIndex())
        press(android.view.KeyEvent.KEYCODE_CHANNEL_UP)
        assertEquals("clamped at the top", 0, focusedIndex())
        press(android.view.KeyEvent.KEYCODE_CHANNEL_DOWN)
        val page = focusedIndex()
        assertTrue("CH- moved a page ($page)", page >= 2)
        press(android.view.KeyEvent.KEYCODE_CHANNEL_UP)
        assertEquals(0, focusedIndex())
        repeat(30) { press(android.view.KeyEvent.KEYCODE_CHANNEL_DOWN) }
        assertEquals("clamped at the end", rowIds.lastIndex, focusedIndex())
    }

    @Test fun withoutAMappingChannelKeysDoNothing() {
        show(paging = false)
        press(android.view.KeyEvent.KEYCODE_CHANNEL_DOWN)
        assertEquals(0, focusedIndex())
    }
}
