package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.core.AutorecEpisodeState
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.RecordingSubmitUnavailable
import java.time.DayOfWeek
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticRecordingEditorStateTest {
    @Test fun levelsRestoreTheirOpeningRowAndChoicesApplyOnlyWhenConfirmed() = runTest {
        val state = AutomaticRecordingEditorFixture(this).state()
        state.navigation.focusedRow = "channel-time"
        state.navigation.open(AutomaticRecordingEditorLevel.CHANNEL_TIME, "days")
        state.navigation.open(AutomaticRecordingEditorLevel.DAYS, "any")
        state.updateDraft { it.copy(days = setOf(DayOfWeek.SATURDAY)) }
        state.back()
        assertEquals("days", state.navigation.page.focusedRow)
        assertEquals(setOf(DayOfWeek.SATURDAY), state.draft.days)
        state.back()
        assertEquals("channel-time", state.navigation.page.focusedRow)
        state.navigation.open(AutomaticRecordingEditorLevel.KEEP, "default")
        state.back()
        assertNull(state.draft.removal)
        state.navigation.open(AutomaticRecordingEditorLevel.WORDS, "words")
        state.navigation.open(AutomaticRecordingEditorLevel.WORDS_ENTRY, "words-input")
        assertFalse(state.navigation.textEntryEditing)
        state.navigation.textEntryEditing = true
        state.back()
        assertFalse(state.navigation.textEntryEditing)
        assertEquals(AutomaticRecordingEditorLevel.WORDS_ENTRY, state.navigation.page.level)
        state.back()
        assertEquals("words", state.navigation.focusedRow)
        state.navigation.open(AutomaticRecordingEditorLevel.WORDS_ENTRY, "words-input")
        state.applyChoice { it.copy(fullText = true) }
        assertEquals(AutomaticRecordingEditorLevel.WORDS, state.navigation.page.level)
        assertFalse(state.navigation.textEntryEditing)
        state.dispose()
    }

    @Test fun newerDraftCancelsAndRejectsAnObsoletePreview() = runTest {
        val oldResult = CompletableDeferred<AutorecPreviewState>()
        lateinit var oldSearch: Job
        var requests = 0
        val state = AutomaticRecordingEditorFixture(this).state(
            load = { _, _, now, zone ->
                assertEquals(automaticRecordingNow, now)
                assertEquals(automaticRecordingZone, zone)
                requests++
                if (requests == 1) {
                    oldSearch = currentCoroutineContext().job
                    withContext(NonCancellable) { oldResult.await() }
                } else {
                    AutorecPreviewState.Matches(emptyList(), 0, false)
                }
            },
        )
        runCurrent()
        state.updateDraft { it.copy(enabled = false) }
        runCurrent()
        assertEquals(0, (state.preview as AutorecPreviewState.Matches).matchCount)
        try {
            assertTrue(oldSearch.isCancelled)
        } finally {
            oldResult.complete(automaticRecordingMatches)
        }
        runCurrent()
        assertEquals(0, (state.preview as AutorecPreviewState.Matches).matchCount)
        state.dispose()
    }

    @Test fun mainAvailabilityDoesNotDependOnPreviewAndFailedSaveKeepsTheDraft() = runTest {
        val fixture = AutomaticRecordingEditorFixture(this)
        fixture.outcome = AutorecSaveOutcome.RuleFailed(
            DvrMutationResult.Failed(TvheadendFailure.SERVER_REJECTED, mayHaveApplied = false),
        )
        val state = fixture.state(load = { _, _, _, _ -> AutorecPreviewState.Failed(TvheadendFailure.TIMEOUT) })
        assertNull(state.mainProblem)
        runCurrent()
        assertNull(state.mainProblem)
        state.submit()
        assertTrue(state.busy)
        state.updateDraft { it.copy(enabled = false) }
        runCurrent()
        assertEquals(automaticRecordingSeries, state.draft)
        assertEquals("main", state.navigation.page.focusedRow)
        assertFalse(state.closed)
        fixture.outcome = AutorecSaveOutcome.PartiallyApplied(AutorecRuleId("series"), setOf(EventId(1)))
        state.submit()
        runCurrent()
        assertTrue(state.closed)
        assertEquals(1, fixture.closes)
        val unchanged = fixture.state(editing = true)
        assertEquals(RecordingSubmitUnavailable.UNCHANGED, unchanged.mainProblem)
        assertEquals("matches", unchanged.navigation.page.focusedRow)
        unchanged.submit()
        runCurrent()
        assertEquals(2, fixture.submissions.size)
        unchanged.dispose()
    }

    @Test fun destructiveConfirmationsKeepDraftUntilExplicitChoice() = runTest {
        val fixture = AutomaticRecordingEditorFixture(this)
        val state = fixture.state()
        state.updateDraft { it.copy(enabled = false) }
        state.back()
        assertTrue(state.navigation.leaveConfirmation)
        state.back()
        assertFalse(state.navigation.leaveConfirmation)
        assertFalse(state.draft.enabled)
        state.back()
        state.back()
        assertFalse(state.closed)
        state.back()
        state.confirmDestructiveAction()
        assertTrue(state.closed)
        assertTrue(fixture.submissions.isEmpty())
        val untouched = fixture.state()
        untouched.back()
        assertTrue(untouched.closed)
        val existing = fixture.state(editing = true)
        existing.navigation.open(AutomaticRecordingEditorLevel.MORE, "delete")
        existing.updateDraft { it.copy(enabled = false) }
        existing.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        existing.back()
        assertEquals(0, fixture.deletions)
        fixture.deletionResult = DvrMutationResult.Failed(TvheadendFailure.SERVER_REJECTED, false)
        existing.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        existing.confirmDestructiveAction()
        assertTrue(existing.busy)
        existing.submit()
        runCurrent()
        assertNull(existing.navigation.confirmation)
        assertFalse(existing.closed)
        assertFalse(existing.draft.enabled)
        assertEquals(1, fixture.deletions)
        assertEquals("delete", existing.navigation.focusedRow)
        fixture.deletionResult = DvrMutationResult.AcceptedButUnconfirmed(Unit)
        existing.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        existing.confirmDestructiveAction()
        runCurrent()
        assertTrue(existing.closed)
        assertEquals(2, fixture.deletions)
        assertTrue(fixture.submissions.isEmpty())
    }

    @Test fun episodeChoicesReachSubmitAndUntoggleableRowsNeverDispatch() = runTest {
        val fixture = AutomaticRecordingEditorFixture(this)
        val edited = fixture.state(editing = true)
        runCurrent()
        val scheduled = automaticRecordingEpisodes.first().copy(
            recordingId = DvrEntryId(1),
            state = AutorecEpisodeState.SCHEDULED,
        )
        edited.toggleEpisode(scheduled.copy(eventId = null))
        edited.toggleEpisode(scheduled.copy(state = AutorecEpisodeState.REMOVED))
        edited.toggleEpisode(scheduled.copy(state = AutorecEpisodeState.DUPLICATE))
        assertTrue(edited.enabledChanges.isEmpty())
        edited.toggleEpisode(scheduled)
        assertEquals(5, (edited.preview as AutorecPreviewState.Matches).willRecordCount)
        assertNull(edited.mainProblem)
        edited.toggleEpisode(scheduled)
        assertEquals(6, (edited.preview as AutorecPreviewState.Matches).willRecordCount)
        assertEquals(RecordingSubmitUnavailable.UNCHANGED, edited.mainProblem)
        edited.toggleEpisode(scheduled)
        edited.submit()
        runCurrent()
        assertEquals(mapOf(EventId(1) to false), fixture.submissions.single().second)

        var loads = 0
        val pending = CompletableDeferred<AutorecPreviewState>()
        val state = fixture.state(load = { _, _, _, _ ->
            if (loads++ == 0) automaticRecordingMatches else pending.await()
        })
        runCurrent()
        assertEquals(6, (state.preview as AutorecPreviewState.Matches).willRecordCount)
        state.toggleEpisode(automaticRecordingEpisodes.first())
        runCurrent()
        assertEquals(automaticRecordingMatches.rows, (state.preview as AutorecPreviewState.Matches).rows)
        assertEquals(5, (state.preview as AutorecPreviewState.Matches).willRecordCount)
        state.toggleEpisode(automaticRecordingEpisodes.first())
        runCurrent()
        assertEquals(6, (state.preview as AutorecPreviewState.Matches).willRecordCount)
        state.toggleEpisode(automaticRecordingEpisodes.first())
        runCurrent()
        assertEquals(5, (state.preview as AutorecPreviewState.Matches).willRecordCount)
        state.submit()
        runCurrent()
        assertEquals(2, fixture.submissions.size)
        assertEquals(setOf(EventId(1)), fixture.submissions.last().first.excludedEventIds)
        assertTrue(fixture.submissions.last().second.isEmpty())
    }
}
