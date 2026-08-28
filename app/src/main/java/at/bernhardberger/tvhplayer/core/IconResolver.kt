package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation

/**
 * Resolves the Coil model used to load a channel icon over the *pure HTSP* transport.
 *
 * TVHeadend may report a channel icon either as a server-relative path
 * (e.g. `imagecache/123`, `picon/...`) which we can open through HTSP, or as a raw
 * `http(s)://` URL (a "User icon"). This is an HTSP-only client by design, so raw
 * remote URLs are intentionally NOT fetched over HTTP — they resolve to `null` and
 * the UI falls back to the placeholder. To get such icons to show, enable
 * TVHeadend's imagecache so the icon is served as an HTSP-openable path instead.
 */
data class AppArtworkSource(
    val currentSession: CurrentSessionObservation,
    val selector: String,
)

fun resolvePiconModel(
    currentSession: CurrentSessionObservation,
    serverTag: String,
    piconPath: String?,
): AppArtworkSource? {
    if (serverTag.isBlank() || piconPath.isNullOrBlank()) return null

    val trimmed = piconPath.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }

    val p = trimmed.trimStart('/')
    if (!p.startsWith("imagecache/")) return null
    val id = p.removePrefix("imagecache/")
    if (id.toIntOrNull()?.let { it > 0 } != true) return null
    return AppArtworkSource(currentSession, p)
}
