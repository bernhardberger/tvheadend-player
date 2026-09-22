package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.playback.currentRecordingPlaybackSelection
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayerStatusTagsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun currentDvrUpdatesRetireGrowingStatusWithoutChangingPlaybackIntent() {
        val id = DvrEntryId(4)
        fun observation(state: DvrEntryState?) = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(
                entries = state?.let { listOf(DvrEntry.create(id = id, state = it)) } ?: emptyList(),
            )),
        )
        val fake = FakeTvheadendSession(observation(DvrEntryState.RECORDING))
        val selection = currentRecordingPlaybackSelection(fake.observation.value, id)!!
        org.junit.Assert.assertFalse(currentRecordingIsGrowing(fake.observation.value, null))
        var current by mutableStateOf(fake.observation.value)
        var paused by mutableStateOf(false)
        compose.setContent { TVHeadendPlayerTheme {
            PlayerStatusTags(paused, recordingPlayback = true, growing = currentRecordingIsGrowing(current, selection))
        } }
        compose.onNodeWithContentDescription("Recording in progress. Playing").assertExists()
        compose.onNodeWithTag("player-recording-now").assertDoesNotExist()
        compose.runOnIdle { paused = true }
        compose.onNodeWithContentDescription("Recording in progress. Paused").assertExists()
        for (state in listOf(DvrEntryState.COMPLETED, DvrEntryState.INVALID, null)) {
            compose.runOnIdle { fake.publish(observation(state)); current = fake.observation.value }
            org.junit.Assert.assertSame(selection.currentSession, current.currentSession)
            compose.onNodeWithTag("player-clock-status").assertDoesNotExist()
            compose.onNodeWithTag("player-recording-now").assertDoesNotExist()
        }
        compose.runOnIdle { fake.publish(observation(DvrEntryState.RECORDING)); current = fake.observation.value }
        compose.onNodeWithContentDescription("Recording in progress. Paused").assertExists()
        compose.runOnIdle { fake.replaceGeneration(observation(DvrEntryState.RECORDING)); current = fake.observation.value }
        compose.onNodeWithTag("player-clock-status").assertDoesNotExist()
    }
    @Test fun explicitDurations() {
        val values = listOf(-1L, 0, 59_000, 60_000, 203_000, 4_320_000)
        assertEquals(listOf("0sec", "0sec", "59sec", "1min 0sec", "3min 23sec", "1hr 12min"),
            values.map { statusDuration(it, "hr", "min", "sec") })
        assertEquals(listOf("0Sek", "0Sek", "59Sek", "1Min 0Sek", "3Min 23Sek", "1Std 12Min"),
            values.map { statusDuration(it, "Std", "Min", "Sek") })
        assertEquals("3m 23s", statusDuration(203_000, "h", "m", "s"))
    }
    @Test fun independentRecordingAndUnknownTiming() {
        var state by mutableStateOf(AppTimeshiftState(available = true, timingKnown = true, liveEdgeMs = 203_000))
        var paused by mutableStateOf(false)
        var recording by mutableStateOf(true)
        var saved by mutableStateOf(false)
        var presented by mutableStateOf(true)
        compose.setContent { TVHeadendPlayerTheme { PlayerStatusTags(paused, timeshift = state,
            recordingNow = recording, recordingPlayback = saved, playbackPresented = presented) } }
        compose.onNodeWithContentDescription("3m 23s behind live. Playing").assertExists()
        compose.onNodeWithContentDescription("Recording now").assertExists()
        compose.onNodeWithTag("player-recording-now").assertExists()
        compose.onNodeWithTag("player-clock-status").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Focused))
        compose.runOnIdle { paused = true }
        compose.onNodeWithContentDescription("3m 23s behind live. Paused").assertExists()
        compose.runOnIdle { state = state.copy(positionMs = 203_000) }
        compose.onNodeWithContentDescription("Live. Paused").assertExists()
        compose.runOnIdle { paused = false }
        compose.onNodeWithContentDescription("Live. Playing").assertExists()
        compose.runOnIdle { state = state.copy(timingKnown = false) }
        compose.onNodeWithTag("player-clock-status").assertDoesNotExist()
        compose.onNodeWithContentDescription("Recording now").assertExists()
        compose.runOnIdle { state = state.copy(available = false) }
        compose.onNodeWithTag("player-clock-status").assertDoesNotExist()
        compose.runOnIdle { saved = true }
        compose.onNodeWithTag("player-clock-status").assertDoesNotExist()
        compose.onNodeWithTag("player-recording-now").assertDoesNotExist()
        compose.runOnIdle { saved = false; recording = false; state = state.copy(available = true, timingKnown = true, positionMs = 203_000) }
        compose.onNodeWithContentDescription("Live. Playing").assertExists()
        compose.onNodeWithTag("player-recording-now").assertDoesNotExist()
        compose.runOnIdle { saved = false; presented = false; state = state.copy(available = true, timingKnown = true) }
        compose.onNodeWithTag("player-clock-status").assertDoesNotExist()
        compose.onNodeWithTag("player-recording-now").assertDoesNotExist()
    }
}
