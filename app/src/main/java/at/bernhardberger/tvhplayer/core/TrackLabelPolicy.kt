package at.bernhardberger.tvhplayer.core

import java.util.Locale

data class HumanTrackLabel(
    val primary: String,
    val secondary: String?,
)

/**
 * Human-readable track labels for ten-foot UI.
 *
 * Language uses Locale display names with explicit und/mis/zxx fallbacks.
 * Channel layout is primary technical information (Mono/Stereo/5.1/7.1);
 * sample rate and codec remain secondary.
 */
fun humanTrackLabel(
    languageCode: String?,
    channelCount: Int?,
    sampleRateHz: Int?,
    sampleMimeType: String?,
    roleLabel: String?,
    unknownLanguageLabel: String,
    monoLabel: String,
    stereoLabel: String,
    surround51Label: String,
    surround71Label: String,
    channelsLabel: (Int) -> String,
    trackFallbackLabel: String,
): HumanTrackLabel {
    val language = humanLanguageName(languageCode, unknownLanguageLabel)
    val channels = humanChannelLayout(
        channelCount = channelCount,
        monoLabel = monoLabel,
        stereoLabel = stereoLabel,
        surround51Label = surround51Label,
        surround71Label = surround71Label,
        channelsLabel = channelsLabel,
    )
    val primary = listOfNotNull(language, channels, roleLabel?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
        .ifBlank { trackFallbackLabel }
    val secondary = listOfNotNull(
        sampleRateHz?.takeIf { it > 0 }?.let { "${it} Hz" },
        humanCodecName(sampleMimeType),
    ).joinToString(" · ").ifBlank { null }
    return HumanTrackLabel(primary = primary, secondary = secondary)
}

fun humanLanguageName(languageCode: String?, unknownLanguageLabel: String): String? {
    val code = languageCode?.trim().orEmpty()
    if (code.isEmpty()) return null
    return when (code.lowercase(Locale.ROOT)) {
        "und", "mis", "zxx", "mul", "qaa" -> unknownLanguageLabel
        else -> {
            val locale = Locale.forLanguageTag(code.replace('_', '-'))
            locale.getDisplayLanguage(Locale.getDefault())
                .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
                ?: locale.getDisplayName(Locale.getDefault())
                    .takeIf { it.isNotBlank() }
                ?: code
        }
    }
}

fun humanChannelLayout(
    channelCount: Int?,
    monoLabel: String,
    stereoLabel: String,
    surround51Label: String,
    surround71Label: String,
    channelsLabel: (Int) -> String,
): String? = when (channelCount) {
    null -> null
    1 -> monoLabel
    2 -> stereoLabel
    6 -> surround51Label
    8 -> surround71Label
    else -> channelsLabel(channelCount)
}

fun humanCodecName(sampleMimeType: String?): String? {
    val mime = sampleMimeType?.trim().orEmpty()
    if (mime.isEmpty()) return null
    return when {
        mime.contains("ac3", ignoreCase = true) -> "Dolby Digital"
        mime.contains("eac3", ignoreCase = true) -> "Dolby Digital Plus"
        mime.contains("ac4", ignoreCase = true) -> "Dolby AC-4"
        mime.contains("mpeg-L2", ignoreCase = true) ||
            mime.contains("mpeg-l2", ignoreCase = true) -> "MPEG-1 Layer II"
        mime.contains("mpeg", ignoreCase = true) && mime.contains("audio", ignoreCase = true) ->
            "MPEG Audio"
        mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
        mime.contains("opus", ignoreCase = true) -> "Opus"
        mime.startsWith("audio/") -> mime.removePrefix("audio/")
        else -> mime
    }
}

