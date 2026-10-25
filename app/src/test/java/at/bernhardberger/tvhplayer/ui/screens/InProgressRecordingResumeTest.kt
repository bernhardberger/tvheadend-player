package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import at.bernhardberger.tvhplayer.ui.PlayerEntryHandoff
import at.bernhardberger.tvhplayer.ui.PlayerEntryOverlays
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
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
@OptIn(TvheadendTestingApi::class)
class InProgressRecordingResumeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun anInProgressRecordingWithASavedPositionResumesThere() {
        val starts = openDetails(playPositionSeconds = 754L)

        compose.onNodeWithText("Watch").assertExists()
        compose.onNodeWithTag("details-watch").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.RESUME), starts) }
    }

    @Test
    fun anInProgressRecordingOffersStopBesideWatch() {
        val starts = openDetails(playPositionSeconds = 754L)

        compose.onNodeWithTag("details-watch").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-start_over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-stop").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-confirmation-back").assertIsFocused()
        compose.runOnIdle { assertEquals(emptyList<RecordingPlaybackStart>(), starts) }
    }

    @Test fun anInProgressRecordingCanStillBePlayedFromTheBeginning() {
        val starts = openDetails(754L)
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("details-start_over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.START_OVER), starts) }
    }

    @Test fun inProgressMenuWatchResumes() {
        val starts = openDetails(754L, menuOnly = true)
        compose.onNodeWithText("Watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.RESUME), starts) }
    }

    @Test fun inProgressMenuStartOverStartsFromBeginning() {
        val starts = openDetails(754L, menuOnly = true)
        compose.onNodeWithText("Watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Start over").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.START_OVER), starts) }
    }

    @Test
    fun anInProgressRecordingWithoutASavedPositionKeepsPlay() = assertPlayOnly(playPositionSeconds = null)

    @Test
    fun anInProgressRecordingAtItsStartKeepsPlay() = assertPlayOnly(playPositionSeconds = 0L)

    private fun assertPlayOnly(playPositionSeconds: Long?) {
        val starts = openDetails(playPositionSeconds)

        compose.onNodeWithTag("details-resume").assertDoesNotExist()
        compose.onNodeWithTag("details-start_over").assertDoesNotExist()
        compose.onNodeWithTag("details-watch").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.RESUME), starts) }
    }

    @Test
    fun okOnRunningRecordingResumesDirectlyWithoutPrompt() {
        val starts = openDetails(754L, directly = true)
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.RESUME), starts) }
    }

    @Test fun completedWarmRecordingReturnsWithoutResumePopupOrPlaybackRequest() {
        var returns = 0
        val starts = openDetails(754L, directly = true, warm = true, completed = true,
            onReturn = { id -> assertEquals(DvrEntryId(1), id); returns++; true })
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, returns); assertEquals(emptyList<RecordingPlaybackStart>(), starts) }
    }

    @Test fun browseWarmDetailsHasTheSameActionsAndProgressAsPlayerDetails() {
        openDetails(754L, warm = true)
        assertWarmDetails()
    }

    @Test fun playerWarmDetailsHasTheSameActionsAndProgressAsBrowseDetails() {
        openDetails(754L, warm = true, fromPlayer = true)
        assertWarmDetails()
    }

    @Test fun rejectedWarmReturnUsesTheNormalCompletedRecordingResumeChoice() {
        val starts = openDetails(754L, directly = true, warm = true, completed = true,
            onReturn = { id -> assertEquals(DvrEntryId(1), id); false })
        compose.onNodeWithTag("recording-resume").assertExists()
        compose.runOnIdle { assertEquals(emptyList<RecordingPlaybackStart>(), starts) }
    }

    @Test fun detailsDoNotOfferContinueForARecordingFromAnotherSession() {
        openDetails(754L, warm = true, staleSession = true)
        compose.onNodeWithTag("details-continue_watching").assertDoesNotExist()
        compose.onNodeWithTag("details-watch").assertIsFocused()
    }

    @Test fun rejectedContinueFromDetailsKeepsTheNormalResumeChoiceOpen() {
        var attempts = 0
        val handoff = PlayerEntryHandoff()
        val starts = openDetails(754L, warm = true, completed = true,
            handoff = handoff,
            onReturn = { attempts++; false })
        compose.onNodeWithTag("details-continue_watching").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("recording-resume").assertExists()
        compose.runOnIdle {
            assertFalse("a rejected return must not register a close for the reopened flow", handoff.departing)
            assertEquals(1, attempts)
            assertEquals(emptyList<RecordingPlaybackStart>(), starts)
        }
    }

    @Test fun queuedContinueRegistersDepartureBeforeRequestAndKeepsItsActionsStable() {
        val handoff = PlayerEntryHandoff()
        val requestGate = CompletableDeferred<Unit>()
        val starts = openDetails(754L, warm = true, completed = true, handoff = handoff, requestGate = requestGate)
        compose.mainClock.advanceTimeBy(1_000)
        fun actions() = compose.onAllNodes(hasAnyAncestor(hasTestTag("details-actions")), useUnmergedTree = true)
            .fetchSemanticsNodes().let { nodes ->
                nodes.flatMap { node -> if (SemanticsProperties.Text in node.config)
                    node.config[SemanticsProperties.Text].map { it.text } else emptyList() } to
                    nodes.filter { SemanticsActions.OnClick in it.config }
                        .map { it.config[SemanticsProperties.TestTag] to it.boundsInRoot.center }
            }
        val pressedActions = actions()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("details-continue_watching").performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle {
            assertTrue("return request is queued", handoff.pending)
            assertTrue("accepted return registered its close before starting the request", handoff.departing)
        }
        repeat(PlayerMotion.DestinationMs / 16) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals("queued departure frame ${it + 1}", pressedActions, actions())
        }
        compose.onNodeWithTag("details-delete").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-confirmation-back").assertDoesNotExist()
        compose.runOnIdle { requestGate.complete(Unit) }
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        compose.runOnIdle { assertFalse(handoff.departing); assertEquals(emptyList<RecordingPlaybackStart>(), starts) }
    }

    @Test fun recordingPlayerDetailsSwallowKeysWhileAnEntryRequestIsPending() {
        val handoff = PlayerEntryHandoff()
        val requestGate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Main)
        try {
            openDetails(754L, warm = true, completed = true, fromPlayer = true, handoff = handoff)
            compose.runOnIdle { handoff.request(scope) { requestGate.await() } }
            compose.runOnIdle { assertTrue(handoff.pending); assertFalse(handoff.departing) }
            compose.onNodeWithTag("details-delete").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithTag("recording-confirmation-back").assertDoesNotExist()
        } finally {
            scope.cancel()
        }
    }

    private fun assertWarmDetails() {
        compose.onNodeWithTag("details-continue_watching").assertIsFocused()
        compose.onNodeWithTag("details-start_over").assertExists()
        compose.onNodeWithTag("details-stop").assertExists()
        compose.onNodeWithTag("details-watch").assertDoesNotExist()
        compose.onNodeWithTag("details-resume").assertDoesNotExist()
        compose.onNodeWithTag("details-timeline").assertExists()
    }

    private fun openDetails(playPositionSeconds: Long?, directly: Boolean = false, menuOnly: Boolean = false,
        warm: Boolean = false, completed: Boolean = false, fromPlayer: Boolean = false, staleSession: Boolean = false,
        handoff: PlayerEntryHandoff? = null, requestGate: CompletableDeferred<Unit>? = null,
        onReturn: (DvrEntryId) -> Boolean = { true }): List<RecordingPlaybackStart> {
        val starts = mutableListOf<RecordingPlaybackStart>()
        val observation = observation(playPositionSeconds, completed)
        val playbackSession = requireNotNull(if (staleSession) observation(playPositionSeconds, completed).currentSession
            else observation.currentSession)
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            stopEntry = { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            cancelEntry = { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            deleteEntry = { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
        )
        val state = RecordingsScreenState().apply { mode.value = if (completed) DvrLibraryMode.ARCHIVE else DvrLibraryMode.SCHEDULE }
        val notices = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) {
            at.bernhardberger.tvhplayer.notices.NoticeContext(0, observation.currentSession?.generationIdentity)
        }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val content: @Composable () -> Unit = {
            androidx.compose.runtime.CompositionLocalProvider(
                at.bernhardberger.tvhplayer.ui.components.LocalActivePlaybackTarget provides
                    at.bernhardberger.tvhplayer.playback.AppPlaybackTarget.Recording(DvrEntryId(1)).takeIf { warm },
                at.bernhardberger.tvhplayer.ui.components.LocalRecordingPlaybackSelection provides
                    at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection(playbackSession, DvrEntryId(1)).takeIf { warm },
            ) {
            TVHeadendPlayerTheme {
                if (fromPlayer) at.bernhardberger.tvhplayer.ui.player.RecordingPlayerDetails(
                    observation.dvrSnapshotForDisplay!!.entries.single(), observation, observation, { observation },
                    System.currentTimeMillis() / 1000L, actions, notices, androidx.compose.runtime.rememberCoroutineScope(),
                    onPlayRecording = { _, start -> starts += start }, onClose = { onReturn(DvrEntryId(1)) },
                ) else RecordingsScreenContent(
                    observation = observation,
                    state = state,
                    dvrMutationActions = actions,
                    notices = notices,
                    onPlayRecording = { _, start -> starts += start },
                    playingRecordingId = DvrEntryId(1).takeIf { warm },
                    onReturnToRecording = { id, beforeRequest ->
                        val accepted = onReturn(id)
                        if (accepted) {
                            beforeRequest()
                            if (handoff != null) handoff.request(scope) { requestGate?.await() }
                        }
                        accepted
                    },
                )
            }
            }
            }
            if (handoff == null) content() else PlayerEntryOverlays(handoff, playerShown = { false }, content = content)
        }
        if (fromPlayer) { compose.waitForIdle(); return starts }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("recording-list-entry-1")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recording-list-entry-1").requestFocus()
            .performKeyInput { pressKey(if (directly) Key.DirectionCenter else Key.Menu) }
        if (!directly && !menuOnly) compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        return starts
    }

    private fun observation(playPositionSeconds: Long?, completed: Boolean = false): SessionObservation {
        val now = System.currentTimeMillis()
        val entry = DvrEntry.create(
            id = DvrEntryId(1),
            title = "Evening News",
            state = if (completed) DvrEntryState.COMPLETED else DvrEntryState.RECORDING,
            start = Instant.fromEpochMilliseconds(now - 1_800_000),
            stop = Instant.fromEpochMilliseconds(now + 1_800_000),
            folder = emptyList(),
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "evening-news.ts",
                start = Instant.fromEpochMilliseconds(now - 1_800_000),
                stop = Instant.fromEpochMilliseconds(now))),
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
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED,
        )
    }
}
