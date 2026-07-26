package at.bernhardberger.tvhplayer.core

enum class PlaybackOptionsPage {
    ROOT,
    AUDIO,
    SUBTITLES,
    DISPLAY,
}

enum class PlaybackAuxiliaryBackAction {
    SHOW_OPTIONS_ROOT,
    CLOSE_OPTIONS,
    HIDE_STATS,
    PASS_THROUGH,
}

fun playbackAuxiliaryBackAction(
    optionsPage: PlaybackOptionsPage?,
    statsVisible: Boolean,
): PlaybackAuxiliaryBackAction = when {
    optionsPage != null && optionsPage != PlaybackOptionsPage.ROOT ->
        PlaybackAuxiliaryBackAction.SHOW_OPTIONS_ROOT

    optionsPage == PlaybackOptionsPage.ROOT -> PlaybackAuxiliaryBackAction.CLOSE_OPTIONS
    statsVisible -> PlaybackAuxiliaryBackAction.HIDE_STATS
    else -> PlaybackAuxiliaryBackAction.PASS_THROUGH
}
