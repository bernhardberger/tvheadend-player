package at.bernhardberger.tvhplayer.player.htsp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionEventPumpTest {
    @Test
    fun collectorsAreRegisteredBeforePumpStartupReturns() = runBlocking {
        val controlEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val muxEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val receivedControl = CompletableDeferred<String>()
        val receivedMux = CompletableDeferred<String>()

        val pump = launchSubscriptionEventPump(
            controlEvents = controlEvents,
            muxEvents = muxEvents,
            onControl = { receivedControl.complete(it) },
            onMux = { receivedMux.complete(it) },
        )

        controlEvents.tryEmit("subscriptionStart")
        muxEvents.tryEmit("muxpkt")

        assertEquals("subscriptionStart", withTimeout(1_000) { receivedControl.await() })
        assertEquals("muxpkt", withTimeout(1_000) { receivedMux.await() })
        pump.cancel()
    }
}
