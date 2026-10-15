package at.bernhardberger.tvhplayer.ui.common

import android.content.Context
import android.text.format.Formatter
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Decimal SI file sizes, using the selected language from the caller's configured context. */
fun formatFileSize(sizeBytes: Long, context: Context): String =
    Formatter.formatShortFileSize(context, sizeBytes)

/** Numeric values whose unit is supplied by a translated string resource. */
fun formatDecimal(value: Double, locale: Locale, minimumFractionDigits: Int, maximumFractionDigits: Int): String =
    NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        roundingMode = RoundingMode.HALF_UP
        this.minimumFractionDigits = minimumFractionDigits
        this.maximumFractionDigits = maximumFractionDigits
    }.format(value)

/** Ungrouped metadata numbers: episode ordinals, years and ratings are not quantities with thousands separators. */
fun formatMetadataNumber(value: Long, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }.format(value)
