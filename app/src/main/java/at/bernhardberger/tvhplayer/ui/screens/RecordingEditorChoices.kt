package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Checkbox
import androidx.tv.material3.RadioButton
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrPriority
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.orderBrowseChannels
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Composable
internal fun RecordingEditorRadio(
    navigation: RecordingEditorNavigation,
    id: String,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
) {
    GuidedStepAction(
        title, { if (navigation.canNavigate()) onClick() }, modifier.then(editorRowModifier(navigation, id)).semantics {
            role = Role.RadioButton; this.selected = selected
        },
        leadingIcon = leadingIcon,
        subtitle = subtitle,
        trailingContent = { RadioButton(selected, onClick = null, modifier = Modifier.clearAndSetSemantics {}) },
    )
}

@Composable
internal fun RecordingEditorChannels(
    navigation: RecordingEditorNavigation,
    channels: List<Channel>,
    current: ChannelId?,
    onChoice: (ChannelId) -> Unit,
    onCurrent: () -> Unit,
    onAll: (() -> Unit)? = null,
) {
    if (onAll != null) RecordingEditorRadio(
        navigation, "channel-all", stringResource(R.string.all_channels), current == null, onAll,
    )
    if (current != null && channels.none { it.id == current }) RecordingEditorRadio(
        navigation, "channel-${current.value}",
        stringResource(R.string.autorec_editor_current, stringResource(R.string.autorec_editor_channel_unavailable)),
        true, onCurrent,
    )
    for (channel in orderBrowseChannels(channels)) RecordingEditorRadio(
        navigation, "channel-${channel.id.value}", channel.name.orEmpty(), current == channel.id,
        { onChoice(channel.id) },
        leadingIcon = { PiconBox(channel.icon, Modifier.size(40.dp, 28.dp)) },
    )
}

@Composable
internal fun RecordingEditorKeep(
    navigation: RecordingEditorNavigation,
    current: DvrRemoval?,
    baseline: DvrRemoval?,
    onChoice: (DvrRemoval?) -> Unit,
    onCurrent: () -> Unit,
) {
    val default = baseline.takeIf { it == DvrRemoval.ServerDefault }
    val values = listOf(default, DvrRemoval.Days(7), DvrRemoval.Days(30), DvrRemoval.Days(90), DvrRemoval.Days(180),
        DvrRemoval.Days(365), DvrRemoval.UntilSpaceNeeded, DvrRemoval.Forever)
    if (current != null && current !in values) RecordingEditorRadio(
        navigation, "keep-$current",
        stringResource(R.string.autorec_editor_current, automaticRecordingKeepLabel(current)), true, onCurrent,
    )
    for (value in values) RecordingEditorRadio(
        navigation, "keep-$value", automaticRecordingKeepChoiceLabel(value), current == value, { onChoice(value) },
    )
}

@Composable
internal fun RecordingEditorPriorities(
    navigation: RecordingEditorNavigation,
    current: DvrPriority?,
    baseline: DvrPriority?,
    onChoice: (DvrPriority?) -> Unit,
    onCurrent: () -> Unit,
) {
    val default = baseline.takeIf { it == DvrPriority.DEFAULT }
    val values = listOf(default, DvrPriority.IMPORTANT, DvrPriority.HIGH, DvrPriority.NORMAL,
        DvrPriority.LOW, DvrPriority.UNIMPORTANT)
    if (current !in values) RecordingEditorRadio(
        navigation, "choice-$current", automaticRecordingPriorityLabel(current), true, onCurrent,
    )
    for (value in values) RecordingEditorRadio(
        navigation, "choice-$value", automaticRecordingPriorityLabel(value), current == value, { onChoice(value) },
    )
}

@Composable
internal fun RecordingEditorPadding(
    navigation: RecordingEditorNavigation,
    current: Duration?,
    baseline: Duration?,
    onChoice: (Duration?) -> Unit,
    onCurrent: () -> Unit,
) {
    val values = listOf(baseline.takeIf { it == Duration.ZERO }) + listOf(1, 2, 5, 10, 15, 30, 60).map { it.minutes }
    if (current !in values) RecordingEditorRadio(
        navigation, "choice-$current",
        stringResource(R.string.autorec_editor_current, automaticRecordingPaddingLabel(current)), true, onCurrent,
    )
    for (value in values) RecordingEditorRadio(
        navigation, "choice-$value", automaticRecordingPaddingLabel(value), current == value, { onChoice(value) },
    )
}

@Composable
internal fun RecordingEditorProfiles(
    navigation: RecordingEditorNavigation,
    configurations: List<DvrConfiguration>,
    current: DvrConfigId?,
    onChoice: (DvrConfigId) -> Unit,
    onCurrent: () -> Unit,
) {
    if (current == null || configurations.none { it.id == current }) RecordingEditorRadio(
        navigation, "choice-${current?.value}", stringResource(if (current == null)
            R.string.profile_server_default else R.string.autorec_editor_current_setting),
        true, onCurrent,
    )
    for (configuration in configurations) RecordingEditorRadio(
        navigation, "choice-${configuration.id.value}", configuration.name, current == configuration.id,
        { onChoice(configuration.id) },
    )
}

@Composable
internal fun RecordingEditorHour(
    navigation: RecordingEditorNavigation,
    hour: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GuidedStepAction(
        formatClock(LocalTime.of(hour, 0)), onClick,
        modifier.then(editorRowModifier(navigation, "hour-$hour")), trailingIcon = R.drawable.ic_keyboard_arrow_right,
        enabled = enabled && navigation.canNavigate(),
    )
}

@Composable
internal fun RecordingEditorDay(
    navigation: RecordingEditorNavigation,
    day: DayOfWeek,
    checked: Boolean,
    onClick: () -> Unit,
) {
    GuidedStepAction(
        day.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0]),
        { if (navigation.canNavigate()) onClick() },
        editorRowModifier(navigation, "day-${day.value}").semantics {
            role = Role.Checkbox; toggleableState = ToggleableState(checked)
        },
        trailingContent = {
            Checkbox(checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
        },
    )
}
