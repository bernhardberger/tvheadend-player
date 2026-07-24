package at.bernhardberger.tvhplayer.ui.player

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackExitTest {
    @Test
    fun stopButtonStopsPlaybackBeforeClosingPlayer() = runBlocking {
        val events = mutableListOf<String>()

        stopPlaybackAndClose(
            stopPlayback = { events += "stop" },
            closePlayer = { events += "close" },
        )

        assertEquals(listOf("stop", "close"), events)
    }
}
