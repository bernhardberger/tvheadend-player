package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.HomeActivityCard

/**
 * The running recordings, one 16:9 card each in a row above the chips: Home's recording card. OK opens the
 * recording like its row; the context-menu key and a long press open the row's menu. The row decides
 * every direction itself, like the chip row: [onUp], [onDown] and [onLeft] (from the first card) act on the
 * key's release and Right stops at the last card. Back is [onBack].
 */
@Composable
internal fun RecordingNowCards(
    entries: List<DvrEntry>,
    channelsById: Map<ChannelId, Channel>,
    nowSec: Long,
    cardFocus: FocusRequester,
    focusTarget: DvrEntryId?,
    contentPadding: PaddingValues,
    onOpen: (DvrEntry) -> Unit,
    onOpenMenu: (DvrEntry) -> Unit,
    onCardFocus: (DvrEntry, Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    var firstFocused by remember { mutableStateOf(false) }
    // Left leaves to the drawer only when its press began on the first item, not when it moved focus there.
    var leftFromFirst by remember { mutableStateOf(false) }
    // [cardFocus] lands on the card the viewer was last on, else the first.
    val targetIndex = entries.indexOfFirst { it.id == focusTarget }.coerceAtLeast(0)
    BackHandler(enabled = hasFocus, onBack = onBack)
    val label = stringResource(R.string.home_activity_recording)
    LazyRow(
        modifier = modifier
            .onFocusChanged { hasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                val move: (() -> Unit)? = when (event.key) {
                    Key.DirectionUp -> onUp
                    Key.DirectionDown -> onDown
                    Key.DirectionLeft -> {
                        if (event.type == KeyEventType.KeyDown) leftFromFirst = firstFocused
                        if (!leftFromFirst) return@onPreviewKeyEvent false
                        onLeft ?: {}
                    }
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyUp && !event.nativeKeyEvent.isCanceled) move?.invoke()
                true
            }
            .testTag("recordings-now-cards"),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.id.value }) { index, entry ->
            val channel = entry.channelId?.let(channelsById::get)
            val description = listOf(label, entry.title.orEmpty(), channel?.name ?: entry.channelName.orEmpty())
                .filter(String::isNotBlank).joinToString(", ")
            HomeActivityCard(
                channel = channel,
                event = null,
                entry = entry,
                playing = false,
                nowSec = nowSec,
                width = 300.dp,
                description = description,
                onClick = { onOpen(entry) },
                onLongClick = { onOpenMenu(entry) },
                modifier = Modifier
                    .padding(vertical = TvSpacing8)
                    .then(if (index == targetIndex) Modifier.focusRequester(cardFocus) else Modifier)
                    .then(
                        if (index == entries.lastIndex) Modifier.focusProperties { right = FocusRequester.Cancel }
                        else Modifier,
                    )
                    .onFocusChanged {
                        if (index == 0) firstFocused = it.isFocused
                        onCardFocus(entry, it.isFocused)
                    }
                    .testTag("recordings-now-card-${entry.id.value}"),
            )
        }
    }
}
