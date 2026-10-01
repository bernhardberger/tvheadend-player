package at.bernhardberger.tvhplayer.core

enum class GlanceBadgeKind { RASTER, VIDEO, AUDIO, AD, SUB, TXT }
data class GlanceBadge(val kind: GlanceBadgeKind, val value: String)

enum class VideoScan { INTERLACED, PROGRESSIVE }

/** Codec inputs are Media3 sample MIME types, never Format.codecs (RFC 6381). Scan/HDR are not inferred. */
fun glanceBadges(
    videoHeight: Int? = null,
    videoSampleMimeType: String? = null,
    audioMimeType: String? = null,
    audioChannelCount: Int? = null,
    audioDescription: Boolean = false,
    subtitles: Boolean = false,
    teletext: Boolean = false,
    scan: VideoScan? = null,
): List<GlanceBadge> = buildList {
    videoHeight?.takeIf { it > 0 }?.let {
        val suffix = when (scan) { VideoScan.INTERLACED -> "i"; VideoScan.PROGRESSIVE -> "p"; null -> "" }
        add(GlanceBadge(GlanceBadgeKind.RASTER, "$it$suffix"))
    }
    shortCodecName(videoSampleMimeType)?.let { add(GlanceBadge(GlanceBadgeKind.VIDEO, it)) }
    shortCodecName(audioMimeType)?.let { codec ->
        val layout = when (audioChannelCount) { 1 -> "1.0"; 2 -> "2.0"; 6 -> "5.1"; 8 -> "7.1"; else -> null }
        add(GlanceBadge(GlanceBadgeKind.AUDIO, listOfNotNull(codec, layout).joinToString(" ")))
    }
    if (audioDescription) add(GlanceBadge(GlanceBadgeKind.AD, ""))
    if (subtitles) add(GlanceBadge(GlanceBadgeKind.SUB, ""))
    if (teletext) add(GlanceBadge(GlanceBadgeKind.TXT, ""))
}
