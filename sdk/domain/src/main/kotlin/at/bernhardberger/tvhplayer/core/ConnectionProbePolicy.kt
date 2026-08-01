package at.bernhardberger.tvhplayer.core

import kotlin.coroutines.cancellation.CancellationException

const val MIN_SUPPORTED_HTSP_VERSION = 19

interface ConnectionProbeSession {
    suspend fun connect(): Int
    suspend fun syncChannelMetadata(): Int
    suspend fun close()
}

sealed interface ConnectionProbeResult {
    data class Success(
        val serverVersion: Int,
        val channelCount: Int,
    ) : ConnectionProbeResult

    data class Failure(val kind: ConnectionFailureKind) : ConnectionProbeResult
}

suspend fun runConnectionProbe(
    session: ConnectionProbeSession,
    minimumHtspVersion: Int = MIN_SUPPORTED_HTSP_VERSION,
): ConnectionProbeResult = try {
    val serverVersion = session.connect()
    if (serverVersion < minimumHtspVersion) {
        throw IncompatibleServerVersionException(serverVersion)
    }

    val channelCount = session.syncChannelMetadata()
    if (channelCount == 0) throw ZeroChannelsException()

    ConnectionProbeResult.Success(
        serverVersion = serverVersion,
        channelCount = channelCount,
    )
} catch (error: Throwable) {
    if (error is CancellationException) throw error
    ConnectionProbeResult.Failure(connectionFailureKind(error))
} finally {
    runCatching { session.close() }.onFailure { error ->
        if (error is CancellationException) throw error
    }
}
