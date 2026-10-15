package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvhplayer.ui.common.formatDecimal
import java.util.Locale

internal fun cacheSizeMegabytes(statistics: CacheStatistics, locale: Locale): String =
    formatDecimal((statistics.metadataBytes.toDouble() + statistics.artworkBytes) / 1_000_000, locale, 1, 1)
