package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import at.bernhardberger.tvheadend.sdk.core.AutorecRule
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfigurationsState
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.newAutorecDraft
import at.bernhardberger.tvhplayer.core.toCreateRequest
import at.bernhardberger.tvhplayer.core.toDraft
import at.bernhardberger.tvhplayer.core.toUpdateRequest
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import java.time.ZoneId
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * Same full-screen overlay as connection setup. The owning screen keeps its entry underneath,
 * disables that entry's focus/Back while covered, and removes this host in [onClose].
 * Exactly one of [kind] and [rule] is supplied; a new series requires a linked [programme].
 */
@Composable
internal fun AutomaticRecordingEditorHost(
    currentSession: CurrentSessionObservation,
    observations: StateFlow<SessionObservation>,
    loader: AutorecPreviewLoader,
    actions: DvrMutationActions,
    notices: NoticeCenter,
    now: () -> Instant,
    serverZone: ZoneId,
    onClose: () -> Unit,
    kind: AutomaticRecordingKind? = null,
    programme: EpgEvent? = null,
    rule: AutorecRule? = null,
) {
    require((kind == null) != (rule == null))
    val observation by observations.collectAsState()
    val configurations = (observation.dvrConfigurationsState as? DvrConfigurationsState.Current)
        ?.configurations.orEmpty()
    val scope = rememberCoroutineScope()
    val close by rememberUpdatedState(onClose)
    val state = remember(kind, programme?.id, rule?.id) {
        val initial = rule?.toDraft() ?: newAutorecDraft(requireNotNull(kind), configurations, programme = programme)
        val context = notices.context()
        AutomaticRecordingEditorState(
            initial, scope,
            loadPreview = { draft, baseline, at, zone ->
                loader.load(draft, currentSession, observations.value, rule?.id, at, zone, baseline)
            },
            saveDraft = { draft, enabled ->
                if (rule == null) actions.createAutorecWithLeftOut(
                    DvrMutationAction.CreateAutorec(currentSession, draft.toCreateRequest()),
                    draft.excludedEventIds, observations, notices, context,
                ) else actions.updateAutorecWithEpisodes(
                    DvrMutationAction.UpdateAutorec(currentSession, rule, draft.toUpdateRequest(initial)),
                    enabled, observations, notices, context,
                )
            },
            now, serverZone, editing = rule != null, prefilled = programme != null, onClose = { close() },
            deleteRule = {
                val action = DvrMutationAction.DeleteAutorec(currentSession, requireNotNull(rule))
                val result = if (action.recordingStateIsCurrent(observations.value)) actions.execute(action)
                    else DvrMutationResult.Failed(TvheadendFailure.NOT_READY, false)
                notices.postDvrFailure(action, result, context)
                result
            },
        )
    }
    RecordingEditorOverlay(state) {
        AutomaticRecordingEditor(state, channels = observation.channelCatalogForDisplay?.channels.orEmpty(),
            configurations = configurations, events = observation.epgSnapshotForDisplay?.events.orEmpty(),
            scheduledEpisodeCount = observation.dvrSnapshotForDisplay?.entries.orEmpty().count {
                rule != null && it.autorecRuleId == rule.id && it.state == DvrEntryState.SCHEDULED
            })
    }
}
