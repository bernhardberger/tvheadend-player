package at.bernhardberger.tvhplayer.core

enum class RecordingPlaybackKeyAction {
    PASS_THROUGH,
    REVEAL_CONTROLS,
    REVEAL_AND_TOGGLE_PAUSE,
    HIDE_CONTROLS,
    CLOSE,
    OPEN_INFO,
    SEEK_BACK,
    SEEK_FORWARD,
}

/**
 * Recording player keys follow the shared player contract: vertical D-pad
 * reveals controls and Left/Right seek. The old hidden Up/Down ten-minute seek
 * mapping is intentionally retired; programme info remains an explicit action.
 */
fun recordingPlaybackKeyAction(
    controlsVisible: Boolean,
    keyCode: Int,
    playerCloseAllowed: Boolean = true,
    seekbarFocused: Boolean = false,
): RecordingPlaybackKeyAction {
    val action = playerKeyAction(
        PlayerKeyContext(
            surface = PlayerSurface.RECORDING,
            controlsVisible = controlsVisible,
            seekbarFocused = seekbarFocused,
            timeshiftAvailable = false,
            playerCloseAllowed = playerCloseAllowed,
        ),
        keyCode,
    )
    return when (action) {
        PlayerKeyAction.PASS_THROUGH -> RecordingPlaybackKeyAction.PASS_THROUGH
        PlayerKeyAction.REVEAL_CONTROLS -> RecordingPlaybackKeyAction.REVEAL_CONTROLS
        PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE ->
            RecordingPlaybackKeyAction.REVEAL_AND_TOGGLE_PAUSE
        PlayerKeyAction.HIDE_CONTROLS -> RecordingPlaybackKeyAction.HIDE_CONTROLS
        PlayerKeyAction.CLOSE_PLAYER,
        PlayerKeyAction.DISMISS_OVERLAY_ONLY -> RecordingPlaybackKeyAction.CLOSE
        PlayerKeyAction.OPEN_INFO -> RecordingPlaybackKeyAction.OPEN_INFO
        PlayerKeyAction.SEEK_BACK -> RecordingPlaybackKeyAction.SEEK_BACK
        PlayerKeyAction.SEEK_FORWARD -> RecordingPlaybackKeyAction.SEEK_FORWARD
        PlayerKeyAction.OPEN_CHANNELS -> RecordingPlaybackKeyAction.PASS_THROUGH
    }
}

fun recordingPlaybackSuppressesRevealingKey(
    revealingKeyCode: Int?,
    keyCode: Int,
): Boolean = revealingKeyCode == keyCode

fun recordingKeyActionStartsOpeningCycle(action: RecordingPlaybackKeyAction): Boolean =
    when (action) {
        RecordingPlaybackKeyAction.REVEAL_CONTROLS,
        RecordingPlaybackKeyAction.REVEAL_AND_TOGGLE_PAUSE,
        RecordingPlaybackKeyAction.OPEN_INFO -> true
        RecordingPlaybackKeyAction.PASS_THROUGH,
        RecordingPlaybackKeyAction.HIDE_CONTROLS,
        RecordingPlaybackKeyAction.CLOSE,
        RecordingPlaybackKeyAction.SEEK_BACK,
        RecordingPlaybackKeyAction.SEEK_FORWARD -> false
    }
