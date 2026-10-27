package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import kotlinx.coroutines.awaitCancellation
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.*
import kotlin.time.Instant
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
        compose.onNodeWithTag("channel-schedule-list").performScrollToNode(hasTestTag("channel-schedule-footer"))
        compose.onNodeWithTag("channel-schedule-footer").assertTextEquals("Loading later days")
        compose.runOnIdle { loading = false }
        compose.onNodeWithTag("channel-schedule-footer").assertTextContains("No program information after", substring = true)
        compose.onNodeWithTag("schedule-row-2").assertIsFocused()
    }

    @Test fun statusLinesLeadWithTheirTimeFactsAndEndWithTheCategory() {
        val news = events.map { EpgEvent.create(it.id, it.channelId, it.start, it.stop, title = it.title,
            contentGenre = ContentGenre.fromContentType(0x20L)) }
        compose.setContent { TVHeadendPlayerTheme { PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
            ChannelSchedule(null, news, NOW, NOW, false, false,
                { if (it.id == EventId(1)) DvrEntryState.COMPLETED_ERROR else null }, {}, {}, {})
        } } }
        // An ellipsis may only ever cut the category, never the time left or the failure.
        compose.onNodeWithText("Ended • Recording failed • News", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("30m left • News", substring = true, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("60m • News", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("News •", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun theSelectedDaysTabStaysWholeInViewWhenTheListMovesToALaterDay() {
        // More days than the panel shows tabs for, even at this test's narrow fallback font.
        val days = List(20) { event(it + 1, NOW + it * 86_400L, NOW + it * 86_400L + 3600) }
        compose.setContent { TVHeadendPlayerTheme { PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
            ChannelSchedule(null, days, NOW, NOW, false, false, { null }, {}, {}, {}, initialEvent = days.last())
        } } }
        compose.onNodeWithTag("schedule-row-20").assertIsFocused()
        val list = compose.onNodeWithTag("channel-schedule-list").getUnclippedBoundsInRoot()
        val tab = compose.onNode(SemanticsMatcher("a day tab") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("schedule-tab-") == true } and isSelected()).getUnclippedBoundsInRoot()
        assertTrue("selected tab $tab outside $list", tab.left >= list.left && tab.right <= list.right)
    }

    @Test fun theRowAboveTheFocusedOneStaysClearOfTheTopFade() {
        val evening = List(6) { event(it + 1, NOW + it * 3600L, NOW + (it + 1) * 3600L) }
        compose.setContent { TVHeadendPlayerTheme { PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
            ChannelSchedule(null, evening, NOW, NOW, false, false, { null }, {}, {}, {}, initialEvent = evening[4])
        } } }
        compose.onNodeWithTag("schedule-row-5").assertIsFocused()
        val list = compose.onNodeWithTag("channel-schedule-list").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("schedule-row-4").assertTopPositionInRootIsAtLeast(list.top + ListTopFadeHeight)
        compose.onNodeWithTag("schedule-row-5").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("schedule-row-4").assertIsFocused()
        compose.onNodeWithTag("schedule-row-3").assertTopPositionInRootIsAtLeast(list.top + ListTopFadeHeight)
    }

    @Test fun theTopReserveIsARealRowsHeightAtEveryFontScale() {
        var fontScale by mutableStateOf(1f)
        var computed = 0.dp
        compose.setContent { TVHeadendPlayerTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                computed = rememberScheduleRowHeight()
                PlaybackOptionsOverlayFrame(panelWidth = 480.dp) {
                    // Focus away from the running row, so its own height is measured unscaled and with its strip.
                    ChannelSchedule(null, events, NOW, NOW, false, false, { null }, {}, {}, {}, initialEvent = events[2])
                }
            }
        } }
        for (scale in listOf(1f, 1.3f)) {
            compose.runOnIdle { fontScale = scale }
            compose.waitForIdle()
            val measured = compose.onNodeWithTag("schedule-row-2").fetchSemanticsNode().size.height
            val expected = with(compose.density) { computed.toPx() }
            // tv-material keeps the list item's padding internal; a library change must show up here.
            assertEquals("row height at font scale $scale", expected, measured.toFloat(), 1f)
        }
    }

    @Test fun theCoverageRequestReachesTheGuidesWeekAndEndsWithItsSessionThePanelOrTheTimeout() {
        val fake = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED,
                dvrWrite = CapabilityAccess.DENIED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events)),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = emptyList())),
        ))
        val requests = java.util.Collections.synchronizedList(mutableListOf<Triple<CurrentSessionObservation, ChannelId, Instant>>())
        val ended = AtomicInteger()
        val session = object : TvheadendSession by fake {
            override val epgRepository = object : EpgRepository by fake.epgRepository {
                override suspend fun acquireCoverage(currentSession: CurrentSessionObservation, channelId: ChannelId,
                    through: Instant): EpgCoverageAcquisitionResult {
                    requests += Triple(currentSession, channelId, through)
                    // A server that never answers: only cancellation or the panel's timeout ends the request.
                    try { awaitCancellation() } finally { ended.incrementAndGet() }
                }
            }
        }
        val mutations = DvrMutationActions(
            scheduleEntry = { _, _ -> error("Unexpected DVR mutation") },
            stopEntry = { _, _ -> error("Unexpected DVR mutation") },
            cancelEntry = { _, _ -> error("Unexpected DVR mutation") },
            deleteEntry = { _, _ -> error("Unexpected DVR mutation") },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected DVR mutation") })
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, null) }
        val settings = UiSettingsStore(ApplicationProvider.getApplicationContext<Application>())
        var open by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { TVHeadendPlayerTheme {
            val observation by fake.observation.collectAsState()
            val scope = rememberCoroutineScope()
            val layers = remember { LivePlayerLayerState(scope, autoHideTimeoutMillis = 5_000) }
            if (open) PlayerChannelSchedule(layers, channel.id, channel, observation, { fake.observation.value }, NOW,
                AppTimeshiftState(), mutations, notices, scope, settings, onRetry = {},
                onPlay = { _, _ -> error("Unexpected playback command") }, onPlayRecording = null,
                onShowGuide = { _, _ -> error("Unexpected Guide") }, session = session)
        } }
        fun settle() { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
        // The request runs off the main thread; frames keep the paused clock's effects moving meanwhile.
        fun settleUntil(condition: () -> Boolean) {
            repeat(100) { if (condition()) return; settle(); Thread.sleep(10) }
            assertTrue(condition())
        }
        fun footer(): SemanticsNodeInteraction {
            compose.onNodeWithTag("channel-schedule-list").performScrollToNode(hasTestTag("channel-schedule-footer"))
            return compose.onNodeWithTag("channel-schedule-footer")
        }
        settle()
        settleUntil { requests.size == 1 }
        val (first, channelId, through) = requests[0]
        assertSame(fake.observation.value.currentSession, first)
        assertEquals(channel.id, channelId)
        assertEquals(Instant.fromEpochSeconds(scheduleCoverageThroughSec(NOW, ZoneId.systemDefault())), through)
        assertTrue("reaches the Guide's week", through.epochSeconds - NOW >= 6 * 86_400L)
        footer().assertTextEquals("Loading later days")

        // A new session generation cancels the old request and asks again under the new proof.
        compose.runOnIdle { fake.replaceGeneration(fake.observation.value) }
        settle()
        settleUntil { requests.size == 2 && ended.get() == 1 }
        assertNotSame(first, requests[1].first)
        assertSame(fake.observation.value.currentSession, requests[1].first)

        // Closing the panel cancels its request; opening it again asks once more.
        compose.runOnIdle { open = false }
        settle()
        settleUntil { ended.get() == 2 }
        compose.runOnIdle { open = true }
        settle()
        settleUntil { requests.size == 3 }
        footer().assertTextEquals("Loading later days")

        // The timeout settles the request: the list ends on its end-of-schedule line.
        compose.mainClock.advanceTimeBy(29_000)
        compose.waitForIdle()
        footer().assertTextEquals("Loading later days")
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        settleUntil { ended.get() == 3 }
        settle()
        footer().assertTextContains("No program information after", substring = true)
        assertEquals(3, requests.size)
    }

    private fun SemanticsNodeInteraction.assertTopPositionInRootIsAtLeast(top: androidx.compose.ui.unit.Dp) {
        val actual = getUnclippedBoundsInRoot().top
        assertTrue("row top $actual above $top", actual >= top)
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
        val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101)
        val events = listOf(
            event(1, NOW - 5400, NOW - 1800), event(2, NOW - 1800, NOW + 1800),
            event(3, NOW + 1800, NOW + 5400), event(4, NOW + 86400, NOW + 90000),
        )
        private fun event(id: Int, start: Long, stop: Long) = EpgEvent.create(EventId(id.toLong()), ChannelId(1),
            Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(stop), title = "Programme $id")
    }
}
