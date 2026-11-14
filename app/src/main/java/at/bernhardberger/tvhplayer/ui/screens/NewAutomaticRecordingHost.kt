package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import java.time.ZoneId
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** Unattached new-recording flow. Its caller keeps the covered content unfocusable until close. */
@Composable
internal fun NewAutomaticRecordingHost(
    currentSession: CurrentSessionObservation,
    observations: StateFlow<SessionObservation>,
    loader: AutorecPreviewLoader,
    actions: DvrMutationActions,
    notices: NoticeCenter,
    now: () -> Instant,
    serverZone: ZoneId,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var kind by remember { mutableStateOf<AutomaticRecordingKind?>(null) }
    var closed by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    val navigation = remember {
        RecordingEditorNavigation("keyword", "automatic-recording-new", { !closed }, false)
    }
    val closeFlow = {
        if (!closed) {
            closed = true
            close()
        }
    }
    val leaveTask = {
        kind = null
        navigation.restoreFocus()
    }
    if (closed) return
    when (val selected = kind) {
        null -> RecordingEditorOverlay(navigation, modifier) {
            RecordingEditorSurface(navigation, closeFlow, guidance = { NewAutomaticRecordingGuidance() }) {
                NewAutomaticRecordingKinds(navigation) { kind = it }
            }
        }
        AutomaticRecordingKind.TIMER -> TimerRecordingEditorHost(
            currentSession, observations, actions, notices, now, leaveTask,
            startingRepeating = true, onSaved = closeFlow,
        )
        else -> AutomaticRecordingEditorHost(
            currentSession, observations, loader, actions, notices, now, serverZone, leaveTask,
            kind = selected, onSaved = closeFlow,
        )
    }
}

@Composable
private fun NewAutomaticRecordingGuidance() {
    GuidedStepGuidance(
        titleMaxLines = 3,
        title = stringResource(R.string.automatic_recording_new_title),
        body = stringResource(R.string.automatic_recording_new_choose),
        lead = {
            Text(stringResource(R.string.automatic_recording_new_overline),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha))
        },
    )
}

@Composable
private fun NewAutomaticRecordingKinds(
    navigation: RecordingEditorNavigation,
    onKind: (AutomaticRecordingKind) -> Unit,
) {
    val kinds = listOf(AutomaticRecordingKind.KEYWORD, AutomaticRecordingKind.TITLE, AutomaticRecordingKind.TIMER)
    for (kind in kinds) {
        val id = kind.name.lowercase()
        val (title, description, icon) = when (kind) {
            AutomaticRecordingKind.KEYWORD -> Triple(R.string.automatic_recording_new_keyword,
                R.string.automatic_recording_new_keyword_help, R.drawable.ic_search)
            AutomaticRecordingKind.TITLE -> Triple(R.string.automatic_recording_new_title_kind,
                R.string.automatic_recording_new_title_help, R.drawable.ic_title)
            else -> Triple(R.string.automatic_recording_new_timer,
                R.string.automatic_recording_new_timer_help, R.drawable.ic_schedule)
        }
        GuidedStepAction(
            title = stringResource(title), subtitleMaxLines = 2, subtitle = stringResource(description),
            leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
            trailingIcon = R.drawable.ic_keyboard_arrow_right,
            modifier = editorRowModifier(navigation, id).focusProperties {
                if (kind == AutomaticRecordingKind.KEYWORD) up = FocusRequester.Cancel
                if (kind == AutomaticRecordingKind.TIMER) down = FocusRequester.Cancel
            },
            onClick = {
                navigation.focusedRow = id
                onKind(kind)
            },
        )
    }
}
