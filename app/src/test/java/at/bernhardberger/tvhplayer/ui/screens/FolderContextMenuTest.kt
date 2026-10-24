package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
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
class FolderContextMenuTest {
    @get:Rule val compose = createComposeRule()

    private val watchCalls = mutableListOf<Pair<DvrEntryId, Boolean>>()
    private val deleteCalls = mutableListOf<DvrEntryId>()
    private var onWatchCall: (DvrEntryId) -> DvrProgressResult = { DvrProgressResult.Accepted }
    private var onDeleteCall: (DvrEntryId) -> DvrMutationResult<Unit> = { DvrMutationResult.Confirmed(Unit) }
    private var watchGate: CompletableDeferred<Unit>? = null
    private val screenShown = mutableStateOf(true)

    @Test fun markAllAsWatchedChangesEachFinishedUnwatchedRecordingAndReturnsToTheFolder() {
        show(seriesFolder())
        openFolderMenu()
        listOf("Mark all as watched", "Mark all as unwatched", "Delete watched", "Delete all").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("Mark all as watched").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        compose.onNodeWithTag("recordings-folder-Series").assertIsFocused()
        compose.runOnIdle {
            assertEquals(listOf(DvrEntryId(1) to true, DvrEntryId(3) to true), watchCalls)
            assertEquals(null, notices.state.value.candidate?.notice)
        }
    }

    @Test fun deleteAllAsksFirstWithTheCountAndKeepsRunningRecordings() {
        show(seriesFolder())
        openFolderMenu()
        compose.onNodeWithText("Delete all").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Delete all recordings in Series?").assertExists()
        compose.onNodeWithText("3 recordings will be deleted from the server. Recordings still running are kept.")
            .assertExists()
        compose.onNodeWithTag("folder-delete-back").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("folder-delete-confirm").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { deleteCalls.size == 3 }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(DvrEntryId(1), DvrEntryId(2), DvrEntryId(3)), deleteCalls) }
    }

    @Test fun keepingClosesTheConfirmationWithoutDeleting() {
        show(seriesFolder())
        openFolderMenu()
        compose.onNodeWithText("Delete watched").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Delete watched recordings in Series?").assertExists()
        compose.onNodeWithTag("folder-delete-back").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("folder-delete-back").assertDoesNotExist()
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        compose.onNodeWithTag("recordings-folder-Series").assertIsFocused()
        compose.runOnIdle { assertEquals(emptyList<DvrEntryId>(), deleteCalls) }
    }

    @Test fun aPartialFailureSaysHowManyOfTheRecordingsFailed() {
        onWatchCall = { id ->
            if (id == DvrEntryId(3)) DvrProgressResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false)
            else DvrProgressResult.Accepted
        }
        show(seriesFolder())
        openFolderMenu()
        compose.onNodeWithText("Mark all as watched").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { watchCalls.size == 2 }
        compose.runOnIdle {
            assertEquals(
                Notice.DvrFolderActionFailed(DvrMutationKind.UPDATE, failed = 1, total = 2, DvrMutationFeedback.PERMISSION_DENIED),
                notices.state.value.candidate?.notice,
            )
        }
    }

    @Test fun losingWritePermissionPartWayFailsTheRest() {
        lateinit var session: FakeTvheadendSession
        onWatchCall = {
            session.publish(observation(seriesFolder(), dvrWrite = CapabilityAccess.DENIED))
            DvrProgressResult.Accepted
        }
        session = show(seriesFolder())
        openFolderMenu()
        compose.onNodeWithText("Mark all as watched").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { notices.state.value.candidate != null }
        compose.runOnIdle {
            assertEquals(listOf(DvrEntryId(1) to true), watchCalls)
            assertEquals(
                Notice.DvrFolderActionFailed(DvrMutationKind.UPDATE, failed = 1, total = 2, DvrMutationFeedback.PERMISSION_DENIED),
                notices.state.value.candidate?.notice,
            )
        }
    }

    @Test fun aFolderActionFinishesAndReportsAfterTheScreenLeaves() {
        val gate = CompletableDeferred<Unit>().also { watchGate = it }
        onWatchCall = { id ->
            if (id == DvrEntryId(3)) DvrProgressResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false)
            else DvrProgressResult.Accepted
        }
        show(seriesFolder())
        openFolderMenu()
        compose.onNodeWithText("Mark all as watched").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { watchCalls.size == 1 }
        screenShown.value = false
        compose.onNodeWithTag("recordings-folder-Series").assertDoesNotExist()
        gate.complete(Unit)
        compose.waitUntil(10_000) { notices.state.value.candidate != null }
        compose.runOnIdle {
            assertEquals(listOf(DvrEntryId(1) to true, DvrEntryId(3) to true), watchCalls)
            assertEquals(
                Notice.DvrFolderActionFailed(DvrMutationKind.UPDATE, failed = 1, total = 2, DvrMutationFeedback.PERMISSION_DENIED),
                notices.state.value.candidate?.notice,
            )
        }
    }

    @Test fun aSecondFolderActionWaitsForTheFirstEvenFromAScreenOpenedAgain() {
        val gate = CompletableDeferred<Unit>().also { watchGate = it }
        show(seriesFolder())
        openFolderMenu()
        compose.onNodeWithText("Mark all as watched").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { watchCalls.size == 1 }
        screenShown.value = false
        compose.onNodeWithTag("recordings-folder-Series").assertDoesNotExist()
        screenShown.value = true
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("recordings-folder-Series").fetchSemanticsNodes().isNotEmpty() }
        openFolderMenu()
        compose.onNodeWithText("Mark all as unwatched").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(DvrEntryId(1) to true), watchCalls) }
        gate.complete(Unit)
        compose.waitUntil(10_000) { watchCalls.size == 3 }
        compose.runOnIdle {
            assertEquals(listOf(DvrEntryId(1) to true, DvrEntryId(3) to true, DvrEntryId(2) to false), watchCalls)
        }
    }

    @Test fun aWatchedFolderEndsItsNameWithTheCheckAndAMixedOneCountsWhatIsLeft() {
        show(
            listOf(
                entry(1, listOf("Seen"), playCount = 1),
                entry(2, listOf("Seen"), playCount = 2),
                entry(3, listOf("Mixed"), playCount = 1),
                entry(4, listOf("Mixed")),
                entry(5, listOf("Mixed")),
                entry(6, listOf("New")),
            )
        )
        compose.onNodeWithTag("recordings-folder-watched-Seen", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("recordings-folder-watched-Mixed", useUnmergedTree = true)
            .assertContentDescriptionEquals("Partly watched")
        compose.onNodeWithTag("recordings-folder-watched-New", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("recordings-folder-supporting-Mixed", useUnmergedTree = true)
            .assertTextEquals("3 recordings • 1 watched")
        compose.onNodeWithTag("recordings-folder-supporting-New", useUnmergedTree = true).assertTextEquals("1 recording")
        compose.onNodeWithTag("recordings-folder-supporting-Seen", useUnmergedTree = true).assertTextEquals("2 recordings")
    }

    @Test fun theRecordingNowFolderHasNoMenu() {
        show(seriesFolder())
        compose.onNodeWithTag("recordings-recording-now-folder").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
    }

    private lateinit var notices: NoticeCenter

    private fun openFolderMenu() {
        compose.onNodeWithTag("recordings-folder-Series").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("browse-context-menu").assertExists()
    }

    /** Unwatched 1 and 3, watched 2, and 4 still recording. */
    private fun seriesFolder() = listOf(
        entry(1, listOf("Series")),
        entry(2, listOf("Series"), playCount = 1),
        entry(3, listOf("Series")),
        entry(4, listOf("Series"), state = DvrEntryState.RECORDING),
    )

    private fun show(entries: List<DvrEntry>): FakeTvheadendSession {
        val session = FakeTvheadendSession(observation(entries))
        val actions = DvrMutationActions(
            updateWatchStateCommand = { _, id, watched ->
                watchCalls += id to watched
                watchGate?.await()
                onWatchCall(id)
            },
            scheduleEntry = { _, _ -> error("Unexpected schedule") },
            stopEntry = { _, _ -> error("Unexpected stop") },
            cancelEntry = { _, _ -> error("Unexpected cancel") },
            deleteEntry = { _, id -> deleteCalls += id; onDeleteCall(id) },
        )
        notices = NoticeCenter({ 0L }) { NoticeContext(0, session.observation.value.currentSession?.generationIdentity) }
        // As in the app, the screen state outlives the screen.
        val state = RecordingsScreenState()
        compose.setContent {
            TVHeadendPlayerTheme {
                if (screenShown.value) RecordingsScreenContent(observation = session.observation.collectAsState().value,
                    currentObservation = { session.observation.value }, state = state,
                    dvrMutationActions = actions, notices = notices)
            }
        }
        val first = "recordings-folder-" + entries.first().folder.orEmpty().joinToString("/")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(first).fetchSemanticsNodes().isNotEmpty() }
        return session
    }

    private fun entry(
        id: Long,
        folder: List<String>,
        playCount: Long? = null,
        state: DvrEntryState = DvrEntryState.COMPLETED,
    ) = DvrEntry.create(
        id = DvrEntryId(id), title = "Recording $id", state = state, playCount = playCount, folder = folder,
        start = kotlin.time.Instant.parse("2026-09-15T20:00:00Z"),
        stop = kotlin.time.Instant.parse("2026-09-15T21:00:00Z"),
    )

    private fun observation(entries: List<DvrEntry>, dvrWrite: CapabilityAccess = CapabilityAccess.ALLOWED) =
        SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = dvrWrite,
            )),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = entries)),
        )
}
