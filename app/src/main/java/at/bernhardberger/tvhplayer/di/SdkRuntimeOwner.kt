package at.bernhardberger.tvhplayer.di

import at.bernhardberger.tvheadend.client.TvheadendClient
import at.bernhardberger.tvheadend.playback.PlaybackRuntime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/** Process-lifetime owner used by DI teardown; screens only borrow its runtimes. */
internal class SdkRuntimeOwner(
    val client: TvheadendClient,
    val playbackRuntime: PlaybackRuntime,
    shutdownDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val shutdownJob = SupervisorJob()
    private val shutdownScope = CoroutineScope(shutdownJob + shutdownDispatcher)
    private val shutdownLock = Any()
    private var shutdown: Deferred<Unit>? = null

    /** Starts ordered teardown without parking Main; explicit owners may await [close]. */
    fun requestClose(): Deferred<Unit> = synchronized(shutdownLock) {
        shutdown ?: shutdownScope.async {
            try {
                playbackRuntime.release()
            } finally {
                client.close()
            }
        }.also { deferred ->
            shutdown = deferred
            deferred.invokeOnCompletion { shutdownJob.cancel() }
        }
    }

    suspend fun close() {
        requestClose().await()
    }
}
