package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.AutomaticRecordingListItem
import at.bernhardberger.tvhplayer.core.toDraft
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ConfirmationSubject
import at.bernhardberger.tvhplayer.ui.components.LocalTabOwner
import at.bernhardberger.tvhplayer.ui.components.ProgrammeEyebrow
import at.bernhardberger.tvhplayer.ui.components.ProgrammeHeadline
import at.bernhardberger.tvhplayer.ui.components.ProgrammeStatusLine
import at.bernhardberger.tvhplayer.ui.components.StatusBadge
import at.bernhardberger.tvhplayer.ui.components.StatusBadgeTone
import at.bernhardberger.tvhplayer.ui.components.rememberBrowseContextMenuTrigger
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.screens.automaticRecordingChannelLabel
import at.bernhardberger.tvhplayer.ui.screens.automaticRecordingDaysLabel
import at.bernhardberger.tvhplayer.ui.screens.automaticRecordingKeepLabel
import at.bernhardberger.tvhplayer.ui.screens.automaticRecordingWindowLabel
import at.bernhardberger.tvhplayer.ui.screens.formatTimerRecordingDays
import at.bernhardberger.tvhplayer.ui.screens.RecordingEditorHeading
import at.bernhardberger.tvhplayer.ui.screens.RecordingEditorPreviewPair
import at.bernhardberger.tvhplayer.ui.screens.RecordingEditorSecondary
import java.time.LocalDate

internal fun AutomaticRecordingListItem.rowId() = scheduleRuleRowId(id, kind)

internal fun scheduleRuleRowId(id: String, kind: AutomaticRecordingKind) =
    "automatic-recording-${if (kind == AutomaticRecordingKind.TIMER) "timer-" else ""}$id"

internal fun AutomaticRecordingListItem.icon() =
    when (kind) {
        AutomaticRecordingKind.TITLE -> R.drawable.ic_title
        AutomaticRecordingKind.TIMER -> R.drawable.ic_schedule
        else -> R.drawable.ic_fiber_smart_record
    }

@Composable
internal fun AutomaticRecordingListItem.kindLabel(): String = stringResource(when (kind) {
    AutomaticRecordingKind.SERIES -> R.string.automatic_recording_kind_series
    AutomaticRecordingKind.TITLE -> R.string.automatic_recording_kind_title
    AutomaticRecordingKind.KEYWORD -> R.string.automatic_recording_kind_keyword
    AutomaticRecordingKind.TIMER -> R.string.automatic_recording_new_timer
})

@Composable
internal fun AutomaticRecordingListItem.subject(channels: List<Channel>): ConfirmationSubject.Rule {
    val channel = if (this is AutomaticRecordingListItem.Automatic && channelId == null) {
        automaticRecordingChannelLabel(rule.toDraft(), channels)
    } else {
        channels.firstOrNull { it.id == channelId }?.name
    }
    return ConfirmationSubject.Rule(icon(), headline.ifBlank { kindLabel() },
        listOfNotNull(kindLabel(), channel).joinToString(" • "))
}

@Composable
internal fun ScheduleRuleRow(
    item: AutomaticRecordingListItem,
    channels: List<Channel>,
    channelMap: Map<ChannelId, Channel>,
    modifier: Modifier,
    onOpen: () -> Unit,
    onMenu: (() -> Unit)?,
) {
    val owner = LocalTabOwner.current
    val menu = rememberBrowseContextMenuTrigger(onMenu, enabled = { owner?.isCurrent != false })
    var focused by remember { mutableStateOf(false) }
    ListItem(
        selected = false, onClick = { if (owner?.isCurrent != false) onOpen() }, onLongClick = menu.onLongClick,
        modifier = modifier.tabFocus().fillMaxWidth().testTag(item.rowId()).then(menu.modifier)
            .onFocusChanged { focused = it.isFocused },
        overlineContent = { Text(item.subject(channels).detail,
            maxLines = 1, overflow = TextOverflow.Ellipsis) },
        headlineContent = { Text(item.headline.ifBlank { item.kindLabel() },
            color = LocalContentColor.current.let {
                if (item.enabled || focused) it else it.copy(alpha = TvTextSecondaryAlpha)
            },
            maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            val logo = item.channelId?.let(channelMap::get)?.icon
            val mark = Modifier.size(32.dp).alpha(if (item.enabled || focused) 1f else TvTextSecondaryAlpha)
            if (logo == null) Icon(painterResource(item.icon()), null, mark)
            else PiconBox(logo, mark,
                ground = if (focused) MaterialTheme.colorScheme.inverseSurface
                    else MaterialTheme.colorScheme.background,
                prefetchGround = if (focused) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.inverseSurface)
        },
        trailingContent = {
            if (!item.enabled) StatusBadge(stringResource(R.string.schedule_automatic_off), StatusBadgeTone.NEUTRAL)
            else Text(pluralStringResource(R.plurals.schedule_automatic_upcoming, item.upcoming, item.upcoming),
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        },
    )
}

@Composable
internal fun ScheduleRulePane(item: AutomaticRecordingListItem, channels: List<Channel>, modifier: Modifier) {
    val subject = item.subject(channels)
    val channel = channels.firstOrNull { it.id == item.channelId }
    val whenLabel: String
    val keepLabel: String
    when (item) {
        is AutomaticRecordingListItem.Automatic -> {
            val draft = item.rule.toDraft()
            whenLabel = automaticRecordingDaysLabel(draft.days) +
                " • ${automaticRecordingWindowLabel(draft.startWindow)}"
            keepLabel = automaticRecordingKeepLabel(draft.removal)
        }
        is AutomaticRecordingListItem.Timer -> {
            val draft = item.rule.toDraft(LocalDate.MIN)
            whenLabel = formatTimerRecordingDays(LocalContext.current, draft.days) +
                " • ${formatClock(draft.start)} – ${formatClock(draft.end)}"
            keepLabel = automaticRecordingKeepLabel(draft.removal)
        }
    }
    Column(modifier.padding(top = 4.dp).testTag("automatic-recording-pane")) {
        ProgrammeHeadline(
            eyebrow = { ProgrammeEyebrow(channel?.icon, subject.detail) {
                if (!item.enabled) StatusBadge(stringResource(R.string.schedule_automatic_off), StatusBadgeTone.NEUTRAL)
            } },
            title = subject.name, tagPrefix = "automatic-recording-pane", synopsisLines = 0,
            titleStyle = MaterialTheme.typography.titleLarge,
            status = { statusModifier ->
                Column(statusModifier) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ProgrammeStatusLine(whenLabel, R.drawable.ic_event, "automatic-recording-pane-when")
                        ProgrammeStatusLine(stringResource(R.string.schedule_automatic_keep, keepLabel),
                            R.drawable.ic_hourglass_bottom, "automatic-recording-pane-keep")
                        if (item.upcomingEntries.isEmpty()) {
                            ProgrammeStatusLine(stringResource(R.string.schedule_automatic_nothing),
                                R.drawable.ic_schedule, "automatic-recording-pane-next", maxLines = 2)
                        }
                    }
                    if (item.upcomingEntries.isNotEmpty()) ScheduleUpcomingEntries(item, channels, subject.name)
                }
            },
        )
    }
}

/** How many next recordings the pane lists before "and N more". */
internal const val SCHEDULE_UPCOMING_SHOWN = 4

/** Its own section under the status lines, spaced like the editors' guidance column. */
@Composable
private fun ScheduleUpcomingEntries(item: AutomaticRecordingListItem, channels: List<Channel>, fallbackTitle: String) {
    Column(Modifier.padding(top = TvSpacing16), verticalArrangement = Arrangement.spacedBy(TvSpacing12)) {
        RecordingEditorHeading(stringResource(R.string.timer_editor_next_recordings),
            Modifier.semantics { heading() })
        for (entry in item.upcomingEntries.take(SCHEDULE_UPCOMING_SHOWN)) {
            val time = entry.start?.epochSeconds?.let { start ->
                formatRecordingDate(start, dateOnly = true) + " • " +
                    (entry.stop?.epochSeconds?.let { formatTimeInterval(start, it) } ?: formatClock(start))
            }
            val channel = if (item.channelId != null) null
                else channels.firstOrNull { it.id == entry.channelId }?.name ?: entry.channelName
            val title = listOf(entry.subtitle, entry.title).firstOrNull { !it.isNullOrBlank() } ?: fallbackTitle
            val meta = listOfNotNull(time, channel?.takeIf(String::isNotBlank)).joinToString(" • ")
            RecordingEditorPreviewPair(title, meta, titleMaxLines = 1)
        }
        if (item.upcoming > SCHEDULE_UPCOMING_SHOWN) {
            RecordingEditorSecondary(
                stringResource(R.string.timer_editor_more_removed, item.upcoming - SCHEDULE_UPCOMING_SHOWN))
        }
    }
}
