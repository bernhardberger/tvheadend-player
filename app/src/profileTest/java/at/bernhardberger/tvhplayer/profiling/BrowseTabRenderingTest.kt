package at.bernhardberger.tvhplayer.profiling

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ChannelTagSelector
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingModeTabs
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BrowseTabRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyProfileActivity>()

    @Test fun channelScopeTravelsAndReversesWithoutDarkeningTextAheadOfThePill() = checkTabs(false)
    @Test fun recordingScopeTravelsAndReversesWithoutDarkeningTextAheadOfThePill() = checkTabs(true)

    @Test fun recordingsUsesTheShellHeaderAnchor() {
        compose.waitUntilAtLeastOneExists(hasText("All channels"), 15_000)
        compose.waitForIdle()
        if (compose.onAllNodes(hasText("Channels") and isFocused()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            compose.waitForIdle()
        }
        compose.onNodeWithText("All channels").assertIsFocused()
        listOf(Key.DirectionLeft, Key.DirectionDown, Key.DirectionDown, Key.DirectionRight).forEach { key ->
            compose.onRoot().performKeyInput { pressKey(key) }
            compose.waitForIdle()
        }
        compose.onNodeWithText("Archive").requestFocus()
        compose.waitForIdle()
        compose.onNodeWithText("Archive").assertIsFocused()
        assertEquals("Recordings tabs use the production shell's heading anchor",
            compose.onNode(hasText("Recordings") and !hasClickAction()).fetchSemanticsNode().boundsInRoot.left,
            compose.onNodeWithText("Archive").fetchSemanticsNode().boundsInRoot.left, 1f)
        capture("recordings-full")
    }

    private fun checkTabs(recordings: Boolean) {
        compose.waitUntilAtLeastOneExists(hasText("All channels"), 15_000)
        var tag by mutableStateOf<ChannelTagId?>(null)
        var mode by mutableStateOf(DvrLibraryMode.ARCHIVE)
        val labels = if (recordings) listOf("Archive", "Schedule", "Problems") else listOf("All channels", "News", "Sports")
        val prefix = if (recordings) "recordings" else "channels-guide"
        val initial = FocusRequester()
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                TVHeadendPlayerTheme {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp)) {
                        Text(if (recordings) "Recordings" else "Channels / Guide", style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("heading"))
                        if (recordings) {
                            RecordingModeTabs(mode, initial, Modifier.width(800.dp).testTag("scope-row"),
                                onFocused = { mode = it }, onClick = { mode = it }, onMoveToContent = {})
                        } else {
                            ChannelTagSelector(
                                tags = listOf(ChannelTag.create(ChannelTagId(7), "News"), ChannelTag.create(ChannelTagId(8), "Sports")),
                                activeTagId = tag, onSelectTag = { tag = it },
                                modifier = Modifier.width(800.dp).testTag("scope-row"), activeFocusRequester = initial,
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(labels.first()).requestFocus()
        compose.waitForIdle()
        assertEquals("Scope and headline share their leading anchor",
            compose.onNodeWithTag("heading").fetchSemanticsNode().boundsInRoot.left,
            compose.onNodeWithText(labels.first()).fetchSemanticsNode().boundsInRoot.left, 1f)
        capture("$prefix-start")
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            compose.mainClock.advanceTimeByFrame()
            val incoming = compose.onNodeWithText(labels[1]).assertIsFocused().captureToImage().toPixelMap()
            var brightText = 0
            for (y in incoming.height / 3 until incoming.height * 2 / 3) {
                for (x in incoming.width / 4 until incoming.width * 3 / 4) {
                    val c = incoming[x, y]
                    if (c.red > 0.65f && c.green > 0.65f && c.blue > 0.65f) brightText++
                }
            }
            assertTrue("Incoming label must stay readable before the pill arrives", brightText > 10)
            capture("$prefix-incoming")
            val centres = mutableSetOf<Int>()
            repeat(6) { frame ->
                compose.mainClock.advanceTimeByFrame()
                centres += pillCentre()
                if (frame == 2) capture("$prefix-moving")
            }
            assertTrue("Pill must travel across rendered frames, not jump", centres.size >= 3)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            repeat(4) { frame ->
                compose.mainClock.advanceTimeByFrame()
                if (frame == 0) {
                    compose.onNodeWithText(labels.first()).assertIsFocused()
                    capture("$prefix-return-incoming")
                }
                if (frame == 2) capture("$prefix-reversing")
            }
            compose.mainClock.advanceTimeBy(200)
            compose.onNodeWithText(labels.first()).assertIsFocused()
            val settled = compose.onNodeWithText(labels.first()).captureToImage().toPixelMap()
            assertTrue("Settled focus has a light backing", settled[settled.width / 2, settled.height / 5].red > 0.8f)
            var darkText = 0
            for (y in settled.height / 3 until settled.height * 2 / 3) {
                for (x in settled.width / 4 until settled.width * 3 / 4) {
                    if (settled[x, y].red < 0.25f) darkText++
                }
            }
            assertTrue("Settled text contrasts with the light pill", darkText > 10)
            capture("$prefix-settled")
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun pillCentre(): Int {
        val pixels = compose.onNodeWithTag("scope-row").captureToImage().toPixelMap()
        val xs = (0 until pixels.width).filter { x -> pixels[x, pixels.height / 5].red > 0.8f }
        assertTrue("Visible focus backing is required", xs.isNotEmpty())
        return (xs.first() + xs.last()) / 2
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "browse-chrome-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
