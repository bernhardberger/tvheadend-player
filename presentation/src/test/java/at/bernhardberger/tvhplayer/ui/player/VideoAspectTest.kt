package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoAspectTest {
    private val wide = 16f / 9f
    private val standard = 4f / 3f

    @Test fun anSdBroadcastPictureSnapsToItsNominalShape() {
        assertEquals(wide, broadcastNominalAspect(720, 576, 16f / 11f)!!, 0f)
        assertEquals(wide, broadcastNominalAspect(704, 576, 16f / 11f)!!, 0f)
        assertEquals(standard, broadcastNominalAspect(720, 576, 12f / 11f)!!, 0f)
        assertEquals(standard, broadcastNominalAspect(768, 576, 1f)!!, 0f)
    }

    @Test fun hdCinemaAndUnknownPicturesKeepTheirOwnShape() {
        assertNull(broadcastNominalAspect(1920, 1080, 1f))
        assertNull(broadcastNominalAspect(1440, 1080, 4f / 3f))
        assertNull("2.35:1", broadcastNominalAspect(720, 306, 1f))
        assertNull("5:4", broadcastNominalAspect(720, 576, 1f))
        assertNull(broadcastNominalAspect(0, 0, 1f))
        assertNull(broadcastNominalAspect(720, 0, 1f))
    }

    @Test fun theToleranceIsThreePercent() {
        // 1.75 is 1.6 % under 16:9; 1.70 is 4.4 % under.
        assertEquals(wide, broadcastNominalAspect(700, 400, 1f)!!, 0f)
        assertNull(broadcastNominalAspect(680, 400, 1f))
    }

    @Test fun anUnreportedPixelRatioCountsAsSquare() {
        assertEquals(standard, broadcastNominalAspect(768, 576, 0f)!!, 0f)
    }

    @Test fun onlyAutoSnapsAndTheForcedModesAlwaysWin() {
        assertEquals(wide, surfaceFrameAspect(AspectRatioMode.FIT, 720, 576, 16f / 11f)!!, 0f)
        assertNull(surfaceFrameAspect(AspectRatioMode.FIT, 1920, 1080, 1f))
        assertEquals(wide, surfaceFrameAspect(AspectRatioMode.FORCE_16_9, 720, 576, 12f / 11f)!!, 0f)
        assertEquals(standard, surfaceFrameAspect(AspectRatioMode.FORCE_4_3, 1920, 1080, 1f)!!, 0f)
        assertEquals(wide, surfaceFrameAspect(AspectRatioMode.FORCE_16_9, 0, 0, 1f)!!, 0f)
    }

    @Test fun aTargetsPictureUsesItsChannelsMode() {
        val recordings = mapOf(DvrEntryId(1) to ChannelId(7), DvrEntryId(2) to null)
        fun channel(target: AppPlaybackTarget?) = aspectChannel(target) { recordings[it] }
        assertEquals(ChannelId(3), channel(AppPlaybackTarget.Live(ChannelId(3))))
        assertEquals(ChannelId(7), channel(AppPlaybackTarget.Recording(DvrEntryId(1))))
        assertNull("a recording without a channel", channel(AppPlaybackTarget.Recording(DvrEntryId(2))))
        assertNull("an unknown recording", channel(AppPlaybackTarget.Recording(DvrEntryId(99))))
        assertNull(channel(null))
    }
}
