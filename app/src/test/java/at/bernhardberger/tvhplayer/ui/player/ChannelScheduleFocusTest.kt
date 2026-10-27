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
        compose.onNodeWithContentDescription("Loading schedule").assertIsFocused()
        compose.runOnIdle { loading = false }
        compose.onNodeWithText("Close").assertIsFocused()
        compose.runOnIdle { data = events }
        compose.onNodeWithTag("schedule-row-2").assertIsFocused()
    }

    @Test fun viewerRowUsesThePlayersBehindLiveDistanceAndNoPlayingMark() {
        var behind by mutableStateOf(0L)
        compose.setContent { TVHeadendPlayerTheme { CompositionLocalProvider(LocalWatchingChannel provides ChannelId(1),
                LocalWatchingIndicator provides ChannelPlaybackIndicator.PLAYING) {
            PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
                ChannelSchedule(null, events, NOW, NOW - 3600, false, false, { null }, {}, {}, {}, viewerBehindMs = behind)
            }
        } } }
        // At the live edge the row says nothing, whatever the wall clock suggests; no "0 minutes".
        compose.onAllNodesWithText("behind live", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("channel-playing-indicator", useUnmergedTree = true).assertCountEquals(0)
        compose.runOnIdle { behind = 3_600_000L }
        compose.onNodeWithText("1:00:00 behind live", substring = true, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("channel-playing-indicator", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun rowsWithLaterDaysArrivingEndOnALoadingLine() {
        var loading by mutableStateOf(true)
        compose.setContent { TVHeadendPlayerTheme { PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
            ChannelSchedule(null, events, NOW, NOW, loading, false, { null }, {}, {}, {})
        } } }
        compose.onNodeWithTag("schedule-row-2").assertIsFocused()
        compose.onNodeWithTag("channel-schedule-footer").performScrollTo().assertTextEquals("Loading later days")
        compose.runOnIdle { loading = false }
        compose.onNodeWithTag("channel-schedule-footer").performScrollTo().assertTextContains("No program information after", substring = true)
        compose.onNodeWithTag("schedule-row-2").assertIsFocused()
    }

    private fun show(viewer: Long, onOpen: (EpgEvent) -> Unit = {}) {
        compose.setContent { TVHeadendPlayerTheme { PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
            ChannelSchedule(null, events, NOW, viewer, false, false, { null }, onOpen, {}, {})
        } } }
    }

    @Test fun endedLiveRowMovesToNextWithoutStealingOrdinaryUpdates() {
        var now by mutableStateOf(NOW)
        var source by mutableStateOf(events)
        compose.setContent { TVHeadendPlayerTheme { PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
            ChannelSchedule(null, channelScheduleEvents(source, ChannelId(1), now, null), now, null,
                false, false, { null }, {}, {}, {})
        } } }
        compose.onNodeWithTag("schedule-row-2").assertIsFocused()
        compose.runOnIdle { source = events + event(5, NOW + 90000, NOW + 93600) }
        compose.onNodeWithTag("schedule-row-2").assertIsFocused()
        compose.runOnIdle { now = NOW + 1800 }
        compose.onNodeWithTag("schedule-row-3").assertIsFocused()
    }

    @Test fun historyBoundaryEvictsFocusedEndedProgrammeToNextRow() {
        var history by mutableStateOf(NOW - 5400)
        compose.setContent { TVHeadendPlayerTheme { PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
            ChannelSchedule(null, channelScheduleEvents(events, ChannelId(1), NOW, history), NOW, NOW - 3600,
                false, false, { null }, {}, {}, {})
        } } }
        compose.onNodeWithTag("schedule-row-1").assertIsFocused()
        compose.runOnIdle { history = NOW - 1800 }
        compose.onNodeWithTag("schedule-row-2").assertIsFocused()
    }

    @Test fun midnightProgrammeStartsOnTodayAndTodayDownReturnsToIt() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.of(2026, 9, 13)
        val midnight = today.atStartOfDay(zone).toEpochSecond()
        val across = listOf(event(1, midnight - 3600, midnight + 3600), event(2, midnight + 3600, midnight + 7200))
        compose.setContent { TVHeadendPlayerTheme { PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
            ChannelSchedule(null, across, midnight + 600, midnight + 600, false, false, { null }, {}, {}, {})
        } } }
        compose.onNodeWithTag("schedule-row-1").assertIsFocused()
        compose.onNodeWithText("Today").assertIsSelected()
        compose.onNodeWithTag("schedule-row-1").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Today").assertIsFocused()
        compose.onNodeWithText("Today").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("schedule-row-1").assertIsFocused()
        val gap = scheduleGaps(midnight - 600, midnight + 600, zone)
        assertEquals(listOf(today.minusDays(1), today), gap.map { it.day })
        assertEquals(midnight, gap[0].stop)
        assertEquals(midnight, gap[1].start)
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
