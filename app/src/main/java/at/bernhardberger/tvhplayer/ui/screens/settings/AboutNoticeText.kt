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

internal enum class ChangelogBlockKind { VERSION_HEADING, GROUP_HEADING, BULLET_PARAGRAPH, PARAGRAPH }

internal data class ChangelogBlock(val kind: ChangelogBlockKind, val text: String)

/** The bundled changelog's headings and paragraphs, with hard-wrapped bullets joined. */
internal fun changelogBlocks(markdown: String): List<ChangelogBlock> = buildList {
    var paragraph = ""
    var paragraphKind = ChangelogBlockKind.PARAGRAPH
    fun flushParagraph() {
        if (paragraph.isNotBlank()) add(ChangelogBlock(paragraphKind, aboutNoticeText(paragraph)))
        paragraph = ""
    }
    for (line in newestChangelogSections(markdown).lineSequence()) {
        val heading = noticeHeading.matchEntire(line)
        when {
            heading != null -> {
                flushParagraph()
                add(ChangelogBlock(
                    if (line.takeWhile { it == '#' }.length == 2) ChangelogBlockKind.VERSION_HEADING else ChangelogBlockKind.GROUP_HEADING,
                    aboutNoticeText(line),
                ))
            }
            line.isBlank() -> flushParagraph()
            noticeListMarker.containsMatchIn(line) -> {
                flushParagraph()
                paragraphKind = ChangelogBlockKind.BULLET_PARAGRAPH
                paragraph = line.trim()
            }
            else -> {
                if (paragraph.isEmpty()) paragraphKind = ChangelogBlockKind.PARAGRAPH
                paragraph = if (paragraph.isEmpty()) line.trim() else "$paragraph ${line.trim()}"
            }
        }
    }
    flushParagraph()
}

/** Keep only the newest five version sections, excluding the document's introductory title. */
internal fun newestChangelogSections(markdown: String): String {
    val headings = Regex("(?m)^##\\s+\\S[^\\r\\n]*").findAll(markdown).toList()
    if (headings.isEmpty()) return ""
    return markdown.substring(headings.first().range.first,
        headings.getOrNull(5)?.range?.first ?: markdown.length).trim()
}
