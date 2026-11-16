package at.bernhardberger.tvhplayer.core

/**
 * Playback options root and detail pages shown in the compact player overlay.
 */
enum class PlaybackOptionsPage {
    ROOT,
    AUDIO,
    SUBTITLES,
    DISPLAY,

    /** A recording's own ad-break handling; the live player has no such page. */
    AD_BREAKS,
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
    data object SubtitlesDefault : PlaybackTrackFocusTarget
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
    subtitleDefaultOffered: Boolean = false,
    subtitlesDefault: Boolean = false,
): PlaybackTrackFocusTarget {
    if (subtitles && subtitleDefaultOffered && subtitlesDefault) return PlaybackTrackFocusTarget.SubtitlesDefault
    val selectedIndex = selectedTrackKey?.let(trackKeys::indexOf)?.takeIf { it >= 0 }
    if (selectedIndex != null) {
        return PlaybackTrackFocusTarget.Track(
            key = trackKeys[selectedIndex],
            lazyIndex = selectedIndex + if (subtitles) (if (subtitleDefaultOffered) 2 else 1) else 0,
        )
    }
    if (subtitles) return PlaybackTrackFocusTarget.SubtitlesOff
    return trackKeys.firstOrNull()?.let { firstKey ->
        PlaybackTrackFocusTarget.Track(key = firstKey, lazyIndex = 0)
    } ?: PlaybackTrackFocusTarget.HeaderBack
}

/**
 * Whether the action row offers [page]'s list, which it does only for Audio and only with a
 * choice to make: another audio track.
 */
fun playbackQuickListOffered(page: PlaybackOptionsPage, trackCount: Int): Boolean = when (page) {
    PlaybackOptionsPage.AUDIO -> trackCount > 1
    else -> false
}

/** The root's pages in row order; [adBreaks] adds the recording player's ad-break page. */
fun playbackOptionsCategories(adBreaks: Boolean = false): List<PlaybackOptionsPage> = buildList {
    add(PlaybackOptionsPage.AUDIO)
    add(PlaybackOptionsPage.SUBTITLES)
    add(PlaybackOptionsPage.DISPLAY)
    if (adBreaks) add(PlaybackOptionsPage.AD_BREAKS)
    add(PlaybackOptionsPage.STATS)
}
