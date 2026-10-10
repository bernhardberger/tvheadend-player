package at.bernhardberger.tvhplayer.core

data class PlayerAutoHideContext(
    val controlsVisible: Boolean,
    val playbackProgressing: Boolean,
    val playbackStable: Boolean,
    val seekPending: Boolean,
    val modalVisible: Boolean,
    val recoveryVisible: Boolean,
    val actionableErrorVisible: Boolean,
)

fun playerPlaybackProgressing(
    isPlaying: Boolean,
    playerReady: Boolean,
): Boolean = isPlaying && playerReady

/** How long the live controls stay up while focus rests on the channel card or its recent row. */
const val PLAYER_CHANNEL_CARD_AUTO_HIDE_MS = 30_000L

/** The controls' auto-hide delay: [baseMillis], or longer while [channelCardFocused] (the card or its recent row). */
fun playerControlsAutoHideTimeoutMillis(baseMillis: Long, channelCardFocused: Boolean): Long =
    if (channelCardFocused) maxOf(baseMillis, PLAYER_CHANNEL_CARD_AUTO_HIDE_MS) else baseMillis

fun playerControlsAutoHideEligible(context: PlayerAutoHideContext): Boolean =
    context.controlsVisible &&
        context.playbackProgressing &&
        context.playbackStable &&
        !context.seekPending &&
        !context.modalVisible &&
        !context.recoveryVisible &&
        !context.actionableErrorVisible
