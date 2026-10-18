package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import at.bernhardberger.tvhplayer.settings.resolveFrameRateSwitching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoFrameRateTest {
    @Test
    fun estimatorReportsNothingUntilEnoughFramesArrived() {
        val estimator = VideoFrameRateEstimator()
        feed(estimator::onFrame, frames = 5, spacingUs = 40_000L)

        assertNull(estimator.estimate())
    }

    @Test
    fun estimatorTakesTheMedianSpacingSoJitterAndOutliersDoNotMoveIt() {
        val estimator = VideoFrameRateEstimator()
        var time = 0L
        repeat(20) { index ->
            estimator.onFrame(time)
            time += when (index) {
                5 -> 120_000L
                9 -> 8_000L
                else -> 40_000L
            }
        }

        assertEquals(25f, estimator.estimate()!!, 0.01f)
    }

    @Test
    fun estimatorMeasuresFiftyAndFiftyNinePointNineFour() {
        val fifty = VideoFrameRateEstimator()
        feed(fifty::onFrame, frames = 20, spacingUs = 20_000L)
        assertEquals(50f, fifty.estimate()!!, 0.01f)

        val ntsc = VideoFrameRateEstimator()
        feed(ntsc::onFrame, frames = 20, spacingUs = 16_683L)
        assertEquals(59.94f, ntsc.estimate()!!, 0.02f)
    }

    @Test
    fun estimatorDropsItsWindowOnSeekBackLongGapsAndReset() {
        val estimator = VideoFrameRateEstimator()
        feed(estimator::onFrame, frames = 20, spacingUs = 40_000L)
        estimator.onFrame(0L)
        assertNull(estimator.estimate())

        feed(estimator::onFrame, frames = 20, spacingUs = 20_000L, startUs = 10_000_000L)
        assertEquals(50f, estimator.estimate()!!, 0.01f)

        estimator.onFrame(60_000_000L)
        assertNull(estimator.estimate())

        feed(estimator::onFrame, frames = 20, spacingUs = 20_000L, startUs = 70_000_000L)
        estimator.reset()
        assertNull(estimator.estimate())
    }

    @Test
    fun pairedTimestampsCountAsSameTimeFramesAndLeaveTheSpacingOfDistinctTimes() {
        // Two frames per 40 ms presentation time, the second identical or 0.5 ms later.
        for (pairOffsetUs in listOf(0L, 500L)) {
            val estimator = VideoFrameRateEstimator()
            repeat(40) {
                estimator.onFrame(it * 40_000L)
                estimator.onFrame(it * 40_000L + pairOffsetUs)
            }
            // The distinct-time spacing is 40 ms less the offset: 25 or 25.3 fps, never 50.
            assertEquals(25f, estimator.estimate()!!, 0.4f)
            assertEquals(0.5f, estimator.sameTimeShare(), 0.04f)
        }

        val plain = VideoFrameRateEstimator()
        feed(plain::onFrame, frames = 40, spacingUs = 20_000L)
        assertEquals(0f, plain.sameTimeShare(), 0f)
        // Just over 1 ms is a real (if odd) spacing, not a same-time frame.
        val close = VideoFrameRateEstimator()
        feed(close::onFrame, frames = 10, spacingUs = 1_001L)
        assertEquals(0f, close.sameTimeShare(), 0f)

        val estimator = VideoFrameRateEstimator()
        repeat(20) { estimator.onFrame(it * 40_000L); estimator.onFrame(it * 40_000L) }
        estimator.reset()
        assertEquals(0f, estimator.sameTimeShare(), 0f)
    }

    @Test
    fun measuredRatesMapToTheNearestStandardRateOnly() {
        assertEquals(50f, standardFrameRate(49.8f))
        assertEquals(25f, standardFrameRate(25.3f))
        assertEquals(59.94f, standardFrameRate(59.94f))
        assertEquals(60f, standardFrameRate(60.01f))
        assertEquals(23.976f, standardFrameRate(23.98f))
        assertEquals(24f, standardFrameRate(24.01f))
        assertEquals(29.97f, standardFrameRate(29.97f))
        assertNull(standardFrameRate(40f))
        assertNull(standardFrameRate(12.5f))
        assertNull(standardFrameRate(100f))
    }

    @Test
    fun aRateBecomesStableOnlyAfterHoldingAndAZapKeepsItUntilTheNewOneHolds() {
        val stable = StableFrameRate(requiredFrames = 4)
        repeat(3) { assertNull(stable.offer(50f)) }
        assertNull(stable.stable)
        assertEquals(50f, stable.offer(50f))
        assertEquals(50f, stable.stable)

        // A zap: unmeasurable frames and a short run of another rate keep 50.
        assertNull(stable.offer(null))
        repeat(3) { assertNull(stable.offer(25f)) }
        assertNull(stable.offer(50f))
        assertEquals(50f, stable.stable)

        // The same rate after a zap changes nothing; a held new rate replaces it once.
        repeat(10) { assertNull(stable.offer(50f)) }
        repeat(3) { assertNull(stable.offer(25f)) }
        assertEquals(25f, stable.offer(25f))
        assertNull(stable.offer(25f))

        stable.reset()
        assertNull(stable.stable)
    }

    @Test
    fun onlyAlwaysOnAndroid12RequestsARateItself() {
        assertEquals(50f, frameRateRequest(FrameRateSwitching.ALWAYS, 31, 50f))
        assertNull(frameRateRequest(FrameRateSwitching.ALWAYS, 31, null))
        assertNull(frameRateRequest(FrameRateSwitching.ALWAYS, 30, 50f))
        assertNull(frameRateRequest(FrameRateSwitching.SEAMLESS_ONLY, 34, 50f))
        assertNull(frameRateRequest(FrameRateSwitching.OFF, 34, 50f))
    }

    @Test
    fun media3HandlesSeamlessSwitchesAndAlwaysBelowAndroid12() {
        assertEquals(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF, media3FrameRateStrategy(FrameRateSwitching.ALWAYS, 31))
        assertEquals(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS, media3FrameRateStrategy(FrameRateSwitching.ALWAYS, 30))
        assertEquals(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS, media3FrameRateStrategy(FrameRateSwitching.SEAMLESS_ONLY, 34))
        assertEquals(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF, media3FrameRateStrategy(FrameRateSwitching.OFF, 34))
    }

    @Test
    fun frameRateSwitchingDefaultsToAlways() {
        assertEquals(FrameRateSwitching.ALWAYS, resolveFrameRateSwitching(null))
        assertEquals(FrameRateSwitching.ALWAYS, resolveFrameRateSwitching("bogus"))
        assertEquals(FrameRateSwitching.SEAMLESS_ONLY, resolveFrameRateSwitching("SEAMLESS_ONLY"))
        assertEquals(FrameRateSwitching.OFF, resolveFrameRateSwitching("OFF"))
    }

    @Test
    fun playbackFrameRateRequestsTheStableRateWhileAlwaysAndForgetsItOnReset() {
        val frameRate = PlaybackFrameRate(sdkInt = 31, requiredFrames = 4)
        feed(frameRate::onFrame, frames = 11, spacingUs = 20_000L)
        assertNull(frameRate.requested.value.rate)
        frameRate.onFrame(11 * 20_000L)
        assertEquals(50f, frameRate.requested.value.rate)

        frameRate.setSwitching(FrameRateSwitching.SEAMLESS_ONLY)
        assertNull(frameRate.requested.value.rate)
        frameRate.setSwitching(FrameRateSwitching.ALWAYS)
        assertEquals(50f, frameRate.requested.value.rate)

        frameRate.reset()
        assertNull(frameRate.requested.value.rate)
        assertNull(frameRate.measured())
    }

    @Test
    fun aRequestWithdrawnForMedia3IsForgottenWithoutClearingItsVote() {
        val vote = SurfaceFrameRateVote()
        vote.surfaceCreated()
        assertEquals(50f, vote.request(50f))
        // Media3 replaced the vote when it took over: clearing now would erase Media3's.
        assertNull(vote.request(null, media3Votes = true))
        assertNull(vote.request(null, media3Votes = true))
        // Back to the app: it asks again rather than assuming its old vote survived.
        assertEquals(50f, vote.request(50f))
        // Withdrawn without Media3 taking over: cleared.
        assertEquals(0f, vote.request(null))
    }

    @Test
    fun aSurfaceHandedBackFromMedia3WithoutARateIsCleared() {
        // Always → seamless only → Off: Media3 leaves its last vote while Off.
        val off = SurfaceFrameRateVote()
        off.surfaceCreated()
        assertEquals(50f, off.request(50f))
        assertNull(off.request(null, media3Votes = true))
        assertEquals(0f, off.request(null))
        assertNull(off.request(null))

        // Seamless only → Always before the app has a rate.
        val always = SurfaceFrameRateVote()
        always.surfaceCreated()
        assertNull(always.request(null, media3Votes = true))
        assertEquals(0f, always.request(null))

        // A surface created while Media3 owns it carries Media3's vote too.
        val recreated = SurfaceFrameRateVote()
        assertNull(recreated.request(null, media3Votes = true))
        assertNull(recreated.surfaceCreated())
        assertEquals(0f, recreated.request(null))
    }

    @Test
    fun aSurfaceHandedBackFromMedia3WithARateVotesIt() {
        val vote = SurfaceFrameRateVote()
        vote.surfaceCreated()
        assertNull(vote.request(null, media3Votes = true))
        assertEquals(25f, vote.request(25f))
        assertNull(vote.request(25f))
    }

    @Test
    fun releasingASurfaceMedia3VotesOnLeavesItsVote() {
        val vote = SurfaceFrameRateVote()
        vote.surfaceCreated()
        assertNull(vote.request(null, media3Votes = true))
        assertNull(vote.release())
    }

    @Test
    fun theRequestCarriesWhetherMedia3VotesOnTheSurface() {
        val frameRate = PlaybackFrameRate(sdkInt = 31)
        frameRate.setSwitching(FrameRateSwitching.ALWAYS)
        assertEquals(SurfaceFrameRateRequest(null, media3Votes = false), frameRate.requested.value)
        frameRate.setSwitching(FrameRateSwitching.SEAMLESS_ONLY)
        // The rate stays null, yet the owner change is a new value collectors see.
        assertEquals(SurfaceFrameRateRequest(null, media3Votes = true), frameRate.requested.value)
        frameRate.setSwitching(FrameRateSwitching.OFF)
        assertEquals(SurfaceFrameRateRequest(null, media3Votes = false), frameRate.requested.value)
    }

    @Test
    fun aClosedFrameRateIgnoresLaterFrames() {
        val frameRate = PlaybackFrameRate(sdkInt = 31, requiredFrames = 4)
        feed(frameRate::onFrame, frames = 12, spacingUs = 20_000L)
        assertEquals(50f, frameRate.requested.value.rate)
        frameRate.close()
        assertNull(frameRate.requested.value.rate)
        feed(frameRate::onFrame, frames = 12, spacingUs = 20_000L, startUs = 1_000_000L)
        assertNull(frameRate.requested.value.rate)
        assertNull(frameRate.measured())
    }

    @Test
    fun aSurfaceVoteIsSetOnceClearedOnDemandAndRenewedOnARecreatedSurface() {
        val vote = SurfaceFrameRateVote()
        // No surface yet: nothing to set until it exists.
        assertNull(vote.request(50f))
        assertEquals(50f, vote.surfaceCreated())
        assertNull(vote.request(50f))
        assertEquals(25f, vote.request(25f))

        assertEquals(0f, vote.surfaceDestroyed())
        assertNull(vote.request(50f))
        assertEquals(50f, vote.surfaceCreated())

        assertEquals(0f, vote.request(null))
        assertNull(vote.request(null))
        assertNull(vote.surfaceDestroyed())
        assertNull(vote.surfaceCreated())
    }

    private fun feed(onFrame: (Long) -> Unit, frames: Int, spacingUs: Long, startUs: Long = 0L) {
        repeat(frames) { onFrame(startUs + it * spacingUs) }
    }
}
