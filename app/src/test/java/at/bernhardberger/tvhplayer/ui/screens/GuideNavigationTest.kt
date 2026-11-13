package at.bernhardberger.tvhplayer.ui.screens


import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import at.bernhardberger.tvhplayer.ui.components.StatusIconSize
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagPreferences
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.GuideRows
import at.bernhardberger.tvhplayer.settings.GuideView
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.GuidePosition
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.rememberPreparedBrowseData
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.performKeyPress
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabelRes
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineTimeRuler
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadlineSettleMs
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadlineRulerGap
import at.bernhardberger.tvhplayer.ui.screens.guide.guideHeadlineHeight
import at.bernhardberger.tvhplayer.ui.screens.guide.guideFocusWindowStart
import at.bernhardberger.tvhplayer.ui.screens.guide.guideTimePositionPx
import at.bernhardberger.tvhplayer.ui.screens.guide.guideVisibleWindowSec
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineChannelRow
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineChannelHeader
import at.bernhardberger.tvhplayer.ui.screens.guide.guideRowTitleBottom
import at.bernhardberger.tvhplayer.ui.screens.guide.guideTimelineRowHeight
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import kotlinx.coroutines.test.StandardTestDispatcher

/** Offline native production Guide navigation: focus, entry, panels and Back; geometry lives in [GuideVisualLayoutTest]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi",
    instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideNavigationTest : GuideScreenFixture() {
    @Test fun recordKeyOnScheduledProgrammeOpensCancelWithoutConfirming() {
        show(recordings = listOf(DvrEntry.create(DvrEntryId(1), eventId = EventId(110),
            channelId = ChannelId(1), state = DvrEntryState.SCHEDULED)))
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key(android.view.KeyEvent.KEYCODE_MEDIA_RECORD))
        compose.onNodeWithTag("details-cancel").assertIsFocused()
        key(Key(android.view.KeyEvent.KEYCODE_MEDIA_RECORD))
        compose.onNodeWithTag("details-cancel").assertIsFocused()
        compose.onNodeWithTag("two-column-first").assertDoesNotExist()
        assertNoPlayback()
    }

    @Test fun retainedEntryWaitsForRowsWhenNewCoverageArrivesBeforePreparation() {
        val coverageRelease = CompletableDeferred<Unit>()
        try {
            show(initialCoverage = false, beforeCoverageBatch = { _, _ -> coverageRelease.await() })
            key(Key.DirectionDown)
            focused(title(1, 0))
            compose.onNodeWithTag("guide-date-button").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.runOnIdle { guideActive = false }
            val snapshot = checkNotNull(session.observation.value.epgSnapshotForDisplay)
            val onAir = EpgEvent.create(EventId(310), ChannelId(3), Instant.fromEpochSeconds(hour),
                Instant.fromEpochSeconds(hour + 3600), title = title(3, 0))
            val fresh = EpgSnapshot.create(events = snapshot.events + onAir,
                historicalEvents = snapshot.historicalEvents, coverages = (1L..12L).map {
                    EpgCoverage.create(ChannelId(it), Instant.fromEpochSeconds(hour), Instant.fromEpochSeconds(hour + 7 * 86400))
                })
            compose.runOnIdle { preparationContext = StandardTestDispatcher() }
            publishEvents(fresh.events, fresh, covered = true)
            compose.runOnIdle {
                position.save(checkNotNull(position.position.value).copy(channelId = ChannelId(3)))
                guideActive = true
            }
            compose.waitForIdle()
            // New coverage must not settle entry against old rows and remember their fallback.
            releasePreparation()
            awaitGuideWork { compose.onAllNodesWithText(title(3, 0)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(title(3, 0)).assertExists()
            key(Key.DirectionDown)
            focused(title(3, 0))
            assertEquals(ChannelId(3), position.position.value?.channelId)
            assertNoPlayback()
        } finally {
            coverageRelease.complete(Unit)
        }
    }

    @Test fun offlineRetainedEntryUsesAvailableFallbackInsteadOfTheOldTarget() {
        val release = CompletableDeferred<Unit>()
        try {
            show(initialCoverage = false, beforeCoverageBatch = { _, _ -> release.await() })
            key(Key.DirectionDown)
            key(Key.DirectionRight)
            focused(title(1, 1))
            compose.onNodeWithTag("guide-date-button").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.runOnIdle { guideActive = false }
            compose.runOnIdle {
                val observation = session.observation.value
                session.publish(SessionObservation.create(sessionState = SessionState.Disconnected,
                    channelState = observation.channelState, epgState = observation.epgState, dvrState = observation.dvrState))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                position.save(checkNotNull(position.position.value).copy(channelId = ChannelId(3)))
                guideActive = true
            }
            compose.waitForIdle()
            compose.onNodeWithText(title(4, 0)).assertExists()
            key(Key.DirectionDown)
            focused(title(4, 0))
            assertNoPlayback()
        } finally { release.complete(Unit) }
    }

    @Test fun groupChangeKeepsTheLastFocusedGuideChannelAheadOfLastPlayed() {
        groupChangeAfterPlayerReturnUsesTheMostRecentlyFocusedChannel(playerReturn = false)
    }

    @Test fun groupChangeAfterPlayerReturnUsesTheMostRecentlyFocusedChannel() {
        groupChangeAfterPlayerReturnUsesTheMostRecentlyFocusedChannel(playerReturn = true)
    }

    private fun groupChangeAfterPlayerReturnUsesTheMostRecentlyFocusedChannel(playerReturn: Boolean) {
        if (playerReturn) position.save(GuidePosition(ChannelId(1), EventId(110), hour, hour - 1800, 0))
        show(groupWithoutFirst = true, lastPlayedChannel = ChannelId(1),
            playerReturn = if (playerReturn) savedTimelineReturn() else null)
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionDown)
        focused(title(2, 0))
        compose.onNodeWithTag("channels-tags-button").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key(Key.Enter)
        tagFocused("channels-tags-all")
        key(Key.DirectionDown)
        tagFocused("channels-tags-1")
        key(Key.Enter)
        key(Key.DirectionDown)
        focused(title(2, 0))
        assertEquals(ChannelId(2), position.position.value?.channelId)
        assertNoPlayback(additionalCoverageCalls = 1)
    }

    @Test fun retainedRecordingPlayerReturnRestoresTheCurrentStoredSpot() {
        show(headline = true)
        repeat(11) { key(Key.DirectionDown) }
        focused(title(12, 0))
        repeat(4) { offset ->
            key(Key.DirectionRight)
            focused(title(12, offset + 1))
        }
        focused(title(12, 4))
        val left = checkNotNull(position.position.value)
        assertNotEquals(hour - 1800, left.windowStartSec)
        assertTrue("The return also exercises a scrolled channel viewport", left.firstVisibleColumn > 0)
        compose.onNodeWithTag("guide-date-button").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        // Keep EpgGridScreen composed, as when Back reverses the shell's outgoing animation.
        compose.runOnIdle { guideActive = false }
        compose.runOnIdle {
            browseReturn = true
            guideActive = true
        }
        key(Key.DirectionDown)
        focused(title(12, 4))
        assertEquals(left, position.position.value)
        assertEquals(0, playbackRequests)
        assertEquals(0, navigationRequests)
    }

    @Test fun offlineRetainedEntryScrollsToTheFallbackChannelsPage() {
        val release = CompletableDeferred<Unit>()
        try {
            show(headline = true, initialCoverage = false, beforeCoverageBatch = { _, _ -> release.await() })
            key(Key.DirectionDown)
            focused(title(1, 0))
            compose.onNodeWithTag("guide-date-button").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.runOnIdle { guideActive = false }
            compose.runOnIdle {
                val observation = session.observation.value
                val snapshot = checkNotNull(observation.epgSnapshotForDisplay)
                session.publish(SessionObservation.create(sessionState = SessionState.Disconnected,
                    channelState = observation.channelState,
                    epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                        events = snapshot.events.filter { it.channelId == ChannelId(7) })),
                    dvrState = observation.dvrState))
            }
            // Wait for the reduced rows, not just the newer disconnected observation.
            compose.waitForIdle()
            compose.onNode(hasText(context().getString(R.string.epg_no_data)) and
                hasAnyAncestor(hasTestTag("epg-channel-row-1")), useUnmergedTree = true).assertExists()
            compose.onNode(hasText(title(1, 0)) and isFocusable()).assertDoesNotExist()
            compose.runOnIdle {
                position.save(checkNotNull(position.position.value).copy(channelId = ChannelId(3)))
                guideActive = true
            }
            dateFocused()
            compose.onNodeWithTag("epg-channel-row-7").assertIsDisplayed()
            key(Key.DirectionDown)
            focused(title(7, 0))
            assertNoPlayback()
        } finally { release.complete(Unit) }
    }
    @Test fun openingWithoutPlaybackUsesRememberedChannelAtNow() {
        rememberTomorrow()
        show(lastOffset = 26)
        key(Key.DirectionDown)
        focused(title(2, 0))
        assertEquals(hour - 1800, position.position.value?.windowStartSec)
    }

    @Test fun openingWithWarmPlaybackOverridesRememberedChannelAndTime() {
        rememberTomorrow()
        show(lastOffset = 26, playingChannel = ChannelId(5))
        key(Key.DirectionDown)
        focused(title(5, 0))
        assertEquals(hour - 1800, position.position.value?.windowStartSec)
    }

    @Test fun openingWithPlayingChannelOutsideGroupUsesRememberedChannelAtNow() {
        rememberTomorrow()
        show(lastOffset = 26, playingChannel = ChannelId(1), groupWithoutFirst = true, activeGroup = ChannelTagId(2))
        key(Key.DirectionDown)
        focused(title(2, 0))
        assertEquals(hour - 1800, position.position.value?.windowStartSec)
        assertEquals(ChannelTagId(2), model.scope.value.scope.activeTagId)
    }

    private fun rememberTomorrow() {
        position.save(GuidePosition(ChannelId(2), EventId(234), hour + 86400, hour + 86400, 0))
    }

    @Test fun openingWaitsForTheRememberedChannelsProgrammeWithoutTakingHeaderFocus() {
        position.save(GuidePosition(ChannelId(3), EventId(334), hour + 86400, hour + 86400, 0))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            show(history = false, initialCoverage = false, beforeCoverageBatch = { _, _ ->
                entered.complete(Unit)
                release.await()
            })
            runAcquisition()
            assertTrue("Initial acquisition reached the held fake", entered.isCompleted)
            dateFocused()
            val event = EpgEvent.create(EventId(310), ChannelId(3), Instant.fromEpochSeconds(hour),
                Instant.fromEpochSeconds(hour + 3600), title = title(3, 0))
            val snapshot = EpgSnapshot.create(events = checkNotNull(session.observation.value.epgSnapshotForDisplay).events + event, coverages = (1L..12L).map {
                EpgCoverage.create(ChannelId(it), Instant.fromEpochSeconds(hour - 3600), Instant.fromEpochSeconds(hour + 4 * 3600))
            })
            publishEvents(snapshot.events, snapshot, covered = true)
            compose.runOnIdle { release.complete(Unit) }
            dateFocused()
            key(Key.DirectionDown)
            focused(title(3, 0))
            assertEquals(hour - 1800, position.position.value?.windowStartSec)
        } finally {
            release.complete(Unit)
        }
    }

    @Test fun headerNavigationAndRestorationStayOnVisibleActions() {
        show()
        compose.onNodeWithText(context().getString(R.string.now)).assertDoesNotExist()
        // A guide without genre codes has nothing to filter by.
        compose.onNodeWithTag("guide-filter-button").assertDoesNotExist()
        val date = compose.onNode(isFocused()).fetchSemanticsNode().id
        key(Key.DirectionRight)
        tagFocused("channels-tags-button")
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.DirectionDown)
        focused(title(1, 0))
        // Up from the top programme row lands on the date button, not on the last header action.
        key(Key.DirectionUp)
        dateFocused()
        assertEquals(date, compose.onNode(isFocused()).fetchSemanticsNode().id)
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        key(Key.DirectionRight)
        focused(title(1, 0))
        assertNoPlayback()
    }

    @Test fun horizontalAndVerticalEdgeFocusSettlesOutsideLocalFades() {
        show()
        key(Key.DirectionDown)
        focused(title(1, 0))
        val nowWindow = checkNotNull(position.position.value).windowStartSec
        // The fourth programme scrolls the window past now; the third still fits beside it.
        // Window preparation runs off the Compose scheduler. waitForIdle alone can return
        // while it is pending, when Guide deliberately consumes the next navigation key.
        repeat(3) { offset ->
            key(Key.DirectionRight)
            focused(title(1, offset + 1))
        }
        focused(title(1, 3))
        repeat(7) { key(Key.DirectionDown) }
        focused(title(9, 3)) // The metadata-only third channel is not an invented programme.
        assertReadableFocus()
        val window = checkNotNull(position.position.value).windowStartSec
        assertNotEquals(nowWindow, window)
        key(Key.Back)
        focused(title(9, 0)) // Scrolled past now: Back returns to now on the same channel first.
        assertEquals(nowWindow, checkNotNull(position.position.value).windowStartSec)
        assertNotEquals(window, checkNotNull(position.position.value).windowStartSec)
        key(Key.Back)
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        assertNoPlayback(additionalCoverageCalls = 1)
    }

    @Test fun absentHistoryDoesNotPaintAnEarlierContinuationAndLeftOpensTheRail() {
        show(history = false, lastOffset = 0)
        key(Key.DirectionDown)
        focused(title(1, 0))
        val before = position.position.value
        // D32: with no earlier programme, Left leaves the grid for the rail instead of doing nothing.
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        assertEquals(before, position.position.value)
        // Right from the rail returns to the remembered programme, as it does in Channels.
        key(Key.DirectionRight)
        focused(title(1, 0))
        assertEquals(before, position.position.value)
        val bitmap = compose.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val focus = compose.onNode(hasText(title(1, 0)) and isFocused()).fetchSemanticsNode().boundsInRoot
        val window = checkNotNull(position.position.value).windowStartSec
        val ruler0 = bounds("epg-time-ruler")
        assertEquals("Known first programme starts at its time, not under a speculative history fade",
            ruler0.left + guideTimePositionPx(hour, window, window + 3 * 3600, ruler0.width), focus.left, 2f)
        // The focus ring's leading edge stays bright: no fade dims it.
        val nearLeft = bitmap.getPixel(focus.left.toInt() + 2, focus.bottom.toInt() - 10)
        assertTrue(android.graphics.Color.red(nearLeft) > 180)
        val ruler = bounds("epg-time-ruler")
        val start = checkNotNull(position.position.value).windowStartSec
        val x = ruler.left + guideTimePositionPx(now, start, start + 3 * 3600, ruler.width)
        assertEquals("Terminal padding uses the same Now mapping as cells", lineColor, bitmap.getPixel(x.toInt(), focus.center.y.toInt()))
        assertEquals("Terminal padding uses the same Now mapping as the ruler", lineColor, bitmap.getPixel(x.toInt(), ruler.bottom.toInt() - 4))
        assertNoPlayback()
        bitmap.recycle()
    }

    @Test fun nowPointSequenceCrossesAGapAndReturnsToItsOrigin() {
        show(history = false, events = listOf(
            programme(110, 1, 0, 30, "Origin on air"),
            programme(111, 1, 30, 120, "Later origin programme"),
            programme(210, 2, 30, 120, "Nearest future programme across gap"),
            programme(410, 4, 0, 30, "On air below gap"),
            programme(411, 4, 30, 120, "Later programme below gap"),
        ))
        key(Key.DirectionDown)
        focused("Origin on air")
        val window = checkNotNull(position.position.value).windowStartSec
        key(Key.DirectionDown)
        focused("Nearest future programme across gap")
        key(Key.DirectionDown)
        focused("On air below gap")
        key(Key.DirectionUp)
        focused("Nearest future programme across gap")
        key(Key.DirectionUp)
        focused("Origin on air")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        assertNoPlayback()
    }

    @Test fun onAirAnchorAdvancesWithTheClockBetweenDownPresses() {
        show(history = false, events = listOf(
            programme(109, 1, -60, 0, "Previous programme"),
            programme(110, 1, 0, 120, "First long programme"),
            programme(210, 2, 0, 120, "Second long programme"),
            programme(410, 4, 0, 30, "Earlier programme"),
            programme(411, 4, 30, 120, "Programme on air later"),
        ))
        key(Key.DirectionDown)
        focused("First long programme")
        key(Key.DirectionLeft)
        focused("Previous programme")
        key(Key.DirectionRight)
        focused("First long programme")
        key(Key.DirectionDown)
        focused("Second long programme")
        val window = checkNotNull(position.position.value).windowStartSec
        assertTrue(SystemClock.setCurrentTimeMillis((hour + 45 * 60) * 1000))
        compose.mainClock.advanceTimeBy(6_000)
        key(Key.DirectionDown)
        focused("Programme on air later")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        assertNoPlayback()
    }

    @Test fun deferredChannelPageResolvesAtTheNewNow() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            show(history = false, initialCoverage = false, beforeCoverageBatch = { ids, _ ->
                if (ids.any { it.value >= 7 }) {
                    entered.complete(Unit)
                    release.await()
                }
            }, events = (1L..12L).flatMap { channel ->
                if (channel <= 6) listOf(programme(channel * 100 + 10, channel, 0, 120, "Long programme $channel"))
                else listOf(programme(channel * 100 + 10, channel, 0, 30, "Earlier programme $channel"),
                    programme(channel * 100 + 11, channel, 30, 120, "Later programme $channel"))
            })
            key(Key.DirectionDown)
            focused("Long programme 1")
            assertTrue(SystemClock.setCurrentTimeMillis((hour + 29 * 60 + 59) * 1000))
            compose.mainClock.advanceTimeBy(6_000)
            assertTrue("The page request starts before the programme change", SystemClock.uptimeMillis() < (hour + 30 * 60) * 1000)
            val window = checkNotNull(position.position.value).windowStartSec
            key(Key.ChannelDown)
            runAcquisition()
            assertTrue("Page acquisition reached the held fake", entered.isCompleted)
            assertTrue(SystemClock.setCurrentTimeMillis((hour + 30 * 60 + 1) * 1000))
            compose.mainClock.advanceTimeBy(6_000)
            assertTrue("The programme changes while coverage is pending", SystemClock.uptimeMillis() >= (hour + 30 * 60) * 1000)
            val snapshot = checkNotNull(session.observation.value.epgSnapshotForDisplay)
            publishEvents(snapshot.events, EpgSnapshot.create(events = snapshot.events, coverages = (1L..12L).map {
                EpgCoverage.create(ChannelId(it), Instant.fromEpochSeconds(hour - 1800), Instant.fromEpochSeconds(hour + 7 * 86400))
            }), covered = true)
            compose.runOnIdle { release.complete(Unit) }
            focused("Later programme 7")
            assertEquals(window, checkNotNull(position.position.value).windowStartSec)
            assertEquals(0, playbackRequests)
        } finally {
            release.complete(Unit)
        }
    }

    @Test fun restoredOnAirProgrammeWithNowInLeftReserveUsesItsVisibleSpan() {
        val window = hour + 14 * 60
        position.save(GuidePosition(ChannelId(1), EventId(110), hour, window, 0))
        show(history = false, playerReturn = savedTimelineReturn(), events = listOf(
            programme(110, 1, 0, 300, "Long programme on air"),
            programme(210, 2, 0, 40, "Short programme on air"),
            programme(211, 2, 40, 300, "Largest visible overlap"),
        ))
        key(Key.DirectionDown)
        focused("Long programme on air")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        val nowOffset = guideTimePositionPx(now, window, window + 10800, bounds("epg-time-ruler").width)
        assertTrue("Now is in the left edge reserve", nowOffset > 0 && nowOffset < 8)
        key(Key.DirectionDown)
        focused("Largest visible overlap")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        assertNoPlayback()
    }

    @Test fun leftThenRightThenDownFromOnAirUsesNowRatherThanProgrammeStart() {
        // Fixed now is 20:15: the next row changes one minute after the focused programme starts.
        show(history = false, events = listOf(
            programme(109, 1, -60, -10, "Previous programme"),
            programme(110, 1, -10, 44, "Current conversation"),
            programme(210, 2, -118, -9, "Ended movie"),
            programme(211, 2, -9, 100, "Current comedy"),
        ))
        key(Key.DirectionDown)
        focused("Current conversation")
        key(Key.DirectionLeft)
        focused("Previous programme")
        key(Key.DirectionRight)
        focused("Current conversation")
        val window = checkNotNull(position.position.value).windowStartSec
        key(Key.DirectionDown)
        focused("Current comedy")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        assertNoPlayback()
    }

    @Test fun futureVisibleSpanStaysStickyThroughALongProgramme() {
        show(events = listOf(
            programme(110, 1, 0, 60, "Current programme"),
            programme(111, 1, 60, 120, "Future span"),
            programme(210, 2, 0, 300, "Long programme"),
            programme(410, 4, 0, 90, "Earlier half"),
            programme(411, 4, 90, 120, "Later half"),
        ))
        key(Key.DirectionDown)
        focused("Current programme")
        key(Key.DirectionRight)
        focused("Future span")
        val window = checkNotNull(position.position.value).windowStartSec
        key(Key.DirectionDown)
        focused("Long programme")
        key(Key.DirectionDown)
        focused("Later half")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        key(Key.DirectionUp)
        focused("Long programme")
        key(Key.DirectionUp)
        focused("Future span")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
    }

    @Test fun leftAndRightBackToTheAnchoredProgrammeStartANewAnchor() {
        // Channel 1 runs one programme from 20:00 to 22:00; channel 2 is hourly.
        show(longProgramme = true, longProgrammeHours = 2, longProgrammeSuccessor = true)
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionDown)
        focused(title(2, 0))
        key(Key.DirectionRight)
        focused(title(2, 1))
        // Up keeps the 21:00–22:00 span inside the long programme.
        key(Key.DirectionUp)
        focused(title(1, 0))
        key(Key.DirectionRight)
        focused(title(1, 2))
        key(Key.DirectionLeft)
        focused(title(1, 0))
        // Left made a new focus, so Down uses now, not the old future span.
        key(Key.DirectionDown)
        focused(title(2, 0))
        assertNoPlayback()
    }

    @Test fun verticalMoveUsesReadableTimeWithoutMovingTheWindow() {
        show(events = listOf(
            programme(110, 1, -60, 30, "Long current programme"),
            programme(111, 1, 30, 90, "Next programme"),
            programme(210, 2, -75, -20, "Hidden earlier programme"),
            programme(211, 2, -20, 40, "Readable adjacent programme"),
        ), history = false)
        key(Key.DirectionDown)
        focused("Long current programme")
        key(Key.DirectionRight)
        focused("Next programme")
        key(Key.DirectionLeft)
        focused("Long current programme")
        val window = checkNotNull(position.position.value).windowStartSec
        assertEquals(hour - 1800, window)
        key(Key.DirectionDown)
        focused("Readable adjacent programme")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        assertReadableFocus()
        key(Key.DirectionUp)
        focused("Long current programme")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
    }

    @Test fun verticalMoveKeepsShortOffAirProgrammeAtItsSizeAcrossTheRightEdge() {
        show(events = listOf(
            EpgEvent.create(EventId(110), ChannelId(1), Instant.fromEpochSeconds(hour),
                Instant.fromEpochSeconds(hour + 3600), title = "Current programme"),
            EpgEvent.create(EventId(111), ChannelId(1), Instant.fromEpochSeconds(hour + 3600),
                Instant.fromEpochSeconds(hour + 7200), title = "Future programme"),
            EpgEvent.create(EventId(211), ChannelId(2), Instant.fromEpochSeconds(hour + 1800),
                Instant.fromEpochSeconds(hour + 9600), title = "Crossing right edge"),
        ))
        key(Key.DirectionDown)
        focused("Current programme")
        key(Key.DirectionRight)
        focused("Future programme")
        val window = checkNotNull(position.position.value).windowStartSec
        key(Key.DirectionDown)
        focused("Crossing right edge")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        assertReadableFocus()
        // 20:30 to 22:40 in a window that ends at 22:00: the screen cuts it, not a closed corner.
        val focus = compose.onNode(hasText("Crossing right edge") and isFocused()).getUnclippedBoundsInRoot()
        val ruler = bounds("epg-time-ruler")
        assertEquals(ruler.width * 130 / 180, (focus.right - focus.left).value, 1f)
        assertTrue("The cell runs past the screen edge: $focus", focus.right.value > ruler.right)
    }

    @Test fun verticalMoveSkipsLeftSliverForAShortFullyVisibleNeighbour() {
        show(history = false, events = listOf(
            programme(110, 1, -60, 30, "Current programme"),
            programme(210, 2, -60, -14, "Left sliver"),
            programme(211, 2, -14, -12, "Short visible neighbour"),
        ))
        key(Key.DirectionDown)
        focused("Current programme")
        val window = checkNotNull(position.position.value).windowStartSec
        key(Key.DirectionDown)
        focused("Short visible neighbour")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        val cell = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        assertTrue("Short fully visible cells qualify below 48dp", cell.width in 1f..47f)
        assertReadableFocus()
    }

    @Test fun verticalMoveSkipsRightSliverForTheFullyVisibleNeighbour() {
        show(events = listOf(
            programme(110, 1, 0, 60, "Current programme"),
            programme(111, 1, 146, 147, "Short right anchor"),
            programme(210, 2, 60, 146, "Visible neighbour"),
            programme(211, 2, 146, 240, "Right sliver"),
        ))
        key(Key.DirectionDown)
        focused("Current programme")
        key(Key.DirectionRight)
        focused("Short right anchor")
        val window = checkNotNull(position.position.value).windowStartSec
        key(Key.DirectionDown)
        focused("Visible neighbour")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        assertReadableFocus()
        key(Key.DirectionUp)
        focused("Short right anchor")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
    }

    @Test fun earlierContentRepositionsAKeptTargetHiddenByTheNewFade() {
        val events = listOf(
            EpgEvent.create(EventId(110), ChannelId(1), Instant.fromEpochSeconds(hour),
                Instant.fromEpochSeconds(hour + 3600), title = "Current programme"),
            EpgEvent.create(EventId(210), ChannelId(2), Instant.fromEpochSeconds(hour - 26 * 60),
                Instant.fromEpochSeconds(hour - 20 * 60), title = "Short edge programme"),
        )
        show(history = false, events = events)
        key(Key.DirectionDown)
        focused("Current programme")
        val window = checkNotNull(position.position.value).windowStartSec
        key(Key.DirectionDown)
        focused("Short edge programme")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        assertTrue(compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot.width > 0f)
        val snapshot = checkNotNull(session.observation.value.epgSnapshotForDisplay)
        val history = EpgEvent.create(EventId(109), ChannelId(1), Instant.fromEpochSeconds(hour - 3600),
            Instant.fromEpochSeconds(hour - 1800), title = "New earlier history")
        publishEvents(events, EpgSnapshot.create(events = events, historicalEvents = listOf(history), coverages = snapshot.coverages), true)
        compose.waitForIdle()
        assertNotEquals(window, checkNotNull(position.position.value).windowStartSec)
        focused("Short edge programme")
        assertReadableFocus()
        assertTrue(compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot.width > 0f)
    }

    @Test fun pageDownAndUpKeepTheTimeWindowForEdgeCrossingProgrammes() {
        show(events = (1L..12L).flatMap { channel ->
            if (channel == 1L) listOf(
                EpgEvent.create(EventId(110), ChannelId(1), Instant.fromEpochSeconds(hour),
                    Instant.fromEpochSeconds(hour + 3600), title = "Current programme"),
                EpgEvent.create(EventId(111), ChannelId(1), Instant.fromEpochSeconds(hour + 3600),
                    Instant.fromEpochSeconds(hour + 7200), title = "Future anchor"),
            ) else listOf(EpgEvent.create(EventId(channel * 100 + 10), ChannelId(channel),
                Instant.fromEpochSeconds(hour + 1800), Instant.fromEpochSeconds(hour + 9600), title = "Crossing edge $channel"))
        })
        key(Key.DirectionDown)
        focused("Current programme")
        key(Key.DirectionRight)
        focused("Future anchor")
        val window = checkNotNull(position.position.value).windowStartSec
        key(Key.ChannelDown) // The remote's channel keys page the Guide rows.
        focused("Crossing edge 8")
        assertEquals(ChannelId(8), checkNotNull(position.position.value).channelId)
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
        assertReadableFocus()
        key(Key.ChannelUp)
        focused("Future anchor")
        assertEquals(window, checkNotNull(position.position.value).windowStartSec)
    }

    @Test fun directionDuringWindowIndexPreparationIsConsumedAndLatestPressReplayedOnce() {
        preparationPress(back = false)
    }

    @Test fun backDuringWindowIndexPreparationDropsThePendingDirection() {
        preparationPress(back = true)
    }

    private fun preparationPress(back: Boolean) {
        show()
        key(Key.DirectionDown)
        compose.onNodeWithText(title(1, 0)).assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText(title(1, 1)).assertIsFocused()
        try {
            compose.runOnIdle { preparationContext = StandardTestDispatcher() }
            compose.waitForIdle()
            keyWhilePreparing(Key.DirectionRight) // 22:00–23:00 shifts the 19:30–22:30 window.
            val before = position.position.value
            keyWhilePreparing(Key.DirectionDown)
            keyWhilePreparing(Key.DirectionRight)
            assertEquals(before, position.position.value)
            if (back) compose.onRoot().performKeyPress(KeyEvent(android.view.KeyEvent(0, 0,
                android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK, 0)))
            releasePreparation()
            compose.mainClock.advanceTimeByFrame()
            releasePreparation()
            if (back) {
                // Back down cancels the queued direction even while its release is still pending.
                compose.onNodeWithText(title(1, 2)).assertIsFocused()
                compose.onRoot().performKeyPress(KeyEvent(android.view.KeyEvent(0, 0,
                    android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK, 0)))
                releasePreparation()
                compose.mainClock.advanceTimeByFrame()
                releasePreparation()
                compose.onNodeWithTag("nav-epg").assertIsFocused()
            } else {
                compose.onNodeWithText(title(1, 3)).assertIsFocused()
                key(Key.DirectionDown)
                compose.onNodeWithText(title(2, 3)).assertIsFocused()
            }
        } finally {
            releasePreparation()
        }
    }

    @Test fun aTripToTheRailKeepsTheAnchorOfTheProgrammeItReturnsTo() {
        // Channel 1 runs one programme from 20:00 to 22:00 at the earliest edge; channel 2 is hourly.
        show(history = false, longProgramme = true, longProgrammeHours = 2, longProgrammeSuccessor = true)
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionDown)
        focused(title(2, 0))
        key(Key.DirectionRight)
        focused(title(2, 1))
        key(Key.DirectionUp)
        focused(title(1, 0))
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        key(Key.DirectionRight)
        focused(title(1, 0))
        // The rail did not move the Guide's time, so Down still undoes Up.
        key(Key.DirectionDown)
        focused(title(2, 1))
        assertNoPlayback()
    }

    @Test fun leftCrossesAnEpgGapWiderThanTheEarlierWindowToTheRowsPreviousProgramme() {
        // Five empty hours exceed the three-hour window Left would otherwise search.
        // Cached return can settle in the same frame: acquire eagerly on the controlled main looper.
        show(historyOffset = -6, coverageAcquisitionContext = Dispatchers.Main.immediate)
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionLeft)
        focused(title(1, -6))
        val start = checkNotNull(position.position.value).windowStartSec
        assertTrue("The window moves back across the gap: $start", start < hour - 5 * 3600)
        assertEquals("Earlier navigation stays local", callsAtEntry, session.calls.size)
        key(Key.DirectionRight)
        focused(title(1, 0))
        // Back over the gap, the window reaches now and acquires coverage like any later window.
        assertNoPlayback(additionalCoverageCalls = 1)
    }

    @Test fun removedHistoryDoesNotStealHeaderFocus() {
        selectHistory()
        key(Key.DirectionUp)
        dateFocused()
        removeHistory()
        dateFocused()
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertNoPlayback()
    }

    @Test fun removedHistoryDoesNotStealDrawerFocus() {
        selectHistory()
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        removeHistory()
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        key(Key.DirectionRight)
        focused(title(1, 0))
        assertNoPlayback()
    }

    @Test fun removedHistoryWithProgrammeOwnershipFallsBackToHeader() {
        selectHistory()
        removeHistory()
        compose.onNode(isFocused()).assert(hasAnyAncestor(hasTestTag("epg-screen")))
        compose.onNode(isFocused()).assert(hasAnyAncestor(hasTestTag("epg-programme-viewport")).not())
        dateFocused()
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertNoPlayback()
    }

    @Test fun rightLeavesSevenHourProgrammeForSuccessor() = longProgrammeNavigation(scale = 1f, hours = 7, reverse = false)

    @Test fun leftLeavesSevenHourProgrammeForPredecessor() = longProgrammeNavigation(scale = 1f, hours = 7, reverse = true)

    @Test fun enlargedRightLeavesFiveHourProgrammeForSuccessor() = longProgrammeNavigation(scale = 1.3f, hours = 5, reverse = false)

    @Test fun enlargedLeftLeavesFiveHourProgrammeForPredecessor() = longProgrammeNavigation(scale = 1.3f, hours = 5, reverse = true)

    @Test fun enlargedSuccessorSurvivesEqualPublicationBeforeFrontierShift() =
        pendingSuccessorNavigation(scale = 1.3f, hours = 5, publication = SuccessorPublication.BeforeShift)

    @Test fun enlargedSuccessorSurvivesEqualPublicationAtFrontierCancellation() =
        pendingSuccessorNavigation(scale = 1.3f, hours = 5, publication = SuccessorPublication.AtCancellation)

    @Test fun normalSuccessorSurvivesEqualPublicationAtFrontierCancellation() =
        pendingSuccessorNavigation(scale = 1f, hours = 7, publication = SuccessorPublication.AtCancellation)

    @Test fun enlargedSuccessorSurvivesEqualPublicationAfterFocusSafeShift() =
        pendingSuccessorNavigation(scale = 1.3f, hours = 5, publication = SuccessorPublication.AfterFocus)

    private enum class SuccessorPublication { BeforeShift, AtCancellation, AfterFocus }

    private fun pendingSuccessorNavigation(scale: Float, hours: Int, publication: SuccessorPublication) {
        val armed = AtomicBoolean(false)
        val entered = AtomicReference<Pair<List<ChannelId>, Instant>>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val autoAdvance = compose.mainClock.autoAdvance
        var rightDown = false
        try {
            show(scale = scale, longProgramme = true, longProgrammeHours = hours, longProgrammeSuccessor = true,
                beforeCoverageBatch = { ids, through ->
                    if (armed.get()) {
                        entered.set(ids to through)
                        try {
                            release.await()
                        } finally {
                            if (!currentCoroutineContext().isActive) {
                                // A cached frontier cancels its still-pending acquisition after
                                // selecting W1. Publish here without suspending cancellation.
                                if (publication == SuccessorPublication.AtCancellation) publishEqualEpg()
                                cancelled.complete(Unit)
                            }
                        }
                    }
                })
            key(Key.DirectionDown)
            focused(title(1, 0))
            val origin = checkNotNull(position.position.value)
            compose.mainClock.autoAdvance = false
            armed.set(true)
            compose.onRoot().performKeyInput { keyDown(Key.DirectionRight) }
            rightDown = true
            // No frame advances before acquisition reaches the released fake's
            // boundary. Thus the cached frontier cannot win by cancelling before entry.
            runAcquisition()
            assertNotNull("Frontier acquisition reached the held fake", entered.get())
            assertEquals((1L..6L).map(::ChannelId), entered.get().first)
            assertEquals(Instant.fromEpochSeconds(hour + (hours + 3) * 3600), entered.get().second)
            assertFalse(cancelled.isCompleted)
            assertEquals(origin, position.position.value)
            if (publication == SuccessorPublication.BeforeShift) publishEqualEpg()
            // pressKey advances 50ms even with autoAdvance=false. Split its down/up
            // around the barrier, keeping the same single complete key cycle.
            compose.onRoot().performKeyInput { advanceEventTime(50); keyUp(Key.DirectionRight) }
            rightDown = false
            compose.mainClock.autoAdvance = autoAdvance
            focused(title(1, hours))
            runAcquisition()
            assertTrue("The cached frontier cancelled the held acquisition", cancelled.isCompleted)
            assertFalse("The frontier must use cached coverage, not release the held query", release.isCompleted)
            val successor = checkNotNull(position.position.value)
            val frontierWindow = hour + hours * 3600
            assertTrue("Native focus must apply W2 after the frontier's W1", successor.windowStartSec < frontierWindow)
            assertEquals(EventId(110L + hours), successor.eventId)
            if (publication == SuccessorPublication.AfterFocus) {
                publishEqualEpg()
                compose.waitForIdle()
                focused(title(1, hours))
                assertEquals(successor, position.position.value)
            }
            assertReadableFocus()
            armed.set(false)
            key(Key.DirectionLeft)
            focused(title(1, 0))
            assertReadableFocus()
            // Cancellation happens before the fake records a query; no playback or extra
            // acquisition is permitted on the cached return to the oversized origin.
            assertNoPlayback()
        } finally {
            armed.set(false)
            release.complete(Unit)
            if (rightDown) compose.onRoot().performKeyInput { keyUp(Key.DirectionRight) }
            compose.mainClock.autoAdvance = autoAdvance
        }
    }

    private fun publishEqualEpg() {
        val observation = session.observation.value
        val snapshot = checkNotNull(observation.epgSnapshotForDisplay)
        session.publish(SessionObservation.create(
            sessionState = observation.sessionState,
            channelState = observation.channelState,
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                events = snapshot.events, historicalEvents = snapshot.historicalEvents, coverages = snapshot.coverages,
            )),
            dvrState = observation.dvrState,
            dvrConfigurationsState = observation.dvrConfigurationsState,
        ))
    }

    @Test fun enlargedDisplayAcquiresThreeHoursWithoutInitialCoverage() = enlargedCoverage(atHorizon = false)

    @Test fun enlargedHorizonAcquisitionUsesClampedThroughAndDisplayPendingKey() = enlargedCoverage(atHorizon = true)

    private fun enlargedCoverage(atHorizon: Boolean) {
        val horizon = hour + 7 * 86400
        val displayAnchor = if (atHorizon) horizon - 2 * 3600 else hour
        val eventHour = if (atHorizon) horizon - 3600 else hour
        if (atHorizon) position.save(GuidePosition(ChannelId(1), EventId(110), eventHour, displayAnchor, 0))
        val request = AtomicReference<Pair<List<ChannelId>, Instant>>()
        val settle = CompletableDeferred<Unit>()
        show(scale = 1.3f, history = false, lastOffset = 0, eventHour = eventHour, initialCoverage = false,
            playerReturn = if (atHorizon) savedTimelineReturn() else null,
            beforeCoverageBatch = { ids, through ->
                request.set(ids to through)
                settle.await()
            })
        runAcquisition()
        assertNotNull("Initial acquisition reached the held fake", request.get())
        compose.runOnIdle {
            val (ids, through) = checkNotNull(request.get())
            assertEquals((1L..6L).map(::ChannelId), ids)
            // Two hours opening half an hour before now (20:15) start at 19:30; three hours from there are acquired.
            assertEquals(Instant.fromEpochSeconds(if (atHorizon) horizon else hour + 2 * 3600 + 1800), through)
            assertTrue(checkNotNull(session.observation.value.epgSnapshotForDisplay).coverages.isEmpty())
        }
        val ticks = compose.onAllNodes(hasText("", substring = true) and hasAnyAncestor(hasTestTag("epg-time-ruler")) and
            !hasAnyAncestor(hasTestTag("epg-now-pill")),
            useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("The displayed two-hour window has four half-hour ticks", 4, ticks.size)
        assertEquals(bounds("epg-time-ruler").width / 4, ticks[1].boundsInRoot.left - ticks[0].boundsInRoot.left, 1f)
        val pendingRow = hasTestTag("guide-row-skeleton") and hasAnyAncestor(hasTestTag("epg-channel-row-3"))
        compose.onNode(pendingRow).assertIsDisplayed()
        dateFocused()
        compose.runOnIdle { settle.complete(Unit) }
        runAcquisition()
        compose.waitForIdle()
        compose.onNode(pendingRow).assertDoesNotExist()
        dateFocused()
        key(Key.DirectionDown)
        focused(title(1, 0))
        if (atHorizon) {
            assertEquals(displayAnchor, checkNotNull(position.position.value).windowStartSec)
            key(Key.DirectionRight)
            focused(title(1, 0))
            assertEquals(displayAnchor, checkNotNull(position.position.value).windowStartSec)
        }
        assertNoPlayback(additionalCoverageCalls = 1)
    }

    private fun longProgrammeNavigation(scale: Float, hours: Int, reverse: Boolean) {
        if (reverse) position.save(GuidePosition(ChannelId(1), EventId(110), hour, hour + (hours - 1) * 3600, 0))
        show(scale = scale, longProgramme = true, longProgrammeHours = hours, longProgrammeSuccessor = true,
            playerReturn = if (reverse) savedTimelineReturn() else null)
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(if (reverse) Key.DirectionLeft else Key.DirectionRight)
        focused(title(1, if (reverse) -1 else hours))
        assertReadableFocus()
        key(if (reverse) Key.DirectionRight else Key.DirectionLeft)
        focused(title(1, 0))
        assertReadableFocus()
        val additionalCoverageCalls = session.calls.drop(callsAtEntry)
            .count { it == FakeSessionCall.EPG_ACQUIRE_COVERAGE_BATCH }
        // Cached frontier coverage can cancel Right's request before the fake is entered.
        // Do not constrain that scheduling: this regression uses an ordinary key cycle.
        val allowedCoverageCalls = if (reverse) 0..0 else 0..1
        assertTrue("Unexpected coverage call count: $additionalCoverageCalls", additionalCoverageCalls in allowedCoverageCalls)
        assertNoPlayback(additionalCoverageCalls = additionalCoverageCalls)
    }

    @Test fun tagsPanelSelectsTheTagAndReturnsFocusToTheTagsButton() {
        show()
        key(Key.DirectionRight)
        tagFocused("channels-tags-button")
        key(Key.Enter)
        compose.onNodeWithTag("channels-tags-panel").assertIsDisplayed()
        tagFocused("channels-tags-all")
        key(Key.Back)
        compose.onNodeWithTag("channels-tags-panel").assertDoesNotExist()
        tagFocused("channels-tags-button")
        assertEquals(null, model.scope.value.scope.activeTagId)

        key(Key.Enter)
        tagFocused("channels-tags-all")
        key(Key.DirectionDown)
        tagFocused("channels-tags-1")
        key(Key.Enter)
        compose.onNodeWithTag("channels-tags-panel").assertDoesNotExist()
        tagFocused("channels-tags-button")
        assertEquals(ChannelTagId(1), model.scope.value.scope.activeTagId)
        // The header is named by its pane title; the tag lives on its button.
        guidePaneTitle().assertExists()
        compose.onNodeWithTag("channels-tags-button").assertContentDescriptionEquals(context().getString(
            R.string.header_button_value_description, context().getString(R.string.browse_tags), "Favourites"))
        key(Key.DirectionDown)
        focused(title(1, 0))
        assertNoPlayback(additionalCoverageCalls = 1)
    }

    @Test fun viewOptionsSwitchBetweenRegularAndDenseRows() {
        show()
        val regularHeight = bounds("epg-channel-row-1").height
        assertTrue("Regular cells show their start time",
            compose.onAllNodesWithTag("guide-cell-time", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        key(Key.Enter)
        tagFocused("guide-view-rows-regular")
        key(Key.DirectionDown)
        tagFocused("guide-view-rows-dense")
        key(Key.Enter)
        compose.waitUntil(5_000) { bounds("epg-channel-row-1").height < regularHeight }
        assertTrue("Dense rows are shorter than regular rows", bounds("epg-channel-row-1").height < regularHeight)
        compose.onAllNodesWithTag("guide-cell-time", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(GuideRows.DENSE, runBlocking { uiSettings.settings.first() }.guideView.rows)
        assertNoPlayback()
    }

    @Test fun theDateButtonOpensTheDayAndTimePanelOnTheShownDay() {
        val gridHidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility) and
            hasAnyDescendant(hasTestTag("epg-channel-row-1"))
        show()
        key(Key.Enter)
        compose.onNodeWithTag("guide-time-panel").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
        assertEquals(1, compose.onAllNodes(gridHidden, useUnmergedTree = true).fetchSemanticsNodes().size)
        tagFocused("guide-time-day-0")
        compose.onNodeWithTag("guide-time-day-0").assertIsSelected()
        compose.onNodeWithTag("guide-time-day-1").assertIsNotSelected()
        // Now carries the current clock; the times of the shown day follow without taking focus.
        compose.onNodeWithTag("guide-time-now").assert(hasText("20:15"))
        compose.onNodeWithTag("guide-time-presets").assert(hasAnyDescendant(hasText("Today") and isHeading()))
        compose.onNodeWithTag("guide-time-preset-20").assertIsNotFocused().assertIsSelected()
            .assert(hasText("20:00") and hasText("Prime time"))
        compose.onNodeWithTag("guide-time-preset-22").assertIsEnabled().assertIsNotSelected()
        assertDateButtonNow()
        assertEquals(0, playbackRequests)
    }

    @Test fun todaysEndedTimesAreDisabledInPlaceAndSkipped() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        // At 20:15 every period up to Evening has ended; the rows stay where another day has them.
        listOf("06", "12", "15", "18").forEach { compose.onNodeWithTag("guide-time-preset-$it").assertIsNotEnabled() }
        val today = (listOf("06", "12", "15", "18", "20", "22")).map { bounds("guide-time-preset-$it").top }
        key(Key.DirectionDown)
        tagFocused("guide-time-day-1")
        compose.onNodeWithTag("guide-time-presets").assert(hasAnyDescendant(hasText("Tomorrow") and isHeading()))
        listOf("06", "12", "15", "18", "20", "22").forEach { compose.onNodeWithTag("guide-time-preset-$it").assertIsEnabled() }
        assertEquals(today, listOf("06", "12", "15", "18", "20", "22").map { bounds("guide-time-preset-$it").top })
        key(Key.DirectionUp)
        tagFocused("guide-time-day-0")
        key(Key.DirectionRight)
        tagFocused("guide-time-preset-20")
        key(Key.DirectionUp)
        tagFocused("guide-time-preset-20")
        key(Key.DirectionDown)
        tagFocused("guide-time-preset-22")
        key(Key.DirectionDown)
        tagFocused("guide-time-preset-22")
        assertEquals(0, playbackRequests)
    }

    @Test fun aFocusedTimeWhosePeriodEndsWhileThePanelIsOpenKeepsFocus() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        key(Key.DirectionRight)
        tagFocused("guide-time-preset-20")
        // Prime time ends at 22:00 while it has focus; the open panel keeps it and its neighbours.
        assertTrue(SystemClock.setCurrentTimeMillis((hour + 2 * 3600 + 30) * 1000))
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("guide-time-now") and hasText("22:00")).fetchSemanticsNodes().isNotEmpty()
        }
        tagFocused("guide-time-preset-20")
        compose.onNodeWithTag("guide-time-preset-20").assertIsEnabled()
        key(Key.DirectionDown)
        tagFocused("guide-time-preset-22")
        key(Key.DirectionUp)
        tagFocused("guide-time-preset-20")
        assertEquals(0, playbackRequests)
    }

    @Test fun aDayJumpsAtTheSameTimeOfDayAndCloses() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        key(Key.DirectionDown)
        tagFocused("guide-time-day-1")
        key(Key.Enter)
        compose.onNodeWithTag("guide-time-panel").assertDoesNotExist()
        dateFocused()
        compose.waitUntil(5_000) { dateButtonShows("Tomorrow", hour + 24 * 3600) }
        // The panel reopens on the day it jumped to; OK there closes without a jump.
        key(Key.Enter)
        tagFocused("guide-time-day-1")
        compose.onNodeWithTag("guide-time-day-1").assertIsSelected()
        compose.onNodeWithTag("guide-time-day-0").assertIsNotSelected()
        key(Key.Enter)
        compose.onNodeWithTag("guide-time-panel").assertDoesNotExist()
        dateFocused()
        assertTrue(dateButtonShows("Tomorrow", hour + 24 * 3600))
        assertEquals(0, playbackRequests)
    }

    @Test fun rightEntersTheFocusedDaysTimesAndLeftOrBackReturnToTheDay() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        // Left on the days and Right on Now go nowhere.
        key(Key.DirectionLeft)
        tagFocused("guide-time-day-0")
        key(Key.DirectionDown)
        tagFocused("guide-time-day-1")
        key(Key.DirectionRight)
        // The window's 20:15 lies in Prime time; no time is checked on another day.
        tagFocused("guide-time-preset-20")
        compose.onNodeWithTag("guide-time-preset-20").assertIsNotSelected()
        // The day whose times have focus reads as selected beside the shown day.
        compose.onNodeWithTag("guide-time-day-1").assertIsSelected()
        compose.onNodeWithTag("guide-time-day-0").assertIsSelected()
        key(Key.DirectionRight)
        tagFocused("guide-time-preset-20")
        key(Key.DirectionUp)
        tagFocused("guide-time-preset-18")
        key(Key.DirectionLeft)
        tagFocused("guide-time-day-1")
        compose.onNodeWithTag("guide-time-day-1").assertIsNotSelected()
        key(Key.DirectionRight)
        tagFocused("guide-time-preset-20")
        key(Key.Back)
        compose.onNodeWithTag("guide-time-panel").assertIsDisplayed()
        tagFocused("guide-time-day-1")
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        tagFocused("guide-time-now")
        key(Key.DirectionRight)
        tagFocused("guide-time-now")
        key(Key.Back)
        compose.onNodeWithTag("guide-time-panel").assertDoesNotExist()
        dateFocused()
        assertDateButtonNow()
        assertEquals(0, playbackRequests)
    }

    @Test fun inRightToLeftTheTimesAreEnteredWithLeftAndLeftWithRight() {
        show(direction = LayoutDirection.Rtl)
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        key(Key.DirectionRight)
        tagFocused("guide-time-day-0")
        key(Key.DirectionDown)
        tagFocused("guide-time-day-1")
        key(Key.DirectionLeft)
        tagFocused("guide-time-preset-20")
        key(Key.DirectionLeft)
        tagFocused("guide-time-preset-20")
        key(Key.DirectionRight)
        tagFocused("guide-time-day-1")
        assertEquals(0, playbackRequests)
    }

    @Test fun aTimeJumpsToItsStartClosesThePanelAndKeepsTheChannel() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        key(Key.DirectionRight)
        tagFocused("guide-time-preset-20")
        key(Key.DirectionDown)
        tagFocused("guide-time-preset-22")
        key(Key.Enter)
        compose.onNodeWithTag("guide-time-panel").assertDoesNotExist()
        dateFocused()
        compose.waitUntil(5_000) { dateButtonShows("Today", hour + 2 * 3600) }
        // Past the Guide's 10 s wait for coverage after a jump: a late window request still leaves focus here.
        compose.mainClock.advanceTimeBy(11_000)
        compose.waitForIdle()
        dateFocused()
        key(Key.DirectionDown)
        focused(title(1, 2))
        assertEquals(0, playbackRequests)
    }

    @Test fun aLaterDayWhoseCoverageTimesOutSaysNotLoadedUntilCoverageArrives() {
        val tomorrow = hour + 24 * 3600
        val farRequest = CompletableDeferred<Unit>()
        try {
            show(initialCoverage = false, beforeCoverageBatch = { _, through ->
                if (through.epochSeconds > tomorrow) farRequest.await()
            })
            key(Key.Enter)
            tagFocused("guide-time-day-0")
            key(Key.DirectionDown)
            key(Key.Enter)
            compose.waitUntil(5_000) { dateButtonShows("Tomorrow", tomorrow) }
            val loading = context().getString(R.string.epg_loading)
            val notLoaded = context().getString(R.string.epg_not_loaded)
            val emptyDay = context().getString(R.string.epg_empty_day)
            compose.waitForIdle()
            assertTrue(compose.onAllNodesWithTag("guide-row-skeleton").fetchSemanticsNodes().isNotEmpty())
            compose.onAllNodesWithText(loading).assertCountEquals(0)
            // Past the Guide's 10 s wait the request proves nothing: the day is not called empty.
            compose.mainClock.advanceTimeBy(11_000)
            compose.waitUntil(5_000) { compose.onAllNodesWithText(notLoaded).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText(emptyDay).assertCountEquals(0)
            compose.onAllNodesWithTag("guide-row-skeleton").assertCountEquals(0)
            // The panel closed onto the date button; neither state takes focus.
            dateFocused()
            // Coverage through the week proves the day: its one programme shows and the other rows are empty.
            compose.runOnIdle {
                val observation = session.observation.value
                val snapshot = checkNotNull(observation.epgSnapshotForDisplay)
                val channels = (1L..12L).map(::ChannelId)
                session.publish(SessionObservation.create(
                    sessionState = observation.sessionState,
                    channelState = observation.channelState,
                    epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                        events = snapshot.events + EpgEvent.create(EventId(9_001), channelId = ChannelId(1),
                            start = Instant.fromEpochSeconds(tomorrow), stop = Instant.fromEpochSeconds(tomorrow + 3600),
                            title = "Tomorrow on Ridge Earth"),
                        historicalEvents = snapshot.historicalEvents,
                        coverages = channels.map {
                            EpgCoverage.create(it, Instant.fromEpochSeconds(hour), Instant.fromEpochSeconds(hour + 7 * 86400))
                        },
                    )),
                    dvrState = observation.dvrState,
                    dvrConfigurationsState = observation.dvrConfigurationsState,
                ))
            }
            compose.waitForIdle()
            compose.onAllNodesWithText(notLoaded).assertCountEquals(0)
            compose.onNodeWithText("Tomorrow on Ridge Earth").assertIsDisplayed()
            assertTrue(compose.onAllNodesWithText(emptyDay).fetchSemanticsNodes().isNotEmpty())
            dateFocused()
            assertEquals(0, playbackRequests)
        } finally {
            farRequest.complete(Unit)
        }
    }

    @Test fun nowJumpsBackToTheCurrentHourFromALaterDay() {
        show()
        key(Key.Enter)
        key(Key.DirectionDown)
        key(Key.Enter)
        compose.waitUntil(5_000) { dateButtonShows("Tomorrow", hour + 24 * 3600) }
        key(Key.Enter)
        tagFocused("guide-time-day-1")
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        tagFocused("guide-time-now")
        // Now shows today's times.
        compose.onNodeWithTag("guide-time-presets").assert(hasAnyDescendant(hasText("Today") and isHeading()))
        key(Key.Enter)
        compose.onNodeWithTag("guide-time-panel").assertDoesNotExist()
        dateFocused()
        compose.waitUntil(5_000) { dateButtonShowsNow() }
        assertEquals(0, playbackRequests)
    }

    @Test fun theLastDaysTimesStopAtTheWindowBounds() {
        show()
        key(Key.Enter)
        tagFocused("guide-time-day-0")
        // The week ends on day 7, whose last window starts at 17:00.
        repeat(7) { key(Key.DirectionDown) }
        tagFocused("guide-time-day-7")
        compose.onAllNodesWithTag("guide-time-day-8").assertCountEquals(0)
        listOf("06", "12", "15").forEach { compose.onNodeWithTag("guide-time-preset-$it").assertIsEnabled() }
        listOf("18", "20", "22").forEach { compose.onNodeWithTag("guide-time-preset-$it").assertIsNotEnabled() }
        // Prime time is past the bound, so Right enters at the last time before it.
        key(Key.DirectionRight)
        tagFocused("guide-time-preset-15")
        key(Key.DirectionDown)
        tagFocused("guide-time-preset-15")
        key(Key.Enter)
        compose.onNodeWithTag("guide-time-panel").assertDoesNotExist()
        compose.waitUntil(5_000) { dateButtonShows("Sun, Oct 11", hour + 7 * 24 * 3600 - 5 * 3600) }
    }

    @Test fun pendingGuideViewSaveSurvivesPreparationAuthorityChange() {
        val saveStarted = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        val saveCompleted = CompletableDeferred<Unit>()
        val dataStore = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                saveStarted.complete(Unit)
                releaseSave.await()
                return transform(data.value).also {
                    data.value = it
                    saveCompleted.complete(Unit)
                }
            }
        }
        try {
            show(headline = true, rows = GuideRows.DENSE, uiPreferences = dataStore)
            key(Key.DirectionRight)
            key(Key.DirectionRight)
            tagFocused("channels-view-button")
            key(Key.Enter)
            tagFocused("guide-view-rows-dense")
            key(Key.DirectionDown)
            tagFocused("guide-view-headline")
            key(Key.Enter)
            compose.runOnIdle {
                assertTrue("The settings write has started", saveStarted.isCompleted)
                assertFalse(saveCompleted.isCompleted)
                preparationContext = StandardTestDispatcher()
            }
            compose.waitForIdle()
            compose.runOnIdle {
                val observation = session.observation.value
                session.publish(SessionObservation.create(sessionState = SessionState.Disconnected,
                    channelState = observation.channelState, epgState = observation.epgState,
                    dvrState = observation.dvrState))
            }
            compose.waitForIdle()
            compose.onNodeWithTag("guide-view-headline").assertDoesNotExist()
            compose.onNodeWithTag("epg-timeline-content").assertDoesNotExist()
            compose.runOnIdle { releaseSave.complete(Unit) }
            compose.waitForIdle()
            compose.runOnIdle {
                assertTrue("The pending guide-view save survives the preparation authority change", saveCompleted.isCompleted)
            }
            assertFalse(runBlocking { uiSettings.settings.first() }.guideView.headline)
        } finally {
            releasePreparation()
            releaseSave.complete(Unit)
        }
    }

    @Test fun backToNowFocusesTheProgrammeOnAirWhenItArrivesAfterTheJump() {
        // 19:30-20:30, 20:30-21:30 ...: at 20:45 the hour's first programme is not the one on air.
        assertTrue(SystemClock.setCurrentTimeMillis((hour + 45 * 60) * 1000))
        val armed = AtomicBoolean(false)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            show(eventHour = hour - 1800, beforeCoverageBatch = { _, _ ->
                if (armed.get()) {
                    entered.complete(Unit)
                    release.await()
                }
            })
            key(Key.DirectionDown)
            key(Key.DirectionDown)
            focused(title(2, 1))
            val nowWindow = checkNotNull(position.position.value).windowStartSec
            repeat(3) { offset ->
                key(Key.DirectionRight)
                focused(title(2, offset + 2))
            }
            focused(title(2, 4))
            assertNotEquals(nowWindow, checkNotNull(position.position.value).windowStartSec)
            val full = checkNotNull(session.observation.value.epgSnapshotForDisplay)
            publishEvents(full.events.filterNot { it.id == EventId(211) }, full, covered = false)
            armed.set(true)
            key(Key.Back)
            runAcquisition()
            assertTrue("Back-to-now acquisition reached the held fake", entered.isCompleted)
            publishEvents(full.events, full, covered = true)
            release.complete(Unit)
            focused(title(2, 1))
            assertEquals(nowWindow, checkNotNull(position.position.value).windowStartSec)
        } finally {
            armed.set(false)
            release.complete(Unit)
        }
    }

    @Test fun filterOpensTheGenresAndAGenreDimsTheOtherProgrammes() {
        show(genres = true)
        key(Key.DirectionRight)
        tagFocused("channels-tags-button")
        key(Key.DirectionRight)
        tagFocused("guide-filter-button")
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(context().getString(R.string.guide_filter))
        key(Key.Enter)
        compose.onNodeWithTag("guide-genre-panel").assertIsDisplayed()
        compose.onNodeWithText(context().getString(R.string.guide_filter_title)).assertIsDisplayed()
        tagFocused("guide-genre-all")
        // The list composes only the rows in view; each family row among them carries its color.
        val familyRows = compose.onAllNodes(SemanticsMatcher("genre family row") {
            it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }.let { tag ->
                tag != null && tag.startsWith("guide-genre-") && tag != "guide-genre-panel" && tag != "guide-genre-all"
            }
        }).fetchSemanticsNodes().size
        assertTrue("Family rows are in view", familyRows >= 4)
        assertEquals("Each family carries its color", familyRows,
            compose.onAllNodesWithTag("option-dot", useUnmergedTree = true).fetchSemanticsNodes().size)
        key(Key.Back)
        compose.onNodeWithTag("guide-genre-panel").assertDoesNotExist()
        tagFocused("guide-filter-button")
        key(Key.Enter)
        tagFocused("guide-genre-all")
        repeat(4) { key(Key.DirectionDown) }
        tagFocused("guide-genre-sport")
        key(Key.Enter)
        compose.onNodeWithTag("guide-genre-panel").assertDoesNotExist()
        tagFocused("guide-filter-button")
        // The button names the genre after its color dot; the pane title stays the destination's.
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(context().getString(
            R.string.header_button_value_description, context().getString(R.string.guide_filter), "Sport"))
        compose.onNode(hasTestTag("header-button-dot") and hasAnyAncestor(hasTestTag("guide-filter-button")), useUnmergedTree = true)
            .assertIsDisplayed()
        guidePaneTitle().assertExists()
        assertEquals("The tag stays", null, model.scope.value.scope.activeTagId)
        key(Key.Enter)
        tagFocused("guide-genre-sport")
        key(Key.Back)
        tagFocused("guide-filter-button")

        // Programmes outside Sport stay in the grid, reachable and named as such.
        fun cell(channel: Int) = hasContentDescription(title(channel, 0), substring = true)
        val outside = hasStateDescription("Not Sport")
        key(Key.DirectionDown)
        focused(title(1, 0))
        compose.onNode(cell(1) and outside).assertDoesNotExist()
        key(Key.DirectionDown)
        compose.onNode(cell(2) and outside).assertIsFocused()
        compose.onNode(cell(4) and outside).assertExists()
        // The label stays the plain cell description; the state says the genre, never "disabled".
        val label = compose.onNode(cell(2) and outside).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(label, label.endsWith(context().getString(R.string.epg_state_now)) || label.endsWith(context().getString(R.string.epg_state_past)) ||
            label.endsWith(context().getString(R.string.epg_state_future)))
        compose.onNode(hasContentDescription("not Sport", substring = true, ignoreCase = true)).assertDoesNotExist()
        compose.onNode(cell(2) and outside).assertIsEnabled()
        assertNoPlayback()
    }

    @Test fun aFocusedFilterOutlivesAReloadWithoutGenresUntilFocusMovesOn() {
        show(genres = true, tags = false)
        compose.onNodeWithTag("channels-tags-button").assertDoesNotExist()
        key(Key.DirectionRight)
        tagFocused("guide-filter-button")
        key(Key.Enter)
        compose.onNodeWithTag("guide-genre-panel").assertIsDisplayed()
        tagFocused("guide-genre-all")
        key(Key.Back)
        tagFocused("guide-filter-button")
        assertNoPlayback()
        val full = compose.runOnIdle { checkNotNull(session.observation.value.epgSnapshotForDisplay) }
        fun List<EpgEvent>.plain() = map {
            EpgEvent.create(it.id, channelId = it.channelId, start = it.start, stop = it.stop,
                title = it.title, summary = it.summary)
        }
        val plain = full.events.plain()
        publishEvents(plain, EpgSnapshot.create(events = plain, historicalEvents = full.historicalEvents.plain(),
            coverages = full.coverages), covered = true)
        compose.waitForIdle()
        tagFocused("guide-filter-button")
        key(Key.DirectionRight)
        tagFocused("channels-view-button")
        compose.onNodeWithTag("guide-filter-button").assertDoesNotExist()
        assertNoPlayback()
    }

    private fun selectHistory() {
        show()
        key(Key.DirectionDown)
        focused(title(1, 0))
        key(Key.DirectionLeft)
        focused(title(1, -1))
    }

    private fun removeHistory() {
        val snapshot = checkNotNull(session.observation.value.epgSnapshotForDisplay)
        publishEvents(snapshot.events, EpgSnapshot.create(events = snapshot.events, coverages = snapshot.coverages), covered = true)
        compose.waitForIdle()
        compose.onNodeWithText(title(1, 0)).assertExists()
        compose.onAllNodes(hasText(title(1, -1))).assertCountEquals(0)
    }

    private fun guidePaneTitle() =
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, context().getString(R.string.epg_title)))

    private fun programme(id: Long, channel: Long, start: Long, end: Long, name: String) =
        EpgEvent.create(EventId(id), ChannelId(channel), Instant.fromEpochSeconds(hour + start * 60),
            Instant.fromEpochSeconds(hour + end * 60), title = name)

    private fun dateButtonShowsNow() =
        compose.onAllNodes(hasTestTag("guide-date-button") and hasText(dateButtonNowText())).fetchSemanticsNodes().isNotEmpty()
}
