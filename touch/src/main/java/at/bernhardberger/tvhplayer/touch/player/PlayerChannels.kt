package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelRows
import at.bernhardberger.tvhplayer.touch.channels.ChannelsActions
import at.bernhardberger.tvhplayer.touch.channels.ChannelsLayout
import at.bernhardberger.tvhplayer.touch.channels.ChannelsLoadingLine
import at.bernhardberger.tvhplayer.touch.channels.ChannelsSkeleton
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.channels.TagFilter
import at.bernhardberger.tvhplayer.touch.ui.ListFailure
import at.bernhardberger.tvhplayer.touch.ui.ListMessage
import at.bernhardberger.tvhplayer.touch.ui.MessageKind
import at.bernhardberger.tvhplayer.touch.ui.OfflineBanner
import at.bernhardberger.tvhplayer.touch.ui.OfflineTexts
import at.bernhardberger.tvhplayer.touch.ui.CompactBrowseMargin

private val ChannelListShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhoneChannelSheet(
    state: ChannelsState,
    onChoice: (PlayerChoice) -> Unit,
    onChannel: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
    minimumPeek: Dp,
    onHandleHeight: (Int) -> Unit,
    program: @Composable (Modifier) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sheet = rememberBottomSheetState(
        SheetValue.PartiallyExpanded, setOf(SheetValue.PartiallyExpanded, SheetValue.Expanded),
    )
    var headerHeight by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.clipToBounds()) {
        val header = with(LocalDensity.current) { headerHeight.toDp() }
        val naturalPeek = if (headerHeight == 0) BottomSheetDefaults.SheetPeekHeight else maxHeight - header
        val peek = maxOf(naturalPeek, minimumPeek).coerceAtMost(maxHeight)
        BottomSheetScaffold(
            scaffoldState = rememberBottomSheetScaffoldState(sheet),
            containerColor = Color.Transparent,
            sheetPeekHeight = peek,
            sheetShape = ChannelListShape,
            sheetContainerColor = colors.surfaceContainer,
            sheetContentColor = colors.onSurface,
            sheetShadowElevation = 0.dp,
            sheetDragHandle = {
                BottomSheetDefaults.DragHandle(
                    Modifier.wrapContentHeight(unbounded = true).onSizeChanged { onHandleHeight(it.height) },
                )
            },
            sheetContent = {
                InlineChannelList(
                    state, ChannelsLayout.Phone, onChoice, onChannel, onRetry,
                    Modifier.fillMaxHeight().padding(horizontal = CompactBrowseMargin),
                )
            },
        ) { padding ->
            // The scaffold's bottom padding is the resting peek, not the raised sheet.
            program(
                Modifier.padding(padding)
                    .verticalScroll(scroll, enabled = header > maxHeight - peek)
                    .onSizeChanged { headerHeight = it.height },
            )
        }
    }
}

@Composable
internal fun UprightChannelList(
    state: ChannelsState,
    onChoice: (PlayerChoice) -> Unit,
    onChannel: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), ChannelListShape, colors.surfaceContainer, colors.onSurface) {
        InlineChannelList(
            state, ChannelsLayout.Upright, onChoice, onChannel, onRetry,
            Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
        )
    }
}

@Composable
private fun InlineChannelList(
    state: ChannelsState,
    layout: ChannelsLayout,
    onChoice: (PlayerChoice) -> Unit,
    onChannel: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("player-channel-list").windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .padding(horizontal = if (layout == ChannelsLayout.Phone) 0.dp else 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.player_channels), Modifier.semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium,
                )
                state.displayedList?.takeIf { it.tags.isNotEmpty() }?.let { list ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        TagFilter(list, { onChoice(PlayerChoice.ChannelTag(it)) })
                    }
                }
            }
        }
        PlayerChannelsContent(state, layout, onChannel, { onChoice(PlayerChoice.ChannelTag(it)) }, onRetry)
    }
}

/** The same browse states under the picture and inside the side sheet, with no settings route. */
@Composable
internal fun PlayerChannelsContent(
    state: ChannelsState,
    layout: ChannelsLayout,
    onChannel: (String) -> Unit,
    onTag: (String?) -> Unit,
    onRetry: () -> Unit,
) {
    val rows = ChannelsActions(onPlay = onChannel, onTag = onTag, onRetry = onRetry, onOpenConnectionSettings = {})
    when (state) {
        ChannelsState.Loading -> Column {
            ChannelsLoadingLine(layout)
            ChannelsSkeleton(layout)
        }
        ChannelsState.Unreachable -> ListMessage(MessageKind.Unreachable, onSettings = null, onRetry = onRetry)
        ChannelsState.SignInFailed -> ListMessage(MessageKind.SignInFailed, onSettings = null)
        ChannelsState.SignInNeeded -> ListMessage(MessageKind.SignInNeeded, onSettings = null)
        is ChannelsState.Failed -> ListFailure(state.error, onRetry, onSettings = null)
        is ChannelsState.Offline -> Column {
            OfflineBanner(!layout.tablet, PlayerChannelsOfflineTexts, onSettings = null, retryIn = state.retryIn)
            ChannelRows(state.list, layout, rows, offline = true, showColumnLabels = false)
        }
        is ChannelsState.Ready -> if (state.list.rows.isEmpty()) {
            val kind = if (state.list.selectedTagId == null) MessageKind.Empty else MessageKind.EmptyTag
            ListMessage(kind, onSettings = null, onShowAll = { onTag(null) })
        } else {
            ChannelRows(state.list, layout, rows, showColumnLabels = false)
        }
    }
}

private val PlayerChannelsOfflineTexts = OfflineTexts(R.string.channels_offline, R.string.channels_saved)
