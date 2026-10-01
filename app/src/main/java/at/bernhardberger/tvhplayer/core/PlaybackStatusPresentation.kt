package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId

enum class PlaybackStatusPresentation {
    NONE,
    COMPACT_TUNING,
    CHANNEL_UNAVAILABLE,
    FULL_RECOVERY,
}

/**
 * [liveInterrupted]: the server stopped the stream of the live channel on screen and has not
 * started it again; no media arrives, whatever the player state, so it presents like a failure.
 */
fun playbackStatusPresentation(
    connectionAvailable: Boolean,
    playbackStarting: Boolean,
    playbackRecovering: Boolean,
    playbackPlaying: Boolean,
    playbackFailed: Boolean = false,
    liveInterrupted: Boolean = false,
): PlaybackStatusPresentation = when {
    !connectionAvailable || playbackRecovering ->
        PlaybackStatusPresentation.FULL_RECOVERY
    playbackFailed || liveInterrupted -> PlaybackStatusPresentation.CHANNEL_UNAVAILABLE
    playbackStarting -> PlaybackStatusPresentation.COMPACT_TUNING
    playbackPlaying -> PlaybackStatusPresentation.NONE
    else -> PlaybackStatusPresentation.NONE
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

/**
 * The unavailable card names an interruption only for a stop without an issue or failure of its
 * own: an issue keeps its own message, and a failure is not a stream that may start again.
 */
fun liveInterruptionMessageShown(
    liveInterrupted: Boolean,
    subscriptionIssuePresent: Boolean,
    playbackFailed: Boolean,
): Boolean = liveInterrupted && !subscriptionIssuePresent && !playbackFailed
