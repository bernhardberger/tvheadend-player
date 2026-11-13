package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrRemoval
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutorecDraft
import at.bernhardberger.tvhplayer.core.RecordingStartWindow
import at.bernhardberger.tvhplayer.ui.common.formatClock
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun automaticRecordingChannelLabel(draft: AutorecDraft, channels: List<Channel>): String =
    when (val selected = draft.channel) {
        RecordingRuleChannel.AllChannels -> stringResource(R.string.all_channels)
        is RecordingRuleChannel.SpecificChannel -> channels.firstOrNull { it.id == selected.channelId }?.name
            ?: stringResource(R.string.autorec_editor_channel_unavailable)
    }

@Composable
internal fun automaticRecordingDaysLabel(days: Set<DayOfWeek>): String =
    if (days.isEmpty() || days.size == DayOfWeek.entries.size) stringResource(R.string.autorec_editor_any_day)
    else days.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

@Composable
internal fun automaticRecordingWindowLabel(window: RecordingStartWindow): String = when (window) {
    RecordingStartWindow.AnyTime -> stringResource(R.string.autorec_editor_any_time)
    is RecordingStartWindow.After -> stringResource(R.string.autorec_editor_after_time, formatClock(window.time))
    is RecordingStartWindow.Before -> stringResource(R.string.autorec_editor_before_time, formatClock(window.time))
    is RecordingStartWindow.Stored -> stringResource(
        R.string.autorec_editor_current, "${formatClock(window.start.time)}–${formatClock(window.end.time)}",
    )
}

@Composable
internal fun automaticRecordingChannelTimeLabel(draft: AutorecDraft, channels: List<Channel>): String =
    listOf(automaticRecordingChannelLabel(draft, channels), automaticRecordingDaysLabel(draft.days),
        automaticRecordingWindowLabel(draft.startWindow)).joinToString(" · ")

@Composable
internal fun automaticRecordingKeepLabel(value: DvrRemoval?): String = when (value) {
    null, DvrRemoval.ServerDefault -> stringResource(R.string.profile_server_default)
    is DvrRemoval.Days -> stringResource(R.string.autorec_editor_days_after_recording, value.days)
    DvrRemoval.UntilSpaceNeeded -> stringResource(R.string.autorec_editor_until_space)
    DvrRemoval.Forever -> stringResource(R.string.autorec_editor_forever)
}

@Composable
internal fun automaticRecordingKeepChoiceLabel(value: DvrRemoval?): String = when (value) {
    is DvrRemoval.Days -> stringResource(when (value.days) {
        7 -> R.string.autorec_editor_keep_week
        30 -> R.string.autorec_editor_keep_month
        90 -> R.string.autorec_editor_keep_three_months
        180 -> R.string.autorec_editor_keep_six_months
        365 -> R.string.autorec_editor_keep_year
        else -> R.string.autorec_editor_keep_days
    }, value.days)
    else -> automaticRecordingKeepLabel(value)
}
