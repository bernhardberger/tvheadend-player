package at.bernhardberger.tvhplayer.ui.player

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackExitTest {
    @Test
    fun stopWaitsForTeardownBeforeClosing() = runTest {
        val teardown = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val operation = launch {
            stopPlaybackAndClose(
                stopPlayback = {
                    started.complete(Unit)
                    teardown.await()
                    events += "stopped"
                },
                closePlayer = { events += "closed" },
            )
        }
        started.await()
        assertEquals(emptyList<String>(), events)
        teardown.complete(Unit)
        operation.join()
        assertEquals(listOf("stopped", "closed"), events)
    }

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
