package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_AUTOMATIC
import at.bernhardberger.tvhplayer.settings.StartupBufferLearningState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartupBufferPolicyTest {
    private val trouble = StartupBufferVerdict.TROUBLE
    private val clean = StartupBufferVerdict.CLEAN

    private fun StartupBufferLearningState.after(vararg verdicts: StartupBufferVerdict) =
        verdicts.fold(this) { state, verdict -> StartupBufferPolicy.after(state, verdict, "a") }

    @Test
    fun aNewServerStartsAtHalfASecond() {
        assertEquals(StartupBufferInEffect(500, automatic = true),
            StartupBufferPolicy.inEffect(STARTUP_BUFFER_AUTOMATIC, StartupBufferLearningState(), "a"))
    }

    @Test
    fun aSingleTroubleNeverRaises() {
        assertEquals(StartupBufferLearningState("a", 500, listOf(trouble)), StartupBufferLearningState("a").after(trouble))
        assertEquals(StartupBufferLearningState("a", 1500, listOf(trouble)),
            StartupBufferLearningState("a", 1500).after(trouble))
    }

    @Test
    fun aSecondTroubleRaisesByHalfASecondAndEmptiesTheHistory() {
        val raised = StartupBufferPolicy.after(StartupBufferLearningState("a", 1000, listOf(trouble)), trouble, "a")
        assertEquals(StartupBufferLearningState("a", 1500), raised)
        assertEquals(StartupBufferLearningState("a", 1000), StartupBufferLearningState("a").after(trouble, clean, trouble))
    }

    @Test
    fun troublesFourVerdictsApartStillRaise() {
        assertEquals(StartupBufferLearningState("a", 1000),
            StartupBufferLearningState("a").after(trouble, clean, clean, clean, trouble))
    }

    @Test
    fun troublesFiveVerdictsApartDoNotRaise() {
        assertEquals(StartupBufferLearningState("a", 500, listOf(clean, clean, clean, clean, trouble)),
            StartupBufferLearningState("a").after(trouble, clean, clean, clean, clean, trouble))
    }

    @Test
    fun aCleanVerdictNeverRaises() {
        // Two troubles in the last five, but the newest verdict is clean.
        val state = StartupBufferLearningState("a", 1000, listOf(trouble, clean, trouble))
        assertEquals(StartupBufferLearningState("a", 1000, listOf(trouble, clean, trouble, clean)), state.after(clean))
    }

    @Test
    fun aTroubleAfterARaiseCountsAloneAgain() {
        assertEquals(StartupBufferLearningState("a", 1000, listOf(trouble)),
            StartupBufferLearningState("a").after(trouble, trouble, trouble))
    }

    @Test
    fun troubleIsCappedAtThreeSeconds() {
        var state = StartupBufferLearningState("a")
        repeat(20) { state = StartupBufferPolicy.after(state, trouble, "a") }
        assertEquals(3000, state.levelMillis)
    }

    @Test
    fun fiveConsecutiveCleanWindowsLowerByHalfASecond() {
        var state = StartupBufferLearningState("a", 2000)
        repeat(4) { state = StartupBufferPolicy.after(state, clean, "a") }
        assertEquals(StartupBufferLearningState("a", 2000, List(4) { clean }), state)
        state = StartupBufferPolicy.after(state, clean, "a")
        assertEquals(StartupBufferLearningState("a", 1500), state)
    }

    @Test
    fun troubleInBetweenRestartsTheCleanStreak() {
        var state = StartupBufferLearningState("a", 2000)
        repeat(4) { state = StartupBufferPolicy.after(state, clean, "a") }
        state = StartupBufferPolicy.after(state, trouble, "a")
        repeat(4) { state = StartupBufferPolicy.after(state, clean, "a") }
        assertEquals(StartupBufferLearningState("a", 2000, listOf(trouble, clean, clean, clean, clean)), state)
        assertEquals(StartupBufferLearningState("a", 1500), state.after(clean))
    }

    @Test
    fun cleanWindowsNeverGoBelowHalfASecond() {
        var state = StartupBufferLearningState("a", 1000)
        repeat(40) { state = StartupBufferPolicy.after(state, clean, "a") }
        assertEquals(500, state.levelMillis)
    }

    @Test
    fun anotherServerStartsOverFromTheDefault() {
        val learned = StartupBufferLearningState("a", 2500, listOf(trouble, clean))
        assertEquals(StartupBufferLearningState("b", 500), StartupBufferPolicy.learningFor(learned, "b"))
        assertEquals(StartupBufferLearningState("b", 500, listOf(trouble)), StartupBufferPolicy.after(learned, trouble, "b"))
        assertEquals(StartupBufferInEffect(500, automatic = true),
            StartupBufferPolicy.inEffect(STARTUP_BUFFER_AUTOMATIC, learned, "b"))
        assertEquals(StartupBufferInEffect(2500, automatic = true),
            StartupBufferPolicy.inEffect(STARTUP_BUFFER_AUTOMATIC, learned, "a"))
    }

    @Test
    fun fixedSettingIgnoresTheLearnedLevel() {
        assertEquals(StartupBufferInEffect(500, automatic = false),
            StartupBufferPolicy.inEffect(500, StartupBufferLearningState("a", 3000), "a"))
    }

    @Test
    fun cutShortWindowGivesNoVerdict() {
        val window = StartupBufferWindow()
        window.arm()
        window.startIfArmed()
        window.cut()
        assertNull(window.elapsed())
        assertNull(window.rebuffered())
    }

    @Test
    fun windowReportsTroubleOnceAndCleanOnlyWhenItRanToTheEnd() {
        val window = StartupBufferWindow()
        window.arm()
        assertNull(window.rebuffered()) // start-up buffering before playback is not a rebuffer
        window.startIfArmed()
        assertNull(window.underrun())
        assertNull(window.underrun())
        assertEquals(trouble, window.underrun())
        assertNull(window.elapsed())
        window.arm()
        window.startIfArmed()
        assertEquals(clean, window.elapsed())
    }
}
