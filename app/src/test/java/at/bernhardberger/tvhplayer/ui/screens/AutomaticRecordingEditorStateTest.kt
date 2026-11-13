package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.core.AutorecEpisodeState
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.RecordingSubmitUnavailable
import java.time.DayOfWeek
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
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
        state.focusedRow = "channel-time"
        state.open(AutomaticRecordingEditorLevel.CHANNEL_TIME, "days")
        state.open(AutomaticRecordingEditorLevel.DAYS, "any")
        state.updateDraft { it.copy(days = setOf(DayOfWeek.SATURDAY)) }
        state.back()
        assertEquals("days", state.page.focusedRow)
        assertEquals(setOf(DayOfWeek.SATURDAY), state.draft.days)
        state.back()
        assertEquals("channel-time", state.page.focusedRow)
        state.open(AutomaticRecordingEditorLevel.KEEP, "default")
        state.back()
        assertNull(state.draft.removal)
        state.open(AutomaticRecordingEditorLevel.WORDS, "words")
        state.open(AutomaticRecordingEditorLevel.WORDS_ENTRY, "words-input")
        assertFalse(state.textEntryEditing)
        state.textEntryEditing = true
        state.back()
        assertFalse(state.textEntryEditing)
        assertEquals(AutomaticRecordingEditorLevel.WORDS_ENTRY, state.page.level)
        state.back()
        assertEquals("words", state.focusedRow)
        state.open(AutomaticRecordingEditorLevel.WORDS_ENTRY, "words-input")
        state.applyChoice { it.copy(fullText = true) }
        assertEquals(AutomaticRecordingEditorLevel.WORDS, state.page.level)
        assertFalse(state.textEntryEditing)
        state.dispose()
    }

    @Test fun newerDraftCancelsAndRejectsAnObsoletePreview() = runTest {
        val oldResult = CompletableDeferred<AutorecPreviewState>()
        var cancelled = false
        var requests = 0
        val state = AutomaticRecordingEditorFixture(this).state(
            load = { _, _, now, zone ->
                assertEquals(automaticRecordingNow, now)
                assertEquals(automaticRecordingZone, zone)
                requests++
                if (requests == 1) {
                    try {
                        withContext(NonCancellable) { oldResult.await() }
                    } finally {
                        cancelled = true
                    }
                } else {
                    AutorecPreviewState.Matches(emptyList(), 0, false)
                }
            },
        )
        runCurrent()
        state.updateDraft { it.copy(enabled = false) }
        runCurrent()
        assertEquals(0, (state.preview as AutorecPreviewState.Matches).matchCount)
        oldResult.complete(automaticRecordingMatches)
        runCurrent()
        assertTrue(cancelled)
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
        assertEquals("main", state.page.focusedRow)
        assertFalse(state.closed)
        fixture.outcome = AutorecSaveOutcome.PartiallyApplied(AutorecRuleId("series"), setOf(EventId(1)))
        state.submit()
        runCurrent()
        assertTrue(state.closed)
        assertEquals(1, fixture.closes)
        val unchanged = fixture.state(editing = true)
        assertEquals(RecordingSubmitUnavailable.UNCHANGED, unchanged.mainProblem)
        assertEquals("matches", unchanged.page.focusedRow)
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
        assertTrue(state.leaveConfirmation)
        state.back()
        assertFalse(state.leaveConfirmation)
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
        existing.open(AutomaticRecordingEditorLevel.MORE, "delete")
        existing.updateDraft { it.copy(enabled = false) }
        existing.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        existing.back()
        assertEquals(0, fixture.deletions)
        fixture.deletionResult = DvrMutationResult.Failed(TvheadendFailure.SERVER_REJECTED, false)
        existing.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        existing.confirmDestructiveAction()
        assertTrue(existing.busy)
        existing.submit()
        runCurrent()
        assertNull(existing.confirmation)
        assertFalse(existing.closed)
        assertFalse(existing.draft.enabled)
        assertEquals(1, fixture.deletions)
        assertEquals("delete", existing.focusedRow)
        fixture.deletionResult = DvrMutationResult.AcceptedButUnconfirmed(Unit)
        existing.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        existing.confirmDestructiveAction()
        runCurrent()
        assertTrue(existing.closed)
        assertEquals(2, fixture.deletions)
        assertTrue(fixture.submissions.isEmpty())
    }

    @Test fun episodeChoicesReachSubmitAndUntoggleableRowsNeverDispatch() = runTest {
        val fixture = AutomaticRecordingEditorFixture(this)
        var loads = 0
        val pending = CompletableDeferred<AutorecPreviewState>()
        val state = fixture.state(load = { _, _, _, _ ->
            if (loads++ == 0) automaticRecordingMatches else pending.await()
        })
        runCurrent()
        state.toggleEpisode(automaticRecordingEpisodes.first())
        runCurrent()
        assertEquals(automaticRecordingMatches, state.preview)
        state.submit()
        runCurrent()
        assertEquals(setOf(EventId(1)), fixture.submissions.single().first.excludedEventIds)
        assertTrue(fixture.submissions.single().second.isEmpty())

        val edited = fixture.state(editing = true)
        val scheduled = automaticRecordingEpisodes.first().copy(
            recordingId = DvrEntryId(1),
            state = AutorecEpisodeState.SCHEDULED,
        )
        edited.toggleEpisode(scheduled.copy(eventId = null))
        edited.toggleEpisode(scheduled.copy(state = AutorecEpisodeState.REMOVED))
        edited.toggleEpisode(scheduled.copy(state = AutorecEpisodeState.DUPLICATE))
        assertTrue(edited.enabledChanges.isEmpty())
        edited.toggleEpisode(scheduled)
        assertNull(edited.mainProblem)
        edited.toggleEpisode(scheduled)
        assertEquals(RecordingSubmitUnavailable.UNCHANGED, edited.mainProblem)
        edited.toggleEpisode(scheduled)
        edited.submit()
        runCurrent()
        assertEquals(mapOf(EventId(1) to false), fixture.submissions.last().second)
    }
}
