package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackOptionsPolicyTest {
    @Test
    fun backClosesOpenOptionsPopoverBeforeStatsOrPlayer() {
        assertEquals(
            PlaybackAuxiliaryBackAction.CLOSE_OPTIONS,
            playbackAuxiliaryBackAction(
                optionsPage = PlaybackOptionsPage.AUDIO,
                statsVisible = true,
            ),
        )
        assertEquals(
            PlaybackAuxiliaryBackAction.CLOSE_OPTIONS,
            playbackAuxiliaryBackAction(
                optionsPage = PlaybackOptionsPage.DISPLAY,
                statsVisible = false,
            ),
        )
    }

    @Test
    fun backHidesStatsBeforeNormalPlayerNavigation() {
        assertEquals(
            PlaybackAuxiliaryBackAction.HIDE_STATS,
            playbackAuxiliaryBackAction(
                optionsPage = null,
                statsVisible = true,
            ),
        )
        assertEquals(
            PlaybackAuxiliaryBackAction.PASS_THROUGH,
            playbackAuxiliaryBackAction(
                optionsPage = null,
                statsVisible = false,
            ),
        )
    }

    @Test
    fun lateralCategoriesWrapAndHideOwnerPagesInSimpleTv() {
        assertEquals(
            listOf(PlaybackOptionsPage.AUDIO, PlaybackOptionsPage.SUBTITLES),
            playbackOptionsCategories(simpleTvActive = true),
        )
        assertEquals(
            PlaybackOptionsPage.SUBTITLES,
            adjacentPlaybackOptionsPage(
                current = PlaybackOptionsPage.AUDIO,
                direction = 1,
                simpleTvActive = true,
            ),
        )
        assertEquals(
            PlaybackOptionsPage.AUDIO,
            adjacentPlaybackOptionsPage(
                current = PlaybackOptionsPage.SUBTITLES,
                direction = 1,
                simpleTvActive = true,
            ),
        )
        assertEquals(
            PlaybackOptionsPage.STATS,
            adjacentPlaybackOptionsPage(
                current = PlaybackOptionsPage.DISPLAY,
                direction = 1,
                simpleTvActive = false,
            ),
        )
        assertEquals(
            PlaybackOptionsPage.AUDIO,
            adjacentPlaybackOptionsPage(
                current = PlaybackOptionsPage.STATS,
                direction = 1,
                simpleTvActive = false,
            ),
        )
    }
}
