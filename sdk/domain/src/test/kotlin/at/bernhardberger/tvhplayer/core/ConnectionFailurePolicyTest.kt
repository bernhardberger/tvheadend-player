package at.bernhardberger.tvhplayer.core

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionFailurePolicyTest {
    @Test
    fun connectionFailures_areReducedToSafeCategories() {
        assertEquals(
            ConnectionFailureKind.AUTHENTICATION,
            connectionFailureKind(IllegalStateException("HTSP authentication failed (noaccess=1)")),
        )
        assertEquals(
            ConnectionFailureKind.AUTHENTICATION,
            connectionFailureKind(
                IllegalStateException("HTSP server requires credentials (noaccess=1)"),
            ),
        )
        assertEquals(ConnectionFailureKind.DNS, connectionFailureKind(UnknownHostException()))
        assertEquals(ConnectionFailureKind.UNREACHABLE, connectionFailureKind(ConnectException()))
        assertEquals(ConnectionFailureKind.TIMEOUT, connectionFailureKind(SocketTimeoutException()))
        assertEquals(
            ConnectionFailureKind.INCOMPATIBLE_SERVER,
            connectionFailureKind(IncompatibleServerVersionException(18)),
        )
        assertEquals(
            ConnectionFailureKind.PERMISSION_DENIED,
            connectionFailureKind(MetadataPermissionDeniedException()),
        )
        assertEquals(
            ConnectionFailureKind.ZERO_CHANNELS,
            connectionFailureKind(ZeroChannelsException()),
        )
        assertEquals(ConnectionFailureKind.OTHER, connectionFailureKind(IllegalArgumentException()))
    }

    @Test
    fun wrappedFailure_usesItsSpecificCause() {
        assertEquals(
            ConnectionFailureKind.TIMEOUT,
            connectionFailureKind(IllegalStateException("wrapper", SocketTimeoutException())),
        )
    }
}
