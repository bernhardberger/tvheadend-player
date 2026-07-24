package at.bernhardberger.tvhplayer.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class PlayerCommandGateTest {
    @Test
    fun activeServiceIsReusedOnlyForTheSameRequest() {
        assertFalse(shouldStartPlayback(activeServiceId = 33, requestedServiceId = 33))
        assertTrue(shouldStartPlayback(activeServiceId = 33, requestedServiceId = 34))
        assertTrue(shouldStartPlayback(activeServiceId = null, requestedServiceId = 33))
    }

    @Test
    fun commandsRunOneAtATimeInSubmissionOrder() = runBlocking {
        val gate = PlayerCommandGate()
        val events = CopyOnWriteArrayList<String>()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = launch(Dispatchers.Default) {
            gate.run {
                events += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "first-end"
            }
        }
        firstEntered.await()

        val second = launch(Dispatchers.Default) {
            gate.run { events += "second" }
        }

        delay(50)
        assertEquals(listOf("first-start"), events)

        releaseFirst.complete(Unit)
        joinAll(first, second)
        assertEquals(listOf("first-start", "first-end", "second"), events)
    }
}
