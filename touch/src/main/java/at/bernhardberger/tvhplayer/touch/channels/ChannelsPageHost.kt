package at.bernhardberger.tvhplayer.touch.channels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.touch.guide.ChannelPage
import at.bernhardberger.tvhplayer.touch.guide.GuideActions
import at.bernhardberger.tvhplayer.touch.guide.GuideLayout
import at.bernhardberger.tvhplayer.touch.guide.ProgramPageHost
import at.bernhardberger.tvhplayer.touch.guide.ProgramPageStack
import at.bernhardberger.tvhplayer.touch.guide.guideLayout
import at.bernhardberger.tvhplayer.touch.guide.programDetailsForPages
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.DvrControls
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationBackEnabled
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlinx.coroutines.CoroutineDispatcher

@Composable
internal fun ChannelsPageHost(
    state: ChannelsState,
    scope: ChannelScopeState,
    session: TvheadendSession,
    channels: ChannelsViewModel,
    pages: ProgramPageStack?,
    dvr: DvrControls,
    dispatcher: CoroutineDispatcher,
    playingId: String?,
    lastWatchedId: ChannelId?,
    behindLiveId: String?,
    onWatch: ((String) -> Unit)?,
    onChannelWatch: ((String, BrowseContextAction, String?) -> Unit)?,
    onRetry: () -> Unit,
    onOpenConnections: () -> Unit,
    onSignIn: () -> Unit,
) {
    val openChannel = pages?.rootChannel
    if (openChannel != null) {
        LaunchedEffect(pages, openChannel, scope) {
            val id = openChannel.channelId
            // A retained channel can leave a tag while offline; an unknown catalog cannot prove deletion.
            val canPrune = scope.channelCatalogCurrent || scope.scope.allChannels.any { it.id.value.toString() == id }
            if (canPrune && scope.scope.visibleChannels.none { it.id.value.toString() == id }) {
                pages.removeChannel(openChannel)
            }
        }
    }
    val details = if (pages != null) programDetailsForPages(pages, session, playingId, dispatcher) else emptyMap()
    val page: @Composable (String, Modifier, Boolean, Boolean, () -> Unit) -> Unit =
        { id, modifier, inPane, active, back ->
            ChannelPage(
                session, channels, id, back, { if (active) pages?.openDetails(it) },
                dvr::record, dvr::cancel, dvr::stopProgram, modifier,
                inPane = inPane, playingId = playingId, active = active, mappingDispatcher = dispatcher,
                behindLive = behindLiveId == id, onWatch = onChannelWatch,
            )
        }
    val shownPages = pages ?: remember { ProgramPageStack() }
    val layout = guideLayout()
    key(scope.profileId) {
        ProgramPageHost(
            destination = Destination.Channels,
            destinations = Destination.entries,
            layout = layout,
            pageStack = shownPages,
            details = details,
            actions = GuideActions(
                onWatch = onWatch, onRecord = dvr::record, onRecordOptions = null, onDay = {}, onJump = {}, onTag = {},
                onCancel = dvr::cancel, onStop = dvr::stopProgram, onAiring = { pages?.openDetails(it) },
            ),
            onShowInGuide = null,
            channelPage = { entry, active, back -> page(entry.channelId, Modifier, false, active, back) },
            channelInPane = layout == GuideLayout.Wide,
        ) { top, modifier ->
            val active = top && LocalDestinationBackEnabled.current
            CompositionLocalProvider(LocalChannelProgressClock provides rememberCurrentEpochSeconds()) {
                ChannelsScreen(
                    state = state,
                    modifier = modifier,
                    actions = ChannelsActions(
                        onPlay = onWatch,
                        onTag = { id -> channels.selectTag(id?.let { ChannelTagId(it.toLong()) }) },
                        onRetry = onRetry,
                        onOpenConnectionSettings = onOpenConnections,
                        onSignIn = onSignIn,
                        onOpenChannel = { if (active) pages?.selectChannel(it) },
                    ),
                    lastWatchedId = lastWatchedId?.value?.toString(),
                    openChannelId = openChannel?.channelId,
                    pane = { id, paneModifier -> page(id, paneModifier, true, active) {} },
                    windowLayout = layout,
                )
            }
        }
    }
}
