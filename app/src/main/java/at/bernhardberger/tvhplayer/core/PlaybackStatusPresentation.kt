package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId

enum class PlaybackStatusPresentation {
    NONE,
    COMPACT_TUNING,
    CHANNEL_UNAVAILABLE,
    FULL_RECOVERY,
}

const val COMPACT_TUNING_DELAY_MS = 500L
const val COMPACT_TUNING_FADE_IN_MS = 150L
const val COMPACT_TUNING_MINIMUM_OPAQUE_MS = 600L

enum class CompactTuningVisibilityAction {
    KEEP_HIDDEN,
    SHOW_AFTER_DELAY,
    KEEP_VISIBLE,
    HIDE_AFTER_MINIMUM,
    HIDE_IMMEDIATELY,
}

fun compactTuningVisibilityAction(
    screenActive: Boolean,
    presentation: PlaybackStatusPresentation,
    currentlyVisible: Boolean,
): CompactTuningVisibilityAction = when {
    !screenActive || presentation == PlaybackStatusPresentation.FULL_RECOVERY ||
        presentation == PlaybackStatusPresentation.CHANNEL_UNAVAILABLE ->
        CompactTuningVisibilityAction.HIDE_IMMEDIATELY
    presentation == PlaybackStatusPresentation.COMPACT_TUNING && currentlyVisible ->
        CompactTuningVisibilityAction.KEEP_VISIBLE
    presentation == PlaybackStatusPresentation.COMPACT_TUNING ->
        CompactTuningVisibilityAction.SHOW_AFTER_DELAY
    currentlyVisible -> CompactTuningVisibilityAction.HIDE_AFTER_MINIMUM
    else -> CompactTuningVisibilityAction.KEEP_HIDDEN
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
