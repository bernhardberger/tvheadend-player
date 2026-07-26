package at.bernhardberger.tvhplayer.core

enum class BackAction {
    POP_NAVIGATION,
    RETURN_TO_PARENT,
    RETURN_TO_PLAYER,
    FINISH_ACTIVITY,
}

/** Warm playback the root destination may return to once. */
enum class WarmPlaybackTarget {
    NONE,
    LIVE,
    RECORDING,
}

/**
 * One-shot opportunity to return from the browse root to warm playback.
 *
 * Armed when playback actually starts or the user navigates deliberately while
 * playback remains warm. Consumed before navigating back to the player so a
 * subsequent root Back finishes the activity instead of looping.
 */
data class WarmReturnOpportunity(
    val armed: Boolean = false,
    val target: WarmPlaybackTarget = WarmPlaybackTarget.NONE,
) {
    val canReturn: Boolean
        get() = armed && target != WarmPlaybackTarget.NONE
}

fun rootBackAction(
    isStartDestination: Boolean,
    warmReturn: WarmReturnOpportunity,
): BackAction = when {
    !isStartDestination -> BackAction.POP_NAVIGATION
    warmReturn.canReturn -> BackAction.RETURN_TO_PLAYER
    else -> BackAction.FINISH_ACTIVITY
}

/** Consume the one-shot token before navigating to the warm player. */
fun consumeWarmReturn(current: WarmReturnOpportunity): WarmReturnOpportunity =
    current.copy(armed = false)

/** Clear when explicit Stop tears down playback. */
fun clearWarmReturn(): WarmReturnOpportunity = WarmReturnOpportunity()

/** Arm after playback actually starts (live or recording). */
fun armWarmReturn(target: WarmPlaybackTarget): WarmReturnOpportunity =
    if (target == WarmPlaybackTarget.NONE) {
        WarmReturnOpportunity()
    } else {
        WarmReturnOpportunity(armed = true, target = target)
    }

/**
 * Re-arm one warm return after deliberate browse navigation while playback
 * remains warm. Does nothing when there is no warm target.
 */
fun rearmWarmReturn(target: WarmPlaybackTarget): WarmReturnOpportunity =
    armWarmReturn(target)

/** Map active session IDs to a warm playback target. */
fun warmPlaybackTarget(
    activeServiceId: Int?,
    activeRecordingId: Int?,
): WarmPlaybackTarget = when {
    activeServiceId != null -> WarmPlaybackTarget.LIVE
    activeRecordingId != null -> WarmPlaybackTarget.RECORDING
    else -> WarmPlaybackTarget.NONE
}

fun nestedBackAction(hasPreviousEntry: Boolean): BackAction =
    if (hasPreviousEntry) BackAction.POP_NAVIGATION else BackAction.RETURN_TO_PARENT
