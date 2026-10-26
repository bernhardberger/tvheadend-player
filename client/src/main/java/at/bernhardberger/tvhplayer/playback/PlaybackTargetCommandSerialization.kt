@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PlaybackTargetCommandSerialization {
    private val mutex = Mutex()
    private val accessLock = Any()
    @Volatile
    private var closed = false
    private val arrivals = AtomicLong()

    /**
     * Commands counted on arrival, before they wait for the lock: a running command that yields to
     * any newer one (a paused seek's picture refresh) compares it with its own arrival.
     */
    val arrivalCount: Long get() = arrivals.get()

    /**
     * [countsAsArrival] false: reactions to server observations and player events, which a brief
     * local play itself causes, wait their turn without ending a yielding command.
     */
    suspend fun <T> serialize(
        onClosed: () -> T,
        countsAsArrival: Boolean = true,
        command: suspend () -> T,
    ): T {
        if (countsAsArrival) arrivals.incrementAndGet()
        return mutex.withLock {
            if (closed) onClosed() else command()
        }
    }

    /** A counted command that knows its own arrival, to tell later whether a newer one followed. */
    suspend fun <T> serializeArrival(
        onClosed: () -> T,
        command: suspend (arrival: Long) -> T,
    ): T {
        val arrival = arrivals.incrementAndGet()
        return mutex.withLock {
            if (closed) onClosed() else command(arrival)
        }
    }

    suspend fun <Request, Result> retryRecording(
        onClosed: () -> Result,
        currentRequest: () -> Request?,
        retry: suspend (Request) -> Result,
    ): Result? = serialize(onClosed) { currentRequest()?.let { retry(it) } }

    suspend fun <Result> restoreRecordingIfNeeded(
        onClosed: () -> Result,
        targetMatches: () -> Boolean,
        restore: suspend () -> Result,
    ): Result? = serialize(onClosed) {
        if (targetMatches()) null else restore()
    }

    fun close(): Boolean = synchronized(accessLock) {
        if (closed) return@synchronized false
        closed = true
        true
    }

    fun isOpen(): Boolean = !closed

    fun runIfOpen(action: () -> Unit): Boolean = synchronized(accessLock) {
        if (closed) return@synchronized false
        action()
        true
    }

    fun <T> readIfOpen(read: () -> T): T? = synchronized(accessLock) {
        if (closed) null else read()
    }

    suspend fun awaitIdle(action: () -> Unit) {
        mutex.withLock {
            synchronized(accessLock, action)
        }
    }
}

internal suspend fun completePlaybackTargetInstallation(
    installTarget: suspend () -> PlaybackTargetResult,
    presentationStillCurrent: () -> Boolean,
    activeTarget: () -> AppPlaybackTarget?,
    onStarted: () -> Unit,
    onFailed: (PlaybackTargetResult) -> Unit,
): PlaybackTargetResult {
    val result = installTarget()
    if (!presentationStillCurrent()) return result
    if (result.isStarted) {
        onStarted()
    } else if (result == PlaybackTargetResult.DEPARTED || activeTarget() == null) {
        onFailed(result)
    }
    return result
}
