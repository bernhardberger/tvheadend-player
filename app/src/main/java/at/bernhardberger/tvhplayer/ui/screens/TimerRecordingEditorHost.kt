package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfigurationsState
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TimerecRule
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.core.newTimerDraft
import at.bernhardberger.tvhplayer.core.toCreateRequest
import at.bernhardberger.tvhplayer.core.toDraft
import at.bernhardberger.tvhplayer.core.toScheduleRequest
import at.bernhardberger.tvhplayer.core.toUpdateRequest
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import java.time.ZoneId
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** Caller retains its entry underneath, disables its focus/Back, then removes this host on close. */
@Composable
internal fun TimerRecordingEditorHost(
    currentSession: CurrentSessionObservation,
    observations: StateFlow<SessionObservation>,
    actions: DvrMutationActions,
    notices: NoticeCenter,
    now: () -> Instant,
    onClose: () -> Unit,
    rule: TimerecRule? = null,
    startingRepeating: Boolean = false,
) {
    val zone = remember { ZoneId.systemDefault() }
    val observation by observations.collectAsState()
    val configurations = (observation.dvrConfigurationsState as? DvrConfigurationsState.Current)
        ?.configurations.orEmpty()
    val scope = rememberCoroutineScope()
    val close by rememberUpdatedState(onClose)
    val state = remember(rule?.id, startingRepeating) {
        val date = java.time.Instant.ofEpochSecond(now().epochSeconds).atZone(zone).toLocalDate()
        val initial = rule?.toDraft(date) ?: newTimerDraft(
            now(), zone, configurations, startingRepeating = startingRepeating,
        )
        val context = notices.context()
        suspend fun execute(action: DvrMutationAction): DvrMutationResult<*> {
            val result = if (action.recordingStateIsCurrent(observations.value)) actions.execute(action)
                else DvrMutationResult.Failed(TvheadendFailure.NOT_READY, false)
            notices.postDvrFailure(action, result, context)
            return result
        }
        TimerRecordingEditorState(
            initial, scope,
            saveDraft = { draft, submittedAt ->
                val channelName = observations.value.channelCatalogForDisplay?.channels
                    ?.firstOrNull { it.id == draft.channelId }?.name.orEmpty()
                execute(when {
                    rule != null -> DvrMutationAction.UpdateTimerec(currentSession, rule,
                        requireNotNull(draft.toUpdateRequest(initial, channelName)))
                    draft.repeating -> DvrMutationAction.CreateTimerec(
                        currentSession, draft.toCreateRequest(channelName),
                    )
                    else -> DvrMutationAction.ScheduleByTime(
                        currentSession, draft.toScheduleRequest(channelName, zone, submittedAt),
                    )
                })
            },
            now, zone, onClose = { close() },
            deleteTimer = { execute(DvrMutationAction.DeleteTimerec(currentSession, requireNotNull(rule))) },
        )
    }
    RecordingEditorOverlay(state) {
        TimerRecordingEditor(state, observation.channelCatalogForDisplay?.channels.orEmpty(), configurations,
            scheduledEntries = observation.dvrSnapshotForDisplay?.entries.orEmpty().filter {
                rule != null && it.timerecRuleId == rule.id && it.state == DvrEntryState.SCHEDULED
            }.sortedBy { it.start })
    }
}
