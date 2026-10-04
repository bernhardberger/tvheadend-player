package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InProgressRecordingResumeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun anInProgressRecordingWithASavedPositionResumesThere() {
        val starts = openDetails(playPositionSeconds = 754L)

        compose.onNodeWithContentDescription("Resume from 12 minutes, 34 seconds").assertExists()
        compose.onNodeWithTag("recording-details-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.RESUME), starts) }
    }

    @Test
    fun anInProgressRecordingCanStillBePlayedFromTheBeginning() {
        val starts = openDetails(playPositionSeconds = 754L)

        compose.onNodeWithTag("recording-details-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-details-beginning").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.START_OVER), starts) }
    }

    @Test
    fun anInProgressRecordingWithoutASavedPositionKeepsPlay() = assertPlayOnly(playPositionSeconds = null)

    @Test
    fun anInProgressRecordingAtItsStartKeepsPlay() = assertPlayOnly(playPositionSeconds = 0L)

    private fun assertPlayOnly(playPositionSeconds: Long?) {
        val starts = openDetails(playPositionSeconds)

        compose.onNodeWithTag("recording-details-resume").assertDoesNotExist()
        compose.onNodeWithTag("recording-details-beginning").assertDoesNotExist()
        compose.onNodeWithTag("recording-details-play").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.START_OVER), starts) }
    }

    private fun openDetails(playPositionSeconds: Long?): List<RecordingPlaybackStart> {
        val starts = mutableListOf<RecordingPlaybackStart>()
        val observation = observation(playPositionSeconds)
        val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> DvrMutationResult.NotReady },
            stopEntry = { _, _ -> DvrMutationResult.NotReady },
            cancelEntry = { _, _ -> DvrMutationResult.NotReady },
            deleteEntry = { _, _ -> DvrMutationResult.NotReady },
        )
        val state = RecordingsScreenState().apply { mode.value = DvrLibraryMode.SCHEDULE }
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreenContent(
                    observation = observation,
                    state = state,
                    imageLoader = loader,
                    dvrMutationActions = actions,
                    notices = androidx.compose.runtime.remember { at.bernhardberger.tvhplayer.ui.notifications.AppNoticeQueue({ 0L }, {}) },
                    onPlayRecording = { _, start -> starts += start },
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("recording-list-entry-1")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recording-list-entry-1").requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        return starts
    }

    private fun observation(playPositionSeconds: Long?): SessionObservation {
        val now = System.currentTimeMillis()
        val entry = DvrEntry.create(
            id = DvrEntryId(1),
            title = "Evening News",
            state = DvrEntryState.RECORDING,
            start = Instant.fromEpochMilliseconds(now - 1_800_000),
            stop = Instant.fromEpochMilliseconds(now + 1_800_000),
            path = "evening-news.ts",
            files = listOf(
                DvrRecordingFile(fileId = null, path = "evening-news.ts", start = null, stop = null, sizeBytes = null)
            ),
            playPosition = playPositionSeconds?.seconds,
        )
        return SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.ALLOWED,
                )
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))),
        )
    }
}
