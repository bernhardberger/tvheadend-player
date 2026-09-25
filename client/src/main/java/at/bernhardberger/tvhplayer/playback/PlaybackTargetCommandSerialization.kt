@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PlaybackTargetCommandSerialization {
    private val mutex = Mutex()
    private val accessLock = Any()
    @Volatile
    private var closed = false

    suspend fun <T> serialize(
        onClosed: () -> T,
        command: suspend () -> T,
    ): T = mutex.withLock {
        if (closed) onClosed() else command()
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
    } else if (activeTarget() == null) {
        onFailed(result)
    }
    return result
}
