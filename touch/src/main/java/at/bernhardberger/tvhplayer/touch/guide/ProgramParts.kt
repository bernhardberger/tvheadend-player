// Parts the overview, a channel's day and the details page share; sizes are the drawings' (round 37).
@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.guide

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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

/** Watch (on now only), Record with its series arrow, Details; they wrap in a narrow place. */
@Composable
internal fun ProgramActions(
    channelId: String,
    programId: String,
    onNow: Boolean,
    actions: GuideActions,
    modifier: Modifier = Modifier,
    onDetails: (() -> Unit)? = null,
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
        if (onNow) WatchButton(contentPadding = padding) { actions.onWatch(channelId) }
        RecordButton({ actions.onRecord(programId) }, { actions.onRecordOptions(programId) }, contentPadding = padding)
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

/** Record, and an arrow for the ways that need a choice (the series). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecordButton(
    onRecord: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = SplitButtonDefaults.leadingButtonContentPaddingFor(ButtonDefaults.MinHeight),
) {
    val options = stringResource(R.string.guide_record_options)
    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.TonalLeadingButton(
                onClick = onRecord,
                contentPadding = contentPadding,
            ) {
                ChannelActionLabel(R.drawable.channels_record, stringResource(R.string.channels_record))
            }
        },
        trailingButton = {
            SplitButtonDefaults.TonalTrailingButton(
                checked = false,
                onCheckedChange = { onOptions() },
                modifier = Modifier.semantics { contentDescription = options },
            ) {
                Icon(
                    painterResource(R.drawable.channels_expand), null,
                    Modifier.size(SplitButtonDefaults.TrailingIconSize),
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
        color = MaterialTheme.colorScheme.primary.copy(alpha = BADGE_ALPHA),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            stringResource(R.string.channels_on_now), Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** How far the program has run: the program's own color, red while it records. */
@Composable
internal fun ProgramBar(progress: Float, recording: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) }, modifier = modifier,
        color = if (recording) TvRecordingColor else scheme.primary,
        trackColor = scheme.onSurface.copy(alpha = TRACK_ALPHA), gapSize = 0.dp, drawStopIndicator = {},
    )
}

/** The bar with what is left after it. */
@Composable
internal fun ProgramProgress(progress: Float, timeLeft: String?, recording: Boolean, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ProgramBar(progress, recording, Modifier.weight(1f).height(4.dp))
        timeLeft?.let {
            Text(
                stringResource(R.string.channels_time_left, it), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** A 16:9 picture with the on-air strip along its foot and REC in its corner while it records. */
@Composable
internal fun ProgramPicture(
    picture: Painter?,
    modifier: Modifier,
    progress: Float? = null,
    recording: Boolean = false,
    strip: Boolean = true,
) {
    Box(
        modifier.aspectRatio(PICTURE_RATIO).clip(PictureShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (picture != null) Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (recording) Box(Modifier.padding(6.dp)) { RecPill() }
        if (strip && progress != null) {
            ProgramBar(progress, recording, Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp))
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
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
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
private const val TRACK_ALPHA = 0.2f
private const val BADGE_ALPHA = 0.16f

internal val CompactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
