package at.bernhardberger.tvhplayer.core

/**
 * How a TVHeadend stream profile should be labelled in Settings.
 *
 * `htsp` is the direct/pass-through profile and gets a human primary label with
 * the exact server name secondarily. Other profiles keep their exact server
 * names without guessing transcoder behaviour.
 */
data class StreamProfilePresentation(
    val primaryLabel: String,
    val secondaryLabel: String?,
)

fun streamProfilePresentation(
    profileName: String,
    directStreamingLabel: String,
): StreamProfilePresentation {
    val exact = profileName.trim()
    return if (exact.equals("htsp", ignoreCase = true)) {
        StreamProfilePresentation(
            primaryLabel = directStreamingLabel,
            secondaryLabel = exact.ifEmpty { "htsp" },
        )
    } else {
        StreamProfilePresentation(
            primaryLabel = exact.ifEmpty { profileName },
            secondaryLabel = null,
        )
    }
}
