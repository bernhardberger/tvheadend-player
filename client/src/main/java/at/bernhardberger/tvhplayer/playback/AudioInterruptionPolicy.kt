package at.bernhardberger.tvhplayer.playback

enum class AudioInterruption { TRANSIENT_LOSS, TRANSIENT_LOSS_CAN_DUCK, PERMANENT_LOSS, NOISY, GAIN }

internal enum class AudioInterruptionContent { LIVE_TIMESHIFT, LIVE, RECORDING, NONE }
internal enum class AudioInterruptionAction { NONE, PAUSE, MUTE, RESUME, UNMUTE }

/** Resume ownership belongs to the runtime's target epoch, not to a subsequent GAIN alone. */
internal fun audioInterruptionAction(
    content: AudioInterruptionContent,
    interruption: AudioInterruption,
    wasPlaying: Boolean,
    resumeOwned: Boolean,
): AudioInterruptionAction = when {
    content == AudioInterruptionContent.NONE -> AudioInterruptionAction.NONE
    interruption == AudioInterruption.TRANSIENT_LOSS_CAN_DUCK -> AudioInterruptionAction.NONE
    interruption == AudioInterruption.GAIN -> when {
        !resumeOwned -> AudioInterruptionAction.NONE
        content == AudioInterruptionContent.LIVE -> AudioInterruptionAction.UNMUTE
        else -> AudioInterruptionAction.RESUME
    }
    !wasPlaying -> AudioInterruptionAction.NONE
    content == AudioInterruptionContent.LIVE -> AudioInterruptionAction.MUTE
    else -> AudioInterruptionAction.PAUSE
}
