package at.bernhardberger.tvhplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingSkipCountdownTest {
    @Test fun remainingSweepCountsDownAndClampsElapsedFraction() {
        for ((elapsed, sweep) in listOf(-.5f to 360f, 0f to 360f, .5f to 180f, 1f to 0f, 1.5f to 0f)) {
            assertEquals("elapsed=$elapsed", sweep, recordingSkipRemainingSweep(elapsed), 0f)
        }
    }
}
