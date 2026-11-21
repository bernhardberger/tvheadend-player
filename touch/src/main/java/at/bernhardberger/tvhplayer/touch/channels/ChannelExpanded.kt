@file:Suppress("MagicNumber") // Layout weights and image proportions.

package at.bernhardberger.tvhplayer.touch.channels

import at.bernhardberger.tvhplayer.touch.guide.RecordControlButton
import at.bernhardberger.tvhplayer.touch.ui.ProgramRecordControl
import at.bernhardberger.tvhplayer.touch.ui.RuleScheduledState
import at.bernhardberger.tvhplayer.touch.ui.rememberStandalonePicture
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.bernhardberger.tvhplayer.touch.R

@Composable
internal fun ChannelExpanded(
    row: ChannelRow,
    details: ChannelDetails,
    layout: ChannelsLayout,
    actions: ChannelsActions,
) {
    Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 12.dp)) {
        when (layout) {
            ChannelsLayout.Wide -> WideDetails(row, details, actions)
            ChannelsLayout.Upright -> UprightDetails(row, details, actions)
            ChannelsLayout.Phone -> PhoneDetails(row, details, actions)
        }
    }
}

@Composable
private fun WideDetails(row: ChannelRow, details: ChannelDetails, actions: ChannelsActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        rememberStandalonePicture(details.artwork, details.artworkModel)?.let { ProgramArt(it, Modifier.weight(2.8f)) }
        Column(Modifier.weight(3.8f)) {
            Episode(details)
            Synopsis(details)
            ProgramActions(row.id, row.now, actions)
        }
        Column(Modifier.weight(3.4f)) {
            Following(details.following, actions.onShowInGuide?.let { { it(row.id) } })
        }
    }
}

@Composable
private fun UprightDetails(row: ChannelRow, details: ChannelDetails, actions: ChannelsActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        rememberStandalonePicture(details.artwork, details.artworkModel)?.let { ProgramArt(it, Modifier.weight(4f)) }
        Column(Modifier.weight(6f)) {
            Episode(details)
            Synopsis(details)
            ProgramActions(row.id, row.now, actions)
        }
    }
    Spacer(Modifier.height(12.dp))
    CompactFollowing(row, details, actions)
}

@Composable
private fun PhoneDetails(row: ChannelRow, details: ChannelDetails, actions: ChannelsActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        rememberStandalonePicture(details.artwork, details.artworkModel)?.let { ProgramArt(it, Modifier.weight(2f)) }
        Column(Modifier.weight(3f)) { Episode(details, row.now?.timeRange, compact = true) }
    }
    Synopsis(details)
    ProgramActions(row.id, row.now, actions)
    CompactFollowing(row, details, actions)
}

@Composable
private fun CompactFollowing(row: ChannelRow, details: ChannelDetails, actions: ChannelsActions) {
    val programs = (listOfNotNull(row.next) + details.following).take(COMPACT_FOLLOWING_LIMIT)
    Following(programs, actions.onShowInGuide?.let { { it(row.id) } })
}

@Composable
private fun ProgramArt(picture: Painter, modifier: Modifier) {
    Image(
        picture, null, modifier.aspectRatio(PROGRAM_ART_RATIO).clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun Episode(details: ChannelDetails, timeRange: String? = null, compact: Boolean = false) {
    details.episode?.let {
        Text(it, fontSize = if (compact) 14.sp else 15.sp, style = MaterialTheme.typography.bodyMedium)
    }
    listOfNotNull(timeRange, details.metadata).forEach {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Synopsis(details: ChannelDetails) {
    details.synopsis?.let {
        Text(
            it, Modifier.padding(top = 10.dp).widthIn(max = 520.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgramActions(id: String, now: ChannelProgram?, actions: ChannelsActions) {
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.onPlay?.let { play ->
            Button(onClick = { play(id) }) {
                ChannelActionLabel(R.drawable.channels_play, stringResource(R.string.channels_watch))
            }
        }
        val record = now?.record
        val onControl = when (record) {
            ProgramRecordControl.Record -> actions.onRecord
            ProgramRecordControl.Cancel -> actions.onCancel
            null -> null
        }
        if (now?.scheduledByRule == true) {
            RuleScheduledState()
        } else if (record != null && onControl != null) {
            RecordControlButton(record) { onControl(id) }
        }
        actions.onDetails?.let { details ->
            TextButton(onClick = { details(id) }) {
                ChannelActionLabel(R.drawable.channels_info, stringResource(R.string.channels_details))
            }
        }
    }
}

@Composable
private fun Following(programs: List<ChannelProgram>, onShowInGuide: (() -> Unit)?) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        programs.forEach { program ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                program.start?.let {
                    Text(
                        it, Modifier.widthIn(min = 84.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline,
                    )
                }
                Text(
                    program.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onShowInGuide != null) {
            TextButton(onClick = onShowInGuide) {
                ChannelActionLabel(R.drawable.calendar_month_outline, stringResource(R.string.channels_show_in_guide))
            }
        }
    }
}

@Composable
internal fun ChannelActionLabel(@DrawableRes icon: Int, label: String) {
    Icon(painterResource(icon), null, Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(label)
}

private const val PROGRAM_ART_RATIO = 16f / 9f
private const val COMPACT_FOLLOWING_LIMIT = 3
