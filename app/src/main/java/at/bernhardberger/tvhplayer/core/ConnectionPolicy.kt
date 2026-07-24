package at.bernhardberger.tvhplayer.core

/**
 * Pure connection-related decisions, kept free of Android/Compose dependencies so
 * they can be unit tested on the JVM and shared between the HTSP layer and the
 * connection view model.
 */
object ConnectionPolicy {

    /**
     * Auto-connect requires only a host and port. Credentials are optional: an
     * unauthenticated TVHeadend connects with empty username/password.
     */
    fun isAutoConnectReady(host: String, port: Int): Boolean =
        host.isNotBlank() && port != 0

    /**
     * Only attempt HTSP authentication when BOTH a username and password are
     * provided. Empty credentials mean an unauthenticated server.
     */
    fun shouldAuthenticate(username: String?, password: String?): Boolean {
        val u = username?.trim().orEmpty()
        val p = password?.trim().orEmpty()
        return u.isNotEmpty() && p.isNotEmpty()
    }

    fun isSameEndpoint(
        connectedHost: String?,
        connectedPort: Int?,
        requestedHost: String,
        requestedPort: Int,
    ): Boolean = connectedHost != null &&
        connectedPort != null &&
        connectedHost == requestedHost.trim() &&
        connectedPort == requestedPort
}
