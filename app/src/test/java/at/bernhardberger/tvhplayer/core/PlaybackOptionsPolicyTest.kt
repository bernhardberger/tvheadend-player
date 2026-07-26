package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackOptionsPolicyTest {
    @Test
    fun backReturnsNestedOptionsToTheRootBeforeClosingTheSheet() {
        assertEquals(
            PlaybackAuxiliaryBackAction.SHOW_OPTIONS_ROOT,
            playbackAuxiliaryBackAction(
                optionsPage = PlaybackOptionsPage.AUDIO,
                statsVisible = true,
            ),
        )
        assertEquals(
            PlaybackAuxiliaryBackAction.CLOSE_OPTIONS,
            playbackAuxiliaryBackAction(
                optionsPage = PlaybackOptionsPage.ROOT,
                statsVisible = true,
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
}
