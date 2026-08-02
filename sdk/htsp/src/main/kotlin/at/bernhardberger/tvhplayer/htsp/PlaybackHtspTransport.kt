package at.bernhardberger.tvhplayer.htsp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Playback-infrastructure SPI for the Media3 runtime.
 *
 * This is intentionally not a frontend service API. It exposes only the attempt-scoped
 * subscription and recording-file operations needed to keep playback atomic across a
 * reconnect; frontend clients should use [TvheadendClient] instead.
 */
@PlaybackIntegrationApi
interface PlaybackHtspTransport {
    val state: StateFlow<ConnectionState>
    val controlEvents: Flow<HtspEvent>
    val muxEvents: Flow<HtspMuxEvent>

    suspend fun startSubscription(
        expectedConnectionAttemptId: Long,
        subscriptionId: Int,
        channelId: Int,
        timeshiftPeriodSec: Int,
        profile: String?,
    ): PlaybackSubscriptionStart

    suspend fun stopSubscription(
        expectedConnectionAttemptId: Long,
        subscriptionId: Int,
    )

    suspend fun setSubscriptionSpeed(
        expectedConnectionAttemptId: Long,
        subscriptionId: Int,
        speed: Int,
    )

    suspend fun seekSubscription(
        expectedConnectionAttemptId: Long,
        subscriptionId: Int,
        timeUs: Long,
        absolute: Boolean,
    )

    suspend fun fileOpen(
        path: String,
        timeoutMs: Long = 5_000,
        expectedConnectionAttemptId: Long? = null,
    ): Int

    suspend fun fileRead(
        id: Int,
        size: Int,
        timeoutMs: Long = 5_000,
        expectedConnectionAttemptId: Long? = null,
    ): ByteArray

    suspend fun fileSeek(
        id: Int,
        offset: Long,
        whence: String = "SEEK_SET",
        timeoutMs: Long = 5_000,
        expectedConnectionAttemptId: Long? = null,
    ): Long

    suspend fun fileCloseRecording(
        id: Int,
        htspVersion: Int?,
        timeoutMs: Long = 5_000,
        expectedConnectionAttemptId: Long? = null,
    )

    fun currentConnectionAttemptId(): Long
    fun currentMuxSequenceForConnectionAttempt(attemptId: Long): Long?
    fun isCurrentConnectionAttemptId(attemptId: Long): Boolean
    fun connectionAttemptStatus(attemptId: Long): HtspConnectionAttemptStatus
    fun <T> commitIfCurrentConnectionAttempt(attemptId: Long, block: () -> T): T?
    fun <T> commitIfLiveConnectionAttempt(attemptId: Long, block: () -> T): T?
}

@PlaybackIntegrationApi
data class PlaybackSubscriptionStart(
    val availableTimeshiftPeriodSec: Int?,
)
