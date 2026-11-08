package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.TwoColumnDialog
import at.bernhardberger.tvhplayer.ui.components.TwoColumnDialogButton

internal fun DvrArchiveGrouping.labelRes(): Int = when (this) {
    DvrArchiveGrouping.FOLDERS -> R.string.recordings_grouping_folders
    DvrArchiveGrouping.CHANNELS -> R.string.recordings_grouping_channels
    DvrArchiveGrouping.GENRES -> R.string.recordings_grouping_genres
    DvrArchiveGrouping.NONE -> R.string.recordings_grouping_none
}

private fun DvrArchiveGrouping.detailRes(): Int = when (this) {
    DvrArchiveGrouping.FOLDERS -> R.string.recordings_grouping_folders_detail
    DvrArchiveGrouping.CHANNELS -> R.string.recordings_grouping_channels_detail
    DvrArchiveGrouping.GENRES -> R.string.recordings_grouping_genres_detail
    DvrArchiveGrouping.NONE -> R.string.recordings_grouping_none_detail
}

/**
 * The grouping choice beside the root title. It is its own focus stop between the tabs and the
 * list: every direction is decided here so no neighbour's key handling can move or activate through it.
 * [onUp], [onDown] and [onLeft] move focus on the first press only; held repeats are swallowed.
 */
@Composable
internal fun ArchiveGroupingPill(
    grouping: DvrArchiveGrouping,
    focusRequester: FocusRequester,
    onOpen: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: (() -> Unit)?,
    onBack: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    BackHandler(enabled = focused, onBack = onBack)
    val label = stringResource(grouping.labelRes())
    val description = stringResource(R.string.recordings_grouping_pill_description, label)
    Surface(
        onClick = onOpen,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .onPreviewKeyEvent { event ->
                val move: (() -> Unit)? = when (event.key) {
                    Key.DirectionUp -> onUp
                    Key.DirectionDown -> onDown
                    Key.DirectionLeft -> onLeft ?: {}
                    Key.DirectionRight -> ({})
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) move?.invoke()
                true
            }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("recordings-grouping-pill"),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_sort), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(TvSpacing8))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The four groupings as the shared two-column dialog's actions, the current one checked and focused.
 * Choosing calls [onChoose] and the owner closes it; Back reaches [onClose] without a change.
 */
@Composable
internal fun ArchiveGroupingChoice(
    current: DvrArchiveGrouping,
    onChoose: (DvrArchiveGrouping) -> Unit,
    onClose: () -> Unit,
    backEnabled: Boolean,
) {
    val options = DvrArchiveGrouping.entries
    val requesters = remember { options.associateWith { FocusRequester() } }
    LaunchedEffect(backEnabled) {
        if (!backEnabled) return@LaunchedEffect
        withFrameNanos { }
        requesters.getValue(current).requestFocus()
    }
    TwoColumnDialog(
        title = stringResource(R.string.recordings_grouping_title),
        message = stringResource(R.string.recordings_grouping_message),
        onDismiss = onClose,
        modifier = Modifier.testTag("recordings-grouping-choice"),
        backEnabled = backEnabled,
    ) {
        options.forEachIndexed { index, option ->
            TwoColumnDialogButton(
                label = stringResource(option.labelRes()),
                onClick = { onChoose(option) },
                modifier = Modifier.testTag("recordings-grouping-option-${option.name.lowercase()}")
                    .focusRequester(requesters.getValue(option))
                    .focusProperties {
                        up = options.getOrNull(index - 1)?.let(requesters::get) ?: FocusRequester.Cancel
                        down = options.getOrNull(index + 1)?.let(requesters::get) ?: FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                subtitle = stringResource(option.detailRes()),
                icon = if (option == current) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(24.dp)) }
                } else null,
            )
        }
    }
}
