package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ChannelShelfCompositionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun shelfUpRestoresControlsAndInvokerThroughTheCompleteKeyCycle() = assertShelfDismissal(Key.DirectionUp)
    @Test fun shelfBackRestoresControlsAndInvokerThroughTheCompleteKeyCycle() = assertShelfDismissal(Key.Back)
    @Test fun emptyShelfUpRestoresControlsAndInvoker() = assertShelfDismissal(Key.DirectionUp, empty = true)
    @Test fun rapidShelfUpRoundTripDoesNotSwallowTheNextDownPress() = assertShelfDismissal(Key.DirectionUp, rapid = true)
    @Test fun rapidShelfBackRoundTripDoesNotSwallowTheNextDownPress() = assertShelfDismissal(Key.Back, rapid = true)

    private fun assertShelfDismissal(closeKey: Key, empty: Boolean = false, rapid: Boolean = false) {
        lateinit var layers: LivePlayerLayerState
        var activations = 0
        rule.setContent {
            layers = rememberLivePlayerLayerState()
            val context = LocalContext.current
            val loader = remember { ImageLoader.Builder(context).build() }
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    when {
                        at.bernhardberger.tvhplayer.core.playbackSuppressesRevealingKey(layers.revealingKeyCode, code) -> {
                            if (event.type == KeyEventType.KeyUp) layers.endOpeningKeyCycle(code)
                            true
                        }
                        event.key == Key.Back -> {
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                layers.beginOpeningKeyCycle(code)
                                if (layers.channelDrawerOpen) layers.dismissChannelDrawer() else layers.hideControls()
                            }
                            true
                        }
                        else -> false
                    }
                }) {
                    PlayerControlsLayer(layers.controlsVisible, modalVisible = false) {
                        OverlayControlsTv(
                            imageLoader = loader, channelNumber = 1, channelName = "Channel", piconPath = null,
                            nowEvent = null, nextEvent = null, nowSec = 0, controlsVisible = layers.controlsVisible,
                            optionsOpen = false, onOpenChannels = {
                                layers.beginOpeningKeyCycle(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                                layers.openChannelDrawer()
                            },
                            onOpenInfo = { activations++ }, onStopPlayback = { activations++ },
                            onUserInteraction = {}, onOpenOptions = { activations++ },
                            timeshiftState = at.bernhardberger.tvhplayer.playback.AppTimeshiftState(available = true),
                            timeshiftFeedback = null, onToggleTimeshiftPause = { activations++ },
                            onSeekTimeshift = {}, onGoLive = {},
                            restoreChannelAction = layers.restoreChannelAction,
                            onChannelActionRestored = layers::onChannelActionRestored,
                            onActionFocused = layers::onActionFocused,
                        )
                    }
                    if (layers.channelDrawerOpen) ChannelDrawer(
                        channels = if (empty) emptyList() else listOf(Channel.create(id = ChannelId(1), name = "Channel")),
                        selectedId = ChannelId(1), playingChannelId = ChannelId(1), recordingChannelIds = emptySet(),
                        nowEvent = { null }, nextEvent = { null }, imageLoader = loader,
                        onFocusChannel = {}, onPickChannel = { activations++ }, onCloseDrawer = { code ->
                            if (code != null) layers.beginOpeningKeyCycle(code)
                            layers.dismissChannelDrawer()
                        },
                    )
                }
            }
        }
        rule.waitForIdle()
        if (rapid) rule.mainClock.autoAdvance = false
        for (invoker in listOf("player-info", "player-settings", "player-stop")) {
            rule.onNodeWithTag(invoker).requestFocus().performKeyInput { pressKey(Key.DirectionDown) }
            if (rapid) rule.mainClock.advanceTimeBy(32)
            rule.onNodeWithTag(if (empty) "player-shelf-close" else "player-channel-card-1").assertIsFocused()
            if (rapid) rule.onNodeWithTag("player-actions").assertExists() else {
                rule.mainClock.advanceTimeBy(500)
                rule.onNodeWithTag("player-actions").assertDoesNotExist()
            }
            rule.onRoot().performKeyInput { keyDown(closeKey) }
            if (rapid) rule.mainClock.advanceTimeBy(48)
            val restored = if (invoker == "player-stop") "player-pause" else invoker
            rule.onNodeWithTag(restored).assertIsFocused()
            val keyCode = if (closeKey == Key.Back) android.view.KeyEvent.KEYCODE_BACK else android.view.KeyEvent.KEYCODE_DPAD_UP
            rule.onRoot().performKeyPress(androidx.compose.ui.input.key.KeyEvent(android.view.KeyEvent(
                0, 0, android.view.KeyEvent.ACTION_DOWN, keyCode, 2,
            )))
            rule.onRoot().performKeyInput { keyUp(closeKey) }
            rule.onNodeWithTag(restored).assertIsFocused()
            assertTrue(layers.controlsVisible)
            assertEquals(0, activations)
        }
        rule.onRoot().performKeyInput { pressKey(Key.Back) }
        rule.runOnIdle { assertTrue(!layers.controlsVisible) }
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun largeTextShelfKeepsChannelIdentityPrimaryAndMissingCardsEqualWithRemoteFocus() {
        var picks = 0
        var closes = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                TVHeadendPlayerTheme {
                    ChannelDrawer(
                        channels = listOf(Channel.create(id = ChannelId(1), name = "Dokumentation und Zeitgeschichte"),
                            Channel.create(id = ChannelId(2), name = "Culture")),
                        selectedId = ChannelId(2), playingChannelId = ChannelId(1),
                        recordingChannelIds = emptySet(),
                        nowEvent = { if (it == ChannelId(1)) event("A long programme about mountains and wildlife") else null },
                        nextEvent = { if (it == ChannelId(1)) event("Another long programme about the natural world") else null },
                        imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                        onFocusChannel = {}, onPickChannel = { picks++ }, onCloseDrawer = { closes++ },
                    )
                }
            }
        }
        val first = rule.onNodeWithTag("player-channel-card-1").assertIsFocused()
        val now = rule.onNodeWithTag("player-channel-1-now", useUnmergedTree = true)
        val next = rule.onNodeWithTag("player-channel-1-next", useUnmergedTree = true)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        next.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue(now.fetchSemanticsNode().boundsInRoot.height > next.fetchSemanticsNode().boundsInRoot.height)
        val firstBounds = first.fetchSemanticsNode().boundsInRoot
        assertEquals(with(rule.density) { 288.dp.toPx() }, firstBounds.width, 1f)
        assertTrue(firstBounds.height <= with(rule.density) { 248.dp.toPx() })
        val picon = rule.onNodeWithTag("player-channel-1-picon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(with(rule.density) { 96.dp.toPx() }, picon.width, 1f)
        assertEquals(with(rule.density) { 64.dp.toPx() }, picon.height, 1f)
        val identityLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        rule.onNodeWithTag("player-channel-1-identity", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(identityLayouts) }
        val nowLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        now.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(nowLayouts) }
        assertTrue(identityLayouts.single().layoutInput.style.fontSize > nowLayouts.single().layoutInput.style.fontSize)
        val identityLayout = identityLayouts.single()
        assertTrue("Identity needs two actual lines without clipping: lines=${identityLayout.lineCount}, " +
            "size=${identityLayout.size}, constraints=${identityLayout.layoutInput.constraints}, " +
            "lineHeight=${identityLayout.layoutInput.style.lineHeight}, density=${identityLayout.layoutInput.density}",
            identityLayout.lineCount == 2 && !identityLayout.isLineEllipsized(0) &&
                identityLayout.getLineBottom(1) <= identityLayout.size.height)
        assertTrue(next.fetchSemanticsNode().boundsInRoot.bottom <= firstBounds.bottom - with(rule.density) { 12.dp.toPx() })
        val shelf = rule.onNodeWithTag("player-channel-shelf").fetchSemanticsNode().boundsInRoot
        assertTrue(shelf.height - firstBounds.height <= with(rule.density) { 56.dp.toPx() } + 1f)
        val nextDescription = next.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()
        assertTrue(nextDescription.contains(at.bernhardberger.tvhplayer.ui.common.formatClock(3600)))
        first.performKeyInput { pressKey(Key.DirectionRight) }
        val second = rule.onNodeWithTag("player-channel-card-2").assertIsFocused()
        val secondBounds = second.fetchSemanticsNode().boundsInRoot
        assertEquals(firstBounds.height, secondBounds.height, 1f)
        assertEquals(firstBounds.width, secondBounds.width, 1f)
        rule.onNodeWithTag("player-channel-2-now", useUnmergedTree = true).assertTextEquals("No EPG")
        rule.onNodeWithTag("player-channel-2-next", useUnmergedTree = true).assertTextEquals("No EPG")
        assertEquals(0, picks)
        second.performKeyInput { pressKey(Key.DirectionLeft) }
        first.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, picks)
        first.performKeyInput { pressKey(Key.DirectionUp) }
        assertEquals(1, closes)
    }

    private fun event(title: String) = EpgEvent.create(
        id = EventId(1), channelId = ChannelId(1), title = title,
        start = Instant.fromEpochSeconds(0), stop = Instant.fromEpochSeconds(3600),
    )
}
