@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.di

import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.PlaybackShutdownResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendPlaybackCoordinator
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.yield

/** Process-lifetime owner for the one released-SDK session/coordinator/player graph. */
internal class SdkRuntimeOwner(
    val session: TvheadendSession,
    val playbackRuntime: AppPlaybackRuntime,
    val appProfileOwner: AppProfileOwner,
    private val coordinator: TvheadendPlaybackCoordinator,
    private val coordinatorRunJob: Job,
    private val profileOwnerRunJob: Job,
    private val player: ExoPlayer,
    private val applicationJob: Job,
    shutdownDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val shutdownController = SdkRuntimeShutdownController(
        shutdownDispatcher = shutdownDispatcher,
        closeRuntime = ::closeInOrder,
    )

    init {
        shutdownController.observeActor("coordinator", coordinatorRunJob)
        shutdownController.observeActor("profile owner", profileOwnerRunJob)
    }

    fun requestClose(): Deferred<Unit> = shutdownController.requestClose()

    suspend fun close() = requestClose().await()

    private suspend fun closeInOrder(initialFailure: Throwable?) = closeSdkRuntime(
        actions = object : SdkShutdownActions {
            override suspend fun shutdownCoordinator(): PlaybackShutdownResult =
                coordinator.shutdown(5.seconds)
            override fun cancelCoordinatorRun() { coordinatorRunJob.cancel() }
            override suspend fun joinCoordinatorRun() { coordinatorRunJob.join() }
            override fun cancelProfileOwnerRun() { profileOwnerRunJob.cancel() }
            override suspend fun joinProfileOwnerRun() { profileOwnerRunJob.join() }
            override suspend fun shutdownSession() { session.shutdown() }
            override suspend fun detachApplicationListeners() { playbackRuntime.detach() }
            override fun releasePlayer() { player.release() }
            override suspend fun cancelApplicationScope() { applicationJob.cancelAndJoin() }
        },
        initialFailure = initialFailure,
    )

    companion object {
        fun create(
            session: TvheadendSession,
            playbackRuntime: AppPlaybackRuntime,
            appProfileOwner: AppProfileOwner,
            coordinator: TvheadendPlaybackCoordinator,
            player: ExoPlayer,
            applicationScope: CoroutineScope,
            shutdownDispatcher: CoroutineDispatcher = Dispatchers.Main,
        ): SdkRuntimeOwner {
            val runJob = launchCoordinatorRun(applicationScope) { coordinator.run() }
            val profileOwnerRunJob = applicationScope.async { appProfileOwner.run() }
            return SdkRuntimeOwner(
                session,
                playbackRuntime,
                appProfileOwner,
                coordinator,
                runJob,
                profileOwnerRunJob,
                player,
                checkNotNull(applicationScope.coroutineContext[Job]) {
                    "Application scope must own a lifecycle job"
                },
                shutdownDispatcher,
            )
        }
    }
}

internal fun launchCoordinatorRun(
    scope: CoroutineScope,
    run: suspend () -> Unit,
): Job = scope.async(start = CoroutineStart.UNDISPATCHED) { run() }

internal class SdkRuntimeShutdownController(
    shutdownDispatcher: CoroutineDispatcher,
    private val closeRuntime: suspend (initialFailure: Throwable?) -> Unit,
) {
    private val shutdownJob = SupervisorJob()
    private val shutdownScope = CoroutineScope(shutdownJob + shutdownDispatcher)
    private val lock = Any()
    private var shutdown: Deferred<Unit>? = null

    fun observeActor(name: String, job: Job) {
        job.invokeOnCompletion { cause ->
            requestCloseAfterUnexpectedActorCompletion(
                cause ?: IllegalStateException("SDK $name actor completed unexpectedly"),
            )
        }
    }

    fun requestClose(): Deferred<Unit> = requireNotNull(requestClose(initialFailure = null))

    private fun requestCloseAfterUnexpectedActorCompletion(failure: Throwable) {
        requestClose(initialFailure = failure, onlyIfOpen = true)
    }

    private fun requestClose(
        initialFailure: Throwable?,
        onlyIfOpen: Boolean = false,
    ): Deferred<Unit>? {
        var created = false
        val deferred = synchronized(lock) {
            if (onlyIfOpen && shutdown != null) return null
            shutdown ?: shutdownScope.async(start = CoroutineStart.LAZY) {
                // Completion handlers must return before cleanup joins the actor's parent scope.
                yield()
                closeRuntime(initialFailure)
            }.also {
                shutdown = it
                created = true
                it.invokeOnCompletion { shutdownJob.cancel() }
            }
        }
        if (created) deferred.start()
        return deferred
    }
}

internal interface SdkShutdownActions {
    suspend fun shutdownCoordinator(): PlaybackShutdownResult
    fun cancelCoordinatorRun()
    suspend fun joinCoordinatorRun()
    fun cancelProfileOwnerRun()
    suspend fun joinProfileOwnerRun()
    suspend fun shutdownSession()
    suspend fun detachApplicationListeners()
    fun releasePlayer()
    suspend fun cancelApplicationScope()
}

internal suspend fun closeSdkRuntime(
    actions: SdkShutdownActions,
    initialFailure: Throwable? = null,
) {
    var failure: Throwable? = initialFailure
    fun record(error: Throwable) {
        if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
    }
    fun cancelCoordinatorRun() {
        try {
            actions.cancelCoordinatorRun()
        } catch (error: Throwable) {
            record(error)
        }
    }

    try {
        if (actions.shutdownCoordinator() == PlaybackShutdownResult.NOT_RUNNING) {
            cancelCoordinatorRun()
        }
    } catch (error: Throwable) {
        record(error)
        cancelCoordinatorRun()
    }
    try {
        actions.joinCoordinatorRun()
    } catch (error: Throwable) {
        record(error)
    }
    try {
        actions.cancelProfileOwnerRun()
    } catch (error: Throwable) {
        record(error)
    }
    try {
        actions.joinProfileOwnerRun()
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
