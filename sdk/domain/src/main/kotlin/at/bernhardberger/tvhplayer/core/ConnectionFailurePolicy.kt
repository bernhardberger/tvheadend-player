package at.bernhardberger.tvhplayer.core

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class ConnectionFailureKind {
    AUTHENTICATION,
    DNS,
    UNREACHABLE,
    TIMEOUT,
    INCOMPATIBLE_SERVER,
    PERMISSION_DENIED,
    ZERO_CHANNELS,
    OTHER,
}

class IncompatibleServerVersionException(val serverVersion: Int) :
    IllegalStateException("Incompatible HTSP server version")

class MetadataPermissionDeniedException :
    IllegalStateException("HTSP metadata permission denied")

class ZeroChannelsException :
    IllegalStateException("HTSP initial sync contained zero channels")

fun connectionFailureKind(error: Throwable): ConnectionFailureKind {
    var current: Throwable? = error
    repeat(8) {
        val failure = current ?: return ConnectionFailureKind.OTHER
        when {
            failure is IncompatibleServerVersionException ->
                return ConnectionFailureKind.INCOMPATIBLE_SERVER
            failure is MetadataPermissionDeniedException ->
                return ConnectionFailureKind.PERMISSION_DENIED
            failure is ZeroChannelsException -> return ConnectionFailureKind.ZERO_CHANNELS
            failure is UnknownHostException -> return ConnectionFailureKind.DNS
            failure is NoRouteToHostException || failure is ConnectException ->
                return ConnectionFailureKind.UNREACHABLE
            failure is SocketTimeoutException -> return ConnectionFailureKind.TIMEOUT
            failure is IllegalStateException &&
                (
                    failure.message?.contains("authentication failed", ignoreCase = true) == true ||
                        // Anonymous connect to a server that grants us no HTSP rights.
                        failure.message?.contains("noaccess=1", ignoreCase = true) == true
                    ) ->
                return ConnectionFailureKind.AUTHENTICATION
        }
        current = failure.cause
    }
    return ConnectionFailureKind.OTHER
}
