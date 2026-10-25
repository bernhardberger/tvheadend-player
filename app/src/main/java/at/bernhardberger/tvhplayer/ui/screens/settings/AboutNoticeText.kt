package at.bernhardberger.tvhplayer.ui.screens.settings

private val noticeHeading = Regex("^#{1,6}\\s+(.+)$")
private val noticeListMarker = Regex("^\\s*(?:[-+*]|\\d+[.)])\\s+")
private val noticeLink = Regex("\\[([^]]+)]\\(([^)]+)\\)")

/** Plain-text presentation of the bundled NOTICE's limited Markdown, not a Markdown renderer. */
internal fun aboutNoticeText(markdown: String): String {
    val lines = mutableListOf<String>()
    for (sourceLine in markdown.lineSequence()) {
        val heading = noticeHeading.matchEntire(sourceLine)
        val line = heading?.groupValues?.get(1) ?: sourceLine.replaceFirst(noticeListMarker, "• ")
        val text = line.replace(noticeLink) {
            val (label, url) = it.destructured
            if (label == url) label else "$label ($url)"
        }.replace("*", "").replace("`", "")
        if (heading != null && lines.lastOrNull()?.isNotBlank() == true) lines.add("")
        if (text.isNotBlank() || lines.lastOrNull()?.isNotBlank() == true) lines.add(text)
        if (heading != null) lines.add("")
    }
    return lines.joinToString("\n").trim()
}
