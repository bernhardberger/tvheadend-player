package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingListItem
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.ui.components.ConfirmationPanel
import at.bernhardberger.tvhplayer.ui.components.ConfirmationStyles
import at.bernhardberger.tvhplayer.ui.components.TwoColumnDialog
import at.bernhardberger.tvhplayer.ui.components.TwoColumnDialogButton
import at.bernhardberger.tvhplayer.ui.screens.AutomaticRecordingEditorHost
import at.bernhardberger.tvhplayer.ui.screens.AutorecPreviewLoader
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import at.bernhardberger.tvhplayer.ui.screens.NewAutomaticRecordingHost
import at.bernhardberger.tvhplayer.ui.screens.RecordingRuleDeleteDialog
import at.bernhardberger.tvhplayer.ui.screens.TimerRecordingEditorHost
import at.bernhardberger.tvhplayer.ui.screens.postDvrFailure
import at.bernhardberger.tvhplayer.ui.screens.recordingStateIsCurrent
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.koinInject
import java.time.ZoneId
import kotlin.time.Instant

internal enum class RuleSurface { NEW, EDIT, MENU, DELETE }
internal data class OpenRule(
    val surface: RuleSurface,
    val session: CurrentSessionObservation,
    val item: AutomaticRecordingListItem? = null,
)

/** The Schedule consumer keeps its row mounted while an existing editor or dialog owns focus. */
@Composable
internal fun ScheduleRuleActions(
    opened: OpenRule?,
    onChange: (OpenRule?) -> Unit,
    observation: () -> SessionObservation,
    isCurrent: Boolean,
    channels: List<Channel>,
    actions: DvrMutationActions,
    notices: NoticeCenter,
    now: () -> Instant,
) {
    val inFlight = remember { mutableSetOf<String>() }
    val scope = rememberCoroutineScope()
    val close = { onChange(null) }
    val target = opened ?: return
    val item = target.item
    when (target.surface) {
        RuleSurface.NEW, RuleSurface.EDIT -> ScheduleRuleEditor(target, actions, notices, now, close)
        RuleSurface.MENU -> if (item != null) ScheduleRuleMenu(item, channels, close,
            onEdit = { onChange(target.copy(surface = RuleSurface.EDIT)) },
            onDelete = { onChange(target.copy(surface = RuleSurface.DELETE)) },
            onToggle = {
                scope.launchRuleMutation(inFlight, item) {
                    val context = notices.context()
                    val latest = observation()
                    val current = target.currentIn(latest, isCurrent)
                    if (current != null) {
                        actions.setRuleEnabled(current, !item.enabled, target.session, latest, notices, context)
                    }
                }
                close()
            })
        RuleSurface.DELETE -> if (item != null) RecordingRuleDeleteDialog(
            item.kind, item.subject(channels), item.upcoming, onDismiss = close,
            onConfirm = {
                scope.launchRuleMutation(inFlight, item) {
                    deleteScheduleRule(target, observation(), isCurrent, actions, notices)
                }
                close()
            },
        )
    }
}

@Composable
private fun ScheduleRuleEditor(
    target: OpenRule,
    actions: DvrMutationActions,
    notices: NoticeCenter,
    now: () -> Instant,
    close: () -> Unit,
) {
    val session: TvheadendSession = koinInject()
    val loader = remember(session) { AutorecPreviewLoader(session.epgRepository) }
    val zone = remember { ZoneId.systemDefault() }
    when (val item = target.item) {
        null -> NewAutomaticRecordingHost(target.session, session.observation, loader, actions,
            notices, now, zone, close)
        is AutomaticRecordingListItem.Automatic -> AutomaticRecordingEditorHost(
            target.session, session.observation, loader, actions, notices, now, zone, close, rule = item.rule)
        is AutomaticRecordingListItem.Timer -> TimerRecordingEditorHost(
            target.session, session.observation, actions, notices, now, close, rule = item.rule)
    }
}

private fun CoroutineScope.launchRuleMutation(
    inFlight: MutableSet<String>,
    item: AutomaticRecordingListItem,
    mutation: suspend () -> Unit,
) {
    if (inFlight.add(item.rowId())) launch {
        try {
            mutation()
        } finally {
            inFlight.remove(item.rowId())
        }
    }
}

private fun OpenRule.currentIn(observation: SessionObservation, isCurrent: Boolean): AutomaticRecordingListItem? =
    if (isCurrent && observation.currentSession == session) item?.currentIn(observation) else null

private suspend fun deleteScheduleRule(
    target: OpenRule,
    observation: SessionObservation,
    isCurrent: Boolean,
    actions: DvrMutationActions,
    notices: NoticeCenter,
) {
    val action = when (val current = target.currentIn(observation, isCurrent)) {
        is AutomaticRecordingListItem.Automatic -> DvrMutationAction.DeleteAutorec(target.session, current.rule)
        is AutomaticRecordingListItem.Timer -> DvrMutationAction.DeleteTimerec(target.session, current.rule)
        null -> null
    }
    if (action != null) {
        val context = notices.context()
        val result = if (action.recordingStateIsCurrent(observation)) actions.execute(action)
            else DvrMutationResult.Failed(TvheadendFailure.NOT_READY, false)
        notices.postDvrFailure(action, result, context)
    }
}

internal fun AutomaticRecordingListItem.currentIn(observation: SessionObservation): AutomaticRecordingListItem? {
    val snapshot = (observation.dvrState as? DvrRepositoryState.Current)?.snapshot ?: return null
    return when (this) {
        is AutomaticRecordingListItem.Automatic -> snapshot.autorecRules.firstOrNull { it.id == rule.id }?.let {
            copy(rule = it)
        }
        is AutomaticRecordingListItem.Timer -> snapshot.timerecRules.firstOrNull { it.id == rule.id }?.let {
            copy(rule = it)
        }
    }
}

@Composable
private fun ScheduleRuleMenu(
    item: AutomaticRecordingListItem,
    channels: List<Channel>,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos {}; first.requestFocus() }
    val type = MaterialTheme.typography
    val editLabel = when (item.kind) {
        AutomaticRecordingKind.SERIES -> R.string.context_edit_series
        AutomaticRecordingKind.TITLE -> R.string.context_edit_title
        AutomaticRecordingKind.KEYWORD -> R.string.context_edit_keyword
        AutomaticRecordingKind.TIMER -> R.string.context_edit_timer
    }
    TwoColumnDialog(
        title = item.headline.ifBlank { item.kindLabel() }, message = "", onDismiss = onClose,
        modifier = Modifier.testTag("automatic-recording-menu"),
        leading = {
            ConfirmationPanel(item.subject(channels),
                ConfirmationStyles(type.headlineMedium, type.bodyLarge, type.titleLarge,
                    type.labelMedium, type.bodyLarge))
        },
        itemCardLayout = true,
    ) {
        TwoColumnDialogButton(stringResource(editLabel), onEdit,
            Modifier.focusRequester(first).testTag("automatic-recording-menu-edit"),
            icon = { Icon(painterResource(R.drawable.ic_edit), null) })
        TwoColumnDialogButton(stringResource(if (item.enabled) R.string.schedule_automatic_turn_off
            else R.string.schedule_automatic_turn_on), onToggle,
            Modifier.testTag("automatic-recording-menu-toggle"),
            icon = { Icon(painterResource(if (item.enabled) R.drawable.ic_power_off else R.drawable.ic_power), null) })
        TwoColumnDialogButton(stringResource(R.string.context_delete), onDelete,
            Modifier.testTag("automatic-recording-menu-delete"),
            icon = { Icon(painterResource(R.drawable.ic_delete_outlined), null) })
    }
}
