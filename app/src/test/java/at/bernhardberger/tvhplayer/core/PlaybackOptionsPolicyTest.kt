package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackOptionsPolicyTest {
    @Test
    fun lateralCategoriesWrapAndHideOwnerPagesInSimpleTv() {
        assertEquals(
            listOf(PlaybackOptionsPage.AUDIO, PlaybackOptionsPage.SUBTITLES),
            playbackOptionsCategories(fullOptionsAvailable = false),
        )
        assertEquals(
            PlaybackOptionsPage.SUBTITLES,
            adjacentPlaybackOptionsPage(
                current = PlaybackOptionsPage.AUDIO,
                direction = 1,
                fullOptionsAvailable = false,
            ),
        )
        assertEquals(
            PlaybackOptionsPage.AUDIO,
            adjacentPlaybackOptionsPage(
                current = PlaybackOptionsPage.SUBTITLES,
                direction = 1,
                fullOptionsAvailable = false,
            ),
        )
        assertEquals(
            PlaybackOptionsPage.STATS,
            adjacentPlaybackOptionsPage(
                current = PlaybackOptionsPage.DISPLAY,
                direction = 1,
                fullOptionsAvailable = true,
            ),
        )
        assertEquals(
            PlaybackOptionsPage.AUDIO,
            adjacentPlaybackOptionsPage(
                current = PlaybackOptionsPage.STATS,
                direction = 1,
                fullOptionsAvailable = true,
            ),
        )
    }

    @Test
    fun emptyTracksAreLoadingOnlyWhilePlaybackIsResolving() {
        assertEquals(
            PlaybackTrackContentState.LOADING,
            playbackTrackContentState(trackCount = 0, tracksResolving = true),
        )
        assertEquals(
            PlaybackTrackContentState.EMPTY,
            playbackTrackContentState(trackCount = 0, tracksResolving = false),
        )
        assertEquals(
            PlaybackTrackContentState.AVAILABLE,
            playbackTrackContentState(trackCount = 1, tracksResolving = true),
        )
    }

    @Test
    fun audioFocusUsesSelectionThenFallsBackToFirstTrackOrHeaderBack() {
        assertEquals(
            PlaybackTrackFocusTarget.track(key = "audio-de", lazyIndex = 1),
            playbackTrackFocusTarget(
                trackKeys = listOf("audio-en", "audio-de", "audio-fr"),
                selectedTrackKey = "audio-de",
                subtitles = false,
            ),
        )
        assertEquals(
            PlaybackTrackFocusTarget.track(key = "audio-en", lazyIndex = 0),
            playbackTrackFocusTarget(
                trackKeys = listOf("audio-en", "audio-fr"),
                selectedTrackKey = "audio-de",
                subtitles = false,
            ),
        )
        assertEquals(
            PlaybackTrackFocusTarget.HeaderBack,
            playbackTrackFocusTarget(
                trackKeys = emptyList(),
                selectedTrackKey = null,
                subtitles = false,
            ),
        )
    }

    @Test
    fun subtitleFocusUsesSelectionThenFallsBackToOff() {
        assertEquals(
            PlaybackTrackFocusTarget.track(key = "sub-de", lazyIndex = 2),
            playbackTrackFocusTarget(
                trackKeys = listOf("sub-en", "sub-de"),
                selectedTrackKey = "sub-de",
                subtitles = true,
            ),
        )
        assertEquals(
            PlaybackTrackFocusTarget.SubtitlesOff,
            playbackTrackFocusTarget(
                trackKeys = listOf("sub-en"),
                selectedTrackKey = "sub-de",
                subtitles = true,
            ),
        )
        assertEquals(
            PlaybackTrackFocusTarget.SubtitlesOff,
            playbackTrackFocusTarget(
                trackKeys = emptyList(),
                selectedTrackKey = null,
                subtitles = true,
            ),
        )
    }
}
