package at.bernhardberger.tvhplayer.appliance

import org.junit.Assert.*
import org.junit.Test

class ButtonCaptureTest {
    private val functions = CompanionFunctions()
    private fun ButtonCapture.down(code: Int, now: Long = 0, repeat: Int = 0) =
        keyEvent(code, true, false, repeat, now, functions)
    private fun ButtonCapture.up(code: Int, now: Long = 0) = keyEvent(code, false, true, 0, now, functions)

    @Test fun captureRecordsTheMoveAndConsumesTheWholeCycleButNothingElseAfterwards() {
        val capture = ButtonCapture()
        assertEquals(CaptureState.Idle, capture.state.value)
        assertFalse(capture.down(172))
        capture.start(CompanionFunction.LIVE_TV, 0)
        assertTrue(capture.down(172))
        assertEquals(CaptureState.Captured(CompanionFunction.LIVE_TV, 172,
            MovedButton(CompanionFunction.GUIDE, 172)), capture.state.value)
        assertTrue(capture.down(172, repeat = 1))
        assertTrue(capture.up(172))
        assertFalse(capture.up(172))
        assertFalse(capture.down(85))
    }

    @Test fun everyRefusalRestartsTheSameTenSecondTimerAndStaysWaiting() {
        val refused = mapOf(19 to "Arrows", 20 to "Arrows", 21 to "Arrows", 22 to "Arrows", 23 to "OK",
            66 to "OK", 160 to "OK", 3 to "HOME", 24 to "Volume", 25 to "Volume", 164 to "Volume",
            26 to "Power", 223 to "Power", 224 to "Power")
        for ((code, name) in refused) {
            val capture = ButtonCapture()
            capture.start(CompanionFunction.GUIDE, 100)
            assertTrue(capture.down(code, 9_000))
            assertEquals(CaptureState.Refused(CompanionFunction.GUIDE, name, 19_000), capture.state.value)
            assertTrue(capture.down(code, 9_100, repeat = 1))
            assertEquals(19_000L, capture.deadline)
            assertTrue(capture.up(code, 9_200))
            capture.expire(10_100)
            assertTrue(capture.state.value is CaptureState.Refused)
            capture.expire(19_000)
            assertEquals(CaptureState.TimedOut(CompanionFunction.GUIDE), capture.state.value)
        }
    }

    @Test fun backCancelsWithoutAssignmentAndConsumesBackUp() {
        val capture = ButtonCapture()
        capture.start(CompanionFunction.GUIDE, 0)
        assertTrue(capture.down(4))
        assertEquals(CaptureState.Cancelled, capture.state.value)
        assertTrue(capture.up(4))
        assertFalse(capture.down(170))
    }

    @Test fun timeoutAndLifecycleCancellationKeepAlreadyConsumedKeysUntilTheirUp() {
        for (timeout in listOf(false, true)) {
            val capture = ButtonCapture()
            capture.start(CompanionFunction.GUIDE, 0)
            assertTrue(capture.down(19))
            if (timeout) capture.expire(ButtonCapture.TIMEOUT_MILLIS) else capture.cancel()
            assertTrue(capture.up(19, ButtonCapture.TIMEOUT_MILLIS))
            assertFalse(capture.down(85, ButtonCapture.TIMEOUT_MILLIS))
            assertEquals(if (timeout) CaptureState.TimedOut(CompanionFunction.GUIDE) else CaptureState.Cancelled,
                capture.state.value)
        }
    }

    @Test fun timeoutOccursAtDeadlineEvenIfNoTimerCallbackHasRun() {
        val capture = ButtonCapture()
        capture.start(CompanionFunction.GUIDE, 0)
        assertFalse(capture.down(85, ButtonCapture.TIMEOUT_MILLIS))
        assertEquals(CaptureState.TimedOut(CompanionFunction.GUIDE), capture.state.value)
    }

    @Test fun repeatedRefusalsExtendTheDeadlineAndAnUpOrRepeatAloneCannotChooseAKey() {
        val capture = ButtonCapture()
        capture.start(CompanionFunction.GUIDE, 0)
        assertTrue(capture.up(85))
        assertTrue(capture.down(85, repeat = 1))
        assertEquals(CaptureState.Waiting(CompanionFunction.GUIDE, 10_000), capture.state.value)
        assertTrue(capture.down(19, 9_000))
        assertTrue(capture.down(23, 18_000))
        assertEquals(CaptureState.Refused(CompanionFunction.GUIDE, "OK", 28_000), capture.state.value)
        assertTrue(capture.down(85, 27_000))
        assertEquals(CaptureState.Captured(CompanionFunction.GUIDE, 85, null), capture.state.value)
        assertTrue(capture.up(19, 27_100))
        assertTrue(capture.up(23, 27_100))
        assertTrue(capture.up(85, 27_100))
    }
}
