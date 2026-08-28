@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.di

import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.TvheadendPlaybackCoordinator
import at.bernhardberger.tvhplayer.core.StreamProfileDiscovery
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/** Process-lifetime owner for the one released-SDK session/coordinator/player graph. */
internal class SdkRuntimeOwner(
    val session: TvheadendSession,
    val playbackRuntime: AppPlaybackRuntime,
    val streamProfileDiscovery: StreamProfileDiscovery,
    private val coordinator: TvheadendPlaybackCoordinator,
    private val coordinatorRunJob: Job,
    private val player: ExoPlayer,
    private val applicationJob: Job,
    shutdownDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val shutdownJob = SupervisorJob()
    private val shutdownScope = CoroutineScope(shutdownJob + shutdownDispatcher)
    private val shutdownLock = Any()
    private var shutdown: Deferred<Unit>? = null

    fun requestClose(): Deferred<Unit> = synchronized(shutdownLock) {
        shutdown ?: shutdownScope.async { closeInOrder() }.also { deferred ->
            shutdown = deferred
            deferred.invokeOnCompletion { shutdownJob.cancel() }
        }
    }

    suspend fun close() = requestClose().await()

    private suspend fun closeInOrder() = closeSdkRuntime(
        object : SdkShutdownActions {
            override suspend fun shutdownCoordinator() { coordinator.shutdown(5.seconds) }
            override fun cancelCoordinatorRun() { coordinatorRunJob.cancel() }
            override suspend fun joinCoordinatorRun() { coordinatorRunJob.join() }
            override suspend fun shutdownSession() { session.shutdown() }
            override fun detachApplicationListeners() { playbackRuntime.detach() }
            override fun releasePlayer() { player.release() }
            override suspend fun cancelApplicationScope() { applicationJob.cancelAndJoin() }
        },
    )

    companion object {
        fun create(
            session: TvheadendSession,
            playbackRuntime: AppPlaybackRuntime,
            streamProfileDiscovery: StreamProfileDiscovery,
            coordinator: TvheadendPlaybackCoordinator,
            player: ExoPlayer,
            applicationScope: CoroutineScope,
            shutdownDispatcher: CoroutineDispatcher = Dispatchers.Main,
        ): SdkRuntimeOwner {
            val runJob = applicationScope.launch { coordinator.run() }
            return SdkRuntimeOwner(
                session,
                playbackRuntime,
                streamProfileDiscovery,
                coordinator,
                runJob,
                player,
                checkNotNull(applicationScope.coroutineContext[Job]) {
                    "Application scope must own a lifecycle job"
                },
                shutdownDispatcher,
            )
        }
    }
}

internal interface SdkShutdownActions {
    suspend fun shutdownCoordinator()
    fun cancelCoordinatorRun()
    suspend fun joinCoordinatorRun()
    suspend fun shutdownSession()
    fun detachApplicationListeners()
    fun releasePlayer()
    suspend fun cancelApplicationScope()
}

internal suspend fun closeSdkRuntime(actions: SdkShutdownActions) {
    var failure: Throwable? = null
    fun record(error: Throwable) {
        if (failure == null) failure = error else failure.addSuppressed(error)
    }

    try {
        actions.shutdownCoordinator()
    } catch (error: Throwable) {
        record(error)
        try {
            actions.cancelCoordinatorRun()
        } catch (cancelError: Throwable) {
            record(cancelError)
        }
    }
    try {
        actions.joinCoordinatorRun()
    } catch (error: Throwable) {
        record(error)
    }
    try {
        actions.shutdownSession()
    } catch (error: Throwable) {
        record(error)
    }
    try {
        actions.detachApplicationListeners()
    } catch (error: Throwable) {
        record(error)
    }
    try {
        actions.releasePlayer()
    } catch (error: Throwable) {
        record(error)
    }
    try {
        actions.cancelApplicationScope()
    } catch (error: Throwable) {
        record(error)
    }
    failure?.let { throw it }
}
