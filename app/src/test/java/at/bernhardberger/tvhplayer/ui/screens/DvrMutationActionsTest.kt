package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.toDvrMutationFeedback

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrProgressResult
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import at.bernhardberger.tvheadend.sdk.core.AutorecRule
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleCreate
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleUpdate
import at.bernhardberger.tvheadend.sdk.core.TimerecRule
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleId
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleCreate
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleUpdate
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntryUpdate
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.DvrRetention
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import kotlin.time.Instant
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvheadend.sdk.core.EpgSearchResult
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.RecordingKeepUntilChoice
import at.bernhardberger.tvhplayer.core.RecordingPattern
import at.bernhardberger.tvhplayer.core.automaticRecordingList
import at.bernhardberger.tvhplayer.core.newAutorecDraft
import at.bernhardberger.tvhplayer.core.recordingKeepUntilOptions
import at.bernhardberger.tvhplayer.core.toDraft
import at.bernhardberger.tvhplayer.core.toUpdateRequest
import at.bernhardberger.tvhplayer.core.AutorecPreviewRow
import at.bernhardberger.tvhplayer.core.AutorecEpisodeState
import kotlinx.coroutines.test.runCurrent
import java.time.ZoneId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi

class DvrMutationActionsTest {
    @Test fun previewLoaderMapsNothingFailureAndEntryOnlySeriesWithoutSearching() = runTest {
        val session = automaticSession()
        val captured = requireNotNull(session.observation.value.currentSession)
        val draft = newAutorecDraft(AutomaticRecordingKind.KEYWORD)
        val loader = AutorecPreviewLoader { _, _ -> EpgSearchResult.Failed(TvheadendFailure.TIMEOUT) }
        val now = Instant.fromEpochSeconds(100)
        val zone = ZoneId.of("UTC")
        assertEquals(AutorecPreviewState.NothingToSearch,
            loader.load(draft, captured, session.observation.value, null, now, zone))
        assertEquals(AutorecPreviewState.Failed(TvheadendFailure.TIMEOUT),
            loader.load(draft.copy(pattern = RecordingPattern("news")),
                captured, session.observation.value, null, now, zone))
        val noSearch = AutorecPreviewLoader { _, _ -> error("Wordless all-channel series must not search") }
        val series = draft.copy(kind = AutomaticRecordingKind.SERIES, seriesLinkUri = "series")
        val rows = noSearch.load(series, captured, session.observation.value, AutorecRuleId("own"), now, zone, series)
        assertEquals(0, (rows as AutorecPreviewState.Matches).matchCount)
        val existing = draft.copy(pattern = RecordingPattern("news"))
        publishAutomaticEntries(session, listOf(DvrEntry.create(DvrEntryId(9),
            autorecRuleId = AutorecRuleId("own"), start = Instant.fromEpochSeconds(200))))
        for (edit in listOf(existing, existing.copy(removal = DvrRemoval.Forever), existing.copy(enabled = false))) {
            val preview = noSearch.load(edit, captured, session.observation.value, AutorecRuleId("own"),
                now, zone, baseline = existing) as AutorecPreviewState.Matches
            assertEquals(1, preview.matchCount)
            assertEquals(if (edit.enabled) 1 else 0, preview.willRecordCount)
        }
        val available = AutorecPreviewLoader { _, _ -> EpgSearchResult.Available.create(emptyList(), captured) }
        assertEquals(0, (available.load(draft.copy(pattern = RecordingPattern("news")),
            captured, session.observation.value, null, now, zone) as AutorecPreviewState.Matches).matchCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun leftOutEpisodesApplyAfterEntriesArriveAndEmptySetDoesNotWait() = runTest {
        val session = automaticSession()
        val ruleId = AutorecRuleId("own")
        session.dvrRepository.scriptCreateAutorecRule(DvrMutationResult.Confirmed(ruleId))
        val requests = mutableListOf<Pair<DvrEntryId, DvrEntryUpdate>>()
        var confirmed = true
        val actions = automaticActions(session) { _, id, update ->
            requests += id to update
            if (confirmed) DvrMutationResult.Confirmed(Unit) else DvrMutationResult.AcceptedButUnconfirmed(Unit)
        }
        val context = NoticeContext(1, null)
        val notices = NoticeCenter({ testScheduler.currentTime }, { context })
        val action = DvrMutationAction.CreateAutorec(
            requireNotNull(session.observation.value.currentSession), AutorecRuleCreate("news"))
        val saving = async(start = CoroutineStart.UNDISPATCHED) {
            actions.createAutorecWithLeftOut(action, setOf(EventId(7)), session.observation, notices, context)
        }
        assertFalse(saving.isCompleted)
        val entry = DvrEntry.create(DvrEntryId(10), eventId = EventId(7), autorecRuleId = ruleId,
            state = DvrEntryState.SCHEDULED, enabled = true)
        publishAutomaticEntries(session, listOf(entry))
        assertEquals(AutorecSaveOutcome.Applied(ruleId), saving.await())
        assertEquals(listOf(entry.id to DvrEntryUpdate(enabled = false)), requests)
        val before = testScheduler.currentTime
        assertEquals(AutorecSaveOutcome.Applied(ruleId),
            actions.createAutorecWithLeftOut(action, emptySet(), session.observation, notices, context))
        assertEquals(before, testScheduler.currentTime)
        confirmed = false
        val accepted = async(start = CoroutineStart.UNDISPATCHED) {
            actions.createAutorecWithLeftOut(action, setOf(EventId(7)), session.observation, notices, context)
        }
        assertFalse(accepted.isCompleted)
        val unchangedEntry = DvrEntry.create(entry.id, eventId = entry.eventId,
            autorecRuleId = ruleId, state = DvrEntryState.SCHEDULED, enabled = true, dataSizeBytes = 1)
        publishAutomaticEntries(session, listOf(unchangedEntry))
        testScheduler.runCurrent()
        publishAutomaticEntries(session, listOf(unchangedEntry))
        testScheduler.runCurrent()
        assertEquals(2, requests.size)
        publishAutomaticEntries(session, listOf(DvrEntry.create(entry.id, eventId = entry.eventId,
            autorecRuleId = ruleId, state = DvrEntryState.SCHEDULED, enabled = false)))
        assertEquals(AutorecSaveOutcome.Applied(ruleId), accepted.await())
        assertEquals(2, requests.size)
    }

    @Test fun missingOrFailedLeftOutUpdatesReturnPartialAndOneNoticeWithoutRetryingRule() = runTest {
        val session = automaticSession()
        val ruleId = AutorecRuleId("own")
        session.dvrRepository.scriptCreateAutorecRule(DvrMutationResult.Confirmed(ruleId))
        session.dvrRepository.scriptUpdateEntry(
            DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false))
        val actions = DvrMutationActions(session.dvrRepository)
        val context = NoticeContext(1, null)
        val notices = NoticeCenter({ testScheduler.currentTime }, { context })
        val action = DvrMutationAction.CreateAutorec(
            requireNotNull(session.observation.value.currentSession), AutorecRuleCreate("news"))
        val before = testScheduler.currentTime
        assertEquals(AutorecSaveOutcome.PartiallyApplied(ruleId, setOf(EventId(7))),
            actions.createAutorecWithLeftOut(action, setOf(EventId(7)), session.observation, notices, context))
        assertTrue(testScheduler.currentTime > before)
        assertEquals(Notice.AutomaticRecordingEpisodesNotApplied(created = true),
            notices.state.value.candidate?.notice)
        publishAutomaticEntries(session, listOf(DvrEntry.create(DvrEntryId(10), eventId = EventId(7),
            autorecRuleId = ruleId, state = DvrEntryState.SCHEDULED, enabled = true)))
        val afterTimeout = testScheduler.currentTime
        assertEquals(AutorecSaveOutcome.PartiallyApplied(ruleId, setOf(EventId(7))),
            actions.createAutorecWithLeftOut(action, setOf(EventId(7)), session.observation, notices, context))
        assertEquals(afterTimeout, testScheduler.currentTime)
        session.dvrRepository.scriptCreateAutorecRule(
            DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false))
        assertTrue(actions.createAutorecWithLeftOut(action, setOf(EventId(7)),
            session.observation, notices, context) is AutorecSaveOutcome.RuleFailed)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun editingEpisodeChoicesWaitsForRecreatedEntriesAndEnablesAsWellAsDisables() = runTest {
        val rule = AutorecRule.create(AutorecRuleId("own"), title = "news")
        val old = DvrEntry.create(DvrEntryId(10), eventId = EventId(7), autorecRuleId = rule.id,
            state = DvrEntryState.SCHEDULED, enabled = false)
        val session = automaticSession(listOf(rule), listOf(old))
        session.dvrRepository.scriptUpdateAutorecRule(DvrMutationResult.Confirmed(Unit))
        val requests = mutableListOf<Pair<DvrEntryId, DvrEntryUpdate>>()
        val actions = automaticActions(session) { _, id, update ->
            requests += id to update
            DvrMutationResult.Confirmed(Unit)
        }
        val context = NoticeContext(1, null)
        val notices = NoticeCenter({ testScheduler.currentTime }, { context })
        val saving = async(start = CoroutineStart.UNDISPATCHED) {
            actions.updateAutorecWithEpisodes(DvrMutationAction.UpdateAutorec(
                requireNotNull(session.observation.value.currentSession), rule, AutorecRuleUpdate(name = "Changed")),
                mapOf(EventId(7) to true, EventId(8) to false), session.observation, notices, context)
        }
        assertFalse(saving.isCompleted)
        val enabled = DvrEntry.create(DvrEntryId(11), eventId = EventId(7), autorecRuleId = rule.id,
            state = DvrEntryState.SCHEDULED, enabled = false)
        val disabled = DvrEntry.create(DvrEntryId(12), eventId = EventId(8), autorecRuleId = rule.id,
            state = DvrEntryState.SCHEDULED, enabled = true)
        publishAutomaticEntries(session, listOf(enabled, disabled), listOf(rule))
        assertEquals(AutorecSaveOutcome.Applied(rule.id), saving.await())
        assertEquals(mapOf(enabled.id to DvrEntryUpdate(enabled = true),
            disabled.id to DvrEntryUpdate(enabled = false)), requests.toMap())
        assertEquals(2, requests.size)

        requests.clear()
        publishAutomaticEntries(session, listOf(old), listOf(rule))
        session.dvrRepository.scriptUpdateAutorecRule(DvrMutationResult.Confirmed(Unit))
        var outcome: AutorecSaveOutcome? = null
        val baseline = rule.toDraft()
        val editor = AutomaticRecordingEditorState(baseline, this,
            loadPreview = { _, _, _, _ -> AutorecPreviewState.Matches(listOf(AutorecPreviewRow(
                old.eventId, old.id, null, null, null, automaticRecordingNow, null, AutorecEpisodeState.SKIPPED,
            )), 0, false) },
            saveDraft = { draft, choices ->
                actions.updateAutorecWithEpisodes(DvrMutationAction.UpdateAutorec(
                    requireNotNull(session.observation.value.currentSession), rule, draft.toUpdateRequest(baseline)),
                    choices, session.observation, notices, context).also { outcome = it }
            }, now = { automaticRecordingNow }, zone = automaticRecordingZone,
            editing = true, deleteRule = { DvrMutationResult.Confirmed(Unit) })
        runCurrent()
        assertFalse(editor.changed)
        editor.updateDraft { it.copy(removal = DvrRemoval.Forever) }
        runCurrent()
        editor.submit()
        runCurrent()
        val recreated = DvrEntry.create(DvrEntryId(13), eventId = old.eventId, autorecRuleId = rule.id,
            state = DvrEntryState.SCHEDULED, enabled = true)
        publishAutomaticEntries(session, listOf(recreated), listOf(rule))
        runCurrent()
        assertEquals(listOf(recreated.id to DvrEntryUpdate(enabled = false)), requests)
        assertEquals(AutorecSaveOutcome.Applied(rule.id), outcome)
        assertTrue(editor.closed)
        editor.dispose()

        for ((previewFails, intermediateRemoved) in listOf(false to false, true to false, true to true)) {
            val explicit = DvrEntry.create(DvrEntryId(20), eventId = EventId(8), autorecRuleId = rule.id,
                state = DvrEntryState.SCHEDULED, enabled = true)
            val gatedSession = automaticSession(listOf(rule), listOf(old, explicit))
            gatedSession.dvrRepository.scriptUpdateAutorecRule(DvrMutationResult.Confirmed(Unit))
            val appliedChoices = mutableListOf<Pair<DvrEntryId, DvrEntryUpdate>>()
            val gatedActions = automaticActions(gatedSession) { _, id, update ->
                appliedChoices += id to update
                DvrMutationResult.Confirmed(Unit)
            }
            val rows = listOf(
                AutorecPreviewRow(old.eventId, old.id, null, null, null, automaticRecordingNow, null,
                    AutorecEpisodeState.SKIPPED),
                AutorecPreviewRow(explicit.eventId, explicit.id, null, null, null, automaticRecordingNow, null,
                    AutorecEpisodeState.SCHEDULED),
            )
            val previewGate = CompletableDeferred<AutorecPreviewState>()
            var gatedOutcome: AutorecSaveOutcome? = null
            val gatedEditor = AutomaticRecordingEditorState(baseline, this,
                loadPreview = { draft, _, _, _ ->
                    when {
                        draft == baseline -> AutorecPreviewState.Matches(rows, 1, false)
                        draft.pattern.words == "narrow" -> AutorecPreviewState.Matches(
                            listOf(rows[0].copy(state = AutorecEpisodeState.REMOVED), rows[1]), 1, false)
                        else -> previewGate.await()
                    }
                },
                saveDraft = { draft, choices ->
                    gatedActions.updateAutorecWithEpisodes(DvrMutationAction.UpdateAutorec(
                        requireNotNull(gatedSession.observation.value.currentSession), rule,
                        draft.toUpdateRequest(baseline)), choices, gatedSession.observation, notices, context)
                        .also { gatedOutcome = it }
                }, now = { automaticRecordingNow }, zone = automaticRecordingZone,
                editing = true, deleteRule = { DvrMutationResult.Confirmed(Unit) })
            try {
                runCurrent()
                if (intermediateRemoved) {
                    gatedEditor.updateDraft { it.copy(pattern = it.pattern.copy(words = "narrow")) }
                    runCurrent()
                    assertEquals(AutorecEpisodeState.REMOVED,
                        (gatedEditor.preview as AutorecPreviewState.Matches).rows.first().state)
                } else gatedEditor.toggleEpisode(rows[1])
                gatedEditor.updateDraft { it.copy(pattern = it.pattern.copy(words = "news updated")) }
                runCurrent()
                assertEquals(AutorecPreviewState.Loading, gatedEditor.preview)
                gatedEditor.submit()
                runCurrent()
                assertTrue(gatedEditor.busy)
                assertEquals(0, gatedSession.calls.count { it == FakeSessionCall.DVR_UPDATE_AUTOREC_RULE })
                previewGate.complete(if (previewFails) AutorecPreviewState.Failed(TvheadendFailure.TIMEOUT)
                    else AutorecPreviewState.Matches(listOf(rows[0].copy(state = AutorecEpisodeState.REMOVED),
                        rows[1]), 1, false))
                runCurrent()
                assertEquals(1, gatedSession.calls.count { it == FakeSessionCall.DVR_UPDATE_AUTOREC_RULE })
                val newSkipped = DvrEntry.create(DvrEntryId(30), eventId = old.eventId, autorecRuleId = rule.id,
                    state = DvrEntryState.SCHEDULED, enabled = true)
                val newExplicit = DvrEntry.create(DvrEntryId(31), eventId = explicit.eventId,
                    autorecRuleId = rule.id, state = DvrEntryState.SCHEDULED, enabled = true)
                publishAutomaticEntries(gatedSession, listOf(newSkipped, newExplicit), listOf(rule))
                runCurrent()
                val expected = (if (intermediateRemoved) emptyList()
                    else listOf(newExplicit.id to DvrEntryUpdate(enabled = false))) +
                    if (previewFails) listOf(newSkipped.id to DvrEntryUpdate(enabled = false)) else emptyList()
                assertEquals(expected.toMap(), appliedChoices.toMap())
                assertEquals(expected.size, appliedChoices.size)
                assertEquals(AutorecSaveOutcome.Applied(rule.id), gatedOutcome)
                assertTrue(gatedEditor.closed)
            } finally {
                previewGate.complete(AutorecPreviewState.Failed(TvheadendFailure.TIMEOUT))
                gatedEditor.dispose()
            }
        }
    }

    private fun automaticSession(
        rules: List<AutorecRule> = emptyList(),
        entries: List<DvrEntry> = emptyList(),
    ): FakeTvheadendSession = FakeTvheadendSession(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = entries, autorecRules = rules)),
    ))

    private fun publishAutomaticEntries(
        session: FakeTvheadendSession,
        entries: List<DvrEntry>,
        rules: List<AutorecRule> = emptyList(),
    ) {
        val current = session.observation.value
        session.publish(SessionObservation.create(sessionState = current.sessionState,
            channelState = current.channelState, epgState = current.epgState,
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = entries, autorecRules = rules))))
    }

    @OptIn(ExperimentalTvheadendApi::class)
    private fun automaticActions(
        session: FakeTvheadendSession,
        update: suspend (CurrentSessionObservation, DvrEntryId, DvrEntryUpdate) -> DvrMutationResult<Unit>,
    ): DvrMutationActions = DvrMutationActions(
        scheduleEntry = session.dvrRepository::scheduleEntry,
        stopEntry = session.dvrRepository::stopEntry,
        cancelEntry = session.dvrRepository::cancelEntry,
        deleteEntry = session.dvrRepository::deleteEntry,
        updateWatchStateCommand = session.dvrRepository::updateWatchState,
        createAutorecRule = session.dvrRepository::createAutorecRule,
        updateAutorecRule = session.dvrRepository::updateAutorecRule,
        updateEntry = update,
    )

    @Test fun keepTimeSurvivesProgressButSkipRequiresCurrentlyScheduledEntry() {
        val entry = DvrEntry.create(DvrEntryId(7), state = DvrEntryState.RECORDING, dataSizeBytes = 10)
        val initial = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED,
                dvrWrite = CapabilityAccess.ALLOWED,
            )),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))),
        )
        val session = FakeTvheadendSession(initial)
        val source = session.observation.value
        val captured = requireNotNull(source.currentSession)
        session.publish(SessionObservation.create(
            sessionState = initial.sessionState,
            channelState = initial.channelState,
            epgState = initial.epgState,
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(
                DvrEntry.create(entry.id, state = DvrEntryState.RECORDING, dataSizeBytes = 20, playCount = 1),
            ))),
        ))
        val keep = DvrMutationAction.SetEntryRemoval(captured, entry, DvrRemoval.Forever)
        assertSame(keep, currentDvrMutation(keep, source, session.observation.value))
        val scheduled = DvrEntry.create(entry.id, state = DvrEntryState.SCHEDULED)
        val skip = DvrMutationAction.SetEntryEnabled(captured, scheduled, false)
        assertEquals(null, currentDvrMutation(skip, source, session.observation.value))
    }

    @Test fun newActionsDropChangedTargetsAndExpiredSessionsAndRulesUseRuleFeedback() = runTest {
        val auto = AutorecRule.create(AutorecRuleId("auto"), title = "news")
        val timer = TimerecRule.create(TimerecRuleId("timer"))
        val entry = DvrEntry.create(DvrEntryId(7), state = DvrEntryState.SCHEDULED)
        val initial = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry),
                autorecRules = listOf(auto), timerecRules = listOf(timer))))
        val session = FakeTvheadendSession(initial)
        val source = session.observation.value
        val captured = requireNotNull(source.currentSession)
        val edits = listOf(
            DvrMutationAction.UpdateAutorec(captured, auto, AutorecRuleUpdate(enabled = false)),
            DvrMutationAction.DeleteAutorec(captured, auto),
            DvrMutationAction.UpdateTimerec(captured, timer, TimerecRuleUpdate(enabled = false)),
            DvrMutationAction.DeleteTimerec(captured, timer),
            DvrMutationAction.SetEntryEnabled(captured, entry, false),
            DvrMutationAction.SetEntryRemoval(captured, entry, DvrRemoval.Forever),
        )
        edits.forEach { assertSame(it, currentDvrMutation(it, source, source)) }
        session.publish(SessionObservation.create(sessionState = initial.sessionState,
            channelState = initial.channelState, epgState = initial.epgState,
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create())))
        edits.forEach { assertEquals(null, currentDvrMutation(it, source, session.observation.value)) }
        session.replaceGeneration(initial)
        val creates = listOf(DvrMutationAction.CreateAutorec(captured, AutorecRuleCreate("news")),
            DvrMutationAction.CreateTimerec(captured, TimerecRuleCreate("timer")))
        creates.forEach { assertEquals(null, currentDvrMutation(it, source, session.observation.value)) }
        val context = NoticeContext(1, null)
        val notices = NoticeCenter({ testScheduler.currentTime }, { context })
        val failed = DvrMutationResult.Failed(TvheadendFailure.TIMEOUT, mayHaveApplied = true)
        notices.postDvrFailure(edits.first(), failed, context)
        assertEquals(Notice.DvrRuleActionFailed(DvrMutationKind.UPDATE, DvrMutationFeedback.TIMEOUT),
            notices.state.value.candidate?.notice)
        notices.postDvrFailure(edits.last(), failed, context)
        assertEquals(Notice.DvrActionFailed(DvrMutationKind.UPDATE, DvrMutationFeedback.TIMEOUT),
            notices.state.value.candidate?.notice)
        val received = mutableListOf<Any>()
        var toggleResult: DvrMutationResult<Unit> = DvrMutationResult.Confirmed(Unit)
        val toggles = DvrMutationActions(
            scheduleEntry = { _, _ -> error("Not scheduling") },
            stopEntry = { _, _ -> error("Not stopping") },
            cancelEntry = { _, _ -> error("Not cancelling") },
            deleteEntry = { _, _ -> error("Not deleting") },
            updateWatchStateCommand = { _, _, _ -> error("Not progress") },
            updateAutorecRule = { _, _, update -> received += update; toggleResult },
            updateTimerecRule = { _, _, update -> received += update; toggleResult },
        )
        val live = FakeTvheadendSession(initial)
        val liveSession = requireNotNull(live.observation.value.currentSession)
        val items = automaticRecordingList(live.observation.value).items
        for (item in items) {
            assertTrue(toggles.setRuleEnabled(item, false, liveSession, live.observation.value,
                notices, context) is DvrMutationResult.Confirmed)
        }
        assertEquals(setOf(AutorecRuleUpdate(enabled = false), TimerecRuleUpdate(enabled = false)), received.toSet())
        assertEquals(2, received.size)
        for (item in items) assertTrue(toggles.setRuleEnabled(item, true, liveSession, live.observation.value,
            notices, context) is DvrMutationResult.Confirmed)
        assertEquals(2, received.size)
        live.publish(SessionObservation.create(sessionState = initial.sessionState,
            channelState = initial.channelState, epgState = initial.epgState,
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(
                autorecRules = listOf(AutorecRule.create(auto.id, title = auto.title, enabled = false)),
                timerecRules = listOf(TimerecRule.create(timer.id, enabled = false)),
            ))))
        assertSame(liveSession, live.observation.value.currentSession)
        val disabledItems = automaticRecordingList(live.observation.value).items
        for (item in disabledItems) assertTrue(toggles.setRuleEnabled(item, true, liveSession, live.observation.value,
            notices, context) is DvrMutationResult.Confirmed)
        assertEquals(setOf(AutorecRuleUpdate(enabled = true), TimerecRuleUpdate(enabled = true)),
            received.drop(2).toSet())
        assertEquals(4, received.size)
        toggleResult = DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, false)
        for (item in disabledItems) assertEquals(toggleResult,
            toggles.setRuleEnabled(item, true, liveSession, live.observation.value, notices, context))
        assertEquals(6, received.size)
        assertEquals(Notice.DvrRuleActionFailed(DvrMutationKind.UPDATE, DvrMutationFeedback.PERMISSION_DENIED),
            notices.state.value.candidate?.notice)
        val removed = SessionObservation.create(sessionState = initial.sessionState,
            channelState = initial.channelState, epgState = initial.epgState,
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()))
        live.publish(removed)
        assertSame(liveSession, live.observation.value.currentSession)
        for (item in disabledItems) assertEquals(DvrMutationResult.Failed(TvheadendFailure.NOT_READY, false),
            toggles.setRuleEnabled(item, false, liveSession, live.observation.value, notices, context))
        for (item in disabledItems) assertEquals(DvrMutationResult.Failed(TvheadendFailure.NOT_READY, false),
            toggles.setRuleEnabled(item, true, liveSession, live.observation.value, notices, context))
        assertEquals(6, received.size)
        assertEquals(Notice.DvrRuleActionFailed(DvrMutationKind.UPDATE, DvrMutationFeedback.CONNECTION_UNAVAILABLE),
            notices.state.value.candidate?.notice)
    }

    @OptIn(TvheadendTestingApi::class, ExperimentalCoroutinesApi::class)
    @Test fun ruleTimeSkipAndKeepActionsDispatchCapturedRequests() = runTest {
        val session = currentSession()
        val auto = AutorecRule.create(AutorecRuleId("auto"), title = "news")
        val timer = TimerecRule.create(TimerecRuleId("timer"))
        val entry = DvrEntry.create(DvrEntryId(7), state = DvrEntryState.SCHEDULED)
        val createdAuto = AutorecRuleCreate("news")
        val updatedAuto = AutorecRuleUpdate(enabled = false)
        val createdTimer = TimerecRuleCreate("timer")
        val updatedTimer = TimerecRuleUpdate(enabled = false)
        val time = DvrScheduleRequest(DvrSchedule.ExplicitTime(ChannelId(1),
            Instant.fromEpochSeconds(100), Instant.fromEpochSeconds(200)), title = "Timer")
        val received = mutableListOf<Any>()
        val actions = DvrMutationActions(
            scheduleEntry = { s, r -> assertSame(session, s); received += r; DvrMutationResult.Confirmed(entry.id) },
            stopEntry = { _, _ -> error("Unexpected stop") },
            cancelEntry = { _, _ -> error("Unexpected cancel") },
            deleteEntry = { _, _ -> error("Unexpected delete") },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch") },
            createAutorecRule = { s, r -> assertSame(session, s); received += r; DvrMutationResult.Confirmed(auto.id) },
            updateAutorecRule = { s, id, r -> assertSame(session, s); assertEquals(auto.id, id); received += r; DvrMutationResult.Confirmed(Unit) },
            deleteAutorecRule = { s, id -> assertSame(session, s); received += id; DvrMutationResult.Confirmed(Unit) },
            createTimerecRule = { s, r -> assertSame(session, s); received += r; DvrMutationResult.Confirmed(timer.id) },
            updateTimerecRule = { s, id, r -> assertSame(session, s); assertEquals(timer.id, id); received += r; DvrMutationResult.Confirmed(Unit) },
            deleteTimerecRule = { s, id -> assertSame(session, s); received += id; DvrMutationResult.Confirmed(Unit) },
            updateEntry = { s, id, r -> assertSame(session, s); assertEquals(entry.id, id); received += r; DvrMutationResult.Confirmed(Unit) },
        )
        listOf(
            DvrMutationAction.CreateAutorec(session, createdAuto),
            DvrMutationAction.UpdateAutorec(session, auto, updatedAuto),
            DvrMutationAction.DeleteAutorec(session, auto),
            DvrMutationAction.CreateTimerec(session, createdTimer),
            DvrMutationAction.UpdateTimerec(session, timer, updatedTimer),
            DvrMutationAction.DeleteTimerec(session, timer),
            DvrMutationAction.ScheduleByTime(session, time),
            DvrMutationAction.SetEntryEnabled(session, entry, false),
            DvrMutationAction.SetEntryEnabled(session, entry, true),
            DvrMutationAction.SetEntryRemoval(session, entry, DvrRemoval.Forever),
        ).forEach { assertEquals(DvrMutationFeedback.CONFIRMED, actions.execute(it).toDvrMutationFeedback()) }
        assertEquals(listOf(createdAuto, updatedAuto, auto.id, createdTimer, updatedTimer, timer.id,
            time, DvrEntryUpdate(enabled = false), DvrEntryUpdate(enabled = true),
            DvrEntryUpdate(removal = DvrRemoval.Forever)), received)
        val publishedUpdates = mutableListOf<Pair<DvrRemoval, DvrEntryUpdate>>()
        val removals = listOf(DvrRemoval.Days(8), DvrRemoval.Forever)
        for (removal in removals) {
            val opening = DvrEntry.create(entry.id, state = DvrEntryState.COMPLETED, removal = removal,
                stop = Instant.fromEpochSeconds(100), files = listOf(
                    TvheadendTestResultFactory.recordingFile(serverPath = "test.ts")))
            val source = automaticSession(entries = listOf(opening))
            val captured = requireNotNull(source.observation.value.currentSession)
            val forever = recordingKeepUntilOptions(opening, 200, ZoneId.of("UTC"))
                .single { it.choice == RecordingKeepUntilChoice.FOREVER }
            publishAutomaticEntries(source, listOf(DvrEntry.create(opening.id, state = opening.state,
                stop = opening.stop, files = opening.files, removal = removal,
                retention = DvrRetention.Days(3))))
            val keep = automaticActions(source) { _, id, update ->
                assertEquals(opening.id, id)
                publishedUpdates += removal to update
                DvrMutationResult.Confirmed(Unit)
            }
            val notices = NoticeCenter({ testScheduler.currentTime }) { NoticeContext(0, null) }
            assertTrue(keep.chooseRecordingRemoval(opening.id, captured, source.observation.value, forever, notices))
        }
        assertEquals(removals.map { Triple(it, DvrRemoval.Forever,
            DvrRetention.UntilFilesRemoved) },
            publishedUpdates.map { Triple(it.first, it.second.removal, it.second.retention) })
    }

    @Test
    fun createStopCancelAndDeleteDispatchCapturedTypedActions() = runTest {
        val capability = currentSession()
        var scheduledCapability: CurrentSessionObservation? = null
        var scheduledRequest: DvrScheduleRequest? = null
        var stopped: Pair<CurrentSessionObservation, DvrEntryId>? = null
        var cancelled: Pair<CurrentSessionObservation, DvrEntryId>? = null
        var deleted: Pair<CurrentSessionObservation, DvrEntryId>? = null
        val actions = DvrMutationActions(
            scheduleEntry = { currentSession, request ->
                scheduledCapability = currentSession
                scheduledRequest = request
                DvrMutationResult.AcceptedButUnconfirmed(DvrEntryId(70))
            },
            stopEntry = { currentSession, id ->
                stopped = currentSession to id
                DvrMutationResult.Confirmed(Unit)
            },
            cancelEntry = { currentSession, id ->
                cancelled = currentSession to id
                DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false)
            },
            deleteEntry = { currentSession, id ->
                deleted = currentSession to id
                DvrMutationResult.Failed(TvheadendFailure.TRANSPORT_UNAVAILABLE, mayHaveApplied = false)
            },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
        )
        val target = ProgrammeRecordingTarget(
            eventId = EventId(7),
            channelId = null,
            start = 100,
            stop = 200,
            title = "News",
            currentSession = capability,
        )
        val configId = DvrConfigId("primary")
        val recordingId = DvrEntryId(9)

        assertEquals(
            DvrMutationFeedback.ACCEPTED_UNCONFIRMED,
            actions.execute(DvrMutationAction.CreateProgramme(target, configId)).toDvrMutationFeedback(),
        )
        assertEquals(
            DvrMutationFeedback.CONFIRMED,
            actions.execute(DvrMutationAction.Stop(capability, recordingId)).toDvrMutationFeedback(),
        )
        assertEquals(
            DvrMutationFeedback.PERMISSION_DENIED,
            actions.execute(DvrMutationAction.Cancel(capability, recordingId)).toDvrMutationFeedback(),
        )
        assertEquals(
            DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            actions.execute(DvrMutationAction.Delete(capability, recordingId)).toDvrMutationFeedback(),
        )

        assertSame(capability, scheduledCapability)
        assertEquals(
            DvrScheduleRequest(
                schedule = DvrSchedule.Programme(EventId(7)),
                configId = configId,
                title = "News",
            ),
            scheduledRequest,
        )
        assertEquals(capability to recordingId, stopped)
        assertEquals(capability to recordingId, cancelled)
        assertEquals(capability to recordingId, deleted)
    }

    @Test
    fun stopDispatchesOnlyStopEntry() = runTest {
        val capability = currentSession()
        val calls = mutableListOf<String>()
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> calls += "schedule"; DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            stopEntry = { _, _ -> calls += "stop"; DvrMutationResult.AcceptedButUnconfirmed(Unit) },
            cancelEntry = { _, _ -> calls += "cancel"; DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            deleteEntry = { _, _ -> calls += "delete"; DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
        )

        val feedback = actions.execute(DvrMutationAction.Stop(capability, DvrEntryId(4))).toDvrMutationFeedback()

        assertEquals(listOf("stop"), calls)
        assertEquals(DvrMutationFeedback.ACCEPTED_UNCONFIRMED, feedback)
    }

    @Test
    fun watchStateDispatchesCapturedSessionAndDesiredStateOnceWithoutRetryingFailure() = runTest {
        val capability = currentSession()
        val recordingId = DvrEntryId(9)
        val calls = mutableListOf<Triple<CurrentSessionObservation, DvrEntryId, Boolean>>()
        val failure = DvrProgressResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true)
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> error("Unexpected schedule") },
            stopEntry = { _, _ -> error("Unexpected stop") },
            cancelEntry = { _, _ -> error("Unexpected cancel") },
            deleteEntry = { _, _ -> error("Unexpected delete") },
            updateWatchStateCommand = { session, id, watched ->
                calls += Triple(session, id, watched)
                if (watched) DvrProgressResult.Accepted else failure
            },
        )

        assertSame(DvrProgressResult.Accepted, actions.updateWatchState(capability, recordingId, true))
        assertSame(failure, actions.updateWatchState(capability, recordingId, false))
        assertEquals(listOf(Triple(capability, recordingId, true), Triple(capability, recordingId, false)), calls)
    }

    @Test
    fun watchStateFailuresPreserveExistingDvrFeedbackAndDispatchEvidence() {
        val cases = listOf(
            Triple(TvheadendFailure.ACCESS_DENIED, false, DvrMutationFeedback.PERMISSION_DENIED),
            Triple(TvheadendFailure.CONNECTION_LIMIT, false, DvrMutationFeedback.CONNECTION_LIMIT),
            Triple(TvheadendFailure.SERVER_REJECTED, false, DvrMutationFeedback.REJECTED),
            Triple(TvheadendFailure.NOT_SUPPORTED, false, DvrMutationFeedback.NOT_SUPPORTED),
            Triple(TvheadendFailure.TIMEOUT, true, DvrMutationFeedback.TIMEOUT),
            Triple(TvheadendFailure.NOT_READY, false, DvrMutationFeedback.CONNECTION_UNAVAILABLE),
            Triple(TvheadendFailure.OBSERVATION_EXPIRED, false, DvrMutationFeedback.CONNECTION_UNAVAILABLE),
            Triple(TvheadendFailure.OBSERVATION_EXPIRED, true, DvrMutationFeedback.UNKNOWN_OUTCOME),
            Triple(TvheadendFailure.CONNECTION_CHANGED, false, DvrMutationFeedback.CONNECTION_UNAVAILABLE),
            Triple(TvheadendFailure.CONNECTION_CHANGED, true, DvrMutationFeedback.UNKNOWN_OUTCOME),
            Triple(TvheadendFailure.TRANSPORT_UNAVAILABLE, false, DvrMutationFeedback.CONNECTION_UNAVAILABLE),
            Triple(TvheadendFailure.TRANSPORT_UNAVAILABLE, true, DvrMutationFeedback.UNKNOWN_OUTCOME),
            Triple(TvheadendFailure.MALFORMED_RESPONSE, false, DvrMutationFeedback.REJECTED),
            Triple(TvheadendFailure.MALFORMED_RESPONSE, true, DvrMutationFeedback.UNKNOWN_OUTCOME),
        )
        cases.forEach { (failure, mayHaveApplied, expected) ->
            val feedback = DvrProgressResult.Failed(failure, mayHaveApplied).toDvrMutationFeedback()
            assertEquals("$failure mayHaveApplied=$mayHaveApplied", expected, feedback)
            assertEquals(DvrMutationResult.Failed(failure, mayHaveApplied).toDvrMutationFeedback(), feedback)
            assertTrue(feedback.isFailure)
        }
    }

    @Test
    fun missingCapturedActionPresentsNotReadyWithoutDispatch() = runTest {
        var dispatchCount = 0
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
            },
            stopEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
            },
            cancelEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
            },
            deleteEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
            },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
        )

        val feedback = actions.execute(null).toDvrMutationFeedback()

        assertEquals(DvrMutationFeedback.CONNECTION_UNAVAILABLE, feedback)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun everyReleasedMutationResultHasOneStablePresentation() {
        val accepted = mapOf(
            DvrMutationResult.Confirmed(Unit) to DvrMutationFeedback.CONFIRMED,
            DvrMutationResult.AcceptedButUnconfirmed(Unit) to DvrMutationFeedback.ACCEPTED_UNCONFIRMED,
        )
        val failures = mapOf(
            DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false) to DvrMutationFeedback.PERMISSION_DENIED,
            DvrMutationResult.Failed(TvheadendFailure.CONNECTION_LIMIT, mayHaveApplied = false) to DvrMutationFeedback.CONNECTION_LIMIT,
            DvrMutationResult.Failed(TvheadendFailure.SERVER_REJECTED, mayHaveApplied = false) to DvrMutationFeedback.REJECTED,
            DvrMutationResult.Failed(TvheadendFailure.NOT_SUPPORTED, mayHaveApplied = false) to DvrMutationFeedback.NOT_SUPPORTED,
            DvrMutationResult.Failed(TvheadendFailure.TIMEOUT, mayHaveApplied = true) to DvrMutationFeedback.TIMEOUT,
            DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) to DvrMutationFeedback.UNKNOWN_OUTCOME,
            DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            DvrMutationResult.Failed(TvheadendFailure.OBSERVATION_EXPIRED, mayHaveApplied = false) to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            DvrMutationResult.Failed(TvheadendFailure.TRANSPORT_UNAVAILABLE, mayHaveApplied = false) to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
        )

        accepted.forEach { (result, expected) ->
            val feedback = result.toDvrMutationFeedback()
            assertEquals(expected, feedback)
            assertFalse(feedback.isFailure)
        }
        failures.forEach { (result, expected) ->
            val feedback = result.toDvrMutationFeedback()
            assertEquals(expected, feedback)
            assertTrue(feedback.isFailure)
        }
    }

    @Test
    fun connectionChangesAndUnlistedFailuresPreserveMutationStageEvidence() {
        val cases = listOf(
            DvrMutationResult.Failed(TvheadendFailure.TRANSPORT_UNAVAILABLE, mayHaveApplied = true) to DvrMutationFeedback.UNKNOWN_OUTCOME,
            DvrMutationResult.Failed(TvheadendFailure.CONNECTION_CHANGED, mayHaveApplied = true) to DvrMutationFeedback.UNKNOWN_OUTCOME,
            DvrMutationResult.Failed(TvheadendFailure.CONNECTION_CHANGED, mayHaveApplied = false) to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) to DvrMutationFeedback.UNKNOWN_OUTCOME,
            DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = false) to DvrMutationFeedback.REJECTED,
        )
        cases.forEach { (result, expected) ->
            assertEquals(expected, result.toDvrMutationFeedback())
            assertTrue(result.toDvrMutationFeedback().isFailure)
        }
    }

    @Test
    fun malformedMutationIsUncertainAndNeverRepeated() = runTest {
        val calls = mutableListOf<String>()
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> calls += "schedule"; DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) },
            stopEntry = { _, _ -> calls += "stop"; DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) },
            cancelEntry = { _, _ -> calls += "cancel"; DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) },
            deleteEntry = { _, _ -> calls += "delete"; DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
        )
        val current = currentSession()
        val target = ProgrammeRecordingTarget(EventId(7), null, 100, 200, "News", current)
        val actionsToRun = listOf(
            DvrMutationAction.CreateProgramme(target, null),
            DvrMutationAction.Stop(current, DvrEntryId(4)),
            DvrMutationAction.Cancel(current, DvrEntryId(4)),
            DvrMutationAction.Delete(current, DvrEntryId(4)),
        )
        actionsToRun.forEach { action ->
            assertEquals(DvrMutationFeedback.UNKNOWN_OUTCOME, actions.execute(action).toDvrMutationFeedback())
        }
        assertEquals(listOf("schedule", "stop", "cancel", "delete"), calls)
    }

    private fun currentSession(): CurrentSessionObservation = FakeSessionObservation(
        SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.ALLOWED,
                ),
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        ),
    ).captureCurrentSession()
}
