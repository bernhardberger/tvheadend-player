package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import at.bernhardberger.tvhplayer.settings.resolveFrameRateSwitching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

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
    fun theDisplayCadenceNeverAsksFor25Or30Hz() {
        assertEquals(50f, displayCadence(25f))
        assertEquals(59.94f, displayCadence(29.97f))
        assertEquals(60f, displayCadence(30f))
        for (rate in listOf(23.976f, 24f, 50f, 59.94f, 60f)) assertEquals(rate, displayCadence(rate))
    }

    @Test
    fun aDeclaredFrameDurationAsksForItsStandardRateAtTheDisplayCadence() {
        assertEquals(50f, declaredDisplayRate(40.milliseconds))
        assertEquals(50f, declaredDisplayRate(20.milliseconds))
        assertEquals(59.94f, declaredDisplayRate(33_367.microseconds))
        assertEquals(23.976f, declaredDisplayRate(41_708.microseconds))
        assertEquals(60f, declaredDisplayRate(33_333.microseconds))
        assertEquals(24f, declaredDisplayRate(41_667.microseconds))
        // Off-standard or nonsense declarations ask for nothing.
        assertNull(declaredDisplayRate(45.milliseconds))
        assertNull(declaredDisplayRate(80.milliseconds))
        assertNull(declaredDisplayRate(Duration.ZERO))
    }

    @Test
    fun theSettingAndAndroidVersionDecideHowTheAppVotes() {
        assertEquals(SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.ALWAYS), surfaceFrameRateRequest(FrameRateSwitching.ALWAYS, 31, 50f))
        assertEquals(
            SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.ONLY_IF_SEAMLESS),
            surfaceFrameRateRequest(FrameRateSwitching.SEAMLESS_ONLY, 34, 50f),
        )
        // Android 11 has only the seamless two-argument call, whatever the setting.
        assertEquals(SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.SEAMLESS_DEFAULT), surfaceFrameRateRequest(FrameRateSwitching.ALWAYS, 30, 50f))
        assertEquals(
            SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.SEAMLESS_DEFAULT),
            surfaceFrameRateRequest(FrameRateSwitching.SEAMLESS_ONLY, 30, 50f),
        )
        // Off, Android 9 and no rate: no vote.
        assertEquals(SurfaceFrameRateRequest(), surfaceFrameRateRequest(FrameRateSwitching.OFF, 34, 50f))
        assertEquals(SurfaceFrameRateRequest(), surfaceFrameRateRequest(FrameRateSwitching.ALWAYS, 28, 50f))
        assertEquals(SurfaceFrameRateRequest(), surfaceFrameRateRequest(FrameRateSwitching.ALWAYS, 34, null))
    }

    @Test
    fun frameRateSwitchingDefaultsToAlways() {
        assertEquals(FrameRateSwitching.ALWAYS, resolveFrameRateSwitching(null))
        assertEquals(FrameRateSwitching.ALWAYS, resolveFrameRateSwitching("bogus"))
        assertEquals(FrameRateSwitching.SEAMLESS_ONLY, resolveFrameRateSwitching("SEAMLESS_ONLY"))
        assertEquals(FrameRateSwitching.OFF, resolveFrameRateSwitching("OFF"))
    }

    @Test
    fun aDeclarationIsRequestedAtOnceKeptThroughAGapReplacedByTheNextAndClearedOnReset() {
        val frameRate = PlaybackFrameRate(sdkInt = 31)
        frameRate.onDeclared(40.milliseconds)
        assertEquals(SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.ALWAYS), frameRate.requested.value)
        assertEquals(40.milliseconds, frameRate.declared.value)

        // A live restart gap keeps the display where it is.
        frameRate.onDeclared(null)
        assertEquals(50f, frameRate.requested.value.rate)
        assertNull(frameRate.declared.value)

        frameRate.onDeclared(41_708.microseconds)
        assertEquals(23.976f, frameRate.requested.value.rate)
        // An off-standard declaration replaces it with no request.
        frameRate.onDeclared(45.milliseconds)
        assertNull(frameRate.requested.value.rate)

        frameRate.onDeclared(20.milliseconds)
        frameRate.reset()
        assertEquals(SurfaceFrameRateRequest(), frameRate.requested.value)
        frameRate.onDeclared(null)
        assertNull(frameRate.requested.value.rate)
    }

    @Test
    fun theSettingChangesHowTheDeclaredRateIsAskedFor() {
        val frameRate = PlaybackFrameRate(sdkInt = 31)
        frameRate.onDeclared(40.milliseconds)
        frameRate.setSwitching(FrameRateSwitching.SEAMLESS_ONLY)
        assertEquals(SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.ONLY_IF_SEAMLESS), frameRate.requested.value)
        frameRate.setSwitching(FrameRateSwitching.OFF)
        assertEquals(SurfaceFrameRateRequest(), frameRate.requested.value)
        frameRate.setSwitching(FrameRateSwitching.ALWAYS)
        assertEquals(SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.ALWAYS), frameRate.requested.value)

        val android11 = PlaybackFrameRate(sdkInt = 30).apply { onDeclared(40.milliseconds) }
        assertEquals(SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.SEAMLESS_DEFAULT), android11.requested.value)
        val android9 = PlaybackFrameRate(sdkInt = 28).apply { onDeclared(40.milliseconds) }
        assertEquals(SurfaceFrameRateRequest(), android9.requested.value)
    }

    @Test
    fun releasedFramesAreMeasuredButRequestNothing() {
        val frameRate = PlaybackFrameRate(sdkInt = 31)
        feed(frameRate::onFrame, frames = 80, spacingUs = 20_000L)
        assertEquals(50f, frameRate.measured()!!, 0.01f)
        assertNull(frameRate.requested.value.rate)
        frameRate.reset()
        assertNull(frameRate.measured())
    }

    @Test
    fun aClosedFrameRateIgnoresLaterDeclarationsAndFrames() {
        val frameRate = PlaybackFrameRate(sdkInt = 31)
        frameRate.onDeclared(40.milliseconds)
        frameRate.close()
        assertEquals(SurfaceFrameRateRequest(), frameRate.requested.value)
        assertNull(frameRate.declared.value)
        frameRate.onDeclared(20.milliseconds)
        feed(frameRate::onFrame, frames = 12, spacingUs = 20_000L)
        assertEquals(SurfaceFrameRateRequest(), frameRate.requested.value)
        assertNull(frameRate.measured())
    }

    @Test
    fun aSurfaceVoteIsSetOnceClearedOnDemandAndRenewedOnARecreatedSurface() {
        val vote = SurfaceFrameRateVote()
        val fifty = SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.ALWAYS)
        val twentyFive = SurfaceFrameRateRequest(25f, SurfaceFrameRateChange.ALWAYS)
        // No surface yet: nothing to set until it exists.
        assertNull(vote.request(fifty))
        assertEquals(fifty, vote.surfaceCreated())
        assertNull(vote.request(fifty))
        assertEquals(twentyFive, vote.request(twentyFive))

        // A destroyed surface clears its vote; a recreated one gets the request again.
        assertEquals(SurfaceFrameRateRequest(), vote.surfaceDestroyed())
        assertNull(vote.request(fifty))
        assertEquals(fifty, vote.surfaceCreated())

        assertEquals(SurfaceFrameRateRequest(), vote.request(SurfaceFrameRateRequest()))
        assertNull(vote.request(SurfaceFrameRateRequest()))
        assertNull(vote.surfaceDestroyed())
        assertNull(vote.surfaceCreated())
    }

    @Test
    fun aChangedStrategyVotesAgainAndOffOrReleaseClearsTheVote() {
        val vote = SurfaceFrameRateVote()
        vote.surfaceCreated()
        assertEquals(SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.ALWAYS), vote.request(SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.ALWAYS)))
        // Same rate, seamless only now: the surface gets the new strategy.
        val seamless = SurfaceFrameRateRequest(50f, SurfaceFrameRateChange.ONLY_IF_SEAMLESS)
        assertEquals(seamless, vote.request(seamless))
        // Off: the app's earlier vote is cleared; nothing else votes on the surface.
        assertEquals(SurfaceFrameRateRequest(), vote.request(SurfaceFrameRateRequest()))

        vote.request(seamless)
        assertEquals(SurfaceFrameRateRequest(), vote.release())
        assertNull(vote.release())
    }

    private fun feed(onFrame: (Long) -> Unit, frames: Int, spacingUs: Long, startUs: Long = 0L) {
        repeat(frames) { onFrame(startUs + it * spacingUs) }
    }
}
