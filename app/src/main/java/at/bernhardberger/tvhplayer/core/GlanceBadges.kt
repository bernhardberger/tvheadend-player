package at.bernhardberger.tvhplayer.core

enum class GlanceBadgeKind { RASTER, AUDIO, AD }
data class GlanceBadge(val kind: GlanceBadgeKind, val value: String)

enum class VideoScan { INTERLACED, PROGRESSIVE }

/** Sample MIME types, not RFC 6381 codec strings. Scan is declared, never inferred. */
fun glanceBadges(
    videoHeight: Int? = null,
    audioMimeType: String? = null,
    audioChannelCount: Int? = null,
    audioDescription: Boolean = false,
    scan: VideoScan? = null,
): List<GlanceBadge> = buildList {
    videoHeight?.takeIf { it > 0 }?.let {
        val suffix = when (scan) { VideoScan.INTERLACED -> "i"; VideoScan.PROGRESSIVE -> "p"; null -> "" }
        add(GlanceBadge(GlanceBadgeKind.RASTER, "$it$suffix"))
    }
    shortCodecName(audioMimeType)?.let { codec ->
        val layout = when (audioChannelCount) { 1 -> "1.0"; 2 -> "2.0"; 6 -> "5.1"; 8 -> "7.1"; else -> null }
        add(GlanceBadge(GlanceBadgeKind.AUDIO, listOfNotNull(codec, layout).joinToString(" ")))
    }
    if (audioDescription) add(GlanceBadge(GlanceBadgeKind.AD, ""))
}
