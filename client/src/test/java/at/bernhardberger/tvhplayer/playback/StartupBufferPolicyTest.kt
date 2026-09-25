package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_AUTOMATIC
import at.bernhardberger.tvhplayer.settings.StartupBufferLearningState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartupBufferPolicyTest {
    private val trouble = StartupBufferVerdict.TROUBLE
    private val clean = StartupBufferVerdict.CLEAN

    @Test
    fun troubleRaisesByHalfASecondAndResetsTheCleanCount() {
        val raised = StartupBufferPolicy.after(StartupBufferLearningState("a", 1000, 12), trouble, "a")
        assertEquals(StartupBufferLearningState("a", 1500, 0), raised)
    }

    @Test
    fun troubleIsCappedAtThreeSeconds() {
        var state = StartupBufferLearningState("a")
        repeat(10) { state = StartupBufferPolicy.after(state, trouble, "a") }
        assertEquals(3000, state.levelMillis)
    }

    @Test
    fun twentyConsecutiveCleanWindowsLowerByHalfASecond() {
        var state = StartupBufferLearningState("a", 2000, 0)
        repeat(19) { state = StartupBufferPolicy.after(state, clean, "a") }
        assertEquals(StartupBufferLearningState("a", 2000, 19), state)
        state = StartupBufferPolicy.after(state, clean, "a")
        assertEquals(StartupBufferLearningState("a", 1500, 0), state)
    }

    @Test
    fun troubleInBetweenRestartsTheCleanCount() {
        var state = StartupBufferLearningState("a", 2000, 0)
        repeat(19) { state = StartupBufferPolicy.after(state, clean, "a") }
        state = StartupBufferPolicy.after(state, trouble, "a")
        repeat(19) { state = StartupBufferPolicy.after(state, clean, "a") }
        assertEquals(StartupBufferLearningState("a", 2500, 19), state)
    }

    @Test
    fun cleanWindowsNeverGoBelowOneSecond() {
        var state = StartupBufferLearningState("a", 1000, 0)
        repeat(40) { state = StartupBufferPolicy.after(state, clean, "a") }
        assertEquals(1000, state.levelMillis)
    }

    @Test
    fun anotherServerStartsOverFromTheDefault() {
        val learned = StartupBufferLearningState("a", 2500, 9)
        assertEquals(StartupBufferLearningState("b", 1000, 0), StartupBufferPolicy.learningFor(learned, "b"))
        assertEquals(StartupBufferLearningState("b", 1500, 0), StartupBufferPolicy.after(learned, trouble, "b"))
        assertEquals(StartupBufferInEffect(1000, automatic = true),
            StartupBufferPolicy.inEffect(STARTUP_BUFFER_AUTOMATIC, learned, "b"))
        assertEquals(StartupBufferInEffect(2500, automatic = true),
            StartupBufferPolicy.inEffect(STARTUP_BUFFER_AUTOMATIC, learned, "a"))
    }

    @Test
    fun fixedSettingIgnoresTheLearnedLevel() {
        assertEquals(StartupBufferInEffect(500, automatic = false),
            StartupBufferPolicy.inEffect(500, StartupBufferLearningState("a", 3000, 0), "a"))
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
