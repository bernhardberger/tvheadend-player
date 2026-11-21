package at.bernhardberger.tvhplayer.core

/**
 * Text for the About section: summary, then a description that does not already open with it. The
 * first of them is the top block's synopsis; when [synopsisShownInFull], About leaves it out rather
 * than repeat it.
 */
fun programmeAboutText(summary: String?, description: String?, synopsisShownInFull: Boolean): List<String> {
    val texts = listOfNotNull(summary, description).map { it.replace("\\r\\n", "\n").replace("\\n", "\n").trim() }
        .filter(String::isNotEmpty)
    val unique = if (texts.size == 2 && texts[1].startsWith(texts[0])) texts.drop(1) else texts
    return if (synopsisShownInFull) unique.drop(1) else unique
}
