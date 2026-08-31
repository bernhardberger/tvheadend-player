package at.bernhardberger.tvhplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.ProductProfile
import at.bernhardberger.tvhplayer.core.SimpleTvRoute
import at.bernhardberger.tvhplayer.core.SimpleTvRouteGuardAction
import at.bernhardberger.tvhplayer.core.WarmPlaybackTarget
import at.bernhardberger.tvhplayer.core.WarmReturnOpportunity
import at.bernhardberger.tvhplayer.core.allowsRoute
import at.bernhardberger.tvhplayer.core.armWarmReturn
import at.bernhardberger.tvhplayer.core.clearWarmReturn
import at.bernhardberger.tvhplayer.core.consumeWarmReturn
import at.bernhardberger.tvhplayer.core.rearmWarmReturn
import at.bernhardberger.tvhplayer.core.rearmWarmReturnForPlaybackSelection
import at.bernhardberger.tvhplayer.core.simpleTvRouteGuardAction
import at.bernhardberger.tvhplayer.core.warmPlaybackTarget

internal sealed interface PlayerRouteTarget {
    data class Live(
        val channelId: ChannelId,
        val channelName: String,
    ) : PlayerRouteTarget

    data class Recording(
        val recordingId: DvrEntryId,
    ) : PlayerRouteTarget
}

@Stable
internal class AppRootPlaybackOrchestrator {
    private var playbackSelectionGeneration = 0L
    private var routeGuardGeneration = 0L

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

    suspend fun requestLivePlayer(
        activeChannelId: ChannelId?,
        activeRecordingId: DvrEntryId?,
        requestedChannelId: ChannelId,
        requestedChannelName: String,
        startPlayback: suspend () -> Unit,
    ): PlayerRouteTarget.Live? {
        val generation = ++playbackSelectionGeneration
        warmReturn = rearmWarmReturnForPlaybackSelection(
            current = warmReturn,
            currentWarmTarget = warmPlaybackTarget(activeChannelId, activeRecordingId),
            requestedTarget = WarmPlaybackTarget.LIVE,
            currentIdentity = activeChannelId,
            requestedIdentity = requestedChannelId,
        )
        if (activeChannelId != requestedChannelId) startPlayback()
        if (generation != playbackSelectionGeneration) return null
        return PlayerRouteTarget.Live(requestedChannelId, requestedChannelName)
    }

    suspend fun requestRecordingPlayer(
        activeChannelId: ChannelId?,
        activeRecordingId: DvrEntryId?,
        requestedRecordingId: DvrEntryId,
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
        startPlayback()
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

    fun consumeWarmPlayerTarget(
        activeChannelId: ChannelId?,
        activeRecordingId: DvrEntryId?,
        currentChannelReadiness: CurrentChannelReadiness,
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
        }
    }

    suspend fun enforceRouteGuard(
        profile: ProductProfile,
        route: SimpleTvRoute?,
        recordingActive: Boolean,
        stopRecording: suspend () -> Unit,
        redirectToLive: () -> Unit,
    ) {
        val generation = ++routeGuardGeneration
        val action = route?.let {
            simpleTvRouteGuardAction(profile, it, recordingActive)
        } ?: return
        when (action) {
            SimpleTvRouteGuardAction.ALLOW -> Unit
            SimpleTvRouteGuardAction.REDIRECT_TO_LIVE -> {
                if (generation == routeGuardGeneration) redirectToLive()
            }
            SimpleTvRouteGuardAction.STOP_RECORDING_AND_REDIRECT_TO_LIVE -> {
                stopRecording()
                if (generation == routeGuardGeneration) redirectToLive()
            }
        }
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

@Composable
internal fun SimpleTvRouteGuardEffect(
    route: SimpleTvRoute?,
    profile: ProductProfile,
    recordingActive: Boolean,
    orchestrator: AppRootPlaybackOrchestrator,
    stopRecording: suspend () -> Unit,
    redirectToLive: () -> Unit,
) {
    val routeAllowed = route?.let(profile::allowsRoute)
    val latestProfile by rememberUpdatedState(profile)
    val latestRecordingActive by rememberUpdatedState(recordingActive)
    val latestStopRecording by rememberUpdatedState(stopRecording)
    val latestRedirectToLive by rememberUpdatedState(redirectToLive)
    LaunchedEffect(route, routeAllowed) {
        orchestrator.enforceRouteGuard(
            profile = latestProfile,
            route = route,
            recordingActive = latestRecordingActive,
            stopRecording = latestStopRecording,
            redirectToLive = latestRedirectToLive,
        )
    }
}
