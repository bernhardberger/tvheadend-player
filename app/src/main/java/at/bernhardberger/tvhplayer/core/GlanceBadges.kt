package at.bernhardberger.tvhplayer.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

enum class GlanceBadgeKind { RASTER, VIDEO, AUDIO, AD, SUB, TXT, SNR_PERCENT, SNR_DB }
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
    liveFrontend: Boolean = false,
    relativeSnrPercent: Double? = null,
    absoluteSnrDecibels: Double? = null,
    maxBadges: Int = 8,
    locale: Locale = Locale.getDefault(),
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
    if (liveFrontend) {
        glanceSnr(relativeSnrPercent, absoluteSnrDecibels)?.let { snr ->
            val pattern = if (snr.kind == GlanceBadgeKind.SNR_PERCENT) "%.0f" else "%.1f"
            add(GlanceBadge(snr.kind, String.format(locale, pattern, snr.value)))
        }
    }
}.take(maxBadges.coerceAtLeast(0))

/** The SNR one badge shows, already rounded to its displayed precision. */
data class GlanceSnr(val kind: GlanceBadgeKind, val value: Double)

/** Relative percent (0–100, whole percent) when reported, else absolute dB (one decimal). */
fun glanceSnr(relativeSnrPercent: Double?, absoluteSnrDecibels: Double?): GlanceSnr? {
    val percent = relativeSnrPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }
    val db = absoluteSnrDecibels?.takeIf(Double::isFinite)
    return when {
        percent != null -> GlanceSnr(GlanceBadgeKind.SNR_PERCENT, displayRounded(percent, 0))
        db != null -> GlanceSnr(GlanceBadgeKind.SNR_DB, displayRounded(db, 1))
        else -> null
    }
}

// Formatter rounds HALF_UP on the shortest decimal form; BigDecimal.valueOf uses the same form.
private fun displayRounded(value: Double, decimals: Int): Double =
    BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toDouble()

/** Keep a priority prefix, never skip an oversized high-priority badge for a later one. */
fun glanceBadgeRows(widths: List<Int>, availableWidth: Int, gap: Int): List<List<Int>> {
    val rows = mutableListOf<MutableList<Int>>()
    var used = 0
    for ((index, width) in widths.withIndex()) {
        if (width > availableWidth) break
        if (rows.isEmpty() || used + gap + width > availableWidth) {
            if (rows.size == 2) break
            rows.add(mutableListOf())
            used = 0
        }
        if (rows.last().isNotEmpty()) used += gap
        rows.last().add(index)
        used += width
    }
    return rows
}
