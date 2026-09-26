package at.bernhardberger.tvhplayer.core

enum class PlayerStatusKind { LIVE, BEHIND_LIVE, PAUSED, TUNING, BUFFERING, RECORDING, GROWING_RECORDING, PROBLEM }
enum class PlayerStatusIndicator { NONE, PLAY, PAUSE, SPINNER, RECORDING_DOT, ERROR }

data class PlayerStatus(
    val kind: PlayerStatusKind,
    val indicator: PlayerStatusIndicator,
    val behindLiveSeconds: Long? = null,
    val problem: String? = null,
)

/** Delayed tuning/buffering presentation is supplied by the existing player policy, not timed here. */
fun playerStatus(
    live: Boolean = true,
    paused: Boolean = false,
    behindLiveSeconds: Long? = null,
    tuning: Boolean = false,
    buffering: Boolean = false,
    growingRecording: Boolean = false,
    problem: String? = null,
    timingKnown: Boolean = true,
): PlayerStatus? {
    val behind = behindLiveSeconds?.coerceAtLeast(0)
    return when {
        !problem.isNullOrBlank() -> PlayerStatus(PlayerStatusKind.PROBLEM, PlayerStatusIndicator.ERROR, problem = problem)
        tuning -> PlayerStatus(PlayerStatusKind.TUNING, PlayerStatusIndicator.SPINNER)
        buffering -> PlayerStatus(PlayerStatusKind.BUFFERING, PlayerStatusIndicator.SPINNER)
        live && !timingKnown -> null
        paused -> PlayerStatus(PlayerStatusKind.PAUSED, PlayerStatusIndicator.PAUSE, behind?.takeIf { live && it > 0 })
        growingRecording -> PlayerStatus(PlayerStatusKind.GROWING_RECORDING, PlayerStatusIndicator.RECORDING_DOT)
        !live -> null
        behind != null && behind > TIMESHIFT_LIVE_EDGE_TOLERANCE_MS / 1000 -> PlayerStatus(PlayerStatusKind.BEHIND_LIVE, PlayerStatusIndicator.PLAY, behind)
        else -> PlayerStatus(PlayerStatusKind.LIVE, PlayerStatusIndicator.NONE)
    }
}

/** Independent of playback status: recording a channel does not replace Live/Paused/Tuning. */
fun recordingNowStatus(recordingNow: Boolean): PlayerStatus? =
    if (recordingNow) PlayerStatus(PlayerStatusKind.RECORDING, PlayerStatusIndicator.RECORDING_DOT) else null
