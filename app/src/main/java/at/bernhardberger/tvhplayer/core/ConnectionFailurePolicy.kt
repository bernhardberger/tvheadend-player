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
    OTHER,
}

fun connectionFailureKind(error: Throwable): ConnectionFailureKind {
    var current: Throwable? = error
    repeat(8) {
        val failure = current ?: return ConnectionFailureKind.OTHER
        when {
            failure is UnknownHostException -> return ConnectionFailureKind.DNS
            failure is NoRouteToHostException || failure is ConnectException ->
                return ConnectionFailureKind.UNREACHABLE
            failure is SocketTimeoutException -> return ConnectionFailureKind.TIMEOUT
            failure is IllegalStateException &&
                failure.message?.contains("authentication failed", ignoreCase = true) == true ->
                return ConnectionFailureKind.AUTHENTICATION
        }
        current = failure.cause
    }
    return ConnectionFailureKind.OTHER
}
