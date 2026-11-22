// Parts the overview, a channel's day and the details page share; sizes are the drawings' (round 37).
@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.guide

import at.bernhardberger.tvhplayer.touch.ui.ArtworkImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelActionLabel
import at.bernhardberger.tvhplayer.touch.recordings.RecPill
import at.bernhardberger.tvhplayer.touch.ui.ProgramRecordControl
import at.bernhardberger.tvhplayer.touch.ui.RuleSchedule
import at.bernhardberger.tvhplayer.touch.ui.RuleScheduleState
import at.bernhardberger.tvhplayer.ui.TvRecordingColor

@Composable
internal fun guideLayout(): GuideLayout {
    val window = currentWindowAdaptiveInfoV2().windowSizeClass
    return when {
        window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> GuideLayout.Wide
        window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> GuideLayout.Upright
        else -> GuideLayout.Phone
    }
}

/**
 * Watch (on now only), Record with its series arrow, Details; they wrap in a narrow place. The first action
 * is filled: Record is tonal beside Watch and filled where it leads (section 9, "Which button is filled").
 */
@Composable
internal fun ProgramActions(
    channelId: String,
    programId: String,
    onNow: Boolean,
    record: ProgramRecordControl?,
    actions: GuideActions,
    modifier: Modifier = Modifier,
    onDetails: (() -> Unit)? = null,
    ruleSchedule: RuleSchedule? = null,
    detailsIcon: Boolean = true,
    compact: Boolean = false,
) {
    // In a phone or upright card the three actions fit one line only with the tighter padding, as drawn.
    val padding = if (compact) CompactButtonPadding else ButtonDefaults.ContentPadding
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val watch = actions.onWatch?.takeIf { onNow }
        if (watch != null) WatchButton(contentPadding = padding) { watch(channelId) }
        ProgramRecordAction(
            record, programId, actions, contentPadding = padding, leads = watch == null,
            ruleSchedule = ruleSchedule,
        )
        if (onDetails != null) {
            TextButton(onClick = onDetails) {
                if (detailsIcon) {
                    ChannelActionLabel(R.drawable.channels_info, stringResource(R.string.channels_details))
                } else {
                    Text(stringResource(R.string.channels_details))
                }
            }
        }
    }
}

@Composable
internal fun WatchButton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = modifier, contentPadding = contentPadding) {
        ChannelActionLabel(R.drawable.channels_play, stringResource(R.string.channels_watch))
    }
}

/**
 * The program's recording control: Record, split with its options arrow when there are options, or Cancel
 * of its scheduled recording, or Stop. Without an action behind it, it is left out. A program an automatic recording
 * scheduled shows its status instead ([RuleScheduleState]).
 */
@Composable
internal fun ProgramRecordAction(
    record: ProgramRecordControl?,
    programId: String,
    actions: GuideActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
    leads: Boolean = false,
    ruleSchedule: RuleSchedule? = null,
) {
    if (ruleSchedule != null) {
        RuleScheduleState(ruleSchedule, modifier)
        return
    }
    val onControl = when (record) {
        ProgramRecordControl.Record -> actions.onRecord
        ProgramRecordControl.Cancel -> actions.onCancel
        ProgramRecordControl.Stop -> actions.onStop
        null -> null
    }
    val onOptions = actions.onRecordOptions
    when {
        record == ProgramRecordControl.Record && onControl != null && onOptions != null -> RecordButton(
            { onControl(programId) }, { onOptions(programId) }, modifier,
            contentPadding ?: SplitButtonDefaults.leadingButtonContentPaddingFor(ButtonDefaults.MinHeight), leads,
        )
        record != null && onControl != null ->
            RecordControlButton(record, modifier, contentPadding ?: ButtonDefaults.ContentPadding, leads) {
                onControl(programId)
            }
    }
}

/** Record without options, Cancel or Stop: filled where it [leads], else tonal. */
@Composable
internal fun RecordControlButton(
    control: ProgramRecordControl,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    leads: Boolean = false,
    onClick: () -> Unit,
) {
    val label: @Composable RowScope.() -> Unit = { ChannelActionLabel(control.icon, stringResource(control.label)) }
    if (leads) {
        Button(onClick = onClick, modifier = modifier, contentPadding = contentPadding, content = label)
    } else {
        FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = contentPadding, content = label)
    }
}

/**
 * Record, and an arrow for the ways that need a choice (the series). Tonal beside Watch; filled where
 * Record [leads]: no Watch before it, as on a later program or in the player's Info sheet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecordButton(
    onRecord: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = SplitButtonDefaults.leadingButtonContentPaddingFor(ButtonDefaults.MinHeight),
    leads: Boolean = false,
) {
    val options = stringResource(R.string.guide_record_options)
    val label: @Composable RowScope.() -> Unit = {
        ChannelActionLabel(R.drawable.channels_record, stringResource(R.string.channels_record))
    }
    val arrow: @Composable RowScope.() -> Unit = {
        Icon(painterResource(R.drawable.channels_expand), null, Modifier.size(SplitButtonDefaults.TrailingIconSize))
    }
    val describe = Modifier.semantics { contentDescription = options }
    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            if (leads) {
                SplitButtonDefaults.LeadingButton(onClick = onRecord, contentPadding = contentPadding, content = label)
            } else {
                SplitButtonDefaults.TonalLeadingButton(
                    onClick = onRecord, contentPadding = contentPadding, content = label,
                )
            }
        },
        trailingButton = {
            if (leads) {
                SplitButtonDefaults.TrailingButton(
                    checked = false, onCheckedChange = { onOptions() }, modifier = describe, content = arrow,
                )
            } else {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = false, onCheckedChange = { onOptions() }, modifier = describe, content = arrow,
                )
            }
        },
    )
}

/** "On now": no Material badge carries a label; a small Surface does, as the drawings show. */
@Composable
internal fun OnNowBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            stringResource(R.string.channels_on_now), Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * How far the program has run: the program's own color, red while it records; orange, as the Channels
 * list's playing row, when it is the item [watching] (section 13: orange wins over red).
 */
@Composable
internal fun ProgramBar(
    progress: Float,
    recording: Boolean,
    modifier: Modifier,
    watching: Boolean = false,
    pictureEdge: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        watching -> scheme.tertiary
        recording -> TvRecordingColor
        else -> scheme.primary
    }
    if (pictureEdge) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) }, modifier = modifier, color = color,
            trackColor = scheme.onSurface.copy(alpha = PICTURE_TRACK_ALPHA), gapSize = 0.dp, drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier, color = color)
    }
}

/** The bar with what is left after it. */
@Composable
internal fun ProgramProgress(
    progress: Float,
    timeLeft: String?,
    recording: Boolean,
    modifier: Modifier,
    watching: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ProgramBar(progress, recording, Modifier.weight(1f), watching)
        timeLeft?.let {
            Text(
                stringResource(R.string.channels_time_left, it), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * A 16:9 picture with the on-air strip along its foot and REC in its corner while it records; the
 * strip of the program [watching] in the player is orange (section 13).
 */
@Composable
internal fun ProgramPicture(
    picture: Painter?,
    modifier: Modifier,
    progress: Float? = null,
    recording: Boolean = false,
    strip: Boolean = true,
    /** The server picture's model, drawn when [picture] is null. */
    model: Any? = null,
    watching: Boolean = false,
) {
    Box(
        modifier.aspectRatio(PICTURE_RATIO).clip(PictureShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        ArtworkImage(picture, model, Modifier.fillMaxSize(), ContentScale.Crop)
        if (recording) Box(Modifier.padding(6.dp)) { RecPill() }
        if (strip && progress != null) {
            ProgramBar(
                progress, recording, Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp),
                watching, pictureEdge = true,
            )
        }
    }
}

/** A filled tonal button that opens a menu of [options], as the Channels tag filter does. */
@Composable
internal fun MenuButton(
    icon: Int,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalButton(
            onClick = { open = true },
            contentPadding = contentPadding,
        ) {
            ChannelActionLabel(icon, options.getOrElse(selected) { "" })
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.channels_expand), null, Modifier.size(ButtonDefaults.IconSize))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        onSelect(index)
                    },
                    trailingIcon = if (index == selected) {
                        { Icon(painterResource(R.drawable.channels_check), null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

internal const val PICTURE_RATIO = 16f / 9f
internal val PictureShape = RoundedCornerShape(12.dp)
private const val PICTURE_TRACK_ALPHA = 0.2f

internal val CompactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
