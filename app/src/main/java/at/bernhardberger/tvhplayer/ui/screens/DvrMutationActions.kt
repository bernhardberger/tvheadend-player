package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrProgressResult
import at.bernhardberger.tvheadend.sdk.core.DvrRepository
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvheadend.sdk.core.AutorecRule
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleCreate
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleUpdate
import at.bernhardberger.tvheadend.sdk.core.TimerecRule
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleId
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleCreate
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleUpdate
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryUpdate
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.DvrRetention
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.toDvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.core.recordUnavailableReason
import at.bernhardberger.tvhplayer.core.recordingWasRemoved
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import at.bernhardberger.tvhplayer.core.RecordingKeepUntilChoice
import at.bernhardberger.tvhplayer.core.folderActionApplies
import at.bernhardberger.tvhplayer.core.dvrMutationStateIsCurrent
import at.bernhardberger.tvhplayer.core.dvrMutationTargetIsCurrent
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvhplayer.core.programmeDetailsActions
import at.bernhardberger.tvhplayer.core.recordingRuleAvailable
import at.bernhardberger.tvhplayer.core.programmeRecordingRecordKeyActions
import at.bernhardberger.tvheadend.sdk.core.EpgRepository
import at.bernhardberger.tvheadend.sdk.core.EpgSearchRequest
import at.bernhardberger.tvheadend.sdk.core.EpgSearchResult
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.AutorecDraft
import at.bernhardberger.tvhplayer.core.AutorecPreviewState
import at.bernhardberger.tvhplayer.core.previewMatches
import at.bernhardberger.tvhplayer.core.previewSearchRequest
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import at.bernhardberger.tvhplayer.core.chooseDvrConfig
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal val AutorecEpisodeWaitTimeout = 10.seconds

@OptIn(ExperimentalTvheadendApi::class)
internal fun SessionObservation.keepUntilEntry(
    id: DvrEntryId,
    openingSession: CurrentSessionObservation?,
): DvrEntry? {
    if (openingSession == null || currentSession !== openingSession) return null
    val writable = (sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    return dvrEntry(id)?.takeIf { at.bernhardberger.tvhplayer.core.recordingKeepUntilAvailable(it, writable) }
}

/** True closes the level, including obsolete targets and choices already published by the server. */
internal suspend fun DvrMutationActions.chooseRecordingRemoval(
    id: DvrEntryId,
    openingSession: CurrentSessionObservation?,
    observation: SessionObservation,
    option: at.bernhardberger.tvhplayer.core.RecordingKeepUntilOption,
    notices: NoticeCenter,
): Boolean {
    val entry = observation.keepUntilEntry(id, openingSession) ?: return true
    val removal = option.removal
    val retention = DvrRetention.UntilFilesRemoved.takeIf {
        option.choice == RecordingKeepUntilChoice.FOREVER &&
            entry.retention is DvrRetention.Days
    }
    return when {
        option.choice == RecordingKeepUntilChoice.CURRENT -> true
        removal == null -> false
        entry.removal == removal && (retention == null || entry.retention == retention) -> true
        else -> {
            val mutation = DvrMutationAction.SetEntryRemoval(requireNotNull(openingSession), entry, removal, retention)
            val context = notices.context()
            val result = execute(mutation)
            notices.postDvrFailure(mutation, result, context)
            result !is DvrMutationResult.Failed
        }
    }
}

/** Failed retains SDK mayHaveApplied: an ambiguous rule failure never means "nothing was created". */
internal sealed interface AutorecSaveOutcome {
    data class RuleFailed(val result: DvrMutationResult.Failed) : AutorecSaveOutcome
    data class Applied(val ruleId: AutorecRuleId) : AutorecSaveOutcome
    data class PartiallyApplied(val ruleId: AutorecRuleId, val unappliedEventIds: Set<EventId>) : AutorecSaveOutcome
}

internal class AutorecPreviewLoader(
    private val search: suspend (CurrentSessionObservation, EpgSearchRequest) -> EpgSearchResult,
) {
    constructor(repository: EpgRepository) : this(repository::search)

    /** The caller owns cancellation and discarding an obsolete draft's result. */
    suspend fun load(
        draft: AutorecDraft,
        currentSession: CurrentSessionObservation,
        observation: SessionObservation,
        ruleId: AutorecRuleId?,
        now: Instant,
        zone: ZoneId,
        baseline: AutorecDraft? = null,
    ): AutorecPreviewState {
        require(ruleId == null || baseline != null) { "An existing rule preview requires its baseline draft" }
        val request = draft.previewSearchRequest(baseline)
        val events = if (request == null) emptyList() else when (val result = search(currentSession, request)) {
            is EpgSearchResult.Failed -> return AutorecPreviewState.Failed(result.failure)
            is EpgSearchResult.Available -> result.events
        }
        return if (request == null && ruleId == null) AutorecPreviewState.NothingToSearch
        else draft.previewMatches(events,
            (observation.dvrState as? DvrRepositoryState.Current)?.snapshot?.entries.orEmpty(),
            ruleId, now, zone, baseline)
    }
}
/** The same eligibility as the programme's action row; Record never acts on a retained event. */
@OptIn(ExperimentalTvheadendApi::class)
internal fun programmeRecordKeyAction(event: EpgEvent, observation: SessionObservation, nowSec: Long): BrowseContextAction? {
    val current = observation.event(event.id) ?: return null
    val recording = observation.dvrEntryForEvent(current)?.takeUnless(::recordingWasRemoved)
    val canModify = observation.currentSession != null && observation.dvrState is DvrRepositoryState.Current &&
        (observation.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    return programmeDetailsActions(current, nowSec, recording,
        recording?.let(observation::recordingPlaybackAdmission), canModify,
        current.channelId?.let(observation::channel) != null, liveProgramme = true,
        ruleAvailable = recording?.let(observation::recordingRuleAvailable) == true)
        .firstOrNull { it == BrowseContextAction.RECORD || it in programmeRecordingRecordKeyActions }
}

/** Shared by the action row and dedicated Record key, with the screen's existing lifetime/lock. */
@OptIn(ExperimentalTvheadendApi::class)
internal fun NoticeCenter.postRecordUnavailable(event: EpgEvent?, observation: SessionObservation, nowSec: Long) {
    val access = (observation.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite
    val canModify = when {
        access == CapabilityAccess.DENIED -> false
        observation.currentSession != null && observation.dvrState is DvrRepositoryState.Current &&
            access == CapabilityAccess.ALLOWED -> true
        else -> null
    }
    post(Notice.RecordUnavailable(recordUnavailableReason(event, nowSec, canModify)), context())
}

/** Schedules the channel's current programme, or returns the details action to focus. */
@OptIn(ExperimentalTvheadendApi::class)
internal fun DvrMutationActions.recordChannelProgramme(
    channelId: ChannelId,
    currentObservation: () -> SessionObservation,
    nowSecProvider: () -> Long,
    settingsStore: UiSettingsStore,
    notices: NoticeCenter,
    scope: CoroutineScope,
    inFlight: MutableState<DvrMutationAction?>,
): BrowseContextAction? {
    val current = currentObservation()
    val event = current.eventAt(channelId, kotlin.time.Instant.fromEpochSeconds(nowSecProvider()))
    return when (val action = event?.let { programmeRecordKeyAction(it, current, nowSecProvider()) }) {
        BrowseContextAction.RECORD -> {
            current.currentSession?.let { capability ->
                launchMutation(DvrMutationAction.CreateProgramme(
                    requireNotNull(event).programmeRecordingTarget(capability), null),
                    current, currentObservation, nowSecProvider, settingsStore, notices, scope, inFlight)
            }
            null
        }
        BrowseContextAction.STOP, BrowseContextAction.CANCEL,
        BrowseContextAction.SKIP, BrowseContextAction.UNSKIP -> action
        else -> { notices.postRecordUnavailable(event, current, nowSecProvider()); null }
    }
}

/** Shared by the action row and dedicated Record key, with the screen's existing lifetime/lock. */
@OptIn(ExperimentalTvheadendApi::class)
internal fun DvrMutationActions.launchMutation(
    mutation: DvrMutationAction,
    sourceObservation: SessionObservation,
    currentObservation: () -> SessionObservation,
    nowSecProvider: () -> Long,
    settingsStore: UiSettingsStore,
    notices: NoticeCenter,
    scope: CoroutineScope,
    inFlight: MutableState<DvrMutationAction?>,
) {
    if (inFlight.value != null) return
    inFlight.value = mutation
    scope.launch(Dispatchers.Main) {
        try {
            val preferred = if (mutation is DvrMutationAction.CreateProgramme)
                settingsStore.settings.first().recordingProfileId else null
            val current = currentObservation()
            val request = currentDvrMutation(mutation, sourceObservation, current) ?: return@launch
            if (request is DvrMutationAction.CreateProgramme &&
                current.event(request.target.eventId)?.let {
                    programmeRecordKeyAction(it, current, nowSecProvider())
                } !=
                BrowseContextAction.RECORD) return@launch
            val configured = if (request is DvrMutationAction.CreateProgramme)
                request.copy(configId = chooseDvrConfig(current.currentDvrConfigurations(), preferred)) else request
            val context = notices.context()
            notices.postDvrFailure(configured, execute(configured), context)
        } finally {
            inFlight.value = null
        }
    }
}

internal sealed interface DvrMutationAction {
    data class CreateAutorec(
        val currentSession: CurrentSessionObservation,
        val request: AutorecRuleCreate,
    ) : DvrMutationAction
    data class UpdateAutorec(
        val currentSession: CurrentSessionObservation,
        val target: AutorecRule,
        /** Null means that only episode choices changed, not the rule itself. */
        val update: AutorecRuleUpdate?,
    ) : DvrMutationAction
    data class DeleteAutorec(
        val currentSession: CurrentSessionObservation,
        val target: AutorecRule,
    ) : DvrMutationAction
    data class CreateTimerec(
        val currentSession: CurrentSessionObservation,
        val request: TimerecRuleCreate,
    ) : DvrMutationAction
    data class UpdateTimerec(
        val currentSession: CurrentSessionObservation,
        val target: TimerecRule,
        val update: TimerecRuleUpdate,
    ) : DvrMutationAction
    data class DeleteTimerec(
        val currentSession: CurrentSessionObservation,
        val target: TimerecRule,
    ) : DvrMutationAction
    data class ScheduleByTime(
        val currentSession: CurrentSessionObservation,
        val request: DvrScheduleRequest,
    ) : DvrMutationAction {
        init { require(request.schedule is DvrSchedule.ExplicitTime) }
    }
    data class SetEntryEnabled(
        val currentSession: CurrentSessionObservation,
        val target: DvrEntry,
        val enabled: Boolean,
    ) : DvrMutationAction
    data class SetEntryRemoval(
        val currentSession: CurrentSessionObservation,
        val target: DvrEntry,
        val removal: DvrRemoval,
        val retention: DvrRetention? = null,
    ) : DvrMutationAction {
        init { require(removal != DvrRemoval.ServerDefault) }
    }

    data class CreateProgramme(
        val target: ProgrammeRecordingTarget,
        val configId: DvrConfigId?,
    ) : DvrMutationAction

    data class Stop(
        val currentSession: CurrentSessionObservation,
        val recordingId: DvrEntryId,
    ) : DvrMutationAction

    data class Cancel(
        val currentSession: CurrentSessionObservation,
        val recordingId: DvrEntryId,
    ) : DvrMutationAction

    data class Delete(
        val currentSession: CurrentSessionObservation,
        val recordingId: DvrEntryId,
    ) : DvrMutationAction
}

private fun SessionObservation.hasCurrentChannel(
    capturedSession: CurrentSessionObservation,
    channelId: ChannelId?,
): Boolean = currentSession === capturedSession && (channelId == null || channel(channelId) != null)

internal fun DvrMutationAction.recordingStateIsCurrent(observation: SessionObservation): Boolean = when (this) {
    is DvrMutationAction.CreateAutorec -> observation.hasCurrentChannel(
        currentSession, (request.channel as? RecordingRuleChannel.SpecificChannel)?.channelId,
    )
    is DvrMutationAction.CreateTimerec -> observation.hasCurrentChannel(
        currentSession, (request.channel as? RecordingRuleChannel.SpecificChannel)?.channelId,
    )
    is DvrMutationAction.ScheduleByTime -> observation.hasCurrentChannel(
        currentSession, (request.schedule as DvrSchedule.ExplicitTime).channelId,
    )
    is DvrMutationAction.UpdateAutorec -> dvrMutationTargetIsCurrent(currentSession, target, observation)
    is DvrMutationAction.DeleteAutorec -> dvrMutationTargetIsCurrent(currentSession, target, observation)
    is DvrMutationAction.UpdateTimerec -> dvrMutationTargetIsCurrent(currentSession, target, observation)
    is DvrMutationAction.DeleteTimerec -> dvrMutationTargetIsCurrent(currentSession, target, observation)
    is DvrMutationAction.SetEntryEnabled -> dvrMutationStateIsCurrent(
        currentSession, target.id, DvrEntryState.SCHEDULED, observation,
    )
    is DvrMutationAction.SetEntryRemoval -> target.state?.let {
        dvrMutationStateIsCurrent(currentSession, target.id, it, observation)
    } == true
    is DvrMutationAction.Cancel -> dvrMutationStateIsCurrent(
        currentSession, recordingId, DvrEntryState.SCHEDULED, observation,
    )
    is DvrMutationAction.Stop -> listOf(DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR).any {
        dvrMutationStateIsCurrent(currentSession, recordingId, it, observation)
    }
    is DvrMutationAction.CreateProgramme,
    is DvrMutationAction.Delete -> true
}

@OptIn(ExperimentalTvheadendApi::class)
internal class DvrMutationActions(
    private val scheduleEntry: suspend (
        CurrentSessionObservation,
        DvrScheduleRequest,
    ) -> DvrMutationResult<DvrEntryId>,
    private val stopEntry: suspend (
        CurrentSessionObservation,
        DvrEntryId,
    ) -> DvrMutationResult<Unit>,
    private val cancelEntry: suspend (
        CurrentSessionObservation,
        DvrEntryId,
    ) -> DvrMutationResult<Unit>,
    private val deleteEntry: suspend (
        CurrentSessionObservation,
        DvrEntryId,
    ) -> DvrMutationResult<Unit>,
    private val updateWatchStateCommand: suspend (
        CurrentSessionObservation,
        DvrEntryId,
        Boolean,
    ) -> DvrProgressResult,
    private val createAutorecRule: suspend (
        CurrentSessionObservation,
        AutorecRuleCreate,
    ) -> DvrMutationResult<AutorecRuleId> =
        { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    private val updateAutorecRule: suspend (
        CurrentSessionObservation,
        AutorecRuleId,
        AutorecRuleUpdate,
    ) -> DvrMutationResult<Unit> =
        { _, _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    private val deleteAutorecRule: suspend (CurrentSessionObservation, AutorecRuleId) -> DvrMutationResult<Unit> =
        { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    private val createTimerecRule: suspend (
        CurrentSessionObservation,
        TimerecRuleCreate,
    ) -> DvrMutationResult<TimerecRuleId> =
        { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    private val updateTimerecRule: suspend (
        CurrentSessionObservation,
        TimerecRuleId,
        TimerecRuleUpdate,
    ) -> DvrMutationResult<Unit> =
        { _, _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    private val deleteTimerecRule: suspend (CurrentSessionObservation, TimerecRuleId) -> DvrMutationResult<Unit> =
        { _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
    private val updateEntry: suspend (
        CurrentSessionObservation,
        DvrEntryId,
        DvrEntryUpdate,
    ) -> DvrMutationResult<Unit> =
        { _, _, _ -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
) {
    constructor(repository: DvrRepository) : this(
        scheduleEntry = repository::scheduleEntry,
        stopEntry = repository::stopEntry,
        cancelEntry = repository::cancelEntry,
        deleteEntry = repository::deleteEntry,
        updateWatchStateCommand = repository::updateWatchState,
        createAutorecRule = repository::createAutorecRule,
        updateAutorecRule = repository::updateAutorecRule,
        deleteAutorecRule = repository::deleteAutorecRule,
        createTimerecRule = repository::createTimerecRule,
        updateTimerecRule = repository::updateTimerecRule,
        deleteTimerecRule = repository::deleteTimerecRule,
        updateEntry = repository::updateEntry,
    )

    suspend fun updateWatchState(
        currentSession: CurrentSessionObservation,
        recordingId: DvrEntryId,
        watched: Boolean,
    ): DvrProgressResult = updateWatchStateCommand(currentSession, recordingId, watched)

    suspend fun createAutorecWithLeftOut(
        action: DvrMutationAction.CreateAutorec,
        excludedEventIds: Set<EventId>,
        observations: StateFlow<SessionObservation>,
        notices: NoticeCenter,
        context: NoticeContext,
    ): AutorecSaveOutcome {
        val result = if (action.recordingStateIsCurrent(observations.value)) {
            createAutorecRule(action.currentSession, action.request)
        } else DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
        val ruleId = when (result) {
            is DvrMutationResult.Confirmed -> result.value
            is DvrMutationResult.AcceptedButUnconfirmed -> result.value
            is DvrMutationResult.Failed -> {
                notices.postDvrFailure(action, result, context)
                return AutorecSaveOutcome.RuleFailed(result)
            }
        }
        return applyEpisodeChoices(action.currentSession, ruleId, excludedEventIds.associateWith { false },
            emptySet(), observations, notices, context, created = true)
    }

    suspend fun updateAutorecWithEpisodes(
        action: DvrMutationAction.UpdateAutorec,
        enabledChanges: Map<EventId, Boolean>,
        observations: StateFlow<SessionObservation>,
        notices: NoticeCenter,
        context: NoticeContext,
    ): AutorecSaveOutcome {
        val oldIds = if (action.update == null) emptySet() else observations.value.dvrSnapshotForDisplay
            ?.entries.orEmpty().filter { it.autorecRuleId == action.target.id }.map { it.id }.toSet()
        val result = if (action.recordingStateIsCurrent(observations.value)) executeRule(action)
            else DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
        if (result is DvrMutationResult.Failed) {
            notices.postDvrFailure(action, result, context)
            return AutorecSaveOutcome.RuleFailed(result)
        }
        return applyEpisodeChoices(action.currentSession, action.target.id, enabledChanges, oldIds,
            observations, notices, context, created = false)
    }

    private suspend fun applyEpisodeChoices(
        session: CurrentSessionObservation,
        ruleId: AutorecRuleId,
        changes: Map<EventId, Boolean>,
        replacedIds: Set<DvrEntryId>,
        observations: StateFlow<SessionObservation>,
        notices: NoticeCenter,
        context: NoticeContext,
        created: Boolean,
    ): AutorecSaveOutcome {
        val pending = changes.toMutableMap()
        val failed = mutableSetOf<EventId>()
        val acceptedIds = mutableSetOf<DvrEntryId>()
        if (pending.isNotEmpty()) withTimeoutOrNull(AutorecEpisodeWaitTimeout) {
            observations.first {
                applyAvailableEpisodeChoices(session, ruleId, pending, failed, acceptedIds, replacedIds, observations)
                pending.isEmpty()
            }
        }
        val unapplied = failed + pending.keys
        return if (unapplied.isEmpty()) AutorecSaveOutcome.Applied(ruleId) else {
            notices.post(Notice.AutomaticRecordingEpisodesNotApplied(created), context)
            AutorecSaveOutcome.PartiallyApplied(ruleId, unapplied)
        }
    }

    private suspend fun applyAvailableEpisodeChoices(
        session: CurrentSessionObservation,
        ruleId: AutorecRuleId,
        pending: MutableMap<EventId, Boolean>,
        failed: MutableSet<EventId>,
        acceptedIds: MutableSet<DvrEntryId>,
        replacedIds: Set<DvrEntryId>,
        observations: StateFlow<SessionObservation>,
    ) {
        for ((eventId, enabled) in pending.toMap()) {
            val current = observations.value
            if (current.currentSession !== session) {
                failed += pending.keys
                pending.clear()
                return
            }
            val entries = (current.dvrState as? DvrRepositoryState.Current)?.snapshot?.entries.orEmpty()
            val entry = entries.singleOrNull { it.autorecRuleId == ruleId && it.eventId == eventId &&
                it.state == DvrEntryState.SCHEDULED && it.id !in replacedIds } ?: continue
            if (entry.enabled == enabled) pending.remove(eventId)
            else if (entry.id !in acceptedIds) {
                when (updateEntry(session, entry.id, DvrEntryUpdate(enabled = enabled))) {
                    is DvrMutationResult.AcceptedButUnconfirmed -> acceptedIds += entry.id
                    is DvrMutationResult.Failed -> {
                        failed += eventId
                        pending.remove(eventId)
                    }
                    is DvrMutationResult.Confirmed -> pending.remove(eventId)
                }
            }
        }
    }

    suspend fun execute(action: DvrMutationAction?): DvrMutationResult<*> =
        when (action) {
            is DvrMutationAction.CreateAutorec,
            is DvrMutationAction.UpdateAutorec,
            is DvrMutationAction.DeleteAutorec,
            is DvrMutationAction.CreateTimerec,
            is DvrMutationAction.UpdateTimerec,
            is DvrMutationAction.DeleteTimerec -> executeRule(action)
            is DvrMutationAction.ScheduleByTime -> scheduleEntry(action.currentSession, action.request)
            is DvrMutationAction.SetEntryEnabled -> updateEntry(
                action.currentSession, action.target.id, DvrEntryUpdate(enabled = action.enabled),
            )
            is DvrMutationAction.SetEntryRemoval -> updateEntry(
                action.currentSession, action.target.id,
                DvrEntryUpdate(removal = action.removal, retention = action.retention),
            )
            is DvrMutationAction.CreateProgramme -> scheduleEntry(
                action.target.currentSession,
                DvrScheduleRequest(
                    schedule = DvrSchedule.Programme(action.target.eventId),
                    configId = action.configId,
                    title = action.target.title,
                ),
            )
            is DvrMutationAction.Stop -> stopEntry(
                action.currentSession,
                action.recordingId,
            )
            is DvrMutationAction.Cancel -> cancelEntry(
                action.currentSession,
                action.recordingId,
            )
            is DvrMutationAction.Delete -> deleteEntry(
                action.currentSession,
                action.recordingId,
            )
            null -> DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
        }

    private suspend fun executeRule(action: DvrMutationAction): DvrMutationResult<*> = when (action) {
        is DvrMutationAction.CreateAutorec -> createAutorecRule(action.currentSession, action.request)
        is DvrMutationAction.UpdateAutorec -> action.update?.let {
            updateAutorecRule(action.currentSession, action.target.id, it)
        } ?: DvrMutationResult.Confirmed(Unit)
        is DvrMutationAction.DeleteAutorec -> deleteAutorecRule(action.currentSession, action.target.id)
        is DvrMutationAction.CreateTimerec -> createTimerecRule(action.currentSession, action.request)
        is DvrMutationAction.UpdateTimerec -> updateTimerecRule(
            action.currentSession, action.target.id, action.update,
        )
        is DvrMutationAction.DeleteTimerec -> deleteTimerecRule(action.currentSession, action.target.id)
        else -> error("Not an automatic recording action")
    }
}

@OptIn(ExperimentalTvheadendApi::class)
internal fun NoticeCenter.postDvrFailure(action: DvrMutationAction, result: DvrMutationResult<*>, context: NoticeContext) {
    val feedback = result.toDvrMutationFeedback()
    if (!feedback.isFailure) return
    val kind = when (action) {
        is DvrMutationAction.CreateProgramme,
        is DvrMutationAction.ScheduleByTime,
        is DvrMutationAction.CreateAutorec,
        is DvrMutationAction.CreateTimerec -> DvrMutationKind.SCHEDULE
        is DvrMutationAction.UpdateAutorec,
        is DvrMutationAction.UpdateTimerec,
        is DvrMutationAction.SetEntryEnabled,
        is DvrMutationAction.SetEntryRemoval -> DvrMutationKind.UPDATE
        is DvrMutationAction.DeleteAutorec,
        is DvrMutationAction.DeleteTimerec -> DvrMutationKind.DELETE
        is DvrMutationAction.Stop -> DvrMutationKind.STOP
        is DvrMutationAction.Cancel -> DvrMutationKind.CANCEL
        is DvrMutationAction.Delete -> DvrMutationKind.DELETE
    }
    // The server's own text belongs only to a refusal; any other failure keeps the app's wording.
    val serverMessage = (result as? DvrMutationResult.Failed)?.serverMessage
        ?.takeIf { feedback == DvrMutationFeedback.REJECTED }
    val rule = action is DvrMutationAction.CreateAutorec || action is DvrMutationAction.UpdateAutorec ||
        action is DvrMutationAction.DeleteAutorec || action is DvrMutationAction.CreateTimerec ||
        action is DvrMutationAction.UpdateTimerec || action is DvrMutationAction.DeleteTimerec
    post(if (rule) Notice.DvrRuleActionFailed(kind, feedback, serverMessage)
        else Notice.DvrActionFailed(kind, feedback, serverMessage), context)
}

/**
 * Applies a folder [action] to [recordingIds] one request at a time. Each recording is checked
 * against the latest observation first, so one that changed or left since the menu opened is
 * skipped; once the opening session is gone, the rest count as not done. Returns the notice to
 * post when any request failed.
 */
@OptIn(ExperimentalTvheadendApi::class)
internal suspend fun DvrMutationActions.runFolderAction(
    action: BrowseContextAction,
    openingSession: CurrentSessionObservation,
    recordingIds: List<DvrEntryId>,
    latestObservation: () -> SessionObservation,
): Notice.DvrFolderActionFailed? {
    val watched = when (action) {
        BrowseContextAction.MARK_ALL_WATCHED -> true
        BrowseContextAction.MARK_ALL_UNWATCHED -> false
        BrowseContextAction.DELETE_WATCHED, BrowseContextAction.DELETE_ALL -> null
        else -> error("Not a folder action: $action")
    }
    var attempted = 0
    var failed = 0
    var failure: DvrMutationFeedback? = null
    for ((index, id) in recordingIds.withIndex()) {
        val current = latestObservation()
        val canModify = (current.sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
        // A lost connection or permission fails the rest; an entry someone else changed is just skipped.
        val stopped = when {
            current.currentSession !== openingSession -> DvrMutationFeedback.CONNECTION_UNAVAILABLE
            !canModify -> DvrMutationFeedback.PERMISSION_DENIED
            else -> null
        }
        if (stopped != null) {
            val rest = recordingIds.size - index
            attempted += rest
            failed += rest
            failure = failure ?: stopped
            break
        }
        val entry = current.dvrEntry(id) ?: continue
        if (!folderActionApplies(entry, action, current.recordingPlaybackAdmission(entry), canModify)) continue
        attempted++
        val feedback = if (watched != null) {
            when (val result = updateWatchState(openingSession, id, watched)) {
                DvrProgressResult.Accepted -> null
                is DvrProgressResult.Failed -> result.toDvrMutationFeedback()
            }
        } else execute(DvrMutationAction.Delete(openingSession, id)).toDvrMutationFeedback().takeIf { it.isFailure }
        if (feedback != null) {
            failed++
            failure = failure ?: feedback
        }
    }
    val kind = if (watched == null) DvrMutationKind.DELETE else DvrMutationKind.UPDATE
    return failure?.let { Notice.DvrFolderActionFailed(kind, failed, attempted, it) }
}
