package at.bernhardberger.tvhplayer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The confirmation card's width rule: a fixed column that grows only while its copy wraps badly. */
class ConfirmationDialogTest {
    @Test fun fittingCopyKeepsTheBaseColumn() {
        assertEquals(352, confirmationColumnWidth(buttonRow = 300, base = 352, max = 520, step = 4) { false })
    }

    @Test fun aWiderButtonRowSetsTheColumn() {
        assertEquals(380, confirmationColumnWidth(buttonRow = 380, base = 352, max = 520, step = 4) { false })
    }

    @Test fun growsInStepsUntilTheCopyFits() {
        val asked = mutableListOf<Int>()
        val width = confirmationColumnWidth(buttonRow = 0, base = 352, max = 520, step = 4) { asked += it; it < 361 }
        assertEquals(364, width)
        assertEquals(listOf(352, 356, 360, 364), asked)
    }

    @Test fun stopsAtTheMaximum() {
        assertEquals(520, confirmationColumnWidth(buttonRow = 0, base = 352, max = 520, step = 5) { true })
    }

    @Test fun aButtonRowWiderThanTheMaximumIsKept() {
        assertEquals(560, confirmationColumnWidth(buttonRow = 560, base = 352, max = 520, step = 4) { true })
    }

    @Test fun aLastLineOfOneWordIsAnOrphan() {
        assertTrue(endsOnOneWord("recordings?"))
        assertTrue(endsOnOneWord(" recorded. "))
        assertFalse(endsOnOneWord("won't be recorded."))
    }
}
