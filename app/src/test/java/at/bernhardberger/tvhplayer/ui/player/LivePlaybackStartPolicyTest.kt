package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LivePlaybackStartPolicyTest {
    @Test
    fun initialPlaybackIsResolvedOnlyAfterTargetRequestCompletes() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val requestResult = CompletableDeferred<PlaybackTargetResult>()
        var resolvedResult: PlaybackTargetResult? = null

        val start = launch {
            startInitialLivePlayback(
                startPlayback = {
                    requestStarted.complete(Unit)
                    requestResult.await()
                },
                onResolved = { resolvedResult = it },
            )
        }
        requestStarted.await()
        runCurrent()

        assertFalse(start.isCompleted)
        assertEquals(null, resolvedResult)
        requestResult.complete(PlaybackTargetResult.STARTED)
        start.join()
        assertEquals(PlaybackTargetResult.STARTED, resolvedResult)
    }
}
