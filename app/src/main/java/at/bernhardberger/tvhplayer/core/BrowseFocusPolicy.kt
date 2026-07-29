package at.bernhardberger.tvhplayer.core

enum class GuideEntryFocusTarget {
    PROGRAMME,
    RETRY,
    HEADER,
}

enum class GuideScopeExitFocusTarget {
    PROGRAMME,
    RETRY,
    STAY_ON_SCOPE,
}

fun shouldRequestEmptyChannelsAction(
    initialFocusEnabled: Boolean,
    hasPrimaryAction: Boolean,
): Boolean = initialFocusEnabled && hasPrimaryAction

fun guideEntryFocusTarget(
    hasProgrammeTarget: Boolean,
    hasRetryAction: Boolean,
): GuideEntryFocusTarget = when {
    hasRetryAction -> GuideEntryFocusTarget.RETRY
    hasProgrammeTarget -> GuideEntryFocusTarget.PROGRAMME
    else -> GuideEntryFocusTarget.HEADER
}

fun guideScopeExitFocusTarget(
    hasProgrammeTarget: Boolean,
    hasRetryAction: Boolean,
): GuideScopeExitFocusTarget = when {
    hasRetryAction -> GuideScopeExitFocusTarget.RETRY
    hasProgrammeTarget -> GuideScopeExitFocusTarget.PROGRAMME
    else -> GuideScopeExitFocusTarget.STAY_ON_SCOPE
}
