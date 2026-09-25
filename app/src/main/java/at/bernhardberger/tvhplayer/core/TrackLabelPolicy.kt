package at.bernhardberger.tvhplayer.core

import java.util.Locale

/**
 * A track label split into the lines of a track list row.
 *
 * [summary] is the one-line form for places that show only the chosen track.
 */
data class HumanTrackLabel(
    val summary: String,
    val name: String,
    val overline: String?,
    val detail: String?,
)

/**
 * Human-readable track labels for ten-foot UI.
 *
 * The name is what the viewer picks: the track's role (audio description, clear
 * dialogue) when it has one, otherwise its language. A role track with a known
 * language shows the language as the overline. The detail is the channel layout and
 * codec. Language uses Locale display names with explicit und/mis/zxx fallbacks;
 * "mul" is named as multiple languages, which is all the code says about the track.
 */
fun humanTrackLabel(
    languageCode: String?,
    channelCount: Int?,
    sampleMimeType: String?,
    roleLabel: String?,
    unknownLanguageLabel: String,
    multipleLanguagesLabel: String,
    monoLabel: String,
    stereoLabel: String,
    surround51Label: String,
    surround71Label: String,
    channelsLabel: (Int) -> String,
    trackFallbackLabel: String,
): HumanTrackLabel {
    val language = humanLanguageName(languageCode, unknownLanguageLabel, multipleLanguagesLabel)
    val knownLanguage = language?.takeUnless {
        isUnknownLanguageCode(languageCode) || isMultipleLanguagesCode(languageCode)
    }
    val channels = humanChannelLayout(
        channelCount = channelCount,
        monoLabel = monoLabel,
        stereoLabel = stereoLabel,
        surround51Label = surround51Label,
        surround71Label = surround71Label,
        channelsLabel = channelsLabel,
    )
    val role = roleLabel?.takeIf { it.isNotBlank() }
    val name = role ?: language ?: channels ?: trackFallbackLabel
    // Broadcasters often give audio description and clear dialogue tracks a placeholder
    // language code; the role then names the track better than "Unknown language".
    val summaryParts = if (role != null && knownLanguage == null) {
        listOf(role, channels)
    } else {
        listOf(language, channels, role)
    }
    return HumanTrackLabel(
        summary = summaryParts.filterNotNull().joinToString(" · ").ifBlank { trackFallbackLabel },
        name = name,
        overline = knownLanguage?.takeIf { role != null },
        detail = listOfNotNull(channels?.takeIf { it != name }, humanCodecName(sampleMimeType))
            .joinToString(" · ")
            .ifBlank { null },
    )
}

fun humanLanguageName(
    languageCode: String?,
    unknownLanguageLabel: String,
    multipleLanguagesLabel: String = unknownLanguageLabel,
): String? {
    val code = languageCode?.trim().orEmpty()
    if (code.isEmpty()) return null
    return when {
        isUnknownLanguageCode(code) -> unknownLanguageLabel
        isMultipleLanguagesCode(code) -> multipleLanguagesLabel
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

private val UNKNOWN_LANGUAGE_CODES = setOf("und", "mis", "zxx", "qaa")

private fun isUnknownLanguageCode(languageCode: String?): Boolean =
    languageCode?.trim()?.lowercase(Locale.ROOT) in UNKNOWN_LANGUAGE_CODES

private fun isMultipleLanguagesCode(languageCode: String?): Boolean =
    languageCode?.trim()?.lowercase(Locale.ROOT) == "mul"

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
        mime.contains("eac3", ignoreCase = true) -> "Dolby Digital Plus"
        mime.contains("ac3", ignoreCase = true) -> "Dolby Digital"
        mime.contains("ac4", ignoreCase = true) -> "Dolby AC-4"
        mime.contains("mpeg-L2", ignoreCase = true) ||
            mime.contains("mpeg-l2", ignoreCase = true) -> "MPEG-1 Layer II"
        mime.contains("mpeg", ignoreCase = true) && mime.contains("audio", ignoreCase = true) ->
            "MPEG Audio"
        mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
        mime.contains("opus", ignoreCase = true) -> "Opus"
        mime.startsWith("audio/") -> mime.removePrefix("audio/")
        mime == "application/dvbsubs" -> "DVB"
        // Tvheadend extracts its text subtitles from teletext pages; the SDK delivers them as cues.
        mime == "application/x-media3-cues" -> "Teletext"
        mime == "application/cea-608" || mime == "application/cea-708" -> "CC"
        else -> null
    }
}

