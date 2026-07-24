package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.htsp.HtspService
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TvhRepositoryConcurrencyTest {
    @Test
    fun epgFlowCreationIsAtomicAcrossThreads() {
        val repository = TvhRepository(
            htsp = HtspService(Dispatchers.IO),
            ioDispatcher = Dispatchers.IO,
        )
        val executor = Executors.newFixedThreadPool(THREADS)

        try {
            repeat(ROUNDS) { channelId ->
                val barrier = CyclicBarrier(THREADS)
                val tasks = List(THREADS) {
                    Callable {
                        barrier.await(2, TimeUnit.SECONDS)
                        repository.epgForChannel(channelId)
                    }
                }

                val flows = executor.invokeAll(tasks).map { it.get(2, TimeUnit.SECONDS) }
                assertEquals(1, flows.distinctBy(System::identityHashCode).size)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private companion object {
        const val THREADS = 16
        const val ROUNDS = 100
    }
}
