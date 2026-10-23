package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.notices.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import kotlinx.coroutines.CompletableDeferred
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
    @Test fun stopConfirmationSurvivesARecordingThatReportsErrors() =
        stopDispatch(before = DvrEntryState.RECORDING, after = DvrEntryState.RECORDING_ERROR)
    @Test fun runningRecordingWithErrorsIsListedInScheduleAndStopped() =
        stopDispatch(before = DvrEntryState.RECORDING_ERROR, after = DvrEntryState.RECORDING_ERROR)

    @Test fun actionFailureIsPostedAfterDetailsClose() = delayedFailure(reopen = false)
    @Test fun actionFailureIsPostedAfterDetailsReopen() = delayedFailure(reopen = true)

    @Test fun menuWatchStateRejectsReplacedSession() = watchStateBeforeRecomposition(false) {
        it.replaceGeneration(observation(DvrEntryState.COMPLETED))
    }
    @Test fun detailsWatchStateRejectsReplacedSession() = watchStateBeforeRecomposition(true) {
        it.replaceGeneration(observation(DvrEntryState.COMPLETED))
    }
    @Test fun menuWatchStateRejectsRevokedWritePermission() = watchStateBeforeRecomposition(false) {
        it.publish(observation(DvrEntryState.COMPLETED, dvrWrite = CapabilityAccess.DENIED))
    }
    @Test fun detailsWatchStateRejectsRevokedWritePermission() = watchStateBeforeRecomposition(true) {
        it.publish(observation(DvrEntryState.COMPLETED, dvrWrite = CapabilityAccess.DENIED))
    }
    @Test fun menuWatchStateRejectsRemovedEntry() = watchStateBeforeRecomposition(false) {
        it.publish(observation(null))
    }
    @Test fun detailsWatchStateRejectsRemovedEntry() = watchStateBeforeRecomposition(true) {
        it.publish(observation(null))
    }
    @Test fun menuWatchStateRejectsChangedWatchState() = watchStateBeforeRecomposition(false) {
        it.publish(observation(DvrEntryState.COMPLETED, playCount = 1))
    }
    @Test fun detailsWatchStateRejectsChangedWatchState() = watchStateBeforeRecomposition(true) {
        it.publish(observation(DvrEntryState.COMPLETED, playCount = 1))
    }
    @Test fun menuWatchStateDispatchesOnceWhileStillAdmitted() =
        watchStateBeforeRecomposition(false, expectedWatched = listOf(true)) {}

    private fun watchStateBeforeRecomposition(
        fromDetails: Boolean,
        expectedWatched: List<Boolean> = emptyList(),
        beforeClick: (FakeTvheadendSession) -> Unit,
    ) {
        val session = FakeTvheadendSession(observation(DvrEntryState.COMPLETED, folder = emptyList()))
        val calls = mutableListOf<Boolean>()
        val actions = DvrMutationActions(
            updateWatchStateCommand = { _, id, watched ->
                assertEquals(DvrEntryId(1), id)
                calls += watched
                DvrProgressResult.Accepted
            },
            scheduleEntry = { _, _ -> error("Unexpected schedule") },
            stopEntry = { _, _ -> error("Unexpected stop") },
            cancelEntry = { _, _ -> error("Unexpected cancel") },
            deleteEntry = { _, _ -> error("Unexpected delete") },
        )
        val state = RecordingsScreenState()
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, session.observation.value.currentSession?.generationIdentity) }
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreenContent(observation = session.observation.collectAsState().value,
                    currentObservation = { session.observation.value }, state = state,
                    dvrMutationActions = actions, notices = notices)
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("recording-list-entry-1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        if (fromDetails) {
            compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        }
        val markWatched = compose.onNodeWithText("Mark as watched").requestFocus()
        val click = requireNotNull(markWatched.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        compose.runOnIdle {
            beforeClick(session)
            click()
        }
        compose.runOnIdle { assertEquals(expectedWatched, calls) }
    }

    @Test fun confirmedDetailsDeleteClosesAndFocusesTheNeighbour() {
        val neighbour = DvrEntry.create(DvrEntryId(2), title = "Neighbour", state = DvrEntryState.COMPLETED, folder = emptyList())
        val entry = DvrEntry.create(DvrEntryId(1), title = "Recording", state = DvrEntryState.COMPLETED, folder = emptyList())
        val initial = observation(DvrEntryState.COMPLETED)
        fun snapshot(entries: List<DvrEntry>) = SessionObservation.create(
            sessionState = initial.sessionState, channelState = initial.channelState, epgState = initial.epgState,
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries)))
        val session = FakeTvheadendSession(snapshot(listOf(entry, neighbour)))
        var calls = 0
        val actions = DvrMutationActions(
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
            scheduleEntry = { _, _ -> error("unexpected schedule") },
            stopEntry = { _, _ -> error("unexpected stop") },
            cancelEntry = { _, _ -> error("unexpected cancel") },
            deleteEntry = { _, _ ->
                calls++
                session.publish(snapshot(listOf(neighbour)))
                DvrMutationResult.Confirmed(Unit)
            },
        )
        val state = RecordingsScreenState()
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, session.observation.value.currentSession?.generationIdentity) }
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreenContent(observation = session.observation.collectAsState().value,
                    currentObservation = { session.observation.value }, state = state,
                    dvrMutationActions = actions, notices = notices)
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("recording-list-entry-1")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-delete").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-confirmation-back").assertIsFocused()
        compose.onNodeWithTag("recording-confirmation-confirm").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        compose.onNodeWithTag("recording-list-entry-2").assertIsFocused()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun lastInFolderDeleteFromDetailsReturnsToNextParentItem() = lastInFolder("details")
    @Test fun lastInFolderDeleteFromMenuReturnsToPreviousParentItem() = lastInFolder("menu")
    @Test fun lastInFolderRemovalFromBrowseReturnsToParentItemAndBackWorks() = lastInFolder("browse")
    @Test fun lastInFolderDeleteAtEmptyRootFocusesMode() = lastInFolder("details", emptyRoot = true)

    private fun lastInFolder(source: String, emptyRoot: Boolean = false) {
        val entry = DvrEntry.create(DvrEntryId(1), title = "Recording", state = DvrEntryState.COMPLETED,
            folder = listOf("Series", "Middle", "Only"))
        val before = DvrEntry.create(DvrEntryId(2), title = "Before", state = DvrEntryState.COMPLETED,
            folder = listOf("Series", "Before"))
        val after = DvrEntry.create(DvrEntryId(3), title = "Next", state = DvrEntryState.COMPLETED,
            folder = listOf("Series", "Next"))
        val survivors = if (emptyRoot) emptyList() else if (source == "menu") listOf(before) else listOf(before, after)
        val initial = observation(DvrEntryState.COMPLETED)
        fun snapshot(entries: List<DvrEntry>) = SessionObservation.create(initial.sessionState, initial.channelState,
            initial.epgState, DvrRepositoryState.Current(DvrSnapshot.create(entries)))
        val session = FakeTvheadendSession(snapshot(survivors + entry))
        var calls = 0
        val actions = DvrMutationActions(
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
            scheduleEntry = { _, _ -> error("unexpected schedule") },
            stopEntry = { _, _ -> error("unexpected stop") },
            cancelEntry = { _, _ -> error("unexpected cancel") },
            deleteEntry = { _, _ -> calls++; session.publish(snapshot(survivors)); DvrMutationResult.Confirmed(Unit) },
        )
        val state = RecordingsScreenState()
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, session.observation.value.currentSession?.generationIdentity) }
        lateinit var back: OnBackPressedDispatcher
        compose.setContent {
            TVHeadendPlayerTheme {
                back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                RecordingsScreenContent(observation = session.observation.collectAsState().value,
                    currentObservation = { session.observation.value }, state = state,
                    dvrMutationActions = actions, notices = notices)
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Series").fetchSemanticsNodes().isNotEmpty() }
        listOf("Series", "Middle", "Only").forEach { name ->
            compose.onNode(hasText(name) and hasClickAction()).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        }
        val row = compose.onNodeWithTag("recording-list-entry-1").assertIsFocused()
        if (source == "browse") {
            // Browse cards open playback; deletion arrives as a repository publication.
            compose.runOnIdle { session.publish(snapshot(survivors)) }
            compose.waitUntil(10_000) { state.archivePath == if (emptyRoot) emptyList<String>() else listOf("Series") }
        } else {
            row.performKeyInput { pressKey(Key.Menu) }
            if (source == "details") {
                compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
                compose.onNodeWithTag("details-delete").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
            } else compose.onNodeWithText("Delete").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithTag("recording-confirmation-back").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("recording-confirmation-confirm").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        }
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        if (emptyRoot) {
            compose.runOnIdle { assertEquals(emptyList<String>(), state.archivePath) }
            compose.onNode(hasText("Archive") and hasClickAction()).assertIsFocused()
        } else {
            compose.runOnIdle { assertEquals(listOf("Series"), state.archivePath) }
            compose.onNode(hasText(if (source == "menu") "Before" else "Next") and hasClickAction()).assertIsFocused()
            compose.runOnIdle { back.onBackPressed() }
            compose.runOnIdle { assertEquals(emptyList<String>(), state.archivePath) }
            compose.onNode(hasText("Series") and hasClickAction()).assertIsFocused()
        }
        compose.runOnIdle { assertEquals(if (source == "browse") 0 else 1, calls) }
    }

    private fun delayedFailure(reopen: Boolean) {
        lateinit var backDispatcher: OnBackPressedDispatcher
        val session = FakeTvheadendSession(observation(DvrEntryState.SCHEDULED))
        val state = RecordingsScreenState().apply { mode.value = DvrLibraryMode.SCHEDULE }
        val result = CompletableDeferred<DvrMutationResult<Unit>>()
        var calls = 0
        val actions = DvrMutationActions(
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
            scheduleEntry = { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            stopEntry = { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            cancelEntry = { _, _ -> calls++; result.await() },
            deleteEntry = { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
        )
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, session.observation.value.currentSession?.generationIdentity) }
        compose.setContent {
            TVHeadendPlayerTheme {
                backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                RecordingsScreenContent(
                    observation = session.observation.collectAsState().value,
                    currentObservation = { session.observation.value },
                    state = state, dvrMutationActions = actions, notices = notices,
                )
            }
        }
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-cancel").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-confirmation-confirm").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, calls) }
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithTag("details-cancel").assertDoesNotExist()
        if (reopen) compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { result.complete(DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false)) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(Notice.DvrActionFailed(DvrMutationKind.CANCEL, DvrMutationFeedback.PERMISSION_DENIED),
                notices.state.value.candidate?.notice)
        }
    }

    private fun transition(before: DvrEntryState, after: DvrEntryState?, clickBeforeRecomposition: Boolean = false) {
        val session = FakeTvheadendSession(observation(before))
        val state = RecordingsScreenState().apply { mode.value = DvrLibraryMode.SCHEDULE }
        var calls = 0
        val actions = DvrMutationActions(
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
            scheduleEntry = { _, _ -> calls++; DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            stopEntry = { _, _ -> calls++; DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            cancelEntry = { _, _ -> calls++; DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            deleteEntry = { _, _ -> calls++; DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
        )
        val notices = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) {
            at.bernhardberger.tvhplayer.notices.NoticeContext(0, session.observation.value.currentSession?.generationIdentity)
        }
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreenContent(
                    observation = session.observation.collectAsState().value,
                    currentObservation = { session.observation.value },
                    state = state, dvrMutationActions = actions,
                    notices = notices,
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("recording-list-entry-1")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recording-list-entry-1").requestFocus()
            .performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        val action = if (before == DvrEntryState.SCHEDULED) "cancel" else "stop"
        compose.onNodeWithTag("details-$action").requestFocus()
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
            compose.onNodeWithTag("details-stop").assertIsFocused()
        }
        compose.onAllNodes(isFocused()).assertCountEquals(1)
        if (!clickBeforeRecomposition) compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-confirmation-confirm").assertDoesNotExist()
        assertEquals(0, calls)
    }

    private fun stopDispatch(before: DvrEntryState, after: DvrEntryState) {
        val session = FakeTvheadendSession(observation(before))
        val state = RecordingsScreenState().apply { mode.value = DvrLibraryMode.SCHEDULE }
        val stopped = mutableListOf<DvrEntryId>()
        val unexpected: suspend (CurrentSessionObservation, DvrEntryId) -> DvrMutationResult<Unit> =
            { _, _ -> error("unexpected mutation") }
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> error("unexpected schedule") },
            stopEntry = { _, id -> stopped += id; DvrMutationResult.Confirmed(Unit) },
            cancelEntry = unexpected,
            deleteEntry = unexpected,
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
        )
        val notices = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) {
            at.bernhardberger.tvhplayer.notices.NoticeContext(0, session.observation.value.currentSession?.generationIdentity)
        }
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreenContent(
                    observation = session.observation.collectAsState().value,
                    currentObservation = { session.observation.value },
                    state = state, dvrMutationActions = actions,
                    notices = notices,
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("recording-list-entry-1")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recording-list-entry-1").requestFocus()
            .performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-stop").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { session.publish(observation(after)) }
        compose.onNodeWithTag("recording-confirmation-confirm").requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { stopped.isNotEmpty() }
        assertEquals(listOf(DvrEntryId(1)), stopped)
    }

    private fun observation(
        state: DvrEntryState?,
        dvrWrite: CapabilityAccess = CapabilityAccess.ALLOWED,
        playCount: Long? = null,
        folder: List<String>? = null,
    ) = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = dvrWrite,
        )),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = state?.let {
            listOf(DvrEntry.create(id = DvrEntryId(1), title = "Recording", state = it, playCount = playCount, folder = folder,
                start = kotlin.time.Instant.parse("2026-09-15T20:00:00Z"),
                stop = kotlin.time.Instant.parse("2026-09-15T21:00:00Z")))
        }.orEmpty())),
    )
}
