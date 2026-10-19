package at.bernhardberger.tvhplayer.core

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import kotlin.time.Instant

enum class PlaybackStatusPresentation {
    NONE,
    COMPACT_TUNING,
    CHANNEL_UNAVAILABLE,
    FULL_RECOVERY,
}

/**
 * [channelCondition] is the [liveChannelCondition] of the channel on screen: a channel message
 * presents as the unavailable box, any other condition as the full-screen alert.
 */
fun playbackStatusPresentation(
    connectionAvailable: Boolean,
    playbackStarting: Boolean,
    playbackRecovering: Boolean,
    playbackPlaying: Boolean,
    channelCondition: AppErrorCondition? = null,
): PlaybackStatusPresentation = when {
    !connectionAvailable || playbackRecovering ->
        PlaybackStatusPresentation.FULL_RECOVERY
    channelCondition != null -> if (channelCondition.kind == AppErrorKind.CHANNEL) {
        PlaybackStatusPresentation.CHANNEL_UNAVAILABLE
    } else {
        PlaybackStatusPresentation.FULL_RECOVERY
    }
    playbackStarting -> PlaybackStatusPresentation.COMPACT_TUNING
    playbackPlaying -> PlaybackStatusPresentation.NONE
    else -> PlaybackStatusPresentation.NONE
}

/**
 * The condition of the live channel on screen, or null while nothing went wrong: the [issue] the
 * server reports, else 207 for a stream the server stopped ([liveInterrupted]), else 210 when this
 * device's player failed ([deviceFault]), else 207 for any other failed playback.
 */
@OptIn(UnstableApi::class)
fun liveChannelCondition(
    issue: SubscriptionIssue?,
    playbackFailed: Boolean,
    liveInterrupted: Boolean,
    deviceFault: Boolean = false,
): AppErrorCondition? = when {
    issue == null && deviceFault && !liveInterrupted -> AppErrorCondition.CHANNEL_PLAYBACK_FAILED
    issue == null && !playbackFailed && !liveInterrupted -> null
    else -> liveErrorCondition(issue)
}

/**
 * What the player's full-screen alert shows. For live it exists exactly when
 * [playbackStatusPresentation] is FULL_RECOVERY; the recording player uses [recordingPlayerAlert].
 */
sealed interface LivePlayerAlert {
    /**
     * A numbered condition with the catalog's buttons; its Retry runs [retry]. [startsAt] is the
     * start of a scheduled recording that has not begun (302).
     */
    data class Error(
        val error: AppError,
        val retry: PlaybackRetryCommand,
        val startsAt: Instant? = null,
    ) : LivePlayerAlert

    /**
     * Live playback retries by itself: passive text and Close only. [retryDelayMillis] is the wait
     * before the next attempt as the runtime published it, 0 when it retunes at once.
     */
    data class Recovering(val retryDelayMillis: Long) : LivePlayerAlert
}

/**
 * A lost connection comes first, then the player's own recovery, then a [channelCondition] that is
 * not a channel message. A connection that is not a failure itself reads as reconnecting.
 */
fun livePlayerAlert(
    connection: ConnectionState,
    playbackState: AppPlaybackState,
    channelCondition: AppErrorCondition?,
): LivePlayerAlert? = when {
    connection !is ConnectionState.Connected -> LivePlayerAlert.Error(
        error = connection.playerConnectionError(),
        retry = PlaybackRetryCommand.RECONNECT,
    )
    playbackState is AppPlaybackState.Recovering -> LivePlayerAlert.Recovering(playbackState.retryDelayMillis)
    channelCondition != null && channelCondition.kind != AppErrorKind.CHANNEL -> LivePlayerAlert.Error(
        error = channelCondition.toAppError(),
        retry = PlaybackRetryCommand.RETRY_LIVE,
    )
    else -> null
}

/**
 * The recording player's full-screen alert, in the same shape as the live one: the player's own
 * recovery first, then the connection while the recording is still unresolved ([connectionFailed]),
 * then [recordingAppError]. Retry runs [retry]; when there is nothing to retry
 * ([PlaybackRetryCommand.NONE]) the alert offers no Retry.
 */
@OptIn(UnstableApi::class)
fun recordingPlayerAlert(
    connection: ConnectionState,
    connectionFailed: Boolean,
    playbackState: AppPlaybackState,
    entry: DvrEntry?,
    admission: RecordingPlaybackAdmission?,
    retry: PlaybackRetryCommand,
): LivePlayerAlert? {
    if (playbackState is AppPlaybackState.Recovering) return LivePlayerAlert.Recovering(playbackState.retryDelayMillis)
    val error = if (connectionFailed) {
        connection.playerConnectionError()
    } else {
        recordingAppError(entry, admission, playbackState as? AppPlaybackState.Failed)
    } ?: return null
    val actions = if (retry == PlaybackRetryCommand.NONE) error.actions - AppErrorAction.RETRY else error.actions
    val startsAt = entry?.start.takeIf { error.condition == AppErrorCondition.RECORDING_NOT_STARTED }
    return LivePlayerAlert.Error(error.copy(actions = actions), retry, startsAt)
}

private fun ConnectionState.playerConnectionError(): AppError {
    val failure = this as? ConnectionState.Error
    return failure?.let { ConnectionUiState.Error(it.kind, it.recoveryDisposition).toAppError(closable = true) }
        ?: AppErrorCondition.RECONNECTING.toAppError(closable = true)
}

/**
 * The failed-request flag a live retry's [result] leaves, or null to keep the flag as it is.
 * Only a retry made for the request that is still current ([requestCurrent]) and that retunes
 * that request's channel ([retunesRequest]) decides: a retune that started ends the failure,
 * even one an earlier overlapping retry gave back; one that did not start ([result] is null
 * when there was no channel to retune) gives back the failure this retry [cleared].
 */
fun liveRetryRequestFailure(
    retunesRequest: Boolean,
    cleared: Boolean,
    result: PlaybackTargetResult?,
    requestCurrent: Boolean,
): Boolean? = when {
    !requestCurrent || !retunesRequest -> null
    result?.isStarted == true -> false
    cleared -> true
    else -> null
}

/**
 * The server stopped the stream of the live channel on screen and has not started it again.
 * [serverStopped] is the current live observation's flag: a retired target has none.
 */
fun liveInterrupted(
    serverStopped: Boolean,
    playingLiveChannelId: ChannelId?,
    shownChannelId: ChannelId,
): Boolean = serverStopped && playingLiveChannelId == shownChannelId
