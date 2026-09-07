package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import java.util.Locale

internal fun cacheSizeMegabytes(statistics: CacheStatistics, locale: Locale): String =
    String.format(locale, "%.1f", (statistics.metadataBytes.toDouble() + statistics.artworkBytes) / 1_000_000)
