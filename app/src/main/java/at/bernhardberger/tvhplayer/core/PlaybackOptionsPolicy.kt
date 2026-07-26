package at.bernhardberger.tvhplayer.core

/**
 * Playback options root and detail pages shown in the compact player overlay.
 */
enum class PlaybackOptionsPage {
    ROOT,
    AUDIO,
    SUBTITLES,
    DISPLAY,
    STATS,
}

enum class PlaybackAuxiliaryBackAction {
    CLOSE_INFO,
    RETURN_TO_OPTIONS_ROOT,
    CLOSE_OPTIONS,
    HIDE_STATS,
    PASS_THROUGH,
}

fun playbackOptionsCategories(simpleTvActive: Boolean): List<PlaybackOptionsPage> =
    if (simpleTvActive) {
        listOf(PlaybackOptionsPage.AUDIO, PlaybackOptionsPage.SUBTITLES)
    } else {
        listOf(
            PlaybackOptionsPage.AUDIO,
            PlaybackOptionsPage.SUBTITLES,
            PlaybackOptionsPage.DISPLAY,
            PlaybackOptionsPage.STATS,
        )
    }

fun adjacentPlaybackOptionsPage(
    current: PlaybackOptionsPage,
    direction: Int,
    simpleTvActive: Boolean,
): PlaybackOptionsPage {
    val pages = playbackOptionsCategories(simpleTvActive)
    val index = pages.indexOf(current).takeIf { it >= 0 } ?: 0
    val next = (index + direction).floorMod(pages.size)
    return pages[next]
}

fun playbackAuxiliaryBackAction(
    optionsPage: PlaybackOptionsPage?,
    statsVisible: Boolean,
    infoOpen: Boolean = false,
): PlaybackAuxiliaryBackAction = when {
    infoOpen -> PlaybackAuxiliaryBackAction.CLOSE_INFO
    optionsPage != null && optionsPage != PlaybackOptionsPage.ROOT ->
        PlaybackAuxiliaryBackAction.RETURN_TO_OPTIONS_ROOT
    optionsPage == PlaybackOptionsPage.ROOT -> PlaybackAuxiliaryBackAction.CLOSE_OPTIONS
    statsVisible -> PlaybackAuxiliaryBackAction.HIDE_STATS
    else -> PlaybackAuxiliaryBackAction.PASS_THROUGH
}

private fun Int.floorMod(modulus: Int): Int {
    val r = this % modulus
    return if (r < 0) r + modulus else r
}
