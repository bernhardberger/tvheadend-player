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

enum class PlaybackTrackContentState {
    LOADING,
    AVAILABLE,
    EMPTY,
}

sealed interface PlaybackTrackFocusTarget {
    data object HeaderBack : PlaybackTrackFocusTarget
    data object SubtitlesOff : PlaybackTrackFocusTarget
    data class Track(
        val key: String,
        val lazyIndex: Int,
    ) : PlaybackTrackFocusTarget

    companion object {
        fun track(key: String, lazyIndex: Int): PlaybackTrackFocusTarget =
            Track(key = key, lazyIndex = lazyIndex)
    }
}

fun playbackTrackContentState(
    trackCount: Int,
    tracksResolving: Boolean,
): PlaybackTrackContentState {
    require(trackCount >= 0)
    return when {
        trackCount > 0 -> PlaybackTrackContentState.AVAILABLE
        tracksResolving -> PlaybackTrackContentState.LOADING
        else -> PlaybackTrackContentState.EMPTY
    }
}

fun playbackTrackFocusTarget(
    trackKeys: List<String>,
    selectedTrackKey: String?,
    subtitles: Boolean,
): PlaybackTrackFocusTarget {
    val selectedIndex = selectedTrackKey?.let(trackKeys::indexOf)?.takeIf { it >= 0 }
    if (selectedIndex != null) {
        return PlaybackTrackFocusTarget.Track(
            key = trackKeys[selectedIndex],
            lazyIndex = selectedIndex + if (subtitles) 1 else 0,
        )
    }
    if (subtitles) return PlaybackTrackFocusTarget.SubtitlesOff
    return trackKeys.firstOrNull()?.let { firstKey ->
        PlaybackTrackFocusTarget.Track(key = firstKey, lazyIndex = 0)
    } ?: PlaybackTrackFocusTarget.HeaderBack
}

fun playbackOptionsCategories(): List<PlaybackOptionsPage> =
        listOf(
            PlaybackOptionsPage.AUDIO,
            PlaybackOptionsPage.SUBTITLES,
            PlaybackOptionsPage.DISPLAY,
            PlaybackOptionsPage.STATS,
        )

fun adjacentPlaybackOptionsPage(
    current: PlaybackOptionsPage,
    direction: Int,
): PlaybackOptionsPage {
    val pages = playbackOptionsCategories()
    val index = pages.indexOf(current).takeIf { it >= 0 } ?: 0
    val next = (index + direction).floorMod(pages.size)
    return pages[next]
}

private fun Int.floorMod(modulus: Int): Int {
    val r = this % modulus
    return if (r < 0) r + modulus else r
}
