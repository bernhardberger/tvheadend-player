package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.components.scheduleInitialEvent
import kotlinx.coroutines.test.runTest
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

    @Test fun guideOwnsTheCycleAndBackRestoresScheduleAction() = runTest {
        val layers = LivePlayerLayerState(this, autoHideTimeoutMillis = 5000)
        assertTrue(layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, true))!!)
        assertTrue(layers.scheduleOpen)
        assertTrue(layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, true, 1))!!)
        assertTrue(layers.scheduleOpen)
        assertTrue(layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_GUIDE, false))!!)
        assertEquals(PlayerForegroundLayer.SCHEDULE, playerForegroundLayer(layers.foregroundContext()))
        assertFalse(playerRootFocusRequired(PlayerForegroundLayer.SCHEDULE))
        assertEquals(PlayerBackAction.CLOSE_SCHEDULE, playerBackAction(PlayerSurface.LIVE, PlayerForegroundLayer.SCHEDULE))
        layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_BACK, true))
        assertFalse(layers.scheduleOpen)
        assertEquals("player-schedule", layers.restoreChannelAction)
        assertTrue(layers.chrome.controlsVisible)
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
        assertEquals(false, layers.handleScheduleKey(key(AndroidKeyEvent.KEYCODE_VOLUME_UP, true)))
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

    private fun event(id: Int, start: Long, stop: Long) = EpgEvent.create(EventId(id.toLong()), ChannelId(1),
        Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(stop), title = "Programme $id")
}
