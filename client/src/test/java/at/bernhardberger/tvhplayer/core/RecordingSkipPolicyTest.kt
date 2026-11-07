package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.SkipLanding
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.*
import org.junit.Test

class RecordingSkipPolicyTest {
    private val ad = RecordingMark(RecordingMarkKind.AD_BREAK, 10_000, 20_000)
    private val cut = RecordingMark(RecordingMarkKind.CUT, 30_000, 40_000)
    private val marks = listOf(ad, cut)
    private val automatic = PlayerSettings(adBreakMode = AdBreakMode.AUTOMATIC)

    @Test fun naturalEntrySkipsCutsSilentlyAndAutomaticAdsOnce() {
        val policy = RecordingSkipPolicy()
        assertNull(policy.evaluate(marks, 9_999, true, automatic).seekToMs)
        assertEquals(RecordingSkipDecision(20_000, ad), policy.evaluate(marks, 10_000, true, automatic))
        assertEquals(RecordingSkipDecision(40_000), policy.evaluate(marks, 30_000, true, automatic))
        assertNull(policy.evaluate(marks, 15_000, true, automatic).seekToMs)
    }

    @Test fun deliberateSeekPlaysDestinationUntilExitAndReentry() {
        val policy = RecordingSkipPolicy()
        policy.deliberateSeek(marks, 35_000)
        assertNull(policy.evaluate(marks, 35_001, true, automatic).seekToMs)
        policy.evaluate(marks, 40_000, true, automatic)
        assertEquals(40_000L, policy.evaluate(marks, 30_000, true, automatic).seekToMs)
        policy.deliberateSeek(marks, 15_000)
        assertNull(policy.evaluate(marks, 15_001, true, automatic).seekToMs)
        assertNull(policy.offeredAdBreak)
    }

    @Test fun resumeInsideAndLateMetadataCountAsEntry() {
        for (mark in marks) {
            val policy = RecordingSkipPolicy()
            assertNull(policy.evaluate(emptyList(), mark.startMs + 1, true, automatic).seekToMs)
            assertNull(policy.evaluate(marks, mark.startMs + 1, false, automatic).seekToMs)
            assertEquals(mark.endMs, policy.evaluate(marks, mark.startMs + 1, true, automatic).seekToMs)
        }
    }

    @Test fun deliberateDestinationSurvivesMetadataArrivingAfterTheSeek() {
        for (settings in listOf(PlayerSettings(), automatic)) {
            for (mark in marks) {
                val policy = RecordingSkipPolicy()
                policy.deliberateSeek(emptyList(), mark.startMs + 1)
                policy.evaluate(emptyList(), mark.startMs + 2, true, settings)
                assertNull(policy.evaluate(listOf(mark), mark.startMs + 3, true, settings).seekToMs)
                assertNull(policy.offeredAdBreak)
                policy.evaluate(listOf(mark), mark.endMs, true, settings)
                val reentry = policy.evaluate(listOf(mark), mark.startMs, true, settings)
                if (mark.kind == RecordingMarkKind.AD_BREAK && settings.adBreakMode == AdBreakMode.ASK) {
                    assertEquals(mark, policy.offeredAdBreak)
                } else assertEquals(mark.endMs, reentry.seekToMs)
            }
        }
    }

    @Test fun leavingTheUnknownSpanBeforeMetadataArrivesEndsDeliberateSuppression() {
        val policy = RecordingSkipPolicy()
        policy.deliberateSeek(emptyList(), ad.startMs + 1)
        policy.evaluate(emptyList(), ad.endMs, true, automatic)
        assertEquals(ad.endMs, policy.evaluate(listOf(ad), ad.startMs, true, automatic).seekToMs)
    }

    @Test fun askOffersOncePerEntryDismissesAndManualSkipUsesLanding() {
        val policy = RecordingSkipPolicy()
        val settings = PlayerSettings(skipLanding = SkipLanding.TWO_SECONDS_BEFORE)
        assertNull(policy.evaluate(marks, 10_000, true, settings).seekToMs)
        assertEquals(ad, policy.offeredAdBreak)
        assertEquals(ad, policy.currentAdBreak)
        policy.dismissPrompt()
        policy.evaluate(marks, 11_000, true, settings)
        assertNull(policy.offeredAdBreak)
        assertEquals(18_000L, policy.skipAdBreak(ad, settings))
        policy.evaluate(marks, 20_000, true, settings)
        assertNull(policy.currentAdBreak)
        assertNull(policy.skipAdBreak(ad, settings))
        policy.evaluate(marks, 10_000, true, settings)
        assertEquals(ad, policy.offeredAdBreak)
    }

    @Test fun anEarlyLandingEndsTheBreakAsCurrentSoSkipCannotSeekBackwards() {
        val settings = PlayerSettings(skipLanding = SkipLanding.FIVE_SECONDS_BEFORE)
        val manual = RecordingSkipPolicy()
        manual.evaluate(marks, 11_000, true, settings)
        assertEquals(15_000L, manual.skipAdBreak(ad, settings))
        assertNull(manual.currentAdBreak)
        manual.evaluate(marks, 15_000, true, settings)
        assertNull(manual.currentAdBreak)
        assertNull(manual.offeredAdBreak)
        assertNull(manual.skipAdBreak(ad, settings))
        manual.evaluate(marks, 19_000, true, settings)
        assertNull(manual.skipAdBreak(ad, settings))

        val automatic = RecordingSkipPolicy()
        val auto = settings.copy(adBreakMode = AdBreakMode.AUTOMATIC)
        assertEquals(RecordingSkipDecision(15_000, ad), automatic.evaluate(marks, 10_000, true, auto))
        assertNull(automatic.currentAdBreak)
        assertNull(automatic.evaluate(marks, 15_000, true, auto).seekToMs)
        assertNull(automatic.currentAdBreak)
        assertNull(automatic.skipAdBreak(ad, auto))
    }

    @Test fun thePromptLeavesAtTheLandingPointAndIsNotOfferedPastIt() {
        val settings = PlayerSettings(skipLanding = SkipLanding.FIVE_SECONDS_BEFORE)
        val policy = RecordingSkipPolicy()
        policy.evaluate(marks, 10_000, true, settings)
        assertEquals(ad, policy.offeredAdBreak)
        policy.evaluate(marks, 14_999, true, settings)
        assertEquals(ad, policy.offeredAdBreak)
        policy.evaluate(marks, 15_000, true, settings)
        assertNull(policy.offeredAdBreak)
        assertNull(policy.currentAdBreak)
        // Resuming inside the break past its landing point offers nothing.
        val late = RecordingSkipPolicy()
        late.evaluate(marks, 16_000, true, settings)
        assertNull(late.offeredAdBreak)
        assertNull(late.currentAdBreak)
        late.deliberateSeek(marks, 17_000, settings.skipLanding)
        assertNull(late.currentAdBreak)
        late.deliberateSeek(marks, 12_000, settings.skipLanding)
        assertEquals(ad, late.currentAdBreak)
    }

    @Test fun offAndDisabledCutsDoNothing() {
        val policy = RecordingSkipPolicy()
        val settings = PlayerSettings(adBreakMode = AdBreakMode.OFF, skipCuts = false)
        assertNull(policy.evaluate(marks, 10_000, true, settings).seekToMs)
        assertNull(policy.offeredAdBreak)
        assertNull(policy.skipAdBreak(ad, settings))
        assertNull(policy.evaluate(marks, 30_000, true, settings).seekToMs)
    }

    @Test fun landingOffsetsClampToStartAndDoNotRepeatBeforeEnd() {
        assertEquals(20_000L, recordingSkipLanding(ad, SkipLanding.END))
        assertEquals(18_000L, recordingSkipLanding(ad, SkipLanding.TWO_SECONDS_BEFORE))
        assertEquals(15_000L, recordingSkipLanding(ad, SkipLanding.FIVE_SECONDS_BEFORE))
        assertEquals(10_000L, recordingSkipLanding(ad.copy(endMs = 11_000), SkipLanding.FIVE_SECONDS_BEFORE))
        val settings = automatic.copy(skipLanding = SkipLanding.FIVE_SECONDS_BEFORE)
        val policy = RecordingSkipPolicy()
        assertEquals(35_000L, policy.evaluate(marks, 30_000, true, settings).seekToMs)
        assertNull(policy.evaluate(marks, 35_000, true, settings).seekToMs)
        assertNull(policy.evaluate(marks, 39_999, true, settings).seekToMs)
    }

    @Test fun extendingPreviouslySkippedAdDoesNotSkipItAgain() {
        val policy = RecordingSkipPolicy()
        policy.evaluate(marks, 10_000, true, automatic)
        policy.evaluate(marks, 25_000, true, automatic)
        assertNull(policy.evaluate(listOf(ad.copy(endMs = 22_000)), 12_000, true, automatic).seekToMs)
    }

    @Test fun metadataExtensionDoesNotUndoADeliberateSeekSuppression() {
        val policy = RecordingSkipPolicy()
        policy.deliberateSeek(marks, 35_000)
        val extended = listOf(cut.copy(endMs = 45_000))
        assertNull(policy.evaluate(extended, 36_000, true, automatic).seekToMs)
        policy.evaluate(extended, 45_000, true, automatic)
        assertEquals(45_000L, policy.evaluate(extended, 35_000, true, automatic).seekToMs)
    }

    @Test fun cutTakesPriorityButLandingInsideAdStillEntersAd() {
        val policy = RecordingSkipPolicy()
        val overlap = cut.copy(startMs = 12_000, endMs = 14_000)
        val both = listOf(ad, overlap)
        assertEquals(RecordingSkipDecision(14_000), policy.evaluate(both, 12_000, true, automatic))
        assertEquals(RecordingSkipDecision(20_000, ad), policy.evaluate(both, 14_000, true, automatic))
    }

    @Test fun muteSpansAreHalfOpenIndependentOfMarkAndSkipSettings() {
        val points = listOf(DvrCutpoint(10.milliseconds, 20.milliseconds, DvrCutpointAction.MUTE))
        assertTrue(recordingMarks(points).isEmpty())
        assertFalse(recordingMuted(points, 9))
        assertTrue(recordingMuted(points, 10))
        assertTrue(recordingMuted(points, 19))
        assertFalse(recordingMuted(points, 20))
    }
}
