package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A target that has data but makes no progress is a stall; network buffering and a held target never are. */
class PlaybackStallWatchTest {
    private val watch = PlaybackStallWatch()
    private val counters = Any()

    @Test fun bufferingWithPlentyQueuedIsAStallAfterItsWindow() {
        assertNull(watch.feed(STALL_BUFFERING_MILLIS - STALL_POLL_MILLIS) { buffering(STALL_BUFFERED_AHEAD_MILLIS) })
        assertEquals(PlaybackStall.BUFFERING, watch.feed(STALL_POLL_MILLIS, from = STALL_BUFFERING_MILLIS) {
            buffering(STALL_BUFFERED_AHEAD_MILLIS)
        })
    }

    @Test fun theEmulatorsStuckZapIsAStallWithinTwelveSeconds() {
        // Same-format live zap on a reused decoder: position frozen, the queue grows from nothing
        // to about 9 s by +12 s while Media3 keeps buffering.
        assertEquals(PlaybackStall.BUFFERING, watch.feed(12_000) { now -> buffering(aheadMillis = now * 9_120 / 12_000) })
    }

    @Test fun bufferingWithLittleQueuedIsNetworkBufferingAndNeverAStall() {
        assertNull(watch.feed(60_000) { buffering(STALL_BUFFERED_AHEAD_MILLIS - 1) })
    }

    @Test fun theQueueDrainingBelowTheThresholdStartsTheWindowOver() {
        assertNull(watch.feed(STALL_BUFFERING_MILLIS - STALL_POLL_MILLIS) { buffering(10_000) })
        assertNull(watch.onSample(buffering(1_000), STALL_BUFFERING_MILLIS))
        assertNull(watch.feed(STALL_BUFFERING_MILLIS - STALL_POLL_MILLIS, from = STALL_BUFFERING_MILLIS + STALL_POLL_MILLIS) {
            buffering(10_000)
        })
    }

    @Test fun aNewTargetStartsTheWindowOver() {
        assertNull(watch.feed(STALL_BUFFERING_MILLIS - STALL_POLL_MILLIS) { buffering(10_000) })
        assertNull(watch.onSample(buffering(10_000).copy(epoch = 2), STALL_BUFFERING_MILLIS))
        assertNull(watch.onSample(buffering(10_000).copy(epoch = 2), STALL_BUFFERING_MILLIS * 2 - 1))
        assertEquals(PlaybackStall.BUFFERING, watch.onSample(buffering(10_000).copy(epoch = 2), STALL_BUFFERING_MILLIS * 2))
    }

    @Test fun aResetForASeekOrPauseStartsTheWindowOver() {
        assertNull(watch.feed(STALL_BUFFERING_MILLIS - STALL_POLL_MILLIS) { buffering(10_000) })
        watch.reset()
        assertNull(watch.onSample(buffering(10_000), STALL_BUFFERING_MILLIS))
        assertNull(watch.onSample(buffering(10_000), STALL_BUFFERING_MILLIS * 2 - 1))
    }

    @Test fun aReportedStallNeedsAFullWindowAgain() {
        assertEquals(PlaybackStall.BUFFERING, watch.feed(STALL_BUFFERING_MILLIS) { buffering(10_000) })
        assertNull(watch.onSample(buffering(10_000), STALL_BUFFERING_MILLIS + STALL_POLL_MILLIS))
    }

    @Test fun theClockRunningWithoutAFrameIsAPictureStall() {
        assertEquals(PlaybackStall.PICTURE, watch.feed(PICTURE_STALL_MILLIS) { now -> playing(position = now) })
    }

    @Test fun aRenderedFrameStartsThePictureWindowOver() {
        var frames = 0
        assertNull(watch.feed(PICTURE_STALL_MILLIS * 4) { now ->
            if (now % 1_000L == 0L) frames++
            playing(position = now, frames = frames)
        })
    }

    @Test fun aStillPictureWhileTheClockStandsIsNoStall() {
        // Paused by the decoder pipeline, not the viewer: the position does not run either.
        assertNull(watch.feed(PICTURE_STALL_MILLIS * 4) { playing(position = 0L) })
    }

    @Test fun aPausedTargetIsNoPictureStall() {
        assertNull(watch.feed(PICTURE_STALL_MILLIS * 4) { now -> playing(position = now).copy(playing = false) })
    }

    @Test fun anUnwatchedPictureIsNoStall() {
        // Radio, tunnelling, or counters of another target.
        assertNull(watch.feed(PICTURE_STALL_MILLIS * 4) { now -> playing(position = now).copy(videoCounters = null) })
    }

    @Test fun otherDecoderCountersStartThePictureWindowOver() {
        assertNull(watch.feed(PICTURE_STALL_MILLIS - STALL_POLL_MILLIS) { now -> playing(position = now) })
        assertNull(watch.onSample(playing(position = PICTURE_STALL_MILLIS).copy(videoCounters = Any()), PICTURE_STALL_MILLIS))
    }

    @Test fun aBackwardPositionStartsThePictureWindowOver() {
        assertNull(watch.feed(PICTURE_STALL_MILLIS - STALL_POLL_MILLIS) { now -> playing(position = 10_000 + now) })
        assertNull(watch.onSample(playing(position = 0L), PICTURE_STALL_MILLIS))
        assertNull(watch.onSample(playing(position = PICTURE_STALL_MILLIS - 1), PICTURE_STALL_MILLIS * 2 - 1))
    }

    @Test fun aCoveredPictureThatNeverMovesIsAStallAfterTheCoversGrace() {
        // The cover waits for the picture to move past its first frame; audio and the clock run.
        assertNull(watch.feed(PICTURE_STALL_MILLIS + PICTURE_COVER_GRACE_MILLIS - STALL_POLL_MILLIS) { now ->
            playing(position = now, frames = 1).copy(pictureShown = false)
        })
        assertEquals(PlaybackStall.PICTURE, watch.onSample(
            playing(position = PICTURE_STALL_MILLIS + PICTURE_COVER_GRACE_MILLIS, frames = 1).copy(pictureShown = false),
            PICTURE_STALL_MILLIS + PICTURE_COVER_GRACE_MILLIS,
        ))
    }

    @Test fun stallingStillIsNoNewEvidenceButProgressAndResetsAre() {
        val reported = watch.feed(STALL_BUFFERING_MILLIS) { buffering(10_000) }
        assertEquals(PlaybackStall.BUFFERING, reported)
        val evidence = watch.evidence
        assertNull(watch.onSample(buffering(10_000), STALL_BUFFERING_MILLIS + STALL_POLL_MILLIS))
        assertEquals(evidence, watch.evidence)
        // Media3 stops buffering after the report: the reported stall is superseded.
        assertNull(watch.onSample(playing(position = 0L), STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS))
        assertTrue(watch.evidence != evidence)
        val afterProgress = watch.evidence
        watch.reset()
        assertTrue(watch.evidence != afterProgress)
    }

    @Test fun aFrameAfterAReportedPictureStallSupersedesIt() {
        assertEquals(PlaybackStall.PICTURE, watch.feed(PICTURE_STALL_MILLIS) { now -> playing(position = now) })
        val evidence = watch.evidence
        assertNull(watch.onSample(playing(position = PICTURE_STALL_MILLIS + STALL_POLL_MILLIS), PICTURE_STALL_MILLIS + STALL_POLL_MILLIS))
        assertEquals(evidence, watch.evidence)
        assertNull(watch.onSample(playing(position = PICTURE_STALL_MILLIS + 2 * STALL_POLL_MILLIS, frames = 1),
            PICTURE_STALL_MILLIS + 2 * STALL_POLL_MILLIS))
        assertTrue(watch.evidence != evidence)
    }

    @Test fun oneRecoveryPerTargetAndIntentWithinTheWindow() {
        val live = AppPlaybackTarget.Live(ChannelId(1))
        val recovery = StallRecovery(live, viewingIntent = 3, atMillis = 1_000)
        assertTrue((null as StallRecovery?).allowsRecovery(live, 3, 1_000))
        assertFalse(recovery.allowsRecovery(live, 3, 1_000 + STALL_RECOVERY_WINDOW_MILLIS - 1))
        assertTrue(recovery.allowsRecovery(live, 3, 1_000 + STALL_RECOVERY_WINDOW_MILLIS))
        // The viewer's own start or Retry, or another target, has its own recovery.
        assertTrue(recovery.allowsRecovery(live, 4, 1_001))
        assertTrue(recovery.allowsRecovery(AppPlaybackTarget.Live(ChannelId(2)), 3, 1_001))
        assertTrue(recovery.allowsRecovery(AppPlaybackTarget.Recording(DvrEntryId(1)), 3, 1_001))
    }

    private fun buffering(aheadMillis: Long) = StallSample(
        epoch = 1, buffering = true, bufferedAheadMillis = aheadMillis, playing = false,
        positionMillis = 0L, videoCounters = counters, renderedFrames = 0, pictureShown = true,
    )

    private fun playing(position: Long, frames: Int = 0) = StallSample(
        epoch = 1, buffering = false, bufferedAheadMillis = 10_000, playing = true,
        positionMillis = position, videoCounters = counters, renderedFrames = frames, pictureShown = true,
    )

    /** Samples every poll from [from] through [from] + [millis]; the last result. */
    private fun PlaybackStallWatch.feed(millis: Long, from: Long = 0L, sample: (Long) -> StallSample): PlaybackStall? {
        var result: PlaybackStall? = null
        var now = from
        while (now <= from + millis) {
            result = onSample(sample(now), now)
            if (result != null) return result
            now += STALL_POLL_MILLIS
        }
        return result
    }
}
