package at.bernhardberger.tvhplayer.player

import at.bernhardberger.tvhplayer.core.RecordingPlaybackIntent
import at.bernhardberger.tvhplayer.core.RecordingStartDecision
import at.bernhardberger.tvhplayer.core.recordingStartDecision
import at.bernhardberger.tvhplayer.htsp.DvrState

internal sealed interface RecordingPreparationDecision {
    data object Wait : RecordingPreparationDecision
    data object Cancel : RecordingPreparationDecision
    data class Start(val decision: RecordingStartDecision) : RecordingPreparationDecision
}

internal class RecordingStartCoordinator(
    private val generation: Long,
    private val intent: RecordingPlaybackIntent,
    private val state: DvrState,
    private val serverPositionSeconds: Long?,
    private val playCount: Int?,
) {
    private var decided = false

    fun requiresDuration(): Boolean =
        state == DvrState.COMPLETED && intent != RecordingPlaybackIntent.FromBeginning

    fun decide(
        currentGeneration: Long,
        durationMs: Long?,
        waitExpired: Boolean,
        preparationFailed: Boolean = false,
    ): RecordingPreparationDecision {
        if (decided || currentGeneration != generation || preparationFailed) {
            return RecordingPreparationDecision.Cancel
        }
        if (durationMs == null && !waitExpired && intent != RecordingPlaybackIntent.FromBeginning) {
            return RecordingPreparationDecision.Wait
        }
        decided = true
        return RecordingPreparationDecision.Start(
            recordingStartDecision(
                intent = intent,
                state = state,
                serverPositionSeconds = serverPositionSeconds,
                durationMs = durationMs,
                playCount = playCount,
            )
        )
    }
}

internal fun recordingIntentForResumeSupport(
    intent: RecordingPlaybackIntent,
    resumeSupported: Boolean,
): RecordingPlaybackIntent = if (!resumeSupported) {
    RecordingPlaybackIntent.FromBeginning
} else {
    intent
}
