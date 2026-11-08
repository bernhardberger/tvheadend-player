package at.bernhardberger.tvhplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.WarmPlaybackTarget
import at.bernhardberger.tvhplayer.core.WarmReturnOpportunity
import at.bernhardberger.tvhplayer.core.armWarmReturn
import at.bernhardberger.tvhplayer.core.clearWarmReturn
import at.bernhardberger.tvhplayer.core.consumeWarmReturn
import at.bernhardberger.tvhplayer.core.rearmWarmReturn
import at.bernhardberger.tvhplayer.core.rearmWarmReturnForPlaybackSelection
import at.bernhardberger.tvhplayer.core.warmPlaybackTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Browse owns delayed zaps; explicit entry withdraws those and waits for any running start. */
internal class BrowseChannelStepHandoff {
    private var generation = 0L
    private var cancelPending: (() -> Unit)? = null
    private var running: Job? = null
    private var playingChannelId: ChannelId? = null
    private var pendingChannelId: ChannelId? = null

    val channelCursor: ChannelId? get() = pendingChannelId ?: playingChannelId

    fun playbackChanged(channelId: ChannelId?) {
        playingChannelId = channelId
    }

    val starting: Boolean get() = running?.isCompleted == false

    fun queue(channelId: ChannelId? = null, cancelPending: () -> Unit): Long {
        withdrawPending()
        ++generation
        pendingChannelId = channelId
        this.cancelPending = cancelPending
        return generation
    }

    fun withdrawPending() {
        val cancel = cancelPending ?: return
        ++generation
        cancelPending = null
        pendingChannelId = null
        cancel()
    }

    fun start(
        scope: CoroutineScope,
        token: Long,
        entryPending: () -> Boolean = { false },
        onFinished: () -> Unit = {},
        playback: suspend () -> Unit,
    ) {
        if (entryPending() || token != generation || cancelPending == null) return
        cancelPending = null
        val superseded = running
        superseded?.cancel()
        running = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                superseded?.join()
                playback()
            } finally {
                if (token == generation) pendingChannelId = null
                onFinished()
                // A cancelled join must not let the next start leapfrog an older cleanup.
                withContext(NonCancellable) { superseded?.join() }
            }
        }
    }

    suspend fun joinStart() { running?.join() }
}

internal sealed interface PlayerRouteTarget {
    data class Live(
        val channelId: ChannelId,
        val channelName: String,
    ) : PlayerRouteTarget

    data class Recording(
        val recordingId: DvrEntryId,
    ) : PlayerRouteTarget
}

/** A recording can be returned to only while both its identity and owning session still match. */
internal fun warmRecordingId(
    activeTarget: AppPlaybackTarget?,
    selection: RecordingPlaybackSelection?,
    currentSession: CurrentSessionObservation?,
): DvrEntryId? = (activeTarget as? AppPlaybackTarget.Recording)?.recordingId?.takeIf {
    selection?.recordingId == it && selection.currentSession === currentSession
}

@Stable
internal class AppRootPlaybackOrchestrator {
    private var playbackSelectionGeneration = 0L

    var warmReturn by mutableStateOf(WarmReturnOpportunity())
        private set

    fun activePlaybackChanged(
        activeChannelId: ChannelId?,
        activeRecordingId: DvrEntryId?,
    ) {
        warmReturn = when (val target = warmPlaybackTarget(activeChannelId, activeRecordingId)) {
            WarmPlaybackTarget.NONE -> clearWarmReturn()
            else -> armWarmReturn(target)
        }
    }

    /**
     * [viewingIntent] is the intent the click noted synchronously, before this suspends. It is
     * the start's generation when the channel changes, and it alone keeps an older pending
     * session Stop from closing the player when the requested channel is already playing.
     */
    suspend fun requestLivePlayer(
        activeChannelId: ChannelId?,
        activeRecordingId: DvrEntryId?,
        requestedChannelId: ChannelId,
        requestedChannelName: String,
        viewingIntent: Long,
        startPlayback: suspend (viewingIntent: Long) -> Unit,
    ): PlayerRouteTarget.Live? {
        val generation = ++playbackSelectionGeneration
        warmReturn = rearmWarmReturnForPlaybackSelection(
            current = warmReturn,
            currentWarmTarget = warmPlaybackTarget(activeChannelId, activeRecordingId),
            requestedTarget = WarmPlaybackTarget.LIVE,
            currentIdentity = activeChannelId,
            requestedIdentity = requestedChannelId,
        )
        if (activeChannelId != requestedChannelId) startPlayback(viewingIntent)
        if (generation != playbackSelectionGeneration) return null
        return PlayerRouteTarget.Live(requestedChannelId, requestedChannelName)
    }

    suspend fun startLiveChannelFromBrowse(
        channelId: ChannelId,
        viewingIntent: Long,
        startPlayback: suspend (viewingIntent: Long) -> Unit,
    ) {
        ++playbackSelectionGeneration
        browseNavigationSelected(channelId, null)
        startPlayback(viewingIntent)
    }

    suspend fun requestRecordingPlayer(
        activeChannelId: ChannelId?,
        activeRecordingId: DvrEntryId?,
        requestedRecordingId: DvrEntryId,
        restartPlayback: Boolean = true,
        startPlayback: suspend () -> Unit,
    ): PlayerRouteTarget.Recording? {
        val generation = ++playbackSelectionGeneration
        warmReturn = rearmWarmReturnForPlaybackSelection(
            current = warmReturn,
            currentWarmTarget = warmPlaybackTarget(activeChannelId, activeRecordingId),
            requestedTarget = WarmPlaybackTarget.RECORDING,
            currentIdentity = activeRecordingId,
            requestedIdentity = requestedRecordingId,
        )
        if (restartPlayback || activeRecordingId != requestedRecordingId) startPlayback()
        if (generation != playbackSelectionGeneration) return null
        return PlayerRouteTarget.Recording(requestedRecordingId)
    }

    fun browseNavigationSelected(
        activeChannelId: ChannelId?,
        activeRecordingId: DvrEntryId?,
    ) {
        val target = warmPlaybackTarget(activeChannelId, activeRecordingId)
        if (target != WarmPlaybackTarget.NONE) warmReturn = rearmWarmReturn(target)
    }

    /**
     * A warm return starts nothing, so [noteViewingIntent] records the viewer's return before
     * the caller pushes the player. It runs only when there is a target to return to.
     */
    fun consumeWarmPlayerTarget(
        activeChannelId: ChannelId?,
        activeRecordingId: DvrEntryId?,
        currentChannelReadiness: CurrentChannelReadiness,
        noteViewingIntent: () -> Unit,
    ): PlayerRouteTarget? {
        if (!warmReturn.canReturn) return null
        val target = warmReturn.target
        warmReturn = consumeWarmReturn(warmReturn)
        return when (target) {
            WarmPlaybackTarget.LIVE -> activeChannelId?.let {
                livePlayerTarget(it, currentChannelReadiness)
            }
            WarmPlaybackTarget.RECORDING -> activeRecordingId?.let(PlayerRouteTarget::Recording)
            WarmPlaybackTarget.NONE -> null
        }?.also { noteViewingIntent() }
    }

}

internal fun livePlayerTarget(
    activeChannelId: ChannelId,
    readiness: CurrentChannelReadiness,
): PlayerRouteTarget.Live {
    val channel = (readiness as? CurrentChannelReadiness.Ready)
        ?.channels
        ?.firstOrNull { it.id == activeChannelId }
    return PlayerRouteTarget.Live(
        channelId = channel?.id ?: activeChannelId,
        channelName = channel?.name.orEmpty(),
    )
}
