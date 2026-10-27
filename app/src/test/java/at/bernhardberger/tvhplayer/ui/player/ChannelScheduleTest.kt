package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.components.scheduleInitialEvent
import kotlinx.coroutines.test.runTest
import java.time.ZoneId
import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChannelScheduleTest {
    @Test fun historyIncludesOnlyOverlappingBufferedProgrammesOnTheChannel() {
        val events = listOf(event(1, 0, 10), event(2, 10, 20), event(3, 20, 30), event(4, 30, 40))
        assertEquals(listOf(3L, 4L).map(::EventId), channelScheduleEvents(events, ChannelId(1), 25, null).map { it.id })
        assertEquals(listOf(2L, 3L, 4L).map(::EventId), channelScheduleEvents(events.reversed() + events[1], ChannelId(1), 25, 10).map { it.id })
        assertTrue(channelScheduleEvents(events, ChannelId(2), 25, 10).isEmpty())
    }

    @Test fun initialFocusFollowsViewerOrLiveAndSkipsGaps() {
        val events = listOf(event(1, 0, 10), event(2, 20, 30), event(3, 40, 50))
        assertEquals(events[1], scheduleInitialEvent(events, 25, 25))
        assertEquals(events[0], scheduleInitialEvent(events, 5, 25))
        assertEquals(events[1], scheduleInitialEvent(events, null, 25))
        assertEquals(events[2], scheduleInitialEvent(events, 35, 25))
        assertNull(scheduleInitialEvent(emptyList(), 25, 25))
    }

    @Test fun guideOwnsTheCycleAndBackRestoresInvokingAction() = runTest {
        val layers = LivePlayerLayerState(this, autoHideTimeoutMillis = 5000)
        layers.showControls()
        layers.onActionFocused("player-settings")
        assertTrue(layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, true))!!)
        assertTrue(layers.scheduleOpen)
        assertTrue(layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, true, 1))!!)
        assertTrue(layers.scheduleOpen)
        assertTrue(layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, false))!!)
        assertEquals(PlayerForegroundLayer.SCHEDULE, playerForegroundLayer(layers.foregroundContext()))
        assertFalse(playerRootFocusRequired(PlayerForegroundLayer.SCHEDULE))
        assertEquals(PlayerBackAction.CLOSE_SCHEDULE, playerBackAction(PlayerSurface.LIVE, PlayerForegroundLayer.SCHEDULE))
        // The panel decides whether Back returns to its day tab or closes it.
        assertEquals(false, layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_BACK, true)))
        assertTrue(layers.scheduleOpen)
        layers.core.beginOpeningKeyCycle(AndroidKeyEvent.KEYCODE_BACK)
        assertEquals(true, layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_BACK, true, 1)))
        assertEquals(true, layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_BACK, false)))
        assertNull(layers.core.revealingKeyCode)
        layers.closeSchedule()
        assertFalse(layers.scheduleOpen)
        assertEquals("player-settings", layers.core.restoreControl)
        assertTrue(layers.core.chrome.controlsVisible)
        layers.dispose()
    }

    @Test fun detailsBackKeepsPanelAndZapOrGuideClosesIt() = runTest {
        val layers = LivePlayerLayerState(this, autoHideTimeoutMillis = 5000)
        layers.openSchedule()
        layers.showScheduleDetails(event(1, 0, 10))
        assertEquals(false, layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_BACK, true)))
        layers.showScheduleDetails(null)
        assertTrue(layers.scheduleOpen)
        assertEquals(false, layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_MENU, true)))
        assertEquals(true, layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_VOLUME_UP, true)))
        assertNull(layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_CHANNEL_UP, true)))
        assertFalse(layers.scheduleOpen)
        layers.openSchedule()
        assertNull(layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_1, true)))
        assertFalse(layers.scheduleOpen)
        layers.openSchedule()
        layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, true))
        assertFalse(layers.scheduleOpen)
        layers.dispose()
    }

    private fun key(code: Int, down: Boolean, repeat: Int = 0) = KeyEvent(
        AndroidKeyEvent(0, 0, if (down) AndroidKeyEvent.ACTION_DOWN else AndroidKeyEvent.ACTION_UP, code, repeat))

    @Test fun readyEmptyIsFinalAndDisconnectedEmptyOffersRetry() {
        assertFalse(scheduleLoading(ConnectionUiState.Ready, EpgRepositoryState.Empty))
        assertFalse(scheduleOffline(ConnectionUiState.Ready, EpgRepositoryState.Empty))
        assertTrue(scheduleLoading(ConnectionUiState.Connecting, EpgRepositoryState.Empty))
        assertTrue(scheduleOffline(ConnectionUiState.Reconnecting, EpgRepositoryState.Empty))
        assertFalse(scheduleLoading(ConnectionUiState.Reconnecting, EpgRepositoryState.Empty))
        assertTrue(scheduleOffline(ConnectionUiState.CredentialUnavailable, EpgRepositoryState.Empty))
    }

    @Test fun laterDaysLoadOnlyWhileTheChannelRequestRunsShortOfTheGuidesReach() {
        val opened = 1789246200L
        val through = scheduleCoverageThroughSec(opened, ZoneId.of("UTC"))
        assertTrue(through > opened + 6 * 86_400 && through <= opened + 7 * 86_400)
        fun epg(channel: Long, knownTo: Long) = EpgRepositoryState.Current(EpgSnapshot.create(events = emptyList(),
            coverages = listOf(EpgCoverage.create(ChannelId(channel), Instant.fromEpochSeconds(opened), Instant.fromEpochSeconds(knownTo)))))
        val ready = ConnectionUiState.Ready
        assertTrue(scheduleCoveragePending(ready, epg(1, opened + 3600), ChannelId(1), through, requestPending = true))
        assertTrue(scheduleCoveragePending(ready, epg(2, through), ChannelId(1), through, requestPending = true))
        assertFalse(scheduleCoveragePending(ready, epg(1, through), ChannelId(1), through, requestPending = true))
        // A settled request shows what the server holds; offline keeps its own Retry instead.
        assertFalse(scheduleCoveragePending(ready, epg(1, opened + 3600), ChannelId(1), through, requestPending = false))
        assertFalse(scheduleCoveragePending(ConnectionUiState.Reconnecting, epg(1, opened + 3600), ChannelId(1), through, requestPending = true))
    }

    @Test fun guidePreparesOnceAndNeverOpensOverRecoveryOrConfirmation() = runTest {
        val layers = LivePlayerLayerState(this, 5000)
        var prepared = 0
        for (layer in listOf(PlayerForegroundLayer.RECOVERY, PlayerForegroundLayer.TERMINAL_ERROR, PlayerForegroundLayer.CONFIRMATION)) {
            assertTrue(layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, true), layer) { prepared++ }!!)
            assertFalse(layers.scheduleOpen)
        }
        layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, true)) { prepared++ }
        layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, true, 1)) { prepared++ }
        layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, false)) { prepared++ }
        assertEquals(1, prepared)
        layers.dispose()
    }

    @Test fun guideFromInfoReturnsToTheCoveredLayerButOptionsRemainModal() = runTest {
        val layers = LivePlayerLayerState(this, 5000)
        layers.openInfo(fromKey = true)
        layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, true))
        assertTrue(layers.scheduleOpen)
        assertFalse(layers.core.infoOpen)
        layers.closeSchedule()
        assertTrue(layers.core.infoOpen)
        layers.closeInfo()
        layers.core.endOpeningKeyCycle(AndroidKeyEvent.KEYCODE_GUIDE)
        for (page in PlaybackOptionsPage.entries) for (quick in listOf(false, true)) {
            if (quick && page !in listOf(PlaybackOptionsPage.AUDIO, PlaybackOptionsPage.SUBTITLES)) continue
            if (quick) layers.core.showQuickList(page) else layers.core.showOptionsPage(page)
            for (event in listOf(key(AndroidKeyEvent.KEYCODE_GUIDE, true),
                    key(AndroidKeyEvent.KEYCODE_GUIDE, true, 1), key(AndroidKeyEvent.KEYCODE_GUIDE, false))) {
                assertEquals(true, layers.handleScheduleKey(event) { error("Modal GUIDE must not prepare Schedule") })
                assertFalse(layers.scheduleOpen)
                assertEquals(page, layers.core.optionsPage)
                assertEquals(quick, layers.core.optionsQuickList)
            }
        }
        layers.dispose()
    }

    @Test fun sheetKeysNeverReachPlaybackSeekOrReveal() = runTest {
        val layers = LivePlayerLayerState(this, 5000)
        layers.openSchedule()
        for (code in listOf(AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_MEDIA_STOP,
                AndroidKeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, AndroidKeyEvent.KEYCODE_CAPTIONS,
                AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD, AndroidKeyEvent.KEYCODE_MEDIA_REWIND)) {
            for (down in listOf(true, false)) assertEquals(true, layers.handleScheduleKey(key(code, down)))
        }
        for (code in listOf(AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)) {
            assertEquals(false, layers.handleScheduleKey(key(code, true)))
        }
        assertTrue(layers.scheduleOpen)
        assertFalse(layers.core.chrome.controlsVisible)
        layers.dispose()
    }

    @Test fun remoteOverlayBackPreservesBareOrControlsOriginAndRestartsTimer() = runTest {
        for (controls in listOf(false, true)) {
            for (code in listOf(AndroidKeyEvent.KEYCODE_INFO, AndroidKeyEvent.KEYCODE_GUIDE,
                    AndroidKeyEvent.KEYCODE_TV_CONTENTS_MENU, AndroidKeyEvent.KEYCODE_TV_NUMBER_ENTRY,
                    AndroidKeyEvent.KEYCODE_BOOKMARK)) {
                val layers = LivePlayerLayerState(this, 5000)
                if (controls) layers.showControls()
                layers.core.chrome.updateAutoHideEligibility(true)
                when (code) {
                    AndroidKeyEvent.KEYCODE_INFO -> layers.openInfo(fromKey = true)
                    AndroidKeyEvent.KEYCODE_GUIDE -> layers.openSchedule(fromKey = true)
                    else -> layers.openChannelDrawer(fromKey = true)
                }
                testScheduler.advanceTimeBy(6000)
                when (code) {
                    AndroidKeyEvent.KEYCODE_INFO -> layers.closeInfo()
                    AndroidKeyEvent.KEYCODE_GUIDE -> layers.closeSchedule()
                    else -> layers.dismissChannelDrawer()
                }
                assertEquals("key=$code", controls, layers.core.chrome.controlsVisible)
                if (controls) {
                    layers.core.chrome.updateAutoHideEligibility(true)
                    testScheduler.advanceTimeBy(4999)
                    assertTrue(layers.core.chrome.controlsVisible)
                    testScheduler.advanceTimeBy(2)
                    assertFalse(layers.core.chrome.controlsVisible)
                }
                layers.dispose()
            }
        }
    }

    private fun event(id: Int, start: Long, stop: Long) = EpgEvent.create(EventId(id.toLong()), ChannelId(1),
        Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(stop), title = "Programme $id")
}
