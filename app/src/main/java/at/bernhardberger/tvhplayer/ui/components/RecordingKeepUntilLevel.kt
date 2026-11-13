package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.RecordingKeepUntilChoice
import at.bernhardberger.tvhplayer.core.RecordingKeepUntilOption
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate

/** A presentation-only level; its caller owns the request and the return destination. */
@Composable
internal fun RecordingKeepUntilLevel(
    title: String,
    channel: String,
    episode: String,
    time: String,
    options: List<RecordingKeepUntilOption>,
    nowSec: Long,
    onChoose: (RecordingKeepUntilOption) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    summary: String = "",
    recordingState: DvrEntryState? = null,
    recordingRule: RecordingRule? = null,
    recordingEnabled: Boolean? = null,
    artwork: @Composable () -> Unit,
) {
    TwoColumnDialog(
        stringResource(R.string.recording_keep_until_heading), "", onBack,
        modifier.testTag("recording-keep-until"), itemCardLayout = true, dismissLevel = true,
        backEnabled = !busy,
        onPreviewKeyEvent = { event ->
            event.key == Key.Menu || busy && event.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
        },
        leading = { BrowseContextRecordingCard(title, channel, episode, time, summary,
            recordingState, recordingRule, recordingEnabled, artwork) },
    ) {
        RecordingKeepUntilChoices(options, nowSec, busy, onChoose)
    }
}

@Composable
internal fun RecordingKeepUntilChoices(
    options: List<RecordingKeepUntilOption>,
    nowSec: Long,
    busy: Boolean,
    onChoose: (RecordingKeepUntilOption) -> Unit,
) {
    val requesters = remember { RecordingKeepUntilChoice.entries.associateWith { FocusRequester() } }
    val initial = options.firstOrNull { it.selected }?.choice ?: options.firstOrNull()?.choice
    var chosen by remember { mutableStateOf<RecordingKeepUntilChoice?>(null) }
    var focused by remember { mutableStateOf<RecordingKeepUntilChoice?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(options) {
        if (options.none { it.choice == focused }) {
            withFrameNanos { }
            initial?.let { requesters.getValue(it).requestFocus() }
        }
    }
    Text(stringResource(R.string.recording_keep_until_heading), Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            key(option.choice) {
                TwoColumnDialogButton(
                    stringResource(option.choice.label()),
                    { if (!busy && option.removal != null) { chosen = option.choice; onChoose(option) } },
                    Modifier.testTag("keep-until-option-${option.choice.name.lowercase()}")
                        .focusRequester(requesters.getValue(option.choice))
                        .onFocusChanged { if (it.isFocused) focused = option.choice }
                        .focusProperties {
                            up = options.getOrNull(index - 1)?.choice?.let(requesters::get) ?: FocusRequester.Cancel
                            down = options.getOrNull(index + 1)?.choice?.let(requesters::get)
                                ?: FocusRequester.Cancel
                            left = FocusRequester.Cancel; right = FocusRequester.Cancel
                        }.semantics {
                            role = Role.RadioButton; selected = option.selected
                            if (busy || option.removal == null) disabled()
                        },
                    subtitle = option.expirySec?.let { formatRecordingDate(it, dateOnly = true, locale, nowSec) },
                    trailingContent = { KeepUntilSelectionMark(option.selected,
                        busy && option.choice == (chosen ?: initial)) },
                )
            }
        }
    }
}

@Composable
private fun KeepUntilSelectionMark(selected: Boolean, busy: Boolean) {
    if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
    else RadioButton(
        selected, onClick = null, modifier = Modifier.clearAndSetSemantics {},
        colors = RadioButtonDefaults.colors(selectedColor = LocalContentColor.current,
            unselectedColor = LocalContentColor.current),
    )
}

@Composable
internal fun BrowseContextRecordingCard(
    title: String, channel: String, episode: String, time: String, summary: String,
    state: DvrEntryState?, rule: RecordingRule?, enabled: Boolean?, artwork: @Composable () -> Unit,
) {
    val subtitle = listOf(channel, time).filter(String::isNotBlank).joinToString(" • ")
    val description = listOf(episode, summary).filter(String::isNotBlank).joinToString("\n")
    Column(Modifier.fillMaxWidth().testTag("browse-context-item")) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { artwork() }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            RecordingBadge(state, Modifier.testTag("browse-context-recording"), rule = rule, skipped = enabled == false)
            if (rule != null && state in listOf(
                    DvrEntryState.SCHEDULED, DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR)) {
                Text(stringResource(rule.label), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun RecordingKeepUntilChoice.label(): Int = when (this) {
    RecordingKeepUntilChoice.CURRENT -> R.string.recording_keep_until_current
    RecordingKeepUntilChoice.ONE_WEEK -> R.string.recording_keep_until_week
    RecordingKeepUntilChoice.ONE_MONTH -> R.string.recording_keep_until_month
    RecordingKeepUntilChoice.THREE_MONTHS -> R.string.recording_keep_until_three_months
    RecordingKeepUntilChoice.SIX_MONTHS -> R.string.recording_keep_until_six_months
    RecordingKeepUntilChoice.ONE_YEAR -> R.string.recording_keep_until_year
    RecordingKeepUntilChoice.UNTIL_SPACE_NEEDED -> R.string.recording_keep_until_space_needed
    RecordingKeepUntilChoice.FOREVER -> R.string.recording_keep_until_forever
}
