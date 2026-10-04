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
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeContext
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeIcon
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeQueue
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    @Test fun stopFailureUsesGlobalNoticeWithoutInlineResult() = actionFeedback(DvrMutationResult.AccessDenied)
    @Test fun acceptedStopWaitsForServerEvent() = actionFeedback(DvrMutationResult.AcceptedButUnconfirmed(Unit))
    @Test fun confirmedStopWaitsForServerEvent() = actionFeedback(DvrMutationResult.Confirmed(Unit))
    @Test fun dismissedDetailsDiscardLateActionFailure() = actionFeedback(DvrMutationResult.AccessDenied, dismiss = true)

    private fun actionFeedback(result: DvrMutationResult<Unit>, dismiss: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val session = FakeTvheadendSession(observation(DvrEntryState.RECORDING))
        val state = RecordingsScreenState().apply { mode.value = DvrLibraryMode.SCHEDULE }
        val loader = ImageLoader.Builder(context).build()
        val notices = AppNoticeQueue({ 0L }, { AppNoticeContext(1, session.observation.value.currentSession?.generationIdentity) })
        val response = CompletableDeferred<DvrMutationResult<Unit>>()
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> DvrMutationResult.NotReady },
            stopEntry = { _, _ -> response.await() },
            cancelEntry = { _, _ -> DvrMutationResult.NotReady },
            deleteEntry = { _, _ -> DvrMutationResult.NotReady },
        )
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreenContent(observation = session.observation.collectAsState().value,
                    currentObservation = { session.observation.value }, state = state, imageLoader = loader,
                    dvrMutationActions = actions, notices = notices)
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("recording-list-entry-1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-details-stop").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-confirmation-confirm").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        if (dismiss) {
            compose.onNodeWithTag("recording-details-close").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithTag("recording-details-panel").assertDoesNotExist()
        }
        compose.runOnIdle { response.complete(result) }
        compose.waitForIdle()
        if (!dismiss && result == DvrMutationResult.AccessDenied) {
            val notice = notices.state.value.pending.single()
            assertEquals(R.string.recording_action_failed, notice.message)
            assertEquals(R.string.recording_action_permission, notice.detailMessage)
            assertEquals(AppNoticeIcon.WARNING, notice.icon)
            compose.onNodeWithText(context.getString(R.string.recording_action_permission)).assertDoesNotExist()
            compose.onNodeWithTag("recording-details-stop").assertIsFocused()
        } else assertTrue(notices.state.value.pending.isEmpty())
    }

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
                    notices = androidx.compose.runtime.remember { at.bernhardberger.tvhplayer.ui.notifications.AppNoticeQueue({ 0L }, {}) },
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
