package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingConfirmationStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelDismissesAndFocusesStopWhenRecordingStarts() = transition(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING)
    @Test fun stopDismissesWhenRecordingCompletes() = transition(DvrEntryState.RECORDING, DvrEntryState.COMPLETED)
    @Test fun cancelDismissesWhenEntryRemoved() = transition(DvrEntryState.SCHEDULED, null)
    @Test fun stopDismissesWhenEntryRemoved() = transition(DvrEntryState.RECORDING, null)
    @Test fun cancelRechecksLatestObservationBeforeDispatch() = transition(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING, true)
    @Test fun stopRechecksLatestObservationBeforeDispatch() = transition(DvrEntryState.RECORDING, DvrEntryState.COMPLETED, true)

    private fun transition(before: DvrEntryState, after: DvrEntryState?, clickBeforeRecomposition: Boolean = false) {
        val session = FakeTvheadendSession(observation(before))
        val state = RecordingsScreenState().apply { mode.value = DvrLibraryMode.SCHEDULE }
        val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
        var calls = 0
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> calls++; DvrMutationResult.NotReady },
            stopEntry = { _, _ -> calls++; DvrMutationResult.NotReady },
            cancelEntry = { _, _ -> calls++; DvrMutationResult.NotReady },
            deleteEntry = { _, _ -> calls++; DvrMutationResult.NotReady },
        )
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreenContent(
                    observation = session.observation.collectAsState().value,
                    currentObservation = { session.observation.value },
                    state = state, imageLoader = loader, dvrMutationActions = actions,
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("recording-list-entry-1")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recording-list-entry-1").requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        val action = if (before == DvrEntryState.SCHEDULED) "cancel" else "stop"
        compose.onNodeWithTag("recording-details-$action").requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-confirmation-back").assertIsFocused()
        val confirm = compose.onNodeWithTag("recording-confirmation-confirm").requestFocus()
        if (clickBeforeRecomposition) {
            val click = requireNotNull(confirm.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
            compose.runOnIdle {
                session.publish(observation(after))
                click()
            }
        } else {
            confirm.performKeyInput { keyDown(Key.DirectionCenter) }
            compose.runOnIdle { session.publish(observation(after)) }
        }
        compose.onNodeWithTag("recording-confirmation-confirm").assertDoesNotExist()
        if (after == DvrEntryState.RECORDING) {
            compose.onNodeWithTag("recording-details-stop").assertIsFocused()
        }
        compose.onAllNodes(isFocused()).assertCountEquals(1)
        if (!clickBeforeRecomposition) compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-confirmation-confirm").assertDoesNotExist()
        assertEquals(0, calls)
    }

    private fun observation(state: DvrEntryState?) = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
        )),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = state?.let {
            listOf(DvrEntry.create(id = DvrEntryId(1), title = "Recording", state = it,
                start = kotlin.time.Instant.fromEpochMilliseconds(System.currentTimeMillis() + 3_600_000),
                stop = kotlin.time.Instant.fromEpochMilliseconds(System.currentTimeMillis() + 7_200_000)))
        }.orEmpty())),
    )
}
