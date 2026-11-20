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

fun playerControlsAutoHideEligible(context: PlayerAutoHideContext): Boolean =
    context.controlsVisible &&
        context.playbackProgressing &&
        context.playbackStable &&
        !context.seekPending &&
        !context.modalVisible &&
        !context.recoveryVisible &&
        !context.actionableErrorVisible
