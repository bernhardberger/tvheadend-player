package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.*
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
class ChannelScheduleFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun liveEntryTabsAndOpeningUseRemoteFocus() {
        var opened: EventId? = null
        show(viewer = NOW, onOpen = { opened = it.id })
        compose.onNodeWithTag("schedule-row-2").assertIsFocused()
        compose.onNodeWithTag("schedule-row-2").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("schedule-row-1").assertIsFocused()
        compose.onNodeWithTag("schedule-row-1").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Today").assertIsFocused()
        compose.onNodeWithText("Today").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Tomorrow").assertIsFocused()
        compose.onNodeWithText("Tomorrow").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("schedule-row-4").assertIsFocused()
        compose.onNodeWithTag("schedule-row-4").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(EventId(4), opened)
    }

    @Test fun behindLiveEntryAndDetailsReturnUseTheSourceRow() {
        var detail by mutableStateOf<EpgEvent?>(null)
        var focused: EpgEvent? = null
        compose.setContent { TVHeadendPlayerTheme {
            if (detail == null) PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
                ChannelSchedule(null, events, NOW, NOW - 3600, false, false, { null },
                    onOpen = { detail = it }, onClose = {}, onRetry = {},
                    initialEvent = focused ?: events[0], onFocused = { focused = it })
            }
        } }
        compose.onNodeWithTag("schedule-row-1").assertIsFocused()
        compose.onNodeWithTag("schedule-row-1").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("schedule-row-2").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { detail = null }
        compose.onNodeWithTag("schedule-row-2").assertIsFocused()
    }

    @Test fun loadingDataArrivalAndEmptyCloseHaveFocus() {
        var data by mutableStateOf(emptyList<EpgEvent>())
        var loading by mutableStateOf(true)
        compose.setContent { TVHeadendPlayerTheme { PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
            ChannelSchedule(null, data, NOW, NOW, loading, false, { null }, {}, {}, {})
        } } }
        compose.onNodeWithText("Loading schedule").assertIsFocused()
        compose.runOnIdle { loading = false }
        compose.onNodeWithText("Close").assertIsFocused()
        compose.runOnIdle { data = events }
        compose.onNodeWithTag("schedule-row-2").assertIsFocused()
    }

    private fun show(viewer: Long, onOpen: (EpgEvent) -> Unit = {}) {
        compose.setContent { TVHeadendPlayerTheme { PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
            ChannelSchedule(null, events, NOW, viewer, false, false, { null }, onOpen, {}, {})
        } } }
    }

    companion object {
        const val NOW = 1789246200L
        val events = listOf(
            event(1, NOW - 5400, NOW - 1800), event(2, NOW - 1800, NOW + 1800),
            event(3, NOW + 1800, NOW + 5400), event(4, NOW + 86400, NOW + 90000),
        )
        private fun event(id: Int, start: Long, stop: Long) = EpgEvent.create(EventId(id.toLong()), ChannelId(1),
            Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(stop), title = "Programme $id")
    }
}
