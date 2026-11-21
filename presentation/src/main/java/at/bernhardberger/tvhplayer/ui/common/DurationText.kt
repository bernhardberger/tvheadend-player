package at.bernhardberger.tvhplayer.ui.common

/** Seconds, whole or fractional, in the configured app locale. */
fun formatDurationSeconds(
    seconds: Number,
    resources: android.content.res.Resources,
    spoken: Boolean = false,
): String = android.icu.text.MeasureFormat.getInstance(
    resources.configuration.locales[0],
    if (spoken) android.icu.text.MeasureFormat.FormatWidth.WIDE else android.icu.text.MeasureFormat.FormatWidth.NARROW,
).format(android.icu.util.Measure(seconds, android.icu.util.MeasureUnit.SECOND))
