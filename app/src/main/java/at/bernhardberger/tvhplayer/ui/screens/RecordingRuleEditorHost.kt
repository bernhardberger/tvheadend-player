package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import java.time.ZoneId
import kotlin.time.Instant

/** Dispatches the captured SDK rule to its existing editor; callers retain their covered surface. */
@Composable
internal fun RecordingRuleEditorHost(
    entry: DvrEntry,
    currentSession: CurrentSessionObservation,
    openingObservation: SessionObservation,
    session: TvheadendSession,
    actions: DvrMutationActions,
    notices: NoticeCenter,
    now: () -> Instant,
    onClose: () -> Unit,
) {
    val snapshot = remember(entry.id, currentSession) {
        (openingObservation.dvrState as? DvrRepositoryState.Current)?.snapshot
    }
    val automatic = remember(entry.id, currentSession) {
        snapshot?.autorecRules?.firstOrNull { it.id == entry.autorecRuleId }
    }
    val timer = remember(entry.id, currentSession) {
        snapshot?.timerecRules?.firstOrNull { it.id == entry.timerecRuleId }
    }
    when {
        automatic != null -> {
            val loader = remember(session) { AutorecPreviewLoader(session.epgRepository) }
            val zone = remember { ZoneId.systemDefault() }
            AutomaticRecordingEditorHost(currentSession, session.observation, loader, actions, notices, now,
                zone, onClose, rule = automatic)
        }
        timer != null -> TimerRecordingEditorHost(currentSession, session.observation, actions, notices, now,
            onClose, rule = timer)
        else -> LaunchedEffect(entry.id) { onClose() }
    }
}
