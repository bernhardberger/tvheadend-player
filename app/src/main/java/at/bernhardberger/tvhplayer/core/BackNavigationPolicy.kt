package at.bernhardberger.tvhplayer.core

enum class BackAction {
    POP_NAVIGATION,
    RETURN_TO_PARENT,
    RETURN_TO_PLAYER,
    FINISH_ACTIVITY,
}

fun rootBackAction(isStartDestination: Boolean, hasActivePlayback: Boolean): BackAction = when {
    !isStartDestination -> BackAction.POP_NAVIGATION
    hasActivePlayback -> BackAction.RETURN_TO_PLAYER
    else -> BackAction.FINISH_ACTIVITY
}

fun nestedBackAction(hasPreviousEntry: Boolean): BackAction =
    if (hasPreviousEntry) BackAction.POP_NAVIGATION else BackAction.RETURN_TO_PARENT
