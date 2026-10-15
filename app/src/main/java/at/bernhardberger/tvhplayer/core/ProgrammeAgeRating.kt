package at.bernhardberger.tvhplayer.core

/**
 * Preserve a rating's authority instead of presenting an unexplained number. Tvheadend passes an
 * unconfigured XMLTV rating through as "XMLTV:<system>:<value>" or "XMLTV:<value>"; guide sources
 * often put the authority's full name into the system, so only a short code is kept.
 */
internal fun programmeAgeRating(label: String?, age: Long?): String? {
    var text = label?.trim()?.takeIf(String::isNotEmpty)
    val xmltv = text?.takeIf { it.startsWith("XMLTV:") }?.split(':', limit = 3)
    if (xmltv != null) {
        val authority = if (xmltv.size == 3) xmltv[1].trim() else ""
        val value = xmltv.last().trim()
        if (value.isNotEmpty() && authority.isShortRatingCode()) return "$authority $value"
        text = value.takeIf(String::isNotEmpty)
    }
    if (text != null && text.toLongOrNull() == null) return text
    return (age ?: text?.toLongOrNull())?.let { "$it+" }
}

private fun String.isShortRatingCode() = length in 1..MaxRatingCodeLength && none(Char::isWhitespace)

// Long enough for FSK, FSF, PEGI, BBFC, MPAA and similar codes.
private const val MaxRatingCodeLength = 6
